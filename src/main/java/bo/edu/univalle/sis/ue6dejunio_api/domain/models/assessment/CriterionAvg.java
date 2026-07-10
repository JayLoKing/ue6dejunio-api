package bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment;

import java.math.BigDecimal;

public record CriterionAvg(String dimension, BigDecimal maxWeight, BigDecimal avgScore) {}
