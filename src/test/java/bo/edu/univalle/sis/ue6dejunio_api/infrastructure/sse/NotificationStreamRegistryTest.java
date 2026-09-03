package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.sse;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.NotificationSent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationStreamRegistryTest {

    private NotificationStreamRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NotificationStreamRegistry();
    }

    @Test
    void open_registersTheStreamUnderItsReader() {
        UUID reader = UUID.randomUUID();

        registry.open(reader);

        assertThat(registry.openStreamsOf(reader)).isEqualTo(1);
    }

    /**
     * A reader is a person, not a tab. The same account open on a phone and a laptop is two
     * streams, and nudging only the last one to connect leaves the other showing a stale badge.
     */
    @Test
    void open_theSameReaderTwice_keepsBothStreams() {
        UUID reader = UUID.randomUUID();

        registry.open(reader);
        registry.open(reader);

        assertThat(registry.openStreamsOf(reader)).isEqualTo(2);
    }

    @Test
    void push_reachesOnlyTheReaderItNames() {
        UUID reader = UUID.randomUUID();
        UUID somebodyElse = UUID.randomUUID();
        registry.open(reader);
        registry.open(somebodyElse);

        registry.push(new NotificationSent(reader, UUID.randomUUID()));

        assertThat(registry.openStreamsOf(reader)).isEqualTo(1);
        assertThat(registry.openStreamsOf(somebodyElse)).isEqualTo(1);
    }

    /** Nobody is listening. The nudge is dropped rather than becoming an error somewhere. */
    @Test
    void push_withNoStreamOpen_doesNothing() {
        UUID reader = UUID.randomUUID();

        registry.push(new NotificationSent(reader, UUID.randomUUID()));

        assertThat(registry.openStreamsOf(reader)).isZero();
    }

    /**
     * A browser that closed the tab leaves an emitter that refuses everything. Kept in the map it
     * would be written to on every heartbeat forever — the leak a registry of long-lived
     * connections is actually made of.
     */
    @Test
    void push_toAStreamThatIsGone_forgetsIt() {
        UUID reader = UUID.randomUUID();
        SseEmitter emitter = registry.open(reader);
        emitter.complete();

        registry.push(new NotificationSent(reader, UUID.randomUUID()));

        assertThat(registry.openStreamsOf(reader)).isZero();
    }

    /**
     * The client watches for these. Without them a connection that a proxy silently stopped
     * forwarding reads as open and idle rather than as broken.
     */
    @Test
    void heartbeat_dropsTheStreamsThatDiedQuietly() {
        UUID alive = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        registry.open(alive);
        registry.open(gone).complete();

        registry.heartbeat();

        assertThat(registry.openStreamsOf(alive)).isEqualTo(1);
        assertThat(registry.openStreamsOf(gone)).isZero();
    }

    // The registry also drops a stream from SseEmitter's own completion, error and timeout
    // callbacks, and that path is not asserted here: an emitter that never reached a servlet
    // request runs no callback when it is completed, so a test of it would be a test of Spring
    // rather than of this class. What is asserted above is the guarantee that holds either way —
    // a stream that refuses a write is forgotten, so a browser that hung up without saying so
    // still leaves the map.
}
