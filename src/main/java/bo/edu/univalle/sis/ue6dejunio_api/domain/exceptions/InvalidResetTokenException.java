package bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions;

public class InvalidResetTokenException extends DomainException {
    public InvalidResetTokenException() {
        super("Token de recuperación inválido o expirado");
    }
}
