package bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod;

import java.time.LocalDate;

public record UpdateTrimesterPeriodCommand(LocalDate startDate, LocalDate endDate) {}
