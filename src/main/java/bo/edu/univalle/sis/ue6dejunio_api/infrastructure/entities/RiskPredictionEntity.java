package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The standing prediction for one student in one subject.
 *
 * <p>The two foreign keys are mapped as plain uuids rather than as {@code @ManyToOne}, which is the
 * one place this entity departs from the rest of the schema. Nothing here ever navigates to the
 * student or the class group: a prediction is written from ids the run already holds, and the two
 * names a reader needs come from an explicit join in the adapter, selected once for the whole list.
 * A relation would buy nothing and cost either an eager fetch of two aggregates per row or a proxy
 * that initialises the moment anyone asks for the id.
 */
@Entity
@Table(name = "risk_predictions")
@Getter
@Setter
public class RiskPredictionEntity {

    @Id
    @UuidGenerator
    @Column(name = "id_risk_prediction", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "id_student", nullable = false)
    private UUID studentId;

    @Column(name = "id_class_group", nullable = false)
    private UUID classGroupId;

    @Column(name = "trimester", nullable = false)
    private Integer trimester;

    /** Stored as the model spells it, so nothing translates between the answer and the column. */
    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    @Column(name = "p_fail", nullable = false)
    private BigDecimal pFail;

    @Column(name = "p_outstanding", nullable = false)
    private BigDecimal pOutstanding;

    @Column(name = "is_attended", nullable = false)
    private boolean attended;

    /**
     * The feature vector the model was given, held as the JSON text it already is.
     *
     * <p>{@code SqlTypes.JSON} rather than a plain varchar mapping: without it Hibernate sends a
     * {@code text} parameter and Postgres refuses to assign it to a {@code jsonb} column.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features_analyzed")
    private String featuresAnalyzed;

    @Column(name = "predicted_at", nullable = false)
    private LocalDateTime predictedAt;

    /**
     * When this prediction last caused a notification to its teacher. Null means never.
     *
     * <p>Not a delivery receipt — {@code notifications} already holds what was sent. This exists
     * only to stop the same student being announced twice in one day now that the sweep runs within
     * minutes of every save, where before somebody had to press a button.
     */
    @Column(name = "last_notified_at")
    private LocalDateTime lastNotifiedAt;
}
