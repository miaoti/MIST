# OpenTelemetry Demo — durable-behavior notes (per endpoint)

**Sole source of truth:** this note + the committed `oteldemo-openapi.yaml` for this pinned version.

## POST `/api/cart` (add item) and POST `/api/checkout` (place order)
- `/api/cart`: records the item in the user's cart (readable via GET `/api/cart`).
- `/api/checkout`: on success (HTTP 200) it returns an `orderId` + shipping tracking + cost and
  **empties the cart** synchronously.
- **Asynchronous downstream:** the checkout's downstream
  effects (accounting record, shipping dispatch) are propagated over an **asynchronous message path**. The pinned documentation and spec do
  not state a completion bound for that async propagation.
- Synchronously observable durable effect: the cart is emptied and an `orderId` is returned.
