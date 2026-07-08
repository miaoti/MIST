# Reconciliation — D1 (g3-result.md) + D2 (g3-evidence-pack.md) review wave

One 3-cold-review wave over both deliverables (per the reconciled consolidation plan §4).
Round 1 = usability/hostile-PC lens (completed @ 10efbe1); the numbers-audit and
reconciliation-fidelity reviewers were terminated mid-run by a session limit and are RE-RUN as rounds
2–3 against the fixed state (they audit the post-fix docs; the fidelity reviewer additionally verifies
the round-1 fixes below landed soundly). This mirrors the TT round-1/round-2 pattern.

## Round 1 — usability / hostile-PC (VERDICT: ACCEPT-WITH-CHANGES) — dispositions
| # | Finding | Disposition |
|---|---|---|
| B1 BLOCKING | Claim 1's provenance clause false for the two TT fork-flag cells (internal contradiction with Table 1) | **FIXED** — claim 1 replaced with the reviewer's two-sentence split (fork-flag carve-out inline; "no discovery-in-the-wild is claimed" imported into the claim) |
| M1 | TT-natural trigger misdescribed as "Istio abort on /drawback" in BOTH docs — the RESULT OF RECORD's executed mechanism is the runtime fault toggle; the EnvoyFilter/Istio abort was tried and REJECTED (pooled-connection race) | **FIXED in both** (D1 §3 row, D2 Table 1 row): "runtime fault toggle: drawBack throws → HTTP 500; EnvoyFilter/Istio abort tried and REJECTED — P3 §Fault mechanism". Verified against P3 §Fault mechanism before editing |
| M2 | Residue-class overclaim ("the class the oracle covers"; "delta/aggregate") | **FIXED** — claim 2 now: "the structurally unbindable residue (11/80) is exactly the delta/transition/object-shaped primitive-gap class the depth cells exercise"; D1 verdict relabeled to census language (ONE-PHRASE disclosed deviation from review A's verbatim sentence, noted inline under the verdict) |
| M3 | D2 not self-contained (P1–P6 undefined) | **FIXED** — pillar key added to the preamble |
| M4 | No artifact-availability statement | **FIXED** — repo/branch/commit + freeze pins + log locations in the preamble |
| M5 | No operational legend; benign N undefined | **FIXED** — FIRE/CAUGHT/MISSED/N legend under Table 1; benign row "(1 stratum / 2 legs)" |
| M6 | The MISSED→CAUGHT dual-form datum derivable but unstated | **FIXED** — appended to claim 2's scope sentences |
| m1 | 2,127 = 30×71 − 3 invalid | FIXED (Table 2 + D1 §4 P1) |
| m2/m3 | "the same SUT's" ambiguous; "§6" unanchored | FIXED ("TrainTicket's"; "the plan README §6 comparator demand") |
| m4 | scope fence not prominent | FIXED (bolded, own line, ⚠) |
| m5 | map cited unnumbered rows | FIXED (instance names) |
| m6 | agreement row descriptor incomplete | FIXED (· sync DB write · account membership (list)) |
| m7 | qm→0 rider absent from D2 | FIXED (footnote e) |
| m8 | TT value-delta benign-mode evidence unstated | FIXED (footnote f: in-cell 5/5 control legs 50→130 + requirePreFundedBaselines; Gate-1's 2,127 = membership/gate mode) |
| m9 | class exemplars | FIXED (claim 2: "the Pact/Dredd/synthetic-monitoring class — R-SS-2") |
| m10 | agreement row "—" cell | FIXED ("analytically misses (schema-valid clean ack); fairness anchor, outside the gate conjunction") |

Round 1's hostile-attack audit: attack 1 (self-injected) = pre-rebutted AFTER B1's fix; attack 2
(comparator self-strengthened / 86% self-analysis) = pre-rebutted incl. M6's added datum; attack 3
(correlated-zero FP) = pre-rebutted as written (Table 2 structure + the FP-vs-timeout curve showing
the instrument CAN fire). Round 1 also independently verified: all cited logs/JSON exist and are
git-tracked; headline numbers reconcile with pillar sources; the prereg §2 quote verbatim; "Tracetest
appears nowhere in the prereg" true by grep.

## Round 3 — reconciliation fidelity (VERDICT: ACCEPT-WITH-CHANGES, all MINOR, zero BLOCKING/MAJOR)
**Discharge statement: "YES — the plan reconciliation and the round-1 fix wave are fully discharged in
substance"** (every adopted disposition landed; token-level verdict-sentence compare = identical to
review A's except the ONE disclosed relabel; prereg §2 quote verified verbatim; numbers reconcile
across D1↔D2↔pillars incl. the raw-log git-tracking; residual-overclaim sweep CLEAN — no finds/detects
slippage, 86.25% analytical at every occurrence, tie cells never tallied as misses, D2 inside the
Plan-B-plus footing). Dispositions:
| # | Finding | Disposition |
|---|---|---|
| MINOR-1 | secondary-localization delivered as a paragraph, not a map row | **FIXED** — 4th map row added (both tie cells; R-TT-3 + R-SS-1) |
| MINOR-2 | gate-status not adjacent to claim 2 | **FIXED** — added to the ⚠ scope fence: "Gate 3 is NOT met as written; closed under the disclosed re-scope, routing to Plan-B-plus" |
| MINOR-3 | provenance/status staleness (pack "authored at 10efbe1"; D1 vs D2 status lines inconsistent) | **FIXED** — both docs now: authored @ 10efbe1, round-1 fixes @ 26a8c97, rounds 2–3 per this file |
| MINOR-4 | deviations-ledger seeds row read SS-only tally as the whole | **FIXED** — "(SS 14/14 pre- + 6/6 post-reboot fault legs; TT 5/5 × 3 cells)" |
| MINOR-5 | citation hygiene (bare review-record filenames unresolvable at the stated root; complementarity un-ID'd) | **FIXED** — `g3-comparator-tt/` + `research/` prefixes in D1 §5 + D2 map; complementarity = **R-TT-4** |
| Observation | §3 column 3 could read as claiming a bare status/schema tool catches TT-natural | **ADDRESSED** — column-definition line added above the table ("as executed = the frozen contract's envelope gates; a bare tool would additionally miss `{1,"error"}` — the filling errs against MIST") |

## Round 2 — forensic numbers audit (VERDICT: ACCEPT-WITH-CHANGES, 4 MINOR, zero BLOCKING/MAJOR)
The auditor RE-COUNTED the raw logs itself (TT: prefunded-run2 + reps 2–5 = 5/5 FIRE+CAUGHT / 5/5
FIRE+MISSED with 50→130 vs 50→50 log-exact; agreement 5/5; v105 re-verification logs incl.
claim-eligibility lines. SS: 14/14 pre-reboot + 6/6 post-reboot fault-leg FIREs; pilot depths
digit-for-digit; the corroboration log EXHIBITS the ~5 s lag). Every quote verified verbatim (Gate-3
sentence; decisive-result pin; prereg-recon item 3; transcript-retention note; Rider-2 §2 rule;
review A's verdict sentence word-for-word with the one disclosed relabel; "Tracetest nowhere in the
prereg record" re-confirmed by repo grep). All rule IDs trace; all arithmetic checks. Dispositions:
| # | Finding | Disposition |
|---|---|---|
| MINOR-1 | "~24 s/TT cell" re-unitized (source: ~24 s per HARNESS REP spanning BOTH cancel cells ≈12 s/cell; agreement rep ≈23 s) | **FIXED** in D1 §7 + D2 map row 3 |
| MINOR-2 | claim-1 "dead code upstream" documented for the CANCEL cell only (agreement's createAccount fabricated-ack is fork-ADDED) | **FIXED** — "clean-ack-and-lose behavior does not exist upstream (on the cancel cell it is literally dead code)" |
| MINOR-3 | footnote (b) "TWO fault phenomena per SUT" (TT has a third injected instance = the agreement anchor) | **FIXED** — "TWO headline fault phenomena per SUT plus anchor/control rows" |
| MINOR-4 | D1 P2 row quoted a compressed, not verbatim, G2 scoping sentence | **FIXED** — verbatim sentence restored (incl. "the fault class is oracle-co-designed") |
| notes (a)(b) | "[0,0] descriptive not a CI" on P1 = the C-mandated extension of P5's language (semantically correct); R-R1 per-cell printing lives in the full-verbosity logs (filtered reps captures omit the line; machine enforcement + full logs carry it) | no change needed — recorded |

## WAVE CLOSED — G3 ARC CLOSED
All three rounds ACCEPT-WITH-CHANGES; every disposition folded (rounds 1–3). Round 3's discharge
statement ("the plan reconciliation and the round-1 fix wave are fully discharged in substance") +
round 2's numerical faithfulness confirmation stand. **D1 `g3-result.md` is the Gate-3 RESULT OF
RECORD; D2 `g3-evidence-pack.md` is the liftable paper-evidence pack.** The verdict: Gate 3 NOT MET
as originally written — closed as MET-UNDER-DISCLOSED-RE-SCOPE, routing to Plan-B-plus; the
empirical-track claim stays "credible, not yet clear" until C2/C3 exist. Next = the direction
decision (user): paper writing on the Plan-B-plus footing vs building C2/C3 vs remaining β.
