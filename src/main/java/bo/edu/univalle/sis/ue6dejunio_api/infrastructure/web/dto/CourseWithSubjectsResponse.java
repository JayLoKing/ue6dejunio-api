package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.CourseWithSubjects;

import java.util.List;

public record CourseWithSubjectsResponse(
    CourseResponse course,
    List<ClassGroupResponse> classGroups
) {
    public static CourseWithSubjectsResponse from(CourseWithSubjects cws) {
        return new CourseWithSubjectsResponse(
            CourseResponse.from(cws.course()),
            cws.classGroups().stream().map(ClassGroupResponse::from).toList());
    }
}
