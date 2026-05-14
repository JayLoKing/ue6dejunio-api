package bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions;

public class UserInactiveException extends DomainException {
    public UserInactiveException() {
        super("Usuario inactivo. Contacte al director.");
    }
}
