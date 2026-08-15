package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

public record ForgotPasswordResponse(String message) {
    private static final String GENERIC_MESSAGE =
        "Si el correo existe en nuestro sistema, recibirás un enlace para restablecer tu contraseña.";

    public static ForgotPasswordResponse generic() {
        return new ForgotPasswordResponse(GENERIC_MESSAGE);
    }
}
