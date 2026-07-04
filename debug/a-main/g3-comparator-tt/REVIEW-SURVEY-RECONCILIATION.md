# Rider-2 bindability survey — ≥3-cold-review reconciliation

Three independent cold reviewers (A/B/C, no shared context) reviewed
`rider2-bindability-survey.md` @ `d6ada0c` against the frozen blind set (`15954a8`), the
pre-registered evaluator (`ContractEvaluator` + `DataIntegrityRuntime.extractItems`), and the
Rider-2 protocol. **All three: ACCEPT-WITH-FIXES.** All findings are folded in (`a6fd3ba` + the C
wave); every disposition flip ran AGAINST the comparator, so the corrected residue is LARGER and
the qualitative two-sided reading is unchanged-but-stronger.

## Convergence

- **A + B independently converged on the identical BLOCKING** (same three rows, same mechanics,
  same corrected numbers): absence checks evaluate `containsSubmittedFields` over
  `DataIntegrityRuntime.extractItems`, which returns EMPTY whenever `data` is not a JSON array
  (`:885-887`) → an absence check "bound" to a single-object per-entity read is VACUOUS (passes on
  both legs; a lost delete is never caught). Flips **#12, #23, #52 → NC-OBJECT-ABSENCE**.
- **C converged on those three AND caught two more the others missed**: the admintravel merged
  list's items are `AdminTrip{trip, trainType, route}` WRAPPERS (`AdminTrip.java:10-12`;
  `TravelServiceImpl.adminQueryAll:562-581`) with no top-level trip fields →
  `containsSubmittedFields` (top-level keys only) is never satisfiable → **#76, #77 →
  NC-NESTED-ITEM-SHAPE** (control-breaking if bound, not merely non-discriminating). Author
  re-verified both mechanics in source before applying.

## Corrected headline (all five flips applied)

| convention | BINDS (incl. partial) | NC |
|---|---|---|
| G (generous) | **69/80 = 86.25 %** | 11/80 = 13.75 % |
| S (strict) | 59/80 = 73.75 % | 21/80 = 26.25 % |

NC census (11): 3× KEY-SHAPE (#29/#32/#78) + 2× NESTED-ITEM-SHAPE (#76/#77) + 3× OBJECT-ABSENCE
(#12/#23/#52) + TRANSITION (#18) + RESPONSE-KEYED (#20) + BATCH (#37).

## Disposition table

| # | Finding | Severity | Disposition |
|---|---|---|---|
| S-1 | #12/#23/#52 absence bound to single-object reads is vacuous (A+B converged; C too) | BLOCKING | **FIXED** — flipped to NC-OBJECT-ABSENCE; collection-shape rule added to the conventions (absence → list reads only; per-entity presence → entity-matches, else healthy controls false-flag) |
| S-2 | #76/#77 merged-list membership never satisfiable (nested AdminTrip wrappers) (C) | BLOCKING | **FIXED** — flipped to NC-NESTED-ITEM-SHAPE with source cites; noted #76's response gate is disclosed-weak so nothing catches a lost admintravel create |
| S-3 | Recount (A+B: 71/80; C after #76/#77: 69/80) | MAJOR | **FIXED** — 69/80 G / 59/80 S; census 11; all percentages restated |
| S-4 | Reading §2 overclaimed "residue concentrates on payment/compensation flows"; §3's "not plain-CRUD bindable at all" asserted for 6 unexamined services (A+B+C) | MAJOR | **FIXED** — residue reframed as STRUCTURAL primitive-vocabulary gaps (flat-single-object assumptions breaking); payment/compensation split into two separately-scoped facts: not_covered coverage fact + depth-cell 3/3 NOT_CHECKABLE; no claim about the five unexamined services |
| S-5 | Envelope-shape rule must be explicit for the executable YAML (per-entity presence = entity-matches: #36/#38/#49/#50; absence = list reads: #5/#39/#81 notes) (A+B+C) | MAJOR | **FIXED** — rule in the conventions block + per-row notes corrected |
| S-6 | "(3 deletes a)" cell typo — adminbasic has 2 alias deletes (A+B+C) | MINOR | **FIXED** |
| S-7 | 79-vs-81 count reconciliation vs the freeze message + protocol (A+B+C) | MINOR | **FIXED** — count note in the header (79 plausibly excluded the two mutating-GET entries; all 81 surveyed = conservative no-drop reading) |
| S-8 | P-label harmonization (#13/#14/#46/#71 have inexpressible frozen observables) (B+C) | MINOR | **FIXED** — all four BINDS-P |
| S-9 | #37 batch: synthetic flat map needed even for response clauses; why batch-of-one binding is rejected (A+C) | MINOR | **FIXED** — both disclosures in the table/notes |
| S-10 | #81/#5 notes must cite the LIST read (the per-entity absence is vacuous) (B+C) | MINOR | **FIXED** |
| S-11 | Trip KEY-SHAPE claim needed a source cite (author self-identified; A/C verified TripId nested) | MINOR | **FIXED** — Trip.java:26-29 + constructor evidence in row #29 |

## What all three verified sound

Conventions fair + fully disclosed; every alias grounded in a frozen-text equation, identity-valued
(no transformations), per-row marked; UNKNOWNs genuinely counted against MIST; all six ORIGINAL NC
rows independently confirmed inexpressible; failure-contract N/A census matches the frozen text
exactly; arithmetic internally consistent as published; expressibility-vs-runtime framing explicit;
no endpoint silently dropped; executable-YAML deferral consistent with the protocol's
"before the G3 comparison run"; no contradiction with the accepted head-to-head framing. C: "the
corrected story is, if anything, better for the paper: the residue grows and remains exactly the
class MIST's value-delta primitive covers."

## Standing rules carried forward to the breadth-run build

1. **Collection-shape rule** (S-1/S-5): absence clauses bind ONLY to collection-shaped reads;
   presence on single-object reads uses `entity-matches-submitted-fields`. The executable YAML
   implements the survey's dispositions exactly; any deviation is a disclosed amendment.
2. The five flipped rows would have corrupted the breadth run in BOTH directions (vacuous absence
   → fake comparator misses, inflating MIST; nested-list membership → false-flagged healthy
   controls, inflating the comparator's infra-cost) — reviewer C's framing of why the survey gate
   mattered.
3. An `entity-absent` primitive would repair the OBJECT-ABSENCE rows but is a NEW primitive =
   disclosed evaluator amendment territory, recorded as the honest boundary of the modeled class.

**STATUS: survey REVIEWER-ACCEPTED (3× ACCEPT-WITH-FIXES, all findings folded) — the corrected
fraction (86.25 % / 73.75 % bind; 13.75 % / 26.25 % structural residue) may feed the
external-validity claim with the reading's scoping intact.**
