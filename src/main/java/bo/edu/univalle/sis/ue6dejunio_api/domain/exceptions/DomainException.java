package bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions;

public abstract class DomainException extends RuntimeException {
    protected DomainException(String message) {
        super(message);
    }

    /**
     * For the failures that wrap a lower-level one. The cause never reaches the client — the
     * handler answers with the message — but it is what makes the log line worth reading when the
     * failure came from a socket rather than from a rule.
     */
    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
