# Wave 2.75-A — review reconciliation (3 cold reviewers → rev 2 disposition)

**Inputs:** `REVIEW-275A-A-oracle.md`, `REVIEW-275A-B-engineering.md`, `REVIEW-275A-C-pc.md`.
**Verdicts:** all three **ACCEPT-WITH-FIXES**. No REJECT. The enablement *work* is sound; the plan's
*framing, mode, and OTel key* were wrong and are corrected here. Rev 2 folds every BLOCKING + MAJOR.

**Live schema evidence gathered during reconciliation (settles A-F2/B-F2/C-B3):**
`accounting."order"` has exactly ONE column — `order_id text` (server-assigned UUID = the forbidden
key). `accounting.shipping` = `shipping_tracking_id, shipping_cost_*, street_address, city, state,
country, zip_code, order_id` — the address fields are CLIENT-SUPPLIED (from the checkout request).
`accounting.orderitem` = `item_cost_*, product_id, quantity, order_id`. ⇒ a request-derived unique
key IS available: plant a unique marker in the checkout **address** (lands in
`accounting.shipping.street_address`) and key the read-back there — no dependence on the server orderId.

## Convergent findings → disposition

### BLOCKING (all fold into rev 2)
- **OTel key is isolation-unsound (A-F2 = B-F2 = C-B3).** The captured read-back keys on the
  server-assigned `order_id` read from the ack; MIST's contract forbids reading the correlation key
  from the response (`DataIntegrityRuntime` L36–40; `freshen` strips server ids L776). A bare
  `count(*)` makes BOTH legs PRESENT ⇒ NO_FIRE ⇒ miss. **FOLD:** the stimulus plants a unique
  request-derived marker in the checkout `streetAddress`; the read-back is
  `SELECT count(*) FROM accounting.shipping WHERE street_address = '<marker>'` (MEMBERSHIP,
  request-derived, unique per run). Disclose the deviation from the capture's order_id locator (the
  capture used the server id for convenience; MIST's principled binding uses the request-derived key).
- **Execution mode = PAIRED, not observe (A-F6 = B-F1).** Observe's defect tier needs
  `traceComplete()` (Jaeger); TeaStore is trace-uninstrumented and OTel's entry trace says nothing
  about the async downstream ⇒ writes stay `TIMEOUT_ABSENT` = UNCONFIRMED, never LOST. The paired
  `evaluate/verdict` path is gate-agnostic (fires on control-present/fault-absent) and is REQUIRED.
  **FOLD:** paired for both SUTs; the "observe-mode Allure on BOTH legs" DoD was self-contradictory
  (observe is single-leg) — re-scoped to the paired data-integrity section.
- **Discrimination overclaim (C-B1).** Neither new case is a MIST-only win: on OTel
  `tracetest_presence_oracle = flag` (presence already catches the async loss) ⇒ read-back is
  **CONCORDANT**, not discriminating; on TeaStore the trace columns are `not_applicable` because the
  SUT is trace-uninstrumented ⇒ "beats trace-only" is **VACUOUS**. **FOLD:** rev 2 reframes the
  objective — this wave proves MIST's read-back BINDS two new modalities and reports OTel as a
  presence-concordance datum and TeaStore as a sole-oracle datum. The MIST-only discrimination win
  stays the TT fabricated-ack case; it is NOT claimed here.

### MAJOR (fold)
- **Don't build `ReadbackProbe`; reuse `installHttpOverride` (A-F1 = B-F3).** The override seam +
  `ShippingReadbackHttp` already route a read-back at a non-SUT transport with the decision loop
  untouched (the real regression guard). Each transport SYNTHESIZES the JSON collection the oracle
  already parses (SQL: rows→`[{street_address}]`/`[]`; JSON: pass-through; the loop is unchanged).
- **Triples are YAML, not Java (A-F7 = B-F4).** `TargetTripleRegistry.load` is the production loader
  (`g3.triples.natural`, `g3.ship.triple` are file paths). Author the two new triples in YAML +
  a `readback.transport` registry field; only the stimulus + the transport override are code.
  Open-Q4 (bespoke vs config) was a false dichotomy — struck.
- **Circularity firewall (C-B2, A-F12).** MIST's read-back hits the same durable store the capture
  used. Rev 2 adds: (i) an INDEPENDENCE STANDARD (MIST keys on a request-derived marker in a DIFFERENT
  column — shipping.street_address — than the capture's server order_id; it is a LIVE re-run of the
  stimulus, not a replay of the capture artifact); (ii) these self-concordant cells are reported in
  their OWN bucket and recall is stated WITH and WITHOUT them; (iii) explicit "live re-run, not
  artifact replay" disclosure. §2.3's label-circularity rebuttal is kept but is no longer the whole
  answer.
- **`mist_bindable` flip is a dated §6 amendment, atomic with a measured run (A-F9 = B-F9 = C-M1).**
  Not pre-registration (the conventions pre-registered the flip only for `bindable-pending-eval`
  cells, not `false→true` T9 cells). The bool flips ONLY in the same commit as the run that produces
  the verdict cell — the audit property "verdict-valued mist cells appear only where MIST ran" holds.
- **Stimulus driver is the real unscoped cost (B-F5).** Both writes are multi-step stateful flows;
  g3 uses hand-written Java `Stimulus` impls; no OTel/TeaStore asset exists in-repo. The authoring-cost
  metric MUST include the stimulus (authored OpenAPI does not execute on the paired path).
- **TeaStore surface: bind the durable JSON, not the HTML (A-F3, A-F13, C-M3, B-advisory).** The HTML
  `firstname` renders in the profile greeting too ⇒ a text/CSS match reads PRESENT on both legs
  (false negative). Resolution adopts ONE consistent principle (C-M3): **MIST's read-back probes the
  SUT's durable system-of-record** — psql for OTel, the persistence `/rest/orders` JSON for TeaStore
  (the oracle parses JSON free; dissolves the HTML FN). This is a modality amendment
  (api-get-HTML → api-get-JSON durable-store) recorded in freeze §6, and it is disclosed as an
  INTERNAL durable-store probe (a stronger threat model than an end-user query), stated honestly for
  BOTH SUTs. B's advisory (TeaStore's request-supplied marker is the cleaner MIST-model fit) is
  ACCEPTED — see scope.
- **Per-leg session for TeaStore (A-F4).** `MstAuthHandler`'s single per-JVM cached cookie collides
  with per-leg-fresh-user; the read-back uses a harness-owned per-leg session.
- **Error-vs-absence latching (A-F5 = B-F6/F8).** A transport failure (psql/HTTP non-2xx, broken
  probe) must map to an ERROR record / non-2xx, NEVER a zero-count ABSENT (which would false-FLAG) —
  the analog of the existing "probe row vanished" rule.
- **kafka-scale injector + control-first + async floor (A-F8 = B-F7).** The registry flag model
  doesn't cover scale-0 → a bespoke injector; control-FIRST, single-toggle leg ordering (all control
  up, then all fault down, restore once) because of the producer-wedge; an async-landing timeout
  floor (≥20 s, the ShippingEnqueue precedent) so the control leg doesn't read absence too early.

### MINOR (fold or note)
- Unique correlators per probe for N≥4 (A-F10, `requireClaimEligible`); pin kubectl-exec argv +
  `otel`/`accounting` constants + key whitelist + the WSL/Windows kubectl knob (B-F6); map exec
  failure to non-2xx (B-F6); don't double-own transport (Triple field vs harness override — B-F10);
  fix DoD wording (B-F11); cite A-M8 construction-bar disclosure (C-MINOR); pre-register the
  SCIENTIFIC anti-findings as reported outcomes, not just engineering failure (C-M5).

## Scope decision (reconciling A "OTel-only" vs B "TeaStore is the cleaner binding")
**Rev 2 keeps BOTH SUTs but re-sequences: TeaStore is the seam-proving PRIMARY, OTel the async
follow.** Grounds: with the durable-store principle adopted, TeaStore binds via its `/rest/orders`
JSON with a NATIVE request-supplied marker (the capture's `firstname`/order token) that fits MIST's
isolation model directly and is SYNCHRONOUS (no async timing, no kafka injector) — the lowest-risk
binding, which is what a derisking pilot should be (this overrides my original "derisk on OTel"). OTel
follows: it exercises the SQL transport + the request-derived street_address marker + the kafka-scale
injector + the async floor, and yields the presence-CONCORDANCE datum. If TeaStore's `/rest/orders`
turns out not to support a keyed GET at execution, it falls back to a DOM-scoped Orders-table HTML
locator (A-F3) or stays a disclosed refutation — no silent re-scope.

## Confirmation pass — OUTCOME: UNANIMOUS CONFIRM-ACCEPT (2026-07-10)
All three reviewers re-derived their findings against the rev-2 text and **CONFIRM-ACCEPT**:
- **A (oracle):** all BLOCKING/MAJOR resolved; verified the OTel differential (kafka-down ⇒ shipping
  row never lands; fresh-unique marker ⇒ baseline guaranteed-absent). One pre-committed residual —
  **F11** (pre-run check that the checkout ack has no top-level `status` field).
- **B (engineering):** all resolved; noted the HTML asymmetry is dissolved by binding `/rest/orders`
  JSON. Three pre-committed textual residuals — **R1** (SQL server-side marker filter → 0/1-row
  collection), **R2** (configurable WSL/Windows kubectl knob), **R3** (fault-leg read-back with
  maintenance restored OFF).
- **C (hostile-PC):** all 3 BLOCKING + 5 MAJOR resolved; verified the `street_address` deviation is
  equivalence-preserving (Order+Shipping+OrderItem in one `SaveChanges()`). Four pre-committed
  textual residuals — **R1** (record `TIMEOUT_ABSENT` stratum + gate-agnostic verdict), **R2**
  (co-persistence justification), **R3** (generalize independence wording to TeaStore), **R4** (pin
  the generality claim-string to the result record).

All eight residuals are non-gating and were folded as **rev 2.1 §0.5** (no design change). **GATE
PASSED — cleared for execution.**
