# Cold Review B — commit 3a94b88 (supplied isolation + value-delta): wiring, state machine, additivity

Reviewer: independent cold reviewer B (no prior context). Angle: wiring, Pending/ThreadLocal state
machine, and regression risk to the pre-existing MEMBERSHIP path that feeds published Gate-1/G2
results.

Evidence base: `git show 3a94b88` diff; full current `DataIntegrityRuntime.java`,
`TargetTripleRegistry.java`, both test classes; pre-commit image (`3a94b88~1`) of
`DataIntegrityRuntime.java` extracted and compared block-by-block; verdict consumer
(`PairedFaultExecutor.verdict`, benign-stats loop) read; shipped
`mist-cli/src/main/resources/My-Example/trainticket/target-triples.yaml` read; both test classes
executed: **48/48 green** (`DataIntegrityRuntimeTest` 26, `TargetTripleRegistryTest` 22).

---

## 1. ADDITIVITY — is the legacy membership path bit-for-bit unaffected? **CONFIRMED YES**

**drainOrphan vs the original inline block.** Extracted both and compared line-by-line: identical
guard (`get()` null-check), identical `pending.remove()` ordering (remove before log/record),
identical log message and argument order, identical 17-arg `RunRecord` (runLabel, triple.name,
writeEndpoint-as-stepKey, isolationKey, -1, null, false, baselineContainedX, false, NOT_APPLICABLE,
0, 0, baselineBody, null, "hook orphaned: beforeWrite ran but afterWrite never fired", null,
correlationId). Call site in `beforeWrite` is the same position (after the `triple == null` early
return, before the try). The only new code between drain and the try is the SUPPLIED wiring guard,
which is unreachable for FRESH_STRINGS/STATION_PAIR triples. Byte-identical behavior. CONFIRMED.

**presentX indirection.** The three replaced call sites in `afterWrite` (not-acked immediate read,
poll loop, post-settle re-read) all pass through
`presentX(triple, body, pending)`, which for `readbackMode == MEMBERSHIP` returns exactly
`containsKey(body, pending.isolationKey)` — same arguments, no other observable effect
(`pending.baselineBody` is not touched on the MEMBERSHIP branch). Every triple parsed from an
existing registry gets `MEMBERSHIP` (`ReadbackMode.parse(null) → MEMBERSHIP`), and the `Triple`
constructor is package-private with `parse` as its only caller, so `readbackMode` is never null in
production (and a hypothetical null still falls to the membership branch). CONFIRMED identical.

**afterWrite otherwise untouched.** Diff + pre-image comparison confirm the ack predicate, the
error-pending branch, the poll/timeout/settle/bound logic, `recordReadbackError`, and all record
shapes are unchanged. `freshen`, `containsKey`, `extractItems`, `endRun`, `beginRun`, Session
validation: unchanged.

**Registry parsing of existing files.** The shipped `target-triples.yaml` (2 triples, no
`readback_mode`/`value_probe`) parses to the identical `Triple` values plus
`readbackMode=MEMBERSHIP, valueProbe=null` — pinned by the still-green
`shippedTrainTicketRegistry_loadsBothGate1Triples`. No previously-valid document can now be
rejected: the new cross-validations only trigger on the new keys/strategy
(`SUPPLIED`/`value-delta`/`value_probe` did not exist before). One cosmetic change: `isolation_key`
and `isolation_strategy` parsing was hoisted above `write_endpoint`/`dependency` extraction, so for
a file with MULTIPLE errors the error *precedence* can differ (e.g. bad `isolation_key` now reported
before missing `write_endpoint`). Affects only invalid files' error text, never a parse outcome.
Nit, not a regression.

**No writer/codegen coupling.** `beforeWriteSupplied` is referenced nowhere outside
DataIntegrityRuntime, its test, a TargetTripleRegistry javadoc, and FILE_INDEX.md — the depth
scenario is hand-authored, so no generated-test surface changed. Public API is extend-only (new
static hook, new enum + nested class, two new public final Triple fields).

**Conclusion:** with no supplied/value-delta triples registered, every code path is behaviorally
identical to the pre-commit build. The Gate-1/G2 membership machinery is unaffected.

## 2. State machine with the new hook interleavings — **CONFIRMED sound; no new drop/double**

- `beforeWriteSupplied → afterWrite` (normal): pending's triple is the same registry instance
  (`byStepKey`), so the identity check passes; one record; pending consumed. Covered by tests.
- `beforeWriteSupplied → beforeWriteSupplied`: second call's `drainOrphan` emits the synthetic
  orphan record (with the FIRST write's supplied key, baseline, correlator) then sets the new
  pending. One record per write, join stays aligned. Correct — but NOT covered by a test (§5).
- `beforeWrite → beforeWriteSupplied` (freshening pending orphaned): same drain path, correct;
  NOT covered by a test (§5).
- **Wiring-error pendings (`error != null`)**: all three guard pendings (wrong-hook-on-supplied,
  supplied-hook-on-freshening, unusable key) plus the baseline-failure and exception pendings take
  afterWrite's pre-existing `pending.error != null || isolationKey.isEmpty()` branch: record
  emitted with the computed `acked`, `readbackContainedX=false`, gate `NOT_APPLICABLE`, **zero
  read-back GETs**, error string and correlator carried. Exactly the pre-existing error-pending
  shape; `PairedFaultExecutor.verdict` maps them to NOT_EVALUABLE ("control/fault run error"),
  never NO_FIRE evidence. CONFIRMED equivalent.
- No interleaving introduced by this commit drops or double-records: `drainOrphan` removes before
  recording; `afterWrite` records only what it pops. Two pre-existing quirks are *unchanged* (out
  of scope, noted for completeness): (a) `afterWrite` on a *different* registered step silently
  discards a live pending while emitting only the "afterWrite without matching beforeWrite" record
  for the other triple — the discarded write surfaces later as a missing-correlator NOT_EVALUABLE;
  (b) a pending left at the very end of a run is not drained by `endRun` (missing record at join).
  Both exist verbatim in the pre-image.

## 3. beforeWriteSupplied guards and passthrough — **CONFIRMED, one nit**

Guard order is strategy → key validation → baseline read, as specified. Messages: "beforeWriteSupplied
on a non-supplied triple (STRATEGY)", "unusable supplied key f=v", "baseline read-back HTTP n" —
first two distinct and greppable; the third is **byte-identical to the freshening hook's baseline
message** (both log line and record error), so grep cannot tell which hook failed the baseline;
disambiguation requires the triple's strategy. Nit (F3 below). A guard-tripped pending consumes
correctly on afterWrite (§2; three of the guards are test-pinned). The key-validation guard runs
before the baseline GET, so an unusable key wastes no HTTP call and leaves `baselineBody=null` —
consistent with the wrong-hook pendings.

**Body passthrough:** all seven exit paths of `beforeWriteSupplied` (inactive session, unmatched
step, strategy guard, key guard, baseline non-2xx, success, RuntimeException) `return requestBody`
and never dereference it — null-safe, byte-untouched. The success path of the CONTROL flow is
asserted (`assertNull` on a null body); guard paths return-but-don't-assert in tests (§5).

## 4. Registry validation completeness — what still parses

| Config | Parses? | Matters? |
| --- | --- | --- |
| supplied + station-pair-style key name (e.g. `[startStation]`) | yes | No — supplied key names are SUT-semantic free-form; only STATION_PAIR key names are pinned (Session ctor). |
| supplied + `fault_flag` | yes | No — legitimate combination (a supplied triple may still have a SUT flag; cancel-refund simply omits it, Toxiproxy fault). |
| value-delta + `readback_bound` | yes | No — the bound guard is shared code and stays coherent for value-delta (guards truncation-driven false absence on the global /account list; arguably *desirable* for the depth triple). |
| supplied + empty `isolation_key` | **no** | Rejected by the pre-existing non-empty-list rule before the size==1 check. Good. |
| duplicate `value_probe` fields in YAML | yes (last wins) | SnakeYAML-1.x default, parser-wide and pre-existing; not specific to this commit. Nit. |
| **`match_field == value_field`** | **yes** | **Yes (F2)** — a degenerate constant probe: for a buyer already present at baseline, the probed value can never differ (it IS the match value), so a genuinely landed refund reads "no movement". Failure surfaces only downstream as a pair-level NOT_EVALUABLE ("control write never appeared"), i.e. silent at parse time — against this file's loud-at-load philosophy. One-line reject. |
| value-delta + non-supplied strategy (fresh-strings / station-pair) | yes | Marginal — semantics are coherent-ish (fresh key ⇒ baseline probe null ⇒ appearance = movement) but unexercised and untested; document-or-reject at leisure. |
| supplied + MEMBERSHIP (no probe) | yes | Intentional and correct: `beforeWriteSupplied` computes `baselineHasX` via `containsKey` for MEMBERSHIP, preserving the isolation-violation guard. |

Enforced-and-correct: value-delta ⇔ value_probe (both directions), match_field ∈ isolation_key
(which with the supplied-size-1 rule transitively forces match_field == the supplied key field),
unknown value_probe keys rejected, probe fields non-empty strings.

## 5. Test adequacy — interleavings/branches NOT covered (worth adding, not added here)

1. `beforeWriteSupplied → beforeWriteSupplied` orphan drain (assert the orphan record carries the
   FIRST supplied key + correlator) — the supplied analogue of
   `g3Correlator_orphanRecordCarriesPriorWritesCorrelator`.
2. `beforeWrite` (freshening pending) orphaned by a following `beforeWriteSupplied` — the
   cross-hook drain named in this commit's own design.
3. The `keyValue == null` / empty-string arm of the unusable-key guard (only wrong `keyField` is
   tested).
4. `beforeWriteSupplied` with **no active session** and with an **unmatched step** — the
   "hooks are passthrough no-ops outside pairing runs" contract is pinned for the legacy hooks
   (`inactive_hooksArePassthroughNoops`) but not for the new 5-arg hook.
5. Non-null body passthrough asserted on the guard paths (`suppliedHook_onFresheningTriple…`
   ignores the return value; only the beforeWrite-on-supplied direction asserts `"{}"`).
6. VALUE_DELTA reaching `OBSERVED_COMPLETE_ABSENT` (trace-complete + post-settle re-read via
   `presentX`) — the exact gate the depth fault leg is expected to publish; only TIMEOUT_ABSENT is
   covered.
7. VALUE_DELTA + `readbackBound` interaction at timeout (bound counts raw items, not probed rows).
8. VALUE_DELTA on the not-acked immediate-read path, including a non-2xx immediate read — pins the
   F1 semantics whichever way it is resolved.
9. `beforeWriteSupplied` RuntimeException path (HTTP seam throws; only the 503 branch is tested).
10. supplied + MEMBERSHIP end-to-end: supplied key already present at baseline ⇒
    `baselineContainedX=true` flows to the record (isolation-violation NOT_EVALUABLE input).

## 6. Style/idiom consistency — **CONFIRMED consistent**

`[runLabel][triple.name]` log prefixes; never-throw hooks (every failure becomes an error pending,
body passed through); loud registry `IllegalArgumentException` with origin + allowed-set text
matching the file's existing messages; inline FQN usage (`java.math.BigDecimal`) matches the file's
existing precedent (`java.util.concurrent.ConcurrentHashMap`, `java.util.regex.Pattern`);
`presentX` private, `extractProbeValue`/`valueDiffers` package-private test seams matching
`containsKey`/`freshen`; javadoc carries the review-ID convention. Orphan record text still says
"beforeWrite ran" even when the orphaned pending came from `beforeWriteSupplied` — cosmetic only.

---

## Findings

- **F1 (CONFIRMED, LOW — no verdict impact).** On the *not-acked* path, `afterWrite` feeds the
  immediate read-back body to `presentX` without a 2xx check (pre-existing). Under MEMBERSHIP an
  error body scans as `false` (harmless); under VALUE_DELTA an unparseable/non-2xx body extracts a
  null probe, and against a non-null baseline `valueDiffers(non-null, null) == true` — the record
  gets `readbackContainedX=true` derived from a non-evidence body, contradicting the file's own
  R1fix-v2 principle ("error bodies are never evidence"). Verified harmless downstream: `verdict()`
  checks `!control.acked` → NOT_EVALUABLE and `!fault.acked` → NO_FIRE *before* consulting
  `readbackContainedX`, and the benign-stats loop skips `!acked` records — so this is forensic-field
  pollution only. Fix: on the not-acked path, evaluate presence only when `now.status/100 == 2`
  (or force `false` for VALUE_DELTA on non-2xx).
- **F2 (CONFIRMED, LOW-MED — validation gap).** `value_probe.match_field == value_field` parses;
  degenerate constant probe blinds the oracle to any refund landing on an already-present row and
  only surfaces later as a pair NOT_EVALUABLE. One-line load-time reject fits the file's loud
  philosophy.
- **F3 (CONFIRMED, NIT).** The supplied hook's baseline-failure message is byte-identical to the
  freshening hook's ("baseline read-back HTTP n" / same warn text) — the only guard whose origin
  hook is not greppable. Suffixing "(supplied)" or naming the hook would restore one-string
  disambiguation.
- **F4 (PLAUSIBLE, INFO).** value-delta + non-supplied strategy is an unvalidated, unexercised
  combination — accept-and-document or reject at load.
- **F5 (PLAUSIBLE, INFO).** `extractProbeValue` is first-match: correctness silently assumes ≤1 row
  per key value in the read-back collection (true for TT `/account`, one aggregate row per userId).
  Worth one javadoc/registry-comment line so a future multi-row surface is not probed blind.
- Test gaps: §5 items 1–10 (items 1–5 exercise the new state-machine wiring; 6–8 exercise the
  gates the depth run will actually publish).

## Verdict: **ACCEPT-WITH-FIXES**

The additivity requirement — the review's headline concern — is fully satisfied and CONFIRMED by
pre-image comparison and the green shipped-registry pins: with no supplied/value-delta triples,
behavior is bit-for-bit the pre-commit membership path. The new state machine introduces no drop or
double-record. Fix list (all small, none blocking the published-results guarantee):

1. F1 — gate not-acked presence evaluation on a 2xx immediate read (or force false on non-2xx under
   VALUE_DELTA).
2. F2 — reject `match_field == value_field` at parse.
3. F3 — disambiguate the supplied hook's baseline-failure message.
4. Add the §5 tests, at minimum items 1, 2, 4, and 6.
