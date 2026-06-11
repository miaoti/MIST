#!/usr/bin/env bash
# =============================================================================
# Offline reproduction of MIST's headline trace-shape oracle result.
#
# Builds mist.jar FROM THE CURRENT COMMIT first -- a stale or pre-built jar can
# predate HiddenDownstreamFailureInvariant and fail with "cannot find symbol".
# Then runs the shipped OracleCheck harness on the committed Bookinfo (HTTP) and
# Online Boutique (gRPC) outage traces. No live SUT, no LLM, no network.
#
# Expected: Bookinfo FIRES at severity=ERROR (swallowed 503) while the
# response-level oracle PASSES; Online Boutique FIRES at severity=WARN
# (swallowed gRPC error, http=200). Both are failures only the trace oracle sees.
#
# Requires JDK 21 (NOT a JRE) -- the harnesses are source-launched.
# Override the JDK with:  JAVA=/path/to/jdk21/bin/java ./evaluation/run-offline-oracle.sh
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."   # repo root

JAVA="${JAVA:-java}"
JAR="mist-cli/target/mist.jar"

echo "==> [1/3] Building mist.jar from this commit (tests skipped)..."
mvn -q -pl mist-cli -am -Dmaven.test.skip=true package

echo
echo "==> [2/3] Bookinfo (HTTP G2): expect HIDDEN_DOWNSTREAM_FAILURE FIRES @ERROR, response-level PASS"
"$JAVA" -cp "$JAR" evaluation/suts/bookinfo/OracleCheck.java \
  docs/main-contribution/evidence/bookinfo_e2e_traces/masked_reviews_ratings_outage.json \
  "GET /api/v1/products/{id}/reviews"

echo
echo "==> [3/3] Online Boutique (gRPC G2): expect FIRES @WARN on the swallowed adservice error"
"$JAVA" -cp "$JAR" evaluation/suts/boutique/OracleCheck.java \
  docs/main-contribution/evidence/boutique_e2e_traces/boutique_adservice_outage.json

echo
echo "==> Done. Both fired offline -- no SUT, no LLM. (Healthy-control traces in the"
echo "    same directories stay silent; see REPRODUCE.md for the full matrix.)"
