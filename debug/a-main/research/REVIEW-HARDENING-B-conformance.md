# Hardening-wave cold review B — spec conformance + test adequacy of e5af35b

**Date:** 2026-07-02. Independent cold reviewer (no shared context), one of three.
Verified against the full diff, both test files, recon §3, gate1-result.md,
FILE_INDEX; locally re-ran PairedFaultExecutorTest + TargetTripleRegistryTest (35/35)
and ShippedRegistryDepoisonTest (2/2); verified the registry restore is byte-exact
(`git diff 6c3261f^ e5af35b` on the shipped file = empty). Reconciliation in
REVIEW-HARDENING-RECONCILIATION.md.

## Conformance table (condensed)
- **Item 1 bar v2 + t1–t5: IMPLEMENTED-AS-SPECIFIED** (constants, verdict order,
  guard naming, fractions, caveat, boundary ≤/≥ semantics, summarizeProbe null-safe;
  t1–t5 arithmetic matches the spec exactly). Disclosed-in-comment deviation:
  gateResolvedFraction computed as 1−timeoutGatedFraction — equivalent under the
  runtime's taxonomy but makes the 0.5-floor branch DEAD CODE (cap always trips
  first; t4 can never name "both guards").
- **Item 2a read-back status / never-absent: AS-SPECIFIED.** 2b readback_bound:
  AS-SPECIFIED. **2c paginate/per-entity adapters: DEFERRED — SILENTLY** (defensible
  — G3 surfaces not in-repo; bounded check is the portable default — but the status
  header claims "all six implemented" and neither it nor the commit message discloses
  the deferral).
- **Item 3 R3fix: IMPLEMENTED WITH 2 PARTIALLY-DISCLOSED DEVIATIONS.** (1) join key
  = (tripleName, index), not the spec's (stepKey, occurrence index) — multiple
  stepKeys per triple are a real configuration per the deleted pick() javadoc →
  equal-count cross-stepKey misalignment with unjoinedRecords=0 possible. (2) report
  carries tallies + ONE representative pair, not itemized per-pair verdicts (spec
  pinned both; recon allowed either). Zero-record triples degrade to the old
  missing-record NOT_EVALUABLE (run-#3 contacts behavior preserved).
- **Item 4 R4fix: AS-SPECIFIED** (exactly one re-read; negative path untouched by
  construction; re-read non-2xx → error, consistent with R1fix).
- **Item 5 C-P1-3fix: AS-SPECIFIED** (sink before throw; sink failure swallowed; f2
  marker only when flagged; same report path as the normal writes — which are
  skipped on the F2 path since the throw precedes them).
- **Item 6 R7fix: IMPLEMENTED WITH A DISCLOSED SEAM CHOICE** (raw property only;
  unset/unparseable ⇒ allowed; disclosed in the status header; GET-validation
  correct, shipped registry passes).

## Test adequacy
Pinned: t1–t5 exact; R1 read-back-5xx + bound-reached; R3
persisted-first/lost-later (genuinely discriminates old pick()); C-P1-3
sink-once-then-throw; R4 present-on-post-settle (seam truly requires the re-read);
R7 reject; GET-validation reject; bound parse/default/reject. 14 new tests.
**Unpinned despite spec enumeration:** (1) baseline-5xx → beforeWrite error; (2) R3
count mismatch → NONZERO unjoinedRecords (the flagship anti-masking signal has no
nonzero test); (3) R4 negative: absent-on-post-settle → OBSERVED_COMPLETE_ABSENT
end-to-end; (4) C-P1-3 report FILE exists with the f2 marker + clean-path
no-marker; (5) bound-not-reached → verdicts unchanged. Single-record R3 regression
held via pre-existing tests (adequate, though the spec's enumerated test wasn't
added as such).

## Other checks
- **No-silent-re-scoring: RESPECTED** (gate1-result.md untouched; both bar readings
  already reported; nothing re-scores the committed report).
- **Karpathy: clean** (10 files, all in scope; pick/count fully removed). Nits: the
  report path is constructed twice in MistRunner (drift risk); MistRunner's
  FILE_INDEX row not refreshed.
- **Depoison side-fix: CORRECT** (byte-exact restore; evidence file + index row
  right). Gap: **gate1-result.md §7 pointer now ambiguous** — it lists the shipped
  registry path under "run config committed with this result" but that path now
  holds the pre-run depoisoned state; the doc nowhere references the moved
  learned-registry evidence file.

## Findings (ranked)
1. **MODERATE — four spec-enumerated tests missing** (worst: nonzero
   unjoinedRecords).
2. **MODERATE — 2(c) adapters deferred without disclosure** ("all six implemented"
   overstates).
3. **MINOR — join keyed on triple, not stepKey** (sub-group recordsFor by stepKey or
   disclose).
4. **MINOR — per-pair verdicts rolled to tallies + representative** (itemize or
   disclose).
5. **MINOR — 0.5 floor dead code** (note in spec so the pre-registered floor isn't
   mistaken for an independent check).
6. **MINOR — gate1-result.md §7 stale artifact pointer post-restore.**
7. **NIT — MistRunner FILE_INDEX row; duplicated report-path construction.**

## Verdict
**Substantially conformant — fit to mark the G3 prerequisites met once the four
missing tests are added and the 2(c) deferral is disclosed.** Most important gap:
no test exercises nonzero unjoinedRecords, compounded by the triple-not-stepKey join
keying.
