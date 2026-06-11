# MIST — H2: Ablation Infrastructure for ICSE/FSE/ASE/ISSTA Main-Track Evaluation

> **Audience.** Self-contained execution brief for a coding agent (Claude
> Code or equivalent) that will add the ablation toggles MIST needs to
> defend its contribution at a main research track. After this work
> lands, every named MIST contribution (Trace Shape Oracle invariants,
> Adaptive Fault Taxonomy via FaultMiner, Thompson-sampling fault-queue
> scheduler) can be independently disabled via a single \`mst.\` property,
> letting the future-task evaluation phase build the ablation matrix
> described in \`docs/mst-plans/PATH_B_POSITIONING.md\` § 4.

---

## 0. Hard Rules — Read Before Touching Anything

### 0.1 Branch and authorship

- **\`inject-detection\` is the user's main branch.** Treat it the same
  way you would treat \`main\` or \`master\` on any other repository.
- **Branch off \`inject-detection\`.** Create a new working branch
  named \`claude/h2-ablation-infrastructure\` (or whatever name the
  user supplies). Do **not** commit directly to \`inject-detection\`.
- **Final push goes to \`inject-detection\`.** When all verification
  gates in § 7 pass and the user has explicitly approved, fast-forward
  / squash-merge the working branch into \`inject-detection\` and push
  \`inject-detection\` to \`origin\`. Do **not** open a pull request
  unless the user explicitly asks.
- Use \`git push -u origin <branch>\` for intermediate work and the
  final push.
- **If push fails for network reasons**, retry up to 4 times with
  exponential backoff (2s, 4s, 8s, 16s). Do **not** retry on
  non-network errors (rejected pushes, hook failures) — diagnose
  the actual cause.

### 0.2 No-Claude-attribution policy

Every commit and every push artifact produced by this work must look
like it was authored by a human. Concretely:

- **Do NOT** include \`🤖 Generated with [Claude Code]\` lines in commit
  messages.
- **Do NOT** include \`Co-Authored-By: Claude <noreply@anthropic.com>\`
  trailers.
- **Do NOT** include any \`https://claude.ai/code/session_*\` URL
  trailers.
- **Do NOT** mention "Claude", "Anthropic", "AI", "agent", "LLM
  generated", or any equivalent in commit messages, branch names,
  PR descriptions, file headers, or code comments.
- **Do NOT** modify the repository's git config to change the author
  or committer. Use whatever \`user.name\` / \`user.email\` is already
  configured.
- Commit messages should look like ordinary engineering commits:
  imperative subject line (≤ 72 chars), optional body explaining
  *why*, no marketing.
- File-level: do not insert generated-by banners, attribution
  comments, or "this file was written by …" preambles.

### 0.3 Scope discipline

This work has exactly two objectives:

1. **Add toggles** for every contribution MIST claims as ablatable in
   \`PATH_B_POSITIONING.md\` § 4.2 (rows R1-R4 of the head-to-head
   ablation table).
2. **Verify** that each toggle combination produces a deterministic,
   byte-comparable run under \`-Drandom.seed=42\`.

No new features. No improvements to algorithms. No "while I'm here"
refactors. No running of actual ablation experiments — that is the
future-task evaluation phase, not this work. This prompt only enables
the experiments; it does not execute them.

If you find a bug along the way that is **not** caused by this work,
write it down in \`debug/Conference-refinement/H2_FOLLOWUPS.md\`
(create if absent) and keep going. Do not fix it in this branch.

### 0.4 Behaviour preservation under defaults

- With **all toggles at their default values**, the bundled TrainTicket
  demo must produce **byte-identical** \`Flow_Scenario_*.java\` files
  before and after this work, under \`-Drandom.seed=42\`. This is the
  cardinal verification gate (§ 7.2).
- Default semantics are pinned in § 4.3. Briefly: every contribution
  that is **currently active** on the \`inject-detection\` HEAD stays
  active by default after this work. The only exception is the
  \`TimingEnvelope\` invariant, which the recent paper revision
  removed from the contribution; its default flips to \`false\`
  (see § 4.3 for the rationale).
- The legacy launch path (\`java -jar mist-cli/target/mist.jar\`) must
  keep working. After this work, both launch paths reach the same
  in-process generator; the seeded-diff continues to be empty under
  defaults.

### 0.5 Tooling discipline

- Use \`Read\`/\`Edit\`/\`Write\` over \`cat\`/\`sed\`/\`awk\` for file work.
- Use \`Bash\` only for build, test, search, and git.
- Build verification after every phase: \`mvn -q -DskipTests compile\`
  must pass. If it fails, **stop and diagnose** before continuing.
- Test verification after Phases H2.B, H2.C, H2.D, H2.E:
  \`mvn -q test -pl mist-core,mist-cli\`. New tests added by this work
  must pass; pre-existing failures are out of scope.

---

## 1. Mission

MIST's flagship paper target is the main research track of ICSE 2027,
FSE 2027, ASE 2027, or ISSTA 2027. The contribution story rests on a
single empirical claim, articulated in
\`docs/mst-plans/PATH_B_POSITIONING.md\` § 4.4:

> **R4 (MIST −trace-shape −adaptive) ≈ R5 (AutoRestTest) within
> statistical noise across ≥ 3 SUTs, while R1 (MIST-full) dominates
> R5 on at least one of (coverage, faults, time-to-first-fault) with
> A₁₂ ≥ 0.71 across the same SUTs.**

This claim requires running the same generator with named components
disabled. Today the generator has **no way to disable any component**:
\`TraceShapeOracle\` always evaluates all installed invariants, the
\`ThompsonScheduler\` always re-ranks the fault queue, and the
\`FaultMiner\` is the only contribution with an existing toggle (it
is already \`mst.fault.mining.enabled\`, default \`false\`). The
acceptance condition above is therefore **unfalsifiable** on
\`inject-detection\` HEAD.

This work fixes that gap by adding the missing toggles and verifying
each toggle combination produces deterministic output. It is **the
narrowest possible work** that unblocks the future-task evaluation
phase. No new behaviour is introduced; behaviour is only made
selectively-suppressible.

---

## 2. Prerequisites — Required Reading

Read these in order. Confirm you understand each. No code changes
before § 4.

1. **\`debug/Conference-refinement/PATH_B_REBUILD_PLAN.md\`** — the
   Path B rebuild plan. Read § 0.1 (target venue) and § 5.2 (Phase 5
   — Ablation + baselines + full evaluation). Note that ablation
   infrastructure was explicitly out of scope for the previous fix
   round (\`CRITICAL_FIXES_S_A_1_6.md\` § 0.5 #7): *"No ablation
   infrastructure. Ablation is deferred to Path B."* This is the work
   that fulfils that deferral.
2. **\`docs/mst-plans/PATH_B_POSITIONING.md\`** — the positioning
   document. The critical sections are:
   - § 4.2 (configuration rows R1-R9) — the canonical list of
     ablation configurations.
   - § 4.4 (acceptance condition) — what the ablation must
     ultimately show.
3. **\`debug/Conference-refinement/PROMPT_B1_SEVER_RESTEST_INHERITANCE.md\`** —
   the previous structural rebuild prompt. Note its branch/authorship/
   verification conventions; this prompt follows them.
4. **\`mist-restest-adapter/src/main/resources/My-Example/trainticket/flow.md\`**
   (path may have moved post-B1; locate with \`find . -name flow.md\`)
   — the algorithm document. Read at least the "Conditions, Flags,
   and Inputs" section.
5. **\`paper/main.tex\` and \`paper/main_issta.tex\`** — the current
   demo papers. Note that the contribution claim is **three**
   invariants (\`SpanTreeShape\`, \`StatusPropagation\`,
   \`ResponseEnvelope\`); \`TimingEnvelope\` was recently dropped from
   the contribution (commits \`b96fb8b6\` and \`dfc11b46\`). The
   ablation toggles must reflect that pivot — see § 4.3 on defaults.

### 2.1 Pre-flight verification commands

Run all of these before starting any code work. If any command fails or
produces unexpected output, stop and escalate.

\`\`\`bash
# Confirm you are on inject-detection HEAD and clean
git status
git rev-parse --abbrev-ref HEAD     # must print 'inject-detection'

# Confirm B1 has landed (mist-core has zero RESTest source)
! grep -rE 'es\\.us\\.isa' mist-core/src/main/java
test -d mist-core/src/main/java/io/mist/core
test -d mist-cli/src/main/java/io/mist/cli
# Confirm mist-restest-adapter is gone (it was deleted in commit 6cd2d10b)
test ! -d mist-restest-adapter

# Confirm the contribution surfaces this work touches exist
test -f mist-core/src/main/java/io/mist/core/config/MstConfig.java
test -f mist-core/src/main/java/io/mist/core/oracle/shape/TraceShapeOracle.java
test -f mist-core/src/main/java/io/mist/core/oracle/shape/invariant/SpanTreeShapeInvariant.java
test -f mist-core/src/main/java/io/mist/core/oracle/shape/invariant/StatusPropagationInvariant.java
test -f mist-core/src/main/java/io/mist/core/oracle/shape/invariant/ResponseEnvelopeInvariant.java
test -f mist-core/src/main/java/io/mist/core/oracle/shape/invariant/TimingEnvelopeInvariant.java
test -f mist-core/src/main/java/io/mist/core/bandit/ThompsonScheduler.java
test -f mist-core/src/main/java/io/mist/core/fault/FaultMiner.java
test -f mist-core/src/main/java/io/mist/core/generation/MistGenerator.java
test -f mist-cli/src/main/java/io/mist/cli/MistRunner.java

# Confirm the demo builds cleanly today (so any later breakage is yours)
mvn -q -DskipTests clean install

# Capture the pre-work seeded baseline (KEEP THIS — you will diff against it)
mkdir -p /tmp/h2-baseline
java -Drandom.seed=42 -jar mist-cli/target/mist.jar \\
  mist-cli/src/main/resources/My-Example/trainticket-demo.properties \\
  > /tmp/h2-baseline/run.log 2>&1
find target/test-cases -name 'Flow_Scenario_*.java' -exec sha256sum {} \\; \\
  | sort > /tmp/h2-baseline/scenarios.sums

# Confirm at least one .sums line exists
test -s /tmp/h2-baseline/scenarios.sums
\`\`\`

If any of the above commands fail or print empty results, **do not
start the work**. Report back to the user with the failing command
and its output. (Note: the bundled \`.properties\` may have moved with
the B1 sever; the actual path is whatever \`README.md\` documents as
the Quick Start.)

---

## 3. Current State

### 3.1 The contribution surfaces this work touches

| Surface | Class / file | Current toggle status |
|---|---|---|
| **Trace Shape Oracle** (whole) | \`mist-core/src/main/java/io/mist/core/oracle/shape/TraceShapeOracle.java\` | None — always evaluates every invariant it loads |
| **SpanTreeShape invariant** | \`oracle/shape/invariant/SpanTreeShapeInvariant.java\` | None — always loaded by \`TraceShapeOracle.evaluate(...)\` |
| **StatusPropagation invariant** | \`oracle/shape/invariant/StatusPropagationInvariant.java\` | None — always loaded |
| **ResponseEnvelope invariant** | \`oracle/shape/invariant/ResponseEnvelopeInvariant.java\` | None — always loaded |
| **TimingEnvelope invariant** | \`oracle/shape/invariant/TimingEnvelopeInvariant.java\` | None — always loaded; class survives the paper revision but is **no longer claimed as a contribution** |
| **FaultMiner** | \`mist-core/src/main/java/io/mist/core/fault/FaultMiner.java\` | **\`mst.fault.mining.enabled\` (default \`false\`)** — already gated; do not change the key or default |
| **ThompsonScheduler** | \`mist-core/src/main/java/io/mist/core/bandit/ThompsonScheduler.java\` | None — always re-ranks the fault queue inside \`MistGenerator\` |
| **MstConfig** (config home) | \`mist-core/src/main/java/io/mist/core/config/MstConfig.java\` | 12 sub-records; \`Faulty\` already houses \`mst.fault.mining.enabled\` if present, otherwise it lives at the top level — verify in § Phase H2.A |

### 3.2 How the surfaces are wired today (no toggles to override)

Approximate call graph as of \`inject-detection\` HEAD (verify via grep
in Phase H2.A):

\`\`\`
MistMain
  └─> MistRunner.run()
        ├─> bootstrapTraceShapeOracle() ─> new TraceShapeOracle(store)
        │     └─> per-step evaluate(trace, rootApi)
        │           ├─> SpanTreeShapeInvariant.load(...).evaluate(...)
        │           ├─> StatusPropagationInvariant.load(...).evaluate(...)
        │           ├─> TimingEnvelopeInvariant.load(...).evaluate(...)
        │           └─> ResponseEnvelopeInvariant.load(...).evaluate(...)
        └─> MistGenerator.generate()
              ├─> ThompsonScheduler bandit = new ThompsonScheduler();
              │     (re-ranks fault queue every iteration)
              └─> (if mst.fault.mining.enabled) FaultMiner.mine(...)
\`\`\`

Every named contribution is reachable, but none of the first four
oracle paths and the bandit path are switchable. This work introduces
the switches.

---

## 4. Target State

### 4.1 The six toggles

| # | Property key | Default | Effect when \`false\` |
|---|---|---|---|
| 1 | \`mst.oracle.shape.enabled\` | \`true\` | \`TraceShapeOracle.evaluate(...)\` returns an empty verdict (\`passed=true\`, \`violations=[]\`); no invariant code paths execute |
| 2 | \`mst.oracle.shape.invariants.span_tree.enabled\` | \`true\` | \`SpanTreeShapeInvariant\` is skipped in \`TraceShapeOracle.evaluate(...)\` |
| 3 | \`mst.oracle.shape.invariants.status_propagation.enabled\` | \`true\` | \`StatusPropagationInvariant\` is skipped |
| 4 | \`mst.oracle.shape.invariants.response_envelope.enabled\` | \`true\` | \`ResponseEnvelopeInvariant\` is skipped |
| 5 | \`mst.oracle.shape.invariants.timing.enabled\` | **\`false\`** — see § 4.3 | \`TimingEnvelopeInvariant\` is skipped |
| 6 | \`mst.scheduler.bandit.enabled\` | \`true\` | \`MistGenerator\` uses round-robin fault-queue ordering; no Thompson sampling |

The **already-existing** \`mst.fault.mining.enabled\` is the seventh
toggle and is not added by this work; it stays at its current default
(\`false\`). All seven together cover the ablation rows R1-R4 of
\`PATH_B_POSITIONING.md\` § 4.2.

### 4.2 Ablation rows realised by toggle combinations

| Row | Name | \`mst.oracle.shape.enabled\` | \`mst.fault.mining.enabled\` | Notes |
|---|---|---|---|---|
| R1 | MIST-full | \`true\` (default) | \`true\` (override) | Reference column |
| R2 | MIST − trace-shape-oracle | \`false\` | \`true\` | Same generation, no shape oracle |
| R3 | MIST − adaptive-fault | \`true\` (default) | \`false\` (default) | Same as today's bundled demo |
| R4 | MIST − trace-shape − adaptive | \`false\` | \`false\` | Strips both contributions |

Per-invariant toggles (#2–#5) are finer-grained dials used for the
in-paper "which invariant contributes which fault" analysis (§ 5 of
the eventual paper's evaluation chapter). They are not on the R1-R4
critical path but are required for the paper's per-invariant
attribution table.

### 4.3 Default-value rationale

- **All "current behaviour" toggles default to \`true\`.** Under
  defaults, the demo's byte-identical equivalence with the pre-work
  baseline (§ 7.2) is the principal correctness check.
- **\`mst.scheduler.bandit.enabled\` defaults to \`true\`** because
  Thompson re-ranking is part of the current behaviour on
  \`inject-detection\` HEAD (\`MistGenerator.java:204\`).
- **\`mst.oracle.shape.invariants.timing.enabled\` defaults to
  \`false\`.** This is the **one default flip** in this work. The
  paper revisions (\`paper/main.tex\` and \`paper/main_issta.tex\`,
  commits \`b96fb8b6\` and \`dfc11b46\`) explicitly drop the
  \`TimingEnvelope\` invariant from the contribution. Shipping with
  it enabled by default would put the runtime behaviour out of sync
  with the claimed contribution. The code keeps
  \`TimingEnvelopeInvariant\` as an experimental hook so that anyone
  who wants to re-enable it can do so by setting the property to
  \`true\` — but the default match between paper and code is
  \`false\`.

This default flip means the byte-identical equivalence test (§ 7.2)
will fail if you run it naively against the pre-work baseline,
because today \`TimingEnvelope\` is implicitly enabled in the trace
shape verdict. Two acceptable resolutions:

- **Preferred:** capture the new baseline with
  \`-Dmst.oracle.shape.invariants.timing.enabled=true\` and verify
  byte-identical equivalence against the original baseline; *then*
  capture a new baseline without that override and confirm the diff
  is limited to verdict-side artefacts (Allure attachments, not the
  generated \`Flow_Scenario_*.java\` files).
- **Alternative:** if the byte-identical scope is solely the generated
  test files (and not the runtime verdicts), the equivalence
  continues to hold even with \`Timing\` flipped off, because the
  generation pipeline is independent of the oracle. Verify both
  scenarios and document the actual scope in
  \`H2_FOLLOWUPS.md\`.

If the user prefers \`TimingEnvelope\` default to remain \`true\` to
preserve full byte-identical equivalence, escalate the decision —
do not silently change the default.

### 4.4 New classes (only what's strictly needed)

- **\`io.mist.core.config.MstConfig.Oracle\` sub-record** (added to
  the existing \`MstConfig\` POJO). Mirrors the pattern of \`Faulty\`,
  \`Llm\`, \`Jaeger\`, etc. Exposes:
  \`\`\`java
  public boolean shapeOracleEnabled();
  public boolean spanTreeInvariantEnabled();
  public boolean statusPropagationInvariantEnabled();
  public boolean responseEnvelopeInvariantEnabled();
  public boolean timingEnvelopeInvariantEnabled();
  \`\`\`
- **\`io.mist.core.config.MstConfig.Scheduler\` sub-record** (added
  to the existing \`MstConfig\` POJO). Exposes:
  \`\`\`java
  public boolean banditEnabled();
  \`\`\`
- **\`io.mist.core.config.AblationProfile\`** (new file). A small
  immutable record that captures the active toggle state for a run.
  Built from \`MstConfig\` at \`MistRunner\` startup and surfaced in
  the startup banner (§ Phase H2.D).
- No SPI changes. No new packages. No new modules.

### 4.5 What the change to \`TraceShapeOracle.evaluate(...)\` looks like

Before (pseudo-code, see actual file for the existing shape):
\`\`\`java
public TraceShapeVerdict evaluate(TraceModel trace, String rootApiKey) {
    TraceShapeVerdict.Builder builder = TraceShapeVerdict.builder();
    builder.add(SpanTreeShapeInvariant.load(rootApiKey, store).evaluate(trace));
    builder.add(StatusPropagationInvariant.load(rootApiKey, store).evaluate(trace));
    builder.add(TimingEnvelopeInvariant.load(rootApiKey, store).evaluate(trace));
    builder.add(ResponseEnvelopeInvariant.load(rootApiKey, store).evaluate(trace));
    return builder.build();
}
\`\`\`

After:
\`\`\`java
public TraceShapeVerdict evaluate(TraceModel trace, String rootApiKey) {
    if (!oracleEnabled) return TraceShapeVerdict.empty();
    TraceShapeVerdict.Builder builder = TraceShapeVerdict.builder();
    if (spanTreeEnabled) builder.add(SpanTreeShapeInvariant.load(rootApiKey, store).evaluate(trace));
    if (statusPropagationEnabled) builder.add(StatusPropagationInvariant.load(rootApiKey, store).evaluate(trace));
    if (timingEnvelopeEnabled) builder.add(TimingEnvelopeInvariant.load(rootApiKey, store).evaluate(trace));
    if (responseEnvelopeEnabled) builder.add(ResponseEnvelopeInvariant.load(rootApiKey, store).evaluate(trace));
    return builder.build();
}
\`\`\`

\`oracleEnabled\` and the four per-invariant flags are populated from
\`MstConfig.Oracle\` via a constructor parameter — \`TraceShapeOracle\`
does **not** read \`System.getProperty\` directly. The constructor
must accept the flags so unit tests can pass arbitrary combinations
without messing with system properties. \`TraceShapeOracle\` already
takes a \`ShapeInvariantStore\`; the new constructor takes
\`(ShapeInvariantStore, MstConfig.Oracle)\` and the old constructor
can either be kept as a delegate that uses the default-all-on profile
or removed if it has no remaining callers.

### 4.6 What the change to \`MistGenerator\` looks like

Find the call site (today: \`MistGenerator.java:204\`):
\`\`\`java
ThompsonScheduler bandit = new ThompsonScheduler();
\`\`\`

Replace with a gated call site that, when the bandit is disabled,
returns the fault queue in its original (round-robin) order without
sampling. The simplest shape:

\`\`\`java
List<FaultTarget> rankedTargets;
if (config.scheduler().banditEnabled()) {
    ThompsonScheduler bandit = new ThompsonScheduler();
    seedBanditFromRegistry(bandit, registry, faultTargets);
    rankedTargets = bandit.rank(faultTargets);
    log.debug("Fault queue re-ranked by ThompsonScheduler: {} targets", rankedTargets.size());
} else {
    rankedTargets = new ArrayList<>(faultTargets);  // preserve insertion order
    log.debug("Fault queue using insertion order (bandit disabled): {} targets", rankedTargets.size());
}
\`\`\`

The exact shape depends on the current code — read it first. The
contract is: when \`banditEnabled\` is \`false\`, the queue's order
is whatever it would be without ever instantiating
\`ThompsonScheduler\`, and no methods on \`ThompsonScheduler\` are
called.

---

## 5. Phased Plan

Each phase produces a sequence of commits. The phase boundary is a
verification gate (§ 7). Do not start the next phase until the
current phase's gate passes.

> **Estimated total elapsed time: 1-2 weeks of focused work.** This
> is small compared to B1; do not let it spread out into more.

### Phase H2.A — Inventory (1 day)

**Goal.** Produce \`debug/Conference-refinement/H2_INVENTORY.md\`. No
code changes in this phase.

**Steps.**

1. Locate every place in the codebase where the six new toggles would
   sit. Specifically:
   - All call sites of \`TraceShapeOracle.evaluate(...)\` — the
     toggle-#1 read site.
   - The body of \`TraceShapeOracle.evaluate(...)\` — the
     per-invariant gates (toggles #2-#5).
   - All construction sites of \`ThompsonScheduler\` — the toggle-#6
     read site.
   - The current state of \`mst.fault.mining.enabled\` and its
     read site (so you confirm you do **not** need to add it).
   - Every \`MstConfig\` sub-record and its property keys.
2. Confirm the file paths in § 3.1 still exist; if any moved (B1 was
   a large rewrite), update the inventory with the actual paths.
3. Inventory output goes to \`H2_INVENTORY.md\`. Each entry carries a
   \`file:line\` reference verified by grep.

**Gate.** § 7.1.

### Phase H2.B — Add the six toggles to MstConfig (1-2 days)

**Goal.** Add two new sub-records (\`Oracle\`, \`Scheduler\`) to
\`MstConfig\`, with the six new property accessors, defaults wired
per § 4.3, and \`MstConfigValidator\` entries so unknown / malformed
values are caught.

**Steps.**

1. Open \`mist-core/src/main/java/io/mist/core/config/MstConfig.java\`.
   Find an existing sub-record (e.g. \`Faulty\` or \`Jaeger\`) and use
   it as a template. The pattern is:
   \`\`\`java
   public static final class Oracle {
       private final boolean shapeOracleEnabled;
       private final boolean spanTreeInvariantEnabled;
       private final boolean statusPropagationInvariantEnabled;
       private final boolean responseEnvelopeInvariantEnabled;
       private final boolean timingEnvelopeInvariantEnabled;
       public Oracle() {
           this.shapeOracleEnabled = Boolean.parseBoolean(
               System.getProperty("mst.oracle.shape.enabled", "true"));
           this.spanTreeInvariantEnabled = Boolean.parseBoolean(
               System.getProperty("mst.oracle.shape.invariants.span_tree.enabled", "true"));
           this.statusPropagationInvariantEnabled = Boolean.parseBoolean(
               System.getProperty("mst.oracle.shape.invariants.status_propagation.enabled", "true"));
           this.responseEnvelopeInvariantEnabled = Boolean.parseBoolean(
               System.getProperty("mst.oracle.shape.invariants.response_envelope.enabled", "true"));
           this.timingEnvelopeInvariantEnabled = Boolean.parseBoolean(
               System.getProperty("mst.oracle.shape.invariants.timing.enabled", "false"));
       }
       public boolean shapeOracleEnabled() { return shapeOracleEnabled; }
       public boolean spanTreeInvariantEnabled() { return spanTreeInvariantEnabled; }
       public boolean statusPropagationInvariantEnabled() { return statusPropagationInvariantEnabled; }
       public boolean responseEnvelopeInvariantEnabled() { return responseEnvelopeInvariantEnabled; }
       public boolean timingEnvelopeInvariantEnabled() { return timingEnvelopeInvariantEnabled; }
   }

   public static final class Scheduler {
       private final boolean banditEnabled;
       public Scheduler() {
           this.banditEnabled = Boolean.parseBoolean(
               System.getProperty("mst.scheduler.bandit.enabled", "true"));
       }
       public boolean banditEnabled() { return banditEnabled; }
   }
   \`\`\`
2. Add the corresponding fields and accessors to the top-level
   \`MstConfig\`:
   \`\`\`java
   private final Oracle oracle;
   private final Scheduler scheduler;
   // … in constructor:
   this.oracle = new Oracle();
   this.scheduler = new Scheduler();
   public Oracle oracle() { return oracle; }
   public Scheduler scheduler() { return scheduler; }
   \`\`\`
3. Update \`MstConfigValidator\` to recognise the six new property
   keys (they should not produce a "unknown key" warning under
   \`mst.config.strict=true\`).
4. Compile and run existing tests. No behaviour change yet — the
   toggles are read but nothing consults them.

**Gate.** § 7.2.

### Phase H2.C — Wire the toggles into runtime (2-3 days)

**Goal.** Make the six toggles actually take effect on
\`TraceShapeOracle\`, the four invariants, and \`MistGenerator\`'s
use of \`ThompsonScheduler\`.

**Steps.**

1. **Refactor \`TraceShapeOracle\` constructor** to accept
   \`MstConfig.Oracle\` (or a 5-tuple of booleans — pick the cleaner
   one and stick with it). Store the five flags as final fields.
   Add the gates inside \`evaluate(...)\` per § 4.5. If the existing
   constructor signature is widely called, keep it as a delegate
   that constructs a default-all-on \`MstConfig.Oracle\`.
2. **Update \`MistRunner\`** (\`bootstrapTraceShapeOracle()\` around
   line 737 — verify the exact line in your inventory) to pass the
   \`MstConfig.Oracle\` into the new \`TraceShapeOracle\` constructor.
3. **Update \`MistGenerator\`** (around line 204) to gate the
   \`ThompsonScheduler\` construction per § 4.6. The generator
   already has a reference to \`MstConfig\` (verify — if not, add a
   constructor parameter; do **not** introduce a singleton lookup
   inside the generator method body).
4. **Do not modify** \`SpanTreeShapeInvariant\`,
   \`StatusPropagationInvariant\`, \`ResponseEnvelopeInvariant\`, or
   \`TimingEnvelopeInvariant\` directly. The toggle lives in
   \`TraceShapeOracle.evaluate(...)\`; the invariant classes
   themselves stay agnostic of the toggle.
5. **Do not modify** \`FaultMiner\` or its existing
   \`mst.fault.mining.enabled\` gate. That toggle already works; this
   work does not touch it.

**Gate.** § 7.3.

### Phase H2.D — AblationProfile + startup banner (1-2 days)

**Goal.** A run's active toggle state is visible to the user and to
the eventual paper-writing pipeline.

**Steps.**

1. Create \`mist-core/src/main/java/io/mist/core/config/AblationProfile.java\`:
   \`\`\`java
   package io.mist.core.config;

   public record AblationProfile(
       boolean shapeOracleEnabled,
       boolean spanTreeInvariantEnabled,
       boolean statusPropagationInvariantEnabled,
       boolean responseEnvelopeInvariantEnabled,
       boolean timingEnvelopeInvariantEnabled,
       boolean banditEnabled,
       boolean faultMiningEnabled
   ) {
       public static AblationProfile from(MstConfig config) {
           return new AblationProfile(
               config.oracle().shapeOracleEnabled(),
               config.oracle().spanTreeInvariantEnabled(),
               config.oracle().statusPropagationInvariantEnabled(),
               config.oracle().responseEnvelopeInvariantEnabled(),
               config.oracle().timingEnvelopeInvariantEnabled(),
               config.scheduler().banditEnabled(),
               config.faulty().faultMiningEnabled()  // or wherever this lives
           );
       }
       public String summary() {
           // One-line human readable summary, e.g.:
           //   "[oracle:on (span,status,response | timing:off) bandit:on faultmining:off]"
           // Used in the startup banner.
       }
   }
   \`\`\`
2. In \`MistMain\` or \`MistRunner.run()\` (whichever owns the
   startup banner), log:
   \`\`\`
   [MIST] ablation profile: <AblationProfile.summary()>
   \`\`\`
   immediately after \`MstConfig.fromSystemProperties()\` is called.
3. Attach the \`AblationProfile.summary()\` to the run's summary
   report (\`stats\` or whatever the existing reporting class is) so
   that the future-task paper-writing pipeline can read which
   configuration was used for each run.
4. **Do not** add Allure attachments for the profile in this work —
   that is a paper-writing concern. The log line + the run summary
   are sufficient.

**Gate.** § 7.4.

### Phase H2.E — Verification (2-3 days)

**Goal.** Prove that each of the R1-R4 ablation rows produces a
deterministic, distinguishable run.

**Steps.**

1. **R1 (MIST-full):** run the bundled demo with all defaults and
   \`-Dmst.fault.mining.enabled=true\` (since R1's defining property
   is "everything on"):
   \`\`\`bash
   mvn -q clean install -DskipTests
   java -Drandom.seed=42 -Dmst.fault.mining.enabled=true \\
        -jar mist-cli/target/mist.jar \\
        mist-cli/src/main/resources/My-Example/trainticket-demo.properties
   find target/test-cases -name 'Flow_Scenario_*.java' -exec sha256sum {} \\; \\
     | sort > /tmp/h2-R1.sums
   \`\`\`
2. **R2 (MIST − trace-shape-oracle):**
   \`\`\`bash
   mvn -q clean install -DskipTests
   java -Drandom.seed=42 \\
        -Dmst.oracle.shape.enabled=false \\
        -Dmst.fault.mining.enabled=true \\
        -jar mist-cli/target/mist.jar \\
        mist-cli/src/main/resources/My-Example/trainticket-demo.properties
   find target/test-cases -name 'Flow_Scenario_*.java' -exec sha256sum {} \\; \\
     | sort > /tmp/h2-R2.sums
   \`\`\`
3. **R3 (MIST − adaptive-fault):** identical to today's bundled demo
   (which already runs with \`mst.fault.mining.enabled=false\`):
   \`\`\`bash
   mvn -q clean install -DskipTests
   java -Drandom.seed=42 -jar mist-cli/target/mist.jar \\
        mist-cli/src/main/resources/My-Example/trainticket-demo.properties
   find target/test-cases -name 'Flow_Scenario_*.java' -exec sha256sum {} \\; \\
     | sort > /tmp/h2-R3.sums
   \`\`\`
4. **R4 (MIST − trace-shape − adaptive):**
   \`\`\`bash
   mvn -q clean install -DskipTests
   java -Drandom.seed=42 \\
        -Dmst.oracle.shape.enabled=false \\
        -jar mist-cli/target/mist.jar \\
        mist-cli/src/main/resources/My-Example/trainticket-demo.properties
   find target/test-cases -name 'Flow_Scenario_*.java' -exec sha256sum {} \\; \\
     | sort > /tmp/h2-R4.sums
   \`\`\`
5. **Determinism:** rerun each of R1-R4 a second time and confirm
   the \`.sums\` files are byte-identical across the two runs of the
   same configuration. This is the "each ablation row reproduces"
   check.
6. **Byte-identical equivalence with the pre-work baseline:** the
   pre-work baseline (\`/tmp/h2-baseline/scenarios.sums\` from § 2.1)
   was captured under the pre-toggle defaults. Compare it against
   R3 (which exactly matches today's bundled defaults):
   \`\`\`bash
   diff /tmp/h2-baseline/scenarios.sums /tmp/h2-R3.sums
   \`\`\`
   **The diff must be empty.** If it is not, you have introduced
   unintended behaviour change.
7. Run \`mvn -q test -pl mist-core,mist-cli\`. Add new unit tests
   under \`mist-core/src/test/java/io/mist/core/oracle/shape/\` and
   \`mist-core/src/test/java/io/mist/core/generation/\` that
   construct a \`TraceShapeOracle\` / \`MistGenerator\` with each
   toggle combination and assert the gated behaviour:
   - Disabled oracle returns empty verdict.
   - Each per-invariant disable skips its invariant only.
   - Disabled bandit yields insertion-order ranking.

**Gate.** § 7.5.

---

## 6. Forbidden Patterns

1. **Do not rebrand \`mst.\` → \`mist.\` in property keys.** The
   two-layer brand strategy is locked (code: \`Mist\` / config: \`mst.\`).
2. **Do not change \`mst.fault.mining.enabled\`'s key, default, or
   gating location.** It is already correct.
3. **Do not introduce a "run multiple ablation configs in one JVM"
   feature.** One JVM = one configuration. The future-task
   evaluation pipeline launches one JVM per row, per repetition.
4. **Do not add CLI flags.** Toggles go through the
   \`.properties\` file → \`MstConfig\` path, like every other
   configuration in MIST.
5. **Do not add new sub-records to \`MstConfig\` beyond \`Oracle\`
   and \`Scheduler\`.** If a configuration value does not fit into
   one of those two, escalate.
6. **Do not delete \`TimingEnvelopeInvariant\`.** It stays as an
   experimental hook. Only its **default** flips to \`false\`.
7. **Do not change the contract of \`TraceShapeVerdict\`** beyond
   ensuring \`TraceShapeVerdict.empty()\` exists (add it if it does
   not). A disabled oracle still returns a verdict object so
   downstream code does not have to special-case \`null\`.
8. **Do not run the actual ablation experiments in this work.** This
   prompt enables the experiments; it does not execute them. Future
   user-driven evaluation is a separate task.
9. **Do not modify the LLM call cache or \`-Drandom.seed\` gating.**
   They were settled by Fix S-4 and are out of scope.
10. **Do not modify the paper files.** The 4 → 3 invariant pivot has
    already been committed (\`b96fb8b6\`, \`dfc11b46\`). This work
    only changes the code.
11. **Do not add comments that say "added for ablation" or
    "experimental hook" inside the production source.** The why is
    the commit messages. Code comments must remain stable across
    the lifecycle of the toggle.
12. **Do not introduce backwards-compatibility shims** for any
    constructor whose signature changes (e.g.
    \`TraceShapeOracle\`'s). Update every call site cleanly.
13. **Do not log the active ablation profile inside Allure
    attachments.** That belongs to the paper-writing pipeline, not
    here.

---

## 7. Verification Gates

Each gate is a checklist. Tick every box before tagging the phase
complete.

### 7.1 Gate H2.A — Inventory

- [ ] \`debug/Conference-refinement/H2_INVENTORY.md\` exists.
- [ ] Every entry carries a verified \`file:line\` reference.
- [ ] The inventory confirms the file-path assumptions in § 3.1
      (or corrects them).
- [ ] The inventory documents the current state of
      \`mst.fault.mining.enabled\` and confirms the key, default,
      and gating location are unchanged.

### 7.2 Gate H2.B — Toggles added

- [ ] \`MstConfig.Oracle\` and \`MstConfig.Scheduler\` sub-records
      exist with the accessors enumerated in § 4.4.
- [ ] All six new system-property keys (per § 4.1) read correctly:
      add a minimal unit test \`MstConfigOracleAndSchedulerTest\` that
      sets each property and asserts the accessor reflects it.
- [ ] \`mvn -q -DskipTests compile\` passes.
- [ ] \`mvn -q test -pl mist-core -Dtest='MstConfig*'\` passes.
- [ ] No call site outside \`MstConfig\` reads any of the six new
      properties via \`System.getProperty\` (verify with grep).

### 7.3 Gate H2.C — Toggles wired into runtime

- [ ] \`TraceShapeOracle.evaluate(...)\` short-circuits when
      \`shapeOracleEnabled\` is \`false\`.
- [ ] Each per-invariant flag in \`TraceShapeOracle.evaluate(...)\`
      gates exactly its invariant; no cross-talk.
- [ ] \`MistGenerator\`'s \`ThompsonScheduler\` construction is gated
      by \`banditEnabled\`; the disabled path preserves insertion
      order.
- [ ] \`mvn -q -DskipTests compile\` passes.
- [ ] All seven toggles' combinations produce no NullPointerException
      or other unexpected exceptions in a smoke run (manual:
      flip each toggle to \`false\` once and confirm the demo
      completes without error).

### 7.4 Gate H2.D — AblationProfile + banner

- [ ] \`io.mist.core.config.AblationProfile\` exists; its
      \`from(MstConfig)\` factory builds correctly.
- [ ] \`MistMain\` or \`MistRunner\` logs the profile summary
      immediately after config load.
- [ ] The summary string is human-readable in one line.
- [ ] Unit test \`AblationProfileTest\` covers all seven boolean
      combinations of the summary line (smoke; not exhaustive
      2⁷ = 128 cases — pick all-on, all-off, and one mid).
- [ ] No Allure attachment changes.

### 7.5 Gate H2.E — Verification

- [ ] \`diff /tmp/h2-baseline/scenarios.sums /tmp/h2-R3.sums\` is
      empty (R3 matches today's bundled defaults byte-for-byte).
- [ ] Each of R1, R2, R3, R4 reproduces byte-for-byte when run twice
      with the same seed.
- [ ] R2 and R4 logs show **zero** Trace Shape Oracle verdicts
      attached to any test (a side-effect of the disabled oracle).
- [ ] R1's run log shows the AblationProfile summary line with all
      flags \`on\` except where defaults say otherwise.
- [ ] \`mvn -q test -pl mist-core,mist-cli\` passes.
- [ ] No regression in the legacy launch path (if it still exists
      post-B1; verify in inventory phase).

### 7.6 Gate H2.F — Final cleanup

- [ ] No \`@Deprecated\` annotations or "TODO H2" comments left in
      the source tree.
- [ ] \`H2_INVENTORY.md\` is marked "Status: closed" with a one-line
      summary of the outcome.
- [ ] README or flow.md documents the six new toggles (one-line
      mention each in whichever doc currently lists \`mst.*\`
      properties). If \`flow.md\` has a "Conditions, Flags, and
      Inputs" table, add the six rows there.

---

## 8. Git Workflow

### 8.1 Per-phase commits

One phase ≈ one logical chunk of work ≈ one or two commits. The
agent commits to the working branch after each phase passes its
gate. Commit messages follow the existing repository's style (check
\`git log --oneline -20\` on \`inject-detection\` to calibrate).

Format:

\`\`\`
<imperative subject ≤ 72 chars>

<optional body explaining why this change was needed and what
non-obvious design decisions it embodies. Wrap at 72 chars.>
\`\`\`

**Do not** include any of the following in commit messages:
- 🤖 or other emoji-banner lines
- \`Co-Authored-By: Claude\` trailers
- \`Generated with Claude Code\` text
- \`https://claude.ai/code/session_…\` URLs
- Self-referential mentions of "this agent", "the AI", etc.

**Do** include:
- Phase reference: \`h2.b: add Oracle and Scheduler sub-records to MstConfig\`
- Concrete enumeration of what changed (which keys, which classes)
- Non-obvious decisions

Example good message:

\`\`\`
h2.c: gate TraceShapeOracle invariants and ThompsonScheduler

Adds the runtime gates for the toggles introduced in h2.b:
- TraceShapeOracle.evaluate(...) short-circuits when
  mst.oracle.shape.enabled is false, returning an empty verdict.
- Each per-invariant flag (span_tree, status_propagation,
  response_envelope, timing) skips its load+evaluate call
  individually.
- MistGenerator preserves fault-queue insertion order when
  mst.scheduler.bandit.enabled is false instead of constructing
  ThompsonScheduler.

TimingEnvelopeInvariant default flips to false to match the
recently revised paper claim of three invariants (commits b96fb8b6,
dfc11b46); the class is retained as an experimental hook.

Byte-identical equivalence with the pre-work baseline verified on
R3 (defaults match today's bundled demo).
\`\`\`

Example bad message (do not produce this):

\`\`\`
Add ablation toggles 🤖

Generated with Claude Code

Co-Authored-By: Claude <noreply@anthropic.com>
\`\`\`

### 8.2 No PR

Do **not** open a pull request unless the user explicitly says
"open a PR". The default workflow is: work on the branch, get the
user's sign-off via chat at each gate, then merge to
\`inject-detection\` and push.

### 8.3 Final merge to inject-detection

When § 7.6 passes and the user explicitly approves:

\`\`\`bash
git checkout inject-detection
git merge --ff-only claude/h2-ablation-infrastructure \\
  || git merge --no-ff claude/h2-ablation-infrastructure \\
       -m "merge h2: ablation infrastructure for main-track evaluation"
git push -u origin inject-detection
\`\`\`

If the fast-forward merge is not possible, rebase the working branch
onto the new \`inject-detection\` HEAD:

\`\`\`bash
git checkout claude/h2-ablation-infrastructure
git fetch origin inject-detection
git rebase origin/inject-detection
# resolve conflicts; escalate non-trivial conflicts to user
git checkout inject-detection
git merge --ff-only claude/h2-ablation-infrastructure
git push -u origin inject-detection
\`\`\`

### 8.4 Push retry on network failure

\`\`\`bash
for delay in 2 4 8 16; do
  if git push -u origin inject-detection; then break; fi
  sleep $delay
done
\`\`\`

Do not retry on non-network errors (rejected pushes, hook failures,
auth failures). Diagnose and report instead.

---

## 9. Rollback

If at any point the agent or the user decides to abandon the work:

1. Do **not** force-push or rewrite \`inject-detection\` history.
2. Abandon the working branch:
   \`\`\`bash
   git branch -D claude/h2-ablation-infrastructure
   git push origin --delete claude/h2-ablation-infrastructure
   \`\`\`
3. The pre-work \`inject-detection\` HEAD remains the source of
   truth; no published artifact is at risk.

If the work has partially landed (e.g. Phases H2.A and H2.B made it
through and were merged before a problem surfaced in H2.C), do **not**
revert the published commits. Either fix forward with a corrective
commit, or escalate to the user.

The cardinal rule: published commits stay; you fix forward.

---

## 10. Glossary

| Term | Meaning |
|---|---|
| **Ablation** | Disabling a single contribution to measure its individual effect on the overall metric. The Path B paper plan needs four named ablation configurations (R1-R4). |
| **R1 / R2 / R3 / R4** | The four MIST ablation configurations from \`PATH_B_POSITIONING.md\` § 4.2. R1 = everything on; R4 = both new contributions off. |
| **TraceShapeOracle** | The MIST oracle subsystem that evaluates trace-shape invariants. Whole-system toggle: \`mst.oracle.shape.enabled\`. |
| **Invariant** | One of \`SpanTreeShape\`, \`StatusPropagation\`, \`ResponseEnvelope\`, \`TimingEnvelope\`. Each has its own toggle. |
| **TimingEnvelope** | The invariant that was dropped from the paper's contribution in commits \`b96fb8b6\`/\`dfc11b46\`. Its code remains; its toggle defaults to \`false\` (the one default flip in this work). |
| **ThompsonScheduler** | The Beta-Binomial bandit that re-ranks the fault-target queue inside \`MistGenerator\`. Toggle: \`mst.scheduler.bandit.enabled\`. When disabled, the queue uses insertion order. |
| **FaultMiner** | The LLM-driven miner that proposes SUT-specific fault categories. Already gated by \`mst.fault.mining.enabled\` (default \`false\`); this work does **not** modify it. |
| **AblationProfile** | New record introduced by this work; captures the active toggle state for a run. |
| **\`inject-detection\`** | The user's main branch. All final pushes land here. |
| **\`claude/h2-ablation-infrastructure\`** | The working branch this work produces. |
| **Byte-identical equivalence** | Under \`-Drandom.seed=42\`, the generated \`Flow_Scenario_*.java\` files produce the same SHA-256 sums before and after this work, when run under the defaults that match today's behaviour (i.e. R3). |

---

## 11. Escalation Triggers

Escalate to the user (one-line question, then continue with the next
independent sub-task) when:

1. The pre-flight check in § 2.1 reveals that file paths have moved
   substantially since this prompt was written, and the inventory
   you produce in Phase H2.A diverges from § 3.1 by more than three
   entries.
2. Byte-identical equivalence fails on R3 vs the baseline. The
   pre-work baseline is the contract; if R3 (defaults matching
   today) diverges, something has introduced unintended behaviour
   change.
3. The user wants \`TimingEnvelope\` to remain \`true\` by default
   to preserve full byte-identical equivalence across the
   entire output. § 4.3 explains the alternative; ask before
   committing the default flip.
4. \`MistGenerator\` does not already have a reference to
   \`MstConfig\`, and threading one through requires a constructor
   change that ripples to more than three call sites. Ask whether
   to absorb the ripple or take a localised \`MstConfig.instance()\`
   read (the latter is discouraged but may be necessary).
5. \`FaultMiner\`'s existing \`mst.fault.mining.enabled\` is not
   actually wired today (i.e. the gate exists in name only). If so,
   this work needs to fix it as part of Phase H2.C — escalate
   first to confirm scope expansion.
6. Any toggle combination produces an exception that surfaces a real
   bug (e.g. \`MistGenerator\` requires \`ThompsonScheduler\` for a
   non-bandit reason). Surface the bug; do not silently re-enable
   the toggle to mask it.

Do **not** escalate for:

- Routine compile errors (fix them).
- Routine test failures introduced by this work (fix them).
- Style or formatting choices (use repository conventions).
- Whether to use a record, sealed class, or plain class for
  \`AblationProfile\` (use whatever pattern \`MstConfig\`'s
  sub-records use today; if they are plain classes, use a plain
  class; if records, use a record).

---

*End of prompt.*
