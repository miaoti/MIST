# Cold review A — G3 consolidation plan @ 24b7fa9, lens: §2 gate-adjudication honesty

**Verdict: ACCEPT-WITH-CHANGES** — the six pillars are genuinely reviewer-accepted and the plan's
disclosure instinct is real, but §2 commits three distinct overclaim patterns a hostile PC finds in
under an hour with repo access. All fixable by rewording/restructuring §2 + the verdict sentence; no
new experiments.

## BLOCKING-1 — §2 resurrects a SUPERSEDED factual claim at the gate's most scrutinized joint
Plan: "TT cancel→refund is natural IN SOURCE (drawbackMoney false → {1,\"Success.\"})". The accepted
RESULT OF RECORD (g3-comparator-tt/g3-headtohead-results.md:92-94) says the opposite: the clean
{1,"Success."}+lost path is **dead code** on the unmodified fork (drawBack's {0} return unreachable —
findByUserId returns empty-not-null List), so a clean-ack lost refund genuinely requires the DISCLOSED
constructed fabricated-ack. The reachable natural variant is {1,"error"} (acked at status:1, refund
lost — a genuine missing-compensation defect, but one the comparator's msg gate CATCHES). The
parenthetical silently converts the constructed clean win into a "real defect both miss" — the exact
gate conjunction. **Fix:** use the survey/results-doc language everywhere in §2 and the verdict.

## BLOCKING-2 — Per-leg decomposition is a CONJUNCTION FALLACY
The gate sentence binds all conditions to ONE defect instance ("a real defect THAT a status/schema
oracle AND the hand-asserted oracle miss"). §2 adjudicates leg-by-leg and satisfies "real" with the
NATURAL cells while satisfying "both miss" with the CONSTRUCTED cells. No executed cell satisfies the
conjunction: TT natural = comparator CAUGHT (tie); SS natural = CAUGHT under the honest P2-strengthened
form (the as-frozen miss is a declared analytically-forced control — and the blind author DID specify
the liveness clause, so its miss category is primitive-not-bindable, not no-assertion-existed); TT
constructed = fork flag; SS constructed = injected broker policy (which per its own threats section
does not even exercise the code swallow). **Fix:** replace per-leg bullets with a PER-INSTANCE
adjudication table (rows = TT-natural, TT-constructed, SS-natural, SS-constructed, agreement, benign;
columns = real/non-injected? · status-schema miss? · strong-comparator miss? · trace-oracle status) and
state the conjunction outcome in one sentence: **no single real, non-injected instance was missed by
both executed oracles**. The verdict headline derives from that table.

## BLOCKING-3 — Cherry-picked pins: the G2 prereg's DECISIVE-RESULT DEFINITION is unmet and unmentioned
The SAME §2 of prep/g2-novelty-comparator-prereg.md (155-160) pre-registers: "The PC-moving result is
defined ONLY over **real (non-injected) defects at G3**: a wild acknowledged-but-lost-write /
missing-compensation defect that (a) MIST FIREs on, (b) the frozen blind assertion set does not flag,
(c) ≥2 blind raters categorize as no-assertion-existed." Measured against this pin the executed
evidence is **NOT MET** (both-miss cells are injected/constructed; SS-natural's frozen-set miss is
specified-but-not-bindable and the strengthened form catches; no rater κ protocol ran). A PC opening
the prereg to verify the re-scope citation finds this two paragraphs down. **Fix:** the verdict MUST
adjudicate the decisive-result definition explicitly, report it UNMET, then present what IS met.

## MAJOR-4 — The re-scope citation overstates the decision's scope (comparator-protocol ≠ gate criterion; Cast ≠ Tracetest)
Reconciliation item 3 is real but its source (REVIEW-PREREG-A-pc.md:98-104) sits under "Gate-2
criterion fit" — the reviewed decision fixed WHICH COMPARATOR TO BUILD for Gate 2; the Cast-OUT
rationale (no production baselines → nominal → crippled-comparator charge) applies only to the Cast
half. "Tracetest" appears NOWHERE in the reviewed prereg record (grep-verified); README §6 E2 (229)
still promises trace-aware comparators and research/05 rates Tracetest HIGH-obtainable. Adjudicating
the GATE's second-oracle leg against the re-scoped class is a CONSOLIDATION-TIME re-interpretation —
per the reconciliation's own standing rule (96: "any future material change is a disclosed amendment")
it must be flagged as exactly that. **Fix for §2 leg 3:** "Cast half reviewed out pre-run at G2 (cite
item 3 + its Gate-2-criterion-fit scope). Tracetest half neither reviewed out nor executed; adjudicating
the gate against the re-scoped class is a disclosed amendment made NOW, at consolidation. Leg NOT met
as written." Also disclose the executed deployments were traceless on the target paths (TT sidecar-free
cancel; SS no traceId) — feasibility context, partly a deployment choice, cannot carry the leg.

## MAJOR-5 — The verb "finds" never adjudicated
Every defect site was source-identified pre-run (TT defect survey; SS wild-hunt decompile) and every
fault experimenter-triggered; the SS wild-hunt plan §0 itself forbids the discovery claim ("NOT claimed
as discovering a previously-unknown real bug — the developer flagged it in a log line"). **Fix:** the
verdict defines the demonstrated capability as "**detects, end-to-end black-box, when the defect is
exercised**" — no discovery-in-the-wild claim; SS natural earns "hazard class exists in shipped code"
credit only.

## MAJOR-6 — The analytical no-errored-span argument OVERCLAIMS and sits in the wrong place
As drafted it scopes the imagined trace assertion to publish-span error-status checks. A hand-asserted
Tracetest check on downstream-span PRESENCE (consumer span on SS; the DB-write span a stock OTel agent
emits on TT's Money save) is the trace-class analogue of MIST's read-back and could catch both
constructed cells in an instrumented deployment — the rebuttal writes itself. Placing the argument
inside the leg-3 adjudication makes it function as a SUBSTITUTE for the unrun oracle. **Fix:** (a) move
to threats-to-validity/deferred ledger, never the adjudication; (b) weaken to "no errored span exists;
error-STATUS assertions pass"; (c) add the presence-assertion counterfactual + instrumentation caveat;
(d) leg-3 adjudication line = "not executed — not met as written; see §threats."

## MAJOR-7 — Protocol-fidelity deltas need a deviations ledger
G2 prereg pinned symmetric miss tables + ≥2 blind raters + κ, operating-point 2×2s, ≥10 seeds MWU/Â₁₂
(g2 prereg 132-170). Executed: N=5 deterministic focused harnesses, author-adjudicated then
cold-reviewed, no κ. Defensible (deterministic categorical outcomes; not generation-driven) — but the
verdict must map each pinned output to what was produced, in a deviations ledger, before a PC does.

## MAJOR-8 — "reproduced across ≥2 SUTs — MET" needs the constructedness qualifier INSIDE the bullet
"The acked-but-lost DETECTION CAPABILITY reproduces across 2 SUTs / hazard classes / sink types; the
BOTH-ORACLE-MISS demonstration reproduces only in its constructed form (disclosed fork flag on TT;
injected operational policy on an unmodified image on SS)."

## MINOR-9 — Keep "analytically forced" attached to leg-1 evidence
TT's pure status/schema miss was not run as a separate artifact (envelope schema-valid at status 1 —
structural miss); SS as-frozen rows are declared controls. "MET, demonstrated live" → "MET —
analytically forced and confirmed live"; as-frozen rows never double as comparator-defeat tallies in D2.

## MINOR-10 — Say the ladder position out loud; fix a stale header
README §9: Plan A holds "iff Gate 3 yields real bugs"; Plan B = capability "demonstrated on injected
faults with measured oracle FP and a fair assertion-based comparator." The evidence EXCEEDS the Plan-B
floor (natural-defect existence on both SUTs; structural-inexpressibility clean wins; Rider-2 fraction)
but does NOT deliver Plan A's trigger (wild PC-moving bugs). "Plan-B-plus" is more honest and more
defensible than "PASS under re-scope" — and costs nothing (§9 prices Plan B as publishable
empirical-track; the primary A-path IS the empirical track). **Also:** g3-headtohead-results.md line 10
still reads "Status: PRELIMINARY — a re-review gates these numbers" while the reconciliation says
REVIEWER-ACCEPTED; fix the stale header before D1 cites it as a pillar.

## Bottom-line recommended verdict sentence for g3-result.md
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
> types, including a delta/aggregate observable class (11/80 structurally non-bindable in the frozen
> set) that the strongest fair blind-authored response(+liveness) contract oracle structurally cannot
> express, at measured FP 0 on both SUTs' benign paths — which clears and exceeds the README §9 Plan-B
> evidence floor without discharging Plan A's "Gate 3 yields real bugs" trigger.
