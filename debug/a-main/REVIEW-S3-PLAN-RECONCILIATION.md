# S3 wild-hunt plan — 3-cold-review reconciliation (rev 1 → rev 2)

**Date:** 2026-07-13
**Reviewed:** `debug/a-main/c2c3/s3-wildhunt-plan.md` rev 1 (pre-execution plan review).
**Reviewers:** A = oracle-soundness/statistics · B = engineering/repro · C = hostile-PC/rater-protocol.

## Verdicts on rev 1

| reviewer | verdict | blocking | major |
|---|---|---|---|
| A | ACCEPT-WITH-FIXES | 5 (F1–F5) | 5 (F6–F10) |
| B | ACCEPT-WITH-FIXES | 3 (B1–B3) | 4 (M4–M7) |
| C | ACCEPT-WITH-FIXES | 3 (B1–B3) | 6 (M1–M6) |

**NOT unanimous-clean → rev 1 does not execute.** All 11 blocking + 15 major findings folded into
rev 2. Convergence was high (the dedup contradiction found independently by B+C; the calibration
shortfall by A+C; TT-mandatory by B+C).

## Disposition (rev-2 section in parentheses)

### Blocking — all folded

- **[C-B1] Pre-register Step-5-as-amended BEFORE the window, not at RESULT time** → §1: the P0
  freeze §6 row enumerating every delta, committed before any calibration/window.
- **[C-B2 = B-B1] The known-site dedup rule vetoed the headline by construction** (every bound
  endpoint has an authored case; a natural OTel checkout loss would be excluded as "rediscovery")
  → §2b: exclusion PROVENANCE-SCOPED — only natural-provenance authored sites excluded;
  injected-provenance sites stay S3-eligible + pre-window environment guard (flagd defaults via the
  flagd-ui API, kafka healthy, maintenance off) + mandatory root-cause-distinction note.
- **[C-B3 + A-F5 + A-F10] Calibration top-up: arithmetic wrong (up to 50, not 40; shortfall
  UNCONDITIONAL — min 30 calib needs ≥20 benign vs 11 pool), ordering contradiction (sized after
  |S3| but tenants at 0), and "clean journeys" = a stratum-decodable blindness hazard** → §4.1:
  corrected bounds; unconditional fixed worst-case batch ~25–30 captured DURING each window;
  **degradation-shaped S2 benigns only** (never "nothing happened"); deterministic surplus rule;
  floor-30 disclosed-shortfall fallback that never dilutes shape/skew; thin-genuine-row + easier-
  negatives disclosures.
- **[A-F1] CONFIRMED rule under-specified** → §2: re-probe evidence rules verbatim (non-2xx/
  VANISHED/bound-hit = ERROR, never CONFIRMED); CONFIRMED reachable from `TIMEOUT_ABSENT` + per-gate
  stratum reporting; scarcity binds on CONFIRMED.
- **[A-F2] The frozen FP bar cannot see timeout-gated FPs (structurally blind on un-traced SUTs)**
  → §3: a NEW pre-registered CONFIRMED-level FP bar (≤5% = ≤1/20 over ≥20 acked benign with the
  identical re-probe) alongside the frozen bar; double-PASS opens the window; observe-mode
  calibration (also A-F14).
- **[A-F3] Sampling strata unpinned = experimenter degree of freedom (worst case: headline-shaped
  sampling)** → §2c: strata = SUT × distinct defect-site, proportional, deterministic seed in the
  freeze row; NO tool-derived signal (trace exports/comparator outcomes) in sampling.
- **[A-F4] Flag-time trace export can fabricate the headline (unflushed batch exporter → artifact
  MISS)** → §2d: comparator export re-fetched at/after CONFIRM with a two-read span-count stability
  check; headline-eligible only on stability-checked exports; RAW-time snapshot supplementary.
- **[B-B2] The 2.75-A `mist-` marker grammar fails `b4_harness.py` BANNED_STRINGS at render time
  (after windows are burned; unrecoverable)** → §2: neutral `corpus-w<seq>-<12hex>` grammar,
  unit-tested against the banned list at P0 + a per-SUT pre-window bundle→render round-trip gate;
  human-neutral probe descriptors.
- **[B-B3] OTel traceparent adoption UNPROVEN (E2 precedent is TT/javaagent; Envoy→Node→Go is
  config-dependent)** → §2d: P1 pre-window CANARY gate + pinned fallback (session-uuid-keyed
  tight-window query, exactly-one-else-ERROR) + Jaeger memory-retention check + load-generator OFF
  pinned.

### Major — all folded

- **[C-M1] The async→underspecified rubric trap (the unmentioned killer of headline leg (b))** →
  §0.4: honest-prior paragraph names it; headline realistically needs a sync-acked path; P0
  docs-bundle async-bound check; feeds the TT-mandatory rescope.
- **[C-M6 + B-M7] TT optional was internally incoherent** → §6: **TT MANDATORY** + 2–3 additional
  TT triples (YAML config, chosen from sites without natural-provenance authored cases) + coverage
  restated per-endpoint (≥500/SUT, ≥100/endpoint, per-endpoint denominators). (B's
  conditional-trigger variant subsumed by C's stronger form; B's optional OTel cart triple kept as a
  priced optional rider.)
- **[C-M2] The T+5min re-probe is a stratum fingerprint** → §4.2: ONE observation cadence for ALL
  rater-facing strata; legacy calibration re-captured or excluded; cadence uniformity added to the
  tell-audit + P0 tests; re-probe NOT stripped (judgment-relevant).
- **[C-M3] M-yield stratum sequencing** → §0.3: "assembly-ready EXCEPT the Step-4 M-yield stratum";
  bold hold in the hand-over note (rating must not begin before the merge, or a separately
  pre-registered two-round protocol).
- **[C-M4] Worked examples unowned** → §4.4/P5: authored our-side on real calibration cases.
- **[C-M5 + A-F7] Claim drift + denominator ambiguity** → §0.5 pre-committed claim sentences
  (rule-of-three zero-finds; existential one-find); §5 pinned numerator/denominators + the quotable
  bound + the estimand string frozen at P0.
- **[A-F6 + C-m5] W3 blind spot into the estimand + session scope** → §2/§5: one session per
  window; quarantine triage rule (inspectable, never S3-eligible); estimand string carries
  "+W3-quarantine-conditioned"; always-lost-unhuntable disclosed (exactly the OTel-flagship class).
- **[A-F8] Dedup silently biases precision** → §2b: precision BOTH ways (new-sites-only rater-
  labeled; all-CONFIRMED with rediscoveries scored by known labels) + rediscovery counts by class.
- **[A-F9 + C-m2] Recall-on-S1 qualifier conditions** → §5: per-case MEASURED/ANALYTIC marking;
  analytic under actual observe semantics (always-on permanent loss ⇒ analytic recall 0/quarantined,
  stated honestly); `specified`-grade never pooled; a MEASURED leg per SUT scheduled strictly after
  the counted window, distinct markers, excluded from denominators.
- **[B-M4] Re-probe path visibility/predicate drift** → §2: public static probe-evaluation accessor
  on `DataIntegrityRuntime` (visibility-only, precedent installHttpOverride); same transport
  instance; markerSupplier re-point/restore; between-journeys scheduling; transcript retention.
- **[B-M5] No mid-window FP-storm breaker** → §2: ≥5-consecutive-RAW / trailing-50 >20% breaker +
  pre-registered pause/repair/resume semantics + window-log accounting + infrastructure-fault flag
  exclusion rule.
- **[B-M6] Sidecar pins** → §4.3: per-case t_rel rebase; one record-scope shape for every producer;
  producer-side credential redaction; separate trace file; Java-raw-records + thin Python assembler
  reusing capture_driver conventions; `producer: "wildflag-bundle"`; replay pointers never
  rater-facing (A-F13).

### Minor — all folded

A-F11 (ERROR-record bucket + `readback_bound` growth watch → §2 knobs/§4) · A-F12+C-m1 (κ withheld
at n<10 + |S3|=0 degenerate branch → §0.5) · A-F14 (observe-mode calibration + single-thread pin →
§2/§3) · A-F15 (wording: "present-at-re-probe (delayed-beyond-cap)"; "under the pinned workload" →
§2/§5) · B-m8 (RAW predicate in runtime terms → §2) · B-m9 (numeric knobs → §2) · B-m10 (TT JWT
refresh; TeaStore user rotation + bound watch → §2) · B-m11 (wall-clock stated; pre-window
flagd/PF checks → §2/§7) · C-m4 (rater-time table + packet 15–45 h vs 22–68 h inconsistency flag →
§4.5) · C-m6 (calibration bundles seed the top-up pool, shape rule applies → §3).

## Net

Rev 2 folds every blocking + major + minor finding. The three deepest structural fixes: the
provenance-scoped dedup rule (the rev-1 rule would have vetoed the headline by construction), the
CONFIRMED-level FP bar (the frozen bar was structurally blind exactly where the hunt runs), and the
honest-prior/TT-mandatory rescope (the headline's only plausible surface is a sync path on the
known-buggy SUT; the async rubric trap is now stated before the data exist).

## Rev-2 confirmation pass — UNANIMOUS ACCEPT (gate satisfied)

| reviewer | rev-2 verdict | residual |
|---|---|---|
| A (oracle-soundness) | **ACCEPT — "no residual blocking"** (all 15 findings verified against the text) | 4 minors (R1–R4) |
| B (engineering) | **ACCEPT — "executable as written"** (all 3 blocking + 4 majors verified; marker grammar double-checked vs whitelist AND banned list) | 3 minors (r1–r3) |
| C (hostile-PC) | **ACCEPT — "worth executing as scoped"** (all conditions met, several stronger than specified; re-attacked the new rev-2 machinery and it held) | 2 minors (1–2) |

**rev 2.1 folds all 9 confirmation minors** (marked [r2.1] in the plan): A-R1 RAW gate ∈
{TIMEOUT_ABSENT, OBSERVED_COMPLETE_ABSENT} · A-R2+C-1 list-driven breaker exclusion (on-list
environment artifacts excluded-but-reported; off-list — incl. SUT-endogenous degradation and the
spontaneous-kafka lottery scenario — stays S3-eligible, surfaced to P6) · A-R3 journey-supplied keys
for every hunted triple (`freshValueLike`'s `mist-…` values would trip the render gate) · A-R4
measured-recall leg schedules pinned per S1 case (always-on = recall-under-quarantine, expected 0;
mixed = gate-open recall) · B-r1 root-cause note sealed-side only ("injector" is a banned string) ·
B-r2 re-probe scheduling W3-independent (every acked-absent write; classification at window end) ·
B-r3 legacy S1-positive cadence re-captures ride the measured-recall leg (exclusion = fallback) ·
C-2 corrected worst-case calibration arithmetic (benign ≈ 42–43 at |S3|=0; floor-30 branch
near-certain there, reported with its power consequence).

**GATE SATISFIED (the standing rule: execution only after all reviewers accept — met). NEXT: P0**
(the freeze §6 "Step-5-as-amended" row FIRST, then runner/accessor/assembler code + journey scripts
+ unit tests; no calibration or counted window before the freeze row is committed).
