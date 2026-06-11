package io.mist.core.value;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the classification rule encoded by {@link ValueProvenance#isLiveGrounded()}
 * and the equality / factory contracts of {@link ResolvedValue}.
 *
 * <p>The {@code isLiveGrounded} predicate is the screening rule used by the
 * resolution-aware test classifier (see §III.L of the companion paper): a
 * test whose every required parameter is live-grounded is a candidate
 * positive test, and any non-grounded value forces a negative-test verdict.
 */
public class ValueProvenanceTest {

    @Test
    public void liveGroundedCoversLiveCacheAndLlm() {
        assertTrue(ValueProvenance.RESOLVED_LIVE.isLiveGrounded());
        assertTrue(ValueProvenance.RESOLVED_CACHE.isLiveGrounded());
        assertTrue(ValueProvenance.LLM_GENERATED.isLiveGrounded());
    }

    @Test
    public void liveGroundedExcludesPlaceholderAndMutated() {
        assertFalse(ValueProvenance.SYNTHETIC_PLACEHOLDER.isLiveGrounded());
        assertFalse(ValueProvenance.MUTATED_FROM_RESOLVED.isLiveGrounded());
    }

    @Test
    public void resolvedValueFactoriesProduceCorrectProvenance() {
        assertEquals(ValueProvenance.RESOLVED_LIVE,        ResolvedValue.live("a").provenance());
        assertEquals(ValueProvenance.RESOLVED_CACHE,       ResolvedValue.cache("b").provenance());
        assertEquals(ValueProvenance.LLM_GENERATED,        ResolvedValue.llm("c").provenance());
        assertEquals(ValueProvenance.SYNTHETIC_PLACEHOLDER, ResolvedValue.synthetic("d").provenance());
        assertEquals(ValueProvenance.MUTATED_FROM_RESOLVED, ResolvedValue.mutated("e").provenance());
    }

    @Test
    public void resolvedValueEqualsRequiresMatchingValueAndProvenance() {
        ResolvedValue a = ResolvedValue.live("x");
        ResolvedValue b = ResolvedValue.live("x");
        ResolvedValue cDifferentValue = ResolvedValue.live("y");
        ResolvedValue cDifferentProvenance = ResolvedValue.cache("x");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, cDifferentValue);
        assertNotEquals(a, cDifferentProvenance);
    }

    @Test(expected = NullPointerException.class)
    public void resolvedValueRejectsNullValue() {
        new ResolvedValue(null, ValueProvenance.RESOLVED_LIVE);
    }

    @Test(expected = NullPointerException.class)
    public void resolvedValueRejectsNullProvenance() {
        new ResolvedValue("x", null);
    }
}
