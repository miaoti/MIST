package io.mist.core.smart;

import io.mist.core.analysis.TraceErrorAnalyzer;
import io.mist.llm.LLMService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes trace errors to identify which API parameter caused the failure
 * and determines the reason for the error using LLM assistance.
 * 
 * This class bridges the gap between trace error analysis and parameter 
 * value generation by providing specific feedback about parameter-related failures.
 */
public class ParameterErrorAnalyzer {
    
    private static final Logger log = LogManager.getLogger(ParameterErrorAnalyzer.class);

    /**
     * Jackson annotates JSON deserialization failures with a reference chain that
     * explicitly names the failing field, e.g. {@code ["paramName"]}. The deepest
     * (last-matched) bracketed name is the actual parameter. Used by
     * {@link #extractParameterErrorFromTrace} for deterministic pre-LLM matching.
     */
    private static final Pattern REF_CHAIN_PARAM = Pattern.compile("\\[\"([^\"]+)\"\\]");

    /**
     * Represents the result of parameter error analysis
     */
    public static class ParameterErrorAnalysisResult {
        private final String apiEndpoint;
        private final String httpMethod;
        private final List<ParameterError> identifiedErrors;
        private final boolean hasParameterErrors;
        private final String analysisDetails;
        
        public ParameterErrorAnalysisResult(String apiEndpoint, String httpMethod, 
                                          List<ParameterError> identifiedErrors, String analysisDetails) {
            this.apiEndpoint = apiEndpoint;
            this.httpMethod = httpMethod;
            this.identifiedErrors = new ArrayList<>(identifiedErrors);
            this.hasParameterErrors = !identifiedErrors.isEmpty();
            this.analysisDetails = analysisDetails;
        }
        
        // Getters
        public String getApiEndpoint() { return apiEndpoint; }
        public String getHttpMethod() { return httpMethod; }
        public List<ParameterError> getIdentifiedErrors() { return identifiedErrors; }
        public boolean hasParameterErrors() { return hasParameterErrors; }
        public String getAnalysisDetails() { return analysisDetails; }
        
        @Override
        public String toString() {
            return String.format("ParameterErrorAnalysisResult{endpoint='%s', method='%s', errors=%d, hasErrors=%s}", 
                               apiEndpoint, httpMethod, identifiedErrors.size(), hasParameterErrors);
        }
    }
    
    /**
     * Analyzes trace errors to identify parameter-related failures
     */
    public static ParameterErrorAnalysisResult analyzeParameterErrors(JSONObject trace, 
                                                                     Map<String, String> testParameters,
                                                                     Map<String, String> llmProperties) {
        if (trace == null || !trace.has("spans")) {
            return new ParameterErrorAnalysisResult("", "", Collections.emptyList(), "No trace data available");
        }
        
        // First, get the basic error analysis
        TraceErrorAnalyzer.ErrorAnalysisResult errorAnalysis = TraceErrorAnalyzer.analyzeTrace(trace);
        if (!errorAnalysis.hasErrors()) {
            return new ParameterErrorAnalysisResult("", "", Collections.emptyList(), "No errors found in trace");
        }
        
        // Extract API endpoint information from the trace
        String apiEndpoint = extractApiEndpoint(trace);
        String httpMethod = extractHttpMethod(trace);
        
        if (apiEndpoint.isEmpty()) {
            log.warn("Could not extract API endpoint from trace");
            return new ParameterErrorAnalysisResult("", httpMethod, Collections.emptyList(), 
                                                  "Could not identify API endpoint from trace");
        }
        
        // Analyze each root cause failure to identify parameter-related issues
        List<ParameterError> parameterErrors = new ArrayList<>();
        StringBuilder analysisDetails = new StringBuilder();
        
        for (TraceErrorAnalyzer.FailedSpan failure : errorAnalysis.getRootCauseFailures()) {
            ParameterErrorAnalysisResult spanResult = analyzeSpanForParameterErrors(
                failure, apiEndpoint, httpMethod, testParameters, llmProperties);
            
            parameterErrors.addAll(spanResult.getIdentifiedErrors());
            if (!spanResult.getAnalysisDetails().isEmpty()) {
                analysisDetails.append(spanResult.getAnalysisDetails()).append("\n");
            }
        }
        
        log.info("Parameter error analysis completed for {}: {} parameter errors identified", 
                apiEndpoint, parameterErrors.size());
        
        return new ParameterErrorAnalysisResult(apiEndpoint, httpMethod, parameterErrors, analysisDetails.toString());
    }
    
    /**
     * Analyzes a specific failed span to identify parameter-related errors
     */
    private static ParameterErrorAnalysisResult analyzeSpanForParameterErrors(
            TraceErrorAnalyzer.FailedSpan failure,
            String apiEndpoint, 
            String httpMethod,
            Map<String, String> testParameters,
            Map<String, String> llmProperties) {
        
        List<ParameterError> errors = new ArrayList<>();
        StringBuilder details = new StringBuilder();

        try {
            // Cache lookup: skip LLM if we've already classified this failure signature.
            String cachedException = failure.getExceptionTypes().isEmpty() ? null : failure.getExceptionTypes().get(0);
            String cacheKey = ParameterErrorAnalysisCache.buildKey(
                    failure.getServiceName(),
                    failure.getOperationName(),
                    failure.getHttpStatusCode(),
                    cachedException);
            ParameterErrorAnalysisCache cache = ParameterErrorAnalysisCache.getInstance(
                    System.getProperty("parameter.error.analysis.cache.path", ".mist/parameter-error-analysis-cache.json"));
            Optional<ParameterErrorAnalysisCache.CachedVerdict> cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                ParameterErrorAnalysisCache.CachedVerdict v = cached.get();
                if (v.isParameterError()) {
                    String actualErrorMessage = extractConciseErrorMessage(failure);
                    errors.add(new ParameterError(v.getErrorType(), actualErrorMessage, apiEndpoint, v.getParameterName()));
                    details.append("Cached Classification: Parameter-related error detected\n");
                } else {
                    details.append("Cached Classification: Not a parameter-related error\n");
                }
                return new ParameterErrorAnalysisResult(apiEndpoint, httpMethod, errors, details.toString());
            }

            // Trace-pattern pre-extractor: when the trace error message explicitly
            // names the failing parameter (Jackson "reference chain"), skip the LLM
            // and produce the verdict deterministically.
            ParameterError extracted = extractParameterErrorFromTrace(failure, apiEndpoint);
            if (extracted != null) {
                errors.add(extracted);
                details.append("Trace-Pattern Extraction: Parameter-related error detected\n");
                cache.put(cacheKey, new ParameterErrorAnalysisCache.CachedVerdict(
                        true, extracted.getParameterName(), extracted.getErrorType()));
                return new ParameterErrorAnalysisResult(apiEndpoint, httpMethod, errors, details.toString());
            }

            // Prepare context for LLM analysis
            String errorContext = buildErrorContext(failure, testParameters);

            // Ask LLM to classify if this is a parameter-related error and identify which parameter
            String prompt = String.format(
                "Analyze this API failure to determine if it's caused by an input parameter:\n\n" +
                "%s\n\n" +
                "Task: Determine if this error is caused by a specific input parameter.\n\n" +
                "If YES, respond with:\n" +
                "PARAMETER_ERROR: YES\n" +
                "PARAMETER: <parameter_name>\n" +
                "ERROR_TYPE: <category>\n\n" +
                "If NO (system error, network issue, etc.), respond with:\n" +
                "PARAMETER_ERROR: NO\n\n" +
                "Categories: VALIDATION_ERROR, TYPE_MISMATCH, FORMAT_ERROR, NULL_ERROR, CONSTRAINT_ERROR\n" +
                "Respond ONLY in the specified format.",
                errorContext
            );

            LLMService llmService = LLMService.getInstance(llmProperties);
            String llmResponse = llmService.generateText(
                "You are an API testing expert. Analyze API failures to identify which parameter caused the issue.",
                prompt
            );

            if (llmResponse != null && !llmResponse.trim().isEmpty()) {
                ParameterError paramError = parseParameterErrorFromLLM(llmResponse, apiEndpoint, failure);
                if (paramError != null) {
                    errors.add(paramError);
                    details.append("LLM Classification: Parameter-related error detected\n");
                    cache.put(cacheKey, new ParameterErrorAnalysisCache.CachedVerdict(
                            true, paramError.getParameterName(), paramError.getErrorType()));
                } else {
                    details.append("LLM Classification: Not a parameter-related error\n");
                    // Only cache an explicit "NO" verdict — leave malformed/empty responses
                    // uncached so future tries can produce a clean answer.
                    String yesNo = extractField(llmResponse, "PARAMETER_ERROR:");
                    if (yesNo != null && yesNo.trim().equalsIgnoreCase("NO")) {
                        cache.put(cacheKey, new ParameterErrorAnalysisCache.CachedVerdict(false, null, null));
                    }
                }
            } else {
                // Fallback analysis if LLM is unavailable
                ParameterError fallbackError = performFallbackAnalysis(failure, apiEndpoint, testParameters);
                if (fallbackError != null) {
                    errors.add(fallbackError);
                    details.append("Fallback Analysis: Pattern-based error detection\n");
                }
            }

        } catch (Exception e) {
            log.warn("Error during parameter error analysis for {}: {}", apiEndpoint, e.getMessage());
            details.append("Analysis failed: ").append(e.getMessage()).append("\n");
        }
        
        return new ParameterErrorAnalysisResult(apiEndpoint, httpMethod, errors, details.toString());
    }
    
    /**
     * Builds context for LLM analysis
     */
    private static String buildErrorContext(TraceErrorAnalyzer.FailedSpan failure, Map<String, String> testParameters) {
        StringBuilder context = new StringBuilder();
        
        context.append("FAILED API CALL:\n");
        context.append("Service: ").append(failure.getServiceName()).append("\n");
        context.append("Operation: ").append(failure.getOperationName()).append("\n");
        context.append("HTTP Status: ").append(failure.getHttpStatusCode()).append("\n");
        context.append("Duration: ").append(failure.getDuration() / 1000.0).append("ms\n\n");
        
        // Add error messages
        if (!failure.getErrorMessages().isEmpty()) {
            context.append("ERROR MESSAGES:\n");
            for (String msg : failure.getErrorMessages()) {
                context.append("- ").append(msg).append("\n");
            }
            context.append("\n");
        }
        
        // Add exception information
        if (!failure.getExceptionTypes().isEmpty()) {
            context.append("EXCEPTION TYPE: ").append(failure.getExceptionTypes().get(0)).append("\n\n");
        }
        
        // Add stack trace (first few lines)
        if (!failure.getStackTraces().isEmpty()) {
            context.append("STACK TRACE (first few lines):\n");
            String[] lines = failure.getStackTraces().get(0).split("\n");
            int linesToShow = Math.min(3, lines.length);
            for (int i = 0; i < linesToShow; i++) {
                context.append(lines[i].trim()).append("\n");
            }
            context.append("\n");
        }
        
        // Add test parameters
        if (testParameters != null && !testParameters.isEmpty()) {
            context.append("TEST PARAMETERS USED:\n");
            for (Map.Entry<String, String> param : testParameters.entrySet()) {
                context.append("- ").append(param.getKey()).append(": ").append(param.getValue()).append("\n");
            }
        }
        
        return context.toString();
    }
    
    /**
     * Deterministic pre-LLM extractor: when Jackson's "reference chain" annotation
     * is present, the failing parameter is named explicitly in the error text.
     * Returns a {@link ParameterError} on confident match, or {@code null} to let
     * the caller fall through to the LLM.
     * <p>
     * Categorization is intentionally coarse: numeric "out of range" maps to
     * {@code CONSTRAINT_ERROR}; everything else with a reference chain maps to
     * {@code TYPE_MISMATCH} (Jackson's most common failure mode).
     */
    private static ParameterError extractParameterErrorFromTrace(
            TraceErrorAnalyzer.FailedSpan failure, String apiEndpoint) {
        if (failure.getErrorMessages().isEmpty()) {
            return null;
        }
        String errorText = String.join(" ", failure.getErrorMessages());

        int chainAt = errorText.indexOf("reference chain");
        if (chainAt < 0) {
            return null;
        }

        // Bound the search to the "(through reference chain: ...)" block so that
        // any bracketed strings appearing after it can't pollute the match.
        int chainEnd = errorText.indexOf(')', chainAt);
        String chain = (chainEnd > chainAt) ? errorText.substring(chainAt, chainEnd) : errorText.substring(chainAt);

        // Successive ["param"] tokens form the deserialization path; the deepest
        // (last-matched) name is the actual failing field.
        Matcher m = REF_CHAIN_PARAM.matcher(chain);
        String paramName = null;
        while (m.find()) {
            paramName = m.group(1);
        }
        if (paramName == null) {
            return null;
        }

        String errorType = errorText.toLowerCase().contains("out of range")
                ? "CONSTRAINT_ERROR"
                : "TYPE_MISMATCH";
        return new ParameterError(errorType, extractConciseErrorMessage(failure), apiEndpoint, paramName);
    }

    /**
     * Parses parameter error information from LLM response and extracts actual error message
     */
    private static ParameterError parseParameterErrorFromLLM(String llmResponse, String apiEndpoint,
                                                            TraceErrorAnalyzer.FailedSpan failure) {
        try {
            // Check if LLM identified this as a parameter error
            String isParameterError = extractField(llmResponse, "PARAMETER_ERROR:");
            if (isParameterError == null || !isParameterError.trim().equalsIgnoreCase("YES")) {
                return null; // Not a parameter-related error
            }
            
            String parameter = extractField(llmResponse, "PARAMETER:");
            String errorType = extractField(llmResponse, "ERROR_TYPE:");
            
            if (parameter != null && errorType != null && !parameter.trim().isEmpty()) {
                // Extract actual error message from the trace (not LLM-generated description)
                String actualErrorMessage = extractConciseErrorMessage(failure);
                
                return new ParameterError(errorType.trim(), actualErrorMessage, apiEndpoint, parameter.trim());
            }
            
        } catch (Exception e) {
            log.debug("Error parsing LLM response for parameter error: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Extracts field value from LLM response
     */
    private static String extractField(String response, String fieldName) {
        String[] lines = response.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith(fieldName)) {
                return line.substring(fieldName.length()).trim();
            }
        }
        return null;
    }
    
    /**
     * Performs fallback analysis when LLM is unavailable
     */
    private static ParameterError performFallbackAnalysis(TraceErrorAnalyzer.FailedSpan failure, 
                                                        String apiEndpoint, Map<String, String> testParameters) {
        
        // Common error patterns and their likely parameter causes
        Map<Pattern, String[]> errorPatterns = new HashMap<>();
        
        // Format: Pattern -> [ErrorType, ParameterHint, ReasonTemplate]
        errorPatterns.put(Pattern.compile(".*NumberFormatException.*", Pattern.CASE_INSENSITIVE),
                         new String[]{"VALIDATION_ERROR", "numeric", "Invalid number format"});
        
        errorPatterns.put(Pattern.compile(".*IllegalArgumentException.*", Pattern.CASE_INSENSITIVE),
                         new String[]{"VALIDATION_ERROR", "any", "Illegal argument value"});
        
        errorPatterns.put(Pattern.compile(".*NullPointerException.*", Pattern.CASE_INSENSITIVE),
                         new String[]{"NULL_ERROR", "any", "Missing required parameter"});
        
        errorPatterns.put(Pattern.compile(".*ValidationException.*", Pattern.CASE_INSENSITIVE),
                         new String[]{"VALIDATION_ERROR", "any", "Parameter validation failed"});
        
        errorPatterns.put(Pattern.compile(".*400.*|.*Bad Request.*", Pattern.CASE_INSENSITIVE),
                         new String[]{"BAD_REQUEST", "any", "Invalid parameter value"});
        
        // Check error messages and exception types
        String fullErrorText = String.join(" ", failure.getErrorMessages()) + " " + 
                              String.join(" ", failure.getExceptionTypes());
        
        for (Map.Entry<Pattern, String[]> patternEntry : errorPatterns.entrySet()) {
            if (patternEntry.getKey().matcher(fullErrorText).find()) {
                String[] errorInfo = patternEntry.getValue();
                String errorType = errorInfo[0];
                String parameterHint = errorInfo[1];
                String reason = errorInfo[2];
                
                // Try to identify the most likely parameter
                String likelyParameter = identifyLikelyParameter(testParameters, parameterHint, fullErrorText);
                
                if (likelyParameter != null) {
                    // Use actual error message from trace, not generic reason
                    String actualErrorMessage = extractConciseErrorMessage(failure);
                    return new ParameterError(errorType, actualErrorMessage, apiEndpoint, likelyParameter);
                }
                
                break;
            }
        }
        
        return null;
    }
    
    /**
     * Identifies the most likely parameter based on hints and error text
     */
    private static String identifyLikelyParameter(Map<String, String> testParameters, 
                                                String parameterHint, String errorText) {
        if (testParameters == null || testParameters.isEmpty()) {
            return null;
        }
        
        // For numeric errors, look for numeric parameters
        if ("numeric".equals(parameterHint)) {
            for (Map.Entry<String, String> param : testParameters.entrySet()) {
                String value = param.getValue();
                if (value != null && (value.matches("\\d+") || value.matches("\\d*\\.\\d+"))) {
                    return param.getKey();
                }
            }
        }
        
        // Look for parameter names mentioned in error text
        for (String paramName : testParameters.keySet()) {
            if (errorText.toLowerCase().contains(paramName.toLowerCase())) {
                return paramName;
            }
        }
        
        // Return the first parameter as a fallback
        return testParameters.keySet().iterator().next();
    }
    
    /**
     * Extracts API endpoint from trace
     */
    private static String extractApiEndpoint(JSONObject trace) {
        try {
            JSONArray spans = trace.getJSONArray("spans");
            for (int i = 0; i < spans.length(); i++) {
                JSONObject span = spans.getJSONObject(i);
                if (span.has("tags")) {
                    JSONArray tags = span.getJSONArray("tags");
                    for (int j = 0; j < tags.length(); j++) {
                        JSONObject tag = tags.getJSONObject(j);
                        String key = tag.optString("key", "");
                        String value = tag.optString("value", "");
                        
                        if ("http.url".equals(key) || "http.target".equals(key)) {
                            // Extract path from full URL — skip "protocol://host" prefix.
                            // indexOf returns -1 when there is no path component (e.g. "http://host"),
                            // so guard explicitly rather than relying on substring's exception.
                            if (value.startsWith("http")) {
                                int pathStart = value.indexOf('/', 8);
                                return pathStart >= 0 ? value.substring(pathStart) : value;
                            }
                            return value;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting API endpoint from trace: {}", e.getMessage());
        }
        
        return "";
    }
    
    /**
     * Extracts HTTP method from trace
     */
    private static String extractHttpMethod(JSONObject trace) {
        try {
            JSONArray spans = trace.getJSONArray("spans");
            for (int i = 0; i < spans.length(); i++) {
                JSONObject span = spans.getJSONObject(i);
                if (span.has("tags")) {
                    JSONArray tags = span.getJSONArray("tags");
                    for (int j = 0; j < tags.length(); j++) {
                        JSONObject tag = tags.getJSONObject(j);
                        if ("http.method".equals(tag.optString("key", ""))) {
                            return tag.optString("value", "");
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting HTTP method from trace: {}", e.getMessage());
        }
        
        return "";
    }
    
    /**
     * Extracts a concise but complete error message from the actual trace data
     */
    private static String extractConciseErrorMessage(TraceErrorAnalyzer.FailedSpan failure) {
        // Priority order: Exception message > Error message > HTTP status
        String errorMessage = null;
        
        // 1. Try to get the most specific exception message
        if (!failure.getErrorMessages().isEmpty()) {
            errorMessage = failure.getErrorMessages().get(0);
        }
        
        // 2. If no error message, try to extract from stack trace
        if (errorMessage == null && !failure.getStackTraces().isEmpty()) {
            errorMessage = extractMessageFromStackTrace(failure.getStackTraces().get(0));
        }
        
        // 3. If still no message, use exception type + HTTP status
        if (errorMessage == null) {
            String exceptionType = failure.getExceptionTypes().isEmpty() ? "Unknown" : failure.getExceptionTypes().get(0);
            errorMessage = exceptionType + " (HTTP " + failure.getHttpStatusCode() + ")";
        }
        
        // Clean and make concise while preserving essential information
        return makeConciseButComplete(errorMessage);
    }
    
    /**
     * Extracts meaningful error message from stack trace
     */
    private static String extractMessageFromStackTrace(String stackTrace) {
        if (stackTrace == null || stackTrace.trim().isEmpty()) {
            return null;
        }
        
        String[] lines = stackTrace.split("\n");
        for (String line : lines) {
            line = line.trim();
            // Look for meaningful exception lines (not just at/caused by)
            if (!line.isEmpty() && !line.startsWith("at ") && !line.startsWith("Caused by:") && 
                (line.contains("Exception") || line.contains("Error") || line.contains(":"))) {
                
                // Extract just the message part if it's an exception line
                int colonIndex = line.indexOf(":");
                if (colonIndex > 0 && colonIndex < line.length() - 1) {
                    return line.substring(colonIndex + 1).trim();
                }
                return line;
            }
        }
        return null;
    }
    
    /**
     * Makes error message concise but preserves all essential information
     */
    private static String makeConciseButComplete(String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            return "Unknown error";
        }
        
        errorMessage = errorMessage.trim();
        
        // Remove excessive whitespace and line breaks
        errorMessage = errorMessage.replaceAll("\\s+", " ");
        
        // Extract the core error message (first sentence or up to first major punctuation)
        String[] sentences = errorMessage.split("[.!;]");
        String coreMessage = sentences[0].trim();
        
        // If the core message is too short, try to include more context
        if (coreMessage.length() < 20 && sentences.length > 1) {
            coreMessage = (coreMessage + ". " + sentences[1].trim()).trim();
        }
        
        // Limit to reasonable length but don't cut off mid-word
        if (coreMessage.length() > 150) {
            int lastSpace = coreMessage.lastIndexOf(' ', 147);
            if (lastSpace > 50) {
                coreMessage = coreMessage.substring(0, lastSpace) + "...";
            } else {
                coreMessage = coreMessage.substring(0, 147) + "...";
            }
        }
        
        return coreMessage;
    }
    
}
