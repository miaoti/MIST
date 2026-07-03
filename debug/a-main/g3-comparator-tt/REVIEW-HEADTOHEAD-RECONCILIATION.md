# G3 head-to-head — ≥3-cold-review reconciliation (round 1)

Three independent cold reviewers (A/B/C, no shared context) reviewed the cancel→refund head-to-head
(harness + frozen bindings + results + fork faults + the source faithfulness). Raw verdicts:
`$CLAUDE_JOB_DIR/tmp/review-{A,B,C}-verdict.md` (transcribed).

## Verdicts: all three ACCEPT-WITH-FIXES

All three independently confirmed the CORE is sound: natural defect faithful + honestly a detection
TIE, constructed defect faithful + clearly disclosed, **no false-FIRE path found**, comparator is not
a strawman at the code level (it catches the natural cell live via the msg gate + caught the G2
calibration faults via STATE_GET), correlator pairing correct, claims literally supported. The fixes
are about making the clean win **un-contestable** and completing the anti-strawman story — not
correctness bugs.

## Convergence

A and B **independently converged** on the sharpest issue: with a fresh zero-balance buyer, MIST's
value-delta degenerates to an appear-vs-absent MEMBERSHIP signal (one operand always null, no
arithmetic), which a response-assertion `STATE_GET` over `/account` could also express — so the
constructed miss's ATTRIBUTION to the value-delta was contestable. C flagged the same tension as a
framing gap. This drove the primary fix.

## Disposition table

| # | Finding (reviewer) | Severity | Disposition | Where |
|---|---|---|---|---|
| 1 | Constructed win is membership-degenerate for a fresh zero-balance buyer (A, B; framing C) | MAJOR | **FIXED** — pre-fund each buyer to a non-zero `/account` balance (addMoney 50.00) before the cancel → the discriminator is a real arithmetic +R delta (control 50→130, fault 50→50), membership true in both legs so it cannot catch it; harness prints a probe line as self-evidence; N=5 stable | `dca01b0` TrainTicketStimulus + CancelRefundHeadToHead; `runs/prefunded-*.log` |
| 2 | Frozen bindings' refund-clause NOT_CHECKABLE reason cites "no per-request auth-token binding" — factually wrong, the comparator DOES carry a JWT (A, B) | MAJOR | **FIXED** — reason corrected to the decisive ground (no arithmetic-delta primitive; membership insufficient against a non-zero baseline; presence≠refund). NOT_CHECKABLE verdict unchanged | `81854cb` assertion-bindings (clause 2 + header + CORRECTION note) |
| 3 | Artifact mismatch: README/triples describe EnvoyFilter/SutFlag + "No fault_flag" but the run used the HttpToggle + a fault_flag (C MAJOR; B MINOR) | MAJOR | **FIXED** — README + both triples rewritten to the runtime-toggle mechanism of record (restart/mesh kept as rejected); natural catch attributed to CancelController per the source check | `81854cb` README + target-triples-* |
| 4 | Agreement anchor (body-carrying write, both catch) not run beside the two cells (B) | MAJOR | **DONE** — built `AccountCreateAgreement` (body-carrying createAccount + runtime fabricated-ack); N=5 AGREEMENT (MIST FIRE via membership + comparator STATE_GET binds & CATCHES). Direct anti-strawman evidence beside the two cells | `9dcf5f2`; `runs/agreement-*.log` |
| 5 | Frame "natural" precisely — injected trigger of the fork's own catch path, not "no injection" (C) | MINOR | **FIXED** — results doc "Natural cell" section: CancelController.java:45-51 catch, order flipped to CANCEL before drawback throws | results doc |
| 6 | Don't overclaim dominance — MIST fires only on ACKED writes; a status:0 loud fail → MIST NO_FIRE but comparator catches (A, C) | MINOR | **FIXED** — results doc "complementary, not a strict superset" section | results doc |
| 7 | "Diagnostic edge" = effect-localization, not fault/component localization (A, B, C) | MINOR | **FIXED** — results doc already scoped (`ad37b61`); kept | results doc |
| 8 | VALUE_DELTA disables the executor isolation tripwire; isolation is a runbook rule (A, B, C) | MINOR | **DISCLOSED** — results doc disclosures + README RUNBOOK; the pre-write baseline stability double-read covers the residual hazard | results doc + README |
| 9 | Traceless timeout-gate = confidence not soundness (A, B, C) | MINOR | **DISCLOSED** — results doc disclosures + harness javadoc; not a false-FIRE (permanent loss, control lands in-window) | results doc |
| 10 | Confirm inside-payment replicas=1 (C) | MINOR | **FIXED/DISCLOSED** — confirmed spec=1/ready=1; recorded in results doc + README | results doc + README |
| 11 | FIRE depends on both legs' same nonzero R — stimulus-enforced (B) | MINOR | **DISCLOSED** — results doc disclosures | results doc |
| 12 | Run evidence transient (tmp logs gone); persist machine-readable records (B) | MINOR | **FIXED** — `runs/` dir committed (prefunded-*.log + reps.txt with the probe balances) | `runs/` |

## Re-review plan

All 12 findings are FIXED/DISCLOSED (the agreement anchor is now built). Next: a fresh ≥3-cold
re-review (round 2) of the complete 3-cell result — verify the round-1 fixes are sound (especially
the pre-fund making the clean win a genuine arithmetic delta, and the agreement anchor being a fair
both-catch demonstration) and that the claims now hold, before any number feeds a paper claim.
