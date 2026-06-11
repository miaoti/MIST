# Adaptive strategy phase 2 — Layer 4 live probe decision

**Decision:** **defer** the live-probe layer (`IdempotencyProbe`) indefinitely.
Phase-1 ships without it. Revisit only when a concrete use case appears that
Layer 2 (OpenAPI hints) cannot cover.

## What Layer 4 would have done

Per `docs/adaptive-strategy-research.md` §5 step 3, `IdempotencyProbe` was a
thin client that, at MistGenerator startup, sent two identical positive
requests at each `POST`/`PATCH` endpoint and compared responses. Identical
→ infer idempotent → upgrade the policy from `DedupMode.OFF` (Layer 1
default) to `DedupMode.PAYLOAD`. Different → keep `OFF`. 5xx / timeout →
abstain.

## Why we don't need it now

| Concern | Phase-1 status |
|---|---|
| Trainticket benchmark has idempotent POSTs that would benefit | **No.** Every documented POST is a mutation (refresh order, create booking, create station, create price rate). All correctly want `DedupMode.OFF`. |
| Operators need to override the RFC default for specific endpoints | **Covered by Layer 2.** `x-mist-dedup-mode: payload` and `x-mist-stateful: false` in the OpenAPI spec give explicit per-endpoint control without runtime cost. |
| Layer 1 (method default) might be wrong | **No evidence.** Run 12 hit 10/10 with Layer 1 only. Run 13's 5/10 was diagnosed as SUT-environmental (admintravel returning HTTP 500), not a dedup-policy mismatch. |
| We need defence-in-depth against bad policy decisions | **Covered by SutHealthCheck + adaptive=off default.** Preflight surfaces SUT issues at startup; `mst.adaptive.enabled=false` is the byte-identical escape hatch. |

## What it would have cost

- 2 HTTP requests per `POST`/`PATCH` endpoint at MistGenerator startup.
  Trainticket has ≈8 such endpoints → 16 extra calls. At 1–2s/call that's
  15–30s added to a run that already takes 5h, so the absolute cost is
  small — but it adds load to the SUT and couples generator startup to
  runtime auth + network state, which is exactly the failure mode
  `SutHealthCheck` was added to make *visible*, not to *increase*.
- ≈200 LOC across: `IdempotencyProbe.java`, the wiring in
  `EndpointPolicyResolver` (a new layer in the cascade), mock-based tests,
  and a config key (`mst.adaptive.probe.enabled`). The complexity carries
  permanent maintenance cost.
- Abstention logic: probe failures must fall through cleanly to Layer 1,
  with care taken not to falsely conclude "idempotent" from two cached
  responses or two errors of the same shape. The research doc names
  `IdempotencyProbeTest#networkErrorAbstains` for that case; the test
  matrix grows with the corner cases (3xx redirect, idempotent failure,
  partial state-change visible only in a third probe, etc.).

## When to revisit

Concrete triggers — any one is sufficient:

1. A target SUT lands in the benchmark where a `POST` is actually
   idempotent and the OpenAPI spec is owned by a team unwilling to add
   `x-mist-stateful: false`. (Layer 2 fails by social, not technical,
   reasons.)
2. Layer 1 produces a measurable false-OFF rate (POSTs that *are*
   idempotent get `OFF`, blowing up the variant budget without finding
   more faults). The bandit metrics already track per-endpoint
   fault-yield-per-variant; if that ratio collapses for specific POSTs
   under `adaptive=true`, probe them.
3. A user explicitly requests it.

## What stays in the design doc anyway

`docs/adaptive-strategy-research.md` retains the Layer 4 design (§4.2,
§5 step 3, §6 tests). When the trigger fires, the work is:

- Implement `IdempotencyProbe.java` per §5 step 3.
- Wire it as the *last* layer in `EndpointPolicyResolver.resolve(...)` so
  Layer 1/2 still produce the same answer when probe abstains.
- Add `mst.adaptive.probe.enabled` (default `false`) and the four tests
  named in §6.
- Re-run the byte-identical regression test
  (`MistGeneratorAdaptiveTest`) to confirm `adaptive=false` is still
  unchanged.

Estimated effort: half a day of code + one full benchmark run for
validation. Not a research-level question; just deferred until evidence
demands it.
