package bo.edu.univalle.sis.ue6dejunio_api.application.services.auth;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.AuthenticatedUser;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth.IJwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class JwtService implements IJwtService {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final long ttlMinutes;

    public JwtService(
        JwtEncoder jwtEncoder,
        @Value("${app.security.jwt.issuer}") String issuer,
        @Value("${app.security.jwt.access-token-ttl-minutes}") long ttlMinutes
    ) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.ttlMinutes = ttlMinutes;
    }

    @Override
    public AuthenticatedUser issueToken(User user, String gradeName, String parallelName) {
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
            .build();

        String token = jwtEncoder.encode(
            JwtEncoderParameters.from(JwsHeader.with(() -> "RS256").build(), claims)
        ).getTokenValue();

        return new AuthenticatedUser(
            user.getId(), user.getEmail(), user.fullName(), role, token, now, exp,
            user.isMustChangePassword(), gradeName, parallelName
        );
    }
}
