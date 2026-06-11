package io.mist.core.workflow.pipeline.stages;

import io.mist.core.spec.Operation;
import io.mist.core.spec.TestConfigurationObject;
import io.mist.core.spec.TestParameter;
import io.mist.core.llm.ParameterInfo;
import io.mist.core.workflow.WorkflowScenario;
import io.mist.core.workflow.WorkflowStep;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Package-private collection of pure helpers shared across the pipeline stages.
 *
 * <p>The methods were previously {@code private} instance methods on
 * {@code MultiServiceTestCaseGenerator}; lifting them here as {@code static}
 * lets the stage classes call them without holding a generator reference and
 * keeps the helpers reusable across stages without duplication.
 *
 * <p>All methods are pure functions of their parameters — no field state is
 * threaded through this class.
 */
public final class StageSupport {
    private static final Logger log = LogManager.getLogger(StageSupport.class);

    /** Matches operation names of the form {@code "VERB /path"}. */
    static final Pattern HTTP_OPERATION_PATTERN =
            Pattern.compile("^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+(.+)$",
                    Pattern.CASE_INSENSITIVE);

    private StageSupport() {}

    /**
     * Extract root API key (method_path) from a scenario's first business operation.
     */
    static String getRootApiKey(WorkflowScenario scenario) {
        for (WorkflowStep rootStep : scenario.getRootSteps()) {
            String apiKey = extractRootApiFromStep(rootStep);
            if (apiKey != null) {
                return apiKey;
            }
        }
        return null;
    }

    /**
     * Recursively find the first business API operation and return method_path key.
     */
    static String extractRootApiFromStep(WorkflowStep step) {
        String opName = step.getOperationName();
        String serviceName = step.getServiceName();

        if (opName != null && serviceName != null) {
            String opLower = opName.toLowerCase();
            String serviceLower = serviceName.toLowerCase();

            boolean isLoginAuth = opLower.contains("login") || opLower.contains("auth") ||
                                  serviceLower.contains("login") || serviceLower.contains("auth") ||
                                  opLower.contains("signin") || opLower.contains("token");

            boolean isGateway = serviceLower.contains("gateway") ||
                               opName.equals("POST /*") || opName.equals("GET /*") ||
                               opName.equals("PUT /*") || opName.equals("DELETE /*");

            if (!isLoginAuth && !isGateway) {
                String verb = null, route = null;

                Matcher httpMatcher = HTTP_OPERATION_PATTERN.matcher(opName);
                if (httpMatcher.matches()) {
                    verb = httpMatcher.group(1).toLowerCase();
                    route = httpMatcher.group(2);
                } else {
                    Pattern servicePattern = Pattern.compile(".*?\\s+(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
                    Matcher serviceMatcher = servicePattern.matcher(opName);
                    if (serviceMatcher.matches()) {
                        verb = serviceMatcher.group(1).toLowerCase();
                        route = serviceMatcher.group(2);
                    } else {
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
                    String normalizedRoute = route.replaceAll("[^a-zA-Z0-9_]", "_");
                    return verb.toUpperCase() + "_" + normalizedRoute;
                }
            }
        }

        for (WorkflowStep child : step.getChildren()) {
            String apiKey = extractRootApiFromStep(child);
            if (apiKey != null) {
                return apiKey;
            }
        }

        return null;
    }

    /** Extract the URL path component for the {@code http.url} fallback. */
    static String extractPathFromUrl(String url) {
        if (url == null) return null;
        try {
            java.net.URL parsed = new java.net.URL(url);
            return parsed.getPath();
        } catch (Exception e) {
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

    /** Find the first business step (non-login) in a scenario. */
    static WorkflowStep findFirstBusinessStep(WorkflowScenario scenario) {
        for (WorkflowStep rootStep : scenario.getRootSteps()) {
            WorkflowStep businessStep = findFirstBusinessStepRecursive(rootStep);
            if (businessStep != null) {
                return businessStep;
            }
        }
        return null;
    }

    /** Recursively find the first business step. */
    public static WorkflowStep findFirstBusinessStepRecursive(WorkflowStep step) {
        String opName = step.getOperationName();
        String serviceName = step.getServiceName();

        if (opName != null && serviceName != null) {
            String opLower = opName.toLowerCase();
            String serviceLower = serviceName.toLowerCase();

            boolean isLoginAuth = opLower.contains("login") || opLower.contains("auth") ||
                                  serviceLower.contains("login") || serviceLower.contains("auth") ||
                                  opLower.contains("signin") || opLower.contains("token");

            boolean isGateway = serviceLower.contains("gateway") ||
                               opName.equals("POST /*") || opName.equals("GET /*") ||
                               opName.equals("PUT /*") || opName.equals("DELETE /*");

            if (!isLoginAuth && !isGateway) {
                return step;
            }
        }

        for (WorkflowStep child : step.getChildren()) {
            WorkflowStep businessStep = findFirstBusinessStepRecursive(child);
            if (businessStep != null) {
                return businessStep;
            }
        }

        return null;
    }

    /** Locate the corresponding Operation object by method + path. */
    public static Operation findOperation(TestConfigurationObject cfg, String verb, String path) {
        if (cfg.getTestConfiguration() == null ||
                cfg.getTestConfiguration().getOperations() == null) {
            return null;
        }

        Operation exact = cfg.getTestConfiguration().getOperations().stream()
                .filter(o -> verb.equalsIgnoreCase(o.getMethod()) &&
                        path.equals(o.getTestPath()))
                .findFirst().orElse(null);
        if (exact != null) return exact;

        // Path-template tolerant fallback: a trace step may carry a literal
        // path like /admintravel/G1235 while the OpenAPI testPath is the
        // templated /admintravel/{tripId}. Match each {param} segment against
        // [^/]+ so pool generation succeeds for path-parameterised endpoints.
        return cfg.getTestConfiguration().getOperations().stream()
                .filter(o -> verb.equalsIgnoreCase(o.getMethod()) &&
                        pathMatchesTemplate(path, o.getTestPath()))
                .findFirst().orElse(null);
    }

    /**
     * Result of {@link #resolveOperation}: the matched conf together with its
     * service key and the {@link Operation} within it. {@code null} is returned
     * from {@code resolveOperation} when nothing matched, so a non-null holder
     * always carries a non-null {@link #op}.
     */
    public static final class ResolvedOperation {
        public final String serviceKey;
        public final TestConfigurationObject cfg;
        public final Operation op;
        ResolvedOperation(String serviceKey, TestConfigurationObject cfg, Operation op) {
            this.serviceKey = serviceKey;
            this.cfg = cfg;
            this.op = op;
        }
    }

    /**
     * Counts how many times the endpoint (HTTP method + path) fallback in
     * {@link #resolveOperation} actually fired — i.e. a step was matched to a
     * conf operation in a service whose KEY differs from the trace's service
     * name. For train-ticket this MUST stay 0 (trace service names equal conf
     * service names, so the service-name match always wins first); a non-zero
     * value on train-ticket signals a regression. Reset between runs via
     * {@link #resetEndpointFallbackCount()}.
     */
    private static final AtomicInteger ENDPOINT_FALLBACK_COUNT = new AtomicInteger(0);

    /** Number of times the endpoint fallback fired since the last reset. */
    public static int getEndpointFallbackCount() {
        return ENDPOINT_FALLBACK_COUNT.get();
    }

    /** Reset the endpoint-fallback counter (call once at the start of a run). */
    public static void resetEndpointFallbackCount() {
        ENDPOINT_FALLBACK_COUNT.set(0);
    }

    /**
     * Resolve a scenario step (service + HTTP verb + concrete route) to a conf
     * {@link Operation}, generalising MIST generation across SUTs whose trace
     * service names differ from the OpenAPI conf service keys (e.g. Bookinfo's
     * trace service {@code productpage.default} vs conf key {@code productpage}).
     *
     * <p><b>Service-name match is ALWAYS tried first</b>, so train-ticket — whose
     * trace service names equal its conf keys — never reaches the fallback and
     * its generated output is byte-identical:
     * <ol>
     *   <li><b>(a) Service-name match:</b> {@code cfg = serviceConfigs.get(service)};
     *       if non-null and {@link #findOperation}(cfg, verb, route) is non-null,
     *       return it.</li>
     *   <li><b>(b) Endpoint fallback</b> (only when (a) misses — {@code cfg==null}
     *       OR no op in that cfg): across ALL configs collect every op with
     *       {@code method==verb} AND (exact path OR {@link #pathMatchesTemplate}).
     *       If exactly 1 candidate → use it. If >1 → keep only those whose service
     *       KEY is a prefix of the trace {@code service} (e.g. key {@code productpage}
     *       is a prefix of {@code productpage.default}); if that narrows to exactly
     *       1 → use it; otherwise log WARN with all candidate services and return
     *       {@code null} (never silently pick). Each successful fallback increments
     *       {@link #ENDPOINT_FALLBACK_COUNT} and logs one INFO line.</li>
     *   <li><b>(c)</b> nothing matched → {@code null}.</li>
     * </ol>
     *
     * @return a non-null {@link ResolvedOperation}, or {@code null} if no
     *         operation could be resolved.
     */
    public static ResolvedOperation resolveOperation(
            Map<String, TestConfigurationObject> serviceConfigs,
            String service, String verb, String route) {

        if (serviceConfigs == null || verb == null || route == null) return null;

        // (a) Service-name match FIRST (train-ticket always lands here).
        if (service != null) {
            TestConfigurationObject cfg = serviceConfigs.get(service);
            if (cfg != null) {
                Operation op = findOperation(cfg, verb, route);
                if (op != null) {
                    return new ResolvedOperation(service, cfg, op);
                }
            }
        }

        // (b) ENDPOINT FALLBACK — service-name match missed. Collect every conf
        // operation that matches by HTTP method + (exact|template) path.
        List<ResolvedOperation> candidates = new ArrayList<>();
        for (Map.Entry<String, TestConfigurationObject> e : serviceConfigs.entrySet()) {
            Operation op = findOperation(e.getValue(), verb, route);
            if (op != null) {
                candidates.add(new ResolvedOperation(e.getKey(), e.getValue(), op));
            }
        }
        if (candidates.isEmpty()) {
            return null; // (c)
        }

        ResolvedOperation chosen = null;
        if (candidates.size() == 1) {
            chosen = candidates.get(0);
        } else {
            // >1 service exposes this endpoint: disambiguate by service-name
            // affinity — keep only configs whose KEY is a prefix of the trace
            // service name (conf "productpage" ⊂ trace "productpage.default").
            List<ResolvedOperation> byPrefix = new ArrayList<>();
            if (service != null) {
                for (ResolvedOperation c : candidates) {
                    if (c.serviceKey != null && service.startsWith(c.serviceKey)) {
                        byPrefix.add(c);
                    }
                }
            }
            if (byPrefix.size() == 1) {
                chosen = byPrefix.get(0);
            } else {
                List<String> names = new ArrayList<>();
                for (ResolvedOperation c : candidates) names.add(c.serviceKey);
                log.warn("Endpoint fallback ambiguous for {} {} (trace service '{}'): "
                        + "{} configs expose it {} and prefix-affinity did not narrow to one; "
                        + "skipping (no silent pick).",
                        verb, route, service, candidates.size(), names);
                return null;
            }
        }

        int n = ENDPOINT_FALLBACK_COUNT.incrementAndGet();
        log.info("Endpoint fallback #{}: matched {} {} (trace service '{}') to conf service '{}' "
                + "by HTTP method+path (service-name match missed).",
                n, verb, route, service, chosen.serviceKey);
        return chosen;
    }

    /**
     * Does a concrete request path match an OpenAPI path template?
     *
     * <p>Used by {@link #findOperation} (and the endpoint-based
     * {@link #resolveOperation} fallback) so a trace step carrying a literal
     * path like {@code /products/0/ratings} matches the templated
     * {@code /products/{id}/ratings} from the OpenAPI conf. Train-ticket is
     * unaffected: its paths have no path-params, so the exact-equals branch in
     * {@link #findOperation} matches first and this is never the deciding test.
     *
     * <p>Matching rules:
     * <ul>
     *   <li>Strip any {@code ?query} suffix and a single trailing {@code /}
     *       from both inputs before comparing.</li>
     *   <li>Split both on {@code /}; require an equal segment count.</li>
     *   <li>Each template segment matches iff it is a {@code {...}} placeholder
     *       (then it matches any non-empty segment, which by construction
     *       contains no {@code /}) OR it is literally equal to the concrete
     *       segment.</li>
     * </ul>
     */
    public static boolean pathMatchesTemplate(String concretePath, String templatePath) {
        if (concretePath == null || templatePath == null) return false;

        String concrete = normalisePathForMatch(concretePath);
        String template = normalisePathForMatch(templatePath);

        String[] concreteSeg = concrete.split("/", -1);
        String[] templateSeg = template.split("/", -1);
        if (concreteSeg.length != templateSeg.length) return false;

        for (int i = 0; i < templateSeg.length; i++) {
            String t = templateSeg[i];
            String c = concreteSeg[i];
            boolean isPlaceholder = t.startsWith("{") && t.endsWith("}") && t.length() > 2;
            if (isPlaceholder) {
                // A placeholder matches any non-empty segment. Segments cannot
                // contain '/' (we split on it), so the only extra check is
                // non-emptiness (reject e.g. /products//ratings vs /products/{id}/ratings).
                if (c.isEmpty()) return false;
            } else if (!t.equals(c)) {
                return false;
            }
        }
        return true;
    }

    /** Strip a {@code ?query} suffix and a single trailing {@code /} (but keep a lone root "/"). */
    private static String normalisePathForMatch(String path) {
        String p = path;
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);
        if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    /** Build a {@link ParameterInfo} with extended context for richer LLM prompts. */
    static ParameterInfo createParameterInfoWithContext(TestParameter p, String apiName,
                                                       String serviceName, List<String> allParamNames) {
        ParameterInfo info = createParameterInfo(p);
        info.setApiName(apiName);
        info.setServiceName(serviceName);
        info.setAllParameterNames(allParamNames);
        return info;
    }

    private static ParameterInfo createParameterInfo(TestParameter p) {
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
        info.setEnumValues(p.getEnumValues());
        info.setMinimum(p.getMinimum());
        info.setMaximum(p.getMaximum());
        info.setMinLength(p.getMinLength());
        info.setMaxLength(p.getMaxLength());
        return info;
    }

    /** Read variant count from properties file with fallback to defaults. */
    static int getVariantCountFromProperties() {
        try {
            String testsProp = System.getProperty("testsperoperation");
            if (testsProp != null) {
                int count = Integer.parseInt(testsProp);
                log.info("Using testsperoperation from properties: {}", count);
                return count;
            }

            String variantsProp = System.getProperty("test.variants.per.scenario");
            if (variantsProp != null) {
                int count = Integer.parseInt(variantsProp);
                log.info("Using test.variants.per.scenario from properties: {}", count);
                return count;
            }

            int defaultCount = 1;
            log.warn("No variant count found in properties, using default: {}", defaultCount);
            return defaultCount;
        } catch (NumberFormatException e) {
            int defaultCount = 1;
            log.warn("Invalid variant count in properties, using default: {} (error: {})", defaultCount, e.getMessage());
            return defaultCount;
        }
    }

    /** Pool sizing heuristic — scales with parameter cardinality. */
    static int computeTargetPoolSize(int numParams, int variantCount) {
        if (numParams <= 1) {
            return variantCount + 10;
        } else if (numParams == 2) {
            return Math.max(25, (int) Math.sqrt(variantCount) * 2);
        } else if (numParams <= 5) {
            return Math.max(20, (int) Math.ceil(Math.pow(variantCount, 1.0 / numParams)) + 5);
        } else {
            return 15;
        }
    }

    /** Type-aware fallback padding value, parseable as the parameter's declared type. */
    static String typeAwareFallbackValue(TestParameter p, int idx) {
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

    /** Normalise an OpenAPI parameter location to one of path|query|header|cookie|body|other. */
    public static String normaliseParamLocation(String in) {
        if (in == null || in.trim().isEmpty()) return "body";
        String lower = in.trim().toLowerCase(Locale.ROOT);
        switch (lower) {
            case "path":
            case "query":
            case "header":
            case "cookie":
            case "body":
                return lower;
            case "formdata":
                return "body";
            default:
                return "other";
        }
    }
}
