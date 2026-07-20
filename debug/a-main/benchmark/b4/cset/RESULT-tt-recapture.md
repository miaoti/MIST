# RESULT — TT re-capture (rev2 pre-reg) — 4/4 CAPTURED — RESULT OF RECORD

**Date:** 2026-07-20 · Executed under `PLAN-tt-recapture-rev2-PREREG.md` (committed BEFORE any
read-back = the anti-outcome-shopping guard). USER-driven: the rater study cannot launch on ~3
positives (all TeaStore); this thickens the blind-rateable positive stratum with real read-back
bodies for 4 already-labelled TT cases. Labels/ground-truth FROZEN — this opens only the
rater-render path. Headline UNCHANGED (MIST 10/13/10 frozen).

## The 4 clean captures (all GATE PASS: control-present/landed & fault-absent/lost)
| case | fault mechanism | read-back (business-key-scoped MySQL, escapes the truncation gate) | control | fault |
|---|---|---|---|---|
| createaccount-agreement | inside-payment createfaultmode=fabricatedack | `ts.inside_money` rows for the fresh userId | **present (1)** | **absent (0)** |
| cancel-refund-fabricatedack (flagship) | inside-payment drawbackfaultmode=fabricatedack | `ts.inside_money` type-D refund rows for the fresh buyer | **appears (0→1)** | **none (0→0)** |
| adminroute-lostwrite | JVM flag lostwrite (`:mistfault` image) | `ts.route` total count-delta | **+1** | **+0** |
| adminbasic-contacts-lostwrite | JVM flag lostwrite (`:mistfault` rebuilt) | `ts.contacts` total count-delta | **+1** | **+0** |

Every case: both legs ack HTTP 200 success-shaped (the "acknowledged" of acknowledged-but-lost);
control persists, fault acks-but-doesn't-persist.

## Pre-registration honored
- Anti-outcome-shopping: inclusion fixed before any read-back; N≤3 cap; first-passing-run=of-record;
  contradiction-is-a-finding (none fired); FULL attempt logs committed incl. the corrected probes.
- Read-back PROBE corrections (transparent, logged; the ground truth was never in doubt — only the
  probe row): cancel limit-1 money → type-D count (`attempts-probe-v1-WRONG-ROW.log`); adminroute
  client-id → salted-station → total-count-delta (`attempts-probe-v1/v2` logs) because the server
  generates its own id + validates stations + the fault leg returns data:null.
- Verify/ABORT gate: control-present + fault-absent enforced per case (a fault-leg-lands would have
  inverted the label → abort; none occurred). Stale-image caught + rebuilt: adminbasic `:1.0.0` →
  `:mistfault`; inside-payment `:1.0.5` verified NOT-stale via the behavioral differential.
- Ground-truth-integrity: fresh LABEL-FREE keys per leg (fixed-UUID+PVC collision AND
  neutralization); toggle-residue reset each leg; ≥90s settle after every rollout.
- Ops findings (disclosed): adminbasic had NO `/otel` volume → the javaagent JTO crashlooped →
  agent dropped (read-back is MySQL, agent unneeded); `Contacts.accountId` is UUID-typed → non-UUID
  body 400'd → fresh UUIDs.

## The positive stratum after (honest, per the 3-cold reconciliation)
Blind-rateable positives: **3 TeaStore → 7 = TeaStore 3 / TT 4** (~4:3, 2 SUTs). Positives are
2-SUT-BY-CONSTRUCTION (OTel-checkout async / SS-swallowed trace-only are never blind-rateable) —
disclosed, not hidden; report per-SUT agreement, AC1 (not κ) headline, calibration floor still
unmet. This is defensible (2-SUT beats mono-TeaStore) and is exactly what the study needed to not
launch on ~3.

## Remaining (OFFLINE — no cluster; folds into PLAN-rater-completion rev-2)
1. Neutralize + render the 4 into rater-safe sidecars: fault-leg-only, de-paired, no expected/"wrong"
   annotation, expanded leak-gate (+`corrupt`/`faultmode`/`lost`/`LEG=`/`fXCTRL|FLT`/`ksXf` …),
   opaque-id re-key on the b4_harness render.
2. MANIFEST-r2 rateability: flip the 4 to `ok` via Step-5 staging + re-seal (NEVER in-place on the
   sealed set).
3. (Separate, from PLAN-rater-completion) the 4 LIGHT non-TT doc bundles.

## End state
TT scaled to 0; all fault toggles verified reset (adminroute JTO agent-only, adminbasic JTO empty,
inside-payment drawback/create=none) BEFORE teardown; adminbasic/adminroute images left at
`:mistfault` (the correct fork build); sealed sets untouched.
