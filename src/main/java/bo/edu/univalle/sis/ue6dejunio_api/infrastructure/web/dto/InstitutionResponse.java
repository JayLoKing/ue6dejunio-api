package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.institution.Institution;

public record InstitutionResponse(String district, String school, String directorName) {

    public static InstitutionResponse from(Institution i) {
        return new InstitutionResponse(i.district(), i.school(), i.directorName());
    }
}
