# G3 head-to-head — ≥3-cold-review reconciliation (rounds 1 + 2)

## ROUND 2 (re-review of the fixed 3-cell result) — ALL THREE ACCEPT(-WITH-FIXES); fixes landed

Three fresh independent cold reviewers verified the round-1 fixes are SOUND, not merely present
(raw verdicts: `$CLAUDE_JOB_DIR/tmp/review-R2C-verdict.md` + the A/B emissions):
- **A: ACCEPT-WITH-FIXES (doc-consistency only)** — "the code, verdict logic, fork faults, and
  committed run evidence are sound and mutually consistent"; verified at fork-source level that the
  refund is intrinsically delta-only observable (no endpoint exposes the Money row; /money returns
  data:null always), so even a generously renamed membership binding cannot catch the clean win.
- **B: ACCEPT-WITH-FIXES** — "no rerun is required for the three cells' numbers to be trustworthy";
  verified the pre-fund is purely additive (no confound), directionally safe (a late pre-fund can
  only suppress FIREs), and the agreement catch is attributable to the STATE clause.
- **C: ACCEPT-WITH-(MINOR)-FIXES, leaning ACCEPT** — "the numbers can feed the paper"; verified
  un-contestability compounds: membership passes both legs, the closed set STRUCTURALLY lacks a
  pre-write snapshot seam, and even binding ALL three NOT_CHECKABLE clauses wouldn't discriminate
  (the order flips to CANCEL in both legs; ENVELOPE_DATA identical).

### Round-2 disposition (all landed)

| # | Finding (reviewer) | Severity | Disposition |
|---|---|---|---|
| R2-1 | Stale ground-truth doc still showed the superseded ZERO-baseline capture while cited as clean-win evidence (A+B converged) | MAJOR | **FIXED** — recaptured byte-level with PRE-FUNDED buyers (both legs PRESENT at 50.00; acks identical; 130.00 vs 50.00); old capture retained as a clearly-marked SUPERSEDED appendix |
| R2-2 | Pre-fund evidence-only → a soft-failed 200-{0} addMoney could silently regress the cell to the contestable membership shape (B) | MINOR | **FIXED** — stimulus asserts the addMoney envelope `{status:1}`; runner aborts unless both legs' parsed baselines are the SAME POSITIVE number (`requirePreFundedBaselines`); for-record rerun gated (`runs/prefunded-run4-gated.log`, `runs/agreement-run3-gated.log`) |
| R2-3 | Claim-eligibility (rider-1 joinMode=correlator ∧ correlatorUnique) not asserted in the harness (C) | MINOR | **FIXED** — `requireClaimEligible` hard-asserts in both runners + prints the claim-eligibility line per cell |
| R2-4 | `HttpToggleFaultInjector.modeOf` accepted any fault family → the agreement createaccount property would silently toggle the DRAWBACK endpoint (A+B) | MINOR | **FIXED** — segment 2 pinned to `drawback` + rejection test (suite 141) |
| R2-5 | Stale comments: harness javadoc said SutFlagFaultInjector; agreement TODO; `PairedFaultExecutor` javadoc said EnvoyFilter; fork comment claimed an inert `-D` channel (A/B/C) | MINOR | **FIXED** — all four corrected (fork comment on MIST-trainticket) |
| R2-6 | Image-tag provenance 1.0.4 vs 1.0.5 + N=5 log bookkeeping (A/C) | MINOR | **FIXED** — provenance section in the results doc; all three cells re-verified on the uniform `:1.0.5` (`*-v105.log`, `*-gated.log`) |
| R2-7 | README omitted the agreement cell (A) | MINOR | **FIXED** — Files + cells table now list all three |
| R2-8 | Precision nits: agreement envelope is `{1,"Create Account Success"}`; "catches whenever its primitives bind" must stay existence-scoped (A/B/C) | MINOR | **FIXED** — results doc corrected + scoped ("in every cell where …; frequency claims belong to the breadth / Rider-2 round") |

### Standing framing rules for the PAPER (discipline, not artifact defects)
1. The clean-win claim is scoped to the **response/contract-assertion oracle class**; keep the
   concession adjacent ("an oracle with snapshot+delta+arithmetic IS MIST"). An unqualified "no
   baseline can catch this" is an overclaim (C's biggest residual concern).
2. External validity: one constructed (disclosed) defect, one SUT, one frozen comparator class —
   expect reviewers to probe how often delta-only observables occur in the wild; the Rider-2
   bindability fraction is the prepared answer (A's biggest residual concern).

**STATUS: the 3-cell head-to-head result is REVIEWER-ACCEPTED for feeding paper claims** (round-1
fix wave + round-2 verification, all findings landed), subject to the two standing framing rules.

---

# Round 1 (original review of the 2-cell result)

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

All 12 round-1 findings were FIXED/DISCLOSED; the round-2 re-review then ran and accepted — see the
ROUND 2 section at the top of this file for its verdicts, disposition table, and the two standing
framing rules.
