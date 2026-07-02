# Hardening-wave cold review C — integration/pipeline risk of e5af35b

**Date:** 2026-07-02. Independent cold reviewer (no shared context), one of three.
Traced the changes through the real pipeline (writer emission, MistRunner pairing
block, MistMain exit path, report consumers, run-#3 report). Reconciliation in
REVIEW-HARDENING-RECONCILIATION.md.

## Findings (most severe first)

**F1 — R3fix positional join misaligns after an asymmetric hook skip; the trigger is
demonstrably live (run #3: 71 control vs 70 fault records).** All records of a triple
share ONE stepKey (= write_endpoint) so the spec's "(stepKey, occurrence)" key is
vacuous; no per-method/ordinal correlator exists on RunRecord. A fault-run step that
dies BEFORE its hooked write (e.g. the injected fault 500s an earlier step in the
method) produces no record → every later fault record shifts left → cross-scenario
joins. unjoinedRecords shows only the count delta, never the location. Bounded:
same-triple joins + control-as-systemic-guard keep the triple-level FIRE robust under
a uniform lost-write fault; but per-pair tallies become silently noisy and a real
loss can land NOT_EVALUABLE against a bad misaligned control. CONFIRMED mechanism /
PLAUSIBLE impact. Mitigation: stamp the generated method name or an execution
ordinal on RunRecord and join on it; until then treat pair tallies as
descriptive-only. Note: R1fix ERROR records still append (no shift) — only
steps that die before the hooked write shift the join.

**F2 — R1fix abort-on-first-non-2xx is strictly more attrition-prone than the old
poll-through loop on the 503-prone SUT.** Old loop: a 503 poll scanned the error
body, found nothing, kept polling — later 2xx could still converge to
OBSERVED_PRESENT (and terminal absence could be scored off error bodies — the
unsoundness fixed). New code kills the record on the FIRST non-2xx anywhere
(baseline, any poll, the R4 re-read — one extra chance to die). At G3 (Toxiproxy
deliberately disturbing dependencies the read-back may traverse) whole legs could
self-report invalid. Right failure direction (explicit-invalid > silently-wrong),
softened by multi-record triples + graceful denominator shrink + status-carrying
errors — but stronger than soundness requires. **Recommended refinement
(pre-registered amendment): tolerate non-2xx polls WITHOUT scanning and keep
polling; conclude absence only from a 2xx decisive read; error only when the
decisive read is non-2xx** — strictly sounder than old, strictly more robust than
new. CONFIRMED change / PLAUSIBLE-to-LIKELY G3 cost.

**F3 — the shipped input-fetch-registry will get dirty again on the next run
(pre-existing).** SmartInputFetcher saves the learned registry back to
smart.input.fetch.registry.path, which for gate runs resolves onto the source-tree
resource (proven by run #3). Restore verified byte-exact; smart-fetch expectations
intact. Mitigation: G2/G3 runbook points the property at a target/ copy.

**F4 — the new load-time GET validation is reachable in oracle-on/injection-off
(generation-only) runs** — a new fail-fast for malformed registries that previously
went unvalidated. Shipped registry complies; right call; noted because it slightly
contradicts the "all changes inside the pairing path" framing (and the yaml's own
stale header). CONFIRMED, LOW.

**F5 — beginRun guard verified unreachable-in-production today** (three call sites,
all inside MistRunner's set-to-"1" bracket; system property wins resolution; the
generated tests share the parent classloader — no separate JVM). "auto"/unset skip
the guard (disclosed). LOW.

**F6 — C-P1-3 sink/write paths verified clean; two small gaps.** No double-write/
overwrite (the F2 throw precedes the success-path writes; process exits non-zero
with the f2 report intact). Gaps: (a) the f2 report lacks WHICH flags failed (spec
said "with the affected flags" — they exist only in the exception/log); (b) a
RuntimeException from writeReport inside the MistRunner lambda is caught by the
executor's guard — safe.

**F7 — report-shape compatibility: safe.** No automated consumers of the report
JSON exist (docs + unit tests + summarizeProbe only, the latter updated). Run #3's
report keeps its v1 shape; bar v2 would not have flipped it (fractions 1.0/0.0).
Cosmetic: an R4 late-presence record includes the 3s settle in elapsedMs (slightly
inflates the FP-vs-timeout curve at small cutoffs).

**F8 — byte-additivity confirmed by inspection** (hooks early-return with no
session; executor behind mist.fault.injection.enabled=false default; writer
untouched). Precise caveat = F4 (oracle-on/injection-off reaches the new load
validation).

## Verdict
**Safe to ship for G2/G3** — every new failure mode is loud, the correct polarity.
Biggest integration risk: **evidence attrition (F2)** — mitigate before the G3 FP
probe with the pre-registered poll-through amendment. Secondary: add a
method/ordinal correlator before per-pair tallies feed any claim (F1; run #3's
71v70 shows the trigger already occurs).
