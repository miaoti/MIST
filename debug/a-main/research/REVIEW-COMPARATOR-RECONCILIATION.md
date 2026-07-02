# Comparator cold-review reconciliation (666c461 + c4b9a08; 3 reviewers, 2026-07-02)

Reviews: [A — soundness](REVIEW-COMPARATOR-A-soundness.md),
[B — bindings faithfulness](REVIEW-COMPARATOR-B-bindings.md),
[C — integration](REVIEW-COMPARATOR-C-integration.md). No shared context. Verdicts:
A "sound + fairly gated, but cannot execute (auth) and the transport/delay asymmetry
must be pre-stated"; B "defensible calibration-scoped translation, but the state
clauses must be completed pre-run"; C "ready-with-fixes + full calibration
checklist". **Critical timing luck: B verified logs/comparator-reports/ is EMPTY —
no run has happened, so everything below is a pre-run amendment, not damage.**

## Consensus → dispositions (fix wave applied same day, suites 35+331+93 green)

| # | Finding | Disposition |
|---|---|---|
| C1 | **Unauthenticated comparator path** (A-F1 CRITICAL, C-F1): nothing calls ensureReady(); calibration DOA. | **FIXED:** runComparatorMode fail-fasts on `MstAuthHandler.ensureReady()`. |
| C2 | **Fault-leg transport failure scored as detection** (A-F2, C-F8): comparator-favoring recall inflation vs MIST's read-back-error category. | **FIXED:** transport-marked CheckOutcomes; fault-leg verdicts whose ONLY failures are transport reclassify to comparator-infra-failure. Pinned by test. |
| C3 | **Bindings narrowed exactly onto MIST's registry keys** (B-1/2/3 — the most attackable pattern; contradicts the prereg "full frozen set" operating point). | **FIXED (amendment A2, pre-run):** adminroute's second frozen read path bound (per-entity GET via ${field:id} templating + entity-matches mode); list membership extended to id+start+end; contacts membership extended to all five submitted fields. G3 rider: the G3 binding round binds the FULL frozen set incl. failure contracts (B-7's landmine: unbound failure contracts at G3 would false-flag legit rejections). |
| C4 | **Design §4's pre-stated outcome falsified by committed G0 evidence** (B-4): the fabricated fault ack is SLOPPY (msg case variant; adminroute data:null) → response clauses will likely flag too. | **DISCLOSED (design §4 correction):** injection-realism artifact; calibration acceptance now reads per-clause outcomes (STATE clauses must FAIL under fault / PASS under control); response-clause flags recorded + attributed. |
| C5 | **Zero-wait STATE_GET** (A-F5 analysis: fair for calibration; C-F3: ~5–15%/endpoint transient control-abort; G3 delay-fault inflation). | **FIXED (amendment A3):** presence-expect checks retry to MIST's pre-registered 10s/500ms budget (harness-level read timing; decisive-read transport failures transport-marked). Absence checks single-shot. Pinned by the benign-slow-visibility test. |
| C6 | **Mid-loop throws lose the report** (A-F4, C-F2 — the run-#2 shape again). | **FIXED:** per-endpoint try/catch → comparator-infra-failure + continue; report ALWAYS written; unknown-triple test updated to the new semantics. |
| C7 | **Clear-failure continued the loop** (A-F3): later verdicts on a possibly-faulted SUT. | **FIXED:** first clear failure stops verdict work; later endpoints = "not-run". Pinned by test. |
| C8 | **mist.fault.injection.enabled silently ignored** (C-F4). | **FIXED:** fail-fast requirement in runComparatorMode. |
| C9 | **Report gaps** (A-F6b): no per-check cite, no fault manifest. | **FIXED:** cite + faultManifest + transportFailure in the report JSON. Pinned by test. |
| C10 | **No client timeouts** (C-F6). | **FIXED:** connect 10s / socket 20s on the standalone client. |
| C11 | **"Matched inputs" wording** (A-F6a, B-5): templates ≠ the pairing's literal bodies (partly forced — zero contacts pairing records exist; adminroute reuse would smuggle MIST's isolation adapter). | **DISCLOSED (design §3 correction):** matched ENDPOINTS + FAULTS + budget, not byte-identical bodies; the ${uuid:id} key is licensed by the frozen contract and comparator-favoring if anything. |
| C12 | **Ops items** (C-F5/F7): shipped root-api-registry dirtied pre-branch; assertions.path CWD-relative; trace.file.path must exist. | **DISCLOSED** in the design's operational preconditions + C's calibration checklist (scratch registry path = H9 extension). |
| C13 | **G3 riders** (A-F5): comparator can never show a control false alarm by construction → report the infra-failure rate as the assertion oracle's cost; delay-vs-loss stratification for delay-type faults. | **CARRIED** onto the G3 pre-statement list (with C3's full-set binding round + B-7's failure-contract re-scoping). |

Also verified across reviewers: freeze hygiene correct (design→bindings→runner, files
byte-identical to freeze commits); §4 outcome/control-gate/f2-report/freshness/
byte-additivity all pinned; bindings MSG rules manifest-compliant; the fork honoring
client-supplied ids is a live-run unknown with a SAFE failure mode (control gate).

## Net-bias statement (print with the study, per B)
Response-contract bindings faithful (and comparator-favoring at calibration via the
sloppy-ack artifact); v1 state-contract narrowings were pro-MIST and are now
completed (A2); remaining known asymmetries are pro-comparator (transport
reclassification + A3 retry remove the two anti-MIST inflations; the infra-failure
channel hides comparator control-FPs → report its rate at G3).

## Post-fix status
Comparator = BUILT + REVIEWED + FIX WAVE APPLIED; calibration checklist ready
(review C). Next: the LIVE calibration run per the checklist (cluster restart), then
the calibration record commit.
