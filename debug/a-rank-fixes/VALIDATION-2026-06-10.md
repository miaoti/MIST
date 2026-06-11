# Live + offline validation of the four deferred A-rank fixes (2026-06-10)

Follow-up to commits `de63674a` (the four fixes) and `71ceb040` (registry lint).
All four fixes are now validated against live SUTs where applicable; the
remainder is locked by tests. mist-core suite: **328 green** (314 baseline + 14
new across the two commits + this run's additions).

## Fix #1 — query parameters emitted (Sock Shop, LIVE)

Run: `java -jar mist.jar sockshop-demo-local.properties` against the live
kind-mist Sock Shop (front-end port-forward on :8080, `jaeger.enabled=false`
override — the cluster's shared jaeger pod is in a pre-existing CrashLoopBackOff,
unrelated to MIST and not needed for this fix). Exit 0, 202 generated / 216
executed.

- **Generated source**: `Flow_Scenario_65.java` (root `GET /catalogue`) emits
  `req.queryParam("page"/"size"/"tags"/"sort", …)`. Pre-fix these were dropped.
- **Form-encoding works**: a value `[a, b]` is emitted as `%5Ba%2C+b%5D`; a sniper
  OVERFLOW value lands as a long `XXXX…` string in `sort`. No request-line
  corruption.
- **Live front-end access log** (`kubectl logs deploy/front-end`): **301 of 301**
  catalogue hits carried a query string (`GET /catalogue?page=…&size=…&tags=…&sort=…`),
  **0** bare `GET /catalogue`. Pre-fix this ratio was 0 / N.
- **Sniper fault reaches query position**: injected values are visible in the live
  log, e.g. `sort=|+cat+/etc/passwd`, `sort=<img src=x onerror=alert('XSS')>`,
  `size=true`, `page=a` — all returned by the SUT (Sock Shop's catalogue is
  permissive; the point is the value *reached* the parameter, which it never did
  before).

## Fix #2 — Phase 3.5 dedup live (Sock Shop, LIVE)

Same run. Sock Shop's spec exposes catalogue at four root shapes
(`/catalogue`, `/catalogue/size`, `/catalogue/{id}`, `/tags`). The generator
emitted **exactly four** `Flow_Scenario_*.java` classes — one per distinct root
API, no duplicate single-root classes. With the pre-fix flag leak a fresh run
re-emitted duplicates for the same key. Logic is additionally locked by
`DedupFlagLeakRegressionTest` (6/6).

## Fix #3 — tracker idempotency (offline only — honest scope)

Locked by `FaultDetectionTrackerIdempotencyTest` (6/6) and the corrected
`FaultDetectionTrackerSummaryTest`.

**The Sock Shop run did NOT exercise this fix.** It ran with `jaeger.enabled=false`
(see the environment note below), and the generated oracle hook is
`if (!JAEGER_ENABLED) return;` at the top of `attachJaegerTrace`
(`MultiServiceRESTAssuredWriter.java:302`). So no Trace Shape Oracle verdict was
produced, `recordVerdict` was never called, and the "no oracle anomalies across
216 executed tests" line means "the oracle did not run", NOT "the oracle ran and
found nothing." The double-count branch this fix targets (a positive variant
failing on an ERROR verdict, recorded once on the success path and again in the
catch path) only occurs with the oracle live, so it is covered by the unit tests,
not by this run.

The oracle itself is unregressed by the writer/tracker changes: the offline
reproduction path of record (`OracleCheck` on committed traces, no Jaeger) still
fires — Bookinfo `HIDDEN_DOWNSTREAM_FAILURE` ERROR, Online Boutique gRPC
`HIDDEN_DOWNSTREAM_FAILURE` WARN, response-level oracle PASS/misses on both. A
live end-to-end re-confirmation of the double-count fix needs a healthy Jaeger
(see below) and is the one remaining gap; it does not affect any paper claim.

## Fix #4 — registry de-poison (TrainTicket, LIVE + offline)

Live TrainTicket reachable at `http://129.62.148.112:32677` (admin login 200,
`/api/v1/stationservice/stations` returns the station name set).

- **`TTEndStationLiveCheck`** (gated by `-Dtt.live.base.url` + `DEEPSEEK_API_KEY`):
  constructs MIST's own `SmartInputFetcher` with the shipped (de-poisoned)
  registry and calls `fetchSmartInput("endStation")` for consumer
  `POST /api/v1/adminrouteservice/adminroute`. The returned value is a member of
  the live `/stations` name set — i.e. grounded from the station producer, not
  the (previously poison-ranked) `trains` producer. **1/1 pass.**
- **`ShippedRegistryDepoisonTest`** (offline data lint): both shipped registries
  carry all-zero successRate, and the real endStation mappings rank stations over
  trains via `rankingScore`. **2/2 pass.**
- **`ProducerRankingTest`**: cold-start name-affinity logic. **4/4 pass.**

## Jaeger health and reviewer reproducibility

How MIST consumes traces (two independent paths):
- **Generation input** — `TraceWorkflowExtractor` reads the committed
  `evaluation/suts/*/traces/*.json` corpus (bookinfo 7, boutique 2, sockshop 4,
  trainticket 2). No live Jaeger. This is what builds scenarios.
- **Runtime oracle** — the generated test code queries `JAEGER_BASE_URL/traces/...`
  at execution time to run the Trace Shape Oracle. This needs a live Jaeger, and
  is gated by `jaeger.enabled` (default false).

Current state of the shared istio-system Jaeger: **CrashLoopBackOff**, restart
count 119 over 8 days. Root cause (diagnosed, not a MIST defect):
- It is the stock Istio demo addon (`samples/addons/jaeger.yaml`): all-in-one
  Jaeger v2.14.0, badger storage on an **emptyDir**, a liveness probe on
  `/status:13133` (failureThreshold 3 × periodSeconds 10, timeout 1s), and **no
  startupProbe**.
- emptyDir persists across container restarts within the same pod, so badger
  accumulated **1745 SST tables** over 8 days. Reopening them on boot now takes
  longer than the ~30s liveness window, so the query service is killed before it
  reports healthy → restart loop. No panic, exit 2 = liveness kill.
- Deleting/recreating the pod wipes the emptyDir → empty badger → sub-second
  startup → healthy. (Not done here: the pod is shared istio-system infra, and a
  delete was correctly blocked.)

Reviewer impact: **none on a fresh deploy.** `deploy/deploy.sh` installs a clean
Jaeger with empty badger; a reviewer's session (minutes–hours) never approaches
the 8-day accumulation that broke this long-lived instance. The committed
Bookinfo/Boutique in-process evidence was captured 2026-06-02 when this same
Jaeger was fresh and healthy, before the degradation. And the headline result
reproduces with **no Jaeger at all** via the offline `OracleCheck` path of record
(re-run today, still fires — see Fix #3 above).

Other notes:
- Live TT IP/port `129.62.148.112:32677` confirmed reachable from this host
  (an earlier "timeout" reading was a stale probe; re-checked with `curl -v` and a
  raw TCP open — both succeed, login returns 200).
- Local-only config copies (`sockshop-demo-local.properties`,
  `sockshop-mst-local.properties`) are gitignored and not committed.

## Remaining gap

A live end-to-end re-confirmation of Fix #3 (tracker double-count) needs a healthy
Jaeger. Two ways to get one for a follow-up: restart the istio-system jaeger pod
(wipes emptyDir), or run any SUT with `jaeger.enabled=true` against a fresh deploy.
This gap is oracle-reporting only and touches no paper claim.

**CLOSED later the same day** — see the second-pass section below: the Jaeger was
recovered AND hardened, and Fix #3 was validated live with a before/after binary
comparison (pre-fix hitCount=2, post-fix hitCount=1, byte-identical generated test).

---

# Second pass (2026-06-10, afternoon) — Jaeger recovered + reviewer-proofed, Fix #3 validated LIVE

## A. The crashed shared Jaeger: diagnosis independently re-verified, recovered, healthy

### A.1 Independent re-verification (evidence)

Pod state at start of this pass (`kubectl get pods -n istio-system`):

```
jaeger-6cc78db566-w7dcj   0/1   CrashLoopBackOff   151 (31s ago)   9d
```

Container spec (`kubectl describe pod …`): image `jaegertracing/jaeger:2.14.0`,
`Liveness: http-get http://:13133/status delay=0s timeout=1s period=10s #failure=3`,
identical Readiness probe, **no startupProbe** (confirmed empty via jsonpath), and
`/badger` mounted from volume `data` = `{"emptyDir":{}}` (confirmed via jsonpath on
the deployment). Same probe shape in the source manifest
`~/istio-1.30.0/samples/addons/jaeger.yaml` (probes carry no tuning fields, so the
k8s defaults above apply).

Previous-container log (`kubectl logs --previous --timestamps`): the ENTIRE log
spans 0.4 s — badger opens the accumulated store

```
badger 2026/06/10 20:22:08 INFO: All 1745 tables opened in 233ms
badger 2026/06/10 20:22:08 INFO: Set nextTxnTs to 49661278
```

(1745 SSTs, ~49.7M writes accumulated over 9 days of 100%-sampled mesh traffic),
extensions start, the last line ever printed is `jaeger_query` "Extension is
starting…" at 20:22:08.449 — then silence until the container terminates at
20:22:33, `exitCode=2`, reason `Error` (NOT `OOMKilled`). No panic, no error, no
shutdown line: the process was killed mid-startup, not self-crashed.

Kill mechanism (established by alignment + exclusion):

- The healthcheck endpoint demonstrably serves **503 while starting**:
  `Readiness probe failed: HTTP probe failed with statuscode: 503` (aggregated
  event, count 600). Liveness hits the SAME endpoint on the SAME schedule, so it
  fails identically.
- failureThreshold 3 × periodSeconds 10 ⇒ kill ~20–30 s after start. Observed
  container lifetimes: 20:22:08→20:22:33 (25 s) and 20:28:16→20:28:43 (27 s,
  restart #153) — every one of 153 restarts died inside that window (BackOff
  events aggregated to count 1801).
- Not OOM (`lastState.reason=Error`), not a panic (Go panics print stack traces;
  log is clean), not a graceful self-exit (no shutdown lines).
- Caveat recorded honestly: the literal `Killing` event objects had aged out of
  etcd (events TTL; kubelet spam-filter suppresses re-emission) and the kubelet
  journal inside the kind node was not reachable from this account
  (`docker.sock` permission denied), so the kill is established by the exclusion
  chain above rather than a kubelet log line.

Root-cause statement (matches the first-pass diagnosis): badger on an emptyDir
**survives container restarts within the pod**, grew for 9 days, and Jaeger
v2.14's startup over that store now exceeds the addon's ~30 s liveness window —
the stock addon ships no startupProbe, so the kubelet kills it before it ever
reports healthy. Crash-loop forever. A fresh pod (empty emptyDir) starts in
seconds — which the recovery below confirmed.

### A.2 Recovery (rolling restart ⇒ new pod ⇒ empty emptyDir)

```
$ kubectl rollout restart deployment/jaeger -n istio-system
$ kubectl rollout status  deployment/jaeger -n istio-system --timeout=180s
deployment "jaeger" successfully rolled out
jaeger-6cc78db566-w7dcj   0/1   Terminating   153   9d
jaeger-7db5d974d9-wgf9h   1/1   Running       0     10s
```

Event timeline captured live by a watch during the cutover: new pod created
20:31:45Z, started 20:31:48Z, one initial readiness 503 at 20:31:48Z (badger
opening an EMPTY store for a moment), then healthy; old pod deleted 20:31:50Z.

### A.3 "Actually healthy", not just Running

- Query API answers and lists services: `curl http://localhost:16686/jaeger/api/services`
  returned JSON with 11 services within ~2 min of the restart (the Online
  Boutique loadgenerator repopulates the fresh store immediately), growing to 17
  after Bookinfo traffic.
- Live trace round-trip: 3× `GET /productpage` through the ingressgateway
  port-forward (all 200, 20:33:28Z) ⇒ `/jaeger/api/traces?service=productpage.default`
  returns 3 fresh traces, 9 spans each, full chain
  `istio-ingressgateway → productpage → details/reviews → ratings`.
- Restart stability: 0 restarts over the whole rest of the session (final pod
  `jaeger-7c899b9f57-42lxh` at 18+ min: `1/1 Running 0` — well past the 2–3 min
  bar), and the strongest possible liveness proof: both Fix #3 oracle runs below
  fetched their marker traces through this Jaeger.

## B. Deploy-script hardening (reviewer-proof, all SUTs audited)

Inventory — every deploy path in the repo was checked for a Jaeger:

| deploy script | Jaeger? | action |
|---|---|---|
| `evaluation/suts/bookinfo/deploy/deploy.sh` | **applies `samples/addons/jaeger.yaml`** (Istio 1.30.0 addon, badger-on-emptyDir, no startupProbe) — the ONLY script in the repo that stands up a Jaeger (repo-wide grep for `samples/addons|jaeger.yaml`) | **hardened (below)** |
| `evaluation/suts/sockshop/deploy/deploy.sh` | none of its own — deploys into the SHARED kind+Istio+Jaeger cluster that bookinfo's script creates (upstream `complete-demo.yaml` carries no Jaeger) | covered by the bookinfo patch (single source of truth) |
| `evaluation/suts/boutique/deploy/deploy.sh` | none of its own — same shared cluster (upstream `kubernetes-manifests.yaml` carries no Jaeger) | covered by the bookinfo patch |
| `evaluation/suts/trainticket/deploy/deploy.sh` | none at all — docker-compose SUT; no tracing backend is deployed by the repo (the `:30005/jaeger/ui/api` in `trainticket-mst.properties` is the authors' lab k8s cluster, and the local-repro path runs with `jaeger.enabled=false`) | no change needed |

The hardening inserted in `bookinfo/deploy/deploy.sh` immediately after the
addon apply (step "3b"):

```bash
kubectl patch deployment jaeger -n istio-system --type=strategic -p '{
  "spec":{"template":{"spec":{"containers":[{"name":"jaeger","startupProbe":{
    "httpGet":{"path":"/status","port":13133},
    "periodSeconds":10,"failureThreshold":60,"timeoutSeconds":5}}]}}}}'
kubectl rollout status deployment/jaeger -n istio-system --timeout=300s
```

Design points:
- `startupProbe` on the SAME endpoint the addon's own probes use
  (`/status:13133`, verified against both the live deployment and the 1.30.0
  manifest); 60 × 10 s = **600 s** of (re)start budget before liveness takes
  over — orders of magnitude above the observed worst case, and k8s disables
  liveness/readiness until the startupProbe passes.
- Strategic merge keyed by container `name` ⇒ **idempotent**: re-running
  deploy.sh re-applies the manifest (client-side apply leaves the patched field
  alone — it is in neither last-applied nor the manifest) and re-patches as a
  no-op.
- The script previously never waited for Jaeger at all; the added
  `rollout status` makes "deploy.sh finished" imply "Jaeger is Ready".

Verified on the real cluster (not just "looks right"):
1. server-side dry-run accepted the patch and rendered the intended probe;
2. real patch ⇒ `deployment.apps/jaeger patched` ⇒ rollout completed ⇒ new pod
   `jaeger-7c899b9f57-42lxh  1/1 Running 0`;
3. live spec now carries
   `{"failureThreshold":60,"httpGet":{"path":"/status","port":13133,"scheme":"HTTP"},"periodSeconds":10,"successThreshold":1,"timeoutSeconds":5}`;
4. idempotency proven: the IDENTICAL patch again ⇒ `patched (no change)`,
   `.metadata.generation` stayed 3 (no new rollout, no side effects), and the
   script's verbatim multi-line command was replayed once more from the shell
   with the same result;
5. `bash -n deploy.sh` passes; no other file in the repo applies a Jaeger.

Scope guard honored: only the deploy script changed — no tool source, no
committed evidence, no SUT manifests.

## C. Fix #3 (tracker double-count) — LIVE before/after on the healthy Jaeger

### Setup (hand-checkable by construction)

- SUT: Bookinfo behind the istio ingressgateway (port-forward :18080 — :8080 on
  this host is held by an unrelated sock-shop forward), `reviews` pinned to v3.
- Real fault: `workload/inject-ratings-outage.sh on` (scale ratings-v1 to 0).
  Verified shape before the runs: direct `/api/v1/products/0/ratings` → **503**;
  `/api/v1/products/0/reviews` → **200** with
  `"rating": {"error": "Ratings service is currently unavailable"}` — the
  hidden-downstream phenomenon, live.
- Minimal MIST config (local, gitignored: `bookinfo-demo-c3-local.properties` +
  `bookinfo-mst-c3-local.properties`): trace corpus narrowed to ONE committed
  trace (`gen_api_reviews.json`, 1 trace ⇒ 1 scenario), `testsperoperation=1`,
  `faulty.ratio=0`, enhancer/status-code-exploration/LLM-response-validation
  OFF, `jaeger.enabled=true`, hidden_downstream invariant ON, propagation delay
  5000 ms, registry paths pointed at throwaway copies under the job tmp dir.
  Two config gotchas found live and worth recording:
  `MistGenerator.getVariantCountFromProperties()` reads `testsperoperation`
  FIRST (it shadows `test.variants.per.scenario`; with the committed 20 the
  run produced 42 variants), and MST reads `faulty.ratio` from System
  properties via `MstConfig.Faulty` (default 0.1) — the core-file value is NOT
  bridged, so the key must live in the MST file / `-D` (passed as `-D` for
  belt-and-suspenders). Net effect, confirmed in the run log:
  `Configured variant count: 1, faultyRatio: 0.0` +
  `faultyRatio=0 — suppressing 23 fault target(s); positives-only generation`
  ⇒ **exactly 1 positive variant, 1 executed test**.
- Binaries: OLD = `b5266f62` (direct parent of the fix commit `de63674a`),
  built from `git archive` into the job tmp (`mvn -pl mist-cli -am package
  -DskipTests`), jar sha256 `e9bc7264ef7418d4…`; NEW = this worktree HEAD
  (`d17f51e8`), jar sha256 `893ccd37ac3f238a…`.
- Determinism: both runs used `-Drandom.seed=42` from the same cwd
  (`.runtime/`), so the seeded LLM call cache (`.mist/llm-call-cache.json`,
  written by the OLD run, replayed by the NEW run) made the generated test
  sources **byte-identical** (`diff -r` of the two generated trees: clean).
  The ONLY variable between the runs is the binary.

### Result — the prescribed golden standard

| | OLD `b5266f62` (pre-fix) | NEW `d17f51e8` (fixed) |
|---|---|---|
| Total Test Cases | 1 | 1 |
| Executed test | `…Flow_Scenario_1.test_positive_flow_S1_v1` | identical (byte-identical source) |
| ORACLE ANOMALIES | **1 distinct, 2 hits** | **1 distinct, 1 hits** |
| `HIDDEN_DOWNSTREAM_FAILURE` entry | `Hits: 2 time(s)` | `Hits: 1 time(s)` |
| First / Last seen | 15:50:01 / **15:50:06** | 15:51:40 / 15:51:40 |
| Marker trace | `c7322cda1591496090e25a09f6efcdea` | `5dcc5cd27a524ab1bd18c028647c2470` |

Report fragments (verbatim, from
`logs/fault-detection-reports/fault-detection-summary-bookinfo_fix3_live_42-*.txt`):

OLD binary:
```
ORACLE ANOMALIES (1 distinct, 2 hits)
1. HIDDEN_DOWNSTREAM_FAILURE  |  GET /api/v1/products/0/reviews
   Severity:      ERROR
   Violation:     reviews.default ──▶ ratings.default.svc.cluster.local:9080/* (http=503 otel=ERROR)
   Hits:          2 time(s)
   First seen:    2026-06-10 15:50:01
   Last seen:     2026-06-10 15:50:06
TEST CASES EXECUTED (1)
```

NEW binary:
```
ORACLE ANOMALIES (1 distinct, 1 hits)
1. HIDDEN_DOWNSTREAM_FAILURE  |  GET /api/v1/products/0/reviews
   Severity:      ERROR
   Violation:     reviews.default ──▶ ratings.default.svc.cluster.local:9080/* (http=503 otel=ERROR)
   Hits:          1 time(s)
   First seen:    2026-06-10 15:51:40
   Last seen:     2026-06-10 15:51:40
TEST CASES EXECUTED (1)
```

End-of-run console findings (printRaw block), same story at a glance:
OLD `…(2x, trace c7322cda)` / `1 unique findings, 2 occurrences`;
NEW `…(1x, trace 5dcc5cd2)` / `1 unique findings, 1 occurrences`.

Why the NEW count is 1 *because of the fix* and not because the second record
never happened: the NEW run's log shows the positive variant DID throw on the
ERROR verdict —
`❌ Root 1: … [expect 200] - FAILED: Positive variant failed — Trace Shape
Oracle verdict has violation(s): [HIDDEN_DOWNSTREAM_FAILURE: …]` at 15:51:40 —
so the writer's catch path executed, re-fetched the same marker trace (the
JUnit collector line lands at 15:51:45, i.e. the catch path consumed its 5 s
propagation delay) and called `recordVerdict` again; the tracker deduplicated
it (`recordedAnomalyInstances` key class+method+traceId+anomaly), so `Last
seen` stayed 15:51:40. In the OLD run the identical second call landed as
`Hits: 2` with `Last seen` exactly 5 s after `First seen` — the two recording
paths of the same single execution, 1:1 with the bug description.

### Attribution roll-up (C.4)

Neither report renders an `Attribution:` line or roll-up block: positive
variants carry no injected target, so `TargetAttributionInvariant` produces no
`TARGET_ATTRIBUTION` outcome and there is nothing to bump — the roll-up cannot
be polluted on this (all-positive) path at all. The fix's gating for paths that
DO attribute (`recordVerdict` bumps the histogram only when
`recordOracleAnomaly` returns "counted") is locked offline by
`FaultDetectionTrackerIdempotencyTest.sameExecutionRecordedTwice_attributionCountsOnce`.
Tracker suites re-run on this binary: `FaultDetectionTracker*` (Idempotency,
Attribution, OracleAnomaly, Summary) — **27/27 green**.

### Artifacts

Run products (reports, generated test trees, consoles, OLD-binary source tree)
live under the job tmp dir and `.runtime/` — both outside git; the two local
`*-local.properties` are gitignored by pattern. Nothing generated was
committed. SUT state restored after the runs (`inject-ratings-outage.sh off`,
ratings answers 200; outage-window traces remain only in Jaeger's emptyDir).
