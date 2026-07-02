# Cold review C — B1+B2 implementation correctness + contribution/positioning

**Date:** 2026-07-02. Independent cold reviewer (no shared context), one of three
dispatched per the ≥3-cold-reviewer rule during the Gate-1 run-#3 wait. Focus:
implementation correctness (concurrency, injector toggle, edge cases) + A-conference
contribution/positioning. Verbatim findings below; reconciliation across all three
reviewers lands in a separate doc.

---

## PART 1 — IMPLEMENTATION CORRECTNESS (ranked)

### P1-1. The prompt's concurrency races are all *latent, not active* — CONFIRMED, but one is a real fragility
Pairing/probe path is forced single-threaded (`MistRunner.java:613-614` sets
`mst.test.parallelism=1`; `resolveTestParallelism` at `:3170` gives the system property
top priority). Therefore: `ThreadLocal pending` set by `beforeWrite` and consumed by
`afterWrite` are always the same thread (`DataIntegrityRuntime.java:173,263,290`) — no
A→B handoff; generated code is straight-line synchronous RestAssured (writer
`:1991,:2207`) — SAFE. `beginRun`/`endRun` cannot overlap (`:224-226`; strict sequence at
`PairedFaultExecutor.java:131-152`) — SAFE. `claimedPairs` + `synchronizedList` are
belt-and-suspenders, correct if ever parallel.

**Real issue (PLAUSIBLE, latent):** the hooks are process-global statics keyed on one
`volatile Session`; single-threadedness is enforced by ONE `System.setProperty` in ONE
caller — an invariant by convention, not construction. Any future caller running the
hooks with parallelism>1 silently re-activates every race. Worth a guard (`beginRun`
refusing to arm when resolved parallelism>1).

### P1-2. Read-back HTTP status is never checked — flaky/5xx read-back indistinguishable from a genuinely-lost write. CONFIRMED
Poll loop (`DataIntegrityRuntime.java:325-328`), not-acked branch (`:311-315`), baseline
capture (`:258`) use only `HttpResponse.body`; `.status` is discarded. A 500/empty/
malformed read-back → `extractItems` returns `[]` (`:513-516`) → `containsKey` false →
"X absent." A read-back 5xx under load makes a *persisted* write look lost. Bounded —
such a fire usually lands TIMEOUT_ABSENT and the ≤5% bar counts only
OBSERVED_COMPLETE_ABSENT (`PairedFaultExecutor.java:369,384`) — but the record stores
`readbackContainedX=false` with no signal the read-back itself failed. This is exactly
the regime the live run #2 hit (`gate1-result.md:30-39,75-77`). Baseline flakiness is
worse: a 500 baseline yields empty `usedPairs` in `freshStationPair` (`:431-439`),
silently weakening isolation, and a false `baselineContainedX=false` defeats the
isolation guard in `verdict` (`:265-268`).

### P1-3. On a clear-failure the entire pairing report is discarded — CONFIRMED (by code and incident log)
`execute()` throws F2 `FaultInjectionException` at `PairedFaultExecutor.java:166-170`,
**before** verdicts (`:172-181`); in `MistRunner` (`:624-627`) `results` is never
assigned, `writeReport` never runs. A failed flag-clear = SUT possibly left faulted
(correctly signalled) **but all control/fault RunRecords — including any FIRE — are
thrown away.** The records exist as locals before the throw and could be persisted
first. `gate1-result.md:40-45` confirms this happened. Safety behavior right; the
"don't swallow the report" intent NOT met.

### P1-4. "SUT is never left faulted" is a *signal*, not a *guarantee* — CONFIRMED
The clear-all `finally` (`:153-165`) does not survive process/JVM death (run #1/#2
wedge left the pod faulted until manual `docker stop`). Guarantee holds only if the JVM
survives and kubectl responds. Operational hazard → threat-to-validity, not a code bug.

### P1-5. Batched inject/clear is correct; partial-inject cannot leave a triple half-faulted — CONFIRMED SAFE
`inject` loop inside the try (`:144-146`); throw on triple #2 reaches the `finally`
clearing ALL injectable targets (`:156-164`); `clear` idempotent
(`SutFlagFaultInjector.java:143-144`).

### P1-6. Multi-record join is positional (first-acked), not keyed by method — PLAUSIBLE sensitivity gap (FP-safe)
One triple can be hooked by many methods (writer `:2208`; run #2 had ~100 variant
methods for adminroute). `pick` (`PairedFaultExecutor.java:218-233`) returns the first
acked/error-free record per run-list — a positional join assuming stable cross-run
ordering. Because each record's `readbackContainedX` is evaluated against its OWN
freshened key, a mismatched join can only MASK a real loss (false negative), never
fabricate a FIRE — safe for the FP bar, but can defeat the fires-on-constructed-case
sensitivity claim if the representative fault record is a variant that persisted.
`controlRecordCount`/`faultRecordCount` surfaced (`:452-453`) but ignored by verdict
logic.

### P1-7. A configured triple can be silently dropped from the run — CONFIRMED
Empty-body write step → writer skips hook emission with only a WARN (writer
`:1976-1984`). `executePairedDataIntegrity` hard-fails only when ZERO methods matched
any triple (`MistRunner.java:561-569`); partial match proceeds silently ("2 triples
configured" degraded to "1 automated" in run #2, `gate1-result.md:78-79`). Coverage
should be asserted per-triple.

### P1-8. Station-pair isolation: finite pair space + permanent writes → exhaustion over a 30-iteration probe. PLAUSIBLE
Every acked write persists a route; `freshStationPair` excludes all baseline
`usedPairs` (`:431-462`). ~100 variant methods × (control+fault+30 probe) runs
progressively consume the ordered-pair space → possible "no unused (start,end) station
pair left" → pass-through/NOT_EVALUABLE for later runs, shrinking the FP denominator
(must clear `MIN_ACKED_FOR_BAR=20`). Compounds with P1-2. Station-pair correctness is
coupled to `ts-station-service` health — disclose. (Run #3 note: catalogue = 87 →
87×86 = 7,482 ordered pairs; consumption bounded well below that, but watch the log.)

### P1-9. Edge-case parsing — mostly robust, two SUT-coupling caveats. CONFIRMED
- `readbackPath` requires uppercase `"GET "` (`:359-365`) but registry load never
  validates it — a lowercase `readback_endpoint` passes load, throws at runtime →
  NOT_EVALUABLE.
- `bodyStatus` (`:519-529`) null on non-object/non-integer `status`; with
  `acked = 2xx && (bodyStatus==null || ==1)` (`:300`), a `status:"ok"` string or bare
  array body is acked on HTTP 2xx alone. Fine for TrainTicket's integer convention but
  a hard SUT-coupling — undercuts the "label-free / general" framing; port note needed.
- `traceComplete` (`:536-557`) conservative on failure, BUT a stable-but-partial trace
  (exporter dropped spans, two equal counts) yields a FALSE `OBSERVED_COMPLETE_ABSENT`
  — high-confidence absence that is wrong; feeds the ≤5% bar's numerator → load-bearing,
  disclosed in plan. Note: the canonical FIRE unit test only reaches TIMEOUT_ABSENT
  (`PairedFaultExecutorTest.java:155`, Jaeger stubbed 404) — absent Jaeger, every
  headline FIRE is the low-confidence stratum.
- Jaeger URL construction matches the writer's convention — OK. Auth applied on SUT
  read-back GET, not on Jaeger — correct. `FaultTarget` injection-hardening present.

### P1-10. SUT-flag toggle confirms *rollout*, not *fault activation* — CONFIRMED threat-to-validity
`inject` = `set env` → `rollout status` → settle (`SutFlagFaultInjector.java:121-137`;
settle wired `MistRunner.java:577-578`): confirms pod convergence, NOT that the `-D`
reached the JVM or that the external LOST_WRITE drop code executed. Drop logic lives in
`train-ticket-injection@injection`, unverifiable from this repo — explicit
threat-to-validity. Minor: `currentJavaToolOptions` whitespace-split (`:164-171`) would
mangle a quoted-space JVM option (safe for TT's space-free tokens).

---

## PART 2 — CONTRIBUTION & POSITIONING (ranked by reject-risk)

### P2-1. Headline novelty is UNMEASURED against the one relevant competitor — dominant reject risk
Plan concedes: Gate-1 = "essentially ZERO novelty evidence"; ALL novelty evidence
back-loaded to G2 (fair Cast/Filibuster comparator) + G3 (`TOOL-EXECUTION-PLAN.md:
344-351`); research/03 §4.3 admits the read-back diff "automates an assertion, not a
new analysis". The strong claim vs Cast is explicitly forbidden as unverified
(`:56-60`). Classic fatal gap for an A-venue: the load-bearing comparison is deferred;
the plan itself calls a fair Cast comparator make-or-break (`:461-466`).

### P2-2. Demonstrated delta is over strawmen on a self-injected fault — "manufactured delta"
Gate-1 fires on a fault MIST itself injects, beaten only against naive span-error +
MIST's own gated mode (`:344-347`). The genuine conceptual contribution — S2
(skip-persist, no downstream error span) coverage that downstream-error-keyed oracles
miss (`:49-56`) — is real but S2 "is only producible invasively" (`:62-66`) →
co-designed fault + strawman baselines = internal-validity/circularity objection.

### P2-3. Prior art a hostile reviewer will cite
(a) metamorphic REST-API testing (Segura/Alonso et al. — state/CRUD MRs; the FIRE rule
is a textbook per-run metamorphic relation, plan `:28` says so itself);
(b) differential/regression REST oracles (EvoMaster, RESTest nominal-vs-error,
response-equivalence); (c) microservice fault-injection + functional oracles —
Filibuster (Meiklejohn et al.), Gremlin (Heorhiadi, ICDCS'16) are closest;
(d) Cast for data-consistency. **Defensible island: the combination — label-free (no
golden state / per-test assertion) + black-box collection-membership key + quiescence
gate + trace-completeness confidence stratification, targeting acknowledged-but-lost
writes.** Legitimate engineering contribution but oracle *automation*, not a new
analysis — G2/G3 evidence must carry it.

### P2-4. External validity: one SUT, effectively one automated triple, shallow CRUD, no labeled benchmark
TrainTicket only; contacts leg body-less/unhooked in run #2; "shallow CRUD" by
admission (`:331-343`); no 2nd SUT / labeled benchmark until G2/G3. Effectively
required for a top venue: **≥2 SUTs + a fair comparator + ≥1 non-injected (wild)
defect** — all deferred. Applicability is narrower than "microservices": needs a write
path with clean black-box collection read-back + a TrainTicket-specific ack decoder
(P1-9).

### P2-5. Reproducibility / threats reviewers reject on
- **No clean automated end-to-end result exists yet** (run #2 produced no JSON;
  mechanism validated only manually at G0). At least one clean PASS with the JSON is
  needed. (Run #3 is exactly this.)
- **≤5% bar trivial-pass hole:** observation gate unavailable → all fires timeout-gated
  → numerator structurally 0 → "PASS". Code flags it ("weak evidence",
  `PairedFaultExecutor.java:339-343`) and refuses <20 acked (`:329-334`) — good, but a
  reviewer will demand the PASS show a non-trivial observed-gated denominator.
- **Async regime gets zero validation at Gate-1** (sync-only) — a disclaimer-path PASS
  must not read as general soundness.
- Positives: pre-registered numeric bar, gate stratification, flags-off byte-identity
  additivity, two-mode separation — methodologically sound.

---

## One-line verdict
Mechanism: implementation is sound-by-construction for the single-threaded pairing path
with correct fail-safes, but the read-back-status blind spot (P1-2),
report-loss-on-clear-failure (P1-3), and no clean automated result make it demo-solid,
not yet evaluation-solid. Contribution: NOT yet A-ready — most likely reject reason:
the headline label-free/differential oracle is, by the authors' own admission, an
automated metamorphic/differential assertion whose novelty over Cast /
Filibuster / metamorphic-REST prior art is entirely deferred to G2/G3 and demonstrated
so far only against strawman baselines on a self-injected fault in a single SUT.

---

## Author triage (main session, same date — what this means for run #3)

- **Nothing here invalidates run #3.** P1-1 defused (parallelism=1 active); P1-5 safe;
  fail-safes correct.
- **Report-audit checklist derived from P1-2/P1-6/P1-7/P1-9/P1-10** (apply when run #3's
  JSON lands): (1) inspect `lastReadbackBody`/`baselineBody` of every fire + every
  NOT_EVALUABLE for error-shaped bodies (P1-2 post-hoc mitigation — the report carries
  the bodies); (2) check `controlRecordCount`/`faultRecordCount` per pair and scan for
  a persisted-variant masking a FIRE (P1-6); (3) check which triples appear in `pairs[]`
  (P1-7 contacts-leg coverage); (4) confirm fires are OBSERVED_COMPLETE_ABSENT with a
  non-trivial observed-gated denominator (P2-5 / P1-9 Jaeger note — traceparent
  propagation was pre-verified live for run #3); (5) if fault-run writes persisted
  (NO_FIRE "did the injected flag take effect?"), suspect P1-10 activation-vs-rollout.
- **Post-run hardening candidates (NOT during run #3):** persist RunRecords before the
  F2 throw (P1-3); parallelism guard in `beginRun` (P1-1); read-back status on the
  record (P1-2); per-triple coverage assertion (P1-7); registry-load validation of
  `readback_endpoint` (P1-9).
- **Paper positioning:** P2-3's "defensible island" formulation + prior-art list feeds
  related-work; P2-1/P2-4 confirm the direction verdict (novelty carried by G2
  comparator + G3 ≥2-SUT/wild-defect evidence).
