package io.mist.core.generation;

import io.mist.core.config.MstConfig;
import io.mist.core.policy.EndpointPolicy;
import io.mist.core.policy.EndpointPolicyResolver;
import io.mist.core.spec.Operation;
import io.mist.core.spec.TestConfigurationObject;
import io.mist.core.spec.TestParameter;
import io.mist.core.fault.InvalidInputPool;
import io.mist.core.fault.PoolKey;
import io.mist.core.generation.AiDrivenLLMGenerator;
import io.mist.core.llm.ParameterInfo;
import io.mist.core.smart.InputFetchRegistry;
import io.mist.core.smart.ParameterError;
import io.mist.core.smart.SmartInputFetcher;
import io.mist.core.smart.SmartInputFetchConfig;
import io.mist.core.bandit.ThompsonScheduler;
import io.mist.core.testcase.MultiServiceTestCase;
import io.mist.core.testcase.TestCase;
import io.mist.core.registry.SemanticDependencyRegistry;
import io.mist.core.workflow.WorkflowScenario;
import io.mist.core.workflow.WorkflowStep;
import io.mist.core.workflow.pipeline.PipelineContext;
import io.mist.core.workflow.pipeline.WorkflowPipeline;
import io.mist.core.workflow.pipeline.stages.StageSupport;
import io.mist.core.workflow.pipeline.stages.Phase25DedupStage;
import io.mist.core.workflow.pipeline.stages.Phase35DedupStage;
import io.mist.core.workflow.pipeline.stages.Phase3ShatteringStage;
import io.mist.core.workflow.pipeline.stages.Phase4DecompositionStage;
import io.mist.core.workflow.pipeline.stages.SharedPoolGenerationStage;

import io.mist.core.util.ConsoleProgressBar;
import io.swagger.v3.oas.models.OpenAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MistGenerator {

    /* ------------------------------------------------------------ */
    private static final Logger log = LogManager.getLogger(MistGenerator.class);

    private final Map<String, OpenAPI>          serviceSpecs;
    private final Map<String, TestConfigurationObject>       serviceConfigs;
    private List<WorkflowScenario>                           scenarios;
    // Phase 1 part 2: snapshot of the scenarios list as received from the
    // constructor (shallow — same WorkflowScenario refs). The workflow
    // pipeline stages (Phase25Dedup, Phase3Shattering, Phase35Dedup,
    // Phase4Decomposition) mutate the live scenarios list in place; without
    // a snapshot, a second generate() call (Phase B) operates on the post-
    // Phase-A residue and produces zero / wrong variants. resetForNewPhase
    // restores this.scenarios to a fresh copy of originalScenarios.
    private final List<WorkflowScenario>                     originalScenarios;
    private final boolean                                    useLLM;
    private final AiDrivenLLMGenerator                       llmGen = new AiDrivenLLMGenerator();
    
    // Configuration: when enabled, only generate first business step (writer keeps login as step 0)
    private final boolean                                    onlyFirstBusinessStep;
    
    // Semantic dependency dictionary: param → producer API binding
    private final SemanticDependencyRegistry dependencyRegistry;

    // JIT Binding observability counters
    private int jitDictionaryHits = 0;
    private int jitFuzzingFallbacks = 0;

    // Smart Input Fetching System
    private SmartInputFetcher smartFetcher;
    private SmartInputFetchConfig smartFetchConfig;
    
    // Negative Test Generation System (tests with intentionally invalid inputs)
    private float faultyRatio;
    private boolean faultyRoundRobin = true;  // true = round-robin, false = random
    /**
     * Faulty parameter pools per root API key, keyed by {@link PoolKey} so two
     * parameters in the same operation that share a name but differ in location
     * (e.g. a path {@code {id}} and a header {@code Id}) get distinct pool entries.
     * The previous {@code Map<String, InvalidInputPool>} keyed by paramName alone
     * collided silently in that case; the second enrolment overwrote the first.
     */
    private Map<String, Map<PoolKey, InvalidInputPool>> faultyParameterPools = new HashMap<>();
    private Random random = io.mist.core.util.SeededRandom.create("MistGenerator");
    
    // Track which parameter should have invalid value in current test case (round-robin mode)
    private List<String> parameterRotation = new ArrayList<>();
    private int currentFaultyParamIndex = 0;

    // Normalised API keys (e.g. "GET__api_v1_...") of 1-root scenarios that have been
    // APPROVED (kept) by a prior dedup pass.  Shared across Phase 2.5, Phase 3.5, and
    // Phase 4 so each pass dedups against the same canonical set.  The earlier
    // "every key ever seen" set over-suppressed shattered components because Phase 3
    // returns new WorkflowScenario instances whose keys were already marked as seen;
    // tracking ONLY approved keys plus the approvedInDedupPass tag fixes that.
    private final Set<String> approvedApiKeys = new LinkedHashSet<>();

    // PoolKey lives in io.mist.core.fault.PoolKey (extracted from this class
    // as part of the B1 sever so the workflow pipeline can reference it
    // without dragging in the rest of MistGenerator).

    /**
     * Represents a single fault-injection target: one invalid value fired at
     * one parameter of one specific root API in a multi-root sequence.
     */
    static final class FaultTarget {
        final int rootIndex;          // 1-based index into sc.getRootSteps()
        final String rootApiKey;      // verb_path key for pool lookup
        final String paramName;       // target parameter
        final String paramLocation;   // normalised OpenAPI location (path|query|header|cookie|body|other)
        final String type;            // which edge-case category (FaultType id)
        final Object value;           // pre-captured invalid value (replayed at fire time)

        FaultTarget(int rootIndex, String rootApiKey, String paramName,
                    String paramLocation,
                    String type, Object value) {
            this.rootIndex     = rootIndex;
            this.rootApiKey    = rootApiKey;
            this.paramName     = paramName;
            this.paramLocation = paramLocation;
            this.type          = type;
            this.value         = value;
        }

        @Override
        public String toString() {
            return "FaultTarget{R" + rootIndex + " " + rootApiKey + "." + paramName
                    + "@" + paramLocation + " [" + type + "]}";
        }
    }

    /**
     * Build a prioritized, exhaustive queue of {@link FaultTarget}s for every
     * parameter of every root API in a scenario.  The order follows the
     * edge-case priority baked into {@link InvalidInputPool}: boundary violations
     * and overflows fire first, semantic mismatches fire last.
     *
     * Each queue entry maps to exactly ONE round-robin draw from ONE
     * parameter's pool, so the queue length equals the total number of
     * distinct invalid values across all roots.
     */
    private List<FaultTarget> buildFaultInjectionQueue(WorkflowScenario scenario) {
        List<FaultTarget> queue = new ArrayList<>();
        List<WorkflowStep> roots = scenario.getRootSteps();

        for (int rootIdx = 0; rootIdx < roots.size(); rootIdx++) {
            WorkflowStep rootStep = roots.get(rootIdx);
            String rootApiKey = extractRootApiFromStep(rootStep);
            if (rootApiKey == null) continue;

            Map<PoolKey, InvalidInputPool> pools = faultyParameterPools.get(rootApiKey);
            if (pools == null || pools.isEmpty()) {
                // Fallback: rootApiKey may have a literal path-param value (e.g. /admintravel/nullescss)
                // while the pool was generated under the templated form (/admintravel/{tripId}).
                String matchedKey = findPoolKeyByTemplateMatch(rootApiKey);
                if (matchedKey != null) {
                    pools = faultyParameterPools.get(matchedKey);
                    log.info("Fault pool lookup fallback: '{}' → '{}'", rootApiKey, matchedKey);
                }
                if (pools == null || pools.isEmpty()) continue;
            }

            for (Map.Entry<PoolKey, InvalidInputPool> pe : pools.entrySet()) {
                PoolKey key = pe.getKey();
                InvalidInputPool pool = pe.getValue();
                pool.resetUsage();

                // Use hasNextRoundRobin() rather than a null-return sentinel so that
                // legitimately stored null values (NULL_INPUT category) do not
                // prematurely terminate the drain loop. Capture both the type AND the
                // value here so fire-time can replay them directly without re-rotating
                // pool state (eliminates the label/value drift risk if anything mutates
                // the pool between build and fire).
                while (pool.hasNextRoundRobin()) {
                    Object val = pool.getNextRoundRobin();
                    String lastType = pool.getLastSelectedType();
                    queue.add(new FaultTarget(rootIdx + 1, rootApiKey,
                            key.getParamName(), key.getParamLocation(),
                            lastType != null ? lastType : "SEMANTIC_MISMATCH",
                            val));
                }
                pool.resetUsage();
            }
        }

        log.info("Fault injection queue built: {} targets across {} roots",
                queue.size(), roots.size());
        return queue;
    }

    /**
     * Re-order the exhaustive fault queue using Thompson sampling over a
     * Beta(α, β) posterior per (rootApiKey, paramName, faultType) — Fix 3
     * Layer 2.
     *
     * <p>α is seeded from the {@link InputFetchRegistry}'s recorded
     * parameter errors: each prior fault on a given (endpoint, parameter)
     * counts as one success, so previously-buggy targets float to the
     * front. β remains at the uniform prior; tightening it requires
     * persistent probe-count tracking (a Layer 2.1 follow-up), but even
     * the open-loop ordering already (i) prioritises known-buggy targets
     * under the {@code K_ZERO_STEP} / {@code K_DEDUP_EXHAUSTED} early
     * exits and (ii) preserves exhaustive coverage when the budget is
     * sufficient — the queue length is unchanged.
     *
     * <p>The set of (endpoint, parameter, faultType) keys is closed
     * (one bandit arm per queue entry) so no probe is wasted on an arm
     * that cannot be pulled.
     */
    List<FaultTarget> rankWithBandit(List<FaultTarget> queue) {
        if (queue.size() <= 1) return queue;
        return applyBanditGate(queue,
                MstConfig.instance().scheduler().banditEnabled(),
                this::rankWithBanditUnchecked);
    }

    /**
     * Gate seam for {@link #rankWithBandit(List)}. Returns {@code queue}
     * unchanged when {@code banditEnabled} is false, otherwise delegates to
     * {@code ranker}. Hoisted out of {@code rankWithBandit} so unit tests can
     * verify the gate without instantiating a full MistGenerator. Package-
     * private deliberately — the visible callers live in this class only.
     */
    static <T> List<T> applyBanditGate(List<T> queue, boolean banditEnabled,
                                       java.util.function.UnaryOperator<List<T>> ranker) {
        if (queue == null || queue.size() <= 1) return queue;
        if (!banditEnabled) {
            log.debug("Fault queue using insertion order (bandit disabled): {} targets", queue.size());
            return queue;
        }
        return ranker.apply(queue);
    }

    private List<FaultTarget> rankWithBanditUnchecked(List<FaultTarget> queue) {
        // Route the bandit's RNG through SeededRandom so two runs with the
        // same -Drandom.seed produce identical fault rankings. Previously
        // the no-arg ThompsonScheduler() constructed a fresh
        // {@code new Random()} (time-derived seed) on every call, which
        // re-ordered the fault queue differently across runs and broke
        // the byte-identical equivalence gate (PROMPT_H2 § 7.2).
        // Using a scope distinct from MistGenerator's own stream
        // ("bandit") keeps the two RNGs from sharing state.
        ThompsonScheduler bandit = new ThompsonScheduler(
                io.mist.core.util.SeededRandom.create("bandit"));
        InputFetchRegistry registry = loadRegistryQuietly();
        seedBanditFromRegistry(bandit, queue, registry);
        List<FaultTarget> ranked = bandit.rank(queue, MistGenerator::banditKey);
        log.debug("Fault queue re-ranked by ThompsonScheduler: {} targets, registry={}",
                ranked.size(), registry != null ? "loaded" : "absent");
        return ranked;
    }

    /**
     * Seed {@code bandit}'s α counter for each {@link FaultTarget} key with the
     * count of parameter errors recorded in {@code registry}. β stays at the
     * uniform prior. Package-private so the seeding contract can be unit-tested
     * without going through the on-disk registry path.
     */
    static void seedBanditFromRegistry(ThompsonScheduler bandit,
                                       List<FaultTarget> queue,
                                       InputFetchRegistry registry) {
        if (registry == null) return;
        for (FaultTarget t : queue) {
            List<ParameterError> errors = registry.getParameterErrors(t.rootApiKey, t.paramName);
            if (!errors.isEmpty()) {
                bandit.seed(banditKey(t), 1.0 + errors.size(), 1.0);
            }
        }
    }

    static String banditKey(FaultTarget t) {
        return t.rootApiKey + "|" + t.paramName + "|" + t.type;
    }

    private static InputFetchRegistry loadRegistryQuietly() {
        try {
            String path = System.getProperty("smart.input.fetch.registry.path",
                    "input-fetch-registry.yaml");
            java.io.File f = new java.io.File(path);
            if (!f.exists()) return null;
            return InputFetchRegistry.loadFromFile(f);
        } catch (Exception e) {
            log.debug("Bandit seeding skipped: registry load failed ({})", e.getMessage());
            return null;
        }
    }

    /**
     * Phase 1: when the registry holds VERIFIED_VALID entries that intersect
     * with the raw pool for this endpoint+param, return the intersection so
     * callers draw only from values the SUT has accepted before. Falls back
     * to the raw pool when the registry is null, has no entries for this
     * endpoint+param, or the intersection is empty.
     *
     * <p>Endpoint signature matches the writer-side
     * {@code recordParameterSuccess} contract: {@code "<METHOD> <route>"}.
     */
    private List<String> preferVerifiedValues(List<String> rawPool, String verb,
                                              String route, String paramName) {
        if (rawPool == null || rawPool.isEmpty()) return rawPool == null ? java.util.Collections.emptyList() : rawPool;
        if (poolStatusRegistry == null) return rawPool;
        String endpoint = verb.toUpperCase() + " " + route;
        List<String> verified = poolStatusRegistry.getVerifiedValues(endpoint, paramName);
        if (verified.isEmpty()) return rawPool;
        // Intersect raw pool with verified — preserves rawPool's ordering for
        // deterministic random.nextInt indexing across runs with the same seed.
        java.util.Set<String> verifiedSet = new java.util.HashSet<>(verified);
        List<String> intersection = new ArrayList<>();
        for (String v : rawPool) if (verifiedSet.contains(v)) intersection.add(v);
        if (!intersection.isEmpty()) {
            // Observability + two-phase verification: this non-target param's pool was narrowed to
            // values the SUT accepted (2xx) in Phase A, so Phase B draws only from SUT-verified inputs.
            log.info("Verified pool → {} {} {}: narrowed {} pool value(s) to {} SUT-verified {} ✅",
                    verb.toUpperCase(), route, paramName, rawPool.size(), intersection.size(), intersection);
        }
        return intersection.isEmpty() ? rawPool : intersection;
    }

    // Pattern to match HTTP operations in operation names
    private static final Pattern HTTP_OPERATION_PATTERN = 
        Pattern.compile("^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+(.+)$", Pattern.CASE_INSENSITIVE);

    public MistGenerator(OpenAPI primarySpec,
                                         TestConfigurationObject dummyPrimaryConf,
                                         Map<String, OpenAPI> serviceSpecs,
                                         Map<String, TestConfigurationObject> serviceConfigs,
                                         List<WorkflowScenario> scenarios,
                                         boolean useLLMforParams,
                                         @SuppressWarnings("unused") boolean ignoreFlowsFlag) {
        this.serviceSpecs     = serviceSpecs;
        this.serviceConfigs   = serviceConfigs;
        this.scenarios        = scenarios;
        this.originalScenarios = new ArrayList<>(scenarios);  // shallow snapshot
        this.useLLM           = useLLMforParams;
        // Root API Mode is the PRIMARY mode (per the paper).  When true, the generator
        // keeps all top-level root API spans in every test case and prunes internal
        // step-API / service-to-service spans; each scenario becomes a sequence of
        // gateway-entry calls only.  When false, every internal span is also materialized
        // as its own step (Multi-Step Replay) — much larger test suites, rarely desired.
        // Default flipped to true so runs with a bare properties file don't silently
        // enter Multi-Step Replay.  Property name kept for backward compatibility.
        MstConfig mstCfg = MstConfig.instance();
        this.onlyFirstBusinessStep = mstCfg.core().generateOnlyFirstStep();
        log.info("Root API mode: onlyFirstBusinessStep={} (true → prune step-API spans, default)", this.onlyFirstBusinessStep);
        this.faultyRatio = (float) mstCfg.faulty().ratio();
        this.faultyRoundRobin = mstCfg.faulty().roundRobin();
        this.dependencyRegistry = SemanticDependencyRegistry.build(
                serviceConfigs, serviceSpecs, scenarios);

        log.info("=== NEGATIVE TEST CONFIGURATION ===");
        log.info("faulty.ratio from MstConfig: {}", mstCfg.faulty().ratio());
        log.info("Parsed faultyRatio: {}", this.faultyRatio);
        log.info("This means {}% of test variants will be negative tests (invalid inputs)", this.faultyRatio * 100);
        log.info("Invalid parameter selection mode: {}", this.faultyRoundRobin ? "ROUND-ROBIN" : "RANDOM");
        
        // Initialize Smart Input Fetching System
        initializeSmartInputFetching();
    }

    /**
     * Initialize the Smart Input Fetching System
     */
    private void initializeSmartInputFetching() {
        try {
            log.info("🔧 Initializing Smart Input Fetching System for MistGenerator...");

            // Load configuration from system properties.
            //
            // We include both prefixes:
            //   * "smart.input.fetch.*" — this module's own settings; also gates the
            //     "no smart-fetch config" early return below.
            //   * "auth.*"              — consumed by SmartFetchAuthManager (admin
            //     username/password, login path/fields, token path, validity).  Before
            //     this fix the filter dropped them, leaving smart-fetch unauthenticated;
            //     every admin-routed parameter-discovery call then returned HTTP 403 and
            //     tests fell back to placeholder values like "FALLBACK_id_10".
            Map<String, String> properties = new HashMap<>();
            int smartFetchKeyCount = 0;
            for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
                String key = entry.getKey().toString();
                String value = entry.getValue() == null ? "" : entry.getValue().toString();
                if (key.startsWith("smart.input.fetch")) {
                    properties.put(key, value);
                    smartFetchKeyCount++;
                } else if (key.startsWith("auth.")) {
                    properties.put(key, value);
                }
            }

            // Also load base.url
            if (System.getProperty("base.url") != null) {
                properties.put("base.url", System.getProperty("base.url"));
                log.debug("Found base.url: {}", System.getProperty("base.url"));
            }

            if (smartFetchKeyCount == 0) {
                log.warn("❌ No smart input fetching properties found, using traditional LLM generation only");
                log.warn("   Make sure properties like 'smart.input.fetch.enabled=true' are in your properties file");
                return;
            }

            log.info("✅ Found {} smart input fetching properties (+ auth/base passthrough, {} total keys)",
                    smartFetchKeyCount, properties.size());
            for (String key : properties.keySet()) {
                String value = properties.get(key);
                // Redact secret-like values so passwords / tokens are not written to the
                // log file at INFO level.
                String displayValue = value;
                String lowerKey = key.toLowerCase(java.util.Locale.ROOT);
                if (value != null && !value.isEmpty()
                        && (lowerKey.contains("password") || lowerKey.contains("token")
                            || lowerKey.contains("secret"))) {
                    displayValue = "<redacted, len=" + value.length() + ">";
                }
                log.info("   - {}: {}", key, displayValue);
            }
            
            smartFetchConfig = SmartInputFetchConfig.fromProperties(properties);
            
            if (smartFetchConfig.isEnabled()) {
                String baseUrl = properties.getOrDefault("base.url", "http://localhost:8080");
                smartFetcher = new SmartInputFetcher(smartFetchConfig, baseUrl);
                log.info("🚀 SmartInputFetcher initialized successfully!");
                log.info("   - Base URL: {}", baseUrl);
                log.info("   - Registry: {}", smartFetchConfig.getRegistryPath());
                log.info("   - Smart Fetch Percentage: {}%", 
                         smartFetchConfig.getSmartFetchPercentage() * 100);
                log.info("   - LLM Discovery: {}", smartFetchConfig.isLlmDiscoveryEnabled());
                log.info("🎯 YOU SHOULD NOW SEE 'Smart Fetch →' LOGS DURING PARAMETER GENERATION!");
            } else {
                log.warn("❌ Smart input fetching is DISABLED (smart.input.fetch.enabled=false)");
                log.warn("   Enable it by setting smart.input.fetch.enabled=true in your properties file");
                smartFetcher = null;
                smartFetchConfig = null;
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to initialize Smart Input Fetching: {}", e.getMessage(), e);
            log.warn("Falling back to traditional LLM generation");
            smartFetcher = null;
            smartFetchConfig = null;
        }
    }

    /*  PUBLIC API – called by RESTest                              */

    // Shared parameter pools grouped by root API to avoid redundant LLM/semantic generation
    private Map<String, Map<String, List<String>>> sharedParameterPools = new HashMap<>();

    /**
     * Phase 1 part 2: temporarily override {@link #faultyRatio} for a single
     * {@code generate()} call. Used by MistRunner's two-phase flow to force
     * Phase A to produce only positive variants ({@code 0.0f}) before
     * restoring the original ratio for Phase B. Caller is responsible for
     * restoring; no internal stack.
     */
    public void setFaultyRatio(float ratio) {
        this.faultyRatio = ratio;
    }

    /**
     * Phase 1 part 2: current {@link #faultyRatio} so the runner can save +
     * restore it around Phase A.
     */
    public float getFaultyRatio() {
        return this.faultyRatio;
    }

    /**
     * Phase 1 part 2: clear cross-call dedup state so a second
     * {@code generate()} call (Phase B in the two-phase flow) produces
     * variants without being suppressed by Phase A's approval set.
     *
     * <p>{@code seenPayloads} and {@code dedupExhaustionStreak} are per-
     * scenario locals initialized inside the variant loop, so they don't
     * survive across {@code generate()} calls; only {@code approvedApiKeys}
     * is a class field that accumulates.
     *
     * <p>Also reloads {@link #poolStatusRegistry} so Phase B's Sniper
     * preferVerifiedValues sees the values that Phase A's drain pushed to
     * disk between the two calls.
     */
    public void resetForNewPhase() {
        approvedApiKeys.clear();
        poolStatusRegistry = loadRegistryQuietly();
        // Replace the live scenarios list with a fresh copy of the original
        // refs so Phase B's pipeline stages see the pre-Phase-A state.
        // NOTE: this is a shallow restore — individual WorkflowScenario
        // instances may carry mutated internal state from Phase A's stages.
        // For trainticket's stage set this is observably fine; if a future
        // stage mutates WorkflowScenario fields directly, this needs to
        // become a deep clone.
        // approvedInDedupPass IS such Phase-A-mutated state: Phase 2.5 tags the
        // original instances in place. approvedApiKeys was just cleared, so a
        // stale true tag would make Phase B's dedup passes skip every scenario
        // AND register zero keys — Phase 4 then re-emits _RT baselines for APIs
        // that standalone 1-roots already cover. Clear it with the key set.
        for (WorkflowScenario sc : originalScenarios) {
            sc.setApprovedInDedupPass(false);
        }
        this.scenarios = new ArrayList<>(originalScenarios);
    }

    // Phase 1: registry of SUT-verified pool values, loaded once at generate()
    // start so the per-parameter Sniper pool pull can prefer verified values
    // over the raw pool. null when smart.input.fetch is disabled or the
    // registry file is missing — falls back to raw pool with no behavior change.
    private InputFetchRegistry poolStatusRegistry;

    /** Produce test cases using two-stage LLM + semantic expansion approach. */
    public Collection<TestCase> generate() {
        // Reset the endpoint-fallback counter for this run. It increments only
        // when service-name matching misses and a step is resolved to a conf op
        // by HTTP method+path instead (cross-SUT generalisation). For train-ticket
        // this MUST remain 0 — trace service names equal conf keys, so the
        // service-name match always wins first and output is byte-identical.
        StageSupport.resetEndpointFallbackCount();

        // Phase 1: load registry once per generate() so Sniper can prefer
        // SUT-verified values for non-target params. Cheap (one YAML parse).
        this.poolStatusRegistry = loadRegistryQuietly();
        List<TestCase> out = new ArrayList<>();
        int counter = 1;

        // Phase pipeline: Phase 2.5 dedup → shared pool generation → Phase 3
        // shattering (gated) → Phase 3.5 post-shatter dedup (gated) → Phase 4
        // decomposition.  Each stage owns its own phase logic; the
        // PipelineContext threads the mutable scenarios list, dedup approval
        // set, and the LLM / smart-fetch state through so the stages do not
        // need a reference back to this generator instance.  The shared and
        // faulty pool maps are the SAME instances this generator reads from
        // in the variant loop below — the stage writes through them in place.
        PipelineContext ctx = new PipelineContext(
                scenarios, serviceSpecs, serviceConfigs,
                dependencyRegistry, approvedApiKeys, MstConfig.instance(),
                llmGen, smartFetcher, smartFetchConfig, useLLM,
                sharedParameterPools, faultyParameterPools);
        new WorkflowPipeline(java.util.Arrays.asList(
                new Phase25DedupStage(),
                new SharedPoolGenerationStage(),
                new Phase3ShatteringStage(),
                new Phase35DedupStage(),
                new Phase4DecompositionStage())).execute(ctx);

        // Dump registry for manual auditing
        dependencyRegistry.dumpRegistryToFile("target/semantic-registry-dump.json");

        // Reset JIT counters for this generation run
        jitDictionaryHits = 0;
        jitFuzzingFallbacks = 0;

        // Generate test cases using shared pools
        ConsoleProgressBar.begin("Variant Gen", scenarios.size());
        for (WorkflowScenario sc : scenarios) {
            // Generate multiple variants per scenario using shared parameter pools
            List<MultiServiceTestCase> variants = generateScenarioVariants(sc, counter);
            out.addAll(variants);
            ConsoleProgressBar.update("Scenario " + counter);
            counter += variants.size();
        }
        ConsoleProgressBar.complete();

        logJitBindingMetrics();
        // Confirm whether the endpoint (method+path) fallback was needed this
        // run. 0 ⇒ pure service-name matching (expected for train-ticket); >0 ⇒
        // a SUT with diverging trace service names relied on the generalisation.
        log.info("Endpoint-fallback fired {}× this generation run (0 = service-name match always succeeded).",
                StageSupport.getEndpointFallbackCount());
        return out;
    }

    private void logJitBindingMetrics() {
        int total = jitDictionaryHits + jitFuzzingFallbacks;
        double hitRate = total > 0 ? (jitDictionaryHits * 100.0 / total) : 0.0;
        log.info("╔══════════════════════════════════════════════════════════════════╗");
        log.info("║          SEMANTIC DEPENDENCY REGISTRY — JIT BINDING METRICS     ║");
        log.info("╠══════════════════════════════════════════════════════════════════╣");
        log.info("║  Dictionary Hits (wired successfully):   {}                 ║", String.format("%6d", jitDictionaryHits));
        log.info("║  Fuzzing Fallbacks (producer not in seq): {}                 ║", String.format("%5d", jitFuzzingFallbacks));
        log.info("║  Total ID-param lookups with producer:   {}                 ║", String.format("%6d", total));
        log.info("║  Hit Rate: {}%                                             ║", String.format("%6.1f", hitRate));
        log.info("╚══════════════════════════════════════════════════════════════════╝");
    }
    
    /**
     * Generate multiple test case variants for a single scenario.
     * Uses shared parameter pools with random selection and global payload
     * deduplication to guarantee 100% unique positive variants.
     */
    private List<MultiServiceTestCase> generateScenarioVariants(WorkflowScenario sc, int baseCounter) {
        List<MultiServiceTestCase> variants = new ArrayList<>();
        Set<String> seenPayloads = new HashSet<>();

        // Reset per-scenario state on the smart fetcher so the diverse-value rotation cursor
        // starts at 0 for every new scenario instead of inheriting wherever the previous
        // scenario left off (deterministic order regardless of scenario ordering).
        if (smartFetcher != null) {
            smartFetcher.resetValueRotation();
        }

        // ── 0. Pre-traversal scenario buildability check ──────────────
        // traverse() returns 0 steps when no root step (or descendant) maps to a
        // service present in serviceConfigs — i.e. the scenario is entirely
        // gateway-only or routes to services whose OpenAPI specs are not loaded.
        // Without this check the variant loop would run N times and each variant
        // would silently produce an empty test case, emitting one WARN per variant
        // (600+ identical warnings in observed runs). Detect that condition once,
        // up front, log a single explanation, and skip the scenario entirely so
        // missing-coverage signal is preserved without log spam.
        if (!scenarioHasBuildableRoot(sc)) {
            log.warn("Skipping scenario {} entirely — no root step resolves to a service in serviceConfigs "
                    + "(likely gateway-only or unconfigured downstream services). 0 variants generated.",
                    baseCounter);
            return variants;
        }

        int configuredVariantCount = getVariantCountFromProperties();

        log.info("=== TARGETED FAULT INJECTION MATRIX ===");
        log.info("Configured variant count: {}, faultyRatio: {}", configuredVariantCount, faultyRatio);

        // ── 1. Build the exhaustive fault-injection queue ──────────────
        // Fix 3 Layer 2: re-rank using Thompson sampling. Length is preserved,
        // so the exhaustive guarantee in §III holds; only the order changes,
        // biasing known-buggy targets toward earlier variant indices so the
        // K_ZERO_STEP / K_DEDUP_EXHAUSTED early exits favour high-value tests.
        List<FaultTarget> faultQueue = rankWithBandit(buildFaultInjectionQueue(sc));
        // faultyRatio == 0 is an explicit positives-only request (single-phase
        // opt-out, or two-phase Phase A via MistRunner.setFaultyRatio(0)). The
        // exhaustive per-FaultTarget floor below must not override it: variants
        // are classified negative by index against faultQueue.size(), so the
        // queue itself has to be emptied, not just requiredNegative.
        if (faultyRatio <= 0f && !faultQueue.isEmpty()) {
            log.info("faultyRatio=0 — suppressing {} fault target(s); positives-only generation", faultQueue.size());
            faultQueue = java.util.Collections.emptyList();
        }
        int totalNegativeSlots = faultQueue.size();

        // ── 2. Dynamic Variant Sizing ─────────────────────────────────
        // Guarantee enough positive variants, plus at least one negative
        // variant for every single invalid value across all roots.
        int positiveBase = Math.max(1, Math.round(configuredVariantCount * (1.0f - faultyRatio)));
        int requiredNegative = Math.max(Math.round(configuredVariantCount * faultyRatio), totalNegativeSlots);
        int variantCount = positiveBase + requiredNegative;

        // ── 2a. Per-endpoint variant-budget cap ────────────────────────
        // Resolve policy early so a budget>0 can cap variantCount BEFORE
        // anything downstream allocates per-variant state. Without this,
        // scenarios with large exhaustive fault queues (POST + many params ×
        // 4 fault types easily exceeds 500) overflow into single-file Java
        // sources that javac cannot compile at default heap. The bandit
        // ranking at line 513 already biased high-value fault targets to the
        // front, so capping at the budget keeps the most informative tests.
        final EndpointPolicy policy = resolveEndpointPolicy(sc);
        if (policy.variantBudget() > 0 && variantCount > policy.variantBudget()) {
            log.info("Variant budget cap: scenario {} would emit {} variants " +
                     "({} positive, {} negative); capping at {} per policy.variantBudget()",
                    baseCounter, variantCount, positiveBase, requiredNegative, policy.variantBudget());
            variantCount = policy.variantBudget();
        }

        if (variantCount > configuredVariantCount) {
            log.info("Dynamic Variant Sizing: overriding configured {} to {} " +
                     "({}+ positive, {} negative covering {} exhaustive fault targets)",
                    configuredVariantCount, variantCount, positiveBase, requiredNegative, totalNegativeSlots);
        }

        // Reset all pools so round-robin starts fresh for actual generation
        for (Map<PoolKey, InvalidInputPool> pools : faultyParameterPools.values()) {
            for (InvalidInputPool pool : pools.values()) {
                pool.resetUsage();
            }
        }

        // ── 3. Assign variant indices to positive / negative ──────────
        // First `positiveBase` are positive; rest are negative, each
        // mapped 1:1 to a FaultTarget in the queue.
        int faultQueueCursor = 0;

        // Flow-centric scenario identifier.
        // Decomposed scenarios carry a tag like "_RT1" that is appended
        // to their parent scenario's index for traceability.
        String scenarioId;
        if (sc.getDecomposedTag() != null && sc.getParentScenarioIndex() > 0) {
            scenarioId = "Flow_Scenario_" + sc.getParentScenarioIndex() + sc.getDecomposedTag();
        } else {
            scenarioId = "Flow_Scenario_" + baseCounter;
        }

        // Early-exit guards (per scenario).
        //   zeroStepStreak — consecutive variants whose traverse() emitted 0 steps.
        //     The upfront scenarioHasBuildableRoot() check filters whole-scenario
        //     gateway-only cases; this guard catches a more subtle case where a few
        //     individual variants still come out empty (e.g. all sub-paths happen to
        //     skip via gotoChildren). After K_ZERO_STEP in a row we bail out.
        //   dedupExhaustionStreak — consecutive variants whose 5-retry dedup loop
        //     failed to produce a fresh fingerprint. Late variants commonly fail
        //     14-16x in a row when the random-draw space is exhausted; the inner
        //     5-retry cap stays, but at K_DEDUP_EXHAUSTED in a row we stop issuing
        //     new variants for this scenario (pool is finite, no point trying more).
        int zeroStepStreak = 0;
        int dedupExhaustionStreak = 0;
        // ── Per-endpoint adaptive policy (legacy = fixed 3/10/PAYLOAD) ─────
        // When mst.adaptive.enabled=false (default), policy == LEGACY → identical
        // pre-adaptive behaviour. When enabled, thresholds are resolved from the
        // scenario's first root step (HTTP method + OpenAPI x-mist-* hints).
        // policy was already resolved in section 2a above (variant-budget cap).
        final int K_ZERO_STEP = policy.kZeroStep();
        final int K_DEDUP_EXHAUSTED = policy.kDedupExhausted();

        ConsoleProgressBar.begin("Variants", variantCount);
        for (int v = 0; v < variantCount; v++) {
            // Generate NEGATIVE variants FIRST (covering each FaultTarget once),
            // then positives. Earlier ordering put positives first, so when the
            // K_DEDUP_EXHAUSTED early-abort triggered during the positive phase
            // (which can happen on scenarios with small fingerprint spaces, e.g.
            // single-step DELETE /admintravel/{tripId}), all negative variants
            // were skipped — losing fault-injection coverage.
            boolean isFaultyVariant = (v < faultQueue.size());

            // ── Resolve current FaultTarget (Sniper Strategy) ─────────
            FaultTarget currentTarget = null;
            int targetFaultRootIndex = -1;
            List<String> targetFaultyParams = new ArrayList<>();

            if (isFaultyVariant) {
                currentTarget = faultQueue.get(v);  // v is now the fault-target index when v < queue.size
                targetFaultRootIndex = currentTarget.rootIndex;
                targetFaultyParams.add(currentTarget.paramName);
                faultQueueCursor++;
            }

            // Flow-centric naming:
            //   positive → test_positive_flow_S12_v3
            //   negative → test_negative_flow_S12_v3_fault_Root2_OVERFLOW
            String testName;
            if (isFaultyVariant) {
                testName = "test_negative_flow_S" + baseCounter
                        + "_v" + (v + 1)
                        + "_fault_Root" + targetFaultRootIndex
                        + "_" + currentTarget.type;
            } else {
                testName = "test_positive_flow_S" + baseCounter + "_v" + (v + 1);
            }

            MultiServiceTestCase tc = new MultiServiceTestCase(testName);
            tc.setScenarioName(scenarioId);
            tc.setFaulty(isFaultyVariant);

            // Attach structured fault context so the Writer can emit rich Allure metadata
            if (isFaultyVariant && currentTarget != null) {
                tc.setTargetFaultRootId("Root " + targetFaultRootIndex);
                tc.setFaultTypeCategory(currentTarget.type);
                tc.setTargetFaultRootApiPath(currentTarget.rootApiKey);
                // Carry the normalised parameter location so the writer can route the
                // invalid value into the correct request slot (header / cookie / path /
                // query / body). Without this, two same-name parameters at different
                // locations would be indistinguishable at fire time.
                tc.setTargetFaultParamLocation(currentTarget.paramLocation);
                // Replay the exact value captured at queue build time so fire-time
                // emission cannot drift from the recorded type label.
                tc.setTargetFaultValue(currentTarget.value);
            }

            String faultyMarker = isFaultyVariant
                    ? "NEGATIVE (sniper R" + targetFaultRootIndex + " param " + targetFaultyParams + " [" + currentTarget.type + "])"
                    : "POSITIVE";
            log.info("--- Variant {}/{}: {} [{}] ---", v + 1, variantCount, testName, faultyMarker);

            Map<String, String> context = new HashMap<>();

            // ── 4. Traverse all roots ────────────────────────────────
            // For negative variants, ONLY the targeted root receives the
            // fault; all preceding (and succeeding) roots receive strictly
            // positive inputs — the "Sniper" strategy.
            List<WorkflowStep> roots = sc.getRootSteps();
            for (int rootIdx = 0; rootIdx < roots.size(); rootIdx++) {
                int oneBasedRoot = rootIdx + 1;
                String rootPrefix = "R" + oneBasedRoot;

                boolean faultThisRoot = isFaultyVariant && (oneBasedRoot == targetFaultRootIndex);

                traverse(roots.get(rootIdx), tc, context, rootPrefix,
                        oneBasedRoot, true,
                        v,
                        faultThisRoot,           // only the sniper target gets faults
                        faultThisRoot ? targetFaultyParams : Collections.emptyList(),
                        faultThisRoot && currentTarget != null ? currentTarget.rootApiKey : null);
            }

            // Prune internal spans in Root API mode
            if (onlyFirstBusinessStep) {
                int before = tc.getSteps().size();
                tc.getSteps().removeIf(step -> !step.isTopLevelRoot());
                if (tc.getSteps().size() < before) {
                    log.info("Root API mode: pruned {} internal spans, kept {} root steps",
                            before - tc.getSteps().size(), tc.getSteps().size());
                }
            }

            // Scenario name stays flow-centric (Flow_Scenario_N).
            // No longer overwritten by the first step's API path.

            // Validate that negative tests actually received invalid values
            if (isFaultyVariant) {
                boolean hasValidInvalidParams = false;
                for (String fp : tc.getFaultyParameters()) {
                    String value = fp.contains("=") ? fp.substring(fp.indexOf("=") + 1) : fp;
                    if (value != null
                            && !value.startsWith("INVALID_VALUE_MISSING_")
                            && !value.startsWith("VAL_")
                            && !value.startsWith("STEP1_")) {
                        hasValidInvalidParams = true;
                        break;
                    }
                }
                if (tc.getFaultyParameters().isEmpty() || !hasValidInvalidParams) {
                    log.warn("Variant {} was NEGATIVE but no valid invalid params set — converting to POSITIVE", v + 1);
                    tc.setFaulty(false);
                    tc.getFaultyParameters().clear();
                    tc.setTargetFaultRootId(null);
                    tc.setFaultTypeCategory(null);
                    tc.setTargetFaultParamLocation(null);
                    // Rewrite method name from negative to positive
                    String demoted = "test_positive_flow_S" + baseCounter + "_v" + (v + 1);
                    tc.setOperationId(demoted);
                }
            }

            if (tc.getFaulty()) {
                log.info("NEGATIVE TEST: {} — faulty params: {}", tc.getOperationId(), tc.getFaultyParameters());
            }

            if (tc.getSteps().isEmpty()) {
                log.warn("Discarding variant {} — traversal produced 0 steps (service config missing or gateway-only scenario)",
                        v + 1);
                zeroStepStreak++;
                if (zeroStepStreak >= K_ZERO_STEP) {
                    log.warn("Aborting scenario {} after {} consecutive 0-step variants — "
                            + "no further variants will be issued (suppressing what would have been "
                            + "{} more empty-traversal warnings).",
                            baseCounter, zeroStepStreak, variantCount - (v + 1));
                    ConsoleProgressBar.update("v" + (v + 1) + " EMPTY");
                    break;
                }
            } else {
                zeroStepStreak = 0;
                // Global payload deduplication for positive variants
                // Honour policy.dedupMode(): OFF skips dedup entirely (POST and
                // x-mist-stateful endpoints), PAYLOAD = current behaviour.
                String fingerprint = buildPayloadFingerprint(tc);
                boolean skipDedup = (policy.dedupMode() == EndpointPolicy.DedupMode.OFF);
                if (tc.getFaulty() || skipDedup || seenPayloads.add(fingerprint)) {
                    variants.add(tc);
                    dedupExhaustionStreak = 0;
                } else {
                    // Duplicate detected — retry with fresh random draws
                    boolean unique = false;
                    for (int retry = 0; retry < 5 && !unique; retry++) {
                        tc = new MultiServiceTestCase(testName);
                        tc.setScenarioName(scenarioId);
                        tc.setFaulty(false);
                        Map<String, String> retryContext = new HashMap<>();
                        for (int rootIdx2 = 0; rootIdx2 < roots.size(); rootIdx2++) {
                            int oneBasedRoot2 = rootIdx2 + 1;
                            traverse(roots.get(rootIdx2), tc, retryContext, "R" + oneBasedRoot2,
                                    oneBasedRoot2, true, v, false, Collections.emptyList(), null);
                        }
                        if (onlyFirstBusinessStep) {
                            tc.getSteps().removeIf(step -> !step.isTopLevelRoot());
                        }
                        fingerprint = buildPayloadFingerprint(tc);
                        if (seenPayloads.add(fingerprint)) {
                            unique = true;
                            variants.add(tc);
                            dedupExhaustionStreak = 0;
                            log.info("Dedup retry {}: found unique combination for variant {}", retry + 1, v + 1);
                        }
                    }
                    if (!unique) {
                        log.warn("Dedup: variant {} still duplicate after 5 retries — skipping", v + 1);
                        dedupExhaustionStreak++;
                        if (dedupExhaustionStreak >= K_DEDUP_EXHAUSTED) {
                            // Exhaustion break: the random-draw space for this scenario is
                            // finite, and K_DEDUP_EXHAUSTED back-to-back failures indicate
                            // the unique-fingerprint pool is fully drained. Issuing more
                            // variants will only burn CPU on retries that cannot succeed.
                            log.warn("Aborting scenario {} after {} consecutive dedup-exhausted variants — "
                                    + "fingerprint pool is drained; {} remaining variant slots will be skipped.",
                                    baseCounter, dedupExhaustionStreak, variantCount - (v + 1));
                            ConsoleProgressBar.update("v" + (v + 1) + " EXHAUSTED");
                            break;
                        }
                    }
                }
            }
            ConsoleProgressBar.update("v" + (v + 1) + " " + (isFaultyVariant ? "NEG" : "POS"));
            log.info("--- Completed variant {} with {} steps ---", v + 1, tc.getSteps().size());
        }
        ConsoleProgressBar.complete();

        // Summary
        long actualFaultyCount = variants.stream().filter(TestCase::getFaulty).count();
        long actualNormalCount = variants.size() - actualFaultyCount;
        int dedupSkipped = variantCount - variants.size();
        log.info("=== GENERATION SUMMARY ===");
        log.info("Total variants: {} (positive: {}, negative: {}, dedup-skipped: {})",
                variants.size(), actualNormalCount, actualFaultyCount, dedupSkipped);
        log.info("Fault queue coverage: {}/{} targets fired", faultQueueCursor, faultQueue.size());

        return variants;
    }

    /**
     * Validation toggle inherited from the legacy RESTest API. MIST has its own
     * span-based validation (Trace Shape Oracle) so the OAS-validity check this
     * flag controlled in classic RESTest is a no-op here; the setter is kept so
     * the legacy CLI surface (MistRunner / TestGenerationAndExecution) still
     * compiles unchanged.
     */
    public void setCheckTestCases(boolean ignored) {
        // intentionally empty
    }

    /* ============================================================ */

    /**
     * Depth-first traversal with hierarchical step numbering (R1, R1.1, R2, R2.3.1, etc.)
     * and variant-specific parameter generation.
     *
     * @param span              current WorkflowStep
     * @param tc                test-case under construction
     * @param context           key→value outputs collected so far
     * @param stepNumber        hierarchical step ID (e.g., "R1", "R1.1", "R2.3.1")
     * @param rootIndex         1-based index of the current Root API tree being traversed
     * @param isTopLevelRoot    true only when this call represents a scenario root (not a child span)
     * @param variantIndex      index of current test variant for parameter selection
     * @param isFaultyVariant   whether this root tree should receive faulty parameters
     * @param targetFaultyParams list of parameter names that should be faulty
     * @param faultRootApiKey   the rootApiKey for the targeted root's faulty pool (may be null)
     */
    private void traverse(WorkflowStep span,
                          MultiServiceTestCase tc,
                          Map<String,String> context,
                          String stepNumber,
                          int rootIndex,
                          boolean isTopLevelRoot,
                          int variantIndex,
                          boolean isFaultyVariant,
                          List<String> targetFaultyParams,
                          String faultRootApiKey) {

        // In Root API mode (onlyFirstBusinessStep): only process top-level root nodes.
        // Once we have emitted one StepCall for this root, skip its children.
        if (onlyFirstBusinessStep && !isTopLevelRoot) {
            return;
        }

        /* 1. Extract HTTP operation info from span ---------------------------------- */
        final String service = span.getServiceName();
        final String opName  = span.getOperationName();

        // Try to extract HTTP method and path from various operation name formats
        String verb = null, route = null;
        
        // Check if it's an HTTP operation pattern (e.g., "POST /api/v1/path")
        Matcher httpMatcher = HTTP_OPERATION_PATTERN.matcher(opName);
        if (httpMatcher.matches()) {
            verb = httpMatcher.group(1).toLowerCase(Locale.ROOT);
            route = httpMatcher.group(2);
        } else {
            // Check if we can extract from attributes/tags
            Map<String, String> outputs = span.getOutputFields();
            String httpMethod = outputs.get("http.method");
            String httpTarget = outputs.get("http.target");
            String httpUrl = outputs.get("http.url");
            
            if (httpMethod != null && (httpTarget != null || httpUrl != null)) {
                verb = httpMethod.toLowerCase(Locale.ROOT);
                route = httpTarget != null ? httpTarget : extractPathFromUrl(httpUrl);
            } else {
                log.debug("Skipping non-HTTP span: {} - {}", service, opName);
                gotoChildren(span, tc, context, stepNumber, rootIndex, isTopLevelRoot,
                        variantIndex, isFaultyVariant, targetFaultyParams, faultRootApiKey);
                return;
            }
        }

        if (verb == null || route == null) {
            log.debug("Could not extract HTTP method/path from span: {} - {}", service, opName);
            gotoChildren(span, tc, context, stepNumber, rootIndex, isTopLevelRoot,
                    variantIndex, isFaultyVariant, targetFaultyParams, faultRootApiKey);
            return;
        }

        // Skip login/auth related operations (writer handles login as Step 0)
        if (isLoginOrAuthOperation(service, opName)) {
            log.debug("Skipping login/auth operation in generator: {} - {} {}", service, verb, route);
            gotoChildren(span, tc, context, stepNumber, rootIndex, isTopLevelRoot,
                    variantIndex, isFaultyVariant, targetFaultyParams, faultRootApiKey);
            return;
        }

        /* 2. Load service-specific test-configuration ------------------------------ */
        // Service-name match FIRST (train-ticket always resolves here, so its
        // generated output is byte-identical). The template-aware ENDPOINT
        // fallback is gated to the ROOT / FIRST-BUSINESS step only — we never
        // fall back for arbitrary deep descendants, which would over-generate
        // variants for intentionally skipped downstream services.
        //
        // Gateway/proxy spans (e.g. train-ticket ts-gateway-service, Bookinfo
        // istio-ingressgateway) are explicitly EXCLUDED from the fallback: they
        // are routing-only and the designed behaviour is to propagate to their
        // children (gotoChildren below, which forwards the root flag). For
        // train-ticket the business child then matches by service name, so the
        // fallback never fires (count stays 0 / output byte-identical). For
        // Bookinfo the gateway is skipped here and the real BFF business step
        // (productpage.default) arrives next as the root, where the fallback
        // can fire if its service name is absent from the conf keys.
        TestConfigurationObject cfg = serviceConfigs.get(service);
        Operation opCfg = (cfg != null) ? findOperation(cfg, verb, route) : null;

        boolean rootStep = isTopLevelRoot || tc.getSteps().isEmpty();
        boolean gatewaySpan = isGatewayOperation(service, opName);
        if (opCfg == null && rootStep && !gatewaySpan) {
            StageSupport.ResolvedOperation resolved =
                    StageSupport.resolveOperation(serviceConfigs, service, verb, route);
            if (resolved != null) {
                cfg = resolved.cfg;
                opCfg = resolved.op;
            }
        }

        if (cfg == null && opCfg == null) {
            // Routing-only services (e.g. ts-gateway-service) intentionally have no
            // per-service test config; propagating to children is the designed behaviour,
            // not an anomaly. DEBUG-level so it doesn't dominate the run log
            // (17K+ identical lines per run otherwise).
            log.debug("No test-configuration for service '{}' (step {}), propagating to children", service, stepNumber);
            gotoChildren(span, tc, context, stepNumber, rootIndex, isTopLevelRoot,
                    variantIndex, isFaultyVariant, targetFaultyParams, faultRootApiKey);
            return;
        }

        if (opCfg == null) {
            log.warn("No Operation config {} {} in service '{}' (step {})", verb, route, service, stepNumber);
            gotoChildren(span, tc, context, stepNumber, rootIndex, isTopLevelRoot,
                    variantIndex, isFaultyVariant, targetFaultyParams, faultRootApiKey);
            return;
        }

        /* 3. Build parameter maps from trace data and LLM --------------------------- */
        Map<String,String> pathParams   = new LinkedHashMap<>();
        Map<String,String> queryParams  = new LinkedHashMap<>();
        Map<String,String> headerParams = new LinkedHashMap<>();
        // Cookie params are routed separately so the writer can emit
        // .cookie(name, value) for each entry (and substitute the invalid value when the
        // sniper target is at location 'cookie').
        Map<String,String> cookieParams = new LinkedHashMap<>();
        Map<String,Object> bodyFields   = new LinkedHashMap<>();

        String resolvedPath = route;

        // Define step roles for parameter generation
        boolean isFirstBusinessStep = tc.getSteps().isEmpty();
        boolean isSubsequentStep = !isFirstBusinessStep;

        // Extract parameters from trace data only for subsequent steps
        if (isSubsequentStep) {
            extractParametersFromTrace(span, bodyFields, queryParams, pathParams, headerParams);
        }

        if (opCfg.getTestParameters() != null) {
            log.info("🔍 Processing {} parameters for step {} (firstBusiness: {}, subsequent: {})",
                    opCfg.getTestParameters().size(), stepNumber, isFirstBusinessStep, isSubsequentStep);

            // Collect all parameter names for this API (for LLM context)
            List<String> allParamNames = new java.util.ArrayList<>();
            for (TestParameter tp : opCfg.getTestParameters()) {
                allParamNames.add(tp.getName());
            }
            
            // Build API name for context (e.g., "POST /api/v1/adminorder")
            String apiName = (verb != null && route != null) ? verb.toUpperCase() + " " + route : opName;

            for (TestParameter p : opCfg.getTestParameters()) {
                log.info("📋 Parameter: {} (type: {}, in: {}, description: '{}')",
                        p.getName(), p.getType(), p.getIn(), p.getDescription());
                String val = null;  // For path/query/header params (must be strings)
                Object typedVal = null;  // For body params (can be typed objects)
                // Distinguishes "typedVal was intentionally set to null" (e.g. NULL_INPUT body
                // value, which should serialise as JSON null) from "typedVal was never assigned".
                boolean typedValSet = false;

                if (isFirstBusinessStep) {
                    /* Step 1 (First Business Step): Check if negative test, then use smart fetch or invalid values */
                    log.info("🎯 Step 1 parameter '{}' - attempting smart fetch", p.getName());
                    
                    boolean faultyValueSet = false;

                    if (useLLM) {
                        // Create ParameterInfo with full API context for better LLM generation
                        ParameterInfo info = createParameterInfoWithContext(p, apiName, service, allParamNames);
                        
                        // Check if this is a negative test variant AND this is one of the target invalid parameters
                        if (isFaultyVariant && targetFaultyParams != null && targetFaultyParams.contains(p.getName())) {
                            log.info("🔴 NEGATIVE TEST: Making parameter '{}' invalid (target param)", p.getName());
                            
                            // Resolve the correct faulty pool: prefer the sniper-provided key,
                            // fall back to deriving it from the current step's verb/route
                            String resolvedFaultKey = faultRootApiKey != null
                                    ? faultRootApiKey
                                    : verb.toUpperCase() + "_" + route.replaceAll("[^a-zA-Z0-9_]", "_");
                            log.debug("Looking up faulty pool with key: '{}' (sniper={}, verb={}, route={})",
                                    resolvedFaultKey, faultRootApiKey != null, verb, route);
                            Map<PoolKey, InvalidInputPool> faultyPool = faultyParameterPools.get(resolvedFaultKey);

                            // Build the same (name, normalisedLocation) key the pool was enrolled under.
                            // Falling back to the test case's recorded location lets the writer-side
                            // route still find the right pool entry when the sniper picked a fault
                            // target that differs in location from this parameter's primary location.
                            String currentParamLocation = io.mist.core.workflow.pipeline.stages.StageSupport.normaliseParamLocation(p.getIn());
                            PoolKey lookupKey = new PoolKey(p.getName(), currentParamLocation);
                            if (faultyPool != null && !faultyPool.containsKey(lookupKey)
                                    && tc.getTargetFaultParamLocation() != null) {
                                PoolKey altKey = new PoolKey(p.getName(), tc.getTargetFaultParamLocation());
                                if (faultyPool.containsKey(altKey)) {
                                    lookupKey = altKey;
                                }
                            }

                            if (faultyPool != null && faultyPool.containsKey(lookupKey)) {
                                InvalidInputPool pool = faultyPool.get(lookupKey);
                                
                                // Get next invalid value based on mode
                                Object invalidValue;
                                if (faultyRoundRobin) {
                                    // Round-robin mode: replay the value captured at queue build time
                                    // (stored on the test case) rather than re-rotating pool state.
                                    // This guarantees the fired value matches the recorded type label.
                                    if (!tc.hasTargetFaultValue()) {
                                        log.warn("⚠️ Round-robin negative test for '{}' is missing a pre-recorded fault value — skipping.", p.getName());
                                        faultyValueSet = false;
                                    } else {
                                        invalidValue = tc.getTargetFaultValue();

                                        String invalidTypeName = tc.getFaultTypeCategory() != null
                                                ? tc.getFaultTypeCategory() : "Unknown";

                                        // For body/formData params keep the typed object (null, Integer,
                                        // Boolean …) so the JSON serializer emits the correct literal.
                                        // For path/query/header params a Java null would break URL
                                        // construction, so represent it as the literal string "null".
                                        if (p.getIn() != null && (p.getIn().equalsIgnoreCase("body") || p.getIn().equalsIgnoreCase("formData"))) {
                                            typedVal = invalidValue; // null → JSON null; Integer → JSON number; etc.
                                            typedValSet = true;
                                            val = (invalidValue == null) ? "null" : convertObjectToString(invalidValue, p.getType());
                                        } else {
                                            val = (invalidValue == null) ? "null" : convertObjectToString(invalidValue, p.getType());
                                        }
                                        tc.addFaultyParameter(p.getName(), val);
                                        faultyValueSet = true;
                                        String displayVal = val.length() > 50 ? val.substring(0, 50) + "..." : val;
                                        String javaType   = (invalidValue == null) ? "null" : invalidValue.getClass().getSimpleName();
                                        log.info("✅ Negative Test (Round-Robin) → {} = {} [InvalidType: {}] (javaType: {}) - LOCKED",
                                                p.getName(), displayVal, invalidTypeName, javaType);
                                    }
                            } else {
                                    // Random mode - can repeat
                                    invalidValue = pool.getRandomValue(random);
                                    if (invalidValue == null) {
                                        log.warn("⚠️ No invalid values in pool for '{}'", p.getName());
                                        faultyValueSet = false;
                                    } else {
                                        // 🔥 FIX: For TYPE_MISMATCH, preserve the actual type (Integer, Boolean, etc.)
                                        // For body/formData params, store in typedVal; for path/query/header, convert to string
                                        if (p.getIn() != null && (p.getIn().equalsIgnoreCase("body") || p.getIn().equalsIgnoreCase("formData"))) {
                                            typedVal = invalidValue; // Keep typed (Integer 123, not "123")
                                            typedValSet = true;
                                            val = convertObjectToString(invalidValue, p.getType()); // String for logging/tracking
                                        } else {
                                            // Path/query/header params must be strings (URL construction)
                                            val = convertObjectToString(invalidValue, p.getType());
                                        }
                                        tc.addFaultyParameter(p.getName(), val);
                                        faultyValueSet = true;
                                        log.info("✅ Negative Test (Random) → {} = {} (type: {}, intentionally invalid) - LOCKED", 
                                                p.getName(), 
                                                val.length() > 50 ? val.substring(0, 50) + "..." : val, 
                                                invalidValue.getClass().getSimpleName());
                                    }
                                }
                            } else {
                                log.warn("⚠️ No invalid value pool found for rootApiKey='{}' or pool key='{}'",
                                        resolvedFaultKey, lookupKey);
                            }
                        }

                        // CRITICAL: Skip pool/smart fetch for negative test parameters
                        boolean isTargetNegativeParam = isFaultyVariant && targetFaultyParams != null && targetFaultyParams.contains(p.getName());

                        // PRIMARY PATH: Use pre-built shared parameter pool with random selection
                        if (!faultyValueSet && !isTargetNegativeParam && val == null && typedVal == null) {
                            String currentRootApiKey = verb.toUpperCase() + "_" + route.replaceAll("[^a-zA-Z0-9_]", "_");
                            Map<String, List<String>> pool = sharedParameterPools.get(currentRootApiKey);
                            if (pool != null && pool.containsKey(p.getName())) {
                                List<String> poolVals = pool.get(p.getName());
                                // Phase 1: prefer SUT-verified values when available. If the
                                // registry has VERIFIED_VALID entries that overlap with the raw
                                // pool, draw from the overlap so Sniper's non-target params land
                                // on values the SUT has accepted before. Empty intersection or
                                // null registry falls back to raw pool with no behavior change.
                                // Key by the OpenAPI template path: the writer records
                                // VERIFIED_VALID observations under "<VERB> <testPath>"
                                // (template form). Span-derived routes can be concrete
                                // (/orders/df2b…), which would silently miss the registry.
                                String verifiedRoute = (opCfg.getTestPath() != null && !opCfg.getTestPath().isBlank())
                                        ? opCfg.getTestPath() : route;
                                List<String> candidates = preferVerifiedValues(poolVals, verb, verifiedRoute, p.getName());
                                if (!candidates.isEmpty()) {
                                    String poolValue = candidates.get(random.nextInt(candidates.size()));
                                    if (p.getIn() != null && (p.getIn().equalsIgnoreCase("body") || p.getIn().equalsIgnoreCase("formData"))) {
                                        typedVal = convertStringToTypedValue(poolValue, p);
                                        typedValSet = true;
                                        val = poolValue;
                                        log.info("Shared Pool (Step 1) → {} {} = {} (type: {}, pool size: {}) ✅",
                                                service, p.getName(), typedVal,
                                                typedVal != null ? typedVal.getClass().getSimpleName() : "null", poolVals.size());
                                    } else {
                                        val = poolValue;
                                        log.info("Shared Pool (Step 1) → {} {} = {} (pool size: {}) ✅",
                                                service, p.getName(), val, poolVals.size());
                                    }
                                }
                            }
                        }

                        // FALLBACK 1: Smart Input Fetching if pool miss
                        if (!faultyValueSet && !isTargetNegativeParam && val == null && !typedValSet
                                && smartFetcher != null && smartFetchConfig != null && smartFetchConfig.isEnabled()) {
                            log.info("Pool miss for '{}', falling back to smart fetch", p.getName());
                            try {
                                String smartFetchValue = smartFetcher.fetchSmartInput(info);
                                if (smartFetchValue != null && !smartFetchValue.trim().isEmpty()) {
                                    if (p.getIn() != null && (p.getIn().equalsIgnoreCase("body") || p.getIn().equalsIgnoreCase("formData"))) {
                                        typedVal = convertStringToTypedValue(smartFetchValue, p);
                                        typedValSet = true;
                                        val = smartFetchValue;
                                        log.info("Smart Fetch (Step 1) → {} {} = {} (type: {}) ✅",
                                                service, p.getName(), typedVal,
                                                typedVal != null ? typedVal.getClass().getSimpleName() : "null");
                                    } else {
                                        val = smartFetchValue;
                                        log.info("Smart Fetch (Step 1) → {} {} = {} ✅",
                                                service, p.getName(), val);
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("Smart fetching failed for step 1 {}.{}: {}",
                                         service, p.getName(), e.getMessage());
                            }
                        }

                        // FALLBACK 2: Direct LLM generation with random selection
                        if (!faultyValueSet && !isTargetNegativeParam && val == null && !typedValSet) {
                            List<String> vals = llmGen.generateParameterValues(info);
                            String llmValue;
                            if (vals.isEmpty()) {
                                llmValue = "LLM_EMPTY_" + p.getName();
                            } else {
                                llmValue = vals.get(random.nextInt(vals.size()));
                            }
                            if (p.getIn() != null && (p.getIn().equalsIgnoreCase("body") || p.getIn().equalsIgnoreCase("formData"))) {
                                typedVal = convertStringToTypedValue(llmValue, p);
                                typedValSet = true;
                                val = llmValue;
                                log.info("LLM (Step 1 Fallback) → {} {} = {} (type: {})",
                                        service, p.getName(), typedVal,
                                        typedVal != null ? typedVal.getClass().getSimpleName() : "null");
                            } else {
                                val = llmValue;
                                log.info("LLM (Step 1 Fallback) → {} {} = {}",
                                        service, p.getName(), val);
                            }
                        }

                        // FALLBACK 3: If negative test parameter failed, get a VALID value instead
                        if (isTargetNegativeParam && !faultyValueSet && val == null && !typedValSet) {
                            log.warn("⚠️ Negative test parameter '{}' failed to get invalid value. Getting valid value instead.", p.getName());
                            String currentRootApiKey = verb.toUpperCase() + "_" + route.replaceAll("[^a-zA-Z0-9_]", "_");
                            Map<String, List<String>> pool = sharedParameterPools.get(currentRootApiKey);
                            if (pool != null && pool.containsKey(p.getName())) {
                                List<String> poolVals = pool.get(p.getName());
                                if (!poolVals.isEmpty()) {
                                    String poolValue = poolVals.get(random.nextInt(poolVals.size()));
                                    if (p.getIn() != null && (p.getIn().equalsIgnoreCase("body") || p.getIn().equalsIgnoreCase("formData"))) {
                                        typedVal = convertStringToTypedValue(poolValue, p);
                                        typedValSet = true;
                                        val = poolValue;
                                    } else {
                                        val = poolValue;
                                    }
                                }
                            }
                            if (val == null && !typedValSet) {
                                List<String> vals = llmGen.generateParameterValues(info);
                                String llmValue = vals.isEmpty()
                                        ? typeAwareFallbackValue(p, variantIndex)
                                        : vals.get(random.nextInt(vals.size()));
                                if (p.getIn() != null && (p.getIn().equalsIgnoreCase("body") || p.getIn().equalsIgnoreCase("formData"))) {
                                    typedVal = convertStringToTypedValue(llmValue, p);
                                    typedValSet = true;
                                    val = llmValue;
                                } else {
                                    val = llmValue;
                                }
                            }
                        }
                    } else {
                        if (!faultyValueSet) {
                            log.info("🚫 LLM disabled for step 1 parameter '{}'", p.getName());
                            val = "STEP1_" + p.getName() + "_v" + variantIndex;
                        }
                    }
                } else {
                    /* Subsequent Steps (2+): Check dependencies first, then use smart fetch for independent parameters */

                    boolean isTargetNegativeParam = isFaultyVariant && targetFaultyParams != null && targetFaultyParams.contains(p.getName());

                    /* 3-SNIPER. Fault replay for targets on roots >= 2. The first-business-step
                     * injection branch never runs for them (tc already has steps by the time the
                     * targeted root is traversed), so without this replay the target parameter
                     * silently took a valid/placeholder value, addFaultyParameter was never
                     * called, and the whole variant was demoted to positive — losing the fault
                     * slot. Replays the exact value captured at queue build time, mirroring the
                     * first-step round-robin branch. */
                    if (isTargetNegativeParam && tc.hasTargetFaultValue()) {
                        Object invalidValue = tc.getTargetFaultValue();
                        if (p.getIn() != null && (p.getIn().equalsIgnoreCase("body") || p.getIn().equalsIgnoreCase("formData"))) {
                            typedVal = invalidValue; // null → JSON null; Integer → JSON number; etc.
                            typedValSet = true;
                        }
                        val = (invalidValue == null) ? "null" : convertObjectToString(invalidValue, p.getType());
                        tc.addFaultyParameter(p.getName(), val);
                        log.info("✅ Negative Test (subsequent root, step {}) → {} = {} [InvalidType: {}] - LOCKED",
                                stepNumber, p.getName(),
                                val.length() > 50 ? val.substring(0, 50) + "..." : val,
                                tc.getFaultTypeCategory() != null ? tc.getFaultTypeCategory() : "Unknown");
                    }

                    /* 3-PROV. Provenance-based resolution: use the exact value from a proven producer */
                    Map<String, String> provenance = span.getDataProvenance();
                    if (val == null && !provenance.isEmpty() && provenance.containsKey(p.getName())) {
                        val = provenance.get(p.getName());
                        log.info("Provenance → {} {} = {} (exact value from proven cross-trace producer, step {})",
                                service, p.getName(), val, stepNumber);
                    }

                    /* 3a. Use previously captured OUTPUT value from context (dependency) ------ */
                    if (val == null) {
                        val = context.get(p.getName());
                        if (val != null) {
                            log.info("Dependency (Output) → {} {} = {} (from previous step output, step {})",
                                    service, p.getName(), val, stepNumber);
                        }
                    }

                    /* 3b. Use previously captured INPUT value for consistency --------------- */
                    if (val == null) {
                        val = context.get("input." + p.getName());
                        if (val != null) {
                            log.info("Dependency (Input) → {} {} = {} (reusing from previous API input, step {})",
                                    service, p.getName(), val, stepNumber);
                        }
                    }

                    /* 3c. Use trace data if available and no context value ------------- */
                    // CRITICAL: Skip trace data for negative test parameters - they must use invalid values only
                    if (val == null && !isTargetNegativeParam) {
                        val = getTraceParameterValue(span, p.getName());
                        if (val != null) {
                            log.info("Trace Data → {} {} = {} (from trace, step {})",
                                    service, p.getName(), val, stepNumber);
                        }
                    }

                    /* 3d. Parameter is INDEPENDENT - use Smart Input Fetching or LLM */
                    // CRITICAL: Skip smart fetch for negative test parameters - they must use invalid values only
                    if (val == null && !isTargetNegativeParam && useLLM) {
                        log.info("Parameter '{}' is INDEPENDENT in step {} - generating new value", p.getName(), stepNumber);

                        // Create ParameterInfo with full API context for better LLM generation
                        ParameterInfo info = createParameterInfoWithContext(p, apiName, service, allParamNames);

                        // Enrich with trace-observed producer endpoints from the workflow tree
                        info.setTraceProducerEndpoints(collectProducerEndpoints(span));

                        // Try Smart Input Fetching first for independent parameters
                        if (smartFetcher != null && smartFetchConfig != null && smartFetchConfig.isEnabled()) {
                            try {
                                String smartFetchValue = smartFetcher.fetchSmartInput(info);
                                if (smartFetchValue != null && !smartFetchValue.trim().isEmpty()) {
                                    // Convert to proper type for body/formData params, keep as string for path/query/header
                                    if (p.getIn() != null && (p.getIn().equalsIgnoreCase("body") || p.getIn().equalsIgnoreCase("formData"))) {
                                        typedVal = convertStringToTypedValue(smartFetchValue, p);
                                        typedValSet = true;
                                        val = smartFetchValue;
                                        log.info("Smart Fetch (Independent) → {} {} = {} (type: {}) ✅ (step {})",
                                                service, p.getName(), typedVal,
                                                typedVal != null ? typedVal.getClass().getSimpleName() : "null", stepNumber);
                                    } else {
                                        val = smartFetchValue;
                                        log.info("Smart Fetch (Independent) → {} {} = {} ✅ (step {})",
                                                service, p.getName(), val, stepNumber);
                                    }
                                } else {
                                    val = null; // Ensure we fall back to LLM
                                }
                            } catch (Exception e) {
                                log.debug("Smart fetching failed for independent parameter {}.{}, falling back to LLM: {}",
                                         service, p.getName(), e.getMessage());
                                val = null; // Ensure we fall back to LLM
                            }
                        }

                        // Fall back to traditional LLM generation if smart fetching didn't work
                        if (val == null && !typedValSet) {
                            List<String> vals = llmGen.generateParameterValues(info);
                            String llmValue;
                            if (vals.isEmpty()) {
                                llmValue = "LLM_EMPTY";
                            } else {
                                llmValue = vals.get(random.nextInt(vals.size()));
                            }
                            if (p.getIn() != null && (p.getIn().equalsIgnoreCase("body") || p.getIn().equalsIgnoreCase("formData"))) {
                                typedVal = convertStringToTypedValue(llmValue, p);
                                typedValSet = true;
                                val = llmValue;
                                log.info("LLM (Independent Fallback) → {} {} = {} (type: {}) (step {})",
                                        service, p.getName(), typedVal,
                                        typedVal != null ? typedVal.getClass().getSimpleName() : "null", stepNumber);
                            } else {
                                val = llmValue;
                                log.info("LLM (Independent Fallback) → {} {} = {} (step {})",
                                        service, p.getName(), val, stepNumber);
                            }
                        }
                    }

                    /* 3e. Error handling for negative test parameters without invalid values */
                    if (val == null && isTargetNegativeParam) {
                        log.error("❌ CRITICAL: Negative test parameter '{}' in step {} has no invalid value. Skipping for negative testing.", 
                                p.getName(), stepNumber);
                        // DON'T add error fallback - let it remain null to trigger positive test conversion
                    }
                    
                    /* 3f. Ultimate fallback ---------------------------------------- */
                    if (val == null) val = "VAL_" + p.getName();
                }

                /* 3e. Store in correct container ----------------------------------- */
                // Skip parameters with null values (e.g., failed negative test params that will trigger test conversion)
                if (val == null && typedVal == null) {
                    log.warn("⚠️ Skipping parameter '{}' - no value available (will trigger test type conversion if needed)", p.getName());
                    continue;
                }
                
                // Use typedVal for body params (already converted), val for path/query/header/cookie (strings)
                switch (p.getIn().toLowerCase(Locale.ROOT)) {
                    case "path":
                        pathParams.put(p.getName(), val); // Path params must be strings for URL construction
                        if (val != null) {
                            // 🔥 FIX: URL-encode path parameter values for proper handling of whitespace and special characters
                            // This is critical for EMPTY_INPUT testing where values like " ", "\t", "\n" must be encoded
                            String encodedVal;
                            try {
                                encodedVal = java.net.URLEncoder.encode(val, java.nio.charset.StandardCharsets.UTF_8)
                                        .replace("+", "%20"); // URLEncoder uses + for space, but URL paths need %20
                            } catch (Exception e) {
                                log.warn("Failed to URL-encode path parameter '{}' value '{}': {}", p.getName(), val, e.getMessage());
                                encodedVal = val; // Fall back to original value
                            }
                            resolvedPath = resolvedPath.replace("{"+p.getName()+"}", encodedVal);
                            if (!val.equals(encodedVal)) {
                                log.debug("Path parameter '{}' URL-encoded: '{}' -> '{}'", p.getName(), val, encodedVal);
                            }
                        }
                        break;
                    case "query":
                        queryParams.put(p.getName(), val); // Query params must be strings for URL construction
                        break;
                    case "header":
                        headerParams.put(p.getName(), val); // Header params must be strings
                        break;
                    case "cookie":
                        // Cookie params were previously silently dropped (no switch case): the
                        // pool enrolled invalid values for them, but the writer had nothing to
                        // route. Storing them on cookieParams here completes the path so the
                        // writer's .cookie(name, value) emission has a source map.
                        cookieParams.put(p.getName(), val);
                        break;
                    case "body":
                    case "formdata":
                        // Use typedVal when it was explicitly assigned (including intentional null
                        // for NULL_INPUT body values, which must serialise as JSON null — not the
                        // string "null"). Fall back to the string val only when typedVal was
                        // never set (path/query/header-flow code did not produce a typed object).
                        Object bodyValue = typedValSet ? typedVal : val;
                        bodyFields.put(p.getName(), bodyValue); // Body fields can be typed objects
                        log.debug("Storing body parameter '{}' = {} (type: {})",
                                p.getName(), bodyValue, bodyValue != null ? bodyValue.getClass().getSimpleName() : "null");
                        break;
                }

                // Fix 3 Layer 1: tag synthetic-placeholder values so the writer's
                // resolution-aware classifier can reclassify the test as negative.
                // The eventual step index is tc.getSteps().size() — the StepCall
                // for this iteration is appended downstream at tc.addStepCall(call).
                io.mist.core.value.ValueProvenance inferred = io.mist.core.value.ValueProvenanceInference.infer(val);
                if (inferred != null) {
                    tc.recordParameterProvenance(tc.getSteps().size(), p.getName(), inferred);
                }
            }
        }

        // For first step, always use LLM-generated body fields, not trace body
        String rawBody = span.getInputFields().get("http.request.body");
        String bodyJson;
        
        if (isFirstBusinessStep) {
            // For login step and first business step, always use generated bodyFields
            bodyJson = bodyFields.isEmpty() ? null : generateRequestBody(bodyFields, opCfg);
            log.info("Using generated body for step {}: {}", stepNumber, bodyJson);
        } else {
            // For subsequent steps, prefer trace body, fallback to generated fields
            bodyJson = rawBody != null
                    ? rawBody
                    : (bodyFields.isEmpty() ? null : generateRequestBody(bodyFields, opCfg));
        }

        /* 4. Expected status from configuration (no hardcoding) ------------------- */
        int expectedStatus = 200; // Default fallback only
        
        // Priority 1: Read expected status from configuration file
        try {
            if (opCfg.getExpectedResponse() != null && !opCfg.getExpectedResponse().trim().isEmpty()) {
                expectedStatus = Integer.parseInt(opCfg.getExpectedResponse().trim());
                log.debug("Using configured expected status {} for {} {}", expectedStatus, verb, route);
            } else {
                log.warn("No expected status configured for {} {} - using default 200", verb, route);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid expected status '{}' in config for {} {} - using default 200", 
                    opCfg.getExpectedResponse(), verb, route);
        }

        // Priority 2: If configured status is still 200, check trace for actual successful status
        // Only use trace status if config doesn't specify a different expected value
        Object recorded = span.getOutputFields().get("http.status_code");
        if (recorded != null && expectedStatus == 200) {
            try {
                int traceStatus = Integer.parseInt(recorded.toString());
                if (traceStatus >= 200 && traceStatus < 300) {
                    expectedStatus = traceStatus;
                    log.debug("Using successful trace status {} for {} {}", traceStatus, verb, route);
                } else {
                    log.debug("Trace shows error status {}, keeping configured expected status {}", traceStatus, expectedStatus);
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid status code in trace: {}", recorded);
            }
        }

        /* 5. Create the StepCall with hierarchical naming -------------------------- */
        MultiServiceTestCase.StepCall call = new MultiServiceTestCase.StepCall(
                service,                      // serviceName
                opCfg,                        // Operation cfg
                resolvedPath,                 // resolved URI
                pathParams,
                queryParams,
                headerParams,
                bodyJson,
                expectedStatus,
                convertObjectMapToStringMap(bodyFields)
        );
        // Cookies are not in the StepCall constructor (kept stable for the existing 9-arg
        // form); populate them post-construction via the cookies map exposed by getCookies().
        if (cookieParams != null && !cookieParams.isEmpty()) {
            call.getCookies().putAll(cookieParams);
        }

        // Populate hierarchical naming metadata
        call.setHierarchicalId(stepNumber);
        call.setTopLevelRoot(isTopLevelRoot);
        if (span.isMergedRoot()) {
            call.setMergedRootStep(true);
            call.setProducerRootIndex(span.getProducerRootIndex());
            call.setDependencyType(MultiServiceTestCase.DependencyType.DATA_DEPENDENCY);
            span.getDataProvenance().forEach(call::addProvenanceBinding);
        }

        // Set step dependencies based on trace relationships
        setStepDependencies(call, span, tc.getSteps().size(), tc);

        // ── JIT Semantic Dependency Binding (Dynamic Multi-Candidate) ────
        // For Root N, scan backwards through Roots 0..N-1. Instead of a single
        // static producer binding, we retrieve ALL candidate producers for the
        // parameter's entity stem and match against the actual trace history.
        if (isTopLevelRoot && rootIndex > 1 && opCfg.getTestParameters() != null) {
            String consumerPath = opCfg.getTestPath();
            for (TestParameter tp : opCfg.getTestParameters()) {
                String pName = tp.getName();
                if (pName == null) continue;
                if (call.getParamDependencies().containsKey(pName)) continue;

                List<SemanticDependencyRegistry.ProducerBinding> candidates =
                        dependencyRegistry.getCandidateProducers(pName, consumerPath);
                if (candidates.isEmpty()) continue;

                // Build a fast lookup set from candidate API keys
                Map<String, SemanticDependencyRegistry.ProducerBinding> candidateMap = new LinkedHashMap<>();
                for (SemanticDependencyRegistry.ProducerBinding pb : candidates) {
                    candidateMap.putIfAbsent(pb.apiKey, pb);
                }

                // Scan previous root steps backward to find the nearest matching producer.
                // Canonicalize the prev-step API key (collapsing {templateName} AND concrete
                // /12345 or UUID segments to /{}) so lookup matches the canonical keys
                // stored in the registry.
                int matchedStepIndex = -1;
                SemanticDependencyRegistry.ProducerBinding matchedProducer = null;
                for (int si = tc.getSteps().size() - 1; si >= 0; si--) {
                    MultiServiceTestCase.StepCall prev = tc.getSteps().get(si);
                    if (!prev.isTopLevelRoot()) continue;
                    String prevMethod = prev.getMethod().getMethod() != null
                            ? prev.getMethod().getMethod().toLowerCase(Locale.ROOT) : "";
                    String prevPath = prev.getMethod().getTestPath() != null
                            ? prev.getMethod().getTestPath() : prev.getPath();
                    String prevApiKey = prevMethod + " " + SemanticDependencyRegistry.canonicalizePath(prevPath);
                    SemanticDependencyRegistry.ProducerBinding hit = candidateMap.get(prevApiKey);
                    if (hit != null) {
                        matchedStepIndex = si;
                        matchedProducer = hit;
                        break;
                    }
                }

                if (matchedStepIndex >= 0) {
                    // ── Trace-Driven JSON Path Resolution ──────────────────────
                    // Try to discover the exact jsonPath from the producer's real
                    // trace response body using provenance or DFS-by-stem.
                    String resolvedJsonPath = matchedProducer.jsonPath;  // registry default
                    MultiServiceTestCase.StepCall producerCall = tc.getSteps().get(matchedStepIndex);
                    String producerBody = producerCall.getTraceResponseBody();

                    if (producerBody != null && !producerBody.isBlank()) {
                        // Strategy 1: if we know the exact provenance value, search for it
                        String provenanceValue = span.getInputFields().get(pName);
                        if (provenanceValue == null) {
                            provenanceValue = span.getDataProvenance().values().stream().findFirst().orElse(null);
                        }
                        if (provenanceValue != null && !provenanceValue.isBlank()) {
                            String dynamicPath = SemanticDependencyRegistry.findJsonPathFromRealPayload(
                                    producerBody, provenanceValue);
                            if (dynamicPath != null) {
                                resolvedJsonPath = dynamicPath;
                                log.info("[Dynamic Path Finder] Resolved via value match: param '{}' → '{}'",
                                        pName, dynamicPath);
                            } else {
                                log.debug("[Dynamic Path Finder] Value '{}' not found in producer body — " +
                                        "falling back to registry path '{}'", provenanceValue, resolvedJsonPath);
                            }
                        }
                        // Strategy 2: if value match failed, search by ID field name pattern
                        if (resolvedJsonPath.equals(matchedProducer.jsonPath)
                                && matchedProducer.jsonPath.equals("data.id")) {
                            String stem = SemanticDependencyRegistry.normaliseIdStem(pName);
                            if (stem != null) {
                                String stemPath = SemanticDependencyRegistry.findJsonPathFromRealPayload(
                                        producerBody,
                                        null);  // pass null → will not match; use stem-based search instead
                                // Re-parse for stem-based field search
                                String trimmed = producerBody.trim();
                                if (trimmed.startsWith("{")) {
                                    try {
                                        org.json.JSONObject bodyObj = new org.json.JSONObject(trimmed);
                                        String bodyPath = findIdFieldInJsonObject(bodyObj, stem, "", 0);
                                        if (bodyPath != null) {
                                            resolvedJsonPath = bodyPath;
                                            log.info("[Dynamic Path Finder] Resolved via stem '{}': param '{}' → '{}'",
                                                    stem, pName, bodyPath);
                                        }
                                    } catch (Exception ignored) { }
                                }
                            }
                        }
                    } else {
                        log.debug("[Dynamic Path Finder] No trace response body for producer step {} — " +
                                "using registry path '{}'", matchedStepIndex, resolvedJsonPath);
                    }

                    call.addParamDependency(pName, matchedStepIndex, resolvedJsonPath);
                    // Compute type-safe fallback for resilient bypass
                    MultiServiceTestCase.Dependency dep = call.getParamDependencies().get(pName);
                    if (dep != null) {
                        dep.fallbackValue = generateTypeSafeFallback(tp);
                    }
                    call.setDependencyType(MultiServiceTestCase.DependencyType.DATA_DEPENDENCY);
                    jitDictionaryHits++;
                    log.info("JIT dependency wired: step {} param '{}' ← step {} ({}) jsonPath='{}' fallback='{}'",
                            stepNumber, pName, matchedStepIndex, matchedProducer.apiKey, resolvedJsonPath,
                            dep != null ? dep.fallbackValue : "N/A");
                } else {
                    jitFuzzingFallbacks++;
                    log.debug("No candidate producer for param '{}' in step {} found in preceding " +
                            "sequence ({} candidates registered) — falling back to smart fetch / LLM generation",
                            pName, stepNumber, candidates.size());
                }
            }
        }

        System.out.println(">> Step " + stepNumber + ": " + span.getServiceName() + " "
                + verb.toUpperCase() + " " + route
                + " body=" + call.getBody()
                + " expected=" + call.getExpectedStatus());
        
        /* Capture output fields for downstream steps */
        for (String key : span.getOutputFields().keySet()) {
            if (!key.startsWith("http.")) {
                call.addCaptureOutputKey(key);
            }
        }

        // Store the trace response body for downstream jsonPath extraction
        String traceBody = span.getOutputFields().get("http.response.body");
        if (traceBody != null && !traceBody.isBlank()) {
            call.setTraceResponseBody(traceBody);
        }

        tc.addStepCall(call);

        /* 6. Update context with outputs ------------------------------------------- */
        context.putAll(span.getOutputFields());
        
        /* 6b. Update context with inputs for consistency across subsequent steps --- */
        // Store all input parameters used in this step for consistency in future steps
        storeUsedInputsInContext(tc, context, pathParams, queryParams, headerParams, bodyFields);
        
        log.debug("Step {}: Stored {} input parameters and {} output fields in context for consistency", 
                stepNumber, 
                pathParams.size() + queryParams.size() + headerParams.size() + bodyFields.size(),
                span.getOutputFields().size());

        /* 7. Process children with hierarchical numbering -------------------------- */
        if (onlyFirstBusinessStep) {
            return;
        }
        gotoChildren(span, tc, context, stepNumber, rootIndex, variantIndex, isFaultyVariant, targetFaultyParams, faultRootApiKey);
    }

    /**
     * Helper method to create ParameterInfo from TestParameter with full API context
     */
    private ParameterInfo createParameterInfo(TestParameter p) {
        ParameterInfo info = new ParameterInfo();
        info.setName(p.getName());
        info.setDescription(p.getDescription());
        info.setInLocation(p.getIn());
        info.setType(p.getType());
        info.setFormat(p.getFormat());
        info.setSchemaType(p.getType());
        info.setSchemaExample(p.getExample() != null ? p.getExample().toString() : "");
        info.setRegex(p.getPattern());
        info.setRequired(p.getRequired());
        // OpenAPI constraint fields for prompt enrichment
        info.setEnumValues(p.getEnumValues());
        info.setMinimum(p.getMinimum());
        info.setMaximum(p.getMaximum());
        info.setMinLength(p.getMinLength());
        info.setMaxLength(p.getMaxLength());
        return info;
    }
    
    /**
     * Enhanced helper method to create ParameterInfo with full API context for better LLM generation
     */
    private ParameterInfo createParameterInfoWithContext(TestParameter p, String apiName, String serviceName, List<String> allParamNames) {
        ParameterInfo info = createParameterInfo(p);
        info.setApiName(apiName);
        info.setServiceName(serviceName);
        info.setAllParameterNames(allParamNames);
        return info;
    }

    /**
     * Helper method to convert Map<String,Object> to Map<String,String>
     */
    private Map<String,String> convertObjectMapToStringMap(Map<String,Object> objectMap) {
        Map<String,String> stringMap = new LinkedHashMap<>();
        for (Map.Entry<String,Object> entry : objectMap.entrySet()) {
            Object value = entry.getValue();
            // Handle null values (e.g., from faulty test cases)
            stringMap.put(entry.getKey(), value == null ? null : value.toString());
        }
        return stringMap;
    }

    /**
     * Extract parameter values from trace input/output fields.
     */
    private void extractParametersFromTrace(WorkflowStep span,
                                           Map<String,Object> bodyFields,
                                           Map<String,String> queryParams,
                                           Map<String,String> pathParams,
                                           Map<String,String> headerParams) {
        // Extract from request body
        String requestBody = span.getInputFields().get("http.request.body");
        if (requestBody != null && !requestBody.trim().isEmpty()) {
            try {
                // Try to parse JSON body
                org.json.JSONObject jsonBody = new org.json.JSONObject(requestBody);
                for (String key : jsonBody.keySet()) {
                    Object value = jsonBody.get(key);
                    bodyFields.put(key, value.toString());
                }
            } catch (Exception e) {
                // If not JSON, try form data parsing
                parseFormDataToObjectMap(requestBody, bodyFields);
            }
        }
        
        // Extract query parameters from URL
        String httpUrl = span.getInputFields().get("http.url");
        if (httpUrl != null && httpUrl.contains("?")) {
            String queryString = httpUrl.substring(httpUrl.indexOf("?") + 1);
            parseFormData(queryString, queryParams);
        }
    }

    /**
     * Get parameter value from trace data.
     */
    private String getTraceParameterValue(WorkflowStep span, String paramName) {
        // Check input fields first
        String value = span.getInputFields().get(paramName);
        if (value != null) return value;
        
        // Check output fields
        value = span.getOutputFields().get(paramName);
        if (value != null) return value;
        
        return null;
    }

    /**
     * Parse form data or query string into key-value pairs (string-typed values).
     * Delegates the decoding loop to {@link #decodeFormPairs(String, java.util.function.BiConsumer)}
     * so the {@link Map String,String} and {@link Map String,Object} variants stay in sync.
     */
    private void parseFormData(String data, Map<String,String> target) {
        decodeFormPairs(data, target::put);
    }

    /**
     * Parse form data or query string into key-value pairs (Object version).
     */
    private void parseFormDataToObjectMap(String data, Map<String,Object> target) {
        decodeFormPairs(data, (k, v) -> target.put(k, v));
    }

    /**
     * Decode {@code key=value&key=value} pairs into the supplied sink. Uses the modern
     * {@link java.nio.charset.StandardCharsets#UTF_8} overload (the {@code String} overload was
     * deprecated for removal in JDK 10) and keeps both form-data parsers in lockstep.
     */
    private static void decodeFormPairs(String data, java.util.function.BiConsumer<String,String> sink) {
        if (data == null || data.trim().isEmpty()) return;
        for (String pair : data.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length != 2) continue;
            String key, value;
            try {
                key = java.net.URLDecoder.decode(kv[0], java.nio.charset.StandardCharsets.UTF_8);
                value = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
            } catch (RuntimeException e) {
                key = kv[0];
                value = kv[1];
            }
            sink.accept(key, value);
        }
    }

    /**
     * Set step dependencies based on trace relationships.
     * This analyzes trace data to determine different types of dependencies:
     * 1. DATA_DEPENDENCY: Step needs output data from a previous step (skip if dependency fails)
     * 2. WORKFLOW_DEPENDENCY: Step is part of a logical sequence (skip if workflow predecessors fail)
     * 3. INDEPENDENT: Step can execute regardless of other step failures
     */
    private void setStepDependencies(MultiServiceTestCase.StepCall call, 
                                   WorkflowStep span, 
                                   int currentStepIndex,
                                   MultiServiceTestCase tc) {
        log.debug("Analyzing dependencies for step {}: {} {}", 
                currentStepIndex, span.getServiceName(), span.getOperationName());

        // Merged roots already have DATA_DEPENDENCY + producerRootIndex set by
        // the caller.  Wire them directly and skip generic analysis that would
        // overwrite the classification to INDEPENDENT.
        if (span.isMergedRoot() && span.getProducerRootIndex() > 0) {
            int producerStepIdx = span.getProducerRootIndex() - 1;
            if (producerStepIdx >= 0 && producerStepIdx < tc.getSteps().size()) {
                call.addWorkflowDependency(producerStepIdx);
            }
            log.debug("Merged root step {} wired to producer step {}", currentStepIndex, producerStepIdx);
            return;
        }

        List<MultiServiceTestCase.StepCall> previousSteps = tc.getSteps();
        analyzeDependencies(call, span, currentStepIndex, previousSteps);
    }
    
    /**
     * Analyze and categorize dependencies between the current step and previous steps
     */
    private void analyzeDependencies(MultiServiceTestCase.StepCall currentCall,
                                   WorkflowStep currentSpan,
                                   int currentStepIndex,
                                   List<MultiServiceTestCase.StepCall> previousSteps) {
        
        // Track what we find
        boolean hasDataDependency = false;
        boolean hasWorkflowDependency = false;
        
        for (int i = 0; i < previousSteps.size(); i++) {
            MultiServiceTestCase.StepCall previousCall = previousSteps.get(i);
            
            // Get the corresponding WorkflowStep for the previous call (if available)
            WorkflowStep previousSpan = findCorrespondingSpan(previousCall, currentSpan);
            
            if (previousSpan != null) {
                // Check for data dependencies (output -> input field matching)
                Map<String, String> dataMatches = findDataDependencies(previousSpan, currentSpan);
                if (!dataMatches.isEmpty()) {
                    hasDataDependency = true;
                    for (Map.Entry<String, String> match : dataMatches.entrySet()) {
                        currentCall.addParamDependency(match.getKey(), i + 1, match.getValue());
                        // Default string fallback for field-matched dependencies
                        MultiServiceTestCase.Dependency dep = currentCall.getParamDependencies().get(match.getKey());
                        if (dep != null && dep.fallbackValue == null) {
                            dep.fallbackValue = java.util.UUID.randomUUID().toString();
                        }
                        log.info("DATA_DEPENDENCY: Step {} param '{}' depends on Step {} output '{}'",
                                currentStepIndex, match.getKey(), i + 1, match.getValue());
                    }
                }
                
                // Check for workflow dependencies (parent-child relationships in trace)
                if (isWorkflowDependent(previousSpan, currentSpan)) {
                    hasWorkflowDependency = true;
                    currentCall.addWorkflowDependency(i + 1);
                    log.info("WORKFLOW_DEPENDENCY: Step {} depends on workflow Step {}",
                            currentStepIndex, i + 1);
                }
            }
            
            // Check for service-level dependencies (same service, likely sequential)
            if (isSameServiceDependency(previousCall, currentCall)) {
                hasWorkflowDependency = true;
                currentCall.addWorkflowDependency(i + 1);
                log.info("SERVICE_DEPENDENCY: Step {} (same service dependency) depends on Step {}",
                        currentStepIndex, i + 1);
            }
        }
        
        // Set the overall dependency type
        if (hasDataDependency) {
            currentCall.setDependencyType(MultiServiceTestCase.DependencyType.DATA_DEPENDENCY);
            log.info("Step {} classified as DATA_DEPENDENT", currentStepIndex);
        } else if (hasWorkflowDependency) {
            currentCall.setDependencyType(MultiServiceTestCase.DependencyType.WORKFLOW_DEPENDENCY);
            log.info("Step {} classified as WORKFLOW_DEPENDENT", currentStepIndex);
        } else {
            currentCall.setDependencyType(MultiServiceTestCase.DependencyType.INDEPENDENT);
            log.info("Step {} classified as INDEPENDENT", currentStepIndex);
        }
    }
    
    /**
     * Find data dependencies by matching output fields from previous step to input fields of current step
     */
    private Map<String, String> findDataDependencies(WorkflowStep previousSpan, WorkflowStep currentSpan) {
        Map<String, String> dependencies = new LinkedHashMap<>();
        
        // Fields to ignore: HTTP metadata, generic/ubiquitous keys
        Set<String> ignoreFields = Set.of(
                "http.status_code", "status_code", "timestamp", "value",
                "id", "type", "version", "success", "error", "message",
                "http.method", "http.url", "http.target", "http.path",
                "http.request.body", "http.response.body",
                "http.scheme", "http.route", "http.client_ip",
                "array", "body"
        );
        
        Map<String, String> previousOutputs = previousSpan.getOutputFields();
        Map<String, String> currentInputs = currentSpan.getInputFields();
        
        for (Map.Entry<String, String> output : previousOutputs.entrySet()) {
            String outputKey = output.getKey();
            String outputValue = output.getValue();
            
            // Skip ignored fields and empty values
            if (ignoreFields.contains(outputKey) || outputValue == null || outputValue.isEmpty()) {
                continue;
            }
            
            // Check if this output value appears in current step's inputs
            for (Map.Entry<String, String> input : currentInputs.entrySet()) {
                String inputKey = input.getKey();
                String inputValue = input.getValue();

                if (ignoreFields.contains(inputKey)) continue;

                if (inputValue != null && inputValue.equals(outputValue)) {
                    dependencies.put(inputKey, outputKey);
                    log.debug("Found data dependency: {} ({}) -> {} ({})", 
                            outputKey, outputValue, inputKey, inputValue);
                }
            }
        }
        
        return dependencies;
    }
    
    /**
     * Check if current step is workflow-dependent on previous step based on trace relationships
     */
    private boolean isWorkflowDependent(WorkflowStep previousSpan, WorkflowStep currentSpan) {
        // Check if currentSpan is a child of previousSpan in the trace hierarchy
        WorkflowStep parent = currentSpan.getParent();
        while (parent != null) {
            if (parent.equals(previousSpan)) {
                return true;
            }
            parent = parent.getParent();
        }
        
        // Check if they're in the same trace and sequential
        if (previousSpan.getTraceId().equals(currentSpan.getTraceId())) {
            // If in same trace and current starts after previous ends, it's likely workflow dependent
            return currentSpan.getStartTime() >= previousSpan.getEndTime();
        }
        
        return false;
    }
    
    /**
     * Check if steps are from the same service and likely sequential
     */
    private boolean isSameServiceDependency(MultiServiceTestCase.StepCall previousCall, 
                                          MultiServiceTestCase.StepCall currentCall) {
        // Steps from the same service are often workflow dependent
        return previousCall.getServiceName().equals(currentCall.getServiceName());
    }
    
    /**
     * DFS search in a parsed JSON object for an ID-like field matching the entity stem.
     * Used during JIT wiring to resolve JSON paths from real trace payloads.
     */
    private static String findIdFieldInJsonObject(org.json.JSONObject obj, String stem,
                                                  String currentPath, int depth) {
        if (obj == null || depth > 8) return null;
        for (String key : obj.keySet()) {
            String lower = key.toLowerCase(Locale.ROOT);
            String stemLower = stem.toLowerCase(Locale.ROOT);
            if ("id".equals(lower) || "uuid".equals(lower)
                    || lower.equals(stemLower + "id") || lower.equals(stemLower + "_id")) {
                return currentPath.isEmpty() ? key : currentPath + "." + key;
            }
        }
        for (String key : obj.keySet()) {
            Object val = obj.opt(key);
            String childPath = currentPath.isEmpty() ? key : currentPath + "." + key;
            if (val instanceof org.json.JSONObject) {
                String found = findIdFieldInJsonObject((org.json.JSONObject) val, stem, childPath, depth + 1);
                if (found != null) return found;
            } else if (val instanceof org.json.JSONArray) {
                org.json.JSONArray arr = (org.json.JSONArray) val;
                if (arr.length() > 0 && arr.get(0) instanceof org.json.JSONObject) {
                    String found = findIdFieldInJsonObject(arr.getJSONObject(0), stem,
                            childPath + "[0]", depth + 1);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    /**
     * Find the WorkflowStep that corresponds to a given StepCall
     */
    private WorkflowStep findCorrespondingSpan(MultiServiceTestCase.StepCall call, WorkflowStep contextSpan) {
        // This is a simplified implementation - in a full implementation,
        // you would maintain a mapping between StepCalls and WorkflowSteps
        // For now, we'll use the contextSpan's siblings and parents
        
        // Check if the call matches the current context span
        if (matchesStep(call, contextSpan)) {
            return contextSpan;
        }
        
        // Check siblings and ancestors
        WorkflowStep parent = contextSpan.getParent();
        if (parent != null) {
            for (WorkflowStep sibling : parent.getChildren()) {
                if (matchesStep(call, sibling)) {
                    return sibling;
                }
            }
        }
        
        return null; // Not found
    }
    
    /**
     * Check if a StepCall matches a WorkflowStep
     */
    private boolean matchesStep(MultiServiceTestCase.StepCall call, WorkflowStep span) {
        return call.getServiceName().equals(span.getServiceName()) &&
               call.getPath() != null && 
               span.getOperationName().contains(call.getPath().replaceFirst("^/+", ""));
    }
    
    /**
     * Store all input parameters used in this step in the context for consistency in subsequent steps.
     * This ensures that if the same parameter is needed again (e.g., loginId), we reuse the same value
     * instead of generating a new one, maintaining consistency across the test case.
     */
    private void storeUsedInputsInContext(MultiServiceTestCase tc,
                                         Map<String, String> context,
                                         Map<String, String> pathParams,
                                         Map<String, String> queryParams,
                                         Map<String, String> headerParams,
                                         Map<String, Object> bodyFields) {
        // Sniper contract: the injected fault value must reach exactly one request
        // slot. Without this exclusion, a same-named parameter on a later root
        // resolves via context.get("input.<name>") (step 3b) and re-fires the
        // fault into a non-target step.
        java.util.Set<String> faultyNames = new java.util.HashSet<>();
        for (String fp : tc.getFaultyParameters()) {
            faultyNames.add(fp.contains("=") ? fp.substring(0, fp.indexOf('=')) : fp);
        }

        // Store path parameters with "input." prefix for consistency tracking
        for (Map.Entry<String, String> entry : pathParams.entrySet()) {
            if (faultyNames.contains(entry.getKey())) continue;
            context.put("input." + entry.getKey(), entry.getValue());
        }

        // Store query parameters with "input." prefix for consistency tracking
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (faultyNames.contains(entry.getKey())) continue;
            context.put("input." + entry.getKey(), entry.getValue());
        }

        // Store header parameters with "input." prefix for consistency tracking
        for (Map.Entry<String, String> entry : headerParams.entrySet()) {
            if (faultyNames.contains(entry.getKey())) continue;
            context.put("input." + entry.getKey(), entry.getValue());
        }

        // Store body fields with "input." prefix for consistency tracking
        for (Map.Entry<String, Object> entry : bodyFields.entrySet()) {
            if (faultyNames.contains(entry.getKey())) continue;
            Object value = entry.getValue();
            // Handle null values (e.g., from faulty test cases)
            context.put("input." + entry.getKey(), value == null ? null : value.toString());
        }
    }

    /**
     * Walks up the WorkflowStep parent tree and collects HTTP paths from ancestor
     * and sibling steps that have output fields — these are endpoints that were
     * observed as producers in the original trace and are likely to provide
     * realistic values when fetched live.
     */
    private List<String> collectProducerEndpoints(WorkflowStep span) {
        List<String> endpoints = new ArrayList<>();
        WorkflowStep current = span.getParent();
        while (current != null) {
            String path = extractHttpPathFromStep(current);
            if (path != null && !endpoints.contains(path)) {
                endpoints.add(path);
            }
            for (WorkflowStep sibling : current.getChildren()) {
                if (sibling == span) continue;
                String siblingPath = extractHttpPathFromStep(sibling);
                if (siblingPath != null && !endpoints.contains(siblingPath)) {
                    endpoints.add(siblingPath);
                }
            }
            current = current.getParent();
        }
        return endpoints;
    }

    private String extractHttpPathFromStep(WorkflowStep step) {
        String target = step.getOutputFields().get("http.target");
        if (target != null) {
            int q = target.indexOf('?');
            return q >= 0 ? target.substring(0, q) : target;
        }
        String url = step.getOutputFields().get("http.url");
        if (url != null) return extractPathFromUrl(url);
        Matcher m = HTTP_OPERATION_PATTERN.matcher(step.getOperationName());
        if (m.matches()) return m.group(2);
        return null;
    }

    /**
     * Extract path from full URL.
     */
    private String extractPathFromUrl(String url) {
        if (url == null) return null;
        try {
            java.net.URL parsed = new java.net.URL(url);
            return parsed.getPath();
        } catch (Exception e) {
            // If URL parsing fails, try to extract path manually
            int pathStart = url.indexOf("://");
            if (pathStart >= 0) {
                int pathBegin = url.indexOf("/", pathStart + 3);
                if (pathBegin >= 0) {
                    int queryStart = url.indexOf("?", pathBegin);
                    return queryStart >= 0 ? url.substring(pathBegin, queryStart) : url.substring(pathBegin);
                }
            }
            return url;
        }
    }

    /** Detect login/auth related operations to skip as generator steps */
    private boolean isLoginOrAuthOperation(String service, String opName) {
        if (service == null && opName == null) return false;
        String s = service != null ? service.toLowerCase(Locale.ROOT) : "";
        String o = opName != null ? opName.toLowerCase(Locale.ROOT) : "";
        return s.contains("login") || s.contains("auth") || s.contains("signin") || s.contains("token")
                || o.contains("login") || o.contains("auth") || o.contains("signin") || o.contains("token");
    }

    /**
     * Is this span a gateway / transparent-proxy span (e.g. train-ticket
     * {@code ts-gateway-service}, Bookinfo {@code istio-ingressgateway}, or a
     * wildcard {@code "<VERB> /*"} routing op)? Mirrors the canonical predicate
     * used by {@code extractRootApiFromStep} / {@code findFirstBusinessStep}.
     * Gateway spans are routing-only: the designed behaviour is to propagate to
     * their children, NOT to build a StepCall or fire the endpoint fallback for
     * them (doing so changes train-ticket output).
     */
    private boolean isGatewayOperation(String service, String opName) {
        String s = service != null ? service.toLowerCase(Locale.ROOT) : "";
        if (s.contains("gateway")) return true;
        return "POST /*".equals(opName) || "GET /*".equals(opName)
                || "PUT /*".equals(opName) || "DELETE /*".equals(opName);
    }

    /**
     * Process children with hierarchical numbering.
     */
    private void gotoChildren(WorkflowStep parent,
                              MultiServiceTestCase tc,
                              Map<String,String> ctx,
                              String parentStepNumber,
                              int rootIndex,
                              int variantIndex,
                              boolean isFaultyVariant,
                              List<String> targetFaultyParams,
                              String faultRootApiKey) {
        gotoChildren(parent, tc, ctx, parentStepNumber, rootIndex,
                     false, variantIndex, isFaultyVariant,
                     targetFaultyParams, faultRootApiKey);
    }
    
    /**
     * Process children with hierarchical numbering, optionally propagating
     * the top-level root flag (used when a transparent/gateway span is skipped).
     *
     * Two edge-case rules when {@code childIsTopLevelRoot == true}:
     *  1. The first child that "consumes" the root status inherits the parent's
     *     exact step number (e.g. "R1"), not "R1.1", so downstream numbering
     *     and pruning remain consistent.
     *  2. Once a child (or any of its descendants) successfully emits a step,
     *     the root status is consumed. Subsequent siblings revert to normal
     *     nested behaviour ({@code isTopLevelRoot=false}, numbered "R1.2" etc.).
     */
    private void gotoChildren(WorkflowStep parent,
                              MultiServiceTestCase tc,
                              Map<String,String> ctx,
                              String parentStepNumber,
                              int rootIndex,
                              boolean childIsTopLevelRoot,
                              int variantIndex,
                              boolean isFaultyVariant,
                              List<String> targetFaultyParams,
                              String faultRootApiKey) {
        List<WorkflowStep> children = parent.getChildren();
        boolean rootConsumed = false;
        for (int i = 0; i < children.size(); i++) {
            boolean passAsRoot = childIsTopLevelRoot && !rootConsumed;
            
            // If propagating root status, keep the parent step number unchanged;
            // otherwise use normal hierarchical child numbering.
            String childStepNumber = passAsRoot
                    ? parentStepNumber
                    : parentStepNumber + "." + (i + 1);
            
            int stepsBefore = tc.getSteps().size();
            
            traverse(children.get(i), tc, ctx, childStepNumber,
                     rootIndex, passAsRoot,
                     variantIndex, isFaultyVariant, targetFaultyParams,
                     faultRootApiKey);
            
            // If any step was emitted in this subtree, the root status is consumed.
            if (passAsRoot && tc.getSteps().size() > stepsBefore) {
                rootConsumed = true;
            }
        }
    }
    
    /** Locate the corresponding Operation object by method + path. */
    private Operation findOperation(TestConfigurationObject cfg,
                                    String verb, String path) {

        if (cfg.getTestConfiguration() == null ||
                cfg.getTestConfiguration().getOperations() == null)
            return null;

        // Exact path match FIRST — train-ticket paths carry no path-params, so
        // they always match here and generation stays byte-identical.
        Operation exact = cfg.getTestConfiguration().getOperations().stream()
                .filter(o -> verb.equalsIgnoreCase(o.getMethod()) &&
                        path.equals(o.getTestPath()))
                .findFirst().orElse(null);
        if (exact != null) return exact;

        // No exact hit: accept a path-template match so a concrete trace path
        // (/products/0/ratings) matches the templated conf path
        // (/products/{id}/ratings). Shared single-source matcher in StageSupport.
        return cfg.getTestConfiguration().getOperations().stream()
                .filter(o -> verb.equalsIgnoreCase(o.getMethod()) &&
                        StageSupport.pathMatchesTemplate(path, o.getTestPath()))
                .findFirst().orElse(null);
    }

    /** Simple JSON builder for test bodies. Properly handles typed objects. */
    private static String toJson(Map<String,Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String,Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            
            // Handle null values (e.g., from faulty test cases)
            Object value = e.getValue();
            if (value == null) {
                sb.append("null");
            } else {
                // Properly serialize typed objects - don't wrap numbers/booleans in quotes
                sb.append(serializeJsonValue(value));
            }
        }
        return sb.append('}').toString();
    }
    
    /**
     * Serialize a value to JSON, preserving types (numbers, booleans, lists, etc.)
     * This ensures that Integer(123) becomes 123, not "123" in JSON
     */
    private static String serializeJsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        
        // Handle numbers (Integer, Long, Double, Float, etc.)
        if (value instanceof Number) {
            return value.toString();
        }
        
        // Handle booleans
        if (value instanceof Boolean) {
            return value.toString();
        }
        
        // Handle lists/arrays
        if (value instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) value;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                sb.append(serializeJsonValue(item));
            }
            sb.append("]");
            return sb.toString();
        }
        
        // Handle maps (nested objects)
        if (value instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) value;
            return toJson(map);
        }
        
        // Handle strings - escape and quote (RFC 8259 §7: every control char < U+0020 must be escaped)
        return '"' + escapeJsonStringStatic(value.toString()) + '"';
    }

    /**
     * RFC 8259 section 7-conforming string escape.
     * Handles backslash, double-quote, newline (n), carriage-return (r), tab (t), backspace (b),
     * form-feed (f), and every other code point in U+0000..U+001F via the six-char form
     * (backslash + u + four hex digits). Without this, SPECIAL_CHARACTERS
     * payloads that contain raw control bytes (NUL, SOH, STX, ...) are emitted as-is,
     * which some JSON parsers reject outright and others silently truncate at the first NUL.
     */
    static String escapeJsonStringStatic(String str) {
        if (str == null || str.isEmpty()) return str == null ? "" : "";
        StringBuilder sb = new StringBuilder(str.length() + 8);
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Get the first API operation name from the scenario (excluding login/pre-calls)
     * This is used for meaningful test naming based on the actual business API being tested
     */
    private String getFirstApiOperationName(WorkflowScenario scenario) {
        // Traverse through root steps to find the first non-login API operation
        for (WorkflowStep rootStep : scenario.getRootSteps()) {
            String apiName = findFirstBusinessApiOperation(rootStep);
            if (apiName != null) {
                return apiName;
            }
        }
        return null;
    }
    
    /**
     * Get HTTP method of the first business API operation
     */
    private String getFirstApiHttpMethod(WorkflowScenario scenario) {
        for (WorkflowStep rootStep : scenario.getRootSteps()) {
            String httpMethod = findFirstBusinessApiHttpMethod(rootStep);
            if (httpMethod != null) {
                return httpMethod;
            }
        }
        return "UNKNOWN";
    }
    
    /**
     * Recursively search for HTTP method of the first business API (not login/auth)
     */
    private String findFirstBusinessApiHttpMethod(WorkflowStep step) {
        String opName = step.getOperationName();
        String serviceName = step.getServiceName();
        
        // Skip login/auth related operations
        if (opName != null && serviceName != null) {
            String opLower = opName.toLowerCase();
            String serviceLower = serviceName.toLowerCase();
            
            if (opLower.contains("login") || opLower.contains("auth") || 
                serviceLower.contains("login") || serviceLower.contains("auth") ||
                opLower.contains("signin") || opLower.contains("token")) {
                // Skip login/auth, continue to children
            } else {
                // Extract HTTP method from operation name
                Matcher httpMatcher = HTTP_OPERATION_PATTERN.matcher(opName);
                if (httpMatcher.matches()) {
                    return httpMatcher.group(1).toUpperCase();
                }
                
                // Try service-prefixed format
                Pattern servicePattern = Pattern.compile(".*?\\s+(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+.+$", Pattern.CASE_INSENSITIVE);
                Matcher serviceMatcher = servicePattern.matcher(opName);
                if (serviceMatcher.matches()) {
                    return serviceMatcher.group(1).toUpperCase();
                }
            }
        }
        
        // Recursively search children
        for (WorkflowStep child : step.getChildren()) {
            String result = findFirstBusinessApiHttpMethod(child);
            if (result != null) {
                return result;
            }
        }
        
        return null;
    }
    
    /**
     * Recursively search for the first business API operation (not login/auth related)
     */
    private String findFirstBusinessApiOperation(WorkflowStep step) {
        String opName = step.getOperationName();
        String serviceName = step.getServiceName();
        

        
        // Skip login/auth related operations (case-insensitive)
        if (opName != null && serviceName != null) {
            String opLower = opName.toLowerCase();
            String serviceLower = serviceName.toLowerCase();
            
            // Skip common login/auth patterns
            if (opLower.contains("login") || opLower.contains("auth") || 
                serviceLower.contains("login") || serviceLower.contains("auth") ||
                opLower.contains("signin") || opLower.contains("token")) {
                // This is likely a login/auth operation, skip it
            } else {
                // This looks like a business API operation
                // Try to extract HTTP method and path for better naming
                String verb = null, route = null;
                
                // Check if it's an HTTP operation pattern (e.g., "POST /api/v1/path")
                Matcher httpMatcher = HTTP_OPERATION_PATTERN.matcher(opName);
                if (httpMatcher.matches()) {
                    verb = httpMatcher.group(1).toLowerCase(Locale.ROOT);
                    route = httpMatcher.group(2);
                } else {
                    // Try to extract from service-prefixed format (e.g., "ts-service POST /api/v1/path")
                    Pattern servicePattern = Pattern.compile(".*?\\s+(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
                    Matcher serviceMatcher = servicePattern.matcher(opName);
                    if (serviceMatcher.matches()) {
                        verb = serviceMatcher.group(1).toLowerCase(Locale.ROOT);
                        route = serviceMatcher.group(2);
                    } else {
                        // For simple HTTP method operations, use the operation name as method
                        // and try to extract the path from the service context
                        if (opName.matches("^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)$")) {
                            verb = opName.toLowerCase(Locale.ROOT);
                            
                            // Try to get HTTP target from trace data
                            Map<String, String> outputs = step.getOutputFields();
                            Map<String, String> inputs = step.getInputFields();
                            
                            String httpTarget = outputs.get("http.target");
                            String httpUrl = outputs.get("http.url");
                            
                            // Also check input fields as fallback
                            if (httpTarget == null) httpTarget = inputs.get("http.target");
                            if (httpUrl == null) httpUrl = inputs.get("http.url");
                            
                            if (httpTarget != null) {
                                route = httpTarget;
                            } else if (httpUrl != null) {
                                route = extractPathFromUrl(httpUrl);
                            } else {
                                // Use service name as fallback to create a meaningful route
                                route = "/" + serviceName;
                            }
                        } else {
                            // Check if we can extract from attributes/tags for other formats
                            Map<String, String> outputs = step.getOutputFields();
                            Map<String, String> inputs = step.getInputFields();
                            
                            String httpMethod = outputs.get("http.method");
                            String httpTarget = outputs.get("http.target");
                            String httpUrl = outputs.get("http.url");
                            
                            // Also check input fields as fallback
                            if (httpMethod == null) httpMethod = inputs.get("http.method");
                            if (httpTarget == null) httpTarget = inputs.get("http.target");
                            if (httpUrl == null) httpUrl = inputs.get("http.url");
                            
                            if (httpMethod != null && (httpTarget != null || httpUrl != null)) {
                                verb = httpMethod.toLowerCase(Locale.ROOT);
                                route = httpTarget != null ? httpTarget : extractPathFromUrl(httpUrl);
                            } else if (httpMethod != null) {
                                // If we only have the method, try to construct a meaningful name
                                verb = httpMethod.toLowerCase(Locale.ROOT);
                                route = "/" + serviceName;
                            }
                        }
                    }
                }
                
                // Return a descriptive name if we can extract method and path
                if (verb != null && route != null) {
                    String descriptiveName = verb.toUpperCase() + "_" + route.replaceAll("[^a-zA-Z0-9_]", "_");
                    return descriptiveName;
                } else {
                    // Fallback to original operation name
                return opName;
                }
            }
        }
        
        // Check children recursively
        for (WorkflowStep child : step.getChildren()) {
            String apiName = findFirstBusinessApiOperation(child);
            if (apiName != null) {
                return apiName;
            }
        }
        
        return null;
    }

    /**
     * Recursively find the first business API operation and return method_path key
     */
    private String extractRootApiFromStep(WorkflowStep step) {
        String opName = step.getOperationName();
        String serviceName = step.getServiceName();
        
        // Skip login/auth AND gateway operations
        if (opName != null && serviceName != null) {
            String opLower = opName.toLowerCase();
            String serviceLower = serviceName.toLowerCase();
            
            // Skip login/auth
            boolean isLoginAuth = opLower.contains("login") || opLower.contains("auth") || 
                                  serviceLower.contains("login") || serviceLower.contains("auth") ||
                                  opLower.contains("signin") || opLower.contains("token");
            
            // Skip gateway services (they're just proxies, not business services)
            boolean isGateway = serviceLower.contains("gateway") || 
                               opName.equals("POST /*") || opName.equals("GET /*") ||
                               opName.equals("PUT /*") || opName.equals("DELETE /*");
            
            if (!isLoginAuth && !isGateway) {
                // Try to extract HTTP method and path
                String verb = null, route = null;
                
                // Check various operation name formats
                Matcher httpMatcher = HTTP_OPERATION_PATTERN.matcher(opName);
                if (httpMatcher.matches()) {
                    verb = httpMatcher.group(1).toLowerCase();
                    route = httpMatcher.group(2);
                } else {
                    // Try service-prefixed format
                    Pattern servicePattern = Pattern.compile(".*?\\s+(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
                    Matcher serviceMatcher = servicePattern.matcher(opName);
                    if (serviceMatcher.matches()) {
                        verb = serviceMatcher.group(1).toLowerCase();
                        route = serviceMatcher.group(2);
                    } else {
                        // Try extracting from trace data
                        Map<String, String> outputs = step.getOutputFields();
                        String httpMethod = outputs.get("http.method");
                        String httpTarget = outputs.get("http.target");
                        String httpUrl = outputs.get("http.url");
                        
                        if (httpMethod != null && (httpTarget != null || httpUrl != null)) {
                            verb = httpMethod.toLowerCase();
                            route = httpTarget != null ? httpTarget : extractPathFromUrl(httpUrl);
                        }
                    }
                }
                
                if (verb != null && route != null) {
                    // Normalize the route by replacing non-alphanumeric characters with underscores
                    String normalizedRoute = route.replaceAll("[^a-zA-Z0-9_]", "_");
                    return verb.toUpperCase() + "_" + normalizedRoute;
                }
            }
        }
        
        // Check children recursively
        for (WorkflowStep child : step.getChildren()) {
            String apiKey = extractRootApiFromStep(child);
            if (apiKey != null) {
                return apiKey;
            }
        }
        
        return null;
    }

    /**
     * Produce a padding value for the shared pool that is parseable as the parameter's
     * declared type. The previous always-string {@code FALLBACK_<name>_<i>} pad worked
     * for string params but produced 'Failed to convert' warnings (and string-typed body
     * slots) for every numeric / boolean / array / object parameter that hit padding.
     */
    private static String typeAwareFallbackValue(TestParameter p, int idx) {
        String name = p != null && p.getName() != null ? p.getName() : "param";
        String type = p != null && p.getType() != null
                ? p.getType().toLowerCase(java.util.Locale.ROOT) : "string";
        switch (type) {
            case "integer":
            case "int":
            case "int32":
            case "int64":
            case "long":
                return Integer.toString(idx);
            case "number":
            case "double":
            case "float":
                return idx + ".0";
            case "boolean":
            case "bool":
                return (idx % 2 == 0) ? "false" : "true";
            case "array":
                return "[]";
            case "object":
                return "{}";
            case "string":
            default:
                return "FALLBACK_" + name + "_" + idx;
        }
    }

    /**
     * Build a deterministic fingerprint of all parameter values across all steps
     * in a test case, used for global payload deduplication.
     */
    private String buildPayloadFingerprint(MultiServiceTestCase tc) {
        StringBuilder sb = new StringBuilder();
        for (MultiServiceTestCase.StepCall step : tc.getSteps()) {
            sb.append(step.getMethod()).append('|').append(step.getPath()).append('|');
            if (step.getBody() != null) {
                sb.append(step.getBody());
            }
            if (step.getQueryParams() != null) {
                new TreeMap<>(step.getQueryParams()).forEach((k, v) -> sb.append(k).append('=').append(v).append('&'));
            }
            if (step.getPathParams() != null) {
                new TreeMap<>(step.getPathParams()).forEach((k, v) -> sb.append(k).append('=').append(v).append('/'));
            }
            sb.append("##");
        }
        return sb.toString();
    }


    /**
     * Convert Object (Integer, Boolean, null, etc.) to String for API parameter value
     * Handles type mismatches properly - preserves type information in string form
     */
    private String convertObjectToString(Object value, String paramType) {
        if (value == null) {
            return null;
        }
        
        // If the value is already a String, return as-is
        if (value instanceof String) {
            return (String) value;
        }
        
        // For type mismatches, we want to preserve the wrong type
        // E.g., if parameter expects String but we have Integer, keep it as integer representation
        // The REST Assured serialization will handle this correctly in JSON
        
        if (value instanceof Integer || value instanceof Long) {
            return value.toString(); // "123" but will be serialized as number in JSON
        }
        
        if (value instanceof Boolean) {
            return value.toString(); // "true" but will be serialized as boolean in JSON
        }
        
        if (value instanceof Double || value instanceof Float) {
            return value.toString(); // "12.34" but will be serialized as number in JSON
        }
        
        // For arrays and objects (represented as JSON strings)
        return value.toString();
    }

    /**
     * Convert string values from LLM/Smart Fetch/Word2Vec to proper typed objects for positive tests
     * This ensures that parameter types match OpenAPI schema requirements.
     * 
     * @param stringValue The string value from generator
     * @param param The parameter with type information
     * @return Properly typed object (Integer, Boolean, List, etc.) or string if conversion fails
     */
    private Object convertStringToTypedValue(String stringValue, TestParameter param) {
        if (stringValue == null) {
            return null;
        }
        
        String type = param.getType();
        String format = param.getFormat();
        
        if (type == null) {
            return stringValue; // No type info, keep as string
        }
        
        try {
            switch (type.toLowerCase()) {
                case "integer":
                    // Handle formats: int32, int64
                    if ("int64".equals(format)) {
                        return Long.parseLong(stringValue.trim());
                    } else {
                        return Integer.parseInt(stringValue.trim());
                    }
                    
                case "number":
                    // Handle formats: float, double
                    if ("float".equals(format)) {
                        return Float.parseFloat(stringValue.trim());
                    } else {
                        return Double.parseDouble(stringValue.trim());
                    }
                    
                case "boolean":
                    return Boolean.parseBoolean(stringValue.trim());
                    
                case "array":
                    // Parse array from string
                    return parseArrayValue(stringValue, param);
                    
                case "object":
                    // Keep as string - will be handled by JSON serialization
                    return stringValue;
                    
                case "string":
                default:
                    // Keep as string
                    return stringValue;
            }
        } catch (IllegalArgumentException e) {
            // Catches NumberFormatException (subclass) and other IllegalArgumentExceptions
            log.warn("Failed to convert value '{}' to type '{}' for parameter '{}': {}. Keeping as string.", 
                    stringValue, type, param.getName(), e.getMessage());
            return stringValue; // Fallback to string if conversion fails
        }
    }
    
    /**
     * Parse array value from string representation
     * Supports: "[1,2,3]", "1,2,3", "value1, value2, value3"
     */
    private Object parseArrayValue(String stringValue, TestParameter param) {
        if (stringValue == null || stringValue.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }
        
        String trimmed = stringValue.trim();
        
        // Remove brackets if present
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        
        if (trimmed.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        
        // Split by comma
        String[] items = trimmed.split(",");
        java.util.List<Object> result = new java.util.ArrayList<>();
        
        // Try to determine item type from format or example
        String itemType = inferArrayItemType(param);
        
        for (String item : items) {
            String cleanItem = item.trim();
            if (cleanItem.isEmpty()) {
                continue;
            }
            
            // Remove quotes if present
            if (cleanItem.startsWith("\"") && cleanItem.endsWith("\"")) {
                cleanItem = cleanItem.substring(1, cleanItem.length() - 1);
            }
            
            // Convert based on item type
            try {
                switch (itemType) {
                    case "integer":
                        result.add(Integer.parseInt(cleanItem));
                        break;
                    case "number":
                        result.add(Double.parseDouble(cleanItem));
                        break;
                    case "boolean":
                        result.add(Boolean.parseBoolean(cleanItem));
                        break;
                    default:
                        result.add(cleanItem); // Keep as string
                }
            } catch (NumberFormatException e) {
                log.debug("Could not parse array item '{}' as {}, keeping as string", cleanItem, itemType);
                result.add(cleanItem);
            }
        }
        
        return result;
    }
    
    /**
     * Infer array item type from parameter metadata
     */
    private String inferArrayItemType(TestParameter param) {
        // Check if example gives us a hint
        if (param.getExample() != null) {
            Object example = param.getExample();
            if (example instanceof java.util.List && !((java.util.List<?>) example).isEmpty()) {
                Object firstItem = ((java.util.List<?>) example).get(0);
                if (firstItem instanceof Integer || firstItem instanceof Long) {
                    return "integer";
                } else if (firstItem instanceof Double || firstItem instanceof Float) {
                    return "number";
                } else if (firstItem instanceof Boolean) {
                    return "boolean";
                }
            }
        }
        
        // Check format for hints
        String format = param.getFormat();
        if (format != null) {
            if (format.contains("int")) return "integer";
            if (format.contains("double") || format.contains("float")) return "number";
            if (format.contains("bool")) return "boolean";
        }
        
        // Default to string
        return "string";
    }

    /**
     * Fast pre-filter: returns true iff the scenario has at least one root step
     * whose subtree contains a span whose service name is in {@code serviceConfigs}.
     *
     * <p><b>Necessary, not sufficient.</b> traverse() additionally requires the
     * span to be an HTTP entry, not a login/auth op, and have a non-null
     * {@code findOperation} result. A scenario that passes this filter can still
     * yield 0 steps for those reasons — the in-loop {@code zeroStepStreak} guard
     * below catches that residual case. The cheap filter here just kills the
     * obvious gateway-only / unconfigured-service scenarios early so we don't
     * burn 100+ variants on them.</p>
     */

    /**
     * Find a key in faultyParameterPools whose templated path matches the literal-path
     * rootApiKey. The pool gen normalises path params to {param} while scenario rootApiKeys
     * may carry literal trace values. Without this fallback, no negative variants fire for
     * path-parameterised endpoints like DELETE /admintravel/{tripId}.
     */
    private String findPoolKeyByTemplateMatch(String literalKey) {
        if (literalKey == null) return null;
        for (String poolKey : faultyParameterPools.keySet()) {
            if (poolKey.equals(literalKey)) return poolKey;
            String poolKeyAsLiteralRegex = poolKey.replaceAll("__[a-zA-Z]+_$", "_[^_]+");
            if (literalKey.matches(poolKeyAsLiteralRegex)) return poolKey;
        }
        return null;
    }

    /**
     * Resolve an {@link EndpointPolicy} for {@code scenario}. The policy is
     * derived from the scenario's first root step (method + path) and any
     * OpenAPI {@code x-mist-*} extensions on the matching Operation. When
     * {@code mst.adaptive.enabled=false} returns {@link EndpointPolicy#LEGACY}
     * to preserve byte-identical pre-adaptive behaviour.
     */
    private EndpointPolicy resolveEndpointPolicy(WorkflowScenario sc) {
        MstConfig cfg = MstConfig.instance();
        if (!cfg.adaptive().enabled()) {
            return EndpointPolicy.LEGACY;
        }
        for (WorkflowStep root : sc.getRootSteps()) {
            String key = extractRootApiFromStep(root);
            if (key == null) continue;
            int sep = key.indexOf("__");
            String verb = (sep > 0) ? key.substring(0, sep) : "GET";
            return decideEndpointPolicy(true, verb,
                    cfg.adaptive().kDedupExhaustedDefault(),
                    cfg.adaptive().kZeroStepDefault());
        }
        return EndpointPolicy.LEGACY;
    }

    /**
     * Test seam for the pure-function policy decision: given the adaptive
     * toggle and an HTTP verb, return the policy. When {@code adaptiveEnabled}
     * is false (or verb is null) returns {@link EndpointPolicy#LEGACY} so the
     * variant loop runs byte-identical to its pre-adaptive form. Otherwise
     * delegates to {@link EndpointPolicyResolver}. Hoisted out of the
     * instance method above so the byte-identical contract is unit-testable
     * without constructing a {@link WorkflowScenario}.
     */
    static EndpointPolicy decideEndpointPolicy(boolean adaptiveEnabled,
                                                String verb,
                                                int kDedupDefault,
                                                int kZeroStepDefault) {
        if (!adaptiveEnabled) return EndpointPolicy.LEGACY;
        if (verb == null)     return EndpointPolicy.LEGACY;
        return new EndpointPolicyResolver(kDedupDefault, kZeroStepDefault)
                .resolve(verb, null, null);
    }

    private boolean scenarioHasBuildableRoot(WorkflowScenario scenario) {
        for (WorkflowStep root : scenario.getRootSteps()) {
            // (1) Exact service-name match anywhere in the subtree (unchanged —
            //     train-ticket always lands here, so it is byte-identical).
            if (subtreeHasConfiguredService(root)) {
                return true;
            }
            // (2) Endpoint fallback, gated to the ROOT / first-business step only:
            //     a SUT whose trace service names differ from the conf keys
            //     (Bookinfo: "productpage.default" vs conf "productpage") is still
            //     buildable if the root's HTTP method+path resolves to a conf op.
            WorkflowStep businessRoot = StageSupport.findFirstBusinessStepRecursive(root);
            if (businessRoot != null && rootStepResolvesByEndpoint(businessRoot)) {
                return true;
            }
        }
        return false;
    }

    private boolean subtreeHasConfiguredService(WorkflowStep step) {
        String service = step.getServiceName();
        if (service != null && serviceConfigs.containsKey(service)) {
            return true;
        }
        for (WorkflowStep child : step.getChildren()) {
            if (subtreeHasConfiguredService(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Does this (root) business step resolve to a conf operation via the
     * service-name-first / endpoint-fallback {@link StageSupport#resolveOperation}?
     * Used only for the root step in {@link #scenarioHasBuildableRoot} so we do
     * NOT mark a scenario buildable on the strength of a deep descendant.
     */
    private boolean rootStepResolvesByEndpoint(WorkflowStep step) {
        String service = step.getServiceName();
        String verb = null, route = null;

        Matcher m = HTTP_OPERATION_PATTERN.matcher(step.getOperationName());
        if (m.matches()) {
            verb = m.group(1).toLowerCase(Locale.ROOT);
            route = m.group(2);
        } else {
            Map<String, String> outputs = step.getOutputFields();
            String httpMethod = outputs.get("http.method");
            String httpTarget = outputs.get("http.target");
            String httpUrl = outputs.get("http.url");
            if (httpMethod != null && (httpTarget != null || httpUrl != null)) {
                verb = httpMethod.toLowerCase(Locale.ROOT);
                route = httpTarget != null ? httpTarget : extractPathFromUrl(httpUrl);
            }
        }
        if (verb == null || route == null) return false;

        return StageSupport.resolveOperation(serviceConfigs, service, verb, route) != null;
    }

    /**
     * Extract API name from a processed step call (guaranteed to have valid HTTP method/path)
     */
    private String extractApiNameFromStep(MultiServiceTestCase.StepCall step) {
        if (step == null) return null;
        
        String method = step.getMethod() != null && step.getMethod().getMethod() != null 
                        ? step.getMethod().getMethod().toUpperCase() 
                        : "GET";
        String path = step.getPath();
        
        if (path != null && !path.isEmpty()) {
            // Create a clean file-safe name from method and path
            String apiName = method + "_" + path.replaceAll("[^a-zA-Z0-9_]", "_")
                                              .replaceAll("_+", "_")
                                              .replaceAll("^_|_$", "");
            
            // Windows path limit is 260 chars. Account for:
            // - Base path (e.g., "src/test/java/trainticket_twostage_test/")
            // - Timestamp (e.g., "TrainTicketTwoStageTest_1762921209504/")
            // - Test variant suffix (e.g., "_123")
            // - File extension (".java")
            // Safe limit for class name: ~100 characters
            final int MAX_CLASS_NAME_LENGTH = 100;
            
            if (apiName.length() > MAX_CLASS_NAME_LENGTH) {
                // Truncate and add hash to maintain uniqueness
                String truncated = apiName.substring(0, MAX_CLASS_NAME_LENGTH - 9); // Leave room for hash
                int hash = apiName.hashCode();
                // Use positive hash value for consistency
                String hashSuffix = String.format("_%08X", hash & 0xFFFFFFFF);
                apiName = truncated + hashSuffix;
                
                log.warn("⚠️  API name truncated due to length: {} -> {} (original length: {})", 
                         path, apiName, apiName.length() + (apiName.length() - MAX_CLASS_NAME_LENGTH));
            }
            
            return apiName;
        }
        
        return null;
    }

    /**
     * Read variant count from properties file with fallback to defaults
     */
    private int getVariantCountFromProperties() {
        try {
            // Try testsperoperation first (this is the main property for test count)
            String testsProp = System.getProperty("testsperoperation");
            if (testsProp != null) {
                int count = Integer.parseInt(testsProp);
                log.info("✅ Using testsperoperation from properties: {}", count);
                return count;
            }
            
            // Try test.variants.per.scenario as fallback
            String variantsProp = System.getProperty("test.variants.per.scenario");
            if (variantsProp != null) {
                int count = Integer.parseInt(variantsProp);
                log.info("✅ Using test.variants.per.scenario from properties: {}", count);
                return count;
            }
            
            // Default behavior
            int defaultCount = 1;
            log.warn("❌ No variant count found in properties, using default: {}", defaultCount);
            return defaultCount;
        } catch (NumberFormatException e) {
            int defaultCount = 1;
            log.warn("❌ Invalid variant count in properties, using default: {} (error: {})", defaultCount, e.getMessage());
            return defaultCount;
        }
    }

    /**
     * Generates the JSON body for a given set of body parameters.
     * Handles special case: single array-type body parameter should generate entire body as array.
     * Otherwise, generates standard JSON object.
     */
    private String generateRequestBody(Map<String, Object> bodyFields, Operation opCfg) {
        // Single body parameter whose schema type is "array" → emit a top-level JSON array,
        // not an object wrapper. The parameter name is no longer required to be the literal
        // "body" — OpenAPI bodies are commonly named after the resource (e.g., "routes",
        // "stationsToAdd", "seatPlan").
        if (bodyFields.size() == 1 && opCfg != null && opCfg.getTestParameters() != null) {
            String onlyKey = bodyFields.keySet().iterator().next();
            for (TestParameter p : opCfg.getTestParameters()) {
                if ("body".equalsIgnoreCase(p.getIn())
                        && "array".equalsIgnoreCase(p.getType())
                        && onlyKey.equals(p.getName())) {
                    String singleValue = bodyFields.get(onlyKey).toString();
                    return generateJsonArray(singleValue, p);
                }
            }
        }

        // Default behavior: generate JSON object
        return toJson(bodyFields);
    }
    
    /**
     * Generates a JSON array for array-type body parameters.
     * Creates multiple array elements to provide realistic test data.
     */
    private String generateJsonArray(String singleValue, TestParameter arrayParam) {
        StringBuilder arrayJson = new StringBuilder("[");
        
        // Generate 2-4 array elements for more realistic testing
        // Use the seeded generator instead of Math.random() so array sizes are reproducible.
        int arraySize = 2 + random.nextInt(3); // Random between 2-4 inclusive
        List<String> arrayValues = new ArrayList<>();
        
        // Add the original value first
        arrayValues.add(singleValue);
        
        // Generate additional values using smart fetch or LLM
        for (int i = 1; i < arraySize; i++) {
            String additionalValue = null;
            
            if (useLLM) {
                ParameterInfo info = new ParameterInfo();
                info.setName(arrayParam.getName());
                info.setDescription(arrayParam.getDescription());
                info.setInLocation(arrayParam.getIn());
                info.setType(arrayParam.getType());
                info.setFormat(arrayParam.getFormat());
                info.setSchemaType(arrayParam.getType());
                info.setSchemaExample(arrayParam.getExample() != null ? arrayParam.getExample().toString() : "");
                info.setRegex(arrayParam.getPattern());
                
                // Try Smart Input Fetching first if available
                if (smartFetcher != null && smartFetchConfig != null && smartFetchConfig.isEnabled()) {
                    try {
                        additionalValue = smartFetcher.fetchSmartInput(info);
                        if (additionalValue != null && !additionalValue.trim().isEmpty()) {
                            log.debug("Smart Fetch (Array) → {} = {}", arrayParam.getName(), additionalValue);
                        } else {
                            additionalValue = null; // Ensure we fall back to LLM
                        }
                    } catch (Exception e) {
                        log.debug("Smart fetching failed for array {}, falling back to LLM: {}", 
                                 arrayParam.getName(), e.getMessage());
                        additionalValue = null; // Ensure we fall back to LLM
                    }
                }
                
                // Fall back to traditional LLM generation if smart fetching didn't work
                if (additionalValue == null) {
                    List<String> vals = llmGen.generateParameterValues(info);
                    if (vals.isEmpty()) {
                        additionalValue = "LLM_EMPTY_" + i;
                    } else {
                        additionalValue = vals.get(random.nextInt(vals.size()));
                    }
                    log.debug("LLM (Array Fallback) → {} = {}", arrayParam.getName(), additionalValue);
                }
            }
            
            // Ultimate fallback
            if (additionalValue == null || additionalValue.trim().isEmpty()) {
                additionalValue = "VAL_" + arrayParam.getName() + "_" + i;
            }
            
            arrayValues.add(additionalValue);
        }
        
        // Build JSON array
        for (int i = 0; i < arrayValues.size(); i++) {
            if (i > 0) arrayJson.append(", ");
            arrayJson.append('"').append(escapeJsonString(arrayValues.get(i))).append('"');
        }
        
        arrayJson.append("]");
        return arrayJson.toString();
    }
    
    /**
     * Escape special characters in JSON string values per RFC 8259 §7. Delegates to the static
     * helper used by {@link #serializeJsonValue(Object)} to keep the two paths in sync.
     */
    private String escapeJsonString(String str) {
        if (str == null) return "";
        return escapeJsonStringStatic(str);
    }

    /**
     * Generate a type-safe fallback value string for a parameter based on its
     * {@link TestParameter} schema.  This value is pre-computed at generation time
     * and emitted as a Java string literal in the bypass code path of the
     * generated test class.
     *
     * <p>Inspects {@code type}, {@code format}, {@code enumValues}, and
     * {@code example} fields to produce the most appropriate fallback:
     * <ul>
     *   <li>{@code integer/int32} → random 6-digit integer string</li>
     *   <li>{@code integer/int64} → random long string</li>
     *   <li>{@code number}        → random decimal string</li>
     *   <li>{@code boolean}       → {@code "true"}</li>
     *   <li>{@code string/uuid}   → UUID</li>
     *   <li>{@code string/email}  → synthetic email</li>
     *   <li>{@code string/date}   → ISO date</li>
     *   <li>{@code string/date-time} → ISO datetime</li>
     *   <li>enum with values     → first enum value</li>
     *   <li>everything else      → UUID</li>
     * </ul>
     *
     * @param tp the test parameter definition
     * @return a non-null string suitable for embedding as a Java literal fallback value
     */
    private String generateTypeSafeFallback(TestParameter tp) {
        // 1. If the parameter has an example value, use it directly
        if (tp.getExample() != null && !tp.getExample().toString().isEmpty()) {
            return tp.getExample().toString();
        }

        // 2. If the parameter has enum values, use the first
        if (tp.getEnumValues() != null && !tp.getEnumValues().isEmpty()) {
            return tp.getEnumValues().get(0);
        }

        String type   = tp.getType()   != null ? tp.getType().toLowerCase(Locale.ROOT)   : "string";
        String format = tp.getFormat() != null ? tp.getFormat().toLowerCase(Locale.ROOT) : "";

        switch (type) {
            case "integer":
                if ("int64".equals(format)) {
                    return String.valueOf(100000L + random.nextInt(900000));
                }
                return String.valueOf(10000 + random.nextInt(90000));

            case "number":
                return String.format(Locale.ROOT, "%.2f", 100.0 + random.nextDouble() * 900.0);

            case "boolean":
                return "true";

            case "string":
                switch (format) {
                    case "uuid":
                        return java.util.UUID.randomUUID().toString();
                    case "email":
                        return "bypass_" + random.nextInt(99999) + "@restest.generated";
                    case "date":
                        return java.time.LocalDate.now().toString();  // ISO "2026-04-01"
                    case "date-time":
                        return java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString();
                    case "uri":
                    case "url":
                        return "https://restest.bypass/" + java.util.UUID.randomUUID();
                    default:
                        return java.util.UUID.randomUUID().toString();
                }

            default:
                return java.util.UUID.randomUUID().toString();
        }
    }
}


