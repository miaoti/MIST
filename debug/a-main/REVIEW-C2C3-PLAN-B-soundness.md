# Cold review B — C2+C3 execution plan @ 980164c, lens: design soundness + completeness (methods)

**Verdict: ACCEPT-WITH-CHANGES.** Skeleton honest + correctly grounded; but the measurement
instruments are underspecified at four load-bearing points (wild detector, trace pipelines,
adjudication volume, matched recall), the "first open-source" claim is defended one-competitor-deep
against ≥4 open prior corpora (one of which the plan itself imports), and several §8.5 commitments
are name-checked without a mechanism. All fixes are plan-text + one added engineering step; none
threatens the §1 adjudication (verified faithful).

## BLOCKING
- **B1 M-prevalence instrument UNDEFINED.** The validated paired differential oracle structurally
  cannot run wild (needs control/fault legs + isolation). Wild-capable: (a) the trace-shape masking
  oracle (needs instrumented traces); (b) single-leg read-back-absence = a DIFFERENT,
  never-FP-characterized operating mode. Fix: name the wild-mode detector(s) + operating points in
  §3.2; CALIBRATE its FP on the S2 stratum BEFORE S3 sampling; state its FP profile is NOT inherited
  from the paired-mode zeros.
- **B2 trace pipelines don't exist; no instrumentation step budgeted.** g3-result:144-145 — executed
  deployments traceless on target paths (TT sidecar-free cancel; SS no traceId); TeaStore =
  Kieker-native. Every E2 comparator + the wild masking oracle consume traces; TraceAnomaly also
  needs a per-SUT NORMAL-trace training corpus. Fix: insert step 2.5 "per-SUT OTel instrumentation +
  measured trace-coverage table (front-end auto-instr; span-links for async; normal-corpus capture)"
  — the artifact IS the §8.5-2 disclosure, pays for itself.
- **B3 "first open-source labeled benchmark" exposed.** Verified counterexamples: the **Filibuster
  application corpus** (SoCC'21, open, apps each "containing one or more fault tolerance bugs",
  Dockerized); **FudanSELab/train-ticket-fault-replicate** ("a benchmark microservice system with 22
  replicated faults" — open, labeled, and OUR S1 input); Nezha's open injected-fault dataset
  (FSE'23, root-cause labels); RCAEval. Claim survives only with the full qualifier chain: the first
  open benchmark FOR ORACLE EVALUATION ON THIS CLASS (masked-downstream/acked-but-lost) with a
  benign-trap FP stratum + adjudicated-wild stratum + provenance taxonomy. Fix: freeze the exact
  claim string + a claim-defense table (one row per competitor, stating which qualifier each fails)
  BEFORE the §2.4-1 freeze.

## MAJOR
- **M1 adjudication volume unbounded** ("every MIST-flagged event" ×10 seeds ×6 SUTs; Gate-1's ONE
  run produced 862 occurrences of one finding). Fix: pre-register event→case clustering (endpoint ×
  fault-signature × SUT), adjudicate representative + random audit sample per cluster.
- **M2 statistics.** (a) companion research/05 §5 pre-registers CI half-width ≤5% on precision;
  min(all,40) yields ±15–21% — supersede explicitly (wild scarcity) or raise n. (b) κ≥0.6 on n=20–40
  is noise (κ CI ±0.2–0.3; prevalence paradox) → pool calibration+S3 n≥50; report κ + CI + raw
  agreement + PABAK/Gwet's AC1. (c) per-SUT Wilson CIs with n<10 decorative → floor: below n=10
  report counts + Clopper–Pearson only, no per-SUT CI claims; pooled-with-stratification secondary.
  (d) inherit the correlated-denominator lesson: CI unit = distinct defect/fault-site, not flagged
  event.
- **M3 strata floors back-derived + gameable; S2 counting unit undefined; no per-family floor.**
  Nothing prevents S1=45 near-identical severs (count≠depth, §8.5-3's own concern); is the TT
  2,127-record corpus 1 case or many? Fix: define the case unit per stratum (packaged corpus = 1
  case + record-set attachment); diversity minima (≥k distinct mechanisms per SUT in S1; ≥m
  data-integrity cases per write-path SUT); make the §8.5-3 opportunity-count table NORMATIVE for S1
  quotas.
- **M4 "matched recall" operationally undefined + trace-invisible positives re-create the tautology
  on the headline class.** Naive span-error has no threshold; the constructed clean-win class has NO
  errored span by construction → span-ERROR comparators are 0-by-construction there = N-vs-0 through
  the back door. g3-result §7 itself names the fair form: downstream-span-PRESENCE. Fix: (a) define
  matched-recall procedure (threshold sweep where tunable; single (P,R) point otherwise; matching on
  the trace-visible subset); (b) tag every case at freeze with trace-visibility class
  (error-span-visible / span-presence-visible / trace-invisible); (c) ADD the Tracetest
  span-presence arm; (d) report comparator recall per visibility class.
- **M5 schema not freezable as listed** — add 7 fields: per-case NEGATIVE CONTROL (no-fault twin);
  SUT health preconditions + data seeding; oracle-config provenance + frozen MIST commit;
  label version-validity (bound to image digest); per-case license/redistribution; trace-visibility
  tag.
- **M6 no license/redistribution audit** (train-ticket-fault-replicate license unverified; upstream
  images redistribute-vs-reference-by-digest; manifests). Add a license-audit step + per-source
  disposition table; default reference-by-digest + build-from-source.
- **M7 rater ask unquantified; fallback needs scars.** Quantify: ≈15–45 h/rater (S3≤40 + calibration
  ~20 + cluster audit sample @15–45 min/case) ≈ 2–3 paid working days each — feasible, say so. The
  two-author-blind fallback partially undoes the §6 central fix (authors wrote the predicate);
  if triggered pre-commit: release all label evidence for community re-adjudication, report
  author-pair κ, demote the C3 precision claim one register IN THE ABSTRACT. Add consent/compensation
  sentence + rater-independence mechanics (no discussion channel pre-submission).
- **M8 M-prevalence workload unpinned + sampling-frame bias unstated.** N is a free variable;
  workload write-fraction determines the prevalence ceiling. Pin N or an event-count stopping rule;
  version workloads; report write fraction; inherit the two denominators (per-request,
  per-endpoint). AND: S3's frame = MIST-flagged events → the estimand is a DETECTOR-CONDITIONED
  LOWER BOUND — state it; optionally qualify with detector recall measured on S1.
- **M9 §8.5-1 rule text still unwritten.** Write it INTO the plan now: three-way label {genuine,
  benign, underspecified}; underspecified excluded from the primary precision denominator + reported
  as a fraction; underspecified-boundary disagreements go to the third rater; admissible evidence =
  docs/spec/source, inadmissible = runtime/traces/MIST output; κ-gate iteration: max rounds,
  calibration-cases-only (no S3 peeking), full relabel after iteration (ideally fresh raters).

## MINOR
m1 §8.5 audit: -3 folded (real mechanism), -6 folded, -4 folded but strike the illusory "unless E3
yields a head-to-head" escape (no replay tool runnable), -1 partial (M9), -2 partial (B2's coverage
table is the fix), **-5 never mentioned — add the four soundness-threat disclosures as a writing
obligation**. m2 describe RCAEval accurately (failure CASES w/ telemetry + root-cause label, not
"runs"). m3 benchmark ≥3-review needs a FORM: sampled reproduction (k random cases end-to-end + m
schema/label audits) + an automated per-case replay script. m4 data management: archive host
(Zenodo/OSF), size budget, hash manifest. m5 E1 fairness mechanics: spec provenance (authored specs
pre-registered + released), per-tool auth wiring, crash/timeout accounting, machine spec + run
exclusivity; cite-and-inherit the "No Time to Rest Yet" protocol. m6 budget ~2× optimistic (G1 =
7.8 h ONE run; TT = 13–15 GiB solo tenant; E1 = 300 SUT-h before setup) — per-step time-boxes. m7
E-item × SUT applicability matrix (Bookinfo ~4 GETs, no write path; Boutique thin) — prevents "on
the 6 SUTs" overclaiming uniformity. m8 inherit Holm/Bonferroni across the SUT × baseline grid.

## Not-considered list: 18 items (negative controls; health preconditions; oracle pin; license
audit; wild detector + its FP calibration; instrumentation wave + coverage table + TraceAnomaly
training corpus; visibility tagging + per-class recall; matched-recall definition; dedup/clustering;
workload pinning; lower-bound estimand; rater numbers/ethics/independence + relabel rule; κ
small-sample + correlated units + per-SUT n-floor + half-width reconciliation; per-family floors +
S2 unit; §8.5-5; data management; benchmark-review form + replay script; applicability matrix +
crash accounting + Holm + time-boxes.)

External sources checked: Filibuster corpus (SoCC'21 + christophermeiklejohn.com + github
filibuster-testing); FudanSELab/train-ticket-fault-replicate + LICENSE + Fault-Description wiki.
