package io.mist.core.analysis;

import io.mist.core.oracle.attribution.AttributionVerdict;
import io.mist.core.oracle.shape.TraceShapeVerdict;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe singleton tracker for monitoring injected fault detection during test execution.
 * Tracks which faults from injected-faults.json are detected by test cases and generates
 * comprehensive coverage reports.
 */
public class FaultDetectionTracker {
    
    private static final Logger logger = LogManager.getLogger(FaultDetectionTracker.class);
    private static final FaultDetectionTracker INSTANCE = new FaultDetectionTracker();
    
    // Thread-safe data structures
    private final Map<String, InjectedFault> injectedFaults = new ConcurrentHashMap<>();
    private final Map<String, List<FaultDetection>> detectedFaults = new ConcurrentHashMap<>();
    private final Set<String> allTestCases = ConcurrentHashMap.newKeySet();

    // Phase 0: TraceShapeOracle violations that fired but didn't match an
    // injected fault name. Keyed by (oracle, endpointSig, violationFingerprint)
    // so same-shape violations across many tests collapse into one entry with
    // its hitCount incremented. See recordOracleAnomaly / recordVerdict.
    private final Map<String, OracleAnomaly> oracleAnomalies = new ConcurrentHashMap<>();

    /**
     * Per-execution idempotency for anomaly recording. The writer's generated
     * code calls attachJaegerTrace twice for the same step when a positive
     * variant throws on an ERROR-severity verdict (success path records, the
     * AssertionError is caught, the catch path re-fetches and records the same
     * verdict again with the SAME marker traceId). The marker is a fresh UUID
     * per step execution, so enhancer-round re-executions produce new keys and
     * keep counting; only the intra-execution double record collapses.
     */
    private final Set<String> recordedAnomalyInstances = ConcurrentHashMap.newKeySet();
    
    // Metadata
    private String experimentName;
    private long trackingStartTime;
    private boolean initialized = false;
    
    private FaultDetectionTracker() {
        this.trackingStartTime = System.currentTimeMillis();
    }
    
    public static FaultDetectionTracker getInstance() {
        return INSTANCE;
    }
    
    /**
     * Load injected faults from the JSON registry file
     */
    public synchronized void loadInjectedFaults(String jsonPath) {
        try {
            logger.info("Loading injected faults from: {}", jsonPath);
            
            String content = new String(Files.readAllBytes(Paths.get(jsonPath)));
            JSONObject root = new JSONObject(content);
            
            if (!root.has("injected_faults")) {
                logger.error("JSON file does not contain 'injected_faults' array");
                return;
            }
            
            JSONArray faults = root.getJSONArray("injected_faults");
            injectedFaults.clear();
            
            for (int i = 0; i < faults.length(); i++) {
                JSONObject faultJson = faults.getJSONObject(i);
                InjectedFault fault = new InjectedFault(
                    faultJson.getString("faultName"),
                    faultJson.getString("service"),
                    faultJson.getString("api")
                );
                injectedFaults.put(fault.faultName, fault);
            }
            
            logger.info("Loaded {} injected faults from registry", injectedFaults.size());
            initialized = true;
            
        } catch (IOException e) {
            logger.error("Failed to load injected faults file: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to parse injected faults JSON: {}", e.getMessage(), e);
        }
    }

    /**
     * Initialize the tracker with NO named injected faults — the SUT-agnostic default when
     * no {@code fault.detection.injected.faults.path} is configured (a SUT that doesn't ship
     * a train-ticket-style injected-faults file). The fault-detection report (the executed
     * test list + the ORACLE ANOMALIES section) is still generated; there are simply 0 named
     * faults to correlate. Without this, {@code initialized} stayed false and report
     * generation was skipped ("not initialized. Cannot generate report") on any non-TT SUT.
     */
    public synchronized void initializeWithNoFaults() {
        injectedFaults.clear();
        initialized = true;
        logger.info("FaultDetectionTracker initialized with no named injected faults");
    }
    
    /**
     * Record a detected fault from a test execution
     */
    public synchronized void recordDetectedFault(String faultName, String testClassName, 
                                                 String testMethodName, long timestamp, 
                                                 String responseBody) {
        if (!initialized) {
            logger.warn("FaultDetectionTracker not initialized. Call loadInjectedFaults() first.");
            return;
        }
        
        // Track test case
        String fullTestName = testClassName + "." + testMethodName;
        allTestCases.add(fullTestName);
        
        // Check if this is a known injected fault
        if (!injectedFaults.containsKey(faultName)) {
            logger.debug("Detected fault '{}' is not in the injected faults registry", faultName);
            return;
        }
        
        // Record the detection
        FaultDetection detection = new FaultDetection(
            faultName, 
            testClassName, 
            testMethodName, 
            timestamp, 
            responseBody
        );
        
        detectedFaults.computeIfAbsent(faultName, k -> new ArrayList<>()).add(detection);
        
        logger.info("✅ Detected injected fault: {} in test: {}", faultName, fullTestName);
    }
    
    /**
     * Record a test case execution (for tracking total test count)
     */
    public synchronized void recordTestCase(String testClassName, String testMethodName) {
        String fullTestName = testClassName + "." + testMethodName;
        allTestCases.add(fullTestName);
    }

    /**
     * Record one oracle violation against the parallel anomaly map. Same
     * (oracle, endpointSig, violationSig) tuples collapse into one entry whose
     * hitCount is incremented; the first-seen sample wins (never overwritten)
     * so each anomaly carries a stable, reproducible example.
     *
     * <p>Severity is preserved (ERROR / WARN / INFO) so the report can show
     * what kind of finding it is.
     *
     * <p>Independent of {@link #recordDetectedFault}: a single test may both
     * detect an injected fault by name AND fire an oracle violation; the two
     * appear in their respective sections.
     *
     * @return {@code true} when the call was counted (new entry or hitCount
     *         bump); {@code false} when rejected by the guards or deduplicated
     *         as a repeat of the same (test, traceId, anomaly) instance.
     */
    public synchronized boolean recordOracleAnomaly(String oracle,
                                                 String endpointSig,
                                                 String violationSig,
                                                 String violationDetail,
                                                 String severity,
                                                 String testClassName,
                                                 String testMethodName,
                                                 String traceId) {
        if (oracle == null || oracle.isEmpty()) return false;
        if (endpointSig == null) endpointSig = "";
        if (violationSig == null || violationSig.isEmpty()) return false;

        // 0x01 SOH is a control char that can't appear in oracle / endpoint /
        // fingerprint strings, so it's a safer delimiter than "::" (paths or
        // method names could theoretically contain "::").
        String key = oracle + "\u0001" + endpointSig + "\u0001" + violationSig;

        // Per-execution dedup (see recordedAnomalyInstances). Only applied when
        // a traceId is present — the marker is the per-execution discriminator.
        // Ad-hoc callers without one keep the legacy count-every-call behaviour.
        if (traceId != null && !traceId.isEmpty()) {
            String instanceKey = (testClassName == null ? "" : testClassName)
                    + "\u0001" + (testMethodName == null ? "" : testMethodName)
                    + "\u0001" + traceId
                    + "\u0001" + key;
            if (!recordedAnomalyInstances.add(instanceKey)) {
                return false;
            }
        }

        long now = System.currentTimeMillis();
        OracleAnomaly existing = oracleAnomalies.get(key);
        if (existing == null) {
            OracleAnomaly anomaly = new OracleAnomaly(
                    oracle,
                    endpointSig,
                    violationSig,
                    violationDetail == null ? "" : violationDetail,
                    severity == null ? "" : severity,
                    testClassName == null ? "" : testClassName,
                    testMethodName == null ? "" : testMethodName,
                    traceId == null ? "" : traceId,
                    now);
            oracleAnomalies.put(key, anomaly);
        } else {
            existing.hitCount++;
            existing.lastSeenTs = now;
        }
        return true;
    }

    /**
     * Convenience: record every failing outcome in a TraceShapeOracle verdict
     * against the anomaly map. Used by generated test code, called once per
     * step-trace evaluation. Passing outcomes are skipped.
     *
     * <p>This is the production wiring path. The four invariant classes
     * themselves don't call {@link #recordOracleAnomaly} directly because they
     * emit verdicts (not log lines) and have no test-identity context.
     * Recording at the verdict-consumption site (generated test code, inside
     * {@code attachJaegerTrace}) is where both verdict and identity are in
     * scope. Violations from oracle runs outside the generated-test path
     * (e.g. ad-hoc replay against a stored trace) must call
     * {@link #recordOracleAnomaly} directly.
     */
    public synchronized void recordVerdict(TraceShapeVerdict verdict,
                                           String rootApiKey,
                                           String testClassName,
                                           String testMethodName,
                                           String traceId) {
        if (verdict == null) return;
        List<TraceShapeVerdict.InvariantOutcome> outcomes = verdict.getOutcomes();
        if (outcomes == null || outcomes.isEmpty()) return;

        // FIXES.md F1+F3: attribution is ONLY taken from the verdict (set
        // by TargetAttributionInvariant inside TraceShapeOracle, gated by
        // mst.oracle.shape.invariants.target_attribution.enabled). No
        // inline TraceAttribution.attribute() fallback: the single kill
        // switch flag fully disables the path. Callers that need
        // attribution must run the invariant first and embed its outcome
        // in the verdict.
        AttributionVerdict attribution = extractAttribution(outcomes);

        for (TraceShapeVerdict.InvariantOutcome o : outcomes) {
            if (o == null || o.passed) continue;
            // TARGET_ATTRIBUTION outcomes are diagnostic classifications,
            // not anomalies. They're consumed above as the attribution
            // bucket key; do not record them as their own anomalies.
            if ("TARGET_ATTRIBUTION".equals(o.kind)) continue;
            String violationSig = fingerprintViolation(o.kind, o.detail);
            boolean counted = recordOracleAnomaly(
                    o.kind,
                    rootApiKey,
                    violationSig,
                    o.detail,
                    o.severity == null ? "" : o.severity.name(),
                    testClassName,
                    testMethodName,
                    traceId);
            // A deduplicated recording (same step execution recorded twice via
            // the writer's success-path + catch-path) must not bump the
            // attribution histogram either, or the roll-up over-counts.
            if (counted && attribution != null) {
                String key = o.kind + "\u0001" + (rootApiKey == null ? "" : rootApiKey) + "\u0001" + violationSig;
                OracleAnomaly anomaly = oracleAnomalies.get(key);
                if (anomaly != null) {
                    anomaly.attributionCounts.merge(attribution, 1, Integer::sum);
                }
            }
        }
    }

    /**
     * Find the AttributionVerdict embedded in a TARGET_ATTRIBUTION outcome,
     * regardless of its passed flag (TARGET_REJECTION and NO_ATTRIBUTION
     * are informative too — they just classify as not-a-deviation). Returns
     * null when no such outcome is present or its detail isn't a recognized
     * verdict name.
     */
    private static AttributionVerdict extractAttribution(List<TraceShapeVerdict.InvariantOutcome> outcomes) {
        for (TraceShapeVerdict.InvariantOutcome o : outcomes) {
            if (o == null) continue;
            if (!"TARGET_ATTRIBUTION".equals(o.kind)) continue;
            String d = o.detail;
            if (d == null || d.isEmpty()) return null;
            try {
                return AttributionVerdict.valueOf(d);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        return null;
    }

    /**
     * Stable fingerprint of an invariant violation. Normalizes whitespace and
     * runs of digits with units (durations, p99 thresholds) so two timing
     * outliers on the same span shape collapse, while structural details
     * (depth, status code, edge endpoints) stay distinguishable.
     *
     * <p>SHA-256 hex; first 16 chars used as a compact, sort-friendly key.
     */
    static String fingerprintViolation(String kind, String detail) {
        String normalized = (detail == null ? "" : detail)
                .toLowerCase()
                .replaceAll("\\s+", " ")
                .trim()
                // Duration literals with unit suffixes — collapse the numeric
                // value so two timing outliers on the same shape share an fp.
                // Order matters: longer units (ns/ms) before "µs"/"us"/"s".
                .replaceAll("\\d+\\s*ns\\b", "<DUR>")
                .replaceAll("\\d+\\s*ms\\b", "<DUR>")
                .replaceAll("\\d+\\s*[µu]s\\b", "<DUR>")
                // Standalone hex blobs of length >= 8 (real Jaeger span IDs
                // are 16 hex chars, trace IDs 32). 8 is the smallest threshold
                // that won't collapse common all-hex English words like "facade"
                // or "decade" (6 chars) into <HEX>.
                .replaceAll("\\b[0-9a-f]{8,}\\b", "<HEX>")
                // Explicit keyed forms (kept for robustness).
                .replaceAll("traceid=[0-9a-f]+", "traceid=<HEX>")
                .replaceAll("spanid=[0-9a-f]+", "spanid=<HEX>");
        String input = (kind == null ? "" : kind) + "::" + normalized;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) hex.append(String.format("%02x", hash[i]));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
    
    /**
     * Generate comprehensive fault detection report
     */
    public synchronized void generateReport(String reportDir, String experimentName) {
        if (!initialized) {
            logger.warn("FaultDetectionTracker not initialized. Cannot generate report.");
            return;
        }
        
        try {
            // Create report directory if needed
            File dir = new File(reportDir);
            if (!dir.exists()) {
                dir.mkdirs();
                logger.info("Created fault detection report directory: {}", reportDir);
            }
            
            // Generate report filename with timestamp
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
            String timestamp = dateFormat.format(new Date());
            String reportFileName = String.format("fault-detection-summary-%s-%s.txt", 
                experimentName, timestamp);
            String reportPath = reportDir + "/" + reportFileName;
            
            // Write report
            try (PrintWriter writer = new PrintWriter(new FileWriter(reportPath))) {
                writeReport(writer, experimentName, timestamp);
            }
            
            logger.info("✅ Fault detection report generated: {}", reportPath);
            
        } catch (IOException e) {
            logger.error("Failed to generate fault detection report: {}", e.getMessage(), e);
        }
    }
    
    private void writeReport(PrintWriter writer, String experimentName, String timestamp) {
        SimpleDateFormat displayFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        // Calculate statistics
        int totalFaults = injectedFaults.size();
        int detectedCount = detectedFaults.size();
        int undetectedCount = totalFaults - detectedCount;
        double detectionRate = totalFaults > 0 ? (detectedCount * 100.0 / totalFaults) : 0.0;
        
        // Header
        writer.println("=".repeat(80));
        writer.println("                    FAULT DETECTION SUMMARY REPORT");
        writer.println("=".repeat(80));
        writer.println();
        writer.println("Experiment:         " + experimentName);
        writer.println("Generated:          " + displayFormat.format(new Date()));
        writer.println("Tracking Started:   " + displayFormat.format(new Date(trackingStartTime)));
        writer.println("Total Test Cases:   " + allTestCases.size());
        writer.println();
        
        // Fault Coverage Summary
        writer.println("=" + "=".repeat(79));
        writer.println("FAULT COVERAGE SUMMARY");
        writer.println("=" + "=".repeat(79));
        writer.println();
        writer.printf("Total Injected Faults:    %d%n", totalFaults);
        writer.printf("Detected Faults:          %d (%.1f%%)%n", detectedCount, detectionRate);
        writer.printf("Undetected Faults:        %d (%.1f%%)%n", undetectedCount, 100.0 - detectionRate);
        writer.println();
        
        // Progress bar visualization
        writer.println("Detection Progress:");
        int barLength = 50;
        int filledLength = (int) (barLength * detectionRate / 100.0);
        String bar = "█".repeat(filledLength) + "░".repeat(barLength - filledLength);
        writer.printf("[%s] %.1f%%%n", bar, detectionRate);
        writer.println();
        
        // Detected Faults Section
        if (!detectedFaults.isEmpty()) {
            writer.println("=" + "=".repeat(79));
            writer.println("DETECTED FAULTS (" + detectedCount + ")");
            writer.println("=" + "=".repeat(79));
            writer.println();
            
            int faultNum = 1;
            List<String> sortedFaultNames = new ArrayList<>(detectedFaults.keySet());
            Collections.sort(sortedFaultNames);
            
            for (String faultName : sortedFaultNames) {
                InjectedFault injectedFault = injectedFaults.get(faultName);
                List<FaultDetection> detections = detectedFaults.get(faultName);
                
                writer.printf("%d. %s%n", faultNum++, faultName);
                writer.printf("   Service:       %s%n", injectedFault.service);
                writer.printf("   API:           %s%n", injectedFault.api);
                writer.printf("   Detections:    %d time(s)%n", detections.size());
                writer.println();
                
                // List all detections with details
                for (int i = 0; i < detections.size(); i++) {
                    FaultDetection detection = detections.get(i);
                    writer.printf("   Detection #%d:%n", i + 1);
                    writer.printf("     Test Class:  %s%n", detection.testClassName);
                    writer.printf("     Test Method: %s%n", detection.testMethodName);
                    writer.printf("     Timestamp:   %s%n", displayFormat.format(new Date(detection.timestamp)));
                    writer.println();
                }
                
                writer.println("-".repeat(80));
                writer.println();
            }
        }
        
        // Undetected Faults Section
        Set<String> undetectedFaultNames = new HashSet<>(injectedFaults.keySet());
        undetectedFaultNames.removeAll(detectedFaults.keySet());
        
        if (!undetectedFaultNames.isEmpty()) {
            writer.println("=" + "=".repeat(79));
            writer.println("UNDETECTED FAULTS (" + undetectedCount + ")");
            writer.println("=" + "=".repeat(79));
            writer.println();
            
            List<String> sortedUndetected = new ArrayList<>(undetectedFaultNames);
            Collections.sort(sortedUndetected);
            
            int faultNum = 1;
            for (String faultName : sortedUndetected) {
                InjectedFault fault = injectedFaults.get(faultName);
                writer.printf("%d. %s%n", faultNum++, faultName);
                writer.printf("   Service:       %s%n", fault.service);
                writer.printf("   API:           %s%n", fault.api);
                writer.println();
            }
        }
        
        // Phase 0: Oracle Anomalies Section — surfaces trace-shape oracle
        // violations that fired but didn't match an injected fault by name.
        // Gated by mist.report.oracle.anomalies.enabled (default true). Set
        // false to reproduce the legacy report shape byte-for-byte.
        boolean anomaliesEnabled = !"false".equalsIgnoreCase(
                System.getProperty("mist.report.oracle.anomalies.enabled", "true"));
        if (anomaliesEnabled && !oracleAnomalies.isEmpty()) {
            List<OracleAnomaly> sorted = new ArrayList<>(oracleAnomalies.values());
            sorted.sort((a, b) -> Integer.compare(b.hitCount, a.hitCount));
            long totalHits = 0L;
            for (OracleAnomaly a : sorted) totalHits += a.hitCount;

            writer.println("=" + "=".repeat(79));
            writer.println("ORACLE ANOMALIES (" + sorted.size() + " distinct, " + totalHits + " hits)");
            writer.println("=" + "=".repeat(79));
            writer.println();
            writer.println("These are TraceShapeOracle invariant violations the tool detected.");
            writer.println("They are tool-detected trace-shape deviations, NOT confirmed bugs —");
            writer.println("an upper-bound bug-finding signal. Entries also matching an injected");
            writer.println("fault name appear in the DETECTED FAULTS section above.");
            writer.println();

            // Phase 2: attribution roll-up. Sums all per-anomaly attribution
            // histograms into a single line so a reviewer sees the
            // tool's confirmed-bug-detection count at a glance, separate
            // from the upper-bound anomaly count above.
            Map<AttributionVerdict, Long> rollup = new EnumMap<>(AttributionVerdict.class);
            for (OracleAnomaly a : sorted) {
                for (Map.Entry<AttributionVerdict, Integer> e : a.attributionCounts.entrySet()) {
                    rollup.merge(e.getKey(), e.getValue().longValue(), Long::sum);
                }
            }
            if (!rollup.isEmpty()) {
                writer.println("Attribution roll-up (target = SUT-confirmed bug-detection event):");
                for (AttributionVerdict v : AttributionVerdict.values()) {
                    Long c = rollup.get(v);
                    if (c != null && c > 0) {
                        writer.printf("  %-22s %d%n", v.name() + ":", c);
                    }
                }
                writer.println();
            }

            int n = 1;
            for (OracleAnomaly a : sorted) {
                writer.printf("%d. %s  |  %s%n", n++, a.oracle,
                        a.endpointSig == null || a.endpointSig.isEmpty() ? "(no endpoint)" : a.endpointSig);
                if (a.severity != null && !a.severity.isEmpty()) {
                    writer.printf("   Severity:      %s%n", a.severity);
                }
                writer.printf("   Violation:     %s%n",
                        a.violationDetail == null || a.violationDetail.isEmpty() ? "(no detail)" : a.violationDetail);
                writer.printf("   Hits:          %d time(s)%n", a.hitCount);
                writer.printf("   First seen:    %s%n", displayFormat.format(new Date(a.firstSeenTs)));
                writer.printf("   Last seen:     %s%n", displayFormat.format(new Date(a.lastSeenTs)));
                // Phase 2: attribution histogram in increasing-confidence order.
                if (!a.attributionCounts.isEmpty()) {
                    StringBuilder attr = new StringBuilder();
                    for (AttributionVerdict v : AttributionVerdict.values()) {
                        Integer c = a.attributionCounts.get(v);
                        if (c != null && c > 0) {
                            if (attr.length() > 0) attr.append(", ");
                            attr.append(v.name()).append(": ").append(c);
                        }
                    }
                    writer.printf("   Attribution:   %s%n", attr);
                }
                if (a.sampleTestClass != null && !a.sampleTestClass.isEmpty()) {
                    writer.printf("   Example test:  %s.%s%n", a.sampleTestClass, a.sampleTestMethod);
                }
                if (a.sampleTraceId != null && !a.sampleTraceId.isEmpty()) {
                    writer.printf("   Example trace: %s%n", a.sampleTraceId);
                }
                writer.println();
            }
        }

        // Test Cases Summary
        if (!allTestCases.isEmpty()) {
            writer.println("=" + "=".repeat(79));
            writer.println("TEST CASES EXECUTED (" + allTestCases.size() + ")");
            writer.println("=" + "=".repeat(79));
            writer.println();
            
            List<String> sortedTestCases = new ArrayList<>(allTestCases);
            Collections.sort(sortedTestCases);
            
            for (int i = 0; i < sortedTestCases.size(); i++) {
                writer.printf("%d. %s%n", i + 1, sortedTestCases.get(i));
            }
            writer.println();
        }
        
        // Footer
        writer.println("=" + "=".repeat(79));
        writer.println("                          END OF REPORT");
        writer.println("=" + "=".repeat(79));
    }
    
    /**
     * Reset tracker state (useful for multiple test runs)
     */
    public synchronized void reset() {
        detectedFaults.clear();
        allTestCases.clear();
        oracleAnomalies.clear();
        recordedAnomalyInstances.clear();
        trackingStartTime = System.currentTimeMillis();
        logger.info("FaultDetectionTracker reset for new test run");
    }
    
    /**
     * Get detection statistics
     */
    public synchronized Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalInjectedFaults", injectedFaults.size());
        stats.put("detectedFaults", detectedFaults.size());
        stats.put("undetectedFaults", injectedFaults.size() - detectedFaults.size());
        stats.put("detectionRate", injectedFaults.size() > 0 
            ? (detectedFaults.size() * 100.0 / injectedFaults.size()) : 0.0);
        stats.put("totalTestCases", allTestCases.size());
        int totalAnomalyHits = 0;
        for (OracleAnomaly a : oracleAnomalies.values()) totalAnomalyHits += a.hitCount;
        stats.put("oracleAnomaliesDistinct", oracleAnomalies.size());
        stats.put("oracleAnomaliesTotalHits", totalAnomalyHits);
        return stats;
    }

    /**
     * End-of-run console summary of oracle findings, grouped by kind × severity.
     * Distinct anomalies (not raw hits) are counted, matching the report's
     * "distinct" bucket. This drives the prominent stdout summary so a
     * hidden-downstream / soft-error finding is visible to a user who only reads
     * the terminal — without it the finding lives only in the Allure report or
     * the .txt report, which most users never open.
     */
    public synchronized AnomalySummary summarizeAnomalies() {
        Map<String, int[]> byKind = new java.util.TreeMap<>(); // kind -> {error, warn, info}
        java.util.List<Finding> findings = new java.util.ArrayList<>();
        int error = 0, warn = 0, info = 0, hits = 0;
        for (OracleAnomaly a : oracleAnomalies.values()) {
            String kind = (a.oracle == null || a.oracle.isEmpty()) ? "UNKNOWN" : a.oracle;
            String sev  = (a.severity == null || a.severity.isEmpty()) ? "INFO" : a.severity.toUpperCase();
            int[] row = byKind.computeIfAbsent(kind, k -> new int[3]);
            if ("ERROR".equals(sev))     { row[0]++; error++; }
            else if ("WARN".equals(sev)) { row[1]++; warn++; }
            else                         { row[2]++; info++; }
            hits += a.hitCount;
            findings.add(new Finding(kind, sev, a.endpointSig, a.violationDetail, a.hitCount, a.sampleTraceId));
        }
        // ERROR first, then by recurrence — so the most actionable findings lead.
        findings.sort((x, y) -> {
            int r = Integer.compare(sevRank(x.severity), sevRank(y.severity));
            return r != 0 ? r : Integer.compare(y.hits, x.hits);
        });
        return new AnomalySummary(byKind, error, warn, info, allTestCases.size(), hits, findings);
    }

    private static int sevRank(String s) { return "ERROR".equals(s) ? 0 : "WARN".equals(s) ? 1 : 2; }

    /** One oracle finding, with the data a user needs to act (endpoint + swallowed span). */
    public static final class Finding {
        public final String kind, severity, endpoint, detail, traceId;
        public final int hits;
        Finding(String kind, String severity, String endpoint, String detail, int hits, String traceId) {
            this.kind = kind; this.severity = severity; this.endpoint = endpoint;
            this.detail = detail; this.hits = hits; this.traceId = traceId;
        }
    }

    /**
     * View of the anomaly counts + top findings for the end-of-run console summary,
     * with {@link #render} formatting the prominent stdout block. {@code byKind} and
     * {@code findings} are fresh per call (snapshots, safe for the caller to read).
     */
    public static final class AnomalySummary {
        public final Map<String, int[]> byKind; // kind -> {errorCount, warnCount, infoCount}
        public final int errorCount, warnCount, infoCount, testCaseCount, totalHits;
        public final java.util.List<Finding> findings; // sorted: ERROR first, then by hits desc

        AnomalySummary(Map<String, int[]> byKind, int e, int w, int i, int t, int hits,
                       java.util.List<Finding> findings) {
            this.byKind = byKind; this.errorCount = e; this.warnCount = w; this.infoCount = i;
            this.testCaseCount = t; this.totalHits = hits; this.findings = findings;
        }
        public int total() { return errorCount + warnCount + infoCount; }

        private static String label(String kind, boolean ascii) {
            switch (kind) {
                case "HIDDEN_DOWNSTREAM_FAILURE": return (ascii ? "[HIDDEN]   " : "🕳️  ") + "Hidden downstream failure  (a 2xx hid a swallowed downstream error)";
                case "RESPONSE_ENVELOPE":         return (ascii ? "[SOFT-ERR] " : "🟡  ") + "Soft error  (a 2xx body that is actually an error)";
                case "STATUS_PROPAGATION":        return (ascii ? "[STATUS]   " : "↕️  ") + "Status-propagation anomaly";
                case "SPAN_TREE_SHAPE":           return (ascii ? "[SHAPE]    " : "🌲  ") + "Span-tree shape anomaly";
                case "TIMING_ENVELOPE":           return (ascii ? "[TIMING]   " : "⏱️  ") + "Timing-envelope anomaly";
                default:                          return (ascii ? "[" + kind + "] " : "") + kind;
            }
        }
        private static String trunc(String s, int n) {
            if (s == null) return "";
            s = s.replace('\n', ' ').trim();
            return s.length() <= n ? s : s.substring(0, Math.max(0, n - 3)) + "...";
        }

        /** Default (UTF-8/emoji) rendering. */
        public String render(String reportDir) { return render(reportDir, false); }

        /**
         * Format the prominent end-of-run findings summary for stdout.
         * @param ascii true → plain-ASCII (no emoji/box glyphs) for non-UTF-8 / NO_COLOR terminals.
         */
        public String render(String reportDir, boolean ascii) {
            String bullet = ascii ? ">" : "▸";
            String arrow  = ascii ? "->" : "→";
            String sep    = ascii ? " | " : " · ";
            String ok     = ascii ? "[OK]" : "✓";
            String bar = "==================================================================";
            StringBuilder sb = new StringBuilder("\n").append(bar).append("\n");
            if (total() == 0) {
                sb.append("  MIST findings: ").append(ok).append(" no oracle anomalies across ")
                  .append(testCaseCount).append(" executed test case(s)\n").append(bar).append("\n");
                return sb.toString();
            }
            sb.append("  MIST findings — ").append(total()).append(total() == 1 ? " anomaly" : " anomalies")
              .append(" across ").append(testCaseCount).append(" executed test case(s)\n");
            sb.append("  ------------------------------------------------------------\n");
            for (Map.Entry<String, int[]> e : byKind.entrySet()) {
                int[] r = e.getValue();
                StringBuilder c = new StringBuilder();
                if (r[0] > 0) c.append("ERROR ").append(r[0]).append("  ");
                if (r[1] > 0) c.append("WARN ").append(r[1]).append("  ");
                if (r[2] > 0) c.append("INFO ").append(r[2]).append("  ");
                sb.append("  ").append(String.format("%-15s", c.toString().trim())).append(" ")
                  .append(label(e.getKey(), ascii)).append("\n");
            }
            // The actionable part: which endpoint, and what was swallowed.
            int shown = Math.min(findings.size(), 5);
            if (shown > 0) {
                sb.append("  ------------------------------------------------------------\n");
                for (int i = 0; i < shown; i++) {
                    Finding f = findings.get(i);
                    String tid = (f.traceId == null || f.traceId.isEmpty()) ? ""
                            : ", trace " + (f.traceId.length() > 8 ? f.traceId.substring(0, 8) : f.traceId);
                    sb.append("  ").append(bullet).append(" ").append(String.format("%-5s", f.severity))
                      .append(" ").append(trunc(f.endpoint, 46)).append("  ").append(arrow).append("  ")
                      .append(trunc(f.detail, 70)).append("   (").append(f.hits).append("x").append(tid).append(")\n");
                }
                if (findings.size() > shown) {
                    sb.append("  ").append(bullet).append(" (+").append(findings.size() - shown)
                      .append(" more — see the detail report)\n");
                }
            }
            sb.append("  ------------------------------------------------------------\n");
            sb.append("  ").append(errorCount).append(" ERROR").append(sep).append(warnCount)
              .append(" WARN").append(sep).append(infoCount).append(" INFO")
              .append("   (").append(total()).append(" unique findings, ").append(totalHits).append(" occurrences)\n");
            // Relate findings to pass/fail so counts aren't misread as failed-test counts.
            sb.append("  ERROR findings fail the test (red); WARN/INFO are reported but do NOT fail it.\n");
            sb.append("  ").append(bullet).append(" detail: ")
              .append(reportDir == null ? "logs/fault-detection-reports/" : reportDir).append("/\n");
            sb.append("  ").append(bullet).append(" Allure: allure serve target/allure-results\n");
            sb.append(bar).append("\n");
            return sb.toString();
        }
    }

    /**
     * Test-only: read-only view of oracle anomalies for assertions.
     */
    Map<String, OracleAnomaly> getOracleAnomaliesForTest() {
        return Collections.unmodifiableMap(oracleAnomalies);
    }
    
    // Inner classes for data structures
    
    private static class InjectedFault {
        final String faultName;
        final String service;
        final String api;
        
        InjectedFault(String faultName, String service, String api) {
            this.faultName = faultName;
            this.service = service;
            this.api = api;
        }
    }
    
    private static class FaultDetection {
        final String faultName;
        final String testClassName;
        final String testMethodName;
        final long timestamp;
        final String responseBody;

        FaultDetection(String faultName, String testClassName, String testMethodName,
                      long timestamp, String responseBody) {
            this.faultName = faultName;
            this.testClassName = testClassName;
            this.testMethodName = testMethodName;
            this.timestamp = timestamp;
            this.responseBody = responseBody;
        }
    }

    /**
     * A trace-shape oracle violation collapsed across all tests that fired
     * the same kind of mismatch against the same endpoint. First-seen sample
     * is preserved; {@link #hitCount} grows with each recurrence.
     */
    static final class OracleAnomaly {
        final String oracle;
        final String endpointSig;
        final String violationSig;
        final String violationDetail;
        final String severity;
        final String sampleTestClass;
        final String sampleTestMethod;
        final String sampleTraceId;
        final long firstSeenTs;
        long lastSeenTs;
        int hitCount;
        // Phase 2: per-anomaly attribution histogram. Each verdict iteration
        // that visits this anomaly increments one bucket. Empty when no
        // negative-target context was passed (positive tests, ad-hoc runs).
        final Map<AttributionVerdict, Integer> attributionCounts = new EnumMap<>(AttributionVerdict.class);

        OracleAnomaly(String oracle, String endpointSig, String violationSig,
                      String violationDetail, String severity,
                      String sampleTestClass, String sampleTestMethod,
                      String sampleTraceId, long ts) {
            this.oracle = oracle;
            this.endpointSig = endpointSig;
            this.violationSig = violationSig;
            this.violationDetail = violationDetail;
            this.severity = severity;
            this.sampleTestClass = sampleTestClass;
            this.sampleTestMethod = sampleTestMethod;
            this.sampleTraceId = sampleTraceId;
            this.firstSeenTs = ts;
            this.lastSeenTs = ts;
            this.hitCount = 1;
        }
    }
}

