# RECONCILIATION — rater-completion plan, 3-cold review (2026-07-19)

**Verdict: UNANIMOUS ACCEPT-WITH-CHANGES**, but the changes RESHAPE the plan and surface a
material truth + two user decisions. No reviewer rejected; none gave a clean accept.

## The bombshell (3/3 convergent): the human-rateable set is ~14, not 32/33 — and completing sidecars does NOT fix it
Per `MANIFEST-r2.json` `rateability`, the BLIND-RATEABLE set is **~14 cases = 3 positives (ALL
TeaStore) + 11 benign**. The rest are gated OUT of clean rating by construction:
- most TT positives/controls = `tt-collection-truncation-gated` (the /account global-collection
  truncation — and F1 inherits the SAME gate, so it is NOT the clean freebie the plan assumed);
- the flagship `oteldemo-checkout-lost` = `async-no-bound` ⇒ rubric-`underspecified`;
- SS-swallowed = `trace-required`; kafka = async-ineligible.
⇒ The plan's "+7 sidecars → 32/33 completeness" goal is MIS-FRAMED: the 7 new sidecars add
**~0 clean positives** to the headline. Completing the substrate is cheap and fine, but it does
NOT strengthen the study.

## The 5 corrupted cases (F8/F10/F11/F14/F20) → OUT of the primary rating set (3/3)
Rubric v3 defines `genuine` = a write that did NOT land (absence); the corrupted cases are
present-but-WRONG. Rating them measures rubric-elasticity, is orthogonal to the loss-variety
landscape, and silently poisons agreement/κ. Keep them in the C2 ARTIFACT ("benchmark broader than
the tool"); do NOT put them in the human study unless the rubric is extended 4-way (a scope
expansion that is the USER's call and NOT worth opening before Oct 2). ⇒ A1's corrupted sidecars =
DROPPED.

## The real positive-stratum lever is a USER decision, not A1 (3/3)
The only thing that adds CLEAN positives is the **Part-B TT re-capture decision** — it un-gates
~5 truncation-gated TT positives AND adds a 2nd positive SUT (today the 3 clean positives are all
TeaStore). This is higher value than every A-item. The rater study's positive stratum stays THIN
(3 sites, 1 SUT, benign-skewed 11:3) until this is done ⇒ headline must be an agreement coefficient
(AC1) not κ, disclosed.

## Blocking neutralization/pipeline holes the plan under-specified (R1+R2)
1. **Leak-gate insufficient**: add `corrupt`/`faultmode`/`skew` + ~11 pattern groups; raw
   `legs.log`/`fault.log` are saturated (`LEG=CTRL/FLT`, `mode=corrupt/lost`, `submitted_/
   persisted_documentType`, `search_price/order_price`, `fXCTRL/fXFLT`, kafka `LOST`/`lost=19/N=20`/
   `ksXf`). Do NOT over-ban `drawBack` (legit refund method).
2. **Opaque-id re-key missing**: the rater artifact is the b4_harness-RENDERED `case.md`, not the
   JSON — new case_ids leak; route all through the opaque-id guard + run the gate on the RENDER.
3. **Evidence-format mismatch**: the neutralizer consumes structured `captures/*/sidecar.json`, but
   the 7 new cases only have raw `cset/*/legs.log` (0 structured sidecars) ⇒ "mirror EXACTLY" needs a
   raw→structured step first; corrupted + kafka need NEW render modes.
4. **A3 must hand-set rateability** (kafka→ineligible, F1→truncation-gated, corrupted→scope-out),
   else `r2_manifest.py` defaults unlisted to `"ok"` = silent inclusion.
5. **A2 over-built**: LIGHT bundle (OpenAPI + per-endpoint behavior note + targeted source snippets +
   version pin), NOT full source trees; MUST add `ts-basic-service` upstream-clean for F14; patch
   `assemble_packet.py` (hardcodes `trainticket` L130-133; ship gate skips `docs-bundles` L138).
6. **De-pair values**: fault-leg-only sidecars, never ship the matched control leg beside it (F14
   shares `trip=D1345`/`22.5`; the OTel `1 Corpus Way` shared-value mistake again); never annotate
   the read-back with the expected/"wrong" marker.
7. **No benign calibration added**: all 7 new cases are positives ⇒ worsens the ≥2:1 benign-skew /
   calibration floor.

## The reconciled rev-2 shape (what this becomes)
- **A2 (LIGHT non-TT bundles + ts-basic-service + assemble_packet patch)** = the only real
  critical-path build (unblocks rating the 3 TeaStore positives + non-TT benigns). Keep.
- **A3 (manifest → 33 with HAND-SET rateability)** = do FIRST; it defines the honest ~14 launch set.
- **A1** = drop the 5 corrupted; F1+kafka are gated (add ~0 clean positives) ⇒ background/artifact-
  only, low priority.
- **Missing pipeline** (render, opaque-id, raw→structured, expanded leak-gate) = must be added before
  any sidecar ships.
- **A4** (CASE-Q47 verify) = fine.

## Why this goes back to the USER (not straight to execution)
The review changed the plan's PREMISE: the goal is not "complete to 32/33" (that adds nothing) but
"launch the honest ~14-set, and the only real positive lever is YOUR TT re-capture decision." Two
things are the user's: (1) confirm corrupted-OUT (or accept a rubric-4-way expansion, not advised
pre-Oct-2); (2) the TT re-capture that alone thickens the positive stratum — and, given the thin
stratum, whether to launch the rater study now or gate it on that re-capture.
