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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * One subject's block inside a plan: its learning objective, its table of weekly entries, and the
 * general adaptations that close it.
 *
 * <p>The block points at a class group rather than a subject because the class group already
 * carries subject, course and teacher together — the three things the printed block names.
 */
@Entity
@Table(name = "curriculum_plan_subjects")
@Getter
@Setter
public class CurriculumPlanSubjectEntity {

    @Id
    @UuidGenerator
    @Column(name = "id_plan_subject", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_curriculum_plan", nullable = false)
    private CurriculumPlanEntity curriculumPlan;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_class_group", nullable = false)
    private ClassGroupEntity classGroup;

    @Column(name = "learning_objective")
    private String learningObjective;

    /**
     * The list of strategies the form prints under "ADAPTACIONES CURRICULARES": what the teacher
     * does for learning difficulties or differing paces across the class. Distinct from the
     * significant adaptations, which name one student each and live in their own table.
     */
    @Column(name = "general_adaptations")
    private String generalAdaptations;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @OneToMany(mappedBy = "planSubject", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<CurriculumPlanEntryEntity> entries = new ArrayList<>();
}
