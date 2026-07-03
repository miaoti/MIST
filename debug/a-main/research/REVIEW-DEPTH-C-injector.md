# REVIEW-DEPTH-C — IstioRouteFaultInjector (commit 7453142)

Cold review (reviewer C, no prior context) of commit `7453142eb8be45e231b5d155289b5cdba769f7f5`
"feat(pairing): IstioRouteFaultInjector — route-scoped abort backend for the G3 natural stratum".

Scope read: `IstioRouteFaultInjector.java` (+208), `SutFlagFaultInjector.java` (runProcess
private→package), `IstioRouteFaultInjectorTest.java` (+210), `FaultInjector.java` (contract),
`PairedFaultExecutor.java` (orchestration), FILE_INDEX rows. `mvn test -Dtest=IstioRouteFaultInjectorTest`
re-run locally: green (exit 0).

**Verdict up front: ACCEPT-WITH-FIXES.** No soundness-breaking code bug found. The load-bearing
design decisions (probe-gated convergence in both directions, -1 neutrality, ignore-not-found
idempotent clear, throw-on-timeout) are correct and correctly biased. The fixes are one
config/doc-level hazard that genuinely matters (abort-status collision, F1), one wrong comment
(F2), and small polish items.

---

## 1. awaitProbe boundary conditions

**F1a — first-poll convergence: CORRECT (confirmed).** `convergeTimeoutSeconds >= 1` guarantees
the first `System.nanoTime() < deadline` check passes, the probe runs before any sleep, and a
converged first poll returns with zero sleeps. This is exactly what the executor's hygiene
clear pass needs (see §3/F5).

**F1b — `last == Integer.MIN_VALUE` ("nothing") branch: effectively dead code, reachable only
via nanoTime overflow (PLAUSIBLE, theoretical).** The loop body always runs at least once for
any sane timeout, so `last` is always assigned. The one reachable path: an absurd
`convergeTimeoutSeconds` (~292 years) makes `deadline = nanoTime() + toNanos(...)` overflow
negative, the while-check fails immediately, and the method throws "last returned nothing".
The idiomatic overflow-safe comparison is `System.nanoTime() - deadline < 0`. Zero practical
risk; nit-level.

**F1c — deadline expiry between poll and sleep: benign overshoot (confirmed, cosmetic).**
Sequence is check→poll→sleep→check, so after the final failed poll the loop sleeps
`probePollMs` once past the deadline before throwing, and no "final poll at the deadline" is
made. Worst-case wall overshoot = one probe duration (≤ ~10 s with the 5 s + 5 s HTTP timeouts)
plus one sleep. Bounded and harmless; the thrown `last` can be up to that much stale, which the
message tolerates.

**F1d — -1 neutrality and the dead-gateway CLEAR (confirmed behavior; bias judged CORRECT).**
A permanently dead probe endpoint (port-forward died after the fault leg — a realistic
minikube/kind event) makes `clear()` spin the full `convergeTimeoutSeconds` and throw even
though `kubectl delete` succeeded and the route very likely IS restored. Executor treatment
(verified in `PairedFaultExecutor.execute()`): the finally-block catches per-target, logs,
collects into `clearFailures`, persists the already-computed evidence through
`clearFailureSink` with `f2ClearFailure:true` + `f2FailedFlags`, then throws the loud
"fault flag may still be active" F2 exception. So evidence is NOT lost and the run is marked
suspect. That is the right conservative bias: with the probe dead, "restored" and "abort still
live behind a dead gateway" are indistinguishable, and the contract forbids claiming
convergence it cannot observe. The cost is purely operational (manual verify), accepted.
Two message nits ride on this:
- (i) the timeout message prints the raw sentinel ("last returned -1"); say "probe I/O
  failure / unreachable" so an operator doesn't parse -1 as an HTTP status.
- (ii) `f2FailedFlags` / the F2 message name the `FaultTarget` (`deployment (-Dproperty)`),
  which for this backend is only the logging identity; the operative coordinate (manifest /
  VirtualService name) appears only in earlier INFO logs. "Verify/clear manually" would be more
  actionable if the awaitProbe/kubectl exceptions carried the manifest path (they carry the
  probe URL today; the CLEAR INFO line carries the manifest). Cosmetic.

**F1e — executor inject-throw + clear-throw corner (PLAUSIBLE, pre-existing, out of this
commit's scope).** If `inject()` throws (probe timeout — a NEW inject-failure mode this backend
introduces vs. the sibling's rollout timeout) and a clear in the finally ALSO fails, the inject
exception propagates and the `clearFailures` block after the try is never reached: no
f2ClearFailure report, only the error log. No fault-leg evidence exists on that path so nothing
is lost, but the "SUT may still be faulted" marker is log-only. Pre-existing
`PairedFaultExecutor` behavior, unchanged here; noted for the runbook.

## 2. Probe semantics (the real attack surface)

**F2a — abortStatus 500 collides with the natural app/mesh 5xx space (PLAUSIBLE with a
concrete near-miss; the one fix that matters).** The probe discriminates solely on
`status == abortStatus`. Failure modes:
- *Persistent collision* (app answers 500 on the probe path by nature): self-detects loudly —
  the executor runs the hygiene clear FIRST, `awaitProbe(false)` can never see non-500, and the
  run dies before any leg executes. Good property, currently documented nowhere.
- *Transient collision* (the dangerous one): inject() runs right after the control leg finished
  hammering the SUT. A transient 5xx on the probe path that happens to equal `abortStatus`
  falsely satisfies `expectAbort=true` BEFORE propagation completes → the fault leg starts
  unconverged → silently degrades into a second control leg → false NEGATIVE — precisely the
  failure the probe exists to prevent. On TT specifically the incomplete-path probe 404s at the
  Spring dispatcher (load-insensitive) and Envoy overload manifests as 503 ≠ 500, so the
  TT+500 instance is low-risk. But the fragility is one config away: abortStatus=503 would
  collide with Envoy's own no-healthy-upstream/overflow status and falsely converge on any
  briefly-unready upstream. Nothing in the code or javadoc warns about this.
- *Fix (config + one javadoc sentence, no logic change needed):* make it an explicit stated
  requirement that (a) the probe path's natural status differs from `abortStatus`, and (b)
  `abortStatus` should be a status neither the app nor Envoy plausibly emits (e.g. 418; Istio
  accepts any 200–599). Nothing downstream requires the abort be 500 — CancelServiceImpl's
  drawback client treats any non-success identically, so 418 preserves the defect mechanics
  while making probe collision structurally impossible. Optionally tighten the constructor
  floor to `abortStatus >= 400` (a 2xx/3xx "abort" is a config typo that currently passes
  validation — with a 200 abort on a 404-natural path the probe still discriminates, but the
  manifest fault would not induce a failure at all).

**F2b — GET is NOT what makes the probe non-mutating on this SUT (CONFIRMED, doc gap).** The
drawback write itself is `GET /inside_payment/drawback/{userId}/{money}`
(prep/g3-tt-cancel-refund-defect.md) — a GET-triggered write. The probe is safe ONLY because the
configured path is incomplete (no route match → dispatcher 404, no handler runs). A future
operator who "fixes" the probe URL to a complete path with dummy vars would make every clear-side
poll execute real writes on the un-faulted SUT (≈2 writes/s during convergence). The javadoc
says "must be non-mutating" and describes the incomplete-path trick, but does not state the
sharp edge that on TT, method-safety is illusory. One sentence in the javadoc/runbook.

**F2c — probe-path convergence ≠ SUT-path convergence (PLAUSIBLE, low risk, disclose).** The
probe observes ONE Envoy path (ingress/port-forward → inside-payment); the defect's traffic is
enforced on a different proxy hop (cancel-service's outbound sidecar or the server sidecar,
depending on the manifest's `gateways`/mesh scoping). xDS pushes to different proxies have no
ordering guarantee; in practice the debounce window (~100 ms) plus fault-leg startup slack
absorbs the skew. Not a code change — a one-line disclosure in the G3 method notes, and a
manifest-review item (the probe should traverse an Envoy hop that the fault also covers, which
the incomplete-path-under-the-same-prefix trick does satisfy when the VS is mesh-scoped).

**F2d — clear-direction accepts ANY non-abort status, including Envoy churn (PLAUSIBLE,
minor).** During delete-propagation Envoy may transiently answer 503/NR; `!= 500` satisfies
`expectAbort=false` and clear() can return while the mesh is still settling. Consequences are
bounded (clears happen at run boundaries; the next leg's own ack/read-back gates would catch
residual churn as NOT_EVALUABLE, not as a false FIRE). Requiring the app's natural status would
need more config (404-vs-405 ambiguity) for little gain. Accepted as designed; worth knowing.

## 3. kubectl argv and process budget

**F5 — clear-when-never-injected and clear-twice: CORRECT (confirmed).**
`kubectl delete -f <manifest> --ignore-not-found=true` exits 0 both when the VS exists and when
it never did; `awaitProbe(false)` then converges on the FIRST poll (app 404/405 ≠ 500) with
zero sleeps. So the executor's hygiene pass (which calls `clear()` on a never-faulted SUT,
outside any try/catch) is cheap and sane, and it also correctly flushes a stale VS from a
crashed prior run. Two operational notes: (a) the probe endpooint being up is a hard RUN
PRECONDITION — a not-yet-established port-forward at hygiene time burns the full converge
timeout and kills the run before the control leg (loud, acceptable, runbook-worthy); (b) if
Istio's CRDs are absent, delete -f fails with "no matches for kind" exit 1 even with
--ignore-not-found — correctly loud env failure.

**F5b — --wait on delete: not missing (confirmed).** kubectl delete defaults to `--wait=true`;
VirtualServices carry no finalizers so API-side deletion is immediate, and the real
(mesh-side) wait is the probe. Nothing to add. `-n` trailing placement is legal; a manifest
carrying a conflicting `metadata.namespace` fails loudly (config discipline). Args go through
ProcessBuilder (no shell), so no quoting/injection surface; namespace/context are trimmed.

**F5c — process budget coupling (confirmed, harmless nit).**
`exec.run(argv, convergeTimeoutSeconds + PROCESS_GRACE_SECONDS)` borrows the sibling's shape,
but unlike `rollout status` the kubectl here never waits for convergence — it is bounded by
`--request-timeout=30s`, so the natural budget is a constant (~REQUEST_TIMEOUT + grace). The
coupled budget is always sufficient (≥ 61 s > 30 s for any valid config) and merely oversized
for large converge timeouts; the coupling implies a relationship that does not exist. Cosmetic.

## 4. runProcess private → package

**F6 — no behavioral risk (confirmed).** Body unchanged; only visibility widened, consumed as a
method-ref for the default Exec. The wait-first-drain-after design carries a documented
small-output assumption; the new callers (apply/delete of a one-resource manifest) emit a line
or two, well within pipe capacity. If a future in-package caller ran a chatty command, the
failure mode is a bounded spurious process-timeout (waitFor(timeout) → destroyForcibly →
IOException) with lost output — degraded, not a hang. The updated comment discloses the
sharing. Public API surface unchanged (class members stay package-private).

## 5. Blocking/interrupt behavior

**F7 — consistent with the sibling (confirmed).** Both interrupt sites (probe sleep, exec.run)
re-assert the interrupt flag and wrap in `FaultInjectionException`, matching
`SutFlagFaultInjector.settle()`/`kubectlCapture()` exactly. Under the executor's finally
clear-all, an interrupt mid-clear marks that target failed and the still-set flag makes
remaining targets fail fast into the same f2 path — conservative and loud, same as the sibling.

## 6. Test adequacy — concrete missing cases (listed, not added)

Covered well: argv assembly both verbs (context present/omitted, trailing -n ns,
ignore-not-found, request-timeout), 3rd-poll convergence both directions, never-converging
inject AND clear throw, -1 neutrality both directions, kubectl exit-1 surfaces output, loud
constructor validation (5 branches). Missing:

1. `IOException` from exec.run → "kubectl failed to start" wrap (ScriptedExec never throws).
2. `InterruptedException` from exec.run → wrapped AND `Thread.currentThread().isInterrupted()`
   restored — the flag restoration is asserted nowhere.
3. Interrupted probe sleep → "interrupted while probing" + flag restored (needs a probe that
   interrupts the thread; probePollMs seam makes this cheap).
4. Hygiene-shape clear: `probe.script(404)` → clear converges with exactly 1 probe call and no
   sleep (the executor's clear-when-never-injected path; currently only implied).
5. First-poll inject convergence pinned explicitly (`nullContext` test exercises it but never
   asserts `probe.calls == 1`).
6. Clear-direction timeout message content ("wanted anything but 500") — only inject's message
   is content-checked.
7. Delete exit 0 with "not found"-style output — behaviorally a no-op today (output unused on
   success) but pins the --ignore-not-found reliance against future output-parsing.
8. Probe receives `probeUrl` (ScriptedProbe ignores its argument; one assertEquals).
9. (Optional, pins F1b) absurdly large convergeTimeoutSeconds currently throws immediately with
   "nothing" via nanoTime overflow — either fix the idiom or pin the edge.

`httpProbe`/`drainQuietly` are untested (would need a real socket); acceptable to exclude from
the unit suite given §7 review.

## 7. httpProbe / drainQuietly

**F8a — comment is wrong: drain-for-keep-alive is defeated by disconnect() (CONFIRMED,
harmless).** `httpProbe`'s finally calls `connection.disconnect()`, which closes the underlying
connection, so "Best-effort body drain so keep-alive connections can be reused" is dead intent —
every poll opens a fresh TCP connection. At ≤2 polls/s that is fine (no leak: streams closed
via try-with-resources, disconnect in finally, error-stream-null handled). Fix the comment (or
drop disconnect; keeping disconnect is the safer leak posture — fix the comment).

**F8b — unbounded drain length (PLAUSIBLE, exotic).** Connect/read timeouts bound the status
line and each read(), but not TOTAL drain time: a server trickling bytes every <5 s keeps the
drain loop alive forever and awaitProbe never regains control past its deadline — an unbounded
hang in theory. The probe targets a localhost port-forward returning tiny 404/abort bodies, so
practical risk ≈ 0. A 64 KB drain cap would close it for one line.

**F8c — redirects followed by default (PLAUSIBLE, minor).** HttpURLConnection follows
same-protocol 3xx, so the returned status is the redirect TARGET's — possibly a route outside
the aborted prefix. The TT probe path (dispatcher 404) never redirects; still,
`setInstanceFollowRedirects(false)` would pin the signal to the probed hop and costs nothing.
GET-vs-HEAD: GET is the right call (HEAD gets 405-ed by some stacks; body cost is trivial).
Correctly avoids the classic 404 trap by using `getResponseCode()` (which doesn't throw
FileNotFoundException) and only touching `getInputStream()` for 2xx.

---

## Verdict

**ACCEPT-WITH-FIXES.** The convergence-probing design, its conservative -1/timeout bias, the
idempotent clear semantics, and the executor integration are sound; tests pin the load-bearing
behavior and are green. Fixes (none block the architecture; 1–3 should land before G3 data):

1. **F2a/F2b (doc+config, the substantive one):** javadoc/runbook must state (a) probe-path
   natural status MUST differ from abortStatus, (b) prefer an abort status neither app nor
   Envoy emits (418-style; explicitly warn against 503), (c) on TT the probe is non-mutating
   only via path-incompleteness — the drawback write is itself a GET. Consider constructor
   floor `abortStatus >= 400`.
2. **F8a (comment):** fix the drainQuietly keep-alive comment (disconnect defeats reuse).
3. **F1d-i (message):** render probe -1 as "I/O failure/unreachable", not a bare -1.
4. Optional polish: F8c `setInstanceFollowRedirects(false)`; F1b overflow-safe deadline idiom;
   F5c constant kubectl process budget; test gaps §6 (1–4 first).
5. Disclose F2c (single-path probe vs. multi-proxy propagation) in the G3 method notes; F1e
   (inject-throw + clear-throw loses the f2 marker) is pre-existing executor behavior worth a
   runbook line.
