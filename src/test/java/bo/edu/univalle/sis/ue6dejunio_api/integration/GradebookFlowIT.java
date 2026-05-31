package bo.edu.univalle.sis.ue6dejunio_api.integration;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.DailyAttendanceCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.DailyAttendanceResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.CreateClassGroupCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.EnrollCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.TeacherStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseAttendanceRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseScoreRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.RegisterScoreByStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment.IEnrollmentService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook.IGradebookService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.RoleEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.SubjectEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaRoleRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaSubjectRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GradebookFlowIT extends AbstractIntegrationTest {

    @Autowired private JpaRoleRepository roleRepo;
    @Autowired private JpaUserRepository userRepo;
    @Autowired private JpaSubjectRepository subjectRepo;
    @Autowired private IClassGroupService classGroupService;
    @Autowired private IEnrollmentService enrollmentService;
    @Autowired private IScoreService scoreService;
    @Autowired private IAttendanceService attendanceService;
    @Autowired private IGradebookService gradebookService;

    @Test
    void fullFlow_course_enroll_score_attendance_gradebook() {
        // --- seed teacher ---
        RoleEntity teacherRole = roleRepo.findByName("Teacher").orElseThrow();
        UserEntity teacher = UserEntity.builder()
            .ci("TCH001").names("Elizabeth").lastNames("Argandona")
            .email("liz@ue6.bo").password("$2a$04$hash").role(teacherRole)
            .active(true).mustChangePassword(false).build();
        teacher = userRepo.save(teacher);
        UUID teacherId = teacher.getId();

        List<SubjectEntity> subjects = subjectRepo.findAll();
        assertThat(subjects).hasSize(2);
        UUID subj0 = subjects.get(0).getId();
        UUID subj1 = subjects.get(1).getId();

        // --- create course: 2 class_groups (grade 1, parallel 1) ---
        List<ClassGroup> groups = classGroupService.createCourse(new CreateClassGroupCommand(
            1, 1, List.of(
                new CreateClassGroupCommand.Assignment(subj0, teacherId),
                new CreateClassGroupCommand.Assignment(subj1, teacherId))));
        assertThat(groups).hasSize(2);
        assertThat(groups).allSatisfy(g -> {
            assertThat(g.gradeId()).isEqualTo(1);
            assertThat(g.parallelId()).isEqualTo(1);
            assertThat(g.year()).isEqualTo(2026);
        });

        // --- enroll 2 students (cross-enrollment -> 4 enrollments) ---
        EnrollResult enroll = enrollmentService.enrollCourse(new EnrollCourseCommand(
            1, 1, List.of(
                new CreateStudentCommand("RUDE-A", "CI-A", "Ana", "Mamani",
                    LocalDate.of(2012, 3, 10), "F"),
                new CreateStudentCommand("RUDE-B", "CI-B", "Bruno", "Quispe",
                    LocalDate.of(2012, 6, 5), "M"))));
        assertThat(enroll.studentsCreated()).isEqualTo(2);
        assertThat(enroll.classGroupsInCourse()).isEqualTo(2);
        assertThat(enroll.enrollmentsCreated()).isEqualTo(4);

        // --- teacher students query (EXISTS + sort, the one that broke) ---
        Page<TeacherStudent> page = enrollmentService.studentsOfTeacher(
            teacherId, PageRequest.of(0, 10, Sort.by("lastNames", "names")));
        assertThat(page.getTotalElements()).isEqualTo(2);
        TeacherStudent first = page.getContent().get(0);
        assertThat(first.lastNames()).isEqualTo("Mamani"); // alphabetical
        assertThat(first.enrollments()).hasSize(2);

        UUID studentA = first.id();
        UUID classGroupA = first.enrollments().get(0).classGroupId();

        // --- score by student (generated total_score in DB) ---
        AcademicScore score = scoreService.registerByStudent(new RegisterScoreByStudentCommand(
            studentA, classGroupA, 1,
            new BigDecimal("8"), new BigDecimal("40"), new BigDecimal("35"), new BigDecimal("4"),
            teacherId));
        assertThat(score.totalScore()).isEqualByComparingTo("87.00");

        // --- daily attendance (replicates to all class_groups) ---
        UUID studentB = page.getContent().get(1).id();
        LocalDate date = LocalDate.of(2026, 5, 21);
        DailyAttendanceResult daily = attendanceService.registerDaily(new DailyAttendanceCommand(
            1, 1, date, List.of(
                new DailyAttendanceCommand.StudentMark(studentA, "Present"),
                new DailyAttendanceCommand.StudentMark(studentB, "Absent"))));
        assertThat(daily.attendanceRowsSaved()).isEqualTo(4); // 2 students x 2 class_groups
        assertThat(daily.studentsNotEnrolled()).isEmpty();

        // --- gradebook scores ---
        Page<CourseScoreRow> scores = gradebookService.classGroupScores(
            classGroupA, 1, PageRequest.of(0, 10, Sort.by("student.lastNames")));
        assertThat(scores.getTotalElements()).isEqualTo(2);
        CourseScoreRow rowA = scores.getContent().stream()
            .filter(r -> r.studentId().equals(studentA)).findFirst().orElseThrow();
        assertThat(rowA.scores()).hasSize(1);
        assertThat(rowA.scores().get(0).totalScore()).isEqualByComparingTo("87.00");

        // --- gradebook attendance ---
        Page<CourseAttendanceRow> att = gradebookService.courseAttendance(
            1, 1, date, PageRequest.of(0, 10, Sort.by("student.lastNames")));
        assertThat(att.getTotalElements()).isEqualTo(2);
        assertThat(att.getContent()).allSatisfy(r ->
            assertThat(r.attendances()).hasSize(1));
    }
}
