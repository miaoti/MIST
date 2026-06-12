# Sock Shop — generalization SUT (MIST's 3rd SUT)

WeaveWorks **Sock Shop** is the **generalization-validation** SUT: a genuinely different
microservices REST app (services catalogue/cart/order/user; paths at root, no `/api/v1`;
grouped by OpenAPI **tags**, not `x-service-name`). It exists to prove that MIST runs on a
NEW SUT from **just its own inputs — 1 swagger-derived conf, 2 properties, traces — with NO
code edits and NO hand-fixed config**, after the train-ticket assumptions were cleared
(see `debug/generalization/PLAN.md`).

> This bundle is the SAME SP1 structure as `bookinfo/`. It is deliberately MINIMAL: the two
> `.properties` files OMIT auth (→ `auth.mode` defaults to none), the smart-fetch OAS path
> (→ defaults to `oas.path`), and the injected-faults path (→ no named faults). If Sock Shop
> runs with this, the generalization holds.

## Bundle contents (self-contained — own input files only)
- `deploy/deploy.sh` — add Sock Shop into the EXISTING kind+Istio+Jaeger cluster (bookinfo's),
  exclude its databases from the mesh (MySQL/Mongo TCP breaks under sidecars), and route it
  through the shared Istio ingress gateway. `deploy.sh teardown` removes it.
- `workload/capture-traces.sh` — drive the catalogue endpoints through the ingress (W3C
  `traceparent` markers) and pull the traces.
- `openapi/sockshop-swagger.yaml` — the SUT's OpenAPI (26 ops incl. 12 writes, 4 services by **tags**).
- `real-system-conf.yaml` — generated from the swagger via `io.mist.cli.MistConfGenMain` with
  NO hand-editing (basePath auto-applied — a no-op here since the API is at root).
- `sockshop-demo.properties` — the single MIST profile (core keys + MST section).
- `traces/` — captured catalogue traces (ingress→front-end; see the propagation caveat).
- `.runtime/` — per-SUT run dir (gitignored): all caches/logs/generated-tests isolate here.

## Quick start
```bash
REPO=$(pwd)
# 1. base cluster (kind+Istio+Jaeger) — bookinfo's deploy stands it up:
evaluation/suts/bookinfo/deploy/deploy.sh            # if not already running
# 2. add Sock Shop into the mesh + ingress routing:
evaluation/suts/sockshop/deploy/deploy.sh
# 3. port-forwards (shared with bookinfo):
kubectl port-forward -n istio-system svc/istio-ingressgateway 8080:80
kubectl port-forward -n istio-system svc/tracing 16686:80
# 4. (re)generate the conf from the swagger — RESTest-free, basePath auto:
java -cp mist-cli/target/mist.jar io.mist.cli.MistConfGenMain \
  evaluation/suts/sockshop/openapi/sockshop-swagger.yaml \
  evaluation/suts/sockshop/real-system-conf.yaml
# 5. capture traces, then run MIST from the SUT's own .runtime/:
evaluation/suts/sockshop/workload/capture-traces.sh
mkdir -p evaluation/suts/sockshop/.runtime
( cd evaluation/suts/sockshop/.runtime && \
  DEEPSEEK_API_KEY="$(cat "$REPO/.api_keys/DEEPSEEK_API_KEY")" \
  java -Dmist.javac=<path/to/jdk21/bin/javac> \
       -jar "$REPO/mist-cli/target/mist.jar" "$REPO/evaluation/suts/sockshop/sockshop-demo.properties" )
```
(On a JRE host, pass `-Dmist.javac=<jdk21 javac>`; on a JDK host it's automatic.)

## What this validates (the generalization, verified)
- conf-gen derives **4 services from tags** (catalogue/cart/order/user) and correct testPaths —
  no `x-service-name`, no hand-fixing. basePath `/` ⇒ paths stay at root (auto-apply is a no-op).
- smart-fetch grounds LLM discovery on the **real Sock Shop services** (whitelist drops the
  model's guesses) — 0 train-ticket (`ts-*`) leaks.
- `auth.mode` unset ⇒ defaults to none ⇒ tests run (no login-skip).
- MIST generates + compiles + executes the catalogue tests against the live SUT; all pass (200).

## Caveats (honest)
- **Tracing is shallow.** Sock Shop's front-end (Node) does not propagate the W3C `traceparent`
  to downstream services, so the marker trace is `ingress→front-end` only (no catalogue span).
  Bookinfo's services do propagate, so Bookinfo has the full chain. This is a SUT instrumentation
  limitation, not a MIST one — the marker-first lookup still works (via the ingress Envoy).
  Consequently Sock Shop is a **generalization** SUT (does MIST run anywhere?), not a
  hidden-downstream **phenomenon** SUT (that is bookinfo, with naturally-propagated full traces).
- **DBs out of the mesh.** Istio sidecars mangle MySQL/Mongo/Redis/RabbitMQ TCP, so the DBs are
  deployed without sidecars (deploy.sh handles it). The app↔app HTTP calls remain in the mesh.
- The cart/order/user endpoints are session-scoped; the clean, session-free validation path is
  the **catalogue** service (front-end→catalogue→catalogue-db).
