package bo.edu.univalle.sis.ue6dejunio_api.application.services.auth;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.InvalidCredentialsException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.UserInactiveException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.AuthenticatedUser;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.ChangePasswordCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.LoginCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth.IAuthService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth.IJwtService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService implements IAuthService {

    private final IUserDomain userDomain;
    private final PasswordEncoder passwordEncoder;
    private final IJwtService jwtService;

    public AuthService(IUserDomain userDomain, PasswordEncoder passwordEncoder, IJwtService jwtService) {
        this.userDomain = userDomain;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    public AuthenticatedUser login(LoginCommand command) {
        User user = userDomain.findByEmail(command.email())
            .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(command.rawPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isActive()) {
            throw new UserInactiveException();
        }
        return jwtService.issueToken(user);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordCommand command) {
        User user = userDomain.findById(command.userId())
            .orElseThrow(() -> new ResourceNotFoundException("Usuario", command.userId()));

        if (!passwordEncoder.matches(command.currentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isActive()) {
            throw new UserInactiveException();
        }
        user.setPassword(passwordEncoder.encode(command.newPassword()));
        user.setMustChangePassword(false);
        userDomain.save(user);
    }
}
