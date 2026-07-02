# Hardening-wave cold-review reconciliation (e5af35b; 3 independent reviewers, 2026-07-02)

Reviews: [A — soundness](REVIEW-HARDENING-A-soundness.md),
[B — spec conformance + tests](REVIEW-HARDENING-B-conformance.md),
[C — integration/pipeline](REVIEW-HARDENING-C-integration.md). No shared context.
**Verdicts: A "sound to rely on for G2/G3"; B "substantially conformant — fit to mark
the G3 prerequisites met once the missing tests are added and the deferral is
disclosed"; C "safe to ship for G2/G3".** No blocking defect; every new failure mode
fails loud (the right polarity). This doc maps the consensus to the fix wave
(e5af35b+1) and records dispositions.

## Consensus findings → dispositions

| # | Finding (reviewers) | Disposition |
|---|---|---|
| H1 | **Join alignment under missing records** (A-F1 MEDIUM, B-3, C-F1 — C proves the trigger live: run #3 71v70). stepKey sub-grouping is vacuous (one stepKey per triple); the silent case is equal-count double-drop. | **FIXED (partial) + DISCLOSED:** (i) orphaned-`pending` detection — a `beforeWrite` that finds an unconsumed pending appends a synthetic ERROR record for the orphaned write, preserving alignment for within-method transport failures (A-F1's exact case; trailing orphans only truncate, which cannot misalign); (ii) steps that die BEFORE their hooked write remain count-visible only → **per-pair tallies are DESCRIPTIVE-ONLY until the writer-side method/ordinal correlator lands (added to the G3-prerequisite list)**. |
| H2 | **R1fix abort-on-first-non-2xx too attrition-prone** (C-F2 top integration risk; A-F6 confirmed the soundness, not the robustness). | **FIXED (pre-registered amendment, C's formulation):** non-2xx polls are tolerated WITHOUT scanning (no evidence from error bodies — soundness kept) and polling continues; absence is concluded only from a 2xx decisive read; the record errors only when the decisive read (timeout-hit poll or the R4 post-settle re-read) is non-2xx. Strictly sounder than the old loop, strictly more robust than v1 of the fix. Baseline non-2xx still aborts (freshness needs a real baseline). |
| H3 | **Missing spec-enumerated tests** (B-1 MODERATE): baseline-5xx; NONZERO unjoinedRecords; R4-negative (absent on the post-settle re-read); f2 report file marker + clean-path no-marker. | **FIXED:** all added, plus a transient-non-2xx-recovery test pinning H2 and an orphan-detection test pinning H1(i). |
| H4 | **2(c) adapters deferred silently** (B-2 MODERATE). | **DISCLOSED** in the spec status header: paginate-to-exhaustion / per-entity adapters await the G3 SUT surfaces (TeaStore windowing / petclinic); the bounded check is the shipped portable default. |
| H5 | **gateResolvedFraction floor is dead code** (A-F3, B-5, C-F7 all confirm the identity). | **DISCLOSED** in the spec (the floor is derived — subsumed by the 0.3 cap under the current three-gate taxonomy; kept as schema for future gate kinds, NOT an independent check). No code change (removing it would un-register a floor; keeping it costs nothing). |
| H6 | **f2 report lacks the affected flags** (C-F6a; spec said "with the affected flags"). | **FIXED:** the clear-failure sink now receives the failed flags and the f2 report carries `f2FailedFlags`. |
| H7 | **Per-pair verdicts rolled to tallies** (A-F2, B-4). | **DISCLOSED** (spec status header): tallies + representative pair are the v1 report shape; itemized per-record verdicts ride with the H1 correlator at G3. |
| H8 | **Sink coverage limited to the runs-complete shape** (A-F4): crash + clear-failure still loses evidence; sink guard catches RuntimeException only. | **DISCLOSED** as residual (the spec only promised the run-#2 shape; the combined-failure path keeps the pre-wave behavior). Accepted: catching Error to protect F2 would be worse practice than the nit it cures. |
| H9 | **Registry write-back re-dirties the shipped resource every run** (C-F3, pre-existing). | **DISCLOSED** in the spec status header as the G2/G3 runbook rule: point `smart.input.fetch.registry.path` at a `target/` copy for gate runs. |
| H10 | **gate1-result §7 stale artifact pointer** (B-6); MistRunner FILE_INDEX row + duplicated report path (B-7, C-F6). | **FIXED:** §7 now points at the depoisoned shipped state + the prep/ learned-evidence snapshot; report path computed once in MistRunner; FILE_INDEX row refreshed. |
| — | Verified sound by multiple reviewers: R1/R4 core semantics (A-F6), no-silent-re-scoring (B-3 check, C-F7), byte-additivity (A-F7, C-F8), depoison restore byte-exact (B-5 check, C-F3), report-shape compatibility (C-F7), C-P1-3 no-double-write (B item 5, C-F6). | No action. |

## Post-fix status
With the fix wave applied and suites green, the **G3 prerequisites (recon §3 items
1, 2, 4, 5) are MET**, with two disclosed riders carried onto the G3-prerequisite
list: (a) the writer-side method/ordinal correlator before per-pair tallies feed any
claim (H1); (b) the runbook registry-path rule (H9).
