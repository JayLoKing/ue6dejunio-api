package bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * One standing request to re-predict a subject for a trimester.
 *
 * <p>The unit is the class group and not the student on purpose. {@code IRiskFeatureDomain} reads
 * marks, planned criteria and attendance for a collection of class groups in four bounded queries;
 * narrowing a request to one student would make the sweep read the whole group anyway and then
 * throw away every vector but one.
 *
 * @param markedAt when the most recent change this row stands for arrived. The sweep clears only
 *     the marks it actually swept, by comparing this against the instant it read them — a save that
 *     lands while the model is answering moves this forward, survives the clear, and is picked up
 *     by the following tick.
 */
public record SweepTarget(UUID classGroupId, int trimester, LocalDateTime markedAt) {

    public SweepTarget {
        Objects.requireNonNull(classGroupId, "classGroupId");
    }
}
