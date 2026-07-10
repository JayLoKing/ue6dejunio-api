package bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment;

import java.math.BigDecimal;
import java.util.Map;

public final class AssessmentDimension {
    public static final String BEING = "Being";
    public static final String KNOWING = "Knowing";
    public static final String DOING = "Doing";
    public static final String DECIDING = "Deciding";

    private static final Map<String, BigDecimal> MAX = Map.of(
        BEING, new BigDecimal("10"),
        KNOWING, new BigDecimal("45"),
        DOING, new BigDecimal("40"),
        DECIDING, new BigDecimal("5"));

    public static boolean isValid(String dimension) {
        return MAX.containsKey(dimension);
    }

    public static BigDecimal max(String dimension) {
        BigDecimal m = MAX.get(dimension);
        if (m == null) {
            throw new IllegalArgumentException("Dimension invalida: " + dimension);
        }
        return m;
    }

    private AssessmentDimension() {}
}
