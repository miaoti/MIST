# R1d benign-power lift — GROUNDING NOTE (not yet a plan) — 2026-07-13

**Status:** grounding only. The full R1d plan awaits the R1c 3-cold-review sequencing verdict
(R1c review C is explicitly asked whether R1d should precede/fold-in). This note fixes the numbers so
the plan, when drafted, starts from solid ground. Prepared in parallel while R1c is in review.

## Why R1d is the BINDING constraint (all 3 R1b reviewers converged on this)
The headline C3 rater study's statistical power is bound by the BENIGN (S2) pool, not the S1 positive
count. R1b chased the non-binding side; R1d addresses the binding one.

## The demand (from the frozen calibration design)
- **Calibration size = max(30, 50 − |S3|)** (`c2-freeze.md` L207; `c3-rater-materials.md` §6 L250-253).
  S3 found **0** wild flags → |S3| = 0 → calibration = **max(30, 50) = 50 items**.
- **Benign-skewed ≥ 2:1** benign:genuine (F16) → of the 50, **~33-34 are benign**, ~16-17 genuine
  (genuine drawn from S1 positives, which we have).
- **Calibration cases are NOT reused as measurement cases** (`c3-rater-materials.md` L264-265). So the
  S2 benign pool must supply ~33 calibration benigns AND still leave benigns for the S2 MEASUREMENT
  (false-positive-trap) stratum.
- **Total degradation-shaped rateable-benign demand ≈ 42-43** (~33 calibration + ~10 S2-measurement).
  This is the number the R1b reviewers cited; verified here from the frozen formula.

## The supply (today)
- **Captured benign TRAPS in the case corpus = 4** (grep, label=negative AND named `*-benign`, controls
  excluded per "controls NEVER count"):
  `bookinfo-ratings-benign-001` (dependency-down, none-durable),
  `oteldemo-checkout-eventual-benign-001` (eventual-consistency, sql-probe),
  `TT-contacts-noop-modify-benign-001` (api-get),
  `TT-contacts-dedupe-benign-001` (api-get).
- The R1b reviewers' "≈12 rateable" likely folds in packaged-corpora benigns + survey-projected
  candidates; **R1d must produce a PRECISE supply survey** (do not ship an unreconciled benign count —
  the M6 discipline from R1c applies here too).
- Prior survey band (memory / R1 rev2 §3): ~19-24 candidate benign traps, ~15-20 "rateable"
  (degradation-shaped, decoder-safe). **If that ceiling is real, ~42-43 is UNREACHABLE by natural traps
  alone** — the same demand>supply wall R1b hit. See the open question below.

## The hard design constraint (decoder-safety — C-B3 / A-F10)
Calibration/S2 benigns must be **degradation-shaped ONLY** (slow-but-eventually-consistent,
retry-succeeded, transient-503-then-healed, dedupe/no-op, partial-then-healed) — **NEVER clean-present**
(a benign where the write simply succeeded cleanly), because a clean-present benign lets a rater decode
the stratum ("if it's clean it's benign"). R1 rev2 §3 already designed the shape floors:
write-acked-absent-then-present ≥8 (decoder → P(genuine|absent)=0.50), eventual-present ≥2. R1d extends
these to the ~42-43 scale.

## THE open question the R1d plan must resolve first (a supply survey, before any capture)
**Is the degradation-shaped benign supply ceiling actually ≥ 43?** Unlike masked-LOST-writes (S3-scarce
by nature), degradation-shaped benigns (transient latency/503/eventual-consistency/retry) are induced by
COMMON transient conditions and are plausibly far more abundant — each write-path SUT can likely yield
several shapes × several endpoints. R1d's Phase 0 = enumerate the achievable degradation shapes per SUT
(OTel eventual-consistency + transient-503; TeaStore maintenance-503 + retry; TT/SS designed-degradation
paths; Bookinfo/Boutique packaged) and PROJECT the ceiling. Two branches, both pre-registered:
- **ceiling ≥ 43** → capture to the floor; benign side no longer binding.
- **ceiling < 43** → disclose the shortfall (freeze §5 discipline) AND adjust: either shrink the
  calibration set toward the max(30, …) floor of 30 (re-deriving pooled-≥50 feasibility), or widen the
  reliability approach. NO padding with clean-present benigns (decoder hazard).

## Sequencing (to be confirmed by the R1c review)
Candidate order: R1c micro-widen (small, in review now) → R1d Phase 0 supply survey → R1d capture. If
R1c review C argues fold-in, R1d Phase 0 could run concurrently with R1c capture (different tenants).
E1 OpenAPI authoring is an independent parallel track (no tenant window).
