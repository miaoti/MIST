# Case CASE-Q04

**System:** trainticket  (version set: train-ticket (pinned image set))

**Reference material:** Use ONLY the provided documentation bundle: `docs-bundles/trainticket/`

## What was done (in order)

- `t+0ms`  **POST /api/v1/users/login**
  - request body: `{"username":"<redacted>","password":"<redacted>"}`
  - response: HTTP 200 — body: `<login response — token redacted>`
- `t+95ms`  **POST /api/v1/adminrouteservice/adminroute**
  - request body: `{"id":"placeholder","startStation":"shanghai","endStation":"taiyuan","stationList":"shanghai,taiyuan","distanceList":"0,1350"}`
  - response: HTTP 200 — body: `{"status":1,"msg":"create and modify success","data":null}`
- `t+3118ms`  observed state via `durable route store, scoped by the start/end stations in the request above: did the route collection gain the submitted route?`: HTTP 200 — `route collection count unchanged (the submitted route is not present)`

## Your task

Using ONLY the reference material above, judge whether the observed outcome is a
**genuine defect**, **by-design / benign**, or **underspecified** (the intended
behavior is not derivable from the reference material). Record your answer in
`ballot.yaml`.
