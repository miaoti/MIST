# Case CASE-Q18

**System:** sockshop  (version set: microservices-demo (Sock Shop))

**Reference material:** Use ONLY the provided documentation bundle: `docs-bundles/sockshop/`

## What was done (in order)

- `t+0ms`  **POST /register**
  - request body: `<credentials redacted>`
  - response: HTTP 200 — body: `<response redacted (carries a session token)>`
- `t+28ms`  **POST /addresses**
  - request body: `{"street":"my road","number":"3","country":"UK","city":"London","postcode":"E2 8RS"}`
  - response: HTTP 200 — body: `{"id":"6a50689ea39e9b00016cdd9f"}`
- `t+78ms`  **POST /cards**
  - request body: `{"longNum":"5544154011345918","expires":"08/29","ccv":"958"}`
  - response: HTTP 200 — body: `{"id":"6a50689ea39e9b00016cdda0"}`
- `t+126ms`  **POST /cart**
  - request body: `{"id":"819e1fbf-8b7e-4f6d-811f-693534916a8b"}`
  - response: HTTP 201 — body: ``
- `t+151ms`  **POST /orders**
  - response: HTTP 201 — body: `{"id":"6a50689e04eb4f000a414b79","customerId":"6a50689ea39e9b00016cdd9e","customer":{"id":null,"firstName":"cb","lastName":"cb","username":"cb1783654558838","addresses":[],"cards":[]},"address":{"id":null,"number":"3","street":"my road","city":"London","postcode":"E2 8RS","country":"UK"},"card":{"id":null,"longNum":"5544154011345918","expires":"08/29","ccv":"958"},"items":[{"id":"6a50689ebf9479000ae1b98e","itemId":"819e1fbf-8b7e-4f6d-811f-693534916a8b","quantity":1,"unitPrice":14}],"shipment":{"id":"09dbb47d-009b-4070-8dda-abf2dd032a40","name":"6a50689ea39e9b00016cdd9e"},"date":"<time>…`
- `t+3322ms`  observed state via `GET /orders (the acting customer's orders)`: HTTP 201 — `[{"customerId":"6a50689ea39e9b00016cdd9e","customer":{"firstName":"cb","lastName":"cb","username":"cb1783654558838","addresses":[],"cards":[]},"address":{"number":"3","street":"my road","city":"London","postcode":"E2 8RS","country":"UK"},"card":{"longNum":"5544154011345918","expires":"08/29","ccv":"958"},"items":[{"itemId":"819e1fbf-8b7e-4f6d-811f-693534916a8b","quantity":1,"unitPrice":14}],"shipment":{"name":"6a50689ea39e9b00016cdd9e"},"date":"<time>+0000","total":18.99,"_links":{"self":{"href":"http://orders/orders/6a50689e04eb4f000a414b79"},"order":{"href":"http://orders/or…`

## Your task

Using ONLY the reference material above, judge whether the observed outcome is a
**genuine defect**, **by-design / benign**, or **underspecified** (the intended
behavior is not derivable from the reference material). Record your answer in
`ballot.yaml`.
