# Rater-materials review reconciliation (3 cold reviews → one fix map)

**Reviews folded:** `REVIEW-RATER-1-soundness.md` (ACCEPT-WITH-CHANGES; 4 BLOCKING),
`REVIEW-RATER-2-adversarial.md` (REJECT; 8-item package), `REVIEW-RATER-3-completeness.md`
(REJECT-AS-WRITTEN; 4 BLOCKING). All three independently GATE the same thing: **first contact with a
human rater.** Net verdict: **REJECT-as-written → fixable at the protocol/materials level, all before
first contact.** This doc is the authoritative fix list; the fix wave executes against it.

**Target files:** `c2c3/c3-rater-materials.md` (packet) + `c2c3/c3-rater-materials-中文版.md` (mirror,
English authoritative); `c2c3/c2-freeze.md` (§6 disclosed-amendment rows — required because
`c3-rater-materials.md:87` binds §3 VERBATIM to the freeze §3); `c2c3-execution-plan.md §3.1` (stale
pre-registration); `c2c3/step2-execution-checklist.md` (step-5 gate + IRB timing).

**R3 root-cause worth stating up front:** the rev-2 re-freeze (`c2-freeze.md:252`, R2 supersession of
`benchmark/`) folded R1–R8 but silently dropped three operational specs the superseded prototype rubric
`benchmark/schema/rubric.md` carried — async-vs-lost-write disambiguation (`:45-50`), a pinned **Cohen's**
κ (`:55`), and per-case **rubric-version + rater** recording (`:56`) — with no `§6` amendment log. Three
findings below (F4, F11, F14) are exactly these regressions; the fix re-lands them as disclosed amendments.

---

## Convergence map — findings ≥2 reviewers hit independently (highest confidence)

| # | Finding | R1 | R2 | R3 | Severity |
|---|---|---|---|---|---|
| F1 | §1 leaks strata names + presupposes tool opinions; §0 "§1–§6" boundary sweeps internal §5/§6 to raters | B1 | 1c | BLOCK-4 | BLOCKING |
| F2 | MIST-blind screen asserted, never instrumented (no questions/attestation/record) | B3 | 1a | — | BLOCKING |
| F3 | No debrief / funneled manipulation check anywhere | B4 | 1b | — | BLOCKING |
| F4 | Rater **genuine** def requires "success-shaped body" → a 2xx `{1,"error"}` acked-but-lost (the named TT-natural exhibit) has NO valid label | B2 | (Attack6 adj) | BLOCK-1 | BLOCKING |
| F5 | S3-only κ ≈ 0.43 predicted under the plan's own benign-dominance prior; no low-primary-κ rule; gate fires on the easy calibration set only | M3 | 4/4a | MAJOR-8 | BLOCKING (decision-rule pre-registration) |
| F6 | Non-blind in-group adjudicator sets every contested final label → authors the headline in the expected case | M5(adj) | 1d | MAJOR-7 | BLOCKING |
| F7 | "VERBATIM-identical" frozen-rubric claim is false (rater copy drops the paired-clean-run clause) | M7 | Attack6 | BLOCK-1/2 note | MAJOR (integrity) |
| F8 | Case-count / calibration-size arithmetic drift across three docs (60 vs ~70–90; ~20 vs ~30) | M1/m2 | 2-nit | MAJOR-5 | MAJOR |
| F9 | Execution plan §3.1 still pre-registers pooled-PRIMARY κ (stale vs R7 S3-only-primary) | M8 | — | MAJOR-3 | MAJOR |
| F10 | Ballot missing `rater_id` + `rubric_version` (κ join / relabel-round provenance) | m3 | — | MAJOR-4 | MAJOR |
| F11 | Rubric under-determines undocumented-window async (EC) — the flagship S3 class is a coin-flip | M4 | 3b(adj) | BLOCK-2 | BLOCKING |
| F12 | M-yield audit labels fall outside both κ definitions (no reliability metric) | M2 | — | MAJOR-6 | MAJOR |
| F13 | Consent incomplete: no withdraw right, no no-academic-consequence clause, false "no correct labels", unresolved pay placeholders | B4 | 2(partial) | (verified load-bearing terms present) | BLOCKING |

## Single-reviewer findings accepted into the fix list

| # | Finding | src | Severity |
|---|---|---|---|
| F14 | κ variant unspecified (Cohen vs Fleiss vs weighted); underspecified's role in κ (3-way vs collapsed) unstated | R3 MAJOR-1/2 | MAJOR |
| F15 | "Use ONLY the provided version-pinned bundle; no web search" rule never reaches the rater | R3 BLOCK-3 | BLOCKING |
| F16 | Calibration mix teaches an inflated genuine base-rate vs the benign-dominance prior; ratio never pre-registered | R2 2a | MAJOR |
| F17 | 30 known calibration labels used for nothing but κ — no confusion-matrix / directional-bias audit | R2 2b | MAJOR |
| F18 | Agreed-`underspecified` cases exit the denominator unaudited; time-incentive rewards fast underspecified | R2 3a | MAJOR |
| F19 | No pre-registered `underspecified`-dominance bound; R6 clean-run strip manufactures underspecified | R2 3b | MAJOR |
| F20 | No reserve rater; "fresh raters if available" is a staffing fiction in a near-empty blind pool | R2 4b / R1 m9 | MAJOR |
| F21 | κ-vs-PABAK/AC1 primacy unstated under the benign-dominance branch | R3 MAJOR-9 | MAJOR |
| F22 | IRB gated "before labeling"; recruiting/screening is itself IRB-covered → gate "before contact"; "no personal data" overclaim | R1 M6 | MAJOR |
| F23 | Genuine "downstream write in causal closure" clause unevaluable if the case doesn't present the downstream durable state | R3 MAJOR-10 | MAJOR |
| F24 | No rater↔TEAM quiet-period rule (the realistic in-group leak channel) | R1 M5 | MAJOR |
| F25 | Minors: causal-closure jargon; CP-at-n<10 category confusion; κ-CI method (bootstrap BCa); `confidence` collected-but-unused; submission mechanics absent; two-rater-variance note; worked-examples schematic; §9 timing; label-name drift | R1 m5/m7/m8/m10, R3 MIN-1..5 | MINOR |

---

## Design decisions committed here (pre-register these; they resolve the "pick one" findings)

- **D-ADJ (resolves F6, F7-adjacent, F19):** adopt **conservative tie-break as PRIMARY**, not a
  MIST-blind external adjudicator. Rationale: the in-group pool has no *guaranteed* MIST-blind senior,
  so option (a) is not reliably staffable; conservative tie-break is self-contained and it makes
  disagreement **cost** precision, deleting the inflation vector. Rule: **any inter-rater disagreement
  that involves the `genuine` label resolves to NOT-genuine for the headline precision**; the third
  reader's adjudicated resolution is reported as a **SECONDARY (upper-bound)** figure. The third reader
  is **case-blind** (sees only admissible evidence, never the tool verdict) and **blind to rater
  identities**; tool-blindness is not required of them because they no longer author the primary. Update
  the frozen "blind-adjudicated wild stratum" claim string → "conservative-tie-break primary; case-blind
  adjudicated secondary."
- **D-κ (resolves F5, F14, F21):** enumerate **three** κ's — (i) *calibration-gate* κ (governs ≤2 rubric
  iterations, calibration-only), (ii) *S3-only-primary* κ (headline reliability), (iii) *pooled
  calibration+S3-secondary* κ (inflation-caveated). Estimator: **Cohen's unweighted** for exactly 2
  raters; **Fleiss'** if a 3rd labeling rater is ever used; labels are **nominal, unweighted**; κ over
  the **full 3-category** space {genuine, benign, underspecified} (the underspecified→precision exclusion
  applies to the precision denominator ONLY, never to κ); κ CI by **bootstrap BCa**; at S3 n<10 withhold
  κ and report raw agreement + Clopper–Pearson on the agreement proportion. **PABAK/Gwet's AC1 leads as
  headline when any single label's prevalence > 0.70** (the benign-dominance regime where κ's base-rate
  paradox bites); κ leads otherwise; both always reported, neither substituted post-hoc.
  **Pre-registered reliability decision ladder on the PRIMARY (S3-only) coefficient:** ≥ 0.6 → full
  register; 0.4–0.6 → **demoted register** (all ballots released, conservative-tie-break primary already
  in force, adjudicated secondary, AC1 reported not substituting, disagreement-dense cases tabled);
  < 0.4 → **no reliability claim**, §8 fallback framing.
- **D-GEN (resolves F4):** rater-facing **genuine** = "the system acknowledged the operation (HTTP 2xx /
  success ack), the promised durable write (or a downstream write in its causal closure that the case
  presents) is absent, and the intended norm is derivable from the provided docs/spec/source." The
  sentinel test moves to a **mechanical ballot field** `ack_carries_failure_sentinel: yes | no`
  (rater-answerable — the body is in front of them); tell-bearing segregation is applied **analytically**.
- **D-ASYNC (resolves F11):** add a determinate async tie-break to §3: "If the write path is
  asynchronous, judge against any documented completion bound. Observed absence **past a documented
  bound → genuine**. **No** completion bound derivable from docs/spec/source (so permanence cannot be
  established) → **underspecified**." (Re-lands the dropped `benchmark/schema/rubric.md:45-50`
  inconclusive→underspecified rule in rater form.)
- **D-BUNDLE (resolves F15):** add to §1 and §3: "For each case use ONLY the provided version-pinned
  docs/spec/source bundle; do not consult the upstream/live repository, web search, or any other version
  — the pinned bundle is the sole norm."
- **D-CAL (resolves F16, F17):** pre-register the calibration mix **benign-skewed ≥ 2:1** (matching the
  S2:S1 corpus reality and the benign-dominance prior), disclosed at debrief; compute **per-rater
  confusion matrices vs the known calibration labels** + a **directional false-genuine rate on
  known-benign cases**, feeding a **pre-registered sensitivity band** on the S3 precision CI.
- **D-USP (resolves F18, F19):** the third reader **audits ALL agreed-`underspecified` cases**;
  pre-registered bound — **`underspecified` > 30% of S3 ⇒ the fraction is promoted to a headline finding
  and qualifies the precision sentence in the abstract**; a fast-underspecified time audit flags raters
  whose underspecified calls cluster at low `time_minutes`.
- **D-STAFF (resolves F20):** staff **2 + 1** raters (the reserve doubles as the fresh relabeler for a
  rubric iteration).
- **D-HANDOVER (resolves F1):** SHIP/INTERNAL tag on every section + an explicit rater hand-over
  manifest — **rater receives:** rendered §1 (brief, strata parenthetical struck, bundle rule added),
  §2 (consent, completed), §3 (rubric, D-GEN/D-ASYNC/D-BUNDLE applied), §4 (ballot + new fields), §9
  (eligibility), and per-case `case.md`+`ballot.yaml`; **ADMIN-ONLY:** §0, §5-statistics, §6, §7, §8,
  and the internal failure-rule half of §10.
- **D-SCREEN + D-DEBRIEF (resolves F2, F3):** author the blindness-screen instrument (objective
  ACL/roster/advisor-attestation checks + indirect self-report questions that never name MIST + a
  decision rule "any hit ⇒ ineligible" + a signed pre-study attestation appended to consent) and a new
  **§10 debrief**: a funneled close-out manipulation check (Q1 "what was this study about?" → Q2 "do you
  believe a software tool produced/selected these cases?" → Q3 "can you name it?") + a non-discussion
  close-out attestation; **pre-registered failure rule** — a rater who names the tool/hypothesis ⇒
  disclosed blindness failure + sensitivity analysis excluding their ballots; both fail ⇒ §8 fallback;
  transcripts retained + released anonymized. Add "screening + debrief records on file" as the **9th
  check** in the step-5 corpus-assembly gate (which today audits only the corpus, never the raters).

## User-decision items (flagged, NOT resolved here — they gate actual contact, already gated; they do NOT block this hardening wave)

- **U1 Compensation:** stipend vs course credit; `[RATE]`; `[HOURS]`. If course credit → an
  alternative-assignment clause is required (coercion review). Leave explicit `[USER DECISION …]`
  placeholders in §1/§2/§7; everything else hardens around them.
- **U2 IRB filing** is an institutional action (the materials pre-register it as a *before-contact*
  precondition per F22; the filing itself is the user's).
- **U3 Strategic (surface, do not block):** the pre-registered ladder means a *demoted-register* C3 is
  the central expected outcome (S3-only κ ≈ 0.43). All three reviewers judge that publishable as
  "threats-to-validity with receipts," and building C3 was already user-chosen — so proceed; but the
  user may later weigh whether a demoted-register C3 is worth the human-subjects cost. Not a
  hardening-wave blocker.

---

## Execution order (fix wave)

1. **c2-freeze.md §6 amendment rows** (the rubric changes must be disclosed at the freeze first, then
   mirrored): D-GEN (genuine-def + sentinel→ballot-field), D-ASYNC, D-BUNDLE, Cohen's-κ re-pin,
   rubric-version+rater-recording re-pin, R6-strip disclosure (F7); and fix both "VERBATIM-identical"
   claims → "identical modulo the disclosed R6 strip + enumerated analyst-only clauses."
2. **c3-rater-materials.md** full pass: SHIP/INTERNAL tags + hand-over manifest (D-HANDOVER); §1 delek +
   bundle rule + case-count reconcile (F8); §2 consent completion + attestation (F13) + U1 placeholders;
   §3 rubric (D-GEN/D-ASYNC/D-BUNDLE, causal-closure scope F23, jargon F25); §4 ballot (+`rater_id`,
   `rubric_version`, `ack_carries_failure_sentinel`, confidence-role F25); §5 stats (D-κ full, F12
   M-yield-κ, F25 CP/BCa); §6 gate (calibration adaptive size F8/M1, D-CAL); §7 adjudicator (D-ADJ) +
   quiet-period (F24) + D-STAFF; §8 fallback trigger aligns to the ladder; §9 timing (F25); **new §10**
   (D-DEBRIEF); D-USP guardrails; D-SCREEN instrument.
3. **c3-rater-materials-中文版.md**: mirror every §-level change (English authoritative).
4. **c2c3-execution-plan.md §3.1**: dated amendment → S3-only PRIMARY per R7; calibration ~30 (F9, F8).
5. **step2-execution-checklist.md**: step-5 gate 9th check (screening+debrief records; worked-examples
   authored) + IRB "before contact" (F22).
6. FILE_INDEX.md rows for any new artifact; MEMORY.md pointer; commit on `main_track`.

**Re-review trigger:** after the wave, the three reviewers' verdicts convert on a diff re-read (R1
stated so explicitly). A 4th cold pass is warranted only if the user wants belt-and-suspenders before
IRB — flag, do not auto-spawn.
