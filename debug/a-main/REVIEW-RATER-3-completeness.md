# REVIEW-RATER-3 — Operational completeness & analysis-plan validity

**Reviewer lens:** end-to-end executability from the packet alone · label-rubric determinacy ·
analysis-plan completeness/consistency · sample-size coherence · ballot↔analysis field match.
**Artifacts read:** `c2c3/c3-rater-materials.md` (target), `c2c3/c2-freeze.md`,
`c2c3/c3-case-corpus-plan.md`, `c2c3/c3-rater-materials-中文版.md`, `c2c3-execution-plan.md`, and the
superseded prototype rubric `benchmark/schema/rubric.md` (for what the re-freeze dropped). I did not
read the sibling `REVIEW-RATER-*` files, to keep this cold/independent; where my findings happen to
overlap theirs, they were reached independently from the primary sources.

---

## VERDICT: **REJECT-AS-WRITTEN**

Four BLOCKING defects reach the rater on first contact and would make the labeling task either
impossible to complete deterministically or actively study-corrupting. Two of the four sit in the
**frozen rubric** (`c2-freeze.md §3`), which `c3-rater-materials.md:87` binds to be VERBATIM-identical
to the packet's §3 — so fixing them requires a disclosed `c2-freeze.md §6` amendment plus a mirror
edit, not a packet-only patch. The analysis plan additionally has enough unspecified/contradictory
steps (κ variant, underspecified's role in κ, primary-κ decision rule, adjudicator blindness, a stale
pooled-primary pre-registration) that κ as computed today is not fully pre-registered. This is fixable
but is more than a wording pass, hence REJECT rather than ACCEPT-WITH-CHANGES.

Root pattern worth flagging to the reconciliation: the rev-2 re-freeze (`c2-freeze.md:252`, R2
supersession of `benchmark/`) folded R1–R8 but **silently dropped three operational specs the
superseded rubric carried** — an async-vs-lost-write disambiguation rule (`benchmark/schema/rubric.md:45-50`),
a pinned **Cohen's** κ (`:55`), and per-case **rubric-version + rater** recording (`:56`). None of the
three drops appears in the `c2-freeze.md §6` amendments log. Three of my findings below (BLOCKING-2,
MAJOR-1, MAJOR-4) are exactly these regressions.

---

## BLOCKING

### BLOCKING-1 — The genuine label is unassignable to a tell-bearing case; the rubric's own central exhibit has no valid label
**Anchor:** `c3-rater-materials.md:90-101` (rater-facing genuine def + sentinel rule); mirrored in the
frozen `c2-freeze.md:154-170`; Chinese mirror `c3-rater-materials-中文版.md:74,79-81`.

The rater-facing **genuine** definition requires *"HTTP 2xx **with a success-shaped body — see the
sentinel rule**"* (`:90`), and the sentinel rule (`:98-101`) states a body carrying `{1,"error"}` /
`-1` / a negative id is **NOT success-shaped** and *"is tell-bearing and is tracked separately …, not
in the primary discriminating denominator."*

Concrete failure: a rater receives a case that is a **real acked-but-lost fault whose 2xx body carries
`{1,"error"}`** and whose durable write is absent with docs saying it should persist. This is not
hypothetical — `c2-freeze.md:168-170` names it as the executed TT-natural exhibit. Following the letter
of the rubric, the rater:
- cannot label it **genuine** (the body is not success-shaped, so the genuine definition's precondition
  fails);
- cannot label it **benign** (the loss is a real defect, not by-design);
- cannot label it **underspecified** (the norm *is* derivable — docs say it should persist).

The rater has no fourth label and no "tracked separately" bucket (buckets are an internal analysis
concept they never see, per §0 blindness). Careful raters will resolve this impasse differently → κ
noise; worse, tell-bearing genuine defects get mislabeled benign/underspecified → corrupted ground
truth. The R8 sentinel/segregation machinery is an **analysis-side denominator concern** that leaked
into the **rater-facing label definition**.

**Fix:** strip the "success-shaped body — see the sentinel rule" clause from the *rater-facing* genuine
definition. Rater genuine = "the system returned a 2xx / otherwise acknowledged success, the promised
durable write (or a downstream write in its causal closure) is absent, and the norm is derivable from
docs/spec/source." Apply the sentinel/tell-bearing segregation **analytically** from the captured
response body (which is already in the case + ballot), not by the rater. Because of the R5
verbatim-identity bind (`c3-rater-materials.md:87`, `c2-freeze.md:152`), make this a disclosed
`c2-freeze.md §6` amendment and mirror it in both the English and Chinese packets.

### BLOCKING-2 — No rule disambiguates "eventually-consistent, undocumented window" from a lost write; the benchmark's flagship async class is a coin-flip
**Anchor:** rubric `c3-rater-materials.md:90-93` (genuine = "durable write did not land") vs `:115-116`
(benign example *requires* a **documented** window). Regression from `benchmark/schema/rubric.md:45-50`.

The **benign** worked example is scoped to *"an async worker cycle **documented** as
eventually-consistent … observed absence **within the documented window** is by-design."* The rubric
gives **no rule** for the very common shape: an **asynchronous** write path where the docs do **not**
state a completion bound, observed absent at the (single, clean-run-stripped — `:28-34`) observation.
For that shape a careful rater can defensibly pick any of the three:
- **genuine** — "the durable write did not land" (`:90-91` reads literally satisfied);
- **benign** — "it's an async worker, it'll converge";
- **underspecified** — "no window is documented, so I can't derive whether this absence is permanent."

This is not an edge case: the swallowed-enqueue / Kafka-accounting async classes are core to this very
benchmark (`c2-freeze.md:148`, `:234`; execution plan §3.2). Because §0 **strips the paired clean-run**
(`c3-rater-materials.md:28-34`), the rater cannot even distinguish "not yet propagated" from
"permanently lost" empirically — they have only the rubric to route on, and the rubric is silent. Two
careful raters *will* diverge here; on an S3 set dominated by async cases, the primary S3-only κ is
measuring rubric-underdetermination, not rater reliability.

Note the project already solved this and dropped it: the superseded rubric
`benchmark/schema/rubric.md:45-50` had an explicit "async writes must not be mistaken for lost writes …
if still changing at timeout, label `inconclusive` and exclude" protocol. The re-freeze did not carry a
rater-appropriate form of it forward.

**Fix (rater-appropriate, mirrors the old `inconclusive` → new `underspecified`):** add to §3 a
determinate tie-break, e.g. *"If the write path is asynchronous, judge against any documented
completion bound. If the observed absence is past a documented bound → genuine. If **no** completion
bound is documented (so you cannot establish the absence is permanent) → **underspecified**."* The
case's relative observation delay is already retained (`c3-case-corpus-plan.md:92` "relative durations
KEPT"), so the rater has the timing input needed. R5 → apply to `c2-freeze.md §3` + both mirrors.

### BLOCKING-3 — The "use only the provided, version-pinned bundle; no web search" rule never reaches the rater; raters will self-source different source versions and deterministically disagree
**Anchor:** rule present ONLY in `c3-case-corpus-plan.md:90-92` (M6); **absent** from the rater-facing
packet. Packet §3 admissibility (`c3-rater-materials.md:103-108`) names the norm sources as *"docs,
OpenAPI/spec, source code"* with **no** provided-bundle-only / pinned-version / no-external-search
restriction; §1's "what you will NOT be shown" (`:71-73`) is only about tool output/traces/hypotheses.

Concrete failure: two raters open the SUT's live GitHub `HEAD` to "read the source," land on a version
where the defect is **fixed** (or the behavior documented differently) than the pinned digest the case
is bound to (`c2-freeze.md:118` `version_validity.image_digest`), and label the *same* case
differently. Version-pinning is load-bearing for the entire benchmark; letting raters self-source both
breaks admissibility and injects deterministic κ divergence. It is also an executability gap — a rater
reading only the packet does not know a curated bundle exists or that they must confine themselves to
it.

**Fix:** add a rater rule to §1 and §3: *"For each case, use ONLY the provided version-pinned
docs/spec/source bundle. Do not consult the upstream/live repository, web search, or any other version;
the pinned bundle is the sole norm."* Mirror in `c3-rater-materials-中文版.md`.

### BLOCKING-4 — The packet does not define what the rater physically receives; its only boundary statement (§0: "§1–§6") sweeps the measurement-structure sections into the rater's hands
**Anchor:** `c3-rater-materials.md:16` declares *"the rater-facing materials (§1–§6)"*; but §5 and §6
describe the study's measurement structure — S3-only vs pooled κ and the *"calibration-inflation
caveat (calibration cases are the easy, rubric-tuned cases)"* (`:140-143`), the calibration-vs-S3 split
and *"no S3 peeking"* iteration (`:148-156`). The actual rater deliverable per `c3-case-corpus-plan.md:80-82`
is `case.md` + `ballot.yaml`, i.e. a **rendering** of parts of this document — but the packet never says
which parts.

Concrete failure: an administrator who follows the packet literally hands a rater §1–§6, which reveals
that some cases carry known labels (calibration) while others are the real measurement, that agreement
is being scored, and that a hidden stratification exists — directly contradicting §0's own invariant
that *"a rater cannot tell which stratum a case is in"* (`:26`). The packet conflates "the design
document" with "the rater packet" and provides no hand-over manifest. (Blindness-leak severity is the
leak reviewer's call; the **operational** defect — an undefined/self-contradictory hand-over boundary —
is squarely a completeness failure and blocks safe first contact.)

**Fix:** add an explicit rater-hand-over manifest, e.g. "the rater receives: a rendering of §1 (brief),
§2 (consent), §3 (rubric), §4 (ballot), §9 (eligibility), plus per-case `case.md` + `ballot.yaml`.
§0, §5-statistics, §6, §7, §8 are ADMINISTRATOR-ONLY and never shown to a rater." Correct the `:16`
"§1–§6" boundary accordingly.

---

## MAJOR

### MAJOR-1 — Which κ is unspecified: Cohen vs Fleiss vs weighted, across all three pre-registration docs
**Anchor:** `c3-rater-materials.md:137` ("≥2 independent raters") + `:140` ("κ"); `c2-freeze.md:182`
("κ"); `c2c3-execution-plan.md:156` ("κ"). The superseded rubric pinned **Cohen's** κ
(`benchmark/schema/rubric.md:55`); the re-freeze dropped the pin (unlogged) and left bare "κ" with a
rater count of "≥2." Cohen's (pairwise, exactly 2 raters) and Fleiss' (≥3) are different estimators;
"≥2" makes the choice indeterminate, and weighting is unstated (the three labels are unordered-nominal,
so unweighted is presumably intended — but say so). A pre-registration that does not name its estimator
leaves a post-hoc degree of freedom.

**Fix:** pin it: e.g. "exactly 2 raters → Cohen's unweighted κ; if a 3rd labeling rater is ever used,
Fleiss' κ; labels are nominal, unweighted." Reconcile the same statement into `c2-freeze.md §3` and
`c2c3-execution-plan.md §3.1`.

### MAJOR-2 — Underspecified's role in κ is unspecified (3-way category vs collapsed/excluded)
**Anchor:** `c3-rater-materials.md:94-96,138-139`; `c2-freeze.md:159-161`. Every doc says underspecified
is *"excluded from the primary **precision** denominator"* and that a disagreement about
underspecified-ness "goes to the third adjudicator like any other." Neither statement tells you whether
the **κ agreement** is computed over **three** categories (genuine/benign/underspecified) or **two**
(underspecified dropped/collapsed). These yield materially different κ. The "excluded from the precision
denominator" phrasing is easy to misread as "excluded from κ." The brief flags this explicitly and it
is unresolved in the artifacts.

**Fix:** state it once, in §5: "κ is computed over the full 3-category label space
{genuine, benign, underspecified}; the underspecified→precision exclusion applies only to the precision
denominator, not to κ." Mirror to `c2-freeze.md §3`.

### MAJOR-3 — Stale pre-registration: the execution plan still says pooled-primary while the two frozen docs say S3-only-primary
**Anchor:** `c2c3-execution-plan.md:156-158` — *"κ computed over **pooled calibration+S3** (n ≥ 50),
reported with its CI …"* (no S3-only-primary) vs `c2-freeze.md:182` and `c3-rater-materials.md:140`
(*"S3-only κ as PRIMARY"*, R7). The execution plan bills itself as *"the executable pre-registration;
deviations from it are disclosed amendments"* (`c2c3-execution-plan.md:6-7`), yet the R7 change (logged
in `c2-freeze.md:258`) is not reflected there and is not logged as a deviation. Two pre-registration
documents disagreeing on the **headline metric** is precisely the decision-rule-before-data violation
the brief warned about — it lets the headline κ be chosen post-hoc.

**Fix:** update `c2c3-execution-plan.md §3.1` to "S3-only κ PRIMARY; pooled calibration+S3 κ SECONDARY
(calibration-inflation caveat)," matching R7, or add an amendment row pointing to R7.

### MAJOR-4 — Ballot omits `rater_id` and `rubric_version`; both are required by the analysis and by the §6 relabel protocol
**Anchor:** ballot `c3-rater-materials.md:122-131` collects `case_id`, `label`, `grounding`,
`confidence`, `rationale`, `time_minutes` — no `rater_id`, no `rubric_version`. The superseded rubric
required *"the rubric version and rater count … recorded per case"* (`benchmark/schema/rubric.md:56`);
the re-freeze dropped it (unlogged).

- `rater_id`: κ (pairwise or Fleiss) cannot be computed without knowing which ballot belongs to which
  rater; relying on the out-of-band "separate return" envelope (`:137`) is fragile and unauditable.
- `rubric_version`: §6 permits **up to two rubric iterations with full relabeling** (`:153-156`). If a
  ballot does not stamp the rubric version it was produced under, v1 and v2 labels can be silently mixed
  into one κ. The sealed manifest holding the rubric version (`c3-case-corpus-plan.md:82`) is per-corpus,
  not per-ballot, so it cannot disambiguate ballots produced across an iteration boundary.

**Fix:** add `rater_id` (an opaque rater code — not leaky) and `rubric_version` to the §4 ballot.

### MAJOR-5 — Rated-set size is internally inconsistent; "≤60 cases" contradicts the corpus sizing and the 15–45 h estimate
**Anchor:** `c3-rater-materials.md:44` ("up to 60"), `:66` ("≤60 cases ≈ 15–45 hours") vs `:150`
("~30 calibration") and the S3 rule min(all flagged, **40**) (`c3-case-corpus-plan.md:16`,
`c2c3-execution-plan.md:181`) plus the M-yield audit sample (`c3-case-corpus-plan.md:17`). 30 + up-to-40
= 70 **before** M-yield; the packet's own §6 "~30 calibration + expected S3 ≥ 20" (`:150`) already
implies 50+ pre-M-yield. At the stated 45 min/case, 70 cases = ~52.5 h, exceeding the "≤ 45 h" ceiling
in §1. Pay is protected (per-hour basis, `:68`), but the case count and hour ceiling set false rater
expectations and understate the consent-relevant workload.

**Fix:** make §1/§2 consistent with the corpus plan — either raise the cap (e.g. "up to ~90 cases; the
per-hour pay scales") or bind S3 lower; and reconcile the internal "~20 calibration" (`:25`, and Chinese
`:25`) with "~30 calibration" (`:150`, Chinese `:121`).

### MAJOR-6 — The M-yield audit labels have no reliability metric; they fall outside both κ definitions
**Anchor:** the rated set includes the M-yield cluster-audit sample (`c3-rater-materials.md:25-27`,
`c3-case-corpus-plan.md:17`), and its labels feed the M-yield precision "genuine/(genuine+benign)"
(`c2c3-execution-plan.md:165`). But §5's κ is **S3-only** (primary) and **calibration+S3** (secondary)
(`:140-143`); §6's ordering mentions only calibration then S3 (`:152`). M-yield audit is in **neither**
κ. So the reliability of the labels underpinning the M-yield yield claim is unspecified.

**Fix:** define an M-yield-audit κ (or explicitly fold M-yield into the pooled/measurement κ and say so),
and state that M-yield disagreements resolve through the same adjudicator.

### MAJOR-7 — Adjudicator blindness is unspecified, and §7 actively selects toward a tool-aware adjudicator who then sets every contested final label
**Anchor:** `c3-rater-materials.md:138-139` (adjudicator "resolves every disagreement," "sees … the
same admissible evidence only") + `:173` ("most senior + most arm's-length available"). The mandatory
MIST-blind screen is written only for *students/raters* (`:165-167`); it is **not** extended to the
adjudicator, and "most senior available" in a group that built MIST points squarely at a tool-aware
person. Because the adjudicator sets the final label on **every inter-rater disagreement**, and those
final labels populate the precision denominator (`c2-freeze.md §4` scoring on `label.value`), the
contested subset of the "**blind-adjudicated** wild stratum" (frozen claim string, `c2-freeze.md:15`)
would be decided **non-blind**. The packet never says which sense of "blind" the adjudicator satisfies:
case-blind (doesn't see the tool's verdict on the case — guaranteed by `:139`) vs tool-blind (ignorant
of MIST/its hypothesis — the raters' bar, not guaranteed here).

**Fix:** pin the adjudicator's blindness level and reconcile it with the "blind-adjudicated" claim. If
tool-blindness for the adjudicator is infeasible (senior staffing), disclose that adjudication is
case-blind only, and pre-register a sensitivity analysis (headline κ and precision recomputed with all
adjudicated cases held out) so the claim does not silently rest on non-blind final calls. (Must be
resolved before adjudication, not before first rater contact — hence MAJOR, not BLOCKING.)

### MAJOR-8 — No decision rule for a low PRIMARY κ; the κ-gate governs a different κ than the headline
**Anchor:** §6 gate (`c3-rater-materials.md:152-156`) fires on the **calibration-round** κ
(*"Calibration is labeled first; κ is computed. κ-gate … if κ < 0.6 …"*), whereas the **headline** is
the **S3-only** κ (`:140`). Three distinct κ's are in play — calibration-gate, S3-only-primary,
pooled-secondary — but the packet never enumerates them, and there is **no pre-registered consequence**
if the *primary* S3-only κ comes out low after calibration passed the gate. So the headline reliability
number could be poor with no stated interpretation, defeating the point of pre-registration.

**Fix:** enumerate the three κ's explicitly; and pre-register the interpretation/decision rule for a low
**primary** (S3-only) κ (e.g. how it is reported, whether it demotes the C3 claim), independent of the
calibration gate.

### MAJOR-9 — κ-vs-PABAK/AC1 primacy is unstated under the plan's own pre-registered benign-dominance branch
**Anchor:** `c3-rater-materials.md:143-145` reports κ "with … a prevalence-adjusted coefficient (PABAK /
Gwet's AC1)" **alongside**, with no rule for which leads when they diverge. The execution plan
pre-registers a **benign-dominance** outcome as load-bearing (`c2c3-execution-plan.md §1`), i.e. a
high-prevalence regime where the base-rate paradox suppresses κ even at high raw agreement — exactly
when κ and AC1 diverge most. Choosing the favorable coefficient after seeing the split is a researcher
degree of freedom.

**Fix:** pre-register the decision rule (e.g. "PABAK/AC1 is the headline when one label's prevalence
exceeds X%; κ otherwise; both always reported"), stated before data.

### MAJOR-10 — The genuine "downstream write in its causal closure" clause may be unevaluable from what the case presents
**Anchor:** genuine def `c3-rater-materials.md:90-92` ("or a downstream write in its causal closure did
not [land]") vs §0/§1 which give the rater *"the resulting observed durable state"* (singular, the
primary — `:37,:55`). To apply the causal-closure clause the rater needs the **downstream** service's
durable state to be observed and presented; the packet does not guarantee the case surfaces it. If it is
absent, the rater cannot evaluate that clause and may false-negative a genuine downstream loss, or route
to underspecified. Related boundary: `partial-aggregate`/`transition` writes (`c2-freeze.md:90`) sit
between the causal-closure clause (→ genuine) and the underspecified atomicity example (`:117`) with no
tie-break.

**Fix:** guarantee (in the B4 harness contract) that every case presents each durable-state observation
the causal-closure clause references, or scope the clause to "durable state presented in this case"; and
add a partial-write tie-break to §3.

---

## MINOR

- **MIN-1 — `confidence` collected but unused.** Ballot `c3-rater-materials.md:128` records confidence;
  no analysis step consumes it (contrast `time_minutes:130`, whose use is stated). Either specify its
  role (e.g. a sensitivity analysis excluding low-confidence labels) or drop it.
- **MIN-2 — κ CI method unspecified at small n.** §5 reports "CI" on κ (`:144`) and Clopper–Pearson for
  counts, but does not say how the κ CI itself is formed (analytic SE vs bootstrap). Analytic κ SE is
  unreliable at the S3-scarce n the plan expects; name bootstrap (BCa) explicitly.
- **MIN-3 — Submission/return mechanics absent from the rater packet.** §5 says "separate delivery,
  separate return" (`:137`) but the rater is never told where/how to submit ballots or in what bundle.
  Can be a cover email, but as written a rater must ask.
- **MIN-4 — Two-rater variance not disclosed.** With exactly 2 raters (the "≥2" floor), the reliability
  estimate itself is high-variance on a single rater pair; worth a one-line threats-to-validity note.
- **MIN-5 — Worked examples are schematic placeholders.** `:110` ("to be authored on real calibration
  cases"). Fine as a plan, but the rubric's determinacy leans heavily on them; the real examples should
  be reviewed before rating, since BLOCKING-1/2 show the abstract patterns don't cover the hard shapes.

---

## VERIFIED-CORRECT (checked and sound — reconciliation need not touch)

- **S3-only-primary framing is consistent** between the packet (`c3-rater-materials.md:140`) and the
  freeze (`c2-freeze.md:182`), and matches the checklist (`step2-execution-checklist.md:158`). The
  stale doc is only the execution plan (MAJOR-3) — the packet↔freeze pair is aligned.
- **Pooled-≥50 sizing is coherent and honestly caveated.** ~30 calibration + expected S3 ≥ 20 given
  S1+S2 ≥ 80 makes pooled ≥ 50 free (`c3-rater-materials.md:148-151`, `c2-freeze.md:184`), and the
  calibration-inflation caveat on the pooled figure is stated (`:142-143`). The math and the disclosure
  are right.
- **Contamination controls are correct.** Eligibility cases are disjoint from calibration and all
  S3/M-yield cases (`:206-207`, m7); calibration cases are not reused as S3 measurement cases (`:155`).
  Both prevent "seen-it-before" leakage into κ.
- **Disagreement → adjudicator → single final label pipeline is specified** (`:138-139`) and maps to the
  schema (`c2-freeze.md:120` `adjudication_record`). Sound modulo the adjudicator-blindness gap
  (MAJOR-7).
- **Underspecified accounting is internally consistent** with the freeze: excluded from the primary
  precision denominator, fraction reported, precision reported both ways (`:94-96`;
  `c2-freeze.md:203-206`). (Its role in **κ** is the separate gap — MAJOR-2.)
- **CI units = distinct defect/fault-sites, not flagged events** is stated consistently across all three
  docs (`c3-rater-materials.md:145`, `c2-freeze.md:186`, `c2c3-execution-plan.md:158`).
- **IRB/ethics is correctly gated as a precondition before labeling** (`:186-193`), independent of the
  in-group channel choice. (Ethics wording is another reviewer's lens; the process gate itself is
  present and correctly ordered.)
- **Consent statement carries the operationally load-bearing terms** — pay regardless of labels, no
  discussion until close, anonymized open release (`:78-83`).
- **The Chinese mirror does not leak more than the English on the operational axis** — it faithfully
  reproduces §0–§9 including the same defects (e.g. the sentinel clause `中文版:74,79-81`, the ~20/~30
  split `中文版:25,121`), so fixes must be applied to both, but the mirror introduces no additional
  operational content beyond the English.
