# TeaStore — durable-behavior notes (per corpus-touched endpoint)

**Sole source of truth:** this note + the committed `teastore-openapi.yaml` for this pinned version.

## POST `/tools.descartes.teastore.webui/cartAction` (confirm/place order)
- On a successful (HTTP 200) order confirmation, TeaStore durably records **the order** and its
  **order line-items**: an order row for the session's user, plus one order-items row per cart line.
  The order and its line-items are part of the same order-placement operation.
- These records are readable afterward via the user's order history / order detail.
- TeaStore is not distributed-traced (Kieker only); the durable store is the authoritative record
  of what was placed.

## Read endpoints (product / category / profile listing)
- Read-only; no durable record is created.
