# §8.5-3 DEPTH SURVEY — four new C2 SUTs (NORMATIVE input for S1 quotas + the §4 applicability matrix)

**Provenance:** independent cold subagent, 2026-07-08, surveyed from upstream source (URLs cited per
claim; no repo assets existed for TeaStore/OTel-Demo). **Opportunity** = endpoint that (a) acks 2xx
while (b) the durable effect is severable downstream and (c) a black-box read-back exists. Counts
follow the "count ≠ depth" rule (`debug/a-main/README.md:300-302`); mechanism taxonomy per plan §2.3.
This survey is NORMATIVE for the S1 per-SUT quotas (plan §2.3 / §8.5-3) and finalizes the
`e-sut-applicability-matrix.md`. **Version caveat:** all findings are against upstream default
branches as of 2026-07-08; §2.2's label version-validity rule requires pinning tags/digests at the
freeze before any label ships.

**Headline outcomes:**
- **TeaStore is NOT as shallow as pre-registered** — a natural, in-tree masked-write chain exists
  (`placeorder` → registry-client status-swallow → 200 ORDERCONFIRMED). It is the SECOND SUT (after
  TT's cancel envelope) with a wholly natural masked write; no fork needed. Write-path S1, E1-FULL.
- **OTel-Demo carries the benchmark's flagship ASYNC acked-but-lost** — checkout → Kafka `orders` →
  accounting-Postgres, with the Kafka publish error swallowed, and a DURABLE consumer-side read-back
  (SQL probe on the accounting DB) — stronger than SS's broker-count delta.
- **Boutique is genuinely shallow** — orders are not persisted anywhere; 1 natural site only → BELOW
  the ≥4-mechanism floor → NOT a write-path SUT (S2 + E1-thin + 1 disclosed S1-minor).
- **Bookinfo has 0 opportunities** — read-only in practice (ratings POST = 501 on DB-backed v2) →
  S2 + E1-thin ONLY.
- **Floor check (plan §2.3):** write-path SUTs = TT + SS + TeaStore + OTel-Demo; new-SUT
  data-integrity cases alone = 8–10 natural ⇒ the "≥6 acked-but-lost across write-path SUTs" floor is
  met with margin, without leaning on TT/SS's proven ≥4.

---

**Rev-2 review annotations (2026-07-08, step-1 3-cold-review):**
- **m4 (A) — exact call path:** the TeaStore swallow chain is
  `AuthUserActionsRest.placeOrder → LoadBalancedCRUDOperations.sendEntityForCreation → (ServiceLoadBalancer)
  → NonBalancedCRUDOperations.sendEntityForCreation`; the `LoadBalanced` wrapper propagates the `-1`
  unchanged (`Optional.ofNullable(...).orElse(-1L)`). A WebFetch-verified the whole chain verbatim.
- **A source check — the `-1` is TELL-FREE at the client:** `placeOrder` CLEARS the SessionBlob before
  returning 200, so the `-1` is never echoed → `ack_content_visibility: success-shaped-clean` (a tell-free
  natural exhibit). The one place `-1` IS visible is the internal-CRUD 201/`-1` tier (survey-capped) →
  `sentinel-in-body`, segregated from the primary discriminating denominator.
- **R1 — mechanism taxonomy → the rev-2 enum:** "DB-down" = `dependency-down`; "maintenance-toggle" =
  `flag`; "mesh-sever ×2 legs" = ONE `mesh-sever` mechanism on TWO sites; "input-driven"/bogus-user-id =
  a `stimulus.workload_variant`, NOT a mechanism (does not count toward diversity). Net TeaStore distinct
  mechanisms = **3** {flag, dependency-down, mesh-sever} → broker-less min-3 floor met (see
  `e-sut-applicability-matrix.md` + `c2-freeze.md` §5).

**LIVE-VERIFICATION CORRECTIONS (2026-07-10, tenancy-window Phase C, kind deploy of v1.4.2 — measured;
no post-capture re-scoping):**
- **Toggle path as written 404s.** The maintenance toggle is `POST /tools.descartes.teastore.persistence/`
  **`rest/generatedb/maintenance`** with a JSON body `true|false` (Content-Type json); the GET form of the
  same path only READS the flag (a `?maintenance=` query param on GET does NOT set it). **SHARP EDGE:**
  bare `GET /rest/generatedb` REGENERATES the whole database (wipes orders) — never probe it.
- **Maintenance producer: VERIFIED-MASKED end-to-end and CAPTURED as the S1 case**
  (`teastore-order-maintenance-masked-001` + control). Intermediate-shape refinement: under maintenance
  the persistence CREATE itself returns **201 with body `-1`** (measured: the same direct-POST body that
  500s healthy returns 201/`-1` under the flag) — the §1 swallow chain then applies to the `-1` exactly
  as written; reads (categories/products/users/orders GETs) stay 200 under maintenance as-deployed, so
  the javadoc's "503 on almost anything" did not manifest for reads either.
- **DB-down producer: UNSOUND FOR CAPTURE on this deploy (disclosed finding).** `teastore-db` has NO PVC —
  any db pod cycle WIPES AND REGENERATES the data (observed live: product 42 → 404 after a cycle),
  destroying exactly the absence evidence the masked-write case turns on. The mechanism stays
  source-true; the capturable-pair count for this deploy drops it (order-row × DB-down: specified-only).
- **Mesh-abort producer: VERIFIED-MASKED live** (C2 rider leg 3): plain VirtualService abort 503 on the
  persistence `/rest/orders` prefix with sidecars temporarily on webui+auth → login/cart fine, confirm
  302→200 confirmed page, marker ABSENT after teardown — precisely the §1 source prediction
  (non-404/408 → silent `-1L`). **T15 datum (REFUTES the pre-registered miss expectation):** plain VS
  host-match INTERCEPTS on this deploy because the kind manifest sets `HOST_NAME` to the kube service
  DNS (the registry holds `["teastore-persistence:8080"]`), so client-LB dials resolve through the
  service and the CLIENT sidecar matches — no EnvoyFilter needed (contrast TT's ribbon pod-IP dials).
  Rider datum only this wave (plan-pinned 2 TeaStore cases); candidate mesh-sever S1 for 3a.
- **Bonus input-driven 201/`-1` (bogus user id): UNADJUDICATED.** The disentangling matrix confounded it:
  BOTH uid cells (valid and bogus, maintenance off) 500ed with the probe's body shape (the body fails
  before user-id logic). Stays source-only; neither confirmed nor refuted.
- **Recommender cold-start S2: REFUTED as user-visible (no case authored).** Pod-delete timeline at
  ~1.4 s sampling: the sole instance reports `isready 500|false` for only ~3 s and the product page
  NEVER degraded (200 + 3 recommendation links in the same iteration the instance reported untrained) —
  `TrainingSynchronizer` gates `isReady` until "Finished training" BY DESIGN and the registry LB bridges
  the window (old instance serves through graceful shutdown). Strengthens the §1 anti-finding: the S2
  quota this wave moves entirely to the OTel-Demo graceful-ad rider (D3b).

**Architecture:** 5 services + registry (WebUI, Auth, Persistence, Recommender, Image), sync REST with
client-side load balancing; **no MQ, no async writes**. Cart lives in a client-held `SessionBlob`
(never persisted server-side — cart-add is NOT an opportunity). **No user registration endpoint**;
users are pre-seeded by the DB generator. The only user-facing durable write is **order confirmation**.

**The verified natural-swallow chain (key finding, all from source):**
1. `NonBalancedCRUDOperations.sendEntityForCreation` (`.../registryclient/rest/NonBalancedCRUDOperations.java`)
   throws only on **404/408**; **any other failure status (500, 503) silently returns `-1L`**.
2. `AuthUserActionsRest.placeOrder` (`services/.../auth/.../rest/AuthUserActionsRest.java`) **never
   checks `orderId == -1`**, catches only 404/408, then `blob.setOrder(new Order()); blob.getOrderItems().clear();`
   and returns **200 + SessionBlob**.
3. WebUI `CartActionServlet` "confirm" has no error handling on the 200 path → redirects **ORDERCONFIRMED**.
4. Producers of the maskable statuses: **maintenance mode** (Persistence returns 503; toggle via
   `POST /generatedb/maintenance`) · **DB-down** (`OrderRepository.createEntity` try/finally, no catch →
   500) · **mesh abort** (any Envoy abort status except 404/408 — 503 fine; unlike TT no 418 needed, the
   swallow is in the CLIENT'S status mapping). **Bonus input-driven trigger:** bogus user id →
   `order.setId(-1)` and `AbstractCRUDEndpoint` still returns **201 Created with body `-1`** (no
   negative-id check).

**Partial-write variant:** placeOrder writes the order row FIRST, then order-items in a loop. A 503
scoped to `POST /orderitems` → items return -1 silently → **order acked and present but items lost**
(child-collection absence — the object/aggregate class from the Rider-2 residue).

**Honest count: 4 genuine natural pairs** (order-row × {maintenance, DB-down, mesh-abort} + order-items
× mesh-abort) **+1 constructed-optional** (code-level spare) +1 at the internal-CRUD 201/-1 tier
(breadth only, capped). **Depth honesty:** all pairs hang off ONE user flow; no compensation logic to
miss → masked-sync-CRUD, not saga depth. TT keeps the depth story. But the swallow is **natural and
in-tree**, no fork.

**Role: write-path S1 contributor — proposed quota 4–5 cases** (≥4 mechanisms met WITHOUT code-level).
E1-FULL; OpenAPI must be authored (pre-registered).

**S2 designed-degradation paths (benign-by-design citations):** (1) DB regeneration wipe (`GET /generatedb`
"Drop database and create a new one") → membership-oracle trap; (2) maintenance 503 window (documented;
note dual use — same toggle produces the S1 masked write); (3) recommender cold-start → empty
recommendations with 200 (verify-at-deploy). **Anti-finding:** TeaStore does NOT gracefully degrade in
the WebUI (`ProductServlet` propagates failures to an error page) → few masked-benign traps.

---

## 2. OpenTelemetry Demo (open-telemetry/opentelemetry-demo, main) — flagship ASYNC + a vendor-flag mask

**Checkout write path (`src/checkout/main.go`):** PlaceOrder = prep → chargeCard → shipOrder →
emptyUserCart → sendOrderConfirmation → sendToPostProcessor (Kafka `orders`). prep/charge/ship = LOUD;
**`_ = cs.emptyUserCart(...)` swallowed**; **email swallowed** (`Warn`); **Kafka publish swallowed**
(sync send with delivery check, errors only logged — order acks regardless). Consumers: **accounting**
(C#, `src/accounting/Consumer.cs`) **persists OrderEntity/OrderItemEntity/ShippingEntity via
`dbContext.SaveChanges()` to Postgres** (schema `accounting`, shared `astronomy-db`; duplicate orders
skipped on unique violation); **fraud-detection** (Kotlin, log-only). Kafka + accounting +
fraud-detection exist ONLY in the **`compose.full.yaml` overlay** → deploy pin required.

**flagd flags (`src/flagd/demo.flagd.json`, main):** productCatalogFailure, recommendationCacheFailure,
adManualGc, adHighCpu, adFailure, **kafkaQueueProblems** (overloads Kafka + consumer-side delay → lag
spike; checkout spawns 100 duplicate goroutine sends; fraud-detection `Thread.sleep(1000)`/msg),
**cartFailure** (routes EmptyCart to a `_badCartStore` that throws — `src/cart/src/services/CartService.cs`),
paymentFailure (loud), paymentUnreachable (loud), loadGeneratorFloodHomepage, imageSlowLoad,
failedReadinessProbe, emailMemoryLeak, intlShippingSlowdown. *Version skew disclosed:* docs additionally
list `llmInaccurateResponse`/`llmRateLimitError`/older `*Service*` names — the pinned release's shipped
JSON governs at freeze. flagd-ui allows runtime toggling.

**Honest count: 4 genuine pairs** (kafka-loss × {broker-down, mesh-sever} + emptycart × {vendor-flag,
method-scoped-sever}) + email as a disclosed trace-only extra. Mechanism classes: **flag + mesh-sever +
broker + (code-level spare) → ≥4 met**. The kafka case is the only async acked-but-lost in the 6-SUT
benchmark with a **durable consumer-side read-back**.

**Role: write-path S1 contributor — proposed quota 4–5 cases** (incl. the flagship async case); E1-THIN;
S2-rich; M-prevalence workload = built-in loadgen. **Step-2 engineering:** compose.full profile pin;
psql read-back probe; verify PlaceOrder latency when the broker is down (producer send is
synchronous-with-logging — confirm the ack path stays fast).

**S2 designed-degradation paths (doc-labeled):** (1) **kafkaQueueProblems = the canonical
pending-vs-missing trap** (orders DELAYED not lost; naive absence checks false-positive); (2)
duplicate-delivery dedupe (accounting skips unique-violation duplicates); (3) imageSlowLoad; (4)
adFailure/adManualGc/adHighCpu (ad panel best-effort — verify graceful render); (5)
recommendationCacheFailure; (6) intlShippingSlowdown; (7) failedReadinessProbe; (8)
loadGeneratorFloodHomepage. All flag descriptions are vendor docs → clean by-docs S2 provenance.

---

## 3. Online Boutique (GoogleCloudPlatform/microservices-demo, main) — very shallow: one genuine site

**Facts (source-verified):** `checkoutservice/main.go` PlaceOrder: cart/prep/quote/charge/ship all
LOUD; **`_ = cs.emptyUserCart(...)` swallowed**; email swallowed (`Warnf`). **Orders are NOT persisted
anywhere** — OrderResult exists only in the response (no DB, no queue, no order-history endpoint).
Only durable state = **the Redis cart**; `RedisCartStore.cs` throws `FailedPrecondition` on store-down
for Add/Get/Empty (honest 5xx); frontend renders error pages for cart/currency/checkout failures (loud).

**Honest count: 1 genuine natural pair (+1 constructed possible)** — checkout→EmptyCart swallow via a
method-scoped mesh abort on `/hipstershop.CartService/EmptyCart`. Below the ≥4-mechanism floor → **must
NOT be declared a write-path SUT.** **Role: S2 + E1-thin, with 1–2 disclosed S1-minor cases at most**
(quota 1, optional). **S2 paths (in-tree, already committed MIST assets):** (1) adservice failure →
`chooseAd` returns nil, page renders 200 without ads (`evaluation/suts/boutique/traces/boutique_adservice_outage.json`);
(2) recommendation failure → logged Warn, page renders without recs.

---

## 4. Bookinfo (istio/istio samples, master) — read-only; 0 opportunities; S2 + E1-thin only

**Facts (source-verified, `samples/bookinfo/src/ratings/ratings.js`):** POST `/ratings/{productId}`
**returns 501** on the DB-backed v2; on non-DB versions writes an in-memory array only (`userAddedRatings`,
lost on restart by design — single-process, no severable downstream). Reviews/details/productpage are
read-only. **Opportunity count: 0.** **Role: S2 benign traps + E1-thin ONLY** (≈4 GET paths, saturation
disclosed); zero S1; S3 wild-hunt via the masking oracle only. **S2 paths (citations):** (1)
reviews→ratings outage → reviews 200 "Ratings service is currently unavailable"
(`evaluation/suts/bookinfo/README.md`, masked traces committed); (2) productpage→reviews degraded → 200
"product reviews are currently unavailable" (Istio fault-injection task's documented expected outcome);
(3) productpage→details degraded → same pattern; (4) ratings-v2 DB down → composes into (1).

---

## Consolidated table (NORMATIVE for S1 quotas; feeds the §4 applicability matrix)

| SUT | genuine acked-but-lost opportunities (endpoint × mechanism) | mechanism classes present | S1 quota | S2 designed paths | role |
|---|---|---|---|---|---|
| **TeaStore** | **4 natural** (placeorder→order-row × {maintenance-toggle, DB-down, mesh-abort}; placeorder→order-items × mesh-abort) +1 code-level spare, +internal-CRUD 201/-1 tier (capped) | designed-toggle(flag-eq) / DB-down / mesh-sever / input-driven / (code-level) → **≥4** | **4–5** | 3 | **write-path S1 + E1-FULL + S2** |
| **OTel-Demo** | **4 natural** (checkout→Kafka→accounting-Postgres × {broker-down, mesh-sever}; checkout→EmptyCart × {cartFailure flag, method-scoped sever}) + email trace-only | vendor-flag / mesh-sever / broker / (code-level) → **≥4** | **4–5** (incl. flagship async) | 8 | **write-path S1 + E1-thin + S2-rich + M-prevalence** |
| **Boutique** | **1 natural** (checkout→EmptyCart × method-scoped sever) +1 constructed | mesh / (code-level) → below floor | **1 (S1-minor, disclosed)** | 2 | **S2 + E1-thin (+1 disclosed S1-minor); NOT a write-path SUT** |
| **Bookinfo** | **0** (ratings POST = 501 on v2 / in-memory otherwise) | — | **0** | 3 | **S2 + E1-thin ONLY** |

**Floor check (plan §2.3):** write-path SUTs = TT + SS + TeaStore + OTel-Demo. New-SUT data-integrity
cases alone = 8–10 natural ⇒ "≥6 acked-but-lost across write-path SUTs" met with margin. Per-SUT
≥4-mechanism minimum: met on TeaStore + OTel-Demo; explicitly NOT met on Boutique/Bookinfo (excluded
from the write-path class rather than quietly under-filled).

**Verify-at-deploy list (step-2 DoD riders):** TeaStore — live-confirm 200-ORDERCONFIRMED under
maintenance/DB-down/mesh-503, recommender cold-start semantics, pin release tag; OTel — compose.full
profile, accounting-Postgres wiring + psql probe, PlaceOrder ack latency under broker-down, frontend
graceful-ad rendering, re-freeze the flag list against the pinned tag; Boutique — gRPC method-scoped
Istio abort on EmptyCart (HTTP/2 path match) live check; Bookinfo — nothing (already live-proven in-repo).
