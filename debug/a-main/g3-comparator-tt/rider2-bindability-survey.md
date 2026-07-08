# Rider-2 bindability survey — the FULL frozen TrainTicket blind set vs the closed primitive set

**What this is.** The analysis product of Rider-2 §1 (`prep/g3-rider2-comparator-protocol.md`,
pre-registered 2026-07-02) over the ENTIRE frozen blind assertion set
(`g2-comparator/blind-assertions-trainticket.yaml` @ freeze commit `15954a8`, 81 mutating-endpoint
entries × 22 services): for every entry, can the frozen **state postcondition** be expressed in the
comparator's closed primitive set at all? This is the **bindability fraction** — the prepared
answer to the round-2 head-to-head reviewers' external-validity question ("how often are
observables delta-only / unbindable beyond the cancel scenario?"). Status: **ALL THREE cold
reviewers returned ACCEPT-WITH-FIXES and every finding is folded in** — A+B converged on the
OBJECT-ABSENCE three-row flip (#12/#23/#52), C additionally caught the NESTED-ITEM-SHAPE pair
(#76/#77); all five flips run AGAINST the comparator, so the corrected residue is LARGER and the
qualitative reading unchanged (reconciliation: `REVIEW-SURVEY-RECONCILIATION.md`). The
per-endpoint EXECUTABLE bindings YAML is authored when the breadth comparison run is built (the
protocol requires it before that run, not before this analysis); this survey fixes the
dispositions the YAML must implement.

Count note (review A/B): the freeze-commit message and Rider-2 §1 say "79 endpoints"; the frozen
file (byte-stable since `15954a8`) contains **81 entries** — the 79 plausibly excluded the two
outside-the-POST/PUT/DELETE-brief entries (login #47, orderPay #18). This survey covers all 81
(the conservative, no-endpoint-dropped reading).

## Scoring conventions (disclosed up front)

Closed primitive semantics (from `ContractEvaluator` — the pre-registered evaluator):
`HTTP_STATUS` / `ENVELOPE_STATUS` / `ENVELOPE_DATA` (nullity only) / `MSG_CONTAINS` on the WRITE
response; `STATE_GET` follow-up GET whose path resolves `${field:NAME}` **from the submitted body
only**, with expect ∈ presence (`contains-submitted-fields`, retried at the matched 10 s/500 ms
budget) / `entity-matches-submitted-fields` / absence (single-shot); field matching is by
**identical field name + value** between the submitted map and the read-back JSON. No pre-write
snapshot seam, no arithmetic/delta, no value-transition assertion, no POST read-backs, no
response-derived paths.

**Collection-shape rule (review A+B, BLOCKING — now explicit):** the membership machinery iterates
`DataIntegrityRuntime.extractItems`, which returns an **empty list whenever the envelope `data` is
not a JSON array** (`DataIntegrityRuntime.java:885-887`). Consequences the dispositions below obey:
- an **absence** check binds ONLY against a **collection-shaped** read-back (a list the entity
  should vanish from). Absence "bound" to a single-object per-entity read is VACUOUS — the
  still-present object yields zero items → `present=false` → PASS on both legs (a lost delete is
  never caught).
- a **presence** check on a single-object read-back must use `entity-matches-submitted-fields`
  (`contains-submitted-fields` is vacuously FALSE there and would false-flag healthy control legs).
  The executable YAML must implement per-entity presence reads as entity-matches.

Submitted map convention:
- **G (generous, primary):** request body fields + path parameters under their template names,
  **plus an alias** of a path parameter onto the entity's JSON field name where the frozen text
  itself equates them (e.g. `{stationsId}` ≡ the station's `id`). Generosity is pro-MIST-adversarial
  (Rider-2 §1: it makes the comparator harder to beat), so the UNbindable remainder under G is the
  maximally credible one. Every alias is marked. Aliases only RENAME a submitted value — they never
  import response-derived, pre-state, or harness-provisioning data (e.g. an accountId the DELETE
  never submitted stays unavailable).
- **S (strict, secondary):** template names as-is, no aliases (the convention the G2 calibration
  used). Reported alongside.
- Where the frozen text leaves a field-name correspondence UNKNOWN, the entry is counted as
  **BINDS** (generous default, disclosed as low-confidence) — doubt is resolved AGAINST MIST.
- Each entry is scored on ITS OWN frozen clause text only — observables from a different entry's
  contract are never borrowed (applied symmetrically: it is what makes #20 NC, and it is why #23
  cannot use #19's list).
- BINDS means ≥1 frozen state observable for the entry is expressible such that an acked-but-lost
  write of that entry's PRIMARY persistence effect **would be caught** (membership/echo/collection-
  absence). BINDS-P = partial (the catching observable binds; some other frozen observables —
  derived values, side channels, response-keyed reads — do not). NC = NO frozen state observable
  expressible → an acked-but-lost write on this endpoint is INVISIBLE to the comparator class even
  though the frozen contract SPECIFIES the state.

## Per-endpoint state-postcondition dispositions

`a` = needs the G-convention alias (NC under S). Response clauses: all 81 bind (status+msg gates;
travel/travel2/admintravel duplicate-quirk entries bind via the msg gate since status stays 1 —
disclosed in the frozen text itself). Operational caveat (review A): #37's submission is a JSON
ARRAY, and the evaluator parses the submitted body as a flat object up front — the breadth-run YAML
must pass a synthetic flat submitted map for #37's response clauses or record the crash. Failure
contracts: bind as ENVELOPE_STATUS 0 + MSG_CONTAINS except where the frozen text documents no
failure branch (price POST, consign×2, consignprice, auth create/delete: N/A) — none of those
affects the state fraction.

| # | endpoint (abbrev.) | state | note |
|---|---|---|---|
| 1 | station POST | BINDS | list membership (name, stayTime) |
| 2 | station PUT | BINDS | list echo (id, name, stayTime) |
| 3 | station DELETE | BINDS a | LIST absence (GET /stations); {stationsId}→id |
| 4 | route POST | BINDS | list membership (fresh startStation/endStation); full-length client id → per-entity GET too |
| 5 | route DELETE | BINDS a | LIST absence (GET /routes); {routeId}→id |
| 6 | train POST | BINDS | list + per-entity (client-supplied id) |
| 7 | train PUT | BINDS | per-entity echo (entity-matches) |
| 8 | train DELETE | BINDS | LIST absence (GET /trains); {id} = entity `id` |
| 9 | contacts POST | BINDS | account-list membership (G2-calibrated) |
| 10 | contacts POST /admin | BINDS | list membership |
| 11 | contacts PUT | BINDS | per-entity echo by submitted id (entity-matches) |
| 12 | contacts DELETE | **NC-OBJECT-ABSENCE** | (review A+B flip) frozen observables: per-entity `GET /contacts/{contactsId}` — single-object `data` → absence VACUOUS; "absent from account list" — path needs `accountId`, NOT submitted on the DELETE → unbuildable. No discriminating observable |
| 13 | order POST | BINDS-P | list-all membership (fresh accountId); per-`data.id` GET part response-keyed → inexpressible |
| 14 | order POST /admin | BINDS-P | list-all membership; per-`data.id` GET part response-keyed → inexpressible |
| 15 | order PUT | BINDS | per-entity echo by submitted order.id |
| 16 | order PUT /admin | BINDS | per-entity echo |
| 17 | order DELETE | BINDS a | LIST absence (GET /order); {orderId}→id |
| 18 | order GET orderPay [mutating GET] | **NC-TRANSITION** | postcondition = status flips to PAID; PAID code is NOT submitted → no primitive asserts a specific NEW value vs pre-state. (The notes' modifyOrder variant `/order/status/{orderId}/{status}` WOULD bind — status is a path param.) |
| 19 | orderOther POST | BINDS | list membership |
| 20 | orderOther POST /admin | **NC-RESPONSE-KEYED** | the only frozen observable is `GET /orderOther/{data.id}` with data.id a fresh SERVER-generated UUID; `${field:…}` resolves from the submission only → path unbuildable |
| 21 | orderOther PUT | BINDS | per-entity echo |
| 22 | orderOther PUT /admin | BINDS | per-entity echo |
| 23 | orderOther DELETE | **NC-OBJECT-ABSENCE** | (review A+B flip) the ONLY frozen observable is per-entity `GET /orderOther/{orderId}` → single-object `data` → absence VACUOUS. (#19's list observable belongs to #19's contract — not borrowable, same rule that makes #20 NC) |
| 24 | config POST | BINDS | per-entity by submitted name (entity-matches) |
| 25 | config PUT | BINDS | per-entity echo |
| 26 | config DELETE | BINDS a | LIST absence (GET /configs); {configName}→name |
| 27 | travel POST | BINDS | per-`{tripId}` GET (tripId submitted) + echo fields (routeId, stations, times) via entity-matches |
| 28 | travel PUT | BINDS | echo fields (trainTypeName excluded — frozen UNKNOWN) |
| 29 | travel DELETE | **NC-KEY-SHAPE** | only submitted field = tripId "G1237"; the Trip entity carries `String id` (server-set; constructors never assign it from the flat trip id) AND nested `tripId` object {type,number} (fork `Trip.java:26-29,38-58`) → no identical-name flat field can VALUE-match; the strip-'G' transformation is inexpressible |
| 30 | travel2 POST | BINDS | as 27 |
| 31 | travel2 PUT | BINDS | as 28 |
| 32 | travel2 DELETE | **NC-KEY-SHAPE** | as 29 |
| 33 | price POST | BINDS | list + per-{routeId}/{trainType} GET from submitted fields |
| 34 | price PUT | BINDS | as 33 |
| 35 | price DELETE | BINDS a | LIST absence (GET /prices); {pricesId}→id (routeId/trainType not submitted on delete) |
| 36 | food order POST | BINDS | per-{orderId} GET — single-object read → presence via entity-matches; FoodOrder carries `orderId` as a real field |
| 37 | food createOrderBatch | **NC-BATCH-SHAPE** | submission is a JSON ARRAY of orders; the closed set has no iteration/multi-entity construct; `${field:…}`/field-matching assume one flat submitted object (the evaluator even parses the submitted body as an object up front). A batch-of-ONE stimulus flattened into the submitted map is rejected (review C): it changes the tested behavior — the frozen clause is about the batch loop — and exceeds the disclosed submitted-map convention |
| 38 | food order PUT | BINDS | per-{orderId} echo (entity-matches) |
| 39 | food order DELETE | BINDS | LIST absence (GET /orders) by `orderId` (real field name — no alias) |
| 40 | consign POST | BINDS-P | order/account-list membership binds; `data.price` is DERIVED (computed by consign-price) → that observable inexpressible |
| 41 | consign PUT | BINDS-P | echo binds; recomputed price NC-DERIVED |
| 42 | consignprice POST | BINDS-P | /consignprice/config echo binds (entity-matches); derived description-string + pricing-behavior observables NC-DERIVED |
| 43 | user register POST | BINDS-P | per-{userName} GET echo binds; "login works" side effect = POST → inexpressible |
| 44 | user PUT | BINDS | per-{userName} echo (lookup-key UNKNOWN in frozen text → counted BINDS, low-confidence) |
| 45 | user DELETE | BINDS | LIST absence (GET /users) by `userId` (real field name) |
| 46 | auth create POST | BINDS-P | membership in GET /api/v1/users by submitted username (field-name correspondence not pinned by the frozen text → counted BINDS, low-confidence); "login works" side effect = POST → inexpressible |
| 47 | auth login POST | — | frozen: "none — token issuance only"; no state clause → excluded from the denominator |
| 48 | auth user DELETE | BINDS-P | LIST absence binds; "login stops working" = POST → inexpressible |
| 49 | assurance create [mutating GET] | BINDS | per-orderid GET — single-object read → presence via entity-matches on `orderId` (real field) |
| 50 | assurance PATCH | BINDS | per-orderid single-object read → entity-matches (runner submits a fresh orderId → discriminating) |
| 51 | assurance DELETE by assuranceId | BINDS a | LIST absence (GET /assurances); {assuranceId}→id |
| 52 | assurance DELETE by orderId | **NC-OBJECT-ABSENCE** | (review A+B flip) the only frozen observable is per-entity `GET /assurance/orderid/{orderId}` — single-object `data` ("one assurance per orderId") → absence VACUOUS |
| 53 | security POST | BINDS | list membership |
| 54 | security PUT | BINDS | list echo by submitted id |
| 55 | security DELETE | BINDS | LIST absence; {id} = entity `id` |
| 56–70 | adminbasic 15× (contacts/stations/trains/configs/prices × POST/PUT/DELETE) | BINDS (2 deletes a) | pass-through LIST membership/echo/absence on GET /adminbasic/* (all list-shaped); deletes: {contactsId}→id a (#58), stations/trains `{id}` no alias, configs `{name}` no alias, {pricesId}→id a (#70) |
| 71 | adminorder POST | BINDS-P | aggregate-list membership (fresh accountId); routed per-`{data.id}` GET part response-keyed → inexpressible |
| 72 | adminorder PUT | BINDS | aggregate echo by submitted id |
| 73 | adminorder DELETE | BINDS a | aggregate-LIST absence; {orderId}→id |
| 74 | adminroute POST | BINDS | list + per-entity (G2-calibrated) |
| 75 | adminroute DELETE | BINDS a | LIST absence (GET /adminroute); {routeId}→id |
| 76 | admintravel POST | **NC-NESTED-ITEM-SHAPE** | (review C flip) the ONLY frozen observable is membership in GET /admintravel — but the merged list's items are `AdminTrip{trip, trainType, route}` WRAPPERS (`AdminTrip.java:10-12`; `TravelServiceImpl.adminQueryAll:562-581` wraps every Trip), so items carry NO top-level trip fields and `containsSubmittedFields` (top-level keys only) is NEVER satisfiable — it fails healthy control legs, i.e. control-breaking, not merely non-discriminating. Doubly load-bearing: the response gate is disclosed-weak (duplicate reports success), so NOTHING catches a lost admintravel create |
| 77 | admintravel PUT | **NC-NESTED-ITEM-SHAPE** | (review C flip) only frozen observable = "GET /admintravel reflects the submitted trip fields" — same nested-wrapper list; per the own-clause rule the per-{tripId} travel-service GET (which would bind) belongs to #28's contract, not #77's |
| 78 | admintravel DELETE | **NC-KEY-SHAPE** | as 29 (nested TripId in the merged list) |
| 79 | adminuser POST | BINDS | list membership by submitted userName |
| 80 | adminuser PUT | BINDS | list echo |
| 81 | adminuser DELETE | BINDS | LIST absence ("userId absent from GET /adminuserservice/users", real field name). The frozen text's OTHER observable (downstream per-{userId} read) is object-shaped → vacuous — the executable YAML must implement the list read |

## The fraction (post-review recount, all three reviewers folded)

Denominator = 80 entries with a frozen state postcondition (81 − login #47).

| convention | state clause BINDS (incl. partial) | NC (state invisible) |
|---|---|---|
| **G (generous, primary)** | **69 / 80 = 86.25 %** | **11 / 80 = 13.75 %** |
| S (strict, no aliases) | 59 / 80 = 73.75 % | 21 / 80 = 26.25 % (11 structural + 10 alias-dependent deletes) |

NC-under-G census (the structural residue no generosity fixes), 11 total:
- **3× KEY-SHAPE** (travel/travel2/admintravel deletes, #29/#32/#78): non-flat server-side key
  serialization defeats identical-name value matching.
- **2× NESTED-ITEM-SHAPE** (admintravel POST/PUT, #76/#77): the merged list's items are
  `AdminTrip{trip,…}` wrappers with no top-level trip fields → membership never satisfiable
  (review C; control-breaking if bound).
- **3× OBJECT-ABSENCE** (contacts/orderOther/assurance-by-orderId deletes, #12/#23/#52): the only
  frozen absence observable is a single-object per-entity read, on which the closed set's absence
  primitive is vacuous (review A+B+C).
- **1× TRANSITION** (orderPay, #18): the new value is not in the submission.
- **1× RESPONSE-KEYED** (orderOther admin create, #20): read-back path needs a server-generated id.
- **1× BATCH-SHAPE** (#37).

The 10 alias-dependent deletes (#3, 5, 17, 26, 35, 51, 58, 70, 73, 75) count as BINDS under G,
NC-FIELD-MISMATCH under S. On the BINDS-P entries, the observables that never bind under either
convention: DERIVED values (consign price ×2, consignprice pricing), out-of-band effects
(register-login works / auth-delete login stops), and one response-keyed read (#13).

## Reading (two-sided, honest — reworded per review A+B)

1. **The comparator class is a STRONG baseline on plain entity CRUD** — 86.25 % of the frozen set's
   state clauses bind under the generous convention (membership/echo/collection-absence with
   same-name keys). This is the breadth-scale version of the agreement anchor: on ordinary
   create/update/delete surfaces, response-assertion + STATE_GET catches acked-but-lost writes, and
   any head-to-head claim must (and does) concede it. No strawman.
2. **The on-surface residue (11/80) is STRUCTURAL, not random** — every NC is a
   primitive-vocabulary gap where the closed set's flat-single-object assumptions break, not a
   flaky contract: value TRANSITIONS (the new value is not in the submission — including the one
   payment-shaped entry, orderPay, whose PAID flip is invisible), SERVER-KEYED / response-derived
   observables, NON-FLAT key serialization and NESTED-WRAPPER list items, BATCH shape, and
   **OBJECT-ABSENCE: endpoints whose only state observable is a single-entity read-back, where the
   closed set cannot express "it is gone."** The object/aggregate-read categories are exactly the
   cancel→refund read-back shape (no flat collection-membership surface; the observable lives in
   one object/aggregate), which is why they matter beyond their count.
3. **The payment/compensation axis is two SEPARATE, correctly-scoped facts** (not an extrapolation
   from the 11/80): (a) coverage fact — the frozen set's own `not_covered` list places the
   money-moving orchestration surface (`ts-inside-payment`, `ts-cancel`, `ts-preserve`,
   `ts-rebook`, `ts-payment`, `ts-seat`) OUTSIDE the surveyed CRUD surface; (b) depth-cell evidence
   — for the one such flow examined end-to-end (cancel→refund), ALL THREE frozen state clauses are
   NOT_CHECKABLE (`assertion-bindings-cancel-refund.yaml`: bodyless write, JWT-scoped aggregate
   read-back, refund observable only as a balance DELTA). This survey asserts nothing about the
   bindability of the other five not-covered services.
4. So the external-validity answer to "how often is the clean-win shape encountered?": on THIS
   SUT's frozen CRUD surface, 13.75 % of specified state clauses are structurally unbindable even
   generously (26.25 % strictly), the residue categories are the same primitive gaps the depth
   cells exercise (delta/transition/object-shaped observables), and the deep
   payment/compensation flows sit outside the surveyed CRUD surface entirely (see point 3 for the
   two separately-scoped facts). MIST is positioned as
   COMPLEMENTARY coverage of that residue (consistent with the accepted framing rule:
   complementary, not a superset — on the 86.25 % the comparator is fine; on loud failures it can
   be better).

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
  executable YAML must implement exactly these dispositions (including the collection-shape rule:
  absence → list reads only; per-entity presence → entity-matches) or disclose the delta.
- An `entity-absent` primitive (data-null / status-0 on a per-entity read) would repair the three
  OBJECT-ABSENCE rows — but that is a NEW primitive outside the pre-registered closed set, i.e. a
  disclosed evaluator amendment, not a binding choice. Recorded as the honest boundary of the
  comparator class this study models.
- DISCLOSED AMENDMENT (2026-07-08, consolidation-plan review B M-2): two pre-recount numeric
  remnants in the Reading section were corrected to the accepted recount ("9/80" → "11/80";
  "88.75 %" → "86.25 %"). The S-3 "all percentages restated" fix had missed these two prose
  occurrences; no disposition changed — the fraction table and every disposition row were already
  at 69/80 = 86.25 % G / 59/80 = 73.75 % S / 11 NC.
