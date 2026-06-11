package io.mist.core.value;

import io.mist.core.value.ValueProvenance;

/**
 * Heuristic provenance detection for parameter values produced by the
 * fallback paths of the adapter-side {@code MultiServiceTestCaseGenerator}.
 *
 * <p>The generator's type-aware fallbacks emit values with one of a fixed
 * set of literal prefixes — e.g. {@code "FALLBACK_id_3"} from the
 * generator's {@code typeAwareFallbackValue(TestParameter, int)} path
 * or {@code "LLM_EMPTY_userId"} when the LLM produces no candidates.
 * Observing one of these prefixes at read time is sufficient to conclude
 * that the value was never grounded in a live system response or LLM
 * call, and the test that contains it must be reclassified as negative
 * by §III.L of the companion paper. Other provenance categories
 * (live, cache, LLM-generated) are introduced by Fix 3 Layer 2.
 */
public final class ValueProvenanceInference {

    /**
     * Literal prefixes the generator (and {@code StageSupport}) emit when no
     * resolved value could be obtained. Kept in sync with
     * {@code typeAwareFallbackValue}, the {@code LLM_EMPTY_*} fallback,
     * the {@code STEP1_*} disabled-LLM placeholder, and the
     * {@code VAL_*} ultimate fallback in dependent-step resolution.
     */
    static final String[] SYNTHETIC_PREFIXES = {
        "FALLBACK_",
        "LLM_EMPTY",
        "STEP1_",
        "VAL_"
    };

    private ValueProvenanceInference() {}

    /**
     * Returns {@link ValueProvenance#SYNTHETIC_PLACEHOLDER} when {@code value}
     * begins with a known synthetic prefix, and {@code null} otherwise. A
     * {@code null} result means "not detectably synthetic"; downstream code
     * treats absence of an explicit tag as live-grounded.
     */
    public static ValueProvenance infer(String value) {
        if (value == null) return null;
        for (String prefix : SYNTHETIC_PREFIXES) {
            if (value.startsWith(prefix)) return ValueProvenance.SYNTHETIC_PLACEHOLDER;
        }
        return null;
    }
}
