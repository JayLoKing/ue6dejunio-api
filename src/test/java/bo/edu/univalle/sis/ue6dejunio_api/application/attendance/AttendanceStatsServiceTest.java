package bo.edu.univalle.sis.ue6dejunio_api.application.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.attendance.AttendanceService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.AttendanceCounts;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.CourseAttendanceStats;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.DailyStatusCount;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.MonthlyAttendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.TrimesterAttendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.TrimesterPeriod;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.event.IDomainEventPublisher;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.trimesterperiod.ITrimesterPeriodDomain;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Spec: pure aggregation + reconciliation + percentage math for GET
 * /api/courses/{id}/attendance-stats, now driven by Director-configured {@link TrimesterPeriod}
 * date ranges instead of a hardcoded month map. All scenarios here are DB-independent: both ports
 * are mocked with synthetic rows exactly as the real single GROUP BY query and the trimester-period
 * lookup would return them.
 *
 * <p>Reconciliation invariant enforced by construction: overall, byMonth and byTrimester are ALL
 * derived from the SAME filtered set of in-period dates, so sum(byTrimester) == sum(byMonth) ==
 * overall always holds; dates outside every configured period are dropped everywhere.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceStatsServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-14T16:00:00Z"), ZoneOffset.UTC);
    private static final Integer YEAR_ID = 7;

    @Mock private IAttendanceDomain attendanceDomain;
    @Mock private ITrimesterPeriodDomain trimesterPeriodDomain;
    @Mock private IDomainEventPublisher events;

    private AttendanceService service;
    private UUID courseId;

    private static final List<TrimesterPeriod> FULL_YEAR_PERIODS =
            List.of(
                    new TrimesterPeriod(
                            UUID.randomUUID(),
                            YEAR_ID,
                            1,
                            LocalDate.parse("2026-02-01"),
                            LocalDate.parse("2026-05-31")),
                    new TrimesterPeriod(
                            UUID.randomUUID(),
                            YEAR_ID,
                            2,
                            LocalDate.parse("2026-06-01"),
                            LocalDate.parse("2026-08-31")),
                    new TrimesterPeriod(
                            UUID.randomUUID(),
                            YEAR_ID,
                            3,
                            LocalDate.parse("2026-09-01"),
                            LocalDate.parse("2026-11-30")));

    @BeforeEach
    void setUp() {
        service =
                new AttendanceService(attendanceDomain, trimesterPeriodDomain, events, FIXED_CLOCK);
        courseId = UUID.randomUUID();
        lenient().when(attendanceDomain.courseExists(courseId)).thenReturn(true);
        lenient().when(attendanceDomain.academicYearOfCourse(courseId)).thenReturn(YEAR_ID);
    }

    @Test
    void unknownCourse_throwsNotFound() {
        when(attendanceDomain.courseExists(courseId)).thenReturn(false);

        assertThatThrownBy(() -> service.attendanceStats(courseId, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void noConfiguredPeriods_throwsConflict() {
        when(trimesterPeriodDomain.findByAcademicYear(YEAR_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.attendanceStats(courseId, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void requestedTrimesterNotConfigured_throwsConflict() {
        when(trimesterPeriodDomain.findByAcademicYear(YEAR_ID))
                .thenReturn(
                        List.of(
                                FULL_YEAR_PERIODS.get(0) // only T1 configured
                                ));

        assertThatThrownBy(() -> service.attendanceStats(courseId, 2))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void emptyCourse_returnsZeroOverall_nullPercentage_emptyByMonth() {
        when(trimesterPeriodDomain.findByAcademicYear(YEAR_ID)).thenReturn(FULL_YEAR_PERIODS);
        when(attendanceDomain.dailyStatusCountsByCourseGroupedByDate(courseId))
                .thenReturn(List.of());

        CourseAttendanceStats stats = service.attendanceStats(courseId, null);

        assertThat(stats.scope()).isEqualTo("annual");
        assertThat(stats.trimester()).isNull();
        assertThat(stats.overall()).isEqualTo(AttendanceCounts.of(0, 0, 0, 0));
        assertThat(stats.overall().percentage()).isNull();
        assertThat(stats.byMonth()).isEmpty();
        assertThat(stats.byTrimester()).isEmpty();
    }

    @Test
    void allExcusedCourse_percentageIsNull() {
        when(trimesterPeriodDomain.findByAcademicYear(YEAR_ID)).thenReturn(FULL_YEAR_PERIODS);
        when(attendanceDomain.dailyStatusCountsByCourseGroupedByDate(courseId))
                .thenReturn(
                        List.of(new DailyStatusCount(LocalDate.parse("2026-03-05"), "Excused", 5)));

        CourseAttendanceStats stats = service.attendanceStats(courseId, null);

        assertThat(stats.overall().present()).isZero();
        assertThat(stats.overall().computableSessions()).isZero();
        assertThat(stats.overall().percentage()).isNull();
    }

    @Test
    void lateOnlyCourse_percentageIsZero() {
        when(trimesterPeriodDomain.findByAcademicYear(YEAR_ID)).thenReturn(FULL_YEAR_PERIODS);
        when(attendanceDomain.dailyStatusCountsByCourseGroupedByDate(courseId))
                .thenReturn(
                        List.of(new DailyStatusCount(LocalDate.parse("2026-03-05"), "Late", 4)));

        CourseAttendanceStats stats = service.attendanceStats(courseId, null);

        assertThat(stats.overall().computableSessions()).isEqualTo(4);
        assertThat(stats.overall().percentage()).isEqualByComparingTo("0.0");
    }

    @Test
    void mixedStatuses_computesPercentage_excludingExcused_lateAsMiss() {
        when(trimesterPeriodDomain.findByAcademicYear(YEAR_ID)).thenReturn(FULL_YEAR_PERIODS);
        // present=9, absent=0, late=1, excused=2 -> computable=10 -> 9/10 = 90.0%
        when(attendanceDomain.dailyStatusCountsByCourseGroupedByDate(courseId))
                .thenReturn(
                        List.of(
                                new DailyStatusCount(LocalDate.parse("2026-03-05"), "Present", 9),
                                new DailyStatusCount(LocalDate.parse("2026-03-06"), "Late", 1),
                                new DailyStatusCount(LocalDate.parse("2026-03-07"), "Excused", 2)));

        CourseAttendanceStats stats = service.attendanceStats(courseId, null);

        assertThat(stats.overall().present()).isEqualTo(9);
        assertThat(stats.overall().late()).isEqualTo(1);
        assertThat(stats.overall().excused()).isEqualTo(2);
        assertThat(stats.overall().computableSessions()).isEqualTo(10);
        assertThat(stats.overall().percentage()).isEqualByComparingTo("90.0");
    }

    @Test
    void percentageRounding_halfUpToOneDecimal() {
        when(trimesterPeriodDomain.findByAcademicYear(YEAR_ID)).thenReturn(FULL_YEAR_PERIODS);
        // present=25, absent=3, late=0 -> computable=28 -> 25/28 = 89.2857...% -> 89.3
        when(attendanceDomain.dailyStatusCountsByCourseGroupedByDate(courseId))
                .thenReturn(
                        List.of(
                                new DailyStatusCount(LocalDate.parse("2026-03-05"), "Present", 25),
                                new DailyStatusCount(LocalDate.parse("2026-03-06"), "Absent", 3)));

        CourseAttendanceStats stats = service.attendanceStats(courseId, null);

        assertThat(stats.overall().percentage()).isEqualByComparingTo("89.3");
    }

    @Test
    void byMonth_ascendingOrder_omitsMonthsWithNoRecords() {
        when(trimesterPeriodDomain.findByAcademicYear(YEAR_ID)).thenReturn(FULL_YEAR_PERIODS);
        when(attendanceDomain.dailyStatusCountsByCourseGroupedByDate(courseId))
                .thenReturn(
                        List.of(
                                new DailyStatusCount(LocalDate.parse("2026-05-01"), "Present", 1),
                                new DailyStatusCount(LocalDate.parse("2026-02-01"), "Present", 1),
                                new DailyStatusCount(LocalDate.parse("2026-03-01"), "Absent", 1)));

        CourseAttendanceStats stats = service.attendanceStats(courseId, null);

        assertThat(stats.byMonth()).extracting(MonthlyAttendance::month).containsExactly(2, 3, 5);
    }

    @Test
    void annualScope_byTrimester_onlyIncludesTrimestersWithData() {
        when(trimesterPeriodDomain.findByAcademicYear(YEAR_ID)).thenReturn(FULL_YEAR_PERIODS);
        when(attendanceDomain.dailyStatusCountsByCourseGroupedByDate(courseId))
                .thenReturn(
                        List.of(
                                new DailyStatusCount(
                                        LocalDate.parse("2026-03-01"), "Present", 10), // T1
                                new DailyStatusCount(
                                        LocalDate.parse("2026-07-01"), "Absent", 2) // T2
                                // T3 has no data
                                ));

        CourseAttendanceStats stats = service.attendanceStats(courseId, null);

        assertThat(stats.byTrimester())
                .extracting(TrimesterAttendance::trimester)
                .containsExactly(1, 2);
    }

    @Test
    void trimesterScope_returnsSingleTrimesterElement_evenWhenEmpty() {
        when(trimesterPeriodDomain.findByAcademicYear(YEAR_ID)).thenReturn(FULL_YEAR_PERIODS);
        when(attendanceDomain.dailyStatusCountsByCourseGroupedByDate(courseId))
                .thenReturn(List.of());

        CourseAttendanceStats stats = service.attendanceStats(courseId, 2);

        assertThat(stats.scope()).isEqualTo("trimester");
        assertThat(stats.trimester()).isEqualTo(2);
        assertThat(stats.byTrimester()).hasSize(1);
        assertThat(stats.byTrimester().get(0).trimester()).isEqualTo(2);
        assertThat(stats.byTrimester().get(0).counts().percentage()).isNull();
    }

    @Test
    void trimesterScope_filtersOutDatesFromOtherTrimesters() {
        when(trimesterPeriodDomain.findByAcademicYear(YEAR_ID)).thenReturn(FULL_YEAR_PERIODS);
        when(attendanceDomain.dailyStatusCountsByCourseGroupedByDate(courseId))
                .thenReturn(
                        List.of(
                                new DailyStatusCount(
                                        LocalDate.parse("2026-03-05"),
                                        "Present",
                                        5), // T1 - included
                                new DailyStatusCount(
                                        LocalDate.parse("2026-07-05"),
                                        "Present",
                                        3) // T2 - excluded
                                ));

        CourseAttendanceStats stats = service.attendanceStats(courseId, 1);

        assertThat(stats.byMonth()).extracting(MonthlyAttendance::month).containsExactly(3);
        assertThat(stats.overall().present()).isEqualTo(5);
        assertThat(stats.byTrimester()).hasSize(1);
        assertThat(stats.byTrimester().get(0).counts().present()).isEqualTo(5);
    }

    @Test
    void datesOutsideAnyConfiguredPeriod_excludedEverywhere_annualScope() {
        when(trimesterPeriodDomain.findByAcademicYear(YEAR_ID)).thenReturn(FULL_YEAR_PERIODS);
        when(attendanceDomain.dailyStatusCountsByCourseGroupedByDate(courseId))
                .thenReturn(
                        List.of(
                                new DailyStatusCount(
                                        LocalDate.parse("2026-12-25"),
                                        "Present",
                                        2), // vacation, no period
                                new DailyStatusCount(
                                        LocalDate.parse("2026-03-01"), "Present", 5) // T1
                                ));

        CourseAttendanceStats stats = service.attendanceStats(courseId, null);

        // December is excluded everywhere: byMonth, byTrimester and overall.
        assertThat(stats.byMonth()).extracting(MonthlyAttendance::month).containsExactly(3);
        assertThat(stats.byTrimester())
                .extracting(TrimesterAttendance::trimester)
                .containsExactly(1);
        assertThat(stats.overall().present()).isEqualTo(5);
    }

    @Test
    void reconciliationInvariant_sumByTrimesterEqualsSumByMonthEqualsOverall() {
        when(trimesterPeriodDomain.findByAcademicYear(YEAR_ID)).thenReturn(FULL_YEAR_PERIODS);
        when(attendanceDomain.dailyStatusCountsByCourseGroupedByDate(courseId))
                .thenReturn(
                        List.of(
                                new DailyStatusCount(LocalDate.parse("2026-02-10"), "Present", 3),
                                new DailyStatusCount(LocalDate.parse("2026-03-15"), "Absent", 2),
                                new DailyStatusCount(LocalDate.parse("2026-06-01"), "Late", 1),
                                new DailyStatusCount(LocalDate.parse("2026-09-20"), "Excused", 4),
                                new DailyStatusCount(
                                        LocalDate.parse("2026-12-31"),
                                        "Present",
                                        99) // excluded (out of period)
                                ));

        CourseAttendanceStats stats = service.attendanceStats(courseId, null);

        long overallTotal =
                stats.overall().present()
                        + stats.overall().absent()
                        + stats.overall().late()
                        + stats.overall().excused();
        long byMonthTotal =
                stats.byMonth().stream()
                        .mapToLong(
                                m ->
                                        m.counts().present()
                                                + m.counts().absent()
                                                + m.counts().late()
                                                + m.counts().excused())
                        .sum();
        long byTrimesterTotal =
                stats.byTrimester().stream()
                        .mapToLong(
                                t ->
                                        t.counts().present()
                                                + t.counts().absent()
                                                + t.counts().late()
                                                + t.counts().excused())
                        .sum();

        assertThat(overallTotal).isEqualTo(10); // 3+2+1+4, the 99 in December is excluded
        assertThat(byMonthTotal).isEqualTo(overallTotal);
        assertThat(byTrimesterTotal).isEqualTo(overallTotal);
    }

    @Test
    void unknownStatus_skippedDefensively_neverThrows() {
        when(trimesterPeriodDomain.findByAcademicYear(YEAR_ID)).thenReturn(FULL_YEAR_PERIODS);
        when(attendanceDomain.dailyStatusCountsByCourseGroupedByDate(courseId))
                .thenReturn(
                        List.of(
                                new DailyStatusCount(
                                        LocalDate.parse("2026-03-05"), "SomeUnknownStatus", 7),
                                new DailyStatusCount(LocalDate.parse("2026-03-06"), "Present", 2)));

        CourseAttendanceStats stats = service.attendanceStats(courseId, null);

        assertThat(stats.overall().present()).isEqualTo(2);
        assertThat(stats.overall().computableSessions()).isEqualTo(2);
    }
}
