# G3 natural-stratum mesh-fault note — @LoadBalanced defeats VS host matching

Deploy-level verification while the depth-code reviews run. Three facts, fork-verified:

1. **The TT k8s Services name their ports `http`** (e.g. ts-inside-payment-service
   port 18673, quickstart-k8s/yamls/svc.yaml:215-221) → Istio L7 treatment is
   available once sidecars are injected. Upstream even ships a
   `deployment/fault-inject-deployment/` (VS + DR) and a `k8s-with-istio` variant.
2. **But inter-service calls use a `@LoadBalanced` RestTemplate**
   (CancelApplication.java:30-32): `http://ts-inside-payment-service/...` is resolved
   by the discovery client to a POD IP + registered port and the URI is rewritten —
   the HTTP authority the sidecar sees is an IP, not the service host.
3. Consequence: a plain **VirtualService (hosts: ts-inside-payment-service) will NOT
   match** the cancel→inside-payment call (the classic Spring-Cloud-client-LB vs
   Istio-routing conflict). Disabling the client LB is not viable either: the URLs are
   portless (:80) while the Services expose app ports (18673) → the SUT would break.

**Primary plan for the route-scoped abort: an `EnvoyFilter` on the
ts-inside-payment-service INBOUND listener** injecting an `envoy.filters.http.fault`
scoped to the `/api/v1/inside_pay_service/inside_payment/drawback` path prefix —
inbound sees all traffic to the pod regardless of authority, so the abort fires for
the LB'd call while `/account` (and every other inside-payment route) stays live.
The abort status follows review-C's finding: **418** (outside both the app's and
Envoy's natural status space; the caller's restTemplate throws on any non-2xx, so the
natural-stratum behavior — drawbackMoney exception → cancel `{1,"error"}` — is
unchanged). A VS variant is kept as fallback ONLY if the live mesh turns out to
preserve service authorities. `IstioRouteFaultInjector` is agnostic (it applies a
committed manifest and probes); the manifest is authored at deploy time against the
installed Istio's inbound listener semantics and live-verified: probe = GET the
incomplete `/drawback` path → 418 when live vs app 404/405 when not, and
`/account` must keep answering while the fault is live.

*Feeds: the natural-stratum deploy step (g3-tt-headtohead-design.md §5 step 4).
The constructed stratum is unaffected (SutFlagFaultInjector + the fork's
fabricated-ack flag f57102e6, no mesh dependency).*
