package io.mist.core.config;

/**
 * Immutable POJO that materializes the ~30 MIST/MST system-property keys
 * scattered across the codebase into a single typed structure with documented
 * defaults. Reads happen exactly once, at first {@link #instance()} call, via
 * {@link #fromSystemProperties()}; subsequent reads return the cached instance.
 *
 * <p>Fields are grouped into inner sub-classes ({@link Core}, {@link SmartFetch},
 * {@link Llm}, {@link Faulty}, {@link ScenarioMerge}, {@link ScenarioShattering},
 * {@link SoftErrorCache}, {@link StatusCodeExploration}, {@link Enhancer},
 * {@link Jaeger}) so call sites self-document which subsystem owns each key.
 *
 * <p>Validation (range checks, conflict detection, unknown-key typo warnings)
 * is delegated to {@link MstConfigValidator#validate(MstConfig)} and runs as
 * part of {@link #fromSystemProperties()}. Strict mode (set
 * {@code mst.config.strict=true}) promotes the unknown-key warning to a fatal
 * {@link IllegalStateException}.
 *
 * <p>Property keys remain backwards-compatible — every key here was previously
 * read via raw {@code System.getProperty}. The POJO does not own any
 * persistence; an adapter-side properties loader continues to push MST file
 * contents into {@code System.getProperty}.
 *
 * <p>Tests can invalidate the singleton via {@link #resetForTesting()}.
 */
public final class MstConfig {

    private final Core core;
    private final SmartFetch smartFetch;
    private final Llm llm;
    private final Faulty faulty;
    private final ScenarioMerge scenarioMerge;
    private final ScenarioShattering scenarioShattering;
    private final SoftErrorCache softErrorCache;
    private final ParameterErrorCache parameterErrorCache;
    private final IntelligentAnalysisCache intelligentAnalysisCache;
    private final StatusCodeExploration statusCodeExploration;
    private final Enhancer enhancer;
    private final Jaeger jaeger;
    private final Oracle oracle;
    private final Scheduler scheduler;
    private final Adaptive adaptive;

    private static volatile MstConfig INSTANCE;

    private MstConfig(Core core, SmartFetch smartFetch, Llm llm, Faulty faulty,
                      ScenarioMerge scenarioMerge, ScenarioShattering scenarioShattering,
                      SoftErrorCache softErrorCache, ParameterErrorCache parameterErrorCache,
                      IntelligentAnalysisCache intelligentAnalysisCache,
                      StatusCodeExploration statusCodeExploration,
                      Enhancer enhancer, Jaeger jaeger,
                      Oracle oracle, Scheduler scheduler, Adaptive adaptive) {
        this.core = core;
        this.smartFetch = smartFetch;
        this.llm = llm;
        this.faulty = faulty;
        this.scenarioMerge = scenarioMerge;
        this.scenarioShattering = scenarioShattering;
        this.softErrorCache = softErrorCache;
        this.parameterErrorCache = parameterErrorCache;
        this.intelligentAnalysisCache = intelligentAnalysisCache;
        this.statusCodeExploration = statusCodeExploration;
        this.enhancer = enhancer;
        this.jaeger = jaeger;
        this.oracle = oracle;
        this.scheduler = scheduler;
        this.adaptive = adaptive;
    }

    public Core core() { return core; }
    public SmartFetch smartFetch() { return smartFetch; }
    public Llm llm() { return llm; }
    public Faulty faulty() { return faulty; }
    public ScenarioMerge scenarioMerge() { return scenarioMerge; }
    public ScenarioShattering scenarioShattering() { return scenarioShattering; }
    public SoftErrorCache softErrorCache() { return softErrorCache; }
    public ParameterErrorCache parameterErrorCache() { return parameterErrorCache; }
    public IntelligentAnalysisCache intelligentAnalysisCache() { return intelligentAnalysisCache; }
    public StatusCodeExploration statusCodeExploration() { return statusCodeExploration; }
    public Enhancer enhancer() { return enhancer; }
    public Jaeger jaeger() { return jaeger; }
    public Oracle oracle() { return oracle; }
    public Scheduler scheduler() { return scheduler; }
    public Adaptive adaptive() { return adaptive; }

    /**
     * Returns the JVM-wide singleton, lazily constructed from System
     * properties on first access. Validation runs once at construction.
     */
    public static MstConfig instance() {
        MstConfig local = INSTANCE;
        if (local == null) {
            synchronized (MstConfig.class) {
                local = INSTANCE;
                if (local == null) {
                    local = fromSystemProperties();
                    INSTANCE = local;
                }
            }
        }
        return local;
    }

    /**
     * Rebuilds an MstConfig from the current System properties and runs the
     * validator. Used by {@link #instance()} and by tests that need a fresh
     * snapshot without caching.
     */
    public static MstConfig fromSystemProperties() {
        MstConfig cfg = new MstConfig(
                new Core(),
                new SmartFetch(),
                new Llm(),
                new Faulty(),
                new ScenarioMerge(),
                new ScenarioShattering(),
                new SoftErrorCache(),
                new ParameterErrorCache(),
                new IntelligentAnalysisCache(),
                new StatusCodeExploration(),
                new Enhancer(),
                new Jaeger(),
                new Oracle(),
                new Scheduler(),
                new Adaptive());
        MstConfigValidator.validate(cfg);
        return cfg;
    }

    /**
     * Clears the cached singleton so a subsequent {@link #instance()} call
     * rebuilds from the current System properties. Intended for tests that
     * mutate {@code System.setProperty(...)} between cases.
     *
     * <p>Public so cross-package tests (e.g. ablation toggles exercised by
     * {@code TraceShapeOracleIntegrationTest}) can refresh the singleton
     * without reflection. Production code must not call this.
     */
    public static void resetForTesting() {
        synchronized (MstConfig.class) {
            INSTANCE = null;
        }
    }

    // --- parsers ------------------------------------------------------

    private static boolean parseBool(String key, String defaultValue) {
        String raw = System.getProperty(key, defaultValue);
        // Boolean.parseBoolean is permissive (accepts any case for "true",
        // everything else becomes false). That is the historical behavior
        // every call site already relied on, so we keep it. A strict
        // 'must be true|false' check would break existing properties files
        // that contain typos like "True " (trailing space).
        return Boolean.parseBoolean(raw.trim());
    }

    private static double parseDouble(String key, String defaultValue) {
        String raw = System.getProperty(key, defaultValue);
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(
                    "MstConfig: " + key + " must be parseable as double, got '" + raw + "'", nfe);
        }
    }

    private static int parseInt(String key, String defaultValue) {
        String raw = System.getProperty(key, defaultValue);
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(
                    "MstConfig: " + key + " must be parseable as int, got '" + raw + "'", nfe);
        }
    }

    private static long parseLong(String key, String defaultValue) {
        String raw = System.getProperty(key, defaultValue);
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(
                    "MstConfig: " + key + " must be parseable as long, got '" + raw + "'", nfe);
        }
    }

    // --- sub-groups ---------------------------------------------------

    public static final class Core {
        private final boolean generateOnlyFirstStep;
        private final String nounMapPath;

        public Core() {
            this.generateOnlyFirstStep = parseBool("mst.generate.only.first.step", "true");
            this.nounMapPath = System.getProperty("mist.noun.map.path", "mist/noun-map.default.yaml");
        }

        public boolean generateOnlyFirstStep() { return generateOnlyFirstStep; }
        public String nounMapPath() { return nounMapPath; }
    }

    public static final class SmartFetch {
        private final boolean enabled;
        private final double percentage;
        private final String registryPath;

        public SmartFetch() {
            this.enabled = parseBool("smart.input.fetch.enabled", "false");
            this.percentage = parseDouble("smart.input.fetch.percentage", "0.0");
            // Null when unset (original call-site contract; consumers handle
            // null by skipping registry lookup).
            this.registryPath = System.getProperty("smart.input.fetch.registry.path");
        }

        public boolean enabled() { return enabled; }
        public double percentage() { return percentage; }
        public String registryPath() { return registryPath; }
    }

    public static final class Llm {
        private final String cachePath;
        private final boolean responseValidationEnabled;
        private final boolean responseValidationOnly2xx;
        private final boolean responseValidationIncludeRca;

        public Llm() {
            this.cachePath = System.getProperty("mist.llm.cache.path",
                    ".mist/llm-call-cache.json");
            this.responseValidationEnabled = parseBool("llm.response.validation.enabled", "false");
            this.responseValidationOnly2xx = parseBool("llm.response.validation.only.2xx", "true");
            this.responseValidationIncludeRca = parseBool("llm.response.validation.include.rca", "true");
        }

        public String cachePath() { return cachePath; }
        public boolean responseValidationEnabled() { return responseValidationEnabled; }
        public boolean responseValidationOnly2xx() { return responseValidationOnly2xx; }
        public boolean responseValidationIncludeRca() { return responseValidationIncludeRca; }
    }

    public static final class Faulty {
        private final double ratio;
        private final boolean roundRobin;
        private final String negativeInputGenerationMode;

        public Faulty() {
            this.ratio = parseDouble("faulty.ratio", "0.1");
            this.roundRobin = parseBool("faulty.round-robin", "true");
            this.negativeInputGenerationMode = System.getProperty(
                    "negative.input.generation.mode", "smart");
        }

        public double ratio() { return ratio; }
        public boolean roundRobin() { return roundRobin; }
        public String negativeInputGenerationMode() { return negativeInputGenerationMode; }
    }

    public static final class ScenarioMerge {
        private final long maxSessionGapMicros;
        private final int maxRootsPerScenario;

        public ScenarioMerge() {
            this.maxSessionGapMicros = parseLong("trace.merge.max.session.gap.micros", "60000000");
            this.maxRootsPerScenario = parseInt("trace.merge.max.roots.per.scenario", "10");
        }

        public long maxSessionGapMicros() { return maxSessionGapMicros; }
        public int maxRootsPerScenario() { return maxRootsPerScenario; }
    }

    public static final class ScenarioShattering {
        private final boolean enabled;

        public ScenarioShattering() {
            this.enabled = parseBool("scenario.shattering.enabled", "true");
        }

        public boolean enabled() { return enabled; }
    }

    public static final class SoftErrorCache {
        private final boolean enabled;
        private final String cachePath;

        public SoftErrorCache() {
            this.enabled = parseBool("soft.error.cache.enabled", "true");
            this.cachePath = System.getProperty("soft.error.cache.path",
                    ".mist/soft-error-rule-cache.json");
        }

        public boolean enabled() { return enabled; }
        public String cachePath() { return cachePath; }
    }

    /**
     * Per-signature LLM verdict cache for the parameter-error analyzer. Same
     * lifetime/footprint as {@link SoftErrorCache} but isolated so the two
     * caches don't share a path. The default lives under {@code .mist/} so it
     * survives {@code mvn clean}.
     */
    public static final class ParameterErrorCache {
        private final String cachePath;

        public ParameterErrorCache() {
            this.cachePath = System.getProperty("parameter.error.analysis.cache.path",
                    ".mist/parameter-error-analysis-cache.json");
        }

        public String cachePath() { return cachePath; }
    }

    /**
     * Trace-failure-mode LLM diagnosis cache for
     * {@link io.mist.core.analysis.TraceErrorAnalyzer#generateIntelligentAnalysis}.
     * Persisted under {@code .mist/} to survive {@code mvn clean}.
     */
    public static final class IntelligentAnalysisCache {
        private final String cachePath;

        public IntelligentAnalysisCache() {
            this.cachePath = System.getProperty("intelligent.analysis.cache.path",
                    ".mist/intelligent-analysis-cache.json");
        }

        public String cachePath() { return cachePath; }
    }

    public static final class StatusCodeExploration {
        private final boolean enabled;
        private final int maxPerTest;
        private final int maxPerRound;

        public StatusCodeExploration() {
            this.enabled = parseBool("status.code.exploration.enabled", "false");
            this.maxPerTest = parseInt("status.code.exploration.max.per.test", "3");
            this.maxPerRound = parseInt("status.code.exploration.max.per.round", "20");
        }

        public boolean enabled() { return enabled; }
        public int maxPerTest() { return maxPerTest; }
        public int maxPerRound() { return maxPerRound; }
    }

    public static final class Enhancer {
        private final boolean enabled;
        private final int rounds;
        private final boolean skip5xx;

        public Enhancer() {
            this.enabled = parseBool("test.enhancer.enabled", "false");
            this.rounds = parseInt("test.enhancer.rounds", "1");
            this.skip5xx = parseBool("test.enhancer.skip.5xx", "true");
        }

        public boolean enabled() { return enabled; }
        public int rounds() { return rounds; }
        public boolean skip5xx() { return skip5xx; }
    }

    public static final class Jaeger {
        private final boolean enabled;
        private final String baseUrl;
        private final String lookback;

        public Jaeger() {
            this.enabled = parseBool("jaeger.enabled", "false");
            this.baseUrl = System.getProperty("jaeger.base.url", "http://localhost:16686");
            this.lookback = System.getProperty("jaeger.lookback", "1h");
        }

        public boolean enabled() { return enabled; }
        public String baseUrl() { return baseUrl; }
        public String lookback() { return lookback; }
    }

    /**
     * Trace Shape Oracle and per-invariant toggles. The whole-oracle gate
     * ({@code mst.oracle.shape.enabled}) short-circuits
     * {@link io.mist.core.oracle.shape.TraceShapeOracle#evaluate} to an empty
     * verdict; the four per-invariant gates skip one invariant each inside
     * that same method.
     *
     * <p>The {@code timing} invariant defaults to {@code false} because the
     * paper revision dropped it from the named contribution; the other four
     * default to {@code true} to preserve byte-identical generated test
     * files against the pre-toggle baseline.
     */
    public static final class Oracle {
        private final boolean shapeOracleEnabled;
        private final boolean spanTreeInvariantEnabled;
        private final boolean statusPropagationInvariantEnabled;
        private final boolean responseEnvelopeInvariantEnabled;
        private final boolean timingEnvelopeInvariantEnabled;
        // Phase 2 part 2 (FIXES.md F1+F3): kill switch for the
        // TargetAttributionInvariant. Default true preserves the byte-for-byte
        // attribution histograms from Run 22; setting false drops the perf
        // cost of TraceAttribution.attribute() AND the report buckets.
        private final boolean targetAttributionInvariantEnabled;
        // Phase 3: intent-agnostic hidden-downstream-failure detector. Default
        // false (opt-in) so legacy verdicts stay byte-identical until enabled.
        private final boolean hiddenDownstreamFailureInvariantEnabled;

        public Oracle() {
            this.shapeOracleEnabled = parseBool("mst.oracle.shape.enabled", "true");
            this.spanTreeInvariantEnabled = parseBool(
                    "mst.oracle.shape.invariants.span_tree.enabled", "true");
            this.statusPropagationInvariantEnabled = parseBool(
                    "mst.oracle.shape.invariants.status_propagation.enabled", "true");
            this.responseEnvelopeInvariantEnabled = parseBool(
                    "mst.oracle.shape.invariants.response_envelope.enabled", "true");
            this.timingEnvelopeInvariantEnabled = parseBool(
                    "mst.oracle.shape.invariants.timing.enabled", "false");
            this.targetAttributionInvariantEnabled = parseBool(
                    "mst.oracle.shape.invariants.target_attribution.enabled", "true");
            this.hiddenDownstreamFailureInvariantEnabled = parseBool(
                    "mst.oracle.shape.invariants.hidden_downstream_failure.enabled", "false");
        }

        public boolean shapeOracleEnabled() { return shapeOracleEnabled; }
        public boolean spanTreeInvariantEnabled() { return spanTreeInvariantEnabled; }
        public boolean statusPropagationInvariantEnabled() { return statusPropagationInvariantEnabled; }
        public boolean responseEnvelopeInvariantEnabled() { return responseEnvelopeInvariantEnabled; }
        public boolean timingEnvelopeInvariantEnabled() { return timingEnvelopeInvariantEnabled; }
        public boolean targetAttributionInvariantEnabled() { return targetAttributionInvariantEnabled; }
        public boolean hiddenDownstreamFailureInvariantEnabled() { return hiddenDownstreamFailureInvariantEnabled; }
    }

    /**
     * Fault-queue scheduler toggle. When
     * {@code mst.scheduler.bandit.enabled=false} (default {@code true}) the
     * generator preserves insertion order of the fault queue without ever
     * constructing the Thompson-sampling bandit.
     */
    public static final class Scheduler {
        private final boolean banditEnabled;

        public Scheduler() {
            this.banditEnabled = parseBool("mst.scheduler.bandit.enabled", "true");
        }

        public boolean banditEnabled() { return banditEnabled; }
    }

    /**
     * Per-endpoint adaptive strategy toggle. When {@code mst.adaptive.enabled=false}
     * (default) the legacy fixed thresholds (K_ZERO_STEP=3, K_DEDUP_EXHAUSTED=10,
     * payload-only dedup, 5s auth refresh window) apply uniformly to every API,
     * preserving byte-identical behaviour with pre-adaptive runs.
     *
     * <p>When {@code mst.adaptive.enabled=true} the generator and auth filter
     * consult {@link io.mist.core.policy.EndpointPolicyResolver} for per-endpoint
     * thresholds derived from HTTP method semantics + OpenAPI {@code x-mist-*}
     * hints. See {@code docs/adaptive-strategy-research.md} for design.
     */
    public static final class Adaptive {
        private final boolean enabled;
        private final int kDedupExhaustedDefault;
        private final int kZeroStepDefault;
        private final long authTokenMinAgeNs;

        public Adaptive() {
            this.enabled = parseBool("mst.adaptive.enabled", "false");
            this.kDedupExhaustedDefault = parseInt("mst.adaptive.k.dedup.exhausted", "10");
            this.kZeroStepDefault = parseInt("mst.adaptive.k.zero.step", "3");
            this.authTokenMinAgeNs = parseLong("mst.adaptive.auth.token.min.age.ns", "5000000000");
        }

        public boolean enabled() { return enabled; }
        public int kDedupExhaustedDefault() { return kDedupExhaustedDefault; }
        public int kZeroStepDefault() { return kZeroStepDefault; }
        public long authTokenMinAgeNs() { return authTokenMinAgeNs; }
    }
}
