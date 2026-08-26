package bo.edu.univalle.sis.ue6dejunio_api.domain.models.common;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;

/**
 * One field of an ordering, as a property name plus its direction. The adapter is what turns the
 * name into a column; the domain only states which attribute the ordering is by.
 */
public record SortField(String property, SortDirection direction) {

    public SortField {
        if (property == null || property.isBlank()) {
            throw new ValidationException("A sort field must name a property");
        }
        if (direction == null) {
            throw new ValidationException("A sort field must have a direction");
        }
    }

    public static SortField asc(String property) {
        return new SortField(property, SortDirection.ASC);
    }

    public static SortField desc(String property) {
        return new SortField(property, SortDirection.DESC);
    }
}
