package bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions;

public class InvalidCredentialsException extends DomainException {
    public InvalidCredentialsException() {
        super("Credenciales inválidas");
    }
}
