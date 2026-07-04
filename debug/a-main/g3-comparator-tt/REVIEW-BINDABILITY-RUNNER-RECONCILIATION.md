# BindabilityRunner 3-cold-review — reconciliation + strategic decision

Runner under review: `mist-cli/.../comparator/BindabilityRunner.java` (commit `5b09a15`), built to
turn the accepted Rider-2 survey (analytical 69/80) into an EMPIRICAL fraction on live TT.

## Verdicts
- **Reviewer A: ACCEPT-WITH-FIXES** (BLOCKING POST-only + 3 MAJOR).
- **Reviewer B: REJECT** (BLOCKING POST-only + 2 MAJOR).
- **Reviewer C: ACCEPT-WITH-FIXES** (POST-only MAJOR + 2 MAJOR).

Net: **REJECT for the full-empirical-69/80 claim** (unanimous on the decisive defects). The runner is
correct + green *within a POST-create presence-audit scope*, but cannot underpin the full claim.

## Decisive findings (all 3 converge unless noted)
1. **POST-only (A+B BLOCKING / C MAJOR).** `ComparatorRunner.path()` throws on non-POST; `SutClient`
   has only post/get. The census is 30 POST / 23 PUT / 25 DELETE / 2 GET / 1 PATCH — **>60% non-POST**.
   PUT-echo and DELETE-absence binds (most of the 69) become INFRA_FAILURE → excluded from the
   denominator → the fraction silently covers only the POST-create subset while looking complete.
2. **Absence unsound in control-only single-write (A+C MAJOR).** (a) A fresh-keyed DELETE targets an
   entity that was never created → key trivially absent → vacuous PASS → false BINDS. (b) Sharper (A):
   an `absent`-expect STATE_GET on a single-object per-entity read → `extractItems` empty →
   `containsSubmittedFields`=false → PASS → **false BINDS on exactly the OBJECT-ABSENCE NC class
   (#12/#23/#52)** — refuting the residue the run exists to confirm. Absence needs a real
   create→delete→check-absent stimulus.
3. **Circularity / overclaim (C MAJOR, A concurs).** `NOT_CHECKABLE` is a no-op the runner replays →
   the 11/80 residue is **re-encoded, never measured**. The runner can only AUDIT the BINDS side and
   can only flip BINDS→UNBINDABLE (never NC→BINDS). Honest claim = "an execution audit of the BINDS
   side," NOT "converts the analytical 69/80 into an empirical one."
4. **Control-leg PASS is only a PARTIAL proxy (A+B+C MAJOR).** Sound only for PRESENCE checks with a
   per-run FRESH key; unsound for absence and non-fresh keys (a static key matches a seeded row →
   false BINDS). The runner enforces neither; delegates entirely to authoring discipline.
5. **Multi-observable ordering bug (A+B MAJOR; C judged it sound).** structural-FAIL (line 161)
   precedes the pass branches → an endpoint that binds via one observable but has a co-located
   structural-FAIL secondary is mislabeled UNBINDABLE. Fix: `stateGetPass>0` ⇒ BINDS/BINDS_PARTIAL;
   reserve UNBINDABLE for `stateGetPass==0`. (A+B majority + the "≥1 observable ⇒ BINDS" definition
   settle it in favor of the fix.)
6. **Denominator inflation (A+B+C MAJOR/MINOR).** Excluding INFRA_FAILURE can push the fraction ABOVE
   the analytical (e.g. 66/70 = 94% > 86.25%) over a quietly-smaller, verb-biased denominator. Needs
   a NON_EXECUTABLE verdict distinct from INFRA_FAILURE, census reconciliation, and a validity gate.
7. MINOR: single-shot flakiness (replicate UNBINDABLE/INFRA before counting); test gaps (mixed
   pass+structural-fail; non-POST fate; retry-within-cap; response-check-absent guard).

## What the reviews establish about the deliverable
- The FULL inject-based executable breadth = infeasible (needs ~69 fork flags).
- The control-only executable breadth, done SOUNDLY and FULLY, needs a **multi-verb harness with
  per-disposition setup flows** (create→delete→check-absent; create→update echo) + fresh-key/absence
  guards + census reconciliation — a substantial build — and even then its value is only a
  **BINDS-side execution audit** (the 11/80 NC residue stays analytical; the runner cannot measure it).
- The analytical Rider-2 survey (69/80) is ALREADY reviewer-accepted as the external-validity answer.

**Conclusion: the executable breadth is LOW-ROI relative to its cost** — a large multi-verb build for a
modest BINDS-side audit of an already-accepted fraction. This is a genuine strategic fork the review
created; recorded for a direction decision (see the memory + the options below). The runner's real bugs
(findings 2/5/6) are worth fixing IF any executable-audit scope is pursued; otherwise the runner stays
as a POST-create-audit tool, not wired into MistRunner.

## Options (for the direction decision)
1. **Re-scope to a small honest POST-create presence audit** — fix the runner (absence-vacuity guard,
   ordering, fresh-key guard, NON_EXECUTABLE verdict, census reconciliation), author ~30 POST-create
   bindings, run live → "of the create endpoints, X/30 presence-bind on live TT (with any BINDS→UNBINDABLE
   flips surfaced)." Modest value, moderate effort.
2. **Full multi-verb + setup-flow harness** — covers the whole census as a BINDS-side audit. Large
   effort; NC residue still analytical.
3. **Defer executable breadth (accept low-ROI); pivot to the wild-hunt** — hunt a NOVEL data-integrity
   defect on Sock Shop (SS-C shipping enqueue-swallow) with MIST. Higher novelty, speculative,
   trace-gated. The accepted analytical survey stands as the Rider-2 answer.
4. **Defer; consolidate** the three accepted results (head-to-head, survey, SUT-2 FP) into paper-ready form.
