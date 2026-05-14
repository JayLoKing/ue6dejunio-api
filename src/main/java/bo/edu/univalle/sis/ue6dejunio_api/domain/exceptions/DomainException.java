package bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions;

public abstract class DomainException extends RuntimeException {
    protected DomainException(String message) {
        super(message);
    }
}
