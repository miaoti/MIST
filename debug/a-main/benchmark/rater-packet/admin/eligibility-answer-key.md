# [INTERNAL] Eligibility screen — answer key (administrator only; never shown to a candidate)

Pass rule (§9): BOTH practice cases labeled correctly AND BOTH spec-reading questions correct.
Grade the ballots against this key; record pass/fail per candidate with the screen outcome.

## Practice case SCREEN-1 — correct label: **genuine**
An order is created: `POST /api/v1/orderservice/order` returns HTTP 200 `{status:1,msg:"Success",
data:{id:…}}` (clean success acknowledgement, an order id echoed). The norm (bundle:
`ts-order-service/.../controller/OrderController.java` → `.../service/OrderServiceImpl.java` — the
create path persists the Order via `orderRepository.save(order)`) requires a successful order
creation to durably record the order. The observed durable state shows **no matching order record**
for the account submitted. An acknowledged (2xx-success) create whose durable order row is absent =
genuine defect. Acceptable groundings: OrderServiceImpl.create's `orderRepository.save`, the
OrderController create endpoint, or the Order entity as the system of record. `ack_carries_failure_
sentinel` = **no** (the success body carries no failure marker). A `benign` or `underspecified`
label = fail (the norm is derivable from the bundle; the transcript presents the durable state).

## Practice case SCREEN-2 — correct label: **benign**
An order create `POST /api/v1/orderservice/order` returns HTTP 400 `{status:0,msg:"Order creation
rejected: invalid request (trainNumber is empty)","data":null}` — a **non-2xx, error-marked
acknowledgement** (the request was refused, not accepted). The observed durable state shows no
matching order record — which is exactly consistent with a rejected create. Because the operation
was never acknowledged as a success (HTTP 400 / status:0, reason stated), the absence is by-design,
not a lost write: a lost write requires a **success** acknowledgement (2xx) whose durable effect is
then missing. Correct label: benign/by-design. `ack_carries_failure_sentinel` = **n/a** (the ack is a 400, not a
2xx success — the field applies only to a success-shaped ack). A `genuine` label = fail (misreads a
rejected request as a lost write); `underspecified` = fail (the rejection reason is stated and the
norm is derivable).

## Spec-reading Q1 — correct answer: **ts-user-service**
`UserServiceImpl.saveUser` (ts-user-service) persists the User entity via `userRepository.save(user)`
and then *notifies* ts-auth-service to create login credentials (a separate credential record).
System of record for the User entity = ts-user-service. Cite: `UserServiceImpl.saveUser`.
(Answering "ts-auth-service" = fail; the auth copy is a credential projection, not the entity of
record.)

## Spec-reading Q2 — correct answer: **No**
`TokenServiceImpl.getToken` (ts-auth-service) verifies the optional verification code (remote GET),
authenticates the username/password, READS the user (`userRepository.findByUsername`), and issues a
JWT. No repository save/update/delete occurs — nothing durable is created or modified. Any "yes"
answer that cites token issuance = fail (a JWT is returned to the caller, not persisted).
