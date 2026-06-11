package io.mist.core.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralised factory for {@link Random} instances that honours the {@code random.seed} system
 * property. When the property is set, every produced generator is deterministically seeded so the
 * test-generation pipeline (smart-fetch picks, shared-pool draws, negative-mode random pool
 * selection) is fully reproducible across runs. When the property is absent, generators fall back
 * to a per-instance time-derived seed and the chosen seed is logged once at startup.
 *
 * <p>Different callers can pass a {@code scope} string so that — even with a single configured
 * seed — independent subsystems use uncorrelated streams (the seed is XORed with the scope hash).
 */
public final class SeededRandom {

    private static final Logger LOG = LogManager.getLogger(SeededRandom.class);

    /** Configured base seed (or {@code null} when the property is unset / unparsable). */
    private static final Long BASE_SEED = parseConfiguredSeed();

    /** Counter used to keep streams unique across calls when no seed is configured. */
    private static final AtomicLong NONCE = new AtomicLong(System.nanoTime());

    private SeededRandom() {}

    private static Long parseConfiguredSeed() {
        String prop = System.getProperty("random.seed");
        if (prop == null || prop.isEmpty()) {
            return null;
        }
        try {
            long seed = Long.parseLong(prop);
            LOG.info("🎲 Using configured random.seed = {}", seed);
            return seed;
        } catch (NumberFormatException e) {
            LOG.warn("Invalid random.seed='{}', falling back to non-deterministic seeding", prop);
            return null;
        }
    }

    /** Creates a new {@link Random} for an unscoped caller. */
    public static Random create() {
        if (BASE_SEED != null) {
            return new Random(BASE_SEED);
        }
        return new Random(NONCE.incrementAndGet());
    }

    /**
     * Creates a new {@link Random} whose stream is derived from the configured seed XORed with
     * the hash of the supplied scope. Two different scopes get independent (but reproducible)
     * sequences. When no seed is configured, behaves like {@link #create()}.
     */
    public static Random create(String scope) {
        if (BASE_SEED != null) {
            return new Random(BASE_SEED ^ (scope == null ? 0L : scope.hashCode()));
        }
        return new Random(NONCE.incrementAndGet());
    }

    /** Whether a deterministic seed is configured (for use in conditional logging). */
    public static boolean isSeeded() {
        return BASE_SEED != null;
    }

    /** Returns the configured seed, or {@code null} when none is set. */
    public static Long getBaseSeed() {
        return BASE_SEED;
    }
}
