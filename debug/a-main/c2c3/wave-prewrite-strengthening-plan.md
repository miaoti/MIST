# Wave PRE-WRITE STRENGTHENING (PWS) — the user-elected 4-leg menu before any write-up — rev 2

**Date:** 2026-07-16 · Owner: main_track · Status: **rev 2 — rev-1 3-cold = A MAJOR-REVISION-2B ·
B REJECT-AS-WRITTEN-2B · C ACCEPT-W-FIXES-4B; ALL 8 blocking folded per
`REVIEW-PWS-PLAN-RECONCILIATION.md` (headline: the CORRUPTED-write class split — 6/7 faults
incl. both new-site candidates are present-but-wrong, OUTSIDE MIST's lost-only scope).
Gate = confirmation pass ⇒ execute (/goal pre-authorization).**
**Trigger:** USER 2026-07-16 — "把你说的这些点都做了" over the strengthening menu, under the
standing gate "NO write-up of any kind until ALL experiments incl. the rater study are
done". This wave = menu items 1-4; the rater study stays user-side.
**Recorded adjudication (f-corpus-spec §6):** the R1 stop-and-replan is resolved as the
survey-recommended **option 2** — accept the <20-distinct-sites finding, report BOTH
denominators (distinct-site AND case-run); the F-corpus is built for case-run depth +
mechanism coverage + upstream grounding, never sold as reaching ≥20 sites. **Sites
correction of record (B): new sites ≤ 2 (F8/F14); ~+7 = CASE-RUNS — the user-facing
'+5 位点' menu framing was an overclaim, corrected.**

## §1 The four legs

### L1 — EvoMaster REAL-TOOL detection arm (kills the strawman/no-real-tool objections)
- **Tool:** EvoMaster (open-source), BLACK-BOX mode; version + JAR sha PINNED at Phase 0
  (latest stable release), recorded in the RESULT.
- **Design (honesty rail first):** EvoMaster generates its OWN tests — these cells are a
  per-SITE REAL-TOOL DETECTION experiment ("given budget B on the FAULT-ACTIVE SUT +
  our committed OpenAPI spec, does the tool's own oracle set surface the masked fault?"),
  reported as a SEPARATE table (`benchmark/scoring/evomaster-detection-table.json`
  or sibling) — **NEVER merged into the matched-recall table** (different inputs; the
  apples-to-oranges rail from B-B1 applies symmetrically).
- **Sites (4, each its own single-tenant window or ride-along):**
  (i) TT cancel-refund (fabricatedack toggle ON for the run window);
  (ii) TeaStore order (maintenance toggle ON — NOT the VS: EvoMaster traffic comes from
  outside the mesh path we severed; maintenance masks at persistence for all callers);
  (iii) SockShop shipping (source-inherent swallow — always on);
  (iv) OTel checkout (accounting `dependency_scale_zero` — **the kafka flag is BARRED**,
  the cset no-third-attempt rule stands).
- **Protocol per site (rev-2 re-pinned):** black-box budgets scaled to spec size —
  **60 min** TeaStore (9 ops) / OTel (6) / SockShop (26); **120 min TT (265 ops)**, plus a
  SECOND seed on TT only; fixed seeds recorded; spec = the committed one; base.url = the
  PF. **Per site TWO runs: the FAULT-ACTIVE run + a CLEAN-SUT CONTROL run** (same
  budget/seed, fault OFF — the tool's FP/noise baseline). **MANDATORY REACHABILITY
  datum:** from the tool's own logs/generated tests, count requests hitting the target
  site's entry endpoint — a miss WITHOUT reachability = `NOT_INTERPRETABLE` (disclosed,
  never sold as a real-tool miss). Afterwards restore the fault OFF + verify.
  **Adjudication rule (pinned):** the tool "detects" iff any generated test FAILS with a
  reason implicating the masked write (its taxonomy: 5xx, schema violation, timeout).
  Expected by construction: masked-2xx produces none — the cell is then a MEASURED
  real-tool miss WITH reachability evidence. Any unexpected detection = a finding.
- Output: per-site rows (tool version, budget, #tests, #faults-it-did-find [loud ones are
  fine and expected], masked-site verdict) + logs banked.

### L2 — F-corpus build (upstream-grounded positives; the size/injected-positives answer)
- **Authority:** `f-corpus-spec.md` (B0 survey EXECUTED; eligibility + occupied map +
  implementer obligations §7 verbatim). Build list = the 7 ELIGIBLE: **F1, F8, F10, F11,
  F13, F14, F20** (F8/F14 = new-site candidates; the rest = mechanism variants on
  occupied sites, floor-credit only). Swap pool: F12 only-if-live-upgraded.
- **Two-actor clean-room (X5, verbatim):** an ISOLATED implementer subagent (explicit
  non-fable model) whose ONLY inputs = `f-corpus-spec.md` + the clean Apache-2.0
  `FudanSELab/train-ticket` base source in-repo; it never fetches the upstream fault repo
  or any re-host; per-fault input artifact recorded; modified files carry Apache-2.0 §4
  change notices. The orchestrator (me) reviews diffs for conduct only.
- **CLASS SPLIT (rev-2, the convergent A/B blocking fold):** per-fault EXPECTED CLASS
  stamped from the survey — LOST vs CORRUPTED-present (6/7 incl. F8/F14 are corrupted).
  The corrupted class is IN-CORPUS (the survey §1 eligibility = "lost OR corrupted") but
  OUT of MIST's lost-only oracle scope: an ADDITIVE `fault_class` schema amendment
  (disclosed) admits it; **MIST column routing = lost-class bindable → 2.75-A read-back
  leg; corrupted-class → principled n_a "out-of-scope-by-design"** (the Scope line;
  MIST's correct abstention is NEVER scored a miss — the twice-pinned lost-not-corrupted
  memory fact). The paper gains the anti-self-serving datum: the benchmark is BROADER
  than the tool.
- **TOGGLE + BUILD DISCIPLINE (rev-2):** ONE fork image carries ALL implemented faults
  behind INDIVIDUAL runtime toggles, DEFAULT OFF (the `drawbackFaultMode` precedent);
  pre-build COLLISION ANALYSIS for the shared `drawBack` method (F1/F13 vs the
  fabricatedack injector — orthogonal toggles verified in code review BEFORE building);
  builds OFF-window; ONE set-image cycle; W5 = per-fault toggle windows on that deploy.
  **Post-build fabricatedack REGRESSION** (the paired FIRE must reproduce) before any
  F-corpus capture counts — the flagship case's reproducibility is protected by the
  default-OFF invariant + this regression.
- **Per-fault pipeline:** implement in the fork branch (isolated actor) → [one batched
  build + set-image] → **B-m6 LIVE IN-CLASS VERIFICATION GATE, class-aware (rev-2):**
  acked-2xx + the durable state WRONG-or-ABSENT by direct read, matching the fault's
  DESCRIBED class (2 attempts then swap/disclose) → capture fault+control legs
  (control-first, T2 cadence, per-run marker salt, 800 ms pacing) with **machine-read
  read-back files persisted per leg** (the A3(ii) lesson: never sidecar-null again;
  mysql reads via kubectl-exec script FILES) → case files (schema-valid; occupied ones
  stamped mechanism-variant, never new-site) → neutralized rater derivatives via the
  hardened harness → validator + tell-audit dry-run.
- **Yield honesty:** up to ~7 fault cases + ~7 controls (case-run denominator); sites
  8 → at most ~10 per the survey ceiling arithmetic; every shortfall pre-disclosed.
- MIST column for new cases: routed by the CLASS SPLIT above (lost-class AND bindable →
  a 2.75-A-style read-back leg IN the capture window; corrupted-class → the
  out-of-scope-by-design n_a REGARDLESS of bindability; lost-class non-bindable →
  principled n_a, adjudicated).

### L3 — TT re-capture: per-endpoint read-backs for the 9 truncation-gated cases
- Per case: re-run the case's write + an ACTING-RECORD-SCOPED read-back (membership by
  business key + count; per-endpoint probe, not the global collection window), persisting
  the read-back BODY to a machine-read file per leg; then neutralize → renders into
  `rater-sidecars-staging/` + a rateability re-adjudication memo (**the +9-units input
  for the USER's seal decision; the swap itself stays user-witnessed**). Sealed sets
  remain READ-ONLY.
- Uses the committed capture-specs/replay scripts; markers fresh-salted; ground truth =
  direct DB reads.

### L4 — Tracetest LIVE (checklist 2.5.5; upgrades the surrogate to real-tool cells)
- Install the Tracetest server + agent on the kind cluster (version pinned at install;
  official chart/CLI), wired to the existing OTLP/jaeger path; javaagent-instrument the
  cancel-refund pair services per the committed `instrument-leg1.sh` recipe (1.33.6 pin).
- Run the ALREADY-AUTHORED specs (`b4/e2/tracetest-specs-authored.yaml`) as REAL Tracetest
  tests: control leg + fabricatedack leg, N≥3 each → real per-leg verdicts for the
  flagship pair (presence / db-insert / span-error).
- Fold: the flagship pair's `tracetest_presence_surrogate` cells get REAL-TOOL siblings
  (a new arm `tracetest_live` in the A6 harness — the surrogate arm + label stay for the
  other cases; nothing relabeled retroactively). **Disagreement disposition PRE-COMMITTED
  (rev-2): if live disagrees with the surrogate, investigate selector/semconv drift
  FIRST; the LIVE cell enters the table, the surrogate cell keeps its label, and the
  disagreement is a REPORTED finding — never silently reconciled.**
- Uninstall/scale-down tracetest at window end; instrumentation restored (0 JTO carriers).

## §2 Windows + order (single-tenant discipline)

- **W1 TeaStore (short):** L1-(ii) EvoMaster. Revive → toggle window → run → restore →
  down. (No mesh needed.)
- **W2 SockShop (short):** L1-(iii). Revive (rabbit user + warm-up) → run → down.
- **W3 OTel (short):** L1-(iv) with accounting scale-0 during the budget → scale-back →
  canary → down. Kafka flag untouched.
- **W4 TT MEGA-WINDOW (rev-2 internal order re-pinned; 5/9 L3 targets share
  ts-inside-payment-service with the fabricatedack toggle ⇒ L3-FIRST is BINDING):**
  revive → **L3 the 9 re-captures FIRST on the clean graph** → per-transition VERIFY
  (toggle state none; fork faults OFF; zero fault residue — the TT-omnibus
  per-transition discipline) → L4 Tracetest (RAM-GATE before install — its self-hosted
  stack needs PostgreSQL+MongoDB+NATS; run on the LEAN keep-set per post-reboot-lean
  precedent; trace-export FLUSH GATE before scoring each run) → verify → L1-(i)
  EvoMaster (fabricatedack ON for its budget only, then OFF+verify) → teardown-verify.
  **WITHIN-WINDOW RAM + nacos doubleWrite checkpoints BETWEEN EVERY sub-leg** (TT-omnibus
  lesson #6) — for W5 too.
- **W5 TT F-CORPUS WINDOW(S):** L2's per-fault set-image cycles (builds happen OFF-window;
  never build while a graph is deployed — build first, then deploy windows). W5 may split
  into W5a/W5b by fault batches; full-graph revival reused.
- Order: W1 → W2 → W3 → W4 → W5 (cheap wins first; the mega-windows last). RAM checkpoints
  between; PFs per window; all tenants to 0 between windows.

## §3 Budgets + stop rules

- W1/W2/W3 ≈ ½ day combined. W4 ≈ 1 day. L2 implementation (off-window) ≈ 1-2 days +
  W5 ≈ 1 day. Whole wave ≈ **3-5 days**; per-leg >1.5× ⇒ pause+disclose.
- Licenses (recorded): EvoMaster **LGPL-3.0** (run + cite; never vendor the JAR);
  Tracetest **MIT**. Both = version-pinned RUNS; outputs = our evidence.
- Stop rules: B-m6 (class-aware) fail ×2 ⇒ swap/disclose (never force a fault in-class); EvoMaster
  crash/incompat on a SUT ⇒ that site = tool-not-runnable, disclosed (never hand-patch
  the tool); Tracetest install fights >½ day ⇒ L4 closes as install-blocked-disclosed
  (the surrogate cells stand, labels unchanged); wedge beyond runbook ⇒ STOP+surface;
  kafka flag = BARRED; sealed sets read-only; no MIST oracle code (bindings/runners/
  external tools only); generatedb gate for any TeaStore pipeline touch.

## §4 DoD

1. L1: 4 site rows measured (or tool-not-runnable-disclosed) + the separate detection
   table + RESULT section; never merged into matched-recall.
2. L2: every attempted fault = in-class-verified case pair OR a disclosed swap/shortfall;
   corpus additions ADDITIVE (validator-green; censuses/map/release-staging regenerated);
   the option-2 adjudication recorded in the freeze row.
3. L3: 9 re-captured per-endpoint read-back bundles + staged renders + the re-adjudication
   memo (the seal decision input); sealed sets untouched.
4. L4: real-tool flagship cells (or install-blocked-disclosed); surrogate labels intact.
5. RESULT(s) + freeze rows + checklist/FILE_INDEX/memory sync + post-hoc 3-cold review.
6. After DoD: the remaining pre-write items = the RATER STUDY + user-side decisions ONLY.

## §5 NOT in scope

The rater study itself (user-side: IRB/raters/seal); any write-up (USER GATE 2026-07-16(b));
the seal swaps (user-witnessed); kafka S1 anything (closed-captured; third attempt barred);
new SUTs; MIST oracle/tool code; SmartFetch.
