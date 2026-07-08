# G3 SUT-2 (Sock Shop) deploy + live-verification record — 2026-07-02

Base cluster + Sock Shop stood up per the in-repo harness
(`evaluation/suts/bookinfo/deploy/deploy.sh` → `evaluation/suts/sockshop/deploy/deploy.sh`);
G3 write-path fixes applied via
[`evaluation/suts/sockshop/deploy/g3-write-path-enable.sh`](../../../evaluation/suts/sockshop/deploy/g3-write-path-enable.sh)
(idempotent overlay — the base bundle validates the read-only catalogue path; the
write study needs the two fixes below). Fresh kind cluster `mist` (the dead exited-137
one was deleted), Istio 1.30 demo profile + Jaeger, all pods Running. Ingress
`localhost:8080`, Jaeger `localhost:16686`.

## Fix 1 — Mongo image pin (prereg §1 "pin images", live-confirmed)
`complete-demo.yaml` leaves `carts-db`/`orders-db` as `image: mongo` (untagged →
`latest` → **v8.2.11**). MongoDB ≥5.1 removed the legacy **OP_QUERY** opcode that the
2017-era `carts:0.4.8` / orders drivers still send, so every Mongo-backed write threw
HTTP 500 (`UncategorizedMongoDbException … Unsupported OP_QUERY command: find …
legacy-opcode-removal`). Catalogue (Go+MySQL) was unaffected — which is why the base
smoke (`GET /catalogue` 200) passed while writes were dead. **Fix:** pin
`carts-db`/`orders-db` → `mongo:3.4` (the version Sock Shop shipped with; restores the
original wire protocol) and restart carts/orders (they connect at startup).
`user-db` = the self-contained `weaveworksdemos/user-db:0.3.0`, left as-is.

## Fix 2 — auth + write ingress routes (engineering item (iv), DONE)
The base VirtualService routed only `/catalogue /tags /cart /orders /customers /cards
/addresses`; `POST /register` → 404, `/login` → 405 (unrouted). Extended the
VirtualService with `/register /login /card /address`. `POST /register` now → **200**
`{"id":…}` + a `logged_in` cookie.

## SS-A cart triple — VIABLE (validated end-to-end)
Live round-trip (cookie session):
- `POST /register {username,password,email,firstName,lastName}` → 200 + `logged_in` cookie.
- baseline `GET /cart` (cookie) → `[]`.
- `POST /cart {id:<catalogue itemId>}` (cookie) → **201**, empty body.
- `GET /cart` (cookie) → `[{"id":…,"itemId":"03fef6ac-…","quantity":1,"unitPrice":99.99}]`
  — **the submitted itemId is PRESENT** (read-back membership viable), and the entry
  echoes the price (front-end resolved it → the {itemId,unitPrice} fan-out to carts is
  confirmed).

**Findings vs the prereg (SS-A):**
- **CORRECTED — `?custId=` isolation does NOT hold on this build.** The prereg (B
  INFO-2) planned a fresh `custId` via the `?custId=` dev override to avoid cookies.
  Live: `POST /cart?custId=X` returns 201 but the item lands under a front-end-resolved
  internal customer id (an earlier trace showed `/carts/AaUC2G8Lr…/items`, not `X`),
  and `GET /cart?custId=X` reads a different (empty) cart → membership always ABSENT.
  **Resolution:** SS-A isolation key = **a fresh registered user per run** (register →
  cookie), which is validated above. This is also the isolation SS-C (orders) needs, so
  the cookie-session path is adopted for the Sock Shop write triples. (A disclosed
  amendment to the prereg's SS-A isolation mechanism; the triple itself is unchanged.)
- **CONFIRMED — ack shape.** Bare `201`, no envelope → exercises MIST's
  `bodyStatus==null` branch (recon R5), as pre-registered.
- **CONFIRMED — membership viable.** `GET /cart` returns the itemId verbatim in an
  unpaginated per-scope list.

## Sensitivity probe (C-pin 2, prereg §4 item 3) — **branch β TAKEN**
Question: does the carts write path 2xx-**mask** a Mongo failure (→ constructed
acknowledged-but-lost writes exist, branch α) or honestly 5xx (branch β)? **Two
independent data points, both HONEST 5xx:**
1. Under `mongo:8` the driver's OP_QUERY was rejected → `POST /cart` returned **500**
   (`Unable to add to cart. Status code: 500`).
2. With `carts-db` scaled to 0 (unreachable) during a write → `POST /cart` returned
   **500** on 3/3 tries.

carts **does not acknowledge** a write it cannot persist — it propagates the failure
as HTTP 500. There is therefore **no S1 path to an acknowledged-but-lost write** on the
cleanest Java+Mongo candidate (SS-A/carts). **Decision — branch β** (pre-registered):
> SUT-2 (Sock Shop) carries **FP/breadth + the wild-defect hunt ONLY**, with **NO
> constructed-sensitivity claim**; comparator calibration stays **TrainTicket-only**;
> the benchmark's SUT-2 injected stratum is **empty and disclosed**. The depth +
> constructed-fault story remains TrainTicket's (prereg §0.5 cancel→refund).

Scope note (honest): the probe was the DB-unreachable (loss) shape via scale-to-0, not
Toxiproxy — a valid S1 loss fault; a service that 5xxes on connection-refused does not
2xx-mask, so the Toxiproxy-latency refinement is very unlikely to flip β→α. `orders`
(SS-C) was not separately faulted (a full checkout state is needed to POST an order);
its role was already pre-registered as **sync fan-out breadth, not saga depth**
(prereg §1 SS-C, async question resolved negative), so β for the SUT does not hinge on
it. SUT-2 still contributes to Gate-3 as an **FP/generalization SUT** (benign probe
N=30) + the wild-hunt inventory (shipping swallowed-enqueue), which is what the ≥2-SUT
breadth claim needs; recall/detection leans on TrainTicket + the wild defects.

## Status / next (prereg §4)
- item 0 (healthy state): **DONE** (with the two fixes).
- item (iv) ingress routes: **DONE**.
- item (iii) sessions: cookie-session path **validated** for cart; the
  `MstAuthHandler` cookie-session wiring for the MIST run is the remaining code piece.
- item 3 sensitivity: **DONE — branch β** (above).
- **NEXT:** item (ii) two-part tracing (Node front-end auto-instr + Java javaagents —
  under β this is breadth/FP-quality, not depth-critical); item (i) completeness (the
  runtime's `readback_bound` already implements the bounded check); then the benign
  probe (N=30, bar v2) for the FP claim, and the wild-defect confirmation. The SUT-2
  **blind set** (comparator) is **not needed under β** (comparator calibration is
  TT-only) — an honest scope reduction from the pre-registration.

Cluster left UP (kind `mist`) for the continuation; minikube stays stopped.

## ADDENDUM (2026-07-04, result-review C-M1): the SUT-2 blind set WAS later authored — for SHIPPING
"The SUT-2 **blind set** is **not needed under β**" above was the honest scope at the time and referred
to the CARTS-based sensitivity branch (carts honestly 5xxes → no constructed stratum THERE). The
shipping enqueue-swallow was subsequently PROMOTED from wild-hunt inventory to a full depth
head-to-head, for which a blind shipping contract WAS authored (freeze-before-reveal) and a constructed
(reject-publish) stratum WAS run — see `debug/a-main/g3-comparator-ss/g3-shipping-headtohead-results.md`.
β's carts finding is unchanged; the scope line no longer bounds SUT-2 as a whole.
