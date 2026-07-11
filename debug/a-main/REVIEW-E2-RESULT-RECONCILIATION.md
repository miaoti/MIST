# E2 RESULT — 3-cold-review reconciliation (post-run §7 backstop)

**Date:** 2026-07-11
**Reviewed:** the EXECUTED E2 result (`b4/e2/RESULT-e2.md` + evidence; the flipped `TT-cancel-refund-fabricatedack-001` cell; the freeze §6 E2 row; the C1/C2 code).
**Reviewers (cold, independent):** A = oracle-soundness · B = engineering/faithfulness · C = hostile-PC/claim.

## Verdicts

| reviewer | verdict | blocking | major | independently reproduced? |
|---|---|---|---|---|
| A (oracle-soundness) | ACCEPT-WITH-FIXES | 0 | 0 | YES — re-ran scorer + re-scored raw JSON by hand |
| B (engineering) | ACCEPT-WITH-FIXES | 0 | 2 (auditability) | YES — re-ran scorer on RUN1 |
| C (hostile-PC) | ACCEPT-WITH-FIXES | 0 | 1 (wording) | YES — re-ran scorer + checked pre-registration in the commit graph |

**UNANIMOUS ACCEPT-WITH-FIXES, ZERO blocking.** All three INDEPENDENTLY reproduced the claimed verdicts
from the committed traces with the frozen `trace_score.py` (fault: naive MISS / service-map MISS /
DB-span CATCH; control: all present). A hand-verified the fault leg has zero inside-payment DB spans and
the drawback SERVER span is present on both legs (the service-map miss is real, not an artifact). B
confirmed the "invalid parent span IDs" warning proves the injected traceparent was adopted, and that C1
is export-selection-only (afterWrite traceId=null → verdict decoupled from Jaeger). No finding changed a
number or the core claim.

## Findings + disposition

### MAJOR

- **[C-F1 / A-3] The mechanism-robustness/reusability claim was in the indicative but measured on ONE
  drop mechanism.** **FOLDED (RESULT wording):** relabeled as an explicit ARGUMENT BY INSPECTION (a read
  of the durable balance is indifferent to *how* the balance failed to move; the skipped-INSERT is the
  one mechanism measured; cross-mechanism robustness is a design-inference, not measured). This is the
  load-bearing hedge that keeps the datum above a tautology.
- **[B-1] Only RUN1's traces were committed; no per-run report for any run.** **FOLDED (evidence):**
  committed all 10 per-leg traces (`e2-run{1..5}-{control,fault}-trace.json`, re-scorable) + all 5 runs'
  harness stdout (`e2-run-stdout.txt`: FIRE, `TIMEOUT_ABSENT` @ ~20 polls, MISSED, correlator-unique,
  value-delta). All 5 runs now independently auditable.
- **[B-2] The executed `-D` matrix + timeout were unrecorded.** **FOLDED:** `e2-run.sh` = the runner of
  record (exact `-D` matrix + deploy/agent pins); RESULT records the DEFAULT 10 s timeout is load-bearing
  and why it is sound (permanent skip + control lands within the cap).

### MINOR (all folded)

- **[A-2 / B-minor-2] P4 is a separate manual probe on its own buyer pair, presented in the N=5 table.**
  → RESULT annotates P4 as a *mechanism-class* orthogonality cross-check (not per-run); the per-run
  durable evidence is the fault trace's DB-span absence (channel orthogonal to `/account`).
- **[C-F3] N=5 is determinism replication, not sample breadth.** → RESULT N-scope note added.
- **[C-F4] Auditable, not reproducible-from-scratch.** → RESULT reproducibility-posture section added
  (committed traces + frozen scorer re-score; live deploy not turnkey).
- **[C-F6] Leftover "out-of-the-box" / "no pre-specified assertion" strings in the PLAN.** → scrubbed in
  `e2-discrimination-plan.md` §0.2 + §1 to the granularity/per-SUT-binding wording (so no descendant doc
  inherits the retracted claim).
- **[B-minor-1] Stale `CancelRefundHeadToHead` class Javadoc (said the cancel yields no trace).** →
  Javadoc updated: post-C1 the cancel DOES yield a trace; the read-back is timeout-gated BY DESIGN
  (runLeg passes afterWrite null), a load-bearing invariant.
- **[B-minor-3] The C1 test doesn't lock "afterWrite null ⇒ verdict unaffected".** → addressed by the
  pinned load-bearing invariant comment (Javadoc + runLeg): "do not pass the trace-id to afterWrite." A
  full unit test would need a DataIntegrityRuntime mock; the invariant is inspection-enforced + commented
  (the option B offered). C1 test stays green (3/3).
- **[A-4] The exactly-one-trace guard is trivially satisfied (the id-fetch is the dedup).** → noted; not
  over-credited as the dedup mechanism.

## Net

Unanimous accept, zero blocking, all findings folded. The result is **claim-ready under the honest
framing**: on this SYNTHETIC acknowledged-but-lost write, naive + service-map trace oracles miss; a
pre-specified DB-span assertion AND MIST's durable read-back catch; the read-back's specification sits at
a coarser, implementation-decoupled granularity (argued by inspection). It is **provenance-closure + a
component/motivating datum that de-risks the deferred S3 headline — NOT a discrimination result, NOT the
paper headline.** The natural-discriminator headline (S3) stays owed.
