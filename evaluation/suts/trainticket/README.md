# TrainTicket — fault-injection SUT (MIST evaluation)

FudanSELab **TrainTicket** (~40 microservices) is MIST's dedicated **fault-injection**
SUT. Seven services carry **code-level injected faults**; on a faulty request they answer
`HTTP 200` with a self-reporting body `{"data":{"injected":true,"faultName":"..."}}`, which
MIST matches against `injectedFaults/injected-faults.json` (10 faults) to score detection.
TrainTicket validates defensively and **fails loudly** (every error propagates a 5xx to the
root), so — unlike Bookinfo/Online Boutique — it has no natural hidden-downstream case; it is
the SUT for the **Sniper negative-test + injected-fault** detection story (10/10).

> This bundle deploys TrainTicket the **proven way the paper's run22 used: Kubernetes via the
> upstream `make deploy`** (minikube). `make build` compiles the ~40 services from the
> injection-branch source — so the 7 fault services carry their injected fault code — and
> `make deploy` k8s-deploys an all-in-one MySQL plus the gateway (NodePort 32677). It is HEAVY
> (~40 JVMs); see the resource note below and REPRODUCE.md §6.3.

## Bundle contents
- `deploy/deploy.sh` — one command: clone the injection source, then `minikube` + `make deploy`
  (build the ~40 service images from source with the fault code baked in, k8s-deploy all-in-one
  MySQL + the NodePort-32677 gateway), and port-forward it to `localhost:32677`. `deploy.sh
  teardown` runs `make reset-deploy`. (A root `docker-compose.yml` also exists in that repo but is
  Mongo-backed while the services are MySQL, so it does not converge — `make deploy` is the path.)
- `openapi/merged_openapi_spec.yaml` — the SUT spec, 265 operations (MIST input #1).
- `real-system-conf.yaml` — MIST conf generated from the spec (MIST input #2a).
- `trainticket-demo.properties` — the single MIST profile (core keys + MST section; input #2b).
- `trainticket-demo-noexec.properties` + `trainticket-mst-noexec.properties` — **offline** profile
  (no SUT, no LLM: `llm.enabled=false`, hardcode inputs) for a zero-infra generation check.
- `traces/` — captured Jaeger traces (MIST input #3).
- `injectedFaults/injected-faults.json` — the **10 injected faults** (non-empty: TrainTicket's
  detection is a SUT-reported named-fault match, unlike Bookinfo's oracle-anomaly).
- `root-api-registry.json` / `input-fetch-registry.yaml` — per-SUT registries (regenerated from traces).
- `ResponseEnvelopeLiveCheck.java` — runs MIST's shipped ResponseEnvelope (LLM-backed) on a
  soft-error response body; see also `docs/main-contribution/evidence/responseenvelope_live_softerror.txt`.

## Fault source (which 7 services are built)
`ts-auth-service`, `ts-admin-basic-info-service`, `ts-admin-order-service`,
`ts-admin-route-service`, `ts-admin-travel-service`, `ts-travel-service`,
`ts-travel-plan-service` — the services whose source contains `INJECTED FAULT` /
`faultName` / `INVALID_*_FAULT`. The public `codewisdom/*` images are upstream (no faults), so
these 7 must be built from `https://github.com/AsifShaafi/train-ticket-injection` (branch `injection`).

## Quick start (from the repo root)
```bash
REPO=$(pwd)

# 1. deploy the fault-injection TrainTicket locally (minikube + make deploy; first
#    run compiles ~40 services from source — long; needs a 16+-core, >=16GB host)
evaluation/suts/trainticket/deploy/deploy.sh            # gateway -> http://localhost:32677

# 2a. offline generation check (no SUT, no LLM):
mkdir -p evaluation/suts/trainticket/.runtime
( cd evaluation/suts/trainticket/.runtime && \
  java -jar "$REPO/mist-cli/target/mist.jar" "$REPO/evaluation/suts/trainticket/trainticket-demo-noexec.properties" )

# 2b. full detection run against the LOCAL SUT (generate -> in-process compile -> execute -> faults):
#     needs a JDK on JAVA_HOME (compile step) + a DeepSeek key (or Ollama).
mkdir -p evaluation/suts/trainticket/.runtime
( cd evaluation/suts/trainticket/.runtime && \
  JAVA_HOME=/path/to/jdk21 DEEPSEEK_API_KEY="$(cat "$REPO/.api_keys/DEEPSEEK_API_KEY")" \
  java -jar "$REPO/mist-cli/target/mist.jar" "$REPO/evaluation/suts/trainticket/trainticket-demo.properties" )
# A fault-detection summary lands in .runtime/logs/fault-detection-reports/.

# 3. teardown
evaluation/suts/trainticket/deploy/deploy.sh teardown
```
TrainTicket's built-in admin account (`admin` / `222222`) is seeded by the SUT — login works on
any fresh deploy (verified: `POST /api/v1/users/login` returns a `ROLE_ADMIN` JWT).

## Notes on reproducibility (lessons baked into deploy.sh)
- **k8s, not compose:** run22 was stood up with `make deploy` (k8s/minikube), and MIST's
  `base.url=http://localhost:32677` is the gateway NodePort that deploy exposes. The repo's root
  `docker-compose.yml` is Mongo-backed while the fault services are MySQL
  (`jdbc:mysql://${AUTH_MYSQL_HOST:ts-auth-mysql}`), so compose never converges — do not use it.
- **Build from source:** `make build` compiles the ~40 services from the injection-branch source,
  so the 7 fault services carry their injected fault code; detection (`faultName` markers) needs
  those built images.
- **Resources:** ~40 JVMs + MySQL is CPU- and RAM-heavy. Give it a 16+-core, >=16GB host; a small
  laptop will thrash and not converge (REPRODUCE.md §6.3). The offline path evidences the claims
  without standing the SUT up.
- **JDK not JRE:** MIST compiles the generated JUnit tests in-process; set `JAVA_HOME` to a JDK 21.
- **Per-SUT isolation:** run MIST from this SUT's own `.runtime/` so caches/logs/generated tests stay
  separate from the other SUTs (the `.properties` input paths still resolve to this bundle).

## Evidence
- `debug/negative_test/runs/run22-fault-detection-10of10.txt` — 15,036 generated tests, **10/10**
  faults detected (experiment `trainticket_twostage_test_42`).
- `docs/main-contribution/evidence/responseenvelope_live_softerror.txt` — the shipped ResponseEnvelope
  + real DeepSeek classifier flips a TrainTicket soft-error body (`HTTP 200, status:0`) to
  `RESPONSE_ENVELOPE: FAIL`, where a status-class oracle passes.
