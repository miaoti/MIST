# Online Boutique home page: body under the `adservice` outage and healthy (2026-10-13)

Purpose: back the paper's sentence that the Online Boutique home page body is a clean 200 with
no error text under the `adservice` outage, so that only the trace exposes the swallowed call.
Until this capture the statement rested on the prose of `../boutique_e2e_pipeline.md`.

Setup: the `boutique` namespace of the kind cluster `mist-control-plane` (the deployment used
for the Boutique runs, kept at zero replicas when idle) was scaled to one replica per
deployment (12 pods, all 2/2 Running within a minute). The frontend service was port-forwarded
to `http://localhost:18080`. `adservice` was scaled to zero and, once its pod was gone, the home
page `GET /` was fetched; `adservice` was then scaled back to one, its rollout awaited, and the
home page fetched again. Afterwards every deployment was scaled back to zero.

| File | State | HTTP | Bytes |
|------|-------|------|-------|
| `home_outage.html` | `adservice` at zero replicas | 200 | 10,499 |
| `home_healthy.html` | `adservice` at one replica | 200 | 10,499 |

Comparison: with the digits stripped, the two pages differ only in the session and request ids
printed in the page footer. The outage page contains none of the words error, unavailable,
failed, exception or sorry. The home page does not render an advertisement in either state
(the markup around the ad slot is the same), so nothing in the body distinguishes the outage.

What the paper takes from this folder: "The home page body is a clean 200 with no error text, so
only the trace exposes the failure" (Section IV, Online Boutique), and the Related Work
statement that the degraded response breaks no schema there. The trace side of that claim is
the earlier evidence (`../boutique_e2e_traces/`, `../boutique_inprocess_e2e/`); this folder
adds the body side.
