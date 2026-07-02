# MIST → A-conference: the next contribution (plan v7 — v4 base, review-hardened through round-6 cold review, 2026-06-30)

> **Purpose.** Concrete, citation-grounded plan for taking MIST from a tool-demo to a top-venue
> (ICSE/FSE/ASE/ISSTA) paper: what to build, what to change, what to evaluate, and — stated frankly —
> whether it is achievable. Backed by five research investigations (`research/01..05`), a 3-reviewer
> A-conference simulation (`research/REVIEW-R1..R3-*.md`), and **primary-source verification** of the
> decisive competitor (Cast). **v4 supersedes v3 after all three reviewers returned Weak Reject; it does
> not paper over that — it fixes what is fixable, reframes the novelty around *verified* facts, and states
> the residual risk honestly (§0, §9).** Every nontrivial external claim is cited.

---

## §0 TL;DR — the honest state (read this first)

**Three independent A-conf reviewers returned Weak Reject on v3**, converging on: (a) the novelty is a thin
increment over **Cast** (ICSE-SEIP'26); (b) the headline oracle's ground truth is circular/confounded;
(c) the guaranteed contribution is below-A, with everything hinging on finding real bugs that may not exist.
All three independently said *"more SUTs/baselines/stats will not help — the contribution is the problem."*

**Primary-source verification confirms the core risk but also pins the real, defensible deltas.** I read
Cast in full (arXiv:2602.00972). Cast already detects masked-2xx failures *and* silent dual-write
inconsistency (89 dev-confirmed bugs) — so **MIST cannot claim to be first to detect masked or
silent cross-service failures.** But Cast's oracle is *not* specification-free (it uses phase-based
metric-threshold criteria from historical traces + configured "granular assertion points"), and Cast
*requires production-traffic replay* (it admits missing bugs when traffic doesn't cover the path) and
*Java AOP instrumentation* (Java-only, Huawei-Cloud-only evaluation). **MIST's genuine, verifiable deltas:**
generation-based (no production traffic; actively drives the vulnerable paths Cast cannot cover),
black-box and language-agnostic (no AOP), a *label-free read-back data-correctness* oracle (vs Cast's
metric thresholds + assertion points), and an *open OSS benchmark* (vs Cast's closed eval).

**What this means.** The honest contribution is **accessibility + automation + an open benchmark**, with a
*modest* mechanism delta — not a fundamentally new analysis. That is **borderline** for an A-venue research
track. Critically, the reviewers' own "path to Accept" is **empirical** (find real data-integrity bugs that
assertion-based tools miss because no human wrote the assertion) — which **a plan cannot guarantee; only
executing the build can.** So this plan's job is not to manufacture a unanimous "accept" on paper (it
cannot, honestly); it is to (1) fix every fixable methodological flaw, (2) specify the cheapest path to the
one make-or-break empirical result, and (3) lay out the strategic decision in §9 that is genuinely the
team's to make.

**The reframed one-line claim (honest, defensible — recast per cold-review from a stacked-"first" to a
capability + benchmark claim):**
> *MIST makes silently-masked cross-service failures and — on **write-path services with a black-box
> read-back** — **acknowledged-but-lost writes** testable **without production traffic and without AOP /
> per-service assertions**, using only the OTel a system already runs plus a label-free read-back differential
> oracle; together with an **open-source labeled benchmark** of such faults and a measurement of how often they
> are genuine defects.*
>
> **Scope honesty (cold-review B — do not overclaim):** the masking oracle is broad (any OTel SUT) but is the
> **non-novel** part (Cast/Microusity already detect masking); the read-back lost-write oracle is the
> differentiated part but applies **only** to the write-path subset (§4 item 6), **not** "any OTel system." No
> stacked-"first" — the accessibility + open-benchmark combination is the claim, not primacy of detection.

---

## §1 What MIST is today, and the hard constraints (code-verified, `research/01`)

Black-box REST API test generator for microservices; trace = generation input + assertion target. Ships:
Root API Mode, Sniper Strategy (1 input-fault/variant), Trace Shape Oracle (headline:
`HiddenDownstreamFailure` — entry 2xx, deeper span 5xx). 4 SUTs. Constraints any plan MUST respect:
1. **Observer, not controller.** MIST controls only request inputs; *no* SUT fault-injection hook exists
   today. Adding it is MODERATE but makes MIST an **opt-in grey-box controller** — frame as a mode.
2. **Signal floor.** Cross-SUT, an oracle may use **only** status + topology + `otel.status_code`/Envoy
   `response_flags`. Exception text/method names = TrainTicket-only; bodies = entry-only/runtime-only.
3. **Corpus floor.** "Learned" invariants today bootstrap from **one** trace; statistical FP control is
   blocked-by-data until a real known-good corpus exists.
4. **No wild bug corpus** (≈0 reproducible). Use injected/replicated faults + cited prevalence.
5. **Attribution ceiling = service-level** (honest; param-level is information-limited).

---

## §2 Honest novelty positioning (verified against primary sources)

**Concede what is taken.** Masked/silent cross-service failure *detection* is **not** unclaimed:
- **Cast** (Chen et al., ICSE-SEIP'26, arXiv:2602.00972) — *verified verbatim*: detects when a *"REST API
  call still returns an HTTP 200 OK… despite the internal failure,"* and the *"Dual-Write… highly
  susceptible to silent but critical inconsistency bugs."* Oracle = *"phase-based performance criteria…
  derived from historical trace data"* checked at *"granular assertion points."* Needs **production-traffic
  replay** and **Java AOP instrumentation**; evaluated **Huawei-Cloud-only**; **89 dev-confirmed** bugs.
- **Microusity** (ICPC'23) — reports backend sub-request errors not visible at a BFF edge; but **BFF-only,
  Zeek port-mapping** (not OTel), exception-string oracle, evaluated by an **8-person user study**.

**State MIST's deltas precisely (each is verifiable, none is "first to detect"):**

| Axis | Cast (ICSE-SEIP'26) | MIST | Is the delta defensible? |
|---|---|---|---|
| Workload source | **Production-traffic replay** (admits missing uncovered paths) | **Generated** cross-service inputs that actively drive vulnerable paths | **Accessibility/setting delta, NOT research-novelty** (cold-review B): generation-vs-replay is a paradigm choice — it enables no-production-traffic SUTs (a real accessibility win) but "reaches paths replay can't" is **argued, not measured** (R2 R5) and would merely confirm the expected even if measured; carries **no novelty weight** |
| Instrumentation | **Java AOP agents**, Java-only | **Black-box** standard OTel, language-agnostic | **Yes** — substantive accessibility delta |
| Oracle | Metric-threshold (success/latency/throughput) at configured **assertion points** + historical baselines | **Label-free read-back data-correctness** differential (no thresholds, no assertion points, no baselines) | **Partial** — a different, cheaper oracle; honestly "automating an assertion" (metamorphic), not a new analysis |
| Evaluation | Closed (Huawei Cloud) | **Open OSS** SUTs + **released labeled benchmark** | **Yes** — reproducibility/benchmark contribution |
| Data-integrity scope | Detects via assertion points | Detects via black-box GET read-back differential | **Partial** — overlapping target, different (weaker-assumption) mechanism |

**Other prior art (positioned, cited):** Filibuster (SoCC'21, DOI 10.1145/3472883.3487005) needs developer
assertions + an existing test suite. Filibuster-DB (Assad et al., ICSE-Companion'24, DOI
10.1145/3639478.3640021) injects DB faults but ships an **IDE visualization plugin, not an automated
label-free oracle** (we make only this abstract-supported claim — the stronger "they named it an open
problem" framing is **dropped as unverifiable**). Gremlin (ICDCS'16), LDFI/Molly (SIGMOD'15), MicroFI
(TDSC'24), FastFI (arXiv:2601.14800), ChaosMachine (TSE'21) all need assertions / a correct-outcome model /
white-box. AGORA (ISSTA'23, DOI 10.1145/3597926.3598114) and AGORA+ (TOSEM'25) mine **single-response**
invariants (note: AGORA's ISSTA'23 eval reports ~11 bugs; the larger confirmed-bug count belongs to
AGORA+ — cite each precisely, do not conflate). TraceAnomaly/Nezha/TraceRCA = operational anomaly/RCA, not
test oracles. MINES (ICSE'26, arXiv:2512.06906) = label-free invariant inference, evaluated on **five**
systems (Train-Ticket, NiceFish, Gitea, Mastodon, NextCloud) → pre-empts any "learning" headline (a reason
the learning pillar is dropped, §3).

**Survey-level gap that still stands** (the framing anchor): across the 92-paper TOSEM'23 REST survey,
oracles are overwhelmingly status/schema; "automated oracles" and "classifying test results" are named
**open**, and **no surveyed REST-testing paper asserts on the distributed trace** [Golmohammadi, Zhang,
Arcuri, TOSEM'23, DOI 10.1145/3617175]. MIST's contribution lives at the *intersection* of that open REST
oracle gap and the (now-occupied) resilience-testing space — its defensibility is the **black-box +
generation + open-benchmark** combination, not the masking idea itself.

---

## §3 Contribution stack (honest weighting)

- **C1 — A black-box, generation-driven, *no-test-specific-instrumentation* resilience/masking *capability* +
  a label-free read-back differential oracle.** ("No test-specific instrumentation" — **NOT** "instrumentation-
  free": it relies on the standard OTel the system already runs, which IS instrumentation — cold-review B / R1
  MAJOR 1.) The novelty is the *setting and automation* (no production traffic, no AOP, no assertions, any
  language), plus the metamorphic read-back oracle for acknowledged-but-lost writes. *Honest weight: modest
  mechanism novelty (Cast caps it); real accessibility novelty. Alone it is an SEIP/empirical contribution, not
  a research-track-A mechanism.*
- **C2 — The first OPEN-SOURCE labeled benchmark** of masked-downstream / data-integrity faults across N OSS
  microservice systems, with an adjudication rubric (Cast ships a *closed* 48-bug benchmark; scope the claim
  to open + OSS). *Honest weight: the most durable, citable asset and the floor-raiser (R1: "a citable
  benchmark regardless of mechanism simplicity").*
- **C3 — A defect-yield / detection study (+ a bounded prevalence estimate)**: primarily, of the masked-2xx
  events MIST surfaces, how many hide a *genuine* defect vs a lived-with non-fatal error (**defect-yield +
  oracle precision** — most masked-2xx on generated OSS workloads are MIST-*induced*, so this is NOT
  ecosystem population-prevalence — cold-review E), and how much status/schema/assertion oracles miss.
  **Population-prevalence** is claimed ONLY from the **stratum-3 adjudicated *wild* sample** — where "wild" =
  naturally-occurring masked-2xx under a realistic **un-faulted** workload on the OSS SUTs (**NOT** production
  traffic, which these demos lack — cold-review H), whose size + source are **pre-registered** (benchmark §8 / §6). *Honest weight: the external-validity backbone; framed as
  the DEFECT subset, never Uber's 29% (not a defect rate — §6).*

Supporting only (not headline): FP-controlled invariant mining (if corpus captured; near-twin MINES
exists); service-level attribution (honest ceiling).

**Primary A-path (committed — cold-review B).** The plan does **not** rest A-worthiness on the Cast-capped
*mechanism* novelty of C1. The guaranteed, **Cast-independent** research contribution is **C2 (open labeled
benchmark) + C3 (defect-prevalence study) + C1 (the accessible capability with a *measured* oracle-FP)** — an
**empirical/benchmark research paper**, for which ISSTA/ASE/FSE are A-venues and which Cast does **not** cap
(Cast is closed and ships no prevalence study). **Gate-3 is the upside, not the floor:** real
acknowledged-but-lost-write / missing-compensation defects that a competent assertion oracle misses would
*additionally* clear the mechanism-novelty research-track bar — high-value but admitted-uncertain (§9). For C2
to be a genuine floor-raiser it must reach **citable scale** (cf. RCAEval's 735 cases — the seed of 4 cases is
a start, not the deliverable; R3 R6). So the honest bar this plan targets is: *a clear empirical/benchmark-track
A, with a high-variance shot at a mechanism-novelty-track A via Gate 3* — not a guaranteed mechanism-novelty A,
which the verified Cast overlap forecloses.

---

## §4 Headline mechanism + the soundness protocol (fixing R3's FATAL)

**The differential data-integrity oracle.** For a state-mutating request *R* on resource *X* with persisting
dependency *D*: run *R* without a fault (read back → `S_control`), run *R* with one injected fault on *D*
(read back → `S_fault`); **[gated / S1 mode ONLY — superseded by the two-mode refinement below]** fire iff
the fault-run client response is 2xx/"success" AND the *D* span errored/aborted AND the success-contract is
violated (acknowledged-but-not-persisted write, skipped compensation, orphaned/partial state).

**[Refined post-REVIEW2 — `TOOL-EXECUTION-PLAN.md` §3 B2.3 is the authoritative spec.]** The D-error conjunct
above is now the **gated (high-confidence / S1)** mode ONLY. The **headline pure-differential mode drops it**:
fire = a 2xx/"success" run that acknowledges entity X but lacks X on its OWN read-back, with the control run
as a false-positive guard. Dropping the conjunct lets the oracle also fire on **skip-persist (S2: D is never
called → no D span)** and keeps read-back **independent of the trace** (the §6 de-circularization precondition,
per REVIEW2-R2 §2a, which flags the D-error conjunct as re-importing trace-conditioning). The gated mode needs
a real errored D span (Toxiproxy/S1) and is validated at Gate 3. **Implementers: build the two-mode rule, not
the single gated rule above.**

**Why a naive version is unsound (R3, verbatim concern): "the diff is a race, not an invariant."** Late saga
compensation, async/CQRS writes, shared mutable state across two mutating runs, and retries all confound a
naive read-back diff — and MIST would false-positive on exactly the async-*benign* cases it must beat. Cast
itself only *acknowledges* this ("state contamination… even a previous passing test could subtly alter
system state"). MIST must do better. **Mandatory protocol (this is a build requirement, not a footnote):**
1. **Per-test isolation.** Fresh entity/account/tenant per test (unique IDs); no shared mutable key across
   the control and fault runs. Where a SUT forbids isolation, use a dedicated namespace + reset between runs.
2. **Quiescence before read-back.** Do not diff until the workflow is quiescent: poll the read-back endpoint
   until the value stabilizes OR the trace shows all causally-related spans completed (trace-driven
   quiescence), with a bounded timeout. Classify *eventually-consistent-then-correct* as **benign**.
3. **Late compensation handling.** Wait a bounded window for saga/compensation completion (detected via the
   trace) before deciding "missing compensation"; distinguish *pending* from *missing*.
4. **Idempotency / determinism.** Use idempotency keys; normalize volatile fields (timestamps, IDs) before
   the diff.
5. **MEASURE the oracle's own error.** Report the read-back oracle's FP/FN rate **under async load** on the
   benign-trap stratum (§6). This characterized FP rate is a deliverable, not an assumption.
6. **Scope honestly.** The data-integrity oracle applies **only** where a clean black-box read-back exists
   and isolation/quiescence are achievable — i.e., **write-path REST SUTs** (TrainTicket, TeaStore, Sock
   Shop, spring-petclinic). It does **not** apply to read-only/derived/gRPC-internal demos (Bookinfo,
   Online Boutique, OTel Demo) — those carry only the *masking* oracle. **Design the eval so ≥3 SUTs
   exercise the data-integrity oracle** (directly answering R2/R3's "single-SUT" MAJOR).

---

## §5 What to build (tool upgrades; effort + feasibility from `research/01`)

| # | Build | Effort | Feasibility | Priority |
|---|---|---|---|---|
| B1 | **Opt-in fault-injection mode**. **Gate-1 backend = SUT-flag injector** (`LOST_WRITE` / S2, smoke-proven; it is ground-truth scaffolding, **not** a tool dependency — TOOL-PLAN B1.1). Toxiproxy/mesh/DB-proxy faults (S1, errored-D) **deferred to G3** (Istio aborts are L7, **not** app-aware DB aborts — R3) | L | MODERATE; grey-box mode | **P0** |
| B2 | **Differential data-integrity oracle + the §4 soundness protocol** (control/fault pairing, isolation, trace-driven quiescence, read-back diff, measured FP) | M-L | HIGH for the diff; the soundness protocol is the real work | **P0** |
| B3 | **Generalize masking oracle + Envoy `response_flags`** | S | HIGH | **P1** |
| B4 | **Independent-label harness** for the precision study: derive required-vs-optional dependency and intended-degradation from spec/docs/code, *blind to MIST's predicate* (fixing R2's circular GT — §6) | M | MODERATE | **P1** |
| B5 | **FP-controlled invariant mining** (only if corpus captured) | M | blocked-by-data | P2 |
| B6 | Trace-shape fitness for input-only elicitation (secondary, ~0 yield) | M | LOW | P3 |

Order: B1→B2 (the spine) → B4 (de-circularize the eval) → B3 → capture corpus → B5.

---

## §6 Evaluation (fixing R2's circular ground truth + the single-SUT MAJOR)

**The independent ground truth (this is the central fix).** The read-back data-correctness differential is
an oracle **independent** of the masking signal (it checks *state*, not *which span errored*). Use it two ways:
1. **As ground truth** to score the cheap structural masking oracle — breaking the circularity (the label
   does not come from the masking predicate).
2. For the masking-precision study where no read-back exists, the genuine-vs-benign label is set from an
   **independent intended-behavior standard applied BLIND to MIST's verdict**: required-vs-optional
   dependency + designed-degradation derived from the **API contract / SUT docs / source**, by raters who do
   **not** see MIST's structural predicate or output. The κ-adjudicated rubric must **not** reuse MIST's
   signals. (Directly answers R2 W1.)

**Strata.** (1) *Positives by construction*: TrainTicket F1–F22 [DOI 10.1109/TSE.2018.2887384]; OTel-Demo
fault flags; controlled injection with a **known** failing dependency — for these the data-integrity label
is *true by injection*, not by MIST. (2) *Benign traps*: Bookinfo `reviews→ratings`, optional deps,
retry-then-succeed, **eventually-consistent writes** — the cases that test the §4 protocol's FP rate.
(3) *Adjudicated real-traffic sample*: ≥2 raters, blind, pre-registered rubric, Cohen's κ.

**Experiments.** E1 generation/coverage + faults-by-own-oracle (calibrate baselines as strong: EvoMaster
mandatory + RESTler/Schemathesis/Morest/AutoRestTest; template = "No Time to Rest Yet," ISSTA'22). E2 oracle
precision/recall/FP vs **non-zero trace-aware comparators** (naive span-error oracle, Tracetest with a
generic assertion, TraceAnomaly/TraceRCA) — headline = **precision/FP at matched recall**, *measured on the
hard async-benign cases* (R3), never "N-vs-0." E3 trigger rate. E4 **defect**-prevalence with CIs. E5
ablations (A1 remove benign-filter; A2 remove generation; A3 trace-input vs spec-only). E6 release the
benchmark. Stats: ≥10 seeds, Mann–Whitney U + Â₁₂ [Arcuri & Briand, STVR'14], Holm/Bonferroni, pre-registration.

**SUTs.** Core 6: TrainTicket, TeaStore, Sock Shop, OpenTelemetry Demo, Online Boutique, Bookinfo;
data-integrity oracle on the write-path subset (TrainTicket/TeaStore/Sock Shop/+petclinic stretch); masking
oracle on all. Stretch: DeathStarBench (oracle-only; thin REST — disclosed).

**The Cast/Filibuster comparator (R1+R2 demand).** Run a **competently-configured** assertion-based
comparator on the SAME injected faults — Filibuster-style fault injection with hand-authored assertions, and
(where feasible) the Cast oracle pattern (metric thresholds + assertion points). Show the **specific defects
MIST catches that the assertion-based oracle misses because no human authored that assertion** — this is the
*only* evidence that moves a reviewer off "you automated an assertion" (R1). If it cannot be produced, the
novelty claim fails (§9 Gate 3).

**Bug story (honest).** Do **not** claim wild counts rivaling EmRest (226) or Cast (89). Report:
injected/replicated positives + adjudicated defect-prevalence + the released benchmark + **any incidental
developer-confirmed bugs** (target ≥2, the Morest bar). State plainly that wild trace-only swallowed-bug
corpora are structurally unobtainable.

**Uber framing fix (R3).** Uber's 29.35% [DOI 10.1145/3700436] is the prevalence of *swallowed non-fatal
errors* — **not a defect rate** (Uber gives no benign/defect split; many are lived-with). Cite it as
evidence the *phenomenon* is pervasive; our contribution is measuring the *genuine-defect* subset via the
independent standard — a **new** measurement, not a reproduction.

---

## §7 Obtainability risks (`research/05` §6)

HIGH: deployable traced SUTs; runnable REST baselines (EvoMaster/RESTler/Schemathesis). MED: uniform OTel +
gateway across polyglot SUTs; baselines needing specs on thin-spec SUTs; trace-aware comparators. **LOW ⚠
(the two that dominate):** the genuine-vs-benign label (mitigated by the §6 independent-blind protocol +
released corpus — turns a risk into a contribution) and **developer-confirmed wild bugs** (structurally
unobtainable — designed around, never claimed). New for v4: a competently-configured **Cast/Filibuster
comparator** (MED — effort to set up assertion-based baselines fairly; but mandatory for the novelty defense).

---

## §8 Decision gates (go/no-go)

- **Gate 1 (build):** B1+B2 run end-to-end on TrainTicket; the §4 protocol fires on a *constructed* lost
  write and the read-back FP rate on the benign-trap stratum is measured and low. *No → mechanism unsound.*
- **Gate 2 (novelty, before committing):** the verified Cast deltas (generation / black-box / read-back /
  open) are articulable in one paragraph a skeptical PC accepts, AND a competently-configured assertion-based
  comparator is set up. *No → headline collapses to the benchmark+prevalence paper (§9 Plan B).*
- **Gate 3 (THE empirical gate):** B2 finds ≥1 **real** acknowledged-but-lost-write / missing-compensation
  defect on a real SUT that a status/schema oracle **and** a hand-asserted Tracetest/Cast-style oracle miss,
  reproduced across **≥2 SUTs**. *Yes → C1 is real; full eval. No → §9 Plan B.*
- **Gate 4 (scale):** ≥4 baselines on ≥6 SUTs produce the E2 precision frontier. *No → narrow honestly.*

---

## §8.5 Round-2 review outcome + pre-registration commitments (2026-06-30)

v4 was re-reviewed by three fresh cold A-conf reviewers (novelty / evaluation / soundness). **All three moved
from Weak Reject (v3) to Borderline**; the evaluation reviewer rates the *methodology axis* at Accept and
independently verified every load-bearing Cast claim verbatim. Convergent conclusion (= §9): the
methodological FATALs are fixed; the cap is the primary-source-verified novelty overlap with Cast; only
executing **Gate 3** converts Borderline → Accept. Residual concerns are non-fatal but **binding commitments**
for execution and writing:

1. **Underspecified-case protocol (top residual, soft-circularity).** Many OSS demos do not document intended
   degradation. Pre-register a resolution rule for "intended behavior unknown" cases; report the
   underspecified fraction; report masking-precision **both including and excluding** underspecified cases.
   Raters must not resolve silence by inferring from runtime/trace behavior (that re-imports MIST-correlated
   signal — the one place a PC will still push on ground truth).
2. **Read-back FP per-SUT + async coverage.** Report the read-back oracle's measured FP/FN **per SUT** (not
   pooled), and disclose per SUT the trace coverage of async write paths (uninstrumented queues/batch/CQRS
   defeat trace-driven quiescence and contaminate the GT). Soundness is empirically conditional on these.
3. **Data-integrity depth, not just count.** Pre-specify, per write-path SUT, the concrete
   saga/dual-write/compensation site the oracle targets and how many genuine acknowledged-but-lost-write
   opportunities each presents (TrainTicket has rich sagas; TeaStore/Sock Shop are shallower CRUD — count ≠ depth).
4. **Replay-coverage delta = argued, not measured.** "Generation reaches vulnerable paths Cast's record-replay
   cannot cover" is architectural; either measure vulnerable-path coverage head-to-head or frame it explicitly
   as argued-not-measured.
5. **Soundness threats-to-validity (disclose).** Trace-driven quiescence degrades to a wall-clock timeout when
   cross-broker OTel span-links are absent/broken; black-box per-test isolation fails on shared-inventory SUTs
   (TrainTicket seats, Sock Shop stock) → use a dedicated namespace + reset; "integrity" is one GET, not a
   cross-service snapshot; Toxiproxy/DB-proxy faults are TCP/connection-level, not application-aware DB aborts.
6. **Claim hygiene (done in v4 text):** "instrumentation-free" → "no test-specific instrumentation"; "first
   open" → "first open-source" benchmark; MINES = five systems (stronger pre-emptor of the dropped learning
   headline); single defensible comparative-"first", not stacked adjectives.

None of these requires new research; none changes the §9 verdict. They are what turns a Borderline plan into a
*pre-registered, reviewer-hardened* one before any code is written.

## §9 Frank verdict, the bet, and the strategic decision (the real output)

**Verdict (after 3 reviewers + Cast verification):** This is a **borderline** A-venue direction. The novelty
is a *setting/accessibility + open-benchmark* contribution with a modest metamorphic-oracle delta over Cast —
real, but not a leap. **No amount of plan-writing changes this**, because the reviewers' objection is to the
idea's novelty ceiling, and their unanimous path to Accept is **empirical** (Gate 3 bugs). Therefore:

- **The plan cannot be made to "pass all reviewers" on paper as a mechanism-novelty paper** — and cold-review
  (round-4 reviewer B) sharpens this into a **split by track**: on a **pure mechanism-novelty research track**
  the guaranteed floor (no Gate-3 credit) is **Weak Reject** (Cast caps the mechanism); on an
  **empirical/benchmark track (ISSTA/ASE/FSE)** the same guaranteed floor is a **clear Accept** (C2 benchmark +
  C3 prevalence are Cast-independent). So the honest label is **"clear empirical/benchmark-track A + a
  high-variance mechanism-novelty-track A via Gate 3,"** NOT a flat "Borderline." The v4/v5/v6 fixes address
  all three methodological FATALs (lifting the *methodology* to Accept); the residual is the *mechanism*-novelty
  ceiling, which only Gate 3 lifts. **This is why §3 commits the primary A-path to the empirical/benchmark leg,
  with Gate 3 as upside — not the reverse.**

**Fallback ladder (unchanged in spirit, sharpened):**
- **Plan A** (this doc): C1 capability + C2 benchmark + C3 defect-prevalence, **iff Gate 3 yields real bugs**.
- **Plan B** (Gate 3 thin): lead with the **open benchmark** + **defect-prevalence** + the **black-box/
  no-traffic/no-AOP capability** demonstrated on injected faults with measured oracle FP and a fair
  assertion-based comparator. Honest, publishable, plausibly a strong ASE/ISSTA or empirical-track paper;
  weaker on mechanism novelty.
- **Plan C** (Gates 1–2 fail): not A-grade; consolidate as an extended tool / short / empirical paper.

**The strategic decision (genuinely the team's call, because it changes what we build next):**
1. **Bet on execution** — build B1+B2 and *run Gate 3* on TrainTicket/TeaStore/Sock Shop. This is the only
   path to a clear A accept, and it is high-variance: it pays off **only** if real lost-write/missing-
   compensation defects exist in these OSS SUTs and assertion-based tools miss them. ~4–6 weeks to a
   go/no-go signal. **Recommended if the team can absorb the variance** — it is also the cheapest way to
   *falsify* the idea before over-investing.
2. **Reframe to a venue that rewards the real contribution** — the black-box/no-traffic/no-AOP accessibility
   delta + open benchmark is a strong fit for an **empirical / benchmark-dataset MAIN track (ISSTA, FSE,
   ICSE) — a full research paper, NOT SEIP** (cold-review E: a labeled benchmark + a blind-adjudicated defect
   study is a legitimate main-track dataset/empirical contribution, a stronger A than an industry track),
   where it is a **credible-to-clear** accept — **"clear" conditional on C2 released at the benchmark §8 scale
   + C3 executed** (cold-review H: until then *credible*, not yet *clear*) — rather than a borderline
   research-track bet.
3. **Pivot the core idea** — accept that masked-failure *detection* is now occupied (Cast/Microusity) and
   look for a less-crowded core (e.g., a genuinely new *generation* objective, or a different fault class).
   This is the honest implication of "the contribution is the problem," but it discards MIST's built assets.

**Bottom line to a skeptical PC (the one sentence — harmonized with the canonical §0 claim, cold-review H):**
*We do not claim a new fault-injection technique or to be first to see masked failures; we claim a black-box,
generation-driven capability needing **no test-specific instrumentation** (beyond the OTel a system already
runs) that makes silent cross-service failures testable on any OTel system — and, **on write-path services
with a black-box read-back**, acknowledged-but-lost writes — without production traffic or authored
assertions, together with the **first open-source labeled benchmark** of such faults and a measurement of how
often they are real defects.* Whether that clears the bar is decided by Gate 3, not by this document.

---

## §10 References

(Carried from v3 with v4 corrections.) Motivation: Uber SIGMETRICS'25 DOI 10.1145/3700436 (29.35% =
swallowed non-fatal errors, **not a defect rate**); Yuan OSDI'14. Surveys/REST: TOSEM'23 DOI
10.1145/3617175; "No Time to Rest Yet" ISSTA'22 arXiv:2204.08348; EvoMaster TOSEM'19 / ASE'24 / vision
arXiv:2603.02551; RESTler ICSE'19; Morest ICSE'22 DOI 10.1145/3510003.3510133 (44 bugs, 2 confirmed); EmRest
ISSTA'25 DOI 10.1145/3728964 (226); AutoRestTest **ICSE'25** arXiv:2411.07098; LlamaRestTest FSE'25 DOI
10.1145/3715737; RESTest ISSTA'21 DOI 10.1145/3460319.3469082; Metamorphic-REST TSE'18. Oracles: AGORA
ISSTA'23 DOI 10.1145/3597926.3598114 (~11 bugs) / AGORA+ TOSEM'25 (do not conflate counts); MINES ICSE'26
arXiv:2512.06906. Resilience/FI: **Cast ICSE-SEIP'26 arXiv:2602.00972 (verified — production-traffic replay
+ Java AOP + metric-threshold/assertion-point oracle; 89 confirmed; Huawei-only)**; Filibuster SoCC'21 DOI
10.1145/3472883.3487005; Filibuster-DB ICSE-C'24 DOI 10.1145/3639478.3640021 (DB FI + IDE visualization —
the "named-it-open" quote is **dropped as unverifiable**); Gremlin ICDCS'16; LDFI/Molly SIGMOD'15 DOI
10.1145/2723372.2723711; MicroFI TDSC'24 DOI 10.1109/TDSC.2024.3363902; FastFI arXiv:2601.14800; ChaosMachine
TSE'21 arXiv:1805.05246; LFI DSN'09. Trace/RCA/bench: Microusity ICPC'23; Tracetest (CNCF); TraceAnomaly
ISSRE'20; TraceRCA IWQoS'21; Nezha FSE'23 DOI 10.1145/3611643.3616249; RCAEval **WWW'25** arXiv:2412.17015.
SUTs: TrainTicket TSE'18 DOI 10.1109/TSE.2018.2887384; DeathStarBench ASPLOS'19 DOI 10.1145/3297858.3304013;
TeaStore MASCOTS'18; OTel Demo / Online Boutique / Sock Shop / Bookinfo / spring-petclinic-microservices
(repos). Stats: Arcuri & Briand STVR'14 DOI 10.1002/stvr.1486. Microservice issues: Waseem et al.
arXiv:2302.01894. Internal: `research/01..05`, `research/REVIEW-R1..R3-*.md`, `archive-2026-06-01/probe-*.md`.
("Lobrest" from old deep-research is unverifiable → dropped.)

---

*Status 2026-06-30: plan hardened v4 → v7 through five review rounds; round-5 (on v6) and round-6 (on v7)
each returned **3/3 cold-reviewer OVERALL SATISFIED**, with the primary A-path re-anchored to the
Cast-independent empirical/benchmark leg (+ Gate-3 upside). The three FATALs are fixed; the residual is the
idea's mechanism-novelty ceiling, which only Gate 3 (execution) can lift. NEXT: the §9 strategic decision is
the team's — execution of B1/B2 remains BLOCKED until the user says "yes".*
