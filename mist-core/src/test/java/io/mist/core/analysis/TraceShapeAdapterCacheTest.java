package io.mist.core.analysis;

import io.mist.core.oracle.shape.TraceModel;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * FIXES.md F4 contract: same traceId on a second toModel call reuses the
 * cached TraceModel instance. Different traceIds get fresh instances.
 * clearCache() drops everything. Null / empty traceId still works
 * (without poisoning the cache).
 */
public class TraceShapeAdapterCacheTest {

    @Before
    public void setUp() {
        TraceShapeAdapter.clearCache();
    }

    @After
    public void tearDown() {
        TraceShapeAdapter.clearCache();
    }

    @Test
    public void sameTraceId_secondCall_reusesInstance() {
        JSONObject t1 = jaegerWrapper("trace-001", "svc-a");
        JSONObject t2 = jaegerWrapper("trace-001", "svc-a");
        TraceModel first = TraceShapeAdapter.toModel(t1, "POST /x");
        TraceModel second = TraceShapeAdapter.toModel(t2, "POST /x");
        assertSame("cache hit — same instance returned", first, second);
        assertEquals(1, TraceShapeAdapter.cacheSize());
    }

    @Test
    public void differentTraceIds_freshInstances() {
        TraceModel a = TraceShapeAdapter.toModel(jaegerWrapper("a", "x"), "POST /x");
        TraceModel b = TraceShapeAdapter.toModel(jaegerWrapper("b", "x"), "POST /x");
        assertNotSame(a, b);
        assertEquals(2, TraceShapeAdapter.cacheSize());
    }

    @Test
    public void clearCache_dropsAll() {
        TraceShapeAdapter.toModel(jaegerWrapper("a", "x"), "k");
        TraceShapeAdapter.toModel(jaegerWrapper("b", "x"), "k");
        assertEquals(2, TraceShapeAdapter.cacheSize());
        TraceShapeAdapter.clearCache();
        assertEquals(0, TraceShapeAdapter.cacheSize());
    }

    @Test
    public void emptyTraceId_doesNotPollutTheCache() {
        TraceModel a = TraceShapeAdapter.toModel(jaegerWrapper("", "x"), "k");
        TraceModel b = TraceShapeAdapter.toModel(jaegerWrapper("", "x"), "k");
        // Empty traceId is skipped on both put and get; each call builds fresh.
        assertNotSame(a, b);
        assertEquals("cache stays empty for empty traceId", 0, TraceShapeAdapter.cacheSize());
    }

    @Test
    public void nullJaegerTrace_returnsEmptyModel_doesNotPoisonCache() {
        TraceModel m = TraceShapeAdapter.toModel(null, "k");
        assertEquals("", m.getTraceId());
        assertTrue(m.getSpans().isEmpty());
        assertEquals(0, TraceShapeAdapter.cacheSize());
    }

    @Test
    public void rootApiKey_doesNotParticipateInKey() {
        // Same traceId, different rootApiKey strings → still a cache hit.
        TraceModel a = TraceShapeAdapter.toModel(jaegerWrapper("t", "x"), "POST /a");
        TraceModel b = TraceShapeAdapter.toModel(jaegerWrapper("t", "x"), "POST /b");
        assertSame("rootApiKey is not part of the cache key", a, b);
        assertEquals(1, TraceShapeAdapter.cacheSize());
    }

    // -----------------------------------------------------------------

    private static JSONObject jaegerWrapper(String traceId, String svc) {
        JSONObject wrapper = new JSONObject();
        wrapper.put("traceID", traceId);
        JSONArray spans = new JSONArray();
        JSONObject span = new JSONObject();
        span.put("spanID", "root");
        span.put("operationName", "op");
        span.put("serviceName", svc);
        JSONArray tags = new JSONArray();
        tags.put(new JSONObject().put("key", "http.status_code").put("value", 200));
        span.put("tags", tags);
        spans.put(span);
        wrapper.put("spans", spans);
        return wrapper;
    }
}
