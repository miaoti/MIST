# Wave PRE-WRITE STRENGTHENING (PWS) — the user-elected 4-leg menu before any write-up — rev 1

**Date:** 2026-07-16 · Owner: main_track · Status: **rev 1 — awaiting 3-cold review
(ALL-ACCEPT + confirmation ⇒ execute; /goal).**
**Trigger:** USER 2026-07-16 — "把你说的这些点都做了" over the strengthening menu, under the
standing gate "NO write-up of any kind until ALL experiments incl. the rater study are
done". This wave = menu items 1-4; the rater study stays user-side.
**Recorded adjudication (f-corpus-spec §6):** the R1 stop-and-replan is resolved as the
survey-recommended **option 2** — accept the <20-distinct-sites finding, report BOTH
denominators (distinct-site AND case-run); the F-corpus is built for case-run depth +
mechanism coverage + upstream grounding, never sold as reaching ≥20 sites.

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
- **Protocol per site:** budget 30 min black-box, fixed seed, spec = the committed one,
  base.url = the PF; afterwards restore the fault to OFF and verify (toggle-verify /
  scale-back). **Adjudication rule (pinned now):** the tool "detects" iff any generated
  test FAILS with a reason implicating the masked write (its failure taxonomy: 5xx,
  schema violation, timeout). Expected by construction: masked-2xx produces none of
  these — but the cell is then a MEASURED real-tool miss, not an argued one. Any
  UNEXPECTED detection = a finding, reported.
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
- **Per-fault pipeline:** implement in the fork branch → build + set-image the touched
  service(s) → **B-m6 LIVE IN-CLASS VERIFICATION GATE** (masked-2xx acked-but-lost
  demonstrated live; 2 attempts then swap/disclose) → capture fault+control legs
  (control-first, T2 cadence, per-run marker salt, 800 ms pacing) with **machine-read
  read-back files persisted per leg** (the A3(ii) lesson: never sidecar-null again;
  mysql reads via kubectl-exec script FILES) → case files (schema-valid; occupied ones
  stamped mechanism-variant, never new-site) → neutralized rater derivatives via the
  hardened harness → validator + tell-audit dry-run.
- **Yield honesty:** up to ~7 fault cases + ~7 controls (case-run denominator); sites
  8 → at most ~10 per the survey ceiling arithmetic; every shortfall pre-disclosed.
- MIST column for new cases: bindable sites get a read-back leg IN the capture window
  (2.75-A style, existing binding classes); non-bindable → principled n_a, adjudicated.

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
  other cases; nothing relabeled retroactively); RESULT discloses agreement/disagreement
  surrogate-vs-live.
- Uninstall/scale-down tracetest at window end; instrumentation restored (0 JTO carriers).

## §2 Windows + order (single-tenant discipline)

- **W1 TeaStore (short):** L1-(ii) EvoMaster. Revive → toggle window → run → restore →
  down. (No mesh needed.)
- **W2 SockShop (short):** L1-(iii). Revive (rabbit user + warm-up) → run → down.
- **W3 OTel (short):** L1-(iv) with accounting scale-0 during the budget → scale-back →
  canary → down. Kafka flag untouched.
- **W4 TT MEGA-WINDOW (long; one full-graph revival serves):** L4 install+instrument+runs
  → L1-(i) EvoMaster (fabricatedack ON for its budget, then OFF+verify) → L3 the 9
  re-captures → teardown-verify between sub-legs (zero fault residue).
- **W5 TT F-CORPUS WINDOW(S):** L2's per-fault set-image cycles (builds happen OFF-window;
  never build while a graph is deployed — build first, then deploy windows). W5 may split
  into W5a/W5b by fault batches; full-graph revival reused.
- Order: W1 → W2 → W3 → W4 → W5 (cheap wins first; the mega-windows last). RAM checkpoints
  between; PFs per window; all tenants to 0 between windows.

## §3 Budgets + stop rules

- W1/W2/W3 ≈ ½ day combined. W4 ≈ 1 day. L2 implementation (off-window) ≈ 1-2 days +
  W5 ≈ 1 day. Whole wave ≈ **3-5 days**; per-leg >1.5× ⇒ pause+disclose.
- Stop rules: B-m6 fail ×2 ⇒ swap/disclose (never force a fault in-class); EvoMaster
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
