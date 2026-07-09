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
strata with a benign-trap false-positive stratum and a blind-adjudicated wild stratum, under a
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
  present: <bool>                        # every S1 case MUST have one; the oracle's negative test (see §4 m2)
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
  config_provenance:                     # +field 3
    mist_properties: <path>
    triples: <path|null>
    timeout_caps: <values>
    mist_commit: <sha>                   # +field 3b: ONE frozen study-wide MIST commit (pin criteria in §6 note)
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
- `stratum==S1` ⇒ `label.value==genuine`, `label.provenance==by-injection`, `negative_control.present==true`.
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

## §3 The adjudication rubric (frozen; identical copy in `c3-rater-materials.md` §3 — R5)
**Three-way label {genuine, benign, underspecified}** (genuine≡positive, benign≡negative for S1/S2).
- **genuine** — a real acked-but-lost data-integrity fault: the SUT acknowledged the operation (HTTP
  2xx **with a success-shaped body — see the sentinel rule below**) while the durable write it promises
  did not land (or a downstream write in its causal closure did not), AND the intended behavior IS
  derivable from docs / spec / source.
- **benign** — the observed degradation is by-design / lived-with per docs / spec / source.
- **underspecified** — the intended behavior is NOT derivable from docs / spec / source. Excluded from
  the primary precision denominator; fraction reported; disagreement about underspecified-ness → the
  third adjudicator.
- **Sentinel/"success-shaped body" rule (R8/C-A2):** a response body carrying a failure sentinel or
  error status-field (`-1`, `{1,"error"}`, a negative id) is **NOT success-shaped** — the ack is
  tell-bearing (`ack_content_visibility = sentinel-in-body | status-field-tells`), a trivial body
  oracle catches it, and the case is tracked in the segregated tell-bearing bucket (§2 invariant), NOT
  the primary discriminating denominator. A genuine *discriminating* positive requires 2xx AND (empty
  body OR a success-shaped body with no failure sentinel). (Note per A's source check: TeaStore's `-1`
  is CLEARED from the blob before the 200 → the client sees a clean 200 → TeaStore natural is
  `success-shaped-clean`, a tell-FREE exhibit; TT-natural `{1,"error"}` is tell-bearing and is graded a
  detection TIE in `g3-result.md` — both are recorded honestly by this field.)

**Admissibility (R5 — the observation-vs-verdict split, verbatim in both files):**
- **Admissible AS the OBSERVATION to be judged:** the case's own presented material — the request
  sequence, the response(s), and the observed durable state (including the paired clean-run state).
- **The sole source of the NORM (what SHOULD have happened):** docs, OpenAPI/spec, source code.
- **Inadmissible:** distributed traces, any tool/oracle output (incl. MIST), and any runtime behavior
  BEYOND what the case itself presents. (The earlier flat "runtime behavior inadmissible" was wrong —
  it forbade the very datum every case is built around.)

**κ-gate:** if κ < 0.6 after calibration, at most TWO rubric-iteration rounds, CALIBRATION CASES ONLY
(no S3 peeking); after any iteration, relabel ALL prior cases under the final rubric (fresh raters if
available). **Statistics (R7):** report **S3-only κ as PRIMARY** (Clopper–Pearson counts when n<10);
pooled calibration+S3 κ is a SECONDARY small-n-stability figure carrying the calibration-inflation
caveat (calibration cases are the easy, rubric-tuned cases). Size calibration so pooled ≥50 is free
given S1+S2 ≥ 80. Prevalence-adjusted coefficient (PABAK/Gwet's AC1) alongside. CI units = distinct
defect/fault-sites, not flagged events.

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
  vs the adjudicated `label.value`; no fault/control legs. **S3 reproduction = captured-artifact +
  best-effort replay, non-determinism documented** (never silently re-manufactured as a deterministic
  injection — that would destroy its wild provenance).
- **Underspecified + tell-bearing buckets:** excluded from the primary discriminating denominator.
  **Precision reported both ways (A-M2):** *excluded* precision = genuine-fires / (genuine-fires +
  benign-fires); *included* precision = genuine-fires / (genuine-fires + benign-fires +
  underspecified-fires) — underspecified-fires count against precision when included.
- `NOT_EVALUABLE` (oracle cannot bind the observable) is its own bucket — never a silent pass/miss.
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
