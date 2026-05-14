package bo.edu.univalle.sis.ue6dejunio_api.domain.models.user;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.role.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class User {
    private UUID id;
    private String ci;
    private String names;
    private String lastNames;
    private String phone;
    private String email;
    private String username;
    @JsonIgnore
    private String password;
    private boolean mustChangePassword;
    private Role role;
    private boolean active;
    private LocalDateTime createdAt;

    public String fullName() {
        return names + " " + lastNames;
    }
}
