package bo.edu.univalle.sis.ue6dejunio_api.application.services.auth;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.InvalidCredentialsException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.UserInactiveException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.AuthenticatedUser;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.ChangePasswordCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.LoginCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.InvalidResetTokenException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth.IAuthService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth.IJwtService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.mail.IEmailService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import org.springframework.beans.factory.annotation.Value;
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
    private final IEmailService emailService;
    private final String resetUrl;

    public AuthService(IUserDomain userDomain, PasswordEncoder passwordEncoder,
                       IJwtService jwtService, ICourseDomain courseDomain,
                       IEmailService emailService,
                       @Value("${app.frontend.reset-url}") String resetUrl) {
        this.userDomain = userDomain;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.courseDomain = courseDomain;
        this.emailService = emailService;
        this.resetUrl = resetUrl;
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

    @Override
    public void forgotPassword(String email) {
        Optional<User> maybeUser = userDomain.findByEmail(email);
        if (maybeUser.isPresent() && maybeUser.get().isActive()) {
            User user = maybeUser.get();
            String token = jwtService.issuePasswordResetToken(user);
            String link = resetUrl + "?token=" + token;
            emailService.sendPasswordReset(user.getEmail(), user.fullName(), link);
        }
        // Enumeration-safe: always returns normally regardless of whether the email exists.
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        java.util.UUID userId = jwtService.validatePasswordResetToken(token);
        User user = userDomain.findById(userId)
            .orElseThrow(InvalidResetTokenException::new);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userDomain.save(user);
    }
}
