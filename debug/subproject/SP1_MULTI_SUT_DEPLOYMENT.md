# SP1 — Multi-SUT Deployment + Trace Corpus Kit

> Scope: 3-credit CSI 5v90 (summer), extendable into a master's project.
> Project repo: `miaoti/Rest`, working branch `inject-detection`.
> Mentor liaison: Tingshuo Miao.

---

## Why this matters

The MIST paper currently bundles **one** system under test (TrainTicket) with
27 captured Jaeger traces and 10 injected faults. The paper's own §6
Limitations names the gap verbatim:

> "The bundled case study covers one SUT (TrainTicket); a broader head-to-head
> against AutoRestTest, LogiAgent, and MACROHIVE across Sock Shop, Online
> Boutique, and DeathStarBench is in-progress companion work and reserved
> for the full research paper."

Without ≥3 SUTs, the full-track A-conference submission cannot establish
external validity. This sub-project closes that gap by producing a reusable
**multi-SUT trace-corpus kit** that any future evaluation (MIST or
otherwise) can ingest.

This work is **orthogonal** to the MIST core — no edits to `mist-core`,
`mist-llm`, or `mist-cli` source. Everything you produce lives under
`evaluation/suts/` and `evaluation/corpora/`.

---

## Objective

> Ship a reproducible deployment + trace-capture kit for **at least three**
> microservice systems, such that MIST can be pointed at each one with a
> single `.properties` file and run end-to-end.

---

## SUT Requirements

For each chosen SUT, the deliverable must satisfy:

1. **Reproducible deployment** — anyone with Docker (or k3s/kind) can stand
   up the SUT from a single command using your scripts.
2. **OTel/Jaeger pipeline working end-to-end** — when traffic hits the SUT,
   the corresponding traces appear in a Jaeger backend reachable from the
   MIST test JVM.
3. **OpenAPI spec available** — either a single merged spec or a per-service
   collection that MIST's spec ingestor can consume.
4. **Workload generator** — a script that produces nominal, realistic
   traffic so the captured trace corpus is representative (not a single
   curl loop).
5. **Captured trace corpus** — at least 100 distinct Jaeger traces per SUT,
   stored in the format MIST expects under `evaluation/corpora/<sut>/`.
6. **MIST `.properties` profile** — sibling to the existing
   `mist-cli/src/main/resources/My-Example/trainticket-demo.properties`,
   one per SUT, that runs MIST against the deployed instance.
7. **One successful end-to-end MIST run per SUT** — `java -jar mist.jar
   <sut>.properties` produces test files and an Allure report without
   crashing. Detection rate is **not** a success criterion for this
   sub-project; "MIST runs without error" is.

---

## SUT Candidates (pick ≥3 for summer)

| SUT | Services | Languages | Why pick | Why skip |
|-----|----------|-----------|----------|----------|
| **Sock Shop** | 12 | Go, Java, Node | Mature, well-documented, OTel-friendly | Some images are stale; may need rebuilds |
| **Online Boutique** | 10 | Go, Python, Java, Node, .NET, C++ | Google reference, polyglot diversity | Heavier resource footprint |
| **TeaStore** | 5 | Java | Designed for research; well-instrumented | Smaller scale; less "real-world" feel |
| **Pitstop** | 6 | .NET | Adds .NET ecosystem coverage | Less commonly used in REST testing papers |
| **Bookinfo** | 4 | Polyglot | Trivially easy to stand up | Too small; reviewers will dismiss |
| **DeathStarBench (Social Network)** | 28 | C++, Python | Heaviest, most "production-like" | **Out of scope for summer**; reserve for thesis |

**Recommended summer trio**: Sock Shop + Online Boutique + TeaStore.
This gives polyglot breadth (6+ languages combined), a research-grade
benchmark, and a workshop-grade demo — enough domain diversity that
reviewers can't dismiss the eval as "one ecosystem."

**Stretch (counts as fourth SUT)**: Add Pitstop or a smaller DeathStarBench
sub-app if you finish the trio in the first half of the semester.

---

## Deliverables

Each item below must land in the repo on a branch off `inject-detection`.
File-tree shape only — no prescription on how you get there.

```
evaluation/
  suts/
    sock-shop/
      deploy/                   # compose / k8s manifests / helm chart
      README.md                 # one-command deploy + teardown
      workload/                 # locust / k6 / artillery script
      openapi/                  # spec(s) used as MIST input
    online-boutique/
      ... (same shape)
    teastore/
      ... (same shape)
  corpora/
    sock-shop/
      traces/                   # >= 100 captured Jaeger JSON
      MANIFEST.json             # source, capture date, capture script
    online-boutique/
      ...
    teastore/
      ...
mist-cli/src/main/resources/My-Example/
  sock-shop-demo.properties     # MIST profile per SUT
  online-boutique-demo.properties
  teastore-demo.properties
```

Plus:

1. **Top-level `evaluation/suts/README.md`** that names every SUT, its
   resource footprint (CPU/memory), and the one command to bring it up.
2. **Smoke-test script** (`evaluation/suts/smoke.sh` or similar) that, for
   each SUT in turn, verifies: deploy → workload runs → trace visible in
   Jaeger → MIST runs the .properties file to completion.
3. **8-page technical report** (`evaluation/suts/REPORT.md`) covering:
   per-SUT deployment notes, OTel injection approach, workload-generator
   design, trace-capture methodology, any deviations from the SUT's
   upstream defaults, and which SUTs were rejected and why. This doubles
   as draft material for the paper's §5 (evaluation setup).

---

## Acceptance Criteria

A reviewer (mentor + one other student) should be able to, from a fresh
clone of `inject-detection`:

- [ ] Pick any one of the ≥3 SUTs from `evaluation/suts/README.md`
- [ ] Run the documented deploy command on a stock Linux box with Docker
- [ ] See Jaeger UI populated with traces within 5 minutes of starting workload
- [ ] Run `java -jar mist-cli/target/mist.jar <sut>.properties` and watch
      MIST consume the bundled corpus + run to completion
- [ ] Open the Allure report and see at least one generated scenario per
      root API

If any of the above fails for any SUT, the deliverable is incomplete.

---

## In Scope vs. Out of Scope

**In scope.**
- SUT deployment (compose / k8s / kind, your choice — document it).
- Auto-instrumentation **if and only if** the SUT does not already export
  OTel. Sock Shop and Online Boutique have community OTel branches; prefer
  those over hand-rolling.
- Workload that touches at least 60% of the SUT's externally-reachable
  endpoints.
- Bundling and de-duplicating Jaeger traces into a clean corpus.

**Out of scope.**
- Running other testing tools (RESTest, EvoMaster, AutoRestTest, …) on
  these SUTs — that is **SP2**'s job. Hand SP2 a working SUT and a corpus;
  don't try to do their work too.
- Manual bug discovery on these SUTs — that is **SP3**'s job.
- Modifying MIST's `mist-core` / `mist-llm` / `mist-cli` source. If MIST
  crashes on your SUT, file a ticket and use the existing bundled
  TrainTicket-demo to sanity-check that your environment is fine; then
  hand the crash to the mentor.
- Adding new oracle invariants — different sub-project.

---

## Thesis Extension Path (if continuing past summer)

If the student continues into a master's thesis (Fall 2026 onwards), the
natural extensions are:

1. **DeathStarBench Social Network** — full 28-service deployment with
   sustained workload; produce a 1000+ trace corpus. This alone is a
   semester of work.
2. **Cross-domain SUT diversification** — add a non-e-commerce SUT
   (e.g., a healthcare or banking microservice reference app) for
   domain-validity claims in the paper.
3. **Publishable benchmark paper** — repackage the kit as a standalone
   "MicroservTraces" benchmark and submit to ESEM / MSR / EMSE as a
   replication-package / artifact paper. The bundled deployment +
   trace-capture + workload-generator is itself a contribution.
4. **Workload realism study** — compare your synthetic workload's
   trace-shape distribution against an academic or production trace
   archive (e.g., the publicly-released Alibaba microservice trace
   dataset) and report distributional gaps.

---

## Pointers (people, prior art, infra)

- **Branch**: work off `inject-detection`, push to a feature branch
  `student/<name>/sp1-suts`, open PR when each SUT lands.
- **Bundle reference**: `mist-cli/src/main/resources/My-Example/trainticket/`
  is the canonical shape for what a finished per-SUT bundle looks like
  (spec, traces, properties, flow.md).
- **OTel injection reference**: see the TrainTicket deployment scripts the
  team already maintains for the existing demo; same pattern should apply
  to Sock Shop and Online Boutique.
- **Worktree workflow**: see `CLAUDE.md` for the team's
  worktree-per-feature convention if working in parallel with other
  students.
- **No-attribution policy**: commits and PR descriptions must not list
  AI tools (Claude, ChatGPT, Copilot, …) as authors, co-authors, or
  contributors.
