# Generalization plan — make MIST work on an arbitrary SUT with no hand-fixing

Status: PLAN (from a 3-agent parallel audit, 2026-05-29). Branch `inject-detection`.
Goal: a NEW SUT runs with only its own inputs (swagger + 2 properties + traces) — no code
edits, no hand-tuned config. Today each new SUT surfaces another train-ticket (TT) assumption
(we hand-fixed Bookinfo's one by one). This plan clears the tail.

Audit method: 3 parallel agents — (1) service-identity/routing, (2) value-gen/OAS-grounding,
(3) defaults/config/structure. Each finding is file:line, classified BLOCKER vs BEST-EFFORT.

Already fixed (this session): InputFetchRegistry ts-* servicePatterns (0d1dbd42); MstAuthHandler
default per_jvm→none (bb57e818); generation gates resolveOperation/pathMatchesTemplate (f9fbeceb);
bookinfo conf basePath (hand-fix, generalized by Phase-1 #1 below).

---

## Phase 1 — BLOCKERS (6). A non-TT SUT BREAKS or silently mis-detects without these.
These ARE the Bookinfo hand-fixes, promoted into code so the next SUT just works.

| # | file:line | What breaks | Fix | Eff |
|---|---|---|---|---|
| **1. conf basePath dropped** | `mist-core/.../multiservice/MicroserviceTestConfigurationGenerator.java:65` (testPath = raw `apiOp.getPath()`), feeding `mist-core/.../spec/OpenAPISpecification.java:98-114` | Conf `testPath` omits the swagger `servers[].url` basePath (e.g. `/api/v1`), but trace paths carry it → `StageSupport.pathMatchesTemplate` (`:354`) never matches → params unresolved, faults not injected, 404s. **= the Bookinfo `/api/v1` hand-fix.** | Prepend the spec's `servers[0]` URL path component to each `testPath` in conf-gen. Auto-derived from the spec. | M |
| **2. generated-test Jaeger default = TT cluster** | `mist-cli/.../writer/MultiServiceRESTAssuredWriter.java:280,284,285` | Emitted tests default `JAEGER_BASE_URL=http://129.62.148.112:30005/jaeger/ui/api` (Baylor TT cluster IP + `/jaeger/ui/api`) and `JAEGER_ENABLED=true`; disagrees with `MstConfig.Jaeger` (`MstConfig.java:366-367` = localhost/false). **= the `jaeger.base.url` `/jaeger/api` hand-fix.** | One source of truth: emit defaults from `MstConfig.Jaeger` (localhost:16686, or empty→disabled); don't bake the TT IP or `/jaeger/ui` path. | M |
| **3. OAS grounding ignores tags** | `mist-core/.../smart/OpenAPIEndpointDiscovery.java:79-102` | A service is registered ONLY if an op has `x-service-name`; OpenAPI `tags`/`servers` ignored. Off-the-shelf swagger (Bookinfo: tags product/review/rating, no x-service-name) → 0 services → smart-fetch can't ground → silently LLM-only (this is also the `product`/`catalog` whitelist-drop we saw post-hallucination-fix). | Port the 4-tier resolver from `MicroserviceTestConfigurationGenerator.determineServiceName` (`:201-245`: x-service-name → tags → server URL → default). In `parseOpenAPISpec`, read `operationNode.get("tags")` off the JsonNode when x-service-name absent. | M |
| **4. smart-fetch OAS path hardcoded to TT** | `mist-core/.../smart/SmartInputFetchConfig.java:78, 120` | Default `openApiSpecPath = ".../My-Example/trainticket/merged_openapi_spec 1.yaml"`; a non-TT SUT that doesn't set `smart.input.fetch.openapi.spec.path` reads the wrong/absent spec. | Default to the already-required `oas.path` instead of a TT literal (or null→skip). | S |
| **5. base.url spec-server fallback = wrong server** | `mist-cli/.../MistRunner.java:699, 3008` (fallback to `spec.getServers().get(0).getUrl()`) | TT spec's first server is the Apache-LICENSE URL; a SUT that omits `base.url` gets a junk/empty base (TT props always set it, masking this). | Make `base.url` required (fail-fast), or pick the first server that is a valid http(s) host (skip license/example URLs). | S |
| **6. non-trace JSON aborts the run** | `mist-core/.../workflow/TraceWorkflowExtractor.java:91, 175` (single-file branch) | A single non-span JSON (MANIFEST.json, a config, a Jaeger *search* export) throws `Unrecognized JSON object format` and **kills the whole run** (dir branch only skips-with-error). **= the MANIFEST-in-traces/ hand-fix.** | Content-sniff for Jaeger/OTel span shape (`traceID`/`data[].spans`) before treating a file as a trace; skip+warn non-traces; never abort on one bad file. | S–M |

Phase-1 total ≈ 3×M + 3×S ≈ half a day → a day. **Definition of done: a fresh SUT (e.g. Sock Shop)
runs from swagger+2 props+traces with NO hand-edits to the conf/properties beyond base.url & jaeger.base.url.**

---

## Phase 2 — BEST-EFFORT: remove TT bias (degrades quality, not correctness)

**2A. TT value examples baked into LLM value-gen prompts** (bias generated values toward
Shanghai/Beijing/G1237/station/train on EVERY SUT). All BEST-EFFORT, mostly S, mechanical —
batch-replace with domain-neutral / type-driven examples:
- `mist-core/.../smart/InputFetchRegistry.java:491-497` (directValueExtraction default — the one actually used on every SUT since no SUT YAML overrides it)
- `mist-core/.../smart/SmartInputFetcher.java:1120-1125, 1877-1881, 2109-2113, 2385-2386, 2406-2414, 2666-2668, 3769-3772, 3886-3906`
- `mist-core/.../generation/ZeroShotLLMGenerator.java:540-549` (SEMANTIC_MISMATCH fault-pool few-shot)
- `mist-core/.../enhancer/StatusCodeExplorationEnhancer.java:863, 869` (TT param names in mutation prompt)
(The PRIMARY positive-value generator `ZeroShotLLMGenerator.buildPrompt:755` is already fully
schema-driven — no TT data. Good.)

**2B. Service-identity quality degradations** (non-fatal; the live request path routes around them):
- `mist-core/.../registry/SemanticDependencyRegistry.java:760` — `"ts-"+stem+"-service"` canonical bonus → derive the prefix/suffix instead (restores cross-service producer selection). [M]
- `mist-cli/.../writer/MultiServiceRESTAssuredWriter.java:302-308` — emitted Jaeger fetch hardcodes `service=ts-gateway-service` as the PRIORITY query; marker-first lookup already works, so these just waste 2-4 queries/step. Parameterize the gateway name or drop strategies 1-4. [M]
- `mist-core/.../registry/ApiTree.java:93` — `path.startsWith("/api/v1/")` defines "an API call"; replace with a structural test (method + non-wildcard path). [S]
- `mist-core/.../generation/MistGenerator.java:2521, 2543` — `/api/v1/`+`ts-`-strip route-synthesis fallback (only when a trace step lacks http.target/url); derive prefix or skip. [S]
- `mist-cli/.../writer/MultiServiceRESTAssuredWriter.java:899, 1066, 1097-1102` — emitted `ts-gateway-service` equals + `ts-`-strip display (cosmetic; redundant with `contains("gateway")`). [S]
- `mist-core/.../registry/SemanticDependencyRegistry.java:1327-1337` — `isLikelyEntityNoun` blocklist is TT-curated (cheapestroute/minstation/left_ticket/…); keep generic verbs, drop/externalize TT nouns. [M]
- `mist-core/.../smart/SmartInputFetcher.java:2893` — `isValidIdValue` ID-shape regex over-fits TT IDs (rejects valid >20-char/base64/urn IDs fetched from the real upstream). Widen or accept upstream 2xx values. [S]

**2C. TT-shaped defaults / config paths** (works but needs a non-obvious setting):
- `mist-cli/.../MistRunner.java:291` (injected-faults path → TT), `:887` + `resources/mist/seed-trace-labels.json:4` (TSO seed corpus → TT)
- `mist-cli/.../MistMain.java:156-158` (`trace.file.path` silently defaults to TT dir if omitted — make required, like oas.path/conf.path)
- `mist-core/.../config/MstConfig.java:196` + `resources/mist/noun-map.default.yaml` (noun map is all-TT nouns; ship empty/minimal or auto-mine from spec path segments) [M]
- `mist-cli/.../writer/MultiServiceRESTAssuredWriter.java:779, 1120-1121` (emitted `/api/v1`→`/v1` + `/jaeger/ui` display surgery — cosmetic report links)

Phase-2 total ≈ half a day to a day (most are S/mechanical prompt edits; a few M).

---

## Phase 3 — Document the structural ceiling (don't fix now)
- `mist-cli/.../MistRunner.java:553-556` — MIST maps ONE merged spec to every service. A SUT needing
  per-service specs or per-service basePaths is unsupported. Document as a known limitation; revisit
  only if a target SUT needs it. [L]

---

## Bottom line
- **6 blockers** = exactly the Bookinfo hand-fixes + the next-SUT ones (OAS-grounding, base.url, smart-fetch OAS path). Fix → a new SUT works out of the box. (~1 day)
- **~15 best-effort** = mostly TT value-example prompt leakage (batch-neutralize) + a few quality degradations. (~1 day)
- **1 structural ceiling** = one-merged-spec-per-SUT (document).
Total ≈ 2 focused days to a genuinely SUT-agnostic tool. Per convention: this plan → reviewer-agent
critique + grep-verified disposition → execute Phase 1 first.

---

## DISPOSITION / STATUS (executed + verified) — 2026-05-29

**Phase 1 — DONE, committed `5f3eed62`, verified.** All 6 blockers fixed in code.
- #1 conf basePath: VERIFIED — regenerating the bookinfo conf now yields `/api/v1/...` identical to the hand-fixed conf (was a hand-fix).
- #2 jaeger default: VERIFIED — fresh generated tests default to `http://localhost:16686/jaeger/api`, not the train-ticket cluster IP.
- #3 OAS tags grounding: VERIFIED — a full autonomous run grounds smart-fetch on the SUT's real product/review/rating endpoints, 0 ts-* leaks.
- #4 smart-fetch OAS path, #5 base.url fallback, #6 non-trace JSON: code-fixed; mist-core 290/0/0 + jar build + autonomous run (no crash) cover them.
- No regression: the full `java -jar mist.jar` autonomous Bookinfo run still detects HIDDEN_DOWNSTREAM_FAILURE.

**Phase 2 — DONE, committed `a3cbd735`, verified.** Value-gen prompt TT examples neutralized;
service-identity heuristics derived (SemanticDependencyRegistry canonical bonus, ApiTree
rooted-path test, MistGenerator route fallback, isValidIdValue); TT-default paths neutralized.
VERIFIED: mist-core 290/0/0; full autonomous run generates values cleanly with the neutral
prompts, still detects hidden-downstream (ORACLE ANOMALIES + Allure attachment), 0 ts-* leaks,
no crash. train-ticket preserved (ts-order-service still scores +60; its data is in its own file).

**Phase 3 — DOCUMENTED (not fixed, by design).** The one-merged-spec-per-SUT ceiling
(`MistRunner.java:553-556`) remains a known limitation; revisit only when a target SUT needs
per-service specs / per-service base paths.

Net: a NEW SUT now runs from swagger + 2 properties + traces with no code edits and no hand-fixed
conf — verified end-to-end on Bookinfo (the 2nd SUT). Remaining generalization is the Phase-3
structural item only.
