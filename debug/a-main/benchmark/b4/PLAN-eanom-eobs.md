# PLAN — E-ANOM + E-OBS (the weeks-scale, offline/bounded top-venue hardening package)

**Date:** 2026-07-19 · Status: PLAN — awaiting ≥3 cold-reviewer ALL-ACCEPT before ANY execution.
Context: after 4 review rounds the a-main empirical paper is a unanimous BORDERLINE-REJECT at a top
venue (~30% ISSTA, ~45-50% honest ceiling even fully executed). The `RESEARCH-flip` agent judged
these two experiments the only weeks-scale, non-scarcity-blocked items that move the honest number
(together ~+5-10 points). This plan asks the reviewers to judge BOTH the design AND **whether they
are worth doing at all** given that ceiling.

---

## Why these two (and why nothing bigger is on the table)
The decisive objection (corpus scale / statistical power) is STRUCTURAL and self-blocked by the S3
scarcity null (0/1514) — no weeks-scale experiment closes it, and the reviewers pre-empt "more
induced positives" as CI-cosmetic. So the only honest weeks-scale moves are the two that convert an
ARGUED claim into a MEASURED one, hardening the instrument's evaluation without pretending to fix
scale:
- **E-ANOM** closes reviewer-2's "no trace-anomaly competitor actually executes" (`traceanomaly` is
  `not_evaluable 33/33` today — we ARGUE construction-blindness, never ran one).
- **E-OBS** closes "the 10/10 headline is the PAIRED eval-harness, not the SHIPPED observe oracle"
  and answers the MYC 0-DI-over-5145 reachability question with a bounded datum.

Neither touches scale or the Cast novelty cap; both are honest hardening. **Reviewers: if you judge
+5-10 not worth the effort against a 45-50% ceiling, say so — "don't do it, go straight to the
rater study" is an acceptable verdict.**

---

## E-ANOM — a REAL unsupervised trace-anomaly detector, measured over the corpus (OFFLINE)

**Goal:** replace the `traceanomaly` arm's `not_evaluable 33/33` with MEASURED per-case verdicts,
so the paper can say "a real anomaly detector, executed, misses the silent masked-loss class"
instead of asserting it.

**Design (pre-registered BEFORE running, to defend against a strawman charge):**
- Input: the 13 corpus cases that carry captured Jaeger traces (control + fault legs where present).
- Detector = a representative UNSUPERVISED structural+statistical trace-anomaly detector, built the
  way the published class works (TraceAnomaly / structural-invariant detectors): from the CONTROL-leg
  trace(s) build a per-root "normal profile" = { services, operations, parent→child edges, status
  codes seen; span-count and per-operation duration distributions }. Score a FAULT-leg trace and
  FLAG iff ANY of: (a) a novel service/operation/edge, (b) a missing REQUIRED edge, (c) any span
  with http>=400 or otel=ERROR, (d) span-count or duration z-score > 3 vs the normal profile.
- The flag rule is FIXED here, in this plan, and is the union of the standard anomaly signals — NOT
  tuned to miss. If it fires on some cases, that is reported as-is.

**Honest expected result (and the nuance that makes it credible):**
- On the SILENT code-level masked losses (the TT sut_injector cases, the teastore vendor-flag): the
  fault trace is structurally IDENTICAL to control (same spans, same 2xx, the write simply didn't
  persist; uninstrumented SUTs have no DB span at all) → detector does NOT flag → MEASURED MISS.
- On the LOUD infra-fault variants (OTel dependency-scale-zero, TeaStore mesh-abort): there MAY be a
  severed edge or an error span → the detector MIGHT flag some of these. **This is reported honestly
  per-case, not hidden.** The likely nuanced finding: "anomaly detection catches the loud
  infrastructure-fault variants but misses the silent application-level masked losses that leave the
  trace structurally normal" — a MORE credible result than a blanket "misses everything," and it
  sharpens exactly where the read-back oracle is uniquely needed.

**DoD:** `scoring/run_anomaly_arm.py` + `scoring/verdicts/traceanomaly.json` with measured
per-case verdicts (flag/no_flag/not_evaluable) replacing the all-n_e stub; the detector code
committed; a RESULT with the per-case table + the honest loud-vs-silent split; the matched-recall
table regenerated. **Fully OFFLINE over committed traces — NO cluster window.**

**Risk / worth-it:** the single design risk is "strawman detector." Mitigation = the pre-registered
standard flag rule + honest per-case reporting incl. any fires. Value = modest but converts a
reviewer-named gap from argued to measured. Reviewers judge if that clears the bar to run it.

---

## E-OBS — MIST's SHIPPED observe-mode oracle on the flagship positives (BOUNDED)

**Goal:** show the SHIPPED observe-mode product (not the paired eval-harness that produced the 10/10)
reproduces the acked-but-lost detection, and get a bounded datum on the MYC reachability question.

**Design:** reuse the committed `TinyObserveRunner` (the E2E Allure demo tool) on flagship positive
scenarios, land-then-flip-then-lose, priority-ordered by MARGINAL VALUE:
1. **One NON-TT flagship** (teastore-order OR oteldemo-checkout) — HIGHEST value: directly chips at
   the TT-heaviness critique by showing the shipped oracle fires on a non-TT SUT.
2. **One additional TT flagship** (cancel-refund fabricatedack) — a second TT site beyond the
   adminroute case the Allure demo already covered.

**DoD:** the shipped observe-mode produces `OBSERVED_COMPLETE_ABSENT` + the `ACKED-BUT-LOST` marker
+ an Allure render on ≥1 non-TT flagship AND ≥1 additional TT flagship, reproducing the paired-mode
verdict; a RESULT documenting it; honest disclosure if any SUT can't reach the write in observe mode
within budget (that is itself the MYC reachability datum, reported not hidden).

**Risk / worth-it:** needs bounded cluster windows (TT revive + teastore or oteldemo). Non-TT observe
runs may hit reachability/starvation — if so, that is an honest finding but weakens the "shipped
product works everywhere" claim. The adminroute Allure demo already shows the mechanism once, so the
marginal value is (a) the non-TT SUT and (b) the "shipped ≠ harness" closure. Reviewers judge if
the bounded cluster time is worth that.

---

## What this package explicitly does NOT do (honesty rail)
- Does NOT add natural positives (scarcity-forbidden) or grow the discriminating-cell count.
- Does NOT touch the Cast novelty cap.
- Does NOT change any label, the 10/10, the 0/13 FP, or any headline number — it hardens the
  COMPARISON around them.
- The honest top-venue ceiling stays ~45-50%; the biggest single lever remains the USER-side rater
  study, which this package does not substitute for.

## Sequencing if accepted
E-ANOM first (offline, no window, lower risk) → then E-OBS (bounded window) → regenerate the chain
→ 3-cold verify-review of the execution → fold. All under the standing no-draft-until-experiments-
+-rater gate.

## The question for the reviewers
1. Is each design SOUND and strawman-free (esp. E-ANOM's flag rule)?
2. Is each WORTH DOING against the ~45-50% ceiling, or is the honest call "skip, go to the rater
   study"? (A "skip E-OBS, do only E-ANOM" or "skip both" verdict is fully in scope.)
3. Any design change that would raise the value-per-effort before execution?
