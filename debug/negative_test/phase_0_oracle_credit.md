# Phase 0 — Credit Existing Oracle Violations

## Goal

Stop hiding ~6,700 real bug-detection signals per run. MIST's
`TraceShapeOracle` (span_tree, status_propagation, response_envelope,
timing) already fires on ~half of all test executions, but the only thing
that ever lands in the `fault-detection-summary` report today is the
fault-tracker's name match. Add a second class of "detected anomalies"
to the same report so reviewers see the tool's true output.

## Why this is Phase 0 (cheapest, highest leverage)

| What we know | What we can change |
|---|---|
| `ShapeInvariantStore` and the four invariant classes already emit one log line per violation, with the offending trace/test context attached. | Nothing in the report generation path consults them. |
| `FaultDetectionTracker` only credits a detection when an emitted test sees `data.injected=true, faultName=X` in the SUT response (`MultiServiceRESTAssuredWriter:2096-2118`). | Same tracker can absorb an "anomaly source" field and report the broader set. |
| Run 21.b: 7,094 SPAN_TREE + 3,735 STATUS_PROPAGATION violations (Agent A); 6,735 have NO matching fault name. | Those 6,735 are real-tool-output the report currently throws away. |

No algorithm changes, no oracle changes, no test-generation changes.
Just plumbing: collect → categorize → render.

## Design

### Data model

Extend `FaultDetectionTracker` with a parallel structure:

```java
// Existing
private final Map<String, InjectedFault> injectedFaults;     // benchmark-known
private final Map<String, List<FaultDetection>> detectedFaults;

// New
private final Map<OracleViolationKey, OracleAnomaly> oracleAnomalies;
//   key = (oracleName, endpointSig, violationSignature)
//   value = OracleAnomaly { firstSeenTs, lastSeenTs, hitCount, sample(testClass, testMethod, traceId) }
```

`OracleAnomaly` deduplicates by `(oracle, endpoint, violation-signature)`
so 415 SPAN_TREE hits on the same endpoint with the same broken span
shape collapse to one anomaly with `hitCount=415`. Mirrors the existing
detection-count rollup but for oracles.

### Recording path

The four invariant classes in `mist-core/src/main/java/io/mist/core/
oracle/shape/invariant/` don't carry test-identity context (testClass,
testMethodName, traceId) — they only know their `rootApiKey` and the
{@link TraceModel} the oracle handed them. Recording at the
verdict-consumption site is therefore where both the failure outcome
and the test identity are simultaneously in scope.

Concretely, the generated test method's `attachJaegerTrace` calls
`oracle.evaluate(model, rootApiKey, targetService, targetParam)` and
hands the resulting `TraceShapeVerdict` to
`FaultDetectionTracker.recordVerdict(verdict, rootApiKey, testClass,
testMethod, traceId)`. The tracker iterates `verdict.getOutcomes()` and
for each failing outcome calls the internal `recordOracleAnomaly(kind,
rootApiKey, violationFingerprint(kind, detail), …, testClass,
testMethod, traceId)` — same data, same dedup key shape, just routed
through the verdict instead of called directly by each invariant.

`violationFingerprint` is a stable hash over the kind of mismatch — e.g.
"missing edge order-service→payment-service at depth 2", not the
concrete trace timestamps — so cross-test recurrences collapse.

### Report

`FaultDetectionTracker.generateReport()` already prints two sections:
DETECTED FAULTS and UNDETECTED FAULTS. Add a third:

```
================================================================================
ORACLE ANOMALIES (no matching injected-fault name)
================================================================================
Total Distinct Anomalies:  N
Total Hit Events:          M

  1. SPAN_TREE  |  POST /api/v1/orders
     Violation: missing edge ts-order-service → ts-payment-service
     Hits:      415 time(s)  (first: ..., last: ...)
     Example test: trainticket_twostage_test...Flow_Scenario_12101.test_negative_..._v37
     Example trace: 8a3d9e7f4c...

  2. STATUS_PROPAGATION  |  GET /api/v1/contacts/{id}
     Violation: leaf 500 not propagated, gateway returned 200
     Hits:      87 time(s)  ...

  ...
```

Sort by `hitCount` descending so the most common anomalies surface
first. Anomalies that ALSO match a fault name appear in BOTH sections
(detected-faults gets credit, anomalies section gets credit) — clearer
than trying to suppress one or the other.

### Configuration

Add one optional flag for backward compatibility:

```properties
# Default true. Setting false reproduces the old report shape (only
# fault-name-matched detections, no oracle anomalies section).
mist.report.oracle.anomalies.enabled=true
```

Allow-list it in `MstConfigValidator.KNOWN_KEYS`.

## Implementation Checklist

- [ ] `FaultDetectionTracker` API additions
  - [ ] `OracleAnomaly` inner class (sample, hitCount, timestamps)
  - [ ] `recordOracleAnomaly(oracle, endpoint, violationSig, sample)` synchronized method
  - [ ] `Map<OracleViolationKey, OracleAnomaly> oracleAnomalies` field
- [ ] Each invariant emits one `recordOracleAnomaly` call alongside its
      existing log:
  - [ ] `SpanTreeInvariant`
  - [ ] `StatusPropagationInvariant`
  - [ ] `ResponseEnvelopeInvariant` (currently producing 0 violations — fix populated learned model later, but wire the call now)
  - [ ] `TimingInvariant` (currently disabled — wire so flipping the flag works)
- [ ] `generateReport()` writes the new ORACLE ANOMALIES section after
      DETECTED + UNDETECTED. Honour `mist.report.oracle.anomalies.enabled`.
- [ ] `MstConfigValidator.KNOWN_KEYS` += `mist.report.oracle.anomalies.enabled`
- [ ] Unit test: `FaultDetectionTrackerOracleAnomalyTest`
  - [ ] 100 recordOracleAnomaly calls with the same key → one entry with `hitCount=100`
  - [ ] Different `(oracle, endpoint, sig)` keys produce distinct entries
  - [ ] Sample fields preserved (first non-null wins)
  - [ ] Report writer produces exact expected text for one sample anomaly
- [ ] Smoke test: rerun against Run 22's already-populated state (no SUT calls needed) and verify the new section appears
- [ ] Backward-compat verify: with `mist.report.oracle.anomalies.enabled=false`
      the old report bytes match Run 22's report bytes

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| 6,700 anomaly entries make the report file unmanageable | Low | Group by `hitCount` and trim per-section sample list; total entries stay under ~200 after key collapse |
| Sample test reference becomes stale when generation re-runs | Low | Store the first-seen sample only; never overwrite |
| Cross-thread `recordOracleAnomaly` race on `hitCount` increment | Medium | Method is `synchronized`; same pattern as existing `recordDetectedFault` |
| Anomalies inflate the perceived fault rate, making 10/10 look like 10/10 + 6,700 false positives | Medium | Report header explicitly labels: "ORACLE ANOMALIES are tool-detected trace-shape deviations, NOT confirmed bugs. They are upper-bound bug-finding signal." Reviewer reads the disclaimer. |

## Success Criteria

- New section appears in Run-23 report, with at least one entry per active
  invariant (span_tree, status_propagation).
- For Run 22 specifically (re-run report generation with the
  already-populated state), the anomaly section recovers something close
  to Agent A's earlier audit numbers: ~7,000 SPAN_TREE + ~3,700
  STATUS_PROPAGATION violations collapsed into low-hundreds of distinct
  anomalies.
- 0 oracle-anomaly entries match an existing detected-fault key (sanity
  check: the two sections record different things).
- All existing tests stay green; `mist.report.oracle.anomalies.enabled=false`
  produces byte-identical legacy report.
- Wall-time impact on a run: < 1% (in-memory map updates, no LLM calls).

## Out of Scope

- Trace shape invariant *learning* improvements (those add real new
  anomaly detections; Phase 0 only surfaces what already fires).
- Cross-run anomaly persistence / de-duplication (each run's report is
  self-contained).
- Anomaly precision tuning (a Phase-2 trace-attribution step can later
  filter anomalies that AREN'T leaf-error matches).
