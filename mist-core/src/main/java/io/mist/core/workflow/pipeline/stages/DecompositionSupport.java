package io.mist.core.workflow.pipeline.stages;

import io.mist.core.workflow.WorkflowScenario;
import io.mist.core.workflow.WorkflowStep;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Package-private helper backing {@link Phase4DecompositionStage}.
 *
 * <p>Contains the verbatim body of the {@code decomposeMultiRootScenarios}
 * method that previously lived on {@code MultiServiceTestCaseGenerator}.
 * Lifting the loop here lets Phase 4's stage class run without a generator
 * reference and surfaces the decomposition rules at the same level as the
 * dedup logic that gates it.
 */
final class DecompositionSupport {
    private static final Logger log = LogManager.getLogger(DecompositionSupport.class);

    private DecompositionSupport() {}

    /**
     * Decompose multi-root scenarios into additional 1-Root baseline scenarios
     * to guarantee independent coverage for every API endpoint.
     *
     * <p>For a scenario with roots [A, B] (indexed as {@code Flow_Scenario_N}),
     * this method creates two new scenarios:
     * <ul>
     *   <li>{@code Flow_Scenario_N_RT1} — containing only A's deep-copied step tree</li>
     *   <li>{@code Flow_Scenario_N_RT2} — containing only B's deep-copied step tree</li>
     * </ul>
     *
     * <p>Deduplication is performed by fingerprint
     * ({@code serviceName::operationName}) <em>and</em> by normalised API key
     * via {@code approvedApiKeys}. If a standalone 1-root scenario already
     * covers an endpoint, the decomposed {@code _RT} baseline for that same
     * endpoint is skipped.
     *
     * <p>The original multi-root scenario is preserved unchanged so the
     * generator still produces end-to-end flow tests alongside the baseline
     * tests.
     */
    static void decomposeMultiRootScenarios(List<WorkflowScenario> scenarios,
                                            Set<String> approvedApiKeys) {
        log.info("=== PHASE 4: TRACE DECOMPOSITION — extracting 1-Root baselines ===");

        // Fingerprints already extracted: prevent duplicate baseline scenarios
        Set<String> extractedFingerprints = new LinkedHashSet<>();

        // Collect new scenarios in a separate list to avoid ConcurrentModificationException
        List<WorkflowScenario> decomposed = new ArrayList<>();

        // Track which index in the original list each scenario occupies.
        // The counter mirrors the baseCounter logic in the generate() loop.
        int scenarioCounter = 1;

        for (WorkflowScenario sc : scenarios) {
            List<WorkflowStep> roots = sc.getRootSteps();

            if (roots.size() <= 1) {
                // Single-root scenario — no decomposition needed
                scenarioCounter++;
                continue;
            }

            log.info("Decomposing scenario {} ({} roots) into 1-Root baselines",
                    scenarioCounter, roots.size());

            for (int ri = 0; ri < roots.size(); ri++) {
                WorkflowStep root = roots.get(ri);
                String fingerprint = root.getServiceName() + "::" + root.getOperationName();

                if (extractedFingerprints.contains(fingerprint)) {
                    log.info("  Root {} (RT{}) fingerprint '{}' already extracted — skipping duplicate",
                            ri + 1, ri + 1, fingerprint);
                    continue;
                }

                // Cross-check with the global single-root dedup set: if a standalone
                // 1-root scenario already covers this API, skip the decomposed _RT baseline.
                String apiKey = StageSupport.extractRootApiFromStep(root);
                if (apiKey != null && approvedApiKeys.contains(apiKey)) {
                    log.info("  Root {} (RT{}) API '{}' already covered by standalone 1-root scenario — skipping",
                            ri + 1, ri + 1, apiKey);
                    extractedFingerprints.add(fingerprint);
                    continue;
                }

                extractedFingerprints.add(fingerprint);
                if (apiKey != null) {
                    approvedApiKeys.add(apiKey);
                }

                // Deep-copy the root step tree to avoid aliasing with the original
                WorkflowStep rootCopy = root.deepCopy();
                // Clear merge metadata — this is now an independent 1-Root scenario
                rootCopy.setMergedRoot(false);
                rootCopy.setProducerRootIndex(-1);

                WorkflowScenario singleRoot = new WorkflowScenario();
                singleRoot.addRootStep(rootCopy);
                singleRoot.setSourceFileName(sc.getSourceFileName());
                singleRoot.setSessionIdentifier(sc.getSessionIdentifier());
                singleRoot.setStartTimeMicros(root.getStartTime());
                singleRoot.setEndTimeMicros(root.getEndTime());

                // Tag for naming: Flow_Scenario_N_RT(ri+1)
                singleRoot.setDecomposedTag("_RT" + (ri + 1));
                singleRoot.setParentScenarioIndex(scenarioCounter);

                decomposed.add(singleRoot);

                log.info("  Created 1-Root baseline: Flow_Scenario_{}_{} [{}]",
                        scenarioCounter, "RT" + (ri + 1), fingerprint);
            }

            scenarioCounter++;
        }

        if (!decomposed.isEmpty()) {
            scenarios.addAll(decomposed);
            log.info("Trace Decomposition complete: {} new 1-Root baselines added (total scenarios: {})",
                    decomposed.size(), scenarios.size());
        } else {
            log.info("Trace Decomposition: no multi-root scenarios found — nothing to decompose");
        }
    }
}
