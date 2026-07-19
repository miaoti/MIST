# F8 (user-selection, CORRUPTED-present, NEW-SITE) — B-m6 LIVE VERIFICATION: PASS (2026-07-17)

**Fault:** ts-user-service `userSelectionFaultMode=corrupt` (fork fcorpus-build; DEFAULT off).
Upstream-grounded: FudanSELab survey F8 (VIP/entitlement token path; vanilla TT has no
VIP/redis primitive → modelled on the persisted user document-type SELECTION attribute,
DISCLOSED). f-corpus-spec eligible + UNOCCUPIED ⇒ a NEW DISTINCT SITE (ts-user-service).

**Mechanism:** on register/update the selection token is misread → silently persists the
DEFAULT documentType (0) instead of the submitted value, while acking 2xx. A masked
CORRUPTED-present durable write.

## Paired live measurement (ground truth = direct GET /userservice/users)
| leg | mode | submitted documentType | PERSISTED documentType |
|---|---|---|---|
| CONTROL | none | 1 | **1** (correct) |
| FAULT | corrupt | 1 | **0** (WRONG — present-but-corrupted) |

⇒ Both register-acks 2xx; the durable value is CORRECT in control, CORRUPTED in fault.
**MIST column = not_applicable "out-of-scope-by-design"**: MIST detects acknowledged-but-LOST
(absence / value-delta-from-own-baseline), NOT acknowledged-but-CORRUPTED (present-but-wrong)
— the value is PRESENT, so MIST correctly ABSTAINS (never a false miss; the lost-not-corrupted
rail). The benchmark is BROADER than MIST's oracle — this case is in-corpus, MIST honestly n_a.

## B-m6 disposition: PASS (in-class verified live, paired control reference per B4).

**RE-CAPTURE CLOSURE (2026-07-18):** the evidence-form gap is CLOSED. `runners/fcorpus/f8-driver.sh`
+ preserved `legs.log` now back this case with a raw log like its siblings:
- CTRL (faultmode=none): submitted documentType=1 -> persisted **1** (register-ack HTTP 201)
- FLT (selectionfaultmode=corrupt): submitted documentType=1 -> persisted **0** (register-ack HTTP 201)
Read-back is by the REGISTER-returned userId (ts-user-service row); the original inline capture read
the auth-login id (a different store) — corrected. B-m6 PASS re-confirmed live.
