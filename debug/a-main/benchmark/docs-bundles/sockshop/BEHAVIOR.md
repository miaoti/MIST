# Sock Shop — durable-behavior notes (per endpoint)

**Sole source of truth:** this note + the committed `sockshop-openapi.yaml` for this pinned version.

## POST `/orders` (place order)
- On success (HTTP 201/200) Sock Shop records **the order document** for the customer (its items,
  address, and total), readable via the customer's order history. The order's own fields are
  written as part of order creation.
- Shipping is handled by a separate shipping/queue path consumed asynchronously; the pinned
  docs/spec/source do not state a completion bound for a durable shipping/queue record.
- Synchronously observable durable effect: the order document.
