package bo.edu.univalle.sis.ue6dejunio_api.application.services.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseAttendanceRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentTrimesterSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.SubjectScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook.IGradebookService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class GradebookService implements IGradebookService {

    private final ICourseEnrollmentDomain enrollmentDomain;
    private final IScoreDomain scoreDomain;
    private final IAttendanceDomain attendanceDomain;

    public GradebookService(ICourseEnrollmentDomain enrollmentDomain,
                            IScoreDomain scoreDomain,
                            IAttendanceDomain attendanceDomain) {
        this.enrollmentDomain = enrollmentDomain;
        this.scoreDomain = scoreDomain;
        this.attendanceDomain = attendanceDomain;
    }

    @Override
    @Transactional(readOnly = true)
    public StudentTrimesterSummary studentSummary(UUID courseEnrollmentId, Integer trimester) {
        CourseStudent cs = enrollmentDomain.courseStudentById(courseEnrollmentId)
            .orElseThrow(() -> new ResourceNotFoundException("CourseEnrollment", courseEnrollmentId));
        return buildSummary(courseEnrollmentId, cs.studentId(), cs.fullName(), trimester);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StudentTrimesterSummary> centralizer(UUID courseId, Integer trimester, Pageable pageable) {
        return enrollmentDomain.studentsByCourse(courseId, pageable)
            .map(cs -> buildSummary(cs.courseEnrollmentId(), cs.studentId(), cs.fullName(), trimester));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CourseAttendanceRow> courseAttendance(UUID courseId, LocalDate date, Pageable pageable) {
        return enrollmentDomain.studentsByCourse(courseId, pageable).map(cs -> {
            List<Attendance> att = attendanceDomain.dailyByCourseEnrollment(cs.courseEnrollmentId());
            if (date != null) {
                att = att.stream().filter(a -> date.equals(a.date())).toList();
            }
            return new CourseAttendanceRow(cs.courseEnrollmentId(), cs.studentId(), cs.fullName(), att);
        });
    }

    private StudentTrimesterSummary buildSummary(UUID courseEnrollmentId, UUID studentId,
                                                 String fullName, Integer trimester) {
        List<AcademicScore> scores = scoreDomain.findByCourseEnrollment(courseEnrollmentId).stream()
            .filter(s -> trimester.equals(s.trimester()))
            .toList();
        List<SubjectScore> subjects = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (AcademicScore s : scores) {
            BigDecimal total = s.totalScore();
            boolean graded = total != null;
            sum = sum.add(graded ? total : BigDecimal.ZERO);
            subjects.add(new SubjectScore(s.classGroupId(), s.subjectName(), total, graded));
        }
        BigDecimal general = subjects.isEmpty()
            ? null
            : sum.divide(BigDecimal.valueOf(subjects.size()), 2, RoundingMode.HALF_UP);
        return new StudentTrimesterSummary(courseEnrollmentId, studentId, fullName, trimester, subjects, general);
    }
}
