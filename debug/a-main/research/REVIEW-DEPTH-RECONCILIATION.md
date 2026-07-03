# Reconciliation — depth-code review (3 cold reviewers) + fix wave

Reconciles REVIEW-DEPTH-{A-oracle-soundness, B-wiring, C-injector}.md over the two
depth commits — 3a94b88 (supplied isolation + value-delta read-back) and 7453142
(IstioRouteFaultInjector). All three reviewers independent, no shared context.

## Verdicts
| Reviewer | Angle | Verdict |
|----------|-------|---------|
| A | value-delta ORACLE SOUNDNESS (false FIRE / false negative) | ACCEPT-WITH-FIXES |
| B | wiring / additivity / regression to the membership path | ACCEPT-WITH-FIXES |
| C | Istio injector correctness under the executor | ACCEPT-WITH-FIXES |

Consensus: the *constructions* are right — own-baseline value-delta with the paired
verdict unchanged (A), byte-identical membership path (B, additivity CONFIRMED clean
by pre-image diff), probe-gated convergence both directions with the correct
conservative bias (C). Every finding had a bounded local fix. None blocks the design;
the blocking ones block *G3 data collection*, and are now fixed.

## Dispositions (all applied unless marked)
| # | Finding | Sev | Disposition | Commit |
|---|---------|-----|-------------|--------|
| A-F2a | value-delta probe row VANISHING from a 2xx read (base≠null, cur=null) read as movement → fault-leg false-negative latch | BLOCKING | FIX: `XVerdict.VANISHED` → error record, never X-present | dfb… wave2 |
| A-F2b | unparseable/garbage 2xx baseline reads as all-null probe surface | BLOCKING | FIX: `parsesToCollection` gate at the hook → error | wave2 |
| A-F2c | `readback_bound` is a membership-absence guard, wrong-way in value-delta | MED | FIX: registry REJECTS value-delta + readback_bound (truncation now surfaces via VANISHED instead) | wave1 |
| A-F1 | value-delta baseline is a numeric datum an unsettled earlier step can poison; isolation assumed not enforced | MED | FIX: pre-write **stability double-read** (two equal probe reads or error). DISCLOSE: per-leg-fresh-user isolation is a **runbook rule**, not machine-enforced (baselineContainedX hardcoded false in value-delta by design — X is defined vs the leg's own baseline, so the executor cross-leg isolation tripwire does not apply); recorded in the runbook below | wave2 + doc |
| A-F3 | zero-delta refunds (expired/NOTPAID → R="0"/"0.00") are invisible; expiry-boundary crossing between legs = false-FIRE vector | MED | RUNBOOK rule (below): PAID order, far-future travelTime, nonzero price, key = loginId | doc |
| A-F4 | two reads of one balance can differ in whitespace | LOW | FIX: `trim()` in valueDiffers (BigDecimal already absorbs scale) | wave2 |
| A-F5 / B-F2a | registry permits FRESH_STRINGS + value-delta (untested mixed semantics) | MED | FIX: registry REJECTS value-delta unless strategy=supplied | wave1 |
| B-F2b | value_probe match_field == value_field = a constant, blind to change | LOW-MED | FIX: registry REJECTS | wave1 |
| B-F1 | not-acked immediate read scans an error body under value-delta | LOW | FIX: gate presentX on 2xx in the !acked path | wave2 |
| B-F3 | supplied-hook baseline-failure message byte-identical to the freshening hook's | NIT | FIX: "supplied baseline read-back …" | wave2 |
| C-F2 | abortStatus 500 sits in the natural app/Envoy 5xx space → transient 5xx falsely converges inject | SUBSTANTIVE (config/doc) | FIX: constructor javadoc REQUIRES a status outside app+mesh space (418); the mesh note + deploy manifest use 418 | wave2 + [g3-tt-mesh-fault-note.md](../prep/g3-tt-mesh-fault-note.md) |
| C-min | -1 sentinel leaked into the timeout message; nanoTime<deadline overflow; drainQuietly keep-alive comment | NIT | FIX: "an I/O failure"/"nothing"; overflow-safe `now-deadline<0`; comment corrected | wave2 |
| B-quirks | supplied+fault_flag, supplied+odd key names, value-delta+readback_bound(now rejected) | — | ACCEPT (harmless / already covered) | — |
| C-F2-authority | @LoadBalanced RestTemplate defeats VS host matching | SUBSTANTIVE (deploy) | RESOLVED in the mesh note: EnvoyFilter on the inbound listener, authority-independent | doc |
| C-min2 | f2FailedFlags names the logging FaultTarget not the manifest; single-path probe ≠ all-proxies converged | LOW | DISCLOSE in the G3 run notes (per-proxy propagation window is conservative-direction only) | run-notes TODO |

## Test-gap fills (this wave)
Added: probe-row vanish→error, unstable-baseline→error, non-collection-baseline→error,
value-delta fault-leg **OBSERVED_COMPLETE_ABSENT** (the gate the depth fault leg
publishes — previously untested), parsesToCollection unit, whitespace-trim, +3 registry
rejects. mist-cli 104 → 137 tests; full reactor 35 + 331 + 137 green.
Deferred (documented, low value): injector exec-IOException/Interrupted wraps +
interrupt-flag restoration, interrupted-probe-sleep, supplied→supplied orphan drain
(the drainOrphan path is already covered for the freshening hook and is shared code).

## RUNBOOK rules for the value-delta depth run (from A-F1/A-F3 — pre-registered)
1. **Per-leg fresh buyer.** Each control/fault execution registers a NEW user; the
   value-delta FIRE feeds a claim only under this (isolation is by construction, not
   enforced in code — disclosed).
2. **A refund that must be nonzero.** The cancelled order is PAID, price > 0, and
   travelTime is far-future so `calculateRefund` returns 0.80·price (not "0"/"0.00").
   No leg may cross the expiry boundary between control and fault.
3. **Key = loginId** (the drawback credits loginId, = the buyer for a self-service
   cancel). match_field=userId, value_field=balance on GET /inside_payment/account.
4. **Baseline stability** is enforced by the double-read; a non-stable baseline is an
   error (NOT_EVALUABLE), never a verdict.

## Bottom line
All three ACCEPT-WITH-FIXES; every blocking + substantive finding fixed and tested,
the rest fixed or disclosed as runbook/run-notes. The depth code is cleared for the
G3 head-to-head data collection (after the harness + deploy). Membership path proven
untouched.
