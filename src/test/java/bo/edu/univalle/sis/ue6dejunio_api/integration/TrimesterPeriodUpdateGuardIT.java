package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Editing a trimester period may extend it freely, but may not leave recorded attendance on a day
 * the old range covered and the new one does not. Works in a gestión of its own, like the delete
 * guard IT.
 */
class TrimesterPeriodUpdateGuardIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private String bearer;
    private Integer yearId;
    private UUID periodOne;
    private UUID enrollment;

    @BeforeEach
    void seed() {
        UUID director = seedUser("Director", false);
        bearer = "Bearer " + tokenFor(director, "Director");
        UUID teacher = seedUser("Teacher", false);
        yearId =
                jdbc.queryForObject(
                        "INSERT INTO academic_years (year) VALUES (2997) RETURNING id_academic_year",
                        Integer.class);
        periodOne =
                jdbc.queryForObject(
                        "INSERT INTO academic_trimesters (id_academic_year, trimester, start_date,"
                                + " end_date) VALUES (?, 1, ?, ?) RETURNING id_academic_trimester",
                        UUID.class,
                        yearId,
                        Date.valueOf("2997-02-01"),
                        Date.valueOf("2997-05-31"));
        UUID course = seedCourse(teacher, "A", yearId);
        enrollment = seedEnrollment(seedStudent(), course);
    }

    private void attendanceOn(String date) {
        jdbc.update(
                "INSERT INTO attendance (id_course_enrollment, date, status) VALUES (?, ?, 'Present')",
                enrollment,
                Date.valueOf(date));
    }

    private org.springframework.test.web.servlet.ResultActions edit(String start, String end)
            throws Exception {
        return mvc.perform(
                put("/api/trimester-periods/" + periodOne)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"start_date\": \""
                                        + start
                                        + "\", \"end_date\": \""
                                        + end
                                        + "\"}"));
    }

    private String storedRange() {
        return jdbc.queryForObject(
                "SELECT start_date || '..' || end_date FROM academic_trimesters "
                        + "WHERE id_academic_trimester = ?",
                String.class,
                periodOne);
    }

    @Test
    void update_extendingThePeriod_returns200EvenWithAttendanceInside() throws Exception {
        attendanceOn("2997-03-10");

        edit("2997-01-15", "2997-06-15").andExpect(status().isOk());

        assertThat(storedRange()).isEqualTo("2997-01-15..2997-06-15");
    }

    @Test
    void update_shrinkingPastRecordedAttendance_returns409AndLeavesThePeriodUnchanged()
            throws Exception {
        attendanceOn("2997-05-20");

        edit("2997-02-01", "2997-05-15").andExpect(status().isConflict());

        assertThat(storedRange()).isEqualTo("2997-02-01..2997-05-31");
    }

    @Test
    void update_shiftingAwayFromRecordedAttendance_returns409() throws Exception {
        attendanceOn("2997-02-10");

        edit("2997-03-01", "2997-06-30").andExpect(status().isConflict());

        assertThat(storedRange()).isEqualTo("2997-02-01..2997-05-31");
    }

    @Test
    void update_shrinkingWhereTheRemovedDaysAreEmpty_returns200() throws Exception {
        attendanceOn("2997-03-10"); // stays inside the new range

        edit("2997-02-15", "2997-05-15").andExpect(status().isOk());

        assertThat(storedRange()).isEqualTo("2997-02-15..2997-05-15");
    }

    @Test
    void update_ignoresAttendanceOfAnotherGestion() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        UUID course2026 = seedCourse(teacher, "B");
        UUID enrollment2026 = seedEnrollment(seedStudent(), course2026);
        jdbc.update(
                "INSERT INTO attendance (id_course_enrollment, date, status) VALUES (?, ?, 'Present')",
                enrollment2026,
                Date.valueOf("2997-05-20"));

        edit("2997-02-01", "2997-05-15").andExpect(status().isOk());
    }
}
