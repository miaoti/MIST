# G3 head-to-head run architecture — a focused harness over the reviewed oracle logic

Decides HOW the two oracles observe the same cancel→refund scenario, now that the
depth oracle code is built + reviewed (3a94b88, 7453142, dfb68e8, d55c60c). Written
before the harness so the choice is on record.

## The constraint that rules out both existing runners
- The **pairing executor** path (MistRunner) drives GENERATED JUnit tests and hardcodes
  `SutFlagFaultInjector`; the cancel write is a bodyless GET with no generated scenario.
- The **comparator runner** (ComparatorRunner) is POST-only with body-resolved state
  reads (REVIEW-BLIND-CANCEL-B) — it cannot drive a bodyless GET either.
Retrofitting BOTH to drive one bodyless GET (+ a register→create→pay→cancel setup) is
disproportionate and risks perturbing the published Gate-1/G2 paths.

## Decision: a dedicated G3 head-to-head harness reusing the oracle LOGIC (not the runners)
A small hand-authored driver (JUnit-style, in the eval area, flag/dir-scoped so it never
runs in normal builds) that, per {stratum × leg}, drives ONE shared stimulus and applies
BOTH oracles' existing, already-reviewed logic:

- **Stimulus (shared):** register a fresh user → create a PAID order (price>0, far-future
  travelTime; runbook §RUNBOOK) → GET the cancel. Same HTTP calls feed both oracles, so
  the comparison is apples-to-apples.
- **MIST B2 leg:** `DataIntegrityRuntime.beginRun([cancelTriple], leg)` →
  `beforeWriteSupplied(cancel, corr, null, "userId", loginId)` → the cancel GET →
  `afterWrite(cancel, corr, status, body, traceId)` → `endRun()`. Pair control+fault
  records with `PairedFaultExecutor.evaluate` (already package-visible + static) → MIST
  verdict (FIRE / NO_FIRE / NOT_EVALUABLE).
- **Comparator leg:** evaluate the FROZEN blind contract on the SAME cancel response +
  read-backs via `ContractEvaluator` — the executable clauses are the response-envelope
  ones (all state clauses NOT_CHECKABLE, per review B), so this yields the
  response-assertion verdict honestly.
- **Fault application (between control and fault legs):**
  - natural stratum → `IstioRouteFaultInjector` (apply/delete the EnvoyFilter abort);
  - constructed stratum → `SutFlagFaultInjector` (toggle the fork's
    `mist.fault.drawback.fabricatedack.enabled` on ts-inside-payment-service, f57102e6).
  Both injectors are built + reviewed; the harness calls inject/clear directly.

This reuses every reviewed component and adds only the thin driver + the setup calls —
no change to the Gate-1/G2 runners, no bodyless-GET retrofit of the generated path.

## Expected results (the three cells)
| stratum | cancel response (fault leg) | comparator | MIST B2 |
|---------|-----------------------------|------------|---------|
| natural (EnvoyFilter abort /drawback) | `{1,"error"}` (msg leaks) | FLAG (msg gate) | FIRE | → **detection tie, MIST localizes the lost refund** |
| constructed (fabricated-ack flag) | `{1,"Success."}` (clean) | PASS (misses) | FIRE | → **the clean MIST win** |
| agreement anchor (a body-carrying create, e.g. contacts, + fabricated-ack) | clean | FLAG (state clause binds) | FIRE | → **both catch — comparator is no strawman** |

## Prerequisites / order
1. **TT deployed in an Istio mesh** (sidecars on ts-cancel + ts-inside-payment at least)
   — the long pole; the harness is developed against the live SUT (real /account shape,
   auth/JWT, the register→create→pay→cancel flow). Deploy decision: minikube+Istio
   (TT's tested path, `k8s-with-istio` manifests) OR the kind "mist" cluster after
   stopping Sock Shop to fit 26 GB.
2. EnvoyFilter abort manifest (inbound listener, 418, /drawback prefix) + live-verify it
   severs /drawback while /account stays 200.
3. The two `target-triples` configs (supplied + value-delta; constructed carries the
   fault_flag, natural does not) — authored beside the harness's conf.
4. The harness itself, then the runs (natural, constructed, agreement) + Rider-2 breadth
   binding round in parallel.

## Authoring blueprint — verified against the real signatures (read 2026-07-03)
Confirmed by reading PairedFaultExecutor / DataIntegrityRuntime / ContractEvaluator /
AssertionBindings so the live authoring pass is mechanical:

- **MIST verdict entry:** `PairedFaultExecutor.evaluate(injectable, controlRecords,
  faultRecords)` is `static` + package-visible and **iterates the list you pass** — it
  does NOT re-filter by `faultFlag`. So the NATURAL triple (no fault_flag; fault is the
  EnvoyFilter) still gets a verdict: pass `[naturalTriple]` as `injectable`. (Only
  `execute()` filters by faultFlag + drives SutFlag inject; the harness bypasses
  `execute()` and drives the fault itself, calling `evaluate()` for the pure verdict.)
- **Per leg:** `DataIntegrityRuntime.beginRun([triple], leg)` → `beforeWriteSupplied(
  stepKey, corrId, null, "userId", loginId)` where `stepKey` == the triple's
  `write_endpoint` literal ("GET /api/v1/cancelservice/cancel/{orderId}/{loginId}") →
  do the cancel GET → `afterWrite(stepKey, corrId, httpStatus, responseBody, traceId)` →
  `List<RunRecord> = endRun()`. Same `corrId` string on the before/after pair (any stable
  token; aligns the join). `requestBody` = null (bodyless).
- **Http / auth:** the runtime's read-back uses `RestAssuredHttp` (or the
  `defaultHttpOverride` static seam) whose `getSut(path)` runs through MstAuthHandler for
  the JWT — configure the SUT base URL + a valid login before `beginRun`. Single-threaded
  only: leave `mst.test.parallelism` unset or =1 (beginRun refuses >1).
- **TRACELESS GATE (subgraph reality):** ts-cancel-service is sidecar-free in the minimal
  subgraph, so the cancel write yields no Istio/Jaeger trace → `traceId` is null → the
  read-back is **timeout-gated**, not OBSERVED_COMPLETE_ABSENT. The differential verdict
  still holds (control: balance moves +R and X becomes PRESENT fast; fault: balance never
  moves, X ABSENT to the cap → FIRE). Record this as a known weaker-gate limitation; it
  does not affect the head-to-head conclusion. (If a stronger gate is wanted later, add a
  sidecar to ts-cancel too.)
- **Comparator verdict entry:** `ContractEvaluator.evaluate(BoundEndpoint, leg,
  submittedBody, new Response(cancelStatus, cancelBody), sutClient)` → `EndpointOutcome`;
  the leg's comparator verdict = `.flagged` (≥1 evaluated check FAILed). Load the frozen
  contract via `AssertionBindings.load(Path("…/blind-cancel-refund-contract.yaml"))` and
  pick the cancel `BoundEndpoint`. `submittedBody` = a minimal JSON of the write's fields
  (e.g. {"orderId":…,"loginId":…}); the cancel contract's STATE clauses are all
  NOT_CHECKABLE (review B) so only the response-envelope checks bind — natural
  `{1,"error"}` FAILs the msg/status gate (flagged), constructed `{1,"Success."}` PASSes
  everything (not flagged = the miss). `SutClient` is a thin adapter over the same base
  URL (its `get`/`post` need no JWT for the NOT_CHECKABLE clauses).
- **Control leg as the systemic guard (both oracles):** mirror the published semantics —
  a MIST control that isn't acked / never shows X = NOT_EVALUABLE; a comparator control
  whose only failures are transport (`transportFailure`) = infra, not detection.
- **Structure:** a `main()` under a new `io.mist.cli.g3` package (or the eval area),
  flag/dir-scoped so it never runs in normal builds; stimulus HTTP as clearly-marked
  methods to tune against live TT; everything else (the two evaluate() calls + the 3-cell
  print) is stable and compile-checkable now. Still goes through the standing ≥3-cold
  review before its numbers feed any claim.

## Deploy prereq — UPDATED to the minimal subgraph
Prereq (1) is superseded by the minimal cancel→refund **subgraph** on the kind "mist"
cluster (g3-tt-deploy-progress.md): the ~20 services on register→create→pay→cancel +
/account, sidecars added only where the fault needs them (inside-payment for the inbound
EnvoyFilter; cancel stays sidecar-free → the traceless-gate note above). This fits the
machine's memory where the full 40-service graph did not.

*Feeds: the G3 head-to-head data. Depends on: the deployed TT (next).*
