# Evidence — 2nd SUT hidden downstream failure: Online Boutique (gRPC), non-circular

Date 2026-05-30. SUT: **Google Cloud "Online Boutique" (microservices-demo)** — 11
gRPC microservices + an HTTP `frontend` — on the same kind + Istio 1.30 + Jaeger
cluster as Bookinfo. This is the **second SUT** for the headline **HiddenDownstreamFailure**
contribution, and it generalises it across a **different RPC protocol (gRPC)** and a
**different failure surface** (a swallowed gRPC `UNAVAILABLE`, not an HTTP 503).

## The phenomenon (real, non-circular)
Online Boutique's `frontend` renders ads from `adservice`. Ad rendering is
**non-critical**: the frontend catches an `adservice` failure, logs it, and returns
the home page **without ads** — `HTTP 200`. We scaled `adservice` to 0 (a real
availability outage; no mutant). The client sees `200`; the only evidence of the
failure is the swallowed `frontend → adservice` gRPC span deeper in the trace.

| frontend `/` | `adservice` | client HTTP | response-level oracle | trace oracle `HIDDEN_DOWNSTREAM_FAILURE` |
|---|---|---|---|---|
| homepage | **outage (0 replicas)** | **200** | **PASS (misses)** | **FIRES** (severity WARN) |
| homepage | healthy | 200 | PASS | silent |

## Result — real oracle on captured traces (`OracleCheck` over `mist.jar`)
`HiddenDownstreamFailureInvariant` (MIST's shipped invariant) run on the captured
Jaeger traces, alongside what a response-level oracle sees:

- **Outage window:** `OracleCheck` reports **`HIDDEN_DOWNSTREAM_FAILURE: FIRES`** on
  **7** captured frontend traces; verbatim:
  ```
  client-facing ROOT : frontend.boutique  frontend...:80/*  http=200 otel=null
  downstream ERROR   : frontend.boutique  adservice...:9555/*  http=200 otel=ERROR
  --> RESPONSE-LEVEL oracle: PASS (root is 2xx — looks successful, MISSES the failure)
  --> TRACE oracle HIDDEN_DOWNSTREAM_FAILURE: FIRES  severity=WARN
      caller received 2xx but 1 downstream span(s) server-errored (swallowed):
      frontend.boutique/adservice...:9555/* http=200 otel=ERROR
  ```
- **Healthy control:** **0** traces fire (the invariant is silent when `adservice` is
  up — no false positives). Sensitivity in the outage window, specificity in the
  control.

Captured traces (committed): `boutique_e2e_traces/boutique_adservice_outage.json`
(outage), `boutique_e2e_traces/boutique_frontend_healthy.json` (control).

## Why severity is WARN here (and ERROR on Bookinfo)
The swallowed `adservice` call fails with gRPC status **14 (UNAVAILABLE)**, which the
Istio sidecar records as `otel.status_code=ERROR` with `http.status_code=200` at the
transport level (no HTTP 5xx). MIST's confidence split (`HiddenDownstreamFailureInvariant`)
flags a span with `http>=500` as **ERROR** (a real swallowed 5xx, as on Bookinfo's
`ratings` 503) and an `otel=ERROR`-only span as **WARN** (surfaced, non-blocking). So
the same invariant catches both, and correctly reports the gRPC case at the lower
confidence tier. This is the designed behaviour, not a miss.

## Honest caveats
- **Outage-driven, not input-driven.** As on Bookinfo, the failure is a real
  availability outage (`adservice` scaled to 0), not elicited by a malicious input.
- **Oracle on captured live traces.** The verdict is MIST's real shipped invariant
  (`mist.jar` via `OracleCheck`) run on traces captured from the live SUT under the
  outage; the other shape invariants were not enabled (they need per-SUT learned
  baselines). The structural HiddenDownstreamFailure invariant needs no learned state.
- **Traffic source.** The home page is driven both by our `curl` and by Online
  Boutique's built-in `loadgenerator`; both produce frontend→adservice traces, and the
  invariant fires on them identically.
- **Clean-body case.** Unlike Bookinfo's `/reviews` (whose degraded body leaks an error
  breadcrumb) and Sock Shop's `/catalogue` (whose body carries `status_code:500`), the
  Online Boutique home page body is a **normal 200 HTML page** — there is no body
  breadcrumb at all, so a body-reading oracle (LogiAgent, MIST's own soft-error check)
  cannot catch it. Only the trace exposes the swallowed gRPC failure. This is the
  strongest demonstration that the signal is structural and body-independent.

## Reproduction
```bash
kubectl scale deploy adservice -n boutique --replicas=0          # induce the outage
curl -s http://localhost:8081/ -o /dev/null -w '%{http_code}\n'  # 200 (degraded, no ads)
# capture frontend traces from Jaeger, then:
java -cp mist-cli/target/mist.jar evaluation/suts/bookinfo/OracleCheck.java \
     docs/main-contribution/evidence/boutique_e2e_traces/boutique_adservice_outage.json "GET /"
kubectl scale deploy adservice -n boutique --replicas=1          # restore
```

## Re-capture & double-check (2026-06-01)
The "X of 12" count is **workload-mix dependent**, not a stable headline: the deep,
`adservice`-bearing frontend traces come from Online Boutique's built-in
`loadgenerator` (realistic user journeys), and the captured set is "the N most recent
frontend traces", so the exact ratio reflects how many of those journeys traversed
`adservice`. On the committed `boutique_adservice_outage.json`: **8 of 12** traces route
through `adservice`; **7** fire (all 7 are `frontend`-rooted: root `HTTP 200` + a
swallowed `adservice` `otel=ERROR`). The 8th `adservice`-routing trace is rooted at
`productcatalogservice` (an internal entry, not the client-facing `frontend`), so from
the edge it is correctly **not** flagged; the other 4 traces never reach `adservice`.
So "every **frontend** trace that routed through the failed `adservice` fires" = 7/7,
and **0** of the 12 healthy controls fire. (The paper previously quoted "8 of 12",
conflating the *route-through* count with the *fire* count; corrected to 7.)

A fresh re-capture (`workload/capture-traces-controlled.sh`: `loadgenerator` kept
running, `adservice` scaled to 0, Jaeger warmed) independently reproduces this —
**24 of 40** outage traces flagged, all root-`200` with a swallowed `adservice` error,
and **0 of 30** healthy. Committed: `boutique_adservice_outage_recapture.json`,
`boutique_frontend_healthy_recapture.json`.

> Capture gotcha (learned here): a plain `curl /` yields a shallow 1–2 span trace with
> no downstream fan-out, and a freshly-restarted Jaeger needs ~60s of warm traffic
> before traces are deep. Drive via the `loadgenerator` and warm up first, or the oracle
> sees no swallowed span and (correctly) does not fire — a false 0.
