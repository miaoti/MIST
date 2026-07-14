# Wave TT-OMNIBUS — the one-revival-window bundle: owed 2.5/E2 traced run + M-yield (Step 4) + E5 ablations — rev 1 (DRAFT, pre-review)

**Date:** 2026-07-14 · Owner: main_track · Status: **rev 1 — awaiting 3-cold review (goal-mode
gate: ALL reviewers accept before execution).** User direction (AskUserQuestion 2026-07-14):
**"TT 复活综合波"** — one TT-revival window serving every remaining TT-dependent experiment.
Grounding: `remaining-experiments-map.md` (2026-07-14).

## §0 Why one window

TT revival is the shared expensive dependency of the three biggest remaining experiments:
the OWED 2.5/E2 traced MIST discrimination run (the standing headline-claim hole), M-yield's
spec-rich TT leg (Step 4), and E5 ablations (Step 7, "TT ×5 seeds"). Reviving once and
banking all three amortizes the cost the user deferred at the R1d decision point. The
TeaStore/OTel M-yield legs run BEFORE the swap (those tenants are up NOW) so nothing is
re-revived later.

## §1 Scope (what this wave BANKS)

1. **Owed 2.5/E2 traced MIST discrimination run** (checklist L87-93: "mist_trace_shape =
   Branch-B traced-but-not-run → 2.5/E2"): live traced TT (OTel javaagents on the target
   write paths per the traced-capture-wave runbook + Jaeger), the MIST-trainticket
   fabricated-ack fork on the cancel-refund site, MIST-the-tool run in BOTH oracle modes
   with `jaeger.base.url` SET — so the trace-gated defect tier (`OBSERVED_COMPLETE_ABSENT`,
   `DataIntegrityRuntime` L736-773) is REACHABLE for the first time. Cells measured:
   `mist_trace_shape_oracle` for the traced pair + the DISCRIMINATION datum (MIST read-back
   + trace-shape verdicts vs the frozen comparator arms' visibility on the same runs).
   N≥5 per leg, control-first; ground truth = direct DB/API reads, never MIST.
2. **M-yield (Step 4, checklist L251-253):** MIST pipeline, LLM-off (disclosed), budget
   pinned 1 h × 10 seeds spec-rich (TT, TeaStore, SS) / 1 h × 3 seeds thin (Bookinfo,
   Boutique, OTel-Demo) per `c2c3-execution-plan.md` §3.2. THIS WAVE runs: TeaStore (rich)
   + OTel (thin) PRE-swap, TT (rich) IN-window. **SS + Bookinfo + Boutique legs = a named
   FOLLOW-UP light window** (disclosed split — those tenants are down and don't justify
   blocking the TT window; the split only delays, never changes, the pinned budgets).
   Event→case clustering frozen PRE-run: equivalence class = endpoint × fault-signature ×
   SUT; 1 representative/cluster + 10% random audit sample; yield = genuine/(genuine+benign);
   upstream filings for genuine finds during execution (§3.2 review A M3).
3. **E5 ablations (Step 7, "one-SUT-pair scope, TT ×5 seeds, disclosed"):** ablation AXES
   frozen at Phase 0 from MIST's actual config surface (candidate axes, to be pinned:
   oracle_mode paired↔observe; re-probe on/off; value-delta on/off; quiescence-gate
   settings; trace-gate on/off via jaeger.base.url) — 5 seeds × axes on the TT pair
   (fabricated-ack + clean twin), wave-runner batched inside the 3–4 d checklist budget.
4. **Trace-coverage table row refresh** (2.5.4) if the re-instrumented deploy differs from
   the traced-capture wave's measured row.

## §2 NOT in scope (disclosed)

kafkaQueueProblems S1 (stays deferred); E1 two-tier grid (Step 3b, ~160 h — separate);
contract-invariant arm RUN (needs its execution-model spike — separate no-tenant item);
TraceAnomaly arm; the rater SEAL + everything IRB/user-gated; S3-BENIGN-01 re-cut (seal-time);
the paper draft (user-gated); any corpus label/cell changes outside the newly-banked
2.5/E2 + M-yield rows.

## §3 Phases

- **Phase 0 (no-tenant prep; blocks everything):** (a) per-SUT MIST runnability inventory —
  spec path (E1 specs now exist for all 6), auth glue, oracle bindings, LLM-off config,
  seed list; (b) freeze the M-yield clustering script + seed budgets; (c) freeze E5 axes;
  (d) freeze the 2.5/E2 run protocol (sites: cancel-refund fabricated-ack + clean twin;
  javaagent pins + Jaeger PF from the traced-capture runbook; exactly-one-trace rule;
  identity ledger + marker salts + 800 ms pacing); (e) pre-register the freeze §6 row.
- **Phase 1 (PRE-swap, OTel+TeaStore up as-is):** M-yield TeaStore 1 h × 10 + OTel 1 h × 3
  (≈13 h driven, wave-runner overnight); cluster + adjudication-queue the flags; then
  tenancy close-out (snapshot + scale to 0, teardown-verified).
- **Phase 2 (TT window):** revive TT via the PROVEN S3 runner `revive` path
  (`b4/runners/s3/trainticket.sh`; runbook §2.6: helm infra, nacos both-members +
  doubleWrite PUT after every nacos restart, image pins, ts-gateway-service up, PF 8080;
  probe-first health gates). Then in order: (i) the 2.5/E2 traced run (fork + javaagents +
  Jaeger; MIST live both modes; N≥5/leg control-first); (ii) M-yield TT 1 h × 10 seeds
  (upstream TT, fork torn down first — M-yield is generation-driven on the UNMODIFIED SUT;
  teardown-verified between (i) and (ii)); (iii) E5 TT ×5 seeds over the frozen axes
  (fork re-applied for the pair — the ablation target is the S1 pair, disclosed).
- **Phase 3 (close-out):** TT snapshot + scale per the next-need decision; RESULT-of-record
  (`RESULT-tt-omnibus.md`) + freeze §6 EXECUTED row + FILE_INDEX/memory; post-hoc 3-cold
  review (DoD gate).

## §4 Hard safety rails (standing, verbatim-carried)

Never GET /rest/generatedb (TeaStore wipe — Phase 1 co-tenancy!); never build images while a
graph is deployed; 26 GB WSL ceiling — TT window runs ALONE (Phase 1 tenants closed first);
kafka pod untouched (rdkafka wedge); TT admin writes unique-keyed → per-run marker salt;
800 ms journey pacing (gateway 429s); nacos doubleWrite PUT after EVERY nacos restart; PFs
die per reboot; disk prune per wave; cluster ops via script FILES only (WSL quoting);
teardown-verify gates between every same-tenant leg; MIST tool-code gate = scoped-open ONLY
for sanctioned runner/orchestration additions (no oracle-semantics changes — any needed ⇒
STOP+disclose).

## §5 DoD + stop rules

1. 2.5/E2: the traced pair measured with per-mode MIST verdicts + trace evidence exported;
   the discrimination cells written into the corpus/freeze WITHOUT touching existing labels;
   if the trace-gated tier does NOT fire on the fabricated-ack leg → that IS the result,
   reported honestly (STOP+disclose, no tuning).
2. M-yield: the three in-scope legs complete at pinned budgets; clusters + representatives +
   audit sample recorded; yield stats per §3.1; genuine finds filed upstream; the SS/BI/
   Boutique follow-up window named-and-scheduled, not silently dropped.
3. E5: frozen axes × 5 seeds complete; per-axis deltas reported (no post-hoc axis edits).
4. RESULT + freeze row + 3-cold review; FILE_INDEX/memory synced.
- **Stop rules:** any oracle-semantics code change ⇒ STOP; any wedged-infra state beyond the
  runbook's listed recoveries ⇒ STOP + surface; M-yield flags that would need new fault
  injection to adjudicate ⇒ record as-is (M-yield adjudicates OBSERVED behavior only);
  budget overrun >1.5× on any leg ⇒ pause + disclose before continuing.

## §6 Review asks (for the 3 cold reviewers)

(a) Is the Phase-1-before-swap M-yield split sound (budget fairness vs the pinned §3.2)?
(b) Is running M-yield TT on the UNMODIFIED upstream (fork torn down) the right reading of
"generation-driven yield" vs the E5 pair scope? (c) Are the E5 candidate axes the right
frozen set, and is 5 seeds × axes feasible inside 3–4 d? (d) Does the 2.5/E2 protocol
actually discharge the OWED discrimination claim as pre-registered (or does it need more
sites than cancel-refund)? (e) Anything in the revival/teardown sequencing that risks the
corpus's existing captures?
