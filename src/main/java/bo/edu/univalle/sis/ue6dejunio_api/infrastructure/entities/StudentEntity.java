package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "students")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentEntity {

    @Id
    @UuidGenerator
    @Column(name = "id_student", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "rude_code", nullable = false, unique = true, length = 20)
    private String rudeCode;

    @Column(name = "identity_card", nullable = false, unique = true, length = 15)
    private String identityCard;

    @Column(name = "names", nullable = false, length = 100)
    private String names;

    @Column(name = "last_names", nullable = false, length = 100)
    private String lastNames;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "gender", length = 1)
    private String gender;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
