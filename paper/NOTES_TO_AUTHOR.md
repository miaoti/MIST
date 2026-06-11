# NOTES_TO_AUTHOR — ICSME 2026 Tool Demo

> Living document. Read top-to-bottom before each writing session. Resolve every `Q*` and `TODO` before submission.

---

## Phase 1 — Discovery report (preserved)

### Tool name (verbatim)

**MIST** — *Microservice Integration & Scenario Tester*. User-facing brand is **MIST**; the underlying implementation keeps **MST** in class names (`MstConfig`, `MstAuthHandler`, ...), config keys (`mst.config.path`, `mst.generate.only.first.step`), the `generator=MST` value, and `trainticket-mst.properties`. The brand layer is in the README, this paper, and the abstract; the code layer matches RESTest convention. Built on top of [RESTest](https://github.com/isa-group/RESTest).

### One-sentence elevator pitch (synthesis)

MIST turns OpenTelemetry/Jaeger traces and OpenAPI specs into runnable, cross-service workflow tests with LLM-generated parameters, single-fault sniper mutations on root APIs, and trace-aware oracles, replayed against microservice systems that today's REST API testing tools handle only weakly.

### Architecture (5 components, mapped to source)

| Paper-side name | Repository class / module | Path |
|---|---|---|
| (i) Spec Ingestor | `OpenAPISpecification` + `MicroserviceTestConfigurationGenerator` | `src/main/java/.../specification/`, `.../configuration/multiservice/` |
| (ii) Semantic Dependency Registry + JIT Binder | `SemanticDependencyRegistry`, called from `MultiServiceTestCaseGenerator.traverse(...)` | `src/main/java/.../registry/` |
| (iii) Sequence Generator (Root API Mode) | `TraceWorkflowExtractor` (5 phases) → `MultiServiceTestCaseGenerator` (gated by `mst.generate.only.first.step=true`) | `.../workflow/`, `.../generators/MultiServiceTestCaseGenerator.java` |
| (iv) Sniper Mutator | Round-robin negative mode (`faulty.round-robin=true`); pool: `HardcodedInvalidInputGenerator`; selection: `MultiServiceTestCaseGenerator` | `.../inputs/HardcodedInvalidInputGenerator.java` |
| (v) Trace-Aware Oracle | Jaeger fetch in writer-emitted code; `TraceErrorAnalyzer`; `SoftErrorRuleCache`; `FaultDetectionTracker` | `.../writers/restassured/MultiServiceRESTAssuredWriter.java`, `.../analysis/`, `.../validation/` |

### Data flow (8 stages)

Entry → spec+traces → 5-phase scenario pipeline (Phase 1 cross-trace data merge → Phase 2 session merge → Phase 2.5 dedup → Phase 3 component shattering → Phase 4 baseline decomposition) → variant generation → test emission → in-process execution → optional enhancer loop → reporting.

### TrainTicket integration evidence

| Asset | Path | Stat |
|---|---|---|
| Merged OpenAPI spec | `src/main/resources/My-Example/trainticket/merged_openapi_spec 1.yaml` | 265 operations |
| MIST test configuration | `src/main/resources/My-Example/trainticket/real-system-conf.yaml` | 37 `ts-*-service` entries (black-box-discovered) |
| Recorded traces | `src/main/resources/My-Example/trainticket/test-trace/` | 1 file: `traces-1772605095842.json` |
| Injected-fault registry | `injectedFaults/injected-faults.json` + `INJECTED_FAULTS.md` | 10 faults across 5 services |
| Bundled config | `trainticket-demo.properties` + `trainticket-mst.properties` | Public TrainTicket: `http://129.62.148.112:32677` |
| Algorithm doc | `flow.md` | 1672 lines |

### Suggested figures (final state)

| # | Figure | Source |
|---|---|---|
| 1 | Architecture diagram (5 components, dotted SUT boundary) | `paper/figures/architecture.tex` (TikZ, generated) |
| 2 | Real captured Jaeger trace (gateway → admin-route → route → MySQL) from this repo's allure-results | `paper/figures/trace_oracle.tex` (TikZ, faithful to trace `032af4e6...f480`) |
| 3 | (Omitted in v1 draft) CLI screenshot — see Q9 |

---

## Phase 1 questions — answers and resolution status

| # | Resolved? | Notes |
|---|:---:|---|
| Q1 | ✅ | Service count framed as "37 black-box-discovered services" — written that way in §3 |
| Q2 | ⚠️ partial | Author confirmed 10/10 detection on the most-current run, **but that run's report is not on this machine.** The local most-recent report (`fault-detection-summary-...1778039778981-20260506-172440.txt`) shows 4/10. Table I cites 10/10 per author confirmation; the per-fault \textbf{Mechanism} column is left as `\todo{mech}` until the current report is on this machine. See Inconsistencies §1 below. |
| Q3 | ✅ | Variant count `2733` extracted from the local-most-recent report. Run time `~20h, ~12h LLM` written verbatim per author. **If the most-current run has different numbers, replace 2733 before submission.** |
| Q4 | ✅ | Canonical model identifier in the codebase: `deepseek-chat` (in `trainticket-mst.properties` and `deepseek-config.properties`). Wrote `DeepSeek deepseek-chat` in §5. The author's "v4flash" appears to be colloquial; not used in the paper. |
| Q5 | ⚠️ partial | Used real captured trace `032af4e629d9075292977fc74a20f480` from `src/main/resources/My-Example/trainticket/allure-results/00fc19f1-...txt`. It is a real MIST/TrainTicket capture from this repo. **It is not from the most-current 10/10 run** (that run's allure data is on the other machine). The figure caption is honest about this. If a fault-detection trace from the current run is available, swap it in. |
| Q6 | ✅ | `paper/refs.bib` populated; entries with uncertain DBLP keys carry a `note = {TODO-...}` line. Verify each on DBLP before submission. |
| Q7 | ✅ | `\author{}` block uses `\todo{AUTHOR NAMES}` and `\todo{AFFILIATION, EMAIL}`. Single-anonymous track (authors visible) — fill before submission. |
| Q8 | ✅ | `https://github.com/miaoti/MIST/` written in abstract and §6. |
| Q9 | ⚠️ pending | Abstract ends `Screencast: \url{\todo{SCREENCAST-URL}}`. Record + replace before submission. |
| Q10 | ✅ | DOI omitted from this draft (no Zenodo plan). Add for camera-ready if accepted. |
| Q11 | ✅ | Ablation dropped (paper has no ablation section). |
| Q12 | ✅ | Author placeholder per Q7. |

---

## Inconsistencies found (between artifacts on this machine)

### §1. Fault detection rate: local report says 4/10, author says 10/10

The most recent fault-detection report file on this machine is:

```
logs/fault-detection-reports/fault-detection-summary-trainticket_twostage_test_1778039778981-20260506-172440.txt
```

It reports:
- Total Test Cases: **2733**
- Detected Faults: **4 (40.0%)** — INSUFFICIENT_STATIONS_FAULT, INVALID_CONTACTS_NAME_FAULT, INVALID_SEAT_NUMBER_FAULT, INVALID_STATION_LENGTH_FAULT
- Undetected Faults: **6** — INVALID_PRICE_RATE_FAULT, INVALID_ROUTE_ID_FAULT, INVALID_STATION_NAME_FAULT, INVALID_STATION_NAME_LENGTH_FAULT, INVALID_TRIP_ID_FORMAT_FAULT, INVALID_TRIP_ID_LENGTH_FAULT

The author has confirmed via direct message that the 10/10 detection result is from a more recent run that is **not on this machine**. The paper goes with 10/10 per author truth.

**Action before submission:**
1. Copy the most-current fault-detection report onto this machine.
2. Verify the 10/10 number and the run identifier; update the footnote in §5 ("fault-detection report `trainticket_twostage_test_1778039778981`") to the current run's identifier if different.
3. Fill the **Mechanism** column of Table~\ref{tab:faults} from the current report, drawing each row from one of: HTTP $\geq 4xx$, LLM soft-error rule, Jaeger error span, injected-fault registry match.

If the current report does not exist as a structured artifact in the runtime, it is also acceptable to re-run the bundled demo on this machine to re-derive it; that run is reproducible by construction (same .properties file, same OAS spec, same trace input, deterministic with `random.seed=...`).

### §2. Field name: `injected` (code) vs `isInjected` (docs)

`MultiServiceRESTAssuredWriter.java:1820` reads `dataObj.optBoolean("injected", false)` from the response body. `INJECTED_FAULTS.md` documents the same field as `isInjected`. Verified via the captured Allure attachment `00487ff8-...txt`: the actual response uses `"injected":true` (matching the code, not the doc). The doc is wrong; the code is right. **Action**: fix `INJECTED_FAULTS.md` to use `injected` to match the deployed contract. (Low priority for the paper; high priority for repository hygiene.)

### §3. Variant count: 2733 in local report

The 2733 figure used in §5 footnote comes from the local-most-recent report. The most-current run (the 10/10 run) may have a different variant count. Re-extract before submission.

---

## Phase 2 — TODO ledger

### Resolved during writing

- All five §3 component descriptions written from the actual code paths
- Figure 1 (architecture) generated as TikZ
- Figure 2 (trace-as-oracle) drawn from a real captured Jaeger trace in this repo, with honest caption
- Algorithm 1 (Sniper) written from the actual `MultiServiceTestCaseGenerator` round-robin code path
- Bibliography populated; uncertain entries marked `note = {TODO-...}`
- Citation count: 14 entries — well within the ~20 budget

### Still open in `paper/main.tex`

- `\todo{AUTHOR NAMES}` and `\todo{AFFILIATION, EMAIL}` in `\author{}` block
- `\todo{SCREENCAST-URL}` in the abstract's last sentence
- 10 × `\todo{mech}` in Table~\ref{tab:faults} mechanism column

### Asset gaps

- Most-current fault-detection report (Q2)
- Most-current variant count (Q3) — currently 2733 from local report
- Most-current run identifier — currently `trainticket_twostage_test_1778039778981`
- (Optional) Allure CLI screenshot for an optional Figure 3

### Verification list (re-check before submission)

- 265 operations in OpenAPI spec — counted directly from the YAML
- 37 black-box-discovered services — counted from `real-system-conf.yaml`
- 10 injected faults — counted from `injectedFaults/injected-faults.json`
- 5 services with injected faults — per `INJECTED_FAULTS.md`
- 8 fault categories (TYPE_MISMATCH, REGEX_MISMATCH, SEMANTIC_MISMATCH, OVERFLOW, EMPTY, NULL, SPECIAL_CHARACTERS, BOUNDARY_VIOLATION) — counted from `flow.md`
- DeepSeek model = `deepseek-chat` — verified in two config files
- 2733 variants — only from local report, may need update
- ~20h / ~12h LLM split — author-stated, not auto-extractable from artifacts on this machine

---

## Phase 2 follow-up — author review fixes (5 risks + 1 polish)

Author flagged five reviewer-bait issues plus one positive polish on the Phase 2 draft. All addressed:

| Risk | Fix applied |
|---|---|
| **R2** — Figure 2 (happy-path GET trace, all-200 spans) did not match §2 (POST /adminbasic/prices, INVALID_PRICE_RATE_FAULT). | Located a real fault-injecting trace in this repo's Allure attachments: trace ID `d4c577d47bcba04a00ef9b3edcbcacf6`, test `test_negative_POST_1_81` against `POST /adminrouteservice/adminroute`, response body `{"status":0,"msg":"start or end station not include in stationList.","data":null}` with HTTP 200 on every span. Redrew Figure 2 from this real trace and rewrote §2 to match. The new §2 example is **honest** about HTTP 200 (the previous draft incorrectly claimed INVALID_PRICE_RATE_FAULT returns 200; per `INJECTED_FAULTS.md` and the corresponding captured trace `ba375a3372e88399a1f8b4a1c40a8795`, that fault actually returns HTTP 400 -- another inconsistency, see §4 below). |
| **R3** — "black-box auto-discovery" framing in §3.(i). | Replaced with: "Parses an OpenAPI specification --- obtained from the SUT operator or aggregated from per-service `/v3/api-docs` endpoints --- and emits a multi-service test configuration. The bundled TrainTicket spec contains 265 operations across 37 REST-exposed services, defining MIST's test scope. Components without a discoverable HTTP surface (asynchronous workers, message brokers, internal-only RPC services) lie outside any black-box tool's reach by construction." |
| **R4** — §4.2 "ablation-style use" sentence invited reviewers to ask for an ablation we don't have. | Changed to: "This paper reports only the root-only mode." |
| **R5** — 12h LLM vs "cache caps at $\sim$2 calls per API" looked like bad arithmetic. | §5 rewritten to clarify cost distribution: the bulk of LLM cost is positive-input synthesis (one prompt per parameter per variant under `testsperoperation=100`), and the soft-error rule cache contributes a one-time $\sim$2 prompts per distinct API. Reviewer can now do the arithmetic and it lands. |
| **Polish** — three contributions previously stood alone; reviewer could attack each as incremental. | Added one sentence at the start of §4: "The Sniper Strategy's single-fault discipline is what makes Trace-as-Oracle's downstream signals causally interpretable; Root API Mode's external-only driving is what allows traces to be the primary oracle without confounding from direct internal calls. Either of the three on its own is incremental; the combination is what closes the oracle and state-fabrication gaps together." |

## Inconsistencies found (post-Phase-2-review additions)

### §4. The previous §2 motivating example fabricated "HTTP 200" for `INVALID_PRICE_RATE_FAULT`

In the Phase-2 first draft, §2 said `POST /adminbasic/prices` with a non-positive rate "returns *HTTP 200 OK*". This was wrong: per the bundled `INJECTED_FAULTS.md` and the captured trace `ba375a3372e88399a1f8b4a1c40a8795`, that fault actually returns HTTP **400** with the soft-error envelope. The error originated from carrying over the user's earlier framing without verifying against the real captured response body and trace.

Fix: §2 was rewritten around `POST /adminrouteservice/adminroute` with a soft-error response that *is* genuinely HTTP 200 (verified: real test `test_negative_POST_1_81`, real trace `d4c577d47bcba04a00ef9b3edcbcacf6`). The motivating example is now backed by data on this machine.

Lesson for the rest of the paper: every concrete claim about the SUT's response shape should be re-checked against the captured Allure attachments before submission. There may be other small claims that drifted from the data (e.g., "every span is 200" -- verified for trace `d4c577d4...acf6`; if some other fault path has a different shape, the §4.3 bullet on "no descendant carries error frames" is correct only for this case).

---

## Phase 3 — Self-review

### Estimated page count

Counting the IEEEtran two-column 10pt layout, lines per element:
- Abstract: ~7 lines column-equivalent
- §1 Introduction: ~25 lines
- §2 Background: ~14 lines + 4-line listing
- §3 Architecture: ~37 lines + Figure 1 (full-page-width, ~14 lines)
- §4.1 Sniper: ~22 lines + Algorithm 1 (10 lines)
- §4.2 Root API Mode: ~14 lines
- §4.3 Trace-as-Oracle: ~16 lines + Figure 2 (full-page-width, ~16 lines)
- §5 Case study: ~22 lines + Table I (12 lines)
- §6 Related Work + Conclusion: ~22 lines
- Bibliography: 14 entries, ~4 lines each = ~56 lines

Total estimate: roughly **5.0–5.2 pages** with both figures spanning full width. **Tight.** First trims if over budget:
1. Drop the "End-to-end flow" paragraph at the bottom of §3 (~5 lines, redundant with Figure 1).
2. Compress Figure 2 caption from 4 sentences to 2.
3. Drop one or two of the four bullet points in §4.3 (the Trace-as-Oracle assertion families).
4. Move the soft-error cache sentence in §4.3 into a footnote.
5. Compress Algorithm 1 from 9 lines to 6 (merge lines 5–7 into one line).

Do **not** cut: the motivating example in §2 (it is the visual hook), Figure 1 (the architecture is the demo), Figure 2 (the trace-as-oracle visual), or the BibTeX entries for the eight prior REST-API-testing tools (positioning is required for tool-demo review).

### ICSME tool-demo review criteria

1. **Value, usefulness, and reusability of the tool.** *Strong.* Three named contributions (Sniper / Root API Mode / Trace-as-Oracle), open-source code, bundled demo with one-command launch, configurable LLM backend, fault-detection report. *Weak.* Table I has `\todo{}` cells until the current report lands. Soft-error cache adds an LLM dependency that some reviewers may flag as a reproducibility hazard — defended in §4.3 by the cache's bounded call count.

2. **Quality of presentation.** *Strong.* Two real figures, one algorithm, one results table; concrete CLI invocation in §5; concrete numbers (265 ops, 37 services, 10 faults, 2733 variants, 20h, 12h LLM) throughout. *Weak.* Three `\todo{}` blocks must be filled. Figure 2 caption is honest about source asymmetry.

3. **Clarity of relation with previous work.** *Strong.* Eight prior REST API testing tools cited with one-sentence positioning each. The "first to use traces as primary oracle for REST API testing" claim is conservatively stated and defensible. *Weak.* Trace-based testing in OTHER domains (microbenchmarks, anomaly detection) is broader than the §6 paragraph implies; if a reviewer flags this, the rebuttal is "we restrict the claim to REST API testing tools, which we cite eight of."

4. **Availability of the tool.** *Strong.* Public GitHub URL, LGPL 3.0 license inherited from RESTest, one properties file launches the bundled demo, requirements documented. *Weak.* Screencast URL is `\todo{}`. Java 11 requirement may exclude readers on JDK 21+ — `mvn -DskipTests compile` is verified on JDK 21 (this session), but the runtime path uses Java 11 as compile target. If a reviewer cannot run on their JDK, that is a footgun; consider testing on JDK 17 + JDK 21 before submission.

### Top 5 reviewer objections + rebuttal plan

1. **"`\todo{mech}` in Table I is unprofessional."** *Rebuttal:* the cells will be filled from the current report; in this draft they are explicit gaps for author review. Camera-ready will be clean.

2. **"How is this different from RESTest? Just an MST mode?"** *Rebuttal:* §3 makes the boundary explicit ("MIST is built on top of RESTest's MST mode"), and the three named contributions (Sniper Strategy, Root API Mode, Trace-as-Oracle) are independent of the underlying generator framework — the reviewer should evaluate those as the contribution, not the codebase split.

3. **"Trace-based oracles are not new (cite Dapper, OTel, anomaly detection literature)."** *Rebuttal:* §6 stipulates the claim is "first openly available REST API testing tool that uses span-level evidence as a primary oracle." The novelty is the integration into a REST testing flow, not the invention of distributed tracing.

4. **"20-hour run time is unreasonable."** *Rebuttal:* §5 itemizes the cost as 12h LLM inference latency + 8h pipeline. The Sniper Mutator and Root API Mode are LLM-free; the cost is in input synthesis and soft-error validation. The soft-error rule cache caps the LLM cost at ~2 calls per distinct API for validation; remaining LLM cost is variant generation, which is configurable via `testsperoperation` and `faulty.ratio`.

5. **"Why only 10 faults? Industrial systems have hundreds."** *Rebuttal:* the 10 are representative across the documented TrainTicket validation gates and the 8 fault categories that MIST's Sniper Mutator generates. They are designed to exercise each mutation type at least once. We do not claim exhaustive coverage; we claim the architecture detects the 10 documented faults.

---

## Pre-submission checklist

- [ ] Paper compiles cleanly with the official `IEEEtran.cls` (placeholder removed)
- [ ] PDF is exactly 5 pages including references — first proof in TeX, run trims if needed
- [ ] Abstract ≤200 words, ends with `Screencast: <URL>` (Q9 resolved)
- [ ] Every `\todo{...}` in `main.tex` resolved (currently: AUTHOR NAMES, AFFILIATION+EMAIL, SCREENCAST-URL, 10× mech)
- [ ] Q1–Q12 all answered above
- [ ] All citations resolved (`note = {TODO-...}` removed from refs.bib)
- [ ] Repository URL public or reviewer-accessible link in place
- [ ] Screencast URL embedded
- [ ] LICENSE in repo (LGPL 3.0 inherited from RESTest — confirm acceptable for ICSME)
- [ ] First-author affiliation + email correct
- [ ] No reviewer-trigger phrases ("perfect accuracy", "all values 1.000", "we solve X", "novel/elegant/powerful")
- [ ] Bibliography uses IEEEtran style; final entry count ≤20
- [ ] Figures are vector or ≥300 dpi raster — both figures are TikZ, vector by construction
- [ ] All figure captions self-contained
- [ ] Most-current fault-detection report copied onto submission machine and Table I mechanism column populated
- [ ] Inconsistency §2 (`injected` vs `isInjected`) fixed in repo's `INJECTED_FAULTS.md` if author chooses


---

## ISSTA 2026 conversion

`paper/main.tex` (ICSME 2026, IEEEtran) is preserved unchanged. The ACM
sigconf variant lives at `paper/main_issta.tex`. Build instructions for both
are in `paper/README_ISSTA.md`.

### Mechanical changes

| Change | From | To |
|---|---|---|
| Document class | `\documentclass[conference]{IEEEtran}` | `\documentclass[sigconf,screen,review]{acmart}` |
| IEEE override | `\IEEEoverridecommandlockouts` | removed |
| Package list | `cite, url, hyperref` (and `cite`) loaded explicitly | removed; acmart loads them internally and a second load would clash |
| Title block | IEEEtran `	hanks{...}` in `	itle` | `cmConference[...]` + `cmBooktitle{...}` |
| Author block | `uthor{\IEEEauthorblockN{...}\IEEEauthorblockA{...}}` | `uthor{...}ffiliation{\institution\city\country}\email{...}` (single-blind: all `	odo{...}` placeholders, visible at submission) |
| Bibliography style | `\bibliographystyle{IEEEtran}` | `\bibliographystyle{ACM-Reference-Format}` |
| Maketitle ordering | `\maketitle` before `\begin{abstract}` | `\maketitle` after `\end{abstract}` (acmart convention) |
| ACM metadata added | n/a | `\setcopyright{rightsretained}`, `cmISBN`, `cmDOI`, `cmPrice`, `cmYear`, `\copyrightyear`, `cmConference`, `cmBooktitle` |
| CCS concepts added | n/a | one primary (`Software testing and debugging`, 500), one secondary (`Network services`, 300) |
| Keywords added | n/a | REST API testing, microservices, distributed tracing, fault injection, large language models |

### Content changes (compression for the 4+1 page budget)

| Cut | Location | Justification |
|---|---|---|
| §3 final paragraph "End-to-end flow" | end of architecture section | redundant with Figure 1; compression target #1 from the task spec |
| Related Work first paragraph | §6 | tightened from a list of eight named tools to two named comparisons (EvoMaster, RESTest) + one parenthetical citation block covering the other six; compression target #2 from the task spec |
| Figure 2 caption | §4.3 | trimmed from three sentences to two (removed the redundant "source: this repository's Allure attachments" sub-clause) |
| Algorithm 1 lines 6-7 | §4.1 | merged the "lock" and "fill from positive sources" lines into a single Algorithmic line |

### Content added (ISSTA-specific)

* **§7 Tool Availability** (mandatory for ISSTA Tool Demos): repository URL, screencast URL placeholder, Zenodo DOI placeholder.
* **Data Availability** subsection inside §7: not strictly required for the Tool Demos track, but recommended by ISSTA's review culture. One paragraph naming each artifact bundled with the public repository and the Zenodo snapshot.

### Unchanged (deliberately)

* Three named contributions (Sniper Strategy, Root API Mode, Trace-as-Oracle) and their headline status.
* §4 interlock sentence ("None of the three could close the oracle and state-fabrication gaps alone; the combination is what does"). The earlier `"incremental"` phrasing has been verified absent from `main_issta.tex` as well.
* §3.(i) black-box framing ("obtained from the SUT operator or aggregated from per-service /v3/api-docs endpoints").
* All concrete numbers: 37 services, 265 operations, 2733 variants, 20 h runtime, 12 h LLM latency.
* Table I structure and the ten fault rows (mechanism column still `	odo{mech}` until the most-current report is on this machine).
* Figures and TikZ sources (`figures/architecture.tex`, `figures/trace_oracle.tex`).
* `refs.bib`: same file, same entries. ACM-Reference-Format accepts the same BibTeX entry types as IEEEtran; the per-entry `note = {TODO-...}` lines for recent DBLP-uncertain entries (RESTGPT, LlamaRestTest, AutoRestTest, DeepREST, TrainTicket secondary) still apply.

### New `	odo{}` markers introduced by the conversion

| Marker | Location | When to resolve |
|---|---|---|
| `	odo{ISBN}` | `cmISBN{...}` in preamble | camera-ready |
| `	odo{DOI}` | `cmDOI{...}` in preamble | camera-ready |
| `	odo{Author Name}` | `uthor{...}` | before submission |
| `	odo{Institution}` / `	odo{City}` / `	odo{Country}` / `	odo{email@domain}` | `ffiliation{}` and `\email{}` | before submission |
| `	odo{SCREENCAST-URL}` | abstract and §7 | before submission (existed pre-conversion; kept) |
| `	odo{ZENODO-DOI}` | §7 Tool Availability | before submission or camera-ready |

### Estimated page count

Without a local TeX compile against the real `acmart.cls`, the estimate is based on the IEEEtran density observed for `main.tex` (~5.0 to 5.2 pages) plus the known density delta:

* acmart sigconf two-column body is roughly 5–10 percent more compact per page than IEEEtran conference (smaller margins, smaller default font for figure captions and listings).
* The compression cuts (End-to-end flow paragraph + Related Work tightening + Figure 2 caption + Algorithm 1 line merge) collectively save ~0.4 page.
* The Tool Availability section adds ~0.3 page (two short paragraphs + section header).

**Net estimate: 4.6 to 4.9 pages including references.** This fits the 4 body + 1 ref budget with margin. If the compile lands long, the next compression in order would be the §4.3 bullet list (merge "span causality" + "error-status propagation" since both express the same idea at different levels of granularity), saving ~0.1 page.

### Rendering issues to inspect manually after first build

1. **Algorithm 1**: IEEEtran rendered `\begin{algorithm}` + `algorithmic` package as an indented numbered block. acmart sigconf may or may not adjust the spacing; if the algorithm shrinks or expands beyond comfort, consider switching to `algorithm2e` (one-line install change in the preamble).
2. **Figure 1** (full-page `figure*`): the architecture diagram was sized for IEEEtran's column width. acmart sigconf columns are slightly narrower; the diagram's right-hand "System Under Test" cluster may push out of the column. If so, tighten `inner sep` and `minimum width` in `figures/architecture.tex` rather than re-laying out by hand.
3. **Listings**: the JSON snippets in §2 and the CLI snippet in §5 use `listings` with `frame=none`. ACM's referee-mode line numbering does not interfere with `listings` numbering, but the two number sequences sit on opposite gutters. If reviewers complain, set `numbers=none` on listings.
4. **Bibliography**: ACM-Reference-Format prefers `doi` fields and is stricter about `@misc` minimum content. After `bibtex` runs, scan the rendered list for warnings; entries without DOIs (RESTGPT, LlamaRestTest, AutoRestTest, DeepREST) will render but may look thin. Adding the URL to the proceedings PDF or arXiv mirror in a `url = {...}` field is the easiest fix.
5. **Title hyphenation**: `Multi-Service` is hyphenated across lines in the IEEEtran title; acmart may break differently or not at all. Re-check the title typesetting in the first PDF.


### Compile fix on first build (acmart 2025)

The first `pdflatex main_issta.tex` exposed three acmart edge cases that the
template silently tolerates in the IEEEtran variant but rejects in acmart:

1. `\Bbbk` already defined.  acmart pulls in `amssymb` itself via `amsmath`,
   so the explicit `\usepackage{amssymb}` in the preamble re-defines `\Bbbk`.
   Fix: the line `\usepackage{amsmath,amssymb,amsfonts}` has been removed.
   `\boldsymbol` and the AMS math symbols stay available via acmart's own
   loads.
2. `\email{...}` parse error.  acmart's `\email`, `\acmISBN`, `\acmDOI`,
   `\author`, `\institution`, `\city`, `\country` macros use low-level
   token expansion (`\xs_arg_ii`, `\xs_call`) that does not tolerate a
   user-defined macro inside the argument.  My `\todo{...}` macro is fine
   in body text but fails in those fields.  Fix: those seven fields now
   contain plain ASCII placeholder strings of the form `TODO-FIELD` or
   `TODO Field`.  Grep for `TODO-` or `^\(author|institution|city|`
   `country\)` to find them before submission.  The body-text `\todo{...}`
   markers (mechanism column of Table I, the SCREENCAST-URL, ZENODO-DOI)
   are unaffected.
3. `\acmPrice` is obsolete.  Removed.

New placeholders introduced by fix #2 (grep for "TODO-" or "TODO "):
* `\acmISBN{TODO-ISBN}`
* `\acmDOI{TODO-DOI}`
* `\author{TODO Author Name}`
* `\institution{TODO Institution}`
* `\city{TODO City}`
* `\country{TODO Country}`
* `\email{todo@example.invalid}` (placeholder address; replace verbatim)


### Section-by-section professional revision (from agent review)

Pass triggered by author's read of the v1 ISSTA draft: it read amateurish
in every section, especially S4.3.  Survey done on 4 to 6 recent ACM /
IEEE tool-demo papers (RESTest ISSTA 2021, AutoRestTest ICSE 2025,
InfraFix ISSTA 2025, ASTRAL ISSTA 2025, Kitten ISSTA 2025, ARAT-RL ASE
2023 for citation-density comparison).  Recurring patterns across the
demos: 5-6 sections (Intro, Tool/Approach with subsections per module,
Evaluation/Usage, Related Work, Conclusion, Tool Availability); no
section titled "Core Innovations"; algorithm pseudocode rare and short
when present; bullet lists used sparingly; "we present" appears at most
once.  See the trip report at the head of this revision for citations.

Edits applied (all in paper/main_issta.tex; do not propagate to
paper/main.tex):

| Section | Before | After | Why |
|---|---|---|---|
| Abstract | "We present MIST, an open-source tool..."  "MIST is built around three named contributions: a Sniper Strategy..." | "MIST turns OpenTelemetry/Jaeger traces and OpenAPI specs into runnable workflow tests."  "Three design choices distinguish it..." | Dropped "we present" and the over-formal "three named contributions" framing.  Demos in the survey state what the tool does, not what its named contributions are. |
| S1 | Contributions presented as a 3-item bullet list with each bullet 1-2 lines | One dense paragraph with the same three sentences | Bullet lists in a 4-page demo eat vertical space.  None of the surveyed demos bullet-list contributions in the Intro. |
| S1 citations | 10-tool parenthetical dump | 6-tool dump (kept EvoMaster, RESTest, RESTler, Morest, DeepREST, AutoRestTest) | An 8-10 citation parenthetical in a single sentence reads stuffed.  Six tools is the density InfraFix uses for its analogous "prior work" line. |
| S2 | "This is exactly the situation MIST is built for." | sentence removed | Marketing flourish; the figure and the body already make the case. |
| S3 intro | "Figure 1 shows the five components. All numbers in this paper come from a MIST run against the bundled TrainTicket deployment described in Section 5." | "Figure 1 shows the five components." | The second sentence was bookkeeping that belongs in the case-study section, not the architecture section. |
| S3.(i) protected framing | (unchanged) | (unchanged) | "obtained from the SUT operator or aggregated from per-service /v3/api-docs endpoints" preserved verbatim per the user's hard constraint. |
| S3.(ii) | "Semantic Dependency Registry with JIT Binding"; mentioned findProducer, ProducerBinding | "Semantic Dependency Registry"; jargon dropped, behaviour described in prose | The dependency-registry component does not need the JIT-Binding suffix in the heading; the body sentence explains the just-in-time behaviour. |
| S3.(iii) | "Sequence Generator (Root API Mode)" header parenthetical | "Sequence Generator" header, Root API Mode introduced one sentence into the body with a forward Section 4.2 reference | The parenthetical pre-empted Section 4.2.  Cross-reference is cleaner. |
| S3.(iv) | "TYPE_MISMATCH, REGEX_MISMATCH, ... EMPTY/NULL, ..." | "TYPE_MISMATCH, REGEX_MISMATCH, ... EMPTY, NULL, ..." | The previous draft collapsed EMPTY and NULL into "EMPTY/NULL", but flow.md and the verification list both name eight categories with EMPTY and NULL as separate ones. |
| S4 section title | "Core Innovations" | "Design" | None of the surveyed demos has a "Core Innovations" section; InfraFix and Kitten use the tool name or "Design" or "Approach" for the same purpose.  The three contributions are still named in the Abstract and in S1; they no longer need a re-introduction in S4. |
| S4 intro paragraph | 3 sentences | 2 sentences (kept the protected interlock sentence verbatim) | The third sentence was an abstract claim that the subsections already make. |
| S4.1 subsection header | "Sniper Strategy" | "Sniper Mutator" | Aligns with the component name in S3.(iv) and with the code (InvalidInputGenerator + MultiServiceTestCaseGenerator round-robin path).  The Abstract and S1 still use "Sniper" as the headline noun. |
| Algorithm 1 | \Procedure...\EndProcedure with 7 numbered lines plus 1 \Statex continuation | 4 numbered lines, no \Procedure wrapper, no return statement | A 10-line algorithm box for a 4-step procedure in a 4-page demo is over-formalised.  4 lines preserves the named environment for the cross-reference but reads as a sketch, which is what the surveyed demos do (when they have an algorithm at all, which most do not). |
| S4.1 closing paragraph | "Compared with conventional black-box fuzzing where multiple parameter mutations interact \cite{...}, the Sniper Strategy trades..." | paragraph removed | Restated the abstraction in a different register.  The point ("causal attributability") is already made in the subsection opener. |
| S4.2 | "The mode is one boolean (...). Disabling it falls back to a multi-step replay where every internal HTTP span also becomes a step. This paper reports only the root-only mode." | "The mode toggles on a single property (mst.generate.only.first.step); this paper reports only the default root-only mode." | Saved two sentences while keeping the configurable / paper-scope statements. |
| S4.3 | 4-bullet itemize listing the four assertion families | One prose paragraph with three named signals plus a closing sentence on registry matching; soft-error-rule-cache pushed to a footnote per the brief | Author's specific complaint.  The bullet list duplicated information already present in S3.(v) and pushed the soft-error-rule-cache sentence into the body when it earned its place only as an aside. |
| S4.3 figure caption | 3 sentences, "source: this repository's Allure attachments" included as parenthetical sub-clause | 2 sentences, same captured-trace claim, repository source still credited inside the parenthetical | Captions in the surveyed demos are 1 to 2 sentences; trimming aligns with style. |
| S5 section title | "Usage Scenarios and Case Study" | "Usage and Case Study" | "Scenarios" is implicit. |
| S5.A title | "Scenario A: targeted regression after a change" | "Targeted regression after a change" | "Scenario A:" prefix is redundant. |
| S5.B title | "Scenario B: exploratory fault detection across the system" | "Exploratory fault detection" | Same.  "Across the system" added no information. |
| S5.B paragraph | 4 sentences plus a separate "All ten injected faults are detected." sentence after the table reference | Merged into one paragraph ending "all ten injected faults caught", and trimmed the LLM-cost prose ("approximately 20 h, of which roughly 12 h is LLM (DeepSeek deepseek-chat) inference latency. The bulk of LLM cost is..." -> "$\sim$12 h is LLM inference (DeepSeek deepseek-chat). Almost all LLM cost is...") | Saves ~1 line and keeps every protected number (2733, 20 h, 12 h, testsperoperation=100, ~2 prompts per API). |
| Table I caption | "TrainTicket 10-fault injected-fault corpus, detection on the most recent end-to-end MIST run." | "Detection on the bundled TrainTicket 10-fault corpus." | Caption did not need to repeat "MIST" or "end-to-end". |
| S5 closing paragraph | "The mechanism column is to be filled per-row from the most-current fault detection report (see ...). Four mechanism kinds are recognised: ..." | "The Mechanism column records which of four signals triggered detection: ..." | One sentence shorter, still names all four mechanisms, still references NOTES_TO_AUTHOR.md Q2. |
| S6 header | "Related Work and Conclusion" | Split into "\section{Related Work}" + "\section{Conclusion}" | All five surveyed demos use separate Related Work + Conclusion; none of them uses a hybrid header. |
| S6 (now Related Work) | 8-tool parenthetical citation dump after EvoMaster/RESTest | Two-sentence flow that names RESTler, Morest, Schemathesis one tool per clause, then bundles the LLM/RL line into a single 5-citation parenthetical | Per-tool naming reads less stuffed; the LLM/RL bundle is genuinely a single line of prior art. |
| S7 (now Conclusion) | "Conclusion." inline label inside the hybrid section, 1 paragraph | Standalone section, 1 paragraph rewritten to drop "\url{...}" repeat (URL is already in Tool Availability) | Conclusion does not need to re-state the repo URL. |
| S8 (now Tool Availability) | "MIST is open-source and available at <URL>..." | "The MIST source code is at <URL> under LGPL-3.0..." | "open-source and available" is filler; the LGPL-3.0 statement is informative and the URL is still the first artifact mentioned. |
| Figure 2 annotation | "Trace-as-Oracle reads:" label inside the TikZ | "Trace-Aware Oracle reads:" inside the TikZ | Matches the component name MIST uses in S3.(v) and the new S4.3 subsection title.  No semantic change. |

Protected items left untouched: the protected interlock sentence in S4
intro ("None of the three could close the oracle and state-fabrication
gaps alone; the combination is what does."); the S3.(i) framing about
the OpenAPI spec source; all concrete numbers (37, 265, 2733, 20 h,
12 h, 10 faults, deepseek-chat); the Table I row structure and ten
\todo{mech} placeholders; the Figure 2 captured-trace claim and trace
ID d4c577d4...acf6; the Tool Availability repository URL, screencast
placeholder, and Zenodo placeholder.  None of the compile fixes are
undone (no \usepackage{amssymb}, no \acmPrice, no \todo{...} inside
\url{}, \email{}, \author{}, \affiliation{}, etc.).

### Estimated page count after this revision

Cuts (net) versus the v1 ISSTA draft, in approximate line-equivalents
at acmart sigconf two-column 10pt:

* S1 bullet list -> dense paragraph: ~0 lines (the dense paragraph is
  roughly the same height as 3 bullets with their indent and spacing)
* S3.(ii) - (v) compression: ~3 lines saved
* S4 intro paragraph trim: ~1 line saved
* Algorithm 1 from 9 lines to 4 lines: ~5 lines saved
* S4.1 closing paragraph removed: ~3 lines saved
* S4.2 last paragraph compression: ~2 lines saved
* S4.3 4-bullet itemize -> 1 paragraph + footnote: ~4 lines saved (the
  footnote takes column-bottom space but the body block shrinks more)
* S5 subsection-title trims and case-study paragraph merge: ~1 line
* S6 hybrid split into S6 + S7: ~0 lines (gains a new section heading,
  loses an inline \textbf{Conclusion.} label and the duplicate URL in
  the Conclusion paragraph)
* Table I caption trim: ~0.5 line

Net cut: ~19 line-equivalents = ~0.3 page.

Pre-revision estimate (NOTES Phase 2): 4.6 to 4.9 pages.
Post-revision estimate: 4.3 to 4.6 pages, comfortably inside the
4 body + 1 ref budget.  If the first compile of the revised version
runs slightly longer (acmart's column rules sometimes float
differently than the v1 estimate assumed), the next available cut is
the S2 listing's "data":null field (move to inline prose).

### Sanity checks after revision

Greps run after the edits:

* `\b(novel|elegant|powerful|comprehensive|extensively)\b` (case-insensitive): 0 matches in body text.
* `incremental`: 0 matches.
* `\acmPrice`: only the explanatory `% \acmPrice is obsolete in modern acmart; removed.` comment from the compile-fix pass; no real `\acmPrice` directive.
* `\usepackage{amssymb`: 0 matches.
* `\url{.*\todo`: 0 matches.
* The protected interlock sentence "None of the three could close the oracle and state-fabrication gaps alone; the combination is what does." is present at line 136.
* Backspace bytes (\x08): 0 matches in `main_issta.tex` and `figures/trace_oracle.tex`.
* All ten Table I `\todo{mech}` placeholders preserved.

### Second compile-fix pass (acmart's \url{} verbatim-mode)

Symptom: at `\maketitle` (l.85) acmart's xstring layer crashed with
`Use of \xs_IfSubStr__ doesn't match its definition`, `Argument of
\xs_execfirst has an extra }`, and `Use of \affiliation doesn't match
its definition`.  The root cause was the abstract's
`Screencast: \url{\todo{SCREENCAST-URL}}` -- `\url{}` parses verbatim-
like and chokes on user macros; the same problem hit two more sites in
the Tool Availability section.

Fix: three URL placeholders rewritten as plain ASCII URLs that the
human can grep for:

* `\url{https://TODO-screencast.example.invalid}` (appears in the
  abstract and in §7 Tool Availability)
* `\url{https://TODO-zenodo-doi.example.invalid}` (in §7 Tool
  Availability)

The Table~I `\todo{mech}` cells, which sit inside ordinary `\textsc{}`-
flanked cells and not inside `\url{}` / `\email{}` / acmart rights
fields, are unaffected and stay as `\todo{...}` markers.

Updated set of placeholder grep targets:

* `TODO-ISBN`, `TODO-DOI` (preamble)
* `TODO Author Name`, `TODO Institution`, `TODO City`, `TODO Country`
* `todo@example.invalid` (email; replace verbatim)
* `TODO-screencast.example.invalid` (two sites)
* `TODO-zenodo-doi.example.invalid`
* `\todo{mech}` (Table I)
