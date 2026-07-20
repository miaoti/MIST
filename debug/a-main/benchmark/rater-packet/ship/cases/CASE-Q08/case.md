# Case CASE-Q08

**System:** trainticket  (version set: train-ticket (pinned image set))

**Reference material:** Use ONLY the provided documentation bundle: `docs-bundles/trainticket/`

## What was done (in order)

- `t+0ms`  **POST /api/v1/userservice/users/register**
  - request body: `<credentials redacted>`
  - response: HTTP 201 — body: `<response redacted (carries a session token)>`
- `t+1053ms`  **POST /api/v1/users/login**
  - request body: `<credentials redacted>`
  - response: HTTP 200 — body: `<response redacted (carries a session token)>`
- `t+1177ms`  **POST /api/v1/contactservice/contacts**
  - request body: `{"accountId":"460fb741-d516-407a-8a96-6cf6c7e825c0","name":"Corpus Contact","documentType":1,"documentNumber":"NM20260710","phoneNumber":"13900000000"}`
  - response: HTTP 201 — body: `{"status":1,"msg":"Create contacts success","data":{"id":"20627a10-bdab-4d8c-9627-a5ef11986442","accountId":"460fb741-d516-407a-8a96-6cf6c7e825c0","name":"Corpus Contact","documentType":1,"documentNumber":"NM20260710","phoneNumber":"13900000000"}}`
- `t+1212ms`  **PUT /api/v1/contactservice/contacts**
  - request body: `{"id":"20627a10-bdab-4d8c-9627-a5ef11986442","accountId":"460fb741-d516-407a-8a96-6cf6c7e825c0","name":"Corpus Contact","documentType":1,"documentNumber":"NM20260710","phoneNumber":"13900000000"}`
  - response: HTTP 200 — body: `{"status":1,"msg":"Modify success","data":{"id":"20627a10-bdab-4d8c-9627-a5ef11986442","accountId":"460fb741-d516-407a-8a96-6cf6c7e825c0","name":"Corpus Contact","documentType":1,"documentNumber":"NM20260710","phoneNumber":"13900000000"}}`
- `t+2781ms`  observed state via `GET /api/v1/contactservice/contacts/account/{accountId}`: HTTP 200 — `{"status":1,"msg":"Success","data":[{"id":"20627a10-bdab-4d8c-9627-a5ef11986442","accountId":"460fb741-d516-407a-8a96-6cf6c7e825c0","name":"Corpus Contact","documentType":1,"documentNumber":"NM20260710","phoneNumber":"13900000000"}]}`

## Your task

Using ONLY the reference material above, judge whether the observed outcome is a
**genuine defect**, **by-design / benign**, or **underspecified** (the intended
behavior is not derivable from the reference material). Record your answer in
`ballot.yaml`.
