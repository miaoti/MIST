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

*Feeds: the G3 head-to-head data. Depends on: the deployed TT (next).*
