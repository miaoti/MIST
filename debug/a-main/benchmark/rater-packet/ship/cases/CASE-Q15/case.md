# Case CASE-Q15

**System:** trainticket  (version set: train-ticket (pinned image set))

**Reference material:** Use ONLY the provided documentation bundle: `docs-bundles/trainticket/`

## What was done (in order)

- `t+0ms`  **POST /api/v1/users/login**
  - request body: `{"username":"<redacted>","password":"<redacted>"}`
  - response: HTTP 200 — body: `<login response — token redacted>`
- `t+88ms`  **POST /api/v1/adminbasicservice/adminbasic/contacts**
  - request body: `{"id":"b4b250ee-02a5-4e99-839c-41e50d97dd28","accountId":"199c5365-6458-415d-9599-10dc907606b3","name":"Rater Contact","documentType":1,"documentNumber":"DN-A1","phoneNumber":"13800000000"}`
  - response: HTTP 200 — body: `{"status":1,"msg":"create contacts success","data":null}`
- `t+3109ms`  observed state via `durable contact store, scoped by the contact id in the request above (single-key lookup, not a windowed list): did the contact collection gain the submitted contact?`: HTTP 200 — `contact collection count unchanged (the submitted contact is not present)`

## Your task

Using ONLY the reference material above, judge whether the observed outcome is a
**genuine defect**, **by-design / benign**, or **underspecified** (the intended
behavior is not derivable from the reference material). Record your answer in
`ballot.yaml`.
