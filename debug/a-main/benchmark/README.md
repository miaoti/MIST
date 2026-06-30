# MIST labeled fault benchmark (C2) — structure + schema

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
    TT-adminbasic-contacts-lostwrite-001.json stratum 1, POSITIVE  (lost write on a 2nd service; cleanest per-entity read-back)
    bookinfo-ratings-benign-001.json         stratum 2, NEGATIVE  (designed degradation; the naive-oracle FP trap)
```
The seed cases double as schema-validation fixtures (covering each stratum/role) and as the first real
seeds; the two lost-write positives sit on different write-path services (adminroute, adminbasic/contacts)
to show the oracle is not endpoint-specific.

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
