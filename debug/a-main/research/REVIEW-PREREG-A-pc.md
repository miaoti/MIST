# Prereg cold review A — hostile-PC simulation on g2-novelty-comparator-prereg.md

**Date:** 2026-07-02. Independent cold reviewer (no shared context), one of three on
the G2/G3 prereg wave. Verified against the repo (found the fault list committed in
`mist-cli/src/main/resources/My-Example/trainticket/target-triples.yaml` and
`debug/a-main/benchmark/cases/TT-adminroute-lostwrite-001.json` — the exact injection
site + fork commit are public). Findings ranked; reconciliation in
REVIEW-PREREG-RECONCILIATION.md.

## Findings (most severe first)

### F1 — CONFIRMED [CRITICAL]. Blind-authoring is unimplementable as written: the "reveal" already happened
§2.2 freezes assertions "BEFORE the injected-fault list is revealed", but the fault
list has been public since Gate-1 (target-triples.yaml ships both LOST_WRITE flags;
benchmark case names `AdminRouteServiceImpl.createAndModifyRoute` on
`MIST-trainticket@5c471dd8`; EXECUTION/prep docs name the faults), and §2.1's
authoring inputs ("service docs/source") don't pin WHICH source tree — the SUT fork
literally contains the skip-persist hooks. Timestamps cannot prove non-access.
**Fix: re-base blindness on enumerated, hash-frozen provisioning** — upstream
FudanSELab source only; no MIST-repo/fork/web access for the author; provisioning
list logged in the prereg.

### F2 — CONFIRMED. Paragraph overclaim: "every open-source system we evaluate cannot meet" the four Cast ingredients
False on its face: TrainTicket/TeaStore are Java (AOP meetable); assertion points are
labor, not access. README §2 claims deltas, never impossibility. **Fix (rewrite):**
"two of those ingredients — production traffic and the historical trace baselines
derived from it — are unavailable by construction outside a production operator; the
other two (language-specific AOP agents, per-site assertion-point configuration) are
per-system costs that scale with fleet size and polyglot spread; none of the OSS
systems we evaluate has production traffic or baselines, and three of six are not
Java."

### F3 — CONFIRMED. "no assertion points … no human-authored assertions" unscoped against MIST's own hand-curated triple registry + TT-coupled ack decoder
MIST ships a hand-authored per-endpoint registry (incl. a TT-specific station-pair
adapter) and a TT ack rule (R5) — configured check points with a SUT-specific success
predicate. **Fix:** claim "no expected-outcome specification per check point" (the
expected value derives from the request/control run), and either show triples are
spec-derivable or disclose hand-selection.

### F4 — CONFIRMED. Comparator competence downgraded and floorless; no branch for failed calibration
EXECUTION G2 says "competent engineer"; prereg says "agent/engineer" with no
competence bar and no rule if the frozen blind set flunks the sensitivity check
(re-authoring breaks blindness; proceeding = strawman). **Fix:** pre-register the
competence floor (a set that misses the trivial create-then-list-contains contract is
incompetent) + the consequence branch (e.g., independent second author, disclosed).

### F5 — CONFIRMED. The authoring brief is not pre-committed
The brief can steer toward or away from read-back assertions; shipping it post-hoc is
not blindness. **Fix (cheapest):** freeze the brief's exact text inside the prereg
now.

### F6 — CONFIRMED. Endpoint-selection leak: "same endpoints" reconstructs the fault list
If the author is briefed on exactly the triple-registry endpoints (two, both fault
targets), the endpoint list IS the fault list. **Fix:** author covers ALL write-path
endpoints from the spec; head-to-head restricts to the target subset afterwards.

### F7 — CONFIRMED. "Matched recall" undefined for two fixed binary oracles; detection unit unstated
No tunable knob exists; unit (per fault instance? record? triple?) undefined despite
reconciliation B-6. **Fix:** pre-register operating points (MIST = observed-gated
stratum; comparator = full frozen set) + unit (per injected fault instance per
triple).

### F8 — CONFIRMED. Miss-category adjudication: authors judge their own win; one-directional forensics
No blind/dual-rater κ (README §6 imposes κ on its own stratum-3); fuzzy boundary
exactly where the incentive lives; only comparator misses get root-caused — MIST's
misses (NOT_EVALUABLE, timeout-gated, R1 truncation) get no category table;
"comparator-infra-failure" needs pre-registered evidence criteria (harness logs,
rerun policy). **Fix:** symmetric miss tables, blind dual-rater categorization with
κ, infra-failure evidence rule.

### F9 — CONFIRMED. FP outputs under-implement reconciliation R2 (vacuous-PASS hole survives)
Prereg commits only the interval; R2 requires interval + gate histogram +
non-trivial observed-gated denominator + healthy-Jaeger evidence (+ B-6 per-triple,
denominator = acked records). **Fix:** one sentence adding these.

### F10 — CONFIRMED (internal inconsistency). The paragraph hides the metamorphic concession its own hygiene rule mandates
Create-then-read-membership is textbook CRUD-metamorphic (Segura); MINES equally
"label-free". **Fix:** put the concession IN the paragraph: "a fixed, generic per-run
metamorphic relation — not per-endpoint authored assertions" (this also sharpens the
delta vs Filibuster/Gremlin).

### F11 — CONFIRMED tension. Injected-fault "decisive results" labeled PC-moving while the benchmark pre-registers the fault as oracle-co-designed
The case file pre-declares `mist_dataintegrity_oracle: flag`, all others `no_flag` —
injected G2 wins are near-tautological. **Fix:** restrict "moves a PC" to REAL (G3)
defects; injected results are calibration only.

### F12 — CONFIRMED (minor, credibility-fatal in kind). Citation slips
"89 production-confirmed" → must be "89 dev-confirmed" (README §2 verified wording;
§0 carries the same slip — fix both). "observes only the OTel…" → "requires only"
(the oracle also issues read-back GETs). "any language" is ahead of evidence (R5
TT-coupled ack decoder).

### F13 — PLAUSIBLE. The headline question is quasi-tautological and one-directional
Reframe as frequency: "how often do competent blind authors fail to write the
load-bearing assertion, and at what FP cost does a label-free read-back close that
gap" (answer may be "rarely" — the phrasing must not foreclose it).

## Gate-2 criterion fit
Does not quietly weaken the gate; strengthens it twice (calibration; pre-committed
identity). One redefinition: "competently-configured" became "blind-authored" with no
competence floor (F4) — the gate needs both. The paragraph embeds promissory facts
("released labeled benchmark") — acceptance is conditional-on-execution; say so.
**Comparator naming:** without historical baselines it approximates Filibuster, NOT
Cast — keep Cast out of the comparator's name.

## Reconciliation contradictions
F9 (R2 under-implementation); R3 pick()-masking unaddressed in §3.4 calibration (a
masked MIST FIRE reads as a MIST miss with no category); reconciliation §4 wording
("observed-not-visible-on-read-path") not carried into the outputs' stratum naming.

## Verdicts
(a) **Paragraph: NO as-is** — F2/F3/F10 sentences are PC-fatal; skeleton is right and
close. (b) **Comparator: right design intent, not yet non-strawman as specified** —
the biggest hole is F1 (blindness rests on an already-violated reveal-ordering);
re-base on enumerated hash-frozen provisioning + frozen brief text (F5) + all-write-
path endpoint superset (F6).
