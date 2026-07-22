# RESULT — depdown live-run upgrade (a REAL MIST paired leg on teastore-order-depdown) — PREREG + RESULT

**Date:** 2026-07-21 · User-directed ("把 depdown 升成 live-run,保留所有记录,明确标明位置+披露") ·
Status: **PRE-REGISTERED (this section committed BEFORE the run)** · DoD: post-hoc 3-cold result
review.

## §1 PRE-REGISTRATION (committed before any leg executes)

**Goal.** Upgrade `teastore-order-depdown-specified-001`'s MIST-column provenance from
`capture-concordant` (the 2026-07-20 curl capture; MIST itself never ran that leg) to `live-run`,
by executing MIST's REAL read-back oracle — the same paired MEMBERSHIP binding as wave 2.75-A and
Phase-C A5(iii) — under the dependency-down producer.

**Harness (driver-only; oracle byte-untouched).** `io.mist.cli.enable.TeaStoreDepdownHeadToHead`
(new, this wave): control legs delegate verbatim to the proven `TeaStoreHttpStimulus` (2.75-A);
the oracle path is `TeaStoreOrderHeadToHead` → `PairedFaultExecutor` → `DataIntegrityRuntime`
(all pre-existing, unmodified); read-back = `JsonDurableReadback` over the persistence
`/rest/orders` JSON; triple = the committed `evaluation/suts/teastore/triples/teastore-order-triple.yaml`
(MEMBERSHIP on `address1`, isolation supplied).

**Fault choreography (mirrors the 2026-07-20 capture protocol + its disclosed nuance).** Per
fault leg, INSIDE the stimulus: (1) login + addToCart with the db UP (login reads REQUIRE the db,
B-m3); (2) `teastore-db` scaled to 0 and POLLED TO 0 PODS — an unverified fault state aborts the
leg loudly; (3) the confirm (marker in `address1`) rides the db-down window; (4) in a finally,
the BUFFER-DROP restore runs BEFORE the oracle polls: force-delete the persistence pod WHILE the
db is down (drops the JPA pool's buffered write — the capture measured ~50% pool-reconnect-flush
persistence without this), scale the db to 1, wait both rollouts, settle 8 s. The oracle's
read-back probes a clean restored path (the meshsever "fault scoped to the write; read-back clean
at probe time" discipline).

**Pinned parameters.** N = 4 pairs (the A5(iii) convention) · fire rule = the executor's
pure-differential rule (fault 2xx-acks marker AND marker absent from the fault read-back AND
control marker present) · read-back poll cap 15000 ms (the A5(iii) value) · seed/user rotation as
the harness does it · PVC-backed `teastore-db` (the 2026-07-20 patch) verified at bring-up.

**Gates + honesty rails.** FIRST-RUN-OF-RECORD: whatever the 4 pairs measure is the result — a
non-FIRE pair is recorded as-is, never re-rolled (probe corrections for OPERATIONAL failures —
e.g. a rollout timeout — are logged and the leg re-run ONCE with the correction disclosed; an
oracle non-FIRE is never re-run). Ground truth = DIRECT `/rest/orders` reads of the report's
markers (never MIST), recorded by the runner script. **Upgrade condition:** FIRE 4/4 AND ground
truth concordant (control markers present / fault markers absent) → the case's provenance flips
to live-run (census provenance string carries `depdown-live H2H`; `LIVE_PROVENANCE_MARKERS` +
the census builder gain the entry; the split becomes 8 live-run + 1 manual + 2→1
capture-concordant). ANY other outcome → the cell STAYS capture-concordant and this RESULT
records the failure honestly. The 2026-07-20 capture evidence is PRESERVED UNTOUCHED either way.

**Records index (locations pinned now; results filled post-run). Per the user's directive, every
record carries HOW EXACTLY it was produced — the invocation line / protocol steps — not just what
it says:**
| record | location | what it holds / discloses | HOW it was produced (exact) |
|---|---|---|---|
| This PREREG+RESULT | `b4/RESULT-depdown-live.md` | the protocol pinned pre-run; then the measured outcome + every disclosure | hand-authored; §1 committed BEFORE the run (commit hash in git history precedes the run log's timestamps) |
| The driver | `mist-cli/src/main/java/io/mist/cli/enable/TeaStoreDepdownHeadToHead.java` | the exact choreography incl. the buffer-drop; discloses the HTTP-helper duplication (TeaStoreHttpStimulus's members are private) | compiled via `mvn -q -pl mist-cli -am compile -DskipTests`; oracle classes untouched (git diff shows ONLY this new file under mist-cli) |
| Run report (MIST's own verdicts) | `b4/cset/teastore-depdown/teastore-order-depdown-run.report.json` | per-pair acks, polls, gates, markers, FIRE reasons | written by `PairedFaultExecutor.writeReport` at the end of the java run below — MIST's own executor emits it, not a hand-built file |
| Run logs (one per attempt — there is NO bare `mist-run.log`) | `b4/cset/teastore-depdown/mist-run.attempt{1-crashed,2-write-ack-conflated,3-writeownack-NOFIRE}.log` | the full driver+oracle stdout per attempt (kubectl exits, db-pod-0 verifications, restore confirmations) **+ each runner's own echoed launch line**; attempt 3 is the run-of-record | `bash b4/runners/livetool/depdown_mist_run.sh` → `java -cp "mist-cli/target/classes;$(cat mist-cli/cp.txt)" -Dts.webui=http://localhost:8091/tools.descartes.teastore.webui -Dts.persistence=<PF 8092 in attempts 1-2; kubectl-proxy 8001 base in attempt 3> -Dts.triple=evaluation/suts/teastore/triples/teastore-order-triple.yaml -Dts.probes=4 -Dts.report=<report path> io.mist.cli.enable.TeaStoreDepdownHeadToHead` (the runner echoes the resolved line verbatim into each log before executing) |
| Ground truth | `b4/cset/teastore-depdown/ground-truth-depdown.txt` | DIRECT `/rest/orders` marker reads (never MIST) + row counts | the runner post-pass: markers parsed from the report JSON, then per marker `wsl curl -s http://localhost:8092/.../rest/orders \| grep -oc <marker>`; the file records each command + its output |
| Runner | `b4/runners/livetool/depdown_mist_run.sh` | classpath build, bring-up reuse, launch line, ground-truth collection — the single reproduction entry point | committed pre-run; re-running it end-to-end reproduces the wave (bring-up → java → ground truth) |
| The ORIGINAL capture (preserved, UNTOUCHED) | `b4/cset/teastore-depdown/depdown-legs.log` + `depdown_capture.sh` + `ts_journey.sh` | the 2026-07-20 curl capture N=6 + the buffer-drop nuance discovery — stays the capture-of-record | produced 2026-07-20 by `wsl bash /tmp/depdown_capture.sh <ctrl> <m1> <m2> <m3>` (curl journeys + kubectl scale/rollout choreography; the committed script IS the how); NOT a MIST run — exactly why this wave exists |
| Provenance chain (post-run) | case JSON `provenance.notes` · `build_mist_column_census.py` PROVENANCE map · `score_arms.py` LIVE_PROVENANCE_MARKERS · regenerated `mist-column-census.json` + `matched-recall-table.json` · freeze §6 dated row · paper-plan §1 amendment | each stamped with this wave's marker (`depdown-live H2H`) so the upgrade is mechanically traceable | census + table REGENERATED by `python build_mist_column_census.py` + `python scoring/score_arms.py` (never hand-edited); the case JSON + freeze rows are hand-edited docs, marked as such |

## §2 RESULT (filled after the run) — 2026-07-21

### Outcome: `NO_FIRE 0/4` — the live oracle REFUTED the case (not merely "upgrade failed")

The real MIST paired oracle (frozen `DataIntegrityRuntime` via `TeaStoreOrderHeadToHead` →
`PairedFaultExecutor`) ran end-to-end over the PVC-backed SUT and returned, verbatim:

```
triple=teastore-order-confirm  verdict=NO_FIRE  FIRE 0/4 probe-pair(s)
  fault run not acknowledged (http 302, body status null) — base relation vacuous
```

All 4 fault probes were identical: `fault confirm WRITE-OWN response: HTTP 302` and, when the
redirect was followed for journey context, `followed page: HTTP 500`. **Independent ground truth**
(direct `/rest/orders`, never MIST): the write WAS lost (fault marker present-count 0, control
marker present-count 1) — but it was **not cleanly 2xx-acknowledged**.

**This is the upgrade condition's "ANY other outcome" branch — and it went deeper than the PREREG
contemplated.** §1 assumed depdown IS an acked-but-lost case and only the provenance (live vs
concordant) was open. The live run refuted that premise:

| | `maintenance-masked` (2.75-A, proven FIRE 5/5) | **`depdown` (this run)** |
|---|---|---|
| producer | maintenance flag; **persistence stays UP** | `teastore-db` scaled to 0; **read path DOWN** |
| persistence create | returns **201 / body `-1`** (a masked 2xx) | **throws → 500** (no catch) |
| followed confirm page | **HTTP 200** order-confirmed (freeze row 306) | **HTTP 500** (the page render reads the down db) |
| what the user sees | a clean "order confirmed" | **a 500 error page** after the redirect |
| MIST verdict | acked-2xx + absent → **FIRE** | not-acked (302→500) → vacuous → **NO_FIRE** |

**Finding: under `db`-scale-0, depdown is a LOUD 500 failure, not a silent masked-2xx loss.** The
maintenance flag is the genuine mask (persistence up, confirm page renders 200, write silently
gone); scaling the db to 0 takes down the whole read path, so the confirmation journey 500s and
the user is NOT told "success". The 2026-07-20 curl capture recorded the confirm's **unfollowed**
302 as "acked (masked)" — a looser criterion than the real oracle's (which follows the journey /
requires 2xx per `DataIntegrityRuntime` L700, `acked = httpStatus/100 == 2`). The live oracle run
**falsified** the capture's acked reading. freeze row 300 (2026-07-10) had already flagged the
DB-down producer "UNSOUND-for-capture"; this run confirms it on a mechanism deeper than the
snapshot-wipe worry — even PVC-backed and buffer-drop-clean, it never produces a 2xx ack.

**Ack-convention robustness (post-review clarification, 3-cold reviewer A/C).** The NO_FIRE is
robust to how the driver forms the ack, because there is **no 2xx anywhere in depdown's response
chain under either faithful convention**: the confirm's first-hop is 302, and the followed page
(the maintenance-consistent convention — `TeaStoreHttpStimulus.placeOrder` follows redirects and
acks on the terminal page, which is a 200 for the maintenance FIRE) is 500. Both are non-2xx →
not-acked → the same verdict. The **operative gate in the run-of-record (attempt 3)** was the
**write-own 302** (`ackHttpStatus=302, acked=false`); the followed **500** is corroborating
user-experience context (it defeats any "MIST missed a real mask" reading — the user is shown an
error, so there is no acknowledged success to have been masked), NOT the verdict driver. Note the
two legs of the differential pair are ack-formed differently by construction — the control leg
delegates to `TeaStoreHttpStimulus` (follows redirects, acks 200) while the fault leg uses the
first-hop 302 — which is why attempt 3 privileged the first-hop; but attempt 2's followed-page ack
(500) was the precedent-consistent convention and gave the **same** not-acked answer. Attempt 2's
genuine defect was therefore NOT "ack conflation" but the **transport-dead read-back** (its
pod-pinned 8092 PF died with the force-deleted persistence pod → read-back HTTP 0 / 0 rows); the
ideal single run would be attempt 2's followed-page ack + attempt 3's kubectl-proxy read-back. The
filename `mist-run.attempt2-write-ack-conflated.log` retains its as-committed label; this note is
the corrected characterization of record.

**Ground-truth scope (post-review, reviewer A).** `ground-truth-depdown.txt` directly verifies the
loss for **pair 0 only** (the report JSON serializes one representative pair's markers): control
marker present-count 1, fault marker present-count 0, +204 total rows intact via the PVC. Pairs
1–3 loss is inferred from the identical per-probe choreography (all 4 logged `teastore-db pods: 0
(fault state VERIFIED)` → `302` → followed `500`). Non-load-bearing: the verdict rests on
not-2xx-acked, which the log confirms 4/4, and `PairedFaultExecutor.verdict()` checks `!acked`
before ever consulting the read-back.

### Three attempts — ALL preserved, each with HOW it was produced (per the user directive)

| attempt | file (preserved) | what it holds | outcome | HOW produced (exact) — and why it is preserved not deleted |
|---|---|---|---|---|
| 1 | `cset/teastore-depdown/mist-run.attempt1-crashed.log` | crash trace | `SocketTimeoutException` at the fault confirm (12s read timeout too short for the db-down confirm) | `bash b4/runners/livetool/depdown_mist_run.sh` w/ the driver's default 12s read timeout; operational failure (a timeout), so per §1 fixed (→90s) + re-run ONCE, disclosed |
| 2 | `mist-run.attempt2-write-ack-conflated.log` + `report.attempt2.json` | NO_FIRE via TWO infra defects | `fault not acknowledged (http 500) — base relation vacuous` | same runner; the driver FOLLOWED the confirm redirect and fed the post-redirect **500** page-read to the Ack (conflating the write ack with a journey read), AND the pod-pinned 8092 persistence PF died with the force-deleted pod (read-back + ground truth transport-dead: 0 rows vs real ~192). Both are OPERATIONAL (transport/harness), logged + fixed |
| 3 | `mist-run.attempt3-writeownack-NOFIRE.log` + `report.attempt3-NOFIRE.json` + `ground-truth-depdown.txt` | **the run of record** | **NO_FIRE 0/4** — fault ack = the write's OWN first-hop **302** (not 2xx) → not-acked → vacuous | same runner, fixed driver (WRITE-OWN first-hop ack; redirect followed as journey-context LOG only) + read-back/ground-truth via `kubectl proxy` (port 8001, re-resolves per request → survives the persistence-pod force-delete). This is an ORACLE non-FIRE (a faithful verdict, not an operational failure) → **never re-rolled** (§1 first-run-of-record) |

The launch line is echoed verbatim into each `mist-run*.log` (the `[runner] LAUNCH LINE (verbatim):`
block); the `-Dts.persistence` value in attempt 3 is the proxy base
`http://localhost:8001/api/v1/namespaces/teastore/services/teastore-persistence:8080/proxy/…`.

### Disposition of the MIST cell (factual correction applied now)

`teastore-order-depdown-specified-001.oracle_expectation.mist_readback_oracle`: **`flag` →
`not_applicable`** (principled ack-gate abstention — MIST's acked-lost class is 2xx-gated and this
producer never yields a 2xx ack). This **reverts the 2026-07-20 E2 fold (`bd362d0`)** that had
counted depdown as a capture-concordant flag on the now-falsified curl reading, and **restores the
Phase-C census exactly**: flag 10→**9**, not_applicable 4→**5**, no_flag 13 unchanged; MIST
evaluable positives 10/10 → **9/9** (still 0 misses among evaluable, 0 FP); provenance split
7 live-run + 1 manual + 2 concordant → **7 live + 1 manual + 1 concordant** (depdown leaves the
flagged set). This is the conservative floor and is honest regardless of the corpus-composition
call below.

### DISPOSITION — RESOLVED 2026-07-21: RETIRE (user-directed "可以退役")

After the finding + the 3-cold review were surfaced, the user directed **retire** (option a).
`teastore-order-depdown-specified-001.json` was `git mv`'d to `cases/excluded-out-of-mask/`
(distinct from `excluded-fcorpus/`: that dir = corrupted-not-lost / same-site-covered; this dir =
loud-not-masked, the ack-gate boundary) with a README carrying the full reason. Result:
**benchmark-of-record 27 → 26 (11 pos / 15 neg)**; all derived artifacts regenerated over 26.
**MIST read-back is UNCHANGED at 9/9 evaluable + 0 FP** (depdown was already n_a, never one of the
9 flags), so the headline does not move; the corpus loses one positive that was mislabeled, and
depdown becomes a boundary appendix demonstrating MIST correctly ABSTAINS on loud failures. The
corpus-class question is settled: a 302-redirect loss whose post-redirect page 500s is NOT in the
2xx-ack-gated masked class; a genuine masked dependency failure would need a read-path-up producer
(a new capture, not this one).

**The 2026-07-20 capture evidence (`depdown-legs.log`, `depdown_capture.sh`, `ts_journey.sh`) is
PRESERVED UNTOUCHED** — it remains the capture-of-record for what the curl legs measured; this
RESULT records that the live oracle refuted its "acked" reading.

### 3-COLD RESULT REVIEW (the PREREG §1 DoD) — CLOSED, all ACCEPT-WITH-FIXES

Three independent cold reviewers (opus, read-only) ran in parallel; all **ACCEPT-WITH-FIXES**, none
REJECT, the refutation independently confirmed sound:
- **A (oracle semantics):** NO_FIRE faithful + robust to the ack convention; `not_applicable` is
  correct (NOT a miss) — the followed-500 means there is no acknowledged success to have masked;
  maintenance-200-vs-depdown-500 code-corroborated. Fixes folded: the ack-convention framing above;
  the ground-truth p0-only note; retiring the case resolves A's "one-sided correction" flag (the
  now-falsified comparator/`ground_truth`/`ack_content_visibility` cells leave with the case rather
  than needing per-cell reconciliation).
- **B (corpus arithmetic):** every count independently re-derived, **zero errors** (census 9/13/5→
  9/13/4 on retirement, MIST 9/9 + 0 FP, "restores Phase-C exactly" diff-verified, no foreign files
  swept). Fixes folded: builder docstring/note + the orphaned depdown adjudication removed; the
  stale "10/10" in `paper-draft-plan.md` §5 and the `RESULT-e2-frontier.md` current-state pointer
  synced; the eanom/synthesis "10/10" references synced.
- **C (honesty/reframe):** high-integrity self-correction; verified the 2026-07-20 capture scripts
  never followed the redirect (the over-claim is real); all 3 attempts + how-produced preserved;
  disposition fairly surfaced; historical evidence untouched. Fixes folded: the §1 run-log pointer
  above; the operative-gate-is-the-302 clarification above.
