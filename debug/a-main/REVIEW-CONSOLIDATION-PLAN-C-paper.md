# Cold review C — G3 consolidation plan @ 24b7fa9, lens: §3-D2 paper-readiness

**Verdict: ACCEPT-WITH-CHANGES.** The three-claim cut is fundamentally right (it is the v3 reviewers'
"path to Accept": defects an assertion oracle misses because no human wrote the assertion), but the
pack currently masquerades as *the paper's* evidence base when it is the evidence base for the
Gate-3/capability LEG of a paper whose committed floor (C2 benchmark at scale + C3 prevalence) is
entirely unbuilt — and two of the three claims drop bounds their own pillar docs state. All fixes are
wording/structure; no new experiments.

## BLOCKING-1 — Positioning: the pack answers Gate 3, not the primary A-path
README's committed primary A-path (§3, §9) = C2 (open labeled benchmark at citable scale) + C3
(adjudicated defect-prevalence) + C1 (capability with measured FP); §9 option 2 makes the
empirical-track accept *"'clear' conditional on C2 released at the benchmark §8 scale + C3 executed."*
The three claims map onto C1 + the §6 comparator demand = the Gate-3 leg. The D1 deferred ledger
("executable breadth; trace comparator; β extras") omits the paper's own committed floor:
- C2 at citable scale (§3: "the seed of 4 cases is a start, not the deliverable"; RCAEval-735; E6 release);
- C3 (stratum-3 adjudicated wild sample, ≥2 blind raters, κ, pre-registered rubric; B4 harness; E4 CIs);
- Gate 4 / E1–E2 breadth (≥4 baselines × ≥6 SUTs; the E2 trace-aware comparator list — superseded by
  the G2-v2 re-scope, which the ledger should SAY rather than silently drop);
- the §8.5 binding commitments (underspecified-case protocol, per-SUT async-coverage disclosure,
  depth-not-count pre-specification, replay-coverage "argued-not-measured" framing).
**Fix:** add a "Position in the plan-v4 ladder" paragraph — *"This pack closes the Gate-3/capability-
differentiation leg (C1 + the §6 comparator demand). It does not deliver C2 or C3; per README §9 the
empirical-track claim remains 'credible, not yet clear' until those exist."* Extend the ledger with the
four bullets. Introduce the claims as "the three claims THIS PACK supports," not the paper's final set.

## MAJOR-1 — Claim (i) unbounded on fault provenance + "black-box"
(a) Every positive is injected or operationally triggered (G1 = SUT-side LOST_WRITE flag; TT natural =
fork runtime toggle activating a real in-source defect; TT constructed = disclosed fabricated-ack; SS =
operational mesh/broker policy on an unmodified image). Nothing observed untriggered in the wild; no
developer-confirmed wild bug (README §1 constraint 4 + §6 "bug story"). Bound the claim: *"on injected
or operationally-triggered faults — the defects are real in unmodified source/images, the triggers are
synthetic and disclosed."* Also: §2 never adjudicates the gate verb **"B2 finds"** — both defect sites
were HUMAN-located (TT source survey; SS wild-hunt), then detected under a scripted stimulus. The
verdict must say "detects at protocol-selected sites," not autonomous discovery.
(b) "End-to-end black-box": SS read-back = broker-admin mgmt API + qm→0 rider; TT activation = fork
endpoint. Reuse the vetted C1 phrase — *"no test-specific instrumentation," explicitly NOT
"instrumentation-free"* (§3, R1 MAJOR 1); observation scoped to "public REST + standard operational
surfaces (broker management API)"; fault activation disclosed grey-box. Oracle scope per §4.6: writes
with a black-box read-back observable on a durable sink, not "any write."

## MAJOR-2 — Claim (ii): lead with residue-is-the-class or invite the "86% ⇒ marginal" misreading
The survey's own §Reading has the armor the D2 one-liner drops: (1) the 11/80 residue is STRUCTURAL —
value transitions (incl. orderPay #18), server-keyed reads, non-flat keys, nested wrappers, batch,
object-absence — the same primitive gaps the depth cells exercise; (2) the payment/compensation surface
(ts-inside-payment, ts-cancel, ts-preserve, ts-rebook, ts-payment, ts-seat) sits ENTIRELY OUTSIDE the
surveyed CRUD denominator — 86.25% says nothing about the deep-flow class; (3) the one deep flow
examined end-to-end had ALL THREE frozen state clauses NOT_CHECKABLE. Also: D2 quotes only generous
86.25% while P4's row carries strict 59/80 = 73.75% — internal inconsistency reading as cherry-picking
(generous is the pro-comparator choice; SAY that). Reword: lead with the residue census; carry both
conventions + the "generous = adversarial-to-MIST" note; scope the fraction to "the full frozen TT
blind set (80 state-clause entries, one SUT's CRUD surface)"; define "the clean-loss class" as the NC
census categories; label the two evidence types (empirical existence N=5 ×2 SUTs; analytical
expressibility). Add the fairness chain to the mapping (P2 calibration competence floor; TT agreement
anchor; SS dual-form; the survey's entity-absent honest-boundary note) — the anti-strawman load-bearers.

## MAJOR-3 — Claim (iii): protocol cited as data; missing stats caveat; "cost" empty; two FP zeros ≠ same machinery
(a) The comparator infra-failure-rate rule (Rider-2 protocol §2, C13) is a PRE-REGISTERED REPORTING
RULE; no rate was ever measured (its breadth run was rejected). Move to methodology, marked
"pre-registered; no breadth measurement executed."
(b) Stats: P5 says verbatim `[0,0]` is "a descriptive observed interval, not a CI" and the 1200 records
are CORRELATED (30×40 on 2 endpoints); G1 analog (30×71 on one triple). If an upper bound is quoted:
rule-of-three at record level (≤3/2127 ≈ 0.14% TT; ≤3/1200 = 0.25% SS) labeled
record-level-and-correlated; a naive n=1200 CI is what P5's threats forbid.
(c) Scope: TT G1 exercised the QUIESCENCE GATE against a real 1–2 s window (+ the FP-vs-timeout curve
12.98%@500ms → 0@≥2s — paper-figure material, include it); SS-B was first-poll-present → validates HAL
parsing + membership + cookie auth, NOT the gate (P5 threats, bold). Both sync-only; no async-FP claim.
Do not pool as "FP 0.0 on two SUTs" simpliciter.
(d) "Cost" has no datum: drop the word or supply measured budgets (10 s/500 ms + 20 s caps, ~24 s/TT
cell, ~43 min/1200-record probe, G1 wall-clock).

## MAJOR-4 — Two tables + shared legend; row-role column; comparator-form as row attribute, never an axis
Cells are not isomorphic (TT: 3 cells incl. agreement, one contract form; SS: 2 strata × 2 forms +
benign, as-frozen rows = analytically forced controls). A symmetric TT×SS grid invents empty cells or
lets a reader sum wins. **Table 1 — phenomena:** rows = phenomenon-instances; columns = SUT ·
write/hazard/sink · defect+trigger provenance (in-source defect + injected trigger / disclosed fork
flag / unmodified-image defect + operational trigger) · MIST verdict (N) · comparator form of record
(row attribute) · comparator verdict · **row role** · evidence pointer {doc, log, review record}. Row
roles: `headline: clean win` (TT-constructed, SS-constructed) / `tie: diagnosis gap` (TT-natural,
SS-natural) / `agreement anchor` (TT-createAccount) / `forced methodological control` (2 SS as-frozen
rows) / `specificity control` (SS benign). Exactly two rows labeled headline = mechanical "never a
win-ratio" enforcement. Footnote WHY TT has no dual-form row (TT's response carries a bindable
envelope; SS's bare 201 gave the blind author only HTTP_STATUS — itself a bindability datum).
**Table 2 — specificity/FP**, separate: G1 0/2127 (30×71, one triple, correlated; gate 100%; the
FP-vs-timeout curve) + SS-B 0/1200 (30×40, two endpoints; first-poll-present — gate not stressed).

## MAJOR-5 — D2 = separate liftable artifact (`g3-evidence-pack.md`), same review wave as D1
D1 (gate verdict) should be immutable post-review like gate1-result.md; the evidence pack is a living
doc for the paper team. Self-contained, no cross-references into §2 adjudication; cited by D1; both
reviewed in the SAME 3-cold-review wave.

## MINORs
1. "the 9 rules in the RESULT OF RECORD §Framing" miscount/mislocation: RESULT OF RECORD §Framing has
   4 bullets; the rest live in REVIEW-SHIPPING-HARNESS-RECONCILIATION.md (Standing rules 1–3 +
   B-MAJOR-1/2 + tie-FRAMING). Enumerate rules BY ID WITH SOURCE DOC (A-m2 durable-sink-not-arithmetic
   lives only in the reconciliation and binds the SS clean-win wording).
2. P1 plural overstates: G1 automated run = 1/1 evaluable constructed site (adminroute); contacts =
   manual G0 smoke only (gate1-result.md §3.4). Say "one constructed site, strong stratum, 1/1
   evaluable; second site manual-smoke only."
3. Localization folded under (i) as secondary = correct (B-MINOR-4 + "effect localization, not fault
   localization" + §1.5 service-level ceiling; avoids the RCA literature). Named secondary row in the
   claim→evidence map (both tie cells); complementarity sentence (MIST NO_FIREs on loud status:0
   failures the comparator catches — TT results "not a strict superset") = scope sentence under (ii).
   Generalization axes = one framing sentence inside (i); N=2 existence cannot carry a standalone claim.
4. §5 draft answers: carry both gate sentences verbatim ("NOT met as written; met under the re-scoped
   protocol") — right, and claim (ii) needs it adjacently. The no-errored-span argument is safe as
   labeled DISCLOSURE given the ledger names the trace comparator; do NOT name specific future trace
   tools in claim text (invites "then run it").

## Recommended final claim set (intro-ready, bounded — use as the D2 drafting base)
1. **Capability (C1):** On two independently-built OSS microservice systems and two integrity-hazard
   classes (synchronous DB compensation on TrainTicket; asynchronous MQ enqueue on Sock Shop), MIST's
   differential read-back oracle — no test-specific instrumentation, no hand-written assertions,
   observation via public REST plus standard operational surfaces — detected every acknowledged-but-lost
   write in the protocol (N=5 deterministic per cell) across three read-back modes (membership,
   arithmetic balance delta, queue-count delta), where the defects are real in unmodified source/images
   and the triggers are injected or operational and fully disclosed; MIST's added value on the tie cells
   is per-write effect localization, secondary to detection.
2. **Comparator boundary (§6 demand):** A blind-authored, calibration-verified response(+liveness)
   contract checker — the strongest form its pre-registered primitive class affords, strengthened
   in-class where its author specified — catches these losses wherever its primitives bind (agreement
   and tie cells) but structurally cannot see losses whose only observable is a state delta or
   transition (both constructed clean-win cells); analytically, 86.25% (generous; 73.75% strict) of the
   same SUT's 80 frozen state clauses bind, and the unbindable residue is exactly the
   delta/transition/object-shaped class the oracle covers — with the deep payment/compensation surface
   lying outside the surveyed CRUD set altogether (0/3 state clauses checkable on the one deep flow
   examined).
3. **Specificity (C1's measured-FP requirement):** On benign workloads the oracle produced zero false
   positives over 2,127 (TrainTicket, observation gate 100% resolved, with a measured FP-vs-timeout
   curve justifying the pre-registered cap) and 1,200 (Sock Shop) acknowledged synchronous writes —
   correlated-record denominators reported per SUT, descriptive zeros with a record-level rule-of-three
   bound ≤0.14%/≤0.25%, no async-FP claim — plus a live benign control on the queue-depth oracle itself.

**Positioning sentence the verdict doc must carry:** This pack closes the Gate-3/capability leg under
the G2-v2 re-scoped comparator protocol; the committed primary A-path deliverables — the C2 open
labeled benchmark at citable scale and the C3 adjudicated prevalence study — remain unbuilt, and per
plan-v4 §9 the empirical-track claim stays "credible, not yet clear" until they exist.
