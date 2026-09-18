package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.scheduling;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.SweepSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskSweepService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The clock behind RF 30.
 *
 * <p>Nothing but a clock. It owns no rule about risk and no rule about the queue — those live in
 * {@code RiskSweepService}, where they can be tested without waiting for a timer.
 *
 * <p>{@code fixedDelay} and not {@code fixedRate}: the delay is counted from the end of the previous
 * sweep, so a run that takes longer than the interval cannot have the next one start on top of it.
 * At a fixed rate, a slow model would stack ticks until several sweeps were asking about the same
 * subjects at once — and the queue rows they were draining would be deleted out from under each
 * other.
 */
@Component
@ConditionalOnProperty(name = "app.risk.sweep.enabled", havingValue = "true", matchIfMissing = true)
public class RiskSweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(RiskSweepScheduler.class);

    private final IRiskSweepService sweepService;

    public RiskSweepScheduler(IRiskSweepService sweepService) {
        this.sweepService = sweepService;
    }

    /**
     * Drains whatever was marked since the last tick.
     *
     * <p>The initial delay is not the same value as the interval and is not decoration: the model is
     * a separate process that loads TensorFlow on its first call, and a sweep firing while this
     * application is still coming up would spend that first minute waiting on a service that has not
     * finished starting either.
     */
    @Scheduled(
        fixedDelayString = "${app.risk.sweep.interval:PT5M}",
        initialDelayString = "${app.risk.sweep.initial-delay:PT1M}")
    public void sweep() {
        SweepSummary summary = sweepService.sweep();
        if (summary.isEmpty()) {
            // The ordinary case. A line every five minutes saying nothing happened would bury the
            // ones that say something did.
            return;
        }
        log.info("Risk sweep: {} subject(s) taken, {} vector(s) considered, {} skipped, "
                + "{} predicted, {} changed category",
            summary.subjects(), summary.considered(), summary.skipped(),
            summary.predicted(), summary.transitions());
    }
}
