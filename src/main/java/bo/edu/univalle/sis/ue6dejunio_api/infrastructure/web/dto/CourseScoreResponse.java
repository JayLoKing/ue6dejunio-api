package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseScoreRow;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CourseScoreResponse(
    UUID studentId,
    String fullName,
    UUID enrollmentId,
    List<Item> scores
) {
    public record Item(
        UUID id,
        Integer trimester,
        BigDecimal scoreBeing,
        BigDecimal scoreKnowing,
        BigDecimal scoreDoing,
        BigDecimal scoreDeciding,
        BigDecimal totalScore
    ) {}

    public static CourseScoreResponse from(CourseScoreRow r) {
        List<Item> items = r.scores().stream()
            .map(s -> new Item(s.id(), s.trimester(),
                s.scoreBeing(), s.scoreKnowing(), s.scoreDoing(), s.scoreDeciding(), s.totalScore()))
            .toList();
        return new CourseScoreResponse(r.studentId(), r.fullName(), r.enrollmentId(), items);
    }
}
