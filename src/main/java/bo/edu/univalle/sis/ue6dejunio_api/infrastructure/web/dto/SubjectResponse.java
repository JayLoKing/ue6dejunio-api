package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.Subject;

import java.util.UUID;

public record SubjectResponse(UUID id, String name, String area, boolean active) {
    public static SubjectResponse from(Subject s) {
        return new SubjectResponse(s.id(), s.name(), s.area(), s.active());
    }
}
