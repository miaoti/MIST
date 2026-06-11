#!/usr/bin/env bash
# =============================================================================
# Re-capture the Online Boutique adservice-outage hidden-downstream evidence and
# VERIFY MIST's oracle on it, as a healthy-vs-outage sensitivity/specificity check.
#
# Lesson baked in (learned the hard way):
#  - The DEEP, adservice-bearing frontend traces come from Online Boutique's built-in
#    LOADGENERATOR (realistic user journeys), NOT from a plain `curl /` (which yields a
#    shallow 1-2 span trace with no downstream fan-out). So this script KEEPS the
#    loadgenerator RUNNING and just contrasts a healthy window (adservice up) with an
#    outage window (adservice scaled to 0).
#  - A freshly (re)started Jaeger needs ~60s of warm traffic before traces are deep;
#    capturing too early yields shallow traces and false 0-fire results.
#
# The "X of N" count is inherently a function of how many captured journeys traverse
# adservice, so it is NOT a stable headline number. The robust signal this prints is:
#   OUTAGE  -> every frontend trace that routed through the failed adservice is flagged
#              (root HTTP 200 + a swallowed otel=ERROR span); a response-level oracle passes.
#   HEALTHY -> zero traces flagged (specificity / no false positives).
#
# Needs cluster perms (kubectl scale + port-forward) and a JDK 21 for OracleCheck.
# Run:  bash evaluation/suts/boutique/workload/capture-traces-controlled.sh
# =============================================================================
set -uo pipefail
NS=boutique
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
SUT="$REPO/evaluation/suts/boutique"; TR="$SUT/traces"; mkdir -p "$TR"
JH="${JAVA_HOME:-/home/tingshuo_miao2/.antigravity/extensions/redhat.java-1.54.0-linux-x64/jre/21.0.10-linux-x86_64}"
JAEGER=http://localhost:16686/jaeger/api
WARM="${WARM:-90}"                              # outage warm-up seconds before pulling

cleanup(){ echo "== cleanup: restore adservice, kill port-forward =="
  kubectl scale deploy adservice -n $NS --replicas=1 >/dev/null 2>&1 || true
  [[ -n "${PF:-}" ]] && kill "$PF" 2>/dev/null || true; }
trap cleanup EXIT

echo "== ensure Jaeger ready (restart only if crashlooping) =="
if ! kubectl -n istio-system get pod -l app=jaeger \
        -o jsonpath='{.items[0].status.containerStatuses[0].ready}' 2>/dev/null | grep -q true; then
  echo "   jaeger not ready -> restarting (fresh emptyDir badger) + warming"
  kubectl -n istio-system delete pod -l app=jaeger --wait=false 2>/dev/null || true
  kubectl -n istio-system rollout status deploy/jaeger --timeout=120s || { echo "ERROR: jaeger not ready"; exit 1; }
  sleep 60                                      # sidecars reconnect + loadgenerator warms the pipeline
fi

echo "== port-forward jaeger:16686 =="
kubectl -n istio-system port-forward deploy/jaeger 16686:16686 >/tmp/pf_jg_recap.log 2>&1 & PF=$!; sleep 6
curl -sf -o /dev/null --max-time 8 "$JAEGER/services" || { echo "ERROR: jaeger api :16686 unreachable"; exit 1; }

echo "== HEALTHY window (adservice up; loadgenerator drives deep journeys) =="
kubectl rollout status deploy/adservice -n $NS --timeout=60s
curl -s --max-time 25 "$JAEGER/traces?service=frontend.boutique&limit=30&lookback=4m" -o "$TR/boutique_recap_healthy.json"

echo "== OUTAGE window: adservice -> 0 =="
kubectl scale deploy adservice -n $NS --replicas=0 2>&1 | head -1
sleep 8
EP=$(kubectl get endpoints adservice -n $NS -o jsonpath='{.subsets[*].addresses[*].ip}' 2>/dev/null)
[[ -n "$EP" ]] && { echo "ABORT: adservice still has endpoints ($EP) — scale not applied"; exit 2; }
echo "   adservice endpoints empty; warming outage traffic ${WARM}s..."
sleep "$WARM"
curl -s --max-time 25 "$JAEGER/traces?service=frontend.boutique&limit=40&lookback=$((WARM+5))s" -o "$TR/boutique_recap_outage.json"

echo "== restore adservice =="
kubectl scale deploy adservice -n $NS --replicas=1 >/dev/null 2>&1; kill $PF 2>/dev/null; PF=""

echo ""
echo "== MIST HiddenDownstreamFailure oracle + swallowed-error metric =="
[[ -x "$JH/bin/java" ]] || { echo "ERROR: set JAVA_HOME to a JDK 21 (OracleCheck is source-launched)"; exit 1; }
for set in healthy outage; do
  F="$TR/boutique_recap_$set.json"
  FIRE=$("$JH/bin/java" -cp "$REPO/mist-cli/target/mist.jar" "$SUT/OracleCheck.java" "$F" "GET /" 2>/dev/null | grep -c "HIDDEN_DOWNSTREAM_FAILURE: FIRES")
  python3 - "$F" "$set" "$FIRE" <<'PY'
import json,sys
d=json.load(open(sys.argv[1])); ts=d.get("data",[]); setn=sys.argv[2].upper(); fire=int(sys.argv[3])
swh=0  # traces with a swallowed otel=ERROR under a 2xx client-facing (frontend) root
for t in ts:
    byid={s["spanID"]:s for s in t.get("spans",[])}
    root=None
    for s in t.get("spans",[]):
        if not any(r.get("refType")=="CHILD_OF" and r.get("spanID") in byid for r in s.get("references",[])): root=s; break
    rt={x["key"]:str(x.get("value")) for x in (root or {}).get("tags",[])}
    root200=rt.get("http.status_code",rt.get("http.response.status_code","")).startswith("2")
    err=any(({x["key"]:str(x.get("value")) for x in s.get("tags",[])}).get("otel.status_code","").upper()=="ERROR"
            for s in t.get("spans",[]))
    if root200 and err: swh+=1
depth=sorted(len(t.get("spans",[])) for t in ts)
print(f"  {setn:8}: {len(ts):3} traces, depth {depth[0]}/{depth[len(depth)//2]}/{depth[-1]}, root-200+swallowed-error={swh:3}, ORACLE FIRES={fire}")
PY
done
echo ""
echo "Expected: HEALTHY fires 0 (specificity); OUTAGE fires on the adservice-routing"
echo "journeys (sensitivity), all root-200 -> a response-level oracle passes on them."
