#!/bin/bash
# Track E leg runner (git-bash, run as a background job). $1 = control|fault.
# Prep (WSL) -> EvoMaster v6.1.1 black-box 60m on the FULL E1 spec via the webui PF, with
# operator-provisioned auth (header0 cookie) + seeded cart -> post-run measurements.
# Plan of record: b4/PLAN-live-tool-h2h.md rev 2 (3-cold CONFIRM).
set -u
LEG=$1
ROOT=/c/Users/miaot/Github/MIST
OUT=$ROOT/debug/a-main/benchmark/b4/pws/evomaster/teastore-auth-$LEG
mkdir -p "$OUT"
JAR=$ROOT/tools/evomaster/evomaster-v6.1.1.jar
SPEC="file:///C:/Users/miaot/Github/MIST/evaluation/suts/teastore/openapi/teastore-swagger.yaml"
P=http://localhost:8092/tools.descartes.teastore.persistence

# ---- prep (WSL side; scripts already copied to /tmp, CRLF-stripped) ----
PREP=$(MSYS_NO_PATHCONV=1 wsl bash /tmp/livetool_leg_prep.sh "$LEG") || { echo "$PREP" | tee "$OUT/ABORT.txt"; exit 2; }
echo "$PREP" > "$OUT/prep.txt"
COOKIE=$(echo "$PREP" | grep '^COOKIE=' | cut -d= -f2-)
BASELINE=$(echo "$PREP" | grep '^BASELINE=' | cut -d= -f2)
MAINT=$(echo "$PREP" | grep '^MAINT=' | cut -d= -f2)
echo "leg=$LEG maint=$MAINT baseline=$BASELINE cookie=(captured, redacted in artifacts)"

# ---- EvoMaster (60 min budget, pre-registered; seed 42; full E1 spec; webui target) ----
START=$(date +%s)
java -jar "$JAR" --blackBox true \
  --bbSwaggerUrl "$SPEC" \
  --bbTargetUrl "http://localhost:8091" \
  --maxTime 60m --seed 42 --writeStatistics true \
  --header0 "Cookie: $COOKIE" \
  --outputFolder "$OUT/tests" --outputFilePrefix "EM_TS_$LEG" \
  > "$OUT/run.log" 2>&1
RC=$?
END=$(date +%s)

# ---- post-run measurements ----
AFTER=$(wsl bash -c "curl -s $P/rest/orders | grep -o '\"id\"' | wc -l" | tr -d '[:space:]')
DELTA=$((AFTER - BASELINE))
FAULTS=$(grep -aE "potential fault|Potential fault|faults|WARN.*fault" "$OUT/run.log" | tail -5)
if [ "$LEG" = "fault" ]; then
  # A5 per-endpoint status census under maintenance (webui via 8091, persistence via 8092)
  {
  echo "# per-endpoint status census under maintenance (A5)"
  for p in "/tools.descartes.teastore.webui/login GET 8091" \
           "/tools.descartes.teastore.webui/profile GET 8091" \
           "/tools.descartes.teastore.persistence/rest/orders GET 8092" \
           "/tools.descartes.teastore.persistence/rest/products GET 8092"; do
    set -- $p
    st=$(wsl bash -c "curl -s -o /dev/null -w '%{http_code}' http://localhost:$3$1")
    echo "  $2 $1 -> $st"
  done
  } > "$OUT/census.txt"
fi
{
  echo "{"
  echo "  \"leg\": \"$LEG\", \"maintenance\": \"$MAINT\", \"budget_s\": $((END-START)), \"evomaster_rc\": $RC,"
  echo "  \"orders_baseline\": $BASELINE, \"orders_after\": $AFTER, \"orders_delta\": $DELTA,"
  echo "  \"gate\": \"control needs delta>=1 else NOT_INTERPRETABLE-well-configured\","
  echo "  \"note\": \"cookie redacted; auth = operator-provisioned login user21 + seeded cart (plan rev 2)\""
  echo "}"
} > "$OUT/leg-summary.json"
echo "LEG $LEG DONE: rc=$RC delta=$DELTA (baseline $BASELINE -> $AFTER); artifacts in $OUT"
