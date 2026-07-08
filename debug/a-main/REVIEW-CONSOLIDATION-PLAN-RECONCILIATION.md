# Reconciliation — 3 cold reviews of the G3 consolidation plan (@ 24b7fa9)

Reviews: `REVIEW-CONSOLIDATION-PLAN-A-adjudication.md` (gate-adjudication honesty),
`REVIEW-CONSOLIDATION-PLAN-B-inventory.md` (inventory accuracy),
`REVIEW-CONSOLIDATION-PLAN-C-paper.md` (paper-readiness).
**All three: ACCEPT-WITH-CHANGES.** No finding requires new experiments; every fix is
wording/structure/source-hygiene. The plan is EXECUTED AS RECONCILED below (the original plan doc
stays as-committed for provenance; D1/D2 are authored per this reconciliation).

## Convergences (independent reviewers, same finding)
- **A-BLOCKING-1 = B-BLOCKING-1:** the plan's §2 resurrected the SUPERSEDED "TT clean-ack natural in
  source" characterization. Corrected everywhere to the authoritative mechanics: natural = drawback
  throws → acked `{1,"error"}` (a genuine missing-compensation defect the comparator's msg gate
  CATCHES); the in-source clean-ack swallow is DEAD CODE on the unmodified fork; the clean-ack lost
  refund requires the disclosed constructed fabricated-ack.
- **A-MINOR-10 = B-M-1:** the TT RESULT OF RECORD still carried a stale "PRELIMINARY" header. **FIXED
  pre-D1** (header now cites the round-2 REVIEWER-ACCEPTED status; numbers unchanged).
- **B-M-3 = C-MINOR-1:** "the 9 rules in the RESULT OF RECORD §Framing" was a wrong-count pointer.
  D1 enumerates every framing rule BY ID WITH SOURCE DOC (results docs + reconciliations).
- **B-m-1 = C-MINOR-2:** P1 plural overstated → "one constructed site (adminroute), strong stratum,
  1/1 evaluable; second site manual G0 smoke only."

## Dispositions — Review A (adjudication)
| # | Finding | Disposition |
|---|---|---|
| BLOCKING-1 | superseded TT-natural claim | FIXED (see convergences; corrected wording used in D1 §adjudication + everywhere) |
| BLOCKING-2 | per-leg = conjunction fallacy | D1 adjudicates PER-INSTANCE (table: TT-natural / TT-constructed / SS-natural / SS-constructed / agreement / benign × real-non-injected? / status-schema-miss? / strong-comparator-miss? / trace-status) + the one-sentence conjunction outcome: **no single real, non-injected instance was missed by both executed oracles** |
| BLOCKING-3 | decisive-result pin unadjudicated | D1 quotes the G2 prereg §2 decisive-result definition and reports it **UNMET** (wild + frozen-set-no-flag + rater-adjudicated: none satisfied), BEFORE presenting what is met |
| MAJOR-4 | re-scope citation overstated | D1 states: Cast half reviewed out pre-run at G2 (item 3, scope = which comparator to BUILD); Tracetest half neither reviewed out nor executed; adjudicating the GATE against the re-scoped class = a **disclosed amendment made now, at consolidation**; leg NOT met as written; executed deployments traceless on target paths = feasibility context only |
| MAJOR-5 | "finds" unadjudicated | D1 defines the demonstrated capability as "detects, end-to-end black-box, when the defect is exercised"; no discovery-in-the-wild claim; defect sites were human-located (survey / wild-hunt) |
| MAJOR-6 | no-errored-span overclaims + wrong place | Moved to threats-to-validity, weakened to error-STATUS assertions, + the span-PRESENCE counterfactual and instrumentation caveat added; the leg-3 adjudication line says only "not executed — not met as written" |
| MAJOR-7 | protocol-fidelity deltas | D1 carries a DEVIATIONS LEDGER mapping each G2-prereg pinned output (κ raters, operating-point tables, ≥10 seeds MWU/Â₁₂) to what was produced (N=5 deterministic focused harnesses, author-adjudicated + cold-reviewed) with the defensibility note (deterministic categorical outcomes; not generation-driven) |
| MAJOR-8 | ≥2-SUTs bullet needs the qualifier inside | D1 wording: detection CAPABILITY reproduces across 2 SUTs/hazards/sinks; the BOTH-ORACLE-MISS demonstration reproduces only in its constructed form (disclosed fork flag on TT; injected operational policy on unmodified image on SS) |
| MINOR-9 | "analytically forced" attach | "MET — analytically forced and confirmed live"; as-frozen rows never tally as comparator defeats |
| MINOR-10 | ladder position + stale header | Verdict routes to **Plan-B-plus** (exceeds the §9 Plan-B floor; does not discharge Plan-A's "Gate 3 yields real bugs" trigger); header FIXED |

**A's bottom-line verdict sentence is ADOPTED as D1's verdict** (with C's positioning sentence appended).

## Dispositions — Review B (inventory)
| # | Finding | Disposition |
|---|---|---|
| B-1 | superseded TT-natural claim | FIXED (= A-BLOCKING-1) |
| M-1 | P3 doc self-described PRELIMINARY | FIXED pre-D1 (header flipped, cites round 2) |
| M-2 | survey stale remnants (9/80, 88.75%) | FIXED pre-D1 as a disclosed amendment inside the survey (no disposition changed; fraction table was already correct) |
| M-3 | "9 rules" wrong-count pointer | D1 enumerates rules by ID + source doc (= C-MINOR-1) |
| m-1 | P1 plural | FIXED wording (= C-MINOR-2) |
| m-2 | review-record regimes conflated | D1's pillar table names each record file + regime (post-result 3-cold-review vs pre-registered-bar + in-doc audit vs dedicated reconciliation) — P4 cites REVIEW-SURVEY-RECONCILIATION.md |
| m-3 | table-rule mis-attribution | FIXED: never-a-win-ratio = B-MAJOR-2/C-m9; C-m4 = generalization axes |
| m-4 | Rider-2 §2 rule is protocol not data | Claim (iii) cites it as PRE-REGISTERED PROTOCOL, "no breadth measurement executed"; protocol doc added to the rule collection |
| m-5 | 3 missing standing rules | Added to D1's rule collection: Rider-1 claim-eligibility (joinMode=correlator ∧ correlatorUnique, machine-enforced); G2 scoping rule ("injected wins are calibration evidence only"); TT effect-localization qualifier ("effect-localization, not fault/component localization") |
| m-6 | transcript-retention-note | Added to D1 threats roll-up (TT blind-set provenance = process-level attestation; SS = byte-identity verified) |
| m-7 | "analytical" adjective on 86.25% | Carried inside claim (ii)'s wording (the empirical version was rejected) |
| Completeness | — | CONFIRMED: six-pillar cut right + complete; exclusion list accurate; raw logs tracked at HEAD |

## Dispositions — Review C (paper-readiness)
| # | Finding | Disposition |
|---|---|---|
| BLOCKING-1 | positioning: pack = Gate-3 leg, not the paper floor | D1 carries the "Position in the plan-v4 ladder" paragraph + the extended deferred ledger (C2 at citable scale; C3 adjudicated prevalence; Gate-4/E1–E2 breadth incl. the E2 trace-comparator list superseded-note; §8.5 binding commitments) + C's positioning sentence verbatim ("…remains 'credible, not yet clear' until they exist"); claims introduced as "the three claims THIS PACK supports" |
| MAJOR-1 | claim (i) unbounded | C's bounded claim-1 text ADOPTED (fault provenance: real defects in unmodified source/images, triggers synthetic/operational + disclosed; "no test-specific instrumentation" NOT "instrumentation-free"; observation = public REST + standard operational surfaces; oracle scope = durable-sink read-back; "finds" → detects-when-exercised) |
| MAJOR-2 | claim (ii) composition | C's bounded claim-2 text ADOPTED (residue-census first; both conventions + "generous = adversarial-to-MIST"; fraction scoped to the 80-entry frozen TT CRUD surface; "analytical" adjective per B-m7; fairness chain added to the evidence map: P2 calibration floor, TT agreement anchor, SS dual-form, entity-absent honest-boundary note) |
| MAJOR-3 | claim (iii) protocol-as-data / stats / cost / pooling | C's bounded claim-3 text ADOPTED (per-SUT denominator semantics: TT 30×71 one triple exercising the quiescence gate + the FP-vs-timeout curve as a pack figure; SS 30×40 two endpoints first-poll-present = gate NOT stressed; record-level rule-of-three ≤0.14%/≤0.25% labeled correlated; "[0,0] descriptive, not a CI"; no async-FP claim; "cost" → measured budgets: matched 10 s/500 ms + 20 s caps, ~24 s/TT cell, ~43 min/1200-record probe) |
| MAJOR-4 | table structure | TWO tables + shared legend ADOPTED: Table 1 phenomena (rows = instances; row-role column: exactly 2 `headline: clean win` rows, `tie: diagnosis gap`, `agreement anchor`, `forced methodological control`, `specificity control`; comparator-form = row attribute; provenance column; TT-no-dual-form footnote) + Table 2 specificity/FP (separate; per-SUT semantics) |
| MAJOR-5 | D2 separate artifact | ADOPTED: `g3-evidence-pack.md` self-contained + liftable, cited by D1, reviewed in the SAME 3-cold-review wave |
| MINOR-1..4 | rules count / P1 plural / localization+generalization / §5 answers | All adopted (localization = named SECONDARY row on the tie cells, "effect-not-fault" verbatim; complementarity sentence under claim (ii); generalization = framing sentence inside claim (i); both gate sentences carried verbatim; no future trace tools named in claim text) |

## The reconciled verdict (D1 headline)
A's sentence, verbatim, closed with C's positioning sentence — i.e.:
**"Gate 3 is NOT MET as originally written — and is closed as MET-UNDER-DISCLOSED-RE-SCOPE, routing to
Plan-B-plus"** … [A's full sentence] … **"This pack closes the Gate-3/capability leg under the G2-v2
re-scoped comparator protocol; the committed primary A-path deliverables — the C2 open labeled
benchmark at citable scale and the C3 adjudicated prevalence study — remain unbuilt, and per plan-v4
§9 the empirical-track claim stays 'credible, not yet clear' until they exist."**

## Execution (per plan §4, as reconciled)
1. ✔ pre-D1 source hygiene (TT header; survey remnants — both committed with this reconciliation).
2. D1 = `debug/a-main/g3-result.md` (verdict + per-instance adjudication + decisive-result-pin
   adjudication + pillar table w/ regimes + rule enumeration by ID + deviations ledger + extended
   deferred ledger + threats roll-up). D2 = `debug/a-main/g3-evidence-pack.md` (3 bounded claims as
   adopted + Table 1 + Table 2 + claim→{cell, log, review, rule} map + figures list).
3. D1+D2 → ONE 3-cold-review wave → reconcile → G3 arc closed; then surface the direction decision
   (paper writing vs C2/C3 build-out vs remaining β) to the user with the verdict in hand.
