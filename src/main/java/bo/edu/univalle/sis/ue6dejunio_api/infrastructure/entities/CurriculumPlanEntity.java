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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

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
    @JoinColumn(name = "id_class_group", nullable = false)
    private ClassGroupEntity classGroup;

    @Column(name = "trimester", nullable = false)
    private Integer trimester;

    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "review_observations")
    private String reviewObservations;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "holistic_objective")
    private String holisticObjective;

    @Column(name = "learning_objective")
    private String learningObjective;

    @Column(name = "contents")
    private String contents;

    @Column(name = "practice_activities")
    private String practiceActivities;

    @Column(name = "theory_activities")
    private String theoryActivities;

    @Column(name = "valuation_activities")
    private String valuationActivities;

    @Column(name = "production_activities")
    private String productionActivities;

    @Column(name = "resources")
    private String resources;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "criteria_being")
    private String criteriaBeing;

    @Column(name = "criteria_knowing")
    private String criteriaKnowing;

    @Column(name = "criteria_doing")
    private String criteriaDoing;

    @Column(name = "criteria_deciding")
    private String criteriaDeciding;

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
