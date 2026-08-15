package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Verifies bucket refill/window-reset behavior (Configurable Rate Limit Parameters requirement)
 * using an injected virtual {@link TimeMeter} for deterministic, instant-free assertions.
 */
class InMemoryBucketStoreTest {

    private static final Bandwidth TWO_PER_MINUTE =
        Bandwidth.builder().capacity(2).refillGreedy(2, Duration.ofMinutes(1)).build();

    @Test
    void exhaustedBucket_refillsAfterWindowElapses_onVirtualClock() {
        AtomicLong virtualNanos = new AtomicLong(0L);
        TimeMeter virtualClock =
            new TimeMeter() {
                @Override
                public long currentTimeNanos() {
                    return virtualNanos.get();
                }

                @Override
                public boolean isWallClockBased() {
                    return false;
                }
            };
        InMemoryBucketStore store = new InMemoryBucketStore(virtualClock);
        String key = "login:1.2.3.4:user@example.com";

        Bucket bucket = store.resolveBucket(key, TWO_PER_MINUTE);
        assertThat(bucket.tryConsumeAndReturnRemaining(1).isConsumed()).isTrue();
        assertThat(bucket.tryConsumeAndReturnRemaining(1).isConsumed()).isTrue();

        ConsumptionProbe rejected = bucket.tryConsumeAndReturnRemaining(1);
        assertThat(rejected.isConsumed()).isFalse();
        assertThat(rejected.getNanosToWaitForRefill()).isGreaterThan(0);

        // Advance the virtual clock past the 1-minute refill window.
        virtualNanos.addAndGet(Duration.ofMinutes(1).plusSeconds(1).toNanos());

        assertThat(bucket.tryConsumeAndReturnRemaining(1).isConsumed()).isTrue();
    }

    @Test
    void sameKey_returnsSameBucketAcrossCalls() {
        InMemoryBucketStore store = new InMemoryBucketStore(TimeMeter.SYSTEM_MILLISECONDS);
        Bucket first = store.resolveBucket("global:9.9.9.9", TWO_PER_MINUTE);
        Bucket second = store.resolveBucket("global:9.9.9.9", TWO_PER_MINUTE);

        assertThat(first).isSameAs(second);
    }
}
