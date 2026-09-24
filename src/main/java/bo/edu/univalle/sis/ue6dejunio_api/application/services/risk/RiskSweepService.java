package bo.edu.univalle.sis.ue6dejunio_api.application.services.risk;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.SweepSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.SweepTarget;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionService.RunSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskSweepQueueDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskSweepService;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Spends the queue: everything marked since the last tick, in as few calls to the model as
 * possible.
 *
 * <p>Carries no {@code @Transactional} of its own, and that is deliberate. A sweep is a read of the
 * queue, an HTTP call that can take a minute, and a write — holding a database transaction open
 * across the model's answer would pin a connection for the duration of somebody else's process. The
 * pieces that must be atomic already are: the upsert inside {@code predictClassGroups}, and the
 * clear below.
 */
@Service
public class RiskSweepService implements IRiskSweepService {

    private static final Logger log = LoggerFactory.getLogger(RiskSweepService.class);

    private final IRiskSweepQueueDomain queue;
    private final IRiskPredictionService predictions;
    private final int batchSize;

    /**
     * @param batchSize the most subjects one tick will take. A bound rather than the whole queue,
     *     because a sweep that woke to a school-sized backlog would hold the model for as long as
     *     it took to drain it. What it leaves behind is taken by the next tick, oldest first, so
     *     nothing starves.
     */
    public RiskSweepService(
            IRiskSweepQueueDomain queue,
            IRiskPredictionService predictions,
            @Value("${app.risk.sweep.batch-size:200}") int batchSize) {
        this.queue = queue;
        this.predictions = predictions;
        // A zero or negative batch would make the query `LIMIT 0` and the sweep would go silent
        // forever: no error, no log, and no prediction ever again. Refused at startup, where a
        // typo in an environment variable is still something somebody is looking at.
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "app.risk.sweep.batch-size must be at least 1, but was " + batchSize);
        }
        this.batchSize = batchSize;
    }

    @Override
    public SweepSummary sweep() {
        List<SweepTarget> taken = queue.pending(batchSize);
        if (taken.isEmpty()) {
            return SweepSummary.empty();
        }

        int considered = 0;
        int skipped = 0;
        int predicted = 0;
        int transitions = 0;

        for (Map.Entry<Integer, List<SweepTarget>> group : byTrimester(taken).entrySet()) {
            RunSummary run = runOne(group.getKey(), group.getValue());
            if (run == null) {
                continue;
            }
            considered += run.considered();
            skipped += run.skipped();
            predicted += run.predicted();
            transitions += run.changed();
        }

        return new SweepSummary(taken.size(), considered, skipped, predicted, transitions);
    }

    /**
     * One trimester's subjects, predicted and then cleared.
     *
     * <p>Cleared per trimester rather than once at the end, so the two failure directions are both
     * handled: a trimester the model refused keeps its marks for the next tick, and a trimester
     * that succeeded is not swept again for having shared a drain with it.
     *
     * @return what the run did, or {@code null} if the model could not answer for this trimester
     */
    private RunSummary runOne(int trimester, List<SweepTarget> targets) {
        Set<UUID> classGroupIds =
                targets.stream()
                        .map(SweepTarget::classGroupId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        try {
            RunSummary run = predictions.predictClassGroups(classGroupIds, trimester);
            // Bounded by the newest mark this sweep actually read. A save that landed while the
            // model was answering carries a later instant, survives this, and is taken next tick —
            // without it, that teacher's change would be deleted having never been predicted.
            queue.clearSwept(classGroupIds, trimester, newestMark(targets));
            return run;
        } catch (RuntimeException ex) {
            // Not rethrown. The only caller is a scheduler, where an exception becomes a stack
            // trace
            // in place of the one sentence that matters: which classrooms are still waiting.
            log.error(
                    "The risk sweep could not predict trimester {} for {} subject(s); their marks "
                            + "stay queued and the next tick will retry them",
                    trimester,
                    classGroupIds.size(),
                    ex);
            return null;
        }
    }

    /**
     * The subjects grouped by the trimester they were marked for.
     *
     * <p>Insertion-ordered so two drains of the same queue ask the model in the same order, which
     * is what makes a failing sweep reproducible.
     */
    private static Map<Integer, List<SweepTarget>> byTrimester(List<SweepTarget> targets) {
        return targets.stream()
                .collect(
                        Collectors.groupingBy(
                                SweepTarget::trimester, LinkedHashMap::new, Collectors.toList()));
    }

    private static LocalDateTime newestMark(List<SweepTarget> targets) {
        return targets.stream()
                .map(SweepTarget::markedAt)
                .max(Comparator.naturalOrder())
                .orElseThrow();
    }
}
