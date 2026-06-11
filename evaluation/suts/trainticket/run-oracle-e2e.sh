#!/usr/bin/env bash
# =============================================================================
# TrainTicket END-TO-END detection run against a LOCAL fault-injection deploy.
#
# Prereq: deploy/deploy.sh has stood up the SUT (gateway http://localhost:8080,
# built-in admin/222222), and a JDK 21 is available (MIST compiles the generated
# JUnit tests in-process) plus a DeepSeek key (or Ollama) for value synthesis.
#
# It runs MIST end-to-end (generate -> in-process compile -> execute against the
# local SUT -> oracle), then prints the fault-detection summary. A faulty request
# returns HTTP 200 with {"data":{"injected":true,"faultName":...}}; MIST matches
# faultName against injectedFaults/injected-faults.json (10 faults).
#
# Usage:  ./run-oracle-e2e.sh
#         JDK_HOME=/path/to/jdk21 ./run-oracle-e2e.sh
# =============================================================================
set -uo pipefail
SUT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SUT_DIR/../../.." && pwd)"
GW="${GW:-http://localhost:8080}"

# locate a JDK 21 (javac) — MIST's in-process compile needs a JDK, not a JRE
JAVA_HOME="${JDK_HOME:-${JAVA_HOME:-}}"
if [[ -z "${JAVA_HOME}" || ! -x "${JAVA_HOME}/bin/javac" ]]; then
  for c in $(command -v javac 2>/dev/null) /usr/lib/jvm/*/bin/javac \
           /home/*/.antigravity/extensions/redhat.java-*/jre/21.*/bin/javac /home/*/.jdks/*/bin/javac; do
    [[ -x "$c" ]] && "$c" -version 2>&1 | grep -q ' 21\.' && { JAVA_HOME="$(dirname "$(dirname "$c")")"; break; }
  done
fi
[[ -x "${JAVA_HOME}/bin/javac" ]] || { echo "ERROR: need a JDK 21 (set JDK_HOME=/path/to/jdk21)"; exit 1; }
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
echo "JDK: $("$JAVA_HOME/bin/javac" -version 2>&1)"

# the SUT must be reachable (built-in admin login returns 200)
code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 -X POST "$GW/api/v1/users/login" \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"222222"}' 2>/dev/null || true)
[[ "$code" == "200" ]] || { echo "ERROR: SUT not reachable at $GW (login -> $code). Run deploy/deploy.sh first."; exit 1; }
echo "SUT up at $GW (login 200)."

# run MIST from the SUT's own .runtime/ so caches/outputs isolate here
RT="$SUT_DIR/.runtime"; mkdir -p "$RT"
KEY="$(cat "$REPO/.api_keys/DEEPSEEK_API_KEY" 2>/dev/null || echo "${DEEPSEEK_API_KEY:-}")"
echo "=== MIST end-to-end (generate -> compile -> execute -> oracle) ==="
( cd "$RT" && DEEPSEEK_API_KEY="$KEY" \
  java -jar "$REPO/mist-cli/target/mist.jar" "$SUT_DIR/trainticket-demo.properties" )

echo "=== fault-detection summary ==="
LATEST="$(ls -t "$RT"/logs/fault-detection-reports/*.txt 2>/dev/null | head -1)"
[[ -n "$LATEST" ]] && grep -iE "Detected Faults|Total Test Cases|Experiment" "$LATEST" | head || \
  echo "(no fault-detection report under $RT/logs/fault-detection-reports/)"
