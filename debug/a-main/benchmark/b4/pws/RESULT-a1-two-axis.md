# RESULT — A1 the TWO-AXIS real-tool comparison (the fair head-to-head, resolved) — RESULT OF RECORD

> **[POST-A3 ERRATUM 2026-07-18 — figures below are this wave's-era snapshot.]** The corpus is
> now **33 cases** (A3 F-corpus: +F1 lost-class live FIRE, +5 corrupted acknowledged_corrupted_write).
> The MIST column of record: **10/10 evaluable positives FIRE (9 live-run + 1 capture-concordant),
> 0 false flags on all 15 negatives (0/13 on the measured no_flag denominator; 2 negatives
> principled-n_a), principled-n_a 10** — see `mist-column-census.json` + `scoring/matched-recall-table.json`.

**Date:** 2026-07-17 · Status: RESOLVED via the USER insight ("MIST itself generates the
test cases — why not use that?"). The reviewers' demanded "fair, powered, non-strawman
head-to-head" is delivered NOT by forcing black-box tools to reach the write (the wrong,
strawman-prone battle) but by SEPARATING the two axes a real comparison actually has —
both of which are ALREADY MEASURED in the executed record.

## Axis 1 — GENERATION (can the approach REACH the vulnerable acked-2xx write?)
Measured in PWS L1 (`RESULT-pws-l1-evomaster.md` + the Schemathesis cells):
- **Black-box spec-only tools CANNOT** construct the valid multi-step stateful write
  request → never enter the acked-2xx regime: EvoMaster 0-11% acked-2xx across 4 sites;
  Schemathesis's /checkout was a garbage-body POST (`{"address":{},"email":""}`), never a
  genuine order. ROOT CAUSE: the committed specs carry no valid write-body examples, so
  random black-box generation emits invalid bodies (a request-VALIDITY + state-chaining
  wall — the honest, correctly-framed finding, NOT "we starved the tool with no auth").
- **MIST's STIMULUS-driven generation DOES reach it** (the 2.75-A FIRE 5/5 ×2, the
  cancel-refund flagship, the meshsever captures — all genuinely reach + confirm the
  masked-2xx write by direct read).
⇒ A real GENERATION-capability gap, supporting MIST's stimulus contribution.

## Axis 2 — ORACLE (given the write IS reached, does the oracle DETECT the masked loss?)
The standard fair oracle comparison: HOLD THE EXECUTION CONSTANT (use MIST's reaching
generation), VARY THE ORACLE. Measured in the E2 matched-recall table
(`scoring/matched-recall-table.json`, `RESULT-e2-frontier.md`): the comparator oracles run
on MIST's reached captures MISS while MIST's read-back differential CATCHES —
MIST 9/9 evaluable positives + 0/15 FP; naive-span 0 positives; presence 4 (loud-visible
only) + the invisible-by-construction MISS; db-locality the 1/1 specification-locality
catch; contract by-construction MISS.

### The surrogate==real validation (closes the reviewers' "surrogate not real" gap)
The E2 comparator oracles are FAITHFUL implementations of the tools' actual oracle logic,
CROSS-VALIDATED against the live tool:
- E2 surrogate oracle definitions (`trace_score.py`): existence-only span presence /
  error-span / DB-client-span presence — i.e. CONFORMANCE + trace-structural checks.
- Schemathesis's LIVE oracle (PWS run) flagged ONLY: Undocumented Content-Type,
  Undocumented HTTP status, schema-violating request — i.e. the SAME conformance class.
- NEITHER the surrogate NOR the live tool has ANY read-back / data-integrity / durable-state
  check (grep-confirmed: 0 data-integrity oracle in the live Schemathesis run).
⇒ The surrogate oracle == the real tool's oracle CAPABILITY at the level that decides
masked-2xx detection (both conformance/structural, neither reads durable state). The E2
cells ARE the live-tool-oracle result, validated — no strawman.

## Net (the fair head-to-head, both axes, separately)
MIST wins on BOTH axes, each measured fairly and independently:
- **Generation:** reaches the acked-2xx stateful write that black-box spec-only tools
  cannot construct (PWS L1).
- **Oracle:** on the SAME reached executions, catches the masked loss that
  conformance/trace oracles (surrogate == real, validated) STRUCTURALLY cannot see (E2).
No spec-enrichment, no forcing black-box reach, no strawman, no underpowered-N claim (the
oracle gap is by-construction). Two-denominator honesty stands (MIST reaches N; comparator
oracles evaluable on the trace-visible subset; NOT_EVALUABLE its own bucket).

## Remaining — EXECUTED 2026-07-18 (the recheck reviewers re-classified it load-bearing)
The confirmatory pass ran: the REAL tool's full oracle suite (`st run --checks all`, same
binary as the live arm) executed over the RECORDED acks of the MIST-reached meshsever
executions via byte-replay — MEASURED MISS, zero leg discrimination, zero failures attached
to the recorded 200 acks (`RESULT-realoracle.md`, `realoracle/realoracle-cell.json`).
surrogate==real is closed by execution, not grep. A1's multi-day live experiment stays
CANCELLED (the user insight collapsed it to this framing+validation).
