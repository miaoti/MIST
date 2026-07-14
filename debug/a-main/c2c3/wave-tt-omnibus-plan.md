# Wave TT-OMNIBUS — one TT-revival window: traced MIST live-run (E2 MIST-column provenance) + M-yield TT leg + E5 ablations — rev 2 (post-3-cold-review fold; CONFIRMATION PASS pending)

**Date:** 2026-07-14 · Owner: main_track · Status: **rev 2 — 3-cold review of rev 1 = A/B/C all
ACCEPT-WITH-FIXES, 11 BLOCKING folded per `REVIEW-TTOMNI-PLAN-RECONCILIATION.md`; execution
gated on a CONFIRMATION PASS (unanimous).** User direction (AskUserQuestion 2026-07-14): the
TT-revival omnibus. Grounding: `remaining-experiments-map.md`.

**rev-2 headline changes:** (1) item 1 RE-SCOPED to an honest **traced MIST LIVE-RUN
provenance/completeness run** — NOT a "discrimination discharge" (the natural-discriminator
question was S3's and CLOSED as scarcity; E2 = matched-recall vs comparators; source-verified:
paired-mode verdicts IGNORE the trace gate [`PairedFaultExecutor.verdict` L447-490], so the
NEW datum is observe-mode-with-Jaeger's first-ever reachable `OBSERVED_COMPLETE_ABSENT` tier);
`mist_trace_shape_oracle` **DEFERRED** (a real but LEARNED oracle needing training/wiring =
tool changes → Branch-B stands). (2) The pre-swap TeaStore/OTel M-yield legs are **DROPPED**
— those SUTs are NOT MIST-runnable (E1 specs only; no `real-system-conf`/`target-triples.yaml`,
which `TargetTripleRegistry` requires); they join SS/Bookinfo/Boutique in a named
**M-YIELD-COMPLETION follow-up window** that owns the Step-2.75 enablement authoring. This
wave's M-yield = the TT leg only (TT verified fully enabled: 265-op spec + conf + triples).
(3) Phase-2 revival = the **§2.6 FULL-GRAPH runbook path** (the S3 runner's `revive` is an
8-service admin-basic subgraph EXCLUDING ts-cancel/ts-order/ts-inside-payment — reference
only). (4) E5 axes PINNED to config-only toggles. (5) Per-leg measurable budgets; WSL
flap-cycle rail; exact tool-code-gate wording.

## §1 Scope (what this wave BANKS)

1. **Traced MIST live-run (the E2 MIST-column provenance/completeness run)** — TT revived
   FULL-graph, OTel javaagents on the cancel-path write services (traced-capture-wave runbook
   + agent pins) + Jaeger; the MIST-trainticket fabricated-ack fork on the cancel-refund
   site; **MIST-the-tool run LIVE in both modes** on the pair (fabricated-ack + clean twin),
   N≥5/leg, control-first, ≤30 min/run:
   - **observe mode WITH `jaeger.base.url`** → the first-ever run where the trace-gated
     defect tier `OBSERVED_COMPLETE_ABSENT` (`DataIntegrityRuntime` L736-773) is REACHABLE;
     whether it fires on the fabricated-ack leg (trace-complete + absent) vs WARN-only
     abstention is the leg's headline measurement — reported EITHER WAY (stop rule: no
     tuning).
   - **paired mode** live verdicts = live-tool provenance for the 2.75-A-style FIRE (the
     paired verdict is trace-gate-independent BY SOURCE — stated as such, not sold as a
     Jaeger effect).
   - Per-run trace exports (exactly-one-trace rule) = comparator-symmetric evidence.
   - **Deliverable cells:** the E2 table's MIST column gains live-run provenance for the
     traced pair; `mist_trace_shape_oracle` cells STAY traced-but-not-run with an explicit
     DEFER note (learned `TraceShapeOracle` needs training/wiring = tool changes; Branch-B).
   - **Claim-language correction (deliverable):** at RESULT time, a freeze §6 note + memory
     correction re-words the standing "discrimination OWED at 2.5/E2": the natural-
     discriminator question closed at S3 (0/1514); E2's obligation = matched-recall
     MIST-vs-comparators on the same cases (comparator cells banked `cd275c9`; this wave
     adds the MIST column); no synthetic-site "discrimination headline" exists to claim.
2. **M-yield — TT leg ONLY (Step 4, PARTIAL by design):** MIST pipeline on the UNMODIFIED
   upstream TT (fork torn down, teardown-verified), LLM-off (disclosed), **1 h × 10 seeds**
   (the pinned spec-rich budget), one pinned MIST commit stamped per-run. Event→case
   clustering frozen PRE-run (endpoint × fault-signature × SUT; 1 representative/cluster +
   10% random audit sample). **In-wave deliverables = clusters + representatives + audit
   sample + author-side upstream filings for genuine finds. The yield STATISTIC
   (genuine/(genuine+benign)) is rater-adjudicated and computes at Step 5 — NOT reported
   from this wave** (the self-concordance rule). Stated prior, not target: the S3 0/1514
   datum predicts low/zero flagged events on upstream TT.
   **Step 4 folds ◐ PARTIAL; the M-YIELD-COMPLETION follow-up window is hereby NAMED:**
   scope = Step-2.75 enablement authoring for TeaStore/OTel (real-system-conf +
   target-triples + auth glue; TeaStore additionally Jaeger-less → TIMEOUT_ABSENT-only,
   disclosed) + the SS/Bookinfo/Boutique thin legs + the TeaStore/OTel legs at pinned
   budgets. Separate plan + review; NOT silently dropped.
3. **E5 ablations (Step 7; "one-SUT-pair scope, TT ×5 seeds"):** axes PINNED NOW,
   config-only (source-verified toggles):
   - A1 `oracle_mode`: paired ↔ observe (invocation-level, two run configs);
   - A2 trace-gate: `jaeger.base.url` set ↔ absent (observe mode);
   - A3 quiescence/cadence: the poll/timeout/settle property set (default cap ↔ extended
     cap variant).
   **EXCLUDED by name** (no config toggle; would need oracle code → collides with the §4
   gate): re-probe toggle, per-triple value-delta semantics. Design = OAT around the
   default config: baseline + 3 axis-variants ≈ 4-5 configs × 5 seeds = 20-25 runs on the
   S1 pair (fork RE-APPLIED by set-image only — never a build while a graph is deployed).
   Per-run duration MEASURED from leg-1 runs BEFORE the batch schedule is fixed; hard cap
   4 d; per-axis deltas reported, no post-hoc axis edits.
4. **Checklist hygiene riders:** 2.5.4 trace-coverage row refreshed if the re-instrumented
   deploy differs; the stale Standing-constraints footer ("TT up (53 pods)…") refreshed to
   the current tenancy reality (C-12).

## §2 NOT in scope (disclosed)

kafkaQueueProblems S1 (deferred); E1 two-tier grid (Step 3b); contract-invariant arm RUN
(needs its execution-model spike); TraceAnomaly arm; **Tracetest Agent live smoke (2.5.5)
and any FRESH comparator-arm cells** (this wave only reuses the frozen comparator cells and
adds the MIST column); **Step 8 / E6 packaging** (not TT-dependent; fork-publication
decision = USER-side); the rater SEAL + all IRB/user-gated items; S3-BENIGN-01 re-cut
(seal-time); the paper draft (user-gated); `mist_trace_shape` training/wiring (Branch-B,
deferred above); any corpus label/cell changes beyond the §1 deliverables.

## §3 Phases (all legs EXCLUSIVE/sequential — no concurrent runs; the R6 host-wedge rail)

- **Phase 0 (no-tenant prep; blocks everything):** (a) freeze the leg-1 run protocol
  (sites: cancel-refund fabricated-ack + clean twin; javaagent pins + Jaeger PF from the
  traced-capture runbook; exactly-one-trace rule; per-run marker SALT — TT discipline is
  marker-salt-only; 800 ms journey pacing); (b) freeze the M-yield clustering convention +
  seed list + the ONE pinned MIST commit for all legs; (c) author the FULL-GRAPH revival +
  teardown script FILES (shell, in debug/ — NOT MIST tool code) per runbook §2.6; (d)
  write the pre-registration freeze §6 row (incl. the item-1 re-scope + the defer notes).
- **Phase 1 (tenancy close-out):** snapshot + scale OTel+TeaStore to 0, teardown-verified;
  RAM checkpoint; disk prune.
- **Phase 2 (the TT window; legs in order, teardown-verify gates between):**
  (i) FULL-graph revival per §2.6 (helm infra, nacos both-members + **doubleWrite PUT
  after EVERY nacos restart**, image pins, ts-gateway-service up, cancel-path services
  verified 1/1, standing background PF per sub-leg); probe-first health gates.
  (ii) Leg 1 = the traced MIST live-run (§1-1): javaagents + Jaeger up → fork set-image →
  N≥5/leg both modes → trace exports → fork torn down + verified.
  (iii) Leg 2 = M-yield TT (§1-2) on the UNMODIFIED graph, 1 h × 10 seeds, wave-runner.
  (iv) Leg 3 = E5 (§1-3): duration-check → fork set-image → the OAT batch → fork torn
  down + verified.
- **Phase 3 (close-out):** javaagent instrumentation TORN DOWN (2.5.1 row stays ◐ with the
  runbook banked — explicit fate per C-10); TT snapshot + scale per the next-need decision;
  `RESULT-tt-omnibus.md` + freeze §6 EXECUTED row (incl. the claim-language correction) +
  checklist folds (Step 4 ◐, 2.5.4, footer refresh) + FILE_INDEX/memory; post-hoc 3-cold
  review (DoD gate).

## §4 Hard safety rails (standing + the review adds)

Never GET /rest/generatedb (human/script rail; the M-yield generator is additionally
STRUCTURALLY incapable of it — the E1 spec declares no destructive path and the generator
cannot reach undeclared paths, C-6); never build images while a graph is deployed (all fork
changes = set-image); 26 GB WSL — the TT window runs ALONE; **WSL flap-cycle (3bb8209): if
WSL goes unresponsive/flaps, run the runbook's `wsl --shutdown` cycle, then re-do the nacos
doubleWrite PUT + re-create PFs before ANY leg resumes**; kafka pod untouched (rdkafka
wedge — moot with OTel at 0, kept for symmetry); TT admin writes unique-keyed → per-run
marker salt; 800 ms journey pacing (gateway 429s); PFs die per reboot → each sub-leg
re-establishes its own standing PF; disk prune per wave; cluster ops via script FILES only.
**MIST tool-code gate (verbatim rule):** all repo changes on `main_track`; MIST tool code
is scoped-open ONLY for the sanctioned categories with S3 precedent (SUT runners /
orchestration / read-only accessors); ANYTHING beyond — especially oracle-semantics or
verdict-path changes — requires ASKING THE USER FIRST. Shell runbook scripts under
`debug/` are not MIST tool code.

## §5 Per-leg budgets (the >1.5× stop rule binds against THESE)

| leg | baseline |
|---|---|
| Phase 1 close-out | ≤0.5 d |
| Phase 2(i) revival + health | ≤0.5 d |
| Phase 2(ii) traced live-run | ≤1 d (≤30 min/run × ≥10 runs + exports) |
| Phase 2(iii) M-yield TT | 10 h driven + ≤0.5 d clustering |
| Phase 2(iv) E5 | ≤4 d incl. the duration-measurement gate |
| Phase 3 close-out + RESULT | ≤1 d |

Honest calendar ≈ 1.5-2.5 wk end-to-end (was mis-framed "one window" in rev 1).

## §6 DoD + stop rules

1. Leg 1: per-mode live verdicts + trace exports recorded for the pair; the observe-mode
   trace-tier outcome reported EITHER WAY (fires or abstains — no tuning, no re-runs to
   flip it); cells written per §1-1 WITHOUT touching existing labels; the claim-language
   correction drafted for the RESULT.
2. Leg 2: 10 seeds complete at the pinned budget; clusters + representatives + audit
   sample + filings recorded; NO yield statistic claimed.
3. Leg 3: the pinned axes × 5 seeds complete; per-axis deltas reported.
4. RESULT + freeze EXECUTED row + checklist folds + 3-cold review; FILE_INDEX/memory.
- **Stop rules:** any MIST change beyond the §4 sanctioned categories ⇒ STOP+ASK; wedged
  infra beyond the runbook's listed recoveries ⇒ STOP+surface; per-leg budget >1.5×
  baseline ⇒ pause+disclose; M-yield flags needing new fault injection to adjudicate ⇒
  record-as-observed only; any evidence the revival corrupted existing capture provenance
  (it cannot — captures are committed files — but verify nothing rewrites them) ⇒ STOP.

## §7 Confirmation pass (the execution gate)

3 cold re-reads of THIS rev 2 (fresh reviewers or the same axes re-run cold); execution
starts ONLY on unanimous confirm. Their brief: verify the 11 blocking dispositions landed
as claimed (each maps to a §1/§3/§4/§5 edit), and that no new over-claim was introduced by
the re-scope.
