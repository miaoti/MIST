# Wave E1+R2 — OpenAPI authoring + corpus assembly PREP — rev 1 (DRAFT, pre-review)

**Date:** 2026-07-14 · Owner: main_track · Status: **DRAFT — needs 3-cold review (all ACCEPT) before
execution** (goal-mode). Low-risk finalization leg the user selected (AskUserQuestion 2026-07-14) after R1d
closed: **no TT revival, no tenant teardown, no fragile cluster ops.** Both parts are no-tenant authoring /
machine-check work.

**Provenance:** the R1d rev-2.1 plan §6 named "E1 OpenAPI (parallel, no tenant window)" as the finalization
track; the corpus-assembly entry gate is `step2-execution-checklist.md` Step-5 §6 (8+1 checks). This wave
does the AUTONOMOUS, machine-checkable subset and DISCLOSES every user/pending gate — it is NOT a final
rater-ready seal.

---

## §1 E1 — author the 2 MISSING OpenAPI specs (TeaStore + OTel-Demo)

**Grounded state (verified 2026-07-14):** `evaluation/suts/{sockshop,trainticket,bookinfo,boutique}/openapi/`
ALREADY exist (Swagger 2.0 / OpenAPI, one file/SUT, full front-end write surface, service-tagged,
provenance-described in `info.description`, Apache-2.0). **MISSING = TeaStore + OTel-Demo** — exactly the two
`step2-execution-checklist.md` lines 217-221 call for ("author OpenAPI, pre-registered as authored-by-us").

- **§1.1 TeaStore** `evaluation/suts/teastore/openapi/teastore-swagger.yaml` — cover the corpus-bound write
  surface + read-backs: webui `POST /tools.descartes.teastore.webui/loginAction`,
  `POST …/cartAction` (addToCart + `?confirm=Confirm` order-place), read-backs `GET …/webui/profile`
  (order history) + the REST API `GET/POST /tools.descartes.teastore.persistence/rest/orders`,
  `/rest/cart`, `/rest/products` (the 2.75-A `JsonDurableReadback` surface). Service-tag per owning
  service (webui/auth/persistence/image/recommender). Authored-by-us (route handlers + REST controllers,
  cited in `externalDocs`). License Apache-2.0.
- **§1.2 OTel-Demo** `evaluation/suts/oteldemo/openapi/oteldemo-swagger.yaml` — cover the frontend-proxy REST
  write surface + reads: `POST /api/cart`, `POST /api/checkout` (the bound write), `GET /api/products/{id}`,
  `GET /api/cart`, `GET /api/recommendations`, `GET /api/shipping`. Note the async caveat in the
  description: the `/api/checkout` durable read-back is the `accounting.shipping` SQL row (NOT an API GET) —
  documented as an `externalDocs` note, not a fabricated GET endpoint. Authored-by-us (frontend-proxy +
  checkout `main.go` route shapes). License Apache-2.0.
- **§1.3 Named consumers (why these specs exist — checklist cross-ref):** (a) the **E1 two-tier baseline
  grid** REST test-generation tools (Step 3b — Morest/RestTestGen/AutoRestTest/…; they consume the OpenAPI
  as input) and (b) the **contract-invariant comparator arm** (Step 6 arm 5 / item 2.5.8; blind assertions
  derived FROM the OpenAPI, `EXECUTION.md` L131). This wave AUTHORS the specs only; the grid EXECUTION
  (~160 h, wave-runner) + the arm run are downstream, NOT here.
- **§1.4 DoD:** each spec (i) parses under an OpenAPI/Swagger validator (offline linter); (ii) covers EVERY
  corpus-bound entry endpoint for that SUT (cross-checked vs the case files — a mechanical grep gate);
  (iii) carries `info.license` Apache-2.0 + a provenance `info.description` (authored-by-us from route
  handlers, upstream cited in `externalDocs`, NO code copied — the R1 X6 clean-room + license conduct); (iv)
  request-body schemas for the write ops; (v) FILE_INDEX + a dated `c2-license-audit.md` note (authored-by-us
  provenance, so no upstream-license entanglement).

## §2 R2 — corpus assembly PREP (machine-checkable subset only)

Run the machine-checkable half of the Step-5 §6 entry gate on the current 26-case corpus, producing a
DISCLOSED-PARTIAL assembly report — NOT a sealed rater-ready corpus (§3 lists the gates that block the seal).

- **§2.1 Rater-facing render + 0 BANNED_STRINGS (DISCHARGES the R1d C-F8 residual):** run all 26 cases +
  their sidecars through `b4/b4_harness.py` render → verify 0 BANNED_STRINGS and opaque-id only. THIS wave
  discharges the owed R1d C-F8 item: the -002/-003 induced-eventual sidecars use raw markers (`r1dev*` — a
  wave-label leak); assign opaque ids + re-key the marker in the rater-facing render (the corpus-wide
  ASSEMBLY step the R1d RESULT deferred here). GROUNDED 2026-07-14: `b4_harness` `BANNED_STRINGS` does NOT
  include `r1dev`, but `ABSOLUTE_TIME_KEYS=/epoch|timestamp|…/` guards absolute time AND the evidence
  sidecars carry `epoch_ms …` inside the probe strings; `render(…, opaque_id, …)` takes the opaque id as an
  ASSEMBLY INPUT (never mints it). So the discharge = produce RATER-CLEAN derivative sidecars (opaque marker
  substituted for `r1dev*`, relative `t_rel_ms` only, no `epoch_ms`) rendered under an assigned opaque id;
  the capture-evidence sidecars stay as-is (provenance). Verify the eventual-present PRESENT-at-re-probe observation
  renders (the disclosed present⇒benign tell, per R1d §9).
- **§2.2 Machine-disjointness (Step-5 §6 check 5):** verify calibration ∩ S3 ∩ M-yield-audit ∩ eligibility =
  ∅ by true case-id. At |S3|=0 + M-yield-not-merged this is a partial check (report which strata are
  populated vs empty), but the machine invariant (no id in two strata) is checkable NOW.
- **§2.3 Tell-audit (Step-5 §6):** the cross-strata shape/timestamp/provenance uniformity audit — compute
  the `readback_shape` × label confusion (the R1d bias-audit input, F17), the re-probe-cadence uniformity
  (300 s across eventual-present cases), and the timestamp/marker-grammar uniformity. Report the known
  present⇒benign + body-tell⇒benign decode directions as DISCLOSED (not defused).
- **§2.4 Corpus-hash freeze (Step-5 §6):** compute + record a corpus content hash over the 26 validated
  cases (a reproducibility pin), and a MANIFEST listing every case + its sha256. NOT the final SEALED
  manifest (that needs the rubric-version + IRB + M-yield — §3), a PRE-seal snapshot.
- **§2.5 DoD:** render clean (0 BANNED_STRINGS, verified via `test_b4_harness.py` + a fresh run);
  disjointness report (∅ where populated); tell-audit report committed; corpus-hash + pre-seal manifest
  committed; the C-F8 residual explicitly discharged (or, if the opaque re-key needs a harness change,
  disclosed as still-owed with the exact reason).

## §3 What R2 does NOT do (DISCLOSED gates — the seal stays OPEN)

To avoid over-claiming (the R1d honesty bar), this wave does NOT and CANNOT produce a final rater-ready seal:
- **IRB determination** (Step-5 §6 check 8) = USER-side, not received.
- **M-yield merge** (Step 4) = NOT run; the M-yield stratum is a NAMED HOLD on rating. The assembly is
  calibration + S1/S2 only, with M-yield disclosed-pending.
- **Final calibration draw** = the R1d disclosed shortfall stands (decode-safe rateable benign traps ≈ 5-6 ≪
  the ≥35 / ~40-43 obligations); the calibration is run at the largest achievable size with the shortfall +
  pooled-κ(n≥50)-basis loss disclosed — a SCORING-time decision, not sealed here.
- **Blindness-screen + §10 debrief records + rubric-version in the seal** = rater-contact artifacts, user-
  gated. The rater packet (rev ≥3) is already shipped (28fbe3a); this wave does not re-cut it.

## §4 Sequencing
Both parts are no-tenant + parallel-safe. §1 (OpenAPI) is a self-contained authoring job (optionally a
subagent per SUT with explicit non-fable model). §2 (assembly prep) runs on the committed corpus files +
`b4_harness.py`. No cluster ops, no RAM pressure, OTel/TeaStore stay UP, TT stays 0.

## §5 DoD + stop rules
1. §1: both specs authored, validate, cover all corpus-bound endpoints, license/provenance clean, indexed.
2. §2: render clean + C-F8 discharged (or disclosed-owed), disjointness + tell-audit + hash committed.
3. §3 gates DISCLOSED in a RESULT-e1r2.md; freeze §6 note if any convention is added (e.g. an `openapi`
   provenance field or the opaque-id re-key convention).
4. RESULT-e1r2 + 3-cold review (the goal-mode gate) + FILE_INDEX/memory synced.
- **Stop rules:** if authoring a spec requires copying upstream OpenAPI text ⇒ STOP (license — author from
  route handlers only, clean-room); if the opaque re-key needs a non-trivial `b4_harness` change ⇒ disclose
  C-F8 as still-owed (do not hack the harness under a finalization wave without its own review); if a
  disjointness/tell-audit check reveals a real corpus defect ⇒ STOP + surface (do not paper over).

## §6 Out of scope
The E1 grid EXECUTION (Step 3b ~160 h wave-runner); the contract-invariant arm RUN (Step 6); M-yield (Step
4); the final rater-ready SEAL (IRB/M-yield-gated); the owed 2.5/E2 traced MIST discrimination run; the
paper draft (user-gated on experiments-done). This wave is deliberately the low-risk finalization subset.
