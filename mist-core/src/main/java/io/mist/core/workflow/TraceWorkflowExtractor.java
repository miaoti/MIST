package io.mist.core.workflow;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.mist.core.config.MstConfig;
import io.mist.core.workflow.NounKeyMap;
import io.mist.core.workflow.WorkflowScenario;
import io.mist.core.workflow.WorkflowStep;

/**
 * Utility class to extract workflow scenarios from OpenTelemetry trace data.
 *
 * The extractor reads a JSON or JSONL (one JSON object per line) file containing span data,
 * then reconstructs hierarchical {@link WorkflowScenario} instances. It handles missing fields,
 * orders spans by parent-child relationships and start times, and infers cross-trace dependencies
 * by matching output fields from one trace to input fields of another.
 *
 * This class is designed for robust operation: it tolerates missing optional data and can merge
 * multiple traces into a single scenario if data dependencies (e.g., an ID in a response used in another request) are detected.
 * Future enhancements could include using OpenTelemetry span links for asynchronous propagation
 * or inserting special {@link WorkflowStep} types for events like message queues or fault injection.
 */
public class TraceWorkflowExtractor {
    private static final Logger log = LogManager.getLogger(TraceWorkflowExtractor.class);

    /**
     * Reads OpenTelemetry spans from a JSON/JSONL file or directory and extracts logical workflow scenarios.
     * Each scenario is a sequence of steps (potentially across multiple services) representing an end-to-end workflow.
     *
     * @param fileOrDirPath the path to the JSON/JSONL file or directory containing trace files
     * @return a list of {@link WorkflowScenario} objects reconstructed from the trace data
     * @throws IOException if an I/O error occurs reading the file(s)
     * @throws IllegalArgumentException if the file content is not valid JSON/span format
     */
    public static List<WorkflowScenario> extractScenarios(String fileOrDirPath) throws IOException {
        File path = new File(fileOrDirPath);
        List<WorkflowScenario> allScenarios = new ArrayList<>();
        
        if (path.isDirectory()) {
            // Process all JSON files in the directory
            File[] jsonFiles = path.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".json") || name.toLowerCase().endsWith(".jsonl"));
            
            if (jsonFiles == null || jsonFiles.length == 0) {
                log.warn("No JSON/JSONL files found in directory: " + fileOrDirPath);
                return allScenarios;
            }
            
            log.info("Processing {} trace files from directory: {}", jsonFiles.length, fileOrDirPath);
            for (File jsonFile : jsonFiles) {
                log.info("Processing trace file: {}", jsonFile.getName());
                try {
                    // Extract file name without extension for scenario naming
                    String fileName = jsonFile.getName();
                    String fileNameWithoutExt = fileName.lastIndexOf('.') != -1 ? 
                        fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
                    
                    List<WorkflowScenario> fileScenarios = extractScenariosFromFile(jsonFile.getPath(), fileNameWithoutExt);
                    allScenarios.addAll(fileScenarios);
                } catch (Exception e) {
                    log.error("Error processing trace file {}: {}", jsonFile.getName(), e.getMessage());
                    // Continue processing other files
                }
            }
        } else {
            // Single file processing. A non-trace JSON dropped in as trace.file.path
            // (a MANIFEST.json, a config, or a Jaeger *search* export) must NOT abort
            // the whole run — skip it with a clear message, mirroring the directory
            // branch's per-file resilience above.
            File singleFile = new File(fileOrDirPath);
            String fileName = singleFile.getName();
            String fileNameWithoutExt = fileName.lastIndexOf('.') != -1 ?
                fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            try {
                allScenarios = extractScenariosFromFile(fileOrDirPath, fileNameWithoutExt);
            } catch (Exception e) {
                log.error("Trace file '{}' is not a recognized Jaeger/OTel trace ({}); skipping. "
                        + "Point trace.file.path at a directory of trace files or a valid trace JSON.",
                        fileName, e.getMessage());
                allScenarios = new ArrayList<>();
            }
        }
        
        return allScenarios;
    }
    
    /**
     * Internal method to extract scenarios from a single file.
     */
    private static List<WorkflowScenario> extractScenariosFromFile(String filePath, String sourceFileName) throws IOException {
        List<JSONObject> spanObjects = new ArrayList<>();


        // Read the file and parse JSON content
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            // Peek at the first non-empty line to determine format (JSON array vs JSON Lines)
            String firstLine = null;
            while ((firstLine = reader.readLine()) != null && firstLine.trim().isEmpty()) {
                // skip empty lines at start
            }
            if (firstLine == null) {
                throw new IllegalArgumentException("Input file is empty or contains no JSON content.");
            }
            String trimmed = firstLine.trim();
            if (trimmed.startsWith("[")) {
                StringBuilder sb = new StringBuilder(trimmed);
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }


                JSONArray spanArray = new JSONArray(sb.toString());
                for (int i = 0; i < spanArray.length(); i++) {
                    if (spanArray.get(i) instanceof JSONObject) {
                        spanObjects.add(spanArray.getJSONObject(i));
                    }
                }

            } else if (trimmed.startsWith("{")) {
                // NEW: potentially a wrapper object
                // Read the whole file into a single string
                StringBuilder sb = new StringBuilder(trimmed);
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                JSONObject root = new JSONObject(sb.toString());
                if (root.has("data")) {
                    // Flatten each data[i].spans[] into spanObjects
                    JSONArray dataArr = root.getJSONArray("data");
                    for (int i = 0; i < dataArr.length(); i++) {
                        JSONObject bucket = dataArr.getJSONObject(i);
                        // grab the map of processID -> { serviceName, tags, … }
                        JSONObject processes = bucket.optJSONObject("processes");

                        JSONArray spans = bucket.optJSONArray("spans");
                        if (spans != null) {
                            for (int j = 0; j < spans.length(); j++) {
                                JSONObject spanObj = spans.getJSONObject(j);

                                // === NEW: pluck serviceName out of the processes map ===
                                if (processes != null) {
                                    String procId = spanObj.optString("processID", null);
                                    if (procId != null && processes.has(procId)) {
                                        JSONObject procInfo = processes.getJSONObject(procId);
                                        String svc = procInfo.optString("serviceName", null);
                                        if (svc != null && !svc.isEmpty()) {
                                            spanObj.put("serviceName", svc);
                                        }
                                    }
                                }

                                spanObjects.add(spanObj);
                            }
                        }
                    }
                } else if (root.has("spans")) {
                    // Top-level "spans": rare but possible
                    JSONArray spans = root.getJSONArray("spans");
                    for (int j = 0; j < spans.length(); j++) {
                        spanObjects.add(spans.getJSONObject(j));
                    }
                } else {
                    throw new IllegalArgumentException("Unrecognized JSON object format");
                }

            } else {
                // The file is newline-delimited JSON (JSONL): each line is a separate JSON object
                try {
                    // Parse the first line as a JSON object
                    spanObjects.add(new JSONObject(firstLine));
                } catch (JSONException e) {
                    throw new IllegalArgumentException("Failed to parse JSON object on line 1: " + e.getMessage(), e);
                }
                String line;
                int lineNum = 1;
                while ((line = reader.readLine()) != null) {
                    lineNum++;
                    String jsonLine = line.trim();
                    if (jsonLine.isEmpty()) continue; // skip blank lines between entries
                    try {
                        spanObjects.add(new JSONObject(jsonLine));
                    } catch (JSONException e) {
                        // Log the error and skip this line, but continue processing other lines
                        log.error("Invalid JSON span on line " + lineNum + " - skipping. Error: " + e.getMessage());
                    }
                }
            }
        }

        // Group spans by traceId
        Map<String, List<JSONObject>> spansByTrace = new HashMap<>();
        for (JSONObject spanObj : spanObjects) {
            // Extract required fields from the JSON object
            String traceId = spanObj.optString("traceId",
                    spanObj.optString("traceID", null));
            if (traceId == null || traceId.isEmpty()) {
                log.warn("Encountered span with missing traceId, skipping: " + spanObj);
                continue;
            }
            // Group by trace
            spansByTrace.computeIfAbsent(traceId, k -> new ArrayList<>()).add(spanObj);
        }

        List<WorkflowScenario> scenarios = new ArrayList<>();
        // Process each trace group into a WorkflowScenario
        for (Map.Entry<String, List<JSONObject>> entry : spansByTrace.entrySet()) {
            String traceId = entry.getKey();
            List<JSONObject> spans = entry.getValue();
            if (spans.isEmpty()) continue;

            // Create a new scenario for this trace
            WorkflowScenario scenario = new WorkflowScenario();
            // Set the source file name for meaningful test naming
            scenario.setSourceFileName(sourceFileName);
            // Temporary map to hold spanId -> WorkflowStep for linking parent/child
            Map<String, WorkflowStep> stepIndex = new HashMap<>();

            // First pass: create WorkflowStep for each span (without parent/child links yet)
            for (JSONObject spanObj : spans) {
                // Basic span info
                String spanId  = spanObj.optString("spanId",
                        spanObj.optString("spanID", null));
                if (spanId == null || spanId.isEmpty()) {
                    // If spanId missing, skip this span
                    log.warn("Skipping span in trace " + traceId + " due to missing spanId.");
                    continue;
                }
                String parentSpanId = spanObj.has("parentSpanId")
                        ? spanObj.optString("parentSpanId", null)
                        : null;

                if (parentSpanId == null && spanObj.has("references")) {
                    JSONArray refs = spanObj.optJSONArray("references");
                    if (refs != null) {
                        for (int k = 0; k < refs.length(); k++) {
                            JSONObject ref = refs.getJSONObject(k);
                            if ("CHILD_OF".equals(ref.optString("refType"))) {
                                parentSpanId = ref.optString("spanID", null);
                                break;
                            }
                        }
                    }
                }

                if (parentSpanId != null && parentSpanId.isEmpty()) {
                    parentSpanId = null; // normalize empty parent ID to null
                }
                String serviceName = spanObj.optString("serviceName", null);
                if (serviceName == null || serviceName.isEmpty()) {
                    serviceName = "unknown";
                }
                // Operation name or fallback to HTTP method + URL
                String operation = spanObj.optString("operationName", null);
                if (operation == null || operation.isEmpty()) {
                    String method = null, url = null;
                    JSONObject attrs = spanObj.optJSONObject("attributes");
                    if (attrs != null) {
                        method = attrs.optString("http.method", null);
                        url = attrs.optString("http.url", null);
                    }
                    if (method != null || url != null) {
                        // Construct a placeholder operation name from method and URL/path
                        String partA = (method != null ? method : "");
                        String partB = (url != null ? url : "");
                        operation = (partA + " " + partB).trim();
                    }
                    if (operation == null || operation.isEmpty()) {
                        operation = "unknown";
                    }
                }
                // Start and end times (as long, microseconds since epoch).
                // Jaeger uses startTime + duration; we compute endTime from them.
                long startTime = -1L;
                long endTime = -1L;
                try {
                    if (spanObj.has("startTime")) {
                        startTime = spanObj.getLong("startTime");
                    }
                } catch (Exception e) {
                    String startStr = spanObj.optString("startTime", null);
                    if (startStr != null) {
                        try { startTime = Long.parseLong(startStr); } catch (NumberFormatException nfe) { }
                    }
                }
                try {
                    if (spanObj.has("endTime")) {
                        endTime = spanObj.getLong("endTime");
                    }
                } catch (Exception e) {
                    String endStr = spanObj.optString("endTime", null);
                    if (endStr != null) {
                        try { endTime = Long.parseLong(endStr); } catch (NumberFormatException nfe) { }
                    }
                }
                // Jaeger format: startTime + duration (no explicit endTime)
                if (endTime < 0 && startTime > 0 && spanObj.has("duration")) {
                    try {
                        long duration = spanObj.getLong("duration");
                        if (duration >= 0) endTime = startTime + duration;
                    } catch (Exception ignored) { }
                }

                // Parse attributes for input/output data
                JSONObject attributes = spanObj.optJSONObject("attributes");
                if (attributes == null) attributes = spanObj.optJSONObject("tags");
                
                // Also handle tags as an array (OpenTelemetry format)
                JSONArray tagsArray = spanObj.optJSONArray("tags");
                Map<String, String> tagMap = new HashMap<>();
                if (tagsArray != null) {
                    for (int i = 0; i < tagsArray.length(); i++) {
                        JSONObject tag = tagsArray.optJSONObject(i);
                        if (tag != null) {
                            String key = tag.optString("key", null);
                            String value = tag.optString("value", null);
                            if (key != null && value != null) {
                                tagMap.put(key, value);
                            }
                        }
                    }
                }
                
                String httpMethod = null;
                String httpUrl = null;
                String httpTarget = null;
                String requestBody = null;
                String responseBody = null;
                String statusCode = null;
                
                if (attributes != null) {
                    httpMethod = attributes.optString("http.method", null);
                    httpUrl = attributes.optString("http.url", null);
                    httpTarget = attributes.optString("http.target", null);
                    requestBody = attributes.optString("http.request.body", null);
                    responseBody = attributes.optString("http.response.body", null);
                    // status code might be numeric or string in JSON; handle both
                    Object statusObj = attributes.opt("http.status_code");
                    if (statusObj != null) {
                        statusCode = statusObj.toString();
                    }
                }
                
                // Override with tag array values if available
                if (tagMap.containsKey("http.method")) httpMethod = tagMap.get("http.method");
                if (tagMap.containsKey("http.url")) httpUrl = tagMap.get("http.url");
                if (tagMap.containsKey("http.target")) httpTarget = tagMap.get("http.target");
                if (tagMap.containsKey("http.request.body")) requestBody = tagMap.get("http.request.body");
                if (tagMap.containsKey("http.response.body")) responseBody = tagMap.get("http.response.body");
                if (tagMap.containsKey("http.status_code")) statusCode = tagMap.get("http.status_code");

                // Prepare maps for input and output fields
                Map<String, String> inputFields = new HashMap<>();
                Map<String, String> outputFields = new HashMap<>();
                
                // Include HTTP method and URL information in input fields for generator access
                if (httpMethod != null && !httpMethod.isEmpty()) {
                    inputFields.put("http.method", httpMethod);
                }
                if (httpUrl != null && !httpUrl.isEmpty()) {
                    inputFields.put("http.url", httpUrl);
                }
                if (httpTarget != null && !httpTarget.isEmpty()) {
                    inputFields.put("http.target", httpTarget);
                }
                
                // Parse request inputs (body and query params)
                if (requestBody != null && !requestBody.isEmpty()) {
                    inputFields.put("http.request.body", requestBody);
                    extractFieldsFromContent(requestBody, inputFields);
                }
                if (httpUrl != null && !httpUrl.isEmpty()) {
                    // If URL contains query parameters, extract them
                    int qMark = httpUrl.indexOf('?');
                    if (qMark >= 0 && qMark < httpUrl.length() - 1) {
                        String queryString = httpUrl.substring(qMark + 1);
                        extractFieldsFromContent(queryString, inputFields);
                    }
                }

                // Extract ID-like path parameters from URLs into input fields.
                // URL path IDs are inputs (the client already knew the ID), not outputs.
                String urlForPathExtraction = httpTarget != null ? httpTarget : httpUrl;
                if (urlForPathExtraction != null && !urlForPathExtraction.isEmpty()) {
                    extractFieldsFromUrl(urlForPathExtraction, inputFields);
                }

                // Parse response outputs (body)
                if (responseBody != null && !responseBody.isEmpty()) {
                    outputFields.put("http.response.body", responseBody);
                    extractFieldsFromContent(responseBody, outputFields);
                }
                if (statusCode != null && !statusCode.isEmpty()) {
                    // Include status code in output fields (could be useful in scenario verification, though not for linking)
                    outputFields.put("http.status_code", statusCode);
                }
                
                // Include HTTP method and URL in output fields as well for generator access
                if (httpMethod != null && !httpMethod.isEmpty()) {
                    outputFields.put("http.method", httpMethod);
                }
                if (httpUrl != null && !httpUrl.isEmpty()) {
                    outputFields.put("http.url", httpUrl);
                }
                if (httpTarget != null && !httpTarget.isEmpty()) {
                    outputFields.put("http.target", httpTarget);
                }

                // Create the WorkflowStep object for this span
                WorkflowStep step = new WorkflowStep(traceId, spanId, serviceName, operation, startTime, endTime,
                        inputFields, outputFields);
                // Temporarily store parent ID for linking
                stepIndex.put(spanId, step);
                // We can use the parentSpanId later to link; for now we might store it via an intermediate structure
                // (Alternatively, we could store it in a field of WorkflowStep, but we chose not to to keep the model clean.)
                step.setParentSpanIdTemp(parentSpanId);  // Pseudo-call: see below for handling parentSpanId linking
            }

            // Second pass: link children to parents within this trace
            for (WorkflowStep step : new ArrayList<>(stepIndex.values())) {
                String parentSpanId = step.getParentSpanIdTemp();  // Retrieve stored parentSpanId
                if (parentSpanId != null) {
                    WorkflowStep parentStep = stepIndex.get(parentSpanId);
                    if (parentStep != null) {
                        // Link this step as a child of its parent
                        parentStep.addChild(step);
                    } else {
                        // Parent span not found in this trace (possibly span data is incomplete)
                        log.warn("Trace " + traceId + ": Parent span " + parentSpanId + " not found for span " + step.getSpanId()
                                + ". Treating as root in scenario.");
                    }
                }
            }

            // Determine roots (spans with no parent in this scenario)
            for (WorkflowStep step : stepIndex.values()) {
                if (step.getParent() == null) {
                    scenario.addRootStep(step);
                }
            }
            // Sort children of each step by startTime to maintain chronological order among siblings
            for (WorkflowStep step : stepIndex.values()) {
                step.sortChildrenByStartTime();
            }

            // --- Compute session identifier and time bounds from spans ---
            long minStart = Long.MAX_VALUE;
            long maxEnd = Long.MIN_VALUE;
            String sessionId = null;
            for (JSONObject spanObj : spans) {
                long st = -1L;
                try { st = spanObj.getLong("startTime"); } catch (Exception ignored) { }
                long dur = 0;
                try { dur = spanObj.getLong("duration"); } catch (Exception ignored) { }
                long en = st > 0 ? st + dur : -1L;

                if (st > 0 && st < minStart) minStart = st;
                if (en > 0 && en > maxEnd) maxEnd = en;

                if (sessionId == null) {
                    // Prefer http.client_ip; fallback to user_agent.original as a weaker signal
                    JSONArray tags = spanObj.optJSONArray("tags");
                    if (tags != null) {
                        for (int ti = 0; ti < tags.length(); ti++) {
                            JSONObject tag = tags.optJSONObject(ti);
                            if (tag == null) continue;
                            String key = tag.optString("key", "");
                            if ("http.client_ip".equals(key)) {
                                sessionId = tag.optString("value", null);
                                break;
                            }
                        }
                    }
                    // Also check flat attributes object
                    if (sessionId == null) {
                        JSONObject attrs = spanObj.optJSONObject("attributes");
                        if (attrs != null) {
                            sessionId = attrs.optString("http.client_ip", null);
                        }
                    }
                }
            }
            scenario.setSessionIdentifier(sessionId != null && !sessionId.isEmpty() ? sessionId : "UNKNOWN_SESSION");
            scenario.setStartTimeMicros(minStart == Long.MAX_VALUE ? -1L : minStart);
            scenario.setEndTimeMicros(maxEnd == Long.MIN_VALUE ? -1L : maxEnd);

            // Record this scenario
            scenario.addTraceId(traceId);
            scenarios.add(scenario);
        }

        // Phase 1: Merge scenarios that have explicit cross-trace data dependencies
        // (requires http.response.body in spans — may be a no-op if bodies are absent)
        mergeScenariosByDataDependency(scenarios);

        // Phase 2: Heuristic session-based merge for traces that share the same
        // client IP and are temporally adjacent (covers the common case where OTel
        // does not capture response/request bodies).
        MstConfig.ScenarioMerge mergeCfg = MstConfig.instance().scenarioMerge();
        long maxGapMicros = mergeCfg.maxSessionGapMicros();
        int maxRootsPerScenario = mergeCfg.maxRootsPerScenario();
        mergeScenariosBySessionTimeWindow(scenarios, maxGapMicros, maxRootsPerScenario);

        return scenarios;
    }

    /**
     * Helper method to parse a content string (JSON or URL-encoded or plain) into key-value fields.
     * This method tries to interpret the content as JSON (object or array) or as URL-encoded form/query parameters.
     * It populates the provided map with any extracted fields.
     *
     * @param content the content string (e.g., HTTP request/response body or query string)
     * @param fieldMap the map to populate with extracted key-value pairs
     */
    private static void extractFieldsFromContent(String content, Map<String, String> fieldMap) {
        String trimmed = content.trim();
        if (trimmed.isEmpty()) return;
        try {
            // Try parsing as JSON object
            if (trimmed.startsWith("{")) {
                JSONObject obj = new JSONObject(trimmed);
                // Recursively extract all key-value pairs from the JSON object
                extractJsonObjectFields(obj, fieldMap);
                return;
            } else if (trimmed.startsWith("[")) {
                // If it's a JSON array, we won't treat each element as separate fields (not typical for id propagation),
                // but we can store the whole array as a single field if needed or try first element if it's an object.
                JSONArray arr = new JSONArray(trimmed);
                if (arr.length() > 0) {
                    Object firstElem = arr.get(0);
                    if (firstElem instanceof JSONObject) {
                        // If array of objects, extract fields from first object (assuming uniform structure or id likely in first element)
                        extractJsonObjectFields((JSONObject) firstElem, fieldMap);
                    } else {
                        // Non-object array elements: we store the entire array string under a generic key
                        fieldMap.put("array", trimmed);
                    }
                }
                return;
            }
        } catch (JSONException e) {
            // Not a JSON object/array, will attempt other formats
        }
        // If content is not pure JSON, check for key=value pairs (e.g., form data or query params)
        if (trimmed.contains("=")) {
            String[] pairs = trimmed.split("&");
            for (String pair : pairs) {
                if (pair.isEmpty()) continue;
                int eqIndex = pair.indexOf('=');
                String key, value;
                if (eqIndex >= 0) {
                    key = pair.substring(0, eqIndex);
                    value = pair.substring(eqIndex + 1);
                } else {
                    // No '=' present, treat the whole pair as key with empty value (or skip)
                    key = pair;
                    value = "";
                }
                try {
                    // URL decode in case of percent-encoding
                    key = URLDecoder.decode(key, StandardCharsets.UTF_8.name());
                    value = URLDecoder.decode(value, StandardCharsets.UTF_8.name());
                } catch (Exception ignore) { /* Should not happen for StandardCharsets */ }
                if (!key.isEmpty()) {
                    fieldMap.put(key, value);
                }
            }
        } else {
            // If it's a plain string with no obvious key, we can store it under a generic key (e.g., "value" or "body")
            // but such values will likely not be used for linking since no key name to match on another span.
            fieldMap.put("value", trimmed);
        }
    }

    /**
     * Recursively extracts all leaf key-value pairs from a JSONObject into the field map.
     * Each leaf is stored under its dot-prefixed path (e.g., {@code data.orderId}) and also under
     * its bare key name with first-wins semantics so that the shallowest occurrence is preserved
     * for cross-trace merging. Last-wins clobbering of duplicate inner keys is avoided.
     *
     * @param jsonObj the JSONObject to extract fields from
     * @param fieldMap the map to populate with extracted fields
     */
    private static void extractJsonObjectFields(JSONObject jsonObj, Map<String, String> fieldMap) {
        extractJsonObjectFields(jsonObj, fieldMap, "");
    }

    private static void extractJsonObjectFields(JSONObject jsonObj, Map<String, String> fieldMap, String prefix) {
        for (String key : jsonObj.keySet()) {
            Object valueObj = jsonObj.get(key);
            if (valueObj == null) {
                continue; // skip null values
            }
            String prefixedKey = prefix.isEmpty() ? key : prefix + "." + key;
            if (valueObj instanceof JSONObject) {
                extractJsonObjectFields((JSONObject) valueObj, fieldMap, prefixedKey);
            } else if (valueObj instanceof JSONArray) {
                JSONArray array = (JSONArray) valueObj;
                boolean hasObjects = false;
                for (int idx = 0; idx < array.length(); idx++) {
                    if (array.get(idx) instanceof JSONObject) {
                        extractJsonObjectFields(array.getJSONObject(idx), fieldMap, prefixedKey);
                        hasObjects = true;
                    }
                }
                if (!hasObjects) {
                    String arrayString = array.toString();
                    fieldMap.put(prefixedKey, arrayString);
                    // Bare-key alias — first-wins so top-level / shallowest values are preserved
                    fieldMap.putIfAbsent(key, arrayString);
                }
            } else {
                String stringValue = valueObj.toString();
                fieldMap.put(prefixedKey, stringValue);
                // Bare-key alias — first-wins so top-level / shallowest values are preserved
                fieldMap.putIfAbsent(key, stringValue);
            }
        }
    }

    // ---- URL path-parameter extraction ----------------------------------------

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern LONG_ID_PATTERN = Pattern.compile("^\\d{5,}$");

    /**
     * Lazily-initialized noun-to-key map. Loaded once on first use from the
     * path configured via {@link MstConfig.Core#nounMapPath()}. When the
     * configured path equals the bundled default classpath resource we load
     * directly from the classpath; otherwise we load from the filesystem and
     * overlay it on top of the default map (see {@link NounKeyMap#fromPath}).
     */
    private static volatile NounKeyMap NOUN_KEY_MAP;

    private static NounKeyMap nounKeyMap() {
        NounKeyMap local = NOUN_KEY_MAP;
        if (local == null) {
            synchronized (TraceWorkflowExtractor.class) {
                local = NOUN_KEY_MAP;
                if (local == null) {
                    String configured = MstConfig.instance().core().nounMapPath();
                    if (configured == null || configured.equals("mist/noun-map.default.yaml")) {
                        local = NounKeyMap.fromDefault();
                    } else {
                        local = NounKeyMap.fromPath(java.nio.file.Paths.get(configured));
                    }
                    NOUN_KEY_MAP = local;
                }
            }
        }
        return local;
    }

    /**
     * Extracts ID-like values from a URL path and stores them in the field map
     * using semantically meaningful keys derived from the preceding path segment.
     * <p>
     * Recognised patterns:
     * <ul>
     *   <li>UUIDs ({@code 4d2a46c7-71cb-...})</li>
     *   <li>Long numeric IDs (5+ digits)</li>
     *   <li>Any value that follows a known resource noun
     *       ({@code /orders/}, {@code /accounts/}, …)</li>
     * </ul>
     *
     * @param url      the full URL or path (e.g., {@code /api/v1/orders/12345})
     * @param fieldMap the map to populate with extracted key-value pairs
     */
    private static void extractFieldsFromUrl(String url, Map<String, String> fieldMap) {
        if (url == null || url.isEmpty()) return;

        // Strip scheme + authority if present (keep only the path)
        String path = url;
        int schemeEnd = url.indexOf("://");
        if (schemeEnd >= 0) {
            int pathStart = url.indexOf('/', schemeEnd + 3);
            if (pathStart >= 0) {
                path = url.substring(pathStart);
            } else {
                return; // URL has no path component
            }
        }

        // Strip query string
        int qMark = path.indexOf('?');
        if (qMark >= 0) path = path.substring(0, qMark);

        String[] segments = path.split("/");
        NounKeyMap nk = nounKeyMap();

        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            if (seg.isEmpty()) continue;

            boolean isUuid = UUID_PATTERN.matcher(seg).matches();
            boolean isLongId = LONG_ID_PATTERN.matcher(seg).matches();

            if (!isUuid && !isLongId) continue;

            // Determine the key name from the preceding segment.
            // Only derive a semantic key when the previous segment is meaningful — short fragments
            // like "as", "ts", "v1", or "api" produce keys ("aId", "tId", "v1Id", "apiId") that
            // are clutter at best and false producer/consumer matches at worst.
            String key = null;
            if (i > 0) {
                String prev = segments[i - 1].toLowerCase();
                key = nk.keyFor(prev);
                if (key == null && isMeaningfulPathNoun(prev)) {
                    // Fallback: derive a camelCase + "Id" key. Handles hyphen/underscore
                    // joining (e.g. "order-items" -> "orderItemId") and plural stripping
                    // via stripPluralForUrlSegment.
                    key = deriveFallbackKey(prev);
                    log.debug("nounKeyMap miss for '{}' — derived '{}'", prev, key);
                }
            }
            // If we couldn't derive a semantic name, skip the segment entirely. The previous
            // {@code pathParam_i} fallback was matching by happenstance across unrelated traces
            // when two requests reused the same UUID at the same positional index.
            if (key == null) {
                log.debug("URL path param: skipping segment '{}' at index {} (no semantic key)", seg, i);
                continue;
            }

            fieldMap.put(key, seg);
            log.debug("URL path param: {}={} (from {})", key, seg, path);
        }
    }

    /**
     * True when the path segment is plausibly a resource noun (not a version, scheme, etc.).
     * Accepts lowercase alphabetic words optionally joined by single hyphens or underscores
     * (e.g. {@code orders}, {@code order-items}, {@code order_items}). Rejects UUIDs, pure
     * numeric segments, version stamps like {@code v1}, and entries in {@link #PATH_NOISE_TOKENS}.
     */
    private static boolean isMeaningfulPathNoun(String seg) {
        if (seg == null || seg.length() < 3) return false;
        if (PATH_NOISE_TOKENS.contains(seg)) return false;
        // Allow lowercase letter groups joined by single hyphens or underscores.
        // String.matches is implicitly anchored, so no leading ^ / trailing $ is needed.
        if (!seg.matches("[a-z]+([-_][a-z]+)*")) return false;
        // Reject UUIDs and pure-numeric segments — those look like IDs, not nouns.
        if (UUID_PATTERN.matcher(seg).matches()) return false;
        if (LONG_ID_PATTERN.matcher(seg).matches()) return false;
        return true;
    }

    /**
     * Fallback key derivation for path nouns absent from the configured map.
     * Strips a trailing plural {@code s} (protected by
     * {@link #stripPluralForUrlSegment}), camel-cases hyphen/underscore-joined
     * tokens (e.g. {@code order-items} → {@code orderItem}), then appends
     * {@code Id}.
     */
    private static String deriveFallbackKey(String segment) {
        String s = stripPluralForUrlSegment(segment);
        StringBuilder sb = new StringBuilder(s.length() + 2);
        boolean upperNext = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '-' || c == '_') {
                upperNext = true;
                continue;
            }
            sb.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        sb.append("Id");
        return sb.toString();
    }

    private static final java.util.Set<String> PATH_NOISE_TOKENS = new java.util.HashSet<>(
            java.util.Arrays.asList("api", "rest", "service", "services", "internal", "external",
                    "public", "private", "v", "version"));

    /** Strip a trailing 's' for plural-singular conversion, but protect common non-plurals. */
    private static String stripPluralForUrlSegment(String seg) {
        if (seg.length() <= 2 || !seg.endsWith("s")) return seg;
        String last2 = seg.substring(seg.length() - 2);
        if (last2.equals("ss") || last2.equals("us") || last2.equals("is")
                || last2.equals("os") || last2.equals("as")) {
            return seg;
        }
        if (PATH_NON_PLURAL_S.contains(seg)) return seg;
        return seg.substring(0, seg.length() - 1);
    }

    private static final java.util.Set<String> PATH_NON_PLURAL_S = new java.util.HashSet<>(
            java.util.Arrays.asList("news", "bus", "gas", "boss", "address", "atlas",
                    "canvas", "chaos", "campus", "focus", "menus", "virus", "lens",
                    "series", "species"));

    // ---- end URL path-parameter extraction ------------------------------------

    /**
     * Merges any scenarios in the list that have data dependencies between them.
     * If a field in one scenario's output matches a field in another scenario's input (by key and value),
     * the two scenarios will be merged into one, with the producer step becoming the parent of the consumer step.
     * The list of scenarios is updated in-place to reflect merged scenarios.
     *
     * @param scenarios the list of scenarios to analyze and merge as necessary
     */
    private static void mergeScenariosByDataDependency(List<WorkflowScenario> scenarios) {
        Set<String> ignoreKeys = new HashSet<>();
        ignoreKeys.add("http.status_code");
        ignoreKeys.add("status_code");
        ignoreKeys.add("value");
        ignoreKeys.add("http.method");
        ignoreKeys.add("http.url");
        ignoreKeys.add("http.target");
        ignoreKeys.add("http.path");
        ignoreKeys.add("http.request.body");
        ignoreKeys.add("http.response.body");

        boolean mergedSomething = true;
        while (mergedSomething) {
            mergedSomething = false;
            outerLoop:
            for (int i = 0; i < scenarios.size(); i++) {
                WorkflowScenario scenarioA = scenarios.get(i);
                List<WorkflowStep> stepsA = collectAllSteps(scenarioA);
                for (int j = i + 1; j < scenarios.size(); j++) {
                    WorkflowScenario scenarioB = scenarios.get(j);
                    List<WorkflowStep> stepsB = collectAllSteps(scenarioB);

                    // Direction 1: output of A consumed by input of B
                    for (WorkflowStep stepA : stepsA) {
                        for (Map.Entry<String, String> outEntry : stepA.getOutputFields().entrySet()) {
                            String key = outEntry.getKey();
                            if (ignoreKeys.contains(key)) continue;
                            String value = outEntry.getValue();
                            if (value == null || value.isEmpty()) continue;
                            for (WorkflowStep stepB : stepsB) {
                                String inVal = stepB.getInputFields().get(key);
                                if (inVal == null || inVal.isEmpty() || !inVal.equals(value)) continue;

                                // Temporal guard: producer must finish before consumer starts
                                long producerEnd = stepA.getEndTime();
                                long consumerStart = stepB.getStartTime();
                                if (producerEnd > 0 && consumerStart > 0 && producerEnd > consumerStart) {
                                    log.debug("Skipping merge for {}={}: producer (trace {}) ends at {} "
                                            + "after consumer (trace {}) starts at {}",
                                            key, value, stepA.getTraceId(), producerEnd,
                                            stepB.getTraceId(), consumerStart);
                                    continue;
                                }

                                log.info("Data dependency found: {}={} from {}(trace {})->{}(trace {})",
                                        key, value,
                                        stepA.getServiceName(), stepA.getTraceId(),
                                        stepB.getServiceName(), stepB.getTraceId());

                                WorkflowStep rootB = stepB;
                                while (rootB.getParent() != null) {
                                    rootB = rootB.getParent();
                                }

                                scenarioA.mergeWith(scenarioB, stepA, rootB);
                                rootB.addProvenance(key, value);

                                log.info("Merged scenario traces {} into {} via field {}={}",
                                        scenarioB.getTraceIds(), scenarioA.getTraceIds(), key, value);

                                scenarios.remove(j);
                                mergedSomething = true;
                                break outerLoop;
                            }
                        }
                    }

                    // Direction 2: output of B consumed by input of A (reverse dependency)
                    for (WorkflowStep stepB : stepsB) {
                        for (Map.Entry<String, String> outEntry : stepB.getOutputFields().entrySet()) {
                            String key = outEntry.getKey();
                            if (ignoreKeys.contains(key)) continue;
                            String value = outEntry.getValue();
                            if (value == null || value.isEmpty()) continue;
                            for (WorkflowStep stepA : stepsA) {
                                String inVal = stepA.getInputFields().get(key);
                                if (inVal == null || inVal.isEmpty() || !inVal.equals(value)) continue;

                                long producerEnd = stepB.getEndTime();
                                long consumerStart = stepA.getStartTime();
                                if (producerEnd > 0 && consumerStart > 0 && producerEnd > consumerStart) {
                                    log.debug("Skipping reverse merge for {}={}: producer (trace {}) ends at {} "
                                            + "after consumer (trace {}) starts at {}",
                                            key, value, stepB.getTraceId(), producerEnd,
                                            stepA.getTraceId(), consumerStart);
                                    continue;
                                }

                                log.info("Data dependency found (reverse): {}={} from {}(trace {})->{}(trace {})",
                                        key, value,
                                        stepB.getServiceName(), stepB.getTraceId(),
                                        stepA.getServiceName(), stepA.getTraceId());

                                WorkflowStep rootA = stepA;
                                while (rootA.getParent() != null) {
                                    rootA = rootA.getParent();
                                }

                                scenarioB.mergeWith(scenarioA, stepB, rootA);
                                rootA.addProvenance(key, value);

                                log.info("Merged scenario traces {} into {} via field {}={}",
                                        scenarioA.getTraceIds(), scenarioB.getTraceIds(), key, value);

                                scenarios.remove(i);
                                mergedSomething = true;
                                break outerLoop;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Heuristic merge: groups scenarios by session identifier (client IP), sorts
     * chronologically, and merges consecutive scenarios whose time gap is within
     * the configured threshold into a single multi-root scenario.
     *
     * @param scenarios          the scenario list (modified in-place)
     * @param maxGapMicros       maximum gap in microseconds between two consecutive
     *                           scenarios for them to be merged (default 60 000 000 = 60s)
     * @param maxRootsPerScenario upper bound on roots in a single merged scenario to
     *                           avoid creating unreasonably large test cases
     */
    private static void mergeScenariosBySessionTimeWindow(
            List<WorkflowScenario> scenarios, long maxGapMicros, int maxRootsPerScenario) {

        // 1. Group by sessionIdentifier
        Map<String, List<WorkflowScenario>> bySession = new HashMap<>();
        for (WorkflowScenario sc : scenarios) {
            bySession.computeIfAbsent(sc.getSessionIdentifier(), k -> new ArrayList<>()).add(sc);
        }

        Set<WorkflowScenario> absorbed = new HashSet<>();

        for (Map.Entry<String, List<WorkflowScenario>> entry : bySession.entrySet()) {
            String session = entry.getKey();
            List<WorkflowScenario> group = entry.getValue();

            if (group.size() < 2) continue;

            // Traces that arrived without an http.client_ip tag fall into the UNKNOWN_SESSION
            // bucket.  Rather than skip them entirely (which loses coverage for workloads
            // with weak OTel session metadata), we merge them with CONSERVATIVE thresholds:
            // a much tighter time window and a smaller root cap.  This preserves some
            // multi-root assembly for legitimately related traces while preventing wild
            // merges across unrelated requests that only happen to share the absence of
            // client IP.
            boolean unknownSession = "UNKNOWN_SESSION".equals(session);
            long    effectiveMaxGap   = unknownSession ? Math.min(maxGapMicros, 15_000_000L) : maxGapMicros;
            int     effectiveMaxRoots = unknownSession ? Math.min(maxRootsPerScenario, 3)    : maxRootsPerScenario;
            if (unknownSession) {
                log.info("Session merge [UNKNOWN_SESSION]: applying conservative thresholds (gap≤{}µs, ≤{} roots)",
                         effectiveMaxGap, effectiveMaxRoots);
            }

            // 2. Sort chronologically
            group.sort(Comparator.comparingLong(WorkflowScenario::getStartTimeMicros));

            // 3. Sliding window merge
            //
            // The `absorbed` set above is correctly scoped to this single call: within one
            // invocation, each `WorkflowScenario` object is either the accumulator (assigned
            // once on entry, replaced only when a `next` falls outside the window) or absorbed
            // exactly once.  Double-absorption inside one call is therefore impossible.
            //
            // However, `mergeScenariosBySessionTimeWindow` runs ONCE PER INPUT TRACE FILE
            // (called from `extractScenariosFromFile`, line 509).  If the SAME Jaeger trace
            // ID appears as input in multiple `.json` files (which happens routinely when
            // the same captured trace is included in several recorded test sessions), each
            // file's invocation independently rebuilds its own `WorkflowScenario` objects
            // for that trace ID and may absorb them into a different parent.  That is by
            // design — each input file represents a distinct test session — and is the
            // reason the "appending trace [X]" log can legitimately repeat for the same X.
            // Downstream global dedup (Phase 2.5 / `runSingleRootDedupPass` in
            // `MultiServiceTestCaseGenerator`) handles cross-file duplicates among
            // single-root scenarios; multi-root scenarios that share a trace ID across
            // files are preserved intentionally.
            //
            // The per-absorb message is logged at DEBUG to keep INFO logs readable; the
            // aggregate "absorbed N single-trace scenarios" message below remains at INFO.
            WorkflowScenario accumulator = group.get(0);
            for (int i = 1; i < group.size(); i++) {
                WorkflowScenario next = group.get(i);

                long gap = next.getStartTimeMicros() - accumulator.getEndTimeMicros();
                boolean withinWindow = gap >= 0 && gap <= effectiveMaxGap;
                boolean underLimit = accumulator.getRootSteps().size() < effectiveMaxRoots;

                if (withinWindow && underLimit) {
                    log.debug("Session merge [{}]: appending trace {} (gap={}µs) → now {} roots",
                            session, next.getTraceIds(),
                            gap, accumulator.getRootSteps().size() + next.getRootSteps().size());
                    accumulator.appendSequentialScenario(next);
                    absorbed.add(next);
                } else {
                    accumulator = next;
                }
            }
        }

        // Remove absorbed scenarios from the master list
        if (!absorbed.isEmpty()) {
            scenarios.removeAll(absorbed);
            log.info("Session-based merge absorbed {} single-trace scenarios into multi-root sequences",
                    absorbed.size());
        }
    }

    /**
     * Utility method to collect all WorkflowStep objects in a scenario into a list.
     * This performs a depth-first traversal of the scenario's step tree.
     */
    private static List<WorkflowStep> collectAllSteps(WorkflowScenario scenario) {
        List<WorkflowStep> allSteps = new ArrayList<>();
        for (WorkflowStep root : scenario.getRootSteps()) {
            collectStepsRecursive(root, allSteps);
        }
        return allSteps;
    }

    /** Recursive helper for collectAllSteps. */
    private static void collectStepsRecursive(WorkflowStep step, List<WorkflowStep> list) {
        list.add(step);
        for (WorkflowStep child : step.getChildren()) {
            collectStepsRecursive(child, list);
        }
    }


}
