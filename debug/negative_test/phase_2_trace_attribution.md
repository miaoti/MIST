# Phase 2 — Trace Leaf-Error-Span Attribution

## Goal

Add a SUT-agnostic oracle that decides: **did the SUT reject because of
our targeted invalid parameter, or for some unrelated upstream reason?**

The mechanism: for each negative test that produces a non-2xx response,
walk the Jaeger trace to find the *leaf error span* — the deepest span
in the call chain that recorded the actual validation failure. Match
that span's service + method against the service that owns the
targeted parameter (known from the OpenAPI spec). If they agree, the
rejection is OUR rejection. If they don't, the test fired but missed.

This is the contribution that makes MIST work on any SUT with Jaeger
tracing — no `data.injected=true, faultName=X` instrumentation needed.

## Literature anchor

Agent B's SOTA review (in-session transcript) recommended this as the
primary oracle for our exact stack:

> **Jha et al. (CLOUD 2022)** — "Localizing and Explaining Faults in
> Microservices Using Distributed Tracing." Walks each failed test's
> trace, identifies the *leaf error span* in the unbroken error chain,
> and attributes the failure to that span's service+method. Free,
> label-free, multi-service-aware.

Supporting evidence:

- **DeepTraLog (ICSE'22)** — same TrainTicket benchmark we use; uses
  distributed-tracing as test oracle, precision > 97%.
- **TraceAnomaly (ISSRE'20)** — unsupervised, 0.97 precision on 18
  production services. Stronger than necessary for our use; Jha's
  simpler algorithm fits Phase 2 scope.

## Why this is Phase 2 (not Phase 0)

- **Highest research value, lowest immediate benchmark impact.** Run 22
  already achieved 10/10 with what we have. Phase 2's value is
  generalization: any next paper benchmark (any microservice SUT with
  tracing) gets correct attribution without injected fault names.
- **Builds on Phase 0.** Phase 0 surfaces oracle violations; Phase 2
  filters them to "violations whose leaf error matches our target",
  promoting them from "anomaly" to "confirmed root-causable negative
  test."
- **Higher implementation cost.** 5-7 days vs Phase 0's 1 day.

## Design

### Trace handle

MIST already pulls Jaeger traces in test execution (`jaeger.base.url`,
trace correlation via W3C `traceparent`). Each generated test method
already knows its trace-id and span-id (commit `#26` injected those).
Phase 2 adds one post-execution step: pull the trace, parse it, run the
attribution algorithm.

### Algorithm (Jha CLOUD'22 simplified)

```
def attribute(trace_root_span, target_service, target_method):
    # 1. Find the deepest error span in the unbroken error chain.
    leaf = None
    queue = [trace_root_span]
    while queue:
        span = queue.pop()
        if span.has_error_tag():     # status.code=ERROR or http.status >= 400
            leaf = span               # keep diving; we want the leaf
            queue.extend(span.children)
        # don't descend into non-error subtrees

    if leaf is None:
        return Verdict.NO_ATTRIBUTION   # SUT returned non-2xx but no error span?

    # 2. Identify the service that recorded the error.
    err_svc = leaf.service_name
    err_op = leaf.operation_name      # e.g. "OrderServiceImpl.validateSeat"

    # 3. Map err_op back to a parameter via known patterns:
    #    - method name often mentions the param: "validateSeat" → seat*
    #    - or service known to own a domain: ts-admin-order-service → seat*, contacts*
    err_param_set = mapMethodToParams(err_svc, err_op)

    if target_service in err_svc and target_param in err_param_set:
        return Verdict.TARGET_REJECTION    # our shot landed
    elif target_service in err_svc:
        return Verdict.WRONG_PARAM_REJECTION  # right service, wrong param
    else:
        return Verdict.UPSTREAM_REJECTION  # different service rejected first
```

`mapMethodToParams` is the only fuzzy bit. Three sources, ranked by
reliability:

1. **OpenAPI extension hints.** If the spec carries
   `x-mist-param-validator-method` per param, exact match.
2. **Naming heuristic.** Method name `validateSeat` ↔ param `seatNumber`,
   `seatClass` (lowercase + token overlap). Cheap, works in TrainTicket.
3. **One-time discovery probe.** For each (service, method) ever seen as
   a leaf, send a deliberately-malformed body and observe which
   `target_param` correlates with that leaf. Records to a side cache.

Phase 2 lands (1) + (2); (3) is a follow-up if precision needs lifting.

### Integration point

A new oracle joins the existing `TraceShapeOracle` invariant family:

```
mist-core/src/main/java/io/mist/core/oracle/shape/invariant/
    SpanTreeInvariant.java
    StatusPropagationInvariant.java
    ResponseEnvelopeInvariant.java
    TimingInvariant.java
    TargetAttributionInvariant.java       ← NEW
```

It's gated under `mst.oracle.shape.invariants.target_attribution.enabled`
(default true once validated, false during the rollout). Honours the
ablation framework already in place.

The invariant reads per-test context (target service, target param —
already on `MultiServiceTestCase`), pulls the Jaeger trace via the same
client `TraceWorkflowExtractor` already uses, runs the attribution, and
emits one of three verdicts:

- `TARGET_REJECTION` → upgrade the test's anomaly into a "confirmed
  bug-detection event" — goes into Phase 0's report as a separate "high
  confidence" bucket.
- `WRONG_PARAM_REJECTION` → counts as a tool-quality bug: the Sniper
  shot landed near, not on, the target. Useful Phase 1 feedback signal.
- `UPSTREAM_REJECTION` → counts as a pool-pollution bug: a non-target
  parameter was invalid. Direct feedback into Phase 1's pool validator.

### Cost-control

One trace pull per non-2xx negative test. For Run 22 scale: ~7,000
traces × ~5 KB each = ~35 MB downloaded, ~1-2 minutes of Jaeger query
time. Trivial. Cached locally per test-run-id so re-evaluation costs
nothing.

## Implementation Checklist

- [ ] **Algorithm core**
  - [ ] `LeafErrorSpanFinder` — depth-first walk over `TraceModel`
        (existing class) keeping the deepest error-tagged span
  - [ ] `MethodToParamMapper` — three strategies (OpenAPI ext, naming, probe-cache)
  - [ ] `AttributionVerdict` enum + result type
  - [ ] Unit tests pinning each branch (no-error trace, single-leaf,
        nested leaves, missing service, ambiguous param-mapping)
- [ ] **Invariant integration**
  - [ ] `TargetAttributionInvariant` implements existing `ShapeInvariant` interface
  - [ ] Wired into `ShapeInvariantStore` enumeration
  - [ ] Config flag `mst.oracle.shape.invariants.target_attribution.enabled`
  - [ ] Allow-listed in `MstConfigValidator.KNOWN_KEYS`
- [ ] **Trace pull**
  - [ ] Reuse `TraceWorkflowExtractor`'s Jaeger client (already
        instantiated). Add a `getTraceById(traceId)` method if not present.
  - [ ] Per-run-id in-memory cache (`Map<String, TraceModel>`) so the
        same trace isn't pulled twice.
- [ ] **Generated-test bridge**
  - [ ] Emit `traceId` into the per-step Allure attachment so post-run
        analysis can pull traces deterministically. (Already done?
        Verify in `MultiServiceRESTAssuredWriter`.)
- [ ] **Report integration (extends Phase 0)**
  - [ ] FaultDetectionTracker adds bucket: TARGET_REJECTION,
        WRONG_PARAM_REJECTION, UPSTREAM_REJECTION
  - [ ] Report section: "ATTRIBUTION-CONFIRMED BUG DETECTIONS"
        (high-confidence subset of Phase 0's oracle anomalies)
- [ ] **Empirical validation**
  - [ ] Reprocess Run 22's traces: how many of the 1,234 detections also
        pass attribution check? Expect ~95%+ for fault-name matches,
        which would corroborate the algorithm.
  - [ ] Process the 6,735 Phase-0 anomalies that don't have fault-name
        match: how many are TARGET_REJECTION? Those are the
        bug-detections-without-injection — the tool's real general
        capability.
- [ ] **Documentation**
  - [ ] Update `flow.md` with the new oracle
  - [ ] Update `AblationProfile` docs with the new flag
  - [ ] One-page methodology section for the eventual A-conf paper

## Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Jaeger trace not available for some tests (trace dropped, sampled out) | Medium | Sample rate config check at runtime; fallback to `NO_ATTRIBUTION` (counts as anomaly, not attributed) |
| `mapMethodToParams` heuristic too lossy on non-TrainTicket SUTs | Medium | Three-tier fallback; (3) probe-cache builds learning per SUT |
| Trace pull adds 1-2 min wall time | Low | Asynchronous pull in batches; doesn't block test execution |
| Span hierarchy ambiguous (parallel calls, retries) | Medium | Use Jha's "unbroken error chain" definition — only descend into error-tagged children |
| OpenAPI spec lacks `x-mist-*` hints (most don't) | High | Document that tier (1) is opt-in; tier (2) naming-heuristic carries the baseline |
| Cross-SUT generalization unproven without another benchmark | High | Pick one secondary benchmark (e.g. Online Boutique, BookInfo, eShop) and re-run; report deltas |

## Success Criteria

- Run 22 traces reprocessed: ≥ 95% of detected fault names also yield
  `TARGET_REJECTION` (algorithm agrees with benchmark ground truth).
- ≥ 10% of Phase-0 oracle anomalies get attributed to a target service
  via Phase 2 (concrete number of "real bugs detected without fault
  injection" the tool finds in TrainTicket).
- One secondary benchmark (without fault-injection markers) shows ≥ N
  attributed detections that match known bug reports for that benchmark.
- Wall-time overhead < 10% on a full run.
- Existing tests stay green;
  `mst.oracle.shape.invariants.target_attribution.enabled=false`
  reproduces Phase-0 behaviour byte-for-byte.

## Out of Scope

- Replacing the fault-name benchmark check. Phase 2 ADDS attribution;
  the existing `data.injected=true` path stays for benchmark
  calibration.
- Full DeepTraLog-style ML model. Phase 2 stays at Jha's heuristic
  algorithm; ML upgrade is a separate research thread.
- Cross-run trace persistence (each run pulls fresh traces from Jaeger
  for its own test-run-id).
