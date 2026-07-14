# MIST — Prompt Inventory

Every prompt MIST sends to an LLM at runtime, with the text as it appears in the source. The code that builds these prompts is all in `mist-core`. `mist-llm` is only transport — `LLMClient`, `LLMService`, and the OpenAI-compatible / Gemini / Ollama adapters — and `mist-cli` just wires the classes together; neither holds prompt text.

Two prompts aren't hard-coded: `apiDiscovery` (#4) and `directValueExtraction` (#5) come from the input-fetch registry. Their defaults live in `InputFetchRegistry.initializeDefaults()` and a per-SUT `input-fetch-registry.yaml` can override them. The registry also carries a `valueSelection` template, but nothing currently calls it.

In the prompt text below, `{name}` is a value the code splices in and a trailing `#` marks a conditional block. Line numbers follow `main_track` as of 2026-07-13 — grep a line if the code has moved. (The `debug/**/PROMPT_*.md` and `VERIFICATION_PROMPT.md` files are work briefs, not runtime prompts.)

## Prompt sites

| # | Prompt | Component / area | Where the text lives | Purpose (one line) | Dispatch (maxTokens · temp) |
|---|--------|------------------|----------------------|--------------------|-----------------------------|
| 1 | Parameter value generation | mist-core · generation | `mist-core/src/main/java/io/mist/core/generation/ZeroShotLLMGenerator.java` · sys `callLLM`:975 / user `buildPrompt`:815 | Produce N realistic, constraint-valid values for any API parameter | 200 · 0.7 |
| 2 | Soft-failure (hidden-error) validation | mist-core · generation | `mist-core/src/main/java/io/mist/core/generation/ZeroShotLLMGenerator.java` · `validateResponse`:1343 | Decide if a 2xx response actually FAILED (status:0 / error msg / null data) | 500 · 0.3 |
| 3 | Negative-test verdict validation | mist-core · generation | `mist-core/src/main/java/io/mist/core/generation/ZeroShotLLMGenerator.java` · `validateNegativeTestResponse`:1470 | Decide if an intentionally-invalid input was rejected *for the right reason* | 500 · 0.3 |
| 4 | Service discovery (which service holds this data) | mist-core · smart input fetch (registry template) | default `mist-core/src/main/java/io/mist/core/smart/InputFetchRegistry.java`:467 · applied `SmartInputFetcher.java`:3723 | Ask which SUT service could supply a real value for a parameter | 2-arg default |
| 5 | Direct value extraction from a response | mist-core · smart input fetch (registry template) | default `mist-core/src/main/java/io/mist/core/smart/InputFetchRegistry.java`:479 · applied `SmartInputFetcher.java`:1037 | Pull the right field value out of a fetched JSON response body | 2-arg default |
| 6 | Semantic field matching | mist-core · smart input fetch | `mist-core/src/main/java/io/mist/core/smart/SmartInputFetcher.java`:1223 | Map a parameter to the most semantically relevant response field (or `NO_MATCH`) | 2-arg default |
| 7 | Parameter-error classification | mist-core · smart input fetch | `mist-core/src/main/java/io/mist/core/smart/ParameterErrorAnalyzer.java`:165 | Classify whether an API failure was caused by a specific input parameter, and which | 2-arg default |
| 8 | HTTP status-code discovery | mist-core · coverage | `mist-core/src/main/java/io/mist/core/coverage/LLMStatusCodeDiscovery.java` · sys `buildSystemPrompt`:188 / user `buildDiscoveryPrompt`:196 | Enumerate ALL status codes an operation can return + trigger strategy + inputs (JSON) | 2000 · 0.3 |
| 9 | Failed-test parameter enhancement | mist-core · enhancer | `mist-core/src/main/java/io/mist/core/enhancer/TestCaseEnhancer.java` · sys `buildSystemPrompt`:414 / user `buildUserPrompt`:434 | Suggest improved parameter values to make a failed test pass (respect locked/invalid params) | config |
| 10 | Status-code exploration test generation | mist-core · enhancer | `mist-core/src/main/java/io/mist/core/enhancer/StatusCodeExplorationEnhancer.java` · sys `buildExplorationSystemPrompt`:704 / user `buildExplorationUserPrompt`:729 | Generate exploration test variants that trigger untriggered status codes (JSON) | config |
| 11 | Fault-category mining | mist-core · fault | `mist-core/src/main/java/io/mist/core/fault/FaultMiner.java` · sys `SYSTEM_PROMPT`:88 / user `buildUserPrompt`:200 | Mine ≤3 SUT-specific invalid-input categories from spec + observed 4xx/5xx (JSON-per-line) | prompt() |
| 12 | Trace root-cause analysis | mist-core · analysis | `mist-core/src/main/java/io/mist/core/analysis/TraceErrorAnalyzer.java`:479 | Root-cause + fix from a distributed trace (ROOT CAUSE / FIX) | 2-arg default |

All prompts flow through `mist-llm`'s `LLMService.getInstance(...).generateText(system, user[, maxTokens, temperature])` (or `LLMClient.prompt(system, user)` for #11), which routes to the configured backend and caches via `LLMCallCache` so seeded reruns short-circuit the backend. "2-arg default" means the call omits explicit `maxTokens`/`temperature`; "config" means they come from run configuration.

---

## The prompts

### 1. Parameter value generation — `ZeroShotLLMGenerator`
- **Component:** mist-core · generation (the primary input-generation path).
- **Location:** `mist-core/src/main/java/io/mist/core/generation/ZeroShotLLMGenerator.java`; system in `callLLM` (line 975), user built in `buildPrompt(param, howMany)` (line 815); dispatched `generateText(system, prompt, 200, 0.7)` at line 991.
- **Purpose:** given one OpenAPI parameter, produce `howMany` distinct, realistic, strictly-valid values (one per line, or a JSON array for `array`-typed params).

**System prompt** (from `callLLM`)
````
You are an expert API tester specialising in test data generation. Your sole task is to produce realistic, constraint-compliant values for API parameters. STRICT RULES: (1) When asked for N values, return EXACTLY N items — no more, no fewer. (2) For line-separated output: one value per line, nothing else on that line. (3) For JSON array output: a single valid JSON array, nothing else. (4) Never add markdown fences (```), bullet points, numbering, explanations, or commentary of any kind. (5) Always respect the Type, Format, Enum, and numeric/length Constraints stated in the prompt. (6) If an enum list is provided, output ONLY values from that list.
````

**User prompt** (from `buildPrompt`)
```
You are an expert API tester. Generate {howMany} distinct, highly realistic, and strictly valid values for the following API parameter.
Current Date/Time: {timestamp}

[API Context]                                  # only if endpoint/service known
Endpoint: {apiName}
Service:  {serviceName}
Sibling Parameters: {name1, name2, ...}

[Parameter Details]
Parameter Name: {name}
Location:       {in}
Type:           {type} ({format})              # "({format})" only if a format is set
Description:    {description}                   # only if present
Example:        {schemaExample}                 # only if present
Required:       Yes|No                          # only if known

[Constraints]                                  # whole block only if any constraint present
Allowed Values (Enum): [{v1, v2, ...}]
  → You MUST only use values from the Allowed Values list above.
Numeric Range: min={min} max={max}
  → Every generated number MUST fall within this range.
String Length: minLength={min} maxLength={max}
  → Every generated string MUST satisfy this length constraint.
Pattern (regex): {regex}
  → Every generated value MUST match this pattern.

[Instructions]
1. You MUST strictly adhere to the Type, Format, and all Constraints listed above.
2. Because an enum is defined, select ONLY values from the Allowed Values list.      # if enum
2. The values must be semantically realistic for the Endpoint context and domain.    # if no enum
3. Return EXACTLY {howMany} values.
4. Domain guidance:
   • Generate realistic numeric values appropriate to the business context.           # integer/number
   • Stay strictly within the Numeric Range defined in Constraints.                    #   (if bounded)
   • Output only 'true' or 'false' (lowercase, no quotes).                             # boolean
   • Each value must be a valid UUID v4 (e.g., 550e8400-e29b-41d4-a716-446655440000).  # format=uuid
   • Use ISO-8601 date format: YYYY-MM-DD.                                             # format=date
   • Use ISO-8601 date-time format: YYYY-MM-DDTHH:MM:SSZ.                              # format=date-time
   • Each value must be a valid email address.                                        # format=email
   • Respect the '{format}' format specification.                                     # other formats
   • This is a future-oriented temporal parameter — generate dates 1-30 days AFTER the Current Date/Time.   # name-based temporal heuristic, no explicit format
5. Output format:
   • Return a single valid JSON array containing exactly {howMany} elements.           # array type
   • Format: ["value1", "value2", ...]
   • Do NOT use markdown code fences, bullet points, numbering, or explanations.
Generate the JSON array now:
   • Output ONLY the values, one per line.                                            # non-array type
   • Do NOT use markdown code blocks, bullet points, numbering, or any explanations.
   • Do NOT prefix values with hyphens, dashes, or numbers.
Generate your {howMany} values now, one per line:
```

### 2. Soft-failure (hidden-error) validation — `ZeroShotLLMGenerator`
- **Component:** mist-core · generation / response validation.
- **Location:** same file; `validateResponse` builds the system prompt at line 1343 and calls `generateText(..., 500, 0.3)` at line 1404. Result cached via `PROP_VALIDATION_CACHE_PATH` (`.mist/llm-validation-cache.json`).
- **Purpose:** detect "success-looking" 2xx responses that actually failed.

**System prompt**
```
You are an API testing expert analyzing response data.

ANALYSIS CRITERIA:
A response is considered FAILED if it contains ANY of:
1. Explicit failure indicators:
   - status: 0 or status: false or status: "error" or status: "failed"
   - success: false
   - error: true or hasError: true
   - Any field explicitly indicating failure
2. Error messages:
   - Fields named: error, errorMessage, msg, message, errorMsg containing non-empty error text
   - Exception information or stack traces
3. Data validation:
   - data field is null or empty when data is expected
   - Empty result arrays when results are expected
4. Business logic errors:
   - Validation error messages (e.g., "invalid parameters", "not found", "unauthorized")
   - Constraint violation messages
IMPORTANT:
- If the response looks successful with valid data, return FAILED=false
- Only return FAILED=true if there are clear error indicators
- Be specific about WHY it failed in your root cause analysis
OUTPUT FORMAT (exactly 2 lines):
FAILED: true|false
RCA: <detailed root cause analysis explaining why this is a failure or success>
```

**User prompt**
````
TASK: Determine if this API call actually FAILED despite returning a success status code.

API Details:
- Service: {serviceName}
- Endpoint: {method} {path}
- HTTP Status Code: {statusCode}

Response Body:
```json
{responseBody}          # JSON-aware truncation if > 16KB
```

Examples:
Example 1 (Soft Error):
Response: {"status":0,"msg":"start station not in list","data":null}
FAILED: true
RCA: API returned status=0 indicating failure. Error message states 'start station not in list', and data field is null. This is a business logic validation failure.

Example 2 (Success):
Response: {"status":1,"msg":"Success","data":{"id":123,"name":"Route A"}}
FAILED: false
RCA: API returned status=1 indicating success. Response contains valid data with id and name fields. No error indicators present.

Now analyze the response above and provide your answer:
````

### 3. Negative-test verdict validation — `ZeroShotLLMGenerator`
- **Component:** mist-core · generation / negative-test validation.
- **Location:** same file; `validateNegativeTestResponse` builds the system prompt at line 1470, calls `generateText(..., 500, 0.3)` at line 1565.
- **Purpose:** for an intentionally-invalid input, decide whether the API rejected it *for a reason related to the designed invalid parameter* (test PASSES) vs accepted it / failed for an unrelated reason (test FAILS).

**System prompt**
```
You are an API testing expert validating NEGATIVE TEST results.

CONTEXT: This is a NEGATIVE test where we INTENTIONALLY sent INVALID inputs to test error handling.
The test PASSES (returns FAILED=true) if the API correctly rejected our invalid input.
The test FAILS (returns FAILED=false) if the API accepted the invalid input or the error is unrelated.

DESIGNED INVALID INPUTS:
  - Parameter '{name}' was set to INVALID value: {value}     # one line per invalid param
  (No specific invalid parameters provided)                  # if none

VALIDATION CRITERIA:
Return FAILED=true (test PASSES) if:
1. The response contains an error message that SPECIFICALLY mentions or relates to one of the designed invalid parameters
2. The error message indicates validation failure for the invalid input we sent
3. The response shows the API correctly rejected our invalid data

Return FAILED=false (test FAILS) if:
1. The response shows SUCCESS (API accepted our invalid input - this is BAD!)
2. The error is about something UNRELATED to our invalid parameters (e.g., authentication, server error)
3. The error message doesn't mention or relate to the parameters we made invalid

OUTPUT FORMAT (exactly 3 lines):
FAILED: true|false
RELATED_TO_INVALID_INPUT: true|false
RCA: <explanation of whether the error is about our designed invalid input>
```

**User prompt**
````
TASK: Determine if this API response correctly rejected our DESIGNED INVALID INPUT.

API Details:
- Service: {serviceName}
- Endpoint: {method} {path}
- HTTP Status Code: {statusCode}

Designed Invalid Parameters:
  - {name}: {value}              # one per invalid param, or:
  (General negative test)

Response Body:
```json
{responseBody}                  # JSON-aware truncation if > 16KB
```

Examples:
Example 1 (Error is about our invalid input - TEST PASSES):
Invalid Parameter: basicPriceRate = -100
Response: {"status":0,"msg":"Invalid price rate: must be positive"}
FAILED: true
RELATED_TO_INVALID_INPUT: true
RCA: The error message 'Invalid price rate' directly relates to our invalid basicPriceRate parameter. The API correctly rejected our negative price value.

Example 2 (Error is UNRELATED to our invalid input - TEST FAILS):
Invalid Parameter: basicPriceRate = -100
Response: {"status":0,"msg":"Authentication token expired"}
FAILED: false
RELATED_TO_INVALID_INPUT: false
RCA: The error is about authentication, NOT about our invalid basicPriceRate. This is an unrelated error so the negative test FAILS.

Example 3 (API accepted invalid input - TEST FAILS):
Invalid Parameter: stationName = "" (empty string)
Response: {"status":1,"msg":"Success","data":{"id":123}}
FAILED: false
RELATED_TO_INVALID_INPUT: false
RCA: The API accepted our empty station name without error. The invalid input was NOT rejected, so the negative test FAILS.

Now analyze the response above and provide your answer:
````

### 4. Service discovery — registry template `apiDiscovery`
- **Component:** mist-core · smart input fetch (fetch real values from live SUT services instead of synthesizing them).
- **Where the text lives:** default in `mist-core/src/main/java/io/mist/core/smart/InputFetchRegistry.java` (`initializeDefaults()`, line 467); loaded and placeholder-filled by `SmartInputFetcher.buildLLMDiscoveryPrompt(...)` (line 3723) and sent via `askLLMForServices` (`discoverByLLM`, line 592). **Overridable per-SUT** in that SUT's `input-fetch-registry.yaml` (`llmPrompts.apiDiscovery`).
- **Purpose:** ask which SUT service(s) could provide a real value for a parameter; answer is whitelisted against known services (`NO_GOOD_MATCH` sentinel stripped) before persisting to the registry.

**Prompt** (verbatim default template):
```
Parameter: {parameterName} (type: {parameterType}, location: {parameterLocation})
Description: {parameterDescription}

Services: {availableServices}

Task: Select the TOP 3 services most likely to provide realistic data for this parameter.
Consider semantic meaning and naming patterns.

If you find good matches, respond with a JSON array of 1-3 service names in priority order:
["service1", "service2", "service3"]

If NO services seem suitable for this parameter, respond with:
NO_GOOD_MATCH

Respond ONLY with the JSON array OR 'NO_GOOD_MATCH', no explanations.
```

### 5. Direct value extraction — registry template `directValueExtraction`
- **Component:** mist-core · smart input fetch.
- **Where the text lives:** default in `InputFetchRegistry.java` (line 479); loaded and placeholder-filled by `SmartInputFetcher.buildDirectExtractionPrompt(...)` (line 1037). **Overridable per-SUT** in `input-fetch-registry.yaml` (`llmPrompts.directValueExtraction`).
- **Purpose:** given a fetched response body and the target parameter, extract an appropriate concrete value that already appears in the response (no invented values).

**Prompt** (verbatim default template):
```
API Response: {responseSchema}

Target Parameter: {parameterName} (type: {parameterType})
Description: {parameterDescription}

Task: Extract or derive a suitable value for this parameter from the API response above.
You must use ONLY values that appear in the response - do not generate new values.

Guidelines:
- Look for exact field matches first
- Consider semantically related fields
- Use any reasonable value from the response
- For list parameters: you can combine multiple values with commas
- Ensure the returned value matches the parameter type

Examples:
- For a location/name parameter: use values from semantically related fields (e.g. 'from', 'to', 'name')
- For 'price': use values from 'price' or cost-related fields
- For 'id': use any ID field from the response
- For a list parameter: use numeric values or names that match the element type

Respond with ONLY the extracted value (e.g., '<a value from the response>' or '100.0')
If no suitable value exists in the response: NO_GOOD_MATCH
```

> Note: a shipped SUT registry (e.g. `mist-cli/src/main/resources/My-Example/trainticket/input-fetch-registry.yaml:1760`) may carry a slightly different, domain-flavoured wording of this template (train-ticket examples such as `stationName`, `distanceList`). The code default above is what applies when a SUT registry does not override it.

### 6. Semantic field matching — `SmartInputFetcher`
- **Component:** mist-core · smart input fetch.
- **Location:** `mist-core/src/main/java/io/mist/core/smart/SmartInputFetcher.java`; `askLLMForSemanticFieldMatching(data, paramName)` (line 1221, prompt from line 1223).
- **Purpose:** map a parameter name to the most semantically relevant field in the available data, respecting value-type compatibility; returns the field name only, or `NO_MATCH`.

**Prompt** (verbatim template):
```
Find the most semantically relevant field in this data for the parameter '{paramName}':

Available fields and their values:
- {fieldName}: {fieldValue}          # one per field; values > 50 chars truncated with "..."

Parameter: {paramName}

Instructions:
1. Find the field that is most semantically related to the parameter
2. Consider meaning, context, and domain relevance
3. Consider the VALUE TYPE - don't match UUIDs to distance/numeric parameters
4. For distance/numeric parameters, only match numeric fields
5. For ID parameters, prefer UUID or numeric ID fields
6. Return ONLY the field name, nothing else
7. If no relevant field exists, respond with: NO_MATCH

Examples:
Parameter 'origin' → field 'from' (if from contains matching values)
Parameter 'destination' → field 'to' (if to contains matching values)
Parameter 'userId' → field 'accountId' (if accountId contains IDs)
Parameter 'distance' → field 'price' (if price contains numbers, not UUIDs)
Parameter 'itemId' → field 'itemNumber' (if itemNumber contains matching IDs)

Which field is most relevant for parameter '{paramName}'?
```

### 7. Parameter-error classification — `ParameterErrorAnalyzer`
- **Component:** mist-core · smart input fetch (failure attribution).
- **Location:** `mist-core/src/main/java/io/mist/core/smart/ParameterErrorAnalyzer.java`; user prompt at line 165, system + dispatch `generateText(system, prompt)` at line 181. A deterministic trace-pattern extraction short-circuits the LLM when the failure already names the parameter.
- **Purpose:** classify whether an API failure is caused by an input parameter and, if so, which one and what category.

**System prompt**
```
You are an API testing expert. Analyze API failures to identify which parameter caused the issue.
```

**User prompt**
```
Analyze this API failure to determine if it's caused by an input parameter:

{errorContext}

Task: Determine if this error is caused by a specific input parameter.

If YES, respond with:
PARAMETER_ERROR: YES
PARAMETER: <parameter_name>
ERROR_TYPE: <category>

If NO (system error, network issue, etc.), respond with:
PARAMETER_ERROR: NO

Categories: VALIDATION_ERROR, TYPE_MISMATCH, FORMAT_ERROR, NULL_ERROR, CONSTRAINT_ERROR
Respond ONLY in the specified format.
```

### 8. HTTP status-code discovery — `LLMStatusCodeDiscovery`
- **Component:** mist-core · coverage.
- **Location:** `mist-core/src/main/java/io/mist/core/coverage/LLMStatusCodeDiscovery.java`; `buildSystemPrompt()` (line 188), `buildDiscoveryPrompt(...)` (line 196), `generateText(system, prompt, 2000, 0.3)` (line 142). Falls back to `createDefaultTargets` on empty/unparseable output.
- **Purpose:** for one operation, enumerate ALL status codes it could return, each with category, description, trigger strategy, `requiresAuthManipulation`, and suggested inputs — as a JSON array.

**System prompt**
```
You are an API testing expert specializing in HTTP status codes and REST API behavior.
Your task is to analyze API operations and identify ALL possible HTTP status codes they could return.

Be comprehensive - consider all standard HTTP status codes (1xx, 2xx, 3xx, 4xx, 5xx).
For each status code, provide a clear strategy to trigger it and suggested parameter values.

Always respond with valid JSON only. No markdown, no explanations outside the JSON.
```

**User prompt**
```
Analyze this REST API operation and identify ALL HTTP status codes it could possibly return.

=== API OPERATION ===
Service: {serviceName}
Method: {httpMethod}
Path: {path}

=== PARAMETERS ===                              # only if parameters present
- {paramName}: {paramDescription}

=== ALREADY OBSERVED STATUS CODES ===           # only if any observed
From first execution: {observedStatusCodes}

=== SAMPLE RESPONSES ===                         # only if any samples
Response {i}: {response}

=== TASK ===
Identify ALL possible HTTP status codes this API could return.
Consider these categories:
- 2xx Success: 200, 201, 202, 204, 206...
- 4xx Client Errors: 400, 401, 403, 404, 405, 409, 422, 429...
- 5xx Server Errors: 500, 502, 503, 504...
- 3xx Redirects if applicable: 301, 302, 304...

For EACH status code, provide:
1. statusCode: The HTTP status code number
2. category: Category name (Success, Client Error, Server Error, Redirect)
3. description: When/why this status code would be returned
4. triggerStrategy: How to trigger this status code
5. requiresAuthManipulation: true/false - does triggering require auth changes?
6. suggestedInputs: Parameter values to trigger this code (JSON object)

=== RESPONSE FORMAT ===
Respond with a JSON array ONLY (no markdown, no explanation):
[
  {
    "statusCode": 200,
    "category": "Success",
    "description": "Successful operation",
    "triggerStrategy": "Provide valid inputs for all parameters",
    "requiresAuthManipulation": false,
    "suggestedInputs": {"param1": "validValue"}
  },
  ...
]
```

### 9. Failed-test parameter enhancement — `TestCaseEnhancer`
- **Component:** mist-core · enhancer.
- **Location:** `mist-core/src/main/java/io/mist/core/enhancer/TestCaseEnhancer.java`; `buildSystemPrompt()` (line 414), `buildUserPrompt(failedTest)` (line 434), `generateText(system, user, maxTokens, temperature)` (line 83).
- **Purpose:** analyze a failed test and suggest improved parameter values more likely to pass — never changing intentionally-invalid params (negative tests) or structurally-locked params (wired to captured outputs of prior steps).

**System prompt**
```
You are an expert API test case analyzer and enhancer.

Your task is to analyze a failed API test case and suggest improved parameter values that are more likely to make the test pass.

IMPORTANT RULES:
1. Suggest realistic, valid values that match the API's expectations.
2. Analyze the error response message carefully to understand WHY the test failed.
3. Consider the parameter descriptions, types, and examples when suggesting new values.
4. Return your response in valid JSON format ONLY.

RESPONSE FORMAT:
{
  "enhancedParameters": [
    {"name": "paramName", "value": "newValue"}
  ],
  "reasoning": "Brief explanation of why these values were chosen"
}
```

**User prompt**
````
ANALYZE THIS FAILED TEST AND SUGGEST IMPROVED PARAMETER VALUES:

TEST INFORMATION:
- Test Name: {testMethodName}
- Endpoint: {httpMethod} {path}
- Test Type: NEGATIVE (invalid inputs)|POSITIVE (valid inputs)
- Service: {serviceName}
- Failed Step Index: {failedStep}          # if applicable

EXECUTION RESULT:
- HTTP Status: {actualStatusCode}
- Response: {truncatedResponseBody}
- Error: {errorMessage}

⚠️ INTENTIONALLY INVALID PARAMETERS (DO NOT CHANGE THESE):     # negative tests only
- {invalidParam}
NOTE: This is a NEGATIVE test. The parameters above are INTENTIONALLY INVALID.
You should ONLY suggest changes to OTHER parameters to make the test trigger a different error response.
DO NOT change the intentionally invalid parameters listed above.

STRUCTURALLY LOCKED PARAMETERS (DO NOT MODIFY):               # if any locked params
These parameters are wired to runtime variables (capturedOutputs from a previous step).
They maintain cross-step data flow and MUST remain unchanged.
- {lockedName}

CRITICAL RULE: Some parameters in the source code are structurally wired to [runtime variables] ...

PARAMETERS USED (Step {failedStep|all}):
```json
{formattedParameters}
```

Based on the error response, suggest improved values for the parameters.
Remember: DO NOT change the intentionally invalid parameters. Only adjust other parameters.   # negative tests
CRITICAL: DO NOT suggest changes for structurally locked parameters listed above.              # if locked
Return ONLY a valid JSON response in the specified format.
````

### 10. Status-code exploration test generation — `StatusCodeExplorationEnhancer`
- **Component:** mist-core · enhancer.
- **Location:** `mist-core/src/main/java/io/mist/core/enhancer/StatusCodeExplorationEnhancer.java`; `buildExplorationSystemPrompt()` (line 704), `buildExplorationUserPrompt(...)` (line 729), `generateText(...)` (line 591).
- **Purpose:** given a test and the set of not-yet-triggered status codes, generate exploration test variants (parameter changes on a target step) that would trigger those codes, without touching dynamically-injected/dependency params.

**System prompt**
```
You are an API testing expert specializing in HTTP status code coverage.
Your task is to generate exploration test cases that trigger specific HTTP status codes.

You will receive:
1. API operation details (method, path, service)
2. Current test parameters and execution result
3. A prioritized list of UNTRIGGERED status codes with suggested inputs

Your job:
- Select which status codes can realistically be triggered by modifying parameters
- Provide EXACT parameter values to trigger each selected status code
- Use the suggested inputs from discovery as a starting point
- Be practical - only suggest codes achievable via parameter changes

IMPORTANT: Respond with valid JSON only. No markdown, no explanations outside JSON.
```

**User prompt** (abridged where it just echoes runtime data)
```
GENERATE EXPLORATION TEST CASES FOR STATUS CODE COVERAGE

=== WORKFLOW SEQUENCE CONTEXT ===                # multi-step workflows only
Step {s}: {method} {path}
⚠️ THIS IS A MULTI-STEP WORKFLOW. You are targeting Step {targetStepIndex} ...

=== TARGET API OPERATION (Step {targetStepIndex}) ===
Service: {serviceName}
Method: {method}
Path: {path}
API: {apiKey}

=== CURRENT TEST PARAMETERS (Step {targetStepIndex}) ===
Test ID: {operationId}
Test Type: NEGATIVE (invalid inputs)|POSITIVE (valid inputs)
Path Parameters: {pathParams}
Query Parameters: {queryParams}
Request Body: {body}
Body Fields: {bodyFields}

🚫 DYNAMICALLY INJECTED PARAMETERS (DO NOT MODIFY THESE):     # if any
  - {depParam}

=== LAST EXECUTION RESULT ===
Actual Status Code (Step {targetStepIndex}): {statusCode}
Response: {truncatedResponse}

=== AVAILABLE STATUS CODES TO EXPLORE (Priority Order) ===
These status codes have NOT been triggered yet. They are listed in priority order.
You can generate exploration tests for ANY of these (suggest multiple if possible).

{priority}. Status {statusCode} ...
   Description: {description}
   Trigger Strategy: {triggerStrategy}
   ⚠️ Requires Auth Manipulation: YES            # if applicable
   📝 Suggested Inputs: {suggestedInputs}

=== YOUR TASK ===
1. Review the available status codes above
2. For EACH status code you think can be triggered, provide:
   - The target status code
   - Your strategy to trigger it
   - The EXACT parameter changes for Step {targetStepIndex}
3. You can suggest MULTIPLE status codes (recommended: 2-5 per test)
4. Only suggest codes that are REALISTICALLY achievable
5. Do NOT suggest changes to dynamically injected parameters listed above   # if any

=== RESPONSE FORMAT (JSON ONLY) ===
{
  "isGoodCandidate": true,
  "reason": "Brief explanation of why this test can trigger these codes",
  "explorations": [
    {
      "targetStatusCode": 400,
      "strategy": "Send malformed request body",
      "parameterChanges": {"<bodyField>": "", "<otherField>": "invalid"},
      "requiresAuthManipulation": false
    },
    {
      "targetStatusCode": 404,
      "strategy": "Request non-existent resource",
      "parameterChanges": {"<pathParam>": "NONEXISTENT_999"},
      "requiresAuthManipulation": false
    }
  ]
}

If this test is NOT a good candidate, respond:
{"isGoodCandidate": false, "reason": "explanation", "explorations": []}
```

### 11. Fault-category mining — `FaultMiner`
- **Component:** mist-core · fault.
- **Location:** `mist-core/src/main/java/io/mist/core/fault/FaultMiner.java`; `SYSTEM_PROMPT` constant (line 88), `buildUserPrompt(spec, responses)` (line 200), `llmClient.prompt(SYSTEM_PROMPT, userPrompt)` (line 154). This is the one site that calls `LLMClient.prompt(...)` directly.
- **Purpose:** from OpenAPI parameter descriptions plus a sample of observed 4xx/5xx responses, propose up to 3 SUT-specific invalid-input categories, as one JSON object per line; candidates are validated against the registry shape and de-duplicated against the eight defaults.

**System prompt**
```
You are an expert REST API security and robustness tester.
Given an OpenAPI parameter description and a sample of observed
4xx/5xx responses for that parameter, propose up to 3 SUT-specific
invalid-input categories the test generator should additionally
exercise. Output each category on its own line as a JSON object
with the keys:
  {"id": "UPPER_SNAKE_CASE_ID",
   "displayName": "human-readable name",
   "applicableTo": ["string"|"integer"|"number"|"boolean"|"array"|"object", ...],
   "applicableLocations": ["path"|"query"|"header"|"cookie"|"body", ...]}
Do not propose categories that overlap with these defaults:
  TYPE_MISMATCH, REGEX_MISMATCH, SEMANTIC_MISMATCH, OVERFLOW,
  EMPTY_INPUT, NULL_INPUT, SPECIAL_CHARACTERS, BOUNDARY_VIOLATION,
  ENUM_VIOLATION.
```

**User prompt** (capped by `MAX_USER_PROMPT_CHARS` / `MAX_OBSERVED_RESPONSES_PER_PROMPT`)
```
SUT: {apiKey}

OpenAPI parameters:                              # if descriptions present
- {paramName}: {description}

Observed 4xx/5xx responses (sample):             # if responses present
- HTTP {statusCode}: {responseBody}
```

### 12. Trace root-cause analysis — `TraceErrorAnalyzer`
- **Component:** mist-core · analysis.
- **Location:** `mist-core/src/main/java/io/mist/core/analysis/TraceErrorAnalyzer.java`; context assembled from line 479, tail appended at line 586, `generateText(system, prompt)` at line 619. Result is cached; falls back to `getFallbackAnalysis` when the LLM is unavailable.
- **Purpose:** from a distributed trace (services, failed spans, stack traces, HTTP methods/endpoints), produce a concise technical `ROOT CAUSE:` + `FIX:` analysis.

**System prompt**
```
You are a microservice debugging expert. Analyze traces and provide direct technical insights.
```

**User prompt**
```
Analyze this microservice distributed trace error:

TRACE OVERVIEW:
- Trace ID: {traceID}
- Total Spans: {n}
- Error Status: FAILED
- Root Cause Failures: {n}
- Total Failed Spans: {n}
- Propagated Failures: {n}

EXACT FAILURE POINTS:
Root Cause #{i}:
- Failed Service: {serviceName}
- Failed Operation: {operationName}
- HTTP Status: {httpStatusCode}
- Execution Time: {duration}ms
- Exact Error: {errorMessage}                    # if present
- Exception Type: {exceptionType}                # if present
- Exact Failure Location:                         # from stack trace, if present
  → {at ...application frame...} ← THIS IS WHERE IT FAILED

SERVICE INTERACTION CONTEXT:
- Services Involved: {service1, service2, ...}
- HTTP Methods Used: {GET, POST, ...}            # if any
- API Endpoints: {n} unique endpoints            # if any

Provide concise technical analysis:
ROOT CAUSE: Exact failing operation and reason
FIX: Specific action needed

Be direct and technical. No conversational language.
```

---

## Not runtime prompts (developer / agent briefs — for disambiguation)

These `PROMPT*`-named files are process documents (execution/verification briefs for human or agent work), **not** prompts MIST sends to an LLM:

- `debug/Conference-refinement/PROMPT_B1_SEVER_RESTEST_INHERITANCE.md`
- `debug/Conference-refinement/PROMPT_H2_ABLATION_INFRASTRUCTURE.md`
- `debug/Conference-refinement/PROMPT_VERIFY_FIXES.md`
- `debug/negative_test/VERIFICATION_PROMPT.md`
