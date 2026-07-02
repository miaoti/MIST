# Gate-1 validation RESULT — **PASS** (run #3, 2026-07-02)

Records the outcome of the automated Gate-1 pairing session (plan
`TOOL-EXECUTION-PLAN.md` §4). **This supersedes the earlier INCONCLUSIVE entry from
runs #1/#2 (kept in §6 + [gate1-infra-incident.md](gate1-infra-incident.md)).**
Companions: [gate1-preflight-audit.md](gate1-preflight-audit.md) (run-#3 pre-flight
evidence), [gate1-smoke-result.md](gate1-smoke-result.md) (manual G0),
[REVIEW-B1B2-RECONCILIATION.md](../research/REVIEW-B1B2-RECONCILIATION.md) (the
mechanism cold-review whose §2 checklist this result was audited against).
Machine-readable report: committed copy at
[gate1-run3-report.json](gate1-run3-report.json) (canonical:
`logs/data-integrity-reports/pairing_trainticket_gate1_pairing_1782976771915.json`).

## 1. Verdict (plan §4, pre-registered v1 bar): **PASS**

| §4 criterion | Result |
|---|---|
| **Fires on the constructed lost-write** | **YES — FIRE on `adminroute-create`, in the STRONG stratum** (`OBSERVED_COMPLETE_ABSENT`): fault run acked X (HTTP 200, body status 1), X absent from its own read-back across 19 polls (13.4 s incl. the 3 s trace-settle), control's X persisted (OBSERVED_PRESENT in 1 poll / 181 ms). |
| **≤5 % non-timeout-gated sync FP over ≥20 acked benign runs** | **PASS at 0.0** — 0 fires / **2 127 acked benign records** (30 iterations × 71 hooked records − 3 invalid). FP **interval** `[observed-gated/acked, fires/acked]` = **[0.0, 0.0]** (both endpoints zero). Gate histogram: `OBSERVED_PRESENT: 2127, NOT_APPLICABLE: 3` — **zero timeout-gated records**, so the observation gate resolved **100 %** of acked runs. |
| **Async** | Disclaimer path (pre-registered): TrainTicket has no broker-mediated async write path (P3 verdict NEW-INJECTOR-NEEDED); no async-FP claim is made; async soundness deferred to G3. |

**Bar-v2 cross-reading (per [hardening-wave-spec.md](hardening-wave-spec.md) — the
run is NOT re-scored; both readings reported):** gateResolvedFraction = 2127/2127 =
1.0 (≥ 0.5 ✓), timeoutGatedFraction = 0 (≤ 0.3 ✓) → under the draft bar-v2 floors the
run is evaluable and also PASSes at 0.0. **The two readings agree.** The vacuous-PASS
concern (recon R2: numerator structurally zero under a degraded gate) does NOT apply:
the gate was fully functional — exactly what the pre-flight exact-id traceparent
verification predicted.

**Exit code 0; report written by `PairedFaultExecutor.writeReport` — the F2-abort
path of run #2 did not recur.**

## 2. The evidence in detail

**The FIRE (sensitivity).** Triple `adminroute-create`
(`POST /api/v1/adminrouteservice/adminroute` → `ts-admin-route-service` →
`GET .../adminroute` read-back), fault = SUT-side `LOST_WRITE` via
`-Dmist.fault.lostwrite.enabled=true` (JAVA_TOOL_OPTIONS APPEND + rollout-confirmed).
Fault-run isolation key (station-pair, freshened): `startStation=shanghai_hongqiao,
endStation=new_station_name_1022`; `baselineContainedX=false` (isolation held);
read-back stayed a healthy `{"status":1,...}` full collection that simply never
contained X → absence is **observed — not visible on the read-back path — with the
write's own trace complete and stable** (the strong stratum), not a timeout guess.
Control record: fresh pair persisted and visible in 181 ms.

**The FP probe (specificity).** 30 flag-off iterations of the same generated pairing
tests: 2 130 records, 2 127 acked, **0 false positives** (no acked benign write was
ever absent at the 10 s cap). Denominator semantics per the B-6 review note: these
are per-record observations (71 hooked adminroute records per iteration), not 30
independent trials — stated as such.

**The FP-vs-timeout curve (pre-registered cap justification).** With shorter caps the
benign eventual-consistency window WOULD have produced false positives:
`500 ms → 12.98 %`, `1 000 ms → 0.14 %`, `≥2 000 ms → 0.0`. The SUT's benign
convergence window is ~1–2 s; the pre-registered 10 s cap sits far above it — the
curve empirically justifies the cap and quantifies the timeout-sensitivity of naive
read-back oracles (paper figure material).

**Corroborating signal (not part of §4):** the whole-trace-shape oracle concurrently
recorded `missing required edges` on `POST /adminroute` (862 occurrences,
`logs/fault-detection-reports/`) — the faulted service's persist-edge disappearing
from traces during the fault run, consistent with the injected skip-persist.

## 3. Report-audit checklist outcomes (recon §2, applied to the JSON)

1. **Read-back/baseline body audit (R1/C-P1-2):** control + fault + FIRE bodies are
   well-formed `{"status":1,"msg":"Success","data":[…]}` collections — no
   error-shaped or truncated read-backs behind any verdict. The 2 127 observed
   presences over a monotonically growing collection are direct evidence the
   `getAllRoutes` read-back returned complete lists at this scale (A-Finding-1's
   truncation trigger did not bite on TrainTicket).
2. **FP interval + gate histogram + per-triple (R2/B-6):** reported above; per-triple
   == aggregate (only the adminroute triple was exercised); denominator = acked
   records.
3. **pick()-join check (R3):** controlRecordCount=71, faultRecordCount=70 (one
   fault-run variant produced no record — count asymmetry noted). The FIRE's own
   record is self-consistent (its absence is evaluated against its own freshened
   key); per-record verdicts are not in the v1 report — R3fix (hardening wave) adds
   them. No indication of a persisted fault-run sibling masking anything (the
   representative itself FIREd).
4. **pairs[] coverage (C-P1-7):** `adminbasic-contacts-create` = NOT_EVALUABLE with
   record counts 0/0 — the generated scenario never reached the contacts hook (the
   known body-less/unhooked generation gap, same as run #2). Sensitivity evidence =
   **1/1 evaluable constructed case**; the second configured triple was NOT exercised
   by the automated run (its LOST_WRITE remains validated only by manual G0,
   `sut-fault-injection-capability.md` §9).
5. **Wording:** all absence verdicts here mean "observed — not visible on the
   read-back path"; the trace gate certifies request completion + stable trace, not
   storage-level loss (recon R4).
6. **Persisted-fault-writes / activation check:** N/A — the fault-run write was
   absent (the flag demonstrably took effect after the rollout-confirmed inject).

## 4. Scope (what this PASS does and does not establish)

- **Establishes (Gate-1 DoD, EXECUTION G1c):** the B1+B2 mechanism is **sound on the
  SYNC stratum of ONE SUT** — it fires on a constructed acknowledged-but-lost write
  in the high-confidence stratum and produces **zero** false positives over 2 127
  acked benign records, with a fully functional observation gate.
- **Does NOT establish (plan §3.5/§3.6 — soundness only, essentially zero novelty
  evidence):** async soundness (deferred to G3), depth beyond shallow CRUD, breadth
  beyond adminroute (contacts unexercised), any novelty claim vs Cast/Filibuster
  (G2/G3 carry that), or generality beyond TrainTicket (G3's ≥2 SUTs).
- Gate-1 therefore routes: **proceed to G2** (comparator calibration per
  [g2-novelty-comparator-prereg.md](g2-novelty-comparator-prereg.md)) **+ the
  hardening wave** ([hardening-wave-spec.md](hardening-wave-spec.md)) **+ G3 prep**
  ([g3-sut2-triples-prereg.md](g3-sut2-triples-prereg.md)).

## 5. Run conditions & disclosures (run #3)

- **Lean deploy:** ~30 orthogonal services (order/travel/food/payment/… domains) +
  prometheus + grafana scaled to 0 at deploy time; topology ~13–15 GiB in the 26 GiB
  WSL cap; MIST launched with `-Xmx4g`. The **pairing verdict path is unaffected**;
  generation breadth on the trimmed services was reduced (their calls 503'd —
  ~20–30 s each at the gateway, the main slowness driver).
- **LLM condition:** `DEEPSEEK_API_KEY` was not exported by the launcher (runs #2
  and #3 identically), so LLM-assisted input fetching fell back (incl. dead-Ollama
  connection-refused noise in the log). **The pairing/oracle path is LLM-free**
  (deterministic hooks/freshening/read-backs); impact = reduced non-target scenario
  breadth + retry latency.
- **Timeline:** launched 02:19, report 10:09 (−05:00), ~7.8 h wall (generation
  ~5.2 h — dominated by dead-service waits + LLM retries — then pairing + 30-iteration
  probe ~2.6 h). 3 of 2 130 probe records invalid (beforeWrite error / not acked) —
  NOT_APPLICABLE, excluded from the acked denominator.
- **Pre-flight evidence** ([gate1-preflight-audit.md](gate1-preflight-audit.md)):
  exact-id traceparent propagation verified live before the run (predicting the
  strong-stratum result); station catalogue = 87 (isolation could not degrade — and
  did not: zero pass-through warnings this run); both target deployments agent-only
  at baseline.
- **Station-pair note:** the catalogue grew during the run (earlier freshened writes
  create stations); isolation keys drew existing stations and `baselineContainedX`
  stayed false throughout — the strategy worked as designed at this scale.

## 6. Run history (runs #1/#2 — superseded, kept for the record)

Runs #1 (2026-07-01) and #2 (2026-07-02 early) on the FULL traced topology wedged the
26 GiB-capped WSL node (committed peak ~31 GiB on the 31.7 GiB host): run #1 in
Phase-B generation; run #2 during the fault-flag clear — where the **F2 fail-safe
correctly threw** (`fault flag may still be active…`) before `writeReport`, so no
report was produced and the then-verdict was INCONCLUSIVE/infra-blocked. Run #2 also
saw station-pair isolation degrade to pass-through under memory pressure. Root cause
= memory **budget** (the `.wslconfig` 26 GB cap), not machine size. Full incident +
recovery log: [gate1-infra-incident.md](gate1-infra-incident.md). Run #3's fixes:
lean deploy + `-Xmx4g` (no tool-code change). Positive robustness evidence retained
from run #2: the F2 fail-safe and the isolation safe-fallback both behaved exactly as
reviewed. (Hardening-wave item C-P1-3fix will additionally persist the report BEFORE
the F2 throw, making run-#2-style evidence loss impossible.)

## 7. Artifacts

- Report JSON: [gate1-run3-report.json](gate1-run3-report.json) (committed copy);
  canonical under `logs/data-integrity-reports/`.
- Run config (committed with this result): `trainticket-gate1-pairing.properties`,
  `trainticket/test-trace-gate1/` (input trace corpus),
  `trainticket/input-fetch-registry.yaml`, `trainticket/root-api-registry.json`.
- Run log: `~/gate1-logs/pairing-run.log` (WSL, ~1.1 MB+; exit file `…/pairing-run.exit` = 0).
- Generated tests: `mist-cli/src/test/java/trainticket_gate1_pairing/TrainTicketGate1Pairing_1782976771915/`
  (local byproduct, not committed).
- Trace-shape findings: `logs/fault-detection-reports/` (local).

## 8. Cleanup (run #3, post-verdict)

`minikube stop` after this result was committed (node healthy this time — no forced
docker stop needed unless it fails; prometheus/grafana remain at 0 for the next
session). Both target deployments' `JAVA_TOOL_OPTIONS` verified agent-only by the
run's own final clear (rollout-confirmed, exit 0 — no F2 throw).
