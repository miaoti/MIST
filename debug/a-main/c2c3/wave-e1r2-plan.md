# Wave E1+R2 — OpenAPI authoring + corpus rater-safety audit — rev 2 (folded, CLEARED)

**Date:** 2026-07-14 · Owner: main_track · Status: **rev 2 — 3-cold-reviewed (A/B/C all ACCEPT-WITH-FIXES,
NONE reject; B's 2 BLOCKING = the SAME narrative-leak, verified LIVE + folded). CLEARED for execution**
(recon `REVIEW-E1R2-PLAN-RECONCILIATION.md`; reviews `REVIEW-E1R2-PLAN-{A,B,C}.md` local-only). The DoD
`RESULT-e1r2 + post-hoc 3-cold review` is the downstream gate. Low-risk finalization the user selected after
R1d: **no TT revival, no tenant teardown, no cluster ops.**

**rev-2 headline change (the BLOCKING fold):** R2 is UPGRADED from a "-002/-003 marker-swap" to a
**corpus-wide rater-facing NARRATIVE-LEAK audit + neutralization** — the review found (and I verified LIVE)
that `b4_harness.render()` L97-99 emits the sidecar `probe` field VERBATIM into the rater case.md while
`_scan_banned` misses injection-mechanism prose, and ≈18 capture sidecars narrate their mechanism
("scaled 0", "maintenance toggle", "meshsever", "post-VS-teardown"). This is a real pre-rater corpus defect
this wave now fixes.

---

## §1 E1 — author the 2 MISSING OpenAPI specs (TeaStore + OTel-Demo)

**Grounded state (verified 2026-07-14, corrected per A-1/C-F3/B):** `evaluation/suts/{sockshop,trainticket,
bookinfo,boutique}/openapi/` exist; TeaStore + OTel-Demo have NO `openapi/` dir → the two to author (checklist
2.75 L217/L219). The existing four are **HETEROGENEOUS, NOT uniformly authored-by-us/Apache-2.0**: `sockshop`
= the clean authored-by-us Swagger-2.0 exemplar (follow it); `bookinfo` = the STOCK UPSTREAM Istio spec
(upstream-derived); `boutique` = no in-file license; `trainticket` = a generated OpenAPI 3.0.3 merged spec.
Author the two NEW specs cleanly (authored-by-us Swagger-2.0, Apache-2.0) — do NOT claim the existing set is
uniform, and (A-5) the §1.4 license note UPDATEs the existing `c2-license-audit.md` TeaStore+OTel rows +
DISCLOSES bookinfo's upstream provenance (no laundering).

- **§1.1 TeaStore** `evaluation/suts/teastore/openapi/teastore-swagger.yaml` — cover the corpus-bound write
  surface + read-backs: webui `POST …/loginAction`, `POST …/cartAction` (addToCart + `?confirm=Confirm`),
  read-backs `GET …/webui/profile`, and the REST API `GET/POST …/persistence/rest/orders`,
  **`GET …/rest/orderitems/order/{orderId}` + `GET …/rest/orders/user/{id}` (A-2 — bound by the orderitems
  cases)**, `/rest/cart`, `/rest/products`. Service-tag per owning service. Authored-by-us from route
  handlers + REST controllers (cited in `externalDocs`), NO upstream text copied.
- **§1.2 OTel-Demo** `evaluation/suts/oteldemo/openapi/oteldemo-swagger.yaml` — `POST /api/cart`,
  `POST /api/checkout` (the bound write), `GET /api/products/{id}`, `GET /api/cart`,
  `GET /api/recommendations`, `GET /api/shipping`. The `/api/checkout` durable read-back is the
  `accounting.shipping` SQL row (NOT an API GET) — documented as an `externalDocs`/description note, NOT a
  fabricated GET endpoint (A-verified as the highest-risk honesty item; keep it honest).
- **§1.3 Named consumers (AUTHORING-only this wave):** (a) the E1 two-tier baseline grid REST tools (Step 3b,
  ~160 h wave-runner — NOT run here) and (b) the contract-invariant comparator arm (Step 6 arm 5 / item
  2.5.8; blind assertions FROM the OpenAPI, `EXECUTION.md` L131-132 — NOT run here).
- **§1.4 DoD:** each spec (i) parses under a Swagger-2.0-capable OFFLINE validator (`openapi-spec-validator`
  or `prance` — C-F5); (ii) covers EVERY corpus-bound endpoint for that SUT — a mechanical grep gate over
  BOTH `entry_endpoint.path` AND `readback.locator` in the case files (A-3, so it catches read-backs);
  (iii) `info.license` Apache-2.0 + provenance `info.description` (authored-by-us from route handlers,
  upstream cited in `externalDocs`, clean-room per freeze §6 R1 **X5** — the two-actor clean-room, A-4);
  (iv) request-body schemas for the writes; (v) UPDATE `c2-license-audit.md` TeaStore+OTel rows + the
  bookinfo-upstream disclosure; (vi) FILE_INDEX.

## §2 R2 — corpus-wide rater-facing NARRATIVE-LEAK audit + neutralization (the BLOCKING-fold scope)

Render every RATED case rater-facing and prove 0 leaks — where "leak" now explicitly includes
injection-mechanism NARRATION, not just `BANNED_STRINGS`.

- **§2.0 Scope = the 25 CAPTURED rated cases (C-F1/B):** exclude `teastore-order-depdown-specified-001`
  (`capture_status: specified`, no sidecar). ADD the Step-5 §6 check-6 gate: every rated case
  `capture_status==captured`. Enumerate + normalize the 4 inconsistent `readback_response` conventions (B:
  2 plain-text evidence, 1 wrong-schema S3 bundle, 1 `null`) — each case gets a well-formed rater-facing
  sidecar or is flagged.
- **§2.1 NEUTRALIZE the rater-facing sidecars (the BLOCKING fix):** for each rated case produce a
  rater-facing sidecar whose `probe`/`body` text is a PLAIN read-back description (endpoint path or
  `SELECT … WHERE key=<opaque>`) with NO mechanism prose (`scaled`/`toggle`/`maintenance`/`meshsever`/
  `post-VS`/`teardown`/`drain`), NO `epoch_ms`, opaque marker only, relative `t_rel_ms`. Model on the clean
  S3 `w120-sidecar.json`. The capture-evidence sidecars stay as-is (provenance); these are clean
  derivatives. THIS discharges the R1d C-F8 residual for -002/-003 AND fixes the ≈18-sidecar corpus-wide
  class (teastore maintenance/meshsever, oteldemo meshsever/emptycart, etc.).
- **§2.2 Harden the leak detector (strict-only, disclosed):** extend `b4_harness.BANNED_STRINGS` with the
  mechanism-narration class (`meshsever`, `mesh-sever`, `teardown`, `post-vs`, `envoyfilter`, `scaled`,
  `toggle`, `drain`, `maintenance`, `read-back cap`). This ONLY tightens the gate (never loosens behavior);
  re-run `test_b4_harness.py` GREEN + confirm it now FAILS-loud on an un-neutralized sample. (A small
  defensive add, NOT a behavior change — distinct from the stop-rule's forbidden "harness hack".)
- **§2.3 MANDATORY MANUAL read-through (B1/C-F2):** after the automated render+scan passes, a human/agent
  reads EVERY rendered `case.md` for residual mechanism narration the scan can't enumerate (the automated
  gate is necessary-NOT-sufficient). Record the read-through as an audit log.
- **§2.4 De-risking dry runs (C-F4 — framed as such, NOT "assembly done"):** machine-disjointness (Step-5 §6
  check-5, PARTIAL at |S3|=0 + M-yield-unmerged — report which strata are populated); tell-audit
  (`readback_shape` × label confusion = the R1d bias-audit input, re-probe-cadence uniformity); corpus
  content hash + a PRE-seal manifest. These are RE-RUN at the real seal (post-M-yield/IRB); the durable R2
  outputs are the neutralized sidecars + the leak-audit log + the hardened harness.
- **§2.5 DoD:** 25 cases render clean (0 BANNED_STRINGS incl. the new terms + 0 manual-read-through hits);
  neutralized rater-facing sidecars committed; `test_b4_harness.py` green; disjointness/tell-audit/hash
  committed as dry-runs; C-F8 explicitly discharged.

## §3 What this wave does NOT do (DISCLOSED gates — the seal stays OPEN)

- **IRB determination** (Step-5 §6 **item 7**, B-corrected) = USER-side, not received.
- **M-yield merge** (Step 4) = NOT run; the M-yield stratum is a NAMED HOLD on rating.
- **Final calibration draw** = the R1d disclosed shortfall stands (rateable benign traps ≈ 5-6 ≪ ≥35 AND
  ~40-43); the size + pooled-κ(n≥50)-basis loss are a SCORING-time decision, not sealed here.
- **Rater-packet worked examples (B):** the shipped rubric's worked examples are GENERIC patterns, not the
  checklist-required REAL-calibration-case examples — itself blocked on the open calibration draw. The
  packet (28fbe3a) is not re-cut here; this gate is disclosed, not "done".

## §4 Sequencing
Both parts no-tenant + parallel-safe. §1 (OpenAPI) = a self-contained authoring job (may be a subagent per
SUT, explicit non-fable). §2 runs on committed corpus files + `b4_harness.py`. OTel/TeaStore stay UP, TT 0.

## §5 DoD + stop rules
1. §1: both specs authored, validate (offline), cover all bound endpoints (entry + readback), license/
   provenance clean + audit updated + bookinfo-upstream disclosed, indexed.
2. §2: 25 cases render 0-leak (automated incl. new terms + manual read-through), neutralized sidecars +
   hardened harness + audit log committed, dry-runs committed, C-F8 discharged.
3. `RESULT-e1r2.md` written with the §3 disclosed gates + the corpus-wide leak finding; freeze §6 note for
   the new conventions (the `BANNED_STRINGS` extension + the rater-facing-sidecar-neutralization convention).
4. **RESULT-e1r2 + 3-cold review** (goal-mode gate) + FILE_INDEX/memory synced.
- **Stop rules:** copying upstream OpenAPI text ⇒ STOP (author from route handlers, clean-room); a
  neutralization that would need a NON-trivial `b4_harness` BEHAVIOR change (beyond the strict-only
  BANNED_STRINGS add) ⇒ STOP + disclose (don't hack the harness under a finalization wave); a
  disjointness/tell-audit/leak check revealing a deeper corpus defect ⇒ STOP + surface (don't paper over —
  this is exactly how the narrative-leak was found).

## §6 Out of scope
E1 grid EXECUTION (Step 3b); the contract-invariant arm RUN (Step 6); M-yield (Step 4); the final
rater-ready SEAL (IRB/M-yield-gated); the owed 2.5/E2 traced MIST discrimination run; the paper draft
(user-gated on experiments-done).
