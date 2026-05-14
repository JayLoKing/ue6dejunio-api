package bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions;

public class ResourceNotFoundException extends DomainException {
    public ResourceNotFoundException(String resource, Object id) {
        super("%s no encontrado: %s".formatted(resource, id));
    }
}
