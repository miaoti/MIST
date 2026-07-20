# Case SCREEN-2

**System:** trainticket  (version set: codewisdom 1.0.0)

**Reference material:** Use ONLY the provided documentation bundle: `docs-bundles/trainticket/`

## What was done (in order)

- `t+0ms`  **POST /api/v1/userservice/users/register**
  - request body: `<credentials redacted>`
  - response: HTTP 201 — body: `<response redacted (carries a session token)>`
- `t+189ms`  **POST /api/v1/users/login**
  - request body: `<credentials redacted>`
  - response: HTTP 200 — body: `<response redacted (carries a session token)>`
- `t+274ms`  **POST /api/v1/orderservice/order**
  - request body: `{"accountId":"8b1e60d4-3a72-49cf-a1d6-05e8f2b93c17","status":1,"price":"95.0","boughtDate":"2026-02-05 14:20:00","travelDate":"2027-10-18","travelTime":"2027-10-18 07:00:00","from":"Wu Han","to":"Chang Sha","trainNumber":"","coachNumber":2,"seatClass":2,"seatNumber":"2B","contactsName":"S Buyer","documentType":1,"contactsDocumentNumber":"S3390014477"}`
  - response: HTTP 400 — body: `{"status":0,"msg":"Order create failed.","data":null}`
- `t+3110ms`  observed state via `durable-state check — query the durable order records for the account submitted above`: HTTP 200 — `no matching durable record present`

## Your task

Using ONLY the reference material above, judge whether the observed outcome is a
**genuine defect**, **by-design / benign**, or **underspecified** (the intended
behavior is not derivable from the reference material). Record your answer in
`ballot.yaml`.
