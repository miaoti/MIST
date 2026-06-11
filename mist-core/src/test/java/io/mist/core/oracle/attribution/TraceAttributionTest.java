package io.mist.core.oracle.attribution;

import io.mist.core.oracle.shape.TraceModel;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Phase 2 attribution contract: leaf-error walk, service matching,
 * param-overlap heuristic, end-to-end verdict.
 */
public class TraceAttributionTest {

    @Test
    public void tokenize_handlesCamelSnakeAndDotCases() {
        Set<String> t1 = MethodToParamMapper.tokenize("validateSeatNumber");
        assertTrue(t1.contains("validate"));
        assertTrue(t1.contains("seat"));
        assertTrue(t1.contains("number"));

        Set<String> t2 = MethodToParamMapper.tokenize("ts_order_service.checkSeat");
        assertTrue(t2.contains("ts"));
        assertTrue(t2.contains("order"));
        assertTrue(t2.contains("service"));
        assertTrue(t2.contains("check"));
        assertTrue(t2.contains("seat"));

        Set<String> t3 = MethodToParamMapper.tokenize("HTTPParser.parseURL");
        assertTrue("acronym split", t3.contains("http") || t3.contains("https"));
        assertTrue(t3.contains("parser"));
    }

    @Test
    public void isResponsibleFor_matchesByTokenOverlap() {
        assertTrue(MethodToParamMapper.isResponsibleFor("validateSeatNumber", "seatNumber"));
        assertTrue(MethodToParamMapper.isResponsibleFor("validateSeat", "seatClass"));
        assertTrue(MethodToParamMapper.isResponsibleFor("OrderServiceImpl.checkContactsName", "contactsName"));
        assertFalse(MethodToParamMapper.isResponsibleFor("validateSeat", "userId"));
        assertFalse(MethodToParamMapper.isResponsibleFor("validateBoot", "seatNumber"));
    }

    @Test
    public void isResponsibleFor_ignoresPrefixNoise() {
        // "validate" alone in operation is noise; without "seat" in operation,
        // no match should fire even though "validate" appears.
        assertFalse(MethodToParamMapper.isResponsibleFor("validateRequest", "validateInput"));
    }

    @Test
    public void isResponsibleFor_emptyOrNullReturnsFalse() {
        assertFalse(MethodToParamMapper.isResponsibleFor(null, "p"));
        assertFalse(MethodToParamMapper.isResponsibleFor("op", null));
        assertFalse(MethodToParamMapper.isResponsibleFor("", "p"));
        assertFalse(MethodToParamMapper.isResponsibleFor("op", ""));
    }

    @Test
    public void serviceMatches_lenientSubstring() {
        assertTrue(TraceAttribution.serviceMatches("ts-order-service", "ts-order-service"));
        assertTrue(TraceAttribution.serviceMatches("ts-order-service.production", "ts-order-service"));
        assertTrue(TraceAttribution.serviceMatches("ts-order-service", "order-service"));
        assertFalse(TraceAttribution.serviceMatches("ts-order-service", "ts-payment-service"));
        assertFalse(TraceAttribution.serviceMatches(null, "ts-order-service"));
        assertFalse(TraceAttribution.serviceMatches("", "ts-order-service"));
    }

    @Test
    public void findLeafError_returnsNull_whenNoErrorSpan() {
        TraceModel trace = buildJaeger(
                span("s0", null, "ts-svc", "rootOp", 200, "OK"),
                span("s1", "s0", "ts-svc", "subOp", 200, "OK"));
        assertNull(LeafErrorSpanFinder.findLeafError(trace));
    }

    @Test
    public void findLeafError_returnsRoot_whenOnlyRootIsError() {
        TraceModel trace = buildJaeger(
                span("s0", null, "ts-order-service", "validateSeat", 400, "ERROR"));
        TraceModel.Span leaf = LeafErrorSpanFinder.findLeafError(trace);
        assertNotNull(leaf);
        assertEquals("s0", leaf.spanId);
    }

    @Test
    public void findLeafError_descendsErrorChain() {
        // s0 (root, 500) -> s1 (500) -> s2 (500): deepest is s2
        // s0 also has s3 (200, not error) — should NOT descend
        TraceModel trace = buildJaeger(
                span("s0", null, "ts-order-service", "createOrder", 500, "ERROR"),
                span("s1", "s0", "ts-payment-service", "charge", 500, "ERROR"),
                span("s2", "s1", "ts-fraud-service", "verifyTransaction", 500, "ERROR"),
                span("s3", "s0", "ts-notify-service", "sendEmail", 200, "OK"));
        TraceModel.Span leaf = LeafErrorSpanFinder.findLeafError(trace);
        assertNotNull(leaf);
        assertEquals("s2", leaf.spanId);
        assertEquals("ts-fraud-service", leaf.service);
    }

    @Test
    public void findLeafError_stopsAtNonErrorChild() {
        // s0 (500) -> s1 (200, OK): chain stops at s0 since s1 is not error
        TraceModel trace = buildJaeger(
                span("s0", null, "ts-order-service", "createOrder", 500, "ERROR"),
                span("s1", "s0", "ts-payment-service", "charge", 200, "OK"));
        TraceModel.Span leaf = LeafErrorSpanFinder.findLeafError(trace);
        assertNotNull(leaf);
        assertEquals("s0", leaf.spanId);
    }

    @Test
    public void findLeafError_picksDeepestAmongParallelErrors_FIXES_F6() {
        // s0 (error root) ->
        //   s1 (error) -> s2 (error)       — left branch, depth 2
        //   s3 (error)                     — right branch, depth 1
        // Algorithm is greedy DFS keeping deepest leaf across branches.
        // Deepest is s2 (depth 2); s3 is shallower so should never win.
        TraceModel trace = buildJaeger(
                span("s0", null, "ts-order-service", "createOrder", 500, "ERROR"),
                span("s1", "s0", "ts-payment-service", "charge", 500, "ERROR"),
                span("s2", "s1", "ts-fraud-service", "verify", 500, "ERROR"),
                span("s3", "s0", "ts-cart-service", "lockCart", 500, "ERROR"));
        TraceModel.Span leaf = LeafErrorSpanFinder.findLeafError(trace);
        assertNotNull(leaf);
        assertEquals("deepest error wins across parallel branches", "s2", leaf.spanId);
    }

    @Test
    public void findLeafError_parallelErrorsAtEqualDepth_picksOne_FIXES_F6() {
        // s0 (error root) ->
        //   s1 (error)  } both at depth 1; algorithm picks ONE arbitrarily.
        //   s2 (error)  } The contract: a leaf is returned and it's an error.
        TraceModel trace = buildJaeger(
                span("s0", null, "ts-order-service", "createOrder", 400, "ERROR"),
                span("s1", "s0", "ts-payment-service", "charge", 400, "ERROR"),
                span("s2", "s0", "ts-cart-service", "lockCart", 400, "ERROR"));
        TraceModel.Span leaf = LeafErrorSpanFinder.findLeafError(trace);
        assertNotNull(leaf);
        assertTrue("picks one of the two equal-depth error children",
                "s1".equals(leaf.spanId) || "s2".equals(leaf.spanId));
        // And whichever is picked: it's an error span (sanity).
        assertTrue("returned span is error-tagged",
                LeafErrorSpanFinder.isErrorTagged(leaf));
    }

    @Test
    public void attribute_targetRejection_serviceAndParamMatch() {
        TraceModel trace = buildJaeger(
                span("s0", null, "ts-order-service", "createOrder", 400, "ERROR"),
                span("s1", "s0", "ts-order-service", "validateSeatNumber", 400, "ERROR"));
        assertEquals(AttributionVerdict.TARGET_REJECTION,
                TraceAttribution.attribute(trace, "ts-order-service", "seatNumber"));
    }

    @Test
    public void attribute_wrongParamRejection_serviceMatchesParamDoesnt() {
        TraceModel trace = buildJaeger(
                span("s0", null, "ts-order-service", "createOrder", 400, "ERROR"),
                span("s1", "s0", "ts-order-service", "validateContactsName", 400, "ERROR"));
        assertEquals(AttributionVerdict.WRONG_PARAM_REJECTION,
                TraceAttribution.attribute(trace, "ts-order-service", "seatNumber"));
    }

    @Test
    public void attribute_upstreamRejection_differentService() {
        TraceModel trace = buildJaeger(
                span("s0", null, "ts-order-service", "createOrder", 500, "ERROR"),
                span("s1", "s0", "ts-payment-service", "charge", 500, "ERROR"));
        assertEquals(AttributionVerdict.UPSTREAM_REJECTION,
                TraceAttribution.attribute(trace, "ts-order-service", "seatNumber"));
    }

    @Test
    public void attribute_noAttribution_whenNoErrorSpan() {
        TraceModel trace = buildJaeger(
                span("s0", null, "ts-order-service", "createOrder", 200, "OK"));
        assertEquals(AttributionVerdict.NO_ATTRIBUTION,
                TraceAttribution.attribute(trace, "ts-order-service", "seatNumber"));
    }

    @Test
    public void attribute_targetWithoutParam_givesTargetWhenServiceMatches() {
        TraceModel trace = buildJaeger(
                span("s0", null, "ts-order-service", "createOrder", 400, "ERROR"));
        assertEquals("no param info → service-level confidence",
                AttributionVerdict.TARGET_REJECTION,
                TraceAttribution.attribute(trace, "ts-order-service", null));
    }

    @Test
    public void attribute_nullTrace_returnsNoAttribution() {
        assertEquals(AttributionVerdict.NO_ATTRIBUTION,
                TraceAttribution.attribute(null, "svc", "param"));
    }

    @Test
    public void isErrorTagged_otelOrHttp4xx() {
        TraceModel.Span otelErr = new TraceModel.Span("s", null, "svc", "op", 0, "ERROR", 0L, null);
        TraceModel.Span http4xx = new TraceModel.Span("s", null, "svc", "op", 422, "OK", 0L, null);
        TraceModel.Span http200 = new TraceModel.Span("s", null, "svc", "op", 200, "OK", 0L, null);
        TraceModel.Span http503 = new TraceModel.Span("s", null, "svc", "op", 503, null, 0L, null);
        assertTrue(LeafErrorSpanFinder.isErrorTagged(otelErr));
        assertTrue(LeafErrorSpanFinder.isErrorTagged(http4xx));
        assertTrue(LeafErrorSpanFinder.isErrorTagged(http503));
        assertFalse(LeafErrorSpanFinder.isErrorTagged(http200));
        assertFalse(LeafErrorSpanFinder.isErrorTagged(null));
    }

    // ---------------------------------------------------------------------
    // Builders: construct TraceModel.Span tuples directly via the public
    // constructor, then wrap in a TraceModel. Avoids file I/O for tests.
    // ---------------------------------------------------------------------

    private static TraceModel buildJaeger(TraceModel.Span... spans) {
        return new TraceModel("test-trace", Arrays.asList(spans));
    }

    private static TraceModel.Span span(String id, String parentId, String svc,
                                        String op, int httpStatus, String otelStatus) {
        Map<String, String> tags = new HashMap<>();
        if (httpStatus > 0) tags.put("http.status_code", String.valueOf(httpStatus));
        if (otelStatus != null) tags.put("otel.status_code", otelStatus);
        return new TraceModel.Span(id, parentId, svc, op, httpStatus, otelStatus, 0L, tags);
    }
}
