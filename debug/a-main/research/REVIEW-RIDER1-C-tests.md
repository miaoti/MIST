# REVIEW — Rider 1 (writer-side correlator join, `e640748`) — Reviewer C (test adequacy + claim integrity)

**Scope:** commit `e640748` only. Cold review, verified against code — no prior conclusions assumed.
**Lens:** do the tests PROVE the fix, and is the "per-pair tallies graduate from DESCRIPTIVE-ONLY and
may feed G3 detection claims" statement EARNED as written?

**Verdict: ACCEPT-WITH-FIXES.** The fix is real and one direction (misalignment → false FIRE) is
demonstrated by a genuine A/B. But the graduation statement is **not fully earned as written**: the
tests exercise the correlator's value only in the regime the OLD positional join already flagged, the
recall/detection direction is asserted-not-tested, and the `canCorrelate` gate the whole claim rides on
(uniqueness + orphan/error records carrying a non-null correlator) is untested, undisclosed, and
unguarded.

**"Graduate to feed G3 claims" earned as written? NO** — earned for the precision direction and the
byte-compat fallback; the detection (recall) direction and the correlator-uniqueness precondition are
asserted, not proven. Keep it hedged (see MAJOR-1/2 + MINOR-7).

---

## Findings (most-severe first)

### MAJOR-1 — The case where the correlator is INDISPENSABLE (equal-count divergent skip) is untested; the one tested win was already flagged by positional
`PairedFaultExecutorTest.correlatorJoin_middleSkip_avoidsMisalignedFalseFire`
(mist-cli/.../fault/PairedFaultExecutorTest.java:623-643) uses control 2 records / fault 1 record.
Under the OLD positional join that count delta ALREADY produced `unjoinedRecords = |2-1| = 1`
(PairedFaultExecutor.java:337) — i.e. the run was *already* flagged as asymmetric/DESCRIPTIVE-ONLY.
So the only tested case where the correlator beats positional is one positional *already* surfaced as
suspect. The case the correlator is actually needed for — **equal record counts on both legs but a
DIFFERENT write skipped on each side** — is untested. There positional computes `unjoinedRecords =
|n-n| = 0` (no warning at all) and silently mispairs, which is exactly the residual
[REVIEW-HARDENING-A-soundness](REVIEW-HARDENING-A-soundness.md) F1 named ("equal-count double-drop
silent") and the residual this rider is meant to close. All three new tests miss it: middleSkip is 2-vs-1
(unequal), `correlatorJoin_reorderedFaultLeg_pairsLikeForLike` (line 665-686) has the SAME correlator
*set* {m#0,m#1} on both legs (just reordered) → `unjoined=0` legitimately.

This also swallows the **recall/false-NO_FIRE-recovery direction** the doc explicitly claims
(g3-rider1-correlator.md:44-45 "removes ... false NO_FIREs alike") and that a *detection* claim depends
on: no test shows positional MASKING a real fault that the correlator RECOVERS to FIRE.

*Suggested test* (closes both): control `[corrRecord("control","m#0",t,true,true)`, `corrRecord("control","m#1",t,true,true)]`;
fault `[corrRecord("fault","m#0",t,true,false)`  /* lost → FIRE */`, corrRecord("fault","m#2",t,true,true)` /* different write, persisted */`]`.
Assert correlator: `firePairs==1`, `unjoinedRecords==2`, only the m#0 pair verdicts. Then a null-correlator
companion asserting positional gives `unjoinedRecords==0` and mispairs (crosses m#1-control with
m#2-fault) — demonstrating the silent regime positional cannot see. A second variant with the m#0 fault
leg *present* and the m#1 control leg *absent* gives the recall-recovery direction (positional
NOT_EVALUABLE/0-fire vs correlator 1-fire).

### MAJOR-2 — Duplicate/colliding correlators silently degrade to positional-within-group while still labeled "correlator-aligned" (claim-eligible); uniqueness is untested, undisclosed, unguarded
`canCorrelate` (PairedFaultExecutor.java:341-351) checks only *non-null*, never *uniqueness*.
`joinRecords` buckets faults into a `Deque` per correlationId and `poll()`s FIFO
(PairedFaultExecutor.java:316-327) — so if two records share `<method>#<stepIdx>` the join pairs them
**positionally within that key**, re-introducing the exact shift the rider claims to remove, with no
fallback and no warning, while the run still counts as "correlator-aligned" and thus (per the doc)
claim-eligible. Collision is realistic: the correlator is `<testMethodName>#<stepIdx>`
(MultiServiceRESTAssuredWriter.java:1989, :2210); nothing here proves a method executes exactly once per
run — a retry, `@RepeatedTest`, or a data-driven/parameterized replay of the same method yields duplicate
keys. The doc's soundness bullet "No new operating assumption" (g3-rider1-correlator.md:48-50) omits this
**per-run correlator-uniqueness** precondition. No test.

*Suggested:* a duplicate-correlator test pinning the actual behavior, PLUS either (a) a uniqueness guard
(`canCorrelate` returns false, or logs+flags, on any duplicate → honest positional fallback) or (b) an
explicit disclosure that the writer emits each hooked method once per run and a report field asserting it
(see MINOR-7).

### MEDIUM-3 — `afterWrite`'s correlationId argument is dead on every path except one untested record; the flow test cannot detect a broken `afterWrite` arg
On every pending-based shape the record's correlator comes from `pending.correlationId` (set by
`beforeWrite`): happy (DataIntegrityRuntime.java:409-ish/459), not-acked (:420), no-key (:409),
read-back-error, afterWrite-exception. The `afterWrite` `correlationId` *argument* is read on exactly ONE
path — the "afterWrite without matching beforeWrite" record (DataIntegrityRuntime.java:398) — and that
path has **no test**. `DataIntegrityRuntimeTest.g3Correlator_flowsFromHooksOntoRecord`
(DataIntegrityRuntimeTest.java:~261-272) passes the *same* literal `"testCreateContact_0#3"` to BOTH
hooks, so it can only prove `beforeWrite → record`; a broken/removed `afterWrite` arg would not fail it.

*Suggested:* (a) a mismatched-literal test (`beforeWrite(...,"x#1",...)`, `afterWrite(...,"y#1",...)`)
asserting the happy record keeps `"x#1"` — proves which hook is load-bearing and documents it; (b) a test
driving the afterWrite-without-before record and asserting its `correlationId` equals the `afterWrite`
arg.

### MEDIUM-4 — The orphan-synthetic correlator is untested and load-bearing for the H1×rider interaction
The orphan synthetic record carries `orphaned.correlationId` (DataIntegrityRuntime.java:339). If that were
ever null, `canCorrelate` (checks ALL records) collapses the WHOLE triple to positional — silently
defeating the rider on precisely the asymmetric-skip / transport-death runs it targets (the orphan path is
the H1 mechanism for those runs). The existing orphan integration test
`h1fix_orphanedBeforeWrite_getsSyntheticErrorRecord` (PairedFaultExecutorTest.java:713-733) uses the
**legacy 2-arg/4-arg hooks** (:717, :720, :727) → the orphan record's correlationId is null and the test
asserts nothing about it. So no test proves a correlator-suite orphan record carries a non-null correlator.

*Suggested:* re-drive that orphan test through the 3-arg/5-arg hooks and assert
`records.get(0).correlationId` equals the first write's correlator (and that the leg stays on the
correlator path).

### MEDIUM-5 — `corrRecord` is unfaithful to the runtime's error/not-acked shapes, and NO integration test drives the correlator path end-to-end
`corrRecord` (PairedFaultExecutorTest.java:611-621) always sets `error=null` and
`gate ∈ {OBSERVED_PRESENT, OBSERVED_COMPLETE_ABSENT}`. The real runtime feeds *error-bearing* records
(baseline-error :349, not-acked :420 with `gate=NOT_APPLICABLE`, read-back-error, orphan,
afterWrite-without-before) through the SAME `joinRecords`. No join test uses an error-bearing correlated
record, so nothing verifies (a) those records carry a non-null correlator (feeds MAJOR-2/MEDIUM-4's
`canCorrelate` fragility) or (b) that they join by correlator. Compounding this: every `execute()`-level
integration test uses the **legacy hooks** — `fakeGeneratedRun` (PairedFaultExecutorTest.java:162-174) and
`alwaysPersist` (:200-208) call `beforeWrite(stepKey, body)` / `afterWrite(stepKey, status, body, trace)`
— so the correlator path (real hooks → real records → correlator join) is **never exercised end-to-end**,
not even against the fake SUT. Notably `r3fix_countMismatch_surfacesNonzeroUnjoined` (:589-607), the one
real-runtime asymmetric-skip test, runs on the POSITIONAL fallback.

*Suggested:* add one fake-SUT integration test that drives the 3-arg/5-arg hooks across two writes (one
persisted, one lost, with a skip on the fault leg) and asserts the correlator join tallies — reuses the
existing harness and naturally exercises error/orphan records flowing into the join.

### MINOR-6 — `correlatorJoin_reorderedFaultLeg`'s discriminating assertion is tautological, and the case yields identical tallies under positional
The assertion `pair.control.correlationId == pair.fault.correlationId`
(PairedFaultExecutorTest.java:684-685) cannot fail on the correlator path: `joinRecords` buckets by
`correlationId`, so any correlator-joined pair has equal ids *by construction*. And because all controls in
that test are identical (acked+present), positional and correlator produce the SAME tallies (1 FIRE /
1 NO_FIRE) — only the internal (invisible) crossing differs. The test is therefore decorative for every
*reported* metric. *Suggested:* add a null-correlator positional companion whose representative crosses
(`control.correlationId != fault.correlationId`), or construct controls that differ so the reorder changes
a reported tally.

### MINOR-7 — No `joinMode` in the report, so a G3 run cannot PROVE it graduated; the fallback test pins a known-wrong FIRE
`positionalFallback_nullCorrelators_reproducesMisalignedFire` (:645-663) deliberately asserts a false FIRE
as expected — legitimate as the "before" half of the A/B and a backward-compat pin, but it locks in that
legacy suites stay UNSOUND on skips. The pairing report emits per-record `correlationId`
(PairedFaultExecutor.java:672) but no explicit `joinMode: correlator|positional` / `correlatorUnique`
field. Since "may feed G3 claims" is *conditional* on being on the correlator path with unique correlators,
the report should state it so an auditor can verify the graduation per run rather than infer it from
null-scanning. *Suggested:* add `joinMode` + `correlatorUnique` to the report and make the doc's graduation
sentence conditional on them.

### MINOR-8 — `unjoinedRecords` conflates both-side leftovers
`joinRecords` sums control-only and fault-only leftovers into one int (PairedFaultExecutor.java:325, :329).
The doc treats `unjoined>0` as a per-run data-quality signal (g3-rider1-correlator.md:70-71) but the report
cannot tell which leg skipped. Minor audit-fidelity note; consider `unjoinedControl` / `unjoinedFault`.

---

## Claim-integrity check (doc "Why it is sound", g3-rider1-correlator.md:42-50)
- **"verdict-rule-neutral"** — TRUE in code: `joinRecords` only chooses which records pair; `verdict()`
  (PairedFaultExecutor.java:396-439) is untouched and reads the same fields. Accurate.
- **"Fail-safe: an unmatched write is unjoinedRecords, never a silent drop; FIRE iff ≥1 correctly-aligned
  pair fires"** — TRUE: leftovers counted (:325,:329), fire rule over `join.pairs` only (:253-292).
  Caveat: an unjoined lost fault write is not verdicted — correct (no control sibling → no differential
  evidence), so not a masked detection.
- **"No new operating assumption"** — OVERSTATED: silently assumes per-run correlator UNIQUENESS
  (MAJOR-2). Should be disclosed.
- **Prereg citation** ("§0's 'R3fix is a G3 prerequisite'", g3-rider1-correlator.md:69) — DEFENSIBLE:
  §0's header names R1, but §0 body (g3-sut2-triples-prereg.md:44-51) explicitly frames R3fix as ALSO a G3
  prerequisite and cites Rider 1 `e640748`. Not a finding.

## What the tests DO establish (CORRECT-list)
- **middleSkip + positionalFallback are a genuine controlled A/B**: identical record shapes, differing
  ONLY by correlator presence (null vs "m#0"/"m#1"), and the correlator flips a positional false FIRE →
  NOT_EVALUABLE with `unjoined=1`. Assertion set for that direction is adequate (firePairs,
  pureDifferential, unjoinedRecords, control/faultRecordCount). Soundly proves the *precision* direction
  and the byte-compat fallback contract.
- `evaluate()` made package-visible and driven with hand-built records — clean unit seam for the join.
- `g3Correlator_flowsFromHooksOntoRecord` proves `beforeWrite → Pending → RunRecord.correlationId` (happy
  path); `_legacyHooksLeaveItNull` proves the legacy overloads yield null (the `canCorrelate` gate that
  selects positional fallback). Both correct as far as they go.
- `DataIntegrityEmissionTest` (DataIntegrityEmissionTest.java:96-104) pins BOTH hooks emit
  `"<method>#<idx>"` with the numeric suffix tied to the step index, and flag-off byte-identity (hook-free
  cases). Because the correlator is a **generation-time constant** (compiled once, run twice), this string
  pin is *sufficient* evidence for cross-leg identity — no runtime path can make the two legs' correlators
  differ. So the brief's question #3 ("cross-leg identity tested or asserted?") is adequately covered BY
  CONSTRUCTION; an integration "two-runs-match" test would assert a compile-time constant equals itself.
  The real residual is *uniqueness* (MAJOR-2), not cross-leg identity.
- The correlator genuinely CAN catch the equal-count divergent skip that positional cannot — that is a
  real strength; it is just not yet TESTED (MAJOR-1).
