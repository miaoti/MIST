# MIST — Prompt Inventory

**Single catalogue of every LLM prompt MIST sends at runtime: where it lives, what it does, and which component owns it.**

This lists the prompts that are actually built and dispatched to a language model during a MIST run. Each row is self-contained (full `file:line` path + purpose + owning component) so a single grep/Ctrl-F tells you both *where* the prompt is and *what* it is for.

> ⚠️ **Maintenance contract.** When you add, remove, or materially change a runtime LLM prompt (its system text, output contract, or the call site), update its entry here in the same change. Keep this the single prompt catalogue. See also `FILE_INDEX.md` and the project memory entries `file-index-consult-first` / `file-index-maintenance`.

## Scope — what counts as a "prompt" here

- **In scope:** text assembled in code and passed to `LLMService.generateText(...)` / `LLMClient.prompt(...)` during a run. All of these live in **`mist-core`**.
- **`mist-llm`** is the transport/SPI layer (`LLMClient`, `LLMRequest`, `LLMService`, backend adapters for OpenAI-compatible / Gemini / Ollama). It routes prompts and caches responses but **defines no prompt text of its own.**
- **`mist-cli`** only *wires up* the prompt-owning classes (`MistRunner` constructs `TestCaseEnhancer` / `StatusCodeExplorationEnhancer`); it holds **no prompt text.**
- **Not runtime prompts:** the `debug/Conference-refinement/PROMPT_*.md` and `debug/negative_test/VERIFICATION_PROMPT.md` files are developer/agent *execution briefs* (process docs), not prompts MIST sends to an LLM. They are listed at the bottom for disambiguation only.

## How to use this catalogue

- **Find a prompt:** grep the prompt name, the class, or a keyword from its system line. Every row carries the full `file:line`.
- **Line numbers** are anchors as of the generated date below; if code has shifted, grep the quoted system-prompt line to relocate.

_Generated 2026-07-13. Runtime prompt sites: 12, across 8 files, all in `mist-core`._

## Contents — prompt sites at a glance

| # | Prompt | Component / area | File · anchor | Purpose (one line) | maxTokens · temp |
|---|--------|------------------|---------------|--------------------|------------------|
| 1 | Parameter value generation | mist-core · generation | `mist-core/src/main/java/io/mist/core/generation/ZeroShotLLMGenerator.java` · `buildPrompt`:815 / `callLLM`:975 | Produce N realistic, constraint-valid values for any API parameter | 200 · 0.7 |
| 2 | Soft-failure (hidden-error) validation | mist-core · generation | `mist-core/src/main/java/io/mist/core/generation/ZeroShotLLMGenerator.java` · `validateResponse`:1343 | Decide if a 2xx response actually FAILED (status:0 / error msg / null data) | 500 · 0.3 |
| 3 | Negative-test verdict validation | mist-core · generation | `mist-core/src/main/java/io/mist/core/generation/ZeroShotLLMGenerator.java` · `validateNegativeTestResponse`:1470 | Decide if an intentionally-invalid input was correctly rejected *for the right reason* | 500 · 0.3 |
| 4 | Service-discovery (which service holds this data) | mist-core · smart input fetch | `mist-core/src/main/java/io/mist/core/smart/SmartInputFetcher.java` · `discoverByLLM`:592 / `buildLLMDiscoveryPrompt` | Ask which SUT service could supply a real value for a parameter | (default) |
| 5 | Direct value extraction from a response | mist-core · smart input fetch | `mist-core/src/main/java/io/mist/core/smart/SmartInputFetcher.java` · `buildDirectExtractionPrompt`:975 | Pull the right field value out of a fetched JSON response body | (default) |
| 6 | Semantic field matching | mist-core · smart input fetch | `mist-core/src/main/java/io/mist/core/smart/SmartInputFetcher.java` · :1223 | Map a parameter name to the most semantically relevant response field (or `NO_MATCH`) | (default) |
| 7 | Parameter-error classification | mist-core · smart input fetch | `mist-core/src/main/java/io/mist/core/smart/ParameterErrorAnalyzer.java` · :165 | Classify whether an API failure was caused by a specific input parameter, and which | (default) |
| 8 | HTTP status-code discovery | mist-core · coverage | `mist-core/src/main/java/io/mist/core/coverage/LLMStatusCodeDiscovery.java` · `buildSystemPrompt`:188 / `buildDiscoveryPrompt`:196 | Enumerate ALL status codes an operation can return + trigger strategy + suggested inputs (JSON) | 2000 · 0.3 |
| 9 | Failed-test parameter enhancement | mist-core · enhancer | `mist-core/src/main/java/io/mist/core/enhancer/TestCaseEnhancer.java` · `buildSystemPrompt`:414 / `buildUserPrompt`:434 | Suggest improved parameter values to make a failed test pass (respecting locked/invalid params) | (config) |
| 10 | Status-code exploration test generation | mist-core · enhancer | `mist-core/src/main/java/io/mist/core/enhancer/StatusCodeExplorationEnhancer.java` · `buildExplorationSystemPrompt`:704 / `buildExplorationUserPrompt`:735 | Generate exploration test variants that trigger untriggered status codes (JSON) | (config) |
| 11 | Fault-category mining | mist-core · fault | `mist-core/src/main/java/io/mist/core/fault/FaultMiner.java` · `SYSTEM_PROMPT`:88 / `buildUserPrompt`:200 | Mine up to 3 SUT-specific invalid-input fault categories from spec + observed 4xx/5xx (JSON-per-line) | (default) |
| 12 | Trace root-cause analysis | mist-core · analysis | `mist-core/src/main/java/io/mist/core/analysis/TraceErrorAnalyzer.java` · :586 | Root-cause + fix from a distributed trace (ROOT CAUSE / FIX) | (default) |

All prompts flow through `mist-llm`'s `LLMService.getInstance(...).generateText(system, user, [maxTokens, temperature])` (or `LLMClient.prompt(system, user)` for #11), which routes to the configured backend and caches via `LLMCallCache` so seeded reruns short-circuit the backend.

---

## Detailed entries

### 1. Parameter value generation — `ZeroShotLLMGenerator`
- **Component:** `mist-core` — generation (the primary input-generation path).
- **Location:** `mist-core/src/main/java/io/mist/core/generation/ZeroShotLLMGenerator.java`; user prompt built in `buildPrompt(param, howMany)` (line 815), system prompt + call in `callLLM(prompt)` (line 975, `generateText(system, prompt, 200, 0.7)` at line 991).
- **Purpose:** given one OpenAPI parameter (name, location, type/format, description, example, enum/bounds/length/regex constraints, sibling params), produce `howMany` distinct, realistic, strictly-valid values — one per line, or a JSON array for `array`-typed params.
- **System prompt (verbatim head):** _"You are an expert API tester specialising in test data generation. Your sole task is to produce realistic, constraint-compliant values… (1) return EXACTLY N items… (4) Never add markdown fences… (5) respect Type, Format, Enum, and numeric/length Constraints… (6) if an enum list is provided, output ONLY values from that list."_
- **User prompt head:** _"You are an expert API tester. Generate {N} distinct, highly realistic, and strictly valid values… Current Date/Time: …"_ then `[API Context]` / `[Parameter Details]` / `[Constraints]` blocks.

### 2. Soft-failure (hidden-error) validation — `ZeroShotLLMGenerator`
- **Component:** `mist-core` — generation / response validation.
- **Location:** same file; `validateResponse` builds system prompt at line 1343 and calls `generateText(..., 500, 0.3)` at line 1404.
- **Purpose:** detect "success-looking" 2xx responses that actually failed (e.g. `status:0`, `success:false`, error message fields, null/empty `data`, business-logic validation errors). Output contract: two lines — `FAILED: true|false` and `RCA: <root cause>`.
- **System prompt (verbatim head):** _"You are an API testing expert analyzing response data."_
- **Caching:** signature-keyed via `PROP_VALIDATION_CACHE_PATH` (`.mist/llm-validation-cache.json`).

### 3. Negative-test verdict validation — `ZeroShotLLMGenerator`
- **Component:** `mist-core` — generation / negative-test validation.
- **Location:** same file; `validateNegativeTestResponse` builds system prompt at line 1470, calls `generateText(..., 500, 0.3)` at line 1565.
- **Purpose:** for an intentionally-invalid input, decide whether the API rejected it *for a reason related to the designed invalid parameter* (test PASSES) versus accepted it or failed for an unrelated reason (test FAILS). Output contract: three lines — `FAILED`, `RELATED_TO_INVALID_INPUT`, `RCA`.
- **System prompt (verbatim head):** _"You are an API testing expert validating NEGATIVE TEST results."_

### 4. Service-discovery — `SmartInputFetcher`
- **Component:** `mist-core` — smart input fetch (fetch real values from live SUT services instead of synthesizing them).
- **Location:** `mist-core/src/main/java/io/mist/core/smart/SmartInputFetcher.java`; `discoverByLLM(parameterInfo)` (line 592) builds the prompt via `buildLLMDiscoveryPrompt(...)` (called at line 600) and calls `askLLMForServices(...)`.
- **Purpose:** given a parameter and the known service set, ask the LLM which SUT service(s) could provide a real value for it. LLM answers with service names or the sentinel `NO_GOOD_MATCH`; results are whitelisted against known services before being persisted to the input-fetch registry.

### 5. Direct value extraction — `SmartInputFetcher`
- **Component:** `mist-core` — smart input fetch.
- **Location:** same file; `buildDirectExtractionPrompt(responseBody, parameterInfo)` (line 975).
- **Purpose:** given a fetched response body and the target parameter, extract the appropriate concrete value (used instead of brittle JSONPath).

### 6. Semantic field matching — `SmartInputFetcher`
- **Component:** `mist-core` — smart input fetch.
- **Location:** same file; prompt built around line 1223.
- **Purpose:** map a parameter name to the most semantically relevant field in the available data, respecting value-type compatibility (don't match a UUID to a numeric/distance param). Returns the field name only, or `NO_MATCH`. Includes worked examples (`origin→from`, `destination→to`, `distance→price`, …).

### 7. Parameter-error classification — `ParameterErrorAnalyzer`
- **Component:** `mist-core` — smart input fetch (failure attribution).
- **Location:** `mist-core/src/main/java/io/mist/core/smart/ParameterErrorAnalyzer.java`; prompt at line 165, `generateText(system, prompt)` at line 181. (A deterministic trace-pattern extraction short-circuits the LLM when the failure already names the parameter.)
- **Purpose:** classify whether an API failure is caused by an input parameter and, if so, which one and what category. Output contract: `PARAMETER_ERROR: YES/NO`, `PARAMETER: <name>`, `ERROR_TYPE: <VALIDATION_ERROR|TYPE_MISMATCH|FORMAT_ERROR|NULL_ERROR|CONSTRAINT_ERROR>`.
- **System prompt (verbatim):** _"You are an API testing expert. Analyze API failures to identify which parameter caused the issue."_

### 8. HTTP status-code discovery — `LLMStatusCodeDiscovery`
- **Component:** `mist-core` — coverage.
- **Location:** `mist-core/src/main/java/io/mist/core/coverage/LLMStatusCodeDiscovery.java`; `buildSystemPrompt()` (line 188), `buildDiscoveryPrompt(...)` (line 196), `generateText(system, prompt, 2000, 0.3)` (line 142).
- **Purpose:** for one API operation, enumerate ALL HTTP status codes it could return, each with category, description, trigger strategy, `requiresAuthManipulation`, and suggested inputs — returned as a JSON array (falls back to `createDefaultTargets` on empty/unparseable output).
- **System prompt (verbatim head):** _"You are an API testing expert specializing in HTTP status codes and REST API behavior… Always respond with valid JSON only."_

### 9. Failed-test parameter enhancement — `TestCaseEnhancer`
- **Component:** `mist-core` — enhancer.
- **Location:** `mist-core/src/main/java/io/mist/core/enhancer/TestCaseEnhancer.java`; `buildSystemPrompt()` (line 414), `buildUserPrompt(failedTest)` (line 434), `generateText(system, user, maxTokens, temperature)` (line 83).
- **Purpose:** analyze a failed test and suggest improved parameter values that are more likely to pass — while never changing intentionally-invalid params (negative tests) or structurally-locked params (wired to captured outputs of prior steps). Returns JSON.
- **System prompt (verbatim head):** _"You are an expert API test case analyzer and enhancer."_

### 10. Status-code exploration test generation — `StatusCodeExplorationEnhancer`
- **Component:** `mist-core` — enhancer.
- **Location:** `mist-core/src/main/java/io/mist/core/enhancer/StatusCodeExplorationEnhancer.java`; `buildExplorationSystemPrompt()` (line 704), `buildExplorationUserPrompt(...)` (line 735), `generateText(...)` (line 591).
- **Purpose:** given a test and the set of not-yet-triggered status codes, generate exploration test variants (parameter changes per target step) that would trigger those codes, without touching dynamically-injected/dependency params. Returns JSON (`isGoodCandidate`, `explorations[]`).
- **System prompt (verbatim head):** _"You are an API testing expert specializing in HTTP status code coverage."_

### 11. Fault-category mining — `FaultMiner`
- **Component:** `mist-core` — fault.
- **Location:** `mist-core/src/main/java/io/mist/core/fault/FaultMiner.java`; `SYSTEM_PROMPT` constant (line 88), `buildUserPrompt(spec, responses)` (line 200), `llmClient.prompt(SYSTEM_PROMPT, userPrompt)` (line 154). This is the one site that calls `LLMClient.prompt(...)` directly.
- **Purpose:** from OpenAPI parameter descriptions plus a sample of observed 4xx/5xx responses, propose up to 3 SUT-specific invalid-input categories for the generator to additionally exercise. Output: one JSON object per line (`id` UPPER_SNAKE_CASE, plus category fields); candidates are validated against the registry shape and de-duplicated against the eight defaults.
- **System prompt (verbatim head):** _"You are an expert REST API security and robustness tester."_

### 12. Trace root-cause analysis — `TraceErrorAnalyzer`
- **Component:** `mist-core` — analysis.
- **Location:** `mist-core/src/main/java/io/mist/core/analysis/TraceErrorAnalyzer.java`; prompt assembled at line 586, `generateText(system, prompt)` at line 619.
- **Purpose:** from a distributed trace (services, HTTP methods, endpoints, failed spans), produce a concise technical `ROOT CAUSE:` + `FIX:` analysis. Result is cached; falls back to `getFallbackAnalysis` when the LLM is unavailable.
- **System prompt (verbatim):** _"You are a microservice debugging expert. Analyze traces and provide direct technical insights."_

---

## Not runtime prompts (developer / agent briefs — for disambiguation)

These `PROMPT*`-named files are process documents (execution/verification briefs for human or agent work), **not** prompts MIST sends to an LLM:

- `debug/Conference-refinement/PROMPT_B1_SEVER_RESTEST_INHERITANCE.md`
- `debug/Conference-refinement/PROMPT_H2_ABLATION_INFRASTRUCTURE.md`
- `debug/Conference-refinement/PROMPT_VERIFY_FIXES.md`
- `debug/negative_test/VERIFICATION_PROMPT.md`
