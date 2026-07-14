# RESULT — Wave E1+R2 (OpenAPI comparator specs + corpus rater-safety audit) — 2026-07-14

Plan: `wave-e1r2-plan.md` rev 2 (3-cold-reviewed, CLEARED; recon
`REVIEW-E1R2-PLAN-RECONCILIATION.md`). The low-risk finalization the user selected after
R1d: **no TT revival, no tenant teardown, no cluster ops.** DoD gate = this RESULT + a
post-hoc 3-cold review.

## Headline
Both deliverables landed. **E1**: the two MISSING OpenAPI comparator specs (TeaStore,
OTel-Demo) are authored clean-room, validate under a strict Swagger-2.0 validator, and cover
every corpus-bound endpoint. **R2**: the review's BLOCKING find — a **corpus-wide
rater-facing narrative-leak** (capture sidecars narrate the injection mechanism in probe
prose that `render()` emitted verbatim and the old `BANNED_STRINGS` missed) — is fixed:
25 canonical neutralized rater-sidecars render 0-leak through a hardened harness, a mandatory
manual read-through caught + fixed three further label-tell/mislabel defects the automated
scan cannot catch, and a fourth (collection-read-back legibility) is disclosed for the gated
seal. The rater SEAL stays OPEN (IRB / M-yield / calibration-gated).

---

## E1 — OpenAPI comparator specs (the 2 missing SUTs)

Authored `evaluation/suts/teastore/openapi/teastore-swagger.yaml` and
`evaluation/suts/oteldemo/openapi/oteldemo-swagger.yaml` — Swagger 2.0, Apache-2.0,
service-tagged, authored-by-us CLEAN-ROOM (freeze §6 R1 X5 two-actor) from the SUT source +
our corpus capture specs, copying ZERO upstream OpenAPI text (neither project ships one).

- **DoD (i) validate:** both PASS `openapi-spec-validator` 0.9.0 (offline, strict
  Swagger-2.0). Response-status keys are quoted (`"200":`) so they are valid string keys —
  the existing `sockshop` exemplar has the same latent int-key issue (unquoted `200:`),
  noted for the maintainer (not fixed — not this wave's file).
- **DoD (ii) coverage:** a mechanical grep gate over EVERY `entry_endpoint.path` AND
  `readback.locator` across all TeaStore+OTel case files = **fully covered**. The OTel
  checkout durable read-back is an `accounting.shipping` SQL row (no frontend GET); it is
  documented HONESTLY as an `externalDocs`/description SQL note, NOT a fabricated GET.
- **DoD (iii/iv):** `info.license` Apache-2.0 + provenance `info.description` + request-body
  schemas for the writes (TeaStore `createOrder`; OTel `addCartItem`/`placeOrder`).
- **DoD (v) license audit:** `c2-license-audit.md` TeaStore+OTel rows updated to point at the
  authored specs; a new **"OpenAPI spec provenance"** subsection discloses the EXISTING set
  is HETEROGENEOUS (not laundered): `sockshop`+`boutique` authored-by-us, `bookinfo` = STOCK
  UPSTREAM Istio spec, `trainticket` = machine-GENERATED 3.0.3, `boutique` carries no in-file
  license. TeaStore+OTel join the authored-by-us set.
- **DoD (vi):** FILE_INDEX updated (2 new `openapi/` subsections in alphabetical slots).
- Named consumers (AUTHORING-only, NOT run here): the E1 two-tier baseline grid REST tools
  and the contract-invariant comparator arm (item 2.5.8).

## R2 — corpus-wide rater-facing narrative-leak audit + neutralization

Durable outputs: `debug/a-main/benchmark/b4/rater-sidecars/*.json` (25 neutralized) ·
`neutralize_rater_sidecars.py` (the transform of record) · `rater-sidecars/AUDIT-r2.md`
(the leak-audit + read-through log) · `rater-sidecars/MANIFEST-r2.json` (pre-seal manifest +
content hash) · the hardened `b4_harness.BANNED_STRINGS` + `test_b4_harness.py` (green).

- **§2.2 harness hardened (strict-only):** `BANNED_STRINGS` extended with the
  injection-mechanism class (`meshsever`, `mesh-sever`, `severed`, `teardown`, `post-vs`,
  `post-drain`, `envoyfilter`, `virtualservice`, `scaled`, `scale to`, `toggle`, `drain`,
  `maintenance`, `read-back cap`, `epoch_ms`). `test_b4_harness.py` GREEN incl. a new
  `test_mechanism_narration_in_probe_fails_loud` proving the gate now catches un-neutralized
  probes. This only TIGHTENS the leak gate (no behavior change).
- **§2.1 neutralized sidecars (25):** probe → plain canonical locator; HTML bodies →
  computed presence descriptors (presence read from the REAL captured body, never hand-set);
  mechanism/epoch/HTML-chrome/`version_ref`-deploy-detail removed; split TeaStore transcripts
  merged; the OTel durable SQL read-back sourced from evidence. Clean-by-construction safety
  elision log = **0**. All 25 render **0-leak** through the hardened harness (blind opaque
  ids).
- **§2.3 mandatory manual read-through:** surfaced FOUR issues the enumerated scan cannot
  catch; **three fixed** (label-encoding markers `TSMWC1`/`Ctl`/`Flt`/`NoOp`/`TRACED` →
  neutral; order-items observation MISLABEL → dispatch by body shape; SQL probe/payload
  marker consistency), **one DISCLOSED** (TT admin global-collection read-backs may truncate
  off the acting record — per-endpoint acting-record rendering is gated Step-5 rater-cut
  assembly). Post-fix tell-scan = 0 label-tell tokens. Full detail: `AUDIT-r2.md`.
- **§2.4 de-risking dry-runs:** disjointness (partial: S1=19/S2=6, 10 pos/15 neg, 5 SUTs;
  `|S3|=0`, M-yield unmerged), 25/25 deterministic renders, tell-audit (no spurious
  single-label RENDERED feature; `readback_shape` perfectly predicts label but is a
  case-file META field the harness never renders — flagged not-for-blind-analysis).
- **C-F8 discharged:** the R1d residual (the `oteldemo-checkout-eventual-benign-002/003`
  rater-facing sidecars) is neutralized (mechanism + `epoch_ms` stripped) and renders clean.

## New structural findings surfaced (honest, per the stop-rule — not papered over)
1. The capture sidecars are inconsistent as rater-render inputs: TeaStore transcripts are
   SPLIT (write phase / read-back phase) and the OTel checkout durable read-back is NOT in
   the sidecar (it is SQL evidence). The neutralizer resolves both by explicit per-case
   assembly; the corpus's `provenance.readback_response` pointers remain heterogeneous
   (2 plain-text, 1 wrong-schema S3 bundle, 1 null) and are EVIDENCE pointers, not render
   inputs.
2. Capture markers systematically encoded the control/fault label (now neutralized).
3. `readback_shape` (added R1d) perfectly predicts the label because positives omit it —
   a META correlation, harmless because the harness allow-list never renders it, but flagged.
4. Collection read-back legibility (membership vs value vs count) is per-endpoint rater-cut
   work, gated to the seal.

## §3 disclosed gates (the seal stays OPEN — unchanged by this wave)
- IRB determination (checklist Step-5 §6 item 7) = USER-side, not received.
- M-yield merge (Step 4) = NOT run; a NAMED HOLD on rating.
- Final calibration draw = the R1d disclosed shortfall stands (rateable benign traps ≈ 4-8
  ≪ the ≥35 / calibration-50 obligations); size + pooled-κ basis are a scoring-time decision.
- Rater-packet worked examples = still generic patterns, blocked on the calibration draw.
- The collection-read-back per-endpoint rendering (finding 4) = gated Step-5 rater-cut.

## Out of scope (unchanged)
E1 grid EXECUTION; the contract-invariant arm RUN; M-yield; the final rater SEAL; the owed
2.5/E2 traced MIST discrimination run; the paper draft (user-gated on experiments-done).
