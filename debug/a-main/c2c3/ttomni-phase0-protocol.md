# TT-OMNIBUS Phase 0 — frozen run protocols (plan rev 2.1 §3 Phase 0) — 2026-07-14

Pinned BEFORE any tenant is touched. Companions: `wave-tt-omnibus-plan.md` rev 2.1 (CLEARED,
confirmation pass unanimous), `REVIEW-TTOMNI-PLAN-RECONCILIATION.md`.

## (a) Leg-1 protocol — the traced MIST live-run (E2 MIST-column provenance)

- **Sites:** `TT-cancel-refund-fabricatedack-001`'s site (cancel → refund; fork branch
  MIST-trainticket, fabricated-ack `f57102e6` lineage) + the clean twin
  (`TT-cancel-refund-clean-001`'s flow). N≥5 per leg, CONTROL-FIRST. Per-run cap 30 min.
- **Instrumentation (reuse the traced-capture-wave recipe verbatim):** OTel javaagent
  **1.33.6 (pinned + sha, hostPath+JTO mount, canary-first on the 8u111 base)** on the
  cancel-path services (ts-ui-dashboard entry + ts-cancel/ts-order/ts-inside-payment +
  auth/user as per the traced wave's 7-service set); Jaeger v2 exports via
  `/jaeger/api/traces` (µs timestamps, immediate-export rule); **exactly-one-trace rule**
  per probe.
- **Client-traceparent wiring (A'-R1, the load-bearing pin):** the MIST observe-path run
  MUST inject a W3C `traceparent` on the entry request so the step carries a non-null
  traceId — without it the trace-complete tier is unreachable regardless of
  `jaeger.base.url`. Mechanics = the traced-capture driver's traceparent injection
  (already exercised at the S3 OTel window: "client W3C traceparent injected").
- **Modes per leg:** (1) observe mode WITH `jaeger.base.url` (the NEW datum: does
  `OBSERVED_COMPLETE_ABSENT` fire on the fabricated-ack leg — reported EITHER WAY, no
  tuning, no re-runs to flip); (2) paired mode (live provenance; trace-gate-independent by
  source — stated as such).
- **Ground truth:** direct DB/API reads (never MIST). **Markers:** per-run salt (TT admin
  convention); 800 ms journey pacing. **Evidence:** per-run Jaeger export + MIST verdict
  log + the run's config, bundled per leg.
- **Cells written:** the E2 table's MIST column (live-run provenance) for the traced pair;
  `mist_trace_shape_oracle` STAYS traced-but-not-run (DEFER: learned `TraceShapeOracle`
  needs training/wiring; Branch-B). NO existing label/cell touched.
- **Claim-language correction (RESULT deliverable):** re-word the standing "discrimination
  OWED at 2.5/E2" (freeze note + memory) — S3 closed the natural-discriminator question;
  E2's obligation = matched-recall vs comparators; this leg adds the MIST column.

## (b) M-yield TT leg — frozen conventions

- **Budget:** 1 h × **10 seeds**, LLM-off (disclosed). **Seeds (frozen):**
  `20260714 + i` for i = 0..9 → {20260714 … 20260723}.
- **Tool pin:** ONE MIST commit for ALL legs of this wave, recorded at Phase-2 start
  (= the `main_track` HEAD at window start; stamped per-run into every run log/report).
  Any mid-window MIST change ⇒ STOP (the §4 gate).
- **Target:** the UNMODIFIED upstream TT graph (fork torn down + teardown-verified before
  this leg). Spec = `evaluation/suts/trainticket/openapi/merged_openapi_spec.yaml`; conf =
  `evaluation/suts/trainticket/real-system-conf.yaml`; triples = the committed TT
  target-triples set.
- **Clustering (frozen pre-run):** equivalence class = **endpoint × fault-signature ×
  SUT**; fault-signature = the oracle's verdict class + the response-shape family (status
  code class + body-shape hash), computed by a committed script at close-out; 1
  representative per cluster + a 10% random audit sample (seed 20260714).
- **Deliverables (in-wave):** cluster table + representatives + audit sample + author-side
  upstream filings for genuine finds. **NO yield statistic** (rater-gated, Step 5).
  Stated prior (not target): low/zero flagged events expected (the S3 0/1514 datum).

## (c) Revival/teardown scripts

`debug/a-main/benchmark/b4/runners/ttomni/revive-tt-full.sh` (snapshot-driven full graph;
verified against the real `tt-replica-snapshot.txt` 3-column format — 47 ts-* Deployments +
StatefulSet nacos 2 / nacosdb-mysql 2 / tsdb-mysql 2; small batches of 6 with 90 s pauses;
nacos readiness → doubleWrite PUT → verify; crash-loop sweep; standing ui-dashboard PF) and
`teardown-tt.sh` (snapshot-then-scale-to-0; KEEP_INFRA switch). Run via WSL as script FILES.

## (d) E5 leg — the frozen OAT matrix (exact 4 configs × 5 seeds = 20 runs)

| config | oracle_mode | jaeger.base.url | quiescence set |
|---|---|---|---|
| C0 baseline | paired | unset | default (poll/timeout/settle as shipped) |
| C1 (A1) | observe | set | default |
| C2 (A2) | observe | unset | default |
| C3 (A3) | paired | unset | extended-cap variant (2× timeout, same poll) |

Seeds per config: {20260714 … 20260718}. Target = the S1 pair (fork by set-image only).
Per-run duration measured from leg-1 BEFORE the batch schedule is fixed; hard cap 4 d.
EXCLUDED by name (no config toggle): re-probe, per-triple value-delta.
(Note C1/C2 isolate the trace-gate effect within observe mode; C0↔C1 spans A1; C0↔C3
spans A3 — OAT around C0 with the observe-side pair sharing the A1 leg, 4 configs total.)
