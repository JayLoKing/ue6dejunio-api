package bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions;

/**
 * The model could not be asked, or answered something this side cannot read.
 *
 * <p>Its own exception rather than a generic failure because the school has to be able to tell the
 * two apart: "no prediction yet" is a student who has not been marked in all four dimensions, and
 * that is normal. This is the other one — the service is down, the token is wrong, or the answer
 * changed shape — and it needs a person, not patience.
 */
public class RiskModelUnavailableException extends DomainException {

    public RiskModelUnavailableException(String message) {
        super(message);
    }

    public RiskModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
