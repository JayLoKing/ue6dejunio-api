package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "assessment_scores")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class AssessmentScoreEntity {

    @Id
    @UuidGenerator
    @Column(name = "id_assessment_score", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_course_enrollment", nullable = false)
    private CourseEnrollmentEntity courseEnrollment;

    // Exactly one of event / criterion is set. Enforced in the database by chk_score_target and,
    // before the write reaches it, by AssessmentScoreService.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_assessment_event")
    private AssessmentEventEntity event;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_criterion")
    private EvaluationCriterionEntity criterion;

    @Column(name = "score", nullable = false)
    private BigDecimal score;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
