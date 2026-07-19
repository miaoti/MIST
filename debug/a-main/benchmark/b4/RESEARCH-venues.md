# Top-venue path research — masked-2xx benchmark + MIST oracle paper

**Compiled:** 2026-07-19. **Author constraints:** top conference (no journal timeline);
empirical/benchmark basis (mechanism novelty capped by prior closed "Cast"); pending human
rater study; borderline-reject at ISSTA main track (~25–38%, scale/novelty); must clearly
out-scope the existing ISSTA 2026 Tool-Demonstrations submission.

All dates AoE (UTC-12). Every date/rate is cited. "PREDICTED" = CFP not yet published;
extrapolated from the prior edition and explicitly flagged as unverified.

---

## 1. Venue landscape — ranked by SOONEST CREDIBLE DECISION

Status key: **OPEN** = deadline still ahead of 2026-07-19 · **CLOSED** = passed · **PREDICTED** = not yet announced.

| # | Venue + track | Next submission | First decision | Status | Revision model | Recent accept. rate | Tier | Benchmark fit |
|---|---|---|---|---|---|---|---|---|
| 1 | **ICSE 2027 — SEIP (industry/practice)** | **Fri 23 Oct 2026** | **11 Dec 2026** | **OPEN** | none | SEIP higher (~25–30%) | A* (industry track) | Medium (industry framing) |
| 2 | **FSE 2027 — Research track** | **Fri 2 Oct 2026** | **22 Jan 2027** (final 31 Mar via major-rev) | **OPEN** | **major revision** | FSE'25 ~22% | A* | **High** |
| 3 | FSE 2027 — Industry track | Sat 23 Jan 2027 | 20 Mar 2027 | OPEN | n/s | industry | A* (industry track) | Medium-High |
| 4 | ISSTA 2027 — Research | ~late Jan–Feb 2027 | ~Apr 2027 | PREDICTED | major revision (expected) | ISSTA'25 19.5% | A* | **Highest (topical)** |
| 5 | ASE 2027 — Research | ~Mar 2027 | ~May 2027 | PREDICTED | n/s | ASE'25 22% | A* | High |
| 6 | ESEM 2027 — Technical | ~late Apr 2027 | ~Jun–Jul 2027 | PREDICTED | n/s | ESEM'25 18% | A (top empirical) | **Highest (empirical)** |
| 7 | ICSME 2027 — Research | ~Mar 2027 | ~May 2027 | PREDICTED | n/s | ~24% | A | High |

**CLOSED for this cycle (decision-relevant — do not target):**
- **ICSE 2027 Research** — abstract 23 Jun / paper **30 Jun 2026 (PASSED)**. Single round + major-revision; next research shot is **ICSE 2028 (~mid-2027, PREDICTED)**. [researchr](https://conf.researchr.org/track/icse-2027/icse-2027-research-track)
- **ASE 2026 Research** — 26 Mar 2026; **Tools & Datasets** 11 May 2026 (both PASSED). [researchr](https://conf.researchr.org/track/ase-2026/ase-2026-research-track)
- **ISSTA 2026 Research** — 29 Jan 2026 (PASSED); conf. 3–9 Oct 2026, Oakland (co-located w/ SPLASH). [researchr](https://conf.researchr.org/home/issta-2026)
- **ESEM 2026** — abstract 22 Apr / paper 29 Apr 2026 (PASSED), Munich.
- **ICSME 2026** — 6 Mar 2026; Industry 15 May 2026 (PASSED), Benevento.
- **MSR 2026** — 23 Oct 2025; Industry 23 Nov 2025 (PASSED), Rio.

Aggregators cross-checked: [se-deadlines.github.io](https://se-deadlines.github.io/) ·
[tum-i4/deadlines conferences.yml](https://raw.githubusercontent.com/tum-i4/deadlines/master/_data/conferences.yml).

**Bottom line:** the only OPEN top-tier slots from today are **FSE 2027 Research (Oct 2)**,
**ICSE 2027 SEIP (Oct 23)**, and **FSE 2027 Industry (Jan 23)**. ISSTA/ASE/ESEM/ICSME 2027
research tracks are not yet announced (predicted Q1–Q2 2027).

---

## 2. Which tracks fit a benchmark+oracle empirical contribution — and do they "count"?

| Track | Counts as top-venue pub? | Bar vs main | Fit for THIS paper |
|---|---|---|---|
| **ISSTA Research** | **Yes, fully (A*)** | = main track (this is where it was borderline) | **Highest topical fit** — test oracles / empirical testing studies / benchmarks are core ISSTA scope. Overcoming the prior reject needs the scaled 33-case corpus + rater study to answer the scale/novelty critique. |
| **FSE Research** | **Yes, fully (A*)** | = main track (high), but **major-revision softens binary reject** | **High.** CFP explicitly welcomes empirical studies, replication, and **"especially encourages" public datasets/tools**; full paper 18pp+4 — unmistakably out-scopes a 4pp tool demo. FSE reviewers weight empirical rigor, not just testing-mechanism novelty. [CFP](https://conf.researchr.org/track/fse-2027/fse-2027-papers) |
| **ICSE SEIP** | Yes — in ICSE proceedings/indexed, but understood as **industry/practice, a notch below** the ICSE research track | Emphasizes industrial relevance/lessons over novelty (could be *easier* on novelty) | **Medium.** SUTs are open-source demo systems (TeaStore, SockShop, OTel-demo, TrainTicket, Boutique), not a real industrial deployment — the "insights from practice" framing is a stretch. **No revision cycle.** Fastest decision. |
| **FSE Industry** | Yes (FSE proceedings), industry-framed, notch below research | Practice relevance over novelty | Medium-High; same industrial-framing caveat as SEIP. |
| **ESEM Technical** | Yes — **the premier EMPIRICAL SE venue** (CORE A, slight prestige discount vs the A* four) | Rewards empirical/measurement rigor over mechanism novelty | **Highest for a pure empirical/benchmark contribution** — directly counters the "novelty" reject rationale; friendlier accept (~18–25%). Later deadline. |
| **ICSME Research** | Yes (CORE A) | maintenance/evolution angle | High — "data-integrity oracle for evolving microservices" fits; secondary. |
| MSR Data & Tool Showcase | Purpose-built for benchmark datasets, **but MSR is CORE A and the showcase is short/demo-like** | lower | **Not recommended** — reads as dataset-demo, too close to the tool-demo you must out-scope. |

---

## 3. Best expected value for THIS paper — ranked top-3

1. **FSE 2027 Research track — deadline Fri 2 Oct 2026.** Best overall EV: A* prestige +
   empirical/benchmark explicitly in scope + **single-round-with-major-revision safety net**
   (journal-like R&R at conference speed) + soonest still-open full-research deadline + ~11-week
   runway to land the rater study. Clearly out-scopes the tool demo.
2. **ISSTA 2027 Research — predicted ~late Jan–Feb 2027.** Highest *topical* fit (test
   oracles/benchmarks are home turf) and A* with major-revision; the natural venue once scaled.
   Risk: re-faces the same community's scale/novelty bar; dates unconfirmed.
3. **ESEM 2027 Technical — predicted ~late Apr 2027** *(fit-first safety venue).* The top
   empirical venue; rewards rigor over novelty, higher accept rate — directly addresses the reject
   rationale. Trade-off: CORE A (not A*) and later. *Alt for pure speed:* **ICSE 2027 SEIP
   (Oct 23 → decision Dec 11 2026)** if an industry-practice framing is viable and a fast binary
   answer beats the revision cushion.

---

## 4. Multi-round / revision models (journal-like benefit, conference speed)

None of the 2027 top venues run a true two-cycle (Cycle-1/Cycle-2 resubmission) model anymore;
all use **single submission + author response + MAJOR REVISION (revise-and-resubmit)**:

- **FSE 2027 Research (OPEN):** submit 2 Oct 2026 → author response 14–18 Dec 2026 → initial
  notif **22 Jan 2027** → major-revision resubmit **5 Mar 2027** → final **31 Mar 2027**.
  [CFP](https://conf.researchr.org/track/fse-2027/fse-2027-papers) — *this is the actionable one.*
- **ICSE 2027 Research (CLOSED):** submit 30 Jun 2026 → response 23–25 Sep → initial notif 20 Oct
  → major-rev resubmit 17 Nov → final 18 Dec 2026. Same model, but the door is shut until ICSE 2028.
  [CFP](https://conf.researchr.org/track/icse-2027/icse-2027-research-track)
- **ISSTA 2026 Research (CLOSED, pattern for 2027):** submit 29 Jan → notif 16 Apr → major-rev
  21 May → final 25 Jun 2026. ISSTA 2027 expected to mirror this ~1 year later. [dates](https://conf.researchr.org/dates/issta-2026)

Takeaway: **FSE 2027 Research is the only OPEN top-tier track offering the journal-style
major-revision cushion** — the single most valuable feature for a borderline scale/novelty paper.

---

## 5. Concrete recommendation

**Submit the full empirical paper to the FSE 2027 Research Track by Friday 2 October 2026 (AoE).**
Register the abstract/paper on [conf.researchr.org/track/fse-2027/fse-2027-papers](https://conf.researchr.org/track/fse-2027/fse-2027-papers).

Reasoning:
- **Soonest still-open full-research decision at an A*/CORE-A* venue** (initial 22 Jan 2027).
- FSE's CFP **explicitly welcomes empirical studies and public benchmarks/datasets**, so the
  33-case corpus + label-free read-back differential oracle lands as a research contribution, not
  a tool demo (18pp+4 research paper vs a 4pp demo — the out-scoping is structural).
- The **major-revision path (→ final 31 Mar 2027)** is the journal-like safety net that de-risks a
  borderline scale/novelty verdict *without* journal timelines — exactly the author's constraint.
- ~11 weeks of runway is enough to finish the pending rater study before the deadline.

**Fallbacks (in order):** (a) **ISSTA 2027 Research** (predicted ~Q1 2027) if the rater study
slips past October — highest topical fit, same major-revision cushion; (b) **ESEM 2027**
(predicted ~Apr 2027) as the empirical-friendly safety venue; (c) **ICSE 2027 SEIP by 23 Oct 2026**
only if a fast binary decision (11 Dec 2026) outweighs fit + revision and an industry-practice
framing is defensible.

---

### Flags / could-not-verify
- **ISSTA 2027, ASE 2027, ESEM 2027, ICSME 2027, ICSE 2028** CFPs are **not yet published**
  (ISSTA 2027 home page 404s as of 2026-07-19). Their rows are PREDICTED from the prior edition —
  confirm on researchr once posted. ISSTA in particular shifted 2026 to October (co-located with
  SPLASH), so its 2027 (Singapore) cadence may move.
- **Acceptance rates** are the most recent completed editions via [openaccept.org](https://openaccept.org/):
  ISSTA'25 [19.5%](https://openaccept.org/c/sw/issta/2025/), FSE'25 ~22%, ICSE'25 ~21.3%,
  ASE'25 22% (245/1136), ESEM'25 18% (28/154). SEIP/industry-track and ICSME rates are approximate.
- FSE 2027 is single-round per the researchr dates page; FSE ran two cycles in some past editions —
  reconfirm no second cycle opens before relying on Oct 2 as the sole deadline.
