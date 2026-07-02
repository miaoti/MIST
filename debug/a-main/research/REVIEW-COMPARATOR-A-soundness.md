# Comparator cold review A — soundness of 666c461 (+ frozen bindings c4b9a08)

**Date:** 2026-07-02. Independent cold reviewer (no shared context), one of three on
the comparator 验收. Reconciliation in REVIEW-COMPARATOR-RECONCILIATION.md.

## Findings (most severe first)

**F1 [CONFIRMED, CRITICAL] — comparator requests are UNAUTHENTICATED; live
calibration is dead on arrival.** RestAssuredSutClient calls the 2-arg
`MstAuthHandler.applyAuth(req, path)`, which only stamps a header if a token already
exists; **nothing on the comparator path calls `ensureReady()`** (the only method
that logs in; `ensureConfigLoaded()` even resets cachedToken=null). The only
production `ensureReady()` callers are writer-emitted test code and the SUT
preflight — and the comparator branch returns BEFORE the preflight. TT runs
auth.mode=per_jvm; both calibration endpoints are admin-role → unauthenticated →
401/403 → HTTP_STATUS 200 FAILs in CONTROL → every endpoint =
comparator-infra-failure, zero usable verdicts. Fails safe (no bias) but guarantees
a wasted live run. **Fix: call MstAuthHandler.ensureReady() in runComparatorMode
(hard-fail on false).** Root cause of the test gap: RestAssuredSutClient is wholly
untested (FakeSut bypasses auth).

**F2 [CONFIRMED semantics / PLAUSIBLE trigger] — fault-leg state-GET transport
failure is scored as a DETECTION (comparator-favoring asymmetry).** Non-2xx
read-back → clause FAIL; the control gate neutralizes this ONLY in the control leg.
In the fault leg a transient 503 on the GET → flagged → verdict "flag" with zero bug
evidence — while MIST maps the identical event to a non-detection error category
(poll-through + decisive-read, H2). Recall inflation for the baseline. **Fix
(pre-stated before any counted run): a fault-leg FAIL whose ONLY failing clause is a
non-2xx STATE_GET is reclassified comparator-infra-failure (or retried once),
mirroring MIST's read-back-error category.** Untested today.

**F3 [CONFIRMED] — after a clear failure the loop CONTINUES; later endpoints'
verdicts are recorded on a possibly-still-faulted SUT** (only a global
f2ClearFailure marks the report; no per-endpoint taint). The pairing executor
throws immediately after its clear phase. Bounded at calibration (2 endpoints,
disjoint deployments); at G3 it contaminates every post-failure verdict. **Fix:
break on the first clear failure; mark remaining endpoints not-run/infra.**
Continue-behavior unpinned by tests.

**F4 [CONFIRMED behavior, low likelihood at calibration] — mid-loop runtime
exceptions crash run() BEFORE writeReport, losing all evidence** (binding-error
throw from containsSubmittedFields, tripleFor, inject rollout timeout, client
connect exception). Violates the design's own "binding errors are
comparator-infra-failure" rule by severity class. Cannot fire with the frozen
calibration bindings (fields verified present in templates); at G3 a crash at
endpoint 78 destroys everything. Ultra-edge: fault-leg exception + finally-clear
failure → clearFailures recorded but never persisted. **Fix: per-endpoint try/catch
→ comparator-infra-failure + continue; always write the report.**

**F5 [ANALYZED — the priority fairness question] — zero-wait STATE_GET vs MIST's
10s quiescence: FAIR for THIS calibration, needs pre-stated stratification for G3.**
(a) Representative, not strawman: Filibuster-style tests assert immediately; the
frozen prose carries no wait; the no-quiescence choice was frozen in the design
BEFORE implementation. (b) The control gate neutralizes the FP direction: a
benign-slow SUT fails the CONTROL leg → infra-failure, never a verdict — it
empirically certifies read-your-writes per endpoint per run. (c) Verdict-equivalent
for LOST_WRITE (absent at t=0 ≡ absent at t=10s) → the pre-stated calibration
outcome is a fair comparison. (d) RESIDUAL G3 THREAT (comparator-favoring):
delay-type faults make the comparator flag at t=0 while MIST correctly observes
arrival — inflates baseline recall; F2 biases the same direction. **Pre-state before
G3: an additive second state-read at MIST's 10s budget reported alongside the
immediate outcome, or delay-vs-loss adjudication from MIST's records. ALSO: the
comparator can never show a control false alarm by construction (control failures
vanish into infra-failure) — the G3 write-up must report the infra-failure rate as
part of the assertion oracle's cost or its FP story is understated.**

**F6 [PLAUSIBLE, disclosure-level] — design §3 promises not kept exactly:**
(a) "matched inputs" — implementation uses comparator-crafted frozen templates (the
${uuid:id} trick is SOUND — licensed verbatim by the frozen contract and frozen
before any run — but the paper cannot claim literally matched inputs; soften/amend).
(b) Report omits the clause `cite` per check and the injected fault manifest per
endpoint (adjudication traceability at G3 needs both). (c) adminroute's state clause
cites two read paths; only the list GET is bound (under-flag direction —
anti-comparator, conservative; disclose).

**F7 [minor/future]:** substitution ignores the field name — two tokens for the SAME
field would get different values (latent for future templates); envelopeDataIsNull
treats garbage 200 bodies as data-null (endpoint verdicts protected by co-bound
ENVELOPE_STATUS; per-clause stats slightly polluted); String.valueOf equality is
character-identical to MIST's containsKey (no cross-oracle asymmetry); byte-additivity
CONFIRMED; injector parity with the pairing confirmed; skipping the preflight
acceptable (the control gate is stronger) but skipping auth is F1. Test gaps: F2
semantics unpinned (and mis-commented in ContractEvaluator), F3 continue unpinned,
F4 untested, RestAssuredSutClient untested, no test pins the bindings→registry join.

## Verdict
Evaluator + orchestration sound and honestly gated for the two-endpoint LOST_WRITE
calibration — zero-wait evaluation is a faithful, control-gate-protected reading,
not a strawman — **but the committed runner cannot execute the calibration at all
(F1), and the biggest threat to the head-to-head is the comparator-favoring
transport/delay asymmetry (F2/F5): fix F1 and pre-state the stratification before
any counted run.**
