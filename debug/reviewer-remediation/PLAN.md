# Reviewer Remediation Campaign — ISSTA 2026 Tool Demo

Source: 3 independent cold ISSTA tool-demo reviews (2026-06-02), recommendations
2× Weak Accept + 1× Weak Reject (→WA if abstract rescoped). This file is the
detailed, step-by-step plan + evidence log. Each finding: verified disposition,
plan, and (where claimed) a real experiment with its result.

Environment (verified 2026-06-02): host `java` = OpenJDK **21** (full JDK, not a
JRE — supersedes the old `project_mist_e2e_execution_gotchas` JRE note for THIS
host); prebuilt `mist-cli/target/mist.jar` present; `.api_keys` symlink present;
seed-42 blessed `.mist/llm-call-cache.json` present. ⇒ experiments are runnable.

## Disposition legend
✅ real, must fix · ◐ valid but nuanced (reword/scope) · ✗ reviewer misread (no change)

---

## Group A — verify-by-experiment (the no-laziness core)

### A1 (F1) — Boutique "7 of 12" vs evidence "8 of 12"   ✗→◐ (number RIGHT, parenthetical imprecise)
- VERIFIED from `docs/main-contribution/evidence/boutique_e2e_pipeline.md:83-90`:
  8 traces ROUTE through adservice, **7 FIRE**; the 8th adservice-routing trace is
  rooted at `productcatalogservice` (internal entry, correctly not flagged); 4 never
  reach adservice; 0/12 healthy fire. Doc explicitly: "(paper previously quoted '8 of
  12', conflating route-through with fire; corrected to 7)." So paper's **7 is correct**.
- Reviewer R3's "paper contradicts artifact" is a MISREAD (route-through ≠ fire).
- Real defect = §5 parenthetical "(those routed through the failed adservice)" conflates
  the 7 (fired) with routing (8). Also answers R2-Q3 (non-firers are true negatives).
- EXPERIMENT A1: re-run `OracleCheck` on committed `boutique_adservice_outage.json` +
  `boutique_frontend_healthy.json` → independently re-confirm 7 fire / 0 fire and the
  routed-but-not-fired breakdown, then reword §5 precisely.
- STATUS: pending

### A2 (F2) — `trainticket_twostage_test_42` "bundled" dir missing / 3 spellings   ✅
- VERIFIED: `trainticket-demo.properties` has `experiment.name=trainticket_twostage_test`,
  `testclass.name=TrainTicketTwoStageTest`, `test.target.dir=mist-cli/src/test/java`.
  Generated suite = `mist-cli/src/test/java/trainticket_twostage_test/TrainTicketTwoStageTest_<ts>.java`,
  and it is **gitignored generation output** (existing peer dirs like `trainticket_small_exec`
  have 0 tracked files). So "bundled" (=committed) is WRONG; the suite is *regenerated*
  deterministically. Three spellings: paper `trainticket_twostage_test_42`, README L187
  `TrainTicketTwoStageTest_42`, README L208 `trainticket_twostage_test`.
- EXPERIMENT A2: run the seed-42 noexec generation → capture the REAL dir + class name,
  confirm determinism, then (a) reword paper §5 "bundled" → "regenerated deterministically
  (seed 42, Quick Start D)"; (b) unify the 3 spellings to the real dir name; (c) fix the
  README "named after the seed (…_42)" claim if the suffix is actually a timestamp.
- Do NOT commit the multi-GB generated suite (that is exactly the `mvn package` bloat the
  repo gitignores — see `project_mistcli_testcompile_gotcha`).
- STATUS: pending

### A3 (F8) — `ResponseEnvelope` (G1) is anecdotal + LLM, no FP rate   ◐ (strengthen with a number)
- R1+R2: G1/`ResponseEnvelope` evidence = one `Invalid_Station_Name` anecdote + a cached
  transcript; no count, no false-positive rate; depends on an LLM.
- EXPERIMENT A3: run `ResponseEnvelopeLiveCheck` (and/or the oracle's ResponseEnvelope)
  over the TrainTicket soft-error corpus → report how many soft-error bodies it flips and
  any false positives on legitimate `status:0`-style non-error sentinels. Turn the §5
  anecdote into "N of M soft-error bodies flipped, 0 FP on K legitimate sentinels".
- If the experiment is not cleanly runnable offline, fall back to softening "attacks G1"
  → "targets G1 (illustrated on the committed `Invalid_Station_Name` case)".
- STATUS: pending

---

## Group B — consistency / overclaim text fixes

### B1 (F3) — `HiddenDownstreamFailure` status stated 3 ways   ✅
- §2 L166 "two **evaluation-only** structural invariants (TargetAttribution, HiddenDownstreamFailure)"
  vs §3.3 L191 "(label-free, LLM-free, **opt-in**)" vs §5 L208 "**fails the test red**".
- Fix: §2 → "opt-in" (drop "evaluation-only", which contradicts "fails the test red");
  keep §3.3 opt-in; keep §5 fails-red. State once that when enabled, HTTP-5xx⇒ERROR (red),
  otel-only⇒WARN (non-blocking).
- STATUS: pending

### B2 (F5) — abstract "all 10 detected via the fault registry"   ◐
- Technically honest ("via the fault registry") but juxtaposed with scale + oracle pitch ⇒
  reads as oracle detection. Body L206 already caveats. Reword abstract to make it a
  SUT-marker *confirmation* (scalability result), not an oracle detection.
- STATUS: pending

### B3 (F6+F7) — loop-claim scope + confusing live-path sentence   ◐
- F6: both hidden-downstream cases are outage-driven (not input-elicited); the §6
  limitation says it — add a one-clause scope near the §1 contribution so "close the
  generation→oracle loop" isn't heard as input-elicited detection.
- F7: §3.3 L191 "the live path anchors on the test's injected client status, using trace
  topology only for offline replay" made R2 misread it as contradicting G2. VERIFY the
  real mechanism in `HiddenDownstreamFailureInvariant.java` first, then rewrite so it is
  clear the live path STILL inspects downstream spans (the injected client status only
  substitutes for the frequently-absent inbound entry span).
- STATUS: pending

---

## Group C — descope / hygiene

### C1 (F9) — `TargetAttribution` claimed, never evaluated   ◐
- Already "advisory"; ensure no sentence implies a validated attribution result.
- STATUS: pending

### C2 (F10) — G3 / Root API Mode "closes G3" unevaluated   ◐
- Soften "closing G3 without instrumenting the SUT" to a design claim, or note it is a
  design contribution not separately measured.
- STATUS: pending

### C3 (F13) — Fig 1 "4 learned + 2 structural invariants" overstates default-on   ◐
- Caption should mark which invariants run by default (TimingEnvelope off, HiddenDownstream
  opt-in, TargetAttribution advisory).
- STATUS: pending

### C4 (F11) — refs.bib 5 TODO-DBLP notes + uncited `OpenAPISpec`   ◐
- Resolve/verify the 5 `note={TODO-…verify on DBLP}` entries (AutoRestTest, RESTGPT,
  LlamaRestTest, TrainTicket, TraceRCA) as far as possible; remove or cite `OpenAPISpec`.
- STATUS: pending

### C5 (F12) — pom `groupId es.us.isa` vs "no vendored fork"   ◐ (cosmetic)
- Decide: leave (low risk; coordinates) or rename to `io.mist`. Likely leave + note.
- STATUS: pending

---

## Group D — finalize
- D1: full verification (brace balance, no dangling cites, ref resolve), commit in logical
  groups, push to `origin/inject-detection`, update this PLAN with results.
- F4 (screencast `\todo{SCREENCAST-URL}`) — author action, cannot do here.
- STATUS: pending

## Execution order
A1 → A2 → A3 (experiments first, they ground the text) → B1,B2,B3,B5 → C1..C5 → D1.

## RESULTS (2026-06-02) — all done
- **A1/F1** ✅ done. Re-ran OracleCheck on all 4 committed Boutique traces: 7/12, 0/12,
  24/40, 0/30 — every paper number reproduces. R3's "contradicts artifact" = misread
  (route≠fire). Fixed §5 parenthetical only. Evidence: EXPERIMENTS.md.
- **A2/F2** ✅ done by source inspection (definitive, no slow gen needed):
  `MistRunner.java:282` uses `IDGenerator.generateTimeId()` ⇒ class suffix is a
  timestamp, not the seed. Fixed paper §5 ("bundled"→regenerated deterministically,
  dropped wrong `_42`) and README (the "named after the seed" claim).
- **A3/F8** ✅ done + live experiment. DeepSeek calls via ResponseEnvelopeLiveCheck:
  flips the real `status:0` soft error (claim re-confirmed live), passes a clear
  success, but FALSE-POSITIVES on `status:1`+`data:null` — so G1 is the LLM-backed,
  lower-assurance half. Added that honest caveat in §5. Evidence: EXPERIMENTS.md.
- **B1/F3** ✅ §2 "evaluation-only" → "advisory + opt-in" (consistent with §3.3/§5).
- **B2/F5** ✅ abstract "detected via the fault registry" → "matching … via the SUT's
  own fault registry" (marker match, not oracle detection).
- **B3/F6** ✅ §1 HiddenDownstreamFailure gets "(shown on real outages, not yet
  input-elicited)". **F7** ✅ §3.3 live-path sentence rewritten (verified in
  `HiddenDownstreamFailureInvariant.java:70-109`: live path still scans downstream
  spans; injected status only substitutes for the absent inbound entry span).
- **C/F9** ✅ TargetAttribution now "(advisory)" in §2 + §3.3. **F10** ✅ "closing G3"
  → "addressing G3". **F13** ✅ Fig 1 box drops the "4 learned + 2 structural" count
  that implied all-default-on.
- **C/F11** ✅ removed 5 printing `note={TODO-…}` fields; removed 2 uncited entries
  (Jaeger, OpenAPISpec); web-verified + corrected 3 comparator cites (AutoRestTest
  pages/DOI; **RESTGPT title was wrong** + missing author Dhruv Shah + pages/DOI;
  LlamaRestTest → @article + vol/articleno/DOI, removed wrongly-listed Stennett);
  added Zhou2018 benchmark DOI (web-confirmed 10.1109/TSE.2018.2887384). Bib now
  17 cited = 17 entries, 0 dangling, 0 unused, 0 TODO.
- **F12** (pom groupId `es.us.isa`) — DEFERRED (cosmetic). Changing it touches every
  module pom + the published artifact coordinates (build risk), and the paper's
  "no vendored source" claim is about *source*, not Maven coordinates. Recommend a
  separate, build-tested `io.mist` rename if desired, not bundled into this pass.
- **F4** (screencast `\todo{SCREENCAST-URL}`) — author action, still open.

Verification: main/refs.bib/architecture/trace_oracle braces all balanced; cites all
resolve (no dangling, no unused); no rendered em-dash / clause-semicolon introduced.

## ADDITIONS (2026-06-02) — using the freed page to upgrade softened points
User confirmed a full page is free, picked: G1 result + Threats-to-validity + related work.
- **ADD-1 (G1 result)** ✅ §5 now reports the live ResponseEnvelope check (flips the
  status:0 soft error, passes a status:1 success, false-positives on status:1/data:null)
  honestly as a best-effort opt-in LLM judgment. (TT traces store no response bodies, so
  this is a qualitative characterisation from the live A3 runs, not a large-N rate.)
- **ADD-2 (Threats to validity)** ✅ new §6 paragraph: the swallowed-error-vs-graceful-
  degradation policy (Fig 2 IS graceful degradation), the retry-recovered ERROR-span FP
  and the no-span FN, and the ResponseEnvelope LLM error surface — why it ships opt-in at
  two severities. Answers R2's policy critique (W9).
- **ADD-3 (related work)** ✅ added the closest 2025 neighbours: AutoRestTest research
  paper (\cite{Kim2025AutoRestTestSPDG}, ICSE'25, the SPDG/MARL method) and RESTifAI
  (\cite{Kogler2025RESTifAI}, ICSE'26 demo, LLM functional oracles), and sharpened the
  contrast: MIST's HiddenDownstreamFailure needs no model at oracle time and catches a
  clean-body swallowed failure an LLM body/business-logic oracle cannot see. Bib 19=19,
  all verified. (R1's named neighbours addressed; 2411.07098 turned out to be the
  AutoRestTest research paper.)

## ROUND 3 (2026-06-02) — unanimous 3× Weak Accept; fixed A–F
Round-3 cold panel = 3× Weak Accept (up from 2WA+1WR); reviewers confirmed the
round-1/2 overclaims are resolved. New finding: the honesty work OVER-corrected.
- **A** ✅ rebalance over-hedging: §6 Limitations now leads with the capability
  (in-process fires on both SUTs) then "Its scope is bounded"; dropped "best-effort,
  lower-assurance complement" → "opt-in LLM judgment, complementing …"; §1 contribution
  "(shown on real outages, not yet input-elicited)" → "(demonstrated on real outages)".
- **B** ✅ abstract "no SUT or LLM" scoped to "this label-free verdict".
- **C** ✅ §6 "closes the generation→oracle loop" → "couples trace-driven generation
  with a trace-based functional oracle" (removes the loop-closed overclaim for G2).
- **D** ✅ §5 states the 7/12 & 24/40 matrices re-run offline via OracleCheck over the
  committed traces (proven in A1).
- **E** ✅ §5 splits "generated 15,036" (throughput) from "matched 10/10 via the SUT's
  own fault registry" (execution-time marker match).
- **F** ✅ README "8 built-in"→9 (verified: fault-types.default.yaml has 9);
  LogiAgent "discards the cross-service context" → "reads each response body rather
  than the cross-service trace"; §5 OracleCheck listing now uses the real resolvable
  trace path + real rootApiKey; bookinfo-demo.properties TrainTicket banner → Bookinfo.
Verify: main 340/340, refs.bib balanced, 19 cited=19 entries, 0 dangling/unused, no
em-dash/semicolon, no residual "lower-assurance"/"not-yet-input-elicited"/"closes…loop".
Open: F4 screencast (author), F12 groupId (deferred).
