# RESULT — E-ANOM (executed) — an UNEXPECTED, MATERIAL finding that NARROWS the contribution claim

**Date:** 2026-07-19 · Status: EXECUTED; result is NOT what the plan predicted and is NOT folded
into the headline matched-recall table pending a USER positioning decision. Harness:
`scoring/run_anomaly_arm.py`; raw `scoring/verdicts/traceanomaly.detail.json`.

## What was expected vs what happened
The plan (and reviewer-2's capture audit) predicted a CLEAN MEASURED NULL — a trace-anomaly
detector flags 0/6 trace-evaluable positives (masked losses leave the trace structurally normal) +
reproduces the bookinfo FP. **That prediction was WRONG.** A control-vs-fault structural differ
(learn-normal-from-control, flag deviation) flagged **5/6** trace-evaluable positives, because
several masked-lost writes leave a REAL trace signature: the skipped persistence call means the
downstream edge is ABSENT in the fault leg.

## The honest per-case classification (signal quality matters)
| case | E-ANOM | signal |
|---|---|---|
| TT-adminroute-lostwrite | flag | **STRONG real** — 15→3 spans, the entire ts-route-service persist subtree vanishes |
| TT-adminbasic-lostwrite | flag | **STRONG real** — 13→2 spans, the ts-contacts persist edge vanishes |
| oteldemo-checkout-lost | flag | **WEAK real** — 50→47 spans, 1 missing edge of 22 (a real detector's threshold may not fire) |
| sockshop-swallowed-enqueue | flag | **WEAK real** — 36→32 spans, 2 missing edges of 12 |
| TT-cancel-refund-fabricatedack | flag | **NOISE** — flagged on a NacosWatch background lambda op; the edge structure is IDENTICAL (the fabricated ack leaves the drawback span present) |
| TT-createaccount-agreement | no_flag | MISS — trace structurally identical |
| bookinfo-ratings-benign (neg) | flag | FP — designed 503 degradation (same FP mist_trace_shape hits) |

⇒ Honest tally: 2 STRONG + 2 WEAK real catches, 1 noise catch, 1 miss, 1 benign FP. Even
discounting the weak/noise ones, **a trace differ genuinely catches the SKIPPED-CALL variety of
masked loss when trace instrumentation and a paired control trace are present.**

## Why this MATTERS (it kills the broad claim, preserves a narrow one)
The paper's implied "trace-based oracles structurally CANNOT see this class" is now **falsified in
the instrumented + paired setting** — where the loss is a skipped downstream call, the missing edge
IS a trace signal an anomaly detector catches. The contribution claim must NARROW to what survives,
which is still real:

**MIST's read-back oracle differentiates on the OPERATING POINT, not on "no one else can detect":**
1. **Black-box / no instrumentation** — E-ANOM is `not_evaluable` on 26/33 cases (trace-
   uninstrumented incl. ALL TeaStore + all sidecar-only captures); MIST's durable read-back works
   there. This is the real moat and E-ANOM CONFIRMS it (it literally cannot run on those cases).
2. **Single-execution / no paired baseline** — E-ANOM needs BOTH a control and a fault trace of
   comparable transactions to diff; MIST's shipped OBSERVE mode re-reads durable state on ONE
   execution, no paired trace needed.
3. **Catches the CALL-HAPPENS-DATA-LOST variety** — the fabricated-ack case (the call runs, the
   span is present, the money just isn't durably persisted) has NO missing edge → E-ANOM misses it
   via real signal (only a noise fire); MIST's read-back catches it. Value-corruption / silent-
   commit-failure losses leave no structural trace signature.

## Disposition
- The `traceanomaly` arm is NOT folded into the headline matched-recall table yet — doing so would
  silently change the central comparison. The measured verdicts + detail are committed as an
  honest artifact; the positioning is a USER decision (below).
- The experiment did its job: it converted an ASSUMPTION into a MEASUREMENT, and the measurement is
  partly adverse. Burying it would be caught at review; disclosing it sharpens the honest claim.

## The decision this surfaces for the USER
The central differentiation must move from "trace oracles can't see masked loss" to the narrower,
defensible **"MIST detects masked loss BLACK-BOX (no trace instrumentation), SINGLE-EXECUTION (no
paired baseline), across the skipped-call AND the silent-persist-failure varieties — where trace
anomaly detection needs instrumentation + a paired baseline and only catches the skipped-call
variety."** This is a real but MODEST operating-point contribution. Options:
(a) adopt the narrowed claim + keep E-ANOM as an honest measured competitor arm (recommended:
    honest, and the black-box/observe-mode moat is genuine);
(b) restrict the corpus/claim to the trace-uninstrumented + value-corruption cases where MIST is
    uniquely applicable (smaller but cleaner);
(c) reconsider whether the operating-point contribution is strong enough for the top-venue attempt
    at all.
