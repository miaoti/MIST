# Adaptive Per-Endpoint Strategy — Research & Design Proposal

Status: draft / proposal. Scope: this round focuses on adaptive **dedup** and adaptive **K_DEDUP_EXHAUSTED**; the same framework extends to K_ZERO_STEP, auth-refresh age, and variant count.

## 1. Problem statement — what's rigid today

All five knobs below are fixed at compile time or as a single global property, with no per-endpoint signal:

| Knob | Location | Value | Failure mode |
|---|---|---|---|
| Payload-fingerprint dedup | `MistGenerator.generateScenarioVariants` `mist-core/.../generation/MistGenerator.java:476, 703-704` | always-on `seenPayloads.add(fp)` | Stateful endpoints (DELETE same id twice: 200 then 404) collapse to one test; second-call faults are invisible. |
| `K_DEDUP_EXHAUSTED` | same file `:562-563, 734` | hard `10` | Small parameter spaces (single-step DELETE `/admintravel/{tripId}`) abort early; the comment at `:567-572` already documents the symptom. Remaining negative slots are dropped. |
| `K_ZERO_STEP` | same file `:562, 692` | hard `3` | Scenarios with sparse-but-real coverage are killed before reaching their reachable variants. |
| Auth refresh age | `mist-cli/.../auth/MstAuthRefreshFilter.java:88-91` | hard `5_000_000_000L` ns | One value across all auth backends — Keycloak vs Spring-Security have very different revocation latencies. |
| Variant count | `MstConfig` `Faulty.ratio` plus a global `variantCount` derived at `MistGenerator.java:519` | one number per run | Same budget for a CRUD POST as for a read-only GET. |

Net effect: coverage is lost on exactly the endpoints we care about — stateful writes (`POST`/`PUT`/`PATCH`/`DELETE`) and endpoints with small input domains.

## 2. Background — literature scan

| Tool | Idempotency / state handling | Dedup policy |
|---|---|---|
| **RESTler** (Atlidakis et al., ICSE 2019) | Builds a stateful sequence graph; tracks producer→consumer dependencies; never dedups *across* sequences — same operation can appear N times with different prefixes. | Dedup keyed on **(request signature × prior-sequence prefix)**, not on payload alone. |
| **EvoMaster** (Arcuri, TOSEM 2019; ASE 2021 white-box ext.) | Splits ops by `safe`/`idempotent`/`unsafe` (RFC 7231 §4.2). Test minimisation removes only payload-equivalent positives; negatives kept regardless. | Heuristic + branch-distance feedback; never collapses two failing tests with different statuses. |
| **Schemathesis** (Hatfield-Dodds, ICSE-SEIP 2022) | `--stateful=links` walks OpenAPI links; treats each link traversal as fresh state. | Hypothesis-style shrinking dedups by **failure class**, not request body. |
| **ARAT-RL** (Kim et al., ISSTA 2023) | Reinforcement-learning over `(operation, parameter)` pairs; Q-table rewards distinct *response* signatures, not distinct requests. | Implicit — duplicate requests are punished by the reward but not removed. |
| **Morest / NLPtoREST** | LLM-derived producer/consumer graphs; treats DELETE+GET pairs as oracle anchors (the GET *should* 404). | Per-edge dedup, never per-payload. |
| **HAR-RestTest / FuzzTheREST** (Pereira & Macedo 2024) | Multi-armed bandit over mutation operators per endpoint. | Operator-level diversity, no payload dedup. |

**Common pattern:** every modern adaptive REST tool keys dedup on something richer than the request payload — usually `(request, observed-response-class)` or `(request, state-prefix)`. RFC 7231 §4.2.2 (idempotency) is the canonical first cut for stateful-vs-stateless.

## 3. What's already adaptive in MIST

So we don't reinvent:

- **Thompson sampling over fault targets** — `mist-core/.../bandit/ThompsonScheduler.java` ranks `(endpoint, parameter, faultType)` triples by Beta(α,β) posteriors (the bandit-based adaptation referenced in the brief). Already plumbed into `FaultMiner` and `MistGenerator`'s queue.
- **OpenAPI metadata access** — `mist-core/.../spec/OpenAPISpecification.java:98-110` (`getAllOperations()`) exposes `(path, method, Operation)` triples; `mist-core/.../smart/OpenAPIEndpointDiscovery.java` already walks the spec.
- **Live SUT probing infrastructure** — `mist-core/.../fault/FaultMiner.java` reads `List<ObservedResponse>` (`ObservedResponse.java:14`: `apiKey, statusCode, responseBody`), so the runtime already has a typed channel for "we sent something, here's what came back".
- **Smart input fetching** — `mist-core/.../smart/SmartInputFetchConfig.java:83` already does HTTP discovery with a 5s timeout; reusable for an idempotency probe.
- **Config split for knobs** — `mist-core/.../config/MstConfig.java:46` is the central registry; adding a new nested section `Adaptive` is the natural home.

Nothing per-endpoint exists today; the bandit is over fault targets, not over generation policies.

## 4. Concrete proposal — adaptive dedup + adaptive K_DEDUP_EXHAUSTED

### 4.1 Per-endpoint decision: `EndpointPolicy`

A frozen record produced once per `(WorkflowScenario, root step)` before the variant loop at `MistGenerator.java:565`:

```java
public record EndpointPolicy(
        String apiKey,            // "DELETE /admintravel/{tripId}"
        DedupMode dedupMode,      // PAYLOAD | RESPONSE_AWARE | OFF
        int kDedupExhausted,      // default 10; raise on small-space endpoints
        int kZeroStep,            // default 3
        int variantBudget,        // may be uplifted/cut from global
        Source source             // SPEC | PROBE | DEFAULT
) {}

public enum DedupMode { PAYLOAD, RESPONSE_AWARE, OFF }
```

`RESPONSE_AWARE` is the RESTler/EvoMaster move: the fingerprint becomes `hash(payload ++ statusClass(prevResponse))` once the first execution lands, so DELETE-then-DELETE keeps both because the second one's `prev` is 200 while the first's is `null`.

### 4.2 Decision rule (layered, fail-safe)

Each layer overrides the next. All return the same `EndpointPolicy`.

1. **RFC 7231 layer (free, runs offline):**
   - `GET`, `HEAD`, `OPTIONS` → `dedupMode=PAYLOAD`, `kDedupExhausted=10` (today's default; safe).
   - `PUT`, `DELETE` (RFC-idempotent but **state-mutating**) → `dedupMode=RESPONSE_AWARE`, `kDedupExhausted=max(10, 2 * paramDomainSize)`.
   - `POST`, `PATCH` → `dedupMode=OFF` (each call is a distinct state transition by spec).
2. **OpenAPI-hint layer:** if the operation declares `x-mist-stateful: true` (or its `responses` list includes both 2xx and 404/409), bump to `RESPONSE_AWARE` even on `GET` (collection endpoints that return different bodies after writes).
3. **Param-space layer:** estimate `|domain|` from enums + boolean + path-id pools (`InputFetchRegistry` already knows the pool sizes). If `|domain| < variantCount`, set `kDedupExhausted = ceil(1.5 * |domain|)` so we don't abort on a fundamentally small space.
4. **Probe layer (optional, gated by `mst.adaptive.probe.enabled`):** before generation, send the *positive* form of the request twice and compare:
   - Same status + body fingerprint → endpoint behaves idempotent for this input class → `PAYLOAD`.
   - Different status (e.g. 201 then 409, or 200 then 404) → `RESPONSE_AWARE`.
   - Both 5xx → abstain, fall through to layer 1.
   Reuses `SmartInputFetchConfig.discoveryTimeoutMs` and the auth filter chain; one HTTP round trip per endpoint, cached for the run.

### 4.3 Wire format & rendering

```
ENDPOINT POLICY ┃ DELETE /admintravel/{tripId}
  dedup        : RESPONSE_AWARE        (source: RFC7231 + probe)
  k-dedup-exh  : 14                    (param-domain |D|=9)
  k-zero-step  : 3                     (default)
  variant-budg : 20                    (global)
```

Persisted alongside the existing `.mist/mined-fault-types.yaml` as `.mist/endpoint-policies.yaml` for reproducibility.

## 5. Implementation steps

| # | Touch | What |
|---|---|---|
| 1 | new `mist-core/.../adaptive/EndpointPolicy.java` | The record + `DedupMode` enum above. |
| 2 | new `mist-core/.../adaptive/EndpointPolicyResolver.java` | Pure function `resolve(WorkflowStep root, OpenAPISpecification spec, InputFetchRegistry reg, ProbeClient probe) -> EndpointPolicy`. Layered as in 4.2. |
| 3 | new `mist-core/.../adaptive/IdempotencyProbe.java` | Thin client that issues 2x positive requests; reuses `MstAuthRefreshFilter` so auth still works. Surfaces `Optional<DedupMode>` (empty = abstain). |
| 4 | `MstConfig.java:46` add `Adaptive` nested record: `probe.enabled` (default false), `policies.path` (default `.mist/endpoint-policies.yaml`), `kZeroStep.default=3`, `kDedupExhausted.default=10`. Keep current values as defaults so nothing regresses when adaptive is off. |
| 5 | `MistGenerator.java:560-565` — replace the four hard-coded ints with `EndpointPolicy policy = resolver.resolve(...);` then `int K_ZERO_STEP = policy.kZeroStep(); int K_DEDUP_EXHAUSTED = policy.kDedupExhausted();` |
| 6 | `MistGenerator.java:703-704` — branch on `policy.dedupMode()`: `PAYLOAD` keeps today's `seenPayloads.add(fp)`; `RESPONSE_AWARE` keys on `fp + observedStatusClassFromPrevRun`; `OFF` skips the add entirely. |
| 7 | `MstAuthRefreshFilter.java:88-91` — read the 5s threshold from `MstConfig.adaptive().auth().minTokenAgeNs()`; same compile-time default, but per-run override possible. |
| 8 | tests in `mist-core/src/test/java/io/mist/core/adaptive/` — see §6. |

No public API change outside `MstConfig` and one new package; the variant loop's shape stays identical (only the constants are now per-scenario).

## 6. Test plan — verify 10/10 doesn't regress

| Test | Setup | Assert |
|---|---|---|
| `EndpointPolicyResolverTest#rfc7231Defaults` | `GET /foo` operation, no probe | `dedupMode=PAYLOAD`, `kDedupExhausted=10` (matches today). |
| `…#postIsAlwaysOffDedup` | `POST /orders` | `dedupMode=OFF`. |
| `…#smallDomainRaisesK` | `DELETE /trip/{id}` with `InputFetchRegistry` pool size 4 | `kDedupExhausted >= 6` (was 10 in absolute terms — still fine, must not be *smaller*). |
| `MistGeneratorAdaptiveTest#legacyModeUnchanged` | `mst.adaptive.enabled=false` | byte-for-byte identical variants to current `main` for the Train-Ticket fixture. **This is the 10/10 regression gate.** |
| `…#statefulDeleteKeepsBothCalls` | DELETE same id twice; probe layer returns `RESPONSE_AWARE` | both variants survive dedup (today only the first survives). |
| `IdempotencyProbeTest#networkErrorAbstains` | mock 5xx | resolver falls through to RFC layer (does not crash). |
| `MstAuthRefreshFilterTest#thresholdFromConfig` | set `mst.adaptive.auth.min-token-age-ms=10000` | filter waits 10s instead of 5s; existing 5s tests still pass when override is absent. |

Acceptance: existing CI green with `mst.adaptive.enabled=false` (default), plus the seven tests above.

## 7. Out of scope (next round)

- Adaptive `variantCount` per endpoint (UCB or Thompson over "more variants here found more faults"). The bandit infra already exists; only needs a per-endpoint key.
- Adaptive `K_ZERO_STEP` — same shape as `K_DEDUP_EXHAUSTED`, mechanically trivial once §4 lands.
- Cross-run learning: persist `EndpointPolicy.source=PROBE` decisions for warm start.

## References

- Atlidakis, Godefroid, Polishchuk. *RESTler: stateful REST API fuzzing.* ICSE 2019.
- Arcuri. *RESTful API automated test case generation with EvoMaster.* TOSEM 2019.
- Hatfield-Dodds et al. *Schemathesis: property-based testing for OpenAPI.* ICSE-SEIP 2022.
- Kim, Xiao, Sinha, Orso. *ARAT-RL: reinforcement learning for REST API testing.* ISSTA 2023.
- RFC 7231 §4.2 — *Hypertext Transfer Protocol (HTTP/1.1): Semantics and Content* — idempotent vs safe methods.
- Pereira, Macedo. *FuzzTheREST: bandit-driven REST fuzzing.* 2024.
