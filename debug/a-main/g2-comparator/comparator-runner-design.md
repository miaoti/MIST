# G2 comparator runner — design spec (test-first; implement next)

**Status:** BUILT (666c461) + 3-COLD-REVIEWED + FIX WAVE APPLIED 2026-07-02
(reviews: research/REVIEW-COMPARATOR-{A-soundness,B-bindings,C-integration}.md;
reconciliation REVIEW-COMPARATOR-RECONCILIATION.md). **Disclosed amendments
(pre-run — no comparator run has executed):**
- **A2 (bindings completed, review B):** the v1 state clauses narrowed exactly onto
  MIST's registry keys; v2 binds adminroute's second frozen read path (per-entity
  `GET /routes/{id}` via `${field:id}` path templating + the new
  `entity-matches-submitted-fields` mode) and extends membership fields
  (adminroute id+start+end; contacts all five submitted fields).
- **A3 (read timing, reviews A-F5/C-F3):** presence-expect STATE_GET checks retry to
  the SAME pre-registered 10 s/500 ms budget MIST's read-back uses — harness-level
  read timing (still no trace gate, no differential); kills the transient
  control-abort risk (~5-15%/endpoint per the Gate-1 curve) and blunts the
  delay-type-fault recall inflation at G3. Decisive-read transport failures are
  transport-marked; **fault-leg verdicts whose only failures are transport are
  reclassified comparator-infra-failure** (mirroring MIST's read-back-error
  category) — an infra blip is never a detection (review A-F2).
- **§3 correction ("matched inputs"):** the comparator uses frozen body TEMPLATES,
  not literally the pairing's bodies (run #3's contacts pairing produced zero
  records — no body exists to reuse; reusing adminroute's would smuggle in MIST's
  isolation adapter, which §1 forbids). The `${uuid:id}` membership key is licensed
  verbatim by the frozen contract and is comparator-favoring if anything. The paper
  claims matched ENDPOINTS + FAULTS + budget, not byte-identical bodies.
- **§4 correction (review B finding 4):** the pre-stated "response clauses alone do
  NOT flag" is FALSIFIED by committed G0 evidence — the injected fault fabricates a
  SLOPPY ack (adminroute: msg lowercase variant + data:null; contacts: msg case
  variant), so MSG_CONTAINS/ENVELOPE_DATA will likely also fail under the fault.
  This is an INJECTION-REALISM artifact (a real masked failure returns the pristine
  ack), disclosed: calibration acceptance now reads on the PER-CLAUSE outcomes —
  the STATE clauses must FAIL under fault and PASS under control (validating the
  state machinery independent of the artifact); response-clause flags are recorded
  and attributed to the artifact.
- **Operational preconditions (review C):** comparator mode fail-fasts on
  `mist.fault.injection.enabled=true` and a successful `MstAuthHandler.ensureReady()`
  login (review A-F1: nothing else on the path logs in); the client carries
  connect/socket timeouts; a clear failure STOPS the endpoint loop (later endpoints
  = not-run); every mid-loop failure is per-endpoint comparator-infra-failure and
  the report is always written. Runbook: run from repo root (assertions.path is
  CWD-relative), point `root.api.registry.path` at a target/ scratch copy (H9
  extension — the preflight otherwise dirties the shipped file), and the G3 binding
  round must bind the FULL frozen set incl. failure contracts.

Original spec below. Consumes the FROZEN assertion set
([blind-assertions-trainticket.yaml](blind-assertions-trainticket.yaml), frozen at
`15954a8`) per the G2 prereg §2. Scope = **calibration** on the two public Gate-1
faults first (prereg §3 step 4); the full head-to-head reuses the same runner at G3.

## 1. What the comparator is (and is not)
A **Filibuster-style assertion oracle**: for a target write endpoint, after MIST's
fault injection (reusing `FaultInjector`/`SutFlagFaultInjector` — the same faults,
same budget), it issues the SAME verified write the pairing test performs and then
evaluates the **frozen, blind-authored contract** for that endpoint. It does NOT use
MIST's read-back differential, isolation freshening, quiescence gate, or trace
lookup — that is the point of the comparison. It is NOT Cast (no metric thresholds,
no historical baselines — disclosed in the prereg).

## 2. Executable translation of the frozen prose (the sensitive step)
The frozen contracts are prose; executing them requires translation. Rules to keep
the translation faithful and auditable (a hostile reviewer WILL check this):
- **Translation is mechanical and per-clause**, recorded in a committed
  `assertion-bindings-<sut>.yaml` mapping each frozen clause to one of a small
  closed set of check primitives:
  `HTTP_STATUS(expected...)`, `ENVELOPE_STATUS(1)`, `ENVELOPE_DATA(non-null|null)`,
  `MSG_CONTAINS(text)` (secondary per the manifest's own caveat),
  `STATE_GET(path, expect: contains-submitted-fields | absent)`.
- Every binding cites the frozen clause verbatim; clauses marked UNKNOWN in the
  frozen set are bound to `NOT_CHECKABLE` (excluded, disclosed) — never guessed.
- The binding file is itself frozen by commit BEFORE any comparator run, and the
  ≥2-rater adjudication (prereg §2) rates misses against the FROZEN set, not the
  bindings — a binding error is a `comparator-infra-failure` category, not a
  no-assertion-existed win.
- MSG checks: bind only when the manifest marks the msg high-confidence; else
  ENVELOPE_STATUS/data/state carry the contract (the manifest's own instruction).

## 3. Runner mechanics (calibration scope)
- New CLI entry (flag-gated, additive): `mst.comparator.enabled=true` +
  `mst.comparator.assertions.path=<bindings yaml>`; OFF by default; zero effect on
  every existing path (byte-additivity like B1/B2).
- Sequence per calibration fault (mirrors the pairing executor's hygiene):
  clear-all → CONTROL: write + evaluate contract (expect: all clauses pass on the
  healthy SUT — this validates the bindings) → inject → FAULT: write + evaluate →
  clear-all (finally; reuse the F2 fail-safe pattern + evidence-sink lesson).
- Inputs: the same verified body the Gate-1 pairing used (from the committed run
  config / pool) — matched inputs, no comparator-specific crafting.
- Verdict per endpoint per run: `flag` iff ≥1 bound clause FAILS (the assertion
  oracle "detects"); record per-clause outcomes.
- Report: `logs/comparator-reports/comparator_<sut>_<id>.json` — per endpoint:
  per-clause pass/fail, verdict, control/fault bodies (truncated), the binding id;
  plus the run's fault manifest. Format mirrors the pairing report's evidence style.
- **Calibration acceptance (prereg §2 competence floor):** on the two Gate-1
  LOST_WRITE faults the comparator must flag BOTH fault runs (the frozen
  state_postcondition clauses — "created entity appears in GET ..." — fail under a
  lost write) AND pass both control runs clean. If it does not flag: apply the
  pre-registered failed-calibration branch (brief improvement + second author),
  NOT binding edits to force a flag.

## 4. Expected calibration outcome (pre-stated, falsifiable)
For adminroute-create and adminbasic-contacts-create under LOST_WRITE:
- `HTTP_STATUS` + `ENVELOPE_STATUS(1)` clauses PASS in the fault run (the SUT lies —
  that is the masking) → response-contract clauses alone do NOT flag.
- `STATE_GET` clauses FAIL in the fault run (entity absent from the list GET) →
  the comparator FLAGS via its state clause.
This is the honest calibration story: a competent blind engineer DID write the
create-then-read contract (the manifest proves it), so the comparator catches the
constructed faults — matching the prereg's expectation that injected wins are
calibration-only, and the G2/G3 question is the FREQUENCY of such coverage over
endpoints and the FP cost, not this one win.

## 5. Test list (pin first)
(t1) bindings loader: valid bindings parse; unknown primitive rejected; UNKNOWN
clause → NOT_CHECKABLE recorded. (t2) evaluator vs fake responses: each primitive
passes/fails correctly (envelope status 0, data null, state-GET containing/missing
submitted fields). (t3) endpoint verdict = flag iff ≥1 clause fails; per-clause
outcomes recorded. (t4) control-run all-pass gate: a failing control clause marks
the endpoint `comparator-infra-failure` (bindings suspect), never a detection.
(t5) report JSON shape. (t6) flag-gated additivity: comparator off ⇒ no new code
reachable.

## 6. Out of scope here
The seeds/statistics plan, matched-recall reporting, and the miss-category κ
adjudication run at the G3 head-to-head (prereg §2); SUT-2/3 assertion sets are
authored per the G3 prereg schedule.
