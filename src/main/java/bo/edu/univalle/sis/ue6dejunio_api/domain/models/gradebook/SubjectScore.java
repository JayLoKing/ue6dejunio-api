package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.math.BigDecimal;
import java.util.UUID;

public record SubjectScore(
        UUID classGroupId, String subjectName, BigDecimal total, boolean graded) {}
