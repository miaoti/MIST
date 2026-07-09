# R4 comparator operability spike (plan v2 §5 step 1 — E2 arms 2/3/4) — 2026-07-08

**Scope + method.** The plan moves the R4 spike into step 1 and gates the TraceAnomaly arm on "the
step-1 spike clearing in ≤2 days; fallback = arms 1–3." This spike is a **documentation +
tool-artifact operability/applicability assessment** — no installs yet, because every trace arm needs
target-SUT write-path instrumentation that does not exist until the step-2.5 instrumentation wave
(review C confirmed TT + SS are trace-dark on the target paths). The actual smoke-installs are
scheduled for the first step-2.5 window (lowest-risk target = Bookinfo, already Istio+Jaeger-traced).
The go/no-go verdicts below are decidable NOW from each tool's own artifacts.

## Verdict (go/no-go per E2 arm)
| arm | tool + assertion | operable? | verdict |
|---|---|---|---|
| 1 | naive span-error oracle | yes (reads error spans) | GO (baseline) — gated on step-2.5 traces |
| 2 | Tracetest + generic span-ERROR assertion | **yes** | **GO** — gated on step-2.5 traces |
| 3 | Tracetest + downstream-span-PRESENCE assertion | **yes — mechanism confirmed** | **GO — this is the real frontier arm for the masked class** |
| 4 | TraceAnomaly / TraceRCA (stretch) | runnable prototype, **inapplicable-by-construction** | **RE-SCOPE** to a construction-blindness demonstration (not a matched-recall competitor); fallback = arms 1–3 as pre-registered |

**Shared blocker (all trace arms):** every arm consumes spans, so E2 as a whole is gated on the
step-2.5 instrumentation of target write paths. This matches the plan's sequencing (spike in step 1,
runs in step 6 after step 2.5). Not a new risk — a confirmed dependency.

**Rev-2 review annotations (2026-07-08, step-1 review C-A3):**
- **A 5th, NON-trace-family arm is added: `contract-invariant`** (Pact/Dredd contract-verification or an
  AGORA+-style invariant oracle — the class `g3-result.md` R-SS-2 names as the real comparator family).
  The four trace arms (naive, tracetest-error, tracetest-presence, traceanomaly) are all trace-span-shaped,
  which is precisely the family blind-by-construction to MIST's read-back differentiator — choosing ONLY
  those would be the anti-tautology failure the plan guards against. The contract-invariant arm is a
  read-back-adjacent baseline that can, in principle, catch a lost write, so it is the fair strong
  comparator. Added to the frozen `comparator_configs.arm` enum (`c2-freeze.md` §2 / the JSON schema).
- **The TraceAnomaly re-scope is PROVISIONAL-until-run.** The construction-blindness verdict is an
  artifact-level (docs) argument; it is CONFIRMED by an actual step-2.5/step-6 run on captured S1
  masked-write traces before it is stated as a result. Until then it is a hypothesis, disclosed as such
  (this answers "you excluded your only learned baseline by armchair verdict").

## Evidence

### Tracetest (arms 2 + 3) — OPERABLE, span presence/absence CONFIRMED
- **Deployment model:** three components — the instrumented SUT, a trace backend (Jaeger/Tempo), and
  Tracetest (the Tracetest Agent runs in-cluster; OTLP ingest at
  `tracetest-agent.<ns>.svc.cluster.local:4317` gRPC / `:4318/v1/traces` HTTP). Tests are YAML:
  a trigger (the request) + assertions over the resulting trace's spans.
- **Selector language:** matches spans by `service.name`, span `name`, and attributes, with AND
  (space-separated), OR (comma), a `contains` operator, and positional pseudo-classes
  (`:first`/`:last`/`:nth_child`). E.g. `span[service.name="cart-api", name="purchase products"]`.
- **Span PRESENCE / ABSENCE (the arm-3 linchpin) — CONFIRMED:** Tracetest exposes a meta-attribute
  `attr:tracetest.selected_spans.count`. Documented usages: `= 0` (assert the selected span is
  ABSENT — e.g., "no database span was emitted"), `>= 1` (assert PRESENCE), `= N` (exact count).
  → A masked/severed downstream write leaves the write span ABSENT ⇒ a selector matching that span
  with `...count = 0` fires the assertion ⇒ the comparator flags. This is exactly E2 arm 3.
- **Authoring cost (the "automation-gap" datum the plan wants):** these presence assertions are
  **hand-authored per test / per endpoint** — a human writes the span selector + the count assertion
  for each endpoint's expected downstream spans; there is no generation of them from a spec. E2 arm 3
  reports this per-endpoint authoring cost + the arm's benign-trap FP.

### TraceAnomaly (arm 4) — RUNNABLE PROTOTYPE, INAPPLICABLE-BY-CONSTRUCTION
- **What it is:** NetManAIOps/TraceAnomaly, ISSRE'20 — unsupervised trace anomaly detection via a
  service-level deep Bayesian network with posterior flow. Two-stage: (1) a manually-maintained
  whitelist flags *previously-unseen call paths* as functional anomalies; (2) otherwise the Bayesian
  model scores the trace's likelihood (latency vector) and flags low-likelihood traces as
  **performance** anomalies.
- **Operability:** Python 3.6; `pip install -r requirements.txt`; `./run.sh`; a prebuilt Docker image
  `silence1990/docker_for_traceanomaly:latest`; ships a TrainTicket corpus (`train.zip` +
  `test_normal.zip` + `test_abnormal.zip`). It IS runnable — but on its own trace format, requiring a
  normal-behavior training corpus per SUT.
- **Applicability to our fault class — NEGATIVE by construction:** the masked / acked-but-lost class
  (esp. the fabricated-ack constructed cases and the trace-invisible subset) produces traces whose
  **structure and latencies look normal** — the call still happens (or the fake ack replaces it) with
  ordinary timing. Stage (1) whitelist keys on *new* paths, not *lost* durable state; stage (2)
  models latency, not persistence. So TraceAnomaly is structurally blind to silent state loss. It
  MIGHT register the natural mesh-sever variant (an aborted downstream call → error span / altered
  latency / a changed path) — but that is precisely the *error-span-visible* case the naive oracle
  (arm 1) already catches, so it adds no frontier coverage there either.

## Implications for E2 + the plan (folded as disclosed reasoning)
1. **Frontier = arms 1–3.** naive span-error, Tracetest span-error, Tracetest span-PRESENCE are the
   three trace-aware comparators on the E2 frontier. Gate-4 accounting (plan §1) holds at **3 frontier
   comparators**; if arm 4 does not tune into a matched-recall competitor (expected), the "≥4
   baselines" claim narrows honestly to "3 frontier trace comparators + a construction-blindness
   result," disclosed in the deviations ledger. Still ≥3 → above the stop-and-replan floor.
2. **Arm 4 re-scoped to a demonstration, not a competitor.** Its honest, still-valuable role: feed it
   S1 masked-write traces and show a published SOTA trace-AD tool does NOT flag them because the
   traces are normal-shaped — a construction-blindness result that STRENGTHENS the class-novelty
   framing. This is a step-2.5/step-6 confirmation run, not a step-1 blocker.
3. **Per-visibility-class reporting (plan §4 E2) is vindicated as necessary and now mechanized:**
   *error-span-visible* → even naive (arm 1) catches; *span-presence-visible* → arm 3's
   `selected_spans.count = 0` catches, arms 1/4 do not; *trace-invisible* → NO trace comparator can
   catch (fabricated-ack with a normal span, or an un-traced write) → its own disclosed N-vs-0 row.
   The visibility class is a frozen per-case schema field (`c2-freeze.md` §2) → this reporting is
   already machine-supported.
4. **Sequencing unchanged + de-risked:** the ≤2-day go/no-go CLEARS on operability + applicability
   from artifacts; the only open item is the mechanical smoke-installs, correctly deferred to step 2.5
   (they need instrumented traces to consume).

## Deferred to the step-2.5 instrumentation window (the actual install)
- Deploy Tracetest Agent + an OTLP collector on the kind cluster; smoke arm 2 + arm 3 against
  **Bookinfo** first (already Jaeger-traced → no new instrumentation needed) to validate the
  `selected_spans.count = 0` absence assertion end-to-end on a real trace.
- Run the TraceAnomaly Docker image on captured S1 masked-write traces (converted to its format) to
  turn the by-construction blindness argument into an empirical row.
- Record both under step-6 E2 with per-visibility-class recall.

## Sources
- Tracetest: [docs — configure trace ingestion](https://docs.tracetest.io/getting-started/configure-trace-ingestion),
  [assertions](https://docs.tracetest.io/concepts/assertions),
  [selectors](https://docs.tracetest.io/concepts/selectors),
  [span-count assertion usage](https://oneuptime.com/blog/post/2026-02-06-assertion-rules-span-attributes-tracetest/view),
  [repo](https://github.com/kubeshop/tracetest).
- TraceAnomaly: [NetManAIOps/TraceAnomaly](https://github.com/NetManAIOps/TraceAnomaly),
  [ISSRE'20 paper](https://netman.aiops.org/wp-content/uploads/2020/09/%E5%88%98%E5%B9%B3issre.pdf).
