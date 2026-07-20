# Case CASE-Q09

**System:** trainticket  (version set: train-ticket (pinned image set))

**Reference material:** Use ONLY the provided documentation bundle: `docs-bundles/trainticket/`

## What was done (in order)

- `t+0ms`  **POST /api/v1/userservice/users/register**
  - request body: `<credentials redacted>`
  - response: HTTP 201 — body: `<response redacted (carries a session token)>`
- `t+253ms`  **POST /api/v1/users/login**
  - request body: `<credentials redacted>`
  - response: HTTP 200 — body: `<response redacted (carries a session token)>`
- `t+345ms`  **POST /api/v1/contactservice/contacts**
  - request body: `{"accountId":"887401ce-c39f-48b5-b84f-a016856b7807","name":"CB Contact","documentType":1,"documentNumber":"D12345678","phoneNumber":"13800000000"}`
  - response: HTTP 201 — body: `{"status":1,"msg":"Create contacts success","data":{"id":"86ca8bf6-c1b4-4641-a8f0-41baf3fc1689","accountId":"887401ce-c39f-48b5-b84f-a016856b7807","name":"CB Contact","documentType":1,"documentNumber":"D12345678","phoneNumber":"13800000000"}}`
- `t+436ms`  **POST /api/v1/contactservice/contacts**
  - request body: `{"accountId":"887401ce-c39f-48b5-b84f-a016856b7807","name":"CB Contact","documentType":1,"documentNumber":"D12345678","phoneNumber":"13800000000"}`
  - response: HTTP 201 — body: `{"status":0,"msg":"Contacts already exists","data":null}`
- `t+2034ms`  observed state via `GET /api/v1/contactservice/contacts/account/{accountId}`: HTTP 200 — `{"status":1,"msg":"Success","data":[{"id":"86ca8bf6-c1b4-4641-a8f0-41baf3fc1689","accountId":"887401ce-c39f-48b5-b84f-a016856b7807","name":"CB Contact","documentType":1,"documentNumber":"D12345678","phoneNumber":"13800000000"}]}`

## Your task

Using ONLY the reference material above, judge whether the observed outcome is a
**genuine defect**, **by-design / benign**, or **underspecified** (the intended
behavior is not derivable from the reference material). Record your answer in
`ballot.yaml`.
