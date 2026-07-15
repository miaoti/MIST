# Wave M-YIELD-COMPLETION — finish Step 4 across the remaining 5 SUTs — rev 1 (DRAFT, pre-review)

**Date:** 2026-07-15 · Owner: main_track · Status: **rev 1 — awaiting 3-cold review (goal-mode
gate: ALL reviewers accept before execution).** User direction (AskUserQuestion 2026-07-15):
finish ALL experiments before drafting → this window completes Step 4 (M-yield), the last
substantive experiment surface. Predecessor: the TT leg (TT-omnibus, `RESULT-tt-omnibus.md`,
Step 4 currently ◐).

## §1 Scope — the five remaining M-yield legs at the frozen §3.2 budgets

| SUT | tier (frozen §3.2) | budget | enablement state (verified 2026-07-15) |
|---|---|---|---|
| TeaStore | spec-rich | 1 h × 10 seeds | **AUTHOR**: conf + triples + auth glue (only `openapi/` exists — the TT-omnibus B-B1 finding) |
| SockShop | spec-rich | 1 h × 10 seeds | READY (real-system-conf + target-triples.yaml + shipping triple) |
| OTel-Demo | thin | 1 h × 3 seeds | **AUTHOR**: conf + triples + auth glue (only `openapi/`) |
| Bookinfo | thin | 1 h × 3 seeds | conf READY (4 GET paths; read-only ⇒ NO DI triples — S2-only contributor per §4; saturation disclosed) |
| Boutique | thin | 1 h × 3 seeds | conf READY; **NEEDS DEPLOY** (checklist 2.4 ☐; `deploy.sh` exists) |

Driven total ≈ 29 h across two overnight windows. LLM-off (as the TT leg, disclosed); one
MIST commit pinned per window, stamped per-run; seeds = `20260714+i` (per-SUT i=0..9 or
0..2 — the same grammar as the TT leg).

## §2 Step-2.75 enablement authoring (TeaStore + OTel; the `mist_authoring` obligation)

- **Pipeline spec inputs = the E1-authored specs** (`evaluation/suts/{teastore,oteldemo}/
  openapi/*-swagger.yaml`) — their FIRST pipeline consumption; TeaStore's spec structurally
  excludes `/rest/generatedb` (the C-6 argument now applies to the pipeline run itself).
- **TeaStore**: `real-system-conf.yaml` (webui + persistence services); auth = the webui
  cookie session (`MstAuthHandler` PER_JVM_COOKIE mode — exists per the auth source; the
  glue is config, not code; if cookie mode proves non-viable for the pipeline, STOP+disclose
  — no oracle-code changes); triples = `teastore-order` (write `POST …/cartAction?confirm=
  Confirm` → read-back `GET …/persistence/rest/orders`, MEMBERSHIP of the supplied
  `address1` marker — the 2.75-A-proven binding, now expressed as a pipeline triple).
- **OTel-Demo**: `real-system-conf.yaml` (frontend `/api`); no auth (session id in body);
  triples = `otel-cart-add` (write `POST /api/cart` → read-back `GET /api/cart`, MEMBERSHIP
  of productId, session-keyed supplied isolation). **DISCLOSED: the checkout DURABLE row is
  SQL-only (no API GET) and therefore NOT pipeline-bindable — the checkout stays covered by
  the runner-level corpus cases; the pipeline triple binds the cart write.** OTel is a THIN
  leg regardless.
- **`mist_authoring` cost RECORDED per SUT** (tier + minutes, the frozen D5/U7 protocol) —
  the carried B'-R4 obligation.
- Boutique deploy (2.4): `deploy.sh` into the kind cluster (light); smoke = home page 200.
  Its G-era conf may need base-url/PF touch-ups only (config, not code).

## §3 Tenancy + sequencing (runbook: big SUTs solo; proven co-fits)

- **Window A (overnight 1): TeaStore + OTel-Demo UP** (proven 25 Gi co-fit with TT at 0):
  enablement authoring + smoke first (attached), then TeaStore 1 h × 10 + OTel 1 h × 3
  sequentially (detached Windows driver ≈ 13 h).
- **Window B (overnight 2): swap → SockShop + Bookinfo + Boutique UP** (light co-reside;
  Boutique deployed at window start): SS 1 h × 10 + Bookinfo 1 h × 3 + Boutique 1 h × 3
  (≈ 16 h). **SS pre-run runbook (standing): re-create the `mist:mist` RabbitMQ user +
  a warm-up POST before any SS run.**
- Close-out: all tenants back to 0, snapshots taken.

## §4 Execution discipline (the TT-omnibus lessons, applied)

1. **Per-seed evidence preservation (fixes the Allure-wipe defect):** after EACH seed the
   driver copies `target/allure-results` + the fault-detection report + `target/test-data`
   into `b4/ttomni/myc/<sut>/s<seed>/` BEFORE the next seed's `deletepreviousresults` wipe.
2. Detached WINDOWS-side PS drivers (the proven all-night pattern) with self-healing PFs;
   attached short calls for ops; NO detached-inside-WSL processes.
3. Load-bearing config stays TRACKED (the triples-move lesson); new confs/triples are
   committed before the first run.
4. Long-window health: a mid-window health probe between seeds (login/home 200) logged by
   the driver — on failure the driver pauses that SUT's remaining seeds and moves on
   (recorded, not silently retried); no nacos on these SUTs (the doubleWrite rule is
   TT-specific).
5. RAM rail: windows run with ONLY their tenants up; RAM checkpoint at window start;
   the lean-profile race-scale pattern on any wedge; never `wsl --shutdown` on a healthy
   cluster.
6. Never GET `/rest/generatedb` (structural: the E1 spec omits it; the rail stays for
   human ops).

## §5 Deliverables + DoD

1. TeaStore + OTel enablement packages committed (conf + triples + `mist_authoring` costs)
   BEFORE their runs; Boutique deployed (2.4 folds ✔ or ◐-with-disclosure).
2. 5 legs at the pinned budgets; per-seed evidence preserved (outcomes survive for EVERY
   seed this time); per-seed outcome table per SUT.
3. Clustering per the frozen convention (endpoint × fault-signature × SUT; 1 rep + 10%
   audit, seed 20260714) over ALL flagged events across the 6 SUTs (incl. the TT leg's 0);
   **NO yield statistic** (rater-gated, Step 5); upstream filings for genuine finds.
4. **Step 4 folds ✔** (all tiers run at pinned budgets); checklist + freeze EXECUTED row;
   `RESULT-myield-completion.md` + post-hoc 3-cold review (the DoD gate); FILE_INDEX/memory.
- **Stop rules:** any MIST oracle/verdict-path code need ⇒ STOP+ASK (enablement = config
  files + at most sanctioned-category glue); an enablement that cannot bind without code ⇒
  run that SUT WITHOUT the DI triple + disclose (M-yield still measures the pipeline's
  generation+status oracles); per-leg budget >1.5× ⇒ pause+disclose; wedge beyond runbook ⇒
  STOP+surface.

## §6 NOT in scope (disclosed)

kafka S1 (deferred rider); the contract-invariant arm spike/run; E1 two-tier grid (Step 3b);
TraceAnomaly; the rater SEAL + IRB items; S3-BENIGN-01 re-cut (seal-time); E6 packaging
(fork-pub = USER); the paper draft (NEXT after this window per the user's direction).

## §7 Review asks

(a) Are the two authored triples (teastore-order membership; otel-cart-add membership) the
right pipeline bindings, and is the checkout-SQL non-bindability disclosure honest?
(b) Is PER_JVM_COOKIE viable for TeaStore's webui session in the PIPELINE (vs the b4/enable
custom runner), and is the STOP+disclose fallback right? (c) Do the budgets/tenancy windows
hold on the single box (29 h driven, 2 nights)? (d) Is the per-seed evidence-preservation
design sufficient to avoid the TT leg's Allure loss? (e) Anything in the Boutique deploy
that risks the existing tenants?
