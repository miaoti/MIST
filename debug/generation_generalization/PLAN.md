# Generation-generalization — decouple MIST generation from train-ticket structure

Status: DRAFT for reviewer-agent critique. 2026-05-29. Branch `inject-detection`.

## Goal
Make trace→scenario→variant generation work on **arbitrary** microservice SUTs (BFF + mesh,
e.g. Bookinfo: `istio-ingressgateway` → `productpage` → reviews/ratings/details) **without
per-SUT hand-tuning**, while keeping train-ticket byte-identical (mist-core 269/0/0 + still
generates variants).

## The coupling (investigation, file:line)
- **`MistGenerator.subtreeHasConfiguredService` / `scenarioHasBuildableRoot` (MistGenerator.java:~2893-2913):**
  a scenario is buildable only if some step's service is in `serviceConfigs` via **exact** key match
  (`serviceConfigs.containsKey(service)`). Bookinfo: trace services `productpage.default` /
  `istio-ingressgateway` ∉ conf services `{product,review,rating}` (swagger *tags*) → logs
  *"no root step resolves to a service in serviceConfigs"* → **0 variants**.
- **`MistGenerator.traverse` (~971):** `serviceConfigs.get(service)` exact match → null for Bookinfo → subtree skipped.
- **Gateway detection** (`StageSupport.java:68`, `MistGenerator.java:2522`): `serviceLower.contains("gateway")` — already generic (catches `istio-ingressgateway`). OK.
- **Hardcoded train-ticket names:** Jaeger trace-fetch gateways `ts-gateway-service`/`gateway-service`/`api-gateway` (MultiServiceRESTAssuredWriter.java:302-308); smart-fetch service patterns `ts-user-service`/`ts-route-service`/… (InputFetchRegistry.java:459-468).

## Design — endpoint-based matching (the generalization; NOT per-SUT aliases)
Rejected: service-name **aliases** (productpage.default→product) — that is per-SUT hand-tuning in disguise.
Chosen: match a scenario step to a conf operation by **endpoint (HTTP method + path)**, used **only as a
fallback** when service-name matching fails. Service-name match stays FIRST, so train-ticket is unaffected.

1. **`StageSupport.findOperationByEndpoint(serviceConfigs, verb, route)`** (NEW): iterate all
   `serviceConfigs.values()`, return the first `findOperation(cfg, verb, route)` hit (reusing the existing
   path-template matcher). If >1 service matches, prefer the step's own service if present, else first + log.
2. **`MistGenerator.traverse` (~971):** if `serviceConfigs.get(service)`==null, OR its `findOperation`==null,
   fall back to `findOperationByEndpoint`. Only then skip.
3. **`subtreeHasConfiguredService` (~2904):** a step is buildable if `service ∈ serviceConfigs` **OR** its
   endpoint matches any conf operation (`findOperationByEndpoint != null`).
4. **Configurable entry/gateway service names (de-hardcode, lower priority):** add a property
   `mist.generation.gateway.service.names` (CSV; default keeps the `contains("gateway")` behavior) and replace
   the hardcoded Jaeger trace-fetch gateway names with a configurable list (default = the current train-ticket
   names, for backward-compat). The marker-first exact-trace lookup is the primary fetch path, so this is
   cleanliness, not a blocker.

(Out of scope for now: InputFetchRegistry's hardcoded smart-fetch patterns — smart-fetch is best-effort and
does not block generation; deriving them from traces is a follow-up.)

## Backward-compatibility (train-ticket)
Service-name match is attempted FIRST; the endpoint fallback fires only when it returns null. Train-ticket's
trace services equal its conf services, so the fallback never triggers → train-ticket output unchanged.

## Risks / open questions (for the reviewer)
1. **Endpoint ambiguity:** same method+path in >1 service config → which operation wins? (BFF: the entry
   service.) Is "prefer step's own service, else first + log" safe, or could it pick a wrong op?
2. **Path templating:** does `findOperation` already match a concrete trace path (`/products/0`) to a
   templated conf path (`/products/{id}`)? If not, the endpoint fallback won't match Bookinfo either.
3. **Over-generation:** could the fallback now build variants for steps that were *intentionally* skipped
   (e.g. a downstream service with no test config)? Should the fallback be gated (only for the ROOT step, or
   only when the scenario would otherwise yield 0 variants)?
4. Is `scenarioHasBuildableRoot` the only gate, or are there other exact-name matches downstream that would
   still skip Bookinfo?

## Verification (definition of done)
- **Train-ticket (no regression):** mist-core 269/0/0; a train-ticket generation run still produces the same
  variants (endpoint fallback never fires).
- **Bookinfo (unblocked):** with `/products`-API traces (matching the swagger) + the swagger-derived conf,
  generation produces variants (no "0 variants") and MIST runs to completion. (Capturing the API traces is a
  separate input step done during verification; the code change is SUT-agnostic.)

## Note
This is the generation-side generalization. The hidden-downstream **oracle** already works on Bookinfo
independently (committed evidence). Together they give: MIST *generates+runs* on a 2nd SUT (multi-SUT A-bar)
AND the oracle catches hidden-downstream there.

---

## Disposition (post reviewer-agent critique, grep-verified) — 2026-05-29
Every claim grep-verified against the code (the reviewer's specific line refs were partly imprecise; corrected).

| # | Sev | Disposition | grep-verified finding |
|---|---|---|---|
| 1 | blocker | ACCEPT | CONFIRMED **4** exact-service gates (not 2): `MistGenerator.java:971` (traverse) + `:2904` (subtreeHasConfiguredService) AND `io.mist.core.workflow.pipeline.stages.SharedPoolSupport.java:154` (pool) + `:384` (faulty pool). Fixing only the first 2 → empty pools → **no negative tests + junk positives**. Apply via ONE shared helper at all four. |
| 2 | major | CORRECTED | `findOperation` (`MistGenerator.java:2214-2223`) matches by **exact** `path.equals(getTestPath())`; there is **no** `pathMatchesTemplate` (the reviewer's StageSupport ref doesn't exist). So endpoint matching alone is insufficient — must **ADD** template matching (`/products/0/ratings` ↔ `/products/{id}/ratings`). Train-ticket unaffected (no path-params → exact==template). |
| 3 | major | ACCEPT | Fallback fires on BOTH `cfg==null` AND `opCfg==null` at every site. DoD = byte-diff of train-ticket generated `.java`. |
| 4 | major | ACCEPT | Tie-break "prefer own service" is incoherent (fallback runs only when service∉conf). Fix: collect ALL template-matches; ==1 → use; >1 → disambiguate by conf-name being a host/prefix of the trace service (trace `productpage.default` ↔ conf `productpage`), else WARN with all candidates + skip (never silent-pick). |
| 5 | major | ACCEPT | Gate the endpoint fallback to the ROOT / first-business step only. |
| 6 | major | PARTIAL | Writer's hardcoded gateways (`MultiServiceRESTAssuredWriter.java:302-312`) are a FALLBACK; the PRIMARY fetch is the marker-first exact-ID lookup (`writer:343-354`, `GET /traces/<markerId>`) which works for Bookinfo (Istio honors W3C trace context). Make the list configurable (cleanliness); marker-first stays primary. Not a hard blocker. |
| 8 | minor | NOTE | `MistRunner` maps every service to one merged spec — pre-existing single-spec limitation; Bookinfo runs from one merged swagger, unaffected now. |

## REVISED design (supersedes §Design)
Service-name match FIRST, then a **template-aware endpoint fallback**, at all four gates, root-gated, real tie-break.
1. **`pathMatchesTemplate(concretePath, templatePath)`** (new util): split on `/`, equal segment count; each template segment is `{...}` (matches any non-empty non-`/` literal) or literal-equal (`Pattern.quote` literals); normalize trailing `/` and strip `?query`.
2. **Extend `findOperation` (2221-2223):** exact `path.equals` first, then `pathMatchesTemplate`. (Train-ticket exact paths still match exactly.)
3. **`resolveOperation(serviceConfigs, service, verb, route)`** (new shared helper): (a) `serviceConfigs.get(service)` + extended `findOperation` → return if non-null; (b) ENDPOINT FALLBACK — across all serviceConfigs collect ops with method==verb && `pathMatchesTemplate`; ==1 → use; >1 → disambiguate by service-name affinity else WARN+null.
4. **Apply `resolveOperation` at the 4 gates** (971, 2904, 154, 384), firing the fallback on `cfg==null` OR `opCfg==null`; gate the fallback to the root/first-business step.
5. **(minor)** configurable gateway list for the writer's Jaeger fetch (default = current); marker-first primary.

## Verify (DoD)
- Train-ticket: mist-core 269/0/0; **byte-diff of generated `.java` (a generation-only run before/after) is empty** (fallback never fires; exact-first preserved).
- Bookinfo: with `/products`-API traces + the swagger conf, generation produces NON-empty faulty + positive pools → variants → MIST runs to completion with tests for `/products` ops.
