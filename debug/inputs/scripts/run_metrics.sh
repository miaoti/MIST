#!/usr/bin/env bash
# Run the full input-quality measurement pipeline on a RESTest run.
#
# Defaults to the most recent TrainTicket two-stage run and the most recent
# LLM log. Override any of them with the flags below.
#
# Usage:
#   ./debug/inputs/scripts/run_metrics.sh
#   ./debug/inputs/scripts/run_metrics.sh --run-dir <PATH>
#   ./debug/inputs/scripts/run_metrics.sh \
#         --run-dir src/test/java/trainticket_twostage_test/<RUN_ID> \
#         --llm-log logs/llm-communications/llm-communication-<TS>.log \
#         --oas     "src/main/resources/My-Example/trainticket/merged_openapi_spec 1.yaml" \
#         --out     debug/inputs/measurements/<RUN_ID>

set -euo pipefail

usage() {
    cat <<EOF
Usage: $0 [options]

Options:
  --run-dir DIR      Generated test directory (default: most-recent TrainTicketTwoStageTest_*)
  --llm-log FILE     LLM communication log (default: most-recent in logs/llm-communications/)
  --exec-log FILE    Execution log (default: trainticket_test_execution.log)
  --trace-dir DIR    Jaeger traces (default: src/main/resources/My-Example/trainticket/test-trace)
  --oas FILE         OpenAPI spec (default: merged_openapi_spec 1.yaml)
  --out DIR          Output directory (default: debug/inputs/measurements/<run_id>)
  --include-negatives  Also score negative variants in D1
  --realism-online   Use Wikidata fallback for D7 (slower; cached)

  --post-exec-jaeger Auto-export post-execution Jaeger traces before D5.
                     Requires --jaeger-start and --jaeger-end. The exporter
                     writes to --post-exec-trace-dir (default: <out>/post_exec_traces)
                     and the metric pipeline then reads from there.
  --jaeger-base URL  Jaeger UI API base (default: http://localhost:30005/jaeger/ui/api)
  --jaeger-service S Repeatable service name (default: ts-gateway-service)
  --jaeger-start TS  Window start (epoch s/ms/us — auto-detected)
  --jaeger-end TS    Window end (epoch s/ms/us — auto-detected)
  --post-exec-trace-dir DIR  Output for the exporter (default: <out>/post_exec_traces)

  --python BIN       Python executable (default: python3)
  -h | --help        Show this help

EOF
}

RUN_DIR=""
LLM_LOG=""
EXEC_LOG=""
TRACE_DIR=""
OAS=""
OUT_DIR=""
INCLUDE_NEG=""
REALISM_ONLINE=""
POST_EXEC_JAEGER=""
JAEGER_BASE=""
JAEGER_SERVICES=()
JAEGER_START=""
JAEGER_END=""
POST_EXEC_TRACE_DIR=""
PYTHON_BIN="python3"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --run-dir)   RUN_DIR="$2"; shift 2 ;;
        --llm-log)   LLM_LOG="$2"; shift 2 ;;
        --exec-log)  EXEC_LOG="$2"; shift 2 ;;
        --trace-dir) TRACE_DIR="$2"; shift 2 ;;
        --oas)       OAS="$2"; shift 2 ;;
        --out)       OUT_DIR="$2"; shift 2 ;;
        --include-negatives) INCLUDE_NEG="--include-negatives"; shift ;;
        --realism-online)    REALISM_ONLINE="--online"; shift ;;
        --post-exec-jaeger)  POST_EXEC_JAEGER="1"; shift ;;
        --jaeger-base)       JAEGER_BASE="$2"; shift 2 ;;
        --jaeger-service)    JAEGER_SERVICES+=("$2"); shift 2 ;;
        --jaeger-start)      JAEGER_START="$2"; shift 2 ;;
        --jaeger-end)        JAEGER_END="$2"; shift 2 ;;
        --post-exec-trace-dir) POST_EXEC_TRACE_DIR="$2"; shift 2 ;;
        --python)    PYTHON_BIN="$2"; shift 2 ;;
        -h|--help)   usage; exit 0 ;;
        *) echo "unknown arg: $1" >&2; usage; exit 2 ;;
    esac
done

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ---- Defaults ----
if [[ -z "$RUN_DIR" ]]; then
    RUN_DIR=$(ls -dt "$REPO_ROOT"/src/test/java/trainticket_twostage_test/TrainTicketTwoStageTest_* 2>/dev/null | head -n1 || true)
fi
if [[ -z "$RUN_DIR" || ! -d "$RUN_DIR" ]]; then
    echo "error: no run dir found; pass --run-dir" >&2
    exit 2
fi
RUN_ID=$(basename "$RUN_DIR")

if [[ -z "$LLM_LOG" ]]; then
    LLM_LOG=$(ls -t "$REPO_ROOT"/logs/llm-communications/*.log 2>/dev/null | head -n1 || true)
fi
if [[ -z "$LLM_LOG" || ! -f "$LLM_LOG" ]]; then
    echo "warning: no LLM log found; D3 will be empty (use --llm-log)" >&2
fi

if [[ -z "$OAS" ]]; then
    OAS="$REPO_ROOT/src/main/resources/My-Example/trainticket/merged_openapi_spec 1.yaml"
fi
if [[ ! -f "$OAS" ]]; then
    echo "error: OAS file not found: $OAS" >&2
    exit 2
fi

if [[ -z "$EXEC_LOG" ]]; then
    EXEC_LOG="$REPO_ROOT/logs/trainticket_twostage_test/trainticket_test_execution.log"
fi
if [[ ! -f "$EXEC_LOG" ]]; then
    echo "warning: exec log not found ($EXEC_LOG); D4 (SFHR) will report no provenance" >&2
fi

if [[ -z "$TRACE_DIR" ]]; then
    TRACE_DIR="$REPO_ROOT/src/main/resources/My-Example/trainticket/test-trace"
fi
if [[ ! -d "$TRACE_DIR" ]]; then
    echo "warning: trace dir not found ($TRACE_DIR); D5 (IDR) will report no observed values" >&2
fi

if [[ -z "$OUT_DIR" ]]; then
    OUT_DIR="$REPO_ROOT/debug/inputs/measurements/$RUN_ID"
fi
mkdir -p "$OUT_DIR"

echo "================================================================"
echo " run_id    : $RUN_ID"
echo " run_dir   : $RUN_DIR"
echo " llm_log   : ${LLM_LOG:-<none>}"
echo " exec_log  : ${EXEC_LOG:-<none>}"
echo " trace_dir : ${TRACE_DIR:-<none>}"
echo " oas       : $OAS"
echo " out       : $OUT_DIR"
echo "================================================================"

# ---- Stage 1: Mining ----
"$PYTHON_BIN" "$SCRIPT_DIR/mine_test_inputs.py" \
    --run-dir "$RUN_DIR" \
    --out "$OUT_DIR/inputs.csv"

if [[ -n "$LLM_LOG" && -f "$LLM_LOG" ]]; then
    "$PYTHON_BIN" "$SCRIPT_DIR/mine_llm_log.py" \
        --log "$LLM_LOG" \
        --out "$OUT_DIR/llm_pairs.csv"
else
    : > "$OUT_DIR/llm_pairs.csv"
fi

# ---- Stage 2: Validation ----
"$PYTHON_BIN" "$SCRIPT_DIR/validate_d1.py" \
    --inputs "$OUT_DIR/inputs.csv" \
    --oas "$OAS" \
    --out-dir "$OUT_DIR" \
    $INCLUDE_NEG

if [[ -s "$OUT_DIR/llm_pairs.csv" ]]; then
    "$PYTHON_BIN" "$SCRIPT_DIR/validate_d3.py" \
        --pairs "$OUT_DIR/llm_pairs.csv" \
        --out-dir "$OUT_DIR"
else
    echo '{"metric":"D3 LLM Hallucination Rate","status":"no LLM log mined","lhr":null}' > "$OUT_DIR/d3_summary.json"
fi

"$PYTHON_BIN" "$SCRIPT_DIR/validate_d2.py" \
    --inputs "$OUT_DIR/inputs.csv" \
    --oas "$OAS" \
    --out-dir "$OUT_DIR"

# ---- Stage 1b: Mining for D4–D5 ----
if [[ -f "$EXEC_LOG" ]]; then
    "$PYTHON_BIN" "$SCRIPT_DIR/mine_provenance.py" \
        --exec-log "$EXEC_LOG" \
        --out "$OUT_DIR/provenance.csv"
else
    : > "$OUT_DIR/provenance.csv"
fi

# Optional: export post-execution Jaeger traces before mining. This is the
# recommended path for D5 — the input trace dir does not contain the responses
# produced by running the generated tests.
if [[ -n "$POST_EXEC_JAEGER" ]]; then
    if [[ -z "$JAEGER_START" || -z "$JAEGER_END" ]]; then
        echo "error: --post-exec-jaeger requires --jaeger-start and --jaeger-end" >&2
        exit 2
    fi
    if [[ -z "$POST_EXEC_TRACE_DIR" ]]; then
        POST_EXEC_TRACE_DIR="$OUT_DIR/post_exec_traces"
    fi
    EXPORTER_ARGS=(
        --start "$JAEGER_START"
        --end "$JAEGER_END"
        --out "$POST_EXEC_TRACE_DIR"
    )
    [[ -n "$JAEGER_BASE" ]] && EXPORTER_ARGS+=(--base "$JAEGER_BASE")
    if [[ ${#JAEGER_SERVICES[@]} -eq 0 ]]; then
        EXPORTER_ARGS+=(--service "ts-gateway-service")
    else
        for s in "${JAEGER_SERVICES[@]}"; do
            EXPORTER_ARGS+=(--service "$s")
        done
    fi
    echo "  exporting post-execution Jaeger traces..."
    "$PYTHON_BIN" "$SCRIPT_DIR/export_jaeger_traces.py" "${EXPORTER_ARGS[@]}" || \
        echo "  warning: Jaeger exporter failed; D5 will use whatever was already in $POST_EXEC_TRACE_DIR" >&2
    # Override TRACE_DIR for the mining step below.
    TRACE_DIR="$POST_EXEC_TRACE_DIR"
fi

if [[ -d "$TRACE_DIR" ]]; then
    "$PYTHON_BIN" "$SCRIPT_DIR/mine_jaeger.py" \
        --trace-dir "$TRACE_DIR" \
        --out "$OUT_DIR/jaeger_outputs.csv"
else
    : > "$OUT_DIR/jaeger_outputs.csv"
fi

# ---- Stage 2b: D4–D7 validation ----
if [[ -s "$OUT_DIR/provenance.csv" ]]; then
    D4_ARGS=(
        --inputs "$OUT_DIR/inputs.csv"
        --provenance "$OUT_DIR/provenance.csv"
        --out-dir "$OUT_DIR"
    )
    if [[ -s "$OUT_DIR/llm_pairs.csv" ]]; then
        D4_ARGS+=(--llm-pairs "$OUT_DIR/llm_pairs.csv")
    fi
    "$PYTHON_BIN" "$SCRIPT_DIR/validate_d4.py" "${D4_ARGS[@]}"
else
    echo '{"metric":"D4 Smart-Fetch Hit Rate","status":"no exec log mined","sfhr_conservative":null}' > "$OUT_DIR/d4_summary.json"
fi

if [[ -s "$OUT_DIR/jaeger_outputs.csv" ]]; then
    "$PYTHON_BIN" "$SCRIPT_DIR/validate_d5.py" \
        --inputs "$OUT_DIR/inputs.csv" \
        --jaeger "$OUT_DIR/jaeger_outputs.csv" \
        --out-dir "$OUT_DIR"
else
    echo '{"metric":"D5 ID-Resolvability Rate","status":"no Jaeger traces mined","idr":null}' > "$OUT_DIR/d5_summary.json"
fi

"$PYTHON_BIN" "$SCRIPT_DIR/validate_d6.py" \
    --inputs "$OUT_DIR/inputs.csv" \
    --out-dir "$OUT_DIR"

"$PYTHON_BIN" "$SCRIPT_DIR/validate_d7.py" \
    --inputs "$OUT_DIR/inputs.csv" \
    --out-dir "$OUT_DIR" \
    --cache "$OUT_DIR/realism_cache.json" \
    $REALISM_ONLINE

# ---- Stage 2c: D8–D10 validation ----
"$PYTHON_BIN" "$SCRIPT_DIR/validate_d8.py" \
    --inputs "$OUT_DIR/inputs.csv" \
    --out-dir "$OUT_DIR"

"$PYTHON_BIN" "$SCRIPT_DIR/validate_d9.py" \
    --inputs "$OUT_DIR/inputs.csv" \
    --oas "$OAS" \
    --out-dir "$OUT_DIR"

if [[ -f "$EXEC_LOG" ]]; then
    "$PYTHON_BIN" "$SCRIPT_DIR/validate_d10.py" \
        --exec-log "$EXEC_LOG" \
        --oas "$OAS" \
        --out-dir "$OUT_DIR"
else
    echo '{"metric":"D10 Negative-Input Fault-Type Purity","status":"no exec log","overall_nifp":null}' > "$OUT_DIR/d10_summary.json"
fi

# ---- Stage 3: Report ----
"$PYTHON_BIN" "$SCRIPT_DIR/generate_report.py" \
    --measurements "$OUT_DIR" \
    --out "$OUT_DIR/report.md" \
    --run-id "$RUN_ID" \
    --run-dir "$RUN_DIR" \
    --llm-log "${LLM_LOG:-}" \
    --oas "$OAS"

echo ""
echo "Done. Open: $OUT_DIR/report.md"
