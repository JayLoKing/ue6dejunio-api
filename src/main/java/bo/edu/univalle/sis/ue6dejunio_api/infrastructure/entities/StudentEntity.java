package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

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

    /** What the Director wrote, for the category that says nothing on its own. */
    @Column(name = "status_note")
    private String statusNote;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    /**
     * Who last changed the status.
     *
     * <p>LAZY, and fetched by name where it is actually read. EAGER on a {@code @ManyToOne} does
     * not become a join on a derived query — Hibernate runs the root query and then one select per
     * distinct author, which is a query per row on the enrolment import that looks students up in
     * batches. The queries that need the author ask for it with an entity graph instead.
     *
     * <p>Nullable: the column is {@code ON DELETE SET NULL}, so a removed account leaves the change
     * recorded without an author rather than taking the student's row with it.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_changed_by")
    private UserEntity statusChangedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
