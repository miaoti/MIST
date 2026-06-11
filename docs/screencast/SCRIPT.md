# MIST Screencast — Production Script (SPLASH/ISSTA 2026 Tool Demonstrations)

**Status:** every command, output snippet, and wall-clock figure in this script
was executed and measured on 2026-06-09/10 on the kind-cluster host (see
§8 Feasibility ledger and `debug/reproduce/README.md` V1–V12). Nothing here is
aspirational.

---

## 1. What the venue actually requires (checked 2026-06-10)

From the SPLASH/ISSTA 2026 Tool Demonstrations call
(<https://conf.researchr.org/track/issta-2026/splash-issta-2026-posters-and-tool-demonstrations>):

- The paper's **Tool Availability** section must contain **(1)** a URL for the
  latest tool version (GitHub), **(2)** **"a YouTube link demonstrating the use
  of the tool as of the current version"**, and **(3)** an archived version
  (Zenodo DOI).
- **No explicit video length limit** is imposed. Sibling 2026 venues (ICST,
  ICSME tool tracks) codify **3–5 minutes with voice-over or annotations**,
  publicly viewable (unlisted YouTube is fine). We target **≤ 4:45**.
- Evaluation criteria: relevance to the ISSTA audience, technical soundness,
  presentation quality, usefulness.

Two compliance consequences for production:

1. **"as of the current version"** → record at (or after) the frozen
   submission commit. Do not record before the final code freeze.
2. The same Zenodo deposit that the paper cites must be **published** and
   should also contain the MP4 (link-rot insurance).

Standard structure of accepted SIGSOFT-venue demo videos (and ours): hook with
the problem → tool in one breath → live core demo → scale/evaluation evidence →
"reproduce it yourself" → availability card. Voice-over throughout, no dead
air, captions for accessibility (auto-captions cleaned up are acceptable).

---

## 2. Measured timings (plan cuts around these — real numbers, not guesses)

| Step | Real wall time (measured) | Screen time in video |
|---|---|---|
| `OracleCheck` single invocation (source-launched) | ~10 s | ~10 s (keep; narrate over JVM startup) |
| Outage inject + ratings settles to 503 | 10–40 s | ~8 s (cut the poll) |
| Jaeger ingest before trace fetch | ~6 s | 0 s (cut) |
| Trace fetch via Jaeger HTTP API | <1 s | keep |
| noexec generation (123 scenarios, offline) | **~2.5 min** | ~12 s (5 s live + time-lapse + last 3 s) |
| `ResponseEnvelopeLiveCheck` (1 real DeepSeek call) | 20–40 s | ~12 s (cut LLM wait to ~3 s) |
| `allure generate` on a real run (2,710 result files) | 30–60 s | 0 s (pre-rendered; only the browser is shown) |
| `evaluation/run-offline-oracle.sh` (incl. its mvn build, warm ~/.m2) | **~5–6 min** | ~15 s (launch + cut to tail) |
| `mvn -q -DskipTests install` (pristine clone) | ~4–6 min | 0 s (prep only, never on screen) |
| Bookinfo full in-process run (166 tests, generate→compile→execute→oracle) | **~30 min** | 0 s (prep only — produces the Allure report and the committed-style finding) |
| TrainTicket live deploy/run | hours + 16-core host | 0 s (never live; cited via run22 report) |

Rule that follows: **nothing longer than ~40 s real time is ever run on
camera**; long steps are either prep (Allure, builds) or time-lapsed with the
progress bar visible (generation).

---

## 3. Choosing the recording machine (it does NOT have to be the lab host)

Nothing in the video is tied to one machine. Requirements for the recording
host:

| Need | Why |
|---|---|
| Linux **x86_64**, ~4+ cores, **~16 GB RAM**, Docker | the kind+Istio+Bookinfo cluster (Scenes 2/5 prep); `deploy.sh` auto-installs kubectl/kind/istioctl |
| JDK 21 + Maven 3.9+ | build + source-launched harnesses (Scenes 1,3,4,6) |
| Internet + a DeepSeek key | Scene 4's one live LLM call (or swap to Ollama) |
| ~1 h of setup before the dry run | `deploy.sh` ~8 min + `mvn install` ~5 min + the ~30-min Allure prep run + checklist |

Platform caveats:
- **macOS / Apple Silicon:** `deploy.sh`'s tool auto-install hardcodes
  `linux-amd64` binaries — `brew install kind kubectl istioctl` first so the
  script's `command -v` checks skip the downloads.
- **Windows = WSL2, and that is a first-class option, not a workaround**
  (Docker Desktop on Windows runs Linux containers through WSL2 anyway).
  Recording setup that works cleanly:
  1. Install WSL2 + Ubuntu 24.04; install Docker Desktop and enable its
     **WSL2 integration** for that distro (Settings → Resources → WSL
     Integration).
  2. Inside the Ubuntu shell, follow this script exactly as on Linux —
     `deploy.sh`'s linux-amd64 auto-install is correct there, and the
     inotify sysctl from REPRODUCE §10 applies inside WSL2 too.
  3. WSL2 auto-forwards `localhost` — the Windows browser reaches
     `http://localhost:8080` / `:16686` / the Allure port directly.
  4. `allure open` cannot launch a Windows browser from WSL2; run
     `allure/bin/allure open /tmp/allure-report` and open the URL it prints
     (`http://<host>:<port>`) in Edge/Chrome manually, or `allure serve`.
  5. Record with OBS on Windows capturing Windows Terminal (Ubuntu profile)
     + the browser — visually identical to a Linux recording.
  Native Windows (PowerShell, no WSL) is **not supported** for the cluster
  scripts (they are bash and use Linux tooling); the pure-Java offline path
  would likely run but is unverified — use WSL2 uniformly.
- The TrainTicket 16-core gate does **not** apply to recording: the script
  never runs TrainTicket live (Scene 3 is the offline noexec profile; the
  10/10 figure is shown from the committed run22 report).
- The byte-identical beat (Scene 3) compares run 1 (prep) with run 2
  (on camera) **on the same machine** — cross-machine byte-identity is
  neither claimed nor needed.

## 3b. Pre-recording checklist (all BEFORE pressing record)

Each item traces to a failure we actually hit during the audit:

- [ ] **Record at the frozen submission commit** (CFP: "as of the current version").
- [ ] **Host**: the kind-cluster machine, nothing else heavy running (load >40
      makes the apiserver flaky → port-forwards die mid-take).
- [ ] **Cluster**: `evaluation/suts/bookinfo/deploy/deploy.sh` already run; all
      default-ns pods `2/2 Running`; Jaeger addon healthy (the deploy now
      patches a startupProbe — re-run deploy.sh if Jaeger crash-loops).
- [ ] **Ports 8080/16686 free of stray forwards** from other terminals:
      `ss -tlnp | grep -E ':8080|:16686'` must show nothing before you start
      yours. A stray forward to another service answers your curls with
      confusing 404s (cost us 30 min during the audit).
- [ ] **Forwards in restart loops** (bare `kubectl port-forward` dies in minutes):
      ```bash
      ( while true; do kubectl port-forward -n istio-system svc/istio-ingressgateway 8080:80; sleep 2; done ) &
      ( while true; do kubectl port-forward -n istio-system svc/tracing 16686:80; sleep 2; done ) &
      curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/productpage   # 200
      ```
- [ ] **Ratings healthy**: `kubectl scale deploy ratings-v1 --replicas=1`, wait Ready.
- [ ] **Jar built** (`export JAVA_HOME=/path/to/jdk21 && mvn -q -DskipTests install`).
      JDK 21 — a JRE breaks the source-launched harnesses.
- [ ] **DeepSeek key** at `.api_keys/DEEPSEEK_API_KEY` (Scene 5 makes one real call).
- [ ] **Determinism prep (Scene 4)** — generate run 1's checksums beforehand:
      ```bash
      rm -rf mist-cli/src/test/java/trainticket_twostage_test
      "$JAVA_HOME/bin/java" -Drandom.seed=42 -jar mist-cli/target/mist.jar \
          mist-cli/src/main/resources/My-Example/trainticket-demo-noexec.properties
      find mist-cli/src/test/java/trainticket_twostage_test -name 'Flow_Scenario_*.java' \
          -exec sha256sum {} \; | sort > /tmp/run1.sums
      rm -rf mist-cli/src/test/java/trainticket_twostage_test     # scene re-generates
      ```
- [ ] **Allure prep (Scene 6)** — produce the red-test report the day before
      (~35 min, one command sequence, verified pattern):
      ```bash
      evaluation/suts/bookinfo/workload/inject-ratings-outage.sh on
      mkdir -p evaluation/suts/bookinfo/.runtime
      ( cd evaluation/suts/bookinfo/.runtime && \
        DEEPSEEK_API_KEY="$(cat ../../../../.api_keys/DEEPSEEK_API_KEY)" \
        "$JAVA_HOME/bin/java" -jar ../../../../mist-cli/target/mist.jar ../bookinfo-demo.properties )   # ~30 min
      evaluation/suts/bookinfo/workload/inject-ratings-outage.sh off
      allure/bin/allure generate evaluation/suts/bookinfo/.runtime/target/allure-results \
          -o /tmp/allure-report --clean                                # ~40 s, verified
      allure/bin/allure open /tmp/allure-report &                      # leave the tab open
      ```
      In the report, pre-locate one **positive** `/reviews` test that is red
      with the message `Trace Shape Oracle verdict has violation(s):
      [HIDDEN_DOWNSTREAM_FAILURE: ...]` (same text as the committed
      `docs/main-contribution/evidence/bookinfo_inprocess_e2e/sample_hidden_downstream_finding.txt`).
      Bookmark that exact test page. Do NOT show the unfiltered failure list
      cold: negative variants legitimately fail on Bookinfo (it accepts any
      input), and an unexplained wall of red invites the wrong question.
- [ ] **Browser tabs** (zoom ~125%): ① the GitHub repo (default branch must
      already be `inject-detection`), ② the bookmarked red Allure test,
      ③ (optional) Jaeger UI `http://localhost:16686/jaeger/search`,
      Service=`productpage.default`.
- [ ] **Terminal**: dark theme, ≥18 pt, ~120×30, `export PS1='$ '`, `clear`
      between scenes. Pre-type commands in a side file to paste from.
- [ ] **Recorder**: 1080p/30fps, system audio off, mic test, OBS or similar.
- [ ] **Dry-run the full script once with a stopwatch.**
- [ ] After recording: `inject-ratings-outage.sh off` (restore), kill forwards.

---

## 4. Scene-by-scene

Notation — **SAY**: narration, read verbatim (≈140 wpm). **DO**: exactly what
is on screen. **EXPECT**: verified output to point at. **CUT**: editing note.

Total target **4:35–4:45**.

---

### Scene 0 — Title card (0:00 – 0:20)

**DO:** full-screen slide:

> **MIST — Microservice Integration & Scenario Tester**
> *Trace-driven test generation and a trace-shape oracle for microservice REST APIs*
> SPLASH/ISSTA 2026 Tool Demonstrations — <paper authors>
> github.com/miaoti/MIST

**SAY:**
> "This is MIST, a test generator for microservice REST APIs. MIST turns
> OpenTelemetry traces and an OpenAPI spec into runnable cross-service tests,
> and judges them with a trace-shape oracle. I'll show you the failure class
> that motivates it: a service that answers HTTP 200 while one of its
> dependencies has actually failed — a failure no status, schema, or body
> oracle can see."

**CUT:** hard cut to terminal.

---

### Scene 1 — The tool in one breath (0:20 – 0:45)

**DO:**
```bash
ls mist-core mist-llm mist-cli evaluation/suts
```
(One command, one screen: the three modules + the four SUT bundles.)

**SAY:**
> "MIST is three Maven modules — the pipeline and oracle in mist-core, LLM
> dispatch with a call cache in mist-llm, and a single entry point in
> mist-cli: one jar, one properties file per system. The repo bundles four
> ready-to-run systems — TrainTicket, Bookinfo, Sock Shop, and Online
> Boutique — and a step-by-step reproduction guide, REPRODUCE-dot-md.
> Right now, Istio Bookinfo is live on a local kind cluster with Jaeger
> tracing. Let's break it."

**CUT:** none.

---

### Scene 2 — LIVE: the hidden downstream failure (0:45 – 2:10) ★ core

**Beat A — break the dependency (0:45 – 1:02)**

**DO:**
```bash
evaluation/suts/bookinfo/workload/inject-ratings-outage.sh on
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/products/0/ratings
```

**EXPECT:** `ratings OUTAGE = ON (hidden-downstream active)`; the curl prints
`503` (CUT the settle delay; re-take if the first on-camera curl shows 200).

**SAY:**
> "I take the ratings service down — a real availability outage, not a code
> mutant. Called directly, ratings fails loudly: five-oh-three."

**Beat B — the lie (1:02 – 1:30)**

**DO:**
```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/products/0/reviews
curl -s http://localhost:8080/api/v1/products/0/reviews | python3 -m json.tool | head -14
```

**EXPECT (verified):** `200`; body shows
`"rating": {"error": "Ratings service is currently unavailable"}` inside an
otherwise valid payload.

**SAY:**
> "But reviews — which depends on ratings — still answers two hundred. The
> service caught the failure and degraded gracefully. A status oracle passes.
> A schema oracle passes — this body is perfectly valid. Every tester that
> judges only the HTTP response calls this green. The failure has been
> swallowed."

**Beat C — the trace knows (1:30 – 2:10)**

**DO:**
```bash
curl -s "http://localhost:16686/jaeger/api/traces?service=productpage.default&limit=20&lookback=10m" -o /tmp/live-traces.json
"$JAVA_HOME/bin/java" -cp mist-cli/target/mist.jar evaluation/suts/bookinfo/OracleCheck.java \
    /tmp/live-traces.json "GET /api/v1/products/0/reviews"
```
(Between the two commands there is a 6-second Jaeger ingest wait — CUT it.)

**EXPECT (verified — point at each line):**
```
  --> RESPONSE-LEVEL oracle (status/schema/soft-error sees the client response): PASS  (root is 2xx — looks successful, MISSES the failure)
  --> TRACE oracle HIDDEN_DOWNSTREAM_FAILURE: FIRES  severity=ERROR
      reviews.default ──▶ ratings.default.svc.cluster.local:9080/* (http=503 otel=ERROR)
```

**SAY:**
> "Now I pull the distributed traces of the exact requests we just made,
> straight from Jaeger's API, and replay MIST's shipped oracle on them.
> Response-level verdict: PASS — it only sees the 200. MIST's
> Hidden-Downstream-Failure invariant: FIRES, severity ERROR — inside the
> trace it found the reviews-to-ratings call that returned 503. The invariant
> is structural — label-free, no LLM, no training. In the full pipeline this
> verdict fails the generated test red; you'll see that report in a minute."

**DO (last frame of the scene):**
```bash
evaluation/suts/bookinfo/workload/inject-ratings-outage.sh off
```

**CUT:** ingest wait, any curl retry. Keep beat C ≤ 40 s.

---

### Scene 3 — Generation at scale, byte-reproducible (2:10 – 2:55)

**DO:**
```bash
"$JAVA_HOME/bin/java" -Drandom.seed=42 -jar mist-cli/target/mist.jar \
    mist-cli/src/main/resources/My-Example/trainticket-demo-noexec.properties
```
Show ~5 s of the live progress bar → **time-lapse** (real: ~2.5 min) → final
banner. Then:
```bash
find mist-cli/src/test/java/trainticket_twostage_test -name 'Flow_Scenario_*.java' \
    -exec sha256sum {} \; | sort > /tmp/run2.sums
diff /tmp/run1.sums /tmp/run2.sums && echo IDENTICAL
grep -A3 "FAULT COVERAGE SUMMARY" debug/negative_test/runs/run22-fault-detection-10of10.txt
```

**EXPECT (verified):** `diff` prints nothing → `IDENTICAL`;
the grep shows `Total Injected Faults: 10 / Detected Faults: 10 (100.0%)`.

**SAY:**
> "Generation is offline: the bundled TrainTicket demo — a 265-operation
> spec plus captured traces — generates cross-service JUnit scenarios with no
> SUT, no key, no network. Negative variants follow the Sniper strategy:
> exactly one fault per variant, so a red test has exactly one cause. Under a
> fixed seed the suite is byte-identical: I generated this once before
> recording, and the checksums of all one-hundred-twenty-three files match —
> that's what makes the artifact reviewable. Against the live forty-service
> TrainTicket, the same engine generated fifteen thousand and thirty-six
> tests, and the fault registry confirms all ten injected faults detected."

**CUT:** the time-lapse (keep the bar visibly moving before the jump).

---

### Scene 4 — Soft errors: the LLM-backed envelope check (2:55 – 3:30)

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

**EXPECT (verified; real 20–40 s, CUT the LLM wait to ~3 s):**
```
Soft-error response (HTTP 200): {"status":0,"msg":"start or end station not include in stationList.","data":null}
Status-class oracle: PASS (HTTP is 200)
  RESPONSE_ENVELOPE: FAIL  severity=ERROR  detail=status=0 classified as failure (LLM, cached)
```

**SAY:**
> "The second failure class: soft errors. TrainTicket answers HTTP 200 with
> status zero and data null — a rejection wearing a success code. The status
> oracle passes; MIST's Response-Envelope invariant classifies the unseen
> body value with one LLM call, caches the rule, and fails it. The same check
> flags Sock Shop's 200-with-status-code-500. And the hidden-downstream
> invariant crosses protocols: on Online Boutique the swallowed call is gRPC,
> and it fires on seven of twelve committed outage traces — and on zero of
> the healthy controls."

---

### Scene 5 — The report a developer sees (3:30 – 3:55)

**DO:** switch to the pre-rendered **Allure report** tab (from the prep
in-process run under the outage). Click the bookmarked red positive test;
zoom on its failure message.

**EXPECT (same text as the committed sample finding):**
`Positive variant failed — Trace Shape Oracle verdict has violation(s):
[HIDDEN_DOWNSTREAM_FAILURE: reviews.default ──▶ ratings.default... (http=503 otel=ERROR)]`

**SAY:**
> "This is what the developer actually gets. MIST's full pipeline — generate,
> compile, execute, oracle, in one JVM — ran one-hundred-sixty-six tests
> against the live Bookinfo and rendered a standard Allure report. Here is the
> positive reviews test: red, failed by the trace oracle, with the swallowed
> reviews-to-ratings 503 named in the message. Note the negative variants
> around it fail by design — Bookinfo accepts any input — every red test
> still names its single cause."

**CUT:** none needed; one tab switch, one click, one zoom.

---

### Scene 6 — Reproduce it yourself + close (3:55 – 4:40)

**DO:**
```bash
evaluation/run-offline-oracle.sh
```
Show the launch, **CUT the ~5-minute build**, land on the tail:
```
==> Done. Both fired offline -- no SUT, no LLM. (Healthy-control traces in the
    same directories stay silent; see REPRODUCE.md for the full matrix.)
```
Switch to the GitHub repo tab. End card (5 s): repo URL · Zenodo DOI ·
"REPRODUCE.md — start at §5".

**SAY:**
> "Everything you saw reproduces from a fresh clone. One script rebuilds the
> jar and replays both hidden-downstream detections from committed traces —
> no cluster, no LLM, about ten minutes end to end. REPRODUCE-dot-md maps
> every paper claim to a command and its committed evidence; the four systems
> ship as self-contained bundles under evaluation slash suts. MIST is open
> source under LGPL — the repository and the archived artifact are linked
> below. Thanks for watching."

---

## 5. Copy-paste appendix (run order)

```bash
# ---- PREP (not recorded; see §3 for the determinism + Allure prep) ----
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
pkill -f "port-forward" 2>/dev/null
( while true; do kubectl port-forward -n istio-system svc/istio-ingressgateway 8080:80; sleep 2; done ) &
( while true; do kubectl port-forward -n istio-system svc/tracing 16686:80; sleep 2; done ) &
kubectl scale deploy ratings-v1 --replicas=1
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/productpage     # 200

# ---- Scene 2 ----
evaluation/suts/bookinfo/workload/inject-ratings-outage.sh on
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/products/0/ratings   # 503
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/products/0/reviews   # 200
curl -s http://localhost:8080/api/v1/products/0/reviews | python3 -m json.tool | head -14
sleep 6
curl -s "http://localhost:16686/jaeger/api/traces?service=productpage.default&limit=20&lookback=10m" -o /tmp/live-traces.json
"$JAVA_HOME/bin/java" -cp mist-cli/target/mist.jar evaluation/suts/bookinfo/OracleCheck.java \
    /tmp/live-traces.json "GET /api/v1/products/0/reviews"
evaluation/suts/bookinfo/workload/inject-ratings-outage.sh off

# ---- Scene 3 ----
"$JAVA_HOME/bin/java" -Drandom.seed=42 -jar mist-cli/target/mist.jar \
    mist-cli/src/main/resources/My-Example/trainticket-demo-noexec.properties
find mist-cli/src/test/java/trainticket_twostage_test -name 'Flow_Scenario_*.java' \
    -exec sha256sum {} \; | sort > /tmp/run2.sums
diff /tmp/run1.sums /tmp/run2.sums && echo IDENTICAL
grep -A3 "FAULT COVERAGE SUMMARY" debug/negative_test/runs/run22-fault-detection-10of10.txt

# ---- Scene 4 ----
"$JAVA_HOME/bin/java" -cp mist-cli/target/mist.jar \
  -Dllm.openai_compatible.enabled=true \
  -Dllm.openai_compatible.url=https://api.deepseek.com/v1/chat/completions \
  -Dllm.openai_compatible.model=deepseek-chat \
  -Dllm.openai_compatible.api.key="$(cat .api_keys/DEEPSEEK_API_KEY)" \
  -Dmst.oracle.shape.invariants.span_tree.enabled=false \
  -Dmst.oracle.shape.invariants.status_propagation.enabled=false \
  evaluation/suts/trainticket/ResponseEnvelopeLiveCheck.java

# ---- Scene 5: browser only (pre-rendered /tmp/allure-report) ----

# ---- Scene 6 ----
evaluation/run-offline-oracle.sh

# ---- restore (not recorded) ----
evaluation/suts/bookinfo/workload/inject-ratings-outage.sh off
kubectl scale deploy ratings-v1 --replicas=1
```

---

## 6. Fallback plan

- **Cluster misbehaves on recording day** → Scene 2 Beat C runs on the
  **committed** outage trace instead of the live capture (identical output
  shape, verified):
  ```bash
  "$JAVA_HOME/bin/java" -cp mist-cli/target/mist.jar evaluation/suts/bookinfo/OracleCheck.java \
    docs/main-contribution/evidence/bookinfo_e2e_traces/masked_reviews_ratings_outage.json \
    "GET /api/v1/products/0/reviews"
  ```
  Change one narration line: "replayed on the outage trace committed in the
  artifact". Beats A/B can be dropped entirely in this mode (show the
  committed `bookinfo_e2e_pipeline.md` table for 5 s instead).
- **Allure prep run not done / report looks off** → drop Scene 5, give its
  25 s to Scene 2; mention the committed run reports verbally in Scene 6
  ("the committed in-process run reports show the same finding across 166
  tests").
- **DeepSeek down** → Scene 4 shows the committed transcript
  `docs/main-contribution/evidence/responseenvelope_live_softerror.txt`
  (`cat` it) with the narration line "here is the committed transcript of
  that one-call classification".
- Scenes are independent takes — record separately, stitch in the editor.

---

## 7. Post-production

- [ ] Trim per the CUT notes; total **3:00 ≤ t ≤ 5:00** (target 4:35–4:45).
- [ ] Lower-third caption with the command name during each terminal beat.
- [ ] Clean up auto-captions (accessibility; SIGSOFT venues encourage it).
- [ ] Export MP4 (H.264, 1080p, 30 fps).
- [ ] Upload **YouTube (unlisted)**; add the MP4 to the **Zenodo deposit**
      and publish the deposit (the paper's DOI must resolve).
- [ ] Replace `\todo{SCREENCAST-URL}` in `paper/main_issta.tex` (abstract +
      Tool Availability paragraph) with the YouTube URL; update REPRODUCE §9.
- [ ] Watch end-to-end once on laptop speakers at 1× before submitting.

---

## 8. Feasibility ledger (why every step above is known to work)

| Scene step | Verified | Evidence |
|---|---|---|
| Outage toggle + 503 settle | 2026-06-09 (matrix runs) | `debug/reproduce/evidence/bookinfo-e2e-matrix.log` |
| Masked 200 + degraded body via ingress | 2026-06-10 live | session transcript (audit V10/V11 follow-up) |
| Live Jaeger-API fetch → `OracleCheck` FIRES @ERROR on fresh traces | **2026-06-10 live, end-to-end** | session transcript (20 traces fetched, FIRES on the just-made requests) |
| OracleCheck on committed traces (fallback) | 2026-06-09, fresh clone | `debug/reproduce/evidence/offline-oracle.log` |
| noexec generation 123 files, ~2.5 min, offline | 2026-06-09, no-network namespace | `debug/reproduce/evidence/noexec-run1-tail.log` |
| Seed-42 byte-identical double run | 2026-06-09 | `debug/reproduce/evidence/run{1,2}.sums` (identical) |
| run22 10/10 + 15,036 numbers | 2026-06-10 re-grepped | `debug/negative_test/runs/run22-fault-detection-10of10.txt` |
| ResponseEnvelopeLiveCheck exact output | 2026-06-09, fresh clone + live DeepSeek | matches `docs/main-contribution/evidence/responseenvelope_live_softerror.txt` |
| Boutique 7/12 (and recapture 24/40, 0/30) | 2026-06-10 re-run | session transcript |
| In-process run produces Allure results (166 tests) | 2026-06-09 live | `.runtime/target/allure-results`, 2,710 files |
| `allure generate` renders that run | **2026-06-10** | report at `/tmp/allure-report-test`, summary 166 total |
| Red positive test with HIDDEN_DOWNSTREAM message in the report | committed 2026-06-02 in-process outage run | `docs/main-contribution/evidence/bookinfo_inprocess_e2e/sample_hidden_downstream_finding.txt` (prep re-run reproduces it) |
| `run-offline-oracle.sh` end-to-end ~5–6 min | 2026-06-09, fresh clone | `debug/reproduce/evidence/offline-oracle.log` |
