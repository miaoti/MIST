package io.mist.core.workflow.pipeline.stages;

import io.mist.core.workflow.WorkflowScenario;
import io.mist.core.workflow.WorkflowStep;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Package-private dedup helper shared by Phase 2.5 and Phase 3.5.
 *
 * <p>The single-root dedup pass walks the scenario list and removes 1-root
 * scenarios whose root API key has already been "approved" (recorded in the
 * shared {@code approvedApiKeys} set). Scenarios tagged
 * {@code approvedInDedupPass} short-circuit through unchanged: they already
 * passed an earlier pass and own their key. Shattered children deliberately
 * arrive UNtagged (ScenarioOptimizer does not propagate the flag) so Phase 3.5
 * can drop partitions that duplicate an approved standalone scenario.
 *
 * <p>This helper was previously the body of the {@code runSingleRootDedupPass}
 * instance method on {@code MultiServiceTestCaseGenerator}. Lifting it here
 * lets the dedup stages call it without holding a generator reference and
 * gives both Phase 2.5 and Phase 3.5 a single source of truth for the loop.
 */
final class DedupSupport {
    private static final Logger log = LogManager.getLogger(DedupSupport.class);

    private DedupSupport() {}

    /**
     * Run a single-root dedup pass.
     *
     * @param label         human-readable label embedded in the open/close log lines
     * @param scenarios     the list to walk; entries are removed in place
     * @param approvedKeys  the running set of approved canonical API keys; new
     *                      approvals are added here so subsequent passes share it
     */
    static void runPass(String label,
                        List<WorkflowScenario> scenarios,
                        Set<String> approvedKeys) {
        log.info("=== {} ===", label);
        int originalSize = scenarios.size();
        int kept = 0;
        int dropped = 0;

        Iterator<WorkflowScenario> it = scenarios.iterator();
        while (it.hasNext()) {
            WorkflowScenario sc = it.next();

            if (sc.isApprovedInDedupPass()) {
                kept++;
                continue;
            }

            if (sc.getRootSteps().size() != 1) {
                // Multi-root scenarios are never collapsed here; tag them so the next
                // pass also treats them as approved.
                sc.setApprovedInDedupPass(true);
                kept++;
                continue;
            }

            WorkflowStep soleRoot = sc.getRootSteps().get(0);
            String apiKey = StageSupport.extractRootApiFromStep(soleRoot);
            if (apiKey == null) {
                // Cannot determine API key — keep the scenario to be safe.
                sc.setApprovedInDedupPass(true);
                kept++;
                continue;
            }

            if (approvedKeys.contains(apiKey)) {
                log.debug("Skipping redundant 1-root scenario for API: {}", apiKey);
                it.remove();
                dropped++;
                continue;
            }

            approvedKeys.add(apiKey);
            sc.setApprovedInDedupPass(true);
            kept++;
        }

        log.info("{} — kept {} / dropped {} duplicate(s) (approved keys so far: {}; {} of {} scenarios remain)",
                label, kept, dropped, approvedKeys.size(), scenarios.size(), originalSize);
    }
}
