package bo.edu.univalle.sis.ue6dejunio_api.application.services.auth;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.InvalidResetTokenException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.AuthenticatedUser;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth.IJwtService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class JwtService implements IJwtService {

    private static final String RESET_PURPOSE = "pwd_reset";

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final long ttlMinutes;
    private final JwtDecoder jwtDecoder;
    private final long resetTokenTtlMinutes;
    private final IUserDomain userDomain;

    public JwtService(
        JwtEncoder jwtEncoder,
        @Value("${app.security.jwt.issuer}") String issuer,
        @Value("${app.security.jwt.access-token-ttl-minutes}") long ttlMinutes,
        JwtDecoder jwtDecoder,
        @Value("${app.security.jwt.reset-token-ttl-minutes}") long resetTokenTtlMinutes,
        IUserDomain userDomain
    ) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.ttlMinutes = ttlMinutes;
        this.jwtDecoder = jwtDecoder;
        this.resetTokenTtlMinutes = resetTokenTtlMinutes;
        this.userDomain = userDomain;
    }

    @Override
    public AuthenticatedUser issueToken(User user, String gradeName, String parallelName, java.util.UUID courseId, Boolean technical) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttlMinutes, ChronoUnit.MINUTES);
        String role = user.getRole() != null ? user.getRole().name() : "";

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .issuedAt(now)
            .expiresAt(exp)
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .claim("role", role)
            .claim("name", user.fullName())
            .claim("mustChangePassword", user.isMustChangePassword())
            .claim("gradeName", gradeName != null ? gradeName : "")
            .claim("parallelName", parallelName != null ? parallelName : "")
            .claim("courseId", courseId != null ? courseId.toString() : "")
            .claim("technical", technical != null ? technical.toString() : "")
            .build();

        String token = jwtEncoder.encode(
            JwtEncoderParameters.from(JwsHeader.with(() -> "RS256").build(), claims)
        ).getTokenValue();

        return new AuthenticatedUser(
            user.getId(), user.getEmail(), user.fullName(), role, token, now, exp,
            user.isMustChangePassword(), gradeName, parallelName, courseId, technical
        );
    }

    @Override
    public String issuePasswordResetToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(resetTokenTtlMinutes, ChronoUnit.MINUTES);

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .issuedAt(now)
            .expiresAt(exp)
            .subject(user.getId().toString())
            .claim("purpose", RESET_PURPOSE)
            .claim("pwh", sha256Hex(user.getPassword()))
            .build();

        return jwtEncoder.encode(
            JwtEncoderParameters.from(JwsHeader.with(() -> "RS256").build(), claims)
        ).getTokenValue();
    }

    @Override
    public UUID validatePasswordResetToken(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);

            if (!RESET_PURPOSE.equals(jwt.getClaimAsString("purpose"))) {
                throw new InvalidResetTokenException();
            }

            UUID userId = UUID.fromString(jwt.getSubject());
            User user = userDomain.findById(userId)
                .orElseThrow(InvalidResetTokenException::new);

            String expectedPwh = sha256Hex(user.getPassword());
            String claimedPwh = jwt.getClaimAsString("pwh");
            if (claimedPwh == null || !constantTimeEquals(claimedPwh, expectedPwh)) {
                throw new InvalidResetTokenException();
            }

            return userId;
        } catch (InvalidResetTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidResetTokenException();
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
