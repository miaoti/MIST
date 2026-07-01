# PREP (main-track) — candidate target triples for the differential data-integrity oracle

> **Main-track (A-conference) prep — NO tool code.** Pure analysis of the bundled TrainTicket OpenAPI spec
> (`mist-cli/src/main/resources/My-Example/trainticket/merged_openapi_spec 1.yaml`, 265 ops) to pick the
> first `(write endpoint, persisting dependency, read-back GET)` triples for Gate 1 of the differential
> data-integrity oracle (plan §4 / EXECUTION.md G0). Final selection happens at G0 against a *live* trace
> (to confirm the actual downstream DB dependency per service). Branch: `main_track`. Date: 2026-06-30.
>
> Reminder: building B1/B2 to exercise these is **BLOCKED until the user says "yes"** — this doc is design prep.

## What the oracle needs from a triple
1. A **state-mutating** request (POST/PUT/PATCH/DELETE) that returns 2xx on success.
2. A **persisting downstream dependency** D (a service's DB write) we can fault black-box (Toxiproxy on D's DB socket).
3. A **read-back GET** that reflects whether the write durably happened.
4. **Isolation-friendliness**: ideally a per-entity create with a unique ID → fresh entity per test, clean
   read-back, low cross-run interference (plan §8.5 / R3: shared-inventory state breaks black-box isolation).

The fire condition **[superseded — see TOOL-PLAN §3 B2.3 for the authoritative two-mode rule; the below is the
gated/S1 mode ONLY]**: fault-run returns 2xx **and** D's span errored/aborted **and** the read-back shows the
entity absent/stale/partial. The **headline pure-differential / S2** mode **drops the D-error conjunct** (in
S2 D is never called → no D span); it fires on 2xx-acknowledging-X **and** X absent on its own read-back, with
the control run as FP-guard.

## TrainTicket write surface (from the spec)
74 POST · 27 PUT · 26 DELETE · 2 PATCH · 134 GET. The admin* and *service CRUD resources give clean
create↔read-back pairs, each backed by its own service DB.

## Candidate triples (ranked for Gate 1)

| # | Write endpoint | Persisting dependency D | Read-back GET | Isolation | Notes |
|---|---|---|---|---|---|
| **A (recommended #1 — known endpoint)** | `POST /api/v1/adminrouteservice/adminroute` (addRoute, body `RouteInfo`) | `ts-admin-route-service` → `ts-route-service` DB | `GET /api/v1/adminrouteservice/adminroute` (getAllRoutes) — check the new route is present | MED — `routeId` is derived from start/end stations; use unique station pairs per test; `DELETE …/{routeId}` exists for cleanup | **We already have `admin_add_route_failed.json` traces** (probe-attribution). Read-back is collection-level (no item-GET on adminroute), so verify presence in getAllRoutes |
| **B (2nd positive)** | `POST /api/v1/adminbasicservice/adminbasic/contacts` (create contact) | `ts-admin-basic-info-service` → `ts-contacts-service` | `GET /api/v1/adminbasicservice/adminbasic/contacts` (**collection** `getAllContacts` — spec has **NO** per-entity `GET /contacts/{id}` on adminbasic, only `delete`; cold-review A) | MED — collection read-back, membership by business key (accountId+documentNumber) | Same collection pattern as adminroute; **no isolation advantage over A** |
| C | `POST /api/v1/consignservice/consigns` (create consign) | `ts-consign-service` DB | `GET /api/v1/consignservice/consigns/order/{id}` or `/account/{id}` | HIGH — keyed by order/account id | Good per-key read-back; richer record (price/weight) to diff |
| D | `POST /api/v1/adminbasicservice/adminbasic/prices` (create price) | `ts-price-service` DB | `GET …/adminbasic/prices/{pricesId}` | HIGH | Same clean pattern as B |
| E | `POST /api/v1/contactservice/contacts` (non-admin create) | `ts-contacts-service` DB | `GET /api/v1/contactservice/contacts/{contactsId}` or `/account/{accountId}` | HIGH | Mirror of B via the user-facing path |
| — (avoid for first cut) | `adminbasic/stations`, `/trains` | reference-data services | GET /{id} | LOW — shared reference data, poor isolation | Use later, not for the soundness baseline |

## Recommendation for Gate 1
- **Sanity target = A (adminroute):** we already have traces and the probe history; good for first end-to-end
  wiring even though read-back is collection-level.
- **Soundness FP target = business-key collection membership on adminroute/adminbasic** (both are collection
  read-back — adminbasic has no per-entity GET, cold-review A). A true per-entity GET exists only on
  `ts-contacts-service` (row E) and `consigns` (row C), but **neither has a LOST_WRITE injector** — using them
  is extra SUT work, deferred. The make-or-break FP number (plan §8.5) is still obtainable via collection
  membership, just without a per-entity cleanliness bonus.
- Run the FP measurement on adminroute + adminbasic (both collection membership); **adminroute is the
  smoke-demonstrated case; adminbasic's read-back demonstration is still pending** (see
  sut-fault-injection-capability §9).

## Open items to resolve at G0 (need a live trace / the SUT up)
1. Confirm, from a live trace of each POST, the **actual** persisting span (which service writes to which DB)
   — the spec's `x-service-name` names the front service, not necessarily the DB-writing one.
2. Confirm the read-back GET reflects the write **synchronously** (no async/CQRS lag) — else apply the §4
   trace-driven quiescence + bounded-wait re-read; record which path was used.
3. Confirm `ts-*-service` DB connections are interceptable by Toxiproxy in the `make deploy` topology
   (see the environment runbook, task #15).
4. Pick the exact unique-ID scheme per triple for per-test isolation.

*Status: candidate set drafted from the spec; final pick deferred to G0 (live-trace confirmation). This is
prep only — no tool code touched.*
