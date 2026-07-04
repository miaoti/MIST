# SUT-2 wild-hunt — execution progress (plan v3, commit 9563c6f)

Tracks execution of the reviewer-accepted plan `debug/a-main/g3-sut2-wildhunt-plan.md`. Live experiment
phase; cluster kind `mist` ns `sock-shop` (14/14 up). Host kubectl + KUBECONFIG per memory; MSYS_NO_PATHCONV=1
for `pod:/path`. All mutations reversible + reverts recorded here.

## Step 1 + Step 3 de-risk — MIST wiring feasibility: RESOLVED (read-only, no mutation)
The #1 execution unknown was "can MIST express the shipping read-back" (a scalar `messages` on a DIFFERENT
host `rabbitmq:15672` with BASIC auth, unlike TT's same-SUT value_probe). Investigated the read-back surface:
- **Read-back extraction fits UNCHANGED.** `DataIntegrityRuntime.extractProbeValue` (796–814) needs a
  COLLECTION whose row matches `match_field` and carries `value_field`. Verified live: the mgmt LIST endpoint
  `GET /api/queues/%2f` returns a JSON **array**; the `shipping-task` element has `name` + `messages`
  (integer). → triple config: `readback_endpoint: "GET /api/queues/%2f"`, `readback_mode: value-delta`,
  `value_probe{match_field: name, value_field: messages}`, supplied key `name=shipping-task`. X = "messages
  differs from this leg's own baseline" (BigDecimal-aware `valueDiffers`). Control: depth +1 → differs →
  PRESENT. Fault: depth +0 → same → ABSENT → FIRE. (`parsesToCollection`/`extractItems` accept a bare array
  per the HAL work.)
- **Different host + basic auth** is NOT expressible via the config path (`s.http.getSut` uses the SUT base
  URI + SUT auth). BUT the `Http` seam is overridable (`DataIntegrityRuntime.defaultHttpOverride`, line 248):
  a focused harness injects a custom `Http` whose read-back GET targets `rabbitmq:15672` with `guest:guest`
  basic auth. This is EXACTLY the accepted g3 pattern (`CancelRefundHeadToHead`): reuse the reviewed oracle
  LOGIC (`PairedFaultExecutor.evaluate` MIST verdict + `ContractEvaluator.evaluate` comparator), the driver
  owns its own I/O + fault mechanism, one SUT-specific `Stimulus` boundary.
- **Conclusion:** the wild-hunt is executable with **NO change to the reviewed oracle** — only a NEW g3-style
  harness `io.mist.cli.g3.ShippingEnqueueHeadToHead` (+ custom Http + `Stimulus` = POST /shipping + the fault
  injectors). New code → unit test + ≥3-cold-review before any claim (standing rule), but the load-bearing
  oracle stays reviewed-verbatim. Low-risk path.
- Note: MIST-from-host (as the g3 head-to-head ran) needs port-forwards to `shipping:80` (write) AND
  `rabbitmq:15672` (read-back); in-cluster would use service DNS. `rabbitmqadmin`/`curl`/`wget` are ABSENT
  from the rabbitmq container → use a host port-forward + host curl for manual checks, `rabbitmqctl` in-pod
  for ground-truth.

## Step 2 — live fault verification (IN PROGRESS)
Goal: empirically confirm the defect + select the S2 fault (reviewers deferred S2 selection to here).
Scaffold: `queue-master` scaled 0 (revert=1); depth read via `rabbitmqctl list_queues` (authoritative/instant).

**CONTROL — VERIFIED (2026-07-04):** POST `/shipping` `{id,name}` → **HTTP 201** + Shipment echo; `/health`
green (shipping-rabbitmq OK); depth **0→1** (message lands, consumer off). The write-lands baseline holds.

**S2 (/health-green clean-win) — the reviewers' recommended queue-policy path FAILS on this broker (NEW
constraint finding):**
- `{"max-length":0,...}` → `rabbitmqctl set_policy` **exit 70 (rejected)**.
- `{"overflow":"reject-publish"}` → **exit 70** — `overflow` is a **RabbitMQ 3.7+** feature; the deployed
  broker is **3.6.8**, so overflow/reject-publish is UNAVAILABLE. Plain `max-length` on 3.6.8 only does
  `drop-head` (keeps the NEWEST, drops the oldest) → the measured write LANDS → UNSOUND for S2 (a depth-delta
  would falsely read 0 while the write actually landed = false FIRE). So max-length is out.
- **Permission-revoke** (`set_permissions guest '.*' '.+' '.*'`, write `.+` excludes the empty default-exchange
  name) applied cleanly BUT **did NOT bite the running shipping** — POST still landed (depth 3→4), no
  "Accepting anyway" log. Confirms reviewer B's channel-auth-caching caveat: the live shipping AMQP channel
  cached its write authorization; a mid-flight permission change is not re-checked. Reverted to `.* .* .*`.
- Forced reconnect attempts also failed on 3.6.8: `close_all_connections` is not a 3.6.8 rabbitmqctl command
  (exit 64); even a full `rollout restart` of shipping under revoked write STILL published (depth 6→7) —
  because the default-exchange write-check is effectively on the QUEUE name (`.+` matches `shipping-task`),
  and a regex that denies `shipping-task` also breaks shipping's startup queue/binding declaration. Dead end.
- `{"message-ttl":0}` policy APPLIES cleanly (valid on 3.6.8) but did NOT drop messages — RabbitMQ expires
  lazily and, with the consumer off, queued messages don't reach the head to expire. Inconclusive (that POST
  also hit a port-forward blip, HTTP 000).
- **ROOT CAUSE: RabbitMQ 3.6.8 (what sock-shop ships) lacks a clean publish-drop primitive.**
  `overflow:reject-publish` is 3.7+; `max-length` alone is unsound drop-head; permission + message-ttl paths
  are blocked as above.

**DECISION (2026-07-04): upgrade the rabbitmq broker to a version with `overflow:reject-publish` (≥3.7,
targeting 3.8-management).** Rationale: the shipping enqueue-swallow defect is BROKER-INDEPENDENT (it lives in
`shipping:0.4.8`), so upgrading the broker does not touch the defect or shipping's behavior; it only gives a
clean, deterministic, `/health`-green S2 fault (`{"max-length":1,"overflow":"reject-publish"}` with the queue
already ≥1 → new publishes rejected → 201 + depth unchanged + `/health` green). Disclosed as an execution
choice + deviation from plan v3's 3.6.8 assumption; strictly improves rigor (may also heal the degraded stats
DB that forced the depth+scaffold read-back). The non-durable `shipping-task` queue is lost on broker restart
and redeclared by shipping on reconnect (queue-master stays at 0). Revert = restore the original image.

**S1 (natural stratum) — VERIFIED (2026-07-04).** Scaled rabbitmq→0 (broker down = exactly the scenario the
swallow's comment names), POST `/shipping` → **HTTP 201** + Shipment echo (fabricated ack) while the message is
lost (broker down); `/health` → **`shipping-rabbitmq: "err"`** (shipping: OK). Restored rabbitmq→1 (shipping
reconnects, queue redeclared). So the natural stratum holds live: acked (201) + lost + `/health` err → MIST
would FIRE (acked-but-lost, localizing the specific POST /shipping write); a `/health`-aware comparator catches
the OUTAGE but not the write-loss = diagnosis-gap. (The "Accepting anyway…" stdout line wasn't in the tail
window this run — behavior confirmed regardless; re-grep logs during the harness run for the narrative.)
NOTE: for the real MIST run S1 needs a SURGICAL sever (shipping↛rabbitmq:5672 while the 15672 read-back stays
up) — scaling rabbitmq→0 was only the swallow-verification. kind's default CNI doesn't enforce NetworkPolicy →
use an Istio L4 AuthorizationPolicy/EnvoyFilter (both pods have sidecars); to be built in the harness phase.

**S2 (clean win) — needs the broker upgrade** (per the DECISION above). rabbitmq has no volumes/env → clean
image swap; will add a dedicated `mist` admin user for the read-back (3.7+ restricts `guest` to loopback).

Cluster state: rabbitmq→1 (up), shipping healthy, queue-master→0 (scaffold), guest perms `.* .* .*`, no
policies. Next: broker upgrade + control re-verify + S2 reject-publish; then build the g3-style harness.

## Step 2 — COMPLETE: broker upgraded, BOTH strata + read-back empirically verified (2026-07-04)
**Broker upgrade 3.6.8 → 3.8.34 DONE.** `kubectl set image deploy/rabbitmq rabbitmq=rabbitmq:3.8-management`;
the 3.8 entrypoint needs to write `/etc/rabbitmq/rabbitmq.conf` but the container had
`securityContext.readOnlyRootFilesystem:true` (sock-shop hardening) → CrashLoopBackOff. FIX: patched
`readOnlyRootFilesystem:false` on the rabbitmq container (revert: true) → 3.8.34 booted 3/3. The old 3.6.8 pod
served throughout the rolling update (no outage). shipping auto-reconnected + redeclared the non-durable
`shipping-task` queue (/health OK). Added a dedicated read-back user **`mist`/`mist` (administrator, `.* .* .*`)**
because 3.7+ restricts `guest` to loopback (the mgmt read-back is remote).

- **CONTROL on 3.8 — VERIFIED:** POST /shipping → 201, depth 0→1.
- **S2 (clean-win) on 3.8 — VERIFIED:** policy `{"max-length":1,"overflow":"reject-publish"}` applies cleanly
  (3.8 supports `overflow`; queue already ≥1 → new publishes rejected) → POST /shipping → **HTTP 201 + depth
  unchanged (message lost) + `/health` GREEN**. So a `/health`-aware comparator PASSES both legs → clean MIST
  win. This is the constructed-realistic stratum, live-confirmed. Reverted (cleared ship-drop policy).
- **READ-BACK on 3.8 — VERIFIED:** `GET /api/queues/%2f` via **mist:mist** → HTTP 200, JSON array with
  `{name:"shipping-task", messages:N}`. The value-delta extraction (match_field=name→shipping-task,
  value_field=messages) binds unchanged. IMPORTANT: the rabbitmq SERVICE exposes only 5672+9090 (NOT 15672) →
  the mgmt read-back is reachable only via the **pod** (port-forward pod:15672 for host-run MIST, or add 15672
  to the svc / use pod IP in-cluster). guest is now loopback-only → the harness read-back MUST use mist:mist.

**Both fault strata + control + read-back are empirically confirmed. The plan's experimental feasibility is
fully validated.** S1 (natural): broker-down → 201 + lost + /health err (diagnosis-gap; verified on 3.6.8, the
shipping swallow is broker-version-independent — re-confirm on 3.8 with the surgical sever during the run). S2
(clean win): reject-publish → 201 + lost + /health green (verified on 3.8).

**Standing cluster state (for the run):** rabbitmq 3.8.34 (readOnlyRootFilesystem=false, mist:mist admin user),
shipping healthy, queue-master→0 (scaffold), guest `.* .* .*`, no policies. END-OF-EXPERIMENT REVERTS: image→
rabbitmq:3.6.8-management, readOnlyRootFilesystem→true, delete mist user, queue-master→1. S1 surgical sever
(shipping↛rabbitmq:5672, 15672 read-back stays up) via Istio L4 AuthorizationPolicy/EnvoyFilter = to build.

## Step 3 — NEXT: build the g3-style harness `io.mist.cli.g3.ShippingEnqueueHeadToHead`
Reuse PairedFaultExecutor.evaluate (MIST) + ContractEvaluator.evaluate (comparator); custom Http override
(defaultHttpOverride) routing the read-back GET to pod:15672 with mist:mist basic auth; Stimulus = POST
/shipping {id,name}; injectors = S1 Istio-sever + S2 reject-publish-policy. Value-delta triple: readback "GET
/api/queues/%2f", match_field=name (supplied key shipping-task), value_field=messages. Unit test + ≥3-review
before any claim (new tool code, standing rule); oracle stays reviewed-verbatim.
