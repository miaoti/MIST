# SUT-2 benign FP probe result — 3-cold-review reconciliation

Result under review: `g3-sut2-fp-probe-result.md` (+ `g3-sut2-fp-probe-report.json`,
`g3-sut2-fp-probe-records.log`), commit `30745cd`. Headline: **0 FP / 1200 acked benign writes,
gate 100 % resolved, bar v2 PASS, interval [0,0]** on Sock Shop SS-B (addresses + cards).

Three independent cold reviewers, no shared context. Each independently recounted the 1200 raw
records, read the bar-v2 source (`PairedFaultExecutor.fpProbeJson`), traced the membership/gate
logic + HAL unit tests, verified the jar/commit provenance, and checked for overclaim.

## Verdicts (all three — headline STANDS)
- **Reviewer A: ACCEPT-WITH-FIXES** — recount exact; FP=0 genuine (3 lines of evidence); no BLOCKING/MAJOR.
- **Reviewer B: ACCEPT-WITH-FIXES** — recount exact; FP=0 genuine (4 lines of evidence); no BLOCKING/MAJOR.
- **Reviewer C: ACCEPT-WITH-FIXES** — recount exact; FP=0 genuine (3 lines of evidence); no BLOCKING/MAJOR.

All three independent recounts reconcile with the JSON report **exactly**: 1200 acked, 0
`X-present=false`, 1200 `OBSERVED_PRESENT`, 600/600 per triple, 30 iterations × 40 records, 1200
unique fresh keys, 0 error/degraded lines.

## The decisive question — is FP=0 genuine or a vacuous always-present? GENUINE (all three)
The clinching evidence (independently reached by A, B, C): the runtime performed **1200 live pre-write
baseline `containsKey` checks that ALL returned FALSE** (`invalidRuns=0` forces `baselineContainedX=false`
for every record; MEMBERSHIP mode really computes it at `DataIntegrityRuntime.java:349`, not the
VALUE_DELTA hardcode). So on 1200 live, seeded, growing HAL collections the oracle read each fresh key
**ABSENT before the write and PRESENT after** — a real before/after delta ×1200. An always-present
oracle would have flagged every baseline (→ invalid runs), of which there are zero. Corroborated by the
HAL unit test's `assertFalse(... containsKey(halBody, un-submitted key))` and the pre-fix ABSENT storm.
(Disclosed residual, review A: there is no live SUT-2 record of the oracle FIRING on a genuinely-lost
write — branch β has no injector; that direction rests on TT + the live baseline-absent reads.)

## Bar-v2 PASS is machine-computed, pre-registration-compliant (all three)
`fpProbeJson`: guards `acked ≥ MIN_ACKED_FOR_BAR(20)`, `timeoutGatedFraction ≤ TIMEOUT_GATED_CAP(0.3)`,
`gateResolvedFraction ≥ GATE_RESOLVED_FLOOR(0.5)`, then `verdict = nonTimeoutFpRate ≤ SYNC_FP_BAR(0.05)
? PASS`. Constants match the prereg §0/§4 verbatim; the report's `syncFpBar` block matches the code
output field-for-field (not hand-set); the weak-PASS caveat (`fires>0 && observedGated==0`) correctly
does not apply (fires=0) → a STRONG zero. Per-SUT (not pooled-across-SUT) aggregation = C-pin-4 correct.

## Findings dispositions (all MINOR/NIT — none falsify the headline)

| # | Reviewers | Finding | Disposition |
|---|-----------|---------|-------------|
| 1 | C1 (A5/C2 related) | **The committed report JSON's `asyncDisclaimer` is a reused TrainTicket constant that is FALSE about Sock Shop** ("no broker-mediated async path exists on this SUT" — but Sock Shop runs RabbitMQ). True only of the measured SS-B path. `acceptThenDropTrap` likewise TT-authored. | **FIXED (doc).** Added a "Disclosure-field provenance" subsection: these two JSON fields are reused Gate-1 constants emitted for every SUT, NOT SS analysis; the SUT-2-correct reading spelled out (SS has a broker but the measured SS-B path is synchronous; no async-FP claim either way). Code constant left unchanged (frozen TT wording); the doc is the SUT-2 correction of record. |
| 2 | A1, B6, C4 | **Quiescence/gate machinery NOT exercised** — all present on poll 1 (9–38 ms); "gate 100% resolved" because nothing needed gating, not because a hard case resolved. | **FIXED (doc).** New "Threats to validity" section: SS-B is synchronously consistent; the delay-induced false-absent vector isn't stressed here → rests on TT Gate-1. Scope tightened to "HAL parsing + exact-match membership + cookie auth," explicitly NOT the quiescence machinery. |
| 3 | A2, B5, C3 | **Pseudo-replication** — 1200 = 30 iterations × 40 shapes (2 endpoints × 20 variants); headline count invites a naive n=1200 CI. | **FIXED (doc).** Threats section: fresh keys make each a genuine trial (honest count), but effective independent designs ≈ 40; `[0,0]` is a descriptive observed interval, NOT a CI; any paper-stage CI must use the correlated structure. |
| 4 | A3, C4 | **`readback_bound: 500` guard never exercised** (absence path never reached). | **FIXED (doc).** Threats section notes the bound rests on unit tests, not this run; immaterial to FP=0. |
| 5 | A4, B7, C6 | **"~12–19 ms" understates the range** (actual 9–38 ms). | **FIXED (doc).** Corrected to "first poll, 9–38 ms." |
| 6 | A6 | **Provenance** — records log is grep-extracted from gitignored mist.log; run dir not committed. | **FIXED (doc).** Threats section states the report JSON is committed, the records are a faithful extraction, internal consistency is complete. |
| 7 | C6b | Run started 02:30:28, ~1.5 min AFTER `a8d7d32` committed (02:28:53), yet jar attributed to `0a16255`. | **NO CHANGE (moot, verified).** The jar was BUILT 02:19 from `0a16255` and not rebuilt after `a8d7d32`; `a8d7d32`'s only main-source change is a comment (behaviorally inert), so 0a16255 ≡ a8d7d32 behavior. Attribution stands; all three verified inertness. |

## Outcome
**RESULT REVIEWER-ACCEPTED.** 3× ACCEPT-WITH-FIXES; every fix was a doc/disclosure clarification or
threats-to-validity addition; none touched the run or the headline. The SUT-2 benign FP result
(0/1200, bar v2 PASS, gate 100 % resolved) is claim-ready as the FP-external-validity half of the
generalization story, correctly scoped (HAL parsing + exact-match membership + cookie auth; NOT the
quiescence machinery, which rests on TT Gate-1).
