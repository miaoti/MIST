package io.mist.core.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a multi-step workflow scenario, which may consist of multiple trace segments.
 * A WorkflowScenario contains a collection of {@link WorkflowStep} instances organized
 * by parent-child relationships (reflecting call hierarchy and sequence). It can also
 * encompass multiple trace IDs if data dependencies link separate traces into one logical workflow.
 * <p>
 * This class provides methods to collect trace IDs and root steps, and to merge scenarios
 * when cross-trace dependencies are detected. It is designed for extensibility, so new types of
 * steps (e.g., asynchronous events or fault injection steps) can be integrated in the future.
 */
public class WorkflowScenario {
    /** The set of all trace IDs represented in this scenario. */
    private final Set<String> traceIds = new LinkedHashSet<>();
    /** The list of root steps (steps with no parent in this scenario). */
    private final List<WorkflowStep> rootSteps = new ArrayList<>();
    /** The source file name (without extension) for this scenario - used for test naming */
    private String sourceFileName;

    /** Session identifier derived from client IP / auth token, for heuristic merging. */
    private String sessionIdentifier = "UNKNOWN_SESSION";
    /** Earliest span start time across all spans in this scenario (microseconds since epoch). */
    private long startTimeMicros = Long.MAX_VALUE;
    /** Latest span end time across all spans in this scenario (microseconds since epoch). */
    private long endTimeMicros = Long.MIN_VALUE;

    /**
     * When non-null this scenario was produced by trace decomposition.
     * Contains the RT suffix (e.g., "_RT1", "_RT2") appended to the parent
     * scenario's Flow_Scenario_N identifier.
     */
    private String decomposedTag = null;

    /**
     * The 1-based index of the parent multi-root scenario from which this
     * decomposed scenario was extracted.  -1 when this is NOT a decomposed scenario.
     */
    private int parentScenarioIndex = -1;

    /**
     * Phase-tag set by Phase 2.5 / Phase 3.5 dedup pass.  Survives shattering
     * because Phase 3 propagates it from a parent scenario to every child
     * component constructed during partitioning.  Mutable so the optimizer
     * can transfer it onto newly-constructed instances.
     */
    private boolean approvedInDedupPass = false;

    public String getDecomposedTag() { return decomposedTag; }
    public void setDecomposedTag(String tag) { this.decomposedTag = tag; }

    public int getParentScenarioIndex() { return parentScenarioIndex; }
    public void setParentScenarioIndex(int idx) { this.parentScenarioIndex = idx; }

    public boolean isApprovedInDedupPass() { return approvedInDedupPass; }
    public void setApprovedInDedupPass(boolean v) { this.approvedInDedupPass = v; }

    /** Creates an empty WorkflowScenario. */
    public WorkflowScenario() {
        // Nothing to initialize beyond empty collections.
    }

    /**
     * Returns an unmodifiable set of all trace IDs that are represented in this scenario.
     * If multiple traces have been merged, all their IDs will be included.
     */
    public Set<String> getTraceIds() {
        // Return a safe copy or unmodifiable view to preserve encapsulation
        return Collections.unmodifiableSet(traceIds);
    }

    /**
     * Returns an unmodifiable list of root steps of the scenario.
     * Root steps are those with no parent step within this scenario (entry points of the workflow).
     */
    public List<WorkflowStep> getRootSteps() {
        return Collections.unmodifiableList(rootSteps);
    }

    /**
     * Returns the source file name (without extension) for this scenario.
     * This is used for generating meaningful test method names.
     */
    public String getSourceFileName() {
        return sourceFileName;
    }

    /**
     * Sets the source file name (without extension) for this scenario.
     * This should be called when the scenario is created from a trace file.
     */
    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    /**
     * Adds a root step to this scenario (a step that starts an independent workflow).
     * This also registers the step's trace ID with the scenario.
     *
     * @param step the WorkflowStep to add as a root of this scenario
     */
    public void addRootStep(WorkflowStep step) {
        if (step == null) return;
        // Ensure the step is marked as a root in this scenario
        step.setParent(null);
        rootSteps.add(step);
        // Record the trace ID of this step in the scenario (if present)
        String traceId = step.getTraceId();
        if (traceId != null && !traceId.isEmpty()) {
            traceIds.add(traceId);
        }
    }

    public String getSessionIdentifier() { return sessionIdentifier; }
    public void setSessionIdentifier(String sessionIdentifier) {
        this.sessionIdentifier = sessionIdentifier != null ? sessionIdentifier : "UNKNOWN_SESSION";
    }

    public long getStartTimeMicros() { return startTimeMicros; }
    public void setStartTimeMicros(long startTimeMicros) { this.startTimeMicros = startTimeMicros; }

    public long getEndTimeMicros() { return endTimeMicros; }
    public void setEndTimeMicros(long endTimeMicros) { this.endTimeMicros = endTimeMicros; }

    /**
     * Internal helper to register an additional trace ID in this scenario.
     * This is used when merging scenarios.
     *
     * @param traceId the trace ID to add
     */
    public void addTraceId(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            traceIds.add(traceId);
        }
    }

    /**
     * Appends another scenario's root steps into this one as sequential downstream roots.
     * Used by the heuristic session-based merger when no explicit data dependency fields
     * are available but temporal proximity and shared session identity suggest a causal flow.
     *
     * <p>Transferred root steps are marked as {@code mergedRoot=true} with sequential
     * {@code producerRootIndex} values so the generator emits them as Root 2, Root 3, etc.
     *
     * @param nextScenario the chronologically later scenario to append
     */
    public void appendSequentialScenario(WorkflowScenario nextScenario) {
        if (nextScenario == null) return;

        int existingRootCount = this.rootSteps.size();

        for (WorkflowStep incomingRoot : nextScenario.rootSteps) {
            incomingRoot.setParent(null);
            incomingRoot.setMergedRoot(true);
            incomingRoot.setProducerRootIndex(existingRootCount);
            this.rootSteps.add(incomingRoot);
            existingRootCount++;
        }

        for (String tid : nextScenario.traceIds) {
            this.traceIds.add(tid);
        }

        this.endTimeMicros = Math.max(this.endTimeMicros, nextScenario.endTimeMicros);
    }

    /**
     * Merges another WorkflowScenario into this one by promoting the consumer's root step
     * to a new top-level root in this scenario (Multi-Root Sequence model).
     * The consumer is NOT attached as a child of the producer — it becomes its own Root
     * so that the Generator can emit it as a separate top-level step (Root 2, Root 3, etc.).
     *
     * @param other the other scenario to merge into this one
     * @param attachParent the step in this scenario whose output field triggered the merge
     *                     (used only for provenance tracking, NOT for parent-child linking)
     * @param attachChild the root step from the other scenario that will be promoted
     */
    public void mergeWith(WorkflowScenario other, WorkflowStep attachParent, WorkflowStep attachChild) {
        if (other == null || attachParent == null || attachChild == null) {
            return;
        }
        other.rootSteps.remove(attachChild);

        // Promote the consumer as a new top-level root (Multi-Root Sequence).
        // Record the 1-based index of the producer root that triggered the merge so the
        // Generator can later wire the DATA_DEPENDENCY between Root N and its producer.
        attachChild.setMergedRoot(true);
        attachChild.setProducerRootIndex(this.rootSteps.indexOf(attachParent) >= 0
                ? this.rootSteps.indexOf(attachParent) + 1
                : findRootIndexContaining(attachParent));
        attachChild.setParent(null);
        this.rootSteps.add(attachChild);

        for (String tid : other.traceIds) {
            this.traceIds.add(tid);
        }
        for (WorkflowStep remainingRoot : other.rootSteps) {
            if (remainingRoot != attachChild) {
                remainingRoot.setParent(null);
                this.rootSteps.add(remainingRoot);
            }
        }
    }

    /**
     * Finds the 1-based root index of the root tree that contains the given step.
     * Returns -1 if not found.
     */
    private int findRootIndexContaining(WorkflowStep step) {
        WorkflowStep current = step;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        int idx = rootSteps.indexOf(current);
        return idx >= 0 ? idx + 1 : -1;
    }

    @Override
    public String toString() {
        // Produce a human-readable representation of the scenario for debugging.
        StringBuilder sb = new StringBuilder();
        sb.append("WorkflowScenario{traceIds=").append(traceIds).append("}\n");
        for (WorkflowStep root : rootSteps) {
            printStep(root, 0, sb);
        }
        return sb.toString();
    }

    // Recursive helper to print the tree of steps with indentation.
    private void printStep(WorkflowStep step, int indent, StringBuilder sb) {
        for (int i = 0; i < indent; i++) {
            sb.append("    ");
        }
        sb.append("- ").append(step.getServiceName())
                .append("::").append(step.getOperationName())
                .append(" (trace ").append(step.getTraceId())
                .append(", span ").append(step.getSpanId()).append(")");
        sb.append("\n");
        for (WorkflowStep child : step.getChildren()) {
            printStep(child, indent + 1, sb);
        }
    }
}
