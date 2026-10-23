# The invariant's verdicts with one test per distinct request (2026-10-15, round 11b)

> Note (2026-10-22): Table I of the submitted paper has eight rows: the two Bookinfo rows of this
> folder, the two Online Boutique rows of `../verdict_table_20261015c/` (543 tests each, replacing the
> Online Boutique rows below), the Sock Shop row without a login and the TrainTicket row of
> `../verdict_table_20261014/`, and the two logged-in Sock Shop rows of `../sockshop_login_20261016/`.
> It totals 3,204 tests and 665 flags. The total below, 1,224 tests and 365 flags, is that of the
> earlier six-row table.

> **Online Boutique rows superseded (2026-10-15, round 11c).** `../verdict_table_20261015c/` re-runs
> the two Online Boutique legs after MIST learned to send HTML-form request bodies (commit 06f4737c)
> and to mark its invented `FALLBACK_test<n>` values (commit f4e52caf): 543 tests per leg, 10 flags
> under the outage. The Bookinfo rows here stay the rows of record.

## Purpose

The Bookinfo and Online Boutique legs of `../verdict_table_20261015/` counted repeated requests.
Read from their generated JUnit sources, the four legs ran 313, 325, 571 and 574 tests but only 70,
73, 58 and 58 distinct request sequences. In the Bookinfo outage leg the 79 flagged tests sent one
request, `GET /api/v1/products/0/reviews`; in the Online Boutique outage leg the 318 flagged tests
sent seven distinct requests (the home page and six product pages, two of them 158 and 155 times).

The cause was in the generator. On a concrete captured route (an Envoy span's
`/api/v1/products/0/reviews`) a positive test re-issues the captured call, but MIST recorded a
generated path value, never sent, as the test's parameter. Its variant deduplication compared those
unsent values, so it kept many copies of one request (up to 85 identical tests in the Bookinfo
outage leg and 158 in the Online Boutique one). Commit 9fa67d41 fixes
this. A positive test now records the value its request carries, and
`mst.generation.dedup.requests=true` keeps one test per distinct request sequence: the same method,
path, query parameters, headers, cookies and body on every step, and the same fault target. The same
fix stops an unsent synthetic placeholder from marking a positive test negative in advance, which in
round 11 had labelled 617 Online Boutique positive tests "[expect not 200]".

This folder holds the four Bookinfo and Online Boutique legs re-run with the fixed jar. They replace
the round-11 rows of those two systems. The Sock Shop and TrainTicket rows of
`../verdict_table_20261014/` stay; their tests were already distinct requests (section "Distinct
requests in every row").

## Method

- Jar: built from the code of commit 9fa67d41 with `mvn -Dmaven.test.skip=true install`
  (2026-10-15 03:09); `mist-core`'s 393 unit tests pass.
- Profiles: `evaluation/suts/bookinfo/bookinfo-hdf-r11b.properties` and
  `evaluation/suts/boutique/boutique-hdf-r11b.properties`: the round-11 profiles (model off, the
  hidden-failure invariant on) plus `mst.generation.dedup.requests=true`,
  `trace.merge.max.roots.per.scenario=1` (every captured entry call is its own scenario, so each
  captured page or operation is re-issued once), and `status.code.exploration.enabled=false` and
  `test.enhancer.enabled=false`. With those two off, every executed test is one generated test,
  executed once; in round 11 the test enhancer had executed each leg's tests twice. Request dedup
  kept 70 of the 73 tests the generator produced in each Bookinfo leg and 64 of 392 in each Online
  Boutique leg (the `Request dedup` line of MIST's runtime log).
- Traces, Bookinfo: the seven checked-in traces of `evaluation/suts/bookinfo/traces/`, all of
  product 0, the catalog's only product (`GET /api/v1/products` lists one product). The three
  `/productpage` traces have no operation in the OpenAPI document and yield no test.
- Traces, Online Boutique: `evaluation/suts/boutique/traces-r11b/`, the two checked-in files of
  `traces/` plus `boutique_products_r11b.json`. That file holds traces of the three catalog pages
  missing from the checked-in files (`/product/1YMWWN1N4O`, `/product/2ZYFJ3GM2N`,
  `/product/66VCHSJNUP`), requested by the shop's own load generator. The load generator ran until a
  Jaeger query returned 20 traces, the query's limit, for each of those pages. `fetch_product_traces.py`
  then took for each page the trace with the most spans that holds the call to `adservice` (26 spans
  each; the root is the load generator sidecar's outbound `GET`, status 200, and its child the
  frontend's inbound span), and the load generator was scaled back to 0 before the legs.
- Trace wait before each lookup: ten seconds on Bookinfo and five on Online Boutique, in both states.
  The tests ran in parallel; every Bookinfo test lasted at least 10.0 s and every Online Boutique
  test at least 5.0 s (Allure start and stop times).
- Outages: `ratings-v1` scaled to 0 (Bookinfo), `adservice` scaled to 0 (Online Boutique). Probes
  before each leg (`driver.log`): Bookinfo `/api/v1/products/0/reviews` 200 in both legs and
  `/api/v1/products/0/ratings` 200 without and 503 under the outage; Online Boutique `/` and
  `/product/OLJCESPC7Z` 200 in both legs.
- Sequences: `run_bookinfo_r11b.ps1` and `run_boutique_r11b.ps1` ran the legs, each through
  `../verdict_table_20261015/run_leg.ps1`, and restored the systems (`ratings-v1` to 1, the Online
  Boutique namespace to 0). They are the scripts as run, with the scratch output directory replaced
  by a `-Root` parameter; `bookinfo-seq.log` and `boutique-seq.log` are their logs, with that
  directory written as `<run dir>`. `stage_leg.py` copied each leg here.
- Tables: `analyze_leg.py` and `negatives.py` of `../verdict_table_20261015/`, with the
  `expect_*.json` and `rule_*.json` of `../verdict_table_20261014/` and the row definitions of its
  README (Hidden: the entry call answered 2xx and either the leg's rule says it calls the failed
  service or the test's own trace holds a failed span; Missed: hidden and the invariant passed it;
  Confirmed: `verdict_table.py` CONFIRMED).
- Distinct requests: `source_distinct.py` reads each run's generated JUnit sources
  (`generated-tests.zip`) and counts a test's request sequence as, per call, the method, the path with
  its path values, the query parameters, headers, cookies and body the source builds.
  `distinct_requests.py` cross-checks from the Allure results (method, endpoint, request-body
  attachment, injected payload).

## Rows

| System | State | Wait | Tests | Distinct requests | Hidden | Fired | Confirmed | Missed | Off-path | Folder |
|---|---|---|---|---|---|---|---|---|---|---|
| Bookinfo | no outage | 10 s | 70 | 70 | 0 | 0 | 0 | 0 | 0 | `bookinfo-nooutage-wait10s/` (run 1792053175714) |
| Bookinfo | ratings down | 10 s | 70 | 70 | 1 | 1 | 1 | 0 | 0 | `bookinfo-outage-wait10s/` (run 1792053441682) |
| Online Boutique | no outage | 5 s | 64 | 64 | 0 | 0 | 0 | 0 | 0 | `boutique-nooutage-wait5s/` (run 1792053926458) |
| Online Boutique | adservice down | 5 s | 64 | 64 | 10 | 10 (WARN) | 10 | 0 | 0 | `boutique-outage-wait5s/` (run 1792054206748) |

With the Sock Shop (511 tests, 354 flags) and TrainTicket (445 tests, 0 flags) rows of
`../verdict_table_20261014/`, Table I totals 1,224 tests and 365 flags. Every flag is CONFIRMED,
Missed is 0 in every row, no flag is off-path, and every test of every row sends a distinct request.

What was flagged:

- Bookinfo, `ratings` down: the positive test of `GET /api/v1/products/0/reviews`, which got 200
  while the call from `reviews` to `ratings` failed with 503; the invariant fired at ERROR.
- Online Boutique, `adservice` down: the positive tests of the home page and of all nine product
  pages, each 200 while the call from `frontend` to `adservice` carried gRPC status 14; the invariant
  fired at WARN. `GET /cart` (200) does not call `adservice` and stayed quiet. The positive tests of
  `POST /cart`, `POST /cart/checkout` and `POST /setCurrency` got 422 (MIST sent no form body), so they
  are not 2xx and not hidden.

Scan categories (`scan.csv`, as defined in `../verdict_table_20261014/README.md`); MISS-IN-TRACE is
0 in every leg:

- `bookinfo-nooutage-wait10s`: NO-2XX 55, QUIET 15; 4 positive and 66 negative tests.
- `bookinfo-outage-wait10s`: NO-2XX 62, QUIET 7, TP 1; 4 positive and 66 negative tests.
- `boutique-nooutage-wait5s`: NO-2XX 53, QUIET 11; 14 positive and 50 negative tests.
- `boutique-outage-wait5s`: NO-2XX 53, QUIET 1, TP 10; 14 positive and 50 negative tests.

## Negative tests send their invalid values

Every negative test sent its invalid value, and none requested a captured valid id (`negatives.py`
over `verdicts.csv`):

- `bookinfo-nooutage-wait10s`: 66 negative tests, 66 distinct request paths, 0 on a captured valid
  id; statuses 200 x11, 308 x2, 400 x31, 404 x22
- `bookinfo-outage-wait10s`: 66 negative tests, 66 distinct request paths, 0 on a captured valid id;
  statuses 200 x5, 308 x2, 400 x16, 404 x22, 503 x21
- `boutique-nooutage-wait5s`: 50 negative tests, 50 distinct request paths, 0 on a captured valid id;
  statuses 404 x5, 500 x45
- `boutique-outage-wait5s`: 50 negative tests, 50 distinct request paths, 0 on a captured valid id;
  statuses 404 x5, 500 x45

On `GET /api/v1/products/{id}/reviews`, each Bookinfo leg has 22 negative tests: 21 got 404 and the
one with an empty id got a 308 redirect. No positive test of the four legs is marked negative in
advance.

## Distinct requests in every row

`source_distinct.py` over the generated sources of each Table I run, restricted to the tests in its
`verdicts.csv`:

| Run | Tests | Distinct request sequences | Sources |
|---|---|---|---|
| Bookinfo, no outage | 70 | 70 | `bookinfo-nooutage-wait10s/generated-tests.zip` |
| Bookinfo, ratings down | 70 | 70 | `bookinfo-outage-wait10s/generated-tests.zip` |
| Online Boutique, no outage | 64 | 64 | `boutique-nooutage-wait5s/generated-tests.zip` |
| Online Boutique, adservice down | 64 | 64 | `boutique-outage-wait5s/generated-tests.zip` |
| Sock Shop, no outage (run 1791952932028) | 511 | 511 | `rows-of-record/sockshop-healthy-20261014/generated-tests.zip` |
| TrainTicket, 8 services (run 1791996359237) | 445 | 445 | `rows-of-record/trainticket-g1-healthy/generated-tests.zip` |

The Sock Shop and TrainTicket sources come from the gitignored `.runtime/` directories of those
runs; their results stay in `../verdict_table_20261014/`. Both rows of record ran with the test
enhancer on (`test.enhancer.enabled=true` in their profiles; the TrainTicket log shows its second
round over all 445 tests), so each of their tests was executed twice, and their results hold one
result per test. On the round-11 sources, now in `../verdict_table_20261015/<leg>/generated-tests.zip`,
the same script finds the repeats: the Bookinfo outage leg's 325 tests are 73 distinct request
sequences (largest repeat 85) and the Online Boutique outage leg's 574 tests are 58 (largest repeat
158). The Allure
cross-check (`distinct_requests.py ... payload`) agrees on the four legs here (70, 70, 64, 64 distinct)
and on TrainTicket (445); on Sock Shop it cannot see query strings, which its Allure endpoint
parameter omits, and the sources settle it.

## The response classifier with the model off

The soft-error classifier made no judgment of its own. On Bookinfo, `GET /api/v1/products` got a
judgment (valid) from the disk cache of an earlier model run (`.mist/llm-validation-cache.json` in
the system's `.runtime/`, written 2026-10-13). Every other 2xx positive response got an empty model
response, which MIST records as "VALID SUCCESS" with the explanation "LLM validation failed: ...
llmResponse is null". No response was flagged: no "SOFT ERROR DETECTED" in any leg, and every
RESPONSE_ENVELOPE check passed.

## Per-leg files

Each leg folder holds `allure-results.zip` (the raw Allure results the tables were computed from),
`generated-tests.zip` (the run's JUnit sources), `driver.log`, `mist-<leg>.log` (MIST's console
output), the fault-detection summary, `verdicts.csv` (`verdict_table.py`) and `scan.csv`
(`verdict_scan.py`). Eight Online Boutique tests per leg carry no trace verdict: five negative tests
that got 404 and the three `POST` positive tests that got 422, none of them 2xx.
