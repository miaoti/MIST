package io.mist.core.oracle.shape.invariant;

import io.mist.core.oracle.attribution.AttributionVerdict;
import io.mist.core.oracle.shape.ShapeInvariantStore;
import io.mist.core.oracle.shape.TraceModel;
import io.mist.core.oracle.shape.TraceShapeOracle;
import io.mist.core.oracle.shape.TraceShapeVerdict;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * FIXES.md F1 contract: TargetAttributionInvariant returns one
 * InvariantOutcome with kind="TARGET_ATTRIBUTION", detail=verdict name,
 * passed flag set per the F1 semantics (TARGET/NO_ATTRIBUTION → passed,
 * UPSTREAM/WRONG_PARAM → failed). Also pins TraceShapeOracle's new
 * 4-arg evaluate overload + the F3 kill switch.
 */
public class TargetAttributionInvariantTest {

    private String prevFlag;

    @Before
    public void setUp() {
        prevFlag = System.getProperty("mst.oracle.shape.invariants.target_attribution.enabled");
        System.clearProperty("mst.oracle.shape.invariants.target_attribution.enabled");
        io.mist.core.config.MstConfig.resetForTesting();
    }

    @After
    public void tearDown() {
        if (prevFlag == null) {
            System.clearProperty("mst.oracle.shape.invariants.target_attribution.enabled");
        } else {
            System.setProperty("mst.oracle.shape.invariants.target_attribution.enabled", prevFlag);
        }
        io.mist.core.config.MstConfig.resetForTesting();
    }

    @Test
    public void targetRejection_outcomePassedTrue() {
        TraceModel trace = traceWithLeaf("ts-order-service", "validateSeatNumber");
        TargetAttributionInvariant inv = new TargetAttributionInvariant(
                "POST /api/v1/orders", "ts-order-service", "seatNumber");
        TraceShapeVerdict.InvariantOutcome o = inv.evaluate(trace);
        assertEquals("TARGET_ATTRIBUTION", o.kind);
        assertEquals(AttributionVerdict.TARGET_REJECTION.name(), o.detail);
        assertTrue("TARGET_REJECTION is good news, not a deviation", o.passed);
        assertEquals(TraceShapeVerdict.Severity.INFO, o.severity);
    }

    @Test
    public void noAttribution_outcomePassedTrue() {
        TraceModel trace = traceNoError();
        TargetAttributionInvariant inv = new TargetAttributionInvariant(
                "POST /x", "ts-x", "p");
        TraceShapeVerdict.InvariantOutcome o = inv.evaluate(trace);
        assertEquals(AttributionVerdict.NO_ATTRIBUTION.name(), o.detail);
        assertTrue("NO_ATTRIBUTION is not a deviation", o.passed);
    }

    @Test
    public void upstreamRejection_outcomePassedFalse() {
        TraceModel trace = traceWithLeaf("ts-payment-service", "charge");
        TargetAttributionInvariant inv = new TargetAttributionInvariant(
                "POST /x", "ts-order-service", "seatNumber");
        TraceShapeVerdict.InvariantOutcome o = inv.evaluate(trace);
        assertEquals(AttributionVerdict.UPSTREAM_REJECTION.name(), o.detail);
        assertFalse("UPSTREAM = off-target deviation", o.passed);
    }

    @Test
    public void wrongParamRejection_outcomePassedFalse() {
        TraceModel trace = traceWithLeaf("ts-order-service", "validateContactsName");
        TargetAttributionInvariant inv = new TargetAttributionInvariant(
                "POST /x", "ts-order-service", "seatNumber");
        TraceShapeVerdict.InvariantOutcome o = inv.evaluate(trace);
        assertEquals(AttributionVerdict.WRONG_PARAM_REJECTION.name(), o.detail);
        assertFalse("WRONG_PARAM = off-target deviation", o.passed);
    }

    @Test
    public void nullTrace_noAttribution() {
        TargetAttributionInvariant inv = new TargetAttributionInvariant(
                "POST /x", "ts-x", "p");
        TraceShapeVerdict.InvariantOutcome o = inv.evaluate(null);
        assertEquals(AttributionVerdict.NO_ATTRIBUTION.name(), o.detail);
    }

    @Test
    public void nullTargetService_noAttribution() {
        TraceModel trace = traceWithLeaf("ts-order-service", "validateSeat");
        TargetAttributionInvariant inv = new TargetAttributionInvariant(
                "POST /x", null, "p");
        TraceShapeVerdict.InvariantOutcome o = inv.evaluate(trace);
        assertEquals(AttributionVerdict.NO_ATTRIBUTION.name(), o.detail);
    }

    @Test
    public void oracleEvaluate_appendsAttribution_whenFlagDefaultAndTargetSet() {
        TraceShapeOracle oracle = new TraceShapeOracle(new ShapeInvariantStore());
        TraceModel trace = traceWithLeaf("ts-order-service", "validateSeatNumber");
        TraceShapeVerdict v = oracle.evaluate(trace, "POST /x",
                "ts-order-service", "seatNumber");
        assertTrue("at least the attribution outcome must be present",
                hasKind(v, "TARGET_ATTRIBUTION"));
    }

    @Test
    public void oracleEvaluate_omitsAttribution_whenFlagFalse() {
        System.setProperty("mst.oracle.shape.invariants.target_attribution.enabled", "false");
        io.mist.core.config.MstConfig.resetForTesting();
        TraceShapeOracle oracle = new TraceShapeOracle(new ShapeInvariantStore());
        TraceModel trace = traceWithLeaf("ts-order-service", "validateSeatNumber");
        TraceShapeVerdict v = oracle.evaluate(trace, "POST /x",
                "ts-order-service", "seatNumber");
        assertFalse("F3 kill switch: attribution outcome must NOT appear",
                hasKind(v, "TARGET_ATTRIBUTION"));
    }

    @Test
    public void oracleEvaluate_omitsAttribution_whenTargetServiceNull() {
        TraceShapeOracle oracle = new TraceShapeOracle(new ShapeInvariantStore());
        TraceModel trace = traceWithLeaf("ts-order-service", "validateSeatNumber");
        TraceShapeVerdict v = oracle.evaluate(trace, "POST /x", null, null);
        assertFalse(hasKind(v, "TARGET_ATTRIBUTION"));
    }

    @Test
    public void oracleEvaluate_legacy2ArgOverload_omitsAttribution() {
        TraceShapeOracle oracle = new TraceShapeOracle(new ShapeInvariantStore());
        TraceModel trace = traceWithLeaf("ts-order-service", "validateSeatNumber");
        TraceShapeVerdict v = oracle.evaluate(trace, "POST /x");
        assertFalse("legacy 2-arg overload must not emit attribution",
                hasKind(v, "TARGET_ATTRIBUTION"));
    }

    // ---------------------------------------------------------------------

    private static boolean hasKind(TraceShapeVerdict v, String kind) {
        for (TraceShapeVerdict.InvariantOutcome o : v.getOutcomes()) {
            if (o != null && kind.equals(o.kind)) return true;
        }
        return false;
    }

    private static TraceModel traceWithLeaf(String leafService, String leafOp) {
        Map<String, String> tags = new HashMap<>();
        tags.put("http.status_code", "400");
        tags.put("otel.status_code", "ERROR");
        return new TraceModel("test-trace", Arrays.asList(
                new TraceModel.Span("root", null, "ts-gateway", "POST /x", 400, "ERROR", 0L, tags),
                new TraceModel.Span("leaf", "root", leafService, leafOp, 400, "ERROR", 0L, tags)));
    }

    private static TraceModel traceNoError() {
        Map<String, String> tags = new HashMap<>();
        tags.put("http.status_code", "200");
        tags.put("otel.status_code", "OK");
        return new TraceModel("test-trace", Collections.singletonList(
                new TraceModel.Span("root", null, "ts-gateway", "POST /x", 200, "OK", 0L, tags)));
    }
}
