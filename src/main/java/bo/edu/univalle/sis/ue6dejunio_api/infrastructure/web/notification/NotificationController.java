package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.notification;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.SortField;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.Notification;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.SendNotificationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.NotificationResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.SendNotificationRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.UnreadCountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Mensajeria interna entre usuarios")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final INotificationService notificationService;

    public NotificationController(INotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private static UUID currentUser(JwtAuthenticationToken token) {
        return UUID.fromString(token.getToken().getSubject());
    }

    @PostMapping
    @Operation(summary = "Enviar notificacion (sender = usuario autenticado)")
    public ResponseEntity<NotificationResponse> send(@Valid @RequestBody SendNotificationRequest request,
                                                     JwtAuthenticationToken token) {
        Notification n = notificationService.send(new SendNotificationCommand(
            currentUser(token), request.receiverId(), request.message()));
        return ResponseEntity.ok(NotificationResponse.from(n));
    }

    @GetMapping
    @Operation(summary = "Bandeja de entrada del usuario. unreadOnly opcional")
    public ResponseEntity<PagedResponse<NotificationResponse>> inbox(
        JwtAuthenticationToken token,
        @RequestParam(defaultValue = "false") boolean unreadOnly,
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit
    ) {
        PageQuery p = PageQuery.of(offset - 1, limit, SortField.desc("createdAt"));
        return ResponseEntity.ok(PagedResponse.of(
            notificationService.inbox(currentUser(token), unreadOnly, p)
                .map(NotificationResponse::from)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Cantidad de notificaciones no leidas del usuario")
    public ResponseEntity<UnreadCountResponse> unreadCount(JwtAuthenticationToken token) {
        return ResponseEntity.ok(new UnreadCountResponse(
            notificationService.unreadCount(currentUser(token))));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("@authz.canActOnNotification(authentication, #id)")
    @Operation(summary = "Marcar notificacion como leida (solo receptor)")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable UUID id) {
        return ResponseEntity.ok(NotificationResponse.from(notificationService.markRead(id)));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Marcar todas las notificaciones del usuario como leidas")
    public ResponseEntity<UnreadCountResponse> markAllRead(JwtAuthenticationToken token) {
        int updated = notificationService.markAllRead(currentUser(token));
        return ResponseEntity.ok(new UnreadCountResponse(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canActOnNotification(authentication, #id)")
    @Operation(summary = "Eliminar notificacion (solo receptor)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        notificationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
