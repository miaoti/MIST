package io.mist.core.policy;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Phase-1 unit tests for {@link EndpointPolicyResolver}. Covers the two
 * implemented layers (HTTP-method default + OpenAPI {@code x-mist-*} hints).
 * Layer 3 (param-domain bump) and Layer 4 (live probe) are deferred to
 * phase 2 per the resolver's class javadoc and are not exercised here.
 *
 * <p>Naming matches the test cases listed in
 * {@code docs/adaptive-strategy-research.md} section 6 where feasible;
 * phase-2 cases (e.g. {@code smallDomainRaisesK},
 * {@code statefulDeleteKeepsBothCalls}) are intentionally omitted because
 * the resolver does not yet have the input to make those decisions.
 */
public class EndpointPolicyResolverTest {

    private final EndpointPolicyResolver resolver = new EndpointPolicyResolver();

    // Layer 1: HTTP-method defaults (RFC 7231 section 4.2)

    @Test
    public void rfc7231Defaults_getIsPayloadDedupAtTwentyFive() {
        EndpointPolicy p = resolver.resolve("GET", "/api/v1/foo");
        assertEquals(EndpointPolicy.DedupMode.PAYLOAD, p.dedupMode());
        // GET / HEAD / OPTIONS get a bumped kDedupExhausted = 25 because the
        // read-side fingerprint space is small; the absolute floor stays at
        // the historical 10 to preserve back-compat for callers that build
        // a resolver with a custom default below 25.
        assertEquals(25, p.kDedupExhausted());
        assertEquals(3, p.kZeroStep());
        assertEquals(-1, p.variantBudget());
    }

    @Test
    public void rfc7231Defaults_headAndOptionsMatchGet() {
        EndpointPolicy head = resolver.resolve("HEAD", "/api/v1/foo");
        EndpointPolicy options = resolver.resolve("OPTIONS", "/api/v1/foo");
        assertEquals(EndpointPolicy.DedupMode.PAYLOAD, head.dedupMode());
        assertEquals(EndpointPolicy.DedupMode.PAYLOAD, options.dedupMode());
        assertEquals(25, head.kDedupExhausted());
        assertEquals(25, options.kDedupExhausted());
    }

    @Test
    public void postIsAlwaysOffDedup() {
        EndpointPolicy p = resolver.resolve("POST", "/api/v1/orders");
        assertEquals(EndpointPolicy.DedupMode.OFF, p.dedupMode());
        // OFF dedup has no fingerprint-based natural stopping rule, so POST
        // carries a hard variant budget to bound the exhaustive fault queue
        // and keep generated single-file Java sources within javac's heap.
        assertEquals(50, p.variantBudget());
    }

    @Test
    public void patchIsAlwaysOffDedup() {
        EndpointPolicy p = resolver.resolve("PATCH", "/api/v1/orders/1");
        assertEquals(EndpointPolicy.DedupMode.OFF, p.dedupMode());
        // Same reasoning as POST: OFF dedup needs a finite cap.
        assertEquals(50, p.variantBudget());
    }

    @Test
    public void putAndDeleteStayOnPayloadDedup() {
        EndpointPolicy put = resolver.resolve("PUT", "/api/v1/orders/1");
        EndpointPolicy del = resolver.resolve("DELETE", "/api/v1/admintravel/{tripId}");
        // PUT and DELETE are RFC-idempotent but state-mutating; in phase 1
        // we keep them on PAYLOAD dedup. RESPONSE_AWARE is phase 2 (needs
        // the probe layer to observe a second-call status divergence).
        assertEquals(EndpointPolicy.DedupMode.PAYLOAD, put.dedupMode());
        assertEquals(EndpointPolicy.DedupMode.PAYLOAD, del.dedupMode());
        assertEquals(10, put.kDedupExhausted());
        assertEquals(10, del.kDedupExhausted());
        // PAYLOAD dedup already self-caps via K_DEDUP_EXHAUSTED, no budget needed.
        assertEquals(-1, put.variantBudget());
        assertEquals(-1, del.variantBudget());
    }

    @Test
    public void unknownVerbFallsBackToPayloadDedup() {
        // Defensive default: anything we don't recognise (e.g. WebDAV verbs)
        // should not crash; treat it like the safe historical default.
        EndpointPolicy p = resolver.resolve("PROPFIND", "/api/v1/foo");
        assertEquals(EndpointPolicy.DedupMode.PAYLOAD, p.dedupMode());
    }

    @Test
    public void nullVerbDoesNotCrash() {
        EndpointPolicy p = resolver.resolve(null, "/api/v1/foo");
        assertEquals(EndpointPolicy.DedupMode.PAYLOAD, p.dedupMode());
    }

    @Test
    public void methodIsCaseInsensitive() {
        EndpointPolicy a = resolver.resolve("post", "/api/v1/orders");
        EndpointPolicy b = resolver.resolve("PoSt", "/api/v1/orders");
        assertEquals(EndpointPolicy.DedupMode.OFF, a.dedupMode());
        assertEquals(EndpointPolicy.DedupMode.OFF, b.dedupMode());
    }

    // Layer 2: OpenAPI x-mist-* hint overrides

    @Test
    public void xMistDedupModeHintOverridesMethodDefault() {
        // POST normally yields OFF; an explicit hint pulls it back to PAYLOAD.
        Map<String, Object> ext = new HashMap<>();
        ext.put("x-mist-dedup-mode", "payload");
        EndpointPolicy p = resolver.resolve("POST", "/api/v1/orders", ext);
        assertEquals(EndpointPolicy.DedupMode.PAYLOAD, p.dedupMode());
    }

    @Test
    public void xMistDedupModeHintIsCaseInsensitive() {
        Map<String, Object> ext = new HashMap<>();
        ext.put("X-MIST-DEDUP-MODE", "OFF");
        EndpointPolicy p = resolver.resolve("GET", "/api/v1/foo", ext);
        assertEquals(EndpointPolicy.DedupMode.OFF, p.dedupMode());
    }

    @Test
    public void xMistStatefulHintForcesOff() {
        // A GET endpoint declared stateful by the spec author still gets OFF.
        Map<String, Object> ext = new HashMap<>();
        ext.put("x-mist-stateful", true);
        EndpointPolicy p = resolver.resolve("GET", "/api/v1/orders", ext);
        assertEquals(EndpointPolicy.DedupMode.OFF, p.dedupMode());
    }

    @Test
    public void xMistStatefulAcceptsStringBoolean() {
        Map<String, Object> ext = new HashMap<>();
        ext.put("x-mist-stateful", "true");
        EndpointPolicy p = resolver.resolve("GET", "/api/v1/orders", ext);
        assertEquals(EndpointPolicy.DedupMode.OFF, p.dedupMode());
    }

    @Test
    public void xMistKDedupExhaustedRaisesK() {
        Map<String, Object> ext = new HashMap<>();
        ext.put("x-mist-k-dedup-exhausted", 50);
        EndpointPolicy p = resolver.resolve("POST", "/api/v1/orders", ext);
        assertEquals(50, p.kDedupExhausted());
    }

    @Test
    public void xMistKDedupExhaustedAcceptsString() {
        Map<String, Object> ext = new HashMap<>();
        ext.put("x-mist-k-dedup-exhausted", "42");
        EndpointPolicy p = resolver.resolve("POST", "/api/v1/orders", ext);
        assertEquals(42, p.kDedupExhausted());
    }

    @Test
    public void xMistVariantBudgetOverridesDefault() {
        Map<String, Object> ext = new HashMap<>();
        ext.put("x-mist-variant-budget", 8);
        EndpointPolicy p = resolver.resolve("POST", "/api/v1/orders", ext);
        assertEquals(8, p.variantBudget());
    }

    @Test
    public void garbageHintsAreIgnoredNotCrashed() {
        Map<String, Object> ext = new HashMap<>();
        ext.put("x-mist-dedup-mode", "not-a-real-mode");
        ext.put("x-mist-k-dedup-exhausted", "not-a-number");
        ext.put("x-mist-stateful", "maybe");
        EndpointPolicy p = resolver.resolve("POST", "/api/v1/orders", ext);
        // Bad mode hint ignored, falls back to method default (POST = OFF).
        assertEquals(EndpointPolicy.DedupMode.OFF, p.dedupMode());
        // Bad k hint ignored, falls back to method default (POST = 10).
        assertEquals(10, p.kDedupExhausted());
    }

    @Test
    public void emptyExtensionsMapEqualsNoExtensions() {
        EndpointPolicy withEmpty = resolver.resolve("POST", "/api/v1/orders", new HashMap<>());
        EndpointPolicy withNull = resolver.resolve("POST", "/api/v1/orders", null);
        assertEquals(withNull.dedupMode(), withEmpty.dedupMode());
        assertEquals(withNull.kDedupExhausted(), withEmpty.kDedupExhausted());
    }

    // Custom-defaults constructor

    @Test
    public void customDefaultsHonoured() {
        EndpointPolicyResolver custom = new EndpointPolicyResolver(15, 7);
        EndpointPolicy p = custom.resolve("POST", "/api/v1/orders");
        assertEquals(15, p.kDedupExhausted());
        assertEquals(7, p.kZeroStep());
    }

    @Test
    public void getMethodFloorStillBumped_evenWithCustomDefaults() {
        // GET always bumps to >= 25 regardless of caller-supplied default,
        // because the read-side fingerprint space is small by construction.
        EndpointPolicyResolver custom = new EndpointPolicyResolver(15, 7);
        EndpointPolicy p = custom.resolve("GET", "/api/v1/foo");
        // Resolver: Math.max(defaultKDedupExhausted, 25)
        assertEquals(25, p.kDedupExhausted());
    }

    // LEGACY sentinel

    @Test
    public void legacyConstant_matchesPreAdaptiveDefaults() {
        // Documents the values the resolver replaces. Any drift here is a
        // signal that pre-adaptive behaviour has shifted unintentionally.
        assertEquals(EndpointPolicy.DedupMode.PAYLOAD, EndpointPolicy.LEGACY.dedupMode());
        assertEquals(10, EndpointPolicy.LEGACY.kDedupExhausted());
        assertEquals(3, EndpointPolicy.LEGACY.kZeroStep());
        assertEquals(-1, EndpointPolicy.LEGACY.variantBudget());
    }

    @Test
    public void resolverDefaultPolicyDiffersFromLegacy_forPost() {
        // Phase-1 sanity: when adaptive is on and the verb is POST, the
        // resolver must NOT return LEGACY (otherwise no behaviour change
        // would be visible in a live run).
        EndpointPolicy adaptive = resolver.resolve("POST", "/api/v1/orders");
        assertNotEquals(EndpointPolicy.LEGACY.dedupMode(), adaptive.dedupMode());
    }
}
