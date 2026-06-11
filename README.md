# MIST — Microservice Integration & Scenario Tester

> **MIST** turns OpenTelemetry / Jaeger traces and OpenAPI specs into
> runnable, cross-service workflow tests for microservice REST APIs.
> Its three named contributions are *Root API Mode* (execute the
> trace's entry points, observe internals via traces), the *Sniper
> Strategy* generation engine (one fault per negative variant with full
> per-fault attribution, over an Adaptive Fault Taxonomy that mines
> SUT-specific categories on top of 9 built-in ones), and the *Trace
> Shape Oracle* (a learner + oracle that promotes a Jaeger trace into a
> checkable assertion across four invariant families). Submitted to
> **ISSTA 2026 Tool Demonstrations**.

---

## Architecture at a glance

MIST ships as a **three-module** Maven reactor. The repository contains
no third-party source code: REST-Assured, swagger-parser, Allure, and
JUnit are declared as ordinary Maven library dependencies.

```
mist-parent (root pom.xml, packaging=pom)
├── mist-core   The contribution. All five architectural stages —
│               spec ingest, semantic dependency registry, sequence
│               generator, sniper strategy, trace shape oracle —
│               plus the adaptive fault taxonomy live here.
│               Zero RESTest source.
├── mist-llm    LLM dispatch SPI + Ollama / Gemini / OpenAI-compatible
│               backends, call cache, env-placeholder resolver.
└── mist-cli    User entry. Houses the launcher (io.mist.cli.MistMain →
                mist.jar), the runner (io.mist.cli.MistRunner), the
                JUnit / REST-Assured writer
                (io.mist.cli.writer.MultiServiceRESTAssuredWriter), the
                test executor, the auth helpers, and the SPI provider
                classes that keep mist-core framework-agnostic.
```

There is one entry point: `java -jar mist-cli/target/mist.jar
<your.properties>`. The previous `mist-restest-adapter` module (which
vendored a forked copy of RESTest 1.6.0-SNAPSHOT) was severed in MIST
1.6 — the runtime now uses RESTAssured + swagger-parser as published
library artifacts, the same way every other black-box REST tester does.

---

## Inputs (every run needs these)

A single MIST run is fully described by one **core `.properties`** file.
All paths inside that file are **resolved relative to the file itself**,
so launching MIST from the repo root, from a module subdirectory, or
from an IntelliJ play-button all produce the same result.

| Input | Key | Bundled demo value (relative to the .properties file) |
|---|---|---|
| **OpenAPI spec** of the system under test | `oas.path` | `trainticket/merged_openapi_spec 1.yaml` |
| **MST test configuration** (one YAML file per SUT, copy + edit the bundled one) | `conf.path` | `trainticket/real-system-conf.yaml` |
| **Jaeger / OpenTelemetry traces** (single file *or* directory of `.json` / `.jsonl`) | `trace.file.path` | `trainticket/test-trace` |
| **Target system base URL** | `base.url` | `http://<your-sut-host>:32677` |
| **MST-mode overlay** (extra MIST-only keys) | `mst.config.path` | `trainticket-mst.properties` |

Two more keys sit in the MIST-mode overlay:

| Input | Key | Where to put the secret |
|---|---|---|
| **LLM backend** (Ollama / OpenAI-compatible / Gemini) | `llm.model.type` + `llm.<backend>.*` | env var `${VAR}` resolved at startup — see *API keys* |
| **Injected-faults registry** (optional, for detection-rate evaluation) | `fault.detection.injected.faults.path` | `trainticket/injectedFaults/injected-faults.json` |

The bundled demo ships every input above pre-staged for TrainTicket.
Pick a Quick Start path below depending on whether you have an LLM
API key handy.

> **One launcher.** `mist-cli/target/mist.jar`
> (`Main-Class: io.mist.cli.MistMain`) is the single supported entry
> point. The legacy `restest.jar` and the `TestGenerationAndExecution`
> main class were retired during the 1.6 RESTest sever.

---

## Quick Start A — bundled demo, fully local LLM (no API key)

Best for first-time validation that the tool works on your machine. Uses Ollama, so nothing leaves your laptop.

```bash
# 1. Build the whole reactor (fat JARs for both launch paths)
mvn clean install -DskipTests

# 2. Start Ollama and pull a model (one-time; see https://ollama.com/download)
ollama serve &
ollama pull qwen2.5-coder:14b

# 3. Switch the bundled demo to Ollama (one-time edit; see snippet below)
#    open mist-cli/src/main/resources/My-Example/trainticket-mst.properties
#    and set:
#       llm.model.type=ollama
#       llm.ollama.enabled=true
#       llm.openai_compatible.enabled=false

# 4. Generate + execute against the bundled TrainTicket demo (run from repo root)
java -jar mist-cli/target/mist.jar \
     mist-cli/src/main/resources/My-Example/trainticket-demo.properties

# 5. Render the Allure report
allure/bin/allure generate target/allure-results -o target/allure-report --clean && \
allure/bin/allure open target/allure-report
```

In IntelliJ, the pre-shipped run configuration **"MIST: Demo
(bundled TrainTicket)"** does the same thing with one click —
working directory is fixed to `$PROJECT_DIR$` so the play-button
behaves like the CLI.

## Quick Start B — bundled demo, hosted LLM API (DeepSeek shown)

Same demo, faster generation. Substitute Gemini / OpenAI / any
OpenAI-compatible endpoint by adjusting the env-var name and the
`llm.*` keys (see *LLM backends* below).

```bash
# 1. Build
mvn clean install -DskipTests

# 2. Provide an API key (resolved by ${DEEPSEEK_API_KEY} placeholder in the MST file)
export DEEPSEEK_API_KEY=sk-...

# 3. The bundled demo is already wired for DeepSeek
#    (llm.model.type=openai_compatible,
#     llm.openai_compatible.url=https://api.deepseek.com/v1/chat/completions).
#    Just run:
java -jar mist-cli/target/mist.jar \
     mist-cli/src/main/resources/My-Example/trainticket-demo.properties

# 4-5. Same Allure rendering as Quick Start A.
```

## Quick Start C — your own microservice system

Generate a conf, point a `.properties` file at your spec, run.

```bash
# 1. Build (one-time)
mvn clean install -DskipTests

# 2. Drop your inputs anywhere — for example, alongside the bundled demo:
#       <yourdir>/openapi.yaml
#       <yourdir>/test-trace/*.json   (one or more Jaeger / OTel traces)

# 3. Author the MST test configuration YAML for your spec (one-time per spec
#    change). The bundled real-system-conf.yaml is the schema; copy it and
#    edit the operations list to match your spec. (The standalone CLI
#    config generator was retired during the 1.6 RESTest sever; reach out
#    via GitHub issues if you'd like the regeneration helper reinstated.)

# 4. Copy the bundled .properties files as a starting point:
#       cp mist-cli/src/main/resources/My-Example/trainticket-demo.properties \
#          <yourdir>/system-demo.properties
#       cp mist-cli/src/main/resources/My-Example/trainticket-mst.properties \
#          <yourdir>/system-mst.properties
#    Then in <yourdir>/system-demo.properties update FIVE keys (paths are
#    resolved relative to the .properties file, so write them relative to
#    <yourdir>):
#       oas.path           → openapi.yaml
#       conf.path          → your-mst-conf.yaml         (from step 3)
#       trace.file.path    → test-trace/
#       base.url           → your system's HTTP entry point
#       mst.config.path    → system-mst.properties      (the MST overlay)

# 5. Launch:
java -jar mist-cli/target/mist.jar <yourdir>/system-demo.properties
```

In IntelliJ, the **"MIST: Demo (bundled TrainTicket)"** run
configuration is a template you can copy + edit for your own SUT.

After any run, the fault-detection report lands under
`logs/fault-detection-reports/`, CSV stats under `target/test-data/`,
and the generated JUnit sources under the directory you pointed
`test.target.dir` at.

## Quick Start D — Reproduce the paper's numbers (artifact track)

For artifact evaluation, peer review, or any context where you need
the **byte-identical** generated test suite the paper reports. The
offline claim is scoped to *generation*: the bundled noexec profile
disables the LLM (`llm.enabled=false`, hardcoded negative inputs), so
under `-Drandom.seed=<n>` two consecutive runs produce byte-for-byte
identical `Flow_Scenario_*.java` files under
`mist-cli/src/test/java/trainticket_twostage_test/`, in a directory
named after the seed (`TrainTicketTwoStageTest_42`; unseeded or
execution runs use a per-run timestamp instead) — no SUT, no API key,
no network at all. Re-verified on 2026-06-09 from a fresh clone inside
a no-network namespace (123 files, identical SHA-256 sums across two
runs); the earlier protocol is in
[`debug/Conference-refinement/PROMPT_VERIFY_FIXES.md`](debug/Conference-refinement/PROMPT_VERIFY_FIXES.md).

```bash
# 1. Build the reactor (same as Quick Start B).
mvn clean install -DskipTests

# 2. Reproduce the paper's generated suite byte-identically, offline
#    (noexec profile: skips execution, LLM disabled, no key needed).
java -Drandom.seed=42 -jar mist-cli/target/mist.jar \
     mist-cli/src/main/resources/My-Example/trainticket-demo-noexec.properties
find mist-cli/src/test/java/trainticket_twostage_test \
     -name 'Flow_Scenario_*.java' -exec sha256sum {} \; | sort > /tmp/run1.sums
# Repeat the same java command, write /tmp/run2.sums, then:
diff /tmp/run1.sums /tmp/run2.sums   # expect empty
```

The headline **detection-rate** number (10/10 injected faults) is a
*live* result, not an offline one: it needs the TrainTicket SUT up
(`evaluation/suts/trainticket/deploy/deploy.sh`, heavy — see
`REPRODUCE.md` §6.3) plus an LLM key, and the exact count varies with
LLM output run to run. The evidence of record is the committed run
report
[`debug/negative_test/runs/run22-fault-detection-10of10.txt`](debug/negative_test/runs/run22-fault-detection-10of10.txt);
`REPRODUCE.md` §5 reproduces the trace-oracle headline results offline
from committed traces instead.

### Ablation rows (Path B § 4.2)

The paper's ablation table is reproduced by changing only the JVM
overrides — the same `.properties` file drives every row, so any
difference between rows is causally attributable to the toggled
contribution. Each row is independently byte-deterministic under its
seed; the startup banner (`[MIST] ablation profile: ...`) identifies
the active row on every run.

| Row | Configuration | JVM overrides |
|---|---|---|
| **R1** | MIST-full | `-Drandom.seed=42 -Dmist.fault.mining.enabled=true` |
| **R2** | MIST − trace-shape-oracle | `-Drandom.seed=42 -Dmst.oracle.shape.enabled=false -Dmist.fault.mining.enabled=true` |
| **R3** | MIST − adaptive-fault (bundled default) | `-Drandom.seed=42` |
| **R4** | MIST − trace-shape − adaptive | `-Drandom.seed=42 -Dmst.oracle.shape.enabled=false` |

Finer-grained per-invariant gates
(`mst.oracle.shape.invariants.{span_tree,status_propagation,response_envelope,timing}.enabled`)
are documented in
[`mist-cli/src/main/resources/My-Example/trainticket/flow.md`](mist-cli/src/main/resources/My-Example/trainticket/flow.md)
§ "H2 ablation toggles". The full ablation matrix layout (rows × SUTs
× metrics) lives in
[`docs/mst-plans/PATH_B_POSITIONING.md`](docs/mst-plans/PATH_B_POSITIONING.md) § 4.

### The LLM call cache is local replay, not a shipped artifact

The repository does **not** ship a pre-populated
`.mist/llm-call-cache.json` (the whole `.mist/` directory is
gitignored). The cache makes *your own* seeded re-runs offline: with
the default knobs (`mist.llm.cache.read=auto`,
`mist.llm.cache.write=true`), one cold LLM-enabled run on your machine
populates the cache, and every later run under the same
`-Drandom.seed=<n>` replays from it without touching the network.

Cache entries are keyed by a SHA-256 of the full prompt, so any change
to a prompt template, the trace corpus, or the model invalidates the
affected entries — they silently fall through to the live backend.
That is why a committed "blessed cache" is not the reproduction path
of record (an empirical check on 2026-06-09 confirmed a months-old
cache no longer covers the current prompts). If you still want to
ship one alongside a frozen SUT snapshot:

```bash
# 1. Wipe the cache and run once cold under the canonical seed
#    (the only run that needs an API key).
export DEEPSEEK_API_KEY=sk-...
rm -f .mist/llm-call-cache.json
java -Drandom.seed=42 -jar mist-cli/target/mist.jar \
     mist-cli/src/main/resources/My-Example/trainticket-demo.properties

# 2. Re-run the same command offline to prove the cache covers the run,
#    then commit it as data (targeted .gitignore negation):
#       echo '!/.mist/llm-call-cache.json' >> .gitignore
git add .mist/llm-call-cache.json .gitignore
git commit -m "data: bless LLM cache for artifact reproducibility"
```

Step 2's offline re-run is the acceptance test: bless a cache only at
the exact commit whose prompts produced it.

---

## What this does

For each microservice scenario reconstructed from a Jaeger trace, MIST emits one JUnit class that:

1. **logs in once per JVM** (configurable; see *Auth strategy*),
2. **replays each root API in order** (*Root API Mode* — exercise the
   trace's entry points; observe internal services via the Jaeger trace
   rather than calling them directly), wiring data between steps via
   cross-trace data-dependency inference and a JIT producer-binding
   registry built from the OpenAPI spec,
3. **runs the *Sniper Strategy***: each negative variant carries
   exactly one fault, drawn from the *Adaptive Fault Taxonomy* — 8
   built-in categories (TYPE_MISMATCH, REGEX_MISMATCH, SEMANTIC_MISMATCH,
   OVERFLOW, EMPTY/NULL, SPECIAL_CHARACTERS, BOUNDARY_VIOLATION) plus
   any per-SUT categories the `FaultMiner` proposes from observed
   4xx/5xx responses + OpenAPI description fields. An
   `ApplicabilityMatrix` filters which faults reach which parameters,
   and the invalid-input pool is keyed per `(parameter, location)` so
   one bad value never bleeds across slots,
4. **checks each response against the *Trace Shape Oracle***: a learner
   builds per-root-API invariants across four families (span tree
   shape, status propagation, timing envelope, response envelope) from
   a known-good trace corpus; the oracle verifies live responses
   against the persisted invariants and returns a verdict with
   evidence. (The Phase 2 response envelope invariant subsumes the
   legacy `SoftErrorRuleCache` and is persisted at
   `.mist/trace-shape-invariants.json`.)
5. **explores untriggered status codes** (401/403/404/409/…) via auth-manipulation and LLM-suggested input mutations.

Full pipeline (Phase 1 cross-trace merging → Phase 2 session merging → Phase 2.5 dedup → Phase 3 component shattering → Phase 4 baseline decomposition → variant generation) is documented in the in-code Javadoc on `io.mist.core.generation.MistGenerator`.

---

## Requirements

| | Version |
|---|---|
| JDK | **21** (a full JDK, not a JRE — MIST compiles the generated tests in-process; bytecode target stays `-source 11 -target 11`) |
| Maven | 3.6+ |
| Allure CLI | bundled in `allure/` (Java 8+ required by Allure itself) |
| LLM backend | one of: Ollama (local), DeepSeek / OpenAI-compatible HTTP, Google Gemini |
| Target system | reachable HTTP base URL + an OpenAPI spec + Jaeger traces |

The TrainTicket demo expects a TrainTicket deployment you provide; set the `base.url` and `jaeger.base.url` keys in the config to your own host (e.g. `http://localhost:32677` via `kubectl port-forward`).

---

## Configuration layout

Configuration is **split into two files** — the core `.properties` you
pass on the command line plus an MST overlay it references via
`mst.config.path`. The split keeps the ~70 MIST-specific keys out of
the core file so the core file only carries the OpenAPI / trace /
target-URL inputs that any tester would need.

```
mist-cli/src/main/resources/My-Example/
├── trainticket-demo.properties   # core (~30 keys) + mst.config.path pointer
└── trainticket-mst.properties    # MST overlay (~70 keys: LLM, smart fetch,
                                  # jaeger, fault detection, enhancer,
                                  # status-code exploration, root-API
                                  # registry, trace merging, auth, …)
```

`mst.config.path` itself and every INPUT-path key inside both files
(`oas.path`, `conf.path`, `trace.file.path`,
`fault.detection.injected.faults.path`, the various registry paths) are
resolved **relative to the .properties file's own directory** by
`io.mist.cli.MistPathResolver` at startup. This is why the bundled
values look like `trainticket/merged_openapi_spec 1.yaml` rather than
absolute paths — the path no longer depends on where the user launches
MIST from.

OUTPUT paths (`test.target.dir`, `allure.results.dir`,
`data.tests.dir`, the various `.mist/*-cache.json` keys) keep the
Maven convention of being relative to the JVM CWD; the run
configurations under
[`.idea/runConfigurations/`](.idea/runConfigurations) pin CWD to
`$PROJECT_DIR$` so the IDE matches the CLI.

---

## LLM backends

Set `llm.model.type` in `*-mst.properties` to one of `openai_compatible`, `gemini`, or `ollama`. The unused backends can be left in the file with `*.enabled=false` — only the selected one is contacted.

> **Heads-up on naming.** Earlier versions called the OpenAI-compatible backend `local` (`llm.model.type=local`, `llm.local.*`). That was misleading — DeepSeek, OpenAI, and the like are *remote hosted APIs*, not local models. The new canonical name is `openai_compatible`, but the old `local` value and `llm.local.*` keys are still accepted as deprecated aliases (you'll see a one-time deprecation warning on startup).

### Ollama (fully local, default in flow.md examples)

```properties
llm.model.type=ollama
llm.ollama.url=http://localhost:11434
llm.ollama.model=qwen2.5-coder:14b
```

Start the daemon (`ollama serve`) and pull the model (`ollama pull qwen2.5-coder:14b`). No API key required.

### OpenAI-compatible HTTP endpoint (DeepSeek, OpenAI, OpenRouter, Together, ...)

Anything that implements OpenAI's `/v1/chat/completions` request/response shape works here — that includes DeepSeek (the bundled default), OpenAI itself, OpenRouter, Together, Groq, Mistral's hosted API, and self-hosted OpenAI shims like `gpt4all` or `llama.cpp --api`. To swap providers, change just the URL, model name, and API-key env var below.

```properties
llm.model.type=openai_compatible
llm.openai_compatible.url=https://api.deepseek.com/v1/chat/completions
llm.openai_compatible.model=deepseek-chat
llm.openai_compatible.api.key=${DEEPSEEK_API_KEY}
```

For a self-hosted server (e.g. `gpt4all` on localhost), leave `api.key` empty:

```properties
llm.openai_compatible.url=http://localhost:4891/v1/chat/completions
llm.openai_compatible.model=Meta-Llama-3-8B-Instruct.Q4_0.gguf
llm.openai_compatible.api.key=
```

The `${VAR}` syntax is resolved at startup — see *API keys* below.

### LLM cache control

MIST persists every LLM response under `.mist/llm-call-cache.json`
(SHA-256-keyed on the prompt). Two knobs gate the cache, settable from
the MST `.properties` file or via `-D` (the latter takes precedence):

```properties
# mist.llm.cache.read = auto | true | false
#   auto (default): read the cache only when -Drandom.seed=<n> is set,
#                   so seeded runs reproduce byte-identically and
#                   unseeded benchmark runs hit the real LLM.
#   true:           always read — short-circuit LLM calls when the
#                   cache is pre-populated (demo / reviewer artifact).
#   false:          never read — every prompt goes to the LLM backend.
mist.llm.cache.read=auto

# mist.llm.cache.write = true | false
#   true (default): write every response into the cache so future
#                   seeded runs can replay them.
#   false:          run-isolated — don't touch the shared cache file
#                   (one-off no-cache benchmark).
mist.llm.cache.write=true
```

Common combinations:

| Goal | Setting |
|---|---|
| Reproduce a published number (cache + temp=0) | `-Drandom.seed=42`, leave defaults |
| Cold-LLM run, full network calls | `-Dmist.llm.cache.read=false` |
| Run isolated from the shared cache file | `-Dmist.llm.cache.read=false -Dmist.llm.cache.write=false` |
| Use a side-channel cache file | `-Dmist.llm.cache.path=.mist/run-A.json` |

The same three knobs also work as command-line `-D` flags for IDE play-button runs that bypass the MST file.

### Google Gemini

```properties
llm.model.type=gemini
llm.gemini.api.key=${GEMINI_API_KEY}
llm.gemini.model=gemini-2.0-flash-exp
llm.gemini.api.url=https://generativelanguage.googleapis.com/v1beta/models
```

---

## API keys

`llm.openai_compatible.api.key`, `llm.gemini.api.key`, and any other secret-bearing key supports `${VAR}` and `${VAR:default}` placeholder syntax. The resolver tries, in order:

1. `System.getenv("VAR")` — `export DEEPSEEK_API_KEY=sk-...` before launching.
2. `System.getProperty("VAR")` — `-DDEEPSEEK_API_KEY=sk-...` on the JVM command line (handy for IntelliJ run configs).
3. `.api_keys/VAR` (or `~/.restest/api_keys/VAR`) — a file containing only the secret. The `.api_keys/` directory is gitignored.
4. The literal `default` after `:` in `${VAR:default}`.

A missing key resolves to the empty string, in which case the request is sent unauthenticated rather than literally containing the placeholder text. The full resolution logic lives in [`LLMConfig.resolveEnvPlaceholder`](mist-llm/src/main/java/io/mist/llm/LLMConfig.java).

---

## Auth strategy for the *target* system

The generated tests use `io.mist.cli.auth.MstAuthHandler` to obtain and stamp tokens. Configured by `auth.*` keys in the MST file:

| `auth.mode` | Effect |
|---|---|
| `none` | No `Authorization` header on any request. |
| `static_token` | Use `auth.static.token` verbatim; never call `/login`. |
| `per_jvm` (default) | Lazy login on first request, cached for the whole JVM. ~2400 logins → ~1. |
| `per_test` | Legacy: fresh login per test method. |

Endpoints matching `auth.skip.path.patterns` (CSV of regex, e.g. `^/actuator,^/api/v1/users/login`) are sent without an `Authorization` header regardless of mode. On HTTP 401, `io.mist.cli.auth.MstAuthRefreshFilter` invalidates the cached token and retries once — disabled automatically for tests that intentionally manipulate auth (e.g. `INVALID_TOKEN` exploration tests).

---

## Outputs

| Path (relative to JVM CWD) | Content |
|---|---|
| `<test.target.dir>/<package>/<TestClassName>_<timestamp>/` | Generated JUnit test sources (default `mist-cli/src/test/java`) |
| `mist-cli/target/test-classes/<package>/...`   | Compiled `.class` files |
| `target/allure-results/`                       | Raw Allure JSON (per test) |
| `target/allure-report/`                        | Rendered HTML report (after `allure generate`) |
| `logs/fault-detection-reports/`                | Injected-fault detection summary, matched against `injectedFaults/injected-faults.json` |
| `logs/llm-communications/`                     | Per-call LLM request/response transcripts (`LLMCommunicationLogger`; gitignored) |
| `.mist/llm-call-cache.json`                    | SHA-256-keyed cache of LLM responses. Default: read enabled only when `-Drandom.seed=<n>` is set (so seeded runs reproduce byte-identically and unseeded benchmark runs hit the real LLM); write always on. Both gates are overridable from the MST `.properties` file or via `-D`. See *LLM cache control* below. |
| `.mist/parameter-error-analysis-cache.json`    | Parameter-error analyser cache |
| `.mist/intelligent-analysis-cache.json`        | Trace error analyser intelligent cache |
| `.mist/trace-shape-invariants.json`            | Phase 2 Trace Shape Oracle persisted invariants |
| `.mist/mist-mined-fault-types.yaml`            | Phase 3 mined SUT-specific fault categories (when `mist.fault.mining.enabled=true`) |
| `target/test-data/`                            | CSV stats (test cases, results, time) |

> **`.mist/` vs `target/`.** Everything under `target/` is recreated by
> Maven and is wiped by `mvn clean`. The `.mist/` directory holds the
> *persistent cross-run state* that MIST needs to stay reproducible —
> the LLM call cache, the mined fault-type catalogue, and the
> Trace-Shape-Oracle invariant store all live here and **survive
> `mvn clean`**. `.mist/` is gitignored by default; commit a blessed
> snapshot alongside the SUT when you want a byte-reproducible
> artifact.

> The legacy `target/soft-error-rule-cache.json` is gone — its contract
> moved into the Phase 2 `ResponseEnvelopeInvariant` and the persisted
> invariant store at `.mist/trace-shape-invariants.json`. See Phase 2.E
> of [`PATH_B_REBUILD_PLAN.md`](debug/Conference-refinement/PATH_B_REBUILD_PLAN.md).

---

## Repository data showcase

The TrainTicket dataset bundled with the tool, all under
`mist-cli/src/main/resources/My-Example/trainticket/`:

| Asset | Description |
|---|---|
| `merged_openapi_spec 1.yaml` | 265-operation merged OpenAPI spec; MIST's black-box scope covers the 37 REST-exposed services |
| `real-system-conf.yaml` | MIST test configuration. Copy + edit for your own SUT (the standalone CLI generator was retired during the 1.6 RESTest sever). |
| `test-trace/*.json` | OpenTelemetry traces used to mine workflow scenarios |
| `injectedFaults/injected-faults.json` | Ground-truth fault registry for detection-rate evaluation |
| `noun-map.default.yaml` (in `mist-core/src/main/resources/mist/`) | Default noun-key map used by the trace workflow extractor |

---

## Repository layout

```
mist-parent (root pom.xml, packaging=pom)
├── mist-core/
│   └── src/main/java/io/mist/core/
│       ├── spec/                      OpenAPI parser wrapper + Operation /
│       │                              Parameter pojos; SemanticDependencyRegistry
│       ├── multiservice/              MST test-configuration pojos + IO
│       ├── generation/                MistGenerator (the 5-phase sequence pipeline:
│       │                              cross-trace merge → session merge → dedup →
│       │                              component shatter → baseline decompose → variants)
│       ├── workflow/                  TraceWorkflowExtractor + WorkflowPipeline +
│       │                              Phase 2.5–4 stage implementations
│       ├── oracle/shape/              Trace Shape Oracle:
│       │                              SpanTreeShape / StatusPropagation /
│       │                              TimingEnvelope / ResponseEnvelope
│       │                              invariants + Learner + Oracle + Verdict
│       ├── fault/                     Adaptive Fault Taxonomy:
│       │                              FaultType + FaultTypeRegistry +
│       │                              ApplicabilityMatrix + FaultMiner
│       ├── enhancer/                  Test-case enhancer + status-code
│       │                              exploration (LLM-driven retries)
│       ├── analysis/                  FaultDetectionTracker, TraceErrorAnalyzer,
│       │                              TraceShapeAdapter
│       ├── auth/                      AuthManipulationStrategy
│       └── util/                      ConsoleProgressBar, ConsoleDedupFilter,
│                                      FileManager, Timer, PropertyManager, …
├── mist-llm/
│   └── src/main/java/io/mist/llm/    LLM client SPI + concrete backends:
│                                      LLMClient, LLMService, LLMCallCache,
│                                      LLMConfig (env placeholder resolver,
│                                      seed gate), OllamaApiClient,
│                                      GeminiApiClient (OpenAI-compatible
│                                      HTTP routed through LLMService)
└── mist-cli/
    └── src/main/java/io/mist/cli/
        ├── MistMain                    Launcher (→ java -jar mist-cli/target/mist.jar)
        ├── MistRunner                  Top-level orchestrator
        ├── MistRunResult               Exit-code carrier
        ├── MistPathResolver            Properties-relative path resolution
        ├── SemanticRegistryDumper      Diagnostic tool
        ├── SemanticRegistryEvaluator   Diagnostic tool
        ├── TraceErrorAnalysisMain      Diagnostic tool
        ├── TraceMain                   Diagnostic tool
        ├── auth/                       MstAuthHandler, MstAuthRefreshFilter
        ├── writer/                     MultiServiceRESTAssuredWriter
        └── spi/                        RestestMistSpec, RestestMistSpecLoader,
                                        RestAssuredMistTestWriter,
                                        MavenSurefireMistTestExecutor,
                                        PojoConverter
```

There is no longer a `mist-restest-adapter` module — it was deleted in
the 1.6 RESTest sever, along with the classic `RT / CBT / ART / FT /
LLM` generators that lived only in the legacy `TestGenerationAndExecution`
entry. Use `mist.jar` for all MIST work; reach out on GitHub if you
need a specific classic-generator behaviour reinstated as an SPI.

---

## Citation

```bibtex
@misc{MIST,
  title  = {{MIST: Trace-Driven, LLM-Assisted Multi-Service Test Generation}},
  author = {<authors>},
  note   = {Submitted to ISSTA 2026 Tool Demonstrations.
            Citation to be filled in on acceptance.},
  year   = {2026}
}
```

MIST acknowledges intellectual debt to RESTest, which informed the
JUnit + REST-Assured test-generation pattern MIST adopts in
`mist-cli/writer/MultiServiceRESTAssuredWriter`; if you cite MIST in
a context where the predecessor is relevant, please also cite:

```bibtex
@inproceedings{MartinLopez2021Restest,
  title     = {{RESTest: Automated Black-Box Testing of RESTful Web APIs}},
  author    = {Alberto Martin-Lopez and Sergio Segura and Antonio Ruiz-Cort\'{e}s},
  booktitle = {Proceedings of the 30th ACM SIGSOFT International Symposium on Software Testing and Analysis},
  series    = {ISSTA '21},
  publisher = {Association for Computing Machinery},
  year      = {2021}
}
```

---

## License

Distributed under the [GNU Lesser General Public License v3.0](LICENSE), inherited from RESTest. Includes Allure Framework © Qameta Software OÜ under the Apache 2.0 License.
