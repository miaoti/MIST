# Prereg cold-review reconciliation (G2 + G3 preregs; 3 independent reviewers, 2026-07-02)

Reviews: [A — hostile PC on G2](REVIEW-PREREG-A-pc.md),
[B — technical accuracy on G3](REVIEW-PREREG-B-tech.md),
[C — methodology on both](REVIEW-PREREG-C-methods.md). No shared context. Verdicts:
G2 paragraph NO-as-is / comparator right-intent-not-yet (A); G3 trustworthy after 2
corrections (B); both docs FIT-AFTER-PINS (C). This doc maps findings → actions and
records their disposition.

## Consensus / cross-confirmed findings
1. **Blindness must be provisioning-based, not reveal-ordered** (A-F1 CRITICAL;
   C-F7 concurs: the repo itself publishes the fault paths). → G2 v2 §2 blindness
   re-based (enumerated hash-frozen provisioning; TT blindness qualified honestly;
   frozen brief text; endpoint superset). **DONE in G2 v2.**
2. **Paragraph fixes** (A-F2/F3/F10/F12/F13; C-F3 javaagent contradiction): rewrite
   the impossibility sentence; scope "no assertion points" to "no expected-outcome
   specification per check point" + registry concession; metamorphic clause inside;
   dev-confirmed; "requires only"; drop "any language"; javaagent disclosure inside;
   frequency phrasing. **DONE in G2 v2.**
3. **Comparator = Filibuster-approximating, Cast-pattern OUT** (A gate-fit; C-pin 6
   "decide now"). **DONE in G2 v2** (with rationale).
4. **Competence floor + failed-calibration branch** (A-F4; C-pin 6): calibration set
   = the two public Gate-1 faults (burned for blindness anyway → correct acceptance
   set); disjoint from G3 eval set by construction; brief-improvement + second
   independent author branch; never edit assertions post-hoc. **DONE in G2 v2.**
5. **Operating points + detection unit** (A-F7; C-pin 12): MIST-strict
   (observed-gated) primary / MIST-all secondary / comparator full-set; unit = per
   injected-fault instance per triple per run-pair; ≥10 seeds + MWU/Â₁₂. **DONE in
   G2 v2.**
6. **Symmetric blind adjudication with κ + infra-evidence rule** (A-F8; C-pin 5).
   **DONE in G2 v2.**
7. **R2-complete FP outputs** (A-F9; C-F6): interval + gate histogram + non-trivial
   observed-gated denominator + Jaeger-health evidence + per-triple + acked-records
   denominator + recon-§4 stratum wording. **DONE in G2 v2.**
8. **Injected wins = calibration only; PC-moving = real G3 defects** (A-F11).
   **DONE in G2 v2.**
9. **Cluster lifecycle stated once** (C credit note): TT minikube up through G2
   calibration → stop → kind+Istio for G3. **DONE in G2 v2 §3.**

## G3-doc corrections (B) + methodology pins (C) — applied in G3 v2
- **B MAJOR-1:** tracing mitigation (a) rewritten — OTel **Node front-end
  auto-instrumentation is the load-bearing half** (`NODE_OPTIONS` +
  auto-instrumentations-node + OTLP to jaeger-collector:4317, which istio-1.30's
  jaeger 2.14 accepts natively); Java javaagents then connect; k8s realities added
  (readOnlyRootFilesystem → initContainer+emptyDir; heap bump above -Xmx128m;
  Java-8-era images → agent-version live-check).
- **B MAJOR-2:** §0 respecified — the BFF `GET /orders` read-back is UNPAGINATED
  (findByCustomerId); completeness mechanism via BFF = bounded-collection/row-count
  assertion; R1fix stays a G3 prerequisite (TeaStore windows; SS-B global lists
  accumulate; conservative).
- **B MEDIUM-3:** SS-B corrected to global-growing seeded lists (front-end proxies
  unfiltered) + explicit R1 inheritance; `GET /customers/{id}` ignores {id} noted.
- **B MEDIUM-4:** 4th engineering item added — extend the VirtualService with
  /register + /login (+/card,/address) routes.
- **B MINOR-5:** Mongo wording scoped to triple-relevant services; rabbitmq +
  session-db in-mesh → AMQP-under-Envoy live-check.
- **B INFO-1 (upside):** shipping's swallowed enqueue failure recorded as a NATURAL
  masked-failure candidate (masking-oracle/benchmark stratum, NOT a B2 read-back
  target — black-box-invisible) — and it resolves the SS-C async QUESTION
  **negative** (queue-master persists nothing; order.shipment written at creation
  regardless): same verdict shape as TT's P3. SS-C depth credential therefore
  reduces per C-pin 11 (sync fan-out breadth; absolute depth = TT's saga site).
- **B INFO-2 (upside):** `?custId=` dev-mode override = cheaper cart isolation lever
  (fresh custId per run without cookie sessions; orders still needs a session).
- **C-pin 1:** TT G3 saga/compensation site + opportunity COUNTS added (CANDIDATE,
  live-verify) — cancel→refund compensation flow as the named site; counts
  spec-derived.
- **C-pin 2:** SUT-2 sensitivity branch pre-registered (live-verify whether
  carts/orders 2xx-mask a Toxiproxy'd Mongo failure; branch = source-injected
  LOST_WRITE fork of carts à la TT, or SUT-2 = FP/breadth+wild-hunt-only).
- **C-pin 3:** bar v2 (R2fix: gate-degraded → NOT_EVALUABLE; interval+histogram
  mandatory) adopted for every G3 run; promotions propagated to
  REVIEW-B1B2-RECONCILIATION §3 and EXECUTION.md G3.
- **C-pin 4:** per-SUT FP protocol pinned (N=30; same ≤5% observed-gated bar
  per-SUT; Gate-1 timeouts carried unless re-registered with justification;
  NOT_EVALUABLE-bar SUT does NOT count toward "≥2 SUTs").
- **C-pins 9/10:** crisp change triggers (time-boxed; "usable membership semantics"
  defined = verbatim key echo + unpaginated-or-exhaustible-or-bounded + no
  normalization; fallback chain pre-ordered); SS-A semantics pinned (fresh scope per
  run AND per probe iteration; item-selection rule; (scope,itemId) re-basing;
  cart-merge live-check).
- **C-pin 13:** petclinic triple pre-spec completed (ack = bare 2xx/201 JSON;
  FRESH_STRINGS; Toxiproxy→MySQL with mysql profile; completeness live-check).
- **C-F11a:** stale stamps — G3 v2 header states run #3 is EXECUTING and
  gate1-result.md will be superseded by its verdict.

## Remaining open (tracked, not blocking the preregs)
- G2 §1 threat: show triples are OpenAPI-derivable or disclose hand-selection
  (paper-writing obligation; carried in G2 v2 hygiene).
- C-F11e: B4 independent-label harness explicitly out of G2 scope (belongs to the
  masking-precision study) — noted here as the declared decision.
- SUT-2/3 blind-set authoring (C-pin 7) scheduled in G3 v2 §4; execution happens at
  G2/G3 build time.

**Net verdict:** with G2 v2 + G3 v2 applied, both preregs are at
FIT (C's bar) — subject to the standing rule that any future material change is a
disclosed amendment.
