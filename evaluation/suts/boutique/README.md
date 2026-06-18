# Online Boutique — 4th SUT (2nd hidden-downstream, over gRPC)

Google Cloud **Online Boutique** (`microservices-demo`) is MIST's **4th** SUT and
the **second hidden-downstream (G2) demonstration** — over a **different RPC
protocol (gRPC)** than Bookinfo's HTTP. Same SP1 bundle shape as `bookinfo/` and
`sockshop/`: 1 swagger-derived conf, 2 properties, traces.

> **Honest note on shape.** Online Boutique's only HTTP service is the
> `frontend` (it serves server-rendered HTML); the other 10 services
> (productcatalog, cart, currency, recommendation, **adservice**, checkout,
> shipping, payment, email) are internal **gRPC** and are NOT REST-exposed.
> So this bundle's swagger describes exactly one HTTP service — the frontend's
> routes — and the conf has one service. That is the real REST surface; the
> value here is the **trace oracle**, which works on the gRPC spans regardless of
> the HTTP response being HTML.

## The phenomenon (real, non-circular)
The home page (`GET /`) fans out to the backends, including the **non-critical**
`adservice`. Ad rendering is best-effort: the frontend catches an `adservice`
failure, logs it, and renders the page **without ads** — `HTTP 200`. We scale
`adservice` to 0 (a real availability outage; no mutant). The client sees `200`;
the only evidence is the swallowed `frontend → adservice` gRPC span
(`otel.status_code=ERROR`, gRPC status 14 UNAVAILABLE). MIST's
`HiddenDownstreamFailure` invariant fires on it (severity WARN — an otel-error,
not an HTTP 5xx); a response-level oracle passes. The home-page body is a
**clean 200 HTML page with no error breadcrumb**, so unlike a soft error, a
body-reading oracle (LogiAgent, MIST's own soft-error check) cannot see it —
only the trace exposes it.

## Bundle contents (self-contained — own input files only)
- `deploy/deploy.sh` — add Online Boutique into the EXISTING kind+Istio+Jaeger
  cluster (bookinfo's), sidecar-injected so frontend→gRPC calls are traced.
  `deploy.sh teardown` removes it.
- `workload/capture-traces.sh` — drive the frontend, pull deep traces; then
  induce the adservice outage and capture the swallowed-gRPC-error traces.
- `openapi/boutique-swagger.yaml` — the frontend's HTTP routes (1 service).
- `real-system-conf.yaml` — generated from the swagger via `io.mist.cli.MistConfGenMain`.
- `boutique-demo.properties` — the single MIST profile (core keys + MST section)
  (hidden-downstream invariant enabled).
- `traces/` — `boutique_home.json` (healthy generation seed) +
  `boutique_adservice_outage.json` (the hidden-downstream evidence).
- `.runtime/` — per-SUT run dir (gitignored): caches/logs/generated-tests isolate here.

## Quick start

> **⚠ Prerequisite — run Bookinfo's `deploy.sh` FIRST.** Online Boutique has **no
> cluster of its own**; it deploys *into* the kind + Istio + Jaeger cluster that
> `evaluation/suts/bookinfo/deploy/deploy.sh` stands up. Run this bundle's
> `deploy.sh` with no such cluster and it fails (`couldn't get server API group
> list`). Re-running either `deploy.sh` after a crash/reboot is safe — they are
> idempotent (skip cluster create, re-apply manifests; nothing is re-cloned).

```bash
REPO=$(pwd)
# 1. base cluster (kind+Istio+Jaeger) — REQUIRED FIRST; bookinfo's deploy stands it up:
evaluation/suts/bookinfo/deploy/deploy.sh            # idempotent: skips work if already running
# 2. add Online Boutique:
evaluation/suts/boutique/deploy/deploy.sh
# 3. port-forwards (frontend on its own port so it doesn't clash with :8080):
kubectl port-forward -n boutique svc/frontend 8081:80
kubectl port-forward -n istio-system svc/tracing 16686:80
# 4. (re)generate the conf from the swagger:
java -cp mist-cli/target/mist.jar io.mist.cli.MistConfGenMain \
  evaluation/suts/boutique/openapi/boutique-swagger.yaml \
  evaluation/suts/boutique/real-system-conf.yaml
# 5. capture traces, then run MIST from the SUT's own .runtime/:
evaluation/suts/boutique/workload/capture-traces.sh
mkdir -p evaluation/suts/boutique/.runtime
( cd evaluation/suts/boutique/.runtime && \
  DEEPSEEK_API_KEY="$(cat "$REPO/.api_keys/DEEPSEEK_API_KEY")" \
  java -Dmist.javac=<path/to/jdk21/bin/javac> \
       -jar "$REPO/mist-cli/target/mist.jar" "$REPO/evaluation/suts/boutique/boutique-demo.properties" )
```

## Verify the oracle on the captured outage trace
```bash
java -cp mist-cli/target/mist.jar evaluation/suts/bookinfo/OracleCheck.java \
  evaluation/suts/boutique/traces/boutique_adservice_outage.json "GET /"
# -> HIDDEN_DOWNSTREAM_FAILURE: FIRES (severity WARN) on the swallowed adservice gRPC error;
#    RESPONSE-LEVEL oracle: PASS (misses). Healthy seed: 0 fires.
```

## Caveats (honest)
- **gRPC, otel-error tier.** The swallowed call fails with gRPC status 14, which
  Istio records as `otel.status_code=ERROR` + `http.status_code=200` (no HTTP
  5xx), so the invariant fires at **WARN** (vs Bookinfo's HTTP-503 → ERROR). Same
  invariant, lower-confidence tier — by design.
- **Outage-driven, not input-driven** (as on Bookinfo).
- **One HTTP service.** The conf has a single service (`frontend`); Online
  Boutique's breadth lives in internal gRPC, outside any black-box REST tester's
  reach. The oracle still sees the gRPC spans via OTel.
- See `docs/main-contribution/evidence/boutique_e2e_pipeline.md` for the full
  A/B (7/7 outage fire, 0 healthy) run on the live SUT.
