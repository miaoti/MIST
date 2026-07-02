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

**The harness already exists in-repo** (user-flagged 2026-07-02):
`evaluation/suts/sockshop/` — `deploy/deploy.sh` (adds Sock Shop into the
kind+Istio+Jaeger cluster that `evaluation/suts/bookinfo/deploy/deploy.sh` stands up;
DBs excluded from the mesh; routed through the shared Istio ingress),
`openapi/sockshop-swagger.yaml` (**26 ops incl. 12 writes**, 4 services by tags),
`real-system-conf.yaml` + `sockshop-demo.properties` (zero hand-edits), captured
traces. The generalization is **already validated**: MIST ran the catalogue tests on
Sock Shop with no code edits, all 200 (that bundle's README). So SUT-2's remaining
cost is exactly three engineering items, pre-registered below: (i) read-back
completeness (§0), (ii) tracing depth, (iii) session-scoped writes.

Why Sock Shop: polyglot (Java Spring + Go + Node BFF), Mongo-per-service, a real
**broker-mediated async leg** (shipping → RabbitMQ → queue-master), manifests +
in-repo harness. Ops caveats (disclose): service repos **archived Dec 2023** (pin
images; no upstream fixes).

**Tracing reality (corrected from the in-repo bundle — do NOT assume javaagent
depth):** as deployed, tracing is Istio-sidecar (Envoy) with the marker-first lookup
via the ingress; **Sock Shop's Node front-end does NOT propagate W3C `traceparent`
downstream**, so traces are `ingress→front-end` ONLY. Consequence for B2: the
quiescence absence-upgrade (`traceComplete` on the write's own trace) would be
**structurally shallow** — span-stability of a 2-span trace says nothing about the
downstream write. Pre-registered mitigation, in order: (a) attach the OTel javaagent
to the **Java** services (carts, orders, shipping, queue-master) via
`JAVA_TOOL_OPTIONS` + OTLP export — the exact mechanism proven on TrainTicket — which
restores W3C propagation and real spans on the two headline write legs; (b) if (a)
fails on this stack, absences on Sock Shop land in the TIMEOUT_ABSENT stratum and are
disclosed as such per README §8.5 item 2 (FP/gate-coverage per-SUT). The **Go**
services (user, catalogue, payment) get no javaagent either way → triples through
them are breadth-only.

**Session reality (corrected):** the public API is the front-end BFF; `/cart`,
`/orders`, `/addresses`, `/cards` are **session-scoped** (cookie after
`/login`/`/register`). MIST needs cookie-session support in auth handling
(TrainTicket used bearer tokens) — a small `MstAuthHandler` extension, pre-registered
as SUT-2 engineering item (iii). The session-free path (catalogue) is read-only.

The 12 write ops (swagger-verified): POST/DELETE `/cart`(+`/cart/update`,
`/cart/{id}`), POST `/orders`, POST `/register`, POST/DELETE `/customers`,
POST/DELETE `/cards`, POST/DELETE `/addresses`.

### Triple SS-A — cart add-item (headline-clean, Java leg) — SPEC-VERIFIED (in-repo swagger)
- **Write:** `POST /cart` (front-end BFF → carts service, Java Spring, carts-db =
  Mongo, **out of mesh** per deploy.sh). Body: `{id: <catalogue itemId>}`.
- **Read-back:** `GET /cart` — the session's item list: naturally scoped, not
  paginated → clean membership read-back (key = `itemId`).
- **Isolation:** **fresh session per run** (fresh registered user → empty cart
  baseline), item picked from the existing catalogue (finite ~9–18 items — a
  STATION_PAIR-flavored "existing-entity within a fresh scope" strategy; small
  isolation-strategy extension, pre-registered).
- **Fault path:** S1 via Toxiproxy between carts service and carts-db (DB out of
  mesh = clean TCP path to proxy; repoint the service's Mongo URL env).
- **Ack semantics:** bare 2xx (no TT `{status}` envelope) → exercises the
  `bodyStatus==null ⇒ ack on 2xx` branch (R5) — verify the error envelope live.

### Triple SS-B — address/card create (breadth, Go leg) — SPEC-VERIFIED (in-repo swagger)
- **Write:** `POST /addresses` (or `/cards`) via BFF → user service (**Go**,
  user-db Mongo); body = street/number/city/postcode/country (FRESH_STRINGS applies
  directly).
- **Read-back:** `GET /addresses` (session-scoped list).
- **Caveat:** Go leg → no javaagent → absences mostly TIMEOUT_ABSENT; breadth
  triple only. (`POST /register` + `GET /customers/{id}` is the same-shaped
  alternative.)

### Triple SS-C — order create (the depth triple) — SPEC-VERIFIED (POST /orders 201; orders-svc GET is PAGINATED per its api-spec)
- **Write:** `POST /orders` via BFF (orders service, Java Spring, orders-db Mongo):
  assembles the session's customer/address/card/cart and fans out
  orders→(user, carts, payment, shipping) sync HTTP, then
  shipping→RabbitMQ→queue-master async.
- **Read-back:** `GET /orders` (session customer's orders via BFF) — the orders
  service's own collection is **paginated HAL** (§0) → completeness support
  REQUIRED here.
- **Isolation:** fresh customer+cart per run (order = derived state; the
  register→cart→order chain is MIST scenario-generation territory).
- **Depth (README §8.5 item 3):** order persisted in orders-db WHILE shipment is
  queued — a genuine **dual-write across a broker**; Sock Shop's closest thing to a
  saga site.
- **Async investigation (pre-registered as a QUESTION, not a claim):** whether any
  black-box read-back reflects queue-master's consumption (shipment status visible
  via the order entity or a shipping GET). If none exists, Sock Shop's async stratum
  gets the same honest verdict as TT's P3 (no clean black-box async read-back →
  async soundness still deferred / Option-A injector). Do NOT quietly upgrade the
  RabbitMQ leg's existence to "async validated."

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
0. Stand up via the EXISTING in-repo harness: `evaluation/suts/bookinfo/deploy/deploy.sh`
   (base kind+Istio+Jaeger cluster) → `evaluation/suts/sockshop/deploy/deploy.sh` →
   port-forwards → `workload/capture-traces.sh` (all idempotent per the bundle README).
   NOTE: this cluster coexists with the Gate-1 minikube only if memory allows —
   schedule G3 deploys AFTER the Gate-1 cluster is stopped (the run-#1/#2 incident
   taught the 26 GB budget).
1. Engineering item (ii): attach the OTel javaagent to carts/orders/shipping via
   `JAVA_TOOL_OPTIONS` (+OTLP export target) and re-run the Gate-1 traceparent
   precheck ported to Sock Shop; if it fails, record the TIMEOUT_ABSENT-stratum
   disclosure instead.
2. Engineering item (iii): cookie-session support in `MstAuthHandler`; verify a
   register→login→POST /cart→GET /cart round-trip echoes the itemId verbatim.
3. Confirm ack semantics (2xx form + error envelope) per triple — feed the R5
   ack-decoder port note; confirm no server-side key normalization (cold-review A
   Finding 5).
4. Engineering item (i): §0 read-back completeness support; verify GET /cart is
   unpaginated and GET /orders pagination is exhausted or per-entity.
5. Confirm Toxiproxy placement per triple (service↔Mongo; DBs are out of the mesh
   per deploy.sh — clean TCP path) produces the S1 fault and clears back to health
   (F2-style hygiene).
6. Re-run the Gate-1-style benign probe per SUT (README §8.5 item 2: FP per-SUT,
   never pooled).

*Review trail: pending ≥3 independent cold reviewers (one wave together with the G2
prereg). Sources: IN-REPO `evaluation/suts/sockshop/` bundle (README, deploy.sh,
swagger — primary, user-flagged); microservices-demo GitHub org + orders api-spec
(fetched 2026-07-02); DescartesResearch/TeaStore persistence rest directory listing;
spring-petclinic-microservices README.*
