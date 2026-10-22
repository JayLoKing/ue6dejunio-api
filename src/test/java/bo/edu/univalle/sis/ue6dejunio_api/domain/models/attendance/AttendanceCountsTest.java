package bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The school's rule (2026-10-22): Present and Late are attended; Absent and Excused are both a
 * falta. Every status is a computable session.
 */
class AttendanceCountsTest {

    @Test
    void lateCountsAsAttended() {
        AttendanceCounts counts = AttendanceCounts.of(0, 0, 4, 0);

        assertThat(counts.computableSessions()).isEqualTo(4);
        assertThat(counts.percentage()).isEqualByComparingTo("100.0");
    }

    @Test
    void excusedCountsAsAFalta() {
        AttendanceCounts counts = AttendanceCounts.of(1, 0, 0, 1);

        assertThat(counts.computableSessions()).isEqualTo(2);
        assertThat(counts.percentage()).isEqualByComparingTo("50.0");
    }

    /** 3 present + 1 late attended over 6 marks: 66.666... rounds HALF_UP to 66.7. */
    @Test
    void everyStatusIsComputable_andRoundsHalfUpToOneDecimal() {
        AttendanceCounts counts = AttendanceCounts.of(3, 1, 1, 1);

        assertThat(counts.computableSessions()).isEqualTo(6);
        assertThat(counts.percentage()).isEqualByComparingTo("66.7");
    }

    @Test
    void noMarks_percentageIsNull() {
        AttendanceCounts counts = AttendanceCounts.of(0, 0, 0, 0);

        assertThat(counts.computableSessions()).isZero();
        assertThat(counts.percentage()).isNull();
    }
}
