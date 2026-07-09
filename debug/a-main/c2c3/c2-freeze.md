# C2 FREEZE — claim, per-case schema, rubric, index format (plan v2 §2.4-1)

**Status: FROZEN ON COMMIT (re-freeze rev 2).** Any post-freeze change is a DISCLOSED AMENDMENT
(dated row in §6). This is **rev 2**, folding the step-1 3-cold-review (`REVIEW-STEP1-FREEZE-{A,B,C}`
+ `-RECONCILIATION`, R1–R8). Inputs discharged: related-work sweep (`c2-claim-sweep.md`), license
audit (`c2-license-audit.md`), §8.5-3 depth survey (`c2-depth-survey.md`, normative for S1 quotas).
**Supersession (R2):** this freeze SUPERSEDES the earlier prototype at `debug/a-main/benchmark/`
(schema v0.1.0 + rubric + 6 seed cases, 2026-06-30); the machine JSON schema in that directory is
updated to THIS model and the seed cases carry a migration map — see §6 and
`debug/a-main/benchmark/README.md`.

## §1 The frozen claim + obligations
**Claim string (final, for-the-record):** *"the first open-source labeled benchmark built for ORACLE
EVALUATION on masked-downstream / acknowledged-but-lost data-integrity faults — pairing positive
strata with a benign-trap false-positive stratum and a blind-labeled wild stratum, under a
per-case label-provenance taxonomy."*
- **The paper LEADS with the STUDY, not "first" (C-A1 / plan §1 writing rule).** The claim string is
  recorded here for priority-defense; the title/abstract/contribution order lead with C2+C3 as a
  measurement instrument + the first genuine-vs-benign measurement of masked-2xx. "First" is defended
  if challenged, never the headline.
- **Definition (first use, binding):** the label-provenance taxonomy classifies HOW each case's
  ground-truth label was established — `by-injection` (true by construction), `by-docs`
  (benign-by-design, cited to SUT docs/source), `by-adjudication` (blind-rated per the rubric) —
  orthogonal to fault taxonomies and to RCA label pipelines.
- **Proactive citations (sweep §3):** CloudAnoBench (benign-stratum axis; differs on Q3+Q4); the Uber
  Zenodo artifact (fault-class axis; raw/unlabeled). **Cast framing (C-A1):** Cast (ICSE-SEIP'26)
  establishes the class is industrially real (89 confirmed silent-2xx bugs) — cite as MOTIVATION; our
  differentiation is not merely openness but (a) the paired benign-trap FP stratum, (b) per-case
  oracle-evaluation labels Cast does not provide, (c) the blind-adjudicated wild stratum. **Filibuster
  (SoCC'21) differentiation (C-A1):** Filibuster is a resilience-TESTING framework with a
  fault-tolerance-bug application corpus; it carries developer-assertion/bug-report labels, NO
  benign-trap FP stratum, NO adjudication rubric, and is not masked-2xx-labeled for oracle eval — add
  this as a first-class defense row (moved into `c2-claim-sweep.md` §2).
- **Positioning (sweep finding 3):** OpenRCA 2.0 + the FP-aware TT benchmark EXCLUDE the masked class
  by construction (SLO filters; 84.4% "No Anomaly" discarded) — prevailing methodology filters out
  exactly the stratum this benchmark labels.
- Camera-ready watch-list: re-check OpenRCA 2.0 + FP-aware-TT release forms before submission.

## §2 Per-case schema (one YAML document per case `cases/<id>.yaml`; the machine JSON schema is
`debug/a-main/benchmark/schema/fault-case.schema.json`, kept in lockstep). Fields tagged `# @eval` are
filled during evaluation and are NOT frozen (freezing them would freeze results); the FREEZE covers
keys + admissible values.

```yaml
# ---- identity ----
id: <sut>-<stratum>-<slug>
schema_version: 2                        # this re-freeze; a bump is a disclosed amendment (§6)
stratum: S1 | S2 | S3
capture_status: specified | captured     # +adopted from scaffold: specified=label-by-construction, traces/read-back not yet recorded; captured=live traces + read-back recorded
title: <one-line human summary>

# ---- SUT + version pins ----
sut:
  name: trainticket | sockshop | teastore | oteldemo | boutique | bookinfo
  deploy:
    manifests: <path>
    manifests_change_notice: <bool>
    images: [{ref: <registry/name:tag>, digest: sha256:<64hex>}]   # label bound to THIS digest; never re-pushed
    replay_script: <path>                # automated per-case reproduce on a clean cluster
  health_preconditions: {checklist: <path>, data_seeding: <path|null>}   # +field 2

# ---- the exercised fault + its clean twin ----
fault:
  fault_class: acknowledged_lost_write | missing_compensation | swallowed_downstream_error | none   # +adopted: the SURFACE phenomenon (none == S2 benign clean)
  mechanism: flag | mesh-sever | broker-policy | dependency-down | code-level | none   # R1: added `dependency-down` (DB/backing-store kill); an app/runtime feature-toggle is the `flag` class; `input-driven` is a STIMULUS (see stimulus.workload_variant), NOT a mechanism, and does NOT count toward diversity
  injection: <kubectl apply <manifest> | @Value flag | rabbitmqctl | dependency scale-to-0 | runtime-toggle URL>
  target_service: <service the masked/lost write lands in>
  provenance_class: by-injection | by-docs | by-adjudication          # the label-provenance taxonomy
negative_control:                        # +field 1 (the no-fault twin)
  present: <bool>                        # every S1 POSITIVE must have one (rev-2.1 R1: a paired clean control does not need its own); the oracle's negative test (see §4 m2)
  replay_script: <path|null>             # same stimulus, no fault; the durable write DOES land

# ---- stimulus / workload ----
stimulus:
  script: <path>
  write_path: <bool>                     # carries a durable write? (bounds prevalence ceiling)
  workload_class: authored-scenario | builtin-loadgen | scripted-browse | none
  workload_variant: <null | e.g. bogus-user-id>   # R1: input-driven variants live HERE, not as a mechanism

# ---- the oracle-evaluation contract ----
oracle_eval:
  readback:                              # R4/B-B4: TYPED, oracle-agnostic observable (replaces prose observable_pin)
    modality: api-get | sql-probe | broker-count | trace-span-presence | none-durable
    locator: <endpoint path | SQL | queue name | span selector | null>
    expect_without_fault: present | landed | count-delta-positive | span-present
    expect_with_fault: absent | not-landed | count-delta-zero | span-absent
    mist_bindable: <bool>                # can MIST's oracle bind this modality? (sql-probe/none-durable may be false)
  ack_content_visibility: success-shaped-clean | sentinel-in-body | status-field-tells   # R8/C-A2: does the 2xx ack itself carry a machine-readable tell?
  trace_visibility: error-span-visible | span-presence-visible | trace-invisible-by-construction | trace-uninstrumented   # B-M5: split by-construction vs tooling-contingent
  write_shape: whole | partial-aggregate | transition          # B-M7: parent-landed/child-lost etc.
  oracle_mode: observe | paired          # U7 amendment: which MIST mode produced this case's verdict (observe = product single-leg; paired = eval harness)
  config_provenance:                     # +field 3
    mist_properties: <path>
    triples: <path|null>
    timeout_caps: <values>
    mist_commit: <sha>                   # +field 3b: ONE frozen study-wide MIST commit — PINNED at the END of the 1.9 UX wave (criteria incl. W0–W6; QuiescenceGate→verdict mapping frozen at this pin; promoted G1/G3 seeds re-recorded at it)
    mist_authoring:                      # U7 amendment: OUR side of the authoring-cost symmetry (unit = minutes per bound endpoint, same as comparator authoring_cost)
      tier: proposed-accepted | hand-written | expert
      minutes: <int>
  oracle_expectation:                    # +adopted from scaffold (R2): per-oracle EXPECTED verdict → precision/recall computable directly
    status_code_oracle:    flag | no_flag | not_applicable    # baseline columns = deterministic by construction
    schema_oracle:         flag | no_flag | not_applicable
    body_marker_oracle:    flag | no_flag | not_applicable    # a `!= -1` / sentinel check — flags iff ack_content_visibility != success-shaped-clean
    naive_span_error_oracle: flag | no_flag | not_applicable
    tracetest_presence_oracle: flag | no_flag | not_applicable
    mist_readback_oracle:  flag | no_flag | not_applicable    # MIST columns = TARGETS, measured at eval, NEVER ground truth (anti-circularity)
    mist_trace_shape_oracle: flag | no_flag | not_applicable
  comparator_configs:                    # @eval
    - {arm: naive | tracetest-error | tracetest-presence | traceanomaly | contract-invariant,
       config: <path>,
       authoring_cost: {minutes: <int>, endpoints_covered: <int>, notes: <str>}}   # B-M4: the arm-3 automation-gap datum

# ---- the label ----
label:
  value: genuine | benign | underspecified       # genuine≡scaffold `positive`, benign≡`negative`; underspecified is S3-only
  provenance: by-injection | by-docs | by-adjudication
  version: <n>                                    # +field 4
  version_validity: {image_digest: sha256:<64hex>, doc_citation: <url+version|null>}
  underspecified: <bool>
  adjudication_record: <path|null>                # S3 only

# ---- provenance / license (+field 5) ----
license:
  case_code: Apache-2.0
  case_data: CC-BY-4.0
  upstream_sut: <SPDX>
  sources: [<citations>]

# ---- raw artifacts + recorded verdicts ----
artifacts:                                        # @eval
  raw_logs: [<paths>]
  mist_verdict: FIRE | NO_FIRE | NOT_EVALUABLE | null
  comparator_verdicts: [{arm: <name>, verdict: <value>}]
```

**Schema invariants (machine-checkable at population unless marked AUDIT):**
- `stratum==S1` ⇒ the label is known **by construction** (not rater-dependent), i.e. one of:
  (a) a **positive** with `label.value==genuine` and `label.provenance ∈ {by-injection, natural, replicated, vendor}`
  (`natural` = a genuine defect grounded in the SUT's own source/docs, e.g. the SS swallowed-enqueue, not a fork flag);
  or (b) a **paired clean control** with `label.value==benign`, `fault.mechanism==none`, `ground_truth.source==by_construction`
  (the no-fault twin of an S1 positive). `negative_control.present==true` is required on S1 **positives** (a control does not need its own control).
  **[rev-2.1 amendment 2026-07-09 — REVIEW-CORPUS R1 (A-M4/C-F5): reconciles the freeze with the machine schema, whose `stratum` description already admits "natural"; the old clause "S1⇒genuine+by-injection" was refuted by the corpus (a natural S1 positive + 3 clean controls). REPORTING RULE: never report a bare "S1 count" as a positive count — the current corpus is 6 positives / 6 negatives (4 S1 clean controls + 2 S2 benign traps; updated by the 2026-07-10 breadth-wave amendment).]**
- `stratum==S2` ⇒ `label.value==benign`, `label.provenance==by-docs`, `label.version_validity.doc_citation!=null`.
  (AUDIT, not machine: that the degradation is genuinely by-design — m1.)
- `stratum==S3` ⇒ `label.provenance==by-adjudication`, `label.adjudication_record!=null`.
- `label.value==underspecified` ⇒ `label.underspecified==true` and the case is EXCLUDED from the
  primary precision denominator (§3, §4).
- **R8 segregation:** `ack_content_visibility != success-shaped-clean` (a tell-bearing case) is tallied
  separately and EXCLUDED from the primary *discriminating* S1-positive denominator (a trivial body
  oracle catches it — it does not discriminate oracle designs), exactly as `underspecified` is
  segregated. It still counts as a real positive for recall reporting, in its own bucket.
- `oracle_eval.readback.modality==none-durable` ⇒ `mist_readback_oracle==not_applicable` (the durable
  read-back oracle cannot run; only trace-shape/presence can — the SS swallowed-enqueue class).
- `oracle_eval.config_provenance.mist_commit` IDENTICAL across every case.
- Zero labels derived from MIST's own predicate (the `mist_*` oracle_expectation columns are TARGETS).

## §3 The adjudication rubric (frozen; the `c3-rater-materials.md` §3 rater copy is identical MODULO the disclosed R6 clean-run strip + the 2026-07-09 rater-rubric-delta amendment — R5)
**Three-way label {genuine, benign, underspecified}** (genuine≡positive, benign≡negative for S1/S2).
- **genuine** — a real acked-but-lost data-integrity fault: the SUT acknowledged the operation (HTTP
  2xx **with a success-shaped body — see the sentinel rule below**) while the durable write it promises
  did not land (or a downstream write in its causal closure did not), AND the intended behavior IS
  derivable from docs / spec / source.
- **benign** — the observed degradation is by-design / lived-with per docs / spec / source.
- **underspecified** — the intended behavior is NOT derivable from docs / spec / source. Excluded from
  the primary precision denominator; fraction reported; disagreement about underspecified-ness → the
  third adjudicator.
- **Async tie-break (re-landed from the superseded `benchmark/schema/rubric.md:45-50`; applies in the
  rater copy too — 2026-07-09 amendment):** if the write path is asynchronous, judge observed absence
  against any documented completion bound — absence past a documented bound → genuine; no derivable
  bound → underspecified (not genuine).
- **Partial writes:** source-stated-atomic but partly landed → genuine; atomicity unstated → underspecified.
- **Sentinel/"success-shaped body" rule (R8/C-A2):** a response body carrying a failure sentinel or
  error status-field (`-1`, `{1,"error"}`, a negative id) is **NOT success-shaped** — the ack is
  tell-bearing (`ack_content_visibility = sentinel-in-body | status-field-tells`), a trivial body
  oracle catches it, and the case is tracked in the segregated tell-bearing bucket (§2 invariant), NOT
  the primary discriminating denominator. A genuine *discriminating* positive requires 2xx AND (empty
  body OR a success-shaped body with no failure sentinel). (Note per A's source check: TeaStore's `-1`
  is CLEARED from the blob before the 200 → the client sees a clean 200 → TeaStore natural is
  `success-shaped-clean`, a tell-FREE exhibit; TT-natural `{1,"error"}` is tell-bearing and is graded a
  detection TIE in `g3-result.md` — both are recorded honestly by this field.)
  **Rater-copy divergence (2026-07-09 amendment, R-review F4):** this "success-shaped" precondition is
  ANALYST-side — it defines the *discriminating denominator* (§4), not the label. The rater-facing
  genuine definition DROPS it; a rater labels a 2xx `{1,"error"}` acked-but-lost as *genuine* and records
  the marker mechanically in a ballot field `ack_carries_failure_sentinel`, and the tell-bearing
  segregation is applied at scoring. (Fixes the impasse where such a case had no valid rater label.)

**Admissibility (R5 — the observation-vs-verdict split; in the rater copy modulo the disclosed R6 clean-run strip):**
- **Admissible AS the OBSERVATION to be judged:** the case's own presented material — the request
  sequence, the response(s), and the observed durable state (including the paired clean-run state).
- **The sole source of the NORM (what SHOULD have happened):** docs, OpenAPI/spec, source code.
- **Inadmissible:** distributed traces, any tool/oracle output (incl. MIST), and any runtime behavior
  BEYOND what the case itself presents. (The earlier flat "runtime behavior inadmissible" was wrong —
  it forbade the very datum every case is built around.)

**κ-gate:** if the *calibration-gate* κ < 0.6, at most TWO rubric-iteration rounds, CALIBRATION CASES
ONLY (no S3 peeking); after any iteration, relabel ALL prior cases under the final rubric (the reserve
rater is the fresh relabeler). **Statistics (R7 + 2026-07-09 amendment, R-review F5/F14/F21):**
estimator = **Cohen's unweighted κ** (2 labelers) / **Fleiss'** (3), **nominal, over the full 3-category**
space {genuine, benign, underspecified} (the underspecified→precision exclusion NEVER applies to κ);
report **S3-only κ as PRIMARY** (measurement κ = S3 + M-yield-audit; **withhold κ at n<10**, report raw
agreement + Clopper–Pearson on the agreement proportion); pooled calibration+S3 κ is a SECONDARY
small-n-stability figure carrying the calibration-inflation caveat; κ CI by **bootstrap BCa**. Report
**PABAK/Gwet's AC1** always; **AC1 is the headline when any single label's prevalence > 0.70**, κ
otherwise (neither substituted post-hoc). **Pre-registered reliability ladder on the primary S3-only κ:**
≥0.6 full register; 0.4–0.6 demoted register (all ballots released, conservative-tie-break primary,
adjudicated secondary, AC1 non-substituting); <0.4 no reliability claim (§8 fallback). Calibration size =
**max(30, 50−|S3|)**, benign-skewed **≥2:1**; per-rater confusion-matrix bias audit vs known calibration
labels feeds a sensitivity band on S3 precision. CI units = distinct defect/fault-sites, not flagged
events. Full rater protocol: `c3-rater-materials.md` §5–§6/§10–§11.

## §4 Machine index format + the scoring contract
**Layout:** `cases/<id>.yaml` (or `.json` per the machine schema) · `index.generated.*` (built, not
hand-edited) · `MANIFEST.sha256` · `LICENSE`/README component-map · `raw/` (large artifacts → Zenodo/OSF
by hash).
**Scoring contract (frozen):**
- **S1/S2 (inject-and-twin path):** deploy at the pinned digest; assert `health_preconditions`; run the
  fault leg (`stimulus.script` + `fault.injection`), record the oracle's verdict on the typed
  `readback` (per its modality; `mist_readback_oracle` is `not_applicable` when modality is
  `none-durable`); if `negative_control.present`, run the control leg. Score vs `label.value`: genuine
  fault leg should FIRE (else FN); any control leg + any benign fault leg should NOT fire (else FP).
- **S3 (observed-flag path — A-M1/B-M6):** wild cases have NO `fault.injection` and usually NO twin.
  The oracle's verdict is the ALREADY-EMITTED flag on the captured transcript; score TP/FP/excluded
  vs the S3 label — **conservative-tie-break resolution is PRIMARY** (any inter-rater disagreement
  involving `genuine` resolves to NOT-genuine; 2026-07-09 amendment, R-review F6), the case-blind
  adjudicated resolution is reported SECONDARY (upper bound); no fault/control legs. **S3 reproduction = captured-artifact +
  best-effort replay, non-determinism documented** (never silently re-manufactured as a deterministic
  injection — that would destroy its wild provenance).
- **Underspecified + tell-bearing buckets:** excluded from the primary discriminating denominator.
  **Precision reported both ways (A-M2):** *excluded* precision = genuine-fires / (genuine-fires +
  benign-fires); *included* precision = genuine-fires / (genuine-fires + benign-fires +
  underspecified-fires) — underspecified-fires count against precision when included.
- `NOT_EVALUABLE` (oracle cannot bind the observable) is its own bucket — never a silent pass/miss.
- **`oracle_expectation` evaluability is keyed to `capture_status` [rev-2.1 amendment 2026-07-09 — REVIEW-CORPUS R2/R3 (A-M1/A-M2)]:**
  a **`captured`** case records **as-deployed** verdicts — a trace oracle on a `trace-uninstrumented` deploy has no input and MUST be `not_applicable` (NOT `no_flag`; a `no_flag` would credit a true-negative the oracle never earned and pollute its precision denominator). A **`specified`** case records the **design expectation** (what each oracle would verdict on a properly-instrumented deploy meeting the case's stated preconditions); it is a pre-registered TARGET and is **never counted as a measured result** until captured. Tallies of results include `captured` cases only. Consequence: the freeze's "`trace-invisible-by-construction` ⇒ N-genuine-vs-0-caught recall row" is EARNED only by a *traced* capture where the trace oracles ran and missed (`no_flag`); until then the two fabricated-ack cases are `trace-uninstrumented` and the N-vs-0 row is a pre-registered claim, not a result.
- **SEED/PILOT disclosure [R7/C-F1]:** the populated corpus is a **seed/pilot** — its captured *discriminating* positives are few and the FP/TP precision pair, breadth positives, and S3 stratum are `specified`/unrun (the pre-registered scale plan). Report it as "schema + rubric + pilot seed corpus + scale plan", never as a completed benchmark; never present a `specified` case's `oracle_expectation` as a result; report **6 positives / 5 negatives**, not a bare "S1 count".
- **Aggregation views (frozen):** per-stratum, per-SUT, per-`trace_visibility`, per-`ack_content_visibility`,
  per-`fault.mechanism`, per-`fault_class`, per-provenance. E2 recall reported PER trace-visibility class;
  `trace-invisible-by-construction` (fabricated-ack/normal-span) is its own disclosed N-vs-0 row, kept
  SEPARATE from `trace-uninstrumented` (e.g. a TeaStore-Kieker-exclude branch — pre-registered, B-M5).

## §5 Frozen acceptance floors (rev 2 — honest recount, R3)
- **Diversity floor (R1, re-worded):** ≥4 distinct `fault.mechanism` values from the enum **as
  applicable to the SUT's architecture**; broker-less SUTs (TeaStore) minimum **3** (`broker-policy`
  N/A). The BINDING cross-SUT diversity floor is **≥6 acked-but-lost (data-integrity) cases across the
  write-path SUTs** using **distinct DEFECT SITES** (not mechanism-multiplexed variants of one site —
  C-A4).
- **Scale floors, reported on TWO denominators (C-A4 distinct-site demand):**
  - *distinct defect sites:* honest recount = TeaStore 4 + OTel-Demo 4 + Boutique 1 + **TT ~4–6
    reviewed sites** (cancel→refund, adminroute-add, adminbasic/contacts-add, + F-corpus in-class
    subset) + **SS ~2–3 sites** (shipping-enqueue, carts) ≈ **15–18 distinct sites**; with the F-corpus
    at target 10 (in-class-verified — B-m6 precondition) ≈ **21–28 sites**.
  - *mechanism-variant case-runs:* ≈ **37–45** (each site × its applicable mechanisms/strata).
  - **Headline floor (rev 2):** report BOTH; the ≥45 target is a **case-run** count, honestly labeled
    as such; the distinct-site count (~21–28) is reported alongside and is the anti-padding denominator.
    If distinct sites < 20, THAT is a disclosed finding (not padded away). S2 ≥ 35 similarly recounted:
    survey gives 16 across the 4 new SUTs + 2 packaged corpora (≤2 cases each) + TT/SS designed-degradation
    paths — TT/SS S2 counts to be enumerated at step 2, disclosed if short.
  - **Tell-free floor (R8):** ≥N S1 positives that are jointly *natural* (not fork-flag),
    *success-shaped-acked* (`ack_content_visibility==success-shaped-clean`), and
    *trace-invisible-by-construction* — the cases that actually justify a read-back oracle. Current
    tell-free-natural exhibits: TeaStore order-confirm (A-verified clean 200) + SS swallowed-enqueue
    (trace-only, no durable read-back). If N is small, that is the honest finding and STRENGTHENS the
    "prevailing methodology filters this out" positioning.
- **S1-by-injection genuineness disclosure (A-M8):** S1 "genuine-by-construction" means
  *injection-induced divergence from the negative control*, a bar distinct from the rubric's
  contract-grounding bar. For S1 cases whose durable write is plausibly best-effort (OTel checkout→Kafka
  accounting especially), attach contract-grounding evidence (docs/spec that order→accounting persistence
  is expected) OR disclose the case rests on the construction bar.
- **Reproduction / review:** every S1/S2 case reproduces via its replay script on a clean cluster (S3
  per the captured-artifact rule); label provenance complete; zero labels from MIST's predicate; the
  ≥3-cold-review takes the sampled-reproduction form (k=5 re-runs + m=15 schema/label audits).
- **F-corpus:** floor ≥6, target ≥10, **each replication verified masked-2xx / in-class before it
  counts as S1** (B-m6).

## §6 Amendments log
FROZEN ON COMMIT. Every post-freeze change = a dated row.

| date | change | reason | invalidates |
|---|---|---|---|
| 2026-07-08 | **rev 2 re-freeze** (this revision) | step-1 3-cold-review R1–R8 (`REVIEW-STEP1-FREEZE-*`) | supersedes rev 1 schema; §2/§3/§4/§5 changed |
| 2026-07-08 | R1: `fault.mechanism` enum +`dependency-down`; `input-driven`→stimulus; ≥4-floor re-worded "as applicable, broker-less min 3"; restored the plan's "as applicable" | A-B1/B-B3 — enum couldn't express the survey's TeaStore mechanisms | matrix mechanism cells; survey mechanism wording |
| 2026-07-08 | R2: SUPERSEDE `debug/a-main/benchmark/` prototype; adopt its `oracle_expectation`/anti-circularity/`fault_class`/`capture_status`; update its JSON schema to this model; seed-case migration map | B-B1 — two incompatible committed schemas | benchmark/ schema v0.1.0 + 6 seed cases (migrated) |
| 2026-07-08 | R4: prose `observable_pin`→typed `readback{modality,locator,expect_*,mist_bindable}` | B-B4 — flagship SS/OTel cases have no durable API read-back | old observable_pin prose |
| 2026-07-08 | R8: added `ack_content_visibility` + sentinel rule + tell-bearing segregation + tell-free floor | C-A2 — no ack-content axis; trivial body oracle credited | — (new axis) |
| 2026-07-08 | R3: honest S1/S2 recount on two denominators (distinct-site + case-run); disclosed shortfall branch | A-M7/B-B2 — ≥45 didn't close; survey refuted the "3×7" lineage | old §5 "≥80+wild" single-denominator floor |
| 2026-07-08 | folded: S3 scoring branch (A-M1/B-M6); included-precision def (A-M2); split `trace_visibility` (B-M5); `write_shape` (B-M7); `authoring_cost` (B-M4); S3-only-κ primary (R7); rubric observation-vs-verdict (R5); m1/m2 invariant fixes | step-1 review MAJ/MIN | rev 1 §2/§3/§4 |
| 2026-07-09 | **Corpus-wave amendment (REVIEW-CORPUS-B B1/M1):** `artifacts.raw_logs` entries point at the RATER-ARTIFACT SIDECAR format (ordered request records method/path/payload · response records status+full body · durable-state observations · RELATIVE times · producer + mist_commit stamp) — the single format every producer (seed capture runs, M-yield, step-5 wild-flag capture bundles) emits; B4 consumes only case+sidecar. No frozen-key change (raw_logs stays `[<paths>]`) — this row documents the adopted format for hygiene | corpus plan rev 2 (`c3-case-corpus-plan.md` §5) | — |
| 2026-07-09 | **rev-2.1 — R1 (A-M4/C-F5):** relaxed the `S1` invariant to match the machine schema (admits `natural` positives + paired clean controls); `negative_control` required on S1 **positives** only; REPORTING RULE = "6 pos / 5 neg", never a bare S1 count | 11-case corpus review (`REVIEW-CORPUS-RECONCILIATION.md`) | §2 S1 invariant (line ~137), §2 `negative_control` note |
| 2026-07-09 | **rev-2.1 — R2/R3 (A-M1/A-M2):** `oracle_expectation` evaluability keyed to `capture_status` — `captured`⇒as-deployed (uninstrumented trace oracle = `not_applicable`), `specified`⇒design-target (never tallied as a result); the `trace-invisible-by-construction` N-vs-0 recall row is earned only by a traced capture | 11-case corpus review | §4 scoring contract (new bullet) |
| 2026-07-09 | **rev-2.1 — R7/C-F1:** SEED/PILOT disclosure — populated corpus is a pilot + pre-registered scale plan; never present a `specified` case's expectation as a result | 11-case corpus review | §4 scoring contract (new bullet) |
| 2026-07-10 | **Breadth-wave capture (scale-plan item executed):** adminroute + adminbasic lost-writes LIVE-CAPTURED on fork-built images (digests e5af2936/1c9913ea, clean a1767ab3 tree) + NEW same-binary adminbasic clean control (env flag = only variable) → corpus 11→12 cases, counts 6 pos / 6 neg, captured discriminating positives 2→4 (2 fabricated-ack + 2 skipped-cross-service-persist; the latter pre-registered as comparator-catchable under instrumentation). Deployments restored to base 1.0.0. New runbook rule: probe-first after any rollout (nacos/ribbon routes to terminating pods) | breadth wave (checklist §1.95.2b remainder) | R1 reporting-rule count (6/5→6/6) |
| 2026-07-08 | **UX-wave amendment (U7, REVIEW-UX-RECONCILIATION):** +`oracle_eval.oracle_mode` (observe\|paired); +`config_provenance.mist_authoring {tier, minutes}` (minutes-per-bound-endpoint = the common symmetry unit with comparator `authoring_cost`); mist_commit pin timing = END of the 1.9 UX wave, criteria include W0–W6, QuiescenceGate→verdict mapping frozen at the pin; promoted G1/G3 seeds re-recorded at the pin; **defect-tier prerequisite disclosed: OBSERVED_COMPLETE_ABSENT requires a trace backend (`jaeger.base.url`) — claim wording "once bound and trace-instrumented"** | UX 3-cold-review (U3/U7/C-A4) | JSON schema updated in lockstep |
| 2026-07-09 | **Rater-rubric-delta + reliability amendment (3-cold rater review, `REVIEW-RATER-RECONCILIATION.md`).** (a) The `c3-rater-materials.md §3` rater copy is NOT byte-verbatim: it drops the analyst "success-shaped body" precondition (moved to the ballot field `ack_carries_failure_sentinel`), so a 2xx `{1,"error"}` acked-but-lost is rater-labelable *genuine* (F4); the "verbatim/identical" claims (§3 header, admissibility) are corrected to "identical modulo the disclosed R6 clean-run strip + this delta" (F7). (b) Re-landed three specs the rev-2 re-freeze silently dropped from `benchmark/schema/rubric.md`: the async-vs-lost-write tie-break (`:45-50` → §3), the **Cohen's-κ** estimator pin (`:55` → §3 statistics), and per-ballot **rater_id + rubric_version** recording (`:56` → rater ballot §4). (c) Pre-registered: 3 enumerated κ's + reliability decision ladder on the primary S3-only κ; PABAK/AC1 headline at prevalence>0.70; **conservative-tie-break primary** for S3 scoring (adjudicated secondary; §4); adaptive calibration size max(30,50−\|S3\|), benign-skewed ≥2:1 + known-label bias audit; underspecified>30%-of-S3 promotion bound. (d) Claim string "blind-adjudicated"→"blind-labeled wild stratum" (§1). Full protocol lives in `c3-rater-materials.md` (rev 3). | 3-cold rater review gated human-subject contact | rev-2 freeze §3 statistics + "verbatim" claims; rater packet rev 2 |
