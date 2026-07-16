# rater-sidecars-STAGING (completion-set wave, Phase A item A3) — NOT the sealed set

Staged pre-seal materials. **Nothing here is rater-facing yet; nothing here touches the
sealed sets** (`b4/rater-sidecars/` + `b4/MANIFEST-r2.json` + `b4/s3/SEALED-MANIFEST.sha256`
are READ-ONLY this wave). Every swap below happens ONLY at the gated, USER-witnessed
Step-5 (re-)seal.

## CASE-Q47/ — the S3-BENIGN-01 re-cut (A3-i)

- Defect of record (E1+R2 corrections row): the sealed S3 calibration render's opaque id
  `S3-BENIGN-01` carries the label ("benign") in its rater-facing TITLE.
- Re-cut: `b4_harness.render('cases/oteldemo-checkout-eventual-benign-001.json',
  'b4/rater-sidecars/oteldemo-checkout-eventual-benign-001.json', 'CASE-Q47', …)` —
  the hardened opaque-id guard (label vocabulary / true-id / banned tokens fail loud)
  passed; producer `wildflag-bundle`, mist_commit `10eb19e`.
- Staged render: `case.md` (sha256 `4d8e66df4fea6a2aac2e9e553e5a1758b8cc3a65acf1365af4bb40fcc6816d7c`)
  + `ballot.yaml`.
- **Expected hash difference vs the sealed record** (`S3-BENIGN-01`,
  `75dce034…`): the seal is v2 (2026-07-13); the neutralized sidecar is rev 3
  (E1+R2 2026-07-14 corrections) — the staged render reflects the CURRENT
  case+sidecar of record. Adjudicating that difference is part of the
  USER-witnessed re-seal, not this wave.
- `CASE-Q47` is a PROVISIONAL blind token: the sealed manifest itself notes "final
  corpus opaque-id assignment + merge is the cross-track/user-side assembly step."
