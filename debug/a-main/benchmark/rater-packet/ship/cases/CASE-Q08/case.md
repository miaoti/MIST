# Case CASE-Q08

**System:** teastore  (version set: TeaStore v1.4.2)

**Reference material:** Use ONLY the provided documentation bundle: `docs-bundles/teastore/`

## What was done (in order)

- `t+0ms`  **POST /tools.descartes.teastore.webui/loginAction?<credentials redacted>**
  - response: HTTP 200 — body: `<response redacted (carries a session token)>`
- `t+35ms`  **POST /tools.descartes.teastore.webui/cartAction?addToCart=&productid=42**
  - response: HTTP 200 — body: `<success-shaped page rendered>`
- `t+105ms`  **POST /tools.descartes.teastore.webui/cartAction?firstname=order-9f3c1a&lastname=Sample&address1=Street1&address2=City1&cardtype=visa&cardnumber=314159265359&expirydate=12%2F2029&confirm=Confirm**
  - response: HTTP 200 — body: `<success-shaped page rendered>`
- `t+120145ms`  observed state via `GET /tools.descartes.teastore.persistence/rest/orderitems/order/{orderId} (line items) — read in a later verification pass`: HTTP 200 — `line items: 0 item(s)`
- `t+120661ms`  observed state via `GET /tools.descartes.teastore.persistence/rest/orders/user/{id} (parent order) — read in a later verification pass`: HTTP 200 — `parent order 'order-9f3c1a': PRESENT`

## Your task

Using ONLY the reference material above, judge whether the observed outcome is a
**genuine defect**, **by-design / benign**, or **underspecified** (the intended
behavior is not derivable from the reference material). Record your answer in
`ballot.yaml`.
