# DECISION OF RECORD — two-tier submission plan (A* shot + CORE-A safety net)

**Date:** 2026-07-19 · USER decision, in two parts:
1. First: **"顶会" = STRICTLY the A* main research track** (benchmark/artifact tracks, journals
   EXCLUDED), accepting ~30% odds.
2. Then RELAXED: **A* is IDEAL, but a CORE **A** venue is an acceptable fallback if the A* shot
   fails** ("如果顶会实在进不去可以考虑 iCORE 的 A，但最好是 A*"). Journals stay excluded.

This relaxation is MATERIAL good news: it converts an all-or-nothing ~30% gamble into a **waterfall**
with a STRONG-accept safety net that is NOT a journal. The go-forward is a two-tier plan.

## The two-tier plan (the timing chains cleanly)
### TIER 1 — the A* shot: **FSE 2027 Research, deadline Fri 2 Oct 2026**
The only OPEN A* main-research slot with a **major-revision cushion** (first decision Jan 22 2027 →
major-rev → final Mar 31 2027). Bet = earn a MAJOR-REVISION (top-decile rigor + honest nulls = the
FSE major-rev profile), the real second chance without a journal wait. ~25-35% honest. If FSE slips
or as a second A* shot: ISSTA 2027 Research (~Q1 2027, highest topical fit) / ASE 2027 (~Mar 2027).

### TIER 2 — the CORE-A safety net (now acceptable): **ESEM 2027 Technical (~late Apr 2027)**
ESEM is the **premier EMPIRICAL SE venue (CORE A)** and, per the venue research, the **single
STRONGEST fit for this exact contribution** — it rewards empirical/measurement rigor over mechanism
novelty, i.e. it directly counters the very critique (Cast-capped novelty) that made this borderline
at A*; friendlier accept (~18-25%). **The timing waterfalls perfectly:** FSE's Jan-22 / Mar-31
outcome lands BEFORE ESEM's ~late-Apr deadline, so a TIER-1 reject redirects straight into TIER 2
with the reviewer feedback folded in. Secondary CORE-A options: ISSRE (reliability — topical
bullseye for data-integrity; reconfirm deadline) / ICSME (maintenance angle).

**Net:** take the A* shot at FSE; if it doesn't land, ESEM (CORE A) is a strong-accept home — no
journal, still a respectable A-tier publication where this work is a GOOD fit, not a coin flip.

## The honest baseline being accepted (no spin)

## The honest baseline being accepted (no spin)
- The contribution is REAL but MODEST: an open controlled benchmark for a scarce, industrially-real
  masked-2xx fault class + a black-box/label-free durable-read-back oracle whose novelty is an
  OPERATING POINT (no instrumentation, single-execution, checks the data not a trace proxy), NOT a
  mechanism (Cast owns the phenomenon).
- E-ANOM LOWERED the ceiling: a control-vs-fault trace differ catches 5/6 instrumented positives via
  the missing-persist-edge, falsifying the broad "trace can't see it" claim. Honest main-research-
  track odds are now **~25-35%**; a single-shot REJECT is the more likely outcome, and a reject
  delays to the next cycle. The user has chosen this path knowing that.

## The ONLY odds-maximizing honest play within the constraint
### Venue + timing
**FSE 2027 Research, deadline Fri 2 Oct 2026** — the only OPEN main-research-track slot with a
**major-revision cushion** (first decision Jan 22 2027 → major-revision → final Mar 31 2027). The
bet is NOT "outright accept" — it is **"earn a MAJOR-REVISION"**: a borderline paper with top-decile
rigor + honest nulls is exactly the profile FSE sends to major-revision rather than reject, which is
the real second chance without journal wait. (Reconfirm the FSE'27 CFP has no earlier cycle before
treating Oct 2 as sole.)

### The reframe that turns E-ANOM from threat into thesis (the "detection-channel landscape")
Do NOT frame as "MIST's oracle beats the others." Frame as a MEASUREMENT of **which observation
channel detects which variety of masked-2xx loss**:
- status/schema/body → detect NONE (2xx, valid, no marker).
- trace-structure / trace-anomaly (E-ANOM) → detect the SKIPPED-CALL variety (missing edge) **only
  when trace-instrumented AND given a paired/learned baseline**; MISS the silent-persist-failure
  variety (call happens, data not durable — no missing edge) and are inapplicable on the 26/33
  uninstrumented cases.
- durable-state READ-BACK (MIST) → detects BOTH varieties, **black-box, no instrumentation, single
  execution**.
Thesis: **no single channel suffices; the durable-state channel is the only black-box one that
covers both varieties** — "check the data, not a proxy." E-ANOM is now a CORE measured result of the
landscape, not a hidden liability. This is the most honest AND highest-odds version.

### The three contributions under the reframe
1. **C2 — the open controlled benchmark** (Defects4J-style artifact contribution; the scarcity null
   0/1514 JUSTIFIES curation; benign-trap-paired; label-provenance taxonomy; 32/33 reproducible).
2. **C1 — the durable-read-back oracle + the channel-landscape evaluation** (novelty on the
   black-box/label-free/directly-checks-durable-state axis; E-ANOM + Schemathesis/EvoMaster as the
   measured competitor channels).
3. **The empirical headline = the human-rater study** (Cast-independent, competitor-independent:
   humans confirm the cases are genuinely adjudicable as loss-vs-benign and the FP-discipline holds).

## What is NOT done and gates the draft (unchanged)
- **Rater study (USER-side)** — the single biggest lever and the empirical headline; the standing
  gate (no draft until ALL experiments + rater done) holds. Launch it.
- **Drafting** — only on the user's explicit GO, under the channel-landscape reframe + the standing
  honesty rails (LOST-only scope, never a pooled recall, disclose induced/TT-weight/E-ANOM).
- E-OBS = NOT executed (reviewers split; close its O6 gap in writing). E-ANOM = done; folds into the
  landscape at drafting time (architecture choice deferred — as the traceanomaly channel vs a
  separate differential-channel analysis).

## The one thing the user must hear repeated
This maximizes the odds of a MODEST paper at a hard bar; it does not manufacture a strong one. The
realistic single-shot outcome is major-revision-or-reject, not accept. The major-revision cushion is
the mechanism that makes the gamble rational; the rater study is what could push it over on revision.
