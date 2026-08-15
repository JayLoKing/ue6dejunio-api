package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.auth;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.AuthenticatedUser;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.ChangePasswordCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.LoginCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth.IAuthService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.AuthResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ChangePasswordRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ForgotPasswordRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ForgotPasswordResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.LoginRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.MeResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ResetPasswordRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Autenticacion y sesion")
public class AuthController {

    private final IAuthService authService;

    public AuthController(IAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesion", security = {})
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthenticatedUser authenticated = authService.login(
            new LoginCommand(request.email(), request.password())
        );
        return ResponseEntity.ok(AuthResponse.from(authenticated));
    }

    @GetMapping("/me")
    @Operation(summary = "Datos del usuario autenticado", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<MeResponse> me(JwtAuthenticationToken token) {
        Jwt jwt = token.getToken();
        Boolean mustChange = jwt.getClaim("mustChangePassword");
        String gradeName = jwt.getClaimAsString("gradeName");
        String parallelName = jwt.getClaimAsString("parallelName");
        String courseId = jwt.getClaimAsString("courseId");
        String technical = jwt.getClaimAsString("technical");
        return ResponseEntity.ok(new MeResponse(
            UUID.fromString(jwt.getSubject()),
            jwt.getClaimAsString("email"),
            jwt.getClaimAsString("name"),
            jwt.getClaimAsString("role"),
            mustChange != null && mustChange,
            gradeName != null && !gradeName.isBlank() ? gradeName : null,
            parallelName != null && !parallelName.isBlank() ? parallelName : null,
            courseId != null && !courseId.isBlank() ? UUID.fromString(courseId) : null,
            technical != null && !technical.isBlank() ? Boolean.valueOf(technical) : null
        ));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Cambiar contrasena del usuario autenticado",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> changePassword(
        @Valid @RequestBody ChangePasswordRequest request,
        JwtAuthenticationToken token
    ) {
        UUID userId = UUID.fromString(token.getToken().getSubject());
        authService.changePassword(new ChangePasswordCommand(
            userId, request.currentPassword(), request.newPassword()
        ));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Solicitar recuperación de contraseña", security = {})
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
        @Valid @RequestBody ForgotPasswordRequest request
    ) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(ForgotPasswordResponse.generic());
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Restablecer contraseña con token de recuperación", security = {})
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
