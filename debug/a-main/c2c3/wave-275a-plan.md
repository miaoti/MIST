# Wave 2.75-A — MIST enablement: read-back modality bindings + new-SUT paired runs (rev 2)

**Status:** rev 2 — folds every BLOCKING + MAJOR from the 3-cold-review (all ACCEPT-WITH-FIXES;
`REVIEW-275A-{A-oracle,B-engineering,C-pc}.md` → `REVIEW-275A-RECONCILIATION.md`). Goes back to the
three reviewers for a confirmation pass; **executes only on unanimous CONFIRM-ACCEPT.** Gate opened
by the user 2026-07-10 ("MIST 启用+跑测"; scoped in `main-track-workflow-rules`). First MIST-tool-code
wave since the prep phase.

## 0. State (corrected against the actual code — rev-1 had two factual errors)
- **The read-back transport seam ALREADY EXISTS.** `DataIntegrityRuntime` exposes
  `installHttpOverride(Http)`; `g3/ShippingReadbackHttp` already routes a read-back through a
  non-SUT transport with the decision loop untouched. Rev 1's proposed new `ReadbackProbe` interface
  is redundant and is DROPPED (A-F1/B-F3).
- **Triples load from YAML, not Java.** `TargetTripleRegistry.load` is the production loader
  (`g3.triples.natural`/`g3.ship.triple` are file paths; used by `MistRunner`). Rev 1's "enablement
  is code-per-SUT" premise was wrong. New triples are authored in YAML (A-F7/B-F4).
- **The reviewed oracle is PAIRED.** `PairedFaultExecutor.evaluate` FIREs on the
  control-present/fault-absent differential and is gate-agnostic; the observe tier
  (`DataIntegrityObserveCheck`) needs `traceComplete()` (Jaeger) to reach `OBSERVED_COMPLETE_ABSENT`.
- **Honest oracle status of the two target cases (this reframes the whole wave — C-B1):**
  - `oteldemo-checkout-lost-001`: `tracetest_presence_oracle = flag` — a TRACE comparator ALREADY
    catches the async loss. MIST's read-back here is **CONCORDANT with presence, NOT discriminating.**
  - `teastore-order-maintenance-masked-001`: trace columns `not_applicable` (SUT trace-uninstrumented,
    Kieker-only). No trace comparator runs, so MIST's read-back is the **SOLE oracle**, and
    "beats trace-only" is VACUOUS here.
  - The MIST-only DISCRIMINATION win stays the TT fabricated-ack case (naive+presence both miss,
    read-back catches). It is NOT claimed for these two SUTs.
- **Live DB schema (gathered at reconciliation):** `accounting."order"` = `order_id` only
  (server-assigned); `accounting.shipping` carries CLIENT-SUPPLIED `street_address/city/state/zip`
  + `order_id`. ⇒ a request-derived unique key is available for OTel via a marker in `street_address`.

## 1. Objective (what this wave HONESTLY delivers)
BIND two new read-back modalities to MIST's durable-store oracle and produce MEASURED paired verdicts
on the captured pairs — reported for what they are, not as discrimination wins:
- **Principle (one, consistent — C-M3):** MIST's read-back probes the SUT's **durable
  system-of-record** (the honest "did the write survive to persistence" check, which IS the
  acked-but-lost fault class): TeaStore → the persistence `/rest/orders` JSON; OTel → the accounting
  Postgres. Disclosed as an INTERNAL durable-store probe (a stronger threat model than an end-user
  query), stated for BOTH SUTs.
- **TeaStore (PRIMARY / seam-proving pilot):** durable JSON read-back with the NATIVE request-supplied
  order marker; SYNCHRONOUS; MIST's isolation model fits directly. Result = **SOLE-oracle** datum
  (no tracing deployed) — FLAG on the masked-write fault leg, no_flag on control.
- **OTel-Demo (async follow):** SQL durable read-back keyed on a request-derived `street_address`
  marker; kafka-scale-0 fault; async. Result = **presence-CONCORDANCE** datum (MIST agrees with the
  already-flagging presence oracle) — FLAG on fault, no_flag on control.
- **Non-goals:** the E2 5-arm frontier (step 6), TT 2.5 trace instrumentation, any discrimination-win
  claim on these SUTs. MIST-side read-back only.

## 2. Engineering — reuse the existing override seam (minimum change)
Karpathy §2/§3: touch ZERO decision-loop code (that IS the regression guard); add only transports +
YAML.
- **2.1 Transports via `installHttpOverride`.** Each transport SYNTHESIZES the JSON collection the
  oracle already parses (so `probeVerdict`/membership is unchanged):
  - `SqlDurableReadback` — runs the case's SQL locator (kubectl-exec psql, argv + `otel`/`accounting`
    pinned as runbook constants, interpolated key WHITELISTED) and returns rows→`[{street_address}]`
    or `[]`. **Transport failure (non-2xx exec, parse error) → an ERROR/non-2xx record, NEVER a
    zero-count ABSENT** (A-F5/B-F6 — the "probe row vanished" analog; a broken probe must not
    false-FLAG).
  - `JsonDurableReadback` — HTTP GET the persistence `/rest/orders` (or a keyed variant), pass the
    JSON through; same error-vs-absence latching.
- **2.2 Registry.** Add a `readback.transport` field (http-json | sql | json-durable) to the triple
  schema + validation (mirror the existing VALUE_DELTA guards). New triples authored in YAML and
  loaded via `TargetTripleRegistry.load`. Transport is owned in ONE place (the registry/harness
  override, not duplicated on the Triple — B-F10).
- **2.3 No decision-loop edits.** MEMBERSHIP verdict, poll/gate/quiescence, error latching, N≥4,
  isolation — all reused verbatim. Regression proof = the existing mist-cli suites (incl. the TT/SS
  oracle tests) stay GREEN; new tests cover the transports only.

## 3. Per-SUT enablement (the real cost is the stimulus — B-F5)
Authored OpenAPI documents the write path (pre-registered authored-by-us) but does NOT execute on the
paired path; the multi-step stateful STIMULUS is a hand-written Java `Stimulus` impl (the g3 pattern).
Authoring cost = minutes for {OpenAPI + YAML triple + stimulus + transport}, recorded per SUT.
- **TeaStore (primary):** stimulus = login→add product 42→confirm with a UNIQUE request marker;
  read-back = `/rest/orders` JSON keyed on that marker; **harness-owned per-leg session** (A-F4 —
  bypass `MstAuthHandler`'s single cached cookie); maintenance-flag ON=fault/OFF=control via
  `POST /rest/generatedb/maintenance` (NEVER bare `GET /rest/generatedb` — DB wipe; NEVER scale the
  db — no PVC). If `/rest/orders` lacks a keyed GET, fall back to a DOM-scoped Orders-table HTML
  locator (A-F3) or a disclosed refutation — no silent re-scope.
- **OTel-Demo (follow):** stimulus = `POST /api/cart`→`POST /api/checkout` with a UNIQUE marker in
  `streetAddress`; read-back = `SELECT count(*) FROM accounting.shipping WHERE street_address='<marker>'`;
  fault = a bespoke kafka **scale-0** injector (the registry flag model doesn't cover scale — A-F8);
  **control-FIRST, single-toggle ordering** (all control up → all fault down → restore once at end)
  because of the producer-wedge; **async-landing floor ≥20 s** before the control leg reads absence
  (ShippingEnqueue precedent). Disclose the locator deviation from the capture's server-order_id key.
- Unique correlator per probe for N≥4 (A-F10). DoD (re-scoped, B-F11) = a PAIRED run whose Allure
  data-integrity section shows control-present/fault-absent on both SUTs.

## 4. Runs — paired, and what is measured (pre-registered NOW)
- **Mode = paired `evaluate()`**, N≥4 consecutive per leg, control-first. Pre-registered cells:
  `mist_readback = FLAG` (fault) / `no_flag` (control); `mist_bindable = true`.
- **The `mist_bindable` false→true flip is a DATED freeze §6 AMENDMENT, atomic with the measured run**
  (A-F9/B-F9/C-M1) — NOT pre-registration (the conventions pre-registered the flip only for
  `bindable-pending-eval`, not `false→true` T9 cells). The bool flips in the SAME commit as the
  verdict cell, preserving "verdict-valued mist cells appear only where MIST ran."
- **Circularity firewall (C-B2):** (i) independence — MIST keys on a request-derived marker in a
  DIFFERENT column (OTel: shipping.street_address) than the capture's server order_id, and it is a
  LIVE re-run of the stimulus, not a replay of the capture artifact; (ii) these self-concordant cells
  are their OWN reporting bucket and any recall figure is stated WITH and WITHOUT them; (iii) explicit
  "live re-run, not artifact replay" disclosure in the run record.
- **Reporting (C-B1):** OTel = read-back CONCORDANT-with-presence (report beside `presence=flag`,
  never "read-back beat the trace arm"); TeaStore = SOLE-oracle under the explicit "no tracing
  deployed" caveat. No discrimination claim.
- **Pre-registered SCIENTIFIC anti-findings as REPORTED outcomes (C-M5), not just engineering
  failure:** if a binding can't isolate soundly (e.g. OTel has no request-derived key — REFUTED by
  the schema, a key exists), or the read-back proves non-independent, or TeaStore's only surface is
  the ambiguous HTML → the cell STAYS `not_applicable`/T9 with a dated disclosure; that is a valid
  wave outcome (the wave-3a refutation discipline).

## 5. Discipline / freeze
- `main_track`; karpathy; no Co-Authored-By; no file deletion; FILE_INDEX + memory sync; per-item
  commits. OTel + TeaStore are UP (wave-3a close-out) — no redeploy; OTel kafka recovery runbook
  (rollout-restart on pod replacement) on standby.
- **Freeze §6 amendments (dated):** (a) TeaStore modality `api-get`-HTML → `api-get`-JSON durable
  `/rest/orders` (A-F13) with the internal-durable-store disclosure; (b) the two `mist_bindable`
  false→true flips, each atomic with its run; (c) the concordance / sole-oracle reporting convention
  for these cells (own bucket, recall with/without).
- Regression guard: decision-loop untouched; suites green; new transport unit tests
  (SQL rows→collection, JSON pass-through, **exec/HTTP failure→non-2xx not ABSENT**, key quoting/
  injection, per-leg isolation, an end-to-end probe→`evaluate` FIRE/NO_FIRE verdict test — B-F8).

## 6. Resolved questions (rev-1 §6 open items, now decided)
1. SQL transport = **kubectl-exec psql** (parity, no shaded driver, no PF to die on reboot; argv +
   constants pinned; failure→non-2xx) — B-F6 over JDBC.
2. Scope = **both SUTs, TeaStore primary** (native request-marker, synchronous, durable-JSON = lowest
   risk), OTel the async follow — reconciled from A (OTel-only) + B (TeaStore-cleaner).
3. `mist_bindable` flip bar = **atomic with a measured paired run + dated §6 amendment** (A-F9).
4. Enablement shape = **YAML triples** (loader exists) + Java stimulus/transport — the "bespoke vs
   config" dichotomy was false (A-F7/B-F4).
5. Mode = **paired** (observe can't FLAG a trace-uninstrumented SUT — A-F6/B-F1).
Residual for reviewers: is binding OTel's read-back to `accounting.shipping.street_address` (rather
than the capture's `order`.`order_id`) an acceptable principled deviation, or must the capture spec be
amended to match? (Rev 2's position: MIST uses the request-derived key by design; disclose, don't
rewrite the capture.)

## 7. File manifest (rev 2)
- MIST code: two transport impls (`SqlDurableReadback`, `JsonDurableReadback`) installed via the
  EXISTING `installHttpOverride`; `readback.transport` registry field + validation; two Java
  `Stimulus` impls (OTel, TeaStore); a bespoke kafka-scale injector; unit tests. NO `ReadbackProbe`
  interface; NO decision-loop edits.
- SUT assets: authored OpenAPI (OTel, TeaStore); two YAML triples; auth glue (TeaStore per-leg session).
- Corpus: the two positives + controls gain measured `mist_readback`/`mist_bindable` cells (atomic
  freeze §6 amendment).
- Docs: this plan → confirmation; a RESULT record per SUT run; FILE_INDEX + memory.

## 8. DoD
- Suites green (regression + new transport tests). Paired runs recorded N≥4/leg, control-first, with
  FLAG/no_flag measured — OR a dated refutation if a binding fails. `mist_bindable` flipped atomically.
- Concordance (OTel) / sole-oracle (TeaStore) framing in the RESULT record; recall stated with/without
  the self-concordant bucket; live-re-run disclosure. Authoring cost per SUT. Freeze §6 + README +
  FILE_INDEX + memory synced. Per-item commits on `main_track`.
