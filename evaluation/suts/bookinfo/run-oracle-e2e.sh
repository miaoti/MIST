#!/usr/bin/env bash
# =============================================================================
# Bookinfo END-TO-END oracle demonstration (non-circular hidden-downstream).
#
# Compiles the MIST-GENERATED JUnit tests (produced under .runtime/ by a prior
# `java -jar mist.jar bookinfo-demo.properties` run) and executes them against
# the LIVE Bookinfo SUT, toggling a real `ratings` outage, to show:
#
#   #1 /products        (outage)  -> 200, oracle SILENT  -> PASS  (no false positive)
#   #2 /reviews         (outage)  -> 200, oracle FIRES    -> FAIL  (HIDDEN_DOWNSTREAM_FAILURE)
#   #3 /ratings         (outage)  -> 503, oracle silent   -> FAIL  (response-level catches; LOUD)
#   #4 /reviews         (healthy) -> 200, oracle SILENT   -> PASS  (the #2 fire is genuine)
#
# Why this script (and not `java -jar mist.jar`): MIST's in-JVM compile+execute
# assumes a JDK on PATH (the host here has a JRE) and a project layout at the
# cwd. The GENERATION and the ORACLE are 100% MIST's; this script only supplies
# the compile/launch harness. See docs/main-contribution/evidence/bookinfo_e2e_pipeline.md.
#
# Prereqs: cluster up (deploy/deploy.sh), port-forwards 8080 (ingress) + 16686
# (Jaeger), and a prior MIST run that generated the tests under .runtime/.
# =============================================================================
set -uo pipefail
export PATH="$HOME/.local/bin:$PATH"

SUT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SUT_DIR/../../.." && pwd)"
RT="$SUT_DIR/.runtime"
TMP="${TMPDIR:-/tmp}/bookinfo-e2e.$$"; mkdir -p "$TMP"
JAEGER="${JAEGER_BASE_URL:-http://localhost:16686/jaeger/api}"
GW="${GW:-http://localhost:8080}"

# --- self-healing port-forwards ------------------------------------------------
# A single `kubectl port-forward` dies with "lost connection to pod" well within
# this script's >10-min runtime, which turns every later case into bogus
# connection failures (verified live). If the gateway is unreachable, run our own
# forwards in restart loops and tear them down on exit.
PF_PIDS=()
gw_up() { curl -s -o /dev/null --max-time 3 "$GW/productpage"; }
cleanup_forwards() { for p in "${PF_PIDS[@]:-}"; do pkill -P "$p" 2>/dev/null; kill "$p" 2>/dev/null; done; }
ensure_forwards() {
  gw_up && return 0
  if [ "${#PF_PIDS[@]}" -eq 0 ]; then
    echo "gateway unreachable — starting auto-restarting port-forwards (8080 ingress, 16686 jaeger)"
    ( while true; do kubectl port-forward -n istio-system svc/istio-ingressgateway 8080:80 >/dev/null 2>&1; sleep 2; done ) & PF_PIDS+=("$!")
    ( while true; do kubectl port-forward -n istio-system svc/tracing 16686:80 >/dev/null 2>&1; sleep 2; done ) & PF_PIDS+=("$!")
    trap cleanup_forwards EXIT
  fi
  local i; for i in $(seq 1 30); do gw_up && return 0; sleep 2; done
  echo "ERROR: gateway still unreachable at $GW (is the cluster up?)"; exit 1
}
ensure_forwards

# --- locate a full JDK 21 (needs javac; the host `java` may be a JRE) ----------
JAVAC=""; JAVA=""
for c in "${JDK_HOME:-}/bin/javac" $(command -v javac 2>/dev/null) \
         /home/*/.antigravity/extensions/redhat.java-*/jre/21.*/bin/javac \
         /usr/lib/jvm/*/bin/javac /opt/*/jbr/bin/javac /home/*/.jdks/*/bin/javac; do
  [ -x "$c" ] || continue
  "$c" -version 2>&1 | grep -q ' 21\.' && { JAVAC="$c"; JAVA="$(dirname "$c")/java"; break; }
done
[ -z "$JAVAC" ] && { echo "ERROR: no JDK 21 javac found; set JDK_HOME=/path/to/jdk21"; exit 1; }
echo "JDK: $("$JAVAC" -version 2>&1)"

# --- dependency classpath (junit, rest-assured, allure, hamcrest, json, ...) ---
CPFILE="$TMP/cp.txt"
( cd "$REPO" && mvn -q -pl mist-cli -am dependency:build-classpath \
    -Dmdep.outputFile="$CPFILE" -DskipTests=true >/dev/null 2>&1 )
[ -s "$CPFILE" ] || { echo "ERROR: could not build dependency classpath via Maven"; exit 1; }
CP="$REPO/mist-cli/target/mist.jar:$(cat "$CPFILE"):$RT/mist-cli/target/test-classes"

# --- find the generated test package (latest BookinfoTest_* run dir) -----------
GENROOT="$RT/mist-cli/src/test/java/bookinfo_hidden_downstream"
[ -d "$GENROOT" ] || { echo "ERROR: no generated tests under $GENROOT — run MIST first:"; \
  echo "  ( cd $RT && DEEPSEEK_API_KEY=\$(cat $REPO/.api_keys/DEEPSEEK_API_KEY) \\"; \
  echo "      java -jar $REPO/mist-cli/target/mist.jar $SUT_DIR/bookinfo-demo.properties )"; exit 1; }
# choose the run dir with the MOST generated scenarios (widest endpoint coverage)
PKGDIR="$(for d in "$GENROOT"/BookinfoTest_*; do [ -d "$d" ] && echo "$(ls "$d"/Flow_Scenario_*.java 2>/dev/null | wc -l) $d"; done | sort -rn | head -1 | cut -d' ' -f2-)"
PKG="bookinfo_hidden_downstream.$(basename "$PKGDIR")"
echo "generated package: $PKG"

# pick one POSITIVE scenario per endpoint by its exact emitted Root line.
# Patterns are exact ("…/reviews [expect 200]") so /products does not also match
# /products/{id}/reviews or /products/{id}/ratings.
pick() { local pat="$1"; for f in "$PKGDIR"/Flow_Scenario_*.java; do \
           grep -qF "$pat [expect 200]" "$f" && grep -q 'public void test_positive_' "$f" && { echo "$f"; return; }; done; }
S_PRODUCTS="$(pick 'GET /api/v1/products')"      # bare collection (no trailing path segments)
S_REVIEWS="$( pick '/reviews')"
S_RATINGS="$( pick '/ratings')"
echo "  /products: $(basename "${S_PRODUCTS:-NONE}")   /reviews: $(basename "${S_REVIEWS:-NONE}")   /ratings: $(basename "${S_RATINGS:-NONE}")"
[ -z "$S_REVIEWS" ] && { echo "ERROR: no positive /reviews scenario generated"; exit 1; }

OUT="$RT/mist-cli/target/test-classes"; mkdir -p "$OUT"
echo "=== compiling generated tests ==="
"$JAVAC" -cp "$CP" -d "$OUT" $S_PRODUCTS $S_REVIEWS $S_RATINGS 2>&1 | grep -v 'annotation processing\|future release\|-Xlint\|-proc:\|processor' || true

cls() { echo "$PKG.$(basename "$1" .java)"; }
# Wait until the SUT actually reflects the intended ratings state BEFORE running a
# test — kubectl scale + `wait` races (the pod may not exist yet / not be torn down
# yet), and a stale SUT state would attach the wrong trace.  Poll the live endpoint.
wait_ratings() { # $1 = expected HTTP code on GET /ratings (503 = outage, 200 = healthy)
  local want="$1" i c
  for i in $(seq 1 40); do
    c=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$GW/api/v1/products/0/ratings" 2>/dev/null)
    [ "$c" = "$want" ] && { echo "  ratings endpoint = HTTP $c (settled)"; return 0; }
    sleep 2
  done
  echo "  WARN: ratings endpoint never reached HTTP $want (last=$c)"; return 1
}

FLAGS=( -Djaeger.base.url="$JAEGER" -Djaeger.enabled=true
        -Dmst.test.jaeger.propagation.delay.ms=5000
        -Dmst.oracle.shape.enabled=true
        -Dmst.oracle.shape.invariants.hidden_downstream_failure.enabled=true
        -Dmst.oracle.shape.invariants.span_tree.enabled=false
        -Dmst.oracle.shape.invariants.status_propagation.enabled=false
        -Dmst.oracle.shape.invariants.response_envelope.enabled=false
        -Dmst.oracle.shape.invariants.target_attribution.enabled=false
        -Dllm.response.validation.enabled=false -Dllm.enabled=false -Dauth.mode=none )
runtest() { ensure_forwards; ( cd "$RT" && "$JAVA" -cp "$CP" "${FLAGS[@]}" org.junit.runner.JUnitCore "$1" ) 2>&1 \
  | grep -aiE '✅ Root|❌ Root|HIDDEN_DOWNSTREAM|Scenario (PASSED|FAILED)|OK \(|Tests run|got: [0-9]' | grep -aiv 'Could not'; }

echo "=== outage ON ==="; kubectl scale deploy ratings-v1 --replicas=0 >/dev/null 2>&1
kubectl wait --for=delete pod -l app=ratings --timeout=90s >/dev/null 2>&1 || true
wait_ratings 503   # block until the SUT actually serves 503 on the direct ratings path
[ -n "$S_PRODUCTS" ] && { echo "--- #1 /products (outage) — expect SILENT/PASS ---"; runtest "$(cls "$S_PRODUCTS")"; }
echo "--- #2 /reviews (outage) — expect HIDDEN_DOWNSTREAM_FAILURE/FAIL ---"; runtest "$(cls "$S_REVIEWS")"
[ -n "$S_RATINGS" ] && { echo "--- #3 /ratings (outage) — expect LOUD 503 status mismatch, NO hidden ---"; runtest "$(cls "$S_RATINGS")"; }

echo "=== ratings RESTORED ==="; kubectl scale deploy ratings-v1 --replicas=1 >/dev/null 2>&1
kubectl wait --for=condition=ready pod -l app=ratings --timeout=120s >/dev/null 2>&1 || true
wait_ratings 200   # block until ratings is genuinely healthy again before the control
echo "--- #4 /reviews (healthy) — expect SILENT/PASS (control) ---"; runtest "$(cls "$S_REVIEWS")"
rm -rf "$TMP"
echo "=== done — see docs/main-contribution/evidence/bookinfo_e2e_pipeline.md ==="
