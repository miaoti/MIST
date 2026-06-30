# 01 — A-conference feasibility, established from actual MIST code

> Audit date 2026-06-30. Method: direct read of `main` (mist-core / mist-cli), per-span
> inspection of the four SUTs' committed traces, verification of the two prior probes
> against current line numbers. Every code claim carries a `file:line`. Citation style:
> `path/Class.java:line` (repo-relative). Verdict scale: EASY / MODERATE / HARD / BLOCKED-by-open-problem.
> Skeptical posture: where I could not re-run the SUT, I say so.

---

## 1. Executive verdict

MIST is, in code, a **black-box observer**: it controls only the *request inputs* fired at
external entry endpoints (the 9-category Sniper) and *observes* the SUT exclusively through the
captured Jaeger trace plus the live entry-response the writer staples into the trace model
(`MultiServiceRESTAssuredWriter.java:706-707`). It has **no hook to perturb the SUT internally** —
no Istio/Chaos/proxy fault injection anywhere in the tree (verified by exhaustive grep). The
trace-shape oracle is real and cleanly factored (a `ShapeInvariant<T>` learn/evaluate interface
with a file-backed store), but its "learning" is fixed-threshold frequency + set-membership with
**no statistical false-positive control** and a **bootstrap corpus of one trace**
(`seed-trace-labels.json:6`). Param-level attribution is exactly as the prior probe found —
one token-overlap line (`TraceAttribution.java:59`) that never reads the very span fields
(`logs[].exception.message`) carrying the injected bad value. The single hardest constraint is the
**instrumentation signal floor**: application-level evidence (exception text, stack traces, method
names) exists *only* on TrainTicket; the three Istio/Envoy SUTs carry status + topology + Envoy
flags and nothing else. Any A-main contribution must either (a) lean on the one property that
survives that floor — `HiddenDownstreamFailure` over status+topology — or (b) deliberately move
MIST from observer to opt-in grey-box controller, which is buildable but costs the "no SUT
instrumentation" identity.

Headline feasibility: **A = observe-only confirmed; downstream-fault injection BLOCKED today / MODERATE to build.
B = MODERATE to implement, BLOCKED-by-data for the statistical claim. C = (b′) confirmed — recoverable only
where the failure is already loud; soft-swallow param attribution BLOCKED-by-open-problem. D = a hard signal floor,
characterized below.**

---

## 2. Per-question findings

### A) Fault / input-injection plumbing — *where, how, and does MIST control or observe?*

**What is injected today (Sniper), traced end-to-end.**
- The 9 fault categories are all **request-input** mutations: `TYPE_MISMATCH, OVERFLOW, EMPTY_INPUT,
  NULL_INPUT, SPECIAL_CHARACTERS, BOUNDARY_VIOLATION, ENUM_VIOLATION, REGEX_MISMATCH, SEMANTIC_MISMATCH`
  (`ZeroShotLLMGenerator.java:242-262`; in default `smart` mode only REGEX+SEMANTIC use the LLM,
  the other 7 are hardcoded, `:236-256`). `FaultType` is an identity-keyed value object
  (`FaultType.java:19-48`) with a `FaultSource.{DEFAULT,MINED}` axis (`FaultType.java:21`).
- The injection target is a request **slot**, never a service internal. The fault queue is
  `params × fault-types` (`MistGenerator.java:144-188`, `FaultTarget` at `:105`); each negative
  variant carries one `(type, paramLocation, value)` routed by the writer into header/cookie/path/
  query/body (`MistGenerator.java:757-765`).
- **Sniper = exactly one fault per variant, on exactly one root**: only the targeted root receives
  faults, all other roots get strictly positive inputs (`MistGenerator.java:774-791`,
  `faultThisRoot ? targetFaultyParams : Collections.emptyList()` at `:788-789`).
- `FaultMiner` (LLM-assisted, default **off** via `mist.fault.mining.enabled`,
  `FaultMiner.java:35-40`) only proposes *new input-fault categories* — still input-side.

**Does MIST control or only observe?** *Only observes the SUT; controls only request inputs.*
- Exhaustive grep for mesh/chaos/fault-injection control over `mist-core/src/main/java` returns
  **zero** injection hooks: the `istio` hits are span-name *parsing* (`MistGenerator.java:1014,2255`;
  `SharedPoolSupport.java:150,411,493`), the `chaos` hits are pluralization word-lists
  (`SemanticDependencyRegistry.java:1450`, `TraceWorkflowExtractor.java:811`), and the
  `fault injection` hits are TODO comments in `WorkflowStep.java:11` / `WorkflowScenario.java:17`.
  No Istio `VirtualService`, no Chaos Mesh, no Toxiproxy/Pumba, no `tc qdisc`, no abort/delay
  injector exists.
- The only SUT-state MIST writes is the request; the only thing it reads back is the trace plus
  the live entry response it injects into the in-memory model (`http.response.body` +
  `mist.client.status`, `MultiServiceRESTAssuredWriter.java:706-707`), then `oracle.evaluate(...)`
  per step (`:714`).

**(a) Generate inputs that drive an upstream service into a swallow/degradation branch.**
Verdict: **HARD (open-problem residue).** MIST can already *detect* a swallow if one happens
(`HiddenDownstreamFailureInvariant`), and the Sniper grammar will occasionally *trigger* one by
luck, but there is **no mechanism to steer an input toward a specific degradation branch**. Black-box
gives no gradient from "input slot" to "which downstream call fails and whether the caller catches
it"; that mapping is exactly the SUT internal MIST refuses to see. The residue is a genuine search
problem (coverage-guided input → internal-branch steering) with no trace signal to guide it on the
Envoy SUTs.

**(b) Systematically inject downstream faults (latency/abort/error) while replaying realistic inputs.**
Verdict: **BLOCKED today; MODERATE to build, with an identity cost.** No hook exists (above). Building
one is standard infra (Istio fault `VirtualService` or Chaos Mesh, applied per-test, paired with the
existing two-phase verified-input pool so the replayed inputs are realistic — `MistRunner.java:502-557`).
The engineering is moderate; the cost is conceptual: it converts MIST from "no SUT instrumentation"
black-box into an opt-in **grey-box controller**, so it must be framed as a separate mode, not the
core claim. Per project memory, MIST already deploys TrainTicket via `make deploy` (k8s/minikube),
so the deploy-control prerequisite is already met for at least one SUT.

---

### B) Oracle / invariant learning — *how learned, where is the baseline, how hard to add FP-controlled mining?*

**How invariants are learned and evaluated.** Clean two-sided `ShapeInvariant<T>` interface
(`ShapeInvariant.java:11-21`, a `learn` side producing a `Data` record and an `evaluate` side).
- `SpanTreeShapeInvariant` — learns `(parent.service → child.service)` edges seen in ≥ a **fixed
  0.8 fraction** of good traces as *required*, any edge as *observed/allowed*, and a per-depth
  max fan-out; flags unexpected/missing edges and fan-out > **3×** learned max
  (`SpanTreeShapeInvariant.java:128-163`; constants `:32-33`).
- `StatusPropagationInvariant` — learns the **set** of `http.status_code` / `otel.status_code`
  values per tree depth; flags any code outside the learned set
  (`StatusPropagationInvariant.java:90-123`, eval `:69-88`). No frequency threshold at all: a status
  seen *once* becomes permanently allowed.
- `ResponseEnvelopeInvariant` — learns the **set** of `status`-field values seen on 2xx bodies;
  an unknown value triggers **one** LLM classification, cached
  (`ResponseEnvelopeInvariant.java:187-202` learn, `:121-141` runtime classify).

**Where the "learned baseline" lives.** `ShapeInvariantStore`, a file-backed JSON map keyed by
`KIND::rootApiKey`, default `.mist/trace-shape-invariants.json`
(`ShapeInvariantStore.java:32,52-91`). Bootstrapped **once at cold start** from the seed corpus
(`MistRunner.java:917-938`) via `TraceShapeLearner.learn(seedCorpusDir, labels, store)`
(`TraceShapeLearner.java:36-63`); at runtime the oracle re-loads each `Data` from the store
(`TraceShapeOracle.java:95-108`). `HiddenDownstreamFailure` and `TargetAttribution` are **not**
learned — constructed fresh, `T = Void` (`TraceShapeOracle.java:111,117`;
`HiddenDownstreamFailureInvariant.java:23-24,49`).

**Two load-bearing weaknesses, verified.**
1. **No statistical FP control of any kind.** Grep across `mist-core/.../oracle` for
   confidence/Hoeffding/Clopper-Pearson/binomial/p-value/significance/Bonferroni/Wilson returns
   only doc-comment prose (`HiddenDownstreamFailureInvariant.java:40`,
   `AttributionVerdict.java:8,24`) — **no method**. Thresholds are hand-set (0.8, 3×) or pure
   set-membership.
2. **The corpus is essentially n = 1.** The shipped label registry marks a single file known-good
   (`seed-trace-labels.json:6`), and absent files default to known-good
   (`TraceShapeLearner.java:100`). A "learned" allowed-edge/allowed-status set derived from one
   trace is statistically vacuous — any unseen-but-legal path is a false positive.

**Feasibility of automated invariant mining with FP control.** Verdict: **MODERATE to implement,
BLOCKED-by-data for the statistical claim.** The `ShapeInvariant.learn` sites are the natural seam
to drop in a Daikon/DIDUCE-style miner with Clopper-Pearson/Hoeffding bounds and a multiple-comparison
correction (the `Data` records and persistence already exist). The code change is moderate and local.
But statistical FP control is **meaningless on a one-trace corpus**; the binding constraint is *data*,
not code — a real known-good corpus (tens–hundreds of traces per root API, captured under load) must
exist first. Until then, any "statistically sound learned invariant" claim is unsupported.

---

### C) Attribution information-limit — *verify the prior claim; is param-level truly impossible?*

**Prior probe re-verified against current lines.** The whole param-level decision is one line:
`MethodToParamMapper.isResponsibleFor(leaf.operation, targetParam)` at `TraceAttribution.java:59`,
fed **only** `leaf.operation`. It never reads `Span.tags` or span `logs`. The funnel:
`LeafErrorSpanFinder.findLeafError` (deepest error span; error = `otel=ERROR` or `http≥400`,
`LeafErrorSpanFinder.java:88-92`) → service match (`TraceAttribution.java:48-49`, lenient substring
`:70-78`) → param token-overlap (`:59`). `MethodToParamMapper` implements **only tier-2 naming
heuristic**; tier-1 (OpenAPI hint) and tier-3 (probe cache) are documented-but-absent
(`MethodToParamMapper.java:14-32`), and the docstring itself names the degenerate case
(`RouteController.createAndModifyRoute`). `TargetAttributionInvariant` wraps it at **INFO** severity
(advisory): `TARGET`/`NO_ATTRIBUTION` → pass, `UPSTREAM`/`WRONG_PARAM` → fail
(`TargetAttributionInvariant.java:60-86`).

**The decisive trace evidence (per-span re-inspection of `admin_add_route_failed.json`).** The
injected bad value is echoed verbatim in the trace, and the stack trace pins the exact code line:
- `admin_add_route_failed.json:70-72` — `exception.message = "For input string: \" 11\""`
- `admin_add_route_failed.json:75-77` — `NumberFormatException ... RouteServiceImpl.java:45`
- `admin_add_route_failed.json:80-82` — `exception.type = java.lang.NumberFormatException`

**But MIST cannot see it: `TraceModel.toSpan` parses `tags` and `attributes` but NOT the `logs[]`
array** (`TraceModel.java:147-213`; the `Span` record `:215-242` has no logs field). The exception
message is dropped at parse time and never reaches any invariant. So the information is *in the
trace on disk* yet *outside MIST's model*.

**Is param-level recoverable?**
- **(i) Exception-message value-matching** — Verdict: **MODERATE to build, but TrainTicket-only and
  not novel.** MIST knows the `(param, value)` it injected (`MistGenerator.java:763-764`); matching
  that value against `exception.message` would lift attribution to param level. Requires only:
  parse `logs[]` in `TraceModel.toSpan` (EASY), then a value-match tier in `MethodToParamMapper`.
  Two hard limits: (1) the exception path is a **loud hard failure already visible in the response**
  — recovering it does not serve the trace-only soft-swallow class the paper headlines; (2)
  **no Istio/Envoy SUT carries exception text at all** (verified: zero `exception.message` outside
  TrainTicket), so the technique covers exactly one of four SUTs.
- **(ii) Active differential probing** (vary one input, diff the trace) — Verdict: **HARD.** Not
  implemented (the tier-3 probe cache is the absent one). Costs N extra SUT executions per param and
  re-pollutes the DB (same hazard as two-phase Phase A, `MIST_FLOW.md` "Phase A re-pollutes the SUT
  DB"). Worse, the diff is only as rich as the trace: on the Envoy SUTs the per-param diff collapses
  to status+topology, which cannot isolate a parameter.

**Frank verdict: prior (b′) confirmed and current-code-accurate.** Param-level attribution is **not**
information-theoretically impossible for loud failures on a richly-instrumented SUT, but it is
recoverable *only* where the failure is already loud (hence not the novel trace-only class) and is
**genuinely impossible for the soft-swallow class under current instrumentation**. Attribution
**cannot be the load-bearing methodological novelty.** The honest ceiling is **service-level**
attribution (TARGET-at-service vs UPSTREAM), which the code already supports
(`TraceAttribution.java:55-57`).

---

### D) Available signals across the four SUTs — *what bounds any new oracle*

Per-span tallies of the committed traces. Two instrumentation regimes:

**TrainTicket (Java OTel SDK auto-instrumentation) — richest.** Reliably present:
span topology (`references[].CHILD_OF`), `http.status_code`, `http.method`, `http.url`/`http.target`/
`http.route`, `otel.status_code`, application-method `operationName`
(`RouteController.createAndModifyRoute`, `StationRepository.findByNames`), and uniquely
**`exception.message` / `exception.stacktrace` / `exception.type`** in `logs[]`
(`admin_add_route_failed.json:61-85`). Body **absent** (only `http.request_content_length`).

**Bookinfo / Boutique / Sock Shop (Istio/Envoy sidecar) — network-level only.** Reliably present:
topology, `http.status_code`, `http.method`, `http.url`, `otel.status_code` (tied to 5xx),
`grpc.status_code` (Boutique), `istio.canonical_service`, `request_size`/`response_size`, and
**Envoy `response_flags`** (e.g. `"UH"` = no healthy upstream in
`bookinfo .../masked_reviews_ratings_outage.json`). **No `exception.*`, no method-level operation
names, no body** (verified: zero `exception.message` across all three).

**Absent everywhere in raw traces: request and response bodies** — verified, no `http.response.body`
in any committed trace under `evaluation/suts/*/traces` or `docs/main-contribution/evidence`. The
body exists only at runtime, only for the **entry** response, injected by the writer
(`MultiServiceRESTAssuredWriter.java:706`); downstream bodies are never available.

**What the model actually keeps** (the real bound): `TraceModel.Span` =
`{spanId, parentSpanId, service, operation, httpStatus, otelStatus, durationMicros, tags{}}`
(`TraceModel.java:215-242`). `tags{}` captures every flat tag/attribute (`:170-210`) — so
`response_flags`, `http.route`, etc. are *available but unused by any invariant* — but `logs[]`
is **not** parsed, so `exception.*` is structurally invisible to every oracle.

**Bound for any new technique:** a cross-SUT oracle may rely **only** on status codes + span
topology + `otel.status_code`/Envoy `response_flags`. Anything depending on exception text or
method names is **TrainTicket-only**; anything depending on a body is **entry-only and runtime-only**.
This is precisely why `HiddenDownstreamFailure` (status + topology, label-free, LLM-free,
`HiddenDownstreamFailureInvariant.java:174-181`) generalizes across all four and the attribution /
soft-error paths do not.

---

## 3. Build list (concrete sketches, effort, open-problem residue)

| # | Upgrade | Code-change sketch | Effort | Open-problem residue |
|---|---|---|---|---|
| 1 | **Parse `logs[]` + value-match attribution tier** | Add a `logs`/`exception.message` field in `TraceModel.toSpan` (`TraceModel.java:147-213`); add a tier in `MethodToParamMapper` that matches MIST's injected value against the exception text | **S** (1-2 d) | TrainTicket-only (no Envoy exception text); recovers only loud failures already visible in the response — not novel |
| 2 | **Promote Envoy `response_flags` + a downstream-connectivity invariant** | New `ShapeInvariant` reading the already-captured `tags["response_flags"]` (UH/UF/DC) to flag masked upstream-health failures | **S** | Largely overlaps `HiddenDownstreamFailure`; Envoy-only tag |
| 3 | **FP-controlled invariant mining** | Replace fixed 0.8 / set-membership in the three `learn` sites with Clopper-Pearson/Hoeffding bounds + multiple-comparison correction; persist bound metadata in `Data` | **M** | **BLOCKED-by-data**: meaningless on the n=1 seed corpus (`seed-trace-labels.json:6`); needs a real multi-trace good corpus first |
| 4 | **Opt-in downstream fault-injection mode** | Orchestrate Istio fault `VirtualService` / Chaos Mesh per test, replay the two-phase verified input pool (`MistRunner.java:502-557`), assert with `HiddenDownstreamFailure` | **L** | Converts MIST to grey-box controller — breaks "no SUT instrumentation"; must be a separate mode. *This is the most defensible research upgrade*: "controlled downstream fault + does the SUT swallow it?" is general and trace-anchored |
| 5 | **Differential probing for param attribution** | Implement the absent tier-3 probe cache: vary one param, diff captured traces | **M-L** | HARD: N extra executions, DB re-pollution; diff collapses to status+topology on Envoy SUTs — cannot isolate a param there |

Most promising A-main spine: **#4 (controlled downstream faults) anchored by the existing
`HiddenDownstreamFailure` oracle**, with **#3** as the rigor layer *iff* a corpus is captured.
#1/#5 (attribution) are engineering, not novelty (Question C).

---

## 4. Hard constraints any A-main plan must respect

1. **Black-box identity vs. control.** Today MIST *observes* and controls only request inputs
   (Section A). Any downstream-fault or differential-probe technique requires SUT control and must
   be framed as an opt-in grey-box mode, or the "no SUT instrumentation" claim is false.
2. **Signal floor (Section D).** Cross-SUT techniques are limited to status + topology +
   `otel.status_code`/`response_flags`. Exception text and method names are TrainTicket-only; bodies
   are entry-only and runtime-only; `logs[]` is currently unparsed.
3. **Corpus floor.** Learned-invariant / statistical-FP claims need a real known-good corpus; the
   shipped seed is one trace (`seed-trace-labels.json:6`, `TraceShapeLearner.java:100`). No
   statistical control is defensible on n=1.
4. **No wild bug corpus** (prior probe B, verified by absence — `probe-wildbugs.md`): individual
   reproducible trace-only swallowed-downstream / silent-acceptance bugs in OSS ≈ 0. Evaluation must
   use injected/replicated faults (`injected-faults.json`, train-ticket-fault-replicate) + cited
   prevalence (Uber SIGMETRICS'25, Yuan OSDI'14); do **not** claim AGORA+-style wild bugs.
5. **Attribution ceiling.** Service-level attribution is honest and already coded
   (`TraceAttribution.java:55-57`); param-level is instrumentation-bound and cannot be load-bearing
   (Section C).

---

*Confidence: high (≈0.9) on every code claim (direct read + per-span trace inspection). The one
empirical claim I did not re-run is the exact `TARGET_REJECTION=0` count on a live TrainTicket run;
I verified the mechanism that makes it fragile (`TraceAttribution.java:59` + unparsed `logs[]`), not
a fresh execution.*
