package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.sse;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.NotificationSent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Who is listening right now, and how to reach them.
 *
 * <p>In memory, and deliberately so — see the decision on real-time delivery. These are open TCP
 * connections; they cannot outlive the process that holds them, so persisting the registry would
 * only record connections that no longer exist. The consequence is the one the client is built
 * around: a redeploy drops every stream, whatever was emitted during the restart is re-emitted by
 * nobody, and the inbox table stays the source of truth the reader falls back to.
 *
 * <p>Nothing here is the message. A push says "your inbox grew"; the reader then fetches it through
 * the authorised endpoint. Putting the text on the stream would hand an open tab content that the
 * inbox itself would have refused it.
 */
@Component
public class NotificationStreamRegistry {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreamRegistry.class);

    /**
     * How long a stream may stay open before the server closes it and the client opens another.
     *
     * <p>Finite on purpose. An infinite emitter leaks a request thread for every browser that ever
     * connected and never hung up cleanly, and the client reconnects on its own anyway.
     */
    static final long STREAM_TIMEOUT_MS = 30L * 60_000L;

    /** Every ~20s, so a client watching for a 45s gap gets two chances before it gives up. */
    private static final long HEARTBEAT_MS = 20_000L;

    /**
     * How many streams one reader may hold at once.
     *
     * <p>A person with a phone, a laptop and a spare tab is the honest ceiling. Without one, a
     * client reconnecting in a loop — a bug, or somebody with a valid token trying — holds an async
     * request slot per attempt until each times out, and the cost of that is paid by every other
     * reader. Small enough that the list below stays cheap to scan.
     */
    static final int MAX_STREAMS_PER_READER = 4;

    /** The one the client refetches on. */
    private static final String NOTIFICATION_EVENT = "notification";

    /** Says only that the connection is alive. Its absence is what the client's watchdog measures. */
    private static final String HEARTBEAT_EVENT = "heartbeat";

    /** Sent the moment the stream opens, so the client knows it is connected and may stop polling. */
    private static final String READY_EVENT = "ready";

    /**
     * A list rather than a set: it is insertion-ordered, which is what lets the cap evict the
     * oldest stream instead of an arbitrary one. Copy-on-write is the right trade at this size —
     * at most {@link #MAX_STREAMS_PER_READER} entries, written on connect and read on every push.
     */
    private final Map<UUID, List<SseEmitter>> streams = new ConcurrentHashMap<>();

    /**
     * Opens a stream for one reader.
     *
     * <p>The same account open on a phone and a laptop is two streams. Keeping only the newest
     * would leave the other tab on a badge that never moves again — so several are kept, up to
     * {@link #MAX_STREAMS_PER_READER}.
     */
    public SseEmitter open(UUID readerId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        streams.computeIfAbsent(readerId, id -> new CopyOnWriteArrayList<>()).add(emitter);
        evictOldestBeyondTheCap(readerId);

        emitter.onCompletion(() -> forget(readerId, emitter));
        emitter.onError(e -> {
            log.debug("The stream of {} failed", readerId, e);
            forget(readerId, emitter);
        });
        // A timeout does not complete the emitter by itself, and an emitter that is neither
        // completed nor forgotten is the connection this map keeps writing to forever.
        emitter.onTimeout(() -> {
            forget(readerId, emitter);
            emitter.complete();
        });

        // Before anything else: this is what makes the response headers reach the browser, which
        // is how the client learns it is connected rather than still opening.
        if (!send(readerId, emitter, READY_EVENT, "ok")) {
            // Forgotten but not finished: handed back open, it would be a response Spring keeps
            // waiting on for the full timeout with nobody left holding the other end.
            emitter.complete();
        }
        return emitter;
    }

    /** Closes the streams over the cap, oldest first — the ones most likely to be already gone. */
    private void evictOldestBeyondTheCap(UUID readerId) {
        List<SseEmitter> open = streams.get(readerId);
        if (open == null) {
            return;
        }
        while (open.size() > MAX_STREAMS_PER_READER) {
            SseEmitter oldest = open.remove(0);
            log.debug("{} is over the stream cap, closing their oldest one", readerId);
            oldest.complete();
        }
    }

    /** Whether this exact stream is still registered. For the tests that assert what was evicted. */
    boolean holds(UUID readerId, SseEmitter emitter) {
        List<SseEmitter> open = streams.get(readerId);
        return open != null && open.contains(emitter);
    }

    /** Nudges every stream the receiver has open. A receiver with none is not an error. */
    public void push(NotificationSent event) {
        List<SseEmitter> open = streams.get(event.receiverId());
        if (open == null || open.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : open) {
            send(event.receiverId(), emitter, NOTIFICATION_EVENT, event.notificationId().toString());
        }
    }

    /**
     * Proof of life on every open stream.
     *
     * <p>Two jobs at once, and the second is the one that matters here: a stream whose browser is
     * gone only reveals itself when something is written to it. Without this, the dead ones are
     * discovered on the next real notification — which may be hours away, or never.
     */
    @Scheduled(fixedRate = HEARTBEAT_MS)
    public void heartbeat() {
        for (Map.Entry<UUID, List<SseEmitter>> entry : streams.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                send(entry.getKey(), emitter, HEARTBEAT_EVENT, "1");
            }
        }
    }

    /** How many streams this reader holds open. */
    public int openStreamsOf(UUID readerId) {
        List<SseEmitter> open = streams.get(readerId);
        return open == null ? 0 : open.size();
    }

    /**
     * Writes, or forgets the stream that could not be written to.
     *
     * <p>{@link IllegalStateException} as well as {@link IOException}: a completed emitter refuses
     * a write with the former, and that is the ordinary shape of a browser that hung up.
     *
     * @return whether the write landed
     */
    private boolean send(UUID readerId, SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
            return true;
        } catch (IOException | IllegalStateException ex) {
            log.debug("The stream of {} is gone, dropping it", readerId);
            forget(readerId, emitter);
            return false;
        }
    }

    /** Takes the stream out, and the reader too once they hold none — an empty list is a leak. */
    private void forget(UUID readerId, SseEmitter emitter) {
        streams.computeIfPresent(readerId, (id, open) -> {
            open.remove(emitter);
            return open.isEmpty() ? null : open;
        });
    }
}
