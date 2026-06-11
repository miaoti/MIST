# Bookinfo — hidden-downstream phenomenon SUT (MIST evaluation)

Istio's **Bookinfo** is the dedicated **hidden-downstream-failure** SUT: its `reviews` service
swallows a failed `ratings` call and still returns HTTP 200 (its own in-tree graceful degradation),
so a downstream span 5xx-errors **behind a clean 2xx** — exactly what MIST's
`HiddenDownstreamFailureInvariant` targets. Train-ticket cannot produce this (it validates
defensively and fails loudly); Bookinfo produces it naturally, which makes the evidence
**non-circular** (real outage + the SUT's own code, not a mutant we wrote).

> SP1 lists Bookinfo as "too small" for the multi-SUT *external-validity* trio (Sock Shop /
> Online Boutique / TeaStore). Here it is the *phenomenon-demonstration* SUT; breadth comes from the trio.

## Bundle contents (self-contained — this SUT uses only its own input files)
- `deploy/deploy.sh` — one-command stand-up (kind + Istio + Jaeger + Bookinfo); `deploy.sh teardown`.
- `workload/inject-ratings-outage.sh on|off` — toggle the **real ratings outage** (the fault).
- `workload/traffic.sh [N]` — drive nominal traffic through the Istio ingress gateway.
- `openapi/bookinfo-swagger.yaml` — the SUT's OpenAPI spec (MIST input #1).
- `real-system-conf.yaml` — generated from the swagger via `io.mist.cli.MistConfGenMain` (MIST input #2a).
- `bookinfo-demo.properties` + `bookinfo-mst.properties` — MIST profiles, core + mst (MIST input #2b).
- `traces/` — captured Jaeger traces, masked + healthy (MIST input #3); see `MANIFEST.json`.
- `injectedFaults/injected-faults.json` — empty: Bookinfo's failure is an ORACLE ANOMALY
  (`HIDDEN_DOWNSTREAM_FAILURE`), not a SUT-reported named fault.
- `OracleCheck.java` — runs the real `HiddenDownstreamFailure` oracle on a captured trace.
- `.runtime/` — per-SUT run dir (gitignored); all caches/logs land here (see below).

## ⚠ Per-SUT cache/output isolation (run from `.runtime/`)
MIST writes many **cwd-relative** caches/logs: `.mist/llm-*-cache.json`,
`.mist/trace-shape-invariants.json`, `logs/llm-communications`, `logs/fault-detection-reports`,
`allure-results/`, generated tests. To keep each SUT's caches **separate** (no overlap with
train-ticket or other SUTs), **always run MIST from this SUT's own `.runtime/` directory**. The
`.properties` *input* paths resolve against the properties-file dir, so the bundle's
swagger/conf/traces are still found; only the **outputs** follow the cwd. (The per-SUT registries
`root-api-registry.json` / `input-fetch-registry.yaml` are written into this bundle — they ARE
Bookinfo's inputs, regenerated from its traces.)

## Quick start (from the repo root)
```bash
REPO=$(pwd)

# 1. deploy (one command; ~8 min first time)
evaluation/suts/bookinfo/deploy/deploy.sh

# 2. port-forwards (each in its own shell)
kubectl port-forward -n istio-system svc/istio-ingressgateway 8080:80   # SUT    -> http://localhost:8080/productpage
kubectl port-forward -n istio-system svc/tracing 16686:80               # Jaeger -> http://localhost:16686/jaeger/api

# 3. inject the hidden-downstream fault (real ratings outage) + warm traffic
evaluation/suts/bookinfo/workload/inject-ratings-outage.sh on
evaluation/suts/bookinfo/workload/traffic.sh 30

# 4a. quick oracle check on a captured trace
java -cp mist-cli/target/mist.jar evaluation/suts/bookinfo/OracleCheck.java \
  evaluation/suts/bookinfo/traces/masked_ratings_outage_f13dbc33d5858c49da37f039a0243c3a.json "GET /productpage"

# 4b. full MIST run — FROM the SUT's own .runtime/ so caches isolate here:
mkdir -p evaluation/suts/bookinfo/.runtime
( cd evaluation/suts/bookinfo/.runtime && \
  DEEPSEEK_API_KEY="$(cat "$REPO/.api_keys/DEEPSEEK_API_KEY")" \
  java -jar "$REPO/mist-cli/target/mist.jar" "$REPO/evaluation/suts/bookinfo/bookinfo-demo.properties" )

# 5. teardown
evaluation/suts/bookinfo/deploy/deploy.sh teardown
```
(Set `DEEPSEEK_API_KEY` in the env or place it at `.api_keys/DEEPSEEK_API_KEY`.)

## Regenerate the config from the swagger (RESTest-free)
```bash
java -cp mist-cli/target/mist.jar io.mist.cli.MistConfGenMain \
  evaluation/suts/bookinfo/openapi/bookinfo-swagger.yaml \
  evaluation/suts/bookinfo/real-system-conf.yaml
```

## End-to-end pipeline demo (generate → execute → oracle)
After a MIST run has generated the tests under `.runtime/`, run the full matrix against the live SUT:
```bash
evaluation/suts/bookinfo/run-oracle-e2e.sh
```
It compiles the MIST-generated JUnit tests (with a JDK 21 — the oracle/test are MIST's, only the
compile/launch is external), toggles a real `ratings` outage, and runs four cases:
`/products` (outage → oracle silent, PASS), **`/reviews` (outage → HIDDEN_DOWNSTREAM_FAILURE, FAIL)**,
`/ratings` (outage → 503 LOUD, response-level catches), `/reviews` (healthy → silent, PASS control).

## Evidence
- `docs/main-contribution/evidence/bookinfo_hidden_downstream.md` — direct-oracle A/B on `/productpage`:
  the trace oracle FIRES (ERROR) on the masked trace, silent on the healthy control, while a
  response-level oracle sees HTTP 200 (and misses it) in both cases.
- `docs/main-contribution/evidence/bookinfo_e2e_pipeline.md` — **full pipeline**: MIST generates +
  executes a `/api/v1/products/{id}/reviews` test; under a real ratings outage the response is 200
  (response-level PASS) but MIST's trace oracle fails it on `HIDDEN_DOWNSTREAM_FAILURE`. Includes the
  4-case sensitivity/specificity matrix + honest caveats. Evidence traces in `bookinfo_e2e_traces/`.
