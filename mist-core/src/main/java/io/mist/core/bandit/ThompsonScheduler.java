package io.mist.core.bandit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Thompson-sampling scheduler over Beta(α, β) posteriors, one per key.
 *
 * <p>Given a fault-target queue {@code [k1, k2, …, kN]} with per-key
 * success/failure counts, {@link #rank(Iterable)} returns a re-ordering
 * that draws each key's score from its current Beta(α, β) posterior and
 * sorts descending. Under a budget {@code B &lt; N}, taking the prefix
 * {@code [0, B)} is exactly Thompson sampling for the top-B-arms
 * problem: high-mean arms are favoured, but exploration is preserved
 * because the variance of {@code Beta(α, β)} decreases as evidence
 * accumulates rather than collapsing immediately to the empirical mean.
 *
 * <p>Counters use the standard Beta-binomial conjugate update:
 * {@link #recordSuccess(Object)} bumps α (a fault was detected on this
 * key), {@link #recordFailure(Object)} bumps β (the key was probed and
 * no fault surfaced). Priors default to {@code α₀ = β₀ = 1} so a key
 * with no observations samples uniformly on {@code [0, 1]} — appropriate
 * cold-start behaviour for {@code (endpoint, parameter, faultType)}
 * triples never tested before.
 *
 * <p>Beta sampling is via two Gamma(α, 1) / Gamma(β, 1) draws
 * (Marsaglia–Tsang 2000) and uses {@link Random#nextGaussian()} so the
 * scheduler has no external math dependency. All public methods are
 * thread-safe.
 */
public final class ThompsonScheduler {

    private static final double DEFAULT_PRIOR_ALPHA = 1.0;
    private static final double DEFAULT_PRIOR_BETA  = 1.0;

    private final ConcurrentHashMap<Object, double[]> counters = new ConcurrentHashMap<>();
    private final Random random;
    private final double priorAlpha;
    private final double priorBeta;

    public ThompsonScheduler() {
        this(new Random(), DEFAULT_PRIOR_ALPHA, DEFAULT_PRIOR_BETA);
    }

    public ThompsonScheduler(Random random) {
        this(random, DEFAULT_PRIOR_ALPHA, DEFAULT_PRIOR_BETA);
    }

    public ThompsonScheduler(Random random, double priorAlpha, double priorBeta) {
        if (priorAlpha <= 0 || priorBeta <= 0) {
            throw new IllegalArgumentException("Beta priors must be > 0");
        }
        this.random = Objects.requireNonNull(random, "random");
        this.priorAlpha = priorAlpha;
        this.priorBeta = priorBeta;
    }

    /** Record one observation of a successful fault detection for {@code key}. */
    public void recordSuccess(Object key) {
        counters.compute(key, (k, v) -> {
            double[] ab = (v == null) ? new double[] { priorAlpha, priorBeta } : v;
            ab[0] += 1.0;
            return ab;
        });
    }

    /** Record one observation of a probe with no fault on {@code key}. */
    public void recordFailure(Object key) {
        counters.compute(key, (k, v) -> {
            double[] ab = (v == null) ? new double[] { priorAlpha, priorBeta } : v;
            ab[1] += 1.0;
            return ab;
        });
    }

    /** Draw one Thompson sample θ ~ Beta(α, β) for {@code key}. */
    public double sample(Object key) {
        double[] ab = counters.getOrDefault(key, new double[] { priorAlpha, priorBeta });
        return sampleBeta(ab[0], ab[1]);
    }

    /** Current posterior mean α / (α + β); diagnostic, not the sampling target. */
    public double posteriorMean(Object key) {
        double[] ab = counters.getOrDefault(key, new double[] { priorAlpha, priorBeta });
        return ab[0] / (ab[0] + ab[1]);
    }

    /** Return the current (α, β) for {@code key}; useful for persistence snapshots. */
    public double[] counters(Object key) {
        double[] ab = counters.getOrDefault(key, new double[] { priorAlpha, priorBeta });
        return new double[] { ab[0], ab[1] };
    }

    /**
     * Rank {@code keys} descending by a fresh Thompson sample per key.
     * Drawing per-call (rather than once at scheduler construction) is
     * deliberate — successive calls produce different orderings,
     * spreading exploration across the budget. Each element is sampled
     * by its own value (i.e. lookup keys are {@code k.equals(k')}).
     */
    public <T> List<T> rank(Iterable<T> keys) {
        return rank(keys, Function.identity());
    }

    /**
     * Same as {@link #rank(Iterable)}, but each element is mapped to its
     * scheduler key by {@code keyExtractor}. Use this when the iterable
     * holds rich objects (e.g. domain records) but the bandit counters
     * are keyed by a stable identifier derived from those records.
     */
    public <T> List<T> rank(Iterable<T> items, Function<? super T, ?> keyExtractor) {
        Map<T, Double> draws = new LinkedHashMap<>();
        for (T item : items) draws.put(item, sample(keyExtractor.apply(item)));
        List<T> out = new ArrayList<>(draws.keySet());
        out.sort(Comparator.comparingDouble(draws::get).reversed());
        return out;
    }

    /**
     * Immutable snapshot of all counters for persistence. The result
     * uses {@code String} keys via {@link Object#toString()} — callers
     * persisting under typed keys should encode/decode at the boundary.
     */
    public Map<String, double[]> snapshot() {
        Map<String, double[]> out = new LinkedHashMap<>();
        counters.forEach((k, v) -> out.put(k.toString(), new double[] { v[0], v[1] }));
        return out;
    }

    /** Seed {@code key} with explicit counters (e.g. when reloading from disk). */
    public void seed(Object key, double alpha, double beta) {
        if (alpha <= 0 || beta <= 0) {
            throw new IllegalArgumentException("Counters must be > 0");
        }
        counters.put(key, new double[] { alpha, beta });
    }

    // ─────────────────────────── Beta sampling ──────────────────────────────

    private double sampleBeta(double alpha, double beta) {
        double x = sampleGamma(alpha);
        double y = sampleGamma(beta);
        return x / (x + y);
    }

    /**
     * Sample Gamma(shape, 1) via Marsaglia–Tsang (2000). For {@code shape < 1},
     * the boost-up identity Γ(α, 1) = Γ(α+1, 1) · U^{1/α} is applied.
     */
    private double sampleGamma(double shape) {
        if (shape < 1.0) {
            double u = Math.max(random.nextDouble(), Double.MIN_VALUE);
            return sampleGamma(shape + 1.0) * Math.pow(u, 1.0 / shape);
        }
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x = random.nextGaussian();
            double v = 1.0 + c * x;
            if (v <= 0) continue;
            v = v * v * v;
            double u = random.nextDouble();
            if (u < 1.0 - 0.0331 * (x * x) * (x * x)) return d * v;
            if (Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) return d * v;
        }
    }
}
