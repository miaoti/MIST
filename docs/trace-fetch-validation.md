# Trace Fetch via W3C `traceparent` — Validation

## What it does

Each generated test step mints a unique W3C trace ID, injects it into the outgoing
request as a `traceparent` header, and later fetches the matching Jaeger trace by
that exact ID. No timestamp/service heuristics, no cross-test ambiguity under
parallel execution.

## How it works (3 lines of generated code)

```java
String __mstTraceId1 = UUID.randomUUID().toString().replace("-", "");          // mint
req = req.header("traceparent", "00-" + __mstTraceId1 + "-" + __mstSpanId1 + "-01"); // inject
attachJaegerTrace(..., __mstTraceId1);   // → GET {JAEGER}/traces/<__mstTraceId1>
```

The same `__mstTraceId1` variable is the only ID used in both the outgoing
header and the Jaeger lookup, so they cannot drift.

## Validation 1 — single request, end-to-end

```
mint        traceId = 1a114eaf3ea9434ca226ef411fd946e6
↓
send        GET /api/v1/admintravelservice/admintravel
            traceparent: 00-1a114eaf3ea9434ca226ef411fd946e6-21c1ec939cdb4964-01
↓
wait 3s     (Jaeger ingest)
↓
fetch       GET /jaeger/ui/api/traces/1a114eaf3ea9434ca226ef411fd946e6
↓
result      HTTP 200, 508 KB
            returned traceID = 1a114eaf3ea9434ca226ef411fd946e6   ← exact match
            spans = 470 (cross-service trace tree)
```

## Validation 2 — three concurrent requests, no crosstalk

Three SUT calls fired in parallel, each with its own trace ID, then each trace
fetched independently:

| Trace ID (prefix)    | Returned ID matches? | Spans |
|----------------------|----------------------|-------|
| `2b750a0e482f4692…`  | ✓                    | 438   |
| `ea4a4a88add0416f…`  | ✓                    | 310   |
| `5a7d7fdac80f4e8a…`  | ✓                    | 285   |

Different span counts (438 / 310 / 285) prove the traces are distinct, and each
parallel test got its own trace back — no race, no crosstalk.

## Why this matters

The earlier design fetched traces by `service + operation + timestamp` and
picked the closest match by score. Under parallel execution two tests hitting
the same endpoint within the same window would collide. The marker-first
lookup makes the test → trace mapping a deterministic 1:1.
