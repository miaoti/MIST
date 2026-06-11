# H2 — Ablation Infrastructure Inventory

> **Status: closed.** Outcome: six toggles + AblationProfile + startup
> banner all landed; mist-core/mist-cli build green; 30 new unit tests
> pass alongside the pre-existing 74 (104 total). Byte-identical
> equivalence on R3 vs the pre-work baseline deferred per
> H2_FOLLOWUPS.md section 1 (pre-existing non-determinism not caused by
> this work). R1/R2/R3/R4 each execute the bundled demo to completion
> and emit the correct AblationProfile banner.

This file enumerates every surface H2 will touch. Each entry carries a
verified `file:line` reference (re-greppable at any time). Cross-checks
PROMPT_H2_ABLATION_INFRASTRUCTURE.md § 3.1 against the actual
post-B1 layout.

## 1. Configuration home (`MstConfig`)

- **Top-level:** `mist-core/src/main/java/io/mist/core/config/MstConfig.java:27`
- **Existing sub-records** (sample, complete list verified):
  `Core` (L173), `SmartFetch` (L186), `Llm` (L204), `Faulty` (L224),
  `ScenarioMerge` (L241), `ScenarioShattering` (L254),
  `SoftErrorCache` (L264), `ParameterErrorCache` (L284),
  `IntelligentAnalysisCache` (L300), `StatusCodeExploration` (L311),
  `Enhancer` (L327), `Jaeger` (L343).
- **Singleton accessor:** `MstConfig.instance()` at L81. Lazy, validated
  once at construction via `MstConfigValidator.validate(...)`.
- **Test reset hook:** `resetForTesting()` at L123 (package-private).
- **Validator:** `mist-core/src/main/java/io/mist/core/config/MstConfigValidator.java:30`,
  with the canonical `KNOWN_KEYS` whitelist at L43-L116 and the
  namespace prefix list at L128-L143.
- **New sub-records to add (H2.B):** `Oracle` (5 toggles) and
  `Scheduler` (1 toggle). See PROMPT_H2 § 4.4 for the exact accessors.

## 2. Trace Shape Oracle surface

- **Class:** `mist-core/src/main/java/io/mist/core/oracle/shape/TraceShapeOracle.java:14`
- **`evaluate(...)`** at L22-L29 — loads four invariants unconditionally
  and folds their outcomes into one `TraceShapeVerdict`.
- **Construction sites of `new TraceShapeOracle(...)`** (3 prod, 1 test):
  - Prod: `mist-cli/src/main/java/io/mist/cli/MistRunner.java:710`
    inside `bootstrapTraceShapeOracle()` (signature returns the oracle).
  - Prod: emitted into generated test classes by
    `mist-cli/src/main/java/io/mist/cli/writer/MultiServiceRESTAssuredWriter.java:1245`
    and L1247 (the two branches differ by whether `tsoPath` exists).
  - Test: `mist-core/src/test/java/io/mist/core/oracle/shape/TraceShapeOracleIntegrationTest.java:41,62,106`.
- **`evaluate(...)` call site in emitted code:**
  `MultiServiceRESTAssuredWriter.java:681`
  (`oracle.evaluate(model, rootApiKey)`).
- **Verdict class:** `mist-core/src/main/java/io/mist/core/oracle/shape/TraceShapeVerdict.java:15`.
  Builder pattern (L43, L45). No `empty()` factory yet — H2 will add one
  per PROMPT § 4.5 / § 6.7.

### 2.1 Invariants (4 files; H2 must NOT modify them)

- `mist-core/src/main/java/io/mist/core/oracle/shape/invariant/SpanTreeShapeInvariant.java`
- `mist-core/src/main/java/io/mist/core/oracle/shape/invariant/StatusPropagationInvariant.java`
- `mist-core/src/main/java/io/mist/core/oracle/shape/invariant/ResponseEnvelopeInvariant.java`
- `mist-core/src/main/java/io/mist/core/oracle/shape/invariant/TimingEnvelopeInvariant.java`

Each exposes `load(rootApiKey, store)` and `evaluate(trace)`. The toggle
lives at the oracle, not in the invariant. Per PROMPT § Phase H2.C #4
the invariant classes stay agnostic of toggles.

## 3. ThompsonScheduler / FaultMiner

- **`ThompsonScheduler` class:**
  `mist-core/src/main/java/io/mist/core/bandit/ThompsonScheduler.java:38`
  with constructors at L48, L52, L56.
- **`ThompsonScheduler` production construction site:**
  `mist-core/src/main/java/io/mist/core/generation/MistGenerator.java:204`
  inside `rankWithBandit(...)` (L201-L211). Already has access to
  `MstConfig.instance()` via L270, L393 — no constructor change needed.
- **`rankWithBandit(...)` call site:** `MistGenerator.java:480`
  (`List<FaultTarget> faultQueue = rankWithBandit(buildFaultInjectionQueue(sc));`).
  Insertion-order fallback path will live inside `rankWithBandit`.
- **`FaultMiner`:** `mist-core/src/main/java/io/mist/core/fault/FaultMiner.java:52`.
  Existing gate at L54 (`public static final String ENABLED_PROPERTY = "mist.fault.mining.enabled";`)
  and read at L440 (`Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"))`).
  Confirmed:
  - Key: `mist.fault.mining.enabled` (note `mist.` prefix; per
    PROMPT § 6 #1 we do not rebrand).
  - Default: `false`.
  - Gating location: `FaultMiner.mine(...)` early-return at L121 when
    disabled.
  - This work **does not** touch any of the above (PROMPT § 6 #2).

## 4. MistRunner / MistMain wiring

- **`MistRunner`:** `mist-cli/src/main/java/io/mist/cli/MistRunner.java`.
  - `bootstrapTraceShapeOracle()` at L674 (returns
    `new TraceShapeOracle(store)` at L710 — this is where we pass in
    the new `MstConfig.Oracle` in Phase H2.C).
  - Banner emission at L240 (`ConsoleProgressBar.banner("v1.6.0-SNAPSHOT")`) —
    we log the `AblationProfile.summary()` immediately after this call
    in Phase H2.D.
  - Writer wire-up at L291 passes the oracle into the writer.

## 5. Generated test class implications

The writer (`MultiServiceRESTAssuredWriter`) emits Java source that
re-instantiates `TraceShapeOracle` inside the test (L1245, L1247) and
calls `oracle.evaluate(...)` at L681. The toggle propagates into the
emitted code transitively: if `mst.oracle.shape.enabled=false` at the
generator JVM, the emitted file *still* constructs an oracle (because
the writer always emits that boilerplate), but the test-execution JVM
will see the same property (passed via `-D` or properties file) and
the oracle's `evaluate(...)` will short-circuit. No emit-side change
is needed for the principal toggle to work end-to-end — the runtime
gate is sufficient.

## 6. Default-flip target: TimingEnvelope

Per PROMPT § 4.3 the one default that flips in this work is
`mst.oracle.shape.invariants.timing.enabled` → `false`. Justification:
papers (`paper/main.tex`, `paper/main_issta.tex` — commits
`b96fb8b6`, `dfc11b46`) drop the TimingEnvelope invariant from the
contribution. Code keeps the class as an experimental hook.

Byte-identical-equivalence implications: because the writer emits the
oracle re-instantiation unconditionally, the only behaviour change
under defaults is that `TimingEnvelope` is skipped inside
`evaluate(...)`. This affects the verdict (and any Allure attachment
that consumes the verdict) but does NOT affect the generated
`Flow_Scenario_*.java` files themselves, since the generation pipeline
is independent of the oracle. Byte-identical equivalence on
`Flow_Scenario_*.java` therefore holds for R3 (PROMPT § 7.5 gate
check).

## 7. Six new keys this work introduces

(verbatim from PROMPT § 4.1; defaults annotated)

| Key | Default | Surface |
|---|---|---|
| `mst.oracle.shape.enabled` | `true` | `TraceShapeOracle.evaluate(...)` |
| `mst.oracle.shape.invariants.span_tree.enabled` | `true` | per-invariant gate in `evaluate(...)` |
| `mst.oracle.shape.invariants.status_propagation.enabled` | `true` | per-invariant gate |
| `mst.oracle.shape.invariants.response_envelope.enabled` | `true` | per-invariant gate |
| `mst.oracle.shape.invariants.timing.enabled` | **`false`** | per-invariant gate |
| `mst.scheduler.bandit.enabled` | `true` | `MistGenerator.rankWithBandit(...)` |

All six fall under the `mst.` namespace already registered in
`MstConfigValidator.NAMESPACES` (L129). Whitelist entries in
`MstConfigValidator.KNOWN_KEYS` (L43-L116) will be added in Phase H2.B.

## 8. AblationProfile placement

- **New class:** `mist-core/src/main/java/io/mist/core/config/AblationProfile.java`
  (does not exist yet).
- **Constructor input:** `MstConfig` (so it can reach `oracle()`,
  `scheduler()`, and the `mist.fault.mining.enabled` value via
  `FaultMiner.ENABLED_PROPERTY`).
- **Banner consumer:** `MistRunner.run()` immediately after
  `ConsoleProgressBar.banner(...)` at L240.

## 9. Out-of-scope (PROMPT § 6 — forbidden patterns)

- Do not rebrand `mst.` → `mist.` or vice-versa.
- Do not change `mist.fault.mining.enabled` key, default, or gate.
- Do not add CLI flags.
- Do not add sub-records beyond `Oracle` and `Scheduler`.
- Do not delete `TimingEnvelopeInvariant`.
- Do not modify paper files (the 4→3 invariant pivot already
  landed in `b96fb8b6` and `dfc11b46`).

## 10. Inventory summary

Every path in PROMPT § 3.1 was verified post-B1 with no path
relocations. The H2 plan can proceed directly to Phase H2.B without
the § 11 escalation #1 trigger.
