package io.mist.core.registry;

import io.mist.core.spec.Operation;
import io.mist.core.spec.TestConfigurationObject;
import io.mist.core.spec.TestParameter;
import io.mist.core.workflow.WorkflowScenario;
import io.mist.core.workflow.WorkflowStep;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Builds a queryable dictionary of semantic parameter dependencies between APIs.
 *
 * <h3>Build Architecture</h3>
 * <ol>
 *   <li><b>Pass 1a — Producer discovery (config-driven):</b> scans the generated
 *       {@link TestConfigurationObject}s and registers POST/PUT operations as
 *       strong producers for ID-like fields derived from resource nouns.
 *       JsonPath defaults to {@code "data.id"}.</li>
 *   <li><b>Pass 1b — Consumer indexing (config-driven):</b> scans the same
 *       generated configurations for ID-like parameters in each operation and
 *       resolves them to the best matching producer.</li>
 *   <li><b>Pass 1c — Consumer indexing (Swagger safety net):</b> scans the
 *       original OpenAPI specs and registers ID-like parameters (path, query,
 *       body-field) as consumers when they aren't already present from 1b.
 *       This catches anything the config generator may have missed.</li>
 *   <li><b>Pass 2a — Schema-driven JSON path refinement:</b> for every producer,
 *       traverses its OpenAPI 200/201 response schema (with service-prefix {@code $ref}
 *       fallback) to locate the exact JSON path of the ID field, replacing the
 *       heuristic default.</li>
 *   <li><b>Pass 2b — Trace-driven JSON path refinement (fallback):</b> for any
 *       producer not resolved by 2a, consults recorded trace {@code http.response.body}
 *       values.  Best-effort; many production OTel setups omit response bodies.</li>
 * </ol>
 *
 * <p><b>Consumers</b> are parameters whose name matches an ID pattern (e.g. {@code orderId},
 * {@code tripId}, {@code account_uuid}).
 *
 * <p><b>Producers</b> are operations that are likely to <em>create or return</em> the entity
 * that owns the ID.  Detection heuristics (in priority order):
 * <ol>
 *   <li>POST/PUT on a path whose resource noun matches the ID stem
 *       (e.g. POST {@code /order} produces {@code orderId}).</li>
 *   <li>Any operation whose own response parameters or path contains the same ID field
 *       (weaker signal, kept for completeness).</li>
 * </ol>
 *
 * The registry is immutable once built; it is safe to share across threads.
 */
public class SemanticDependencyRegistry {

    private static final Logger log = LogManager.getLogger(SemanticDependencyRegistry.class);

    /**
     * Suffix form: {@code orderId}, {@code account_id}, {@code trip_uuid}, {@code clientUUID}.
     * Requires an explicit boundary so plain English words ending in {@code id}
     * ({@code paid}, {@code aid}, {@code valid}, {@code void}, {@code humid}) and {@code uuid}
     * variants never satisfy the pattern. Accepted boundaries: camelCase {@code Id} / {@code ID} /
     * {@code UUID} / {@code Uuid}, or snake-case {@code _id} / {@code _ID} / {@code _uuid} /
     * {@code _UUID}.
     */
    private static final Pattern ID_SUFFIX =
            Pattern.compile("^.+?(Id|ID|UUID|Uuid|_id|_ID|_uuid|_UUID)$");

    /** Same suffix set as {@link #ID_SUFFIX}, used by {@link #normaliseIdStem} to strip the suffix. */
    private static final Pattern ID_SUFFIX_STRIP =
            Pattern.compile("(Id|ID|UUID|Uuid|_id|_ID|_uuid|_UUID)$");

    /**
     * Prefix form: {@code id_account}, {@code idAccount}, {@code uuid_order},
     * {@code uuidOrder}. Requires an explicit separator after the prefix — either
     * an underscore or a camelCase boundary — to avoid false positives on English
     * words like {@code identify}, {@code identity}, {@code idle} that happen to
     * start with {@code id}. The capturing groups expose the stem portion:
     * group~1 when the separator is {@code _}, group~2 when the separator is a
     * camelCase uppercase boundary.
     */
    private static final Pattern ID_PREFIX =
            Pattern.compile("^(?:id|Id|ID|uuid|Uuid|UUID)(?:_(\\w+)|([A-Z]\\w*))$");

    private static final String DEFAULT_JSON_PATH = "data.id";
    private static final int MAX_SCHEMA_DEPTH = 8;

    /**
     * Key: normalised consumer API key ({@code "post /api/v1/orderservice/order"}).
     * Value: map of paramName → {@link ProducerBinding}.
     */
    private final Map<String, Map<String, ProducerBinding>> consumerIndex = new HashMap<>();

    /** All known producer API keys, keyed by the normalised ID stem they produce. */
    private final Map<String, List<ProducerBinding>> producersByIdStem = new HashMap<>();

    public static class ProducerBinding {
        public final String serviceName;
        public final String apiKey;
        /** Mutable so that Pass 2 (schema refinement) can upgrade the default path. */
        public String jsonPath;
        /** True if Pass 2 resolved the jsonPath from the actual OpenAPI response schema. */
        public boolean schemaResolved;
        /** HTTP method (post/put/get/delete/…) for scoring when a stem has multiple candidates. */
        public final String httpMethod;
        /** 1 = POST/PUT on entity resource (strong); 2 = reverse-engineered from path ID param (weak). */
        public final int heuristic;

        public ProducerBinding(String serviceName, String apiKey, String jsonPath) {
            this(serviceName, apiKey, jsonPath, /*httpMethod*/ "", /*heuristic*/ 1);
        }

        public ProducerBinding(String serviceName, String apiKey, String jsonPath,
                               String httpMethod, int heuristic) {
            this.serviceName = serviceName;
            this.apiKey = apiKey;
            this.jsonPath = jsonPath;
            this.schemaResolved = false;
            this.httpMethod = httpMethod != null ? httpMethod.toLowerCase(Locale.ROOT) : "";
            this.heuristic = heuristic;
        }

        @Override
        public String toString() {
            return apiKey + " → " + jsonPath + (schemaResolved ? " [schema]" : " [heuristic]");
        }
    }

    /** Which optional passes ran during the last build; used by helpers that
     *  need to honour the ablation mask (e.g. generic-id path inference). */
    private EnumSet<Pass> enabledPasses = EnumSet.allOf(Pass.class);

    private SemanticDependencyRegistry() { }

    /**
     * Optional passes in the registry-build pipeline. Used to parameterize
     * {@link #build(Map, Map, List, EnumSet)} for ablation studies.
     *
     * <p>Mandatory passes (producer discovery, heuristic consumer binding via
     * explicit ID-suffixed parameter names, canonical-path indexing) are always
     * applied and not represented here.
     */
    public enum Pass {
        /** Pass 1c — scan the raw OpenAPI spec to catch consumers that the
         *  generated test configuration dropped. Adds bindings via
         *  {@code putIfAbsent}; never overwrites. */
        SWAGGER_CONSUMER_SCAN,

        /** Pass 2a — walk the OpenAPI 200/201 response schema for each producer
         *  and locate a concrete ID field; replaces the {@code data.id}
         *  heuristic default when a match is found. */
        SCHEMA_JSONPATH_REFINEMENT,

        /** Pass 2b — inspect recorded trace response bodies to discover the
         *  real JSON path of each producer's ID field. Best-effort: many OTel
         *  setups omit {@code http.response.body}. */
        TRACE_JSONPATH_REFINEMENT,

        /** For path parameters named generically ({@code id}, {@code uuid}),
         *  infer the entity stem from the preceding path segment
         *  (e.g. {@code /consigns/account/{id}} ⇒ stem {@code account}). */
        GENERIC_ID_PATH_INFERENCE,

        /** Choose the best producer via the scoring function
         *  ({@link #chooseBestProducer}). When disabled, the registry falls
         *  back to the first candidate in insertion order (the naive baseline). */
        SCORED_PRODUCER_SELECTION
    }

    /** Convenience: every optional pass enabled (the default production config). */
    public static EnumSet<Pass> defaultPasses() {
        return EnumSet.allOf(Pass.class);
    }

    /**
     * Builds the registry from the full set of per-service test configurations.
     * Backwards-compatible overload that skips Pass 2 (trace refinement).
     *
     * @param serviceConfigs map of serviceName → parsed {@link TestConfigurationObject}
     * @return a populated registry using heuristic "data.id" fallback paths
     */
    public static SemanticDependencyRegistry build(Map<String, TestConfigurationObject> serviceConfigs) {
        return build(serviceConfigs, null, null);
    }

    /**
     * Builds the registry using the Two-Pass Architecture.
     * Backwards-compatible overload that accepts OpenAPI specs but also needs traces
     * for the new trace-driven Pass 2.
     *
     * @param serviceConfigs map of serviceName → parsed {@link TestConfigurationObject}
     * @param serviceSpecs   map of serviceName → parsed {@code OpenAPI} (nullable, kept for API compat)
     * @return a populated registry
     */
    public static SemanticDependencyRegistry build(Map<String, TestConfigurationObject> serviceConfigs,
                                                   Map<String, OpenAPI> serviceSpecs) {
        return build(serviceConfigs, serviceSpecs, null);
    }

    /**
     * Builds the registry using the Two-Pass Architecture.
     *
     * <ul>
     *   <li><b>Pass 1:</b> Heuristic Discovery — populates {@code producersByIdStem}
     *       and {@code consumerIndex} with {@code jsonPath = "data.id"} fallback.</li>
     *   <li><b>Pass 2:</b> Trace-Driven Refinement — uses actual recorded trace
     *       response bodies to locate the exact JSON path of ID fields, completely
     *       bypassing the broken OpenAPI schema resolution.</li>
     * </ul>
     *
     * @param serviceConfigs map of serviceName → parsed {@link TestConfigurationObject}
     * @param serviceSpecs   map of serviceName → parsed {@code OpenAPI} (nullable, kept for API compat)
     * @param scenarios      list of recorded {@link WorkflowScenario}s with trace data (nullable)
     * @return a populated registry
     */
    public static SemanticDependencyRegistry build(Map<String, TestConfigurationObject> serviceConfigs,
                                                   Map<String, OpenAPI> serviceSpecs,
                                                   List<WorkflowScenario> scenarios) {
        return build(serviceConfigs, serviceSpecs, scenarios, defaultPasses());
    }

    /**
     * Ablation-aware build. Caller chooses which optional {@link Pass}es run.
     * Mandatory passes (producer discovery from config, canonical-path keying,
     * ID-suffixed-name consumer binding) always run regardless.
     */
    public static SemanticDependencyRegistry build(Map<String, TestConfigurationObject> serviceConfigs,
                                                   Map<String, OpenAPI> serviceSpecs,
                                                   List<WorkflowScenario> scenarios,
                                                   EnumSet<Pass> passes) {
        if (passes == null) passes = defaultPasses();
        SemanticDependencyRegistry reg = new SemanticDependencyRegistry();
        reg.enabledPasses = EnumSet.copyOf(passes);

        // ═══════════════════════════════════════════════════════════════
        // Pass 1: Heuristic Discovery
        // ═══════════════════════════════════════════════════════════════

        // Pass 1a: discover all producers (POST/PUT operations on entity resources)
        for (Map.Entry<String, TestConfigurationObject> svcEntry : serviceConfigs.entrySet()) {
            String svcName = svcEntry.getKey();
            TestConfigurationObject tco = svcEntry.getValue();
            if (tco.getTestConfiguration() == null
                    || tco.getTestConfiguration().getOperations() == null) continue;

            for (Operation op : tco.getTestConfiguration().getOperations()) {
                String method = op.getMethod() != null ? op.getMethod().toLowerCase(Locale.ROOT) : "";
                String path = op.getTestPath() != null ? op.getTestPath() : "";
                // Canonicalize the path so the registry key is independent of how path
                // parameters are spelled: a template /{orderId} stored as {} matches a
                // runtime concrete /df2b3a56-… (also canonicalized to {}) at lookup time.
                String apiKey = method + " " + canonicalizePath(path);

                registerProducers(reg, svcName, apiKey, method, path, op);
            }
        }

        // Pass 1b: for every consumer parameter, find the best producer
        for (Map.Entry<String, TestConfigurationObject> svcEntry : serviceConfigs.entrySet()) {
            String svcName = svcEntry.getKey();
            TestConfigurationObject tco = svcEntry.getValue();
            if (tco.getTestConfiguration() == null
                    || tco.getTestConfiguration().getOperations() == null) continue;

            for (Operation op : tco.getTestConfiguration().getOperations()) {
                String method = op.getMethod() != null ? op.getMethod().toLowerCase(Locale.ROOT) : "";
                String path = op.getTestPath() != null ? op.getTestPath() : "";
                String consumerKey = method + " " + canonicalizePath(path);

                if (op.getTestParameters() == null) continue;

                for (TestParameter tp : op.getTestParameters()) {
                    String pName = tp.getName();
                    if (pName == null) continue;
                    String stem = null;
                    if (isIdLikeParam(pName)) {
                        stem = normaliseIdStem(pName);
                    } else if (isGenericIdParam(pName) && passes.contains(Pass.GENERIC_ID_PATH_INFERENCE)) {
                        // Generic "id" / "uuid" path param — infer from preceding segment
                        stem = inferStemFromPathSegment(path, pName);
                    }
                    if (stem == null) continue;
                    List<ProducerBinding> candidates = reg.producersByIdStem.get(stem);
                    if (candidates == null || candidates.isEmpty()) continue;

                    ProducerBinding best = passes.contains(Pass.SCORED_PRODUCER_SELECTION)
                            ? chooseBestProducer(candidates, stem, svcName)
                            : candidates.get(0);
                    reg.consumerIndex
                            .computeIfAbsent(consumerKey, k -> new LinkedHashMap<>())
                            .put(pName, best);
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // Pass 1c: Swagger-side consumer scan (SAFETY NET for anything the
        // generated config might have dropped or that was added to the raw
        // OpenAPI spec by hand).  For every operation defined in an
        // OpenAPISpecification, look at its parameters (path + query + body)
        // and register ID-like ones as consumers.  Entries that already exist
        // from Pass 1b are preserved via putIfAbsent.
        // ═══════════════════════════════════════════════════════════════
        if (serviceSpecs != null && !serviceSpecs.isEmpty() && passes.contains(Pass.SWAGGER_CONSUMER_SCAN)) {
            int addedFromSwagger = scanSwaggerConsumers(reg, serviceSpecs);
            log.info("Pass 1c (Swagger consumer scan): added {} consumer bindings not present in generated config",
                    addedFromSwagger);
        }

        // ═══════════════════════════════════════════════════════════════
        // Pass 2a: Schema-Driven JSON Path Refinement
        // ----------------------------------------------------------------
        // For every producer, locate its OpenAPI operation's 200/201 response
        // schema, resolve any $ref (with service-prefix fallback to handle
        // broken ref names like "api_HttpEntity" that are actually stored as
        // "ts-<service>_HttpEntity" in components.schemas), and traverse to
        // find the first field whose name matches the producer's ID stem.
        // ═══════════════════════════════════════════════════════════════
        int schemaRefined = 0;
        if (serviceSpecs != null && !serviceSpecs.isEmpty() && passes.contains(Pass.SCHEMA_JSONPATH_REFINEMENT)) {
            schemaRefined = refineJsonPathsFromSchema(reg, serviceSpecs);
            log.info("Pass 2a (schema-driven): {} producer jsonPaths refined from OpenAPI response schemas",
                    schemaRefined);
        }

        // ═══════════════════════════════════════════════════════════════
        // Pass 2b: Trace-Driven JSON Path Refinement (fallback)
        // ----------------------------------------------------------------
        // For any producer whose jsonPath is still the heuristic default after
        // schema refinement, consult actual recorded trace response bodies.
        // Many production OTel setups omit http.response.body, so this pass
        // is best-effort.
        // ═══════════════════════════════════════════════════════════════
        if (scenarios != null && !scenarios.isEmpty() && passes.contains(Pass.TRACE_JSONPATH_REFINEMENT)) {
            List<WorkflowStep> allSteps = flattenAllSteps(scenarios);
            log.info("Pass 2b: running trace-driven refinement with {} trace steps", allSteps.size());
            refineJsonPathsFromTraces(reg, allSteps);
        } else {
            log.info("Pass 2b skipped: {}; remaining producers use heuristic '{}'",
                    passes.contains(Pass.TRACE_JSONPATH_REFINEMENT) ? "no trace scenarios provided" : "disabled by ablation mask",
                    DEFAULT_JSON_PATH);
        }

        log.info("SemanticDependencyRegistry built: {} consumer APIs, {} ID stems with producers",
                reg.consumerIndex.size(), reg.producersByIdStem.size());
        for (Map.Entry<String, Map<String, ProducerBinding>> e : reg.consumerIndex.entrySet()) {
            for (Map.Entry<String, ProducerBinding> dep : e.getValue().entrySet()) {
                log.debug("  {} param '{}' ← {}", e.getKey(), dep.getKey(), dep.getValue());
            }
        }

        return reg;
    }

    /**
     * Looks up a producer for a given consumer API and parameter name.
     *
     * @param consumerApiKey normalised API key (e.g. {@code "get /api/v1/orderservice/order/{orderId}"})
     * @param paramName      the parameter name (e.g. {@code "orderId"})
     * @return the {@link ProducerBinding}, or {@code null} if none is registered
     */
    public ProducerBinding findProducer(String consumerApiKey, String paramName) {
        Map<String, ProducerBinding> params = consumerIndex.get(consumerApiKey);
        return params != null ? params.get(paramName) : null;
    }

    /**
     * Returns all candidate producers that could provide a value for the given
     * parameter, based purely on the parameter's entity stem.
     *
     * <p>Unlike {@link #findProducer(String, String)} which returns a single
     * pre-selected binding for a specific consumer API, this method returns
     * <b>every</b> registered producer for the stem, allowing the caller to
     * match against the actual preceding test-case history at runtime.
     *
     * @param paramName the consumer parameter name (e.g. {@code "orderId"})
     * @return all producer bindings whose stem matches, or an empty list
     */
    public List<ProducerBinding> getCandidateProducers(String paramName) {
        String stem = normaliseIdStem(paramName);
        if (stem == null) return Collections.emptyList();
        List<ProducerBinding> candidates = producersByIdStem.get(stem);
        return candidates != null ? Collections.unmodifiableList(candidates) : Collections.emptyList();
    }

    /**
     * Path-aware variant of {@link #getCandidateProducers(String)} — when the
     * parameter name is generic ({@code id}, {@code uuid}) so stem derivation
     * from the name alone yields nothing, the stem is inferred from the
     * preceding path segment of the consumer endpoint.
     *
     * <p>Example: {@code (paramName="id", consumerPath="/.../consigns/account/{id}")}
     * resolves to stem {@code account} and returns every account-producer
     * already indexed.
     */
    public List<ProducerBinding> getCandidateProducers(String paramName, String consumerPath) {
        List<ProducerBinding> primary = getCandidateProducers(paramName);
        if (!primary.isEmpty()) return primary;
        if (isGenericIdParam(paramName) && consumerPath != null) {
            String stem = inferStemFromPathSegment(consumerPath, paramName);
            if (stem != null) {
                List<ProducerBinding> candidates = producersByIdStem.get(stem);
                if (candidates != null) return Collections.unmodifiableList(candidates);
            }
        }
        return Collections.emptyList();
    }

    /**
     * Returns all registered consumer entries (for diagnostics / logging).
     */
    public Map<String, Map<String, ProducerBinding>> getAllConsumerBindings() {
        return Collections.unmodifiableMap(consumerIndex);
    }

    /**
     * Serializes the entire consumer index and producer stems to a human-readable
     * JSON file for manual auditing of the heuristic stemming rules.
     *
     * @param filePath the output file path (e.g. {@code "target/semantic-registry-dump.json"})
     */
    public void dumpRegistryToFile(String filePath) {
        try {
            JSONObject root = new JSONObject();

            // Consumer index
            JSONObject consumers = new JSONObject();
            for (Map.Entry<String, Map<String, ProducerBinding>> api : consumerIndex.entrySet()) {
                JSONObject params = new JSONObject();
                for (Map.Entry<String, ProducerBinding> param : api.getValue().entrySet()) {
                    JSONObject binding = new JSONObject();
                    binding.put("producerService", param.getValue().serviceName);
                    binding.put("producerApiKey", param.getValue().apiKey);
                    binding.put("jsonPath", param.getValue().jsonPath);
                    binding.put("schemaResolved", param.getValue().schemaResolved);
                    params.put(param.getKey(), binding);
                }
                consumers.put(api.getKey(), params);
            }
            root.put("consumerIndex", consumers);

            // Producer stems
            JSONObject producers = new JSONObject();
            int schemaResolvedCount = 0;
            int heuristicCount = 0;
            for (Map.Entry<String, List<ProducerBinding>> stem : producersByIdStem.entrySet()) {
                org.json.JSONArray arr = new org.json.JSONArray();
                for (ProducerBinding pb : stem.getValue()) {
                    JSONObject entry = new JSONObject();
                    entry.put("serviceName", pb.serviceName);
                    entry.put("apiKey", pb.apiKey);
                    entry.put("jsonPath", pb.jsonPath);
                    entry.put("schemaResolved", pb.schemaResolved);
                    arr.put(entry);
                    if (pb.schemaResolved) schemaResolvedCount++; else heuristicCount++;
                }
                producers.put(stem.getKey(), arr);
            }
            root.put("producersByIdStem", producers);

            // Summary stats
            JSONObject stats = new JSONObject();
            stats.put("totalConsumerApis", consumerIndex.size());
            stats.put("totalProducerStems", producersByIdStem.size());
            int totalBindings = 0;
            for (Map<String, ProducerBinding> m : consumerIndex.values()) totalBindings += m.size();
            stats.put("totalParamBindings", totalBindings);
            stats.put("pass2_schemaResolved", schemaResolvedCount);
            stats.put("pass2_heuristicFallback", heuristicCount);
            root.put("_stats", stats);

            Path path = Paths.get(filePath);
            Files.createDirectories(path.getParent());
            Files.writeString(path, root.toString(2));
            log.info("SemanticDependencyRegistry dumped to {}", filePath);
        } catch (IOException e) {
            log.warn("Failed to dump registry to {}: {}", filePath, e.getMessage());
        }
    }

    /**
     * Checks whether {@code consumerStep} has ANY parameter that can be satisfied by
     * {@code producerStep} according to the registry's semantic rules.
     *
     * <p>This is used by the Scenario Shattering / Partitioning optimizer to build
     * a directed dependency graph over the root steps of a merged scenario. An edge
     * from producer → consumer is drawn when this method returns {@code true}.
     *
     * @param consumerStep the downstream WorkflowStep whose parameters may depend on the producer
     * @param producerStep the upstream WorkflowStep that may produce values for the consumer
     * @return true if at least one consumer parameter has a registered producer that matches
     *         the producer step's API key
     */
    public boolean hasDirectedDependency(WorkflowStep consumerStep, WorkflowStep producerStep) {
        String consumerApiKey = buildApiKey(consumerStep);
        String producerApiKey = buildApiKey(producerStep);
        if (consumerApiKey == null || producerApiKey == null) return false;

        Map<String, ProducerBinding> paramBindings = consumerIndex.get(consumerApiKey);
        if (paramBindings == null || paramBindings.isEmpty()) return false;

        for (ProducerBinding binding : paramBindings.values()) {
            if (producerApiKey.equals(binding.apiKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds a normalised API key ({@code "post /api/v1/orderservice/order"}) from
     * a WorkflowStep's span attributes — mirrors the key format used by the registry.
     */
    private static String buildApiKey(WorkflowStep step) {
        Map<String, String> out = step.getOutputFields();
        Map<String, String> in = step.getInputFields();

        String method = out.get("http.method");
        if (method == null) method = in.get("http.method");
        if (method == null) {
            String opName = step.getOperationName();
            if (opName != null && opName.contains(" ")) {
                method = opName.substring(0, opName.indexOf(' '));
            }
        }

        String path = out.get("http.target");
        if (path == null) path = in.get("http.target");
        if (path == null) {
            String url = out.get("http.url");
            if (url == null) url = in.get("http.url");
            if (url != null) {
                try {
                    path = new java.net.URL(url).getPath();
                } catch (Exception ignored) {
                    int s = url.indexOf("://");
                    if (s >= 0) {
                        int p = url.indexOf("/", s + 3);
                        if (p >= 0) path = url.substring(p);
                    }
                }
            }
        }
        if (path == null) {
            String opName = step.getOperationName();
            if (opName != null && opName.contains(" ")) {
                path = opName.substring(opName.indexOf(' ') + 1);
            }
        }

        if (method == null || path == null) return null;

        // Strip query string from path
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);

        // Canonicalize: registry keys were built from OpenAPI testPath ({orderId}, {id}),
        // runtime span attributes give concrete values (df2b3a56-…, 12345). Both forms
        // collapse to {} here so lookups succeed for endpoints with path parameters —
        // which is most entity-by-id endpoints.
        return method.toLowerCase(Locale.ROOT) + " " + canonicalizePath(path);
    }

    /**
     * Canonicalize an API path to a form where both OpenAPI path-parameter templates
     * and concrete runtime ID-like segments collapse to the same {@code {}} placeholder,
     * enabling template-vs-runtime key matching.
     *
     * <p>Replacements applied (order matters):
     * <ul>
     *   <li>OpenAPI path params {@code /{anyName}} → {@code /{}}</li>
     *   <li>UUID v4 segments (36-char hyphenated hex) → {@code /{}}</li>
     *   <li>Pure digit segments with ≥4 digits → {@code /{}}</li>
     *   <li>Decimal segments like {@code /12.0} (used by consignprice/weight) → {@code /{}}</li>
     *   <li>Boolean-looking segments {@code /true}, {@code /false} (after a numeric
     *       segment was canonicalized; only done when preceded by {@code /{}}) → {@code /{}}</li>
     * </ul>
     *
     * <p>We deliberately do NOT collapse short alphanumeric segments because legitimate
     * resource names (e.g. {@code /api/v1/orderservice/order/refresh}) would otherwise
     * be mis-canonicalized.
     */
    public static String canonicalizePath(String path) {
        if (path == null) return null;
        // 1. OpenAPI path params: /{whatever} → /{}
        path = path.replaceAll("/\\{[^/}]+\\}", "/{}");
        // 2. UUID: /xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx → /{}
        path = path.replaceAll("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "/{}");
        // 3. Long integer IDs: /12345 (4+ digits) → /{}.  Threshold of 4 avoids stripping
        //    API version numbers like /v1 and status codes like /200.
        path = path.replaceAll("/\\d{4,}", "/{}");
        // 4. Decimal segments (weight, price): /12.0 → /{}
        path = path.replaceAll("/\\d+\\.\\d+", "/{}");
        // 5. Trailing boolean after an already-canonicalized segment: /{}/true → /{}/{}
        path = path.replaceAll("/\\{\\}/(true|false)(?=/|$)", "/{}/{}");
        return path;
    }

    // ── Pass 1c: Swagger-side consumer scan ───────────────────────────────
    //
    // Iterates every OpenAPI operation across all services and, for every ID-like
    // parameter (path, query, or body-field) whose binding is NOT yet present in
    // the consumerIndex from Pass 1b, registers it.  This is a safety net against
    // gaps in the generator-produced TestConfigurationObject.
    private static int scanSwaggerConsumers(SemanticDependencyRegistry reg,
                                            Map<String, OpenAPI> serviceSpecs) {
        int added = 0;
        for (Map.Entry<String, OpenAPI> svcEntry : serviceSpecs.entrySet()) {
            String svcName = svcEntry.getKey();
            OpenAPI spec = svcEntry.getValue();
            if (spec == null
                    || spec.getPaths() == null) continue;

            for (Map.Entry<String, PathItem> pathEntry : spec.getPaths().entrySet()) {
                String path = pathEntry.getKey();
                PathItem pathItem = pathEntry.getValue();
                if (pathItem == null) continue;

                for (Map.Entry<PathItem.HttpMethod, io.swagger.v3.oas.models.Operation> opEntry :
                        pathItem.readOperationsMap().entrySet()) {
                    String method = opEntry.getKey().name().toLowerCase(Locale.ROOT);
                    io.swagger.v3.oas.models.Operation op = opEntry.getValue();
                    if (op == null) continue;

                    String consumerKey = method + " " + canonicalizePath(path);

                    // Parameters — path/query/header/cookie
                    if (op.getParameters() != null) {
                        for (Parameter p : op.getParameters()) {
                            if (p == null || p.getName() == null) continue;
                            String pn = p.getName();
                            if (isIdLikeParam(pn)) {
                                added += registerConsumerIfAbsent(reg, svcName, consumerKey, pn, null);
                            } else if (reg.enabledPasses.contains(Pass.GENERIC_ID_PATH_INFERENCE)
                                    && isGenericIdParam(pn) && "path".equalsIgnoreCase(p.getIn())) {
                                String stem = inferStemFromPathSegment(path, pn);
                                if (stem != null) {
                                    added += registerConsumerIfAbsent(reg, svcName, consumerKey, pn, stem);
                                }
                            }
                        }
                    }
                    // Request body properties
                    if (op.getRequestBody() != null && op.getRequestBody().getContent() != null) {
                        for (MediaType mt : op.getRequestBody().getContent().values()) {
                            if (mt == null || mt.getSchema() == null) continue;
                            Schema<?> bodySchema = resolveSchemaRef(mt.getSchema(), spec, svcName);
                            if (bodySchema == null || bodySchema.getProperties() == null) continue;
                            for (Object propName : bodySchema.getProperties().keySet()) {
                                String pn = String.valueOf(propName);
                                if (!isIdLikeParam(pn)) continue;
                                added += registerConsumerIfAbsent(reg, svcName, consumerKey, pn, null);
                            }
                        }
                    }
                }
            }
        }
        return added;
    }

    /**
     * Register a consumer binding only if the (consumerKey, paramName) pair is not already indexed.
     *
     * @param explicitStem when non-null, use this stem directly (e.g. for generic {@code id}
     *                     path params with path-segment-inferred stems); otherwise derive
     *                     it from {@code paramName} via {@link #normaliseIdStem}.
     */
    private static int registerConsumerIfAbsent(SemanticDependencyRegistry reg,
                                                String svcName,
                                                String consumerKey,
                                                String paramName,
                                                String explicitStem) {
        Map<String, ProducerBinding> existing = reg.consumerIndex.get(consumerKey);
        if (existing != null && existing.containsKey(paramName)) return 0;

        String stem = explicitStem != null ? explicitStem : normaliseIdStem(paramName);
        if (stem == null) return 0;
        List<ProducerBinding> candidates = reg.producersByIdStem.get(stem);
        if (candidates == null || candidates.isEmpty()) return 0;

        ProducerBinding best = reg.enabledPasses.contains(Pass.SCORED_PRODUCER_SELECTION)
                ? chooseBestProducer(candidates, stem, svcName)
                : candidates.get(0);
        reg.consumerIndex
                .computeIfAbsent(consumerKey, k -> new LinkedHashMap<>())
                .put(paramName, best);
        return 1;
    }

    /**
     * Choose the best producer for a given stem and consumer-side service.
     *
     * <p>Scoring rationale (higher is better):
     * <ul>
     *   <li><b>Heuristic 1 over Heuristic 2</b> (H1 = explicit POST/PUT on the resource;
     *       H2 = reverse-engineered from path ID param in any-method endpoint).</li>
     *   <li><b>POST &gt; PUT &gt; PATCH &gt; DELETE &gt; GET</b> (creation strength).</li>
     *   <li><b>Service-name match with stem</b> — prefer a producer from the service
     *       whose name contains the stem (e.g. stem {@code order} → {@code ts-order-service}
     *       beats {@code ts-wait-order-service} or {@code ts-admin-order-service} when the
     *       consumer is cross-service).</li>
     *   <li><b>Cross-service bonus</b> when the producer service differs from the consumer's
     *       (all else equal, a cross-service dependency is usually the real one).</li>
     *   <li><b>Shorter path is more canonical</b> — tiebreaker favouring
     *       {@code /orderservice/order} over {@code /orderservice/order/refresh}.</li>
     * </ul>
     */
    private static ProducerBinding chooseBestProducer(List<ProducerBinding> candidates,
                                                      String stem,
                                                      String consumerSvcName) {
        ProducerBinding best = null;
        int bestScore = Integer.MIN_VALUE;
        for (ProducerBinding pb : candidates) {
            int score = scoreProducer(pb, stem, consumerSvcName);
            if (score > bestScore) {
                bestScore = score;
                best = pb;
            }
        }
        return best != null ? best : candidates.get(0);
    }

    private static int scoreProducer(ProducerBinding pb, String stem, String consumerSvcName) {
        int score = 0;
        // Heuristic tier (H1 is much stronger than H2)
        score += (pb.heuristic == 1) ? 100 : 0;
        // HTTP method strength
        switch (pb.httpMethod) {
            case "post":  score += 50; break;
            case "put":   score += 40; break;
            case "patch": score += 25; break;
            case "delete":score += 10; break;
            case "get":   score +=  5; break;
            default:                    break;
        }
        // Service-name matches the entity stem — layered so the MOST specific match wins.
        //   "ts-order-service"        ↔ stem "order"  → EXACT match, strongest bonus
        //   "ts-wait-order-service"   ↔ stem "order"  → stem appears but service is specialized
        //   "ts-admin-order-service"  ↔ stem "order"  → admin-variant, lower priority
        if (pb.serviceName != null && stem != null) {
            String svcLc = pb.serviceName.toLowerCase(Locale.ROOT);
            // Derive the canonical match structurally instead of requiring the literal
            // "ts-<stem>-service" shape, so producers on any SUT (e.g. "order-service",
            // "order", "myapp-order-svc") still earn the strongest bonus. We tokenize on
            // non-alphanumeric boundaries, drop generic prefix/suffix tokens, and treat the
            // service as the canonical producer when the remaining tokens are exactly {stem}.
            String[] svcTokens = svcLc.split("[^a-z0-9]+");
            java.util.List<String> meaningful = new java.util.ArrayList<>();
            for (String tok : svcTokens) {
                if (tok.isEmpty()) continue;
                if (tok.equals("ts") || tok.equals("service") || tok.equals("services")
                        || tok.equals("svc") || tok.equals("srv") || tok.equals("api")) {
                    continue;                      // generic naming affixes, not part of the entity
                }
                meaningful.add(tok);
            }
            boolean isCanonical = meaningful.size() == 1 && meaningful.get(0).equals(stem);
            if (isCanonical) {
                score += 60;                       // canonical service named after this stem
            } else if (svcLc.contains("-" + stem + "-")) {
                score += 25;                       // stem appears as a whole path segment in svc name (wait-order, admin-order)
            } else if (svcLc.contains(stem)) {
                score += 10;                       // weaker substring match
            }
        }
        // Cross-service dependencies are generally the real ones.
        if (pb.serviceName != null && consumerSvcName != null
                && !pb.serviceName.equals(consumerSvcName)) {
            score += 5;
        }
        // Shorter canonical path breaks ties (e.g. /order beats /order/refresh).
        if (pb.apiKey != null) {
            // Penalize path length weakly so it only matters as a tiebreaker.
            score -= pb.apiKey.length() / 20;
        }
        return score;
    }

    // ── Pass 2a: Schema-Driven JSON Path Resolution ───────────────────────
    //
    // For every producer in producersByIdStem, find its OpenAPI operation's
    // 200/201 response schema, resolve $ref (with service-prefix fallback),
    // and locate the first field name that matches the producer's ID stem.
    // The resolved dotted path (e.g. "data.id", "data[0].orderId") replaces
    // the heuristic default only when a match is found.
    private static int refineJsonPathsFromSchema(SemanticDependencyRegistry reg,
                                                 Map<String, OpenAPI> serviceSpecs) {
        int refined = 0;

        // Index every OpenAPI operation across all services by canonical apiKey.
        Map<String, SchemaOperationRef> opByKey = new HashMap<>();
        for (Map.Entry<String, OpenAPI> svcEntry : serviceSpecs.entrySet()) {
            String svcName = svcEntry.getKey();
            OpenAPI spec = svcEntry.getValue();
            if (spec == null
                    || spec.getPaths() == null) continue;
            for (Map.Entry<String, PathItem> pe : spec.getPaths().entrySet()) {
                String path = pe.getKey();
                PathItem pathItem = pe.getValue();
                if (pathItem == null) continue;
                for (Map.Entry<PathItem.HttpMethod, io.swagger.v3.oas.models.Operation> me :
                        pathItem.readOperationsMap().entrySet()) {
                    String apiKey = me.getKey().name().toLowerCase(Locale.ROOT) + " " + canonicalizePath(path);
                    opByKey.put(apiKey, new SchemaOperationRef(svcName, spec, me.getValue()));
                }
            }
        }

        for (Map.Entry<String, List<ProducerBinding>> stemEntry : reg.producersByIdStem.entrySet()) {
            String stem = stemEntry.getKey();
            for (ProducerBinding pb : stemEntry.getValue()) {
                if (pb.schemaResolved) continue;     // already refined
                SchemaOperationRef ref = opByKey.get(pb.apiKey);
                if (ref == null || ref.op == null) continue;

                Schema<?> responseSchema = extractSuccessResponseSchema(ref.op, ref.openApi, ref.svcName);
                if (responseSchema == null) continue;

                String discovered = findIdJsonPathInSchema(responseSchema, stem,
                        ref.openApi, ref.svcName, new HashSet<>(), "", 0);
                if (discovered != null) {
                    pb.jsonPath = discovered;
                    pb.schemaResolved = true;
                    refined++;
                    log.debug("Pass 2a: {} stem '{}' → '{}'", pb.apiKey, stem, discovered);
                }
            }
        }
        return refined;
    }

    /** Holds a reference to an operation together with the OpenAPI root it was pulled from. */
    private static final class SchemaOperationRef {
        final String svcName;
        final OpenAPI openApi;
        final io.swagger.v3.oas.models.Operation op;
        SchemaOperationRef(String svcName, OpenAPI openApi, io.swagger.v3.oas.models.Operation op) {
            this.svcName = svcName;
            this.openApi = openApi;
            this.op = op;
        }
    }

    /** Get the schema of the first 2xx response content entry; resolve $ref. */
    private static Schema<?> extractSuccessResponseSchema(io.swagger.v3.oas.models.Operation op,
                                                          OpenAPI openApi,
                                                          String svcName) {
        if (op == null || op.getResponses() == null) return null;
        ApiResponses responses = op.getResponses();
        for (String code : Arrays.asList("200", "201")) {
            ApiResponse resp = responses.get(code);
            if (resp == null || resp.getContent() == null) continue;
            Content content = resp.getContent();
            // Prefer JSON; fall back to first non-null media type
            MediaType mt = content.get("application/json");
            if (mt == null) mt = content.get("*/*");
            if (mt == null && !content.isEmpty()) mt = content.values().iterator().next();
            if (mt == null || mt.getSchema() == null) continue;
            return resolveSchemaRef(mt.getSchema(), openApi, svcName);
        }
        return null;
    }

    /**
     * Resolve a {@link Schema}'s {@code $ref} (if any) by name lookup against
     * {@code components.schemas}, with a service-prefix fallback.  The trainticket
     * Swagger uses short refs like {@code api_HttpEntity} while components use
     * service-prefixed names like {@code ts-admin-order-service_HttpEntity} — so
     * if the literal ref name is not found we retry with a few prefix variants.
     */
    private static Schema<?> resolveSchemaRef(Schema<?> schema, OpenAPI openApi, String svcName) {
        if (schema == null || openApi == null || openApi.getComponents() == null
                || openApi.getComponents().getSchemas() == null) return schema;
        if (schema.get$ref() == null) return schema;

        String ref = schema.get$ref();
        int slash = ref.lastIndexOf('/');
        String refName = slash >= 0 ? ref.substring(slash + 1) : ref;
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();

        Schema<?> target = schemas.get(refName);
        if (target != null) return target;

        // Service-prefix fallback: e.g. api_HttpEntity → ts-<svc>_HttpEntity
        String shortName = refName.startsWith("api_") ? refName.substring(4) : refName;
        if (svcName != null) {
            Schema<?> pref = schemas.get(svcName + "_" + shortName);
            if (pref != null) return pref;
        }
        // Brute-force: any schema whose name ends with "_<shortName>"
        for (Map.Entry<String, Schema> e : schemas.entrySet()) {
            if (e.getKey().endsWith("_" + shortName)) return e.getValue();
        }
        return null;
    }

    /**
     * DFS over a resolved schema looking for the first property whose name is an
     * ID for {@code stem} (e.g. stem = "order" matches {@code id}, {@code orderId},
     * {@code order_id}).  Returns the dot-notation path to the ID field.
     */
    private static String findIdJsonPathInSchema(Schema<?> schema, String stem,
                                                 OpenAPI openApi, String svcName,
                                                 Set<String> visited, String pathSoFar,
                                                 int depth) {
        if (schema == null || depth > MAX_SCHEMA_DEPTH) return null;
        Schema<?> resolved = resolveSchemaRef(schema, openApi, svcName);
        if (resolved == null) return null;
        // Prevent cycles when following $ref chains
        String refKey = resolved.get$ref() != null ? resolved.get$ref() : System.identityHashCode(resolved) + "";
        if (!visited.add(refKey)) return null;

        // Array: descend into items, keep the same path (arrays don't add a dotted segment here)
        if (resolved instanceof ArraySchema) {
            Schema<?> items = ((ArraySchema) resolved).getItems();
            return findIdJsonPathInSchema(items, stem, openApi, svcName, visited, pathSoFar, depth + 1);
        }

        Map<String, Schema> props = resolved.getProperties();
        if (props == null || props.isEmpty()) return null;

        // First pass: exact ID-match at this level
        for (Map.Entry<String, Schema> e : props.entrySet()) {
            if (matchesIdForStem(e.getKey(), stem)) {
                return pathSoFar.isEmpty() ? e.getKey() : pathSoFar + "." + e.getKey();
            }
        }
        // Second pass: recurse into object/array-typed properties (common wrapper: "data")
        for (Map.Entry<String, Schema> e : props.entrySet()) {
            Schema<?> child = e.getValue();
            if (child == null) continue;
            String childPath = pathSoFar.isEmpty() ? e.getKey() : pathSoFar + "." + e.getKey();
            String hit = findIdJsonPathInSchema(child, stem, openApi, svcName, visited, childPath, depth + 1);
            if (hit != null) return hit;
        }
        return null;
    }

    /** True if {@code fieldName} is an ID field for the given entity stem. */
    private static boolean matchesIdForStem(String fieldName, String stem) {
        if (fieldName == null || stem == null) return false;
        String lc = fieldName.toLowerCase(Locale.ROOT);
        if (lc.equals("id")) return true;
        if (lc.equals(stem + "id") || lc.equals(stem + "_id")) return true;
        // Also accept the full param-name form (e.g. "orderId" is already exact)
        return lc.startsWith(stem) && (lc.endsWith("id") || lc.endsWith("uuid"));
    }

    // ── Pass 2b: Trace-Driven JSON Path Resolution ────────────────────────

    /**
     * Iterates through every {@link ProducerBinding} in {@code producersByIdStem}
     * and uses the actual recorded trace response bodies to resolve the exact
     * JSON path of the ID field.  This replaces the previous schema-based approach
     * which failed due to broken {@code $ref} names and generic type erasure.
     *
     * <p>For each producer, we find a matching trace span, extract its
     * {@code http.response.body}, and run a DFS to locate the ID field path.
     */
    private static void refineJsonPathsFromTraces(SemanticDependencyRegistry reg,
                                                  List<WorkflowStep> allTraceSteps) {
        int refined = 0;
        int skipped = 0;

        // Index trace steps by normalised API key for fast lookup
        Map<String, List<WorkflowStep>> stepsByApiKey = new HashMap<>();
        for (WorkflowStep step : allTraceSteps) {
            String apiKey = buildApiKey(step);
            if (apiKey != null) {
                stepsByApiKey.computeIfAbsent(apiKey, k -> new ArrayList<>()).add(step);
            }
        }

        for (Map.Entry<String, List<ProducerBinding>> stemEntry : reg.producersByIdStem.entrySet()) {
            String stem = stemEntry.getKey();
            for (ProducerBinding pb : stemEntry.getValue()) {
                // Skip producers already resolved by the schema pass.
                if (pb.schemaResolved) continue;
                // Find a trace step that matches this producer's API key
                List<WorkflowStep> matchingSteps = stepsByApiKey.get(pb.apiKey);
                if (matchingSteps == null || matchingSteps.isEmpty()) {
                    // Try fuzzy match: normalise path params to {}
                    String normKey = pb.apiKey.replaceAll("\\{[^}]+}", "{}");
                    for (Map.Entry<String, List<WorkflowStep>> e : stepsByApiKey.entrySet()) {
                        if (e.getKey().replaceAll("\\{[^}]+}", "{}").equals(normKey)) {
                            matchingSteps = e.getValue();
                            break;
                        }
                    }
                }
                if (matchingSteps == null || matchingSteps.isEmpty()) {
                    skipped++;
                    log.trace("Pass 2 (trace): no trace data for {} — keeping '{}'", pb.apiKey, pb.jsonPath);
                    continue;
                }

                // Try each matching step until we find a usable response body
                String discoveredPath = null;
                for (WorkflowStep traceStep : matchingSteps) {
                    String responseBody = traceStep.getOutputFields().get("http.response.body");
                    if (responseBody == null || responseBody.isBlank()) continue;

                    discoveredPath = findIdJsonPathInPayload(responseBody, stem);
                    if (discoveredPath != null) break;
                }

                if (discoveredPath != null) {
                    String oldPath = pb.jsonPath;
                    pb.jsonPath = discoveredPath;
                    pb.schemaResolved = true;
                    refined++;
                    log.debug("Pass 2 (trace) refined: {} stem '{}': '{}' → '{}'",
                            pb.apiKey, stem, oldPath, discoveredPath);
                } else {
                    skipped++;
                    log.trace("Pass 2 (trace): ID not found in response body for {} stem '{}' — keeping '{}'",
                            pb.apiKey, stem, pb.jsonPath);
                }
            }
        }

        log.info("Pass 2 trace-driven refinement complete: {} refined, {} kept heuristic default", refined, skipped);
    }

    /**
     * Parses a real JSON response body and searches for the first field whose
     * name matches an ID pattern for the given entity stem.  Returns the
     * dot-notation JSON path (e.g. {@code "data.id"}).
     *
     * @param jsonBody   the raw JSON response string
     * @param targetStem the normalised entity stem (e.g. "order", "trip")
     * @return the JSON path, or {@code null} if not found
     */
    private static String findIdJsonPathInPayload(String jsonBody, String targetStem) {
        try {
            String trimmed = jsonBody.trim();
            if (trimmed.startsWith("{")) {
                JSONObject root = new JSONObject(trimmed);
                return dfsForIdField(root, targetStem, "", 0);
            } else if (trimmed.startsWith("[")) {
                JSONArray arr = new JSONArray(trimmed);
                if (arr.length() > 0 && arr.get(0) instanceof JSONObject) {
                    return dfsForIdField(arr.getJSONObject(0), targetStem, "[0]", 0);
                }
            }
        } catch (Exception e) {
            log.trace("Failed to parse response body for stem '{}': {}", targetStem, e.getMessage());
        }
        return null;
    }

    /**
     * DFS traversal of a {@link JSONObject} to find the first ID-like field
     * that matches the target entity stem.
     *
     * <p><b>Matching priority per level:</b>
     * <ol>
     *   <li>Exact ID match ({@code id}, {@code uuid}, {@code {stem}Id}, etc.)</li>
     *   <li>Recurse into nested objects</li>
     *   <li>Recurse into array elements (first element only)</li>
     * </ol>
     */
    private static String dfsForIdField(JSONObject obj, String targetStem,
                                        String currentPath, int depth) {
        if (obj == null || depth > MAX_SCHEMA_DEPTH) return null;

        // First pass: check direct properties for an ID match
        for (String key : obj.keySet()) {
            if (isIdMatch(key, targetStem)) {
                String path = currentPath.isEmpty() ? key : currentPath + "." + key;
                return path;
            }
        }

        // Second pass: recurse into nested objects and arrays
        for (String key : obj.keySet()) {
            Object value = obj.opt(key);
            if (value == null) continue;

            String childPath = currentPath.isEmpty() ? key : currentPath + "." + key;

            if (value instanceof JSONObject) {
                String found = dfsForIdField((JSONObject) value, targetStem, childPath, depth + 1);
                if (found != null) return found;
            } else if (value instanceof JSONArray) {
                JSONArray arr = (JSONArray) value;
                if (arr.length() > 0 && arr.get(0) instanceof JSONObject) {
                    String found = dfsForIdField(arr.getJSONObject(0), targetStem,
                            childPath + "[0]", depth + 1);
                    if (found != null) return found;
                }
            }
        }

        return null;
    }

    // ── Dynamic Payload Traverser (public API) ──────────────────────────────

    /**
     * Traverses a real JSON response body using DFS to find the exact JSON path
     * of a leaf node whose string representation matches {@code targetValue}.
     *
     * <p>This is the core of the "Trace-Driven Double Traversal Algorithm":
     * given a concrete value that was observed flowing from Step A to Step B,
     * this method locates where that value lives in Step A's response body,
     * yielding a precise JSON path for runtime extraction.
     *
     * @param jsonBody    the raw JSON response body string from the producer step
     * @param targetValue the concrete value to search for (e.g. "abc-123")
     * @return the dot-notation JSON path (e.g. "data.id"), or {@code null} if not found
     */
    public static String findJsonPathFromRealPayload(String jsonBody, String targetValue) {
        if (jsonBody == null || jsonBody.isBlank()
                || targetValue == null || targetValue.isBlank()) {
            return null;
        }
        try {
            String trimmed = jsonBody.trim();
            if (trimmed.startsWith("{")) {
                JSONObject root = new JSONObject(trimmed);
                return dfsForValue(root, targetValue, "", 0);
            } else if (trimmed.startsWith("[")) {
                JSONArray arr = new JSONArray(trimmed);
                if (arr.length() > 0 && arr.get(0) instanceof JSONObject) {
                    return dfsForValue(arr.getJSONObject(0), targetValue, "[0]", 0);
                }
            }
        } catch (Exception e) {
            log.trace("[Dynamic Path Finder] Failed to parse payload: {}", e.getMessage());
        }
        return null;
    }

    /**
     * DFS traversal that visits every leaf node searching for a value match.
     * Halts and returns the path on first match.
     */
    private static String dfsForValue(JSONObject obj, String targetValue,
                                      String currentPath, int depth) {
        if (obj == null || depth > MAX_SCHEMA_DEPTH) return null;

        for (String key : obj.keySet()) {
            Object value = obj.opt(key);
            if (value == null) continue;

            String childPath = currentPath.isEmpty() ? key : currentPath + "." + key;

            if (value instanceof JSONObject) {
                String found = dfsForValue((JSONObject) value, targetValue, childPath, depth + 1);
                if (found != null) return found;
            } else if (value instanceof JSONArray) {
                JSONArray arr = (JSONArray) value;
                for (int i = 0; i < arr.length(); i++) {
                    Object elem = arr.get(i);
                    if (elem instanceof JSONObject) {
                        String found = dfsForValue((JSONObject) elem, targetValue,
                                childPath + "[" + i + "]", depth + 1);
                        if (found != null) return found;
                    } else if (matchesValue(elem, targetValue)) {
                        return childPath + "[" + i + "]";
                    }
                }
            } else {
                if (matchesValue(value, targetValue)) {
                    return childPath;
                }
            }
        }
        return null;
    }

    /**
     * Compares a JSON leaf value against the target, tolerant of type differences
     * (e.g. numeric 123 vs string "123").
     */
    private static boolean matchesValue(Object jsonValue, String targetValue) {
        if (jsonValue == null || targetValue == null) return false;
        String asString = jsonValue.toString();
        if (asString.equalsIgnoreCase(targetValue)) return true;
        // Numeric tolerance: "123.0" should match "123"
        try {
            double jsonNum = Double.parseDouble(asString);
            double targetNum = Double.parseDouble(targetValue);
            return Double.compare(jsonNum, targetNum) == 0;
        } catch (NumberFormatException ignored) {
            // not numeric, string comparison was definitive
        }
        return false;
    }

    /**
     * Checks whether a property name represents an ID field for the given entity stem.
     *
     * <p>Matches (case-insensitive):
     * <ul>
     *   <li>{@code id}, {@code uuid}</li>
     *   <li>{@code {stem}Id}, {@code {stem}_id}, {@code {stem}UUID}</li>
     * </ul>
     */
    private static boolean isIdMatch(String propName, String targetStem) {
        if (propName == null) return false;
        String lower = propName.toLowerCase(Locale.ROOT);

        if ("id".equals(lower) || "uuid".equals(lower)) return true;

        String stemLower = targetStem.toLowerCase(Locale.ROOT);
        if (lower.equals(stemLower + "id")
                || lower.equals(stemLower + "_id")
                || lower.equals(stemLower + "uuid")
                || lower.equals(stemLower + "_uuid")) {
            return true;
        }

        return false;
    }

    /**
     * Collects all {@link WorkflowStep} nodes from a list of scenarios into a
     * flat list, recursively including children.
     */
    public static List<WorkflowStep> flattenAllSteps(List<WorkflowScenario> scenarios) {
        List<WorkflowStep> all = new ArrayList<>();
        for (WorkflowScenario scenario : scenarios) {
            for (WorkflowStep root : scenario.getRootSteps()) {
                collectStepsRecursively(root, all);
            }
        }
        return all;
    }

    private static void collectStepsRecursively(WorkflowStep step, List<WorkflowStep> acc) {
        acc.add(step);
        if (step.getChildren() != null) {
            for (WorkflowStep child : step.getChildren()) {
                collectStepsRecursively(child, acc);
            }
        }
    }

    // ── internals ──────────────────────────────────────────────────────────

    /** POST endpoints whose trailing segment matches one of these verbs are
     *  recognised as authentication producers — they return a session/login
     *  identifier as part of their success response. This is a carve-out from
     *  {@link #isLikelyEntityNoun} (which otherwise blacklists these as
     *  actions rather than entities). */
    private static final Set<String> AUTH_VERBS = new HashSet<>(Arrays.asList(
            "login", "authenticate", "signin"));

    private static void registerProducers(SemanticDependencyRegistry reg,
                                          String svcName, String apiKey,
                                          String method, String path,
                                          Operation op) {
        // Heuristic 1: POST or PUT on an entity resource produces that entity's ID.
        // e.g. POST /api/v1/orderservice/order  → produces "orderId" at "data.id"
        //      POST /api/v1/consignservice/consigns → produces "consignId"
        if ("post".equals(method) || "put".equals(method)) {
            String resourceNoun = extractTrailingNoun(path);
            if (resourceNoun != null && isLikelyEntityNoun(resourceNoun)) {
                String stem = normaliseNounToStem(resourceNoun);
                if (stem != null) {
                    ProducerBinding pb = new ProducerBinding(svcName, apiKey, "data.id", method, /*heuristic*/ 1);
                    reg.producersByIdStem
                            .computeIfAbsent(stem, k -> new ArrayList<>())
                            .add(pb);
                    log.debug("Producer H1 registered: {} ({}) → stem '{}'", apiKey, svcName, stem);
                }
            }
        }

        // Heuristic 1-auth: POST to an authentication verb (login / authenticate /
        // signin) produces a session identifier. Stem equals the verb; the default
        // jsonPath follows the "data.<verb>Id" convention (e.g. "data.loginId").
        if ("post".equals(method)) {
            String tail = extractTrailingNoun(path);
            if (tail != null) {
                String lc = tail.toLowerCase(Locale.ROOT);
                if (AUTH_VERBS.contains(lc)) {
                    ProducerBinding pb = new ProducerBinding(svcName, apiKey,
                            "data." + lc + "Id", method, /*heuristic*/ 1);
                    reg.producersByIdStem
                            .computeIfAbsent(lc, k -> new ArrayList<>())
                            .add(pb);
                    log.debug("Producer H1-auth registered: {} ({}) → stem '{}'", apiKey, svcName, lc);
                }
            }
        }

        // Heuristic 2 (restricted): reverse-engineer producers from operations
        // that carry an ID-like path parameter. Only POST/PUT qualify — GET is a
        // reader and DELETE is a destroyer, neither produces fresh IDs, so
        // allowing those created false-positive producers (e.g. a GET-by-id was
        // being registered as the producer of that id).
        if (("post".equals(method) || "put".equals(method)) && op.getTestParameters() != null) {
            for (TestParameter tp : op.getTestParameters()) {
                String pName = tp.getName();
                if (pName == null || !isIdLikeParam(pName)) continue;
                if (!"path".equalsIgnoreCase(tp.getIn())) continue;

                String stem = normaliseIdStem(pName);
                if (!reg.producersByIdStem.containsKey(stem)) {
                    String jsonPath = "data." + pName;
                    ProducerBinding pb = new ProducerBinding(svcName, apiKey, jsonPath, method, /*heuristic*/ 2);
                    reg.producersByIdStem
                            .computeIfAbsent(stem, k -> new ArrayList<>())
                            .add(pb);
                }
            }
        }
    }

    /**
     * Reject path segments that are clearly actions, queries, or modifiers rather than
     * entity resource names.  This prevents garbage stems like {@code cheapest},
     * {@code refresh}, {@code money}, {@code byIds} from polluting the registry
     * (they cannot be anyone's producer).
     */
    private static boolean isLikelyEntityNoun(String segment) {
        if (segment == null || segment.isEmpty()) return false;
        String lc = segment.toLowerCase(Locale.ROOT);
        // Verbs/modifiers that commonly appear as trailing path segments but are NOT entities.
        switch (lc) {
            case "cheapest": case "quickest":
            case "refresh":  case "refreshed":
            case "login":    case "logout":   case "register":   case "signup":
            case "query":    case "search":   case "find":       case "lookup":
            case "byid":     case "byids":    case "byname":
            case "money":    case "amount":   case "balance":
            case "idlist":   case "namelist":
            case "left":
            case "error":    case "success":
                return false;
        }
        // Paths ending with common success/notification event names
        if (lc.endsWith("_success") || lc.endsWith("_cancel_success")
                || lc.endsWith("_change_success") || lc.endsWith("_create_success")) {
            return false;
        }
        return true;
    }

    /**
     * Extracts the trailing resource noun from a REST path, ignoring path parameters.
     * <pre>
     *   /api/v1/orderservice/order         → "order"
     *   /api/v1/consignservice/consigns    → "consigns"
     *   /api/v1/orderservice/order/{id}    → "order"  (skips the {id} segment)
     * </pre>
     */
    static String extractTrailingNoun(String path) {
        if (path == null || path.isEmpty()) return null;
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            String seg = segments[i].trim();
            if (seg.isEmpty() || seg.startsWith("{")) continue;
            if (seg.matches("v\\d+|api|actuator")) continue;
            return seg;
        }
        return null;
    }

    /**
     * Normalises a resource noun to a canonical stem used as the dictionary key.
     * <pre>
     *   "orders" → "order", "consigns" → "consign", "trips" → "trip"
     *   "success" → "success"  (NOT "succes" — ends with "ss")
     *   "status"  → "status"   (NOT "statu"  — ends with "us")
     *   "business"→ "business" (NOT "busines"— ends with "ss")
     * </pre>
     * The rule: only strip a trailing 's' when it looks like a plural form.  English
     * plural heuristic: preceding char is a consonant that can form a regular plural,
     * AND the previous char is NOT another 's' (guards against "ss"), NOT 'u' (guards
     * against "us" words like "status"), NOT 'i' (guards against Latin plurals like
     * "genesis").  Simpler rule: keep the word unchanged if the last two chars are
     * one of {ss, us, is}.  Plural "s" after vowels (orders, prices, trips, consigns)
     * still gets stripped.
     */
    static String normaliseNounToStem(String noun) {
        if (noun == null || noun.isEmpty()) return null;
        String lower = noun.toLowerCase(Locale.ROOT);
        if (lower.length() > 2 && lower.endsWith("s")) {
            String last2 = lower.substring(lower.length() - 2);
            boolean isPluralSafe = !last2.equals("ss")
                                && !last2.equals("us")
                                && !last2.equals("is");
            if (isPluralSafe) {
                lower = lower.substring(0, lower.length() - 1);
            }
        }
        return lower;
    }

    /**
     * Normalises a parameter name like "orderId" or "trip_uuid" to its entity stem.
     * <pre>
     *   "orderId"    → "order"
     *   "tripId"     → "trip"
     *   "accountId"  → "account"
     *   "contactsId" → "contact"
     * </pre>
     */
    public static String normaliseIdStem(String paramName) {
        if (paramName == null) return null;

        // Prefix form first (id_account, idAccount, uuid_order, uuidOrder)
        java.util.regex.Matcher pre = ID_PREFIX.matcher(paramName);
        if (pre.matches()) {
            String raw = pre.group(1) != null ? pre.group(1) : pre.group(2);
            return pluralSafeStem(raw.toLowerCase(Locale.ROOT));
        }

        // Suffix form (orderId, account_id, trip_uuid, clientUUID)
        // Require the same boundary as ID_SUFFIX so words like "paid"/"valid"/"void" do not
        // produce stems "pa"/"val"/"vo".
        if (!ID_SUFFIX.matcher(paramName).matches()) return null;
        String stem = ID_SUFFIX_STRIP.matcher(paramName).replaceFirst("")
                .replaceAll("_$", "");
        if (stem.isEmpty()) return null;
        return pluralSafeStem(stem.toLowerCase(Locale.ROOT));
    }

    /**
     * Common English non-plural words ending in 's' that must not be stem-stripped.
     * Hard-coded list of low-coverage but frequently-encountered cases. (A proper
     * Porter / Lancaster stemmer would be heavier than warranted here.)
     */
    private static final java.util.Set<String> NON_PLURAL_S_WORDS = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "address", "news", "bus", "gas", "boss", "loss", "pass", "class",
                    "miss", "kiss", "less", "press", "process", "stress", "access",
                    "atlas", "canvas", "chaos", "lens", "series", "species", "logos",
                    "campus", "focus", "menus", "virus", "thus", "plus", "minus", "bonus"
            ));

    /**
     * Strip a trailing {@code s} only when it forms a regular English plural.
     * Protects words ending in {@code ss} / {@code us} / {@code is} / {@code os} / {@code as}
     * ({@code success}, {@code status}, {@code analysis}, {@code logos}, {@code atlas}) and a
     * curated list of common non-plurals ({@code news}, {@code bus}, {@code address}, ...).
     */
    private static String pluralSafeStem(String stem) {
        if (stem.length() > 2 && stem.endsWith("s")) {
            String last2 = stem.substring(stem.length() - 2);
            boolean protectedSuffix = last2.equals("ss") || last2.equals("us")
                    || last2.equals("is") || last2.equals("os") || last2.equals("as");
            if (!protectedSuffix && !NON_PLURAL_S_WORDS.contains(stem)) {
                stem = stem.substring(0, stem.length() - 1);
            }
        }
        return stem;
    }

    /**
     * True when a parameter name follows a recognised ID naming convention —
     * either the suffix form ({@code orderId}, {@code account_id}) or the
     * prefix form ({@code id_account}, {@code idAccount}). Bare {@code id} /
     * {@code uuid} path parameters are handled separately by
     * {@link #isGenericIdParam}, with stem derived from the surrounding path.
     */
    public static boolean isIdLikeParam(String paramName) {
        if (paramName == null) return false;
        return ID_SUFFIX.matcher(paramName).matches()
            || ID_PREFIX.matcher(paramName).matches();
    }

    /**
     * Infer an entity stem from the segment preceding a path parameter.
     *
     * <p>Used when the parameter name is generic ({@code id}, {@code uuid}) and
     * carries no entity info by itself. REST convention puts the entity just
     * before the ID segment: {@code /consigns/account/{id}} ⇒ stem {@code account},
     * {@code /consigns/order/{id}} ⇒ stem {@code order}.
     *
     * <p>Returns {@code null} when the path does not contain the parameter, the
     * preceding segment is missing, or the preceding segment fails the
     * entity-noun filter (so action-like segments like {@code refresh} or
     * {@code byIds} do not contaminate the dictionary).
     */
    public static String inferStemFromPathSegment(String path, String paramName) {
        if (path == null || paramName == null) return null;
        String token = "{" + paramName + "}";
        int idx = path.indexOf(token);
        if (idx < 0) return null;
        String prefix = path.substring(0, idx);
        while (prefix.endsWith("/")) prefix = prefix.substring(0, prefix.length() - 1);
        int lastSlash = prefix.lastIndexOf('/');
        if (lastSlash < 0) return null;
        String prev = prefix.substring(lastSlash + 1);
        if (prev.isEmpty() || prev.startsWith("{")) return null;
        if (!isLikelyEntityNoun(prev)) return null;
        return normaliseNounToStem(prev);
    }

    /**
     * True when a parameter name is generic (not carrying its own entity info).
     * For such params, stem inference falls back to the surrounding path context.
     */
    public static boolean isGenericIdParam(String paramName) {
        if (paramName == null) return false;
        String lc = paramName.toLowerCase(Locale.ROOT);
        return lc.equals("id") || lc.equals("uuid");
    }
}
