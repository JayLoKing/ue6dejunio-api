package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportDraft;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportSheet;
import java.util.UUID;

/**
 * The informe pedagógico: the one report of the school's five that a person writes rather than the
 * system derives.
 *
 * <p>Kept apart from {@link IGradebookService} on purpose. Every method there answers a question
 * about marks somebody already entered; this one owns a document with its own table, its own write
 * path and its own owner — the homeroom teacher, who signs it.
 */
public interface IPedagogicalReportService {

    /**
     * The whole sheet, derived and written parts merged, whether or not a teacher has started it.
     *
     * <p>A report nobody has written still has sections I, III and the marks of IV, and that is
     * what the teacher opens the blank form against: the statistics and the list of who failed are
     * the reason they are about to write anything.
     */
    PedagogicalReportSheet sheet(UUID courseId, int trimester);

    /**
     * Saves the written half and answers with the sheet as it now reads.
     *
     * <p>Returning the rendered sheet and not the stored draft: what the teacher typed is only half
     * of the page in front of them, and a save that answered with the other half missing would make
     * the screen ask for it again.
     */
    PedagogicalReportSheet save(UUID courseId, int trimester, PedagogicalReportDraft draft);
}
