# Reconciliation — 3 cold reviews of the C2+C3 execution plan (@ 980164c)

Reviews: `REVIEW-C2C3-PLAN-A-sufficiency.md` (hostile PC / A-bar), `REVIEW-C2C3-PLAN-B-soundness.md`
(methods/design), `REVIEW-C2C3-PLAN-C-feasibility.md` (engineering). **All three:
ACCEPT-WITH-CHANGES.** No REJECT; no finding kills the direction. The plan is REWRITTEN AS v2 in
place (`c2c3-execution-plan.md`; git history preserves v1) with every disposition folded. Execution
follows v2.

## The user's question — the reviewers' answer (recorded verbatim intent)
**A (charged with answering head-on): "NO — failing the wild-bugs trigger does not mean the
contribution is insufficient for an A-venue"** — the six-round-reviewed path never rested on it; the
contribution IS sufficient for a **credible-to-clear empirical-track A if and only if this plan
executes at its pre-registered quality**. Calibration honesty (folded into v2 §1): "clear" as a prior
is overconfident (RCAEval landed at a WWW COMPANION track; Defects4J/ISSTA'22 support viability) —
"credible-to-clear, decided by execution quality" is the defensible label; what makes the paper MORE
than a dataset paper and must LEAD the framing is **C3-as-first-measurement** (no genuine-vs-benign
split of masked-2xx exists in the literature). Biggest remaining threat = the ~empty wild stratum
(priced in v2 §1/§3 per A-M1). B and C concur by not contesting §1 (B: "none of this threatens the
§1 adjudication, which is faithful"; C challenges only budgets).

## Convergences (independent reviewers, same finding)
1. **A-B1 = B-M4(c):** E2 must carry the **Tracetest downstream-span-PRESENCE arm** — our own
   g3-result §7 names it the trace-class analogue of MIST's read-back; omitting it re-opens the
   crippled-comparator charge with our own artifact as ammunition. → v2 §4 E2 arm 4 + visibility
   classes + authoring-cost reporting.
2. **B-B2 = C-M3:** the trace pipelines E2/M-prevalence presuppose DO NOT EXIST (TT cancel path
   traceless; SS no traceId; TeaStore Kieker-native) → v2 step 2.5 instrumentation wave + measured
   per-SUT trace-coverage table (= the §8.5-2 disclosure) + TraceAnomaly normal-corpus capture + R4
   spike moved to step 1.
3. **A-M4 = B-B3:** the "first open-source" claim needs a pre-freeze sweep + claim-defense table —
   B supplies verified counterexamples (Filibuster corpus SoCC'21; train-ticket-fault-replicate —
   OUR OWN S1 INPUT; Nezha; RCAEval) → v2 §2 claim string + defense table.
4. **A-m1 = C-B2 (+B-m7):** E1's flat grid is part-vacuous (3/6 SUTs thin/no spec; auth glue
   unbudgeted) → v2 two-tier E1 + spec authoring + per-tool auth glue + evaluability smoke gate +
   E×SUT applicability matrix.
5. **A-m5 = B-m6 = C-B1:** budget 2–3× optimistic → v2 §5 rebuilt on C's evidence-based timeline
   (10–13 wk single-box; 8–10 wk with a cloud-burst second node — RECOMMENDED ask, surfaced to the
   user; single-box fallback fully scheduled) + pinned M-yield/M-prevalence budgets + wave-runner
   work item + 26 GB WSL restore + tenancy schedule + TT topology pin.
6. **B-M7 = C-m4 (+A-m4):** rater ask quantified (≈15–45 h/rater ≈ 2–3 paid days; consent +
   compensation + independence mechanics); author-blind fallback carries SCARS (abstract-level
   downgrade + evidence release + author-κ) → v2 §3.1.
7. **A-m3 = B-M2:** statistics overhaul (κ pooled n≥50 + CI + PABAK/AC1; per-SUT CI floor;
   correlated CI units = distinct fault-site; explicit supersession of the ≤5% half-width target
   with the wild-scarcity rationale) → v2 §3.
8. **A-m2 = B-M3 (adjacent):** headline formula "≥80 constructed/benign + wild as-found"; strata
   floors get diversity minima + per-family floors + defined case units (packaged corpus = 1 case);
   §8.5-3 table becomes NORMATIVE for S1 quotas → v2 §2.3.

## Dispositions — A (sufficiency)
| # | Finding | Disposition (v2 location) |
|---|---|---|
| B1 | span-presence E2 arm | FOLDED — §4 E2 arm 4 (convergence 1) |
| M1 | price the 0-wild branch | FOLDED — §1 "clear is two-sided" + §3.3 pre-registered benign-dominance interpretation (E2 frontier promoted to headline in that branch; venue fit stated) |
| M2 | Gate-4 accounting | FOLDED — §4 pre-registered accounting paragraph (which tools count; narrowed claim if the frontier ends at 2) |
| M3 | study-first framing + early harvest | FOLDED — §1 binding writing rule (title/abstract lead with C2+C3; C1 = instrument; cells = boundary demos) + §3.2 upstream filing moved into steps 4–5 |
| M4 | first-claim sweep | FOLDED — §2 claim-defense table + §2.4-1 sweep step (convergence 3) |
| m1–m5 | spec fallback; headline formula; CI sizing; fallback-in-abstract; wave schedule | ALL FOLDED (convergences 4/8/7/6/5) |
| (b) nuance | "EXISTS in constructed form" one notch generous | FOLDED — §1 table row reworded to "waived-for-the-track (constructed-form boundary evidence exists; practical-incidence evidence would discharge it — C3's wild stratum is the instrument)" |

## Dispositions — B (soundness)
| # | Finding | Disposition (v2) |
|---|---|---|
| B1 | wild-mode detector undefined | FOLDED — §3.2 names the wild instruments (trace-shape masking oracle on instrumented SUTs + single-leg read-back-absence as a SEPARATE mode) + pre-S3 FP calibration on S2 + explicit non-inheritance of paired-mode zeros |
| B2 | instrumentation wave | FOLDED — step 2.5 (convergence 2) |
| B3 | first-claim exposure | FOLDED — §2 claim string + defense table (convergence 3) |
| M1 | adjudication volume | FOLDED — §3.2 event→case clustering (endpoint × fault-signature × SUT) + representative + audit sample |
| M2 | statistics | FOLDED — §3 stats block (convergence 7) |
| M3 | strata floors/units | FOLDED — §2.3 (convergence 8) |
| M4 | matched recall + visibility | FOLDED — §4 operational definition + per-case visibility tag + per-class recall reporting + arm 4 |
| M5 | schema +7 fields | FOLDED — §2.2 |
| M6 | license audit | FOLDED — §2.4 license-audit step + per-source disposition; reference-by-digest default |
| M7 | rater quantification + fallback scars | FOLDED — §3.1 (convergence 6) |
| M8 | workload pinning + lower-bound estimand | FOLDED — §3.2 M-prevalence (N pinned = 12 h/SUT OR 500-write-event stopping rule, whichever first; write-fraction reported; two denominators; detector-conditioned LOWER BOUND stated) |
| M9 | §8.5-1 rule text | FOLDED — §3.1 verbatim rule (three-way label; denominator; third-rater; evidence admissibility; κ-iteration bounds + full relabel) |
| m1–m8 | §8.5 audit (-5 added, -4 escape struck); RCAEval described accurately; benchmark-review form + replay script; data management; E1 fairness mechanics; time-boxes; applicability matrix; Holm | ALL FOLDED (§2/§4/§5) |

## Dispositions — C (feasibility)
| # | Finding | Disposition (v2) |
|---|---|---|
| B1 | compute math + wave-runner + cloud ask | FOLDED — §5 rebuilt (C's timeline adopted; budgets pinned; wave-runner = named work item; cloud-burst = RECOMMENDED infrastructure ask surfaced to the user w/ single-box fallback) |
| B2 | E1 two-tier + specs + auth glue | FOLDED — §4 (convergence 4) |
| B3 | per-SUT MIST enablement package | FOLDED — §5 step 2 named work package w/ per-SUT DoD; thin SUTs = oracle/prevalence-only for M-yield |
| M1 | deploy wave realism | FOLDED — §5 (2.5–3.5 wk incl. enablement) |
| M2 | memory envelope + topology pin | FOLDED — §5 runbook block (26 GB restore; tenancy schedule; no builds during deployed graphs; ONE pinned TT topology) |
| M3 | R4 spike timing + fallback pair | FOLDED — spike → step 1; fallback pair = baseline plan, TraceAnomaly stretch (≤2-day spike gate) |
| M4 | F-corpus cost | FOLDED — floor ≥6 (F6/F8/F10/F20+2), target ≥10; off-peak builds |
| M5 | state-reset policy | FOLDED — §5 per-SUT reset method + wipe scripts in step 2 + budget line |
| M6 | M-prevalence workloads | FOLDED — §3.2 per-SUT workload source table + pinned N |
| m1–m4 | LLM pin; RestTestGen substitute; disk pruning; rater-hours in the ask | ALL FOLDED |
| De-scope ladder | — | ADOPTED verbatim as §5.4 (never below the C2 floor; cut E-depth before SUT count) |

## Net
Direction confirmed by all three; the plan v2 is the executable pre-registration. Open decision
surfaced to the user (not blocking step 0–1): the **cloud-burst second node** (~$150–600 spot,
halves elapsed time, removes the WSL-relay SPOF). Execution begins with v2 §5 step 1 (schema/rubric
freeze + §8.5-3 depth surveys + R4 spike + rater outreach + claim sweep — doc/laptop work, no
cluster load).
