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
@Table(name = "curriculum_plan_progress")
@Getter
@Setter
public class CurriculumPlanProgressEntity {

    @Id
    @UuidGenerator
    @Column(name = "id_progress", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_curriculum_plan", nullable = false)
    private CurriculumPlanEntity curriculumPlan;

    @Column(name = "progress_date", nullable = false)
    private LocalDate progressDate;

    @Column(name = "advanced_content")
    private String advancedContent;

    @Column(name = "percentage")
    private BigDecimal percentage;

    @Column(name = "observations")
    private String observations;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
