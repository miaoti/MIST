package io.mist.core.oracle.shape;

import io.mist.core.config.MstConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Pins the Phase-3 wiring of HiddenDownstreamFailureInvariant into
 * TraceShapeOracle's 4-arg evaluate: it participates only when its opt-in flag
 * is on (default OFF), so legacy verdicts are unchanged.
 */
public class TraceShapeOracleIntentTogglesTest {

    private static final String HDF = "mst.oracle.shape.invariants.hidden_downstream_failure.enabled";
    private String prevHdf;

    @Before
    public void setUp() {
        prevHdf = System.getProperty(HDF);
        System.clearProperty(HDF);
        MstConfig.resetForTesting();
    }

    @After
    public void tearDown() {
        if (prevHdf == null) System.clearProperty(HDF); else System.setProperty(HDF, prevHdf);
        MstConfig.resetForTesting();
    }

    private static TraceModel.Span span(String id, String parent, String svc, String op, int http, String otel) {
        return new TraceModel.Span(id, parent, svc, op, http, otel, 0L, new HashMap<>());
    }

    private static TraceModel hiddenFailureTrace() {
        return new TraceModel("t", Arrays.asList(
                span("root", null, "ts-gateway", "POST /x", 200, null),
                span("child", "root", "ts-order", "OrderController.create", 500, "ERROR")));
    }

    private static TraceShapeVerdict.InvariantOutcome outcome(TraceShapeVerdict v, String kind) {
        for (TraceShapeVerdict.InvariantOutcome o : v.getOutcomes()) {
            if (kind.equals(o.kind)) return o;
        }
        return null;
    }

    @Test
    public void hiddenDownstream_firesWhenFlagOn() {
        System.setProperty(HDF, "true");
        MstConfig.resetForTesting();
        TraceShapeVerdict v = new TraceShapeOracle(new ShapeInvariantStore())
                .evaluate(hiddenFailureTrace(), "POST /x", null, null);
        TraceShapeVerdict.InvariantOutcome o = outcome(v, "HIDDEN_DOWNSTREAM_FAILURE");
        assertNotNull("invariant should be wired in when flag on", o);
        assertFalse(o.passed);
    }

    @Test
    public void hiddenDownstream_absentWhenFlagOff() {
        TraceShapeVerdict v = new TraceShapeOracle(new ShapeInvariantStore())
                .evaluate(hiddenFailureTrace(), "POST /x", null, null);
        assertNull("must not run when flag off (default)", outcome(v, "HIDDEN_DOWNSTREAM_FAILURE"));
    }
}
