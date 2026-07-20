# PRE-REGISTRATION — TT re-capture rev 2 (3-cold reconciled; committed BEFORE any read-back)

**Date:** 2026-07-20 · This document is the PRE-REGISTRATION: the inclusion set, per-case escape
surface, verify/ABORT gate, and anti-outcome-shopping rules are FIXED HERE, committed BEFORE any
read-back value is observed. 3 cold reviews = unanimous ACCEPT-WITH-CHANGES; all blocking changes
folded. Executing after this commits.

## The REDUCED inclusion set (FIXED — decided on the INPUT side, before reading any read-back)
8 cases = 4 positives + 4 controls. `cancel-refund-natural` DROPPED (tell-bearing, R3).
| case (positive + its control) | fault class | escape surface (R1-verified) | service class |
|---|---|---|---|
| TT-adminroute-lostwrite + control | JVM flag | **API** `GET /api/v1/adminrouteservice/adminroute/{routeId}` (route id is client-supplied) | A (adminroute image) |
| TT-adminbasic-contacts-lostwrite + control | JVM flag | **API downstream** `GET /api/v1/contactservice/contacts/account/{accountId}` (admin layer has no per-entity GET; downstream does) | A (adminbasic image) |
| TT-createaccount-agreement + control | inside-payment toggle | **MySQL** `inside_money` probe by accountId (NO scoped API read exists) | B (inside-payment) |
| TT-cancel-refund-fabricatedack + control | inside-payment toggle | **existing E2 DB ground truth FIRST**; only if absent, MySQL probe; refund has NO client key ⇒ within-leg before/after balance-delta only | B (inside-payment) |

Post-re-capture positive stratum (HONEST, disclosed): **7 = TeaStore 3 / TT 4 (~4:3, 2-SUT)**.
DISCLOSURE OBLIGATIONS baked in: TT-domination (report per-SUT agreement), positives are
2-SUT-by-construction (OTel async / SS trace-only never blind-rateable), the calibration floor
(50, ≥2:1 benign) stays UNMET regardless, the 2 soft MIST-provenance positives.

## Anti-outcome-shopping protocol (R3 — the integrity spine; PRE-COMMITTED)
1. Inclusion is the table above; NO case added/removed after a read-back is seen.
2. **Attempt cap N=3 per leg**; the FIRST run that passes the verify gate = the run of record.
3. **Contradiction-is-a-finding**: if the fault is verified ACTIVE (below) yet the fault leg reads
   PRESENT, that is a DISCLOSED ANOMALY recorded as-is — NEVER a re-roll.
4. **Full attempt log** committed (every leg, every attempt, gate outcome), even discarded ones.
5. Labels/ground-truth are FROZEN (`by_construction`); this re-capture opens the read-back render
   path ONLY. It cannot and must not move any headline number (MIST 10/13/10 frozen).

## Verify / ABORT gate (R2 — prevents silent ground-truth inversion; MANDATORY per leg)
- **Stale-image gate (Class A)**: rebuild from fork `MIST-trainticket` → kind-load → in-pod
  TOOL-FREE jar-literal grep (`unzip -p <jar> <class> | grep -a`) for BOTH `MIST_FAULT_LOSTWRITE_ENABLED`
  AND `-Dmist.fault.lostwrite.enabled` (prop-vs-env silent-no-fault trap). adminbasic = confirmed
  stale `:1.0.0` MUST rebuild; adminroute `:mistfault` re-verify survived (`crictl images`).
- **Toggle-liveness gate (Class B)**: the inside-payment toggle endpoint returns non-404 before use.
- **Behavioral ABORT rule (authoritative for both classes)**: record a leg ONLY if control-leg key
  PRESENT and fault-leg key ABSENT (via the SAME scoped surface). Fault-leg-LANDS ⇒ stale/mis-toggle
  ⇒ ABORT that case (a positive whose fault silently didn't fire would INVERT the label).
- **Control-present precondition**: verify the control leg returns PRESENT via the scoped surface
  BEFORE trusting any fault-leg ABSENT (esp. adminroute control runs on base `1.0.0` — if base
  doesn't honor client route ids the negative control silently breaks).

## Ground-truth-integrity hazards (R2 — both can silently flip truth; MANDATORY)
- **Fresh label-free keys per leg** (OTel `street_address` style, request-derived) — NEVER reuse
  `BR-FLT-R4`, `f1${LEG}`, or any leg-baked key (would re-leak the label into the machine-read body).
  This satisfies BOTH the collision-avoidance (fixed UUIDs + persistent PVCs) AND the neutralization
  requirement.
- **Toggle-residue reset** (Class B `static volatile`): reset fault→none AFTER each fault leg AND
  assert none BEFORE each control leg.
- **≥90s settle** after Class-A rollouts (ribbon/nacos re-registration; else read-timeout masquerades
  as absence).

## Neutralization / render (folds PLAN-rater-completion-RECONCILIATION's blocking fixes)
- Render from the STRUCTURED probe file ONLY, never `provenance.notes`.
- Expanded leak-gate on the RENDER: + `corrupt`/`faultmode`/`skew`/`lost`/`LEG=`/`submitted_`/
  `persisted_`/`fXCTRL|FLT`/`ksXf` (keep `drawBack` allowed). Opaque-id re-key on the b4_harness render.
- Fault-leg-only, de-paired values, no expected/"wrong" annotation.

## Sequencing (R2 split; time-boxed — memo says hours-scale, NOT 30-45 min)
1. Revive TT full graph (revive-tt-full → phase-c; WSL-flap rails; bounded-foreground probes).
2. Class B FIRST (zero rollout): inside-payment toggle legs — createaccount + cancel-fabricatedack
   (cancel: try existing E2 DB ground truth before any new probe). Checkpoint.
3. Class A: rebuild adminbasic (+ re-verify adminroute :mistfault) → rollout → the 2 API-scoped legs.
   Spill to a 2nd window if the box flaps.
4. Neutralize + render + expanded leak-gate; STAGE the MANIFEST-r2 rateability flips (Step-5
   staging + re-seal — NEVER in-place on the sealed read-only set).
5. Teardown TT to 0.

## What this is NOT (scope discipline)
NOT new fault sites; NOT the 5 corrupted cases; NOT S3/natural-hunt; NOT any label/headline change.
Only: real read-back bodies for 8 already-labelled TT cases, so ~4 become blind-rateable positives.
