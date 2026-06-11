#!/usr/bin/env python3
"""
Roll D1 / D2 / D3 outputs up into a single markdown report.

Reads the JSON summaries written by validate_d1.py, validate_d2.py, and
validate_d3.py, and produces a human-readable report that highlights pass/fail
against the thresholds in `input-quality-measurement-framework.md`.
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path


def load(p: Path) -> dict | None:
    if not p.is_file():
        return None
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None


def fmt_pct(x: float | None) -> str:
    if x is None:
        return "N/A"
    return f"{x * 100:.2f}%"


def status(passed: bool | None) -> str:
    if passed is None:
        return "—"
    return "✅ Pass" if passed else "❌ Fail"


def fmt_threshold(metric: str) -> str:
    return {
        "D1": "≥ 90%",
        "D2": "≥ 85%",
        "D3": "≤ 20%",
        "D4": "≥ 60%",
        "D5": "≥ 40%",
        "D6": "≥ 50%",
        "D7": "≥ 50%",
        "D8": "H_norm ≥ 50%",
        "D9": "≥ 50%",
        "D10": "per-type ≥ 95%",
    }[metric]


def fmt_num(x: float | None, places: int = 4) -> str:
    if x is None:
        return "N/A"
    return f"{x:.{places}f}"


def section_d1(d1: dict | None) -> list[str]:
    if d1 is None:
        return ["## D1 Schema Conformance Rate", "", "_No `d1_summary.json` found._", ""]
    out = [
        "## D1 — Schema Conformance Rate",
        "",
        f"- **SCR (positive variants): {fmt_pct(d1.get('scr'))}** "
        f"({d1.get('rows_valid', 0)} / {d1.get('rows_validated', 0)} validated)",
        f"- Rows total: {d1.get('rows_total', 0)} "
        f"(no-schema: {d1.get('rows_no_schema', 0)}, missing-op: {d1.get('rows_missing_operation', 0)}, "
        f"skipped negatives: {d1.get('rows_skipped_negative', 0)}, "
        f"skipped fallback: {d1.get('rows_skipped_fallback', 0)})",
        f"- Threshold: {fmt_threshold('D1')}  →  {status(d1.get('scr_threshold_pass'))}",
        "",
    ]
    bk = d1.get("by_test_kind") or {}
    if bk:
        out += ["### Breakdown by test kind", "",
                "| Kind | Total | Valid | SCR |",
                "| --- | ---: | ---: | ---: |"]
        for k, v in sorted(bk.items()):
            out.append(f"| {k} | {v.get('total', 0)} | {v.get('valid', 0)} | {fmt_pct(v.get('scr'))} |")
        out.append("")
    worst = d1.get("worst_parameters") or []
    if worst:
        out += ["### Worst-performing parameters (≥ 3 rows)", "",
                "| Operation | Parameter | Location | Total | Valid | SCR |",
                "| --- | --- | --- | ---: | ---: | ---: |"]
        for w in worst[:10]:
            out.append(
                f"| `{w['operation']}` | `{w['parameter']}` | {w['location']} | "
                f"{w['total']} | {w['valid']} | {fmt_pct(w['scr'])} |"
            )
        out.append("")
        # Show example violations for the worst
        out += ["### Example violations", ""]
        for w in worst[:5]:
            ex = w.get("invalid_examples") or []
            if not ex:
                continue
            out.append(f"- `{w['operation']}` / `{w['parameter']}` ({w['location']}):")
            for e in ex[:3]:
                val = e.get("value", "")
                if len(val) > 80:
                    val = val[:77] + "..."
                out.append(f"  - value `{val}` → {e.get('error', '')}")
        out.append("")
    return out


def section_d2(d2: dict | None) -> list[str]:
    if d2 is None:
        return ["## D2 — IPD Satisfaction Rate", "", "_No `d2_summary.json` found._", ""]
    out = ["## D2 — Inter-Parameter-Dependency Satisfaction Rate", ""]
    if (d2.get("status") or "").startswith("N/A"):
        out += [
            f"- **Status: {d2['status']}**",
            f"- {d2.get('note', '')}",
            "",
            "To enable: copy `debug/inputs/scripts/curated_idl.example.yaml` to "
            "`curated_idl.yaml` (without the `.example`) and add rules.",
            "",
        ]
        return out
    out += [
        f"- **IPD-SR: {fmt_pct(d2.get('ipd_sr'))}** "
        f"({d2.get('payloads_fully_satisfied', 0)} / {d2.get('payloads_evaluated', 0)} payloads)",
        f"- Rules loaded: {d2.get('rules_loaded', 0)} "
        f"(curated file present: {d2.get('rules_from_curated')})",
        f"- Total payloads observed: {d2.get('payloads_total', 0)}",
        f"- Threshold: {fmt_threshold('D2')}  →  {status(d2.get('ipd_sr_threshold_pass'))}",
        "",
    ]
    by_op = d2.get("by_operation") or {}
    if by_op:
        out += ["### Per-operation breakdown", "",
                "| Operation | Total | Satisfied | IPD-SR |",
                "| --- | ---: | ---: | ---: |"]
        rows = sorted(
            ((op, v) for op, v in by_op.items()),
            key=lambda x: ((x[1].get("ipd_sr") or 0.0), x[0]),
        )
        for op, v in rows[:15]:
            out.append(f"| `{op}` | {v.get('total', 0)} | {v.get('satisfied', 0)} | {fmt_pct(v.get('ipd_sr'))} |")
        out.append("")
    rv = d2.get("rule_violation_counts") or {}
    if rv:
        out += ["### Most-violated rules", "",
                "| Rule | Violations |",
                "| --- | ---: |"]
        for rule, count in list(rv.items())[:10]:
            out.append(f"| `{rule}` | {count} |")
        out.append("")
    return out


def section_d3(d3: dict | None) -> list[str]:
    if d3 is None:
        return ["## D3 — LLM Hallucination Rate", "", "_No `d3_summary.json` found._", ""]
    out = [
        "## D3 — LLM Hallucination Rate",
        "",
        f"- **LHR: {fmt_pct(d3.get('lhr'))}** "
        f"({d3.get('rows_hallucinated', 0)} / {d3.get('rows_actionable', 0)} actionable values)",
        f"- Definition: `{d3.get('lhr_definition', 'hallucinated / actionable')}` — abstentions are not hallucinations",
        f"- Total LLM values seen: {d3.get('rows_total', 0)} "
        f"(scored: {d3.get('rows_scored', 0)}; valid: {d3.get('rows_valid', 0)}; "
        f"abstained: {d3.get('rows_abstained', 0)})",
        f"- Scored categories: {', '.join(d3.get('scored_categories', []))}",
        f"- Abstain rate: {fmt_pct(d3.get('abstain_rate'))} "
        f"(LLM emitted `NO_GOOD_MATCH` / `NO_VALUES_GENERATED` instead of a value)",
        f"- Threshold: {fmt_threshold('D3')}  →  {status(d3.get('lhr_threshold_pass'))}",
        "",
    ]
    bv = d3.get("by_constraint_violation") or {}
    if bv:
        out += ["### Violations by constraint type", "",
                "| Constraint | Violations |",
                "| --- | ---: |"]
        for k, v in bv.items():
            out.append(f"| {k} | {v} |")
        out.append("")
    bm = d3.get("by_model") or {}
    if bm:
        out += ["### Per-model breakdown", "",
                "| Model | Total | Valid | Hallucinated | Abstained | LHR |",
                "| --- | ---: | ---: | ---: | ---: | ---: |"]
        for m, v in sorted(bm.items()):
            out.append(
                f"| `{m or 'unknown'}` | {v.get('total', 0)} | {v.get('valid', 0)} | "
                f"{v.get('hallucinated', 0)} | {v.get('abstained', 0)} | {fmt_pct(v.get('lhr'))} |"
            )
        out.append("")
    bc = d3.get("by_prompt_category") or {}
    if bc:
        out += ["### Per prompt category", "",
                "| Category | Total | Valid | Hallucinated | Abstained | LHR |",
                "| --- | ---: | ---: | ---: | ---: | ---: |"]
        for c, v in sorted(bc.items()):
            out.append(
                f"| `{c}` | {v.get('total', 0)} | {v.get('valid', 0)} | "
                f"{v.get('hallucinated', 0)} | {v.get('abstained', 0)} | {fmt_pct(v.get('lhr'))} |"
            )
        out.append("")
    worst = d3.get("worst_parameters") or []
    if worst:
        out += ["### Worst-performing parameters (≥ 3 actionable values)", "",
                "| Parameter | Total | Hallucinated | Abstained | LHR |",
                "| --- | ---: | ---: | ---: | ---: |"]
        for w in worst[:10]:
            out.append(
                f"| `{w['parameter']}` | {w['total']} | {w['hallucinated']} | "
                f"{w.get('abstained', 0)} | {fmt_pct(w['lhr'])} |"
            )
        out.append("")
        out += ["### Example violations", ""]
        for w in worst[:5]:
            ex = w.get("examples") or []
            if not ex:
                continue
            out.append(f"- `{w['parameter']}`:")
            for e in ex[:3]:
                val = e.get("value", "")
                if len(val) > 80:
                    val = val[:77] + "..."
                vstr = " | ".join(e.get("violations") or [])
                out.append(f"  - value `{val}` → {vstr}")
        out.append("")
    return out


def section_d4(d4: dict | None) -> list[str]:
    if d4 is None:
        return ["## D4 — Smart-Fetch Hit Rate", "", "_No `d4_summary.json` found._", ""]
    if (d4.get("status") or "").startswith("no exec log"):
        return ["## D4 — Smart-Fetch Hit Rate", "",
                "_Skipped: no execution log was mined._", ""]
    out = [
        "## D4 — Smart-Fetch Hit Rate",
        "",
        f"- **SFHR (conservative): {fmt_pct(d4.get('sfhr_conservative'))}** "
        f"({d4.get('by_classification', {}).get('smart_fetch', 0)} explicit smart-fetch / "
        f"{d4.get('id_typed_inputs', 0)} ID-typed inputs)",
        f"- SFHR (upper bound, including unattributed pool draws): {fmt_pct(d4.get('sfhr_upper'))}",
        f"- ID-typed positive inputs: {d4.get('id_typed_inputs', 0)}",
        f"- Threshold: {fmt_threshold('D4')}  →  {status(d4.get('sfhr_threshold_pass'))}",
        "",
        "**Coverage caveat.** "
        + (d4.get("coverage_caveat") or ""),
        "",
    ]
    by_cls = d4.get("by_classification") or {}
    if by_cls:
        out += ["### Classification of ID-typed inputs", "",
                "| Classification | Count |",
                "| --- | ---: |"]
        for k in ("smart_fetch", "shared_pool_draw", "llm", "negative", "unknown"):
            out.append(f"| {k} | {by_cls.get(k, 0)} |")
        out.append("")
    worst = d4.get("worst_parameters") or []
    if worst:
        out += ["### Worst-performing parameters (≥ 3 ID-typed rows)", "",
                "| Parameter | Total | SmartFetch | SFHR (cons.) | SFHR (upper) |",
                "| --- | ---: | ---: | ---: | ---: |"]
        for w in worst[:10]:
            out.append(
                f"| `{w['parameter']}` | {w['total']} | {w['smart_fetch']} | "
                f"{fmt_pct(w['sfhr_conservative'])} | {fmt_pct(w['sfhr_upper'])} |"
            )
        out.append("")
    return out


def section_d5(d5: dict | None) -> list[str]:
    if d5 is None:
        return ["## D5 — ID-Resolvability Rate", "", "_No `d5_summary.json` found._", ""]
    if (d5.get("status") or "").startswith("no Jaeger"):
        return ["## D5 — ID-Resolvability Rate", "",
                "_Skipped: no Jaeger traces were mined._", ""]
    out = [
        "## D5 — ID-Resolvability Rate",
        "",
        f"- **IDR: {fmt_pct(d5.get('idr'))}** "
        f"({d5.get('resolvable', 0)} / {d5.get('id_typed_inputs', 0)} ID-typed values "
        f"appeared as outputs in the Jaeger trace export)",
        f"- Distinct values observed in spans: {d5.get('observed_values_in_traces', 0)}",
        f"- Threshold: {fmt_threshold('D5')}  →  {status(d5.get('idr_threshold_pass'))}",
        "",
    ]
    worst = d5.get("worst_parameters") or []
    if worst:
        out += ["### Worst-performing parameters (≥ 3 ID-typed rows)", "",
                "| Parameter | Total | Resolvable | IDR |",
                "| --- | ---: | ---: | ---: |"]
        for w in worst[:10]:
            out.append(
                f"| `{w['parameter']}` | {w['total']} | {w['resolvable']} | {fmt_pct(w['idr'])} |"
            )
        out.append("")
        # Show example unresolvable values for the worst few
        out += ["### Example unresolvable values", ""]
        for w in worst[:5]:
            ex = w.get("unresolvable_examples") or []
            if not ex:
                continue
            out.append(f"- `{w['parameter']}`: " + ", ".join(f"`{e}`" for e in ex[:3]))
        out.append("")
    return out


def section_d6(d6: dict | None) -> list[str]:
    if d6 is None:
        return ["## D6 — Chain Resolution Rate", "", "_No `d6_summary.json` found._", ""]
    out = [
        "## D6 — Chain Resolution Rate",
        "",
        f"- **CRR: {fmt_pct(d6.get('crr'))}** "
        f"({d6.get('sequences_fully_resolved', 0)} / {d6.get('sequences_considered', 0)} "
        f"multi-step sequences fully resolved)",
        f"- ID-input resolution rate (across all multi-step sequences): "
        f"{fmt_pct(d6.get('id_input_resolution_rate'))} "
        f"({d6.get('id_inputs_resolved', 0)} / {d6.get('id_inputs_total', 0)})",
        f"- Single-step sequences excluded: {d6.get('sequences_skipped_single_step', 0)}",
        f"- Threshold: {fmt_threshold('D6')}  →  {status(d6.get('crr_threshold_pass'))}",
        "",
    ]
    return out


def section_d7(d7: dict | None) -> list[str]:
    if d7 is None:
        return ["## D7 — Realism Score", "", "_No `d7_summary.json` found._", ""]
    out = [
        "## D7 — Realism Score",
        "",
        f"- **Realism: {fmt_pct(d7.get('realism'))}** "
        f"({d7.get('realistic', 0)} / {d7.get('nlp_typed_inputs', 0)} NLP-typed values "
        f"matched a known entity)",
        f"- Oracle: `{d7.get('oracle_mode', 'offline')}` "
        f"({d7.get('entities_loaded', 0)} curated entries; "
        f"{d7.get('online_calls', 0)} online lookups)",
        f"- Empty values: {d7.get('empty', 0)}",
        f"- Threshold: {fmt_threshold('D7')}  →  {status(d7.get('realism_threshold_pass'))}",
        "",
    ]
    by_src = d7.get("by_lookup_source") or {}
    if by_src:
        out += ["### Lookup source breakdown", "",
                "| Source | Count |",
                "| --- | ---: |"]
        for k, v in sorted(by_src.items(), key=lambda x: -x[1]):
            out.append(f"| {k} | {v} |")
        out.append("")
    worst = d7.get("worst_parameters") or []
    if worst:
        out += ["### Worst-performing parameters (≥ 3 NLP-typed rows)", "",
                "| Parameter | Total | Realistic | Realism |",
                "| --- | ---: | ---: | ---: |"]
        for w in worst[:10]:
            out.append(
                f"| `{w['parameter']}` | {w['total']} | {w['realistic']} | {fmt_pct(w['realism'])} |"
            )
        out.append("")
        out += ["### Example unrealistic values", ""]
        for w in worst[:5]:
            ex = w.get("unrealistic_examples") or []
            if not ex:
                continue
            out.append(f"- `{w['parameter']}`: " + ", ".join(f"`{e}`" for e in ex[:3]))
        out.append("")
    return out


def section_d8(d8: dict | None) -> list[str]:
    if d8 is None:
        return ["## D8 — Pool Shannon Entropy + Simpson Index", "",
                "_No `d8_summary.json` found._", ""]
    out = [
        "## D8 — Pool Shannon Entropy + Simpson Index",
        "",
        f"- **Mean H_norm: {fmt_num(d8.get('mean_shannon_normalised'))}** "
        f"(Shannon entropy as fraction of log₂(pool size); see framework spec §D8)",
        f"- **Mean Simpson 1-D: {fmt_num(d8.get('mean_simpson'))}**",
        f"- Pools gated (≥ 3 rows): {d8.get('pools_gated', 0)} / {d8.get('pools_total', 0)}",
        f"- Rows kept: {d8.get('rows_kept', 0)} "
        f"(fallback-skipped: {d8.get('rows_fallback_skipped', 0)})",
        f"- Threshold: {fmt_threshold('D8')}  →  {status(d8.get('shannon_threshold_pass'))}",
        "",
    ]
    worst = d8.get("worst_pools") or []
    if worst:
        out += ["### Lowest-diversity pools (≥ 3 rows)", "",
                "| Operation | Parameter | Total | Distinct | H_norm | Simpson |",
                "| --- | --- | ---: | ---: | ---: | ---: |"]
        for w in worst[:10]:
            h = w.get("shannon_normalised")
            out.append(
                f"| `{w['operation']}` | `{w['parameter']}` | "
                f"{w['total']} | {w['distinct']} | "
                f"{fmt_num(h) if h is not None else '—'} | "
                f"{fmt_num(w['simpson'])} |"
            )
        out.append("")
    return out


def section_d9(d9: dict | None) -> list[str]:
    if d9 is None:
        return ["## D9 — Equivalence-Partition Coverage", "",
                "_No `d9_summary.json` found._", ""]
    out = [
        "## D9 — Equivalence-Partition Coverage",
        "",
        f"- **Mean EPC: {fmt_pct(d9.get('mean_epc'))}**",
        f"- Pools gated (≥ 3 rows): {d9.get('pools_gated', 0)} / {d9.get('pools_total', 0)}",
        f"- Rows kept: {d9.get('rows_kept', 0)} "
        f"(fallback-skipped: {d9.get('rows_fallback_skipped', 0)})",
        f"- Threshold: {fmt_threshold('D9')}  →  {status(d9.get('epc_threshold_pass'))}",
        "",
    ]
    worst = d9.get("worst_pools") or []
    if worst:
        out += ["### Worst-covered pools (≥ 3 rows)", "",
                "| Operation | Parameter | Total | Covered/Total | EPC | Missing classes |",
                "| --- | --- | ---: | ---: | ---: | --- |"]
        for w in worst[:10]:
            miss = w.get("missing_classes") or []
            miss_str = ", ".join(f"`{m}`" for m in miss[:5])
            if len(miss) > 5:
                miss_str += f" (+{len(miss) - 5} more)"
            out.append(
                f"| `{w['operation']}` | `{w['parameter']}` | "
                f"{w['total']} | {w['classes_covered']}/{w['classes_total']} | "
                f"{fmt_pct(w['epc'])} | {miss_str} |"
            )
        out.append("")
    return out


def section_d10(d10: dict | None) -> list[str]:
    if d10 is None:
        return ["## D10 — Negative-Input Fault-Type Purity", "",
                "_No `d10_summary.json` found._", ""]
    if (d10.get("status") or "").startswith("no exec log"):
        return ["## D10 — Negative-Input Fault-Type Purity", "",
                "_Skipped: no execution log was mined._", ""]
    out = [
        "## D10 — Negative-Input Fault-Type Purity",
        "",
        f"- **Overall NIFP: {fmt_pct(d10.get('overall_nifp'))}** "
        f"({d10.get('rows_pure', 0)} / {d10.get('rows_total', 0)})",
        f"- Definition: `{d10.get('definition', '')}`",
        f"- Threshold: {fmt_threshold('D10')}  →  {status(d10.get('nifp_threshold_pass'))}",
        "",
    ]
    by = d10.get("by_fault") or {}
    if by:
        out += ["### Per fault-type purity", "",
                "| Fault label | Total | Pure | Schema-unbounded | NIFP | Pass (≥ 95%) |",
                "| --- | ---: | ---: | ---: | ---: | --- |"]
        per_pass = d10.get("per_type_pass") or {}
        for fault, agg in sorted(by.items()):
            purity = agg.get("purity")
            passed = per_pass.get(fault)
            out.append(
                f"| `{fault}` | {agg.get('total', 0)} | {agg.get('pure', 0)} | "
                f"{agg.get('schema_unbounded', 0)} | "
                f"{fmt_pct(purity)} | "
                f"{('✅' if passed else '❌') if fault in per_pass else '—'} |"
            )
        out.append("")
        # Add a clarification footnote when there are unbounded boundary cases
        bv = by.get("BOUNDARY_VIOLATION") or {}
        if bv.get("schema_unbounded", 0) > 0:
            out.append(
                f"_Note: {bv['schema_unbounded']} `BOUNDARY_VIOLATION`-labelled values target "
                f"parameters whose OAS schema declares no `minimum`/`maximum`/`minLength`/`maxLength`. "
                f"These cannot violate any boundary because none is declared — the impurity here "
                f"is a **schema gap**, not a value-purity miss._"
            )
            out.append("")
        out += ["### Example impure values", ""]
        for fault, agg in sorted(by.items()):
            ex = agg.get("impure_examples") or []
            if not ex:
                continue
            out.append(f"- `{fault}`:")
            for e in ex[:3]:
                val = e.get("value", "")
                if len(val) > 60:
                    val = val[:57] + "..."
                out.append(
                    f"  - `{e.get('parameter', '')}` = `{val}` "
                    f"({e.get('operation', 'unknown op')})"
                )
        out.append("")
    return out


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--measurements", required=True, type=Path,
                   help="Output directory used by validate_d{1..10}.py")
    p.add_argument("--out", required=True, type=Path, help="Markdown report path")
    p.add_argument("--run-id", default=None, help="Run identifier to include in the header")
    p.add_argument("--run-dir", default=None, help="Path to the test directory (for the header)")
    p.add_argument("--llm-log", default=None, help="LLM log file used for D3 (for the header)")
    p.add_argument("--oas", default=None, help="OAS spec used for D1/D2 (for the header)")
    args = p.parse_args(argv)

    base = args.measurements
    d1 = load(base / "d1_summary.json")
    d2 = load(base / "d2_summary.json")
    d3 = load(base / "d3_summary.json")
    d4 = load(base / "d4_summary.json")
    d5 = load(base / "d5_summary.json")
    d6 = load(base / "d6_summary.json")
    d7 = load(base / "d7_summary.json")
    d8 = load(base / "d8_summary.json")
    d9 = load(base / "d9_summary.json")
    d10 = load(base / "d10_summary.json")

    title_run = args.run_id or base.name
    lines = [
        f"# Input Quality Metrics — {title_run}",
        "",
        f"_Generated {datetime.now(timezone.utc).isoformat(timespec='seconds').replace("+00:00", "")}Z_",
        "",
        "## Source artefacts",
        "",
    ]
    if args.run_dir:
        lines.append(f"- Generated tests: `{args.run_dir}`")
    if args.llm_log:
        lines.append(f"- LLM log: `{args.llm_log}`")
    if args.oas:
        lines.append(f"- OpenAPI spec: `{args.oas}`")
    lines.append("")

    # Top-line summary
    lines += [
        "## Summary",
        "",
        "| Metric | Score | Threshold | Status |",
        "| --- | --- | --- | --- |",
    ]
    if d1:
        lines.append(f"| D1 SCR (positive) | **{fmt_pct(d1.get('scr'))}** | {fmt_threshold('D1')} | {status(d1.get('scr_threshold_pass'))} |")
    if d2:
        sc = d2.get("ipd_sr")
        if (d2.get("status") or "").startswith("N/A"):
            lines.append(f"| D2 IPD-SR | N/A — no IDL declared | {fmt_threshold('D2')} | — |")
        else:
            lines.append(f"| D2 IPD-SR | **{fmt_pct(sc)}** | {fmt_threshold('D2')} | {status(d2.get('ipd_sr_threshold_pass'))} |")
    if d3:
        lines.append(f"| D3 LHR | **{fmt_pct(d3.get('lhr'))}** | {fmt_threshold('D3')} | {status(d3.get('lhr_threshold_pass'))} |")
    if d4:
        if (d4.get("status") or "").startswith("no exec log"):
            lines.append(f"| D4 SFHR | _no exec log_ | {fmt_threshold('D4')} | — |")
        else:
            lines.append(
                f"| D4 SFHR (conservative) | **{fmt_pct(d4.get('sfhr_conservative'))}** "
                f"(upper {fmt_pct(d4.get('sfhr_upper'))}) | {fmt_threshold('D4')} | "
                f"{status(d4.get('sfhr_threshold_pass'))} |"
            )
    if d5:
        if (d5.get("status") or "").startswith("no Jaeger"):
            lines.append(f"| D5 IDR | _no Jaeger traces_ | {fmt_threshold('D5')} | — |")
        else:
            lines.append(f"| D5 IDR | **{fmt_pct(d5.get('idr'))}** | {fmt_threshold('D5')} | {status(d5.get('idr_threshold_pass'))} |")
    if d6:
        lines.append(f"| D6 CRR | **{fmt_pct(d6.get('crr'))}** | {fmt_threshold('D6')} | {status(d6.get('crr_threshold_pass'))} |")
    if d7:
        lines.append(f"| D7 Realism | **{fmt_pct(d7.get('realism'))}** | {fmt_threshold('D7')} | {status(d7.get('realism_threshold_pass'))} |")
    if d8:
        lines.append(
            f"| D8 Pool Diversity (H_norm / Simpson) | "
            f"**{fmt_num(d8.get('mean_shannon_normalised'))} / {fmt_num(d8.get('mean_simpson'))}** | "
            f"{fmt_threshold('D8')} | {status(d8.get('shannon_threshold_pass'))} |"
        )
    if d9:
        lines.append(
            f"| D9 Equivalence-Partition Coverage | **{fmt_pct(d9.get('mean_epc'))}** | "
            f"{fmt_threshold('D9')} | {status(d9.get('epc_threshold_pass'))} |"
        )
    if d10:
        if (d10.get("status") or "").startswith("no exec log"):
            lines.append(f"| D10 NIFP | _no exec log_ | {fmt_threshold('D10')} | — |")
        else:
            lines.append(
                f"| D10 NIFP (overall) | **{fmt_pct(d10.get('overall_nifp'))}** | "
                f"{fmt_threshold('D10')} | {status(d10.get('nifp_threshold_pass'))} |"
            )
    lines.append("")

    lines += section_d1(d1)
    lines += section_d2(d2)
    lines += section_d3(d3)
    lines += section_d4(d4)
    lines += section_d5(d5)
    lines += section_d6(d6)
    lines += section_d7(d7)
    lines += section_d8(d8)
    lines += section_d9(d9)
    lines += section_d10(d10)

    lines += [
        "---",
        "",
        "_See `debug/inputs/input-quality-measurement-framework.md` for the full definitions and citations._",
        "",
    ]

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text("\n".join(lines), encoding="utf-8")
    print(f"generate_report: → {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
