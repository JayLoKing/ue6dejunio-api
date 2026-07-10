package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseAttendanceRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentTrimesterSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface IGradebookService {
    StudentTrimesterSummary studentSummary(UUID courseEnrollmentId, Integer trimester);
    Page<StudentTrimesterSummary> centralizer(UUID courseId, Integer trimester, Pageable pageable);
    Page<CourseAttendanceRow> courseAttendance(UUID courseId, LocalDate date, Pageable pageable);
}
