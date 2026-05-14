package bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions;

public class DuplicateResourceException extends DomainException {
    public DuplicateResourceException(String field, String value) {
        super("Recurso duplicado: %s=%s".formatted(field, value));
    }
}
