package bookinfo_productpage_outage.BookinfoTest_1791942463835;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.AssumptionViolatedException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.hamcrest.Matchers;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.json.JSONObject;
import org.json.JSONArray;
import io.mist.core.analysis.TraceErrorAnalyzer;
import io.mist.core.analysis.TraceShapeAdapter;
import io.mist.core.smart.ParameterErrorAnalyzer;
import io.mist.core.smart.InputFetchRegistry;
import io.mist.core.smart.ParameterError;
import io.mist.core.oracle.shape.ShapeInvariantStore;
import io.mist.core.oracle.shape.TraceModel;
import io.mist.core.oracle.shape.TraceShapeOracle;
import io.mist.core.oracle.shape.TraceShapeVerdict;
import static org.junit.Assert.*;
import io.mist.core.testcase.MultiServiceTestCase;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.model.Status;

@Epic("Baseline Tests")
@Feature("productpage.default")
@io.qameta.allure.junit4.DisplayName("Flow_Scenario_1 | GET /productpage")
public class Flow_Scenario_1 {

    // LLM validation singletons — created ONCE per test class, not per test method
    private static final boolean LLM_VALIDATION_ENABLED = Boolean.parseBoolean(System.getProperty("llm.response.validation.enabled", "false"));
    private static final boolean LLM_ONLY_2XX = Boolean.parseBoolean(System.getProperty("llm.response.validation.only.2xx", "true"));
    private static final boolean LLM_INCLUDE_RCA = Boolean.parseBoolean(System.getProperty("llm.response.validation.include.rca", "true"));
    private static io.mist.core.generation.ZeroShotLLMGenerator llmValidator;

    // Trace Shape Oracle (Phase 2.F) — created ONCE per test class, reads .mist/trace-shape-invariants.json
    private static TraceShapeOracle oracle;
    // Per-step verdict, populated from inside attachJaegerTrace(...) so the test-method assertion
    // path can consult it when deciding whether to flip a negative test from FAIL to PASS.
    private static final ThreadLocal<TraceShapeVerdict> LAST_VERDICT = new ThreadLocal<>();
    // Phase 4.x: carries the live client response body into attachJaegerTrace() so the
    // ResponseEnvelope invariant can read it (Jaeger spans omit http.response.body).
    private static final ThreadLocal<String> CLIENT_RESPONSE_BODY = new ThreadLocal<>();
    private static final ThreadLocal<Integer> CLIENT_RESPONSE_STATUS = new ThreadLocal<>();

    // Output-coverage backstop (Fix 3 Layer 3): shared response-fingerprint set
    private static final java.util.Set<String> SEEN_RESPONSE_HASHES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static String responseFingerprint(int status, String body) {
        String payload = status + "|" + (body == null ? "" : body);
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toString(payload.hashCode());
        }
    }

    // Jaeger configuration (defaults captured from this run's config)
    private static final boolean JAEGER_ENABLED = Boolean.parseBoolean(System.getProperty("jaeger.enabled", "true"));
    // /traces/{id} returns the COMPLETE trace tree with all spans
    private static final String JAEGER_BASE_URL = System.getProperty("jaeger.base.url", "http://localhost:16686/jaeger/api");
    private static final String JAEGER_LOOKBACK = System.getProperty("jaeger.lookback", "10m");

    private static void attachJaegerTrace(String service, String method, String path, long requestStartMicros, Map<String, String> stepParameters, boolean isStepFailed, String markerTraceId, String testMethodName, String targetService, String targetParam) {
        if (!JAEGER_ENABLED) return;
        // Clear any verdict left by the previous step so a trace-fetch miss in
        // THIS step cannot make the oracle gate read the previous step's verdict.
        // Repopulated below via LAST_VERDICT.set(verdict) only when a trace is found.
        LAST_VERDICT.remove();
        try {
            String operation = method + " " + path;
            String opEncoded = URLEncoder.encode(operation, StandardCharsets.UTF_8);
            String svcEncoded = URLEncoder.encode(service, StandardCharsets.UTF_8);
            // Create very precise time window for unique trace identification (10 seconds before, 30 seconds after)
            long start = requestStartMicros - (10L * 1000L * 1000L); // 10 seconds earlier (us)
            long end = requestStartMicros + (30L * 1000L * 1000L); // 30 seconds later (us)
            if (start < 0) start = 0;
            
            // Heuristic fallbacks (only reached if the marker-first exact-ID lookup
            // below misses). The primary path is the W3C-traceparent marker lookup;
            // these are SUT-agnostic — a broad time-window search (which already
            // returns the gateway + downstream spans) plus the target-service queries.
            // (Previously this hardcoded service=ts-gateway-service as the priority
            // query — a train-ticket assumption useless on any other SUT.)
            String[] queryUrls = {
                // Strategy 1: Broad search — any traces in the window (includes the
                // ingress/gateway span and all downstream spans of this request).
                JAEGER_BASE_URL + "/traces?limit=200&start=" + start + "&end=" + end,
                // Strategy 2: Target service with exact operation
                JAEGER_BASE_URL + "/traces?limit=50&service=" + svcEncoded + "&operation=" + opEncoded + "&start=" + start + "&end=" + end,
                // Strategy 3: Target service with method only
                JAEGER_BASE_URL + "/traces?limit=50&service=" + svcEncoded + "&operation=" + URLEncoder.encode(method, StandardCharsets.UTF_8) + "&start=" + start + "&end=" + end,
                // Strategy 4: Target service in time window
                JAEGER_BASE_URL + "/traces?limit=100&service=" + svcEncoded + "&start=" + start + "&end=" + end,
                // Strategy 5: Recent traces with lookback (last resort)
                JAEGER_BASE_URL + "/traces?limit=200&lookback=" + JAEGER_LOOKBACK
            };
            
            HttpClient client = HttpClient.newHttpClient();
            StringBuilder debugInfo = new StringBuilder();
            debugInfo.append("🔍 Jaeger Query Debug Info:\n");
            debugInfo.append("Target Service: ").append(service).append("\n");
            debugInfo.append("Method: ").append(method).append("\n");
            debugInfo.append("Path: ").append(path).append("\n");
            debugInfo.append("Operation: ").append(operation).append("\n");
            debugInfo.append("Request Start (μs): ").append(requestStartMicros).append("\n");
            debugInfo.append("Search Window: ").append(start).append(" to ").append(end).append(" (μs)\n");
            debugInfo.append("Time Window: ").append((end - start) / 1000000).append(" seconds\n");
            debugInfo.append("🔎 Lookup: marker-first exact-ID (W3C traceparent), then broad time-window + target-service\n");
            debugInfo.append("⏱️ Test Separation: 2-second delay enforced between executions for unique traces\n");
            debugInfo.append("🔄 Trace Propagation: 3-second delay after execution for Jaeger indexing\n");
            debugInfo.append("🔄 Retry Strategy: Multiple attempts to find current execution trace\n\n");
            
            // 🔄 Retry mechanism to find current execution trace
            boolean foundCurrentTrace = false;
            int maxRetries = 3;
            JSONObject globalBestTrace = null;  // Track best trace across all queries
            long globalBestDiff = Long.MAX_VALUE;
            int globalBestScore = -1;
            
            // ── Marker-first exact-ID lookup (W3C trace context) ──
            // Test client injects traceparent: 00-<traceId>-...-01 on every HTTP request.
            // Jaeger ingests SUT spans under exactly that traceId, so GET /traces/<id> is a
            // deterministic 1:1 lookup — no time-window ambiguity under parallel execution.
            // Poll 5×200ms for ingest to settle; fallthrough to heuristic if SUT doesn't honor
            // W3C traceparent or ingest is unusually delayed.
            if (markerTraceId != null && !markerTraceId.isEmpty()) {
                debugInfo.append("🎯 Marker-first query: ").append(JAEGER_BASE_URL).append("/traces/").append(markerTraceId).append("\n");
                for (int __markAttempt = 0; __markAttempt < 5 && globalBestTrace == null; __markAttempt++) {
                    try {
                        HttpRequest __mreq = HttpRequest.newBuilder(
                            java.net.URI.create(JAEGER_BASE_URL + "/traces/" + markerTraceId)).GET().build();
                        HttpResponse<String> __mres = HttpClient.newHttpClient().send(__mreq, HttpResponse.BodyHandlers.ofString());
                        if (__mres.statusCode() == 200) {
                            JSONObject __mjson = new JSONObject(__mres.body());
                            JSONArray __mdata = __mjson.optJSONArray("data");
                            if (__mdata != null && __mdata.length() > 0) {
                                JSONObject __mt = __mdata.getJSONObject(0);
                                JSONArray __mspans = __mt.optJSONArray("spans");
                                if (__mspans != null && __mspans.length() > 0) {
                                    globalBestTrace = __mt;
                                    globalBestScore = Integer.MAX_VALUE;
                                    foundCurrentTrace = true;
                                    debugInfo.append("✅ Marker match at attempt ").append(__markAttempt + 1)
                                             .append(" (").append(__mspans.length()).append(" spans)\n");
                                }
                            }
                        }
                    } catch (Exception __markEx) { /* swallow + retry */ }
                    if (globalBestTrace == null) {
                        try { Thread.sleep(200); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    }
                }
                if (globalBestTrace == null) {
                    debugInfo.append("⚠️ Marker query exhausted; falling back to time-window heuristic\n");
                }
            }
            
            for (int retry = 0; retry < maxRetries && !foundCurrentTrace; retry++) {
                if (retry > 0) {
                    debugInfo.append("\n🔄 RETRY ").append(retry).append(": Searching again for current execution trace...\n");
                    try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
                
            for (int queryIdx = 0; queryIdx < queryUrls.length; queryIdx++) {
                String url = queryUrls[queryIdx];
                debugInfo.append("Query ").append(queryIdx + 1).append(": ").append(url).append("\n");
                
                HttpRequest req = HttpRequest.newBuilder(java.net.URI.create(url)).GET().build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                debugInfo.append("Response Status: ").append(res.statusCode()).append("\n");
                
                if (res.statusCode() >= 200 && res.statusCode() < 300) {
                    String body = res.body();
                    debugInfo.append("Response Body Length: ").append(body.length()).append("\n");
                    
                    try {
                        JSONObject json = new JSONObject(body);
                        if (json.has("data")) {
                            JSONArray data = json.getJSONArray("data");
                            debugInfo.append("Traces Found: ").append(data.length()).append("\n");
                            
                            if (data.length() > 0) {
                                // Smart trace selection - prioritize traces with actual API calls
                                JSONObject bestTrace = null;
                                long bestDiff = Long.MAX_VALUE;
                                int bestTraceIdx = -1;
                                int bestScore = -1;
                                
                                debugInfo.append("Analyzing ").append(data.length()).append(" traces for API calls...\n");
                                
                                for (int i = 0; i < data.length(); i++) {
                                    JSONObject trace = data.getJSONObject(i);
                                    JSONArray spans = trace.optJSONArray("spans");
                                    if (spans == null) continue;
                                    
                                    int traceScore = 0;
                                    int apiCallCount = 0;
                                    boolean hasTargetOperation = false;
                                    long closestTime = Long.MAX_VALUE;
                                    
                                    // Analyze all spans in this trace
                                    for (int s = 0; s < spans.length(); s++) {
                                        JSONObject span = spans.getJSONObject(s);
                                        String opName = span.optString("operationName", "");
                                        long spanStart = span.optLong("startTime", 0L);
                                        
                                        // Check if this span is an actual API call
                                        boolean isApiSpan = false;
                                        String spanMethod = "";
                                        String spanPath = "";
                                        
                                        JSONArray tags = span.optJSONArray("tags");
                                        if (tags != null) {
                                            for (int t = 0; t < tags.length(); t++) {
                                                JSONObject tag = tags.getJSONObject(t);
                                                String key = tag.optString("key");
                                                String value = tag.optString("value");
                                                if ("http.method".equals(key)) spanMethod = value;
                                                if ("http.target".equals(key) || "http.url".equals(key)) spanPath = value;
                                            }
                                        }
                                        
                                        // Check if this is an API call
                                        if ((!spanMethod.isEmpty() && !spanPath.isEmpty() && spanPath.contains("/api/")) ||
                                            opName.contains("/api/") ||
                                            opName.matches("(GET|POST|PUT|DELETE|PATCH)\\s+/api/.*")) {
                                            isApiSpan = true;
                                            apiCallCount++;
                                        }
                                        
                                        // Check if this matches our target operation
                                        boolean isTargetOp = opName.equals(operation) ||
                                            (spanMethod.equalsIgnoreCase(method) && spanPath.contains(path)) ||
                                            (opName.contains(method) && opName.contains(path));
                                        
                                        if (isTargetOp) {
                                            hasTargetOperation = true;
                                            if (spanStart > 0L) {
                                                long timeDiff = Math.abs(spanStart - requestStartMicros);
                                                closestTime = Math.min(closestTime, timeDiff);
                                            }
                                        }
                                    }
                                    
                                    // Enhanced scoring - HEAVILY prioritize recent execution timing
                                    if (hasTargetOperation) traceScore += 1000; // Target operation match
                                    traceScore += apiCallCount * 5; // More API calls = better (reduced weight)
                                    
                                    // CRITICAL: Ultra-precise time proximity scoring (test separation enforced)
                                    if (closestTime < Long.MAX_VALUE) {
                                        if (closestTime < 1000000L) traceScore += 5000; // Within 1 second (CURRENT execution)
                                        else if (closestTime < 2000000L) traceScore += 3000; // Within 2 seconds (EXACT execution)
                                        else if (closestTime < 5000000L) traceScore += 1500; // Within 5 seconds (very recent)
                                        else if (closestTime < 10000000L) traceScore += 500; // Within 10 seconds
                                        else if (closestTime < 20000000L) traceScore += 200; // Within 20 seconds
                                        else if (closestTime < 30000000L) traceScore += 100; // Within 30 seconds
                                    }
                                    
                                    debugInfo.append("Trace ").append(i).append(": score=").append(traceScore);
                                    debugInfo.append(", APIs=").append(apiCallCount);
                                    debugInfo.append(", hasTarget=").append(hasTargetOperation);
                                    debugInfo.append(", timeDiff=").append(closestTime == Long.MAX_VALUE ? "N/A" : closestTime + "μs");
                                    debugInfo.append(", spans=").append(spans.length()).append("\n");
                                    
                                    // Show first few operations for debugging
                                    debugInfo.append("  Operations: ");
                                    for (int opIdx = 0; opIdx < Math.min(spans.length(), 5); opIdx++) {
                                        JSONObject span = spans.getJSONObject(opIdx);
                                        String opName = span.optString("operationName", "");
                                        if (opIdx > 0) debugInfo.append(", ");
                                        debugInfo.append(opName.length() > 30 ? opName.substring(0, 27) + "..." : opName);
                                    }
                                    if (spans.length() > 5) debugInfo.append(", +").append(spans.length() - 5).append(" more");
                                    debugInfo.append("\n");
                                    
                                    // Select best trace: TIMING is the primary discriminator for recent requests
                                    boolean shouldSelectTrace = false;
                                    
                                    if (traceScore > 0) { // Must have API calls
                                        if (bestTrace == null) {
                                            shouldSelectTrace = true;
                                        } else {
                                            // For recent traces (within 10 seconds), prioritize timing over score
                                            boolean currentIsRecent = closestTime < 10000000L; // 10 seconds (post-delay execution)
                                            boolean bestIsRecent = bestDiff < 10000000L;
                                            
                                            if (currentIsRecent && !bestIsRecent) {
                                                shouldSelectTrace = true; // Always prefer recent traces
                                            } else if (!currentIsRecent && bestIsRecent) {
                                                shouldSelectTrace = false; // Keep the recent one
                                            } else if (currentIsRecent && bestIsRecent) {
                                                // Both recent: pick the closest in time
                                                shouldSelectTrace = closestTime < bestDiff;
                                            } else {
                                                // Both old: use score then timing
                                                shouldSelectTrace = traceScore > bestScore || 
                                                    (traceScore == bestScore && closestTime < bestDiff);
                                            }
                                        }
                                    }
                                    
                                    if (shouldSelectTrace) {
                                        bestScore = traceScore;
                                        bestDiff = closestTime;
                                        bestTrace = trace;
                                        bestTraceIdx = i;
                                    }
                                }
                                
                                debugInfo.append("\nSelected trace ").append(bestTraceIdx).append(" with score ").append(bestScore);
                                if (bestScore > 0) {
                                    // Check if this is a current execution trace (within 3 seconds)
                                    boolean isCurrentTrace = bestDiff < 3000000L; // 3 seconds
                                    if (isCurrentTrace) {
                                        debugInfo.append(" (CURRENT EXECUTION - perfect match!)\n");
                                        foundCurrentTrace = true;
                                    } else {
                                        debugInfo.append(" (matched but not current - possibly previous execution)\n");
                                    }
                                    
                                    // Update global best trace if this is better
                                    boolean shouldUpdateGlobal = false;
                                    if (globalBestTrace == null) {
                                        shouldUpdateGlobal = true;
                                    } else if (isCurrentTrace && globalBestDiff >= 3000000L) {
                                        shouldUpdateGlobal = true; // Always prefer current over non-current
                                    } else if (isCurrentTrace && globalBestDiff < 3000000L) {
                                        shouldUpdateGlobal = bestDiff < globalBestDiff; // Both current: pick closer
                                    } else if (!isCurrentTrace && globalBestDiff >= 3000000L) {
                                        // Both non-current: pick better score or closer time
                                        shouldUpdateGlobal = bestScore > globalBestScore || 
                                            (bestScore == globalBestScore && bestDiff < globalBestDiff);
                                    }
                                    
                                    if (shouldUpdateGlobal) {
                                        // Fetch COMPLETE trace to replace summary
                                        if (bestTrace != null) {
                                            String traceId = bestTrace.optString("traceID", "");
                                            if (!traceId.isEmpty()) {
                                                debugInfo.append("\n🔄 Fetching complete trace for ID: ").append(traceId).append("\n");
                                                String traceDetailUrl = JAEGER_BASE_URL + "/traces/" + traceId;
                                                HttpRequest traceReq = HttpRequest.newBuilder(java.net.URI.create(traceDetailUrl)).GET().build();
                                                HttpResponse<String> traceRes = client.send(traceReq, HttpResponse.BodyHandlers.ofString());
                                                
                                                if (traceRes.statusCode() >= 200 && traceRes.statusCode() < 300) {
                                                    JSONObject completeTraceResponse = new JSONObject(traceRes.body());
                                                    if (completeTraceResponse.has("data") && completeTraceResponse.getJSONArray("data").length() > 0) {
                                                        bestTrace = completeTraceResponse.getJSONArray("data").getJSONObject(0);
                                                        debugInfo.append("✅ Complete trace fetched with ").append(bestTrace.optJSONArray("spans").length()).append(" spans\n");
                                                    } else {
                                                        debugInfo.append("⚠️  Complete trace response has no data, using summary trace\n");
                                                    }
                                                } else {
                                                    debugInfo.append("⚠️  Failed to fetch complete trace (status: ").append(traceRes.statusCode()).append("), using summary trace\n");
                                                }
                                            }
                                        }
                                        
                                        // Update global best
                                        globalBestTrace = bestTrace;
                                        globalBestDiff = bestDiff;
                                        globalBestScore = bestScore;
                                        debugInfo.append("✅ Updated global best trace (score: ").append(bestScore).append(", timeDiff: ").append(bestDiff / 1000000.0).append("s)\n");
                                    }
                                    
                                    // If this is a current trace, we can stop retrying
                                    if (isCurrentTrace) {
                                        debugInfo.append("✅ Found current execution trace - stopping search\n");
                                        break; // Exit query loop
                                    } else {
                                        debugInfo.append("⏭️ Continuing to search for more recent trace...\n");
                                    }
                                } else {
                                    debugInfo.append(" (no API calls found)\n");
                                    debugInfo.append("\n⚠️ This query found traces but no API calls. Continuing to next strategy...\n");
                                }
                            } else {
                                debugInfo.append("No traces found in response\n");
                            }
                        } else {
                            debugInfo.append("No 'data' field in response\n");
                        }
                    } catch (Exception e) {
                        debugInfo.append("JSON Parse Error: ").append(e.getMessage()).append("\n");
                    }
                    
                    // If this query didn't work, try next one
                    debugInfo.append("Query ").append(queryIdx + 1).append(" failed, trying next...\n\n");
                } else {
                    debugInfo.append("HTTP Error: ").append(res.statusCode()).append("\n\n");
                }
            }
            
            // If we found a current trace, exit retry loop
            if (foundCurrentTrace) {
                debugInfo.append("✅ Found current execution trace, stopping retries.\n");
                break; // Exit retry loop
            }
            }
            
            // After all searches, analyze and attach the BEST trace found (if any)
            if (globalBestTrace != null) {
                debugInfo.append("\n✅ Using best trace found (score: ").append(globalBestScore)
                           .append(", timeDiff: ").append(globalBestDiff / 1000000.0).append("s)\n");
                
                String traceTable = generateTraceTable(globalBestTrace);
                String traceSummary = generateTraceSummary(globalBestTrace);
                TraceErrorAnalyzer.ErrorAnalysisResult errorAnalysis = TraceErrorAnalyzer.analyzeTrace(globalBestTrace);
                String errorReport = TraceErrorAnalyzer.generateErrorReport(errorAnalysis);
                
                if (errorAnalysis.hasErrors()) {
                    // Trace contains technical errors (error=true tags)
                    analyzeAndRecordParameterErrors(globalBestTrace, stepParameters);
                    String intelligentAnalysis = TraceErrorAnalyzer.generateIntelligentAnalysis(errorAnalysis, globalBestTrace);
                    if (intelligentAnalysis != null && !intelligentAnalysis.trim().isEmpty()) {
                        Allure.addAttachment("🤖 INTELLIGENT ANALYSIS (Based on Trace)", "text/plain", intelligentAnalysis);
                    }
                    Allure.addAttachment("🔗 API Call Trace", "text/plain", traceTable);
                } else if (isStepFailed) {
                    // Test failed but trace has no technical errors - likely business logic validation failure
                    StringBuilder analysisMsg = new StringBuilder();
                    analysisMsg.append("═══════════════════════════════════════════════════════════════════════\n");
                    analysisMsg.append("🔎 ROOT CAUSE (grounded in this test's actual response + trace)\n");
                    analysisMsg.append("═══════════════════════════════════════════════════════════════════════\n\n");
                    analysisMsg.append("The trace shows NO swallowed downstream server error (no http>=500 / otel=ERROR\n");
                    analysisMsg.append("span). So this is a CLIENT-FACING failure — the endpoint itself rejected the\n");
                    analysisMsg.append("request (status/validation), not a hidden backend fault.\n\n");
                    Integer __rcaStatus = CLIENT_RESPONSE_STATUS.get();
                    analysisMsg.append("Actual client response");
                    if (__rcaStatus != null) analysisMsg.append(" — HTTP ").append(__rcaStatus);
                    analysisMsg.append(" (the concrete rejection reason):\n");
                    String __rcaBody = CLIENT_RESPONSE_BODY.get();
                    if (__rcaBody != null && !__rcaBody.isEmpty()) {
                        analysisMsg.append(__rcaBody.length() > 800 ? __rcaBody.substring(0, 800) + "…" : __rcaBody).append("\n");
                    } else {
                        analysisMsg.append("(no response body captured for this step)\n");
                    }
                    Allure.addAttachment("🔎 ROOT CAUSE (grounded)", "text/plain", analysisMsg.toString());
                    Allure.addAttachment("🔗 API Call Trace", "text/plain", traceTable);
                } else {
                    // Test succeeded and trace has no errors
                    Allure.addAttachment("🔗 API Call Trace", "text/plain", traceTable);
                }
                Allure.addAttachment("📊 Trace Summary", "text/plain", traceSummary);
                Allure.addAttachment("📈 Raw Trace Data", "application/json", globalBestTrace.toString());

                // Trace Shape Oracle (Phase 2.F contribution) — runs on the same trace
                // the TraceErrorAnalyzer block just processed. The verdict is attached
                // to the step as a JSON document AND recorded on a thread-local so the
                // surrounding test-method assertion path can consult it (used for the
                // negative-test FAIL->PASS flip on ResponseEnvelope violations).
                if (oracle != null) {
                    try {
                        String rootApiKey = method + " " + path;
                        TraceModel model = TraceShapeAdapter.toModel(globalBestTrace, rootApiKey);
                        // Phase 4.x: inject the live client response body into the model's root
                        // span(s) so ResponseEnvelopeInvariant can read it (Jaeger spans omit it).
                        String __clientBody = CLIENT_RESPONSE_BODY.get();
                        CLIENT_RESPONSE_BODY.remove();
                        Integer __clientStatus = CLIENT_RESPONSE_STATUS.get();
                        CLIENT_RESPONSE_STATUS.remove();
                        if (model != null) {
                            for (TraceModel.Span __rs : model.roots()) {
                                if (__rs.tags == null) continue;
                                if (__clientBody != null && !__clientBody.isEmpty()) __rs.tags.put("http.response.body", __clientBody);
                                if (__clientStatus != null) __rs.tags.put("mist.client.status", String.valueOf(__clientStatus));
                            }
                        }
                        // FIXES.md F1+F3: target-aware overload runs TargetAttributionInvariant
                        // when the per-test target context + ablation flag are both set, so
                        // attribution is embedded as one of the verdict outcomes and the
                        // report-rendering path collapses it uniformly with the other 4.
                        TraceShapeVerdict verdict = oracle.evaluate(model, rootApiKey, targetService, targetParam);
                        LAST_VERDICT.set(verdict);
                        StringBuilder verdictJson = new StringBuilder();
                        verdictJson.append("{\n  \"rootApiKey\": \"").append(rootApiKey.replace("\\", "\\\\").replace("\"", "\\\"")).append("\",\n");
                        verdictJson.append("  \"passed\": ").append(verdict.isPassed()).append(",\n");
                        verdictJson.append("  \"outcomes\": [\n");
                        java.util.List<TraceShapeVerdict.InvariantOutcome> outcomes = verdict.getOutcomes();
                        for (int oi = 0; oi < outcomes.size(); oi++) {
                            TraceShapeVerdict.InvariantOutcome o = outcomes.get(oi);
                            verdictJson.append("    { \"kind\": \"").append(o.kind).append("\",");
                            verdictJson.append(" \"passed\": ").append(o.passed).append(",");
                            verdictJson.append(" \"severity\": \"").append(o.severity).append("\",");
                            String esc = o.detail == null ? "" : o.detail.replace("\\", "\\\\").replace("\"", "\\\"");
                            verdictJson.append(" \"detail\": \"").append(esc).append("\" }");
                            if (oi < outcomes.size() - 1) verdictJson.append(",");
                            verdictJson.append("\n");
                        }
                        verdictJson.append("  ]\n}");
                        Allure.addAttachment("Trace Shape Oracle Verdict", "application/json", verdictJson.toString());
                        if (!verdict.isPassed()) {
                            for (TraceShapeVerdict.InvariantOutcome o : outcomes) {
                                if (!o.passed && o.severity == TraceShapeVerdict.Severity.ERROR) {
                                    Allure.step("❌ shape violation: " + o.kind + " " + o.detail);
                                }
                            }
                        }
                        if (outcomes != null) {
                            for (TraceShapeVerdict.InvariantOutcome o : outcomes) {
                                if (!o.passed && "HIDDEN_DOWNSTREAM_FAILURE".equals(o.kind)) {
                                    StringBuilder hd = new StringBuilder();
                                    hd.append("🕳️ HIDDEN DOWNSTREAM FAILURE  [severity=").append(o.severity).append("]\n");
                                    String __uiBase = JAEGER_BASE_URL.endsWith("/api") ? JAEGER_BASE_URL.substring(0, JAEGER_BASE_URL.length() - 4) : JAEGER_BASE_URL;
                                    hd.append("🔍 Open in Jaeger (visual call tree): ").append(__uiBase).append("/trace/").append(markerTraceId).append("\n\n");
                                    hd.append("WHAT THE CLIENT SAW:\n  ").append(rootApiKey).append("  →  HTTP 2xx ✅   (looks fine — status, schema, and body are all clean)\n\n");
                                    hd.append("WHAT ACTUALLY HAPPENED (only the trace shows it):\n");
                                    hd.append("  A downstream call failed and was SWALLOWED — the caller returned 2xx anyway.\n");
                                    hd.append("  Read as  caller ──▶ callee ;  the FAILURE is in the callee (the downstream service):\n    ").append(o.detail).append("\n\n");
                                    hd.append("WHY A SEPARATE FINDING (vs the '🔗 API Call Trace' below)?\n");
                                    hd.append("  The trace marks EVERY errored span ❌. This one is invisible to status/schema/body\n");
                                    hd.append("  checks because the client got a 200 — that is what makes it a HIDDEN failure\n");
                                    hd.append("  (severity ").append(o.severity).append(": it errored but never surfaced to the caller).\n");
                                    Allure.addAttachment("🕳️ HIDDEN DOWNSTREAM FAILURE — swallowed downstream error behind a 2xx", "text/plain", hd.toString());
                                    Allure.label("mist.anomaly", "HIDDEN_DOWNSTREAM_FAILURE");
                                    Allure.label("tag", "mist_hidden_downstream");
                                    Allure.parameter("🕳️ Hidden downstream failure", o.detail);
                                }
                            }
                        }
                        // Phase 0: credit ALL failing outcomes (any severity) to the
                        // tracker's anomaly map so the fault-detection report surfaces
                        // them as a separate bucket. Called per-step (attachJaegerTrace
                        // fires once per step), so a 5-step test records up to 5 sets of
                        // outcomes — the first-seen sample for any (oracle,endpoint,sig)
                        // tuple wins. markerTraceId is preferred over model.getTraceId()
                        // because the marker is the in-process request marker (W3C
                        // traceparent) — Jaeger may return a stale or wrong trace.
                        try {
                            io.mist.core.analysis.FaultDetectionTracker.getInstance()
                                .recordVerdict(verdict, rootApiKey, Flow_Scenario_1.class.getName(), testMethodName, markerTraceId);
                        } catch (Throwable anomalyEx) {
                            Allure.addAttachment("Oracle Anomaly Record Error", "text/plain", anomalyEx.toString());
                        }
                    } catch (Throwable tsoEx) {
                        Allure.addAttachment("Trace Shape Oracle Error", "text/plain", "Oracle evaluation failed: " + tsoEx.toString());
                    }
                }

                Allure.addAttachment("🔍 Query Debug Info", "text/plain", debugInfo.toString());
            } else {
                // No traces found with any query after all retries
                if (!foundCurrentTrace) {
                    debugInfo.append("❌ No current execution traces found after ").append(maxRetries).append(" retries\n");
                }
                debugInfo.append("❌ No traces found with any query strategy");
                Allure.addAttachment("🔍 Jaeger Query Debug (No Traces)", "text/plain", debugInfo.toString());
            }
            
        } catch (Exception e) {
            Allure.addAttachment("📈 Jaeger Trace Error", "text/plain", "Error: " + e.toString() + "\nService: " + service + "\nMethod: " + method + "\nPath: " + path);
        }
    }

    private static String generateTraceTable(JSONObject trace) {
        try {
            JSONArray spans = trace.getJSONArray("spans");
            JSONObject processes = trace.optJSONObject("processes");
            String traceId = trace.optString("traceID", "");
            
            StringBuilder table = new StringBuilder();
            table.append("----------------------------------------------------------------------------------------\n");
            table.append("                           🔗 MICROSERVICE API CALL TRACE                           \n");
            table.append("----------------------------------------------------------------------------------------\n");
            if (!traceId.isEmpty()) {
                table.append("Trace ID: ").append(traceId).append("\n");
                // Convert API endpoint to UI endpoint for browser link
                // Remove /api from end if present, keep everything else (including /jaeger/ui if present)
                String uiBase = JAEGER_BASE_URL;
                if (uiBase.endsWith("/api")) {
                    uiBase = uiBase.substring(0, uiBase.length() - 4);  // Remove "/api"
                }
                table.append("Jaeger UI: ").append(uiBase).append("/trace/").append(traceId).append("\n");
                table.append("────────────────────────────────────────────────────────────────────────────────────────\n");
            }
            
            // Build hierarchical API call structure
            Map<String, JSONObject> apiSpanById = new HashMap<>();
            Map<String, List<String>> apiChildren = new HashMap<>();
            Map<String, String> apiParentOf = new HashMap<>();
            
            // First pass: collect all API spans and build relationships
            for (int i = 0; i < spans.length(); i++) {
                JSONObject span = spans.getJSONObject(i);
                if (isApiCall(span)) {
                    String spanId = span.getString("spanID");
                    apiSpanById.put(spanId, span);
                    apiChildren.put(spanId, new ArrayList<>());
                }
            }
            
            // Second pass: establish parent-child relationships for API calls
            for (int i = 0; i < spans.length(); i++) {
                JSONObject span = spans.getJSONObject(i);
                if (isApiCall(span)) {
                    String spanId = span.getString("spanID");
                    String parentId = span.optString("parentSpanId", null);
                    
                    // Look for parent in references if not directly available
                    if (parentId == null || parentId.isEmpty()) {
                        JSONArray refs = span.optJSONArray("references");
                        if (refs != null) {
                            for (int r = 0; r < refs.length(); r++) {
                                JSONObject ref = refs.getJSONObject(r);
                                if ("CHILD_OF".equalsIgnoreCase(ref.optString("refType"))) {
                                    parentId = ref.optString("spanID");
                                    break;
                                }
                            }
                        }
                    }
                    
                    // Only connect if parent is also an API call
                    if (parentId != null && apiSpanById.containsKey(parentId)) {
                        apiChildren.get(parentId).add(spanId);
                        apiParentOf.put(spanId, parentId);
                    }
                }
            }
            
            // Find root API calls (those without API parents)
            List<String> rootApiCalls = new ArrayList<>();
            for (String apiId : apiSpanById.keySet()) {
                if (!apiParentOf.containsKey(apiId)) {
                    rootApiCalls.add(apiId);
                }
            }
            
            // Sort children by start time
            Comparator<String> byStartTime = (a, b) -> Long.compare(
                apiSpanById.get(a).optLong("startTime", 0L),
                apiSpanById.get(b).optLong("startTime", 0L)
            );
            for (List<String> children : apiChildren.values()) {
                children.sort(byStartTime);
            }
            rootApiCalls.sort(byStartTime);
            
            if (rootApiCalls.isEmpty()) {
                table.append("⚠️  No API calls found in this trace\n");
                table.append("   This might be an internal operation or framework activity\n");
            } else {
                table.append("🌐 API CALL HIERARCHY (Parent → Child Relationships)\n");
                table.append("────────────────────────────────────────────────────────────────────────────────────────\n");
                // Honest note for externally-driven traces: MIST's external caller is not
                // itself traced, so the entry service's inbound span is absent and its downstream
                // calls appear as separate top-level entries. Tell the user they are siblings
                // of ONE request, not independent calls (do NOT fabricate a synthetic parent).
                if (rootApiCalls.size() > 1) {
                    table.append("ℹ️  The external caller's own entry span is not captured, so this request's downstream\n");
                    table.append("    calls appear below as separate top-level entries — they are SIBLINGS OF ONE REQUEST,\n");
                    table.append("    not independent calls. Each line's callee is the service it called (see the ──▶ / ↳).\n");
                    table.append("────────────────────────────────────────────────────────────────────────────────────────\n");
                }
                
                // Render hierarchical tree
                AtomicInteger apiCounter = new AtomicInteger(1);
                for (String rootId : rootApiCalls) {
                    renderApiHierarchy(table, rootId, apiSpanById, apiChildren, processes, 0, apiCounter, "");
                }
            }
            
            table.append("████████████████████████████████████████████████████████████████████████████████████████\n");
            return table.toString();
        } catch (Exception e) {
            return "❌ Failed to generate trace table: " + e.getMessage();
        }
    }

    private static String generateTraceSummary(JSONObject trace) {
        try {
            JSONArray spans = trace.getJSONArray("spans");
            JSONObject processes = trace.optJSONObject("processes");
            String traceId = trace.optString("traceID", "");
            
            StringBuilder summary = new StringBuilder();
            summary.append("🎯 TRACE SUMMARY\n");
            summary.append("═══════════════\n\n");
            
            // Count API calls by status
            int totalApis = 0;
            int successApis = 0;
            int errorApis = 0;
            Set<String> services = new HashSet<>();
            long totalDuration = 0L;
            long minDuration = Long.MAX_VALUE;
            long maxDuration = 0L;
            
            for (int i = 0; i < spans.length(); i++) {
                JSONObject span = spans.getJSONObject(i);
                if (isApiCall(span)) {
                    String serviceName = getServiceName(span, processes);
                    
                    // Filter out gateway services to match the API Call Hierarchy display
                    // 'gateway'/'proxy' substring already catches istio-ingressgateway,
                    // ts-gateway-service, api-gateway, front-proxy, etc. — SUT-agnostic.
                    boolean isGateway = serviceName.toLowerCase().contains("gateway")
                                       || serviceName.toLowerCase().contains("proxy");
                    if (isGateway) {
                        continue; // Skip gateway services in statistics
                    }
                    
                    totalApis++;
                    String[] httpInfo = extractHttpInfo(span);
                    String status = httpInfo[2];
                    
                    if (status.startsWith("2")) successApis++;
                    else if (status.startsWith("4") || status.startsWith("5")) errorApis++;
                    
                    services.add(serviceName);
                    
                    long duration = span.optLong("duration", 0L);
                    if (duration > 0) {
                        totalDuration += duration;
                        minDuration = Math.min(minDuration, duration);
                        maxDuration = Math.max(maxDuration, duration);
                    }
                }
            }
            
            summary.append("📊 API Call Statistics:\n");
            summary.append("   Total API Calls: ").append(totalApis).append("\n");
            summary.append("   ✅ Successful: ").append(successApis).append("\n");
            summary.append("   ❌ Failed: ").append(errorApis).append("\n");
            summary.append("   🏢 Services Involved: ").append(services.size()).append("\n\n");
            
            if (totalApis > 0) {
                summary.append("⏱️ Performance Metrics:\n");
                summary.append("   Total Duration: ").append(formatDuration(totalDuration)).append("\n");
                summary.append("   Average Duration: ").append(formatDuration(totalDuration / totalApis)).append("\n");
                if (minDuration != Long.MAX_VALUE) {
                    summary.append("   Fastest Call: ").append(formatDuration(minDuration)).append("\n");
                    summary.append("   Slowest Call: ").append(formatDuration(maxDuration)).append("\n");
                }
                summary.append("\n");
            }
            
            summary.append("🏢 Services in Trace:\n");
            for (String service : services) {
                summary.append("   • ").append(service).append("\n");
            }
            
            return summary.toString();
        } catch (Exception e) {
            return "❌ Failed to generate trace summary: " + e.getMessage();
        }
    }

    private static boolean isApiCall(JSONObject span) {
        String opName = span.optString("operationName", "");
        JSONArray tags = span.optJSONArray("tags");
        
        boolean hasHttpMethod = false;
        boolean hasHttpUrl = false;
        String httpUrl = "";
        String httpMethod = "";
        
        if (tags != null) {
            for (int t = 0; t < tags.length(); t++) {
                JSONObject tag = tags.getJSONObject(t);
                String key = tag.optString("key");
                String value = tag.optString("value");
                
                if ("http.method".equals(key)) { hasHttpMethod = true; httpMethod = value; }
                if ("http.url".equals(key) || "http.target".equals(key)) { hasHttpUrl = true; httpUrl = value; }
            }
        }
        
        // Must be an actual API call
        if (hasHttpMethod && hasHttpUrl && (httpUrl.contains("/api/") || httpMethod.matches("GET|POST|PUT|DELETE|PATCH"))) {
            return true;
        }
        
        if (opName.contains("/api/") && (opName.contains("GET") || opName.contains("POST") || opName.contains("PUT") || opName.contains("DELETE") || opName.contains("PATCH"))) {
            return true;
        }
        
        return opName.matches("(GET|POST|PUT|DELETE|PATCH)\\s+/api/.*");
    }

    private static String getServiceName(JSONObject span, JSONObject processes) {
        String processId = span.optString("processID", "");
        if (processes != null && processes.has(processId)) {
            return processes.getJSONObject(processId).optString("serviceName", "unknown-service");
        }
        return "unknown-service";
    }

    private static String[] extractHttpInfo(JSONObject span) {
        String method = "";
        String endpoint = "";
        String status = "";
        String opName = span.optString("operationName", "");
        
        JSONArray tags = span.optJSONArray("tags");
        if (tags != null) {
            for (int t = 0; t < tags.length(); t++) {
                JSONObject tag = tags.getJSONObject(t);
                String key = tag.optString("key");
                String value = tag.optString("value");
                
                if ("http.method".equals(key)) method = value;
                else if ("http.url".equals(key) || "http.target".equals(key)) endpoint = value;
                else if ("http.status_code".equals(key)) status = value;
            }
        }
        
        // Extract from operation name if tags don't have the info
        if (method.isEmpty() && opName.matches("(GET|POST|PUT|DELETE|PATCH)\\s+.*")) {
            String[] parts = opName.split("\\s+", 2);
            if (parts.length == 2) {
                method = parts[0];
                endpoint = parts[1];
            }
        }
        
        if (endpoint.isEmpty()) endpoint = opName;
        if (status.isEmpty()) status = "?";
        
        return new String[]{method, endpoint, status};
    }

    private static boolean hasErrorTag(JSONObject span) {
        if (!span.has("tags")) return false;
        
        JSONArray tags = span.getJSONArray("tags");
        for (int i = 0; i < tags.length(); i++) {
            JSONObject tag = tags.getJSONObject(i);
            if ("error".equals(tag.optString("key")) && 
                "bool".equals(tag.optString("type")) && 
                tag.optBoolean("value", false)) {
                return true;
            }
        }
        return false;
    }

    private static String formatDuration(long durationMicros) {
        if (durationMicros < 1000) {
            return durationMicros + "μs";
        } else if (durationMicros < 1000000) {
            return String.format("%.1f", durationMicros / 1000.0) + "ms";
        } else {
            return String.format("%.2f", durationMicros / 1000000.0) + "s";
        }
    }

    private static void renderApiHierarchy(StringBuilder table, String spanId, Map<String, JSONObject> apiSpanById, Map<String, List<String>> apiChildren, JSONObject processes, int depth, AtomicInteger counter, String prefix) {
        JSONObject span = apiSpanById.get(spanId);
        if (span == null) return;
        
        // Extract API information
        String serviceName = getServiceName(span, processes);
        String[] httpInfo = extractHttpInfo(span);
        String method = httpInfo[0];
        String endpoint = httpInfo[1];
        String status = httpInfo[2];
        long duration = span.optLong("duration", 0L);
        
        // Filter out gateway services - only show actual business APIs
        boolean isGateway = serviceName.toLowerCase().contains("gateway")
                           || serviceName.toLowerCase().contains("proxy");
        
        // If this is a gateway, skip rendering but still process children
        if (isGateway) {
            // Render children recursively without showing this gateway span
            List<String> children = apiChildren.getOrDefault(spanId, Collections.emptyList());
            for (String childId : children) {
                renderApiHierarchy(table, childId, apiSpanById, apiChildren, processes, depth, counter, "");
            }
            return;
        }
        
        // Check for errors in this span
        boolean hasError = hasErrorTag(span);
        String durationStr = formatDuration(duration);
        
        // Use SINGLE status indicator - prioritize error detection over HTTP status
        String statusIcon;
        if (hasError) {
            statusIcon = "❌";  // Error detected in span
        } else if (status.startsWith("2")) {
            statusIcon = "✅";  // HTTP 2xx success
        } else if (status.startsWith("4") || status.startsWith("5")) {
            statusIcon = "❌";  // HTTP 4xx/5xx error
        } else {
            statusIcon = "❓";  // Unknown/other status
        }
        
        // Smart service name handling - keep essential info
        String displayService = serviceName;
        if (serviceName.startsWith("ts-")) {
            // Keep meaningful part: ts-admin-order-service -> admin-order
            displayService = serviceName.substring(3);
            if (displayService.endsWith("-service")) {
                displayService = displayService.substring(0, displayService.length() - 8);
            }
        }
        
        // Smart endpoint handling - preserve important path info
        String displayEndpoint = endpoint;
        if (endpoint.startsWith("http")) {
            try {
                // Extract path from full URL: http://host:port/path -> /path
                int pathStart = endpoint.indexOf("/", 8);
                if (pathStart > 0) {
                    displayEndpoint = endpoint.substring(pathStart);
                }
            } catch (Exception ignored) {
                // If parsing fails, use original
            }
        }
        
        // Compress common patterns but keep essential info
        if (displayEndpoint.contains("/api/v1/")) {
            displayEndpoint = displayEndpoint.replace("/api/v1/", "/v1/");
        }
        
        // Create compact tree line
        StringBuilder line = new StringBuilder();
        
        // Minimal indentation for space efficiency
        for (int i = 0; i < depth; i++) {
            line.append("  ");  // 2 spaces per level
        }
        
        // Add compact connector
        if (depth > 0) {
            line.append("├─");
        }
        
        // Build comprehensive format with full information
        int apiNumber = counter.getAndIncrement();
        
        // Main line: [#] Status Service[ ──▶ callee] Method HTTPStatus(time)
        // For an errored client/egress span, surface the CALLEE on the headline so the
        // direction is explicit: the failure is in the callee (downstream), not in the
        // caller service whose client span merely RECORDED the failed call.
        String __callee = "";
        if (statusIcon.equals("❌") && endpoint != null && endpoint.startsWith("http")) {
            int __h = endpoint.indexOf("://");
            String __rest = __h >= 0 ? endpoint.substring(__h + 3) : endpoint;
            int __cut = __rest.length();
            int __ci = __rest.indexOf(':'); if (__ci >= 0 && __ci < __cut) __cut = __ci;
            int __si = __rest.indexOf('/'); if (__si >= 0 && __si < __cut) __cut = __si;
            if (__cut > 0) __callee = " ──▶ " + __rest.substring(0, __cut);
        }
        line.append(String.format("[%d] %s %s%s %s %s(%s)", 
            apiNumber, statusIcon, displayService, __callee, method, status, durationStr));
        table.append(line.toString()).append("\n");
        
        // Detail line: Full endpoint path (indented)
        StringBuilder detailLine = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            detailLine.append("  ");
        }
        if (depth > 0) {
            detailLine.append("   ");  // Extra indent for detail line
        } else {
            detailLine.append("    ");  // Indent for root detail line
        }
        detailLine.append("↳ ").append(endpoint);
        table.append(detailLine.toString()).append("\n");
        
        // Render children recursively
        List<String> children = apiChildren.getOrDefault(spanId, Collections.emptyList());
        for (String childId : children) {
            renderApiHierarchy(table, childId, apiSpanById, apiChildren, processes, depth + 1, counter, "");
        }
    }

    private static void analyzeAndRecordParameterErrors(JSONObject trace, Map<String, String> stepParameters) {
        try {
            // Get LLM properties from system properties
            Map<String, String> llmProperties = new HashMap<>();
            llmProperties.put("llm.enabled", System.getProperty("llm.enabled", "true"));
            llmProperties.put("llm.model.type", System.getProperty("llm.model.type", "ollama"));
            llmProperties.put("llm.ollama.enabled", System.getProperty("llm.ollama.enabled", "true"));
            llmProperties.put("llm.ollama.url", System.getProperty("llm.ollama.url", "http://localhost:11434"));
            llmProperties.put("llm.ollama.model", System.getProperty("llm.ollama.model", "gemma3:4b"));
            llmProperties.put("llm.gemini.enabled", System.getProperty("llm.gemini.enabled", "false"));
            llmProperties.put("llm.gemini.api.key", System.getProperty("llm.gemini.api.key", ""));
            llmProperties.put("llm.gemini.model", System.getProperty("llm.gemini.model", "gemini-2.0-flash-exp"));
            llmProperties.put("llm.gemini.api.url", System.getProperty("llm.gemini.api.url", "https://generativelanguage.googleapis.com/v1beta/models"));
            
            // Analyze parameter errors
            io.mist.core.smart.ParameterErrorAnalyzer.ParameterErrorAnalysisResult result = 
                io.mist.core.smart.ParameterErrorAnalyzer.analyzeParameterErrors(trace, stepParameters, llmProperties);
            
            if (result.hasParameterErrors()) {
                System.out.println("🔍 Parameter Error Analysis: Found " + result.getIdentifiedErrors().size() + " parameter-related errors");
                
                // Load and update the input fetch registry
                String registryPath = System.getProperty("smart.input.fetch.registry.path");
                if (registryPath != null && !registryPath.isEmpty()) {
                    try {
                        java.io.File registryFile = new java.io.File(registryPath);
                        io.mist.core.smart.InputFetchRegistry registry;
                        
                        // JVM-wide lock on the registry class serialises load+mutate+save across
                        // parallel test threads. Audit (#21) flagged: only saveToFile() is
                        // synchronized today, but each thread instantiates its own registry via
                        // loadFromFile, so the per-instance lock provides no cross-thread guard.
                        // Without this monitor, two threads racing on load/mutate/save would
                        // produce a lost-update where the second writer overwrites the first's
                        // contribution. Performance cost: the registry write path is failure-only
                        // and the critical section is small relative to per-scenario cost.
                        synchronized (io.mist.core.smart.InputFetchRegistry.class) {
                        if (registryFile.exists()) {
                            registry = io.mist.core.smart.InputFetchRegistry.loadFromFile(registryFile);
                        } else {
                            registry = new io.mist.core.smart.InputFetchRegistry();
                        }
                        
                        // Record each parameter error; skip YAML flush if nothing changed (dedup).
                        boolean registryChanged = false;
                        for (io.mist.core.smart.ParameterError error : result.getIdentifiedErrors()) {
                            if (registry.isAlreadyRegistered(error.getApiEndpoint(), error.getParameterName(), error)) {
                                continue;
                            }
                            registry.addParameterError(error.getApiEndpoint(), error.getParameterName(), error);
                            registryChanged = true;
                            System.out.println("📝 Recorded error: " + error.getParameterName() + " -> " + error.getErrorType() + ": " + error.getErrorReason());
                        }
                        
                        // Save updated registry only when at least one new entry was added.
                        if (registryChanged) {
                            registry.saveToFile(registryFile);
                            System.out.println("💾 Updated parameter error registry at: " + registryPath);
                        }
                        } // end synchronized(InputFetchRegistry.class)
                        
                    } catch (Exception e) {
                        System.err.println("⚠️ Failed to update parameter error registry: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    System.err.println("⚠️ Parameter error registry path not configured. Set 'smart.input.fetch.registry.path' property.");
                }
            } else {
                System.out.println("✅ No parameter-related errors detected in trace");
            }
            
        } catch (Exception e) {
            System.err.println("⚠️ Error during parameter error analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @BeforeClass
    public static void setupRestAssured() {
        RestAssured.baseURI = "http://localhost:8080";
        RestAssured.config = RestAssured.config()
            .httpClient(io.restassured.config.HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", 10000)
                .setParam("http.socket.timeout", 30000));
        // Configure Allure results directory
        System.setProperty("allure.results.directory", "target/allure-results");
        // ⚠️ AllureRestAssured filter disabled - causes duplicate request logging
        // We use manual, controlled attachments instead for cleaner reports
        // RestAssured.filters(new AllureRestAssured());
        // Initialize LLM validation singletons (ONCE per class, not per test)
        if (LLM_VALIDATION_ENABLED) {
            llmValidator = new io.mist.core.generation.ZeroShotLLMGenerator();
        }

        // Trace Shape Oracle bootstrap (Phase 2.F) — reads the persisted invariant store.
        // The file is created/refreshed by MistRunner's bootstrap on its cold-start pass.
        try {
            java.nio.file.Path tsoPath = java.nio.file.Paths.get(".mist\\trace-shape-invariants.json");
            oracle = new TraceShapeOracle(new ShapeInvariantStore(tsoPath));
        } catch (Throwable tsoEx) {
            System.err.println("[Trace Shape Oracle] init failed: " + tsoEx.getMessage());
            oracle = null;
        }
        // Phase 4.x: wire the LLM-backed envelope classifier so ResponseEnvelope can
        // classify+cache a previously-unseen 2xx body value (the paper's one-shot-LLM-
        // on-first-2xx behaviour). Reuses the existing validateResponse soft-error model.
        if (oracle != null && LLM_VALIDATION_ENABLED && llmValidator != null) {
            oracle.setEnvelopeClassifier((rootApi, field, value, body) -> {
                try {
                    int __sp = rootApi == null ? -1 : rootApi.indexOf(' ');
                    String __m = __sp > 0 ? rootApi.substring(0, __sp) : "GET";
                    String __p = __sp > 0 ? rootApi.substring(__sp + 1) : (rootApi == null ? "" : rootApi);
                    io.mist.core.generation.ZeroShotLLMGenerator.ValidationResult __vr =
                        llmValidator.validateResponse(200, body, "", __m, __p);
                    return __vr == null ? null : Boolean.valueOf(__vr.isFailed());
                } catch (Throwable __t) { return null; }
            });
        }
    }

    @Test
    public void test_positive_flow_S1_v1() throws Exception {
        // Record test execution for fault detection tracking
        io.mist.core.analysis.FaultDetectionTracker.getInstance()
            .recordTestCase(this.getClass().getName(), "test_positive_flow_S1_v1");

        // Phase 2: trace-attribution target (negative tests only).
        final String __targetService = null;
        final String __targetParam = null;


        // Allure Suite Taxonomy: Service -> <JUnit4 auto-suite from @DisplayName> -> Variant
        // IMPORTANT: we do NOT emit an explicit 'suite' label here.  The AllureJunit4 listener
        // already auto-adds one derived from the class's @DisplayName (e.g.
        // 'Flow_Scenario_2386 | POST /api/v1/...').  Emitting another 'suite' label would
        // be additive (Allure does not override on duplicate label names) and the same
        // tests would appear TWICE in the Suites view under two different suite folders.
        Allure.label("parentSuite", "productpage.default");
        Allure.label("subSuite", "[Valid] v1");

        // Per-test auth override (AuthManipulationStrategy). null = use default.
        final String  __mstOverrideToken = null;
        final boolean __mstDisableAuth   = false;
        final java.util.concurrent.atomic.AtomicBoolean loginSucceeded  = new java.util.concurrent.atomic.AtomicBoolean(true);
        final java.util.concurrent.atomic.AtomicBoolean scenarioFailed = new java.util.concurrent.atomic.AtomicBoolean(false);
        // 🔐 STEP 0: Authentication (delegates to MstAuthHandler)
        Allure.step("🔐 Step 0: Authentication", () -> {
            try {
                io.mist.cli.auth.MstAuthHandler.Mode __mstMode = io.mist.cli.auth.MstAuthHandler.getMode();
                Allure.parameter("🔐 Auth Mode", __mstMode.name());
                if (__mstMode == io.mist.cli.auth.MstAuthHandler.Mode.NONE) {
                    Allure.parameter("🎯 Result", "⏭️ SKIPPED (auth.mode=none)");
                } else {
                    boolean __mstReady = io.mist.cli.auth.MstAuthHandler.ensureReady();
                    if (!__mstReady) loginSucceeded.set(false);
                    Allure.parameter("🎯 Result", __mstReady ? "✅ READY" : "❌ FAILED");
                }
            } catch (Throwable __mstAuthErr) {
                loginSucceeded.set(false);
                Allure.parameter("🎯 Result", "❌ FAILED (" + __mstAuthErr.getClass().getSimpleName() + ")");
                Allure.addAttachment("💥 Auth Error", "text/plain", String.valueOf(__mstAuthErr.getMessage()));
            }
        });

        // Step execution results tracking
        final java.util.Map<Integer, Boolean> stepResults = new java.util.HashMap<>();
        final java.util.Map<Integer, String> capturedOutputs = new java.util.HashMap<>();
        // Parameter tracking for error analysis
        final java.util.Map<String, String> allStepParameters = new java.util.HashMap<>();

        final java.util.Map<String, String> stepParams1 = new java.util.HashMap<>();
        // Root 1: productpage.default GET /productpage [expect 200]
        io.mist.core.enhancer.TestResultCapture.setStepMetadata(
            1, "/productpage", "GET", 
            "productpage.default", false);

        // 🔥 ALWAYS create Allure step - execution decision happens INSIDE
        { // per-step scope block — prevents variable redeclaration across steps
        
        long __interScenarioDelayMs;
        try {
            __interScenarioDelayMs = Long.parseLong(
                System.getProperty("mst.test.inter.scenario.delay.ms", "0"));
        } catch (NumberFormatException nfe) {
            __interScenarioDelayMs = 0L;
        }
        if (__interScenarioDelayMs > 0) {
            try {
                Thread.sleep(__interScenarioDelayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        
        final long requestStartMicros = System.currentTimeMillis() * 1000L;
        try {
            Allure.step("Root 1: productpage.default GET /productpage [expect 200]", () -> {
                Allure.parameter("🏢 Service", "productpage.default");
                Allure.parameter("📡 HTTP Method", "GET");
                Allure.parameter("🔗 Endpoint", "/productpage");
                Allure.parameter("🎯 Expected Status", 200);
                Allure.parameter("🔗 Dependency Type", "INDEPENDENT (can execute regardless of other step results)");
                Allure.parameter("📌 Step ID", "R1");
                Allure.description("🎯 **Testing**: productpage.default\n" +
                                 "📡 **Method**: GET\n" +
                                 "🔗 **Path**: /productpage\n" +
                                 "🎯 **Expected**: 200\n" +
                                 "🔗 **Dependencies**: INDEPENDENT (can execute regardless of other step results)");
                
                // Execution decision analysis - determine if step should execute
                boolean shouldSkip = false;
                String skipReason = "";
                String skipCategory = "";
                
                // Check authentication dependency
                if (!loginSucceeded.get()) {
                    shouldSkip = true;
                    skipReason = "Authentication failed - cannot proceed with authenticated API calls";
                    skipCategory = "🔐 AUTH_FAILED";
                }
                
                // Add execution decision as parameter
                if (shouldSkip) {
                    Allure.parameter("🚦 Execution Decision", "⏭️ SKIP - " + skipCategory);
                    Allure.parameter("⏭️ Skip Reason", skipReason);
                } else {
                    Allure.parameter("🚦 Execution Decision", "▶️ EXECUTE - All dependencies satisfied");
                }
                
                if (!shouldSkip) {
                    System.out.println("▶️ EXECUTING: Root 1: productpage.default GET /productpage [expect 200] (dependency analysis passed)");
                    Response stepResponse1 = null;
                    String __mstTraceId1 = java.util.UUID.randomUUID().toString().replace("-", "");
                    String __mstSpanId1 = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                    boolean __traceAttached1 = false;
                    
                    try {
                        RequestSpecification req = RestAssured.given().urlEncodingEnabled(false);
                        req = io.mist.cli.auth.MstAuthHandler.applyAuth(req, "/productpage", __mstOverrideToken, __mstDisableAuth);
                        if (__mstOverrideToken == null && !__mstDisableAuth) {
                            req = req.filter(io.mist.cli.auth.MstAuthRefreshFilter.INSTANCE);
                        }
                        req = req.header("traceparent", "00-" + __mstTraceId1 + "-" + __mstSpanId1 + "-01");
                        // 🔥 FIX: Extract response FIRST (before status code assertion) to capture response body in all cases
                        stepResponse1 = req.when().get("/productpage")
                               .then().log().ifValidationFails()
                               .extract().response();
                        
                        // Now validate status code based on expected status from configuration
                        int actualStatusCode1 = stepResponse1.getStatusCode();
                        int expectedStatusCode1 = 200;
                        
                        // Fix 3 Layer 3: output-coverage backstop
                        String responseFingerprint1 = responseFingerprint(actualStatusCode1, stepResponse1.getBody().asString());
                        boolean isDuplicateResponse1 = !SEEN_RESPONSE_HASHES.add(responseFingerprint1);
                        if (isDuplicateResponse1) {
                            System.out.println("⏭️ Duplicate response observed (hash=" + responseFingerprint1.substring(0, 12) + "…), skipping LLM validation for step 1");
                        }
                        
                        // 🤖 LLM RESPONSE VALIDATION: uses class-level singletons
                        
                        // ✅ POSITIVE TEST VALIDATION
                        // Positive test: PASS only if status == expected AND response body indicates success
                        if (actualStatusCode1 != expectedStatusCode1) {
                            throw new AssertionError("Expected status code " + expectedStatusCode1 + ", but got: " + actualStatusCode1);
                        }
                        
                        // LLM validation for positive tests - detect soft errors in 2XX responses
                        if (LLM_VALIDATION_ENABLED && llmValidator != null && !isDuplicateResponse1 && (!LLM_ONLY_2XX || (actualStatusCode1 >= 200 && actualStatusCode1 < 300))) {
                            try {
                                String validationBody = stepResponse1.getBody().asString();
                                
                                // Use class-level singleton (created once in @BeforeClass)
                                io.mist.core.generation.ZeroShotLLMGenerator.ValidationResult validationResult =
                                    llmValidator.validateResponse(
                                        actualStatusCode1,
                                        validationBody,
                                        "productpage.default",
                                        "GET",
                                        "/productpage"
                                    );
                                
                                System.out.println("🤖 LLM Validation (Positive Test): Failed=" + validationResult.isFailed() + ", RCA: " + validationResult.getRca());
                                
                                // Attach LLM analysis to Allure report
                                if (LLM_INCLUDE_RCA) {
                                    StringBuilder llmReport = new StringBuilder();
                                    llmReport.append("════════════════════════════════════════════════════════════════════════\n");
                                    llmReport.append("🤖 INTELLIGENT ANALYSIS (Positive Test)\n");
                                    llmReport.append("════════════════════════════════════════════════════════════════════════\n\n");
                                    llmReport.append("Test Type: ✅ POSITIVE\n");
                                    llmReport.append("Analysis Result: ").append(validationResult.isFailed() ? "❌ SOFT ERROR DETECTED" : "✅ VALID SUCCESS").append("\n\n");
                                    llmReport.append("Root Cause Analysis:\n");
                                    llmReport.append(validationResult.getRca()).append("\n");
                                    Allure.addAttachment("🤖 INTELLIGENT ANALYSIS (Positive Test)", "text/plain", llmReport.toString());
                                }
                                
                                // Positive test logic:
                                // - If LLM says FAILED: FAIL (we expected success but got soft error)
                                // - If LLM says SUCCESS: PASS (as expected)
                                if (validationResult.isFailed()) {
                                    throw new AssertionError("Positive test failed: Expected success but LLM detected soft error. RCA: " + validationResult.getRca());
                                }
                                System.out.println("✅ Positive test PASSED: LLM validated response as successful");
                                
                            } catch (AssertionError ae) {
                                throw ae; // Re-throw assertion errors
                            } catch (Exception llmEx) {
                                System.err.println("⚠️ LLM validation failed: " + llmEx.getMessage());
                                // Don't fail the test due to LLM validation errors
                            }
                        }
                        
                        // 🔍 FAULT DETECTION: Check if root API response contains injected fault
                        try {
                            String faultCheckBody = stepResponse1.getBody().asString();
                            org.json.JSONObject faultJson = new org.json.JSONObject(faultCheckBody);
                            if (faultJson.has("data")) {
                                Object dataValue = faultJson.get("data");
                                if (dataValue instanceof org.json.JSONObject) {
                                    org.json.JSONObject dataObj = (org.json.JSONObject) dataValue;
                                    if (dataObj.optBoolean("injected", false)) {
                                        String detectedFaultName = dataObj.optString("faultName", "");
                                        if (!detectedFaultName.isEmpty()) {
                                            io.mist.core.analysis.FaultDetectionTracker.getInstance().recordDetectedFault(
                                                detectedFaultName,
                                                this.getClass().getName(),
                                                "test_positive_flow_S1_v1",
                                                System.currentTimeMillis(),
                                                faultCheckBody
                                            );
                                            System.out.println("🔍 FAULT DETECTED: " + detectedFaultName + " in test: test_positive_flow_S1_v1");
                                        }
                                    }
                                }
                            }
                        } catch (Exception faultEx) {
                            // Silent fail - don't break test execution
                        }
                        
                        stepResults.put(1, true);
                        System.out.println("✅ Root 1: productpage.default GET /productpage [expect 200] - SUCCESS");
                        try {
                            String fullBody1 = stepResponse1.getBody().asString();
                            if (fullBody1 != null && !fullBody1.isEmpty()) {
                                capturedOutputs.put(1, fullBody1);
                            }
                        } catch (Exception captureEx) {
                            System.err.println("[Capture] Failed to store response body for step 1: " + captureEx.getMessage());
                        }
                        
                        // ✅ SUCCESS: Clean success reporting without duplication
                        try {
                            String responseBody = stepResponse1.getBody().asString();
                            int actualStatus = stepResponse1.getStatusCode();
                            long responseTime = stepResponse1.getTime();
                            
                            // 🔧 Test Case Enhancer: Capture response for enhancement (step-aware)
                            io.mist.core.enhancer.TestResultCapture.captureStepResponse(1, actualStatus, responseBody);
                            
                            // Phase 1: record each parameter's value as VERIFIED_VALID (SUT
                            // accepted it on this 2xx step). recordParameterSuccess gates
                            // on captureEnabled + isNegativeTest internally so we emit
                            // unconditionally here. Endpoint is the OpenAPI TEMPLATE path
                            // (with {paramName} markers) so it matches what MistGenerator's
                            // preferVerifiedValues reads via the registry. Using the
                            // resolved path would key the registry on concrete IDs that
                            // generation-time reads can never match.
                            // Iterate the step-scoped map (declared at the top of this
                            // step's emission), NOT allStepParameters which is cumulative
                            // across all steps in this test method.
                            String __ep1 = "GET /productpage";
                            for (java.util.Map.Entry<String, String> __pe : stepParams1.entrySet()) {
                                io.mist.core.enhancer.TestResultCapture.recordParameterSuccess(
                                    __ep1, __pe.getKey(), __pe.getValue(), actualStatus);
                            }
                            
                            // Single success status parameter
                            Allure.parameter("🎯 Result", "✅ SUCCESS (" + actualStatus + " in " + responseTime + "ms)");
                            
                            // Add response as attachment (AllureRestAssured filter disabled to avoid duplication)
                            Allure.addAttachment("📥 Response (" + actualStatus + ")", "application/json", responseBody);
                            long __jaegerPropagationDelayMs;
                            try {
                                __jaegerPropagationDelayMs = Long.parseLong(
                                    System.getProperty("mst.test.jaeger.propagation.delay.ms", "0"));
                            } catch (NumberFormatException nfe) { __jaegerPropagationDelayMs = 0L; }
                            if (__jaegerPropagationDelayMs > 0) {
                                try { Thread.sleep(__jaegerPropagationDelayMs); }
                                catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                            }
                            CLIENT_RESPONSE_BODY.set(responseBody);
                            CLIENT_RESPONSE_STATUS.set(actualStatus);
                            attachJaegerTrace("productpage.default", "GET", "/productpage", requestStartMicros, allStepParameters, false, __mstTraceId1, "test_positive_flow_S1_v1", __targetService, __targetParam);
                            __traceAttached1 = true;
                        } catch (Exception e) {
                            Allure.parameter("🎯 Result", "✅ SUCCESS (response capture failed)");
                        }
                        // Phase 2.F: Trace Shape Oracle — positive variant FAILS when verdict
                        // reports any ERROR-severity violation (verdict was attached above).
                        {
                            TraceShapeVerdict tsoPosVerdict = LAST_VERDICT.get();
                            if (tsoPosVerdict != null && !tsoPosVerdict.isPassed()) {
                                StringBuilder vmsg = new StringBuilder("Positive variant failed — Trace Shape Oracle verdict has violation(s):");
                                for (TraceShapeVerdict.InvariantOutcome o : tsoPosVerdict.getOutcomes()) {
                                    if (!o.passed && o.severity == TraceShapeVerdict.Severity.ERROR) {
                                        vmsg.append(" [").append(o.kind).append(": ").append(o.detail).append("]");
                                    }
                                }
                                throw new AssertionError(vmsg.toString());
                            }
                        }
                    } catch (Throwable t) {
                        stepResults.put(1, false);
                        System.out.println("❌ Root 1: productpage.default GET /productpage [expect 200] - FAILED: " + t.getMessage());
                        
                        // 🔥 CRITICAL: Capture response body for failed requests (response was extracted before assertion)
                        String failedResponseBody = null;
                        int failedStatusCode = -1;
                        long failedResponseTime = -1;
                        try {
                            if (stepResponse1 != null) {
                                failedResponseBody = stepResponse1.getBody().asString();
                                failedStatusCode = stepResponse1.getStatusCode();
                                failedResponseTime = stepResponse1.getTime();
                                // 🔧 Test Case Enhancer: Capture response for enhancement (step-aware)
                                io.mist.core.enhancer.TestResultCapture.captureStepResponse(1, failedStatusCode, failedResponseBody);
                            }
                        } catch (Exception respEx) {
                            failedResponseBody = "Unable to capture response: " + respEx.getMessage();
                        }
                        
                        // 🔍 FAULT DETECTION (FAILURE PATH): Check if failed response contains injected fault
                        try {
                            if (failedResponseBody != null && !failedResponseBody.startsWith("Unable to capture")) {
                                org.json.JSONObject faultJson = new org.json.JSONObject(failedResponseBody);
                                if (faultJson.has("data")) {
                                    Object dataValue = faultJson.get("data");
                                    if (dataValue instanceof org.json.JSONObject) {
                                        org.json.JSONObject dataObj = (org.json.JSONObject) dataValue;
                                        if (dataObj.optBoolean("injected", false)) {
                                            String detectedFaultName = dataObj.optString("faultName", "");
                                            if (!detectedFaultName.isEmpty()) {
                                                io.mist.core.analysis.FaultDetectionTracker.getInstance().recordDetectedFault(
                                                    detectedFaultName,
                                                    this.getClass().getName(),
                                                    "test_positive_flow_S1_v1",
                                                    System.currentTimeMillis(),
                                                    failedResponseBody
                                                );
                                                System.out.println("🔍 FAULT DETECTED (in failed test): " + detectedFaultName + " in test: test_positive_flow_S1_v1");
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception faultEx) {
                            // Silent fail - don't break error handling
                        }
                        
                        // ❌ FAILURE: Enhanced failure reporting with detailed analysis
                        String errorType = t.getClass().getSimpleName();
                        String failureReason = "";
                        if (t instanceof java.net.ConnectException) {
                            errorType = "Connection Failed";
                            failureReason = "Service unreachable - Connection refused";
                        } else if (t instanceof AssertionError) {
                            errorType = "Status Code Mismatch";
                            failureReason = "Expected vs actual status code mismatch";
                        } else if (t instanceof java.net.SocketTimeoutException) {
                            errorType = "Request Timeout";
                            failureReason = "Service response timeout";
                        } else if (t.getMessage() != null && t.getMessage().contains("404")) {
                            errorType = "Not Found (404)";
                            failureReason = "API endpoint not found";
                        } else if (t.getMessage() != null && t.getMessage().contains("500")) {
                            errorType = "Internal Server Error (500)";
                            failureReason = "Service internal error";
                        } else {
                            failureReason = "Unexpected error: " + errorType;
                        }
                        
                        // Enhanced failure parameters
                        String resultMsg = "❌ FAILED (" + errorType;
                        if (failedStatusCode >= 0) {
                            resultMsg += ", status: " + failedStatusCode;
                        }
                        if (failedResponseTime >= 0) {
                            resultMsg += ", " + failedResponseTime + "ms";
                        }
                        resultMsg += ")";
                        Allure.parameter("🎯 Result", resultMsg);
                        Allure.parameter("🔍 Failure Reason", failureReason);
                        Allure.parameter("🏢 Failed Service", "productpage.default");
                        Allure.parameter("📡 Failed Method", "GET");
                        Allure.parameter("🔗 Failed Endpoint", "/productpage");
                        
                        // 🔥 CRITICAL: Attach response body for failed requests
                        if (!__traceAttached1 && failedResponseBody != null) {
                            String responseTitle = "📥 Response (" + failedStatusCode + ")";
                            Allure.addAttachment(responseTitle, "application/json", failedResponseBody);
                        }
                        
                        // Comprehensive error details
                        StringBuilder errorDetails = new StringBuilder();
                        errorDetails.append("❌ STEP FAILURE ANALYSIS\n");
                        errorDetails.append("=====================================\n\n");
                        errorDetails.append("📋 Step: Root 1: productpage.default GET /productpage [expect 200]\n");
                        errorDetails.append("🏢 Service: productpage.default\n");
                        errorDetails.append("📡 Method: GET\n");
                        errorDetails.append("🔗 Endpoint: /productpage\n");
                        errorDetails.append("💥 Error Type: ").append(errorType).append("\n");
                        errorDetails.append("🔍 Reason: ").append(failureReason).append("\n\n");
                        errorDetails.append("📜 Full Error Message:\n");
                        errorDetails.append(t.getMessage() != null ? t.getMessage() : "No message").append("\n\n");
                        if (t.getCause() != null) {
                            errorDetails.append("🔗 Root Cause:\n");
                            errorDetails.append(t.getCause().toString()).append("\n\n");
                        }
                        errorDetails.append("🔧 Troubleshooting Tips:\n");
                        if (errorType.contains("Connection Failed")) {
                            errorDetails.append("• Check if the service is running\n• Verify network connectivity\n• Check firewall settings\n");
                        } else if (errorType.contains("Timeout")) {
                            errorDetails.append("• Service may be overloaded\n• Increase timeout settings\n• Check service health\n");
                        } else if (errorType.contains("404")) {
                            errorDetails.append("• Verify API endpoint exists\n• Check service deployment\n• Review API documentation\n");
                        } else if (errorType.contains("500")) {
                            errorDetails.append("• Check service logs\n• Verify service configuration\n• Review dependencies\n");
                        } else {
                            errorDetails.append("• Review full error message\n• Check service status\n• Verify request parameters\n");
                        }
                        
                        long __jaegerPropagationDelayMs;
                        try {
                            __jaegerPropagationDelayMs = Long.parseLong(
                                System.getProperty("mst.test.jaeger.propagation.delay.ms", "0"));
                        } catch (NumberFormatException nfe) { __jaegerPropagationDelayMs = 0L; }
                        if (__jaegerPropagationDelayMs > 0) {
                            try { Thread.sleep(__jaegerPropagationDelayMs); }
                            catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        }
                        // Skip when the success path already emitted this step's trace block
                        // (positive variant failed via the Phase 2.F oracle throw) — prevents
                        // duplicating every attachment + shape-violation step in the report.
                        if (!__traceAttached1) {
                            CLIENT_RESPONSE_BODY.set(failedResponseBody);
                            if (failedStatusCode > 0) CLIENT_RESPONSE_STATUS.set(failedStatusCode);
                            attachJaegerTrace("productpage.default", "GET", "/productpage", requestStartMicros, allStepParameters, true, __mstTraceId1, "test_positive_flow_S1_v1", __targetService, __targetParam);
                        }
                        
                        // 🔥 CRITICAL: Throw exception to mark step as FAILED (red arrow) in Allure
                        throw new RuntimeException("Root 1: productpage.default GET /productpage [expect 200] failed: " + failureReason + " (" + errorType + ")", t);
                    }
                } else {
                    // ⏭️ SKIP: Clean skip reporting with proper Allure status
                    System.out.println("⏭️ SKIPPING: Root 1: productpage.default GET /productpage [expect 200] - " + skipReason);
                    stepResults.put(1, false);
                    
                    // Single skip status parameter
                    Allure.parameter("🎯 Result", "⏭️ SKIPPED (" + skipCategory.replaceAll("🔐 |📊 |🔄 ", "") + ")");
                    
                    // Single skip reason attachment
                    Allure.addAttachment("⏭️ Skip Details", "text/plain", "Reason: " + skipReason);
                    
                    // 🔥 CRITICAL: Throw Assumption exception to mark step as SKIPPED (yellow arrow) in Allure
                    throw new org.junit.AssumptionViolatedException("Step skipped: " + skipReason);
                }
            });
        } catch (Exception stepException) {
            // Step wrapper exception handling - maintain execution flow
            if (stepException instanceof RuntimeException && stepException.getMessage().startsWith("Step failed:")) {
                // This is a failed step - already handled, just continue
                System.out.println("Step 1 marked as FAILED in Allure");
            } else if (stepException instanceof org.junit.AssumptionViolatedException) {
                // This is a skipped step - already handled, just continue
                System.out.println("Step 1 marked as SKIPPED in Allure");
            } else {
                // Unexpected wrapper failure
                System.out.println("⚠️ Step wrapper failed for Root 1: productpage.default GET /productpage [expect 200]: " + stepException.getMessage());
                stepResults.put(1, false);
            }
        }
        } // end per-step scope block

        // Evaluate scenario result with comprehensive reporting
        long successfulSteps = stepResults.values().stream().filter(result -> result).count();
        long failedSteps = stepResults.values().stream().filter(result -> !result).count();
        long totalSteps = stepResults.size();
        
        // Add clean test summary - no duplicate content
        String overallResult;
        String severity;
        if (!loginSucceeded.get()) {
            overallResult = "❌ AUTHENTICATION FAILED";
            severity = "critical";
        } else if (failedSteps == 0) {
            overallResult = "✅ ALL STEPS PASSED";
            severity = "normal";
        } else if (successfulSteps > 0) {
            overallResult = "⚠️ PARTIAL FAILURE";
            severity = "major";
        } else {
            overallResult = "❌ ALL STEPS FAILED";
            severity = "critical";
        }
        
        // If the trace oracle found a hidden-downstream failure on the (passing) request,
        // carry it INTO the scenario result so a green test is not read as a clean success.
        // WARN does not fail the test by design (a swallowed otel-error may be tolerated, e.g.
        // non-critical ads), but the headline must show it. Gate with --fail-on=warn to make red.
        String hdWarn = "";
        TraceShapeVerdict __scenVerdict = LAST_VERDICT.get();
        LAST_VERDICT.remove();  // avoid a stale verdict leaking into the next scenario
        if (__scenVerdict != null) {
            int __hdCount = 0;
            for (TraceShapeVerdict.InvariantOutcome __o : __scenVerdict.getOutcomes()) {
                if (!__o.passed && "HIDDEN_DOWNSTREAM_FAILURE".equals(__o.kind)) __hdCount++;
            }
            if (__hdCount > 0) hdWarn = "  ⚠️ " + __hdCount + " HIDDEN DOWNSTREAM FAILURE (passed, but a 2xx hid a swallowed downstream error)";
        }
        
        // Single summary parameter with all key info
        Allure.parameter("📊 Scenario Result", overallResult + " (" + successfulSteps + "/" + totalSteps + " steps)" + hdWarn);
        
        // Add clean categorization
        // NOTE: do NOT emit an extra 'feature' label here — it conflicts with class-level
        // @Feature(serviceName) and Allure treats duplicate label names as additive,
        // producing phantom groupings like "Microservice Workflow" in the Behaviors view.
        Allure.label("severity", severity);
        Allure.description("Microservice test scenario with " + totalSteps + " steps.");
        
        System.out.println("=== SCENARIO RESULT ===");
        System.out.println("Scenario: test_positive_flow_S1_v1");
        System.out.println("Total Steps: " + totalSteps);
        System.out.println("Successful: " + successfulSteps);
        System.out.println("Failed: " + failedSteps);
        System.out.println("Login Status: " + (loginSucceeded.get() ? "SUCCESS" : "FAILED"));
        
        // IMPROVED: Test fails if ANY step fails or login fails (not just when ALL fail)
        if (!loginSucceeded.get()) {
            fail("Scenario FAILED: Authentication failed - cannot proceed with API calls");
        } else if (failedSteps > 0) {
            fail("Scenario FAILED: " + failedSteps + " out of " + totalSteps + " steps failed. " +
                 "In microservice testing, all workflow steps must succeed for end-to-end validation.");
        } else if (successfulSteps == 0) {
            fail("Scenario FAILED: No steps executed successfully - check service availability");
        } else {
            System.out.println("🎉 Scenario PASSED: All " + totalSteps + " steps completed successfully");
        }
    }

}
