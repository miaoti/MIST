package io.mist.core.workflow.pipeline.stages;

import io.mist.core.workflow.pipeline.PipelineContext;
import io.mist.core.workflow.pipeline.PipelineStage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Phase 2.5: collapse duplicate 1-root scenarios before any downstream
 * processing.
 *
 * <p>The dedup loop itself lives in the package-private
 * {@link DedupSupport#runPass(String, java.util.List, java.util.Set)} helper
 * because Phase 3.5 runs the same logic against the same approval set after
 * shattering. The stage owns the label, the precondition check, and the
 * snapshot logging that bookends the call.
 */
public final class Phase25DedupStage implements PipelineStage {
    private static final Logger log = LogManager.getLogger(Phase25DedupStage.class);
    private static final String LABEL = "PHASE 2.5: SINGLE-ROOT SCENARIO DEDUPLICATION";

    @Override
    public String name() { return "Phase 2.5: Single-Root Dedup"; }

    @Override
    public void run(PipelineContext ctx) {
        // Snapshot the pre-dedup size for the bookend log line below — the
        // dedup pass mutates the list in place so we cannot read it after the
        // call. Empty list is still legal: the dedup pass is a no-op then.
        int before = ctx.scenarios.size();
        if (before == 0) {
            log.info("[Phase25DedupStage] no scenarios to dedup — short-circuiting");
            return;
        }
        int approvedBefore = ctx.approvedApiKeys.size();
        log.debug("[Phase25DedupStage] entering with scenarios={} approvedKeys={}",
                before, approvedBefore);

        DedupSupport.runPass(LABEL, ctx.scenarios, ctx.approvedApiKeys);

        int after = ctx.scenarios.size();
        int approvedAfter = ctx.approvedApiKeys.size();
        log.debug("[Phase25DedupStage] post-dedup scenarios={} (Δ={}) approvedKeys={} (Δ+{})",
                after, after - before, approvedAfter, approvedAfter - approvedBefore);
    }
}
