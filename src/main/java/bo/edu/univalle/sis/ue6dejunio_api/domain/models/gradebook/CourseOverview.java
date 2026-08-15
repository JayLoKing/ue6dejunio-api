package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import org.springframework.data.domain.Page;

import java.util.List;

public record CourseOverview(
    Course course,
    List<ClassGroup> classGroups,
    Page<StudentTrimesterSummary> students
) {}
