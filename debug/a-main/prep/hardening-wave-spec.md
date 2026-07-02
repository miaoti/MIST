# Post-Gate-1 hardening wave — spec (test-first), incl. bar v2 numeric floors

**Status:** SPEC 2026-07-02 (written during the run-#3 wait; implementation starts
AFTER the Gate-1 verdict is committed — no MIST code changes while run #3 lives).
Scope = the G3 PREREQUISITES promoted in
[REVIEW-B1B2-RECONCILIATION.md §3](../research/REVIEW-B1B2-RECONCILIATION.md)
(R1fix, R2fix/bar v2, R3fix, R4fix) + the two non-prerequisite items (C-P1-3fix,
R7fix). Each fix ships with unit tests pinned BEFORE the change (karpathy
discipline); the built wave gets the standing ≥3-cold-reviewer 验收. Bar v2's
numeric floors below are **pre-registered here** (they must exist before any run
that uses the bar — recon §3 item 1); they are draft-registered pending the wave's
cold review, and any later change is a disclosed amendment.

## 1. R2fix — bar v2 (`syncFpBar`) [G3 PREREQUISITE]
**Semantics change:** the bar verdict becomes NOT_EVALUABLE (never PASS) when the
observation gate was degraded for the run, and the report always carries the FP
interval + gate histogram (already emitted).

**Pre-registered floors (with rationale):**
- `gateResolvedFraction` = (OBSERVED_PRESENT + OBSERVED_COMPLETE_ABSENT acked
  records) / (all acked records). **Floor: ≥ 0.5.** Rationale: if fewer than half
  of acked benign records got an observation-gated resolution, the numerator
  (observed-gated fires) is computed over a minority stratum and the bar's ≤5%
  claim would ride mostly-unobserved data; 0.5 is the weakest majority requirement
  (deliberately permissive — the bar already carries the interval; this floor only
  blocks PASS-labeling, not reporting).
- `timeoutGatedFraction` = TIMEOUT_ABSENT acked records / all acked records.
  **Cap: ≤ 0.3.** Rationale: timeout-gated absence is the low-confidence stratum
  the numerator excludes; if >30% of the denominator is timeout-gated, the
  excluded mass can hide more than the 5% bar itself several times over
  (0.3 >> 0.05), so a PASS would be uninformative. 0.3 keeps normal poll-latency
  noise tolerable while blocking the B-1 scenario (50% timeout-gated → 4% "PASS").
- Existing `MIN_ACKED_FOR_BAR = 20` unchanged.
- Verdict logic v2: NOT_EVALUABLE if acked < 20 OR gateResolvedFraction < 0.5 OR
  timeoutGatedFraction > 0.3, with the failed guard named in `reason`; else
  PASS/FAIL on `observedGated/acked ≤ 0.05` as today. The all-fires-timeout-gated
  caveat stays.

**Tests (pin first):** (t1) 40 acked, 25 observed-present, 10 timeout-absent,
5 observed-gated fires → gateResolvedFraction 0.75, timeoutGatedFraction 0.25 →
evaluable, 5/40 = 0.125 → FAIL. (t2) same but 25 timeout-absent / 10
observed-present → timeoutGatedFraction 0.625 → NOT_EVALUABLE naming the cap.
(t3) B-1's scenario: acked 100, observedGated 4, timeoutGated 50 → NOT_EVALUABLE
(cap), NOT "PASS 4%". (t4) Jaeger-down run: all absences timeout-gated,
observedGated 0 → NOT_EVALUABLE (both guards), not vacuous PASS. (t5) clean run:
30 acked, 29 present + 1 observed-gated fire → evaluable, 1/30 ≈ 0.033 → PASS.

## 2. R1fix — read-back completeness [G3 PREREQUISITE]
**Change:** (a) record the read-back HTTP status on `RunRecord` (new field; C-P1-2)
and treat non-2xx read-back as `error` (→ NOT_EVALUABLE, never "absent");
(b) a per-triple optional `readback_bound` (registry field): when the read-back
collection size ≥ bound, the record is NOT_EVALUABLE("collection at bound —
completeness unverifiable"); (c) where the surface offers it, paginate-to-
exhaustion / per-entity read-back adapters (TeaStore windowing; petclinic TBD) —
BFF-compatible bounded check is the portable default (B MAJOR-2).
**Tests:** 5xx read-back → NOT_EVALUABLE not absent (kills A-Finding-1's
error-shaped-absence path); baseline 5xx → beforeWrite error → NOT_EVALUABLE;
bound reached → NOT_EVALUABLE; bound not reached → normal verdicts unchanged.

## 3. R3fix — verdict-aware join [G3 PREREQUISITE]
**Change:** replace `pick()`-representative pairing with per-record evaluation:
join control/fault records positionally by (stepKey, occurrence index); a triple
FIREs iff ≥1 joined pair satisfies the fire rule (fault acked ∧ absent ∧ its own
control sibling acked ∧ persisted); pairs with mismatched counts are reported as
`unjoined` (visible, NOT silently dropped). Report per-record pair verdicts +
the triple roll-up.
**Tests:** persisted-variant-first + lost-variant-later → FIRE (kills B-5/C-P1-6
masking); all-persisted → NO_FIRE; count mismatch → unjoined surfaced; single
record per run → identical to today's behavior (regression).

## 4. R4fix — post-settle re-read [G3 PREREQUISITE]
**Change:** after `traceComplete` returns true, do ONE more read-back; if X is now
present → gate = OBSERVED_PRESENT (late arrival, elapsed recorded), NOT
OBSERVED_COMPLETE_ABSENT. Only a still-absent X keeps the upgrade.
**Tests:** present-on-post-settle-read → OBSERVED_PRESENT (kills B-3's
settle-window blind spot); absent-on-post-settle-read → OBSERVED_COMPLETE_ABSENT;
traceComplete false → TIMEOUT_ABSENT unchanged (no extra read).

## 5. C-P1-3fix — persist records before the F2 throw
**Change:** in `execute()`, on clear-failure: compute verdicts + write the report
(marked `"f2ClearFailure": true` with the affected flags) BEFORE throwing
FaultInjectionException. The throw semantics stay (SUT-may-be-faulted is still
loud); the evidence is no longer discarded (run-#2's loss becomes impossible).
**Tests:** injected clear-failure fake → report file exists with f2 marker AND the
exception still propagates; clean path → no marker, no behavior change.

## 6. R7fix — guards (non-prerequisite, same wave)
`beginRun` throws if resolved test parallelism > 1 (C-P1-1: invariant by
construction); `TargetTripleRegistry` validates `readback_endpoint` starts with
"GET " at load (C-P1-9). Tests: both rejects + happy paths.

## Sequencing
Implement after the Gate-1 verdict commit, in the order above (bar v2 and
completeness first — they gate G3's FP protocol), unit-pin each, run the full
mist-core + mist-cli suites, verify flags-off additivity stays byte-identical,
then dispatch the ≥3-cold-reviewer wave on the diff before marking the
prerequisites met. Gate-1's already-produced report (run #3) is NOT re-scored
under bar v2 — its pre-registered bar was v1; the verdict doc reports both
readings if they differ (disclosed, no silent re-registration).
