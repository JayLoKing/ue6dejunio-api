package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseOverview;
import java.util.List;

/**
 * A course opened.
 *
 * <p>{@code students.total} and {@code activeStudents} answer different questions and are both here
 * on purpose: the first is the academic roster, which keeps a withdrawn student because their marks
 * are still owed to the year's records, and the second is the roll as it stands today. A screen
 * showing a head count wants {@code activeStudents} — it is the one {@code males} and {@code
 * females} were counted over.
 */
public record CourseOverviewResponse(
        CourseResponse course,
        List<ClassGroupResponse> classGroups,
        PagedResponse<StudentSummaryResponse> students,
        int males,
        int females,
        int activeStudents) {
    public static CourseOverviewResponse from(CourseOverview overview) {
        return new CourseOverviewResponse(
                CourseResponse.from(overview.course()),
                overview.classGroups().stream().map(ClassGroupResponse::from).toList(),
                PagedResponse.of(overview.students().map(StudentSummaryResponse::from)),
                overview.males(),
                overview.females(),
                overview.activeStudents());
    }
}
