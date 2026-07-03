# REVIEW-RIDER1-A — writer-side correlator join (commit e640748), soundness lens

Independent cold review. Lens: **correctness/soundness of the correlator join
semantics.** Claim under audit (debug/a-main/prep/g3-rider1-correlator.md): the
`<method>#<stepIdx>` correlator fixes positional misalignment (run #3: 71 control
vs 70 fault) **without changing any single pair's verdict rule**, and is
**fail-safe** — "an unmatched write is `unjoinedRecords`, never a silent drop; the
triple still FIREs iff ≥1 *correctly-aligned* pair fires."

**Verdict: ACCEPT-WITH-FIXES.** The join itself (multimap FIFO, both-side
leftovers, per-triple filter-before-join, all record shapes threading the id) is
correct and genuinely removes the run-#3 misalignment. But one path the commit did
NOT touch — the representative fallback in `evaluate()` — silently re-introduces a
crossed pair on the headline `pureDifferential` field in exactly the asymmetric-skip
regime the commit exists to harden, breaking the doc's stated fail-safe invariant.
Plus the correlator omits the class/scenario name, so it is not the "globally unique
join key" the doc claims across multi-class runs.

---

## Findings (most-severe first)

### F1 — HIGH — representative can FIRE on a *crossed* pair when the correlator join produces zero aligned pairs but both legs are non-empty

**Where:** `PairedFaultExecutor.evaluate()`, the empty-`join.pairs` fallback,
`PairedFaultExecutor.java:282-286`; reachable because `joinRecords`
(`PairedFaultExecutor.java:312-339`) can now return an **empty** `pairs` list with
**both** `controls` and `faults` non-empty (disjoint correlator sets) — a state the
*old* positional join could never produce.

**Why the old code was safe and the new code is not:** under the prior positional
join, `join.pairs` was empty **iff** `min(controls.size, faults.size) == 0`, i.e. at
least one leg empty. So the `else` fallback `verdict(controls.get(0), faults.get(0))`
always had a `null` argument → a NOT_EVALUABLE "missing record". Under the correlator
join, `join.pairs` is empty whenever the two correlator *sets are disjoint*, which can
happen with **both legs non-empty** → the fallback calls `verdict()` with two
**non-null but unrelated** records — a cross-pair — and can return FIRE.

**Concrete triggering scenario (opposite asymmetric skips — the exact mechanism the
commit cites):**
- Triple `T = POST /contactservice/contacts` is written by two scenarios in the run:
  `createViaOrder` (write at step 4 → correlator `createViaOrder#4`) and
  `createDirect` (write at step 2 → `createDirect#2`). `write()` groups by scenario
  name into two classes, so both method names coexist.
- **Control leg:** `createViaOrder` runs (X acked + persisted); `createDirect`'s guard
  fails → its write is `shouldSkip`. `controls = [createViaOrder#4: acked, present]`.
- **Fault leg:** injection kills `createViaOrder`'s upstream so its write is skipped;
  `createDirect` now runs and its write is dropped by the fault →
  `faults = [createDirect#2: acked, ABSENT]`.
- `joinRecords`: `faultBy = {createDirect#2:[f]}`; control's `createViaOrder#4` misses
  → `unjoined=1`; leftover fault deque → `unjoined=2`; **`pairs` is empty**.
- `evaluate`: `fires=noFires=notEvaluable=0`, `firstAny=null` → `else` branch →
  `representative = verdict(T, createViaOrder#4-control, createDirect#2-fault)`. Control
  acked+present, fault acked+absent → **FIRE**.
- Result: `pureDifferential = FIRE`, but `firePairs = 0`, `unjoinedRecords = 2`. The
  headline FIRE is a cross-pair of two *different* writes — the very misalignment the
  correlator is meant to eliminate, and a **recall-inflating false FIRE**.

This directly violates the doc's invariant "the triple FIREs iff ≥1 *correctly-aligned*
pair fires" (here zero aligned pairs fired) and the "never changes a pair's verdict
rule / removes false FIREs from misalignment" soundness claim. Blast radius is real:
`pureDifferential` is the headline — it is what `summarize()` prints
(`PairedFaultExecutor.java:709`) and the primary report field
(`PairedFaultExecutor.java:647`); a consumer scoring the triple off `pureDifferential`
(rather than `firePairs`) reads a spurious FIRE. The two fields silently disagree only
in this corner, which is itself a hazard (nothing reconciles them).

**Probability:** modest — needs two same-triple scenarios with *opposite* skips and no
third scenario contributing a joinable correlator (any joinable pair makes `firstAny`
non-null and bypasses the fallback). But it is reachable via the same asymmetric-skip
mechanism the commit is built to defend against, and the tallies staying correct
(`firePairs=0`) shows the fix's own machinery already "knows" nothing fired — only the
representative disagrees.

**Suggested fix:** make the representative correlator-aware / pairs-aware. When
`join.pairs` is empty, the honest verdict is NOT_EVALUABLE ("no aligned pair — N
records unjoined"), never a cross-pair. Minimal change: in the `else` branch, if BOTH
`controls` and `faults` are non-empty (i.e. the emptiness came from a disjoint
correlator join, not an empty leg), synthesize a NOT_EVALUABLE representative instead
of `verdict(controls.get(0), faults.get(0))`. Add a unit test with the disjoint-both-
non-empty shape asserting `pureDifferential == NOT_EVALUABLE` and `firePairs == 0`.

---

### F2 — MED — correlator omits the class/scenario name, so it is not run-unique across multiple generated classes; a cross-class method-name collision leaves a residual (narrowed) positional misalignment

**Where:** correlator string built at `MultiServiceRESTAssuredWriter.java:1994`
(`beforeWrite(..., "<method>#<idx>", ...)`) and `:2210` (`afterWrite`). It is
`<testMethodName>#<stepIdx>` — **no class/scenario name**.

**Why it matters:** a single pairing run spans **many** generated classes —
`write()` groups test cases by scenario name and emits one class per group
(`MultiServiceRESTAssuredWriter.java:151-168`), and one `beginRun`/`endRun` session
(`PairedFaultExecutor.java:172-192`) collects records across *all* of them.
`recordsFor` then pools every class's records for a triple by `tripleName` only
(`PairedFaultExecutor.java:353-361`). Java guarantees method-name uniqueness only
*within* a class; across classes it is not guaranteed — and the counter fallback
`testScenario<scenarioIdx>` **resets to 1 per class** (`:1435`, `:1445`), so
`testScenario1#1` recurs in every class that hits the null/empty/"workflow"
operationId path, and shared `operationId`s recur likewise.

**Concrete scenario:** two scenario-classes `A` and `B` each contain a write to the
same triple `T` under a method that resolves to the same name (`testScenario1`, or a
shared operationId), both at step 1 → both emit correlator `testScenario1#1`. Within
one leg the correlator bucket `testScenario1#1` now holds **two** records (A's and
B's), paired FIFO by execution order. If a fault-leg asymmetric skip drops A's write
(but not B's), the FIFO within that bucket pairs A-control against B-fault — the
original run-#3 misalignment, merely **narrowed to the collided bucket** instead of
shifting the whole triple. The commit's "leaves only that write unjoined instead of
shifting every subsequent pair" holds *per correlator bucket* but not *within* a
collided bucket, so the "free, deterministic, identical-across-legs join key" is not
strictly unique.

**Probability:** depends on whether two scenario-classes write the same triple with a
colliding method name; plausible for workflow suites (many scenarios create a contact
as a sub-step) and for the `testScenarioN` fallback path.

**Suggested fix:** include the class/scenario name in the correlator, e.g.
`escape(className) + "/" + escape(testMethodName) + "#" + stepIdx`. `className` is in
scope at emission (`writeTestSuite(..., className)`), class names are unique per run
(one file per scenario name), method names are unique per class, stepIdx per method —
so `<class>/<method>#<idx>` is genuinely run-unique. Update the two emission regexes
in `DataIntegrityEmissionTest` accordingly.

---

### F3 — LOW — `canCorrelate` is all-or-nothing per triple and reverts to the buggy positional join on a *single* null correlator, with no warning

**Where:** `PairedFaultExecutor.canCorrelate` (`PairedFaultExecutor.java:341-351`) —
any one `correlationId == null` makes the whole triple fall back to positional.

**Assessment:** this is the correct *fail-safe direction* (degrade to prior behavior,
never mix join modes — a genuinely mixed run would otherwise be ill-defined), and in a
single fresh-writer run it cannot trigger because every RunRecord shape threads the id
(verified below). But it is **silent**: if a future record shape is added that forgets
to pass the correlator, or a stale pre-correlator generated class is left on the
classpath alongside fresh ones, that triple silently reverts to the positional join
(re-exposing the run-#3 bug) with nothing logged. There is no observability that a
triple *expected* to correlate did not.

**Concrete scenario:** a maintainer adds an 11th RunRecord emission site and passes the
legacy 2-arg overload; that triple's records now contain one `null` correlator →
`canCorrelate` false → positional join → misalignment returns, undetected.

**Suggested fix:** when `controls`/`faults` are non-empty and a *mix* of null and
non-null correlators is seen (as opposed to the uniform legacy all-null case), log a
WARN ("triple T: N/M records lack a correlator — reverting to positional join") so the
degradation is visible in the run log. Optionally record it on the report JSON.

---

### F4 — LOW (pre-existing, note the interaction) — a write that dies between hooks as the *last* hooked call of a leg is never flushed by `endRun`, so it produces no record

**Where:** `DataIntegrityRuntime.endRun` (`:291-298`) returns `s.records` without
flushing a dangling `pending`; orphans are only flushed by the *next* `beforeWrite`
(`:328-340`).

**Assessment:** NOT a defect introduced here, and under the correlator join it is
**fail-safe**: the dead write's control sibling has a correlator with no fault match →
counted in `unjoined`, never misverdicted. Worth recording only because the doc leans
on "an unmatched write is `unjoinedRecords`, never a silent drop" — that holds for the
*surviving* leg's record, but the *dead* leg genuinely emits no record at all (a silent
absence of a record, correctly surfaced as the sibling's unjoined count). No fix
required for this commit; a future `endRun` orphan-flush would make the disclosure
exact.

---

## Verified CORRECT

- **Filter-before-join.** `recordsFor` filters by `tripleName` *before* `joinRecords`
  (`PairedFaultExecutor.java:243-245`), so cross-triple step-index collisions
  (`m#3` for triple A vs `m#3` for triple B) can never cross-join. Correct and load-
  bearing.
- **Both-side leftovers counted.** Constructed `controls=[m#0,m#1]`,
  `faults=[m#1,m#2]`: `m#0` control unjoined (+1), `m#1` pairs, `m#2` fault leftover
  (+1) → `unjoined=2`, one pair. The control loop counts control-only misses
  (`:325`) and the trailing deque sweep counts fault-only leftovers (`:328-330`).
  Correct.
- **Duplicate correlators within a leg** pair deterministically FIFO: faults added via
  `ArrayDeque.add` (addLast) and consumed via `poll` (pollFirst); controls iterated in
  list order; single-threaded execution (armed guard, `DataIntegrityRuntime.java:273-284`)
  fixes insertion order → occurrence-k ↔ occurrence-k. Degrades to positional *within*
  the bucket, the best available for a same-slot repeat.
- **Reordered fault leg** joins like-for-like: the multimap match is order-independent;
  `correlatorJoin_reorderedFaultLeg_pairsLikeForLike` asserts
  `control.correlationId == fault.correlationId` on the fired pair and `unjoined==0`.
  Correct.
- **Empty legs** (control-only / fault-only / both empty): `canCorrelate` returns false
  on an empty list (`:342-343`) → positional path → `pairs` empty → representative is
  the null-guarded `verdict(...)` → NOT_EVALUABLE "missing record". Fail-safe. (This is
  precisely why F1 is limited to the *both-non-empty* disjoint case.)
- **All ten RunRecord shapes thread the correlator:** happy/absent (`:499-502`),
  not-acked (`:416-420`), error/no-key (`:405-409`), afterWrite-RuntimeException
  (`:507-510`), readback-error (`:523-526`), afterWrite-without-before (uses the
  *current* call's id, `:396-398`), and the orphan-synthetic (uses the *previous*
  write's `orphaned.correlationId`, `:334-339` — correct, since the orphan *is* the
  prior write). `Pending` carries it through all four `beforeWrite` set-sites
  (`:349,:356,:363`). Verified against the diff and full file.
- **Correlator identity across legs.** `testMethodName` and `stepIdx` are
  generation-time constants emitted as string literals into the source
  (`:1994`, `:2210`); the same compiled file runs twice, so the literal is byte-
  identical control-vs-fault. Steps are not runtime-looped or retried (the step loop
  at `:1694` is a *generation-time* loop; each hook line runs ≤once per method
  invocation under `if(!shouldSkip)`), so a slot yields one record per leg in the
  normal case.
- **Flag-off byte-identical.** Both emissions sit inside `if (__diTriple != null)`
  (`:1989` and `:2205`); with no registered triple nothing is emitted, so non-pairing
  output is unchanged. Matches the pin in `DataIntegrityEmissionTest`.
- **No verdict-rule change.** `verdict()` (`:396-439`) is untouched; the representative
  preference `firstFire > firstNoFire > firstAny` (`:275-286`) is unchanged. The only
  behavioural delta is which record pairs feed it — correct **except** the empty-pairs
  fallback (F1).
- **`evaluate()` visibility widened to package-private** for the join unit tests; no
  semantic change.
