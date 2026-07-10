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
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "academic_scores")
@Getter
@Setter
public class AcademicScoreEntity {

    @Id
    @UuidGenerator
    @Column(name = "id_academic_score", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_course_enrollment", nullable = false)
    private CourseEnrollmentEntity courseEnrollment;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_class_group", nullable = false)
    private ClassGroupEntity classGroup;

    @Column(name = "trimester")
    private Integer trimester;

    @Column(name = "score_being")
    private BigDecimal scoreBeing;

    @Column(name = "score_knowing")
    private BigDecimal scoreKnowing;

    @Column(name = "score_doing")
    private BigDecimal scoreDoing;

    @Column(name = "score_deciding")
    private BigDecimal scoreDeciding;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "total_score", insertable = false, updatable = false)
    private BigDecimal totalScore;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "digital_signature_hash")
    private String digitalSignatureHash;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
