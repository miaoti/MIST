# Evidence — END-TO-END pipeline: MIST generates + executes a test whose trace oracle catches a HIDDEN downstream failure (Istio Bookinfo, non-circular)

Date 2026-05-29. SUT: **Istio Bookinfo** on kind + Istio 1.30 + Jaeger (the same deployment
as `bookinfo_hidden_downstream.md`). This upgrades that earlier evidence — which ran the oracle
**directly** on a hand-captured trace — to the **full MIST pipeline**: MIST *generates* the test
from the swagger-derived config + captured traces, *executes* it against the live SUT, fetches the
per-test distributed trace, and its *trace-shape oracle* renders the verdict. Response-level oracles
pass; MIST's oracle fails the test on `HIDDEN_DOWNSTREAM_FAILURE`.

## What makes this the end-to-end claim (vs the direct-oracle evidence)
- **Generation is MIST's.** `MistConfGenMain` turned `openapi/bookinfo-swagger.yaml` into
  `real-system-conf.yaml`; `TraceWorkflowExtractor` + `MistGenerator` (with the SUT-agnostic
  template-aware endpoint fallback — see `debug/generation_generalization/PLAN.md`) built scenarios
  from the captured `/api/v1/...` traces and emitted JUnit tests for every conf operation, including
  **`GET /api/v1/products/{id}/reviews`** — the endpoint that masks.
- **Execution is against the live SUT** under a **real ratings outage** (`ratings` scaled to 0).
- **The verdict is MIST's trace-shape oracle** (`HiddenDownstreamFailureInvariant`), wired into the
  generated test's `attachJaegerTrace(...)` → Phase 2.F (a positive variant FAILS when the verdict
  carries any ERROR-severity violation).
- **Non-circular:** the masking is Bookinfo's *own* in-tree graceful degradation (`reviews` swallows
  a failed `ratings` call and still returns 200); the failure is a real availability outage. We wrote
  no mutant. (Contrast: train-ticket validates defensively + fails loudly → 0 natural instances → only
  an injected mutant could demonstrate it = circular. See `project_phase2_attribution_gap`.)

## Why `/api/v1/products/{id}/reviews` is the right vehicle
The documented masking lives on the `/productpage` BFF, which is not in the swagger. But the
productpage service's **`/api/v1/products/{id}/reviews`** routes through the *same* `reviews → ratings`
path and the *same* catch block — so it masks identically **and it is in the conf**. Empirically, under
the ratings outage:

| endpoint (through the Istio ingress) | client HTTP | masks? |
|---|---|---|
| `GET /api/v1/products` | 200 | no (no ratings dependency) |
| `GET /api/v1/products/{id}` | 200 | no (details only) |
| **`GET /api/v1/products/{id}/reviews`** | **200** | **YES** — body carries `"rating": {"error":"Ratings service is currently unavailable"}`, trace shows `ratings` 503 |
| `GET /api/v1/products/{id}/ratings` | 503 | no — direct ratings call, LOUD |

## Result — the 4-run matrix (MIST-generated test `Flow_Scenario_5`, `GET /api/v1/products/{id}/reviews`, run via JUnit against the live SUT)

| # | endpoint | ratings | client HTTP | response-level oracle | trace oracle `HIDDEN_DOWNSTREAM_FAILURE` | test verdict |
|---|---|---|---|---|---|---|
| 1 | `/api/v1/products` | **outage** | 200 | PASS | **silent** (no dependency) | **PASS** — no false positive |
| 2 | **`/api/v1/products/{id}/reviews`** | **outage** | **200** | **PASS (misses)** | **FIRES — ERROR** | **FAIL** — caught only by the trace oracle |
| 3 | `/api/v1/products/{id}/ratings` | **outage** | 503 | **FAIL (catches)** | **silent** (entry is 503 → LOUD, not hidden) | FAIL — caught by response-level |
| 4 | `/api/v1/products/{id}/reviews` | **healthy** | 200 | PASS | **silent** (no downstream error) | **PASS** — the fire in #2 is genuine, not an artifact |

**Sensitivity** is run #2; **specificity** is runs #1 (healthy endpoint), #3 (the oracle does not
over-claim "hidden" on a failure the caller can already see), and #4 (the same test goes silent the
moment the real dependency is healthy).

### Verbatim — run #2 (masked `/reviews`): the oracle flips a 200 to FAIL
```
▶️ EXECUTING: Root 1: productpage.default GET /api/v1/products/0/reviews [expect 200]
✅ Root 1: productpage.default GET /api/v1/products/0/reviews [expect 200] - SUCCESS   ← response-level sees 200
❌ Root 1: productpage.default GET /api/v1/products/0/reviews [expect 200] - FAILED:
   Positive variant failed — Trace Shape Oracle verdict has violation(s):
   [HIDDEN_DOWNSTREAM_FAILURE: caller received 2xx but 1 downstream span(s) server-errored
    (swallowed): reviews.default/ratings.default.svc.cluster.local:9080/* http=503 otel=ERROR]
Tests run: 1,  Failures: 1
```

### Verbatim — run #3 (`/ratings`, LOUD): response-level catches it, oracle does NOT claim hidden
```
❌ Root 1: ... GET /api/v1/products/0/ratings [expect 200] - FAILED: Expected status code 200, but got: 503
Tests run: 1,  Failures: 1      (plain status mismatch — no HIDDEN_DOWNSTREAM_FAILURE)
```

### Verbatim — run #1 (`/products`) & run #4 (healthy `/reviews`): oracle silent, test PASSES
```
✅ Root 1: ... GET /api/v1/products [expect 200] - SUCCESS
🎉 Scenario PASSED: All 1 steps completed successfully          (run #1, outage, no false positive)

✅ Root 1: ... GET /api/v1/products/0/reviews [expect 200] - SUCCESS
🎉 Scenario PASSED: All 1 steps completed successfully          (run #4, ratings healthy)
```

### Oracle-level confirmation (`OracleCheck` on the captured per-run traces)
```
masked  /reviews (ratings outage): client-facing ROOT productpage .../api/v1/products* http=200;
        downstream ERROR reviews→ratings http=503 otel=ERROR;
        RESPONSE-LEVEL: PASS (misses);  TRACE oracle HIDDEN_DOWNSTREAM_FAILURE: FIRES severity=ERROR
healthy /reviews (control)        : reviews→ratings http=200 (no error);
        RESPONSE-LEVEL: PASS;          TRACE oracle HIDDEN_DOWNSTREAM_FAILURE: pass (silent)
```
Traces: `bookinfo_e2e_traces/masked_reviews_ratings_outage.json`, `.../healthy_reviews_control.json`.

## Reproduction
1. Deploy + outage: `evaluation/suts/bookinfo/deploy/deploy.sh`; `workload/inject-ratings-outage.sh on`.
2. Generate: run MIST on `bookinfo-demo.properties` (produces the JUnit tests under
   `evaluation/suts/bookinfo/.runtime/mist-cli/src/test/java/.../Flow_Scenario_*.java`).
3. Compile + run the matrix: `evaluation/suts/bookinfo/run-oracle-e2e.sh` (builds the dep classpath,
   compiles the generated tests with a JDK 21, runs the four cases toggling the outage, prints verdicts).

## Caveats (honest)
- **Test harness (this 2026-05-29 run). SUPERSEDED 2026-06-02 → see `bookinfo_inprocess_e2e/`.**
  The full in-process generate→compile→execute→oracle loop now runs on the live Bookinfo SUT in
  one JVM and fires at ERROR (commit `87915f42`,
  `bookinfo_inprocess_e2e/bookinfo_inprocess_fault-detection-summary.txt`). At the time of *this*
  run the generated tests were compiled with an external `javac` and executed via
  `org.junit.runner.JUnitCore` because the host exposed only a **JRE** (no in-process compiler) and
  MIST's Maven-compile fallback ran from the per-SUT `.runtime/` cwd which has no `pom.xml`. MIST's
  compile step was subsequently made JDK-aware — it uses the in-process `ToolProvider` compiler,
  falling back to an external `javac` only on a JRE host. The test source and oracle were always
  100% MIST's; only that run's compile/launch was external.
- **Oracle flag.** `mst.oracle.shape.invariants.hidden_downstream_failure.enabled` **defaults to
  `false`** in `MstConfig`; the committed `bookinfo-mst.properties` sets it `true` (a normal MIST run
  enables it from the file). The direct JUnit run set it via `-D`. The other shape invariants
  (span-tree / status-propagation / response-envelope / target-attribution) were disabled for this
  run to isolate the hidden-downstream contribution — they need learned baselines not trained for
  Bookinfo and pass vacuously on the empty store.
- **Trace lookup timing.** A 3 s Jaeger ingest delay (`-Dmst.test.jaeger.propagation.delay.ms=3000`)
  was needed so the marker-first (`traceparent`) exact-ID lookup finds the freshly-ingested trace;
  the default 0 ms (poll 5×200 ms) was too tight here.
- **Outage-driven, not input-driven.** The hidden failure is a real availability outage (`ratings`
  scaled to 0), not elicited by a malicious input. The `/reviews` body also carries an error
  breadcrumb (`"Ratings service is currently unavailable"`), so a body-reading soft-error oracle
  *could* also flag it; LLM soft-error validation was disabled here precisely to isolate the
  **structural, body-independent** trace oracle, which catches the swallowed 5xx span regardless of
  what the body says. Input-driven masking is a separate, harder-to-elicit case.
- **One endpoint / one SUT** for the phenomenon; multi-SUT external-validity breadth remains per SP1
  (Sock Shop / Online Boutique / TeaStore).
