# The invariant's verdicts over all executed tests, per system (2026-10-14)

## Purpose

The paper's reviewers asked how many of the invariant's flags are real, over every test MIST
executed, system by system. This folder holds one run per system state with the model off and
`HiddenDownstreamFailure` on, the per-test verdict tables of each run, the raw Allure results each
table was computed from, and the scripts.

## Method

- Profiles: each bundle's demo profile plus three lines (`*-hdf-probe.properties` in
  `evaluation/suts/<system>/`): the experiment name, `llm.enabled=false`, the invariant switch.
  TrainTicket runs on the G1 subset (`real-system-conf-g1.yaml`, 36 operations of the eight deployed
  services, ten tests per operation). With the model off, the response classifier logs a null model
  response on every 2xx it is asked about and judges nothing (the same lines appear in every leg).
- Trace wait: the generated tests look a trace up by the W3C `traceparent` marker and accept the
  first match. Under the Bookinfo outage with the default setting the match was often a partial
  trace without the `reviews -> ratings` span (section "Trace completeness" below), so the Online
  Boutique outage leg of record runs with `-Dmst.test.jaeger.propagation.delay.ms=5000`, a five-second
  wait before each lookup, and the Bookinfo outage leg of record with 10000 (ten seconds, see below);
  the healthy Bookinfo, Online Boutique and Sock Shop legs ran with the default and TrainTicket
  with 5000 (the Wait column of the rows table; a 5 s healthy Bookinfo leg is kept beside the default one) (the generated tests read `System.getProperty("mst.test.jaeger.propagation.delay.ms", "0")`;
  the "3-second delay" line in the Query Debug Info attachment is a fixed string, not the setting).
  The default-setting legs are kept beside them.
- `verdict_table.py` (raw Allure results) and `verdict_table_report.py` (a generated Allure report)
  write one row per test: entry, kind, the client status MIST recorded, whether the invariant
  fired, the failed spans it listed, and a verdict: CONFIRMED (client 2xx, every listed span a 5xx
  or otel ERROR, every listed span on the entry's own call path per `expect_<system>.json`),
  OFF-PATH (a listed span of a service the entry never calls: the trace lookup paired the test
  with a neighbouring request), NO-2XX, NOT-5XX, NONE.
- `verdict_scan.py` checks every test, quiet ones included, against the raw Jaeger trace the test
  itself attached: it lists the failed spans of that trace (http >= 500, `otel.status_code` ERROR,
  `error=true`, or a server-side gRPC code 2/4/8/12/13/14/15; the invariant's own predicate is
  http >= 500 or otel ERROR, `HiddenDownstreamFailureInvariant.java` line 176) and classifies the
  test: TP (fired, 2xx, failed span in the trace), MISS-IN-TRACE (quiet, 2xx, failed span in the
  trace), MISS-TRACE-SHORT (quiet, 2xx, the leg's rule says it should fire, no failed span in the
  fetched trace), QUIET, NO-2XX, FIRED-NO-FAIL, FIRED-NO-2XX. `rule_<leg>.json` gives the rule.
- "Hidden" (should fire) counts the tests whose entry answered 2xx while a call on its path failed
  with a 5xx or an ERROR span in the same run: Bookinfo, every `/reviews` test answered 200 under
  the outage; Online Boutique, every home and product page (the cart page does not call
  adservice); Sock Shop, every `POST /addresses` and `POST /cards`, plus the two reads whose own
  trace held a transient 503 (the scan found them, the rule did not).
- "Real" = CONFIRMED by `verdict_table.py` and TP by the scan, and for Sock Shop also reproduced by
  hand with a logged-in control (`../sockshop_healthy_run_20261014/`).

## Rows

| System | State | Wait | Tests | Hidden | Fired | Real | Artifacts | Missed | Folder |
|---|---|---|---|---|---|---|---|---|---|
| Bookinfo | healthy | 0 | 93 | 0 | 0 | 0 | 0 | 0 | `bookinfo-healthy/` |
| Bookinfo | healthy | 5 s | 93 | 0 | 0 | 0 | 0 | 0 | `bookinfo-healthy-wait5s/` |
| Bookinfo | ratings outage, 2026-10-13 run | 0 | 108 | 29 | 9 | 6 | 3 | 23 | `bookinfo-outage-20261013/` (table from the generated report) |
| Bookinfo | ratings outage | 0 | 102 | 27 | 5 | 5 | 0 | 22 | `bookinfo-outage/` |
| Bookinfo | ratings outage | 5 s | 102 | 27 | 24 | 24 | 0 | 3 | `bookinfo-outage-wait5s/` |
| Bookinfo | ratings outage, leg of record | 10 s | 102 | 27 | 27 | 27 | 0 | 0 | `bookinfo-outage-wait10s/` (run 1791999712849) |
| Online Boutique | healthy | 0 | 269 | 0 | 0 | 0 | 0 | 0 | `boutique-healthy/` |
| Online Boutique | adservice outage | 5 s | 269 | 265 | 265 | 265 | 0 | 0 | `boutique-outage-wait5s/` (WARN severity: gRPC status 14 behind HTTP 200) |
| Sock Shop | healthy, no outage | 0 | 511 | 354 | 354 | 354 (352 defect + 2 transient 503) | 0 | 0 | `sockshop-healthy-20261014/` (run in `../sockshop_healthy_run_20261014/`) |
| TrainTicket | healthy, G1 subset (8 services) | 5 s | 445 | 0 | 0 | 0 | 0 | 0 | `trainticket-g1-healthy/` (run 1791996359237; first attempt aborted, see its `aborted-run-20261014-0241/NOTE.md`) |

Scan categories per leg of record (`scan.csv`): Bookinfo healthy, both waits: QUIET 93. Bookinfo
outage, default wait: TP 5, MISS-TRACE-SHORT 22, NO-2XX 36 (the loud `/ratings` 503s), QUIET 39.
Bookinfo outage, 5 s: TP 24, MISS-TRACE-SHORT 3, NO-2XX 36, QUIET 39. Bookinfo outage, 10 s: TP 27,
NO-2XX 36, QUIET 39. Online Boutique healthy:
QUIET 266, NO-2XX 3. Online Boutique outage, 5 s: TP 265, QUIET 1 (the cart page), NO-2XX 3 (two
422s and setCurrency). Sock Shop: TP 354, QUIET 157. TrainTicket G1: QUIET 36 (every 2xx test;
their traces hold 4, 6 or 9 spans, the admin-route chain included, and no failed span), NO-2XX 409
(all 4xx; 205 of them carry `"injected":true`, 203 INSUFFICIENT_STATIONS_FAULT and 2
INVALID_STATION_NAME_LENGTH_FAULT: the planted defects answer loudly). MISS-IN-TRACE is 0 in every leg: no test whose
own trace held a failed span went unflagged. Off-path flags among fired tests: 0 in every leg.

## Trace completeness at lookup time

In the Bookinfo outage legs with the default setting, the quiet `/reviews` tests held traces of 1
to 4 spans without the `reviews -> ratings` span (`Query Debug Info`: "Marker match at attempt N
(k spans)"): Envoy's span for the inner hop had not reached Jaeger when the test accepted the
first marker match. With the five-second wait 24 of 27 fired, and the three misses again lacked
that span (the scan confirms: no failed span in those three attached traces). With a ten-second
wait (run 1791999712849, 12:41 to 12:55 CDT; the run took 13.5 minutes against 6.8 for the
five-second leg, consistent with 102 tests waiting five seconds longer each) all 27 fired, 27
CONFIRMED and TP, 0 off-path, 0 missed. That leg is the Bookinfo outage row of record. The three OFF-PATH
flags of the default run (`GET /api/v1/products/0`, listing ratings and gateway 503 spans that
request cannot produce) did not recur with the wait.

## Per-run files

Each leg folder holds the MIST console log, the fault-detection summary, `verdicts.csv`
(`verdict_table.py`), `scan.csv` (`verdict_scan.py`) and `allure-results.zip`, the raw Allure
results the two tables were computed from (the Sock Shop zip holds the results of the run recorded
in `../sockshop_healthy_run_20261014/`). The 2026-10-13 Bookinfo leg has only the table computed
from its generated report. The ten-second Bookinfo leg was started with `-Dexperiment.name=bookinfo_outage_wait10s`, but
its fault-detection summary is named after the profile's `experiment.name`
(`fault-detection-summary-bookinfo_healthy_wait5s_1791999712849-...`): the override reached
neither the summary name nor, apparently, anything else; the run id, the driver log (ratings probe
503, 27 flags) and the results identify the leg. The TrainTicket leg has no fault-detection summary
(MIST wrote none for that run) and carries `driver.log`, the log of the detached driver that ran it (port-forwards, login
probe, MIST exit 0, 3,951 result files copied); the TrainTicket pods ran with Istio sidecars so that
every hop has an Envoy span. No login token appears in the TrainTicket log or results (checked).

## Corrections (2026-10-15, records audit of the paper's round 11)

1. Online Boutique outage, 5 s: the three NO-2XX tests are `POST /cart`, `POST /cart/checkout` and
   `POST /setCurrency`, all three answered 422 (`verdicts.csv`); "two 422s and setCurrency" above
   should read "three 422s".
2. The negative tests of the Bookinfo and Online Boutique legs did not send their invalid path
   values: all 80 Bookinfo negatives requested `/api/v1/products/0/...` and all 252 Online
   Boutique negatives requested valid product pages, although each records its injected payload
   (a path-substitution defect for concrete captured paths). The verdicts above stand, because the
   invariant judged the requests that were sent; those negatives behaved as positive requests.
   TrainTicket and Sock Shop negatives did send their invalid values. Superseded for Bookinfo and
   Online Boutique: `../verdict_table_20261015/` holds the four legs re-run after the fix (commit
   c4f43d38), in which every negative test sent its invalid value.
3. The Sock Shop leg's lookups all used the time-window fallback; see the correction section of
   `../sockshop_healthy_run_20261014/README.md`.
