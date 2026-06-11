# Code Review — Jaeger Trace Exporter (D5 post-execution)
Date: 2026-04-26

## Summary

The exporter is in good shape overall: 28/28 of its own tests pass, the full suite (99/99) passes, the URL/HTTP wire format matches the Java reference (modulo query-parameter order), the time-format auto-detection works for the realistic ranges, and the output envelope round-trips through `mine_jaeger.iter_traces`. The orchestrator wiring is mostly correct.

That said, there are two real correctness defects and several latent ones. The most important: **on the real-HTTP path, `JaegerClient.fetch` retries 4xx responses up to `--retries` times** because `urllib.request.urlopen` raises `HTTPError` for any non-2xx, and the catch list (`URLError, …, OSError`) sucks them all in indiscriminately — the "fail fast on 4xx" branch is only hit by the test's `FakeOpener`. So the test suite green-checks behaviour the code never actually executes against a real server. The second: `dedupe_traces` raises `TypeError` if a Jaeger envelope's `data` field is a non-iterable scalar (e.g., `null` is fine, but `42` or other malformed payload crashes the run), and there is no test for malformed input shapes. The remaining findings are smaller — boundary inputs, misleading error messages, unbounded backoff, missing test coverage, and a couple of orchestrator UX gaps.

**Top three issues, ranked by severity:**

1. **High — Real-HTTP path silently retries 4xx errors** (`export_jaeger_traces.py:165-190`). The unit tests pass via a fake opener that returns `(status, body)` tuples and trips the explicit `if 500 <= status < 600` branch. But `urllib.request.urlopen` raises `urllib.error.HTTPError` (a subclass of `URLError` and therefore `OSError`) for *any* non-2xx response, so in production a 404/410/422 follows the retry loop just like a 503. Repro inline below; the test suite cannot detect this because every test uses the fake opener.
2. **Medium — `dedupe_traces` crashes on malformed `data` values** (`export_jaeger_traces.py:126`). `for trace in resp.get("data") or []` works when `data` is a list, tuple, dict, string, or `None`, but raises `TypeError: 'int' object is not iterable` when `data` is any non-iterable scalar. Real Jaeger should never emit this, but a misconfigured proxy or auth page returning `{"data": 0}` would crash an entire export run rather than reporting a clean error.
3. **Medium — `normalise_to_micros` does not validate against `inf`/`NaN` consistently and partly uses `OverflowError`** (`export_jaeger_traces.py:66-90`, `main` at lines 298-303). `--start NaN` exits cleanly with code 2 (`ValueError` caught); `--start inf` crashes with an uncaught `OverflowError` (exit 1, traceback to stderr). Float-second precision worse than 1 µs is silently floored without warning. Plus the boundary "treat as seconds vs ms" condition `n < SECONDS_UPPER` excludes `n == 10**11` from seconds — which is fine for real epochs in 2026, but the boundary is documented as "approximate" and is undocumented in the help text.

**Verdict: ship after small fixes.** The corrections are short and well-scoped — broaden the 4xx fast-fail in `fetch`, harden `dedupe_traces` against scalar `data`, tighten the timestamp validator, cap the backoff, and add three or four negative-path tests. None of these block the headline use case.

---

## Findings

### Finding #1 — Real-HTTP path silently retries 4xx [High]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:165-190`
**Issue:** `JaegerClient.fetch` only fast-fails on a 4xx when the response goes through the `opener` injection path (which returns `(status, body)`). On the real path (`urllib.request.urlopen`), Python raises `urllib.error.HTTPError` for *any* non-2xx response by default. `HTTPError` is a subclass of `URLError`, which is in the `except` list — so it's caught, `last_err` is set, and the request is retried up to `self.retries` times.
**Evidence:** Direct repro:
```python
import urllib.error, unittest.mock as um, sys
sys.path.insert(0, "debug/inputs/scripts")
import export_jaeger_traces as ej
attempts = [0]
def fake_urlopen(req, timeout):
    attempts[0] += 1
    raise urllib.error.HTTPError(req.full_url, 404, "not found", {}, None)
c = ej.JaegerClient(base="http://x", retries=3, backoff=0.01)
with um.patch("urllib.request.urlopen", fake_urlopen):
    try: c.fetch("http://x/y")
    except ej.JaegerHttpError as e: print(attempts[0], e)
# → 3, "Failed after 3 attempts: HTTP Error 404: not found"
```
**Impact:** A misconfigured base URL (typo, wrong path prefix, expired token returning 401) takes 3× longer to fail than necessary, with `(retries-1)` extra wasted retry sleeps in between. With the default `backoff=1.5` and `retries=3` that's `1.5 + 2.25 = 3.75 s` of spurious waiting per misconfigured query. The test suite cannot catch this because the fake-opener path numerically inspects status and never raises HTTPError; the explicit `if 500 <= status < 600` branch in lines 182-185 is therefore *dead code* in production.
**Suggested fix:** Either install a custom `HTTPDefaultErrorHandler` that returns the response object instead of raising, or catch `HTTPError` separately and decide based on `e.code`:
```python
except urllib.error.HTTPError as e:
    if 400 <= e.code < 500:
        raise JaegerHttpError(f"{url} → HTTP {e.code}") from e
    last_err = e  # 5xx: retry
except (urllib.error.URLError, TimeoutError, ConnectionError, OSError, json.JSONDecodeError) as e:
    last_err = e
```
**Test gap:** Yes — there is no test for the real `urllib.request` path. Add a test that monkey-patches `urllib.request.urlopen` to raise `HTTPError(404)` and asserts a single attempt.

---

### Finding #2 — `dedupe_traces` crashes on a non-iterable `data` value [Medium]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:126`
**Issue:** `for trace in resp.get("data") or []:` works fine when `data` is missing, `None`, an empty list, a dict (iterates keys), or a string (iterates characters — each rejected by the `isinstance(trace, dict)` guard). It raises `TypeError: 'int' object is not iterable` when `data` is a number, bool, or any other non-iterable scalar.
**Evidence:**
```python
>>> ej.dedupe_traces([{"data": 42}])
TypeError: 'int' object is not iterable
```
**Impact:** Real Jaeger never emits `{"data": 42}`, but a misbehaving reverse proxy, an auth page, or an HTML 200 from a misconfigured ingress could yield arbitrary payloads. With `data` being a non-iterable scalar (or e.g., `True`), the entire run aborts with an unhelpful traceback rather than a controlled error message. Hardening is cheap.
**Suggested fix:**
```python
data = resp.get("data")
if not isinstance(data, (list, tuple)):
    continue
for trace in data:
    ...
```
**Test gap:** Yes. Add a `test_dedupe_handles_scalar_data` that passes `{"data": 42}` and `{"data": True}` and asserts an empty result rather than a traceback.

---

### Finding #3 — `--start inf` produces an uncaught traceback [Medium]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:66-90`, `main` at lines 298-303
**Issue:** `normalise_to_micros` does `int(n)` after the magnitude branches. For `float('inf')`, `int(float('inf'))` raises `OverflowError`, which is *not* caught by `main`'s `except ValueError` block. Result: a Python traceback to the user, exit code 1, and no graceful error. `NaN` raises `ValueError` (correctly caught). The asymmetry is surprising.
**Evidence:**
```
$ python3 export_jaeger_traces.py --start inf --end 2 --out /tmp/x
Traceback (most recent call last):
  ...
OverflowError: cannot convert float infinity to integer
```
**Impact:** Low (no realistic user types `inf`), but the CLI should fail gracefully on bad input. Also, `--start` accepting bool literals (`True`/`False`) yields a quiet `1`/`0` because `isinstance(True, int)` is True; not a bug per se but worth documenting.
**Suggested fix:** Reject non-finite floats explicitly inside `normalise_to_micros`:
```python
import math
if isinstance(num, float) and not math.isfinite(num):
    raise ValueError(f"non-finite timestamp: {num!r}")
```
Or, alternatively, broaden `main`'s except to `(ValueError, OverflowError)`.
**Test gap:** Yes. Add tests for `inf`, `-inf`, `nan` (the latter is partially covered via "rejects negative" but not specifically NaN).

---

### Finding #4 — Backoff is unbounded [Medium]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:188-189`
**Issue:** `time.sleep(self.backoff ** attempt)`. With the defaults (`backoff=1.5, retries=3`), max sleep is 1.5² = 2.25 s — fine. But the parameters are user-configurable via `--retries` (no upper bound) and `backoff` (constructor only, but a future caller could pass any value). For `retries=10, backoff=1.5` the cumulative wait is ~112 s; for `retries=10, backoff=2.0` it's ~1023 s (≈17 min).
**Evidence:** Cumulative pre-final-attempt sleep for `backoff=1.5, retries=10`:
```
attempt 1: 1.50s
attempt 2: 2.25s
attempt 3: 3.38s
...
attempt 9: 38.44s
total: 112.33s
```
**Impact:** A user passing `--retries 10` against a service in a maintenance loop would hang for nearly 2 minutes per query. With one query per service per run and N traces in the expansion phase, a partial outage could pause an entire experiment for tens of minutes.
**Suggested fix:** Cap each sleep at, say, 30 seconds, or expose a `max_backoff` knob:
```python
sleep_s = min(self.backoff ** attempt, 30.0)
time.sleep(sleep_s)
```
Also document that the *expected* policy is exponential-with-cap, not unbounded.
**Test gap:** None — covered indirectly by the existing retry tests, but no explicit assertion that sleeps are capped. A test could set `backoff=2`, `retries=4`, monkey-patch `time.sleep` to record args, and assert `max(args) <= cap`.

---

### Finding #5 — `JSONDecodeError` on a 200 body causes pointless retries [Medium]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:181, 186`
**Issue:** When the server returns a 200 status but a malformed JSON body, `json.loads` raises `JSONDecodeError` *inside* the `if 200 <= status < 300` branch. The `except` clause catches it, sets `last_err`, and the loop retries up to `--retries` times. Re-asking a server that just sent garbage to send the same garbage again is wasteful.
**Evidence:**
```python
def bad_json(url): return 200, b"{not-json"
c = ej.JaegerClient(base="http://x", retries=3, backoff=0.01, opener=bad_json)
c.fetch("http://x/y")
# → JaegerHttpError after 3 attempts (with 2 spurious sleeps)
```
**Impact:** Low — real Jaeger doesn't emit malformed JSON for a 200, so this is a robustness issue rather than a correctness one. But the retries waste time and produce a misleading message ("Failed after 3 attempts").
**Suggested fix:** Move the JSON parse outside the retry loop, or fail fast on `JSONDecodeError` from a 2xx body:
```python
if 200 <= status < 300:
    if not body:
        return {}
    try:
        return json.loads(body.decode("utf-8", errors="replace"))
    except json.JSONDecodeError as e:
        raise JaegerHttpError(f"{url} → 2xx with malformed JSON: {e}") from e
```
Then drop `JSONDecodeError` from the retry-able exception list.
**Test gap:** Yes. Add a test asserting that malformed JSON from a 200 surfaces as a single attempt, not N.

---

### Finding #6 — `retries=0` produces the message "Failed after 0 attempts: None" [Low]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:168-190`
**Issue:** `for attempt in range(1, self.retries + 1):` is `range(1, 1)` = empty when `retries=0`, so the loop body never executes, `last_err` stays `None`, and the final `raise JaegerHttpError(f"Failed after {self.retries} attempts: {last_err}")` produces:
```
Failed after 0 attempts: None
```
**Impact:** Trivial — `retries=0` is unusual, but the message is clearly broken. argparse does not validate `--retries >= 1`.
**Suggested fix:** Either reject `--retries < 1` in argparse with `type=int, choices=range(1, 100)` style validation, or normalize `retries < 1` to 1 internally. Or just produce a clearer message ("retries must be >= 1").
**Test gap:** No test covers `retries=0`.

---

### Finding #7 — Empty `--service` value produces a query that's silently meaningless [Low]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:202-219, 296`
**Issue:** `--service ''` passes through `services = args.service if args.service else ["ts-gateway-service"]` because `args.service` is `[""]`, which is truthy. Then `build_traces_url` happily constructs `…/traces?service=&start=…&end=…&limit=1500`. Jaeger usually returns 400 or an empty data list; the user sees `service='': 0 traces in window`. Not a crash, but a quiet "0 results" with no diagnostic.
**Evidence:**
```python
>>> ej.build_traces_url("http://x", "", 1, 2, 3)
'http://x/traces?service=&start=1&end=2&limit=3'
```
**Suggested fix:** Reject empty service names early:
```python
services = [s for s in (args.service or ["ts-gateway-service"]) if s.strip()]
if not services:
    print("error: --service must be non-empty", file=sys.stderr)
    return 2
```
**Test gap:** Yes.

---

### Finding #8 — Orchestrator unconditionally overrides `TRACE_DIR` even on exporter failure [Low]
**File:** `debug/inputs/scripts/run_metrics.sh:188-214`
**Issue:**
```bash
"$PYTHON_BIN" "$SCRIPT_DIR/export_jaeger_traces.py" "${EXPORTER_ARGS[@]}" || \
    echo "  warning: Jaeger exporter failed; D5 will use whatever was already in $POST_EXEC_TRACE_DIR" >&2
TRACE_DIR="$POST_EXEC_TRACE_DIR"   # ← always set, even on failure
```
The `|| echo` swallows the exit code, so subsequent `set -e` doesn't fire. `TRACE_DIR` is then *always* overridden to `$POST_EXEC_TRACE_DIR`, even if the exporter never produced any output (because, e.g., bad args caused an exit 2 before any file write). The downstream `if [[ -d "$TRACE_DIR" ]]` check passes if the directory exists from a *previous* run, so `mine_jaeger` happily mines a stale post-exec file.
**Impact:** Re-running with a different window after an early-exit failure can silently re-mine the previous window's traces.
**Suggested fix:** Track the exporter's exit status and skip the override on failure:
```bash
if "$PYTHON_BIN" "$SCRIPT_DIR/export_jaeger_traces.py" "${EXPORTER_ARGS[@]}"; then
    TRACE_DIR="$POST_EXEC_TRACE_DIR"
else
    echo "  warning: Jaeger exporter failed; falling back to $TRACE_DIR" >&2
fi
```
**Test gap:** No shell-level tests for the orchestrator. Manual test sufficient.

---

### Finding #9 — `expand_full` is a hot loop with no rate limiting or batching [Low]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:222-259`
**Issue:** The exporter's expansion phase issues *one* HTTP request per unique trace ID, sequentially. For 1,000 traces that's 1,000 round-trips to the Jaeger UI. There's no batch endpoint in Jaeger, so this is unavoidable, but the implementation is fully sequential — no concurrency, no progress reporting beyond per-cap warnings, and no way to abort early on Ctrl-C without losing the partial result. The `--max-traces` cap (default 5000) at least bounds the worst-case cost.
**Impact:** For typical research-scale workloads (≤ a few hundred traces) this is fine. For larger windows, expansion can dominate the run time. A KeyboardInterrupt mid-expansion loses every trace fetched so far because `write_output` runs only after the loop completes.
**Suggested fix:** None required for the headline use case, but worth tracking. If/when needed: a `concurrent.futures.ThreadPoolExecutor` with a small worker pool (e.g., 4-8) gives a 4-8× speedup with negligible code changes. Optional: write a checkpoint every 100 traces.
**Test gap:** N/A — performance optimization, not correctness.

---

### Finding #10 — Pagination is silently truncated at `--limit` [Low]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:192-194, 203-219`
**Issue:** `client.list_traces(svc, start, end, limit)` queries a single window with a single limit. If the window contains more traces than `--limit`, the extras are silently dropped. There is no warning, no sub-window split, no "I hit the limit" detection (Jaeger doesn't return a count of available results — only the truncated array). The default limit of 1500 is high enough for a typical 5-minute test run, but users who run wider windows could lose data and not notice.
**Impact:** Low for the headline use case (a single test execution typically produces < 1500 traces total). Could be material for batched / parallel test runs. No way for the exporter to detect truncation without paginating.
**Suggested fix:** Print a warning when `len(traces) == limit`:
```python
if len(traces) >= limit:
    print(f"  warn: hit limit={limit} for service={svc!r}; some traces may be missing. "
          f"Try a smaller window or --limit", file=sys.stderr)
```
For a more thorough fix, support window splitting: when `len(traces) == limit`, split the window in half and re-query.
**Test gap:** Yes — no test of the "result count == limit" condition.

---

### Finding #11 — URL parameter order differs from the Java reference [Low / informational]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:93-107`
**Issue:** Java emits `?limit=N&service=…&start=…&end=…` while Python emits `?service=…&start=…&end=…&limit=N`. The user's brief explicitly says "modulo query-parameter order", so this is intentional and acceptable. Worth flagging for the record because if a future cache-aware proxy keys on full URLs, Python and Java would never share cache entries.
**Suggested fix:** None. Document the policy decision in a comment.
**Test gap:** N/A.

---

### Finding #12 — Service-name encoding differs slightly from Java [Low / informational]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:107` (`quote_via=urllib.parse.quote`)
**Issue:** Java's `URLEncoder.encode(s, UTF_8)` encodes a space as `+`. Python's `urllib.parse.quote` (selected via `quote_via`) encodes a space as `%20`. Both are valid for HTTP query strings (the Jaeger server accepts either), but they're different bytes on the wire.
**Evidence:**
```python
# Java: URLEncoder.encode("my service", UTF_8) → "my+service"
# Python: build_traces_url(..., "my service", ...) → "...service=my%20service&..."
```
**Impact:** None for the Jaeger server; real Jaeger service names are alphanumeric+dash. But the test `test_build_traces_url_encodes_service_name` (lines 75-82) accepts either encoding, which masks the divergence.
**Suggested fix:** None required. If exact byte parity matters, switch to `quote_via=urllib.parse.quote_plus` to match Java.
**Test gap:** N/A.

---

### Finding #13 — `write_output` always overwrites; no merge / append mode [Low]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:262-267`
**Issue:** Re-running the exporter into the same `--out` dir overwrites `post_execution_traces.json`. There's no flag to merge with an existing export. For a research workflow where the user runs N test batches and wants the union, they must either choose distinct `--out` dirs and merge by hand, or invoke with the largest spanning window once.
**Impact:** Low; idempotency-by-overwrite is a sane default. Worth a help-text mention.
**Suggested fix:** None required. If/when desired: add `--merge` that loads the existing file (if any) and dedupes against the new fetch.
**Test gap:** N/A.

---

### Finding #14 — `expand_full` cap message can fire repeatedly across runs but is single-line per run [Trivial]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:241-244`
**Issue:** When `cap` is reached, one warning line is emitted and `cap_warned` suppresses further per-trace messages. This is correct. Cosmetic note: the message says "remaining traces emitted as summaries" — slightly ambiguous, since unique traces past the cap *are* still emitted (with summary spans), but readers might think they're dropped. Re-phrase to "remaining traces emitted with summary span lists only" for clarity.
**Suggested fix:** Cosmetic.
**Test gap:** Already tested in `test_expand_full_respects_cap`.

---

### Finding #15 — Output JSON has hardcoded `"limit": 0`, but the request used `args.limit` [Trivial]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:265`
**Issue:** `payload = {"data": traces, "total": len(traces), "limit": 0, "offset": 0, "errors": None}`. The output envelope claims `limit=0`, regardless of what the user passed via `--limit`. Real Jaeger sets `total=0` and `limit=0` itself, so this is consistent with the wire format — but if a downstream tool reads `limit` for diagnostics it would see the wrong value.
**Impact:** Trivial. `mine_jaeger.iter_traces` reads `data` only.
**Suggested fix:** Either drop the field, or set it to the user's `--limit`. I'd drop it.
**Test gap:** Already tested via `test_write_output_creates_file_and_envelope`.

---

### Finding #16 — `normalise_to_micros` accepts booleans without warning [Trivial]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:66-90`
**Issue:** Python `isinstance(True, int)` is `True`. `normalise_to_micros(True)` returns `1_000_000` (treats `True` as 1 second, scaled to µs). `normalise_to_micros(False)` returns `0`. The CLI doesn't expose this — `--start` is always `str` from argparse — but a programmatic caller using the helper directly could be surprised.
**Suggested fix:** `if isinstance(value, bool): raise TypeError(...)` at the top, or document. Lowest priority.
**Test gap:** N/A.

---

### Finding #17 — No tests for HTTPS / TLS handling [Trivial]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:165-178`
**Issue:** `urllib.request.urlopen` accepts `https://` URLs and uses the system trust store by default. For a dev cluster with self-signed certs, the user would have to set `SSL_CERT_FILE` or pass a custom `ssl.SSLContext` (not currently supported). The default base URL is HTTP, so this is fine for now, but a future user pointing at an HTTPS Jaeger UI might hit cert errors.
**Suggested fix:** Add a `--ca-bundle` or `--insecure` flag if/when needed. Document that the exporter relies on the system trust store. Not required for the headline use case.
**Test gap:** N/A — testing TLS path requires a real or mock TLS server.

---

### Finding #18 — Type hint `opener: object = None` is misleading [Trivial]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:163`
**Issue:** The `JaegerClient` dataclass declares `opener: object = None` but the comment says "callable[[str], tuple[int, bytes]]". The type hint should be `Callable[[str], tuple[int, bytes]] | None` (which `from __future__ import annotations` already lets you write).
**Suggested fix:** Cosmetic.
**Test gap:** N/A.

---

### Finding #19 — Help text doesn't document `--start`/`--end` accepted units [Trivial]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:281-282`
**Issue:** "Epoch start (s, ms, or us — auto-detected)" is informative, but doesn't mention the magnitude thresholds. A user passing `--start 99999999999` (just under 10^11) and `--end 100000000001` (just over) would have *one* timestamp interpreted as seconds and the *other* as milliseconds — silently producing a window 1000× too wide. The thresholds are sensible for current and near-future epochs, but not documented.
**Suggested fix:** Mention the heuristic in the help text or refuse mixed-magnitude pairs:
```python
# After both are normalised, sanity-check that the implied units match
def _detect_unit(n): ...   # 's', 'ms', or 'us'
if _detect_unit(args.start) != _detect_unit(args.end):
    print("warning: --start and --end appear to use different units", file=sys.stderr)
```
**Test gap:** None.

---

### Finding #20 — Sub-microsecond float-second precision is silently floored [Trivial]
**File:** `debug/inputs/scripts/export_jaeger_traces.py:86-87`
**Issue:** `int(n * 1_000_000)` truncates. `normalise_to_micros("1777334400.1234567")` → `1_777_334_400_123_456` (the trailing 7 is lost via float rounding). This is correct behaviour for "microseconds since epoch" but worth a note.
**Suggested fix:** None required.
**Test gap:** Existing test `test_normalise_accepts_float_seconds` covers this for `.5`; doesn't cover sub-microsecond precision.

---

## Test-coverage gaps (consolidated)

| Behaviour | Currently tested? | Suggested |
|---|---|---|
| Real `urllib.request` path with HTTPError 404 | No | Add monkey-patched test |
| Real `urllib.request` path with HTTPError 503 | No | Add monkey-patched test, assert retries |
| `dedupe_traces` with non-iterable scalar `data` | No | Yes |
| `normalise_to_micros(float('inf'))` | No | Yes |
| `normalise_to_micros(float('nan'))` | Implicit | Yes (explicit) |
| `JSONDecodeError` on a 200 body | No | Yes |
| `retries=0` UX | No | Yes |
| `--service ''` (empty) | No | Yes |
| `--limit` exactly equal to result count (truncation warn) | No | Yes once warn added |
| `expand_full` Ctrl-C / partial result | No | N/A |
| `set -e` interaction with the orchestrator | No | Manual |

---

## Tier-by-tier scorecard (vs the brief's checklist)

**Tier 1 — correctness on the headline use case**

1. **URL construction** — Correct (modulo query-param order; intentional). Service name is URL-encoded, `quote_via=quote` rather than Java's `quote_plus` (different but equivalent).  Special chars are encoded.
2. **Time-format auto-detection** — Correct for realistic 2026 epochs and the year-9999 bound. Boundary: `n < SECONDS_UPPER` excludes `10**11` from "seconds" (treated as ms). For epoch-second input, the heuristic mis-classifies any value `>= 10^11`, which won't happen until the year ~5138. **Acceptable.**
3. **Pagination / limits** — Silently truncates. **Add a warn line** (Finding #10).
4. **Dedupe** — Keeps first occurrence per traceID; this is *the wrong policy if the second response has more spans*. After `expand_full`, this is moot because `/traces/{id}` re-fetches the complete trace. So the policy is fine *as long as* `--full-traces` is on (the default). With `--no-full-traces`, the user could lose spans. Worth a doc-string note.
5. **Full-trace expansion** — N requests for N traces, sequential. Acceptable for the headline use case (Finding #9).
6. **Output format** — Verified compatible with `mine_jaeger.iter_traces`. Round-tripped a synthetic export through it; works.
7. **Retry logic** — Uncapped (Finding #4); 4xx handling is broken on real path (Finding #1).

**Tier 2 — edge cases and robustness**

8. **Empty `--service`** — Quietly produces a meaningless query (Finding #7).
9. **`--end <= --start`** — Rejected with exit 2 and a helpful message ("--end (X) must be > --start (Y)"). 
10. **HTTPS / TLS** — Not specially handled (Finding #17). Acceptable default; document.
11. **Large traces** — Reads the whole body into memory. Acceptable for research use.
12. **Encoding** — `indent=2` with all traces in one file. For large exports the file gets big. Worth a `--compact` flag long-term; not needed now.
13. **Idempotency** — Overwrite-only (Finding #13). Acceptable.

**Tier 3 — engineering quality**

14. **Test coverage** — 28 tests, all pass. Negative paths under-tested (consolidated table above).
15. **CLI ergonomics** — Reasonable. Flags are consistent; exit codes 0/1/2 are conventional. Missing: validation on `--retries`, `--limit`, `--max-traces` ranges (no upper or lower bound enforced).
16. **Python compatibility** — `from __future__ import annotations` defers all annotations, so the file should run on 3.9+. Confirmed with grep for runtime 3.10-only constructs (none found). Type hints in body annotations (`set[str] = set()`) are fine because they're deferred.
17. **Logging vs print** — Consistent with the rest of the directory (`mine_jaeger.py`, `mine_test_inputs.py`, `validate_d5.py` all use `print`).

**Tier 4 — wiring**

18. **`run_metrics.sh` integration** — Mostly correct; the exporter's exit status is swallowed by `|| echo`, so `set -e` doesn't trip. `TRACE_DIR` is always overridden to `POST_EXEC_TRACE_DIR` even on failure (Finding #8) — could mine a stale file from a previous run.
19. **Default trace dir** — When `--post-exec-jaeger` is not set, the orchestrator falls back to `$TRACE_DIR` (default `src/main/resources/My-Example/trainticket/test-trace`). This is the *input* trace dir, which has no response bodies — already correctly logged with the warning at line 124-126. **Acceptable.**

---

## Suggested fix priority (one-line)

1. Catch `HTTPError` separately in `JaegerClient.fetch`; fail fast on 4xx (Finding #1).
2. Guard `dedupe_traces` against non-iterable `data` (Finding #2).
3. Reject non-finite floats in `normalise_to_micros` and broaden `main`'s except (Finding #3).
4. Cap the backoff (Finding #4).
5. Add the consolidated test cases (table above).
6. Tighten `run_metrics.sh` to skip the `TRACE_DIR` override on exporter failure (Finding #8).

Everything else (orchestrator UX, pagination warning, help text) is polish.
