# [INTERNAL] Rater packet — administrator guide

**What this is.** The physical hand-over packet for the C3 rater study, assembled by
`assemble_packet.py` from the frozen protocol (`debug/a-main/c2c3/c3-rater-materials.md`, rev 3,
3-cold-reviewed). `ship/` is EVERYTHING a rater may see; `admin/` is administrator-only. Never
hand-edit `ship/` — change the frozen source (as a disclosed amendment) and re-run the script; the
script's leak gate re-scans every ship/ file for internal/tooling vocabulary on each run.

## Administration order (per the frozen protocol)
1. **Fill the compensation blanks** in `ship/02-consent.md` (the `[USER DECISION U1]` markers:
   stipend-vs-credit, rate, expected hours) BEFORE anything is signed. (IRB/exemption status per your
   filing — the protocol requires it before first participant contact.)
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
- `ship/01-brief.md` (§1) · `ship/02-consent.md` (§2; FILL U1) · `ship/03-rubric.md` (§3,
  rubric_version 3) · `ship/04-ballot.md` (§4)
- `ship/eligibility/instructions.md` + `SCREEN-G1/` + `SCREEN-B1/` (rendered practice cases)
- `ship/docs-bundles/trainticket/` — pinned upstream source bundle (`BUNDLE-MANIFEST.md` inside;
  149 Java files extracted from the public FudanSELab commit `5526e505…`; mechanically leak-scanned)
- `admin/screen-instrument.md` (§11) · `admin/debrief.md` (§10) ·
  `admin/eligibility-answer-key.md` · `admin/eligibility-protocol-sec9.md` (the frozen §9 text)

## Disclosed rendering transforms (frozen source unchanged; applied by the script)
- §2 consent: internal section refs "(screening, §9/§11)" → "(the screening)"; the reviewed
  known-labels disclosure reworded "check calibration" → "quality checking" (meaning-preserving;
  keeps stratum vocabulary out of rater-facing text per §0).
- §3 rubric: the admin TODO line about worked examples removed from the rendering (worked examples
  are rating-phase material, authored at corpus assembly on real cases and reviewed then).
- §9: replaced in ship/ by purpose-written rater-facing instructions (the frozen §9 is an
  administrator protocol description; it is preserved at `admin/eligibility-protocol-sec9.md`).
