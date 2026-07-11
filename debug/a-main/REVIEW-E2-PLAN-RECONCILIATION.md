# E2 discrimination plan — 3-cold-review reconciliation (rev 1 → rev 2)

**Date:** 2026-07-10
**Reviewed:** `debug/a-main/c2c3/e2-discrimination-plan.md` rev 1 (pre-execution plan review).
**Reviewers (cold, independent):** A = oracle-soundness · B = engineering/reproducibility · C = hostile-PC/claim.

## Verdicts on rev 1

| reviewer | verdict | blocking | major |
|---|---|---|---|
| A (oracle-soundness) | ACCEPT-WITH-FIXES | 1 | 2 |
| B (engineering/repro) | **REJECT** | 2 | 1 |
| C (hostile-PC/claim) | ACCEPT-WITH-FIXES | 0 (3 "must-fix-before-claim", 1 before-execution) | 4 |

**NOT unanimous (B REJECT + A blocking) → rev 1 does NOT execute.** The reviews were high-value and
convergent; rev 2 folds every blocking + major finding. Nothing has executed.

## Findings + disposition in rev 2

### Convergent BLOCKING (the two that made it non-executable)

- **[B-1 BLOCKING] The traced deploy is not restored by the snapshot** — the traced-capture wave TORE
  DOWN instrumentation and TT was scaled to 0 de-instrumented; the snapshot restores replica counts +
  nacos + image tags only, not the javaagent/OTEL/jaeger. **FOLDED:** rev 2 §3 P0 is a PRIMARY
  re-instrumentation phase (scale-0 → patch volume+mount+env → scale-1 + gates), with an istio/jaeger
  health check and a canary-trace STOP gate. Demoted from a §5 risk bullet to the largest work item.
- **[A-B1 + B-2 BLOCKING] No trace-to-leg binding** — the harness passes `afterWrite(traceId=null)`,
  the gateway is header-transparent, and `trace_score.py` requires exactly-one-trace-per-file (selects
  by service+kind, not buyer), so a time-window pull cannot isolate a leg's cancel; and the harness
  runs legs back-to-back with no per-leg boundaries. **FOLDED:** rev 2 §2 C1 = a DISCLOSED harness+stimulus
  change — inject a client-generated W3C `traceparent` on the cancel, return its id in `Resp`, feed it to
  `afterWrite` (earns a trace-gated read-back) AND use it to select exactly that cancel's trace. Plus
  §2 C3 = 5 fresh JVM invocations so each leg's trace windows cleanly. rev 1's "no harness change" claim
  is retracted.

### MAJOR — claim/comparator (fix before execution / before claim-ready)

- **[C-F1 MAJOR, before execution] The strongest trace comparator (DB-span-granularity presence) was
  EXCLUDED, then the plan claimed "beats trace"** — a bait-and-switch, since T6 shows the fault IS
  visible at DB-span granularity (0 vs 2 inside-payment DB-client spans). **FOLDED (the key upgrade):**
  rev 2 §0.2 + §2 C2 add a FROZEN DB-span-presence selector to `trace_score.py` and report all THREE
  trace configs (naive MISS / service-map-presence MISS / DB-span-presence CATCH). The claim is
  reframed to the DEFENSIBLE and stronger **specification-locality / authoring-burden** argument:
  read-back catches it out-of-the-box; a trace oracle catches it only with a pre-specified assertion on
  the exact skipped DB write. This scores off the SAME exported traces, so it must be pre-committed and
  run in the same pass (before execution).
- **[A-M2 + C-F2 MAJOR] "beats TRACE" over-titles §1's honest scope.** **FOLDED:** rev 2 retitles to a
  capability + provenance-closure run, carries the granularity qualifier everywhere, deletes "owed
  headline" language. §7 already produced §1's scoped claim; the drift was title/§0 ↔ §1, now aligned.
- **[C-F3 MAJOR] The corpus does not rescue the synthetic discrimination** — §1 slid "natural fault
  class" into "natural discrimination"; the 2.75-A cases are sole-oracle or presence-concordant, ZERO
  natural trace-miss instances. **FOLDED:** rev 2 §1 states explicitly the corpus gives read-back
  applicability breadth, NOT natural discrimination; the S3 natural discriminator stays the owed headline.
- **[C-F4 significance] The synthetic construct is near-tautological; existence is a footnote not a
  headline.** **FOLDED as scope:** rev 2 positions the run as provenance-closure + a secondary capability
  datum that DE-RISKS the S3 headline, never substitutes for it (C's own recommendation).
- **[A-M1 + B-4 MAJOR] "traced deploy → stronger gate" is unreachable with `traceId=null`.** **FOLDED:**
  rev 2 §4 — with C1 the read-back IS trace-gated (now reachable); if C1 were descoped, the timeout-gate
  is sound because `fabricatedack` is a by-construction permanent skip + control lands within the cap.
  The "may strengthen" aspiration is deleted.

### MINOR — reframings + pins (all folded)

- **[A-m3] label-independence not orthogonal** → rev 2 P4 reads the inside-payment DB (orthogonal),
  carries the 2.75-A "store re-read, not an orthogonal oracle" caveat, credits the control-leg validator.
- **[A-m4] report both legs' trace scores** → rev 2 P3 reports control AND fault (pinned oracles blind
  on both; control is the export-health canary).
- **[A-m5] value-delta fires on any movement, not delta==refund** → rev 2 §4 states magnitude is carried
  by probe values + ground truth, not the predicate.
- **[A-m6 / B-3 / C-F6] N + `-D` matrix unpinned** → rev 2 §2 C3 pins 5 fresh constructed-only JVMs
  (one begin/endRun each, else `allUnique` fails) + the exact `-D` matrix (both triples required).
- **[B-5] toggle auth is the reader token, not admin bearer** → rev 2 P1 aligns the wording.
- **[B-6 / C-F5] host-local non-repo state / run is provenance not a new finding** → rev 2 §0 reframes as
  provenance closure; §5 records exact pod count / digests / agent sha / `-D` matrix for auditability.

### What the reviewers confirmed sound (kept)

The harness reuse is faithful (A + B verified): `CancelRefundHeadToHead` runs a clean control + a
fabricated-ack fault leg over the runtime `HttpToggleFaultInjector`, feeds MIST value-delta on
`/inside_payment/account`, enforces `requirePreFundedBaselines` + `requireClaimEligible`; its built-in
comparator is genuinely the response-contract (so the "trace scored out-of-band" split is real);
`trace_score.py` does naive=error-span + presence=cross-service-span-existence + the DB-span disclosure;
the stimulus (fresh buyer, pre-fund 50.00, PAID order, bodyless cancel) still matches a revived deploy.

## Net

Rev 2 folds all 3 blocking + all 6 major findings + the minors. The conceptual upgrade (C-F1's
DB-granularity comparator → specification-locality claim) makes the datum genuinely defensible rather
than tautological, and the honest reframe (provenance-closure + bounded capability, NOT the headline;
S3 still owed) matches all three reviewers. **NEXT: re-review rev 2 (unanimous ACCEPT required) before
any execution.** The run itself is now correctly scoped as a secondary, S3-de-risking step — the real
discrimination headline remains the rater-gated S3 natural-discriminator hunt.
