# Wave A-VENUE LIFT — HALTED at 3-cold review (premise over-read; question back to user) — rev 1

**Date:** 2026-07-17 · Owner: main_track · Status: **HALTED. 3-cold = A REJECT / B accept-w-revisions / C REJECT. C found (source-verified) the plan's premise over-read the user's '只能投A会' into 'research-novelty-track ONLY', foreclosing the empirical/benchmark-track A (ISSTA/FSE/ICSE) that IS an A-venue, IS weak-accept-achievable per the publishability review, and IS the project's own committed primary A-path (README §139/§328). A found the research-novelty goal likely unachievable by engineering (Cast cap). A4 re-runs the already-CLOSED null Gate-3 (g3-result.md 'NOT MET… nothing in the wild' + S3 0/1514). WAVE DOES NOT EXECUTE AS WRITTEN — the interpretation question is with the user (recon REVIEW-LIFT-PLAN-RECONCILIATION.md). A2 framing fixes = safe under either reading.**
**Trigger:** USER 2026-07-17 — "只能投A会" (A-venue research track ONLY; benchmark/B-venue
fallback REJECTED). The publishability review (3-cold UNANIMOUS, `REVIEW-PUBLISHABILITY-
RECONCILIATION.md`) put current materials at **weak-reject research / weak-accept benchmark**;
this wave operationalizes the reviewers' UNANIMOUS fix so the research-track bar is
credibly contested.

## §0 The honest ceiling (stated so the plan is not oversold)
Cast caps the MECHANISM novelty (it already detects masked-2xx, 89 dev-confirmed). The
engineering package below lifts weak-reject → **borderline research** (a real, fightable
improvement) but NOT clear-accept — the research case rests on accessibility + the
open benchmark + a MEASURED demonstration that a conformance/trace oracle STRUCTURALLY
misses what MIST's read-back catches. The ONLY clear-accept uncapper is **Gate-3** (a real
acked-but-lost defect a competent LIVE oracle misses), which S3's 0/1514 says is scarce —
included as a HIGH-VARIANCE upside leg, honestly bounded.

## §1 The four legs (risk/leverage-ordered)

### A1 — THE FAIR, POWERED, NON-STRAWMAN HEAD-TO-HEAD (the unanimous #1 fix; highest leverage)
The gap all three reviewers named: no fair head-to-head where a real tool REACHES the
acked-2xx write and is MEASURED to miss the masked loss. Convert "tools can't run / we tie
/ cells n_e" into "real tool, fairly configured, reaches the write, measurably misses."
- **Design:** on each eligible site, DRIVE a real black-box tool to the acked-2xx WRITE
  success state (with legitimate config — auth tokens, a scripted state-setup prelude that
  is DISCLOSED tool configuration, NOT a MIST advantage), run it FAULT-ACTIVE, and MEASURE
  (N≥5) that it (a) acks 2xx and (b) does NOT surface the durable loss its oracle set can't
  see. Then run MIST on the SAME site → catches it. The cell becomes a MEASURED miss, not
  a NOT_INTERPRETABLE.
- **Sites (pick the ones a tool can be fairly driven to the acked-2xx write):**
  (i) **OTel checkout** — Schemathesis's STATEFUL phase already REACHES POST /checkout
  (the review's clean cell); add a cart-populate prelude so checkout acks 2xx with a real
  order, run fault-active (accounting scale-0), measure the miss. THIS IS THE HEADLINE CELL.
  (ii) a 2nd site for power — SockShop orders or TeaStore order with a scripted
  login+cart prelude feeding the tool a valid session, fault-active, measured miss.
- **Tool set:** Schemathesis (clean, reached statefully — PROMOTE to headline) + EvoMaster
  WITH auth/state config (revisiting the earlier no-auth call — the review flagged the
  auth-skip as the liability "you set the baseline up to fail"; a fairly-configured
  EvoMaster that reaches+misses removes that line). Both = REAL tools, fairly driven.
- **Rails:** the state-setup prelude is DISCLOSED tool configuration (a fair-comparison
  input, symmetric with MIST's own stimulus authoring — reported in the authoring-cost
  table); the fault-active masking is the SAME committed fault; ground truth = direct
  reads; SEPARATE table (never merged into matched-recall); N≥5 per cell for a measured
  (not anecdotal) miss.

### A2 — FRAMING FIXES (zero new experiment; endorsed by B+C; do FIRST, immediately)
- **Promote Schemathesis's clean stateful-reach-yet-oracle-blind miss to the HEADLINE
  real-tool claim; DEMOTE EvoMaster's reachability-barrier to a hedged secondary note**
  (its 4 NOT_INTERPRETABLE cells stop being a co-equal "measured barrier").
- **Make the pre-registered NULLS the STATED THESIS** (intro): "a pre-registered
  measurement of this fault class, nulls included (S3 0/1514; presence-defuser 0/≥8)" —
  NOT results-section asterisks after a capability-first pitch. Recast the claim map's
  ordering + the paper-draft-plan skeleton accordingly.
- Update `RESULT-pws-l1-evomaster.md` + `paper-draft-plan.md` P-rows to the demoted
  EvoMaster / promoted Schemathesis / nulls-first framing.

### A3 — L2 F-corpus BUILD-OUT (the SIZE answer; implementation DONE, needs cluster)
The implementer finished 7 faults on `fcorpus-build` (compile-verified). Orchestrator:
diff-review (conduct) → build images (JDK-8 target; the disclosed JDK-21 compile overrides
are build-only) → set-image → **class-aware B-m6 live verify** (lost = absent by direct
read; corrupted = present-but-wrong) → capture fault+control legs with machine-read
read-back bodies → case files (corrupted → MIST n_a out-of-scope-by-design; F8/F14 C-A4
adjudication) → neutralize + validate + integration-chain regen. Additive; corpus 27 → up
to ~34; NOT headline-load-bearing (size helps #1/#2, not the research bar).

### A4 — GATE-3 real-bug hunt (the HIGH-VARIANCE uncapper; honestly bounded)
A fresh, pre-registered wild-hunt for a NATURAL acked-but-lost-write defect on the live
SUTs that a competently-configured LIVE oracle (from A1) also misses — the one thing that
uncaps to clear-accept research. S3 prior = 0/1514 (scarce); this leg is time-boxed and
STOP-discloses if 0 (the scarcity itself already a finding). NOT relied upon; pure upside.

## §2 Order + budgets
A2 (framing, ~½ day, no cluster) → A1 (the head-to-head, ~2-3 d, live) → A3 (L2 build-out,
~2-3 d, live) → A4 (Gate-3 hunt, time-boxed ~1-2 d, live). A1 is the load-bearing lift;
if A1's fair head-to-head cannot be achieved on ≥2 sites (tools genuinely can't be driven
to the acked-2xx write even with fair config), that is itself a STOP+surface (it would mean
the reachability barrier is real+fair, re-validating the current framing — but the review
says try the config first).

## §3 DoD
1. A2: framing fixes landed in the RESULTs + paper-draft-plan (Schemathesis headline,
   EvoMaster demoted, nulls-as-thesis); 3-cold re-read of the reframed claim map.
2. A1: ≥2 sites with a MEASURED real-tool miss (tool reaches acked-2xx write + misses the
   durable loss, N≥5) + MIST catches on the same site; the separate head-to-head table;
   authoring-cost symmetry recorded; RESULT + freeze row.
3. A3: L2 faults built + B-m6-verified + captured + case-authored (or per-fault
   swap/disclose); integration-chain regen; freeze row.
4. A4: the hunt executed + result (a landed bug = the uncapper; else the disclosed 0).
5. Post-DoD: a FRESH 3-cold publishability re-review — does the lifted package clear the
   research-track bar (or reach honest borderline)? + RESULT + freeze + memory.
- **The write-up gate stays: NO draft until ALL experiments incl. the rater study done
  (user 2026-07-16(b)).**

## §4 NOT in scope
The rater study (user-side); the write-up; any MIST oracle-semantics change; SmartFetch;
dropping to a non-A venue (user: A-only).
