package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.RiskModelUnavailableException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskFeatures;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskScore;
import java.util.List;

/**
 * The way out to the model.
 *
 * <p>One direction only: this API calls the model, and the model never calls back. There is no
 * webhook and no callback URL, so the model service can be down, restarted or moved without this
 * side holding a registration that has gone stale.
 */
public interface IRiskModelClient {

    /**
     * Scores a whole batch in one call.
     *
     * <p>Batched rather than one call per student on purpose: a full sweep of the school is in the
     * thousands of vectors, and one round trip each turns a sweep into an afternoon.
     *
     * @param batch complete vectors only — {@link RiskFeatures#isComplete()} is the caller's job,
     *     because the model rejects an incomplete one with a 422 and a rejected batch costs every
     *     vector in it
     * @return one score per vector, <b>in the same order as the input</b>. That positional pairing
     *     is the only thing tying a score back to its student, so an answer of a different length
     *     is an error, not a partial result.
     * @throws RiskModelUnavailableException when the model could not be reached or answered in a
     *     way this side cannot read
     */
    List<RiskScore> predictBatch(List<RiskFeatures> batch);
}
