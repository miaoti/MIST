# G3 pre-specification — write-path triples on SUT-2/SUT-3 candidates

**Status:** DRAFT 2026-07-02 (written during the Gate-1 run-#3 wait). Fulfils README
§8.5 commitment 3 ("pre-specify, per write-path SUT, the concrete site the oracle
targets") for the Gate-3 requirement of **≥2 write-path SUTs** (EXECUTION G3;
candidates named in README §4 item 6: TeaStore, Sock Shop, spring-petclinic).
Sources: primary API specs/repos fetched 2026-07-02 (cited inline). Each triple is
marked **SPEC-VERIFIED** (endpoint seen in the published spec/source listing) or
**CANDIDATE** (inferred; must be confirmed on a live deploy before the G3 run).
**Subject to the ≥3-cold-reviewer 验收 together with
[g2-novelty-comparator-prereg.md](g2-novelty-comparator-prereg.md).**

---

## 0. Cross-cutting finding first: R1 (read-back completeness) is a G3 PREREQUISITE

The Sock Shop orders API spec (`api-spec/orders.json`, fetched) documents
`GET /orders` as **paginated HAL+JSON** (`_embedded.customerOrders` +
`page{size,totalElements,totalPages}`). So on SUT-2 the collection read-back is
**paginated by default (Spring Data REST, page size 20)** — the exact trigger for
cold-review A's Finding 1 (membership over a truncated list → systematic false FIRE
in the high-confidence stratum). Consequence, pre-registered now:

> **Before any G3 run, the runtime MUST gain read-back completeness support**
> (paginate-to-exhaustion, or per-entity read-back via the HAL `_links.self` /
> `GET /orders/{id}` form, or an explicit bounded-collection assertion) — the
> reconciliation's R1fix is **promoted from "hardening candidate" to G3 prerequisite.**
> TrainTicket Gate-1 is unaffected (its admin list endpoints are unpaginated
> findAll-style — to be re-verified in the run-#3 report audit).

## 1. SUT-2 recommendation: Sock Shop (microservices-demo)

Why: polyglot (Java Spring + Go + Node), Mongo-per-service, a real **broker-mediated
async leg** (shipping → RabbitMQ → queue-master — confirmed by the project's design
docs), k8s manifests maintained in the deploy repo, and a published per-service API
spec. Ops caveats (disclose): the service repos were **archived Dec 2023** (pin
images/tags; no upstream fixes), and **only the Java services** (orders, carts,
shipping, queue-master) can take the OTel javaagent without rebuilding — the Go
services (user, catalogue, payment) ship without OTel, so trace coverage is partial
and the quiescence gate degrades to timeout on Go legs (pre-register this as the
expected gate-coverage stratum, per README §8.5 item 2).

### Triple SS-A — carts add-item (the clean CRUD analogue of TT contacts) — SPEC-VERIFIED (paths), CANDIDATE (ack/body details)
- **Write:** `POST /carts/{customerId}/items` (carts service, Java Spring,
  carts-db = Mongo). Body: `{itemId, unitPrice}` (+quantity).
- **Read-back:** `GET /carts/{customerId}/items` — a per-cart item list:
  **naturally scoped, NOT globally paginated** → cleanest membership read-back.
- **Isolation:** fresh `customerId` per run (register a fresh user, or a fresh
  session cart id) → FRESH_STRINGS-style; key = `itemId` within that cart.
- **Fault path:** S1 via Toxiproxy between carts service and carts-db (TCP cut /
  latency) — the G3 unmodified-system backend. No SUT-flag injector here (Gate-1
  scaffolding stays TT-only).
- **Ack semantics:** expect 201/200 bare JSON (no TT-style `{status}` envelope) →
  exercises the `bodyStatus==null ⇒ ack on 2xx` branch (cold-review R5) — verify the
  error envelope on a live deploy before trusting the ack rule.

### Triple SS-B — user register — SPEC-VERIFIED (endpoint), CANDIDATE (read-back form)
- **Write:** `POST /register` (user service, **Go**, user-db = Mongo);
  body `{username,password,email,firstName,lastName}`.
- **Read-back:** `GET /customers/{id}` / login-derived lookup — confirm exact form
  live.
- **Isolation:** fresh unique `username` (FRESH_STRINGS).
- **Caveat:** Go service → no javaagent → absence upgrades rarely available on this
  triple (mostly TIMEOUT_ABSENT stratum) — keep it as a breadth triple, not a
  headline one.

### Triple SS-C — order create (the depth triple) — SPEC-VERIFIED (POST /orders, 201; paginated GET)
- **Write:** `POST /orders` (orders service, Java Spring, orders-db = Mongo); body
  carries customer/address/card/items **as HATEOAS URIs** into the user+carts
  services; response 201 with the order entity.
- **Read-back:** `GET /orders/{id}` (HAL self-link) or paginate `GET /orders` —
  **requires the §0 completeness support**.
- **Isolation:** fresh user + fresh cart per run (order is derived state — the
  scenario needs register→cart→order; MIST's trace/pool generation covers such
  chains).
- **Depth (README §8.5 item 3):** order creation fans out orders→(user, carts,
  payment, shipping) sync HTTP, then shipping→RabbitMQ→queue-master async. This is
  Sock Shop's genuine multi-service write site — the closest thing it has to a
  saga/dual-write: order persisted in orders-db WHILE shipment is queued — a
  **dual-write across a broker**.
- **Async investigation (pre-registered as a QUESTION, not a claim):** whether any
  black-box read-back reflects queue-master's consumption (shipment status
  visible via the order entity or a shipping GET). If none exists, the async
  stratum on Sock Shop gets the same honest verdict as TT's P3
  (no clean black-box async read-back → async soundness still deferred), and G3's
  async claim rests on the Option-A injector path or is dropped. Do NOT let the
  RabbitMQ leg's existence be quietly upgraded to "async validated."

## 2. SUT-3 candidates (breadth; pick one)

### spring-petclinic-microservices — CANDIDATE (endpoints inferred from resource classes; README fetched)
- customers-service `POST /owners` → `GET /owners/{id}` (gateway route
  `/api/customer/...`); pets `POST /owners/{id}/pets`; visits-service
  `POST .../visits` → `GET` visits per pet.
- HSQLDB in-mem default / MySQL profile (use MySQL for a real persisting D +
  Toxiproxy point). No broker (sync-only — fine: it's a breadth SUT). Spring Boot →
  OTel javaagent trivial; no OpenAPI mentioned in README (springdoc may exist —
  check live).
- Honest weight: 3 shallow CRUD services — good for oracle breadth (3rd SUT for the
  FP-per-SUT table), weak for depth claims.

### TeaStore — persistence REST CRUD confirmed (endpoint classes seen: Category/Order/OrderItem/Product/User) — WEAKER FIT
- Write path exists (`POST /tools.descartes.teastore.persistence/rest/orders` style),
  BUT: no gateway-level JSON API for user flows (WebUI is HTML), no OpenAPI, Kieker
  (not OTel) instrumentation native, and direct-to-persistence writes make the
  "cross-service" story thin (WebUI→Auth→Persistence checkout is the multi-service
  path but is form-based).
- Verdict: keep as fallback SUT-3; petclinic is cheaper for the same breadth value;
  Sock Shop carries the depth.

## 3. Pre-registered SUT-2/3 decision + what would change it
- **SUT-2 = Sock Shop** (triples SS-A headline-clean, SS-C depth, SS-B breadth);
  **SUT-3 = spring-petclinic** (CRUD breadth) — TeaStore fallback.
- Change triggers: live deploy shows Sock Shop unrunnable on our k8s (archived
  images), or SS-A/SS-C read-backs lack usable membership semantics → swap in
  TeaStore persistence triples; document the swap here before running.

## 4. Live-verification checklist (before any G3 run; per triple)
1. Deploy + OTel javaagent on the Java services; confirm gateway/trace capture for
   one write per triple (the Gate-1 traceparent precheck, ported).
2. Confirm ack semantics (2xx form + error envelope) and read-back form
   (pagination? per-entity GET? key echo verbatim?) — feed the R5 ack-decoder port
   note.
3. Confirm isolation key freshness works (no server-side normalization of keys —
   cold-review A Finding 5).
4. Confirm Toxiproxy placement per triple (service↔Mongo) produces the S1 fault
   (errored D-span) — and that clearing restores health (F2-style hygiene).
5. Station the §0 completeness support and re-run the Gate-1-style benign probe
   per SUT (README §8.5 item 2: FP per-SUT, never pooled).

*Review trail: pending ≥3 independent cold reviewers (one wave together with the G2
prereg). Sources: microservices-demo GitHub org + orders api-spec (fetched
2026-07-02); DescartesResearch/TeaStore persistence rest directory listing;
spring-petclinic-microservices README.*
