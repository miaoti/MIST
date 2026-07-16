# MEMO — A3(ii): TT per-endpoint rendering for the 9 truncation-gated cases — EVIDENCE-BLOCKED (decision = USER at the Step-5 seal)

**Date:** 2026-07-16 · Completion-set wave, Phase A. Pinned item: implement + stage the
gated per-endpoint membership/value/count rendering for the 9
`tt-collection-truncation-gated` cases (MANIFEST-r2 rateability class).

## Finding (verified, not argued): the committed evidence cannot feed it

The TT admin family's committed sidecars carry **NO observation payloads at any layer** —
raw capture, `-traced` capture (the case files' designated `readback_response` of record),
AND the rev-3 neutralized rater-sidecars all have `payload: null` on their observation
records (verified across `tt-s1-adminbasic-contacts-lostwrite{,-traced}`,
`tt-s1-adminroute-lostwrite{,-traced}`, `tt-ctl-adminroute-create`,
`tt-benign-contacts-dedupe`). Unlike the OTel captures (separate machine-read
`readback-psql.txt` files), the TT admin capture dirs hold ONLY `sidecar.json`
(+`trace-fault.json` in traced variants). The acting-record evidence exists only in
ADMIN-SIDE case-file prose (`provenance.notes`, label-carrying — not rater-safe by
construction).

**Consequence:** a per-endpoint membership/value/count rendering built "from committed
evidence" is IMPOSSIBLE for these 9 cases — there is no committed read-back body to
re-key, re-window, or re-render. The E1+R2 truncation finding gated the class correctly;
this memo upgrades the gate's basis from "acting record beyond the rendered window" to
"no rater-safe read-back body exists in the committed record at all."

## The two branches (decision = USER, at the gated Step-5 seal)

- **(a) Re-capture with per-endpoint probes** — a TT revival window re-running the 9
  cases' read-backs with acting-record-scoped probes (membership by business key +
  count), captured as machine-read files (the OTel `readback-psql.txt` pattern), then
  neutralized + rendered. COST: one TT full-graph revival window (hours-scale, the
  revive-stage.sh path) + re-neutralization + manifest/seal updates. GAIN: up to 9
  additional rateable units (4 lostwrite/control pairs + benigns) for the calibration
  supply (which is short on the benign side — R1d).
- **(b) Keep the class excluded from blind rating** (the current MANIFEST-r2 state) —
  zero cost; the corpus's rateable supply stays as-is (14 ok of 25); the 9 cases remain
  fully valid BENCHMARK cases (labels/ground truth untouched) — only the RATER-render
  path stays closed.

**Recommendation:** (b) unless the Step-5 calibration assembly is blocked by supply — the
9 cases' ground truth does not depend on rater-rendering, and branch (a)'s window is
better spent only if the calibration floor demands it (see the A3(iv) rehearsal, which
quantifies the supply). NEW captures are out of THIS wave's scope either way (rev-2 §6).

Sealed sets untouched (this memo lives in staging).
