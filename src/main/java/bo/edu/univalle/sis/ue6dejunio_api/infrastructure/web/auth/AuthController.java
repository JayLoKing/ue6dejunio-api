package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.auth;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.AuthenticatedUser;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.ChangeEmailCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.ChangePasswordCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.LoginCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.UpdateProfileCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth.IAuthService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.AuthResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ChangeEmailRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ChangePasswordRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ForgotPasswordRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ForgotPasswordResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.LoginRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.MeResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ProfileResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ResetPasswordRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Autenticacion y sesion")
public class AuthController {

    private final IAuthService authService;

    public AuthController(IAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Iniciar sesion",
            security = {})
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthenticatedUser authenticated =
                authService.login(new LoginCommand(request.email(), request.password()));
        return ResponseEntity.ok(AuthResponse.from(authenticated));
    }

    /**
     * Renews the session of whoever holds a still-valid token. No body: the id comes from the token
     * the resource server already verified, so nobody can ask to renew somebody else's session.
     */
    @PostMapping("/refresh")
    @Operation(
            summary = "Renovar la sesion con el token vigente",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<AuthResponse> refresh(JwtAuthenticationToken token) {
        UUID userId = UUID.fromString(token.getToken().getSubject());
        return ResponseEntity.ok(AuthResponse.from(authService.refresh(userId)));
    }

    @GetMapping("/me")
    @Operation(
            summary = "Datos del usuario autenticado",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<MeResponse> me(JwtAuthenticationToken token) {
        Jwt jwt = token.getToken();
        Boolean mustChange = jwt.getClaim("mustChangePassword");
        String gradeName = jwt.getClaimAsString("gradeName");
        String parallelName = jwt.getClaimAsString("parallelName");
        String courseId = jwt.getClaimAsString("courseId");
        String technical = jwt.getClaimAsString("technical");
        return ResponseEntity.ok(
                new MeResponse(
                        UUID.fromString(jwt.getSubject()),
                        jwt.getClaimAsString("email"),
                        jwt.getClaimAsString("name"),
                        jwt.getClaimAsString("role"),
                        mustChange != null && mustChange,
                        gradeName != null && !gradeName.isBlank() ? gradeName : null,
                        parallelName != null && !parallelName.isBlank() ? parallelName : null,
                        courseId != null && !courseId.isBlank() ? UUID.fromString(courseId) : null,
                        technical != null && !technical.isBlank()
                                ? Boolean.valueOf(technical)
                                : null));
    }

    /** Reads the caller's own row; the subject comes from the verified token, never from a path. */
    @GetMapping("/profile")
    @Operation(
            summary = "Datos editables del usuario autenticado",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ProfileResponse> profile(JwtAuthenticationToken token) {
        UUID userId = UUID.fromString(token.getToken().getSubject());
        return ResponseEntity.ok(ProfileResponse.from(authService.getProfile(userId)));
    }

    /**
     * Edits the caller's own names and phone. No id anywhere in the request: the subject is the one
     * the resource server verified, so nobody can edit somebody else's profile through here.
     */
    @PutMapping("/profile")
    @Operation(
            summary = "Actualizar nombres y teléfono del usuario autenticado",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<MeResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request, JwtAuthenticationToken token) {
        UUID userId = UUID.fromString(token.getToken().getSubject());
        AuthenticatedUser updated =
                authService.updateProfile(
                        new UpdateProfileCommand(
                                userId, request.names(), request.lastNames(), request.phone()));
        return ResponseEntity.ok(
                new MeResponse(
                        updated.userId(),
                        updated.email(),
                        updated.fullName(),
                        updated.role(),
                        updated.mustChangePassword(),
                        updated.gradeName(),
                        updated.parallelName(),
                        updated.courseId(),
                        updated.technical()));
    }

    @PostMapping("/change-password")
    @Operation(
            summary = "Cambiar contrasena del usuario autenticado",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request, JwtAuthenticationToken token) {
        UUID userId = UUID.fromString(token.getToken().getSubject());
        authService.changePassword(
                new ChangePasswordCommand(
                        userId, request.currentPassword(), request.newPassword()));
        return ResponseEntity.noContent().build();
    }

    /**
     * Moves the caller's own account to another address. The id comes from the verified token, and
     * the current password proves the person at the keyboard owns it. Answers with a fresh session
     * because the old token's email claim still names the previous address.
     */
    @PostMapping("/change-email")
    @Operation(
            summary = "Cambiar el correo del usuario autenticado",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<AuthResponse> changeEmail(
            @Valid @RequestBody ChangeEmailRequest request, JwtAuthenticationToken token) {
        UUID userId = UUID.fromString(token.getToken().getSubject());
        AuthenticatedUser updated =
                authService.changeEmail(
                        new ChangeEmailCommand(
                                userId, request.newEmail(), request.currentPassword()));
        return ResponseEntity.ok(AuthResponse.from(updated));
    }

    @PostMapping("/forgot-password")
    @Operation(
            summary = "Solicitar recuperación de contraseña",
            security = {})
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(ForgotPasswordResponse.generic());
    }

    @PostMapping("/reset-password")
    @Operation(
            summary = "Restablecer contraseña con token de recuperación",
            security = {})
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
