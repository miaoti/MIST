# Rider-2 bindability survey — the FULL frozen TrainTicket blind set vs the closed primitive set

**What this is.** The analysis product of Rider-2 §1 (`prep/g3-rider2-comparator-protocol.md`,
pre-registered 2026-07-02) over the ENTIRE frozen blind assertion set
(`g2-comparator/blind-assertions-trainticket.yaml` @ freeze commit `15954a8`, 81 mutating-endpoint
entries × 22 services): for every entry, can the frozen **state postcondition** be expressed in the
comparator's closed primitive set at all? This is the **bindability fraction** — the prepared
answer to the round-2 head-to-head reviewers' external-validity question ("how often are
observables delta-only / unbindable beyond the cancel scenario?"). Status: **PRELIMINARY — needs
the standing ≥3-cold-review before feeding claims.** The per-endpoint EXECUTABLE bindings YAML is
authored when the breadth comparison run is built (the protocol requires it before that run, not
before this analysis); this survey fixes the dispositions the YAML must implement.

## Scoring conventions (disclosed up front)

Closed primitive semantics (from `ContractEvaluator` — the pre-registered evaluator):
`HTTP_STATUS` / `ENVELOPE_STATUS` / `ENVELOPE_DATA` (nullity only) / `MSG_CONTAINS` on the WRITE
response; `STATE_GET` follow-up GET whose path resolves `${field:NAME}` **from the submitted body
only**, with expect ∈ presence (`contains-submitted-fields`, retried at the matched 10 s/500 ms
budget) / `entity-matches-submitted-fields` / absence (single-shot); field matching is by
**identical field name + value** between the submitted map and the read-back JSON. No pre-write
snapshot seam, no arithmetic/delta, no value-transition assertion, no POST read-backs, no
response-derived paths.

Submitted map convention:
- **G (generous, primary):** request body fields + path parameters under their template names,
  **plus an alias** of a path parameter onto the entity's JSON field name where the frozen text
  itself equates them (e.g. `{stationsId}` ≡ the station's `id`). Generosity is pro-MIST-adversarial
  (Rider-2 §1: it makes the comparator harder to beat), so the UNbindable remainder under G is the
  maximally credible one. Every alias is marked.
- **S (strict, secondary):** template names as-is, no aliases (the convention the G2 calibration
  used). Reported alongside.
- Where the frozen text leaves a field-name correspondence UNKNOWN, the entry is counted as
  **BINDS** (generous default, disclosed as low-confidence) — doubt is resolved AGAINST MIST.
- BINDS means ≥1 frozen state observable for the entry is expressible such that an acked-but-lost
  write of that entry's PRIMARY persistence effect would be caught (membership/echo/absence).
  BINDS-P = partial (membership binds; some frozen observables — derived values, side channels —
  do not; the lost write is still caught). NC = NO frozen state observable expressible → an
  acked-but-lost write on this endpoint is INVISIBLE to the comparator class even though the frozen
  contract SPECIFIES the state.

## Per-endpoint state-postcondition dispositions

`a` = needs the G-convention alias (NC under S). Response clauses: all 81 bind
(status+msg gates; travel/travel2/admintravel duplicate-quirk entries bind via the msg gate since
status stays 1 — disclosed in the frozen text itself). Failure contracts: bind as
ENVELOPE_STATUS 0 + MSG_CONTAINS except where the frozen text documents no failure branch (price
POST, consign×2, consignprice, auth create/delete: N/A) — none of those affects the state fraction.

| # | endpoint (abbrev.) | state | note |
|---|---|---|---|
| 1 | station POST | BINDS | list membership (name, stayTime) |
| 2 | station PUT | BINDS | list echo (id, name, stayTime) |
| 3 | station DELETE | BINDS a | list absence; {stationsId}→id |
| 4 | route POST | BINDS | list membership (fresh startStation/endStation); full-length client id → per-entity GET too |
| 5 | route DELETE | BINDS a | absence; {routeId}→id |
| 6 | train POST | BINDS | list + per-entity (client-supplied id) |
| 7 | train PUT | BINDS | per-entity echo |
| 8 | train DELETE | BINDS | {id} = entity `id` |
| 9 | contacts POST | BINDS | account-list membership (G2-calibrated) |
| 10 | contacts POST /admin | BINDS | list membership |
| 11 | contacts PUT | BINDS | per-entity echo by submitted id |
| 12 | contacts DELETE | BINDS a | {contactsId}→id |
| 13 | order POST | BINDS | list-all membership (fresh accountId); per-`data.id` GET part NOT expressible (response-keyed) |
| 14 | order POST /admin | BINDS | list-all membership |
| 15 | order PUT | BINDS | per-entity echo by submitted order.id |
| 16 | order PUT /admin | BINDS | per-entity echo |
| 17 | order DELETE | BINDS a | {orderId}→id |
| 18 | order GET orderPay [mutating GET] | **NC-TRANSITION** | postcondition = status flips to PAID; PAID code is NOT submitted → no primitive asserts a specific NEW value vs pre-state. (The notes' modifyOrder variant `/order/status/{orderId}/{status}` WOULD bind — status is a path param.) |
| 19 | orderOther POST | BINDS | list membership |
| 20 | orderOther POST /admin | **NC-RESPONSE-KEYED** | the only frozen observable is `GET /orderOther/{data.id}` with data.id a fresh SERVER-generated UUID; `${field:…}` resolves from the submission only → path unbuildable |
| 21 | orderOther PUT | BINDS | per-entity echo |
| 22 | orderOther PUT /admin | BINDS | per-entity echo |
| 23 | orderOther DELETE | BINDS a | {orderId}→id |
| 24 | config POST | BINDS | per-entity by submitted name |
| 25 | config PUT | BINDS | per-entity echo |
| 26 | config DELETE | BINDS a | {configName}→name |
| 27 | travel POST | BINDS | per-`{tripId}` GET (tripId submitted) + echo fields (routeId, stations, times) |
| 28 | travel PUT | BINDS | echo fields (trainTypeName excluded — frozen UNKNOWN) |
| 29 | travel DELETE | **NC-KEY-SHAPE** | only submitted field = tripId "G1237"; the Trip entity's id serializes as a NESTED object {type,number} → no identical-name flat field can match; value transformation (strip 'G') inexpressible |
| 30 | travel2 POST | BINDS | as 27 |
| 31 | travel2 PUT | BINDS | as 28 |
| 32 | travel2 DELETE | **NC-KEY-SHAPE** | as 29 |
| 33 | price POST | BINDS | list + per-{routeId}/{trainType} GET from submitted fields |
| 34 | price PUT | BINDS | as 33 |
| 35 | price DELETE | BINDS a | {pricesId}→id (routeId/trainType not submitted on delete) |
| 36 | food order POST | BINDS | per-{orderId} GET; FoodOrder carries `orderId` as a real field |
| 37 | food createOrderBatch | **NC-BATCH-SHAPE** | submission is a JSON ARRAY of orders; the closed set has no iteration/multi-entity construct and `${field:…}`/field-matching assume one flat submitted object |
| 38 | food order PUT | BINDS | per-{orderId} echo |
| 39 | food order DELETE | BINDS | absence by `orderId` (real field name — no alias) |
| 40 | consign POST | BINDS-P | order/account-list membership binds; `data.price` is DERIVED (computed by consign-price) → that observable inexpressible |
| 41 | consign PUT | BINDS-P | echo binds; recomputed price NC-DERIVED |
| 42 | consignprice POST | BINDS-P | /consignprice/config echo binds; derived description-string + pricing-behavior observables NC-DERIVED |
| 43 | user register POST | BINDS-P | per-{userName} GET echo binds; "login works" side effect = POST → inexpressible |
| 44 | user PUT | BINDS | per-{userName} echo |
| 45 | user DELETE | BINDS | absence by `userId` (real field name) |
| 46 | auth create POST | BINDS | membership in GET /api/v1/users by submitted username (field-name correspondence not pinned by the frozen text → counted BINDS, low-confidence) |
| 47 | auth login POST | — | frozen: "none — token issuance only"; no state clause → excluded from the denominator |
| 48 | auth user DELETE | BINDS-P | list absence binds; "login stops working" = POST → inexpressible |
| 49 | assurance create [mutating GET] | BINDS | per-orderid GET membership by `orderId` (real field) |
| 50 | assurance PATCH | BINDS | per-orderid membership (runner submits a fresh orderId → discriminating) |
| 51 | assurance DELETE by assuranceId | BINDS a | list absence; {assuranceId}→id |
| 52 | assurance DELETE by orderId | BINDS | per-orderid absence by `orderId` (real field) |
| 53 | security POST | BINDS | list membership |
| 54 | security PUT | BINDS | list echo by submitted id |
| 55 | security DELETE | BINDS | {id} = entity `id` |
| 56–70 | adminbasic 15× (contacts/stations/trains/configs/prices × POST/PUT/DELETE) | BINDS (3 deletes a) | pass-through list membership/echo/absence on GET /adminbasic/*; deletes: {contactsId}→id a, stations/trains `{id}` no alias, configs `{name}` no alias, {pricesId}→id a |
| 71 | adminorder POST | BINDS | aggregate-list membership (fresh accountId) |
| 72 | adminorder PUT | BINDS | aggregate echo by submitted id |
| 73 | adminorder DELETE | BINDS a | {orderId}→id |
| 74 | adminroute POST | BINDS | list + per-entity (G2-calibrated) |
| 75 | adminroute DELETE | BINDS a | {routeId}→id |
| 76 | admintravel POST | BINDS | merged-list membership by echoed fields (response gate weak — duplicate reports success; the STATE clause is what discriminates, disclosed in frozen text) |
| 77 | admintravel PUT | BINDS | merged-list echo |
| 78 | admintravel DELETE | **NC-KEY-SHAPE** | as 29 (nested TripId in the merged list) |
| 79 | adminuser POST | BINDS | list membership by submitted userName |
| 80 | adminuser PUT | BINDS | list echo |
| 81 | adminuser DELETE | BINDS | downstream per-{userId} absence (real field name) |

## The fraction

Denominator = 80 entries with a frozen state postcondition (81 − login).

| convention | state clause BINDS (incl. partial) | NC (state invisible) |
|---|---|---|
| **G (generous, primary)** | **74 / 80 = 92.5 %** | **6 / 80 = 7.5 %** |
| S (strict, no aliases) | 62 / 80 = 77.5 % | 18 / 80 = 22.5 % (6 NC + 12 alias-dependent deletes) |

NC-under-G census (the structural residue no generosity fixes): **3× KEY-SHAPE**
(travel/travel2/admintravel deletes, #29/#32/#78), **1× TRANSITION** (orderPay, #18), **1×
RESPONSE-KEYED** (orderOther admin create, #20), **1× BATCH-SHAPE** (#37) — total 6. The 12
alias-dependent deletes (#3, 5, 12, 17, 23, 26, 35, 51, 58, 70, 73, 75) count as BINDS under G,
NC-FIELD-MISMATCH under S. Plus, on the BINDS-P entries, the observables that never bind under
either convention: DERIVED values (consign price ×2, consignprice pricing) and out-of-band effects
(register-login works / auth-delete login stops).

## Reading (two-sided, honest)

1. **The comparator class is a STRONG baseline on plain entity CRUD** — 92.5 % of the frozen set's
   state clauses bind under the generous convention (membership/echo/absence with same-name keys).
   This is the breadth-scale version of the agreement anchor: on ordinary create/update/delete
   surfaces, response-assertion + STATE_GET catches acked-but-lost writes, and any head-to-head
   claim must (and does) concede it. No strawman.
2. **The unbindable residue is not random — it concentrates on exactly the flows where silent loss
   matters.** The structural NC categories are value TRANSITIONS (`orderPay` — the PAYMENT status
   flip is invisible: the new value isn't in the submission), server-KEYED/derived observables, and
   non-flat key shapes. And the frozen set's own coverage note places the deepest such flows —
   `ts-inside-payment`, `ts-cancel`, `ts-preserve`, `ts-rebook`, `ts-payment`, `ts-seat` (the
   money-moving orchestration/compensation surface) — in `not_covered`: they are not plain-CRUD
   bindable surfaces at all. The depth head-to-head's cancel→refund defect lives there: bodyless
   write, JWT-scoped aggregate read-back, refund observable ONLY as a balance DELTA — the
   depth-cell bindings record all three of its state clauses NOT_CHECKABLE
   (`assertion-bindings-cancel-refund.yaml`), and MIST's differential value-delta is precisely the
   primitive that covers this residue class.
3. So the external-validity answer to "how often is the clean-win shape encountered?": on THIS
   SUT's frozen surface, ~7.5 % of specified state clauses are structurally unbindable even
   generously (~22.5 % strictly), and the entire payment/compensation axis sits outside the
   bindable CRUD surface. MIST is positioned as COMPLEMENTARY coverage of that residue (consistent
   with the accepted framing rule: complementary, not a superset — on the 92.5 % the comparator is
   fine; on loud failures it can be better).

## Protocol notes

- Failure contracts (Rider-2 §1, review-B finding 7): bindable for every entry that documents a
  reject envelope (ENVELOPE_STATUS 0 + MSG_CONTAINS the frozen reason; travel-family duplicates
  bind via MSG_CONTAINS alone since status stays 1). Entries with no failure branch in source
  (price POST upsert, consign POST/PUT upsert, consignprice POST, auth create/delete) have nothing
  to bind — recorded N/A, not NOT_CHECKABLE. These will be carried into the executable YAML so a
  contract-correct rejection is never scored as a comparator flag.
- This survey scores EXPRESSIBILITY (can the clause be written in the closed set), not runtime
  reliability (weak duplicate keys, list-growth costs, eventual consistency are runtime concerns
  the A3 retry budget + control legs handle).
- Amendment discipline: any disposition change after this file is a disclosed amendment; the
  executable YAML must implement exactly these dispositions or disclose the delta.
