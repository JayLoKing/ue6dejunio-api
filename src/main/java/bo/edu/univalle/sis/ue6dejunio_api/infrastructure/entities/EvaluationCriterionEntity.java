package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "evaluation_criteria")
@Getter
@Setter
public class EvaluationCriterionEntity {

    @Id
    @UuidGenerator
    @Column(name = "id_criterion", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_class_group", nullable = false)
    private ClassGroupEntity classGroup;

    @Column(name = "trimester", nullable = false)
    private Integer trimester;

    @Column(name = "dimension", nullable = false, length = 20)
    private String dimension;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    // Null marks a criterion scored directly; set, it names the activity whose items feed it.
    @Column(name = "activity_name", length = 150)
    private String activityName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_curriculum_plan")
    private CurriculumPlanEntity curriculumPlan;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
