# Wave COMPLETION-SET — everything still owed before the paper draft — rev 2

**Date:** 2026-07-16 · Owner: main_track · Status: **rev 2 CONFIRMED 3/3 (A'/B'/C' all
CONFIRM, 2026-07-16; recon + confirm records local `REVIEW-CSET-*.md`) — EXECUTION OPEN
under the user's 2026-07-16 pre-authorization. Confirm-pass non-blocking residuals carried:
Phase-A density watched by the >1.5× stop rule; DoD §3.1 transitively includes A5-A8;
Phase-B "same fault instances" phrasing = inherited B-B1 wording, read per row-314's
observe-run/paired-rerun split. Order: Phase A → B → C.**
Rev-1 history: 3-cold = A REVISE-4B · B REVISE-2B · C ACCEPT-W-FIXES-3B; all 9 blocking +
non-blocking folded per `REVIEW-CSET-PLAN-RECONCILIATION.md`; §3.5 user amendment integrated.
**Trigger: USER direction 2026-07-16** — "所有的实验和材料都备齐了才去做 paper" — superseding
my Step-4-scoped "experiment surface complete" reading. This wave = the full inventory of
what WE can still do without user-side inputs; user-gated items are LISTED, never executed.

## §0 Honest inventory (what exists vs what this wave owes)

Banked already: corpus 26 cases (freeze §5), 2.75-A FIRE 5/5 ×2, S3 0/1514, R1d, E1+R2,
G1 FP 0/2127 + curve, G3 head-to-head cells + Rider-2 survey, E2 flagship cell (MIST
value-delta FIRE 5/5 vs frozen response-contract comparator MISS 5/5, cd275c9+TT-omnibus)
+ spec-locality, TT-omnibus trace-tier + E5 exact-4 OAT, MYC 6-SUT M-yield (Step 4 ✔).
**Owed (checklist truth, rev-2 corrected):** Step 6 = ◐ NOT ☐ — the flagship table already
carries `naive_span_error` + `service-map-presence` + `DB-span-presence` cells
(`b4/e2/e2-trace-scores.txt`, freeze row 305; breadth rows 292/293): the genuinely-missing
arms = Tracetest-SEMANTICS (surrogate), TraceAnomaly (conditional), contract-invariant (on
spike GO). Kafka S1 rider (a SECOND attempt — the first STOPPED, see Phase C); the
contract-invariant spike; Step-8 E3 mining re-scoped to EXISTING logs (M-yield + TT-omnibus
trees; the "E1-era logs" of rev 1 do not exist); seal-prep + E6-prep MATERIALS + the §3.5
C1×C2 integration layer (A5-A8).
**Rev-2 inventory adjudications (A-blocking folds):** (a) the freeze-row-308 low-risk
TeaStore observe-leg remedy = **RETIRED-AS-DISCHARGED** by TT-omnibus row 314 (fresh
observe-mode end-to-end on an injected loss, fault 5/5 OBSERVED_COMPLETE_ABSENT + control
clean); an opportunistic ride-along stays allowed if A5(iii) opens a TeaStore window.
(b) the Step-3b E1 baseline grid (☐, never run) = **SUPERSEDED-BY-MYC with disclosure**
(the 6-SUT pipeline ran at pinned budgets; the grid's exact matrix was not run as
specified) — checklist row annotated at execution. (c) checklist 2.5.5 (live Tracetest
Agent install) = **explicitly NOT attempted this wave** (true cost = a TT-omnibus-scale
window; declared out of scope, disclosed). (d) the Boutique 2.4 Istio gRPC-abort rider =
OPTIONAL short Phase-C-adjacent leg: close it or defer-with-reason at wave end.

## §1 Scope — three phases (risk-ordered: free → offline-heavy → live-risky)

### Phase A — no-tenant items (no cluster, no MIST tool code)
- **A1. E3 trigger-rate mining** (checklist Step 8; rev-2 re-scope — the rev-1 "E1-era
  logs" DO NOT EXIST): mine the EXISTING committed evidence only (the M-yield 6-SUT trees
  `b4/ttomni/myc/**` + the TT-omnibus logs). "Trigger" DEFINED (B-nb): an oracle-check
  emission above INFO, counted per oracle family per endpoint-class. Output:
  `RESULT-e3-mining.md` + scripts under `b4/e3/` (committed). Interpretation rail:
  DESCRIPTIVE pipeline telemetry — no yield/defect language (rater-gated), no cross-SUT
  pooling.
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
  recommendation, decision = USER) **+ the TT-admin ACK-TEXT cross-leg-tell keep-vs-exclude
  memo (A-nb fold — the disclosed rendered confound; same memo discipline, decision =
  USER)**;
  (iv) calibration-set assembly PREP: the mechanical draw (per frozen conventions: benign-
  skewed ≥2:1, disjointness by true id, `async-no-bound` exclusions) as a REHEARSAL
  manifest + entry-gate dry-run over the 8+1 checks that are machine-checkable today
  (IRB/blindness-screen rows marked USER-PENDING).
- **A4. E6 assembly PREP (no publication):** benchmark-repo layout staged under
  `benchmark/release-staging/` — index.generated, MANIFEST.sha256, license/component map
  (Apache-2.0 + CC-BY-4.0 per Step 8), README skeleton; the fork-publication decision and
  any push = USER.

### Phase B — E2 comparator-frontier COMPLETION (Step 6 ◐→✔; offline, rev-2 re-scoped)
- **Existing cells IMPORTED, not re-measured:** naive_span_error + service-map-presence +
  DB-span-presence flagship cells (`e2-trace-scores.txt`) + breadth cells flow through the
  A6 harness (re-derivation mismatch vs the banked txt ⇒ STOP+investigate).
- **New arms:** (1) **Tracetest-SEMANTICS SURROGATE** — verdict-source PINNED (B-blocking
  fold): offline surrogate over the co-generated TT-omnibus `leg1/` trace exports (SAME
  fault instances as the MIST column ⇒ matched inputs); cells labeled
  "span-assertion-semantics (surrogate; the live tool was NOT run)"; per-endpoint authoring
  cost measured by authoring the REAL Tracetest test spec offline (the automation-gap
  datum, symmetric with our triples cost). **The rev-1 live-leg branch is STRUCK**
  (fresh run-pairs ≠ matched inputs; live 2.5.5 = out of scope, §0(c)). (2) **TraceAnomaly
  conditional**: an ACTUAL clearance check first (license/feasibility, no armchair verdict);
  cleared ⇒ competitor cells (offline over the same exports if its input contract allows,
  else construction-blindness); uncleared ⇒ the pre-registered construction-blindness demo.
  (3) **contract-invariant (on the A2 spike GO)** — the arm's invariant spec authored BLIND
  to outcomes (from the OpenAPI + the traces' REQUEST side only).
- Scoring: ONLY through the A6 harness; NOT_EVALUABLE = its own bucket;
  trace-invisible-by-construction = its own N-vs-0 row; per-visibility-class recall (A7).
- Outputs: the completed matched-recall table, runner scripts, `RESULT-e2-frontier.md` +
  freeze row. **Claim rail:** matched-recall ONLY (never "discrimination"); never pool
  self-concordant read-back cells; the MIST column stays the TT-omnibus live-provenance
  cells (NOT re-run); surrogate cells NEVER presented as the live tool.

### Phase C — kafka S1 (the deferred stochastic rider; own tenancy window, LAST; rev-2 rewritten)
- **This is a SECOND ATTEMPT, disclosed:** the first attempt STOPPED
  (`tenancy-window-result.md` item 3: 7/8 orders permanently lost; the wedge PERSISTED past
  flag-off). **The wedge trigger of record = the FLAG-ON condition itself wedging rdkafka**
  (not kafka-pod replacement, which is a separate known edge).
- The `kafkaQueueProblems` vendor-flag S1 case on live OTel-demo, per the frozen R1 X4
  conventions: control-leg-FIRST N≥10 off → N≥20 on; per-lost-trial scoring; measured rate
  + Wilson CI in `fault.config`; **T+5 min RE-PROBE per trial (BINDING — delayed-but-landed
  ≠ LOST; ground-truth integrity)**; 1 representative lost trial + the N-trial record as
  raw evidence; `injection_method: vendor_flag`, `ground_truth.source: vendor`; case stamps
  incl. `readback_shape` + calibration-eligibility (`async-no-bound` ⇒
  calibration-genuine-INELIGIBLE); **A-M8 contract-grounding disclosure for the accounting
  write**; ground truth = direct DB reads via script FILES (psql quoting rail), never MIST.
- **Sequence (BINDING):** flag flips via the **flagd-ui API recipe-of-record** (freeze rows
  299/306 — CM patches never reach the runtime copy; rev-1's "script files" applied to the
  wrong channel) → flag-off → **POISONING-WINDOW drain + health CANARY (a clean control
  order end-to-end)** → if the canary fails: recovery-restart checkout+accounting+fraud →
  re-canary → **still failing ⇒ STOP+disclose, NO third attempt in-wave**.
- Rails: no JVM tools in the 700Mi kafka pod; single-tenant window; RAM checkpoints; never
  `wsl --shutdown` on a healthy cluster. (The 800 ms pacing rail is TT-gateway-specific —
  not carried here; OTel pacing = the capture-recipe-of-record's.)
- Optional adjacent short leg: the Boutique 2.4 gRPC-abort rider (§0(d)) — close or
  defer-with-reason.
- Output: 1-2 new S1 case files (schema-validated; NOT counted toward S2 floors), capture
  log, freeze row. Budget: a full ½-1.5 d window (widened per C-B2). STOP ⇒ the corpus
  stands at 26 (additive, not load-bearing).

## §2 Budgets + stop rules

- Phase A ≈ 1-1.5 days (offline; A5-A8 added). Phase B ≈ 1-2 days offline (NO live leg —
  struck per B-B1). Phase C ≈ ½-1.5 day live window (widened per C-B2). Per-leg >1.5× ⇒
  pause+disclose.
- Stop rules: any arm needing MIST tool-code changes ⇒ that arm runs in its
  offline-approximation form + disclose (never code under this wave; the 2026-07-10 gate
  amendment covers comparator-arm RUNS, not oracle changes); TraceAnomaly unclear ⇒ the
  construction-blindness branch; kafka: the Phase-C STOP-again criterion (no third
  attempt); staging never touches sealed artifacts — READ-ONLY set (C-B1+A-nb, convergent):
  `rater-sidecars/` + `b4/MANIFEST-r2.json` + **`b4/s3/SEALED-MANIFEST.sha256` (v2)**; all
  A3 outputs land strictly under `rater-sidecars-staging/`; imported-cell re-derivation
  mismatch (Phase B) ⇒ STOP+investigate.

## §3 DoD

1. A1/A2/A3/A4 artifacts committed (mining table; spike memo w/ GO/NO-GO; 3 staging
   packages + 2 decision memos; release-staging tree) — each with its interpretation rails.
2. Step 6 folds: ✔ if all non-conditional arms produce cells at pinned scoring (else ◐ +
   blockers named); conditional arms resolved per their branches, disclosed.
3. Phase C: case captured + validated, or the STOP disclosure.
4. RESULT docs + freeze rows + FILE_INDEX + memory; post-hoc 3-cold review per RESULT.
5. After DoD: the ONLY remaining pre-draft items are USER-side (IRB/raters, fork-pub, seal
   decisions, venue) — CONDITIONAL on the §0 rev-2 adjudications standing (row-308 remedy
   retired-as-discharged; E1 grid superseded-by-MYC-disclosed; 2.5.5 declared out;
   2.4 rider closed-or-deferred-with-reason). **The paper draft then still waits for
   EXPLICIT USER CONSENT (user gate 2026-07-16) — DoD here never auto-opens drafting.**

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
