# Case SCREEN-1

**System:** trainticket  (version set: codewisdom 1.0.0)

**Reference material:** Use ONLY the provided documentation bundle: `docs-bundles/trainticket/`

## What was done (in order)

- `t+0ms`  **POST /api/v1/userservice/users/register**
  - request body: `<credentials redacted>`
  - response: HTTP 201 — body: `<response redacted (carries a session token)>`
- `t+248ms`  **POST /api/v1/users/login**
  - request body: `<credentials redacted>`
  - response: HTTP 200 — body: `<response redacted (carries a session token)>`
- `t+402ms`  **POST /api/v1/orderservice/order**
  - request body: `{"accountId":"c47f9a12-6b30-4e51-9d84-2f0a7c118e63","status":1,"price":"120.0","boughtDate":"2026-02-03 09:15:00","travelDate":"2027-11-04","travelTime":"2027-11-04 08:30:00","from":"Nan Jing","to":"Hang Zhou","trainNumber":"G7","coachNumber":3,"seatClass":2,"seatNumber":"3C","contactsName":"S Buyer","documentType":1,"contactsDocumentNumber":"S7781120034"}`
  - response: HTTP 200 — body: `{"status":1,"msg":"Success","data":{"id":"e58c2740-91b6-4d0a-8a3e-77c1d9a4b2f5","accountId":"c47f9a12-6b30-4e51-9d84-2f0a7c118e63","from":"Nan Jing","to":"Hang Zhou","trainNumber":"G7","coachNumber":3,"seatClass":2,"seatNumber":"3C","status":1,"price":"120.0"}}`
- `t+3520ms`  observed state via `durable-state check — query the durable order records for the account submitted above`: HTTP 200 — `no matching durable record present`

## Your task

Using ONLY the reference material above, judge whether the observed outcome is a
**genuine defect**, **by-design / benign**, or **underspecified** (the intended
behavior is not derivable from the reference material). Record your answer in
`ballot.yaml`.
