package bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion;

import java.math.BigDecimal;

public record UpdateCriterionCommand(
    String name,
    BigDecimal maxWeight
) {}
