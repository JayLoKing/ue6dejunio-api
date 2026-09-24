package bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc;

import java.util.UUID;

/**
 * One week's row of a subject's table: the content taught, the four moments it is taught through,
 * and what the teacher looks at to judge it.
 *
 * <p>{@code weekLabel} is written as the form writes it — "Semana 1", or a span like "Semanas 3 y
 * 4". {@code periods} is absent when the teacher left the cell blank.
 */
public record PdcEntry(
        UUID id,
        String weekLabel,
        String contents,
        String practice,
        String theory,
        String valuation,
        String production,
        String resources,
        Integer periods,
        String criteriaBeing,
        String criteriaKnowing,
        String criteriaDoing,
        Integer displayOrder) {}
