# Wild-hunt plan v1 — 3-cold-review reconciliation (→ drives plan v2)

Plan under review: `debug/a-main/g3-sut2-wildhunt-plan.md` v1 (commit 081fdf5). Three independent cold
reviewers (skeptical-PC framing on one). All verified the bytecode (javap on the deployed 0.4.8 jar) and the
live cluster themselves.

## Verdicts
- **Reviewer A: ACCEPT-WITH-CHANGES** (1 BLOCKING /health + 3 MAJOR + factual corrections).
- **Reviewer B: ACCEPT-WITH-CHANGES** (2 MAJOR: /health, scale-to-0; + factual corrections).
- **Reviewer C: ACCEPT-WITH-CHANGES** (3 MAJOR: /health, R3↔R4 tension, novelty over-claim).

Net: **plan is sound and worth executing (NOT kill-switch)** — the defect is real, the env supports it, a
surgical fault is feasible — but the "clean structural head-to-head win" framing is refuted as written and
must be reframed. All fixes are folded into **plan v2**.

## Factual claims — adjudicated
- **Defect bytecode: CONFIRMED verbatim by all three.** `postShipping` try/catch(Exception) around
  `convertAndSend("shipping-task",…)`, verbatim "Accepting anyway. Don't do this for real!" log,
  UNCONDITIONAL return. `getShippingById`/`getShipping` are pure stubs (live-confirmed 200 + literal echo).
- **CORRECTION (A): the ack is HTTP 201 CREATED** (`@ResponseStatus(HttpStatus.CREATED)`), not 200. v1 says
  200 in §0/§1/§3/§4/§5. MIST's 2xx predicate is unaffected; the prose/criteria were literally wrong.
- **CORRECTION (A+B+C): `GET /health` is NOT absent — it live-probes rabbitmq** (`rabbitTemplate.execute →
  getConnection().getServerProperties()`, catches `AmqpException` → `setStatus("err")`). So the service DOES
  expose an app-API signal that reflects broker connectivity. v1 §1/§6-R3 ("no app-level state / no clause
  over the service's own API can catch this") is FALSE as written. This is the load-bearing finding.
- **CORRECTION (A+B): the read-back channel is far more fragile than v1 claims.** Exporter is on **9419 not
  9090** and is **fully broken** (`rabbitmq_module_up=0` for every module; no queue metrics). `/api/overview`
  returns a persistent **HTTP 500** (stats DB partially unhealthy). `message_stats.publish` is **absent**
  from the idle queue JSON. The ONLY working HTTP observable is `messages` (depth) via
  `/api/queues/%2f/shipping-task`, and it is only usable with **queue-master scaled to 0** (consumer drains
  live otherwise). Mgmt API answers with `guest:guest` and `loopback_users=[]` (remote read permitted).
  The mgmt depth read is **stats-DB-sampled (~5 s in 3.6.8)** → lags a just-completed publish.
- **CONTROL confirmed (A+B): the fault does NOT break MIST's read-back** — MIST reads 15672 (a separate
  listener) while the fault severs 5672; distinct ports; mgmt API answered throughout. Keep as an explicit
  control in the write-up.

## Triply/doubly-convergent findings → disposition in v2
1. **/health confound (A BLOCKING, B+C MAJOR) — the connection-sever fault trips `/health`→err, so a blind
   comparator with a `/health` clause discriminates the legs; "comparator PASSes both legs" (§5.3) fails.**
   → v2: adopt the **TT-parity two-stratum design**. (S1) natural self-documented sever → `/health` err →
   **diagnosis-gap tie** (comparator flags the OUTAGE via liveness; only MIST localizes the specific
   acked-but-lost POST /shipping write) — mirrors the accepted TT *natural* stratum. (S2) a `/health`-green
   loss (revoke publish permission / unroutable / queue max-length-0) → comparator PASSes both legs even WITH
   a `/health` clause → **clean MIST win** — mirrors the accepted TT *constructed* stratum. The comparator is
   authored WITH a `/health` liveness clause (maximally fair/adversarial-to-MIST), and the outcome is
   reported honestly per stratum.
2. **R3↔R4 tension (C) — the self-documented catch only fires on a real connection failure, which is exactly
   what `/health` sees.** → v2: owned explicitly; it is WHY the two strata exist. Empirically test in step 2
   whether "revoke publish permission" can fire the swallow (publish denied throws) while `/health`
   (connection-level) stays green — if so, S2 also carries the self-documented narrative; if the denial is a
   silent async channel-close, S2 is a clean loss without the log (still a clean win). Resolve by experiment,
   not on paper.
3. **Read-back fragility (A+B MAJOR).** → v2: single channel = mgmt-API depth + `queue-master`→0; DROP the
   exporter and `message_stats.publish` claims; treat the overview-500 stats-DB health as a live risk to
   validate before the run; ground-truth EVERY reading against `rabbitmqctl` (authoritative).
4. **R1 fairness rests on SUT doctoring (A+B+C).** → v2: disclose (a) depth+consumer-off as a **demo-only
   proxy** for the (unavailable) monotonic publish counter — a test-harness intervention, not a production
   path; (b) out-of-band topology knowledge (queue name, creds) as a configured assumption; (c) the no-op
   consumer as a degenerate instance (in a consumer-with-effect system MIST would read the consumer's durable
   effect). State the general capability = "read the write's durable-effect sink"; do NOT imply auto-binding
   to broker queues.
5. **R5 async second lag (A+B).** → v2: the publish is sync-to-broker (disclaimer doesn't fatally bite), but
   the HTTP depth read lags ~5 s via the stats emitter → reading too early on CONTROL → delta 0 → false FIRE.
   Require quiescence/poll ≫ stats interval and validate depth freshness against `rabbitmqctl` at read time.
6. **R6 novelty over-claim (A+B+C).** → v2: drop "previously-unflagged" (dev flagged it in a log) and "finds
   real bugs in the wild" (intentional demo shortcut). Reframe as: a real-world **anti-pattern class**
   (fire-and-forget enqueue without landing confirmation), naturally instantiated + self-documented in a
   standard benchmark, that a write-boundary contract oracle structurally cannot localize. It is a **rider**
   generalizing the accepted TT result along three axes (sync→async, SUT1→SUT2, constructed→natural), not a
   centerpiece.
7. **Fault mechanics (A MINOR).** → v2: use a fail-fast Envoy **abort/RST** (not black-hole drop) so the POST
   returns 201 promptly; on the fault leg confirm BOTH the "Accepting anyway…" shipping log (substantiates
   self-documented) AND low latency + 201 (substantiates acked).
8. **HTTP 201 not 200 (A).** → v2: corrected throughout.
9. **`orders` amplifier unverified (A+B NIT).** → v2: kept clearly-optional + marked unverified.

## Framing decision
**Option A (two-stratum head-to-head).** A+B recommend A; C leaned B (demo) only because the single-cell
clean win is fragile — the two-stratum design removes that fragility (S1 is the honest tie, S2 is the clean
win), so A dominates B and preserves the strong structural-miss story where it is real (S2). Kill-switch (C)
remains named: trigger only if BOTH (a) no `/health`-green loss is surgically injectable AND (b) the
diagnosis-gap reframe of S1 is judged insufficient.
