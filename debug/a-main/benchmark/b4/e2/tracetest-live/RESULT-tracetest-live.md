# RESULT — Tracetest LIVE (verification-round follow-up) — ADVANCED-BUT-BLOCKED (build-vs-jaeger)

**Date:** 2026-07-18 · The R5 meta-review's highest-ROI remaining item (≥1 LIVE competitor
head-to-head) was ATTEMPTED to completion. Outcome: materially past the prior L4 blocker, blocked
at a new, precisely-isolated point. Artifacts: `docker-compose.yml`, `tracetest_run_leg.py`,
`run-control-adminroute-*.json` (the live trigger records), `tracetest-specs-authored.yaml`
(the authored specs, unchanged).

## What advanced past L4 (`RESULT-pws-l4-tracetest.md` = install-blocked on the dead Bitnami postgres tag)
- **Server RUNS.** Replaced the helm chart with a 2-service compose (official `postgres:14-alpine`
  + `kubeshop/tracetest:sha-af157ca1-amd64`, published port 11633, `host.docker.internal` host-gateway).
  API healthy (`/api/tests` 200), provisioning success — the postgres blocker is GONE.
- **Tests CREATE + TRIGGER LIVE.** The 3 real adminroute tests (presence / acked-2xx / naive-no-5xx)
  were created via the REST API and RUN; each drove a genuine live POST to
  `/api/v1/adminrouteservice/adminroute` on the revived TT (run state EXECUTING → AWAITING_TRACE;
  `resolvedTrigger` shows the real request; `serviceTriggerCompletedAt` set) — the tool reached
  and stimulated the SUT.

## The isolated blocker (evidence-backed, reproduced on TWO image versions)
Every run hangs in `AWAITING_TRACE` forever: Tracetest's jaeger **gRPC tracedb worker PANICS**
(`Worker exits from a panic: runtime error: invalid memory address or nil pointer dereference`,
`agent/tracedb/jaegerdb.go:68 TestConnection`) while pulling the trace from the cluster's jaeger —
even though the container REACHES the endpoint (in-container `nc -z host.docker.internal 16685` =
TCP-OK). Reproduced identically on `sha-af157ca1` (Nov 2024) AND `sha-3df9fae2` (Oct 2024) ⇒ the
fault is the **cluster's jaeger side**: the istio all-in-one jaeger's query-service gRPC proto is
older than what the tracetest jaeger client expects. Fixing it = redeploying the cluster jaeger,
which would DESTROY every other SUT's captured corpus traces (the shared datastore) — an
unacceptable cost for upgrading 5 presence surrogate cells, and squarely the >½-day fight the L4
stop rule pre-registers.

## Disposition (honest, within the reviewed plan)
Tracetest-live closes **ADVANCED-BUT-BLOCKED** — a strictly stronger disclosed result than L4's
install-blocked: the server runs and triggers the SUT live; the ONLY gap is trace INGESTION from
an incompatible pre-existing jaeger. The E2 Tracetest cells stay the labeled **SURROGATE** cells
(unchanged, not spun into live results).

## Why the paper's real-tool story does NOT depend on this
The LIVE real-tool comparison is ALREADY carried, independently and by execution:
- **`RESULT-realoracle.md`** — Schemathesis v4.23.0's FULL oracle suite EXECUTED on the recorded
  acked-but-lost 200 acks: MEASURED MISS, leg-invariant (surrogate==real closed by execution).
- **`RESULT-pws-l1-evomaster.md`** — EvoMaster + Schemathesis both run live to completion, both
  miss the masked class for two complementary fundamental reasons.
Tracetest-live's marginal value was upgrading 5 flagship *presence* surrogate cells to live — it
does not move the contribution, and its surrogate cells were always labeled as such.

## Teardown
Tracetest compose `down -v` (volumes removed). TT stays up for the F8 re-capture (this same
window); torn down at window close.
