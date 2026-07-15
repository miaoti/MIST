#!/usr/bin/env bash
# TT-OMNIBUS leg 3 — ONE E5 repetition (all 4 OAT configs), run as a single ATTACHED call
# from the Windows Git Bash tool (the proven leg-1/e2 shape: PFs backgrounded in THIS
# shell). Usage: bash e5-rep.sh <rep>   (fork 1.0.5 must already be set-image'd + rolled.)
# Matrix (protocol (d)): C0 paired/default · C3 paired/timeout20000 · C1 observe+jaeger
# (control,fault) · C2 observe−jaeger (control,fault). ~6 min/rep.
set -u
REP="${1:?rep number}"
cd "C:/Users/miaot/Github/MIST"
CP="mist-cli/target/classes;mist-core/target/classes;$(cat cp.txt)"
LOG=debug/a-main/benchmark/b4/ttomni/leg3
mkdir -p "$LOG"

JPOD=$(wsl kubectl --context kind-mist -n istio-system get pods -l app=jaeger -o name | head -1 | tr -d '\r')
wsl kubectl --context kind-mist -n trainticket port-forward svc/ts-ui-dashboard 8080:8080 >/tmp/pf-gw.log 2>&1 &
PF1=$!
wsl kubectl --context kind-mist -n istio-system port-forward "$JPOD" 16686:16686 >/tmp/pf-jg.log 2>&1 &
PF2=$!
sleep 8

PAIRED_ARGS=(-Dg3.base.url=http://localhost:8080 -Dg3.strata=constructed
  -Dg3.triples.natural=evaluation/suts/trainticket/g3/target-triples-natural.yaml
  -Dg3.triples.constructed=evaluation/suts/trainticket/g3/target-triples-constructed.yaml
  -Dg3.contract.path=debug/a-main/g3-comparator-tt/assertion-bindings-cancel-refund.yaml)

echo "[e5] rep $REP C0 paired/default"
java -cp "$CP" "${PAIRED_ARGS[@]}" io.mist.cli.g3.TrainTicketStimulus > "$LOG/C0-rep$REP.log" 2>&1
echo "[e5] C0 rc=$?"

echo "[e5] rep $REP C3 paired/timeout20000"
java -cp "$CP" "${PAIRED_ARGS[@]}" -Dmst.oracle.dataintegrity.timeout.ms=20000 \
  io.mist.cli.g3.TrainTicketStimulus > "$LOG/C3-rep$REP.log" 2>&1
echo "[e5] C3 rc=$?"

for LEG in control fault; do
  echo "[e5] rep $REP C1 observe+jaeger leg=$LEG"
  java -cp "$CP" -Dg3.base.url=http://localhost:8080 \
    -Dg3.triples.constructed=evaluation/suts/trainticket/g3/target-triples-constructed.yaml \
    -Djaeger.base.url=http://localhost:16686/jaeger/api \
    -Dttomni.leg=$LEG -Dttomni.n=1 \
    io.mist.cli.g3.TtOmniObserveLeg > "$LOG/C1-$LEG-rep$REP.log" 2>&1
  echo "[e5] C1-$LEG rc=$?"
done

for LEG in control fault; do
  echo "[e5] rep $REP C2 observe-nojaeger leg=$LEG"
  java -cp "$CP" -Dg3.base.url=http://localhost:8080 \
    -Dg3.triples.constructed=evaluation/suts/trainticket/g3/target-triples-constructed.yaml \
    -Dttomni.leg=$LEG -Dttomni.n=1 \
    io.mist.cli.g3.TtOmniObserveLeg > "$LOG/C2-$LEG-rep$REP.log" 2>&1
  echo "[e5] C2-$LEG rc=$?"
done

kill $PF1 $PF2 2>/dev/null
grep -h "MIST B2\|gate=" "$LOG"/C*-rep$REP.log 2>/dev/null | grep -oE "MIST B2.*|gate=[A-Z_]+" | sort | uniq -c
echo "[e5] rep $REP done"
