package io.mist.core.workflow.pipeline.stages;

import io.mist.core.spec.Operation;
import io.mist.core.spec.TestConfiguration;
import io.mist.core.spec.TestConfigurationObject;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the generation-generalisation helpers added to
 * {@link StageSupport}: the path-template matcher and the
 * service-name-first / endpoint-fallback {@code resolveOperation}.
 *
 * <p>These guard the cross-SUT generalisation (Bookinfo's trace service
 * {@code productpage.default} vs conf key {@code productpage}; concrete trace
 * path {@code /products/0/ratings} vs templated conf path
 * {@code /products/{id}/ratings}) WITHOUT regressing train-ticket: the
 * endpoint-fallback counter must increment ONLY when service-name matching
 * misses.
 */
public class StageSupportPathMatchTest {

    @Before
    public void resetCounter() {
        StageSupport.resetEndpointFallbackCount();
    }

    // ── pathMatchesTemplate ─────────────────────────────────────────────────

    @Test
    public void concreteMatchesTemplate_singleParam() {
        assertTrue(StageSupport.pathMatchesTemplate("/products/0", "/products/{id}"));
    }

    @Test
    public void concreteMatchesTemplate_paramInMiddle() {
        assertTrue(StageSupport.pathMatchesTemplate(
                "/products/0/ratings", "/products/{id}/ratings"));
    }

    @Test
    public void concreteMatchesTemplate_multipleParams() {
        assertTrue(StageSupport.pathMatchesTemplate(
                "/a/1/b/2", "/a/{x}/b/{y}"));
    }

    @Test
    public void exactLiteralPath_matchesItself() {
        // Train-ticket-style fully-literal path with no params.
        assertTrue(StageSupport.pathMatchesTemplate(
                "/api/v1/orderservice/order", "/api/v1/orderservice/order"));
    }

    @Test
    public void mismatchedSegmentCount_shorterConcrete_doesNotMatch() {
        assertFalse(StageSupport.pathMatchesTemplate("/products", "/products/{id}"));
    }

    @Test
    public void mismatchedSegmentCount_longerConcrete_doesNotMatch() {
        assertFalse(StageSupport.pathMatchesTemplate(
                "/products/0/ratings", "/products/{id}"));
    }

    @Test
    public void differentLiteralSegment_doesNotMatch() {
        assertFalse(StageSupport.pathMatchesTemplate(
                "/products/0/reviews", "/products/{id}/ratings"));
    }

    @Test
    public void emptySegmentAgainstPlaceholder_doesNotMatch() {
        // /products//ratings must NOT satisfy /products/{id}/ratings.
        assertFalse(StageSupport.pathMatchesTemplate(
                "/products//ratings", "/products/{id}/ratings"));
    }

    @Test
    public void queryStringIsStrippedBeforeMatching() {
        assertTrue(StageSupport.pathMatchesTemplate(
                "/products/0/ratings?expand=true&x=1", "/products/{id}/ratings"));
    }

    @Test
    public void trailingSlashIsNormalised() {
        assertTrue(StageSupport.pathMatchesTemplate(
                "/products/0/ratings/", "/products/{id}/ratings"));
        assertTrue(StageSupport.pathMatchesTemplate(
                "/products/0/ratings", "/products/{id}/ratings/"));
    }

    @Test
    public void nullInputs_returnFalse() {
        assertFalse(StageSupport.pathMatchesTemplate(null, "/products/{id}"));
        assertFalse(StageSupport.pathMatchesTemplate("/products/0", null));
    }

    @Test
    public void literalSegmentWithRegexMetacharacters_isTreatedLiterally() {
        // A '.' in a real segment (e.g. a host-style segment) must NOT act as a
        // regex wildcard — the matcher compares segments literally, never builds
        // an unquoted regex. "a.b" matches only "a.b", not "axb".
        assertTrue(StageSupport.pathMatchesTemplate("/svc/a.b/x", "/svc/a.b/{id}"));
        assertFalse(StageSupport.pathMatchesTemplate("/svc/axb/x", "/svc/a.b/{id}"));
    }

    // ── resolveOperation: service-name match FIRST (train-ticket path) ───────

    @Test
    public void resolveOperation_serviceNameMatch_exactPath_noFallback() {
        Map<String, TestConfigurationObject> cfgs = new LinkedHashMap<>();
        cfgs.put("order-service", cfg(op("GET", "/api/v1/orderservice/order")));

        StageSupport.ResolvedOperation r = StageSupport.resolveOperation(
                cfgs, "order-service", "GET", "/api/v1/orderservice/order");

        assertNotNull(r);
        assertEquals("order-service", r.serviceKey);
        assertEquals("/api/v1/orderservice/order", r.op.getTestPath());
        assertEquals("endpoint fallback must NOT fire on a service-name hit",
                0, StageSupport.getEndpointFallbackCount());
    }

    @Test
    public void resolveOperation_serviceNameMatch_templatePath_noFallback() {
        Map<String, TestConfigurationObject> cfgs = new LinkedHashMap<>();
        cfgs.put("travel-service", cfg(op("GET", "/admintravel/{tripId}")));

        StageSupport.ResolvedOperation r = StageSupport.resolveOperation(
                cfgs, "travel-service", "GET", "/admintravel/G1235");

        assertNotNull(r);
        assertEquals("travel-service", r.serviceKey);
        assertEquals(0, StageSupport.getEndpointFallbackCount());
    }

    // ── resolveOperation: endpoint fallback (cross-SUT) ──────────────────────

    @Test
    public void resolveOperation_endpointFallback_singleMatch_incrementsCounter() {
        // Trace service "productpage.default" is NOT a conf key; the op lives
        // under conf key "productpage". Endpoint (method+path) matching finds it.
        Map<String, TestConfigurationObject> cfgs = new LinkedHashMap<>();
        cfgs.put("productpage", cfg(op("GET", "/products/{id}/ratings")));

        StageSupport.ResolvedOperation r = StageSupport.resolveOperation(
                cfgs, "productpage.default", "GET", "/products/0/ratings");

        assertNotNull("endpoint fallback should resolve the op", r);
        assertEquals("productpage", r.serviceKey);
        assertEquals("fallback fired exactly once",
                1, StageSupport.getEndpointFallbackCount());
    }

    @Test
    public void resolveOperation_noMatchAnywhere_returnsNull_noIncrement() {
        Map<String, TestConfigurationObject> cfgs = new LinkedHashMap<>();
        cfgs.put("productpage", cfg(op("GET", "/products/{id}/ratings")));

        StageSupport.ResolvedOperation r = StageSupport.resolveOperation(
                cfgs, "unknown.svc", "POST", "/totally/unrelated");

        assertNull(r);
        assertEquals(0, StageSupport.getEndpointFallbackCount());
    }

    @Test
    public void resolveOperation_ambiguousEndpoint_prefixDisambiguates() {
        // Two services expose GET /shared/{id}; the trace service name
        // "productpage.default" has conf key "productpage" as a prefix.
        Map<String, TestConfigurationObject> cfgs = new LinkedHashMap<>();
        cfgs.put("details", cfg(op("GET", "/shared/{id}")));
        cfgs.put("productpage", cfg(op("GET", "/shared/{id}")));

        StageSupport.ResolvedOperation r = StageSupport.resolveOperation(
                cfgs, "productpage.default", "GET", "/shared/9");

        assertNotNull(r);
        assertEquals("prefix-affinity must pick the conf whose key prefixes the trace service",
                "productpage", r.serviceKey);
        assertEquals(1, StageSupport.getEndpointFallbackCount());
    }

    @Test
    public void resolveOperation_ambiguousEndpoint_noPrefix_returnsNull_noIncrement() {
        // Two services expose the endpoint and NEITHER key prefixes the trace
        // service name → never silently pick → null, and counter stays 0.
        Map<String, TestConfigurationObject> cfgs = new LinkedHashMap<>();
        cfgs.put("details", cfg(op("GET", "/shared/{id}")));
        cfgs.put("reviews", cfg(op("GET", "/shared/{id}")));

        StageSupport.ResolvedOperation r = StageSupport.resolveOperation(
                cfgs, "productpage.default", "GET", "/shared/9");

        assertNull("ambiguous + no prefix affinity must not silently pick", r);
        assertEquals("a skipped (null) resolution must NOT increment the counter",
                0, StageSupport.getEndpointFallbackCount());
    }

    @Test
    public void resolveOperation_serviceKeyMatchesButOpMissing_fallsBackByEndpoint() {
        // cfg key matches the trace service, but that cfg lacks the op; another
        // cfg has it by endpoint. Fallback (opCfg==null branch) must engage.
        Map<String, TestConfigurationObject> cfgs = new LinkedHashMap<>();
        cfgs.put("productpage.default", cfg(op("GET", "/something/else")));
        cfgs.put("ratings", cfg(op("GET", "/products/{id}/ratings")));

        StageSupport.ResolvedOperation r = StageSupport.resolveOperation(
                cfgs, "productpage.default", "GET", "/products/0/ratings");

        assertNotNull(r);
        assertEquals("ratings", r.serviceKey);
        assertEquals(1, StageSupport.getEndpointFallbackCount());
    }

    // ── findOperation: exact-first, then template (drives train-ticket parity) ─

    @Test
    public void findOperation_exactBeatsTemplate() {
        // Both an exact literal op and a templated op match the same verb; the
        // exact one must win (preserves train-ticket selection).
        Operation exact = op("GET", "/products/0");
        Operation templated = op("GET", "/products/{id}");
        TestConfigurationObject c = cfg(templated, exact);

        Operation hit = StageSupport.findOperation(c, "GET", "/products/0");
        assertSame(exact, hit);
    }

    @Test
    public void findOperation_templateUsedWhenNoExact() {
        TestConfigurationObject c = cfg(op("GET", "/products/{id}"));
        Operation hit = StageSupport.findOperation(c, "GET", "/products/42");
        assertNotNull(hit);
        assertEquals("/products/{id}", hit.getTestPath());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Operation op(String method, String testPath) {
        Operation o = new Operation();
        o.setMethod(method);
        o.setTestPath(testPath);
        return o;
    }

    private static TestConfigurationObject cfg(Operation... ops) {
        TestConfiguration tc = new TestConfiguration();
        List<Operation> list = Arrays.asList(ops);
        tc.setOperations(list);
        TestConfigurationObject obj = new TestConfigurationObject();
        obj.setTestConfiguration(tc);
        return obj;
    }
}
