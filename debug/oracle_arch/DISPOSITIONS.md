# Reviewer Dispositions — Oracle Redesign

Adversarial review (incl. an empirical scan of 836 train-ticket traces) → per-comment
disposition with grep-verified evidence. **Outcome: 0 rejects — the reviewer was right on
every point.** Two comments reshape the thesis (B1+F1) and one is the decisive strategic
finding (C2).

| # | Sev | Comment | Disposition | Evidence |
|---|---|---|---|---|
| A1 | MINOR | "FaultTarget garbage after :728" loose — read at `:732/:754` | **ACCEPT** | dropped after the traverse loop, not `:728`; doc wording fixed. Substantive claim (scattered scalars replace the object) holds. |
| A2 | MINOR | "expectedStatus always 200" omits config path | **ACCEPT (tighten)** | `MistGenerator.java:1454-1484` can read `opCfg.getExpectedResponse()`. Correct claim = "never derived from the injected fault; no 4xx-for-faulty branch" (G2 already says this). |
| A3 | NIT | Timing gated OFF by default | **ACCEPT** | grep-verified `MstConfig.java:408-409` `timing.enabled` default `"false"`. Noted in docs. |
| **B1** | **BLOCKER (framing)** | "silent acceptance undetectable today" is false — emitted test already throws on 2xx | **ACCEPT — reframe thesis** | grep-verified writer `:2102-2105`: `negativeTestPassed = statusCodeIndicatesError \|\| llmDetectedRelatedError; if(!…) throw AssertionError`. So 2xx-for-negative already FAILS the test (coarsely, status-only + unreliable LLM). Novelty re-stated as **trace-backed disambiguation**, not first-detection. |
| B2 | MAJOR | SilentAcceptance precision — legit 2xx-for-"invalid" exists | **ACCEPT** | idempotent/optional-param/syntactic-coercion/sniper-wrong-param. Ship as **WARN** until semantic baits (Move 5) land; gate ERROR on semantic fault types. |
| B3 | MAJOR | HiddenDownstreamFailure threshold — reuse of `isErrorTagged` (≥400) fires on benign 4xx | **ACCEPT** | `LeafErrorSpanFinder.isErrorTagged:88-92` treats http≥400 as error. Design must define a **separate ≥500-only** predicate; explicitly forbid reuse. |
| C1 | (support) | 0% empirical FP on train-ticket | **ACCEPT (cite)** | reviewer scan: 796 2xx-root traces, 0 with descendant error spans. |
| **C2** | **MAJOR (decisive)** | train-ticket has **ZERO positive instances** of hidden-downstream-failure | **ACCEPT — see Bottom Line** | reviewer scan + my own Tier-1 recall (anyErr=41, rootErr=41, errButRootClean=0): every error propagates to an error-tagged root. The "traces are necessary" contribution has **no demonstrable example** on train-ticket. |
| D1 | MINOR | writer still renders literals; "round-trip disappears" half-true | **ACCEPT (tighten)** | `:1368-1369` field→Java-literal emission remains; only the `"Root N"→"RN"` reconstruction goes away. |
| D2 | MAJOR | reject-expected guard must be INSIDE the invariant | **ACCEPT** | mirror `TargetAttributionInvariant` `targetService==null` guard (`:63-64,88-89`); else a stray ERROR outcome would fail healthy positives via the AND-of-ERROR verdict (`TraceShapeVerdict.java:64-73`, consumed positive `:2275-2284`). |
| E1 | (confirm) | additive-flag backward-compat real | **ACCEPT (no change)** | writer consumes live `Collection<TestCase>` (`:107`), `MultiServiceTestCase` not serialized; flags-off delegates to 4-arg `evaluate`. |
| F1 | MAJOR | broad novelty over-reaches (Tracetest, metamorphic, silent-failure-via-tracing exist) | **ACCEPT — narrow it** | defensible wedge = **label-free LEARNED invariants + injected-fault INTENT as per-test spec + cross-service correlation**, for an automated negative-test oracle. Position vs Tracetest (needs human-written span assertions → we're label-free), AGORA+ (single-API response), SynthoDiag (FSE'24 trace fault-localization). |
| F2 | MINOR | AGORA-beating boundary too strong (AGORA+ catches 48% seeded) | **ACCEPT (tighten)** | contribution = cross-service reach + intent-conditioning, not "AGORA blind to silent acceptance." |

## Bottom line (the decisive takeaway — C2 + B1)

The architecture is **technically sound and feasible** (Move 3 is a near-trivial extension of
already-threaded params; 0% empirical FP). The fatal risks are **not implementation** but:

1. **Novelty must narrow** (B1, F1): MIST already flags silent acceptance coarsely (the
   assertion throws on 2xx). The honest, defensible contribution is **label-free,
   intent-conditioned, trace-backed disambiguation** — telling apart *correct rejection* /
   *silent-accept-that-actually-mutated-downstream* / *benign 2xx (input ignored)* /
   *hidden downstream failure* — distinctions a status-code assertion and a single-API
   response oracle (AGORA+) cannot make. The **trace** is what enables the disambiguation.

2. **train-ticket cannot demonstrate the headline** (C2): it fails **loudly** — every error
   propagates a 500 to the root (0 hidden-downstream, 0 silent-mishandle observed; and
   param-level `TARGET_REJECTION` was already 0 in the cert). The interesting trace-only bug
   classes **do not occur** on this SUT. So the architecture's distinctive value is currently
   **un-demonstrable here**, regardless of evaluation scale.

   → To show the contribution you need a SUT that **mishandles silently** (many real APIs do;
   train-ticket is unusually crash-happy), **or** deliberately injected **SUT-side mutants**
   that swallow downstream errors / accept-and-mutate invalid input — controlled instances of
   the exact bug class the oracle targets, used as ground truth. This is an architecture/
   methodology requirement, not an evaluation-scale one.
