# Comparator cold review B — bindings faithfulness (c4b9a08 vs frozen set 15954a8)

**Date:** 2026-07-02. Independent cold reviewer (no shared context), one of three.
Pre-checks: both frozen files byte-identical to their freeze commits; commit order
design→bindings→runner correct; **logs/comparator-reports/ empty — NO comparator run
has happened, so every fix below is still a pre-run amendment, not damage.** Every
binding cite verified verbatim against the frozen clauses. Reconciliation in
REVIEW-COMPARATOR-RECONCILIATION.md.

## Ranked findings

1. **CONFIRMED — adminroute's second frozen read path (`GET
   /api/v1/routeservice/routes/{id}`) dropped silently** (no check, no NOT_CHECKABLE
   record). The kept path is exactly MIST's registry readback_endpoint; the dropped
   one is the comparator's dual-read divergence channel — precisely where it could
   beat MIST. Favors MIST. Calibration-neutral (both paths fail under LOST_WRITE).

2. **CONFIRMED — contacts "with submitted fields" narrowed 5→2** (name, documentType,
   phoneNumber silently dropped; the two kept = exactly MIST's isolation_key).
   Conjunctive binding of all five was mechanically possible (String.valueOf handles
   the int). Favors MIST (comparator blind to persisted-but-mangled fields).

3. **CONFIRMED pattern — every narrowing lands exactly on MIST's own target-triple
   registry** (same read-back endpoint kept, same isolation-key fields kept). The
   single most attackable fact: the unblinded translator reduced the comparator's
   observation surface to MIST's. Also contradicts the prereg §2 operating point
   ("comparator = the full frozen assertion set"). Mitigated for the decisive claim
   by frozen-set-based adjudication (binding gaps = comparator-infra-failure), but
   net pro-MIST on the raw detection axis.

4. **CONFIRMED — design §4's pre-stated calibration outcome is FALSIFIED by
   already-committed live evidence.** The injected faults fabricate SLOPPY acks:
   adminroute fault ack = status:1, msg "create and modify success" (lowercase),
   data:null (G0 smoke line 20; injector code §8); contacts fault ack msg = "create
   contacts success" (§9). Case-sensitive MSG_CONTAINS fails on BOTH fault runs, and
   adminroute's ENVELOPE_DATA(non-null) fails too → the comparator flags the
   calibration faults ON RESPONSE CLAUSES ALONE, falsifying "response-contract
   clauses alone do NOT flag". Not a binding infidelity — an INJECTION-REALISM
   artifact (a real masked failure would return the pristine success ack; the
   fabricated one is distinguishable by exact-msg/data comparison). Knowable from G0
   before the freeze. Must be corrected as a pre-run amendment + disclosed (it also
   softens the "passes every response oracle clean" narrative around the benchmark
   cases if a msg-diff oracle is ever considered).

5. **CONFIRMED tension — design §3 "same verified body … no comparator-specific
   crafting" vs the bindings' own templates.** Partly forced: run3's contacts pairing
   produced ZERO records (no body to reuse), and reusing adminroute's body would
   smuggle in MIST's station-pair isolation adapter (design §1 forbids). The
   ${uuid:id} membership trick is legitimately grounded INSIDE the frozen set
   ("otherwise the submitted id") and arguably comparator-favoring (cleaner key than
   MIST's own station-pair) — "never weaken the comparator" permits it. Amend the
   wording; disclose.

6. **CONFIRMED (design-level) — membership checks verify key presence only, never
   entity field values** (inherited from the design-frozen primitive set, not the
   translator). Favors MIST. Disclose.

7. **PLAUSIBLE future risk — NOT_CHECKABLE self-extended from "UNKNOWN" to
   "not-exercisable".** Justified at calibration (failure branches unreachable under
   valid writes; a status-0 control rejection is correctly routed to infra-failure
   by the control gate). At G3 the failure contracts MUST come back into scope or
   the comparator false-flags legitimate rejections of invalid generated inputs
   (ENVELOPE_STATUS(1) fails on a correct status-0 rejection) — inflating comparator
   FP and flattering MIST.

Also verified: adminroute template satisfies every frozen precondition (stations
live-verified seeded via G0 + run3 baselines; 36-char id ≥ the documented 32
threshold); the fork honoring client-supplied ids has never been live-demonstrated —
if it ignores them, the control gate correctly yields comparator-infra-failure (safe
failure mode, expect it as a possibility). MSG bindings comply with the manifest's
secondary-msg rule (translator did NOT weaken there — a point in the bindings'
favor).

## Net bias statement (print in the study)
Response-contract bindings are faithful and even comparator-favoring at calibration
(finding 4); state-contract bindings are systematically narrower than the frozen
text, and the narrowing coincides with MIST's own registry keys (findings 1–3) —
net pro-MIST on the raw detection axis at G3, partially neutralized by
frozen-set-based adjudication.

## Verdict
Defensible as a calibration-scoped mechanical translation (verbatim citations,
faithful response clauses, justified exclusions, correct freeze ordering) — but not
yet the prereg's "full frozen assertion set", and the two silent state-clause
narrowings mirror MIST's registry too exactly to survive a hostile reviewer
unamended. **Most needed (pre-run amendment, one commit): complete the two state
clauses — add STATE_GET /api/v1/routeservice/routes/{id} (path templated on the
submitted id) for adminroute and extend contacts membership to all five submitted
fields — plus reconcile design §3's matched-input rule and correct §4's falsified
expectation.**
