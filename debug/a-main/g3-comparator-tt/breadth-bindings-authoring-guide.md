# Breadth bindings YAML — authoring guide (Rider-2 executable comparator)

Turns the reviewer-accepted `rider2-bindability-survey.md` (80 entries: 69 BIND / 11 NC) into an
**executable** `assertion-bindings-breadth.yaml` the `ComparatorRunner` loads, so a breadth run
measures what fraction of the frozen blind-set state clauses the closed comparator can actually
evaluate on live TrainTicket. Authoring is mechanical given the survey; this guide pins the
disposition→clause mapping so the pass is consistent and reviewable.

## Schema (from `AssertionBindings` + `ContractEvaluator`, and the two frozen exemplars)
```yaml
sut: trainticket
frozen_set: "<one-line provenance>"
endpoints:
  - endpoint: "POST /api/v1/<service>/<...>"      # the write under test
    triple: <triple-name>                          # must exist in the paired target-triples.yaml
    body_template: '{"field":"${supplied:field}"}' # ${supplied:NAME} = runner-provided stimulus
    clauses:
      - cite: "<verbatim/near-verbatim frozen response spec>"
        checks:
          - { primitive: HTTP_STATUS,    expect: "200" }
          - { primitive: ENVELOPE_STATUS, expect: "1" }        # TT success = status==1
          - { primitive: MSG_CONTAINS,   expect: "<success msg substring>" }
          - { primitive: ENVELOPE_DATA,  expect: "non-null" }  # nullity only (optional)
      - cite: "<verbatim/near-verbatim frozen STATE postcondition>"
        checks:
          - primitive: STATE_GET
            path: "/api/v1/<service>/<read-back>/${field:NAME}"  # ${field:NAME} from submitted body
            expect: "<see mapping below>"
            fields: "f1,f2"                                       # comma-sep; matched by name+value
```

## Vocabulary (the ONLY legal values — verified in `ContractEvaluator:123-219`)
- Response primitives: `HTTP_STATUS`, `ENVELOPE_STATUS`, `ENVELOPE_DATA` (nullity only), `MSG_CONTAINS`.
- `STATE_GET` `expect`:
  - `contains-submitted-fields` → **list membership** presence (retried to cap 10s/500ms). Read-back
    must be a COLLECTION (bare array or `{...,data:[]}` — or now HAL `_embedded`, but TT is `data:[]`).
  - `entity-matches-submitted-fields` → **per-entity echo** presence (same retry). Read-back is a
    single-object `{...,data:{...}}`; matches submitted fields on that one entity.
  - anything else (e.g. `absent-submitted-key`) → **ABSENCE** check, SINGLE-SHOT: present→FAIL, absent→PASS.
    Use ONLY on a collection read-back (per-entity single-object absence is VACUOUS — survey rule).
- `NOT_CHECKABLE` with a `reason` — for NC entries and the NC part of BINDS-P.
- `${field:NAME}` in a `path` MUST reference a field present in `body_template`'s submitted body, else
  the loader throws a binding error (fail-fast). `${supplied:NAME}` only appears in `body_template`.

## Disposition → clause mapping (apply per survey row)
| Survey disposition | State clause shape |
|--------------------|--------------------|
| **BINDS** — "list membership" | one STATE_GET `contains-submitted-fields`, path = the list GET, `fields` = the fresh identifying fields (e.g. name+stayTime; startStation+endStation) |
| **BINDS** — "per-entity echo / entity-matches" | STATE_GET `entity-matches-submitted-fields`, path = per-entity GET with `${field:id-name}`, `fields` = echoed fields |
| **BINDS a** — "LIST absence (GET /xs); {kId}→id" | STATE_GET absence (`expect: absent-submitted-key`) on the LIST GET; `fields` = the id under the read-back's field name (note the `{kId}→id` rename the survey records) |
| **BINDS-P** — partial | the catching observable as above **plus** a second clause `NOT_CHECKABLE` for the derived/response-keyed part, reason = survey text (e.g. #13 per-`data.id` response-keyed; #40 derived price) |
| **NC-\*** (OBJECT-ABSENCE, KEY-SHAPE, NESTED-ITEM-SHAPE, TRANSITION, RESPONSE-KEYED, BATCH) | single `NOT_CHECKABLE` clause, reason = survey's per-row justification VERBATIM (these ARE the 11-entry residue; keep the taxonomy tag in the reason) |

Every endpoint ALSO gets its response clause (HTTP_STATUS + ENVELOPE_STATUS + MSG_CONTAINS) — the
response envelope always binds; only the STATE postcondition is the discriminator being measured.

## Source-of-truth for exact paths / bodies / success msgs
- Paths + body schemas: `mist-cli/src/main/resources/My-Example/trainticket/merged_openapi_spec 1.yaml`
  (also `evaluation/suts/trainticket/openapi/merged_openapi_spec.yaml`).
- Success `msg` strings + envelope: the service impls in the fork (train-ticket-injection) — the survey
  already resolved the observables; cross-check the exact `msg` substring per create (e.g. "Success.",
  "Create Account Success") from the controller/impl to keep MSG_CONTAINS faithful.
- `real-system-conf.yaml` enumerates the tested surface (the 80 survey rows map 1:1).

## Runner + pre-registration
- Rider-2 comparator protocol is pre-registered (`g3-rider2-comparator-protocol.md`, commit 3d4891d).
- Run: `mst.comparator.enabled=true` + `mst.comparator.assertions.path=<this yaml>` (MistRunner:590-613,
  COMPARATOR MODE). Needs TT deployed (currently scaled to 0 — redeploy: scale up + port-forward 18888).
  Each endpoint's `triple` must exist in the paired `target-triples.yaml`.
- Output: per-endpoint verdict (PASS/FAIL/NOT_CHECKABLE per clause) → the breadth bindability fraction
  matches the survey's 69/80, now demonstrated EXECUTABLY (not just on-paper). This is the plan;
  authoring + a ≥3-cold-review of the executable YAML is the deliverable.

## Discipline
- Faithfulness: `cite` each clause to the frozen contract; do NOT strengthen a clause to make it bind.
- The 11 NC entries MUST stay NOT_CHECKABLE (they are the honest residue — the whole point). Any BIND
  that required an alias the frozen text does not license is an overclaim → keep NC.
- This is a SEPARATE focused effort (not a wait-window squeeze); author in strata (CRUD families),
  compile-check each stratum against the loader, then one reconciled ≥3-cold-review before any run.
