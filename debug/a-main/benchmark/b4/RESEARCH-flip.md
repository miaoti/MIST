# RESEARCH-flip — what would it ACTUALLY take to flip the main-track paper to a top-conf accept

**Author of this memo:** hostile-but-constructive strategist. **Date:** 2026-07-19.
**Inputs:** `REVIEW-final3-{1,2,3}.md` (three cold PC reviews, unanimous BORDERLINE-REJECT,
~25–38% at ISSTA, point ~30%) + the artifacts they cite (`c2-freeze.md` §5 floors;
`scoring/matched-recall-table.json` cell-level; `paper-draft-plan.md` claim map + rails;
`step2-execution-checklist.md` Step 5). **Constraint from the brief:** author REQUIRES a
top conference, CANNOT wait for a journal cycle; a human rater study is pending (weeks-scale,
user-run). **Forbidden:** re-running the disclosed closed nulls (S3 wild-hunt 0/1514; Gate-3).

**One-sentence honest frame:** the decisive reject-driver (statistical power / corpus scale)
is STRUCTURAL and is *blocked from a clean fix by the paper's own S3 scarcity finding* — so no
weeks-scale team experiment closes it; the only levers that move accept-probability meaningfully
are a REFRAME (free) plus the RATER study becoming the headline (user-run, weeks), with a small
team experiment package that removes two named secondary reject-reasons.

---

## 1. Each distinct structural objection → smallest addition that weakens it → achievability

Labels: **[WEEKS]** = cluster experiment the team can run in ~2–4 weeks · **[RATER]** = needs the
pending human study · **[MONTHS/PARTNER]** = needs a new mechanism, field data, or a partnership ·
**[WRITE]** = fixable in the write-up, no experiment.

| # | Objection (reviewer refs) | Smallest concrete addition that materially weakens it | Label | What it buys / residual |
|---|---|---|---|---|
| **O1** | **Statistical power / corpus scale — THE decisive one.** 10 evaluable positives / 13 evaluable negatives, 3 SUTs; per-cell N=1–4; Wilson-95 recall floors 0.44–0.51 (`matched-recall-table.json`: span-presence & trace-invisible `[0.4385,1.0]`, trace-uninstrumented `[0.5101,1.0]`); **6 of 10 flagged positives are TrainTicket** (TT 6 / TeaStore 3 / OTel 1); corpus below its OWN floors (`c2-freeze.md` §5: distinct sites 10 vs ≥21–28 F-corpus target / ~15–18 estimate; case-runs 33 vs ≥45; S2 ≥35 unmet). R1-1, R1-2 (R1), R3 sep#1 MUST-FIX. | (a) Add induced positive replicates in the EXISTING harnesses (TeaStore mesh-sever order-adjacent writes; OTel flag sites) to lift the span-presence + trace-invisible cells from n=3 toward n≈7–9 → floor rises ~0.44→~0.55–0.65. (b) The *real* fix — a larger multi-SUT NATURAL corpus or field-confirmed masked-2xx (the Cast route). | (a) **[WEEKS]** but CI-cosmetic; (b) **[MONTHS/PARTNER]** | (a) deletes the single most-quoted number (0.44) but is induced padding — all three reviewers pre-empt it, does NOT change the "underpowered pilot" character; the pre-registered 21–28 distinct-site floor is UNREACHABLE (survey ceiling ~2 new sites, F8/F14). (b) is the only thing that truly closes it and is blocked by S3 (0/1514). **No weeks-scale move closes O1.** |
| **O2** | **Mechanism novelty capped by Cast — conceded.** `paper-draft-plan.md` §5 rail: the masking oracle is "the NON-NOVEL part — Cast/Microusity own detection primacy." Remainder = accessibility (black-box, OTel-only, no AOP) + a standard differential/metamorphic read-back. R1-2(R2), R2 R2, R3 sep#2 STRUCTURAL. | A genuinely NEW oracle mechanism (e.g. a timeout-free lost-vs-eventual separator, or a causal/counterfactual read-back). OR: demote the oracle to explicitly SECONDARY and let the benchmark+FP-measurement carry novelty. | Mechanism = **[MONTHS/PARTNER]** (R3: "not fixable without a genuinely new technical mechanism"); demotion = **[WRITE]** | Demotion removes the load the novelty cannot bear (R2: "within reach if the tool is framed as clearly secondary to the instrument") but adds nothing new. |
| **O3** | **Lost-only scope; 8/18 positives out of scope.** 5 corrupted-write + kafka-barred + teastore-depdown + sockshop-swallowed = `not_applicable`; flagship oracle touches ~10 of 18. R1-2(R1 detail), R3 sep#5 STRUCTURAL. | (a) Extend the oracle to the corrupted-present class (tool code). (b) State the honest denominator: "of 13 lost-scope positives, MIST evaluated 10 / flagged 10; 5 further are corrupted-present, outside scope" (R2 gives the exact sentence). | (a) **[MONTHS/PARTNER]** + blocked by the no-tool-code rule; (b) **[WRITE]** | (b) kills the "18 is padded" charge for free; (a) grows reach but is a research effort, not a capture. |
| **O4** | **No EXECUTED learning/anomaly baseline.** `traceanomaly` 33/33 `not_evaluable` (no LICENSE, py3.6, needs a training corpus the 1–5 traces/leg can't supply); Tracetest-live ADVANCED-BUT-BLOCKED (jaeger gRPC panic). "Structurally invisible to anomaly methods" is ARGUED, never demonstrated. R2 resid(a) ("an experiment is arguably still needed"), R2 R3, R3 sep#8. | Implement ONE minimal runnable unsupervised structural detector (span-set/edge-set novelty scored against the per-case control-leg trace as "normal"; even a weak one — R2's words) and run it OFFLINE over the 13 traced captures as one more arm in the scoring table. | **[WEEKS]** — HIGH confidence | Converts "structurally cannot" from argued to **MEASURED** on the trace-invisible-by-construction row (the anomaly baseline flags error-span cases, MISSES the 3 by-construction-invisible positives the read-back catches), leg-invariant like the existing Schemathesis arm. **The single most tractable, highest-value team experiment.** Residual: the invisible cell is still only N=3. |
| **O5** | **Induced-heavy / single-box ecological validity.** Most positives are fork-flag / scale-to-zero; only ~2 natural (`TT-cancel-refund-natural`, oteldemo kafka-swallow); single deployment box. R1-4(R3), R3 sep#7. | Re-run 1–2 flagship cases on a 3-node cluster to kill "single-box"; otherwise disclose. The natural:induced ratio CANNOT improve (scarcity). | Small **[WEEKS]** gesture + **[WRITE]** | Low leverage; removes a cheap dock, not a spine issue. |
| **O6** | **Headline is the PAIRED eval-harness mode, not the shipped OBSERVE oracle.** Nearly every flag carries `oracle_mode=paired`; README ships observe. 10/10 = harness differential, not the runtime oracle. Reinforced by MYC: **0 DI records over 5145 tests** under realistic budgets (R1 add'l note: "is the oracle practically reachable outside the hand-picked harnesses?"). R3 sep#3 (bait-and-switch risk). | (a) Foreground paired-vs-observe; state 10/10 is the harness differential. (b) Run the SHIPPED observe-mode oracle end-to-end on the flagship positives, properly armed (avoiding the MYC starvation), and report catch/miss — extend the existing `RESULT-e2e-allure-demo.md`. | (a) **[WRITE]**; (b) **[WEEKS]** — MEDIUM confidence | (b) closes the "what we ship ≠ what we report" gap AND answers R1's practical-reachability question; the SECOND most valuable team experiment. Residual: if observe-mode still starves on these cases, the honest measurement is itself a limitation (cleaner than today's silence). |
| **O7** | **Rater study (C3) incomplete, pre-known shortfalls.** All κ/prevalence cells `[RATER-GATED]` (`paper-draft-plan.md` §1); floors unmet (calibration ≪50; S2 ≥35; rehearsal S0=14/36, S1=22/28, S2=15/35 per `step2-execution-checklist.md`). R3 sep#6 MUST-FIX. | Complete the human study with usable κ; headline the human-adjudicated **genuine-vs-benign discrimination + FP-instrument validation ON THE CONSTRUCTED CORPUS** (NOT wild prevalence). | **[RATER]** — weeks, user-run, IRB-gated | The ONE result no cluster/competitor can preempt (Cast-independent). R1 caveat: wild-prevalence loops back to S3 ("we couldn't measure prevalence — scarce") — so headline adjudicability + genuine-vs-benign, which are NOT scarcity-blocked; disclose the inherited power floor. |
| **O8** | **MIST false-negatives described, not measured.** The evaluable-positive denominator is curated to exactly the cases where MIST fired; no in-scope corpus miss observed. R2 resid(b). | Design ≥1 in-scope stress case (lost write with a queryable-but-noisy read-back surface) in the existing harnesses and measure whether MIST misses. | **[WEEKS]** — low–medium | Converts "no measured miss" into measured boundary behavior; modest, and risks producing a real FN that must then be disclosed (acceptable). |
| **O9** | **Thin live competitor head-to-head.** Tracetest never ran; TraceAnomaly not-cleared; contract-invariant evaluable on ~1 case; live comparison rests on Schemathesis-on-recorded-acks + EvoMaster reachability-barrier. R1-3(R3), R3 sep#8. | Get ONE live frontier tool running e2e (fix the Tracetest jaeger gRPC panic if it's config) OR let the O4 anomaly arm double as the live-competitor answer. | **[WEEKS]** if the panic is config; else folds into O4 | Medium; the anti-strawman is ALREADY substantially closed by execution (Schemathesis `leg_invariant:true` MISS; EvoMaster reachability barrier) — a live cell adds polish, not a new story. |

---

## 2. Is there a weeks-scale executable package that honestly moves accept-probability +10–15?

**Short answer: NO, not a reliable +10–15 from team-executable work alone — the honest ceiling of a
pure weeks-scale team package is ~+5–10 (≈30% → ~37–40%), because it cannot touch the decisive O1
(scale, scarcity-blocked) or O2 (Cast-capped novelty, conceded).** The reliable +10–15 exists ONLY
if the rater study (O7, user-run) lands cleanly AND is promoted to the headline via a reframe.

**The precise team package (no new natural positives, no forbidden re-runs):**
- **E-ANOM [O4, HIGH value]** — implement a minimal unsupervised structural trace-anomaly detector
  (edge-set/span-set novelty vs the per-case control-leg trace) and add it as one more OFFLINE arm to
  `matched-recall-table.json`, run over the 13 traced captures (OTel + TT rows). Buys: "structurally
  invisible to anomaly methods" becomes MEASURED (flags error-span, misses the 3
  trace-invisible-by-construction positives), leg-invariant. Kills R2-resid(a) + R3-sep#8. **~+3–5.**
- **E-OBS [O6, HIGH value]** — run the SHIPPED observe-mode oracle end-to-end on the flagship positives
  under a realistic budget (avoiding MYC starvation), report catch/miss; extend `RESULT-e2e-allure-demo.md`.
  Buys: closes paired-vs-shipped (R3-sep#3) + answers R1's practical-reachability. **~+2–4.**
- **Writing reframes [O2/O3/O6/O8, free]** — instrument-first spine; honest "13 lost-scope / 10
  evaluable" denominator; 8/1/1 live-split at every headline; paired-vs-observe foregrounded. **~+2–3.**
- **E-CI [O1-partial, LOW value, optional]** — induced replicates to lift the 0.44 floor toward ~0.6.
  Deletes the most-quoted number but invites the padding charge; near-wash. **~+0–2.**

These do NOT stack cleanly (a reject-on-scale reviewer is unmoved by any of them). Net honest team-only
delta: **~+5–10**. Add the rater study landing + reframe and you reach **~+10–20**, i.e. ~30% → ~48–55%
at the best-fit venue — still a coin flip, never a safe accept, because O1's character and O2's conceded
novelty both survive everything available in the timeframe.

---

## 3. The single biggest lever

**Not (b), not (c). The biggest lever is (d) fused with (a): REFRAME the paper's identity to a
measurement instrument, with the human-adjudicated genuine-vs-benign / FP-validation (the rater study)
as the Cast-independent HEADLINE — and the detection recall table demoted to supporting evidence.**

Why each alternative loses:
- **(b) more induced positives** — scarcity-capped and CI-cosmetic; all three reviewers pre-empt it as
  induced padding; R3 explicitly names it the *runner-up*, not the winner. It cannot change the
  "underpowered pilot" character; it only edits a confidence interval.
- **(c) live competitor head-to-head** — the anti-strawman is already largely closed *by execution*
  (Schemathesis leg-invariant MISS; EvoMaster reachability barrier). A live Tracetest cell is polish;
  the competitors MISS by construction either way. Medium, not decisive.
- **(e) new mechanism** — that's O2's [MONTHS/PARTNER] fix; not available in the timeframe.

Why (d)+(a) wins (and is what R3's "single highest-leverage move" and R2's "framed as clearly secondary
to the instrument" both point at): it (i) adds a result **no bigger cluster or competitor can preempt**
— the first open genuine-vs-benign measurement of masked-2xx, and the benign-trap FP axis all three
reviewers call genuinely rare (R1 accept#1; R2 A3; R3 non-blocking credits); (ii) grows the NEGATIVE
stratum, which attacks the scale critique from the side that is **NOT** scarcity-blocked (benign traps
are constructible, unlike natural positives); (iii) converts O1 from fatal-to-the-spine into a
survivable supporting table. The critical guardrail (R1's warning): headline **adjudicability +
genuine-vs-benign on the constructed corpus**, NOT wild prevalence — wild prevalence loops straight
back to S3's null and reads as "we couldn't measure it."

Brutal caveat: even this lever does not fix O2 (Cast) or O1's underlying character. A power-focused
reviewer can still sink it; all three say one firm reject on scale is near-certain. The lever raises the
CEILING to plausible-borderline-accept, not to safe-accept.

---

## 4. Honest bottom line + next 2–3 actions

**Recommendation: (iii) REFRAME — sequenced behind the in-flight rater study, with the small team
package running in parallel — and submit to FSE, not ISSTA.** Rationale:

- **(i) push as-is to a top venue and accept ~30%** wastes the one submission on the framing the reviews
  already rejected 3/3. The detection-recall spine is exactly what fails; shipping it unchanged banks a
  reject and burns a cycle.
- **(ii) invest weeks in a package then submit** is necessary but INSUFFICIENT alone — a pure team
  package moves only ~+5–10 and leaves the paper still headlined by the losing thesis.
- **(iii) reframe** is the only move that changes the paper's IDENTITY from the thing that is failing
  (underpowered detection) to the thing that is strong (an honest measurement instrument + a rare FP
  axis + two first-class NULLS). It is compatible with "can't wait for a journal": the rater study is
  already in flight on a weeks scale, so the reframe's headline evidence arrives on the conference clock,
  not the journal clock.

**Venue:** FSE, not ISSTA. All three rank ISSTA best-FIT but hardest-BAR on exactly the scale axis where
this is weakest; FSE is the most receptive to the measurement-instrument / honest-nulls narrative the
reframe leans into (R1: "I'd submit here first"), and MIST is already at ISSTA'26 tool-demo — the
main-track submission must visibly out-scope that, which today it does not.

**Next 2–3 concrete actions:**
1. **Lock the instrument-first reframe as the SPINE (not a rider).** Rewrite so C2 (benchmark) + C3
   (rater-measured genuine-vs-benign + FP validation) is the headline and the recall table is explicitly
   supporting with its honest 8/1/1 live-split and "13 lost-scope / 10 evaluable" denominator. This is a
   write-up move gated by the standing user "no write-up until all experiments incl. raters are done"
   rule — so stage it as the reframe DECISION now, execute when the gate opens.
2. **Execute E-ANOM** (one runnable unsupervised structural anomaly detector, offline over the 13 traced
   captures, added as a scoring arm) — the highest-value team-executable experiment; converts the
   "structurally invisible to anomaly methods" claim from ARGUED to MEASURED and is leg-invariant-provable.
3. **Execute E-OBS** (shipped observe-mode oracle end-to-end on the flagship positives, extending the
   allure demo) — closes the paired-vs-shipped credibility gap and answers "is the oracle practically
   reachable," which the MYC 0-DI-over-5145 result currently leaves wide open.
   *(In parallel, user-side: complete the rater study, headlined as adjudicability + genuine-vs-benign,
   never wild prevalence.)*

**The unvarnished truth the author should hear:** with EVERYTHING above done well, the honest ceiling at
a top venue is ~50% — a coin flip — because two objections (O1's scale character, O2's Cast-capped
novelty) are structurally unfixable in the available time. The move that maximizes expected value under
"top-conference, can't-wait" is reframe-to-FSE. The move that maximizes probability of acceptance
outright is the journal home all three reviewers name (EMSE/TSE/TOSEM), which the brief forbids — that
forbiddance, not any weakness in the work, is what caps the odds at a coin flip.
