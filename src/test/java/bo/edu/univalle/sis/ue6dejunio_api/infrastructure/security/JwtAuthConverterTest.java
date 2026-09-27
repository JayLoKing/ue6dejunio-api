package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

@ExtendWith(MockitoExtension.class)
class JwtAuthConverterTest {

    @Mock private IUserDomain userDomain;

    private JwtAuthConverter converter;

    private JwtAuthConverter converter() {
        if (converter == null) {
            converter = new JwtAuthConverter(userDomain);
        }
        return converter;
    }

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600));
    }

    @Test
    void convert_resetPurposeToken_throwsInvalidBearerToken() {
        Jwt jwt =
                token().subject(UUID.randomUUID().toString()).claim("purpose", "pwd_reset").build();

        assertThatThrownBy(() -> converter().convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class);
        // Rejected on the claim alone: a reset token is not an access token whoever holds it, so
        // there is nothing to look up.
        verify(userDomain, never()).isActive(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void convert_accessToken_returnsAuthenticationTokenWithRole() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = token().subject(userId.toString()).claim("role", "DIRECTOR").build();
        when(userDomain.isActive(userId)).thenReturn(true);

        AbstractAuthenticationToken result = converter().convert(jwt);

        assertThat(result).isNotNull();
        assertThat(result.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_DIRECTOR");
    }

    /**
     * The point of the whole check. The signature is still good and the token is still inside its
     * window, so nothing about the token itself says the school closed the account two minutes ago.
     * Without this the holder keeps working until the token expires, and the Director's own
     * reassignment workflow starts by deactivating the teacher whose course is being moved.
     */
    @Test
    void convert_deactivatedUser_throwsInvalidBearerToken() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = token().subject(userId.toString()).claim("role", "Teacher").build();
        when(userDomain.isActive(userId)).thenReturn(false);

        assertThatThrownBy(() -> converter().convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    /**
     * A deleted user is not an open account either. `isActive` answers false for both, so the
     * converter does not need to tell them apart — and it must not answer differently, because that
     * would let a caller probe which ids exist.
     */
    @Test
    void convert_unknownUser_throwsInvalidBearerToken() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = token().subject(userId.toString()).claim("role", "DIRECTOR").build();
        when(userDomain.isActive(userId)).thenReturn(false);

        assertThatThrownBy(() -> converter().convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    /**
     * Only this issuer mints these tokens and it always puts a uuid in the subject, so anything
     * else did not come from us. Letting the parse failure escape would surface as a 500 on a
     * request that is simply not authenticated.
     */
    @Test
    void convert_subjectThatIsNotAUuid_throwsInvalidBearerToken() {
        Jwt jwt = token().subject("not-a-uuid").claim("role", "DIRECTOR").build();

        assertThatThrownBy(() -> converter().convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class);
    }
}
