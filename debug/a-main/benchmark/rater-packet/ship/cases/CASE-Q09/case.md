# Case CASE-Q09

**System:** oteldemo  (version set: opentelemetry-demo app 2.2.0 / chart 0.40.9)

**Reference material:** Use ONLY the provided documentation bundle: `docs-bundles/oteldemo/`

## What was done (in order)

- `t+0ms`  **POST /api/cart?currencyCode=USD**
  - request body: `{"item":{"productId":"0PUK6V6EV0","quantity":1},"userId":"7d3e9f01-4b28-4c15-9a6e-2f10c8b47d93"}`
  - response: HTTP 200 — body: `<cart accepted>`
- `t+0ms`  **POST /api/checkout?currencyCode=USD**
  - request body: `{"userId":"7d3e9f01-4b28-4c15-9a6e-2f10c8b47d93","userCurrency":"USD","email":"ODR-2M8P3@example.test","address":{"streetAddress":"ODR-2M8P3","state":"CA","country":"United States","city":"Mountain View","zipCode":"94043"},"creditCard":{"creditCardCvv":672,"creditCardExpirationMonth":1,"creditCardExpirationYear":2030,"creditCardNumber":"4432-8015-6152-0454"}}`
  - response: HTTP 200 — body: `{"orderId":"17158d51-7f3c-11f1-90da-96ecc7e640eb","shippingAddress":{"streetAddress":"ODR-2M8P3","city":"Mountain View","state":"CA","country":"United States","zipCode":"94043"},"items":"<elided>"}`
- `t+30402ms`  observed state via `durable-state check — query the durable order/accounting record for the key submitted above`: HTTP 200 — `no matching durable record present`
- `t+58214ms`  observed state via `durable-state check — query the durable order/accounting record for the key submitted above`: HTTP 200 — `matching durable record present — order ODR-2M8P3`

## Your task

Using ONLY the reference material above, judge whether the observed outcome is a
**genuine defect**, **by-design / benign**, or **underspecified** (the intended
behavior is not derivable from the reference material). Record your answer in
`ballot.yaml`.
