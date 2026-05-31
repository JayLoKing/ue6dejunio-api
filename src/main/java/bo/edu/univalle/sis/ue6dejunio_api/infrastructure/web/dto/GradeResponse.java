package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.Grade;

public record GradeResponse(Integer id, String name, Integer levelId, String levelName) {
    public static GradeResponse from(Grade g) {
        return new GradeResponse(g.id(), g.name(), g.levelId(), g.levelName());
    }
}
