package io.mist.core.workflow.pipeline.stages;

import io.mist.core.workflow.WorkflowScenario;
import io.mist.core.workflow.pipeline.PipelineContext;
import io.mist.core.workflow.pipeline.PipelineStage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Pre-processing stage: groups scenarios by their root API and generates the
 * shared parameter pools used by the variant loop later in
 * {@code MultiServiceTestCaseGenerator.generate()}.
 *
 * <p>Both grouping and pool generation live in
 * {@link SharedPoolSupport} so the heavy lift (LLM calls, smart-fetch loops,
 * fault-pool enrolment) is exercised by reflective tests directly against
 * the helper. The stage owns the orchestration: extracting the threaded
 * state from the context, kicking off the grouping log line, and chaining
 * the pool generation against the same grouped map.
 */
public final class SharedPoolGenerationStage implements PipelineStage {
    private static final Logger log = LogManager.getLogger(SharedPoolGenerationStage.class);

    @Override
    public String name() { return "Pre-processing: Shared Pool Generation"; }

    @Override
    public void run(PipelineContext ctx) {
        if (ctx.sharedParameterPools == null || ctx.faultyParameterPools == null) {
            // Defensive: the generator wires the maps through the context;
            // a context constructed by tests for the dedup stages doesn't
            // need them and shouldn't be running this stage at all.
            log.warn("[SharedPoolGenerationStage] pool maps missing — skipping pool generation");
            return;
        }
        log.info("=== PRE-PROCESSING: Grouping scenarios by root API ===");
        Map<String, List<WorkflowScenario>> groupedScenarios =
                SharedPoolSupport.groupScenariosByRootApi(ctx.scenarios);

        // Generate shared parameter pools for each root API group; the same
        // call populates the faulty pools too because the original method
        // tail-called generateFaultyParameterPools and the lift preserves
        // that ordering.
        SharedPoolSupport.generateSharedParameterPools(
                groupedScenarios,
                ctx.serviceConfigs,
                ctx.useLLM,
                ctx.llmGen,
                ctx.smartFetcher,
                ctx.smartFetchConfig,
                ctx.sharedParameterPools,
                ctx.faultyParameterPools);
    }
}
