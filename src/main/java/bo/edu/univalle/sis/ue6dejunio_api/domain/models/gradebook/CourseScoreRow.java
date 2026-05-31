package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;

import java.util.List;
import java.util.UUID;

public record CourseScoreRow(
    UUID studentId,
    String fullName,
    UUID enrollmentId,
    List<AcademicScore> scores
) {}
