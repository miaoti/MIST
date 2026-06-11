#!/usr/bin/env python3
"""
Export post-execution Jaeger traces from a deployed TrainTicket / OTel cluster
into a directory that `mine_jaeger.py` can read for D5 ID-Resolvability.

Mirrors the proven HTTP query logic from RESTest's generated tests
(`Flow_Scenario_*.java::attachJaegerTrace`):

  GET <base>/traces?service=<svc>&start=<us>&end=<us>&limit=N
      → JSON list of trace summaries
  GET <base>/traces/{traceId}
      → JSON object containing the complete trace (every span)

Both endpoints return Jaeger's standard envelope: ``{"data": [trace, ...]}``.

The exporter:
  1. Normalises the user's start/end timestamps to microseconds, regardless
     of whether they passed seconds, milliseconds, or microseconds.
  2. Queries each requested service for traces in that window.
  3. Deduplicates by ``traceID``.
  4. Optionally fetches the FULL trace for each unique ID (`--full-traces`,
     default on) so the per-trace span list is complete — the summary
     responses can omit child spans.
  5. Writes a single consolidated ``post_execution_traces.json`` with the
     same envelope ``{"data": [trace, trace, ...]}`` that the mining script
     already understands.

Usage:
    python3 export_jaeger_traces.py \
        --base http://localhost:30005/jaeger/ui/api \
        --service ts-gateway-service \
        --start "$START_EPOCH" --end "$END_EPOCH" \
        --out logs/post_exec_traces/

After export, point the metrics pipeline at the output dir:
    ./debug/inputs/scripts/run_metrics.sh --trace-dir logs/post_exec_traces
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

# ---------------------------------------------------------------------------
# Pure helpers (no I/O — easy to unit test)
# ---------------------------------------------------------------------------

# Magnitudes that distinguish the unit a numeric epoch is in. Values smaller
# than ~10^11 are seconds, ~10^11..10^14 are milliseconds, ~10^14+ are
# microseconds. The heuristic is the same one the OpenTelemetry community
# uses when ingesting timestamps from heterogeneous sources.
SECONDS_UPPER  = 10 ** 11   # < this  → seconds
MILLIS_UPPER   = 10 ** 14   # < this  → milliseconds
                            # ≥ MILLIS_UPPER → microseconds


def normalise_to_micros(value: int | float | str) -> int:
    """Coerce a timestamp expressed in seconds, milliseconds, or microseconds
    to an integer count of microseconds since the Unix epoch.

    Auto-detection is by magnitude — see SECONDS_UPPER / MILLIS_UPPER.
    Negative, non-numeric, infinite, or NaN values raise ValueError.
    """
    import math
    if isinstance(value, bool):
        # bool subclasses int; reject explicitly so True/False don't quietly
        # become epoch 1/0 µs.
        raise ValueError(f"boolean timestamp not allowed: {value!r}")
    if isinstance(value, str):
        value = value.strip()
        if not value:
            raise ValueError("empty timestamp")
        try:
            num = int(value)
        except ValueError:
            num = float(value)
    else:
        num = value
    if isinstance(num, float) and not math.isfinite(num):
        raise ValueError(f"non-finite timestamp: {num!r}")
    if num < 0:
        raise ValueError(f"negative timestamp: {num!r}")
    n = float(num)
    if n < SECONDS_UPPER:
        return int(n * 1_000_000)
    if n < MILLIS_UPPER:
        return int(n * 1_000)
    return int(n)


def build_traces_url(base: str, service: str, start_us: int, end_us: int,
                     limit: int = 1500) -> str:
    """Build the /traces query URL exactly the way the Java generator does.

    The Java tests use `&` separators with no leading `?` collision and
    URL-encode the service name; we mirror that.
    """
    base = base.rstrip("/")
    params = {
        "service": service,
        "start": str(int(start_us)),
        "end": str(int(end_us)),
        "limit": str(int(limit)),
    }
    return f"{base}/traces?{urllib.parse.urlencode(params, quote_via=urllib.parse.quote)}"


def build_trace_detail_url(base: str, trace_id: str) -> str:
    """`GET <base>/traces/{traceId}` returns the full trace with all spans."""
    base = base.rstrip("/")
    if not trace_id:
        raise ValueError("trace_id is required")
    return f"{base}/traces/{urllib.parse.quote(trace_id, safe='')}"


def dedupe_traces(responses: Iterable[dict]) -> list[dict]:
    """Merge `{"data": [...]}` envelopes from multiple Jaeger responses,
    keeping the first occurrence of each unique traceID. Hardened against
    malformed payloads (e.g., `{"data": 42}` from a misbehaving proxy or
    auth page): non-list `data` is skipped silently rather than raising."""
    seen: set[str] = set()
    out: list[dict] = []
    for resp in responses:
        if not isinstance(resp, dict):
            continue
        data = resp.get("data")
        if not isinstance(data, (list, tuple)):
            # `None`, scalars, dicts, strings — none of these are valid
            # Jaeger trace lists. Skip cleanly so a single bad envelope does
            # not abort the whole run with a TypeError traceback.
            continue
        for trace in data:
            if not isinstance(trace, dict):
                continue
            tid = trace.get("traceID") or trace.get("traceId") or ""
            if not tid:
                # Treat traces without IDs as a fresh entry — they can still
                # contribute spans, but cannot be deduped.
                out.append(trace)
                continue
            if tid in seen:
                continue
            seen.add(tid)
            out.append(trace)
    return out


# ---------------------------------------------------------------------------
# I/O layer (a thin client that's easy to mock in tests)
# ---------------------------------------------------------------------------

class JaegerHttpError(RuntimeError):
    """Raised when Jaeger returns a non-2xx response after retries."""


@dataclass
class JaegerClient:
    """Minimal HTTP client over Jaeger's HTTP API.

    Tests inject a fake by passing a custom ``opener`` (callable that takes a
    URL, returns ``(status, body_bytes)``). No external HTTP library required;
    stdlib `urllib.request` is enough for this read-only workload.
    """

    base: str
    timeout: float = 30.0
    retries: int = 3
    backoff: float = 1.5
    backoff_cap_seconds: float = 30.0  # capped per-attempt sleep
    opener: object = None  # callable[[str], tuple[int, bytes]] — for tests

    def fetch(self, url: str) -> dict:
        """GET `url` with retry/backoff. Returns the parsed JSON body.

        Retry policy:
          - 5xx responses, network errors, and timeouts: retried with
            exponential backoff (capped at `backoff_cap_seconds`).
          - 4xx responses: raised immediately as `JaegerHttpError` — no point
            retrying a permanent client error.
          - 2xx with empty body: returns ``{}``.
          - Malformed JSON in a 2xx: raised as JaegerHttpError (no retry —
            the server is broken, not transient).
        """
        if self.retries < 1:
            raise ValueError(f"retries must be >= 1, got {self.retries}")
        last_err: Exception | None = None
        for attempt in range(1, self.retries + 1):
            try:
                if self.opener is not None:
                    status, body = self.opener(url)
                else:
                    req = urllib.request.Request(
                        url, method="GET",
                        headers={"User-Agent": "RESTest-D5-Exporter/1.0"})
                    try:
                        with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                            status = resp.status
                            body = resp.read()
                    except urllib.error.HTTPError as http_err:
                        # urlopen raises HTTPError for ALL non-2xx by default,
                        # so we must inspect .code here to mirror the explicit
                        # branch logic the FakeOpener path takes.
                        status = http_err.code
                        body = http_err.read() or b""
                if 200 <= status < 300:
                    if not body:
                        return {}
                    try:
                        return json.loads(body.decode("utf-8", errors="replace"))
                    except json.JSONDecodeError as e:
                        # Server replied 200 with garbage — almost certainly a
                        # misconfigured ingress (HTML login page, etc.). No
                        # point retrying.
                        raise JaegerHttpError(
                            f"{url} → HTTP {status} but body is not JSON: {e}") from e
                if 400 <= status < 500:
                    raise JaegerHttpError(f"{url} → HTTP {status}")
                if 500 <= status < 600:
                    last_err = JaegerHttpError(f"{url} → HTTP {status}")
                else:
                    # 1xx / 3xx / unknown: treat as transient.
                    last_err = JaegerHttpError(f"{url} → HTTP {status} (unexpected)")
            except (urllib.error.URLError, TimeoutError, ConnectionError, OSError) as e:
                last_err = e
            if attempt < self.retries:
                sleep_for = min(self.backoff ** attempt, self.backoff_cap_seconds)
                time.sleep(sleep_for)
        raise JaegerHttpError(f"Failed after {self.retries} attempts: {last_err}")

    def list_traces(self, service: str, start_us: int, end_us: int, limit: int) -> dict:
        return self.fetch(build_traces_url(self.base, service, start_us, end_us, limit))

    def get_trace(self, trace_id: str) -> dict:
        return self.fetch(build_trace_detail_url(self.base, trace_id))


# ---------------------------------------------------------------------------
# Orchestration
# ---------------------------------------------------------------------------

def collect_summaries(client: JaegerClient, services: list[str],
                      start_us: int, end_us: int, limit: int) -> list[dict]:
    """Query /traces once per requested service and return a deduplicated
    list of trace dicts. Errors from a single service do not abort the run —
    they're logged and the next service is tried. The aggregate may be empty
    if every service errors, in which case `main` reports a non-zero exit.
    """
    responses: list[dict] = []
    for svc in services:
        try:
            doc = client.list_traces(svc, start_us, end_us, limit)
            traces = (doc.get("data") or [])
            print(f"  service={svc!r}: {len(traces)} traces in window")
            responses.append(doc)
        except JaegerHttpError as e:
            print(f"  service={svc!r}: ERROR {e}", file=sys.stderr)
    return dedupe_traces(responses)


def expand_full(client: JaegerClient, summaries: list[dict],
                cap: int) -> list[dict]:
    """For each unique traceID, fetch /traces/{id} so the per-trace span
    list is complete. The bulk /traces endpoint truncates spans for some
    Jaeger versions (per the Java generator's documented note).

    `cap` bounds *expansion* calls, not output size: once we've upgraded
    `cap` traces, we still emit the remaining summaries (so downstream
    sees every trace ID in the window, just with shorter span lists for
    the trailing ones).
    """
    out: list[dict] = []
    fetched = 0
    cap_warned = False
    for trace in summaries:
        tid = trace.get("traceID") or trace.get("traceId") or ""
        if not tid:
            out.append(trace)
            continue
        if fetched >= cap:
            if not cap_warned:
                print(f"  expand: hit max-traces cap of {cap}; "
                      f"remaining traces emitted as summaries", file=sys.stderr)
                cap_warned = True
            out.append(trace)
            continue
        try:
            doc = client.get_trace(tid)
            inner = (doc.get("data") or [])
            if inner:
                out.append(inner[0])
                fetched += 1
            else:
                out.append(trace)  # fall back to summary
        except JaegerHttpError as e:
            print(f"  expand traceId={tid}: WARN {e} — using summary", file=sys.stderr)
            out.append(trace)
    return out


def write_output(traces: list[dict], out_dir: Path) -> Path:
    out_dir.mkdir(parents=True, exist_ok=True)
    path = out_dir / "post_execution_traces.json"
    payload = {"data": traces, "total": len(traces), "limit": 0, "offset": 0, "errors": None}
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    return path


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main(argv: list[str], client_factory=JaegerClient) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--base", default=os.environ.get(
        "JAEGER_BASE_URL", "http://localhost:30005/jaeger/ui/api"))
    p.add_argument("--service", action="append", default=None,
                   help="Repeatable. Service name to query. Default: "
                        "ts-gateway-service (recommended for distributed traces).")
    p.add_argument("--start", required=True, help="Epoch start (s, ms, or us — auto-detected)")
    p.add_argument("--end", required=True, help="Epoch end (s, ms, or us — auto-detected)")
    p.add_argument("--out", required=True, type=Path, help="Output directory")
    p.add_argument("--limit", type=int, default=1500,
                   help="Per-service /traces limit (default: 1500)")
    p.add_argument("--max-traces", type=int, default=5000,
                   help="Cap on total traces expanded (default: 5000)")
    p.add_argument("--full-traces", dest="full_traces", action="store_true", default=True,
                   help="Fetch /traces/{id} for each unique trace (default: ON)")
    p.add_argument("--no-full-traces", dest="full_traces", action="store_false",
                   help="Skip per-trace expansion; just save the summaries.")
    p.add_argument("--timeout", type=float, default=30.0)
    p.add_argument("--retries", type=int, default=3)
    args = p.parse_args(argv)

    services = args.service if args.service else ["ts-gateway-service"]

    try:
        start_us = normalise_to_micros(args.start)
        end_us = normalise_to_micros(args.end)
    except ValueError as e:
        print(f"error: bad timestamp: {e}", file=sys.stderr)
        return 2
    if end_us <= start_us:
        print(f"error: --end ({end_us}) must be > --start ({start_us})", file=sys.stderr)
        return 2

    print("================================================================")
    print(f" Jaeger base : {args.base}")
    print(f" services    : {services}")
    print(f" window      : {start_us} → {end_us} (μs)")
    print(f"               ({(end_us - start_us) / 1_000_000:.1f} seconds)")
    print(f" full-traces : {args.full_traces}")
    print(f" out         : {args.out}")
    print("================================================================")

    client = client_factory(base=args.base, timeout=args.timeout, retries=args.retries)
    summaries = collect_summaries(client, services, start_us, end_us, args.limit)
    print(f"  collected {len(summaries)} unique traces from {len(services)} service(s)")

    if not summaries:
        print("warning: no traces collected — nothing to export. "
              "Check --start/--end and that the services emitted traces.",
              file=sys.stderr)
        # Still write an empty file so downstream tools see "0 observed values"
        # rather than failing on a missing input.
        write_output([], args.out)
        return 1

    full = expand_full(client, summaries, args.max_traces) if args.full_traces else summaries
    out_path = write_output(full, args.out)
    total_spans = sum(len(t.get("spans") or []) for t in full)
    print(f"  wrote {len(full)} traces / {total_spans} spans → {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
