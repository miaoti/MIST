# YouTube description for the screencast (SANER 2027 version, 2026-10-13)

Replaces the description written for the ISSTA 2026 submission. The video itself
(https://youtu.be/T2AePcbFT6E, recorded June 2026) is unchanged: it shows the Bookinfo
`/reviews` call under the ratings outage, the MIST run, the Allure report and the offline
TrainTicket generation. The text below claims only what the video shows and what the SANER
paper says.

---

MIST (Microservice Integration and Scenario Tester) is an open-source tool that turns the
OpenAPI document and the OpenTelemetry/Jaeger traces a microservice system already exports into
runnable cross-service REST API tests, and checks every test run against the trace it produced.

This screencast runs MIST on Istio Bookinfo under a ratings outage and walks through the Allure
report. The HiddenDownstreamFailure invariant of its Trace Shape Oracle, which needs no labelled
traces, no learned baseline and no language model, flags a call that failed with HTTP 503 deeper
in the system while the client-facing response returned 200. The status oracle passes that
response, and only the trace shows the failed call. The screencast also generates the
TrainTicket tests offline and shows the model-backed soft-error check. MIST ships with input
bundles for TrainTicket, Istio Bookinfo, Sock Shop and Online Boutique.

Companion to the paper "MIST: A Trace-Driven Tool for Multi-Service REST API Test Generation
with Trace Shape Oracles", submitted to the SANER 2027 Tool Demo Track.

Source: https://github.com/miaoti/MIST/
