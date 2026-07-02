# Gate-1 validation RESULT (2026-07-02)

Records the outcome of the automated Gate-1 pairing session (plan `TOOL-EXECUTION-PLAN.md` §4).
Companion to `gate1-infra-incident.md` (the memory-incident + recovery log) and
`gate1-smoke-result.md` (the earlier manual adminroute smoke).

## Verdict (plan §4): INCONCLUSIVE — infra-blocked at the pairing stage. NOT a PASS, NOT a mechanism-FAIL.

The automated pairing run reached and **executed** the pairing stage live but **aborted before writing
any verdict/FP report**, defeated by host-memory exhaustion. It therefore did **not** reach the point of
measuring §4's PASS conditions (fires-on-constructed-case + ≤5% non-timeout-gated sync FP). Because no
unsound FIRE was ever produced, it is **not** a §4 mechanism-FAIL either — it is an infrastructure abort
with **no machine-readable result**. The core mechanism remains validated **only** by the manual G0 evidence
(below), exactly as before this session.

## What actually executed (the run got far — this is real evidence the pipeline works)
Run #2 (the counted run, after the run-#1 recovery in `gate1-infra-incident.md`):
1. Completed the full **two-phase generation** (capture → enhance → variant/dedup, ~100 scenarios; fingerprint
   pool drained normally).
2. Loaded the **data-integrity oracle** with 2 target triples (adminroute-create, adminbasic-contacts-create).
3. Entered **pairing execution** for the adminroute triple (`S107`, 100 variant methods): ran the **control
   run**, then **injected** the `-Dmist.fault.lostwrite.enabled` flag on `ts-admin-route-service` via
   JAVA_TOOL_OPTIONS APPEND + rollout (**the inject rollout succeeded**), then ran the **batched fault run**
   against the faulted SUT.

So the live pipeline — generation → control → SUT-flag inject via rollout → fault run — **executed
end-to-end**. What it did **not** do is complete cleanup + verdict + FP probe + report.

## Why there is no report (two compounding memory-pressure failures)
1. **Isolation degraded to pass-through for the entire adminroute fault run.** Every fault-run method logged:
   `DataIntegrity[fault][adminroute-create]: beforeWrite failed (IllegalStateException: station catalogue has
   fewer than 2 stations; cannot build a fresh pair); passing body through`. TrainTicket normally has dozens of
   stations, and `ts-station-service` answered healthily earlier this session (200 @ 0.02–0.23 s). The "<2
   stations" appeared **only at peak memory pressure** (swap ~7.5 GiB), consistent with the intermittent **503s**
   the memory-starved SUT was returning — i.e. the station-catalogue query degraded, so the **station-pair
   isolation strategy safely fell back to pass-through** (it warned, did not crash — correct fail-safe). Net: the
   fault-run writes were **not soundly isolated**, so even had the run finished, the adminroute numbers from THIS
   run would be untrustworthy. (Robustness note for the paper: station-pair isolation depends on a healthy
   catalogue endpoint; confirm on a non-pressured box.)
2. **The node wedged during the fault-flag CLEAR.** In the `finally` clear-all, `kubectl rollout status
   deployment/ts-admin-route-service` (and `…admin-basic-info-service`) **timed out** — the crash tail shows
   `net/http: TLS handshake timeout` / `client connection lost`: the **apiserver went down mid-clear** (second
   memory wedge). The tool's **F2 fail-safe fired correctly**: it refused to silently proceed and threw
   `FaultInjectionException: fault flag may still be active on [both] — verify/clear manually`, which **preempted
   `writeReport()`**. Exit code **1**, no `data-integrity-reports/*.json`.

## Tool behaved correctly under adversity (positive robustness evidence)
- **F2 clear-failure fail-safe worked as designed** (`PairedFaultExecutor.execute` → loud throw when a clear
  cannot be confirmed) — it did not emit a possibly-unsound verdict and it surfaced the left-faulted SUT rather
  than hiding it. This is the safety behavior the build was reviewed for.
- **Station-pair isolation safe-fallback worked** — degraded catalogue → warn + pass-through, no crash.
- The **inject** path (APPEND `-D` flag + rollout) worked live on the traced topology.

## Mechanism status (unchanged by this run — still validated ONLY manually)
The differential oracle's core signal (acknowledged-but-lost write caught only by black-box read-back) is
live-validated by the **manual G0** evidence, not by this automated run:
- **adminroute** LOST_WRITE control-vs-fault: `gate1-smoke-result.md` (HTTP 200/status:1, getAllRoutes unchanged;
  status/schema/body oracles pass, only read-back catches it).
- **adminbasic/contacts** LOST_WRITE + read-back: manual G0 this session (status:1 "create contacts success",
  data:null, membership=0) — `sut-fault-injection-capability.md` §9.
The **automated FP measurement (B2.4) and the pairing FIRE/NO_FIRE verdict remain UNMEASURED** on live TrainTicket.

## Root cause & why no third relaunch
The traced TrainTicket topology (~19–20 GiB) + a MIST pairing run (JVM heap spikes to load/execute 100 test
classes, plus rollout churn for inject/clear) sum to ~24 GiB on a **25 GiB** WSL box → swap saturates → the
in-node runtime/apiserver wedges. This wedged the box **twice** (run #1 in generation, run #2 at the clear). Per
the user's explicit standing instruction ("if it OOMs again, write the honest verdict rather than looping"),
**no third relaunch** was attempted — the box demonstrably cannot hold this workload. A mid-pairing emergency
scale-down of orthogonal services was attempted and **correctly blocked by the safety classifier** as out of
scope (`gate1-infra-incident.md`).

## Disclosures (carry into any writeup)
- **Run #1 discarded** (wedged in Phase-B generation, 1046 connection-refused); **run #2 is the counted run**.
- **`prometheus` + `grafana` scaled to 0** for headroom (B2 needs only jaeger + otelcol); trace path verified intact.
- **Peripheral-service caveat:** under memory pressure many non-target endpoints returned 5 s timeouts / 404 /
  503; MIST itself WARNs detection counts may drop. The two **target** services stayed healthy until the final
  pressure spike — but that spike is exactly what broke the adminroute isolation + the clear.
- **adminroute (`S107`) = the load-bearing sync leg** that was paired here. The **contacts leg was body-less /
  unhooked** in the generated Phase-B step (as before) — it has manual-G0 evidence and remains a follow-up.
- **SUT left faulted + node wedged** at run end (see Cleanup below).

## Follow-up to actually obtain the Gate-1 numbers (not blocked by the tool — blocked by the box)
Re-run the automated pairing on a box where the topology + run fit with headroom:
- Bump WSL to **≥32 GiB** (`.wslconfig`) — needs `wsl --shutdown` (was user-gated this session), **or** run on a
  larger host / real cluster.
- Then: healthy `ts-station-service` → station-pair isolation succeeds (no pass-through), and enough memory →
  the inject/clear rollouts complete → `PairedFaultExecutor` writes the FP + FIRE/NO_FIRE report → evaluate §4
  PASS (fires + ≤5% non-timeout-gated sync FP).
The build itself is **complete + cold-reviewed** (all P1–B2.4 tasks) and needs no code change for the retry.

## Cleanup performed at session end
See the trailer appended below once cleanup ran (recover node → verify/clear the two fault flags → `minikube stop`).
