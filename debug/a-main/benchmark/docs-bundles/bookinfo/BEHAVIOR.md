# Istio Bookinfo — durable-behavior notes (per endpoint)

**Sole source of truth:** this note + the committed `bookinfo-openapi.yaml` for this pinned version.

## GET `/productpage`
- `/productpage` is a read-only aggregation: it composes product details, reviews, and (optionally)
  a ratings score for display. It creates no durable record.
- The reviews service optionally enriches its output with a ratings score. The reviews service optionally includes a ratings score in its output. The product page
  returns HTTP 200 and is displayed whether or not a ratings score is included.
- No durable record is created by this request.
