package io.mist.core.workflow.pipeline;

/**
 * One step in the {@link WorkflowPipeline}. Implementations should be small
 * adapters that delegate to logic on {@code MultiServiceTestCaseGenerator}
 * — the goal of stage extraction is composition, not re-implementation.
 */
public interface PipelineStage {
    /** Human-readable label logged by {@link WorkflowPipeline#execute}. */
    String name();

    /** Run the stage against the shared mutable context. */
    void run(PipelineContext ctx);
}
