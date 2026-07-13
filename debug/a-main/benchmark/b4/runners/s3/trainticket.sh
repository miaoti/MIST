#!/bin/bash
# S3 wild-hunt — TrainTicket runner (plan rev 2.1 §6 P3, MANDATORY: the only sync-acked + nonzero-
# prior surface). Binds the 3 admin-basic write endpoints (station/config/price creates) as SUPPLIED
# MEMBERSHIP triples, admin-authed. usage: bash trainticket.sh <revive|calibration|window>
#   revive       scale the MEASUREMENT subgraph 0->1 (assumes infra+nacos already bootstrapped per the
#                runbook debug/a-main/c2c3/step2-execution-checklist.md §2.6 — nacos quorum + doubleWrite
#                PUT are a one-time manual bootstrap, NOT re-done here)
#   calibration  20 benign writes, §3 FP gate (expect present=20, 0 error, CONFIRMED=0)
#   window       500 acked writes across station/config/price (RUN THIS CALL IN BACKGROUND — paced,
#                ~11 min; the gateway rate-limiter forces the pacing, see s3.journey.delay.ms below)
# Pacing: TT's Spring Cloud Gateway + Sentinel rate-limits bursts (429s corrupt read-backs). The runner
# paces journeys (s3.journey.delay.ms, default 800 in TrainTicketWildHunt) to stay under it — this is
# workload pacing ONLY, not an observation-cadence knob (poll/re-probe unchanged), so cross-strata
# cadence uniformity holds (plan §4.2). NEVER touches the SUT's own rate-limit config (frozen defaults).
set -u
REPO="C:/Users/miaot/Github/MIST"; cd "$REPO"
NS=trainticket; KC="wsl kubectl --context kind-mist"
MODE="${1:-calibration}"
# Minimal subgraph the admin-basic write+read-back path needs (ui->gateway->admin-basic->{station,
# config,price}; auth+user for the admin bearer). Downstream station/config/price services receive the
# proxied create (AdminBasicInfoServiceImpl blocking RestTemplate.exchange).
SUBGRAPH="ts-ui-dashboard ts-gateway-service ts-auth-service ts-user-service \
ts-admin-basic-info-service ts-station-service ts-config-service ts-price-service"

if [ "$MODE" = "revive" ]; then
  echo "NOTE: infra (nacos quorum + doubleWriteEnabled=false + mysql/rabbitmq) must already be"
  echo "      bootstrapped per debug/a-main/c2c3/step2-execution-checklist.md §2.6 (one-time manual)."
  for d in $SUBGRAPH; do $KC -n $NS scale deploy/$d --replicas=1; done
  for i in $(seq 1 30); do sleep 10
    R=0; for d in $SUBGRAPH; do
      R=$((R + $($KC -n $NS get deploy/$d --no-headers 2>/dev/null | awk '{split($2,a,"/"); print (a[1]==a[2]&&a[2]!="0")?1:0}') ))
    done
    T=$(echo $SUBGRAPH | wc -w)
    echo "t=${i}0s ready=$R/$T"; [ "$R" -ge "$T" ] && { echo ALL_READY; break; }
  done
  exit 0
fi

$KC -n $NS port-forward svc/ts-ui-dashboard 8080:8080 >/tmp/pf-tt.log 2>&1 &
PF=$!
trap "kill $PF 2>/dev/null" EXIT
sleep 8
HEAD=$(git rev-parse --short HEAD)
CP="mist-cli/target/classes;mist-core/target/classes;$(cat cp.txt)"
# journey count: 20 (calibration) / 500 (window) default; optional $2 override to add margin so
# operational skips still leave >=500 ACKED writes (the denominator counts acked, not attempted).
N=$([ "$MODE" = window ] && echo 500 || echo 20)
N="${2:-$N}"
java -cp "$CP" \
  -Dg3.base.url=http://localhost:8080 -Ds3.admin.user=admin -Ds3.admin.pass=222222 \
  -Ds3.mode=$MODE -Ds3.journeys=$N \
  -Ds3.out=debug/a-main/benchmark/b4/s3/${MODE}-trainticket \
  -Ds3.triples=debug/a-main/benchmark/b4/s3/trainticket-adminbasic-triples.yaml \
  -Ds3.mist.commit="$HEAD" -Ds3.envguard="admin-auth-ok;subgraph-healthy;no-fault-injected;sync-SUT" \
  io.mist.cli.s3.TrainTicketWildHunt \
  2>&1 | grep -viE "StatusConsoleListener|Unable to rename|I/O exception|Retrying request|DefaultHttpClient"
