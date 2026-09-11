package bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions;

/**
 * The model was reached, understood the request, and refused it.
 *
 * <p>A subclass of {@link RiskModelUnavailableException} so that callers which only care that no
 * prediction came back keep working unchanged, and so that the one place that does care can tell
 * the two apart.
 *
 * <p>They are different problems for different people. An outage is somebody's to restart. This is
 * a disagreement between the batch this side assembled and the rule the model enforces — the
 * service was running the whole time, and no amount of retrying will change the answer. Reported
 * as "the model is unavailable" it sends a teacher to look at a process that is working, which is
 * the most expensive wrong place to send them.
 *
 * <p>The model states its reason in the response body, and that sentence is carried in the message
 * rather than logged and dropped: it names the offending vectors, and it exists only at the moment
 * the answer arrives.
 */
public class RiskModelRejectedException extends RiskModelUnavailableException {

    public RiskModelRejectedException(String message) {
        super(message);
    }
}
