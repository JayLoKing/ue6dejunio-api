package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.mail;

public interface IEmailService {
    void sendWelcomeCredentials(String toEmail, String fullName, String temporaryPassword);
    void sendPasswordReset(String toEmail, String fullName, String resetLink);
}
