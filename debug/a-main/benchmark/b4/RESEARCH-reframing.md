# RESEARCH — Reframing a borderline benchmark/oracle paper for a top SE venue

**Date:** 2026-07-19
**Question:** How do BORDERLINE empirical/benchmark/measurement SE papers actually get ACCEPTED at ISSTA/FSE/ICSE/ASE, and what winning framing best fits THIS paper (33-case masked-2xx acknowledged-but-lost benchmark + MIST black-box label-free read-back oracle)?
**Method:** WebSearch/WebFetch for real accepted precedents. All papers below are real and cited with venue/year/URL. Where a claim is uncertain it is flagged.

---

## The paper's flagged weaknesses (restated)
- (a) **Small evaluated scale** — 10 evaluable positives across ~3 systems, per-cell N=1–4, Wilson recall floors ~0.44.
- (b) **Mechanism-novelty cap** — a prior CLOSED system "Cast" already found 89 dev-confirmed masked-2xx bugs; "first to detect acknowledged-but-lost writes" is not claimable.
- (c) **Scarcity finding** — a wild hunt found 0 natural instances in 1514 candidates (K=5); blocks the reviewer reflex "just add more natural cases."
- (d) **Pending human-rater study** — prevalence + genuine-vs-benign adjudication; could become the empirical headline (not yet run).

Also load-bearing for honesty: MIST's oracle detects acknowledged-but-**LOST** (absence / baseline-non-movement), **NOT** corrupted-but-present. The header's "lost/corrupted" overclaims MIST's own verified scope — reframings below lead with LOST only.

---

## 1. REAL accepted precedents and their winning contribution framing

| # | Paper (authors) | Venue / Year | One-line winning framing (what made the PC credit it) | URL |
|---|---|---|---|---|
| 1 | **Defects4J: a database of existing faults to enable controlled testing studies** (Just, Jalali, Ernst) | **ISSTA 2014** | Pure *instrument* paper — ships a curated corpus of REAL faults + reproducible harness because "real bugs are too infrequently used; mutants/seeded faults are poor substitutes." **No detection/recall claim at all.** | https://dl.acm.org/doi/10.1145/2610384.2628055 |
| 2 | **Fault Analysis and Debugging of Microservice Systems: Industrial Survey, Benchmark System, and Empirical Study** (Zhou, Peng, Xie et al. — TrainTicket) | **IEEE TSE 2018** (conf. companion: ICSE 2018 poster "Benchmarking Microservice Systems for SE Research") | A **small** replicated fault set (22 faults grounded in an industrial survey) becomes the canonical microservice benchmark; survey supplies prevalence/importance, benchmark supplies controlled reproducibility. Small-n is fine when each case is survey-grounded. | https://dl.acm.org/doi/10.1109/TSE.2018.2887384 |
| 3 | **Detecting Optimization Bugs in Database Engines via Non-Optimizing Reference Engine Construction (NoREC)** (Rigger, Su) | **ESEC/FSE 2020** | The *test oracle* is the contribution — a label-free differential oracle for a class that had no automatable oracle. (Caution: validated by finding **159** bugs — an oracle-capability paper that won on LARGE bug yield, not small-n.) | https://dl.acm.org/doi/10.1145/3368089.3409710 |
| 4 | **AGORA: Automated Generation of Test Oracles for REST APIs** (Alonso, Segura et al.) | **ISSTA 2023** (Distinguished Artifact Award) | "**First** approach for automated test-oracle generation for REST APIs in a **black-box** context." Novelty framed on the *black-box, learned-invariant oracle mechanism*, evaluated on a modest operation set; artifact quality carried it. | https://dl.acm.org/doi/10.1145/3597926.3598114 |
| 5 | **An Empirical Analysis of Flaky Tests** (Luo, Hariri, Eloussi, Marinov) | **ESEC/FSE 2014** | A **manual-labeling measurement study** (201 fix commits, 51 projects) that produced the field's founding *taxonomy* of an under-characterized fault class. The "n" that mattered was labeled incidents, not a tool eval. Became foundational. | https://dl.acm.org/doi/10.1145/2635868.2635920 |
| 6 | **An Analysis of Patch Plausibility and Correctness for Generate-and-Validate Patch Generation Systems** (Qi, Long, Achour, Rinard) | **ISSTA 2015** | A **negative/limits result** ("most generated patches are plausible-but-incorrect") that reframed a whole subfield. Contribution = the rigorous debunking + measurement methodology, not a new tool. | https://dl.acm.org/doi/10.1145/2771783.2771791 |
| 7 | **Is the Cure Worse than the Disease? Overfitting in Automated Program Repair** (Smith, Barr, Le Goues, Brun) | **ESEC/FSE 2015** | Same genre as #6: a negative result (repair overfits the tests used to build+judge it) built on a careful controlled measurement design. Rigor of the study *is* the contribution. | https://people.cs.umass.edu/~brun/pubs/pubs/Smith15fse.pdf |
| 8 | **An Empirical Study on Automatically Detecting AI-Generated Source Code: How Far Are We?** (ICSE 2025) | **ICSE 2025** | The "How Far Are We?" reality-check genre: headline is "existing tools perform poorly / don't generalize." A negative empirical finding accepted at the flagship because the measurement is systematic and decision-relevant. | https://conf.researchr.org/details/icse-2025/icse-2025-research-track/134/ |

**Cross-cutting patterns the PCs rewarded**
- **Instrument > estimate.** Defects4J/TrainTicket/AGORA won because they shipped a *reusable, reproducible artifact* (corpus/oracle/harness). None hinged on a large recall number. Recall floors stop being the headline when the artifact is the contribution.
- **Novelty can be on the *axis*, not the *phenomenon*.** AGORA didn't claim "first to find REST bugs"; it claimed "first *black-box* oracle." NoREC didn't claim a new bug class; it claimed a new *oracle construction*. Mechanism-cap is dodged by moving the novelty claim to the delivery axis (black-box, label-free, open, reproducible).
- **A measurement/taxonomy study is a first-class SE contribution** (Luo). The empirical sample is *labeled incidents*, decoupled from any tool's eval-n.
- **Negative/limits results DO land at ISSTA/FSE/ICSE** — but as rigorous *studies* (#6/#7/#8), and note the SE community still has **no dedicated negative-results conference track** (EMSE special-section foreword, Springer 2017, https://link.springer.com/article/10.1007/s10664-017-9498-0). So scarcity is safest folded INTO a study/benchmark, not shipped as a standalone "we found nothing" paper.

---

## 2. Concrete reframings of THIS paper

### R1 — Benchmark + label-free oracle **evaluation instrument** (Defects4J × AGORA)
- **(i) Headline:** *"A controlled, reproducible benchmark of masked-2xx acknowledged-but-lost writes in microservices, plus MIST — the first black-box, label-free read-back differential oracle for the class — packaged as a community evaluation instrument."*
- **(ii) Primary contribution:** the **open artifact** = 33 validated controlled cases (pos/neg) + the black-box oracle + a reproducible evaluation protocol + comparator harness. The instrument, not a recall number.
- **(iii) Venue/track:** **ISSTA** research track (testing + oracle + artifact culture; AGORA/Defects4J precedent). Target ACM Artifact Evaluation "Available + Reusable" as a co-signal.
- **(iv) Neutralizes Cast + small-n:** Novelty is the *black-box, label-free, OPEN, reproducible* axis — Cast is CLOSED, non-reproducible, community-unusable, so it is cited as **motivation** (89 real bugs = the class matters) not as prior art that preempts. Small-n dissolves because a curated instrument is judged on fidelity/reuse, not sample size (Defects4J reported no recall at all).

### R2 — **Measurement / prevalence study led by the human raters** (Luo × the microservice-issues empirical genre)
- **(i) Headline:** *"How prevalent and how adjudicable are acknowledged-but-lost writes in microservices? A rater-grounded characterization of a silently-lost fault class."*
- **(ii) Primary contribution:** the **empirical characterization** — inter-rater genuine-vs-benign adjudication, a prevalence/scarcity measurement (the 1514-candidate hunt is the *denominator*, a strength), and a taxonomy of how the loss manifests and why it evades acknowledgement. MIST + benchmark are the *measurement apparatus*.
- **(iii) Venue/track:** **FSE** or **ICSE** research track (empirical/measurement studies land well here — Luo, "How Far Are We").
- **(iv) Neutralizes Cast + small-n:** A measurement study has no mechanism-novelty obligation, so Cast cannot preempt it (Cast published no open prevalence methodology or corpus). Small-n moves off the tool eval and onto *labeled adjudications* + the 1514-candidate measurement. **Requires the rater study to be run first** — currently the binding gap.

### R3 — **Capability paper: detecting a silently-lost fault class prior black-box tools miss** (NoREC × AGORA)
- **(i) Headline:** *"MIST detects acknowledged-but-lost writes that existing black-box API/oracle tools cannot, without labels or instrumentation."*
- **(ii) Primary contribution:** the oracle mechanism + matched-recall comparison (E2 cells) showing comparators miss what MIST flags.
- **(iii) Venue/track:** ASE or ISSTA.
- **(iv) Neutralizes Cast:** partially — must concede Cast detected the class and claim only the black-box/label-free axis. **Does NOT neutralize small-n** — this genre historically wins on bug YIELD (NoREC 159, AGORA+ 32); MIST's 10 positives / Wilson ~0.44 is thin for a capability headline. **Weakest fit given current materials.**

### R4 — **Scarcity / negative-results note** (Qi × Smith × "How Far Are We")
- **(i) Headline:** *"Acknowledged-but-lost writes are real but naturally scarce (0/1514) — why the class needs a controlled benchmark, not a mining campaign."*
- **(ii) Primary contribution:** the rigorous scarcity measurement + its methodological consequence.
- **(iii) Venue/track:** hard as a standalone at a top *conference* (no negative-results track). **Best folded into R1/R2 as the justification section**, not shipped alone.
- **(iv) Neutralizes Cast + small-n:** the scarcity result is the single best *defense* of both — see below — but it needs a host paper.

---

## 3. Ranking (accept-likelihood × fit-with-existing-materials × speed)

| Rank | Reframing | Accept-likelihood | Fit w/ existing materials | Speed | Note |
|---|---|---|---|---|---|
| **1** | **R1 benchmark+oracle instrument** | High | **Highest** (corpus validator-green, oracle built+evaluated, comparator cells exist) | **Fastest** (defensible WITHOUT waiting on raters) | The safe, shippable spine |
| 2 | **R2 rater-led measurement** | **Highest ceiling** | High but **gated** — rater study not yet run | Slower (blocked on IRB/raters) | Best headline once rater data lands |
| 3 | R4 scarcity | Med (needs a host) | High | Fast | Fold into R1/R2 |
| 4 | R3 capability | Med-low | Low (small-n is fatal here) | Fast | Avoid as the headline |

**Single strongest = R1 as the spine, with R2 elevated to co-headline once the rater data exists — i.e. an instrument paper whose empirical validation is the rater study.** This is the honest reading of the materials: the benchmark + black-box label-free oracle already exist and neutralize both objections *today*; the rater study (which the author is gating the whole write-up on anyway) then supplies the "these cases are genuine + here is how rare/hard" empirical spine. ISSTA is the best home (oracle + benchmark + artifact culture).

**The killer linkage to make explicit (turns two weaknesses into one argument):** the corpus is small (weakness a) *because* the phenomenon is naturally scarce (weakness c), and we PROVE the scarcity (0/1514). So a curated, controlled benchmark is not a convenience — it is the **only viable instrument** for studying this class, and "just add natural cases" is empirically foreclosed. This converts (a)+(c) from two liabilities into the paper's central justification. No precedent paper had to make this move as cleanly; it is the reframing's strongest single sentence.

**Already supported by existing artifacts (R1 spine):** validated 33-case corpus (pos/neg, validator-green); MIST oracle implemented + evaluated; controlled multi-SUT capture evidence (TeaStore/OTel/SockShop/TrainTicket); comparator/matched-recall (E2) cells; wild-hunt scarcity (0/1514, K=5); neutralized rater sidecars + MANIFEST + sealed corpus + reproduction census + release staging.

**Still needed:** (1) the human-rater study executed (IRB/consent/raters) — unlocks R2 co-headline and the genuine-vs-benign spine; (2) frame recall as *per-cell diagnostic characterization*, not a pooled headline statistic (drop any single recall number from the abstract); (3) an explicit reusability/artifact-evaluation packaging pass (Available+Reusable badge target); (4) a crisp "novelty axis = black-box + label-free + open + reproducible" positioning paragraph that cites Cast as motivation; (5) tighten the scope claim to LOST-only (the "corrupted" wording is an overclaim against MIST's own verified semantics).

---

## 4. Does any reframing legitimately convert the Cast cap from a weakness into a NON-issue?

**Yes — R1 and R2 both do, and legitimately (no sleight of hand):**

- **By not claiming mechanism novelty at all.** R1 leads with an *instrument* (open benchmark + black-box label-free oracle + protocol); R2 leads with a *measurement*. Neither asserts "first to detect acknowledged-but-lost writes," so Cast's 89 bugs cannot preempt the contribution — Cast is on a different axis.
- **Cast's closedness becomes an asset, not a threat.** Cast is a *closed* system with no open corpus, no reproducible harness, no published prevalence methodology, and no black-box/label-free oracle the community can run. R1/R2 contribute exactly what Cast is not: **open, reproducible, black-box, label-free, community-usable.** Cast is therefore cited as *external corroboration that the fault class is real and costly* (89 dev-confirmed bugs) — it strengthens motivation while leaving the contribution axis untouched.
- **R3 does NOT convert it** — it still competes with Cast on "detecting the class" and must concede priority, and it also fails the small-n bar. Avoid leading with R3.

**Bottom line:** lead with the benchmark + label-free oracle as an open evaluation *instrument* (R1), make the scarcity finding the justification for the curated scale, and elevate the human-rater study to the empirical co-headline (R2) once it is run. Cite Cast as motivation, never as the novelty foil. This neutralizes the mechanism cap (weakness b) outright and dissolves the small-n objection (weakness a) by using the scarcity finding (weakness c) as its defense. Target **ISSTA** (research track + artifact evaluation); **FSE/ICSE** are the fallbacks if the rater study becomes strong enough to carry an empirical-study headline (R2).

---

### Honesty notes / uncertainty
- TrainTicket's full paper is **TSE (journal)**; its top-*conference* footprint is the ICSE 2018 poster. Cited as a domain/benchmark precedent, not as proof that this exact package lands in a conference research track.
- NoREC/AGORA/AGORA+ found **many** bugs (159 / 32); they are precedents for *oracle-mechanism-as-contribution*, and a *caution* that the pure capability genre (R3) usually wins on yield — which MIST lacks.
- I did not find a clean top-*conference* precedent for a STANDALONE scarcity/"we found almost nothing" paper; the SE negative-results tradition is concentrated in EMSE/ESEM and journals (confirmed by the EMSE 2017 negative-results special-section foreword). Hence the recommendation to fold scarcity into R1/R2 rather than ship it alone.
