# COLD REVIEW C (hostile A-venue PC) — step-1 C2/C3 freeze, adversarial read

**Reviewer stance:** hostile program-committee member at ICSE/FSE/ASE/ISSTA, cold read, no shared
context, charged to find the reject that sinks this and state whether it is pre-rebutted. Uncharitable
by instruction. Citations are `file:line` into the step-1 freeze set under `debug/a-main/c2c3/`.

---

## VERDICT (one line)

**BORDERLINE, leaning REJECT on the current framing** — the disclosure discipline is genuinely
above-median (Attack 4 is fully pre-rebutted, which is rare and creditable), but the artifact's
*motivating* fault class is, in its naturally-occurring instances, frequently accompanied by a
machine-readable tell in the acknowledgement itself, and the genuinely-silent cases are
disproportionately synthetic — a crack the schema does not measure and no floor protects. On the
literal "the **first** …" claim string I would argue reject; reframed around the study with the
Attack-2 fix, this is a credible-to-borderline Accept. I would not champion as written.

---

## The five attacks

### Attack 1 — "First" survives only as a 7-way conjunction; it leans on Cast's non-openness

**Reject rationale (one sentence):** "First open-source labeled benchmark for oracle evaluation on
masked-downstream faults" is a stack of seven qualifiers (`c2-claim-sweep.md:8-10`) engineered so that
the one industrial artifact validating the *exact* fault class at scale — Cast (ICSE-SEIP'26, 89
confirmed silent-2xx bugs) — is knocked out on Q1 (open-source) alone, i.e. the "first" is a
licensing accident, not intellectual priority.

**Status: PARTIALLY pre-rebutted; the Cast dependency stands OPEN.**
- Pre-rebutted parts are real and creditable: the sweep is broad (32 candidates, `c2-claim-sweep.md:13`),
  names the closest calls honestly (CloudAnoBench on the benign-stratum axis fails Q3+Q4,
  `c2-claim-sweep.md:63-71`; Uber Zenodo on the fault-class axis; AGORA+ on the purpose axis), and
  schedules proactive citations + a camera-ready watch-list (`c2-freeze.md:20-28`). The affirmative
  positioning — "prevailing benchmark methodology filters out exactly the stratum we label" (OpenRCA
  2.0 drops no-SLO-impact injections; FP-aware-TT discards 84.4% "No Anomaly", `c2-claim-sweep.md:27-28,
  51-52`) — is the single best framing move in the set and should lead.
- OPEN soft spots a hostile PC will press:
  1. **The Cast Q1 knockout is the whole ballgame and it is fragile.** `c2-claim-sweep.md:15-20,44`
     dismisses Cast purely because it "released nothing open," then re-uses Cast's own text as
     *motivation* ("HTTP 200 OK … despite the internal failure"). A Cast-aware reviewer flips this in
     one sentence: *Cast already established this class is real and industrial and found 89 confirmed
     bugs; your novelty over Cast is packaging + openness, which is an artifact contribution, not a
     scientific first.* "First **open-source**" is doing load-bearing work that a PC reads as a dodge.
  2. **The sweep's own admission is a tell:** "every candidate fails ≥2 qualifiers, most fail 4+"
     (`c2-claim-sweep.md:13-14`). Drop any single qualifier and a competitor matches (drop Q1 → Cast;
     drop Q3 → CloudAnoBench pairs positives+benign; drop Q4 → the RCA benchmark field). A conjunction
     that collapses on the removal of *any one* conjunct is the textbook "first-by-adjectives."
  3. **Filibuster (SoCC'21) — the most dangerous SE-venue competitor — gets no fresh row.** It is
     carried as "re-verified accurate" (`c2-claim-sweep.md:39`) with the dismissal living only in the
     plan (`c2c3-execution-plan.md:67`). Filibuster is a resilience-testing framework with an
     application corpus of exactly these microservice failure modes; a Filibuster-aware reviewer
     deserves (and the freeze does not give) a first-class differentiation.
  4. **Internal contradiction with the writing rule:** the plan's binding rule says lead with the study,
     "never discovery" (`c2c3-execution-plan.md:38-40`), yet the *frozen claim string* literally opens
     "the **first** open-source labeled benchmark…" (`c2-freeze.md:10`). The headline the PC reads is a
     "first" claim the authors have already been told not to lead with.

### Attack 2 — The differentiated fault class is, in its NATURAL form, often trivially detectable

**Reject rationale (one sentence):** The benchmark's flagship "masked / silent / acked-but-lost" class
— the entire reason an *oracle-evaluation* benchmark is warranted — leaves a machine-readable tell in
the acknowledgement in its natural instances (TeaStore's `-1` body; TrainTicket-natural's `{1,"error"}`,
which the authors' own G3 result grades a detection **TIE**), so a trivial body-content assertion
catches them, while the genuinely-silent, hard-for-any-oracle instances are disproportionately
synthetic (fabricated-ack fork flags) — meaning the class that motivates C2/C3 is thinner than claimed.

**Status: SUBSTANTIALLY OPEN — this is the strongest attack and it is handed to the reviewer by the
authors' own depth survey.**
- The tells are in the survey: `NonBalancedCRUDOperations.sendEntityForCreation` "silently returns
  `-1L`" on non-404/408 (`c2-depth-survey.md:42`); the internal-CRUD endpoint "returns 201 Created
  with body `-1`" (`c2-depth-survey.md:48`). The survey itself caps that tier "(breadth only, capped)"
  (`c2-depth-survey.md:54,145`) — i.e. the authors *know* the `-1`-body case is trivially detectable —
  but capping is not the same as excluding it from the positive stratum or recording *why*.
- The tell recurs in MIST's own flagship evidence: `g3-result.md:70` grades TrainTicket-natural a
  **detection TIE** precisely because "the envelope msg gate flags `{1,"error"}`." So even the
  authors' headline natural case is body-detectable; the only **clean** oracle-eval wins in the entire
  G3 record are the *constructed* fabricated-ack fork flag and the *injected* broker policy
  (`g3-result.md:71,73`). The truly-silent, discriminating cases are substantially synthetic.
- **The schema measures the wrong visibility axis.** `oracle_eval.trace_visibility` is
  `error-span-visible | span-presence-visible | trace-invisible` (`c2-freeze.md:76`) — a *trace* axis.
  There is **no response-body / ack-content visibility field**: nothing records whether the 2xx
  acknowledgement itself carries a sentinel (`-1`, `{1,"error"}`) that a body assertion trivially
  catches. For a masked-**2xx** benchmark this is the more damning axis, and it is unmeasured.
- **It corrupts the scoring contract, not just the motivation.** `c2-freeze.md:164-174` scores oracles:
  a genuine case's fault leg "should FIRE (else a false negative)." A body-tell case will FIRE a trivial
  `body != -1` oracle → the benchmark *credits* a trivial oracle on those cases → they do not
  discriminate oracle designs → they are non-discriminating filler in a benchmark whose entire purpose
  (per the claim string) is *oracle discrimination*. An oracle-eval benchmark cannot count
  everyone-catches-it cases toward its positive stratum.
- **Rubric tension left unresolved:** the "genuine" definition requires "2xx **or a success-shaped
  body**" (`c2-freeze.md:128-131`). A `-1` or `{1,"error"}` body is *not* success-shaped — so either
  those cases fail the rubric's own genuine bar (and the natural flagship shrinks), or "success-shaped"
  is quietly stretched to admit sentinels (and the class is trivially detectable). The freeze does not
  pick.
- Weak pre-rebuttal only: the survey caps the `-1` tier and `g3-result.md` is honest that natural =
  tie. But there is **no `ack_tell`/content-visibility schema field, no accounting of how many S1
  positives are body-detectable, and no floor requiring cases that are jointly natural + success-shaped
  + tell-free + trace-invisible.** The `≥6 acked-but-lost across write-path SUTs` floor
  (`c2-freeze.md:184`) can be satisfied entirely by constructed and/or body-tell cases.

### Attack 3 — TraceAnomaly re-scope excludes the one learned baseline by armchair verdict; "3 frontier" are same-family

**Reject rationale (one sentence):** The spike re-scopes the only ML/learned baseline (TraceAnomaly)
from "matched-recall competitor" to "construction-blindness demonstration" on an *unrun, docs-only*
verdict (`r4-comparator-spike.md:5-9,43-61`), dropping the pre-registered "≥4 baselines" to three
comparators that are all trace-span-shaped (naive span-error, Tracetest span-error, Tracetest
span-presence) — i.e. one tool in two configs plus a naive restatement of the same error-span idea —
so a PC eats a "weak/strawman baselines" reject.

**Status: PARTIALLY pre-rebutted; the same-family + missing-oracle-class problems stand OPEN.**
- The technical argument is plausible, even likely correct: TraceAnomaly keys on unseen call paths +
  latency likelihood, and a masked write returns normal-shaped with ordinary timing → structurally
  blind (`r4-comparator-spike.md:53-61`). And the narrowing is disclosed with a stop-and-replan floor:
  "narrows honestly to '3 frontier trace comparators + a construction-blindness result'… Still ≥3"
  (`r4-comparator-spike.md:64-67`; `c2c3-execution-plan.md:49`).
- OPEN attacks:
  1. **Armchair exclusion.** The spike states plainly "no installs yet" and the verdicts are
     "decidable NOW from each tool's own artifacts" (`r4-comparator-spike.md:5-9`). Excluding your only
     learned baseline from competition based on *your reading of its mechanism*, before running it, is
     a strawman-by-assertion. The empirical confirmation run is deferred to step 2.5/6
     (`r4-comparator-spike.md:70-71,86-88`) — so at *freeze* the "it can't compete" claim is an
     assertion, and a hostile PC will not grant it.
  2. **The three survivors are ~1.5 ideas, not three baselines.** Arm 1 (naive error span) and arm 2
     (Tracetest error span) are the same error-span oracle; arm 3 is the same tool (Tracetest) in a
     presence config (`r4-comparator-spike.md:14-17`). "3 frontier comparators" reads to a PC as one
     trace tool + a naive twin.
  3. **The comparator suite is entirely trace-span-shaped — precisely the family blind to MIST's
     differentiator.** There is no invariant/contract oracle (AGORA+, the sweep's *own* nearest
     "labeled dataset for REST oracle eval", `c2-claim-sweep.md:47,70`), no Pact/Dredd contract oracle
     (which the authors themselves name as the real comparator class in `g3-result.md:112`, R-SS-2),
     and no differential/metamorphic oracle. Choosing only comparators that are blind-by-construction to
     your read-back differentiator is the anti-tautology failure the plan claims to guard against
     (`c2c3-execution-plan.md:48`). This is the crux of the "weak baselines" reject and it is not
     addressed.

### Attack 4 — "6 SUTs" is really 4 write-path SUTs, two of them demos; S1≥45 is mechanism-multiplexed

**Reject rationale (one sentence):** Headline breadth "6 SUTs" collapses to 4 write-path SUTs — two of
which (TeaStore, OTel-Demo) are vendor/reference demos with no saga depth — and the S1≥45 floor is
padded by counting injection-mechanism variants of the same defect site as distinct cases plus
re-implementing an existing single-SUT fault corpus.

**Status: the "4 not 6" half is FULLY pre-rebutted; the depth/padding half is PARTIALLY open.**
- Pre-rebuttal is exemplary and should be kept front-and-center: the matrix states it as "the single
  most important non-uniformity — 'on the 6 SUTs' must never imply six write-path SUTs"
  (`e-sut-applicability-matrix.md:23-25`); Boutique/Bookinfo are explicitly excluded from the
  write-path class "rather than quietly under-filled" (`c2-depth-survey.md:151-153`). This is the honest
  way to ship it.
- OPEN residue:
  1. **4 write-path SUTs = 1 research-grade SUT + 3 demos.** SS/TeaStore/OTel-Demo are demo apps; the
     survey concedes TeaStore's masked write is "masked-sync-CRUD, not saga depth. **TT keeps the depth
     story.**" (`c2-depth-survey.md:58`). So *depth* rests on a single SUT (TrainTicket). "Breadth" is
     largely reference apps.
  2. **Mechanism-multiplexing inflates the case count.** The normative quota table counts
     `placeorder→order-row × {maintenance-toggle, DB-down, mesh-abort}` as three S1 cases
     (`c2-depth-survey.md:145`) — three *injection mechanisms* reaching **one** unchecked-`-1` swallow
     site. The survey's own "depth honesty" note admits "all pairs hang off ONE user flow"
     (`c2-depth-survey.md:58`). The distinct-defect-**site** count across all SUTs is far below 45.
  3. **Inconsistent granularity vs C3.** C3 M-yield clusters by "endpoint × fault-signature × SUT"
     (`c2c3-execution-plan.md:158`), which would collapse exactly these mechanism-variants — but that
     clustering is not applied to the C2 S1 count. The benchmark multiplexes for size and clusters for
     prevalence. A PC will ask for a distinct-site denominator.
  4. **A large slice of S1 is a re-implemented existing corpus:** TT's contribution is "F-corpus ≥6 +
     G1/G3 reviewed cases" (`e-sut-applicability-matrix.md:39-41`), and the F-corpus is
     "replicate-by-description" of train-ticket-fault-replicate's 22 faults
     (`c2-license-audit.md:22-23`). Re-implementing a known single-SUT corpus is legitimate but is not
     the "new labeled benchmark" novelty the count implies.

### Attack 5 — Study-not-dataset: the increment over Uber/RCAEval is thin and may collapse to "benign-dominance"

**Reject rationale (one sentence):** C3's load-bearing claim — "first genuine-vs-benign measurement of
masked-2xx" (`c2c3-execution-plan.md:26`) — is a benign/genuine split measured as a
*detector-conditioned lower bound* on 6 demo/research SUTs under synthetic workloads with S3 possibly
`< 20` cases, i.e. a thin, synthetic, small-N increment over Uber's ~1.4M **production** traces, and the
plan itself pre-registers a fallback where the study collapses to "benign dominance."

**Status: PARTIALLY pre-rebutted by honesty; the thin-increment reject stands OPEN.**
- Self-aware pre-rebuttal: the plan calibrates to "**credible-to-clear**, decided by execution quality"
  and cites RCAEval landing at a WWW'25 *companion* track as the cautionary precedent
  (`c2c3-execution-plan.md:23-24`); it pre-registers (not retrofits) the benign-dominance branch as
  "itself the load-bearing finding" if S3 ≈ 0 (`c2c3-execution-plan.md:31-34`). Pre-registration is the
  right move and blunts the "post-hoc spin" charge.
- OPEN attacks:
  1. **The increment over Uber is thinner than stated.** Uber already reports that 29% of 2xx carry
     hidden **non-fatal** errors (`c2-claim-sweep.md:45`) — "non-fatal" is a benign-flavored split.
     "First genuine-vs-benign of **masked-2xx**" narrows to the qualifier again, on a sample orders of
     magnitude smaller and synthetic where Uber is production.
  2. **Estimand fragility.** The wild measure is a "DETECTOR-CONDITIONED LOWER BOUND"
     (`c2c3-execution-plan.md:170`) with `min(all, 40)` sampling and "`< 20` wild flags ⇒ the scarcity
     IS the finding" (`c2c3-execution-plan.md:176`). A PC reads: the study may return almost no genuine
     wild defects and pivot to "most masked-2xx is lived-with" — a plausible but modest finding on demo
     apps, weaker than a production prevalence result.
  3. **The load-bearing leg depends on an unsolved recruitment problem.** C3 precision rests entirely on
     blind human raters (`c3-rater-materials.md:15-31`), which is "the longest-lead item"
     (`c3-rater-materials.md:3`) with an **OPEN** recruitment channel (`c3-rater-materials.md:136`) and a
     two-author-blind fallback that "partially undoes the §6 central fix" (`c3-rater-materials.md:149-153`).
     A study whose headline can degrade to author-adjudicated κ is a study a PC discounts.

---

## THE single strongest reject rationale + recommended pre-rebuttal

**Strongest reject (Attack 2):** *An oracle-evaluation benchmark for a "masked / silent" fault class must
be built from cases that no trivial oracle can catch; but the authors' own depth survey and G3 record
show the naturally-occurring instances of the class routinely carry a machine-readable tell in the 2xx
acknowledgement (TeaStore `-1`, TrainTicket `{1,"error"}` → graded a detection TIE), while the only
clean, discriminating oracle-wins in the whole evidence base are constructed fork-flag / injected-policy
cases. The per-case schema records trace-visibility but has no response-content-visibility axis and no
floor guaranteeing cases that are jointly natural, success-shaped-acked, tell-free, and trace-invisible —
so the paper cannot demonstrate that its differentiated, oracle-eval-motivating class is anything more
than a handful of natural cases plus constructions, and the benchmark's own scoring contract would credit
a trivial body-content oracle on the tell-cases.* This is the strongest because it undercuts the
motivation for **both** C2 and C3 simultaneously, it is sourced from the authors' *own* freeze artifacts
(not reviewer speculation), and it is the least defended (no field, no floor, unresolved rubric tension).

**Recommended pre-rebuttal (converts the attack into a measured, disclosed axis — cheap, at freeze):**
1. **Add a per-case `ack_content_visibility` (or `ack_tell`) schema field** parallel to
   `trace_visibility` (`c2-freeze.md:76`): `{success-shaped-clean, sentinel-in-body,
   status-field-tells}`. Freeze it now so population records it for every S1 case.
2. **Report the joint distribution** natural×tell-free×trace-invisible, and **segregate body-tell cases
   out of the primary S1 positive denominator** exactly the way `underspecified` cases are segregated
   (`c2-freeze.md:117,171`) — they are non-discriminating for oracle eval and inflate the count.
3. **Add a floor** (alongside `c2-freeze.md:184`): ≥N S1 positives that are jointly *natural* (not
   fork-flag), *success-shaped-acked*, *tell-free*, and *trace-invisible* — the cases that actually
   justify a read-back oracle and an oracle-eval benchmark. If that N is small, that is itself the honest
   finding and should be stated (it also *strengthens* the "prevailing methodology filters this out"
   positioning: the class is rare *and* hard *and* under-served).
4. **Resolve the rubric tension** at `c2-freeze.md:128-131`: state explicitly whether a sentinel/status
   body (`-1`, `{1,"error"}`) counts as "success-shaped." Either decision is defensible; leaving it
   implicit is not.

With that fix, the class becomes a *measured* spectrum (from trivially-tell-detectable to
trace-invisible) rather than an asserted monolith, and the reject "your silent class isn't silent"
converts into a contribution ("we are the first to *quantify* how detectable this class actually is").
