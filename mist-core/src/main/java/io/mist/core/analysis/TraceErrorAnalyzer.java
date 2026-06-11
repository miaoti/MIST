package io.mist.core.analysis;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.stream.Collectors;
import io.mist.llm.LLMService;

/**
 * Analyzes Jaeger traces to identify root causes of API failures.
 * 
 * This class examines the trace hierarchy and identifies:
 * - Which spans actually failed (have error=true in tags)
 * - The root cause failures (deepest failed spans in the call tree)
 * - Error propagation through the parent-child relationships
 * - Detailed error information from logs
 */
public class TraceErrorAnalyzer {
    
    /**
     * Represents a failed span with error details
     */
    public static class FailedSpan {
        private final String spanId;
        private final String operationName;
        private final String serviceName;
        private final boolean isRootCause;
        private final List<String> errorMessages;
        private final List<String> exceptionTypes;
        private final List<String> stackTraces;
        private final long startTime;
        private final long duration;
        private final int httpStatusCode;
        
        public FailedSpan(String spanId, String operationName, String serviceName, 
                         boolean isRootCause, List<String> errorMessages, 
                         List<String> exceptionTypes, List<String> stackTraces,
                         long startTime, long duration, int httpStatusCode) {
            this.spanId = spanId;
            this.operationName = operationName;
            this.serviceName = serviceName;
            this.isRootCause = isRootCause;
            this.errorMessages = new ArrayList<>(errorMessages);
            this.exceptionTypes = new ArrayList<>(exceptionTypes);
            this.stackTraces = new ArrayList<>(stackTraces);
            this.startTime = startTime;
            this.duration = duration;
            this.httpStatusCode = httpStatusCode;
        }
        
        // Getters
        public String getSpanId() { return spanId; }
        public String getOperationName() { return operationName; }
        public String getServiceName() { return serviceName; }
        public boolean isRootCause() { return isRootCause; }
        public List<String> getErrorMessages() { return errorMessages; }
        public List<String> getExceptionTypes() { return exceptionTypes; }
        public List<String> getStackTraces() { return stackTraces; }
        public long getStartTime() { return startTime; }
        public long getDuration() { return duration; }
        public int getHttpStatusCode() { return httpStatusCode; }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("FailedSpan{");
            sb.append("spanId='").append(spanId).append('\'');
            sb.append(", operation='").append(operationName).append('\'');
            sb.append(", service='").append(serviceName).append('\'');
            sb.append(", isRootCause=").append(isRootCause);
            sb.append(", httpStatus=").append(httpStatusCode);
            sb.append(", errors=").append(errorMessages.size());
            sb.append('}');
            return sb.toString();
        }
    }
    
    /**
     * Represents the complete error analysis result
     */
    public static class ErrorAnalysisResult {
        private final List<FailedSpan> allFailedSpans;
        private final List<FailedSpan> rootCauseFailures;
        private final boolean hasErrors;
        private final String summary;
        
        public ErrorAnalysisResult(List<FailedSpan> allFailedSpans, List<FailedSpan> rootCauseFailures) {
            this.allFailedSpans = new ArrayList<>(allFailedSpans);
            this.rootCauseFailures = new ArrayList<>(rootCauseFailures);
            this.hasErrors = !allFailedSpans.isEmpty();
            this.summary = generateSummary();
        }
        
        private String generateSummary() {
            if (!hasErrors) {
                return "✅ No errors detected in trace";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("❌ ").append(rootCauseFailures.size()).append(" root cause failure(s) detected:\n");
            
            for (int i = 0; i < rootCauseFailures.size(); i++) {
                FailedSpan failure = rootCauseFailures.get(i);
                sb.append("  ").append(i + 1).append(". ");
                sb.append(failure.getServiceName()).append(" → ");
                sb.append(failure.getOperationName());
                
                if (failure.getHttpStatusCode() > 0) {
                    sb.append(" (HTTP ").append(failure.getHttpStatusCode()).append(")");
                }
                
                if (!failure.getErrorMessages().isEmpty()) {
                    sb.append(" - ").append(failure.getErrorMessages().get(0));
                }
                sb.append("\n");
            }
            
            if (allFailedSpans.size() > rootCauseFailures.size()) {
                int propagated = allFailedSpans.size() - rootCauseFailures.size();
                sb.append("  ↗️ ").append(propagated).append(" additional span(s) failed due to error propagation");
            }
            
            return sb.toString();
        }
        
        // Getters
        public List<FailedSpan> getAllFailedSpans() { return allFailedSpans; }
        public List<FailedSpan> getRootCauseFailures() { return rootCauseFailures; }
        public boolean hasErrors() { return hasErrors; }
        public String getSummary() { return summary; }
    }
    
    /**
     * Analyzes a Jaeger trace to identify error root causes
     */
    public static ErrorAnalysisResult analyzeTrace(JSONObject trace) {
        if (trace == null || !trace.has("spans")) {
            return new ErrorAnalysisResult(Collections.emptyList(), Collections.emptyList());
        }
        
        JSONArray spans = trace.getJSONArray("spans");
        Map<String, String> processes = extractProcesses(trace);
        
        // Build span hierarchy (parent-child relationships)
        Map<String, List<String>> childrenMap = buildHierarchy(spans);
        
        // Find all spans with error=true
        List<FailedSpan> allFailedSpans = findFailedSpans(spans, processes);
        
        // Identify root causes (failed spans that don't have failed children)
        List<FailedSpan> rootCauseFailures = identifyRootCauses(allFailedSpans, childrenMap);
        
        return new ErrorAnalysisResult(allFailedSpans, rootCauseFailures);
    }
    
    /**
     * Extracts process (service) information from trace
     */
    private static Map<String, String> extractProcesses(JSONObject trace) {
        Map<String, String> processes = new HashMap<>();
        
        if (trace.has("processes")) {
            JSONObject processesObj = trace.getJSONObject("processes");
            for (String processId : processesObj.keySet()) {
                JSONObject process = processesObj.getJSONObject(processId);
                if (process.has("serviceName")) {
                    processes.put(processId, process.getString("serviceName"));
                }
            }
        }
        
        return processes;
    }
    
    /**
     * Builds parent-child hierarchy from spans
     */
    private static Map<String, List<String>> buildHierarchy(JSONArray spans) {
        Map<String, List<String>> childrenMap = new HashMap<>();
        
        for (int i = 0; i < spans.length(); i++) {
            JSONObject span = spans.getJSONObject(i);
            String spanId = span.getString("spanID");
            
            // Initialize children list for this span
            childrenMap.putIfAbsent(spanId, new ArrayList<>());
            
            // Find parent relationships
            if (span.has("references")) {
                JSONArray references = span.getJSONArray("references");
                for (int j = 0; j < references.length(); j++) {
                    JSONObject ref = references.getJSONObject(j);
                    if ("CHILD_OF".equals(ref.optString("refType"))) {
                        String parentSpanId = ref.getString("spanID");
                        childrenMap.computeIfAbsent(parentSpanId, k -> new ArrayList<>()).add(spanId);
                    }
                }
            }
        }
        
        return childrenMap;
    }
    
    /**
     * Finds all spans that have error=true in their tags
     */
    private static List<FailedSpan> findFailedSpans(JSONArray spans, Map<String, String> processes) {
        List<FailedSpan> failedSpans = new ArrayList<>();
        
        for (int i = 0; i < spans.length(); i++) {
            JSONObject span = spans.getJSONObject(i);
            
            if (hasErrorTag(span)) {
                FailedSpan failedSpan = createFailedSpan(span, processes);
                failedSpans.add(failedSpan);
            }
        }
        
        return failedSpans;
    }
    
    /**
     * Checks if a span has error=true in its tags
     */
    private static boolean hasErrorTag(JSONObject span) {
        if (!span.has("tags")) {
            return false;
        }
        
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
    
    /**
     * Creates a FailedSpan object from a span JSON
     */
    private static FailedSpan createFailedSpan(JSONObject span, Map<String, String> processes) {
        String spanId = span.getString("spanID");
        String operationName = span.optString("operationName", "Unknown Operation");
        String processId = span.optString("processID", "");
        String serviceName = processes.getOrDefault(processId, "Unknown Service");
        long startTime = span.optLong("startTime", 0);
        long duration = span.optLong("duration", 0);
        
        // Extract HTTP status code from tags
        int httpStatusCode = extractHttpStatusCode(span);
        
        // Extract error information from logs
        List<String> errorMessages = new ArrayList<>();
        List<String> exceptionTypes = new ArrayList<>();
        List<String> stackTraces = new ArrayList<>();
        
        if (span.has("logs")) {
            JSONArray logs = span.getJSONArray("logs");
            for (int i = 0; i < logs.length(); i++) {
                JSONObject log = logs.getJSONObject(i);
                if (log.has("fields")) {
                    JSONArray fields = log.getJSONArray("fields");
                    extractErrorDetails(fields, errorMessages, exceptionTypes, stackTraces);
                }
            }
        }
        
        return new FailedSpan(spanId, operationName, serviceName, false, 
                             errorMessages, exceptionTypes, stackTraces, 
                             startTime, duration, httpStatusCode);
    }
    
    /**
     * Extracts HTTP status code from span tags
     */
    private static int extractHttpStatusCode(JSONObject span) {
        if (!span.has("tags")) {
            return 0;
        }
        
        JSONArray tags = span.getJSONArray("tags");
        for (int i = 0; i < tags.length(); i++) {
            JSONObject tag = tags.getJSONObject(i);
            if ("http.status_code".equals(tag.optString("key")) && 
                "int64".equals(tag.optString("type"))) {
                return tag.optInt("value", 0);
            }
        }
        
        return 0;
    }
    
    /**
     * Extracts error details from log fields
     */
    private static void extractErrorDetails(JSONArray fields, List<String> errorMessages, 
                                          List<String> exceptionTypes, List<String> stackTraces) {
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            String key = field.optString("key", "");
            String value = field.optString("value", "");
            
            switch (key) {
                case "exception.message":
                    if (!value.isEmpty()) {
                        errorMessages.add(value);
                    }
                    break;
                case "exception.type":
                    if (!value.isEmpty()) {
                        exceptionTypes.add(value);
                    }
                    break;
                case "exception.stacktrace":
                    if (!value.isEmpty()) {
                        stackTraces.add(value);
                    }
                    break;
            }
        }
    }
    
    /**
     * Identifies root cause failures (failed spans without failed children)
     */
    private static List<FailedSpan> identifyRootCauses(List<FailedSpan> allFailedSpans, 
                                                      Map<String, List<String>> childrenMap) {
        Set<String> failedSpanIds = allFailedSpans.stream()
                .map(FailedSpan::getSpanId)
                .collect(Collectors.toSet());
        
        List<FailedSpan> rootCauses = new ArrayList<>();
        
        for (FailedSpan failedSpan : allFailedSpans) {
            boolean isRootCause = true;
            
            // Check if this span has any failed children
            List<String> children = childrenMap.getOrDefault(failedSpan.getSpanId(), Collections.emptyList());
            for (String childId : children) {
                if (failedSpanIds.contains(childId)) {
                    isRootCause = false;
                    break;
                }
            }
            
            if (isRootCause) {
                // Create a new FailedSpan with isRootCause=true
                FailedSpan rootCauseSpan = new FailedSpan(
                    failedSpan.getSpanId(),
                    failedSpan.getOperationName(),
                    failedSpan.getServiceName(),
                    true, // Mark as root cause
                    failedSpan.getErrorMessages(),
                    failedSpan.getExceptionTypes(),
                    failedSpan.getStackTraces(),
                    failedSpan.getStartTime(),
                    failedSpan.getDuration(),
                    failedSpan.getHttpStatusCode()
                );
                rootCauses.add(rootCauseSpan);
            }
        }
        
        return rootCauses;
    }
    
    /**
     * Generates a detailed error report for display
     */
    public static String generateErrorReport(ErrorAnalysisResult analysis) {
        if (!analysis.hasErrors()) {
            return "✅ TRACE STATUS: SUCCESS\n\nNo errors detected in this trace execution.";
        }
        
        StringBuilder report = new StringBuilder();
        report.append("❌ TRACE STATUS: FAILED\n\n");
        report.append("═══════════════════════════════════════════════════════════════════════════════\n");
        report.append("🔍 ERROR ANALYSIS SUMMARY\n");
        report.append("═══════════════════════════════════════════════════════════════════════════════\n\n");
        
        report.append(analysis.getSummary()).append("\n\n");
        
        // Root cause details
        if (!analysis.getRootCauseFailures().isEmpty()) {
            report.append("═════════════════════════════════════════════════════════════════════════════════\n");
            report.append("💥 ROOT CAUSE FAILURES (").append(analysis.getRootCauseFailures().size()).append(")\n");
            report.append("═════════════════════════════════════════════════════════════════════════════════\n\n");
            
            for (int i = 0; i < analysis.getRootCauseFailures().size(); i++) {
                FailedSpan failure = analysis.getRootCauseFailures().get(i);
                report.append("┌─ ROOT CAUSE #").append(i + 1).append(" ").append("─".repeat(60)).append("\n");
                report.append("│ 🏢 Service: ").append(failure.getServiceName()).append("\n");
                report.append("│ 🔧 Operation: ").append(failure.getOperationName()).append("\n");
                report.append("│ 🆔 Span ID: ").append(failure.getSpanId()).append("\n");
                
                if (failure.getHttpStatusCode() > 0) {
                    report.append("│ 🌐 HTTP Status: ").append(failure.getHttpStatusCode()).append("\n");
                }
                
                report.append("│ ⏱️ Duration: ").append(failure.getDuration() / 1000.0).append("ms\n");
                
                // Error messages
                if (!failure.getErrorMessages().isEmpty()) {
                    report.append("│\n│ 💬 Error Messages:\n");
                    for (String msg : failure.getErrorMessages()) {
                        report.append("│   • ").append(msg).append("\n");
                    }
                }
                
                // Exception types
                if (!failure.getExceptionTypes().isEmpty()) {
                    report.append("│\n│ 🐛 Exception Types:\n");
                    for (String type : failure.getExceptionTypes()) {
                        report.append("│   • ").append(type).append("\n");
                    }
                }
                
                // Stack trace (first few lines only)
                if (!failure.getStackTraces().isEmpty()) {
                    report.append("│\n│ 📋 Stack Trace Preview:\n");
                    String stackTrace = failure.getStackTraces().get(0);
                    String[] lines = stackTrace.split("\n");
                    int linesToShow = Math.min(5, lines.length);
                    for (int j = 0; j < linesToShow; j++) {
                        report.append("│   ").append(lines[j]).append("\n");
                    }
                    if (lines.length > linesToShow) {
                        report.append("│   ... (").append(lines.length - linesToShow).append(" more lines)\n");
                    }
                }
                
                report.append("└").append("─".repeat(70)).append("\n\n");
            }
        }
        
        // Error propagation summary
        if (analysis.getAllFailedSpans().size() > analysis.getRootCauseFailures().size()) {
            int propagated = analysis.getAllFailedSpans().size() - analysis.getRootCauseFailures().size();
            report.append("┌─ ERROR PROPAGATION ").append("─".repeat(50)).append("\n");
            report.append("│ ↗️ ").append(propagated).append(" additional spans failed due to error propagation\n");
            report.append("│ (Parent operations automatically fail when children fail)\n");
            report.append("└").append("─".repeat(70)).append("\n\n");
        }
        
        report.append("═══════════════════════════════════════════════════════════════════════════════\n");
        report.append("📊 FAILURE STATISTICS\n");
        report.append("═══════════════════════════════════════════════════════════════════════════════\n");
        report.append("• Total Failed Spans: ").append(analysis.getAllFailedSpans().size()).append("\n");
        report.append("• Root Cause Failures: ").append(analysis.getRootCauseFailures().size()).append("\n");
        report.append("• Propagated Failures: ").append(analysis.getAllFailedSpans().size() - analysis.getRootCauseFailures().size()).append("\n");
        
        return report.toString();
    }
    
    /**
     * Generates intelligent analysis using LLM for deeper insights
     */
    public static String generateIntelligentAnalysis(ErrorAnalysisResult analysis, JSONObject trace) {
        if (!analysis.hasErrors()) {
            return "✅ Trace executed successfully with no errors detected. All services are functioning normally.";
        }

        // Cache lookup: identical failure-mode signatures across traces reuse the same diagnosis.
        String cacheKey = buildIntelligentAnalysisCacheKey(analysis);
        IntelligentAnalysisCache cache = IntelligentAnalysisCache.getInstance(
                System.getProperty("intelligent.analysis.cache.path", ".mist/intelligent-analysis-cache.json"));
        Optional<String> cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        try {
            // Prepare context for LLM analysis
            StringBuilder context = new StringBuilder();
            context.append("Analyze this microservice distributed trace error:\n\n");
            
            // Basic trace information
            context.append("TRACE OVERVIEW:\n");
            context.append("- Trace ID: ").append(trace.optString("traceID", "Unknown")).append("\n");
            if (trace.has("spans")) {
                context.append("- Total Spans: ").append(trace.getJSONArray("spans").length()).append("\n");
            }
            context.append("- Error Status: FAILED\n");
            context.append("- Root Cause Failures: ").append(analysis.getRootCauseFailures().size()).append("\n");
            context.append("- Total Failed Spans: ").append(analysis.getAllFailedSpans().size()).append("\n");
            context.append("- Propagated Failures: ").append(analysis.getAllFailedSpans().size() - analysis.getRootCauseFailures().size()).append("\n\n");
            
            // Detailed root cause information
            context.append("EXACT FAILURE POINTS:\n");
            for (int i = 0; i < analysis.getRootCauseFailures().size(); i++) {
                FailedSpan failure = analysis.getRootCauseFailures().get(i);
                context.append("Root Cause #").append(i + 1).append(":\n");
                context.append("- Failed Service: ").append(failure.getServiceName()).append("\n");
                context.append("- Failed Operation: ").append(failure.getOperationName()).append("\n");
                context.append("- HTTP Status: ").append(failure.getHttpStatusCode()).append("\n");
                context.append("- Execution Time: ").append(failure.getDuration() / 1000.0).append("ms\n");
                
                if (!failure.getErrorMessages().isEmpty()) {
                    context.append("- Exact Error: ").append(failure.getErrorMessages().get(0)).append("\n");
                }
                
                if (!failure.getExceptionTypes().isEmpty()) {
                    context.append("- Exception Type: ").append(failure.getExceptionTypes().get(0)).append("\n");
                }
                
                // Extract the exact failing method/line from stack trace
                if (!failure.getStackTraces().isEmpty()) {
                    String stackTrace = failure.getStackTraces().get(0);
                    String[] lines = stackTrace.split("\n");
                    context.append("- Exact Failure Location:\n");
                    
                    // Find the first non-framework line (actual application code)
                    boolean foundApplicationCode = false;
                    for (String line : lines) {
                        line = line.trim();
                        if (line.startsWith("at ") && 
                            !line.contains("java.lang.") && 
                            !line.contains("org.springframework.") &&
                            !line.contains("sun.reflect.") &&
                            !line.contains("org.apache.") &&
                            !foundApplicationCode) {
                            context.append("  → ").append(line).append(" ← THIS IS WHERE IT FAILED\n");
                            foundApplicationCode = true;
                            break;
                        }
                    }
                    
                    // If no application code found, show first few relevant lines
                    if (!foundApplicationCode) {
                        int count = 0;
                        for (String line : lines) {
                            if (line.trim().startsWith("at ") && count < 3) {
                                context.append("  ").append(line.trim()).append("\n");
                                count++;
                            }
                        }
                    }
                }
                context.append("\n");
            }
            
            // Service interaction context
            context.append("SERVICE INTERACTION CONTEXT:\n");
            Map<String, String> processes = extractProcesses(trace);
            context.append("- Services Involved: ").append(String.join(", ", processes.values())).append("\n");
            
            // Extract service call patterns
            if (trace.has("spans")) {
                JSONArray spans = trace.getJSONArray("spans");
                Set<String> httpMethods = new HashSet<>();
                Set<String> endpoints = new HashSet<>();
                
                for (int i = 0; i < spans.length(); i++) {
                    JSONObject span = spans.getJSONObject(i);
                    if (span.has("tags")) {
                        JSONArray tags = span.getJSONArray("tags");
                        for (int j = 0; j < tags.length(); j++) {
                            JSONObject tag = tags.getJSONObject(j);
                            String key = tag.optString("key", "");
                            String value = tag.optString("value", "");
                            
                            if ("http.method".equals(key)) {
                                httpMethods.add(value);
                            } else if ("http.url".equals(key) || "http.target".equals(key)) {
                                endpoints.add(value);
                            }
                        }
                    }
                }
                
                if (!httpMethods.isEmpty()) {
                    context.append("- HTTP Methods Used: ").append(String.join(", ", httpMethods)).append("\n");
                }
                if (!endpoints.isEmpty()) {
                    context.append("- API Endpoints: ").append(endpoints.size()).append(" unique endpoints\n");
                }
            }
            context.append("\n");
            
            // Request LLM analysis
            String prompt = context.toString() + 
                "Provide concise technical analysis:\n" +
                "ROOT CAUSE: Exact failing operation and reason\n" +
                "FIX: Specific action needed\n\n" +
                "Be direct and technical. No conversational language.";
            
            // Get LLM configuration from system properties (set by TestGenerationAndExecution)
            Map<String, String> llmProperties = new HashMap<>();
            llmProperties.put("llm.enabled", System.getProperty("llm.enabled", "true"));
            llmProperties.put("llm.model.type", System.getProperty("llm.model.type", "ollama")); // Default from properties
            
            // Add all LLM-related system properties.
            // Canonical llm.openai_compatible.* keys first; the legacy llm.local.*
            // keys are also forwarded so existing *.properties files keep working.
            llmProperties.put("llm.openai_compatible.enabled", System.getProperty("llm.openai_compatible.enabled", "false"));
            llmProperties.put("llm.openai_compatible.url", System.getProperty("llm.openai_compatible.url", "http://localhost:4891/v1/chat/completions"));
            llmProperties.put("llm.openai_compatible.model", System.getProperty("llm.openai_compatible.model", "llama-3-8b-instruct"));
            llmProperties.put("llm.openai_compatible.api.key", System.getProperty("llm.openai_compatible.api.key", ""));
            llmProperties.put("llm.local.enabled", System.getProperty("llm.local.enabled", "false"));
            llmProperties.put("llm.local.url", System.getProperty("llm.local.url", "http://localhost:4891/v1/chat/completions"));
            llmProperties.put("llm.local.model", System.getProperty("llm.local.model", "llama-3-8b-instruct"));
            llmProperties.put("llm.local.api.key", System.getProperty("llm.local.api.key", ""));
            llmProperties.put("llm.gemini.enabled", System.getProperty("llm.gemini.enabled", "false"));
            llmProperties.put("llm.gemini.api.key", System.getProperty("llm.gemini.api.key", ""));
            llmProperties.put("llm.gemini.model", System.getProperty("llm.gemini.model", "gemini-2.0-flash-exp"));
            llmProperties.put("llm.gemini.api.url", System.getProperty("llm.gemini.api.url", "https://generativelanguage.googleapis.com/v1beta/models"));
            llmProperties.put("llm.ollama.enabled", System.getProperty("llm.ollama.enabled", "true"));
            llmProperties.put("llm.ollama.url", System.getProperty("llm.ollama.url", "http://localhost:11434"));
            llmProperties.put("llm.ollama.model", System.getProperty("llm.ollama.model", "gemma3:4b"));
            llmProperties.put("llm.rate.limit.retry.enabled", System.getProperty("llm.rate.limit.retry.enabled", "true"));
            llmProperties.put("llm.rate.limit.max.retries", System.getProperty("llm.rate.limit.max.retries", "2"));
            
            LLMService llmService = LLMService.getInstance(llmProperties);
            String llmResponse = llmService.generateText(
                "You are a microservice debugging expert. Analyze traces and provide direct technical insights.", 
                prompt
            );
            
            if (llmResponse != null && !llmResponse.trim().isEmpty()) {
                String formatted = formatLLMResponse(llmResponse);
                cache.put(cacheKey, formatted);
                return formatted;
            } else {
                return getFallbackAnalysis(analysis);
            }

        } catch (Exception e) {
            System.err.println("⚠️ LLM analysis failed: " + e.getMessage());
            return getFallbackAnalysis(analysis);
        }
    }

    /**
     * Build a stable cache key for {@link #generateIntelligentAnalysis} from the
     * sorted set of root-cause failure signatures. Two traces with the same set
     * of {@code (service, operation, status, exception)} root-cause failures
     * receive the same diagnosis (and so reuse the cache entry).
     */
    private static String buildIntelligentAnalysisCacheKey(ErrorAnalysisResult analysis) {
        List<String> sigs = new ArrayList<>();
        for (FailedSpan failure : analysis.getRootCauseFailures()) {
            String svc = (failure.getServiceName() == null || failure.getServiceName().isEmpty()) ? "none" : failure.getServiceName();
            String op = (failure.getOperationName() == null || failure.getOperationName().isEmpty()) ? "none" : failure.getOperationName();
            String exc = failure.getExceptionTypes().isEmpty() ? "none" : failure.getExceptionTypes().get(0);
            sigs.add(svc + "|" + op + "|" + failure.getHttpStatusCode() + "|" + exc);
        }
        Collections.sort(sigs);
        return String.join(";;", sigs);
    }
    
    /**
     * Formats the LLM response for better readability
     */
    private static String formatLLMResponse(String llmResponse) {
        StringBuilder formatted = new StringBuilder();
        formatted.append("🔍 ROOT CAUSE ANALYSIS\n");
        formatted.append("─────────────────────\n");
        formatted.append(llmResponse.trim());
        
        return formatted.toString();
    }
    
    /**
     * Provides fallback analysis when LLM is not available
     */
    private static String getFallbackAnalysis(ErrorAnalysisResult analysis) {
        StringBuilder fallback = new StringBuilder();
        fallback.append("🔧 BASIC ANALYSIS (LLM unavailable)\n");
        fallback.append("═══════════════════════════════════════════════════════════════════════════════\n\n");
        
        for (FailedSpan failure : analysis.getRootCauseFailures()) {
            fallback.append("🔍 ROOT CAUSE:\n");
            fallback.append("   Failed Service: ").append(failure.getServiceName()).append("\n");
            fallback.append("   Failed Operation: ").append(failure.getOperationName()).append("\n");
            
            if (!failure.getErrorMessages().isEmpty()) {
                fallback.append("   Exact Error: ").append(failure.getErrorMessages().get(0)).append("\n");
            }
            
            if (!failure.getExceptionTypes().isEmpty()) {
                String exceptionType = failure.getExceptionTypes().get(0);
                fallback.append("   Exception Type: ").append(exceptionType).append("\n");
            }
            
            // Extract exact failing location from stack trace
            if (!failure.getStackTraces().isEmpty()) {
                String stackTrace = failure.getStackTraces().get(0);
                String[] lines = stackTrace.split("\n");
                
                for (String line : lines) {
                    line = line.trim();
                    if (line.startsWith("at ") && 
                        !line.contains("java.lang.") && 
                        !line.contains("org.springframework.") &&
                        !line.contains("sun.reflect.") &&
                        !line.contains("org.apache.")) {
                        fallback.append("   Exact Location: ").append(line).append("\n");
                        break;
                    }
                }
            }
            
            fallback.append("\n🔧 FIX RECOMMENDATIONS:\n");
            if (!failure.getExceptionTypes().isEmpty()) {
                String exceptionType = failure.getExceptionTypes().get(0);
                
                if (exceptionType.contains("NumberFormatException")) {
                    fallback.append("   • Add input validation before parsing numbers\n");
                    fallback.append("   • Trim whitespace from input strings\n");
                    fallback.append("   • Handle invalid number formats gracefully\n");
                } else if (exceptionType.contains("NullPointerException")) {
                    fallback.append("   • Add null checks before object access\n");
                    fallback.append("   • Initialize objects properly\n");
                    fallback.append("   • Use Optional for nullable values\n");
                } else if (exceptionType.contains("TimeoutException")) {
                    fallback.append("   • Check service availability\n");
                    fallback.append("   • Verify network connectivity\n");
                    fallback.append("   • Increase timeout values if needed\n");
                } else if (exceptionType.contains("ValidationException")) {
                    fallback.append("   • Review input validation rules\n");
                    fallback.append("   • Check data format requirements\n");
                    fallback.append("   • Validate request payload structure\n");
                } else {
                    fallback.append("   • Review the failing operation logic\n");
                    fallback.append("   • Check input parameters and data types\n");
                    fallback.append("   • Add proper error handling\n");
                }
            }
            
            fallback.append("\n");
        }
        
        int propagatedCount = analysis.getAllFailedSpans().size() - analysis.getRootCauseFailures().size();
        if (propagatedCount > 0) {
            fallback.append("📊 IMPACT ASSESSMENT:\n");
            fallback.append("   • ").append(propagatedCount).append(" additional services failed due to error propagation\n");
            fallback.append("   • Fix the root cause above to resolve all failures\n\n");
        }
        
        fallback.append("🎯 DEBUGGING GUIDANCE:\n");
        fallback.append("   • Focus on the exact failure location shown above\n");
        fallback.append("   • Check the specific operation that threw the exception\n");
        fallback.append("   • For detailed AI analysis, configure LLM service\n");
        
        return fallback.toString();
    }
}
