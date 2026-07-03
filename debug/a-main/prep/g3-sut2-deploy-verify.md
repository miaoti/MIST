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

## Status / next (prereg §4)
- item 0 (healthy state): **DONE** (with the two fixes).
- item (iv) ingress routes: **DONE**.
- item (iii) sessions: cookie-session path **validated** for cart; the
  `MstAuthHandler` cookie-session wiring for the MIST run is the remaining code piece.
- **NEXT:** item (ii) two-part tracing (Node front-end auto-instr + Java javaagents —
  the load-bearing half); item 3 the C-pin-2 sensitivity probe (Toxiproxy carts-db:
  does `POST /cart` 2xx-mask a Mongo failure? → branch α/β); item (i) completeness;
  then author + freeze the SUT-2 blind set (A1) and the benign probe (N=30).

Cluster left UP (kind `mist`) for the continuation; minikube stays stopped.
