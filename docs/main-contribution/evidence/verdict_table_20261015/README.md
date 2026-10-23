# The invariant's verdicts after the path-substitution fix (2026-10-15)

> **Superseded, with corrections (2026-10-15, round 11b).** `../verdict_table_20261015b/` replaces
> the four rows of this folder with legs that run one test per distinct request. A records audit of
> this folder found:
>
> - The legs counted repeated requests. Read from their generated sources (now `generated-tests.zip`
>   in each leg folder), the 313, 325, 571 and 574 tests are 70, 73, 58 and 58 distinct request
>   sequences. The 79 flagged Bookinfo tests sent one request, and the 318 flagged Online Boutique
>   tests sent seven.
> - In the two Online Boutique legs, 617 positive tests (307 without and 310 under the outage) were
>   marked negative in advance ("[expect not 200]"), because an unsent synthetic placeholder stood in
>   their parameters. Each sent a captured valid id, got 200 and was reported as a failed test. The
>   invariant's verdicts do not depend on that label.
> - Each leg executed its tests twice, once per round of the test enhancer; the Allure results here
>   are from the second round.
> - Probes: in the Bookinfo no-outage leg the two probes went out as one malformed path (404), and the
>   invalid-product-id probe (500) ran only in the Online Boutique no-outage leg.
> - The response classifier did not "judge nothing": with the model off it gave Bookinfo's
>   `GET /api/v1/products` a cached judgment (valid) from an earlier model run. It flagged no response.

## Purpose

The round-11 records audit of the paper found that in the Bookinfo and Online Boutique legs of
`../verdict_table_20261014/` the negative tests never sent their invalid path values. MIST takes a
step's request path from the captured span, which on these two systems is concrete (an Envoy span's
`/api/v1/products/0/reviews`), and it filled path parameters only by replacing `{param}`
placeholders, so the Sniper value never reached the request. Commit c4f43d38 fixes this:
`StageSupport.replaceTemplateSegment` puts the targeted parameter's invalid value into the path
segment that the conf template assigns to it; the other parameters keep their captured values, and
templated routes (TrainTicket) are unchanged. Six unit tests cover the helper, and the 386 tests of
`mist-core` pass.

This folder holds the four Bookinfo and Online Boutique legs re-run with the fixed jar. The Sock Shop
and TrainTicket legs of `../verdict_table_20261014/` are unaffected (their invalid values travel in
request bodies, query strings and templated paths, and reached the requests) and remain the rows of
record for those two systems.

## Method

- Jar: built from c4f43d38 with `mvn -Dmaven.test.skip=true install` (2026-10-15 00:24).
- Profiles: `evaluation/suts/bookinfo/bookinfo-hdf-r11.properties` and
  `evaluation/suts/boutique/boutique-hdf-r11.properties`, the probe profiles of 2026-10-14 (model
  off, the hidden-failure invariant on) plus `faulty.ratio=0.2`, `test.variants.per.scenario=100`
  and `testsperoperation=100` (Bookinfo) or `200` (Online Boutique). The negative tests are
  generated as before, one per targeted parameter and category (79 on Bookinfo, 252 on Online
  Boutique); the settings add positive flows, the tests on which the invariant can see a failed call
  behind a 2xx entry response.
- Trace wait before each lookup: ten seconds in the Bookinfo outage leg, five in the Online Boutique
  outage leg, the default in the no-outage legs, as on 2026-10-14.
- Outages: `ratings-v1` scaled to 0 (Bookinfo), `adservice` scaled to 0 (Online Boutique). Probes
  before each leg (`driver.log`): Bookinfo `/api/v1/products/0/reviews` 200 and `/ratings` 503 under
  the outage; Online Boutique home and product pages 200 under the outage, an invalid product id 500.
- Each leg was run by a detached PowerShell driver (`run_leg.ps1`): port-forwards (Jaeger 16686;
  Bookinfo ingress 8080 or the Online Boutique frontend 8081), probes, MIST from the system's
  `.runtime/`, results copied, the driver's steps in `driver.log`.
- Tables: `verdict_table.py` and `verdict_scan.py` of `../verdict_table_20261014/` with the same
  `expect_*.json` and `rule_*.json`; `analyze_leg.py` counts each row as that folder's README defines
  it (Hidden: the entry call answered 2xx and either the leg's rule says it calls the failed service
  or the test's own trace holds a failed span; Missed: hidden and the invariant passed it; Confirmed:
  `verdict_table.py` CONFIRMED).

## Rows

| System | State | Wait | Tests | Hidden | Fired | Confirmed | Missed | Off-path | Folder |
|---|---|---|---|---|---|---|---|---|---|
| Bookinfo | no outage | default | 313 | 0 | 0 | 0 | 0 | 0 | `bookinfo-nooutage/` (run 1792042326478) |
| Bookinfo | ratings down | 10 s | 325 | 79 | 79 | 79 | 0 | 0 | `bookinfo-outage-wait10s/` (run 1792042525731) |
| Online Boutique | no outage | default | 571 | 0 | 0 | 0 | 0 | 0 | `boutique-nooutage/` (run 1792046385522) |
| Online Boutique | adservice down | 5 s | 574 | 318 | 318 (WARN) | 318 | 0 | 0 | `boutique-outage-wait5s/` (run 1792047131740) |

With the unchanged Sock Shop (511 tests, 354 flags) and TrainTicket (445 tests, 0 flags) rows of `../verdict_table_20261014/`, Table I totals 2,739 tests and 751 flags, every flag CONFIRMED, Missed 0 in every row, no off-path flag.

Scan categories (`scan.csv`, as defined in `../verdict_table_20261014/README.md`); MISS-IN-TRACE is 0 in every leg:

- `bookinfo-nooutage`: NO-2XX 58, QUIET 255; 234 positive and 79 negative tests.
- `bookinfo-outage-wait10s`: NO-2XX 152, QUIET 94, TP 79; 246 positive and 79 negative tests.
- `boutique-nooutage`: NO-2XX 255, QUIET 316; 319 positive and 252 negative tests.
- `boutique-outage-wait5s`: NO-2XX 255, QUIET 1, TP 318; 322 positive and 252 negative tests.

## Negative tests send their invalid values

Every negative test of the four legs sent its invalid value; none requested a captured valid id (`negatives.py` over `verdicts.csv`):

- `bookinfo-nooutage`: 79 negative tests, 66 distinct request paths, 0 on a captured valid id; statuses 200 x21, 308 x2, 400 x33, 404 x23
- `bookinfo-outage-wait10s`: 79 negative tests, 66 distinct request paths, 0 on a captured valid id; statuses 200 x15, 308 x2, 400 x17, 404 x23, 503 x22
- `boutique-nooutage`: 252 negative tests, 47 distinct request paths, 0 on a captured valid id; statuses 404 x30, 500 x222
- `boutique-outage-wait5s`: 252 negative tests, 47 distinct request paths, 0 on a captured valid id; statuses 404 x30, 500 x222

## Per-leg files

Each leg folder holds `allure-results.zip` (the raw Allure results the tables were computed from),
`driver.log`, `mist-<leg>.log` (MIST's console output), the fault-detection summary, `verdicts.csv`
(`verdict_table.py`) and `scan.csv` (`verdict_scan.py`). With the model off, the response classifier
logs a null model response on every 2xx it is asked about and judges nothing, as on 2026-10-14.
