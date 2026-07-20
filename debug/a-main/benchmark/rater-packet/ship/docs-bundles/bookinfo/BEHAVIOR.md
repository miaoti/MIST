# Istio Bookinfo — durable-behavior notes (per corpus-touched endpoint)

**Sole source of truth:** this note + the committed `bookinfo-openapi.yaml` for this pinned version.

## GET `/productpage`
- `/productpage` is a read-only aggregation: it composes product details, reviews, and (optionally)
  a ratings score for display. It creates no durable record.
- The reviews service optionally enriches its output with a ratings score. In the pinned design the
  ratings score is an **optional enhancement**: the product page is served (HTTP 200) and remains
  functional **with or without** a ratings score — the page renders in both cases by design.
- No durable record is created by this request.
