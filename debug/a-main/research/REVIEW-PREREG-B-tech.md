# Prereg cold review B — technical accuracy of g3-sut2-triples-prereg.md

**Date:** 2026-07-02. Independent cold reviewer (no shared context), one of three on
the G2/G3 prereg wave; verified 28 claims against primary sources (in-repo
evaluation/suts/sockshop bundle + bookinfo deploy.sh; microservices-demo service
repos incl. front-end/orders/shipping/queue-master/user/catalogue source; istio
release-1.30 jaeger addon; spring-petclinic-microservices source; TeaStore source).
Full table in the review transcript; verdict-relevant rows below.

## Verification highlights (VERIFIED)
- Swagger 26 ops / 12 writes / 4 tag-services; the 12-write list; POST /cart body
  `{id}` → front-end resolves price → carts 201; GET /cart = session cart items,
  unpaginated, itemId visible (membership viable).
- POST /orders: front-end IGNORES the client body — rebuilds NewOrder from the
  session (customer/address/card/items URIs); orders 201 (406 on payment-declined).
- Fan-out verified in source: orders→GET user(customer/address/card)+carts(items) →
  POST payment → POST shipping → `customerOrderRepository.save`; shipping does
  `rabbitTemplate.convertAndSend("shipping-task", …)`; queue-master consumes.
- Front-end does NOT propagate W3C traceparent (in-repo README + front-end source).
- Jaeger in the bookinfo cluster = istio 1.30 addon = jaeger 2.14 with OTLP
  4317/4318 — no extra collector needed.
- Archived Dec 2023 (carts/user/shipping/queue-master); catalogue seeded with
  exactly 10 items; petclinic claims all verified (incl. gateway routes,
  HSQLDB/mysql profile, Boot 3 + Micrometer/OTel — javaagent claim conservative);
  TeaStore claims verified — AND TeaStore's `listAllEntities(startIndex,
  maxResultCount)` means its lists ARE windowed (independently supports R1fix).
- Reconciliation-consistency verified (R1 promotion labeled correctly).

## WRONG / corrections required

**MAJOR-1 (claim 16) — the tracing mitigation (a) cannot achieve its goal.** The W3C
break is AT the Node front-end, upstream of every Java service; `JAVA_TOOL_OPTIONS`
javaagents on carts/orders/shipping produce real Java-tier spans in a DISCONNECTED
trace while the marker trace stays at 2 spans. "The exact mechanism proven on
TrainTicket" does not transfer (TT is all-Java). As pre-registered, the precheck
would predictably fail → everything falls to TIMEOUT_ABSENT → guts the SUT-2
high-confidence stratum. **Correction:** mitigation (a) must ALSO instrument the
front-end with OTel Node auto-instrumentation (`NODE_OPTIONS --require
@opentelemetry/auto-instrumentations-node/register` + OTLP to jaeger-collector:4317);
then the Java javaagents connect. Plus omitted k8s realities: manifests set
`readOnlyRootFilesystem: true` (agent jar needs initContainer+emptyDir),
`JAVA_OPTS -Xms64m -Xmx128m` (agent overhead may OOM — bump heap), 2017-era Java-8
images (agent-version compatibility must be live-verified).

**MAJOR-2 (claims 7-8) — §0's pagination diagnosis is wrong for the tested surface.**
The orders-service collection endpoint IS paginated HAL (quote accurate), but the
BFF's `GET /orders` — the triple's actual read-back — calls
`/orders/search/customerId?...` backed by `List<CustomerOrder> findByCustomerId`
(no Pageable) → **unpaginated**, returns the full per-customer array. Two of §0's
three completeness mechanisms are unimplementable via the BFF (no page params; no
front-end `/orders/{id}` route; HAL self-links point at cluster-internal hosts).
Keeping R1fix as a G3 prerequisite stays defensible (conservative; TeaStore genuinely
windows) but the factual chain + mechanism must be respecified (bounded-collection
assertion / row-count via the BFF response).

**MEDIUM-3 (claim 19) — SS-B's read-back is a GLOBAL, seeded, monotonically-growing
list, not session-scoped.** Front-end proxies `/addresses` and `/cards` UNFILTERED
(global DB list; user-db seeds test users → non-empty baseline). This is precisely
the R1 accumulation shape — SS-B must inherit the §0 prerequisite explicitly. Also
`GET /customers/{id}` ignores `{id}` and reads session.customerId.

**MEDIUM-4 (claim 28) — missing ingress routes.** The in-repo VirtualService routes
only /catalogue /tags /cart /orders /customers /cards /addresses — **/register and
/login are NOT routed** (4 of 26 ops unrouted incl. 1 of 12 writes). The
register→login round-trip and "fresh registered user" isolation cannot run through
the ingress as deployed. Trivial fix (extend the VirtualService) = a 4th engineering
item; "exactly three engineering items" is wrong.

**MINOR-5 —** "Mongo-per-service" false for catalogue (Go + MySQL; deploy.sh itself
excludes port 3306); rabbitmq + session-db are NOT mesh-excluded — live-verify the
AMQP leg under Envoy.

## INFO — two upsides the prereg missed
1. **A natural (non-injected) silent-drop site:** shipping SWALLOWS enqueue failure —
   source comment "Unable to add to queue… Accepting anyway. Don't do this for
   real!" — and `order.shipment` is written at creation regardless of enqueue
   success; queue-master persists NOTHING (consumes + simulates). Direct relevance to
   the Gate-3 wild-defect goal — but note the drop is black-box-INVISIBLE to the
   read-back oracle (no state reflects consumption): it is a masking-oracle /
   benchmark candidate, not a B2 read-back target. This also effectively ANSWERS the
   SS-C async QUESTION in the negative (same shape as TT's P3).
2. **Cheaper isolation lever:** front-end runs with NODE_ENV unset (Express
   env=development) → the `?custId=` override in getCustomerId is ACTIVE — cart
   paths can be scoped per run via a fresh custId query param without cookie
   sessions (POST /orders still needs a real session).

## Verdict
Trustworthy enough to commit to **after two corrections** (MAJOR-1 tracing
mitigation rewrite to include Node front-end instrumentation; MAJOR-2 completeness
mechanism respecification). Everything else overwhelmingly held up against primary
sources; the honest-framing devices are real; nothing contradicts the
reconciliation.
