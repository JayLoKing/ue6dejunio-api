package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import java.util.List;

/**
 * A course opened: its header, its subjects, its marks and how many children are in it.
 *
 * <p>Two different populations live here on purpose, and mixing them up is the trap. {@code
 * students} is the ACADEMIC roster and keeps a withdrawn student, because the marks they earned
 * before leaving are still owed to the year's records. {@code activeStudents}, {@code males} and
 * {@code females} are the roll as it stands today. On a course somebody left, {@code
 * students.totalElements()} is the larger of the two — show them side by side without saying which
 * is which and a reader sees arithmetic that does not work.
 *
 * <p>{@code males + females} equals {@code activeStudents} only when every student has a gender on
 * record. It is nullable on a student, so the difference is the ones who do not; neither count is
 * padded to close it.
 */
public record CourseOverview(
        Course course,
        List<ClassGroup> classGroups,
        PageResult<StudentTrimesterSummary> students,
        int males,
        int females,
        int activeStudents) {}
