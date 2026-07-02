# Prereg cold review C — methodology/pre-registration audit of the G2+G3 preregs

**Date:** 2026-07-02. Independent cold reviewer (no shared context), one of three on
the G2/G3 prereg wave. Cross-checked against README §4/§6/§8/§8.5, EXECUTION.md,
REVIEW-B1B2-RECONCILIATION.md, TOOL-PLAN §0/§3.5/§4/§5–7, p3-async-path-resolution.md,
the in-repo sockshop bundle, gate1-result.md. Findings ranked; reconciliation in
REVIEW-PREREG-RECONCILIATION.md.

## Findings (most severe first)

**F1 — CONFIRMED. §8.5-3 only half-fulfilled: sites named, opportunities never COUNTED; TrainTicket's own G3 saga site MISSING entirely.** The G3 doc trims the
commitment quote at "concrete site" (drops "how many genuine acked-but-lost-write
opportunities each presents"); no counts anywhere. TOOL-PLAN §3.5 promises a named TT
saga site (order/booking/payment flow) run at G3 — absent from the only G3 prereg. So
"missing-compensation" (North star / Gate-3 wording) and pending-vs-missing (BUILT+
VALIDATED at G3 vs a named saga site) currently have NO pre-registered target.

**F2 — CONFIRMED (silence) / PLAUSIBLE (5xx outcome). No sensitivity/constructed-positive story for SUT-2.** Sock Shop has no SUT-flag injector; S2 is producible only
invasively (TOOL-PLAN §0 fact 6) → Gate-1-style S2 sensitivity NOT demonstrable on
Sock Shop as pre-registered. Toxiproxy S1 yields acked-but-lost ONLY IF carts/orders
2xx-mask a Mongo failure — an unstated empirical unknown; if they honestly 5xx: zero
constructed positives on SUT-2 → no sensitivity demo, no comparator calibration
faults, no injected benchmark stratum there, nothing for the gated/S1 D-span locator
to validate against (doubly so under mitigation (b): no javaagent → no D spans).

**F3 — CONFIRMED. G2 paragraph's "observes only the OTel the system already emits (no added instrumentation, any language)" is contradicted by the sibling G3 prereg** (attaches the javaagent to 4 Sock Shop services; TT also got it). README's own rule
is "no *test-specific* instrumentation" — the paragraph over-tightens into a
falsifiable absolute on the headline deliverable.

**F4 — CONFIRMED. Hardening promotions un-propagated; bar v2 (R2fix) not adopted for G3** although G3 mitigation (b) (shallow traces → all TIMEOUT_ABSENT →
observedGated==0) lands exactly in the regime where the current bar vacuously PASSes.
R1fix promotion lives only in the G3 prereg (recon §3 + EXECUTION G3 not updated) —
the same propagation failure mode two earlier reviewers caught. R3fix/R4fix G3 status
undecided.

**F5 — CONFIRMED gaps. Decisive-result adjudication + comparator competence:** no
named adjudicator/blinding/≥2-rater κ (README §6 stratum-3 precedent); "post-hoc
root-cause shows" has no subject; single fresh-context agent = one sample of
"competent engineer," no floor; calibration has no acceptance criterion; the Cast-
pattern half is re-hedged "where the OSS setting permits" inside a section titled
"pre-committed."

**F6 — CONFIRMED. Per-SUT FP measurement invoked, not operationalized:** probe N,
the ≤5% bar per-SUT (same or re-registered?), quiescence/compensation timeouts per
SUT, and whether a NOT_EVALUABLE-bar SUT counts toward "≥2 SUTs" — all unpinned. G2
carries R2's interval (credit) but drops gate histogram + non-trivial observed-gated
denominator.

**F7 — CONFIRMED. Comparator assets for SUT-2/3 unscheduled:** blind sets scheduled
for TT only; no author/freeze plan for Sock Shop/petclinic although the G3 prereg
itself publishes SUT-2's fault paths in-repo (only a context-fresh agent can be
blind).

**F8 — CONFIRMED. Elastic change triggers:** "unrunnable" un-boxed; "usable
membership semantics" undefined (arguably already true of SS-C's paginated read-back
→ could dodge R1fix); petclinic-promoted-to-SUT-2 pivot outside registered triggers.

**F9 — CONFIRMED. SS-A's isolation-strategy extension unpinned:** fresh session per
run AND per probe iteration not required (session reuse + cart-merge on re-add would
leave X present → optimistic FP undercount); item-selection rule and (session,itemId)
re-basing of the FP/isolation-violation rules unregistered — cold-review I's
true-negative reasoning depended on fresh keys.

**F10 — Framing asymmetry CONFIRMED / degeneration PLAUSIBLE (precedented by P3).**
"Depth triple"/"Sock Shop carries the depth" rides the broker leg BEFORE the async
QUESTION resolves; if negative (P3 found the identical shape on TT), SS-C degenerates
to sync fan-out CRUD and the depth credential survives unretracted. Topology fact
fine; §8.5-3 credential NOW is the smuggle.

**F11 — MINOR cluster (all CONFIRMED as gaps):** (a) stale stamps vs gate1-result
("no 3rd relaunch") — ambiguous which doc governs; (b) matched recall undefined for a
threshold-less comparator; (c) no seeds/stats plan for the head-to-head (README §6:
≥10 seeds, MWU/Â₁₂); (d) F-MOD-3 P2-completeness not referenced for queue-master if
async resolves positive; (e) §8.5-1/B4 absence from G2 checklist undeclared;
(f) petclinic triples named-endpoints-only (no ack/isolation/fault/completeness);
(g) "structurally miss" must stay bound to the blind comparator, never drift to Cast.

**Credit:** §0 pagination catch = prereg working as intended; R2 interval carried;
argued-not-measured respected; async-QUESTION hedge is model anti-goalpost language;
blind-freeze + shipped brief auditable; drop-SUT-never-weaken retained; SUT-2+SUT-3
satisfies README §4-6's "≥3 SUTs exercise the data-integrity oracle"; memory-budget
sequencing correct — but state the full cluster lifecycle once (TT up through G2
calibration → stop → kind/Istio for G3), since G2's "when the cluster frees up" and
G3's "after Gate-1 stops" read as opposite dispositions.

## PIN THESE NOW (13)
1. TT depth site + COUNTS (cures F1): name TT's G3 saga/compensation flow + Toxiproxy
   placement + count opportunities; add counts for SS-A/B/C + petclinic; say where
   pending-vs-missing gets its named target.
2. SUT-2 sensitivity branch (F2): live-verify whether carts/orders 2xx-mask a
   Toxiproxy'd Mongo failure; pre-commit the branch if not (source-injected LOST_WRITE
   fork of carts à la TT, or SUT-2 = FP/breadth+wild-hunt-only, with consequences for
   calibration + benchmark strata). State where gated-mode validation happens if SS
   traces stay shallow.
3. Bar v2 + propagation (F4): adopt R2fix for every G3 run; propagate R1fix promotion
   into recon §3 + EXECUTION G3; decide R3fix/R4fix status.
4. Per-SUT FP protocol (F6): N (default 30), per-SUT bar, per-SUT timeouts fixed
   before deploy, NOT_EVALUABLE-SUT counting rule.
5. Miss-category adjudication (F5a): ≥2 raters, blind to tool identity, κ, assigned
   from frozen assertion set + artifacts; MIST author never adjudicates alone.
6. Comparator competence + scope (F5b): calibration acceptance criterion on faults
   disjoint from the eval set; re-authoring only as brief-improvement before any
   eval-fault reveal; consider ≥2 independent blind authors; decide Cast-pattern
   in/out NOW (delete "where the OSS setting permits").
7. SUT-2/3 blind sets (F7): author + fresh-context requirement + freeze-by-commit
   before the G3 fault list is finalized.
8. Instrumentation wording (F3): "no test-specific instrumentation — standard
   off-the-shelf OTel (stock javaagent where a system doesn't already export), zero
   code changes"; disclose TT + SS Java legs get the agent from the harness.
9. Crisp triggers (F8): time/attempt-box "unrunnable"; define "usable membership
   semantics" = §4 checklist (verbatim echo; unpaginated or exhaustible; no
   normalization); pre-order the fallback chain; swap documented before any run.
10. SS-A semantics (F9): fresh session per run AND per probe iteration; item-selection
    rule; FP/isolation-violation re-based on (session,itemId); live-verify cart-merge.
11. SS-C depth conditional (F10): pre-register the credential's reduction if async
    resolves negative (sync fan-out breadth; absolute depth stays TT's per pin 1); if
    positive, queue-master inventory = declared P2 set + F-MOD-3 validation.
12. G2 outputs completion (F6/F11b,c): + gate histogram + non-trivial observed-gated
    denominator; seeds/stats plan; operationalize matched recall or pre-register the
    alternative reporting.
13. petclinic (F11f): complete its triple pre-spec or pre-register a frozen amendment
    before any petclinic run.

## Verdicts
- g2-novelty-comparator-prereg.md: **FIT-AFTER-PINS** (5–8, 12).
- g3-sut2-triples-prereg.md: **FIT-AFTER-PINS** (1–4, 9–11, 13); do not rely on it
  for any G3 deploy until pins 1–4 land.
