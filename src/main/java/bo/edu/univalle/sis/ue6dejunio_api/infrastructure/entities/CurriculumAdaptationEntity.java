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
@Table(name = "curriculum_adaptations")
@Getter
@Setter
public class CurriculumAdaptationEntity {

    @Id
    @UuidGenerator
    @Column(name = "id_curriculum_adaptation", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_curriculum_plan", nullable = false)
    private CurriculumPlanEntity curriculumPlan;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_student", nullable = false)
    private StudentEntity student;

    /**
     * What the adaptation answers to — a disability, an extraordinary talent, ADHD, ASD or another
     * condition. The form asks for it by name, and the same adapted content means different things
     * depending on it.
     */
    @Column(name = "condition_type", length = 120)
    private String conditionType;

    @Column(name = "adapted_contents")
    private String adaptedContents;

    @Column(name = "adapted_methodology")
    private String adaptedMethodology;

    @Column(name = "adapted_criteria")
    private String adaptedCriteria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private UserEntity updatedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
