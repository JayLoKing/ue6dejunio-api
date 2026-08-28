package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The header of one month's curriculum plan for a course.
 *
 * <p>The plan belongs to the course rather than to a single class group: the homeroom teacher's
 * plan covers every subject taught in the course. A specialist's plan covers one, and says so by
 * holding a single {@link CurriculumPlanSubjectEntity}. Both shapes are the same row.
 */
@Entity
@Table(name = "curriculum_plans")
@Getter
@Setter
public class CurriculumPlanEntity {

    @Id
    @UuidGenerator
    @Column(name = "id_curriculum_plan", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_course", nullable = false)
    private CourseEntity course;

    /** Which plan of the year this is — the "Nº 4" the form prints in its heading. */
    @Column(name = "plan_number", nullable = false)
    private Integer planNumber;

    @Column(name = "trimester", nullable = false)
    private Integer trimester;

    /** The month the plan runs, which is why one trimester holds three or four of them. */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "review_observations")
    private String reviewObservations;

    @Column(name = "holistic_objective")
    private String holisticObjective;

    @Column(name = "final_product")
    private String finalProduct;

    @Column(name = "bibliography")
    private String bibliography;

    /**
     * The plan this one was copied from. Teachers of a grade take turns writing the month's plan
     * and each parallel receives a copy it may adjust; this keeps the rotation visible instead of
     * leaving three unrelated-looking plans behind.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_plan_id")
    private CurriculumPlanEntity sourcePlan;

    @OneToMany(mappedBy = "curriculumPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<CurriculumPlanSubjectEntity> subjects = new ArrayList<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "updated_by")
    private UserEntity updatedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
