# S3-HS stress-hunt plan (rev 1) — 3-cold-review reconciliation (EXECUTION GATE)

Three INDEPENDENT cold reviewers audited `stress-hunt-plan.md` rev 1 + the base docs it reuses (E2
result/plan, S3 result + reconciliation, s3-wildhunt-plan, traced-capture-wave-plan, `trace_score.py`, the
detector source). Lenses: **A** feasibility & methodological soundness, **B** scope/value/honest-framing,
**C** hostile-PC & anti-circularity.

## Verdicts (NOT unanimous → per the /goal rule the plan does NOT execute)

| reviewer | verdict | one-line |
|---|---|---|
| **A** (feasibility/soundness) | **REJECT (as written)** | 3 BLOCKING = missing core mechanisms; the plan's premise "only the workload regime changes; detector frozen by construction" is **FALSE against the code** |
| **B** (scope/value/honesty) | **REJECT (as next move)** | plan is honest + well-engineered, but it's the wrong move now: near-certain marginal outcome at documented RAM cost; a NAMED pillar is cheaper to fix |
| **C** (hostile-PC/anti-circ) | **ACCEPT-WITH-FIXES (1 BLOCKING)** | science is disciplined, but it **regresses from E2's own mandated anti-circularity guard**; would REJECT any headline without the fix |

**Net: rejected by the review floor.** Not a paper-integrity failure — the plan is careful and honest — but
it is (A) not executable as written without new tool code that breaks its own scoped-gate premise, and (B)
strategically low-ROI even if fixed, while (C) the rigorous version needs E2's orthogonal-ground-truth
discipline restored.

## Convergence — findings ≥2 reviewers independently hit (the load-bearing signals)

| # | finding | A | B | C | class |
|---|---|---|---|---|---|
| **X1** | **Per-candidate ORTHOGONAL durable ground truth (E2's mandated `inside_money` direct read) was DROPPED** — the plan confirms loss only via the same `/account` endpoint MIST reads (a store re-read, "not an orthogonal oracle" per E2). Under the induced Xenon-HA stress a stale-replica read fabricates a self-concordant false loss → CONFIRMED. bucket-(a) has ZERO independent loss evidence. | BLOCKING-3 fix | Finding 7 | **F1 BLOCKING** | must-fix IF run |
| **X2** | **The Q3 "natural" bright line must be pinned CLOSED**: a "controlled natural-CLASS perturbation" (resource cap, etc.) = **injection**, forfeits "natural." Bright line = the SUT API boundary (client-side load = natural; any knob inside the boundary = injection). If workload-only yields 0, the honest terminus is EXTENDED-SCARCITY, not a redefinition. | (implied) | Finding 6 | **Q3 ruling** | must-pin IF run |
| **X3** | **Rule-of-three `3/H` is optimistic under correlated concurrent failure** (bursty failover fails many concurrent writes together → effective N ≪ H). Report a design-effect-discounted / qualitative bound. | MINOR-9 + Q2 | Finding 8 | F7 | must-fix IF run |
| **X4** | **H≥5000 is wrong; the bound can INVERT to WEAKER than S3.** Cancel "writes" are full register→fund→order→cancel journeys; realistic achieved-H is plausibly a few hundred → `3/H ≈ 0.5–1%` **weaker than S3's 0.20%** — the "stronger extended bound" selling point inverts. | **Q2 ruling** | Finding 1–2 | (F7 adj.) | re-scope: drop fixed H, frame the REGIME (closes F6) not a tighter number |
| **X5** | **The headline is near-unreachable by pure workload; the only lever to raise it crosses X2's bright-line.** On a consensus-HA store, an acked-`{status:1}` × durably-lost intersection needs a triggering event the plan forbids. EXTENDED-SCARCITY is near-certain. | feasibility | **Q1 core** | rejection-case | STRATEGIC (drives the decision) |

## Reviewer-A-unique DECISIVE technical findings (B/C could not see these without the detector source)

- **BLOCKING-1 — the reused detector FORBIDS the concurrency the plan is built on.**
  `DataIntegrityRuntime.beginObserveRun`→`beginRun` **throws** at `mst.test.parallelism>1`
  (:433-444, "the pairing hooks require single-threaded execution"); one global `static volatile Session`
  (:260, :425-427); `WildHuntEngine` state (`ledger`/`seq`/`trailing`/`feedBreaker`) is unsynchronized and
  `runWindow` is a sequential loop (:123-127, :384-410); `OtelWildHunt.currentMarker` is a static read-back
  global (:35). Both existing runners hard-set parallelism=1. ⇒ "only the workload driver changes" is FALSE.
  Reaching P>1 requires EITHER P JVMs + new cross-process aggregation of H/denominators/breaker/dedup
  (**outside the scoped "S3-detector + E2-stimulus reuse" gate**, and it dissolves the single-window
  W3/breaker soundness the S3 reviewers signed off on) OR threading the engine (**new concurrency-safe tool
  code = a different review**). The only sound in-scope reading = **one single-threaded observe stream +
  P−1 uninstrumented background-load generators**; then H is a SERIAL stream's throughput, not P×aggregate,
  and the estimand/§4 H-accounting are wrong as written.
- **BLOCKING-2 — the ack gate mis-classifies handled cancel failures as acked.** Ack rule =
  `httpStatus/100==2 && (bodyStatus==null || bodyStatus==1)` (:675-676), reads only `status`. The frozen
  cancel contract: success = `status==1 AND msg=='Success.'` (assertion-bindings-cancel-refund.yaml:38). So
  a controller-caught cancel failure `{status:1,msg:'error'}` (refund correctly absent, response-VISIBLE)
  is scored acked→RAW→CONFIRMED. C4 does NOT hold on the headline path; the numerator is polluted. Fix: the
  cancel-triple ack predicate must include the `msg=='Success.'` gate.
- **BLOCKING-3 — trace-completeness fail-safe not airtight** (= X1's trace half): a single silently-dropped
  ERROR span promotes a trace-VISIBLE loss (bucket-c) into a fake bucket-(b) headline; `trace_score.py`
  checks "exactly one entry span" (:171-175), never the expected span SET. Needs a per-candidate positive
  span-control under identical stress + orthogonal-channel corroboration (X1), else invoke the traced-wave
  B-M1 rule → collapse to extended-scarcity.
- **MAJOR-5** no per-sub-batch checkpoint (`emit()` runs only after `runWindow` returns → an OOM at write
  4800 loses the whole ledger; "ship the achieved-H partial" is unimplementable as written); the S3 breaker
  exclusion LIST (host/WSL OOM, Xenon cold-start artifacts) — the MODAL stress failure — is not carried
  forward. **MAJOR-6** `/account` returns ALL balances (TrainTicketStimulus:52-54) → O(N²) read-backs at
  5000 buyers, inflating attrition + FP surface. **MAJOR-7** no observe cancel runner exists (E2 used the
  PAIRED `CancelRefundHeadToHead`, not the observe `WildHuntEngine`; `TrainTicketWildHunt` drives
  admin-basic, not cancel) → "direct E2 reuse" is substantial NEW code.

**A independently VERIFIED as REAL:** the `trace_score.py` cancel selector incl. the DB-span that catches
(:62-67, :218-223); the E2 cancel instrumentation recipe; `classify()`/`reProbe` behavior + the
`{status:0}`→not-acked routing. **VERIFIED FALSE:** the "detector frozen by construction" premise; the
ack-gate `msg` check; the existence of an observe cancel runner.

## Reviewer-B-unique (the strategic case)

The frozen claim string (`c2-freeze.md` §1) is about the **benchmark**, not "MIST-beats-trace"; the
natural-discrimination case is a nice-to-have upgrade of a *motivating datum* (E2 synthetic → natural), and
the fault-class **motivation is already banked from the literature** (Cast "HTTP 200 despite internal
failure"; Uber "29% of 2xx carry hidden errors"). Meanwhile a NAMED claim pillar — the benign-trap **FP
stratum (frozen-claim Q5)** — sits under-powered (benign pool 12 < floor 30, presence↮label tell unfixed)
and the **degradation-shaped benign-capture wave** (a standing S3 recommendation, runnable on the standing
OTel tenant, no TT revival) fixes exactly that at a fraction of the risk. B's sequence: (1) run the
benign-power wave; (2) ship E2-synthetic + S3-scarcity + 2.75-A-breadth under the existing honest framing.

## Reviewer-C-unique (the baseline-scoping honesty point)

For an acked-but-lost **money** write the textbook oracle is a **reconciliation / accounting-invariant
monitor** — and MIST's read-back *is* one. The arc scores read-back only against **trace** oracles → a
hostile PC reads "read-back beats tracing" as beating a straw target for this fault class. Honest scoping:
"**trace oracles** = the *observability* baseline; reconciliation monitoring = related work MIST
**auto-derives from a typed contract**." Pre-register that no result is phrased as "read-back beats [all]
oracles."

## Disposition — GATED ON A USER STRATEGIC DECISION (does not auto-proceed)

The findings split into two classes, and the second makes this the user's call, not a mechanical fix-loop:

1. **Fixable-but-heavy technical (A + C's X1/BLOCKING-2/-3, MAJOR-5/6/7):** restore E2's orthogonal
   ground-truth; fix the cancel ack gate; add per-candidate span control; write a NEW observe cancel runner;
   pin Δ under true contention; checkpoint per sub-batch; carry the breaker exclusion list; fix the O(N²)
   read-back; and resolve BLOCKING-1 (either accept a single serial observe stream + background load, or
   step OUTSIDE the scoped gate into new concurrency tooling + a fresh review). This is a MAJOR rev-2, not a
   bar-tightening — and it partly breaks the "zero new tool code" premise the plan sold.
2. **Strategic (B's X5/Q1 + A's Q2):** even fully fixed, the headline is near-unreachable without crossing
   X2's bright-line (which forfeits "natural"), the 0-outcome is marginal or **inverts weaker** than S3, and
   a cheaper reviewer-endorsed alternative fixes a NAMED pillar.

Because executing requires reversing/re-scoping the direction the USER explicitly chose ("追真 headline,"
accepting high-risk/low-yield), and because the review supplies new information the user did not have at
that choice (the headline is near-unreachable by the only "natural"-preserving route; the bound may invert;
a named pillar is cheaper to fix), the disposition is **surfaced to the user** among:

- **(1) Pivot to the degradation-shaped benign-power capture wave** (B-endorsed; fixes the named FP-stratum
  pillar Q5; cheap; standing OTel tenant), then ship. — *Reviewer-favored highest-ROI next move.*
- **(2) Ship what's banked** (E2 synthetic + S3 scarcity + 2.75-A breadth) under existing honest framing;
  the natural headline stays honestly disclosed-as-owed.
- **(3) Re-scoped BOUND-ONLY stress run** (NOT a headline hunt): single observe stream + ambient background
  load, all X1–X4 fixes folded, X2 bright-line pinned closed, result framed as the stress-REGIME extended
  scarcity that closes F6 — reviewers judge it low-ROI but it is the user's prerogative if closing the "you
  didn't look under load" objection is worth the cost.

No execution until the user rules. If (3) is chosen, a rev-2 plan folding X1–X4 + A's BLOCKING-1/2/3 +
MAJOR-5/6/7 goes back through ≥3-cold review before any run (the standing rule).
