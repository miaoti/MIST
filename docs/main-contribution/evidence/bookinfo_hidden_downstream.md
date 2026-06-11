# Evidence — HIDDEN-DOWNSTREAM detected on a real third-party SUT (Istio Bookinfo), NON-circular

Date 2026-05-29. SUT: **Istio Bookinfo** deployed locally on kind + Istio 1.30 + Jaeger.
This is the path-1 demonstration: MIST's trace oracle catches a swallowed downstream failure
that a response-level oracle cannot see — on a system **we did not write or mutate**, via a
**real outage** plus Bookinfo's **own** graceful-degradation code.

## Why this is non-circular (vs the train-ticket attempt)
- The masking is **Bookinfo's own in-tree behavior**: `reviews` (v3) catches a failed `ratings`
  call and still returns HTTP 200; `productpage` renders a clean 200. We added nothing.
- The failure is a **real availability outage** (`kubectl scale deploy ratings-v1 --replicas=0`),
  not a hand-written mutant. The downstream span errors at the mesh level (Envoy "no healthy
  upstream", HTTP 503 / otel ERROR).
- So the baseline is not "0 by construction": the bug class arises naturally; we merely observe it.

## Method
1. Deploy Bookinfo (see `evaluation/suts/bookinfo/deploy/deploy.sh`); Jaeger tracing at 100% sampling.
2. Pin reviews → v3 (v3 calls ratings) for deterministic traces; drive traffic through the Istio
   ingress gateway (`GET /productpage`).
3. **Outage:** scale `ratings` to 0 replicas. Reviews degrades; productpage stays HTTP 200.
4. Capture the full Jaeger trace; run MIST's real `HiddenDownstreamFailureInvariant` (from `mist.jar`)
   on it, next to what a response-level oracle sees.

## Result — A/B (MIST's real oracle on real Bookinfo traces)

| Scenario | client / response-level oracle | trace oracle `HIDDEN_DOWNSTREAM_FAILURE` |
|---|---|---|
| **ratings outage** (trace `f13dbc33…`, `c80623f5…`) | HTTP 200 — **PASS (misses the failure)** | **FIRES — severity ERROR** |
| **ratings healthy** (control, trace `3c613a26…`) | HTTP 200 — pass | **pass (silent)** |

Verbatim oracle output on the masked trace (8 spans):
```
  client-facing ROOT : istio-ingressgateway.istio-system  productpage.default.svc.cluster.local:9080/productpage  http=200 otel=null
  downstream ERROR   : reviews.default  ratings.default.svc.cluster.local:9080/*  http=503 otel=ERROR
  --> RESPONSE-LEVEL oracle (status/schema/soft-error sees the client response): PASS  (root is 2xx — looks successful, MISSES the failure)
  --> TRACE oracle HIDDEN_DOWNSTREAM_FAILURE: FIRES  severity=ERROR
      caller received 2xx but 1 downstream span(s) server-errored (swallowed): reviews.default/ratings.default.svc.cluster.local:9080/* http=503 otel=ERROR
```
Healthy control: `HIDDEN_DOWNSTREAM_FAILURE: pass` (no downstream error) — the oracle fires **only**
on the genuine masked failure, not trivially.

## Reproduction
- Deploy: `evaluation/suts/bookinfo/deploy/deploy.sh`; outage toggle: `evaluation/suts/bookinfo/workload/`.
- Captured traces: `evaluation/suts/bookinfo/traces/` (masked + healthy).
- Oracle run: `java -cp mist-cli/target/mist.jar <OracleCheck.java> <trace.json> "GET /productpage"`
  (OracleCheck loads the trace via `TraceModel.fromJaegerJson` and runs the real invariant).

## Caveats (honest)
- The downstream error here is **outage-driven** (a real availability fault), surfaced at the mesh
  as a 503/UH span — this is the "swallowed failure from any cause" reading of the oracle, broader
  than input-driven. (Input-driven masking is a separate, harder-to-elicit case.)
- Local kind + Istio (mesh-injected sidecar spans). The masking itself is application-level
  (reviews' catch block), independent of the mesh; the mesh provides the 503 span + tracing.
- Single SUT here for the *phenomenon*; SP1's bigger trio (Sock Shop / Online Boutique / TeaStore)
  remains for external-validity breadth.
