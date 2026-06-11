package io.mist.core.workflow.pipeline;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Sequential composition of {@link PipelineStage}s. Stages run in submission
 * order against a single shared {@link PipelineContext}; if a stage throws,
 * subsequent stages do NOT run (matches the previous in-line behaviour of
 * {@code MultiServiceTestCaseGenerator.generate()}).
 */
public final class WorkflowPipeline {
    private static final Logger log = LogManager.getLogger(WorkflowPipeline.class);
    private final List<PipelineStage> stages;

    public WorkflowPipeline(List<PipelineStage> stages) {
        this.stages = stages;
    }

    public void execute(PipelineContext ctx) {
        for (PipelineStage s : stages) {
            log.info("[Pipeline] running stage: {}", s.name());
            s.run(ctx);
        }
    }
}
