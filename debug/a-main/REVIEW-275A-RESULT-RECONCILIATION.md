# Wave 2.75-A RESULT — 3-cold-review reconciliation

**Date:** 2026-07-10
**Reviewed artifact:** the executed 2.75-A result (`wave-275a-result.md`, both per-SUT RESULTs, the
4 flipped case JSONs, the 2 freeze §6 rows, the enable-package code + reports).
**Reviewers (independent, cold):** A = oracle-soundness · B = engineering/reproducibility · C =
hostile PC / claim framing.

## Verdicts

| reviewer | verdict | blocking | major | minor |
|---|---|---|---|---|
| A (oracle-soundness) | ACCEPT-WITH-FIXES | 0 | 1 | 3 |
| B (engineering/repro) | ACCEPT-WITH-FIXES | 0 | 2 | 4 |
| C (hostile PC / claim) | ACCEPT-WITH-FIXES | 0 | 2 | 5 |

**Unanimous ACCEPT-WITH-FIXES. No BLOCKING finding; no REJECT.** All three independently confirmed:
the FIRE 5/5 is genuine per-probe (not 1-mislabeled-as-5, not cross-paired — `correlatorUnique=true`,
`unjoined=0`, 5 independent `verdict()` calls); the oracle degrades to NOT_EVALUABLE (never a false
FIRE) on transport failure, silent misconfig, and slow/late reads (the paired-control guard); and the
C-B1 honest framing (TeaStore sole-oracle / OTel presence-concordant, explicitly NOT a discrimination
win) is consistent across all six surfaces. B independently rebuilt: 10/10 tests green, BUILD SUCCESS.

## Findings + disposition

### Convergent (≥2 reviewers)

- **[A-1 MAJOR / C-F1 MAJOR] "First wave MIST ran as a measured oracle" overclaims** — MIST's
  read-back ran earlier in the G3 head-to-heads (`g3.ShippingReadbackHttp`). **FIXED (doc):**
  `wave-275a-result.md` rescoped to "first wave that flips verdict-valued cells for BENCHMARK-CORPUS
  cases + first binding to JSON-collection and async-SQL modalities," with the G3 precedent stated.

- **[A-3 MINOR / C-F5 MINOR] The "independent firewall" wording overstates independence** — the
  direct store read wraps the same query MIST's transport wraps (a re-read, not an orthogonal
  oracle); the real shared-mode-failure guard is the paired CONTROL leg. **FIXED (doc):** all three
  RESULT surfaces reworded to two named guards — (1) SUT-native label independence, (2) the
  control-leg read-mechanism validator (`control.readbackContainedX=true` proves the read path).

- **[A-2 MINOR / B-3 MINOR / C-F5 MINOR] Report JSON serializes only the p0 pair; p1–p4 are
  count-only.** **FIXED (committed evidence):** captured `b4/enable/ground-truth-{teastore,oteldemo}.txt`
  — direct store reads listing EVERY landed control marker (OTel 5, TeaStore 9 across two runs) and
  confirming 0 fault markers. Per-probe auditable from the repo, via a read distinct from MIST's
  transport. Disclosure added to `wave-275a-result.md` (evidence-completeness subsection).

- **[B-5 MINOR / C-F6 MINOR] "~5 s async landing" inconsistent with the recorded `elapsedMs=224`.**
  **FIXED (doc):** `RESULT-oteldemo-2.75a.md` now states the measured landing was sub-second (p0 first
  poll, 224 ms) and the 25 s floor is a conservative over-provision (over-margin risks only a missed
  fire, never a false one). No integrity issue — C confirmed no surface implies MIST beat a comparator.

- **[C-F7 MINOR / B-6] Authoring cost recorded as `minutes:0`; B-F8 end-to-end unit test absent.**
  **FIXED (doc):** `wave-275a-result.md` adds an authoring-cost note (transports write-once + reused;
  marginal per-SUT binding ≈ 1–2 h, disclosed estimate) feeding the C-R4 anti-drift claim string; and
  discloses the live paired runs + ground-truth cross-check substitute for the mocked end-to-end test
  (transport unit tests stay). The frozen per-case `mist_authoring` pin is left unchanged (it is not
  the 2.75-A binding cost).

### Engineering (Reviewer B) — latent harness bugs, happy-path-identical

- **[B-1 MAJOR] Exception-swallowing `finally { return endRun(); }` in both harness `runLeg`s** — a
  `return` in `finally` discards a probe-loop exception (silent partial leg; TeaStore maintenance
  could be left ON silently). **FIXED (code):** refactored to `try{…} finally{ records = endRun(); }
  return records;` in both harnesses — `endRun()` still always runs, but exceptions now PROPAGATE.
  Happy path byte-identical (the successful runs threw nothing), so the committed reports stand.

- **[B-2 MAJOR] OTel `scaleKafka(0)`/`waitKafkaGone` sat OUTSIDE the restore `finally`** — if
  `waitKafkaGone` threw, kafka could be left down. **FIXED (code):** `waitKafkaGone` + the fault leg
  moved inside the try whose `finally` restores kafka; `scaleKafka(0)` immediately precedes it (if it
  throws, kafka was never scaled). Happy path identical.

- **[A-4 / B-4 MINOR] TeaStore harness comment claimed a `readback_bound` guard that isn't set.**
  **FIXED (code + doc):** comment corrected — truncation soundness is carried by the control-present
  guard, not a bound; `/rest/orders` serves newest-first unbounded. Noted in `RESULT-teastore`.

- **[B-6 MINOR] Environment coupling (psql creds, ns, kafka name/label, wsl kubectl).** **DISCLOSED
  (no change):** all are `-D`-overridable except the baked psql/ns/kafka constants; reproducibility is
  tied to the documented kind cluster. A name/label mismatch self-protects (a live broker →
  fault-present → NO_FIRE, never a false FIRE). Left as a documented runbook dependency.

### Claim framing (Reviewer C) — significance, not integrity

- **[C-F1 / C-F2 MAJOR] "So-what": both legs are honestly NOT discrimination wins, and the reserved
  TT discrimination win is itself unrun + synthetic (forked-source worst case).** **ACCEPTED AS A
  STANDING SCOPE CONSTRAINT (no silent fix):** 2.75-A is a **read-back applicability/breadth** datum
  (two durable modalities bound + agreeing with ground truth), NOT a discrimination or recall
  headline. The result doc already says this; reinforced in the honest-framing section. Recorded
  consequence for the paper: **MIST's discrimination claim remains PRE-REGISTERED and unmeasured by
  any MIST run**; its exemplar (TT fabricated-ack) is synthetic on forked source; a real (ideally
  natural) traced MIST *discrimination* run is still owed (2.5/E2). This wave materially de-risks that
  run (the read-back path is now proven end-to-end) but does not substitute for it. Carried to memory.

- **[C-F3 / C-F4 MINOR] Construct validity (read-back is the definitional detector) + N=5×2×1 is
  anecdotal.** **DISCLOSED (rebutted):** faults are natural unmodified-source masking (more natural
  than the reserved TT exemplar); the NOT_EVALUABLE guards mean a non-persisting control aborts rather
  than fabricates FIRE; no rate/recall claim is made from these N (existence/determinism datum only).
  Standing constraint: never pool these self-concordant cells into a headline recall.

## Net

All findings dispositioned: 2 code fixes (B-1, B-2) + 1 comment fix (A-4/B-4) applied and re-tested
(10/10 green, BUILD SUCCESS); 5 doc-accuracy fixes applied; 2 committed ground-truth evidence files
added; the remainder disclosed as standing scope constraints. No finding touched the FIRE 5/5 verdict
or the C-B1 honest framing. The result is **claim-ready as a read-back-applicability datum** under the
honest reframing — explicitly not a discrimination/recall headline (that MIST run is still owed).
