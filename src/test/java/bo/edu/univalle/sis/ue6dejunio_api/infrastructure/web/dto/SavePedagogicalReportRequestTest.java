package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportDraft;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The one mapping in this request where null and empty may not be flattened into each other.
 *
 * <p>Section IV is prose a teacher typed and the schema keeps no second copy of it. A body that
 * omits {@code failingStudents} says nothing about that section; a body that sends {@code []} says
 * it is now empty, and the adapter deletes accordingly. Collapsing the first into the second turns
 * the plainest possible save — the prose and nothing else — into the loss of every paragraph on the
 * report.
 */
class SavePedagogicalReportRequestTest {

    @Test
    void toDraft_fieldOmitted_leavesTheNotesNullSoTheSaveSaysNothingAboutSectionFour() {
        PedagogicalReportDraft draft =
                new SavePedagogicalReportRequest("Logros", "Dificultades", null).toDraft();

        assertThat(draft.notes()).isNull();
        assertThat(draft.achievements()).isEqualTo("Logros");
        assertThat(draft.difficulties()).isEqualTo("Dificultades");
    }

    @Test
    void toDraft_fieldSentEmpty_keepsItEmptySoSectionFourIsCleared() {
        PedagogicalReportDraft draft =
                new SavePedagogicalReportRequest(null, null, List.of()).toDraft();

        assertThat(draft.notes()).isNotNull().isEmpty();
    }

    @Test
    void toDraft_carriesEachParagraphOntoTheEnrolmentItNames() {
        UUID enrollmentId = UUID.randomUUID();

        PedagogicalReportDraft draft =
                new SavePedagogicalReportRequest(
                                null,
                                null,
                                List.of(
                                        new SavePedagogicalReportRequest.FailingStudentNote(
                                                enrollmentId, "Refuerzo", "Cuaderno")))
                        .toDraft();

        assertThat(draft.notes()).hasSize(1);
        assertThat(draft.notes().get(0).courseEnrollmentId()).isEqualTo(enrollmentId);
        assertThat(draft.notes().get(0).actions()).isEqualTo("Refuerzo");
        assertThat(draft.notes().get(0).verificationSource()).isEqualTo("Cuaderno");
    }
}
