package io.mist.core.workflow.pipeline.stages;

import io.mist.core.workflow.ScenarioOptimizer;
import io.mist.core.workflow.pipeline.PipelineContext;
import io.mist.core.workflow.pipeline.PipelineStage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Phase 3: Scenario Shattering — partition fat multi-root scenarios into
 * semantically cohesive components using the dependency graph.
 *
 * <p>Gated by {@code mst.scenarioShattering.enabled}. When disabled the stage
 * is a no-op so the rest of the pipeline still runs (mirrors the previous
 * inline {@code if (shatterEnabled) { ... }} guard in {@code generate()}).
 *
 * <p>Phase 3.5 (post-shatter single-root dedup) is intentionally kept in its
 * own {@link Phase35DedupStage} so the on/off gating cleanly skips both.
 *
 * <p>The shattering loop itself lives on {@link ScenarioOptimizer}; this
 * stage owns the gate, the dependency-registry preflight, and the
 * before/after size logging that frames the call.
 */
public final class Phase3ShatteringStage implements PipelineStage {
    private static final Logger log = LogManager.getLogger(Phase3ShatteringStage.class);

    @Override
    public String name() { return "Phase 3: Scenario Shattering"; }

    @Override
    public void run(PipelineContext ctx) {
        if (!ctx.config.scenarioShattering().enabled()) {
            log.info("[Phase3ShatteringStage] disabled by config — skipping");
            return;
        }
        if (ctx.dependencyRegistry == null) {
            // Defence in depth: the optimizer dereferences the registry, and
            // a misconfigured pipeline could thread a null in. Surface the
            // failure as a skip + warn rather than an NPE three frames deep.
            log.warn("[Phase3ShatteringStage] dependencyRegistry is null — skipping shattering");
            return;
        }
        int before = ctx.scenarios.size();
        log.info("[Phase3ShatteringStage] entering with {} scenarios", before);

        new ScenarioOptimizer(ctx.dependencyRegistry).optimizeScenarios(ctx.scenarios);

        int after = ctx.scenarios.size();
        log.info("[Phase3ShatteringStage] post-shatter scenarios={} (Δ{}{})",
                after, after >= before ? "+" : "", after - before);
    }
}
