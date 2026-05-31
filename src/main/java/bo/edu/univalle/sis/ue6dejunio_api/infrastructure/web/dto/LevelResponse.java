package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.Level;

public record LevelResponse(Integer id, String name) {
    public static LevelResponse from(Level l) { return new LevelResponse(l.id(), l.name()); }
}
