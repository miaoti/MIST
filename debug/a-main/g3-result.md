# Gate-3 verdict — G3 arc consolidation (RESULT OF RECORD once its review wave closes)

**What this is.** The Gate-3 gate verdict, mirroring `prep/gate1-result.md`'s role: the adjudication of
the plan-v4 gate sentence against the six reviewer-accepted evidence pillars, with every disclosure the
consolidation-plan reviews mandated. Authored per `g3-consolidation-plan.md` @ 24b7fa9 **as reconciled**
by `REVIEW-CONSOLIDATION-PLAN-RECONCILIATION.md` (3× ACCEPT-WITH-CHANGES; the reconciliation, not the
plan, is authoritative for this doc's structure). The liftable paper-evidence pack is the sibling
`g3-evidence-pack.md` (D2). Status: **D1+D2 review wave — round 1 (usability) folded @ 26a8c97; rounds 2–3 (numbers audit;
fidelity) dispositioned in `REVIEW-G3-DELIVERABLES-RECONCILIATION.md`.**

## 1. The verdict

> **Gate 3 is NOT MET as originally written — and is closed as MET-UNDER-DISCLOSED-RE-SCOPE, routing to
> Plan-B-plus:** no single real, non-injected defect was missed by both oracle legs (the natural
> instances are detection ties under the strengthened comparator; the both-miss instances are disclosed
> constructions — a fork fabricated-ack on TT, an injected broker policy on an unmodified image on SS —
> and the trace-style oracle leg was never executed: its Cast half was reviewed out pre-run at G2, its
> Tracetest half is addressed analytically only, as a threat-to-validity, not as evidence), so the
> pre-registered decisive-result bar (G2 prereg §2: wild defect, frozen-set no-flag, rater-adjudicated)
> remains unmet; what the evidence does establish, under the G2-v2 comparator re-scope now flagged as a
> disclosed amendment to the gate criterion, is the gate's capability core in its defensible form —
> MIST **detects, end-to-end black-box, when the defect is exercised** (it discovered nothing in the
> wild), acked-but-lost writes on two independently-built SUTs, two hazard classes, and two durable-sink
> types, including a delta/transition/object-shaped observable class (11/80 structurally non-bindable
> in the frozen set) that the strongest fair blind-authored response(+liveness) contract oracle
> structurally cannot express, at measured FP 0 on both SUTs' benign paths — which clears and exceeds
> the README §9 Plan-B evidence floor without discharging Plan A's "Gate 3 yields real bugs" trigger.

(One-phrase disclosed deviation from review A's verbatim sentence: "a delta/aggregate observable
class" → "a delta/transition/object-shaped observable class", per the D1+D2 usability review M2 — the
census language of the accepted survey; A's "structurally cannot express" framing is unchanged.)

**Position in the plan-v4 ladder.** This pack closes the Gate-3/capability leg under the G2-v2
re-scoped comparator protocol; the committed primary A-path deliverables — the C2 open labeled
benchmark at citable scale and the C3 adjudicated prevalence study — remain unbuilt, and per plan-v4 §9
the empirical-track claim stays **"credible, not yet clear"** until they exist.

## 2. The gate sentence, and the two pre-registered pins it must be read with

Gate 3 as written (`README.md` §decision-gates): *"B2 finds ≥1 real acknowledged-but-lost-write /
missing-compensation defect on a real SUT that a status/schema oracle AND a hand-asserted
Tracetest/Cast-style oracle miss, reproduced across ≥2 SUTs."*

Decisive-result pin (G2 prereg `prep/g2-novelty-comparator-prereg.md` §2, pre-registered): *"The
PC-moving result is defined ONLY over real (non-injected) defects at G3: a wild
acknowledged-but-lost-write / missing-compensation defect that (a) MIST FIREs on, (b) the frozen blind
assertion set does not flag, (c) ≥2 blind raters categorize as no-assertion-existed."*
**Adjudication: UNMET.** Both both-miss instances are injected/constructed; SS-natural's frozen-set
miss is specified-but-not-bindable (the blind author DID specify the liveness clause; the closed
primitive set could not express it — that is the primitive-vocabulary boundary, not "no assertion
existed") and the in-class-strengthened form catches it; no ≥2-rater κ adjudication was run.

Comparator-class pin (prereg reconciliation item 3, reviewer-accepted pre-run): *"Comparator =
Filibuster-approximating, Cast-pattern OUT."* **Scope honestly stated:** that decision fixed which
comparator to BUILD for Gate 2 (its rationale — no production traffic/baselines → a nominal Cast
replica invites the crippled-comparator charge — covers the Cast half only). "Tracetest" appears
nowhere in the reviewed prereg record. **Therefore: adjudicating the GATE's second-oracle leg against
the re-scoped class is a disclosed amendment made NOW, at consolidation** (per the reconciliation's own
standing rule that material changes are disclosed amendments), not an inherited reviewer decision.

## 3. Per-instance adjudication (the gate binds all conditions to ONE instance)

Column definition: "status/schema oracle misses?" is answered AS EXECUTED — the frozen contract's
envelope gates (a bare status-code/schema tool would additionally miss TT-natural's HTTP-200
schema-valid `{1,"error"}`; the executed msg gate is a VALUE clause — the filling errs against MIST).

| instance | real, non-injected? | status/schema oracle misses? | strongest executed comparator misses? | trace-style oracle |
|---|---|---|---|---|
| TT natural (drawback throws → acked `{1,"error"}`; refund lost) | defect + response REAL on the unmodified fork; the THROW is injected (runtime fault toggle: drawBack throws → HTTP 500; an EnvoyFilter/Istio abort was tried and REJECTED — pooled-connection race, P3 §Fault mechanism) | NO — the envelope msg gate flags `{1,"error"}` → detection TIE (MIST adds effect-localization) | NO (CAUGHT) | not executed |
| TT constructed (fabricated-ack → clean `{1,"Success."}`; refund lost) | NO — disclosed fork flag; the clean-ack+lost path is DEAD CODE on the unmodified fork (drawBack's `{0}` return unreachable) | YES — analytically forced and confirmed live (envelope schema-valid, status 1) | YES (**clean miss**) | not executed |
| SS natural (Istio DENY 5672 + connection close; enqueue lost; `/health` err) | defect REAL in the unmodified upstream image; the sever is operator-injected | as-frozen form: YES — analytically forced (bare 201) and confirmed live (2/2 + 3/3 post-reboot) | NO — the P2-strengthened form CATCHES the outage (diagnosis-gap tie; the blind author specified the liveness clause; P2 made it expressible) | not executed |
| SS constructed (reject-publish policy; enqueue lost; `/health` green) | defect REAL in the unmodified image; the policy is injected (operational only — no source change anywhere) | YES — analytically forced + confirmed live | YES (**clean miss**, 5/5) | not executed |
| TT agreement (body-carrying createAccount + fabricated-ack) | NO (fork flag, runtime toggle) | analytically misses (schema-valid clean ack); fairness anchor, outside the gate conjunction | NO (CAUGHT via bound STATE_GET) — the fairness anchor | not executed |
| SS benign (no fault) | — (control) | no flag (nothing to catch) | no flag | — |

**Conjunction outcome (one sentence): no single real, non-injected instance was missed by both executed
oracle legs.** The clean misses are constructions (disclosed fork flag on TT; injected operational
policy on an unmodified image on SS); the natural instances are detection ties in which MIST's added
value is per-write **effect**-localization (not fault/component localization — TT round-1 rule), a
modest refinement under SS's broker-wide outage (SS review B-MINOR-4).

Leg-by-leg readings derived from the table: the **detection capability** reproduces across 2 SUTs / 2
hazard classes / 2 sink types; the **both-oracle-miss demonstration** reproduces only in its
constructed form. The verb **"finds"** is adjudicated as: both defect sites were HUMAN-located
(the TT source survey; the SS wild-hunt — whose own plan §0 forbids a discovery claim since the
developer flagged the swallow in a log line); MIST **detects when the defect is exercised**; no
discovery-in-the-wild is claimed. The trace-style leg is **not met as written** (never executed; see
§2 for the re-scope disclosure and §7 for the analytical threat note).

## 4. Evidence pillars (numbers verified against sources by the plan-review inventory audit)

| # | pillar | headline numbers | review record + regime |
|---|---|---|---|
| P1 | Gate-1 verdict (`prep/gate1-result.md`) | FIRE on ONE constructed site (adminroute), strong stratum, **1/1 evaluable** (second triple = manual G0 smoke only); sync FP **0/2127** (30 iter × 71 records − 3 invalid, ONE triple, correlated — descriptive interval [0,0], not a CI), observation gate 100% resolved; FP-vs-timeout curve 12.98%@500 ms → 0@≥2 s; async disclaimed | mechanism 3-cold-reviewed PRE-run (`research/REVIEW-B1B2-RECONCILIATION.md`); result audited in-doc against the pre-registered §2 checklist (no post-result wave) |
| P2 | Gate-2 calibration (`g2-comparator/calibration-result.md`) | both Gate-1 faults flagged via genuine STATE-clause failures; all-clean control legs; competence floor MET. Standing scope rule: "injected wins are calibration evidence only — the PC-moving comparison happens at G3 over real defects" | chain reviews in separate files (blind contract, bindings, runner); result self-adjudicated against the pre-registered §4 bar |
| P3 | TT cancel→refund head-to-head (`g3-comparator-tt/g3-headtohead-results.md`) | 3 cells, N=5 each: natural FIRE+CAUGHT (tie), constructed FIRE+MISSED (clean win via pre-funded arithmetic balance delta), agreement FIRE+CAUGHT | 2 rounds × 3 cold reviewers, ACCEPTED (`REVIEW-HEADTOHEAD-RECONCILIATION.md`); header staleness fixed 2026-07-08 |
| P4 | Rider-2 bindability survey (`g3-comparator-tt/rider2-bindability-survey.md`) | ANALYTICAL expressibility over the full frozen TT set: generous **69/80 = 86.25%** bind (adversarial-to-MIST convention) / strict 59/80 = 73.75%; **11 structural NC** (3 OBJECT-ABSENCE, 3 KEY-SHAPE, 2 NESTED-ITEM-SHAPE, TRANSITION, RESPONSE-KEYED, BATCH); payment/compensation surface OUTSIDE the surveyed CRUD denominator; the one deep flow examined = 0/3 state clauses checkable | dedicated record `REVIEW-SURVEY-RECONCILIATION.md` (3× ACCEPT-WITH-FIXES, all folded); two prose remnants corrected 2026-07-08 (disclosed amendment; no disposition changed). The EMPIRICAL breadth run was REJECTED (`REVIEW-BINDABILITY-RUNNER-RECONCILIATION.md`) — this fraction is analytical, by design |
| P5 | SUT-2 benign FP probe (`prep/g3-sut2-fp-probe-result.md`) | **0/1200** acked benign writes (30 iter × 40 shapes, TWO endpoints, correlated; [0,0] descriptive, not a CI), gate 100% (every record first-poll-present, 9–38 ms → the quiescence gate NOT stressed here; it was stressed at P1); HAL/_embedded + cookie-auth read-back path validated; ~43 min wall-clock | `prep/REVIEW-SUT2-FP-RECONCILIATION.md` (3×, fixes folded) |
| P6 | SUT-2 shipping head-to-head (`g3-comparator-ss/g3-shipping-headtohead-results.md`, RESULT OF RECORD) | 2 strata × 2 comparator forms + benign: P2-amended natural FIRE+CAUGHT 5/5 (diagnosis gap), P2-amended constructed FIRE+MISSED 5/5 (clean win), as-frozen rows = analytically-forced CONTROLS (5/5 each, incl. 3 post-reboot reps on fresh broker state), benign NO_FIRE; count-delta on a durable sink (NOT arithmetic); ground-truth corroboration (rabbitmqctl direct vs mgmt, ~5 s lag measured); fault-corroborated absence (TIMEOUT_ABSENT; no traceId wired) | 3 waves × 3 reviewers (harness, P2 primitive, result) — `g3-comparator-ss/REVIEW-SHIPPING-HARNESS-RECONCILIATION.md`; raw logs `g3-comparator-ss/runs/` (git-tracked) |

## 5. Standing rules collected (by ID, with source — binding for any use of this evidence)

From P3 (`g3-comparator-tt/REVIEW-HEADTOHEAD-RECONCILIATION.md`): **R-TT-1** oracle-class scope
adjacent to any clean-win claim; **R-TT-2** Rider-2 = the external-validity answer; **R-TT-3**
"effect-localization, not fault/component localization"; **R-TT-4** complementarity — "not a strict
superset" (MIST NO_FIREs on loud `status:0` failures the comparator catches).
From Rider-1 (`research/REVIEW-RIDER1-RECONCILIATION.md`): **R-R1** tallies feed claims only when
joinMode=correlator ∧ correlatorUnique (machine-enforced; printed per cell in both head-to-heads).
From P2 (`g2-comparator/calibration-result.md`): **R-G2** injected wins = calibration evidence only.
From P6 RESULT OF RECORD §Framing: **R-SS-1** natural = diagnosis gap, tie at binary granularity,
localization modest under a broker-wide outage; **R-SS-2** class-scope the constructed win
(Pact/Dredd/synthetic-monitoring shape; "add queue-depth monitoring" concedes the thesis); **R-SS-3**
protocol-not-personhood (freeze-before-reveal); **R-SS-4** generalization axes sentence.
From P6 doc, other sections: **R-SS-5** scope-pivot disclosure (§Scope provenance; B-MAJOR-3/C-M1);
**R-SS-6** never win-ratio optics — 2 result cells + controls (§How-to-read; B-MAJOR-2/C-m9); **R-SS-7**
count-delta/durable-sink, no arithmetic re-claim (C-M2; also reconciliation standing rule A-m2);
**R-SS-8** qm→0 observability rider (B-MINOR-7); **R-SS-9** fault-corroborated absence, never
trace-corroborated language (A-M1).
From the Rider-2 protocol (`prep/g3-rider2-comparator-protocol.md` §2): **R-R2** report the
comparator's per-SUT infra-failure RATE alongside MIST's FP rate — **a pre-registered reporting rule;
no breadth measurement was executed** (the run that would have produced a rate was rejected); cite as
protocol, never as a measured comparator-cost number.

## 6. Deviations ledger (G2-prereg pinned outputs vs what was produced)

| pinned | produced | defensibility |
|---|---|---|
| symmetric miss tables adjudicated by ≥2 blind raters + Cohen's κ | author-adjudicated cells, then 3-cold-review waves per result | outcomes are deterministic and categorical (FIRE/flag booleans, N=5 zero-variance); κ protocol was designed for ambiguous wild defects — none exist in the executed set; disclosed, not repaired |
| operating-point 2×2 tables (MIST-strict primary / MIST-all / comparator full-set) | per-cell verdicts at the pre-registered operating point only | the focused harnesses run 1–2 endpoints; the full-set operating grid belongs to the (unbuilt) C2 benchmark |
| ≥10 seeds + MWU/Â₁₂ for generation-driven runs | N=5 deterministic reps per cell (SS 14/14 pre- + 6/6 post-reboot fault legs; TT 5/5 × 3 cells; zero variance) | the head-to-heads are scripted-stimulus, not generation-driven; determinism is structural (mechanism re-observed), not statistical — stated in both RESULTs OF RECORD |
| decisive-result definition (wild, frozen-set no-flag, rater-adjudicated) | **UNMET** (§2) | reported as unmet; the verdict routes to Plan-B-plus instead of claiming the pin |

## 7. Threats-to-validity roll-up (pack-level; per-pillar threats live in the pillar docs)

- **Fault provenance:** every positive is injected or operationally triggered; defects are real in
  unmodified source/images, triggers are synthetic and disclosed (TT constructed additionally needs a
  fork flag; its natural counterpart is source-real but its clean-ack variant is dead code).
- **Trace-style oracle (not executed):** for the constructed cells, no ERRORED span exists — SS's
  reject-publish drops broker-side after a protocol-successful publish (no confirms), TT's
  fabricated-ack returns a normal 2xx tree — so error-STATUS trace assertions would pass; **but** a
  hand-asserted downstream-span-PRESENCE assertion (consumer span on SS; a DB-write span from a stock
  OTel agent on TT) is the trace-class analogue of MIST's read-back and could catch both constructed
  cells in an instrumented deployment. The executed deployments were traceless on the target paths (TT
  sidecar-free cancel; SS no traceId) — partly a deployment choice; this is why the leg is adjudicated
  NOT met as written rather than argued away. ANALYTICAL DISCLOSURE ONLY, not evidence.
- **Statistics:** both FP zeros are descriptive intervals over CORRELATED record sets (P1 30×71 one
  triple; P5 30×40 two endpoints); record-level rule-of-three upper bounds ≤3/2127 ≈ 0.14% (TT) and
  ≤3/1200 = 0.25% (SS), labeled record-level-and-correlated; no async-FP claim anywhere (P1 disclaims
  async; P5 is sync-only; P6's benign stratum is a small-N specificity control, not an FP rate).
- **Provenance asymmetry of the blind sets:** the SS as-frozen contract is byte-identity-verified
  against its freeze commit; the TT set's "transcript-audited" claim was weakened to a process-level
  attestation (`g2-comparator/transcript-retention-note.md`).
- **Denominator scope of the 86.25%:** analytical expressibility over ONE SUT's 80-entry frozen CRUD
  surface; the deep payment/compensation services sit outside it (0/3 state clauses checkable on the
  one deep flow examined). Never quote the fraction as a measured breadth run (that run was rejected).
- **Measured budgets (the honest "cost" content):** matched read-back budgets 10 s/500 ms (TT
  comparator retry cap; A3) and 20 s oracle cap (SS, 4× the measured ~5 s mgmt stats lag); ~24 s per TT
  cell leg-pair; ~43 min for the 1200-record SUT-2 probe; Gate-1 automated run wall-clock per its
  result doc.

## 8. Deferred-item ledger (nothing here is quietly dropped)

- **C2 open labeled benchmark at citable scale** — the primary-path floor; the current 4-case seed "is
  a start, not the deliverable" (README §3). UNBUILT.
- **C3 adjudicated defect-prevalence study** (stratum-3 wild sample, ≥2 blind raters, κ, pre-registered
  rubric; B4 harness; E4 CIs). UNBUILT.
- **Gate-4 / E1–E2 breadth** (≥4 baselines × ≥6 SUTs; the E2 trace-aware comparator list —
  **superseded in part by the G2-v2 re-scope for the Gate-3 leg, NOT deleted**: trace-aware baselines
  remain the E2 plan for the full eval). NOT STARTED.
- **§8.5 binding writing-time commitments** (underspecified-case protocol; per-SUT async-coverage
  disclosure; depth-not-count pre-specification; replay-coverage "argued-not-measured"). OPEN.
- **Executable breadth run** — REJECTED as LOW-ROI by 3-cold-review; the analytical survey stands. Do
  not resurrect without a new decision.
- **Trace-style comparator execution** — never run (see §7); a future instrumented-deployment arc.
- **SUT-2 β extras** (front-end tracing, MstAuthHandler cookie wiring beyond the FP probe's scope,
  further wild-hunt) — secondary.

## 9. What follows this verdict

D2 (`g3-evidence-pack.md`) carries the three bounded claims this pack supports, the two tables, and
the claim→evidence map. After the D1+D2 review wave closes, the direction decision (paper writing on
the Plan-B-plus footing vs building C2/C3 vs remaining β) goes to the user with this verdict in hand.
