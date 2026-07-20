# PLAN — TT re-capture (thicken the positive stratum) — awaiting 3-cold ALL-ACCEPT

**Date:** 2026-07-20 · USER decisions locked: (1) the 5 corrupted cases are OUT of the primary
rating set; (2) the rater study CANNOT launch on ~3-4 positives — the TT re-capture MUST happen to
give the study a real positive stratum. This plan does the re-capture. Awaiting ≥3 cold-reviewer
ALL-ACCEPT before ANY execution.

## Root cause (verified, not the earlier "truncation" framing)
`MEMO-tt-per-endpoint-rendering.md` + MANIFEST-r2 confirm: the 9 TT admin-family cases are
blind-un-rateable NOT merely because a collection window truncated, but because **their committed
read-back records carry `payload: null` at every layer** (raw / -traced / neutralized). There is no
read-back BODY to re-key or re-render. The only fix is to RE-CAPTURE the read-backs with
acting-record-scoped per-endpoint probes, saved as machine-read files (the OTel
`readback-psql.txt` pattern), then neutralize + render.

## The 9 cases this un-gates (all TrainTicket)
4 lostwrite/control PAIRS + benigns: TT-adminroute-{lostwrite,control}, TT-adminbasic-contacts-
{lostwrite,control}, TT-cancel-refund-{fabricatedack,clean,natural}, TT-createaccount-{agreement,
clean}. Un-gating them adds up to ~5 clean POSITIVES (adminroute-lostwrite, adminbasic-lostwrite,
cancel-fabricatedack, cancel-natural, createaccount-agreement) + their control/benign twins —
turning the positive stratum from "3, all TeaStore" into a multi-site, multi-SUT set.

## Prerequisite (learned from the E2E Allure demo, do NOT skip)
The deployed `ts-admin-basic-info-service:1.0.0` (and likely other admin services) is a STALE image
PREDATING the lostwrite fault commit — same class as the adminroute `:1.0.0` the E2E demo caught.
Any service whose fault must be toggled for a re-capture must FIRST be rebuilt from the fork
(`MIST-trainticket`, the fault present) + kind-loaded + verified in-pod (constant-pool grep), exactly
as the E2E demo rebuilt `:mistfault`. Verify per service BEFORE capture; a re-capture on a stale
image would silently produce a no-fault result.

## Design — per-endpoint acting-record probes (mirrors the OTel machine-read pattern)
For each case, per leg (control fault-off / fault fault-on), capture the read-back as a MEMBERSHIP +
VALUE + COUNT probe scoped to the business key (NOT the truncated global collection):
- membership: GET the specific record by business key → present / absent
- value: the durable field(s) the case is about
- count: the collection delta for the buyer/owner, bounded to the acting record
Save each as a machine-read `readback-*.txt/json` beside the capture (the OTel `readback-psql.txt`
convention), so the neutralizer has a real body to consume. Land-then-(flip)-then-read like the E2E
demo; admin auth = `-Dauth.mode=per_jvm` (admin/222222); jaeger not required for the read-back probe
(observe/paired durable read only).

## Then the rater pipeline (folds into PLAN-rater-completion rev-2, all its blocking fixes apply)
1. raw probe → structured sidecar (the raw→structured step R1 flagged missing)
2. neutralize with the EXPANDED leak-gate (R2: + `corrupt`/`faultmode`/`skew`/`lost`/`LEG=`/
   `submitted_`/`persisted_`/`fXCTRL|FLT` + ~11 groups; keep `drawBack` allowed) — **fault-leg-only,
   de-paired values, no expected/wrong annotation** (R2 B5)
3. opaque-id re-key + render `case.md` via b4_harness; run the leak-gate ON THE RENDER (R2 B2)
4. MANIFEST-r2 rateability → `ok` for the re-captured cases; regenerate
5. calibration supply: these add benign twins too (helps the R1d benign-skew floor)

## Scope discipline (what this is NOT)
- NOT re-running S3 / natural-positive hunting (scarcity-forbidden).
- NOT the 5 corrupted cases (USER: out).
- NOT new fault SITES — same 9 cases, real read-back bodies this time.
- Labels/ground-truth UNCHANGED — only the read-back render path opens.

## Cost / risk
One TT full-graph revival window (the revive-tt-full → revive-phase-c path; ~30-45 min incl. the
WSL-flap rails) + per-service image rebuilds where stale (adminbasic confirmed stale; check
adminroute[:mistfault already built], cancel, order, user, inside-payment) + per-case bounded probe
runs + neutralize/render. RISK: (a) stale images silently no-fault → the in-pod verify gate is
mandatory; (b) admin read-back endpoints may themselves window/truncate → the probe MUST be
business-key-scoped, not collection-wide (that was the original gate); (c) WSL flap → bounded
foreground probes, no long background waiters for the measurement.

## Questions for the reviewers
1. Is the per-endpoint business-key probe design SOUND — does it actually escape the global-
   collection truncation that gated these 9, and will it yield a rater-safe read-back body?
2. Is the stale-image prerequisite correctly scoped — which of the 9 cases' services need a rebuild,
   and is the in-pod verify gate sufficient to prevent a silent no-fault capture?
3. WORTH-IT: does un-gating ~5 positives across the TT sites materially fix the "3-positives-all-
   TeaStore" thinness the rater study needs, enough to justify the window? Any case among the 9 not
   worth re-capturing?
4. Does anything here risk changing a LABEL or the headline numbers (it must not — read-back body
   only)?
