# REVIEW-UX-C — hostile A-venue PC cold read of the tool-story (mist-ux-design.md + step2-execution-checklist.md)

**Reviewer charge:** attack the tool-story the paper will tell (oracle-as-product, automation gap,
precision defaults, observe-vs-paired, checklist-as-skeleton). Cold read; no shared context.
**Files reviewed:** `debug/a-main/c2c3/mist-ux-design.md`, `debug/a-main/c2c3/step2-execution-checklist.md`;
context `debug/a-main/c2c3-execution-plan.md` §1/§3.2/§4, `debug/a-main/c2c3/c2-freeze.md` §1–§5,
`debug/a-main/c2c3/r4-comparator-spike.md`. Code citations independently verified against
`mist-cli/src/main/java/io/mist/cli/MistRunner.java`, `.../fault/DataIntegrityRuntime.java`,
`.../writer/MultiServiceRESTAssuredWriter.java`, `.../resources/My-Example/*.properties`.

## VERDICT (one line)

**BORDERLINE-LEANING-REJECT on the tool-story as currently worded** — the honesty scaffolding (D5
symmetry, §6 self-questions, plan §3.2 non-inheritance) is unusually good, but the design doc
contradicts its own pre-registration on FP inheritance, omits the Jaeger prerequisite that
structurally gates the ONLY failing verdict, and the checklist as written manufactures a benchmark
where MIST structurally false-negatives an entire SUT; each is fixable by pre-registered amendment
NOW, none is fixable in a rebuttal.

Credit where due (so the fixes land on the right substrate): the design doc's §1 code citations all
CHECK OUT on cold verification (`MistRunner.java:103,324-337` registry gate; `DataIntegrityRuntime.java:31`
hooks-never-throw; `MultiServiceRESTAssuredWriter.java:772-804` trace-shape Allure UX; the bundled
`trainticket-demo.properties` indeed carries no B2 keys while the eval configs
`trainticket-gate1-pairing.properties:482-498` do). The gap statement (`mist-ux-design.md:28-29`) is
accurate and brave. That is exactly why the residue below matters: the remaining holes are the ones
the authors did NOT see.

---

## Attack 1 — the symmetry attack ("your automation-gap criticism applies verbatim to you")

**Reject rationale:** the paper's stick for arm 3 — "hand-authored per test / per endpoint … there is
no generation of them from a spec" (`r4-comparator-spike.md:51-54`, promoted to "the automation-gap
datum" at `c2c3-execution-plan.md:196-198`) — describes MIST's own triples registry verbatim today
(`mist-ux-design.md:24`: "user, entirely by hand | we hand-authored TT + SS; nothing proposes them").

**Pre-rebutted?** HALF. The prose pre-rebuttal exists and is the right shape:
`mist-ux-design.md:74-77` (D5): "record per SUT: # triples, authoring minutes (proposed-accepted vs
hand-written vs expert), proposal acceptance rate. Reported NEXT TO arm-3's per-endpoint Tracetest
authoring cost. If our cost is nontrivial, we say so — the differentiator is what the config BUYS
(state-level verdicts), not that it is free." Plus the standing rider `step2-execution-checklist.md:122-123`
("MIST vs arm-3 authoring-cost symmetry"). But three holes make it lipstick as instrumented:

1. **Schema asymmetry.** The frozen per-case schema mechanizes the COMPARATOR's cost
   (`c2-freeze.md:104-107`: `comparator_configs[].authoring_cost: {minutes, endpoints_covered, notes}`)
   while MIST's own config carries NO cost or tier field (`c2-freeze.md:91-95`:
   `config_provenance: {mist_properties, triples, timeout_caps, mist_commit}`). The released artifact
   will structurally record the baseline's price per case and the tool's price in a side spreadsheet
   (W5 "capture template", `mist-ux-design.md:86`). A PC reads that as asymmetry-by-construction.
2. **Unit mismatch.** D5 records per-SUT minutes + an acceptance RATE; arm 3 records per-ENDPOINT
   minutes. As specified, the two columns of the "symmetric" table are incommensurable — and the
   choice of units flatters MIST (a percentage vs a cost).
3. **The acceptance rate is a feature bullet, not an evaluated claim.** W3's DoD
   (`mist-ux-design.md:84`) unit-tests the proposer to reproduce the authors' OWN hand-written TT/SS
   triples — the heuristics are tuned on the answer key. The step-2.75 "measured" acceptance
   (`step2-execution-checklist.md:60-64`) is then authors-accepting-proposals on specs the authors
   wrote (TeaStore + OTel-Demo OpenAPI "pre-registered as authored" — `c2c3-execution-plan.md:187-188`).
   Author-as-user on author-authored specs is an insider CEILING, not an automation result.

**Fix (concrete):** (i) disclosed amendment (`c2-freeze.md` §6) adding
`config_provenance.mist_authoring: {tier: proposed-accepted|hand|expert, minutes}` per case — the
symmetry must live in the frozen schema, not prose; (ii) pre-register the common unit: minutes per
covered write-endpoint, same clock protocol, same author class, BOTH arms, insider-ceiling caveat
attached to both; (iii) scope the paper's tool claim to **"state-level verdicts once a triple is
bound"** — the word "automatic" appears only next to the measured acceptance number and its ceiling
caveat; (iv) say the true differentiator out loud: on the deep class the comparator's cost is not
large-but-finite, it is **inexpressible** (span-land has no durable-state assertion; the frozen blind
contract found all three state postconditions NOT_CHECKABLE) — an expressiveness gap, not an
automation gap. Conflating the two is what makes this attack land.

---

## Attack 2 — the "expert tier" dodge ("the headline capability requires hand-configuration")

**Reject rationale:** the paper's deepest results (TT cancel→refund value-delta + supplied-isolation,
the G3 clean-win centerpiece) run on config D2 declares un-proposable, and the flagship write is a
**GET** (`/cancelservice/cancel/{orderId}/{loginId}`) that the "each 2xx write (POST/PUT)" heuristic
(`mist-ux-design.md:58-59`) cannot even ENUMERATE — so the automatic tier covers exactly the region
where the paper's own agreement-anchor cells show the comparator ties, and the win region is
hand-made.

**Pre-rebutted?** HALF. Disclosure exists: `mist-ux-design.md:64-66` — "Expert modes stay manual BY
DESIGN (value-delta probes, supplied-isolation for bodyless writes — the TT cancel class): not
heuristically proposable; documented as the expert tier. This is the same expressiveness the paper
prices as depth — disclosed, not hidden." And W3's DoD honestly hard-codes the negative ("proposes
NOTHING for the cancel class", `mist-ux-design.md:84`). But disclosure is not PRICING: nothing
measures the expert-tier FRACTION of S1 acked-but-lost cases, no result table is stratified by
config tier, and the sharpest form — "everywhere automatic, tied; everywhere winning, hand-made" —
is stated nowhere, so the PC gets to say it first.

**Fix (concrete):** (i) the per-case `tier` field from Attack 1 makes the fraction a frozen,
reportable number; (ii) report headline benchmark recall stratified by tier (proposed-accepted /
hand / expert) — if the win class is 100% expert, print it; (iii) price the flagship in the paper:
"N minutes of expert configuration buys a verdict class NO comparator arm can express at any cost"
(cite the NOT_CHECKABLE finding) — that sentence, with a real N, converts the dodge into a priced
trade; (iv) cheap product counter: extend the proposer to ENUMERATE GET/DELETE side-effect
candidates as explicitly un-proposable TODO stubs (it cannot bind them, but it CAN surface them) —
turning "the tool can't see the class" into a measurable surfacing-coverage number; (v) wording rule:
the capability claim is "automatic for the body-carrying CRUD subclass (acceptance X/Y), expert-priced
for the delta subclass (N min/case)" — never bare "automatic".

---

## Attack 3 — the precision story under product defaults ("what false-alarm rate does a REAL user eat?")

**Reject rationale:** `failOnLost` **default true** (`mist-ux-design.md:45`) generalizes an FP-0.0
that was measured on ONE SUT, in PAIRED mode, at specific caps (poll 500 ms / timeout 10 s / settle
3 s — `trainticket-gate1-pairing.properties:488-493`) to arbitrary user systems where none of it was
measured.

**Pre-rebutted?** MOSTLY OPEN — and the design doc contradicts its own pre-registration. The plan is
honest: `c2c3-execution-plan.md:164-166` — "both FP-CALIBRATED ON THE S2 STRATUM BEFORE S3 SAMPLING;
their FP profiles are **NOT inherited from the paired-mode zeros** (those are scoped to paired/probe
modes)." The design doc says the opposite: `mist-ux-design.md:38-39` — "the product **inherits a
calibrated precision story**, and C3's S2-FP calibration covers exactly this mode." The first clause
is false under the plan's own rule; if that sentence migrates into the paper, the PC refutes it with
the authors' own pre-registration. The second clause is the honest bridge, but it under-delivers
three ways: (a) the calibration (step 5) POSTDATES shipping the default (step 1.9); (b) it covers six
benchmark SUTs under pinned workloads, not "THEIR system"; (c) nothing pins the S2 calibration to run
at PRODUCT-DEFAULT caps — `config_provenance.timeout_caps` is per-case (`c2-freeze.md:94`), so tuned
per-case caps can pass calibration while the shipped default is never measured.

**The undisclosed structural half (verified in code, feeds Attack 5):** the only failing verdict is
trace-gated. `DataIntegrityRuntime.java:44-46`: absence upgrades to OBSERVED_COMPLETE_ABSENT "only
when the step's own Jaeger trace (exact W3C traceparent id) is present with a stable span set —
otherwise it stays TIMEOUT_ABSENT"; `:998-1015`: the check reads `jaeger.base.url` and on any failure
logs "staying timeout-gated". So: **no Jaeger ⇒ the product's headline oracle can never fail a test**
(100% "persistence unconfirmed" warnings — vacuous precision), and on traced systems the real FP
vector is materialization latency > the 10 s default cap with a complete trace (`:592-630`: cap
reached + traceComplete + post-settle re-read absent ⇒ fires). The design's user-input table
(`mist-ux-design.md:20-26`) lists spec + base URL + auth + triples — **the trace backend is not
mentioned as a user prerequisite anywhere in either document**. The S2 stratum must therefore contain
a slower-than-default-cap eventual-consistency benign trap per async SUT (OTel-Demo's
kafkaQueueProblems pending-vs-missing path is the obvious candidate) or the default is uncalibrated
against its most likely real-world false alarm.

**Fix (concrete):** (i) delete/replace the "inherits" clause at `mist-ux-design.md:38-39`; (ii)
pre-register the default-decision rule: ship `fail` as default ONLY if S2 observe-mode FP at
PRODUCT-DEFAULT caps is 0 across write-path SUTs, else default `warn` (adopt §6 Q1's off/warn/fail
knob — the design already floats it at `mist-ux-design.md:97-98`); (iii) require the S2 calibration
to run (or additionally report) product-default caps; (iv) report the TIMEOUT_ABSENT fraction per
SUT under defaults — that is the number a real user eats, and hiding it invites "your oracle mostly
says 'unconfirmed'"; (v) disclose the Jaeger prerequisite in the user journey AND the paper's tool
claim (see Strongest Reject).

---

## Attack 4 — observe-vs-paired ("the tool users get is not the tool you evaluated")

**Reject rationale:** every accepted headline number to date (G1 FP-0.0, all G3 cells) was produced
by the paired control/fault injection harness, while the product is a single-leg observe mode that
**does not exist in the codebase** — the only wired execution path today is
`executePairedDataIntegrity` (`MistRunner.java:550,624-627`); grep finds no observe/single-leg mode
anywhere in `mist-cli`.

**Pre-rebutted?** MOSTLY, in prose — the bridge exists and is the right one:
`mist-ux-design.md:32-35` ("Default product mode = OBSERVE (wild, single-leg) … identical to C3's
single-leg wild detector; the paired executor stays an eval-harness behind its existing flag"), D3
(`:67-68`), plan instrument (ii) (`c2c3-execution-plan.md:163-164`), and the freeze's per-leg scoring
contract (`c2-freeze.md:188-193`) is already observe-shaped. But the bridge is ASSERTED, not
MECHANIZED, and three concrete gaps let the attack through:

1. **No `oracle_mode` field** in the frozen schema and no invariant that benchmark MIST verdicts are
   produced in observe mode — mode heterogeneity inside the headline table is currently legal.
2. **The promoted G1/G3 seed cases collide with a frozen invariant.** They carry paired-mode verdicts
   from pre-freeze commits (`step2-execution-checklist.md:65,71-72`), while `c2-freeze.md:145` demands
   "`oracle_eval.config_provenance.mist_commit` IDENTICAL across every case." Import the old verdicts
   and the invariant is violated; re-run and the numbers may drift from the reviewer-accepted G3
   results of record. Either way an undisclosed seam — the checklist's "Seed-case migration …
   validator PASS" (`:65`) says nothing about re-running.
3. **The step-8 commit-pin criteria predate the UX wave.** `step2-execution-checklist.md:112-113`
   pins "value-delta + supplied hooks + injectors + fabricated-ack in one buildable commit (B-m3)" —
   observe mode / W1–W4 are NOT in the criteria, so followed as written the frozen study commit may
   exclude the very product mode the paper describes.

**Fix (concrete):** (i) add `oracle_mode: observe|paired` to `config_provenance` + invariant: every
case counted in product-facing precision/recall tables ran OBSERVE mode at the study commit; (ii)
re-run the promoted seed cases single-leg at the study commit (cheap — one leg each) and keep the G3
paired cells as the separately-labeled class-boundary demonstration, which the plan's own writing
rule already licenses ("the G3 head-to-head cells are ~10 seed cases of the benchmark plus a
class-boundary demonstration — never discovery", `c2c3-execution-plan.md:36-39`); (iii) amend the
pin criteria to include W1–W4; (iv) add one machine-checked equivalence pin: observe mode replayed
on a paired run's control leg reproduces that leg's verdict stream (same `DataIntegrityRuntime`
path) — that single test converts "identical to C3's detector" from assertion to invariant.

---

## Attack 5 — checklist as paper skeleton (results the paper cannot honestly use)

**Reject rationale:** executed as written, checklist 2.5.3's TeaStore Kieker exclude branch
(`step2-execution-checklist.md:48-49`) produces a benchmark in which MIST **structurally cannot
fire** on TeaStore's 4–5 S1 sites — because the tool's own decisive verdict is trace-gated
(Attack 3), a dependency both documents treat as a comparator-arm concern only.

**Instances, each with status:**

- **5a (OPEN, blocking): the TeaStore collision.** 2.5.3 tags un-instrumented cases
  `trace-uninstrumented` to protect E2 comparator fairness — but `DataIntegrityRuntime.java:600-630`
  shows MIST's OWN absence upgrade needs the Jaeger trace; no backend ⇒ TIMEOUT_ABSENT forever ⇒
  under the frozen scoring contract ("genuine fault leg should FIRE (else FN)", `c2-freeze.md:190-192`)
  MIST eats FNs on every TeaStore S1 case — on the SUT the depth survey crowned the second wholly
  natural masked-write exhibit. The tempting quiet repair (relax the trace gate) silently destroys
  the G1 FP-discipline inheritance, because the gate IS the discipline. Neither doc sees the dilemma.
  **Fix (pre-register NOW, before step 2):** either (1) instrumentation is a MIST-oracle prerequisite
  too — un-instrumented cases score `NOT_EVALUABLE`-by-instrumentation in a bucket parallel to
  `none-durable` (`c2-freeze.md:143-144`), disclosed as a tool prerequisite; or (2) build AND
  S2-calibrate a non-trace absence-upgrade variant (K consecutive decisive complete 2xx reads across
  a settle window) as a SEPARATE disclosed mode with its own FP row. Never a silent gate change.
- **5b (OPEN): the FIRE-mapping gap.** `artifacts.mist_verdict ∈ {FIRE, NO_FIRE, NOT_EVALUABLE}`
  (`c2-freeze.md:128`) has NO frozen mapping from the QuiescenceGate strata
  (`DataIntegrityRuntime.java:77-86`): is TIMEOUT_ABSENT a NO_FIRE or a NOT_EVALUABLE? Where do
  readback-error records land? Left unfrozen, the mapping is a post-hoc degree of freedom directly
  under the headline recall/precision numbers — a hostile PC calls that results-tuning surface.
  **Fix:** freeze the gate→verdict mapping table as a §6 disclosed amendment BEFORE population.
- **5c (HALF pre-rebutted): the E1/M-yield juxtaposition.** Configured-MIST (pre-paid triples,
  author-authored specs, demo-tuned properties per D4) vs out-of-box baselines at the same 1 h clock
  (`c2c3-execution-plan.md:155-160,185-193`; `step2-execution-checklist.md:88-90`) — MIST's authoring
  minutes are excluded from its budget while baselines have no pre-payable config at all. D5 +
  budget-matching pre-rebut the direction; adequate ONLY if the yield table carries the authoring
  cost inline, labels the comparison "configured MIST vs out-of-box baselines," and adds one
  sensitivity row amortizing authoring minutes into the budget.
- **5d (wording): the out-of-the-box demo is the SUT the authors pre-configured.** D4
  (`mist-ux-design.md:69-73`) ships bundled TT triples so "the FIRST run … already shows the
  data-integrity section" — true only for TrainTicket. The paper must not let the demo imply
  zero-config generality; one sentence fixes it.
- **5e (wording): 1.9.4's protocol** (`step2-execution-checklist.md:24-25`) measures OUR authoring
  minutes with author-as-user — label the datum as the insider ceiling it is (see Attack 1(3)).

---

## THE SINGLE STRONGEST REJECT + FIX

**The undisclosed trace-backend dependency of the only failing verdict** (Attack 3's structural half
× Attack 5a). In PC words: *"The product's sole defect verdict requires the write's own Jaeger trace
to be present and stable (`DataIntegrityRuntime.java:44-46,600,998-1015`); neither the UX design's
what-the-user-provides table (`mist-ux-design.md:20-26`) nor the execution checklist mentions a
trace backend as a prerequisite; on un-traced systems the headline oracle is inert (every loss is a
non-failing 'unconfirmed' warning under `failOnLost=true`), on the benchmark's own TeaStore exclude
branch the tool structurally false-negatives an entire SUT — and the paper simultaneously charges
instrumentation burden against the trace-family comparators whose arms are gated on the very same
step-2.5 instrumentation. The symmetry attack, in its most damaging concrete form, is hiding inside
your own quiescence gate."*

It is the strongest because it is structural (code-level, not wording), currently invisible in BOTH
reviewed documents, undermines the product story and the benchmark scoring at once, and its cheapest
repair (relaxing the gate) silently invalidates the FP-0.0 inheritance the whole precision story
stands on.

**Fix (all pre-registrable this week):**
1. Disclose the dependency everywhere the user journey or the paper states the capability: the tool
   claim becomes **"state-level verdicts once a triple is bound AND the write path is
   trace-instrumented"** — which is also the honest symmetric footing for the E2 chapter (MIST's
   decisive verdict and the trace arms share the instrumentation prerequisite; MIST's differentiator
   is what it checks WITH the trace present, i.e., durable state, not span shape).
2. Add the trace backend row to the §1 inputs table + D4 docs; D1's TIMEOUT_ABSENT attachment text
   should say WHY it is unconfirmed ("no trace confirmation — is Jaeger configured?") so the inert
   mode is self-diagnosing rather than silently green.
3. Pre-register the TeaStore disposition (5a fix, option (1) or (2) — never a silent relaxation).
4. Freeze the QuiescenceGate→{FIRE, NO_FIRE, NOT_EVALUABLE} mapping (5b) in the same amendment.
5. Gate the shipped default on the S2-at-default-caps calibration (Attack 3 fix (ii)).

With 1–5 plus the schema-level symmetry fields (Attack 1) and the tier stratification (Attack 2),
the tool-story upgrades to a defensible ACCEPT: an oracle with a priced configuration cost, a priced
expertise tier, a disclosed instrumentation prerequisite, a mode-invariant bridge from product to
evaluation, and a default calibrated on the benchmark's own benign stratum. As written today, a
hostile PC gets to discover items 1, 3, and 4 themselves — and that is a reject.
