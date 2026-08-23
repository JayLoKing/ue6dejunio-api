package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Default {@link RateLimitStore}: a {@link ConcurrentHashMap}-backed in-memory bucket registry.
 * Suitable for a single instance deployment. The {@link TimeMeter} is injectable so tests can
 * drive a virtual clock instead of wall-clock time.
 */
@Component
public class InMemoryBucketStore implements RateLimitStore {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final TimeMeter timeMeter;

    public InMemoryBucketStore() {
        this(TimeMeter.SYSTEM_MILLISECONDS);
    }

    public InMemoryBucketStore(TimeMeter timeMeter) {
        this.timeMeter = timeMeter;
    }

    @Override
    public Bucket resolveBucket(String key, Bandwidth bandwidth) {
        return buckets.computeIfAbsent(
            key,
            k -> Bucket.builder().withCustomTimePrecision(timeMeter).addLimit(bandwidth).build());
    }

    /**
     * Drops every registered bucket. Integration tests share a single Spring context, so an
     * exhausted bucket left behind by one test would reject the next one's very first request.
     */
    public void clear() {
        buckets.clear();
    }
}
