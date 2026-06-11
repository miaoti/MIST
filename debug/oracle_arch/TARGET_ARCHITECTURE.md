# MIST Oracle — Target Architecture (intent-aware, outcome-aware trace oracle)

Design grounded in the [current architecture](CURRENT_ARCHITECTURE.md). Goal: turn
MIST's oracle from "intent-agnostic learned-shape anomaly detector + one INFO-level
attribution classifier" into a **first-class intent-aware, outcome-aware oracle** that
judges, per negative test, whether the SUT *handled the bad input correctly* — and
thereby catches bug classes that response-level oracles (e.g. AGORA+, TOSEM'25) and
crash-only oracles **structurally cannot** see.

## Positioning (the one-sentence contribution) — *narrowed per review (DISPOSITIONS.md B1/F1)*
> A **label-free, intent-conditioned** test oracle that lifts invariant-based REST oracles
> from **single-API request/response** (AGORA+) to **cross-service distributed traces**,
> using the negative test's injected-fault **intent** as a per-test spec to **disambiguate**
> how the SUT handled bad input: correct-rejection / *silent-accept-that-mutated-downstream*
> / *benign 2xx* / *hidden downstream failure*.
>
> NOT "first to detect silent acceptance" — MIST's emitted assertion already fails on a 2xx
> (writer `:2102-2105`), coarsely, status-only, with an unreliable LLM rescue. The wedge is
> the **trace-backed, label-free disambiguation** that a status assertion and a single-API
> response oracle cannot do. Position vs Tracetest (needs human-written span assertions →
> we are label-free), AGORA+ (single-API responses; catches ~48% of seeded output bugs but
> no cross-service reach), SynthoDiag/RCA (localization in production, not a test oracle).

The key realization from the as-is map: **MIST already produces all three signals
(intent, expected, observed) but disconnects them before the oracle.** The redesign is
mostly *re-connection*, not new machinery — which is why it's feasible on the existing code.

---

## The five architectural moves

### Move 1 — Promote intent to a first-class, exported artifact (`NegativeTestIntent`)
Stop discarding `FaultTarget`. Carry it end-to-end.

- **Keep** `MistGenerator.FaultTarget` (`MistGenerator.java:107-131`) but **export** it: attach a structured `NegativeTestIntent` to `MultiServiceTestCase` instead of de-normalizing into 5 scalar fields (`:717-729`).
- **Fields**: `targetRootApiKey, targetService, targetStepId, targetParam, paramLocation, faultType, injectedValue`.
- **Writer** emits it as one structured object into the test (replacing the fragile string round-trip reconstruction at `MultiServiceRESTAssuredWriter.java:1336-1369`).
- **Kills G1**: the oracle now receives full intent (incl. value + fault type), and the brittle `"Root N"→"RN"` / `"name=value"` parsing disappears.

```java
// NEW — mist-core/.../oracle/intent/NegativeTestIntent.java
record NegativeTestIntent(String targetRootApiKey, String targetService, String targetStepId,
                          String targetParam, String paramLocation,
                          String faultType, String injectedValue) {}
```

### Move 2 — Make the expectation explicit data (`ExpectedOutcome`)
A negative test's intent *defines* what correct handling looks like. Represent it (today it's only an implicit runtime predicate, G2).

```java
// NEW — derived from intent at generation time
record ExpectedOutcome(StatusClass expectedClass,   // REJECT_4XX for negatives
                       String expectRejectionService, // = targetService
                       boolean expectNoDownstreamMutation) {}
```
- For a faulty variant: `expectedClass = REJECT_4XX`, `expectRejectionService = targetService`, `expectNoDownstreamMutation = true`.
- This is the spec the oracle checks against — the thing that makes it *intent-aware* in the AGORA-beating sense (AGORA+ learns "what always holds"; we *assert what should happen this time*).

### Move 3 — Outcome-aware oracle interface (thread the observed response in)
Give the oracle the observed `(httpStatus, responseBody)` that already sits in scope.

- **Refactor** `attachJaegerTrace(...)` (`MultiServiceRESTAssuredWriter.java:287`) and `TraceShapeOracle.evaluate(...)` (`TraceShapeOracle.java:72`) to also accept an `ObservedOutcome{httpStatus, responseBody, stepFailed}`.
- **Pass** `actualStatusCode<N>` / `responseBody` at the call sites (`:2263` success, `:2432` catch) — they're already computed at `:1986-2019`.
- **Kills G3**: the oracle now has the full triple `{intent+expected, observed-response, trace}`. The authoritative status is the REST-assured response; the **trace is used for structure** (downstream errors, attribution), not for the top-line status — which also de-risks the lag-prone Jaeger-status scrape (`TraceShapeAdapter.java:150`) and the marker-trace correlation.

```
oracle.evaluate(trace, rootApiKey, intent, expected, observed)   // was: (trace, rootApiKey, targetService, targetParam)
```

### Move 4 — Add the intent-aware bug-class detectors (the actual novelty)
Two new invariants consuming `{expected, observed, trace}`. **These are the contribution** — they detect what response-level + crash-only oracles miss.

| New invariant | KIND | Fires when | Severity | Why traces+intent are required |
|---|---|---|---|---|
| **SilentAcceptanceInvariant** | `SILENT_ACCEPTANCE` | `expected=REJECT_4XX` **but** observed 2xx **AND** the trace shows the target op completed with a downstream **mutation/write** span (not a no-op) | **WARN initially → ERROR once semantic baits (Move 5) land** (B2) | Needs *intent* + *observed 2xx* + *trace* (did the bad value actually flow downstream and mutate?). The trace is what separates a **real** silent-accept from a **benign** 2xx (input ignored / optional / coerced). A status assertion only sees "2xx"; it cannot tell these apart. |
| **HiddenDownstreamFailureInvariant** | `HIDDEN_DOWNSTREAM_FAILURE` | observed root/gateway 2xx **but** trace has a descendant span with **http≥500 or otel=ERROR** — using a **dedicated ≥500 predicate, NOT `LeafErrorSpanFinder.isErrorTagged` (which is ≥400 and would fire on benign 4xx control-flow)** (B3) | ERROR | Needs *observed root status* + *trace* (deep span). `LeafErrorSpanFinder` can't start (root not error-tagged, `:48-49`); `StatusPropagation` only catches it incidentally. A response-level oracle is blind. |

- **Elevate `TargetAttributionInvariant`** from INFO to a real verdict *when `expected=REJECT_4XX`*: `UPSTREAM_REJECTION` / `WRONG_PARAM_REJECTION` = "SUT rejected, but at the wrong place" → WARN (localization signal). Keep `TARGET_REJECTION`/`NO_ATTRIBUTION` = pass.
- **Hard precondition (D2):** both new invariants MUST early-return PASS when `expected != REJECT_4XX` (positive tests) **inside the invariant** (mirror `TargetAttributionInvariant`'s `targetService==null` guard, `:63-64,88-89`) — not merely at the call site — or a stray ERROR outcome would fail healthy positives via the AND-of-ERROR verdict (`TraceShapeVerdict.java:64-73`).
- **Keep the 4 learned-shape invariants** as **intent-agnostic priors** (unchanged): they still flag unexpected structural/timing/status deviations. The intent-aware trio rides *on top*.

> **Move 4 note (honesty):** param-level `TARGET_REJECTION` is infeasible on coarse SUTs like train-ticket (controller-method spans; see cert finding). Service-level + the two new bug classes do **not** depend on param granularity, so they stand regardless. Param-level stays future-work for SUTs with field-level validation.

### Move 5 — Generate negatives that *exercise* the new detectors
Small generation-side change so the oracle has signal to judge:
- Emit, per faulty variant, the `ExpectedOutcome` (Move 2) — trivial, derived from `getFaulty()` + intent.
- (Optional, raises the ceiling) add a fault sub-type that targets **silent-acceptance bait**: values that are *semantically* invalid but *syntactically* valid (e.g. a well-formed but non-existent FK, a negative price) — these are the inputs most likely to be silently accepted. Fault taxonomy is data (`fault-types.default.yaml`), so this is additive.

---

## Target component diagram

```
GENERATION                         EMITTED TEST (per step)                         ORACLE (intent+outcome aware)
──────────                         ───────────────────────                         ─────────────────────────────
FaultTarget ──promote──▶           observed = (actualStatusCode<N>,                 evaluate(trace, rootApiKey,
 NegativeTestIntent                            responseBody, stepFailed)                     intent, expected, observed)
 + ExpectedOutcome    ──emit──▶     intent/expected (structured, not literals)        │
 (on MultiServiceTestCase)          trace = fetch+adapt (marker id)                    ├ INTENT-AGNOSTIC priors (kept):
                                    oracle.evaluate(trace, rootApiKey,                 │   SpanTree, StatusProp, RespEnv, Timing
                                                    intent, expected, observed) ──────▶├ INTENT-AWARE (new/elevated):
                                                                                       │   SilentAcceptance      [ERROR]
                                    recordVerdict(verdict, intent, observed, ids) ◀────┤   HiddenDownstreamFailure[ERROR]
                                                                                       │   TargetAttribution     [WARN when reject expected]
                                                                                       └ verdict.passed = AND(ERROR outcomes)
```

---

## Delta table (existing → action, anchored to code)

| Existing element | Action | Where |
|---|---|---|
| `MistGenerator.FaultTarget` | **keep + export** as `NegativeTestIntent` (don't discard at `:728`) | `MistGenerator.java:107-131,717-729` |
| `MultiServiceTestCase` scalar fault fields | **replace** with one `NegativeTestIntent` + `ExpectedOutcome` | `MultiServiceTestCase.java:64-133` |
| writer target reconstruction | **delete** (no more string round-trips); emit structured intent | `MultiServiceRESTAssuredWriter.java:1336-1369` |
| `attachJaegerTrace(...)` signature | **add** `ObservedOutcome` (status+body, already in scope) | writer `:287`, call sites `:2263,:2432` |
| `TraceShapeOracle.evaluate(...)` | **add** `(intent, expected, observed)`; keep 4 priors | `TraceShapeOracle.java:72-94` |
| `SilentAcceptanceInvariant` | **NEW** (ERROR) | new file |
| `HiddenDownstreamFailureInvariant` | **NEW** (ERROR) | new file |
| `TargetAttributionInvariant` | **elevate** INFO→WARN when reject expected | `TargetAttributionInvariant.java:72-86` |
| `ResponseEnvelopeInvariant` LLM stub / empty `failureSet` | **either** implement, **or** subsume into SilentAcceptance + drop the dead path | `ResponseEnvelopeInvariant.java:77-84,130` |
| `recordVerdict` | **carry** intent+observed for richer anomaly rows | `FaultDetectionTracker.java:202-244` |
| 4 learned-shape invariants | **keep unchanged** (intent-agnostic priors) | invariant/*.java |

---

## Backward-compat / flags
- New detectors behind `mst.oracle.shape.invariants.silent_acceptance.enabled` / `.hidden_downstream_failure.enabled` (mirror the existing per-invariant gate pattern, `TraceShapeOracle.java:33-42`). Add both to `MstConfigValidator` KNOWN_KEYS.
- Intent/expected/observed threading is **additive** (new params; null/empty on positive tests → new invariants no-op). Legacy single-phase + the 4 learned invariants behave identically when the new flags are off.

## What it newly catches (and why competitors can't)
- **Silent acceptance**: SUT returns 2xx for invalid input → real correctness bug. AGORA+ (response invariants) might catch *some* via response-field invariants, but only if it learned the field; it has no notion of *this input was supposed to be rejected*. Crash oracles see nothing.
- **Hidden downstream failure**: gateway 2xx, downstream 500 → swallowed failure. Response-level oracles are blind (response is clean); needs the trace. This is the **existence proof** that traces are necessary.

## Open risks (must validate, architecture-level)
0. **★ DECISIVE — demonstrability on train-ticket = ZERO instances (DISPOSITIONS.md C2).**
   Empirical scan of 836 traces (+ Stage-2a recall data): every error propagates a 500 to the
   root → **0** hidden-downstream-failures, **0** observed silent-mishandles, and param-level
   `TARGET_REJECTION` was already 0 in the cert. train-ticket **fails loudly**, so the
   trace-only bug classes this oracle targets **do not occur** here — the headline cannot be
   shown on this SUT. **Requirement:** a SUT that mishandles silently, OR deliberately injected
   **SUT-side mutants** (swallow downstream errors / accept-and-mutate) as ground truth. This
   is an architecture/methodology prerequisite, not an evaluation-scale detail.
1. **Precision of the new ERROR verdicts** — a silent-acceptance/hidden-failure flag must be a real bug, not a learned-corpus gap. Needs a triage pass. (On train-ticket the empirical FP surface is 0%, but so is the TP surface — see risk 0.)
2. **Observed-vs-trace consistency** — when REST-assured status and Jaeger span status disagree (lag / wrong trace), define which wins (proposal: REST-assured authoritative for top-line; trace authoritative for structure).
3. **Silent-acceptance ground truth** — confirming "should have been rejected" still relies on the negative-test intent being correct (the input really is invalid); semantically-invalid-but-syntactically-valid baits (Move 5) are the strongest cases.
4. **Novelty boundary (F1)** — position narrowly vs Tracetest (human-spec'd trace assertions) / AGORA+ (single-API) / RCA; the wedge is *label-free + intent-conditioned + cross-service*, not "first trace oracle."
