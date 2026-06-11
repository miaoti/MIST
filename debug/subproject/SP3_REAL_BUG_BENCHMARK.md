# SP3 — Real-Bug Replay Benchmark

> Scope: 3-credit CSI 5v90 (summer), extendable into a master's project.
> Project repo: `miaoti/Rest`, working branch `inject-detection`.
> Mentor liaison: Tingshuo Miao.

---

## Why this matters

The MIST paper's TrainTicket evaluation uses **10 synthetic injected
faults** (`Invalid_Station_Name_Length`, `Invalid_Route_Id`, …). These are
hand-crafted to exercise the fault categories MIST claims to cover.

An A-conference reviewer will read this and immediately ask: *"Are these
faults realistic? You designed the faults and the tool that detects them
— of course detection is 10/10."* This is the classic fault-realism
attack, and the right answer is a **real-bug benchmark**: bugs mined from
the SUT's own history, where the fix-commit is the ground truth and
nobody designed them with MIST in mind.

This sub-project produces a **curated real-bug catalog** + **replay
harness** for the SP1 SUTs, modelled on Defects4J's bug-card format. Once
it exists, MIST and every baseline from SP2 can be evaluated against
"bugs the developers actually fixed," which is the external-validity
argument the paper currently lacks.

This work is **orthogonal** to MIST core. You don't modify MIST; you
mine git history and write a replay harness.

---

## Objective

> Ship a curated catalog of **20-30 historically-real microservice REST
> bugs** drawn from the SUTs SP1 deploys, plus a one-command replay
> harness that checks out a buggy version, deploys it, runs MIST or any
> SP2 baseline against it, and records detection or miss.

---

## Required Deliverables

```
evaluation/bugs/
  README.md                       # catalog overview, inclusion criteria, status
  catalog.yaml                    # the canonical bug list
  cards/
    trainticket-001/
      card.yaml                   # metadata (see schema below)
      reproduction.md             # how the buggy behaviour manifests
      pre-fix-commit              # symlink or pointer to upstream SHA
      fix-commit                  # ditto
      expected-detection.json     # what a tool must observe to "detect" this bug
    trainticket-002/
      ...
    sockshop-001/
      ...
    onlineboutique-001/
      ...
  replay/
    replay.sh                     # one-command replay
    deploy/                       # per-SUT deployment overrides for buggy versions
    smoke-test.sh
  results/
    mist_2026-06-15.json
    autoresttest_2026-06-15.json  # filled in by SP2 once integrated
    ...
```

Plus:

1. **`evaluation/bugs/README.md`** — catalog overview, **explicit
   inclusion criteria** (see below), counts per SUT, counts per bug
   category, and pointers to the upstream issues / PRs the catalog
   draws from.
2. **`evaluation/bugs/SUMMARY.md`** — current detection picture: which
   tools catch which bugs, derived from `results/*.json`.
3. **8-page technical report** (`evaluation/bugs/REPORT.md`) covering:
   mining methodology (search queries, time window, labels filtered on),
   inclusion / exclusion decisions and counts at each filter stage,
   inter-rater agreement on bug realism (you + one other student / RA
   sign off independently on each card), and the synthetic-vs-real
   gap analysis (which synthetic-fault categories these real bugs map
   to, and which real-bug categories have no synthetic counterpart).
   This is paper §5.X material on its own.

---

## Bug Inclusion Criteria

A bug enters the catalog only if it satisfies **all** of:

1. **Reproducible** — a commit SHA exists where the bug manifests, and
   the buggy version can be built and deployed using SP1's infrastructure
   with at most a documented diff.
2. **Externally observable** — the bug must be triggerable through the
   public REST API. Bugs that only manifest under specific intra-cluster
   conditions (e.g., a corrupt etcd state) are excluded.
3. **Functional, not infrastructural** — wrong logic, wrong response
   shape, missing validation, mis-handled edge case. Excluded: dependency
   CVEs, Docker image build breakage, README typos, performance
   regressions, flaky tests.
4. **Has a developer-authored fix** — a commit or PR that the upstream
   maintainers accepted. The fix is the ground-truth oracle: post-fix
   behaviour is correct, pre-fix is buggy.
5. **Detectable in principle from the REST surface or trace** — the
   bug's observable signature (wrong status code, wrong response body,
   missing downstream span, wrong span order, etc.) is present in what
   a black-box tester would see.

A bug is **rejected** if any of those fail. The REPORT.md must include
**how many bugs were considered and how many were rejected at each
filter**, so reviewers can audit selection bias.

---

## Bug-Card Schema (`card.yaml`)

```yaml
id: trainticket-007
sut: trainticket
upstream_repo: FudanSELab/train-ticket
issue_url: https://github.com/FudanSELab/train-ticket/issues/NNN
fix_pr_url: https://github.com/FudanSELab/train-ticket/pull/MMM
pre_fix_commit: abc1234
fix_commit: def5678
discovered_at: 2024-03-14            # when the upstream bug was filed
category: soft-error                 # one of: soft-error, status-mismatch,
                                     #   span-missing, span-order, validation-gap,
                                     #   propagation, other
affected_services:
  - ts-admin-route-service
trigger:
  method: POST
  path: /api/v1/adminrouteservice/adminroute
  notes: "end station absent from stationList -> 200 OK with status:0"
expected_detection:
  signal: response_envelope_violation
  rationale: "Response body has status:0 / data:null despite HTTP 200"
inter_rater:
  reviewer_1: tmiao         (initials only, no emails)
  reviewer_2: <student>
  agreed: true
notes: |
  Free-form. What was hard about reproducing this. What MIST does on it
  today. Anything a future reader needs to know.
```

The catalog (`catalog.yaml`) is just a list of these cards, suitable for
machine consumption by `replay.sh` and SP2's `aggregate.py`.

---

## Acceptance Criteria

From a fresh clone of `inject-detection`:

- [ ] `evaluation/bugs/catalog.yaml` lists ≥20 cards across ≥2 SUTs
- [ ] Each card has a complete `card.yaml`, a `reproduction.md` that walks
      through the buggy behaviour, and inter-rater sign-off
- [ ] `./replay.sh trainticket-007 mist` deploys the buggy version, runs
      MIST against it, and writes a `detected|missed` result
- [ ] The same command shape works for any card and any tool from SP2
- [ ] `evaluation/bugs/SUMMARY.md` shows the current MIST vs. baselines
      detection table on the real-bug catalog
- [ ] REPORT.md transparently documents inclusion/exclusion counts and
      the mining methodology

---

## Bug Sources (where to mine)

| SUT | Where to look | Realistic yield |
|-----|---------------|-----------------|
| **TrainTicket** | `FudanSELab/train-ticket` issues + PRs; published bug studies (TrainTicket has academic bug-catalog papers — read them and cross-reference) | High (15-25 candidates) |
| **Sock Shop** | `microservices-demo/microservices-demo` issues + PRs; community forks with bug reports | Medium (5-10 candidates) |
| **Online Boutique** | `GoogleCloudPlatform/microservices-demo` issues + PRs | Medium (5-10 candidates) |
| **TeaStore** | `DescartesResearch/TeaStore` issues; SPEC papers around it | Low (3-5 candidates) |
| **DeathStarBench** | `delimitrou/DeathStarBench` issues; performance-study papers (most issues are perf, filter aggressively) | Low for *functional* bugs |

**Recommended target mix**: 15 TrainTicket + 5 Sock Shop + 5 Online
Boutique = 25 cards. This biases toward TrainTicket because (a) it has
the richest bug history in the literature and (b) the team already
operates a TrainTicket cluster.

---

## In Scope vs. Out of Scope

**In scope.**
- Mining git history, issue tracker, and academic bug-catalog papers for
  candidates.
- Building the per-card reproduction (checkout + deploy + trigger).
- Inter-rater realism review with one other person.
- Writing the replay harness.
- Running MIST against the catalog and recording results.
- Once SP2 is integrated: running every SP2 baseline against the
  catalog and writing up the comparison.

**Out of scope.**
- Inventing new bugs — that's mutation testing, not real-bug mining.
- Fixing the bugs upstream — leave that to the SUT maintainers.
- Modifying MIST's source. If MIST misses a bug that the
  card asserts should be detectable, that's a finding to report, not a
  ticket to fix during this sub-project.
- Building deployment automation for the buggy versions from scratch —
  that's SP1's territory. Take SP1's deploy scripts and patch them with
  the bug commit; if SP1 hasn't shipped yet, work with TrainTicket only
  (the team already operates a cluster).
- Performance bugs, flaky tests, and infrastructure-only bugs.

---

## Thesis Extension Path

1. **Scale the catalog to 100+ bugs** across all SP1 SUTs + DeathStarBench.
   At this size the catalog itself becomes citable as a benchmark
   ("MicroservBugs-100").
2. **Synthetic-vs-real empirical study** — for every fault category in
   MIST's 8-category default registry, count how many real-bug instances
   map to that category, and how many real bugs fall outside the
   registry. This is the empirical answer to "are MIST's synthetic faults
   representative?" — directly publishable as ESEM / EMSE.
3. **Automated bug mining** — replace the manual mining loop with an LLM
   + commit-classifier pipeline; report precision/recall against the
   manually-curated catalog.
4. **Time-travel evaluation** — for each bug, also run the *fix* commit
   and confirm no tool detects a "bug" (false-positive check).

---

## Pointers

- **Branch**: feature branch `student/<name>/sp3-bugs` off
  `inject-detection`, PR per ~5 cards (don't wait until the end to open a
  PR; mentor needs to review card quality early).
- **Reference catalogs**:
  - Defects4J card format — copy structure, not contents.
  - Existing TrainTicket bug-study papers (the mentor will share the
    bibliography). Several of these enumerate real bugs already; treat
    them as a starting candidate list, then verify each independently.
- **Hard dependency on SP1**: needs SP1's deploy infrastructure to
  rebuild buggy versions of Sock Shop and Online Boutique. Until SP1
  lands those, work TrainTicket-first (the team already has the
  cluster). If SP1 slips badly, 20 TrainTicket-only cards is still a
  paper-grade contribution.
- **Soft dependency on SP2**: ideal is to have the harness drive every
  baseline. If SP2 isn't ready, drive MIST alone and leave the
  baseline-comparison table as a placeholder for SP2 to fill in.
- **No-attribution policy**: commits and PR descriptions must not list
  AI tools (Claude, ChatGPT, Copilot, …) as authors, co-authors, or
  contributors. The reviewer-1 / reviewer-2 fields in `card.yaml` are
  for humans only.
