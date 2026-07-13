# S3 wild-hunt — P0 pins (journeys versioned · knobs · §0.4 async-bound check)

Governing plan: `debug/a-main/c2c3/s3-wildhunt-plan.md` rev 2.1 (UNANIMOUS-cleared).
Pre-registration: freeze `debug/a-main/c2c3/c2-freeze.md` §6 "Step-5-as-amended" row (2026-07-13,
committed BEFORE any window). This file discharges the P0 §7 items "journey scripts versioned",
"knobs pinned", "TT extra-triple selection pinned", and "docs-bundle async-bound check (§0.4)".

Emission architecture (plan §4.3): the Java runners emit ordered raw flag bundles
(`s3/<mode>-<sut>/flags/flag-w<idx>.json`, producer `wildflag-bundle`); the thin deterministic
Python assembler `b4/wildflag_assemble.py` rebases them to rater-facing `sidecar.json` (v1 format);
`b4/b4_harness.py` renders the blind case. Unit-tested end to end (`b4/test_wildflag_assemble.py`
8/8 + `mist-cli` `WildHuntEngineTest` 5/5).

## 1. Journey scripts (VERSIONED — plan §0.2 "workload scripts versioned pre-run")

One observation cadence for ALL strata (plan §4.2): initial poll cadence + the ≥T+5min re-probe are
pinned identically for S3 windows, top-up captures, and calibration. Each journey has exactly ONE
bound write (the marker-carrying create/checkout) preceded by ≥1 read step (the write-path-fraction
denominator, plan §5). Markers use the pre-registered ban-free grammar `corpus-w<seq>-<12hex>`
(`WildHuntEngine.nextMarker`, base seed `20260713`).

**Marker-seed salting (disclosed refinement, TT-only).** TT's admin-basic writes are UNIQUE-KEYED
(station.name/id, config.name, price.trainType), so a *fixed* seed re-generates a prior run's markers
and the store rejects the duplicate (HTTP 200 `{status:0}` → not-acked, NOT a detector event). The TT
runner therefore XOR-salts the pinned base seed `20260713` with a per-run nonce (`System.nanoTime()`)
so every run's markers are globally unique against the persistent store; the effective seed is recorded
our-side in `window-log.json.environment_guard.marker_seed_effective` (provenance; never rater-facing).
The ban-free grammar and the "no tell-string" property are UNCHANGED (unit-tested); exact hex values are
scientifically opaque (the assembler rebases/opaquifies). OTel/TeaStore writes are NOT unique-keyed (a
re-run just appends a row; the marker-scoped read-back still finds it), so their runners keep the raw
pinned seed and their already-emitted windows are unaffected.

### OTel-Demo (`OtelWildHunt`, SqlDurableReadback — MEMBERSHIP on accounting.shipping)
1. `GET /api/products/0PUK6V6EV0` (read step)
2. `POST /api/cart?currencyCode=USD` (cart)
3. `POST /api/checkout?currencyCode=USD` — **bound write**, marker in `address.streetAddress`
   (+ email), client W3C `traceparent` injected (export-selection only; canary-gated at P1).
- Read-back: `SELECT street_address FROM accounting.shipping WHERE street_address='<marker>'`
  (marker-scoped; the re-probe re-points the supplier via the engine swap).
- FlagHook: RAW-time Jaeger trace snapshot + CONFIRM-time two-read span-count stability (plan §2d).

### TeaStore (`TeaStoreWildHunt`, JsonDurableReadback — MEMBERSHIP on /rest/orders)
1. `POST /loginAction` (rotated pre-generated user `user<22+i mod 40>`; credentials redacted)
2. `GET /category?category=2&page=1` (read step)
3. `POST /cartAction?addToCart=&productid=42` (cart)
4. `POST /cartAction ...confirm` — **bound write**, marker in `address1`.
- Read-back: `GET /rest/orders` (persistence system-of-record; NEVER `GET /rest/generatedb`).

### TrainTicket (`TrainTicketWildHunt`, built-in RestAssuredHttp — MEMBERSHIP, admin-authed)
Round-robins across all 3 bound admin-basic endpoints (plan §6 "journeys cover all bound endpoints").
Per journey:
1. `GET /api/v1/adminbasicservice/adminbasic/{stations|configs|prices}` (read step, admin bearer)
2. `POST .../{stations|configs|prices}` — **bound write**, marker in the keyed field.
- Read-back: the SAME collection GET via the built-in transport (admin bearer via `MstAuthHandler`).

## 2. Knobs (freeze-row pin 2/§2 — restated at the runner)

| SUT | read-back timeout | poll | re-probe delay |
|-----|------|------|------|
| OTel-Demo | 25000 ms | 2000 ms | 300000 ms |
| TeaStore  | 10000 ms | 500 ms  | 300000 ms |
| TrainTicket | 10000 ms | 500 ms | 300000 ms |

Breaker (plan §2, review B-M5): ≥5 consecutive RAW candidates OR trailing-50 >20% ⇒ `BreakerTripped`
(pause + runbook health check; resume appends to the same window id). Exclusion is LIST-driven at
triage, never decided by the engine.

## 3. TT extra-triple selection — PINNED and SOURCE-VERIFIED at P0

Triples: `b4/s3/trainticket-adminbasic-triples.yaml` (station / config / price — SUPPLIED MEMBERSHIP).
Chosen from admin-basic CRUD sites with NO natural-provenance authored cases (§2b eligibility). Paths
AND create-body field schemas verified against the pinned docs-bundle source (not merely deferred to
the P3 live preflight):

- `docs-bundles/trainticket/ts-admin-basic-info-service/.../AdminBasicInfoController.java` — confirms
  `POST/GET /api/v1/adminbasicservice/adminbasic/{stations,configs,prices}`.
- Entities: `Station{id,name,stayTime}`, `Config{name,value,description}` (ts-common),
  `PriceInfo{id,trainType,routeId,basicPriceRate,firstClassPriceRate}` (adminbasic.entity) — the
  runner's `createBody` fields match exactly. Keyed field (marker): station `name`, config `name`,
  price `trainType`.
- P3 live-preflight residue (deployed behavior can differ from bundled source): admin creds resolve
  2xx; the `{status,msg,data:[...]}` envelope's `data[]` parses in the MEMBERSHIP oracle's
  `extractItems`; marker round-trips create→collection. Pre-registered `price/trainType` fallback:
  if `addPrice` validates `trainType` against known types and rejects a marker, swap to
  `/adminbasic/trains` keyed on `id` (free-string; same shape) — a pre-registered contingency.

## 4. §0.4 docs-bundle async-completion-bound check (per SUT)

Plan §0.4: "P0 verifies whether each per-SUT pinned docs bundle contains any upstream statement
bounding async completion (included only if it exists — never manufactured)." A rater judging an
acked-but-absent write as *genuine* vs *underspecified* needs a bundle-derivable completion bound;
where none exists upstream, the frozen rubric rules the async absence **underspecified, not genuine**
(honest prior C-M1). This check records, per SUT, whether such a bound exists — it is NOT manufactured
into the bundle.

- **TrainTicket admin-basic (station/config/price) — SYNCHRONOUS, no async bound needed.**
  `AdminBasicInfoServiceImpl.add{Station,Config,Price}` proxies the create via a *blocking*
  `RestTemplate.exchange(POST, ...)` to the downstream service and returns that service's `Response`
  envelope synchronously; the controller returns only AFTER the downstream call returns. There is no
  queue, no `@Async`, no eventual-consistency deferral in this path. **Consequence:** a `status:1`
  create absent from its GET-all collection at cap is NOT excusable as async lag — it is a genuine
  sync-acked-but-lost, exactly the surface where a rater-*genuine* label is reachable (plan §0.4 →
  TT MANDATORY). Caveat recorded: the downstream station/config/price services' source is NOT in the
  bundle; TT's reference architecture is uniformly synchronous Spring-Data `repository.save`, but the
  P3 preflight empirically confirms create→collection round-trip (the hunt does not assume it).
  No async-completion statement is added to the bundle (none is needed for a synchronous path).

- **OTel-Demo checkout — ASYNC, NO upstream completion bound (do not manufacture one).**
  checkout → Kafka → accounting is asynchronous; the checkout API returns an `orderId` synchronously
  but the downstream durable persistence (accounting.shipping) is decoupled. The OTel demo's upstream
  material bounds NO time by which the shipping row MUST be durable. **Consequence:** per the frozen
  rubric's async tie-break, an acked-but-absent OTel checkout most plausibly forfeits the *genuine*
  leg BY RULE (underspecified) — the pre-registered scarcity expectation on this SUT. The P1 bundle
  build will re-run this check against the actual pinned OTel doc set and record verbatim any
  completion statement IF ONE EXISTS; absent that, none is manufactured.

- **TeaStore order confirm — SYNCHRONOUS, no async bound needed (P2, observed).**
  The webui `cartAction` confirm makes a BLOCKING REST call to the persistence service's order
  create and returns only after it responds; there is no queue/async deferral. Observed live: the
  §3 FP calibration's 20/20 writes were present-at-cap IMMEDIATELY (poll 1, no async lag), and 2.75-A
  already pinned TeaStore as a sync SUT (`TIMEOUT_ABSENT`, no async-completion signal). **Consequence:**
  a `cartAction`-acked order absent from `/rest/orders` at cap is NOT excusable as async lag — it is
  sync-acked-but-lost, genuine-eligible exactly like TT (though the honest prior C-M1 still expects 0
  NATURAL finds — every TeaStore acked-loss ever captured required an injected fault: maintenance-mask
  / mesh-sever). TeaStore has no upstream durability-SLA material, so no async-completion bound exists
  or is manufactured. (TeaStore is trace-uninstrumented ⇒ sidecar-only; no comparator trace column.)

**Rule of record:** OTel/TeaStore bundles do not exist yet (built at P1/P2); this check is
pre-registered here and DISCHARGED verbatim at each bundle build. Only TT's bundle exists now and its
check is COMPLETE above (synchronous path; no bound needed).
