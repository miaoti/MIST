# COLD REVIEW — Rider 1 (writer-side correlator join) — lens B: byte-additivity / backward-compat / generated-code integrity

**Commit:** `e640748` "feat(pairing): G3 rider 1 — writer-side correlator join (H1 / comparator-C13)"
**Reviewer:** independent cold reviewer B (no shared context)
**Verdict:** **ACCEPT-WITH-FIXES**

The three headline claims — (a) flag-off output byte-identical, (b) legacy/null-correlator suites hit the exact prior positional join byte-for-byte, (c) the generated Java compiles and binds the correct new overloads — **all verify TRUE**. One MEDIUM correctness edge and one LOW doc-staleness issue are the only findings; neither breaks the three claims, but the MEDIUM contradicts the commit's own stated fail-safe invariant and is untested, so it should be fixed before the per-pair tallies feed G3 claims (which is the stated purpose of this commit).

---

## Findings (most-severe-first)

### [MEDIUM] Representative-verdict fallback re-injects a *mismatched positional* verdict into the headline `pureDifferential` when the correlator join aligns ZERO pairs but both legs are non-empty

**File:** `mist-cli/src/main/java/io/mist/cli/fault/PairedFaultExecutor.java:275-286` (the `representative` selection in `evaluate`), reached via the correlator branch of `joinRecords` (`:307-350`).

**Mechanism.** After `joinRecords`, the representative is chosen as
`firstFire ?? firstNoFire ?? firstAny`, and only when `join.pairs` is **empty**:
```java
} else {
    representative = verdict(t.name,
            controls.isEmpty() ? null : controls.get(0),
            faults.isEmpty()   ? null : faults.get(0));   // :283-285
}
```
In the **old (pre-commit) positional** code, both-legs-non-empty always produced
`joined = min(size) >= 1` pair, so `firstAny` was always set and this fallback was
**only reachable when a leg was empty**. The new **correlator** branch introduces a
way for both-legs-non-empty to yield **zero** joined pairs — when the two legs'
correlator sets are disjoint. That newly makes the fallback compute
`verdict(controls.get(0), faults.get(0))` on records whose correlators **cannot
match** (if they matched they'd have been joined) — i.e. exactly the positional
cross-pairing this rider set out to eliminate. The verdict leaks into the headline.

**Concrete trigger.** Two *opposite* single-sided skips on the same triple (a triple
has one write endpoint hit at multiple step indices):
- control leg records `[m#0]` — write-0 present & acked (`readbackContainedX=true`), write-1 skipped (guard/transport death in control);
- fault leg records `[m#1]` — write-1 acked but absent (`readbackContainedX=false`), write-0 skipped in fault.

Both legs carry correlators ⇒ `canCorrelate` true on both ⇒ correlator branch.
`m#0 != m#1` ⇒ `join.pairs` empty, `unjoined = 2`. Fallback ⇒
`verdict(m#0-control-present, m#1-fault-absent)` ⇒ **FIRE**. Result:
`pureDifferential = FIRE` while `firePairs = 0, noFirePairs = 0, unjoinedRecords = 2`.

**Why it matters.** `pureDifferential` **is** the per-triple headline verdict a human
reviewer reads: it is the first token of `summarize()` (`:709`) and the top-level
`pureDifferential` field per pair in the report JSON (`:647`). So the console/report
headline can announce FIRE for a triple where *no correlator-aligned pair fired* —
directly contradicting the commit message's invariant ("the triple still FIREs iff
≥1 *correctly-aligned* pair fires… an unmatched write is unjoinedRecords, never a
silent drop") and re-introducing the misalignment false-FIRE that would **inflate
MIST recall** — the exact failure mode the rider claims to remove. The correct
tallies (`firePairs=0`, `unjoinedRecords=2`) *are* emitted alongside, so a diligent
reviewer who cross-checks is not fooled; that is what keeps this MEDIUM not HIGH.

**Reachability caveat (honest).** In a real fault run the control leg is the clean
baseline and normally runs a *superset* of the fault leg's writes (fault suppresses,
never adds), so the sets overlap and this fallback is not hit. The trigger needs the
control leg to *also* skip a write that the fault leg ran — two independent, opposite
flakes. Rare, but (i) the whole rider exists because run #3 already showed asymmetric
skips (71 vs 70), (ii) it is **untested** — none of the 5 new tests exercise
both-non-empty-with-zero-overlap; `correlatorJoin_middleSkip` has one matching pair
(`m#1`), so `firstAny` is set and the fallback is dodged.

**Fix (small).** When `join.pairs` is empty but at least one leg is non-empty via the
correlator path, do **not** synthesize a positional verdict; emit an explicit
NOT_EVALUABLE so the headline mirrors `firePairs==0`:
```java
} else if (!controls.isEmpty() && !faults.isEmpty()) {
    representative = new PairResult(t.name, controls.get(0), faults.get(0),
            PairVerdict.NOT_EVALUABLE,
            "no correlator-aligned pairs (" + join.unjoined
                    + " unjoined) — fully asymmetric execution across legs");
} else {
    representative = verdict(t.name,
            controls.isEmpty() ? null : controls.get(0),
            faults.isEmpty()   ? null : faults.get(0));
}
```
Add a unit test with `controls=[corr m#0 present]`, `faults=[corr m#1 absent]`
asserting `firePairs==0 && pureDifferential==NOT_EVALUABLE && unjoinedRecords==2`.

---

### [LOW] Stale javadoc on `evaluate()` still describes the positional join as THE behavior

**File:** `mist-cli/src/main/java/io/mist/cli/fault/PairedFaultExecutor.java:228-237`.

The method javadoc (unchanged by this commit) still reads *"The i-th control record
is joined with the i-th fault record and each joined pair gets its own verdict."*
That is now only the **fallback**; the primary path aligns by correlator via
`joinRecords`. A maintainer reading `evaluate`'s contract is misled about the actual
join semantics introduced here. Fix: update the javadoc to state correlator-primary,
positional-fallback, and move/reference the `joinRecords` javadoc (which is correct).

---

## CORRECT list (claims independently verified)

- **(a) Flag-off byte-identical — VERIFIED.** Both new emissions
  (`MultiServiceRESTAssuredWriter.java:1994` beforeWrite, `:2210` afterWrite) are
  strictly inside the pre-existing `if (__diTriple != null)` guard (`:1993`, `:2205`).
  The 3-line rider comment added above `:1994` is writer-source (`//`), not wrapped in
  `pw.println`, so it is **not** emitted into generated code. The three hook-free
  emission tests still assert `!src.contains("DataIntegrityRuntime")`
  (`DataIntegrityEmissionTest.java:123, 131, 139`) and remain valid — nothing emits
  `DataIntegrityRuntime` outside the triple/body-present path.

- **(b) Legacy/null path byte-for-byte — VERIFIED.** `canCorrelate` returns false
  (empty list, or any null correlator) ⇒ `joinRecords` runs the identical
  `Math.min(...)` loop and `Math.abs(size delta)` unjoined count as the old inline
  code; `evaluate` iterates `join.pairs` calling `verdict(p[0],p[1])` in the same
  order and sets `unjoinedRecords = join.unjoined`. Representative selection is
  unchanged on this path (both-non-empty ⇒ ≥1 pair ⇒ `firstAny` set; the MEDIUM
  fallback is unreachable positionally). The checked-in legacy generated suite
  `mist-cli/src/test/java/trainticket_gate1_pairing/TrainTicketGate1Pairing_1782976771915_phaseB/Flow_Scenario_107.java`
  (compiled as part of the test build) calls the **2-arg** `beforeWrite` and **4-arg**
  `afterWrite` throughout — its green compile is live evidence the legacy overloads
  survive and bind. Tests `positionalFallback_nullCorrelators_reproducesMisalignedFire`
  pins the prior behavior explicitly.

- **(c) Generated Java compiles / correct overload — VERIFIED.** Emitted 3-arg
  `beforeWrite("POST /x", "method#3", requestBody3)` and 5-arg
  `afterWrite("POST /x", "method#3", actualStatusCode3, …asString(), __mstTraceId3)`
  bind unambiguously to the new overloads (`DataIntegrityRuntime.java:315`, `:383`):
  arg-counts are disjoint from the legacy 2-arg/4-arg overloads, and the legacy 4-arg
  `afterWrite` has `int` as its 2nd param vs the new `String correlationId`, so even
  same-arity confusion is excluded. No caller (generated/test/production) is ambiguous.
  Adding a longer-arity constructor/overload never makes existing shorter calls
  ambiguous.

- **Literal escaping / `testMethodName` safety — VERIFIED.** `escape()`
  (`:2950`) neutralizes `\ " \n \r \t` — the Java-string-literal breakers — matching
  the adjacent `escape(__diStepKey)`. Independently, `testMethodName` is emitted
  verbatim as a Java **method name** (`:1449 public void <name>()`), so any value that
  could break a string literal would already have broken generation pre-commit; it is
  constrained to a valid identifier. A `#` inside the name is harmless (it lands inside
  the string literal and the `[^"]*#\1` regex still matches; the join uses the whole
  string as a stable key across legs).

- **RunRecord 15→16→17 constructor chain — VERIFIED.** `correlationId` is a new
  `final` field appended **last** (`:119`, assigned last at `:164`). The 15-arg ctor
  (`:121`, ends `error`) delegates with `readbackHttpStatus=null`; the 16-arg ctor
  (`:131`, ends `readbackHttpStatus`) delegates with `correlationId=null`; the 17-arg
  ctor (`:142`) assigns every prior field to its own slot then `correlationId`. No
  positional shift — `readbackHttpStatus` still binds to its own arg. Both legacy
  ctors set `correlationId=null` ⇒ positional fallback preserved.

- **`Pending` threading onto every record shape — VERIFIED.** All 7
  `new RunRecord(...)` sites carry a correlator: orphan-synthetic uses
  `orphaned.correlationId` (the *prior* skipped write's key — correct, keeps the join
  aligned per H1) (`:334`); afterWrite-without-before uses the incoming `correlationId`
  param (`:398`); baseline-non-2xx and beforeWrite-catch stamp the incoming param onto
  `Pending` (`:346`, `:364`); the remaining shapes use `pending.correlationId`
  (`:405,416,499,507,523`). No site was missed, so a real new-writer run has **every**
  record non-null ⇒ `canCorrelate` true ⇒ correlator join active (a single missed site
  would silently demote the whole triple to positional).

- **(5) Report JSON back-compat — VERIFIED.** `correlationId` is an additive key
  emitted with `JSONObject.NULL` for null (`PairedFaultExecutor.java:672`). No script
  or schema consumer parses the pairing report (grep for the report field names across
  `*.py|*.sh|*.js` = none; the only `debug/a-main` hits are prose docs and the
  `gate1-run3-report.json` output artifact, not readers). `org.json` ignores unknown
  keys, and the gate verdict is human/reviewer-read, so the new key breaks nothing.

- **(6) No unintended change — VERIFIED.** The diff is confined to the feature:
  writer (2 emission lines + a non-emitted comment), `DataIntegrityRuntime` (correlator
  threading + 2 new overloads), `PairedFaultExecutor` (`joinRecords`/`Join`/`canCorrelate`
  + report key + `evaluate` made package-visible for tests), the 3 imports
  `ArrayDeque/Deque/LinkedHashMap` (all used by `joinRecords`), 2 test files, and 2
  docs. No formatting churn or stray edits. The `private`→package visibility change on
  `evaluate` is intentional (unit-test seam) and does not widen the public API.

---

## Bottom line
Byte-additivity (a), legacy byte-for-byte fallback (b), and generated-code/overload
integrity (c) are all sound. The one substantive issue is a headline/tally
inconsistency in the empty-correlator-join fallback (MEDIUM) that contradicts the
commit's own fail-safe invariant and is untested — cheap to fix and worth fixing
before these tallies feed G3 claims. Recommend **ACCEPT-WITH-FIXES**.
