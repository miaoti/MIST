# G3 SUT-2 head-to-head — Sock Shop `shipping` acked-but-lost enqueue (RESULT OF RECORD)

MIST's differential value-delta STATE oracle vs a FROZEN blind response-assertion comparator, on a
SECOND SUT (Sock Shop) and a DIFFERENT integrity hazard class (a lost message-queue enqueue) — the
external-validity complement to the TrainTicket cancel→refund head-to-head.

**Status:** result 3-cold-reviewed (empirical soundness / fairness / claim+composition — all
ACCEPT-WITH-CHANGES, zero BLOCKING); every disposition folded here or in
`REVIEW-SHIPPING-HARNESS-RECONCILIATION.md` §RESULT REVIEWS. Raw run logs committed under `runs/`.

## Scope provenance (disclosed pivot — reviews B-MAJOR-3 / C-M1)
SUT-2 was earlier scoped "FP/breadth + wild-hunt only" because the CARTS write path honestly 5xxes on
backend failure (branch β — no constructed sensitivity THERE). The shipping enqueue-swallow, found by
the wild-hunt, was then deliberately PROMOTED to this depth head-to-head: shipping is a different
service whose loss is acked (201) rather than honest, which is exactly the hazard MIST targets. β's
carts finding is unchanged (addenda in `prep/g3-sut2-fp-probe-result.md` + `prep/g3-sut2-deploy-verify.md`);
shipping is banked as DEPTH (a second independent SUT), never double-counted as breadth.

## The defect (natural, self-documented — no fork, no image edit)
`weaveworksdemos/shipping:0.4.8` `ShippingController.postShipping` wraps `convertAndSend("shipping-task", …)`
in a try/catch that logs *"Accepting anyway. Don't do this for real!"* and returns **HTTP 201 unconditionally**.
The acked-but-lost hazard ships in the UNMODIFIED upstream image, and both fault mechanisms below are
purely operational (mesh policy / broker policy) — unlike TT's constructed cell, no source flag is
involved anywhere in this experiment (review C-m5).

## Two fault strata + a benign control (× two comparator forms)
- **natural** — a surgical shipping↛broker sever: an Istio L4 AuthorizationPolicy denies shipping→rabbitmq:5672
  **plus** a `rabbitmqctl close_all_connections` so the cached connection reconnects INTO the block (verified
  live: `/health` flips `shipping-rabbitmq` to "err" in ~1s, HTTP stays 200). POST still 201 + the enqueue is
  lost. With the connection down, `convertAndSend` fails at connection establishment → the catch path (the code
  swallow) produces the 201; we cite the mechanism, not the container log line, which was not captured
  (review A-MINOR-3). Because `/health` degrades, a liveness-aware comparator CATCHES the outage.
- **constructed** — a `max-length:1` / `overflow:reject-publish` queue policy: the broker REJECTS the publish
  while the connection stays live, so `/health` reads **GREEN**. POST still 201. Note the loss mechanism here
  is a broker-side silent drop: shipping publishes without publisher confirms, so the rejection is never
  delivered back and the code swallow does NOT fire — the blindness is *fire-and-forget-without-confirms*
  (reviews B-MINOR-5/A-MINOR-3). `max-length:1` is a test ACCELERANT for the reject-publish SEMANTICS (the
  realistic class: disk alarms, quotas, poison-message policies — which bite at any limit); applying it to a
  non-empty queue trims it to 1 on apply, which is why fault-leg baselines read 1 (review B-MINOR-6). The
  fault was deliberately chosen to exhibit a green-liveness loss; `requireRejectWillBite` (consumers==0 ∧
  depth≥max-length, gated before every fault leg) is the evidence the publish is *rejected*, not drained.
- **benign (specificity control, review C-M3)** — `NoOpFault`: no fault at all; both legs' enqueues land.
  Shows the oracle does not cry wolf on this exact queue-depth read-back (the earlier SS FP probe exercised a
  different endpoint/read-back/mode and cannot stand in for this).
- **comparator forms:** (i) **as-frozen** = the blind author's sound output (HTTP_STATUS 201 only; the id/name
  echo, the enqueue effect, and `/health` liveness were all NOT_CHECKABLE in the closed primitive set); (ii)
  **P2-amended** = the `/health` clause bound via the `contains-literal-fields` liveness primitive the blind
  author specified (disclosed, pre-run; byte-identity of the as-frozen file to git `41ff9ac~1` was
  reviewer-verified). Reporting both proves the amendment only made MIST's story HARDER, never manufactured it.

## Oracles + config
- **MIST**: value-delta on the `shipping-task` queue depth (RabbitMQ mgmt `GET /api/queues/%2f`,
  match_field=name, value_field=messages), read-back routed off-SUT via `ShippingReadbackHttp` (a dedicated
  broker-admin user). FIRE = the fault leg acks X (201) yet its own read-back never shows the +1 while
  control's does. **This is a COUNT-delta on a durable sink** — the queue row always exists, so membership is
  vacuous and the unit depth increment is the only detector; it demonstrates durable-sink BINDING breadth, and
  deliberately does NOT re-claim TT's arithmetic-magnitude power (review C-M2).
- **comparator**: the frozen contract via `ContractEvaluator` (HTTP_STATUS + P2 liveness), `/health` over the SUT.
- queue-master scaled to 0 — a disclosed observability rider (review B-MINOR-7): the loss is real regardless of
  the consumer; qm=0 is what makes depth monotonic and the loss *measurable*. Oracle timeout 20 s ≫ the ~5 s
  mgmt stats-DB sampling (default `collect_statistics_interval` 5000 ms on rabbitmq:3.8.34-management — pin on
  re-deploy, review A-MINOR-4); comparator retry cap 3 s. Driven kind cluster: no order/front-end load during
  runs, so the harness is the only publisher (review A-MINOR-2). kind "mist"; rabbitmq 3.8.34.

## Results

**How to read the table (reviews B-MAJOR-2 / C-m9):** the comparator-form axis does not multiply MIST's
evidence — there are TWO fault phenomena (natural, constructed) plus a benign control. The two as-frozen rows
are METHODOLOGICAL CONTROLS, analytically forced (HTTP_STATUS 201 cannot fail on a swallow that always
returns 201), not additional MIST wins. The headline is ONE existence-proof clean-win cell
(constructed × P2-amended) and ONE diagnosis-gap cell (natural × P2-amended) — never a "×3 sweep".

| stratum | comparator form | MIST | comparator | reading |
|---|---|---|---|---|
| natural | P2-amended | **FIRE** 5/5 | **CAUGHT** (control=F, fault=T) 5/5 | diagnosis gap — BOTH detect; comparator sees the outage, MIST adds per-write localization |
| constructed | P2-amended | **FIRE** 5/5 | **MISSED** (F, F) 5/5 | **the clean-win cell** — /health green under reject-publish |
| natural | as-frozen (control row) | **FIRE** 5/5 | MISSED (F, F) 5/5 | forced: a 201-only contract cannot fail |
| constructed | as-frozen (control row) | **FIRE** 5/5 | MISSED (F, F) 5/5 | forced: same |
| benign (no fault) | P2-amended | **NO_FIRE** | no flag on either leg | specificity control — both legs land (1→2, 2→3) |

Per-cell pilot detail (depth = shipping-task `messages`, own-baseline value-delta; every MIST FIRE via a
unique correlator join, `body status null`; raw logs: `runs/shipping-h2h-*.txt`):

```
P2  natural     : comparator control=F fault=T -> CAUGHT   depth control 4->5 (present) | fault 5->5 (lost)
P2  constructed : comparator control=F fault=F -> MISSED   depth control 5->6 (present) | fault 1->1 (lost)
frz natural     : comparator control=F fault=F -> MISSED   depth control 1->2 (present) | fault 2->2 (lost)
frz constructed : comparator control=F fault=F -> MISSED   depth control 2->3 (present) | fault 1->1 (lost)
benign          : comparator control=F fault=F (nothing to catch)   depth 1->2 and 2->3 (BOTH present)
```
(The benign row's raw log prints the label "MISSED" — cosmetic reuse of the cell printer; in a no-fault
stratum there is nothing to catch, and the datum is that neither leg flags and MIST does NOT fire.)

**Absence-evidence class (review A-M1):** every FIRE's absence gate resolved `TIMEOUT_ABSENT` (~40 polls over
20 s); the trace-corroborated `OBSERVED_COMPLETE_ABSENT` gate is unreachable here (no traceId wired). SS
absence is instead **fault-corroborated**: the sever/reject was independently verified live before each fault
leg (`/health`=err converge-gate; `requireRejectWillBite`). Do not borrow TT's trace-corroborated language.

## Ground-truth corroboration (review A's single-confound ask — `runs/shipping-h2h-benign-corrob-*.txt`)
The one datum every MIST verdict rests on is the mgmt `messages` count, which has a known ~5 s sampling lag.
One rep per mechanism was corroborated against the broker's DIRECT, non-lagging source
(`rabbitmqctl list_queues`):

| mechanism | mgmt (stats DB) | direct (queue process) | agree? |
|---|---|---|---|
| LANDED (plain POST, 9 s settle) | 1→2 | 1→2 | ✔ both +1 |
| NATURAL-LOST (sever) | 2→2 | 2→2 | ✔ both unchanged |
| CONSTRUCTED-LOST (reject-publish) | baseline **2 (stale)** → 1 | **1→1 (truth)** | ✔ loss real; the stale mgmt baseline directly EXHIBITS the ~5 s lag converging to truth |

The lag is now measured, not assumed — and its oracle failure direction is conservative by construction: a
stale/moving baseline yields NO_FIRE or the loud baseline-stability error, never a false FIRE (reviewer A
verified this path independently). Landings were visible at the first settled read (9 s ≪ the 20 s cap),
covering the control-leg margin question (A-MINOR-1). Cross-leg settle (A-MAJOR-2) is discharged by
measurement: every rep shows depth continuity (natural fault baseline == control final, e.g. 1→2 then 2→2),
and a control-leg bleed into the fault baseline would have broken the 14/14 FIRE record — it never did
(inject()'s own converge round-trips exceed the stats interval).

## N=5 stability — deterministic, and reproduced across a cluster restart
P2-amended ×5 (`runs/shipping-h2h-reps-n5-*.txt`) + as-frozen ×5 (2 pre-reboot + 3 POST-REBOOT on a fresh
broker state: re-created admin user, re-declared queue — `runs/shipping-h2h-asfrozen-reps3-5-*.txt`):
every rep categorically identical, MIST FIRE on **14/14 pre-reboot + 6/6 post-reboot fault legs**, zero
variance. Determinism here is STRUCTURAL, not statistical (review A-MAJOR-3): reps share the self-resetting
monotonic queue and re-observe one mechanism (the swallow always 201s; sever/reject always lose the write) —
the honest claim is "the mechanism reproduces, including across a full host reboot," not "N independent trials."

## Reading the result
- **MIST FIREs on every fault leg and NO_FIREs on the benign control** — the differential can only fire on a
  real control-present ∧ fault-absent split, shown on this exact oracle, not asserted (review C-M3).
- **The as-frozen (sound blind) comparator misses both strata** — analytically forced; its role is provenance
  (the blind author's strongest SOUND contract binds only HTTP_STATUS 201), not a win tally.
- **The P2-strengthened comparator closes ONLY the natural cell and STILL misses the constructed cell** — the
  clean win is robust to in-class strengthening: the strongest liveness check a response-contract checker
  affords does not close it; only out-of-class broker/queue-state observation does, which is MIST's thesis.
- **Dual-form discharged:** P2 moved natural MISSED→CAUGHT — strengthening the baseline made MIST's story
  HARDER (turned a would-be clean win into an honest diagnosis gap), never manufactured a win.

## Framing rules (reviewer-mandated, binding for the paper)
- **Natural = a diagnosis gap, honestly a tie at binary detection granularity** (review B-MINOR-4): both
  oracles flag the fault leg. The comparator's flag is a genuine in-body liveness FAIL (`/health` HTTP 200,
  `shipping-rabbitmq=err`, transport-reclassification-guarded). Under a broker-WIDE outage, MIST's per-write
  localization is a MODEST diagnostic refinement — MIST's DECISIVE win is the constructed cell, and the paper
  must not present natural as a MIST detection win.
- **Class-scope the constructed win**: "the strongest fair single-endpoint response+liveness contract-checker
  (Pact/Dredd/synthetic-monitoring shape) misses it" — never "no diligent engineer could." The rebuttal "add
  queue-depth monitoring" CONCEDES the thesis (catching this class needs out-of-class broker/queue state = MIST).
- **Protocol, not personhood**: the anti-gaming guarantee is freeze-before-reveal (primary-sources-only, no
  MIST internals, git-frozen before reveal, P2 specified inside the frozen notes), not an org-separate human.
- **Generalization axes (review C-m4):** the differential value-delta oracle held across (a) two
  independently-built systems, (b) two hazard classes — synchronous DB compensation (TT cancel→refund) and
  asynchronous MQ enqueue (SS shipping), (c) two durable-sink types — an arithmetic account balance and a
  broker queue count — while the response+liveness contract class missed (constructed) or only coarsely
  detected (natural) both.

## Threats to validity (review C-m6)
- The constructed fault is an injected representative of the green-liveness-loss class (reject-publish
  semantics under disk-alarm/quota/poison policies), accelerated by max-length:1; it does not exercise the
  code swallow (broker-side drop without confirms). The natural stratum exercises the swallow.
- The defect is self-documented in the upstream image — but it is REAL shipped code, un-modified, and the
  faults are operational only; "authors picked a documented bug" concedes the bug class exists in the wild.
- The observable is a unit count on an always-existing row (durable-sink binding breadth; no arithmetic
  magnitude claim). qm→0 is required to make the loss observable; the loss itself is consumer-independent.
- N is small and reps are structurally coupled (one mechanism re-observed, deterministically — including
  across a host reboot); as-frozen misses are analytically forced, hence controls.
- The mgmt read-back's ~5 s lag is measured and conservative-direction; the oracle's 20 s cap gives 4×
  headroom; broker restarts reset mgmt users/queues (runbook: re-create the admin user + one warm-up POST).

## Bottom line
On a second, independently-built SUT and a different integrity-hazard class (an asynchronous message-queue
enqueue), MIST's contract-independent count-delta oracle catches an acked-but-lost write that the strongest
FAIR response+liveness contract-checker misses cleanly (constructed cell) or detects only as a coarse
service-wide outage (natural cell, where MIST adds per-write localization) — with a live benign control
showing the same oracle stays silent when nothing is lost, ground-truth corroboration of the queue-depth
datum, and dual-form reporting proving the comparator strengthening only made MIST's task harder. This is
the external-validity complement to the TrainTicket cancel→refund centerpiece.
