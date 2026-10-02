package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "academic_years")
@Getter
@Setter
public class AcademicYearEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_academic_year")
    private Integer id;

    @Column(name = "year", nullable = false, unique = true)
    private Integer year;

    /** The year the system works in when a request names none. At most one row (DB-enforced). */
    @Column(name = "active", nullable = false)
    private boolean active;

    /** When the year was closed; null while it is open. Independent of {@link #active}. */
    @Column(name = "closed_at")
    private Instant closedAt;
}
