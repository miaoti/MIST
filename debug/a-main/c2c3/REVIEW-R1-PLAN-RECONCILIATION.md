# Wave R1 plan (rev 1) — 3-cold-review reconciliation → rev 2 (EXECUTION GO, phase-gated)

Three INDEPENDENT cold reviewers (deliberately model-diverse per user 2026-07-13) audited
`wave-r1-corpus-completion-plan.md` rev 1 against the ground-truth docs + the 22 case files + the
upstream repos (C ran live GitHub checks). **All three: ACCEPT-WITH-FIXES — unanimous, no REJECT.**
Per the project's established GO-WITH-CHANGES precedent, every disposition below is FOLDED into
**rev 2** (same file), and execution proceeds under C's phase split: **Phase A may start once the
text fixes are folded (done in rev 2); Phase B stays BLOCKED until the B0 gates close** (license/
lineage audit, occupied-site cross-check, clean-room protocol, citation pin — all made explicit
Phase-B0 gates in rev 2).

| reviewer | model | verdict | blocking findings |
|---|---|---|---|
| A (feasibility/quotas) | opus | ACCEPT-WITH-FIXES | 2 (S2 recount; site recount) |
| B (design/evidence) | fable | ACCEPT-WITH-FIXES | 2 (same counting layer; polarity floor unsatisfiable) |
| C (hostile-PC/license) | sonnet | ACCEPT-WITH-FIXES (Phase-B-gated) | 3 (dead fork citation/unaudited lineage; F-candidates on occupied sites; no clean-room mechanism) |

## Convergence table (findings ≥2 reviewers hit) — dispositions all APPLIED in rev 2

| # | finding | reviewers | disposition in rev 2 |
|---|---|---|---|
| X1 | **S2 arithmetic broken**: rev-1's "current 12" mixed 8 stratum-1 clean controls into the trap count; honest S2 traps NOW = **4**; post-R1 trap band = **19–24**; the ≥35 floor is **structurally unreachable on this SUT set** (all-candidates ceiling ≈30); "calibration floor 30 met in-band" is FALSE (rateable band ≈15–20); the "+2 controls-convention" step was unexplained | A-B1 · B-F1/F6 · C-F5 | §1 recounted on the trap-only denominator; label-negatives reported separately, never against the floor; floor-30 shortfall + power consequence + pooled-κ(n≥50)-basis loss OWNED in §1/§7; the +2 line deleted; "dedupe ×2" corrected to 1 |
| X2 | **S1 site count inflated**: current honest = **7** (TeaStore's 3 cases share one entry = 1 site per C-A4); kafka rider + kafka×mesh-sever = same-site mechanisms (+0 sites); **F-corpus candidates never cross-checked against occupied sites — C verified upstream: F10 targets Contacts (occupied ×3), F1 targets Cancel/Inside-Payment (the flagship site)** | A-B2 · C-F2 | §1 recounted: current 7; additions with the occupied-site rule (occupied ⇒ mechanism/floor-6 credit only, NEVER a new site); site-yield projection now **12–19 = ≥20 AT SERIOUS RISK**; the B0 survey (X5) computes the real projection BEFORE Phase B and <20 surfaces the pre-registered stop-and-replan decision |
| X3 | **≥5 re-probe-PRESENT floor unsatisfiable** (supply ≈1–3, structurally OTel-async-only); presence can never be made uninformative (all genuines are ABSENT); the load-bearing floor is wrong | A-M3 · B-F2 | §3 redesigned: three-way shape taxonomy {write-acked-absent / write-acked-eventual-present / no-write-degradation}; floors = **write-absent benigns ≥8 (≥ genuine-in-mix)** + eventual-present ≥2 (w120 + one deliberate bounded-backlog capture); structural decode directions DISCLOSED + the known-label bias audit pre-registered as the decoder detector (B's worked arithmetic: absent-benigns 8 ⇒ P(genuine\|absent)=0.50) |
| X4 | **Stochastic kafka case under-specified on all consumer surfaces**: schema enums admit no rate; single-trial replay scores spurious FN; rubric's async-no-bound tie-break makes it (and ALL OTel async positives) **calibration-genuine-INELIGIBLE**; provenance fields wrong (by-docs); control-ordering unpinned | A-M5 · B-F3 · C-F9 | §5-R1 rewritten: enum verdicts stay; rate+CI in `fault.config`/notes; per-lost-trial scoring + "replay = N trials, reproduce = ≥1 lost" pinned; sidecar = 1 representative lost trial + N-trial record as raw evidence; **calibration-genuine-INELIGIBLE stamp applied to every OTel async positive**; `injection_method: vendor_flag` + `ground_truth.source: vendor`; control-leg-FIRST; `mist_readback_oracle` stays `not_applicable` in R1; wedge-restart budget priced |
| X5 | **F-corpus not front-loaded + license/lineage holes**: masked-2xx eligibility unverified before a 4–7-day build loop; **the cited fork repo `miaoti/train-ticket-injection` 404s (C, live)**; the actual lineage (`AsifShaafi/train-ticket-injection`, `codewisdom` images) is UNAUDITED; "replicate-by-description" has **no clean-room mechanism** (upstream repo bundles the fault diffs one click from the prose); the obligated survey-paper citation is never named anywhere | A-M4 · C-F1/F3/F4 | **NEW Phase B0 (hard gate, blocks Phase B)**: (1) description-only eligibility survey — transcribe F1–F22 prose to a fresh spec file, masked-2xx call + occupied-site cross-check per fault, ≥6 eligible on unoccupied services else surface replan BEFORE builds; (2) license/lineage closure — GitHub license-API check on AsifShaafi repo, codewisdom namespace resolution, dead-citation correction plan for the whole TT stratum, fork-publication decision FLAGGED TO USER; (3) survey-paper citation resolved + pinned in `c2-license-audit.md`; (4) **two-actor clean-room**: implementation runs in an ISOLATED subagent whose only input is the spec file and which is instructed never to fetch the upstream repo; per-fault input artifact recorded |
| X6 | **Correlated cart-refutation risk with no combined contingency** (3 SUTs already show cart stores honest-loud; R1 bets on 2 more cart-adjacent probes) | A-M6 · C-F10 | §6 combined-contingency: if BOTH refute, the site projection is re-run immediately + named backfills (F-corpus target extension beyond 10 on unoccupied services; TeaStore internal-CRUD tier explicitly EXCLUDED from the discriminating floor — never a backfill) |

## Single-reviewer findings — all APPLIED

- **B-F4 (MAJOR): cadence pinned for only 3/6 SUTs; 3 legacy traps non-conformant (dedupe = 1 observation, no re-probe).** → §3: dated cadence pin for SS/Bookinfo/Boutique (sync default 10s cap / 0.5s poll / 300s re-probe) BEFORE Phase A; legacy re-captures (tt-dedupe, tt-noop-modify, bookinfo-ratings) scheduled inside their tenants' windows under T2; per-SUT pre-batch render gate (one bundle through `b4_harness.render`, 0 BANNED_STRINGS).
- **C-F7 (MAJOR): no teardown-verification between same-tenant captures.** → §4: after EVERY mesh/flag capture — assert zero fault-related VS/EnvoyFilter/env objects + a clean healthy-probe round before the next capture (the wave-3a executed bar).
- **C-F8 (MAJOR): packaged FP corpora violate the S2 by-docs invariant + are pre-pin.** → §1/§6: authored with a dated freeze §6 exemption (by-construction benign ground truth, pre-pin disclosure, S3-precedent form) and EXCLUDED from the C3 rateable supply (also B-F5).
- **C-F6 (MAJOR): S2 shortfall needs an earned exhaustion bar.** → §6: shortfall may be declared only after every §1-named candidate is captured-or-refuted-with-datum, the flagd-13 sweep is complete, and the Bookinfo path list is exhausted.
- **A-M7/A-M8 (MINOR)**: F-corpus per-iteration realism noted (stop rule retained); OTel mechanism-floor thinning noted in §1.

## Verified-sound (kept as-is)
Riders' designs (kafka N≥20+control+CI+poisoning-window; TeaStore observe-recall = the exact S3-F2b
remedy); license BASIS for replicate-by-description (upstream license=null); refutation-branch
discipline; destructive-op ordering; tenancy phases; stop rules; the "kafka×mesh-sever = same site"
honesty; packaged-corpora ≤2-unit no-padding framing.

## Net effect on targets (the honest rev-2 numbers)
- S1 distinct sites: 7 → **12–19 projected** (≥20 at risk; B0 computes the real number; <20 ⇒
  pre-registered stop-and-replan surface, per freeze §5).
- S2 traps: 4 → **19–24 projected** (≥35 unreachable — disclosed-shortfall branch with the earned
  exhaustion bar; rateable calibration benigns ≈15–20 < floor 30 → shortfall + power consequence
  disclosed now, before capture).
- The wave REMAINS the right next move: every downstream consumer (C3 mix, C2 floors, M-yield audit)
  needs exactly these captures; the recount changes the claims, not the work.
