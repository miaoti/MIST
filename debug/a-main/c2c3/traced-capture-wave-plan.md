# Traced-capture wave — plan REV 2 (3-cold-reviewed: ALL GO-WITH-CHANGES → amended; EXECUTION GO)

Date 2026-07-10. Owner: main_track. Status: **REV 2 — review wave reconciled
(`../REVIEW-TRACED-WAVE-RECONCILIATION.md`, reviewers A feasibility / B evidential / C scope-value);
the §3.5 BINDING PINS below are normative and override any older §3/§4 phrasing they touch.**
Prereq context: `benchmark/README.md` (13-case pilot), `REVIEW-CORPUS-RECONCILIATION.md` (R2/R3/R4),
`c2-freeze.md` rev-2.1 (§4 capture_status-keyed evaluability).
**Relationship to the checklist (T10, pinned): this wave partially discharges 2.5.1 (TT write-path
javaagents) as a de-risking PILOT; instrumentation is TORN DOWN afterwards (preserves the DoD-passed
baseline); deliverables consumed by 2.5.1/2.5.2 = the runbook + pinned agent version + committed scoring
script + the TT row of 2.5.4's trace-coverage table (produced from this wave's actual traces). The
2.15(b) lean-traced convergence stays a separate later decision. Budget (T4): 2–3 days; stop rule: if
batch 1 + scoring exceed 2 days, or a canary-class failure recurs on a second service, SHIP BATCH 1
ALONE (batch 2 folds into 2.5.1 + a freeze-amendment disclosure that the comparator-favoring breadth
cells remain pre-registered). G1 precedent (T5): JTO+OTel javaagent ran full paired runs on this exact
TT stack (`gate1-result.md:168`, `gate1-smoke-result.md:39`) — boot risk is verify-not-fear; pin the
1.x line (1.33.z) + sha256 + the 1.x semconv names (`http.target`/`http.method`/`http.status_code`).**

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
| 6 | TT-adminroute-control-001 | control | base 1.0.0 (flag path identical; digest binding preserved) — **REQUIRED** (T1: the traced control is the presence-assertion's baseline — absence on the fault leg is unfalsifiable without a same-deploy leg that PRODUCES the span) |
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
- **Trace export per capture** (CORRECTED, A-F2/F3: the backend is **Jaeger v2.14** with
  `base_path: /jaeger`, badger storage on an **emptyDir**): query
  `/jaeger/api/traces?service=<entry-svc>&start=<t0µs>&end=<t1µs>&limit=200` (epoch **microseconds**,
  explicit limit); **export + verify non-empty IMMEDIATELY after each leg's capture, before touching the
  cluster again** (an eviction wipes badger); t0/t1 taken with the WSL clock (same VM as the JVMs; skew≈0);
  trace selection = entry service+operation+window, **exactly-one-match else ERROR**, URL-borne ids as a
  cross-check where they exist (cancel pair only — admin ids ride in POST bodies). Store
  `b4/captures/<case>/trace-<leg>.json`; link `provenance.fault_trace`/`control_trace` (positive case
  gets both, pointing at the twin's export for control_trace; control case gets control_trace).
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

## 3.5 REV-2 BINDING PINS (from the reconciliation; normative)
- **T6 presence semantics + granularity**: `tracetest_presence` = **existence-only** assertion at
  **cross-service HTTP-span granularity** (selector = the `target.dependency` service's SERVER span, what
  a Tracetest user names from the service map); error/status belongs exclusively to `naive_span_error`.
  Agent runs with **DEFAULT instrumentation — NO `OTEL_INSTRUMENTATION_*_ENABLED=false` suppression**
  (attestation recorded in each sidecar). **Mandatory measured disclosure** per fabricated-ack case + in
  the N-vs-0 row: whether the lost write's absence IS visible at DB-span granularity (the agent
  instruments JDBC/Mongo by default — control leg has the write span, fault leg lacks it) and that a
  statement-level presence assertion authored at that granularity WOULD catch it. Disclosed, this
  strengthens the read-back story (black-box equivalent of statement-level assertions).
- **T7 createaccount presence pinned NOW**: `not_applicable` on BOTH createaccount legs (in-process
  persistence, no cross-service span at the pinned granularity). Row-N wording (B-m6): "2 fabricated-ack
  positives, 2 defect sites, 1 service; presence run-and-missed evidence N=1 (cancel)".
- **T8 scoring script pre-commit**: the script + frozen per-case selector table are COMMITTED BEFORE the
  first real capture (canary/throwaway traces only for debugging). Selectors: cancel →
  ts-inside-payment-service server span for POST /drawback; adminroute → ts-route-service server span
  (persist POST); adminbasic → ts-contacts-service server span (contacts create); createaccount →
  not_applicable. Error-span def = exported ERROR status, mechanical, scoped to the 7 instrumented
  services, NO exclusions ever. Record selector authoring minutes (freeze authoring_cost datum).
  Disclosure: the presence column is scored by this committed script implementing presence SEMANTICS on
  exported traces — the Tracetest PRODUCT does not run in this wave.
- **T9 mist_trace_shape**: Branch A attempted first PER LEG with existing vehicles only (no tool-code
  changes): a bounded mist-cli run at pinned commit 7d69de9 with `jaeger.base.url` bound, where the demo
  corpus covers the path (adminroute: covered). Legs with no existing vehicle → Branch B: disclosed
  freeze §6 amendment scoping the N-vs-0 row's "trace oracles" to the comparator columns; the cell keeps
  a prose note "traced-but-not-run; deferred to <named wave>" (distinct from R2's no-input
  not_applicable). Symmetry pre-empt: `mist_readback` is fillable without running MIST because the typed
  readback contract (R4) is construction-deterministic; `mist_trace_shape` has no typed contract — which
  is exactly why hand-derivation is banned.
- **T2 capture-of-record**: each traced re-capture = a complete NEW sidecar; ALL 7 oracle columns
  re-recorded from that single run; old sidecars kept as superseded history; any divergence on a
  non-trace cell → the NEW measurement stands + disclosed.
- **T3 RAM discipline (581 MB free, swap active — the present state, not a contingency)**: sub-batching
  DEFAULT; one deployment at a time; **scale-0 → single `kubectl patch` (volume+mount+env in one change)
  → scale-1** (no surge); de-instrument batch 1 before batch 2; canary (contacts) de-instrumented after
  the canary, re-instrumented in batch 2; scale ts-consign-price-service to 0 for the wave (restore
  after); `free` + pod-restart + `OutOfMemoryError`/export-failure log grep between steps; proceed
  threshold ≥400 MB free after consign-price scale-0, else STOP.
- **B-M1 noise rules**: canary gate — each path's probe trace must be error-span-free before real
  captures (remediation only for deploy-health causes, disclosed); a real capture showing a background
  error span is recorded AS MEASURED + diagnosed; quiescent cluster (no concurrent traffic), disclosed.
  If RestTemplate context propagation is broken: window correlation supports span-PRESENT verdicts only —
  **absence-based `flag` cells are NOT claimable**; they stay unfilled + disclosed.
- **B-M3 controls' trace_visibility pinned**: a control carries its positive twin's
  instrumented-condition value + the fixed note "describes the PAIR's visibility regime; the control leg
  has no fault to be visible".
- **C-M4 health gates**: pre-wave `kubectl get deploy -o yaml` snapshot of the 7 deployments; pre-wave
  RAM/pod-health check (threshold above); post-restore = demo-DoD key-path smoke (login + adminroute GET
  + cancel-path probe), not just 200s.
- **Minors**: `docker exec mist-control-plane mkdir -p /otel` before `docker cp`; `hostPath.type: File`;
  fallback bake-in rebuild ⇒ new digest ⇒ label re-version + disclosure; record the FULL OTEL_*/JTO env
  set per deployment in each sidecar; mesh precondition: no residual EnvoyFilter/VS fault config on
  inside-payment; re-verify the ack-text tells on the new sidecars; optional BOUNDED 30–60 min
  normal-corpus rider (skip ⇒ note the config-drift caveat); fork 1.0.5 images verified already
  kind-loaded (no builds).

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

## 6. Execution order (rev 2)
0. ✓ ≥3-cold-review → reconciliation (`REVIEW-TRACED-WAVE-RECONCILIATION.md`) → GO.
1. Pre-wave gates (C-M4/T3): deploy-spec snapshot of the 7 deployments; scale consign-price to 0;
   RAM check (≥400 MB free else STOP); pin agent 1.33.z + sha + semconv names; mkdir + docker cp into
   the node.
1.5. **Commit the scoring script + frozen selector table (T8) BEFORE any real capture.**
2. Canary = ts-contacts-service (base 1.0.0, OpenJDK 8u111 = worst case): scale-0 → single patch →
   scale-1; verify JTO "Picked up" banner + no OOME + nacos registration + key-path 200; throwaway
   request → span visible via `/jaeger/api/traces`; error-span-free probe gate. De-instrument after.
3. Cancel-path batch: instrument order/cancel/inside-payment (one at a time, scale-0→patch→scale-1);
   captures #1–#4 (faultmode toggles per leg; probe-first; EXPORT + verify non-empty per leg,
   immediately). T9 Branch-A attempt where a vehicle exists. De-instrument batch 1.
4. STOP-RULE CHECK (T4). Admin-path batch: instrument admin-route/route/admin-basic/contacts;
   fork-image + env-flag rollouts as §2; captures #5–#8 (all four controls REQUIRED); per-leg export;
   T9 Branch-A for adminroute via the bounded mist-cli demo-corpus run. De-instrument.
5. Score (committed script) → case/file updates (§4 under the §3.5 pins) → validate corpus → restore
   cluster (images/env/consign-price) → post-restore demo-DoD key-path smoke → freeze §6 amendment +
   README + FILE_INDEX + checklist (2.5.1 pointer + 2.5.4 TT coverage row) → commit.
6. The wave's own result note (earned vs deferred, incl. the T6 DB-granularity disclosure and the T9
   per-leg run status) → fold into the next consolidation.

## 7. Out of scope
FP/TP pair (bookinfo/sockshop — needs tenancy window + Node/queue-master instrumentation), S3, TeaStore/
OTel-Demo deploys, any MIST tool-code change (prep-only rule stands).
