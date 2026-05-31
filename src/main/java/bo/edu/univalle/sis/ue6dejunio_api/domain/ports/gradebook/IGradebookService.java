package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseAttendanceRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseScoreRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface IGradebookService {
    Page<CourseAttendanceRow> courseAttendance(Integer gradeId, Integer parallelId,
                                                LocalDate date, Pageable pageable);
    Page<CourseScoreRow> classGroupScores(UUID classGroupId, Integer trimester, Pageable pageable);
}
