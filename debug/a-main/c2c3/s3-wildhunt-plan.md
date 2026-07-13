# Plan — S3 wild-hunt + M-prevalence window (rev 1, DRAFT for 3-cold-review)

**Status:** DRAFT. Nothing executes until all reviewers ACCEPT (the standing gate).
**What this wave IS:** the pre-registered **Step-5** execution — run the wild detector over pinned
natural workloads on live SUTs, capture every flag as a **rater-ready wild-flag bundle**, and deliver
the **assembly-ready C3 rating corpus** (S3 cases + calibration mix) up to the frozen 9-check entry
gate. It is BOTH of the user's hard constraints in one artifact: (a) the material collection that the
rater study adjudicates, and (b) the only remaining path to the paper's **natural-discriminator
headline** — while its null outcome (scarcity/benign-dominance) is equally pre-registered and
publishable.

## §0 Deliverables (mapped to the rater study and the A-goal)

1. **The S3 candidate corpus**: every wild flag captured AT FLAG TIME as a sidecar-format bundle
   (freeze corpus-plan §5; B-M1: "wild-flag capture bundle present for every S3 case"), authored as
   `stratum: 3` case files (`capture_status: captured`, `label.provenance: by-adjudication`, label
   PENDING — **labels come from raters, never from the tool**; anti-circularity).
2. **The M-prevalence datum** (Step-5 obligations VERBATIM): detector-conditioned **LOWER-BOUND**
   estimand; detector recall on S1 as the qualifier; **two denominators** (per-request, per-endpoint);
   write-path fraction; workload scripts **versioned in the benchmark before the run**.
3. **The assembly-ready rating corpus**: S3 sample (min(all, 40), stratified) + calibration mix sized
   **max(30, 50−|S3|), benign-skewed ≥2:1** (freeze §3), rendered through the EXISTING `b4_harness.py`
   (§1.95.1 DONE, strip-list + 5 invariant tests) — delivered up to the 9-check entry gate with
   USER-side items (IRB received, rater blindness-screens/debriefs) explicitly marked as the gate's
   remaining holds. **First contact with raters happens only after IRB receipt (F22) — user-side.**
4. **A-goal mapping (explicit):**
   - *Headline path:* a rater-confirmed **genuine** wild flag on the natively-traced OTel-Demo whose
     same-write trace export scores MISS on the frozen comparator = the natural discriminator (the
     one claim every prior review said is still owed).
   - *Null path (equally pre-registered):* **<20 wild flags ⇒ scarcity IS the finding** — the
     benign-dominance branch: detector precision under natural operation + the M-prevalence lower
     bound + FP characterization. Honest, publishable, and pinned BEFORE we look.
   - *Either way:* the C3 κ study (S3-only κ primary) gets its wild stratum, closing the biggest
     open item of the benchmark's human-validation pillar.

## §1 Pre-registration this executes + the ONE disclosed deviation

Executes checklist **Step 5** as frozen: detectors S2-FP-calibrated BEFORE sampling; workload pin
(**the 500-write-stop branch** of "12 h/SUT or 500-write stop" — disclosed choice, single box);
S3 sample = min(all, 40) stratified; scarcity branch; conservative-tie-break primary; S3-only κ
primary; calibration sizing; CI units = **distinct defect-sites, not flagged events**.

**Deviation D1 (must be disclosed + reviewer-approved):** Step 5 pre-registers TWO wild detectors —
(i) the trace-shape masking oracle and (ii) single-leg read-back absence. **Detector (i) does not
exist** (`mist_trace_shape` is Branch-B, deferred with tool changes; every prior wave carried this).
This wave hunts with **detector (ii) only** and re-qualifies the estimand accordingly: the
M-prevalence lower bound is "**read-back-absence-detector-conditioned, over the BOUND write paths**"
(coverage stated per SUT: bound endpoints / total write endpoints). Compensating measure: on the
natively-traced OTel, EVERY flagged write also gets its per-write trace export (the E2/C1
traceparent-selection trick), so the trace-COMPARATOR columns are measurable on any confirmed find —
the headline needs the comparator to miss, not MIST's trace oracle to run. Detector-(i)'s absence
narrows the hunt's sensitivity (trace-visible-but-store-present anomalies are not hunted); disclosed.

## §2 Design (code-grounded; reuse-first)

**Detector (ii) = MIST observe mode, which EXISTS:** `DataIntegrityRuntime.beginObserveRun` +
`afterWrite` polling + the **W3 quarantine rule** (`observeTripleHasObservedPresent`: an absence
counts only if the SAME triple showed ≥1 OBSERVED_PRESENT in-session — mis-binding becomes a
quarantined warning, never a flag). Read-back transports are all bound and run-proven:
`SqlDurableReadback` (OTel, 2.75-A), `JsonDurableReadback` (TeaStore, 2.75-A), the G3 `/account`
value-delta + `RestAssuredHttp` (TT, E2).

**Per-SUT observe-runners** (new Java in `io.mist.cli.s3`, mirroring the 2.75-A/E2 harness pattern —
harness-level, inside the user-opened tool-code gate; **capture at RUNNER level keeps the
`mist_commit` pin** per the corpus-plan B1-fix-3 ownership decision):
- Drive the **pinned journey scripts** (committed BEFORE the run): mixed read+write natural user
  journeys, NO injected faults, NO vendor flags (flagd stays frozen default), fresh identities per
  journey, globally-unique request-derived markers per write (the 2.75-A isolation discipline).
- For each acked write: observe-mode read-back poll to the cap. **Flag levels (pre-registered here,
  BOTH counts reported):** RAW flag = acked ∧ absent-at-cap; **CONFIRMED flag = RAW ∧ still absent at
  a T+5 min re-probe** (the pending-vs-missing discipline from the kafka work — an async slow-drain
  is the known FP storm; RAW-only flags are reported as the delayed-not-lost bucket, never sampled
  into S3). S3 sampling draws from CONFIRMED flags.
- **At flag time, emit the wild-flag bundle** in the frozen sidecar format (ordered request records
  method/path/payload · response records status+full body · read-back/durable observations with
  RELATIVE times only · producer + mist_commit stamp) + a best-effort replay pointer. On OTel
  additionally fetch the flagged write's trace by its injected traceparent id (retained OUT of the
  rater-facing view — trace attachments are on the B4 strip-list; used only for comparator scoring).
- Emit the WINDOW log: total requests, write count, per-endpoint counts (the two denominators +
  write-path fraction), quarantined triples, RAW vs CONFIRMED flags.

**S2-FP calibration BEFORE sampling (pre-registered order):** per SUT, before the counted window,
run ≥20 acked benign writes through the identical detector path and hold the frozen bar
(`PairedFaultExecutor` fpProbe semantics: non-timeout-gated sync FP ≤5% over ≥20 acked; the OTel
async floor stays ≥25 s per 2.75-A). Bar fails ⇒ fix binding/caps, re-calibrate; the hunt window
starts only after a PASS. Calibration runs are reported in the RESULT.

**SUT set + order (D2, reviewer-decided):** one tenant at a time (26 GB WSL; the E2 lesson).
Recommended: **OTel-Demo first** (natively traced = headline-eligible; standing values), **TeaStore
second** (bound, cheap, trace-uninstrumented so flags are sidecar-only), **TT third as OPTIONAL**
(the richest natural-bug surface — FudanSELab TT is known-buggy — but the heaviest revival; the
battle-tested runbook exists). Sockshop EXCLUDED: its read-back surface is a draining queue count
(none-durable) — single-leg absence there is a guaranteed FP storm; disclosed.

**Known-site dedup rule (pre-registered):** a wild flag whose endpoint + failure mode matches an
ALREADY-AUTHORED S1/S2 case site (e.g., TT contacts dedupe) is tagged `known-site-rediscovery`,
EXCLUDED from the S3 rated sample (machine disjointness by true id + no double-counted defect-sites
in CI units), and reported separately as a detector-validation datum. Genuinely new sites only.

## §3 The rater-material pipeline (the user's constraint (a), made first-class)

1. Wild-flag bundles → S3 case files + sidecars (schema rev-2; NO `fault.injection`, no twin; freeze
   §4 S3 scoring path).
2. **Calibration arithmetic (surfaced honestly — a real constraint):** calibration =
   max(30, 50−|S3|), benign-skewed ≥2:1, and must be disjoint from S3/M-yield-audit/eligibility by
   true id. The captured corpus today = 9 pos / 11 neg. If |S3| lands small (likely under the
   scarcity branch), the required calibration (up to 40) EXCEEDS the existing pool. Pre-registered
   handling: (a) a **calibration top-up mini-capture** rider — additional benign/clean-twin captures
   on the SUTs we revive anyway during the hunt (cheap: clean journeys through already-bound
   endpoints, rendered by the same pipeline) sized after |S3| is known; (b) if the top-up still
   cannot reach the floor, the DISCLOSED calibration-shortfall branch (report the achieved size +
   its power consequence; never silently shrink the benign skew). Reviewers pin which.
3. Render EVERYTHING through the existing `b4_harness.py` (strip-list: label.*, fault.*, expect_*,
   oracle_expectation.*, negative_control, trace attachments, every tool string; relative times;
   opaque ids; shape uniformity across strata; deterministic output).
4. Deliver the **9-check entry-gate checklist** with our-side items DONE (same-harness-version ·
   tell-audit · sealed manifest + rubric version · corpus hash freeze · machine disjointness ·
   all-captured · wild-flag bundle per S3 case) and USER-side items marked as the remaining holds
   (**IRB determination RECEIVED before first contact — F22** · per-rater blindness-screen + debrief
   records). Upstream-filing deferral rule (M6) honored for any genuine find in the rated set.

## §4 Execution phases (each gated)

- **P0 — Pre-commit pins:** journey scripts per SUT (versioned) + the runner code + this plan's flag
  levels/dedup rules committed BEFORE any counted window. Runner unit tests (flag-level logic,
  sidecar shape, relative-time normalization) green.
- **P1 — OTel window:** revive (values pinned; kafka/checkout health per runbook) → S2-FP calibrate
  (≥20 benign, bar PASS) → 500-write hunt window (journeys; per-write traceparent) → bundles + window
  log → scale to 0.
- **P2 — TeaStore window:** revive → calibrate → 500-write window → bundles → scale to 0. (Never
  `GET /rest/generatedb`; never scale teastore-db.)
- **P3 — TT window (OPTIONAL, reviewer-decided):** revive per runbook (mysql-0 force-recreate fix;
  nacos doubleWrite) → calibrate → 500-write window over the bound triples' journeys → bundles →
  scale to 0.
- **P4 — Dedup + sample + author:** defect-site dedup → known-site exclusion → stratified
  min(all, 40) sample → S3 case files + sidecars; **<20 CONFIRMED flags ⇒ invoke the scarcity branch
  in the RESULT (pre-registered, not a failure).**
- **P5 — Rating-corpus assembly readiness:** calibration sizing per |S3| (+ top-up rider if pinned) →
  B4 render of all strata → our-side entry-gate checks → SEALED manifest + hash → hand-over note to
  the user (IRB/rater logistics + the §11 screens = user-side).
- **P6 — RESULT + 3-cold review** (the standing §7-style backstop) before anything is called
  claim-ready; corpus counts reported per the R1 rule; freeze §6 dated row.

## §5 Soundness (the standing disciplines, applied)

- **Anti-circularity:** the tool's flag selects CANDIDATES; the LABEL comes only from blind raters
  (conservative-tie-break primary). No case cell is verdict-valued by this wave; S3
  `oracle_expectation` for the flagging oracle records the already-emitted flag per freeze §4 (the
  observed-flag path), never a target invented post-hoc.
- **Blindness/leak:** B4 strip-list + opaque ids + relative times + shape uniformity; trace exports
  and tool logs never reach rater-facing bytes; the pinned per-SUT docs bundle is the only pointer
  set raters get.
- **Honest estimand:** lower-bound only, detector-(ii)-conditioned, bound-endpoint coverage stated,
  both denominators, write-path fraction, detector recall on S1 as the qualifier (computed on the
  captured S1 positives with bound read-backs: TT fabricated-ack, OTel checkout-lost, TeaStore
  maintenance + mesh-sever — measured or analytically derived, each marked which).
- **No prevalence overclaim:** flagged-event counts ≠ defect counts (CI units = distinct sites);
  RAW vs CONFIRMED both reported; quarantined triples listed, never silently dropped.
- **Environment:** one tenant at a time; every window's cluster ops via CRLF-stripped script files;
  end state = all tenants 0.

## §6 Explicitly OUT of scope

- Building detector (i) (`mist_trace_shape`) — stays Branch-B/deferred.
- The deferred kafkaQueueProblems S1 candidate (separate provenance discipline; separate item).
- **The actual rating** (rater contact, consent, ballots, κ computation on real ballots) — gated on
  the USER-side IRB + rater logistics; this wave delivers everything up to that gate.
- Any claim that a wild flag IS genuine before raters say so.

## §7 Definition of done

Runners + journey scripts committed pre-window; per-SUT S2-FP calibration PASS records; window logs
with both denominators + write-path fraction; every CONFIRMED flag has a sidecar-format bundle (+
trace export on OTel); RAW/CONFIRMED/quarantined/known-site counts reported; S3 sample + case files
authored (or the scarcity branch invoked); calibration mix sized + rendered; our-side entry-gate
checks green + SEALED manifest; hand-over note marking the USER-side holds; RESULT-of-record carrying
§0/§1 verbatim; freeze §6 dated row; README/FILE_INDEX/memory sync; tenants at 0. THEN the 3-cold
review of the RESULT.
