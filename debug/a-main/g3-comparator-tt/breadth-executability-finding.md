# Executable breadth run — constraint finding + feasible path (2026-07-04)

**Status:** finding recorded; direction chosen (control-only bindability); pending build + review.
Supersedes the "author 69 bindings + run via ComparatorRunner = mechanical" assumption in
`breadth-bindings-authoring-guide.md` (that guide's SCHEMA + disposition→clause mapping stay valid;
only the RUNNER/execution model changes).

## The constraint

`ComparatorRunner.runEndpoint` (`mist-cli/.../comparator/ComparatorRunner.java:116-120`) **requires a
per-endpoint `fault_flag`** and always runs the full calibration cycle: clear flag → CONTROL write +
evaluate (must PASS) → INJECT flag → FAULT write + evaluate → clear. It is a *calibration* runner
(prove the contract CATCHES a real injected lost-write), not a bindability surveyor.

A full inject-based executable breadth over the 69 BIND endpoints would therefore need **~69 real
lost-write fault flags across ~15 TrainTicket services** (each a source modification in the
train-ticket-injection fork, like `mist.fault.lostwrite.enabled` on ts-admin-basic-info-service). That
is **not mechanical and largely infeasible** — it is a fork-engineering project, not a YAML-authoring
task. The head-to-head + G2 calibration deliberately used only the 2-3 endpoints that HAVE such flags.

## What the Rider-2 fraction actually needs to demonstrate

The survey's 69/80 is a **BINDABILITY** claim: can each frozen state clause be EVALUATED on live TT
(path resolves from submitted fields + read-back is the right shape) — NOT whether it catches a fault.
Bindability is a CONTROL-LEG property: run a benign write, evaluate the frozen STATE_GET clause, and
observe BINDS (the clause evaluated and PASSed — submitted state found) vs NOT_CHECKABLE (clause marked
NC) vs ERROR (path won't resolve / read-back wrong shape = structurally unbindable). **No fault
injection is required to demonstrate bindability.**

The `ContractEvaluator.evaluate(endpoint, runLabel, body, resp, client)` already returns exactly these
per-check outcomes (PASS / FAIL / NOT_CHECKABLE) for the control leg. So an executable bindability
demonstration is a thin **control-only** driver over the reviewed evaluator — no injector, no fork
flags.

## Options

1. **Control-only bindability run (CHOSEN).** A small `BindabilityRunner` (reuses `ContractEvaluator`,
   `AssertionBindings`, the SutClient; NO `FaultInjector`): for each endpoint, POST a control write,
   evaluate the frozen clauses, classify the STATE clause BINDS / NOT_CHECKABLE / ERROR, aggregate the
   fraction. Converts the survey's ANALYTICAL 69/80 into an EMPIRICAL one on live TT — directly
   answering a PC reviewer's "did you actually run it?". Cost: ~small runner + test + ≥3-review; 80
   bindings + triples (NO fault_flags needed); env swap (TT up); the run. Value: upgrades the headline
   external-validity number from on-paper to executable. Triples need NO fault_flag (bindability ≠
   calibration), so the `runEndpoint` null-check is bypassed by using the dedicated runner.
2. **Partial inject-based breadth (bonus, later).** For the strata whose services ALREADY carry a
   lost-write flag (adminbasic family via `mist.fault.lostwrite.enabled` — survey #56-70, if the flag
   is service-wide), the existing `ComparatorRunner` can show actual CATCH. Stronger per-endpoint but
   limited to flag-bearing services; additive on top of option 1, not a replacement.
3. **Representative-first (execution tactic for option 1).** Author + run one exemplar per disposition
   class (clean-membership BIND, per-entity-echo BIND, list-absence BIND-a, BINDS-P, + each of the 6 NC
   categories) FIRST to validate the runner + classing empirically, then scale to the full 80. Keeps
   the first reviewable increment small; the accepted survey carries the full census meanwhile.

## Decision (autonomous, per delegated scope — user can redirect)

Pursue **option 1 via option 3's tactic**: build the small control-only `BindabilityRunner`, validate
it on a representative set spanning all disposition classes (empirically proving the executable
machinery + the classing), then scale to the full 80 for the empirical fraction. Option 2 is a
later bonus for the adminbasic stratum if its flag is service-wide (to be checked in the fork).

Rationale: the full inject-breadth (strongest form) is infeasible; the on-paper survey is already
accepted; the highest-ROI increment is making the HEADLINE Rider-2 fraction EMPIRICAL, which option 1
does without a fork-engineering project. This is a tool-code effort (new small runner) → test +
≥3-cold-review before any claim, per the standing rules.

## Not-wasted note
The bindings + triples authored for option 1 are ALSO the inputs for option 2 (they only differ by the
presence of a `fault_flag`), so authoring is not wasted regardless of how far the inject-based bonus goes.
