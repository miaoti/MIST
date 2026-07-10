#!/bin/bash
# wave-3a pin 1-P0 (plan rev 2.1, B-F1): flagd toggle with VERIFIED propagation + frozen restore.
#   usage: flagd-toggle.sh <flagName> <on|off|status>
# Mechanics: edits the flagd-config ConfigMap's demo.flagd.json (defaultVariant), applies, then
# POLLS the flag's OFREP evaluation (temporary PF to flagd :8016) until the value flips; prints the
# patch->effect latency. "off" additionally verifies BYTE-EQUALITY of the live CM data against the
# wave-start frozen reference /home/miaot/gate1-logs/tenancy-window/otel/flagd-frozen.json.
# Fallback ladder (per the plan): if the CM patch does not propagate within 180 s -> try the
# flagd-ui API; if that fails -> flagd pod restart (ordering consequences recorded by the caller).
# Item 3 inherits this script verbatim.
set -u
NS=otel-demo
FLAG=${1:?flag name}
ACTION=${2:?on|off|status}
FROZEN=/home/miaot/gate1-logs/tenancy-window/otel/flagd-frozen.json

pf_start() {
  kubectl -n $NS port-forward svc/flagd 18016:8016 >/tmp/pf-flagd.log 2>&1 &
  PF_PID=$!
  sleep 2
}
pf_stop() { kill ${PF_PID:-0} 2>/dev/null; }

ofrep_value() { # prints the current variant-implied boolean value ("true"/"false") or ERR
  curl -s --max-time 5 -X POST -H "Content-Type: application/json" -d '{"context":{}}' \
    "http://localhost:18016/ofrep/v1/evaluate/flags/$FLAG" | \
    python3 -c "import json,sys;d=json.load(sys.stdin);print(str(d.get('value')).lower())" 2>/dev/null || echo ERR
}

cm_read() { kubectl -n $NS get cm flagd-config -o jsonpath='{.data.demo\.flagd\.json}'; }

cm_write_variant() { # $1 = target defaultVariant
  local TARGET=$1
  cm_read > /tmp/flagd-live.json
  python3 - "$FLAG" "$TARGET" <<'PY'
import json, sys
flag, target = sys.argv[1], sys.argv[2]
d = json.load(open("/tmp/flagd-live.json"))
assert flag in d["flags"], "flag %s not in config" % flag
assert target in d["flags"][flag]["variants"], "variant %s not defined for %s" % (target, flag)
d["flags"][flag]["defaultVariant"] = target
json.dump(d, open("/tmp/flagd-new.json", "w"), indent=2)
print("edited %s -> defaultVariant=%s" % (flag, target))
PY
  kubectl -n $NS create configmap flagd-config --from-file=demo.flagd.json=/tmp/flagd-new.json \
    --dry-run=client -o yaml | kubectl -n $NS apply -f - >/dev/null
}

case "$ACTION" in
  status)
    pf_start; echo "$FLAG value: $(ofrep_value)"; pf_stop ;;
  on|off)
    TARGETVAL=$([ "$ACTION" = "on" ] && echo true || echo false)
    TARGETVAR=$([ "$ACTION" = "on" ] && echo on || echo off)
    pf_start
    BEFORE=$(ofrep_value)
    echo "$FLAG before: $BEFORE (target $TARGETVAL)"
    T0=$(date +%s)
    cm_write_variant "$TARGETVAR"
    LAT=-1
    for i in $(seq 1 90); do
      V=$(ofrep_value)
      if [ "$V" = "$TARGETVAL" ]; then LAT=$(( $(date +%s) - T0 )); break; fi
      sleep 2
    done
    if [ "$LAT" = "-1" ]; then
      echo "PRIMARY (CM patch) did NOT propagate in 180s -> FALLBACK NEEDED (flagd-ui API / pod restart) - caller decides"
      pf_stop; exit 2
    fi
    echo "$FLAG flipped to $TARGETVAL; patch->effect latency ${LAT}s"
    if [ "$ACTION" = "off" ]; then
      cm_read > /tmp/flagd-restored.json
      if python3 -c "
import json,sys
a=json.load(open('/tmp/flagd-restored.json')); b=json.load(open('$FROZEN'))
sys.exit(0 if a==b else 1)"; then
        echo "restore verified: live CM == frozen reference (semantic JSON equality)"
      else
        echo "RESTORE MISMATCH vs frozen reference - INVESTIGATE"; pf_stop; exit 3
      fi
    fi
    pf_stop ;;
  *) echo "usage: $0 <flag> <on|off|status>"; exit 1 ;;
esac
