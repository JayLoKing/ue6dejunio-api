package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * B10: autorizacion por propiedad. Director escribe todo; Teacher solo sus class_groups.
 * Uso en @PreAuthorize: @authz.canWriteClassGroup(authentication, #id)
 */
@Component("authz")
public class AuthorizationComponent {

    private static final String ROLE_DIRECTOR = "ROLE_Director";

    private final IClassGroupDomain classGroupDomain;

    public AuthorizationComponent(IClassGroupDomain classGroupDomain) {
        this.classGroupDomain = classGroupDomain;
    }

    public boolean canWriteClassGroup(Authentication authentication, UUID classGroupId) {
        if (authentication == null || classGroupId == null) {
            return false;
        }
        if (hasRole(authentication, ROLE_DIRECTOR)) {
            return true;
        }
        UUID userId = userId(authentication);
        UUID owner = classGroupDomain.teacherIdOfClassGroup(classGroupId);
        return userId != null && userId.equals(owner);
    }

    private boolean hasRole(Authentication auth, String role) {
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (role.equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private UUID userId(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            return UUID.fromString(jwt.getSubject());
        }
        return null;
    }
}
