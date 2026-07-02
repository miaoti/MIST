# B1+B2 cold-review reconciliation (3 independent reviewers, 2026-07-02)

Reviews run per the ≥3-cold-reviewer rule, in parallel with Gate-1 run #3, no shared
context: [A — FP-freedom](REVIEW-B1B2-COLD-A-fp-freedom.md),
[B — FP-measurement + false negatives](REVIEW-B1B2-COLD-B-fp-measurement.md),
[C — implementation + contribution](REVIEW-B1B2-COLD-C-impl-contribution.md).
This doc reconciles them: consensus findings, triage, and the actions they drive.

**Headline:** all three independently confirm the core mechanism is sound for the
Gate-1 sync-CRUD path (isolation non-circular and race-safe at parallelism=1;
verdict() guards correct; batch inject/clear safe; arithmetic/curve correct; strata
never pooled). None found a defect that invalidates run #3. The real findings are
(i) two systematic threats to the *interpretation* of FP numbers, (ii) one shared
join weakness, and (iii) the known contribution gap (novelty back-loaded to G2/G3).

---

## 1. Consensus findings (found independently by ≥2 reviewers)

| # | Finding | Reviewers | Consensus severity |
|---|---------|-----------|--------------------|
| R1 | **Read-back completeness is an unstated precondition.** Collection-membership over whatever the list GET returns; no pagination/completeness check; control-before-fault + no teardown → monotonic accumulation. If the list ever truncates → systematic false FIRE in the HIGH-confidence stratum (trace complete, absence "observed"). | A-1 (CRITICAL, mechanism CONFIRMED / TT trigger PLAUSIBLE); C-P1-2 (adjacent: read-back HTTP status discarded → 5xx looks like absence) | **CRITICAL-if-triggered; must VERIFY TT list endpoints don't truncate + audit report bodies post-run** |
| R2 | **The ≤5% bar is a lower bound and Jaeger-dependent.** Numerator = observed-gated fires only; denominator = all acked. Timeout-gated fires (which the FIRE verdict still emits) are excluded → gate degradation lowers the governed rate without lowering the true FP rate; PASS possible with no caveat under partial degradation; observedGated==0 → vacuous PASS + caveat string only. | B-1, B-2 (MAJOR CONFIRMED); A-3 (MAJOR CONFIRMED); C-P2-5 (trivial-pass hole) | **MAJOR — report BOTH endpoints `[observedGated/acked, fires/acked]` + gate histogram; PASS claim only with a non-trivial observed-gated denominator and healthy Jaeger evidence** |
| R3 | **`pick()` positional join.** First acked+error-free record represents the triple; can mask a lost sibling behind a persisted sibling (false negative on the FIRE demo; deterministic at parallelism=1), and under parallelism amplifies R1/R2 into order-dependent verdicts. FP-rate unaffected (iterates all records). | A-2, B-5, C-P1-6 (all CONFIRMED mechanism) | **MAJOR for sensitivity claims — audit per-record data in the report, not just the pair verdict** |
| R4 | **OBSERVED_COMPLETE_ABSENT semantics are weaker than the name.** (a) No post-settle re-read: a write persisting during the 3s settle is still "complete-absent" (inflates FP numerator — conservative for PASS, spurious-FAIL risk). (b) Trace stability proxies *request completion*, not *durable absence* (read-path lag / stable-but-partial trace → wrong high-confidence absence). | B-3, B-4 (MAJOR CONFIRMED); C-P1-9 (partial-trace note) | **MAJOR for wording — call the stratum "observed-not-visible-on-read-path"; disclose both gaps** |
| R5 | **Ack rule + body conventions are TrainTicket-coupled.** `2xx ∧ status∈{null,1}`; `{status,msg,data}` envelope; uppercase-GET readback parsing unvalidated at registry load. Fine for the shipped targets; a portability precondition, not a Gate-1 bug. | A-4, C-P1-9 (both) | **MINOR — state as target preconditions; registry-load validation later** |
| R6 | **Station-pair strategy has SUT-health coupling + finite-pool caveats.** Exhaustion over long probes shrinks the denominator (self-protecting via the ≥20 floor); cross-run replication-lag collision can mask a lost write as OBSERVED_PRESENT (biases FP rate DOWN); baseline 5xx silently weakens usedPairs. | C-P1-8, B-7, B-9; A (sound-list caveat) | **MEDIUM — disclose; run #3 mitigated by 87-station catalogue + healthy topology** |
| R7 | **Single-threadedness is enforced by convention, not construction.** Pairing forces `mst.test.parallelism=1` (races defused today); nothing at the runtime layer refuses parallel arming. | C-P1-1; A/B (analyzed the races as latent) | **LOW today — add a beginRun guard in post-run hardening** |
| R8 | **Contribution: Gate-1 = soundness only; novelty evidence deferred.** Nearest prior art: Cast, Filibuster, Gremlin, metamorphic REST (Segura/Alonso), EvoMaster/RESTest. Defensible island = the combination (label-free + black-box membership + quiescence gate + trace stratification for acked-but-lost writes). A-ready only with G2 fair comparator + G3 ≥2 SUTs + ≥1 wild (non-injected) defect. | C-P2-1..5 (primary); plan's own §3.6 concession | **Strategic — matches the direction verdict; drives G2/G3 design** |

Unique-but-notable: C-P1-3 (clear-failure discards the whole report — records could be
persisted before the F2 throw; confirmed by run #2), C-P1-7 (partial triple coverage
proceeds silently — contacts leg), C-P1-10 (rollout-confirmed ≠ fault-activation-
confirmed; drop logic external, G0-validated only), B-6 (bar denominator counts
records, not iterations; aggregate pooled across heterogeneous endpoints — report
per-triple), B-8 (soft cap, disclosed), A-5 (strict-string membership normalization
precondition).

## 2. Impact on run #3 (executing now) — NO abort

None of the findings invalidates the run: R1 needs a truncating list endpoint
(unverified; TT admin list endpoints are naive findAll-style — verify post-run), R2/R4
change how the numbers must be *read and worded*, R3 only matters if fault-run
variants persist (which itself indicates injection failure — separately diagnosable),
R6 is mitigated this run (87 stations, healthy topology). Aborting and rerunning after
code changes would also unpre-register the bar. **Run continues; the findings sharpen
the audit.**

### Report-audit checklist (apply the moment run #3's JSON lands)
1. **R1/C-P1-2:** inspect `baselineBody`/`lastReadbackBody` of every fire and every
   NOT_EVALUABLE for error-shaped or truncated bodies; sanity-check collection growth
   (row counts across control→fault→probe) vs any suspicious absence late in the run.
   Post-run, verify on the live SUT that `getAllRoutes`/`getAllContacts` return
   unbounded lists (row count == writes issued).
2. **R2/B-6:** extract BOTH `fpRate` (raw) and `nonTimeoutGatedFpRate`; report the
   interval, the `gateHistogram` (need a non-trivial observed-gated denominator), and
   per-triple stats alongside the aggregate; state denominator = acked *records*.
   Jaeger health evidence: the pre-verified live traceparent lookup
   (gate1-preflight-audit.md) + in-run gate distribution.
3. **R3:** check `controlRecordCount`/`faultRecordCount`; if fault-run count > 1,
   confirm no persisted fault-run sibling is masking (or being masked); note the
   representative-record caveat in the verdict.
4. **R4:** word all absence verdicts as observed-not-visible-on-read-path; if the bar
   FAILs marginally, B-3 (no post-settle re-read) is a candidate inflator — check
   `elapsedMs` of FP fires near the cap.
5. **C-P1-7:** check which triples appear in `pairs[]` (contacts-leg coverage).
6. **C-P1-10:** if fault-run writes persisted (NO_FIRE "did the injected flag take
   effect?"), suspect activation-vs-rollout, not mechanism.

## 3. Post-run hardening candidates (NOT during run #3 — changing oracle semantics mid-gate would unpre-register the bar)

Priority order, each traceable to a consensus finding:
1. **R2fix:** `syncFpBar` → NOT_EVALUABLE (not PASS) when the observation gate is
   degraded (e.g. observedGated+observedPresent below a floor, or timeout-gated
   fraction above a cap); report the FP interval explicitly. Document as bar v2 —
   pre-registered before any run that uses it.
2. **R1fix:** read-back completeness assertion (paginate-to-exhaustion or row-count
   bound) + wire the available per-row DELETE as teardown; record read-back HTTP
   status on the RunRecord (C-P1-2).
3. **C-P1-3fix:** persist RunRecords/report BEFORE the F2 clear-failure throw.
4. **R3fix:** verdict-aware join (evaluate all records; a triple FIREs iff ≥1 record
   fires AND its control sibling persisted — or emit per-record verdicts).
5. **R4fix:** one post-settle read-back re-read before labeling
   OBSERVED_COMPLETE_ABSENT.
6. **R7fix:** `beginRun` refuses to arm when resolved parallelism > 1; registry-load
   validation of `readback_endpoint` (R5).

## 4. Paper-wording obligations (from all three)
- Bar claim: "≤5% *observed-gated* sync FP over ≥20 acked benign *records*", always
  paired with the raw rate + gate histogram; never let "N detections" mix strata.
- OBSERVED_COMPLETE_ABSENT = "request-complete, not visible on the read-back path".
- Preconditions: complete (untruncated) collection read-back echoing keys verbatim;
  TT-style status envelope (ack decoder); write-path SUTs with black-box read-back.
- Threats: SUT-side drop logic external (G0 manual validation); rollout≠activation;
  station-pair coupling to catalogue health; clear-failure report loss (until fixed);
  single-SUT/shallow-CRUD scope (G2/G3 carry novelty).
