package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * V26 itself against the IT Postgres: the handover table, and the partial unique index that lets
 * the database, not only the service, refuse a second pending handover.
 */
class DirectorHandoverSchemaIT extends AbstractIntegrationTest {

    private static final Path V26 = Path.of("docs/sql/V26__director_handover.sql");

    private void insertHandover(UUID outgoing, UUID incoming, String closedAt, String cancelledAt) {
        jdbc.update(
                "INSERT INTO director_handovers (id_handover, outgoing_id, incoming_id, "
                        + "incoming_created, closed_at, cancelled_at) "
                        + "VALUES (gen_random_uuid(), ?, ?, true, "
                        + closedAt
                        + ", "
                        + cancelledAt
                        + ")",
                outgoing,
                incoming);
    }

    @Test
    void v26Script_createsTheTableAndIndex_andIsIdempotent() throws Exception {
        String script = Files.readString(V26);
        try {
            jdbc.execute("DROP TABLE IF EXISTS public.director_handovers");
            assertThat(handoverObjects()).isEmpty();

            jdbc.execute(script);
            assertThat(handoverObjects())
                    .containsExactlyInAnyOrder(
                            "director_handovers", "ux_director_handovers_single_pending");

            jdbc.execute(script);
            assertThat(handoverObjects()).hasSize(2);
        } finally {
            jdbc.execute(script);
        }
    }

    @Test
    void secondPendingHandover_isRefusedByTheDatabase() {
        UUID a = seedUser("Director", false);
        UUID b = seedUser("Teacher", false);
        UUID c = seedUser("Teacher", false);
        insertHandover(a, b, "NULL", "NULL");

        assertThatThrownBy(() -> insertHandover(a, c, "NULL", "NULL"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void closedOrCancelledHandovers_doNotBlockANewPendingOne() {
        UUID a = seedUser("Director", false);
        UUID b = seedUser("Teacher", false);
        UUID c = seedUser("Teacher", false);
        insertHandover(a, b, "now()", "NULL");
        insertHandover(a, b, "NULL", "now()");
        insertHandover(a, c, "NULL", "NULL");

        Integer rows =
                jdbc.queryForObject("SELECT count(*) FROM director_handovers", Integer.class);
        assertThat(rows).isEqualTo(3);
    }

    @Test
    void aHandoverToTheSameUser_andOneBothClosedAndCancelled_areRefused() {
        UUID a = seedUser("Director", false);
        UUID b = seedUser("Teacher", false);

        assertThatThrownBy(() -> insertHandover(a, a, "NULL", "NULL"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHandover(a, b, "now()", "now()"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<String> handoverObjects() {
        return jdbc.queryForList(
                "SELECT relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'public' AND relname IN "
                        + "('director_handovers', 'ux_director_handovers_single_pending')",
                String.class);
    }
}
