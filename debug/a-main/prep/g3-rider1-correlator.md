# G3 Rider 1 — writer-side correlator join (H1 / comparator-C13) — BUILT

**Status:** BUILT + test-first, full suites green (35 llm + 331 core + 98 cli, +5
tests). Awaiting the standing 3-cold-reviewer wave. Closes the DESCRIPTIVE-ONLY
caveat that [REVIEW-HARDENING-RECONCILIATION](../research/REVIEW-HARDENING-RECONCILIATION.md)
(H1) and [REVIEW-COMPARATOR-RECONCILIATION](../research/REVIEW-COMPARATOR-RECONCILIATION.md)
(C13) placed on the per-pair tallies: they may now feed claims.

## Problem
The pairing verdict join (hardening R3fix) pairs the *i*-th control record with the
*i*-th fault record **positionally**. Positional alignment is correct only when both
legs emit the same record sequence. Gate-1 run #3 produced **71 control vs 70 fault**
records for one triple: a single asymmetric skip (a write that executed in one leg
but not the other — a guarded precondition, a transport death between hooks) shifts
**every subsequent pair by one**, so a present control write can be paired against a
different write's absent fault leg → a spurious FIRE (or a masked one). The old join
surfaced the count delta as `unjoinedRecords` but still verdicted the misaligned
pairs.

## Mechanism
The writer already generates one test method per scenario and numbers each step. It
now stamps a **generation-time correlator `<testMethodName>#<stepIdx>`** onto both
data-integrity hooks. Because the *same generated test file is compiled once and run
twice* (control label, then fault label), the correlator for a given hooked write is
**identical across the two legs** — a free, deterministic join key, no runtime state.

- `MultiServiceRESTAssuredWriter` emits `beforeWrite(stepKey, "<method>#<idx>", body)`
  and `afterWrite(stepKey, "<method>#<idx>", status, body, traceId)` — only inside
  the existing `__diTriple != null` block, so **flag-off output is byte-identical**
  (pinned by `DataIntegrityEmissionTest`).
- `DataIntegrityRuntime` carries the correlator through `Pending` onto every
  `RunRecord` it builds (happy path, not-acked, error, orphan-synthetic,
  afterWrite-without-before, read-back-error). Legacy 2-arg/4-arg hook overloads
  pass `null` → positional fallback preserved.
- `PairedFaultExecutor.evaluate` joins by correlator **iff every record on both
  sides carries one** (`joinRecords`): builds a multimap fault-correlator→records,
  matches each control to its like-correlator fault, and counts the leftovers on
  BOTH sides as `unjoinedRecords`. Any null correlator (legacy suites) → the exact
  prior positional join, byte-for-byte. The correlator is also emitted per record in
  the pairing report JSON for audit.

## Why it is sound (not a claim-inflator)
- **Comparator-neutral / MIST-neutral:** the correlator only *aligns* pairs; it never
  changes a single pair's verdict rule. It removes false FIREs from misalignment
  (which would have *inflated* MIST recall) and false NO_FIREs alike.
- **Fail-safe:** an unmatched write is `unjoinedRecords`, never a silent drop; the
  triple still FIREs iff ≥1 *correctly-aligned* pair fires.
- **No new operating assumption:** single-threaded execution (beginRun guard) already
  makes per-leg order deterministic; the correlator makes *cross-leg* identity
  explicit instead of positional-by-convention.

## Tests (test-first)
- `PairedFaultExecutorTest.correlatorJoin_middleSkip_avoidsMisalignedFalseFire` — the
  run-#3 shape: control [A present, B absent], fault [B only]. Correlator →
  NOT_EVALUABLE (B absent in both) + 1 unjoined; the companion
  `positionalFallback_nullCorrelators_reproducesMisalignedFire` shows the SAME shapes
  without correlators FALSELY FIRE (the exact bug the rider fixes).
- `correlatorJoin_reorderedFaultLeg_pairsLikeForLike` — asserts the fired pair is
  like-for-like (`control.correlationId == fault.correlationId`), which a crossed
  positional join would violate.
- `DataIntegrityRuntimeTest.g3Correlator_flowsFromHooksOntoRecord` /
  `_legacyHooksLeaveItNull` — hook→record propagation + the null fallback.
- `DataIntegrityEmissionTest` — the two emission regexes now pin `"<method>#<idx>"`
  with the index tied to the step number; the three hook-free cases stay hook-free.

## G3 consequence
The per-pair tallies (`firePairs` / `noFirePairs` / `notEvaluablePairs` /
`unjoinedRecords`) are now **correlator-aligned** and may feed the Gate-3 detection
claims. The prereg §0 "R3fix is a G3 prerequisite" is satisfied by the stronger
correlator join; `unjoinedRecords > 0` remains a disclosed data-quality signal per
run (asymmetric execution), not a verdict.
