# REVIEW — Step-1 C2/C3 freeze wave · charge B (COMPLETENESS + GAPS)

**Reviewer:** independent cold reviewer B (no shared context; judgment formed from the files).
**Scope:** `c2c3-execution-plan.md` §5 + the step-1 outputs under `c2c3/` (`c2-freeze.md`,
`c3-rater-materials.md`, `r4-comparator-spike.md`, `c2-depth-survey.md`,
`e-sut-applicability-matrix.md`, `c2-claim-sweep.md`, `c2-license-audit.md`), cross-checked against the
already-committed `benchmark/` prep artifact and `g3-result.md`.

---

## VERDICT: **REJECT** — one more focused re-freeze pass required before step 2 relies on this.

The freeze does its narrative job well (claim defense, license conduct, applicability matrix, depth
survey are strong). But a completeness pass finds **four BLOCKING gaps that make step-2+ hit a wall or
emit un-defensible data**, and they are exactly the kind a pre-registration is supposed to close: a
second, incompatible, *committed* C2 schema + a methodologically-opposed rubric that the freeze never
mentions; an S1≥45 floor that the freeze's own normative survey contradicts; a fault-mechanism enum
that cannot satisfy the diversity floor it is paired with; and an observable model that cannot
represent flagship cases the benchmark already owns. None is fatal to the project; all must be fixed
*now*, at the freeze, not discovered at population. Hence REJECT rather than ACCEPT-WITH-CHANGES — the
frozen schema and floors themselves must change, which is a re-freeze, not a downstream amendment.

---

## BLOCKING

### B1. A second, incompatible C2 schema + an *opposed* rubric are already committed — the freeze ignores both.
`benchmark/schema/fault-case.schema.json` (+ `schema/rubric.md` v0.1.0 + **6** committed seed cases in
`benchmark/cases/`) is a live, JSON-Schema-validated C2 benchmark definition. `c2-freeze.md` §2 invents
a *different* schema and never references it. They are incompatible on every load-bearing axis:

| axis | committed `benchmark/` (v0.1.0) | frozen `c2-freeze.md` §2 |
|---|---|---|
| format | JSON + draft-2020-12 validator | YAML example + **prose** invariants (no machine schema) |
| stratum | integer `1\|2\|3` (schema line 32-36) | string `S1\|S2\|S3` (freeze line 39) |
| label enum | **two-way** `positive\|negative` (schema line 115-118) | **three-way** `genuine\|benign\|underspecified` (freeze line 88) |
| mechanism | `sut_injector\|toxiproxy\|replicated_fault\|vendor_flag\|natural\|none` (schema line 97-100) | `flag\|mesh-sever\|broker-policy\|code-level\|none` (freeze line 58) |
| oracle columns | 6 fixed `oracle_expectation.*` incl. `mist_*` targets (schema line 131-149) | `comparator_configs` arms `naive\|tracetest-error\|tracetest-presence\|traceanomaly` (freeze line 82-84) |
| agreement | per-case `cohen_kappa` (schema line 169) | pooled κ + PABAK/Gwet's AC1, n≥50 (freeze line 148) |

Worse than "different" — the **rubrics are methodologically opposed**. `benchmark/schema/rubric.md`
decides labels *from runtime evidence*: §B inputs = "the read-back GET response" (line 15), §C inputs =
"the distributed trace for the entry request" (line 31). The frozen rubric (`c2-freeze.md` §3 line 140;
`c3-rater-materials.md` §3 line 91) declares **"Inadmissible evidence: runtime behavior, traces, MIST
output."** The old rubric also mandates "Cohen's κ" + "target precision CI half-width ≤ 5%" (rubric
line 55, 57) — which plan §3.1 (line 150) *explicitly supersedes*. And the old rubric's third label is
`inconclusive`-and-excluded (rubric line 49-50), not `underspecified`.

**How it bites:** plan §2.4-2 (line 108) says step 2 "promote the ~10 reviewed existing assets into
cases." The assets are the 6 seed cases (JSON, two-way label, runtime-grounded rationales) + the G3
result-doc cells. Under the freeze they must be re-formatted, re-labeled (positive→genuine,
negative→benign), re-grounded (runtime rationale → docs/spec/source only), and re-homed — with **two
"authoritative" C2 schemas + two rubrics live in the repo** and nothing declaring which wins. First
populator or first artifact-reviewer stalls on "which schema?".
**Fix:** in the freeze, explicitly supersede `benchmark/` (dated §6 amendment row), OR port the freeze
into that dir. Ship a **machine schema** (the freeze currently only *describes* invariants in prose at
`c2-freeze.md` line 111 — "machine-checkable at population" is asserted, not delivered; the old dir
already had a real validator, a regression). Write the seed-case **migration map** (field-by-field,
incl. the runtime→docs re-grounding of every existing rationale) as a step-1 output.

### B2. S1 ≥ 45 does not close — and is *contradicted* by the freeze's own normative depth survey.
The applicability matrix's floor binding (`e-sut-applicability-matrix.md` line 39-41) is the only
S1 arithmetic in the freeze: TeaStore (4–5) + OTel-Demo (4–5) + Boutique (1) = **~9–11 new**, then
"TT (F-corpus ≥6 + G1/G3) + SS carry the balance." The balance is **34–36 of 45**, and it is entirely
unquantified: the depth survey — declared NORMATIVE for S1 quotas (`c2-depth-survey.md` line 6; plan
§2.3 line 99) — **surveys only the four new SUTs and gives no opportunity count for TT or SS.** So the
larger half of the floor rests on a hand-wave.

Walking it with the survey's *own* honest method (case = endpoint × mechanism, line 143-148): the four
new SUTs yield ~10 with mechanism-multiplication already applied; if TT and SS have comparable real
masked-write surfaces (~4–8 each, the survey's realized scale), the total is **~20–30 S1**, and the
F-corpus adds only ≥6→10 replications → **~26–40, short of 45.** The one prior document that *did* reach
45 — `benchmark/README.md` §8 line 124-127 ("3 SUTs × ~7 endpoints × ~2 classes ≈ 40–60 S1") — is
**directly refuted** by this survey: TeaStore has **one** user-facing durable write, not seven (survey
line 34); Boutique persists **zero** orders (line 113). The freeze inherited the ≥45 floor from that
optimistic lineage without re-deriving it against the survey that undercuts it.
**How it bites:** step 3 populates to a floor that likely cannot be met, discovered only after weeks of
deploy/enable/inject work; or the gap is closed by padding (counting weak breadth cases), which the
"units-of-measure honesty vs RCAEval" defense (freeze §5) then cannot survive at artifact review.
**Fix:** either extend the depth survey to TT + SS with explicit endpoint×mechanism counts *now* and
recompute the floor from the sum, or lower S1 to what the survey supports and disclose it in the freeze.
Do not leave "TT + SS carry the balance" as the load-bearing sentence. (Same defect applies to **S2≥35**:
survey gives 16 across the four new SUTs + 2 packaged corpora capped at 2 cases each; TT/SS must carry
~17 with zero survey backing.)

### B3. The `fault.mechanism` enum (4 values) cannot satisfy "≥4 mechanisms per write-path SUT" for a broker-less SUT.
Frozen enum = `flag | mesh-sever | broker-policy | code-level | none` (`c2-freeze.md` line 58; four
non-none values). Frozen diversity floor = "S1 ≥ 4 distinct fault MECHANISMS per write-path SUT"
(`c2-freeze.md` line 184; plan §2.3 line 96). **TeaStore has no message broker** ("no MQ, no async
writes" — survey line 34) → it can exhibit at most **3** of the 4 enum values {flag, mesh-sever,
code-level}. It is nonetheless declared a write-path SUT that "meets ≥4 mechanisms" — but the survey
reaches 4 only by naming mechanisms **outside the enum**: "designed-toggle / **DB-down** / mesh-sever /
**input-driven**" (survey line 60, 145). DB-down and input-driven have no enum value.
**How it bites:** at population every TeaStore/OTel/Boutique case authored with a "DB-down" or
"input-driven" mechanism is **schema-invalid** against the frozen enum; or the diversity floor is
"met on paper" only by silently widening the vocabulary the freeze fixed. And the honest fix in the
other direction — excluding TeaStore from the write-path class because it can't reach 4 enum
mechanisms — deletes 4–5 S1 cases and makes B2 worse.
**Fix (freeze-time):** either expand the mechanism enum to cover the real categories the survey found
(add e.g. `dependency-down`/`resource-failure`, `input-driven`; keep it closed) **and** re-word the
floor as "≥4 distinct mechanisms *from the enum, as applicable to the SUT's architecture*, minimum 3
for broker-less SUTs," or drop the per-SUT "≥4" to "≥3" and lean on the cross-SUT count. Decide now;
it changes both the schema and a floor.

### B4. The frozen observable model assumes a durable read-back — the benchmark already owns cases that have none.
`c2-freeze.md` §2 requires, per case, `fault.expected_observable: <what SHOULD change durably and does
not>` (line 61) and `oracle_eval.observable_pin: <the single durable observable the case tests>`
(line 75). But the committed SS flagship seed `sockshop-shipping-swallowed-enqueue-001.json` states, in
source, that **there is no durable observable**: "no black-box read-back reflects broker consumption
(order.shipment is written at creation regardless of enqueue success)… only the trace-shape/masking
oracle can see this class" (case line 50; `mist_dataintegrity_oracle: not_applicable`, line 44). The
async **OTel-Demo flagship** is the mirror problem: its read-back is an **out-of-band psql probe on the
accounting DB** (survey line 18, 94), not an API GET — a different modality with no schema field, and
one whose MIST-bindability (a raw-SQL observable) is nowhere confirmed.
**How it bites:** the two headline S1 positives of the benchmark (SS swallowed-enqueue; OTel async
Kafka-loss) cannot be faithfully expressed: the schema forces a "durable observable" that the first
doesn't have and mis-types the second. At scoring (§4 line 164-177) these become NOT_EVALUABLE or
mis-authored, and the E2 "trace-invisible N-vs-0" story loses its cleanest exhibit.
**Fix:** make the observable a typed, oracle-agnostic field — `readback: {modality: api-get | sql-probe
| broker-count | trace-span-presence | none-durable, locator: …}` — so trace-only and out-of-band cases
are first-class, and record whether MIST can bind each modality. This is exactly the schema property an
*oracle-evaluation* benchmark must freeze.

---

## MAJOR

### M1. κ over pooled n≥50 is unreachable in the pre-registered S3-scarce branch, and is calibration-inflated.
Pooled = calibration (~20; `c3-rater-materials.md` §6 line 129) + S3. Plan §1/§3.3 *pre-register* S3 < 20
as a plausible outcome (line 30-34, 176). Then pooled = 20 + (<20) = **< 50**, so "n ≥ 50"
(plan §3.1 line 147; freeze §3 line 148) cannot hold in the branch the plan itself foregrounds. Worse:
calibration cases are drawn from S1/S2 known-label positives/benigns (§6 line 129) — the *easy* cases —
so inter-rater κ on them is near-ceiling; padding the reported pooled κ with them to reach 50 **inflates**
the coefficient relative to the hard wild S3 where disagreement actually lives.
**Bites:** the headline C3 agreement statistic is either uncomputable-at-target or optimistically biased
— a reviewer discounts it immediately.
**Fix:** size the calibration set to guarantee pooled ≥ 50 given expected S3 (S1+S2 ≥ 80 makes this
free), and **report calibration-only κ and S3-only κ separately** in addition to pooled, so the
easy-case inflation is visible rather than hidden.

### M2. The rater "normalized mix" is not actually indistinguishable — the clean-run field is a tell.
`c3-rater-materials.md` §0 (line 24-26) claims "A rater cannot tell a flagged case from a calibration
case," and each case carries "the observed state on the paired clean run **where one exists**" (line 27).
Calibration cases are S1-derived and carry a negative control (`c2-freeze.md` schema line 63-66: every
S1 case MUST have one); genuine **wild S3 events have no clean twin**. So presence/absence of the
paired-clean-run observation **separates calibration from measurement cases deterministically**,
breaking the blindness invariant the whole C3 precision claim rests on (§0 line 15).
**Bites:** a rater who notices "the ones with no clean run are the real study" can shade labels; the
soundness argument for tool-blindness is undermined.
**Fix:** normalize the field's presence — either strip the clean-run observation from *all* rater-facing
cases, or synthesize/withhold it uniformly so it carries no signal about stratum.

### M3. The M-yield cluster-audit cases are in the rater workload but absent from the rater package.
Plan §3.1 (line 138-140) sizes rater work as "S3 (≤40) + calibration (~20) + **the M-yield cluster
audit sample**." The rater materials describe the mix as S3 + ~20 calibration **only** (§0 line 24; §1
line 37 "~60–100"). The M-yield audit cases (generation-driven finds) get no format, no blindness
handling, no ballot path.
**Bites:** either those cases go un-adjudicated (M-yield precision unsupported) or they are injected
into the rater set ad hoc at step 4, unblinded and unaccounted — precisely the improvisation the freeze
exists to prevent.
**Fix:** fold the M-yield audit sample into §0's normalized mix and the §4 ballot explicitly; state its
size and how it stays blind.

### M4. No schema home for the arm-3 per-endpoint authoring cost — a promised headline datum.
Plan §4 E2 (line 197-199) elevates the hand-authored span-presence assertion's **per-endpoint authoring
cost** to "the automation-gap datum," and R4 confirms it is the real frontier arm
(`r4-comparator-spike.md` line 40-42). The frozen schema records comparators only as
`comparator_configs:[{arm,config}]` (freeze line 82-84) and `comparator_verdicts` (line 108) — **no
field for authoring effort/time.**
**Bites:** the automation-gap number that motivates MIST's value is not machine-recordable → captured
ad hoc or not at all, and not aggregable across endpoints.
**Fix:** add `comparator_configs[].authoring_cost: {minutes, endpoints_covered, notes}` to the freeze.

### M5. `trace_visibility` conflates "invisible by construction" with "un-instrumented" — an attackable E2 headline.
The enum is `error-span-visible | span-presence-visible | trace-invisible` (freeze line 76). Two very
different things land in `trace-invisible`: (a) fabricated-ack with a normal span tree — *fundamentally*
invisible to any trace oracle (the class-novelty claim); (b) TeaStore, whose Kieker-not-OTel traces are
"converter-or-exclude decided at step 2.5" (`e-sut-applicability-matrix.md` line 13, 31) — if excluded,
its 4–5 cases become trace-invisible for a *contingent tooling* reason. Mixed in one row, the "MIST
catches what trace tools fundamentally can't" story is padded by "we didn't instrument TeaStore."
**Bites:** a reviewer splits the N-vs-0 row and the headline shrinks; and the step-2.5 "exclude" branch's
effect on the E2 trace-visible denominator is not pre-registered.
**Fix:** split the enum (`trace-invisible-by-construction` vs `trace-uninstrumented`), and pre-register
the TeaStore-exclude branch's handling now (as S3≈0 is pre-registered).

### M6. S3 "wild" cases collide with the "automated per-case replay on a clean cluster" acceptance.
Acceptance (`c2-freeze.md` §5 line 187; plan §2.4) requires **every** case reproduce via an automated
replay script, and the §4 scoring contract (line 164-177) is written as live deploy→fault-leg→control-leg
execution. Genuine wild S3 events (races, load-dependent timing) may not be deterministically replayable,
and by definition have no injected fault leg or negative control. The schema exempts S3 from nothing
(only adds `adjudication_record`, line 115).
**Bites:** S3 cases fail the reproduction acceptance gate, or are silently re-manufactured as
deterministic injections (destroying their "wild" provenance), or the §4 contract has no path to score
an oracle on a *recorded* S3 artifact.
**Fix:** define S3 reproduction as "captured-artifact + best-effort replay, non-determinism documented,"
and add an S3 branch to the §4 scoring contract (score against the recorded transcript, not a live run).

### M7. Partial / aggregate-write cases have no convention — the rubric's "did not land" doesn't cover them.
The survey found TeaStore's "order acked and present but items lost" (survey line 52) and analogous
child-collection losses; g3-result's own residue names the OBJECT/aggregate class. The genuine rubric
says "the durable write … did not land" (freeze §3 line 130) — but here the *parent* landed and a
*child collection* did not. `observable_pin` is free text so it *can* point at the items collection, but
there is no marker or convention, so populators and raters will author it inconsistently.
**Bites:** inter-rater disagreement on partial writes inflates the underspecified fraction and depresses
κ for a reason that is a spec gap, not a real ambiguity.
**Fix:** add a `write_shape: whole | partial-aggregate | transition` field and one worked
partial-write example to the rubric packet.

### M8. Human-subjects / IRB path is entirely absent from the longest-lead item.
`c3-rater-materials.md` has consent + compensation (§2) but **no IRB / ethics-board determination**
(approval or exemption) anywhere, for a *paid* study recruiting external humans (§7). A-venues
increasingly require an ethics statement, and institutional IRB can add weeks — onto the item the plan
itself calls "the longest-lead" (§ preamble line 3).
**Bites:** recruitment (2–6 wk lead, on the critical path per plan §5 line 213) cannot safely start, and
the paper's ethics section has no basis.
**Fix:** add an IRB/exemption determination step as a §7 precondition surfaced to the user alongside the
channel decision; state the expected exemption rationale (open-source code review, no PII, anonymized
release).

---

## MINOR

- **m1 — claim-string version drift across "frozen" docs.** `c2-claim-sweep.md` line 6 and plan §2 still
  read "per-case *provenance* taxonomy"; the freeze hardened it to "per-case *label-provenance*
  taxonomy" (line 10-14); `benchmark/README.md` line 12-15 carries a *third* wording ("first
  open-source labeled benchmark of these faults"). Three slightly different "frozen" strings in the
  repo. **Fix:** annotate the inputs to point at the hardened string, or restate it once and reference.
- **m2 — rater hours vs case-count inconsistent.** Pay basis is "15–45 h ≈ 2–3 days"
  (`c3-rater-materials.md` line 59) but the set is "~60–100 cases" at "15–45 min" (line 37) → up to
  100×45 min = **75 h**, under-compensated at the high end. **Fix:** reconcile the case-count band and
  the hours/comp band (cap the set or widen the estimate).
- **m3 — the "one frozen MIST commit" is a placeholder, not pinned.** Schema `mist_commit: <sha>`
  (freeze line 81) with the invariant "IDENTICAL across every case" (line 118), but no sha and no
  pin-timing criteria — against an actively-developed MIST (main_track). **Fix:** state the pin criteria
  (feature-complete: value-delta + supplied hooks + injectors + fabricated-ack in one buildable commit)
  and when it is stamped.
- **m4 — scoring-harness home + license undecided.** The §4 harness scores cases; the audit puts MIST
  under LGPL-by-reference and the benchmark in a standalone Apache repo (`c2-license-audit.md` line
  15-17, 30, 48-49). If the harness reuses MIST code it cannot be Apache in the standalone repo. **Fix:**
  state where the scoring harness lives and its license.
- **m5 — Gate-4 "≥4 baselines" is now 3 (disclosed) — keep downstream wording honest.** R4 re-scopes
  TraceAnomaly off the frontier → 3 frontier comparators (`r4-comparator-spike.md` line 63-67), which
  plan §1 (line 49) pre-registers as an acceptable disclosed narrowing. Ensure the paper/eval docs say
  "3 frontier trace comparators," never "≥4 baselines."
- **m6 — F-corpus billed as "the main remaining lever" is a 4-case swing.** Matrix line 41 / freeze §5
  line 186: floor ≥6, target ≥10 — a 4-case range cannot close the ~15-case S1 gap of B2. Also the
  freeze never verifies the swallowed-subset (F6/F8/F10/F20) is actually *masked-2xx* (vs loud failure),
  a precondition for those to count as S1. **Fix:** verify the F-subset is in-class; stop calling ≥6→10
  "the main lever."
- **m7 — eligibility-screen / calibration overlap unspecified.** §9 uses "two calibration-style cases"
  (line 156); if they are the actual §6 calibration cases the rater sees them twice (biasing κ). **Fix:**
  state the eligibility cases are disjoint from the 20 calibration cases.

---

## Not-considered list (things a freeze should have addressed and didn't)
1. The **pre-existing `benchmark/` schema + rubric + 6 seed cases** (supersession, migration, single
   source of truth) — B1.
2. A **machine-validatable schema file** for the frozen schema (only prose invariants shipped) — B1.
3. **Read-back / observable modality** as a first-class typed field (api-get / sql-probe / broker-count /
   trace-only) — B4.
4. **S3 wild reproducibility semantics** and an S3 branch of the scoring contract — M6.
5. **M-yield audit cases** inside the rater package — M3.
6. **IRB / ethics determination** for paid human raters — M8.
7. **Arm-3 authoring-cost** capture field — M4.
8. **Partial / aggregate-write** observable convention — M7.
9. The **actual MIST study-commit pin** + timing — m3.
10. **Scoring-harness location + license** under the LGPL/Apache split — m4.
11. Per-case **population cost** for S1 (the by-description F-corpus re-implementation is *more* work than
    copying — license audit conduct rule 1 — and is unestimated).

---

## Bottom line
The story-level freeze is strong; the *machinery* freeze is not yet load-bearing. Two committed C2
schemas with opposed rubrics coexist unacknowledged (B1); the S1 (and S2) floors are contradicted by the
freeze's own normative survey (B2); a frozen enum and a frozen floor are mutually unsatisfiable (B3); and
the frozen observable can't hold the benchmark's flagship cases (B4). Each is a change to the frozen
schema or floors — i.e., a re-freeze, which is cheap now and expensive after step-2 population commits to
it. Fix B1–B4 + M1–M8, re-freeze, then proceed.
