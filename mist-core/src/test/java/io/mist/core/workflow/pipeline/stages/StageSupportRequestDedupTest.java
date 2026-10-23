package io.mist.core.workflow.pipeline.stages;

import io.mist.core.spec.Operation;
import io.mist.core.testcase.MultiServiceTestCase;
import io.mist.core.testcase.TestCase;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Tests {@link StageSupport#dropDuplicateRequests}: with mst.generation.dedup.requests on, a run keeps
 * one test per distinct request sequence, so no two executed tests send the same request.
 */
public class StageSupportRequestDedupTest {

    @Test
    public void identicalRequestsKeepTheFirst() {
        TestCase a = test("a", "/api/v1/products/0/reviews", null, null);
        TestCase b = test("b", "/api/v1/products/0/reviews", null, null);
        List<TestCase> kept = StageSupport.dropDuplicateRequests(Arrays.asList(a, b));
        assertEquals(1, kept.size());
        assertSame(a, kept.get(0));
    }

    @Test
    public void differentPathsAreKept() {
        TestCase a = test("a", "/api/v1/products/0/reviews", null, null);
        TestCase b = test("b", "/api/v1/products/7/reviews", null, null);
        assertEquals(2, StageSupport.dropDuplicateRequests(Arrays.asList(a, b)).size());
    }

    @Test
    public void differentQueryValuesAreKept() {
        TestCase a = test("a", "/catalogue", "size=1", null);
        TestCase b = test("b", "/catalogue", "size=2", null);
        assertEquals(2, StageSupport.dropDuplicateRequests(Arrays.asList(a, b)).size());
    }

    @Test
    public void sameStepsWithDifferentFaultTargetsAreKept() {
        // the writer emits a query-located fault value at send time, so the fault target is part of
        // the request even when the step's own query map is the same
        TestCase a = test("a", "/catalogue", "size=1", "size=-1");
        TestCase b = test("b", "/catalogue", "size=1", "size=99999999999");
        assertEquals(2, StageSupport.dropDuplicateRequests(Arrays.asList(a, b)).size());
    }

    private static TestCase test(String name, String path, String query, String fault) {
        Operation op = new Operation();
        op.setMethod("GET");
        Map<String, String> q = new LinkedHashMap<>();
        if (query != null) {
            String[] kv = query.split("=", 2);
            q.put(kv[0], kv[1]);
        }
        MultiServiceTestCase tc = new MultiServiceTestCase(name);
        tc.addStepCall(new MultiServiceTestCase.StepCall("svc", op, path, null, q, null, null, 200, null));
        if (fault != null) {
            String[] kv = fault.split("=", 2);
            tc.addFaultyParameter(kv[0], kv[1]);
        }
        return tc;
    }
}
