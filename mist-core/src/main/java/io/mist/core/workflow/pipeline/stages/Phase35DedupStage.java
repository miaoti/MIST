package io.mist.core.workflow.pipeline.stages;

import io.mist.core.workflow.pipeline.PipelineContext;
import io.mist.core.workflow.pipeline.PipelineStage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Phase 3.5: Post-shatter single-root deduplication.
 *
 * <p>Shattering can emit NEW 1-root partitions (isolated connected components)
 * that never went through the Phase 2.5 dedup filter. This stage re-applies the
 * 1-root dedup against the same {@code approvedApiKeys} set to prevent
 * byte-identical duplicate test classes for the same parameterless endpoint.
 * Shattered children arrive UNtagged (ScenarioOptimizer does not propagate
 * {@code approvedInDedupPass}) so this pass actually evaluates them; scenarios
 * Phase 3 left untouched are the same tagged instances Phase 2.5 approved and
 * short-circuit through {@link DedupSupport#runPass} unchanged.
 *
 * <p>This stage is gated by the same {@code mst.scenarioShattering.enabled}
 * flag as {@link Phase3ShatteringStage}. Running it without shattering would
 * be wasted work (the input is unchanged from Phase 2.5).
 */
public final class Phase35DedupStage implements PipelineStage {
    private static final Logger log = LogManager.getLogger(Phase35DedupStage.class);
    private static final String LABEL = "PHASE 3.5: POST-SHATTER SINGLE-ROOT DEDUPLICATION";

    @Override
    public String name() { return "Phase 3.5: Post-Shatter Single-Root Dedup"; }

    @Override
    public void run(PipelineContext ctx) {
        if (!ctx.config.scenarioShattering().enabled()) {
            log.info("[Phase35DedupStage] shattering disabled — skipping post-shatter dedup");
            return;
        }
        int before = ctx.scenarios.size();
        if (before == 0) {
            log.info("[Phase35DedupStage] no scenarios after shattering — short-circuiting");
            return;
        }
        int approvedBefore = ctx.approvedApiKeys.size();
        log.debug("[Phase35DedupStage] entering with scenarios={} approvedKeys={}",
                before, approvedBefore);

        DedupSupport.runPass(LABEL, ctx.scenarios, ctx.approvedApiKeys);

        int after = ctx.scenarios.size();
        int approvedAfter = ctx.approvedApiKeys.size();
        log.debug("[Phase35DedupStage] post-dedup scenarios={} (Δ={}) approvedKeys={} (Δ+{})",
                after, after - before, approvedAfter, approvedAfter - approvedBefore);
    }
}
