package bo.edu.univalle.sis.ue6dejunio_api.application.services.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseAttendanceRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseScoreRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment.IEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook.IGradebookService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class GradebookService implements IGradebookService {

    private final IClassGroupDomain classGroupDomain;
    private final IEnrollmentDomain enrollmentDomain;
    private final IAttendanceDomain attendanceDomain;
    private final IScoreDomain scoreDomain;

    public GradebookService(IClassGroupDomain classGroupDomain,
                            IEnrollmentDomain enrollmentDomain,
                            IAttendanceDomain attendanceDomain,
                            IScoreDomain scoreDomain) {
        this.classGroupDomain = classGroupDomain;
        this.enrollmentDomain = enrollmentDomain;
        this.attendanceDomain = attendanceDomain;
        this.scoreDomain = scoreDomain;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CourseAttendanceRow> courseAttendance(Integer gradeId, Integer parallelId,
                                                      LocalDate date, Pageable pageable) {
        Integer yearId = classGroupDomain.currentAcademicYearId();
        List<UUID> classGroupIds = classGroupDomain.classGroupIdsByCourse(gradeId, parallelId, yearId);
        if (classGroupIds.isEmpty()) {
            throw new ResourceNotFoundException("Curso sin materias asignadas (class_groups)",
                "grado=" + gradeId + " paralelo=" + parallelId);
        }
        // asistencia replicada en todas las materias -> usar 1 class_group de referencia
        UUID refClassGroup = classGroupIds.get(0);
        return enrollmentDomain.enrollmentsByClassGroup(refClassGroup, pageable)
            .map(ref -> {
                List<Attendance> att = attendanceDomain.findByEnrollment(ref.enrollmentId());
                if (date != null) {
                    att = att.stream().filter(a -> date.equals(a.date())).toList();
                }
                return new CourseAttendanceRow(ref.studentId(), ref.fullName(), ref.enrollmentId(), att);
            });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CourseScoreRow> classGroupScores(UUID classGroupId, Integer trimester, Pageable pageable) {
        return enrollmentDomain.enrollmentsByClassGroup(classGroupId, pageable)
            .map(ref -> {
                List<AcademicScore> scores = scoreDomain.findByEnrollment(ref.enrollmentId());
                if (trimester != null) {
                    scores = scores.stream().filter(s -> trimester.equals(s.trimester())).toList();
                }
                return new CourseScoreRow(ref.studentId(), ref.fullName(), ref.enrollmentId(), scores);
            });
    }
}
