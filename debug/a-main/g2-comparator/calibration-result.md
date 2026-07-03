# G2 comparator calibration RESULT — ACCEPTED (2026-07-02)

Run `1783032488954` (exit 0, MIST-side wall ≈ 6.0 min), executed per the review-C
checklist against the lean TrainTicket minikube deploy (29 pods Running, both target
deployments verified agent-only before AND after). Report: committed copy at
[calibration-report.json](calibration-report.json) (canonical
`logs/comparator-reports/comparator_trainticket_g2_calibration_1783032488954.json`).
Config: `mist-cli/src/main/resources/My-Example/trainticket-g2-calibration.properties`
(committed with this record). Protocol chain: prereg frozen `5045b36`+A1 `c7a7711` →
blind set frozen `15954a8` → bindings frozen `c4b9a08` + pre-run amendments A2/A3
`f42ea2a` → runner `666c461` + review fix wave `f42ea2a` → THIS run (first and only
comparator execution — reviewer B verified no earlier run existed).

## Verdicts (acceptance per the corrected design §4)

| Endpoint | Verdict | Control | Fault STATE clauses | Acceptance |
|---|---|---|---|---|
| POST /api/v1/adminrouteservice/adminroute | **flag** | ALL 6 evaluated clauses PASS | list membership **ABSENT at the 10 s cap** (10 polls) AND per-entity `GET /routes/{id}` **ABSENT** (21 polls) — both transportFailure=false | ✅ |
| POST /api/v1/adminbasicservice/adminbasic/contacts | **flag** | ALL 5 evaluated clauses PASS | 5-field membership **ABSENT at the cap** (19 polls) — transportFailure=false | ✅ |

**ACCEPTED:** each endpoint flags via genuine STATE-clause failures on the injected
LOST_WRITE (never transport), and every control leg passed every evaluated clause —
the frozen blind contracts are executable, the bindings are live-validated
(including the client-supplied-id membership key: the control run's echoed
`data.id` equals the submitted UUID, settling review B's "never live-demonstrated"
risk), and the A3 bounded retry behaved as designed (control presence in 1 poll;
fault absence held through the full ~10 s budget).

## Per-clause outcomes (fault legs)

adminroute: HTTP_STATUS **PASS** (200) · ENVELOPE_STATUS **PASS** (1 — the masking) ·
ENVELOPE_DATA **FAIL** (data:null) · MSG_CONTAINS **FAIL** ("create and modify
success") · STATE list **FAIL** (absent) · STATE per-entity **FAIL** (absent) ·
failure-contract NOT_CHECKABLE.
contacts: HTTP_STATUS **PASS** · ENVELOPE_STATUS **PASS** · ENVELOPE_DATA **PASS**
(the fabricated ack's data:null coincides with this endpoint's frozen contract) ·
MSG_CONTAINS **FAIL** ("create contacts success") · STATE 5-field **FAIL** (absent) ·
failure-contract NOT_CHECKABLE.

**Artifact attribution (disclosed per the §4 correction / review B finding 4):** the
MSG failures (both endpoints) and adminroute's ENVELOPE_DATA failure are the
**injection-realism artifact** — the SUT fork's fabricated ack is sloppy (lowercase
msg variants, data:null) and thus distinguishable from the pristine success ack by
exact response comparison. A real masked failure would return the pristine ack, so
**the load-bearing detection channel for the study is the STATE clause — which
independently failed on every fault leg here**, validating the machinery the
calibration exists to validate. Both readings are reported; neither is suppressed.

## MIST cross-reference (the competence floor, prereg §2)

The SAME two constructed faults: MIST's read-back oracle **FIREd on adminroute in
the Gate-1 run #3 automated pairing** (strong stratum, `prep/gate1-result.md`), and
the contacts LOST_WRITE is validated by the manual G0 evidence
(`prep/sut-fault-injection-capability.md` §9; the automated pairing's contacts leg
remains unhooked — a generation gap, not an oracle gap). The comparator's frozen
blind set **flags BOTH calibration faults → the prereg §2 competence floor is met**
(a set missing these would have been demonstrably incompetent; the pre-registered
failed-calibration branch was not needed). Per the prereg's decisive-result rule,
these injected wins are **calibration evidence only** (the fault class is
oracle-co-designed); the PC-moving comparison happens at G3 over real defects.

## G2 CLOSURE

Gate-2's two deliverables (README §8) are met:
1. **The one-paragraph Cast delta a skeptical PC accepts** — prereg §1 v2
   (post-hostile-PC-review rewrite), acceptance conditional-on-execution as
   disclosed.
2. **A competently-configured assertion-based comparator is set up** — blind-authored
   (upstream-only provisioning), frozen, 3-cold-reviewed, fix-waved, and now
   **calibration-accepted on both Gate-1 faults with all-clean control legs**.

G2 prereg §3 checklist: steps 1–5 ALL DONE (freeze records in the prereg; this file
is the step-4/5 record). **NEXT per the plan: G3** (`prep/g3-sut2-triples-prereg.md`
v2) with the carried riders: full-frozen-set binding round incl. failure contracts,
infra-failure-rate reporting, delay-vs-loss stratification, the writer-side
method/ordinal correlator before per-pair tallies feed claims, and the H9/H9-ext
registry scratch paths (both honored this run: shipped input-fetch AND root-api
registries stayed clean — the preflight wrote to `target/comparator-scratch/`).

Post-run state: both deployments agent-only (verified), cluster stopped.
