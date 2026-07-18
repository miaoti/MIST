package io.mist.cli.tools;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Review-A test gap: the actual offline measurement path. Pins ERROR=>flag,
 * WARN-only=>no_flag, multi-trace handling, and single-wrapper (no data array)
 * input. Distinct traceIDs per payload — TraceShapeAdapter caches JVM-wide.
 */
public class OfflineTraceShapeEvaluatorTest {

    private static String trace(String id, String entryStatusTag, String childTags) {
        return "{\"traceID\":\"" + id + "\",\"spans\":["
                + "{\"traceID\":\"" + id + "\",\"spanID\":\"a\",\"operationName\":\"POST /orders\","
                + "\"tags\":[" + entryStatusTag + "],\"processID\":\"p1\"},"
                + "{\"traceID\":\"" + id + "\",\"spanID\":\"b\",\"operationName\":\"enqueue\","
                + "\"references\":[{\"refType\":\"CHILD_OF\",\"spanID\":\"a\"}],"
                + "\"tags\":[" + childTags + "],\"processID\":\"p2\"}],"
                + "\"processes\":{\"p1\":{\"serviceName\":\"orders\"},\"p2\":{\"serviceName\":\"shipping\"}}}";
    }

    private static final String T_2XX = "{\"key\":\"http.status_code\",\"value\":201}";
    private static final String T_5XX = "{\"key\":\"http.status_code\",\"value\":503}";
    private static final String T_OTEL_ERR = "{\"key\":\"otel.status_code\",\"value\":\"ERROR\"}";
    private static final String T_OK = "{\"key\":\"http.status_code\",\"value\":200}";

    @Before
    public void enable() {
        System.setProperty("mst.oracle.shape.enabled", "true");
        System.setProperty("mst.oracle.shape.invariants.hidden_downstream_failure.enabled", "true");
        System.setProperty("mst.oracle.shape.invariants.span_tree.enabled", "false");
        System.setProperty("mst.oracle.shape.invariants.status_propagation.enabled", "false");
        System.setProperty("mst.oracle.shape.invariants.response_envelope.enabled", "false");
        System.setProperty("mst.oracle.shape.invariants.target_attribution.enabled", "false");
        io.mist.core.config.MstConfig.resetForTesting();
    }

    @After
    public void reset() {
        for (String k : new String[]{"mst.oracle.shape.enabled",
                "mst.oracle.shape.invariants.hidden_downstream_failure.enabled",
                "mst.oracle.shape.invariants.span_tree.enabled",
                "mst.oracle.shape.invariants.status_propagation.enabled",
                "mst.oracle.shape.invariants.response_envelope.enabled",
                "mst.oracle.shape.invariants.target_attribution.enabled"}) {
            System.clearProperty(k);
        }
        io.mist.core.config.MstConfig.resetForTesting();
    }

    @Test
    public void swallowed5xxFlags() {
        JSONObject in = new JSONObject("{\"data\":[" + trace("ots1", T_2XX, T_5XX) + "]}");
        JSONObject out = OfflineTraceShapeEvaluator.evaluate(in, "POST /orders");
        assertEquals("flag", out.getString("verdict"));
        assertEquals(1, out.getInt("traces_evaluated"));
    }

    @Test
    public void warnOnlyOtelErrorStaysNoFlag() {
        JSONObject in = new JSONObject("{\"data\":[" + trace("ots2", T_2XX, T_OTEL_ERR) + "]}");
        JSONObject out = OfflineTraceShapeEvaluator.evaluate(in, "POST /orders");
        assertEquals("no_flag", out.getString("verdict"));
    }

    @Test
    public void multiTraceAnyErrorFlags() {
        JSONObject in = new JSONObject("{\"data\":[" + trace("ots3", T_2XX, T_OK) + ","
                + trace("ots4", T_2XX, T_5XX) + "]}");
        JSONObject out = OfflineTraceShapeEvaluator.evaluate(in, "POST /orders");
        assertEquals("flag", out.getString("verdict"));
        assertEquals(2, out.getInt("traces_evaluated"));
    }

    @Test
    public void singleWrapperWithoutDataArray() {
        JSONObject in = new JSONObject(trace("ots5", T_2XX, T_OK));
        JSONObject out = OfflineTraceShapeEvaluator.evaluate(in, "POST /orders");
        assertEquals("no_flag", out.getString("verdict"));
        assertEquals(1, out.getInt("traces_evaluated"));
    }
}
