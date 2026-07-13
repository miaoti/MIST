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
  **[rev-2.1 amendment 2026-07-09 — REVIEW-CORPUS R1 (A-M4/C-F5): reconciles the freeze with the machine schema, whose `stratum` description already admits "natural"; the old clause "S1⇒genuine+by-injection" was refuted by the corpus (a natural S1 positive + 3 clean controls). REPORTING RULE: never report a bare "S1 count" as a positive count — the current corpus is 6 positives / 7 negatives (4 S1 clean controls + 3 S2 benign traps; updated by the 2026-07-10 breadth-wave + noop-modify amendments).]**
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
| 2026-07-10 | **TRACED-CAPTURE WAVE EXECUTED (plan rev-2, 3-cold-reviewed; REVIEW-TRACED-WAVE-RECONCILIATION.md):** 8 legs re-captured as capture-of-record on an OTel-instrumented deploy (javaagent 1.33.6 pinned+sha; DEFAULT instrumentation, no suppression) with per-leg trace exports + the pre-committed frozen scorer. **N-vs-0 recall row status: the COMPARATOR trace columns are now MEASURED ran-and-missed on the fabricated-ack pair (naive N=2, presence N=1 per T7 — createaccount presence pinned n/a) = EARNED for those columns; `mist_trace_shape` = Branch-B (traced-but-NOT-RUN this wave — demo corpus lacks the cancel path and observe-mode quarantine semantics make fault-leg runs ill-defined without tool changes; deferred to step-2.5/E2), so the row is scoped to the comparator columns until MIST's trace oracle runs — DISCLOSED, this amendment is that disclosure.** Breadth pair MEASURED symmetrically: presence-assertion CATCHES both skipped-call lost-writes (flag) with traced controls validating the selectors (B-B4 baselines). **T6 DB-granularity disclosure (measured): fault legs carry ZERO DB-client spans vs controls' 2/6/3 — the lost write IS visible at DB-span granularity (a statement-level assertion would catch it); the benchmark's presence column is pinned at cross-service HTTP-span granularity (service-map authoring), so trace-invisible-by-construction holds at the pinned granularity.** Runbook hardened: N≥4 CONSECUTIVE probes (ribbon round-robin serves stale pods past a single success — one adminbasic traced attempt was mis-served + abandoned); fresh-ids rule explicit for hardcoded-id specs (a reused id exercised the dedupe path — abandoned + re-run). Cluster restored to pre-wave state | traced-capture wave | R2/R3 N-vs-0 row wording (scoped to comparator columns pending MIST-run); the 8 cases' trace cells |
| 2026-07-10 | **TENANCY-WINDOW PHASE B EXECUTED (plan rev-2, UNANIMOUS 3-reviewer accept; recon REVIEW-TENANCY-RECONCILIATION.md): the FP/TP pair's COMPARATOR columns MEASURED** — bookinfo benign leg: naive=FLAG (Envoy client ≥500) AND presence=FLAG (ratings server span absent) = both structural columns false-positive on the designed degradation (control leg validates both families); sockshop genuine leg: presence=FLAG TP (queue-master CONSUMER span absent; 30-span control contains it) + **T2 DIVERGENCE disclosed: pre-registered naive=flag REFUTED — connection fails before basicPublish, zero error spans, measured no_flag = FN**; + sockshop CONTROL measured naive=FLAG (queue-master docker-socket POST error on every consume — env-conditioned SUT behavior) → naive fails both directions AND on clean operation, all measured. **`mist_trace_shape` = Branch-B traced-but-not-run on all pair legs; displaced R4 design targets preserved verbatim in case notes; THE PAIR-SEPARATION (MIST-side precision) CLAIM STAYS PRE-REGISTERED until the 2.5/E2 run — this row is that scoping disclosure.** Corpus 13→14 (new sockshop-shipping-control-001; counts 6 pos / 8 neg). SS runbook: order requires address+card first; payment declines >100 (use a cheap item); queue-master spawns docker workers (error span on every consume under k8s) | tenancy window Phase B | the 2 pair cases' cells + counts |
| 2026-07-10 | **Idempotent-no-op benign control CAPTURED (closes REVIEW-CORPUS R6's missing read-back stress case, on TT itself — no borrowed SUT):** `TT-contacts-noop-modify-benign-001` — PUT an existing contact with IDENTICAL values → 200 `{1,"Modify success"}` success-shaped-CLEAN (no tell, unlike dedupe's status:0) + durable state legitimately unchanged → the FP trap for any "acked write must produce a durable delta" rule; MIST's membership/agreement read-back target = no_flag. Captured on the untouched base deploy (no fork/rollout). Corpus 12→13, counts 6 pos / 7 neg (3 S2 traps) | noop-modify capture | R1 reporting-rule count (6/6→6/7) |
| 2026-07-08 | **UX-wave amendment (U7, REVIEW-UX-RECONCILIATION):** +`oracle_eval.oracle_mode` (observe\|paired); +`config_provenance.mist_authoring {tier, minutes}` (minutes-per-bound-endpoint = the common symmetry unit with comparator `authoring_cost`); mist_commit pin timing = END of the 1.9 UX wave, criteria include W0–W6, QuiescenceGate→verdict mapping frozen at the pin; promoted G1/G3 seeds re-recorded at the pin; **defect-tier prerequisite disclosed: OBSERVED_COMPLETE_ABSENT requires a trace backend (`jaeger.base.url`) — claim wording "once bound and trace-instrumented"** | UX 3-cold-review (U3/U7/C-A4) | JSON schema updated in lockstep |
| 2026-07-09 | **Rater-rubric-delta + reliability amendment (3-cold rater review, `REVIEW-RATER-RECONCILIATION.md`).** (a) The `c3-rater-materials.md §3` rater copy is NOT byte-verbatim: it drops the analyst "success-shaped body" precondition (moved to the ballot field `ack_carries_failure_sentinel`), so a 2xx `{1,"error"}` acked-but-lost is rater-labelable *genuine* (F4); the "verbatim/identical" claims (§3 header, admissibility) are corrected to "identical modulo the disclosed R6 clean-run strip + this delta" (F7). (b) Re-landed three specs the rev-2 re-freeze silently dropped from `benchmark/schema/rubric.md`: the async-vs-lost-write tie-break (`:45-50` → §3), the **Cohen's-κ** estimator pin (`:55` → §3 statistics), and per-ballot **rater_id + rubric_version** recording (`:56` → rater ballot §4). (c) Pre-registered: 3 enumerated κ's + reliability decision ladder on the primary S3-only κ; PABAK/AC1 headline at prevalence>0.70; **conservative-tie-break primary** for S3 scoring (adjudicated secondary; §4); adaptive calibration size max(30,50−\|S3\|), benign-skewed ≥2:1 + known-label bias audit; underspecified>30%-of-S3 promotion bound. (d) Claim string "blind-adjudicated"→"blind-labeled wild stratum" (§1). Full protocol lives in `c3-rater-materials.md` (rev 3). | 3-cold rater review gated human-subject contact | rev-2 freeze §3 statistics + "verbatim" claims; rater packet rev 2 |
| 2026-07-10 | **TENANCY-WINDOW PHASE C EXECUTED (TeaStore, plan rev-2 unanimous-accept):** natural maintenance-flag masked-write pair CAPTURED (`teastore-order-maintenance-masked-001` + control) — the SUT's own runtime flag makes the persistence CREATE fabricate **201/body `-1`** (measured: the same direct-POST body 500s healthy) while the webui/auth chain renders the order-confirmed page; marker ABSENT in-window + post-restore, DB intact throughout; N≥4 probes all masked; **all three ack columns RAN-AND-MISSED; trace cells `not_applicable` (Kieker branch, captured⇒as-deployed)**; read-back = `api-get` **HTML** profile ⇒ `mist_bindable=false` (see the T9 convention row). Survey corrections (dated in `c2-depth-survey.md`): toggle path as-written 404s (real = POST `/rest/generatedb/maintenance` JSON body; bare GET `/rest/generatedb` REGENERATES the DB — capture-tooling hazard); **DB-down producer UNSOUND-for-capture** (no PVC — the wipe destroys the absence evidence); input-driven bogus-uid 201/`-1` UNADJUDICATED (probe-body confound); **recommender cold-start REFUTED as user-visible** (isReady gating + registry-LB bridge the ~3 s window) → no TeaStore S2 case. **T15/C-M4 datum: plain-VS host-match INTERCEPTS on TeaStore** (kind manifest sets HOST_NAME=service DNS; registry holds `teastore-persistence:8080`) — the pre-registered miss expectation REFUTED as measured; **mesh-503 rider leg VERIFIED-MASKED live** (503 at the auth→persistence client swallowed into the confirmed page + marker lost, exactly the §1-survey `-1L` chain) — rider datum only, a 3a mesh-sever S1 candidate. Corpus 14→16 (7 pos / 9 neg) | tenancy window Phase C | counts (6/8→7/9); TeaStore survey rows |
| 2026-07-10 | **TENANCY-WINDOW PHASE D EXECUTED (OTel-Demo, the ASYNC FLAGSHIP):** `oteldemo-checkout-lost-001` + control CAPTURED on the pinned chart 0.40.9 / app 2.2.0 (natively traced; scorer Phase-D selectors pre-committed 8bb0424 with `presence_scope=file` — Kafka consumers continue in LINKED traces, so the per-leg export merges the service=checkout and service=accounting windowed queries; exactly-one rule unchanged). MEASURED: broker-down PlaceOrder acks **200 at ~0.02 s** (produce fully async — the §2.3 latency rider VERIFIED) and the accounting-Postgres row is **PERMANENTLY absent** (in-window, post-restore, and after a VERIFIED-heal canary landed; probe orders 4/4 + 4/4 absent across both attempts); `naive_span_error` **no_flag ran-and-missed — zero error spans AND the checkout `publish orders` producer span is PRESENT+CLEAN on BOTH legs** (async local-enqueue instrumentation: the trace actively looks successful at the producer — sharper than sockshop's vanishing span); `tracetest_presence` **FLAG ran-and-caught** (accounting `receive orders` CONSUMER span absent from the fault file; control validates the family, T2; T6 db report: the accounting postgres INSERT client span present-on-control/absent-on-fault). **Recovery-window datum (measured): a REPLACED kafka pod (emptyDir, new cluster id) wedges BOTH rdkafka clients** — the old producer keeps silently losing 200-acked orders PAST the restore (4 heal canaries lost) until a checkout restart, and the old consumer stops consuming while later messages buffer DURABLY and drain after an accounting restart (**pending-vs-missing observed live**); recovery = rollout-restart checkout+accounting+fraud. Attempt-1 capture kept as `*-attempt1` (its export window caught the probe traces → 5 entry traces broke the frozen exactly-one rule → full re-capture with a quiet gap; NO rule change). **D3b graceful-ad rider REFUTED as an S2 case** (ads are browser-XHR; `/api/data` answers an honest 500 under ad-down while the SSR page stays 200 — no success-shaped server-side trap; ~30 s gRPC reconnect-backoff datum). **D3c flagd list re-frozen vs the deployed 2.2.0 config** (15 flags; paymentFailure percentage-graded; intlShippingSlowdown gone; llm flags quota-ineligible as-deployed → 13 3a-eligible). D4: OTel-Demo stays UP (plan §1 default). Corpus 16→18 (**8 pos / 10 neg**) | tenancy window Phase D | counts (7/9→8/10); OTel survey rows; 3a vendor-flag quota list |
| 2026-07-11 | **WAVE-3A ITEMS 1+2+2b EXECUTED (plan rev 2.1, unanimous 3-cold-review + confirmation pass; recon `REVIEW-3A-RECONCILIATION.md`):** **Item 1 (`cartFailure` bindable-read-back positive) = REFUTED, NOT AUTHORED** — the pre-registered C-M5 branch fired: deployed 2.2.0 `cartFailure` makes the cart store throw FailedPrecondition (simulated redis-down) and PlaceOrder fails LOUDLY (504 @ ~15 s, no order, cart intact; measured N=5; toggle mechanism of record = the flagd-ui API after the 1-P0 probe found CM patches never reach the runtime emptyDir copy); survey corrected — 3rd SUT with honest-loud cart-store failure (SS carts β, Boutique); captures retained as refutation evidence. **Item 2 (TeaStore mesh-sever pair) = CAPTURED — the corpus's FIRST mesh-sever case**: plain-VS abort 503 on the persistence orders prefix with TEMPORARY sidecars on webui+auth (deploy-shape parity: both legs sidecars-on; healthy probes 4/4 landed pre-VS, fault probes 4/4 confirmed-and-0/4-landed; record marker ABSENT post-teardown, control PRESENT; A-9 zero orderitem orphans; A-7 measured the temp sidecars export NOTHING — the pre-registered trace-exclusion doubly grounded; enumerated teardown verified, no persistence/db restarts). **Item 2b = `teastore-order-depdown-specified-001` (specified)**: the C-M4 min-3 fix — TeaStore's mechanism floor now points at 3 corpus rows: flag CAPTURED + mesh-sever CAPTURED + dependency-down specified-with-disclosed-capturability (PVC precondition in-file). Corpus 18→21 files: **9 pos / 11 neg captured + 1 specified** (R1 reporting rule) | wave-3a items 1-2b | counts (8/10→9/11+1spec); OTel survey cartFailure row; checklist 2.2 floor wording |
| 2026-07-11 | **`bindable-pending-eval` CONVENTION ROW (pre-registered by wave-3a rev 2.1 §1, unanimous):** when a case's typed read-back is MACHINE-BINDABLE (`mist_bindable=true`) but MIST has not run on the SUT (enablement pending, a 2.75 decision), the `mist_readback_oracle` cell records `not_applicable` with the notes reason string `bindable-pending-eval`; such cells are **NEVER pooled with T9 boundary rows and ENTER the MIST recall denominator at the wave that runs them** (the case is enrolled in the 2.5/E2 run list in its own notes); the FLAG/no_flag design target is preserved per T1. The audit property is "verdict-valued mist cells appear ONLY where MIST ran" — **GRANDFATHER CLAUSE (A-2c): the two pre-convention benign cells `TT-contacts-noop-modify-benign-001` and `TT-contacts-dedupe-benign-001` carry analytically-derived `no_flag` targets not backed by a recorded MIST run; they are disclosed legacy target-valued cells, so the property holds by construction going forward.** NOTE: the convention's instantiating case (wave-3a item 1) was refuted-not-authored — the convention stands PRE-REGISTERED for the next bindable-pending-eval case | wave-3a rev 2.1 §1 pin (A-2/B-F4/C-B1 reconciliation) | none (no existing cell changes; the two legacy cells gain a disclosure, not a value change) |
| 2026-07-10 | **T9 CONVENTION ROW (pinned at Phase-D close-out, as the plan required): `mist_readback_oracle = not_applicable` WITH AN APPLICABILITY-BOUNDARY NOTE whenever the typed read-back EXISTS and is decisive but MIST's oracle cannot bind the MODALITY at the pinned commit** — instantiated twice this window: OTel-Demo `sql-probe` (the psql probe is the ground truth; no SQL binding at the pin) and TeaStore `api-get` over an **HTML** surface (the profile page is durable and user-facing; no HTML field binding at the pin). DISTINCT from `none-durable` (where no durable read-back exists at all — bookinfo/sockshop): the boundary cases document what an oracle WOULD bind given an enablement decision (2.75), and their pre-registered design targets (mist_readback=FLAG on the positives) are PRESERVED in case notes per the T1 convention, never tallied as results. Reporting rule: boundary cells are excluded from MIST recall denominators and reported as their own applicability row | plan rev-2 D3/T9 + Phase C/D captures | mist_readback cells of the 4 new cases (semantics clarified, values unchanged) |
| 2026-07-10 | **WAVE-3A ITEM 3 (`kafkaQueueProblems` probe-gated S2) = STOP / C-m8 — NO case authored (neither S2 nor S1).** The plan pre-registered three outcomes (AUTHOR / NOT-AUTHORED / STOP); the probe fired STOP and the confirmatory + separation runs upgraded it: on chart 0.40.9 / app 2.2.0 the flag is NOT the survey's "delayed-not-lost" trap but a STOCHASTIC MIX dominated by PERMANENT PRODUCTION LOSS — measured **7 of 8 in-window acked orders lost, 1 fast success under-flag, 0 pending** (a later canary drained PAST the four probe orders ⇒ dropped at production; a post-flag canary AND a flag-off canary also lost ⇒ the wedge persists past toggle-off; an accounting+checkout+fraud rollout-restart restored LIVE traffic but recovered ZERO lost orders; kafka pod 0 restarts throughout ⇒ not the Phase-D broker-replacement wedge). Disposition (plan §3 verbatim): dated survey correction (`c2-depth-survey.md` §2 OTel item-3 block + S2 path-(1) inline pointer) + an **S1-positive candidate (vendor-flag provenance) deferred to its own discipline**, never silently subsumed. Corpus UNCHANGED (21 files; no case added). Evidence: `b4/runners/3a/item3-*` (RESULT.md + probe/confirm/recovery logs). `trace_score.py` `oteldemo-kafkaqueue-pending-benign` row STOP-annotated (inert) | wave-3a item 3 probe round (rev 2.1 §3 decision rule) | none (no case authored; survey + scorer annotations only) |
| 2026-07-10 | **WAVE 2.75-A EXECUTED — TeaStore MIST read-back ENABLED + RUN (plan `wave-275a-plan.md` rev 2.1, unanimous 3-cold-accept + confirmation pass):** the pre-registered 2.75 enablement decision RESOLVES the T9 `api-get`-over-**HTML** boundary on `teastore-order-maintenance-masked-001` + control — the modality enum stays `api-get` (the correct value for an HTTP JSON API); the **locator** moves from the HTML profile page to the durable **JSON** order-list surface (persistence `/rest/orders`), treated as in-scope by the enablement decision (**A-F13**: locator HTML-profile→JSON `/rest/orders`, modality unchanged), so MIST's MEMBERSHIP read-back binds. MEASURED (live paired run via `TeaStoreOrderHeadToHead` + `JsonDurableReadback` over the full `/rest/orders` collection; globally-unique per-probe `address1` marker `mist-<leg>-p<i>-<uuid>` supplied to `beforeWriteSupplied`; fault leg toggles persistence maintenance ON around the confirm and OFF before the read-back, B-R3): **MIST paired FIRE 5/5 probe-pairs** (control-present / maintenance-masked-absent), correlator-joined, `correlatorUnique=true`, 0 unjoined; fault acks HTTP 200 success-shaped-clean, absent from own read-back (gate `TIMEOUT_ABSENT` — sync SUT, no async-completion signal; 15 s/31-poll absence is conclusive, disclosed). **INDEPENDENT GROUND TRUTH** (direct `/rest/orders` read, NOT MIST's oracle): 5/5 control markers persisted, 0/5 fault markers persisted; +5 total orders. Cell changes (atomic with this row): `mist_bindable` false→**true**; `mist_readback_oracle` not_applicable→**flag** (positive) / **no_flag** (clean control) — the pair now **ENTERS the MIST recall denominator** (leaves the T9 boundary row). Audit property preserved (verdict-valued mist cell appears ONLY where MIST ran). SOLE-ORACLE datum (trace-uninstrumented SUT — read-back is the only oracle here), **NOT a discrimination win over a trace comparator** (C-B1 honest reframe). Transports committed 655fa0b; evidence `b4/enable/teastore-order-run.report.json` + `b4/enable/RESULT-teastore-2.75a.md` | wave 2.75-A (accepted plan, A-F9 flip-atomic-with-measured-run) | the 2 TeaStore cases' `mist_bindable`+`mist_readback_oracle` cells; the T9 row (TeaStore `api-get` line now resolved); MIST recall denominator (+1 measured positive, +1 clean control) |
| 2026-07-10 | **WAVE 2.75-A EXECUTED — OTel-Demo MIST read-back ENABLED + RUN (the ASYNC FLAGSHIP; plan `wave-275a-plan.md` rev 2.1):** the pre-registered 2.75 enablement decision RESOLVES the T9 `sql-probe` modality boundary on `oteldemo-checkout-lost-001` + control — MIST's MEMBERSHIP read-back binds `accounting.shipping` via `SqlDurableReadback` (marker-filtered `kubectl exec … psql`, server-side WHERE so the growing table never inflates the read, B-R1). **KEY (C-R2):** the correlation key is the REQUEST-DERIVED `street_address` marker, NOT the server-assigned `order_id` (which the oracle forbids as a key); accounting persists Order+Shipping+OrderItem in ONE `SaveChanges()`, so a street_address-keyed absence is equivalence-preserving vs an order_id-keyed one. MEASURED (live paired run via `OtelCheckoutHeadToHead`; a SINGLE kafka scale-0 toggle between the control and fault legs per A-F8/B-F7 control-first; 25 s async floor ≥ the measured ~5 s landing): **MIST paired FIRE 5/5 probe-pairs** (control-present / broker-down-absent), correlator-joined, `correlatorUnique=true`, 0 unjoined; fault acks HTTP 200 success-shaped at ~0.02 s (fully-async produce), absent from own read-back (gate `TIMEOUT_ABSENT`, 34 polls — permanent async loss, no completion signal to observe). **INDEPENDENT GROUND TRUTH** (direct psql, NOT MIST's oracle): 5/5 control markers persisted, 0/5 fault markers persisted **AND STILL 0 after a verified-heal canary drained** (the loss is PERMANENT, not pending — the fault produce never entered the topic). Cell changes (atomic with this row): `mist_bindable` false→**true**; `mist_readback_oracle` not_applicable→**flag** (positive) / **no_flag** (clean control); read-back locator `accounting."order"`/order_id → `accounting.shipping`/street_address (C-R2, modality enum stays `sql-probe`). **PRESENCE-CONCORDANT, NOT a discrimination win** (C-B1): `tracetest_presence_oracle=flag` already catches the accounting CONSUMER-span absence — MIST's read-back INDEPENDENTLY CONFIRMS the same loss at the durable store (concordance, not "beats trace"). Recovery: rollout-restart checkout+accounting+fraud-detection (the rdkafka-wedge runbook) executed + verified healthy (both heal canaries landed, one pending-then-drained). Transports committed 655fa0b; evidence `b4/enable/oteldemo-checkout-run.report.json` + `b4/enable/RESULT-oteldemo-2.75a.md` | wave 2.75-A (accepted plan, A-F9) | the 2 OTel cases' `mist_bindable`+`mist_readback_oracle` cells; the T9 row (OTel `sql-probe` line now resolved); MIST recall denominator (+1 measured positive, +1 clean control) |
| 2026-07-11 | **E2 EXECUTED — flagship discrimination cell UPGRADED pre-registered→HARNESS-RUN-BACKED (plan `e2-discrimination-plan.md` rev 2.1, re-review UNANIMOUS; recon `REVIEW-E2-PLAN-RECONCILIATION.md`):** `TT-cancel-refund-fabricatedack-001.mist_readback_oracle` stays `flag` but is now a REAL MIST-oracle run on a RE-INSTRUMENTED traced deploy (javaagent 1.33.6 on ts-cancel/inside-payment/order), not the prior manual-curl pre-registration. MEASURED N=5 (`g3.CancelRefundHeadToHead` constructed stratum + **C1** client-traceparent capture; read-back TIMEOUT-gated): MIST value-delta read-back **FIRE 5/5**, response-contract comparator MISSED 5/5, ground truth control 50→130 / fault 50→50. **THREE-CONFIG TRACE COMPARATOR** (frozen `trace_score.py` + pre-registered **C2** DB-span selector), fault leg N=5: naive **MISS** + service-map-granularity presence **MISS** + **DB-span-granularity presence CATCH** (`INSERT ts.inside_money` absent-on-fault / present-on-control); all no_flag/present on control. So on ONE coherent traced run: read-back FIRES, naive+service-map trace MISS, DB-span trace CATCHES. **CLAIM = specification-locality (A-MAJOR: granularity/reusability + implementation-decoupling, NOT "out-of-the-box", NOT "beats trace", NOT prevalence).** Anti-circularity: control-leg validator + **P4 orthogonal direct `inside_money` DB read** (mysql, distinct from `/account`): fault = A 50 only (no drawback) / control = A 50 + D 80.00. **STANDING CONSTRAINT: this is a SYNTHETIC fork worst-case + provenance-closure, NOT the paper headline; the corpus gives read-back BREADTH not natural discrimination; the natural-discriminator headline stays the deferred/rater-gated S3 wild-hunt.** Code C1+C2 committed ab73ba6. Evidence `b4/e2/`. Environment disclosure `e2-ram-teardown-note.md` (OTel/TeaStore scaled 0 for RAM; 2.75-A tenants-UP end-state SUPERSEDED, measurements unaffected). Post-run 3-cold review of the RESULT is the §7 backstop | E2 run (accepted plan rev 2.1) | `TT-cancel-refund-fabricatedack-001` mist_readback provenance (pre-registered→run-backed); + the DB-span-granularity comparator datum (documented, not a schema column) |
| 2026-07-13 | **STEP-5-AS-AMENDED (S3 wild-hunt pre-registration row — COMMITTED BEFORE any calibration or counted window; plan `s3-wildhunt-plan.md` rev 2.1, 3-cold-reviewed rev-1→rev-2 + confirmation pass UNANIMOUS; recon `REVIEW-S3-PLAN-RECONCILIATION.md`).** Amendments to the frozen Step-5, all pinned NOW: **(1) detector-(ii)-only** (D1: the trace-shape masking oracle is unbuilt/Branch-B; the estimand is re-qualified accordingly; compensating per-flag trace exports on OTel feed the frozen COMPARATOR, not a MIST oracle). **(2)** the **500-write-stop branch** of "12 h/SUT or 500-write" (single box), restated per-endpoint: ≥500 acked bound-triple writes/SUT, ≥100/endpoint, both denominators per endpoint. **(3) Flag levels:** RAW = acked ∧ `error==null` ∧ absence-at-cap with **gate ∈ {TIMEOUT_ABSENT, OBSERVED_COMPLETE_ABSENT}** (per-gate-stratum reporting; CONFIRMED reachable from TIMEOUT_ABSENT) ∧ W3-gate-open-at-window-end; **CONFIRMED = RAW ∧ still-absent at a T+5 min re-probe** through the same transport + the SAME runtime predicate (public accessor; no fork), with the runtime evidence rules verbatim (non-2xx/VANISHED/bound-hit re-probe = ERROR record, never CONFIRMED); re-probes scheduled **W3-independently** for every acked-absent write; **the <20 scarcity threshold binds on CONFIRMED**; RAW-only = the "present-at-re-probe (delayed-beyond-cap)" descriptive bucket. **(4) Known-site rule, provenance-scoped:** wild flags matching NATURAL-provenance authored sites are excluded from the rated sample (rediscoveries; reported by class, precision computed BOTH ways — new-sites-only rater-labeled AND all-CONFIRMED with rediscoveries scored by known labels); **injected-provenance sites (OTel checkout, TeaStore maintenance/mesh-sever) STAY S3-eligible** behind a pre-window environment guard (flagd defaults via the flagd-ui API, kafka healthy, maintenance=false, no mesh artifacts) + a sealed-side root-cause-distinction note (never in sidecar records). **(5) Sampling:** min(all,40); strata = SUT × distinct defect-site, proportional, **deterministic seed = 20260713**; NO tool-derived signal (trace exports/comparator outcomes) in sampling; CI units = distinct defect-sites. **(6) Sockshop EXCLUDED** (draining-queue read-back ⇒ single-leg FP storm). **(7) Estimand string (frozen):** "a LOWER BOUND on the prevalence of rater-confirmed-genuine acked-lost writes, conditioned on detector-(ii) + the W3 presence-quarantine + the CONFIRMED re-probe qualifier, over the BOUND write paths, under the pinned fault-free workload"; numerator = rater-confirmed genuine distinct defect-sites among CONFIRMED flags; denominators = (a) acked bound-triple writes, (b) distinct bound endpoints; claim sentences pre-committed (zero-finds = rule-of-three 3/N on the CONFIRMED-flag rate under these conditions, no cross-population claim; any-find = existential + site count; κ withheld at |S3|<10). **(8) CONFIRMED-level FP bar (new, alongside the frozen fpProbe bar):** CONFIRMED-flag rate ≤5% (≤1/20) over ≥20 acked benign writes with the identical re-probe, observe-mode; double-PASS opens each window; the frozen sync bar is disclosed structurally weak on trace-uninstrumented SUTs. **(9) Calibration top-up, UNCONDITIONAL:** ~25–30 degradation-shaped S2 benigns captured DURING the windows (never "nothing happened" journeys); ONE observation cadence across ALL rater-facing strata; legacy S1-positive cadence re-captures ride the post-window measured-recall legs; floor-30 disclosed-shortfall fallback (worst case at |S3|=0 needs ~42–43 benign — near-certain shortfall there, reported with its power consequence). **(10) Breaker exclusions are LIST-driven:** on-list environment artifacts only (rdkafka wedge post kafka-pod-replacement · PF death · host/WSL OOM-or-wedge of our own hosting · DB snapshot/quorum artifacts · operator revival/scale ops) — excluded-but-reported; **off-list (incl. SUT-endogenous degradation) flags STAND and stay S3-eligible**, surfaced to the P6 review. **(11) Markers/keys:** neutral `corpus-w<seq>-<12hex>` grammar for every hunted triple (`isolation_strategy: supplied` only — `freshValueLike`'s `mist-…` values are banned-string hazards); per-SUT pre-window gate = one calibration bundle round-trips `b4_harness.py` render. **(12) Measured-recall legs** (post-window, same tenancy, distinct markers, excluded from denominators) with per-S1-case schedules pinned at P0 (always-on = recall-under-quarantine, expected 0; mixed on/off = gate-open recall). TT is **MANDATORY** (+2–3 new TT triples from sites without natural-provenance authored cases); the M-yield stratum is a NAMED HOLD on rating (assembly-ready-EXCEPT). Labels come ONLY from blind raters (conservative-tie-break primary) — no tool verdict is ever a label | S3 wild-hunt plan rev 2.1 (unanimous) | Step-5 checklist rows (as-amended semantics); the §3 calibration-sizing premise (pool arithmetic corrected); no existing case/cell changes |
