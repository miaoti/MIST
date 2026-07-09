# REVIEW-RATER-2 (adversarial) — hostile A-venue PC cold read of the C3 rater protocol

**Reviewer stance:** hostile methods-savvy PC member; cold read, no shared context with prior reviews.
**Artifacts read:** `debug/a-main/c2c3/c3-rater-materials.md` (the package), `debug/a-main/c2c3/c3-case-corpus-plan.md`
rev 2, `debug/a-main/c2c3-execution-plan.md` §1/§3, `debug/a-main/c2c3/step2-execution-checklist.md`
(§1.9.6 / §1.95 / step-5 gate), `debug/a-main/c2c3/c2-freeze.md` §3–§4 (cross-checked because the
materials claim verbatim identity to it).

**VERDICT (one line): REJECT as written — the paper's "MIST-blind raters" sentence is currently an
unverifiable assertion that the materials themselves leak against, and at realistic S3 agreement the
headline precision number is authored by a non-blind in-group adjudicator; every fix is cheap and
protocol-level, but it must land BEFORE the first rater is contacted, not in a rebuttal.**

---

## Attack 1 — "In-group students are not blind" (STATUS: OPEN — the central hole)

The paper will say raters were MIST-blind. Here is the cross-examination:

**1a. Blindness is asserted, never instrumented.** `c3-rater-materials.md:175` promises the MIST-blind
screen "is screened before assignment and recorded" — recorded HOW? No screening instrument exists
(no verbatim questions, no retention/release plan), the §2 consent (`c3-rater-materials.md:78-83`)
attests to non-discussion and pay-neutrality but contains NO "I have not seen/heard of the tool,
its hypothesis, or this project" attestation, and the §9 eligibility screen
(`c3-rater-materials.md:201-207`) gates skill only — it is explicitly not a blindness screen.

**1b. There is NO debrief manipulation check.** Grep of the entire `debug/a-main` tree: zero hits for
"debrief" or "manipulation check" in the c2c3 protocol. The one instrument that could EVIDENCE
blindness post-hoc — a funneled exit question at close ("what do you think this study was about?
do you believe a software tool selected these cases? can you name it?") — is absent. Without it,
the rebuttal to "same lab, same advisor, flagship tool — how is this blind?" is "trust us."
A methods reviewer will not.

**1c. The rater-facing materials leak the design.** Two self-inflicted wounds:
- `c3-rater-materials.md:45-46`: the recruitment brief tells raters the set is "(S3 wild +
  calibration + M-yield audit, interleaved)" — stratum NAMES, in rater-facing text, directly
  contradicting §0's own invariant that stratum sizes are "stated to the rater only as part of the
  total" (`c3-rater-materials.md:26-27`) and that a rater cannot tell "which detector (if any)
  surfaced" a case (`:26`). "Wild" + "yield" + "calibration" tells a rater: some cases have known
  labels, some are machine-surfaced measurements.
- `c3-rater-materials.md:71-73`: "What you will NOT be shown … any automated tool's opinion on these
  cases … any of our own hypotheses about which cases are defects" — this PRESUPPOSES tool opinions
  and author hypotheses exist for these cases, i.e., it tells the rater the cases were
  machine-flagged, the exact fact §0 (`:23-24`) says must stay hidden.
An SE grad student in this lab, handed a task whose "genuine" label is defined verbatim as the
lab flagship's detection predicate (acked-2xx + durable effect missing,
`c3-rater-materials.md:56-58`), plus those two leaks, guesses the study's purpose in one step.

**1d. The adjudicator is not blind, and the adjudicator decides the result.** §5 sends EVERY
disagreement — i.e., exactly the ambiguous cases that determine wild precision — to the third
adjudicator (`c3-rater-materials.md:138-139`). §7 staffs that role as "most senior + most
arm's-length available (**ideally** not the students' direct advisor)"
(`c3-rater-materials.md:177-178`). In an in-group staffing the senior member is MIST-aware by
construction; "ideally" is aspiration, not protocol; and NOTHING requires the adjudicator to be
MIST-blind — the blindness invariant (§0) and the paper's disclosure line ("internal but MIST-blind
raters", `c2c3-execution-plan.md:147-149`) are written about RATERS only. The tie-breaker on every
contested case knows which answer helps the paper.

**1e. The step-5 entry gate audits the corpus, never the raters.** The 8-check gate
(`step2-execution-checklist.md:152-157`) verifies every byte shown to raters (tell-audit, sealed
manifest, hashes, disjointness) — and contains no check that screening records are on file, no
debrief plan, no attestations. The gate that admits raters inspects everything except the raters.

**Demanded fix (see §5):** a written screening instrument + signed pre-study attestation, a §10
funneled debrief manipulation check with a pre-registered failure rule, a blindness-evidence check
added to the step-5 gate, the two §1 leaks deleted, and the adjudicator either made MIST-blind or
demoted to a secondary analysis via a conservative tie-break (1d's fix).

---

## Attack 2 — Demand characteristics & incentive (STATUS: PARTIALLY PRE-REBUTTED; two open holes)

**Pre-rebutted (credit where due):** pay is hourly and label-independent, stated twice in
rater-facing text ("regardless of the labels you produce; there is no 'right answer'",
`c3-rater-materials.md:68-69`, `:81-82`); the brief states no expected base rate; genuine/benign
calls require a doc/spec/source citation (`:125`), which raises the cost of a casual "genuine";
the in-group relationship bias is disclosed as a threat with the channel-comparison table kept on
record (`:169-184`). This is more counter-measure than most labeling studies ship.

**OPEN 2a — the calibration mix TEACHES an inflated base rate, and blindness amplifies it.** The
calibration set is ~30 cases "balanced" from S1 positives + S2 benign
(`c3-case-corpus-plan.md:15,27,53`; ratio never pre-registered in the materials, §6
`c3-rater-materials.md:149-152`). Interleaved indistinguishably (the §0 invariant), a ~15/15 mix
plants a ≥25% genuine floor in the rater's experienced base rate — while the plan's own
pre-registered prior for S3 is benign-dominance (`c2c3-execution-plan.md:29-34`). Raters cannot
discount calibration cases they cannot identify, so ambiguous wild cases get pulled toward
"genuine" by representativeness — the direction that INFLATES the C3 precision and M-yield numbers.
The blindness fix and the demand-characteristic thus fight each other, and the protocol never
notices. Fix: pre-register the calibration mix (benign-skewed, e.g. ≥2:1, matching the S2:S1
corpus reality) and disclose the ratio at debrief.

**OPEN 2b — 30 known labels exist and are used for NOTHING but κ.** §6 computes only inter-rater κ
on the calibration round (`c3-rater-materials.md:152-156`); two raters sharing the same helpful
bias (both over-call genuine for the lab) pass a κ gate perfectly. The ballot even collects
`time_minutes` "for the compensation + calibration audit" (`:130`) — an audit specified nowhere.
The cheap, decisive counter-measure is missing: per-rater confusion matrices against the KNOWN
calibration labels, a pre-registered directional-bias statistic (false-genuine rate on known-benign
cases), and a pre-registered sensitivity analysis propagating that rate into the S3 precision CI.

**Framing nit (feeds 1c):** `:45-46` and `:71-73` are the leading-language findings; the rest of §1
is acceptably neutral. Also: the brief caps at "up to 60 cases" (`:45`) while the components are
~30 calibration (`:150,206`) + S3 min(all,40) + M-yield audit (`c3-case-corpus-plan.md:15-17`)
≈ up to ~75, and plan §3.1 still says "calibration (~20)" (`c2c3-execution-plan.md:138`) — three
documents, three arithmetic states; the pay/consent estimate rides on this. Reconcile before
contact. (The 中文版 fork `c3-rater-materials-中文版.md` must receive every fix too — a translated
fork of a "frozen" document is its own drift vector.)

---

## Attack 3 — The "underspecified" escape hatch (STATUS: PARTIALLY PRE-REBUTTED; audit + bound missing)

**Pre-rebutted:** the fraction is reported; precision is reported both including and excluding
underspecified (`c2c3-execution-plan.md:131-133`; freeze §4 `c2-freeze.md:203-206` defines both
formulas), so the hatch's quantitative impact is bounded and visible; the ballot demands "state
what's missing" (`c3-rater-materials.md:125`); disagreements about underspecified-ness are
adjudicated like any other (`:95-96`).

**OPEN 3a — AGREED underspecified is invisible.** The adjudicator sees disagreements only
(`:138-139`). Two tired raters who each independently dump a hard case into "underspecified" agree
— the case silently exits the primary denominator with no second look, no audit sample, ever. And
the incentive points that way: pay is fixed for ESTIMATED hours (`:68`), so finishing fast raises
the effective rate, and "underspecified + one line about what's missing" is the fastest exit from a
45-minute case. `time_minutes` is collected but no fast-underspecified audit is pre-registered.

**OPEN 3b — no pre-registered expectation or tripwire.** The S3-scarcity branch is priced
(`c2c3-execution-plan.md:180-183`); an underspecified-dominance branch is NOT — yet it is the
LIKELY branch, because the SUTs are demo systems with thin docs AND because the protocol itself
manufactures underspecified calls: the R6 blindness fix strips the paired clean-run column from all
rater-facing cases (`c3-rater-materials.md:29-34`), removing evidence the frozen rubric explicitly
made admissible ("the observed durable state **(including the paired clean-run state)**",
`c2-freeze.md:173-174` — a clause the freeze added precisely because forbidding it "forbade the
very datum every case is built around", `:176-178`). Cases decidable WITH the twin become
underspecified WITHOUT it. If 40-50% of S3 lands underspecified, the primary denominator halves,
the ±15-21% CI (`c2c3-execution-plan.md:154-156`) becomes ±25-30%, and the "precision" headline
covers only the well-documented slice — undisclosed. Fix: pre-register (i) an adjudicator audit of
ALL agreed-underspecified cases (at ≤60 cases this is hours, not days), (ii) a bound (e.g.
underspecified > 30% of S3 ⇒ the fraction itself is promoted to a headline finding and qualifies
the precision sentence in the abstract), (iii) the R5/R6 contradiction resolved honestly — see
Attack 6.

---

## Attack 4 — Power/κ realism (STATUS: OPEN — the design predicts its own gate failure)

**The arithmetic the protocol never runs.** S3-only κ is the pre-registered HEADLINE reliability
number (`c3-rater-materials.md:141-146`; `c2-freeze.md:182`). Take the plan's own benign-dominance
prior (`c2c3-execution-plan.md:29-34`): at ~85/10/5 prevalence across {benign, genuine,
underspecified}, chance agreement p_e ≈ 0.85²+0.10²+0.05² ≈ 0.735, so even two COMPETENT raters
with observed agreement 0.85 land at κ ≈ (0.85−0.735)/0.265 ≈ **0.43**. κ ≥ 0.6 on S3 is not
"realistically achievable" — under the study's own pre-registered expected world, κ ≈ 0.4-0.5 is
the CENTRAL outcome, on n ≤ 40 (possibly < 10, the pre-registered scarce branch) with a CI wide
enough to be compatible with anything.

**The gate guards the wrong door.** The κ<0.6 iteration gate applies to the CALIBRATION round
(`c3-rater-materials.md:153-156`) — a deliberately balanced mix where p_e ≈ 0.5 and κ behaves. So
the protocol's only reliability GATE passes on the easy set, and the number that becomes the
headline (S3-only κ) has NO pre-registered branch for landing at 0.45: no claim demotion, no
decision rule. The protocol even acknowledges the calibration-inflation direction (`:143-144`) —
honest — but prices no consequence for the S3 number.

**Is PABAK/AC1 a p-hack ladder?** Half-rebutted: reporting κ + raw agreement + PABAK/AC1 jointly is
pre-registered in advance (`:144-145`), not chosen post-hoc, and the prevalence pathology of κ is
real textbook statistics — naming AC1 now is the honest move. BUT no decision rule binds the CLAIM
to a coefficient and threshold. As written, the paper can pass the calibration gate, report "S3
κ=0.45 (moderate)", and lean rhetorically on AC1=0.82 — which IS the ladder, and a methods PC will
read it as one. What the paper looks like at κ=0.45 TODAY: "blind human adjudication (κ=0.45)"
translates to "the two students disagreed on a third to half of the hard cases, and the senior lab
member broke every tie" — which is Attack 1d compounded: low κ maximizes the non-blind
adjudicator's authorship of the result.

**Two staffing fictions.** (i) "fresh raters if available" after rubric iteration
(`c3-rater-materials.md:154-155`) — the eligible in-group pool (MIST-blind SE grads in a MIST lab)
is near-empty by construction, so relabeling is by the SAME raters under a rubric tuned on their
own disagreements: teach-to-the-test κ inflation. (ii) Exactly 2 raters, no reserve — one dropout
or one failed debrief check (once added) collapses the study to the §8 fallback register.

**Fix:** pre-register the reliability decision rule NOW (S3-only κ ≥ 0.6 ⇒ full register; 0.4-0.6 ⇒
demoted register: all ballots released, conservative tie-break primary [Attack 5], adjudicated
secondary, AC1 reported but never substituting; < 0.4 ⇒ no reliability claim, fallback framing) and
staff 2+1 raters (reserve doubles as the fresh relabeler).

---

## Attack 6 (bonus, found in cross-check) — the "frozen VERBATIM-identical" rubric claim is false

`c3-rater-materials.md:87` claims §3 is "frozen — VERBATIM-identical to c2-freeze.md §3"; the
freeze claims the same ("identical copy in `c3-rater-materials.md` §3 — R5", `c2-freeze.md:152`;
"verbatim in both files", `:172`). They differ at a load-bearing clause: the freeze's admissible
observation includes "(including the paired clean-run state)" (`c2-freeze.md:173-174`); the rater
copy silently drops it (`c3-rater-materials.md:106-107`) because §0/R6 strips the clean-run column.
So (a) an integrity claim in a frozen document is false as stated — reviewers who catch one silent
edit stop trusting every "frozen" label in the artifact; and (b) it is substantive: the calibration
cases' "known labels" were established under the richer evidence regime (twin admissible), and
raters are κ-gated against those labels while judging under the poorer regime — an evidence-regime
mismatch that inflates apparent rater error and the underspecified fraction (Attack 3b). Fix: amend
the freeze via its own §6 disclosed-amendment row ("rater-view admissibility = frozen rubric MINUS
paired-clean-run, per R6") and change both "verbatim" claims to "identical modulo the disclosed R6
strip". Never let a hostile reviewer find this before you disclose it.

---

## §5 The single strongest reject — and the pre-rebuttal to write NOW

**The reject a methods-savvy PC writes:** *"The C3 precision claim rests on 'MIST-blind' raters, but
blindness is asserted, not evidenced: raters are students from the tool's own lab, screened by an
unspecified procedure, with no attestation and no debrief manipulation check; the recruitment brief
itself names the study's strata (S3 wild / calibration / M-yield) and presupposes the existence of
tool opinions on the cases; the interleaved calibration mix teaches a genuine base rate far above
the authors' own pre-registered benign-dominance prior; and every disagreement — which, at the
κ ≈ 0.4-0.5 their own prevalence prior predicts for the 3-way S3 label, is a third to half of the
measurement cases — is resolved by a senior in-group adjudicator who is not required to be blind at
all. The headline precision number is therefore, in the expected case, substantially authored by a
non-blind member of the tool's lab, and no post-hoc analysis can recover blindness that was never
measured. Reject; re-run with a sound protocol."*

Every clause of that paragraph is currently true. None of it survives the following pre-rebuttal
package, all of which is protocol-level, cheap, and must be folded into the materials BEFORE any
rater is contacted (the step-1.9.6 review window is exactly for this):

1. **§10 DEBRIEF (new section):** funneled manipulation check at study close, before raters may
   discuss — Q1 "what do you think this study was about?" → Q2 "do you believe a software tool
   produced or selected these cases?" → Q3 "can you name the tool or project?"; plus a close-out
   attestation ("I did not discuss cases/labels"). Pre-registered failure rule: a rater who names
   the tool/hypothesis ⇒ disclosed blindness failure + sensitivity analysis excluding that rater's
   ballots; both fail ⇒ §8 fallback register. Transcripts retained + released (anonymized) with the
   artifact.
2. **Screening made evidential:** written instrument with verbatim items, outcomes recorded and
   released; a signed pre-study MIST-unawareness attestation appended to the §2 consent. Add
   "screening + debrief records on file" as a 9th check in the step-5 corpus-assembly gate
   (`step2-execution-checklist.md:152-157`).
3. **Adjudicator fix (pick one, pre-register):** (a) a MIST-blind adjudicator (external senior
   collaborator), or (b) **conservative tie-break primary** — any rater disagreement involving
   "genuine" resolves to NOT-genuine for the headline precision; the adjudicated resolution is
   reported as a secondary (upper-bound) figure. Option (b) makes disagreement COST precision,
   deleting the inflation vector and converting the non-blind adjudicator from author-of-the-result
   into a disclosed sensitivity analysis. Either way, blind the adjudicator to rater identities.
4. **Delete the two §1 leaks:** strip "(S3 wild + calibration + M-yield audit, interleaved)" from
   `c3-rater-materials.md:45-46`; rewrite `:71-73` as a plain instruction ("use only the provided
   docs/spec/source bundle; do not consult tools, people, or the web") without presupposing our
   tool's opinions exist. Propagate to the 中文版 fork.
5. **Calibration used as the bias instrument it already is:** pre-register the calibration mix
   (benign-skewed, ratio fixed in advance) + per-rater confusion matrices vs known labels + a
   directional false-genuine statistic feeding a pre-registered sensitivity band on S3 precision.
6. **Underspecified guardrails:** adjudicator audits ALL agreed-underspecified cases; pre-registered
   bound (>30% of S3 ⇒ promoted to a qualifying finding); fast-underspecified time audit named.
7. **Reliability decision rule pre-registered** (Attack 4 fix) + staff 2+1 raters.
8. **Disclose the R6 rubric delta** via a freeze §6 amendment; fix both "verbatim" claims (Attack 6).

With 1-8 in the materials before first contact, the strongest reject dissolves into a
threats-to-validity paragraph with receipts: screening + debrief transcripts, a bias audit against
known labels, a precision figure whose primary form the non-blind party cannot inflate, and a
pre-priced low-κ branch. Without them, this protocol hands the PC its reject rationale in the
authors' own words.

---

## Finding table (status summary)

| # | Attack | Status | Load-bearing cite |
|---|---|---|---|
| 1a | Blindness screen has no instrument/attestation | OPEN | c3-rater-materials.md:175, :78-83 |
| 1b | No debrief manipulation check anywhere | OPEN | grep: zero hits in c2c3 tree |
| 1c | Rater-facing brief leaks strata + tool existence | OPEN | c3-rater-materials.md:45-46, :71-73 vs :23-27 |
| 1d | Adjudicator not required to be blind; decides all contested cases | OPEN | :138-139, :177-178 |
| 1e | Step-5 gate audits corpus, not raters | OPEN | step2-execution-checklist.md:152-157 |
| 2 | Pay neutrality + citation burden + disclosure | PRE-REBUTTED | :66-69, :81-82, :125, :169-184 |
| 2a | Calibration mix teaches inflated base rate | OPEN | c3-case-corpus-plan.md:15,27; plan §1:29-34 |
| 2b | Known labels never used as bias/accuracy audit | OPEN | c3-rater-materials.md:152-156, :130 |
| 3 | Underspecified fraction + both-ways precision | PRE-REBUTTED | plan:131-133; c2-freeze.md:203-206 |
| 3a | Agreed-underspecified unaudited; time incentive | OPEN | :138-139, :68, :130 |
| 3b | No underspecified bound; R6 strip manufactures it | OPEN | :29-34; c2-freeze.md:173-178 |
| 4 | S3-only κ primary + calibration-inflation caveat + AC1 named in advance | PARTIALLY PRE-REBUTTED | :141-146 |
| 4a | Own prior predicts S3 κ≈0.43; no low-κ branch; gate on easy set only | OPEN | :153-156; plan:29-34 |
| 4b | "Fresh raters" fiction; no reserve rater | OPEN | :154-155; plan:137-140 |
| 6 | "VERBATIM-identical" frozen-rubric claim is false | OPEN | :87, :106-107 vs c2-freeze.md:152, :173-174 |
| — | Case-count arithmetic drift (60 vs ~75; ~20 vs ~30) | OPEN (minor) | :45, :150; plan:138 |
