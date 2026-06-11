# Evidence — intent-aware trace oracle detects SILENT ACCEPTANCE (controlled mutation, real SUT)

> **STATUS (2026-05-29): SUPERSEDED — silent-acceptance is no longer the contribution, and the `SilentAcceptanceInvariant` shown here has been REMOVED from the tool.** Two findings made it redundant: (1) RESTifAI (ICSE'26 demo) already detects HTTP-200-instead-of-4xx silent-accept at the response level; (2) MIST *itself* already ships an LLM "soft-error" check (LLM Response Validation) that adjudicates a 2xx-for-a-negative-test by reading the response body — so a trace-based silent-accept detector merely duplicated an existing capability. It was deleted (mist-core 266/0/0 after removal). This file is retained as a **historical record** that the capability was built and demonstrated end-to-end on the live SUT. The real main contribution is **HIDDEN-DOWNSTREAM-FAILURE** detection (a gateway 2xx hiding a downstream 5xx) — invisible to RESTifAI, to response-level oracles, *and* to MIST's own soft-error check (the response body is clean; only the **trace** exposes it) — plus the automated intent-conditioning mechanism. See `../RESEARCH_a-conference-viability.md`.

Reproducible evidence for the main contribution: a **label-free, intent-aware trace
oracle** detects *silent acceptance of invalid input* — a bug class that the
response-status / fault-name oracle structurally cannot see. Demonstrated end-to-end on
the live train-ticket SUT with a known ground-truth mutant. Date: 2026-05-28.

## Methodology (controlled mutation = ground truth)
1. Pick one of the 10 tracked faults: `INSUFFICIENT_STATIONS_FAULT` (POST `/api/v1/adminrouteservice/adminroute`).
2. **Inject a mutant** that turns its correct rejection into a *silent acceptance*:
   `AdminRouteServiceImpl.createAndModifyRoute` now returns `Response(1,"…success") → HTTP 200`
   (no `faultName`) for `<2`-station routes, instead of `Response(0,…faultName) → HTTP 400`.
   (train-ticket-injection commit `76d15163`, branch `injection`; one method, revertible.)
3. Run MIST against the rebuilt SUT with `mst.oracle.shape.invariants.silent_acceptance.enabled=true`.

## Result — A/B with ground truth

| | Clean SUT (run22) | Mutated SUT (this demo) |
|---|---|---|
| SUT response to insufficient-stations negative | HTTP 400 + `faultName` | **HTTP 200, no faultName** |
| Fault-name / status oracle on `INSUFFICIENT_STATIONS` | **DETECTED** (820 hits) | **UNDETECTED** (missed) |
| `SilentAcceptanceInvariant` (trace oracle) | silent (correct — SUT rejected) | **FIRES** — 70 hits on `stationList`, 67 `startStation`, 67 `loginId`, … |
| `INVALID_STATION_NAME_LENGTH` (same endpoint, **not** mutated — control) | DETECTED | **DETECTED** (4×) — no regression, trace oracle correctly stays silent on its 400 |

→ When the SUT regresses to silently accepting invalid input (a realistic bug), the
response/fault-name oracle goes blind, but the intent-aware trace oracle catches it. The
un-mutated control fault proves this is not a false-positive artifact.

Report (committed alongside): `fault-detection-summary-silent_accept_demo.txt`.
SILENT_ACCEPTANCE violation text (verbatim): *"negative test targeting
ts-admin-route-service/stationList was silently accepted: no rejection in trace and root
returned 2xx"* (WARN, 70 hits).

## Why response-level / crash / fault-name oracles cannot see this
- Status oracle: the SUT returns 200 → looks successful.
- Fault-name oracle: the mutant emits no `faultName` → nothing to match.
- Crash oracle: no 500.
- AGORA+-style single-API response invariants: would need to have learned the exact
  response field; no notion of "this input was supposed to be rejected."
- Only the **trace + the negative test's intent** (this input targeted `stationList` and
  should have been rejected, yet the whole trace is 2xx) reveals it.

## Reproducibility
- Tool: `inject-detection` @ `b817c4e6` (detectors + flags); `mist.jar` contains
  `SilentAcceptanceInvariant`. Unit tests: `mist-core` 273/0/0.
- SUT mutant: re-push train-ticket-injection `76d15163` to `injection` (~20-min rebuild),
  then run MIST with the demo config (`_demo-core.properties`, silent_acceptance enabled).
- Revert: `git revert 76d15163` on `injection` restores correct 400-rejection.

## Caveats (honest)
- Scoped to one adminroute scenario, so 8 of 9 "undetected" faults are simply on other
  endpoints not exercised in this run (expected).
- SilentAcceptance fired for several target params on adminroute, not only `stationList`;
  each is a genuine silent-accept (SUT returned 200 for an invalid input), incidentally
  surfacing that adminroute also lacks `loginId`/`startStation` validation.
- A single hand-crafted mutant is an anecdote; the paper-grade version is a **mutation
  study** over the 10 tracked faults (each turned into a silent-accept and/or
  hidden-downstream mutant) reporting trace-oracle detection rate vs the baseline (0 by
  construction for these classes). See the design doc `debug/oracle_arch/TARGET_ARCHITECTURE.md`.
