package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollToCourseCommand;
import java.util.UUID;

public interface ICourseEnrollmentService {
    /**
     * Loads a roster. Rows that cannot be seated — a withdrawn student, one already seated in
     * another course of the gestión, a RUDE and a CI of two different students — are left out and
     * reported in the result, so one bad row never costs the rest of the file.
     */
    EnrollResult enroll(EnrollToCourseCommand command);

    /**
     * Enrols one student typed in by hand. Same rules as {@link #enroll}, except that a student
     * seated in another course of the gestión, or a RUDE and a CI of two different students, is
     * refused with a conflict: there is no summary to report a single row in.
     */
    EnrollResult enrollOne(EnrollToCourseCommand command);

    PageResult<CourseStudent> studentsOfCourse(UUID courseId, PageQuery pageQuery);
}
