package io.mist.cli.writer;

import io.mist.core.spec.Operation;
import io.mist.core.util.ConsoleProgressBar;
import io.mist.core.testcase.MultiServiceTestCase;
import io.mist.core.testcase.TestCase;
import io.mist.core.util.RESTestException;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.json.JSONObject;
import org.json.JSONArray;
import io.mist.core.analysis.TraceErrorAnalyzer;



/**
 * Writes a JUnit/REST‑assured test‑suite that replays a
 * {@link MultiServiceTestCase}.
 */
public class MultiServiceRESTAssuredWriter {

    /* ------------------------------------------------------------------ */
    private final String outputDir;
    private final String packageName;
    private final String baseURI;
    /** Test class-name prefix; was inherited from RESTAssuredWriter. */
    protected String testClassName;

    private boolean loggingEnabled        = false;
    private boolean allureReport          = false;
    private boolean statsEnabled          = false;
    private boolean outputCoverageEnabled = false;
    private String  proxyHostPort         = null;

    /**
     * Trace Shape Oracle handle passed in by {@code MistRunner.run()}. The
     * writer does not invoke {@code evaluate(...)} directly; it only needs to
     * (1) know the oracle was bootstrapped so generated tests can read the
     * persisted store, and (2) emit a static field + @BeforeClass init that
     * reconstructs the same oracle in the generated test class's JVM.
     *
     * <p>The store path mirrors what MistRunner wrote so the generated test
     * loads the exact same invariants this run trained. A null oracle (e.g.
     * tests that bypass the runner) suppresses the emission cleanly.
     */
    private io.mist.core.oracle.shape.TraceShapeOracle traceShapeOracle = null;
    private String traceShapeStorePath = null;

    /* ------------------------------------------------------------------ */
    public MultiServiceRESTAssuredWriter(String openAPIPath,
                                         String testConfPath,
                                         String outputDir,
                                         String className,
                                         String packageName,
                                         String baseURI,
                                         boolean logToFile) {

        // RESTAssuredWriter inheritance is severed: this writer is a
        // pure MIST output formatter. The seven constructor args are
        // captured locally; the parent's state (spec / conf parsing,
        // OAI-validation flag, log-to-file flag, stateful-filter flag,
        // etc.) was never read by the override paths below, so dropping
        // them is safe.
        this.outputDir    = outputDir;
        this.packageName  = packageName;
        this.baseURI      = baseURI;

        this.testClassName = className;
    }

    /* ---------- feature toggles (parent inheritance severed) ----------- */
    public void setLogging(boolean logging)                    { this.loggingEnabled        = logging; }
    public void setAllureReport(boolean allure)                { this.allureReport          = allure;  }
    public void setEnableStats(boolean enableStats)            { this.statsEnabled          = enableStats; }
    public void setEnableOutputCoverage(boolean enableOutput)  { this.outputCoverageEnabled = enableOutput; }
    public void setProxy(String proxy)                         { this.proxyHostPort         = proxy; }

    /**
     * Inject the Trace Shape Oracle and the on-disk store path used by
     * MistRunner so the writer can emit a re-construction block in the
     * generated test class. The oracle reference itself is not consumed by
     * the writer (the generated tests run in a separate JVM and reload the
     * store from disk), but accepting it preserves the contract MistRunner
     * documents and lets the writer skip emission when no oracle was set up.
     */
    public void setTraceShapeOracle(io.mist.core.oracle.shape.TraceShapeOracle oracle, java.nio.file.Path storePath) {
        this.traceShapeOracle = oracle;
        this.traceShapeStorePath = storePath == null ? null : storePath.toString();
    }


    /*  WRITE JAVA SOURCE                                           */
    public void write(Collection<TestCase> testCases) {
        if (testCases == null || testCases.isEmpty()) return;

        // Group test cases by scenario name
        Map<String, List<TestCase>> byScenario = new LinkedHashMap<>();
        for (TestCase tc : testCases) {
            if (tc instanceof MultiServiceTestCase) {
                String sc = ((MultiServiceTestCase) tc).getScenarioName();
                if (sc == null || sc.isEmpty()) sc = "Scenario";
                byScenario.computeIfAbsent(sc, k -> new ArrayList<>()).add(tc);
            }
        }

        ConsoleProgressBar.begin("Writing Tests", byScenario.size());
        for (Map.Entry<String, List<TestCase>> entry : byScenario.entrySet()) {
            try {
                writeTestSuite(entry.getValue(), sanitize(entry.getKey()));
                ConsoleProgressBar.update(entry.getKey());
        } catch (RESTestException e) {
            throw new RuntimeException("Error writing multi‑service test suite", e);
            }
        }
        ConsoleProgressBar.complete();
    }

    private void writeTestSuite(Collection<TestCase> testCases, String className) throws RESTestException {

        if (testCases == null || testCases.isEmpty()) return;

        try {
            // Create directory structure that matches package structure
            // Package: packageName.testClassName -> Directory: packageName/testClassName
            String packagePath = packageName.replace('.', '/');
            File packageDir = new File(outputDir, packagePath);
            File testClassDir = new File(packageDir, this.testClassName);
            if (!testClassDir.exists()) testClassDir.mkdirs();

            File javaFile = new File(testClassDir, className + ".java");

            try (PrintWriter pw = new PrintWriter(new FileWriter(javaFile))) {

                /* ---------- package & imports ------------------------------------ */
                if (packageName != null && !packageName.isEmpty()) {
                    // Use unique package name to avoid duplicate class issues across test runs
                    String uniquePackageName = packageName + "." + this.testClassName;
                    pw.println("package " + uniquePackageName + ";");
                    pw.println();
                }

                pw.println("import io.restassured.RestAssured;");
                pw.println("import io.restassured.response.Response;");
                pw.println("import io.restassured.specification.RequestSpecification;");
                pw.println("import org.junit.BeforeClass;");
                pw.println("import org.junit.Test;");
                pw.println("import org.junit.AssumptionViolatedException;");
                pw.println("import java.util.concurrent.atomic.AtomicBoolean;");
                pw.println("import java.util.concurrent.atomic.AtomicInteger;");
                pw.println("import java.util.Map;");
                pw.println("import java.util.*;");
                pw.println("import java.net.URLEncoder;");
                pw.println("import java.nio.charset.StandardCharsets;");
                pw.println("import org.hamcrest.Matchers;");
                pw.println("import java.net.http.HttpClient;");
                pw.println("import java.net.http.HttpRequest;");
                pw.println("import java.net.http.HttpResponse;");
                pw.println("import org.json.JSONObject;");
                pw.println("import org.json.JSONArray;");
                pw.println("import io.mist.core.analysis.TraceErrorAnalyzer;");
                pw.println("import io.mist.core.analysis.TraceShapeAdapter;");
                pw.println("import io.mist.core.smart.ParameterErrorAnalyzer;");
                pw.println("import io.mist.core.smart.InputFetchRegistry;");
                pw.println("import io.mist.core.smart.ParameterError;");
                pw.println("import io.mist.core.oracle.shape.ShapeInvariantStore;");
                pw.println("import io.mist.core.oracle.shape.TraceModel;");
                pw.println("import io.mist.core.oracle.shape.TraceShapeOracle;");
                pw.println("import io.mist.core.oracle.shape.TraceShapeVerdict;");
                pw.println("import static org.junit.Assert.*;");
                pw.println("import io.mist.core.testcase.MultiServiceTestCase;");

                if (allureReport) {
                    pw.println("import io.qameta.allure.Allure;");
                    pw.println("import io.qameta.allure.Epic;");
                    pw.println("import io.qameta.allure.Feature;");
                    // AllureRestAssured filter removed - causes duplicate request logging
                    // pw.println("import io.qameta.allure.restassured.AllureRestAssured;");
                    pw.println("import io.qameta.allure.model.Status;");
                }
                pw.println();

                /* ---------- class-level Allure annotations ---------------------- */
                if (allureReport) {
                    String epicLabel;
                    String featureLabel;
                    String classDisplayName;

                    MultiServiceTestCase representative = null;
                    for (TestCase _tc : testCases) {
                        if (_tc instanceof MultiServiceTestCase) {
                            representative = (MultiServiceTestCase) _tc;
                            break;
                        }
                    }

                    if (representative != null && !representative.getSteps().isEmpty()) {
                        boolean isMultiRoot = representative.getSteps().stream()
                                .filter(MultiServiceTestCase.StepCall::isTopLevelRoot).count() > 1;
                        epicLabel = isMultiRoot ? "Integration Flow Tests" : "Baseline Tests";

                        String flowPath = buildFlowPath(representative);
                        featureLabel = representative.getSteps().get(0).getServiceName();
                        if (featureLabel == null || featureLabel.isEmpty()) featureLabel = "Unknown Service";

                        String scenarioName = representative.getScenarioName();
                        classDisplayName = (scenarioName != null ? scenarioName : className)
                                + " | " + flowPath;
                    } else {
                        epicLabel = "Baseline Tests";
                        featureLabel = "Unknown Service";
                        classDisplayName = className;
                    }

                    pw.println("@Epic(\"" + escape(epicLabel) + "\")");
                    pw.println("@Feature(\"" + escape(featureLabel) + "\")");
                    pw.println("@io.qameta.allure.junit4.DisplayName(\"" + escape(classDisplayName) + "\")");
                }

                /* ---------- class header ---------------------------------------- */
                pw.println("public class " + className + " {");
                pw.println();

                // Static LLM singleton fields — initialized once in @BeforeClass
                pw.println("    // LLM validation singletons — created ONCE per test class, not per test method");
                pw.println("    private static final boolean LLM_VALIDATION_ENABLED = Boolean.parseBoolean(System.getProperty(\"llm.response.validation.enabled\", \"false\"));");
                pw.println("    private static final boolean LLM_ONLY_2XX = Boolean.parseBoolean(System.getProperty(\"llm.response.validation.only.2xx\", \"true\"));");
                pw.println("    private static final boolean LLM_INCLUDE_RCA = Boolean.parseBoolean(System.getProperty(\"llm.response.validation.include.rca\", \"true\"));");
                pw.println("    private static io.mist.core.generation.ZeroShotLLMGenerator llmValidator;");
                pw.println();

                // Phase 2.F: Trace Shape Oracle singleton — created once in @BeforeClass.
                // The oracle reloads the .mist/trace-shape-invariants.json file MistRunner
                // populated on its cold-start training pass.
                pw.println("    // Trace Shape Oracle (Phase 2.F) — created ONCE per test class, reads .mist/trace-shape-invariants.json");
                pw.println("    private static TraceShapeOracle oracle;");
                pw.println("    // Per-step verdict, populated from inside attachJaegerTrace(...) so the test-method assertion");
                pw.println("    // path can consult it when deciding whether to flip a negative test from FAIL to PASS.");
                pw.println("    private static final ThreadLocal<TraceShapeVerdict> LAST_VERDICT = new ThreadLocal<>();");
                pw.println("    // Phase 4.x: carries the live client response body into attachJaegerTrace() so the");
                pw.println("    // ResponseEnvelope invariant can read it (Jaeger spans omit http.response.body).");
                pw.println("    private static final ThreadLocal<String> CLIENT_RESPONSE_BODY = new ThreadLocal<>();");
                // Ground-truth client-facing HTTP status of the step, injected into the model root
                // span(s) as 'mist.client.status' so HiddenDownstreamFailureInvariant can anchor on
                // the REAL response code — MIST drives external entry points whose own inbound span
                // is often missing/orphaned in the trace, so topology alone can't confirm 2xx.
                pw.println("    private static final ThreadLocal<Integer> CLIENT_RESPONSE_STATUS = new ThreadLocal<>();");
                pw.println();

                // Fix 3 Layer 3: output-coverage backstop. A shared set of response
                // (status + body hash) fingerprints across all @Test methods in the
                // class. When a step's response duplicates a previously-seen
                // response, downstream expensive oracles (LLM validation, Allure
                // attachment) are short-circuited — the HTTP call has already
                // landed, so the test still exercises the SUT, but we don't pay
                // the per-step oracle cost twice for an identical observation.
                pw.println("    // Output-coverage backstop (Fix 3 Layer 3): shared response-fingerprint set");
                pw.println("    private static final java.util.Set<String> SEEN_RESPONSE_HASHES = java.util.concurrent.ConcurrentHashMap.newKeySet();");
                pw.println("    private static String responseFingerprint(int status, String body) {");
                pw.println("        String payload = status + \"|\" + (body == null ? \"\" : body);");
                pw.println("        try {");
                pw.println("            java.security.MessageDigest md = java.security.MessageDigest.getInstance(\"SHA-256\");");
                pw.println("            byte[] h = md.digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));");
                pw.println("            StringBuilder sb = new StringBuilder(h.length * 2);");
                pw.println("            for (byte b : h) sb.append(String.format(\"%02x\", b));");
                pw.println("            return sb.toString();");
                pw.println("        } catch (java.security.NoSuchAlgorithmException e) {");
                pw.println("            return Integer.toString(payload.hashCode());");
                pw.println("        }");
                pw.println("    }");
                pw.println();

                if (allureReport) {
                    // Emit the SUT's OWN configured Jaeger settings (read at generation
                    // time from System properties, which MstConfig populates) as the
                    // generated-test defaults — instead of a hardcoded train-ticket cluster
                    // IP. The property still overrides at runtime; this only fixes the
                    // fallback so a generated test isn't pinned to a dead TT cluster.
                    String __jaegerBaseDefault = System.getProperty("jaeger.base.url", "http://localhost:16686");
                    String __jaegerEnabledDefault = System.getProperty("jaeger.enabled", "false");
                    String __jaegerLookbackDefault = System.getProperty("jaeger.lookback", "10m");
                    pw.println("    // Jaeger configuration (defaults captured from this run's config)");
                    pw.println("    private static final boolean JAEGER_ENABLED = Boolean.parseBoolean(System.getProperty(\"jaeger.enabled\", \"" + __jaegerEnabledDefault + "\"));");
                    pw.println("    // /traces/{id} returns the COMPLETE trace tree with all spans");
                    pw.println("    private static final String JAEGER_BASE_URL = System.getProperty(\"jaeger.base.url\", \"" + escape(__jaegerBaseDefault) + "\");");
                    pw.println("    private static final String JAEGER_LOOKBACK = System.getProperty(\"jaeger.lookback\", \"" + escape(__jaegerLookbackDefault) + "\");");
                    pw.println();
                    pw.println("    private static void attachJaegerTrace(String service, String method, String path, long requestStartMicros, Map<String, String> stepParameters, boolean isStepFailed, String markerTraceId, String testMethodName, String targetService, String targetParam) {");
                    pw.println("        if (!JAEGER_ENABLED) return;");
                    pw.println("        // Clear any verdict left by the previous step so a trace-fetch miss in");
                    pw.println("        // THIS step cannot make the oracle gate read the previous step's verdict.");
                    pw.println("        // Repopulated below via LAST_VERDICT.set(verdict) only when a trace is found.");
                    pw.println("        LAST_VERDICT.remove();");
                    pw.println("        try {");
                    pw.println("            String operation = method + \" \" + path;");
                    pw.println("            String opEncoded = URLEncoder.encode(operation, StandardCharsets.UTF_8);");
                    pw.println("            String svcEncoded = URLEncoder.encode(service, StandardCharsets.UTF_8);");
                    pw.println("            // Create very precise time window for unique trace identification (10 seconds before, 30 seconds after)");
                    pw.println("            long start = requestStartMicros - (10L * 1000L * 1000L); // 10 seconds earlier (us)");
                    pw.println("            long end = requestStartMicros + (30L * 1000L * 1000L); // 30 seconds later (us)");
                    pw.println("            if (start < 0) start = 0;");
                    pw.println("            ");
                    pw.println("            // Heuristic fallbacks (only reached if the marker-first exact-ID lookup");
                    pw.println("            // below misses). The primary path is the W3C-traceparent marker lookup;");
                    pw.println("            // these are SUT-agnostic — a broad time-window search (which already");
                    pw.println("            // returns the gateway + downstream spans) plus the target-service queries.");
                    pw.println("            // (Previously this hardcoded service=ts-gateway-service as the priority");
                    pw.println("            // query — a train-ticket assumption useless on any other SUT.)");
                    pw.println("            String[] queryUrls = {");
                    pw.println("                // Strategy 1: Broad search — any traces in the window (includes the");
                    pw.println("                // ingress/gateway span and all downstream spans of this request).");
                    pw.println("                JAEGER_BASE_URL + \"/traces?limit=200&start=\" + start + \"&end=\" + end,");
                    pw.println("                // Strategy 2: Target service with exact operation");
                    pw.println("                JAEGER_BASE_URL + \"/traces?limit=50&service=\" + svcEncoded + \"&operation=\" + opEncoded + \"&start=\" + start + \"&end=\" + end,");
                    pw.println("                // Strategy 3: Target service with method only");
                    pw.println("                JAEGER_BASE_URL + \"/traces?limit=50&service=\" + svcEncoded + \"&operation=\" + URLEncoder.encode(method, StandardCharsets.UTF_8) + \"&start=\" + start + \"&end=\" + end,");
                    pw.println("                // Strategy 4: Target service in time window");
                    pw.println("                JAEGER_BASE_URL + \"/traces?limit=100&service=\" + svcEncoded + \"&start=\" + start + \"&end=\" + end,");
                    pw.println("                // Strategy 5: Recent traces with lookback (last resort)");
                    pw.println("                JAEGER_BASE_URL + \"/traces?limit=200&lookback=\" + JAEGER_LOOKBACK");
                    pw.println("            };");
                    pw.println("            ");
                    pw.println("            HttpClient client = HttpClient.newHttpClient();");
                    pw.println("            StringBuilder debugInfo = new StringBuilder();");
                    pw.println("            debugInfo.append(\"🔍 Jaeger Query Debug Info:\\n\");");
                    pw.println("            debugInfo.append(\"Target Service: \").append(service).append(\"\\n\");");
                    pw.println("            debugInfo.append(\"Method: \").append(method).append(\"\\n\");");
                    pw.println("            debugInfo.append(\"Path: \").append(path).append(\"\\n\");");
                    pw.println("            debugInfo.append(\"Operation: \").append(operation).append(\"\\n\");");
                    pw.println("            debugInfo.append(\"Request Start (μs): \").append(requestStartMicros).append(\"\\n\");");
                    pw.println("            debugInfo.append(\"Search Window: \").append(start).append(\" to \").append(end).append(\" (μs)\\n\");");
                    pw.println("            debugInfo.append(\"Time Window: \").append((end - start) / 1000000).append(\" seconds\\n\");");
                    pw.println("            debugInfo.append(\"🔎 Lookup: marker-first exact-ID (W3C traceparent), then broad time-window + target-service\\n\");");
                    pw.println("            debugInfo.append(\"⏱️ Test Separation: 2-second delay enforced between executions for unique traces\\n\");");
                    pw.println("            debugInfo.append(\"🔄 Trace Propagation: 3-second delay after execution for Jaeger indexing\\n\");");
                    pw.println("            debugInfo.append(\"🔄 Retry Strategy: Multiple attempts to find current execution trace\\n\\n\");");
                    pw.println("            ");
                    pw.println("            // 🔄 Retry mechanism to find current execution trace");
                    pw.println("            boolean foundCurrentTrace = false;");
                    pw.println("            int maxRetries = 3;");
                    pw.println("            JSONObject globalBestTrace = null;  // Track best trace across all queries");
                    pw.println("            long globalBestDiff = Long.MAX_VALUE;");
                    pw.println("            int globalBestScore = -1;");
                    pw.println("            ");
                    pw.println("            // ── Marker-first exact-ID lookup (W3C trace context) ──");
                    pw.println("            // Test client injects traceparent: 00-<traceId>-...-01 on every HTTP request.");
                    pw.println("            // Jaeger ingests SUT spans under exactly that traceId, so GET /traces/<id> is a");
                    pw.println("            // deterministic 1:1 lookup — no time-window ambiguity under parallel execution.");
                    pw.println("            // Poll 5×200ms for ingest to settle; fallthrough to heuristic if SUT doesn't honor");
                    pw.println("            // W3C traceparent or ingest is unusually delayed.");
                    pw.println("            if (markerTraceId != null && !markerTraceId.isEmpty()) {");
                    pw.println("                debugInfo.append(\"🎯 Marker-first query: \").append(JAEGER_BASE_URL).append(\"/traces/\").append(markerTraceId).append(\"\\n\");");
                    pw.println("                for (int __markAttempt = 0; __markAttempt < 5 && globalBestTrace == null; __markAttempt++) {");
                    pw.println("                    try {");
                    pw.println("                        HttpRequest __mreq = HttpRequest.newBuilder(");
                    pw.println("                            java.net.URI.create(JAEGER_BASE_URL + \"/traces/\" + markerTraceId)).GET().build();");
                    pw.println("                        HttpResponse<String> __mres = HttpClient.newHttpClient().send(__mreq, HttpResponse.BodyHandlers.ofString());");
                    pw.println("                        if (__mres.statusCode() == 200) {");
                    pw.println("                            JSONObject __mjson = new JSONObject(__mres.body());");
                    pw.println("                            JSONArray __mdata = __mjson.optJSONArray(\"data\");");
                    pw.println("                            if (__mdata != null && __mdata.length() > 0) {");
                    pw.println("                                JSONObject __mt = __mdata.getJSONObject(0);");
                    pw.println("                                JSONArray __mspans = __mt.optJSONArray(\"spans\");");
                    pw.println("                                if (__mspans != null && __mspans.length() > 0) {");
                    pw.println("                                    globalBestTrace = __mt;");
                    pw.println("                                    globalBestScore = Integer.MAX_VALUE;");
                    pw.println("                                    foundCurrentTrace = true;");
                    pw.println("                                    debugInfo.append(\"✅ Marker match at attempt \").append(__markAttempt + 1)");
                    pw.println("                                             .append(\" (\").append(__mspans.length()).append(\" spans)\\n\");");
                    pw.println("                                }");
                    pw.println("                            }");
                    pw.println("                        }");
                    pw.println("                    } catch (Exception __markEx) { /* swallow + retry */ }");
                    pw.println("                    if (globalBestTrace == null) {");
                    pw.println("                        try { Thread.sleep(200); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }");
                    pw.println("                    }");
                    pw.println("                }");
                    pw.println("                if (globalBestTrace == null) {");
                    pw.println("                    debugInfo.append(\"⚠️ Marker query exhausted; falling back to time-window heuristic\\n\");");
                    pw.println("                }");
                    pw.println("            }");
                    pw.println("            ");
                    pw.println("            for (int retry = 0; retry < maxRetries && !foundCurrentTrace; retry++) {");
                    pw.println("                if (retry > 0) {");
                    pw.println("                    debugInfo.append(\"\\n🔄 RETRY \").append(retry).append(\": Searching again for current execution trace...\\n\");");
                    pw.println("                    try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }");
                    pw.println("                }");
                    pw.println("                ");
                    pw.println("            for (int queryIdx = 0; queryIdx < queryUrls.length; queryIdx++) {");
                    pw.println("                String url = queryUrls[queryIdx];");
                    pw.println("                debugInfo.append(\"Query \").append(queryIdx + 1).append(\": \").append(url).append(\"\\n\");");
                    pw.println("                ");
                    pw.println("                HttpRequest req = HttpRequest.newBuilder(java.net.URI.create(url)).GET().build();");
                    pw.println("                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());");
                    pw.println("                debugInfo.append(\"Response Status: \").append(res.statusCode()).append(\"\\n\");");
                    pw.println("                ");
                    pw.println("                if (res.statusCode() >= 200 && res.statusCode() < 300) {");
                    pw.println("                    String body = res.body();");
                    pw.println("                    debugInfo.append(\"Response Body Length: \").append(body.length()).append(\"\\n\");");
                    pw.println("                    ");
                    pw.println("                    try {");
                    pw.println("                        JSONObject json = new JSONObject(body);");
                    pw.println("                        if (json.has(\"data\")) {");
                    pw.println("                            JSONArray data = json.getJSONArray(\"data\");");
                    pw.println("                            debugInfo.append(\"Traces Found: \").append(data.length()).append(\"\\n\");");
                    pw.println("                            ");
                                pw.println("                            if (data.length() > 0) {");
                                pw.println("                                // Smart trace selection - prioritize traces with actual API calls");
                                pw.println("                                JSONObject bestTrace = null;");
                                pw.println("                                long bestDiff = Long.MAX_VALUE;");
                                pw.println("                                int bestTraceIdx = -1;");
                                pw.println("                                int bestScore = -1;");
                                pw.println("                                ");
                                pw.println("                                debugInfo.append(\"Analyzing \").append(data.length()).append(\" traces for API calls...\\n\");");
                                pw.println("                                ");
                                pw.println("                                for (int i = 0; i < data.length(); i++) {");
                                pw.println("                                    JSONObject trace = data.getJSONObject(i);");
                                pw.println("                                    JSONArray spans = trace.optJSONArray(\"spans\");");
                                pw.println("                                    if (spans == null) continue;");
                                pw.println("                                    ");
                                pw.println("                                    int traceScore = 0;");
                                pw.println("                                    int apiCallCount = 0;");
                                pw.println("                                    boolean hasTargetOperation = false;");
                                pw.println("                                    long closestTime = Long.MAX_VALUE;");
                                pw.println("                                    ");
                                pw.println("                                    // Analyze all spans in this trace");
                                pw.println("                                    for (int s = 0; s < spans.length(); s++) {");
                                pw.println("                                        JSONObject span = spans.getJSONObject(s);");
                                pw.println("                                        String opName = span.optString(\"operationName\", \"\");");
                                pw.println("                                        long spanStart = span.optLong(\"startTime\", 0L);");
                                pw.println("                                        ");
                                pw.println("                                        // Check if this span is an actual API call");
                                pw.println("                                        boolean isApiSpan = false;");
                                pw.println("                                        String spanMethod = \"\";");
                                pw.println("                                        String spanPath = \"\";");
                                pw.println("                                        ");
                                pw.println("                                        JSONArray tags = span.optJSONArray(\"tags\");");
                                pw.println("                                        if (tags != null) {");
                                pw.println("                                            for (int t = 0; t < tags.length(); t++) {");
                                pw.println("                                                JSONObject tag = tags.getJSONObject(t);");
                                pw.println("                                                String key = tag.optString(\"key\");");
                                pw.println("                                                String value = tag.optString(\"value\");");
                                pw.println("                                                if (\"http.method\".equals(key)) spanMethod = value;");
                                pw.println("                                                if (\"http.target\".equals(key) || \"http.url\".equals(key)) spanPath = value;");
                                pw.println("                                            }");
                                pw.println("                                        }");
                                pw.println("                                        ");
                                pw.println("                                        // Check if this is an API call");
                                pw.println("                                        if ((!spanMethod.isEmpty() && !spanPath.isEmpty() && spanPath.contains(\"/api/\")) ||");
                                pw.println("                                            opName.contains(\"/api/\") ||");
                                pw.println("                                            opName.matches(\"(GET|POST|PUT|DELETE|PATCH)\\\\s+/api/.*\")) {");
                                pw.println("                                            isApiSpan = true;");
                                pw.println("                                            apiCallCount++;");
                                pw.println("                                        }");
                                pw.println("                                        ");
                                pw.println("                                        // Check if this matches our target operation");
                                pw.println("                                        boolean isTargetOp = opName.equals(operation) ||");
                                pw.println("                                            (spanMethod.equalsIgnoreCase(method) && spanPath.contains(path)) ||");
                                pw.println("                                            (opName.contains(method) && opName.contains(path));");
                                pw.println("                                        ");
                                pw.println("                                        if (isTargetOp) {");
                                pw.println("                                            hasTargetOperation = true;");
                                pw.println("                                            if (spanStart > 0L) {");
                                pw.println("                                                long timeDiff = Math.abs(spanStart - requestStartMicros);");
                                pw.println("                                                closestTime = Math.min(closestTime, timeDiff);");
                                pw.println("                                            }");
                                pw.println("                                        }");
                                pw.println("                                    }");
                                pw.println("                                    ");
                    pw.println("                                    // Enhanced scoring - HEAVILY prioritize recent execution timing");
                    pw.println("                                    if (hasTargetOperation) traceScore += 1000; // Target operation match");
                    pw.println("                                    traceScore += apiCallCount * 5; // More API calls = better (reduced weight)");
                    pw.println("                                    ");
                    pw.println("                                    // CRITICAL: Ultra-precise time proximity scoring (test separation enforced)");
                    pw.println("                                    if (closestTime < Long.MAX_VALUE) {");
                    pw.println("                                        if (closestTime < 1000000L) traceScore += 5000; // Within 1 second (CURRENT execution)");
                    pw.println("                                        else if (closestTime < 2000000L) traceScore += 3000; // Within 2 seconds (EXACT execution)");
                    pw.println("                                        else if (closestTime < 5000000L) traceScore += 1500; // Within 5 seconds (very recent)");
                    pw.println("                                        else if (closestTime < 10000000L) traceScore += 500; // Within 10 seconds");
                    pw.println("                                        else if (closestTime < 20000000L) traceScore += 200; // Within 20 seconds");
                    pw.println("                                        else if (closestTime < 30000000L) traceScore += 100; // Within 30 seconds");
                    pw.println("                                    }");
                                pw.println("                                    ");
                                pw.println("                                    debugInfo.append(\"Trace \").append(i).append(\": score=\").append(traceScore);");
                                pw.println("                                    debugInfo.append(\", APIs=\").append(apiCallCount);");
                                pw.println("                                    debugInfo.append(\", hasTarget=\").append(hasTargetOperation);");
                                pw.println("                                    debugInfo.append(\", timeDiff=\").append(closestTime == Long.MAX_VALUE ? \"N/A\" : closestTime + \"μs\");");
                                pw.println("                                    debugInfo.append(\", spans=\").append(spans.length()).append(\"\\n\");");
                                pw.println("                                    ");
                                pw.println("                                    // Show first few operations for debugging");
                                pw.println("                                    debugInfo.append(\"  Operations: \");");
                                pw.println("                                    for (int opIdx = 0; opIdx < Math.min(spans.length(), 5); opIdx++) {");
                                pw.println("                                        JSONObject span = spans.getJSONObject(opIdx);");
                                pw.println("                                        String opName = span.optString(\"operationName\", \"\");");
                                pw.println("                                        if (opIdx > 0) debugInfo.append(\", \");");
                                pw.println("                                        debugInfo.append(opName.length() > 30 ? opName.substring(0, 27) + \"...\" : opName);");
                                pw.println("                                    }");
                                pw.println("                                    if (spans.length() > 5) debugInfo.append(\", +\").append(spans.length() - 5).append(\" more\");");
                                pw.println("                                    debugInfo.append(\"\\n\");");
                                pw.println("                                    ");
                                pw.println("                                    // Select best trace: TIMING is the primary discriminator for recent requests");
                                pw.println("                                    boolean shouldSelectTrace = false;");
                                pw.println("                                    ");
                                pw.println("                                    if (traceScore > 0) { // Must have API calls");
                                pw.println("                                        if (bestTrace == null) {");
                                pw.println("                                            shouldSelectTrace = true;");
                                pw.println("                                        } else {");
                                pw.println("                                            // For recent traces (within 10 seconds), prioritize timing over score");
                                pw.println("                                            boolean currentIsRecent = closestTime < 10000000L; // 10 seconds (post-delay execution)");
                                pw.println("                                            boolean bestIsRecent = bestDiff < 10000000L;");
                                pw.println("                                            ");
                                pw.println("                                            if (currentIsRecent && !bestIsRecent) {");
                                pw.println("                                                shouldSelectTrace = true; // Always prefer recent traces");
                                pw.println("                                            } else if (!currentIsRecent && bestIsRecent) {");
                                pw.println("                                                shouldSelectTrace = false; // Keep the recent one");
                                pw.println("                                            } else if (currentIsRecent && bestIsRecent) {");
                                pw.println("                                                // Both recent: pick the closest in time");
                                pw.println("                                                shouldSelectTrace = closestTime < bestDiff;");
                                pw.println("                                            } else {");
                                pw.println("                                                // Both old: use score then timing");
                                pw.println("                                                shouldSelectTrace = traceScore > bestScore || ");
                                pw.println("                                                    (traceScore == bestScore && closestTime < bestDiff);");
                                pw.println("                                            }");
                                pw.println("                                        }");
                                pw.println("                                    }");
                                pw.println("                                    ");
                                pw.println("                                    if (shouldSelectTrace) {");
                                pw.println("                                        bestScore = traceScore;");
                                pw.println("                                        bestDiff = closestTime;");
                                pw.println("                                        bestTrace = trace;");
                                pw.println("                                        bestTraceIdx = i;");
                                pw.println("                                    }");
                                pw.println("                                }");
                                pw.println("                                ");
                                pw.println("                                debugInfo.append(\"\\nSelected trace \").append(bestTraceIdx).append(\" with score \").append(bestScore);");
                                pw.println("                                if (bestScore > 0) {");
                                pw.println("                                    // Check if this is a current execution trace (within 3 seconds)");
                                pw.println("                                    boolean isCurrentTrace = bestDiff < 3000000L; // 3 seconds");
                                pw.println("                                    if (isCurrentTrace) {");
                                pw.println("                                        debugInfo.append(\" (CURRENT EXECUTION - perfect match!)\\n\");");
                                pw.println("                                        foundCurrentTrace = true;");
                                pw.println("                                    } else {");
                                pw.println("                                        debugInfo.append(\" (matched but not current - possibly previous execution)\\n\");");
                                pw.println("                                    }");
                                pw.println("                                    ");
                                pw.println("                                    // Update global best trace if this is better");
                                pw.println("                                    boolean shouldUpdateGlobal = false;");
                                pw.println("                                    if (globalBestTrace == null) {");
                                pw.println("                                        shouldUpdateGlobal = true;");
                                pw.println("                                    } else if (isCurrentTrace && globalBestDiff >= 3000000L) {");
                                pw.println("                                        shouldUpdateGlobal = true; // Always prefer current over non-current");
                                pw.println("                                    } else if (isCurrentTrace && globalBestDiff < 3000000L) {");
                                pw.println("                                        shouldUpdateGlobal = bestDiff < globalBestDiff; // Both current: pick closer");
                                pw.println("                                    } else if (!isCurrentTrace && globalBestDiff >= 3000000L) {");
                                pw.println("                                        // Both non-current: pick better score or closer time");
                                pw.println("                                        shouldUpdateGlobal = bestScore > globalBestScore || ");
                                pw.println("                                            (bestScore == globalBestScore && bestDiff < globalBestDiff);");
                                pw.println("                                    }");
                                pw.println("                                    ");
                                pw.println("                                    if (shouldUpdateGlobal) {");
                                pw.println("                                        // Fetch COMPLETE trace to replace summary");
                                pw.println("                                        if (bestTrace != null) {");
                                pw.println("                                            String traceId = bestTrace.optString(\"traceID\", \"\");");
                                pw.println("                                            if (!traceId.isEmpty()) {");
                                pw.println("                                                debugInfo.append(\"\\n🔄 Fetching complete trace for ID: \").append(traceId).append(\"\\n\");");
                                pw.println("                                                String traceDetailUrl = JAEGER_BASE_URL + \"/traces/\" + traceId;");
                                pw.println("                                                HttpRequest traceReq = HttpRequest.newBuilder(java.net.URI.create(traceDetailUrl)).GET().build();");
                                pw.println("                                                HttpResponse<String> traceRes = client.send(traceReq, HttpResponse.BodyHandlers.ofString());");
                                pw.println("                                                ");
                                pw.println("                                                if (traceRes.statusCode() >= 200 && traceRes.statusCode() < 300) {");
                                pw.println("                                                    JSONObject completeTraceResponse = new JSONObject(traceRes.body());");
                                pw.println("                                                    if (completeTraceResponse.has(\"data\") && completeTraceResponse.getJSONArray(\"data\").length() > 0) {");
                                pw.println("                                                        bestTrace = completeTraceResponse.getJSONArray(\"data\").getJSONObject(0);");
                                pw.println("                                                        debugInfo.append(\"✅ Complete trace fetched with \").append(bestTrace.optJSONArray(\"spans\").length()).append(\" spans\\n\");");
                                pw.println("                                                    } else {");
                                pw.println("                                                        debugInfo.append(\"⚠️  Complete trace response has no data, using summary trace\\n\");");
                                pw.println("                                                    }");
                                pw.println("                                                } else {");
                                pw.println("                                                    debugInfo.append(\"⚠️  Failed to fetch complete trace (status: \").append(traceRes.statusCode()).append(\"), using summary trace\\n\");");
                                pw.println("                                                }");
                                pw.println("                                            }");
                                pw.println("                                        }");
                                pw.println("                                        ");
                                pw.println("                                        // Update global best");
                                pw.println("                                        globalBestTrace = bestTrace;");
                                pw.println("                                        globalBestDiff = bestDiff;");
                                pw.println("                                        globalBestScore = bestScore;");
                                pw.println("                                        debugInfo.append(\"✅ Updated global best trace (score: \").append(bestScore).append(\", timeDiff: \").append(bestDiff / 1000000.0).append(\"s)\\n\");");
                                pw.println("                                    }");
                                pw.println("                                    ");
                                pw.println("                                    // If this is a current trace, we can stop retrying");
                                pw.println("                                    if (isCurrentTrace) {");
                                pw.println("                                        debugInfo.append(\"✅ Found current execution trace - stopping search\\n\");");
                                pw.println("                                        break; // Exit query loop");
                                pw.println("                                    } else {");
                                pw.println("                                        debugInfo.append(\"⏭️ Continuing to search for more recent trace...\\n\");");
                                pw.println("                                    }");
                                pw.println("                                } else {");
                                pw.println("                                    debugInfo.append(\" (no API calls found)\\n\");");
                                pw.println("                                    debugInfo.append(\"\\n⚠️ This query found traces but no API calls. Continuing to next strategy...\\n\");");
                                pw.println("                                }");
                    pw.println("                            } else {");
                    pw.println("                                debugInfo.append(\"No traces found in response\\n\");");
                    pw.println("                            }");
                    pw.println("                        } else {");
                    pw.println("                            debugInfo.append(\"No 'data' field in response\\n\");");
                    pw.println("                        }");
                    pw.println("                    } catch (Exception e) {");
                    pw.println("                        debugInfo.append(\"JSON Parse Error: \").append(e.getMessage()).append(\"\\n\");");
                    pw.println("                    }");
                    pw.println("                    ");
                    pw.println("                    // If this query didn't work, try next one");
                    pw.println("                    debugInfo.append(\"Query \").append(queryIdx + 1).append(\" failed, trying next...\\n\\n\");");
                    pw.println("                } else {");
                    pw.println("                    debugInfo.append(\"HTTP Error: \").append(res.statusCode()).append(\"\\n\\n\");");
                    pw.println("                }");
                    pw.println("            }"); // End of query loop
                    pw.println("            "); 
                    pw.println("            // If we found a current trace, exit retry loop");
                    pw.println("            if (foundCurrentTrace) {");
                    pw.println("                debugInfo.append(\"✅ Found current execution trace, stopping retries.\\n\");");
                    pw.println("                break; // Exit retry loop");
                    pw.println("            }");
                    pw.println("            }"); // End of retry loop
                    pw.println("            ");
                    pw.println("            // After all searches, analyze and attach the BEST trace found (if any)");
                    pw.println("            if (globalBestTrace != null) {");
                    pw.println("                debugInfo.append(\"\\n✅ Using best trace found (score: \").append(globalBestScore)");
                    pw.println("                           .append(\", timeDiff: \").append(globalBestDiff / 1000000.0).append(\"s)\\n\");");
                    pw.println("                ");
                    pw.println("                String traceTable = generateTraceTable(globalBestTrace);");
                    pw.println("                String traceSummary = generateTraceSummary(globalBestTrace);");
                    pw.println("                TraceErrorAnalyzer.ErrorAnalysisResult errorAnalysis = TraceErrorAnalyzer.analyzeTrace(globalBestTrace);");
                    pw.println("                String errorReport = TraceErrorAnalyzer.generateErrorReport(errorAnalysis);");
                    pw.println("                ");
                    pw.println("                if (errorAnalysis.hasErrors()) {");
                    pw.println("                    // Trace contains technical errors (error=true tags)");
                    pw.println("                    analyzeAndRecordParameterErrors(globalBestTrace, stepParameters);");
                    pw.println("                    String intelligentAnalysis = TraceErrorAnalyzer.generateIntelligentAnalysis(errorAnalysis, globalBestTrace);");
                    pw.println("                    if (intelligentAnalysis != null && !intelligentAnalysis.trim().isEmpty()) {");
                    pw.println("                        Allure.addAttachment(\"🤖 INTELLIGENT ANALYSIS (Based on Trace)\", \"text/plain\", intelligentAnalysis);");
                    pw.println("                    }");
                    pw.println("                    Allure.addAttachment(\"🔗 API Call Trace\", \"text/plain\", traceTable);");
                    pw.println("                } else if (isStepFailed) {");
                    pw.println("                    // Test failed but trace has no technical errors - likely business logic validation failure");
                    // Grounded RCA — state the CONCRETE cause from this test's actual response +
                    // trace, not generic advice. (The previous fixed boilerplate said the same
                    // thing every time and carried no information.)
                    pw.println("                    StringBuilder analysisMsg = new StringBuilder();");
                    pw.println("                    analysisMsg.append(\"═══════════════════════════════════════════════════════════════════════\\n\");");
                    pw.println("                    analysisMsg.append(\"🔎 ROOT CAUSE (grounded in this test's actual response + trace)\\n\");");
                    pw.println("                    analysisMsg.append(\"═══════════════════════════════════════════════════════════════════════\\n\\n\");");
                    pw.println("                    analysisMsg.append(\"The trace shows NO swallowed downstream server error (no http>=500 / otel=ERROR\\n\");");
                    pw.println("                    analysisMsg.append(\"span). So this is a CLIENT-FACING failure — the endpoint itself rejected the\\n\");");
                    pw.println("                    analysisMsg.append(\"request (status/validation), not a hidden backend fault.\\n\\n\");");
                    pw.println("                    Integer __rcaStatus = CLIENT_RESPONSE_STATUS.get();");
                    pw.println("                    analysisMsg.append(\"Actual client response\");");
                    pw.println("                    if (__rcaStatus != null) analysisMsg.append(\" — HTTP \").append(__rcaStatus);");
                    pw.println("                    analysisMsg.append(\" (the concrete rejection reason):\\n\");");
                    pw.println("                    String __rcaBody = CLIENT_RESPONSE_BODY.get();");
                    pw.println("                    if (__rcaBody != null && !__rcaBody.isEmpty()) {");
                    pw.println("                        analysisMsg.append(__rcaBody.length() > 800 ? __rcaBody.substring(0, 800) + \"…\" : __rcaBody).append(\"\\n\");");
                    pw.println("                    } else {");
                    pw.println("                        analysisMsg.append(\"(no response body captured for this step)\\n\");");
                    pw.println("                    }");
                    pw.println("                    Allure.addAttachment(\"🔎 ROOT CAUSE (grounded)\", \"text/plain\", analysisMsg.toString());");
                    pw.println("                    Allure.addAttachment(\"🔗 API Call Trace\", \"text/plain\", traceTable);");
                    pw.println("                } else {");
                    pw.println("                    // Test succeeded and trace has no errors");
                    pw.println("                    Allure.addAttachment(\"🔗 API Call Trace\", \"text/plain\", traceTable);");
                    pw.println("                }");
                    pw.println("                Allure.addAttachment(\"📊 Trace Summary\", \"text/plain\", traceSummary);");
                    pw.println("                Allure.addAttachment(\"📈 Raw Trace Data\", \"application/json\", globalBestTrace.toString());");
                    pw.println();
                    pw.println("                // Trace Shape Oracle (Phase 2.F contribution) — runs on the same trace");
                    pw.println("                // the TraceErrorAnalyzer block just processed. The verdict is attached");
                    pw.println("                // to the step as a JSON document AND recorded on a thread-local so the");
                    pw.println("                // surrounding test-method assertion path can consult it (used for the");
                    pw.println("                // negative-test FAIL->PASS flip on ResponseEnvelope violations).");
                    pw.println("                if (oracle != null) {");
                    pw.println("                    try {");
                    pw.println("                        String rootApiKey = method + \" \" + path;");
                    pw.println("                        TraceModel model = TraceShapeAdapter.toModel(globalBestTrace, rootApiKey);");
                    pw.println("                        // Phase 4.x: inject the live client response body into the model's root");
                    pw.println("                        // span(s) so ResponseEnvelopeInvariant can read it (Jaeger spans omit it).");
                    pw.println("                        String __clientBody = CLIENT_RESPONSE_BODY.get();");
                    pw.println("                        CLIENT_RESPONSE_BODY.remove();");
                    pw.println("                        Integer __clientStatus = CLIENT_RESPONSE_STATUS.get();");
                    pw.println("                        CLIENT_RESPONSE_STATUS.remove();");
                    pw.println("                        if (model != null) {");
                    pw.println("                            for (TraceModel.Span __rs : model.roots()) {");
                    pw.println("                                if (__rs.tags == null) continue;");
                    pw.println("                                if (__clientBody != null && !__clientBody.isEmpty()) __rs.tags.put(\"http.response.body\", __clientBody);");
                    pw.println("                                if (__clientStatus != null) __rs.tags.put(\"mist.client.status\", String.valueOf(__clientStatus));");
                    pw.println("                            }");
                    pw.println("                        }");
                    pw.println("                        // FIXES.md F1+F3: target-aware overload runs TargetAttributionInvariant");
                    pw.println("                        // when the per-test target context + ablation flag are both set, so");
                    pw.println("                        // attribution is embedded as one of the verdict outcomes and the");
                    pw.println("                        // report-rendering path collapses it uniformly with the other 4.");
                    pw.println("                        TraceShapeVerdict verdict = oracle.evaluate(model, rootApiKey, targetService, targetParam);");
                    pw.println("                        LAST_VERDICT.set(verdict);");
                    pw.println("                        StringBuilder verdictJson = new StringBuilder();");
                    pw.println("                        verdictJson.append(\"{\\n  \\\"rootApiKey\\\": \\\"\").append(rootApiKey.replace(\"\\\\\", \"\\\\\\\\\").replace(\"\\\"\", \"\\\\\\\"\")).append(\"\\\",\\n\");");
                    pw.println("                        verdictJson.append(\"  \\\"passed\\\": \").append(verdict.isPassed()).append(\",\\n\");");
                    pw.println("                        verdictJson.append(\"  \\\"outcomes\\\": [\\n\");");
                    pw.println("                        java.util.List<TraceShapeVerdict.InvariantOutcome> outcomes = verdict.getOutcomes();");
                    pw.println("                        for (int oi = 0; oi < outcomes.size(); oi++) {");
                    pw.println("                            TraceShapeVerdict.InvariantOutcome o = outcomes.get(oi);");
                    pw.println("                            verdictJson.append(\"    { \\\"kind\\\": \\\"\").append(o.kind).append(\"\\\",\");");
                    pw.println("                            verdictJson.append(\" \\\"passed\\\": \").append(o.passed).append(\",\");");
                    pw.println("                            verdictJson.append(\" \\\"severity\\\": \\\"\").append(o.severity).append(\"\\\",\");");
                    pw.println("                            String esc = o.detail == null ? \"\" : o.detail.replace(\"\\\\\", \"\\\\\\\\\").replace(\"\\\"\", \"\\\\\\\"\");");
                    pw.println("                            verdictJson.append(\" \\\"detail\\\": \\\"\").append(esc).append(\"\\\" }\");");
                    pw.println("                            if (oi < outcomes.size() - 1) verdictJson.append(\",\");");
                    pw.println("                            verdictJson.append(\"\\n\");");
                    pw.println("                        }");
                    pw.println("                        verdictJson.append(\"  ]\\n}\");");
                    pw.println("                        Allure.addAttachment(\"Trace Shape Oracle Verdict\", \"application/json\", verdictJson.toString());");
                    pw.println("                        if (!verdict.isPassed()) {");
                    pw.println("                            for (TraceShapeVerdict.InvariantOutcome o : outcomes) {");
                    pw.println("                                if (!o.passed && o.severity == TraceShapeVerdict.Severity.ERROR) {");
                    pw.println("                                    Allure.step(\"❌ shape violation: \" + o.kind + \" \" + o.detail);");
                    pw.println("                                }");
                    pw.println("                            }");
                    pw.println("                        }");
                    // Dedicated, human-readable surfacing of a HIDDEN downstream failure — the
                    // highest-value finding (invisible to status/schema/response-body oracles).
                    // Previously it was buried in the JSON verdict blob + a generic step line;
                    // now it gets a titled attachment + a filterable label + a parameter so the
                    // user sees, plainly, that a 2xx hid a downstream 5xx and which span it was.
                    // NOT gated on verdict.isPassed(): that is ERROR-only, but a hidden-downstream
                    // is often WARN (a swallowed gRPC/otel error with no HTTP 5xx). Gating here is
                    // exactly why the WARN finding was buried in the verdict JSON and never got the
                    // titled attachment + filterable label. The per-outcome check below is the gate.
                    pw.println("                        if (outcomes != null) {");
                    pw.println("                            for (TraceShapeVerdict.InvariantOutcome o : outcomes) {");
                    pw.println("                                if (!o.passed && \"HIDDEN_DOWNSTREAM_FAILURE\".equals(o.kind)) {");
                    pw.println("                                    StringBuilder hd = new StringBuilder();");
                    pw.println("                                    hd.append(\"🕳️ HIDDEN DOWNSTREAM FAILURE  [severity=\").append(o.severity).append(\"]\\n\");");
                    pw.println("                                    String __uiBase = JAEGER_BASE_URL.endsWith(\"/api\") ? JAEGER_BASE_URL.substring(0, JAEGER_BASE_URL.length() - 4) : JAEGER_BASE_URL;");
                    pw.println("                                    hd.append(\"🔍 Open in Jaeger (visual call tree): \").append(__uiBase).append(\"/trace/\").append(markerTraceId).append(\"\\n\\n\");");
                    pw.println("                                    hd.append(\"WHAT THE CLIENT SAW:\\n  \").append(rootApiKey).append(\"  →  HTTP 2xx ✅   (looks fine — status, schema, and body are all clean)\\n\\n\");");
                    pw.println("                                    hd.append(\"WHAT ACTUALLY HAPPENED (only the trace shows it):\\n\");");
                    pw.println("                                    hd.append(\"  A downstream call failed and was SWALLOWED — the caller returned 2xx anyway.\\n\");");
                    pw.println("                                    hd.append(\"  Read as  caller ──▶ callee ;  the FAILURE is in the callee (the downstream service):\\n    \").append(o.detail).append(\"\\n\\n\");");
                    pw.println("                                    hd.append(\"WHY A SEPARATE FINDING (vs the '🔗 API Call Trace' below)?\\n\");");
                    pw.println("                                    hd.append(\"  The trace marks EVERY errored span ❌. This one is invisible to status/schema/body\\n\");");
                    pw.println("                                    hd.append(\"  checks because the client got a 200 — that is what makes it a HIDDEN failure\\n\");");
                    pw.println("                                    hd.append(\"  (severity \").append(o.severity).append(\": it errored but never surfaced to the caller).\\n\");");
                    pw.println("                                    Allure.addAttachment(\"🕳️ HIDDEN DOWNSTREAM FAILURE — swallowed downstream error behind a 2xx\", \"text/plain\", hd.toString());");
                    pw.println("                                    Allure.label(\"mist.anomaly\", \"HIDDEN_DOWNSTREAM_FAILURE\");");
                    pw.println("                                    Allure.label(\"tag\", \"mist_hidden_downstream\");"); // filterable in the Allure UI (custom labels are not)
                    pw.println("                                    Allure.parameter(\"🕳️ Hidden downstream failure\", o.detail);");
                    pw.println("                                }");
                    pw.println("                            }");
                    pw.println("                        }");
                    pw.println("                        // Phase 0: credit ALL failing outcomes (any severity) to the");
                    pw.println("                        // tracker's anomaly map so the fault-detection report surfaces");
                    pw.println("                        // them as a separate bucket. Called per-step (attachJaegerTrace");
                    pw.println("                        // fires once per step), so a 5-step test records up to 5 sets of");
                    pw.println("                        // outcomes — the first-seen sample for any (oracle,endpoint,sig)");
                    pw.println("                        // tuple wins. markerTraceId is preferred over model.getTraceId()");
                    pw.println("                        // because the marker is the in-process request marker (W3C");
                    pw.println("                        // traceparent) — Jaeger may return a stale or wrong trace.");
                    pw.println("                        try {");
                    pw.println("                            io.mist.core.analysis.FaultDetectionTracker.getInstance()");
                    pw.println("                                .recordVerdict(verdict, rootApiKey, " + className + ".class.getName(), testMethodName, markerTraceId);");
                    pw.println("                        } catch (Throwable anomalyEx) {");
                    pw.println("                            Allure.addAttachment(\"Oracle Anomaly Record Error\", \"text/plain\", anomalyEx.toString());");
                    pw.println("                        }");
                    pw.println("                    } catch (Throwable tsoEx) {");
                    pw.println("                        Allure.addAttachment(\"Trace Shape Oracle Error\", \"text/plain\", \"Oracle evaluation failed: \" + tsoEx.toString());");
                    pw.println("                    }");
                    pw.println("                }");
                    pw.println();
                    pw.println("                Allure.addAttachment(\"🔍 Query Debug Info\", \"text/plain\", debugInfo.toString());");
                    pw.println("            } else {");
                    pw.println("                // No traces found with any query after all retries");
                    pw.println("                if (!foundCurrentTrace) {");
                    pw.println("                    debugInfo.append(\"❌ No current execution traces found after \").append(maxRetries).append(\" retries\\n\");");
                    pw.println("                }");
                    pw.println("                debugInfo.append(\"❌ No traces found with any query strategy\");");
                    pw.println("                Allure.addAttachment(\"🔍 Jaeger Query Debug (No Traces)\", \"text/plain\", debugInfo.toString());");
                    pw.println("            }");
                    pw.println("            ");
                    pw.println("        } catch (Exception e) {");
                    pw.println("            Allure.addAttachment(\"📈 Jaeger Trace Error\", \"text/plain\", \"Error: \" + e.toString() + \"\\nService: \" + service + \"\\nMethod: \" + method + \"\\nPath: \" + path);");
                    pw.println("        }");
                    pw.println("    }");
                    pw.println();
                    pw.println("    private static String generateTraceTable(JSONObject trace) {");
                    pw.println("        try {");
                    pw.println("            JSONArray spans = trace.getJSONArray(\"spans\");");
                    pw.println("            JSONObject processes = trace.optJSONObject(\"processes\");");
                    pw.println("            String traceId = trace.optString(\"traceID\", \"\");");
                    pw.println("            ");
                    pw.println("            StringBuilder table = new StringBuilder();");
                    pw.println("            table.append(\"----------------------------------------------------------------------------------------\\n\");");
                    pw.println("            table.append(\"                           🔗 MICROSERVICE API CALL TRACE                           \\n\");");
                    pw.println("            table.append(\"----------------------------------------------------------------------------------------\\n\");");
                    pw.println("            if (!traceId.isEmpty()) {");
                    pw.println("                table.append(\"Trace ID: \").append(traceId).append(\"\\n\");");
                    pw.println("                // Convert API endpoint to UI endpoint for browser link");
                    pw.println("                // Remove /api from end if present, keep everything else (including /jaeger/ui if present)");
                    pw.println("                String uiBase = JAEGER_BASE_URL;");
                    pw.println("                if (uiBase.endsWith(\"/api\")) {");
                    pw.println("                    uiBase = uiBase.substring(0, uiBase.length() - 4);  // Remove \"/api\"");
                    pw.println("                }");
                    pw.println("                table.append(\"Jaeger UI: \").append(uiBase).append(\"/trace/\").append(traceId).append(\"\\n\");");
                    pw.println("                table.append(\"────────────────────────────────────────────────────────────────────────────────────────\\n\");");
                    pw.println("            }");
                    pw.println("            ");
                    pw.println("            // Build hierarchical API call structure");
                    pw.println("            Map<String, JSONObject> apiSpanById = new HashMap<>();");
                    pw.println("            Map<String, List<String>> apiChildren = new HashMap<>();");
                    pw.println("            Map<String, String> apiParentOf = new HashMap<>();");
                    pw.println("            ");
                    pw.println("            // First pass: collect all API spans and build relationships");
                    pw.println("            for (int i = 0; i < spans.length(); i++) {");
                    pw.println("                JSONObject span = spans.getJSONObject(i);");
                    pw.println("                if (isApiCall(span)) {");
                    pw.println("                    String spanId = span.getString(\"spanID\");");
                    pw.println("                    apiSpanById.put(spanId, span);");
                    pw.println("                    apiChildren.put(spanId, new ArrayList<>());");
                    pw.println("                }");
                    pw.println("            }");
                    pw.println("            ");
                    pw.println("            // Second pass: establish parent-child relationships for API calls");
                    pw.println("            for (int i = 0; i < spans.length(); i++) {");
                    pw.println("                JSONObject span = spans.getJSONObject(i);");
                    pw.println("                if (isApiCall(span)) {");
                    pw.println("                    String spanId = span.getString(\"spanID\");");
                    pw.println("                    String parentId = span.optString(\"parentSpanId\", null);");
                    pw.println("                    ");
                    pw.println("                    // Look for parent in references if not directly available");
                    pw.println("                    if (parentId == null || parentId.isEmpty()) {");
                    pw.println("                        JSONArray refs = span.optJSONArray(\"references\");");
                    pw.println("                        if (refs != null) {");
                    pw.println("                            for (int r = 0; r < refs.length(); r++) {");
                    pw.println("                                JSONObject ref = refs.getJSONObject(r);");
                    pw.println("                                if (\"CHILD_OF\".equalsIgnoreCase(ref.optString(\"refType\"))) {");
                    pw.println("                                    parentId = ref.optString(\"spanID\");");
                    pw.println("                                    break;");
                    pw.println("                                }");
                    pw.println("                            }");
                    pw.println("                        }");
                    pw.println("                    }");
                    pw.println("                    ");
                    pw.println("                    // Only connect if parent is also an API call");
                    pw.println("                    if (parentId != null && apiSpanById.containsKey(parentId)) {");
                    pw.println("                        apiChildren.get(parentId).add(spanId);");
                    pw.println("                        apiParentOf.put(spanId, parentId);");
                    pw.println("                    }");
                    pw.println("                }");
                    pw.println("            }");
                    pw.println("            ");
                    pw.println("            // Find root API calls (those without API parents)");
                    pw.println("            List<String> rootApiCalls = new ArrayList<>();");
                    pw.println("            for (String apiId : apiSpanById.keySet()) {");
                    pw.println("                if (!apiParentOf.containsKey(apiId)) {");
                    pw.println("                    rootApiCalls.add(apiId);");
                    pw.println("                }");
                    pw.println("            }");
                    pw.println("            ");
                    pw.println("            // Sort children by start time");
                    pw.println("            Comparator<String> byStartTime = (a, b) -> Long.compare(");
                    pw.println("                apiSpanById.get(a).optLong(\"startTime\", 0L),");
                    pw.println("                apiSpanById.get(b).optLong(\"startTime\", 0L)");
                    pw.println("            );");
                    pw.println("            for (List<String> children : apiChildren.values()) {");
                    pw.println("                children.sort(byStartTime);");
                    pw.println("            }");
                    pw.println("            rootApiCalls.sort(byStartTime);");
                    pw.println("            ");
                    pw.println("            if (rootApiCalls.isEmpty()) {");
                    pw.println("                table.append(\"⚠️  No API calls found in this trace\\n\");");
                    pw.println("                table.append(\"   This might be an internal operation or framework activity\\n\");");
                    pw.println("            } else {");
                    pw.println("                table.append(\"🌐 API CALL HIERARCHY (Parent → Child Relationships)\\n\");");
                    pw.println("                table.append(\"────────────────────────────────────────────────────────────────────────────────────────\\n\");");
                    pw.println("                // Honest note for externally-driven traces: MIST's external caller is not");
                    pw.println("                // itself traced, so the entry service's inbound span is absent and its downstream");
                    pw.println("                // calls appear as separate top-level entries. Tell the user they are siblings");
                    pw.println("                // of ONE request, not independent calls (do NOT fabricate a synthetic parent).");
                    pw.println("                if (rootApiCalls.size() > 1) {");
                    pw.println("                    table.append(\"ℹ️  The external caller's own entry span is not captured, so this request's downstream\\n\");");
                    pw.println("                    table.append(\"    calls appear below as separate top-level entries — they are SIBLINGS OF ONE REQUEST,\\n\");");
                    pw.println("                    table.append(\"    not independent calls. Each line's callee is the service it called (see the ──▶ / ↳).\\n\");");
                    pw.println("                    table.append(\"────────────────────────────────────────────────────────────────────────────────────────\\n\");");
                    pw.println("                }");
                    pw.println("                ");
                    pw.println("                // Render hierarchical tree");
                    pw.println("                AtomicInteger apiCounter = new AtomicInteger(1);");
                    pw.println("                for (String rootId : rootApiCalls) {");
                    pw.println("                    renderApiHierarchy(table, rootId, apiSpanById, apiChildren, processes, 0, apiCounter, \"\");");
                    pw.println("                }");
                    pw.println("            }");
                    pw.println("            ");
                    pw.println("            table.append(\"████████████████████████████████████████████████████████████████████████████████████████\\n\");");
                    pw.println("            return table.toString();");
                    pw.println("        } catch (Exception e) {");
                    pw.println("            return \"❌ Failed to generate trace table: \" + e.getMessage();");
                    pw.println("        }");
                    pw.println("    }");
                    pw.println();
                    pw.println("    private static String generateTraceSummary(JSONObject trace) {");
                    pw.println("        try {");
                    pw.println("            JSONArray spans = trace.getJSONArray(\"spans\");");
                    pw.println("            JSONObject processes = trace.optJSONObject(\"processes\");");
                    pw.println("            String traceId = trace.optString(\"traceID\", \"\");");
                    pw.println("            ");
                    pw.println("            StringBuilder summary = new StringBuilder();");
                    pw.println("            summary.append(\"🎯 TRACE SUMMARY\\n\");");
                    pw.println("            summary.append(\"═══════════════\\n\\n\");");
                    pw.println("            ");
                    pw.println("            // Count API calls by status");
                    pw.println("            int totalApis = 0;");
                    pw.println("            int successApis = 0;");
                    pw.println("            int errorApis = 0;");
                    pw.println("            Set<String> services = new HashSet<>();");
                    pw.println("            long totalDuration = 0L;");
                    pw.println("            long minDuration = Long.MAX_VALUE;");
                    pw.println("            long maxDuration = 0L;");
                    pw.println("            ");
                    pw.println("            for (int i = 0; i < spans.length(); i++) {");
                    pw.println("                JSONObject span = spans.getJSONObject(i);");
                    pw.println("                if (isApiCall(span)) {");
                    pw.println("                    String serviceName = getServiceName(span, processes);");
                    pw.println("                    ");
                    pw.println("                    // Filter out gateway services to match the API Call Hierarchy display");
                    pw.println("                    // 'gateway'/'proxy' substring already catches istio-ingressgateway,");
                    pw.println("                    // ts-gateway-service, api-gateway, front-proxy, etc. — SUT-agnostic.");
                    pw.println("                    boolean isGateway = serviceName.toLowerCase().contains(\"gateway\")");
                    pw.println("                                       || serviceName.toLowerCase().contains(\"proxy\");");
                    pw.println("                    if (isGateway) {");
                    pw.println("                        continue; // Skip gateway services in statistics");
                    pw.println("                    }");
                    pw.println("                    ");
                    pw.println("                    totalApis++;");
                    pw.println("                    String[] httpInfo = extractHttpInfo(span);");
                    pw.println("                    String status = httpInfo[2];");
                    pw.println("                    ");
                    pw.println("                    if (status.startsWith(\"2\")) successApis++;");
                    pw.println("                    else if (status.startsWith(\"4\") || status.startsWith(\"5\")) errorApis++;");
                    pw.println("                    ");
                    pw.println("                    services.add(serviceName);");
                    pw.println("                    ");
                    pw.println("                    long duration = span.optLong(\"duration\", 0L);");
                    pw.println("                    if (duration > 0) {");
                    pw.println("                        totalDuration += duration;");
                    pw.println("                        minDuration = Math.min(minDuration, duration);");
                    pw.println("                        maxDuration = Math.max(maxDuration, duration);");
                    pw.println("                    }");
                    pw.println("                }");
                    pw.println("            }");
                    pw.println("            ");
                    pw.println("            summary.append(\"📊 API Call Statistics:\\n\");");
                    pw.println("            summary.append(\"   Total API Calls: \").append(totalApis).append(\"\\n\");");
                    pw.println("            summary.append(\"   ✅ Successful: \").append(successApis).append(\"\\n\");");
                    pw.println("            summary.append(\"   ❌ Failed: \").append(errorApis).append(\"\\n\");");
                    pw.println("            summary.append(\"   🏢 Services Involved: \").append(services.size()).append(\"\\n\\n\");");
                    pw.println("            ");
                    pw.println("            if (totalApis > 0) {");
                    pw.println("                summary.append(\"⏱️ Performance Metrics:\\n\");");
                    pw.println("                summary.append(\"   Total Duration: \").append(formatDuration(totalDuration)).append(\"\\n\");");
                    pw.println("                summary.append(\"   Average Duration: \").append(formatDuration(totalDuration / totalApis)).append(\"\\n\");");
                    pw.println("                if (minDuration != Long.MAX_VALUE) {");
                    pw.println("                    summary.append(\"   Fastest Call: \").append(formatDuration(minDuration)).append(\"\\n\");");
                    pw.println("                    summary.append(\"   Slowest Call: \").append(formatDuration(maxDuration)).append(\"\\n\");");
                    pw.println("                }");
                    pw.println("                summary.append(\"\\n\");");
                    pw.println("            }");
                    pw.println("            ");
                    pw.println("            summary.append(\"🏢 Services in Trace:\\n\");");
                    pw.println("            for (String service : services) {");
                    pw.println("                summary.append(\"   • \").append(service).append(\"\\n\");");
                    pw.println("            }");
                    pw.println("            ");
                    pw.println("            return summary.toString();");
                    pw.println("        } catch (Exception e) {");
                    pw.println("            return \"❌ Failed to generate trace summary: \" + e.getMessage();");
                    pw.println("        }");
                    pw.println("    }");
                    pw.println();
                    pw.println("    private static boolean isApiCall(JSONObject span) {");
                    pw.println("        String opName = span.optString(\"operationName\", \"\");");
                    pw.println("        JSONArray tags = span.optJSONArray(\"tags\");");
                    pw.println("        ");
                    pw.println("        boolean hasHttpMethod = false;");
                    pw.println("        boolean hasHttpUrl = false;");
                    pw.println("        String httpUrl = \"\";");
                    pw.println("        String httpMethod = \"\";");
                    pw.println("        ");
                    pw.println("        if (tags != null) {");
                    pw.println("            for (int t = 0; t < tags.length(); t++) {");
                    pw.println("                JSONObject tag = tags.getJSONObject(t);");
                    pw.println("                String key = tag.optString(\"key\");");
                    pw.println("                String value = tag.optString(\"value\");");
                    pw.println("                ");
                    pw.println("                if (\"http.method\".equals(key)) { hasHttpMethod = true; httpMethod = value; }");
                    pw.println("                if (\"http.url\".equals(key) || \"http.target\".equals(key)) { hasHttpUrl = true; httpUrl = value; }");
                    pw.println("            }");
                    pw.println("        }");
                    pw.println("        ");
                    pw.println("        // Must be an actual API call");
                    pw.println("        if (hasHttpMethod && hasHttpUrl && (httpUrl.contains(\"/api/\") || httpMethod.matches(\"GET|POST|PUT|DELETE|PATCH\"))) {");
                    pw.println("            return true;");
                    pw.println("        }");
                    pw.println("        ");
                    pw.println("        if (opName.contains(\"/api/\") && (opName.contains(\"GET\") || opName.contains(\"POST\") || opName.contains(\"PUT\") || opName.contains(\"DELETE\") || opName.contains(\"PATCH\"))) {");
                    pw.println("            return true;");
                    pw.println("        }");
                    pw.println("        ");
                    pw.println("        return opName.matches(\"(GET|POST|PUT|DELETE|PATCH)\\\\s+/api/.*\");");
                    pw.println("    }");
                    pw.println();
                    pw.println("    private static String getServiceName(JSONObject span, JSONObject processes) {");
                    pw.println("        String processId = span.optString(\"processID\", \"\");");
                    pw.println("        if (processes != null && processes.has(processId)) {");
                    pw.println("            return processes.getJSONObject(processId).optString(\"serviceName\", \"unknown-service\");");
                    pw.println("        }");
                    pw.println("        return \"unknown-service\";");
                    pw.println("    }");
                    pw.println();
                    pw.println("    private static String[] extractHttpInfo(JSONObject span) {");
                    pw.println("        String method = \"\";");
                    pw.println("        String endpoint = \"\";");
                    pw.println("        String status = \"\";");
                    pw.println("        String opName = span.optString(\"operationName\", \"\");");
                    pw.println("        ");
                    pw.println("        JSONArray tags = span.optJSONArray(\"tags\");");
                    pw.println("        if (tags != null) {");
                    pw.println("            for (int t = 0; t < tags.length(); t++) {");
                    pw.println("                JSONObject tag = tags.getJSONObject(t);");
                    pw.println("                String key = tag.optString(\"key\");");
                    pw.println("                String value = tag.optString(\"value\");");
                    pw.println("                ");
                    pw.println("                if (\"http.method\".equals(key)) method = value;");
                    pw.println("                else if (\"http.url\".equals(key) || \"http.target\".equals(key)) endpoint = value;");
                    pw.println("                else if (\"http.status_code\".equals(key)) status = value;");
                    pw.println("            }");
                    pw.println("        }");
                    pw.println("        ");
                    pw.println("        // Extract from operation name if tags don't have the info");
                    pw.println("        if (method.isEmpty() && opName.matches(\"(GET|POST|PUT|DELETE|PATCH)\\\\s+.*\")) {");
                    pw.println("            String[] parts = opName.split(\"\\\\s+\", 2);");
                    pw.println("            if (parts.length == 2) {");
                    pw.println("                method = parts[0];");
                    pw.println("                endpoint = parts[1];");
                    pw.println("            }");
                    pw.println("        }");
                    pw.println("        ");
                    pw.println("        if (endpoint.isEmpty()) endpoint = opName;");
                    pw.println("        if (status.isEmpty()) status = \"?\";");
                    pw.println("        ");
                    pw.println("        return new String[]{method, endpoint, status};");
                    pw.println("    }");
                    pw.println();
                    pw.println("    private static boolean hasErrorTag(JSONObject span) {");
                    pw.println("        if (!span.has(\"tags\")) return false;");
                    pw.println("        ");
                    pw.println("        JSONArray tags = span.getJSONArray(\"tags\");");
                    pw.println("        for (int i = 0; i < tags.length(); i++) {");
                    pw.println("            JSONObject tag = tags.getJSONObject(i);");
                    pw.println("            if (\"error\".equals(tag.optString(\"key\")) && ");
                    pw.println("                \"bool\".equals(tag.optString(\"type\")) && ");
                    pw.println("                tag.optBoolean(\"value\", false)) {");
                    pw.println("                return true;");
                    pw.println("            }");
                    pw.println("        }");
                    pw.println("        return false;");
                    pw.println("    }");
                    pw.println();
                    pw.println("    private static String formatDuration(long durationMicros) {");
                    pw.println("        if (durationMicros < 1000) {");
                    pw.println("            return durationMicros + \"μs\";");
                    pw.println("        } else if (durationMicros < 1000000) {");
                    pw.println("            return String.format(\"%.1f\", durationMicros / 1000.0) + \"ms\";");
                    pw.println("        } else {");
                    pw.println("            return String.format(\"%.2f\", durationMicros / 1000000.0) + \"s\";");
                    pw.println("        }");
                    pw.println("    }");
                    pw.println();
                    pw.println("    private static void renderApiHierarchy(StringBuilder table, String spanId, Map<String, JSONObject> apiSpanById, Map<String, List<String>> apiChildren, JSONObject processes, int depth, AtomicInteger counter, String prefix) {");
                    pw.println("        JSONObject span = apiSpanById.get(spanId);");
                    pw.println("        if (span == null) return;");
                    pw.println("        ");
                    pw.println("        // Extract API information");
                    pw.println("        String serviceName = getServiceName(span, processes);");
                    pw.println("        String[] httpInfo = extractHttpInfo(span);");
                    pw.println("        String method = httpInfo[0];");
                    pw.println("        String endpoint = httpInfo[1];");
                    pw.println("        String status = httpInfo[2];");
                    pw.println("        long duration = span.optLong(\"duration\", 0L);");
                    pw.println("        ");
                    pw.println("        // Filter out gateway services - only show actual business APIs");
                    pw.println("        boolean isGateway = serviceName.toLowerCase().contains(\"gateway\")");
                    pw.println("                           || serviceName.toLowerCase().contains(\"proxy\");");
                    pw.println("        ");
                    pw.println("        // If this is a gateway, skip rendering but still process children");
                    pw.println("        if (isGateway) {");
                    pw.println("            // Render children recursively without showing this gateway span");
                    pw.println("            List<String> children = apiChildren.getOrDefault(spanId, Collections.emptyList());");
                    pw.println("            for (String childId : children) {");
                    pw.println("                renderApiHierarchy(table, childId, apiSpanById, apiChildren, processes, depth, counter, \"\");");
                    pw.println("            }");
                    pw.println("            return;");
                    pw.println("        }");
                    pw.println("        ");
                    pw.println("        // Check for errors in this span");
                    pw.println("        boolean hasError = hasErrorTag(span);");
                    pw.println("        String durationStr = formatDuration(duration);");
                    pw.println("        ");
                    pw.println("        // Use SINGLE status indicator - prioritize error detection over HTTP status");
                    pw.println("        String statusIcon;");
                    pw.println("        if (hasError) {");
                    pw.println("            statusIcon = \"❌\";  // Error detected in span");
                    pw.println("        } else if (status.startsWith(\"2\")) {");
                    pw.println("            statusIcon = \"✅\";  // HTTP 2xx success");
                    pw.println("        } else if (status.startsWith(\"4\") || status.startsWith(\"5\")) {");
                    pw.println("            statusIcon = \"❌\";  // HTTP 4xx/5xx error");
                    pw.println("        } else {");
                    pw.println("            statusIcon = \"❓\";  // Unknown/other status");
                    pw.println("        }");
                    pw.println("        ");
                    pw.println("        // Smart service name handling - keep essential info");
                    pw.println("        String displayService = serviceName;");
                    pw.println("        if (serviceName.startsWith(\"ts-\")) {");
                    pw.println("            // Keep meaningful part: ts-admin-order-service -> admin-order");
                    pw.println("            displayService = serviceName.substring(3);");
                    pw.println("            if (displayService.endsWith(\"-service\")) {");
                    pw.println("                displayService = displayService.substring(0, displayService.length() - 8);");
                    pw.println("            }");
                    pw.println("        }");
                    pw.println("        ");
                    pw.println("        // Smart endpoint handling - preserve important path info");
                    pw.println("        String displayEndpoint = endpoint;");
                    pw.println("        if (endpoint.startsWith(\"http\")) {");
                    pw.println("            try {");
                    pw.println("                // Extract path from full URL: http://host:port/path -> /path");
                    pw.println("                int pathStart = endpoint.indexOf(\"/\", 8);");
                    pw.println("                if (pathStart > 0) {");
                    pw.println("                    displayEndpoint = endpoint.substring(pathStart);");
                    pw.println("                }");
                    pw.println("            } catch (Exception ignored) {");
                    pw.println("                // If parsing fails, use original");
                    pw.println("            }");
                    pw.println("        }");
                    pw.println("        ");
                    pw.println("        // Compress common patterns but keep essential info");
                    pw.println("        if (displayEndpoint.contains(\"/api/v1/\")) {");
                    pw.println("            displayEndpoint = displayEndpoint.replace(\"/api/v1/\", \"/v1/\");");
                    pw.println("        }");
                    pw.println("        ");
                    pw.println("        // Create compact tree line");
                    pw.println("        StringBuilder line = new StringBuilder();");
                    pw.println("        ");
                    pw.println("        // Minimal indentation for space efficiency");
                    pw.println("        for (int i = 0; i < depth; i++) {");
                    pw.println("            line.append(\"  \");  // 2 spaces per level");
                    pw.println("        }");
                    pw.println("        ");
                    pw.println("        // Add compact connector");
                    pw.println("        if (depth > 0) {");
                    pw.println("            line.append(\"├─\");");
                    pw.println("        }");
                    pw.println("        ");
                    pw.println("        // Build comprehensive format with full information");
                    pw.println("        int apiNumber = counter.getAndIncrement();");
                    pw.println("        ");
                    pw.println("        // Main line: [#] Status Service[ ──▶ callee] Method HTTPStatus(time)");
                    pw.println("        // For an errored client/egress span, surface the CALLEE on the headline so the");
                    pw.println("        // direction is explicit: the failure is in the callee (downstream), not in the");
                    pw.println("        // caller service whose client span merely RECORDED the failed call.");
                    pw.println("        String __callee = \"\";");
                    pw.println("        if (statusIcon.equals(\"❌\") && endpoint != null && endpoint.startsWith(\"http\")) {");
                    pw.println("            int __h = endpoint.indexOf(\"://\");");
                    pw.println("            String __rest = __h >= 0 ? endpoint.substring(__h + 3) : endpoint;");
                    pw.println("            int __cut = __rest.length();");
                    pw.println("            int __ci = __rest.indexOf(':'); if (__ci >= 0 && __ci < __cut) __cut = __ci;");
                    pw.println("            int __si = __rest.indexOf('/'); if (__si >= 0 && __si < __cut) __cut = __si;");
                    pw.println("            if (__cut > 0) __callee = \" ──▶ \" + __rest.substring(0, __cut);");
                    pw.println("        }");
                    pw.println("        line.append(String.format(\"[%d] %s %s%s %s %s(%s)\", ");
                    pw.println("            apiNumber, statusIcon, displayService, __callee, method, status, durationStr));");
                    pw.println("        table.append(line.toString()).append(\"\\n\");");
                    pw.println("        ");
                    pw.println("        // Detail line: Full endpoint path (indented)");
                    pw.println("        StringBuilder detailLine = new StringBuilder();");
                    pw.println("        for (int i = 0; i < depth; i++) {");
                    pw.println("            detailLine.append(\"  \");");
                    pw.println("        }");
                    pw.println("        if (depth > 0) {");
                    pw.println("            detailLine.append(\"   \");  // Extra indent for detail line");
                    pw.println("        } else {");
                    pw.println("            detailLine.append(\"    \");  // Indent for root detail line");
                    pw.println("        }");
                    pw.println("        detailLine.append(\"↳ \").append(endpoint);");
                    pw.println("        table.append(detailLine.toString()).append(\"\\n\");");
                    pw.println("        ");
                    pw.println("        // Render children recursively");
                    pw.println("        List<String> children = apiChildren.getOrDefault(spanId, Collections.emptyList());");
                    pw.println("        for (String childId : children) {");
                    pw.println("            renderApiHierarchy(table, childId, apiSpanById, apiChildren, processes, depth + 1, counter, \"\");");
                    pw.println("        }");
                    pw.println("    }");
                    pw.println();
                    
                    // Add parameter error analysis method
                    pw.println("    private static void analyzeAndRecordParameterErrors(JSONObject trace, Map<String, String> stepParameters) {");
                    pw.println("        try {");
                    pw.println("            // Get LLM properties from system properties");
                    pw.println("            Map<String, String> llmProperties = new HashMap<>();");
                    pw.println("            llmProperties.put(\"llm.enabled\", System.getProperty(\"llm.enabled\", \"true\"));");
                    pw.println("            llmProperties.put(\"llm.model.type\", System.getProperty(\"llm.model.type\", \"ollama\"));");
                    pw.println("            llmProperties.put(\"llm.ollama.enabled\", System.getProperty(\"llm.ollama.enabled\", \"true\"));");
                    pw.println("            llmProperties.put(\"llm.ollama.url\", System.getProperty(\"llm.ollama.url\", \"http://localhost:11434\"));");
                    pw.println("            llmProperties.put(\"llm.ollama.model\", System.getProperty(\"llm.ollama.model\", \"gemma3:4b\"));");
                    pw.println("            llmProperties.put(\"llm.gemini.enabled\", System.getProperty(\"llm.gemini.enabled\", \"false\"));");
                    pw.println("            llmProperties.put(\"llm.gemini.api.key\", System.getProperty(\"llm.gemini.api.key\", \"\"));");
                    pw.println("            llmProperties.put(\"llm.gemini.model\", System.getProperty(\"llm.gemini.model\", \"gemini-2.0-flash-exp\"));");
                    pw.println("            llmProperties.put(\"llm.gemini.api.url\", System.getProperty(\"llm.gemini.api.url\", \"https://generativelanguage.googleapis.com/v1beta/models\"));");
                    pw.println("            ");
                    pw.println("            // Analyze parameter errors");
                    pw.println("            io.mist.core.smart.ParameterErrorAnalyzer.ParameterErrorAnalysisResult result = ");
                    pw.println("                io.mist.core.smart.ParameterErrorAnalyzer.analyzeParameterErrors(trace, stepParameters, llmProperties);");
                    pw.println("            ");
                    pw.println("            if (result.hasParameterErrors()) {");
                    pw.println("                System.out.println(\"🔍 Parameter Error Analysis: Found \" + result.getIdentifiedErrors().size() + \" parameter-related errors\");");
                    pw.println("                ");
                    pw.println("                // Load and update the input fetch registry");
                    pw.println("                String registryPath = System.getProperty(\"smart.input.fetch.registry.path\");");
                    pw.println("                if (registryPath != null && !registryPath.isEmpty()) {");
                    pw.println("                    try {");
                    pw.println("                        java.io.File registryFile = new java.io.File(registryPath);");
                    pw.println("                        io.mist.core.smart.InputFetchRegistry registry;");
                    pw.println("                        ");
                    pw.println("                        // JVM-wide lock on the registry class serialises load+mutate+save across");
                    pw.println("                        // parallel test threads. Audit (#21) flagged: only saveToFile() is");
                    pw.println("                        // synchronized today, but each thread instantiates its own registry via");
                    pw.println("                        // loadFromFile, so the per-instance lock provides no cross-thread guard.");
                    pw.println("                        // Without this monitor, two threads racing on load/mutate/save would");
                    pw.println("                        // produce a lost-update where the second writer overwrites the first's");
                    pw.println("                        // contribution. Performance cost: the registry write path is failure-only");
                    pw.println("                        // and the critical section is small relative to per-scenario cost.");
                    pw.println("                        synchronized (io.mist.core.smart.InputFetchRegistry.class) {");
                    pw.println("                        if (registryFile.exists()) {");
                    pw.println("                            registry = io.mist.core.smart.InputFetchRegistry.loadFromFile(registryFile);");
                    pw.println("                        } else {");
                    pw.println("                            registry = new io.mist.core.smart.InputFetchRegistry();");
                    pw.println("                        }");
                    pw.println("                        ");
                    pw.println("                        // Record each parameter error; skip YAML flush if nothing changed (dedup).");
                    pw.println("                        boolean registryChanged = false;");
                    pw.println("                        for (io.mist.core.smart.ParameterError error : result.getIdentifiedErrors()) {");
                    pw.println("                            if (registry.isAlreadyRegistered(error.getApiEndpoint(), error.getParameterName(), error)) {");
                    pw.println("                                continue;");
                    pw.println("                            }");
                    pw.println("                            registry.addParameterError(error.getApiEndpoint(), error.getParameterName(), error);");
                    pw.println("                            registryChanged = true;");
                    pw.println("                            System.out.println(\"📝 Recorded error: \" + error.getParameterName() + \" -> \" + error.getErrorType() + \": \" + error.getErrorReason());");
                    pw.println("                        }");
                    pw.println("                        ");
                    pw.println("                        // Save updated registry only when at least one new entry was added.");
                    pw.println("                        if (registryChanged) {");
                    pw.println("                            registry.saveToFile(registryFile);");
                    pw.println("                            System.out.println(\"💾 Updated parameter error registry at: \" + registryPath);");
                    pw.println("                        }");
                    pw.println("                        } // end synchronized(InputFetchRegistry.class)");
                    pw.println("                        ");
                    pw.println("                    } catch (Exception e) {");
                    pw.println("                        System.err.println(\"⚠️ Failed to update parameter error registry: \" + e.getMessage());");
                    pw.println("                        e.printStackTrace();");
                    pw.println("                    }");
                    pw.println("                } else {");
                    pw.println("                    System.err.println(\"⚠️ Parameter error registry path not configured. Set 'smart.input.fetch.registry.path' property.\");");
                    pw.println("                }");
                    pw.println("            } else {");
                    pw.println("                System.out.println(\"✅ No parameter-related errors detected in trace\");");
                    pw.println("            }");
                    pw.println("            ");
                    pw.println("        } catch (Exception e) {");
                    pw.println("            System.err.println(\"⚠️ Error during parameter error analysis: \" + e.getMessage());");
                    pw.println("            e.printStackTrace();");
                    pw.println("        }");
                    pw.println("    }");
                    pw.println();
                }

                /* ---------- Rest-assured global setup --------------------------- */
                pw.println("    @BeforeClass");
                pw.println("    public static void setupRestAssured() {");
                if (baseURI != null && !baseURI.isEmpty()) {
                    pw.println("        RestAssured.baseURI = \"" + escape(baseURI) + "\";");
                }
                // Configure HTTP timeouts to prevent hanging on unresponsive endpoints
                int connectTimeoutMs = Integer.parseInt(System.getProperty("http.connect.timeout.ms", "10000"));
                int socketTimeoutMs  = Integer.parseInt(System.getProperty("http.socket.timeout.ms", "30000"));
                pw.println("        RestAssured.config = RestAssured.config()");
                pw.println("            .httpClient(io.restassured.config.HttpClientConfig.httpClientConfig()");
                pw.println("                .setParam(\"http.connection.timeout\", " + connectTimeoutMs + ")");
                pw.println("                .setParam(\"http.socket.timeout\", " + socketTimeoutMs + "));");
                if (proxyHostPort != null && !proxyHostPort.isEmpty()) {
                    String[] p = proxyHostPort.split(":", 2);
                    pw.println("        RestAssured.proxy = RestAssured.proxy(\"" + p[0] + "\"," + p[1] + ");");
                }
                if (allureReport) {
                    pw.println("        // Configure Allure results directory");
                    pw.println("        System.setProperty(\"allure.results.directory\", \"target/allure-results\");");
                    pw.println("        // ⚠️ AllureRestAssured filter disabled - causes duplicate request logging");
                    pw.println("        // We use manual, controlled attachments instead for cleaner reports");
                    pw.println("        // RestAssured.filters(new AllureRestAssured());");
                }
                // Initialize LLM singletons once for the entire test class
                pw.println("        // Initialize LLM validation singletons (ONCE per class, not per test)");
                pw.println("        if (LLM_VALIDATION_ENABLED) {");
                pw.println("            llmValidator = new io.mist.core.generation.ZeroShotLLMGenerator();");
                pw.println("        }");
                pw.println();
                // Phase 2.F: reconstruct the Trace Shape Oracle from the JSON file MistRunner
                // populated. The path mirrors what MistRunner used so the same invariants apply.
                pw.println("        // Trace Shape Oracle bootstrap (Phase 2.F) — reads the persisted invariant store.");
                pw.println("        // The file is created/refreshed by MistRunner's bootstrap on its cold-start pass.");
                pw.println("        try {");
                if (traceShapeStorePath != null) {
                    pw.println("            java.nio.file.Path tsoPath = java.nio.file.Paths.get(\"" + escape(traceShapeStorePath) + "\");");
                    pw.println("            oracle = new TraceShapeOracle(new ShapeInvariantStore(tsoPath));");
                } else {
                    pw.println("            oracle = new TraceShapeOracle(new ShapeInvariantStore());");
                }
                pw.println("        } catch (Throwable tsoEx) {");
                pw.println("            System.err.println(\"[Trace Shape Oracle] init failed: \" + tsoEx.getMessage());");
                pw.println("            oracle = null;");
                pw.println("        }");
                pw.println("        // Phase 4.x: wire the LLM-backed envelope classifier so ResponseEnvelope can");
                pw.println("        // classify+cache a previously-unseen 2xx body value (the paper's one-shot-LLM-");
                pw.println("        // on-first-2xx behaviour). Reuses the existing validateResponse soft-error model.");
                pw.println("        if (oracle != null && LLM_VALIDATION_ENABLED && llmValidator != null) {");
                pw.println("            oracle.setEnvelopeClassifier((rootApi, field, value, body) -> {");
                pw.println("                try {");
                pw.println("                    int __sp = rootApi == null ? -1 : rootApi.indexOf(' ');");
                pw.println("                    String __m = __sp > 0 ? rootApi.substring(0, __sp) : \"GET\";");
                pw.println("                    String __p = __sp > 0 ? rootApi.substring(__sp + 1) : (rootApi == null ? \"\" : rootApi);");
                pw.println("                    io.mist.core.generation.ZeroShotLLMGenerator.ValidationResult __vr =");
                pw.println("                        llmValidator.validateResponse(200, body, \"\", __m, __p);");
                pw.println("                    return __vr == null ? null : Boolean.valueOf(__vr.isFailed());");
                pw.println("                } catch (Throwable __t) { return null; }");
                pw.println("            });");
                pw.println("        }");
                pw.println("    }");
                pw.println();

                /* ---------- resolve dominant service for class-level grouping -- */
                // Determine parentSuite once per class so every test method emits the
                // same value, preventing Allure from creating a second grouping node
                // under the Java class name when a test has 0 steps.
                String classParentSuite = "Unknown Service";
                String classNormalizedSuite = "";
                for (TestCase _raw : testCases) {
                    if (_raw instanceof MultiServiceTestCase) {
                        MultiServiceTestCase _mstc = (MultiServiceTestCase) _raw;
                        if (!_mstc.getSteps().isEmpty()) {
                            MultiServiceTestCase.StepCall _first = _mstc.getSteps().get(0);
                            if (_first.getServiceName() != null && !_first.getServiceName().isEmpty()) {
                                classParentSuite = _first.getServiceName();
                            }
                            if (_first.getMethod() != null) {
                                String _m = _first.getMethod().getMethod() != null
                                        ? _first.getMethod().getMethod().toUpperCase() : "GET";
                                String _p = _first.getMethod().getTestPath() != null
                                        ? _first.getMethod().getTestPath()
                                        : (_first.getPath() != null ? _first.getPath() : "");
                                classNormalizedSuite = _m + " " + _p;
                            }
                            break;
                        }
                    }
                }

                /* ---------- one @Test per scenario ----------------------------- */
                int scenarioIdx = 1;
                for (TestCase raw : testCases) {

                    if (!(raw instanceof MultiServiceTestCase)) continue;
                    MultiServiceTestCase scenario = (MultiServiceTestCase) raw;

                    // Use the meaningful test name from the generator instead of generic counter
                    String testMethodName = scenario.getOperationId();
                    if (testMethodName == null || testMethodName.isEmpty() || testMethodName.equals("workflow")) {
                        // Fallback to counter-based naming if no meaningful name is set
                        testMethodName = "testScenario" + scenarioIdx;
                    }

                    pw.println("    @Test");
                    pw.println("    public void " + testMethodName + "() throws Exception {");

                    /* ------------ Record test case for fault detection tracking ---------- */
                    pw.println("        // Record test execution for fault detection tracking");
                    pw.println("        io.mist.core.analysis.FaultDetectionTracker.getInstance()");
                    pw.println("            .recordTestCase(this.getClass().getName(), \"" + testMethodName + "\");");
                    pw.println();

                    /* ------------ Phase 2: target service + param for trace attribution ---- */
                    // Resolve at write-time so the generated code carries them as literal
                    // strings. attribute() runs inside attachJaegerTrace per step; for
                    // positive tests (and for negative tests where target root cannot be
                    // resolved) both stay null and FaultDetectionTracker skips attribution.
                    //
                    // Format mismatch: MistGenerator writes the target id as "Root N"
                    // (display text consumed elsewhere) but StepCall.hierarchicalId is
                    // "RN" / "R1.2" etc. — normalize the prefix before matching, and do
                    // NOT fall back to a different step because using the wrong service
                    // produces phantom UPSTREAM/WRONG_PARAM counts in the attribution
                    // rollup. Null is the honest signal.
                    MultiServiceTestCase __mstcForTarget = (MultiServiceTestCase) scenario;
                    String __targetServiceLiteral = "null";
                    String __targetParamLiteral = "null";
                    if (__mstcForTarget.getFaulty() && !__mstcForTarget.getFaultyParameters().isEmpty()) {
                        String __targetRootId = __mstcForTarget.getTargetFaultRootId();
                        // Normalize: "Root 2" -> "R2". Leave "R1.2" / "R1" alone.
                        String __normalized = __targetRootId;
                        if (__normalized != null && __normalized.startsWith("Root ")) {
                            __normalized = "R" + __normalized.substring("Root ".length()).trim();
                        }
                        String __resolvedSvc = null;
                        if (__normalized != null && !__normalized.isEmpty()) {
                            for (MultiServiceTestCase.StepCall __s : __mstcForTarget.getSteps()) {
                                if (__normalized.equals(__s.getHierarchicalId())) {
                                    __resolvedSvc = __s.getServiceName();
                                    break;
                                }
                            }
                        }
                        if (__resolvedSvc != null && !__resolvedSvc.isEmpty()) {
                            __targetServiceLiteral = "\"" + escape(__resolvedSvc) + "\"";
                        }
                        // First faulty param: "paramName=faultyValue" → take the name half.
                        // MistGenerator's invariant: every faulty entry contains '=', so
                        // indexOf('=') > 0. The check is defensive only.
                        String __firstFault = __mstcForTarget.getFaultyParameters().get(0);
                        int __eq = __firstFault == null ? -1 : __firstFault.indexOf('=');
                        if (__eq > 0) {
                            __targetParamLiteral = "\"" + escape(__firstFault.substring(0, __eq)) + "\"";
                        }
                    }
                    pw.println("        // Phase 2: trace-attribution target (negative tests only).");
                    pw.println("        final String __targetService = " + __targetServiceLiteral + ";");
                    pw.println("        final String __targetParam = " + __targetParamLiteral + ";");
                    pw.println();

                    /* ------------ Set up Test Case Enhancer capture ---------- */
                    MultiServiceTestCase mstc = (MultiServiceTestCase) scenario;
                    String firstEndpoint = "";
                    String firstMethod = "POST";
                    String firstService = "";
                    if (!mstc.getSteps().isEmpty()) {
                        MultiServiceTestCase.StepCall firstStep = mstc.getSteps().get(0);
                        firstEndpoint = firstStep.getPath() != null ? firstStep.getPath() : "";
                        firstMethod = firstStep.getMethod() != null && firstStep.getMethod().getMethod() != null 
                                    ? firstStep.getMethod().getMethod().toUpperCase() : "POST";
                        firstService = firstStep.getServiceName() != null ? firstStep.getServiceName() : "";
                    }
                    // Fix 3 Layer 1: resolution-aware classifier — a positive scenario whose
                    // generator could not resolve every parameter falls back to synthetic
                    // placeholders (FALLBACK_/LLM_EMPTY/STEP1_/VAL_); the SUT will reject those,
                    // so the oracle expects an error response just as for Sniper-designed faults.
                    // EXCEPTION — two-phase Phase-A rescue mode: keep such a positive a POSITIVE
                    // (expects 2xx) so its 400 fails the positive assertion and the enhancer rescues it
                    // from the SUT error message; the rescued 2xx then harvests VERIFIED_VALID. Genuine
                    // faulty variants (scenario.getFaulty()) stay negative regardless.
                    boolean isNegativeTest = scenario.getFaulty()
                            || (mstc.hasSyntheticPlaceholder() && !phaseARescuePlaceholders);
                    pw.println();
                    
                    /* ------------ Allure Taxonomy: parentSuite / suite / subSuite + @DisplayName ---------- */
                    if (allureReport) {
                        String serviceSuite;
                        String flowPath;
                        String subSuiteLabel = createCleanSubSuiteLabel(mstc, testMethodName);

                        if (!mstc.getSteps().isEmpty()) {
                            serviceSuite = firstService.isEmpty() ? classParentSuite : firstService;
                            flowPath = buildFlowPath(mstc);
                        } else {
                            serviceSuite = classParentSuite;
                            flowPath = classNormalizedSuite;
                        }

                        pw.println("        // Allure Suite Taxonomy: Service -> <JUnit4 auto-suite from @DisplayName> -> Variant");
                        pw.println("        // IMPORTANT: we do NOT emit an explicit 'suite' label here.  The AllureJunit4 listener");
                        pw.println("        // already auto-adds one derived from the class's @DisplayName (e.g.");
                        pw.println("        // 'Flow_Scenario_2386 | POST /api/v1/...').  Emitting another 'suite' label would");
                        pw.println("        // be additive (Allure does not override on duplicate label names) and the same");
                        pw.println("        // tests would appear TWICE in the Suites view under two different suite folders.");
                        pw.println("        Allure.label(\"parentSuite\", \"" + escape(serviceSuite) + "\");");
                        pw.println("        Allure.label(\"subSuite\", \"" + escape(subSuiteLabel) + "\");");
                        pw.println();
                    }
                    
                    /* ------------ Add Allure metadata for negative tests ---------- */
                    {
                        if (allureReport && scenario.getFaulty() && !mstc.getFaultyParameters().isEmpty()) {

                            String targetRoot     = mstc.getTargetFaultRootId();
                            String faultType      = mstc.getFaultTypeCategory();
                            String faultLocation  = mstc.getTargetFaultParamLocation();
                            String targetRootSafe = (targetRoot != null && !targetRoot.isEmpty()) ? targetRoot : "Unknown Root";
                            String faultTypeSafe  = (faultType  != null && !faultType.isEmpty())  ? faultType  : "UNKNOWN";
                            String faultLocSafe   = (faultLocation != null && !faultLocation.isEmpty()) ? faultLocation : "unknown";

                            String faultyParamsEscaped        = escapeJavaString(String.join(", ", mstc.getFaultyParameters()));
                            String faultyParamsNewlineEscaped = escapeJavaString(String.join("\\n", mstc.getFaultyParameters()));

                            pw.println("        // NEGATIVE TEST METADATA (Targeted Fault Injection)");
                            pw.println("        Allure.parameter(\"🚨 Test Type\", \"NEGATIVE (Targeted Fault Injection)\");");
                            pw.println("        Allure.parameter(\"🎯 Target API\", \"" + escapeJavaString(targetRootSafe) + "\");");
                            pw.println("        Allure.parameter(\"💥 Fault Type\", \"" + escapeJavaString(faultTypeSafe) + "\");");
                            // Parameter location names the request slot the invalid value lands in
                            // (path / query / header / cookie / body). Disambiguates same-name
                            // parameters across locations so the Allure entry reads like
                            // "header X-API-Key" rather than just "X-API-Key".
                            pw.println("        Allure.parameter(\"📍 Fault Location\", \"" + escapeJavaString(faultLocSafe) + "\");");
                            pw.println("        Allure.parameter(\"🔴 Injected Payload\", \"" + faultyParamsEscaped + "\");");
                            pw.println("        Allure.description(\"**⚠️ Targeted Fault Injection Test**\\n\\n\" +");
                            pw.println("                         \"This test validates the error-handling of **" + escapeJavaString(targetRootSafe) + "** \" +");
                            pw.println("                         \"when injected with a **" + escapeJavaString(faultTypeSafe) + "** payload, \" +");
                            pw.println("                         \"while ensuring all preceding APIs execute successfully.\\n\\n\" +");
                            pw.println("                         \"**Injected invalid values:**\\n\" +");
                            pw.println("                         \"" + faultyParamsNewlineEscaped + "\\n\\n\" +");
                            pw.println("                         \"This test expects a 4XX or 5XX error response from the targeted API. \" +");
                            pw.println("                         \"If it returns 2XX, the test will FAIL.\");");

                            for (String faultyParam : mstc.getFaultyParameters()) {
                                pw.println("        io.mist.core.enhancer.TestResultCapture.addInvalidParameter(\"" + escapeJavaString(faultyParam) + "\");");
                            }
                            pw.println();
                        }
                    }
                    
                    /* ------------ Add Allure metadata for status code exploration tests ---------- */
                    {
                        if (allureReport && mstc.isStatusCodeExplorationTest()) {
                            int targetCode = mstc.getTargetStatusCode();
                            String targetDescription = mstc.getTargetStatusCodeDescription();

                            pw.println("        // 🎯 STATUS CODE EXPLORATION TEST METADATA");
                            pw.println("        Allure.parameter(\"🎯 Test Type\", \"STATUS CODE EXPLORATION\");");
                            pw.println("        Allure.parameter(\"🔍 Target Status Code\", \"" + targetCode + "\");");
                            pw.println("        Allure.parameter(\"📊 Target Category\", \"" + escapeJavaString(targetDescription) + "\");");
                            pw.println("        Allure.label(\"coverage_type\", \"STATUS_CODE\");");
                            pw.println("        Allure.label(\"target_status\", \"" + targetCode + "\");");
                            pw.println("        Allure.description(\"**🎯 This is a STATUS CODE EXPLORATION test.**\\n\\n\" +");
                            pw.println("                         \"This test was automatically generated to explore HTTP status code coverage.\\n\" +");
                            pw.println("                         \"Target: **" + targetCode + " " + escapeJavaString(targetDescription) + "**\\n\\n\" +");
                            pw.println("                         \"The test parameters have been modified to attempt triggering this specific status code.\");");
                            pw.println();
                            
                            // Handle auth manipulation for 401/403 exploration
                            io.mist.core.auth.AuthManipulationStrategy.AuthConfig authConfig = mstc.getAuthManipulation();
                            if (authConfig != null && !authConfig.isAuthEnabled()) {
                                pw.println("        // 🔐 Auth manipulation: REMOVE_AUTH for status code exploration");
                                pw.println("        Allure.parameter(\"🔐 Auth Manipulation\", \"" + authConfig.getManipulationType().getDescription() + "\");");
                            } else if (authConfig != null && authConfig.getToken() != null) {
                                pw.println("        // 🔐 Auth manipulation: Using modified token");
                                pw.println("        Allure.parameter(\"🔐 Auth Manipulation\", \"" + authConfig.getManipulationType().getDescription() + "\");");
                            }
                            pw.println();
                        }
                    }

                    /* ---------- auth (delegated to MstAuthHandler) ----------------
                     * The handler picks the strategy from auth.mode in the MST
                     * configuration:  none / static_token / per_jvm (default)
                     * / per_test.  PER_JVM caches the token across the whole
                     * suite so we no longer hit /login per test.
                     *
                     * Per-test override (AuthManipulationStrategy: INVALID_TOKEN
                     * / EXPIRED_TOKEN / REMOVE_AUTH for status-code exploration)
                     * is forwarded via the two locals below and consumed at the
                     * per-step header stamp.
                     */
                    String __overrideTokenLit = "null";
                    boolean __disableAuthLit = false;
                    io.mist.core.auth.AuthManipulationStrategy.AuthConfig __mstAcWrite =
                            mstc.getAuthManipulation();
                    if (__mstAcWrite != null) {
                        if (!__mstAcWrite.isAuthEnabled()) {
                            __disableAuthLit = true;
                        } else if (__mstAcWrite.getToken() != null) {
                            __overrideTokenLit = "\"" + escape(__mstAcWrite.getToken()) + "\"";
                        }
                    }

                    pw.println("        // Per-test auth override (AuthManipulationStrategy). null = use default.");
                    pw.println("        final String  __mstOverrideToken = " + __overrideTokenLit + ";");
                    pw.println("        final boolean __mstDisableAuth   = " + __disableAuthLit + ";");
                    pw.println("        final java.util.concurrent.atomic.AtomicBoolean loginSucceeded  = new java.util.concurrent.atomic.AtomicBoolean(true);");
                    pw.println("        final java.util.concurrent.atomic.AtomicBoolean scenarioFailed = new java.util.concurrent.atomic.AtomicBoolean(false);");

                    if (allureReport) {
                        pw.println("        // 🔐 STEP 0: Authentication (delegates to MstAuthHandler)");
                        pw.println("        Allure.step(\"🔐 Step 0: Authentication\", () -> {");
                        pw.println("            try {");
                        pw.println("                io.mist.cli.auth.MstAuthHandler.Mode __mstMode = io.mist.cli.auth.MstAuthHandler.getMode();");
                        pw.println("                Allure.parameter(\"🔐 Auth Mode\", __mstMode.name());");
                        pw.println("                if (__mstMode == io.mist.cli.auth.MstAuthHandler.Mode.NONE) {");
                        pw.println("                    Allure.parameter(\"🎯 Result\", \"⏭️ SKIPPED (auth.mode=none)\");");
                        pw.println("                } else {");
                        pw.println("                    boolean __mstReady = io.mist.cli.auth.MstAuthHandler.ensureReady();");
                        pw.println("                    if (!__mstReady) loginSucceeded.set(false);");
                        pw.println("                    Allure.parameter(\"🎯 Result\", __mstReady ? \"✅ READY\" : \"❌ FAILED\");");
                        pw.println("                }");
                        pw.println("            } catch (Throwable __mstAuthErr) {");
                        pw.println("                loginSucceeded.set(false);");
                        pw.println("                Allure.parameter(\"🎯 Result\", \"❌ FAILED (\" + __mstAuthErr.getClass().getSimpleName() + \")\");");
                        pw.println("                Allure.addAttachment(\"💥 Auth Error\", \"text/plain\", String.valueOf(__mstAuthErr.getMessage()));");
                        pw.println("            }");
                        pw.println("        });");
                    } else {
                        pw.println("        try {");
                        pw.println("            if (io.mist.cli.auth.MstAuthHandler.getMode() != io.mist.cli.auth.MstAuthHandler.Mode.NONE");
                        pw.println("                    && !io.mist.cli.auth.MstAuthHandler.ensureReady()) {");
                        pw.println("                loginSucceeded.set(false);");
                        pw.println("            }");
                        pw.println("        } catch (Throwable __mstAuthErr) {");
                        pw.println("            loginSucceeded.set(false);");
                        pw.println("        }");
                    }
                    pw.println();

                    /* ------------ every StepCall -------------------------------- */
                    pw.println("        // Step execution results tracking");
                    pw.println("        final java.util.Map<Integer, Boolean> stepResults = new java.util.HashMap<>();");
                    pw.println("        final java.util.Map<Integer, String> capturedOutputs = new java.util.HashMap<>();");
                    pw.println("        // Parameter tracking for error analysis");
                    pw.println("        final java.util.Map<String, String> allStepParameters = new java.util.HashMap<>();");
                    pw.println();

                    int stepIdx = 1;
                    for (MultiServiceTestCase.StepCall step : scenario.getSteps()) {

                        // Phase 1: a per-step parameter map scoped to THIS step only.
                        // allStepParameters (declared at method scope above) is the
                        // cumulative map used by attachJaegerTrace's Allure attachment
                        // — keeping it cumulative is intentional so the trace UI shows
                        // all params for the scenario. But the Phase 1
                        // recordParameterSuccess loop must iterate ONLY this step's
                        // params so earlier steps' params aren't mis-attributed to this
                        // step's endpoint.
                        pw.println("        final java.util.Map<String, String> stepParams" + stepIdx
                                + " = new java.util.HashMap<>();");

                        String verb = step.getMethod() == null || step.getMethod().getMethod() == null
                                ? "get"
                                : step.getMethod().getMethod().toLowerCase();
                        
                        // Use hierarchical ID from the generator (R1, R2, R1.1, etc.)
                        String hierIdRaw = step.getHierarchicalId();
                        String stepLabel;
                        if (hierIdRaw != null && !hierIdRaw.isEmpty()) {
                            if (step.isTopLevelRoot()) {
                                stepLabel = hierIdRaw.replace("R", "Root ");
                            } else {
                                stepLabel = hierIdRaw;
                            }
                        } else {
                            stepLabel = "Step " + stepIdx;
                        }

                        String expectedStatusDisplay;
                        if (mstc.isStatusCodeExplorationTest() && mstc.getTargetStatusCode() > 0) {
                            expectedStatusDisplay = String.valueOf(mstc.getTargetStatusCode()) + " (exploration)";
                        } else if (isNegativeTest) {
                            expectedStatusDisplay = "not " + step.getExpectedStatus();
                        } else {
                            expectedStatusDisplay = String.valueOf(step.getExpectedStatus());
                        }
                        String stepTitle = stepLabel + ": "
                                + step.getServiceName() + " "
                                + verb.toUpperCase() + " " + step.getPath()
                                + " [expect " + expectedStatusDisplay + "]";

                        pw.println("        // " + escape(stepTitle));
                        
                        // 🔧 Test Case Enhancer: Per-step metadata and parameter capture
                        {
                            String stepEndpoint = step.getPath() != null ? step.getPath() : "";
                            String stepVerb = step.getMethod() != null && step.getMethod().getMethod() != null
                                    ? step.getMethod().getMethod().toUpperCase() : "GET";
                            String stepService = step.getServiceName() != null ? step.getServiceName() : "";
                            
                            pw.println("        io.mist.core.enhancer.TestResultCapture.setStepMetadata(");
                            pw.println("            " + stepIdx + ", \"" + escape(stepEndpoint) + "\", \"" + stepVerb + "\", ");
                            pw.println("            \"" + escape(stepService) + "\", " + isNegativeTest + ");");
                            
                            // Collect data-injected parameter names from paramDependencies
                            java.util.Set<String> dataInjectedParams = new java.util.HashSet<>();
                            if (step.getParamDependencies() != null) {
                                dataInjectedParams.addAll(step.getParamDependencies().keySet());
                            }
                            // Also include provenance bindings for merged-root steps
                            if (step.isMergedRootStep() && step.getProvenanceBindings() != null) {
                                dataInjectedParams.addAll(step.getProvenanceBindings().keySet());
                            }
                            
                            // Register structurally locked params for the enhancer's post-execution filter
                            if (!dataInjectedParams.isEmpty()) {
                                StringBuilder lockedSetLiteral = new StringBuilder("java.util.Set.of(");
                                boolean first = true;
                                for (String lp : dataInjectedParams) {
                                    if (!first) lockedSetLiteral.append(", ");
                                    lockedSetLiteral.append("\"").append(escape(lp)).append("\"");
                                    first = false;
                                }
                                lockedSetLiteral.append(")");
                                pw.println("        io.mist.core.enhancer.TestResultCapture.setLockedDependencyParams("
                                        + stepIdx + ", " + lockedSetLiteral + ");");
                            }
                            
                            // Capture body field parameters for this step
                            Map<String, String> stepBodyFields = new LinkedHashMap<>();
                            if (step.getBodyFields() != null) {
                                stepBodyFields.putAll(step.getBodyFields());
                            }
                            Map<String, io.mist.core.spec.TestParameter> stepParamMeta = new HashMap<>();
                            if (step.getMethod() != null && step.getMethod().getTestParameters() != null) {
                                for (io.mist.core.spec.TestParameter tp : step.getMethod().getTestParameters()) {
                                    stepParamMeta.put(tp.getName(), tp);
                                }
                            }
                            
                            for (Map.Entry<String, String> field : stepBodyFields.entrySet()) {
                                String paramName = field.getKey();
                                String paramValue = field.getValue();
                                boolean isDI = dataInjectedParams.contains(paramName);
                                io.mist.core.spec.TestParameter tp = stepParamMeta.get(paramName);
                                String paramType = "string";
                                String paramLoc = "body";
                                String description = "";
                                String example = "";
                                boolean required = false;
                                if (tp != null) {
                                    paramType = tp.getType() != null ? tp.getType() : "string";
                                    paramLoc = tp.getIn() != null ? tp.getIn() : "body";
                                    description = tp.getDescription() != null ? tp.getDescription() : "";
                                    example = tp.getExample() != null ? String.valueOf(tp.getExample()) : "";
                                    required = tp.getRequired() != null && tp.getRequired();
                                }
                                pw.println("        io.mist.core.enhancer.TestResultCapture.addParameter(");
                                pw.println("            \"" + escape(paramName) + "\", \"" + escape(paramValue) + "\", ");
                                pw.println("            \"" + escape(paramType) + "\", \"" + escape(paramLoc) + "\", ");
                                pw.println("            " + (description.isEmpty() ? "null" : "\"" + escape(description) + "\"") + ", ");
                                pw.println("            " + (example.isEmpty() ? "null" : "\"" + escape(example) + "\"") + ", ");
                                pw.println("            " + required + ", " + stepIdx + ", " + isDI + ");");
                            }
                            
                            // Capture path parameters for this step
                            if (step.getPathParams() != null) {
                                for (Map.Entry<String, String> pp : step.getPathParams().entrySet()) {
                                    boolean isDI = dataInjectedParams.contains(pp.getKey());
                                    pw.println("        io.mist.core.enhancer.TestResultCapture.addParameter(");
                                    pw.println("            \"" + escape(pp.getKey()) + "\", \"" + escape(pp.getValue()) + "\", ");
                                    pw.println("            \"string\", \"path\", null, null, true, " + stepIdx + ", " + isDI + ");");
                                }
                            }
                            
                            // Capture query parameters for this step
                            if (step.getQueryParams() != null) {
                                for (Map.Entry<String, String> qp : step.getQueryParams().entrySet()) {
                                    boolean isDI = dataInjectedParams.contains(qp.getKey());
                                    pw.println("        io.mist.core.enhancer.TestResultCapture.addParameter(");
                                    pw.println("            \"" + escape(qp.getKey()) + "\", \"" + escape(qp.getValue()) + "\", ");
                                    pw.println("            \"string\", \"query\", null, null, false, " + stepIdx + ", " + isDI + ");");
                                }
                            }
                            pw.println();
                        }
                        
                        // 🔥 CRITICAL FIX: ALWAYS create Allure step - NO conditional logic outside
                        // This ensures ALL steps appear in the Allure report regardless of dependencies
                        if (allureReport) {
                            pw.println("        // 🔥 ALWAYS create Allure step - execution decision happens INSIDE");
                            pw.println("        { // per-step scope block — prevents variable redeclaration across steps");
                            pw.println("        ");
                            // Inter-scenario delay. The historical default was 2000ms with the
                            // rationale 'prevents tests from executing so rapidly that they find
                            // the same traces'.  Traces are now correlated by traceId via the
                            // W3C traceparent marker, so the timing-collision concern is gone
                            // and the only remaining use of this delay is per-thread SUT rate-
                            // limit pacing.  Default lowered to 0 (no delay); operators on
                            // throttled APIs can set mst.test.inter.scenario.delay.ms to a
                            // positive integer in their .properties file or via -D.
                            pw.println("        long __interScenarioDelayMs;");
                            pw.println("        try {");
                            pw.println("            __interScenarioDelayMs = Long.parseLong(");
                            pw.println("                System.getProperty(\"mst.test.inter.scenario.delay.ms\", \"0\"));");
                            pw.println("        } catch (NumberFormatException nfe) {");
                            pw.println("            __interScenarioDelayMs = 0L;");
                            pw.println("        }");
                            pw.println("        if (__interScenarioDelayMs > 0) {");
                            pw.println("            try {");
                            pw.println("                Thread.sleep(__interScenarioDelayMs);");
                            pw.println("            } catch (InterruptedException ie) {");
                            pw.println("                Thread.currentThread().interrupt();");
                            pw.println("            }");
                            pw.println("        }");
                            pw.println("        ");
                            pw.println("        final long requestStartMicros = System.currentTimeMillis() * 1000L;");
                            pw.println("        try {");
                            pw.println("            Allure.step(\"" + escape(stepTitle) + "\", () -> {");
                            
                            // Add step metadata as parameters that will be prominently displayed
                            pw.println("                Allure.parameter(\"🏢 Service\", \"" + escape(step.getServiceName()) + "\");");
                            pw.println("                Allure.parameter(\"📡 HTTP Method\", \"" + verb.toUpperCase() + "\");");
                            pw.println("                Allure.parameter(\"🔗 Endpoint\", \"" + escape(step.getPath()) + "\");");
                            pw.println("                Allure.parameter(\"🎯 Expected Status\", " + step.getExpectedStatus() + ");");
                            
                            // Add dependency analysis information
                            String stepDepType = getDependencyTypeString(step);
                            pw.println("                Allure.parameter(\"🔗 Dependency Type\", \"" + stepDepType + "\");");

                            // Add hierarchical ID tag so it is searchable in Allure
                            if (step.getHierarchicalId() != null && !step.getHierarchicalId().isEmpty()) {
                                pw.println("                Allure.parameter(\"📌 Step ID\", \"" + escape(step.getHierarchicalId()) + "\");");
                            }

                            // For merged root steps, attach provenance lineage metadata
                            if (step.isMergedRootStep() && !step.getProvenanceBindings().isEmpty()) {
                                StringBuilder provDesc = new StringBuilder();
                                int producerIdx = step.getProducerRootIndex();
                                for (Map.Entry<String, String> prov : step.getProvenanceBindings().entrySet()) {
                                    String inherited = prov.getKey() + "=" + prov.getValue();
                                    provDesc.append("Inherited ").append(inherited)
                                            .append(" from Root ").append(producerIdx > 0 ? producerIdx : "?")
                                            .append("\\n");
                                }
                                String provString = escapeJavaString(provDesc.toString());
                                pw.println("                Allure.parameter(\"🔗 Data Lineage\", \"Merged Root (depends on Root " + (producerIdx > 0 ? producerIdx : "?") + ")\");");
                                pw.println("                Allure.addAttachment(\"📊 Cross-Trace Provenance\", \"text/plain\", \"" + provString + "\");");
                            }

                            // Add comprehensive description
                            String descDep = stepDepType;
                            if (step.isMergedRootStep()) {
                                descDep += " (Merged Root from separate trace)";
                            }
                            pw.println("                Allure.description(\"🎯 **Testing**: " + escape(step.getServiceName()) + "\\n\" +");
                            pw.println("                                 \"📡 **Method**: " + verb.toUpperCase() + "\\n\" +");
                            pw.println("                                 \"🔗 **Path**: " + escape(step.getPath()) + "\\n\" +");
                            pw.println("                                 \"🎯 **Expected**: " + step.getExpectedStatus() + "\\n\" +");
                            pw.println("                                 \"🔗 **Dependencies**: " + escape(descDep) + "\");");
                            pw.println("                ");
                            
                            // 🔥 EXECUTION DECISION INSIDE THE STEP - so it's always shown
                            pw.println("                // Execution decision analysis - determine if step should execute");
                            pw.println("                boolean shouldSkip = false;");
                            pw.println("                String skipReason = \"\";");
                            pw.println("                String skipCategory = \"\";");
                            pw.println("                ");
                            
                            // Check authentication dependency first
                            pw.println("                // Check authentication dependency");
                            pw.println("                if (!loginSucceeded.get()) {");
                            pw.println("                    shouldSkip = true;");
                            pw.println("                    skipReason = \"Authentication failed - cannot proceed with authenticated API calls\";");
                            pw.println("                    skipCategory = \"🔐 AUTH_FAILED\";");
                            pw.println("                }");
                            
                            // Check other dependencies — RESILIENT BYPASS MODE:
                            // Data and workflow dependencies no longer cause skips.
                            // If predecessors failed, fallback values are injected at
                            // parameter wiring time. Only AUTH_FAILED can skip a step.
                            
                            pw.println("                ");
                            pw.println("                // Add execution decision as parameter");
                            pw.println("                if (shouldSkip) {");
                            pw.println("                    Allure.parameter(\"🚦 Execution Decision\", \"⏭️ SKIP - \" + skipCategory);");
                            pw.println("                    Allure.parameter(\"⏭️ Skip Reason\", skipReason);");
                            pw.println("                } else {");
                            pw.println("                    Allure.parameter(\"🚦 Execution Decision\", \"▶️ EXECUTE - All dependencies satisfied\");");
                            pw.println("                }");
                            pw.println("                ");
                            
                            // NOW the actual execution or skip logic
                            pw.println("                if (!shouldSkip) {");
                            pw.println("                    System.out.println(\"▶️ EXECUTING: " + escape(stepTitle) + " (dependency analysis passed)\");");
                            
                            // 🔥 FIX: Declare Response variable OUTSIDE try block for catch block accessibility
                            pw.println("                    Response stepResponse" + stepIdx + " = null;");
                            // Mirror of the same fix for trace-correlation locals: the failure-path
                            // attachJaegerTrace() call lives in the catch block, so __mstTraceId<N>
                            // and __mstSpanId<N> must be visible there. Declaring them inside the
                            // try (where they were before) was a Java scope error.
                            pw.println("                    String __mstTraceId" + stepIdx + " = java.util.UUID.randomUUID().toString().replace(\"-\", \"\");");
                            pw.println("                    String __mstSpanId" + stepIdx + " = java.util.UUID.randomUUID().toString().replace(\"-\", \"\").substring(0, 16);");
                            pw.println("                    ");
                            
                            // Execute the step
                            pw.println("                    try {");
                            // Disable REST Assured's URL encoding: paths are already URL-encoded in MistGenerator
                            // for path-param values (e.g. " " -> "%20"). Without this, % gets re-encoded to %25 (double-encoded), so /admintravel/%20 -> /admintravel/%2520.
                            pw.println("                        RequestSpecification req = RestAssured.given().urlEncodingEnabled(false);");
                            
                            // 🔥 FIX: Always set Content-Type to application/json for requests with bodies
                            String requestBody = step.getBody() != null ? step.getBody() : "";
                            if (!requestBody.isEmpty()) {
                                pw.println("                        // 🔥 FIX: Set Content-Type to application/json for requests with bodies");
                                pw.println("                        req = req.contentType(\"application/json\");");
                                pw.println("                        String requestBody" + stepIdx + " = \"" + escape(requestBody) + "\";");
                                pw.println("                        req = req.body(requestBody" + stepIdx + ");");
                                pw.println("                        ");
                                pw.println("                        // Add request body as attachment (AllureRestAssured filter disabled to avoid duplication)");
                                pw.println("                        Allure.addAttachment(\"📤 Request Body\", \"application/json\", requestBody" + stepIdx + ");");
                            }
                            
                            // Auth header: delegates to MstAuthHandler.applyAuth which honours
                            // (1) per-test override (__mstOverrideToken / __mstDisableAuth from
                            // AuthManipulationStrategy), (2) auth.skip.path.patterns, and
                            // (3) the cached token from the configured auth.mode.
                            pw.println("                        req = io.mist.cli.auth.MstAuthHandler.applyAuth(req, \"" + escape(step.getPath()) + "\", __mstOverrideToken, __mstDisableAuth);");
                            // 401 hardening (MST only): when no per-test auth override is in effect,
                            // attach the refresh filter so an expired/revoked cached token gets
                            // transparently swapped and the request retried once. Exploration tests
                            // targeting 401 (INVALID_TOKEN / EXPIRED_TOKEN / REMOVE_AUTH) bypass
                            // this filter on purpose - they need to observe the 401.
                            pw.println("                        if (__mstOverrideToken == null && !__mstDisableAuth) {");
                            pw.println("                            req = req.filter(io.mist.cli.auth.MstAuthRefreshFilter.INSTANCE);");
                            pw.println("                        }");

                            // ── Header / cookie emission (A-5b) ─────────────────────────────────
                            // For negative variants targeted at a header- or cookie-located parameter
                            // we substitute the matching entry with the invalid value captured on
                            // the test case. Previously these locations were enrolled in the fault
                            // pool but discarded at emission time, so "negative" tests effectively
                            // sent only valid values for the parameter under test.
                            //
                            // Sniper invariant: at most ONE entry is replaced — the one matching
                            // the (location, paramName) tuple stored on the test case. All other
                            // entries pass through unchanged.
                            boolean isFaultStep = scenario.getFaulty()
                                    && step.isTopLevelRoot()
                                    && mstc.getTargetFaultParamLocation() != null;
                            String faultLoc = isFaultStep
                                    ? mstc.getTargetFaultParamLocation().toLowerCase(java.util.Locale.ROOT) : null;
                            String faultName = null;
                            String faultValueLiteral = null;
                            if (isFaultStep) {
                                for (String fp : mstc.getFaultyParameters()) {
                                    int eq = fp.indexOf('=');
                                    if (eq > 0) {
                                        faultName = fp.substring(0, eq);
                                        faultValueLiteral = fp.substring(eq + 1);
                                        break; // exactly one targeted param per sniper variant
                                    }
                                }
                            }

                            if (step.getHeaders() != null && !step.getHeaders().isEmpty()) {
                                for (Map.Entry<String, String> h : step.getHeaders().entrySet()) {
                                    String hName  = h.getKey();
                                    String hValue = h.getValue();
                                    boolean replaceWithInvalid = isFaultStep
                                            && "header".equals(faultLoc)
                                            && hName.equals(faultName);
                                    String emitted = replaceWithInvalid ? faultValueLiteral : hValue;
                                    pw.println("                        req = req.header(\""
                                            + escape(hName) + "\", \""
                                            + escape(emitted == null ? "" : emitted) + "\");");
                                }
                            }
                            // Even when no positive header was carried on this step, a header-located
                            // fault target must still be emitted so the invalid value reaches the API.
                            if (isFaultStep && "header".equals(faultLoc) && faultName != null
                                    && (step.getHeaders() == null || !step.getHeaders().containsKey(faultName))) {
                                pw.println("                        req = req.header(\""
                                        + escape(faultName) + "\", \""
                                        + escape(faultValueLiteral == null ? "" : faultValueLiteral) + "\");");
                            }

                            if (step.getCookies() != null && !step.getCookies().isEmpty()) {
                                for (Map.Entry<String, String> c : step.getCookies().entrySet()) {
                                    String cName  = c.getKey();
                                    String cValue = c.getValue();
                                    boolean replaceWithInvalid = isFaultStep
                                            && "cookie".equals(faultLoc)
                                            && cName.equals(faultName);
                                    String emitted = replaceWithInvalid ? faultValueLiteral : cValue;
                                    pw.println("                        req = req.cookie(\""
                                            + escape(cName) + "\", \""
                                            + escape(emitted == null ? "" : emitted) + "\");");
                                }
                            }
                            if (isFaultStep && "cookie".equals(faultLoc) && faultName != null
                                    && (step.getCookies() == null || !step.getCookies().containsKey(faultName))) {
                                pw.println("                        req = req.cookie(\""
                                        + escape(faultName) + "\", \""
                                        + escape(faultValueLiteral == null ? "" : faultValueLiteral) + "\");");
                            }

                            // ── Query parameter emission ────────────────────────────────────────
                            // Mirrors the header/cookie pattern above. Previously query params were
                            // collected by the generator and captured for telemetry but never
                            // emitted into the request, so every query-declared operation was
                            // exercised without its parameters (and a query-located fault target
                            // never reached the SUT). Values are URL-encoded HERE because the
                            // request spec runs with urlEncodingEnabled(false) (path params arrive
                            // pre-encoded from the generator); encoding the fault value too is
                            // transport-correct — the SUT decodes back the exact invalid value.
                            // Params wired via paramDependencies are skipped: the dependency loop
                            // below emits them with the runtime-resolved producer value.
                            if (step.getQueryParams() != null && !step.getQueryParams().isEmpty()) {
                                for (Map.Entry<String, String> qp : step.getQueryParams().entrySet()) {
                                    String qName  = qp.getKey();
                                    if (step.getParamDependencies().containsKey(qName)) {
                                        continue;
                                    }
                                    String qValue = qp.getValue();
                                    boolean replaceWithInvalid = isFaultStep
                                            && "query".equals(faultLoc)
                                            && qName.equals(faultName);
                                    String emitted = replaceWithInvalid ? faultValueLiteral : qValue;
                                    pw.println("                        req = req.queryParam(\""
                                            + escape(urlEncode(qName)) + "\", \""
                                            + escape(urlEncode(emitted == null ? "" : emitted)) + "\");");
                                }
                            }
                            if (isFaultStep && "query".equals(faultLoc) && faultName != null
                                    && (step.getQueryParams() == null || !step.getQueryParams().containsKey(faultName))) {
                                pw.println("                        req = req.queryParam(\""
                                        + escape(urlEncode(faultName)) + "\", \""
                                        + escape(urlEncode(faultValueLiteral == null ? "" : faultValueLiteral)) + "\");");
                            }

                            // Add dependency resolution for parameters (with resilient bypass)
                            // Uses jsonPath extraction from the producer's captured response body
                            for (Map.Entry<String, MultiServiceTestCase.Dependency> dep : step.getParamDependencies().entrySet()) {
                                String paramName = dep.getKey();
                                String varName = sanitize(paramName);
                                int sourceStepIdx = dep.getValue().sourceStepIndex;
                                String sourceJsonPath = dep.getValue().sourceOutputKey;
                                String fallback = dep.getValue().fallbackValue;
                                if (fallback == null || fallback.isEmpty()) {
                                    fallback = java.util.UUID.randomUUID().toString();
                                }
                                String escapedFallback = escape(fallback);
                                String escapedJsonPath = escape(sourceJsonPath != null ? sourceJsonPath : "data.id");
                                
                                pw.println("                        String " + varName + "Value = null;");
                                pw.println("                        if (stepResults.getOrDefault(" + sourceStepIdx + ", false)) {");
                                pw.println("                            String producerBody = capturedOutputs.get(" + sourceStepIdx + ");");
                                pw.println("                            if (producerBody != null) {");
                                pw.println("                                try {");
                                pw.println("                                    io.restassured.path.json.JsonPath jp = new io.restassured.path.json.JsonPath(producerBody);");
                                pw.println("                                    Object extracted = jp.get(\"" + escapedJsonPath + "\");");
                                pw.println("                                    if (extracted != null) {");
                                pw.println("                                        " + varName + "Value = extracted.toString();");
                                pw.println("                                        System.out.println(\"[Dependency] Extracted '\" + " + varName + "Value + \"' from step " + sourceStepIdx + " via jsonPath '" + escapedJsonPath + "'\");");
                                pw.println("                                    }");
                                pw.println("                                } catch (Exception jpEx) {");
                                pw.println("                                    System.err.println(\"[Dynamic Path Finder] jsonPath '\" + \"" + escapedJsonPath + "\" + \"' extraction failed: \" + jpEx.getMessage());");
                                pw.println("                                }");
                                pw.println("                            }");
                                pw.println("                            if (" + varName + "Value == null) {");
                                pw.println("                                System.out.println(\"[Dynamic Path Finder] Failed to locate value in payload. Falling back to data.id\");");
                                pw.println("                                " + varName + "Value = \"" + escapedFallback + "\";");
                                pw.println("                            }");
                                pw.println("                        } else {");
                                pw.println("                            System.out.println(\"⚡ BYPASS: Step " + sourceStepIdx + " failed — using fallback for '" + escape(paramName) + "'\");");
                                pw.println("                            " + varName + "Value = \"" + escapedFallback + "\";");
                                pw.println("                            Allure.parameter(\"⚡ Bypass Mode\", \"YES — fallback for " + escape(paramName) + "\");");
                                pw.println("                            io.mist.core.enhancer.TestResultCapture.recordBypassTriggered(" + stepIdx + ");");
                                pw.println("                        }");
                                pw.println("                        if (" + varName + "Value != null) {");
                                pw.println("                            allStepParameters.put(\"" + escape(paramName) + "\", " + varName + "Value);");
                                pw.println("                            stepParams" + stepIdx + ".put(\"" + escape(paramName) + "\", " + varName + "Value);");
                                if (step.getMethod().getMethod().equalsIgnoreCase("GET")) {
                                    pw.println("                            req = req.queryParam(\"" + escape(paramName) + "\", " + varName + "Value);");
                                } else {
                                    pw.println("                            // Add to body if needed");
                                }
                                pw.println("                        }");
                            }
                            
                            // Add body parameters if any
                            if (!requestBody.isEmpty()) {
                                pw.println("                        allStepParameters.put(\"body\", \"" + escape(requestBody) + "\");");
                                // Two-phase VERIFIED_VALID fix: enrol body fields INDIVIDUALLY into stepParams
                                // (paramName = field name, value = that field's value) so the per-step
                                // recordParameterSuccess key matches the field-level pool key that
                                // MistGenerator.preferVerifiedValues queries (e.g. "startStation"). Emitting the
                                // whole serialized body under "body" never matched any field-level pool key, so
                                // the verified pool was a no-op for body endpoints. The human-readable body blob
                                // is still kept in allStepParameters above for the Allure trace attachment.
                                if (step.getBodyFields() != null) {
                                    for (java.util.Map.Entry<String, String> __bf : step.getBodyFields().entrySet()) {
                                        pw.println("                        stepParams" + stepIdx + ".put(\""
                                                + escape(__bf.getKey()) + "\", \"" + escape(__bf.getValue()) + "\");");
                                    }
                                }
                            }
                            
                            // ── W3C traceparent header (parallel-safe trace correlation) ──
                            // __mstTraceId<N> and __mstSpanId<N> are declared OUTSIDE the surrounding
                            // try block so both success-path and failure-path call sites can pass
                            // them to attachJaegerTrace(). SUT honors W3C Trace Context (verified by
                            // curl: train-ticket OpenTelemetry attaches downstream spans under this
                            // traceId). attachJaegerTrace later queries /traces/<id> for an exact 1:1
                            // match — no race even with N parallel test threads.
                            pw.println("                        req = req.header(\"traceparent\", \"00-\" + __mstTraceId" + stepIdx + " + \"-\" + __mstSpanId" + stepIdx + " + \"-01\");");
                            pw.println("                        // 🔥 FIX: Extract response FIRST (before status code assertion) to capture response body in all cases");
                            pw.println("                        stepResponse" + stepIdx + " = req.when()." + verb + "(\"" + escape(step.getPath()) + "\")");
                            pw.println("                               .then().log().ifValidationFails()");
                            pw.println("                               .extract().response();");
                            pw.println("                        ");
                            pw.println("                        // Now validate status code based on expected status from configuration");
                            pw.println("                        int actualStatusCode" + stepIdx + " = stepResponse" + stepIdx + ".getStatusCode();");
                            pw.println("                        int expectedStatusCode" + stepIdx + " = " + step.getExpectedStatus() + ";");
                            pw.println("                        ");

                            // Fix 3 Layer 3: output-coverage backstop. Compute the response
                            // fingerprint (SHA-256 over status + body) and short-circuit
                            // expensive per-step oracles (LLM validation) when the same
                            // fingerprint has already been observed in this class. The
                            // HTTP call itself has already happened; we only avoid double-
                            // paying the oracle cost on identical observations.
                            pw.println("                        // Fix 3 Layer 3: output-coverage backstop");
                            pw.println("                        String responseFingerprint" + stepIdx + " = responseFingerprint(actualStatusCode" + stepIdx + ", stepResponse" + stepIdx + ".getBody().asString());");
                            pw.println("                        boolean isDuplicateResponse" + stepIdx + " = !SEEN_RESPONSE_HASHES.add(responseFingerprint" + stepIdx + ");");
                            pw.println("                        if (isDuplicateResponse" + stepIdx + ") {");
                            pw.println("                            System.out.println(\"⏭️ Duplicate response observed (hash=\" + responseFingerprint" + stepIdx + ".substring(0, 12) + \"…), skipping LLM validation for step " + stepIdx + "\");");
                            pw.println("                        }");
                            pw.println("                        ");

                            // 🤖 LLM RESPONSE VALIDATION: uses class-level singleton llmValidator
                            pw.println("                        // 🤖 LLM RESPONSE VALIDATION: uses class-level singletons");
                            pw.println("                        ");

                            if (isNegativeTest) {
                                // NEGATIVE TEST: Check LLM validation FIRST, then status code
                                pw.println("                        // 🔴 NEGATIVE TEST VALIDATION");
                                pw.println("                        // For negative tests with invalid inputs, pass iff:");
                                pw.println("                        //   (a) the response status is non-2xx (API clearly rejected the invalid input), OR");
                                pw.println("                        //   (b) the response is 2xx AND the LLM detects a soft error related to our invalid input.");
                                pw.println("                        // This predicate is response-class based (not \"actual != expected\"), so it works");
                                pw.println("                        // correctly when expectedStatus itself is non-2xx and naturally respects the");
                                pw.println("                        // llm.response.validation.only.2xx contract (LLM only fires on actual 2xx).");
                                pw.println("                        boolean responseIsError = (actualStatusCode" + stepIdx + " < 200 || actualStatusCode" + stepIdx + " >= 300);");
                                pw.println("                        boolean statusCodeIndicatesError = responseIsError;  // alias kept for downstream log strings");
                                pw.println("                        boolean llmDetectedRelatedError = false;");
                                pw.println("                        String llmRca = \"LLM validation not performed\";");
                                pw.println("                        ");
                                
                                // Build the map of invalid parameters from the test case
                                pw.println("                        // Build map of designed invalid parameters for this negative test");
                                pw.println("                        java.util.Map<String, String> invalidParams = new java.util.HashMap<>();");
                                
                                // Get faulty parameters from the test case and add them to the map
                                List<String> faultyParams = mstc.getFaultyParameters();
                                for (String faultyParam : faultyParams) {
                                    // faultyParam is in format "paramName=value"
                                    int eqIdx = faultyParam.indexOf('=');
                                    if (eqIdx > 0) {
                                        String paramName = faultyParam.substring(0, eqIdx);
                                        String paramValue = faultyParam.substring(eqIdx + 1);
                                        pw.println("                        invalidParams.put(\"" + escapeJavaString(paramName) + "\", \"" + escapeJavaString(paramValue) + "\");");
                                    }
                                }
                                pw.println("                        ");
                                
                                pw.println("                        // 🚀 ZERO-OVERHEAD FAST PATH: when the response is non-2xx the API has already");
                                pw.println("                        // rejected the invalid input — the negative test passes without an LLM call.");
                                pw.println("                        // This naturally enforces the llm.response.validation.only.2xx contract for");
                                pw.println("                        // negative tests: LLM only runs when the response is in [200,300).");
                                pw.println("                        if (responseIsError) {");
                                pw.println("                            llmRca = \"Skipped: response status \" + actualStatusCode" + stepIdx + " + \" is non-2xx; API rejected invalid input\";");
                                pw.println("                            System.out.println(\"⚡ Skipping LLM validation: response is non-2xx (\" + actualStatusCode" + stepIdx + " + \")\");");
                                pw.println("                        } else if (isDuplicateResponse" + stepIdx + ") {");
                                pw.println("                            llmRca = \"Skipped: response fingerprint already validated in this class\";");
                                pw.println("                        } else if (LLM_VALIDATION_ENABLED && llmValidator != null) {");
                                pw.println("                            try {");
                                pw.println("                                String validationBody = stepResponse" + stepIdx + ".getBody().asString();");
                                pw.println("                                ");
                                pw.println("                                // Use class-level singleton (created once in @BeforeClass)");
                                pw.println("                                io.mist.core.generation.ZeroShotLLMGenerator.ValidationResult validationResult =");
                                pw.println("                                    llmValidator.validateNegativeTestResponse(");
                                pw.println("                                        actualStatusCode" + stepIdx + ",");
                                pw.println("                                        validationBody,");
                                pw.println("                                        \"" + escape(step.getServiceName()) + "\",");
                                pw.println("                                        \"" + escape(verb.toUpperCase()) + "\",");
                                pw.println("                                        \"" + escape(step.getPath()) + "\",");
                                pw.println("                                        invalidParams");
                                pw.println("                                    );");
                                pw.println("                                ");
                                pw.println("                                // isFailed() returns true only if error was detected AND related to our invalid input");
                                pw.println("                                llmDetectedRelatedError = validationResult.isFailed();");
                                pw.println("                                llmRca = validationResult.getRca();");
                                pw.println("                                System.out.println(\"🤖 LLM Validation (Negative Test): Error Related to Invalid Input=\" + llmDetectedRelatedError + \", RCA: \" + llmRca);");
                                pw.println("                                ");
                                pw.println("                                // Attach LLM analysis to Allure report");
                                pw.println("                                if (LLM_INCLUDE_RCA) {");
                                pw.println("                                    StringBuilder llmReport = new StringBuilder();");
                                pw.println("                                    llmReport.append(\"════════════════════════════════════════════════════════════════════════\\n\");");
                                pw.println("                                    llmReport.append(\"🤖 INTELLIGENT ANALYSIS (Negative Test)\\n\");");
                                pw.println("                                    llmReport.append(\"════════════════════════════════════════════════════════════════════════\\n\\n\");");
                                pw.println("                                    llmReport.append(\"Test Type: 🔴 NEGATIVE (Invalid Input Testing)\\n\\n\");");
                                pw.println("                                    ");
                                pw.println("                                    // Show designed invalid inputs");
                                pw.println("                                    llmReport.append(\"📋 DESIGNED INVALID INPUTS:\\n\");");
                                pw.println("                                    for (java.util.Map.Entry<String, String> entry : invalidParams.entrySet()) {");
                                pw.println("                                        llmReport.append(\"   • \").append(entry.getKey()).append(\" = \").append(entry.getValue()).append(\"\\n\");");
                                pw.println("                                    }");
                                pw.println("                                    llmReport.append(\"\\n\");");
                                pw.println("                                    ");
                                pw.println("                                    llmReport.append(\"Status Code: \").append(actualStatusCode" + stepIdx + ").append(\" (expected: \").append(expectedStatusCode" + stepIdx + ").append(\")\\n\");");
                                pw.println("                                    llmReport.append(\"Status Code Indicates Error: \").append(statusCodeIndicatesError ? \"✅ YES\" : \"❌ NO\").append(\"\\n\");");
                                pw.println("                                    llmReport.append(\"Error Related to Invalid Input: \").append(llmDetectedRelatedError ? \"✅ YES\" : \"❌ NO\").append(\"\\n\\n\");");
                                pw.println("                                    llmReport.append(\"Root Cause Analysis:\\n\");");
                                pw.println("                                    llmReport.append(llmRca).append(\"\\n\");");
                                pw.println("                                    Allure.addAttachment(\"🤖 INTELLIGENT ANALYSIS (Negative Test)\", \"text/plain\", llmReport.toString());");
                                pw.println("                                }");
                                pw.println("                                ");
                                pw.println("                            } catch (Exception llmEx) {");
                                pw.println("                                System.err.println(\"⚠️ LLM validation failed: \" + llmEx.getMessage());");
                                pw.println("                                // Continue with status code check only");
                                pw.println("                            }");
                                pw.println("                        }");
                                pw.println("                        ");
                                pw.println("                        // Negative test PASSES if either:");
                                pw.println("                        // 1. Status code is different from expected (clear error), OR");
                                pw.println("                        // 2. LLM detected a soft error that is RELATED to our designed invalid input");
                                pw.println("                        // NOTE: If error is NOT related to our invalid input, test still FAILS!");
                                pw.println("                        boolean negativeTestPassed = statusCodeIndicatesError || llmDetectedRelatedError;");
                                pw.println("                        ");
                                pw.println("                        if (!negativeTestPassed) {");
                                pw.println("                            throw new AssertionError(\"Negative test failed: Either no error detected, or error was not related to our invalid input. RCA: \" + llmRca);");
                                pw.println("                        }");
                                pw.println("                        ");
                                pw.println("                        if (statusCodeIndicatesError) {");
                                pw.println("                            System.out.println(\"✅ Negative test PASSED: Status code \" + actualStatusCode" + stepIdx + " + \" indicates error (expected: \" + expectedStatusCode" + stepIdx + " + \")\");");
                                pw.println("                        } else {");
                                pw.println("                            System.out.println(\"✅ Negative test PASSED: LLM confirmed error is related to our designed invalid input\");");
                                pw.println("                        }");
                                pw.println("                        ");
                            } else {
                                // POSITIVE TEST: Status code check first, then LLM validation
                                pw.println("                        // ✅ POSITIVE TEST VALIDATION");
                                pw.println("                        // Positive test: PASS only if status == expected AND response body indicates success");
                                pw.println("                        if (actualStatusCode" + stepIdx + " != expectedStatusCode" + stepIdx + ") {");
                                pw.println("                            throw new AssertionError(\"Expected status code \" + expectedStatusCode" + stepIdx + " + \", but got: \" + actualStatusCode" + stepIdx + ");");
                                pw.println("                        }");
                                pw.println("                        ");
                                pw.println("                        // LLM validation for positive tests - detect soft errors in 2XX responses");
                                pw.println("                        if (LLM_VALIDATION_ENABLED && llmValidator != null && !isDuplicateResponse" + stepIdx + " && (!LLM_ONLY_2XX || (actualStatusCode" + stepIdx + " >= 200 && actualStatusCode" + stepIdx + " < 300))) {");
                                pw.println("                            try {");
                                pw.println("                                String validationBody = stepResponse" + stepIdx + ".getBody().asString();");
                                pw.println("                                ");
                                pw.println("                                // Use class-level singleton (created once in @BeforeClass)");
                                pw.println("                                io.mist.core.generation.ZeroShotLLMGenerator.ValidationResult validationResult =");
                                pw.println("                                    llmValidator.validateResponse(");
                                pw.println("                                        actualStatusCode" + stepIdx + ",");
                                pw.println("                                        validationBody,");
                                pw.println("                                        \"" + escape(step.getServiceName()) + "\",");
                                pw.println("                                        \"" + escape(verb.toUpperCase()) + "\",");
                                pw.println("                                        \"" + escape(step.getPath()) + "\"");
                                pw.println("                                    );");
                                pw.println("                                ");
                                pw.println("                                System.out.println(\"🤖 LLM Validation (Positive Test): Failed=\" + validationResult.isFailed() + \", RCA: \" + validationResult.getRca());");
                                pw.println("                                ");
                                pw.println("                                // Attach LLM analysis to Allure report");
                                pw.println("                                if (LLM_INCLUDE_RCA) {");
                                pw.println("                                    StringBuilder llmReport = new StringBuilder();");
                                pw.println("                                    llmReport.append(\"════════════════════════════════════════════════════════════════════════\\n\");");
                                pw.println("                                    llmReport.append(\"🤖 INTELLIGENT ANALYSIS (Positive Test)\\n\");");
                                pw.println("                                    llmReport.append(\"════════════════════════════════════════════════════════════════════════\\n\\n\");");
                                pw.println("                                    llmReport.append(\"Test Type: ✅ POSITIVE\\n\");");
                                pw.println("                                    llmReport.append(\"Analysis Result: \").append(validationResult.isFailed() ? \"❌ SOFT ERROR DETECTED\" : \"✅ VALID SUCCESS\").append(\"\\n\\n\");");
                                pw.println("                                    llmReport.append(\"Root Cause Analysis:\\n\");");
                                pw.println("                                    llmReport.append(validationResult.getRca()).append(\"\\n\");");
                                pw.println("                                    Allure.addAttachment(\"🤖 INTELLIGENT ANALYSIS (Positive Test)\", \"text/plain\", llmReport.toString());");
                                pw.println("                                }");
                                pw.println("                                ");
                                pw.println("                                // Positive test logic:");
                                pw.println("                                // - If LLM says FAILED: FAIL (we expected success but got soft error)");
                                pw.println("                                // - If LLM says SUCCESS: PASS (as expected)");
                                pw.println("                                if (validationResult.isFailed()) {");
                                pw.println("                                    throw new AssertionError(\"Positive test failed: Expected success but LLM detected soft error. RCA: \" + validationResult.getRca());");
                                pw.println("                                }");
                                pw.println("                                System.out.println(\"✅ Positive test PASSED: LLM validated response as successful\");");
                                pw.println("                                ");
                                pw.println("                            } catch (AssertionError ae) {");
                                pw.println("                                throw ae; // Re-throw assertion errors");
                                pw.println("                            } catch (Exception llmEx) {");
                                pw.println("                                System.err.println(\"⚠️ LLM validation failed: \" + llmEx.getMessage());");
                                pw.println("                                // Don't fail the test due to LLM validation errors");
                                pw.println("                            }");
                                pw.println("                        }");
                            }
                            pw.println("                        ");
                            
                            // 🔍 FAULT DETECTION: Inject fault detection code for root API (step 1 = first business API)
                            if (stepIdx == 1) {
                                pw.println("                        // 🔍 FAULT DETECTION: Check if root API response contains injected fault");
                                pw.println("                        try {");
                                pw.println("                            String faultCheckBody = stepResponse" + stepIdx + ".getBody().asString();");
                                pw.println("                            org.json.JSONObject faultJson = new org.json.JSONObject(faultCheckBody);");
                                pw.println("                            if (faultJson.has(\"data\")) {");
                                pw.println("                                Object dataValue = faultJson.get(\"data\");");
                                pw.println("                                if (dataValue instanceof org.json.JSONObject) {");
                                pw.println("                                    org.json.JSONObject dataObj = (org.json.JSONObject) dataValue;");
                                pw.println("                                    if (dataObj.optBoolean(\"injected\", false)) {");
                                pw.println("                                        String detectedFaultName = dataObj.optString(\"faultName\", \"\");");
                                pw.println("                                        if (!detectedFaultName.isEmpty()) {");
                                pw.println("                                            io.mist.core.analysis.FaultDetectionTracker.getInstance().recordDetectedFault(");
                                pw.println("                                                detectedFaultName,");
                                pw.println("                                                this.getClass().getName(),");
                                pw.println("                                                \"" + escape(testMethodName) + "\",");
                                pw.println("                                                System.currentTimeMillis(),");
                                pw.println("                                                faultCheckBody");
                                pw.println("                                            );");
                                pw.println("                                            System.out.println(\"🔍 FAULT DETECTED: \" + detectedFaultName + \" in test: " + escape(testMethodName) + "\");");
                                pw.println("                                        }");
                                pw.println("                                    }");
                                pw.println("                                }");
                                pw.println("                            }");
                                pw.println("                        } catch (Exception faultEx) {");
                                pw.println("                            // Silent fail - don't break test execution");
                                pw.println("                        }");
                                pw.println("                        ");
                            }
                            
                            pw.println("                        stepResults.put(" + stepIdx + ", true);");
                            pw.println("                        System.out.println(\"✅ " + escape(stepTitle) + " - SUCCESS\");");
                            
                            // Capture response body for downstream dependency resolution via jsonPath
                            pw.println("                        try {");
                            pw.println("                            String fullBody" + stepIdx + " = stepResponse" + stepIdx + ".getBody().asString();");
                            pw.println("                            if (fullBody" + stepIdx + " != null && !fullBody" + stepIdx + ".isEmpty()) {");
                            pw.println("                                capturedOutputs.put(" + stepIdx + ", fullBody" + stepIdx + ");");
                            pw.println("                            }");
                            pw.println("                        } catch (Exception captureEx) {");
                            pw.println("                            System.err.println(\"[Capture] Failed to store response body for step " + stepIdx + ": \" + captureEx.getMessage());");
                            pw.println("                        }");
                            pw.println("                        ");
                            
                            // 🔥 FIX: Clean up SUCCESS reporting - single set of parameters, no duplication
                            pw.println("                        // ✅ SUCCESS: Clean success reporting without duplication");
                            pw.println("                        try {");
                            pw.println("                            String responseBody = stepResponse" + stepIdx + ".getBody().asString();");
                            pw.println("                            int actualStatus = stepResponse" + stepIdx + ".getStatusCode();");
                            pw.println("                            long responseTime = stepResponse" + stepIdx + ".getTime();");
                            pw.println("                            ");
                            pw.println("                            // 🔧 Test Case Enhancer: Capture response for enhancement (step-aware)");
                            pw.println("                            io.mist.core.enhancer.TestResultCapture.captureStepResponse(" + stepIdx + ", actualStatus, responseBody);");
                            pw.println("                            ");
                            pw.println("                            // Phase 1: record each parameter's value as VERIFIED_VALID (SUT");
                            pw.println("                            // accepted it on this 2xx step). recordParameterSuccess gates");
                            pw.println("                            // on captureEnabled + isNegativeTest internally so we emit");
                            pw.println("                            // unconditionally here. Endpoint is the OpenAPI TEMPLATE path");
                            pw.println("                            // (with {paramName} markers) so it matches what MistGenerator's");
                            pw.println("                            // preferVerifiedValues reads via the registry. Using the");
                            pw.println("                            // resolved path would key the registry on concrete IDs that");
                            pw.println("                            // generation-time reads can never match.");
                            pw.println("                            // Iterate the step-scoped map (declared at the top of this");
                            pw.println("                            // step's emission), NOT allStepParameters which is cumulative");
                            pw.println("                            // across all steps in this test method.");
                            pw.println("                            String __ep" + stepIdx + " = \"" + verb.toUpperCase() + " " + escape(step.getMethod().getTestPath()) + "\";");
                            pw.println("                            for (java.util.Map.Entry<String, String> __pe : stepParams" + stepIdx + ".entrySet()) {");
                            pw.println("                                io.mist.core.enhancer.TestResultCapture.recordParameterSuccess(");
                            pw.println("                                    __ep" + stepIdx + ", __pe.getKey(), __pe.getValue(), actualStatus);");
                            pw.println("                            }");
                            pw.println("                            ");
                            pw.println("                            // Single success status parameter");
                            pw.println("                            Allure.parameter(\"🎯 Result\", \"✅ SUCCESS (\" + actualStatus + \" in \" + responseTime + \"ms)\");");
                            pw.println("                            ");
                            pw.println("                            // Add response as attachment (AllureRestAssured filter disabled to avoid duplication)");
                            pw.println("                            Allure.addAttachment(\"📥 Response (\" + actualStatus + \")\", \"application/json\", responseBody);");
                            // Wait for Jaeger to ingest the trace before attachJaegerTrace() tries to
                            // fetch it. Mirrors the failure-path delay below — both call sites
                            // pull from the same {@code mst.test.jaeger.propagation.delay.ms} system
                            // property (default 0ms). The marker-first lookup INSIDE attachJaegerTrace
                            // polls 5×200ms for ingest, which already handles propagation delay; this
                            // pre-sleep is a legacy safety wait for SUTs that don't honor W3C
                            // traceparent. Operators on such SUTs can bump this back up.
                            pw.println("                            long __jaegerPropagationDelayMs;");
                            pw.println("                            try {");
                            pw.println("                                __jaegerPropagationDelayMs = Long.parseLong(");
                            pw.println("                                    System.getProperty(\"mst.test.jaeger.propagation.delay.ms\", \"0\"));");
                            pw.println("                            } catch (NumberFormatException nfe) { __jaegerPropagationDelayMs = 0L; }");
                            pw.println("                            if (__jaegerPropagationDelayMs > 0) {");
                            pw.println("                                try { Thread.sleep(__jaegerPropagationDelayMs); }");
                            pw.println("                                catch (InterruptedException ie) { Thread.currentThread().interrupt(); }");
                            pw.println("                            }");
                            pw.println("                            CLIENT_RESPONSE_BODY.set(responseBody);");
                            pw.println("                            CLIENT_RESPONSE_STATUS.set(actualStatus);");
                            pw.println("                            attachJaegerTrace(\"" + escape(step.getServiceName()) + "\", \"" + verb.toUpperCase() + "\", \"" + escape(step.getPath()) + "\", requestStartMicros, allStepParameters, false, __mstTraceId" + stepIdx + ", \"" + escape(testMethodName) + "\", __targetService, __targetParam);");
                            pw.println("                        } catch (Exception e) {");
                            pw.println("                            Allure.parameter(\"🎯 Result\", \"✅ SUCCESS (response capture failed)\");");
                            pw.println("                        }");
                            // Phase 2.F: Positive variants whose verdict carries any ERROR-severity
                            // violation must be marked FAIL — the success path has already attached
                            // the verdict and Allure step in attachJaegerTrace. We throw here so the
                            // surrounding catch block records the failure with a clear message.
                            if (!isNegativeTest) {
                                pw.println("                        // Phase 2.F: Trace Shape Oracle — positive variant FAILS when verdict");
                                pw.println("                        // reports any ERROR-severity violation (verdict was attached above).");
                                pw.println("                        {");
                                pw.println("                            TraceShapeVerdict tsoPosVerdict = LAST_VERDICT.get();");
                                pw.println("                            if (tsoPosVerdict != null && !tsoPosVerdict.isPassed()) {");
                                pw.println("                                StringBuilder vmsg = new StringBuilder(\"Positive variant failed — Trace Shape Oracle verdict has violation(s):\");");
                                pw.println("                                for (TraceShapeVerdict.InvariantOutcome o : tsoPosVerdict.getOutcomes()) {");
                                pw.println("                                    if (!o.passed && o.severity == TraceShapeVerdict.Severity.ERROR) {");
                                pw.println("                                        vmsg.append(\" [\").append(o.kind).append(\": \").append(o.detail).append(\"]\");");
                                pw.println("                                    }");
                                pw.println("                                }");
                                pw.println("                                throw new AssertionError(vmsg.toString());");
                                pw.println("                            }");
                                pw.println("                        }");
                            }
                            
                                                    // 🔥 FIX: Proper FAILURE reporting with correct Allure status
                        pw.println("                    } catch (Throwable t) {");
                        pw.println("                        stepResults.put(" + stepIdx + ", false);");
                        pw.println("                        System.out.println(\"❌ " + escape(stepTitle) + " - FAILED: \" + t.getMessage());");
                        pw.println("                        ");
                        pw.println("                        // 🔥 CRITICAL: Capture response body for failed requests (response was extracted before assertion)");
                        pw.println("                        String failedResponseBody = null;");
                        pw.println("                        int failedStatusCode = -1;");
                        pw.println("                        long failedResponseTime = -1;");
                        pw.println("                        try {");
                        pw.println("                            if (stepResponse" + stepIdx + " != null) {");
                        pw.println("                                failedResponseBody = stepResponse" + stepIdx + ".getBody().asString();");
                        pw.println("                                failedStatusCode = stepResponse" + stepIdx + ".getStatusCode();");
                        pw.println("                                failedResponseTime = stepResponse" + stepIdx + ".getTime();");
                        pw.println("                                // 🔧 Test Case Enhancer: Capture response for enhancement (step-aware)");
                        pw.println("                                io.mist.core.enhancer.TestResultCapture.captureStepResponse(" + stepIdx + ", failedStatusCode, failedResponseBody);");
                        pw.println("                            }");
                        pw.println("                        } catch (Exception respEx) {");
                        pw.println("                            failedResponseBody = \"Unable to capture response: \" + respEx.getMessage();");
                        pw.println("                        }");
                        pw.println("                        ");
                        
                        // 🔍 FAULT DETECTION IN FAILURE PATH: Also check for injected faults when test fails
                        // This is critical because injected faults often cause status code mismatches
                        if (stepIdx == 1) {
                            pw.println("                        // 🔍 FAULT DETECTION (FAILURE PATH): Check if failed response contains injected fault");
                            pw.println("                        try {");
                            pw.println("                            if (failedResponseBody != null && !failedResponseBody.startsWith(\"Unable to capture\")) {");
                            pw.println("                                org.json.JSONObject faultJson = new org.json.JSONObject(failedResponseBody);");
                            pw.println("                                if (faultJson.has(\"data\")) {");
                            pw.println("                                    Object dataValue = faultJson.get(\"data\");");
                            pw.println("                                    if (dataValue instanceof org.json.JSONObject) {");
                            pw.println("                                        org.json.JSONObject dataObj = (org.json.JSONObject) dataValue;");
                            pw.println("                                        if (dataObj.optBoolean(\"injected\", false)) {");
                            pw.println("                                            String detectedFaultName = dataObj.optString(\"faultName\", \"\");");
                            pw.println("                                            if (!detectedFaultName.isEmpty()) {");
                            pw.println("                                                io.mist.core.analysis.FaultDetectionTracker.getInstance().recordDetectedFault(");
                            pw.println("                                                    detectedFaultName,");
                            pw.println("                                                    this.getClass().getName(),");
                            pw.println("                                                    \"" + escape(testMethodName) + "\",");
                            pw.println("                                                    System.currentTimeMillis(),");
                            pw.println("                                                    failedResponseBody");
                            pw.println("                                                );");
                            pw.println("                                                System.out.println(\"🔍 FAULT DETECTED (in failed test): \" + detectedFaultName + \" in test: " + escape(testMethodName) + "\");");
                            pw.println("                                            }");
                            pw.println("                                        }");
                            pw.println("                                    }");
                            pw.println("                                }");
                            pw.println("                            }");
                            pw.println("                        } catch (Exception faultEx) {");
                            pw.println("                            // Silent fail - don't break error handling");
                            pw.println("                        }");
                            pw.println("                        ");
                        }
                        
                        pw.println("                        // ❌ FAILURE: Enhanced failure reporting with detailed analysis");
                        pw.println("                        String errorType = t.getClass().getSimpleName();");
                        pw.println("                        String failureReason = \"\";");
                        pw.println("                        if (t instanceof java.net.ConnectException) {");
                        pw.println("                            errorType = \"Connection Failed\";");
                        pw.println("                            failureReason = \"Service unreachable - Connection refused\";");
                        pw.println("                        } else if (t instanceof AssertionError) {");
                        pw.println("                            errorType = \"Status Code Mismatch\";");
                        pw.println("                            failureReason = \"Expected vs actual status code mismatch\";");
                        pw.println("                        } else if (t instanceof java.net.SocketTimeoutException) {");
                        pw.println("                            errorType = \"Request Timeout\";");
                        pw.println("                            failureReason = \"Service response timeout\";");
                        pw.println("                        } else if (t.getMessage() != null && t.getMessage().contains(\"404\")) {");
                        pw.println("                            errorType = \"Not Found (404)\";");
                        pw.println("                            failureReason = \"API endpoint not found\";");
                        pw.println("                        } else if (t.getMessage() != null && t.getMessage().contains(\"500\")) {");
                        pw.println("                            errorType = \"Internal Server Error (500)\";");
                        pw.println("                            failureReason = \"Service internal error\";");
                        pw.println("                        } else {");
                        pw.println("                            failureReason = \"Unexpected error: \" + errorType;");
                        pw.println("                        }");
                        pw.println("                        ");
                        pw.println("                        // Enhanced failure parameters");
                        pw.println("                        String resultMsg = \"❌ FAILED (\" + errorType;");
                        pw.println("                        if (failedStatusCode >= 0) {");
                        pw.println("                            resultMsg += \", status: \" + failedStatusCode;");
                        pw.println("                        }");
                        pw.println("                        if (failedResponseTime >= 0) {");
                        pw.println("                            resultMsg += \", \" + failedResponseTime + \"ms\";");
                        pw.println("                        }");
                        pw.println("                        resultMsg += \")\";");
                        pw.println("                        Allure.parameter(\"🎯 Result\", resultMsg);");
                        pw.println("                        Allure.parameter(\"🔍 Failure Reason\", failureReason);");
                        pw.println("                        Allure.parameter(\"🏢 Failed Service\", \"" + escape(step.getServiceName()) + "\");");
                        pw.println("                        Allure.parameter(\"📡 Failed Method\", \"" + verb.toUpperCase() + "\");");
                        pw.println("                        Allure.parameter(\"🔗 Failed Endpoint\", \"" + escape(step.getPath()) + "\");");
                        pw.println("                        ");
                        pw.println("                        // 🔥 CRITICAL: Attach response body for failed requests");
                        pw.println("                        if (failedResponseBody != null) {");
                        pw.println("                            String responseTitle = \"📥 Response (\" + failedStatusCode + \")\";");
                        pw.println("                            Allure.addAttachment(responseTitle, \"application/json\", failedResponseBody);");
                        pw.println("                        }");
                        pw.println("                        ");
                        pw.println("                        // Comprehensive error details");
                        pw.println("                        StringBuilder errorDetails = new StringBuilder();");
                        pw.println("                        errorDetails.append(\"❌ STEP FAILURE ANALYSIS\\n\");");
                        pw.println("                        errorDetails.append(\"=====================================\\n\\n\");");
                        pw.println("                        errorDetails.append(\"📋 Step: " + escape(stepTitle) + "\\n\");");
                        pw.println("                        errorDetails.append(\"🏢 Service: " + escape(step.getServiceName()) + "\\n\");");
                        pw.println("                        errorDetails.append(\"📡 Method: " + verb.toUpperCase() + "\\n\");");
                        pw.println("                        errorDetails.append(\"🔗 Endpoint: " + escape(step.getPath()) + "\\n\");");
                        pw.println("                        errorDetails.append(\"💥 Error Type: \").append(errorType).append(\"\\n\");");
                        pw.println("                        errorDetails.append(\"🔍 Reason: \").append(failureReason).append(\"\\n\\n\");");
                        pw.println("                        errorDetails.append(\"📜 Full Error Message:\\n\");");
                        pw.println("                        errorDetails.append(t.getMessage() != null ? t.getMessage() : \"No message\").append(\"\\n\\n\");");
                        pw.println("                        if (t.getCause() != null) {");
                        pw.println("                            errorDetails.append(\"🔗 Root Cause:\\n\");");
                        pw.println("                            errorDetails.append(t.getCause().toString()).append(\"\\n\\n\");");
                        pw.println("                        }");
                        pw.println("                        errorDetails.append(\"🔧 Troubleshooting Tips:\\n\");");
                        pw.println("                        if (errorType.contains(\"Connection Failed\")) {");
                        pw.println("                            errorDetails.append(\"• Check if the service is running\\n• Verify network connectivity\\n• Check firewall settings\\n\");");
                        pw.println("                        } else if (errorType.contains(\"Timeout\")) {");
                        pw.println("                            errorDetails.append(\"• Service may be overloaded\\n• Increase timeout settings\\n• Check service health\\n\");");
                        pw.println("                        } else if (errorType.contains(\"404\")) {");
                        pw.println("                            errorDetails.append(\"• Verify API endpoint exists\\n• Check service deployment\\n• Review API documentation\\n\");");
                        pw.println("                        } else if (errorType.contains(\"500\")) {");
                        pw.println("                            errorDetails.append(\"• Check service logs\\n• Verify service configuration\\n• Review dependencies\\n\");");
                        pw.println("                        } else {");
                        pw.println("                            errorDetails.append(\"• Review full error message\\n• Check service status\\n• Verify request parameters\\n\");");
                        pw.println("                        }");
                        pw.println("                        ");
                        // Remove detailed failure analysis - replaced by intelligent analysis
                        // Wait for Jaeger to ingest the failed call's spans before searching for
                        // the trace.  Default lowered to 0ms: the marker-first lookup INSIDE
                        // attachJaegerTrace polls 5×200ms for ingest to settle, which is the
                        // correct mechanism for propagation. Property
                        // mst.test.jaeger.propagation.delay.ms remains exposed for operators
                        // running against SUTs that do not honor W3C traceparent — in that
                        // case the fallback time-window heuristic benefits from a pre-sleep.
                        pw.println("                        long __jaegerPropagationDelayMs;");
                        pw.println("                        try {");
                        pw.println("                            __jaegerPropagationDelayMs = Long.parseLong(");
                        pw.println("                                System.getProperty(\"mst.test.jaeger.propagation.delay.ms\", \"0\"));");
                        pw.println("                        } catch (NumberFormatException nfe) { __jaegerPropagationDelayMs = 0L; }");
                        pw.println("                        if (__jaegerPropagationDelayMs > 0) {");
                        pw.println("                            try { Thread.sleep(__jaegerPropagationDelayMs); }");
                        pw.println("                            catch (InterruptedException ie) { Thread.currentThread().interrupt(); }");
                        pw.println("                        }");
                        pw.println("                        CLIENT_RESPONSE_BODY.set(failedResponseBody);");
                        pw.println("                        if (failedStatusCode > 0) CLIENT_RESPONSE_STATUS.set(failedStatusCode);");
                        pw.println("                        attachJaegerTrace(\"" + escape(step.getServiceName()) + "\", \"" + verb.toUpperCase() + "\", \"" + escape(step.getPath()) + "\", requestStartMicros, allStepParameters, true, __mstTraceId" + stepIdx + ", \"" + escape(testMethodName) + "\", __targetService, __targetParam);");
                        pw.println("                        ");
                        // Phase 2.F: ResponseEnvelopeInvariant carries the contract the deleted
                        // SoftErrorRuleCache used to encode (a soft error in a 2xx response on a
                        // negative variant flips FAIL -> PASS). The verdict was populated by the
                        // attachJaegerTrace() call above (it stashes the result on LAST_VERDICT).
                        if (isNegativeTest) {
                            pw.println("                        // Phase 2.F: Trace Shape Oracle — flip negative test FAIL -> PASS when");
                            pw.println("                        // ResponseEnvelopeInvariant reports a violation (the modern replacement for");
                            pw.println("                        // the deleted SoftErrorRuleCache soft-error contract).");
                            pw.println("                        TraceShapeVerdict tsoVerdict = LAST_VERDICT.get();");
                            pw.println("                        if (tsoVerdict != null) {");
                            pw.println("                            boolean respEnvelopeViolation = false;");
                            pw.println("                            for (TraceShapeVerdict.InvariantOutcome o : tsoVerdict.getOutcomes()) {");
                            pw.println("                                if (\"RESPONSE_ENVELOPE\".equals(o.kind) && !o.passed) { respEnvelopeViolation = true; break; }");
                            pw.println("                            }");
                            pw.println("                            if (respEnvelopeViolation) {");
                            pw.println("                                // Overwrite the failed marker set at the top of this catch");
                            pw.println("                                // block: without it the scenario-end tally still counts this");
                            pw.println("                                // step as failed and fail()s the whole test, defeating the flip.");
                            pw.println("                                stepResults.put(" + stepIdx + ", true);");
                            pw.println("                                Allure.step(\"✅ negative variant PASSED via ResponseEnvelopeInvariant violation (replaces SoftErrorRuleCache contract)\");");
                            pw.println("                                System.out.println(\"✅ Negative test PASSED: Trace Shape Oracle detected ResponseEnvelopeInvariant violation\");");
                            pw.println("                                return; // suppress the wrapping RuntimeException — variant is PASS");
                            pw.println("                            }");
                            pw.println("                        }");
                            pw.println("                        ");
                        }
                        pw.println("                        // 🔥 CRITICAL: Throw exception to mark step as FAILED (red arrow) in Allure");
                        pw.println("                        throw new RuntimeException(\"" + escape(stepTitle) + " failed: \" + failureReason + \" (\" + errorType + \")\", t);");
                        pw.println("                    }");
                            
                            // 🔥 FIX: Proper SKIP reporting with correct Allure status
                            pw.println("                } else {");
                            pw.println("                    // ⏭️ SKIP: Clean skip reporting with proper Allure status");
                            pw.println("                    System.out.println(\"⏭️ SKIPPING: " + escape(stepTitle) + " - \" + skipReason);");
                            pw.println("                    stepResults.put(" + stepIdx + ", false);");
                            pw.println("                    ");
                            pw.println("                    // Single skip status parameter");
                            pw.println("                    Allure.parameter(\"🎯 Result\", \"⏭️ SKIPPED (\" + skipCategory.replaceAll(\"🔐 |📊 |🔄 \", \"\") + \")\");");
                            pw.println("                    ");
                            pw.println("                    // Single skip reason attachment");
                            pw.println("                    Allure.addAttachment(\"⏭️ Skip Details\", \"text/plain\", \"Reason: \" + skipReason);");
                            pw.println("                    ");
                            pw.println("                    // 🔥 CRITICAL: Throw Assumption exception to mark step as SKIPPED (yellow arrow) in Allure");
                            pw.println("                    throw new org.junit.AssumptionViolatedException(\"Step skipped: \" + skipReason);");
                            pw.println("                }");
                            
                            pw.println("            });");
                            pw.println("        } catch (Exception stepException) {");
                            pw.println("            // Step wrapper exception handling - maintain execution flow");
                            pw.println("            if (stepException instanceof RuntimeException && stepException.getMessage().startsWith(\"Step failed:\")) {");
                            pw.println("                // This is a failed step - already handled, just continue");
                            pw.println("                System.out.println(\"Step " + stepIdx + " marked as FAILED in Allure\");");
                            pw.println("            } else if (stepException instanceof org.junit.AssumptionViolatedException) {");
                            pw.println("                // This is a skipped step - already handled, just continue"); 
                            pw.println("                System.out.println(\"Step " + stepIdx + " marked as SKIPPED in Allure\");");
                            pw.println("            } else {");
                            pw.println("                // Unexpected wrapper failure");
                            pw.println("                System.out.println(\"⚠️ Step wrapper failed for " + escape(stepTitle) + ": \" + stepException.getMessage());");
                            pw.println("                stepResults.put(" + stepIdx + ", false);");
                            pw.println("            }");
                            pw.println("        }");
                            pw.println("        } // end per-step scope block");
                        } else {
                            // Non-Allure version - simplified (fallback for when Allure is disabled)
                            pw.println("        // Non-Allure version - simplified execution");
                        pw.println("        MultiServiceTestCase.ExecutionDecision decision" + stepIdx + ";");
                        
                        // Generate the actual decision logic based on step's dependency configuration
                        // RESILIENT BYPASS: Data/workflow dependencies no longer cause skips.
                        // Only auth failure causes step skips in non-Allure mode.
                        pw.println("        // Resilient mode: execute regardless of predecessor results");
                        pw.println("        decision" + stepIdx + " = new MultiServiceTestCase.ExecutionDecision(true, null, null);");
                            
                            pw.println("        if (decision" + stepIdx + ".shouldExecute && loginSucceeded.get()) {");
                            pw.println("            System.out.println(\"✅ EXECUTING: " + escape(stepTitle) + "\");");
                            pw.println("            // Execute step logic here (simplified version)");
                            pw.println("            stepResults.put(" + stepIdx + ", true);");
                            pw.println("        } else {");
                            pw.println("            System.out.println(\"⏭️ SKIPPING: " + escape(stepTitle) + "\");");
                            pw.println("            stepResults.put(" + stepIdx + ", false);");
                            pw.println("        }");
                        }
                        
                        pw.println();
                        stepIdx++;
                    }

                    // Check overall scenario result with detailed reporting
                    pw.println("        // Evaluate scenario result with comprehensive reporting");
                    pw.println("        long successfulSteps = stepResults.values().stream().filter(result -> result).count();");
                    pw.println("        long failedSteps = stepResults.values().stream().filter(result -> !result).count();");
                    pw.println("        long totalSteps = stepResults.size();");
                    pw.println("        ");
                    
                    // Add clean test summary
                    if (allureReport) {
                        pw.println("        // Add clean test summary - no duplicate content");
                        pw.println("        String overallResult;");
                        pw.println("        String severity;");
                        pw.println("        if (!loginSucceeded.get()) {");
                        pw.println("            overallResult = \"❌ AUTHENTICATION FAILED\";");
                        pw.println("            severity = \"critical\";");
                        pw.println("        } else if (failedSteps == 0) {");
                        pw.println("            overallResult = \"✅ ALL STEPS PASSED\";");
                        pw.println("            severity = \"normal\";");
                        pw.println("        } else if (successfulSteps > 0) {");
                        pw.println("            overallResult = \"⚠️ PARTIAL FAILURE\";");
                        pw.println("            severity = \"major\";");
                        pw.println("        } else {");
                        pw.println("            overallResult = \"❌ ALL STEPS FAILED\";");
                        pw.println("            severity = \"critical\";");
                        pw.println("        }");
                        pw.println("        ");
                        pw.println("        // If the trace oracle found a hidden-downstream failure on the (passing) request,");
                        pw.println("        // carry it INTO the scenario result so a green test is not read as a clean success.");
                        pw.println("        // WARN does not fail the test by design (a swallowed otel-error may be tolerated, e.g.");
                        pw.println("        // non-critical ads), but the headline must show it. Gate with --fail-on=warn to make red.");
                        pw.println("        String hdWarn = \"\";");
                        pw.println("        TraceShapeVerdict __scenVerdict = LAST_VERDICT.get();");
                        pw.println("        LAST_VERDICT.remove();  // avoid a stale verdict leaking into the next scenario");
                        pw.println("        if (__scenVerdict != null) {");
                        pw.println("            int __hdCount = 0;");
                        pw.println("            for (TraceShapeVerdict.InvariantOutcome __o : __scenVerdict.getOutcomes()) {");
                        pw.println("                if (!__o.passed && \"HIDDEN_DOWNSTREAM_FAILURE\".equals(__o.kind)) __hdCount++;");
                        pw.println("            }");
                        pw.println("            if (__hdCount > 0) hdWarn = \"  ⚠️ \" + __hdCount + \" HIDDEN DOWNSTREAM FAILURE (passed, but a 2xx hid a swallowed downstream error)\";");
                        pw.println("        }");
                        pw.println("        ");
                        pw.println("        // Single summary parameter with all key info");
                        pw.println("        Allure.parameter(\"📊 Scenario Result\", overallResult + \" (\" + successfulSteps + \"/\" + totalSteps + \" steps)\" + hdWarn);");
                        pw.println("        ");
                        pw.println("        // Add clean categorization");
                        pw.println("        // NOTE: do NOT emit an extra 'feature' label here — it conflicts with class-level");
                        pw.println("        // @Feature(serviceName) and Allure treats duplicate label names as additive,");
                        pw.println("        // producing phantom groupings like \"Microservice Workflow\" in the Behaviors view.");
                        pw.println("        Allure.label(\"severity\", severity);");
                        pw.println("        Allure.description(\"Microservice test scenario with \" + totalSteps + \" steps.\");");
                        pw.println("        ");
                    }
                    
                    pw.println("        System.out.println(\"=== SCENARIO RESULT ===\");");
                    pw.println("        System.out.println(\"Scenario: " + escape(scenario.getOperationId()) + "\");");
                    pw.println("        System.out.println(\"Total Steps: \" + totalSteps);");
                    pw.println("        System.out.println(\"Successful: \" + successfulSteps);");
                    pw.println("        System.out.println(\"Failed: \" + failedSteps);");
                    pw.println("        System.out.println(\"Login Status: \" + (loginSucceeded.get() ? \"SUCCESS\" : \"FAILED\"));");
                    pw.println("        ");
                    
                    // Enhanced failure logic - fail if ANY step fails OR login fails
                    pw.println("        // IMPROVED: Test fails if ANY step fails or login fails (not just when ALL fail)");
                    pw.println("        if (!loginSucceeded.get()) {");
                    pw.println("            fail(\"Scenario FAILED: Authentication failed - cannot proceed with API calls\");");
                    pw.println("        } else if (failedSteps > 0) {");
                    pw.println("            fail(\"Scenario FAILED: \" + failedSteps + \" out of \" + totalSteps + \" steps failed. \" +");
                    pw.println("                 \"In microservice testing, all workflow steps must succeed for end-to-end validation.\");");
                    pw.println("        } else if (successfulSteps == 0) {");
                    pw.println("            fail(\"Scenario FAILED: No steps executed successfully - check service availability\");");
                    pw.println("        } else {");
                    pw.println("            System.out.println(\"🎉 Scenario PASSED: All \" + totalSteps + \" steps completed successfully\");");
                    pw.println("        }");
                    pw.println("    }");
                    pw.println();
                    scenarioIdx++;
                }

                pw.println("}");
            }
        } catch (Exception e) {
            throw new RESTestException("Error writing REST‑assured suite: " + e.getMessage(), e);
        }
    }


    private static boolean isStandardVerb(String v) {
        switch (v) {
            case "get": case "post": case "put":
            case "delete": case "patch": return true;
            default: return false;
        }
    }
    
    /**
     * Create a descriptive subSuite label for Allure grouping.
     *
     * Examples:
     *   Positive test       → "[Valid] v3"
     *   Negative test       → "[Fault: POST /order] BOUNDARY_VIOLATION (v5)"
     *   Exploration test    → "[Explore 404]"
     */
    private static String createCleanSubSuiteLabel(MultiServiceTestCase mstc, String testMethodName) {
        // Exploration tests
        if (mstc.isStatusCodeExplorationTest()) {
            int targetStatus = mstc.getTargetStatusCode();
            if (targetStatus > 0) {
                return "[Explore " + targetStatus + "]";
            }
            if (testMethodName != null && testMethodName.contains("_explore_")) {
                int idx = testMethodName.lastIndexOf("_explore_");
                String suffix = testMethodName.substring(idx + 9);
                int underscoreIdx = suffix.indexOf('_');
                if (underscoreIdx > 0) {
                    suffix = suffix.substring(0, underscoreIdx);
                }
                return "[Explore " + suffix + "]";
            }
        }

        // Negative tests — show the targeted API path and fault type
        if (mstc.getFaulty()) {
            String variant = extractVariantTag(testMethodName);
            String faultType = mstc.getFaultTypeCategory();
            String apiPath = mstc.getTargetFaultRootApiPath();

            if (apiPath != null && !apiPath.isEmpty() && faultType != null) {
                return "[Fault: " + apiPath + "] " + faultType + " (" + variant + ")";
            }
            String targetRoot = mstc.getTargetFaultRootId();
            if (targetRoot != null && faultType != null) {
                return "[Fault: " + targetRoot + "] " + faultType + " (" + variant + ")";
            }
            return "[Fault] " + variant;
        }

        // Positive tests
        return "[Valid] " + extractVariantTag(testMethodName);
    }

    /**
     * Extract the variant tag from a flow-centric test name.
     *   "test_positive_flow_S12_v3" → "v3"
     *   "test_negative_flow_S12_v5_fault_Root2_OVERFLOW" → "v5"
     * Falls back to "v1" for legacy names.
     */
    private static String extractVariantTag(String testMethodName) {
        if (testMethodName == null) return "v1";
        // Look for _vN segment
        int vIdx = testMethodName.indexOf("_v");
        if (vIdx >= 0) {
            int start = vIdx + 1; // skip the underscore
            int end = testMethodName.indexOf('_', start + 1);
            return end > start ? testMethodName.substring(start, end) : testMethodName.substring(start);
        }
        // Legacy fallback: grab last numeric segment
        String[] parts = testMethodName.split("_");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].equals("explore") || parts[i].startsWith("retry")) continue;
            try { Integer.parseInt(parts[i]); return "v" + parts[i]; }
            catch (NumberFormatException ignored) {}
        }
        return "v1";
    }
    
    /**
     * Build a human-readable flow path from all root steps in a test case.
     *
     * 1-Root:  "GET /api/v1/prices"
     * Multi:   "POST /login -> POST /order -> POST /pay"
     */
    private static String buildFlowPath(MultiServiceTestCase tc) {
        if (tc == null || tc.getSteps().isEmpty()) return "Empty Flow";

        StringBuilder sb = new StringBuilder();
        for (MultiServiceTestCase.StepCall step : tc.getSteps()) {
            if (!step.isTopLevelRoot()) continue;

            if (sb.length() > 0) sb.append(" -> ");

            String verb = "GET";
            String path = step.getPath() != null ? step.getPath() : "";
            if (step.getMethod() != null) {
                if (step.getMethod().getMethod() != null) {
                    verb = step.getMethod().getMethod().toUpperCase();
                }
                if (step.getMethod().getTestPath() != null) {
                    path = step.getMethod().getTestPath();
                }
            }
            sb.append(verb).append(' ').append(path);
        }
        return sb.length() > 0 ? sb.toString() : "Empty Flow";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Form-encode a query name/value at generation time. The generated request
     * spec runs with urlEncodingEnabled(false), so REST Assured appends
     * queryParam values verbatim — without this, a value containing '&', '=',
     * '#' or whitespace would corrupt the request line.
     */
    private static String urlEncode(String s) {
        if (s == null) return "";
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Escape a string for use in Java string literals (for generated test code).
     * This handles all special characters that need escaping in Java strings.
     */
    private static String escapeJavaString(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;  // Backslash
                case '\"': sb.append("\\\""); break;  // Double quote
                case '\n': sb.append("\\n"); break;   // Newline
                case '\r': sb.append("\\r"); break;   // Carriage return
                case '\t': sb.append("\\t"); break;   // Tab
                case '\b': sb.append("\\b"); break;   // Backspace
                case '\f': sb.append("\\f"); break;   // Form feed
                default:
                    // For any non-printable or control characters, use unicode escape
                    if (c < 32 || c > 126) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    private static String sanitize(String s) {
        if (s == null) return "Scenario";
        // Preserve casing for flow-centric names like "Flow_Scenario_12".
        // Only strip characters that are invalid in Java identifiers.
        return s.replaceAll("[^a-zA-Z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }
    
    /**
     * Get human-readable dependency type string for a step
     */
    private static String getDependencyTypeString(MultiServiceTestCase.StepCall step) {
        if (step.getParamDependencies() != null && !step.getParamDependencies().isEmpty()) {
            return "DATA_DEPENDENCY (needs data from previous steps)";
        } else if (step.getWorkflowDependencies() != null && !step.getWorkflowDependencies().isEmpty()) {
            return "WORKFLOW_DEPENDENCY (part of sequential workflow)";
        } else {
            return "INDEPENDENT (can execute regardless of other step results)";
        }
    }
    
    /**
     * Generate detailed dependency analysis report for Allure
     */
    private static void generateDependencyAnalysisReport(MultiServiceTestCase.StepCall step, int stepIdx, PrintWriter pw) {
        pw.println("                // Generate comprehensive dependency analysis attachment");
        pw.println("                StringBuilder depAnalysis = new StringBuilder();");
        pw.println("                depAnalysis.append(\"📋 DEPENDENCY ANALYSIS REPORT\\n\\n\");");
        pw.println("                depAnalysis.append(\"🔍 Step: \" + " + stepIdx + " + \" - " + escape(step.getServiceName()) + "\\n\");");
        pw.println("                depAnalysis.append(\"📡 Method: " + (step.getMethod() != null ? step.getMethod().getMethod() : "UNKNOWN") + "\\n\");");
        pw.println("                depAnalysis.append(\"🔗 Path: " + escape(step.getPath()) + "\\n\\n\");");
        
        // Add parameter dependencies analysis
        if (step.getParamDependencies() != null && !step.getParamDependencies().isEmpty()) {
            pw.println("                depAnalysis.append(\"💾 DATA DEPENDENCIES:\\n\");");
            for (Map.Entry<String, MultiServiceTestCase.Dependency> dep : step.getParamDependencies().entrySet()) {
                pw.println("                depAnalysis.append(\"  • Parameter '" + escape(dep.getKey()) + "' requires data from Step \" + " + dep.getValue().sourceStepIndex + " + \" (field: '" + escape(dep.getValue().sourceOutputKey) + "')\\n\");");
            }
            pw.println("                depAnalysis.append(\"\\n\");");
        }
        
        // Add workflow dependencies analysis  
        if (step.getWorkflowDependencies() != null && !step.getWorkflowDependencies().isEmpty()) {
            pw.println("                depAnalysis.append(\"🔄 WORKFLOW DEPENDENCIES:\\n\");");
            for (Integer workflowDep : step.getWorkflowDependencies()) {
                pw.println("                depAnalysis.append(\"  • Must execute after Step \" + " + workflowDep + " + \" completes successfully\\n\");");
            }
            pw.println("                depAnalysis.append(\"\\n\");");
        }
        
        // Add execution decision reasoning
        pw.println("                depAnalysis.append(\"📊 EXECUTION DECISION LOGIC:\\n\");");
        pw.println("                depAnalysis.append(\"  Reason: \" + decision" + stepIdx + ".skipReason.description + \"\\n\");");
        pw.println("                depAnalysis.append(\"  Details: \" + decision" + stepIdx + ".skipMessage + \"\\n\\n\");");
        
        // Add step results context
        pw.println("                depAnalysis.append(\"📈 PREVIOUS STEP RESULTS:\\n\");");
        pw.println("                for (Map.Entry<Integer, Boolean> result : stepResults.entrySet()) {");
        pw.println("                    String status = result.getValue() ? \"✅ PASSED\" : \"❌ FAILED\";");
        pw.println("                    depAnalysis.append(\"  Step \" + result.getKey() + \": \" + status + \"\\n\");");
        pw.println("                }");
        pw.println("                depAnalysis.append(\"\\n\");");
        
        // Add impact analysis
        pw.println("                depAnalysis.append(\"🎯 IMPACT ANALYSIS:\\n\");");
        pw.println("                depAnalysis.append(\"  • This step was skipped to prevent cascading failures\\n\");");
        pw.println("                depAnalysis.append(\"  • Dependent steps may also be skipped if they rely on this step\\n\");");
        pw.println("                depAnalysis.append(\"  • Independent steps will continue to execute\\n\");");
        
        pw.println("                Allure.addAttachment(\"🔍 Dependency Analysis Report\", \"text/plain\", depAnalysis.toString());");
    }

    /** Two-phase Phase-A rescue mode (see {@link #setPhaseARescuePlaceholders}). */
    private boolean phaseARescuePlaceholders = false;

    public void setClassName(String className) {
        this.testClassName = className;
    }

    /**
     * Two-phase Phase-A rescue mode: when true, a positive scenario whose generator could not ground
     * every parameter (synthetic placeholder) is treated as a rescue-target POSITIVE (expects 2xx)
     * instead of being reclassified as a negative. Its 400 then fails the positive assertion → the
     * enhancer rescues it from the SUT's error message → the rescued 2xx harvests VERIFIED_VALID for
     * the verified pool. Off (default) keeps the normal false-positive-avoidance reclassification.
     */
    public void setPhaseARescuePlaceholders(boolean v) {
        this.phaseARescuePlaceholders = v;
    }

    public void setTestId(String testId) {
        // No-op since RESTAssuredWriter inheritance was severed. Generated
        // tests carry their own testId in package + class names.
    }

    /** Caller setter kept for source-compat with the previous RESTAssuredWriter API. */
    public void setAPIName(String apiName) {
        // No-op: previously stored on the parent; the writer never reads
        // this field after the inheritance sever.
    }
}
