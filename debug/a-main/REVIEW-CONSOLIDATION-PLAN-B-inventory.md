# Cold review B — G3 consolidation plan @ 24b7fa9, lens: §1 inventory completeness + accuracy

**Verdict: ACCEPT-WITH-CHANGES.** The six-pillar inventory is the right cut and COMPLETE (no claimable
reviewer-accepted result missing — swept debug/a-main/ + FILE_INDEX; everything else is supporting
infrastructure/evidence correctly not promoted); every headline number in §1 traces exactly to source;
the exclusion list is accurate (bindability-runner "REJECT for the full-empirical-69/80 claim" + 4-option
fork verified verbatim; option 3 closed at 4972d3b verified in git; branch β correctly carts-scoped;
prereg item 3 verified verbatim; all 9 TT + 4 SS raw-log files tracked at HEAD). Fixes below before D1.

## BLOCKING
**B-1 (= review A BLOCKING-1). §2 misstates the TT natural defect using the SUPERSEDED characterization.**
"natural IN SOURCE (drawbackMoney false → {1,\"Success.\"})" names the unreachable branch + the
constructed-only response as the natural instance, contradicting the authoritative
prep/g3-tt-defect-survey.md ("{0,...} branch effectively dead code"; natural acked failure =
"{1,\"error\"}") and the RESULT OF RECORD ("clean {1,\"Success.\"}+lost path is dead code ... genuinely
requires the DISCLOSED constructed fabricated-ack") — and §2's own tie bullet (the tie exists BECAUSE
natural yields {1,"error"} which the msg gate flags). Fix wording: "natural = the fork's own
compensation-failure path (drawback throws → acked {1,\"error\"}, status 1); the in-source clean-ack
swallow (drawbackMoney false → {1,\"Success.\"}) is dead code naturally and is exercised only via the
disclosed constructed fabricated-ack."

## MAJOR
- **M-1. P3's verdict doc self-describes PRELIMINARY** (g3-headtohead-results.md:10 "a re-review gates
  these numbers") while REVIEW-HEADTOHEAD-RECONCILIATION.md:40 records "REVIEWER-ACCEPTED for feeding
  paper claims". Flip the status header (cite round-2) BEFORE D1 cites the doc.
- **M-2. Survey doc carries stale pre-fix numbers contradicting its own recount:** Reading §3 "(not an
  extrapolation from the 9/80)" and Reading §4 "on the 88.75 % the comparator is fine" — 88.75% = 71/80
  is the intermediate post-A+B/pre-C-flip state; the accepted recount is 69/80 = 86.25% / 11 NC.
  REVIEW-SURVEY-RECONCILIATION S-3 ("all percentages restated") was incompletely applied. Correct both
  remnants as a disclosed amendment per the survey's own amendment-discipline rule, before the pack
  cites it.
- **M-3 (= review C MINOR-1). "the 9 rules in the RESULT OF RECORD §Framing" is a wrong-count pointer:**
  that section has exactly 4 bullets; 9 is reachable only by tallying reviewer-tagged rules across the
  whole doc (+§Scope provenance B-MAJOR-3/C-M1, §How-to-read B-MAJOR-2/C-m9, C-M2 count-delta,
  B-MINOR-7 qm rider, A-M1 absence-class). A literal D1 author collects 4 and silently drops 5. Fix:
  enumerate BY ID with source doc.

## MINOR
- m-1. P1 "fires on injected faults" plural overstates: gate1-result.md = FIRE on adminroute-create
  only, "1/1 evaluable constructed case"; contacts = manual G0 only. Use singular / "1/1 evaluable
  (+ manual G0 on contacts)".
- m-2. Review-record pointers conflate regimes: P1's 3-cold-review = the PRE-RUN mechanism review
  (research/REVIEW-B1B2-RECONCILIATION.md), result audited in-doc — no post-result wave like P3–P6;
  P2 = self-adjudication against the pre-registered §4 bar (chain reviews in separate files); P4 has a
  DEDICATED record (REVIEW-SURVEY-RECONCILIATION.md, "3× ACCEPT-WITH-FIXES, all folded") — cite it.
  D1 must name each pillar's regime.
- m-3. D2 mis-attributes the table rule: it is B-MAJOR-2/C-m9 (never-a-win-ratio), NOT C-m4 (that is
  the generalization-axes rule).
- m-4. Claim (iii)'s Rider-2 §2 infra-failure-rate rule: verified verbatim in
  prep/g3-rider2-comparator-protocol.md §2 — but it is a PROTOCOL, never a measured rate (the breadth
  run that would have measured it was rejected). Cite as protocol; add the doc to D1's rule collection.
- m-5. D1's rule collection is missing three standing rules: Rider-1 claim-eligibility (tallies feed
  claims only when joinMode=correlator ∧ correlatorUnique — machine-enforced, printed per cell); the
  G2 scoping rule ("injected wins are calibration evidence only ... PC-moving comparison happens at G3
  over real defects", calibration-result.md); P3's effect-localization qualifier (round-1 finding 7:
  "effect-localization, not fault/component localization") on the plan's "diagnosis/localization".
- m-6. Threats roll-up should include g2-comparator/transcript-retention-note.md (TT blind set's
  "transcript-audited" weakened to process-level attestation; SS has byte-identity verification, TT
  does not) — qualifies the freeze provenance claim (ii) leans on.
- m-7. Claim (ii) must carry the adjective "analytical/expressibility" on the 86.25% fraction in the
  claim wording itself (the empirical version was REJECTED — correctly excluded); otherwise the number
  reads as measured.

## Completeness statement
Six-pillar cut RIGHT and COMPLETE; exclusion list ACCURATE; D2 citations sufficient per claim with
m-4/m-7 guards + P5's carried caveats (pseudo-replication; "[0,0] is a descriptive observed interval,
not a CI"; quiescence machinery not exercised on SUT-2) surviving into the roll-up.
