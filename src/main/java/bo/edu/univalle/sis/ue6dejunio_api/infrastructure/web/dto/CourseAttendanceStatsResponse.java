package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.AttendanceCounts;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.CourseAttendanceStats;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.MonthlyAttendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.TrimesterAttendance;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// The camelCase field names below (courseId, computableSessions, byMonth, byTrimester, etc.) are
// a fixed, spec-mandated frontend contract — do NOT rename or add @JsonProperty overrides here,
// even if other DTOs in this package change convention.
public record CourseAttendanceStatsResponse(
        UUID courseId,
        String scope,
        Integer trimester,
        Counts overall,
        List<Month> byMonth,
        List<Trimester> byTrimester) {
    public record Counts(
            long present,
            long absent,
            long late,
            long excused,
            long computableSessions,
            BigDecimal percentage) {
        static Counts from(AttendanceCounts c) {
            return new Counts(
                    c.present(),
                    c.absent(),
                    c.late(),
                    c.excused(),
                    c.computableSessions(),
                    c.percentage());
        }
    }

    public record Month(
            int year,
            int month,
            long present,
            long absent,
            long late,
            long excused,
            long computableSessions,
            BigDecimal percentage) {
        static Month from(MonthlyAttendance m) {
            AttendanceCounts c = m.counts();
            return new Month(
                    m.year(),
                    m.month(),
                    c.present(),
                    c.absent(),
                    c.late(),
                    c.excused(),
                    c.computableSessions(),
                    c.percentage());
        }
    }

    public record Trimester(
            int trimester,
            long present,
            long absent,
            long late,
            long excused,
            long computableSessions,
            BigDecimal percentage) {
        static Trimester from(TrimesterAttendance t) {
            AttendanceCounts c = t.counts();
            return new Trimester(
                    t.trimester(),
                    c.present(),
                    c.absent(),
                    c.late(),
                    c.excused(),
                    c.computableSessions(),
                    c.percentage());
        }
    }

    public static CourseAttendanceStatsResponse from(CourseAttendanceStats s) {
        return new CourseAttendanceStatsResponse(
                s.courseId(),
                s.scope(),
                s.trimester(),
                Counts.from(s.overall()),
                s.byMonth().stream().map(Month::from).toList(),
                s.byTrimester().stream().map(Trimester::from).toList());
    }
}
