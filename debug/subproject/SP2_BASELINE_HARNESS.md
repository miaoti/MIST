# SP2 — Baseline Reproducibility Harness

> Scope: 3-credit CSI 5v90 (summer), extendable into a master's project.
> Project repo: `miaoti/Rest`, working branch `inject-detection`.
> Mentor liaison: Tingshuo Miao.

---

## Why this matters

The MIST paper's Table 1 compares MIST against **two** baselines (RESTest
and EvoMaster) at a hand-tuned 25-hour budget. For a full-track A-conference
submission this is two problems at once:

1. **Baseline thinness** — the literature has moved past RESTest/EvoMaster.
   Recent REST testers MIST needs to beat are AutoRestTest (2025),
   LogiAgent (2025), MoREST (2022), ARAT-RL (2023), Schemathesis, and
   RESTler. The paper's Related Work already cites them; the eval doesn't
   run them.
2. **Reproducibility** — even the two baselines we do report are run by
   hand on local boxes with undocumented flags. Reviewer first comment
   will be "results not reproducible."

This sub-project produces a **uniform, containerised baseline harness**:
every baseline lives in its own Docker image, exposes the same input
contract (`OpenAPI URL, SUT URL, time budget, output dir`), and emits the
same output contract (`detection_report.json` with a documented schema).
After the harness exists, swapping in a new SUT (from SP1) or rerunning
the head-to-head is a single command.

This work is **orthogonal** to MIST core. You don't modify `mist-core` /
`mist-llm` / `mist-cli` — you containerise other people's tools and write
a driver script.

---

## Objective

> Containerise at least three competing REST testers behind one uniform
> driver, such that
> `./run-baseline.sh <tool> <openapi> <sut-url> <budget> <out-dir>`
> works the same way for every tool and produces a comparable detection
> report.

---

## Required Deliverables

```
evaluation/baselines/
  README.md                       # baseline list, status, known caveats
  schema/
    detection_report.schema.json  # canonical output schema
    run_config.schema.json        # canonical input schema
  driver/
    run-baseline.sh               # uniform entry point
    aggregate.py                  # combine N reports -> comparison table
  autoresttest/
    Dockerfile
    README.md                     # config knobs, known issues, version pin
    config.yml                    # the harness-exposed knobs
    smoke-test.sh                 # one-command verification
  logiagent/
    ... (same shape)
  schemathesis/
    ... (same shape)
  restest/                        # optional but recommended (paper baseline)
    ...
  evomaster/                      # optional but recommended (paper baseline)
    ...
results/
  trainticket/
    autoresttest_2026-06-15.json
    logiagent_2026-06-15.json
    schemathesis_2026-06-15.json
    mist_2026-06-15.json
    SUMMARY.md                    # the table the paper will use
```

Plus:

1. **Top-level `evaluation/baselines/README.md`** that lists every baseline,
   the upstream URL / commit / version pinned in the Dockerfile, what
   inputs it accepts, what outputs it produces natively, and how the
   harness translates between them.
2. **Per-baseline `smoke-test.sh`** that runs the containerised baseline
   against the bundled TrainTicket demo and confirms the
   `detection_report.json` matches the schema.
3. **`evaluation/baselines/COMPARISON.md`** — a written-up
   apples-to-apples reproduction of the MIST paper Table 1 using the
   harness. Same SUT, same budget, all baselines from inside the harness.
   This is what replaces the current hand-run Table 1.
4. **8-page technical report** (`evaluation/baselines/REPORT.md`)
   covering: per-baseline reproduction notes (what we changed from
   upstream defaults and why), failure modes encountered (out-of-memory,
   missing deps, etc.), config knobs that materially affect output,
   any baselines you tried and failed to reproduce (and what blocked
   you). This doubles as draft material for paper §5 (evaluation
   setup) and §7 (threats to validity).

---

## Acceptance Criteria

From a fresh clone of `inject-detection`, a reviewer should be able to:

- [ ] `docker build` every baseline image without manual intervention
- [ ] `./run-baseline.sh autoresttest <openapi> <sut-url> 1h /tmp/out`
      and get a schema-conformant `detection_report.json` back
- [ ] Repeat the above for every baseline in the harness — same command
      shape, same output shape
- [ ] Run `aggregate.py` over a directory of N reports and get a single
      Markdown table mirroring the paper's Table 1 format
- [ ] Re-run the entire head-to-head from `make all` (or equivalent
      one-liner) — including pulling the bundled TrainTicket spec and
      pointing at a deployed SUT URL passed via env var

---

## Baseline Candidates (pick ≥3 for summer)

| Tool | Year | Why include | Why hard |
|------|------|-------------|----------|
| **AutoRestTest** | 2025 | Paper Related Work; current SOTA LLM-driven | Python env is finicky; needs OpenAI / local LLM creds |
| **LogiAgent** | 2025 | Paper Related Work; "reads response bodies" angle | LLM-dependent; rate-limit budget |
| **Schemathesis** | active | Battle-tested, easy to run | Property-based, may need tuning to match other budgets |
| **RESTler** | 2019 | Microsoft tool, often cited | Has its own grammar format; setup is heavy |
| **MoREST** | 2022 | Sequence-aware predecessor | Less actively maintained |
| **ARAT-RL** | 2023 | RL-based baseline | RL training cost; reproducibility known-hard |
| **EvoMaster** | active | Paper baseline; black-box mode | Already partially understood by team |
| **RESTest** | active | Paper baseline | Already partially understood by team |
| **MACROHIVE** | 2022/24 | Grey-box, paper cites | Requires mesh-proxy injection per SUT — heavy. **Out of scope for summer.** |

**Recommended summer trio**: AutoRestTest + LogiAgent + Schemathesis.
This trio gives one "LLM SOTA" (AutoRestTest), one "LLM-body-aware"
(LogiAgent), and one "non-LLM property-based" (Schemathesis), so the
paper's comparison covers the three live competitor categories.

**Strong stretch (counts as fourth/fifth)**: also containerise RESTest
and EvoMaster so the paper's existing Table 1 results are reproducible
inside the same harness, not on a team laptop.

---

## Canonical Output Schema (`detection_report.schema.json`)

This is the **contract** every baseline driver must produce. Pin it early
and don't drift.

```jsonc
{
  "tool": "autoresttest",
  "tool_version": "v0.3.2-commit-abc1234",
  "sut": "trainticket",
  "sut_url": "http://203.0.113.10:8080",
  "openapi_source": "trainticket/openapi.yaml",
  "started_at": "2026-06-15T14:00:00Z",
  "finished_at": "2026-06-15T15:00:00Z",
  "budget_seconds": 3600,
  "requests_sent": 124501,
  "unique_paths_hit": 87,
  "faults_detected": [
    {
      "fault_id": "Invalid_Station_Name",
      "first_detected_at_seconds": 432,
      "evidence": "trace_id=abc... or http response snippet"
    }
  ],
  "raw_output_path": "results/trainticket/autoresttest_2026-06-15/raw/"
}
```

Faults are matched against the bundled TrainTicket 10-fault registry by
name (or by SP3's bug-id once that ships). "evidence" is for the human
sanity-checker; the boolean is the comparison-table number.

---

## In Scope vs. Out of Scope

**In scope.**
- Containerising each baseline so its build is hermetic and its run is
  reproducible.
- Writing the per-tool driver that maps the uniform input contract to
  the tool's native invocation, and the tool's native output to the
  uniform `detection_report.json`.
- Documenting every meaningful config knob (LLM backend, budget,
  parallelism) the harness exposes — and every knob the harness pins
  to a default so reviewers know what's frozen.
- Reproducing the existing paper Table 1 inside the harness.
- Running the harness against SP1's SUTs once they're available
  (coordinate with SP1 student via the mentor).

**Out of scope.**
- Modifying any baseline's source. If a baseline is broken upstream,
  file the issue with them, pin the last-working commit in your
  Dockerfile, and document the workaround in the per-baseline README.
- Building a new REST tester. This sub-project is purely
  harness + reproduction work.
- Modifying MIST itself, including how MIST's own results land in the
  harness output schema — wrap MIST in the same driver pattern as the
  others; don't change MIST's output format.
- MACROHIVE — mesh-proxy injection per SUT is a thesis-scale project
  on its own.

---

## Thesis Extension Path

1. **Complete the baseline matrix** — add RESTler, MoREST, ARAT-RL,
   bringing the harness to ≥7 baselines. Each addition is 1-2 weeks.
2. **Continuous head-to-head dashboard** — wire the harness to run
   weekly against the SP1 SUTs, publish results as a tracked website
   (GitHub Pages). Future paper revisions get fresh numbers for free.
3. **MACROHIVE reproduction** — grey-box vs. trace-shape comparison;
   one full semester just on this baseline.
4. **Replication-study paper** — repackage the harness + reproduced
   numbers as a standalone "Reproducing N REST testers across M SUTs"
   replication study; submit to MSR, ICSE Registered Reports track, or
   EMSE. The harness alone is a publishable artifact.

---

## Pointers

- **Branch**: feature branch `student/<name>/sp2-harness` off
  `inject-detection`, PR per baseline.
- **MIST-side integration**: MIST's `MistRunner` writes summary numbers
  somewhere in `target/`; wrap it with a thin shell script that emits the
  same `detection_report.json`. Talk to the mentor before changing where
  MIST writes its native output.
- **Hard dependency on SP1**: the second half of the semester needs SP1's
  SUTs to be deployable. If SP1 is delayed, run the harness against
  TrainTicket only and reproduce the paper's Table 1 — that is itself a
  paper-grade result.
- **No-attribution policy**: commits and PR descriptions must not list
  AI tools (Claude, ChatGPT, Copilot, …) as authors, co-authors, or
  contributors.
