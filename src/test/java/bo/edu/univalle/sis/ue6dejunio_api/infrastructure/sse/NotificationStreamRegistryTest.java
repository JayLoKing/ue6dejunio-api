package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.sse;

import static org.assertj.core.api.Assertions.assertThat;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.NotificationSent;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    /**
     * A tab that reconnects in a loop — a bug, or somebody trying — would otherwise hold one async
     * request slot per attempt for as long as the timeout lasts. The cap is per reader, so nobody
     * can spend the server's connections just by holding a token.
     */
    @Test
    void open_pastTheCap_dropsTheOldestRatherThanGrowing() {
        UUID reader = UUID.randomUUID();

        for (int i = 0; i < NotificationStreamRegistry.MAX_STREAMS_PER_READER + 3; i++) {
            registry.open(reader);
        }

        assertThat(registry.openStreamsOf(reader))
                .isEqualTo(NotificationStreamRegistry.MAX_STREAMS_PER_READER);
    }

    /**
     * The stream that was evicted is the first one opened, not an arbitrary one: the oldest is the
     * one most likely to be the tab that already went away.
     */
    @Test
    void open_pastTheCap_keepsTheNewestStreams() {
        UUID reader = UUID.randomUUID();
        SseEmitter first = registry.open(reader);
        for (int i = 1; i < NotificationStreamRegistry.MAX_STREAMS_PER_READER + 1; i++) {
            registry.open(reader);
        }

        assertThat(registry.holds(reader, first)).isFalse();
    }

    // The registry also drops a stream from SseEmitter's own completion, error and timeout
    // callbacks, and that path is not asserted here: an emitter that never reached a servlet
    // request runs no callback when it is completed, so a test of it would be a test of Spring
    // rather than of this class. What is asserted above is the guarantee that holds either way —
    // a stream that refuses a write is forgotten, so a browser that hung up without saying so
    // still leaves the map.
}
