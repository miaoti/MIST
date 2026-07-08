# MIST — Black-Box Detection of Acknowledged-but-Lost Writes

**Project overview.** A concise, presentation-oriented synthesis of the direction, mechanism, baseline,
evaluation, results, and honest scope. Detailed plans and results live in the docs referenced by
`EXECUTION.md` (the top-level document map).

---

## 1. Problem

Microservices routinely acknowledge a request as successful (HTTP `200`) while silently failing to persist
the underlying state change — an **acknowledged-but-lost write**. A canonical instance: an order
cancellation returns `{status: 1, "Success"}` while the refund is never credited.

Conventional test oracles — status code, schema, response-shape — **pass these by construction**: they
inspect only the response, never the resulting state. The phenomenon is pervasive: swallowed errors account
for roughly 29% of errors in a recent industrial study (Uber, SIGMETRICS'25).

## 2. Approach and positioning

MIST detects acknowledged-but-lost writes with a **black-box, generation-driven, label-free read-back
oracle**. It uses only the system's public HTTP surface plus the standard OpenTelemetry it already emits —
**no source access, no AOP instrumentation, no production traffic, and no human-authored per-endpoint
assertions**.

Positioning against the closest prior work, **Cast** (ICSE-SEIP'26), which also detects masked failures:

| Axis | Cast | MIST |
|---|---|---|
| Workload | Production-traffic replay | Generated cross-service inputs |
| Instrumentation | Java AOP agents (language-specific) | Black-box, standard OTel (language-agnostic) |
| Oracle | Metric thresholds + assertion points + historical baselines | Label-free read-back differential (no thresholds, no assertions, no baselines) |
| Evaluation | Closed | Open-source SUTs + released labeled benchmark |

**Honest framing.** The contribution is *accessibility + automation + an open benchmark*, not primacy of
detection. The read-back oracle applies to write-path services with a black-box read-back; the masking
signal itself is not claimed as novel.

## 3. Mechanism

### 3.1 The oracle (the contribution)

- **Core relation (per-run, metamorphic).** A `2xx`/success response that acknowledges entity `X` must have
  `X` observable on its **own** read-back; the oracle fires when this is violated. The write contradicts
  *itself* — no second run is required to detect it.
- **Read-back = a black-box follow-up GET**, never database access, issued at a declared
  `(write endpoint, persisting dependency, read-back endpoint)` triple.
- **Two read-back modes:** *membership* (`X` appears in a collection) and *value-delta* (a numeric field
  moved by the expected amount — e.g., a balance).
- **Soundness protocol:** fresh-key isolation per test; quiescence (poll until the value stabilizes or the
  trace shows the write complete, bounded timeout) to absorb benign eventual consistency; confidence
  stratification (observed-absent vs. timeout-gated).
- **Deployment vs. lab.** At a target site the oracle runs **standalone per-run** (generate a write → read it
  back → check self-consistency). The control/fault pairing (below) is lab scaffolding and is not shipped.
  Measured false-positive rate of the standalone per-run mode: **0 / 2127** (TrainTicket), **0 / 1200**
  (Sock Shop).

### 3.2 Fault injection — scaffolding, not the contribution

A control/fault pairing manufactures **labeled ground truth** to validate the oracle, and is disclosed as an
opt-in grey-box mode — a deployed MIST detects *naturally occurring* defects, not injected ones. Injection is
realized two ways: a SUT-side flag (source fork, disclosed) for clean labeled positives, and
infrastructure-level faults (service-mesh and message-broker policies) for the unmodified-system path.

## 4. Fair baseline (the comparator)

To substantiate "MIST catches what assertion oracles miss," we build a **competently configured,
blind-authored contract oracle** in the style of Filibuster (fault injection + hand-authored per-endpoint
assertions).

- **Blind protocol.** An independent author writes success contracts for **all 79 write endpoints across 22
  services** from the upstream source only, then freezes them in git **before** the fault/defect set is
  revealed. This precludes reverse-engineering the baseline to lose.
- **Execution pipeline.** Each natural-language clause is mechanically translated into **executable bindings**
  over a **closed primitive set** — `HTTP_STATUS`, `ENVELOPE_STATUS`, `ENVELOPE_DATA`, `MSG_CONTAINS`,
  `STATE_GET` (a follow-up GET with membership/entity matching), and `NOT_CHECKABLE`. An evaluator runs its
  own control and fault writes and checks every clause; it **flags iff at least one evaluated check fails**.
  `NOT_CHECKABLE` never fires; a transport failure on a follow-up GET is reclassified as infrastructure, never
  a detection.
- **Why it is fair (construct validity).** The closed primitive set faithfully represents the expressiveness
  of the response/contract-assertion oracle *class* (Pact, Dredd, synthetic monitoring), which cannot express
  cross-request arithmetic. Fairness is enforced by a pre-registered **competence floor** (the frozen set must
  catch the injected faults) and three independent cold reviews.
- **Grounding.** The specification/contract-driven oracle paradigm (Barr et al., *The Oracle Problem in
  Software Testing*, TSE'15); Filibuster (SoCC'21); contract testing (Pact, Dredd).

## 5. Evaluation gates

| Gate | Question | Bar | Outcome |
|---|---|---|---|
| **G1** | Is the mechanism sound on one SUT? | Fires on a constructed lost-write; low, characterized FP | **PASS** |
| **G2** | Is the comparison against a competent, fair baseline? | Blind comparator frozen and calibration-accepted | **PASS** |
| **G3** | Does MIST catch a real defect the comparator misses because no human wrote the assertion, across ≥2 SUTs? | Head-to-head on a natural defect | **In progress** (results below) |

## 6. Results

- **Gate-1 (TrainTicket).** FIRE on a constructed lost-write in the high-confidence (observed-absent)
  stratum; synchronous FP = **0 / 2127** acknowledged benign records; observation gate 100% resolved.
- **Gate-2 (TrainTicket).** Both calibration faults flagged via **genuine state-clause failures** (not
  transport), while **every control leg passes every clause** — evidence the baseline discriminates rather
  than rubber-stamps.
- **G3 centerpiece — TrainTicket `cancel → refund`** (three cells, N = 5, deterministic):
  - *Natural* (dependency fault): both oracles detect (tie); MIST additionally **localizes** the specific
    lost write.
  - *Constructed* (fabricated clean acknowledgement): **clean MIST win** — the refund is a numeric balance
    delta (control `50 → 130` vs. fault `50 → 50`) the comparator's closed primitives cannot express
    (`NOT_CHECKABLE`).
  - *Agreement anchor* (body-carrying create): both catch — the comparator is demonstrably non-strawman.
- **G3 external validity — Sock Shop shipping enqueue-loss** (four cells = two fault strata × two comparator
  forms, N = 5, deterministic): MIST fires on all fault legs; the strongest *fair* comparator (extended with
  a liveness primitive) still misses the constructed cell — closing that boundary requires out-of-class
  broker/queue-state observation, i.e., MIST.
- **Breadth (Rider-2).** 69 / 80 (86.25%) of frozen state clauses are bindable/evaluable on live TrainTicket;
  the residual is exactly the object/aggregate/delta class MIST covers.
- **False positives.** 0 / 2127 (TrainTicket), 0 / 1200 (Sock Shop).

## 7. Scope and threats to validity

- Gate-1 establishes **synchronous-mechanism soundness on one SUT**; it carries no novelty evidence by
  itself. Novelty rests on the G2/G3 head-to-head.
- "Black-box" qualifies the **oracle** (judging), not fault injection (controlling), which is disclosed lab
  scaffolding.
- The head-to-head **defect logic is natural/upstream**, but the clean-win cell is *triggered* by a disclosed
  constructed fault. Fully injection-free wild-hunt evidence is the higher bar and remains in progress.
- Comparator fairness rests on **construct validity** (the closed primitive set representing the oracle
  class); the claim is scoped to the response/liveness contract-checking class, never "no tool could."
- Blind authorship used an LLM agent gated by the competence floor; a human cross-check is the stronger form.
- Read-back parsing covers three collection encodings; an unknown shape degrades **loudly**
  (`NOT_EVALUABLE` or an obvious FP storm), never a silent wrong verdict.

## 8. Status and next steps

**Complete and reviewed:** Gate-1 (PASS), Gate-2 (accepted), the TrainTicket `cancel → refund` head-to-head,
the Sock Shop external-validity head-to-head, the Rider-2 breadth survey, and the false-positive probes on
both SUTs. **Next:** an executable Rider-2 breadth run on live TrainTicket, then consolidation for write-up.
