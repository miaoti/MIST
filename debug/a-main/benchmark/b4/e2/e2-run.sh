#!/bin/bash
# E2 runner of record — the EXACT launch used for the N=5 measured run (2026-07-11).
# Records the -D matrix (reviewer B-MAJOR-2). The g3 harness JVM runs on the WINDOWS side and hits the
# TT gateway via a port-forward that MUST be backgrounded in the SAME shell as `java` (a detached wsl PF
# dies). Read-back gate: DEFAULT timeout (mst.oracle.dataintegrity.timeout.ms NOT overridden = 10000 ms;
# the fault leg's absence was reached at ~20 polls x 500 ms = the 10 s cap, sound because fabricated-ack
# is a by-construction PERMANENT skip and control landed well within the cap on all 5 runs).
set -u
REPO="C:/Users/miaot/Github/MIST"
cd "$REPO"
CP="mist-cli/target/classes;mist-core/target/classes;$(cat cp.txt)"   # cp.txt = mvn dependency:build-classpath
# gateway PF (backgrounded in THIS shell so it lives for the whole loop):
wsl kubectl --context kind-mist -n trainticket port-forward svc/ts-ui-dashboard 8080:8080 >/tmp/pf-run.log 2>&1 &
PFPID=$!; sleep 7
for i in 1 2 3 4 5; do
  java -cp "$CP" \
    -Dg3.base.url=http://localhost:8080 \
    -Dg3.strata=constructed \
    -Dg3.triples.natural=evaluation/suts/trainticket/g3/target-triples-natural.yaml \
    -Dg3.triples.constructed=evaluation/suts/trainticket/g3/target-triples-constructed.yaml \
    -Dg3.contract.path=debug/a-main/g3-comparator-tt/assertion-bindings-cancel-refund.yaml \
    io.mist.cli.g3.TrainTicketStimulus > e2-run$i.log 2>&1
done
kill $PFPID 2>/dev/null
# Deploy pins (recorded for audit): TT fork miaoti/train-ticket-injection@MIST-trainticket, images :1.0.0
# (fork ts-inside-payment-service:1.0.5 runtime drawback toggle); cancel path re-instrumented with
# opentelemetry-javaagent 1.33.6 (OTEL_TRACES_SAMPLER=always_on, exporter jaeger-collector.istio-system:4318)
# on ts-cancel-service / ts-inside-payment-service / ts-order-service. Jaeger query: svc tracing:80 ->
# /jaeger/api/traces/<id>. Traces fetched per-leg by the C1-injected trace-id (see e2-run-stdout.txt).
