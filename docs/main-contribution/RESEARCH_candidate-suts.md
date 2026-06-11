# Candidate SUTs for hidden-downstream evidence — research verdict #3 (deep-research, 2026-05-29)

Question: is there a microservice SUT that *naturally* exhibits hidden-downstream failures
(gateway/aggregator returns 2xx while a downstream span errors), so we can produce **non-circular**
evidence — unlike train-ticket, which is defensive (4xx-rejects before downstream) + fails loudly?
Method: 5-angle fan-out, 23 sources, 3-vote verification. Raw: `research-raw-candidate-suts.json`.

## Verdict (frank)
**The worst case does NOT hold.** At least two canonical benchmarks naturally exhibit hidden-downstream
masking in present-day primary source (not injected): **Istio Bookinfo** and **Google Online Boutique**.
So a hidden-downstream contribution is **not structurally blocked** — there is a real path to
non-circular evidence. The narrower risk: each best candidate fails exactly ONE strict requirement.

## Candidates
| System | Tracing | REST? | Natural hidden-downstream? | Biggest risk |
|---|---|---|---|---|
| **Istio Bookinfo** ⭐ best REST fit | Jaeger (canonical Istio demo) | **Yes** (productpage→reviews→ratings are HTTP) | **Yes, in-tree:** reviews catches a failed/timed-out ratings call, returns HTTP 200 with `{"rating":{"error":"Ratings service is currently unavailable"}}`; productpage renders a clean page. Developer-intended (PR #15489: *"Catching the exception allows to show the reviews without the rating stars"*). | masking partly mesh/fault-injection-driven; degradation has regressed once (#14842 → fixed #15489) — re-verify at deploy |
| **Google Online Boutique** ⭐ best masking evidence | **native OpenTelemetry** out-of-box | **No — gRPC** internally (frontend HTTP only) | **Yes, committed Go:** frontend log-and-continues on adservice/recommendationservice errors → 200 page; comments *"ignores the error … since it is not critical"*. Mixed: fails LOUD (500) on critical cart/product/currency. | gRPC, not REST — failure is a gRPC error status on the span, not HTTP 5xx (oracle handles it via `otel=ERROR`; MIST's REST test-gen is the gap) |
| Instana Robot Shop | Instana only (no native Jaeger/OTel) | partial | weak — README disclaims *"error handling is patchy"* but NO documented specific masked-failure instance | speculative; needs added instrumentation |

## The non-circular evidence path (Bookinfo)
Bookinfo's `reviews` natively degrades on a **real** downstream exception/non-200 (the
`LibertyRestEndpoint` catch block) — independent of any Istio fault rule. So deploying `ratings` in a
**genuinely failing state** (e.g., its DB down) yields **productpage HTTP 200 + a failed ratings span**
with no injected VirtualService abort. That is a *natural* instance: response-level oracle sees 200
(passes), trace oracle catches the masked failure — and the "bug" is Bookinfo's real in-tree behavior +
a real outage, **not a mutant we wrote**. This is the rebuttal to the "baseline 0 by construction" objection.

## Phenomenon is a real, studied bug class (premise validation)
- **OSDI'14** (Yuan et al.): 25% of catastrophic failures caused by ignoring explicit errors, *explicitly*
  counting "an error handler that only logs" as ignoring — developer-confirmed in HBase/Cassandra/HDFS.
- **NSDI'24 Legolas**: partial/"gray" failure — a subset breaks while liveness checks still report healthy —
  the direct conceptual analogue.
- Caveat: both substrates are storage/coordination systems, **not microservices** — they validate the
  *phenomenon*, not a drop-in microservice SUT with confirmed bugs.

## Benchmark alignment (part C)
Recent REST-testing papers (e.g. ASE'23 RL) use mostly **standalone single APIs** (EvoMaster Benchmark:
Features Service, LanguageTool, NCS, REST Countries, SCS, Genome Nexus, …) — no gateway/BFF-over-downstream
topology, unusable for hidden-downstream. train-ticket remains an accepted single-SUT benchmark. So a
hidden-downstream contribution must look **outside** the most-cited REST-testing benchmark sets for topology.

## train-ticket unfitness — confirmed
Its 14 fault branches (async / multi-instance / config / monolithic) include **none** that is
"aggregator swallows a downstream 5xx"; corroborated as having "no circuit breakers, fallback handlers, or
graceful degradation" and faults that "propagate to dependent microservices" (loud). Our premise holds.

## Recommendation
1. **Adopt Bookinfo as the second SUT** (REST + Jaeger + natural, developer-intended graceful degradation +
   a real bug history). Produce the non-circular instance via a real `ratings` outage (above).
2. **Online Boutique as a third SUT** for breadth (native OTel; strongest committed masking evidence) —
   the oracle already handles its gRPC failures (`otel=ERROR` path); only MIST's REST test-gen needs adapting.
3. **Re-frame** the detector's reach: it catches a masked downstream failure regardless of cause (bad input
   OR outage). Bookinfo's natural masking is *outage*-driven; MIST's paradigm is *input*-driven — the eval
   can combine both (drive the SUT while a downstream is failing).
4. **Open**: no dataset of developer-confirmed hidden-downstream bugs in real *microservice* systems was found
   (OSDI/NSDI are non-microservice). Finding GitHub-issue / postmortem instances would fully retire the
   "circular" objection. Also unverified for a cleaner all-axes fit: DeathStarBench, Sock Shop, TeaStore,
   Spring PetClinic microservices, Acme Air, eShopOnContainers.

## Key sources
Istio Bookinfo source: `samples/bookinfo/src/reviews/.../LibertyRestEndpoint.java`, `…/productpage/productpage.py`,
PR istio/istio#15489, fault-injection task page · Online Boutique: github.com/GoogleCloudPlatform/microservices-demo
(frontend `chooseAd`/`getRecommendations`) · OSDI'14 "Simple Testing Can Prevent Most Critical Failures" ·
NSDI'24 "Legolas" · ASE'23 RL REST testing (EvoMaster Benchmark SUT list).
