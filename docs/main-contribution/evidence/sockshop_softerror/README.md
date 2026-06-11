# Evidence — Sock Shop soft error (G1) under a real catalogue-db outage

Date 2026-05-30. SUT: **WeaveWorks Sock Shop** on the same kind + Istio + Jaeger
cluster. This is a **2nd-SUT instance of the soft-error gap (G1)**: a client-facing
`HTTP 200` whose body carries a server-side failure, which a status-class oracle
records as a pass.

## What was captured (real responses, committed here)
Drive `GET /catalogue` through the Istio ingress, with `catalogue-db` scaled to 0
so the `catalogue` service cannot reach its database:

| state | client HTTP | body (captured) | status oracle | response-envelope / soft-error oracle |
|---|---|---|---|---|
| healthy (`catalogue-db` up) | 200 | `[{"id":"03fef6ac…","name":"Holy",…}]` (product array) | pass | pass (success-shaped) |
| **outage** (`catalogue-db` down) | **200** | `{"error":"Do: database connection error","status_code":500,"status_text":"Internal Server Error"}` | **pass (MISSES)** | **flags** (failure-valued body) |

Files: `sockshop_catalogue_healthy.json`, `sockshop_catalogue_outage.json`.

The front-end answers `HTTP 200` in **both** states; only the body distinguishes
success (a product array) from failure (an error object with `status_code:500`).
A status-class oracle passes both. This is the same soft-error class as the
TrainTicket motivating example (`{"status":0,"data":null}` at HTTP 200), which
MIST detects end-to-end (run `trainticket_twostage_test_42`); Sock Shop reproduces
it on a second SUT under a real availability outage.

## Honest scope
- **This is response-level (G1), not the trace oracle (G2).** The signal is in the
  body. A body-reading oracle (MIST's response-envelope / LLM soft-error check, and
  also RESTifAI / LogiAgent) can catch it. The *novel* contribution —
  trace-only **HiddenDownstreamFailure** with a clean body — is shown on Bookinfo
  and Online Boutique, not here.
- **Sock Shop cannot demonstrate hidden-downstream (G2).** Verified empirically: its
  front-end (Node) does not propagate trace context from its inbound request to its
  outbound call, so the client-facing `200` span and the downstream `catalogue 500`
  span land in **separate** Jaeger traces. `HiddenDownstreamFailure` correctly does
  not fire (no single trace has a 2xx entry + a 5xx descendant). See
  `../bookinfo_e2e_pipeline.md` for the SUT where the full chain is in one trace.

## Reproduction
```bash
kubectl scale deploy catalogue-db -n sock-shop --replicas=0    # break the DB
curl -s http://localhost:8080/catalogue                        # HTTP 200 + status_code:500 body
kubectl scale deploy catalogue-db -n sock-shop --replicas=1    # restore
kubectl rollout restart deploy catalogue -n sock-shop
```
