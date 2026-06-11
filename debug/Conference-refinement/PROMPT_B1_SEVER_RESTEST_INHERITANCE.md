# MIST — Path B1: Sever RESTest Inheritance & Vendor the Generation Core

> **Audience.** Self-contained execution brief for a coding agent (Claude
> Code or equivalent) that will execute the deep architectural rebuild:
> cut every code-level dependency between MIST's generation pipeline and
> upstream RESTest's `AbstractTestCaseGenerator` family, by vendoring
> the minimal slice of RESTest internals MIST actually uses into
> `mist-core`. After this work lands, `mist-core` and `mist-cli`
> together can produce, write, and execute MIST tests with **zero**
> compile-time edges to `es.us.isa.restest.*`.

---

## 0. Hard Rules — Read Before Touching Anything

### 0.1 Branch and authorship

- **`inject-detection` is the user's main branch.** Treat it the same
  way you would treat `main` or `master` on any other repository.
- **Branch off `inject-detection`.** Create a new long-lived working
  branch named `claude/b1-sever-restest-inheritance` (or whatever
  name the user supplies). Do **not** commit directly to
  `inject-detection`.
- **Final push goes to `inject-detection`.** When all verification
  gates in § 7 pass and the user has explicitly approved, fast-forward
  / squash-merge the working branch into `inject-detection` and push
  `inject-detection` to `origin`. Do **not** open a pull request
  unless the user explicitly asks.
- Use `git push -u origin inject-detection` (after merge) and
  `git push -u origin claude/b1-sever-restest-inheritance` (for
  intermediate work).
- **If push fails for network reasons**, retry up to 4 times with
  exponential backoff (2s, 4s, 8s, 16s). Do **not** retry on
  non-network errors (rejected pushes, hook failures) — diagnose
  the actual cause.

### 0.2 No-Claude-attribution policy

Every commit and every push artifact produced by this work must look
like it was authored by a human. Concretely:

- **Do NOT** include `🤖 Generated with [Claude Code]` lines in commit
  messages.
- **Do NOT** include `Co-Authored-By: Claude <noreply@anthropic.com>`
  trailers.
- **Do NOT** include any `https://claude.ai/code/session_*` URL
  trailers.
- **Do NOT** mention "Claude", "Anthropic", "AI", "agent", "LLM
  generated", or any equivalent in commit messages, branch names,
  PR descriptions, file headers, or code comments.
- **Do NOT** modify the repository's git config to change the author
  or committer. Use whatever `user.name` / `user.email` is already
  configured.
- Commit messages should look like ordinary engineering commits:
  imperative subject line (≤ 72 chars), optional body explaining
  *why*, no marketing.
- File-level: do not insert generated-by banners, attribution
  comments, or "this file was written by …" preambles.

### 0.3 Scope discipline

- This work has exactly one objective: **sever the RESTest
  inheritance edge for the MIST generation pipeline** and prove the
  severed pipeline is behaviour-equivalent to the inherited one. No
  new features. No improvements to algorithms. No "while I'm here"
  refactors of unrelated code.
- If you find a bug along the way that is **not** caused by this
  refactor, write it down in a follow-up note (a one-liner in
  `debug/Conference-refinement/B1_FOLLOWUPS.md`, create the file
  if it does not exist) and keep going. Do not fix it in this
  branch.
- If you find a tempting abstraction that would be "nicer" than the
  vendoring path described in § 5, **resist**. The vendoring path is
  picked deliberately for two reasons: (a) it is mechanical and
  byte-comparable; (b) reviewers at ICSE/FSE main track will read
  the `mist-core` source and ask "does this code do its own work or
  call out to a framework?" — vendoring puts the answer in plain
  sight.

### 0.4 Behaviour preservation

- Under `-Drandom.seed=42`, the bundled TrainTicket demo must produce
  **byte-identical** `Flow_Scenario_*.java` files before and after
  this rebuild. This is the single most important verification gate
  in § 7.
- If you cannot achieve byte-identical output, the rebuild is
  incomplete. Diagnose the divergence; do not paper over it.
- The legacy launch path
  (`java -jar mist-restest-adapter/target/restest.jar`) must keep
  working. After the rebuild, both `mist.jar` and `restest.jar`
  reach the same in-process generator; the diff continues to be
  empty.

### 0.5 Tooling discipline

- Use `Read`/`Edit`/`Write` over `cat`/`sed`/`awk` for file work.
- Use `Bash` only for build, test, search, and git.
- For broad codebase exploration spanning more than three queries,
  spawn the `Explore` subagent rather than running searches yourself.
- Build verification after every phase: `mvn -q -DskipTests compile`
  must pass. If it fails, **stop and diagnose** before continuing.
  Do not chain-edit through compile errors.
- Test verification after Phases B1.D, B1.F, B1.G:
  `mvn -q test -Dtest='Mist*'`. New tests added by this work must
  pass; pre-existing failures are documented in § 9 of
  `CRITICAL_FIXES_S_A_1_6.md` and may be tolerated only if they
  predate this branch.

---

## 1. Mission

MIST's flagship paper target is the main research track of ICSE 2027,
FSE 2027, ASE 2027, or ISSTA 2027. A reviewer on any of those PCs will
read `mist-core` source and ask:

> "How much of this is your code, and how much is RESTest?"

Today the honest answer is: **the contribution lives in `mist-core`,
but the generation pipeline that produces tests lives in
`mist-restest-adapter` and extends `AbstractTestCaseGenerator` from
RESTest.** Reviewers will read that and downgrade the novelty
assessment because they cannot cleanly separate MIST's algorithmic
work from the framework it inherits.

This rebuild changes the answer to:

> "The entire generation pipeline lives in `mist-core` with zero
> compile-time edges to RESTest. The adapter module exists only to
> bridge MIST's interfaces to RESTest's writer for backwards
> compatibility with the ICSME 2026 / ISSTA 2026 tool demo."

That sentence is what this rebuild buys. Nothing more, nothing less.

---

## 2. Prerequisites — Required Reading

Before writing a single line of code, read these in order and confirm
you understand each. Take notes if helpful but do not summarise back
to the user.

1. **`debug/Conference-refinement/PATH_B_REBUILD_PLAN.md`** — the
   original Path B plan. Pay special attention to:
   - § 2 (Current vs. target architecture diagrams)
   - § Phase 1.C (the original module split — already executed)
   - § Phase 1.C "Three options considered" — **Option C ("Vendor
     RESTest internals into mist-core") is exactly the work this
     prompt instructs you to do**. The original plan deferred it.
     This prompt un-defers it.
2. **`debug/Conference-refinement/CRITICAL_FIXES_S_A_1_6.md`** — the
   eleven critical fixes that landed before Path B. You do not need
   to redo any of them, but you must know they exist because their
   new symbols (`MstConfig`, `PoolKey`, `WorkflowPipeline`,
   `LLMCallCache`, `NounKeyMap`, `runSingleRootDedupPass`) are now
   integral to the pipeline you are about to move.
3. **`mist-restest-adapter/src/main/resources/My-Example/trainticket/flow.md`** —
   the algorithm document. Read at least:
   - § "MST Mode End-to-End Flow"
   - § "Workflow Scenario Extraction"
   - § "Reproducibility"
4. **`README.md`** at the repo root — the 4-module reactor layout
   and the bundled demo invocation.
5. **`docs/mst-plans/PATH_B_POSITIONING.md`** — the positioning
   document. You do not need to defend the positioning, but you
   need to know which classes the positioning doc cites by name,
   because those class names should survive the rebuild (with their
   packages updated). If you must rename a class that the
   positioning doc cites by `file:line`, note the rename in
   `B1_FOLLOWUPS.md` so the positioning doc can be updated
   separately.

### 2.1 Pre-flight verification commands

Run all of these before starting any code work. They confirm the
post-`inject-detection` state matches what this prompt assumes. If
any command fails or produces unexpected output, stop and escalate.

```bash
# Confirm you are on inject-detection HEAD and clean
git status
git rev-parse --abbrev-ref HEAD     # must print 'inject-detection'

# Confirm the four-module reactor exists
test -d mist-core/src/main/java/io/mist/core
test -d mist-llm/src/main/java/io/mist/llm
test -d mist-restest-adapter/src/main/java/es/us/isa/restest
test -d mist-cli/src/main/java/io/mist/cli

# Confirm mist-core has zero edges to es.us.isa.*
! grep -rE 'es\.us\.isa' mist-core/src/main/java

# Confirm the inheritance edge to sever still exists
grep -n 'extends AbstractTestCaseGenerator' \
  mist-restest-adapter/src/main/java/es/us/isa/restest/generators/MultiServiceTestCaseGenerator.java

# Confirm the demo builds cleanly today (so any later breakage is yours)
mvn -q -DskipTests clean install

# Capture the pre-rebuild seeded baseline (KEEP THIS — you'll diff against it)
mkdir -p /tmp/b1-baseline
java -Drandom.seed=42 -jar mist-cli/target/mist.jar \
  mist-restest-adapter/src/main/resources/My-Example/trainticket-demo.properties \
  > /tmp/b1-baseline/run.log 2>&1
find target/test-cases -name 'Flow_Scenario_*.java' -exec sha256sum {} \; \
  | sort > /tmp/b1-baseline/scenarios.sums
cp -r target/test-cases /tmp/b1-baseline/test-cases-pre

# Confirm at least one .sums line exists
test -s /tmp/b1-baseline/scenarios.sums
```

If any of the above commands fail or print empty results, **do not
start the rebuild**. Report back to the user with the failing
command and its output.

---

## 3. Current State

### 3.1 Module layout (today, on `inject-detection`)

```
Rest/
├── mist-core/                       (~clean — no RESTest deps)
│   └── io.mist.core.{oracle,fault,bandit,value}
├── mist-llm/                        (~clean — no RESTest deps)
│   └── io.mist.llm.{LLMClient,LLMCallCache,…}
├── mist-restest-adapter/            (HEAVY RESTest dep — the target of this rebuild)
│   └── es.us.isa.restest.{workflow,generators,inputs,writers,main,…}
└── mist-cli/                        (~clean — only imports MistRunner + MstConfig)
    └── io.mist.cli.{MistMain,MistConfGenMain}
```

### 3.2 The inheritance edge to sever

```java
// mist-restest-adapter/src/main/java/es/us/isa/restest/generators/MultiServiceTestCaseGenerator.java
public class MultiServiceTestCaseGenerator extends AbstractTestCaseGenerator {
    //                                              ^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    //                                              RESTest base class — this is what we cut
}
```

`AbstractTestCaseGenerator` lives in `es.us.isa.restest.generators`
and pulls in the entire RESTest test-generation framework
(`TestConfigurationObject`, `Operation`, `TestParameter`,
`TestParameterFilter`, the abstract `generate()` shell, statistics
infrastructure, …). MIST overrides almost all of it but is forced
to declare `extends` because the writer pipeline and the legacy
`TestGenerationAndExecution.main` expect that type.

### 3.3 What `MultiServiceTestCaseGenerator` actually uses from RESTest

Run these commands and capture the output into
`debug/Conference-refinement/B1_INVENTORY.md` (create if absent) as
**Phase B1.A's deliverable**. Do not start moving code until this
inventory exists.

```bash
# All es.us.isa imports inside the adapter's MIST-specific packages
grep -rh '^import es\.us\.isa' \
  mist-restest-adapter/src/main/java/es/us/isa/restest/{generators,workflow,inputs,validation,analysis,registry,enhancer,configuration,llm,main,writers,testcases,util} \
  2>/dev/null | sort | uniq -c | sort -rn

# Per-class dependency
for f in $(find mist-restest-adapter/src/main/java/es/us/isa/restest/{generators,workflow,inputs,validation,analysis,registry,enhancer,configuration,llm} -name '*.java' 2>/dev/null); do
  base=$(basename "$f" .java)
  deps=$(grep -h '^import es\.us\.isa' "$f" | wc -l)
  echo "$deps $base"
done | sort -rn | head -50

# Specifically what MultiServiceTestCaseGenerator inherits / overrides
grep -nE 'super\.|@Override|extends |implements ' \
  mist-restest-adapter/src/main/java/es/us/isa/restest/generators/MultiServiceTestCaseGenerator.java
```

The inventory must enumerate, at minimum:

| Category | What to list |
|---|---|
| **A. RESTest superclass surface** | Every method `MultiServiceTestCaseGenerator` overrides from `AbstractTestCaseGenerator`. Every field of the superclass it reads. Every `super.foo()` it calls. |
| **B. RESTest data classes (consumed)** | Every `es.us.isa.restest.*` type appearing in a field, parameter, or return type inside the MIST packages. Examples: `TestConfigurationObject`, `Operation`, `TestParameter`, `OpenAPISpecification`, `TestCase`. |
| **C. RESTest utilities (called)** | Static utility classes the MIST code calls. Examples: `RESTestUtils`, parameter filters. |
| **D. RESTest interfaces (implemented)** | Interfaces other than `AbstractTestCaseGenerator` that MIST code implements. |
| **E. Already-pure-MIST classes** | The list under § Phase 1.C in `PATH_B_REBUILD_PLAN.md` of "MIST code in the adapter that has no RESTest deps". These move trivially; the rest is where the work is. |

Each entry must carry a `file:line` reference.

---

## 4. Target State

### 4.1 Module dependency graph (after this rebuild)

```
Rest/
├── mist-core/                       (now also owns generation pipeline)
│   ├── io.mist.core.oracle.shape    (Trace Shape Oracle — unchanged)
│   ├── io.mist.core.fault           (Adaptive Fault Taxonomy — unchanged)
│   ├── io.mist.core.bandit          (ThompsonScheduler — unchanged)
│   ├── io.mist.core.value           (ValueProvenance — unchanged)
│   ├── io.mist.core.spec            (NEW: vendored OAS data model)
│   ├── io.mist.core.testcase        (NEW: vendored TestCase / TestParameter)
│   ├── io.mist.core.workflow        (NEW: TraceWorkflowExtractor + pipeline)
│   ├── io.mist.core.generation      (NEW: MistGenerator + variant loop)
│   ├── io.mist.core.registry        (NEW: SemanticDependencyRegistry + RootApiRegistry)
│   ├── io.mist.core.config          (NEW: MistConfig — renamed from MstConfig)
│   └── io.mist.core.spi             (NEW: MistSpecLoader / MistTestWriter / … interfaces)
├── mist-llm/                        (unchanged)
├── mist-restest-adapter/            (thin SPI provider)
│   └── es.us.isa.restest            (only: spec loader bridge + RESTAssured writer + legacy main)
└── mist-cli/                        (now constructs MistGenerator directly)
    └── io.mist.cli                  (MistMain + MistRunner moved here)
```

### 4.2 Inheritance edges (after)

```java
// mist-core/src/main/java/io/mist/core/generation/MistGenerator.java
public final class MistGenerator {
    // No extends. No implements of any RESTest type. Composition only.
}
```

```java
// mist-restest-adapter/src/main/java/es/us/isa/restest/adapter/RestestSpecLoaderImpl.java
public final class RestestSpecLoaderImpl implements io.mist.core.spi.MistSpecLoader {
    // Adapter implements the new SPI by delegating to RESTest's OpenAPISpecification.
}
```

### 4.3 What stays in the adapter (and why)

These three things are **deliberately** kept in `mist-restest-adapter`:

1. **Spec loader bridge.** RESTest has a robust OpenAPI parser
   (`OpenAPISpecification`). Re-implementing one in `mist-core`
   would add 1500+ LOC of YAML/JSON parsing that has nothing to do
   with MIST's contribution. The adapter implements
   `io.mist.core.spi.MistSpecLoader` by wrapping `OpenAPISpecification`.
2. **RESTAssured test writer.** The writer emits compilable JUnit +
   RESTAssured code. The output framework choice (RESTAssured vs.
   raw HTTP vs. something else) is orthogonal to MIST's
   contribution. The adapter implements
   `io.mist.core.spi.MistTestWriter` by wrapping the existing
   `MultiServiceRESTAssuredWriter`.
3. **Legacy `TestGenerationAndExecution` main and `restest.jar`
   build.** Keeps the ICSME 2026 / ISSTA 2026 demo invocation
   working for the user's existing publications. Its MST branch
   continues to be a single delegation into the new entry point.

Everything else — the entire generation pipeline, the variant loop,
the workflow extraction, the dependency registry, the fault pool,
the smart input fetcher, the LLM-driven generators — moves to
`mist-core`.

---

## 5. Phased Plan

Each phase produces a sequence of commits. The phase boundary is a
verification gate (§ 7). Do not start the next phase until the
current phase's gate passes.

> **Estimated total elapsed time: 12-16 weeks of focused work.** If
> you are blocking on a decision, raise it with the user via a one-
> line question and continue with the next independent sub-task in
> the meantime.

### Phase B1.A — Dependency inventory (1 week)

**Goal.** Produce `debug/Conference-refinement/B1_INVENTORY.md` per
§ 3.3. No code changes in this phase.

**Steps.**

1. Run the commands in § 3.3 and structure the output into the five
   categories.
2. For each entry in category B (RESTest data classes), decide one
   of three dispositions:
   - **Vendor**: the class is small (< 200 LOC), has no transitive
     RESTest deps, and is part of MIST's hot path. Move a copy into
     `mist-core` under `io.mist.core.spec` or `io.mist.core.testcase`.
   - **Wrap**: the class is large, has transitive deps, or is owned
     by RESTest's life cycle. Define a `mist-core` interface in
     `io.mist.core.spi` and have the adapter wrap.
   - **Drop**: the class is used in only one place by MIST and that
     usage is dead code or trivially replaceable. Note the
     replacement in the inventory.
3. For category A (superclass overrides), decide which override
   bodies are MIST-specific (move into `MistGenerator`) and which
   are simply re-implementing what the superclass already does (drop
   the override).
4. Commit `B1_INVENTORY.md` to the working branch. **No source
   code changes yet.**

**Gate.** § 7.1.

### Phase B1.B — Vendor data classes into mist-core (2 weeks)

**Goal.** Create the new packages `io.mist.core.spec`,
`io.mist.core.testcase`, `io.mist.core.config` in `mist-core` and
populate them with vendored copies of the RESTest data classes
that category B of the inventory marked as "Vendor". Existing
`mist-restest-adapter` code continues to compile and pass tests
against the old (RESTest) types; only the `mist-core` side is new
in this phase.

**Steps.**

1. For each vendored class, create a new file in `mist-core/src/main/java/io/mist/core/<sub-package>/`.
   The new class should:
   - Live in the appropriate `io.mist.core.*` package.
   - Have the same name as the original (renaming is forbidden in
     this phase; do not, e.g., rename `Operation` to `MistOperation`
     — that is a separate decision deferred to Phase B1.G).
   - Be a verbatim copy of the original's logic, with all
     `es.us.isa.restest.*` imports either removed (if not used) or
     replaced with their newly-vendored `io.mist.core.*` equivalents.
   - Carry no marker that says "vendored from RESTest". The license
     line at the top of the file (if any) is preserved.
2. Vendor in dependency order, leaves first. If `TestCase` depends
   on `TestParameter`, vendor `TestParameter` first.
3. After each class is vendored, run `mvn -q -DskipTests compile`
   on the `mist-core` module. It must pass.
4. **Do not change call sites yet.** The adapter still uses the
   RESTest originals; the new vendored copies in `mist-core` are
   unreferenced by anything except their own tests.
5. Write unit tests in `mist-core/src/test/java/io/mist/core/<sub-package>/`
   that exercise the vendored class's public surface. These tests
   prove the vendored copy is functionally equivalent to the
   RESTest original. Aim for ≥ 1 test method per non-trivial public
   method.

**Gate.** § 7.2.

### Phase B1.C — Move the generation pipeline into mist-core (4 weeks)

**Goal.** Move `MultiServiceTestCaseGenerator` and its direct
collaborators into `mist-core` as `MistGenerator`, switching all
references from RESTest types to the vendored `io.mist.core.*` types
from Phase B1.B. The `extends AbstractTestCaseGenerator` clause is
still in place at the end of this phase — Phase B1.D severs it. This
phase is purely a package move + type-rewire.

**Steps.**

1. **Move classes.** Migrate the following from
   `mist-restest-adapter/src/main/java/es/us/isa/restest/`
   to `mist-core/src/main/java/io/mist/core/`:

   | Old location | New location |
   |---|---|
   | `generators/MultiServiceTestCaseGenerator.java` | `generation/MistGenerator.java` |
   | `generators/AiDrivenLLMGenerator.java` | `generation/AiDrivenValueGenerator.java` |
   | `generators/ZeroShotLLMGenerator.java` | `generation/ZeroShotValueGenerator.java` |
   | `workflow/TraceWorkflowExtractor.java` | `workflow/TraceWorkflowExtractor.java` |
   | `workflow/ScenarioOptimizer.java` | `workflow/ScenarioOptimizer.java` |
   | `workflow/SemanticDependencyRegistry.java` | `registry/SemanticDependencyRegistry.java` |
   | `workflow/NounKeyMap.java` | `workflow/NounKeyMap.java` |
   | `workflow/WorkflowScenario.java` | `workflow/WorkflowScenario.java` |
   | `workflow/pipeline/*.java` | `workflow/pipeline/*.java` |
   | `workflow/pipeline/stages/*.java` | `workflow/pipeline/stages/*.java` |
   | `registry/RootApiRegistry.java` | `registry/RootApiRegistry.java` |
   | `testcases/MultiServiceTestCase.java` | `testcase/MultiServiceTestCase.java` |
   | `inputs/InvalidInputPool.java` | `generation/InvalidInputPool.java` |
   | `inputs/InvalidInputType.java` | *(already retired by Phase 3 of Path B; if any references remain, repoint to `io.mist.core.fault.FaultType`)* |
   | `inputs/smart/*.java` | `generation/smart/*.java` |
   | `validation/SoftErrorRuleCache.java` | *(already retired by Phase 2 of Path B as `ResponseEnvelopeInvariant`; confirm no stragglers)* |
   | `analysis/TraceErrorAnalyzer.java` | `oracle/TraceErrorAnalyzer.java` *(if it's still used; if Phase 2 fully subsumed it, drop)* |
   | `configuration/MstConfig.java` | `config/MistConfig.java` *(rename: `Mst` → `Mist`)* |
   | `configuration/MstConfigValidator.java` | `config/MistConfigValidator.java` |
   | `llm/*.java` not already in mist-llm | *(consolidate into `mist-llm` if pure LLM, else `generation/`)* |

2. **Rewire imports.** In every moved file:
   - Replace `es.us.isa.restest.<sub>` → `io.mist.core.<new-sub>`
     for any class that moved with it.
   - Replace `es.us.isa.restest.<sub>` → `io.mist.core.spec` or
     `io.mist.core.testcase` for any class that was vendored in
     Phase B1.B.
   - For RESTest types that are still consumed but not yet
     vendored or wrapped (the "Wrap" disposition in category B
     of the inventory), **temporarily keep** the
     `es.us.isa.restest.*` import. Phase B1.E introduces the SPI
     interfaces to replace them; Phase B1.F wires the adapter.
     `mist-core` will have temporary `es.us.isa.restest.*`
     imports during Phases B1.C → B1.E; this is expected and is
     fixed in Phase B1.E.

3. **Rename `Mst` → `Mist` in moved classes.** Path B Plan § 1.2
   said "within-package renames are forbidden because they create
   merge conflicts with `inject-detection`". That constraint
   applied to in-place renames. Here, every renamed class is
   **also moving packages**, so the rename is implicit in the
   move and creates no in-place merge surface. Specifically:
   - `MstConfig` → `MistConfig`
   - `MstConfigValidator` → `MistConfigValidator`
   - `MstAuthHandler` → `MistAuthHandler` (if it moves)
   - System property keys (`mst.*`) and YAML keys **stay**. The
     code-level brand layer renames; the config-level layer does
     not. This matches Path B Plan § 1.2's two-layer strategy.
   - File `trainticket-mst.properties` stays named as is. Its
     keys (`mst.config.path`, `mst.generate.only.first.step`, …)
     stay.

4. **Update the adapter to depend on `mist-core` for the moved
   classes.** Anywhere in `mist-restest-adapter` that still
   references the old `es.us.isa.restest.generators.MultiServiceTestCaseGenerator`,
   change to `io.mist.core.generation.MistGenerator`. This will
   touch:
   - `MistRunner.java` (it constructs the generator)
   - `MultiServiceRESTAssuredWriter.java` (it receives the
     generator's output)
   - `TestGenerationAndExecution.java` (the legacy main)
   - Any internal helpers.

5. **`MistGenerator` still extends `AbstractTestCaseGenerator`
   at the end of this phase.** That's the next phase. For now,
   the `extends` clause references the RESTest class via its
   FQN: `extends es.us.isa.restest.generators.AbstractTestCaseGenerator`
   in the new `io.mist.core.generation.MistGenerator`. This makes
   the dependency edge explicit and ugly on purpose, so it shows
   up in the Phase B1.D diff.

6. **Update `MistRunner`'s package.** `MistRunner` is currently in
   `es.us.isa.restest.main`. Move it to `io.mist.cli.MistRunner` in
   the `mist-cli` module. This is consistent with how `MistMain`
   already lives in `mist-cli`.

7. **Compile and verify.** After all moves, the reactor must
   build:
   ```
   mvn -q -DskipTests clean compile
   ```
   Run the seeded demo and diff against the Phase B1.A baseline:
   ```
   mvn -q -DskipTests install
   java -Drandom.seed=42 -jar mist-cli/target/mist.jar \
     mist-restest-adapter/src/main/resources/My-Example/trainticket-demo.properties
   find target/test-cases -name 'Flow_Scenario_*.java' \
     -exec sha256sum {} \; | sort > /tmp/b1-after-bc.sums
   diff /tmp/b1-baseline/scenarios.sums /tmp/b1-after-bc.sums
   ```
   The diff **must be empty.** If non-empty, a class move altered
   behaviour — find and fix before Phase B1.D.

**Gate.** § 7.3.

### Phase B1.D — Sever the `extends` relationship (2 weeks)

**Goal.** Cut the `extends AbstractTestCaseGenerator` clause from
`MistGenerator` and inline the genuinely-needed superclass surface
into `MistGenerator` itself.

**Steps.**

1. Read the entirety of `AbstractTestCaseGenerator` in the RESTest
   source. Identify, per category A of the inventory:
   - **Methods MIST actually calls via `super.…`**: count each. Then
     inline the body of each such method into `MistGenerator` as a
     private method. Drop the `super.` call. If the body is trivial
     (e.g. one field assignment), inline at the call site instead
     of creating a private method.
   - **Methods MIST overrides but no one else calls**: drop the
     `@Override` and the no-longer-needed superclass-shaped
     signature. Keep the body as a private `MistGenerator` method.
   - **Fields MIST reads from the superclass**: declare them as
     `MistGenerator` fields. Initialise from the constructor.
   - **The abstract `generate()` shell**: re-implement as a public
     `MistGenerator.generate()` that does what MIST needs without
     the framework scaffolding (statistics callbacks, RESTest's
     metrics infrastructure). If MIST never uses a particular
     framework hook, drop it.
2. Remove the `extends` clause:
   ```java
   // BEFORE
   public class MistGenerator
           extends es.us.isa.restest.generators.AbstractTestCaseGenerator { … }
   // AFTER
   public final class MistGenerator { … }
   ```
3. **The class becomes `final` by default.** It is no longer
   intended to be subclassed. If any test class subclasses it
   today (it almost certainly does not — Path B Plan § Phase 1
   was clear about this), promote the test to use composition.
4. Remove the now-unused `es.us.isa.restest.generators.*` import
   from `MistGenerator` and from any class that only imported
   RESTest because it was subclass-coupled.
5. **Verify**: `grep -rE 'es\.us\.isa' mist-core/src/main/java`
   should now print only references to types that the inventory
   marked as "Wrap" disposition. Those are removed in Phase B1.E.
6. Run the demo. Diff against baseline. Must be empty.

**Gate.** § 7.4.

### Phase B1.E — Define SPI interfaces (1 week)

**Goal.** For each "Wrap"-disposition RESTest type identified in
the inventory, define a clean `io.mist.core.spi.*` interface in
`mist-core`. Replace `mist-core`'s remaining `es.us.isa.restest.*`
imports with the new interface imports. After this phase,
`mist-core` has **zero** `es.us.isa.restest.*` imports.

**Steps.**

1. The minimum viable SPI surface is approximately:
   ```java
   // io.mist.core.spi.MistSpecLoader
   public interface MistSpecLoader {
       MistSpec loadFromYaml(Path yamlFile);
       MistSpec loadFromUrl(URI specUrl);
   }
   // io.mist.core.spi.MistSpec
   public interface MistSpec {
       List<MistOperation> operations();
       MistOperation findOperation(String operationId);
       String basePath();
   }
   // io.mist.core.spi.MistOperation
   public interface MistOperation {
       String method();
       String path();
       List<MistParameter> parameters();
       Map<String, MistResponseSchema> responses();
   }
   // io.mist.core.spi.MistTestWriter
   public interface MistTestWriter {
       void write(List<MultiServiceTestCase> testCases, Path outputDir);
   }
   // io.mist.core.spi.MistTestExecutor
   public interface MistTestExecutor {
       MistExecutionResult execute(Path testsDir);
   }
   ```
   Adjust according to what the inventory actually flagged. **Do
   not** invent SPIs for hypothetical needs. Only the wrap surface
   the inventory enumerated.
2. SPIs are loaded via `java.util.ServiceLoader` from the adapter
   module's classpath. `mist-core` has no compile-time dependency
   on any SPI implementation.
3. Rewrite the consuming code in `mist-core` to use the new
   interfaces. The transformation is mechanical: every
   `OpenAPISpecification x = …` becomes `MistSpec x = specLoader.load…(…)`,
   etc.
4. Update existing unit tests in `mist-core` that previously
   pulled in RESTest classes — they now construct hand-rolled
   `MistSpec` test doubles. Pure data tests should not need a
   RESTest classpath at all.

**Gate.** § 7.5.

### Phase B1.F — Adapter becomes an SPI provider (2 weeks)

**Goal.** Implement each SPI from Phase B1.E inside
`mist-restest-adapter` by wrapping the existing RESTest types.
Register the implementations via `META-INF/services/` so
`ServiceLoader` discovers them at runtime.

**Steps.**

1. Create a new package `io.mist.adapter.restest` (or rename the
   adapter's source root accordingly — pick once and stick with
   it). Inside, write implementations:
   ```
   io.mist.adapter.restest.RestestSpecLoader            implements io.mist.core.spi.MistSpecLoader
   io.mist.adapter.restest.RestestSpec                   implements io.mist.core.spi.MistSpec
   io.mist.adapter.restest.RestestOperationView          implements io.mist.core.spi.MistOperation
   io.mist.adapter.restest.RestestParameterView          implements io.mist.core.spi.MistParameter
   io.mist.adapter.restest.RestAssuredJUnitWriter        implements io.mist.core.spi.MistTestWriter
   io.mist.adapter.restest.RestAssuredJUnitExecutor      implements io.mist.core.spi.MistTestExecutor
   ```
   Each implementation is a thin wrapper around the corresponding
   RESTest type (or, in the writer's case, the existing
   `MultiServiceRESTAssuredWriter`).
2. Register via `META-INF/services/`:
   ```
   mist-restest-adapter/src/main/resources/META-INF/services/
     io.mist.core.spi.MistSpecLoader     → contains: io.mist.adapter.restest.RestestSpecLoader
     io.mist.core.spi.MistTestWriter     → contains: io.mist.adapter.restest.RestAssuredJUnitWriter
     io.mist.core.spi.MistTestExecutor   → contains: io.mist.adapter.restest.RestAssuredJUnitExecutor
   ```
3. **`MistMain` / `MistRunner` continue to work unmodified.** They
   construct `MistGenerator` (now in `mist-core`) and let
   `ServiceLoader.load(MistSpecLoader.class)` discover the
   adapter's implementation at runtime. If `mist-cli` is launched
   without the adapter on the classpath, MIST fails fast with a
   clear error: "No MistSpecLoader implementation on classpath".
4. **The legacy `restest.jar` launch path continues to work.**
   `TestGenerationAndExecution.main` is unchanged — its MST
   branch still delegates to `MistRunner.run()`. The only
   difference is `MistRunner` now resolves the spec loader via
   `ServiceLoader` instead of constructing `OpenAPISpecification`
   directly.

**Gate.** § 7.6.

### Phase B1.G — Verification and cleanup (1-2 weeks)

**Goal.** Final sweep. Demo runs byte-identical to baseline. The
positioning doc's `file:line` citations get refreshed. The README's
module-layout diagram gets updated. `B1_INVENTORY.md`'s entries are
all marked closed.

**Steps.**

1. **Behavioural equivalence.**
   ```bash
   mvn -q -DskipTests clean install
   java -Drandom.seed=42 -jar mist-cli/target/mist.jar \
     mist-restest-adapter/src/main/resources/My-Example/trainticket-demo.properties
   find target/test-cases -name 'Flow_Scenario_*.java' \
     -exec sha256sum {} \; | sort > /tmp/b1-final.sums
   diff /tmp/b1-baseline/scenarios.sums /tmp/b1-final.sums
   ```
   Must be empty. If non-empty, you are not done.

2. **Both launch paths produce the same output.**
   ```bash
   java -Drandom.seed=42 -jar mist-restest-adapter/target/restest.jar \
     mist-restest-adapter/src/main/resources/My-Example/trainticket-demo.properties
   find target/test-cases -name 'Flow_Scenario_*.java' \
     -exec sha256sum {} \; | sort > /tmp/b1-legacy.sums
   diff /tmp/b1-final.sums /tmp/b1-legacy.sums
   ```
   Must be empty.

3. **Dependency tree is clean.**
   ```bash
   mvn -pl mist-core dependency:tree | grep -E 'es\.us\.isa' && echo FAIL || echo OK
   ```
   Must print `OK`.

4. **Update the positioning doc citations.** Open
   `docs/mst-plans/PATH_B_POSITIONING.md`. Every `file:line`
   citation pointing to `mist-restest-adapter/src/main/java/es/us/isa/restest/`
   that moved in Phase B1.C must be updated to the new
   `mist-core/src/main/java/io/mist/core/` location. Refresh line
   numbers using grep on the new files.

5. **Update the README architecture diagram.** Open the repo-root
   `README.md`. The "Architecture at a glance" section (around
   lines 17-40) shows the four-module layout. Update the comment
   on `mist-core` from
   `# the contribution — no RESTest dependency`
   to something more accurate post-rebuild, e.g.
   `# the contribution — entire generation pipeline, no RESTest dependency`.
   Update the comment on `mist-restest-adapter` to reflect its
   reduced scope:
   `# thin SPI provider: RESTest-backed spec loader + RESTAssured writer + legacy main`.

6. **Update flow.md.** Open
   `mist-restest-adapter/src/main/resources/My-Example/trainticket/flow.md`
   (yes, it stays in the adapter for now — moving it is a separate
   decision). The "Path B entry points" section needs class-name
   updates: `MstConfig` → `MistConfig`, etc. Use search-and-replace
   carefully (the system property `mst.*` keys must NOT change).

7. **Delete `B1_INVENTORY.md` if every entry is closed**, or leave
   it with a "Status: closed" header and a one-line summary of
   the rebuild outcome.

8. **Final commit.** Squash or keep history — user's choice, but
   defer to the user. By default, keep the per-phase commits;
   they tell the story.

**Gate.** § 7.7.

---

## 6. Forbidden Patterns

Things this rebuild must NOT do, even if they seem tempting.

1. **Do not rebrand `MST` → `Mist` in system-property keys, YAML
   keys, generator-name strings, or the `.properties` file
   contents.** The two-layer brand strategy (code: `Mist`; config:
   `mst`) is locked. Path B Plan § 1.2.
2. **Do not delete the `TestGenerationAndExecution` legacy main.**
   It stays as a one-line delegation. The ICSME 2026 / ISSTA 2026
   demo paper references the `java -jar restest.jar` invocation;
   killing it invalidates published artifacts.
3. **Do not introduce new system properties.** If you need
   configuration for a new SPI, it goes into `MistConfig` as a new
   typed field. Path B Plan § 1.3 #5.
4. **Do not add fallbacks for "RESTest not on classpath" cases.**
   The adapter is the only ServiceLoader implementation; if it is
   missing, fail fast at startup with a clear error. No silent
   default-to-stub.
5. **Do not vendor RESTest classes that MIST does not actively use.**
   The temptation is to copy `OpenAPISpecification` entirely;
   resist. Only vendor the data shapes MIST touches.
6. **Do not introduce ablation flags in this rebuild.** Ablation
   infrastructure is a separate work item (it has its own prompt /
   plan); B1 is purely a structural rebuild. § 0.3.
7. **Do not modify the LLM call cache, the `random.seed` gating,
   or any other determinism mechanism.** They were settled by Fix
   S-4 and downstream patches. Touching them invalidates the
   byte-identical equivalence test.
8. **Do not change the `pom.xml` module ordering casually.** The
   four-module reactor's order is `mist-llm → mist-core →
   mist-restest-adapter → mist-cli` (dependents after dependencies).
   If you need to alter ordering, justify why in the commit
   message and re-run the full build.
9. **Do not add new test SUTs.** TrainTicket bundled demo is the
   only verification surface for this rebuild. SUT expansion is
   future task § 5.1 of Path B Plan.
10. **Do not refactor `MultiServiceRESTAssuredWriter`.** It is
    large and bolted-on (the four bolted caches and enhancers
    hang off it), and that is a known debt. This rebuild moves
    references to its new package or wraps it via SPI, but it
    does not touch its internals.
11. **Do not add comments that say "vendored from RESTest" or
    "extracted from upstream".** § 0.2.
12. **Do not introduce backward-compatibility shims across the
    new `io.mist.core.*` boundary.** If a `mist-core` class
    moves package, anything that referenced it updates its
    import. No re-exports, no deprecation stubs.

---

## 7. Verification Gates

Each gate is a checklist. Tick every box before tagging the phase
complete.

### 7.1 Gate B1.A — Inventory

- [ ] `debug/Conference-refinement/B1_INVENTORY.md` exists.
- [ ] Every entry in categories A-E carries a `file:line` reference.
- [ ] Every category-B entry has a Vendor / Wrap / Drop disposition.
- [ ] User has reviewed the inventory (the user signs off; the
      agent does not self-approve).

### 7.2 Gate B1.B — Vendor data classes

- [ ] New packages `io.mist.core.spec`, `io.mist.core.testcase`,
      `io.mist.core.config` (or whichever the inventory required)
      exist.
- [ ] `mvn -q -DskipTests compile` passes for the whole reactor.
- [ ] Each vendored class has unit tests in
      `mist-core/src/test/java/io/mist/core/<sub>/`.
- [ ] `mvn -q test -pl mist-core` passes.
- [ ] The pre-existing demo still runs and still byte-matches the
      baseline (the adapter still uses RESTest originals at this
      gate, so this is a regression-free check on the unchanged
      path).

### 7.3 Gate B1.C — Generation pipeline moved

- [ ] All classes from the migration table in § Phase B1.C #1
      have been moved.
- [ ] `find mist-restest-adapter/src/main/java -name 'MultiService*' -path '*generators*'`
      returns nothing (the file moved).
- [ ] `find mist-core/src/main/java/io/mist/core/generation -name 'MistGenerator.java'`
      returns one file.
- [ ] `mvn -q -DskipTests clean compile` passes.
- [ ] Seeded demo diff against baseline is **empty**.
- [ ] `MistGenerator` still declares
      `extends es.us.isa.restest.generators.AbstractTestCaseGenerator`
      (sever happens next phase).

### 7.4 Gate B1.D — Inheritance severed

- [ ] `grep -rE 'extends AbstractTestCaseGenerator' mist-core/src` returns nothing.
- [ ] `grep -rE 'extends AbstractTestCaseGenerator' mist-restest-adapter/src` returns
      at most legacy non-MIST entries (i.e. any RESTest-internal subclasses
      that were not part of MIST's pipeline).
- [ ] `MistGenerator` is declared `final`.
- [ ] `mvn -q -DskipTests compile` passes.
- [ ] Seeded demo diff against baseline is **empty**.
- [ ] `mvn -q test -pl mist-core` passes.

### 7.5 Gate B1.E — SPIs defined; mist-core RESTest-free

- [ ] `grep -rE 'es\.us\.isa' mist-core/src/main/java` returns nothing.
      **This is the cardinal success criterion of the rebuild.**
- [ ] `mist-core/src/main/java/io/mist/core/spi/` exists and contains
      the interfaces enumerated by the inventory.
- [ ] `mvn -q -DskipTests compile` passes for `mist-core` in
      isolation: `mvn -pl mist-core -am -q -DskipTests compile`.
- [ ] The reactor build still passes.
- [ ] `mist-core` unit tests pass without any RESTest classes on
      the classpath (verify by inspecting `mvn -pl mist-core dependency:tree`).

### 7.6 Gate B1.F — Adapter wraps SPIs

- [ ] `mist-restest-adapter/src/main/resources/META-INF/services/`
      contains one file per SPI, each naming the adapter's
      implementation class.
- [ ] `java -jar mist-cli/target/mist.jar` works end-to-end on the
      bundled demo.
- [ ] `java -jar mist-restest-adapter/target/restest.jar` works
      end-to-end on the bundled demo.
- [ ] Both launch paths produce byte-identical output under the
      same `-Drandom.seed=42`.
- [ ] If the adapter is removed from the classpath (e.g. by manually
      excluding it from `mist-cli`'s shaded jar in a one-off test),
      `mist-cli` fails fast at startup with the clear error
      "No MistSpecLoader implementation on classpath", not a
      NullPointerException.

### 7.7 Gate B1.G — Final cleanup

- [ ] Seeded byte-identical equivalence against the Phase B1.A baseline.
- [ ] Two launch paths still byte-equivalent under the seed.
- [ ] `mvn -pl mist-core dependency:tree` shows zero RESTest deps.
- [ ] Positioning doc `file:line` citations refreshed.
- [ ] README architecture diagram updated.
- [ ] flow.md class-name references updated.
- [ ] `B1_INVENTORY.md` either deleted or marked "Status: closed".
- [ ] No `@Deprecated` annotations or "TODO B1" comments left in
      the source tree.

---

## 8. Git Workflow

### 8.1 Per-phase commits

One phase ≈ one logical chunk of work ≈ one or two commits. The
agent commits to the working branch after each phase passes its
gate. Commit messages follow the existing repository's style
(check `git log --oneline -20` on `inject-detection` to calibrate).

Format:

```
<imperative subject ≤ 72 chars>

<optional body explaining why this change was needed and what
non-obvious design decisions it embodies. Wrap at 72 chars.>
```

**Do not** include any of the following in commit messages:
- 🤖 or other emoji-banner lines
- `Co-Authored-By: Claude` trailers
- `Generated with Claude Code` text
- `https://claude.ai/code/session_…` URLs
- Self-referential mentions of "this agent", "the AI", etc.

**Do** include:
- Reference to the phase: `B1.C — move generation pipeline into mist-core`
- Concrete enumeration of what moved: `MultiServiceTestCaseGenerator → MistGenerator`
- Non-obvious decisions: `kept package-private visibility on PoolKey so legacy adapter tests still compile`

Example good message:

```
b1.c: move MultiServiceTestCaseGenerator to mist-core as MistGenerator

Moves the generation core (2906 lines) from the RESTest-derived
package to io.mist.core.generation. Renames the class to drop
the MultiService prefix since the workflow distinction is now
captured by the WorkflowPipeline boundary, not the generator's
class identity. Extends clause still points at
AbstractTestCaseGenerator via FQN; b1.d severs it. Seeded demo
output is byte-identical to the pre-move baseline.

Updates MistRunner and MultiServiceRESTAssuredWriter to import
the new location. The five pipeline stages and four support
helpers (S-1b lift) move with the generator.
```

Example bad message (do not produce this):

```
Move generator 🤖

Generated with Claude Code

Co-Authored-By: Claude <noreply@anthropic.com>
```

### 8.2 No PR

Do **not** open a pull request unless the user explicitly says
"open a PR". The default workflow is: work on the branch, get the
user's sign-off via chat at each gate, then merge to
`inject-detection` and push.

### 8.3 Final merge to inject-detection

When § 7.7 passes and the user explicitly approves:

```bash
git checkout inject-detection
git merge --ff-only claude/b1-sever-restest-inheritance \
  || git merge --no-ff claude/b1-sever-restest-inheritance \
       -m "merge b1: sever RESTest inheritance and vendor generation core"
git push -u origin inject-detection
```

If the fast-forward merge is not possible (because `inject-detection`
moved ahead during the rebuild), do not force-push. Instead, rebase
the working branch onto the new `inject-detection` HEAD:

```bash
git checkout claude/b1-sever-restest-inheritance
git fetch origin inject-detection
git rebase origin/inject-detection
# resolve conflicts if any; the rebuild is large enough that
# conflicts are non-trivial — escalate to user before resolving
git checkout inject-detection
git merge --ff-only claude/b1-sever-restest-inheritance
git push -u origin inject-detection
```

### 8.4 Push retry on network failure

If `git push` fails due to a network error, retry up to 4 times with
exponential backoff:

```bash
for delay in 2 4 8 16; do
  if git push -u origin inject-detection; then break; fi
  sleep $delay
done
```

Do not retry on non-network errors (rejected pushes, hook failures,
authentication failures). Diagnose and report instead.

---

## 9. Rollback

If at any point the agent or the user decides to abandon the
rebuild:

1. Do **not** force-push or rewrite `inject-detection` history.
2. Simply abandon the working branch: `git branch -D
   claude/b1-sever-restest-inheritance` (after pushing the
   branch is deleted on remote: `git push origin --delete
   claude/b1-sever-restest-inheritance`).
3. The pre-rebuild `inject-detection` HEAD remains the source of
   truth; nothing on the published branch is at risk.

If the rebuild has partially landed on `inject-detection` (e.g.
Phases B1.A and B1.B made it through and were merged before a
problem surfaced in B1.C), do **not** revert the published
commits. Either:
- Push forward with a corrective commit that gets the build green
  again, or
- Escalate to the user to decide whether a partial rebuild is an
  acceptable interim state.

The cardinal rule: published commits stay; you fix forward.

---

## 10. Glossary

| Term | Meaning |
|---|---|
| **`AbstractTestCaseGenerator`** | RESTest's abstract base class for test generators, located in `es.us.isa.restest.generators`. The inheritance edge this rebuild severs. |
| **`MultiServiceTestCaseGenerator`** | MIST's generator, currently a subclass. Becomes `MistGenerator` in `mist-core` after B1.C. |
| **`MistGenerator`** | New name and location for the generation core. Lives in `io.mist.core.generation`. Has no `extends` clause after B1.D. |
| **`MistSpecLoader` / `MistTestWriter`** | New SPI interfaces in `io.mist.core.spi`, introduced in B1.E and implemented by the adapter in B1.F. |
| **Vendor** | The act of copying a small, stable class from RESTest into `mist-core` so the dependency edge can be cut. |
| **Wrap** | The alternative to vendoring: defining an SPI in `mist-core` and providing a thin RESTest-backed implementation in the adapter. |
| **Drop** | The disposition for RESTest types MIST does not actually use; remove the reference without replacement. |
| **byte-identical equivalence** | The verification gate that says: under `-Drandom.seed=42`, the demo produces the exact same generated test files before and after the rebuild. SHA-256 sums must match line-for-line. |
| **`inject-detection`** | The user's main branch. All final pushes land here. |
| **`claude/b1-sever-restest-inheritance`** | The working branch this rebuild produces. Merged into `inject-detection` only after § 7.7 passes. |

---

## 11. Escalation Triggers

Escalate to the user (one-line question, then continue with the next
independent sub-task) when:

1. The inventory in § Phase B1.A reveals a category-B entry the user
   needs to choose Vendor / Wrap / Drop for, and the agent's
   judgement is uncertain.
2. A class rename collision: e.g. moving `MstConfig` → `MistConfig`
   would shadow an existing `io.mist.core.MistConfig`. Stop and ask.
3. The byte-identical equivalence test fails after a phase and the
   agent cannot identify the root cause in ≤ 4 hours of focused
   debugging.
4. A test that previously passed starts failing and the failure
   appears to be a real regression rather than a stale-snapshot
   issue.
5. The dependency-tree check at § 7.5 still shows RESTest edges
   despite the agent's belief that all Wrap-disposition surfaces
   have been SPI-ified. Surface the unexpected edge and let the
   user decide whether to add a new SPI or revisit the inventory.
6. The rebuild has exceeded 18 weeks of elapsed effort. Stop and
   re-plan with the user.

Do **not** escalate for:

- Routine compile errors (fix them).
- Routine test failures introduced by the rebuild (fix them).
- Style or formatting choices (use the repository's existing
  conventions; if unclear, pick one and be consistent).
- Whether to use Lombok / records / etc. (use what the surrounding
  code uses).

---

*End of prompt.*
