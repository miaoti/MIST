# Wave 3a-live plan — S1/S2 population on the STANDING tenants (DRAFT rev 1, pre-review)

**Status: DRAFT — requires unanimous 3-cold-reviewer ACCEPT before execution (standing rule).**
Scope: the live-tenant subset of checklist step 3a. Executes on the tenancy end-state (OTel-Demo UP,
TeaStore UP, TT at 0 — `tenancy-window-result.md` §3). NO MIST tool code (prep rule). NO TT revival
in this wave. Corpus schema rev-2 FROZEN (changes only via freeze §6 amendments).

## 0. Why now / what it buys
The 18-case pilot (8 pos / 10 neg) has ONE case with a MIST-bindable read-back outside TT
(none — TT's 4 discriminating positives are the only `mist_bindable=true` rows). Item 1 below adds
the FIRST non-TT bindable-read-back positive (JSON api-get), item 2 adds TeaStore's second captured
MECHANISM (mesh-sever — the min-3 floor's live evidence), item 3 adds the canonical
pending-vs-missing S2 trap the survey pinned as "the trap for absence oracles" — including OUR OWN
presence column (a disclosed self-test: the benchmark's comparator column is expected to FP on it).
Floor honesty: this tranche adds ~1 distinct SITE (EmptyCart) + mechanisms/traps; the §5
distinct-site floor closes later via the TT-revival tranche + Boutique/remaining survey sites — this
plan does NOT claim floor closure (disclosed).

## 1. OTel-Demo `cartFailure` S1 + control (the bindable-read-back positive)
- **Mechanism = `flag` / injection `vendor_flag`** (flagd `cartFailure`, deployed-config re-freeze row:
  routes EmptyCart to a throwing `_badCartStore`), on the UNMODIFIED SUT. Survey chain: checkout's
  `_ = cs.emptyUserCart(...)` return is DISCARDED (source) → PlaceOrder acks 200 while the cart
  empty fails.
- **Read-back (the point): `api-get` JSON, `mist_bindable=true`** — GET `/api/cart?sessionId=<uuid>`
  after the acked checkout: control = items `[]` (emptied); fault = the item STILL PRESENT
  (residual-state read-back: the durable effect that should have happened didn't). First non-TT case
  whose `mist_readback` design target is machine-bindable as-deployed (still recorded as a TARGET —
  MIST does not run this wave; the CELL stays `not_applicable`? NO — pin: `mist_bindable=true` and
  the cell stays a design-target under the same rev-2.1 R2/R3 discipline used everywhere:
  captured⇒as-deployed applies to columns that RAN; MIST didn't run ⇒ `not_applicable` recorded,
  FLAG design target preserved in notes per T1. Reviewers: check this pin.)
- **Trace cells**: natively traced; selectors PRE-COMMITTED before capture (T4): entry =
  (frontend-proxy, "POST", server) on the checkout trace; presence target = the cart service's
  EmptyCart SERVER span... **probe-then-freeze**: canary the healthy trace, bind the exact
  EmptyCart span (service `cart`, op fragment `emptycart`), decide presence semantics (the span may
  be PRESENT-but-erroring under the flag → presence=no_flag MISS + naive=flag CATCH via the
  error span — the INVERSE of the broker case; that asymmetry is the pair's value). Scope
  {frontend-proxy, frontend, checkout, cart}; `presence_scope` default (sync gRPC, same trace).
- **Toggle**: flagd ConfigMap patch (`cartFailure` defaultVariant off→on) + flagd hot-reload;
  verify via the flag evaluation API before the leg; restore + verify after. N≥4 probes under the
  flag (distinct sessions; expect 200 acks + non-emptied carts) before the capture-of-record.
- Legs: control (flag off) + fault; sidecars + per-leg merged exports (checkout window; accounting
  spans NOT asserted here — the kafka leg is healthy and the ORDER still lands: assert the psql row
  LANDS on BOTH legs in the runner log to isolate the failure to the cart-empty effect; disclosed
  in notes as a partial-aggregate: order lands, cart-empty lost ⇒ `write_shape=partial-aggregate`).
- Case JSONs: `oteldemo-emptycart-swallowed-001` (S1 positive, natural/by-docs) +
  `oteldemo-emptycart-control-001`.

## 2. TeaStore mesh-sever S1 + control (the second captured TeaStore mechanism)
- **Mechanism = `mesh-sever` / injection `mesh_abort`**: the T15-verified mechanics — sidecars
  TEMPORARILY on webui+auth, plain VirtualService abort **503** on the persistence
  `/tools.descartes.teastore.persistence/rest/orders` prefix (the Phase-C rider already VERIFIED
  the end-to-end mask live: confirmed page + marker lost).
- **Deploy-shape parity pin**: BOTH legs (control AND fault) captured with the sidecars ON
  (webui+auth 2/2) so the only variable is the VS; sidecars + VS torn down after; the maintenance
  pair's sidecarless deploy shape is unaffected (different case ids, deploy strings disclose the
  shape). Trace cells stay `not_applicable` (TeaStore app uninstrumented; Envoy sidecar spans exist
  on 2 services only — NOT a scoreable trace per the 2.5.3 branch; disclosed in notes. Reviewers:
  confirm not scoring 2-hop Envoy fragments is the right call vs a bookinfo-style Envoy scoring —
  the asymmetry argument: bookinfo is FULLY meshed, TeaStore here has exactly 2 sidecars, so a
  "trace" is a 2-span fragment with no persistence-side span; scoring it would fabricate a
  presence-assertion target that cannot exist even on control).
- Flow: the frozen `teastore-order-flow.yaml` + `teastore-profile-readback.yaml` specs, fresh
  users (user18/user19), distinct markers, N≥4 probes under the VS, post-teardown read-back of
  record + REST corroboration; maintenance flag verified `false` throughout (mechanism isolation).
- Case JSONs: `teastore-order-meshsever-masked-001` (S1 positive; mechanism diversity datum:
  TeaStore now {flag CAPTURED, mesh-sever CAPTURED, dependency-down SPECIFIED-UNSOUND-disclosed})
  + `teastore-order-meshsever-control-001`.

## 3. OTel-Demo `kafkaQueueProblems` S2 (the pending-vs-missing benign trap) — PROBE-GATED
- The survey's canonical trap: flag on → checkout floods duplicate sends + fraud-detection sleeps →
  orders DELAYED, not lost. The S2 case documents: entry ack clean 200; the row lands LATE
  (measure the latency); an absence oracle sampled at the standard window FLAGS (our presence
  column included — a DISCLOSED self-test: `tracetest_presence_oracle: flag` on a NEGATIVE =
  the benchmark measuring its own comparator column's FP mode honestly).
- **PROBE ROUND FIRST (probe-then-freeze, ≤1 h):** the flag also spawns 100 duplicate goroutine
  sends (survey) → accounting dedupes on unique violation; verify (a) the row DOES land, with what
  delay; (b) duplicates are visible only as dedupe skips (no double-rows); (c) the deployed 2.2.0
  flag semantics match the survey (the re-freeze row lists it ENABLED/off). **If the probe refutes
  the delayed-not-lost semantics (e.g. rows actually lost, or delay < the export window so nothing
  distinguishes), the case is NOT authored — C-m8/R9 precedent, disclosed finding in the survey.**
- Read-back convention: `sql-probe` + T9 boundary (same as the flagship pair); the case's POINT is
  the comparator-column FP + the pending-vs-missing taxonomy already measured operationally in
  Phase D (the wedge datum) — now as a DESIGNED, documented flag.
- Case JSON (if probe passes): `oteldemo-kafkaqueue-pending-benign-001` (S2 negative, by-docs =
  the flag's own description; no separate control — S2 convention, bookinfo precedent).

## 4. Out of scope (disclosed)
- Boutique deploy + its 1 S1-minor case (below the write-path floor; separate small window later).
- S3 wild-hunt, M-yield/M-prevalence (step 4/5; rater-gated).
- Any MIST run or tool change (2.75/E2; user-gated).
- TT revival (2.5/E2 tranche).
- OTel `paymentFailure`/`paymentUnreachable` (LOUD per survey — not masked; not S1-eligible).

## 5. Discipline (inherited, binding)
T2 family-validation + divergence-as-measured; T4 pre-committed selectors (canary-bound, committed
BEFORE first capture per item); N≥4 consecutive probes; fresh identities/markers per leg; per-leg
immediate exports w/ quiet gaps + exactly-one pre-check; sidecar leak checks; script-files-only for
cluster ops; validator exit-0 after every case; per-item commits (a stop strands nothing);
FILE_INDEX + freeze §6 + README counts in the same commit as each case pair; survey corrections
dated. RAM guard: `free` before each item; the box currently carries OTel(21 pods)+TeaStore(7).
Kafka-recovery runbook stands (restart checkout+accounting+fraud after any kafka pod replacement;
item 3's flag does NOT restart kafka — no wedge expected; verify anyway).

## 6. Budget + collapse order
Item 1 ≈ 0.5 d; item 2 ≈ 0.5 d; item 3 ≈ 0.5 d incl. probe. Collapse order on any pressure:
**1 → 2 → 3** (bindable-read-back positive first: it is the unique paper asset; mesh-sever second:
mechanism floor; the S2 trap last: nice-to-have, its taxonomy is already partially measured).
Each item lands as its own commit; the wave closes with a short result addendum to
`tenancy-window-result.md`'s NEXT section (or its own note if all 3 land).

## 7. Expected corpus after (report as counts, never bare S1)
All 3 items: 18 → 23 cases, **10 pos / 13 neg** (2 new S1 positives + 2 controls + 1 S2 negative).
Items 1-2 only: 22 cases, 10 pos / 12 neg.
