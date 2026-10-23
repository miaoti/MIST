# Sock Shop with a logged-in session, with and without a catalogue outage (2026-10-16)

## Why these runs exist

Blind readers of the SANER tool-demo draft (rounds 11 to 15) kept naming two points about Table I: 354
of its 365 flags come from one Sock Shop cause (write requests sent without a login session), and the
"on path" column checks a flag against the trace the test attached rather than against a ground truth
of its own. These runs ask two questions on Sock Shop: what does MIST flag when every request carries a
logged-in session, and does it flag a failure whose ground truth comes from the experiment, a stopped
service, whose effect a reader can check without the traces.

## Setup

- Sock Shop as in `../sockshop_healthy_run_20261014/` (weaveworksdemos images, front-end 0.3.12, user
  0.4.7) in the `sock-shop` namespace of the kind cluster `mist` (node `mist-control-plane`),
  Istio-injected; every deployment scaled from zero to one replica at 00:16 CDT and back to zero at 02:57
  (`ss_up.sh`, `ss_down_all.sh`); the front-end port-forwarded to `localhost:8080`, Jaeger to
  `localhost:16686`.
- MIST: `mist-cli/target/mist.jar` built at f4e52caf (no tool code has changed since), run from
  `evaluation/suts/sockshop/.runtime/` through `../verdict_table_20261015/run_leg.ps1`, the last two legs
  by the unattended `chain_waited.ps1`.
- Profile `evaluation/suts/sockshop/sockshop-hdf-login.properties`: the profile of Table I's Sock Shop
  row (`sockshop-hdf-probe.properties`) plus a logged-in session (`make_profile.py`). MstAuthHandler's
  `per_jvm_cookie` mode registers one user through the front-end (`POST /register`) before the first
  request and attaches the session cookies it sets (`logged_in`, `md.sid`) to every request. The model is off (`llm.enabled=false`), the
  hidden-failure invariant on, Root API Mode on.
- Each leg generated its tests anew with this profile: 511 tests in every leg, with the same split by
  operation (219 `POST /addresses`, 133 `POST /cards`, 114 `GET /catalogue`, 43 `GET /catalogue/size`,
  one each for `GET /catalogue/{id}` and `GET /tags`) and kind (18 positive, 493 negative), and different
  invented and invalid values (`compare_requests.py`: 470 or 471 of the 511 request sequences differ
  between any two legs). In every leg each test sends a distinct request
  (`../verdict_table_20261015b/source_distinct.py`: 511 distinct of 511).
- Probes before the runs (`probe.ps1`): with the session, `POST /addresses` answered 200 with the stored
  address's id; without it, 200 with `{"error":"Invalid Id Hex","status_code":500,...}`. With catalogue
  scaled to zero (`ss_catdown.sh`; its database stays up), `GET /catalogue`, `/tags` and
  `/catalogue/size` answered 200 with the body `no healthy upstream`: the front-end's sidecar answers 503
  and the front-end returns 200 with that body.

| Leg (folder) | Window (CDT) | Catalogue | Trace wait |
|---|---|---|---|
| `login-nooutage-wait0` | 00:17 to 00:49 | up | 0 s (as Table I's Sock Shop row) |
| `login-catalogue-down-wait0` | 00:50 to 01:22 | scaled to zero | 0 s |
| `login-nooutage-wait5s` | 01:25 to 02:11 | up | 5 s (as Table I's Online Boutique rows) |
| `login-catalogue-down-wait5s` | 02:11 to 02:57 | scaled to zero | 5 s |

## Results

Table I definitions, computed by `../verdict_table_20261015/analyze_leg.py` with
`../verdict_table_20261014/expect_sockshop.json` (the services each entry operation calls); the two
catalogue-down legs also with `rule_sockshop_catalogue_down.json` (every catalogue read calls the stopped
service, so a catalogue read that answered 2xx counts as hidden whatever its trace holds). The full
outputs are in `ANALYSIS.txt`.

| Leg | Tests | Hidden | Fired | On path | Missed |
|---|---|---|---|---|---|
| logged in, no outage, 5 s | 511 | 72 | 72 | 72 | 0 |
| logged in, catalogue down, 5 s | 511 | 228 | 228 | 228 | 0 |
| logged in, no outage, 0 s | 511 | 65 | 65 | 65 | 0 |
| logged in, catalogue down, 0 s | 511 | 233 | 233 | 233 | 0 |

What the flags of the two five-second legs are (`e_breakdown.py`):

- In both no-outage legs no positive test was flagged (18 of 18 quiet); with the session the eight
  positive writes were stored (200 with the new id). In both catalogue-down legs the ten positive catalogue
  reads were flagged, as hidden failures under the outage, and the eight positive writes were not.
- Logged in, no outage, 72 flags: 56 type-mismatch negatives of `POST /addresses` (35) and `POST /cards`
  (21), whose wrongly typed JSON value the user service cannot unmarshal and answers with 500, while the
  front-end answers 200 with `{"error":"json: cannot unmarshal ..."}`; 13 negatives of `POST /cards`
  (empty input 6, semantic mismatch 5, null input 1, special characters 1) whose call to the user service
  was reset (sidecar 503; body `upstream connect error or disconnect/reset before headers`); 3 overflow
  negatives of `GET /catalogue` with the same reset on the call to catalogue.
- Logged in, catalogue down, 228 flags: all 159 tests of the four catalogue reads, positive and negative
  alike, got 200 with `no healthy upstream`, and each flag names the front-end's call to catalogue with 503;
  the other 69 are the same kinds of input-caused failures of `POST /addresses` (35) and `POST /cards` (34)
  as in the no-outage leg.
- Ground truth from the response bodies, independent of the traces (`e_pairing.py`; a body counts as a
  failure when it carries `Invalid Id Hex`, an Envoy reset, `no healthy upstream`, a JSON unmarshal error
  or `status_code 500`): catalogue down, 228 failed bodies, 228 flagged, no flag on a success body; no
  outage, 74 failed bodies, 72 flagged, no flag on a success body. The two unflagged are overflow tests of
  `GET /catalogue` whose body is an Envoy reset while the trace they attached, which began within 0.06 s
  of their request, shows a successful call to catalogue (`e_misses.py`); under the Table I definitions
  they are not hidden.

## Trace pairing and the five-second wait

Sock Shop's front-end does not pass on the trace context it receives, so no lookup matched the test's own
trace id (0 of 511 in every leg) and every trace came from the time-window fallback. With no wait (the
two 0 s legs, set as Table I's Sock Shop row), 40 and 33 tests attached a trace that began about a second
before their request (median -1.17 s and -1.13 s), a previous request's: 15 and 19 flags sit on a
success body, and 25 and 14 failed bodies went unflagged. With a five-second wait, every attached trace
began within 0.24 s of its request, and the verdicts agree with the bodies as above. Read from these
numbers: with no wait, a request's own spans are often not yet in Jaeger when MIST looks them up. Table I's
Sock Shop row was run with no wait; its 352 write requests all failed, so a neighbour's trace carried the
same failure, and the three GET tests it paired with other requests' traces are recorded in
`../sockshop_healthy_run_20261014/README.md`.

## Files

| File | Purpose |
|---|---|
| `<leg>/allure-results.zip` | The leg's raw Allure results (every step's request, response, query debug info and raw trace). |
| `<leg>/generated-tests.zip` | The JUnit sources MIST generated for the leg. |
| `<leg>/verdicts.csv`, `<leg>/scan.csv`, `<leg>/row.json` | Per-test verdicts and trace scan, and the Table I row, from `analyze_leg.py`. |
| `<leg>/mist-<leg>.log`, `<leg>/driver.log`, `<leg>/fault-detection-summary-*.txt` | MIST's console log, the run driver's log, and MIST's fault-detection summary. |
| `ANALYSIS.txt` | Outputs of `analyze_leg.py`, `e_pairing.py`, `e_breakdown.py` and `e_misses.py` on the four legs. |
| `e_breakdown.py`, `e_pairing.py`, `e_misses.py` | Per-test breakdown by body and cause; body ground truth against flags and the trace pairing gap; the failed bodies the invariant passed, with their traces. |
| `compare_requests.py` | Compares the request multisets of two legs' generated sources. |
| `rule_sockshop_catalogue_down.json` | The catalogue-down legs' rule: catalogue reads call the stopped service. |
| `make_profile.py`, `probe.ps1`, `chain_waited.ps1`, `stage16.py`, `ss_up.sh`, `ss_catdown.sh`, `ss_catup.sh`, `ss_down_all.sh` | The profile builder, the pre-run probe, the unattended driver of the last two legs, the staging script and the cluster scripts, as run (the run directory written as `<run dir>`). |

To re-derive a leg: unzip `allure-results.zip` into the leg folder, then run `analyze_leg.py <leg>
../verdict_table_20261014/expect_sockshop.json [rule_sockshop_catalogue_down.json]` and the three
`e_*.py` scripts on the leg folder (`e_*.py` read `scan.csv` from it).

## Notes after records audit C10 (2026-10-16)

- Concurrent tests and trace pairing. MIST ran each run's test classes on four threads (Allure `thread`
  labels; in the logged-in no-outage leg 219, 133, 114 and 45 tests per thread, and 360 of 510 consecutive
  test intervals overlap). With no marker match, the time-window fallback can take a concurrent request's
  trace: in that leg 24 `GET /catalogue` tests carry the trace of a concurrent `GET /catalogue/size` or
  `GET /tags` request, and in the catalogue-down leg one test does; all 25 traces began within 0.24 s of the
  test's request. The two unflagged reset tests above are among them. The five-second wait closed the
  late-span gap of the zero-wait legs, not this one, so "every attached trace began within 0.24 s of its
  request" above describes timing, not that every trace is the test's own. All 72 flags of the no-outage leg
  and 158 of the 159 catalogue flags of the catalogue-down leg sit on the test's own request.
- Online Boutique's cart page. `../verdict_table_20261014/expect_boutique.json` lists `ad` among the services
  of `^/cart`, while the deployment's traces show no adservice call from the cart page (the no-outage leg's
  `GET /cart`, 22 spans) and `rule_boutique_outage.json` lists only `/` and `/product/`. The paper's sentence
  on the three outage runs uses the rule (Bookinfo 1 of 1, Online Boutique 10 of 10, Sock Shop 159 of 159).
- Tool defect, not fixed: the HIDDEN DOWNSTREAM FAILURE attachment links Jaeger to the test's marker trace
  id. On Sock Shop no lookup matched it, so the finding's own link names a trace Jaeger did not return (354,
  72 and 228 findings of the three Sock Shop runs), while the trace the invariant judged is linked in the
  step's "API Call Trace" and "Raw Trace Data" attachments.
