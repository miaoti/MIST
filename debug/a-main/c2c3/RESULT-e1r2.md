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
  Swagger-2.0). Response-status keys are quoted (`"200":`) so they are valid string keys.
  **[Corrected post-review, A-1/C-2:]** the existing `sockshop` exemplar's unquoted int
  keys (`200:`, 42 of them) are NOT "latent" — it outright FAILS the same strict validator
  (`TypeError`); the two E1 specs avoid the bug. Noted for the maintainer (not fixed — not
  this wave's file).
- **DoD (ii) coverage:** a mechanical grep gate over EVERY `entry_endpoint.path` AND
  `readback.locator` across all TeaStore+OTel case files = **fully covered**. The OTel
  checkout durable read-back is an `accounting.shipping` SQL row (no frontend GET); it is
  documented HONESTLY as an `externalDocs`/description SQL note, NOT a fabricated GET.
  **[Post-review, A-2/C-3:]** the gate is now a COMMITTED artifact —
  `debug/a-main/benchmark/e1_openapi_coverage_gate.py` (validation + coverage +
  normalization rules; re-run PASS).
- **DoD (iii/iv):** `info.license` Apache-2.0 + provenance `info.description` + request-body
  schemas for the writes. **[Clarified post-review, A-3:]** the corpus's TeaStore ENTRY
  write is the form-urlencoded webui `cartAction?confirm=Confirm` (modeled as formData
  parameters); `createOrder` is the INTERNAL persistence write it triggers (JSON body
  schema); OTel: `addCartItem`/`placeOrder` JSON bodies.
- **DoD (v) license audit:** `c2-license-audit.md` TeaStore+OTel rows updated to point at the
  authored specs; a new **"OpenAPI spec provenance"** subsection discloses the EXISTING set
  is HETEROGENEOUS (not laundered): `sockshop`+`boutique` authored-by-us, `bookinfo` = STOCK
  UPSTREAM Istio spec, `trainticket` = machine-GENERATED 3.0.3, `boutique` carries no in-file
  license. TeaStore+OTel join the authored-by-us set.
- **DoD (vi):** FILE_INDEX updated (2 new `openapi/` subsections in alphabetical slots).
- Named consumers (AUTHORING-only, NOT run here): the E1 two-tier baseline grid REST tools
  and the contract-invariant comparator arm (item 2.5.8).

## R2 — corpus-wide rater-facing narrative-leak audit + neutralization

Durable outputs: `debug/a-main/benchmark/b4/rater-sidecars/*.json` (25 neutralized, rev 3) ·
`neutralize_rater_sidecars.py` (the transform of record) · `rater-sidecars/AUDIT-r2.md`
(the leak-audit + read-through log + post-review corrections) · `b4/MANIFEST-r2.json`
(pre-seal manifest + content hash + per-case `rateability`, rebuilt by the committed
`b4/r2_manifest.py`; moved OUT of the sidecar dir post-review, B-8) · the hardened
`b4_harness.BANNED_STRINGS` + opaque-id guard + `test_b4_harness.py` (green).

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
- Final calibration draw = the R1d disclosed shortfall stands (decode-safe trap pool = 6,
  of which 5 C3-rateable, per the R1d record [aligned post-review, C-7ii] ≪ the ≥35 /
  ~40-43 obligations); size + pooled-κ basis are a scoring-time decision.
- Rater-packet worked examples = still generic patterns, blocked on the calibration draw.
- The collection-read-back per-endpoint rendering (finding 4) = gated Step-5 rater-cut.

## Out of scope (unchanged)
E1 grid EXECUTION; the contract-invariant arm RUN; M-yield; the final rater SEAL; the owed
2.5/E2 traced MIST discrimination run; the paper draft (user-gated on experiments-done).

---

# POST-HOC 3-COLD REVIEW → FOLDED (2026-07-14) — the DoD §5-4 gate

Reviewers: A (E1/OpenAPI) **ACCEPT, 0 blocking**; B (rater-blindness) **ACCEPT-WITH-FIXES,
5 BLOCKING**; C (scope/DoD/honesty) **ACCEPT-WITH-FIXES, 1 BLOCKING**. NONE reject.
Reconciliation: `REVIEW-E1R2-RESULT-RECONCILIATION.md` (local-only, gitignored). The
blocking set = ONE family: evidence-adequacy/faithfulness defects in the neutralizer's
merge/durable assembly + this wave's audit having certified exactly those renders — the
class the wave's own stop-rule ("surface, don't paper over") existed for.

**All blocking items FOLDED (neutralizer rev 3, re-rendered 25/25 0-leak, re-manifested):**
- [B-1] `otlc1`/`otlf2` label-encoding emails neutralized (`corpus-user@corpus.test`).
- [B-4 + C-1] OTel durable observations RE-KEYED on the acked `order_id` (the old
  `street_address='1 Corpus Way'` → `[]` render was COUNTERFACTUAL — both legs shared that
  address); row counts MACHINE-READ from `readback-psql.txt` (ack↔evidence id guard);
  the flagship lost case now renders its FULL permanence evidence (0 in-window → 0
  post-restore → 0 later still) instead of one synthetic t+2 s read; SQL probes render
  status "n/a" (no invented HTTP 200).
- [C-1a] Cross-phase reads are MARKED "verification read / later verification pass" with
  REAL intra-phase deltas + ROUND PLACEHOLDER cross-phase offsets, and the placeholder
  convention is DISCLOSED in AUDIT-r2 (real offsets unrecorded in the committed evidence).
- [B-3] bookinfo's decision signal restored (computed page descriptor carrying the visible
  "Ratings service is currently unavailable" message — was collapsed to a vacuous
  placeholder).
- [B-5] AUDIT finding-4 scope corrected: the 9 TT global-collection cases are un-rateable
  AS RENDERED (acting record ALWAYS beyond the window — gated Step-5 rater-cut), and
  `sockshop-shipping-swallowed-enqueue-001` has NO rendered discriminator (trace-only) —
  recorded per-case as `rateability` in the manifest (default: exclude from blind rating
  unless the seal ships disclosed white-box evidence).
- [B-2] TT admin cross-leg ACK-TEXT differential (fork-authored fault-leg strings ABSENT
  from the shipped upstream docs bundle → direction derivable) — DISCLOSED as a rendered
  confound (evidence not rewritten); keep-vs-exclude is a pre-seal decision.

**Also folded (non-blocking):** the committed coverage gate + manifest builder (A-2/C-3/
C-4); manifest moved out of the sidecar dir after a live glob-pickup demonstration (B-8);
`__main__` guard (B-7); ISO-T datetime masking (B-11); mechanical OPAQUE-ID guard in
`b4_harness.render()` + test (C-5 — strict-only; label vocabulary / true-id / banned tokens
in the opaque id now fail loud); tell-audit wording + the missing cadence row (B-12/C-6);
sockshop-validator + calibration-number + cartAction wording corrections in this RESULT
(A-1/C-2, C-7ii, A-3).

**NEW DEFECT SURFACED while folding C-5 (recorded, gated):** the SEALED S3 calibration
case was rendered with opaque id `S3-BENIGN-01` — its rater-facing TITLE carries "BENIGN"
(= the label; "benign" was never in `BANNED_STRINGS`). No rating has occurred → no harm
yet; flagged for a re-cut with a truly opaque id + re-seal at the gated Step-5 seal. The
new opaque-id guard makes the class structurally impossible going forward.
