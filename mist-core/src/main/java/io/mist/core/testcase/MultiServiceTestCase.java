package io.mist.core.testcase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.mist.core.spec.Operation;
import io.mist.core.value.ValueProvenance;
import io.swagger.v3.oas.models.PathItem.HttpMethod;


/**
 * Multi-service workflow test case carrier for the mist-core
 * generation pipeline. Extends the base {@link TestCase} carrier
 * with the per-step structure used to drive a chain of HTTP calls
 * across multiple microservices.
 *
 * <p>An auth-manipulation field for status-code exploration is
 * intentionally absent — that feature lives on the adapter side
 * and uses its own carrier so mist-core does not pull in
 * adapter-flavoured auth types.
 */
public class MultiServiceTestCase extends TestCase {

    /* -------- synthetic "root" values needed by super-class -------- */
    private static String newSyntheticId() {
        /* completely ASCII: "workflow_" + UUID with '-' swapped for '_' */
        return "workflow_" + java.util.UUID
                .randomUUID()
                .toString()
                .replace('-', '_');
    }

    /* -------- constructors -------- */

    /** Default constructor used by the generator. */
    public MultiServiceTestCase() {
        super(newSyntheticId(),                 // id
                false,                            // faulty
                "workflow",                       // operationId placeholder
                "/",                              // path placeholder
                HttpMethod.POST);                 // dummy HTTP verb
        setEnableOracles(false);                // we assert per-step, not "root"
    }

    /* Optional convenience: create and name a test */
    public MultiServiceTestCase(String name) {
        this();
        setOperationId(name);
    }

    /* -------- multi-step data -------- */

    private final List<StepCall> steps = new ArrayList<>();

    /* name of the logical scenario this test case belongs to */
    private String scenarioName;

    /* track faulty parameters for Allure reporting */
    private final List<String> faultyParameters = new ArrayList<>();

    /** The hierarchical root ID that was targeted by the sniper fault injection (e.g. "Root 2"). */
    private String targetFaultRootId;

    /** The fault-type id used for this negative test (e.g. "OVERFLOW", "BOUNDARY_VIOLATION"). */
    private String faultTypeCategory;

    /** The actual API path of the targeted fault root (e.g. "POST /api/v1/orderservice/order"). */
    private String targetFaultRootApiPath;

    /**
     * Normalised location of the faulted parameter ({@code path|query|header|cookie|body}).
     */
    private String targetFaultParamLocation;

    /* -------- status code exploration fields -------- */

    /** Flag indicating this test was created for status code exploration */
    private boolean isStatusCodeExplorationTest = false;

    /** Target status code this exploration test is trying to trigger (-1 = not targeting specific code) */
    private int targetStatusCode = -1;

    /** Add a step (request/response) to the workflow. */
    public void addStepCall(StepCall step) {
        steps.add(step);
    }

    /** Ordered list of workflow steps. */
    public List<StepCall> getSteps() {
        return steps;
    }

    @Override
    public void setScenarioName(String s) {
        this.scenarioName = s;
    }

    @Override
    public String getScenarioName() { return scenarioName; }

    /** Track a parameter that was made faulty in this test case */
    public void addFaultyParameter(String paramName, String faultyValue) {
        faultyParameters.add(paramName + "=" + faultyValue);
    }

    /** Get list of faulty parameters for reporting */
    public List<String> getFaultyParameters() {
        return faultyParameters;
    }

    public String getTargetFaultRootId() { return targetFaultRootId; }
    public void setTargetFaultRootId(String targetFaultRootId) { this.targetFaultRootId = targetFaultRootId; }

    public String getFaultTypeCategory() { return faultTypeCategory; }
    public void setFaultTypeCategory(String faultTypeCategory) { this.faultTypeCategory = faultTypeCategory; }

    public String getTargetFaultRootApiPath() { return targetFaultRootApiPath; }
    public void setTargetFaultRootApiPath(String targetFaultRootApiPath) { this.targetFaultRootApiPath = targetFaultRootApiPath; }

    public String getTargetFaultParamLocation() { return targetFaultParamLocation; }
    public void setTargetFaultParamLocation(String targetFaultParamLocation) { this.targetFaultParamLocation = targetFaultParamLocation; }

    private Object targetFaultValue;
    private boolean hasTargetFaultValue = false;

    public Object getTargetFaultValue() { return targetFaultValue; }
    public boolean hasTargetFaultValue() { return hasTargetFaultValue; }
    public void setTargetFaultValue(Object value) {
        this.targetFaultValue = value;
        this.hasTargetFaultValue = true;
    }

    /* -------- value provenance -------- */

    private final Map<String, ValueProvenance> parameterProvenance = new LinkedHashMap<>();

    /** Record how a value at the given step / parameter was obtained. */
    public void recordParameterProvenance(int stepIndex, String paramName, ValueProvenance provenance) {
        if (paramName == null || provenance == null) return;
        parameterProvenance.put(stepIndex + ":" + paramName, provenance);
    }

    /** Immutable view of the parameter provenance map. */
    public Map<String, ValueProvenance> getParameterProvenance() {
        return java.util.Collections.unmodifiableMap(parameterProvenance);
    }

    /**
     * True when at least one parameter value in this test case is a
     * {@link ValueProvenance#SYNTHETIC_PLACEHOLDER}.
     */
    public boolean hasSyntheticPlaceholder() {
        for (ValueProvenance p : parameterProvenance.values()) {
            if (p == ValueProvenance.SYNTHETIC_PLACEHOLDER) return true;
        }
        return false;
    }

    /* -------- status code exploration methods -------- */

    public void setStatusCodeExplorationTest(boolean isExplorationTest) {
        this.isStatusCodeExplorationTest = isExplorationTest;
    }

    public boolean isStatusCodeExplorationTest() {
        return isStatusCodeExplorationTest;
    }

    public void setTargetStatusCode(int statusCode) {
        this.targetStatusCode = statusCode;
    }

    public int getTargetStatusCode() {
        return targetStatusCode;
    }

    /**
     * Per-test auth-manipulation override consumed by the writer's
     * status-code-exploration path. {@code null} ⇒ use the default
     * auth configuration. The {@link io.mist.core.auth.AuthManipulationStrategy.AuthConfig}
     * type is the carrier; the writer + StatusCodeExplorationEnhancer
     * agree on the same enum.
     */
    private io.mist.core.auth.AuthManipulationStrategy.AuthConfig authManipulation = null;

    public void setAuthManipulation(io.mist.core.auth.AuthManipulationStrategy.AuthConfig authConfig) {
        this.authManipulation = authConfig;
    }

    public io.mist.core.auth.AuthManipulationStrategy.AuthConfig getAuthManipulation() {
        return authManipulation;
    }

    /**
     * Get a description of the target status code for Allure reporting.
     */
    public String getTargetStatusCodeDescription() {
        if (!isStatusCodeExplorationTest || targetStatusCode < 0) {
            return "Default";
        }

        String category;
        if (targetStatusCode >= 200 && targetStatusCode < 300) {
            category = "Success";
        } else if (targetStatusCode >= 400 && targetStatusCode < 500) {
            category = "Client Error";
        } else if (targetStatusCode >= 500 && targetStatusCode < 600) {
            category = "Server Error";
        } else {
            category = "Other";
        }

        return targetStatusCode + " " + category;
    }


    /**
     * One REST call inside the workflow.
     */
    public static class StepCall {
        private final String serviceName;
        private final Operation method;               // "get", "post", …
        private final String path;                 // URI template
        private final Map<String,String> pathParams;
        private final Map<String,String> queryParams;
        private final Map<String,String> headers;
        private final Map<String,String> cookies = new LinkedHashMap<>();
        private final String body;                 // JSON or form string
        private final int expectedStatus;
        private List<String> outputKeys;

        private final Map<String,String> bodyFields;

        private final List<String> captureOutputKeys = new ArrayList<>();

        private final Map<String,Dependency> paramDependencies = new LinkedHashMap<>();

        private final List<Integer> workflowDependencies = new ArrayList<>();

        private DependencyType dependencyType = DependencyType.INDEPENDENT;

        /** Hierarchical step ID (e.g., "R1", "R2", "R1.1", "R1.2.3"). */
        private String hierarchicalId = "";
        /** True if this step represents a top-level Root API (not an internal span). */
        private boolean topLevelRoot = false;
        /** True if this step was a Root API merged from another trace via data dependency. */
        private boolean mergedRootStep = false;
        /** 1-based index of the producer root this merged step depends on, or -1. */
        private int producerRootIndex = -1;
        /** Provenance bindings: paramKey -> concrete value inherited from the producer root. */
        private final Map<String, String> provenanceBindings = new LinkedHashMap<>();

        /** Raw response body from the trace span (for jsonPath extraction by the writer). */
        private String traceResponseBody;

        public StepCall(String serviceName, Operation method, String path,
                        Map<String,String> pathParams,
                        Map<String,String> queryParams,
                        Map<String,String> headers,
                        String body, int expectedStatus,
                        Map<String,String> bodyFields) {

            this.serviceName = serviceName;
            this.method      = method;
            this.path        = path;
            this.pathParams  = (pathParams  != null ? pathParams  : new LinkedHashMap<>());
            this.queryParams = (queryParams != null ? queryParams : new LinkedHashMap<>());
            this.headers     = (headers     != null ? headers     : new LinkedHashMap<>());
            this.body        = body;
            this.expectedStatus = expectedStatus;
            this.bodyFields  = (bodyFields  != null ? new LinkedHashMap<>(bodyFields)
                    : new LinkedHashMap<>());
        }

        /* getters */
        public String getServiceName()           { return serviceName; }
        public Operation getMethod()                { return method; }
        public String getPath()                  { return path; }
        public Map<String,String> getPathParams(){ return pathParams; }
        public Map<String,String> getQueryParams(){ return queryParams; }
        public Map<String,String> getHeaders()   { return headers; }
        public Map<String,String> getCookies()   { return cookies; }
        public String getBody()                  { return body; }
        public int getExpectedStatus()           { return expectedStatus; }
        public Map<String,String> getBodyFields(){ return bodyFields; }
        public List<String> getCaptureOutputKeys(){ return captureOutputKeys; }
        public Map<String,Dependency> getParamDependencies(){ return paramDependencies; }

        public List<Integer> getWorkflowDependencies() { return workflowDependencies; }
        public DependencyType getDependencyType() { return dependencyType; }

        public void addCaptureOutputKey(String key) {
            if (!captureOutputKeys.contains(key)) captureOutputKeys.add(key);
        }
        public void addParamDependency(String param,
                                       int sourceStepIdx,
                                       String sourceKey) {
            paramDependencies.put(param, new Dependency(sourceStepIdx, sourceKey));
        }

        public String getHierarchicalId() { return hierarchicalId; }
        public void setHierarchicalId(String id) { this.hierarchicalId = id; }

        public boolean isTopLevelRoot() { return topLevelRoot; }
        public void setTopLevelRoot(boolean topLevelRoot) { this.topLevelRoot = topLevelRoot; }

        public boolean isMergedRootStep() { return mergedRootStep; }
        public void setMergedRootStep(boolean mergedRootStep) { this.mergedRootStep = mergedRootStep; }

        public int getProducerRootIndex() { return producerRootIndex; }
        public void setProducerRootIndex(int idx) { this.producerRootIndex = idx; }

        public Map<String, String> getProvenanceBindings() { return provenanceBindings; }

        public String getTraceResponseBody() { return traceResponseBody; }
        public void setTraceResponseBody(String body) { this.traceResponseBody = body; }
        public void addProvenanceBinding(String key, String value) {
            provenanceBindings.put(key, value);
        }

        public void addWorkflowDependency(int stepIndex) {
            if (!workflowDependencies.contains(stepIndex)) {
                workflowDependencies.add(stepIndex);
            }
        }

        public void setDependencyType(DependencyType type) {
            this.dependencyType = type;
        }

        public ExecutionDecision shouldExecute(Map<Integer, Boolean> stepResults) {
            switch (dependencyType) {
                case DATA_DEPENDENCY:
                    for (Dependency dep : paramDependencies.values()) {
                        if (!stepResults.getOrDefault(dep.sourceStepIndex, false)) {
                            return new ExecutionDecision(false, SkipReason.DATA_DEPENDENCY_FAILED,
                                    "Required data from step " + dep.sourceStepIndex + " is not available");
                        }
                    }
                    return new ExecutionDecision(true, null, null);

                case WORKFLOW_DEPENDENCY:
                    for (int workflowDep : workflowDependencies) {
                        if (!stepResults.getOrDefault(workflowDep, false)) {
                            return new ExecutionDecision(false, SkipReason.WORKFLOW_DEPENDENCY_FAILED,
                                    "Workflow predecessor step " + workflowDep + " failed");
                        }
                    }
                    return new ExecutionDecision(true, null, null);

                case INDEPENDENT:
                default:
                    return new ExecutionDecision(true, null, null);
            }
        }

        public void addParameter(String key, String value) {
        }

        public void setCaptureOutputKeys(List<String> outputKeys) {
            this.outputKeys = outputKeys;
        }
    }

    public static class Dependency {
        public final int    sourceStepIndex;
        public final String sourceOutputKey;
        /** Pre-computed type-safe fallback value for resilient bypass when source step fails. */
        public String fallbackValue;
        public Dependency(int idx, String key) {
            this.sourceStepIndex = idx;
            this.sourceOutputKey = key;
            this.fallbackValue   = null;
        }
    }

    /**
     * Classifies different types of step dependencies.
     */
    public enum DependencyType {
        /** Step needs data output from previous steps to function correctly. */
        DATA_DEPENDENCY,
        /** Step is part of a workflow sequence and depends on workflow flow. */
        WORKFLOW_DEPENDENCY,
        /** Step can execute independently of other step results. */
        INDEPENDENT
    }

    /**
     * Result of execution decision for a step.
     */
    public static class ExecutionDecision {
        public final boolean shouldExecute;
        public final SkipReason skipReason;
        public final String skipMessage;

        public ExecutionDecision(boolean shouldExecute, SkipReason skipReason, String skipMessage) {
            this.shouldExecute = shouldExecute;
            this.skipReason = skipReason;
            this.skipMessage = skipMessage;
        }
    }

    /**
     * Reasons why a step might be skipped.
     */
    public enum SkipReason {
        DATA_DEPENDENCY_FAILED("Data dependency failed"),
        WORKFLOW_DEPENDENCY_FAILED("Workflow dependency failed"),
        SERVICE_UNAVAILABLE("Service unavailable"),
        AUTH_FAILED("Authentication failed");

        public final String description;

        SkipReason(String description) {
            this.description = description;
        }
    }
}
