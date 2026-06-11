package io.mist.core.workflow;

import io.mist.core.registry.SemanticDependencyRegistry;
import io.mist.core.workflow.WorkflowScenario;
import io.mist.core.workflow.WorkflowStep;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Post-merge optimizer that shatters fat multi-root scenarios into smaller,
 * semantically cohesive partitions using Weakly Connected Components analysis.
 *
 * <p><b>Problem:</b> The session-based heuristic merger (Phase 2) groups traces by
 * client IP and temporal proximity. This may produce scenarios containing root APIs
 * that have zero data dependencies on each other (e.g., interleaved requests from
 * multiple browser tabs, or unrelated APIs that happen to be close in time).
 *
 * <p><b>Solution:</b> For each multi-root scenario, build a directed graph where
 * nodes are root steps and edges represent semantic data dependencies (via
 * {@link SemanticDependencyRegistry#hasDirectedDependency}). Then compute
 * Weakly Connected Components: each component becomes its own scenario. Isolated
 * nodes (no incoming or outgoing edges) become independent 1-root scenarios.
 */
public class ScenarioOptimizer {

    private static final Logger log = LogManager.getLogger(ScenarioOptimizer.class);

    private final SemanticDependencyRegistry registry;

    public ScenarioOptimizer(SemanticDependencyRegistry registry) {
        this.registry = registry;
    }

    /**
     * Shatters multi-root scenarios in-place. Scenarios with 0 or 1 root steps are
     * passed through unchanged. Multi-root scenarios are partitioned into weakly
     * connected components based on the semantic dependency graph.
     *
     * @param scenarios the mutable list of scenarios (modified in-place)
     */
    public void optimizeScenarios(List<WorkflowScenario> scenarios) {
        if (scenarios == null || scenarios.isEmpty()) return;

        int originalCount = scenarios.size();
        int shatteredCount = 0;

        List<WorkflowScenario> result = new ArrayList<>();

        for (WorkflowScenario sc : scenarios) {
            List<WorkflowStep> roots = sc.getRootSteps();
            if (roots.size() <= 1) {
                result.add(sc);
                continue;
            }

            List<WorkflowScenario> partitions = shatter(sc);
            if (partitions.size() > 1) {
                shatteredCount++;
                log.info("Shattered scenario (traces={}, {} roots) into {} partitions: {}",
                        sc.getTraceIds().size(), roots.size(), partitions.size(),
                        describePartitions(partitions));
            }
            result.addAll(partitions);
        }

        scenarios.clear();
        scenarios.addAll(result);

        log.info("ScenarioOptimizer: {} scenarios in → {} scenarios out ({} shattered)",
                originalCount, scenarios.size(), shatteredCount);
    }

    /**
     * Core algorithm: build dependency graph, find weakly connected components,
     * and produce one new scenario per component.
     */
    private List<WorkflowScenario> shatter(WorkflowScenario original) {
        List<WorkflowStep> roots = original.getRootSteps();
        int n = roots.size();

        // Build adjacency lists for the directed dependency graph.
        // adj[i] contains indices j where root[j] depends on root[i] (i produces for j).
        // adjReverse[j] contains indices i where root[i] produces for root[j].
        List<Set<Integer>> adj = new ArrayList<>();
        List<Set<Integer>> adjReverse = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adj.add(new HashSet<>());
            adjReverse.add(new HashSet<>());
        }

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (registry.hasDirectedDependency(roots.get(j), roots.get(i))) {
                    adj.get(i).add(j);
                    adjReverse.get(j).add(i);
                }
                // Also check reverse: j might produce for i (unlikely in chronological
                // order, but the graph is undirected for component discovery)
                if (registry.hasDirectedDependency(roots.get(i), roots.get(j))) {
                    adj.get(j).add(i);
                    adjReverse.get(i).add(j);
                }
            }
        }

        // Find weakly connected components using Union-Find.
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        for (int i = 0; i < n; i++) {
            for (int j : adj.get(i)) {
                union(parent, i, j);
            }
        }

        // Group indices by their component root.
        Map<Integer, List<Integer>> components = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            components.computeIfAbsent(root, k -> new ArrayList<>()).add(i);
        }

        if (components.size() == 1) {
            return Collections.singletonList(original);
        }

        // Build one new WorkflowScenario per connected component.
        List<WorkflowScenario> partitions = new ArrayList<>();
        for (List<Integer> memberIndices : components.values()) {
            WorkflowScenario partition = new WorkflowScenario();
            partition.setSourceFileName(original.getSourceFileName());
            partition.setSessionIdentifier(original.getSessionIdentifier());
            // Deliberately do NOT propagate approvedInDedupPass: shattered partitions
            // are NEW scenarios that have not been through a dedup pass in their current
            // shape, so Phase 3.5 must evaluate them (it drops a 1-root partition only
            // when approvedApiKeys already holds its key — i.e. a Phase 2.5 standalone
            // owner or an earlier sibling survives elsewhere in the list). Multi-root
            // parents never registered their root keys, so a partition with a genuinely
            // new key is approved and kept. Propagating the tag made every partition
            // skip Phase 3.5 and duplicate 1-root test classes survived. The unshattered
            // path (components.size() == 1) returns the SAME instance above, tag intact.

            // Map the member's GLOBAL root index → its 1-based LOCAL position inside this
            // partition.  Used below to compute producerRootIndex from actual dependency
            // edges (adjReverse), which correctly handles fan-out topologies (A → B, A → C)
            // where the naive "previous member" assumption would wrongly record C's producer
            // as B.
            Map<Integer, Integer> globalToLocal = new HashMap<>(memberIndices.size() * 2);
            for (int local = 0; local < memberIndices.size(); local++) {
                globalToLocal.put(memberIndices.get(local), local + 1);
            }

            long minStart = Long.MAX_VALUE;
            long maxEnd = Long.MIN_VALUE;

            for (int idx = 0; idx < memberIndices.size(); idx++) {
                int globalIdx = memberIndices.get(idx);
                WorkflowStep step = roots.get(globalIdx);

                if (idx == 0) {
                    // First root in component keeps original root status
                    step.setMergedRoot(false);
                    step.setProducerRootIndex(-1);
                } else {
                    step.setMergedRoot(true);
                    // Real producer = the earliest predecessor inside this component that
                    // has a directed edge INTO this node (adjReverse).  Falls back to the
                    // chain predecessor (idx) if the edge set is empty, which shouldn't
                    // happen inside a connected component but keeps the code robust.
                    int producerLocalIdx = -1;
                    for (int pred : adjReverse.get(globalIdx)) {
                        Integer predLocal = globalToLocal.get(pred);
                        if (predLocal != null && predLocal < idx + 1) {
                            if (producerLocalIdx == -1 || predLocal < producerLocalIdx) {
                                producerLocalIdx = predLocal;
                            }
                        }
                    }
                    // Fallback: chain to the immediately preceding member of this component.
                    // Local positions are 1-based and aligned to the loop index (member at
                    // loop position k has local position k+1), so the previous member's local
                    // position equals the current loop index `idx` — not `idx - 1`.
                    step.setProducerRootIndex(producerLocalIdx != -1 ? producerLocalIdx : idx);
                }
                step.setParent(null);
                partition.addRootStep(step);

                // Aggregate time bounds
                if (step.getStartTime() < minStart) minStart = step.getStartTime();
                long stepEnd = step.getEndTime() > 0 ? step.getEndTime() : step.getStartTime();
                if (stepEnd > maxEnd) maxEnd = stepEnd;

                // Transfer trace IDs
                if (step.getTraceId() != null && !step.getTraceId().isEmpty()) {
                    partition.addTraceId(step.getTraceId());
                }
            }

            partition.setStartTimeMicros(minStart);
            partition.setEndTimeMicros(maxEnd);
            partitions.add(partition);
        }

        return partitions;
    }

    // ── Union-Find helpers ──────────────────────────────────────────────

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]]; // path compression
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            // Always merge into the smaller-index root to preserve chronological order
            if (ra < rb) {
                parent[rb] = ra;
            } else {
                parent[ra] = rb;
            }
        }
    }

    private static String describePartitions(List<WorkflowScenario> partitions) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < partitions.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(partitions.get(i).getRootSteps().size()).append("-root");
        }
        sb.append("]");
        return sb.toString();
    }
}
