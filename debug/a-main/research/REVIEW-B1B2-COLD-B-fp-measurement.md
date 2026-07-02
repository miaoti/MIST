# Cold review B — validity of the ≤5% FP bar + false-negative behavior

**Date:** 2026-07-02. Independent cold reviewer (no shared context), one of three
dispatched per the ≥3-cold-reviewer rule during the Gate-1 run-#3 wait. Focus: is the
pre-registered ≤5% non-timeout-gated sync-FP bar scientifically sound / non-gameable,
and can a real lost write be hidden? Verbatim findings; reconciliation in
REVIEW-B1B2-RECONCILIATION.md.

---

## Ranked findings

### 1. [MAJOR — CONFIRMED] The bar's governed rate is a *lower bound* on the true benign-loss rate; excluding timeout-gated fires from the numerator while keeping them in the denominator is anti-conservative and Jaeger-dependent
`PairedFaultExecutor.java:336,:384-385,:364-373`.
`nonTimeoutFpRate = observedGated/acked` where `acked` includes ALL acked records
(TIMEOUT_ABSENT and OBSERVED_PRESENT included). Since observedGated ≤ fires, the bar
governs a quantity ≤ raw `fpRate`; PASS does NOT imply raw acked-but-absent ≤ 5%.
Scenario: acked=100, observedGated=4 (4% → PASS), timeoutGated=50 because Jaeger was
latency-degraded → raw benign-loss 54%, bar reads 4% PASS **with no caveat** (caveat
only fires at observedGated==0). An ack already establishes the request executed;
trace non-confirmation is about trace completeness, not whether the write happened —
excluding those fires from the numerator while retaining them in the denominator is
not a well-defined conditional rate. Honest metric: report the interval
`[observedGated/acked, fires/acked]` (or NOT_EVALUABLE under gate degradation).
Dangerous direction: anything shifting fires observed→timeout-gated (Jaeger latency,
exporter lag, longer settle) lowers the governed rate WITHOUT lowering the true FP
rate. **Single biggest threat.**

### 2. [MAJOR — CONFIRMED] Bar emits "PASS" (not NOT_EVALUABLE) when the observation gate was non-functional; the caveat annotates rather than prevents, and misses partial degradation
`PairedFaultExecutor.java:335-344`. Jaeger down all run → observedGated==0 → rate 0 →
verdict "PASS" + caveat *string*. The ≥20-acked floor guards the denominator but
nothing guards observation-gate collapse. Partial case (observedGated ≥ 1 but many
timeout-gated) gets NO caveat and still PASSes (= finding 1's scenario). Downstream
consumers keying on `syncFpBar.verdict == "PASS"` record a pass for a vacuous run.

### 3. [MAJOR — CONFIRMED] OBSERVED_COMPLETE_ABSENT is declared without re-reading the read-back after the settle window
`DataIntegrityRuntime.java:334-345`, settle sleep `:550`. Absence is sampled at the
cap (~10s), then traceComplete sleeps 3s checking only Jaeger span stability; the
record keeps the PRE-settle body — no `getSut` re-read at ~13s. An eventually-
consistent benign write committing at t=11s is counted a high-confidence FP.
Direction: inflates `observedGated` — conservative for PASS (could cause spurious
FAIL) but invalidates the semantic claim that OBSERVED_COMPLETE_ABSENT means "did not
persist." The pinned test never scripts a late-present read-back — blind spot
untested.

### 4. [MAJOR — CONFIRMED mechanism / PLAUSIBLE trigger] "Trace present + stable span count" proxies *request completion*, not *durable absence*
`DataIntegrityRuntime.java:536-573`. Nothing in the gate inspects storage/write-path
success. (a) Read-path lag / wrong view: write persists on primary but not visible on
the collection read-back within the cap → OBSERVED_COMPLETE_ABSENT though persisted →
spurious high-confidence FP. (b) Stable-but-partial trace (exporter drops, no new
batch during settle) → false "complete" (disclosed in code comment `:60-66`).
Consequence: OBSERVED_COMPLETE_ABSENT should be read as "observed-not-visible-on-
read-path," not "observed-lost" — weakens the label-free headline: the gate raises
confidence the *request ran*, not that the *write was durably lost*.

### 5. [MAJOR — CONFIRMED mechanism] `pick()` returns the first acked record regardless of fire status → a persisted sibling masks a genuinely-lost sibling (false negative on the pairing FIRE)
`PairedFaultExecutor.java:218-233` used at `:173-180`; verdict `:284-292`. Config
emits ~10 positive variants per scenario → up to 10 `afterWrite` calls per stepKey.
Scenario: fault run, variant #1 persists (acked, present), variant #7 lost (acked,
absent); pick returns #1 → NO_FIRE → the genuine acknowledged-but-lost write is
hidden. With `mst.test.parallelism=1` (MistRunner:614) this is deterministic
(first-acked-variant-wins), still systematically hides a true positive whenever the
first acked variant persisted. Affects only the pairing FIRE demonstration, not the FP
rate (which iterates all records).

### 6. [MEDIUM — CONFIRMED] Denominator + ≥20 floor count per-variant, per-triple *records* as independent "runs," pooled across two heterogeneous endpoints
`PairedFaultExecutor.java:358-364,:323,:329,:58`; `benignProbe :206-214`;
pre-registration string says "runs" `:327-328`. 30 iterations × 2 triples × up to 10
variants → denominator can be hundreds; the ≥20 floor trivially met by ~10 iterations;
records within an iteration share deploy/time-window/pool contention → not independent
trials. Aggregate bar is a record-weighted pooled rate over correlated multi-endpoint
observations; the endpoint with more variants dominates. Per-triple stats exist
(`:315-319`) but the bar verdict is aggregate-only (`:323-344`).

### 7. [MEDIUM — PLAUSIBLE] STATION_PAIR cross-run collision under replication lag → sibling run's persisted route satisfies this run's membership → spurious OBSERVED_PRESENT hides a lost write (biases probe rate DOWN)
`DataIntegrityRuntime.java:418-462` (usedPairs from THIS run's baseline; claimedPairs
per-Session), membership scans whole collection. Across control→fault or probe
iterations, if iteration i-1's route is not yet visible in i's baseline but becomes
visible during i's poll, i can freshen onto the same pair and match i-1's row →
OBSERVED_PRESENT even if i's own write was lost. FRESH_STRINGS contacts immune; only
station-pair adminroute at risk.

### 8. [LOW — CONFIRMED, deliberate] Soft cap: presence checked before the timeout test → OBSERVED_PRESENT with elapsed > cap is reachable
`DataIntegrityRuntime.java:324-341`; curve guard `PairedFaultExecutor.java:394-400`;
pinned by tests. A write appearing in (cap, cap+poll+latency] is "present" and not
re-counted at the cap (F4 comment). Effect small (one poll interval), disclosed; the
cap is elastic upward and the cap-point FP rate slightly underestimates. Curve math
otherwise correct (fires XOR presence, strict >, honest censoring).

### 9. [LOW — PLAUSIBLE] Representativeness: probe shares the fault run's code path (good) but station depletion + per-JVM token expiry over 30 iterations skew the acked sample to early, low-contention iterations
`benignProbe :198-216` reuses the same run (sound); `freshStationPair` throws on
exhaustion → recorded error → invalid, not acked (self-protecting via the <20 guard),
but the acked population skews early. Stated threat to representativeness, not fatal.

## What is genuinely sound
Arithmetic correct (`:336,:381`); MIN_ACKED floor genuinely forces NOT_EVALUABLE
(pinned); curve censoring honest, no off-by-one/double-count; `verdict()` routes
environment failures to NOT_EVALUABLE and requires control-persisted (real systemic
guard); strata never pooled; async no-claim disclaimer explicit; traceId is the
write's own client-injected traceparent (correct trace looked up).

## One-line verdict
The ≤5% bar is carefully engineered and largely honest but **not yet fully
non-gameable**: the governed quantity `observedGated/allAcked` is a lower bound whose
bias direction depends on Jaeger health — trace-confirmation latency/downtime shifts
genuine acked-but-lost benign writes into the excluded timeout-gated bucket, so the
bar can certify "≤5% FP" (PASS, no caveat) while the raw benign-loss rate is far
higher. Fix: verdict NOT_EVALUABLE (not PASS) when the observation gate is degraded;
report the FP rate as the interval `[observedGated/acked, fires/acked]` rather than
the lower endpoint alone.
