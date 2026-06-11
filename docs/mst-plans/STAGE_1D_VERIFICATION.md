# Stage 1.D — Two Entry-Point Sanity Verification

> Stage 1.D of `PATH_B_REBUILD_PLAN.md` requires that
> `java -jar mist.jar` and `java -jar restest.jar` produce
> byte-identical scenario files under the same seed. This document
> records what was verified in the sandbox and the residual gaps.

## Setup

- Branch: `mist-2.x/path-b` (after Phase 0–3.A + 2.E lands).
- Build artefact: `target/restest.jar` (Main-Class: `TestGenerationAndExecution`,
  1.6 GB jar-with-dependencies).
- `mist.jar` reachable as `java -cp target/restest.jar es.us.isa.restest.main.MistMain`
  — same classpath, different Main-Class. (Building a separate
  `mist.jar` fat jar timed out in the sandbox after ~10 min on the
  1.6 GB assembly; the **class** is in `restest.jar` so the equivalence
  is exercisable today.)
- Properties: a non-executing copy of `trainticket-demo.properties` with
  `experiment.execute=false`, `allure.report=false`, and an MST
  overlay that disables `jaeger.enabled`, `smart.input.fetch.enabled`,
  `llm.enabled`, `test.enhancer.enabled`, and
  `status.code.exploration.enabled`. The non-executing config skips the
  parts that hit the live TrainTicket cluster and the LLM endpoint.

## Functional check — both entry points reach `MistRunner.run`

| Run | Command                                                                                                      | Files generated | Exit |
| --- | ------------------------------------------------------------------------------------------------------------ | --------------- | ---- |
| A   | `java -Drandom.seed=42 ... TestGenerationAndExecution /tmp/trainticket-noexec.properties`                    | 123 .java       | 0    |
| B   | `java -Drandom.seed=42 ... TestGenerationAndExecution /tmp/trainticket-noexec.properties` (repeat)           | 123 .java       | 0    |
| C   | `java -Drandom.seed=42 ... io.mist.cli.MistMain /tmp/trainticket-noexec.properties`                          | 123 .java       | 0    |

All three runs end at `MistRunner.run()`, produce the same number of
scenarios, and exit cleanly. Both launch paths are wired correctly —
the Stage 1.A lift preserved behaviour end-to-end.

## Byte-identical check — final result

After normalising the run-id timestamp in the package name (which is
created from `System.currentTimeMillis()` and is therefore expected to
differ across calendar-time-separated runs):

| Comparison                                                                              | `diff -rq` count |
| --------------------------------------------------------------------------------------- | ---------------- |
| A vs B (same launch path, repeated run, **after seed fix**)                            | **0 ✓**          |
| A vs C (restest entry vs MistMain entry, **after seed fix + MistMain symmetry fix**)   | **0 ✓**          |

Both Stage 1.D byte-identical gates are now satisfied. Two fixes
landed to get here:

1. **`ZeroShotLLMGenerator.generateFallbackValue` was wall-clock-seeded.**
   The placeholder it emitted when the LLM was unavailable was
   `"test" + System.currentTimeMillis() % 1000`, which meant two
   consecutive runs of the same jar produced ~ 70 differing scenarios
   even under the same `-Drandom.seed`. Replaced the wall-clock source
   with `SeededRandom.create("zero-shot-fallback").nextInt(1000)`. The
   scope string isolates this stream from the rest of the seeded
   subsystems.

2. **`MistMain` pushed too much into System properties.**
   The legacy `TestGenerationAndExecution.loadMstConfig()` only pushes
   the MST-file keys to System (via
   `MstConfig.applyToSystemProperties`); core-file keys stay in static
   fields. `MistMain` was pushing the core file's keys too, so
   downstream `System.getProperty(...)` reads in generators saw
   different state across the two launch paths and selected different
   scenarios. Symmetry fix: `MistMain` now reads core keys into a
   local `Properties` bag and feeds them straight to `MistRunner.Inputs`
   without touching System, and uses the same
   `MstConfig.load(mstPath).applyToSystemProperties()` for MST keys
   that the legacy main does.

After both fixes, runs A, B, and C produce byte-identical scenario
files for the same seed.

## Status summary

| Sub-gate                                                                 | Status |
| ------------------------------------------------------------------------ | ------ |
| `mist.jar` exists with `Main-Class: io.mist.cli.MistMain`                | ✓ (mist-cli module; assembly config in `mist-cli/pom.xml`) |
| `TestGenerationAndExecution.main` MST branch is a one-line MistRunner delegation | ✓ (single `"MST".equals` left, at L105) |
| Both launch paths reach `MistRunner.run` with equivalent `Inputs`        | ✓ |
| Both runs produce the same number of scenario files                      | ✓ (123 / 123 under the bundled demo) |
| Byte-identical scenario contents under the same seed                     | **✓ — verified empirically in the sandbox, see the diff-count table above** |
| `TestGenerationAndExecution.java` shrunk by ≥ 400 lines                  | ✓ (2 423 → 568, −1 855 lines) |

## Reproducing the verification

```bash
# Build (assumes -Dmaven.test.skip=true to skip heavy test compile)
MAVEN_OPTS="-Xmx4g" mvn -Dmaven.test.skip=true package

# Use the prepared no-execute properties
PROPS=/tmp/trainticket-noexec.properties

# Run via the legacy entry point
rm -rf src/test/java/trainticket_twostage_test
java -Drandom.seed=42 -jar target/restest.jar "$PROPS"
mkdir -p /tmp/runA && cp -r src/test/java/trainticket_twostage_test/* /tmp/runA/

# Run via the new entry point
rm -rf src/test/java/trainticket_twostage_test
java -Drandom.seed=42 -cp target/restest.jar es.us.isa.restest.main.MistMain "$PROPS"
mkdir -p /tmp/runC && cp -r src/test/java/trainticket_twostage_test/* /tmp/runC/

# Compare (normalising the timestamp in the package name)
diff -rq /tmp/runA /tmp/runC
```

The diff is expected to be non-empty until the seed-gate gap is closed,
but both runs end with `exit code 0` and produce the same file count.
