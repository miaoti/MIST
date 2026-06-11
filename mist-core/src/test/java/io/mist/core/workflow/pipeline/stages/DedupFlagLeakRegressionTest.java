package io.mist.core.workflow.pipeline.stages;

import io.mist.core.generation.MistGenerator;
import io.mist.core.registry.SemanticDependencyRegistry;
import io.mist.core.workflow.ScenarioOptimizer;
import io.mist.core.workflow.WorkflowScenario;
import io.mist.core.workflow.WorkflowStep;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Locks the approvedInDedupPass lifecycle that made Phase 3.5 dead code:
 *
 * <ul>
 *   <li>ScenarioOptimizer must NOT propagate the tag to shattered partitions —
 *       propagation made every partition skip the Phase 3.5 pass-through guard,
 *       so duplicate 1-root test classes survived (under-dedup).</li>
 *   <li>Phase 3.5 (a second {@link DedupSupport#runPass}) must drop an untagged
 *       1-root partition whose key is already approved, keep one with a new key,
 *       and never collapse multi-root scenarios.</li>
 *   <li>{@code MistGenerator.resetForNewPhase()} must clear the tag Phase A's
 *       stages left on the shared scenario instances, or Phase B's dedup passes
 *       skip every scenario while registering zero keys.</li>
 * </ul>
 */
public class DedupFlagLeakRegressionTest {

    private static WorkflowStep step(String operationName) {
        return new WorkflowStep("trace-1", "span-1", "ts-station-service", operationName,
                0L, 1L, null, null);
    }

    private static WorkflowScenario oneRoot(String operationName) {
        WorkflowScenario sc = new WorkflowScenario();
        sc.addRootStep(step(operationName));
        return sc;
    }

    // ── DedupSupport second-pass semantics (Phase 3.5) ────────────────────

    @Test
    public void secondPass_dropsUntaggedDuplicate_keepsApprovedOwner() {
        Set<String> approved = new LinkedHashSet<>();
        List<WorkflowScenario> list = new ArrayList<>();
        WorkflowScenario owner = oneRoot("GET /api/v1/stations");
        list.add(owner);
        DedupSupport.runPass("PHASE 2.5 (test)", list, approved);
        assertTrue(owner.isApprovedInDedupPass());

        // Simulates a shattered partition for the same API: new instance, untagged.
        list.add(oneRoot("GET /api/v1/stations"));
        DedupSupport.runPass("PHASE 3.5 (test)", list, approved);

        assertEquals(1, list.size());
        assertSame(owner, list.get(0));
    }

    @Test
    public void secondPass_keepsUntaggedPartitionWithNewKey_andRegistersIt() {
        Set<String> approved = new LinkedHashSet<>();
        List<WorkflowScenario> list = new ArrayList<>();
        list.add(oneRoot("GET /api/v1/stations"));
        DedupSupport.runPass("PHASE 2.5 (test)", list, approved);
        int approvedAfterFirstPass = approved.size();

        WorkflowScenario partition = oneRoot("POST /api/v1/orders");
        list.add(partition);
        DedupSupport.runPass("PHASE 3.5 (test)", list, approved);

        assertEquals(2, list.size());
        assertTrue(partition.isApprovedInDedupPass());
        assertEquals(approvedAfterFirstPass + 1, approved.size());
    }

    @Test
    public void multiRootScenario_isNeverCollapsed() {
        Set<String> approved = new LinkedHashSet<>();
        WorkflowScenario fat = new WorkflowScenario();
        fat.addRootStep(step("GET /api/v1/stations"));
        fat.addRootStep(step("POST /api/v1/orders"));
        List<WorkflowScenario> list = new ArrayList<>();
        list.add(fat);

        DedupSupport.runPass("PHASE 2.5 (test)", list, approved);
        DedupSupport.runPass("PHASE 3.5 (test)", list, approved);

        assertEquals(1, list.size());
        assertSame(fat, list.get(0));
    }

    // ── ScenarioOptimizer tag handling ────────────────────────────────────

    @Test
    public void optimizer_doesNotPropagateApprovalTagToShatteredPartitions() {
        // Empty registry → no dependency edges → a 2-root scenario shatters
        // into 2 single-root partitions.
        WorkflowScenario fat = new WorkflowScenario();
        fat.addRootStep(step("GET /api/v1/stations"));
        fat.addRootStep(step("POST /api/v1/orders"));
        fat.setApprovedInDedupPass(true);
        List<WorkflowScenario> list = new ArrayList<>();
        list.add(fat);

        new ScenarioOptimizer(SemanticDependencyRegistry.build(Collections.emptyMap()))
                .optimizeScenarios(list);

        assertEquals(2, list.size());
        for (WorkflowScenario partition : list) {
            assertFalse("shattered partition must re-enter dedup untagged",
                    partition.isApprovedInDedupPass());
        }
    }

    @Test
    public void optimizer_keepsTagOnUnshatteredScenario() {
        WorkflowScenario single = oneRoot("GET /api/v1/stations");
        single.setApprovedInDedupPass(true);
        List<WorkflowScenario> list = new ArrayList<>();
        list.add(single);

        new ScenarioOptimizer(SemanticDependencyRegistry.build(Collections.emptyMap()))
                .optimizeScenarios(list);

        assertEquals(1, list.size());
        assertSame(single, list.get(0));
        assertTrue(single.isApprovedInDedupPass());
    }

    // ── Two-phase reset ───────────────────────────────────────────────────

    @Test
    public void resetForNewPhase_clearsStaleApprovalTags() {
        WorkflowScenario sc = oneRoot("GET /api/v1/stations");
        sc.setApprovedInDedupPass(true);  // Phase A residue
        List<WorkflowScenario> scenarios = new ArrayList<>();
        scenarios.add(sc);

        OpenAPI primary = new OpenAPI();
        primary.setInfo(new Info().title("empty").version("0.0.0"));
        MistGenerator gen = new MistGenerator(
                primary, null,
                Collections.emptyMap(), Collections.emptyMap(),
                scenarios, false, false);

        gen.resetForNewPhase();

        assertFalse("Phase B must re-evaluate scenarios the Phase A pipeline tagged",
                sc.isApprovedInDedupPass());
    }
}
