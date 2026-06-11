# Online Boutique — full in-process loop run (gRPC, WARN)

`boutique_inprocess_fault-detection-summary.txt` is the report from a single
`java -jar mist.jar boutique-demo.properties` run against the **live** Online Boutique
SUT under an `adservice` outage (`kubectl scale deploy adservice -n boutique --replicas=0`),
commit `5137a1f0`. MIST **generated AND executed 579 tests in one JVM** (in-process
`ToolProvider` JDK compile + `JUnitCore`), and `HiddenDownstreamFailure` fired **7× at WARN**
on the swallowed `frontend ──▶ adservice` gRPC error (`http=200 otel=ERROR`), each tied to a
specific generated negative test (`test_negative_flow_S*_fault_Root1_*`) and a distinct marker
trace id.

**`Total Injected Faults: 0` in the header is expected and correct** — Online Boutique has no
injected-fault registry (that confirmation path exists only for the self-instrumented
TrainTicket). The hidden-downstream signal lives entirely in the **ORACLE ANOMALIES** section.

**WARN does not fail the test** (an `otel`-only error with no HTTP 5xx is surfaced but
non-blocking). Contrast the Bookinfo in-process run (`../bookinfo_inprocess_e2e/`), where an
HTTP-503 swallow fires at **ERROR** and fails the test red.

`sample_hidden_downstream_finding.txt` — one readable Allure 🕳️ finding box from this run.

Trigger is the availability outage, not an elicited input — per the paper's honest scope.
