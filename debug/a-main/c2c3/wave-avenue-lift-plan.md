# Wave A-VENUE STRENGTHEN (empirical/benchmark-basis FULL main-track paper) — rev 2

**Date:** 2026-07-17 · Owner: main_track · Status: **rev 2 — GOAL CLARIFIED BY USER
(2026-07-17): "full paper, main track" at an A-venue (ISSTA/FSE/ICSE), contribution basis =
EMPIRICAL/BENCHMARK (the README §9 committed primary A-path), NOT mechanism-novelty. rev-1
3-cold (A REJECT / B accept-w-rev / C REJECT) folded: the research-novelty framing is
DROPPED (my over-read of "只能投A会"); A4 Gate-3 re-run DROPPED (already a closed null:
g3-result.md "NOT MET… nothing in the wild" + S3 0/1514); A1 REFRAMED per B (structural
oracle-blindness + control-vs-fault differential, NOT a fed/underpowered N=5 "systematically
misses"). Awaiting a 3-cold re-review of rev 2 before the cluster legs; A2 framing = safe
now.**

## §0 The honest positioning (recalibrated)
A full main-track submission draws BOTH novelty-lens and empirical-lens reviewers, so on
current materials it is **BORDERLINE** (empirical-lens weak-accept; novelty-lens weak-reject
— Cast caps the mechanism). This wave does NOT chase novelty (unreachable) or re-hunt a
real bug (G3+S3 both null). It STRENGTHENS the empirical/benchmark case + fixes the framing
so the empirical through-line dominates — pushing borderline → the accept side of borderline.
The honest deliverable target = **a defensible full empirical/benchmark main-track paper**,
not a guaranteed accept.

## §1 The four legs (risk/leverage-ordered)

### A1 — THE STRUCTURAL ORACLE-BLINDNESS HEAD-TO-HEAD (B-reframed; the empirical spine)
The rigorous form of the reviewers' fix — NOT "we ran a tool N times and it missed"
(fed-prelude tautology / underpowered: 0/5 → 95% CI upper ~0.6, A-B1/B-B1/B-B2), BUT:
**the real-tool CLASS structurally lacks a durable read-back oracle, so a CONTROL-vs-FAULT
run pair produces IDENTICAL tool output even where the tool REACHES the acked-2xx write** —
the miss is by-construction, demonstrated by the differential, not asserted from a small N.
- **The claim = structural + differential**, not statistical: for each cell, show (a) the
  tool REACHES the write (autonomously where it can — Schemathesis's stateful phase reached
  POST /checkout; via a DISCLOSED tool-standard state prelude where needed — e.g. a booked
  order for TT cancel — with the tool's OWN oracle still deciding detection, never a
  MIST-authored check), and (b) control-leg tool output == fault-leg tool output (the tool
  cannot distinguish landed from masked-lost), while (c) MIST on the SAME site catches it +
  ground-truth direct-reads confirm the loss.
- **Cells (B's recommendation — the two documented-blocked 2nd sites are OUT):**
  (i) **OTel checkout** — Schemathesis stateful REACHES /checkout; run control-vs-fault
  (accounting scale-0), show identical tool verdicts. (ii) **TT cancel-refund** — MIST's
  5/5 flagship; a DISCLOSED booked-order prelude drives the tool to the cancel write;
  control(clean) vs fault(fabricatedack), identical tool output. (TeaStore 302-not-2xx =
  off-thesis; SockShop crashed-under-load — both excluded, disclosed.)
- **Tools:** Schemathesis (its own stateful reach — the clean headline) + optionally
  EvoMaster-with-auth ONLY if it reaches fairly (else its NOT_INTERPRETABLE stays a hedged
  secondary, per A2). The prelude is DISCLOSED tool config (symmetric with MIST's stimulus
  authoring-cost); SEPARATE table, never merged.


### A1 EXECUTION NOTE (2026-07-17, dated — the load-bearing fair-reach question resolved)
**Verified:** the earlier Schemathesis-OTel /checkout was a GARBAGE-BODY POST
(`{"address":{},"email":""}`), NOT a genuine acked-2xx order — reviewer-A/B-N6 confirmed.
ROOT CAUSE (offline spec check): the committed specs carry NO request-body EXAMPLES for the
write ops, so a black-box tool has nothing spec-provided to build a valid write → random
garbage bodies → never enters the acked-2xx regime.
**FAIR RESOLUTION (pinned):** enrich a SEPARATE tool-facing spec copy with GENERAL valid
write-body EXAMPLES (a valid order / checkout — standard OpenAPI practice, the same info any
API provider ships; DISCLOSED as fair tool config, NOT the test-specific masked request =
not hand-feeding). The committed E2 comparator specs stay FROZEN (examples live in the
tool-config copy). The tool still generates + DECIDES with its OWN oracle. Then: genuine
acked-2xx reach → control-vs-fault identical tool output → MIST catches + direct-read ground
truth. If even WITH valid examples the tool can't reach a genuine 2xx (multi-step stateful
chaining still defeats it), THAT is the honestly-sharpened barrier (a request-VALIDITY +
state-chaining wall, not a config artifact) — framed carefully, not as "we starved it".
**Candidate genuine-reach paths (try in order):** TeaStore persistence POST /rest/orders
(DIRECT-body, unauthenticated REST — the likeliest genuine-2xx reach) > OTel /checkout
(needs cart prelude) > TT cancel (needs booked-order prelude). Pick the ones that reach
fairly; disclose the rest.

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

### A4 — GATE-3 real-bug hunt — DROPPED (rev 2)
Re-running an already-CLOSED null gate (g3-result.md 2026-07-08 RESULT-of-record: NOT MET, nothing in the wild; + S3 0/1514) at ~1-2d vs the project's ~4-6wk precedent, no new method = not funded. The scarcity is ALREADY a stated finding; the empirical/benchmark paper does not depend on a wild bug.

## §2 Order + budgets
A2 (framing, ~½ day, no cluster; DO FIRST) → A1 (the oracle-blindness head-to-head, ~2-3 d, live) → A3 (L2 build-out, ~2-3 d, live). A4 DROPPED. A1 is the load-bearing empirical strengthening;
if A1's control-vs-fault differential can't be established on ≥2 sites (the tool genuinely can't reach the write even with disclosed fair config), STOP+surface — the Schemathesis-OTel cell alone + the honest disclosure still stands as the real-tool evidence.

## §3 DoD
1. A2: framing fixes landed in the RESULTs + paper-draft-plan (Schemathesis headline,
   EvoMaster demoted, nulls-as-thesis); 3-cold re-read of the reframed claim map.
2. A1: ≥2 sites with a MEASURED real-tool miss (tool reaches acked-2xx write + misses the
   durable loss, N≥5) + MIST catches on the same site; the separate head-to-head table;
   authoring-cost symmetry recorded; RESULT + freeze row.
3. A3: L2 faults built + B-m6-verified + captured + case-authored (or per-fault
   swap/disclose); integration-chain regen; freeze row.
4. Post-DoD: a FRESH 3-cold publishability re-review — does the strengthened package sit
   on the ACCEPT side of borderline for a full empirical/benchmark main-track paper? + RESULT + freeze + memory.
- **The write-up gate stays: NO draft until ALL experiments incl. the rater study done
  (user 2026-07-16(b)).**

## §4 NOT in scope
The rater study (user-side); the write-up; any MIST oracle-semantics change; SmartFetch;
re-hunting Gate-3 (closed null); chasing mechanism-novelty (Cast-capped); dropping to a non-A venue (user: A full main-track).
