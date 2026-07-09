# §1.95.05 Rater-artifact SIDECAR format v1 (deliverable 0 — B-B1)

**One JSON file per case: `<case-dir>/sidecar.json`.** The single behavior-transcript format EVERY
producer emits (seed-capture driver · step-4 M-yield · step-5 wild-flag capture bundles). B4 consumes
ONLY `case.yaml` + `sidecar.json`. The sidecar records BEHAVIOR, never verdicts/labels.

```json
{
  "sidecar_version": 1,
  "case_id": "<true case id — sealed-manifest side only, B4 replaces with the opaque id>",
  "producer": "seed-capture-driver | myield-runner | wildflag-bundle",
  "mist_commit": "<pin sha (7d69de9); producer stamp — B4 STRIPS it from rater-facing output>",
  "sut": { "name": "trainticket", "version_ref": "<image tag/digest set id>" },
  "records": [
    { "t_rel_ms": 0,    "kind": "request",     "method": "POST", "path": "/api/v1/…", "payload": "<verbatim body or null>" },
    { "t_rel_ms": 118,  "kind": "response",    "status": 201,    "body": "<verbatim FULL body>" },
    { "t_rel_ms": 5250, "kind": "observation", "probe": "GET /api/v1/…", "status": 200, "body": "<verbatim read-back/probe body>" }
  ]
}
```

**Frozen rules:**
1. `t_rel_ms` is RELATIVE to the first record; **no absolute timestamps anywhere** (M4 stratum-tell).
   Relative spacing is kept — judgment-relevant for documented consistency windows.
2. `records` is the COMPLETE ordered transcript of the case's stimulus: every request the scenario
   issued (auth/login steps included, credentials REDACTED as `"<redacted>"`), every response with
   its FULL body, every durable-state observation (read-back GET / SQL probe / broker count — probe
   text describes the query neutrally, e.g. `"queue depth shipping-task"`).
3. NO verdict vocabulary in any field (no FIRE/gate/oracle words); `producer`+`mist_commit` are
   provenance for US and on B4's strip-list.
4. Deterministic: producers write records in wall order with stable field order; two captures of the
   same behavior may differ in bodies/timing (that's real), but a given sidecar file is byte-stable.
5. Redaction is the producer's job (tokens, passwords, Set-Cookie values → `"<redacted>"`).
6. `case.yaml`'s `artifacts.raw_logs` includes the sidecar path (freeze §6 amendment row 2026-07-09).
