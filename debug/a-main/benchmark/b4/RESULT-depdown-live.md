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
| Run log | `b4/cset/teastore-depdown/mist-run.log` | the full driver+oracle stdout (kubectl exits, db-pod-0 verifications, restore confirmations) **+ the runner's own echoed launch line** | `bash b4/runners/livetool/depdown_mist_run.sh` → `java -cp "mist-cli/target/classes;$(cat mist-cli/cp.txt)" -Dts.webui=http://localhost:8091/tools.descartes.teastore.webui -Dts.persistence=http://localhost:8092/tools.descartes.teastore.persistence -Dts.triple=evaluation/suts/teastore/triples/teastore-order-triple.yaml -Dts.probes=4 -Dts.report=<report path> io.mist.cli.enable.TeaStoreDepdownHeadToHead` (the runner echoes the resolved line verbatim into this log before executing) |
| Ground truth | `b4/cset/teastore-depdown/ground-truth-depdown.txt` | DIRECT `/rest/orders` marker reads (never MIST) + row counts | the runner post-pass: markers parsed from the report JSON, then per marker `wsl curl -s http://localhost:8092/.../rest/orders \| grep -oc <marker>`; the file records each command + its output |
| Runner | `b4/runners/livetool/depdown_mist_run.sh` | classpath build, bring-up reuse, launch line, ground-truth collection — the single reproduction entry point | committed pre-run; re-running it end-to-end reproduces the wave (bring-up → java → ground truth) |
| The ORIGINAL capture (preserved, UNTOUCHED) | `b4/cset/teastore-depdown/depdown-legs.log` + `depdown_capture.sh` + `ts_journey.sh` | the 2026-07-20 curl capture N=6 + the buffer-drop nuance discovery — stays the capture-of-record | produced 2026-07-20 by `wsl bash /tmp/depdown_capture.sh <ctrl> <m1> <m2> <m3>` (curl journeys + kubectl scale/rollout choreography; the committed script IS the how); NOT a MIST run — exactly why this wave exists |
| Provenance chain (post-run) | case JSON `provenance.notes` · `build_mist_column_census.py` PROVENANCE map · `score_arms.py` LIVE_PROVENANCE_MARKERS · regenerated `mist-column-census.json` + `matched-recall-table.json` · freeze §6 dated row · paper-plan §1 amendment | each stamped with this wave's marker (`depdown-live H2H`) so the upgrade is mechanically traceable | census + table REGENERATED by `python build_mist_column_census.py` + `python scoring/score_arms.py` (never hand-edited); the case JSON + freeze rows are hand-edited docs, marked as such |

## §2 RESULT (filled after the run)

_PENDING — this section is empty at the pre-registration commit._
