package bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc;

import java.time.LocalDate;

/**
 * Edits the header of a plan. Every field is optional: a null one is left alone, so the form can
 * save one step at a time without carrying the steps the teacher has not reached yet.
 */
public record UpdatePdcCommand(
        Integer planNumber,
        LocalDate periodStart,
        LocalDate periodEnd,
        String holisticObjective,
        String finalProduct,
        String bibliography) {}
