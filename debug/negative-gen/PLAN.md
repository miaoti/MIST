# Negative-Generation Strengthening — Implementation Plan (2026-06-01)

Scope decided by maintainer: **implement Gap 3 (schema-aware negative values) + Gap 1 (IPD-violation
negatives); mark Gap 2 (stateful negatives) as a documented limitation + future work.**

This plan was produced after a 3-agent code-feasibility gate and a 3-agent web-research pass; every
claim below is anchored to `file:line` or to a cited source. File shorthands:
**HIG** = `mist-core/.../generation/HardcodedInvalidInputGenerator.java`,
**ZSG** = `mist-core/.../generation/ZeroShotLLMGenerator.java`,
**IIP** = `mist-core/.../fault/InvalidInputPool.java`,
**PI** = `mist-core/.../llm/ParameterInfo.java`,
**MG** = `mist-core/.../generation/MistGenerator.java`,
**LSD** = `mist-core/.../coverage/LLMStatusCodeDiscovery.java`,
**YAML** = `mist-core/src/main/resources/mist/fault-types.default.yaml`.

The contribution of MIST is the trace-shape oracle (silent-acceptance / hidden-downstream). Both gaps
here exist to make negatives **reach deeper behaviours the oracle judges** — values that pass field
validation and hit business logic. That framing decides priorities below.

---

## STATUS & DISPOSITION (2026-06-02, post 3-agent review)

**Gap 3 — IMPLEMENTED + VERIFIED.** 3A enum-violation, 3B OVERFLOW-honors-maxLength, 3C
constraint-aware SEMANTIC (safe form). Full mist-core suite green: **308 tests, 0 failures** (incl. new
`EnumViolationTest` 5/5; the `FaultTypeRegistryTest` size lock updated 8→9). Files touched:
`fault-types.default.yaml` (+ENUM_VIOLATION), `HardcodedInvalidInputGenerator` (new
`generateEnumViolationInputs` + helpers; OVERFLOW string defers to BOUNDARY when `maxLength` set),
`ZeroShotLLMGenerator` (smart+llm dispatch via `hc`; SEMANTIC enum-avoid hint + enum-member post-filter;
LLM-path OVERFLOW maxLength guard), `InvalidInputPool` (ENUM_VIOLATION 2nd in rotation),
`FaultTypeRegistryTest` + `FaultMiner` (DEFAULT_IDS + prompt) for the 9th-id sites the reviewer found.

Review dispositions folded in:
- R1 **[BLOCKER]** new id breaks `FaultTypeRegistryTest`/`FaultMiner.DEFAULT_IDS` 8-id sites → **ACCEPTED**, both updated; suite green.
- R1 **[MAJOR/3C]** feeding positive MUST-comply constraints into SEMANTIC can make the LLM emit valid values → **ACCEPTED (PARTIAL)**: did NOT reuse the positive block; instead inverted framing ("values to AVOID") + a `not-is-valid` enum post-filter. Fuller constraint-fed negative prompt (with a full schema-reject filter) left as a deferred follow-on.
- R1 **[MAJOR/3A]** numeric-enum hardening + missing `hc` local → **ACCEPTED**: `size≥2` gap guard via TreeSet, `< Long.MAX_VALUE`/`> Long.MIN_VALUE` overflow guards, values stored as raw `Number`, non-numeric members skipped, `hc` local added to the all-LLM method; test asserts Java type + 1-element enum.
- R1 line nudges (constraints block ZSG:803-834; `applicableLocations` inert through `applies()`) → noted, no action needed.

**Gap 1 — DESCOPED to documented future-work** (joins Gap 2). Maintainer decision 2026-06-02 after two
independent reviewers (mechanics + strategy) **convergently** recommended not building it as planned, on
grep/spec-verified grounds the Phase-① gate had not surfaced:
- **Eval-SUT IPD surface ≈ empty (R3, spec-verified):** `merged_openapi_spec` has **0/239 schemas marking any `required`**, **7/760 body properties with a description (<1%)**, and **0 operations with ≥2 query parameters** (query was the plan's 65% priority). Presence/absence IPDs need required-markers to construct a valid "omit" — absent here. So Gap 1 reproduces Gap 2's "built but won't fire" on the only evaluation SUT.
- **Verification gate not sound on generic-400 controllers (R3):** train-ticket returns blanket 4xx with no field attribution (cf. `TARGET_REJECTION=0`), so a `violate→4xx` cannot be distinguished from an unrelated rejection → the gate can promote a hallucinated IPD whose `violate→2xx` is then reported as a **fabricated SILENT-ACCEPTANCE bug**, contaminating the paper's headline class. Closing it needs a discriminating control probe (heavier).
- **Two BLOCKER mechanics (R2, grep-verified):** (B2) omit/duplicate IPD violations add nothing to `getFaultyParameters()`, so the `MistGenerator:789-813` guard demotes them to positives pre-emission and the writer's negative assertion (writer:2208, keyed on `getFaultyParameters()`) never fires — i.e. "reuse, no new oracle code" is false. (B4) the IpdVerifier cannot "wire next to StatusCodeExplorationEnhancer" — neither the runner nor the enhancer executes HTTP; the only live-SUT path is emit→compile→run JUnit, so the verifier needs a full JUnit-probe pass.
- **Contribution-fit (R3, STRATEGIC):** the contribution is the ORACLE; the silent-acceptance detector is structurally indifferent to *how* a request became invalid, so "IPD violations reach deeper behaviour" is unmeasured and the MVP defers exactly the Relational/Arithmetic types that would make that claim land. Better marginal investment: Gap 3 (done) + oracle precision/recall + prevalence evidence.

The full Gap-1 design below is **retained as the future-work record** (LLM IPD inference mirroring
`LLMStatusCodeDiscovery`, two-sided SUT verification, per-type violation recipes). To revive it the only
defensible path is: adopt a SUT with real declared IPDs (search/geo/payment per the IDL catalogue), gate
go/no-go on a pre-flight inference+verify count (≥~8 confirmed), add a discriminating-control probe to the
gate, and include Relational in the MVP.

---

## GAP 3 — Schema-aware negative values   (verdict: FIXABLE, data all in-scope)

Research basis: Schemathesis `--mode=negative` (`change_type`/`negate_constraints`/`remove_required` +
the load-bearing `not validator.is_valid(value)` post-filter); RESTGPT (+36% input relevance when the
LLM is fed enum/format/bounds vs name+type+desc); ISTQB BVA off-by-one. The constraint data
(enum/format/example/min-max) is already on every `ParameterInfo` (PI:18-22, getters PI:53-62,
predicates `hasEnum`/`hasBounds`/`hasLengthConstraints` PI:75-87) and the SAME object reaches every
negative generator — so this is in-method work, **zero cross-layer threading**.

Three sub-fixes, ordered by value × certainty.

### 3A — ENUM_VIOLATION fault type  (NEW; highest value, deterministic, no LLM)

**Why:** there is no "valid-type/format-but-not-in-enum" negative anywhere in the tree (confirmed:
enum only read in `buildCacheKey` ZSG:736 and the positive prompt ZSG:809). Enum params are exactly
where servers under-validate → accept an out-of-enum value with 2xx = a silent-acceptance bug the
oracle can catch. This is the cheapest negative that *passes the type check and reaches enum
validation*.

**Changes:**
1. **YAML** (after BOUNDARY_VIOLATION, YAML:55-59): add
   ```yaml
     - id: ENUM_VIOLATION
       displayName: Enum Violation
       applicableTo: [string, integer, number]
       applicableLocations: [path, query, header, cookie, body]
       source: DEFAULT
   ```
   The `applies()` gate (HIG:35-37, FaultTypeRegistry) reads this YAML; without the entry the dispatch
   line is a no-op. Mirror the change into `target/classes/.../fault-types.default.yaml`? No — `target`
   is build output; `mvn` regenerates it. Only edit `src/main/resources`.
2. **HIG**: add `generateEnumViolationInputs(ParameterInfo param, InvalidInputPool pool)` (package-
   private, near BOUNDARY at HIG:357). Logic:
   ```
   if (!param.hasEnum()) return;                  // only meaningful with a declared enum
   List<String> e = param.getEnumValues();
   String t = safeStr(param.getType()).toLowerCase();
   switch (t) {
     case integer/int/long/number/double/float:
        parse members to numbers; emit max(members)+1  (and min(members)-1 if distinct);
        if a gap exists between two sorted members, emit a gap value;
        skip any candidate that parses back into the member set.
     default (string):
        emit a fresh non-member: "__not_in_enum__", and (firstMember + "_x"), and a UUID;
        each guarded by  !e.contains(candidate).
   }
   ```
   Members must stay the right **type** (don't emit a string for an int enum — that's TYPE_MISMATCH's
   job), so the value still passes the type check and only the enum check rejects it. Post-check
   membership before every `addValue` (mirror Schemathesis `not is_valid`).
3. **Dispatch** — add the gated call in all three pool builders:
   - HIG hardcode path HIG:56-63 → `if (applies("ENUM_VIOLATION", paramType)) generateEnumViolationInputs(param, pool);`
   - ZSG smart path ZSG:252-253 → `if (HardcodedInvalidInputGenerator.applies("ENUM_VIOLATION", paramType)) hc.generateEnumViolationInputs(param, pool);` (deterministic — route through `hc`, NOT the LLM)
   - ZSG llm path ZSG:290-291 → same as smart (enum violation never needs the LLM)
   → requires making `generateEnumViolationInputs` package-private (like the other `hc.*` reuse methods).
4. **IIP** PRIORITIZED_TYPE_ORDER (IIP:33-42): insert `"ENUM_VIOLATION"` second, right after
   BOUNDARY_VIOLATION — it is high-signal and deterministic, should fire early. (Without this, `addValue`
   still auto-appends it to the rotation at IIP:64-66, but last = low priority; explicit placement is
   the intent.)

**Verify:** new JUnit `EnumViolationTest` in `mist-core/src/test/java/.../generation/`:
(a) string enum `["A","B","C"]` → pool's ENUM_VIOLATION values all ∉ enum and are Strings;
(b) integer enum `[1,2,5]` → contains `6` (max+1) and a gap (`3` or `4`), none ∈ {1,2,5};
(c) no enum → `generateEnumViolationInputs` adds nothing (`getCountForType("ENUM_VIOLATION")==0`);
(d) registry `applies("ENUM_VIOLATION","boolean")==false` (not in applicableTo).

### 3B — OVERFLOW honors maxLength  (small; dedup vs BOUNDARY)

**Why:** `generateOverflowInputs` (HIG:169-222) string case emits fixed 1000/5000/10000-char strings
and **never reads `maxLength`**; BOUNDARY already owns the precise `maxLength+1` off-by-one (HIG:416-417).
When `maxLength` is declared, the giant strings are a redundant, coarser "too long" — Schemathesis
discipline = one mutation that actually negates, no dupes.

**Change (HIG:174-181, string case):**
```
case "string":
  Integer maxLen = param.getMaxLength();
  if (maxLen != null) {
     // BOUNDARY owns the bounded off-by-one (maxLen+1). Defer to avoid a duplicate
     // "too-long" value; nothing to add here.
     break;
  }
  // Unbounded: BVA has no neighbour, so a large sentinel is the right stand-in.
  pool.addValue("OVERFLOW", "A".repeat(1000));
  pool.addValue("OVERFLOW", "X".repeat(5000));
  pool.addValue("OVERFLOW", "Z".repeat(10000));
  pool.addValue("OVERFLOW", "Lorem ipsum dolor sit amet ".repeat(100));
  break;
```
Leave the numeric/array OVERFLOW cases unchanged (they target type-range overflow, orthogonal to
maxLength). The LLM-path twin (ZSG:573-610, only used in `mode=llm` ablation) gets the same guard for
consistency but is lower priority — note it, do it if cheap.

**Verify:** extend BVA test — param with `maxLength=50`: OVERFLOW contributes 0 string values, BOUNDARY
contributes the 51-char `maxLength+1`; param with no length bound: OVERFLOW contributes the 4 sentinels.
Assert no value appears in both OVERFLOW and BOUNDARY lists (dedup).

### 3C — Feed constraints into the negative LLM prompts + post-filter  (M; probabilistic)

**Why:** in default smart mode REGEX + SEMANTIC go to the LLM (ZSG:256-259). Their prompts carry only
name/type/description (SEMANTIC ZSG:528-553; REGEX already feeds `pattern` ZSG:509). The positive
`[Constraints]` block (enum/bounds/length/regex, ZSG:806-835) is never reused for negatives. RESTGPT
shows +36% relevance when the model is given those fields.

**Changes (ZSG):**
1. Extract the `[Constraints]` rendering (ZSG:806-835) into a private helper
   `appendConstraints(StringBuilder, ParameterInfo)` and call it from `buildPrompt` (no behaviour
   change there) AND append it to the SEMANTIC prompt (ZSG:528-553) and the TYPE/OVERFLOW/SPECIAL/
   BOUNDARY LLM prompts (only material in `mode=llm`). Primary target = SEMANTIC (the only context-
   dependent one live in default mode besides REGEX).
2. **Post-filter** (the load-bearing Schemathesis mechanism). After parsing each LLM negative value,
   drop it when it does **not** actually violate:
   - if `param.hasEnum()` and value ∈ enum → drop (it's valid).
   - SEMANTIC stays "format-valid but meaningless", so do NOT schema-reject on format — only the enum
     check applies. (A full `not is_valid` gate belongs to a future LLM-negative mode; note it, don't
     build it now.)
   REGEX already has its post-filter (`!value.matches` ZSG:519) — keep.

**Verify:** unit test with a stubbed `LLMService` (the constructor takes properties; inject a fake via
the existing `LLMService.getInstance` seam or a small refactor to allow a test double): assert the
SEMANTIC prompt string contains `Allowed Values (Enum)` when an enum is present, and that an enum-member
returned by the stub is filtered out of the pool.

**Gap-3 risk register:** (a) `applies()` depends on the YAML being on the classpath at test time — the
registry loads `loadDefault()` (HIG:28); confirm the test resource resolves. (b) ENUM_VIOLATION on a
`path` enum: empty/odd path segments route oddly (see HIG:239-244 path note) — but a non-member enum
value is a normal segment, so fine. (c) numeric enum parsing must tolerate non-numeric junk in a mis-
typed enum list (try/catch, skip).

---

## GAP 1 — IPD-violation negatives   [DESCOPED → FUTURE WORK; see STATUS above]

> Retained below as the design/future-work record. Not implemented — see the STATUS & DISPOSITION
> section for the convergent-review reasons (empty eval-SUT IPD surface; gate unsound on generic-400
> controllers; two BLOCKER mechanics; contribution-fit).

This is a feature, not a patch. The "dead `x-dependencies` hook" (OpenAPISpecificationVisitor:218) is a
red herring: **0 IPDs are declared in any of the 5 real specs** (train-ticket 11,976 lines incl.), and
RESTest's IDL engine was deliberately removed when vendoring (TestCase.java:34-40). So the IPD source
must be **LLM-inferred and then SUT-verified**. Research basis: IDL catalogue (Martín-López ICSOC'19 —
7 types, 85% of APIs have IPDs, Requires=35%); RESTGPT/NLP2REST (per-op LLM extraction; **~50% raw
precision on terse specs**, not RESTGPT's 97%); the verification gate is the load-bearing safety
mechanism (NLP2REST 50%→79% with live validation; ours is strictly stronger — two-sided).

### Architecture — 4 components + a config gate

```
 (opt-in  mst.ipd.enabled=true,  default false)

 ① IpdInferenceDiscovery   per-op LLM → JSON IPD candidates → disk cache   [NEW, mirrors LSD]
        │
 ② IpdVerifier             for each candidate: satisfy-probe + violate-probe at live SUT;
        │                  promote only if satisfy→2xx ∧ violate→4xx                  [NEW]
        ▼  (confirmed IPDs only)
 ③ IPD-violation generation  emit one violating request per confirmed IPD            [MG hook]
        ▼
 ④ Oracle/expected-status  setFaulty(true); violation→2xx = SILENT ACCEPTANCE (the bug)  [reuse]
```

### ① IpdInferenceDiscovery  (NEW class, mirror LSD almost 1:1)

New `mist-core/.../coverage/IpdInferenceDiscovery.java`, structurally identical to LSD:
- ctor `(LLMService)` + `(LLMService, Path)` test seam (LSD:67-88); disk cache gated by `CacheToggle`
  (LSD:84,129,159); atomic temp+rename save (LSD:511-537).
- **signature cache key** = `buildSignatureCacheKey(method, path, params)` reused verbatim (LSD:455-469)
  — IPDs are an endpoint property, same key shape.
- `inferDependencies(service, method, path, List<ParameterInfo> params)` → `List<IpdCandidate>`:
  one LLM call per operation (NOT per param — IPDs are cross-parameter, invisible from one param).
  Prompt = the few-shot template from the research recipe: list the 7 IDL forms, feed the FULL param
  list (name+type+required+description+enum), instruct "extract ONLY explicitly-stated/unambiguous
  dependencies; output `[]` if none; quote the justifying phrase". `temperature=0.3`, `maxTokens≈1500`
  (LSD uses 2000@0.3 for status codes — same ballpark). Parse JSON array → IpdCandidate (mirror
  `parseDiscoveryResponse` LSD:280-310).
- **`IpdCandidate`** (new small value class):
  ```
  type   ∈ {REQUIRES, OR, ONLY_ONE, ALL_OR_NONE, ZERO_OR_ONE, RELATIONAL, ARITHMETIC}
  params : ordered List<Participant{ name; location∈{QUERY,BODY,HEADER,PATH}; valueCond? }>
           (REQUIRES: [0]=antecedent, [1]=consequent)
  relOp? ∈ {LT,LE,GT,GE,EQ,NE}            // RELATIONAL/ARITHMETIC: the operator to NEGATE
  constant? ; arithExpr?                  // ARITHMETIC RHS / expression
  idl : the canonical IDL text (for logging / interop / paper artifact)
  evidence : the quoted justifying phrase
  ```
  COMPLEX deferred (4% of deps; recurse later).

### ② IpdVerifier  (NEW — the non-negotiable false-positive gate)

For each `IpdCandidate`, build two requests against a known-good baseline (all other params at SUT-
verified values — reuse the two-phase verified pool, MG:300 `preferVerifiedValues`, which already
exists for exactly this "values the SUT accepts" need):
- **satisfy-probe**: a request that satisfies the IPD → expect 2xx.
- **violate-probe**: the same baseline, changing *exactly one IPD-relevant thing* (per §"violation
  recipe" below) → expect 4xx.
- promote to **CONFIRMED** only if `satisfy→2xx ∧ violate→4xx`.
- `violate→2xx` ⇒ **hallucinated**, discard (this is the fabricated-bug case we must avoid).
- `satisfy→4xx` (or any non-IPD error) ⇒ **inconclusive**, discard.

Where it runs: a new opt-in probe phase, wired in **MistRunner** next to the existing
`StatusCodeExplorationEnhancer` construction (the precedent for live mid-pipeline HTTP). Reuses the
runner's HTTP client + base URL resolution. Cache the verdict on the IpdCandidate's disk entry
(`status: confirmed|refuted|inconclusive` + the two probe statuses) keyed by (op-signature + specHash)
so reruns skip both the LLM and the probes. Guards from research: change exactly one thing; require the
satisfy baseline to pass before trusting a 4xx; fresh fixtures / prefer read-only ops for write ops
(parse error body for the param name when present — LlamaRestTest signal — optional v1).

### ③ IPD-violation generation  (MG hook)

The existing Sniper model is one `FaultTarget=(rootIndex,paramName,paramLocation,type,value)`
(MG:108-132) — a single value swap. An IPD violation is a **structural** change (omit / duplicate /
relational pair), so it needs a sibling representation, NOT a forced fit into the single-value target.

**Per-type violation construction** (closed-form, NO constraint solver needed — confirmed by research;
RESTest needs MiniZinc only for global consistency/sampling, which we don't):

| Type | Violation (every individual value stays valid) |
|------|-----------------------------------------------|
| Requires A→B | include A (or set A's valueCond), **omit B** |
| Or(set) | **omit all** of the set |
| OnlyOne(set) | include **≥2** (also the include-none shape) |
| AllOrNone(set) | include a **proper non-empty subset** (one present, one absent) |
| ZeroOrOne(set) | include **≥2** |
| Relational A<B | valid values with the **negated** op (A≥B) |
| Arithmetic f(..)⊙k | valid per-param values that break the **aggregate** (e.g. A+B=k+1) |

Mechanically, "omit a field" / "include both" is reachable in MG's body assembly: the body is built
field-by-field via `bodyFields.put(name, value)` (MG:1467) → `generateRequestBody(bodyFields, opCfg)`
(MG:1490). So a body-IPD violation = **don't `put` B (Requires)** or **`put` both A and B with valid
values (OnlyOne/ZeroOrOne)** — the writer needs NO change (it emits the prebuilt body string). For
query/header IPDs the writer's "at most ONE replacement" Sniper invariant
(MultiServiceRESTAssuredWriter ~:1947-1965) must be relaxed for the IPD path (or the IPD variant routes
through a dedicated emit). v1 priority = **body + query** (65% of real IPDs are query, 34% body).

**Hook design (minimal-blast-radius option, to be confirmed in review):** add a parallel
`List<IpdViolationTarget>` alongside the value-level `faultQueue` in `generateScenarioVariants`
(MG:697-774). When `v` falls in the IPD band, instead of a single-value Sniper swap the traversal
applies the structural action to the target root's field map. The variant is still `tc.setFaulty(true)`
with a new `setFaultTypeCategory("IPD_" + type)` so all downstream marker/report/oracle machinery treats
it as a negative expecting rejection — no new oracle code. Naming: `test_negative_flow_S.._ipd_<TYPE>`.

### ④ Oracle / expected-status  (reuse — no new oracle code)

IPD negatives reuse the existing negative path: `setFaulty(true)` → writer asserts "expect rejection";
expected behaviour = 4xx (Martín-López RESTest convention: faulty ⇒ 4xx). The bug classes, already
distinguishable:
- violation → **2xx** = SILENT ACCEPTANCE (primary; the oracle/marker flags it) ← the A-main story.
- violation → **5xx** = robustness defect (flag separately, don't collapse with clean 4xx).
- violation → **4xx** = correct rejection (the test passes; baseline behaviour).

### Gap-1 MVP boundary (recommend for first implementation)

- Types: **Requires, OnlyOne, AllOrNone, ZeroOrOne** (presence/absence; closed-form; ~76% of deps).
  Relational/Arithmetic (value reasoning) = phase 2; Complex = deferred.
- Locations: **body + query**. header/cookie/path = phase 2.
- Inference + verification + generation all behind `mst.ipd.enabled=false` (opt-in, like two-phase).
- Honesty: log how many IPDs were inferred, confirmed, refuted, inconclusive — no silent caps.

### Gap-1 verification

1. Unit: IpdCandidate JSON round-trip; per-type violation construction (given a candidate + a baseline
   field map, the produced request omits/duplicates the right fields) — pure, no LLM/SUT.
2. Unit: IpdVerifier verdict logic with stubbed HTTP (satisfy/violate status pairs → confirmed/refuted/
   inconclusive truth table).
3. Integration (opt-in, live SUT): on train-ticket, run inference on a handful of ops with plausible
   IPDs (e.g. a search endpoint with from/to/date), confirm the gate promotes only SUT-validated ones,
   and that a confirmed IPD's violating request is generated and executed. Capture counts.
4. Guard: with `mst.ipd.enabled=false` (default), generation is byte-identical to today (no IPD targets
   in the queue) — protects the existing pipeline.

### Gap-1 risk register

- **False positives** are the make-or-break: inferred IPDs are ~50% wrong on terse specs → the verifier
  gate is mandatory; never generate from an unconfirmed IPD.
- **Empty yield on train-ticket**: its controllers have terse/empty param descriptions, so the LLM may
  infer few IPDs and the verifier may confirm fewer. That is an honest, reportable outcome (and aligns
  with the catalogue: IPDs cluster in search/geo/payment APIs). Mitigation: also demo on an op known to
  have an IPD; report yield per SUT.
- **Writer Sniper-invariant** relaxation for query IPDs must not leak into value-level negatives (keep
  the IPD emit on a separate branch).
- **Verifier pollutes SUT state** for write ops (same caveat as two-phase Phase A) — prefer read-only
  ops; fresh fixtures otherwise.

---

## GAP 2 — Stateful / sequence negatives   (MARKED: documented limitation + future work)

Not implemented this round (maintainer decision). The honest, citable limitation note for the paper:

- The generated-test **format already supports** ordered stateful sequences (multi-step
  `stepParams<N>`, runtime ID-threading `capturedOutputs`+JsonPath, per-step `expectedStatus` —
  MultiServiceRESTAssuredWriter:2026-2051,2314). So emission is not the blocker.
- The blocker is generation-side: the fault model is value-only (`FaultTarget` has no sequence field,
  MG:108-132), there is **no resource-lifecycle inference** (HTTP method is present at MG:1593 but never
  mapped to create/delete roles; no operation-dependency graph), and negatives always expect success.
- **Decisive for the current evaluation:** on the real train-ticket corpus **124/124 scenarios are
  single-step** (OTel traces lack response bodies for data-dependency merge and often lack
  `http.client_ip` for session merge — TraceWorkflowExtractor:514). So even if built, stateful negatives
  **would not fire** on the eval SUT without richer trace collection or a different SUT. Building it now
  would be effort with no evaluation evidence — hence deferred.
- Future work: lifecycle tagging (POST=create/DELETE=delete) + a sequence-fault target type +
  downstream error-expectation + a SUT whose traces contain create→delete→reuse chains.

Cite: RESTler (ICSE'19), Morest (ISSTA'22), DeepREST (ASE'24) for stateful/sequence negatives.

---

## Cross-cutting verification & guardrails

- Build/test per memory gotcha: verify via `mvn test -pl mist-core -am` (NOT `mvn package`, which trips
  on multi-GB untracked generated train-ticket tests). Gap-3 changes are all mist-core; Gap-1 inference/
  verify are mist-core + a MistRunner (mist-cli) wiring point.
- No new default behaviour: ENUM_VIOLATION adds values only when an enum is declared; OVERFLOW change is
  a strict subset/relocation; IPD is fully behind an opt-in flag. Existing runs stay stable.
- Commit only source + this plan + tests; never the train-ticket run artifacts (per standing rule).
- No AI authorship attribution anywhere.

## Open questions for the plan reviewers

1. Gap-3: is deferring OVERFLOW-string entirely when `maxLength` present correct, or keep one mid-size
   overflow even when bounded (robustness vs dedup)?
2. Gap-1: the parallel-queue vs extend-FaultTarget decision for IPD targets — which has smaller blast
   radius and keeps the value-level Sniper path untouched?
3. Gap-1: where exactly should the IpdVerifier probe phase be wired in MistRunner relative to two-phase
   Phase A (which already harvests verified values the verifier needs)?
4. Gap-1: is body+query+4-types a defensible MVP for the paper, or is Relational/Arithmetic needed to
   make the "reaches deep behaviour" claim land?
5. Is the enum-violation membership post-check + numeric-enum gap construction robust to mistyped enum
   lists (strings in an int enum, etc.)?
