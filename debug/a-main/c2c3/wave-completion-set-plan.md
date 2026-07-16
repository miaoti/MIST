# Wave COMPLETION-SET — everything still owed before the paper draft — rev 1

**Date:** 2026-07-16 · Owner: main_track · Status: **rev 1 — awaiting 3-cold review
(ALL-ACCEPT gate before execution; /goal rule).**
**Trigger: USER direction 2026-07-16** — "所有的实验和材料都备齐了才去做 paper" — superseding
my Step-4-scoped "experiment surface complete" reading. This wave = the full inventory of
what WE can still do without user-side inputs; user-gated items are LISTED, never executed.

## §0 Honest inventory (what exists vs what this wave owes)

Banked already: corpus 26 cases (freeze §5), 2.75-A FIRE 5/5 ×2, S3 0/1514, R1d, E1+R2,
G1 FP 0/2127 + curve, G3 head-to-head cells + Rider-2 survey, E2 flagship cell (MIST
value-delta FIRE 5/5 vs frozen response-contract comparator MISS 5/5, cd275c9+TT-omnibus)
+ spec-locality, TT-omnibus trace-tier + E5 exact-4 OAT, MYC 6-SUT M-yield (Step 4 ✔).
**Owed (checklist truth):** Step 6 = ☐ (the 5-arm frontier beyond the flagship pair);
kafka S1 rider; the contract-invariant spike; Step-8 E3 mining (☐, "free"); seal-prep +
E6-prep MATERIALS (prep only — the seal/fork decisions are USER's).

## §1 Scope — three phases (risk-ordered: free → offline-heavy → live-risky)

### Phase A — no-tenant items (no cluster, no MIST tool code)
- **A1. E3 trigger-rate mining** (checklist Step 8): mine the E1-era + M-yield committed
  logs/evidence for oracle trigger rates per endpoint-class. Output: `RESULT-e3-mining.md`
  table + scripts under `b4/e3/` (committed). Interpretation rail: DESCRIPTIVE pipeline
  telemetry — no yield/defect language (rater-gated), no cross-SUT pooling.
- **A2. Contract-invariant arm SPIKE** (the deferred no-tenant rider): assess feasibility +
  authoring cost of a contract/OpenAPI-invariant comparator arm over the COMMITTED E2
  traces + specs (response-schema/status-model invariants; AGORA-class positioning). Output:
  spike memo `e2-contract-invariant-spike.md` — arm design, per-endpoint authoring-cost
  estimate, and a GO/NO-GO recommendation for including it as an E2 arm in Phase B. A spike,
  NOT the arm run; no MIST code — offline scripts under `b4/e2/` only.
- **A3. Seal-prep MATERIALS (prep-only, decisions stay USER's):**
  (i) S3-BENIGN-01 re-cut PREP: generate the corrected blind-titled rater sidecar via the
  hardened `b4_harness` (opaque-id guard makes the class impossible) — staged as
  `rater-sidecars-staging/`, NOT swapped into the sealed set (re-seal = USER-witnessed
  decision at Step 5);
  (ii) TT-truncation per-endpoint rendering: implement + run the gated per-endpoint
  membership/value/count rendering for the 9 `tt-collection-truncation-gated` cases into
  staging, with a before/after rateability note;
  (iii) SS `sockshop-swallowed` keep-vs-exclude ANALYSIS memo (both branches consequenced,
  recommendation, decision = USER);
  (iv) calibration-set assembly PREP: the mechanical draw (per frozen conventions: benign-
  skewed ≥2:1, disjointness by true id, `async-no-bound` exclusions) as a REHEARSAL
  manifest + entry-gate dry-run over the 8+1 checks that are machine-checkable today
  (IRB/blindness-screen rows marked USER-PENDING).
- **A4. E6 assembly PREP (no publication):** benchmark-repo layout staged under
  `benchmark/release-staging/` — index.generated, MANIFEST.sha256, license/component map
  (Apache-2.0 + CC-BY-4.0 per Step 8), README skeleton; the fork-publication decision and
  any push = USER.

### Phase B — E2 5-arm comparator frontier (Step 6; offline-first)
- Arms per the checklist: **naive span-error** · **Tracetest span-error** · **Tracetest
  span-PRESENCE** (per-endpoint authoring cost RECORDED — the automation-gap datum,
  symmetric with our triples cost) · **TraceAnomaly (conditional-as-cleared:** run as
  competitor only if license/feasibility clears at Phase-B0; else the pre-registered
  construction-blindness demo**)** · **contract-invariant (conditional on the A2 spike GO)**.
- **Phase B0 (pinned before any arm runs):** per-arm feasibility + input contract — what
  each arm consumes (the COMMITTED E2 trace bundles + specs FIRST; a live leg is in scope
  ONLY if an arm provably cannot run offline, and then ONLY on the TT cancel-refund pair
  with the e2 recipe verbatim); scoring stays `trace_score.py`-class frozen scripts;
  NOT_EVALUABLE = its own bucket; trace-invisible-by-construction = its own N-vs-0 row.
- Outputs: per-arm cells into the E2 matched-recall table (recall per visibility class),
  runner scripts committed, `RESULT-e2-frontier.md` + freeze row.
- **Claim rail:** matched-recall vs comparators ONLY (never "discrimination"); never pool
  self-concordant read-back cells; the MIST column stays the TT-omnibus live-provenance
  cells (NOT re-run).

### Phase C — kafka S1 (the deferred stochastic rider; own tenancy window, LAST)
- The `kafkaQueueProblems` vendor-flag S1 case on live OTel-demo, per the frozen R1 X4
  stochastic conventions: control-leg-FIRST N≥10 off → N≥20 on; per-lost-trial scoring;
  measured rate + Wilson CI in `fault.config`; 1 representative lost trial + the N-trial
  record as raw evidence; `injection_method: vendor_flag`, `ground_truth.source: vendor`;
  ground truth = direct DB reads, never MIST.
- **Kafka rails (sharp edges, verbatim):** no JVM tools in the 700Mi kafka pod; kafka-pod
  REPLACEMENT wedges rdkafka → restart checkout+accounting+fraud after any kafka restart;
  flagd flag flips via script files only; pace 800 ms; single-tenant window; RAM checkpoints;
  never `wsl --shutdown` on a healthy cluster.
- Output: 1-2 new S1 case files (schema-validated; NOT counted toward S2 floors), capture
  log, freeze row. If the case cannot be captured within budget ⇒ STOP + disclose (the
  corpus stands at 26; this is additive, not load-bearing).

## §2 Budgets + stop rules

- Phase A ≈ 1 day (all offline). Phase B ≈ 1-2 days offline (+½ day if ONE live leg is
  proven necessary). Phase C ≈ ½-1 day live window. Per-leg >1.5× ⇒ pause+disclose.
- Stop rules: any arm needing MIST tool-code changes ⇒ that arm runs in its
  offline-approximation form + disclose (never code under this wave; the 2026-07-10 gate
  amendment covers comparator-arm RUNS, not oracle changes); TraceAnomaly unclear ⇒ the
  construction-blindness branch; kafka wedge beyond runbook ⇒ STOP+surface; staging never
  touches sealed artifacts (`rater-sidecars/` + `MANIFEST-r2.json` read-only this wave).

## §3 DoD

1. A1/A2/A3/A4 artifacts committed (mining table; spike memo w/ GO/NO-GO; 3 staging
   packages + 2 decision memos; release-staging tree) — each with its interpretation rails.
2. Step 6 folds: ✔ if all non-conditional arms produce cells at pinned scoring (else ◐ +
   blockers named); conditional arms resolved per their branches, disclosed.
3. Phase C: case captured + validated, or the STOP disclosure.
4. RESULT docs + freeze rows + FILE_INDEX + memory; post-hoc 3-cold review per RESULT.
5. After DoD: the ONLY remaining pre-draft items are USER-side (IRB/raters, fork-pub, seal
   decisions, venue) ⇒ the paper-draft plan re-enters its review.

## §3.5 USER-DIRECTED AMENDMENT (2026-07-16, dated — added while rev-1 review in flight; folds into rev 2 at reconciliation): C1×C2 INTEGRATION — the benchmark (contribution 1) × MIST (contribution 2) mechanical linkage

User: "benchmark 是第一贡献, MIST 是第二贡献 … 第一贡献和第二贡献的 integration 这些都做好了吗?"
Verified state (2026-07-16 greps): 26/26 cases carry `mist_readback_oracle` + `mist_commit`;
verdicts = **flag 7 / no_flag 11 / `not_applicable` 8**. The integration gaps below are OWED:

- **A5. The 26-case MIST-column census + the 8 `not_applicable` adjudication.**
  (i) One committed artifact (`benchmark/mist-column-census.json` + md rendering): per case —
  verdict + `oracle_mode` + `mist_commit` + the PROVENANCE RUN pointer (which wave/log), or
  `not_applicable` with a PRINCIPLED reason class. (ii) Adjudicate EACH of the 8
  `not_applicable` (bookinfo-ratings; sockshop control + swallowed-enqueue; the teastore
  meshsever×4 + depdown families): STRUCTURAL (read-only SUT / no read-back surface /
  trace-only evidence) vs **bindable-pending-eval left over from R1**. (iii) For any case
  adjudicated bindable-pending-eval: BRANCH — run the missing MIST read-back leg (2.75-A
  harness-binding style; TeaStore legs fold into a SHORT tenancy window appended to Phase C
  scheduling; ground truth = direct reads, never MIST) OR re-stamp as
  `not_evaluable`-with-reason, DISCLOSED. **NO silent pending survives this wave** — the
  paper's benchmark-with-MIST-column claim needs every cell filled or principled.
- **A6. The SCORING HARNESS (Step 8 B-m4, built now — the single mechanical C1×C2 path):**
  benchmark labels × per-tool verdict files → matched-recall cells + per-visibility-class
  rows + NOT_EVALUABLE bucket + the trace-invisible N-vs-0 row. Committed under
  `benchmark/scoring/` (license note per Step 8); **Phase B arms MUST be scored through it**
  (no ad hoc per-arm scoring), and the MIST column flows from A5 through the same harness.
- **A7. The per-case TRACE-VISIBILITY census** (trace-visible / trace-invisible-by-
  construction / NOT_EVALUABLE — the E2 reporting obligation's input): committed artifact
  (`benchmark/e2-visibility-census.json`), derivation rule stated per case (from capture
  evidence + spec, NOT from any comparator's output — no circularity), feeding A6's rows.
- **A8. The case ↔ trace-bundle ↔ arm mapping table** (which committed trace bundle serves
  which case for which Phase-B arm; gaps = that arm's NOT_EVALUABLE rows, disclosed).
- **A4 extension (reproduction machinery census):** per-case executable-reproduction status
  (recipe + injection method + replay-ability vs capture-only) recorded in the release
  staging — the Step-8 sampled-reproduction review (k=5 re-runs + m=15 audits) becomes
  mechanically possible; the review itself stays at Step 8.
- Sequencing: A5-A8 are Phase-A no-tenant EXCEPT the A5(iii) run branch (short window,
  scheduled with/before Phase C). Phase B does not start until A6-A8 exist (its scoring +
  denominators depend on them). The deferred `paper-draft-plan.md` claim map gains a P16
  (integration census) when it re-enters review.

## §4 NOT in scope

Rater contact/rating; IRB; the actual re-seal/publication (decisions = USER); MIST
tool/oracle code; E1 grid; new positive-site hunting (closed per freeze §5); the paper
draft itself; SmartFetch (parallel track).
