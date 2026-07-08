# C2 FREEZE — claim, per-case schema, rubric, index format (plan v2 §2.4-1)

**Status: FROZEN ON COMMIT.** Any post-freeze change to this document is a DISCLOSED AMENDMENT
(dated note in §6, reason + what changed; the amendment discipline of the blind-contract precedent).
Inputs already discharged: the related-work sweep (`c2-claim-sweep.md` — claim SAFE) and the license
audit (`c2-license-audit.md` — no blockers, 4 conduct rules). The §8.5-3 depth survey feeds S1
QUOTAS at step 3 and is normative for population, not part of this freeze.

## §1 The frozen claim + obligations
**Claim string (final):** *"the first open-source labeled benchmark built for ORACLE EVALUATION on
masked-downstream / acknowledged-but-lost data-integrity faults — pairing positive strata with a
benign-trap false-positive stratum and a blind-adjudicated wild stratum, under a per-case
label-provenance taxonomy."* (One word hardened vs the plan draft: "per-case provenance taxonomy" →
"per-case **label-provenance** taxonomy", discharging the sweep's wording obligation at the string
itself.)
- **Definition (first use, binding for the paper):** the label-provenance taxonomy classifies HOW
  each case's ground-truth label was established — `by-injection` (true by construction),
  `by-docs` (benign-by-design, cited to SUT docs/source), `by-adjudication` (blind-rated per the
  rubric) — orthogonal to fault taxonomies (what the fault IS) and to RCA label pipelines.
- **Proactive citations (sweep §3):** CloudAnoBench cited where S2 is motivated (shares the
  benign-stratum idea; differs on Q3+Q4 — synthesized telemetry AD vs executed-SUT oracle eval);
  the Uber Zenodo artifact (CC-BY-4.0) cited alongside the paper (raw wild traces, unlabeled,
  unadjudicated). **Positioning sentence (sweep finding 3):** OpenRCA 2.0 and the
  fault-propagation-aware TT benchmark EXCLUDE the masked class by construction (SLO-impact filters;
  84.4% "No Anomaly" discarded) — the prevailing methodology filters out exactly the stratum this
  benchmark labels.
- Defense table = `c2-claim-sweep.md` §2 (18 rows + the 4 original) — shipped with the benchmark.
- Camera-ready watch-list: re-check OpenRCA 2.0 + FP-aware-TT release forms before submission.

## §2 Per-case schema (the machine-readable index row; one YAML document per case)
Each case is one file `cases/<id>.yaml`. All seven review-B-M5 fields are present (tagged `# +field`
below). Fields marked `# @eval` are FILLED during population/evaluation and are NOT frozen here —
freezing them would mean freezing results; the FREEZE covers the KEYS and their admissible values.

```yaml
# ---- identity ----
id: <sut>-<stratum>-<slug>              # unique, stable; filename == <id>.yaml
schema_version: 1                        # this freeze; a bump is a disclosed amendment
stratum: S1 | S2 | S3                    # positives-by-construction | benign-trap | adjudicated-wild
title: <one-line human summary>

# ---- SUT + version pins (the reproducibility root) ----
sut:
  name: trainticket | sockshop | teastore | oteldemo | boutique | bookinfo
  deploy:
    manifests: <path-in-benchmark-repo>         # authored or redistributed per the license audit
    manifests_change_notice: <bool>             # true if upstream was modified (Apache-2.0 §4)
    images:                                      # reference-by-digest ONLY; never re-pushed by us
      - ref: <registry/name:tag>
        digest: sha256:<64hex>                   # the label is bound to THIS digest
    replay_script: <path>                        # automated per-case reproduce on a clean cluster
  health_preconditions:                          # +field 2 (Gate-1-style pre-flight)
    checklist: <path>                            # pods Ready, endpoints 200, mesh/broker state
    data_seeding: <path|null>                    # required fixtures (users, catalogue, funded acct)

# ---- the exercised fault + its clean twin ----
fault:
  mechanism: flag | mesh-sever | broker-policy | code-level | none   # none == an S2 benign case
  injection: <how — kubectl apply <manifest> | @Value flag | rabbitmqctl | runtime-toggle URL>
  target_service: <service the masked / lost write lands in>
  expected_observable: <what SHOULD change durably and does not — the ground truth of "lost">
  provenance_class: by-injection | by-docs | by-adjudication          # the label-provenance taxonomy
negative_control:                                # +field 1 (the no-fault twin)
  present: <bool>                                # every S1 case MUST have one; scored as a true negative
  replay_script: <path|null>                     # same stimulus, no fault; the durable write DOES land

# ---- stimulus / workload ----
stimulus:
  script: <path>                                 # register -> ... -> write sequence, versioned
  write_path: <bool>                             # carries a durable write? (bounds prevalence ceiling)
  workload_class: authored-scenario | builtin-loadgen | scripted-browse | none

# ---- the oracle-evaluation contract (what ANY oracle is scored against) ----
oracle_eval:
  observable_pin: <the single durable observable the case tests>     # anti-gaming; no oracle-specific tailoring
  trace_visibility: error-span-visible | span-presence-visible | trace-invisible   # +field 6; feeds E2 per-class
  config_provenance:                             # +field 3
    mist_properties: <path>
    triples: <path|null>
    timeout_caps: <values>
    mist_commit: <sha>                           # +field 3b: ONE frozen MIST commit for the WHOLE study
  comparator_configs:                            # @eval — per-arm configs used in E2
    - arm: naive | tracetest-error | tracetest-presence | traceanomaly
      config: <path>

# ---- the label ----
label:
  value: genuine | benign | underspecified       # §8.5-1 three-way; S1==genuine-by-construction, S2==benign
  provenance: by-injection | by-docs | by-adjudication
  version: <n>                                    # +field 4 (label-version)
  version_validity:
    image_digest: sha256:<64hex>                  # label valid ONLY for this digest
    doc_citation: <url+version|null>              # by-docs labels cite the doc version
  underspecified: <bool>                          # true iff value==underspecified (§8.5-1 marker)
  adjudication_record: <path|null>                # S3 only — rater ballots + the pooled-kappa context

# ---- provenance / license (+field 5) ----
license:
  case_code: Apache-2.0                           # benchmark-authored glue + scripts
  case_data: CC-BY-4.0                            # labels, ground truth, adjudication records, run outputs
  upstream_sut: <SPDX of the SUT>                 # component-map pointer (all six SUTs are Apache-2.0)
  sources: [<citations>]                          # e.g. train-ticket-fault-replicate == replicated-by-description

# ---- raw artifacts + recorded verdicts ----
artifacts:                                        # @eval — not part of the freeze
  raw_logs: [<paths>]
  mist_verdict: FIRE | NO_FIRE | NOT_EVALUABLE | null
  comparator_verdicts: [{arm: <name>, verdict: <value>}]
```

**Schema invariants (frozen, machine-checkable at population):**
- `stratum==S1` ⇒ `label.value==genuine`, `label.provenance==by-injection`, `negative_control.present==true`.
- `stratum==S2` ⇒ `label.value==benign`, `label.provenance==by-docs`, `fault.mechanism==none` OR a
  documented benign degradation, `label.version_validity.doc_citation!=null`.
- `stratum==S3` ⇒ `label.provenance==by-adjudication`, `label.adjudication_record!=null`.
- `label.value==underspecified` ⇒ `label.underspecified==true` and the case is EXCLUDED from the
  primary precision denominator (reported separately — §3 rubric).
- `oracle_eval.config_provenance.mist_commit` is IDENTICAL across every case (oracle drift would
  break comparability — plan §2.2).
- Zero labels are derived from MIST's own predicate (plan §2.4 acceptance).

## §3 The adjudication rubric (frozen — governs S3 population + the C3 blind labels)
The rubric is embedded verbatim from plan v2 §3.1 (the §8.5-1 rule). It is FROZEN here so raters and
reviewers read one authoritative copy.

**Three-way label {genuine, benign, underspecified}:**
- **genuine** — a real acked-but-lost data-integrity fault: the SUT acknowledged the client
  operation (2xx or a success-shaped body) while the durable write the operation promises did not
  land (or a downstream write in its causal closure did not), AND the intended behavior IS derivable
  from docs / OpenAPI-spec / source (i.e., "it should have persisted" is contract-grounded).
- **benign** — the observed degradation is by-design / lived-with: docs, spec, or source establish
  that the non-persistence (or the masked downstream state) is acceptable, retried elsewhere,
  eventually-consistent within contract, or otherwise not a defect.
- **underspecified** — the intended behavior for the observed degradation is NOT derivable from
  docs / spec / source. Underspecified cases are EXCLUDED from the primary precision denominator and
  their fraction is reported; precision is reported both including and excluding them. A disagreement
  about WHETHER a case is underspecified goes to the third rater like any other disagreement.

**Admissible evidence:** docs, OpenAPI / spec, source code.
**Inadmissible evidence:** runtime behavior, traces, MIST output (raters are MIST-blind).

**κ-gate iteration rule:** if κ < 0.6 after the calibration round, at most TWO rubric-iteration
rounds, each using CALIBRATION CASES ONLY (no S3 peeking). After any iteration, ALL
previously-labeled cases are relabeled under the final rubric (fresh raters if available).

**Rater independence + statistics (plan §3.1, frozen pointers):** ≥2 MIST-blind raters with
microservice literacy + a third adjudicator; no discussion channel before submission; κ over pooled
calibration+S3 (n≥50) reported with CI + raw agreement + a prevalence-adjusted coefficient
(PABAK / Gwet's AC1); per-SUT n<10 → Clopper–Pearson counts only, no per-SUT CI claims; CI units are
distinct defect / fault-sites, not flagged events. The two-author-blind FALLBACK carries its
pre-committed scars (abstract-level precision demotion + full label release + author-pair κ).

## §4 Machine index format (how cases aggregate + the scoring contract)
**Layout (frozen):**
```
benchmark-repo/
  cases/<id>.yaml               # one document per case (the §2 schema)
  index.generated.yaml          # BUILT from cases/*.yaml (not hand-edited): id, sut, stratum,
                                #   label.value, provenance, trace_visibility, fault.mechanism
  MANIFEST.sha256               # hash of every case file + every referenced local artifact
  LICENSE / README component-map   # Apache-2.0 vs CC-BY-4.0 vs MIST-by-reference (license audit §)
  raw/                          # large artifacts (traces/transcripts) -> Zenodo/OSF mirror by hash
```
**Scoring contract (frozen — how a harness scores ANY oracle against a case):**
1. Deploy `sut.deploy` at the pinned digest; assert `health_preconditions`.
2. Run the fault leg (`stimulus.script` + `fault.injection`); record the oracle's verdict on
   `oracle_eval.observable_pin`.
3. If `negative_control.present`, run the control leg (same stimulus, no fault); record the verdict.
4. Score against `label.value`: on a `genuine` case the fault leg should FIRE (else a false negative);
   on any case the control leg should NOT fire (else a false positive); on a `benign` case the fault
   leg should NOT fire (else a false positive). `underspecified` cases are tallied separately and
   excluded from the primary precision denominator.
5. `NOT_EVALUABLE` verdicts (oracle cannot bind the observable) are their own bucket — never silently
   counted as a pass or a miss (the G3 evaluability discipline).
**Aggregation views the index MUST support (frozen):** per-stratum, per-SUT, per-visibility-class,
per-fault-mechanism, and per-provenance-class. E2 comparator recall is reported PER visibility class
(trace-invisible positives are their own disclosed row — plan §4 E2).

## §5 Frozen acceptance floors (from plan §2.3 — restated so the population step reads one copy)
- **Headline formula:** ≥80 constructed/benign cases + wild as-found. S1 ≥ 45, S2 ≥ 35, S3 target 20
  with the shortfall-is-a-finding rule; "≥100" is reported only if S3 ≥ 20 materializes.
- **Case units:** an S2 case = one designed-degradation path on one SUT; a packaged FP corpus = ONE
  case with a record-set attachment (no padding).
- **Diversity minima:** S1 ≥ 4 distinct fault MECHANISMS per write-path SUT; ≥ 6 data-integrity
  (acked-but-lost) cases ACROSS the write-path SUTs; the §8.5-3 opportunity-count table (step 3) is
  NORMATIVE for per-SUT S1 quotas. F-corpus: floor ≥ 6 replications (F6/F8/F10/F20 + 2), target ≥ 10.
- **Reproduction:** every case reproduces via its automated per-case replay script on a clean
  cluster; label provenance complete; zero labels from MIST's own predicate.
- **Artifact review:** the benchmark's ≥3-cold-review takes the sampled-reproduction form (each
  reviewer re-runs k=5 random cases end-to-end + schema/label-audits m=15 more).

## §6 Amendments log
FROZEN ON COMMIT. Every post-freeze change is a dated row: date · what changed · why · which downstream
artifact it invalidates (if any).

| date | change | reason | invalidates |
|---|---|---|---|
| — | (none yet — freeze baseline) | — | — |
