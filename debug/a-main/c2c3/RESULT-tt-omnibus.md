# RESULT — Wave TT-OMNIBUS (traced MIST live-run + M-yield TT leg + E5 ablations) — DRAFT (leg-2/3 in flight)

**Status: DRAFT 2026-07-14 — leg 1 FINAL; leg 2 (M-yield batch) RUNNING (detached driver,
10×1h, ends ~03:33); leg 3 (E5) queued behind it; Phase-3 close-out pending.** Plan:
`wave-tt-omnibus-plan.md` rev 2.1 (rev-1 3-cold review 11-BLOCKING folded; confirmation
pass UNANIMOUS). Protocols: `ttomni-phase0-protocol.md`. MIST pin: the `main_track` tree at
`1c9e4df` (runner-only additions during the window; runtime/oracle untouched — verified by
the leg-1 commit diffs).

## Headline (leg 1, FINAL)

**The trace-gated defect tier was REACHED and FIRED for the first time in any run of
record.** On the revived full-graph TT (53 pods) with the cancel path instrumented (OTel
javaagent 1.33.6, the traced-capture recipe verbatim) and the fork's fabricated-ack toggled
at runtime:

- **Observe mode + `jaeger.base.url` + client-traceparent (the ONE wiring change —
  `TtOmniObserveLeg` passes the REAL trace-id to `afterWrite`, where the G3 harness passes
  null BY DESIGN):**
  | leg | acked | read-back | gate |
  |---|---|---|---|
  | control ×5 | 5/5 200/{1} | refund PRESENT (value-delta moved) | `OBSERVED_PRESENT` 5/5 @1 poll |
  | fault ×5 | 5/5 200/{1,"Success."} | ABSENT at the 20-21-poll cap | **`OBSERVED_COMPLETE_ABSENT` 5/5** |
  The pre-registered either-way measurement came out **FIRES**; the control stayed clean
  (no FP). Descriptive trace-shape datum: control traces 31 spans vs fault 24 — the missing
  spans are the never-executed persistence work.
- **Paired rerun (the e2-run.sh matrix verbatim, live provenance):** MIST differential
  value-delta **FIRE 5/5** vs the frozen response-contract comparator **MISS 5/5** (control
  flagged=false, fault flagged=false) — the E2 table's MIST column now carries live-tool
  provenance on the traced deploy. (The paired verdict is trace-gate-independent BY SOURCE;
  stated as such, not sold as a Jaeger effect.)

Evidence bundle: `debug/a-main/benchmark/b4/ttomni/leg1/` (per-run Jaeger exports under the
immediate-export rule; paired logs; `LEG1-REPORT.md`).

## The claim-language CORRECTION (a deliverable of this wave — freeze/memory wording)

The standing constraint "MIST's DISCRIMINATION claim remains PRE-REGISTERED + UNMEASURED by
any real traced MIST run — OWED at 2.5/E2" was an OVER-CLAIMED framing, corrected as
follows (per the plan-review A-F2 finding and the rev-2.1 pre-registration):

1. The **natural-discriminator** question ("does a natural masked loss exist that read-back
   catches and trace oracles miss?") belonged to S3 and CLOSED as the scarcity finding
   (0 CONFIRMED / N=1514); no synthetic-fork site can produce a *natural* discrimination
   headline, and none is claimed.
2. What E2 actually owes is **matched-recall MIST-vs-comparators on the same cases**: the
   comparator cells were banked at the traced-capture wave (`cd275c9`); THIS wave adds the
   **MIST column with live-run provenance** (both modes), completing that table for the
   traced pair.
3. The NEW datum this wave contributes is the **observe-mode trace-gated tier
   reachability measurement** (fires on the fabricated-ack loss; abstains on nothing it
   shouldn't — control clean), which upgrades the R1d-era characterization: observe-mode is
   WARN-only *when trace evidence is absent*; with trace completeness established, it
   escalates to the defect tier as designed.
4. `mist_trace_shape_oracle` cells remain **traced-but-not-run (Branch-B, DEFERRED)** — the
   learned `TraceShapeOracle` needs training/wiring; a feasibility choice (the 2026-07-10
   gate amendment permits the work), not a gate prohibition.

## Leg 2 — M-yield TT (Step 4, PARTIAL by design) — RUNNING

Fork TORN DOWN first (inside-payment image 1.0.5 → 1.0.0; fork-absence evidence: the
faultmode route flipped 200 → 403 — recorded as-is; the JTO/OTEL env survives set-image).
Detached driver `myield-driver.ps1` launched 17:33: 10 seeds {20260714..20260723} × 1 h
wall budget, FULL MIST pipeline (`MistMain`, demo profile + appended overrides:
**LLM-off**, base.url = self-healing PF 8080, **canonical `target-triples.yaml` registry**
[2 shipped triples: adminroute-create, adminbasic-contacts-create] — confirmed armed in the
seed-1 log), per-seed `experiment.name=ttomni_myield_s<seed>`. Pilot (8 min bounded)
validated the pipeline end-to-end (generation → execution progress bars; observe armed).

- **[PLACEHOLDER: per-seed completion table — EXIT vs KILLED-at-budget, per-seed test
  counts, flagged-event counts]**
- **[PLACEHOLDER: clustering output — equivalence classes (endpoint × fault-signature ×
  SUT), 1 representative/cluster + 10% audit sample (seed 20260714), upstream filings if
  any genuine finds]**
- **NO yield statistic is reported from this wave** (rater-gated; computes at Step 5).
  Stated prior (not target): the S3 0/1514 datum predicts low/zero flagged events on
  upstream TT.
- Step 4 folds **◐ PARTIAL**: the M-YIELD-COMPLETION follow-up window (named in the plan)
  owns TeaStore/OTel 2.75 enablement (+ `mist_authoring` cost recording) + SS/BI/Boutique
  thin legs.

## Leg 3 — E5 ablations (exact-4 OAT × 5 reps) — QUEUED

Driver `e5-driver.ps1` ready: fork back by set-image 1.0.5 → C0 paired/default · C1
observe+jaeger · C2 observe−jaeger · C3 paired/`mst.oracle.dataintegrity.timeout.ms=20000`;
30-min per-run caps; per-config logs.

- **[PLACEHOLDER: per-config × rep outcome table + per-axis deltas]**

## Phase 3 — close-out — PENDING

Instrumentation restore (`instrument-leg1.sh restore` × 4 services incl. the canary
ts-contacts); `teardown-tt.sh` (snapshot → services to 0; infra fate recorded); checklist
folds (Step 4 ◐; 2.5.1 stays ◐ with the runbook banked; 2.5.4 row refresh if needed; the
stale Standing-constraints footer refresh); freeze §6 EXECUTED row; FILE_INDEX/memory;
**post-hoc 3-cold review (the DoD gate)**.

## Budgets vs actuals (the §5 table; >1.5× rule)

| leg | baseline | actual |
|---|---|---|
| Phase 0 prep | ≤1 d | ~2 h ✅ |
| Phase 1 close-out | ≤0.5 d | ~25 min ✅ |
| Phase 2(i) revival | ≤0.5 d | ~70 min (incl. one predicted flap window + the staged-runner pivot) ✅ |
| Phase 2(ii) leg 1 | ≤1 d | ~2.5 h (incl. instrumentation + runner authoring) ✅ |
| Phase 2(iii) M-yield | 10 h driven + ≤0.5 d clustering | [RUNNING — 10 h wall by construction] |
| Phase 2(iv) E5 | ≤4 d | [QUEUED — projected ≈40 min] |
| Phase 3 close-out | ≤1 d | [PENDING] |

## Operational field lessons (recorded for the runbook)

1. Detached-inside-WSL processes die silently minutes after their `wsl.exe` client exits —
   long runs need either staged FOREGROUND-attached calls (`revive-stage.sh`) or a DETACHED
   WINDOWS-side process (`Start-Process` drivers). 2. `svc/nacos` port-forward fed a dead
   target while both pods answered readiness directly → pod-level PF. 3. The 0x8007274c WSL
   flap window hit at infra-JVM boot exactly as 3bb8209 predicts and self-recovered (no
   `wsl --shutdown`, small batches held). 4. g3 runners require the TrainTicketStimulus.main
   reader-auth block (fresh reader + `per_jvm` + `ensureReady`), else the runtime toggle
   403s. 5. The rev-2 batch background commands were repeatedly killed → SHORT foreground
   calls + detached Windows drivers became the wave's execution discipline.
