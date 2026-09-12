package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.institution.Institution;

public record InstitutionResponse(
    String district,
    String school,
    String directorName,
    String department,
    String dependency,
    String shift,
    String educationLevel
) {

    public static InstitutionResponse from(Institution i) {
        return new InstitutionResponse(i.district(), i.school(), i.directorName(),
            i.department(), i.dependency(), i.shift(), i.educationLevel());
    }
}
