package io.mist.core.workflow;

import io.mist.core.util.ApiClient;
import io.mist.core.util.ApiResponse;
import io.mist.core.util.TraceValidator;
import io.mist.core.workflow.WorkflowScenario;
import io.mist.core.workflow.WorkflowStep;

import org.json.JSONObject;

import java.util.*;


public class WorkflowTestCase {

    private final WorkflowScenario scenario;
    private final String           name;

    private final ApiClient      apiClient   = new ApiClient();
    private final TraceValidator traceVal    = new TraceValidator();

    /** collected outputs – used to inject into later requests */
    private final Map<String,String> context = new HashMap<>();

    /** traceparent header from first request (for later span checks) */
    private String traceparentHeader = null;

    public WorkflowTestCase(WorkflowScenario scenario, String name) {
        this.scenario = scenario;
        this.name     = name;
    }

    /* ------------------------------------------------------------------ */
    public void execute() throws AssertionError {
        System.out.println("\n=== EXECUTING WORKFLOW TEST: " + name + " ===");

        // 1. Flatten scenario to deterministic order (parent before child)
        List<WorkflowStep> ordered = flatten(scenario);

        // 2. Run each step
        for (WorkflowStep step : ordered) {
            System.out.println("→ " + step.getServiceName() + "  ::  " + step.getOperationName());

            // --- 2a. Build mutable input map & inject dependencies
            Map<String,String> inputs = new HashMap<>(step.getInputFields());
            injectDependencies(inputs);

            // --- 2b. Prepare headers (traceparent on first call)
            Map<String,String> headers = new HashMap<>();
            if (traceparentHeader != null)
                headers.put("traceparent", traceparentHeader);

            // --- 2c. Call service
            ApiResponse resp;
            try {
                resp = apiClient.call(
                        step.getServiceName(),
                        step.getOperationName(),
                        inputs,
                        headers
                );
            } catch (Exception e) {
                throw new AssertionError("HTTP call failed: " + e.getMessage(), e);
            }

            // store trace header for later validation
            if (traceparentHeader == null)
                traceparentHeader = resp.getTraceId();

            // --- 2d. Validate status
            int expectedStatus = 200; // default
            String statusStr = step.getOutputFields().get("http.status_code");
            if (statusStr != null && !statusStr.isEmpty()) {
                try { expectedStatus = Integer.parseInt(statusStr); }
                catch (NumberFormatException ignore) {}
            }
            if (resp.getStatusCode() != expectedStatus) {
                throw new AssertionError("Step " + step.getOperationName()
                        + " returned " + resp.getStatusCode()
                        + " (expected " + expectedStatus + ")");
            }

            // --- 2e. Validate & capture body
            captureOutputs(step, resp.getBody());
        }

        // 3. Trace‑based validation
        if (!traceVal.validateTrace(traceparentHeader, ordered)) {
            throw new AssertionError("Trace validation failed – expected spans missing");
        }

        System.out.println("=== WORKFLOW PASSED ===\n");
    }

    /* ------------------------------------------------------------------ */
    private void injectDependencies(Map<String,String> inputs) {
        for (Map.Entry<String,String> e : new HashMap<>(inputs).entrySet()) {
            if (context.containsKey(e.getKey())) {
                inputs.put(e.getKey(), context.get(e.getKey()));
            }
        }
    }

    /** parse response JSON & update context for downstream steps */
    private void captureOutputs(WorkflowStep step, String bodyJson) {
        if (bodyJson == null || bodyJson.isEmpty()) return;

        try {
            JSONObject json = new JSONObject(bodyJson);
            json.keySet().forEach(k -> {
                String val = json.optString(k, null);
                if (val != null && !val.isEmpty())
                    context.put(k, val);
            });
        } catch (Exception ignore) {
            // not JSON – skip
        }
    }

    /* depth‑first pre‑order traversal so parents come before children */
    private List<WorkflowStep> flatten(WorkflowScenario s) {
        List<WorkflowStep> list = new ArrayList<>();
        for (WorkflowStep root : s.getRootSteps()) dfs(root, list);
        return list;
    }
    private void dfs(WorkflowStep node, List<WorkflowStep> out) {
        out.add(node);
        node.getChildren().forEach(c -> dfs(c, out));
    }
}
