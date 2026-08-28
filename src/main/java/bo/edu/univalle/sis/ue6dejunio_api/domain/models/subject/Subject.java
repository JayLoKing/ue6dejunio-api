package bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject;

import java.util.UUID;

/**
 * A subject of the curriculum. It carries the area it belongs to because the curriculum plan
 * groups its blocks by area, and the catalogue is where that grouping is chosen.
 */
public record Subject(UUID id, String name, Integer areaId, String areaName,
                      boolean technical, boolean active) {}
