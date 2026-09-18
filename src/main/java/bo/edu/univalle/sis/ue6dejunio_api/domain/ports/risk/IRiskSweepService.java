package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.SweepSummary;

/**
 * Drains the queue of subjects whose model inputs changed.
 *
 * <p>This is the half of RF 30 that makes "predict on every change" survivable. The other half is
 * {@code IRiskSweepQueueDomain}, where a save leaves a mark and returns immediately. Here the marks
 * are collected and spent in as few calls to the model as the queue allows.
 */
public interface IRiskSweepService {

    /**
     * Predicts everything standing in the queue and clears what it swept.
     *
     * <p>Clears <b>after</b> the model answers, never before: a sweep that emptied the queue first
     * and then failed would drop the changes it was holding, and nothing anywhere would say a
     * classroom went un-predicted. A failed sweep leaves its marks and the next tick retries them.
     *
     * @return what was done, for the log
     */
    SweepSummary sweep();
}
