# Hardening-wave cold review A — soundness of e5af35b

**Date:** 2026-07-02. Independent cold reviewer (no shared context), one of three on
the hardening-wave 验收. Verified against code with file:line evidence; attack
attempts on R1/R4 semantics failed (F6). Reconciliation in
REVIEW-HARDENING-RECONCILIATION.md.

## Findings (most severe first)

**F1 [PLAUSIBLE, MEDIUM] — R3 join alignment is not guaranteed by construction.**
`afterWrite` is NOT emitted in a `finally` (writer emits beforeWrite :1991, call
:2194-2196, afterWrite :2207 inside one per-step try whose catch(Throwable) at :2513
marks-failed-and-continues). A transport-level failure between the hooks (connection
reset, auth filter throw, pod flap) drops that record entirely, while every failure
path INSIDE afterWrite does add a record. A missing record mid-run shifts the
positional join (evaluate :238-247): fault record k+1 joins control record k.
Fabrication corner exists (misaligned healthy sibling passes the systemic guard that
the true sibling would have failed); masking is symmetric. Count mismatch is surfaced
(unjoinedRecords) BUT an equal-count double-drop (one gap in each run at different
positions) is FULLY SILENT (unjoinedRecords==0). Robust today for Gate-1's
all-writes-lost flag; matters for G3 partial-fault per-pair tallies. **Fix cheaply:
emit afterWrite in a finally, or sequence-number records and join on it.**

**F2 [CONFIRMED, LOW-MEDIUM] — R3 spec deviation: tallies, not per-record verdicts.**
Spec §3 promised "per-record pair verdicts + the triple roll-up" and unjoined pairs
"reported"; the report carries firePairs/noFirePairs/notEvaluablePairs/
unjoinedRecords + ONE representative pair's records. An acked+absent fault record in
the unjoined tail is never verdicted anywhere and its evidence is absent from the
report. Thinner than pre-registered.

**F3 [CONFIRMED, LOW] — bar v2's gateResolvedFraction floor is dead code.**
It is DERIVED (1 − timeoutGatedFraction, :430-431), so floor-trip ⟺ fraction>0.5 ⟹
cap-trip (0.3) — it can never trip independently and the reason always names the cap.
If a future gate kind produced acked non-fire unresolved-style records they would
silently count as "resolved" — the exact case the guard was kept for. Present-day
soundness intact (verified all RunRecord sites: acked+error-free ⇒ exactly the three
gates; R1 error records are excluded from acked by fpStats before gate counting).
Registered exposure (not a bug): ≤30% timeout-gated can hide up to 6× the 5% bar
behind a PASS; caveat fires only at observedGated==0.

**F4 [CONFIRMED, LOW] — C-P1-3 sink covers only the run-#2 shape.**
Sink+throw happen only when the try completed normally; fault-leg crash + clear
failure together ⇒ original exception propagates, clearFailures populated but never
consulted — no sink, no f2 report, F2 signal is only a log line (:195). Pre-existing
path, spec only promised the runs-complete case — undisclosed residual. Nit: sink
guard catches RuntimeException only — an Error would replace the F2 exception.
Verified correct: f2 report cannot be overwritten by the normal path (MistRunner
throws before its writes); probe=null truthful; f2ReportPath/reportPath built twice
(desync hazard for future edits, cosmetic today).

**F5 [CONFIRMED, LOW — documented] — R7 guard reads only the raw system property.**
With the property unset, .properties mst.test.parallelism=8 or the "auto" fallback
resolves parallel while the guard stays silent; "auto" (unparseable) also skips it.
No production hole today (the only arming path force-sets "1"; property wins
resolution priority; spec disclosed "unset ⇒ allowed"). Invariant = cooperation of
two files. Registry GET-validation correct; shipped target-triples.yaml passes;
flags-off runs can't be broken.

**F6 [VERIFIED SOUND] — R1/R4 semantics.** Non-2xx read-back/baseline/post-settle
all become error records; verdict() checks error before absence logic (can never
FIRE or launder into NO_FIRE); fpStats counts them invalid before acked++ (bar
denominators clean; they appear in gateHistogram as NOT_APPLICABLE — visibility
only). R4 re-read can flip to OBSERVED_PRESENT only on a genuine key match; exactly
one re-read; polls/elapsed sane; bound voids only absence (presence breaks before
the bound check) for both absence gates.

**F7 [VERIFIED] — regression/additivity.** pick()/count() were private, no other
references; existing tests still meaningful; flags-off additivity holds (all new
paths behind session==null early returns / flag-gated load / gated pairing entry);
barV2 t1–t5 match the spec arithmetic.

## Verdict
**Sound to rely on for G2/G3.** Biggest residual = F1: the positional join's
completeness assumption — fix (finally-emission or sequence-numbered join) before
G3's partial-fault scoring leans on per-pair tallies.
