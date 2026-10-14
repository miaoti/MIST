# Istio Bookinfo: MIST re-run of 2026-10-13 (the run behind Fig. 3 of the SANER tool-demo paper)

## Purpose

Produce an Allure report of a live MIST run for the screenshot in
`paper/tool-demo/figures/allure_finding.png` and re-confirm, on the current build, that
`HiddenDownstreamFailure` fires on the swallowed `reviews -> ratings` 503.

## Setup

- MIST `v1.6.0-SNAPSHOT`, built from the working tree at commit `057454ca` (2026-10-13 18:27 CDT)
  and run from the git-ignored working directory `evaluation/suts/bookinfo/.runtime/` with
  `evaluation/suts/bookinfo/bookinfo-demo.properties`. The properties file does not set the
  invariant switch; `mst.oracle.shape.invariants.hidden_downstream_failure.enabled=true` was
  passed as a JVM system property for this run (the finding below proves it was on; the log's
  ablation line does not print it).
- Cluster and outage: the kind node `mist-control-plane`, Istio 1.30.0, Bookinfo from the
  release-1.30 sample manifests with `reviews` pinned to v3,
  `kubectl scale deploy ratings-v1 -n default --replicas=0`. The ingress gateway was
  port-forwarded to `http://localhost:8080` and the Jaeger query service to
  `http://localhost:16686`.
- Log facts (`mist-run-20261013.log`): ablation profile `oracle:on (span,status,response |
  timing:off) bandit:on faultmining:off`; seed-corpus training skipped (no corpus for Bookinfo);
  SUT preflight 6/7 endpoints reachable, `GET /api/v1/products/0/ratings` answering 503 (the
  outage); smart input fetching disabled; the language-model provider fell back from
  `openai_compatible` to OLLAMA because the configured key was not resolved.

## Result

`fault-detection-summary-bookinfo_hidden_downstream_1791939061994-20261013-195316.txt`
(experiment `bookinfo_hidden_downstream_1791939061994`): 108 generated tests executed between
19:51:01 and 19:53:16 CDT; 5 distinct oracle anomalies, 19 occurrences, all
`HIDDEN_DOWNSTREAM_FAILURE` at severity ERROR:

1. `GET /api/v1/products/0/reviews`: `reviews.default -> ratings.default.svc.cluster.local:9080/*
   (http=503 otel=ERROR)`, 13 hits, first seen 19:51:19, example trace
   `9bb539d397e8409798c98985d1be7588`. This is the finding the paper reports.
2. to 5. `GET /api/v1/products/0`: four anomalies, 6 hits in all, that name ratings and
   gateway 503 spans which a request to `/products/0` (it calls only `details`) cannot
   produce. The likely cause is the generated tests' time-window fallback when they look up
   a step's trace, which can pick up a neighbouring request's spans (an inference from the
   records, not verified in the code). The paper reports nothing from these four anomalies.

`Total Injected Faults: 0` in the summary header is expected: Bookinfo has no injected-fault
registry, and the hidden-downstream signal is the ORACLE ANOMALIES section.

The six `finding_reviews_*-attachment.txt` files are the finding attachment of six tests on
`/reviews`, copied from `target/allure-results/`.

## The figure

The Allure report was rendered with Allure command line 2.46.1 from `target/allure-results/`
(`allure generate`, then served locally). The screenshot shows the test
`bookinfo_hidden_downstream.BookinfoTest_1791939061994.Flow_Scenario_60.test_positive_flow_S60_v28`
(Allure uid `83bf2b81fee12a81`): expected 200, got 200 in 27 ms, 266 ms in all. Its
`Trace Shape Oracle Verdict` attachment reads `SPAN_TREE_SHAPE` passed, `STATUS_PROPAGATION`
passed, `RESPONSE_ENVELOPE` passed, `HIDDEN_DOWNSTREAM_FAILURE` failed with
`reviews.default -> ratings.default.svc.cluster.local:9080/* (http=503 otel=ERROR)`. The
figure crops the test body's step rows: the entry step, the shape-violation sub-step that
names the swallowed call, and the `Response (200)` attachment row (Allure page at browser zoom
2.9, right panel widened, so that the text prints at about 7 pt in a column). `allure_hdf_attachment_modal.png` is the finding attachment opened in
Allure, kept here as the record of its full text.

## Caveats recorded here and not in the paper

1. The attachment's line `(looks fine - status, schema, and body are all clean)` is a fixed
   string in `MultiServiceRESTAssuredWriter`, not a measured verdict. On this body a schema
   validator does flag the missing rating properties (see `../bookinfo_baselines_20261013/`).
   The figure therefore crops the step rows, not the attachment.
2. The `INTELLIGENT ANALYSIS (Based on Trace)` attachment is model-written (the OLLAMA
   fallback) and wrong for this trace: it reports HTTP status 0 and a TCP failure while the
   trace carries a 503. The oracle verdict does not use it and the paper does not show it.
3. Allure's parameter list gives `Status Code Mismatch, status: 200` as the failure reason of a
   shape violation (the writer's generic label), and the shape-violation sub-step carries a
   passed icon under a broken step.
