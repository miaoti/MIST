# Plan — E2 read-back capability + provenance-closure run (rev 2)

**Status:** rev 2 (after 3-cold-review: 1 REJECT + 2 ACCEPT-WITH-FIXES on rev 1; reconciliation =
`REVIEW-E2-PLAN-RECONCILIATION.md`). Nothing executes until a re-review returns unanimous ACCEPT.
**What this run IS (reframed per the review — C-F1/C-F2/C-F5):** it CLOSES a provenance liability and
produces a bounded *specification-locality* capability datum. It is **NOT** the paper's discrimination
headline. The headline — a NATURAL fault where an in-practice trace oracle misses and read-back catches
— is the S3 wild-hunt, rater-gated and deferred, and this run does not substitute for it (it de-risks it).

## §0 The two things this run actually delivers (no more)

1. **Provenance closure.** The flagship case `TT-cancel-refund-fabricatedack-001` currently records
   `mist_readback_oracle=flag` as a MANUAL-CURL PRE-REGISTRATION, never run by MIST's harness on the
   traced deploy. A benchmark paper whose flagship discrimination cell is un-run is a liability. This
   run flips that cell to **harness-run-backed**, on the same traced deploy where the trace columns are
   measured, in one coherent artifact.
2. **A specification-locality capability datum (the reframed, defensible claim).** On ONE traced
   fabricated-ack run, report a THREE-config trace comparator plus MIST's read-back:
   - naive error-span oracle → **MISS** (no error spans),
   - service-map-granularity presence (cross-service HTTP span exists) → **MISS**,
   - **DB-span-granularity presence** (the inside-payment INSERT client span) → **CATCH** (T6: fault
     trace has 0 such spans vs control's 2),
   - MIST durable-value read-back → **CATCH** with no pre-specified per-write assertion.
   The claim is therefore NOT "read-back beats trace" (false — a DB-granularity trace assertion catches
   it). It is: **read-back catches this acked-lost write out-of-the-box, whereas a trace oracle catches
   it only if the author pre-specified an assertion on the exact durable write that was skipped** — a
   specification-locality / authoring-burden argument (C-F1). That is the interesting, honest point.

Neither deliverable is a prevalence, recall, or "trace can never catch it" claim.

## §1 Honest framing (carried verbatim into the RESULT; C-F2/C-F3/C-F4/C-F5)

- **Synthetic worst-case.** The fault is a fork flag (`drawbackFaultMode=fabricatedack`) *defined* as
  "return the success envelope, skip the persist" = trace-clean + durable-absent by construction. The
  capability datum is an EXISTENCE/bounding result, close to measuring a definition; it is a component
  / motivating example, never the headline (C-F4).
- **The corpus does NOT rescue it as a natural discrimination (C-F3).** Wave-2.75-A shows read-back
  BINDS natural acked-lost faults on other SUTs, but those are SOLE-oracle (TeaStore, trace-uninstrumented)
  or PRESENCE-CONCORDANT (OTel, the trace presence oracle already flags) — ZERO of them is a
  "trace-runs-and-misses-but-read-back-catches" instance. So the discrimination-over-trace rests solely
  on the synthetic fork; the corpus provides read-back *applicability breadth*, not natural discrimination.
  The RESULT must say this explicitly.
- **Granularity qualifier in the headline everywhere (A-M2).** Never write "beats trace" unqualified;
  write "naive + service-map-granularity presence miss; DB-granularity presence catches only with a
  pre-specified assertion; read-back catches out-of-the-box."
- **The owed headline stays owed.** The S3 natural discriminator is the real discrimination headline;
  it is rater-gated/deferred and named here so this run is not mis-sold as closing it.

## §2 Design (code-grounded; the reuse + the DISCLOSED changes it requires)

**Reused unchanged:** `io.mist.cli.g3.CancelRefundHeadToHead` (constructed stratum) + `TrainTicketStimulus`
for the MIST value-delta read-back on the buyer `/inside_payment/account` balance, with its reviewed
guards `requirePreFundedBaselines` (same POSITIVE baseline both legs) and `requireClaimEligible`
(correlator join, unique). Its built-in comparator is the response-assertion contract (NOT trace) —
retained as a secondary comparator, not the point.

**Disclosed changes required (rev-1 wrongly said "no harness change"):**
- **C1 — trace-id capture (resolves the A-B1/B-2 BLOCKING coherence gap AND the gate).** The TT gateway
  is header-transparent and the harness passes `afterWrite(..., traceId=null)`, so nothing ties a leg's
  cancel to a trace, and `trace_score.py` requires exactly-one-trace-per-file (it selects by
  service+kind, not by buyer). Fix: the stimulus injects a CLIENT-GENERATED W3C `traceparent` on the
  cancel, returns its trace-id in `Resp`, and the harness (a) feeds it to `afterWrite` (earning a
  trace-gated read-back instead of timeout-gated — the real, not aspirational, stronger gate) and (b)
  uses it to select EXACTLY that cancel's trace for scoring. This is a real behavioral change to the g3
  harness + stimulus — disclosed, visibility-plus-gate, unit-tested, and it does not alter the verdict
  predicate.
- **C2 — DB-span-granularity presence selector in `trace_score.py`.** Add a third, FROZEN selector: the
  inside-payment DB-client INSERT span (db.system + the drawback/Money write). Report all three trace
  configs (C-F1). Committed BEFORE the run (pre-registered selectors, no post-hoc tuning), scored off
  the SAME exported traces.
- **C3 — N loop shape (A-m6/B-3/C-F6).** N=5 via **5 fresh JVM invocations, constructed-only** (each its
  own `beginRun/endRun`, so the per-triple constant correlator stays unique-per-leg; 5 pairs in one
  session would fail `allUnique`). Pin the exact `-D` matrix: `g3.strata=constructed`, both
  `g3.triples.natural`+`g3.triples.constructed` (run() requires both even for constructed-only),
  `g3.base.url`, `g3.contract.path`.

## §3 Execution phases (each gated; re-instrumentation is a PRIMARY phase, B-1)

- **P0 — Re-instrument the traced deploy (the largest work item, NOT a risk bullet).** The
  traced-capture wave TORE DOWN instrumentation and TT was scaled to 0 DE-INSTRUMENTED; the snapshot
  restores replica counts + nacos doubleWrite + image tags ONLY. So P0 re-runs the traced-wave
  instrumentation for the cancel path (order / cancel / inside-payment): the `scale-0 → single kubectl
  patch (javaagent hostPath volume+mount + OTEL_* env) → scale-1` dance with its pre-wave gates
  (deploy-spec snapshot, RAM headroom, agent-OOM watch). Verify: istio + `jaeger-collector.istio-system:4318`
  up (they live OUTSIDE the TT ns and are not in the TT snapshot); a canary cancel produces a trace in
  Jaeger v2 with the injected traceparent's id resolvable. GATE: no canary trace ⇒ STOP, do not measure.
- **P1 — Revive + smoke.** Snapshot replica revival + nacos doubleWrite + demo-DoD 7/7 GREEN + N≥4
  consecutive probes (ribbon stale-pod guard). Preflight: `/account` GET returns the buyer balance JSON;
  `faultmode/fabricatedack|none` toggle 200 (the harness sends the reader token, not an admin bearer —
  the endpoint is effectively unguarded; align wording with the code); a manual clean cancel refunds
  (+price), a manual fabricatedack cancel does not (balance flat).
- **P2 — Run constructed stratum 5× fresh JVMs.** Each: MIST paired FIRE (control +refund present, fault
  flat absent); guards pass; capture the cancel's trace-id; per-leg tight Jaeger window keyed by that
  trace-id → exactly-one-trace file per leg.
- **P3 — Score with frozen `trace_score.py` (3 configs) on BOTH legs.** Report naive/service-map/DB-span
  for control AND fault (A-m4): the pinned naive+service-map are `no_flag` on BOTH legs (structurally
  blind, an export-health canary — control MUST show the DB-client spans or the fault no_flag is
  uninformative); DB-span presence = present-control / absent-fault = CATCH. This is the
  specification-locality datum.
- **P4 — Independent ground truth (anti-circularity).** Read the label from the inside-payment DB (a
  mechanism ORTHOGONAL to MIST's `/account` transport — A-m3; the `/account` re-read wraps the same
  endpoint, so it is a store re-read, not an orthogonal oracle, carried as the 2.75-A caveat). The
  read-mechanism validator remains the paired CONTROL leg (control must show +refund, else NOT_EVALUABLE).
- **P5 — Flip the cell + freeze + RESULT.** `mist_readback_oracle` PRE-REGISTERED→run-backed flag
  (dated freeze §6 row); add the DB-span-granularity trace-comparator result to the case; RESULT-of-record
  carries §0/§1 verbatim; README/FILE_INDEX/memory sync; TT left in a known state (re-snapshot or scale 0).

## §4 Soundness (guards + the corrected gate rationale)

- **Anti-circularity:** SUT-native label from the inside-payment DB (orthogonal), control-leg validator,
  the 2.75-A firewall — intact for value-delta (a broken read makes control read flat → NOT_EVALUABLE,
  never FIRE).
- **Value-delta predicate (A-m5):** the verdict FIRES on any baseline→final MOVEMENT, not on
  delta==refund; refund-magnitude is carried by `printProbeValues` + the ground-truth read, not the
  predicate. State this so FIRE is not over-read as amount-verified. `requirePreFundedBaselines` makes it
  a real arithmetic delta (same positive baseline both legs), defeating the appear-vs-absent critique.
- **Gate (A-M1/B-4 corrected):** with C1's trace-id capture the read-back is TRACE-GATED (stronger). If
  C1 is descoped, the timeout-gate is still sound HERE because `fabricatedack` is a by-construction
  PERMANENT skip (no slow-refund competing hypothesis) and control lands within the cap — NOT because
  the deploy "may" strengthen it (rev-1's claim was unreachable with `traceId=null`).
- **Claim-eligibility:** correlator join + unique per leg (asserted, fail-closed).
- **Trace T6 attestation:** DEFAULT javaagent instrumentation, no suppression — "trace misses at
  naive/service-map granularity" is not an artifact of hidden spans.

## §5 Risks

- **P0 re-instrumentation is the real cost + risk** (agent OOM at low `-Xmx`, istio/jaeger health,
  hostPath mount). Mitigated by the traced-wave runbook + the canary-trace GATE before any measurement.
- **TT revival fragility** (nacos doubleWrite, ribbon stale routing, host-local snapshot). Mitigated by
  demo smoke + N≥4 probes.
- **Host-local, non-repo state** (snapshot, PFs, counts): the RESULT records exact pod count, image
  digests, agent sha, and the `-D` matrix so the datum is auditable after the environment is gone (B-6).
- **Synthetic-case significance** (C-F4): mitigated by FRAMING (§0/§1), not by pretending otherwise.

## §6 Explicitly OUT of scope

- `mist_trace_shape` as a MIST oracle (a MIST-side trace-structure detector) stays Branch-B/deferred —
  distinct from the DB-span-granularity *comparator* C2 adds (that is a TRACE-side baseline, correctly
  IN the head-to-head per C-F1).
- The S3 natural discriminator (the real headline) — rater-gated, deferred.
- Any prevalence/recall claim from N=5 on one synthetic case.

## §7 Definition of done

MIST read-back FIRE 5/5 on the traced fabricated-ack deploy (harness, trace-id-gated); frozen
`trace_score.py` (3 configs) on the SAME run's traces = naive MISS + service-map-presence MISS +
DB-span-presence CATCH, reported for BOTH legs; independent ground truth from the orthogonal DB read
committed; the corpus cell flipped to run-backed + the DB-span-comparator result added, with a dated
freeze §6 row; RESULT-of-record carrying §0/§1 verbatim (provenance-closure + specification-locality,
NOT the headline; corpus gives breadth not natural discrimination; S3 still owed); docs/memory synced;
TT left in a known state. THEN a 3-cold review of the RESULT before it is called claim-ready.
