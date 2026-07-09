# REVIEW-RATER-1 — methodological soundness of the C3 rater package (cold review 1 of ≥3)

**Reviewed:** `debug/a-main/c2c3/c3-rater-materials.md` (primary), against
`debug/a-main/c2c3/c3-case-corpus-plan.md` (rev 2), `debug/a-main/c2c3-execution-plan.md` §3.1,
`debug/a-main/c2c3/c2-freeze.md` §3 (rev 2), `debug/a-main/c2c3/step2-execution-checklist.md`
§1.9.6/§1.95/step-5 gate, and `debug/a-main/c2c3/c3-rater-materials-中文版.md`.
**Charge:** methodological soundness — blindness under the in-group channel, rubric operational
validity, statistics, ethics/consent, ballot usability. This review GATES contacting human raters.
**Reviewer stance:** cold, no shared context with the authors of the package.

---

## VERDICT: ACCEPT-WITH-CHANGES

The architecture is sound: per-case stratum blindness enforced by a normalization harness plus a
pre-rating tell-audit gate, a frozen three-way rubric with adjudication, S3-only-κ-primary with the
calibration-inflation caveat, and a pre-committed fallback with scars. But the package as written is
**not yet sendable**: the rater-facing boundary is internally incoherent (the recruitment brief
itself leaks the stratum structure §0 exists to hide), the rater copy of the rubric makes
tell-bearing lost-writes unlabelable, the load-bearing MIST-blind screen has no instrument, and the
consent is incomplete for in-group students. **The four [BLOCKING] findings are pre-contact
conditions — do not contact any rater until they land.** MAJOR items M1–M4 must land before corpus
assembly / labeling; M5–M8 as soon as practical.

---

## [BLOCKING] findings

### B1. The rater-facing boundary is incoherent, and §1 itself leaks the stratum structure
- `c3-rater-materials.md:17-18` declares "nothing in the rater-facing materials **(§1–§6)** names
  MIST…" — i.e., §1–§6 are the rater packet. But §5–§6 are study-internal mechanics that destroy §0
  if shown: `:140-144` reveals S3-only vs pooled κ and that "calibration cases are the easy,
  rubric-tuned cases"; `:148-151` reveals a known-label calibration set drawn "from S1 positives +
  S2 benign"; `:153-154` reveals "no S3 peeking".
- Worse, the **explicitly rater-facing** recruitment brief names the strata: `:45-46` "a capped set
  of up to 60 recorded system behaviors **(S3 wild + calibration + M-yield audit, interleaved)**".
  "Wild" + "calibration" + "M-yield" tells a rater that (a) some cases have pre-known labels,
  (b) some were flagged in the wild by something, (c) a *yield*-producing generator exists — i.e.,
  the cases were machine-surfaced. This directly contradicts §0's own invariant (`:19-20`, `:26-27`:
  "Raters believe they are labeling a curated set… nor which detector (if any) surfaced it").
- Smaller instances of the same disease: `:87` (§3 header cites `c2-freeze.md`/"§8.5-1 rule" —
  internal doc names inside a rater-facing section) and `:123` (ballot skeleton comment says
  "rater cannot decode **stratum** from it" — the word "stratum" ships to the rater).
- The Chinese translation carries the identical §1 leak (`c3-rater-materials-中文版.md:40`).

**Fix (concrete):** add a SHIP/INTERNAL tag to every section. Rater packet = §1 (with the strata
parenthetical struck — say only "a curated set of up to N recorded behaviors"), §2, §3 (rubric body
only, headers stripped of freeze/R-references), §4 (comment reworded to "opaque id"), plus a
rater-instructions excerpt of §5 containing ONLY the no-discussion rule. §5 (stats), §6, §7, §8, §9
answer keys = INTERNAL. Re-scope the §0 sentence from "(§1–§6)" to the tagged ship-list. Propagate
to the Chinese copy if a Chinese packet is derived (English authoritative).

### B2. The rater copy of the rubric makes tell-bearing lost-writes unlabelable
`c3-rater-materials.md:89-90` defines **genuine** as requiring "HTTP 2xx **with a success-shaped
body**", and the sentinel rule `:97-102` says a body carrying `{1,"error"}`/`-1` "is NOT
success-shaped… not in the primary discriminating denominator." Apply this as a rater to the known
TT-natural calibration shape (2xx + `{1,"error"}` + refund lost): it fails the *genuine* definition
(not success-shaped), is not *benign* (no doc blesses losing the refund), and is not
*underspecified* (the intended behavior IS derivable). **The rater has no valid label.** The freeze
resolves this on the analyst side — `c2-freeze.md:143-146`: a tell-bearing case "still counts as a
real positive for recall reporting, in its own bucket" — but that sentence was NOT carried into the
rater packet, and `ack_content_visibility` is a machine-set case field, not a rater judgment. As
copied, a denominator-hygiene rule (whose home is scoring, `c2-freeze.md:188-207`) has been folded
into the LABEL definition, which will corrupt labels and crater κ on exactly these cases (they are
in the calibration candidate pool: TT-natural is an S1 asset).

**Fix (concrete):** in the rater-facing rubric, define genuine with ack = "the system acknowledged
the operation (HTTP 2xx)" full stop; move the sentinel test to a separate mechanical ballot field
`ack_carries_failure_sentinel: yes | no` (the rater CAN answer it — the sentinel is in the response
body they see; no tool output needed); keep the discriminating-denominator segregation entirely
analysis-side, keyed off that field. Route the change as a `c2-freeze.md` §6 amendment row so the
R5 one-rubric rule stays honest (see M7).

### B3. The mandatory MIST-blind screen — the load-bearing §1.9.6 mitigation — has no instrument
`c3-rater-materials.md:164-167` says eligibility requires never having "seen the tool, its
hypothesis, this repo, or the head-to-head results… This is screened before assignment and
recorded." That is the entire specification. There is **no question set, no procedure, no decision
rule, no record format** anywhere in the package; `step2-execution-checklist.md:31` lists the "§9
eligibility screen" as the open item, but §9 (`:201-207`) is a SKILL screen only — it does not test
MIST-blindness at all. Two design problems any instrument must solve, unaddressed:
1. **Asking "have you seen MIST?" leaks the tool's existence** to every rater who passes — a
   passing rater now knows a named lab tool exists and can infer the study validates it (partially
   defeating §0's "which detector (if any)" clause).
2. **"Never seen this repo" is not verifiable for a public repo** (the paper cites
   github.com/miaoti/MIST) — self-report is the only probe, and ACL checks only cover private
   artifacts.

**Fix (concrete — author this instrument before any contact):**
- *Objective checks (no student contact needed):* not a contributor/collaborator on the MIST
  repo(s) or paper drafts; not a member of MIST project channels/meeting rosters; advisor attests
  the student has not attended MIST-specific talks. Recorded on a signed screening form.
- *Indirect self-report (does not name MIST):* "List the research projects in the group you can
  describe by topic" (any mention of a microservice fault/oracle/testing tool ⇒ ineligible); "Have
  you read or discussed results comparing testing tools on TrainTicket / Sock Shop?" (yes ⇒
  ineligible); "Which group repositories have you cloned or browsed?" (MIST ⇒ ineligible).
- *Decision rule:* any hit ⇒ ineligible (may help with logistics only, per `:166-167`); borderline
  (e.g., "saw a title slide once") ⇒ ineligible — the rule errs toward exclusion, and §8 is the
  pre-committed relief valve, per `:174-176`'s own "do NOT relax blindness to fill seats".
- *Administration:* by a non-author if available; the form is retained as a study record and cited
  in the threats-to-validity disclosure.

### B4. §2 consent is incomplete for in-group students, and the purpose-withheld design has no debrief
The charge's ethics points are simply absent from `c3-rater-materials.md:77-84`:
- **No right to withdraw** (at any time, without penalty, with labels-to-date discarded on request).
- **No voluntariness / power-dynamics clause** — for students rated by, funded by, or advised by
  the study authors, consent must state participation/refusal/labels have NO effect on academic
  standing, funding, advising, authorship, or letters. §7's arm's-length language covers only the
  adjudicator (`:172-173`, and only "ideally"); nothing covers the RECRUITER, who is the acute
  power-dynamic (ideally not the direct advisor; if unavoidable, disclose in the IRB filing).
- **No debriefing plan.** The design withholds the study's purpose (deception by omission —
  `:19-20`), and `:82-83` "no predetermined 'correct' set of labels" is literally false for the
  calibration and eligibility cases (they have pre-known labels; §9 grades "correct"). Standard
  handling for purpose-withheld designs: (i) reword the consent sentence to what is actually meant
  — "compensation does not depend on which labels you produce"; (ii) state in consent that the full
  purpose will be explained at study close; (iii) add a written debrief at close (reveals the
  detector context, why blindness was required, and offers post-debrief label withdrawal). In-group
  students WILL learn the purpose from the paper anyway — the debrief converts that from a trust
  incident into protocol. This also feeds the M6 IRB filing (deception disclosure + debrief are
  what an IRB looks for).
- **Placeholders:** `[RATE]`/`[HOURS]` (`:68`, `:81`) and the §7 table's "stipend/credit" (`:183`)
  are unresolved. If compensation is course credit, an alternative-assignment clause is required
  (coercion review); decide stipend-vs-credit and fill the numbers BEFORE contact — the brief and
  consent are unsendable with placeholders.

---

## [MAJOR] findings

### M1. The "pooled ≥ 50 guaranteed" sizing fails under the pre-registered S3-scarcity branch
`c3-rater-materials.md:148-151` sizes calibration so "pooled calibration+S3 ≥ 50 is guaranteed
**given the expected S3**… (e.g. ~30 calibration + expected S3 ≥ 20)". But S3 < 20 is not an edge
case — it is a pre-registered branch ("scarcity IS the finding", plan `:180-182`). S3 = 5 with
calibration 30 ⇒ pooled 35 < 50: the guarantee self-destructs exactly when the scarce branch fires.
Since corpus assembly happens AFTER the S3 count is known (checklist step-5 gate,
`step2-execution-checklist.md:152-156`), the fix is free: **calibration size = max(~30, 50 −
|S3|)**, decided at assembly and recorded in the sealed manifest. (S1+S2 ≥ 80 makes the material
available per `:149-150`.)

### M2. M-yield audit cases are homeless in the κ strata
The rater workload explicitly includes the M-yield cluster-audit sample
(`c2c3-execution-plan.md:138`, `c3-rater-materials.md:26-27`), and their labels feed M-yield
precision (plan `:165-167`). But both κ definitions omit them: "S3-only κ PRIMARY" and "pooled
calibration+S3" (`c3-rater-materials.md:140-144`; plan `:150`) — audit cases are neither
calibration nor S3. Their inter-rater agreement will exist and must be pre-assigned a home before
data exists. **Fix:** pre-register measurement-κ = S3 + M-yield-audit (all unknown-truth cases) as
the headline, with S3-only also reported (preserving R7's intent); or explicitly exclude audit
cases from κ with a stated reason. One sentence, but it must be written BEFORE labels exist.

### M3. No pre-registered response to a LOW S3-only κ — the PRIMARY number is gate-less
The κ-gate (`c3-rater-materials.md:153-155`) triggers on the CALIBRATION round only. Calibration κ
is upward-biased by construction (the package says so itself, `:142-144`), so the realistic failure
mode is: calibration κ = 0.75 → gate passes → S3 labeled → **S3-only κ (the headline reliability
number, R7) comes out 0.35 — and the protocol has nothing to say.** Rubric iterations are (rightly)
forbidden at that point (no S3 peeking, and S3 is already labeled). Pre-register the branch now:
low S3-only κ (e.g. < 0.4) ⇒ adjudicated labels still reported (the third adjudicator resolves
every disagreement, so labels exist), but the C3 precision claim carries an explicit reliability
caveat / drops one register, and the disagreement-dense cases are reported as their own table. Post
hoc, this paragraph is a scramble; pre-registered, it is a strength.

### M4. The rubric under-determines the boundary shapes that will dominate the wild stratum
Stress-testing §3 with realistic microservice cases:
- **Eventual consistency WITHOUT a documented window** — the single most likely S3 shape. The
  benign worked example (`c3-rater-materials.md:114-116`) covers only "documented as
  eventually-consistent… within the documented window". Docs say "async" with no bound + observed
  absence at +30s: rater A reads *genuine* ("the durable effect did not occur" — `:57-58`), rater B
  reads *underspecified* ("no derivable window"). Both are defensible readings of the current text.
- **Idempotent retries** (two identical acked POSTs, one row) and **documented best-effort queues**
  ("may drop under load" — acked-but-lost yet contract-blessed): neither is covered by any example
  or clause; the best-effort case is a benign-that-looks-genuine trap.
- Partial-write/atomicity-unstated IS covered (`:116-118`) — good.
Worked examples are also still patterns, not authored anchors (`:110` "to be authored on real
calibration cases"), which is fine at contact time but not at labeling time.
**Fix:** (i) add a default decision rule — "if the promised effect is documented as asynchronous
and no time bound or completion signal is derivable from docs/spec/source, and the observation
shows absence at a finite time ⇒ *underspecified*, not *genuine*"; (ii) add three boundary worked
examples (undocumented-window EC; idempotent retry; documented best-effort drop); (iii) add
"worked examples authored + rater rubric packet final" as a 9th check in the step-5
corpus-assembly gate (`step2-execution-checklist.md:152-156`). Otherwise the ≤2 calibration-only
iterations (`:153-155`) will be spent discovering these, or worse, S3-primary κ eats them (M3).

### M5. Independence protocol has no rater↔TEAM rule — for in-group raters, the bigger channel
§5 (`c3-rater-materials.md:136-137`) and the consent (`:80-81`) bar rater↔rater discussion only.
In-group raters share offices/Slack/lab lunches with the MIST authors for the entire labeling
window; the realistic leak is a rater asking a labmate "what's this study about?" — not rater↔rater
collusion. `:71-73` ("Please do not seek them out") covers tool output, not provenance questions to
group members. **Fix:** (i) a quiet-period rule binding the TEAM (no author discusses the study,
the cases, or MIST-adjacent results with any rater until close); (ii) extend the rater instruction
to "do not discuss this study or these systems with anyone in the group until notified of close";
(iii) do not reveal co-rater identities to raters (separate delivery already implies this — make it
explicit); (iv) extend the disclosed threat text (`:168-171`) beyond "relationship bias" to name
demand characteristics: an in-group rater may INFER a detector exists even when blind to which tool
— per-case stratum blindness (the invariant the precision claim actually needs) survives that
inference via B4-uniformity + the tell-audit, and the m4 calibration-accuracy audit bounds the
resulting label-shift risk. That is the honest and defensible form of the disclosure.

### M6. IRB timing: determination is pinned "before LABELS", but recruiting students is itself an IRB-covered activity
`c3-rater-materials.md:186-189` requires the determination "BEFORE labeling begins". At most
institutions, recruitment materials, screening, and consent of human subjects fall under the
protocol — contacting students first risks a compliance defect the ethics statement (`:193`) would
then have to disclose. The in-group channel removed the 2–6 wk outreach lead, so filing-first costs
nearly nothing. **Fix:** move the precondition to "before any rater is CONTACTED" (align B-M8 and
checklist 1.9.6/1.95.3 wording, `step2-execution-checklist.md:29-33,47-49`). Also fix the
exemption-rationale over-claim at `:190-191`: the study DOES collect person-linked data (screening
records B3, eligibility outcomes, per-case `time_minutes`, payment records) — claim "no sensitive
personal data; only anonymized labels + rationales are released", not "NO personal… data".

### M7. The "VERBATIM-identical" rubric claim is false — and the differences prove a fork is necessary
`c3-rater-materials.md:87` and `c2-freeze.md:152` both claim the two §3s are identical. They are
not: the rater copy (correctly, per R6 stripping) drops the freeze's "(including the paired
clean-run state)" (`c2-freeze.md:174`), drops the TeaStore/TT tell-bearing note
(`c2-freeze.md:167-170`), restructures the underspecified clause, and paraphrases the sentinel
rule. B2 will add a further deliberate divergence. **Fix:** replace "VERBATIM-identical" with the
true relationship — "substantively identical; the rater-facing copy omits/adjusts the analyst-only
clauses enumerated in a delta note" — and log the rater-copy adjustments (incl. B2) as a
`c2-freeze.md` §6 amendment row. A falsifiable identity claim that a `diff` refutes is exactly what
a hostile artifact reviewer will run.

### M8. Plan §3.1 still pre-registers pooled-primary κ — stale against R7
`c2c3-execution-plan.md:150` ("κ computed over pooled calibration+S3 (n ≥ 50)") predates R7;
the freeze (`c2-freeze.md:182-184`) and the materials (`:140-144`) made S3-only PRIMARY. The plan
calls itself "the executable pre-registration; deviations… are disclosed amendments" (`:6-7`).
The primary reliability statistic changing between documents without a dated amendment in the plan
itself looks like post-hoc statistic-shopping. **Fix:** one dated amendment line in plan §3.1
pointing at R7 (freeze rev-2, 2026-07-08) — the switch is legitimately pre-execution; make that
visible where the pre-registration lives. (Same pass: plan `:138` says calibration "~20", the M1
fix made it "~30" — see m1.)

---

## [MINOR] findings

- **m1.** Calibration-size drift: ~20 (`c3-rater-materials.md:23-24`; plan `:138`; 中文版 `:22`)
  vs ~30 (`:150`; corpus plan `:15`). Update §0 + plan to the M1-fix value (and to M1's adaptive
  rule).
- **m2.** The "up to 60" cap (`:45-46`) is arithmetically busted by 30-calibration + 40-S3 +
  audit (≥70+). The pay clause (`:66-69`) survives, but fix the cap text or pre-commit the
  priority rule for what gets dropped at >60.
- **m3.** Ballot (`:121-131`) lacks `rater_id` and `rubric_version` — both required to join
  ballots across the κ-gate relabel rounds and to prove which rubric version produced each label.
  Also specify WHERE "state what's missing" goes for underspecified (`:125`) — a dedicated field,
  not an overload of `citation`.
- **m4.** Add a per-rater calibration-accuracy report as a demand-bias audit: a rater
  systematically over-calling *genuine* is directly visible against the known calibration labels.
  Cheap, and it is the strongest quantitative answer to the in-group-bias threat (feeds M5's
  disclosure).
- **m5.** "Clopper–Pearson counts when n<10" applied to κ (`:141`) is category-confused as
  written — κ is not a binomial proportion. Specify: at S3 n<10, report raw agreement counts +
  CP interval on the agreement proportion and withhold κ.
- **m6.** "CI units = distinct defect/fault-sites" (`:145-146`) sits inside the
  agreement-statistics bullet; κ's units are rated cases. Scope the sentence to the precision CIs
  (where it belongs, per plan `:154-155`).
- **m7.** Label-name drift: brief says "by-design / benign" and "not decidable" (`:59-62`), ballot
  enum is `genuine | benign | underspecified` (`:124`). Print the exact enum in §1.
- **m8.** §9 screen (`:201-207`): no retake policy; whether the ≤20-min screen is paid is
  unstated (state it — unpaid is defensible at 20 min, silent is not); note it can only be
  administered once the two eligibility cases exist (checklist `1.95.2`), i.e., at assignment,
  not at first contact.
- **m9.** "fresh raters if available" (`:155`): with a small in-group pool shrunk further by the
  B3 screen, fresh raters will NOT be available unless planned. Screen a replacement pool
  (≥2 extra passing students) at recruitment time.
- **m10.** "causal closure" (`:91`) is jargon a rater cannot apply; define rater-facing: "any
  write the implementation initiates as part of fulfilling this operation, per the source."
- **m11.** The Chinese translation (`c3-rater-materials-中文版.md`) mirrors the §1 leak (`:40`)
  and will mirror B2/B4 fixes; if any Chinese-language packet is derived for local raters, add a
  translation-fidelity check to the ship-list (English authoritative).

---

## Answers to the charge, in brief

1. **Blindness under the in-group channel.** What an in-group grad already knows: the lab's
   research area (lab talks ⇒ "a tool that catches masked/lost writes in microservices" ≈ the
   hypothesis), the SUT roster (TT/SS visibly run in the lab), the public repo, the screencast.
   Consequence: **study-purpose blindness is fragile under this channel and cannot be fully
   restored by screening** — a screened-eligible rater can still infer a detector exists.
   **Per-case stratum blindness — the invariant the precision claim actually rests on — is the
   defensible one**, and the package protects it correctly (B4 uniformity + strip-list +
   tell-audit gate). The §1.9.6 mitigations are the right set but only 3 of 4 are operationalized;
   the screen is aspirational text (B3), the rater↔team channel is unaddressed (M5), and §1
   currently leaks the very structure blindness hides (B1). With B1/B3 + M5/m4 landed, the §7
   disclosure ("internal but MIST-blind, independence by protocol") is honest and defensible.
2. **Rubric operational validity.** The three-way core + grounding-citation requirement +
   underspecified-disagreements-to-adjudicator are operationally sound. Two raters will NOT apply
   it consistently on undocumented-window eventual consistency, idempotent retries, or documented
   best-effort queues (M4), and the sentinel rule as copied misdirects the LABEL on tell-bearing
   cases (B2). The sentinel test itself IS applicable by a tool-blind rater (it is body-visible);
   the fix is scope, not observability. Worked examples must become authored anchors before
   labeling (M4 gate check).
3. **Statistics.** κ-gate mechanics are verbatim-consistent across materials/freeze/plan
   (verified below). S3-only-primary + pooled-secondary-with-inflation-caveat is internally
   coherent and is the honest answer to the calibration-circularity charge — the circularity is
   real (calibration tunes the rubric AND sits in pooled κ) but acknowledged and demoted. Gaps:
   the pooled≥50 guarantee fails in the pre-registered scarce branch (M1), M-yield-audit cases
   have no κ home (M2), the PRIMARY κ has no low-value branch (M3), and plan §3.1 text is stale
   against R7 (M8). CP-at-n<10 needs one clarifying sentence (m5).
4. **Ethics/consent.** Incomplete for in-group students: no withdraw right, no
   no-academic-consequence clause, no debrief for a purpose-withheld design, unresolved
   compensation placeholders (B4); IRB timing should move to before-contact and the "no personal
   data" claim is overstated (M6).
5. **Ballot usability.** Executable-as-is except: missing `rater_id`/`rubric_version`, ambiguous
   underspecified-grounding field (m3), stratum-word in a shipped comment (B1), and the §9 screen
   is skill-only — it is NOT the blindness screen and cannot double as one (B3).

## Verified correct (checked, not rubber-stamped)

- κ-gate mechanics (≤2 iterations, calibration-only, full relabel, fresh-if-available) are
  consistent across `c3-rater-materials.md:153-155`, `c2-freeze.md:180-182`,
  `c2c3-execution-plan.md:133-136`.
- No case double-duty in the measurement: calibration never reused as S3 (`:155`), eligibility
  cases disjoint (m7, `:205-207`), machine disjointness by true id in the step-5 gate
  (`step2-execution-checklist.md:154-155`).
- R6 clean-run stripping (`:28-34`) closes a real deterministic stratum tell; strip-by-default is
  the right choice; corpus plan §5 genuinely operationalizes blindness (explicit strip-list,
  relative-times-only, no-own-clock, cross-strata shape uniformity, leak fixture —
  `c3-case-corpus-plan.md:85-93`).
- Pay decoupled from labels (`:67-69`); hours arithmetic correct (60 × 15–45 min = 15–45 h).
- Adjudicator information diet correct (both ballots + admissible evidence only, `:138-139`).
- §8 fallback is operationalized, not aspirational: trigger point (step-5 gate), pre-committed
  scars (abstract demotion, label release, author-pair κ) — `:195-199`, plan `:141-144`.
- IRB-precondition-notwithstanding-channel correctly retained (`:186-189`); B-M8 honored (timing
  tightened by M6).
- The step-5 corpus-assembly entry gate (8 checks incl. tell-audit, sealed manifest with rubric
  version, IRB received, capture-status) is a real, executable blindness gate
  (`step2-execution-checklist.md:152-156`).
- The channel-decision riders in §7 are the right four mitigations in principle; the disclosure
  text exists in both materials (`:168-171`) and plan (`:145-149`).

**Disposition requested:** land B1–B4 (pre-contact), M1–M4 (pre-assembly/labeling), M6 + M8
(document hygiene, this week), M5 + M7 + minors in the same fix wave; then this reviewer's verdict
converts to ACCEPT on re-read of the diff.
