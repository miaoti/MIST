# S3 P5 — assembly readiness (sizing · top-up finalization · tell-audit · render · SEAL · hand-over)

Plan `s3-wildhunt-plan.md` rev 2.1 §7 P5 / §4. Operates on the committed S3 artifacts; the SUTs are at 0
(P5 is artifact-only). This phase makes the S3 contribution assembly-ready and marks the cross-track +
USER-side holds — it does NOT itself assemble/seal the FULL rating corpus (that merge is cross-track).

## §1 Calibration sizing per |S3| (A-F5 arithmetic)

|S3| (CONFIRMED) = **0** ⇒ required calibration = max(30, 50−|S3|) = **50**; benign ≥2:1 over the genuine
supply. **Corrected worst case (r2.1 C-2):** genuine supply ≈ 7–8 after eligibility-screen +
worked-example consumption ⇒ benign requirement ≈ 50−8 ≈ **42–43**. Captured negative pool = **11**
(corpus track). ⇒ a benign shortfall was **UNCONDITIONAL and NEAR-CERTAIN** (pre-registered).

## §2 Top-up finalization — FLOOR-30 SHORTFALL BRANCH invoked (deterministic, disclosed)

Benign top-ups must be **degradation-shaped ONLY** (documented async-delay / by-design soft-reject /
bounded eventual consistency — NEVER clean "present" journeys, else a present-vs-absent split decodes the
stratum, C-B3/A-F10). The counted windows' achieved degradation-shaped supply:

| SUT | shape | degradation-shaped benigns yielded |
|-----|-------|-----|
| OTel-Demo | async (kafka→accounting) | **1** (`w120`, bounded eventual consistency: absent-at-cap, present-at-re-probe) |
| TeaStore | fully synchronous | 0 (no degradation shape exists on a sync path) |
| TrainTicket | fully synchronous | 0 (same) |
| **total** | | **1** |

Deterministic surplus rule: achieved (1) < computed size ⇒ **no surplus to trim; the disclosed
floor-30 shortfall branch is invoked** ("never dilute shape or skew" — a clean-present top-up is
forbidden). **Achieved degradation-shaped benign top-up = 1.** Combined with the 11 captured negatives,
the benign pool = **12**, below the floor 30 and far below the computed ≈42–43.

**Power consequence (reported per the branch, so nobody is surprised at RESULT time):** the C3 rater
study's benign side is thin — 12 benign against ~7–8 genuine. The bias-audit's negatives
under-represent **async ambiguity** (only `w120` carries it; the rest are the corpus-track sync
negatives), exactly the A-F10 disclosure. S3-only κ is WITHHELD anyway (|S3|=0 degenerate branch); the
study yields the calibration κ + bias audit at reduced benign power. **This is the pre-registered outcome
of the scarcity world, not a surprise.** (A dedicated degradation-shaped capture wave — by-design bounded
delays/soft-rejects on the async SUT — would raise benign power; it is a NEW capture activity outside this
accepted plan's "no post-window re-revival" rule and would need its own plan + reviewer pass. Flagged as a
recommendation in §6, not executed here.)

## §3 Cadence-uniformity tell-audit

- **Within the S3 material:** `w120`'s 3 observations (baseline / at-cap ABSENT ~27s / re-probe PRESENT
  ~328s) use the OTel pinned knobs (timeout 25s, poll 2s, re-probe 300s); the **re-probe is uniform at
  T+5min (300s)** across all strata (plan §4.2). The one-shape 3-observation transcript matches the P0
  unit-tested sidecar shape. PASS.
- **Cross-SUT (recorded, not resolvable in this track):** the at-cap timeout differs by SUT (OTel 25s vs
  TeaStore/TT 10s) — but the SUT is already visible to the rater from endpoints/payloads, so per-SUT
  timing is not a NEW tell. **The load-bearing tell-audit item for the MERGED corpus (C-B3/A-F10):** the
  re-probe-PRESENT shape must NOT perfectly correlate with the benign label. `w120` (the lone S3 benign)
  is re-probe-PRESENT=benign; if every benign is re-probe-present and every genuine is re-probe-absent,
  the label is decodable. **Mitigation is a corpus-assembly (cross-track) obligation:** the merged benign
  set must include re-probe-ABSENT-but-benign shapes (by-design drops/soft-rejects — the corpus track's
  `tt-s2-contacts-dedupe` family) so presence↮label. Recorded here as a hand-over checklist item.

## §4 B4 render (all S3 strata) + our-side entry-gate checks

The single S3 rating case (the `w120` benign top-up) is authored + rendered:
- Case file `cases/oteldemo-checkout-eventual-benign-001.json` (schema 2.0.0, stratum 2, label negative
  by_observation) + sidecar `s3/window-oteldemo/sidecars/w120-sidecar.json`.
- **Rendered clean through `b4_harness.render`:** opaque `S3-BENIGN-01`, `case_md_sha256 =
  75dce034…d45f7`; **0 BANNED_STRINGS**; answer key (label/rationale/classification/mist_commit) fully
  **stripped** — only legitimate SUT-domain content reaches the rater (the `accounting.shipping` read-back
  probe, which the rater must see to judge). NO S3 CONFIRMED (stratum-3) cases exist to render (|S3|=0).

Our-side entry-gate checks on the S3 material: leak-clean render ✓ · opaque-id-only (true id only in the
manifest row) ✓ · deterministic bytes (stable `case_md_sha256`) ✓ · 3-observation one-shape ✓ · no
absolute-time key (harness rejects; sidecar rebases `t_rel`) ✓ · credentials N/A (OTel flow has no login)
✓ · cadence uniformity ✓ (cross-corpus correlation caveat per §3). The FULL 9-check entry gate on the
MERGED corpus is the cross-track/user-side step.

## §5 SEALED manifest + hash

`s3/SEALED-MANIFEST.sha256` — SHA-256 over 17 S3 deliverable artifacts (the 1 benign rating case + its
sidecar + flag bundle; the 3 counted windows + 3 FP calibrations; the 5 phase-result/pins docs). Seal
fingerprint (sha256 of the manifest) = `9080dbb8b9d1ef46a3adece423c255d974f0cdb2b5e825239fb00c8bdb93eacd`.
CONFIRMED S3 cases sealed = 0 (scarcity). Final corpus-wide opaque-id assignment + the merge into the
sealed rating mix is the cross-track/user-side assembly step.

## §6 Hand-over note — USER-side holds + recommendations

- **IRB (F22):** received before FIRST rater contact (USER-side).
- **Blindness screens + debriefs (§11):** per-rater, USER-side.
- **M-yield hold (C-M3) — IN BOLD:** **rating MUST NOT begin until the Step-4 M-yield audit stratum is
  merged into the sealed mix** (raters are debriefed/unblinded at close; a second round with the same
  raters is impossible), OR a two-round protocol is separately pre-registered.
- **Rater-time table (C-m4):** |S3|=0 ⇒ the rated set = calibration (50 target, 12 benign achieved) +
  M-yield when merged; the per-scenario time budget is the corpus-track/USER hand-over's table (the S3
  addition is the single `S3-BENIGN-01` case, ~1 rating unit).
- **RECOMMENDATION (not executed — needs a new plan + reviewer pass):** to lift the benign-side power, a
  dedicated degradation-shaped capture wave on the async SUT (by-design bounded delays / soft-rejects,
  cadence-conformant, re-probe-present AND re-probe-absent-benign shapes) would raise the benign pool
  toward the computed ≈42–43 and directly fix the §3 presence↮label tell. This deviates from the accepted
  plan's "no post-window re-revival" and is surfaced for the USER to decide.

## Carry-forward to P6

The S3 deliverable is assembly-ready as scoped: the scarcity datum (P4) + the single sealed benign
top-up + the documented shortfall/power-consequence + the cross-track/USER holds. Next: P6 RESULT-of-record
(the pre-committed claim sentences) + freeze §6 close-out row + the 3-cold review.
