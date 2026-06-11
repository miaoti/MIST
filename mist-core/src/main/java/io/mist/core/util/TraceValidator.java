package io.mist.core.util;

import io.mist.core.workflow.WorkflowStep;

import java.util.*;
import java.util.stream.Collectors;

/**
 * **Stub implementation** — in real life you would query Jaeger / Zipkin /
 * the OpenTelemetry collector here.  The class is isolated so swapping in a
 * real implementation later does NOT require touching the test‑runner logic.
 */
public class TraceValidator {

    /** Validates that every <service::operation> in expectedSteps appears in the recorded trace. */
    public boolean validateTrace(String traceparent,
                                 List<WorkflowStep> expectedSteps) {

        // (1) look up real trace ID if needed
        String traceId = extractTraceId(traceparent);

        // (2) ⬇️  Fetch spans from tracing backend (stubbed)
        Set<String> recordedOps = fetchSpans(traceId);

        // (3) compute expected op‑signatures
        Set<String> expectedOps = expectedSteps.stream()
                .map(s -> signature(s.getServiceName(), s.getOperationName()))
                .collect(Collectors.toSet());

        // (4) compare
        boolean allPresent = recordedOps.containsAll(expectedOps);

        System.out.println("[TraceValidator] traceId=" + traceId);
        System.out.println("[TraceValidator] expected : " + expectedOps);
        System.out.println("[TraceValidator] recorded : " + recordedOps);
        System.out.println("[TraceValidator] RESULT   : " + (allPresent ? "PASS" : "FAIL"));

        return allPresent;
    }

    /* ------- helpers ---------------------------------------------------- */
    private Set<String> fetchSpans(String traceId) {
        // TODO: replace with Jaeger/Zipkin/OpenTelemetry call.
        // For now we just pretend everything is present.
        return new HashSet<>(Collections.singletonList("dummy::span"));
    }

    private static String extractTraceId(String traceparent) {
        // spec: "00-<32 hex trace‑id>-<16 hex span‑id>-01"
        try { return traceparent.split("-")[1]; }
        catch (Exception e) { return "unknown"; }
    }

    private static String signature(String service, String op) {
        return service + "::" + op;
    }
}
