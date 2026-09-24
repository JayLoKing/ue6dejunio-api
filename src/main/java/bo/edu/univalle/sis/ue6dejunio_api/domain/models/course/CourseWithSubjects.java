package bo.edu.univalle.sis.ue6dejunio_api.domain.models.course;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import java.util.List;

public record CourseWithSubjects(Course course, List<ClassGroup> classGroups) {}
