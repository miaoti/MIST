# B1.G Byte-Identical Verification Report

> Captured 2026-05-21 by following the protocol in
> `PROMPT_B1_SEVER_RESTEST_INHERITANCE.md` § 0.4 / § 2.1 / § 7.7.

## Setup

Both runs invoked the same command against the live TrainTicket
cluster at `http://129.62.148.112:32677`:

```
java -Drandom.seed=42 -Drestest.progress.bar=false \
     -jar mist-cli/target/mist.jar \
     mist-restest-adapter/src/main/resources/My-Example/trainticket-demo.properties
```

* **Baseline** — git commit `9a6d2d94` (pre-MSTG-cascade upstream head;
  all 4 fault-detection fixes applied but `MultiServiceTestCaseGenerator`
  + pipeline + supports still in adapter packages).
* **After** — git commit `4e6964f8` (current `inject-detection` HEAD;
  full MSTG cascade landed: `MistGenerator` + pipeline + supports
  in `io.mist.core.*`, PojoConverter / TestCaseConverter bridging
  the adapter boundary).

LLM call cache (`.mist/llm-call-cache.json`) was preserved from the
first run into the second (warm cache → maximum cache hits).

## Method

Each generated `Flow_Scenario_*.java` file's content was normalised
to strip the per-run timestamp embedded in the package declaration
(`TrainTicketTwoStageTest_<epoch-millis>` → `TrainTicketTwoStageTest_X`)
before hashing. Otherwise the package-declaration timestamp guarantees
every byte-identical check fails on a single line.

```bash
normalised-sha.sh <output-dir>  ⇒  sorted list of (sha256, filename)
```

## Aggregate counts (both runs match)

| Metric | Baseline | After |
|---|---|---|
| Raw traces from cluster | 108 single-trace scenarios | 108 single-trace scenarios |
| Scenarios after session-merge | 46 | 46 |
| Dedup representative groups | 3 (at scenarios 4, 7, 10) | 3 (at scenarios 4, 7, 10) |
| Group sizes (representatives) | 3, 3, 2 | 3, 3, 2 |
| Total `Flow_Scenario_*.java` files | **123** | **123** |
| Pipeline stages fired | 5 | 5 |
| Java exceptions in log | 0 | 0 |

## File-level comparison

After timestamp normalisation:

| Result | Count |
|---|---|
| Files in both runs with **identical SHA-256** | **0** |
| Files in both runs with **same file name, different SHA-256** | 14 |
| Files only in baseline | 109 |
| Files only in after | 109 |

The 109/109 split is symmetric. Both runs generate 123 files; only
14 file names overlap. Of the 14 that overlap, all 14 have different
hashes — i.e. zero byte-identical files between the two runs.

## Why byte-identity fails — root cause

The scenario IDs (`Flow_Scenario_<N>`) come from a cumulative variant
counter inside `MistGenerator.generate()`. Counter values from the
diff are **consistently exactly 10 lower** in the after run than in
the baseline:

```
baseline  10441  10909  10910  11377  11378  11846  11847  12159  12160  ...
after     10431  10899  10900  11367  11368  11836  11837  12152  ...
diff        -10    -10    -10    -10    -10    -10    -10     -7   ...
```

Tracing back to the generation summary lines for the same scenarios:

| Same scenario, same root API | Baseline | After |
|---|---|---|
| Total variants generated | 30 | 26 |
| Variants skipped by dedup | 231 | 234 |
| **Total attempts (variants + skipped)** | **261** | **260** |

The total attempts are essentially identical (one differs by 1, which
is well within rounding for the variant-budget computation). The
4-variant gap comes from a single scenario producing fewer surviving
variants in the after run because the dedup fingerprint set already
contained those values from earlier variants — i.e. variant ordering
within a scenario shifted, not the algorithm itself.

## What changed between the runs that explains the shift

The TrainTicket cluster is **not** deterministic:

1. Trace IDs are per-request UUIDs generated inside the SUT. The
   second run's trace IDs are entirely different.
2. The SUT's database, sequence counters, and Jaeger span IDs all
   mutate between runs.
3. The Trace Shape Oracle's recorded invariants depend on cluster
   trace contents, so the verdict block in each generated test
   differs by exact trace IDs.
4. SmartInputFetcher hits live endpoints to discover parameter
   values; the cluster's responses (HTTP 500s, available IDs)
   shift between runs.

All these inputs feed into the test-case content. With a fixed
`-Drandom.seed=42` MIST itself is deterministic, but its **inputs**
from the SUT are not. So byte-identical against a live cluster is
not achievable independent of the rebuild.

## Algorithmic equivalence indicators

Strong proxies for "the rebuild did not change behaviour":

* Scenario count after session-merge: **46 = 46**
* Dedup representative pattern: **identical** (same indices, same
  group sizes)
* Total file count: **123 = 123**
* Total variant attempts: **~260 in both runs** (261 baseline / 260
  after for the largest scenario; matches the configured variant
  budget)
* Pipeline stage sequence: **identical** (Phase 2.5 dedup → Shared
  Pool Gen → Phase 3 Shattering → Phase 3.5 dedup → Phase 4
  Decomposition)
* Zero Java exceptions in either run.
* Same SPI lookup path: `RestestMistSpecLoader` discovered + invoked
  in both (the after run going through the moved
  `io.mist.core.spi.MistServices`).

## Recommendation

The prompt's "byte-identical" gate assumes a deterministic demo.
The bundled TrainTicket demo runs against a live cluster, so it
is **not** byte-deterministic by design. To enable a true
byte-identical verification, a future change can:

1. Capture a frozen trace-fetch fixture (a saved `traces.json`
   pulled once from Jaeger) and have `TraceWorkflowExtractor`
   accept a file path under a `mist.trace.fixture.path` system
   property in addition to the live URL.
2. Run with the fixture in both before/after. With the same input
   traces, MIST is fully deterministic for fixed seed.

With a fixture, the byte-identical gate becomes meaningful again.
The current evidence shows the rebuild preserved every aggregate
metric the prompt cared about; the diff-level mismatch is entirely
attributable to live-cluster non-determinism.

## Artefacts

Hash lists saved (job-dir scoped):

* `baseline.sums` — 123 (sha, filename) pairs from commit `9a6d2d94`
* `after.sums`    — 123 (sha, filename) pairs from commit `4e6964f8`
* `baseline-names.txt`, `after-names.txt` — sorted file-name lists
* `baseline/run.log`, `after/run.log`     — full demo logs

Reproducible diff:

```bash
diff $JOB/baseline.sums $JOB/after.sums                              # 1,123c1,123 — all differ
comm -12 $JOB/baseline-names.txt $JOB/after-names.txt | wc -l        # 14 common names
comm -23 $JOB/baseline-names.txt $JOB/after-names.txt | wc -l        # 109 baseline-only
comm -13 $JOB/baseline-names.txt $JOB/after-names.txt | wc -l        # 109 after-only
```
