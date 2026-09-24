package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final IUserDomain userDomain;

    public CustomUserDetailsService(IUserDomain userDomain) {
        this.userDomain = userDomain;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return userDomain
                .findByEmail(email)
                .map(SecurityUserAdapter::new)
                .orElseThrow(
                        () -> new UsernameNotFoundException("Usuario no encontrado: " + email));
    }
}
