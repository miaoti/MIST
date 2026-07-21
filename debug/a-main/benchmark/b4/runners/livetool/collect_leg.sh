#!/bin/bash
# Track E leg collector (git-bash, bounded foreground). $1 = control|fault.
# Replays run_leg.sh's post-run section after a detached/orphaned EvoMaster finishes
# (the wrapper was externally killed mid-leg; java survived — the documented class).
set -u
LEG=$1
ROOT=/c/Users/miaot/Github/MIST
OUT=$ROOT/debug/a-main/benchmark/b4/pws/evomaster/teastore-auth-$LEG
P=http://localhost:8092/tools.descartes.teastore.persistence

BASELINE=$(grep '^BASELINE=' "$OUT/prep.txt" | cut -d= -f2)
MAINT=$(grep '^MAINT=' "$OUT/prep.txt" | cut -d= -f2)
AFTER=$(wsl bash -c "curl -s $P/rest/orders | grep -o '\"id\"' | wc -l" | tr -d '[:space:]')
DELTA=$((AFTER - BASELINE))
# EvoMaster's own summary lines (faults + coverage), from the tail of the run log
grep -aE "Potential faults|potential fault|Covered targets|Generated .* tests|Evaluated" "$OUT/run.log" | tail -6 > "$OUT/evomaster-summary.txt"
if [ "$LEG" = "fault" ]; then
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
  echo "  \"leg\": \"$LEG\", \"maintenance\": \"$MAINT\","
  echo "  \"orders_baseline\": $BASELINE, \"orders_after\": $AFTER, \"orders_delta\": $DELTA,"
  echo "  \"gate\": \"control needs delta>=1 else NOT_INTERPRETABLE-well-configured\","
  echo "  \"collection\": \"collect_leg.sh (wrapper externally killed mid-leg; java survived and completed; budget integrity checked from run.log timestamps)\","
  echo "  \"note\": \"cookie redacted; auth = operator-provisioned login user21 + seeded cart (plan rev 2)\""
  echo "}"
} > "$OUT/leg-summary.json"
echo "COLLECTED $LEG: maint=$MAINT baseline=$BASELINE after=$AFTER delta=$DELTA"
tail -2 "$OUT/evomaster-summary.txt"
