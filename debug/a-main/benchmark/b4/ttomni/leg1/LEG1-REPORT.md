# TT-OMNIBUS leg 1 — traced MIST live-run (E2 MIST-column provenance) — 2026-07-14

Plan: wave-tt-omnibus-plan.md rev 2.1 §1-1 (confirmation-pass unanimous); frozen protocol
ttomni-phase0-protocol.md (a). MIST commit pin for all runs: the main_track tree at
be7bdb9 (runner committed; runtime/oracle untouched — the runner passes the REAL client
trace-id where the G3 harness passes null BY DESIGN).

Deploy: full-graph TT (53 pods) revived per revive-stage.sh; cancel path
(ts-cancel/ts-order/ts-inside-payment) instrumented w/ opentelemetry-javaagent 1.33.6
(hostPath+JTO, the traced-capture recipe verbatim; banners verified, 0 restarts); Jaeger
istio-system (badger emptyDir); fork codewisdom/ts-inside-payment-service:1.0.5 with the
RUNTIME fabricatedack toggle (no restarts); doubleWriteEnabled=false verified.

## Measurement A — OBSERVE mode with the trace gate (THE new datum)

Runner: io.mist.cli.g3.TtOmniObserveLeg; -Djaeger.base.url=http://localhost:16686/jaeger/api;
client W3C traceparent injected per write (TrainTicketStimulus.cancel); N=5/leg,
control-first; per-run Jaeger export immediately (immediate-export rule).

| leg | acked | read-back | gate | polls |
|---|---|---|---|---|
| control ×5 | 5/5 HTTP 200/{1} | refund PRESENT (value-delta moved) | **OBSERVED_PRESENT 5/5** | 1 |
| fault (fabricatedack) ×5 | 5/5 HTTP 200/{1,"Success."} | ABSENT at cap | **OBSERVED_COMPLETE_ABSENT 5/5** | 20-21 |

**The trace-gated defect tier (DataIntegrityRuntime L736-773) was REACHED and FIRED — the first
controlled, pre-registered either-way measurement of it on this masked-loss site in the c2c3
benchmark record [scope corrected post-review: the tier itself fired 126× in the G1-era
adminroute pairing run of 2026-07-02, trace-gated since 696a2fe]** — traceComplete(traceId) confirmed each fault write's
trace complete while the durable refund stayed absent, escalating past the WARN-only
TIMEOUT_ABSENT. The pre-registered either-way measurement came out FIRES; control stayed
clean (no FP). Descriptive trace-shape datum: control traces 31 spans vs fault 24 (the
missing spans = the never-executed persistence work). Ops notes: transient PF connection
resets during run-2's traceComplete polling (elapsedMs 73154; gate resolved correctly);
toggle verified cleared after the leg (faultmode none).

## Measurement B — PAIRED rerun on the traced deploy (live provenance)

e2-run.sh -D matrix verbatim (entry TrainTicketStimulus.main, strata=constructed), N=5:

| run | MIST B2 (differential value-delta) | comparator (frozen response contract) |
|---|---|---|
| 1-5 | **FIRE 5/5** | control flagged=false, fault flagged=false → **MISSED 5/5** |

Logs: paired-run{1..5}.log. The paired verdict is trace-gate-independent BY SOURCE
(PairedFaultExecutor — stated per the plan, not sold as a Jaeger effect).

## Evidence
trace-control-{1..5}-<traceId>.json (31 spans each) · trace-fault-{1..5}-<traceId>.json
(24 spans each) · paired-run{1..5}.log · this report. Cells + the claim-language
correction land in RESULT-tt-omnibus.md at Phase 3.
