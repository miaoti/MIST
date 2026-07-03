# G3 pre-specification — write-path triples on SUT-2/SUT-3 (+ the TT depth site)

**Status:** v2 2026-07-02 — rewritten after the prereg cold-review wave
([B — technical accuracy, 28 claims verified against primary sources](../research/REVIEW-PREREG-B-tech.md);
[C — methodology](../research/REVIEW-PREREG-C-methods.md);
[A — hostile PC, G2-scoped](../research/REVIEW-PREREG-A-pc.md); reconciliation in
[REVIEW-PREREG-RECONCILIATION.md](../research/REVIEW-PREREG-RECONCILIATION.md)).
Fulfils README §8.5 commitment 3 — **including opportunity counts and TrainTicket's
own G3 depth site** (C-pin 1) — for Gate-3's ≥2 write-path SUTs. Gate-1 state note
(C-F11a): run #3 (the lean retry) is EXECUTING now; gate1-result.md's INCONCLUSIVE
entry will be superseded by its verdict. Each claim below is **SPEC-VERIFIED**
(checked against the in-repo harness / primary sources by reviewer B) or
**CANDIDATE** (live-verify before the G3 run). Any material change after this v2 is
a disclosed amendment.

---

## 0. R1 (read-back completeness) is a G3 PREREQUISITE — corrected mechanism (B MAJOR-2)

Facts (B-verified): the Sock Shop **BFF's `GET /orders`** — the read-back the triple
actually uses — calls the orders service's `search/customerId` backed by
`List<CustomerOrder> findByCustomerId` (no Pageable) and returns the **full
per-customer array, unpaginated**; the paginated HAL collection endpoint exists but
is not on the tested surface (and the BFF exposes no page params and no
`/orders/{id}` route; HAL self-links point at cluster-internal hosts). Meanwhile
**TeaStore's persistence lists genuinely window** (`listAllEntities(startIndex,
maxResultCount)`), and SS-B's `/addresses`/`/cards` read-backs are **global,
seeded, monotonically-growing lists** (B MEDIUM-3) — the exact R1 accumulation
shape.

> **Prerequisite (kept, mechanism respecified):** before any G3 run, the runtime
> gains a **read-back completeness assertion** implementable through a BFF: a
> bounded-collection / row-count check on the read-back response (fail the run
> NOT_EVALUABLE when the collection is at or beyond a pre-registered size bound or
> shows truncation), plus paginate-to-exhaustion / per-entity read-back **where the
> surface offers it** (TeaStore windows; petclinic TBD). Promotion propagated to
> [REVIEW-B1B2-RECONCILIATION §3](../research/REVIEW-B1B2-RECONCILIATION.md) and
> EXECUTION.md G3.

**Bar v2 adopted for G3 (C-pin 3):** every G3 FP run uses the R2fix semantics —
`syncFpBar` → **NOT_EVALUABLE (not PASS) when the observation gate is degraded**
(observed-gated denominator below a pre-registered floor, or timeout-gated fraction
above a cap), and the FP result is always the interval
`[observed-gated/acked, fires/acked]` + gate histogram. R3fix (verdict-aware join)
and R4fix (post-settle re-read) are ALSO G3 prerequisites — they land in the
post-Gate-1 hardening wave before any G3 data collection.

**Riders BUILT/PRE-REGISTERED (2026-07-02):** R3fix is now the stronger
**correlator join** — [Rider 1](g3-rider1-correlator.md) (`e640748`, test-first,
suites green, under 3-cold-review) upgrades the positional join to a generation-time
`<method>#<stepIdx>` correlator so an asymmetric skip leaves only that write
unjoined; the per-pair tallies GRADUATE from DESCRIPTIVE-ONLY and may feed the G3
detection claims. The comparator's G3 operating point is fixed by
[Rider 2](g3-rider2-comparator-protocol.md): the full-frozen-set binding round incl.
failure contracts (B-7), the comparator infra-failure-rate reporting rule, and the
delay-vs-loss stratification (satisfied by the A3 bounded retry at the matched
10 s/500 ms budget).

**Per-SUT FP protocol (C-pin 4):** benign probe N=30 per SUT; the same
pre-registered **≤5% observed-gated sync-FP bar per SUT** (never pooled across
SUTs); quiescence knobs carried from Gate-1 (poll 500 ms / timeout 10 000 ms /
settle 3 000 ms) unless re-registered per SUT with written justification BEFORE
deploy; **a SUT whose bar is NOT_EVALUABLE does NOT count toward Gate-3's "≥2
write-path SUTs."**

## 0.5 TrainTicket's G3 depth site + opportunity counts (C-pin 1)

The absolute **depth** story is TrainTicket's, not SUT-2's (TOOL-PLAN §3.5; README
§8.5-3 "TeaStore/Sock Shop are shallower CRUD").

- **Named saga/compensation site (CANDIDATE — live-verify against the TT fork
  source before the G3 run):** the **cancel→refund flow**: `ts-cancel-service`
  cancelOrder → order status transition (order/order-other service) + refund via
  `ts-inside-payment-service`. Missing-compensation shape: order acknowledged
  "cancelled" (2xx) while the refund/drawback write never lands. Read-backs: order
  status via the order-service query; refund record via inside-payment queries.
  Secondary site: the booking saga `ts-preserve-service` preserve → order create +
  seat/assurance/food side-writes (acked-but-partial state).
  `pending-vs-missing` (TOOL-PLAN §5, built at Gate-1, VALIDATED at G3) gets its
  named target here: refund pending (async settle) vs missing.
- **Fault path:** Toxiproxy S1 between the participating service and its datastore
  (unmodified-system hunt); the Gate-1 SUT-flag injector is NOT used at G3.
- **Opportunity counts (spec-derived CANDIDATES; live-verify):** TT ≈ **6
  compensation/saga-bearing write flows** (preserve, preserveOther, cancel+refund,
  rebook, consign, inside-payment) + the 2 Gate-1 CRUD triples. Sock Shop = **3
  write sites, 0 compensation flows** (cart item; user sub-entities
  address/card/register; order create — fire-and-forget fan-out, no compensation).
  petclinic = **3 CRUD sites, 0 compensation flows** (owner, pet, visit). These
  counts ARE the §8.5-3 "how many genuine acked-but-lost opportunities" answer at
  pre-registration time; the live-verified counts ship with the benchmark.

## 1. SUT-2: Sock Shop (microservices-demo) — grounded in the in-repo harness

Harness: `evaluation/suts/sockshop/` (deploy into bookinfo's kind+Istio+Jaeger
cluster; swagger 26 ops / 12 writes; MIST conf + properties zero-hand-edits;
generalization already validated — catalogue tests all 200). Ops caveats: service
repos **archived Dec 2023** (pin images); front-end runs NODE_ENV-unset (dev mode).

**SUT-2 engineering items (FOUR — B MEDIUM-4 corrected the count):**
1. **(i) Read-back completeness** (§0) — bounded-collection/row-count via the BFF.
2. **(ii) Tracing depth — corrected per B MAJOR-1.** The W3C break is AT the Node
   front-end (it fronts every Java service), so javaagents on Java services alone
   yield DISCONNECTED traces. Mitigation (a) is therefore two-part and the
   **front-end half is load-bearing:** OTel Node auto-instrumentation on front-end
   (`NODE_OPTIONS --require @opentelemetry/auto-instrumentations-node/register`,
   OTLP → `jaeger-collector:4317` — istio-1.30's jaeger 2.14 ingests OTLP natively,
   B-verified) **plus** stock javaagents on carts/orders/shipping via
   `JAVA_TOOL_OPTIONS`. k8s realities (B claim-18): manifests set
   `readOnlyRootFilesystem: true` → agent jars come via initContainer+emptyDir;
   `JAVA_OPTS -Xms64m -Xmx128m` → bump heap with the agent; 2017-era Java-8 images →
   live-verify agent-version compatibility. **Fallback (b):** if (a) fails on this
   stack, all Sock Shop absences are TIMEOUT_ABSENT, disclosed per README §8.5-2 —
   and per §0 the SUT's bar is then NOT_EVALUABLE and Sock Shop does NOT count
   toward the ≥2-SUT requirement (no quiet downgrade).
3. **(iii) Session handling:** cart paths can use the **`?custId=` dev-mode
   override** (B INFO-2) — fresh custId per run, no cookies; POST /orders still
   requires a real session (register→login cookie) → small `MstAuthHandler`
   cookie-session extension.
4. **(iv) Ingress routes:** extend the VirtualService with `/register` + `/login`
   (+`/card`, `/address`) — the in-repo deploy routes only
   /catalogue /tags /cart /orders /customers /cards /addresses (B MEDIUM-4).

DB note (B MINOR-5): triple-relevant services (carts, orders, user) are
Mongo-backed; catalogue is Go+MySQL; rabbitmq and session-db are NOT mesh-excluded →
live-verify the AMQP leg under Envoy.

**SUT-2 sensitivity branch (C-pin 2, pre-registered):** Sock Shop has NO SUT-flag
injector; S2 is producible only invasively. Live-verify FIRST whether carts/orders
**2xx-mask a Toxiproxy'd Mongo failure** (that is the only S1 path to an
acknowledged-but-lost write here). Branch: (α) if they mask → S1 constructed
positives exist; sensitivity + comparator calibration + injected benchmark stratum
proceed on SUT-2. (β) if they honestly 5xx → **SUT-2 carries FP/breadth + the wild
hunt ONLY, with NO constructed-sensitivity claim**; comparator calibration stays
TT-only; the benchmark's SUT-2 injected stratum is empty and disclosed. Gated-mode
(S1 D-span) validation happens on whichever SUT yields an errored D-span under (a)
tracing — TT primary.

### Triple SS-A — cart add-item (headline-clean, Java leg) — SPEC-VERIFIED
- **Write:** `POST /cart` body `{id: <catalogue itemId>}` (front-end resolves the
  price, POSTs {itemId, unitPrice} to carts; 201). carts = Java Spring + carts-db
  Mongo (out of mesh → clean Toxiproxy TCP path).
- **Read-back:** `GET /cart` — the scope's item list, unpaginated, itemId visible
  (B-verified membership viability).
- **Isolation (pinned, C-pin 9/F9):** fresh scope **per run AND per benign-probe
  iteration** — a fresh `custId` (UUID) via the `?custId=` override (or a fresh
  registered user when sessions are exercised); **item-selection rule:** uniform
  random from the 10 seeded catalogue items (B claim-21), re-drawn per run;
  freshness/membership/isolation-violation checks re-based on the composite
  **(scope=custId, itemId)** key — X = "itemId present in THIS custId's cart";
  baseline = the fresh scope's cart (expected empty; isolation violation if not).
  **Live-verify cart-merge semantics** (duplicate add within a scope merges
  quantities — the membership key must remain presence-not-quantity).
- **Ack:** bare 2xx/201, no TT envelope → exercises the `bodyStatus==null` branch
  (recon R5); verify the error envelope live.

### Triple SS-B — address/card create (breadth, Go leg) — corrected per B MEDIUM-3
- **Write:** `POST /addresses` (or `/cards`) via BFF → user service (**Go**,
  user-db Mongo); FRESH_STRINGS on street/number/city/postcode/country applies.
- **Read-back:** `GET /addresses` is a **GLOBAL, seeded, growing list** (front-end
  proxies unfiltered — NOT session-scoped; baseline non-empty). SS-B therefore
  **explicitly inherits the §0 completeness prerequisite** (bounded-collection
  check), and FRESH_STRINGS membership stays valid at parallelism=1.
- **Caveats:** Go leg → no javaagent → absences mostly TIMEOUT_ABSENT (breadth
  only). `GET /customers/{id}` ignores `{id}` (reads the session) — do NOT use it
  as a read-back; `/register` needs engineering item (iv) to be routable.

### Triple SS-C — order create (multi-service fan-out; depth credential CONDITIONAL, C-pin 11)
- **Write:** `POST /orders` via BFF. B-verified: the front-end IGNORES the client
  body and rebuilds the order from the session (customer/address/card/items URIs) —
  so input control is via session state, not the POST body (the writer's
  beforeWrite body-freshening does NOT apply here; isolation = fresh
  customer+cart per run; a per-triple note for the hook design at G3).
  Fan-out (source-verified): orders → GET user(customer/address/card) +
  carts(items) → POST payment → POST shipping → save to orders-db; shipping
  enqueues to RabbitMQ; queue-master consumes.
- **Read-back:** BFF `GET /orders` = full per-customer array, unpaginated (§0);
  membership by fresh customer scope.
- **Async QUESTION — RESOLVED NEGATIVE at pre-registration (B claim-13):**
  queue-master persists NOTHING (consumes + simulates) and `order.shipment` is
  written at creation regardless of enqueue success → **no black-box read-back
  reflects broker consumption**. Same verdict shape as TT's P3. Therefore, per
  C-pin 11: **SS-C's §8.5-3 credential is "multi-service sync fan-out breadth,"
  NOT saga depth** — the depth story stays TT's (§0.5). The broker leg is
  topology fact only; no async-soundness claim rides on it.
- **Wild-defect candidate (B INFO-1 — record, do not oversell):** shipping
  **swallows enqueue failure** ("Accepting anyway. Don't do this for real!") while
  the order is acked and `shipment` is set — a NATURAL masked failure. It is
  black-box-INVISIBLE to the read-back oracle (no state reflects consumption) → it
  is a **masking-oracle / benchmark-stratum candidate** (2xx entry + errored
  AMQP-send span under (a) tracing), not a B2 read-back target. Pre-registered as
  such for the C2 benchmark and the Gate-3 wild-hunt inventory.

## 2. SUT-3: spring-petclinic-microservices — triple pre-spec completed (C-pin 13; B-verified)
- **Triples:** `POST /api/customer/owners` → 201 Owner → `GET
  /api/customer/owners` (+`/{id}`); `POST /api/customer/owners/{id}/pets` → 201 →
  owner read-back; `POST /api/visit/owners/*/pets/{petId}/visits` → 201 → `GET`
  visits per pet. Gateway routes verified (StripPrefix=2).
- **Ack:** bare 201 + JSON entity, no envelope → `bodyStatus==null` branch (R5).
- **Isolation:** FRESH_STRINGS (owner lastName/telephone; visit description);
  fresh owner per run.
- **Fault path:** Toxiproxy → MySQL (**run the `mysql` Spring profile**, not
  default HSQLDB in-mem, so a real persisting D exists).
- **Tracing:** Spring Boot 3 + Micrometer/OTel mentioned upstream (B: javaagent
  claim conservative) → full-depth traces expected.
- **Completeness:** GET /owners pagination unknown → live-verify; §0 bounded check
  applies regardless. Sync-only SUT (no broker) — breadth role.
- **TeaStore fallback:** persistence REST CRUD verified; WebUI is servlet/JSP (no
  gateway JSON API); Kieker-native; **its lists window** (`listAllEntities(start,
  max)`) → §0 pagination handling REQUIRED there (B claim-26).

## 3. Pre-registered decision + crisp change triggers (C-pin 9)
- **SUT-2 = Sock Shop; SUT-3 = spring-petclinic; fallback chain (pre-ordered):
  Sock Shop → TeaStore → petclinic-promoted-to-SUT-2.**
- **Triggers (time-boxed):** "unrunnable" = fails to reach the §4 item-0 healthy
  state within **2 working sessions / ~4 h of attempts** (logged); "unusable
  membership semantics" = fails any §4 live check: verbatim key echo, or
  unpaginated/exhaustible/boundable read-back, or no server-side key normalization.
  Any swap is documented HERE (disclosed amendment) before any run.

## 4. Live-verification checklist (before any G3 run; per triple)
0. Stand up via the in-repo harness: `evaluation/suts/bookinfo/deploy/deploy.sh` →
   `evaluation/suts/sockshop/deploy/deploy.sh` → port-forwards →
   `workload/capture-traces.sh` (idempotent). **Cluster lifecycle:** TT minikube
   stays up through G2 calibration → stopped → kind+Istio for G3 (26 GB budget;
   gate1-infra-incident lesson).
1. Engineering item (ii) BOTH halves: Node front-end auto-instr + Java javaagents;
   re-run the Gate-1 traceparent precheck ported to Sock Shop (exact-id lookup must
   show front-end AND carts/orders spans in ONE trace). On failure → fallback (b)
   disclosure + §0 NOT_EVALUABLE-SUT rule.
2. Engineering item (iv): extend the VirtualService (/register, /login, /card,
   /address); verify register→login→POST /cart→GET /cart round-trip echoes itemId
   verbatim; verify the `?custId=` override behavior and cart-merge semantics
   (SS-A pins).
3. **C-pin 2 sensitivity probe:** Toxiproxy-sever carts-db (then orders-db) during
   a write; record 2xx-mask vs honest-5xx; take branch (α)/(β) and document it here.
4. Ack semantics + error envelopes per triple (R5 port note); no server-side key
   normalization (A-Finding-5).
5. Engineering item (i): completeness support live-checked (BFF row-count bound;
   TeaStore windowing if swapped in; petclinic pagination check).
6. Toxiproxy placement per triple produces the S1 fault and clears back to health
   (F2-style hygiene); AMQP-under-Envoy check (B MINOR-5).
7. Benign probe per SUT under the §0 per-SUT FP protocol (N=30, bar v2, never
   pooled).

*Review trail: v1 → 3 independent cold reviewers (A/B/C) → reconciliation
(REVIEW-PREREG-RECONCILIATION.md) → this v2. Sources: in-repo
evaluation/suts/sockshop bundle (primary); microservices-demo service sources
(front-end/orders/shipping/queue-master/user/catalogue); istio release-1.30 jaeger
addon; spring-petclinic-microservices source; TeaStore source — all fetched/verified
2026-07-02 by reviewer B.*
