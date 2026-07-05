# Blind contract notes — Sock Shop `shipping`, `POST /shipping` (G3, SUT-2)

Authoring reasoning for `blind-shipping-contract.yaml`. Written BLIND from primary
sources and frozen before use. The goal was the STRONGEST *fair* success oracle for
`POST /shipping` expressible in the frozen closed primitive set — asserting everything
soundly checkable, and honestly recording (with primitive proposals) everything that is
not, rather than padding the contract with vacuous or false-firing checks.

## Verdict up front

- **Bound (live, sound, discriminating):** exactly **one** check — `HTTP_STATUS 201`.
- **NOT_CHECKABLE (3):** (a) the response-body id/name **echo**; (b) the true **enqueue
  effect** (shipment reached RabbitMQ); (c) **service/broker liveness** via `GET /health`.
- **Is service/broker liveness expressible in the current primitive set? NO.** Two
  independent obstructions (no literal-value matching; `extractItems` does not unwrap the
  `{"health":[...]}` shape) — and reachability alone is a useless broker oracle because
  `/health` stays HTTP 200 even when the broker is down. Minimal primitive proposed below.

This is not a weak result from lack of effort — it is a faithful measurement of a thin
echo/stub service against a closed primitive set built for `{status,msg,data}` envelopes.
The rigor lives in the NOT_CHECKABLE analysis and the primitive proposals.

## Sources consulted

Primary (authoritative):
- `github.com/microservices-demo/shipping` `@master`
  `src/main/java/works/weave/socks/shipping/controllers/ShippingController.java` — fetched
  verbatim. Gives the `POST /shipping` handler, `GET /health`, and the `GET /shipping` +
  `GET /shipping/{id}` stubs.
- `.../entities/Shipment.java` — fields `String id`, `String name`; no `@JsonProperty`
  (serialize as `id`, `name`); constructors `Shipment()`, `Shipment(name)`, `Shipment(id,name)`.
- `.../entities/HealthCheck.java` — fields `service`, `status`, `date` (all default JSON names).

Live confirmation (deployed image, the exact SUT):
- Image `weaveworksdemos/shipping:0.4.8`, pod `shipping-*` in ns `sock-shop`, kind cluster
  `mist`, on 2026-07-04. Port-forwarded and exercised:
  - `POST /shipping -d '{"id":"itest-abc-123","name":"itest-name-xyz"}'`
    -> `HTTP/1.1 201`, `Content-Type: application/json`, body
    `{"id":"itest-abc-123","name":"itest-name-xyz"}` (exact echo).
  - `GET /health` -> `HTTP/1.1 200`, body
    `{"health":[{"service":"shipping-rabbitmq","status":"OK","date":"..."},{"service":"shipping","status":"OK","date":"..."}]}`.
  - `GET /shipping/itest-abc-123` -> `HTTP/1.1 200`, `Content-Type: text/plain`, body
    `GET Shipping Resource with id: itest-abc-123` (a stub string, NOT the shipment).

Repo OpenAPI (checked, not a source for this endpoint):
- `evaluation/suts/sockshop/openapi/sockshop-swagger.yaml` — covers ONLY the front-end
  browser surface (catalogue/cart/order/user). It has **no `/shipping` path** (shipping is
  an internal service the front-end does not proxy), so it contributes nothing here. Recorded
  for completeness.

Format / evaluator sources (to bind correctly):
- `mist-cli/src/main/java/io/mist/cli/comparator/AssertionBindings.java` — the strict loader
  and closed primitive set `{HTTP_STATUS, ENVELOPE_STATUS, ENVELOPE_DATA, MSG_CONTAINS,
  STATE_GET, NOT_CHECKABLE}`; STATE_GET expects one of `contains-submitted-fields`,
  `entity-matches-submitted-fields`, `absent`.
- `mist-cli/src/main/java/io/mist/cli/comparator/ContractEvaluator.java` — how each primitive
  is evaluated (envelope primitives parse the WRITE body for `status`/`msg`/`data`; STATE_GET
  does a follow-up `client.get(path)` and matches SUBMITTED-body field values).
- `mist-cli/src/main/java/io/mist/cli/fault/DataIntegrityRuntime.java` `extractItems(...)` —
  the collection parser STATE_GET membership relies on: unwraps a bare array `[..]`, a
  `{data:[..]}` envelope, or HAL `{_embedded:{rel:[..]}}` — and **nothing else**.
- Worked examples mirrored for exact key structure:
  `debug/a-main/g2-comparator/assertion-bindings-trainticket-calibration.yaml`,
  `debug/a-main/g3-comparator-tt/assertion-bindings-cancel-refund.yaml`.

## What `POST /shipping`'s real success is

`postShipping(@RequestBody Shipment)` is `@ResponseStatus(HttpStatus.CREATED)` and returns
`@ResponseBody Shipment` = **the very object it received**. So a success is:

1. HTTP **201 Created**, and
2. a response body that **faithfully echoes** the submitted `id` and `name` (bare JSON
   object, no `{status,msg,data}` envelope), and
3. the intended side effect: `rabbitTemplate.convertAndSend("shipping-task", shipment)`
   places the shipment on the broker queue (consumed downstream by `queue-master`).

Crucially, the handler wraps the send in `try { ... } catch (Exception e) { print("Unable to
add to queue ... Accepting anyway. Don't do this for real!"); }` and **returns 201 + the echo
regardless**. The HTTP response therefore proves (1) and (2) but says **nothing** about (3):
a broker outage yields the identical 201 + echo. This is a textbook **acked-but-lost** shape
baked into the service.

## Clause-by-clause

### Clause 1 — success status = 201 → BOUND `HTTP_STATUS 201`
The one unambiguous, sound, discriminating assertion. Source: `@ResponseStatus(CREATED)`;
live `HTTP/1.1 201`. Expect is exactly `201` (not `200,201`): a correct shipping success is
specifically *Created*, and a 200 would itself be a deviation worth flagging.

### Clause 2 — response echoes submitted id + name → NOT_CHECKABLE
A rigorous engineer absolutely wants this (it is the whole content of a create-echo response),
but **no closed primitive can assert it soundly**:
- The only primitives that read the WRITE response body are the envelope trio, and they read
  fixed envelope keys, not arbitrary echoed fields:
  - `ENVELOPE_STATUS` → `status` field absent → `null` → **FAIL on a correct success** (unsound).
  - `MSG_CONTAINS` → `msg` field absent → `null` → **FAIL on a correct success** (unsound).
  - `ENVELOPE_DATA expect:null` → `data` absent → treated as null → **PASS**, but **vacuous**:
    `envelopeDataIsNull` also returns true for any error/empty/garbage body, so it can never
    fail on this endpoint and discriminates nothing. Binding it would be padding, not strength.
- `STATE_GET` only reads a *follow-up GET*, and there is **no GET that returns the shipment**:
  `GET /shipping/{id}` is a hardcoded `text/plain` stub (`"GET Shipping Resource with id: <id>"`),
  not JSON and not the resource; `extractItems` on that non-JSON string yields empty, so a
  membership STATE_GET would **FALSELY FAIL**. Unsound.

Because every available binding is either unsound (false-fail) or vacuous (never-fail), the
honest verdict is NOT_CHECKABLE. **Proposed primitive** below (`RESPONSE_BODY_CONTAINS`) closes
this exactly.

### Clause 3 — the enqueue actually happened → NOT_CHECKABLE
The true success postcondition (shipment reached the `shipping-task` queue) has **no black-box
HTTP observable on this service at all**: shipping persists nothing queryable (both GETs are
stubs), and the enqueue outcome lives in RabbitMQ / `queue-master`, off this service's HTTP
surface. No closed primitive — and no single-response HTTP oracle over this service alone — can
see it. This is the deepest gap and the one most interesting for the comparison: it is precisely
the acked-but-lost class a **cross-service trace / differential** oracle (MIST) can catch and a
fixed single-endpoint contract cannot. Not expressible; recorded NOT_CHECKABLE (no primitive
proposal here because it is out of reach of *any* single-endpoint HTTP contract, not merely of
this closed set — it needs broker/queue state or a downstream read).

### Clause 4 — service + broker liveness via `GET /health` → NOT_CHECKABLE
The meaningful assertion is: `/health` shows the `shipping-rabbitmq` entry with
`status == "OK"` (and the `shipping` app entry `"OK"`). **STATE_GET cannot express it**, for
two independent reasons:

1. **No literal-value matching.** Both presence expects key ONLY on the *submitted body's*
   field values `{id,name}` (see `containsSubmittedFields` / `entityMatchesSubmittedFields`),
   which never appear in `/health`. There is no way to state an expected literal like
   `service=="shipping-rabbitmq" AND status=="OK"`. `absent` only tests absence of the
   submitted key. So the health field values (`service`, `status`) are simply unaddressable.
2. **Collection shape.** `extractItems` unwraps a bare array, `{data:[..]}`, or `{_embedded}`
   only. The health list is nested under the custom key `health` (`{"health":[...]}`), so
   `extractItems` returns **empty** and any membership check sees zero items.

And even the fallback "assert `/health` is reachable (2xx)" is a **useless broker oracle here**:
`/health` returns **HTTP 200 even when the broker is down** — the failure surfaces only as the
in-body `status:"err"`, never as the HTTP code (`getHealth` never throws). On top of that, a
non-2xx *decisive* STATE_GET read is reclassified `transportFailure → comparator-infra-failure`,
never a detection. So there is no sound, non-vacuous way to bind liveness. NOT_CHECKABLE, with
the proposal below.

I explicitly considered and **rejected** binding a weak `STATE_GET absent` on `/health` keyed on
the submitted id: it would trivially PASS (id never appears in `/health`), assert nothing about
health, misuse the loss-oracle `absent` expect, and — because `/health` is 200 even when the
broker is down — could never flag a real broker failure. That is padding, and the task forbids
forcing a check that cannot really express the intent.

## Primitive proposals (proposals only — not implemented)

### P1 — `RESPONSE_BODY_CONTAINS` (would bind Clause 2, the echo)
A new closed primitive that inspects the **WRITE response body** (not a follow-up GET), mirroring
`contains-submitted-fields` but over the create response:
- args: `fields:` — comma-separated submitted field names.
- semantics: parse `write.body` as a JSON object (or array of objects); PASS iff, for every
  listed field, the response carries that field with a value **equal to the submitted body's
  value**; FAIL otherwise (and on unparseable body).
- shipping binding: `primitive: RESPONSE_BODY_CONTAINS`, `fields: id,name` — asserts the 201
  body echoes both submitted values verbatim. This is the natural oracle for the very common
  "create echoes the submitted resource as a bare object" REST contract that the envelope-only
  write-body primitives cannot currently express.

### P2 — literal-match STATE_GET over a named collection entry (would bind Clause 4, liveness)
Two small extensions are needed together:
- (a) a **configurable collection key** so `extractItems`/STATE_GET can unwrap a custom wrapper
  such as `{"health":[...]}` — e.g. a `collection_key: health` arg (or generic "first
  array-valued property" handling); and
- (b) a new STATE_GET **expect that matches literals on a named entry**, independent of the
  submitted body — e.g. `expect: entry-field-equals` with two new literal args
  `select: "service=shipping-rabbitmq"` and `assert: "status=OK"`, semantics: PASS iff the
  collection at `path` contains an item whose `service == "shipping-rabbitmq"` and
  `status == "OK"`.
- shipping binding would then be two clauses on `GET /health`:
  `select service=shipping-rabbitmq assert status=OK` (broker up) and
  `select service=shipping assert status=OK` (app up) — a genuine service+broker liveness gate.
- Note this catches the broker-down case that HTTP status cannot (health stays 200), but it
  still does NOT bridge Clause 3's acked-but-lost gap for an *individual* POST (health is a
  service-wide probe, not per-request enqueue proof) — Clause 3 remains outside any
  single-endpoint HTTP contract.

## Fairness statement

Everything defensibly checkable is asserted (the 201). Nothing unsound (`ENVELOPE_STATUS`,
`MSG_CONTAINS`, membership on a stub GET) or vacuous (`ENVELOPE_DATA:null`) was bound, because a
false-firing or never-firing check would corrupt a head-to-head comparison. The gaps are real
properties of the service + closed primitive set, recorded precisely and, where a bounded
extension would close them, accompanied by a minimal primitive proposal.
