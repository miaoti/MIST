# Case CASE-Q14

**System:** oteldemo  (version set: opentelemetry-demo app 2.2.0 / chart 0.40.9)

**Reference material:** Use ONLY the provided documentation bundle: `docs-bundles/oteldemo/`

## What was done (in order)

- `t+0ms`  **POST /api/cart?currencyCode=USD**
  - request body: `{"item":{"productId":"0PUK6V6EV0","quantity":1},"userId":"<session uuid, internal>"}`
  - response: HTTP 200 — body: `<cart accepted>`
- `t+0ms`  **POST /api/checkout?currencyCode=USD**
  - request body: `{"userId":"<session uuid>","userCurrency":"USD","email":"r1dev2-1784003126@Sample.test","address":{"streetAddress":"r1dev2-1784003126","state":"CA","country":"United States","city":"Mountain View","zipCode":"94043"},"creditCard":{"creditCardCvv":672,"creditCardExpirationMonth":1,"creditCardExpirationYear":2030,"creditCardNumber":"4432-8015-6152-0454"}}`
  - response: HTTP 200 — body: `{"orderId":"3cfd32b3-7f3c-11f1-90da-96ecc7e640eb","shippingAddress":{"streetAddress":"r1dev2-1784003126","city":"Mountain View","state":"CA","country":"United States","zipCode":"94043"},"items":"<elided>"}`
- `t+29337ms`  observed state via `durable-state check for the record submitted above`: HTTP 200 — `[]`
- `t+58842ms`  observed state via `durable-state check for the record submitted above`: HTTP 200 — `[{"the request marker field":"r1dev2-1784003126"}]`

## Your task

Using ONLY the reference material above, judge whether the observed outcome is a
**genuine defect**, **by-design / benign**, or **underspecified** (the intended
behavior is not derivable from the reference material). Record your answer in
`ballot.yaml`.
