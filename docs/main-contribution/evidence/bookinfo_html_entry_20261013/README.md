# Istio Bookinfo, HTML entry `GET /productpage` under the `ratings` outage (2026-10-13)

## Why this run exists

The JSON entry `GET /api/v1/products/{id}/reviews` of Bookinfo hides the `ratings` 503 behind a
200, but its degraded body breaks the documented schema (rating objects lose `stars` and
`color`), so a schema oracle flags it (see `../bookinfo_baselines_20261013/`). The HTML entry
`GET /productpage` is the same system, the same outage and the same swallowed call
(`productpage -> reviews -> ratings`), with a response that no schema oracle can fault: the
page is `text/html` and comes back 200 with the reviews rendered and the stars replaced by
"Ratings service is currently unavailable". This folder records a MIST run on that entry and
the two baseline tools on the same entry, in the outage and healthy.

## Inputs (all committed)

- Document: `evaluation/suts/bookinfo/openapi/bookinfo-productpage-swagger.yaml`, Swagger 2.0,
  `basePath /`, one operation `GET /productpage` producing `text/html`, response 200 with
  `schema: type: string`. The first version had no `schema` on the 200 response; it is kept
  here as `bookinfo-productpage-swagger-v1-noschema.yaml` because EvoMaster reads a documented
  response without a schema as "no body expected" and reports the page body as a schema
  violation in the healthy state as well as in the outage (see below).
- MIST configuration: `evaluation/suts/bookinfo/bookinfo-productpage.properties`, derived from
  `bookinfo-demo.properties` by four keys (`oas.path` to the document above, `conf.path` to
  `real-system-conf-productpage.yaml`, `trace.file.path` to `traces-productpage`,
  `experiment.name` to `bookinfo_productpage_outage`), everything else identical, and the
  invariant switch `mst.oracle.shape.invariants.hidden_downstream_failure.enabled=true` passed as a
  JVM system property as in the re-run of `../bookinfo_mist_rerun_20261013/`.
- Captured entry trace for Root API Mode: `traces/healthy_productpage.json` (trace 9cad9c1c...)
  (9 spans, taken from Jaeger on the healthy system before the outage; the copy MIST read is
  `evaluation/suts/bookinfo/traces-productpage/`). The root-API registry
  `evaluation/suts/bookinfo/root-api-registry.json` already listed `GET /productpage`.
- Cluster, outage and port-forwards as in `../bookinfo_baselines_20261013/README.md`.

## MIST (two runs, same result)

| Run | Document | Window (CDT) | Generated and executed | Finding | Trace |
|-----|----------|--------------|------------------------|---------|-------|
| `mist-run/` | v1 (no schema) | 20:47:43 to 20:48:04 | 1 test (`Flow_Scenario_1.test_positive_flow_S1_v1`) | `HIDDEN_DOWNSTREAM_FAILURE` at ERROR, `reviews.default -> ratings.default.svc.cluster.local:9080/* (http=503 otel=ERROR)`, 2 hits | `e5bbdae6106044bdba5040d1b0c953b6` |
| `mist-run-v2/` | v2 (string schema) | 20:56 | the same | the same | `fa078ed9b3fe4de986a73c027754a9e4` |

Only one test is generated because the operation has no parameters: the Sniper variants are
exhausted by deduplication after the positive test (log: "Aborting scenario 1 after 10
consecutive dedup-exhausted variants"). The model calls of the run failed (401 from the
configured endpoint, no local Ollama), so the `ResponseEnvelope` classifier fell back and
logged an error; the invariant does not use the model. Each run's log, fault-detection summary
and generated test file are in its folder.

Both outage traces are saved under `traces/` and replay offline with the paper's harness:

```
java -cp mist-cli/target/mist.jar evaluation/suts/bookinfo/OracleCheck.java \
     docs/main-contribution/evidence/bookinfo_html_entry_20261013/traces/outage_productpage.json "GET /productpage"
```

prints `TRACE oracle HIDDEN_DOWNSTREAM_FAILURE: FIRES severity=ERROR`
(`oraclecheck_productpage_outage.txt`, `oraclecheck_productpage_v2_outage.txt`), and the healthy
trace prints `pass` (`oraclecheck_productpage_healthy.txt`). The harness's own line
`RESPONSE-LEVEL oracle ... PASS (root is 2xx, looks successful, MISSES the failure)` is a fixed
wording derived from the root status alone; it also prints on the healthy trace.

## The page itself

`productpage_healthy_1.html` (15,068 bytes, 9 `glyphicon-star` elements) and
`productpage_outage_1.html` (9,669 bytes, "Ratings service is currently unavailable" twice,
no stars), both HTTP 200. A body oracle that knows that phrase would catch the outage; a status
oracle and a schema oracle have nothing to flag.

## Baseline tools on the HTML entry

| Tool and document | Healthy | Outage |
|-------------------|---------|--------|
| Schemathesis 4.23.0, v2 (`schemathesis-healthy/`, `schemathesis-outage/`) | 3 test cases, 1 failure: Unsupported methods (the page answers methods the document does not declare); the GET 200 passes every check | the same |
| Schemathesis 4.23.0, v1 (`schemathesis-outage-v1-noschema/`) | not run | the same as v2 |
| EvoMaster 6.2.0 black-box, 3 min, seed 42, v2 (`evomaster-healthy/`, `evomaster-outage/`) | 3 tests written, 0 potential faults | 3 tests written, 0 potential faults (the GET 200 is in the successes file) |
| EvoMaster 6.2.0 black-box, 3 min, seed 42, v1 (`evomaster-healthy-v1-noschema/`, `evomaster-outage-v1-noschema/`) | 1 potential fault on `GET /productpage`: `validation.response.body.unexpected No response body is expected but one was found` | the same 1 fault |

The v1 fault is a property of the document form (no `schema` on a `text/html` response), not of
the outage: it is reported identically on the healthy page. With the response typed as a string
(v2) EvoMaster reports nothing on the healthy page.

## What the paper may take from this folder

Under the same outage as the JSON case, the HTML entry answers 200 with a body that breaks no
schema, a status oracle and a schema oracle pass it (both baseline tools, both document
versions once the v1 artifact is accounted for), and MIST's invariant fires at ERROR on the same
swallowed call from one generated test, with the verdict replayable offline from the committed
trace.
