# PLAN — rater-materials completion (fill the 25/33 gap) — awaiting 3-cold ALL-ACCEPT

**Date:** 2026-07-19 · Goal: bring the rater rating-corpus from 25/33 to complete, so the study
(the reframe's Cast-independent empirical HEADLINE + biggest lever) can launch once the USER-side
legal/scope decisions land. Split: **Part A = I execute (low-regret, unblocked)**; **Part B =
BLOCKED on USER decisions**. Awaiting ≥3 cold-reviewer ALL-ACCEPT before ANY execution, per the
standing rule.

## Current state (verified on disk 2026-07-19)
- Packet scaffolding READY: brief/consent/rubric-v3/ballot + admin (screen/eligibility/answer-key/
  debrief) + assembly script + calibration-rehearsal anchors + CASE-Q47 re-cut STAGED.
- 25 neutralized rev-3 sidecars cover 25/33 cases.
- **Gaps:** 8 cases have no sidecar (6 TT F-corpus F1/F8/F10/F11/F14/F20 + kafka + teastore-specified);
  doc bundles exist ONLY for TrainTicket (TeaStore/OTel-demo/SockShop/Bookinfo missing); consent has
  `[U1-RATE]`/`[U1-HOURS]` blanks + unfiled IRB; 4 seal MEMOs staged-not-executed.

## Part A — I EXECUTE (low-regret; nothing here presupposes an excludable case survives)
### A1. +7 missing sidecars (mirror the committed rev-3 neutralization EXACTLY)
- 6 TrainTicket F-corpus (F1/F8/F10/F11/F14/F20) + 1 kafka (oteldemo). Built from each case's
  committed B-m6 evidence (legs.log / capture) into the sidecar record format (request/response,
  t_rel_ms, method/path, bodies). **Neutralization protocol (BLOCKING, verbatim from the 25):**
  secrets redacted (`<redacted>` tokens/passwords), NO ground-truth label, NO MIST/oracle/hypothesis
  fingerprint, opaque case_id only, `mist_commit` pinned. A leak-gate grep (label words, "MIST",
  "oracle", "lost", "fault", "inject") must return 0 before commit.
- teastore-order-depdown-specified = NOT built (capture_status=specified, no capture exists → correctly
  un-rateable; it stays out of the rating set, disclosed — not a gap to fill).
- ⇒ rating corpus sidecars 25 → 32 (the 33rd is the specified case, principled-excluded).

### A2. 4 non-TT doc bundles (mirror the TrainTicket bundle: per-service `src/` + BUNDLE-MANIFEST + README)
- TeaStore / OTel-demo / SockShop / Bookinfo, each: version-pinned upstream source of ONLY the
  services a corpus case touches + the committed clean-room OpenAPI spec where one exists (E1:
  teastore + oteldemo already authored) + a README stating the pinned version_ref. CLEAN-ROOM:
  upstream source only, NO MIST artifacts, NO fault-injection code, NO fork branches — the rater
  grounds the LABEL against the genuine system, exactly as the TT bundle does. Leak-gate grep before
  commit.
- These bundles are ESSENTIAL regardless of the new cases — the EXISTING non-TT sidecars (teastore/
  oteldemo/sockshop/bookinfo) currently have NO bundle to ground against, so the study cannot rate
  any non-TT case without them.

### A3. rateability manifest refresh 6 → 33 (MANIFEST-r2 currently describes only 6)
- Regenerate the per-case rateability field (ok / trace-required / async-ineligible / specified-
  excluded) over all 33 so the analysis knows which cases are in the measurement set vs QC vs excluded.

### A4. CASE-Q47 staging finalize (prep only; the actual re-seal is Part B)
- Verify the staged re-cut (ballot.yaml + case.md, the S3-BENIGN-01 label-leak fix) is neutralization-
  clean and ready to swap; do NOT execute the seal (that is the USER's seal batch, B3).

## Part B — BLOCKED on USER decisions (I do NOT proceed on these)
- **B1. IRB filing** (consent §7) — user/institution.
- **B2. Compensation** — the `[U1-RATE]` / `[U1-HOURS]` consent blanks.
- **B3. Seal keep-vs-exclude decisions** (the 4 staged MEMOs): SS-swallowed keep-vs-exclude,
  TT-ack-text keep-vs-exclude, TT-per-endpoint-rendering, + the CASE-Q47 swap + RE-SEAL authorization.
  These DETERMINE the final rating set, so A-built materials for an excluded case simply go unused
  (low regret — the build is cheap and the exclusions are few).
- **B4. Scope confirm** — are the 5 corrupted cases (F8/F10/F11/F14/F20, MIST-out-of-scope) IN the
  rating set? The rater rates the LABEL (genuine/benign/underspecified), independent of MIST, so they
  CAN be rated as genuine defects; A1 builds their sidecars so the option stays open, but the final
  in/out is the user's.

## The reframe check (does the completed study still measure the right thing?)
YES. The channel-landscape reframe + E-ANOM do NOT change what the rater does — humans adjudicate
each case as a genuine loss / benign / underspecified against the pinned bundle. That is the
Cast-independent, competitor-independent empirical headline the reframe elevates. rubric v3 stands.

## Dependencies + sequencing
A1–A4 are all executable NOW and independent of Part B (low-regret: they build the substrate; B
only prunes/authorizes). Order: A2 bundles first (they unblock rating ANY non-TT case), then A1
sidecars, then A3 manifest, then A4 verify. Part B is surfaced to the user in parallel.

## Questions for the reviewers
1. Is Part A SOUND + COMPLETE, and is the low-regret claim right (does building any A-item risk being
   wasted or, worse, LOCKING IN a scope choice that should be the user's)?
2. The BLOCKING risk: does A1/A2's neutralization + clean-room protocol actually prevent label /
   hypothesis / MIST-fingerprint leakage into the rater's view? Any hole a rater could decode the
   grouping or the intended answer through? (esp. the F-corpus sidecars derived from fault legs.)
3. WORTH-IT / scope: should the 5 corrupted cases be in the rating set at all, or does including
   MIST-out-of-scope cases muddy the study's headline? Is building 4 full non-TT bundles the right
   effort, or is there a lighter grounding artifact that suffices?
