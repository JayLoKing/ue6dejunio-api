package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.user;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.CreateUserCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UpdateUserCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UsersList;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateUserRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.UpdateUserRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Gestion de usuarios (Director)")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final IUserService userService;

    public UserController(IUserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @Operation(summary = "Registrar usuario")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request,
                                               UriComponentsBuilder uriBuilder) {
        User created = userService.create(new CreateUserCommand(
            request.ci(), request.names(), request.lastNames(),
            request.phone(), request.email(), request.roleId(), request.technical()
        ));
        URI location = uriBuilder.path("/api/users/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(UserResponse.from(created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener usuario por id")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(UserResponse.from(userService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "Listar usuarios paginados. offset=pagina (1-indexed), limit=cantidad")
    public ResponseEntity<PagedResponse<UsersList>> list(
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "asc") @Pattern(regexp = "(?i)asc|desc") String sort,
        JwtAuthenticationToken token
    ) {
        Sort.Direction dir = "desc".equalsIgnoreCase(sort) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(offset - 1, limit, Sort.by(dir, "lastNames", "names"));
        UUID currentUserId = UUID.fromString(token.getToken().getSubject());
        return ResponseEntity.ok(PagedResponse.of(userService.list(pageable, search, currentUserId)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar usuario")
    public ResponseEntity<UserResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateUserRequest request) {
        User updated = userService.update(id, new UpdateUserCommand(
            request.names(), request.lastNames(), request.phone(),
            request.roleId(), request.active()
        ));
        return ResponseEntity.ok(UserResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Dar de baja a usuario")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        userService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
