package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "courses")
@Getter
@Setter
public class CourseEntity {

    @Id
    @UuidGenerator
    @Column(name = "id_course", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_grade", nullable = false)
    private GradeEntity grade;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_parallel", nullable = false)
    private ParallelEntity parallel;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_academic_year", nullable = false)
    private AcademicYearEntity academicYear;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_homeroom_teacher")
    private UserEntity homeroomTeacher;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
