package io.mist.core.workflow.pipeline.stages;

import io.mist.core.workflow.pipeline.PipelineContext;
import io.mist.core.workflow.pipeline.PipelineStage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Phase 4: Trace Decomposition — extract individual 1-Root baseline
 * scenarios from multi-root workflows to guarantee per-API coverage.
 *
 * <p>The decomposition loop itself lives in
 * {@link DecompositionSupport#decomposeMultiRootScenarios} (lifted verbatim
 * from the previous {@code MultiServiceTestCaseGenerator} method); the
 * stage owns the no-op guard, the snapshot logging, and the contract with
 * {@code approvedApiKeys} that prevents the decomposed {@code _RT}
 * baselines from duplicating an already-approved standalone 1-root
 * scenario.
 */
public final class Phase4DecompositionStage implements PipelineStage {
    private static final Logger log = LogManager.getLogger(Phase4DecompositionStage.class);

    @Override
    public String name() { return "Phase 4: Trace Decomposition"; }

    @Override
    public void run(PipelineContext ctx) {
        if (ctx.scenarios.isEmpty()) {
            log.info("[Phase4DecompositionStage] no scenarios to decompose — short-circuiting");
            return;
        }
        int before = ctx.scenarios.size();
        int approvedBefore = ctx.approvedApiKeys.size();
        log.debug("[Phase4DecompositionStage] entering with scenarios={} approvedKeys={}",
                before, approvedBefore);

        DecompositionSupport.decomposeMultiRootScenarios(ctx.scenarios, ctx.approvedApiKeys);

        int after = ctx.scenarios.size();
        int approvedAfter = ctx.approvedApiKeys.size();
        log.debug("[Phase4DecompositionStage] post-decomposition scenarios={} (Δ+{}) approvedKeys={} (Δ+{})",
                after, after - before, approvedAfter, approvedAfter - approvedBefore);
    }
}
