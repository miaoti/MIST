# S3 P4 — dedup + environment-guard audit + SCARCITY BRANCH (invoked)

Plan `s3-wildhunt-plan.md` rev 2.1 §7 P4. Operates on the three committed window artifacts
(`s3/window-{oteldemo,teastore,trainticket}/`); no live SUT (all tenants at 0). Detector pin `5802fa8`
(unchanged across all three windows; TT ran the `0fbe00f` tree, workload-harness diff only).

## §1 Provenance-scoped dedup + rediscovery counts

CONFIRMED wild flags across all three windows: **0** (OTel 0 / TeaStore 0 / TT 0). Provenance-scoped
dedup is therefore **vacuous**: 0 flags → 0 duplicate triples → **0 rediscovery**. The S3 (stratum-3,
CONFIRMED) candidate set is **empty**; there is nothing to key by provenance, nothing to collapse.

Non-CONFIRMED classifications (for completeness; NOT S3 members):
- **raw-delayed = 1** (OTel `w120`, present-at-re-probe — a bounded-eventual-consistency benign; a P5
  top-up candidate, not a CONFIRMED).
- **present = 1513**, **error = 0**, **quarantined = 0**, **not-acked = 0** (excluded), **operational
  skips = 1** (TT journey-4 connection reset; excluded from the denominator, never a detector event).

## §2 Environment-guard audit (all three windows)

Each window's `environment_guard` is checked for the pinned "no fault injected / frozen defaults"
conditions that make an acked-loss *natural* (not induced). Result: **PASS** on all three.

| SUT | recorded environment_guard | fault-free established by | breaker | denom |
|-----|----------------------------|---------------------------|---------|-------|
| OTel-Demo | `flagd-15-off-2026-07-13; loadgen-absent; traceparent-adopted-57spans; dbbaseline-23`; `load_generator: off` | flagd at frozen defaults (15 flags OFF) **and** load-generator OFF — the two contamination risks affirmatively ruled out; traceparent adoption canary-confirmed | `[]` | 500 / 1 ep |
| TeaStore | `autoseed-100users; maintenance-false; sync-SUT` | the known fault (persistence maintenance-mask) affirmatively OFF; auto-seed baseline | `[]` | 500 / 1 ep |
| TrainTicket | `admin_auth per_jvm; marker_seed_base/effective; gateway; bound_endpoints:3` | (a) the runner has **no fault-injection code path** (pure benign workload); (b) the invocation's `-Ds3.envguard="admin-auth-ok; subgraph-healthy; no-fault-injected; sync-SUT"` (greppable in `runners/s3/trainticket.sh` + RESULT-p3); (c) §0.4 SYNC proxy, 514/514 present-at-cap | `[]` | 514 / 3 ep |

**One documented minor inconsistency (not a substantive gap):** TT's `emit()` builds its env_guard JSON
from SUT-specific fields and does not re-serialize the `-Ds3.envguard` shell string, so the TT JSON lacks
an explicit `no-fault` token that OTel/TeaStore carry (`loadgen off` / `maintenance-false`). TT's
fault-free status is nonetheless established three independent ways (above). Recorded here rather than
re-running a 12-minute window for a cosmetic field.

## §3 Stratified sample

The §2c stratified sample draws from the CONFIRMED (S3) set. **|S3| = 0 ⇒ the sample is empty**; the
"no-tool-signal-in-sampling" rule (C-B3/A-F10) is trivially satisfied (nothing to sample). No S3 case
files (`stratum: 3`) are authored — there are no CONFIRMED flags to author them from.

## §4 SCARCITY BRANCH — formally invoked (pre-registered; the central expectation)

The A-F1c threshold binds on CONFIRMED: **< 20 CONFIRMED ⇒ scarcity branch** (freeze §6; plan §1/§0.4).
Observed CONFIRMED = **0 < 20** across **1514 acked writes / 5 bound endpoints / 3 diverse OSS
microservice SUTs**. The branch is INVOKED. Per §0/C-M1 this is the **pre-registered CENTRAL expectation,
not a null path**: every acked-lost behavior ever captured on these SUTs required an *injected* fault, so
the honest prior of a *natural* find was ≈ 0 — now measured as a lower bound.

**M-prevalence datum (§5 estimand).** Pre-committed zero-finds sentence (§0.5), instantiated:

> **0 CONFIRMED flags in 1514 acked writes over 5 bound endpoints on these SUTs (OTel-Demo, TeaStore,
> TrainTicket) under the pinned workload; rule-of-three 95% upper bound ≈ 3/1514 ≈ 0.20% on the per-write
> CONFIRMED-flag rate under these conditions; no cross-population claim.**

Supporting per-SUT denominators (both bars): OTel 500/1ep, TeaStore 500/1ep, TT 514/3ep; write-path
fraction 0.5 (TT/OTel journeys = 1 read step per bound write); coverage = the bound endpoints named per
SUT (no claim beyond them). **κ:** |S3| = 0 ⇒ the §0.5 degenerate branch — S3-only κ WITHHELD (no S3
precision row); the rated set = calibration (+ the Step-4 M-yield stratum when merged), still yielding
calibration κ + the bias audit.

## Carry-forward to P5

- Rating-corpus benign/top-up mix (P5): the 1 OTel raw-delayed (`w120`, already B4-validated end-to-end)
  + calibration-derived clean present cases (19 TT + 20 TeaStore + 20 OTel available) → the §4
  floor-30 top-up is met from calibration presents (the pre-registered floor-30 shortfall branch — the
  natural top-up yield was 1, exactly the C-2 worst case).
- Measured-recall legs: OTel analytic-0 (always-on) + 2.75-A paired 5/5; TeaStore maintenance-toggle
  (2.75-A paired 5/5 cross-ref); TT fabricated-ack SYNTHETIC exemplar — all cross-referenced, none owed
  new by S3 (the real traced discrimination run stays owed at 2.5/E2).
- No S3 case files; the deliverable is the scarcity datum + the assembly-ready calibration/top-up mix.
