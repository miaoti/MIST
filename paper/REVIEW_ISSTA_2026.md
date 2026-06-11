# MIST tool-demo — ISSTA 2026 review + change plan

> Consolidated review of `paper/main_issta.tex` for **ISSTA 2026 Posters & Tool
> Demonstrations**. Date: 2026-05-30. Evidence base: a skeptical-PC-reviewer
> agent pass, a venue/bar web-research pass, and direct code/artifact grounding.
> Every quantitative claim below is grounded to a file or artifact.

---

## 0. Bottom line

**Verdict as-is: Weak Reject → flips to comfortable Weak Accept** once the
evaluation section is made honest and the screencast lands. The tool is real,
the trace-shape-oracle niche is defensible ("first open-source REST tester to
use the distributed trace as both a generation input and a first-class
oracle"), and `mist-core` genuinely vendors **no** third-party source (0
`es.us.isa` imports — the RESTest sever is complete, so §3's claim holds).

**The one fatal risk: Table 1.** The headline "MIST 10/10 vs RESTest 3/10 vs
EvoMaster 6/10 at a matched 25 h budget" is **3/4 unbacked**:
- MIST 10/10 **is** backed (`debug/negative_test/runs/run22-fault-detection-10of10.txt`,
  experiment `trainticket_twostage_test_42`) — but that run is **~6.5 h
  wall-clock** (17:34→00:05, 2026-05-27), **not 25 h**, and records **no LLM
  backend** (so "Qwen3-Coder-30B is the configuration the numbers are reported
  on" is unverified).
- RESTest 3/10 and EvoMaster 6/10 have **no config, jar, driver script, or
  result artifact anywhere in the repo** — only `docs/*.png` screenshots and a
  sibling poster `.tex` that re-asserts the same numbers. A single-blind ISSTA
  reviewer greps the artifact; unbacked comparative numbers read as fabricated
  and are fatal in this track.

**Good news from the research:** a full baseline bake-off is **NOT required**
for a tool demo. The dominant accepted pattern (incl. RESTest's *own* ISSTA
2021 demo) is qualitative "the tool runs end-to-end and finds X on ≥1 real
SUT." So the safe, deadline-friendly move is to **cut the head-to-head and
reframe around what we can back.**

---

## 1. Venue facts — ISSTA 2026 Posters & Tool Demonstrations

Source: official track page (`conf.researchr.org/track/issta-2026/splash-issta-2026-posters-and-tool-demonstrations`) + dates page.

| Item | Value |
|---|---|
| Track | SPLASH/ISSTA 2026 Posters and Tool Demonstrations (open) |
| **Submission** | **Fri June 26, 2026 (AoE)** |
| Notification | Fri July 24, 2026 |
| Camera-ready | Fri Aug 7, 2026 |
| Length | **4 pages body + 1 page references** |
| Format | **ACM `acmart` `sigconf`** (`sample-sigconf.tex`), PDF |
| Blinding | **Single-blind** (authors named) |
| **Screencast** | **MANDATORY** — a "Tool Availability" section must contain (1) repo/tool URL, (2) **a YouTube link** demonstrating the tool, (3) an archived Zenodo DOI "if appropriate" |
| Reviewers | ≥3 PC members |
| Review criteria (verbatim) | relevance to ISSTA audience · technical soundness · **originality** · presentation quality · usefulness · **comparison to related work** |

Note: `README_ISSTA.md` is **stale** — it says the ISSTA window closed and
`main.tex` (ICSE 2027) is active. The track is open with a June 26 deadline;
`main_issta.tex` is the correct active file. Fix `README_ISSTA.md`.

### Calibrated evaluation bar (from recent accepted demos)
- RESTest (ISSTA'21 demo): **qualitative**, 2 usage scenarios, **no baseline table**.
- Microusity (ICPC'23 demo): qualitative + an 8-person user study, **no tool comparison**.
- AutoRestTest (ICSE'25 demo): heavier — 4 SUTs, ran 4 baselines, **had** a table.
- EvoMaster tool report (journal'24): feature showcase, **zero results tables**.
- "comparison to related work" is satisfiable by a **capability/positioning
  table**, not necessarily by *running* the baselines.

**Implication:** we do **not** need RESTest/EvoMaster detection-rate runs to
clear the bar. We DO need: working public repo + **YouTube screencast** +
end-to-end "it runs and produces its oracle output on ≥1 real SUT."

---

## 2. Findings (severity / evidence / fix)

### BLOCKING

**B1 — Unbacked baseline columns in Table 1.** No RESTest/EvoMaster artifact
exists in the repo. *Fix:* either run both for real with committed configs +
logs, or **delete the baseline columns** and reframe (see §4 path A). For June
26, delete.

**B2 — "25 h budget" and "Qwen3-Coder-30B … is the configuration the numbers
are reported on" contradict the backing run.** `run22` = ~6.5 h, no backend
recorded; `trainticket-demo.properties` points at DeepSeek, not Qwen. *Fix:*
state the real wall-clock and the real backend; drop "25 h" unless a 25 h log
exists.

### MAJOR

**B3 — Oracle count is wrong and the strongest idea is missing.** Paper says
"three invariant families" and "we deliberately exclude timing"; code has
**six** (`TraceShapeOracle.java:80–100`): 4 learned (SpanTree, StatusPropagation,
**Timing** [default off, `MstConfig.java:412`], ResponseEnvelope) + 2
evaluation-only (**TargetAttribution**, **HiddenDownstreamFailure**). The
architecture figure even says "4 invariant families" while §3/§4.3 say three —
internally inconsistent. *Fix:* state the truth (4 learned, timing
disabled-by-default; the demo enables the 3 label-free families) and **promote
HiddenDownstreamFailure** — the swallowed-5xx-behind-2xx detector — to a
headline. It is label-free, **LLM-free**, proven end-to-end on Bookinfo this
session, and is the clearest thing no status/schema oracle and no body-reading
oracle (LogiAgent) can catch. It is the paper's best, most-defensible hook and
is currently absent.

**B4 — "Matched budget" comparison is unfalsifiable without config disclosure**
(EvoMaster black- vs white-box? same spec aggregation? same fault harness?).
Even if run, a bare ✓/– table invites "apples-to-oranges." *Fix:* same as B1 —
cut, or disclose every knob + commit configs.

**B5 — Reproducibility overstated.** Abstract/§1/§5 say "27 captured traces";
`test-trace/` ships **2 files** (`traces-1772605095842.json` [~154 trace
objects] + `admin_add_route_failed.json`) — "27" matches neither. The 3-command
quick-start depends on (a) a `DEEPSEEK_API_KEY` a reviewer may lack, (b) a
**hardcoded lab SUT `http://129.62.148.112:32677`** a reviewer cannot reach and
that takes ~20 min to rebuild, (c) **Java 11** compile target while many
reviewers run JDK 21+. None disclosed. *Fix:* reconcile the trace count; add an
**offline/replay path** (primed `LLMCallCache` + recorded traces) so the demo
runs with no key and no lab server; state the JDK range.

**B6 — Placeholders still live.** `\todo{SCREENCAST-URL}` (abstract **and** §7),
`\todo{ZENODO-DOI}` (§7). A screencast is mandatory; an abstract ending in a
TODO is an instant "unfinished" signal. *Fix:* record screencast, mint Zenodo
DOI, clear all `\todo`. (The §4 "Core Innovations" → "Design" rename described
in NOTES was **not** actually applied to `main_issta.tex` — apply it or own the
title.)

### MINOR

- **B7 — Number drift:** my recount gives **263 ops / 36 services** vs paper's
  **265 / 37**. Recount precisely and use the real numbers.
- **B8 — §4.2 overclaim:** "Thompson sampling … eliminating the false-positive
  failures" — strong correctness claim, no data. Soften to "reduces."
- **B9 — License provenance:** §7 says LGPL-3.0 "inherited from RESTest," but
  the fork is severed (0 RESTest source). Reframe as MIST's own license choice.
- **B10 — Title/abstract register:** title repeats "trace" twice; abstract
  front-loads "three named contributions" (research-paper register) and the
  contested numbers. Lead with capability; foreground the hidden-failure hook.
- **B11 — Novelty scoping:** tighten "first to use trace shape" to "first
  *open-source REST API test generator* to use the distributed trace as both a
  generation input and a first-class functional oracle (vs grey-box coverage or
  post-hoc RCA)."

---

## 3. Verified assets we already HAVE (use these)

- **TrainTicket 10/10 detection**, backed by `run22` (~6.5 h; restate backend).
- **Hidden-downstream end-to-end on Bookinfo** (this session): a swallowed 5xx
  behind a 2xx, caught by `HiddenDownstreamFailureInvariant`, surfaced in Allure
  (`🕳️` attachment + `mist.anomaly` label). LLM-free. → 2nd SUT + headline demo.
- **Generality across 3 SUTs from their own inputs, no code edits**: TrainTicket
  + Bookinfo + Sock Shop (`evaluation/suts/`). → reusability signal.
- **`mist-core` vendors no third-party source** (0 `es.us.isa`). → kills the
  "it's just RESTest" objection; keep the claim.
- **The motivating-example soft-error trace** `d4c577d4…acf6` (real, HTTP 200,
  `status:0,data:null`).

## 4. Change plan — 怎么改 / 加什么 / 加哪些实验

### The strategic fork — Table 1 / baselines
- **Path A (recommended; demo-appropriate; hits June 26).** Cut the
  RESTest/EvoMaster columns. Replace Table 1 with (i) MIST's honest TrainTicket
  detection from `run22` + (ii) an **"oracle in action" 3-row table**: same
  request under status-class oracle = PASS, ResponseEnvelope = FAIL (soft
  error), HiddenDownstreamFailure = FAIL (swallowed 5xx) — row 3 needs no LLM.
  Add a **qualitative capability comparison** vs EvoMaster/RESTest/LogiAgent/
  MACROHIVE (satisfies "comparison to related work" without running them).
- **Path B (research-grade; higher deadline risk).** Actually run RESTest +
  EvoMaster on TrainTicket, matched budget, **commit configs + raw logs**, cite
  their paths. Only worth it if baseline access exists; better saved for the
  full research paper. Not required for the demo.

### Content to ADD (calibrated to a demo)
1. **Hidden-downstream as a headline** (B3): reframe G1 → G1a (soft error in 2xx
   body, ResponseEnvelope) + **G1b (hidden downstream failure, trace-only,
   LLM-free)**. Add the Bookinfo case to §2/§4.3.
2. **Offline/replay reproducibility path** (B5): primed LLM cache + recorded
   traces; "runs to a fault report in minutes, no key, no lab server." For this
   track that outweighs any baseline table.
3. **Multi-SUT "it runs" line**: 3 SUTs from their own inputs, no code edits.
4. **Config-disclosure box**: tool version, JDK, seed, backend, SUT — one line
   each (reproducible-by-construction, matches the demo bar).
5. **YouTube screencast** (mandatory): one command → tests → Allure → fault
   report, with one soft-error verdict shot and one hidden-downstream catch.

### Things to FIX in place
- Correct the invariant count everywhere (abstract, §3(v), §4.3, Fig 1 caption,
  Fig 2 box) — make them agree and match code (B3).
- Real wall-clock + real backend (B2); recount 263/36/traces (B7); soften §4.2
  (B8); reconcile license provenance (B9); tighten title/abstract/novelty
  (B10/B11); apply the §4 "Design" rename (B6); fix stale `README_ISSTA.md`.

## 5. Pre-submission checklist (venue-specific)
- [ ] 4 pages body + ≤1 page refs, `acmart sigconf`, single-blind (authors visible)
- [ ] "Tool Availability" §: repo URL + **YouTube** link + Zenodo DOI (if appropriate)
- [ ] All `\todo{}` / `TODO-` placeholders cleared
- [ ] Every number in the paper traces to a committed artifact (no "on another machine")
- [ ] If any comparison stays: baseline configs + logs committed and cited
- [ ] Offline/replay path documented and tested on a clean JDK
- [ ] No reviewer-trigger absolutes ("perfect", "eliminates", "solves")

---

## 6. Data-provenance audit (every empirical claim → backing log)

> Integrity bar (author, 2026-05-30): *every number must be real and backed by a committed log/record.*

| Paper claim | Backing artifact | Committed? | Verdict |
|---|---|---|---|
| TrainTicket 10/10, 15,036 test cases, ~6.5h | `debug/negative_test/runs/run22-fault-detection-10of10.txt` (exp. `trainticket_twostage_test_42`) | ✓ (commit 19c0bf3c) | **BACKED** |
| TT backend = DeepSeek `deepseek-chat` | config default; run22 does **not** record a backend | n/a | keep as a general config statement, not a per-run claim |
| TT soft-error → ResponseEnvelope (Fig 2) | captured trace `d4c577d4…acf6` (Allure attachment) | ✓ | BACKED |
| Bookinfo hidden-downstream matrix (Table 1) | `docs/main-contribution/evidence/bookinfo_e2e_pipeline.md` + `bookinfo_e2e_traces/{masked,healthy}*.json` + `run-oracle-e2e.sh` (+ `.runtime/run*.log`) | ✓ evidence committed (rows 2,4 have trace files; rows 1,3 verbatim in the MD) | **BACKED** |
| "runs on 3 SUTs, no code changes" — Sock Shop leg | `evaluation/suts/sockshop/.runtime/` run `sockshop_generalization_1780114334404`: **4 positive tests, 0 injected faults**, fault-detection summary present | ✗ **UNCOMMITTED** (`.runtime` gitignored) + **THIN** (log shows dedup-exhaustion + smart-fetch "no mappings") | **NEEDS** a committed record and/or strengthening |

### Fault-injection reality (the asymmetry)
- **TrainTicket** — 10 injected faults (committed registry) → 10/10. The only SUT with a fault corpus.
- **Bookinfo** — *no* injected fault; a real `ratings` outage. Non-circular (Bookinfo's own graceful degradation, no mutant) — a strength, and the paper says so.
- **Sock Shop** — *no* injected fault; pure generality (4 positive tests). The thinnest leg.
- The rewrite does **not** claim fault injection on Bookinfo/Sock Shop; abstract/§5/limitations distinguish injected faults (TT) vs real outage (Bookinfo) vs runs (Sock Shop).

### Silent acceptance is OUT — do not reintroduce
`docs/main-contribution/evidence/silent_acceptance_demo.md` is **SUPERSEDED**: the `SilentAcceptanceInvariant` was removed (RESTifAI ICSE'26 already does response-level silent-accept; MIST's own LLM soft-error check duplicated it). Headline is **HiddenDownstreamFailure**. The rewrite correctly omits it.

## 7. Applied to `main_issta.tex` (2026-05-30)

Per the author's decisions (cut baselines; promote hidden-downstream + multi-SUT). Verified: braces 336/336, all environments paired, refs resolved, no stale tokens. **Not compiled** (no `acmart`/`pdflatex` here) — author must compile on Overleaf/local.
- **Abstract** rewritten capability-first; hidden-downstream hook; 3 SUTs; **baseline numbers removed**; honest 15,036 / all-10-faults.
- **Intro** three gaps (G1 soft error, **G2 hidden downstream**, G3 state fabrication); Root API Mode now closes G3; multi-SUT line.
- **§2** added the Bookinfo hidden-downstream motivator alongside the TrainTicket soft-error one.
- **§3(v)** invariant description corrected to six (4 learned incl. timing-default-off + 2 evaluation-only).
- **§4** retitled *Design*; **§4.3** invariant list rewritten — **HiddenDownstreamFailure** + TargetAttribution + honest timing framing.
- **§5** honest numbers (15,036 / ~6.5h / default DeepSeek); **Table 1 replaced** by the backed Bookinfo 4-run matrix; TT 10/10 in prose; Generality paragraph.
- **Related Work** novelty scope tightened; **Limitations** = 3 SUTs + honest caveats (outage-driven, external compile harness, isolated invariant).
- **Fig 1** node (v) → "6 invariants incl. hidden-downstream". `README_ISSTA.md` banner corrected to ISSTA-2026-active.

### Still REQUIRES author action before June 26
1. **YouTube screencast** (mandatory) — paste URL in abstract + §7 (`\todo{SCREENCAST-URL}`).
2. **Zenodo DOI** (§7, `\todo{ZENODO-DOI}`).
3. **Compile** on Overleaf/local with `acmart`; confirm ≤4 body + 1 ref pages.
4. **Commit a Sock Shop evidence record** (or soften the 3-SUT claim) — see §6.
5. Re-verify the 3-command quick-start on a clean clone; fill ISBN/DOI at camera-ready.

---

## 8. Multi-SUT experiments added (2026-05-30) — both backed by committed evidence

Author chose to add fault/hidden-downstream on a 2nd SUT. Both run on the live
kind+Istio+Jaeger cluster; verdicts come from MIST's shipped invariant
(`OracleCheck` over `mist.jar`) on traces captured from the live SUTs.

- **Online Boutique — 2nd hidden-downstream (G2), over gRPC.** `adservice` scaled
  to 0 → `frontend` returns HTTP 200 (renders without ads) while the swallowed
  `frontend→adservice` gRPC call is `otel.status_code=ERROR` (gRPC 14 UNAVAILABLE).
  `HiddenDownstreamFailure` **FIRES on 7/7 outage traces, 0 healthy controls**,
  severity WARN (otel-error tier, not HTTP 5xx). The home-page body is a clean 200
  with **no** error breadcrumb → only the trace exposes it (a body-reading oracle
  cannot). Evidence: `docs/main-contribution/evidence/boutique_e2e_pipeline.md` +
  `boutique_e2e_traces/{boutique_adservice_outage,boutique_frontend_healthy}.json`.
- **Sock Shop — 2nd soft-error (G1).** `catalogue-db` outage → `GET /catalogue`
  returns HTTP 200 with `{error,status_code:500}` body; status oracle passes,
  response-envelope/soft-error oracle flags. Evidence:
  `docs/main-contribution/evidence/sockshop_softerror/`. NOTE: Sock Shop **cannot**
  do hidden-downstream — its front-end does not propagate trace context (entry-200
  and downstream-500 land in separate traces); verified empirically.

Net coverage: **hidden-downstream (G2) on 2 SUTs / 2 protocols** (Bookinfo
HTTP-503→ERROR; Online Boutique gRPC→WARN); **soft-error (G1) on 2 SUTs**
(TrainTicket, Sock Shop); fault detection 10/10 on TrainTicket. Paper
abstract/§5/limitations updated. The cluster was restored after each induced
outage; Online Boutique was deployed into namespace `boutique` (sidecar-injected)
and left running for reproduction (teardown: `kubectl delete ns boutique`).

---

## 9. Online Boutique promoted to 4th bundled SUT (2026-05-30)

Per the author's decision (count must be consistent; bundle it). Online Boutique
is now a full SP1 bundle at `evaluation/suts/boutique/` (same shape as
bookinfo/sockshop): `deploy/deploy.sh`, `openapi/boutique-swagger.yaml`,
`real-system-conf.yaml` (1 service `frontend`, 9 ops — its only HTTP surface;
the rest is internal gRPC), `boutique-demo.properties` + `boutique-mst.properties`,
`workload/capture-traces.sh`, `traces/` (healthy seed + adservice-outage evidence),
`README.md` (honest about the HTML-frontend/gRPC shape).

**End-to-end run (SP1 req #7):** `java -jar mist.jar boutique-demo.properties`
generated **22 scenario files / 38 test cases**, executed against the live SUT,
produced 38 Allure results + a fault-detection report, **exit 0** (run
`boutique_hidden_downstream_1780164710192`; report committed at
`docs/main-contribution/evidence/boutique_run_fault-detection-summary.txt`).
0 injected faults (correct — Boutique uses a real outage; the report still
generates via the no-faults path).

Paper updated: abstract/§1/§5/limitations now say **four** SUTs and name Online
Boutique; the abstract count inconsistency (said "three" while naming Boutique)
is fixed. Final coverage: **hidden-downstream (G2) on 2 SUTs/2 protocols**
(Bookinfo HTTP, Online Boutique gRPC), **soft-error (G1) on 2 SUTs** (TrainTicket,
Sock Shop), **fault-injection detection 10/10 on TrainTicket**, and the demo runs
end-to-end on all **four** bundled SUTs.
