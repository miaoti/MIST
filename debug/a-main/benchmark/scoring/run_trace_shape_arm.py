#!/usr/bin/env python3
"""Run MIST's Trace Shape Oracle (offline, io.mist.cli.tools.OfflineTraceShapeEvaluator
— the SAME oracle code the generated tests wire) over every corpus case that carries a
captured trace, and materialize the mist_trace_shape verdict arm.

Per-case verdict = the oracle over the case's OWN capture leg (fault_trace for cases
that have one, else control_trace). flag iff an ERROR-severity invariant failure fires
(WARN stays no_flag — the runtime's blocking rule). Cases without a captured trace →
not_evaluable (own bucket; trace-uninstrumented/invisible classes land here honestly).

Configuration disclosed in the verdict file: ONLY the structural intent-agnostic
hidden-downstream-failure invariant is enabled — the four learned invariants require a
learned ShapeInvariantStore, which captured corpus traces do not carry.
"""
import json
import subprocess
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent.parent.parent  # repo root
CASES = HERE.parent / "cases"
OUT = HERE / "verdicts" / "mist_trace_shape.json"
DETAIL = HERE / "verdicts" / "mist_trace_shape.detail.json"

CP = f"{ROOT}/mist-cli/target/classes;{ROOT}/mist-core/target/classes;" + \
     (ROOT / "mist-cli/cp.txt").read_text(encoding="utf-8").strip()
FLAGS = ["-Dmst.oracle.shape.enabled=true",
         "-Dmst.oracle.shape.invariants.hidden_downstream_failure.enabled=true",
         "-Dmst.oracle.shape.invariants.span_tree.enabled=false",
         "-Dmst.oracle.shape.invariants.status_propagation.enabled=false",
         "-Dmst.oracle.shape.invariants.response_envelope.enabled=false",
         "-Dmst.oracle.shape.invariants.target_attribution.enabled=false"]


def main():
    verdicts, detail = {}, {}
    for f in sorted(CASES.glob("*.json")):
        c = json.loads(f.read_text(encoding="utf-8"))
        cid = c.get("case_id", f.stem)
        prov = c.get("provenance") or {}
        trace = prov.get("fault_trace") or prov.get("control_trace")
        if not trace or not (ROOT / trace).is_file():
            verdicts[cid] = "not_evaluable"
            continue
        e = c["target"]["entry_endpoint"]
        key = f"{e['method']} {e['path']}"
        r = subprocess.run(["java", "-cp", CP] + FLAGS +
                           ["io.mist.cli.tools.OfflineTraceShapeEvaluator",
                            str(ROOT / trace), key],
                           capture_output=True, text=True, timeout=180)
        try:
            out = json.loads(r.stdout[r.stdout.index("{"):])
        except Exception:
            verdicts[cid] = "not_evaluable"
            detail[cid] = {"error": (r.stdout + r.stderr)[-400:]}
            continue
        verdicts[cid] = out["verdict"]
        fails = [o for o in out["outcomes"] if not o["passed"]]
        detail[cid] = {"trace": trace, "root_api_key": key,
                       "traces_evaluated": out["traces_evaluated"],
                       "failed_outcomes": fails}
        print(cid, "->", out["verdict"],
              f"({len(fails)} failed outcomes)" if fails else "")

    OUT.write_text(json.dumps({
        "arm": "mist_trace_shape",
        "verdict_source": ("MIST TraceShapeOracle (io.mist.cli.tools.OfflineTraceShapeEvaluator, "
                           "the same oracle code the generated tests wire) executed offline over the "
                           "case's captured trace of record; ONLY the structural intent-agnostic "
                           "HIDDEN_DOWNSTREAM_FAILURE invariant enabled (the 4 learned invariants "
                           "need a learned store the captures do not carry); flag iff ERROR-severity "
                           "failure (WARN non-blocking, the runtime rule)"),
        "cases": verdicts}, indent=2) + "\n", encoding="utf-8")
    DETAIL.write_text(json.dumps(detail, indent=2) + "\n", encoding="utf-8")
    tally = {}
    for v in verdicts.values():
        tally[v] = tally.get(v, 0) + 1
    print("tally:", tally)


if __name__ == "__main__":
    main()
