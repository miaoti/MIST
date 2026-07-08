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

## Rounds 2–3 (numbers audit; reconciliation fidelity) — PENDING
Launched against the post-fix state. Their dispositions append here; G3 closes when both are folded.
