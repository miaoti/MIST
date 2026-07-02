# G2 pre-registration — the Cast-delta paragraph + the fair-comparator protocol

**Status:** v2 2026-07-02 — rewritten after the prereg cold-review wave
([A — hostile PC](../research/REVIEW-PREREG-A-pc.md),
[C — methodology](../research/REVIEW-PREREG-C-methods.md); reviewer B covers the G3
sibling doc). v2 implements A-F1..F13 and C-pins 5, 6, 7, 8, 12. G2 per
[EXECUTION.md](../EXECUTION.md) = "novelty articulation + comparator, before scaling".
Sources: verified-Cast facts in [README §2](../README.md); comparator commitments in
README §6 + EXECUTION G2; B1+B2 consensus findings in
[REVIEW-B1B2-RECONCILIATION.md](../research/REVIEW-B1B2-RECONCILIATION.md).

---

## 1. The one-paragraph Cast delta (Gate-2 deliverable a) — v2

> We do not claim a new fault-injection technique, nor to be the first to detect
> masked or silent cross-service failures — Cast (ICSE-SEIP'26) already detects
> masked-2xx failures and silent dual-write inconsistencies, with 89
> developer-confirmed bugs. Cast achieves this by replaying production traffic
> through Java AOP instrumentation and checking phase-based metric thresholds,
> derived from historical trace baselines, at configured assertion points. Two of
> those ingredients — production traffic and the historical baselines derived from
> it — are unavailable by construction outside a production operator; the other two
> (language-specific AOP agents, per-site assertion-point configuration) are
> per-system costs that scale with fleet size and polyglot spread. None of the
> open-source systems we evaluate has production traffic or baselines. MIST
> substitutes weaker, cheaper inputs for all four: it generates its cross-service
> workload from the system's own traces and OpenAPI (no production traffic); it
> requires only the standard OpenTelemetry the system already runs, with no
> test-specific instrumentation (where a service does not export usable traces, the
> harness attaches the stock OTel javaagent — a deploy-time toggle, not a code
> change); it decides "acknowledged-but-lost write" with a label-free read-back
> differential — a single, generic per-run metamorphic relation (a 2xx-acknowledged
> write must appear on its own read-back), checked at declared write/read-back
> endpoint pairs, with no expected-outcome specification per check point, no metric
> thresholds, and no historical baselines; and its evaluation and labeled fault
> benchmark are open-source (vs Cast's closed evaluation). We quantify what the
> weaker assumptions cost and buy: the oracle's own measured false-positive rate on
> pre-registered benign traps, reported per SUT and per confidence stratum as an
> interval; and a head-to-head against a competently-configured, blind-authored
> per-endpoint assertion oracle (Filibuster-style) on the same faults — asking how
> often a competent engineer authoring from the spec alone fails to write the
> load-bearing assertion, and at what false-positive cost the label-free read-back
> closes that gap.

Hygiene rules bound to this paragraph:
- Never "first to detect"; the claim is the **combination** (generation + black-box
  OTel + label-free read-back + open benchmark) — accessibility + automation +
  measurement, not detection primacy (README §0/§8.5-6).
- "No test-specific instrumentation", never "instrumentation-free"; the javaagent
  disclosure stays IN the paragraph (review C-F3 / pin 8). Do not claim
  "any language": trace depth is polyglot-limited (Go legs) and the ack decoder has
  per-SUT preconditions (B1+B2 recon R5) — language-agnosticism is an argued design
  property, not a measured one.
- The metamorphic concession stays IN the paragraph (A-F10): the FIRE rule is a
  per-run metamorphic relation (Segura et al. territory); the delta vs
  Filibuster/Gremlin/metamorphic-REST is that the relation is fixed and generic
  (no per-site, human-specified expected outcome), and vs MINES that it is a
  state read-back, not response-invariant mining.
- "Declared write/read-back endpoint pairs" concedes the target-triple registry is
  configuration (A-F3). Threat-to-validity: today the triples are hand-picked
  (EXECUTION G0); the paper must either show they are derivable from the OpenAPI
  (write op + matching collection GET) or disclose hand-selection.
- Generation-vs-replay coverage is **argued, not measured** (README §8.5-4) unless
  measured head-to-head.
- Cast quantifier discipline: "89 developer-confirmed" (verified §2 wording — A-F12);
  never "Cast structurally cannot" (TOOL-PLAN §0 fact 6); "structurally miss"
  language binds ONLY to the blind comparator adjudication below, never to Cast
  (C-F11g).
- The paragraph's benchmark/eval clauses are **conditional-on-execution** (C2 at
  citable scale + C3 executed); PC acceptance now can only be conditional (A gate-fit
  note).

## 2. The fair-comparator protocol (Gate-2 deliverable b) — v2

**Comparator identity (pinned; C-pin 6).** A **blind-authored per-endpoint assertion
oracle, Filibuster-style** (fault injection + hand-authored assertions on the same
faults). The Cast metric-threshold pattern is **OUT** of the comparator: without
production-derived historical baselines any Cast approximation is nominal and invites
the "crippled comparator" charge (README §7; A gate-fit note). We say plainly the
comparator approximates Filibuster's oracle model, not Cast's pipeline.

**Blindness — re-based on enumerated, hash-frozen provisioning (A-F1; C-pin 7).**
Reveal-ordering cannot carry blindness: the Gate-1 fault list is already public in
this repo (target-triples.yaml, benchmark cases, prep docs). Therefore:
1. **Provisioning list (enumerated, frozen):** the assertion author receives ONLY
   (a) the SUT's upstream OpenAPI spec and (b) the SUT's upstream service
   docs/source at a pinned upstream commit (for TrainTicket: the FudanSELab tree,
   NOT the train-ticket-injection fork; for Sock Shop: the microservices-demo org
   trees). Explicitly excluded: this repo, the SUT fork, MIST outputs, the
   benchmark, and (for an agent author) web access. The provisioning manifest
   (paths + hashes) is committed with the assertion set.
2. **Author:** a fresh-context agent or engineer with no MIST exposure. For
   TrainTicket we additionally DISCLOSE that the lost-write fault class on TT is
   published research context; TT blindness therefore means "no access to MIST's
   fault list/verdicts/artifacts", not "fault-class-unaware". The undiluted blind
   claim attaches to faults/defects selected AFTER the freeze (G3) — injected Gate-1
   faults are calibration only (§ below).
3. **Endpoint superset (A-F6):** the author asserts over ALL write-path endpoints in
   the provisioned spec — never just the triple-registry endpoints. The head-to-head
   later restricts to the target subset.
4. **Frozen brief (A-F5):** the authoring brief's exact text is fixed here, now:
   > "For each state-mutating endpoint in this OpenAPI spec, write the
   > post-condition checks you would defend in code review as that endpoint's
   > success contract: what must be true of the system's observable state (via the
   > documented read endpoints) and of the response, when the call returns success.
   > Also write the checks for documented failure responses. Use only this spec and
   > the provided service documentation."
   Any change to the brief after this prereg is a disclosed protocol amendment.
5. **Freeze:** assertion set + brief + provisioning manifest committed (hash
   recorded) BEFORE the G3 eval-fault/defect list is finalized and before any
   comparator eval run.

**Competence floor + failed-calibration branch (A-F4; C-pin 6).**
- **Calibration set = the two public Gate-1 TT LOST_WRITE faults** (they are burned
  for blindness anyway — A-F1 — which makes them the correct acceptance set), kept
  DISJOINT from the G3 eval set by construction (G3 evaluates faults/defects chosen
  after the freeze).
- **Acceptance criterion:** the frozen set must flag both calibration faults'
  violated success contracts (a competent create-then-read contract catches an
  acknowledged-but-lost write; a set that misses both is demonstrably incompetent).
- **Failed-calibration branch (pre-registered):** the assertion set is NEVER edited
  post-hoc. If calibration fails, the brief may be improved (disclosed amendment)
  and a SECOND independent blind author commissioned with the improved brief; both
  sets ship; the eval uses the second. If that fails too → disclose and treat the
  comparator as infeasible on that SUT (drop the SUT from the head-to-head — never
  weaken/patch the comparator).
- MIST faces the same calibration: B2 must FIRE on both calibration faults (Gate-1's
  sensitivity), with recon-R3 join effects audited per-record so a masked FIRE is
  visible (A reconciliation note).

**Matched budget + operating points (A-F7; C-pin 12).**
- Same verified inputs, same endpoints, same fault strata, same SUTs, same run
  budget; **≥10 seeds** for generation-driven runs with Mann-Whitney U + Â₁₂
  (README §6 statistics bind the head-to-head).
- **Detection unit (pinned):** per injected-fault instance per triple per run-pair.
- **Operating points (pinned, no post-hoc subsetting):** MIST-strict =
  observed-gated FIREs only (primary); MIST-all = all FIREs (secondary, always
  reported with the gate histogram); comparator = the full frozen assertion set.
  "Matched recall" is operationalized as: report the full 2×2 detection table per
  operating point — never tune either tool to the other post-hoc.

**Adjudication (A-F8; C-pin 5).**
- **Symmetric miss tables:** every comparator miss AND every MIST miss gets a
  category. Comparator misses: no-assertion-existed / assertion-existed-wrong-signal
  / comparator-infra-failure. MIST misses: NOT_EVALUABLE(reason) / timeout-gated /
  read-back-completeness (recon R1) / join-masked (recon R3) / infra-failure.
- **≥2 raters, blind to tool identity** where the artifact allows, rating from the
  frozen assertion set + harness artifacts; **Cohen's κ reported** (the README §6
  stratum-3 standard). The MIST author never adjudicates alone.
- **Infra-failure evidence rule:** requires harness logs + one scripted rerun;
  otherwise the miss counts against the tool claiming it.
- Only "no-assertion-existed" counts toward MIST's decisive claim.

**Decisive-result definition (v2, A-F11).** Injected-fault wins (G2 calibration) are
**calibration evidence only** — the injected class is oracle-co-designed (benchmark
cases pre-declare it) and moves no PC. The PC-moving result is defined ONLY over
**real (non-injected) defects at G3**: a wild acknowledged-but-lost-write /
missing-compensation defect that (a) MIST FIREs on, (b) the frozen blind assertion
set does not flag, (c) ≥2 blind raters categorize as no-assertion-existed.

**Pre-registered outputs (A-F9 / recon R2 complete).** Per SUT, per stratum, per
operating point: the 2×2 detection table; MIST oracle FP as the **interval**
`[observed-gated/acked, fires/acked]` **plus the quiescence-gate histogram**, with a
PASS claim admissible only on a **non-trivial observed-gated denominator** and with
**Jaeger-health evidence** for the run (the Gate-1 traceparent precheck, per-SUT);
per-triple stats alongside aggregates (denominator = acked records, recon B-6);
comparator assertion count + authoring time (the cost axis); symmetric miss-category
tables with κ. Stratum naming uses "observed — not visible on read-back path"
wording (recon §4).

**What G2 does NOT decide.** G2 = the paragraph is PC-defensible (conditional on
execution) + the comparator is frozen and calibration-accepted. Whether MIST finds
REAL defects the comparator misses is G3; a thin G3 routes to Plan B (README §9).
G2 must not leak Gate-3 claims.

## 3. Execution checklist for G2 (cluster lifecycle pinned — C credit note)
Cluster lifecycle: **TrainTicket minikube stays up through G2 calibration → then
stopped → then the kind+Istio cluster for G3 SUTs** (single-box 26 GB budget;
gate1-infra-incident lesson).
1. Freeze this prereg v2 (post-reconciliation) → record commit hash here.
2. Provision + author the blind assertion set for ALL TrainTicket write-path
   endpoints (per §2 provisioning list); commit set + brief + manifest (freeze
   hash).
3. Build the comparator runner (Filibuster-style injection reusing B1's
   FaultInjector seam; assertion evaluation harness).
4. Calibrate BOTH tools on the two Gate-1 faults; apply the acceptance criterion +
   pre-registered branch on failure.
5. Record the §1 paragraph + comparator config in the paper repo.

*Review trail: v1 reviewed by 2 of 3 prereg cold reviewers (A hostile-PC, C
methodology; B covers the G3 sibling); v2 implements their G2-scoped findings
(A-F1..F13; C-pins 5-8, 12). Remaining known-open: the SUT-2/3 blind-set authoring
schedule lives in the G3 doc (C-pin 7); the G3 doc's own pins land after reviewer B
returns.*
