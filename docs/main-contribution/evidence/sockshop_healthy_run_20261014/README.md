# Sock Shop, healthy, no outage: a hidden downstream failure from generated requests (2026-10-14)

## Why this run exists

Every hidden downstream failure the SANER tool-demo paper reported until round 7 came from an
outage the authors induced (Bookinfo `ratings`, Online Boutique `adservice`). This run asked
whether MIST's own generated requests elicit one on a healthy system. They do, on Sock Shop.

## Setup

- Sock Shop (weaveworksdemos images, front-end 0.3.12, user 0.4.7) in the `sock-shop` namespace
  of the kind cluster `mist-control-plane`, Istio-injected (databases outside the mesh), every
  deployment scaled from zero to one replica for the run and back to zero afterwards; all pods
  ready and `GET /catalogue` answering JSON before the run started. No outage, no fault
  injection, no restarts during the run (checked afterwards).
- Entry: the `front-end` service port-forwarded to `http://localhost:8080` (the bundle's
  `base.url`); Jaeger (`istio-system/tracing`) port-forwarded to `http://localhost:16686`.
- MIST `v1.6.0-SNAPSHOT` (`mist-cli/target/mist.jar`), run from
  `evaluation/suts/sockshop/.runtime/` with `evaluation/suts/sockshop/sockshop-hdf-probe.properties`,
  which is `sockshop-demo.properties` plus three lines at the end: `experiment.name`,
  `llm.enabled=false` (no model: the smart invalid-input mode falls back to its built-in values)
  and `mst.oracle.shape.invariants.hidden_downstream_failure.enabled=true` (the demo profile
  already carried it). Generation from the bundle's six captured traces and `root-api-registry.json`
  (Root API Mode); `auth.mode` defaults to none, so every request is sent without a session.
- Window: 2026-10-13 23:42:12 to 2026-10-14 00:15:10 CDT (`mist-sockshop-probe.log`,
  `fault-detection-summary-...txt`). The log's SUT preflight reports every endpoint as
  "unhealthy (unauthenticated)" and the run proceeded regardless.

## Result

511 generated tests executed. `tests_hdf.csv` lists every test with its entry, kind (positive
or negative), whether the HiddenDownstreamFailure invariant fired, and the violation.

| Entry (scenario) | Tests | Invariant fired | Failed span behind the 200 |
|---|---|---|---|
| `POST /addresses` (1) | 219 | 219 | `front-end -> user` HTTP 500, `otel=ERROR` (all 219) |
| `POST /cards` (220) | 133 | 133 | HTTP 500 (119), HTTP 503 (14) |
| `GET /catalogue` (353) | 114 | 2 | HTTP 503 (2) |
| `GET /catalogue/{id}` (467) | 1 | 0 | |
| `GET /catalogue/size` (468) | 43 | 0 | |
| `GET /tags` (511) | 1 | 0 | |

The finding the paper reports is the first two rows: all 352 requests to the two write
operations, positive and negative alike, received `HTTP 200` from the front-end with the body
`{"error":"Invalid Id Hex","status_code":500,"status_text":"Internal Server Error"}`
(`allure-examples/*Response_200.txt`), while the `user` service answered the front-end's call
with 500 (`allure-examples/*Raw_Trace_Data.txt`, `traces/addresses_positive_S1_v217_trace.json`:
the front-end's client span and the user service's server span, both `http=500 otel=ERROR`).
The cause is the missing customer id of a session-less request, which the user service tries
to parse as a Mongo ObjectId ("Invalid Id Hex") and answers with 500 instead of 400 or 401; the
front-end then answers the client with 200 and the error in the body. The Sniper values play no
part: the positive tests fail the same way. The 503s (14 on `/cards`, 2 on `/catalogue`) are
sidecar-level upstream errors under the run's load, also swallowed into a 200, and not counted
in the paper.

Manual reproduction (`manual/`): one `curl` each to `POST /addresses` and `POST /cards` with a
plausible body and no session returns `HTTP/1.1 200 OK` with the same error body.

## Caveats, recorded here and not in the paper

1. The front-end does not propagate the trace context it receives, so a test's trace has no
   entry span: the trace MIST found holds only the front-end-to-user hop. The invariant fired
   on the client status the test writer injects (200) plus the 500 spans. Offline,
   `OracleCheck` on the committed raw trace prints `pass`
   (`traces/oraclecheck_addresses_positive_S1_v217.txt`), because without the injected status
   it takes the 500 root for a loud failure; the paper therefore claims no replay for Sock Shop.
2. The trace was matched by the generated tests' time-window fallback (the id in the
   `traceparent` header the test sends is not carried into the mesh spans). Under the run's
   concurrency a test may have been paired with a neighbouring request's trace; every request to
   the two operations fails the same way, so the verdict does not depend on the pairing.
3. The `INTELLIGENT ANALYSIS` attachments were produced with the model disabled and are the
   tool's fallback text.
4. A session-less write is an unauthenticated request. The correct answer is 401 or 400; the
   system's 200 with a swallowed 500 is the defect, and it is reproducible with one request.
5. Schema oracles would flag this body too (the document types the 200 response as an Address
   or a Card). The paper claims the status oracle passes it and that the classifier of
   `ResponseEnvelope` misses a body of that shape (`../responseenvelope_live_3case.txt`, case 4).
