package bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup;

import java.util.UUID;

/**
 * Which field of knowledge a class group's subject belongs to.
 *
 * <p>Read as its own row rather than hung onto {@link ClassGroup}: only the documents that group by
 * field need it, and widening the class group everywhere would make every listing in the system
 * carry a join it has no use for.
 *
 * @param fieldId      the field's own identity, which is what a document groups by. Nothing stops
 *                     two fields from sharing a {@code displayOrder} — the Director sets it by
 *                     hand and no constraint holds it unique — so grouping by the order instead
 *                     would fold two fields into one row and drop one of their names.
 * @param displayOrder the order the school's own sheets read the fields in, held in the data so
 *                     the Director can change it without a deploy. A sort key, never a key.
 */
public record ClassGroupField(
    UUID classGroupId,
    Integer fieldId,
    String fieldName,
    Integer displayOrder
) {}
