# Negative-Test Capability — Phase Plan

## Context

Run 22 (2026-05-28 00:05) closed the original 7→10 fault-detection gap that
motivated this investigation. The fix path was:

1. Restore failure capture in parallel test execution (commit `72521685`) —
   the `enableCapture` + per-task-listener race that commit `e3d7e4b6` had
   sidestepped, leaving `Enhanceable Failures: 0` and every TestCaseEnhancer
   run silently skipped.
2. Once enhancer ran, the dedup pipeline from `967e5a2e` / `a5353498` worked
   exactly as designed (7,407 enhanceable → 2,847 unique LLM calls → 4,560
   saved). The LLM-enhanced parameters were valid enough that Sniper now
   actually wounds only its target parameter, triggering the three
   NAME-class faults that Run 21.b missed (CONTACTS_NAME, STATION_NAME,
   STATION_NAME_LENGTH).

That validates the existing TrainTicket benchmark at 10/10. It does NOT
validate the tool's bug-finding capability in general. Two concerns remain
for an A-conference submission:

1. **Credit gap.** Run 21.b's
   [Agent A audit](../../) found 7,481 of 14,989 tests (≈49%) fired at least
   one trace-shape oracle violation, but the FaultDetectionTracker only
   recognized 7 fault names. The other ~6,700 oracle hits are real
   bug-detection signal the tool produces but never reports.
2. **Generalization gap.** Fault-name matching only works on a SUT that
   has been instrumented with `{"data":{"injected":true,"faultName":"X"}}`
   responses. On any other SUT, MIST has zero way to credit a real bug it
   finds.

The three phases below address these in order of cost-to-impact:

| Phase | What | Effort | Impact |
|---|---|---|---|
| 0 | Surface oracle-only violations in the fault detection report | ~1 day | Reveals tool's true bug-finding power on the existing benchmark; no algorithm changes |
| 1 | Two-phase positive-first / negative-second flow with SUT-verified pool entries | 3-4 days | Removes the Sniper-pollution failure mode without depending on the LLM enhancer; helps reproducibility |
| 2 | Trace leaf-error-span attribution (Jha CLOUD'22 algorithm) | 5-7 days | Makes MIST's negative-test oracle work on any SUT with Jaeger tracing — no fault injection required |

Each phase has its own design doc in this directory:

- [`phase_0_oracle_credit.md`](phase_0_oracle_credit.md)
- [`phase_1_two_phase_flow.md`](phase_1_two_phase_flow.md)
- [`phase_2_trace_attribution.md`](phase_2_trace_attribution.md)

## Ground-truth data driving the phases

| Source | What it tells us |
|---|---|
| [`runs/run22-fault-detection-10of10.txt`](runs/run22-fault-detection-10of10.txt) | Run 22 final report: 10/10 with 2,439 total detection events across 15,036 tests (2.4 MB) |
| [`runs/run21b-fault-detection-7of10.txt`](runs/run21b-fault-detection-7of10.txt) | Run 21.b final: 7/10 (pre-listener-fix baseline, ~2.1 MB) |
| `.mist/enhancement-cache.json` (1.8 MB, Run 22) | Run-22 EnhancementCache contents — same prompts on Run 23+ now hit cache |
| `.mist/llm-validation-cache.json` (1.7 MB, Run 22) | Run-22 LLM Validation cache; same response fingerprints free on rerun |
| Agent A audit (oracle catch-rate, in-session transcript) | 7,094 SPAN_TREE_SHAPE + 3,735 STATUS_PROPAGATION violations during Run 21.b; 6,735 of these have no matching fault name |
| Agent B SOTA review (literature, in-session transcript) | Jaeger leaf-error attribution (Jha CLOUD'22) + AGORA+ invariant mining (ISSTA'23) recommended as multi-tier oracle |
| Agent C Sniper/Pool audit (in-session transcript) | `contactsName` registry mapping `successRate: 0.0`; ~25-30% of pool entries stale; Sniper code at `MistGenerator:1050-1074` doesn't validate non-target pool values |
| Agent D flow restructure plan (in-session transcript) | Two-phase minimal hack 3-4 days; 7 risks (singleton state, generate() non-idempotence, pool emptiness …) |
