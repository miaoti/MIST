# Case CASE-Q03

**System:** teastore  (version set: TeaStore v1.4.2)

**Reference material:** Use ONLY the provided documentation bundle: `docs-bundles/teastore/`

## What was done (in order)

- `t+0ms`  **POST /tools.descartes.teastore.webui/loginAction?<credentials redacted>**
  - response: HTTP 200 — body: `<response redacted (carries a session token)>`
- `t+44ms`  **POST /tools.descartes.teastore.webui/cartAction?addToCart=&productid=42**
  - response: HTTP 200 — body: `<success-shaped page rendered>`
- `t+144ms`  **POST /tools.descartes.teastore.webui/cartAction?firstname=7002&lastname=Sample&address1=Street1&address2=City1&cardtype=visa&cardnumber=314159265359&expirydate=12%2F2029&confirm=Confirm**
  - response: HTTP 200 — body: `<success-shaped page rendered>`
- `t+120181ms`  observed state via `durable-state check — query the durable order collection for the key submitted above`: HTTP 200 — `no matching durable record present`

## Your task

Using ONLY the reference material above, judge whether the observed outcome is a
**genuine defect**, **by-design / benign**, or **underspecified** (the intended
behavior is not derivable from the reference material). Record your answer in
`ballot.yaml`.
