# Rider-1 cold-review reconciliation (e640748; 3 reviewers, 2026-07-02)

Reviews (no shared context): [A — join soundness](REVIEW-RIDER1-A-soundness.md),
[B — additivity & fallback](REVIEW-RIDER1-B-additivity.md),
[C — tests & claim integrity](REVIEW-RIDER1-C-tests.md). All three **ACCEPT-WITH-FIXES**.
Convergence: A and B **independently** found the same representative-fallback bug
(A-F1 HIGH / B-MED); A and C **independently** found the correlator-uniqueness gap
(A-F2 / C-MAJOR-2). Fix wave applied same day; full suites green **35 + 331 + 104**
(+8 tests). The commit-e640748 tests + these fixes together earn the (now hedged)
graduation claim.

## Consensus → dispositions

| # | Finding (reviewer) | Disposition |
|---|---|---|
| R1 | **Representative fallback cross-pairs → silent false FIRE** (A-F1 HIGH, B-MED): when the correlator aligns ZERO pairs but both legs are non-empty (opposite asymmetric skips), `evaluate` fell to `verdict(controls.get(0), faults.get(0))` — a crossed pair that reads FIRE with `firePairs==0`, re-admitting the exact misalignment the rider removes. Positionally unreachable with both legs non-empty; the correlator makes it real. | **FIXED:** empty-pairs-but-both-legs-non-empty → explicit `NOT_EVALUABLE` (never a cross-pair). Test `correlatorJoin_bothLegsNonEmptyDisjoint_isNotEvaluableNotCrossFire` pins it (and shows the legacy positional path DOES cross-fire the same shapes). |
| R2 | **Correlator not unique** (A-F2 MED: `<method>#<stepIdx>` omits the class, method names reset per generated class → cross-class collision; C-MAJOR-2: `canCorrelate` checks non-null only, a method run >1× duplicates silently and stays "correlator-aligned" + claim-eligible). | **FIXED two ways:** (a) the writer now emits `<className>.<method>#<stepIdx>` — globally unique across a run's classes; (b) `joinRecords` computes `correlatorUnique` (distinct within each leg) and `joinMode`, both surfaced on `PairResult` + the report JSON. **Claims are gated on `joinMode==correlator ∧ correlatorUnique==true`** — a duplicate no longer counts as aligned. Tests: emission regex now pins the class-qualified form; `correlatorJoin_duplicateCorrelator_isFlaggedNotUnique`. |
| R3 | **The INDISPENSABLE case is untested** (C-MAJOR-1): equal record counts but a different write skipped each side — positional `unjoined=0` gives NO warning yet SILENTLY mispairs. The one committed win (`middleSkip`, 2-vs-1) was already catchable by the count delta. | **FIXED:** `correlatorJoin_equalCountDivergentSkip_positionalWouldSilentlyMispair` — control {a,b} vs fault {a,c}, counts 2-vs-2; correlator → NO_FIRE + `unjoined=2`, the null-correlator companion → silent FIRE with `unjoined=0`. This is the test that proves the correlator is load-bearing, not decorative. |
| R4 | **afterWrite's correlator arg looked dead** (C-MEDIUM-3): the flow test passed the same literal to both hooks, so a broken afterWrite couldn't be caught. | **FIXED:** matched writes correctly take the correlator from `pending` (beforeWrite's) — both hooks emit the same literal in generated code, so this is by design; the afterWrite arg is live ONLY on the no-matching-before path, now pinned by `g3Correlator_afterWriteWithoutBefore_usesAfterWriteCorrelator`. |
| R5 | **Orphan correlator load-bearing but untested** (C-MEDIUM-4): a null there reverts the triple to positional. | **FIXED:** `g3Correlator_orphanRecordCarriesPriorWritesCorrelator` drives the double-beforeWrite orphan and asserts the synthetic record carries the PRIOR write's correlator. |
| R6 | **No execute()-level correlator test** (C-MEDIUM-5): every correlator test called `evaluate()`/hooks directly; the full pipeline used legacy hooks only. | **FIXED:** `execute_correlatorHooks_driveCorrelatorJoinEndToEnd` runs the whole clear→control→inject→fault pipeline through the 3-arg/5-arg hooks and asserts `joinMode==correlator`. |
| R7 | **Silent revert-to-positional on any null correlator** (A-F3 LOW). | **FIXED (surfaced):** `joinMode` is now in the report — a mixed/legacy run is visible, not silent. |
| R8 | **evaluate() javadoc still described positional as THE behavior** (A-LOW, B-LOW). | **FIXED:** javadoc rewritten (correlator-first, positional fallback, the NOT_EVALUABLE-on-zero-aligned rule). |
| R9 | **Pre-existing unflushed dangling pending** (A-F4 LOW): fail-safe here, not introduced by this rider. | **DISCLOSED:** out of rider scope; the orphan-detection (H1) already emits a synthetic record on the NEXT beforeWrite, and endRun's tail is unchanged — no regression. |
| R10 | **"Graduate to feed G3 detection claims" NOT earned as written** (C): precision + fallback proven, recall + uniqueness asserted. | **RESOLVED via hedge + evidence gate** (see below). Uniqueness now tested + surfaced; the claim is reworded to what the code earns. |

## The graduation claim — corrected wording (C's headline)

The misalignment error is **directionally a false POSITIVE**: an acknowledged-but-absent
fault record is "sticky" — wherever a positional shift lands it, it tends to fire against
any present-acked control, so mis-join inflates FIRE tallies (precision loss), it does
**not** manufacture a false NO_FIRE (recall loss). Hence:

> **Graduation (earned):** the per-pair FIRE tallies are now **misalignment-proof** — a
> reported FIRE is a genuine like-for-like fire — **gated on `joinMode==correlator ∧
> correlatorUnique==true` in the report**. This is exactly the integrity a G3 detection
> claim needs (a reported detection is real, not a pairing artifact). The correlator's
> contribution is **precision**; it does not fabricate detections, so no recall-recovery
> claim is made. A G3 run that shows `positional` or `correlatorUnique==false` for a
> triple does NOT feed that triple's tallies into the claims.

Reviewer C's soundness cross-check stands: verdict-neutral ✓, fail-safe ✓; the earlier
"no new operating assumption" was overstated (it omitted uniqueness) — the rider doc is
updated to name the uniqueness precondition and its evidence gate.

## Net status
Rider 1 = BUILT + 3-cold-reviewed + FIX WAVE APPLIED (suites green). The per-pair
tallies feed G3 claims under the stated evidence gate. Fix-wave commit carries this
reconciliation + the updated rider doc.
