package bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog;

import java.time.LocalDate;
import java.util.UUID;

public record TrimesterPeriodItem(UUID id, int trimester, LocalDate startDate, LocalDate endDate) {}
