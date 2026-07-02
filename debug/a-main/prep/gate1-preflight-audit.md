# Gate-1 Pre-Flight Audit & Live Verification (run #3)

**Date:** 2026-07-02
**Status:** run #3 (lean-topology automated pairing) executing. This note records the
independent author-audit and live checks done to (a) de-risk that run and (b) supply
soundness evidence for reviewer 验收. It is **not** the Gate-1 verdict — that lands in
[gate1-result.md](gate1-result.md) once run #3 emits its report.

## 1. Independent code audit — 4 core files, all sound

Author re-read of the built B1+B2 code as a hostile reader, verifying against the code
(not the comments).

**`PairedFaultExecutor`** (pairing + verdict + FP bar)
- Sequence: clear-all → control run → inject (inside the `try`) → fault run →
  clear-all (`finally`). A failed inject still reaches clear-all; the F2 fail-safe
  throws if any clear cannot be confirmed, **before** `writeReport` → the SUT is
  never left faulted.
- `verdict()`: NOT_EVALUABLE for missing record / control error / fault error /
  isolation violation (`baselineContainedX`) / control-not-acked / control-readback-absent
  (systemic guards); NO_FIRE for fault-not-acked (base relation vacuous) or
  fault-persisted; FIRE **only** for fault-acked ∧ absent-on-own-readback ∧
  control-persisted. Environment failures are reported as broken pairs, never as
  NO_FIRE evidence.
- `fpProbeJson`/`syncFpBar`: non-timeout-gated FP = `observedGatedFpFires/acked`;
  NOT_EVALUABLE unless ≥ 20 acked (`MIN_ACKED_FOR_BAR` — a collapsed denominator
  cannot auto-PASS); caveat when all fires are timeout-gated (numerator structurally 0);
  observed-gated and timeout-gated strata are **never pooled**; the FP-vs-timeout curve
  censors beyond the cap and does not re-count a presence seen on the final poll past
  the cap.

**`DataIntegrityRuntime`** (isolation + quiescence + normalization)
- Isolation (`freshen`): strips server-assigned `id` (no create→update flip);
  FRESH_STRINGS is form-preserving (UUID-shape → fresh UUID, else `mist-` prefix — the
  Contacts.accountId Jackson fix); STATION_PAIR picks an existing unused `(start,end)`
  pair with a **cross-thread `claimedPairs`** set. X is request-derived, never read from
  the response (no circularity).
- Quiescence (`afterWrite`): polls the read-back until present (OBSERVED_PRESENT) or
  the timeout; on timeout upgrades to OBSERVED_COMPLETE_ABSENT **only** if the write's
  own Jaeger trace (exact traceparent id) is present with a span count stable across the
  settle window, else TIMEOUT_ABSENT. Conservative on any failure.
- Normalization (`containsKey`): business-key projection — matches only isolation-key
  fields; volatile fields (id/timestamps) are excluded. Hooks never throw (record the
  error and pass the body through).

**`SutFlagFaultInjector`** (B1 toggle)
- `inject` appends `-D<property>=true` to `JAVA_TOOL_OPTIONS` (idempotent), then
  `awaitRollout` blocks on `kubectl rollout status` until convergence, then settles for
  stale nacos/ribbon caches → the flag is genuinely live **before** the fault run.
  `clear` strips exactly the token, restoring the remainder. Append/strip **preserves**
  the `-javaagent:/otel/...` token, so injection does not break tracing (the
  OBSERVED_COMPLETE_ABSENT stratum survives the fault). Non-zero kubectl exit →
  `FaultInjectionException` (loud). Explicit `--context`; bounded timeouts +
  `destroyForcibly`.

**`FaultInjector`** — the interface + `FaultInjectionException` used above.

**Author-audit verdict:** the mechanism is sound end-to-end; no false-positive path and
no silent lost-inject path found on re-read.

## 2. Live environment verification (run #3 deploy)

- **Traceparent propagation CONFIRMED live:** an adminroute GET carrying a fabricated
  W3C traceparent was retrieved from Jaeger by that **exact** trace id (3 spans,
  ts-gateway-service). → `afterWrite`'s absence-upgrade to OBSERVED_COMPLETE_ABSENT is
  real, not vacuous; run #3's FIRE-absence evidence and the FP-bar numerator will be the
  trace-confirmed **strong** stratum.
- **Config verified:** `jaeger.base.url=http://localhost:30005/jaeger/ui/api`,
  `jaeger.enabled=true`, `faulty.ratio=0.0`, enhancer OFF, `two.phase` ON,
  `dataintegrity` ON, poll=500 / timeout=10000 / settle=3000, `fpprobe.runs=30`.
- **Isolation feasibility:** station catalogue = **87 stations** → station-pair isolation
  cannot exhaust or degrade to pass-through (the run #2 failure mode is fixed).
- **Memory headroom:** lean topology ~13–15 GiB used in a 26 GiB WSL cap; MIST `-Xmx4g`
  → ~10 GiB free, no wedge risk (the run #1/#2 root cause was the memory *budget*, not
  machine size — see [gate1-infra-incident.md](gate1-infra-incident.md)).

## 3. Independent cold review (reviewer 验收) — COMPLETE

Three independent cold reviewers (no shared context) ran in parallel with run #3 and
returned; findings + consensus in
[REVIEW-B1B2-RECONCILIATION.md](../research/REVIEW-B1B2-RECONCILIATION.md)
(individual reports: [A — FP-freedom](../research/REVIEW-B1B2-COLD-A-fp-freedom.md),
[B — FP-measurement](../research/REVIEW-B1B2-COLD-B-fp-measurement.md),
[C — impl + contribution](../research/REVIEW-B1B2-COLD-C-impl-contribution.md)).

**Outcome:** all three independently confirm the Gate-1 sync-CRUD mechanism sound
(non-circular isolation, race-safe at parallelism=1, correct verdict guards, safe
batch inject/clear, correct arithmetic); **nothing invalidates run #3**. The
substantive consensus findings (read-back-completeness precondition; the ≤5% bar is a
Jaeger-dependent lower bound → report the FP interval; pick() positional join;
OBSERVED_COMPLETE_ABSENT wording) drive the run-#3 report-audit checklist and the
post-run hardening list in the reconciliation doc — they change how the numbers are
audited and worded, not whether the run is valid.

## 4. Net effect on run #3

The run is set up to yield a strong, valid result: inject is rollout-confirmed and
tracing-preserving (valid fault run), the absence stratum is trace-confirmed (strong
verdicts), isolation cannot degrade (87 stations), and the FP bar is measurable
(≥ 20 acked over 30 iterations). **Residual:** the SUT-side drop logic is external
(train-ticket-injection@injection), validated manually by G0
([gate1-smoke-result.md](gate1-smoke-result.md)); the cold-reviewer findings are pending.
