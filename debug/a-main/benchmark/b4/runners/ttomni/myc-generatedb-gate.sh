#!/usr/bin/env bash
# MYC generatedb grep-gate (plan rev 2 §3-0d): 0 hits required across the ACTUAL TeaStore
# PIPELINE INPUTS (oas.path + conf.path + trace.file.path + the properties profile) before
# any TeaStore seed runs. Scope note: the harness-era triples file mentions the maintenance
# toggle path in comments — it is NOT a pipeline input (DI descoped) and is excluded.
cd "C:/Users/miaot/Github/MIST" 2>/dev/null || cd "$(dirname "$0")/../../../../.."
INPUTS="evaluation/suts/teastore/openapi/teastore-swagger.yaml evaluation/suts/teastore/real-system-conf.yaml evaluation/suts/teastore/teastore-myc.properties evaluation/suts/teastore/traces"
H=$(grep -ril "generatedb" $INPUTS 2>/dev/null | wc -l)
echo "generatedb hits in the pipeline inputs: $H"
[ "$H" = "0" ] && echo "GATE PASS" || { echo "GATE FAIL — STOP"; grep -ril "generatedb" $INPUTS; exit 1; }
