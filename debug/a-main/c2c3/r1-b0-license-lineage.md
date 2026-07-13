# R1-B0 license + lineage closure (Wave R1, Phase B0-2/B0-3)

**Date:** 2026-07-13 · Owner: main_track · Status: **EXECUTED (document+web only; NO existing file
edited — the dead-citation correction is PLANNED here and APPLIED in Phase C)**
**Authority:** `wave-r1-corpus-completion-plan.md` §4-B0-2/-3 (C-F1, C-F4) · `c2-license-audit.md`
(2026-07-08 dispositions, which this record extends without modifying).

---

## §1 GitHub lineage/license findings (license API, verified 2026-07-13)

| repo | exists | fork metadata | license | key facts | source URL |
|---|---|---|---|---|---|
| FudanSELab/train-ticket | yes | (base) | **Apache-2.0** (`LICENSE` at root) | the clean TrainTicket base; README carries the TSE-paper reference | https://api.github.com/repos/FudanSELab/train-ticket/license |
| FudanSELab/train-ticket-fault-replicate | yes | fork:true of z-xiaolong/fault_replicate | **null (UNLICENSED)** | created 2018-01-02, last push 2018-12-27; description: a benchmark with 22 replicated faults from an industry survey | https://api.github.com/repos/FudanSELab/train-ticket-fault-replicate |
| z-xiaolong/fault_replicate | yes | original (not a fork) | **null (UNLICENSED)** | created 2018-08-13; no description | https://api.github.com/repos/z-xiaolong/fault_replicate |
| AsifShaafi/train-ticket-injection | yes | **fork:false** — a detached re-upload, NOT GitHub-fork-linked to FudanSELab | **Apache-2.0** (`LICENSE` at master) | created 2025-05-01, pushed 2026-06-10; description matches upstream ("Train Ticket - A Benchmark Microservice System"); README credits FudanSELab/train-ticket and cites the TSE paper | https://api.github.com/repos/AsifShaafi/train-ticket-injection and /license |

Metadata anomaly (recorded, immaterial): the FudanSELab fork's `created_at` (2018-01-02) predates
its listed parent's `created_at` (2018-08-13) — consistent with a rename/transfer in the parent's
history. Both ends of the fault-replicate chain are license-null either way, so the
`c2-license-audit.md` disposition (CODE all-rights-reserved → replicate-by-description only; FACTS
citable) stands unchanged.

**AsifShaafi/train-ticket-injection specifics (the fork-lineage question answered):**
- It is the TT SUT's actual public source: `evaluation/suts/trainticket/deploy/deploy.sh` clones
  `https://github.com/AsifShaafi/train-ticket-injection.git` branch `injection` (head `54e1a2e5`,
  branches API 2026-07-13); `MANIFEST.json` and the SUT README cite the same.
- Lineage to the Apache-2.0 base is established by CONTENT + ATTRIBUTION, not by GitHub fork
  metadata: the repo retains the Apache-2.0 `LICENSE` at root, and its README retains the credit to
  `FudanSELab/train-ticket` and the TSE-paper citation → notice-preserving redistribution of the
  Apache-2.0 base is satisfied for the content we use.
- **Sharp caveat:** its branch inventory (names observed via the branches API; NO branch content
  read) includes re-hosted fault-implementation branches `ts-error-F1`…`ts-error-F14-*` and
  `istio-error-*` — i.e., it re-hosts the UNLICENSED fault-replicate branch code under a repo-level
  Apache-2.0 LICENSE stamp. That stamp cannot retroactively license z-xiaolong's all-rights-reserved
  fault code. Consequence: our lineage citations scope the Apache-2.0 claim to the BASE content
  (branch `injection` / master tree), and the clean-room refusal (spec §0) extends to those
  `ts-error-*` branches wherever hosted.
- **Branch `MIST-trainticket`: does NOT exist there** (branch GET → 404). **Commit `a1767ab3`: not
  found in the public repo** (commit lookup → 422 not-found). See §4.

## §2 codewisdom Docker Hub namespace resolution

- **Docker Hub v2 API** (https://hub.docker.com/v2/users/codewisdom/ , 2026-07-13): account type
  **User** (not Org), id `a25eee84a054…`, joined 2019-12-11, ALL profile fields empty (no full name,
  company, location, or link). Repository listing
  (https://hub.docker.com/v2/repositories/codewisdom/?page_size=100): **94 repositories, 58 of them
  `ts-*` TrainTicket service images**, plus infra images (mysqlclient, rabbitmq) and
  Jaeger-instrumented variants; pull counts up to ~298k (`ts-ui-dashboard`).
- **The verifying tie:** `FudanSELab/train-ticket`'s own `Makefile`
  (https://raw.githubusercontent.com/FudanSELab/train-ticket/master/Makefile) defaults the image
  repository to `Repo=codewisdom` (with `Tag=latest`) for its `build-image` / `push-image` /
  `publish-image` / `deploy` targets — the project's OWN build/publish tooling targets this
  namespace. That makes `codewisdom` the project's de-facto official image namespace by the
  project's own configuration.
- **Lab identity:** "CodeWisdom" is the Fudan SE lab's platform/team name — the lab's GitHub org
  page and site (https://fudanselab.github.io/ , http://codewisdom.net/), and third-party
  literature describing TrainTicket as maintained by the CodeWisdom team of Fudan University
  (arXiv:2306.05895 case-study prose).
- **What CANNOT be verified:** administrative ownership of the Docker Hub ACCOUNT by the lab — the
  profile is empty, there is no verified-publisher badge, and Docker Hub exposes no org linkage.
  Verification is circumstantial-but-strong (the repo's own Makefile default + the lab name + 58
  matching ts-* images + pull volume); recorded as such, not overclaimed.
- **Our conduct (unchanged, compliant):** `deploy.sh` builds all images FROM SOURCE locally into
  minikube's docker, tagged `codewisdom/ts-*:1.0.2` solely so the upstream deploy manifests resolve
  to the local (fault-bearing) builds; nothing is ever pushed to any registry
  (`c2-license-audit.md` conduct rule 2, "never re-push", upheld).

## §3 Survey-paper citation (pinned — ready for `c2-license-audit.md` at Phase C)

> Xiang Zhou, Xin Peng, Tao Xie, Jun Sun, Chao Ji, Wenhai Li, and Dan Ding. "Fault Analysis and
> Debugging of Microservice Systems: Industrial Survey, Benchmark System, and Empirical Study."
> *IEEE Transactions on Software Engineering*, vol. 47, no. 2, pp. 243–260, 2021 (first online
> December 2018). DOI: **10.1109/TSE.2018.2887384**.

Verified against: the DOI landing page (https://dl.acm.org/doi/10.1109/TSE.2018.2887384), NASA ADS
bibcode 2021ITSEn..47..243Z (https://ui.adsabs.harvard.edu/abs/2021ITSEn..47..243Z/abstract), NSF
PAR record 10357355 (https://par.nsf.gov/biblio/10357355), and the `FudanSELab/train-ticket`
README's own reference to the paper. The fault-replicate README itself points to the study's
companion page (https://fudanselab.github.io/research/MSFaultEmpiricalStudy/) — the [PAGE] input of
`f-corpus-spec.md`. Author-order note: one README rendering transposed the middle authors (Li/Ji);
the order above follows IEEE/ADS metadata; the DOI is the pin. This record does NOT edit
`c2-license-audit.md` (B0 scope); Phase C pins the citation there (plan §4-B0-3 / C-F4).

## §4 DEAD-CITATION finding + Phase-C correction plan (NOT applied in B0)

**Finding — the existing TT stratum's provenance is dead at all three coordinates:**
- `repo: github.com/miaoti/train-ticket-injection` → **HTTP 404** (API check 2026-07-13).
- `branch: MIST-trainticket` → absent from the true public ancestor (AsifShaafi branch GET 404).
- `commit: a1767ab3` → not in the public repo's object store (commit lookup 422 not-found).
  ⇒ the pinned branch+commit exist only in the LOCAL clone; reproducibility of the TT stratum's SUT
  build currently rests on the local machine + the user-gated fork-publication decision.

**True lineage (established in §1/§2):**
`FudanSELab/train-ticket` (Apache-2.0) → content re-upload with retained LICENSE + README
attribution (2025) → `AsifShaafi/train-ticket-injection`, branch `injection` (public; the actual
clone source in `deploy.sh`) → LOCAL unpublished branch `MIST-trainticket` @ `a1767ab3` (the
corpus's deployed SUT; the disclosed synthetic fabricated-ack fork `f57102e6` for the G3 exemplar
sits on top of this local line).

**Affected files (enumerated 2026-07-13; 15 total):**
- 14 carry the dead `miaoti/train-ticket-injection` URL: 11 case JSONs
  (`debug/a-main/benchmark/cases/`: TT-cancel-refund-fabricatedack-001, TT-cancel-refund-clean-001,
  TT-cancel-refund-natural-001, TT-createaccount-clean-001, TT-createaccount-agreement-001,
  TT-contacts-noop-modify-benign-001, TT-contacts-dedupe-benign-001, TT-adminroute-lostwrite-001,
  TT-adminroute-control-001, TT-adminbasic-contacts-lostwrite-001,
  TT-adminbasic-contacts-control-001) + 2 eligibility JSONs
  (`debug/a-main/benchmark/eligibility/tt-elig-genuine.json`, `tt-elig-benign.json`) +
  `debug/a-main/benchmark/b4/e2/e2-run.sh`.
- `debug/a-main/benchmark/README.md` additionally references the `MIST-trainticket`/`a1767ab3`
  identifiers (15th file).

**Correction plan (Phase C applies; per plan §4-B0-2 "fix field or dated disclosure"):**
1. Per affected file: set `sut.repo` → `https://github.com/AsifShaafi/train-ticket-injection`
   (immediate PUBLIC ancestor; base branch `injection`), KEEP `branch: MIST-trainticket` and
   `commit: a1767ab3` as LOCAL build identifiers, and add a dated disclosure note/field: the branch
   is a local unpublished derivative of the public ancestor; publication pending the USER-gated
   fork-publication decision (pre-E6 release obligation, plan §8).
2. Same re-point + note in `e2-run.sh` comments and `benchmark/README.md`.
3. Dated freeze §6 row recording the correction; corpus-wide validator re-run after the edits.
4. Scope discipline: lineage wording cites AsifShaafi for the Apache-2.0 BASE only (§1 caveat about
   its re-hosted unlicensed `ts-error-*` branches).
None of this is performed in B0 (this record + `f-corpus-spec.md` are the only files created; no
existing file touched). FILE_INDEX.md rows for these two new files are owed by the committing
orchestrator in the same change.

## §5 Clean-room conduct log (this B0 session)

- Fetched (prose only): fault-replicate README ×2 (WebFetch-summarized), the companion study page,
  `ts-fault.txt` (classified as an experiment-metrics table — no fault prose; nothing used), GitHub
  license/branches/commit API endpoints, Docker Hub v2 endpoints, `FudanSELab/train-ticket` README +
  Makefile (Apache-2.0 base — permitted), `AsifShaafi/train-ticket-injection` README (base content —
  permitted). A TSE-paper PDF mirror fetch returned unparseable binary — no paper-body content
  ingested.
- Refused: ALL fault-implementation content — `ts-error-*` / `istio-error-*` branch contents,
  `faults/`, `faults-dingding/`, `faults-lwh/`, any diff/patch — on every host. Branch NAMES only
  were read via the API (metadata).
- Forward rule (C-F3): the Phase-B implementer's ONLY upstream-derived input is `f-corpus-spec.md`;
  the orchestrator never pastes upstream diff/code into the implementer's context.

## §6 B0-GATE VERDICT

**PASS — Phase B may proceed as authored**, on the three gate legs:
1. **≥6 eligible candidates exist:** 7 by description (F1, F8, F10, F11, F13, F14, F20) — floor 6
   with exactly ONE spare (margin disclosed; F12 is the only swap-alternate and only if live-upgraded).
2. **Lineage/license resolved enough to author:** yes — replicate-by-description on the Apache-2.0
   base per the standing `c2-license-audit.md` disposition; true public lineage pinned
   (FudanSELab → AsifShaafi@injection → local MIST-trainticket); clean-room protocol operational.
3. **Citation pinned:** §3 (Phase C copies it into `c2-license-audit.md`).

**Mandatory surface accompanying the PASS (plan §1.1/§4-B0-1):** the post-survey S1 distinct-site
projection is **10–13 < 20** (eligible-AND-unoccupied = 2: F8, F14; hard ceiling 13 on the current
SUT set) → the **stop-and-replan decision is SURFACED before Phase B budget spend**; options and the
survey's recommendation (accept-and-disclose) in `f-corpus-spec.md` §6.

**Items for the USER:**
1. **Fork-publication decision** (pre-existing flag, now SHARPENED): the cases' pinned commit
   `a1767ab3` is verifiably unpublished — E6 reproducibility requires either publishing the local
   `MIST-trainticket` branch (as a fork/repo under user control) or re-pinning cases to a public
   commit; Phase C's correction (§4) is honest either way but cannot make the build reproducible by
   itself.
2. **NEW — the <20 stop-and-replan option choice** (accept-disclosed-shortfall vs futile-extension vs
   out-of-plan widening; survey recommends accept-and-disclose).
3. **NEW (minor) — lineage-wording constraint:** AsifShaafi re-hosts the unlicensed fault-replicate
   `ts-error-*` branches under an Apache-2.0 repo stamp; our citations scope the license claim to
   base content only (wording rule already folded into §4's correction plan; no user action unless
   the user prefers citing FudanSELab/train-ticket directly as the license anchor).
