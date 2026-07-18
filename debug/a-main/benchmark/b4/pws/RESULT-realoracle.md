# RESULT — REAL tool oracle EXECUTED on MIST-reached executions (surrogate==real closed by measurement)

**Date:** 2026-07-18 · The 3-cold A-venue recheck's UNANIMOUS top-ROI item (A2 "load-bearing",
B item-1, C item-A), executed. Evidence: `realoracle/realoracle-cell.json` + `realoracle/{control,lost}.log`.

## Method (byte-replay; the oracle axis holds the RESPONSE constant)
A local byte-replay server serves the RECORDED responses (status + content-type + body) from the
meshsever capture sidecars (`captures/oteldemo-checkout-meshsever-{control,lost}/sidecar.json`) for
POST /cart + POST /checkout under the committed clean-room OTel spec; **Schemathesis v4.23.0 —
`st run --checks all`, the tool's ENTIRE response-oracle suite, the same binary/version as the PWS
live arm — runs against it** (10 examples/op, seed 7, ops restricted to the two recorded ones).
The lost leg's checkout response is the GENUINE acknowledged-but-LOST 200 (orderId + tracking +
cost, order durably absent, MIST read-back FIRE on that very execution).

## CORRECTION (v1 was vacuous; corrected same day)
The first run keyed the replay at `/api/cart|/api/checkout` while `st --url` targets
spec-RELATIVE paths under `basePath: /api` — every request 404'd and **no recorded 200 was ever
judged** (caught cold by sufficiency-reviewer 2 from the logs). The replay keying was corrected
(spec-relative ops) and the experiment re-run; everything below is the CORRECTED run, in which
the logs show the recorded acks served and judged (`[200] OK` exchanges present in both legs).

## Result (corrected run): MEASURED MISS, zero leg discrimination
- Failure-title sets IDENTICAL on control vs lost (`leg_invariant: true`): no check the real tool
  has separates the acknowledged-but-LOST leg from the landed leg.
- **Response-side checks PASS on every recorded 200 ack** (server-error / undocumented-status /
  content-type / response-schema — no response-conformance failure cites a 200): the genuine
  acked-but-lost checkout ack is conformance-clean to the real tool.
- The one failure kind citing a 200 is `API accepted schema-violating request` — a REQUEST-side
  validity check (the replay answers the recorded ack regardless of the generated body; the PWS
  LIVE run flagged the same gap against the live SUT). Identical on both legs; not a response
  oracle; no data-integrity signal. Remaining titles are auxiliary probe edges (a 404 on an
  unrecorded probe path; http.server's 501 to TRACE), leg-invariant.
- Zero data-integrity/durable-state findings — the tool has no such check to run (consistent with
  the PWS live arm's taxonomy: conformance-only).

## What this closes
The E2 surrogate==real claim is no longer a grep argument: the REAL tool's oracle CODE, executed
over the REAL recorded acks of a MIST-reached masked-loss execution, produces no flag and no
control-vs-fault difference, while MIST's read-back differential FIREs on the same execution.
The residual gap named by reviewer B (`db_span_presence` surrogate unvalidated) is out of this
tool's scope by construction (Schemathesis consumes no traces) — the trace-arm surrogates remain
labeled surrogates, disclosed.

## Caveat (disclosed)
Request bodies on the replayed ops are tool-generated (the response is what an oracle judges; the
replay holds it constant). This is the standard oracle-axis isolation, and the PWS LIVE run
already showed the same tool generating-and-judging end-to-end with the same taxonomy.
