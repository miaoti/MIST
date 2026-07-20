# Case SCREEN-B1

**System:** trainticket  (version set: codewisdom 1.0.0)

**Reference material:** Use ONLY the provided documentation bundle: `docs-bundles/trainticket/`

## What was done (in order)

- `t+0ms`  **POST /api/v1/userservice/users/register**
  - request body: `<credentials redacted>`
  - response: HTTP 201 — body: `<response redacted (carries a session token)>`
- `t+176ms`  **POST /api/v1/users/login**
  - request body: `<credentials redacted>`
  - response: HTTP 200 — body: `<response redacted (carries a session token)>`
- `t+258ms`  **POST /api/v1/contactservice/contacts**
  - request body: `{"accountId":"361b82e1-45ce-43e2-bdbc-5a75a9b4096f","name":"CB Contact","documentType":1,"documentNumber":"D12345678","phoneNumber":"13800000000"}`
  - response: HTTP 201 — body: `{"status":1,"msg":"Create contacts success","data":{"id":"5bffdb85-30c3-4c04-b3f8-e4e30376f459","accountId":"361b82e1-45ce-43e2-bdbc-5a75a9b4096f","name":"CB Contact","documentType":1,"documentNumber":"D12345678","phoneNumber":"13800000000"}}`
- `t+289ms`  **POST /api/v1/contactservice/contacts**
  - request body: `{"accountId":"361b82e1-45ce-43e2-bdbc-5a75a9b4096f","name":"CB Contact","documentType":1,"documentNumber":"D12345678","phoneNumber":"13800000000"}`
  - response: HTTP 201 — body: `{"status":0,"msg":"Contacts already exists","data":null}`
- `t+1869ms`  observed state via `GET the acting user's contacts (durable durable-state check)`: HTTP 200 — `{"status":1,"msg":"Success","data":[{"id":"5bffdb85-30c3-4c04-b3f8-e4e30376f459","accountId":"361b82e1-45ce-43e2-bdbc-5a75a9b4096f","name":"CB Contact","documentType":1,"documentNumber":"D12345678","phoneNumber":"13800000000"}]}`

## Your task

Using ONLY the reference material above, judge whether the observed outcome is a
**genuine defect**, **by-design / benign**, or **underspecified** (the intended
behavior is not derivable from the reference material). Record your answer in
`ballot.yaml`.
