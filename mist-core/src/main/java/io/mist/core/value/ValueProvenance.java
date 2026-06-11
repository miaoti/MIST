package io.mist.core.value;

/**
 * How a parameter value was obtained during input generation.
 *
 * <p>Used by the resolution-aware test classifier: a test whose parameters
 * are all "real-system-grounded" ({@link #RESOLVED_LIVE}, {@link #RESOLVED_CACHE},
 * {@link #LLM_GENERATED}) is a candidate <em>positive</em> test, while any
 * presence of {@link #SYNTHETIC_PLACEHOLDER} reclassifies the test as
 * <em>negative</em> up front — a SUT 4xx on such a request is expected, not
 * a fault. {@link #MUTATED_FROM_RESOLVED} flags Sniper-Strategy derivatives
 * whose oracle continues to expect a 4xx on the mutated slot but unchanged
 * behaviour on the surrounding parameters.
 *
 * <p>The five categories are deliberately disjoint and exhaustive over the
 * MIST input pipeline; see §III.L of the companion paper for the formal
 * definitions and the classification rules that consume them.
 */
public enum ValueProvenance {

    /** Fetched live from a real producer endpoint or trace-observed service. */
    RESOLVED_LIVE,

    /** Returned from the smart-fetch cache of previously resolved live values. */
    RESOLVED_CACHE,

    /** Synthesised by the LLM as a realistic value (positive); not a placeholder. */
    LLM_GENERATED,

    /** Type-aware placeholder (e.g., {@code "FALLBACK_id_10"}) — no live grounding. */
    SYNTHETIC_PLACEHOLDER,

    /** Sniper-mutated derivative of a value originally tagged {@link #RESOLVED_LIVE}. */
    MUTATED_FROM_RESOLVED;

    /**
     * Whether values with this provenance can ground a <em>positive</em> test
     * expectation. The negative-test classifier uses this as the screening rule:
     * if any required parameter value returns {@code false} here, the entire
     * test is reclassified as negative.
     */
    public boolean isLiveGrounded() {
        return this == RESOLVED_LIVE || this == RESOLVED_CACHE || this == LLM_GENERATED;
    }
}
