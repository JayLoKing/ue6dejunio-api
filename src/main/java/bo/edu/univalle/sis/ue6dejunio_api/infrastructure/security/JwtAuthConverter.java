package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String RESET_PURPOSE = "pwd_reset";

    private final IUserDomain userDomain;

    public JwtAuthConverter(IUserDomain userDomain) {
        this.userDomain = userDomain;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (RESET_PURPOSE.equals(jwt.getClaimAsString("purpose"))) {
            throw new InvalidBearerTokenException("Reset tokens cannot be used as access tokens");
        }

        // The account is re-read on every request, and this is the only thing that makes closing
        // one
        // take effect now. Nothing about the token can say otherwise: it is still signed and still
        // inside its window, so without this read the holder keeps working until it expires -- up
        // to
        // the full access TTL. It matters because the Director's own reassignment workflow begins
        // by
        // deactivating the teacher whose course is being moved, and for those minutes that teacher
        // could still enter marks for the course they just lost.
        //
        // There is no session table to consult, so this is where it has to happen. One indexed
        // boolean per authenticated request is the price; a cache would buy it back by reopening
        // exactly the hole this closes, for however long the cache lived.
        if (!userDomain.isActive(subjectOf(jwt))) {
            throw new InvalidBearerTokenException("The account behind this token is not active");
        }

        String role = jwt.getClaimAsString("role");
        Collection<GrantedAuthority> authorities =
                role == null || role.isBlank()
                        ? List.of()
                        : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    /**
     * Only this issuer mints these tokens and it always writes a uuid here, so anything else did
     * not come from us. Letting the parse failure escape would answer an unauthenticated request
     * with a 500.
     */
    private static UUID subjectOf(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("Token subject is not a user id");
        }
    }
}
