#!/usr/bin/env python3
"""Mine per-call latency + call-structure data from MIST LLMCommunicationLogger logs.

Input:  one or more log directories (llm-communication-*.log files).
Output: per-call CSV, per-file summary CSV, aggregate JSON (stdout digest).

Log block format (LLMCommunicationLogger.java):
    ================================================================================
    <rocket> LLM REQUEST #N
    ================================================================================
    Timestamp: 2026-05-02 22:55:33.450
    Session ID: LLM-...
    Request ID: N
    Model Type: OLLAMA
    Model Name: qwen2.5-coder:14b
    Endpoint: http://localhost:11434
    Additional Metadata: maxTokens=200, temperature=0.70
    ...
    <memo> SYSTEM PROMPT: ... <bust> USER PROMPT: ...
    <target> LLM RESPONSE #N
    Timestamp: ...
    Status: <check> SUCCESS | <cross> FAILED
    Response Time: 2345 ms
    [Error Message: ...]
    <robot> LLM RESPONSE: ...
"""
import csv
import json
import os
import re
import statistics
import sys
from datetime import datetime

REQ_RE = re.compile(r"LLM REQUEST #(\d+)\s*$")
RESP_RE = re.compile(r"LLM RESPONSE #(\d+)\s*$")
TIME_RE = re.compile(r"^Response Time: (\d+) ms")
TS_RE = re.compile(r"^Timestamp: (.+)$")
MODEL_TYPE_RE = re.compile(r"^Model Type: (.+)$")
MODEL_NAME_RE = re.compile(r"^Model Name: (.+)$")
ENDPOINT_RE = re.compile(r"^Endpoint: (.+)$")
META_RE = re.compile(r"^Additional Metadata: maxTokens=(-?\d+), temperature=([\d.]+)")
STATUS_RE = re.compile(r"^Status: .*(SUCCESS|FAILED)")
ERR_RE = re.compile(r"^Error Message: (.+)$")
TRUNC_RE = re.compile(r"\[TRUNCATED - Original length: (\d+) characters\]")
STARTED_RE = re.compile(r"^# Started: (.+)$")
DIVIDER = "-" * 80
BLOCK_EDGE = "=" * 80

SECTION_NONE, SECTION_SYS, SECTION_USER, SECTION_RESP = 0, 1, 2, 3


def parse_ts(s):
    s = s.strip()
    try:
        return datetime.strptime(s, "%Y-%m-%d %H:%M:%S.%f")
    except ValueError:
        try:
            return datetime.strptime(s, "%Y-%m-%d %H:%M:%S")
        except ValueError:
            return None


def endpoint_class(endpoint, model_type):
    e = (endpoint or "").lower()
    if "deepseek" in e:
        return "deepseek-api"
    if "openai.com" in e:
        return "openai-api"
    if "generativelanguage" in e or (model_type or "").upper() == "GEMINI":
        return "gemini-api"
    if "localhost" in e or "127.0.0.1" in e:
        return "local"
    return "other:" + e[:40]


def mine_file(path, call_writer):
    """Parse one log file; return per-file summary dict."""
    fname = os.path.basename(path)
    session_started = None
    calls = []  # finished call dicts
    cur = None  # open request
    section = SECTION_NONE
    sec_chars = {SECTION_SYS: 0, SECTION_USER: 0, SECTION_RESP: 0}
    sec_trunc = {SECTION_SYS: None, SECTION_USER: None, SECTION_RESP: None}
    in_response_block = False

    def close_sections():
        nonlocal section
        section = SECTION_NONE

    def finish_call():
        nonlocal cur
        if cur is not None:
            for k, label in ((SECTION_SYS, "sys_chars"), (SECTION_USER, "user_chars"), (SECTION_RESP, "resp_chars")):
                cur[label] = sec_trunc[k] if sec_trunc[k] is not None else sec_chars[k]
            calls.append(cur)
            call_writer.writerow([
                fname, cur.get("req_id"), cur.get("req_ts"), cur.get("model_type"),
                cur.get("model_name"), cur.get("endpoint_class"), cur.get("max_tokens"),
                cur.get("temperature"), cur.get("success"), cur.get("response_time_ms"),
                cur.get("sys_chars"), cur.get("user_chars"), cur.get("resp_chars"),
                cur.get("error", "")[:120],
            ])
        cur = None
        for k in sec_chars:
            sec_chars[k] = 0
            sec_trunc[k] = None

    with open(path, "r", encoding="utf-8", errors="replace") as f:
        for raw in f:
            line = raw.rstrip("\n")

            if session_started is None:
                m = STARTED_RE.match(line)
                if m:
                    session_started = parse_ts(m.group(1))

            m = REQ_RE.search(line)
            if m and "REQUEST" in line:
                finish_call()
                in_response_block = False
                close_sections()
                cur = {"req_id": int(m.group(1)), "success": None, "response_time_ms": None}
                continue

            if cur is None:
                continue

            m = RESP_RE.search(line)
            if m and "RESPONSE #" in line:
                in_response_block = True
                close_sections()
                continue

            if not in_response_block:
                if section == SECTION_NONE:
                    m = TS_RE.match(line)
                    if m and "req_ts" not in cur:
                        cur["req_ts"] = m.group(1).strip()
                        continue
                    m = MODEL_TYPE_RE.match(line)
                    if m:
                        cur["model_type"] = m.group(1).strip()
                        continue
                    m = MODEL_NAME_RE.match(line)
                    if m:
                        cur["model_name"] = m.group(1).strip()
                        continue
                    m = ENDPOINT_RE.match(line)
                    if m:
                        cur["endpoint"] = m.group(1).strip()
                        cur["endpoint_class"] = endpoint_class(cur["endpoint"], cur.get("model_type"))
                        continue
                    m = META_RE.match(line)
                    if m:
                        cur["max_tokens"] = int(m.group(1))
                        cur["temperature"] = float(m.group(2))
                        continue
                if "SYSTEM PROMPT:" in line:
                    section = SECTION_SYS
                    continue
                if "USER PROMPT:" in line:
                    section = SECTION_USER
                    continue
                if line == DIVIDER:
                    close_sections()
                    continue
                if section in (SECTION_SYS, SECTION_USER):
                    mt = TRUNC_RE.search(line)
                    if mt:
                        sec_trunc[section] = int(mt.group(1))
                    else:
                        sec_chars[section] += len(line) + 1
                continue

            # response block
            m = STATUS_RE.match(line)
            if m:
                cur["success"] = (m.group(1) == "SUCCESS")
                continue
            m = TIME_RE.match(line)
            if m:
                cur["response_time_ms"] = int(m.group(1))
                continue
            m = TS_RE.match(line)
            if m and "resp_ts" not in cur:
                cur["resp_ts"] = m.group(1).strip()
                continue
            m = ERR_RE.match(line)
            if m:
                cur["error"] = m.group(1)
                continue
            if "LLM RESPONSE:" in line and "#" not in line:
                section = SECTION_RESP
                continue
            if line == DIVIDER:
                close_sections()
                continue
            if line == BLOCK_EDGE:
                finish_call()
                in_response_block = False
                continue
            if section == SECTION_RESP:
                mt = TRUNC_RE.search(line)
                if mt:
                    sec_trunc[SECTION_RESP] = int(mt.group(1))
                else:
                    sec_chars[SECTION_RESP] += len(line) + 1

    finish_call()

    # per-file summary
    lat = [c["response_time_ms"] for c in calls if c.get("response_time_ms") is not None]
    ts_first = parse_ts(calls[0]["req_ts"]) if calls and calls[0].get("req_ts") else None
    ts_last = None
    for c in reversed(calls):
        t = parse_ts(c.get("resp_ts") or c.get("req_ts") or "")
        if t:
            ts_last = t
            break
    span_s = (ts_last - ts_first).total_seconds() if ts_first and ts_last else None
    models = {}
    for c in calls:
        key = "{}|{}".format(c.get("endpoint_class"), c.get("model_name"))
        models[key] = models.get(key, 0) + 1
    return {
        "file": fname,
        "session_started": session_started.isoformat() if session_started else None,
        "n_calls": len(calls),
        "n_success": sum(1 for c in calls if c.get("success")),
        "n_failed": sum(1 for c in calls if c.get("success") is False),
        "models": models,
        "lat_ms_p50": statistics.median(lat) if lat else None,
        "lat_ms_mean": statistics.fmean(lat) if lat else None,
        "lat_ms_max": max(lat) if lat else None,
        "sum_llm_s": sum(lat) / 1000.0 if lat else 0.0,
        "wall_span_s": span_s,
        "llm_share": (sum(lat) / 1000.0 / span_s) if lat and span_s and span_s > 0 else None,
        "calls": calls,  # kept for aggregation, stripped before writing summary
    }


def pct(sorted_vals, p):
    if not sorted_vals:
        return None
    k = (len(sorted_vals) - 1) * p
    f = int(k)
    c = min(f + 1, len(sorted_vals) - 1)
    if f == c:
        return sorted_vals[f]
    return sorted_vals[f] + (sorted_vals[c] - sorted_vals[f]) * (k - f)


def aggregate(all_calls, key_fn, label):
    groups = {}
    for c in all_calls:
        groups.setdefault(key_fn(c), []).append(c)
    out = {}
    for k, cs in sorted(groups.items(), key=lambda kv: -len(kv[1])):
        lat = sorted(c["response_time_ms"] for c in cs if c.get("response_time_ms") is not None)
        n_fail = sum(1 for c in cs if c.get("success") is False)
        entry = {
            "n_calls": len(cs),
            "n_failed": n_fail,
            "fail_rate": round(n_fail / len(cs), 4) if cs else None,
        }
        if lat:
            entry.update({
                "lat_ms_mean": round(statistics.fmean(lat), 1),
                "lat_ms_p50": pct(lat, 0.50),
                "lat_ms_p90": pct(lat, 0.90),
                "lat_ms_p95": pct(lat, 0.95),
                "lat_ms_p99": pct(lat, 0.99),
                "lat_ms_max": lat[-1],
                "n_under_100ms": sum(1 for v in lat if v < 100),
                "n_over_30s": sum(1 for v in lat if v > 30000),
                "n_over_120s": sum(1 for v in lat if v > 120000),
                "sum_llm_hours": round(sum(lat) / 3600000.0, 2),
            })
            chars_user = [c.get("user_chars") or 0 for c in cs]
            chars_sys = [c.get("sys_chars") or 0 for c in cs]
            chars_resp = [c.get("resp_chars") or 0 for c in cs]
            entry["mean_prompt_chars"] = round(statistics.fmean(chars_sys) + statistics.fmean(chars_user), 0) if cs else None
            entry["mean_resp_chars"] = round(statistics.fmean(chars_resp), 0) if cs else None
        out[str(k)] = entry
    return {label: out}


def main():
    log_dirs = sys.argv[1:-1]
    out_dir = sys.argv[-1]
    os.makedirs(out_dir, exist_ok=True)

    files = []
    for d in log_dirs:
        for fn in sorted(os.listdir(d)):
            if fn.startswith("llm-communication") and fn.endswith(".log"):
                files.append(os.path.join(d, fn))
    print("Found {} log files".format(len(files)), file=sys.stderr)

    all_calls = []
    summaries = []
    with open(os.path.join(out_dir, "per_call.csv"), "w", newline="", encoding="utf-8") as fc:
        cw = csv.writer(fc)
        cw.writerow(["file", "req_id", "req_ts", "model_type", "model_name", "endpoint_class",
                     "max_tokens", "temperature", "success", "response_time_ms",
                     "sys_chars", "user_chars", "resp_chars", "error"])
        for i, path in enumerate(files):
            try:
                s = mine_file(path, cw)
            except Exception as e:
                print("ERROR parsing {}: {}".format(path, e), file=sys.stderr)
                continue
            for c in s["calls"]:
                c["file"] = s["file"]
            all_calls.extend(s["calls"])
            del s["calls"]
            summaries.append(s)
            if (i + 1) % 25 == 0:
                print("  parsed {}/{}".format(i + 1, len(files)), file=sys.stderr)

    with open(os.path.join(out_dir, "per_file_summary.csv"), "w", newline="", encoding="utf-8") as fs:
        sw = csv.writer(fs)
        sw.writerow(["file", "session_started", "n_calls", "n_success", "n_failed",
                     "models", "lat_ms_p50", "lat_ms_mean", "lat_ms_max",
                     "sum_llm_s", "wall_span_s", "llm_share"])
        for s in summaries:
            sw.writerow([s["file"], s["session_started"], s["n_calls"], s["n_success"],
                         s["n_failed"], json.dumps(s["models"]), s["lat_ms_p50"],
                         round(s["lat_ms_mean"], 1) if s["lat_ms_mean"] else None,
                         s["lat_ms_max"], round(s["sum_llm_s"], 1),
                         round(s["wall_span_s"], 1) if s["wall_span_s"] else None,
                         round(s["llm_share"], 3) if s["llm_share"] else None])

    agg = {}
    agg.update(aggregate(all_calls, lambda c: "{}|{}".format(c.get("endpoint_class"), c.get("model_name")), "by_backend_model"))
    agg.update(aggregate(all_calls, lambda c: (c.get("file") or "")[18:24], "by_month"))  # YYYYMM from filename
    agg.update(aggregate(
        [c for c in all_calls if (c.get("file") or "")[18:24] in ("202604", "202605")],
        lambda c: "{}|{}".format(c.get("endpoint_class"), c.get("model_name")),
        "d4_era_by_backend"))
    agg["total_calls"] = len(all_calls)

    with open(os.path.join(out_dir, "aggregate.json"), "w", encoding="utf-8") as fa:
        json.dump(agg, fa, indent=2)
    print(json.dumps(agg, indent=2))


if __name__ == "__main__":
    main()
