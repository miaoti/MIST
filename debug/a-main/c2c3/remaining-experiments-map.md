# Remaining-experiments map (post-E1+R2) — 2026-07-14

Grounding for the next-wave decision (task: experiment-completion track). E1+R2 CLOSED
(commits `7404873`/`d4a6c96`); ALL planned capture waves closed; corpus 26 (11 pos/15 neg),
25 neutralized rater-sidecars rev 3. This maps what is STILL OPEN against the frozen
checklist, with what each item actually requires.

## A. Experiments still open (checklist-anchored)

| item | what it is (verbatim anchor) | requires | state |
|---|---|---|---|
| **Step 4 — M-yield** | MIST tool runs "1 h × 10 seeds spec-rich tier; 1 h × 3 thin; LLM-off disclosed" → event→case clustering (endpoint × fault-signature × SUT), 1 representative + 10% audit sample → the rater M-yield audit set (checklist L251-253) | LIVE SUTs for ~13 h wall-clock of tool runs; the spec-rich tier centers on TT (the 265-op merged spec) → **TT revival**; sockshop/teastore/oteldemo specs now all exist (E1) | NOT run; the M-yield stratum is the standing NAMED HOLD on rating |
| **Owed 2.5/E2 traced MIST discrimination run** | the STANDING CONSTRAINT: MIST's discrimination claim is pre-registered + unmeasured by any real traced MIST run (the TT fabricated-ack exemplar is synthetic on forked source) | **TT revival** (traced deploy; nacos doubleWrite runbook) | OWED — the single biggest headline-claim hole |
| **Step 6 — E2 comparator frontier** | 5 arms: naive span-error · tracetest-error · tracetest-presence · traceanomaly (provisional-until-run) · **contract-invariant** (the non-trace fair-strong arm, r4 spike; blind assertions FROM the OpenAPI) | N-vs-0 comparator cells for traced captures ALREADY MEASURED (traced-capture wave, `cd275c9`); the contract-invariant arm is NOW UNBLOCKED by E1's specs — needs an execution-model spike (Dredd/Pact live vs offline scoring over captured transcripts) then the run; tracetest/traceanomaly arms need live traced SUTs for any fresh cells | PARTIALLY banked; contract-invariant arm = the newly-unblocked piece |
| **Step 7 — E5 ablations** | TT × 5 seeds, 3–4 d | **TT revival** | NOT run |
| **Step 8 — E6 packaging** | standalone benchmark repo, licenses/component map, manifests | fork-publication decision = **OWED TO USER** (E6) | NOT started; user-gated input |
| **kafkaQueueProblems S1 rider** | deferred stochastic S1 (vendor-flag conventions pinned at R1 X4) | OTel tenant up (it is) + kafka care (rdkafka wedge runbook) | DEFERRED — optional corpus add |

## B. Seal-gated (NOT experiments; listed so nothing hides)

IRB determination + rater consent (USER-side) · final calibration draw (R1d disclosed
shortfall stands) · rater-packet worked examples (blocked on calibration draw) ·
**S3-BENIGN-01 re-cut + re-seal** (the surfaced title-leak; gated Step-5) · TT-truncation
per-endpoint rendering (9 cases) · keep-vs-exclude decisions (sockshop trace-only case; TT
admin ack-text confound) · M-yield-audit disjointness re-run at seal.

## C. The structural fact for sequencing

**TT revival is the shared expensive dependency**: the owed 2.5/E2 run, M-yield's spec-rich
tier, and E5 ablations ALL want a live (traced) TT. One revival window serving all three is
the efficient shape ("TT omnibus"); revival cost/risk is why the user deferred it at the
R1d decision point. The only substantial NO-tenant item left is the contract-invariant
arm's spike + (if offline-scorable) its run over the captured corpus + E1 specs.

## D. Paper-draft gate

The user's standing gate: draft AFTER experiments are done. With E1+R2 closed, the open
experiments are exactly A-above. Whether the line is "done enough" to start drafting in
parallel (e.g., sections that don't depend on the TT omnibus: corpus construction, S3
scarcity finding, oracle-eval design, MIST scope/limitations) is the USER's call, not an
execution decision.
