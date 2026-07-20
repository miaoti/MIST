# Case CASE-Q05

**System:** teastore  (version set: TeaStore v1.4.2)

**Reference material:** Use ONLY the provided documentation bundle: `docs-bundles/teastore/`

## What was done (in order)

- `t+0ms`  **POST /tools.descartes.teastore.webui/loginAction?<credentials redacted>**
  - response: HTTP 200 — body: `<response redacted (carries a session token)>`
- `t+38ms`  **POST /tools.descartes.teastore.webui/cartAction?addToCart=&productid=42**
  - response: HTTP 200 — body: `<success-shaped page rendered>`
- `t+111ms`  **POST /tools.descartes.teastore.webui/cartAction?firstname=7535&lastname=Sample&address1=Street1&address2=City1&cardtype=visa&cardnumber=314159265359&expirydate=12%2F2029&confirm=Confirm**
  - response: HTTP 200 — body: `<success-shaped page rendered>`
- `t+120154ms`  observed state via `durable-state check — query the durable order line-items for the key submitted above`: HTTP 200 — `matching durable record present — order line-items: 1 item present`
- `t+120670ms`  observed state via `durable-state check — query the durable parent order record for the key submitted above`: HTTP 200 — `matching durable record present — parent order present`

## Your task

Using ONLY the reference material above, judge whether the observed outcome is a
**genuine defect**, **by-design / benign**, or **underspecified** (the intended
behavior is not derivable from the reference material). Record your answer in
`ballot.yaml`.
