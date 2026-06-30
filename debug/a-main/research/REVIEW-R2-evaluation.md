# Review R2 — Evaluation Rigor & Claims Support (MIST plan v3, 2026-06-30)

**Reviewer role.** PC member, A-venue (ICSE/FSE/ASE/ISSTA). Primary lens: empirical rigor —
does the proposed evaluation actually support the claims and survive a demanding PC?
**What I read.** Only `debug/a-main/README.md` (the plan). I treat it as the basis of the paper
that would result from competent execution of what is written, including the stated fallbacks
(§9). Per instructions I give **no credit for admitted-uncertain results** (chiefly the Gate-3
"real lost-write bugs"). I web-checked the evaluation-relevant citations; notes inline.

---

## 1. Recommendation + summary

**Recommendation: Weak Reject** (a defensible Borderline if the PC heavily weights the artifact
and the honesty of the threats-to-validity treatment; my vote is Weak Reject at an A venue).

This is an unusually honest, well-cited, self-aware plan. The core idea — a **differential
read-back metamorphic oracle** (run a mutating request with vs. without an injected downstream
fault, read state back, fire on divergence; §4) — is a clean, correct mechanism that targets a
real, high-severity class (acknowledged-but-lost writes / missing compensation) with strong
motivation (Yuan OSDI'14; Uber SIGMETRICS'25, verified). The positioning against the most
dangerous, very recent prior art (Cast, Filibuster-DB, Microusity) is precise and the deltas are
articulated rather than hidden.

But judged as a *paper*, and giving no credit for the admitted-uncertain bug story, the
**guaranteed deliverable** is the §9 Plan-B paper — which the authors themselves grade as below
the top bar — and its **headline quantitative result (a precision frontier over a naive oracle)
rests on a genuine-vs-benign ground truth that is defined by the same trace signals the oracle
consumes.** The "anti-tautology" section correctly names this danger but its design does not
escape it. The single most novel quantitative claim that *would* break the tie — real
data-integrity bugs that competently-configured assertion tools miss — is admitted-uncertain,
demonstrated (best case) on essentially one SUT, and even in the best case orders of magnitude
below the field's demonstrated bug-finding bar (EmRest 226; AGORA 11; Cast 89 confirmed in
production; verified). Net: high-variance contribution whose *expected* value, de-risked per the
instructions, sits below the A-venue acceptance line.

---

## 2. Strengths

- **S1. The mechanism is a legitimate metamorphic oracle, not a structural check.** The control-vs-fault
  read-back differential (§4) is a sound metamorphic relation grounded in observed state divergence.
  This genuinely answers the "trivial mechanism / 40-line check" objection that (correctly) sinks
  `HiddenDownstreamFailure` alone. The class it targets (lost writes / skipped compensation) is real
  and high-severity (Yuan et al., OSDI'14; verified motivation).
- **S2. Honesty and pre-registration.** Explicit go/no-go gates (§8), an explicit fallback ladder (§9),
  pre-registered rubric and thresholds, and frank disclosure of constraints (signal floor, corpus
  floor, no wild-bug corpus). In a submitted paper this maps to an above-median threats-to-validity
  treatment.
- **S3. Citations are real, current, and correctly used (mostly).** I verified EmRest (226 bugs/16
  APIs, ISSTA'25), Morest (44 found/2 confirmed, ICSE'22), Uber 29.35% and the 42.46%
  Entity-Not-Found correction (SIGMETRICS'25, DOI 10.1145/3700436), Cast (arXiv:2602.00972,
  ICSE-SEIP'26), RCAEval (735 cases/11 fault types, WWW'25), and "No Time to Rest Yet" (ISSTA'22).
  The plan does not invent or inflate these.
- **S4. The prevalence framing is defensibly novel on one axis.** I confirmed Uber publishes **no
  benign-vs-harmful split** (it treats all non-fatal errors as performance waste, ~1.9x compute).
  So an *adjudicated correctness-masking rate* is not a reproduction. (Caveat: M1.)
- **S5. Statistics are above the median.** Mann–Whitney U + Vargha–Delaney Â₁₂ (Arcuri & Briand,
  STVR'14 — correct, standard), Holm/Bonferroni over the SUT×baseline grid, ≥10 seeds, Wilson/
  Clopper–Pearson CIs, released corpus + rubric. This is the right machinery.
- **S6. The "non-zero baseline" instinct is correct.** Refusing "N-vs-0" and insisting on
  precision/FP at matched recall against trace-aware comparators is exactly the right framing for
  this subfield. The problem (below) is the *execution* of that instinct, not the instinct.

---

## 3. Weaknesses / concerns (ranked)

### [FATAL] W1 — The genuine-vs-benign ground truth is defined by the same trace signals the oracle consumes; the "anti-tautology" design does not escape the tautology for the strata that dominate the corpus.

This is the central rigor failure and it is squarely the thing the venue will probe hardest.

The headline of the de-risked paper is **E2: 3–5x precision at matched recall over a naive
"any-error-span-under-2xx" oracle.** That entire win is produced by MIST's "benign filter."
But look at how the labels are made (§6):

- The **operational rubric** defines *genuine* = "required-dependency span server-errored AND
  entry stays nominal 2xx AND no retry/fallback," and *benign* = "optional dep / designed fallback
  / recovered-by-retry." **This is essentially the predicate MIST's benign filter computes.** So on
  the constructed strata the ground-truth partition and the tool's decision rule are near-identical.
  "MIST beats the naive oracle on precision" then reduces to "MIST's filter matches the rule used to
  generate the labels" — a restatement, not an independent result.
- The **benign traps** (Bookinfo `reviews→ratings`, optional `adservice`/`recommendation`,
  circuit-breaker defaults) are *selected to be exactly* what the filter keys on. The naive oracle
  floods FPs on them **by construction**, because the corpus's benign class was defined as
  "graceful degradation that a fallback-detector would suppress."
- The **κ adjudication** is human, which helps — but raters apply *MIST's own rubric*, so inter-rater
  agreement measures consistency with MIST's worldview, not correctness against an external standard.
  High κ here is necessary, not sufficient; it does not de-circularize.

Note an internal inconsistency that makes this worse: the **mechanism** (§4) grounds truth in
*read-back state divergence* (`S_fault` vs the success contract), but the **eval rubric** (§6,
"genuine") grounds it in *span topology only* — read-back is not mentioned in the labeling rule.
Either grounding has a hole:
- Span-topology labels ⇒ circular with MIST's own filter (above).
- Read-back-state labels ⇒ non-circular against a *span-only* naive baseline, but then "the write
  diverged" must be interpreted as "the app *should* have persisted it" — which requires a model of
  *intended* behavior that the plan explicitly disclaims ("no correct-outcome model"). A 2xx with a
  non-persisted write can be a correct rejection. Without an independent intended-behavior oracle,
  "divergence = defect" is itself an assumption.

**What's needed:** an *independent* standard of intended behavior for the genuine/benign call —
e.g., developer confirmation, documented saga/compensation specs, or version-diff oracles — applied
**without** running MIST's predicate, plus benign cases that MIST's filter does *not* trivially
suppress. As written, the precision headline is at material risk of being unfalsifiable. The
RCAEval/Nezha "injected GT is accepted" defense (§6) only legitimizes the *root-cause* label of the
*positive* stratum (which service was faulted) — it does **not** legitimize the contested
*genuine-vs-benign correctness* call, which injection alone never settles (a correctly-compensated
injected fault is benign). I verified RCAEval's GT is "derived from fault-injection parameters"
(service/pod/type) — a root-cause label, not a defect/benign correctness label. The defense is
aimed at the wrong target.

### [FATAL] W2 — The guaranteed deliverable is below-bar by the authors' own grading; the part that lifts it is admitted-uncertain and, even best-case, far below the field's bug-count bar.

The headline contribution (C1 finding real lost-write/missing-compensation bugs that assertion
tools miss) is conditional on **Gate 3** (§8), and the plan concedes "≈0 reproducible wild bugs"
(§1.4) and that the story "hinges on Gate 3" (§0). Giving no credit for that admitted-uncertain
result, the paper I am reviewing is **Plan B** (§9), which the authors themselves grade as "weaker
... plausibly at a slightly lower-tier A venue or a strong empirical track." That is close to a
self-admitted below-A deliverable for the guaranteed output.

Even in the best case the bug evidence does not clear the bar. Verified anchors for oracle/test-gen
papers at these venues: **EmRest 226** (ISSTA'25), **AGORA 11** in million-user APIs (ISSTA'23;
the plan's "32" appears to conflate AGORA+ TOSEM'25 — Mi1), **Morest 44/2-confirmed** (ICSE'22),
and the direct competitor **Cast 137 found / 89 confirmed in production** (ICSE-SEIP'26, verified).
The plan's target is "≥2 incidental loud bugs" plus injected/replicated positives. "MIST finds 1–2
real lost-write bugs that Cast-style tools miss" — even if Gate 3 fully succeeds — is anecdote
against a competitor that confirmed 89 in production. The empirical backbone (C3) is thinnest
exactly where it must be thickest, and the differentiating claim ("bugs no human wrote the
assertion for") is not operationalized as a measured experiment (see W4).

### [MAJOR] W3 — The headline mechanism may be a single-SUT result dressed as a 6–8-SUT study.

C1 requires: state-mutating REST endpoints + a GET read-back + a faultable persisting dependency +
(ideally) a saga/compensation to violate. Of the 6–8 SUTs, the plan itself concedes the
gRPC-thin-REST ones (OTel Demo, Online Boutique, Bookinfo) are **oracle-only, not generation**, and
most demos (Sock Shop, Bookinfo) are read-heavy with little meaningful mutable persistent state or
compensations. Realistically the **data-integrity** story lives on **TrainTicket** (maybe TeaStore);
the others contribute only the weaker "masked 5xx" oracle, which the plan admits is closer to
engineering. So the *count* (6–8) looks adequate while the **novel** claim may be demonstrated on
essentially one system. The plan half-discloses the split ("oracle story rides on instrumented
SUTs") but never confronts that the *data-integrity* headline is plausibly single-SUT. A PC will
read SpanTreeShape/StatusPropagation results on the other SUTs as padding for the headline.

### [MAJOR] W4 — The claim that decides the paper ("bugs assertion tools miss because no human wrote the assertion") is never run as an experiment.

This sentence (§0, §4 residual objection, §9) is load-bearing yet has no corresponding measured
comparison. E2's comparators are a self-built naive oracle, **Tracetest with "a generic span
assertion,"** and anomaly detectors (TraceAnomaly/TraceRCA — which the plan itself flags as the
"this is just anomaly detection" risk, and which are not test oracles, so the comparison is
apples-to-oranges). The fairest and most threatening baseline — **Cast / Filibuster-DB configured by
a competent engineer with a real data-integrity assertion** — appears only in Gate 3 as "a
hand-asserted Tracetest," a weak proxy. To substantiate "no human wrote the assertion" you must show
that competent humans using assertion-based tools, given comparable effort, *do not* catch these —
i.e., a human/effort-controlled comparison. A single generic hand-asserted Tracetest does not
falsify "a competent human would have written it." As written, the differentiator is asserted, not
demonstrated.

### [MAJOR] W5 — The prevalence number measures a different estimand than the Uber anchor it is compared to.

Verified: Uber's 29.35% is a **performance-waste** rate over **natural production traffic** at 6000+
services. MIST's self-measured rate is a **correctness-masking** rate over **error paths it
deliberately drives** (wrk2/locust + "MIST negative inputs to exercise error paths," §7) on 6–8
small OSS demos. Different population, different definition, and — critically — the rate is
**conditional on MIST's own input distribution**, so it is researcher-controlled, not a natural base
rate. The CIs (Wilson/Clopper–Pearson) will be tight but around a researcher-defined estimand.
"Compare to Uber 29.35% as external validity" is rhetorically appealing and methodologically loose;
a careful reviewer discounts it. Prevalence-on-driven-error-paths-on-OSS ≠ prevalence-in-the-wild.

### [MAJOR] W6 — The precision claim may be blocked-by-data on FP control.

The plan concedes (constraint §1.3; B4 "BLOCKED-by-data," priority P2 "only if corpus captured")
that statistical FP control needs a real known-good corpus that does not exist, and that invariants
are bootstrapped from **one** trace today. The headline is a **precision** claim, which requires a
trustworthy FP characterization under realistic load. If B4 is contingent and uncertain, the
precision frontier rests on the naive-vs-MIST rubric comparison (W1) rather than a validated FP
model — i.e., the paper may lack the data to support the very claim (C, §6 anti-tautology) it leads
with. Ablation A1 ("remove benign-filter → naive oracle") re-enters the W1 circularity rather than
escaping it.

### [MINOR] W7 — "First labeled benchmark" is contestable.

RCAEval (735 cases, public, WWW'25) and Nezha (FSE'23) already release labeled microservice
trace/telemetry corpora. The novelty is the *specific* swallowed-downstream/data-integrity labeling;
that must be sharply differentiated or "first labeled benchmark" invites a counterexample.

### [MINOR] W8 — Citation/number tightening.

AGORA's ISSTA'23 paper reports **11** confirmed bugs in million-user APIs (Amadeus/GitHub/Marvel),
not 32; the "32" likely belongs to AGORA+ (TOSEM'25). Tighten before a reviewer catches it. Also,
the central novelty hook leans on Filibuster-DB "naming 'silent data corruption' / 'no oracle for
behavior under failure' as OPEN"; I could not independently confirm that crisp framing from a 4-page
*companion tool demo* — Gate 2 (read it in full) is the right instinct; do not over-quote it as if
it were a survey's open-problem list. The stronger "open oracle" anchor is the TOSEM'23 survey.

### [MINOR] W9 — "Specification-free" sits awkwardly with authoring OpenAPI for baselines.

§7 mitigates baseline-fairness by "author OpenAPI for thin-spec SUTs, or restrict head-to-head to
spec-rich SUTs." Either path shrinks E1's breadth or dents the "specification-free" framing for the
comparison. State the head-to-head SUT set explicitly and own the trade-off.

### [MINOR] W10 — "Matched recall" needs a pre-registered operating-point protocol.

Matching recall across a label-free oracle, a naive oracle, and an anomaly score requires a
threshold sweep that can be gamed at a single operating point. Report full PR curves with the
identical recall target, not a hand-picked point, and pre-register the selection rule.

---

## 4. Single most-likely rejection cause

**Circular / researcher-defined ground truth for the genuine-vs-benign label (W1), compounded by a
conditional contribution whose tie-breaking evidence is admitted-uncertain and below-bar (W2).**

On a review form this reads: *"The headline precision result is produced by a benign/genuine
partition defined by the same trace signals the oracle consumes; the anti-tautology design names but
does not neutralize this, and the injected-GT defense addresses root-cause labeling, not the
contested correctness call. The bug-finding evidence that could independently substantiate the
contribution is, by the authors' own statement, uncertain and — best case — far below the venue's
demonstrated bar. I cannot distinguish 'MIST's filter is right' from 'MIST's filter restates the
labeling rule.'"* That objection sinks even the §9 fallback, which is why it is the single most
likely cause rather than any one external-validity gap.

---

## 5. Questions to authors

1. **De-circularize W1.** What is your *independent* standard of intended behavior for the
   genuine-vs-benign call — applied without running MIST's own predicate? If none, why is E2 not a
   restatement of the labeling rule? Concretely: are benign/genuine labels assigned from span
   topology (circular with the filter) or from read-back state (needs an intended-behavior model
   you disclaim)?
2. Does the corpus contain benign cases your filter does **not** trivially suppress, and genuine
   cases your filter does **not** trivially fire on? Report the confusion matrix on *human-only*
   labels produced without reference to the rubric's MIST-shaped predicates.
3. **Operationalize W4.** Will you run a human/effort-controlled comparison against a *competently
   configured* Cast/Filibuster-DB (not a generic Tracetest assertion) and report bugs-found-per-unit-
   effort? Without it, how do you falsify "a competent human would have written that assertion"?
4. On how many SUTs does the **data-integrity** oracle (not the masked-5xx oracle) actually fire on
   a non-trivial mutable-state/compensation path? If it is one (TrainTicket), why is this not framed
   as a single-system case study?
5. Since Uber measures a performance-waste rate on natural traffic and you measure a correctness-
   masking rate on driven error paths, what exactly does the 29.35% comparison establish?
6. If B4 (FP control) stays blocked-by-data, on what basis is the *precision* headline computed under
   realistic load?

---

## 6. What would raise my score, and to what

- **To Borderline / Weak Accept:** Re-ground W1 with an independent intended-behavior standard
  (developer confirmation, documented saga specs, or version-diff oracles) applied blind to MIST's
  predicate, *and* show benign/genuine cases that are not trivially separable by the filter, *and*
  report PR curves (not a single matched-recall point). Add a competently-configured
  Cast/Filibuster-DB as a measured E2 comparator. This makes the precision frontier a real, non-
  circular result and turns the prevalence/benchmark into a credible empirical contribution even
  without the bug story.
- **To Accept:** The above, **plus** Gate 3 delivering convincingly — *multiple* real
  acknowledged-but-lost-write / missing-compensation defects across **≥2–3 SUTs** that a competently
  configured assertion-based tool, given comparable effort, demonstrably misses (developer-confirmed
  where possible). That would substantiate the "no human wrote the assertion" claim at more than
  anecdote scale and differentiate from Cast on evidence, not framing.
- **To Strong Accept:** The above at the bug-count/scale of the field's anchors (well into double
  digits, multi-SUT, with a released benchmark others adopt). The plan's own §9 concedes this is not
  the expected outcome; I agree.

**Bottom line.** A real idea, honestly presented, with good machinery and a useful artifact — but
the *guaranteed* paper's headline number is not yet de-circularized, the *novel* mechanism is an
admitted increment over very recent prior art (Cast/Filibuster-DB) likely shown on one SUT, and the
evidence that would break the tie is admitted-uncertain and below-bar. **Weak Reject**, with a
concrete and achievable path to acceptance if W1 and W4 are fixed and Gate 3 delivers at scale.
