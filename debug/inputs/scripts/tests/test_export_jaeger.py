"""Tests for export_jaeger_traces — Jaeger HTTP-API exporter for D5."""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import export_jaeger_traces as ej  # noqa: E402


# --- normalise_to_micros -----------------------------------------------

def test_normalise_seconds():
    """Epoch in seconds (10-digit) becomes microseconds."""
    # 2026-04-30 00:00:00 UTC ≈ 1777334400 seconds
    assert ej.normalise_to_micros(1777334400) == 1_777_334_400_000_000
    assert ej.normalise_to_micros("1777334400") == 1_777_334_400_000_000


def test_normalise_milliseconds():
    """13-digit values are interpreted as milliseconds."""
    assert ej.normalise_to_micros(1_777_334_400_000) == 1_777_334_400_000_000
    assert ej.normalise_to_micros("1777334400000") == 1_777_334_400_000_000


def test_normalise_microseconds():
    """16-digit values are interpreted as microseconds (no scaling)."""
    assert ej.normalise_to_micros(1_777_334_400_000_000) == 1_777_334_400_000_000


def test_normalise_rejects_negative():
    try:
        ej.normalise_to_micros(-1)
    except ValueError:
        return
    raise AssertionError("expected ValueError for negative")


def test_normalise_rejects_empty_string():
    try:
        ej.normalise_to_micros("")
    except ValueError:
        return
    raise AssertionError("expected ValueError for empty string")


def test_normalise_accepts_float_seconds():
    """Bash `date +%s.%N` produces sub-second precision."""
    out = ej.normalise_to_micros("1777334400.5")
    assert out == 1_777_334_400_500_000


def test_normalise_rejects_inf_and_nan():
    """REVIEW finding #3: float('inf') previously raised an uncaught
    OverflowError. Both inf and NaN must surface as ValueError."""
    for bad in ("inf", "-inf", "nan", "Infinity", "NaN"):
        try:
            ej.normalise_to_micros(bad)
        except ValueError:
            continue
        raise AssertionError(f"expected ValueError for {bad!r}")


def test_normalise_rejects_bool():
    """bool inherits from int. Passing True/False must not silently
    become epoch 1/0 microseconds."""
    for bad in (True, False):
        try:
            ej.normalise_to_micros(bad)
        except ValueError:
            continue
        raise AssertionError(f"expected ValueError for {bad!r}")


# --- URL builders -------------------------------------------------------

def test_build_traces_url_basic():
    url = ej.build_traces_url(
        "http://example.com/jaeger/ui/api", "ts-gateway-service", 100, 200, limit=42)
    assert url.startswith("http://example.com/jaeger/ui/api/traces?")
    assert "service=ts-gateway-service" in url
    assert "start=100" in url
    assert "end=200" in url
    assert "limit=42" in url


def test_build_traces_url_strips_trailing_slash():
    url = ej.build_traces_url(
        "http://example.com/api/", "svc", 1, 2, 3)
    assert url.startswith("http://example.com/api/traces?")
    # No double slash
    assert "//traces" not in url


def test_build_traces_url_encodes_service_name():
    """Service names with special chars are URL-encoded."""
    url = ej.build_traces_url("http://x", "ts/wait order", 1, 2, 3)
    assert "ts%2Fwait%20order" in url or "ts%2Fwait+order" in url or "ts/wait+order" in url
    # The exact encoding depends on quote_via; just verify the unencoded
    # space and slash never appear in the URL.
    assert "ts/wait order" not in url
    assert " " not in url


def test_build_trace_detail_url():
    url = ej.build_trace_detail_url("http://example.com/api", "abc123")
    assert url == "http://example.com/api/traces/abc123"


def test_build_trace_detail_url_rejects_empty_id():
    try:
        ej.build_trace_detail_url("http://x", "")
    except ValueError:
        return
    raise AssertionError("expected ValueError for empty trace_id")


# --- dedupe_traces -----------------------------------------------------

def test_dedupe_keeps_first_occurrence():
    r1 = {"data": [{"traceID": "A", "spans": [1]}, {"traceID": "B", "spans": [2]}]}
    r2 = {"data": [{"traceID": "B", "spans": [99]}, {"traceID": "C", "spans": [3]}]}
    out = ej.dedupe_traces([r1, r2])
    assert [t["traceID"] for t in out] == ["A", "B", "C"]
    # B's first occurrence wins
    b = [t for t in out if t["traceID"] == "B"][0]
    assert b["spans"] == [2]


def test_dedupe_handles_missing_data_key():
    out = ej.dedupe_traces([{"errors": "x"}, {}])
    assert out == []


def test_dedupe_keeps_traces_without_id():
    """Traces without traceID are kept as-is (cannot be deduped)."""
    r = {"data": [{"spans": [1]}, {"spans": [2]}]}
    out = ej.dedupe_traces([r])
    assert len(out) == 2


def test_dedupe_handles_traceId_camelcase():
    """Some Jaeger versions emit traceId (lower-case d). We accept both."""
    r1 = {"data": [{"traceId": "A", "spans": [1]}]}
    r2 = {"data": [{"traceID": "A", "spans": [2]}]}
    out = ej.dedupe_traces([r1, r2])
    assert len(out) == 1


def test_dedupe_handles_scalar_data():
    """REVIEW finding #2: misbehaving proxy might return `{"data": 42}`
    or `{"data": true}`. dedupe_traces must skip cleanly (not raise)."""
    assert ej.dedupe_traces([{"data": 42}]) == []
    assert ej.dedupe_traces([{"data": True}]) == []
    assert ej.dedupe_traces([{"data": "string"}]) == []
    # And mixed-shape envelopes still extract good traces.
    out = ej.dedupe_traces([{"data": 42}, {"data": [{"traceID": "T1"}]}])
    assert [t["traceID"] for t in out] == ["T1"]


# --- JaegerClient with injected opener (no real HTTP) -----------------

class FakeOpener:
    """Records every URL fetched and returns canned responses."""
    def __init__(self, responses: dict[str, tuple[int, bytes]]):
        self.responses = responses
        self.calls: list[str] = []
        self.fail_count: dict[str, int] = {}

    def __call__(self, url: str) -> tuple[int, bytes]:
        self.calls.append(url)
        if url in self.responses:
            return self.responses[url]
        return 404, b'{"errors":"not found"}'


def test_client_fetch_success():
    body = json.dumps({"data": [{"traceID": "X"}]}).encode()
    fake = FakeOpener({"http://x/traces?service=s&start=0&end=1&limit=1": (200, body)})
    c = ej.JaegerClient(base="http://x", retries=1, opener=fake)
    resp = c.fetch("http://x/traces?service=s&start=0&end=1&limit=1")
    assert resp == {"data": [{"traceID": "X"}]}
    assert len(fake.calls) == 1


def test_client_fetch_retries_on_5xx():
    """A transient 503 is retried; eventual success returns the body."""
    body = json.dumps({"data": []}).encode()
    attempts = {"n": 0}
    def opener(url):
        attempts["n"] += 1
        if attempts["n"] < 3:
            return 503, b"unavailable"
        return 200, body
    c = ej.JaegerClient(base="http://x", retries=4, backoff=1.0, opener=opener)
    resp = c.fetch("http://x/whatever")
    assert resp == {"data": []}
    assert attempts["n"] == 3


def test_client_fetch_raises_after_max_retries():
    def opener(url): return 500, b"oops"
    c = ej.JaegerClient(base="http://x", retries=2, backoff=1.0, opener=opener)
    try:
        c.fetch("http://x/y")
    except ej.JaegerHttpError:
        return
    raise AssertionError("expected JaegerHttpError")


def test_client_fetch_raises_on_4xx_immediately():
    """4xx is non-retryable — fail fast."""
    attempts = {"n": 0}
    def opener(url):
        attempts["n"] += 1
        return 404, b"not found"
    c = ej.JaegerClient(base="http://x", retries=5, backoff=1.0, opener=opener)
    try:
        c.fetch("http://x/y")
    except ej.JaegerHttpError:
        # Critical: 4xx must NOT be retried.
        assert attempts["n"] == 1, f"4xx should fail fast, but was retried {attempts['n']} times"
        return
    raise AssertionError("expected JaegerHttpError on 4xx")


def test_client_fetch_real_urlopen_path_treats_4xx_as_fast_fail(monkeypatch_compat=None):
    """REVIEW finding #1: `urllib.request.urlopen` raises HTTPError for any
    non-2xx response, which the previous catch list silently swallowed and
    retried. Verify the dedicated HTTPError handling path. We mock urlopen
    by name so this exercises the real-HTTP branch (not the opener=... shim).
    """
    import urllib.error
    import urllib.request as urllib_request

    attempts = {"n": 0}

    def fake_urlopen(req, timeout=None):
        attempts["n"] += 1
        raise urllib.error.HTTPError(req.full_url, 404, "not found", {}, None)

    real = urllib_request.urlopen
    urllib_request.urlopen = fake_urlopen
    try:
        c = ej.JaegerClient(base="http://x", retries=3, backoff=0.01, opener=None)
        try:
            c.fetch("http://x/y")
        except ej.JaegerHttpError:
            assert attempts["n"] == 1, f"real-HTTP 4xx must not retry, got {attempts['n']} attempts"
            return
        raise AssertionError("expected JaegerHttpError on real-HTTP 4xx")
    finally:
        urllib_request.urlopen = real


def test_client_fetch_real_urlopen_path_retries_5xx():
    """REVIEW finding #1 follow-up: 5xx via the real urllib.request path
    must still be retried (the bug fix shouldn't break the retry case)."""
    import urllib.error
    import urllib.request as urllib_request

    attempts = {"n": 0}
    body = json.dumps({"data": []}).encode()

    def fake_urlopen(req, timeout=None):
        attempts["n"] += 1
        if attempts["n"] < 3:
            raise urllib.error.HTTPError(req.full_url, 503, "unavailable", {}, None)
        # On 3rd attempt return a 200 via context manager
        class _Resp:
            status = 200
            def __enter__(self_): return self_
            def __exit__(self_, *a): pass
            def read(self_): return body
        return _Resp()

    real = urllib_request.urlopen
    urllib_request.urlopen = fake_urlopen
    try:
        c = ej.JaegerClient(base="http://x", retries=4, backoff=0.01, opener=None)
        resp = c.fetch("http://x/y")
        assert resp == {"data": []}
        assert attempts["n"] == 3
    finally:
        urllib_request.urlopen = real


def test_client_fetch_200_garbage_body_does_not_retry():
    """A 200 with non-JSON body is a server-side bug; no point retrying."""
    attempts = {"n": 0}
    def opener(url):
        attempts["n"] += 1
        return 200, b"<html>login page</html>"
    c = ej.JaegerClient(base="http://x", retries=5, backoff=0.01, opener=opener)
    try:
        c.fetch("http://x/y")
    except ej.JaegerHttpError:
        assert attempts["n"] == 1
        return
    raise AssertionError("expected JaegerHttpError on garbage body")


def test_client_fetch_backoff_capped():
    """REVIEW finding #4: backoff exponent must be capped so a misconfigured
    --retries doesn't translate into multi-minute waits."""
    sleeps = []
    import time as _t
    real_sleep = _t.sleep
    _t.sleep = lambda s: sleeps.append(s)
    try:
        c = ej.JaegerClient(base="http://x", retries=5, backoff=10.0,
                            backoff_cap_seconds=0.5,
                            opener=lambda u: (503, b"x"))
        try:
            c.fetch("http://x/y")
        except ej.JaegerHttpError:
            pass
    finally:
        _t.sleep = real_sleep
    # Every recorded sleep must be ≤ the cap.
    assert sleeps and all(s <= 0.5 + 1e-9 for s in sleeps), f"unexpected sleeps: {sleeps}"


def test_client_rejects_zero_retries():
    """A bogus --retries 0 should fail fast with a clear ValueError, not
    produce 'Failed after 0 attempts: None'."""
    c = ej.JaegerClient(base="http://x", retries=0, opener=lambda u: (200, b"{}"))
    try:
        c.fetch("http://x/y")
    except ValueError:
        return
    raise AssertionError("expected ValueError for retries < 1")


# --- collect_summaries + expand_full --------------------------------

def test_collect_summaries_aggregates_per_service():
    body_a = json.dumps({"data": [{"traceID": "T1"}]}).encode()
    body_b = json.dumps({"data": [{"traceID": "T2"}, {"traceID": "T1"}]}).encode()

    def opener(url):
        if "service=svc-a" in url:
            return 200, body_a
        if "service=svc-b" in url:
            return 200, body_b
        return 404, b"x"

    c = ej.JaegerClient(base="http://x", retries=1, opener=opener)
    out = ej.collect_summaries(c, ["svc-a", "svc-b"], 0, 1, 100)
    assert sorted(t["traceID"] for t in out) == ["T1", "T2"]


def test_collect_summaries_resilient_to_per_service_failure():
    """If one service errors, the other still contributes."""
    def opener(url):
        if "service=ok" in url:
            return 200, json.dumps({"data": [{"traceID": "K"}]}).encode()
        return 500, b"down"
    c = ej.JaegerClient(base="http://x", retries=1, opener=opener)
    out = ej.collect_summaries(c, ["ok", "down"], 0, 1, 100)
    assert [t["traceID"] for t in out] == ["K"]


def test_expand_full_replaces_summary_with_complete():
    summary = {"traceID": "T1", "spans": [{"id": "s1"}]}  # 1-span summary
    full = {"data": [{"traceID": "T1", "spans": [{"id": "s1"}, {"id": "s2"}, {"id": "s3"}]}]}

    def opener(url):
        if url.endswith("/traces/T1"):
            return 200, json.dumps(full).encode()
        return 404, b"x"

    c = ej.JaegerClient(base="http://x", retries=1, opener=opener)
    out = ej.expand_full(c, [summary], cap=10)
    assert len(out) == 1
    assert len(out[0]["spans"]) == 3


def test_expand_full_falls_back_on_per_trace_error():
    """If /traces/{id} fails for one trace, expand_full keeps the summary."""
    summary = {"traceID": "T1", "spans": [{"id": "s1"}]}

    def opener(url):
        return 500, b"oops"

    c = ej.JaegerClient(base="http://x", retries=1, opener=opener)
    out = ej.expand_full(c, [summary], cap=10)
    assert len(out) == 1
    assert out[0]["traceID"] == "T1"
    assert out[0]["spans"] == [{"id": "s1"}]


def test_expand_full_respects_cap():
    summaries = [{"traceID": f"T{i}", "spans": []} for i in range(50)]

    def opener(url):
        tid = url.rsplit("/", 1)[-1]
        return 200, json.dumps({"data": [{"traceID": tid, "spans": [{"id": tid}]}]}).encode()

    c = ej.JaegerClient(base="http://x", retries=1, opener=opener)
    out = ej.expand_full(c, summaries, cap=5)
    # All 50 traces are emitted, but only the first 5 are upgraded; the rest
    # fall back to summaries (which still have the right traceID).
    assert len(out) == 50


# --- write_output ----------------------------------------------------

def test_write_output_creates_file_and_envelope(tmp_path):
    traces = [{"traceID": "T1", "spans": []}]
    p = ej.write_output(traces, tmp_path / "out")
    assert p.is_file()
    doc = json.loads(p.read_text())
    assert doc["data"] == traces
    assert doc["total"] == 1
    # Confirm mine_jaeger.iter_traces can read it.
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
    from mine_jaeger import iter_traces
    seen = list(iter_traces(p))
    assert len(seen) == 1
    assert seen[0]["traceID"] == "T1"


# --- main: end-to-end with mock client -----------------------------

def test_main_end_to_end_with_mock_client(tmp_path):
    """Drive `main()` through a fake JaegerClient — verifies argument parsing
    and orchestrator wiring without touching real HTTP."""
    summary_resp = json.dumps({"data": [
        {"traceID": "T1", "spans": [{"id": "a"}]},
        {"traceID": "T2", "spans": [{"id": "b"}]},
    ]}).encode()
    detail_responses = {
        "T1": json.dumps({"data": [{"traceID": "T1", "spans": [{"id": "a"}, {"id": "a2"}]}]}).encode(),
        "T2": json.dumps({"data": [{"traceID": "T2", "spans": [{"id": "b"}, {"id": "b2"}]}]}).encode(),
    }

    def opener(url):
        if "/traces?" in url and "start=" in url:
            return 200, summary_resp
        for tid, body in detail_responses.items():
            if url.endswith(f"/traces/{tid}"):
                return 200, body
        return 404, b"x"

    captured_clients = []
    def factory(**kwargs):
        # `main` passes retries / timeout / base in kwargs; we override the
        # opener (test injection) but leave the rest as-is.
        kwargs["opener"] = opener
        c = ej.JaegerClient(**kwargs)
        captured_clients.append(c)
        return c

    out_dir = tmp_path / "post_exec"
    rc = ej.main([
        "--base", "http://example.com/api",
        "--service", "svc-x",
        "--start", "1777334400",
        "--end", "1777334500",
        "--out", str(out_dir),
        "--retries", "1",
    ], client_factory=factory)
    assert rc == 0
    out_file = out_dir / "post_execution_traces.json"
    assert out_file.is_file()
    doc = json.loads(out_file.read_text())
    assert doc["total"] == 2
    ids = sorted(t["traceID"] for t in doc["data"])
    assert ids == ["T1", "T2"]
    # T1 was expanded — should have 2 spans (vs the 1-span summary).
    t1 = next(t for t in doc["data"] if t["traceID"] == "T1")
    assert len(t1["spans"]) == 2


def test_main_rejects_inverted_window(tmp_path):
    def factory(**kw):
        kw["opener"] = lambda u: (200, b"{}")
        return ej.JaegerClient(**kw)
    rc = ej.main([
        "--base", "http://x",
        "--service", "s",
        "--start", "200",
        "--end", "100",
        "--out", str(tmp_path / "o"),
    ], client_factory=factory)
    assert rc == 2  # bad-args exit


def test_main_writes_empty_file_when_no_traces(tmp_path):
    """When the window has no traces, we still produce an empty envelope so
    downstream mine_jaeger.py doesn't choke on a missing file."""
    def opener(url):
        return 200, json.dumps({"data": []}).encode()
    def factory(**kw):
        kw["opener"] = opener
        return ej.JaegerClient(**kw)
    rc = ej.main([
        "--base", "http://x",
        "--service", "s",
        "--start", "1777334400",
        "--end", "1777334500",
        "--out", str(tmp_path / "o"),
        "--retries", "1",
    ], client_factory=factory)
    # Empty result → exit code 1 (warning), but file is still written.
    assert rc == 1
    out_file = tmp_path / "o" / "post_execution_traces.json"
    assert out_file.is_file()
    doc = json.loads(out_file.read_text())
    assert doc["data"] == []
