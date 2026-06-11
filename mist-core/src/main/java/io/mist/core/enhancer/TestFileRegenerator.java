package io.mist.core.enhancer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Regenerates test files with enhanced parameter values.
 * 
 * This class parses existing test Java files, identifies parameter assignments,
 * and replaces them with LLM-suggested improved values.
 */
public class TestFileRegenerator {
    
    private static final Logger log = LogManager.getLogger(TestFileRegenerator.class);
    
    // Track which tests have been enhanced
    private final Map<String, EnhancementInfo> enhancedTests = new HashMap<>();
    
    /**
     * Regenerate a test file with enhanced parameter values.
     * 
     * When a failedStepIndex is available, replacements are scoped to only the
     * code block for that specific step (delimited by step-title comments emitted
     * by MultiServiceRESTAssuredWriter), preventing cross-step name collisions.
     * 
     * @param testFilePath Path to the Java test file
     * @param testMethodName Name of the test method to modify
     * @param enhancedParams Map of parameter name to new value
     * @param originalFailure Original failure information for Allure attachment
     * @return true if regeneration was successful
     */
    public boolean regenerateTestFile(String testFilePath, String testMethodName,
                                      Map<String, String> enhancedParams,
                                      FailedTestResult originalFailure) {
        
        log.info("🔄 Regenerating test file: {} (method: {})", testFilePath, testMethodName);
        log.info("   Enhanced parameters: {}", enhancedParams);
        
        try {
            Path path = Paths.get(testFilePath);
            if (!Files.exists(path)) {
                log.error("Test file not found: {}", testFilePath);
                return false;
            }
            
            String content = Files.readString(path);
            
            int methodStart = findMethodStart(content, testMethodName);
            if (methodStart < 0) {
                log.error("Could not find test method: {}", testMethodName);
                return false;
            }
            
            int methodEnd = findMethodEnd(content, methodStart);
            if (methodEnd < 0) {
                log.error("Could not find end of test method: {}", testMethodName);
                return false;
            }
            
            String methodContent = content.substring(methodStart, methodEnd);
            String modifiedMethod;
            
            int failedStep = originalFailure.getFailedStepIndex();
            
            // Layer B: build safe param map by stripping structurally locked dependencies
            Map<String, String> safeParams = new LinkedHashMap<>(enhancedParams);
            Set<String> locked = originalFailure.getLockedDependencyParams();
            if (locked != null && !locked.isEmpty()) {
                Iterator<String> it = safeParams.keySet().iterator();
                while (it.hasNext()) {
                    String paramName = it.next();
                    if (locked.contains(paramName)) {
                        log.warn("Pre-regex guard: stripping locked dependency '{}' from replacement map", paramName);
                        it.remove();
                    }
                }
            }
            
            if (failedStep > 0) {
                modifiedMethod = replaceWithinStepBlock(methodContent, failedStep, safeParams);
            } else {
                modifiedMethod = methodContent;
                for (Map.Entry<String, String> param : safeParams.entrySet()) {
                    modifiedMethod = replaceParameterValue(modifiedMethod, param.getKey(), param.getValue());
                }
            }
            
            modifiedMethod = addEnhancementMarker(modifiedMethod, testMethodName, 
                    originalFailure, enhancedParams);
            
            String newContent = content.substring(0, methodStart) + 
                               modifiedMethod + 
                               content.substring(methodEnd);
            
            Files.writeString(path, newContent);
            
            enhancedTests.put(testFilePath + "#" + testMethodName, 
                    new EnhancementInfo(testFilePath, testMethodName, enhancedParams, originalFailure));
            
            log.info("✅ Successfully regenerated test file (step-fenced: {})", failedStep > 0);
            return true;
            
        } catch (IOException e) {
            log.error("Failed to regenerate test file: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Replace parameters only within the code block belonging to the specified step.
     * Step blocks are delimited by step-title comments emitted by the writer, e.g.:
     * {@code // Root 1: ts-travel-service POST /api/v1/... [expect 200]}
     * 
     * The method finds the N-th step comment (matching the failed step's 1-based index)
     * and scopes all regex replacements to the text between that comment and the next
     * step comment (or end of method).
     */
    private String replaceWithinStepBlock(String methodContent, int targetStepIdx,
                                          Map<String, String> enhancedParams) {
        // Step comments follow the pattern: // <label>: <service> <VERB> <path> [expect <status>]
        // Labels are "Root N", "Step N", or hierarchical IDs like "R1.1"
        Pattern stepCommentPattern = Pattern.compile(
                "^\\s*//\\s*(?:Root \\d+|Step \\d+|R\\d+(?:\\.\\d+)*):\\s+.+\\[expect .+\\]",
                Pattern.MULTILINE
        );
        
        Matcher matcher = stepCommentPattern.matcher(methodContent);
        List<Integer> stepStarts = new ArrayList<>();
        while (matcher.find()) {
            stepStarts.add(matcher.start());
        }
        
        if (stepStarts.isEmpty() || targetStepIdx > stepStarts.size()) {
            log.warn("Could not find step {} boundary in method; falling back to global replacement", targetStepIdx);
            for (Map.Entry<String, String> param : enhancedParams.entrySet()) {
                methodContent = replaceParameterValue(methodContent, param.getKey(), param.getValue());
            }
            return methodContent;
        }
        
        // Steps are 1-indexed; stepStarts list is 0-indexed
        int blockStart = stepStarts.get(targetStepIdx - 1);
        int blockEnd = (targetStepIdx < stepStarts.size())
                ? stepStarts.get(targetStepIdx)
                : methodContent.length();
        
        String before = methodContent.substring(0, blockStart);
        String stepBlock = methodContent.substring(blockStart, blockEnd);
        String after = methodContent.substring(blockEnd);
        
        for (Map.Entry<String, String> param : enhancedParams.entrySet()) {
            stepBlock = replaceParameterValue(stepBlock, param.getKey(), param.getValue());
        }
        
        log.info("   Step-fenced replacement: modified chars {}–{} (step {})", blockStart, blockEnd, targetStepIdx);
        return before + stepBlock + after;
    }
    
    /**
     * Replace a parameter value in the method content.
     *
     * <p>CRITICAL: every replacement argument to {@link String#replaceAll} must be wrapped
     * in {@link Matcher#quoteReplacement(String)}.  The replacement argument of
     * {@code replaceAll} treats {@code $} as a backreference ({@code $1}, {@code $2}, ...)
     * and {@code \} as an escape — so an LLM-enhanced value containing e.g. {@code $5},
     * {@code \n}, or a literal backslash would either corrupt the generated Java code or
     * throw {@code IndexOutOfBoundsException}.  {@code Matcher.quoteReplacement} treats
     * the string as a literal, eliminating that hazard.
     */
    private String replaceParameterValue(String methodContent, String paramName, String newValue) {
        String originalContent = methodContent;

        // Pattern 1: JSON body in Java string - \"paramName\":\"oldValue\" (escaped quotes in Java strings)
        // This is the main pattern used in generated test code like: String requestBody1 = "{\"stationList\":\"value\"}";
        //
        // Escape semantics (this MUST be preserved exactly to keep generated Java compilable):
        //   - Pattern (matches `\"` 2-char sequences in the source file):
        //       Java source "\\\\\"" → runtime string `\\"` → regex matches the literal 2-char `\"`.
        //   - Replacement (must INSERT the same 2-char `\"` back into the source file):
        //       Java source  "\\\""  → runtime string `\"`  → after Matcher.quoteReplacement → `\\\"` →
        //                                replaceAll resolves to `\"` in the output file.
        //   The previous "\\\\\"" wrapping in the replacement was inserting `\\"` (3 chars) into the
        //   Java source, which the compiler then read as "one literal backslash + end-of-string",
        //   yielding tens of thousands of `illegal character: '\'` errors per regenerated file.
        String escapedJsonPattern = "\\\\\"" + Pattern.quote(paramName) + "\\\\\"\\s*:\\s*\\\\\"[^\\\\]*\\\\\"";
        String escapedReplacement = "\\\"" + paramName + "\\\":\\\"" + escapeJavaForJson(newValue) + "\\\"";
        methodContent = methodContent.replaceAll(escapedJsonPattern, Matcher.quoteReplacement(escapedReplacement));

        // Pattern 2: Body field assignment - bodyFields.put("paramName", "oldValue")
        String bodyPattern = "bodyFields\\.put\\(\"" + Pattern.quote(paramName) + "\",\\s*\"[^\"]*\"\\)";
        methodContent = methodContent.replaceAll(bodyPattern,
                Matcher.quoteReplacement("bodyFields.put(\"" + paramName + "\", \"" + escapeJava(newValue) + "\")"));

        // Pattern 3: Regular JSON (not escaped) - "paramName":"oldValue"
        String jsonPattern = "\"" + Pattern.quote(paramName) + "\"\\s*:\\s*\"[^\"]*\"";
        methodContent = methodContent.replaceAll(jsonPattern,
                Matcher.quoteReplacement("\"" + paramName + "\":\"" + escapeJava(newValue) + "\""));

        // Pattern 4: Path parameter - pathParams.put("paramName", "oldValue")
        String pathPattern = "pathParams\\.put\\(\"" + Pattern.quote(paramName) + "\",\\s*\"[^\"]*\"\\)";
        methodContent = methodContent.replaceAll(pathPattern,
                Matcher.quoteReplacement("pathParams.put(\"" + paramName + "\", \"" + escapeJava(newValue) + "\")"));

        // Pattern 5: Query parameter - queryParams.put("paramName", "oldValue")
        String queryPattern = "queryParams\\.put\\(\"" + Pattern.quote(paramName) + "\",\\s*\"[^\"]*\"\\)";
        methodContent = methodContent.replaceAll(queryPattern,
                Matcher.quoteReplacement("queryParams.put(\"" + paramName + "\", \"" + escapeJava(newValue) + "\")"));

        // Pattern 7: two-phase per-field capture line — stepParams<N>.put("paramName", "oldValue").
        // Emitted per body field for VERIFIED_VALID harvest; rewrite it so a rescued positive harvests the
        // ENHANCED value, not the stale synthetic placeholder. The stepParams index (group 1) is preserved.
        java.util.regex.Matcher __spMatcher = java.util.regex.Pattern.compile(
                "stepParams(\\d*)\\.put\\(\"" + Pattern.quote(paramName) + "\",\\s*\"[^\"]*\"\\)")
                .matcher(methodContent);
        StringBuilder __spOut = new StringBuilder();
        while (__spMatcher.find()) {
            __spMatcher.appendReplacement(__spOut, Matcher.quoteReplacement(
                    "stepParams" + __spMatcher.group(1) + ".put(\"" + paramName + "\", \"" + escapeJava(newValue) + "\")"));
        }
        __spMatcher.appendTail(__spOut);
        methodContent = __spOut.toString();

        // Pattern 6: Allure parameter reporting
        String allurePattern = "Allure\\.parameter\\(\"🔴 Invalid Parameters\",\\s*\"" +
                Pattern.quote(paramName) + "=[^\"]*\"\\)";
        methodContent = methodContent.replaceAll(allurePattern,
                Matcher.quoteReplacement("Allure.parameter(\"🔴 Invalid Parameters (ENHANCED)\", \"" +
                        paramName + "=" + escapeJava(newValue) + "\")"));

        // Log if any changes were made
        if (!methodContent.equals(originalContent)) {
            log.debug("   Replaced {} with new value", paramName);
        }

        return methodContent;
    }
    
    /**
     * Escape string for use inside a JSON value that's inside a Java string literal.
     * This handles double escaping needed for: String s = "{\"key\":\"value\"}";
     */
    private String escapeJavaForJson(String s) {
        if (s == null) return "";
        // First escape for JSON, then escape for Java string
        return s.replace("\\", "\\\\\\\\")  // Backslash needs 4 escapes in replacement
                .replace("\"", "\\\\\\\"")   // Quote needs escaped quote in JSON, then escaped for Java
                .replace("\n", "\\\\n")
                .replace("\r", "\\\\r")
                .replace("\t", "\\\\t");
    }
    
    /**
     * Add enhancement marker to the test method.
     */
    private String addEnhancementMarker(String methodContent, String testMethodName,
                                       FailedTestResult originalFailure, 
                                       Map<String, String> enhancedParams) {
        
        // Find the position after the method declaration line
        int insertPos = methodContent.indexOf('{') + 1;
        if (insertPos <= 0) return methodContent;
        
        StringBuilder marker = new StringBuilder();
        marker.append("\n        // ═══════════════════════════════════════════════════════════════════════\n");
        marker.append("        // 🔧 ENHANCED TEST - Modified by Test Case Enhancer\n");
        marker.append("        // Original Status: ").append(originalFailure.getActualStatusCode()).append("\n");
        marker.append("        // Enhanced Parameters: ").append(enhancedParams.keySet()).append("\n");
        marker.append("        // ═══════════════════════════════════════════════════════════════════════\n");
        marker.append("        Allure.label(\"enhancement\", \"ENHANCED\");\n");
        marker.append("        Allure.addAttachment(\"📝 Original Failure\", \"text/plain\", ");
        marker.append("\"Original Status: ").append(originalFailure.getActualStatusCode());
        marker.append("\\nOriginal Response: ").append(escapeJava(truncate(originalFailure.getResponseBody(), 200)));
        marker.append("\\nEnhanced Parameters: ").append(escapeJava(enhancedParams.toString())).append("\");\n");
        
        return methodContent.substring(0, insertPos) + marker + methodContent.substring(insertPos);
    }
    
    /**
     * Find the start position of a test method.
     */
    private int findMethodStart(String content, String methodName) {
        // Look for @Test annotation followed by method declaration
        Pattern pattern = Pattern.compile(
                "@Test\\s*(?:\\([^)]*\\))?\\s*\\n\\s*public\\s+void\\s+" + 
                Pattern.quote(methodName) + "\\s*\\([^)]*\\)\\s*(?:throws[^{]+)?\\{",
                Pattern.MULTILINE
        );
        
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.start();
        }
        
        // Fallback: just look for method signature
        Pattern fallback = Pattern.compile(
                "public\\s+void\\s+" + Pattern.quote(methodName) + "\\s*\\(",
                Pattern.MULTILINE
        );
        
        Matcher fallbackMatcher = fallback.matcher(content);
        if (fallbackMatcher.find()) {
            // Go back to find @Test annotation
            int methodSig = fallbackMatcher.start();
            int searchStart = Math.max(0, methodSig - 100);
            String before = content.substring(searchStart, methodSig);
            int testAnnotation = before.lastIndexOf("@Test");
            if (testAnnotation >= 0) {
                return searchStart + testAnnotation;
            }
            return methodSig;
        }
        
        return -1;
    }
    
    /**
     * Find the end position of a method (matching closing brace).
     */
    private int findMethodEnd(String content, int methodStart) {
        int braceCount = 0;
        boolean inMethod = false;
        
        for (int i = methodStart; i < content.length(); i++) {
            char c = content.charAt(i);
            
            if (c == '{') {
                braceCount++;
                inMethod = true;
            } else if (c == '}') {
                braceCount--;
                if (inMethod && braceCount == 0) {
                    return i + 1;  // Include the closing brace
                }
            }
        }
        
        return -1;
    }
    
    /**
     * Escape string for Java string literal.
     */
    private String escapeJava(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
    
    /**
     * Get all tests that were enhanced.
     */
    public Map<String, EnhancementInfo> getEnhancedTests() {
        return new HashMap<>(enhancedTests);
    }
    
    /**
     * Information about an enhanced test.
     */
    public static class EnhancementInfo {
        public final String testFilePath;
        public final String testMethodName;
        public final Map<String, String> enhancedParams;
        public final FailedTestResult originalFailure;
        
        public EnhancementInfo(String testFilePath, String testMethodName,
                              Map<String, String> enhancedParams, 
                              FailedTestResult originalFailure) {
            this.testFilePath = testFilePath;
            this.testMethodName = testMethodName;
            this.enhancedParams = enhancedParams;
            this.originalFailure = originalFailure;
        }
    }
}

