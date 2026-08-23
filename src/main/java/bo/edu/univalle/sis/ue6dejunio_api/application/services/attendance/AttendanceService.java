package bo.edu.univalle.sis.ue6dejunio_api.application.services.attendance;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.AttendanceCounts;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.CourseAttendanceStats;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.DailyBatchResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.DailyStatusCount;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.MonthlyAttendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.TrimesterAttendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.TrimesterPeriod;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.trimesterperiod.ITrimesterPeriodDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class AttendanceService implements IAttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    private static final String STATUS_PRESENT = "Present";
    private static final String STATUS_ABSENT = "Absent";
    private static final String STATUS_LATE = "Late";
    private static final String STATUS_EXCUSED = "Excused";

    private static final String SCOPE_ANNUAL = "annual";
    private static final String SCOPE_TRIMESTER = "trimester";

    private final IAttendanceDomain attendanceDomain;
    private final ITrimesterPeriodDomain trimesterPeriodDomain;
    private final Clock clock;

    public AttendanceService(IAttendanceDomain attendanceDomain,
                             ITrimesterPeriodDomain trimesterPeriodDomain, Clock clock) {
        this.attendanceDomain = attendanceDomain;
        this.trimesterPeriodDomain = trimesterPeriodDomain;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Attendance registerDaily(UUID courseEnrollmentId, LocalDate date, String status) {
        requireEditableSchoolDay(date);
        if (!attendanceDomain.courseEnrollmentExists(courseEnrollmentId)) {
            throw new ResourceNotFoundException("CourseEnrollment", courseEnrollmentId);
        }
        return attendanceDomain.upsertDaily(courseEnrollmentId, date, status);
    }

    @Override
    @Transactional
    public DailyBatchResult registerDailyBatch(LocalDate date, List<DailyMark> marks) {
        requireEditableSchoolDay(date);
        if (marks.isEmpty()) {
            return new DailyBatchResult(0, 0);
        }

        // One bounded existence check for the whole batch instead of one query per mark.
        List<UUID> courseEnrollmentIds = marks.stream().map(DailyMark::courseEnrollmentId).toList();
        Set<UUID> existingIds = attendanceDomain.existingCourseEnrollmentIds(courseEnrollmentIds);
        for (DailyMark m : marks) {
            if (!existingIds.contains(m.courseEnrollmentId())) {
                // Whole batch fails atomically before anything is persisted — same as before.
                throw new ResourceNotFoundException("CourseEnrollment", m.courseEnrollmentId());
            }
        }

        // Preserve mark order; last status wins if the same enrollment appears twice, matching
        // the previous per-mark upsert loop where a later mark for the same id overwrote the
        // earlier one within the same transaction.
        Map<UUID, String> statusByCourseEnrollmentId = new LinkedHashMap<>();
        for (DailyMark m : marks) {
            statusByCourseEnrollmentId.put(m.courseEnrollmentId(), m.status());
        }

        // One bounded query for existing rows + one batch save instead of a find+save per mark.
        attendanceDomain.upsertDailyBatch(date, statusByCourseEnrollmentId);
        return new DailyBatchResult(marks.size(), statusByCourseEnrollmentId.size());
    }

    @Override
    @Transactional
    public DailyBatchResult registerSessionBatch(UUID classGroupId, LocalDate date, List<DailyMark> marks) {
        requireEditableSchoolDay(date);
        if (marks.isEmpty()) {
            return new DailyBatchResult(0, 0);
        }
        UUID courseId = attendanceDomain.courseOfClassGroup(classGroupId);
        List<UUID> courseEnrollmentIds = marks.stream().map(DailyMark::courseEnrollmentId).toList();

        Set<UUID> existingIds = attendanceDomain.existingCourseEnrollmentIds(courseEnrollmentIds);
        for (DailyMark m : marks) {
            if (!existingIds.contains(m.courseEnrollmentId())) {
                throw new ResourceNotFoundException("CourseEnrollment", m.courseEnrollmentId());
            }
        }

        // Second bounded query instead of a course lookup per mark. Everyone in the batch must
        // belong to the course this class group teaches, and the whole batch fails before
        // anything is persisted — same atomicity contract as the daily batch.
        Set<UUID> inCourse = attendanceDomain.courseEnrollmentIdsInCourse(courseEnrollmentIds, courseId);
        for (DailyMark m : marks) {
            if (!inCourse.contains(m.courseEnrollmentId())) {
                throw new ConflictException("El estudiante no pertenece al curso de la materia");
            }
        }

        Map<UUID, String> statusByCourseEnrollmentId = new LinkedHashMap<>();
        for (DailyMark m : marks) {
            statusByCourseEnrollmentId.put(m.courseEnrollmentId(), m.status());
        }
        attendanceDomain.upsertSessionBatch(classGroupId, date, statusByCourseEnrollmentId);
        return new DailyBatchResult(marks.size(), statusByCourseEnrollmentId.size());
    }

    /**
     * Attendance is writable for any school day (Mon-Fri) of the current ISO week, so a teacher
     * who forgot to mark — or marked wrong — can still fix it before the week closes. Once the
     * week rolls over the records are frozen, because they feed regularity reporting.
     *
     * <p>The window is anchored on the ISO week of {@code today}, so Saturday and Sunday still
     * allow correcting that same week's Mon-Fri; only the date being written must be a school day.
     */
    private void requireEditableSchoolDay(LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        if (date.isAfter(today)) {
            throw new ConflictException("no puede registrar asistencia de fechas futuras");
        }
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            throw new ConflictException("no se registra asistencia en sabados ni domingos");
        }
        if (date.isBefore(today.with(DayOfWeek.MONDAY))) {
            throw new ConflictException(
                "solo puede modificar registros de la semana en curso (lunes a viernes)");
        }
    }

    @Override
    @Transactional
    public Attendance registerSession(UUID courseEnrollmentId, UUID classGroupId, LocalDate date, String status) {
        requireEditableSchoolDay(date);
        if (!attendanceDomain.courseEnrollmentExists(courseEnrollmentId)) {
            throw new ResourceNotFoundException("CourseEnrollment", courseEnrollmentId);
        }
        UUID ceCourse = attendanceDomain.courseOfCourseEnrollment(courseEnrollmentId);
        UUID cgCourse = attendanceDomain.courseOfClassGroup(classGroupId);
        if (!ceCourse.equals(cgCourse)) {
            throw new ConflictException("El estudiante no pertenece al curso de la materia");
        }
        return attendanceDomain.upsertSession(courseEnrollmentId, classGroupId, date, status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Attendance> byCourseEnrollment(UUID courseEnrollmentId) {
        return attendanceDomain.byCourseEnrollment(courseEnrollmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseAttendanceStats attendanceStats(UUID courseId, Integer trimester) {
        if (!attendanceDomain.courseExists(courseId)) {
            throw new ResourceNotFoundException("Course", courseId);
        }

        Integer academicYearId = attendanceDomain.academicYearOfCourse(courseId);
        List<TrimesterPeriod> periods = trimesterPeriodDomain.findByAcademicYear(academicYearId);
        if (periods.isEmpty()) {
            throw new ConflictException(
                "El curso no tiene trimestres configurados para su año académico. "
                    + "El Director debe configurar los periodos de trimestre primero.");
        }

        List<TrimesterPeriod> includedPeriods;
        String scope;
        if (trimester != null) {
            TrimesterPeriod requested = periods.stream()
                .filter(p -> p.trimester() == trimester)
                .findFirst()
                .orElseThrow(() -> new ConflictException(
                    "El trimestre " + trimester
                        + " no tiene un periodo configurado para el año académico del curso."));
            includedPeriods = List.of(requested);
            scope = SCOPE_TRIMESTER;
        } else {
            includedPeriods = periods;
            scope = SCOPE_ANNUAL;
        }

        List<DailyStatusCount> rows = attendanceDomain.dailyStatusCountsByCourseGroupedByDate(courseId);

        // All three views (overall / byMonth / byTrimester) are derived from the SAME filtered
        // set of in-period rows below, so their totals always reconcile by construction.
        Map<YearMonth, long[]> perMonth = new TreeMap<>();
        Map<Integer, long[]> perTrimester = new TreeMap<>();
        long[] overallCounts = new long[4];

        for (DailyStatusCount row : rows) {
            TrimesterPeriod period = periodContaining(includedPeriods, row.date());
            if (period == null) {
                continue; // out-of-period date (vacation/holiday/other trimester/out-of-year): excluded everywhere
            }
            long[] monthCounts = perMonth.computeIfAbsent(YearMonth.from(row.date()), k -> new long[4]);
            long[] trimesterCounts = perTrimester.computeIfAbsent(period.trimester(), k -> new long[4]);
            addToCounts(monthCounts, row.status(), row.count());
            addToCounts(trimesterCounts, row.status(), row.count());
            addToCounts(overallCounts, row.status(), row.count());
        }

        List<MonthlyAttendance> byMonth = perMonth.entrySet().stream()
            .map(e -> new MonthlyAttendance(e.getKey().getYear(), e.getKey().getMonthValue(),
                toAttendanceCounts(e.getValue())))
            .toList();

        List<TrimesterAttendance> byTrimester = trimester != null
            ? List.of(new TrimesterAttendance(trimester, toAttendanceCounts(perTrimester.getOrDefault(trimester, new long[4]))))
            : perTrimester.entrySet().stream()
                .map(e -> new TrimesterAttendance(e.getKey(), toAttendanceCounts(e.getValue())))
                .toList();

        AttendanceCounts overall = toAttendanceCounts(overallCounts);
        return new CourseAttendanceStats(courseId, scope, trimester, overall, byMonth, byTrimester);
    }

    private static TrimesterPeriod periodContaining(List<TrimesterPeriod> periods, LocalDate date) {
        for (TrimesterPeriod p : periods) {
            if (p.contains(date)) {
                return p;
            }
        }
        return null;
    }

    /** Defensive: an unknown status must never 500 a read. The DB CHECK constraint already
     * restricts {@code status}, so this is belt-and-suspenders — log and skip, don't throw. */
    private static void addToCounts(long[] counts, String status, long amount) {
        switch (status) {
            case STATUS_PRESENT -> counts[0] += amount;
            case STATUS_ABSENT -> counts[1] += amount;
            case STATUS_LATE -> counts[2] += amount;
            case STATUS_EXCUSED -> counts[3] += amount;
            default -> log.warn("Unknown attendance status '{}' skipped in stats aggregation", status);
        }
    }

    private static AttendanceCounts toAttendanceCounts(long[] counts) {
        return AttendanceCounts.of(counts[0], counts[1], counts[2], counts[3]);
    }
}
