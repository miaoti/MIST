# MIST — Artifact & Reproduction Guide

**MIST** (Microservice Integration & Scenario Tester) is an open-source REST API test
generator that turns OpenTelemetry/Jaeger traces + OpenAPI specs into runnable
cross-service workflow tests and checks them with a **trace-shape oracle**. This guide
lets a reviewer reproduce the paper's headline results. Source:
<https://github.com/miaoti/MIST/>. Screencast: see §9. Paper: `paper/main_issta.tex`.

## 0. TL;DR for reviewers (start here)
- **Zero-infra path (≈10 min, no SUT, no cluster):** the shipped trace oracle reproduces
  both headline detections directly from **committed traces** — `HiddenDownstreamFailure`
  on Bookinfo/Online-Boutique and `ResponseEnvelope` (soft error) on TrainTicket. This is
  both the kick-the-tires smoke test **and** a genuine result-reproduction (§5; one command:
  `evaluation/run-offline-oracle.sh` builds the jar from the current commit, then runs both G2
  oracle checks). Committed **in-process** full-loop run reports (live SUT, generate→execute→oracle
  in one JVM) are under `docs/main-contribution/evidence/{bookinfo,boutique}_inprocess_e2e/`.
- **Light SUTs (kind/Docker):** Bookinfo, Sock Shop, Online Boutique deploy on a laptop-class
  kind cluster (§6.2).
- **Heavy SUT (optional, gated):** the 40-service TrainTicket needs a beefy host; the offline
  path (§5) already evidences its claims, so a full TrainTicket run is **optional** (§6.3).

## 1. Badges targeted (STATUS)
- **Artifacts Available** — public repo + an archival Zenodo DOI of the evaluated commit (§11).
- **Artifacts Evaluated — Functional** — every headline claim is backed by a committed
  command + output (§7); the oracle harnesses + SUT bundles are self-contained.
- **Reusable** (aspirational) — `mist-core` is framework-agnostic; each SUT is a self-contained
  bundle under `evaluation/suts/<sut>/` that a user can copy to onboard a new SUT.
- **Results Reproduced** — the §5 offline path lets a reviewer re-obtain the headline detections.

## 2. Requirements
### 2.1 Hardware
| Path | Needs |
|---|---|
| Offline oracle (§5) | any laptop; ~2 GB RAM; minutes |
| Light SUTs: Bookinfo / Sock Shop / Online Boutique (§6.2) | kind/Docker host, ~8–16 GB RAM |
| Heavy SUT: TrainTicket (§6.3) | **a host that can run ~40 JVMs** — 16+ cores recommended, ~16 GB RAM; an 8-core box running anything else concurrently will NOT converge |
### 2.2 Software
- **OS:** Linux x86_64 is the verified platform. **macOS:** pre-install
  `kind`/`kubectl`/`istioctl` via Homebrew (the deploy scripts' auto-install fetches
  linux-amd64 binaries). **Windows: use WSL2 (Ubuntu)** with Docker Desktop's WSL2
  integration enabled — the scripts then run unmodified; native PowerShell is not
  supported (the SUT scripts are bash).
- **JDK 21** (a JRE is **not** enough — MIST compiles generated tests, and the single-file
  oracle harnesses are source-launched). Set `JAVA_HOME` to the JDK.
- **Maven 3.9+** (to build the jar). **Docker** + **docker compose v2** (SUTs). **kind** +
  **kubectl** + **Istio 1.30** (Bookinfo/Sock Shop/Online Boutique). **git**, **curl**.
### 2.3 Accounts / keys / external deps
- An LLM for value synthesis + the soft-error classifier: **DeepSeek API key**
  (`export DEEPSEEK_API_KEY=...` or place it at `.api_keys/DEEPSEEK_API_KEY`) **or** a local
  **Ollama** (`ollama pull qwen3-coder:30b`). The §5 `HiddenDownstreamFailure` checks need **no LLM**;
  only the `ResponseEnvelope` soft-error check calls the LLM (one live call, cached for repeats).

## 3. Layout
```
mist-cli/target/mist.jar                         # the tool (after `mvn -DskipTests install`)
evaluation/suts/{bookinfo,sockshop,boutique,trainticket}/   # one self-contained bundle per SUT
   deploy/deploy.sh   README.md   MANIFEST.json   openapi/   traces/   *.properties
   OracleCheck.java                              # offline HiddenDownstreamFailure on a captured trace
   trainticket/ResponseEnvelopeLiveCheck.java    # offline ResponseEnvelope (soft error) via LLM
docs/main-contribution/evidence/                 # committed traces + verbatim oracle transcripts
debug/negative_test/runs/run22-fault-detection-10of10.txt   # TrainTicket 10/10 detection report
```

## 4. Install
```bash
git clone https://github.com/miaoti/MIST && cd MIST
export JAVA_HOME=/path/to/jdk21          # a JDK, not a JRE
mvn -q -DskipTests install               # builds mist-cli/target/mist.jar
```

## 5. Getting Started — ≈10-min offline smoke = result reproduction (no SUT, no LLM*)
Run MIST's **shipped** oracle on **committed** traces; the trace oracle fires where a
status/schema/body oracle passes. (*The two `HiddenDownstreamFailure` checks need no LLM.)
```bash
# (G2) Bookinfo: 200 entry hides a swallowed ratings 503 -> HIDDEN_DOWNSTREAM_FAILURE FIRES (ERROR)
"$JAVA_HOME/bin/java" -cp mist-cli/target/mist.jar evaluation/suts/bookinfo/OracleCheck.java \
  docs/main-contribution/evidence/bookinfo_e2e_traces/masked_reviews_ratings_outage.json \
  "GET /api/v1/products/0/reviews"

# (G2, gRPC) Online Boutique: clean 200 HTML hides a swallowed adservice gRPC ERROR
#   -> HIDDEN_DOWNSTREAM_FAILURE FIRES on the outage traces (7 of 12); healthy controls stay silent
"$JAVA_HOME/bin/java" -cp mist-cli/target/mist.jar evaluation/suts/boutique/OracleCheck.java \
  docs/main-contribution/evidence/boutique_e2e_traces/boutique_adservice_outage.json "GET /"
```
Expected: each prints `RESPONSE-LEVEL oracle: PASS (misses)` and `TRACE oracle
HIDDEN_DOWNSTREAM_FAILURE: FIRES`. The healthy control traces in the same dir stay silent.

```bash
# (G1) TrainTicket soft error: 200 + {"status":0,...} -> RESPONSE_ENVELOPE FIRES (needs an LLM)
"$JAVA_HOME/bin/java" -cp mist-cli/target/mist.jar \
  -Dllm.openai_compatible.enabled=true \
  -Dllm.openai_compatible.url=https://api.deepseek.com/v1/chat/completions \
  -Dllm.openai_compatible.model=deepseek-chat \
  -Dllm.openai_compatible.api.key="$(cat .api_keys/DEEPSEEK_API_KEY)" \
  -Dmst.oracle.shape.invariants.span_tree.enabled=false \
  -Dmst.oracle.shape.invariants.status_propagation.enabled=false \
  evaluation/suts/trainticket/ResponseEnvelopeLiveCheck.java
```
Expected: `RESPONSE_ENVELOPE: FAIL ... status=0 classified as failure (LLM, cached)`. A
committed transcript is at `docs/main-contribution/evidence/responseenvelope_live_softerror.txt`.

## 6. Step-by-step (full, live)
### 6.2 Light SUTs — Bookinfo / Sock Shop / Online Boutique (kind/Docker)
Each bundle is self-contained; from the repo root:
```bash
evaluation/suts/bookinfo/deploy/deploy.sh         # kind + Istio + Jaeger + Bookinfo (~8 min)
# (deploy.sh also patches the Jaeger addon with a startupProbe and waits for its
#  rollout — the stock addon keeps badger on an emptyDir with only a ~30s liveness
#  window and crash-loops on long-lived clusters once the store grows; verified +
#  hardened 2026-06-10, see debug/a-rank-fixes/VALIDATION-2026-06-10.md)
# start the two port-forwards the deploy prints. Run each inside a restart loop —
# a bare `kubectl port-forward` dies with "lost connection to pod" within minutes,
# which would turn the later test runs into bogus connection failures
# (run-oracle-e2e.sh also self-heals these if the gateway is unreachable):
( while true; do kubectl port-forward -n istio-system svc/istio-ingressgateway 8080:80; sleep 2; done ) &
( while true; do kubectl port-forward -n istio-system svc/tracing 16686:80; sleep 2; done ) &
# one MIST generation run against the live SUT (run-oracle-e2e.sh replays the
# tests it generates under .runtime/; needs a DeepSeek key for value synthesis):
mkdir -p evaluation/suts/bookinfo/.runtime
( cd evaluation/suts/bookinfo/.runtime && \
  DEEPSEEK_API_KEY="$(cat ../../../../.api_keys/DEEPSEEK_API_KEY)" \
  "$JAVA_HOME/bin/java" -jar ../../../../mist-cli/target/mist.jar ../bookinfo-demo.properties )
# run the end-to-end oracle matrix (the script toggles the real ratings outage
# itself and restores it; inject-ratings-outage.sh on|off is the manual toggle):
evaluation/suts/bookinfo/run-oracle-e2e.sh        # 4-case sensitivity/specificity, see its README
evaluation/suts/bookinfo/deploy/deploy.sh teardown
```
Online Boutique + Sock Shop follow the same shape (`evaluation/suts/{boutique,sockshop}/deploy/deploy.sh`
+ each bundle's `README.md`). Online Boutique shows the same `HiddenDownstreamFailure` over gRPC;
Sock Shop shows the `ResponseEnvelope` soft error (`GET /catalogue` -> 200 + `{status_code:500}`).

### 6.3 Heavy SUT — TrainTicket (40 services) — OPTIONAL
> **Resource caveat (honest):** TrainTicket on docker-compose runs ~40 JVMs and is CPU-heavy at
> startup (first run also **builds 7 fault images, ~40 min**). On an 8-core host (especially with
> another cluster running) it will **not converge** (it thrashes to a very high load). Use a host
> with adequate cores, or the FudanSELab-recommended k8s deploy. **The §5 offline path already
> evidences TrainTicket's claims**, so this full run is optional for reviewers without a beefy host.
```bash
evaluation/suts/trainticket/deploy/deploy.sh      # clone fault source, BUILD 7 fault images, compose up
# zero-infra generation sanity check (no SUT, no LLM):
( cd evaluation/suts/trainticket/.runtime 2>/dev/null || mkdir -p evaluation/suts/trainticket/.runtime; \
  cd evaluation/suts/trainticket/.runtime && \
  "$JAVA_HOME/bin/java" -jar ../../../../mist-cli/target/mist.jar ../trainticket-demo-noexec.properties )
# full detection run against the LOCAL SUT (generate -> in-process compile -> execute -> faults):
evaluation/suts/trainticket/run-oracle-e2e.sh     # needs JDK 21 on JAVA_HOME + a DeepSeek key
evaluation/suts/trainticket/deploy/deploy.sh teardown
```
The 7 fault-bearing services are built from the public fault-injection source
(`https://github.com/AsifShaafi/train-ticket-injection`, branch `injection`); the other ~33 are the
upstream public `codewisdom/*` images. Built-in account `admin`/`222222`. See the bundle README.

## 7. Claim → evidence map
| Paper claim | Reproduce via | Evidence / output | Needs |
|---|---|---|---|
| Bookinfo: 200 hides a swallowed downstream 5xx; trace oracle catches it, response-level misses (Fig.1) | §5 cmd 1 / §6.2 | `bookinfo_e2e_traces/`, `bookinfo_e2e_pipeline.md` | offline / kind |
| Online Boutique: same over gRPC, clean body (7 of 12 committed outage traces fire — every frontend trace through the failed adservice — 0 healthy; fresh re-capture confirms 24/40, 0/30) | §5 cmd 2 / §6.2 | `boutique_e2e_traces/` (+ `*_recapture.json`), `boutique_e2e_pipeline.md` | offline / kind |
| Full in-process loop on a live SUT: MIST generates+executes its own tests and the oracle fires in one JVM (Bookinfo HTTP → ERROR/red; Online Boutique gRPC → WARN) | §6.2 (live) / inspect committed report | `bookinfo_inprocess_e2e/` (166 tests, ERROR), `boutique_inprocess_e2e/` (579 tests, WARN) | kind / committed report |
| TrainTicket soft error (200 + status:0) caught by ResponseEnvelope (§2) | §5 cmd 3 | `responseenvelope_live_softerror.txt` | offline + 1 LLM call |
| TrainTicket: MIST detects all 10 injected faults / 15,036 tests (§5) | §6.3 (or inspect) | `debug/negative_test/runs/run22-fault-detection-10of10.txt` | committed report / live=beefy host |
| Sock Shop soft error (200 + {status_code:500}) | inspect / §6.2 | `sockshop_softerror/sockshop_catalogue_outage.json` | committed body / kind |
| 265 operations across 37 services | inspect spec | `evaluation/suts/trainticket/openapi/merged_openapi_spec.yaml` | offline |

**Cannot be reviewer-reproduced without the right hardware:** the full live TrainTicket 10/10 run
(needs a host that can host 40 services). The committed run report + the §5 offline path stand in.

**Binary drift note (2026-06-10).** Four pipeline fixes landed after the committed runs above were
produced, so a fresh run with a current binary diverges from those artifacts in known ways.
(1) The post-shatter dedup pass (Phase 3.5) was dead code. Fresh generation runs now drop duplicate
single-root partitions, so re-running generation can emit fewer test classes than the committed
reports (15,036 TrainTicket / 166 Bookinfo / 579 Boutique). The committed reports remain faithful
records of the binaries that produced them. (2) Oracle-anomaly "hits" totals in committed
fault-detection reports are inflated: a step whose verdict failed a positive variant was recorded
twice (success path + catch path, same marker). The current binary records it once — validated
live on Bookinfo with a real ratings outage: the pre-fix binary reports `Hits: 2` and the fixed
binary `Hits: 1` for one byte-identical executed test. "Distinct" anomaly counts are unaffected,
and so are the offline OracleCheck numbers (7/7, 24/40, 0/30). (3) Declared query parameters are now
emitted into requests (previously dropped at the writer), so Sock Shop catalogue re-runs exercise
their parameters. (4) The shipped TrainTicket input-fetch registries were reset to successRate=0
(stale pre-fix scores blocked the cold-start producer ranking). None of these change a paper claim:
the 10/10 TrainTicket confirmations come from the SUT's own fault registry, and the
hidden-downstream verdicts reproduce offline from committed traces.

Live validation of (1)–(4) (2026-06-10, `debug/a-rank-fixes/VALIDATION-2026-06-10.md`): a fresh
Sock Shop run (216 executed tests, exit 0) sent 301/301 catalogue requests WITH their query strings
(front-end access log; previously 0), with sniper fault values arriving intact in query position,
and emitted exactly one test class per root API (no duplicates). On the live TrainTicket,
`TTEndStationLiveCheck` confirms smart fetch grounds `endStation` to a real station name with the
de-poisoned registry. For (2), a minimal live Bookinfo run (1 scenario × 1 positive variant, real
ratings outage, healthy Jaeger) executed the byte-identical generated test under both binaries:
`b5266f62` (pre-fix) reports `HIDDEN_DOWNSTREAM_FAILURE … Hits: 2`, the current binary `Hits: 1`,
each with exactly 1 executed test case — the double-count is gone end-to-end, not just in unit
tests (`FaultDetectionTracker*` suites: 27/27 green).

## 8. LLM determinism & variance
Value synthesis + the soft-error classifier use an LLM, so generated test *values* and the exact
fault-detection *count* vary run to run (`run22` is a representative **10/10** run). The §5 oracle
checks run on **committed traces** and are **deterministic** regardless of LLM availability — that
is why they are the reproduction path of record.

## 9. Screencast
A 3–5 min screencast of the bundled demo: see the URL at the end of the paper abstract
(`paper/main_issta.tex`) / the repository README.

## 10. Troubleshooting
- **`no external javac` / compile fails** → you are on a JRE; set `JAVA_HOME` to a JDK 21.
- **TrainTicket won't converge / load skyrockets** → too few cores for 40 services; see §6.3 caveat.
- **`docker` permission denied** → add your user to the `docker` group (or use sudo).
- **`kind create cluster` fails with `could not find a log line that matches "Reached target
  .*Multi-User System"`** → the stock Linux inotify limits are too low for a kind node
  (typical on Ubuntu when other watchers are running):
  `sudo sysctl fs.inotify.max_user_watches=1048576 fs.inotify.max_user_instances=8192`.
  Also plan on **one kind cluster per host** — a second concurrent cluster on a small box
  can time out at `kubeadm` control-plane bootstrap.
- **`deploy.sh` times out waiting for a pod** → slow image pulls on a busy host; the script
  is idempotent, just re-run it (already-created resources are skipped).
- **Windows/WSL2 notes** → enable Docker Desktop's WSL2 integration for the Ubuntu distro
  (otherwise `docker` is absent inside WSL); the inotify sysctl above applies inside WSL2
  too; WSL2 auto-forwards `localhost`, so the Windows browser reaches the port-forwards
  directly; `allure open` cannot launch a Windows browser from WSL — open the URL it
  prints manually (or use `allure serve`).
- **LLM step fails** → set `DEEPSEEK_API_KEY` (or switch to Ollama); only the ResponseEnvelope
  check needs it.
- **Bookinfo/Boutique trace fetch empty** → allow a few seconds for Jaeger ingest before the oracle.
- **Jaeger pod in CrashLoopBackOff on a LONG-LIVED cluster** → the stock Istio addon keeps badger
  on an emptyDir (survives container restarts) with a ~30s liveness window and no startupProbe;
  after days of 100%-sampled traffic, startup outlives the window and the kubelet kills it forever.
  Deploys made by the current `deploy.sh` are immune (it patches in a 600s startupProbe). On a
  cluster deployed before the hardening: `kubectl rollout restart deployment/jaeger -n istio-system`
  (the NEW pod gets an empty store and starts in seconds; traces are demo-scoped, losing them is
  fine), then re-run `deploy.sh` to pick up the startupProbe. Diagnosis + evidence:
  `debug/a-rank-fixes/VALIDATION-2026-06-10.md`.

## 11. License & citation
- License: see `LICENSE` (**LGPL-3.0**).
- Archived snapshot (the **Available** badge): the paper cites the reserved DOI
  <https://doi.org/10.5281/zenodo.20514985>. The deposit must be **published** from the
  final evaluated commit before submission — a reserved-but-unpublished DOI resolves to 404.
- Cite the paper (`paper/main_issta.tex`) and the repository.
