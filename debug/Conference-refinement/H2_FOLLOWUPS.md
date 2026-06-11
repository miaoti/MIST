# H2 Follow-Ups

Notes captured during the H2 ablation infrastructure work that are
**out of scope** for this branch (per PROMPT_H2_ABLATION_INFRASTRUCTURE.md
section 0.3). Resolve under separate tickets.

## 1. Pre-existing non-determinism under `-Drandom.seed=42` — **RESOLVED**

**Resolution.** Root cause was an unseeded `new Random()` inside
`MistGenerator.rankWithBanditUnchecked()`: the Thompson scheduler that
re-ranks the fault queue was constructed via the no-arg
`new ThompsonScheduler()` and therefore picked up a fresh, time-derived
seed on every run. Even with `-Drandom.seed=42` set, the bandit's
sampling order drifted across runs, which cascaded into different fault
categories and different fall-back indices in `typeAwareFallbackValue`.

The fix routes the bandit's RNG through `SeededRandom.create("bandit")`
so the bandit shares the configured seed (under a distinct stream
scope so its draws don't correlate with the generator's own
`SeededRandom.create("MistGenerator")` instance).

**Post-fix verification gate § 7.5.** With the bandit fix in place,
running the bundled `trainticket-demo-noexec.properties` twice under
`-Drandom.seed=42` for each ablation row yields:

| Row | Toggles                                              | Byte-identical |
|-----|------------------------------------------------------|----------------|
| R1  | shape=true,  mining=true                             | 123 / 123 |
| R2  | shape=false, mining=true                             | 123 / 123 |
| R3  | shape=true,  mining=false  (bundled default)         | 123 / 123 |
| R4  | shape=false, mining=false                            | 123 / 123 |

(Comparison normalises the timestamped package suffix
`TrainTicketTwoStageTest_<epoch-millis>` to `<TS>` and SHA-256s each
`Flow_Scenario_*.java` pair.)

R1≡R2≡R3≡R4 on the noexec profile is expected and not a bug: the
oracle toggles affect runtime verdict only (Flow_Scenario_*.java
generation is independent), and `mst.fault.mining.enabled` requires
LLM (disabled in noexec). Cross-row generation divergence only fires
when LLM is on, which is the live (`trainticket-demo.properties`)
demo — a longer-running validation that is out of scope for this
fix.

### 1.1 Second-order fix: drop the suffix normalisation

The §1 verification table above relied on normalising the
`TrainTicketTwoStageTest_<epoch-millis>` package suffix to a
placeholder before SHA-256ing. That normalisation existed because
`IDGenerator.generateTimeId()` returned `new Date().getTime()`
unconditionally, so every seeded run picked a different wall-clock
suffix and the generated test class name (and therefore the
package directive at the top of every `Flow_Scenario_*.java`)
disagreed across runs.

The unseeded fallback is unchanged; the seeded path now uses the
configured seed value as the suffix instead:

```java
public static String generateTimeId() {
    Long baseSeed = SeededRandom.getBaseSeed();
    if (baseSeed != null) return String.valueOf(baseSeed);
    return String.valueOf(new Date().getTime());
}
```

With this change, two `-Drandom.seed=42` runs of the noexec demo
produce the package `TrainTicketTwoStageTest_42` in both runs, so
the file contents agree at the byte level without preprocessing.

**Verification (post-fix, no normalisation):**

| Row | Toggles                                              | Byte-identical |
|-----|------------------------------------------------------|----------------|
| R3  | shape=true, mining=false  (bundled default, default seed)        | 123 / 123 raw  |

`diff <run1.sums> <run2.sums>` is empty without any sed/awk pass
over the file content. `mvn test -pl mist-core,mist-cli` continues
to pass (100 + 4).

The unseeded path is untouched, so production behaviour for users
who do not set `random.seed` (every run gets a unique class name
to avoid output collisions on parallel runs) is preserved.

---

**Original report (kept for history):**

## 1. Pre-existing non-determinism under `-Drandom.seed=42`

**Observed:** Running
`java -Drandom.seed=42 -jar mist-cli/target/mist.jar mist-cli/src/main/resources/My-Example/trainticket-demo-noexec.properties`
twice produces `Flow_Scenario_*.java` files that differ in content
even after normalising the timestamped package suffix
(`TrainTicketTwoStageTest_<epoch-millis>`).

The diff is **not** caused by H2:
- The H2 oracle toggles never feed back into the generated test source
  (they affect runtime verdict only, and the verdict bootstrap code
  the writer emits is unconditional).
- The H2 bandit gate preserves insertion order when disabled —
  insertion order is itself deterministic given a fixed queue, so the
  disabled path cannot inject randomness; the enabled path was the
  pre-H2 behaviour, so it carries whatever non-determinism existed
  before.

Concretely, two same-seed R3 runs produced different fault selections:

```
< test_negative_flow_S2283_v1_fault_Root1_TYPE_MISMATCH
> test_negative_flow_S2283_v1_fault_Root1_SPECIAL_CHARACTERS
< "boughtDateStart", "; ls -la"
> "boughtDateStart", "test892"
```

i.e. completely different fault types selected for the same parameter,
and completely different invalid input values. This points at a
random source that isn't reseeded via `-Drandom.seed=42` (likely
the smart-input fetcher's LLM call cache, the parameter-error analysis
cache, or a `System.currentTimeMillis()` leak into a generator).

**Why this matters for H2:** PROMPT_H2 section 7.2's cardinal
byte-identical-equivalence gate (`diff baseline.sums R3.sums == empty`)
cannot pass without first stabilising the pre-existing seed. H2 still
verifies what it can:
1. `mvn -pl mist-core,mist-cli -am test` is green (100 tests in
   mist-core, 4 in mist-cli, including 30 new H2 cases).
2. Each of R1/R2/R3/R4 runs the demo to completion without throwing.
3. The ablation profile banner emits the right summary for each.
4. The toggle wiring is unit-tested at the TraceShapeOracle and
   MistGenerator.applyBanditGate seams.

**Follow-up scope:** ~~Pin the leaking randomness source.~~ **Resolved
by the bandit-seeding fix above.** The bundled `-Drandom.seed=42`
flag now produces byte-identical `Flow_Scenario_*.java` files
across reruns of the same config — verified for R1/R2/R3/R4 on the
`trainticket-demo-noexec.properties` profile, 123 / 123 files
byte-identical per row.

## 2. Per-invariant attribution in AblationProfile.summary()

`AblationProfile.summary()` currently lists only the three "named
contribution" invariants (`span`, `status`, `response`) in the
on-list and pulls `timing` out into its own slot. If the paper
revision ever re-promotes `timing` (or adds a fifth invariant), the
summary format will need a one-line tweak. Left intentionally
hard-coded so the format is easy to grep on; revisit during the
paper-writing phase.

## 3. Writer-emitted oracle bootstrap reads the runtime singleton

`MultiServiceRESTAssuredWriter.java:1245` still emits
`new TraceShapeOracle(new ShapeInvariantStore(tsoPath))` into the
generated test source. The delegate constructor on `TraceShapeOracle`
calls `MstConfig.instance().oracle()`, so when the test executes the
toggles take effect via the test JVM's system properties. This means
the user can still ablate at test-execution time by passing
`-Dmst.oracle.shape.enabled=false` to the surefire JVM, even though
the writer wasn't asked to thread the explicit config through. No
action needed — just documenting the indirection in case it confuses
a future reader.
