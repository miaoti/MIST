# C3 rater materials — recruitment brief, consent, rubric packet, ballot, independence protocol

**Purpose:** the human-adjudication package for the C3 wild stratum (S3) + the M-yield cluster audit.
Pre-registered by plan v2 §3.1 (raters quantified: ≥2 MIST-blind + a third adjudicator; 15–45 h each).
This is the **longest-lead item** on the critical path (recruitment 2–6 wk), so it is authored during
step 1 while the depth survey runs. The rubric here is the frozen `c2-freeze.md` §3 copy — one
authoritative rubric, not a fork.

**Draft status:** materials draft, authored autonomously; the RECRUITMENT CHANNEL (§7) is the open
USER decision. To be ≥3-cold-reviewed before any rater is contacted (soundness-critical: the whole
C3 precision claim rests on §0).

---

## §0 The blindness invariant (this section is for US, never shown to raters)
The C3 precision claim's soundness rests on raters being blind to the tool whose output they are
(unknowingly) validating. Therefore **nothing in the rater-facing materials (§1–§6) names MIST,
describes a detector, or reveals that the cases were machine-flagged.** Raters believe they are
labeling a curated set of observed microservice behaviors as genuine-defect / by-design /
underspecified, using only each system's docs, spec, and source.

Concretely, blindness requires:
- The case set shown to each rater is a **normalized mix**: S3 wild-flagged cases + ~20 calibration
  cases (pre-known labels) + **the M-yield cluster-audit sample** (generation-driven finds, plan §3.1
  — B-M3), all in ONE common format (B4 harness output). A rater cannot tell which stratum a case is
  in, nor which detector (if any) surfaced it. The M-yield audit cases are format-normalized and
  interleaved exactly like S3 cases; their size is stated to the rater only as part of the total.
- **Clean-run normalization (R6 — closes a blindness leak).** Every S1 calibration case has a paired
  clean-run twin, but wild S3 / M-yield cases usually do NOT — so presence-vs-absence of a "no-fault
  run" column would deterministically separate calibration from measurement. Invariant: **the paired
  clean-run observation is either (a) stripped from ALL rater-facing cases, or (b) synthesized/withheld
  uniformly**, so the field carries ZERO signal about stratum. Default: strip it — the rater judges the
  single observed run against docs/spec/source. Also audit for other distributional tells (SUT /
  endpoint / response-shape mix must not correlate with stratum).
- Each rater-facing case carries ONLY: the SUT identity + version, the request sequence performed, the
  observed durable state, and pointers to that SUT's docs/spec/source. It carries NO tool verdict, NO
  trace, NO hypothesis label, NO "expected observable" annotation, and NO clean-run column.
- Independence mechanics (§5) prevent raters from reconstructing the tool lens by comparing notes.
This §0 is the reason the materials read the way they do; it is removed from the rater packet.

---

## §1 Rater-facing recruitment brief
**What this is.** A paid, short-term labeling study on open-source microservice benchmarks. You will
review a **capped set of up to 60 recorded system behaviors** (S3 wild + calibration + M-yield audit,
interleaved) and judge, for each, whether the behavior is a genuine correctness defect, an
intentional/by-design behavior, or not decidable from the available documentation.

**Who we're looking for.** Software engineers with microservice literacy: comfortable reading
OpenAPI/REST specs, understanding synchronous REST + asynchronous messaging + eventual consistency,
and reading application source (Java and Go). A short screening task (§ eligibility) confirms fit.

**The task, per case.** You are given (a) the system and its exact version, (b) the sequence of API
requests performed, (c) the system's response(s), and (d) the resulting observed durable state. Using
ONLY the system's documentation, OpenAPI/spec, and source code, you assign one label:
- **genuine defect** — the system reported success (or otherwise acknowledged the operation) but the
  durable effect the operation promises did not occur, AND the intended behavior is derivable from
  the docs/spec/source (i.e., "it should have persisted/propagated" is contract-grounded).
- **by-design / benign** — the docs/spec/source establish the behavior is acceptable: retried
  elsewhere, eventually consistent within contract, an intentional partial-degradation, etc.
- **underspecified** — the intended behavior for what you observed is NOT derivable from the
  docs/spec/source.
You record the specific doc/spec/source citation that grounds your label, a confidence, and a brief
rationale (§4 ballot).

**Time + pay (m2 — reconciled).** ~15–45 minutes per case × **≤60 cases ≈ 15–45 hours (~2–3 working
days)**; if a larger set is unavoidable the estimate/compensation scales with it (the pay basis is
per-hour, not per-case). You are paid [RATE — §7] for the estimated hours **regardless of the labels
you produce**; there is no "right answer" we are steering toward.

**What you will NOT be shown** (and why it matters): any automated tool's opinion on these cases, any
execution traces, and any of our own hypotheses about which cases are defects. We are measuring your
independent judgment; seeing those would defeat the study. Please do not seek them out.

---

## §2 Consent + compensation statement (plan §3.1 requirement — rater-facing, verbatim)
> I understand this is a paid labeling study on open-source microservice systems. I will label each
> case using only the system's documentation, specification, and source code. I will not discuss the
> cases or my labels with any other rater until notified that the study has closed. I understand I am
> compensated [RATE] for the estimated [HOURS] hours regardless of the labels I produce, and that
> there is no predetermined "correct" set of labels. I consent to my anonymized labels and rationales
> being released as part of an open research artifact.

---

## §3 The rubric packet (frozen — VERBATIM-identical to c2-freeze.md §3 / the §8.5-1 rule; R5)
**Three-way label {genuine, benign, underspecified}.**
- **genuine** — a real acked-but-lost data-integrity fault: the system acknowledged the operation (HTTP
  2xx **with a success-shaped body — see the sentinel rule**) while the durable write it promises did
  not land (or a downstream write in its causal closure did not), AND the intended behavior IS
  derivable from docs / spec / source.
- **benign** — the observed degradation is by-design / lived-with per docs / spec / source.
- **underspecified** — the intended behavior is NOT derivable from docs / spec / source. Excluded from
  the primary precision denominator; fraction reported; a disagreement about WHETHER a case is
  underspecified goes to the third adjudicator like any other.
- **Sentinel / "success-shaped body" rule (R8):** a body carrying a failure sentinel or error
  status-field (`-1`, `{1,"error"}`, a negative id) is **NOT success-shaped** — such a case is
  tell-bearing and is tracked separately (it is trivially catchable by a body check), not in the
  primary discriminating denominator. A discriminating genuine positive needs 2xx AND (empty body OR a
  success-shaped body with no failure sentinel).

**Admissibility (the observation-vs-verdict split — R5):**
- **Admissible AS the OBSERVATION to be judged:** the case's own presented material — the request
  sequence, the response(s), and the observed durable state.
- **The sole source of the NORM (what SHOULD have happened):** docs, OpenAPI/spec, source code.
- **Inadmissible:** distributed traces, any tool/oracle output (incl. MIST), and any runtime behavior
  BEYOND what the case itself presents.

**Worked examples (calibration-only; to be authored on real calibration cases, kept out of S3):**
- *genuine pattern* — POST returns 201 with an order id; GET on that id 404s and no row exists in the
  service whose OpenAPI schema lists it as the system of record. (Contract-grounded "should persist".)
- *benign pattern* — a write returns 202 Accepted and the durable effect appears only after an async
  worker cycle documented as eventually-consistent; observed "absence" within the documented window
  is by-design.
- *underspecified pattern* — a partial write where neither the docs nor the source state whether the
  operation is atomic; the intended post-state cannot be derived.

---

## §4 The per-case ballot (what a rater records)
```yaml
case_id: <opaque id — rater cannot decode stratum from it>
label: genuine | benign | underspecified
grounding:                       # REQUIRED for genuine/benign; for underspecified, state what's missing
  citation: <doc-url+version | spec-path+operation | source-file:symbol>
  quote_or_ref: <the sentence/clause/signature that grounds the label>
confidence: high | medium | low
rationale: <2–4 sentences: what was promised, what was observed, why the label follows>
time_minutes: <int>              # for the compensation + calibration audit
```

---

## §5 Independence + adjudication protocol
- **≥2 independent raters** label the full set; **no discussion channel** between raters before the
  study closes (separate delivery, separate return).
- **Third adjudicator** resolves every disagreement, including disagreements about whether a case is
  underspecified. The adjudicator sees both ballots and the same admissible evidence only.
- **Agreement statistics (R7 — S3-only κ is PRIMARY):** report **S3-only κ as the headline reliability
  number** (Clopper–Pearson counts when n<10 — the pre-registered S3-scarce branch). Pooled
  calibration+S3 κ is a SECONDARY small-n-stability figure carrying the **calibration-inflation caveat**
  (calibration cases are the easy, rubric-tuned cases → pooled κ is upward-biased). Both reported with
  CI + raw agreement + a prevalence-adjusted coefficient (PABAK / Gwet's AC1). CI units = distinct
  defect/fault-sites, not flagged events.

## §6 The κ-calibration round (runs first)
- **Calibration set sizing (M1 fix):** draw the calibration set (known labels, from S1 positives + S2
  benign, format-normalized) large enough that **pooled calibration+S3 ≥ 50 is guaranteed given the
  expected S3** — with S1+S2 ≥ 80 this is free (e.g. ~30 calibration + expected S3 ≥ 20). This makes the
  secondary pooled figure computable without letting S3 scarcity block it.
- Calibration is labeled first; κ is computed.
- **κ-gate (frozen):** if κ < 0.6, at most TWO rubric-iteration rounds, each using CALIBRATION CASES
  ONLY (no S3 peeking). After any iteration, ALL previously-labeled cases are relabeled under the
  final rubric (fresh raters if available). Calibration cases are NOT reused as S3 measurement cases.

---

## §7 Recruitment channel — **DECIDED (2026-07-09): in-group SE grad students**
**Decision (user, 2026-07-09):** raters = software-engineering grad students from our own group. This
resolves the channel and removes the 2–6 wk external-outreach lead. It is chosen for skill-fit + zero
recruitment lead — but it is the "medium independence-risk" row below, so it is admissible ONLY under
the following BINDING conditions (they protect §0, the load-bearing soundness invariant):
- **MIST-blind is mandatory, not aspirational.** An eligible student must NOT be on the MIST project
  and must never have seen the tool, its hypothesis, this repo, or the head-to-head results. A student
  who knows MIST is INELIGIBLE as a rater (they may help elsewhere, e.g. logistics — never as a
  labeler). This is screened before assignment and recorded.
- **In-group ⇒ not off-team ⇒ a disclosed threat to validity.** Because same-group raters carry
  relationship bias, the paper discloses "internal but MIST-blind raters, independence enforced by
  protocol (§5)" in threats-to-validity, and the §5 mechanics are enforced strictly (separate delivery
  + return, no discussion channel until close).
- **Third adjudicator = most senior + most arm's-length available** (ideally not the students' direct
  advisor on this work).
- **If genuinely-blind in-group students cannot be staffed, the §8 two-author-blind fallback register
  applies** (claim demoted one register, all labels released, author-pair κ) — do NOT quietly relax
  blindness to fill seats.

The channel comparison stays on record for the priority defense (chosen row = **SE grad students**):
| channel | skill fit | lead time | cost | independence risk |
|---|---|---|---|---|
| Contract SWEs (Upwork/Toptal, microservice-screened) | good if screened | 1–3 wk | market hourly ×2 raters ×~30 h | low (strangers, no shared channel) |
| **SE grad students (our group) — CHOSEN** | good | ~0 (in-house) | stipend/credit | medium (in-group — mitigated by mandatory MIST-blind screen + §5 + disclosure) |
| Industry SRE/backend contacts | very good | 1–4 wk | favor/honorarium | medium (relationship bias — keep blind + independent) |
| Prolific/MTurk | poor (needs source-reading) | days | low | low but skill floor likely fails eligibility |

**IRB / ethics determination — a PRECONDITION before any rater LABELS (B-M8), channel notwithstanding.**
Even with in-group student raters, a study with paid/credited human labelers still needs an IRB/ethics
determination (approval OR a documented exemption) at the institution BEFORE labeling begins — the
in-group choice removes the outreach lead but NOT this precondition. **Expected exemption
rationale** (to file): the task is expert code/spec review of PUBLIC open-source systems, collects NO
personal or sensitive data, records only anonymized labels + rationales, and releases them as an open
research artifact — typically exempt (e.g. US 45 CFR 46.104(d)(2) benign-behavioral / existing-public).
The paper's ethics statement cites the determination.

## §8 Fallback (two-author-blind — pre-committed scars, plan §3.1)
Triggers ONLY if recruitment fails by the step-5 gate. Then, as pre-committed: (i) the C3 precision
claim is demoted one register in the ABSTRACT; (ii) all label evidence is released for community
re-adjudication; (iii) author-pair κ is reported. Acknowledged as partially undoing the §6 central
fix of the review. This is a fallback, not a plan — the §7 recruitment starts now precisely to avoid it.

## §9 Eligibility screen (≤20 min, before the paid work)
Two calibration-style cases with unambiguous ground truth (one clear genuine, one clear benign) + a
2-question spec-reading check (given an OpenAPI snippet, identify the system-of-record service for a
field; given a source method, state whether it persists). Pass = both cases correct + both check
questions correct. This gates the skill floor without revealing the study's purpose. **These two
eligibility cases are DISJOINT from the ~30 §6 calibration cases and from all S3/M-yield cases (m7)** —
a rater never sees them again, so they cannot bias κ or the measurement.
