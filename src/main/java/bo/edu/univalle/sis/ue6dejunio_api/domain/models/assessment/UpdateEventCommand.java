package bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment;

import java.math.BigDecimal;

public record UpdateEventCommand(String title, String description, BigDecimal maxScore) {}
