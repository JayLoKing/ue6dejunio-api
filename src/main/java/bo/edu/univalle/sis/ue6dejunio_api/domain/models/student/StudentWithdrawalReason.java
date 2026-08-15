package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import java.util.Optional;

public enum StudentWithdrawalReason {
    RETIRO_VOLUNTARIO("Retiro Voluntario"),
    TRANSFERENCIA("Transferencia"),
    OTRO("Otro");

    private final String label;

    StudentWithdrawalReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Accepts either the Spanish label ("Retiro Voluntario") or the enum constant name, case-insensitive. */
    public static Optional<StudentWithdrawalReason> fromRequestValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim();
        for (StudentWithdrawalReason reason : values()) {
            if (reason.label.equalsIgnoreCase(normalized) || reason.name().equalsIgnoreCase(normalized)) {
                return Optional.of(reason);
            }
        }
        return Optional.empty();
    }
}
