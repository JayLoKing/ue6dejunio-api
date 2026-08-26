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
@Table(name = "assessment_events")
@Getter
@Setter
public class AssessmentEventEntity {

    @Id
    @UuidGenerator
    @Column(name = "id_assessment_event", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_criterion", nullable = false)
    private EvaluationCriterionEntity criterion;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
