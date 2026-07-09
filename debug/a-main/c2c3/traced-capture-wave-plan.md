# Traced-capture wave — plan (pre-registered; PENDING ≥3-cold-review before execution)

Date 2026-07-10. Owner: main_track. Status: **PLAN ONLY — no cluster change until the review wave
reconciles.** Prereq context: `benchmark/README.md` (13-case pilot), `REVIEW-CORPUS-RECONCILIATION.md`
(R2/R3/R4), `c2-freeze.md` rev-2.1 (§4 capture_status-keyed evaluability).

## 1. Goal (what this wave EARNS, in freeze terms)
1. **The N-vs-0 recall row** (freeze §4, flagship): on a *traced* deploy the two fabricated-ack positives
   (`TT-cancel-refund-fabricatedack-001`, `TT-createaccount-agreement-001`) become
   `trace-invisible-by-construction` — the trace oracles **run and MISS** (`no_flag`), only
   `mist_readback` catches. Today those cells are `not_applicable` (uninstrumented), so the row is only a
   pre-registered claim. This wave converts it to a measured result.
2. **Upgrade the breadth pair's pre-registered expectation to measured**: adminroute/adminbasic
   lost-writes are `span-presence-visible` under instrumentation — the skipped downstream persist span is
   ABSENT (presence-assertion flags; disclosed as NOT MIST-unique once traced) while naive error-span +
   trace-shape stay clean.
3. **Reusable instrumentation runbook** for the later FP/TP pair (bookinfo/sockshop needs the same
   OTel-javaagent + collector mechanics plus queue-master consume spans).

## 2. Scope (exactly which captures re-run)
8 captures, each with FRESH ids and probe-first discipline, traces exported per leg:

| # | case | leg | deploy condition |
|---|---|---|---|
| 1 | TT-cancel-refund-fabricatedack-001 | fault | fork inside-payment 1.0.5, drawbackFaultMode=fabricatedack |
| 2 | TT-cancel-refund-clean-001 | control | same image, faultmode=none |
| 3 | TT-createaccount-agreement-001 | fault | same image, createAccountFaultMode=fabricatedack |
| 4 | TT-createaccount-clean-001 | control | same image, createfaultmode=none |
| 5 | TT-adminroute-lostwrite-001 | fault | fork admin-route 1.0.5 + env flag=true |
| 6 | TT-adminroute-control-001 | control | base 1.0.0 (flag path identical) — OPTIONAL re-capture; see D3 |
| 7 | TT-adminbasic-contacts-lostwrite-001 | fault | fork admin-basic 1.0.5 + env flag=true |
| 8 | TT-adminbasic-contacts-control-001 | control | same fork image, env flag=false |

cancel path services to instrument: ts-cancel-service, ts-inside-payment-service, ts-order-service
(+ ts-ui-dashboard is nginx — NOT instrumented; entry span comes from the first Java hop).
admin paths add: ts-admin-route-service, ts-route-service, ts-admin-basic-info-service,
ts-contacts-service. Total 7 Java services.

## 3. Mechanics
- **Backend exists**: `jaeger-collector.istio-system:4317` (OTLP gRPC) / `4318` (HTTP) — verified live.
- **Agent**: OpenTelemetry Java agent (Java-8-compatible 1.x line; PIN the exact version + sha256 in the
  runbook at execution). Delivery = **docker cp the jar into the kind node once**
  (`/otel/opentelemetry-javaagent.jar`), then a `hostPath` volume + mount per instrumented deployment —
  no image rebuilds, works for base AND fork images, fully reversible.
- **Per-deployment env** (via kubectl set env + volume patch):
  `JAVA_TOOL_OPTIONS=-javaagent:/otel/opentelemetry-javaagent.jar`,
  `OTEL_SERVICE_NAME=<svc>`, `OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger-collector.istio-system:4318`,
  `OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf`, `OTEL_TRACES_SAMPLER=always_on` (bounded window),
  `OTEL_METRICS_EXPORTER=none`, `OTEL_LOGS_EXPORTER=none`.
- **Memory rider**: TT containers run `java -Xmx200m`; the agent needs headroom → raise the container
  memory limit (if any) and accept agent overhead; if a service OOMs/boot-fails, bump to 300m via
  JAVA_TOOL_OPTIONS `-Xmx300m` (JTO appends AFTER the CMD's -Xmx200m? NO — JTO options are prepended by
  the JVM and CMD flags win; overriding heap needs `_JAVA_OPTIONS` or leaving heap alone. DECISION: do
  NOT fight the heap; the agent runs fine at 200m heap since agent memory is mostly native/metaspace —
  but WATCH pod restarts; fallback = rebuild the two fork images with the agent baked in + bigger -Xmx).
- **Trace export per capture**: query the Jaeger HTTP API
  (`/api/traces?service=<entry-svc>&start=<t0>&end=<t1>&limit=...`) for the capture's time window; store
  the full trace JSON at `b4/captures/<case>/trace-<leg>.json`; link via `provenance.fault_trace` /
  `provenance.control_trace` (the schema fields exist and are null today).
- **Oracle scoring on the exported traces** (small script, committed): `naive_span_error` = any
  error-tagged span under a 2xx entry; `tracetest_presence` = the named downstream span
  (per-case selector, e.g. `ts-inside-payment-service POST /drawback` | `ts-route-service persist` |
  `ts-contacts-service create`) present?; `mist_trace_shape` = MIST's masking logic — for THIS wave
  report it only if MIST's actual trace-shape oracle can run on the exported trace; otherwise mark the
  column's upgrade deferred (do NOT hand-simulate MIST; anti-circularity).
- **Probe-first discipline** (breadth-wave runbook rule): after every rollout, throwaway-probe the entry
  path for the expected leg behavior before the real capture.
- **Restore**: remove JAVA_TOOL_OPTIONS/OTEL_* env + volume mounts, restore base images, verify pods
  healthy + key-path 200s. The cluster ends in today's state.

## 4. Case/file updates on success (per leg, only from MEASURED artifacts)
- `trace_visibility`: fabricated-ack pair → `trace-invisible-by-construction`; breadth pair →
  `span-presence-visible`; controls → `error-span-visible`? NO — controls have no error; controls keep
  their visibility axis consistent with the fault twin's instrumented condition (value chosen at
  execution from the actual traces; disclose).
- `oracle_expectation` trace columns: flip from `not_applicable` to the MEASURED verdicts
  (fabricated-ack: naive=no_flag, presence=no_flag [the /drawback|/account span IS present with a normal
  status] or not_applicable for in-process createaccount — decide from the actual trace; breadth:
  presence=flag [persist span absent], naive=no_flag).
- `provenance.fault_trace`/`control_trace` populated; sidecars gain trace-file pointers (sidecar spec
  allows raw-artifact paths).
- Freeze §6 amendment row; README front-matter + §5 + scale plan; FILE_INDEX; checklist.
- **Honesty rails**: every flipped cell must cite the exported trace file; `mist_trace_shape` is NEVER
  hand-derived; if a leg's instrumentation fails, its cells STAY `not_applicable` with the failure
  disclosed (no silent partial upgrade).

## 5. Risks / pre-registered failure handling
| risk | handling |
|---|---|
| Java-8 Spring Boot 1.x boot failure under the agent (nacos/ribbon interference) | per-service canary: instrument ONE service, verify Running + registered + key-path 200 before the next; any failure → that service stays uninstrumented, its cases' cells stay not_applicable, disclosed |
| OOM at -Xmx200m + agent | watch restarts; fallback = bake-in rebuild for the 2 fork images only; base services left alone → partial instrumentation disclosed |
| JTO ignored (some TT images strip env) | verify agent banner in pod logs at canary |
| Entry span missing (nginx entry, no Java hop instrumented on the path) | entry = first instrumented hop; scoring treats the first-hop server span as the entry — disclosed in the scoring script |
| Trace context broken across @LoadBalanced RestTemplate | the agent instruments RestTemplate; verify parent-child linkage at canary; if broken, per-service spans still allow presence-assertions (weaker but honest: presence scoring per service+operation+time-window correlation; naive/shape need linkage → stay not_applicable, disclosed) |
| Jaeger OTLP ingest rejects (version mismatch) | canary export first; fallback zipkin 9411 exporter |
| tenancy/RAM pressure (7 more agent-bearing JVMs) | instrument ONLY the 7 involved services; if node pressure → run the wave in two sub-batches (cancel-path first, admin-path second), de-instrumenting between |
| clock skew between driver host and Jaeger | select traces by service+operation+id-bearing attributes (route id / documentNumber / userId in URL paths), not by time alone |

## 6. Execution order
0. (this plan) ≥3-cold-review → reconcile → GO/NO-GO.
1. Pin agent version+sha; docker cp into node; canary = ts-contacts-service (leaf, low risk): mount+env,
   verify boot/banner/Jaeger service appears; throwaway request → span visible in Jaeger API.
2. Cancel-path batch: instrument order/cancel/inside-payment; captures #1-#4 (faultmode toggles per leg;
   probe-first; export traces).
3. Admin-path batch: instrument admin-route/route/admin-basic (+contacts already canaried); rollouts to
   fork images + env flags as in §2; captures #5-#8; export.
4. Score traces (committed script) → case/file updates (§4) → validate corpus → restore cluster →
   freeze amendment + README + FILE_INDEX + checklist → commit.
5. The wave's own result note (what was earned vs deferred) → fold into the next consolidation.

## 7. Out of scope
FP/TP pair (bookinfo/sockshop — needs tenancy window + Node/queue-master instrumentation), S3, TeaStore/
OTel-Demo deploys, any MIST tool-code change (prep-only rule stands).
