# Reproducibility audit (ISSTA 2026 tool-demo artifact)

**Date:** 2026-06-09. **Commit audited:** `c39ceec4` (branch `inject-detection`).
**Method:** every REPRODUCE.md / README claim was re-executed from a **fresh clone**
of HEAD (`git clone -b inject-detection <local> Rest-fresh`) on a clean JDK 21
(`openjdk-21-jdk-headless`), simulating a reviewer machine. Network-free claims
were run inside a no-network namespace (`unshare -n`), which is stricter than
"no key exported". Logs are in `evidence/`.

## 1. Verification matrix — what a reviewer can reproduce

| # | Claim (source) | How verified | Result | Evidence |
|---|---|---|---|---|
| V1 | §5 offline oracle: Bookinfo HTTP G2 fires @ERROR, response-level PASS (REPRODUCE §5 cmd 1; paper §Case Study) | fresh clone, `evaluation/run-offline-oracle.sh` + verbatim §5 cmd | **PASS** | `evidence/offline-oracle.log` |
| V2 | §5 offline oracle: Boutique gRPC G2 fires @WARN on 7 of 12 outage traces (REPRODUCE §7 row 2) | same script | **PASS** (exactly 7/12) | `evidence/offline-oracle.log` |
| V3 | Healthy controls stay silent (REPRODUCE §5 "expected") | both healthy traces through OracleCheck | **PASS** (0 fires) | this README §3 commands |
| V4 | noexec generation is offline ("no network", paper §Case Study) | run inside `unshare -n` (no interfaces at all), fresh clone | **PASS** (exit 0, 123 scenario files from the 1 committed trace) | `evidence/noexec-run1-tail.log` |
| V5 | Seed-42 byte-identical generation (README Quick Start D) | two runs, sha256 over all `Flow_Scenario_*.java`, diff | **PASS** (123/123 identical) | `evidence/run1.sums`, `evidence/run2.sums` |
| V6 | §6.3 zero-infra sanity command, verbatim from `.runtime/` | verbatim | **PASS** (exit 0) | session transcript |
| V7 | §5 cmd 3 ResponseEnvelopeLiveCheck (1 live LLM call) | verbatim with DeepSeek key | **PASS** (`RESPONSE_ENVELOPE: FAIL ... status=0 classified as failure (LLM, cached)`) | matches committed `docs/main-contribution/evidence/responseenvelope_live_softerror.txt` |
| V8 | §7 evidence files all committed | `git ls-files --error-unmatch` each | **PASS** (all present) | session transcript |
| V12 | §4 install command, verbatim (`mvn -q -DskipTests install`, which also compiles test sources) → §5 oracle command, verbatim | pristine re-clone, JDK 21 | **PASS** (install exit 0, jar built, Bookinfo check FIRES @ERROR) | session transcript |
| V9 | SP1 one-command SUT deploy (bookinfo `deploy/deploy.sh`) | fresh deploy to a second kind cluster on this host (cluster name sed-renamed, nothing else) | **PASS with caveats** — one command stood up cluster + Istio + Jaeger + all Bookinfo resources (4/6 pods Running, 2 still pulling images when the 240s wait expired); this 8-core host cannot sustain two Istio clusters (G5), and the standing `mist` cluster (same script, 11 days up) is the complete end state the live runs (V10) used. The G4 kubeconfig fix was exercised live by the re-run. | `evidence/bookinfo-deploy-*.log` |
| V10 | Live in-process run + 4-case oracle matrix (§6.2) | full `java -jar mist.jar bookinfo-demo.properties` (generate+compile+execute, 166 tests, exit 0, healthy SUT) then `run-oracle-e2e.sh` with a real ratings outage | **PASS** — #1 outage `/products` silent; #2 outage `/reviews` HTTP 200 yet `HIDDEN_DOWNSTREAM_FAILURE: reviews→ratings (http=503 otel=ERROR)` fails the test red; #3 outage `/ratings` loud 503, 0 hidden fires; #4 healthy `/reviews` passes, 0 hidden fires. (First attempt invalidated by dying `kubectl port-forward`s → G10 fix, then clean rerun.) | `evidence/bookinfo-e2e-matrix.log` |
| V11 | TrainTicket local one-command deploy (§6.3, the docker-compose SUT) | `deploy.sh` run verbatim on this host (7 fault images pre-built at `codewisdom/*:0.2.0`, port 8080 free) | **Machinery PASS, convergence blocked by hardware — exactly as §6.3 gates it.** One command brought all 60 containers up (image reuse, compose env wiring, network all correct), but the ~40-JVM fleet thrashed on this 8-core host with the standing kind cluster running: DB containers were slow, services crash-looped on MySQL DNS, the gateway nginx could not resolve its upstreams, login never reached 200 in ~25 min (load avg peaked 79). This is the documented "will NOT converge" scenario, now empirically confirmed; the 10/10 evidence of record remains `run22` + the lab deployment. | `evidence/tt-local-deploy-nonconvergence.log` |

## 2. Gaps found (and dispositions)

| # | Gap | Severity | Disposition |
|---|---|---|---|
| G1 | **GitHub default branch was `main`** — 475 commits behind `inject-detection`, lacking `REPRODUCE.md`/`evaluation/`. A reviewer's plain `git clone` landed on dead content. | **CRITICAL** | **RESOLVED 2026-06-11 by repository migration:** the canonical repo is now **github.com/miaoti/MIST** (default branch `main` = this tree); all reviewer-facing URLs updated. The old `miaoti/Rest` stays as the frozen full-history archive. |
| G2 | README Quick Start D claimed a **bundled** `.mist/llm-call-cache.json` serving all LLM calls offline. The file was never committed (`.gitignore` line 23), and an empirical no-network run with the local 51 MB cache present showed ≥26 cache misses falling through to the network (prompt drift since the cache was written). | HIGH | Quick Start D rewritten: offline claim scoped to the (verified) noexec determinism path; cache documented as *local replay*, with a bless-then-prove-offline acceptance test. Evidence: `evidence/noexec-llm-cache-misses.log`. |
| G3 | REPRODUCE §6.2 recipe was missing two required steps: the port-forwards and the prior MIST generation run that `run-oracle-e2e.sh` expects under `.runtime/` (it hard-fails without them). | HIGH | §6.2 updated with both steps. |
| G4 | `deploy.sh` re-run on a host where the cluster already exists skips `kind create cluster` and therefore never gets a kubeconfig in the current account → `istioctl` dials `localhost:8080` and dies. Verified live. | MED | One-line fix in `deploy.sh`: `kind export kubeconfig --name "$CLUSTER"`. Evidence: `evidence/bookinfo-deploy-fail-kubeconfig.log`. |
| G5 | `kind create cluster` fails on stock Ubuntu inotify limits ("could not find a log line ... Multi-User System"); a second concurrent cluster can also time out at kubeadm bootstrap on a small host. Verified live. | MED | REPRODUCE §10 troubleshooting entry added (sysctl line + one-cluster-per-host note). Evidence: `evidence/bookinfo-deploy-fail-kubeadm.log`. |
| G6 | README Requirements said **JDK 11** while REPRODUCE (correctly) demands JDK 21; the host JRE failure mode is real (no `javac`). | MED | README table now says JDK 21 (bytecode target stays 11). |
| G7 | REPRODUCE §2.3 called the ResponseEnvelope check "one cached call" — it is one **live** call (the harness deliberately uses a fresh store; `(LLM, cached)` in its output refers to the rule being cached afterwards). | LOW | Wording fixed. |
| G8 | Paper abstract screencast URL is still `\todo{SCREENCAST-URL}`; REPRODUCE §9 points at it. | **BLOCKER at submission** | Author action: record + upload + fill in. Cannot be done by tooling. |
| G9 | 50 untracked local files in `mist-cli/src/main/resources/My-Example/trainticket/test-trace/`; only 1 trace is committed. Generation from the single committed trace works (V4), so the artifact is self-sufficient. | INFO | No repo change. Do not commit the local extras. |
| G10 | A bare `kubectl port-forward` dies ("lost connection to pod") within the >10-min `run-oracle-e2e.sh` runtime, silently turning later cases into bogus connection failures/500s — observed live: the first matrix attempt produced a meaningless 48/50-failure case #2 with no oracle involvement. | HIGH | `run-oracle-e2e.sh` now self-heals: `ensure_forwards` starts auto-restarting forwards when the gateway is unreachable (re-checked before every case, cleaned up on exit); REPRODUCE §6.2 shows the restart-loop form. Verified by the clean rerun (V10). |
| G11 | The public TrainTicket fault source (`AsifShaafi/train-ticket-injection`, branch `injection`) was left with **demo-experiment mutants applied**: 76d15163 turned `INSUFFICIENT_STATIONS_FAULT` into silent acceptance, then 661cd688 replaced it with a hidden-downstream swallow (the abandoned TT-mutant G2 experiment). Both commits self-describe as "revert to restore". A reviewer building the 7 fault images from branch HEAD gets at most **9/10** registry-echoing faults — the paper's 10/10 path silently degrades. | **CRITICAL** | `AdminRouteServiceImpl.java` restored to its pre-experiment 560be95e state and committed locally on `injection` as `54e1a2e5`. **NOT pushed** (pushing auto-rebuilds the live lab SUT, ~20 min — author's call; push when convenient). |
| G12 | `trainticket/deploy/deploy.sh` printed "TrainTicket up" even when the 10-min login poll never saw a 200 — a reviewer on a small host reads success while the SUT never converged. | MED | Poll outcome now tracked; on timeout the script prints an explicit WARNING + manual re-check command and exits 1. |
| G13 | The paper cites the Zenodo snapshot DOI `10.5281/zenodo.20514985`, but the DOI returns **404** (reserved, never published) — a reviewer clicking the artifact link gets a dead end. REPRODUCE §11 also still carried the "archive and cite here" placeholder. | **BLOCKER at submission** | §11 now cites the reserved DOI + the publish requirement. Author action: upload the final evaluated commit to the Zenodo deposit and **publish** it before submission (verified 404 on 2026-06-09). |

## 3. Re-run commands (copy-paste)

```bash
# fresh-clone offline path (V1-V5), JDK 21 required
git clone https://github.com/miaoti/MIST MIST-fresh && cd MIST-fresh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
JAVA="$JAVA_HOME/bin/java" ./evaluation/run-offline-oracle.sh

# healthy controls (V3)
"$JAVA_HOME/bin/java" -cp mist-cli/target/mist.jar evaluation/suts/bookinfo/OracleCheck.java \
  docs/main-contribution/evidence/bookinfo_e2e_traces/healthy_reviews_control.json \
  "GET /api/v1/products/0/reviews"
"$JAVA_HOME/bin/java" -cp mist-cli/target/mist.jar evaluation/suts/boutique/OracleCheck.java \
  docs/main-contribution/evidence/boutique_e2e_traces/boutique_frontend_healthy.json

# determinism (V5) — run twice, diff
"$JAVA_HOME/bin/java" -Drandom.seed=42 -jar mist-cli/target/mist.jar \
  mist-cli/src/main/resources/My-Example/trainticket-demo-noexec.properties
find mist-cli/src/test/java/trainticket_twostage_test -name 'Flow_Scenario_*.java' \
  -exec sha256sum {} \; | sort > /tmp/run1.sums
```

## 4. Out of scope on this host (honestly gated, matching REPRODUCE)

- Full live TrainTicket (40 JVMs): this host has 8 cores and a standing kind
  cluster; REPRODUCE §6.3 already gates it as optional with the committed
  `run22` report as evidence of record.
- The detection-rate count (10/10) is a live, LLM-variant number (REPRODUCE §8);
  it is not an offline-reproducible claim and the docs no longer imply it is.
