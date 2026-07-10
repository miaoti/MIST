# MIST labeled fault benchmark (C2) — structure + schema

> **STATUS (2026-07-10b): SEED / PILOT corpus, rev-2 schema, 3-cold-reviewed + breadth wave + TRACED-CAPTURE WAVE executed.**
> The 8 flagship/breadth legs are now **capture-of-record on an OTel-instrumented deploy** (agent 1.33.6
> pinned, per-leg Jaeger exports, frozen pre-committed scorer): the **N-vs-0 comparator cells are MEASURED**
> — on the fabricated-ack pair `naive_span_error` and `tracetest_presence` **ran and missed** (the /drawback
> span is present with a fake-normal status; naive N=2, presence N=1 — createaccount presence pinned n/a);
> on the breadth pair the presence assertion **ran and caught** (skipped-call span absent; traced controls
> validate the selectors). `mist_trace_shape` = traced-but-not-run (Branch-B, deferred to 2.5/E2 —
> disclosed in the freeze §6). **DB-granularity disclosure (measured):** fault legs carry ZERO DB-client
> spans vs controls' 2/6/3 — the lost write IS visible at DB-span granularity; the presence column is
> pinned at cross-service HTTP-span granularity (service-map authoring), where invisibility holds.
> The AUTHORITATIVE C2 pre-registration is `debug/a-main/c2c3/c2-freeze.md` (rev 2 + rev-2.1 amendments).
> All **14 cases in `cases/` validate** (`schema/validate_cases.py`, exit 0); the two `eligibility/` cases
> (rater screen, outside the measurement corpus) also validate.
> This README describes the design + the *current populated pilot*; read the freeze for the frozen truth.
> `schema/rubric.md` (v0.1.0) is superseded by `c2-freeze.md` §3 + `c2c3/c3-rater-materials.md` §3.

> **Read this first — what the pilot IS and is NOT (REVIEW-CORPUS-RECONCILIATION.md, 3-cold-review):**
> The populated corpus is a **seed/pilot + a pre-registered scale plan**, NOT a completed benchmark. It is
> **6 positives / 8 negatives** (report it that way — never a bare "S1 count"). Of the 6 positives,
> **4 are captured *discriminating* positives** across 3 write-path services: 2 fabricated-ack fork flags
> (`TT-cancel-refund-fabricatedack`, `TT-createaccount-agreement`, on `ts-inside-payment-service`) + 2
> skipped-cross-service-persist flags captured 2026-07-10 (`TT-adminroute-lostwrite`,
> `TT-adminbasic-contacts-lostwrite` — as-deployed only `mist_readback` catches them; pre-registered as
> comparator-catchable under trace instrumentation, i.e. NOT MIST-unique once traced). **PHASE B (2026-07-10c, unanimous-accept plan): the FP/TP pair's COMPARATOR columns are now
> MEASURED** — bookinfo benign: naive=FLAG + presence=FLAG (both structural columns false-positive);
> sockshop genuine: presence=FLAG TP, naive pre-registration REFUTED by capture (no producer span →
> no_flag FN, disclosed); sockshop clean control: naive=FLAG (docker-socket consume-side error —
> naive FPs even on clean operation). `mist_trace_shape` stays Branch-B on all pair legs — **the
> MIST-side pair-separation claim remains PRE-REGISTERED** (freeze §6 scoping row). The S3 wild
> stratum remains the scale plan (§8); a `specified` case's `oracle_expectation` is a design TARGET.

> **PREP deliverable (main-track). NO MIST tool code.** Contribution **C2 — an open-source labeled
> benchmark of masked-downstream / acked-but-lost data-integrity faults** in OSS microservice systems.

## 1. Why this exists (scope of the claim)
The contribution is **accessibility + automation + an open benchmark**, not "first to detect masked
faults" (Cast, ICSE-SEIP'26 arXiv:2602.00972, already detects masked-2xx + silent dual-write
inconsistency on a *closed* 48-bug Huawei benchmark). The defensible, scoped claim is the **first
OPEN-SOURCE labeled benchmark** of these faults across OSS SUTs, with a pre-registered genuine-vs-benign
rubric + a blind-labeled wild stratum. State it exactly that way; do not over-claim novelty of the
phenomenon, and — per the review — **do not call the current pilot "the benchmark"**: it is the schema +
rubric + pilot seed + scale plan.

## 2. Layout + the current 14 cases
```
benchmark/
  README.md                         this file
  schema/
    fault-case.schema.json          JSON Schema (draft 2020-12) for ONE labeled case — rev 2
    validate_cases.py               validator: cases/*.json vs the schema (exit 0 iff all pass)
    rubric.md                       v0.1.0 rubric (SUPERSEDED by c2-freeze §3 + c3-rater-materials §3)
  cases/                            14 cases, all rev-2-valid (6 positive / 8 negative)
  eligibility/                      2 rater-screen cases (§9 instrument; OUTSIDE the measurement corpus)
  b4/                               blind-label harness + capture specs + captured sidecars
```

| case_id | stratum | label | capture | role |
|---|---|---|---|---|
| `TT-cancel-refund-natural` | 1 | positive | captured | genuine error-swallow ack `{1,"error"}`; **tell-bearing** (segregated) → detection TIE, MIST edge = localization |
| `TT-cancel-refund-fabricatedack` | 1 | positive | **captured** | **clean MIST win** (synthetic worst-case): success-shaped-clean, only `mist_readback` catches |
| `TT-createaccount-agreement` | 1 | positive | **captured** | body-carrying-CRUD read-back positive (breadth). *Agreement/fairness role → g3 head-to-head, not scored here (R5)* |
| `TT-cancel-refund-clean` | 1 | negative | captured | clean control (drawback=none; refund lands) |
| `TT-createaccount-clean` | 1 | negative | captured | clean control (createAccount=none; row persists) |
| `TT-adminroute-control` | 1 | negative | captured | clean control (base image; route persists) |
| `TT-adminroute-lostwrite` | 1 | positive | **captured** | breadth: skipped cross-service persist, clean fabricated ack, client-supplied id absent from read-back. Pre-registered: presence-assertion *also* catches under instrumentation |
| `TT-adminbasic-contacts-lostwrite` | 1 | positive | **captured** | breadth on a 2nd service (collection read-back); pod-log corroborated (`LOST_WRITE_FAULT` line matches the submitted id) |
| `TT-adminbasic-contacts-control` | 1 | negative | captured | **same-binary twin** of the adminbasic positive (identical fork digest, env flag off → persists); isolates the flag as the only variable |
| `TT-contacts-dedupe-benign` | 2 | negative | captured | benign trap for the **ack rule**: 2xx + `status:0` soft-reject; `body_marker` wrongly flags |
| `TT-contacts-noop-modify-benign` | 2 | negative | captured | benign trap for the **read-back oracle itself**: idempotent no-op PUT acks `{1,"Modify success"}` success-shaped-CLEAN (no tell) + zero durable delta by design; FPs any "acked write ⇒ durable delta" rule (closes R6) |
| `bookinfo-ratings-benign` | 2 | negative | **captured** | FP half of the precision pair, MEASURED: naive=FLAG + presence=FLAG on the benign leg (both structural columns fail the trap); mist_trace_shape Branch-B |
| `sockshop-shipping-swallowed-enqueue` | 1 | positive | **captured** | TP half, MEASURED: presence=FLAG TP (consume span absent, validated baseline); naive pre-registration REFUTED (no_flag FN, disclosed T2) |
| `sockshop-shipping-control` | 1 | negative | captured | clean twin (rabbit up; consume span present). MEASURED surprise: naive=FLAG on the clean control (queue-master docker-socket error every consume, diagnosed) |

## 3. The three strata (rev-2.1 R1)
- **Stratum 1 — label known by construction (not rater-dependent).** Either (a) a **positive** with
  `provenance ∈ {by-injection, natural, replicated, vendor}` (`natural` = a genuine defect grounded in the
  SUT's own source/docs, e.g. sockshop's swallowed enqueue), or (b) a **paired clean control** (`fault.mechanism=none`,
  the no-fault twin of a positive). `negative_control.present` is required on S1 **positives**.
- **Stratum 2 — benign traps (false-positive stratum).** Designed-degradation / soft-reject paths labeled
  `negative` **by documented design** (`doc_citation` required): bookinfo `reviews→ratings` (istio PR #15489);
  the contacts dedupe soft-reject (`status:0`). These are what a naive oracle mislabels; beating them on
  precision/FP is the headline test.
- **Stratum 3 — adjudicated wild traffic.** Labels by ≥2 blind MIST-blind raters per the rubric; S3-only κ is
  the PRIMARY reliability statistic. **None populated yet** (the C3 rater study output; scale plan).

## 4. What a case records — the anti-circularity rule + evaluability
Each case fixes the **target** (`entry_endpoint`, `dependency`, typed `readback`), the `fault` + its clean
twin, the `ground_truth` label + rationale + source, and the **expected verdict of each oracle** in
`oracle_expectation` (7 columns). An oracle is **correct** on a case iff its verdict matches its
`oracle_expectation` entry; for baseline columns that equals `(verdict==flag)==(label==positive)`.

Two oracle-column classes, kept distinct on purpose (this defuses the "circular ground truth" objection):
- **Baseline / comparator columns** (`status_code`, `schema`, `body_marker`, `naive_span_error`,
  `tracetest_presence`) — **deterministic by construction**. `body_marker` flags iff
  `ack_content_visibility != success-shaped-clean`.
- **MIST columns** (`mist_readback`, `mist_trace_shape`) — the **target** (measured at eval, **never** ground
  truth). A mismatch is precisely an FP or FN.

**Evaluability is keyed to `capture_status` (rev-2.1 R2/R3):** a **`captured`** case records **as-deployed**
verdicts — a trace oracle on a `trace-uninstrumented` deploy has no input and is `not_applicable` (NOT
`no_flag`, which would credit an unearned true-negative). A **`specified`** case records the **design
expectation** (intended-instrumented, precondition-gated) and is a pre-registered TARGET, **never tallied as
a measured result**. Consequence: the `trace-invisible-by-construction` "N-genuine-vs-0-caught" recall row is
earned only by a *traced* capture (trace oracles ran and missed); the two fabricated-ack cases are
`trace-uninstrumented` today, so that row is a pre-registered claim.

Verdict values: `flag`, `no_flag`, `not_applicable`.

## 5. Two design points the review sharpened
- **The FP/TP precision pair (R4).** bookinfo (benign) and sockshop (genuine) are structurally identical
  (an error/absence under a 2xx). `naive_span_error` **and** `tracetest_presence` FP on bookinfo and TP on
  sockshop — **neither separates benign from genuine.** The ONLY separating column is `mist_trace_shape`
  (`no_flag` bookinfo / `flag` sockshop), and only via a **semantic** discriminator: ratings is a
  contractually-OPTIONAL read with no required durable effect (and the entry response even communicates the
  degradation), whereas sockshop's lost shipping task is a REQUIRED durable effect masked behind an
  identical-to-success ack. This advantage is a **pre-registered claim validated on live capture**, not a
  pilot result (both cases are `specified`).
- **The "agreement" anchor lives in the head-to-head, not here (R5).** `TT-createaccount-agreement` is scored
  in C2 only as a body-carrying-CRUD read-back positive (`mist_readback=flag`). The claim that a state-clause
  contract comparator ALSO binds its postcondition is a **measured** quantity (authoring/bindability
  dependent) → it belongs to the g3 head-to-head (`debug/a-main/g3-comparator-tt/`, Rider-2 bindability).
  The C2 table has **no** contract-invariant column, so this row is indistinguishable from a MIST-only win;
  do not read it as an agreement from the table alone.

## 6. Add a case, then validate
1. Copy a case in `cases/`, unique `case_id`, fill every required field (the schema rejects unknown keys +
   bad enums; `additionalProperties:false` throughout).
2. Set baseline columns by construction; set MIST columns to the correct target verdict; honor the
   `capture_status` evaluability rule (§4).
3. `python3 debug/a-main/benchmark/schema/validate_cases.py` (Python `jsonschema` ≥ 4, draft 2020-12).

## 7. Honest caveats
- **Construct bias (R6).** The captured discriminating positives are all **synthetic fork flags** (2
  fabricated-ack + 2 skipped-cross-service-persist) — faults *shaped* to be visible primarily to a read-back.
  They **bound** the read-back oracle's value; they are not evidence of prevalence. The realistic positives
  (sockshop natural swallow; cancel-natural) are respectively `specified`/unrun and tell-bearing/segregated.
  The formerly-missing benign case that stresses the read-back oracle itself is **CLOSED 2026-07-10**:
  `TT-contacts-noop-modify-benign-001` — an idempotent no-op modify acks success-shaped-CLEAN with
  legitimately-zero durable delta, found ON TrainTicket (no borrowed SUT; the earlier "TT has no such
  representative" claim applied to *creates* — dedupe signals `status:0` — not to idempotent *modifies*).
  Precision must be reported per-oracle **including MIST's read-back**, not only against the baselines.
- Strata 1–2 labels are by construction / documented design — legitimate ground truth (RCAEval, Nezha do the
  same), but **not** wild developer-confirmed bugs. The "real bug assertion-tools miss" claim requires the
  S3 wild stratum + Gate 3; it is not made by this pilot alone.
- This is the **PREP location**. At release, promote to a repo-root `benchmark/` or a standalone artifact
  repo with a DOI.

## 8. Scale plan (pilot → release; the pre-registered targets)
The 11 pilot cases prove the schema + rubric + a live clean-MIST-win. They are **not** the deliverable. The
release deliverable is the captured, scaled corpus. Pre-registered targets (SUTs × write endpoints × fault
classes × strata) and honest denominators live in `c2-freeze.md` §5 (the earlier "3 SUTs × ~7 endpoints ≈
100–140" arithmetic here was **refuted** by the §8.5-3 depth survey — TeaStore = 1 durable write, Boutique =
0 — and is superseded by the freeze's two-denominator recount; do not cite the old count). **Deferred
captures (need live deploys; none is a blocker to the honest pilot):**
- ~~Traced captures of the two fabricated-ack cases~~ — **DONE 2026-07-10 (traced-capture wave, plan rev-2
  3-cold-reviewed)**: comparator N-vs-0 cells measured (ran-and-missed); breadth presence-catches measured
  symmetrically; DB-granularity disclosure measured. REMAINING from that wave: `mist_trace_shape` =
  traced-but-not-run (Branch-B) → runs at step 2.5/E2 with MIST bound to Jaeger.
- The FP/TP pair live (bookinfo redeploy + sockshop tenancy window) with **queue-master consume-span**
  instrumentation → validate the `mist_trace_shape` precision advantage.
- ~~Fork-built breadth positives (adminroute / adminbasic) + the adminbasic clean control~~ — **DONE 2026-07-10**
  (breadth wave: fork images e5af2936/1c9913ea from the clean a1767ab3 tree; captures triple-corroborated;
  deployments restored to base 1.0.0; runbook rule added: probe-first after any rollout — nacos/ribbon
  routes to terminating pods for tens of seconds).
- ~~An **idempotent-no-op benign read-back control**~~ — **DONE 2026-07-10** on TT itself
  (`TT-contacts-noop-modify-benign-001`; no borrowed SUT needed).
- The **S3 wild-adjudicated stratum** (the C3 rater study).

## 9. Migration map — schema v0.1.0 → rev 2 — **DONE (2026-07-09)**
All 6 legacy v0.1.0 cases were migrated field-by-field to rev-2 and validate; the 5 flagship cancel/createaccount
cases were authored rev-2. Field deltas applied: `schema_version 0.1.0→2.0.0`; `injection.mechanism` **split**
→ `fault.mechanism` (diversity) + `fault.injection_method` (method); `target.readback_endpoint` → typed
`target.readback{modality,locator,expect_*,mist_bindable}` (the SS swallowed-enqueue + bookinfo →
`modality:none-durable` → `mist_readback_oracle:not_applicable`); added `oracle_eval.{ack_content_visibility,
trace_visibility,write_shape}`; `mist_dataintegrity_oracle` → `mist_readback_oracle`; **added**
`tracetest_presence_oracle`; `ground_truth` + optional `version`/`doc_citation`; `sut` + optional
`image_digest`/`replay_script`/`health_precondition`. See `REVIEW-CORPUS-RECONCILIATION.md` for the review
that hardened the result and `../REVIEW-CORPUS-RECONCILIATION.md`-driven per-case notes (each migrated case's
`provenance.notes` records its own deltas).
