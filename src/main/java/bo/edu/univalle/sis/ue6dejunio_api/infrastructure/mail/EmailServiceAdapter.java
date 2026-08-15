package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mail;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.mail.IEmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Service
public class EmailServiceAdapter implements IEmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceAdapter.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String fromName;

    public EmailServiceAdapter(
        JavaMailSender mailSender,
        @Value("${app.mail.from}") String from,
        @Value("${app.mail.from-name}") String fromName
    ) {
        this.mailSender = mailSender;
        this.from = from;
        this.fromName = fromName;
    }

    @Override
    @Async
    public void sendWelcomeCredentials(String toEmail, String fullName, String temporaryPassword) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Bienvenido(a) al Sistema UE 6 de Junio - Credenciales de acceso");
            helper.setText(buildBody(fullName, toEmail, temporaryPassword), true);
            mailSender.send(message);
            log.info("Email de credenciales enviado a {}", toEmail);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Error enviando email a {}: {}", toEmail, e.getMessage(), e);
            throw new IllegalStateException("No se pudo enviar el correo de bienvenida", e);
        }
    }

    @Override
    @Async
    public void sendPasswordReset(String toEmail, String fullName, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Recuperación de contraseña - Sistema UE 6 de Junio");
            helper.setText(buildPasswordResetBody(fullName, resetLink), true);
            mailSender.send(message);
            log.info("Email de recuperación de contraseña enviado a {}", toEmail);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Error enviando email de recuperación a {}: {}", toEmail, e.getMessage(), e);
            throw new IllegalStateException("No se pudo enviar el correo de recuperación", e);
        }
    }

    private String buildPasswordResetBody(String fullName, String resetLink) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto;">
              <h2 style="color: #1f4e79;">Unidad Educativa 6 de Junio</h2>
              <p>Hola <b>%s</b>,</p>
              <p>Recibimos una solicitud para restablecer tu contrase&ntilde;a. Haz clic en el siguiente enlace para continuar:</p>
              <p style="margin: 16px 0;"><a href="%s" style="background:#1f4e79;color:#fff;padding:10px 16px;text-decoration:none;border-radius:4px;">Restablecer contrase&ntilde;a</a></p>
              <p style="color:#b00;"><b>Importante:</b> este enlace expira en un corto periodo de tiempo y solo puede usarse una vez.</p>
              <p>Si no solicitaste este cambio, puedes ignorar este mensaje.</p>
              <hr>
              <p style="font-size:12px;color:#666;">Mensaje autom&aacute;tico, no responder.</p>
            </body>
            </html>
            """.formatted(fullName, resetLink);
    }

    private String buildBody(String fullName, String email, String password) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto;">
              <h2 style="color: #1f4e79;">Unidad Educativa 6 de Junio</h2>
              <p>Hola <b>%s</b>,</p>
              <p>Se cre&oacute; tu cuenta en el sistema. Inicia sesi&oacute;n con tu correo:</p>
              <table style="border-collapse: collapse; margin: 16px 0;">
                <tr><td style="padding:8px;border:1px solid #ddd;"><b>Email</b></td><td style="padding:8px;border:1px solid #ddd;">%s</td></tr>
                <tr><td style="padding:8px;border:1px solid #ddd;"><b>Contrase&ntilde;a temporal</b></td><td style="padding:8px;border:1px solid #ddd;"><code>%s</code></td></tr>
              </table>
              <p style="color:#b00;"><b>Importante:</b> debes cambiar tu contrase&ntilde;a al iniciar sesi&oacute;n por primera vez.</p>
              <hr>
              <p style="font-size:12px;color:#666;">Mensaje autom&aacute;tico, no responder.</p>
            </body>
            </html>
            """.formatted(fullName, email, password);
    }
}
