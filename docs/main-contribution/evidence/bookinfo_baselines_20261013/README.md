# Istio Bookinfo under the `ratings` outage: EvoMaster and Schemathesis runs (2026-10-13)

> Note (2026-10-22): the submitted version of the paper no longer cites these runs. Its sentence on
> EvoMaster and Schemathesis is about the HTML entry `GET /productpage`, whose record is
> `../bookinfo_html_entry_20261013/`. The results below stand as recorded.

## Why these runs exist

The SANER 2027 tool-demo paper (`paper/tool-demo/main_saner.tex`, Section IV, Bookinfo
paragraph, and Section V, oracle paragraph) says that EvoMaster 6.2.0 in black-box mode and
Schemathesis 4.23.0, run against the same outage, also flag the degraded
`GET /api/v1/products/{id}/reviews` response, through schema validation, because Bookinfo's
OpenAPI document requires `stars` and `color` on every rating and the degraded body carries an
error object instead. These two runs are the record behind that sentence. They are not a
comparison of generators and the paper draws no count from them.

## Shared setup

- Cluster: the kind node `mist-control-plane`, Istio 1.30.0, Bookinfo deployed from the
  release-1.30 sample manifests with `reviews` pinned to v3 (the response bodies name the pod
  `reviews-v3-77f747dc48-ppk99`).
- Outage: `kubectl scale deploy ratings-v1 -n default --replicas=0`, in place for the whole
  evening, the same outage as the MIST re-run in `../bookinfo_mist_rerun_20261013/`.
- Entry point: the Istio ingress gateway port-forwarded to `http://localhost:8080`
  (`kubectl port-forward -n istio-system svc/istio-ingressgateway 8080:80`).
- OpenAPI document: `evaluation/suts/bookinfo/openapi/bookinfo-swagger.yaml` (Bookinfo's own
  Swagger 2.0 document, 4 operations, `basePath` `/api/v1`).
- Both tools ran from the Windows host, one after the other, on 2026-10-13 between about
  19:55 and 20:08 (America/Chicago).

## Schemathesis 4.23.0 (`schemathesis/`)

- Invocation: the document above with `--url http://localhost:8080/api/v1`, JUnit and HAR
  reports requested. `schemathesis-run.log` is the terminal output with control codes,
  `schemathesis-run.clean.log` the same output cleaned, `junit-20261013T195550Z.xml` the JUnit
  report (the HAR file was not kept).
- Result: 4 of 4 operations tested, 1,095 test cases generated, 9 unique failures in 12.11 s,
  seed `287247234971293569780090676443146338988`.
  - Response violates schema (2 violations, one test case, `GET /products/{id}/reviews`, HTTP 200):
    `"0" is not of type "integer"` for `id`, and `"stars" is a required property` for the
    first rating, whose value is `{"error": "Ratings service is currently unavailable"}`. The
    second violation is the outage's signature (the error object replaces the rating). Whether
    the `id` type violation also occurs with a healthy `ratings` was not measured.
  - Server error and Undocumented HTTP status code (one test case, `GET /products/{id}/ratings`):
    the 503 of the outage, which the document does not list for that path.
  - Unsupported methods (4) and API accepted schema-violating request (1): unrelated to the
    outage (methods the document does not declare are answered, and a negative product id is
    accepted).
- `INVALID-first-attempt-wrong-base-url.log`: the first invocation used
  `http://localhost:8080` without the `/api/v1` prefix, so every call answered 404. Kept for
  completeness; it is not a result.

## EvoMaster 6.2.0, black-box (`evomaster/`)

- Options, as recorded by EvoMaster itself in `statistics.csv`: `blackBox=true`,
  `bbSwaggerUrl=<the document above>`, `bbTargetUrl=http://localhost:8080` (EvoMaster applies the
  document's `basePath`), `maxTime=10m`, `seed=42`, `outputFolder=em-out`, `writeStatistics=true`;
  `problemType` REST and `outputFormat` PYTHON_UNITTEST by default. `em.yaml`, created by
  EvoMaster, holds no settings and is not committed (the repository ignores that name). `evomaster-run.log` is the full console output.
- Result (`evomaster-run.log`, `em-out/report.json`): 97,083 evaluated tests and 119,290 HTTP
  calls in the 10-minute budget, 15 tests written, 3 potential faults, all of EvoMaster's
  category 200 ("schema violation", `potentialFaultsSummary` `200:3`), 3 of 4 endpoints reached
  with a 2xx (the ratings endpoint answered only 503):
  - `GET /products/{id}/ratings`: `Response status 503 not defined for path`
    (`EMBookinfoOutage_faults_Test.py`, `test_0` and `test_1`).
  - `GET /products/{id}/reviews`, HTTP 200: `[Path '/id'] Instance type (string) does not match
    any allowed primitive type (allowed: ["integer"])` and `[Path '/reviews/0/rating'] Object has
    missing required properties (["color","stars"])` (`test_2` and `test_3`; the asserted body
    carries `"rating": {"error": "Ratings service is currently unavailable"}`).
- `em-out/EMBookinfoOutage_successes_Test.py` holds the other 11 generated tests and
  `em-out/em_test_utils.py` EvoMaster's helper module.

## Healthy control (`healthy/`, run after the outage was lifted, 20:38 to 20:52 CDT)

Both tools were run again on the same document with `ratings` back at one replica.

- Schemathesis 4.23.0 (`healthy/schemathesis/`): 2,269 test cases, 6 unique failures in
  18.62 s, seed `235961657479623093999152664583250982795`: Response violates schema 2, on
  `GET /products/{id}/reviews` (`"0" is not of type "integer"` for `id`) and on
  `GET /products/{id}/ratings` (`5 is not of type "string"` at
  `/properties/ratings/additionalProperties`: the document types rating values as strings and
  the service returns integers); Unsupported methods 4. The JUnit file of a first attempt that
  crashed on console encoding before testing is kept as `INVALID-...xml`.
- EvoMaster 6.2.0 black-box, 3 minutes, seed 42 (`healthy/evomaster/`): 15 tests written, 2
  potential faults, both schema violations: `GET /products/{id}/reviews` (`/id` string where
  integer is allowed) and `GET /products/{id}/ratings` (`/ratings/Reviewer1` integer where
  string is allowed); 4 of 4 endpoints reached with a 2xx.

So on a healthy Bookinfo both tools already fail `/reviews` and `/ratings` against the document.
Under the outage the `/reviews` report gains the missing-`stars`/`color` violation and the
`/ratings` report turns into a server error with an undocumented status; the verdict on both
operations is red in both states.

## What the paper takes from these runs, and what it does not

- Taken: both tools flag the degraded `/reviews` body, by schema validation, and both flag the
  loud 503 on `/ratings`.
- Not claimed: anything about test generation, counts, or coverage. The paper states the
  condition of the catch (a document that requires the rating properties and a body that changes
  shape) and contrasts it with Online Boutique, where the home page body stays clean under the
  outage; that Boutique statement rests on the existing Boutique evidence in this folder's
  parent, not on a run of 2026-10-13.
