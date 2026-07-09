# MIST labeled fault benchmark (C2) — structure + schema

> **⚠ SUPERSEDED BY THE STEP-1 RE-FREEZE (2026-07-08).** The AUTHORITATIVE C2 pre-registration is now
> `debug/a-main/c2c3/c2-freeze.md` (rev 2). This directory's `schema/fault-case.schema.json` has been
> **updated to rev 2** (in lockstep with the freeze); `schema/rubric.md` (v0.1.0) is superseded by
> `c2-freeze.md` §3 + `c2c3/c3-rater-materials.md` §3 (the observation-vs-verdict admissibility split).
> The **6 seed cases in `cases/` are still in v0.1.0 format and FAIL the rev-2 schema** — they are
> migrated at step-2 population per the migration map in §9 below. This README's §8 scale plan
> ("3 SUTs × ~7 endpoints") is **refuted by the §8.5-3 depth survey** (TeaStore = 1 durable write,
> Boutique = 0) and superseded by the honest two-denominator recount in `c2-freeze.md` §5. Read this
> README for the design rationale; read the freeze for the frozen truth.

> **PREP deliverable (main-track). NO MIST tool code.** This defines the structure, schema, and
> labeling rubric for contribution **C2 — the first open-source labeled benchmark of masked-downstream /
> data-integrity faults** in OSS microservice systems. The cases here are the *seed* (designed, label
> known by construction); traces + read-backs are recorded later at Gate 1. Date 2026-06-30.
> Plan context: `../README.md` §C2/§4, `../research/05-evaluation-and-benchmarks.md` §4, `../EXECUTION.md`.

## 1. Why this exists (scope of the claim)
The contribution is **accessibility + automation + an open benchmark**, not "first to detect masked
faults" (Cast, ICSE-SEIP'26 arXiv:2602.00972, already detects masked-2xx + silent dual-write
inconsistency on a *closed* 48-bug Huawei benchmark). The defensible, scoped claim is therefore the
**first OPEN-SOURCE labeled benchmark** of these faults across OSS SUTs, with a pre-registered
genuine-vs-benign rubric. State it exactly that way in the paper; do not over-claim novelty of the
phenomenon.

## 2. Layout
```
benchmark/
  README.md                         this file
  schema/
    fault-case.schema.json          JSON Schema (draft 2020-12) for ONE labeled case
    rubric.md                       pre-registered genuine-vs-benign labeling rubric (the adjudication guide)
  cases/
    TT-adminroute-lostwrite-001.json         stratum 1, POSITIVE  (acknowledged-but-lost write; only our oracle catches it)
    TT-adminroute-control-001.json           stratum 1, NEGATIVE  (clean control, same input, fault off)
    TT-adminbasic-contacts-lostwrite-001.json stratum 1, POSITIVE  (lost write on a 2nd service; COLLECTION read-back — adminbasic has NO per-entity GET, cold-review A)
    bookinfo-ratings-benign-001.json         stratum 2, NEGATIVE  (designed degradation; the naive-oracle FP trap)
```
The seed cases double as schema-validation fixtures (covering each stratum/role) and as the first real
seeds; the two lost-write positives sit on different write-path services (adminroute, adminbasic/contacts)
to show the oracle is not endpoint-specific. **Status (cold-review):** adminroute is
live-smoke-demonstrated; adminbasic is build-verified with its read-back capture pending G0 (see
`../prep/sut-fault-injection-capability.md` §9). Both read-backs are **collection membership by business
key** — adminbasic has no per-entity GET.

## 3. The three strata (from the evaluation design, §4 of doc 05)
- **Stratum 1 — positive ground truth.** Injected / replicated / vendor faults with a label known *by
  construction*: our SUT-side `LOST_WRITE_FAULT` (see `../prep/sut-fault-injection-capability.md`),
  TrainTicket's F-corpus, OTel-Demo vendor fault flags, RCAEval/Nezha-style controlled injection.
- **Stratum 2 — benign traps (false-positive stratum).** Designed-degradation / eventual-consistency
  paths labeled `negative` by documented design (e.g. Bookinfo `reviews→ratings`). These are what a
  naive "any error span under a 2xx" oracle mislabels; beating them on precision/FP is the headline test.
- **Stratum 3 — adjudicated wild traffic.** Labels set by ≥2 human raters per `rubric.md` §E (Cohen's κ,
  third-rater adjudication). The `adjudication` block is required for stratum-3 cases.

## 4. What a case records — and the anti-circularity rule
Each case fixes the **target triple** (`entry_endpoint`, `dependency`, optional `readback_endpoint`), how
the fault was produced (`injection`), the `ground_truth` label + rationale + source, and the **expected
verdict of each oracle** in `oracle_expectation`. From those, precision / recall / FP for any oracle are
computable directly:

> An oracle is **correct** on a case iff `(verdict == flag) == (ground_truth.label == positive)`.

Two oracle-column classes, kept distinct on purpose (this is what defuses the "circular ground truth"
objection):
- **Baseline columns** (`status_code_oracle`, `schema_oracle`, `body_marker_oracle`,
  `naive_span_error_oracle`) — **deterministic by construction**. Their logic is fixed and simple, so the
  expected verdict is known without running MIST. (E.g. `naive_span_error_oracle` flags iff an error span
  sits under a 2xx entry — which is why it false-positives on `bookinfo-ratings-benign-001`.)
- **MIST columns** (`mist_trace_shape_oracle`, `mist_dataintegrity_oracle`) — the **target** (the correct
  verdict). MIST's *actual* output is measured against this at Gate 1; a mismatch is precisely an FP or
  FN. These are **not** assumed correct — the ground truth never reuses MIST's own signal.

Verdict values: `flag` (reports a fault), `no_flag` (reports clean), `not_applicable` (oracle doesn't run
on this class — e.g. the data-integrity oracle on a pure swallowed-downstream case).

`capture_status`: `specified` = designed, label known, traces/read-back not yet recorded (all three seed
cases today); `captured` = control/fault traces + read-back recorded on a live deploy and the
`provenance.*` paths populated.

## 5. How each case maps to the eval
- **E2 (oracle effectiveness):** strata 1–2 give known labels → precision/recall/FP frontier vs the
  baseline + trace-aware comparators.
- **E5 / A1 ablation:** the gap between `naive_span_error_oracle` (flags benign traps) and the MIST target
  on stratum 2 isolates the label-free discrimination — the precision/FP win at matched recall.
- **Gate 1:** capture the control/fault traces + read-back for the stratum-1 pair; confirm
  `mist_dataintegrity_oracle` fires on the positive and not on the control; measure read-back FP on stratum 2.

## 6. Add a case, then validate
1. Copy a case in `cases/`, give it a unique `case_id`, fill every required field (the schema rejects
   unknown keys and bad enums).
2. Set the baseline columns by construction; set the MIST columns to the correct target verdict.
3. Validate (Python `jsonschema` ≥ 4, draft 2020-12):
   ```
   python - <<'PY'
   import json, glob, pathlib
   from jsonschema import Draft202012Validator
   base = pathlib.Path("debug/a-main/benchmark")
   v = Draft202012Validator(json.loads((base/"schema/fault-case.schema.json").read_text(encoding="utf-8")))
   for f in glob.glob(str(base/"cases/*.json")):
       errs = list(v.iter_errors(json.loads(pathlib.Path(f).read_text(encoding="utf-8"))))
       print(("PASS " if not errs else "FAIL ") + pathlib.Path(f).name)
       for e in errs: print("   ", list(e.path), e.message)
   PY
   ```

## 7. Honest caveats
- Strata 1–2 labels are by construction / documented design — legitimate ground truth (RCAEval, Nezha do
  the same), but **not** wild developer-confirmed bugs. The headline "real bug assertion-tools miss" claim
  still requires **Gate 3** (see `../EXECUTION.md`); it is not made by this benchmark alone.
- This is the **PREP location** (`debug/a-main/benchmark/`). At release, promote to a repo-root
  `benchmark/` or a standalone artifact repo with a DOI. Building B1/B2 and capturing traces is BLOCKED
  until the user says "yes"; the schema, rubric, and seed cases are prep and need no tool code.

## 8. Scale plan (seed 4 → release N; the C2 floor-raiser — cold-review MAJOR)
The 4 seed cases are validation fixtures + first seeds, **not** the deliverable. C2 is a *citable*
floor-raiser only at release scale; until captured at scale it is a **credible** floor, not yet a **clear**
one — do not label it "clear" in the paper before the corpus is released. Pre-registered target + budget:

**Target: N ≈ 100–140 labeled cases** at first release (the sum of the strata below). (RCAEval's 735 is a
broad multi-fault RCA corpus, not the right yardstick; a *scoped* masked-fault / data-integrity oracle
benchmark is A-grade in the low hundreds.)

**Costed decomposition (SUTs × write endpoints × fault classes × strata):**
- **SUTs (3 write-path):** TrainTicket, TeaStore, Sock Shop (README §4 item 6 — all have a black-box
  read-back + achievable isolation).
- **Write endpoints:** ~6–10 CRUD write paths per SUT (TrainTicket alone exposes 74 POST / 27 PUT / 26
  DELETE — `../prep/target-triples.md`).
- **Fault classes:** stratum-1 positives = {LOST_WRITE (S2), SWALLOW_DOWNSTREAM (S1), MISSING_COMPENSATION
  (saga)} via the injection recipe below; stratum-2 benign traps = {eventual-consistency, retry-then-succeed,
  optional-dependency, **and the accept-then-drop / idempotent-no-op sub-class**} incl. ≥1 broker-async path
  per SUT (the make-or-break FP stratum — TOOL-PLAN P3/B2.4).
- **Rough count:** 3 SUTs × ~7 endpoints × ~2 applicable positive classes ≈ 40–60 stratum-1; ~10 benign
  traps/SUT ≈ 30 stratum-2; a **bounded, size-pre-registered** stratum-3 wild-adjudicated sample (see the C3
  caveat in README §6 — this is the only genuinely population-*prevalence* element) ≈ 30–50. Total ≈ 100–140
  (40–60 + 30 + 30–50).

**Injection recipe (reproducible case production):** S2 = SUT-flag `LOST_WRITE` (source-injected — the only
way to get skip-persist, §0 fact 6); S1 = Toxiproxy TCP cut on D's socket (errored-D) OR SUT-flag
`SWALLOW_DOWNSTREAM`; compensation = drive the named saga site, then fault a mid-saga dependency. Each case:
capture control+fault traces + read-back, populate `provenance.*`, flip `capture_status` specified→captured.

**This scale run IS the G3 corpus build** (EXECUTION G3) — gated behind B1/B2 (BLOCKED until "yes"). The
seed 4 exist now to prove the schema + rubric + one live positive; the 120–160 are the release deliverable.

## 9. Migration map — schema v0.1.0 → rev 2 (2026-07-08 re-freeze; step-2 mechanical port)
The rev-2 schema (in lockstep with `c2c3/c2-freeze.md` §2) is a superset with three structural changes;
the 6 seed cases are ported field-by-field at step-2 population. Field deltas:

| v0.1.0 | rev 2 | port rule |
|---|---|---|
| `schema_version: "0.1.0"` | `"2.0.0"` | bump |
| `injection.mechanism` (method enum) | **split** → `fault.mechanism` (diversity class) + `fault.injection_method` (method) | `sut_injector`→{mechanism per case: flag/code-level, method sut_injector}; `toxiproxy`→{mesh-sever, toxiproxy}; `vendor_flag`→{flag, vendor_flag}; `natural`→{mechanism per the natural path, natural}; `none`→{none, none}. Add `dependency-down` where a DB/backing-store kill is used. |
| `target.readback_endpoint` (optional endpoint) | `target.readback` (typed: modality/locator/expect_*/mist_bindable) | a GET read-back → `modality: api-get`. **The SS swallowed-enqueue case has NO durable read-back → `modality: none-durable` → `mist_readback_oracle: not_applicable`** (matches its existing `mist_dataintegrity_oracle: not_applicable`). The OTel async case → `modality: sql-probe`, `mist_bindable: <TBD at capture>`. |
| — (absent) | `oracle_eval.ack_content_visibility` **(new, required)** | author per case. TeaStore order-confirm = `success-shaped-clean` (A-verified: `-1` cleared from blob → clean 200). A `{1,"error"}`/sentinel body = `sentinel-in-body`/`status-field-tells`. |
| — (absent) | `oracle_eval.trace_visibility` **(new, required, 4-value)** | `error-span-visible` \| `span-presence-visible` \| `trace-invisible-by-construction` \| `trace-uninstrumented` (Kieker/un-instrumented ≠ by-construction). |
| — (absent) | `oracle_eval.write_shape` **(new, required)** | `whole` \| `partial-aggregate` (e.g. TeaStore order present, items lost) \| `transition`. |
| `oracle_expectation.mist_dataintegrity_oracle` | `oracle_expectation.mist_readback_oracle` | rename; add `tracetest_presence_oracle` (baseline). `body_marker_oracle` now flags iff `ack_content_visibility != success-shaped-clean`. |
| `ground_truth.label: positive\|negative` | `positive\|negative\|underspecified` | unchanged for S1/S2 (positive≡genuine, negative≡benign); `underspecified` is S3-only. Add optional `version` + `doc_citation`. |
| `adjudication.cohen_kappa` | `adjudication.kappa` | rename; S3-only κ is the PRIMARY statistic (R7). |
| `sut` | + optional `image_digest`, `replay_script`, `health_precondition` | label bound to `image_digest`. |

Unchanged: `case_id`, `stratum` (int 1/2/3), `capture_status`, `fault_class`, `target.entry_endpoint`,
`target.dependency` (+ `broker` kind added), `provenance`. Validation snippet (README §6) unchanged; run
it after the port to confirm all cases PASS rev 2. Migration is single-homed at checklist §1.95 (the corpus-factory NOW track — c3-case-corpus-plan.md
REV 2/M2 restored the governing plan's promote-before-deploy ordering); the seed cases FAIL rev 2
until migrated + short-capture-run at the pinned MIST commit (capture_status specified → captured),
by design.
