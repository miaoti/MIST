# Istio Bookinfo — full in-process loop run (HTTP, ERROR → RED test)

`bookinfo_inprocess_fault-detection-summary.txt` is the report from a single
`java -jar mist.jar bookinfo-demo.properties` run against the **live** Istio Bookinfo
SUT under a `ratings` outage (`kubectl scale deploy ratings-v1 -n default --replicas=0`),
commit `87915f42`. MIST **generated AND executed 166 tests in one JVM** (in-process
`ToolProvider` JDK compile + `JUnitCore`), and `HiddenDownstreamFailure` fired at **ERROR** on
`GET /api/v1/products/0/reviews` — the swallowed `reviews ──▶ ratings` `503` (`http=503 otel=ERROR`,
138 hits) — tied to generated test `test_negative_flow_S50_v21_fault_Root1_TYPE_MISMATCH` and
marker trace `d3a5fa5e…`.

> **Hit-count caveat (2026-06-10).** The binary that produced this report double-recorded a step's
> verdict when the violation failed a positive variant (success path + catch path, same marker), so
> the raw `138 hits` figure is inflated by up to 2x. The finding itself is unaffected: 1 distinct
> anomaly, the swallowed `reviews ──▶ ratings` 503, severity ERROR, red test. Fixed in
> `FaultDetectionTracker` on 2026-06-10 (per-execution idempotent recording).

**An HTTP-5xx swallow fires at ERROR, which fails the test red** (the caller waited on the call
and masked a synchronous 5xx). This is the stronger HTTP counterpart to the Online Boutique
gRPC/WARN case (`../boutique_inprocess_e2e/`).

**`Total Injected Faults: 0` in the header is expected and correct** — Bookinfo has no
injected-fault registry; the hidden-downstream signal is the **ORACLE ANOMALIES** entry.

`sample_hidden_downstream_finding.txt` — the readable Allure 🕳️ finding box (severity ERROR).

Trigger is the availability outage, not an elicited input — per the paper's honest scope.
