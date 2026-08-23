package bo.edu.univalle.sis.ue6dejunio_api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Spec: RF3 stateless, single-use, email-based password reset.
 * Docker-gated via {@link AbstractIntegrationTest} (Testcontainers disabledWithoutDocker=true).
 */
class AccountRecoveryIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID activeUserId;
    private static final String ORIGINAL_PASSWORD = "OldPass1";
    private static final String NEW_PASSWORD = "NewPass123";

    @BeforeEach
    void seed() {
        activeUserId = seedUser("Director", false);
        jdbc.update(
            "UPDATE users SET password = ? WHERE id_user = ?",
            passwordEncoder.encode(ORIGINAL_PASSWORD), activeUserId
        );
    }

    private String currentStoredHash() {
        return jdbc.queryForObject(
            "SELECT password FROM users WHERE id_user = ?", String.class, activeUserId);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Mints a real RS256 reset token mirroring JwtService.issuePasswordResetToken. */
    private String resetTokenFor(UUID userId, String pwh, Instant exp) {
        Instant now = Instant.now();
        // An already-expired token still has to be internally consistent: JwtClaimsSet rejects an
        // expiry that precedes issuance, so back-date issuedAt when the caller asks for a past exp.
        Instant issuedAt = now.isBefore(exp) ? now : exp.minus(1, ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(jwtIssuer)
            .issuedAt(issuedAt)
            .expiresAt(exp)
            .subject(userId.toString())
            .claim("purpose", "pwd_reset")
            .claim("pwh", pwh)
            .build();
        return jwtEncoder.encode(
            JwtEncoderParameters.from(JwsHeader.with(() -> "RS256").build(), claims)
        ).getTokenValue();
    }

    @Test
    void forgotPassword_existingAndUnknownEmail_returnIdenticalGeneric200() throws Exception {
        String email = jdbc.queryForObject(
            "SELECT email FROM users WHERE id_user = ?", String.class, activeUserId);

        String existingBody = mvc.perform(post("/api/auth/forgot-password")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String unknownBody = mvc.perform(post("/api/auth/forgot-password")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"nobody-" + UUID.randomUUID() + "@ue6.test\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(existingBody).isEqualTo(unknownBody);
    }

    @Test
    void resetPassword_validToken_returns204ThenLoginWithNewPasswordWorks() throws Exception {
        String token = resetTokenFor(
            activeUserId, sha256Hex(currentStoredHash()), Instant.now().plus(20, ChronoUnit.MINUTES));

        mvc.perform(post("/api/auth/reset-password")
                .contentType(APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
            .andExpect(status().isNoContent());

        String email = jdbc.queryForObject(
            "SELECT email FROM users WHERE id_user = ?", String.class, activeUserId);

        mvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + NEW_PASSWORD + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void resetPassword_expiredToken_returns400AndPasswordUnchanged() throws Exception {
        String storedHashBefore = currentStoredHash();
        String token = resetTokenFor(
            activeUserId, sha256Hex(storedHashBefore), Instant.now().minus(1, ChronoUnit.MINUTES));

        mvc.perform(post("/api/auth/reset-password")
                .contentType(APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
            .andExpect(status().isBadRequest());

        org.assertj.core.api.Assertions.assertThat(currentStoredHash()).isEqualTo(storedHashBefore);
    }

    @Test
    void resetPassword_reusedToken_returns400AndPasswordUnchangedOnSecondUse() throws Exception {
        String token = resetTokenFor(
            activeUserId, sha256Hex(currentStoredHash()), Instant.now().plus(20, ChronoUnit.MINUTES));

        mvc.perform(post("/api/auth/reset-password")
                .contentType(APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
            .andExpect(status().isNoContent());

        String hashAfterFirstReset = currentStoredHash();

        mvc.perform(post("/api/auth/reset-password")
                .contentType(APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"AnotherPass1\"}"))
            .andExpect(status().isBadRequest());

        org.assertj.core.api.Assertions.assertThat(currentStoredHash()).isEqualTo(hashAfterFirstReset);
    }

    @Test
    void resetToken_usedAsBearer_isRejected() throws Exception {
        String token = resetTokenFor(
            activeUserId, sha256Hex(currentStoredHash()), Instant.now().plus(20, ChronoUnit.MINUTES));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                org.assertj.core.api.Assertions.assertThat(status).isIn(401, 403);
            });
    }

    @Test
    void forgotPassword_repeatedRequestsFromSameIp_returns429() throws Exception {
        String email = jdbc.queryForObject(
            "SELECT email FROM users WHERE id_user = ?", String.class, activeUserId);
        String body = "{\"email\":\"" + email + "\"}";

        // application-it.properties caps forgot-password bucket at capacity=2 for determinism.
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/auth/forgot-password").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        }

        mvc.perform(post("/api/auth/forgot-password").contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isTooManyRequests());
    }
}
