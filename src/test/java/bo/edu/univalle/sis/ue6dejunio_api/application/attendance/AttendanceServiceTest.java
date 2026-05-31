package bo.edu.univalle.sis.ue6dejunio_api.application.attendance;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.attendance.AttendanceService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.DailyAttendanceCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.DailyAttendanceResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment.IEnrollmentDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock private IAttendanceDomain attendanceDomain;
    @Mock private IClassGroupDomain classGroupDomain;
    @Mock private IEnrollmentDomain enrollmentDomain;
    @InjectMocks private AttendanceService attendanceService;

    @Test
    void registerDaily_replicatesToAllClassGroups() {
        UUID cg1 = UUID.randomUUID();
        UUID cg2 = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID enr1 = UUID.randomUUID();
        UUID enr2 = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 5, 21);

        when(classGroupDomain.currentAcademicYearId()).thenReturn(1);
        when(classGroupDomain.classGroupIdsByCourse(1, 1, 1)).thenReturn(List.of(cg1, cg2));
        when(enrollmentDomain.findEnrollmentId(studentId, cg1)).thenReturn(Optional.of(enr1));
        when(enrollmentDomain.findEnrollmentId(studentId, cg2)).thenReturn(Optional.of(enr2));
        when(attendanceDomain.upsert(any())).thenReturn(
            new Attendance(UUID.randomUUID(), enr1, date, "Present"));

        DailyAttendanceResult r = attendanceService.registerDaily(new DailyAttendanceCommand(
            1, 1, date, List.of(new DailyAttendanceCommand.StudentMark(studentId, "Present"))));

        assertThat(r.attendanceRowsSaved()).isEqualTo(2);
        assertThat(r.studentsNotEnrolled()).isEmpty();
        verify(attendanceDomain, times(2)).upsert(any());
    }

    @Test
    void registerDaily_studentNotEnrolled_listed() {
        UUID cg1 = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(classGroupDomain.currentAcademicYearId()).thenReturn(1);
        when(classGroupDomain.classGroupIdsByCourse(1, 1, 1)).thenReturn(List.of(cg1));
        when(enrollmentDomain.findEnrollmentId(studentId, cg1)).thenReturn(Optional.empty());

        DailyAttendanceResult r = attendanceService.registerDaily(new DailyAttendanceCommand(
            1, 1, LocalDate.now(), List.of(new DailyAttendanceCommand.StudentMark(studentId, "Absent"))));

        assertThat(r.attendanceRowsSaved()).isZero();
        assertThat(r.studentsNotEnrolled()).containsExactly(studentId);
    }

    @Test
    void registerDaily_noClassGroups_throws() {
        when(classGroupDomain.currentAcademicYearId()).thenReturn(1);
        when(classGroupDomain.classGroupIdsByCourse(1, 1, 1)).thenReturn(List.of());
        assertThatThrownBy(() ->
            attendanceService.registerDaily(new DailyAttendanceCommand(
                1, 1, LocalDate.now(), List.of(
                    new DailyAttendanceCommand.StudentMark(UUID.randomUUID(), "Present")))))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
