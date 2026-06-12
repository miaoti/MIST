# MIST Screencast — Production Script (SPLASH/ISSTA 2026 Tool Demonstrations)

**Status:** every command, output snippet, and wall-clock figure was executed
and measured on 2026-06-09/10 (see §9 Feasibility ledger and
`debug/reproduce/README.md` V1–V12). The one beat whose *console form* you
must confirm yourself at the dry run is marked ⚠ in §9.

**Reading rule: nothing pre-exists.** Every artifact the camera touches — the
jar, the cluster, the Allure report, the checksum file, the browser tabs — is
created on the recording machine by §4 (prep). If you are on a fresh computer,
§4 is the complete bring-up; after it finishes, §3's inventory lists exactly
what exists and where.

---

## 1. What the venue requires (checked 2026-06-10)

From the SPLASH/ISSTA 2026 Tool Demonstrations call
(<https://conf.researchr.org/track/issta-2026/splash-issta-2026-posters-and-tool-demonstrations>):

- The paper's **Tool Availability** section must contain **(1)** the tool URL,
  **(2)** **"a YouTube link demonstrating the use of the tool as of the
  current version"**, **(3)** an archived version (Zenodo DOI).
- **No explicit video length limit**; sibling 2026 tool tracks (ICST, ICSME)
  codify **3–5 min with voice-over**, unlisted YouTube fine. Target **≤ 4:45**.
- Criteria: relevance, soundness, presentation quality, usefulness.

Consequences: record at/after the **frozen submission commit** ("as of the
current version"); the cited Zenodo deposit must be **published** and should
also carry the MP4.

**Structure note.** The video's protagonist is the TOOL: of ~4:30 runtime,
~2:30 is MIST itself running (one-command pipeline, its console verdicts, its
Allure report, its generator, its LLM check); the *problem* gets one 30-second
beat as motivation, and the OracleCheck harness beat is explicitly narrated as
"replaying MIST's shipped invariant so you can see what fired inside".

---

## 2. Measured timings (plan cuts around these)

| Step | Real wall time (measured) | Screen time |
|---|---|---|
| Outage inject + ratings settles to 503 | 10–40 s | ~8 s (cut the poll) |
| **Full in-process run `java -jar mist.jar bookinfo-demo.properties`** (generate→compile→execute 166 tests→oracle) | **~30 min** | **~45 s: launch live, "⏩ ~30 min" caption, tail** |
| Jaeger ingest before trace fetch | ~6 s | 0 s (cut) |
| `OracleCheck` single invocation | ~10 s | ~10 s |
| noexec generation (26 deduplicated scenarios, offline) | ~2.5 min | ~10 s (time-lapse) |
| `ResponseEnvelopeLiveCheck` (1 real DeepSeek call) | 20–40 s | ~10 s (cut LLM wait) |
| `allure generate` (2,710 result files) | 30–60 s | 0 s (prep) |
| `evaluation/run-offline-oracle.sh` (incl. mvn build) | ~5–6 min | ~15 s (launch + tail) |
| `mvn -q -DskipTests install` (fresh clone) | ~4–6 min | 0 s (prep) |
| TrainTicket live | hours, 16-core host | 0 s (never live; run22 report only) |

Time-lapse honesty: keep the progress bar / terminal clock visible across the
jump and overlay a "⏩ ~30 min" caption — a visible cut, never a hidden one.

Why the LLM cache cannot shorten Scene 2 (asked and answered): the ~30 min is
dominated by EXECUTION — 166 tests × (request + 5 s Jaeger-ingest wait + trace
fetch + oracle) ≈ 22–28 min of physical waiting. The cache only serves LLM
calls in the generation/enhancement phases (~5–8 min), and the bookinfo run is
unseeded so `mist.llm.cache.read=auto` does not read it anyway. Hence the
design: run the full pipeline ONCE in prep (P6), show launch + a visible
time-lapse + the tail on camera. Do not swap Scene 2 to the noexec profile —
a tool demo must show the tool executing; noexec lives in Scene 5.

---

## 3. What exists on the machine after prep (the inventory the camera relies on)

| Artifact | Path / location | Created by (§4 step) |
|---|---|---|
| `mist.jar` | `mist-cli/target/mist.jar` | P2 build |
| kind cluster + Istio + Jaeger + Bookinfo, healthy | kind context `kind-mist` | P3 deploy |
| Port-forwards 8080 (ingress) / 16686 (Jaeger), self-restarting | two background loops | P4 |
| Run-1 checksums for the determinism beat | `/tmp/run1.sums` | P5 |
| **The MIST outage run** (its console log, its Allure results, its fault-detection report) | console log `/tmp/mist-bookinfo-run.log`; results `evaluation/suts/bookinfo/.runtime/target/allure-results`; report `evaluation/suts/bookinfo/.runtime/logs/fault-detection-reports/` | P6 ★ |
| **Rendered Allure report** | `/tmp/allure-report` (browser at the URL `allure open` prints) | P7 |
| Browser tab A: GitHub repo | https://github.com/miaoti/MIST | P8 |
| Browser tab B: the bookmarked **red positive `/reviews` test** in Allure | from P7 | P8 |
| DeepSeek key | `.api_keys/DEEPSEEK_API_KEY` | P1 |

---

## 4. Prep (run start-to-finish on the recording machine; ~1.5 h incl. one 30-min run; +15 min P0 on a blank box)

```bash
# P0 — blank-machine preconditions (one-time; skip any you already have).
#      Every line below corresponds to a failure we actually hit during the
#      2026-06 audit — do not trust a fresh box without them.
sudo apt-get update && sudo apt-get install -y \
    openjdk-21-jdk-headless maven git curl python3 jq        # python3: Scene 1 pretty-prints JSON
#   Docker engine (https://docs.docker.com/engine/install/), then make it
#   usable without sudo (kind runs as your user; re-login after):
sudo usermod -aG docker "$USER" && newgrp docker
docker ps                                                    # must work WITHOUT sudo
#   Stock Ubuntu inotify limits are too low for a kind node ("could not find
#   a log line that matches 'Reached target ... Multi-User System'"):
sudo sysctl fs.inotify.max_user_watches=1048576 fs.inotify.max_user_instances=8192
#   deploy.sh installs kind/kubectl/istioctl into ~/.local/bin — make sure
#   your shell sees it (Ubuntu's default .profile does; WSL sometimes not):
export PATH="$HOME/.local/bin:$PATH"
#   Recorder: install OBS (sudo apt-get install -y obs-studio) and do a mic test.

# P1 — clone at the frozen submission commit + key
git clone https://github.com/miaoti/MIST && cd MIST
mkdir -p .api_keys && <put your DeepSeek key in .api_keys/DEEPSEEK_API_KEY>
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # a JDK, not a JRE (adjust to your install)

# P2 — build (~5 min)
mvn -q -DskipTests install

# P3 — SUT up (~8 min; Linux x86_64; macOS: brew install kind kubectl istioctl first)
evaluation/suts/bookinfo/deploy/deploy.sh
kubectl get pods -A | grep -vE 'Running|Completed'   # expect EMPTY (bookinfo 2/2 in
                                                     # default, istiod/jaeger/gateways
                                                     # Running in istio-system)

# P4 — self-restarting forwards (bare port-forwards die mid-take; ports must be free:
#       ss -tlnp | grep -E ':8080|:16686' shows nothing before this)
( while true; do kubectl port-forward -n istio-system svc/istio-ingressgateway 8080:80; sleep 2; done ) &
( while true; do kubectl port-forward -n istio-system svc/tracing 16686:80; sleep 2; done ) &
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/productpage             # 200
curl -s -o /dev/null -w '%{http_code}\n' "http://localhost:16686/jaeger/api/services"  # 200

# P5 — determinism run 1 (~2.5 min) -> /tmp/run1.sums, then clear for the on-camera run 2
rm -rf mist-cli/src/test/java/trainticket_twostage_test
"$JAVA_HOME/bin/java" -Drandom.seed=42 -jar mist-cli/target/mist.jar \
    mist-cli/src/main/resources/My-Example/trainticket-demo-noexec.properties
find mist-cli/src/test/java/trainticket_twostage_test -name 'Flow_Scenario_*.java' \
    -exec sha256sum {} \; | sort > /tmp/run1.sums
rm -rf mist-cli/src/test/java/trainticket_twostage_test

# P6 ★ — THE tool run the video is built around (~30 min). Scene 2 shows its
#         launch + tail; Scene 4 shows its Allure report. Run it ONCE here as
#         the dry-run-of-record, keeping the console log:
evaluation/suts/bookinfo/workload/inject-ratings-outage.sh on
mkdir -p evaluation/suts/bookinfo/.runtime
( cd evaluation/suts/bookinfo/.runtime && \
  DEEPSEEK_API_KEY="$(cat ../../../../.api_keys/DEEPSEEK_API_KEY)" \
  "$JAVA_HOME/bin/java" -jar ../../../../mist-cli/target/mist.jar ../bookinfo-demo.properties \
  ) 2>&1 | tee /tmp/mist-bookinfo-run.log
evaluation/suts/bookinfo/workload/inject-ratings-outage.sh off
#   Confirm now (this is the ⚠ dry-run check): the closing "MIST findings"
#   block and .runtime/logs/fault-detection-reports/ name
#   HIDDEN_DOWNSTREAM_FAILURE findings (committed reference for the expected
#   content: docs/main-contribution/evidence/bookinfo_inprocess_e2e/).

# P7 — render the Allure report from that exact run (~40 s)
allure/bin/allure generate evaluation/suts/bookinfo/.runtime/target/allure-results \
    -o /tmp/allure-report --clean
allure/bin/allure open /tmp/allure-report &    # note the URL it prints; open in the browser
#   In the report, find ONE positive /reviews test that is red with
#   "Trace Shape Oracle verdict has violation(s): [HIDDEN_DOWNSTREAM_FAILURE: ...]"
#   and BOOKMARK that page. Never show the unfiltered failure list cold —
#   negative variants fail red by design on Bookinfo (it accepts any input).

# P8 — browser: tab A = github.com/miaoti/MIST, tab B = the bookmarked red test.
#   Terminal: dark theme, >=18pt, ~120x30, export PS1='$ ', clear.
#   Recorder: OBS 1080p/30fps, mic check. Restore after: ratings replicas=1.
```

Re-recording later? P5's run-1 sums and P6/P7's report stay valid as long as
the commit doesn't change; only P4's forwards need restarting after a reboot.

---

## 5. Scene-by-scene (target 4:25–4:40)

**SAY** = narration verbatim (≈140 wpm) · **DO** = on screen · **EXPECT** =
verified output to point at · **CUT** = edit note.

---

### Scene 0 — Title (0:00 – 0:18)

**DO:** slide: tool name + one-liner + venue + authors + `github.com/miaoti/MIST`.

**SAY:**
> "This is MIST, a test generator for microservice REST APIs. It turns
> OpenTelemetry traces and an OpenAPI spec into runnable cross-service tests,
> and judges them with a trace-shape oracle. Let me first show you the
> failure class that motivates it."

---

### Scene 1 — The problem, in 30 seconds (0:18 – 0:48)

**DO:**
```bash
evaluation/suts/bookinfo/workload/inject-ratings-outage.sh on
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/products/0/ratings
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/products/0/reviews
curl -s http://localhost:8080/api/v1/products/0/reviews | python3 -m json.tool | head -8
```

**EXPECT (verified):** `503` (direct), then `200` (reviews), body carrying
`"rating": {"error": "Ratings service is currently unavailable"}`.

**SAY:**
> "Istio Bookinfo, live on a kind cluster. I take the ratings service down —
> a real outage. Called directly it fails loudly: 503. But reviews, which
> depends on ratings, still answers 200 with a perfectly valid body — the
> failure was swallowed. Status oracles, schema oracles, body oracles: all
> green. This is a hidden downstream failure."

**CUT:** settle delay between inject and the 503.

---

### Scene 2 — THE TOOL, one command, end to end (0:48 – 1:40) ★ core

**DO (live):**
```bash
cd evaluation/suts/bookinfo/.runtime
DEEPSEEK_API_KEY="$(cat ../../../../.api_keys/DEEPSEEK_API_KEY)" \
"$JAVA_HOME/bin/java" -jar ../../../../mist-cli/target/mist.jar ../bookinfo-demo.properties
```
Let the banner + generation progress run ~8 s on camera → **"⏩ ~30 min"
caption** → cut to the END of the run. (To avoid waiting again on the day,
replay the tail of `/tmp/mist-bookinfo-run.log` from prep P6 — same commit,
same SUT state; narrate it honestly as "the run we recorded earlier".) End on
the closing `MIST findings` block, then:
```bash
tail -20 logs/fault-detection-reports/*.txt
```

**EXPECT:** the findings block / report names `HIDDEN_DOWNSTREAM_FAILURE` on
the `/reviews` positive test(s) with the swallowed `reviews → ratings` 503
(⚠ confirm exact lines at prep P6; committed reference:
`docs/main-contribution/evidence/bookinfo_inprocess_e2e/`).

**SAY:**
> "Now the tool. One jar, one properties file — this is the entire interface.
> MIST reads the OpenAPI spec and the captured traces, generates
> cross-service scenarios, compiles them, executes them against the live
> system, fetches each test's distributed trace from Jaeger, and runs its
> trace-shape oracle — all in this one process. Thirty minutes later: one
> hundred sixty-six tests executed, and the run report flags the reviews
> tests red — hidden downstream failure, the swallowed reviews-to-ratings
> five-oh-three — caught by the oracle, not by the HTTP response."

---

### Scene 3 — Inside the verdict (1:40 – 2:10)

**DO:**
```bash
cd ../../../..   # repo root
curl -s "http://localhost:16686/jaeger/api/traces?service=productpage.default&limit=20&lookback=10m" -o /tmp/live-traces.json
"$JAVA_HOME/bin/java" -cp mist-cli/target/mist.jar evaluation/suts/bookinfo/OracleCheck.java \
    /tmp/live-traces.json "GET /api/v1/products/0/reviews"
```

**EXPECT (verified live 2026-06-10):**
```
  --> RESPONSE-LEVEL oracle (status/schema/soft-error sees the client response): PASS  (root is 2xx — looks successful, MISSES the failure)
  --> TRACE oracle HIDDEN_DOWNSTREAM_FAILURE: FIRES  severity=ERROR
      reviews.default ──▶ ratings.default.svc.cluster.local:9080/* (http=503 otel=ERROR)
```

**SAY:**
> "What did the oracle actually see? Here I replay MIST's shipped invariant —
> the same class the pipeline just used — on the live traces, so you can read
> the verdict. Response-level: PASS, it only sees the 200. The
> hidden-downstream invariant: FIRES at severity ERROR, naming the swallowed
> span. It's structural — label-free, and it needs no LLM."

**DO (last frame):** `evaluation/suts/bookinfo/workload/inject-ratings-outage.sh off`

**CUT:** the 6-s Jaeger ingest before the fetch.

---

### Scene 4 — The report a developer sees (2:10 – 2:35)

**DO:** switch to browser tab B — the **bookmarked red positive test** in the
Allure report rendered at prep P7 from the Scene-2 run. One click, zoom on the
failure message.

**EXPECT:** `Positive variant failed — Trace Shape Oracle verdict has
violation(s): [HIDDEN_DOWNSTREAM_FAILURE: reviews.default ──▶ ratings... (http=503 otel=ERROR)]`

**SAY:**
> "The same run renders a standard Allure report. Here's the reviews test:
> red, failed by the trace oracle, the swallowed call named in the message.
> The negative variants around it fail by design — Bookinfo accepts any
> input — and every red test names its single cause."

---

### Scene 5 — Generation at scale, byte-reproducible (2:35 – 3:15)

**DO:**
```bash
"$JAVA_HOME/bin/java" -Drandom.seed=42 -jar mist-cli/target/mist.jar \
    mist-cli/src/main/resources/My-Example/trainticket-demo-noexec.properties
```
~5 s of progress bar → time-lapse → then:
```bash
find mist-cli/src/test/java/trainticket_twostage_test -name 'Flow_Scenario_*.java' \
    -exec sha256sum {} \; | sort > /tmp/run2.sums
diff /tmp/run1.sums /tmp/run2.sums && echo IDENTICAL
grep -A3 "FAULT COVERAGE SUMMARY" debug/negative_test/runs/run22-fault-detection-10of10.txt
```

**EXPECT (verified):** `IDENTICAL`; `Total Injected Faults: 10 / Detected
Faults: 10 (100.0%)`.

**SAY:**
> "The generator also runs fully offline: the bundled TrainTicket demo — a
> 265-operation spec plus captured traces — no SUT, no key, no network.
> Negative variants follow the Sniper strategy: exactly one fault each, so
> every red test has exactly one cause. Under a fixed seed the suite is
> byte-identical — I generated it before recording, and every
> checksum matches. Against the live forty-service
> TrainTicket, this engine generated fifteen thousand thirty-six tests, and
> the fault registry confirms ten out of ten injected faults detected."

---

### Scene 6 — Soft errors: the LLM-backed check (3:15 – 3:45)

**DO:**
```bash
"$JAVA_HOME/bin/java" -cp mist-cli/target/mist.jar \
  -Dllm.openai_compatible.enabled=true \
  -Dllm.openai_compatible.url=https://api.deepseek.com/v1/chat/completions \
  -Dllm.openai_compatible.model=deepseek-chat \
  -Dllm.openai_compatible.api.key="$(cat .api_keys/DEEPSEEK_API_KEY)" \
  -Dmst.oracle.shape.invariants.span_tree.enabled=false \
  -Dmst.oracle.shape.invariants.status_propagation.enabled=false \
  evaluation/suts/trainticket/ResponseEnvelopeLiveCheck.java
```

**EXPECT (verified):**
```
Soft-error response (HTTP 200): {"status":0,"msg":"start or end station not include in stationList.","data":null}
Status-class oracle: PASS (HTTP is 200)
  RESPONSE_ENVELOPE: FAIL  severity=ERROR  detail=status=0 classified as failure (LLM, cached)
```

**SAY:**
> "The second failure class: soft errors — HTTP 200 wrapping a domain
> rejection, like TrainTicket's status zero. MIST's Response-Envelope
> invariant classifies the unseen body value with one LLM call, caches the
> rule, and fails it. The same check flags Sock Shop's
> 200-with-status-code-500. And the hidden-downstream invariant crosses
> protocols: on Online Boutique the swallowed call is gRPC, and it fires on
> seven of twelve committed outage traces — zero on the healthy controls."

**CUT:** LLM wait to ~3 s.

---

### Scene 7 — Reproduce it yourself + close (3:45 – 4:25)

**DO:**
```bash
evaluation/run-offline-oracle.sh
```
Launch on camera → cut the ~5-min build → tail:
```
==> Done. Both fired offline -- no SUT, no LLM. (Healthy-control traces in the
    same directories stay silent; see REPRODUCE.md for the full matrix.)
```
Switch to tab A (GitHub). End card 5 s: `github.com/miaoti/MIST` · Zenodo DOI ·
"REPRODUCE.md — start at §5".

**SAY:**
> "Everything here reproduces from a fresh clone: one script rebuilds the jar
> and replays both hidden-downstream detections from committed traces — no
> cluster, no LLM, about ten minutes. REPRODUCE-dot-md maps every claim to a
> command and its committed evidence; four systems ship as self-contained
> bundles. MIST is open source under LGPL. Thanks for watching."

---

## 6. Copy-paste appendix (camera order)

```bash
# Scene 1
evaluation/suts/bookinfo/workload/inject-ratings-outage.sh on
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/products/0/ratings
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/products/0/reviews
curl -s http://localhost:8080/api/v1/products/0/reviews | python3 -m json.tool | head -8

# Scene 2 (launch live; the tail can replay /tmp/mist-bookinfo-run.log from prep P6)
cd evaluation/suts/bookinfo/.runtime
DEEPSEEK_API_KEY="$(cat ../../../../.api_keys/DEEPSEEK_API_KEY)" \
"$JAVA_HOME/bin/java" -jar ../../../../mist-cli/target/mist.jar ../bookinfo-demo.properties
tail -20 logs/fault-detection-reports/*.txt

# Scene 3
cd ../../../..
sleep 6
curl -s "http://localhost:16686/jaeger/api/traces?service=productpage.default&limit=20&lookback=10m" -o /tmp/live-traces.json
"$JAVA_HOME/bin/java" -cp mist-cli/target/mist.jar evaluation/suts/bookinfo/OracleCheck.java \
    /tmp/live-traces.json "GET /api/v1/products/0/reviews"
evaluation/suts/bookinfo/workload/inject-ratings-outage.sh off

# Scene 4: browser only (tab B from prep P7)

# Scene 5
"$JAVA_HOME/bin/java" -Drandom.seed=42 -jar mist-cli/target/mist.jar \
    mist-cli/src/main/resources/My-Example/trainticket-demo-noexec.properties
find mist-cli/src/test/java/trainticket_twostage_test -name 'Flow_Scenario_*.java' \
    -exec sha256sum {} \; | sort > /tmp/run2.sums
diff /tmp/run1.sums /tmp/run2.sums && echo IDENTICAL
grep -A3 "FAULT COVERAGE SUMMARY" debug/negative_test/runs/run22-fault-detection-10of10.txt

# Scene 6
"$JAVA_HOME/bin/java" -cp mist-cli/target/mist.jar \
  -Dllm.openai_compatible.enabled=true \
  -Dllm.openai_compatible.url=https://api.deepseek.com/v1/chat/completions \
  -Dllm.openai_compatible.model=deepseek-chat \
  -Dllm.openai_compatible.api.key="$(cat .api_keys/DEEPSEEK_API_KEY)" \
  -Dmst.oracle.shape.invariants.span_tree.enabled=false \
  -Dmst.oracle.shape.invariants.status_propagation.enabled=false \
  evaluation/suts/trainticket/ResponseEnvelopeLiveCheck.java

# Scene 7
evaluation/run-offline-oracle.sh

# restore (not recorded)
evaluation/suts/bookinfo/workload/inject-ratings-outage.sh off
kubectl scale deploy ratings-v1 --replicas=1
```

---

## 7. Fallbacks

- **Cluster misbehaves on the day** → Scene 1 keeps only the committed
  pipeline table (`docs/main-contribution/evidence/bookinfo_e2e_pipeline.md`,
  5 s); Scene 2 uses `/tmp/mist-bookinfo-run.log` + the committed
  `bookinfo_inprocess_e2e/` report ("the run we recorded earlier"); Scene 3
  replays the **committed** outage trace:
  ```bash
  "$JAVA_HOME/bin/java" -cp mist-cli/target/mist.jar evaluation/suts/bookinfo/OracleCheck.java \
    docs/main-contribution/evidence/bookinfo_e2e_traces/masked_reviews_ratings_outage.json \
    "GET /api/v1/products/0/reviews"
  ```
- **DeepSeek down** → Scene 6 `cat`s the committed transcript
  `docs/main-contribution/evidence/responseenvelope_live_softerror.txt`.
- **Allure report unsatisfying** → drop Scene 4, give 25 s to Scene 2's tail
  + report file.
- Scenes are independent takes; stitch in the editor.

---

## 8. Post-production

- [ ] Trim per CUT notes; total 3:00–5:00 (target 4:25–4:40).
- [ ] Lower-third caption with each command name; "⏩" captions on every jump.
- [ ] Clean up auto-captions (accessibility).
- [ ] Export MP4 (H.264, 1080p/30).
- [ ] Upload YouTube (unlisted) **and** add the MP4 to the Zenodo deposit;
      publish the deposit (the paper's DOI must resolve).
- [ ] Fill `\todo{SCREENCAST-URL}` in `paper/main_issta.tex` (abstract + Tool
      Availability); update REPRODUCE §9.
- [ ] Watch once end-to-end on laptop speakers.

---

## 9. Feasibility ledger

| Beat | Verified | Evidence |
|---|---|---|
| Outage toggle, 503 settle, masked 200 + body | 2026-06-09/10 live | `debug/reproduce/evidence/bookinfo-e2e-matrix.log` + session transcript |
| **In-process run on live Bookinfo, exit 0** (Scene 2's engine) | 2026-06-09 live: 166 tests, healthy SUT | session transcript (audit V10) |
| In-process run **under outage** produces HIDDEN_DOWNSTREAM findings + report | 2026-06-02 committed run | `docs/main-contribution/evidence/bookinfo_inprocess_e2e/` — ⚠ confirm the console-tail wording at prep P6 (the only unconfirmed *formatting*; the behavior itself is committed evidence) |
| Live Jaeger-API fetch → OracleCheck FIRES on fresh traces | **2026-06-10 live, end-to-end** | session transcript |
| OracleCheck on committed traces (fallback) | 2026-06-09 fresh clone | `debug/reproduce/evidence/offline-oracle.log` |
| noexec generation ~2.5 min offline; seed-42 byte-identical (26 files at current HEAD — count re-baselined after the dedup-leak fix; 123 at the audit commit) | 2026-06-09 audit + **2026-06-11 re-run** | `debug/reproduce/README.md` addendum |
| run22 numbers (10/10, 15,036) | 2026-06-10 re-grepped | `debug/negative_test/runs/run22-fault-detection-10of10.txt` |
| ResponseEnvelopeLiveCheck exact output | 2026-06-09 + committed transcript | `docs/main-contribution/evidence/responseenvelope_live_softerror.txt` |
| Boutique 7/12 (+ recapture 24/40, 0/30) | 2026-06-10 re-run | session transcript |
| `allure generate` renders a real run (2,710 files) | **2026-06-10** | summary: 166 total |
| Red positive test with the oracle message in Allure | committed 2026-06-02 run (prep P6/P7 reproduces it) | `bookinfo_inprocess_e2e/sample_hidden_downstream_finding.txt` |
| `run-offline-oracle.sh` ~5–6 min end-to-end | 2026-06-09 fresh clone | `evidence/offline-oracle.log` |
