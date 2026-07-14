package bo.edu.univalle.sis.ue6dejunio_api.application.services.auth;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.InvalidCredentialsException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.UserInactiveException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.AuthenticatedUser;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.ChangePasswordCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.LoginCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth.IAuthService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth.IJwtService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService implements IAuthService {

    private static final String TEACHER_ROLE = "Teacher";

    private final IUserDomain userDomain;
    private final PasswordEncoder passwordEncoder;
    private final IJwtService jwtService;
    private final ICourseDomain courseDomain;

    public AuthService(IUserDomain userDomain, PasswordEncoder passwordEncoder,
                       IJwtService jwtService, ICourseDomain courseDomain) {
        this.userDomain = userDomain;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.courseDomain = courseDomain;
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

        String gradeName = null;
        String parallelName = null;
        java.util.UUID courseId = null;
        Boolean technical = null;
        if (user.getRole() != null && TEACHER_ROLE.equals(user.getRole().name())) {
            technical = user.isTechnical();
            Optional<Course> homeroom = courseDomain.homeroomCourseOf(user.getId());
            if (homeroom.isPresent()) {
                gradeName = homeroom.get().gradeName();
                parallelName = homeroom.get().parallelName();
                courseId = homeroom.get().id();
            }
        }
        return jwtService.issueToken(user, gradeName, parallelName, courseId, technical);
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
