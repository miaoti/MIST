# REVIEW RECONCILIATION — Wave R1d (benign-power lift) rev 1 — 2026-07-13

**Plan:** `wave-r1d-benign-power-plan.md` rev 1 · **Reviewers (explicit models):** A = demand/statistical
(opus) · B = decoder-safety/feasibility (sonnet) · C = honesty/anti-relapse (opus).

## Verdicts
| Reviewer | Verdict | Blocking |
|---|---|---|
| A | ACCEPT-WITH-FIXES | 0 (1 MAJOR, 3 MINOR) |
| B | ACCEPT-WITH-FIXES | **4 BLOCKING** (3 MAJOR, 5 MINOR) — wants re-check before capture |
| C | ACCEPT-WITH-FIXES | 0 (6 MAJOR, 3 MINOR) |

**UNANIMOUS ACCEPT-WITH-FIXES**, but B's 4 BLOCKING findings + the material rewrite they force mean the
honest disposition is **fold into rev 2 → CONFIRMATION PASS (3 cold, focused) → execute only on
unanimous confirm** (the wave-3a precedent), NOT fold-and-run. Several rev-1 errors were mine (a
confabulated SockShop story, a mischaracterized TeaStore toggle, a mis-assigned decode-safety floor) —
capture must not start on an unverified rewrite.

## What the spine got RIGHT (keep)
- **Demand = ≥35** (S2 stratum floor) is CORRECT and A-verified against source; it SUBSUMES the ~33-34
  calibration benign draw (calibration draws FROM the S2 pool; mechanical FP runs don't consume cases).
  The grounding's ~42-43 double-counted (treated FP + calibration as disjoint pools) → a dated freeze
  note will reconcile the number; the ACTION (capture toward the floor + disclose shortfall) is
  unchanged either way. **Supply = 4** captured traps (dated-correct the S3 RESULT's "12").
- The **structural disanalogy to the rejected positive waves HOLDS** (C): decode-safety *requires*
  benigns nature structurally cannot supply, so inducing them is genuine construction — but ONLY up to
  the decode-safety minimum (above that, inducing volume-to-35 is padding-in-a-benign-hat).

## BLOCKING fixes (B — verified against render code + the real w120 artifact)
1. **Decode-safety was mis-designed.** `b4_harness.render()` renders EVERY observation verbatim (no cap
   /truncation); the real rater-facing `w120-sidecar.json` ends in a **PRESENT** observation (t+328s) →
   the rater sees the write LANDED (late) → "genuine" is foreclosed on its face. So eventual-present
   (S-A) benigns do NOT defuse "absent⇒genuine"; they populate the already-disclosed **present⇒benign**
   tell bucket. **The substrate's ≥8 floor is on `write-acked-absent`** (permanently-absent
   soft-rejects/dedupe — benign by ack-body-tell or documented semantics, render ABSENT like a genuine
   loss → THESE defuse the decode), with eventual-present floored at ≥2. **Retarget the ≥8 onto
   write-acked-absent; cap eventual-present at ≥2.** Either strip the re-probe-PRESENT observation from
   the rater-facing sidecar for the defuser shapes, OR drop the "defuses" claim and inherit the
   substrate's honest framing (presence⇒benign is a DISCLOSED structural tell, detected by the
   known-label bias-audit — not defused).
2. **Drop the SockShop S-F repurposing (confabulated).** S3 had 3 SUTs (SockShop never in it); the "FP
   storm" I cited is a fixed G3-era MIST HATEOAS parsing bug (`g3-sut2-fp-probe-result.md`, commits
   0a16255/a8d7d32), not a draining-queue phenomenon; and the shipping-enqueue site is ALREADY a
   captured S1 positive with `mist_readback=not_applicable` (no read-back surface to observe
   absent-then-present). Drop it.
3. **Drop TeaStore maintenance-503 (confusable).** The existing S1 positive measured maintenance ON =
   201/body-`-1`/silent (a MASKED SUCCESS — the flagship mechanism), NOT an "honest 503". A
   maintenance-based benign duplicates/conflates the positive. Drop.
4. **Drop TeaStore recommender cold-start (refuted).** Already tested + refuted as not-user-visible
   (`c2-depth-survey.md`). Do not re-spend a window rediscovering it.

## MAJOR fixes (A + C)
- **Calibration: NO "shrink to 30".** At |S3|=0, `max(30,50−0)=50` MANDATES 50; pooled=calibration so
  pooled-≥50 REQUIRES calibration=50; "30" isn't a legal floor here and M-yield joins measurement-κ not
  pooled-κ (A+C agree). **Replace with the already-frozen honest version (freeze L309/L306): run the
  largest calibration the achieved decode-safe benign supply permits; any calibration < 50 at |S3|=0 is
  a DISCLOSED shortfall (not a formula floor), with the pooled-κ(n≥50)-basis loss + power consequence
  disclosed.** Do NOT pre-commit 30-vs-50; let the achieved supply set it.
- **RE-SCOPE from "capture toward 35" to "achieve decode-safety MINIMUM + disclose the ≥35 shortfall"**
  (C; the shortfall is ALREADY pre-registered, freeze §5 + the R1 row "≥35 structurally unreachable").
  Lead with the disclosure branch; demote volume-to-35. Revised size ≈ **~8-10 captures** (≥6 more
  write-acked-absent soft-rejects beyond the 2 dedupe/noop we have + ~1 more eventual-present beyond
  w120), NOT 31.
- **Per-SUT / per-shape hard CEILINGS that can actually fire** (C) + a mechanism-diversity check (avoid
  a `dependency-down` monoculture — the benign twin of the all-`flag` relapse).
- **Ground induced-degradation MAGNITUDES in documented SLOs/p99s**, disclose per case, report FP as a
  sensitivity band over magnitude (C — else MIST's S2 FP rate is a knob artifact).
- **Framing (C):** pin an S2-stratum sentence ("constructed FP-trap stratum, parallel to the constructed
  S1 positives; natural-prevalence claims cite S3 only; the induced majority is disclosed"). And frame
  the **MIST-FP-on-eventual-present as a documented LIMITATION** — CONFIRMED vs source (`reProbe` is an
  S3-hunt-only accessor; the product observe path is single-shot-timeout → MIST fires TIMEOUT_ABSENT on
  absent-at-cap, so it cannot distinguish eventual-consistency-beyond-cap from loss). This is honest and
  paper-valuable (MIST's precision splits: fires on eventual-present traps = a known read-back-oracle
  limit; correctly no-fires on dedupe/no-op/soft-reject) — surface it, don't bury it.

## MINOR fixes
- R8 (tell-free-natural floor) is a POSITIVE floor — remove from the benign §6 (A/C-adjacent, B-M).
- Pin the demand/supply numbers in Phase 0 with the justification written out (the M6 discipline).
- Marker-salt is TT-ONLY (`s3-p0-pins.md` §1); verify key-uniqueness for any new SUT before assuming.
- Cross-reference the dated cadence-extension pin (SS/Bookinfo/Boutique) before those captures.
- Packaged FP corpora (Bookinfo/Boutique ≤2) are EXCLUDED from C3 rateable supply — carve them out of
  the ~33 calibration arithmetic.
- Record the S-A…S-F / three-way shape taxonomy in a STRUCTURED field (not free-text) so the
  shape-polarity census is mechanically computable.
- w120 is `natural`; a new induced OTel eventual capture is `by-injection` — count separately.
- E1 OpenAPI DoD must NAME the consuming comparator arm(s).

## Phase 0 = the verification B wants (the confabulation lesson)
Rev 2 must NOT pre-assert specific candidates I haven't verified. Phase 0 = a LIVE per-SUT survey that
DISCOVERS + verifies the achievable decode-safe benign shapes (starting from the substrate's known-good
families: write-acked-absent soft-rejects/dedupe; the OTel eventual-present; TeaStore
persistence-retry-heal IF real — the survey says TeaStore "does NOT gracefully degrade… few masked
benign traps", so verify cheaply before counting), pins the numbers, then captures only the verified.

## Disposition
Fold ALL of the above into **rev 2** → **CONFIRMATION PASS** (re-dispatch the 3 reviewers, focused: did
rev 2 fold the fixes + is the decode-safety redesign sound?) → on unanimous confirm, execute Phase 0
then the (much smaller, ~8-10-case) capture batches. This is the binding critical-path wave; getting the
decode-safety design right (B-1) is worth the confirmation cycle.
