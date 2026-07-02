# Comparator cold review C — integration/operational readiness of 666c461

**Date:** 2026-07-02. Independent cold reviewer (no shared context), one of three.
Reconciliation in REVIEW-COMPARATOR-RECONCILIATION.md.

## Findings (most severe first)
- **F1 [CONFIRMED HIGH]** — comparator auth works ONLY via the SUT-preflight side
  effect (`buildPreflightAuthHeaders` → `ensureReady()`), which is best-effort and
  skippable; operator disables the preflight → all requests unauthenticated → both
  endpoints infra-failure with a misleading "bindings/deploy suspect" detail. Fails
  safe (control gate precedes inject — zero rollouts) but burns the session. Fix:
  explicit `ensureReady()` + fail-loud. (Matches review A-F1.)
- **F2 [CONFIRMED MED-HIGH]** — the evidence sink covered only clear-failures;
  inject/transport/bindings-mismatch throws lost the whole report (incl. recorded
  clearFailures — the run-#2 evidence-loss shape). Fix: per-endpoint try/catch →
  infra-failure + continue; report always written.
- **F3 [CONFIRMED MED]** — immediate single-shot STATE_GET vs the measured 1–2 s
  benign convergence window → ~5–15%/endpoint transient control-abort probability
  (wastes the session, not rollouts — the clean-clear is rollout-free).
  Recommendation: bounded retry to MIST's 10 s/500 ms pre-registered cap, committed
  as a disclosed amendment BEFORE the run; transient control-FAIL = rerun-with-
  evidence, NOT the prereg failed-calibration branch.
- **F4 [CONFIRMED MED]** — `mist.fault.injection.enabled` was silently IGNORED by
  comparator mode (SutFlagFaultInjector constructed unconditionally): an operator
  setting it false still got live rollouts. Fix: fail-fast requirement.
- **F5 [CONFIRMED MED hygiene]** — smart-fetch never runs in comparator mode (H9's
  named risk does not bite) BUT the pre-branch RootApiRegistry.saveRegistry stamps
  last_updated on the SHIPPED root-api-registry.json every run. Mitigation: point
  `root.api.registry.path` at a target/ scratch copy (H9 extension); bonus — the
  preflight then probes only the gate-1-trace root APIs.
- **F6 [CONFIRMED LOW-MED]** — no HTTP timeouts on the comparator client (writer
  bakes them only into generated tests). Fix: connect/socket timeouts in the client.
- **F7 [CONFIRMED LOW]** — pre-branch work is wasteful-but-harmless (spec parse,
  trace extraction — `trace.file.path` must exist; FaultDetectionTracker init;
  preflight). TRAP: `mst.comparator.assertions.path` is NOT in MistPathResolver's
  input-path keys → resolved against CWD — run from repo root or use an absolute
  path. Flag plumbing from the .properties file verified working.
- **F8 [CONFIRMED LOW]** — fault-leg transport noise could produce a "flag" (same
  as review A-F2); per-check details disambiguate; fixed by the transport
  reclassification.
- Positive verifications: bindings↔registry names consistent; §4 outcome/control
  gate/f2-report/freshness pinned; clean-clear rollout-free; byte-additivity holds.

## Calibration-run checklist (operational — use with the fix wave applied)
- **Properties** (clone trainticket-gate1-pairing.properties →
  `trainticket-g2-calibration.properties`): `experiment.name=trainticket_g2_calibration`;
  ON: `mst.comparator.enabled=true`,
  `mst.comparator.assertions.path=debug/a-main/g2-comparator/assertion-bindings-trainticket-calibration.yaml`
  (CWD-relative — run from repo root), `mst.oracle.dataintegrity.enabled=true`,
  **`mist.fault.injection.enabled=true` (REQUIRED post-fix)**, `base.url=http://localhost:32677`,
  the shipped `auth.*` block verbatim (per_jvm, admin/222222), `mst.preflight.enabled=true`,
  `root.api.registry.path=target/comparator-scratch/root-api-registry.json` (H9),
  `trace.file.path=trainticket/test-trace-gate1` (parsed pre-branch, must exist);
  OFF: enhancer, two-phase; LLM keys not needed (no LLM calls on this path).
- **Cluster:** minikube start (lean topology persists; prometheus/grafana stay 0),
  wait pods Ready ~5–15 min; verify agent-only JAVA_TOOL_OPTIONS on both target
  deployments; port-forward ts-ui-dashboard 32677:8080 (NO Jaeger needed); manual
  login+GET smoke; then `java -Xmx4g -jar mist-cli/target/mist.jar <props>` from
  repo root.
- **Expect:** "login OK" → "COMPARATOR MODE: 2 bound endpoint(s)" → per-endpoint
  INJECT/CLEAR rollouts; wall ~5–12 min MIST-side (~1.5–3.5 min/endpoint: 2 real
  rollouts × [30–90 s + 15 s settle]), session ~20–40 min incl. cluster start.
- **Read the report:** both endpoints verdict `flag`; control all-PASS; fault-leg
  STATE clauses FAIL with detail "submitted state ABSENT" (NOT a transport error);
  response-clause outcomes recorded (per the §4 correction they may FAIL due to the
  sloppy fabricated ack — attribute to the injection-realism artifact).
- **Post-run:** re-verify both deployments agent-only; commit the report + the
  properties as the calibration record; minikube stop per the prereg lifecycle. Any
  abnormal exit → check both deployments' flags BEFORE rerunning.

## Verdict
READY-WITH-FIXES: pin the login (F1), commit the disclosed bounded state-read wait
(F3), patch the report-loss path (F2); F4/F5/F6 = fail-fast + scratch path + client
timeouts. (All applied in the fix wave.)
