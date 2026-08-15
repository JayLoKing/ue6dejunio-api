package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

/**
 * Storage abstraction for rate-limiting buckets, keyed by an arbitrary string (e.g.
 * {@code login:ip:email}). The default implementation is in-memory; a distributed (e.g.
 * Redis-backed) implementation can be swapped in via configuration without touching the filter.
 */
public interface RateLimitStore {

    /** Returns (creating if absent) the {@link Bucket} for {@code key}, configured with {@code bandwidth}. */
    Bucket resolveBucket(String key, Bandwidth bandwidth);
}
