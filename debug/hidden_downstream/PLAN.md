# Hidden-Downstream Oracle — code-modification plan (task 1 + task 2)

Status: DRAFT for reviewer-agent critique. Date 2026-05-29. Branch `inject-detection`.

## Goal (the contribution's load-bearing mechanism)
Make `HiddenDownstreamFailure` the **defensible, label-free, intent-conditioned** detector that
is the paper's novel core — a swallowed downstream 5xx behind a clean 2xx, **invisible to every
response-level oracle including MIST's own soft-error check**. Two tasks:

- **Task 1 (tool only):** refine the oracle so it fires *only* on the genuinely-hidden case and
  cannot be dismissed as "just a 5xx is obviously a bug." Verified by unit tests.
- **Task 2 (tool + 1 SUT mutant):** demonstrate it end-to-end on the live train-ticket SUT with a
  controlled hidden-downstream mutant as ground truth — the A/B evidence (status/fault-name oracle
  = UNDETECTED, trace oracle = FIRES). Involves a ~20-min SUT rebuild.

## Agreed semantic rule (from the soft-error discussion)
> Hidden-downstream fires iff the system **claims success** — root span 2xx **and** the
> response-level checks (HTTP status + LLM soft-error) found nothing — **yet** a non-root descendant
> span genuinely server-errored (HTTP ≥500 or otel=ERROR). The clean "success" claim is the per-test
> spec; the trace contradicts it. No human assertion, no trained baseline = label-free.

Soft-error (root body has an error) and hidden-downstream (descendant span 5xx, clean body) are
**complementary**, partitioned by *where the error is*. They only overlap if the system both calls a
downstream that 5xx'd **and** surfaces it as a soft-error in the body — rare, and the gate below
removes even that.

---

## Current state (from code map — file:line)
- `HiddenDownstreamFailureInvariant.java:42–82` — fires on (no root server-error) + (≥1 root 2xx) +
  (≥1 non-root descendant with `httpStatus>=500 || otelStatus==ERROR`). 4xx excluded. **Intent-AGNOSTIC.**
  Kind `HIDDEN_DOWNSTREAM_FAILURE`, severity ERROR.
- Soft-error verdict: emitted in generated test code, `MultiServiceRESTAssuredWriter.java:~2017–2102`,
  boolean `llmDetectedRelatedError`; only computed on 2xx responses; negative-test pass =
  `statusCodeIndicatesError || llmDetectedRelatedError`.
- Oracle invoked: `MultiServiceRESTAssuredWriter.java:~685` `oracle.evaluate(model, rootApiKey,
  targetService, targetParam)`; recorded via `FaultDetectionTracker.recordVerdict(...)` at `~720–724`
  with `markerTraceId`.
- **No single per-test record holds BOTH** the soft-error verdict and the oracle outcome (they flow
  separately into the tracker). This is the central plumbing question for Task 1.
- Intent signals: `targetService`/`targetParam` from the faulty test case (`~1339–1365`), null on
  positive tests; `isNegativeTest = scenario.getFaulty() || mstc.hasSyntheticPlaceholder()` (`~1388`).
- Fault registry: `mist-cli/.../trainticket/injectedFaults/injected-faults.json` (10 faults, tool-side);
  detection by SUT body `{"data":{"injected":true,"faultName":"NAME"}}` → `recordDetectedFault`.
- Report: DETECTED (by fault name) / UNDETECTED (registry − detected) / ORACLE ANOMALIES (gated by
  `mist.report.oracle.anomalies.enabled`).

---

## TASK 1 — oracle refinement (tool only)

### 1A. Response-level-clean gate (the user's core ask — removes soft-error overlap)
Fire hidden-downstream **only when the response-level checks reported success** (status 2xx AND
soft-error found nothing). This makes "fire only when body clean" explicit rather than relying on
structural separation alone.

**Design options (reviewer to pick):**
- **Option A (recommended) — thread the signal into the detector.** Add an additive overload
  `TraceShapeOracle.evaluate(model, rootApiKey, targetService, targetParam, boolean responseLevelClean)`
  (existing 4-arg overload delegates with `responseLevelClean=true` to preserve all current call
  sites + unit tests). `HiddenDownstreamFailureInvariant` receives `responseLevelClean` and returns
  `pass()` when it is false. Cohesive + unit-testable; the rule lives in the detector.
  - Plumbing: in the generated test, compute `responseLevelClean = !statusCodeIndicatesError &&
    !llmDetectedRelatedError` and pass it to the oracle-eval call site. **Risk:** the soft-error
    boolean and the oracle call may sit in different emitted methods; if so, thread the boolean
    through `attachJaegerTrace(...)` (confirm exact method at execution).
- **Option B — report-level reconciliation.** Keep the oracle trace-only; record the per-test
  soft-error verdict alongside the oracle outcome (keyed by `markerTraceId`) and dedupe/annotate in
  the report. Less invasive to the detector but scatters the rule into the tracker/report.
- **Option C — structural only (v1 minimal).** Do nothing for the gate; argue non-overlap purely
  structurally (root-body vs descendant-span) and accept the rare double-fire. Cheapest; weakest
  against the explicit ask.

**Recommendation:** Option A. Fall back to B if the soft-error boolean cannot be reached at the
oracle call site without large churn.

### 1B. Intent framing (label-free)
The `responseLevelClean` gate IS the intent-conditioning: the system's clean-success claim is the
spec, the descendant 5xx is the violation. Update the invariant Javadoc + the outcome message to
state this precisely (e.g., *"system claimed success (2xx, no soft-error) but downstream span <id>
server-errored — swallowed failure invisible to response-level oracles"*). No new labels, no model.

### 1C. Precision guard vs graceful degradation (optional / v2)
A tolerated side-failure (optional service down, fallback) could be a false positive. Label-free
mitigation: require the server-error span to be a **synchronous descendant on the call path toward
the root** (not a detached/async span). Implement only if span parent/timing data supports it
cleanly; otherwise document as a known precision limitation and defer. (Reviewer: in or out for v1?)

### 1D. Tests (mist-core, must stay green)
- fires: clean-2xx root + descendant 5xx + `responseLevelClean=true`.
- suppressed: same trace but `responseLevelClean=false` (soft-error already flagged).
- suppressed: no descendant 5xx; loud root 5xx; empty/null trace (existing pass-cases).
- (if 1C) suppressed: detached/async 5xx span.
- 4-arg overload unchanged → existing `TraceShapeOracleIntentTogglesTest` + suite pass.

---

## TASK 2 — train-ticket hidden-downstream mutation study (tool + 1 SUT mutant)

### 2A. The SUT mutant (one upstream "swallow")
train-ticket fails *loudly* (downstream 5xx propagates to the root as 5xx). To create a HIDDEN case,
pick a flow where a downstream returns 5xx on a negative input and inject a **swallow in the upstream
service**: catch the downstream failure and return `Response(1,"…success", normalData)` → clean 200.
- Candidate flow: `ts-admin-route-service` → `ts-route-service` (the POST in
  `AdminRouteServiceImpl.createAndModifyRoute`, lines ~111–120). On a marker input where route-service
  5xx's, wrap the call: `try { … } catch (… ) { return new Response(1,"Save success", route); }`.
  Confirm at execution that route-service actually 5xx's on that input (else mutate the downstream too).
- One file if the downstream already 5xx's; two files if we must force the downstream 5xx. Revertible;
  documented in the evidence diff. Push → ~20-min rebuild.

### 2B. Ground-truth tracking (the A/B mechanism)
The mutant returns a **clean body (no faultName)** → the SUT-side fault-name detector does NOT fire →
the fault stays **UNDETECTED** by the status/fault-name oracle. Add the new fault (e.g.
`HIDDEN_DOWNSTREAM_ROUTE_FAULT`) to `injected-faults.json` so it is a registered injected fault that
shows as UNDETECTED, while the **trace oracle** records a `HIDDEN_DOWNSTREAM_FAILURE` anomaly on that
endpoint. That contrast is the evidence.

### 2C. Run + evidence
Run MIST against the rebuilt SUT with `mst.oracle.shape.invariants.hidden_downstream_failure.enabled=true`
and `mist.report.oracle.anomalies.enabled=true`. Collect: the fault-detection report (fault =
UNDETECTED) + ORACLE ANOMALIES (HIDDEN_DOWNSTREAM_FAILURE fired) + a control fault still detected.
Save to `docs/main-contribution/evidence/` (methodology + A/B table + report + mutant diff), mirroring
the silent-accept evidence format.

### 2D. Caveats (honest)
First detection-rate datapoint on **one** SUT — not the full mutation study. Multi-SUT + non-trivial
baseline + confirmed bugs remain the deferred evaluation (the A-bar gating items).

---

## Verification criteria (definition of done)
- Task 1: mist-core builds; full suite green; new tests pin fire/suppress per 1D; 4-arg overload
  behavior byte-identical for existing callers.
- Task 2: report shows the hidden-downstream fault UNDETECTED by status/fault-name AND a
  HIDDEN_DOWNSTREAM_FAILURE anomaly fired on that endpoint; a control fault still DETECTED (no
  regression); evidence committed.
- Commits authored as miaoti, no AI attribution; tool changes pushed to `inject-detection`; SUT
  mutant on `train-ticket-injection` `injection`.

## Open questions for the reviewer
1. Task 1 gate: Option A (thread `responseLevelClean` into the detector) vs B (report-level) vs C
   (structural only)? Recommendation = A.
2. Is the soft-error boolean reachable at the oracle call site, or must it be threaded through
   `attachJaegerTrace`? (Confirm topology before coding.)
3. Precision guard 1C (synchronous-critical-path requirement): in or out for v1?
4. Task 2 mutant: is one upstream swallow enough (does route-service already 5xx on the chosen
   input), or must we also mutate the downstream to force the 5xx?
5. Should the hidden-downstream fault even be added to `injected-faults.json` (it is never
   body-reported), or tracked purely via the ORACLE ANOMALIES section?

---

## Disposition (post reviewer-agent critique, grep-verified) — 2026-05-29
All comments were grep-verified against the actual code before disposition.

| # | Sev | Disposition | Evidence / action |
|---|---|---|---|
| 1 | blocker | ACCEPT | neg-test clean-2xx → `negativeTestPassed=false` (Writer:2102) → `throw AssertionError` (:2105) → `catch(Throwable t)` (:2289) → FAILURE-path `attachJaegerTrace(...,true,...)` (:2432); SUCCESS-path (:2263) never runs → Option A call site dead for the target case. |
| 2 | blocker | ACCEPT | `attachJaegerTrace` is `private static` (:287); `llmDetectedRelatedError` declared in `try` (:2019), OUT OF SCOPE at catch-path call (:2432). → drop Option A (no plumbing). |
| 3 | major | ACCEPT | gate would depend on `llmDetectedRelatedError = validationResult.isFailed()` (:2065) = LLM verdict → contradicts "label-free". Keep firing rule purely structural; soft-error = separate report-level concern, never a firing gate. |
| 4 | major | ACCEPT | swallow wraps `restTemplate.exchange(...)` (AdminRouteServiceImpl ~:111-116), catch `HttpServerErrorException`; downstream 500 span survives (route-service emits own span; 500 via unhandled NumberFormatException). State as checked precondition. |
| 5 | major | ACCEPT | existing mutant (:66-70) returns BEFORE the downstream call → no descendant span (wrong class); comment references the REMOVED SilentAcceptanceInvariant. → new mutant + revert the stale one. |
| 6 | major | ACCEPT | `detectionRate = detectedCount/injectedFaults.size()` (Tracker:347-350) → registry add skews denominator. Do NOT add to injected-faults.json; ground truth = mutant diff + methodology. (resolves Q5) |
| 7 | major | ACCEPT | `roots()` = every parentless span (TraceModel:47-55) → co-root 500 in partial view = false negative. Fix "root" = the rootApiKey entry span. + critical-path guard (1C) IN for v1 (sync=ERROR, detached/async=WARN). 4xx exclusion kept. |
| 8 | major | ACCEPT | Re-scope Task 1: structural label-free firing rule + critical-path guard + "structurally invisible to ALL response-level oracles" framing — NOT a soft-error gate. |
| 9 | minor | PARTIAL | Detector is intent-AGNOSTIC structural (its own Javadoc). Drop "intent-conditioned" for THIS detector; system intent-conditioning lives in negative-gen + TargetAttribution. (Reject the 3-way-intent variant for v1.) |
| 10 | minor | ACCEPT | Add tests: multi-root false-negative; async/detached → WARN; overload delegation byte-identical. |
| 11 | minor | ACCEPT (moot) | Not touching injected-faults.json (see #6) → malformed `api` field not replicated. |
| 12 | minor | ACCEPT | Pair anomaly with mutant diff as ground truth; note the "NOT confirmed bugs" disclaimer doesn't apply (we hold the mutant). |

## REVISED design (supersedes the Option-A emphasis above)

### Task 1 (tool only) — structural, label-free, precision-guarded
1. **Root correctness (C7a):** the client-facing entry = the span matching `rootApiKey`, not "any parentless span"; base the 2xx check on that entry so a co-root downstream 500 in a partial trace view no longer suppresses firing.
2. **Critical-path confidence guard (C7b/1C) — the genuine precision novelty:** a descendant 5xx that is SYNCHRONOUS on the entry's call path (entry causally waited on it) → ERROR (swallowed bug); a detached/async/side 5xx → WARN (possibly tolerated). Label-free, from span parent-chain + timing.
3. **Stay structural + label-free (C3/C8/C9):** NO LLM/soft-error gate in the firing rule. Reframe Javadoc + message: "system returned a valid 2xx; the only failure evidence is a descendant span — invisible to every response-level oracle, incl. MIST's soft-error check." Honest: this detector is intent-AGNOSTIC structural.
4. **Soft-error non-overlap (C3):** structural (root body vs descendant span); dedupe at the REPORT level if needed — not a firing gate. No writer plumbing.
5. **Tests (C10):** ERROR on sync descendant-5xx under clean entry-2xx; WARN on detached/async; suppress on no-5xx / loud-entry-5xx / 4xx / empty; multi-root co-root-500 still fires; 4-arg overload byte-identical.

### Task 2 (tool + 1 SUT mutant)
1. **New mutant:** wrap the `restTemplate.exchange(...)` to ts-route-service in `AdminRouteServiceImpl.createAndModifyRoute` with `catch (HttpServerErrorException e) { return new Response(1,"…success", route); }` on a marker input that makes route-service 5xx (NumberFormatException path). Clean 200 root + surviving downstream 500 span.
2. **Replace the stale silent-accept mutant:** revert `76d15163` (returns before the downstream call AND references the removed SilentAcceptanceInvariant) and add the hidden-downstream mutant — one SUT push / one ~20-min rebuild.
3. **No registry change (C6):** ground truth = mutant diff + methodology doc.
4. **Evidence (C12):** report shows status/fault-name silent vs ORACLE ANOMALIES HIDDEN_DOWNSTREAM_FAILURE (ERROR, sync); pair with the mutant diff as ground truth; note the unconfirmed-anomaly disclaimer doesn't apply.

### Resolved open questions
Q1 → structural rule (≈C) + report-level dedup, NOT an A/B firing gate. Q2 → boolean unreachable/out-of-scope, no plumbing. Q3 → 1C IN for v1 (ERROR/WARN). Q4 → one upstream swallow suffices, catch `HttpServerErrorException`, after the exchange. Q5 → do NOT add to injected-faults.json.
