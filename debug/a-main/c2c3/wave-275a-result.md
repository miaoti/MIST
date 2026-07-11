# Wave 2.75-A — RESULT OF RECORD (MIST read-back ENABLED + RUN on two SUTs)

**Date:** 2026-07-10
**Plan:** `wave-275a-plan.md` rev 2.1 (unanimous 3-cold-accept + confirmation pass)
**Freeze:** `c2-freeze.md` §6 — two dated "WAVE 2.75-A EXECUTED" rows (TeaStore, OTel-Demo)
**Per-leg RESULTs:** `benchmark/b4/enable/RESULT-teastore-2.75a.md`, `RESULT-oteldemo-2.75a.md`
**This is the first wave in which MIST's read-back oracle produced verdict-valued cells for BENCHMARK-CORPUS cases** (flipping `mist_bindable` + `mist_readback_oracle` from measured runs), and the first binding of the durable-store read-back to a JSON-collection modality (TeaStore) and an async-SQL modality (OTel). (MIST's read-back oracle itself ran earlier, in the G3 head-to-heads via `g3.ShippingReadbackHttp` — this wave is not the first oracle run *ever*; it is the first that flips corpus cells. The MIST-tool-code gate for this benchmark track was opened by the user 2026-07-10.)

## The wave claim (what this buys the paper)

MIST's differential data-integrity **read-back** oracle was bound to two SUTs' durable
systems-of-record — a JSON order collection (TeaStore) and an async SQL ledger (OTel-Demo) — and run
as paired control/fault head-to-heads. **On both, MIST's read-back FIRED 5/5 probe-pairs on a
genuine acknowledged-but-lost write whose HTTP ack is a clean success-shaped 200, and did NOT fire on
the clean control.** Each verdict was cross-checked against **independent ground truth** (a direct
store read, not MIST's oracle). This converts two cases from the T9 "read-back exists but MIST can't
bind the modality" applicability-boundary into the **MIST recall denominator** with measured,
run-backed cells.

## The two legs

| | TeaStore (`teastore-order-maintenance-masked-001`) | OTel-Demo (`oteldemo-checkout-lost-001`) |
|---|---|---|
| durable store | persistence `/rest/orders` JSON | `accounting.shipping` Postgres (async) |
| transport | `JsonDurableReadback` (full collection, MEMBERSHIP) | `SqlDurableReadback` (`kubectl exec psql`, server-side WHERE) |
| key | `address1` (confirm-form) | `street_address` (request-derived; NOT server `order_id` — C-R2) |
| fault | persistence maintenance flag (stimulus-owned, B-R3) | kafka scale-0 (leg-level single toggle, A-F8/B-F7) |
| **MIST verdict** | **FIRE 5/5** | **FIRE 5/5** |
| ground truth | 5/5 control persisted, 0/5 fault | 5/5 control, 0/5 fault, STILL 0 post-heal (permanent) |
| fault ack | HTTP 200 ORDERCONFIRMED | HTTP 200 @ ~0.02 s (fully async) |
| absence gate | `TIMEOUT_ABSENT` (sync SUT) | `TIMEOUT_ABSENT` (permanent async loss) |
| **framing (C-B1)** | **SOLE-oracle** (trace-uninstrumented) | **PRESENCE-CONCORDANT** (`tracetest_presence=flag` already) |

## Honest framing (the C-B1 discipline, carried from the plan review)

**Neither leg is a discrimination win over a trace comparator.** That claim is reserved for the
TrainTicket fabricated-ack case (a traced SUT whose trace looks clean).

- **TeaStore is a SOLE-oracle datum:** the SUT is trace-uninstrumented (Kieker-only), so no trace
  comparator is even bindable. Every *deployed* ack-side column (status 200, schema unchanged, body
  success-shaped, the `-1` never reaching the client) MISSES the loss; MIST's read-back catches it.
  "Beats trace-only" would be vacuous where no trace oracle exists.
- **OTel-Demo is a PRESENCE-CONCORDANT datum:** the case's `tracetest_presence_oracle` already FLAGs
  (accounting CONSUMER span absent). MIST's read-back *independently confirms* the same loss at the
  durable store, via a different mechanism (durable state vs span topology). Concordance strengthens
  the evidence; it is not a "beats trace" result.

What the wave DOES establish: MIST's read-back oracle binds two very different durable modalities
(sync JSON collection, async SQL ledger), agrees with independently-verified ground truth on
genuinely-acked losses, and does so with the anti-circularity firewall intact (labels/ground-truth
never taken from MIST's own oracle output).

## Soundness guards actually exercised (not just designed)

- **Anti-circularity / soundness of the FIRE.** Two distinct guards, not one:
  (1) *Label independence* — the ground-truth label is SUT-native (the write did/didn't durably
  land), read directly from the store (`b4/enable/ground-truth-{teastore,oteldemo}.txt`), NEVER taken
  from MIST's verdict. This direct read wraps the same store query MIST's transport wraps, so it is a
  store re-read distinct from MIST's transport, not a mechanistically-orthogonal second oracle.
  (2) *The read-mechanism validator is the paired CONTROL leg* — this is what actually forecloses a
  shared-mode read failure faking a FIRE: if the query silently hit the wrong schema/table/db (exit 0,
  empty result), the control leg's marker would ALSO read absent → `control.readbackContainedX=false`
  → the verdict is NOT_EVALUABLE ("control write never appeared — systemic guard failed"), never
  FIRE. The reports show `control.readbackContainedX=true` with the marker echoed, so the read path is
  empirically validated each run. On OTel the fault-leg absence was additionally re-verified permanent
  AFTER a heal canary drained (pending-vs-missing observed live).
- **Key soundness (C-R2):** OTel keys on the request-derived `street_address`, never the
  server-assigned `order_id`; live-verified the marker lands in `accounting.shipping`.
- **Error-vs-absence (A-F5):** `SqlDurableReadback` maps a non-zero psql exit / thrown / quoted
  marker to a non-2xx UNUSABLE read, never to `[]` absence (unit-tested; the absent-marker smoke
  returned status 200 `[]`, the error path status 0).
- **Atomic flip (A-F9):** `mist_bindable` false→true and the verdict-valued `mist_readback_oracle`
  cells changed ONLY atomic with the measured runs + the dated §6 amendments. Audit property holds:
  verdict-valued MIST cells appear only where MIST ran.
- **Async floor (A-F8/B-F7):** OTel read-back timeout 25 s over the measured ~5 s async landing;
  control-first single kafka toggle.
- **Disclosed:** both legs' absence is `TIMEOUT_ABSENT`, not `OBSERVED_COMPLETE_ABSENT` (no
  trace-backed quiescence gate wired for the read-back) — sound here because the sync write (TeaStore)
  and the never-produced async message (OTel) are conclusively absent once the floor elapses.

## Evidence completeness + authoring cost (cold-review disclosures)

- **Per-probe auditability.** The committed `*.report.json` serialize the representative (p0) pair
  in full plus the aggregate `firePairs`/record counts (5/5); p1–p4 are attested by the reviewed
  join counters, not per-record JSON (inherited `PairedFaultExecutor.writeReport` behaviour). The
  per-probe ground truth is committed separately and independently: `b4/enable/ground-truth-*.txt`
  list EVERY landed control marker (OTel 5, TeaStore 9 across its two runs) and confirm ZERO fault
  markers — so all probes are auditable from the repo, via a read distinct from MIST's transport.
- **Authoring cost (anti-drift claim string, C-R4).** MIST is *not* out-of-the-box here: it is an
  extensible core + a per-SUT authored binding. The transports (`SqlDurableReadback`,
  `JsonDurableReadback`, `ProcessCommandRunner`) are write-once and reused across cases; the marginal
  per-SUT binding is the triple YAML (~20 lines) + the live stimulus/harness (~100–250 lines). A
  defensible estimate of that marginal per-SUT authoring, for someone fluent in the framework, is
  **~1–2 h/SUT** (disclosed estimate, not a stopwatch measurement). The per-case
  `config_provenance.mist_authoring.minutes` field stays at its frozen UX-wave pin value and is NOT
  the 2.75-A binding cost; this note is.
- **B-F8 (end-to-end verdict test).** The committed unit tests cover the transports only
  (`SqlDurableReadbackTest` 6, `JsonDurableReadbackTest` 4). The end-to-end probe→evaluate→FIRE/NO_FIRE
  path is validated by the LIVE paired runs (arguably stronger than a mocked unit test), plus the
  ground-truth cross-check — a disclosed substitution for the plan's mocked end-to-end unit test.

## Corpus impact

No case added or removed. Four existing cases (2 positive, 2 clean controls) gain measured,
run-backed MIST read-back cells and enter the MIST recall denominator, leaving the T9 boundary rows.
Counts unchanged (still 9 pos / 11 neg captured + 1 specified from wave-3a).

## End state

- Code committed on `main_track`: transports 655fa0b; TeaStore leg 9eff481; OTel leg ba87306.
- Tenants (at 2.75-A close): OTel-Demo UP + verified healthy (post-recovery); TeaStore UP; TT still 0.
  **SUPERSEDED 2026-07-11:** both OTel-Demo and TeaStore were later scaled to 0 (user-authorized) to
  free WSL RAM for the E2 TrainTicket revival — the 26 GB VM cannot hold all three tenants at once. So
  this "tenants UP" end-state **no longer holds** (they are DOWN now, reversibly). The 2.75-A
  *measurements* are unaffected (captured/committed/archived: the report JSONs + `ground-truth-*.txt`).
  Disclosure of record: `debug/a-main/c2c3/e2-ram-teardown-note.md`.
- Unit tests: 10 green (SqlDurableReadbackTest 6, JsonDurableReadbackTest 4); mist-cli BUILD SUCCESS.

## Not in this wave (deferred, unchanged)

`mist_trace_shape_oracle` stays Branch-B (traced-but-not-run) on both legs — deferred to step-2.5/E2.
The MIST pair-separation (precision) claim stays PRE-REGISTERED until that run. Deferred also:
kafkaQueueProblems S1 candidate, S3 wild-hunt (rater-gated), TT revival for 2.5/E2.
