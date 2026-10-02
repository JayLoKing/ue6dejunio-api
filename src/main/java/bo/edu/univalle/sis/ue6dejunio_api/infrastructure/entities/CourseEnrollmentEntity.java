package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "course_enrollments")
@Getter
@Setter
public class CourseEnrollmentEntity {

    @Id
    @UuidGenerator
    @Column(name = "id_course_enrollment", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_student", nullable = false)
    private StudentEntity student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_course", nullable = false)
    private CourseEntity course;

    @Column(name = "enrollment_date")
    private LocalDate enrollmentDate;

    @Column(name = "status", length = 20)
    private String status;

    /** Frozen when the year closes; see V23. Null until then, and for withdrawn or ungraded. */
    @Column(name = "final_average", precision = 5, scale = 2)
    private BigDecimal finalAverage;

    /** {@code APPROVED} or {@code FAILED}; same lifecycle as {@link #finalAverage}. */
    @Column(name = "final_result", length = 10)
    private String finalResult;

    /** When the enrollment's year was closed; cleared again when the year is reopened. */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
