# SmartFetch Paper Plan (B-venue track) — PLAN OF RECORD

Status: **v4 — VENUE ELECTED BY USER (2026-07-16): SANER 2027. §9-A is the sprint amendment of
record; Round-3 reviewer confirmation (sufficiency-for-SANER) pending.** Base study design =
v3.1 (REVIEWER-ACCEPTED 3/3, Round 2; gate log §12) — §9-A descopes its execution for the SANER
window and defines the thickening path back to the full design. Execution of any MIST tool-code
change still requires explicit user approval of the E-items (standing rule).
Branch: `main_track`. Working folder: `debug/b-smartfetch/`. Evidence base: `research/*.md`
(codebase inventory, related-work scan, venue scan — all dated 2026-07-15).

---

## 1. Pitch (one paragraph)

REST API test generators — spec-based, search-based, and LLM-based alike — routinely fabricate
parameter values that do not exist in the system under test: request bodies reference accounts,
products, and stations that were never created, so positive test flows die in 404/400 storms and
deep multi-service workflows are never exercised. **SmartFetch** treats the deployed microservice
system's own read surface as a *queryable value oracle*: at generation time it (i) routes a
needed parameter (e.g. `accountId`) to a producer service and read-only GET endpoint selected
from the system's merged, service-annotated OpenAPI surface, (ii) issues an authenticated live
GET and LLM-extracts an *actually existing* value from the response body (no JSONPath
brittleness, tolerant of field-name/parameter-name mismatch), and (iii) persists the learned
parameter→producer mappings in a registry that is reused across runs, so discovery cost
amortizes toward zero. A trace-observed-producer priority and a diversity-rotation cache
complete a four-level chain that degrades gracefully to plain LLM generation. We evaluate
SmartFetch inside the MIST generator on four open-source microservice systems (~70+ services
combined), measuring [EXPECTED: higher valid-request rate and deeper workflow execution than
LLM-only and lexical-matching value sourcing, at declining per-run discovery cost] — and we
test whether harvested values transfer to a third-party fuzzer as a drop-in dictionary.

Framing in one line (as an *analogy*, not a mechanism claim — PrediQL owns the literal "first
retrieval-augmented fuzzer" phrase, for GraphQL + self-session-history): **retrieval-augmented
test input generation, where the retrieval corpus is the live system itself** (vs.
LlamaRestTest's frozen weights, AutoRestTest's parametric guessing, Keploy's stale recorded
traffic, EvoMaster's out-of-band SQL).

Title candidates (final at draft time):
1. *SmartFetch: Grounding REST API Test Inputs in Live Microservice State*
2. *Ask the System, Not Just the Model: Live-Value Retrieval for Microservice API Testing*

## 2. The artifact today (evidence: `research/codebase-inventory.md`)

Code: `mist-core/src/main/java/io/mist/core/smart/` — 11 files, 7,636 LOC (orchestrator
`SmartInputFetcher` 4,576 LOC). Live integration points: `MistGenerator` (3 fetch sites +
per-scenario rotation reset) and `SharedPoolSupport` (pool seeding via the provenance API
`fetchSmartInputWithProvenance` → `ResolvedValue` + 5-value `ValueProvenance` enum). NOTE: the
frozen 2026-06-11 docs (`docs/Smart-Fetch-Process.md`) describe two integration classes that
**no longer exist** (`SmartLLMParameterGenerator`, `TestDataGeneratorFactory`) — the paper
describes only the surviving MistGenerator path.

**Metric-definition caution (adopted from review)**: the enum's helper `isLiveGrounded()`
(`ValueProvenance.java:42-44`) returns true for `LLM_GENERATED` too — its semantics are
*positive-test candidacy* (screening rule for the negative-test classifier), NOT live
provenance. The paper's **grounded-share metric is defined on raw enum values:
grounded = {RESOLVED_LIVE, RESOLVED_CACHE}**, computed by E4 without using `isLiveGrounded()`.

What is already built and verified:

- **Four-level priority chain**: P0 trace-observed producer endpoints (from MIST's workflow
  traces, session-scoped) → P1 registry mappings, ranked by
  `0.5·(priority/10) + 0.3·successRate + 0.2·recency` **plus a cold-start name-affinity prior**
  (token/stem overlap parameter↔service/endpoint; engages while all successRates are zero) →
  P2 LLM discovery (service shortlist → GET-endpoint selection with forced-choice retry;
  persists to registry; also fires as last resort when every existing mapping fails) → P3 LLM
  fallback. Percentage gate (default **1.0, "grounding-first"**) controls the smart/LLM split
  per call. Mapping quarantine suppresses repeat-failing producers within a run.
- **15 distinct LLM prompts in the fetcher** (+1 in the parameter-error analyzer): service
  discovery, endpoint selection (+forced), direct value extraction, multi-value extraction,
  semantic-similarity expansion, semantic field matching, field relevance, minimal fallbacks,
  value variation, etc. Two are registry-overridable templates. Verbatim texts + current
  file:line in `docs/PROMPT_INVENTORY.md` (maintained, 2026-07-13). The prompt suite itself is
  paper material (design rationale: small budgets, low temperatures for decisions, sentinel
  tokens, markdown-fence cleaning).
- **Persistent learned registry** (YAML): parameter→[endpoint, service, priority, successRate,
  lastUsed, consumerApiKey-scoped], atomic writes, dirty-flag flush at scenario boundary + JVM
  shutdown, error-context store, verified-value pool (`poolEntryStatus`), re-runnable migration
  tool. **Precision matters for the paper's claims**: shipped TrainTicket registries are
  structurally mature (57 parameters, 103/155 endpoint rows across 18/23 services) but their
  successRates are **all 0.0 by deliberate design** (2026-06-10 de-poison: the old format-check
  feedback rewarded wrong producers, e.g. `endStation`→trains at 0.97 yielding SUT-400s;
  demote-only until producer-keyed feedback lands). So today: structural coverage = operational;
  score learning = designed but awaiting A2 (E2, user-gated — see the contingent-claim split in
  §3). The **evaluation registry-of-record is `evaluation/suts/trainticket/input-fetch-registry.yaml`
  (155 rows)**; the demo variant (103 rows) is disclosed but not used for banked runs. Locked by
  `ProducerRankingTest` (4/4), `ShippedRegistryDepoisonTest`, and the live-gated
  `TTEndStationLiveCheck`.
- **Two-phase verified-value loop (landed, opt-in)**: Phase A executes positives with capture +
  enhancer-rescue (a placeholder positive that 400s is regenerated from the SUT's error text,
  then harvested), drains SUT-2xx-verified values into the registry; Phase B narrows generation
  to verified values. Live TT run: 34 enhancer rescues, 869 narrowing events
  (`debug/grounding/producer-ranking-and-two-phase.md`).
- **Generic auth** (post-audit): configurable login path/fields/token JSON path/validity;
  401/403 invalidates the token; no baked-in credentials. Single-arg constructor still defaults
  to the TrainTicket shape (fine — our other SUTs run auth-less).
- **Hardened by a 70-finding audit** (2 rounds + independent verification; 64 fixed, 6 disclosed
  deferrals; registry cleaned 175→82 rows, 0 fabricated endpoints). The audit record doubles as
  artifact-evaluation rigor evidence, and its design-tension findings feed Discussion honestly.
- **Existing quantitative evidence — indicative only, superseded by re-measurement**: four D4
  SFHR runs on TrainTicket (2026-05, pre-grounding-fix): ID-typed N=123/357/178/370, SFHR
  conservative 45.5/75.9/58.4/47.0% (mean 56.7%, spread 30pp; only 1/4 passed ≥60%), upper
  57.7/89.6/100/65.7%. Caveats the paper must respect: documented mining-script bugs,
  placeholder contamination of one upper bound, and D5 trace-confirmation 0.00% in every run
  (fetch-time provenance ≠ execution-time corroboration — hence the independent corroboration
  metric in §5/E8). The June grounding fixes lifted the all-parameter grounded share 14%→~99.7%
  on TT (`debug/a-main/archive-2026-06-01/PLAN.md:113-114`) — while the endStation case proved
  **grounding ≠ validity**. All headline numbers are RE-MEASURED (E4); old numbers appear only
  as caveated priors, or not at all.
- **A designed-but-never-run per-stage metric framework** (7 families, 12 named metric IDs with
  KPI thresholds). Three implementability gaps documented; executing this framework for the
  first time = the paper's per-stage instrument (E5). The S1.2 gold-producer set is **manual
  annotation** (disclosed single-labeler, same rigor rules as RQ5; scoped to 1 SUT or dropped to
  Future Work if a defensible gold set doesn't fit the window).
- **Scope boundaries to keep clean**: (a) MIST's *separate* SemanticDependencyRegistry /
  "JIT bind" subsystem (smart-fetch is its dictionary-miss fallback) is held constant across
  arms — E1's pre-step verifies the hold-constant knob exists *before execution starts*
  (fallback: add one, or run all arms JIT-bind OFF globally); (b)
  `negative.input.generation.mode=smart` is an unrelated knob; (c) **service-annotation
  dependency disclosed**: endpoint discovery keys on `x-service-name` (falling back to OpenAPI
  tags); on a plain tag-grouped spec (Bookinfo) it registered ZERO services and smart-fetch
  degraded to hallucinated service names — the technique requires service-annotated or
  service-suffixed-tag specs, stated in Threats.

Registry/SUT starting state (measured 2026-07-15): TrainTicket = structurally mature registry;
SockShop = empty mappings but 92 VERIFIED_VALID pool entries (2 ops / 8 fields — evidence the
two-phase loop ran); TeaStore / OTel Demo / Bookinfo / Boutique = no registry (cold start).
TeaStore+OTel's recent minimal-tier runs (LLM off, smart-fetch off by default) produced
positive-flow 404s from type-naive values — the motivating anecdote; cold-start on them is a
*feature* of the design (RQ3 learning curves), not missing prep.

## 3. Novelty & positioning (evidence: `research/related-work.md`)

**External scan verdict (45+ searches, 2026-07-15): no single paper or tool combines
SmartFetch's ingredients.** Individually precedented; the combination is unclaimed. The
sub-claims, phrased to survive a hostile PC (Round-1 A-B1/A-B2 adopted):

1. **Cross-microservice, live-retrieval routing** — routing a bare parameter to a producer
   *service + read endpoint*, selected from a merged multi-service registry, *for the purpose of
   issuing a live GET and harvesting a currently-real value* (inseparable from sub-claim 2).
   Distinct from AutoRestTest's SPDG (embedding-similarity property-dependency inference over a
   *single* merged spec, built to *chain* requests, never to fetch) and from the name/schema/noun
   dependency lineage (RESTler, Morest, RESTest/IDLReasoner, RAFT, ASTRA, KAT). **We do not
   claim "semantic vs syntactic dependency inference" as the novelty — AutoRestTest already
   infers dependencies semantically; our novelty is routing-to-a-read-endpoint-for-live-retrieval
   across an explicit multi-service registry.**
2. **Freshness / instance-specificity via live fetch at generation time** — the value is
   guaranteed to exist in *this* deployment *right now*; vs values frozen in fine-tuned weights
   (LlamaRestTest), hallucinated from parametric knowledge (AutoRestTest/RESTGPT/KAT), replayed
   from stale recorded traffic (Keploy/Speedscale), or pulled via out-of-band SQL (EvoMaster
   white-box). The scan found no tool making this guarantee via this mechanism — the paper's
   anchor claim.
3. **Persistent, cross-run learned registry** amortizing discovery cost. The
   *persistence + cold-start name-affinity prior + demote-only multi-producer disambiguation +
   quarantine* half is **operational and evaluated today** (RQ3a); the *producer-keyed
   success-rate ranking* that closes the feedback loop is **designed but unlanded
   [E2-dependent]**, claimed only as a *contingent* contribution and evaluated in RQ3b **iff E2
   is approved** (tool code is user-gated). Absent E2, the registry's novelty rests on
   persistence + prior + disambiguation — still unprecedented in the surveyed literature.

**What we must NOT claim** (all well-precedented): LLM value generation per se; producer-consumer
inference per se; same-session response-value reuse (ARAT-RL/RESTler/Morest/EvoMaster/ASTRA);
"real backend data instead of synthetic" as a general idea (EvoMaster SQL-select +
external-service harvesting — different layer, same spirit); probabilistic real-vs-synthetic
gating (EvoMaster `probOf*`). Internal scan (2026-06-01, `debug/grounding/…md`) agrees and adds
Arcuri's survey naming test-data setup an open need with "no source claiming robust cross-SUT
grounding under data pollution / multiple producers solved" — the slot this paper occupies, with
**multi-producer disambiguation** (prior + demote-only + quarantine + contingent feedback) as
our documented instance of the open problem.

**Preempting "isn't this just an LLM agent with a GET tool?"**: the contribution is the
engineered, measured pipeline (4-level graceful degradation, explicit disambiguation, persistent
registry, provenance instrumentation) plus the empirical characterization of when live-fetch
beats guess-and-retry — none of which falls out of handing an agent a curl tool; the scan found
no agentic tool that fetches a real value for a *different* request's parameter.

**Danger papers, preempted explicitly in Related Work**: (1) EvoMaster's
`probOfHarvestingResponsesFromActualExternalServices` — a *naming* collision (harvests the SUT's
*outbound* third-party calls to build mocks; we harvest the SUT's *inbound* read surface); quote
option semantics precisely. (2) AutoRestTest — twice: as the semantic-SPDG holder (see sub-claim
1) and as the LLM guess-and-retry value agent; the "how much does live-fetch buy?" question is
answered experimentally (AX/RQ1), not by citation. (3) ASTRA (IBM 2025) — LLM classifies *error
text* within one spec, session-scoped, no artifact; conceptual comparison only, stated as such.
(4) KAT (ICST 2024 — target-tier predecessor): spec+LLM *synthetic* values, single-spec ODG, no
live fetch, **and no persistence — the persistent cross-run registry is a co-equal delta axis**;
KAT has no runnable artifact, so the comparison is conceptual-by-necessity (stated openly;
AutoRestTest carries the empirical burden). Also positioned: PrediQL (GraphQL, self-history
retrieval), MIRAGE (the inverse: fabricates responses when reality is unavailable — we fetch
reality when it is available), record-replay industry tools (backward-looking corpus vs our
forward-looking just-in-time retrieval).

## 4. Claims & contributions

1. **Technique**: SmartFetch — live-state-grounded input generation for microservice REST APIs:
   live-retrieval routing to producer read-endpoints across services, live harvesting with LLM
   direct extraction, and a persistent mapping registry with explicit multi-producer
   disambiguation (cold-start prior + demote-only scoring + quarantine; producer-keyed feedback
   ranking as the E2-contingent completion), organized as a 4-level graceful-degradation chain.
2. **Measurement instrument**: first-class value provenance (5-way enum at the API level;
   grounded = {RESOLVED_LIVE, RESOLVED_CACHE} on raw values) + per-stage metric framework +
   an independent execution-time corroboration metric — grounded-share is
   mechanism-descriptive; **headline outcomes are the SUT-independent ones** (2xx validity,
   coverage, unique 5xx, workflow depth).
3. **Empirical study** on 4 microservice systems (~70+ services; total operation counts stated
   explicitly): marginal AND absolute value-source contrasts (A0/A0′), component ablations,
   cold→warm discovery-amortization curves (RQ3a; feedback-ranking curves iff E2), external-tool
   baseline runs, and a portability experiment (harvested values as a RESTler dictionary).
4. **Open artifact**: implementation in MIST (open-source), prompts, registries, measurement
   toolchain, audit record — aimed at the venue's artifact-evaluation badge.

## 5. Research questions

- **RQ1 (Effectiveness — grounding AND validity)**: Does SmartFetch improve input validity and
  system exercise vs (a) MIST-default-minus-smart-fetch (marginal contrast, A0), (a′) true
  LLM-only (absolute contrast, A0′), and (b) lexical (non-LLM) producer matching — **(b) scoped
  to TrainTicket + TeaStore, the ablation hosts**? Report BOTH grounded share (mechanism metric,
  raw-enum definition) AND validity outcomes, since they diverge. **Primary metric: 2xx
  valid-request rate.** Secondary: operation coverage (ops with ≥1 2xx), 400/404/5xx breakdown,
  unique 5xx faults (dedup signature = status + failing endpoint + error-span path; proxy
  caveat per the survey), workflow depth (deepest consecutive-2xx step AND fraction-of-scenario
  completed; distributions), service-interaction coverage (unique caller→callee pairs in Jaeger).
  Plus the **independent corroboration metric** (harvested value observed in the emitted request
  AND request 2xx; revived trace-presence check) — pre-committed to report even if low.
- **RQ2 (Component analysis)**: Which ingredients matter? Ablations on TrainTicket + TeaStore:
  LLM discovery → **lexical discovery** (pinned: RESTler/RAFT-lineage token/noun-overlap matcher
  — camelCase/snake split + lowercase + stem; Jaccard overlap of parameter tokens vs endpoint
  path + response-field tokens; fixed threshold; deterministic tie-break — *replaces* LLM
  candidate-set selection, and is distinct from the affinity-prior ablation, which re-ranks an
  LLM-discovered candidate set); LLM direct extraction → deterministic field-walk; name-affinity
  prior off (the documented endStation failure mode); diversity rotation off; P0 off;
  percentage gate ∈ {0, 0.3, 0.7, 1.0} (midpoints first to cut under the de-scope ladder);
  two-phase verified loop on/off.
- **RQ3 (Learning & cost — decomposed for independent degradation)**:
  - **RQ3a (E2-independent, works today)**: cross-run **discovery-cost amortization** — a warm
    registry converts P2 LLM-discovery calls into P1 registry hits, seeded cold by the
    name-affinity prior. Measured over r1..r5: grounded share, validity, and **LLM-discovery-call
    fraction (primary metric)**, token/$ decomposed **by prompt type** (amortization is claimed
    on the discovery component — extraction calls persist by design). This alone substantiates
    the cross-run-registry half of sub-claim 3.
  - **RQ3b (E2-dependent)**: producer-keyed feedback ranking (successRate movement, mapping
    re-ordering, EMA convergence). **Iff E2 lands**; on slip, RQ3 reports RQ3a only and feedback
    ranking moves to Future Work (sub-claim 3's contingency, §3).
  - SUT-state policy (pinned): fresh SUT redeploy between r's; concrete-value caches and the
    verified pool invalidated on reset; **only mapping-learning is claimed across resets**;
    SUT-state covariate recorded. AutoRestTest's ≈$0.02/run/service is compared
    **token-normalized** (different models — raw $ is apples-to-oranges).
- **RQ4 (Portability)**: Do harvested values transfer? Control = RESTler with its **default
  spec-derived dictionary**; treatment = default **+** harvested values (injection granularity
  pinned in PROTOCOL; per-parameter slotting preferred if feasible, else global-dict disclosed);
  harvesting cost (MIST's LLM spend) disclosed as part of the treatment; optional
  random-real-value control (DB-sampled) to separate "real values help" from "*our* pipeline's
  values help". Metrics: RESTler 2xx rate + coverage delta.
- **RQ5 (Extraction fidelity, small)**: Stratified sample (~100 harvested values per SUT; 50
  under the de-scope ladder): does the extracted value occur in the live response, and is it
  semantically correct for the parameter? Pre-registered rubric; labeler blinded to arm where
  feasible; ~20% double-labeled with Cohen's κ; labels + rubric published; single-labeler
  disclosed.

## 6. Experiment design

**SUTs** (all deployable on the existing WSL2/minikube cluster; revival scripts exist):

| SUT | Services | Registry start | Auth | Role |
|---|---|---|---|---|
| TrainTicket | ~40 | structurally mature (57 params; eval registry = registry-of-record) | JWT | flagship; ablation host; admin-write salt + 800ms pacing |
| Sock Shop | ~8 | cold mappings (verified pool present — excluded per arm rules) | none | second system; RabbitMQ warm-up before runs |
| TeaStore | ~6 | cold | none | ablation host; cold-start curves; motivating 404 anecdote |
| OTel Demo | ~15 | cold | none | polyglot; real captured seed traces |
| (optional) Bookinfo / Boutique | 4 / 11 | cold | none | first rung of the de-scope ladder; Bookinfo also illustrates the annotation-dependency threat |

State per-SUT operation/endpoint totals in the paper (reviewers pattern-match on API counts).

**Arms** (MIST generator held fixed; only the value source varies; JIT-bind held constant; fresh
SUT redeploy/DB reset at arm boundaries — never TrainTicket's destructive `generatedb` mid-run):

- **A0 — MIST-default minus smart-fetch** (trace-payload grounding retained; smart-fetch off).
  Measures smart-fetch's **marginal** lift over the grounding MIST already has. (Round-1 B-B1:
  the old "LLM-only" label was code-falsified — `getTraceParameterValue` and
  `preferVerifiedValues` are not gated by the smart-fetch switch.)
- **A0′ — true LLM-only** (smart-fetch off + trace-payload grounding off + verified-pool
  narrowing off; E1 switches). Measures the **absolute** contrast. Context/input *reuse*
  (same-scenario consistency) stays ON in all arms — it propagates an upstream-chosen value,
  it does not source new ones (disclosed).
- **A1** SmartFetch ON, cold registry (r1 of the learning curve).
- **A2** SmartFetch ON, warm registry (r2..r5 continue A1's registry per the RQ3 state policy).
- **A3** ablation set (RQ2) on TrainTicket + TeaStore.
- **AX — external baseline set (expected by the venue, de-risked by staging)**: must-run pair =
  **AutoRestTest + one of {EvoMaster BB, RESTler}**; the third tool run if the harness lands;
  any tool that will not run on a SUT is disclosed, never silently dropped. Budget for AX:
  #requests-issued-to-SUT AND wall-clock-including-generation, both reported; MIST's
  generation-time LLM cost disclosed separately. ASTRA = conceptual only (no artifact). RQ4
  reuses the RESTler harness ± dictionary.

**Arm-integrity rules (pre-registered gates, not post-hoc assumptions)**:
- Per-run provenance gate: **A0′ must emit zero RESOLVED_LIVE / RESOLVED_CACHE / trace-sourced
  values** (checked from E4 output before a run banks); A0 must emit zero smart-fetch values.
- Registry lifecycle: pristine/empty registry file for every A0/A0′/cold-A1 run;
  snapshot-and-restore at arm boundaries; pool-status source disabled in A0′ (and in A0 unless
  measuring MIST-default explicitly — pinned in PROTOCOL).
- Execution order: arm×seed interleaved/randomized so SUT data growth is spread across arms, not
  aligned with one; SUT-state covariate (row/order/user counts) recorded per run and reported.

**Run matrix & throughput honesty** (Round-1 C-F7): headline cells (A0, A0′, A1, A2-warm) at
**≥10 seeds** × 4 SUTs = 160 runs; learning-curve continuation r3..r5 at 5 seeds = 60; ablations
(~10 configs × 2 SUTs × 5 seeds) = 100; AX (≤3 tools × 4 SUTs × 3 repeats) ≤ 36; RQ4 (± dict ×
4 SUTs × 3) = 24 → **≈ 380 runs full matrix**. Working assumption ~12 runs/day in a-main gaps →
~32 run-days vs the ~25 scheduled: the calibration smoke measures true run duration and
**re-sizes the matrix before any banked run**; the §9 de-scope ladder sheds load in a
pre-committed order. Protected cells in all cases: A0/A0′/A1 on 4 SUTs + RQ3a.

**Statistics** (Round-1 B-B6/B-A1): one **primary metric per RQ** (RQ1: 2xx valid-request rate;
RQ3a: LLM-discovery-call fraction; unique-5xx promoted to co-primary only in an ISSRE-variant
re-lead); all else secondary/exploratory. Mann-Whitney U with **Holm-Bonferroni within each
metric family** (family boundaries declared in PROTOCOL), **Â12 + CI as the primary evidence**
(magnitude over significance in the small-n regime); medians + IQR; ≥10 seeds on headline cells,
5 elsewhere, with a power note. Budget unit: **fixed #tests for within-tool arms** (wall-clock +
LLM calls are cost axes, not caps — a wall-clock cap would punish A1's live-GET latency);
wall-clock budgeting only for AX.

### 6.1 PROTOCOL.md must pin (pre-registration checklist, before any banked run)

1. A0/A0′ exact knob configs + the per-run provenance gates (zero grounded/trace values in
   A0′). **The A0′ switch/gate enumeration must cover ALL grounding stages in the generator —
   including the second trace-derived stage `span.getDataProvenance()` (`MistGenerator.java:1355`)
   alongside `getTraceParameterValue` (`:1381`) — and the gate predicate must not false-trip on
   the retained output-chaining/context-reuse stage (`:1362`), which propagates upstream-chosen
   values rather than sourcing new ones (Round-2 B-R2-A1).**
2. Grounded-share definition = {RESOLVED_LIVE, RESOLVED_CACHE} on raw enum values;
   `isLiveGrounded()` forbidden for the metric; per-emission-site reporting.
3. The independent execution-time corroboration metric, reported even if low — **with a
   positive control proving the metric CAN fire (seed a known-present value and verify
   detection), given the prior D5=0.00%-every-run history (Round-2 B-R2-A2).**
4. Registry-file lifecycle per arm; interleaved arm×seed order; SUT-state covariate.
5. RQ3 SUT-state policy; mapping-vs-value-cache separation; concrete-cache invalidation on reset.
6. The lexical-discovery algorithm (tokenization, similarity, threshold, tie-break) + its
   distinctness from the affinity-prior ablation.
7. Primary/secondary metric split per RQ; Holm-Bonferroni family boundaries.
8. Seeds per cell with power note; budget unit = fixed #tests (within-tool) / dual-reported (AX).
9. Definitions: workflow depth (deepest consecutive-2xx + fraction-completed), 5xx trace
   signature, grounded-share denominator (all positive-variant params; ID-typed subgroup).
10. Exclusion rules for degenerate runs (SUT down, auth failure, empty pool, gateway rate-limit
    storm) + their disclosure. **A wall-clock-cap bust = discard-and-rerun after re-sizing,
    never mid-run truncation (B-R4-A3).**
11. RQ4 injection granularity + control definition; RQ5 rubric + blinding + κ subset.
12. LLM pin (model, version, temperature) + token accounting per prompt type.
13. **The run unit per SUT (§9-A.4a): the exact seed-trace corpus (versioned file list), operation
    subset, tests-per-op, enhancer settings — identical across arms/seeds/rounds; plus the
    per-run cost envelope (wall-clock + LLM-call band) the G2 smoke must confirm. Per-ARM
    expected call bands are recorded too — they double as an A0′ arm-integrity cross-check
    (an A0′ run whose call profile matches A1's band indicates a leaking switch; B-R4-A5).
    Cost control is scope-only — no runtime call caps (the §9-A.4a non-censoring rule).**

## 7. Engineering prerequisites (all user-gated; no tool code until approved)

| # | Item | Size | Why |
|---|---|---|---|
| E1 | Ablation/scope switches: discovery=llm/lexical/off, extraction=llm/heuristic, affinity-prior on/off, rotation on/off, P0 on/off, **trace-grounding off + pool-narrowing off (the A0′ switches)**. **Pre-step before execution: verify the JIT-bind hold-constant knob exists (30-min grep); fallback = add one (size→M) or run all arms JIT-bind OFF globally** | M | RQ1/RQ2 internal validity (Round-1 B-B1, C-F11) |
| E2 | **A2 producer-keyed execution feedback** — SUT 2xx/4xx raises/lowers the producer's successRate (design on file in `debug/grounding/`); verify dirty-flag flush persists it. **NOVELTY-CRITICAL flag: the only E-item whose absence downgrades a headline sub-claim — if user approval is withheld or it slips, sub-claim 3 and RQ3 re-scope to RQ3a per §3/§5** | M (integration risk: net-new cross-layer wire into a frozen-import orchestrator with no integration tests on its call sites) | RQ3b |
| E3 | Per-SUT smart-fetch profiles (TeaStore/OTel/SS cold registries, auth=none; Boutique/Bookinfo if used) | S | RQ1/RQ3 |
| E4 | Provenance-based measurement pipeline v2 — consume raw `ValueProvenance` values (never `isLiveGrounded()`); **wire provenance into all four value-emitting sites (3 bare `fetchSmartInput` generator sites + the trace path) or formally bound coverage and report grounded-share per emission site** | M | all RQs (headline numbers; eliminates rather than relocates the D4 defect class — Round-1 B-B2) |
| E5 | S-framework calculator + gap-fills. **Gold-producer set = manual annotation (single-labeler disclosed, day-budgeted; 1 SUT scope or Future-Work drop if not defensible in time)** | M | per-stage metrics |
| E6 | Harvested-values → RESTler dictionary / Schemathesis examples exporter | S | RQ4 |
| E7 | External-tool harnesses (EvoMaster BB / AutoRestTest / RESTler vs gateway specs, matched budgets) — **resized ~2 weeks, starts in window 1 (cluster/integration work, parallel to E-coding)** | L | AX (venue-expected baseline set) |
| E8 | **Measurement tooling (new)**: Jaeger 5xx-dedup signature extraction; service-interaction (caller→callee) coverage extraction; workflow-depth instrumentation; the independent execution-time corroboration metric | M | RQ1 metrics are not computable without it (Round-1 B-A9) |

## 8. Paper skeleton (target 10pp + refs, IEEE conference)

1. Introduction (the 404-storm motivating run; grounding≠validity; contributions)
2. Background & motivating measurement (provenance snapshot of an LLM-only run)
3. SmartFetch design (priority chain; discovery; extraction; registry learning + multi-producer
   disambiguation; prompt design rationale)
4. Implementation in MIST (auth, caches, hardening; audit as rigor evidence)
5. Evaluation (RQ1-RQ5; protocol; stats)
6. Discussion & threats (when grounding helps/doesn't; read-only safety & state pollution;
   service-annotation dependency — the Bookinfo zero-services case; needs a deployed SUT with
   read endpoints + spec; LLM dependence; self-report vs corroboration)
7. Related work (dependency inference; LLM testers; EvoMaster harvesting disambiguation;
   replay tools; RAG framing-as-analogy)
8. Conclusion + artifact statement (AE badge target)

Page-budget cut order (pre-committed): RQ5 → artifact appendix; RQ4 → short subsection or
companion demo; per-stage S-metrics → selected highlights + artifact.

## 9. Venue & timeline (evidence: `research/venue-scan.md`; final call = USER at plan approval)

**Primary deliverable = the completed study.** Verified deadlines: **SANER 2027 abstract Sept 21
/ paper Sept 25, 2026** (CCF-B + CORE-A); **ICST 2027 Nov 2, 2026** (CCF-C / CORE-A; KAT
precedent, AE badges, Major-Revision mechanism). Estimates (UNVERIFIED): ICSME/ICWS 2027
~Mar 2027; ISSRE 2027 ~Jun-Jul 2027 (both CCF-B + CORE-A).

**Recommendation (user decides; Round-2 C-R2-1 framing)**: the **plan-of-record submission
target is the on-bar CCF-B window — ICWS/ICSME 2027 (~Mar 2027)** — matching the stated
"roughly CCF-B" bar with zero rework; the work is nevertheless **paced to Nov-2 readiness**, so
**ICST 2027 (Nov 2) stands as an opportunistic early CORE-A shot the user may elect** (strongest
venue fit: testing-native PC, KAT vocabulary, AE badges, Major-Revision de-risk — but CCF-C).
ISSRE 2027 (~Jul) remains the later on-bar option and is NOT zero-rework: it requires re-leading
with fault detection (unique-5xx promoted to co-primary and the intro re-framed; per Reviewer A,
that re-lead should rest on the depth-unlocks-reachability chain the plan already instruments,
not on implying grounding finds faults directly). SANER (Sept 25) is NOT recommended: 9.5 weeks
including user-gated engineering on a shared cluster where a-main has priority is a
protocol-integrity risk, not just a schedule risk. Companion option (user decision): ICSE 2027
Tool Demos (Oct 23) / AST 2027 (Oct 30) — the AutoRestTest full-paper+demo pattern.

**Timeline to ICST (assuming plan+engineering approval ~Jul 20):**

| Window | Work |
|---|---|
| Jul 20 – Aug 9 (3 wks) | E1-E6 + E8 engineering + TT smoke; **E7 starts here in parallel** (cluster work); PROTOCOL.md drafted |
| Aug 10 – Aug 14 | Calibration smoke — **chartered to measure achievable banked-runs/day under live a-main contention, not just per-run duration (Round-2 C-R2-2)** → re-sizes the §6 matrix; protocol freeze |
| Aug 15 – Sep 12 (4 wks) | Main arms A0/A0′/A1/A2 × 4 SUTs (10-seed headline cells); ablations (TT+TeaStore); learning curves r1-r5 |
| Sep 13 – Sep 19 | AX external runs + RQ4 dictionary injection; RQ5 fidelity audit |
| Sep 20 – Oct 11 | Analysis + full draft; internal 3-cold-reviewer pass on the draft |
| Oct 12 – Oct 25 | Revision buffer; artifact packaging (AE) |
| Oct 23 / Oct 30 | (optional) ICSE-demo / AST companion |
| **Nov 2** | **ICST 2027 submission** (or hold for the CCF-B fallback per the user's venue call) |

**De-scope ladder (pre-committed order; engage top-down when the smoke or the window forces
it)**: (1) drop Bookinfo/Boutique; (2) drop AX beyond the must-run pair; (3) ablation seeds 5→3
and drop gate-sweep midpoints {0.3, 0.7}; (4) RQ5 sample 100→50/SUT; (5) cold-SUT headline seeds
10→5. **Never cut**: A0/A0′/A1 on all 4 SUTs, RQ3a, the provenance gates.

Slack: ~2 weeks absorbable; catastrophic slip retargets ICSME/ICWS (~Mar) with no work lost.
Cluster sharing: a-main has priority; SmartFetch runs schedule into gaps (revival scripts make
context switches cheap); E-window work is off-cluster by design.

## 9-A. SANER 2027 SPRINT — venue amendment of record (USER-ELECTED 2026-07-16)

**Decision provenance**: v3.1 recommended the ~Mar CCF-B window as plan-of-record and reserved
the final venue call for the user; the user elected **SANER 2027 (abstract Sept 21 / paper
Sept 25, 2026 — VERIFIED; Richmond VA, Mar 9-12, 2027; CCF-B + CORE-A; documented 23-26%
acceptance)**. Electing SANER **forfeits ICST 2027** (SANER notification ~Dec, after ICST's
Nov 2 deadline; no double submission). This amendment defines the sprint that fits the window
without betting the protocol: it bets the calendar, with pre-committed exits.

### 9-A.1 SANER-specific framing (the venue has zero LLM-REST-testing precedent; PC gravity =
analysis/evolution/reengineering)

- **Lead with the registry story, scoped to in-sprint evidence (Round-3 A-R3-B1)**: the hook is
  a persistent producer registry that **accumulates and self-amortizes parameter→producer
  mappings across runs** (cold-started by a name-affinity prior, stabilizing run-over-run);
  live-fetch is the mechanism inside it. RQ3a (cross-run accumulation + discovery-cost
  amortization curves) is promoted to co-lead narrative beside RQ1. Title candidate:
  *"SmartFetch: Grounding Microservice API Test Inputs in Live System State with a Persistent,
  Self-Amortizing Producer Registry."* **"Learns from execution outcomes" / adaptive
  success-rate ranking is reserved strictly for the deferred RQ3b/E2 and appears only in the
  abstract's future-work sentence — never as the headline.** Consistency rule: the SANER
  narrative is carried by RQ3a (accumulation + amortization), mapping-stability, and the
  de-poison/audit *maintenance* history; outcome-feedback learning (RQ3b/E2) is disclosed as
  deferred and is NOT part of the SANER evidence or headline (this keeps §9-A.5's "no claim
  rests on deferred work" true at the framing layer, closing the §9-A.1↔§3.3 seam).
- Everything else already in the accepted design serves this framing: registry lifecycle,
  mapping-stability across runs, cold→warm curves, the audit/de-poison history as registry
  *maintenance* evidence.
- Per Reviewer A's standing note: fault detection stays a secondary metric here (no ISSRE-style
  re-lead); "tool-oriented and empirical work" is explicitly welcomed in SANER's CFP.
- **CFP facts VERIFIED from the official SANER 2027 research-track page (G1 sub-item closed
  2026-07-16; conf.researchr.org/track/saner-2027/saner-2027-papers)**:
  - **10 pages + 2 references-only**, IEEE `\documentclass[10pt,conference]{IEEEtran}`;
    EasyChair; deadlines AoE: abstract Sep 21 (**mandatory**) / paper Sep 25 / **notification
    Dec 1, 2026** / camera-ready Jan 8, 2027. (Dec-1 notification keeps the ~Mar fallback
    chain workable.) No rebuttal mechanism listed.
  - **DOUBLE-ANONYMOUS review** — a new work item for the writing window: tool pseudonym for
    MIST/SmartFetch in the submission, third-person self-citation of the MIST papers,
    **anonymous artifact mirror** (anonymous.4open.science / anonymized Zenodo) since the real
    repo de-anonymizes; evaluation criteria explicitly include **open science/verifiability**,
    and a **Data Availability statement** after the conclusions is expected (encouraged, not
    mandatory).
  - **Topic-fit risk softened**: the 2027 CFP's topics explicitly include "AI for Software
    Engineering," "Generative AI and LLMs applied to software analysis," and "Agentic AI
    systems" — the venue-scan's "zero LLM-REST precedent" concern is now partially offset by
    the CFP's own invitation; the evolution-led framing remains the primary pitch, with the
    AI4SE topic listing cited as fit evidence.
  - Same conference also hosts an **Agentic AI4SE track and a Tool Demo track (abstracts
    Oct 19, 2026)** — potential same-venue fallback/companion options, USER decision, not in
    the sprint's critical path.

### 9-A.2 Sprint matrix (descoped from §6; the §6 full matrix remains the post-SANER thickening
target)

| Cell | Sprint scope | Δ vs full design |
|---|---|---|
| Headline arms A0 / A0′ / A1(r1) / A2(r2) | 4 SUTs × **5 seeds** = 80 runs | seeds 10→5 (power tradeoff disclosed; Â12 + CI carry the evidence per §6 stats) |
| Learning curve r3..r5 (RQ3a) | 4 SUTs × **3 seeds** = 36 runs | seeds 5→3 on continuation only |
| Ablations | **lexical discovery + affinity-prior-off** × 2 SUTs (TT+TeaStore) × 5 seeds = 20 runs | extraction/rotation/P0/gate-sweep/two-phase ablations → deferred |
| AX external | **AutoRestTest** × 4 SUTs × 3 = 12 runs; second tool (RESTler or EvoMaster BB) only if the window allows, disclosed if not | must-run pair → must-run one |
| RQ4 (portability), RQ5 (fidelity audit) | **deferred to the thickening pass** (stated as Future Work / in-progress in the paper) | dropped from sprint |
| **Total** | **≈ 148-160 runs** | vs ≈380 full |

Protected cells (unchanged, never cut below this): A0/A0′/A1 × 4 SUTs + RQ3a curves + the
per-run provenance gates. E-items on the sprint critical path: **E1, E3, E4, E8** (+ E7 scoped
to the AutoRestTest harness). **E2 is OFF the sprint path** — RQ3b deferred exactly per the
accepted §3.3/§5 contingency (sub-claim 3 rests on its operational half). **E5 deferred**
(per-stage S-metrics limited to what E4/E8 emit naturally; no gold-producer annotation in the
sprint). E6 deferred with RQ4.

Disambiguation-evidence disclosure (Round-3 A-R3-A1): the **affinity-prior ablation carries the
multi-producer-disambiguation *evaluation* in-sprint** (the endStation mechanism); demote-only
scoring and within-run quarantine remain **active but not independently ablated** — disclosed in
the paper as design elements, not separately-measured components.

### 9-A.3 Sprint timeline with hard go/no-go gates (exits pre-committed; a NO at any gate
retargets to ICST Nov 2 — still open at every gate — or the ~Mar window, with zero work lost)

| Window | Work | Gate |
|---|---|---|
| now – Jul 19 | USER approves sprint E-items (E1/E3/E4/E8 + E7-AutoRestTest); ~~verify SANER 2027 CFP~~ **DONE 2026-07-16** (10+2pp IEEE, double-anonymous, EasyChair, abstract MANDATORY Sep 21 / paper Sep 25 / notification Dec 1 — details §9-A.1); ~~record a-main Aug-Sep phase basis~~ **DONE 2026-07-16**: a-main's capture waves are CLOSED and its completion-set (Phase A A1-A8 complete, Phase B underway per commits 631c603/2babb46/d089062) is analysis/staging-dominant — expected Aug-Sep cluster load LOW, with two identified contingencies that would contend if the user elects them (TT re-capture window [+9 units] at the seal decision; rater-study operational needs); re-validated at the G2 smoke | **G1 (Jul 19): approval in hand?** NO → revert to v3.1 pacing (ICST Nov 2) |
| Jul 19 – Aug 6 (2.5 wks) | Critical-path engineering (off-cluster) + E7-AutoRestTest harness (cluster gaps) + PROTOCOL.md (all §6.1 items incl. B-R2-A1/A2) | — |
| Aug 7 – Aug 11 | Calibration smoke: banked-runs/day under live a-main contention **+ validation of the available-run-days factor (a-main's Aug-Sep phase share) + reboot/PF-revival overhead budgeted into the run-day arithmetic (Round-3 C advisories)** + per-run duration **+ per-run cost envelope (§9-A.4a: cold-A1 TT run ≤ ~75 min AND LLM-call count within the estimated band — else re-size the run unit before banking)** + **A0′ provenance-gate operationalized as a TWO-SIDED positive control — inject one known trace-sourced value via the `span.getDataProvenance()` (`:1355`) path and one via `getTraceParameterValue` (`:1381`) and confirm the gate FLAGS both, plus one clean A0′ run the gate PASSES (Round-3 B-R3-A1: prevents a `:1355`-blind E4 from passing G2 vacuously)** | **G2 (Aug 11): E-items landed AND ≥8 banked-runs/day demonstrated at the measured per-run cost AND the two-sided gate control passes?** NO → retarget ICST (11.5 wks of runway remain) |
| Aug 12 – Sep 8 (4 wks) | Sprint matrix (~150 runs, interleaved arm×seed; a-main keeps priority) | **G3 (Sep 8): headline 80 runs banked?** NO → retarget ICST (8 wks remain) |
| Sep 3 – Sep 19 (overlapping) | Analysis + full draft; internal cold-review pass on the draft | — |
| **Sep 21** | **SANER abstract (story lock)** | — |
| **Sep 25** | **SANER 2027 full paper** | — |

Throughput arithmetic: ~150 runs ÷ ~18 run-days ≈ 8.3/day — inside the smoke-verified G2 floor,
with the §9 de-scope ladder still available beneath it (its lower rungs: continuation seeds
3→2, ablations 2 SUTs→1, AX 3 repeats→2).

### 9-A.4a Run-unit definition & per-run cost model (USER-flagged 2026-07-16: a full-TT MIST
run is expensive — traces as input, N LLM interactions, many generated tests — and the sprint
arithmetic must price this in)

*(This section consolidates two same-day passes on the user's correction — one by each active
session; merged 2026-07-16.)*

**The run unit is a SCOPED suite, never the full fault-detection suite.** Precedent for the
danger: the full TT two-stage suite ran **~6.5 h wall-clock** (2026-05-27 run of record,
`paper/tool-demo/REVIEW_ISSTA_2026.md`), and the **longest all-traces TT run on record took
~54 h** (user-reported); 148 runs at even the 6.5-h unit = ~40 days of compute — impossible.
Precedent for the fix: the MYC/a-main legs already run MIST as scoped suites (OTel: 3 seeds ×
~214 tests on 4 captured traces; TT omnibus legs: small pre-registered scenario sets). The
sprint adopts that convention. **Full-corpus runs are OUT of sprint scope** — at most ONE
optional full-scale showcase run at the very end, if the window allows, under three guardrails
(Round-4 A-R4-A2): illustrative N-of-1 **outside all statistics**; **reported if run,
regardless of outcome**; labeled un-replicated:

- **Run unit (pinned in PROTOCOL, per SUT)**: a fixed seed-trace set × a fixed operation subset
  × a fixed tests-per-op count, sized so one **cold-A1 TrainTicket run ≤ ~60-75 min wall-clock**
  (warm/A0/A0′ runs are faster) — final numbers set at the calibration smoke, frozen before any
  banked run. Working shape: TT ≈ 5 seed traces × ~10 variants each (the full paper's own eval
  scale) ≈ 50 test cases/run; other SUTs sized equivalently from their MYC precedents.
- **Trace input is a dependency, not new work — and a controlled variable**: MIST is
  trace-driven, so each SUT's **seed-trace corpus is fixed, versioned, and IDENTICAL across all
  arms, seeds, and r-rounds** (else trace variety confounds the value-source contrast).
  **Actual corpora (corrected per Round-4 C-R4-1 — per-SUT pinned trace counts are set from
  these at PROTOCOL time, not assumed)**: TT = the 5-trace eval set + omnibus material;
  OTel = 4 real captured seed traces; **TeaStore = ONE authored synthetic trace (Kieker-only)
  — the "10 MYC seeds" were 10 statistical replications of that single trace, so TeaStore runs
  a 1-trace × more-variants shape to reach a comparable #tests, disclosed**; SS = the MYC-leg
  traces (shallow GETs — see the SS health check below). **No new trace capture inside the
  sprint window**; if a corpus proves inadequate at smoke, that is a G2 NO, not an ad-hoc
  capture. **Trace-staleness threat (Round-4 B-R4-A1)**: seed traces carry concrete values
  that the B4 redeploy-invalidation cannot reach, so trace-grounded stages can degrade with
  capture-vs-execution staleness — per-run **trace-value resolvability** is recorded via the
  SUT-state covariate and disclosed (the flagship A0′-vs-A1 contrast is immune by
  construction; RQ3a sees only a constant offset).
- **LLM-interaction budget (the real cost is latency, not dollars)**: per parameter fill, the
  chain costs ~1-2 LLM calls in A0/A0′/warm-A2 (generation or extraction) and ~3-6 in cold-A1
  (discovery: service shortlist + endpoint selection per candidate; then extraction), serial at
  ~2-4 s/call — so **LLM latency, not compute, sets per-run duration**, and cold runs are the
  slowest. Estimated ~50-test TT run ≈ 0.4-2.4k LLM calls ≈ 2-8M tokens; at DeepSeek pricing
  the **sprint's total $-cost is modest (est. low hundreds of $ across ~150 runs)** — reported
  precisely per run via the E4/§6.1-#12 token accounting. Note the arms' cost asymmetry is
  itself a headline result: **the r1→r2 LLM-call/latency drop IS RQ3a's amortization curve** —
  the cost problem and the paper's registry story are the same measurement.
  **Non-censoring rule (Round-4 B-R4-A2, elevate-to-blocking if violated): cost is controlled
  ONLY by scoping fills (variants/operations, pre-banking) — NEVER by a runtime LLM-call cap;
  cold-A1 discovery always runs to completion** (a call cap would truncate exactly the metric
  RQ3a reports and artificially flatten the curve). **RQ3a scope statement (Round-4 A-R4-A3):
  the amortization claim is scoped to recurring-workload discovery-cost amortization (the same
  pinned corpus re-run); value caches + the verified pool are invalidated between r-rounds
  (B4), so r2+ gains come from mapping reuse, not cache-warming — stated in the paper.**
  **No LLM-on precedent caveat (Round-4 C-R4-3): run22's 6.5h was an LLM-OFF run of 15,036
  tests and the MYC legs ran LLM-off — the ≤75-min envelope is a bottom-up estimate with no
  measured precedent; G2 is the first LLM-on measurement, and the smoke week reserves slack
  for one bust→re-size→re-measure cycle before Aug 12.**
- **G2 gains a cost criterion** (added to the gate row): measured per-run wall-clock and
  LLM-call count for a cold-A1 TT run must fit the pinned envelope (≤ ~75 min; calls within the
  estimated band) — else the run unit is re-sized (fewer variants/operations) or the ladder
  engages BEFORE banking begins. The ≥8 banked-runs/day floor is thus derived from measured
  per-run duration + a-main gap share + reboot/PF overhead, not assumed.
- **Comparability guard**: the fixed run unit (same traces, same ops, same tests-per-op,
  enhancer settings held at MIST defaults and identical across arms; two-phase OFF) is what
  makes #tests the budget unit (§6 stats) fair across arms.
- **Capacity model = machine-hours with unattended execution** (merged from the parallel pass):
  148 scoped runs × ≤75 min-target (2 h hard cap) ≈ 185-300 machine-hours ≈ ~7-11 h/day over
  the 4-week window — met by **scripted, unattended, per-run-banked execution** (each run banks
  independently; overnight/gap scheduling is the a-main-contention mitigation; the MYC driver
  pattern already supports this). If a cold-A1 run busts the 2-h hard cap at smoke, the re-size
  rule is variants-first (V=10→5 halves run cost) before touching the seed-trace count —
  **and a cap bust is handled by discard-and-rerun after re-sizing, never by mid-run truncation
  (Round-4 B-R4-A3; added to §6.1 #10)**. **The unattended scheduler consumes the
  pre-registered interleaved arm×seed order — it must not batch-by-arm for convenience
  (Round-4 B-R4-A4; protects the B3 order-confound control).** **The G2 smoke runs as a REAL
  overnight batch including one deliberate mid-batch wedge (kill a port-forward/pod) to
  measure unattended-batch SURVIVABILITY — lost-slot cost, auto-recovery or safe-halt — not
  just reboot-revival cost (Round-4 C-R4-2).** **SockShop health check at smoke (Round-4
  C-R4-4): validate a healthy LLM-on write path first (the only SS run of record was
  degenerate: 100% of writes 500'd, LLM-off, shallow-GET traces); the paper's scale claim
  pre-states graceful degradation from 4 systems to 3 if a SUT fails health.**
- **Scale-claim consistency**: with scoped runs, the paper's scale statement is "N workflow
  scenarios spanning M services / P operations per SUT" (the pinned corpus's actual span,
  stated per SUT) — NOT "all of TrainTicket". §9-A.5's sufficiency argument is read
  accordingly: breadth = 4 systems × scenario span; depth = the multi-step workflows;
  full-corpus breadth = future work. (Also the honest reading of the comparison literature —
  the 10-12-API-norm papers run budgeted sessions per API, not exhaustive corpora.)

### 9-A.4 Post-SANER pipeline (nothing is wasted)

- **Accept** → camera-ready + thickening items become the artifact/extension work.
- **Reject (notification ~Dec 2026)** → thicken per §6's full matrix (10-seed headline cells,
  full ablations, EvoMaster BB + RESTler, RQ4/RQ5, E2 → RQ3b feedback-ranking) → **ICWS/ICSME
  2027 (~Mar, CCF-B)**; ISSRE 2027 (~Jul) remains the third gate (fault-detection re-lead per
  §9). The SANER submission is v1 of a strictly-growing study, not a one-shot bet.

### 9-A.5 Sufficiency-for-SANER argument (what Round 3 confirms)

- **Scale**: 4 microservice systems / ~70+ services (**systems context, not evaluation
  coverage** — per-run coverage is the §9-A.4a pinned scenario span, stated per SUT; Round-4
  A-R4-A1) / operation totals stated — the *systems-scale* comparison is at or above the
  10-12-single-API norm of the literature (whose runs are budgeted sessions too); SANER's own
  accepted empirical/tool papers do not exceed this bar (venue scan §2). Degrades gracefully to
  3 systems if a SUT fails the smoke health check (C-R4-4).
- **Baselines**: 3 controlled within-tool contrasts (A0 marginal, A0′ absolute, lexical) + 1
  external SOTA LLM-era tool (AutoRestTest, the sharpest comparator) + disclosed-if-absent
  second tool — vs KAT's single-baseline precedent at the tier; the full quintet remains the
  thickening target.
- **Rigor**: pre-registered PROTOCOL (all 12 §6.1 items), provenance gates, interleaved
  execution, Holm-Bonferroni + Â12/CI, 5 seeds disclosed as the sprint's power tradeoff.
- **Fit**: evolution-led framing (9-A.1) aims the paper at SANER's actual identity rather than
  importing an ICST-shaped testing pitch.
- **Honesty**: RQ4/RQ5/RQ3b explicitly deferred and stated; no claim rests on deferred work.

## 10. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Novelty read as "producer-consumer with an LLM" | §3 positioning: out-of-band live retrieval vs in-sequence dependency resolution; covers pre-existing/seeded data, cross-service gaps, bootstrap; RQ4 shows component value beyond MIST. |
| **AutoRestTest punctures a "semantic routing" claim (SPDG is already semantic)** | Adopted: sub-claim 1 rests on live-retrieval routing across a multi-service registry, with the explicit "we do not claim semantic-vs-syntactic" sentence; AutoRestTest preempted twice (SPDG + value agent). |
| **"Feedback-ranked registry" oversold while successRates are all 0.0 and E2 is unlanded** | Adopted: contingent-claim split (§3.3), RQ3a/RQ3b decomposition, E2 novelty-critical flag with re-scope rule. |
| **A0 mislabeled "LLM-only" (trace grounding + pool narrowing ungated — code-verified)** | Adopted: A0 relabeled marginal contrast + new A0′ absolute contrast + per-run provenance gates + registry lifecycle rules. |
| **Grounded-share inflated/under-covered/self-reported** | Adopted: raw-enum definition ({RESOLVED_LIVE, RESOLVED_CACHE}), `isLiveGrounded()` forbidden, E4 all-sites wiring or bounded per-site coverage, independent corroboration metric pre-committed, grounded-share never a headline outcome. |
| EvoMaster "harvesting" naming collision | Preempt early in Related Work quoting `probOf*` semantics: outbound-dependency mocks + SQL vs our inbound read-surface GETs. |
| KAT (ICST'24) adjacency at the target venue | Delta on two co-equal axes (live fetch; persistent cross-run registry); conceptual-by-necessity disclosed; AutoRestTest carries the empirical burden. |
| Our own June note calls the grounding fixes "field-standard, not novel" | That note graded two *individual mechanisms* against the a-main bar with grounding deliberately descoped; the paper claims the *composed mechanism* + measured effect, and the same note certifies the composed problem open. Cite the individual-mechanism precedents exactly as the note does. |
| Old D4 numbers are shaky | Never headlined; re-measured via E4; old numbers only as caveated priors. |
| Grounded-share ≠ validity (endStation lesson) | Both axes separate in every table; disambiguation machinery evaluated by ablation. |
| JIT-bind / negative-mode-"smart" confounds | Scope statement §2; E1 pre-step verifies the hold-constant knob before execution; unrelated knob documented. |
| SUT state pollution / execution-order confound (TT DB growth) | Interleaved arm×seed order; SUT-state covariate; redeploy resets at seed boundaries where feasible; registry lifecycle per arm; never `generatedb` mid-run. |
| External baselines won't run | AX = expected baseline set with a must-run pair (AutoRestTest + one of EvoMaster BB/RESTler); failures disclosed, never silently dropped; primary claims still rest on controlled within-generator arms. |
| LLM nondeterminism / provider drift | Pinned model+temp, 10-seed headline cells, medians+IQR+Â12+CI, Holm-Bonferroni; warm-registry arms reduce LLM in the loop; per-prompt-type token accounting. |
| Run-matrix vs shared-cluster wall-clock | §6 matrix count + throughput assumption stated; calibration smoke re-sizes before banking; pre-committed de-scope ladder; protected cells named. |
| "Only 4 systems" | ~70+ services / explicit operation totals; field norm is 10-12 single-service APIs — at or above it in operation count. |
| Two papers in parallel overload | No rater/IRB path here; engineering user-gated; cluster contention managed by a-main-priority + off-cluster E-window. |
| Stale internal docs leaking into the paper (JSONPath claim, "50%" split, dead classes) | §2 pins current truth; paper text written from `research/codebase-inventory.md`, never from the frozen process doc or old main.tex wording. |
| **[SPRINT] 9.5-week SANER window slips** | Three hard go/no-go gates (G1 Jul 19 / G2 Aug 11 / G3 Sep 8) with pre-committed exits to ICST Nov 2 (open at every gate) or ~Mar; the sprint bets the calendar, never the protocol — no gate can be "argued past". |
| **[SPRINT] SANER PC-fit (zero LLM-REST precedent)** | Evolution-led framing (§9-A.1): the evolving producer registry is the headline, live-fetch the mechanism; RQ3a promoted to co-lead. |
| **[SPRINT] 5-seed power + single external baseline read as thin** | Disclosed as sprint tradeoffs with Â12+CI carrying evidence; second tool disclosed-if-absent; the thickening pipeline (§9-A.4) is stated in the paper so reviewers see the trajectory, not a ceiling. |
| **[SPRINT] Electing SANER forfeits ICST 2027** | Acknowledged in §9-A; ICST remains the retarget at every gate BEFORE submission; after submission the fallback chain is ICWS/ICSME ~Mar → ISSRE ~Jul (SANER notification Dec 1 keeps it workable). |
| **[SPRINT] Double-anonymous compliance (CFP-verified)** | Writing-window items: tool pseudonym, third-person self-citation of the MIST papers, anonymous artifact mirror (the public MIST repo de-anonymizes); Data Availability statement drafted with the anonymized links; checked at the internal draft review before submission. |
| **[SPRINT] Per-run cost blowup (trace-driven input + N serial LLM interactions + test-suite size — the full-TT precedent is 6.5h/run)** | §9-A.4a run-unit definition: scoped suites only (TT ≈ 50 tests/run), fixed versioned seed-trace corpora identical across arms (no in-sprint capture), LLM-latency-aware envelope (cold-A1 TT ≤ ~75 min), G2 cost criterion re-sizes the unit before any banking; the r1→r2 cost drop is itself RQ3a's amortization result. |
| Service-annotation dependency (Bookinfo zero-services hallucination case) | Disclosed in §2 + Threats; framed as an operating requirement (x-service-name or service-suffixed tags), with Bookinfo as the documented negative case. |

## 11. Relation to the other track (no-double-claim policy)

- `debug/a-main/` = A-venue main track (masked-2xx read-back oracle benchmark + rater study).
  Claims: trace-shape oracle / hidden-downstream detection / attribution. Its own record
  declares value grounding "supporting machinery, not claimed as a contribution" there — no
  claim collision; a-main can cite this paper for the machinery.
- The MIST full paper (`paper/full-paper/main.tex`) and the ISSTA 2026 tool-demo material
  present SmartFetch as a component figure/paragraph with **zero quantitative smart-fetch
  evaluation anywhere in `paper/`** (grep-confirmed) — this paper adds the first, duplicating
  nothing. (It also corrects two stale component descriptions; see §10.)
- `debug/b-smartfetch/` = this track (B-venue, SmartFetch as protagonist). Claims: the grounding
  mechanism + its evaluation. It does NOT claim MIST's oracles, trace-driven scenario
  generation, or the benchmark corpus.
- Shared infrastructure only (cluster, SUTs, driver patterns); a-main has cluster priority.

## 12. Review gate log

| Round | Reviewer A (novelty/PC) | Reviewer B (experiment soundness) | Reviewer C (feasibility/scope) | Outcome |
|---|---|---|---|---|
| 1 (2026-07-15, on v2) | REVISE (2 blocking) | REVISE (6 blocking) | REVISE (1 blocking) | All 9 blocking + 24 advisory adopted → v3 (`REVIEW-PLAN-RECONCILIATION.md`) |
| 2 (2026-07-16, on v3) | **ACCEPT** (0 blocking; 1 venue-contingent advisory) | **ACCEPT** (0 blocking; R2-A1/A2 must-address-in-PROTOCOL + 4 refinements) | **ACCEPT** (0 blocking; R2-1/R2-2 advisories) | **GATE PASSED 3/3.** Four concrete Round-2 advisories adopted post-accept per the reviewers' own wording → v3.1: §6.1 #1 A0′ full grounding-site enumeration incl. `span.getDataProvenance()` (B-R2-A1); §6.1 #3 corroboration positive control (B-R2-A2); §9 plan-of-record = on-bar CCF-B ~Mar window with ICST Nov 2 as the user-electable opportunistic shot (C-R2-1); §9 smoke charter = banked-runs/day under contention (C-R2-2). Reviewer A's ISSRE-re-lead advisory folded into §9's ISSRE sentence. |

| 3 (2026-07-16, on v4 §9-A; scope = sufficiency-for-SANER, venue user-decided) | REVISE→**ACCEPT** (R3-B1 "learns/evolves" headline over-reach fixed per reviewer wording — hook re-scoped to accumulates/self-amortizes, title recalibrated, outcome-learning confined to future-work; R3-A1 disambiguation disclosure added) | **ACCEPT** (0 blocking; R3-A1 two-sided A0′ positive control incl. `:1355` injection folded into G2) | **ACCEPT** (0 blocking; arithmetic re-verified: 148 runs, 8.22/day vs ≥8 floor with ladder cushion to ~6.8, gate runways 11.9/7.9 wks; G1 page-limit check + smoke run-days validation + reboot overhead folded in) | **GATE PASSED 3/3 → v4 = SANER SPRINT PLAN OF RECORD** |
| 4 (2026-07-16, delta: §9-A.4a run-unit & cost model; successor reviewers — original reviewer transcripts expired, threads continued from the on-disk review files) | **ACCEPT** (0 blocking; R4-A1 scale-rule wiring, R4-A2 showcase guardrails, R4-A3 RQ3a recurring-workload scope — all folded) | **ACCEPT** (0 blocking; R4-A1 trace-staleness covariate+disclosure, R4-A2 non-censoring rule [no runtime call caps], R4-A3 discard-and-rerun, R4-A4 scheduler consumes interleaved order, R4-A5 per-arm call bands as A0′ cross-check — all folded) | **ACCEPT** (0 blocking; **R4-1 HIGH factual fix: TeaStore corpus = ONE authored synthetic trace, not "10 MYC seeds" — corrected pre-G1**; R4-2 overnight-batch survivability wedge test at smoke; R4-3 no-LLM-on-precedent caveat [run22's 6.5h was LLM-OFF] + bust→re-size slack; R4-4 SS health check + 4→3-systems graceful degradation — all folded) | **GATE PASSED 3/3.** USER flagged full-TT run cost (6.5h LLM-off run-of-record, ~54h all-traces; trace-input dependency; N serial LLM calls) → §9-A.4a scoped run-unit + cost model, consolidating both sessions' same-day passes; all 12 Round-4 advisories folded per reviewer wording. |

**Next actions (gate passed)**: (1) **USER approves the sprint E-items (E1/E3/E4/E8 +
E7-AutoRestTest) — this IS the G1 gate, target Jul 19**; also at G1: verify SANER 2027 page
limit/format from the official CFP. (2) PROTOCOL.md authored per §6.1 → sprint E-window begins
Jul 19. Companion-demo decision (ICSE 10/23 / AST 10/30) stays open — both fall between SANER
submission and notification, so a SmartFetch demo there is compatible.
