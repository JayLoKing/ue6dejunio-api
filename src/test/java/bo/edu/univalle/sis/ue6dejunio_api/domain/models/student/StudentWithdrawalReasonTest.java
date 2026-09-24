package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StudentWithdrawalReasonTest {

    @Test
    void fromRequestValue_spanishLabel_matches() {
        assertThat(StudentWithdrawalReason.fromRequestValue("Retiro Voluntario"))
                .contains(StudentWithdrawalReason.RETIRO_VOLUNTARIO);
    }

    @Test
    void fromRequestValue_caseInsensitive_matches() {
        assertThat(StudentWithdrawalReason.fromRequestValue("transferencia"))
                .contains(StudentWithdrawalReason.TRANSFERENCIA);
    }

    @Test
    void fromRequestValue_enumConstantName_matches() {
        assertThat(StudentWithdrawalReason.fromRequestValue("OTRO"))
                .contains(StudentWithdrawalReason.OTRO);
    }

    @Test
    void fromRequestValue_invalidValue_empty() {
        assertThat(StudentWithdrawalReason.fromRequestValue("No existe")).isEmpty();
    }

    @Test
    void fromRequestValue_blank_empty() {
        assertThat(StudentWithdrawalReason.fromRequestValue("   ")).isEmpty();
    }

    @Test
    void fromRequestValue_null_empty() {
        assertThat(StudentWithdrawalReason.fromRequestValue(null)).isEmpty();
    }
}
