# SYNTHESIS — how to actually go to a TOP CONFERENCE (user: must be top-venue, cannot wait for journal)

**Date:** 2026-07-19 · Inputs: `RESEARCH-venues.md` + `RESEARCH-reframing.md` + `RESEARCH-flip.md`
(3 web-research agents) on top of the final3 3-cold verdict (unanimous BORDERLINE-REJECT, ~30% at
ISSTA main track, gap STRUCTURAL: scale + Cast-capped novelty). The three researches CONVERGE.

## The single convergent plan
**Reframe to an INSTRUMENT-first paper (benchmark + label-free read-back oracle + evaluation
protocol), fold the scarcity null in as the CENTRAL JUSTIFICATION, and submit to FSE 2027 Research
by the Oct 2, 2026 deadline — with the human-rater study as the empirical validation.**

### Why this is the answer to "top venue, can't wait for journal"
- **The ONLY open top-tier full-research slot with a journal-like cushion.** All big-four research
  tracks for the current cycle are CLOSED (ICSE'27 Jun 30, ASE'26 Mar, ISSTA'26 Jan). The soonest
  OPEN A* full-research deadline is **FSE 2027 Research, Fri 2 Oct 2026** (first decision Jan 22,
  2027; MAJOR-REVISION path to final Mar 31, 2027). That major-revision model gives the borderline
  verdict a second chance WITHOUT the 12-18mo journal wait — exactly the user's constraint. [venues]
- **FSE Research explicitly welcomes empirical + public benchmarks/datasets** (18pp; structurally
  out-scopes the existing 4pp ISSTA tool-demo) and is the venue most receptive to a rigor-and-
  honest-nulls narrative (flip agent's venue call: FSE over ISSTA, because ISSTA is hardest on
  scale AND MIST is already at its ISSTA tool-demo). [venues + flip]

### Why the reframe converts the two structural killers
- **Cast novelty cap → NON-issue (legitimately).** Lead with the instrument; claim novelty ONLY on
  the axis — **black-box + label-free + OPEN/reproducible** — where Cast (closed, AOP, production-
  traffic, no public corpus/oracle) cannot preempt. Cast becomes MOTIVATION (89 dev-confirmed bugs
  = the class is industrially real), not a competitor. [reframing: precedents Defects4J/NoREC/AGORA]
- **Small-n scale → the central justification, not the weakness.** The killer linkage: the corpus
  is small *because the class is naturally scarce*, and the pre-registered wild-hunt (0/1514)
  PROVES it — so a curated, controlled, benign-trap-paired benchmark is the ONLY viable instrument
  and "just add natural cases" is empirically FORECLOSED. Two liabilities → one argument. [reframing]

### The honest ceiling (do not oversell)
Even executed fully, the top-venue ceiling is ~**45-50%** (flip agent) — the scale character and
conceded novelty cap it; the probability-maximizing home really is the journal the reviewers named.
The user's no-journal constraint, not the work's quality, is what holds it to a coin-flip. FSE's
major-revision path is the single biggest hedge against that.

## The execution package (parallel to the USER-side rater study)
The two weeks-scale team experiments the flip agent judged highest-value (each ACHIEVABLE-IN-WEEKS,
together ~+5-10 honest points — real but not decisive; they harden the instrument story):
- **E-ANOM** — run ONE runnable unsupervised structural trace-anomaly detector offline over the 13
  captured corpus traces → converts the current `traceanomaly = not_evaluable 26/26` (argued
  construction-blindness) into a MEASURED leg-invariant miss. Closes reviewer-2's "no anomaly
  competitor actually executes" gap. NO cluster window needed (offline over committed traces).
- **E-OBS** — run MIST's SHIPPED observe-mode oracle on the flagship positives (extend the E2E
  Allure demo beyond adminroute) → closes "the 9/9 headline is the paired eval-harness, not what
  ships" AND answers the MYC 0-DI-over-5145 reachability question with a bounded, honest datum.

### Writing-time reframes (free, do at draft)
Instrument-first spine; scope header to **LOST-only** (drop "lost/corrupted" overclaim — MIST's
verified semantics are lost-only); NEVER a pooled recall in the abstract (per-cell diagnostic +
Wilson only); a "novelty axis" positioning paragraph; artifact-evaluation packaging (the corpus is
already 32/33 reproducible with a 48-member release staging — strong for an Artifact badge).

## Recommended order of operations
1. **USER decides the reframe + venue now** (instrument-first → FSE 2027 Research, Oct 2). This is
   the one decision that unblocks everything; it is a positioning choice, not new work.
2. **USER launches the rater study** (the Cast-independent empirical validation + the highest single
   lever; ~weeks; also the R2 co-headline once it lands).
3. **Team executes E-ANOM + E-OBS in parallel** (weeks; offline/bounded; I can run both on your go).
4. **Draft under the instrument-first framing** (only after experiments + rater per the standing
   gate), targeting FSE Oct 2 with the major-revision path as the safety net.
5. Reconfirm the FSE 2027 CFP has no earlier/second cycle before treating Oct 2 as the sole date
   (research agent flagged the 2027 CFPs as partly predicted; ISSTA'27 ~Q1 2027 is the backup slot,
   same reframe).

## Backup slots (same reframe, if FSE slips or as a second shot)
ISSTA 2027 Research (~Q1 2027; most topical but hardest on scale) · ESEM 2027 (~Apr 2027; premier
EMPIRICAL venue, CORE A, rewards rigor over novelty = the best-fit conference safety school) ·
ICSE 2027 SEIP (Oct 23, 2026; fastest decision Dec 11 but industry-framed, a notch below research).
