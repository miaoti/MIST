# S3 P1 — OTel-Demo window RESULT (intermediate; feeds the P6 aggregate)

Plan `s3-wildhunt-plan.md` rev 2.1 §7 P1. Pre-registration: freeze §6 "Step-5-as-amended"
(2026-07-13). MIST code pin: `mist_commit 5802fa8` (engine + reProbe accessor + traceId fix).
Artifacts: `s3/window-oteldemo/{window-log.json, ledger.json, flags/, sidecars/}`.

## Environment + pre-window gates (all GREEN)

- **Revive:** OTel tenant scaled 0→1 (20 deployments, all Ready in ~20s, warm image cache);
  **load-generator ABSENT by construction** (not a deployment) → the pinned "load-gen OFF" holds.
- **flagd env guard:** all 15 flags `defaultVariant=off` (value false/0) — frozen defaults, no active
  fault flag (cartFailure / kafkaQueueProblems / paymentFailure / … all off).
- **traceparent adoption CANARY: CONFIRMED** — a client-generated W3C trace id was found in Jaeger
  with 57 spans (the SUT roots the whole trace on our id). The D1 compensating measure (per-flag
  trace export for the frozen comparator) is viable by-id; **no session-uuid fallback needed.**
  Jaeger API path confirmed `/jaeger/ui/api/traces/<id>` (matches the runner).
- **Read-back target:** the canary marker landed in `accounting.shipping` (1/1; DB empty before →
  clean baseline). Healthy async lag is SUB-SECOND (marker present before the checkout response
  returns), so read-backs resolve on the first poll.
- **§3 double-bar FP calibration (20 benign writes, observe mode, identical re-probe):**
  **20/20 present-at-cap, 0 CONFIRMED (0/20 ≤ 1/20)**, 0 false candidates → both bars PASS.

## Counted window (500 acked writes)

| metric | value |
|--------|-------|
| acked bound-triple writes (denominator a) | **500** |
| bound endpoints (denominator b) | 1 (`POST /api/checkout`) |
| **CONFIRMED flags** | **0** |
| present-at-cap | 499 |
| raw-delayed (absent at 25 s cap, present at T+5 min re-probe) | 1 |
| failed_journeys (operational, excluded) | 0 |
| write_path_fraction | 0.5 (1 read + 1 write / journey) |
| breaker events | 0 |

**Result: 0 CONFIRMED wild flags in 500 acked writes on OTel checkout** — the pre-registered
SCARCITY outcome (honest prior C-M1: a natural acked-lost find on OTel ≈ 0; every acked-lost behavior
ever captured here required an injected fault). DB row count 523 = 23 pre-window baseline + 500,
exact (no lost/duplicate writes at the DB).

## The 1 raw-delayed case (w120) — a real degradation-shaped top-up

`corpus-w121-3b5f90840d2c`, gate `TIMEOUT_ABSENT`, re-probe `PRESENT`. The shipping row was absent at
the 25 s cap (27.7 s observed) but **present at the T+5 min re-probe** (~5.5 min) — a genuine
bounded-eventual-consistency case that recovered. Assembled to `sidecars/w120-sidecar.json` and
**round-tripped through the real `b4_harness.render`: no leak, 3 observations render** (baseline `[]`
→ at-cap `[]` → re-probe `[{street_address: …}]`). This is the FIRST full-pipeline validation on
LIVE window output (Java runner → flag bundle → Python assembler → sidecar → b4 blind case) and a
valid §4 degradation-shaped benign top-up case (rater should judge benign/underspecified: the order
DID eventually persist).

## Measured-recall disposition (freeze pin 12; §5)

- **Always-on schedule → ANALYTIC recall-under-quarantine = 0** (per §5: an always-on permanent
  kafka-down loss has NO benign same-triple sibling in-session, so the W3 gate never opens and every
  absence is quarantined — MIST's self-protection, not a miss). Zero-risk, no injection needed.
- **Cross-mode cross-check:** wave 2.75-A already MEASURED OTel recall = **FIRE 5/5** (paired mode,
  `RESULT-oteldemo-2.75a.md`) on the same kafka-scale-0 loss.
- **Mixed on/off (gate-open) MEASURED observe leg = DEFERRED/optional** — it requires toggling kafka
  mid-session (down→up) to open the W3 gate, which risks the rdkafka-client wedge (recovery = restart
  checkout+accounting+fraud). Observe-mode MEASURED recall is demonstrated on the low-risk TeaStore
  (maintenance toggle) and TT (fabricated-ack toggle) legs; the OTel mixed leg is an optional
  end-of-execution add-on if cluster stability permits. **Disclosed, not silently skipped.**

## Carry-forward to P6

- OTel contributes **0 CONFIRMED** to the scarcity numerator and **500** to denominator (a).
- 1 degradation-shaped benign top-up case (w120) for the §4 rater calibration mix.
- Next: scale OTel to 0 → P2 TeaStore (window + recall) → P3 TT (MANDATORY; window + recall).
