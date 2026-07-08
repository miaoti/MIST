# G3 paper-evidence pack (D2 — liftable, self-contained)

**What this is.** The paper-facing evidence pack for the Gate-3/capability leg: the THREE claims this
pack supports (bounded exactly as the consolidation-plan reviews adopted), the two tables, and the
claim→evidence map. Companion to the gate verdict `g3-result.md` (which carries the adjudication,
deviations, threats, and deferred ledgers — not repeated here).

**⚠ SCOPE FENCE: these are the claims THIS PACK supports — NOT the paper's final headline set. Per
plan-v4 §9 the empirical-track claim stays "credible, not yet clear" until the C2 benchmark and C3
prevalence study exist. Gate status per the verdict (`g3-result.md`): Gate 3 is NOT met as written;
it is closed under the disclosed re-scope, routing to Plan-B-plus.**

**Pillar key:** P1 = `prep/gate1-result.md` · P2 = `g2-comparator/calibration-result.md` · P3 =
`g3-comparator-tt/g3-headtohead-results.md` · P4 = `g3-comparator-tt/rider2-bindability-survey.md` ·
P5 = `prep/g3-sut2-fp-probe-result.md` · P6 = `g3-comparator-ss/g3-shipping-headtohead-results.md`.
**Artifact availability:** all paths relative to `debug/a-main/` in github.com/miaoti/MIST, branch
`main_track` (evidence tracked at HEAD; pack authored @ 10efbe1, round-1 fixes folded @ 26a8c97,
rounds 2–3 per `REVIEW-G3-DELIVERABLES-RECONCILIATION.md`); contract freezes: TT
`38e7aa6`, SS as-frozen byte-verified against `41ff9ac~1`; raw run logs git-tracked under
`g3-comparator-{tt,ss}/runs/` + `prep/gate1-run3-report.json` + `prep/g3-sut2-fp-probe-*`.
Status: **REVIEW WAVE CLOSED — 3 rounds, all ACCEPT-WITH-CHANGES, every disposition folded
(`REVIEW-G3-DELIVERABLES-RECONCILIATION.md` §WAVE CLOSED). This pack is lift-ready within its scope
fence.**

## The three claims (intro-ready, bounded)

**Claim 1 — Capability (C1).** On two independently-built OSS microservice systems and two
integrity-hazard classes (synchronous DB compensation on TrainTicket; asynchronous MQ enqueue on Sock
Shop), MIST's differential read-back oracle — no test-specific instrumentation, no hand-written
assertions, observation via public REST plus standard operational surfaces — detected every
acknowledged-but-lost write in the protocol (N=5 deterministic per cell) across three read-back modes
(membership, arithmetic balance delta, queue-count delta). Provenance, fully disclosed: every defect
ships in unmodified source or images and every trigger is injected or operational — except the two
TrainTicket fabricated-ack cells, whose clean-ack-and-lose behavior does not exist upstream (on the
cancel cell it is literally dead code) and runs behind a disclosed fork flag; no
discovery-in-the-wild is claimed, and MIST's added value on the tie cells is per-write **effect**
localization, secondary to detection.

**Claim 2 — Comparator boundary (the plan README §6 comparator demand).** A blind-authored,
calibration-verified response(+liveness) contract checker (the Pact/Dredd/synthetic-monitoring class —
rule R-SS-2) — the strongest form its pre-registered primitive class affords, strengthened in-class
where its author specified — catches these losses wherever its primitives bind (agreement and tie
cells) but structurally cannot see losses whose only observable is a state delta or transition (both
constructed clean-win cells); **analytically**, 86.25% (generous — the adversarial-to-MIST convention;
73.75% strict) of TrainTicket's 80 frozen state clauses bind, and the structurally unbindable residue
(11/80) is exactly the delta/transition/object-shaped primitive-gap class the depth cells exercise —
with the deep payment/compensation surface lying outside the surveyed CRUD set altogether (0/3 state
clauses checkable on the one deep flow examined). Scope sentences: the two oracles are complementary,
not a superset relation — MIST NO_FIREs on loud `status:0` failures the comparator catches; and the
sole in-class strengthening (the blind-author-specified P2 liveness primitive) moved the comparator's
SS natural-cell verdict MISSED→CAUGHT — strengthening only ever helped the comparator (dual-form:
Table 1, SS natural vs its as-frozen control row).

**Claim 3 — Specificity (C1's measured-FP requirement).** On benign workloads the oracle produced zero
false positives over 2,127 (TrainTicket — observation gate 100% resolved, with a measured
FP-vs-timeout curve justifying the pre-registered cap) and 1,200 (Sock Shop) acknowledged synchronous
writes — correlated-record denominators reported per SUT, descriptive zeros with a record-level
rule-of-three bound ≤0.14%/≤0.25%, no async-FP claim — plus a live benign control on the queue-depth
oracle itself (MIST NO_FIRE when both legs' enqueues land).

Secondary (never a headline): on both tie cells MIST adds per-write **effect** localization ("effect,
not fault/component" — TT rule R-TT-3; modest under SS's broker-wide outage — rule R-SS-1). The
generalization axes (2 systems / 2 hazard classes / 2 durable-sink types) are a framing sentence
inside Claim 1, not a standalone claim (N=2 existence).

## Table 1 — head-to-head phenomena (row roles enforce "never a win ratio": exactly TWO headline rows)

| instance | SUT · write · hazard · sink | defect + trigger provenance | MIST (N) | comparator form of record | comparator verdict | **row role** | evidence |
|---|---|---|---|---|---|---|---|
| TT natural | TT · bodyless cancel · sync DB compensation · account balance (arithmetic delta) | defect real in fork source (drawback failure path); trigger injected (runtime fault toggle: drawBack throws → HTTP 500; an EnvoyFilter/Istio abort was tried and REJECTED — pooled-connection race, P3 §Fault mechanism) → acked `{1,"error"}` | FIRE (5/5) | frozen envelope contract | **CAUGHT** (msg gate) | tie: diagnosis gap | P3 doc + `g3-comparator-tt/runs/` + R2×3 reviews |
| TT constructed | same | clean-ack+lost path is DEAD CODE on the unmodified fork → DISCLOSED fork fabricated-ack flag → `{1,"Success."}` | FIRE (5/5) | frozen envelope contract | **MISSED** | **headline: clean win** | same |
| TT agreement | TT · body-carrying createAccount · sync DB write · account membership (list) | disclosed fork fabricated-ack (runtime toggle) | FIRE (5/5) | frozen contract (STATE_GET binds) | **CAUGHT** | agreement anchor (fairness) | same |
| SS natural | SS · POST /shipping · async MQ enqueue · queue count (count-delta) | defect real in UNMODIFIED upstream image (201-on-enqueue-failure swallow); trigger operational (Istio DENY 5672 + connection close) → `/health` err | FIRE (5/5) | P2-amended (liveness bound per the blind author's spec) | **CAUGHT** (in-body liveness FAIL) | tie: diagnosis gap | P6 doc + `g3-comparator-ss/runs/` + 3 review waves |
| SS constructed | same | defect real in unmodified image; trigger operational (max-length:1/reject-publish — accelerant for the reject semantics; no source change anywhere) → `/health` green | FIRE (5/5) | P2-amended | **MISSED** | **headline: clean win** | same |
| SS natural (control row) | same as SS natural | same | FIRE (2/2 + 3/3 post-reboot) | as-frozen (HTTP_STATUS-only) | MISSED — analytically forced (a 201-only contract cannot fail on a swallow that always 201s) | forced methodological control | same |
| SS constructed (control row) | same as SS constructed | same | FIRE (2/2 + 3/3) | as-frozen | MISSED — analytically forced | forced methodological control | same |
| SS benign | no fault; both legs land | — | **NO_FIRE** (1 stratum / 2 legs) | P2-amended | no flag on either leg (nothing to catch) | specificity control | same |

Legend. **FIRE** = the fault leg ACKS the write (2xx / body-status success) yet the write is ABSENT
from that leg's own read-back at the gate, while the control leg's write is PRESENT — the paired
differential cannot fire without that split. **CAUGHT / MISSED** = the comparator flags the fault leg
and not the control / flags neither. **N "(5/5)"** = five independent control+fault leg-pair
repetitions, all with the same categorical outcome.

Footnotes. (a) TT has no dual-form rows because its response carries a bindable envelope
(`{status,msg,data}`) — the blind author could and did bind ENVELOPE_STATUS/MSG_CONTAINS; SS's bare
`{id,name}` 201 gave the blind author only HTTP_STATUS — itself a datum on the bindability spectrum.
(b) The comparator-form axis does not multiply MIST's evidence: there are TWO headline fault
phenomena per SUT plus anchor/control rows. (c) Every MIST verdict passed the machine-enforced claim-eligibility gate (R-R1:
joinMode=correlator ∧ correlatorUnique, printed per cell). (d) SS as-frozen reps 3–5 ran post-reboot on
fresh broker state — the mechanism reproduces across a full host restart; determinism is structural,
not statistical. (e) SS depth observability rides on the disclosed qm→0 rider (queue-master scaled to
0 makes the queue depth monotonic; the loss itself is consumer-independent — R-SS-8). (f) TT
value-delta benign evidence is in-cell: 5/5 control legs landed (pre-funded balance 50→130), the
paired oracle cannot fire without a control-present ∧ fault-absent split, and
`requirePreFundedBaselines` aborts degenerate configs; Gate-1's 2,127-record benign run exercised the
membership/gate mode.

## Table 2 — specificity / FP (separate table; per-SUT semantics are NOT poolable)

| SUT | denominator | result | what it validates | what it does NOT validate |
|---|---|---|---|---|
| TrainTicket (Gate-1) | 2,127 acked benign records = 30 iterations × 71 records − 3 invalid, ONE triple (correlated) | **0 FP**; [0,0] descriptive interval; gate 100% resolved; FP-vs-timeout curve 12.98%@500 ms → 0@≥2 s (the pre-registered cap justification — pack figure) | the QUIESCENCE GATE against a real 1–2 s consistency window | breadth across endpoints; async |
| Sock Shop (SS-B probe) | 1,200 acked benign records = 30 iterations × 40 shapes, TWO endpoints (correlated) | **0 FP**; [0,0] descriptive; every record first-poll-present (9–38 ms) | HAL/_embedded parsing + exact-match membership + cookie-auth read-back on a second system | the gate (never stressed — first-poll presence); async |
| Sock Shop (benign stratum, P6) | 1 no-fault stratum (2 legs) on the queue-depth oracle | **NO_FIRE**; comparator no-flag | the exact head-to-head oracle does not cry wolf when nothing is lost | an FP RATE (small-N control) |

Record-level rule-of-three upper bounds: ≤3/2127 ≈ 0.14% (TT), ≤3/1200 = 0.25% (SS) — labeled
record-level-and-correlated; the per-endpoint/per-design effective N is far smaller. No async-FP claim.

## Claim→evidence map

| claim | cells / result | raw logs | review record | binding rules |
|---|---|---|---|---|
| 1 Capability | Table 1: TT-natural, TT-constructed, TT-agreement, SS-natural, SS-constructed (all FIRE) + SS-benign (NO_FIRE) + P1 FIRE (1/1 evaluable constructed site, adminroute; second site manual G0 only) | `g3-comparator-tt/runs/*` · `g3-comparator-ss/runs/*` · Gate-1 report JSON | P3 2×3-review · P6 3×3-review · P1 in-doc audit + pre-run mechanism review | R-R1 claim-eligibility · R-SS-9 fault-corroborated (TIMEOUT_ABSENT; never trace-corroborated language) · provenance bound (real defects, disclosed triggers) · "no test-specific instrumentation" NOT "instrumentation-free" |
| 2 Comparator boundary | Table 1 headline rows (TT-constructed, SS-constructed) + tie rows + agreement anchor; P4 fraction | same + survey doc | P3/P6 as above · P4 `g3-comparator-tt/REVIEW-SURVEY-RECONCILIATION.md` | R-TT-1 class-scope · R-SS-2 class-scope/concedes-the-thesis · R-SS-6 never win-ratio · R-SS-7 count-delta not arithmetic · "analytical" adjective on 86.25% (the empirical breadth run was REJECTED) · fairness chain: G2 calibration floor (R-G2), agreement anchor, SS dual-form, freeze-protocol (R-SS-3) + TT transcript-retention caveat, entity-absent honest-boundary note |
| 3 Specificity | Table 2 (all three rows) | Gate-1 report/records · `prep/g3-sut2-fp-probe-{report.json,records.log}` · SS benign log | P1 in-doc audit · P5 3×-review · P6 3×-review | correlated-denominator + descriptive-zero caveats carried verbatim; R-R2 comparator infra-failure-rate = PRE-REGISTERED PROTOCOL, no breadth measurement executed; measured budgets as the "cost" content (10 s/500 ms + 20 s caps; ~24 s per TT harness rep = two cancel cells, ≈12 s/cell; ~43 min probe) |
| secondary: effect localization (never a headline) | both tie cells (TT-natural, SS-natural) — MIST additionally localizes the specific lost write where the comparator flags an envelope/service-wide signal | same logs as claim 1 | P3 · P6 records as above | R-TT-3 ("effect, not fault/component"); R-SS-1 (modest under a broker-wide outage) |

## Figures list
1. FP-vs-timeout curve (Gate-1): 12.98%@500 ms → 0@≥2 s — the pre-registered cap justification.
2. Table 1 (phenomena) + Table 2 (specificity) as above.
3. Per-cell depth/balance traces (optional appendix): TT pre-funded balance deltas; SS queue-depth
   own-baseline deltas incl. the ground-truth corroboration row (mgmt vs rabbitmqctl, the measured
   ~5 s stats lag).
