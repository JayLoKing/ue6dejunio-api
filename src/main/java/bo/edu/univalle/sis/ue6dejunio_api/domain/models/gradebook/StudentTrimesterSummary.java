package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StudentTrimesterSummary(
        UUID courseEnrollmentId,
        UUID studentId,
        String fullName,
        Integer trimester,
        List<SubjectScore> subjects,
        BigDecimal generalAverage) {}
