# Case CASE-Q04

**System:** oteldemo  (version set: opentelemetry-demo app 2.2.0 / chart 0.40.9)

**Reference material:** Use ONLY the provided documentation bundle: `docs-bundles/oteldemo/`

## What was done (in order)

- `t+0ms`  **POST /api/cart?currencyCode=USD**
  - request body: `{"item":{"productId":"0PUK6V6EV0","quantity":1},"userId":"a152381a-c201-4a27-ac67-663b83e0e6c1"}`
  - response: HTTP 200 — body: `{"userId":"a152381a-c201-4a27-ac67-663b83e0e6c1","items":[{"productId":"0PUK6V6EV0","quantity":1}]}`
- `t+17ms`  **POST /api/checkout?currencyCode=USD**
  - request body: `{"userId":"a152381a-c201-4a27-ac67-663b83e0e6c1","userCurrency":"USD","email":"ODR-8H2N6@example.test","address":{"streetAddress":"ODR-8H2N6","state":"CA","country":"United States","city":"Mountain View","zipCode":"94043"},"creditCard":{"creditCardCvv":672,"creditCardExpirationMonth":1,"creditCardExpirationYear":2030,"creditCardNumber":"4432-8015-6152-0454"}}`
  - response: HTTP 200 — body: `{"orderId":"64fe18eb-7c21-11f1-96f7-7a7df9c38b25","shippingTrackingId":"6e62b247-ea2a-4654-b620-db784fa6cd0d","shippingCost":{"currencyCode":"USD","units":8,"nanos":990000000},"shippingAddress":{"streetAddress":"ODR-8H2N6","city":"Mountain View","state":"CA","country":"United States","zipCode":"94043"},"items":[{"cost":{"currencyCode":"USD","units":175,"nanos":0},"item":{"productId":"0PUK6V6EV0","quantity":1,"product":{"id":"0PUK6V6EV0","name":"Solar System Color Imager","description":"You have your new telescope and have observed Saturn and Jupiter. Now you're ready to take the next step a…`
- `t+30039ms`  observed state via `durable-state check — query the durable order/accounting record for the key submitted above`: HTTP 200 — `matching durable record present — order ODR-8H2N6`

## Your task

Using ONLY the reference material above, judge whether the observed outcome is a
**genuine defect**, **by-design / benign**, or **underspecified** (the intended
behavior is not derivable from the reference material). Record your answer in
`ballot.yaml`.
