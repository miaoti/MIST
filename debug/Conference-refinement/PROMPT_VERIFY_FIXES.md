# MIST — Fix-Set Verification

> **Audience.** Self-contained brief for a local agent (Claude Code or
> equivalent) that will independently verify the four fix tracks
> landed on `inject-detection` (and the one pending merge from
> `claude/review-architecture-l6Xhj`) actually do what their commit
> messages claim. No new code is written by this work; the agent
> reads, greps, runs, and reports.

---

## 0. Hard Rules

### 0.1 No-Claude-attribution policy

This work produces **only a verification report** — no source code
edits. Any artefact the agent commits (the report file itself if
the user asks for it on disk) must look like ordinary engineering
output:

- **Do NOT** include `🤖 Generated with [Claude Code]` lines.
- **Do NOT** include `Co-Authored-By: Claude` trailers.
- **Do NOT** include `https://claude.ai/code/session_*` URLs.
- **Do NOT** mention "Claude", "Anthropic", "AI", "agent", or "LLM
  generated" in any committed text.
- **Do NOT** modify git config.

### 0.2 Branch discipline

- Verification runs against **`origin/inject-detection`** (the user's
  main branch) by default. If the user wants the pending
  `bf61ce89` IDGenerator fix included, run a second pass against
  `origin/claude/review-architecture-l6Xhj` and report the delta.
- **Do not push anything** in this verification work. Do not
  modify any committed file. If the verification report itself needs
  to be on disk, write it to a path the user specifies (or
  `debug/Conference-refinement/VERIFICATION_REPORT_<YYYY-MM-DD>.md`
  if no path is specified) and **show the user the diff before
  staging**.

### 0.3 Honesty over completeness

This is a verification task, not a green-rubber-stamp task. If a
check fails, **say it failed**, document the evidence, do not paper
over it. The user wants to know what is actually working, not what
should be working according to the commit messages.

If a check is ambiguous (passes one way, fails another way), report
both outcomes and let the user adjudicate.

### 0.4 Tooling

- Use `Read` / `Bash` / `grep` for inspection.
- Use `Write` only for the final verification report.
- Do not use `Edit` — this work does not modify any existing file.

---

## 1. Mission

Four substantial work tracks landed on `inject-detection` between
the `d276461f docs(b1): add prompt …` and `9ea0a34b docs(flow): …`
commits. A fifth fix (`bf61ce89` IDGenerator seed-aware) is on
`claude/review-architecture-l6Xhj` awaiting merge. The user needs
an independent answer to a single question:

> **Do the runtime, build, and paper artefacts actually reflect
> what the commit messages claim, end-to-end?**

The four tracks plus the pending one:

| # | Track | Landing commits | Claim |
|---|---|---|---|
| **V1** | B1 sever (Path B Phase 1.C complete) | `6cd2d10b sever: delete mist-restest-adapter module` and ~50 preceding commits | `mist-core` has zero `es.us.isa.*` source; `mist-restest-adapter` module deleted; `MultiServiceTestCaseGenerator` no longer `extends AbstractTestCaseGenerator`; both legacy and new launchers reach the same generator |
| **V2** | 4 → 3 invariants paper pivot | `b96fb8b6 paper: drop TimingEnvelope from contribution; 4 invariants → 3`, `dfc11b46 paper(nostep): align Nostep_version with 3-invariant pivot + 3-module post-B1` | Both `paper/main.tex` and `paper/main_issta.tex` (plus the long `Nostep_version.tex`) claim 3 invariants, not 4; the paragraph explaining the timing exclusion is present |
| **V3** | H2 ablation infrastructure | `df58553e h2.a`, `996b3970 h2.b`, `1393511b h2.c`, `339e115b h2.d`, `300f4518 h2.e+h2.f` | Six new toggles in `MstConfig` (5 oracle + 1 scheduler) plus the pre-existing `mst.fault.mining.enabled`; runtime gates wired; `AblationProfile` exists; startup banner shows the profile |
| **V4** | Gap 1 — bandit seed determinism | `fb5bb746 fix(generator): seed ThompsonScheduler so -Drandom.seed=42 is stable` | Under `-Drandom.seed=42`, two seeded runs of the noexec demo produce byte-identical `Flow_Scenario_*.java` files (modulo the timestamped class-name suffix, which V5 also fixes) |
| **V5** | Gap 1.1 — IDGenerator seed-aware (**pending merge**) | `bf61ce89 fix(idgen): seed-aware generateTimeId` on `origin/claude/review-architecture-l6Xhj` | The class-name suffix `TrainTicketTwoStageTest_<...>` is no longer time-derived under `-Drandom.seed=<n>`; it becomes `_<n>` so the byte-identical test passes raw, without normalisation |

V1-V4 must pass on `origin/inject-detection`. V5 is verified on
`origin/claude/review-architecture-l6Xhj` only.

---

## 2. Pre-flight

Before running the verification, capture the environment so failures
can be reproduced:

```bash
# Branch tip and clean state
git fetch origin
git rev-parse origin/inject-detection
git rev-parse origin/claude/review-architecture-l6Xhj

# Working directory must be clean before each verification phase
git status --short
```

Then build the reactor once:

```bash
mvn -q -DskipTests --batch-mode install 2>&1 | tail -5
# Must end with BUILD SUCCESS
```

If the build fails, **stop**. Do not run further checks; report the
build failure with its tail.

---

## 3. Verification Tasks

Each task ends with a structured **VERDICT** line the agent populates.
The verdict is one of: `PASS`, `FAIL`, `INCONCLUSIVE`. Append a
one-sentence reason. Collect all verdicts into the final report.

### V1 — B1 sever (architecture)

The claim is: MIST is a stand-alone tool with no RESTest source.

#### V1.1 Module layout

```bash
test ! -d mist-restest-adapter && echo "OK: mist-restest-adapter is gone" \
  || echo "FAIL: mist-restest-adapter directory still exists"

ls mist-core mist-llm mist-cli 2>&1 | grep -E "^mist-(core|llm|cli)" \
  || echo "FAIL: not all three target modules present"
```

Expected output:
- `mist-restest-adapter` does not exist
- exactly `mist-core`, `mist-llm`, `mist-cli` present at top level

#### V1.2 Zero RESTest source in mist-core

```bash
grep -rE 'es\.us\.isa' mist-core/src/main/java && echo "FAIL: RESTest imports linger" \
  || echo "OK: zero es.us.isa references in mist-core"
```

Expected: zero matches.

#### V1.3 No inheritance from `AbstractTestCaseGenerator`

```bash
grep -rE 'extends AbstractTestCaseGenerator' mist-core mist-cli mist-llm \
  && echo "FAIL: extends survived" \
  || echo "OK: inheritance severed"
```

Expected: zero matches.

#### V1.4 Generator class moved to `io.mist.core.generation.MistGenerator`

```bash
test -f mist-core/src/main/java/io/mist/core/generation/MistGenerator.java && echo "OK"
grep -nE '^public final class MistGenerator' \
  mist-core/src/main/java/io/mist/core/generation/MistGenerator.java
```

Expected: class is `public final`, no `extends` clause.

#### V1.5 Dependency tree clean

```bash
mvn -pl mist-core dependency:tree --batch-mode 2>&1 \
  | grep -E 'es\.us\.isa' \
  && echo "FAIL: RESTest dep in mist-core" \
  || echo "OK: clean"
```

Expected: zero hits.

**VERDICT V1**: `[fill in]` — `[one-line reason]`

### V2 — 4 → 3 invariants paper pivot

The claim is: every paper variant now ships **3 invariants**, drops
the `TimingEnvelope` from the contribution claim, and explains why.

#### V2.1 No surviving 4-invariant language

```bash
for f in paper/main.tex paper/main_issta.tex "Restest_Micro (1)/Nostep_version.tex"; do
  echo "=== $f ==="
  grep -in "four checkable\|four learned\|four invariant\|four families" "$f" \
    | head -5 || echo "  (clean)"
done
```

Expected: zero matches in any file.

#### V2.2 Three invariants explicitly named

```bash
for f in paper/main.tex paper/main_issta.tex "Restest_Micro (1)/Nostep_version.tex"; do
  echo "=== $f ==="
  grep -cE "three checkable|three learned|three invariant|three retained|three families" "$f"
done
```

Expected: each file has at least one match.

#### V2.3 TimingEnvelope NOT listed in the invariant bullet block

```bash
for f in paper/main.tex paper/main_issta.tex; do
  echo "=== $f ==="
  awk '/\\textsc\{SpanTreeShape\}/,/\\end\{itemize\}/' "$f" \
    | grep -i "TimingEnvelope" \
    && echo "FAIL: TimingEnvelope listed in $f" \
    || echo "OK: TimingEnvelope absent from invariant list"
done
```

For `Nostep_version.tex`, the equivalent check is that no
`\paragraph{Timing envelope.}` block exists:

```bash
grep -n "paragraph{Timing envelope" "Restest_Micro (1)/Nostep_version.tex" \
  && echo "FAIL" || echo "OK: Timing paragraph removed"
```

#### V2.4 Explanation paragraph present

```bash
for f in paper/main.tex paper/main_issta.tex "Restest_Micro (1)/Nostep_version.tex"; do
  echo "=== $f ==="
  grep -c "PII-stripping\|PII stripping\|GDPR\|HIPAA\|production-realistic\|trace anomaly detection" "$f"
done
```

Expected: each file has at least one hit explaining the timing
exclusion / the PII-stripping defensive angle.

#### V2.5 Module-count language updated

`Nostep_version.tex` claimed a four-module project at one point. After
the pivot, it should say three.

```bash
grep -n "four-module\|four Maven modules" "Restest_Micro (1)/Nostep_version.tex" \
  && echo "FAIL: four-module claim survives" \
  || echo "OK"
grep -c "three Maven modules\|three-module" "Restest_Micro (1)/Nostep_version.tex"
```

Expected: zero "four-module" hits; at least one "three" hit.

**VERDICT V2**: `[fill in]` — `[one-line reason]`

### V3 — H2 ablation infrastructure

The claim is: six new toggles wired end-to-end, plus
`AblationProfile` + startup banner, plus tests.

#### V3.1 Six new property keys exist with the right defaults

```bash
grep -A 1 'parseBool("mst.oracle' \
  mist-core/src/main/java/io/mist/core/config/MstConfig.java
grep -A 1 'parseBool("mst.scheduler' \
  mist-core/src/main/java/io/mist/core/config/MstConfig.java
```

Expected (one match per key):

| Key | Default |
|---|---|
| `mst.oracle.shape.enabled` | `"true"` |
| `mst.oracle.shape.invariants.span_tree.enabled` | `"true"` |
| `mst.oracle.shape.invariants.status_propagation.enabled` | `"true"` |
| `mst.oracle.shape.invariants.response_envelope.enabled` | `"true"` |
| `mst.oracle.shape.invariants.timing.enabled` | `"false"` ← the one default flip |
| `mst.scheduler.bandit.enabled` | `"true"` |

Verify especially that the Timing default is `"false"` — that is the
single intentional behaviour change and the verification gate's main
concern.

#### V3.2 Runtime wiring

`TraceShapeOracle.evaluate(...)` must short-circuit on the
whole-oracle toggle and gate each per-invariant call:

```bash
grep -nE '!shapeOracleEnabled|spanTreeEnabled|statusPropagationEnabled|responseEnvelopeEnabled|timingEnvelopeEnabled' \
  mist-core/src/main/java/io/mist/core/oracle/shape/TraceShapeOracle.java
```

Expected: at least 5 distinct lines (1 short-circuit + 4 per-invariant
gates).

`MistGenerator` must gate the Thompson scheduler:

```bash
grep -nE 'banditEnabled|applyBanditGate' \
  mist-core/src/main/java/io/mist/core/generation/MistGenerator.java
```

Expected: the bandit construction is wrapped in an `if (banditEnabled)`
branch, and the disabled branch preserves insertion order.

#### V3.3 AblationProfile + banner

```bash
test -f mist-core/src/main/java/io/mist/core/config/AblationProfile.java && echo OK
grep -nE 'AblationProfile|ablation profile' \
  mist-cli/src/main/java/io/mist/cli/MistRunner.java | head -5
```

Expected: file present; `MistRunner.run()` constructs an
`AblationProfile` and logs `[MIST] ablation profile: <summary>`.

#### V3.4 Tests pass

```bash
mvn test -pl mist-core,mist-cli -am --batch-mode 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, no test failures. The specific H2 tests are:

```bash
mvn test -pl mist-core --batch-mode \
  -Dtest='MstConfigOracleAndSchedulerTest,AblationProfileTest,TraceShapeOracleTogglesTest,MistGeneratorBanditGateTest' \
  2>&1 | tail -10
```

Expected: 30 tests, 0 failures.

#### V3.5 R1-R4 each runs to completion under seed

For each of the four ablation rows the H2 work claims to support,
launch the noexec demo and confirm it exits 0 and emits the right
banner. The four invocations:

```bash
for ROW in \
  "R1 mst.fault.mining.enabled=true" \
  "R2 mst.oracle.shape.enabled=false:mst.fault.mining.enabled=true" \
  "R3 " \
  "R4 mst.oracle.shape.enabled=false"; do
  NAME=${ROW%% *}
  OVR=${ROW#* }
  ARGS=""
  IFS=':' read -ra KV <<< "$OVR"
  for kv in "${KV[@]}"; do [ -n "$kv" ] && ARGS+=" -D$kv"; done
  echo "=== $NAME ==="
  rm -rf mist-cli/src/test/java/trainticket_twostage_test/TrainTicketTwoStageTest_*
  java -Drandom.seed=42 $ARGS \
    -jar mist-cli/target/mist.jar \
    mist-cli/src/main/resources/My-Example/trainticket-demo-noexec.properties \
    2>&1 | grep -E "ablation profile|Flow_Scenario|BUILD" | head -5
done
```

Expected: each row exits 0 and produces a sensible banner line. The
banner format is documented in `flow.md` and looks like
`[MIST] ablation profile: [oracle:... bandit:... faultmining:...]`.

**VERDICT V3**: `[fill in]` — `[one-line reason]`

### V4 — Gap 1 bandit seed determinism

The claim is: under `-Drandom.seed=42`, two consecutive runs of
each R-row produce byte-identical `Flow_Scenario_*.java` files when
the timestamped suffix is normalised.

```bash
# Two runs of R3 (defaults)
rm -rf mist-cli/src/test/java/trainticket_twostage_test/TrainTicketTwoStageTest_*
java -Drandom.seed=42 -jar mist-cli/target/mist.jar \
  mist-cli/src/main/resources/My-Example/trainticket-demo-noexec.properties \
  > /tmp/v4-r1.log 2>&1
find mist-cli/src/test/java/trainticket_twostage_test -name 'Flow_Scenario_*.java' \
  -exec sed -E 's/TrainTicketTwoStageTest_[0-9]+/TrainTicketTwoStageTest_NORM/g' {} \; \
  | sha256sum > /tmp/v4-r1.normsum

rm -rf mist-cli/src/test/java/trainticket_twostage_test/TrainTicketTwoStageTest_*
java -Drandom.seed=42 -jar mist-cli/target/mist.jar \
  mist-cli/src/main/resources/My-Example/trainticket-demo-noexec.properties \
  > /tmp/v4-r2.log 2>&1
find mist-cli/src/test/java/trainticket_twostage_test -name 'Flow_Scenario_*.java' \
  -exec sed -E 's/TrainTicketTwoStageTest_[0-9]+/TrainTicketTwoStageTest_NORM/g' {} \; \
  | sha256sum > /tmp/v4-r2.normsum

diff /tmp/v4-r1.normsum /tmp/v4-r2.normsum && echo "OK: R3 byte-identical after suffix normalisation" \
  || echo "FAIL: V4 fix did not stabilise R3"
```

Expected: the two normalised SHA-256 sums match. (Note the `sed`
normalisation of the class-name suffix — V4 alone does NOT remove
the time-derived suffix; V5 does.)

**VERDICT V4**: `[fill in]` — `[one-line reason]`

### V5 — Gap 1.1 IDGenerator seed-aware (pending merge)

V5 is verified only against `origin/claude/review-architecture-l6Xhj`.
Skip if the user has not asked to include it.

```bash
# Switch to the branch carrying V5
git fetch origin
git checkout origin/claude/review-architecture-l6Xhj
mvn -q -DskipTests --batch-mode install 2>&1 | tail -3

# Two runs without suffix normalisation
rm -rf mist-cli/src/test/java/trainticket_twostage_test/TrainTicketTwoStageTest_*
java -Drandom.seed=42 -jar mist-cli/target/mist.jar \
  mist-cli/src/main/resources/My-Example/trainticket-demo-noexec.properties \
  > /tmp/v5-r1.log 2>&1
find mist-cli/src/test/java/trainticket_twostage_test -name 'Flow_Scenario_*.java' \
  -exec sha256sum {} \; | sort > /tmp/v5-r1.sums

rm -rf mist-cli/src/test/java/trainticket_twostage_test/TrainTicketTwoStageTest_*
java -Drandom.seed=42 -jar mist-cli/target/mist.jar \
  mist-cli/src/main/resources/My-Example/trainticket-demo-noexec.properties \
  > /tmp/v5-r2.log 2>&1
find mist-cli/src/test/java/trainticket_twostage_test -name 'Flow_Scenario_*.java' \
  -exec sha256sum {} \; | sort > /tmp/v5-r2.sums

diff /tmp/v5-r1.sums /tmp/v5-r2.sums && echo "OK: V5 byte-identical RAW, no normalisation needed" \
  || echo "FAIL: V5 fix did not eliminate suffix drift"

# Also confirm the class-name suffix is literally the seed
find mist-cli/src/test/java/trainticket_twostage_test -name 'TrainTicketTwoStageTest_*' -type d
```

Expected: the two raw SHA-256 sum files match, **and** the
directory under `trainticket_twostage_test/` is named exactly
`TrainTicketTwoStageTest_42` (the seed value, not a wall-clock
timestamp).

After V5, return to the user's chosen branch:

```bash
git checkout origin/inject-detection   # or whichever they prefer
```

**VERDICT V5**: `[fill in]` — `[one-line reason]`

---

## 4. Report Format

Write the final report to
`debug/Conference-refinement/VERIFICATION_REPORT_<YYYY-MM-DD>.md`
(create if absent). Format:

```markdown
# MIST Fix-Set Verification — <YYYY-MM-DD>

Verified against:
- `origin/inject-detection` @ <SHA>
- `origin/claude/review-architecture-l6Xhj` @ <SHA>  (for V5)

## Summary

| Track | Verdict | Notes |
|---|---|---|
| V1 — B1 sever | PASS / FAIL | <one-liner> |
| V2 — 3-invariant pivot | PASS / FAIL | <one-liner> |
| V3 — H2 ablation infra | PASS / FAIL | <one-liner> |
| V4 — bandit seed | PASS / FAIL | <one-liner> |
| V5 — IDGenerator seed | PASS / FAIL | <one-liner> |

## Per-track evidence

### V1 — B1 sever
- V1.1 module layout: <result, with command output>
- V1.2 zero RESTest source: <result>
- V1.3 no extends: <result>
- V1.4 MistGenerator location: <result>
- V1.5 dependency tree clean: <result>

(repeat for V2-V5)

## Anomalies and Follow-ups

<List anything that didn't fit cleanly into PASS/FAIL: ambiguous
checks, expected-but-not-claim-breaking divergences, environment
limitations, etc. Each item should be actionable or explicitly
labelled "no action needed; for the record".>

## Reproduction

To reproduce this verification on a fresh checkout:
```
git clone <repo>
cd Rest
git checkout origin/inject-detection
# (then run each command from sections V1-V5)
```
```

The report goes to disk under
`debug/Conference-refinement/VERIFICATION_REPORT_<YYYY-MM-DD>.md`.
**Do not push** unless the user explicitly asks. Show the user the
file path and a one-paragraph summary when the verification is done.

---

## 5. Escalation Triggers

Escalate to the user when:

1. The build itself (`mvn install`) fails. Do not run any further
   checks; report the build failure.
2. The bundled demo properties file path has moved — the V4/V5
   commands assume
   `mist-cli/src/main/resources/My-Example/trainticket-demo-noexec.properties`.
   If `find . -name 'trainticket-demo-noexec.properties'` returns a
   different path, ask which one to use.
3. V4 or V5's byte-identical check fails. The fix's commit message
   makes a falsifiable promise; if it fails, the user needs to know
   immediately, with the diff of the differing files.
4. V1 reveals a surviving `es.us.isa.*` reference in `mist-core` that
   the B1 sever was supposed to eliminate. This invalidates the
   contribution claim and the user needs to know before the paper
   submission goes out.
5. The H2 test suite (V3.4) returns any failing test. Do not
   self-diagnose; report which test failed with its assertion message.

Do **not** escalate for:

- A clean PASS on every verdict (just produce the report).
- Environmental quirks that the user can resolve without changing
  the code (e.g. missing `JAVA_HOME`, stale Maven cache).
- Cosmetic differences in log lines.

---

## 6. Out of Scope

This verification does **not** check:

- LLM-enabled live demo runs (only noexec). The fix-set's
  byte-identical promise is documented to hold on noexec; live runs
  depend on warm cache state and external SUT behaviour.
- The paper's actual rendered PDF — only the LaTeX source. PDF
  rendering is a separate concern.
- Code style / formatting choices.
- Whether the H2 future-task evaluation phase has run (it has not;
  H2 only enabled the ablation, it did not execute it).
- Cross-SUT generalisation (only TrainTicket is bundled).
- Any of the open H2_FOLLOWUPS items (those are deferred by design).

---

*End of prompt.*
