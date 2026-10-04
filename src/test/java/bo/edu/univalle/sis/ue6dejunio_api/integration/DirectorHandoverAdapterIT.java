package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.handover.DirectorHandover;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.handover.IDirectorHandoverDomain;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The persistence adapter of the handover row: what it stores, finds, and refuses. */
class DirectorHandoverAdapterIT extends AbstractIntegrationTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 3, 9, 0);

    @Autowired private IDirectorHandoverDomain handovers;

    private DirectorHandover pending(UUID outgoing, UUID incoming, boolean created, Integer role) {
        return new DirectorHandover(null, outgoing, incoming, created, role, T0, null, null);
    }

    @Test
    void save_thenFindPending_returnsTheRowWithEveryField() {
        UUID outgoing = seedUser("Director", false);
        UUID incoming = seedUser("Teacher", false);
        Integer teacherRole =
                jdbc.queryForObject(
                        "SELECT id_role FROM roles WHERE name = 'Teacher'", Integer.class);

        DirectorHandover saved = handovers.save(pending(outgoing, incoming, false, teacherRole));

        assertThat(saved.id()).isNotNull();
        DirectorHandover found = handovers.findPending().orElseThrow();
        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.outgoingId()).isEqualTo(outgoing);
        assertThat(found.incomingId()).isEqualTo(incoming);
        assertThat(found.incomingCreated()).isFalse();
        assertThat(found.previousRoleId()).isEqualTo(teacherRole);
        assertThat(found.startedAt()).isEqualTo(T0);
        assertThat(found.isPending()).isTrue();
    }

    @Test
    void findPendingByIncoming_matchesOnlyTheIncoming() {
        UUID outgoing = seedUser("Director", false);
        UUID incoming = seedUser("Teacher", false);
        UUID other = seedUser("Teacher", false);
        handovers.save(pending(outgoing, incoming, true, null));

        assertThat(handovers.findPendingByIncoming(incoming)).isPresent();
        assertThat(handovers.findPendingByIncoming(outgoing)).isEmpty();
        assertThat(handovers.findPendingByIncoming(other)).isEmpty();
    }

    @Test
    void aClosedOrCancelledRow_isNoLongerPending() {
        UUID outgoing = seedUser("Director", false);
        UUID incoming = seedUser("Teacher", false);
        DirectorHandover saved = handovers.save(pending(outgoing, incoming, true, null));

        handovers.save(saved.close(T0.plusHours(1)));

        assertThat(handovers.findPending()).isEmpty();
        assertThat(handovers.findPendingByIncoming(incoming)).isEmpty();
        Integer closed =
                jdbc.queryForObject(
                        "SELECT count(*) FROM director_handovers WHERE closed_at IS NOT NULL",
                        Integer.class);
        assertThat(closed).isEqualTo(1);
    }

    @Test
    void aSecondPendingRow_surfacesAsAConflict_notAServerError() {
        UUID outgoing = seedUser("Director", false);
        UUID first = seedUser("Teacher", false);
        UUID second = seedUser("Teacher", false);
        handovers.save(pending(outgoing, first, true, null));

        assertThatThrownBy(() -> handovers.save(pending(outgoing, second, true, null)))
                .isInstanceOf(ConflictException.class);
    }
}
