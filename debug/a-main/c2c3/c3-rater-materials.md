# C3 rater materials — recruitment brief, consent, rubric packet, ballot, independence protocol

**Purpose:** the human-adjudication package for the C3 wild stratum (S3) + the M-yield cluster audit.
Pre-registered by plan v2 §3.1 (raters quantified: 2 MIST-blind labelers **+ 1 reserve** + a third
case-blind reader; 15–45 h each). This is the **longest-lead item** on the critical path.

**Draft status: rev 3** — folds the 3-cold rater review (`REVIEW-RATER-1-soundness`,
`REVIEW-RATER-2-adversarial`, `REVIEW-RATER-3-completeness` → `REVIEW-RATER-RECONCILIATION.md`). The
whole C3 precision claim rests on §0, so this packet is REJECT-until-hardened: **no rater is contacted
until the [SHIP] sections + §11 screen + §10 debrief + the `c2-freeze.md §6` amendments (below) are all
in place.** Two items remain genuine USER decisions (compensation, IRB filing — flagged inline).

> **Rubric ↔ freeze relationship (was falsely "VERBATIM-identical"; R-review F7).** The §3 rubric is
> **substantively identical** to `c2-freeze.md §3` **modulo the disclosed R6 strip** (the rater copy
> omits the paired-clean-run observation, per §0) **and the rater-facing adjustments enumerated in the
> `c2-freeze.md §6` amendment row dated 2026-07-09 (rater-rubric delta)** — the genuine-def
> success-shaped precondition is moved to a mechanical ballot field, an async tie-break is added, and a
> bundle-only rule is added. It is NOT a byte-for-byte copy; a `diff` will show these disclosed deltas.

---

## Rater hand-over manifest (BLOCK-4 fix — what a rater physically receives)
A rater receives, and ONLY receives: a rendering of **§1** (brief), **§2** (consent), **§3** (rubric,
with its `rubric_version` stamp), **§4** (ballot), **§9** (eligibility screen), the **§10 funneled
debrief** (at close only), plus per-case `case.md` + `ballot.yaml` (B4-harness output). Everything
tagged **[INTERNAL]** below — §0, §5-statistics, §6, §7, §8, §11, and the failure-rule half of §10 —
is ADMINISTRATOR-ONLY and is never shown to a rater. Section tags below are authoritative.

---

## §0 [INTERNAL] The blindness invariant
The C3 precision claim's soundness rests on raters being blind to the tool whose output they are
(unknowingly) validating. Two distinct blindnesses, and only one is load-bearing:
- **Per-case stratum blindness** (a rater cannot tell which stratum/detector a case came from) — this
  is what the *precision* claim actually rests on, and the design protects it (B4 normalization +
  strip-list + the pre-rating tell-audit gate + §11 screen).
- **Study-purpose blindness** (a rater does not infer a detector is being validated) — **fragile under
  the in-group channel and NOT fully restorable by screening**; an in-group grad may infer a detector
  exists. This is why §10 (debrief manipulation check) + the conservative-tie-break primary (§5, so a
  non-blind reader cannot inflate the headline) + honest threats-to-validity disclosure carry it, not a
  claim of perfect purpose-blindness.

Therefore **nothing in the [SHIP] materials names MIST, describes a detector, reveals that cases were
machine-flagged, or names the stratum structure.** Concretely:
- The set shown is a **normalized mix**: S3 wild-flagged + calibration (pre-known labels) + M-yield
  cluster-audit cases, in ONE common format. Stratum/detector is undecodable per case; sizes are stated
  to the rater ONLY as a single total (never broken out — the §1 brief must NOT name "wild/
  calibration/M-yield").
- **Clean-run normalization (R6).** Wild S3 / M-yield cases usually have no paired clean-run twin, so a
  clean-run column would separate calibration from measurement. Invariant: strip the paired clean-run
  from ALL rater-facing cases (default) so the field carries ZERO stratum signal; the rater judges the
  single observed run against docs/spec/source. Consequence disclosed at §5: stripping the twin can push
  a twin-decidable case to `underspecified` — this is why §5 pre-registers an underspecified bound.
- Each case carries ONLY: SUT identity + pinned version, the request sequence, the observed durable
  state (as the case presents it), and the pinned docs/spec/source bundle. NO tool verdict, trace,
  hypothesis label, "expected observable," or clean-run column.

---

## §1 [SHIP] Rater-facing recruitment brief
**What this is.** A paid, short-term labeling study on open-source microservice benchmarks. You will
review a **capped set of recorded system behaviors** and judge, for each, whether the behavior is a
genuine correctness defect, an intentional/by-design behavior, or not decidable from the available
documentation.

**Who we're looking for.** Software engineers with microservice literacy: comfortable reading
OpenAPI/REST specs, synchronous REST + asynchronous messaging + eventual consistency, and reading
application source (Java and Go). A short screening task (in `eligibility/`) confirms fit.

**The task, per case.** You are given (a) the system and its exact version, (b) the sequence of API
requests performed, (c) the system's response(s), and (d) the resulting observed durable state. Using
ONLY the **provided, version-pinned** documentation, OpenAPI/spec, and source bundle for that system,
you assign one label (full definitions in §3):
- **genuine defect** — the system acknowledged success but a durable effect it promises did not occur,
  and "it should have persisted/propagated" is derivable from the provided bundle.
- **by-design / benign** — the provided bundle establishes the behavior is acceptable.
- **underspecified** — the intended behavior for what you observed is NOT derivable from the bundle.

**Sole source of truth.** For each case use ONLY the provided version-pinned docs/spec/source bundle for
that system. **Do not consult the upstream or live repository, web search, or any other version** — the
live code may differ from the pinned version each case is bound to, which would make labels
irreproducible. The pinned bundle is the sole source of the norm.

**Time + pay.** ~15–45 minutes per case. You will rate roughly **18 cases** (your administrator
confirms the exact set, and may include a short warm-up set first; they will tell you the total). You
are paid **`[USER DECISION U1 — RATE]`** on a **per-hour** basis for the estimated
hours **regardless of the labels you produce** — there is no "right answer" we are steering toward, and
compensation does not depend on which labels you record.

**What you may consult, and only that:** the provided bundle. We are measuring your independent judgment
from the bundle alone; do not consult tools, traces, other people, or the web.

---

## §2 [SHIP] Consent + compensation statement
> I understand this is a paid labeling study on open-source microservice systems, run within my academic
> group. I will label each case using only the provided version-pinned documentation, specification, and
> source bundle for that system. **I attest that I am not a member of the group's microservice
> software-testing tooling project, and that I have not seen or discussed that tool, its hypothesis,
> or its results** (screening, §9/§11). I will not discuss the cases or my labels with any other rater,
> or with anyone in the group, until notified that the study has closed.
>
> I understand my **compensation does not depend on which labels I produce**, and that some cases have
> pre-known labels used only to check calibration (there is no single "correct" verdict I am steered
> toward on the measurement cases). I am paid `[USER DECISION U1 — RATE]` for the estimated
> `[USER DECISION U1 — HOURS]` hours.
>
> **Participation is voluntary.** I may withdraw at any time without penalty and may request that my
> labels to date be discarded; my participation, refusal, or withdrawal has **no effect on my academic
> standing, grades, funding, advising, authorship, or letters**. `[If compensation is course credit: an
> equivalent alternative assignment is available — U1.]`
>
> I understand the study's **full purpose will be explained to me at close** (a short debrief), and
> that I may withdraw my labels after that debrief. I consent to my anonymized labels and rationales
> being released as part of an open research artifact.

*(Administered by a non-author where available; the recruiter is not the students' direct advisor —
if unavoidable, disclosed in the IRB filing.)*

---

## §3 [SHIP] The rubric packet  `rubric_version: 3`
**Three-way label {genuine, benign, underspecified}.**
- **genuine defect** — the system **acknowledged the operation** (returned HTTP 2xx or otherwise
  signaled success) while a **durable write it promises did not land** — or a **downstream write in its
  causal closure did not land** (causal closure = any write the implementation performs as part of
  fulfilling this operation, per the provided source), **where this case presents the relevant durable
  state** — AND the intended "it should have persisted/propagated" **is derivable** from the provided
  docs/spec/source. *(The response body may look like success or may carry an error marker — either
  way, if the durable effect is missing and was contract-required, it is genuine. Record the body's
  marker mechanically in the ballot; it does not change the label.)*
- **benign** — the observed degradation is by-design / lived-with per the provided docs/spec/source.
- **underspecified** — the intended behavior is NOT derivable from the provided docs/spec/source.

**Asynchronous write paths (tie-break — apply before choosing genuine).** If the write path is
asynchronous (a `202 Accepted`, a queue, an "async"/eventually-consistent worker), judge the observed
absence against any **completion bound derivable from the bundle**: absence **past a documented bound →
genuine**; if **no** completion bound is derivable from docs/spec/source (so you cannot establish the
absence is permanent) → **underspecified**, not genuine. Each case gives the **relative** timing of the
observation, so you can compare it to any documented bound.

**Partial writes.** A write where the source states the operation is atomic but only part landed
(parent saved, child lost) → **genuine**. A partial write where neither docs nor source state whether
the operation is atomic → **underspecified**.

**If the case does not present the durable state a clause would need** (e.g. it references a downstream
write whose state is not shown), you cannot evaluate that clause → **underspecified**, never a guessed
genuine.

**Admissibility (observation-vs-norm split — R5):**
- **Admissible AS the OBSERVATION to be judged:** the case's own presented material — the request
  sequence, the response(s), and the observed durable state.
- **Sole source of the NORM (what SHOULD have happened):** the provided pinned docs, OpenAPI/spec, and
  source code — nothing else.
- **The observed transcript is the deployed system's ACTUAL output.** Ground the NORM in the bundle;
  do not down-weight a case because an observed response `msg`/`data` string differs cosmetically from
  a source literal — judge whether the *durable effect* the bundle promises is present or absent.
- **Inadmissible:** distributed traces, any external tool output, the live/upstream repository or any
  other version, and any runtime behavior beyond what the case presents.

**Worked examples (calibration-only; to be AUTHORED on real calibration cases and reviewed before
labeling — the abstract patterns below do not by themselves cover the hard async/partial shapes):**
- *genuine* — POST returns 201 with an order id; GET on that id 404s and no row exists in the service
  whose OpenAPI schema lists it as the system of record. (Contract-grounded "should persist".)
- *genuine, error-marked ack* — an endpoint returns HTTP 200 whose body carries an in-envelope
  failure sentinel (e.g. `{1,"error"}`); the source shows the call must persist a record, and a
  scoped read shows none. Acked (2xx), durable write missing, contract-required → genuine.
  (Record `ack_carries_failure_sentinel: yes`.)
- *benign* — a write returns `202 Accepted` and the durable effect appears only after an async worker
  cycle the docs bound to a stated window; observed absence within that documented window is by-design.
- *underspecified, async* — same async shape but the docs state no completion bound; absence at a finite
  time is not derivably permanent → underspecified.
- *underspecified, atomicity* — a partial write where neither docs nor source state whether the
  operation is atomic; the intended post-state cannot be derived.

---

## §4 [SHIP] The per-case ballot (what a rater records)
```yaml
rater_id: <your assigned opaque rater code>          # required — the analysis joins ballots by this
rubric_version: 3                                    # copy from the rubric packet header (§3)
case_id: <opaque id — you cannot decode any grouping from it>
label: genuine | benign | underspecified
ack_carries_failure_sentinel: yes | no | n/a         # MECHANICAL, not a judgment: does a 2xx-SUCCESS
                                                     # response body carry a failure marker (-1,
                                                     # {1,"error"}, a negative id)? read it off the body.
                                                     # n/a = the ack is NOT a 2xx success (nothing was
                                                     # acknowledged as done — e.g. an HTTP 4xx/5xx).
grounding:                       # REQUIRED for genuine/benign
  citation: <doc-url+version | spec-path+operation | source-file:symbol — INSIDE the provided bundle>
  quote_or_ref: <the clause/signature that grounds the label>
missing_norm: <underspecified ONLY: state exactly what the docs/spec/source do not say>
confidence: high | medium | low  # used ONLY in a sensitivity analysis (labels excluded if low); never primary
rationale: <2–4 sentences: what was promised, what was observed, why the label follows>
time_minutes: <int>
```
Submit each ballot via the channel named in your assignment email (separate return; do not share).

---

## §5 [INTERNAL] Agreement statistics, adjudication, and the reliability decision rule
**Raters.** Exactly **2 labelers + 1 reserve** (the reserve doubles as the fresh relabeler for a rubric
iteration — §6). A **third reader** adjudicates; see the conservative-tie-break rule below.

**Three distinct κ's — enumerated so none is chosen post-hoc:**
1. **Calibration-gate κ** — over the calibration round only; governs the ≤2 rubric iterations (§6).
2. **S3-only κ — PRIMARY** (the headline reliability number, R7).
3. **Pooled calibration+S3 κ — SECONDARY** small-n-stability figure, carrying the **calibration-inflation
   caveat** (calibration cases are the easy, rubric-tuned cases → upward-biased).

**Estimator (re-pinned; the rev-2 re-freeze dropped this — R3 root-cause).** **Cohen's unweighted κ**
for exactly 2 labelers; **Fleiss' κ** if a 3rd labeler is ever used; labels are **nominal, unweighted**;
κ is computed over the **full 3-category** space {genuine, benign, underspecified}. The
underspecified→precision exclusion applies to the **precision denominator only, never to κ.** κ CI by
**bootstrap BCa**; at S3 n<10 **withhold κ** and report raw agreement + a Clopper–Pearson interval on
the agreement proportion. Report κ **and** a prevalence-adjusted coefficient (PABAK / Gwet's AC1)
always; **AC1 is the headline when any single label's prevalence > 0.70** (the benign-dominance regime
where κ's base-rate paradox bites), κ otherwise — neither substituted after seeing the split. CI units =
distinct defect/fault-sites, not flagged events.

**M-yield audit κ (F12).** The M-yield cluster-audit cases are unknown-truth like S3, so they join the
**measurement κ** with S3 (headline = S3+M-yield-audit; S3-only also reported). M-yield disagreements
resolve through the same third reader.

**Adjudication — conservative tie-break PRIMARY (F6).** The in-group pool has no *guaranteed* MIST-blind
senior, so instead of resting the headline on a possibly-tool-aware adjudicator: **any inter-rater
disagreement that involves the `genuine` label resolves to NOT-genuine for the headline precision.** The
third reader's adjudicated resolution is reported as a **SECONDARY (upper-bound)** figure. The third
reader is **case-blind** (sees both ballots + the same admissible evidence only, never a tool verdict)
and **blind to rater identities**; tool-blindness is not required of them because they no longer author
the primary number. The frozen "blind-adjudicated wild stratum" claim string is updated accordingly
(→ "conservative-tie-break primary; case-blind adjudicated secondary"). The third reader **audits ALL
agreed-`underspecified` cases** (they otherwise exit the denominator unseen — F18); a fast-underspecified
time audit flags any rater whose underspecified calls cluster at low `time_minutes`.

**Reliability decision ladder (pre-registered NOW, on the PRIMARY S3-only κ — F5).** Under the plan's own
benign-dominance prior the central expectation is S3-only κ ≈ 0.4–0.5, so a low value is not a failure to
scramble over post-hoc:
- **κ ≥ 0.6** → full reliability register.
- **0.4 ≤ κ < 0.6** → **demoted register**: all ballots released, conservative-tie-break primary already
  in force, adjudicated secondary, AC1 reported (not substituting for κ), disagreement-dense cases tabled.
- **κ < 0.4** → **no reliability claim**; §8 fallback framing.

**Underspecified bound (F19).** If `underspecified` > **30% of S3**, the underspecified fraction is
**promoted to a headline finding** and qualifies the precision sentence in the abstract (the R6 clean-run
strip can manufacture underspecified — this bounds and discloses it rather than hiding it).

**Independence [SHIP excerpt → included in the assignment email]:** the 2 labelers work independently, no
discussion channel between them, and **no discussion of the study, the cases, or these systems with
anyone in the group** until close (separate delivery, separate return; co-rater identities are not
revealed). **Quiet period (binds the TEAM, F24):** no author discusses the study, the cases, or
tool-adjacent results with any rater until close.

---

## §6 [INTERNAL] The κ-calibration round (runs first)
- **Calibration set sizing — adaptive (F8/M1).** The pooled-≥50 guarantee must survive the pre-registered
  S3-scarcity branch (S3 can be < 20 — "scarcity is the finding"). So set **calibration size =
  max(30, 50 − |S3|)**, decided at assembly once |S3| is known and recorded in the sealed manifest (with
  S1+S2 ≥ 80 the material is available). This keeps pooled ≥ 50 even when S3 is scarce.
- **Calibration mix — pre-registered benign-skewed (F16).** Draw calibration from S1 positives + S2
  benign at a **fixed ≥ 2:1 benign:genuine ratio** (matching the S2:S1 corpus reality and the
  benign-dominance prior), so the interleaved calibration cases do not teach an inflated genuine
  base-rate. The ratio is fixed in advance and disclosed at debrief.
- **Known-label bias audit (F17).** Because the 30 calibration labels are known, compute **per-rater
  confusion matrices vs the known labels** and a **directional false-genuine rate on known-benign cases**;
  propagate that rate into a **pre-registered sensitivity band on the S3 precision CI**.
- Calibration is labeled first; the calibration-gate κ is computed.
- **κ-gate (frozen):** if calibration-gate κ < 0.6, at most **TWO** rubric-iteration rounds, CALIBRATION
  CASES ONLY (no S3 peeking). After any iteration, relabel ALL prior cases under the final rubric
  (`rubric_version` bumped; the reserve rater is the fresh relabeler). Calibration cases are NOT reused as
  S3 measurement cases.

---

## §7 [INTERNAL] Recruitment channel — in-group SE grad students (user-decided 2026-07-09)
Raters = software-engineering grad students from our own group (skill-fit + ~0 recruitment lead).
Admissible ONLY under these BINDING conditions (they protect §0):
- **MIST-blind is mandatory, screened by the §11 instrument** (not the aspirational text this once was).
  A student on the tool project, or who has seen it/its hypothesis/its results, is INELIGIBLE as a
  labeler (may help with logistics only). Screening is recorded and retained.
- **In-group ⇒ disclosed threat to validity.** The paper discloses "internal but MIST-blind labelers;
  per-case stratum blindness enforced by B4 normalization + tell-audit; independence + quiet-period by
  protocol (§5); study-purpose blindness imperfect and bounded by the §10 debrief manipulation check +
  the conservative-tie-break primary + the known-label bias audit." This is the honest, defensible form.
- **Third reader = most senior + most arm's-length available;** case-blind + rater-identity-blind (§5).
  If tool-blindness for the third reader is infeasible, it is disclosed that adjudication is **case-blind
  only**, and a sensitivity analysis (headline recomputed with all adjudicated cases held out) is
  pre-registered — but note the conservative-tie-break primary already prevents them from authoring the
  headline.
- **If genuinely-blind in-group students cannot be staffed → the §8 fallback** (do NOT relax blindness to
  fill seats).

Channel comparison (on record for the priority defense; chosen = **SE grad students**):
| channel | skill fit | lead time | cost | independence risk |
|---|---|---|---|---|
| Contract SWEs (Upwork/Toptal, microservice-screened) | good if screened | 1–3 wk | market hourly ×2 raters ×~30 h | low (strangers) |
| **SE grad students (our group) — CHOSEN** | good | ~0 (in-house) | `[U1: stipend or credit]` | medium (in-group — mitigated by §11 screen + §5 + §10 + disclosure) |
| Industry SRE/backend contacts | very good | 1–4 wk | favor/honorarium | medium (relationship bias) |
| Prolific/MTurk | poor (needs source-reading) | days | low | low but skill floor likely fails eligibility |

**IRB / ethics — a PRECONDITION before any rater is CONTACTED (F22, was "before labeling").** Recruitment,
screening, and consent of human subjects are themselves IRB-covered, so the determination (approval OR a
documented exemption) must be on file **before first contact**; the in-group choice removes the outreach
lead but NOT this precondition. **Expected exemption rationale (to file):** expert code/spec review of
PUBLIC open-source systems; **no sensitive personal data** (screening records, eligibility outcomes,
per-case `time_minutes`, and payment records ARE person-linked and retained — so the claim is "no
sensitive personal data; only anonymized labels + rationales are released," not "no personal data");
typically exempt (e.g. US 45 CFR 46.104(d)(2)). `[USER DECISION U2: the filing itself is institutional.]`

## §8 [INTERNAL] Fallback (two-author-blind — pre-committed scars)
Triggers if recruitment fails by the step-5 gate, OR the §10 debrief fails for both labelers, OR primary
S3-only κ < 0.4 (the ladder's bottom rung). Then, as pre-committed: (i) the C3 precision claim is demoted
one register in the ABSTRACT; (ii) all label evidence is released for community re-adjudication; (iii)
author-pair κ is reported. A fallback, not a plan — §7 recruitment starts now to avoid it.

## §9 [SHIP] Eligibility screen (≤20 min, before the paid work — unpaid, stated up front)
Two calibration-style cases with unambiguous ground truth (one clear genuine, one clear benign) + a
2-question spec-reading check (given an OpenAPI snippet, identify the system-of-record service for a
field; given a source method, state whether it persists). Pass = both cases + both check questions
correct. Gates the skill floor without revealing the study's purpose. Administered at ASSIGNMENT (it
needs the two eligibility cases to exist — checklist §1.95.2), after the §11 blindness screen. **These two
eligibility cases are DISJOINT from the §6 calibration cases and from all S3/M-yield cases** — a rater
never sees them again, so they cannot bias κ or the measurement.

## §10 Debrief + manipulation check (§10a [SHIP] at close · §10b [INTERNAL] failure rule)
**§10a [SHIP] — administered at study close, before any rater may discuss the study.** A funneled exit
question set, asked in order, answers recorded verbatim:
1. "In your own words, what do you think this study was about?"
2. "Do you believe a software tool produced or selected these cases?"
3. "If yes, can you name the tool or project?"
Plus a close-out attestation: "I did not discuss the cases or my labels with anyone during the study."
The full purpose is then explained (a detector's outputs were being validated; why blindness was
required), and the rater may withdraw their labels post-debrief.

**§10b [INTERNAL] — pre-registered failure rule.** A rater who **names the tool/hypothesis** at Q3 ⇒
disclosed blindness failure + a sensitivity analysis excluding that rater's ballots. **Both** labelers
fail ⇒ §8 fallback register. Debrief transcripts are retained and released (anonymized) with the artifact.

## §11 [INTERNAL] MIST-blindness screening instrument (the §7 mandatory screen, operationalized)
Administered by a non-author where available, BEFORE assignment; outcomes recorded + retained as a study
record and cited in threats-to-validity. Naming the tool is avoided so the screen does not itself leak it.
- **Objective checks (no student contact):** not a contributor/collaborator on the tool repo(s) or paper
  drafts (repo ACL / git history); not on the project's channels/meeting rosters; advisor attests the
  student has not attended project-specific talks.
- **Indirect self-report (does not name the tool):** "List the group's research projects you can describe
  by topic" (any microservice fault/oracle/testing-tool mention ⇒ ineligible); "Have you read or discussed
  results comparing testing tools on TrainTicket / Sock Shop?" (yes ⇒ ineligible); "Which group
  repositories have you cloned or browsed?" (the tool repo ⇒ ineligible).
- **Decision rule:** any hit ⇒ ineligible (logistics-only). Borderline (e.g. "saw a title slide once") ⇒
  ineligible — err toward exclusion; §8 is the relief valve, not blindness relaxation.
- **Reserve pool:** screen ≥ 2 extra passing students at recruitment time (the blind pool is small and the
  §10 failure rule can shrink it; a reserve labeler is required by §5).
