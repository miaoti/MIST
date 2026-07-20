# [INTERNAL] Rater packet — administrator guide

**What this is.** The physical hand-over packet for the C3 rater study, assembled by
`assemble_packet.py` from the frozen protocol (`debug/a-main/c2c3/c3-rater-materials.md`, rev 3,
3-cold-reviewed). `ship/` is EVERYTHING a rater may see; `admin/` is administrator-only. Never
hand-edit `ship/` — change the frozen source (as a disclosed amendment) and re-run the script; the
script's leak gate re-scans every ship/ file for internal/tooling vocabulary on each run.

## Administration order (per the frozen protocol)
1. **Fill the compensation blanks** in `ship/01-brief.md` AND `ship/02-consent.md` (the
   `[USER DECISION U1]` markers: stipend-vs-credit, rate, expected hours) BEFORE anything is shown or
   signed. (IRB/exemption status per your filing — the protocol requires it before first participant
   contact.)
2. **Blindness screen** each candidate FIRST — `admin/screen-instrument.md` (§11). Objective checks +
   indirect self-report; any hit ⇒ ineligible (tell them "logistics"). Record outcomes. Screen ≥2
   extra passing candidates as the reserve pool.
3. **Consent** (`ship/02-consent.md`) with passing candidates.
4. **Eligibility exercise** — give the candidate `ship/` (brief `01`, rubric `03`, ballot format
   `04`, `eligibility/` incl. the two practice cases and the `docs-bundles/` reference). Grade
   against `admin/eligibility-answer-key.md`. Pass = both cases + both questions correct. Record.
5. **Then the raters WAIT for the rating corpus.** The measurement set (a single normalized mix,
   sized per the frozen protocol) is assembled later, once the corpus-production track delivers it;
   it will arrive as additional per-case `case.md`+`ballot.yaml` folders in the same format as the
   practice cases, with an assignment email whose independence text is the §5 excerpt. Raters must
   never discuss cases with each other from consent until the study closes.
6. **At study close only:** the funneled debrief — `admin/debrief.md` (§10a), answers recorded
   verbatim, then the close-out attestation. (The failure rule in the same file is admin-only.)

## Contents
- `ship/00-START-HERE.md` (rater orientation / reading order) · `ship/01-brief.md` (§1) ·
  `ship/02-consent.md` (§2; FILL U1) · `ship/03-rubric.md` (§3, rubric_version 3) · `ship/04-ballot.md` (§4)
- `ship/eligibility/instructions.md` + `spec-answers.yaml` (the 2 spec-reading answers) +
  `SCREEN-1/` + `SCREEN-2/` (rendered practice cases: SCREEN-1 order-create-lost → genuine,
  SCREEN-2 order-create-rejected → benign; both grounded in `ts-order-service`)
- `ship/docs-bundles/{trainticket,teastore,oteldemo,sockshop,bookinfo}/` — the pinned per-SUT
  reference bundles (trainticket = full upstream source, `BUNDLE-MANIFEST.md` inside, 149 Java files
  from FudanSELab commit `5526e505…`; the other four = pinned `*-openapi.yaml` skeleton + `BEHAVIOR.md`
  + `README.md`). Self-contained; all mechanically leak-scanned.
- `ship/cases/CASE-Q01..Q18/` — the **measurement instrument** (phase 2; see below)
- `admin/screen-instrument.md` (§11) · `admin/debrief.md` (§10) ·
  `admin/eligibility-answer-key.md` · `admin/eligibility-protocol-sec9.md` (the frozen §9 text) ·
  `admin/opaque-id-map.json` (the sealed opaque→true-case-id + label map — NEVER in a rater's copy)

## Phased delivery (do NOT zip all of `ship/` at once)
The packet is delivered in two phases, matching step 4→5 above:
- **Phase 1 — eligibility packet (at recruitment):** `01-brief`, `02-consent`, `03-rubric`,
  `04-ballot`, `eligibility/`, and `docs-bundles/`. This is what a candidate receives to consent +
  sit the eligibility exercise.
- **Phase 2 — measurement packet (after a candidate passes):** the `cases/CASE-Q01..Q18/` folders
  (same `case.md`+`ballot.yaml` format) + the assignment email (§5 independence text). Shipping the
  measurement cases with the eligibility packet would expose the instrument before assignment —
  keep them separate.
The 18-case `cases/` set is the **M-yield measurement instrument** (7 genuine / 11 benign), NOT the
§6 calibration round; do not size/skew-check it against the calibration-round minima. By construction
every one of the 18 has a norm derivable from its bundle, so the set contains **no *underspecified*
exemplar** — this is a disclosed characteristic, not a defect (the *underspecified* label stays
available to raters, and its reliability is exercised in the §6 calibration round + S3, not here). Do
NOT reveal this label distribution to raters.

## Regeneration vs rendered artifacts
`assemble_packet.py` regenerates the SCAFFOLDING (`01`–`04`, `eligibility/instructions.md`,
`eligibility/spec-answers.yaml`) from the frozen source and copies `docs-bundles/` from
`debug/a-main/benchmark/docs-bundles/` — never hand-edit those in `ship/`; change the source and
re-run (the script's leak gate re-scans on each run). The `cases/CASE-Qxx/` and `eligibility/SCREEN-*/`
folders are rendered by the B4 harness from the case + sidecar sources (blind opaque ids, banned-string
gate) and are NOT touched by `assemble_packet.py`.

## Disclosed rendering transforms (frozen source unchanged; applied by the script)
- §2 consent: internal section refs "(screening, §9/§11)" → "(the screening)"; the reviewed
  known-labels disclosure reworded "check calibration" → "quality checking" (meaning-preserving;
  keeps stratum vocabulary out of rater-facing text per §0).
- §3 rubric: the worked-examples heading's admin TODO clause (the authoring plan for real worked
  examples — rating-phase material, authored at corpus assembly and reviewed then) is replaced by a
  plain caveat heading; the caveat and the abstract example patterns render unchanged.
- §9: replaced in ship/ by purpose-written rater-facing instructions (the frozen §9 is an
  administrator protocol description; it is preserved at `admin/eligibility-protocol-sec9.md`).
