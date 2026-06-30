# Genuine-vs-benign labeling rubric (pre-registered) — MIST C2 benchmark

> Pre-registered adjudication guide released WITH the benchmark. It turns "is this a real defect or
> benign degradation?" into checkable predicates, so labels are reproducible and the genuine/benign
> boundary is auditable. Rubric version **0.1.0**. Grounded in the cited evaluation design
> (`../../research/05-evaluation-and-benchmarks.md` §4 and `../../README.md` §4); fault-injection-as-
> ground-truth is accepted practice (RCAEval WWW'25 arXiv:2412.17015; Nezha FSE'23 DOI 10.1145/3611643.3616249).

## A. Scope
Each case carries exactly one `ground_truth.label` ∈ {`positive`, `negative`}. `positive` = a genuine
data-integrity / masked-downstream defect. `negative` = benign (clean, designed degradation, or
eventually-consistent-then-correct). The predicates below decide the label by `fault_class`.

## B. Predicate — `acknowledged_lost_write` / `missing_compensation`
Inputs: the entry response, and the read-back GET response taken **after quiescence** (see §D).

**POSITIVE iff ALL hold:**
1. The entry response acknowledges success (HTTP 2xx and/or the SUT's success status), AND
2. the write target is **required** (the call's stated purpose is to persist/modify state — not an
   optional best-effort side effect), AND
3. after quiescence, the read-back shows the write **absent or inconsistent** with the acknowledged
   request (for `missing_compensation`: a partial-failure left an uncompensated intermediate state).

**NEGATIVE if ANY hold:**
- The read-back **converges to correct** within the quiescence window (eventual consistency in progress,
  not a lost write); or
- the entry did **not** acknowledge success (the write was correctly rejected — non-2xx / error marker); or
- the write target is explicitly **best-effort / optional** by design (documented).

## C. Predicate — `swallowed_downstream_error`
Inputs: the distributed trace for the entry request.

**POSITIVE iff ALL hold** (operationalizes Uber "non-fatal but hidden", DOI 10.1145/3700436):
1. A downstream span **server-errored** (5xx / otel status=ERROR) on a **required** dependency, AND
2. the entry response neither **reflects** the failure (it is still 2xx with a nominal payload) NOR
   **recovers** it (no successful retry, no designed fallback).

**NEGATIVE if ANY hold** (the false-positive traps a naive "any error span under a 2xx" oracle hits):
- The failed dependency is **optional** (designed fallback / graceful degradation — e.g. Istio Bookinfo
  `reviews→ratings`, istio/istio PR #15489); or
- the failure was **recovered** by a successful retry, circuit-breaker default, or cached value; or
- the entry response **does** reflect the failure (it is not actually masked).

## D. Quiescence protocol (required before any read-back diff)
Async writes must not be mistaken for lost writes. Before diffing control vs fault read-backs:
1. Poll the read-back endpoint until the response is **stable across K consecutive polls** (K, interval,
   and a hard timeout are pre-registered per SUT), then diff; OR wait the SUT's documented convergence
   bound, whichever is defined for that SUT.
2. If the read-back is still changing at timeout, label the case `inconclusive` and **exclude** it from
   precision/recall (report the excluded fraction). Never force a label on a non-quiescent case.

## E. Stratum-3 adjudication (wild traffic only)
For MIST-flagged traces on non-seeded workloads where the label is not known by construction:
1. ≥2 independent raters apply §B/§C to a **stratified random sample**.
2. Report **Cohen's κ** inter-rater agreement; a third rater adjudicates disagreements.
3. The rubric version and rater count are recorded per case in `adjudication`.
4. Pre-register the sample size to a target precision CI half-width ≤ 5% before rating.

## F. Honesty rules (must hold for the whole corpus)
- Strata 1–2 labels are **by construction / documented design** — state the construction in
  `ground_truth.rationale`; do not present them as wild developer-confirmed bugs.
- The MIST-oracle columns in `oracle_expectation` are **targets** (the correct verdict), measured against
  MIST's actual runs at Gate 1 — they are NOT assumed correct. Baseline-oracle columns are deterministic
  by construction.
- Wild developer-confirmed counts are **not** claimed for the trace-only classes (structurally
  under-reported; see `../../research/05-evaluation-and-benchmarks.md` §4 and the prior wild-bug probe).
