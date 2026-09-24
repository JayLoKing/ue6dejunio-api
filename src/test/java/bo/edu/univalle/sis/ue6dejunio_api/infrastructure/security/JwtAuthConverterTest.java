package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class JwtAuthConverterTest {

    private final JwtAuthConverter converter = new JwtAuthConverter();

    @Test
    void convert_resetPurposeToken_throwsInvalidBearerToken() {
        Jwt jwt =
                Jwt.withTokenValue("token")
                        .header("alg", "RS256")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(600))
                        .subject("user-id")
                        .claim("purpose", "pwd_reset")
                        .build();

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void convert_accessToken_returnsAuthenticationTokenWithRole() {
        Jwt jwt =
                Jwt.withTokenValue("token")
                        .header("alg", "RS256")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(600))
                        .subject("user-id")
                        .claim("role", "DIRECTOR")
                        .build();

        AbstractAuthenticationToken result = converter.convert(jwt);

        assertThat(result).isNotNull();
        assertThat(result.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_DIRECTOR");
    }
}
