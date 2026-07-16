# Case CASE-Q47

**System:** oteldemo  (version set: opentelemetry-demo app 2.2.0 / chart 0.40.9)

**Reference material:** Use ONLY the provided documentation bundle: `docs-bundles/oteldemo/`

## What was done (in order)

- `t+0ms`  **GET /api/products/0PUK6V6EV0**
  - response: HTTP 200 — body: `<product json elided>`
- `t+5ms`  **POST /api/cart?currencyCode=USD**
  - request body: `{"item":{"productId":"0PUK6V6EV0","quantity":1},"userId":"1624af2a-31a3-4409-9689-25849379569d"}`
  - response: HTTP 200 — body: `{"userId":"1624af2a-31a3-4409-9689-25849379569d","items":[{"productId":"0PUK6V6EV0","quantity":1}]}`
- `t+16ms`  **POST /api/checkout?currencyCode=USD**
  - request body: `{"userId":"1624af2a-31a3-4409-9689-25849379569d","userCurrency":"USD","email":"corpus-w121-3b5f90840d2c@example.test","address":{"streetAddress":"corpus-w121-3b5f90840d2c","state":"CA","country":"United States","city":"Mountain View","zipCode":"94043"},"creditCard":{"creditCardCvv":672,"creditCardExpirationMonth":1,"creditCardExpirationYear":2030,"creditCardNumber":"4432-8015-6152-0454"}}`
- `t+16ms`  observed state via `SQL SELECT street_address FROM accounting.shipping WHERE street_address='corpus-w121-3b5f90840d2c'`: HTTP 200 — `[]`
  - response: HTTP 200 — body: `{"orderId":"4714783e-7e9a-11f1-a92b-72c3ce69f378","shippingTrackingId":"c3cf14f4-1fab-4bf6-8497-e54bf69dbb55","shippingCost":{"currencyCode":"USD","units":8,"nanos":990000000},"shippingAddress":{"streetAddress":"corpus-w121-3b5f90840d2c","city":"Mountain View","state":"CA","country":"United States","zipCode":"94043"},"items":[{"cost":{"currencyCode":"USD","units":175,"nanos":0},"item":{"productId":"0PUK6V6EV0","quantity":1,"product":{"id":"0PUK6V6EV0","name":"Solar System Color Imager","description":"You have your new telescope and have observed Saturn and Jupiter. Now you're ready to take the…`
- `t+27698ms`  observed state via `SQL SELECT street_address FROM accounting.shipping WHERE street_address='corpus-w121-3b5f90840d2c'`: HTTP 200 — `[]`
- `t+328116ms`  observed state via `SQL SELECT street_address FROM accounting.shipping WHERE street_address='corpus-w121-3b5f90840d2c'`: HTTP 200 — `[{"street_address":"corpus-w121-3b5f90840d2c"}]`

## Your task

Using ONLY the reference material above, judge whether the observed outcome is a
**genuine defect**, **by-design / benign**, or **underspecified** (the intended
behavior is not derivable from the reference material). Record your answer in
`ballot.yaml`.
