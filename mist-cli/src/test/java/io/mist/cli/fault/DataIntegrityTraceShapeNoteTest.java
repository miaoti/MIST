package io.mist.cli.fault;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A-venue wave (2026-07-18): the flag-gated Trace Shape Oracle note in the DI
 * runtime. Pins (1) flag-off => null (legacy records byte-identical), (2) a
 * swallowed downstream 5xx under a 2xx entry => the failing detail, (3) a
 * clean trace => the compact pass note, (4) any fetch failure => null.
 */
public class DataIntegrityTraceShapeNoteTest {

    private static final String JAEGER = "{\"data\":[{\"traceID\":\"t1\",\"spans\":["
            + "{\"traceID\":\"t1\",\"spanID\":\"a\",\"operationName\":\"POST /orders\","
            + "\"tags\":[{\"key\":\"http.status_code\",\"value\":201}],\"processID\":\"p1\"},"
            + "{\"traceID\":\"t1\",\"spanID\":\"b\",\"operationName\":\"enqueue\","
            + "\"references\":[{\"refType\":\"CHILD_OF\",\"spanID\":\"a\"}],"
            + "\"tags\":[{\"key\":\"http.status_code\",\"value\":503}],\"processID\":\"p2\"}],"
            + "\"processes\":{\"p1\":{\"serviceName\":\"orders\"},\"p2\":{\"serviceName\":\"shipping\"}}}]}";

    // distinct traceID: TraceShapeAdapter caches models JVM-wide by traceId
    private static final String CLEAN = JAEGER.replace("503", "200").replace("t1", "t2");

    private static DataIntegrityRuntime.Http http(final String body) {
        return new DataIntegrityRuntime.Http() {
            @Override public DataIntegrityRuntime.HttpResponse getSut(String path) {
                return new DataIntegrityRuntime.HttpResponse(200, "{}");
            }
            @Override public DataIntegrityRuntime.HttpResponse getAbsolute(String url) {
                return new DataIntegrityRuntime.HttpResponse(200, body);
            }
        };
    }

    private void enable() {
        System.setProperty("jaeger.base.url", "http://fake/api");
        System.setProperty("mst.oracle.shape.enabled", "true");
        System.setProperty("mst.oracle.shape.invariants.hidden_downstream_failure.enabled", "true");
        io.mist.core.config.MstConfig.resetForTesting();
    }

    @After
    public void reset() {
        System.clearProperty("jaeger.base.url");
        System.clearProperty("mst.oracle.shape.enabled");
        System.clearProperty("mst.oracle.shape.invariants.hidden_downstream_failure.enabled");
        io.mist.core.config.MstConfig.resetForTesting();
    }

    @Test
    public void flagOffYieldsNull() {
        System.setProperty("jaeger.base.url", "http://fake/api");
        io.mist.core.config.MstConfig.resetForTesting();
        assertNull(DataIntegrityRuntime.traceShapeNote(http(JAEGER), "t1", "POST /orders"));
    }

    @Test
    public void swallowedDownstream5xxYieldsFailingDetail() {
        enable();
        String note = DataIntegrityRuntime.traceShapeNote(http(JAEGER), "t1", "POST /orders");
        assertTrue("note=" + note, note != null && note.contains("HIDDEN_DOWNSTREAM_FAILURE"));
        assertTrue("severity in note=" + note, note.contains("ERROR"));
    }

    @Test
    public void cleanTraceYieldsPassNote() {
        enable();
        assertEquals("HIDDEN_DOWNSTREAM_FAILURE pass (1 trace)",
                DataIntegrityRuntime.traceShapeNote(http(CLEAN), "t2", "POST /orders"));
    }

    @Test
    public void fetchFailureYieldsNull() {
        enable();
        DataIntegrityRuntime.Http broken = new DataIntegrityRuntime.Http() {
            @Override public DataIntegrityRuntime.HttpResponse getSut(String path) {
                return new DataIntegrityRuntime.HttpResponse(200, "{}");
            }
            @Override public DataIntegrityRuntime.HttpResponse getAbsolute(String url) {
                throw new RuntimeException("boom");
            }
        };
        assertNull(DataIntegrityRuntime.traceShapeNote(broken, "t1", "POST /orders"));
    }
}
