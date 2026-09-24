package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseOverview;
import java.util.List;

public record CourseOverviewResponse(
        CourseResponse course,
        List<ClassGroupResponse> classGroups,
        PagedResponse<StudentSummaryResponse> students) {
    public static CourseOverviewResponse from(CourseOverview overview) {
        return new CourseOverviewResponse(
                CourseResponse.from(overview.course()),
                overview.classGroups().stream().map(ClassGroupResponse::from).toList(),
                PagedResponse.of(overview.students().map(StudentSummaryResponse::from)));
    }
}
