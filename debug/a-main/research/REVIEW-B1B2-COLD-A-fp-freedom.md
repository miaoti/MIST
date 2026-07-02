# Cold review A — FP-freedom of the differential data-integrity FIRE verdict

**Date:** 2026-07-02. Independent cold reviewer (no shared context), one of three
dispatched per the ≥3-cold-reviewer rule during the Gate-1 run-#3 wait. Focus: can
FIRE fire on an execution with no acknowledged-but-lost write? Verbatim findings;
reconciliation in REVIEW-B1B2-RECONCILIATION.md.

---

## What is genuinely sound (verified against code, not comments)

- **X guaranteed absent from the baseline at write time:** `freshValueLike` random
  UUIDs (`DataIntegrityRuntime.java:406-411`); `freshStationPair` excludes every
  baseline pair (`:431-459`); `verdict()` hard-fails NOT_EVALUABLE on
  `baselineContainedX` (`PairedFaultExecutor.java:265-268`).
- **X is request-derived, not response-derived — NOT circular:** `freshen` writes X
  into body + `outKey` (`:372-401`); `afterWrite` polls membership on
  `pending.isolationKey` (`:328`), never the write response; `traceId` is the
  request-injected `__mstTraceId` (writer `MultiServiceRESTAssuredWriter.java:2192,2207`).
- **Intra-run isolation/races handled:** ThreadLocal `pending` (`:173`), synchronized
  records (`:172`), `claimedPairs` ConcurrentHashMap keyset (`:177,447`).

Design is a genuine, thoughtful answer to the prior W1 [FATAL]. Three real FP paths
survive:

## Finding 1 — [CRITICAL] Collection-level membership + monotonic row accumulation (control before fault, no teardown) → systematic, high-confidence-looking false FIRE if the read-back list ever truncates
`DataIntegrityRuntime.java:468-489` (`containsKey`), `:492-517` (`extractItems`),
`:536-557` (`traceComplete`); `PairedFaultExecutor.java:131→:147` (control before
fault), `:284-292` (FIRE regardless of gate); prep target-triples.md:32 (read-back =
`getAllRoutes`; a DELETE exists for cleanup but is unused).

Membership is decided only over the items the list GET actually returns — no
page/size param, no completeness check — and control always runs before fault with no
deletion of created rows, so the fault run always reads a strictly larger collection.
Any server-side cap (Spring-Data default page, gateway cap, truncation) silently drops
the fault run's freshly-written row while control's smaller-collection row stayed
visible → `control.readbackContainedX=true`, `fault.readbackContainedX=false` →
**FIRE with no lost write**. Worse: the write truly persisted, so its trace is present
and stable → gate = OBSERVED_COMPLETE_ABSENT — the high-confidence stratum. This
defeats BOTH the control-run guard AND the trace backstop (persisted-off-page and lost
are indistinguishable: both have complete traces and absent read-backs).

**CONFIRMED** mechanism (no pagination handling anywhere; no teardown in
`execute()`/`benignProbe()`; control-before-fault accumulation structural).
**PLAUSIBLE** trigger (whether TrainTicket's `getAllRoutes`/`getAllContacts` truncate
at scale is unverified; prep assumes completeness but never checks). The benign probe
only partly nets this: `MIN_ACKED_FOR_BAR=20` samples far below the accumulation a
long detection run reaches.

## Finding 2 — [MAJOR] `pick()` collapses multiple per-triple records to the first acked, error-free one; order-dependent under parallelism
`PairedFaultExecutor.java:218-233`, used at `:173-179`.
Several methods hit one triple per run; `pick` returns the first acked+error-free
record, no tie-break toward "present"; under parallel execution the order is
nondeterministic, so one benign-absent write among many present ones can become the
triple verdict (FIRE vs NO_FIRE across thread schedules). Also mixes evidence: control
guard satisfied by control-write-A while "fault absent" comes from fault-write-B.
**CONFIRMED** as coded; an *amplifier* of Findings 1/3 rather than an independent FP
source (each record's own membership is self-consistent). Record counts surfaced
(`:452-453`) so at least visible.

## Finding 3 — [MAJOR] The pre-registered ≤5% bar excludes exactly the timeout-gated fires the FIRE verdict still emits
`PairedFaultExecutor.java:284-292` (FIRE with no `fault.gate` check); bar numerator =
`observedGatedFpFires` (`:336,:384-385`) but `fpFires`/`fpRate` count both strata;
test pins TIMEOUT_ABSENT → FIRE (`PairedFaultExecutorTest.java:155`).
A benign write landing at cap+50ms with Jaeger unreachable → TIMEOUT_ABSENT → FIRE;
the probe files it under `timeoutGatedFpFires`, which does not count against the ≤5%
bar — the advertised bound does not bound the FP rate of the verdict actually shipped.
**CONFIRMED**; substantially **disclosed** (lower-confidence stratum, never pooled),
so the honest defense is "the bar is claimed for the observed-gated stratum only, both
are reported." Residual: the FIRE enum doesn't carry the stratum, so any headline
"N detections" silently mixes strata unless reporting always joins verdict+gate.

## Finding 4 — [MINOR] Ack rule `2xx ∧ status∈{null,1}` can ack an ambiguous 200-without-status benign rejection
`DataIntegrityRuntime.java:300`, `:519-529`. A benign rejection returned as HTTP 200
with no parseable `status` is acked; if it hits the fault run but not control → FIRE.
**PLAUSIBLE**, scoped away on the two shipped targets (envelopes carry status:1/0;
contacts dedupe soft-rejects status:0, correctly excluded). State as a target
precondition.

## Finding 5 — [MINOR] Strict `String.valueOf(...).equals` membership over only two body shapes; server normalization drift unguarded
`DataIntegrityRuntime.java:479`, `:500-509`; station names sent as start/end
(`:421-427,451-453`). Re-typed/normalized keys or `{content:[…]}` wrappers → false
absent. **PLAUSIBLE**; mostly manifests as universal NOT_EVALUABLE (control never
persists either → visible dead oracle, not silent FP). FP only if representation
differs between the two runs — unlikely. Correctness precondition: read-back must echo
keys in the exact form sent.

## Verdict
Partly defensible. The pure-differential FIRE is sound for the sync-CRUD targets
**only under an unstated, unchecked precondition — the collection read-back returns
the complete, untruncated set and is read-your-writes-consistent within the cap.**
Isolation, control guard, gate stratification, benign-probe bar are a real advance,
but the claim is empirically *controlled*, not *free*.

**Single biggest threat:** Finding 1 — read-back-completeness assumption + structural
control-before-fault no-teardown accumulation: a systematic false FIRE that grows over
a run, evades the control guard, and masquerades as OBSERVED_COMPLETE_ABSENT.
Mitigations a reviewer would demand: wire the available per-row DELETE (or
snapshot/restore); assert read-back completeness (paginate to exhaustion or bound
collection size below any server cap); gate the shipped FIRE enum on
OBSERVED_COMPLETE_ABSENT (or carry the stratum in the verdict).
