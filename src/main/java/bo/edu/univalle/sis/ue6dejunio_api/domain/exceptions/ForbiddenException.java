package bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions;

/** The caller is known and allowed in, but not allowed to do this one thing (403). */
public class ForbiddenException extends DomainException {
    public ForbiddenException(String message) {
        super(message);
    }
}
