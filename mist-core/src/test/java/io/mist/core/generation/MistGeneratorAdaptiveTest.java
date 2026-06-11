package io.mist.core.generation;

import org.junit.Test;

import io.mist.core.policy.EndpointPolicy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

/**
 * Byte-identical regression guard for the per-endpoint adaptive feature
 * (see docs/adaptive-strategy-research.md). When
 * {@code mst.adaptive.enabled=false} the MistGenerator variant loop must
 * behave identically to the pre-adaptive code, which used the literal
 * triplet {@code K_ZERO_STEP=3, K_DEDUP_EXHAUSTED=10, payload-dedup-always}.
 *
 * <p>Because that triplet is now read from {@link EndpointPolicy#LEGACY},
 * the byte-identical contract collapses to two independently checkable
 * properties:
 * <ol>
 *   <li>{@code LEGACY} encodes exactly the historical literals.</li>
 *   <li>{@link MistGenerator#decideEndpointPolicy} returns {@code LEGACY}
 *       in every adaptive-disabled call site, regardless of verb or config.</li>
 * </ol>
 * Together they prove (by construction) that {@code adaptive.enabled=false}
 * routes the variant loop through the same K-values and dedup mode as the
 * pre-adaptive code, so generator output cannot diverge.
 */
public class MistGeneratorAdaptiveTest {

    // ── (1) LEGACY constant matches the pre-adaptive literals ──────────────

    @Test
    public void legacyPolicy_matchesPreAdaptiveLiterals() {
        // Pre-adaptive MistGenerator had:
        //   final int K_ZERO_STEP = 3;
        //   final int K_DEDUP_EXHAUSTED = 10;
        //   // (no DedupMode toggle — payload fingerprint was unconditional)
        // EndpointPolicy.LEGACY MUST encode that triplet exactly.
        assertEquals("K_ZERO_STEP drifted from pre-adaptive 3",
                3, EndpointPolicy.LEGACY.kZeroStep());
        assertEquals("K_DEDUP_EXHAUSTED drifted from pre-adaptive 10",
                10, EndpointPolicy.LEGACY.kDedupExhausted());
        assertEquals("pre-adaptive code always ran payload dedup; LEGACY must keep PAYLOAD",
                EndpointPolicy.DedupMode.PAYLOAD, EndpointPolicy.LEGACY.dedupMode());
        assertEquals("pre-adaptive code had no variant budget; LEGACY must keep -1",
                -1, EndpointPolicy.LEGACY.variantBudget());
    }

    // ── (2) decideEndpointPolicy returns LEGACY when adaptive is off ───────

    @Test
    public void adaptiveOff_returnsLEGACY_forGet() {
        assertSame("adaptive=false GET must hit the LEGACY fast path",
                EndpointPolicy.LEGACY,
                MistGenerator.decideEndpointPolicy(false, "GET", 10, 3));
    }

    @Test
    public void adaptiveOff_returnsLEGACY_forPost() {
        assertSame("adaptive=false POST must hit the LEGACY fast path "
                        + "(NOT the DedupMode.OFF branch that adaptive=true POST takes)",
                EndpointPolicy.LEGACY,
                MistGenerator.decideEndpointPolicy(false, "POST", 10, 3));
    }

    @Test
    public void adaptiveOff_returnsLEGACY_forDelete() {
        assertSame(EndpointPolicy.LEGACY,
                MistGenerator.decideEndpointPolicy(false, "DELETE", 10, 3));
    }

    @Test
    public void adaptiveOff_returnsLEGACY_ignoringCustomDefaults() {
        // Even when the caller passes raised k-values, adaptive=false must
        // still return LEGACY — the config keys mst.adaptive.k.* are silently
        // ignored in disabled mode, which is the contract the byte-identical
        // promise rests on.
        assertSame("adaptive=false must ignore caller-supplied k-defaults",
                EndpointPolicy.LEGACY,
                MistGenerator.decideEndpointPolicy(false, "GET", 99, 99));
    }

    @Test
    public void adaptiveOff_returnsLEGACY_forNullVerb() {
        // Defensive: a malformed root-step key produces verb=null; LEGACY is
        // still the right answer (matches what the pre-adaptive code would do
        // since it had no verb input to make a different decision).
        assertSame(EndpointPolicy.LEGACY,
                MistGenerator.decideEndpointPolicy(false, null, 10, 3));
    }

    // ── (3) Counter-tests: adaptive=true *does* diverge per verb ───────────
    // These prove the seam is wired correctly — the LEGACY return when
    // adaptive=off is not just an unconditional return-LEGACY function.

    @Test
    public void adaptiveOn_post_returnsOffDedupMode() {
        EndpointPolicy p = MistGenerator.decideEndpointPolicy(true, "POST", 10, 3);
        assertNotSame("adaptive=true POST must diverge from LEGACY", EndpointPolicy.LEGACY, p);
        assertEquals("POST is not idempotent → DedupMode.OFF",
                EndpointPolicy.DedupMode.OFF, p.dedupMode());
    }

    @Test
    public void adaptiveOn_get_raisesKDedupExhausted() {
        EndpointPolicy p = MistGenerator.decideEndpointPolicy(true, "GET", 10, 3);
        assertNotSame("adaptive=true GET must diverge from LEGACY", EndpointPolicy.LEGACY, p);
        assertEquals("adaptive GET raises K_DEDUP_EXHAUSTED to 25 (small read-side space)",
                25, p.kDedupExhausted());
        assertEquals(EndpointPolicy.DedupMode.PAYLOAD, p.dedupMode());
    }

    @Test
    public void adaptiveOn_delete_keepsPayloadDedup() {
        EndpointPolicy p = MistGenerator.decideEndpointPolicy(true, "DELETE", 10, 3);
        assertEquals("DELETE: idempotent per RFC 7231 → PAYLOAD",
                EndpointPolicy.DedupMode.PAYLOAD, p.dedupMode());
        assertEquals(10, p.kDedupExhausted());
    }

    @Test
    public void adaptiveOn_nullVerb_fallsBackToLEGACY() {
        // The defensive null guard in decideEndpointPolicy must short-circuit
        // BEFORE the resolver is invoked (resolver would throw NPE).
        assertSame(EndpointPolicy.LEGACY,
                MistGenerator.decideEndpointPolicy(true, null, 10, 3));
    }
}
