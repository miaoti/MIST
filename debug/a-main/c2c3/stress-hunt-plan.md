# High-Stress Natural Discrimination Hunt (S3-HS) — plan rev 1 (FOR 3-COLD REVIEW)

**Date:** 2026-07-13 · Owner: main_track · Status: **DRAFT — not executed. Requires ≥3 independent cold
reviewers UNANIMOUS-ACCEPT before any run (standing /goal rule).**
**Lineage:** adapts `s3-wildhunt-plan.md` rev 2.1 (the detector + pre-registration discipline) + the
traced-capture wave (`traced-capture-wave-plan.md`, the OTel-javaagent instrumentation recipe) + E2
(`benchmark/b4/e2/RESULT-e2.md`, the `trace_score.py` 3-config comparator + the specification-locality
framing). This plan changes exactly ONE thing versus S3: the **workload regime** (low-stress single-thread
→ high-stress concurrency/volume) on a **traced sync SUT**, so a natural acked-lost write can be scored
against the frozen trace comparators.

---

## §0. What this is, the honest prior, and the two possible outcomes

**The gap this targets.** E2 (2026-07-11) proved the read-back-catches / trace-misses discrimination on a
**SYNTHETIC** fork fault (fabricated-ack, *defined* to be trace-clean + durable-absent). E2's own honest
framing names the still-owed headline verbatim: *"a NATURAL fault where an in-practice trace oracle misses
and read-back catches = the S3 wild-hunt (rater-gated, deferred)."* S3 (2026-07-13) then hunted that
natural case and found **0 CONFIRMED in N=1514 acked writes / K=5 endpoints** — but in a **deliberately
low-stress regime** (single-threaded, paced, no induced contention). The S3 review flagged this as the
central residual (the low-stress-regime minor): **acked-loss is likeliest under concurrency / load /
partial failure, which S3's workload excludes; the 0.20% rule-of-three bound does NOT exclude rare natural
loss under load.** This hunt goes there.

**The target (the paper headline).** ≥1 **natural** (no injected fault), **rater-genuine**,
**acked-but-durably-lost** write on a **traced sync SUT** where the **in-practice trace oracles**
(naive error-span + service-map-granularity presence, the two E2 pre-registered as what a Tracetest user
actually authors) **MISS**, and MIST's durable-value **read-back CATCHES**. That is the natural analogue
of the E2 synthetic result — the discrimination-over-trace headline the whole arc has deferred to here.

**Honest prior (pre-registered — this is a genuine wild-hunt, not a foregone win).** Two outcomes, both
publishable, declared BEFORE running so neither is a surprise:

- **HEADLINE branch (≥1 rater-genuine trace-missed CONFIRMED):** the natural discrimination exists; the
  paper's central claim upgrades from "synthetic worst-case + a scarcity bound" to "**demonstrated on a
  natural production-shaped loss under load**." This is the A-venue headline.
- **EXTENDED-SCARCITY branch (0 rater-genuine trace-missed CONFIRMED under stress):** the scarcity bound
  from S3 EXTENDS into the high-stress regime — a strictly STRONGER negative result ("0 in the low-stress
  regime AND 0 in H acked writes under P-way concurrency + load, rule-of-three ≤ 3/H"). Not the headline,
  but it closes the F6 escape hatch and makes S3's scarcity claim defensible against the "you didn't look
  under load" objection. The paper then leans on the synthetic E2 existence result + the extended bound +
  the read-back *applicability breadth* (2.75-A), honestly framed.

**We do NOT pre-commit to finding the headline.** The estimand (§8) is symmetric; the value is the
measurement either way. A hunt that is only publishable if it "hits" is a fishing expedition; this one is
pre-registered to be publishable at 0.

**Deliverables:** (1) this plan, reviewer-cleared; (2) a pre-registration freeze row (§1); (3) the traced
high-stress runner + the stress-FP calibration; (4) `RESULT-s3hs.md` (RESULT-of-record with the §8
pre-committed claim sentences for whichever branch fires); (5) per-CONFIRMED rater material (blind case +
sidecar + full comparator battery) if any fire; (6) a SEALED manifest + a 3-cold review of the RESULT.

---

## §1. Pre-registration (freeze row + timing discipline — the S3 rule, unchanged)

Before ANY real capture, a dated pre-registration row is committed to `c2-freeze.md` §6 pinning: the SUT +
traced subgraph, the stress workload parameters (P, rate, volume, ramp, window count), the detector
predicate (RAW + CONFIRMED, §5), the trace-comparator selectors (§6, frozen — reuse the E2 committed
`trace_score.py` selector for the cancel path), the stress-FP calibration bar (§7), the estimand + both
branches' claim sentences (§8), and the rater-gating (§9). **Timing gate (S3 A-F-precedent):** the freeze
row must be a git-ancestor of the P1 engine/runner commits and of every window commit — pre-registered
before the data exists. The detector code is **byte-reused from S3** (`WildHuntEngine.classify()` +
`ReProbeOutcome` — the exact predicate 3 cold reviewers verified); only the workload driver changes, so the
detector predicate is frozen by construction.

**No answer-key leakage:** as in S3, the blind rating case is rendered through `b4_harness.render` with 0
BANNED_STRINGS; the true label / classification / mist_commit are stripped and live only in the sealed
manifest.

---

## §2. The target shape + the per-candidate sub-classification (honest, not binary)

Not every natural acked-loss is the headline. Each CONFIRMED candidate gets the FULL comparator battery
(§6) and is classified into exactly one bucket — declared now so we cannot retro-fit:

| bucket | read-back | naive error-span | service-map presence | DB-span presence | verdict |
|---|---|---|---|---|---|
| **(a) read-back-only** | CATCH | miss | miss | **miss** | strongest discrimination (rarest) |
| **(b) granularity** (E2 shape, natural) | CATCH | miss | miss | catch | **the headline target** — in-practice oracles miss; only a span authored at the exact internal write-granularity, or the read-back, catches |
| **(c) trace-also-catches** | CATCH | **catch** OR service-map **catch** | — | — | natural acked-loss datum, but NOT discrimination (an in-practice trace oracle already flags it) |
| **(d) benign / operational** | no CONFIRMED (raw-delayed, ERROR, non-2xx, or present-at-reprobe) | — | — | — | not counted; FP-calibration territory (§7) |

**Headline = (a) or (b) with a blind-rater "genuine" label.** (c) is reported honestly as "natural
acked-loss that trace ALSO catches" — still evidence the loss class exists in nature, but it does not carry
the discrimination claim. This mirrors E2's accepted framing verbatim: the in-practice trace oracle =
naive error-span + **service-map-granularity presence** (the cross-service SERVER span a Tracetest user
names from the service map); the **DB-span-granularity** presence is the disclosed "if you already knew the
exact internal write span to assert on" comparator — coupled + implementation-specific, not the default a
practitioner authors. A (b) result is exactly the natural version of what E2 measured synthetically.

**§0.4 genuine-eligibility carries over unchanged.** TT write paths are SYNCHRONOUS (a completion bound
exists — control writes land within the cap). A stress-loss on such a path is genuine-eligible. **If** a
candidate's loss turns out to be an internal fire-and-forget with no completion bound (an async drop hiding
inside a sync-looking endpoint), it is **underspecified-by-rule** (like the OTel async losses) — disclosed,
NOT counted toward the genuine estimand. The completion-bound check is applied per candidate.

---

## §3. SUT + traced instrumentation (TrainTicket cancel-refund path — direct E2 reuse)

**SUT = TrainTicket, traced, cancel-refund write path.** Rationale, pre-registered:
- **Sync ⇒ genuine-eligible** (§0.4). OTel-Demo is async → any stress-loss there is underspecified-by-rule,
  so it CANNOT yield the genuine headline; it is **excluded** from the genuine leg (may appear only as a
  stress-FP cross-check, not headline-eligible). TeaStore is sync but trace-**uninstrumented** in the
  corpus (sole-oracle) → cannot show a trace-miss without new instrumentation; **deferred** (optional
  secondary in §11 if TT yields 0 and RAM allows).
- **E2 already instrumented this exact path end-to-end** (`ts-cancel-service`, `ts-inside-payment-service`,
  `ts-order-service`, javaagent 1.33.6, jaeger-collector, canary-verified id-resolvable trace with the
  entry + drawback + `INSERT ts.inside_money` DB-client span). The instrumentation recipe + the frozen
  `trace_score.py` cancel selector are **reused verbatim** — no new tool code, no new selectors.
- **Richest natural-loss surface under stress:** the write lands in the **Xenon MySQL HA** cluster
  (`inside_money`) via Spring Cloud Gateway + Sentinel + ribbon + nacos — a stack with real concurrency
  fragility (pool exhaustion, failover, stale routing). If a natural acked-loss exists anywhere in this
  corpus, this path is the most likely host.

**Instrumentation mechanics (verbatim from the traced-capture wave §3, proven):** `docker cp` the pinned
`opentelemetry-javaagent.jar` (1.33.6, sha256-pinned) into the kind node once; per-deployment `hostPath`
mount + env (`JAVA_TOOL_OPTIONS=-javaagent:...`, `OTEL_SERVICE_NAME`,
`OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger-collector.istio-system:4318`,
`OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf`, `OTEL_TRACES_SAMPLER=always_on`, metrics/logs exporters
`none`), DEFAULT instrumentation (no suppression — attested in each sidecar). Fully reversible; cluster
ends in today's state. **RAM discipline (E2 lesson — TT over-committed the 26 GB WSL and wedged):** revive
TT as the **cancel-refund subgraph only** (non-path services scaled to 0); pre-flight `free` ≥ threshold;
the Xenon recovery runbook (force-delete `tsdb-mysql-0` to re-form quorum after a hard restart) is on hand.

**Sampling under stress (NEW pin, critical):** `always_on` at high volume can overwhelm the badger
emptyDir backend / drop spans → a DROPPED span would masquerade as a trace-miss (false discrimination).
Mitigation, pre-registered: (i) the discrimination scoring is **id-selected per candidate** (the stimulus
injects a W3C traceparent, exactly E2's C1) — only a candidate whose FULL trace is retrievable and
**parent-child-linked** is comparator-scorable; (ii) a candidate whose trace is **incomplete/unretrievable**
is marked `trace-indeterminate` and is **NOT** counted as a trace-miss (it fails safe to "cannot claim
discrimination"), disclosed; (iii) a stress-level span-loss audit runs in calibration (§7) — if the backend
drops spans for KNOWN-complete control writes above a pre-set rate, the stress level is throttled until
span-capture is reliable, OR the window's absence-based trace cells are declared unclaimable (the
traced-wave B-M1 rule).

---

## §4. The stress workload (natural — workload-only, NO injected fault)

The stress is **pure workload** (concurrency + rate + volume). **No fault injection, no config sabotage, no
mesh fault, no scale-down of the path's own replicas** — any loss that emerges is the SUT's OWN behavior
under load = natural. This is the bright line that keeps the result a *natural* discrimination.

- **Concurrency:** P parallel journey-threads driving the cancel-refund path, P ramped `{8, 16, 32}` (stop
  ramping one step below the level where the SUT returns predominantly loud 5xx/429 — see the acked-bar
  below; we want the regime where the SUT still *accepts* writes with clean 2xx but strains internally).
- **Rate:** as high as the path sustains with a majority of 2xx acks. (S3's 800 ms pacing existed to AVOID
  the gateway 429 knee; here we push TOWARD the knee but stay below pure-rejection — 429/5xx are loud, NOT
  acked, so they are excluded by the detector and waste no CONFIRMED budget, but a pure-429 storm yields no
  acked writes to hunt. The useful regime is "high acceptance, high internal strain.")
- **Volume:** target **H ≥ 5,000 acked writes** aggregate across windows (vs S3's 1,514) to raise the
  chance of catching a rare event and to tighten the rule-of-three bound if 0. Windows are sub-batched for
  RAM/stability; H is the pre-registered denominator.
- **"Partial failure" (F6's third axis), kept natural:** emergent partial failure UNDER load — some Xenon
  replicas lagging, pool timeouts, ribbon retries against a saturated instance. These arise from the load
  itself, not from an injected fault. (We do NOT kill replicas or inject latency — that would manufacture
  the loss and forfeit "natural.") If the reviewers judge that a *controlled, disclosed, natural-class*
  perturbation is needed to reach the loss regime, that is a **rev-2 pre-registered amendment**, not a
  silent change — flagged as an open question for review (§12 Q3).

**The acked bar under stress (the hard, headline-worthy case).** The detector counts a write only if it
returned a **clean 2xx application-success ack** (TT's `{status:1}` envelope, not the `{status:0}`
soft-reject). Under load most failures will be loud (5xx, timeout, 429, `{status:0}`) — those are excluded
(they are NOT silent loss; a trace/alarm sees them). The headline case is the **narrow, valuable**
intersection: a write that gets a clean 2xx `{status:1}` ack AND is durably absent. That is precisely the
"acknowledged-but-lost" class trace oracles cannot see from the response.

---

## §5. The detector (S3 detector-(ii) + CONFIRMED re-probe — byte-reused)

Unchanged from S3 (the predicate 3 cold reviewers verified sound):
- **RAW** = acked (`{status:1}` 2xx) ∧ `error == null` ∧ absent-at-cap (gate ∈ {`TIMEOUT_ABSENT`,
  `OBSERVED_COMPLETE_ABSENT`}) ∧ the W3 quarantine gate open. A non-2xx / non-`{status:1}` decisive
  read-back → `recordReadbackError` → `record.error != null` → classified "error", **never RAW**. This is
  what makes the higher stress error-rate safe: loud failures cannot fabricate a RAW.
- **CONFIRMED** = RAW ∧ still-absent at a **T+Δ re-probe** (Δ = the re-probe cap; S3 used 300 s). A
  present-at-reprobe candidate = **raw-delayed** (benign; the write was merely lagged). **Under stress,
  replication lag is larger** → Δ is re-set from the §7 stress calibration (the benign-delay tail), NOT
  assumed at 300 s. Δ is pinned in the freeze row before the hunt.
- **Marker discipline:** TT admin-basic writes are unique-keyed → per-run salt (XOR nanoTime) to avoid the
  duplicate-key `{status:0}` collision that bit S3. The cancel-refund path writes to `inside_money` keyed
  by order/account — confirm keying at P1 canary and salt if unique-keyed.

**Per-run detector-provenance (S3 F1 lesson):** the exact `WildHuntEngine` commit for every window is
stamped in its `window-log.json`; `classify()` is byte-identical to the S3-sealed classifier (verified by
diff at P0).

---

## §6. The trace-comparator battery (frozen `trace_score.py` — E2 reuse) + discrimination scoring

For **every CONFIRMED candidate**, on the traced deploy, export the leg's id-selected trace (traceparent
injected by the stimulus = E2 C1) and score with the **frozen, already-committed** `trace_score.py` and its
**already-committed cancel selector** (no new selectors authored — anti-circularity + no post-hoc tuning):

1. **naive error-span** = any ERROR-status span under the 2xx entry, scoped to the instrumented services,
   no exclusions. Expect **miss** for a silent loss; a **catch** here → bucket (c).
2. **service-map-granularity presence** = the cross-service SERVER span the Tracetest user names from the
   service map (cancel selector: `ts-inside-payment-service` server span for `POST /drawback`). Expect
   **miss** (the HTTP hop completed — the loss is below it); a **catch** → bucket (c).
3. **DB-span-granularity presence** = the `INSERT ts.inside_money` DB-client span. **Measured + disclosed**
   as the coupled comparator; a **catch** with (1)+(2) missing → bucket (b) = the headline.
4. **MIST read-back** = the detector already fired (CONFIRMED) = catch.

**Trace-completeness gate (from §3):** a candidate whose id-selected trace is not fully retrievable +
parent-child-linked is `trace-indeterminate` → excluded from the discrimination numerator (fails safe),
disclosed. Only complete-trace candidates can carry a (a)/(b)/(c) verdict.

**`mist_trace_shape`:** NEVER hand-derived (the traced-wave T9 rule). Reported only if a real MIST
trace-shape vehicle can run on the exported trace at the pinned commit; else the cell stays
"traced-but-not-run, deferred" — it does not affect the naive/presence/read-back verdicts that carry the
claim.

---

## §7. The stress-FP calibration (THE CRUX — what separates this from an FP storm)

High stress inflates the false-positive surface (transient absence, replication lag, backend span-drops,
connection resets). The calibration is the load-bearing methodological addition; it runs BEFORE the hunt
windows and its bars are pre-registered:

**C1 — benign-delay distribution under stress → sets Δ.** Drive a **KNOWN-DURABLE** high-stress workload
(writes verified to land by direct store read) at the hunt's P/rate; measure the distribution of
absent-at-cap → present-at-reprobe latencies (the raw-delayed tail). **Bar:** Δ (the CONFIRMED re-probe
cap) is set to exceed the observed benign-delay P99 by a margin, so a merely-lagged write cannot reach
CONFIRMED. If the benign tail is unbounded (no completion bound even for durable writes) the path is
async-underspecified → §0.4 excludes it. Δ is pinned in the freeze row.

**C2 — stress-FP soundness bar: 0 CONFIRMED on known-durable writes.** The whole known-durable calibration
batch (writes that provably landed) must yield **0 CONFIRMED** (they may yield raw-delayed, which is
benign-by-definition). A non-zero CONFIRMED on a known-durable write means the detector is UNSOUND under
stress (the re-probe/keying is racing the store) → **STOP**, diagnose, re-pin, re-calibrate. This is the
gate that earns the right to treat a hunt-window CONFIRMED as a real loss.

**C3 — span-capture reliability under stress (from §3).** For the known-durable batch, audit that the
id-selected traces are retrievable + complete at the hunt's stress level above a pre-set rate. If the
backend drops spans for complete writes, throttle the stress until reliable OR declare absence-based trace
cells unclaimable for that level (traced-wave B-M1). Prevents a dropped span from faking a trace-miss.

**C4 — the acked-bar audit.** Confirm the detector's non-2xx / `{status:0}` → ERROR routing holds at the
elevated stress error-rate (re-verify the S3 R2 finding under load): sample loud failures and confirm none
reach RAW. Confirms loud stress failures cannot pollute the numerator.

Only after C1–C4 pass are the hunt windows opened.

---

## §8. Estimand + pre-committed claim sentences (both branches, §0.5-style, verbatim-at-RESULT)

**Estimand:** a LOWER BOUND on the prevalence of rater-genuine, trace-missed (bucket a/b), acked-lost
natural writes on a traced sync production-shaped write path **under P-way concurrency + high load** — the
regime S3 excluded.

**HEADLINE branch (≥1 rater-genuine bucket-(a/b) CONFIRMED):**
> "On a traced TrainTicket cancel-refund write path driven at [P]-way concurrency / [rate], we observed
> [m] natural, blind-rater-adjudicated-genuine, acknowledged-but-durably-lost write(s) for which the naive
> error-span and service-map-granularity trace oracles a practitioner authors both MISS, while MIST's
> durable-value read-back CATCHES. This is the natural analogue of the E2 synthetic result: an
> acknowledged-but-lost write is invisible to the in-practice trace oracle and visible to the read-back.
> [For bucket (b): a DB-span-granularity presence assertion also catches it, disclosed — the read-back's
> advantage is granularity + implementation-decoupling, not zero-authoring, exactly per E2.] No prevalence
> rate is extrapolated beyond this SUT/regime; the estimand is a lower bound."

**EXTENDED-SCARCITY branch (0 rater-genuine bucket-(a/b) CONFIRMED):**
> "In H = [N] acknowledged writes on a traced TrainTicket cancel-refund path driven at [P]-way concurrency
> / [rate] (the high-stress regime S3 excluded), we observed 0 rater-genuine trace-missed acked-lost
> writes ([k] raw-delayed/benign; [j] bucket-(c) trace-also-catches if any). Combined with S3's 0 in the
> low-stress regime, the rule-of-three 95% upper bound on the trace-missed natural-loss rate is ≈ 3/H =
> [rate]. The natural discrimination headline is therefore not demonstrated on this corpus under load; the
> discrimination-over-trace claim rests on the E2 synthetic existence result, and the paper's empirical
> contribution is the read-back applicability breadth (2.75-A) + this bounded-scarcity result. No
> cross-population claim."

**κ / rater-agreement:** withheld at |genuine| < 10 (the S3 rule; degenerate at 0). Any single CONFIRMED
headline case is reported as an existence result with its blind-rating adjudication, not a rate.

---

## §9. Rater-gating + blindness pipeline (S3 reuse)

Every bucket-(a/b) CONFIRMED → a blind rating case (schema 2.0.0, stress-window sidecar with the 3
one-shape observations: baseline / at-cap-absent / re-probe-still-absent) + the full comparator battery in
the sidecar. Rendered through `b4_harness.render`: opaque id, 0 BANNED_STRINGS, answer key stripped. The
"genuine vs operational-artifact" call is the **blind rater's** (given the read-back probe + the SUT
domain, NOT the label). The headline requires a genuine label. The case joins the C3 rating corpus under
the same M-yield / IRB / two-round holds already documented in the S3 hand-over.

---

## §10. Phases

| phase | content | verify / gate |
|---|---|---|
| **P0** | Pre-registration: freeze §6 row (SUT, workload P/rate/H, detector, Δ-placeholder, comparator selectors reused, FP bars, both claim sentences, rater-gating); diff-verify `classify()` byte-identical to S3-sealed; pin javaagent sha256 | freeze row is git-ancestor of all P1+ commits; classifier diff = 0 |
| **P1** | Revive TT cancel-subgraph; instrument cancel path (E2 recipe); canary (id-resolvable trace w/ entry+drawback+INSERT span); RAM/stability pre-flight; commit the traced high-stress runner | canary trace retrievable + linked; `free` ≥ threshold; runner committed BEFORE calibration |
| **P2** | **Stress-FP calibration C1–C4** (known-durable batch): benign-delay tail → pin Δ; 0 CONFIRMED on durable; span-capture reliable; acked-bar routing holds under load | C2 = 0 CONFIRMED on known-durable (else STOP + re-pin); C1 pins Δ; C3/C4 pass |
| **P3** | **Hunt windows**: ramp P `{8,16,32}`, drive to H ≥ 5,000 acked, sub-batched; detector RAW live; per-window log stamps commit | H reached OR stop-rule (§11); RAW candidates captured |
| **P4** | **CONFIRMED re-probe + comparator scoring**: T+Δ re-probe each RAW; for each CONFIRMED, id-select trace → frozen `trace_score.py` battery → bucket (a/b/c/indeterminate); §0.4 completion-bound check per candidate | each CONFIRMED bucketed from MEASURED artifacts; trace-indeterminate excluded (fail-safe) |
| **P5** | **Rater material + assemble**: bucket-(a/b) → blind case + sidecar + battery; render leak-clean; SEAL manifest | 0 BANNED_STRINGS; schema-valid; sealed |
| **P6** | **RESULT-s3hs.md** (the §8 branch that fired, verbatim) + freeze close-out row + FILE_INDEX + memory; **3-cold review → reconcile** | ≥3 reviewers ACCEPT; fixes applied; re-seal |
| — | restore cluster (de-instrument, restore images/env, non-path scale restore), post-restore TT key-path smoke | cluster in pre-hunt state; DoD smoke green |

---

## §11. Risks + pre-registered failure handling

| risk | handling (pre-registered) |
|---|---|
| **TT falls over from stress before H reached** (RAM wedge / Xenon deadlock / cascade) | sub-batch windows; `free` + pod-health between batches; Xenon `tsdb-mysql-0` force-recreate runbook on hand; **stop-rule: if TT cannot sustain the target stress, SHIP the achieved-H partial + the extended-scarcity bound at that H + disclose** (a smaller H is a weaker bound, not an invalid one) |
| **FP storm** (stress fakes CONFIRMED) | §7 C2 gate — 0 CONFIRMED on known-durable is a HARD precondition; a non-zero there STOPS the hunt until the detector is re-pinned; Δ from the measured benign tail, not assumed |
| **Backend drops spans under load → fake trace-miss** | §3 sampling pins + §7 C3 span-capture audit; trace-indeterminate candidates fail safe to "not a trace-miss"; throttle stress until span-capture reliable |
| **Loud failures pollute the numerator** | detector non-2xx/`{status:0}` → ERROR routing (S3 R2), re-verified under load at §7 C4 |
| **Marker collision under concurrency** | per-run nanoTime salt (S3 fix); confirm cancel-path keying at P1 canary |
| **0 yield** | pre-registered as the EXTENDED-SCARCITY branch (§0, §8) — a valid, stronger result, not a failure |
| **Loss is async-internal (no completion bound)** | §0.4/§2 per-candidate completion-bound check → underspecified-by-rule, disclosed, not counted genuine |
| **Async SUT temptation** | OTel-Demo EXCLUDED from the genuine leg by §0.4; do not backfill the headline from an async loss |

**Optional secondary (only if TT yields 0 and RAM allows, and only as a pre-registered rev-2 amendment):**
instrument + stress the TeaStore sync write path (same recipe) to widen the sync denominator. Not in rev-1
scope; flagged for the reviewers.

---

## §12. Out of scope + open questions for the reviewers

**Out of scope:** any MIST tool-code change beyond the S3-reused detector + the E2-reused stimulus
traceparent hook (the scoped gate); OTel-Demo/TeaStore as headline SUTs (async-excluded / uninstrumented);
new trace selectors (frozen from E2); the kafkaQueueProblems S1 deferral; the degradation-shaped benign
capture wave (a separate recommendation).

**Open questions I want the reviewers to rule on (rev-1 → rev-2 gates):**
- **Q1 (headline reachability):** is pure workload stress (no injected perturbation) a plausible route to a
  natural acked-loss on this path, or is the honest prior so scarcity-weighted that the EXTENDED-SCARCITY
  branch is the near-certain outcome — and if so, is the extended bound alone worth the RAM/stability cost?
  (I lean: worth it — it closes F6 and the run also produces the traced high-stress infra the paper needs;
  but I want this challenged.)
- **Q2 (H sizing):** is H ≥ 5,000 the right denominator, or does the rule-of-three math + the stability
  ceiling argue for a different target?
- **Q3 (the "natural" bright line):** if the loss regime is unreachable by pure workload, is a *controlled,
  disclosed, natural-CLASS* perturbation (e.g., a transient resource cap that mimics organic pool
  exhaustion) still "natural" for the paper's claim, or does ANY perturbation forfeit the natural headline
  and collapse this into a (differently-framed) injection study? This is the plan's biggest framing risk —
  I want an explicit ruling BEFORE execution.
- **Q4 (secondary SUT):** should TeaStore instrumentation be in rev-1 scope to widen the sync denominator,
  or held as the §11 amendment?

**Nothing executes until ≥3 independent cold reviewers return ACCEPT and the open questions are ruled on.**
