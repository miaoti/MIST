package io.mist.core.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validation companion for {@link MstConfig}. Three responsibilities:
 *
 * <ol>
 *   <li><b>Unknown-key scan.</b> Walks {@link System#getProperties()} and warns
 *   (or, under {@code mst.config.strict=true}, aborts) when a key starts with
 *   one of our owned namespaces but is not in the whitelist below — catches
 *   typos like {@code smart.input.fetch.percetage} that would otherwise
 *   silently fall back to a default.</li>
 *   <li><b>Range checks.</b> Numeric properties whose values must lie in a
 *   specific interval ({@code faulty.ratio}, {@code smart.input.fetch.percentage}
 *   in [0,1]; gap micros must be non-negative).</li>
 *   <li><b>Documented conflict detection.</b> Logs (INFO level) when settings
 *   interact in a non-obvious way; today only the
 *   {@code mst.generate.only.first.step} vs {@code scenario.shattering.enabled}
 *   precedence is surfaced.</li>
 * </ol>
 */
public final class MstConfigValidator {

    private static final Logger logger = LogManager.getLogger(MstConfigValidator.class);

    /**
     * Hard-coded whitelist of every known MIST/MST key. Keep in sync with the
     * sub-classes of {@link MstConfig}. Any key matching one of
     * {@link #NAMESPACES} but absent here triggers the typo warning.
     *
     * <p>Maintenance contract: when you add a parse call in any sub-class of
     * {@link MstConfig}, add its key here too. Forgetting only weakens the
     * typo-detection UX for that one key; everything else keeps working.
     */
    private static final Set<String> KNOWN_KEYS;
    static {
        Set<String> keys = new HashSet<>(Arrays.asList(
                // Core
                "mst.generate.only.first.step",
                "mist.noun.map.path",
                // SmartFetch — full read surface (some keys are read by helper
                // classes via Properties rather than System.getProperty, but they
                // all land in System.properties via the multiservice.MstConfig
                // loader, so the validator sees them and must whitelist them).
                "smart.input.fetch.enabled",
                "smart.input.fetch.percentage",
                "smart.input.fetch.registry.path",
                "smart.input.fetch.openapi.spec.path",
                "smart.input.fetch.cache.enabled",
                "smart.input.fetch.cache.llm.fallback",
                "smart.input.fetch.cache.ttl.seconds",
                "smart.input.fetch.connect.timeout.ms",
                "smart.input.fetch.read.timeout.ms",
                "smart.input.fetch.decay.days",
                "smart.input.fetch.default.priority",
                "smart.input.fetch.dependency.resolution.enabled",
                "smart.input.fetch.discovery.timeout.ms",
                "smart.input.fetch.diverse.target.count",
                "smart.input.fetch.ema.alpha",
                "smart.input.fetch.http.content.type",
                "smart.input.fetch.http.success.code",
                "smart.input.fetch.llm.discovery.enabled",
                "smart.input.fetch.llm.discovery.priority",
                "smart.input.fetch.llm.endpoint.selection.enabled",
                "smart.input.fetch.max.candidates",
                "smart.input.fetch.max.prompt.chars",
                "smart.input.fetch.pattern.discovery.priority",
                "smart.input.fetch.schema.discovery.timeout.ms",
                // Llm
                "mist.llm.cache.path",
                "mist.llm.cache.read",
                "mist.llm.cache.write",
                "llm.response.validation.enabled",
                "llm.response.validation.only.2xx",
                "llm.response.validation.include.rca",
                // Faulty
                "faulty.ratio",
                "faulty.round-robin",
                "faulty.dependency.ratio",
                "negative.input.generation.mode",
                // FaultMiner (gated directly via FaultMiner.ENABLED_PROPERTY)
                "mist.fault.mining.enabled",
                // ScenarioMerge
                "trace.merge.max.session.gap.micros",
                "trace.merge.max.roots.per.scenario",
                // ScenarioShattering
                "scenario.shattering.enabled",
                // SoftErrorCache
                "soft.error.cache.enabled",
                "soft.error.cache.path",
                // ParameterErrorCache
                "parameter.error.analysis.cache.path",
                // IntelligentAnalysisCache
                "intelligent.analysis.cache.path",
                // StatusCodeExploration
                "status.code.exploration.enabled",
                "status.code.exploration.max.per.test",
                "status.code.exploration.max.per.round",
                // Master cache trigger (single source of truth — see CacheToggle).
                // Signature-based reproducibility caches (LLMStatusCodeDiscovery,
                // ExplorationEnhancer, LLM Validation) all honor this single pair.
                "mst.cache.read",
                "mst.cache.write",
                // LLMStatusCodeDiscovery persistent cache path
                "mst.status.code.discovery.cache.path",
                // Exploration suggest LLM cache path
                "mst.exploration.suggest.cache.path",
                // LLM Validation response cache path
                "mst.llm.validation.cache.path",
                // Enhancer
                "test.enhancer.enabled",
                "test.enhancer.rounds",
                "test.enhancer.skip.5xx",
                // Enhancer dedup + parallel + persistent cache (commit 967e5a2e)
                "mst.enhancer.parallelism",
                "mst.enhancer.dedup.negative",
                "mst.enhancer.cache.enabled",
                "mst.enhancer.cache.path",
                // Jaeger
                "jaeger.enabled",
                "jaeger.base.url",
                "jaeger.lookback",
                // Oracle (Trace Shape Oracle + 4 invariant toggles)
                "mst.oracle.shape.enabled",
                "mst.oracle.shape.invariants.span_tree.enabled",
                "mst.oracle.shape.invariants.status_propagation.enabled",
                "mst.oracle.shape.invariants.response_envelope.enabled",
                "mst.oracle.shape.invariants.timing.enabled",
                // FIXES.md F2: TargetAttributionInvariant kill switch
                "mst.oracle.shape.invariants.target_attribution.enabled",
                // Phase 3: intent-agnostic trace detector (opt-in, default off)
                "mst.oracle.shape.invariants.hidden_downstream_failure.enabled",
                // Phase 0: surface oracle violations in the fault-detection report
                "mist.report.oracle.anomalies.enabled",
                // Phase 1: opt-in two-phase positive-first / negative-second flow
                // (currently a reserved flag; verified-pool filter runs
                // unconditionally on every Sniper non-target pull).
                "mst.two.phase.enabled",
                // Scheduler (ThompsonScheduler toggle)
                "mst.scheduler.bandit.enabled",
                // Test-execution timing knobs (read by MistRunner + generated tests)
                "mst.test.parallelism",
                "mst.test.inter.scenario.delay.ms",
                "mst.test.jaeger.propagation.delay.ms",
                // SUT preflight health check (probes registry before scenario gen)
                "mst.preflight.enabled",
                "mst.preflight.timeout.ms",
                "mst.preflight.auth.enabled",
                // Adaptive per-endpoint strategy (docs/adaptive-strategy-research.md)
                "mst.adaptive.enabled",
                "mst.adaptive.k.dedup.exhausted",
                "mst.adaptive.k.zero.step",
                "mst.adaptive.auth.token.min.age.ns",
                // Bootstrap loader (consumed by multiservice.MstConfig before
                // the validator runs, but stays in System.properties)
                "mst.config.path",
                // Validator's own switch
                "mst.config.strict"
        ));
        KNOWN_KEYS = Collections.unmodifiableSet(keys);
    }

    /**
     * Property namespaces we own. A System property starting with one of these
     * is expected to appear in {@link #KNOWN_KEYS}; otherwise it is almost
     * certainly a typo and a warning is emitted. We intentionally do NOT own
     * the generic {@code llm.} prefix (used by the LLM backend client for
     * model URLs, API keys, etc.) — only the narrower
     * {@code llm.response.validation.} subspace.
     */
    private static final List<String> NAMESPACES = Collections.unmodifiableList(Arrays.asList(
            "mst.",
            "mist.",
            "smart.input.fetch.",
            "scenario.shattering.",
            "faulty.",
            "trace.merge.",
            "test.enhancer.",
            "status.code.exploration.",
            "soft.error.cache.",
            "parameter.error.analysis.",
            "intelligent.analysis.",
            "jaeger.",
            "negative.input.generation.",
            "llm.response.validation."
    ));

    private MstConfigValidator() {
        // utility class
    }

    /**
     * Run all three validation passes against {@code cfg}. Throws on range
     * violations and (in strict mode) on unknown keys; otherwise logs.
     */
    public static void validate(MstConfig cfg) {
        boolean strict = Boolean.parseBoolean(System.getProperty("mst.config.strict", "false"));

        // 1. Unknown-key scan
        boolean foundUnknown = false;
        for (String key : System.getProperties().stringPropertyNames()) {
            for (String ns : NAMESPACES) {
                if (key.startsWith(ns) && !KNOWN_KEYS.contains(key)) {
                    logger.warn("MstConfig: unknown property '{}' (typo?). Strict mode = {}", key, strict);
                    foundUnknown = true;
                    break;
                }
            }
        }
        if (foundUnknown && strict) {
            throw new IllegalStateException(
                    "MstConfig: unknown properties found; mst.config.strict=true rejects the run.");
        }

        // 2. Range checks
        double faultyRatio = cfg.faulty().ratio();
        if (faultyRatio < 0.0 || faultyRatio > 1.0) {
            throw new IllegalArgumentException(
                    "faulty.ratio must be in [0,1], got " + faultyRatio);
        }
        long gapMicros = cfg.scenarioMerge().maxSessionGapMicros();
        if (gapMicros < 0) {
            throw new IllegalArgumentException(
                    "trace.merge.max.session.gap.micros must be >= 0, got " + gapMicros);
        }
        double smartFetchPct = cfg.smartFetch().percentage();
        if (smartFetchPct < 0.0 || smartFetchPct > 1.0) {
            throw new IllegalArgumentException(
                    "smart.input.fetch.percentage must be in [0,1], got " + smartFetchPct);
        }

        // 3. Documented conflict (INFO, not fatal)
        if (cfg.core().generateOnlyFirstStep() && cfg.scenarioShattering().enabled()) {
            logger.info("MstConfig: mst.generate.only.first.step=true takes precedence — " +
                    "scenario.shattering.enabled is effectively ignored.");
        }
    }
}
