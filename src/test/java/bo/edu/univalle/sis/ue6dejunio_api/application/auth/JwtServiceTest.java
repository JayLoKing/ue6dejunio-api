package bo.edu.univalle.sis.ue6dejunio_api.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.auth.JwtService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.InvalidResetTokenException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.role.Role;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock private IUserDomain userDomain;

    private JwtEncoder jwtEncoder;
    private JwtDecoder jwtDecoder;
    private JwtService jwtService;

    private static final long TTL_MINUTES = 20L;
    private static final String ISSUER = "ue6dejunio-api";

    private User activeUser;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        RSAKey rsaKey =
                new RSAKey.Builder(publicKey)
                        .privateKey(privateKey)
                        .keyID(UUID.randomUUID().toString())
                        .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        jwtEncoder = new NimbusJwtEncoder(jwkSource);
        jwtDecoder = NimbusJwtDecoder.withPublicKey(publicKey).build();

        jwtService =
                new JwtService(
                        jwtEncoder, ISSUER, TTL_MINUTES, jwtDecoder, TTL_MINUTES, userDomain);

        activeUser =
                User.builder()
                        .id(UUID.randomUUID())
                        .email("user@ue6.bo")
                        .names("Ana")
                        .lastNames("Perez")
                        .password("$2a$10$hashedvalue")
                        .role(new Role(1, "DIRECTOR"))
                        .active(true)
                        .build();
    }

    @Test
    void issuePasswordResetToken_setsPwhClaimToSha256OfStoredHash() {
        String token = jwtService.issuePasswordResetToken(activeUser);

        org.springframework.security.oauth2.jwt.Jwt decoded = jwtDecoder.decode(token);
        assertThat(decoded.getClaimAsString("purpose")).isEqualTo("pwd_reset");
        assertThat(decoded.getSubject()).isEqualTo(activeUser.getId().toString());
        assertThat(decoded.getClaimAsString("pwh")).isEqualTo(sha256Hex(activeUser.getPassword()));
        assertThat(decoded.getExpiresAt()).isAfter(Instant.now().plus(19, ChronoUnit.MINUTES));
    }

    @Test
    void validatePasswordResetToken_validToken_returnsUserId() {
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        String token = jwtService.issuePasswordResetToken(activeUser);

        UUID result = jwtService.validatePasswordResetToken(token);

        assertThat(result).isEqualTo(activeUser.getId());
    }

    @Test
    void validatePasswordResetToken_expiredToken_throwsInvalidResetToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(ISSUER)
                        .issuedAt(now.minus(30, ChronoUnit.MINUTES))
                        .expiresAt(now.minus(10, ChronoUnit.MINUTES))
                        .subject(activeUser.getId().toString())
                        .claim("purpose", "pwd_reset")
                        .claim("pwh", sha256Hex(activeUser.getPassword()))
                        .build();
        String expiredToken =
                jwtEncoder
                        .encode(
                                JwtEncoderParameters.from(
                                        JwsHeader.with(() -> "RS256").build(), claims))
                        .getTokenValue();

        assertThatThrownBy(() -> jwtService.validatePasswordResetToken(expiredToken))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void validatePasswordResetToken_passwordChangedSinceIssuance_throwsInvalidResetToken() {
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        String token = jwtService.issuePasswordResetToken(activeUser);

        // password rotated after token issuance (e.g. token already used once)
        activeUser.setPassword("$2a$10$anotherHashAfterReset");

        assertThatThrownBy(() -> jwtService.validatePasswordResetToken(token))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void validatePasswordResetToken_wrongPurpose_throwsInvalidResetToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(ISSUER)
                        .issuedAt(now)
                        .expiresAt(now.plus(20, ChronoUnit.MINUTES))
                        .subject(activeUser.getId().toString())
                        .claim("purpose", "access")
                        .claim("pwh", sha256Hex(activeUser.getPassword()))
                        .build();
        String accessToken =
                jwtEncoder
                        .encode(
                                JwtEncoderParameters.from(
                                        JwsHeader.with(() -> "RS256").build(), claims))
                        .getTokenValue();

        assertThatThrownBy(() -> jwtService.validatePasswordResetToken(accessToken))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void validatePasswordResetToken_unknownUser_throwsInvalidResetToken() {
        when(userDomain.findById(activeUser.getId())).thenReturn(Optional.empty());
        String token = jwtService.issuePasswordResetToken(activeUser);

        assertThatThrownBy(() -> jwtService.validatePasswordResetToken(token))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
