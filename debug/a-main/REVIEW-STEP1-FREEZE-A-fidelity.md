# REVIEW — Step-1 Freeze Wave (charge A: FIDELITY + SOUNDNESS)

**VERDICT: ACCEPT-WITH-CHANGES** — the freeze is well-constructed and the depth survey's two load-bearing
SOURCE claims (TeaStore natural swallow; OTel-Demo async Kafka swallow) both VERIFY against upstream. But
one **BLOCKING** defect must be resolved before any population begins: the frozen `fault.mechanism` enum
cannot express the mechanisms the NORMATIVE survey uses to clear the ≥4-mechanism write-path floor, so the
write-path classification (TeaStore in particular) does not follow from the floor as frozen, and several
proposed S1 cases would be schema-invalid. Plus eight MAJOR gaps in scoring machine-checkability, rubric
fidelity, and blindness.

Reviewer stance: independent cold read of the five freeze docs + verification of the cited upstream source
via WebFetch. Line cites are to the files as read on 2026-07-08.

---

## [BLOCKING]

### B1 — The frozen `fault.mechanism` enum cannot express the survey's mechanisms; the ≥4-mechanism write-path floor and the TeaStore write-path classification do not follow as frozen; several normative S1 cases are schema-invalid.
`c2-freeze.md:58` (enum), `c2-freeze.md:184` (floor), `c2-depth-survey.md:145-146` (survey mechanism lists),
`e-sut-applicability-matrix.md:13,21-25,37` (classification), `c2c3-execution-plan.md:97-98` (plan floor).

**The frozen enum** (`c2-freeze.md:58`) is `mechanism: flag | mesh-sever | broker-policy | code-level | none`
— exactly four real mechanisms. **The frozen floor** (`c2-freeze.md:184`) is "S1 ≥ 4 distinct fault
MECHANISMS per write-path SUT." Read together, a write-path SUT must instantiate S1 cases spanning **all
four** enum mechanisms.

**But the NORMATIVE survey satisfies the floor with a DIFFERENT taxonomy that does not map onto the enum:**
- **TeaStore** (`c2-depth-survey.md:145`): mechanism classes = "designed-toggle(flag-eq) / **DB-down** /
  mesh-sever / **input-driven** / (code-level) → ≥4". Mapping to the enum: `designed-toggle`≈`flag` (charitably);
  `mesh-sever`=`mesh-sever`; `code-level`=`code-level`. **`DB-down` and `input-driven` have NO enum home**
  (they are neither flag, mesh-sever, broker-policy, nor code-level). And TeaStore has **no broker** (survey
  `:34`: "no MQ, no async writes"), so `broker-policy` is permanently unavailable. Under the frozen enum
  TeaStore therefore has at most **{flag, mesh-sever, code-level} = 3** distinct mechanisms — it **cannot
  reach 4**.
- **OTel-Demo** (`c2-depth-survey.md:146`): the four GENUINE pairs actually use `{broker-down, mesh-sever,
  vendor-flag, method-scoped-sever}` = **{broker, mesh-sever, flag} = 3 distinct**; the "≥4 met" claim only
  reaches four by **counting `(code-level spare)`** — a mechanism the survey brackets as a spare, i.e. not
  necessarily built. A mechanism with no populated S1 case does not count toward S1 diversity.

**Failing scenario (population engineer, step 3):** they open the survey's TeaStore quota (4–5 S1 cases),
write the `DB-down` case, and reach `fault.mechanism:` — no enum value fits (`kubectl scale sts/…-db
--replicas=0` is not flag/mesh-sever/broker-policy/code-level). The case is schema-invalid against
`c2-freeze.md:58`. They then try to satisfy `c2-freeze.md:184` "≥4 distinct mechanisms" for TeaStore using
only enum values and top out at 3. Result: either TeaStore is silently under-filled (floor violated) or the
engineer invents an off-enum value (schema drift). Both are the failure the frozen invariants exist to
prevent. The applicability matrix's headline non-uniformity — "write-path SUTs = TT + SS + TeaStore +
OTel-Demo" (`e-sut-applicability-matrix.md:21`) — thus does NOT follow from the ≥4-mechanism floor for
TeaStore, and follows for OTel-Demo only if a code-level case is actually built.

**Aggravating fidelity note:** the plan's floor (`c2c3-execution-plan.md:97-98`) reads "S1 ≥ 4 distinct
fault MECHANISMS per write-path SUT **(flag / mesh-sever / broker-policy / code-level, as applicable)**."
The freeze (`c2-freeze.md:184`) **silently dropped both the enum listing AND the "as applicable" qualifier**,
hardening a partly-elastic floor into a strict ≥4 without disclosure in the §6 amendments log — the exact
"silently narrows/changes the plan" failure mode. (Note the plan clause is itself internally tense: "≥4
distinct … as applicable" cannot both hold when only 3 apply.)

**Fix (bounded, must precede population):**
1. Amend the frozen enum to cover the survey's real mechanisms — e.g. add `dependency-down` (the DB-down /
   backing-store-kill class) and either fold `designed-toggle`/`runtime-toggle` into `flag` explicitly or add
   `app-toggle`; decide whether `input-driven`/malformed-input is a *mechanism* at all or a *stimulus* variant
   (it reads as the latter — a workload, not an injected fault — and probably should NOT count toward
   mechanism diversity). Log it in `c2-freeze.md:196` amendments.
2. Restore an explicit "as applicable" semantics OR keep hard-≥4 but then **commit** (not "spare") the 4th
   built mechanism per write-path SUT and re-verify each SUT's count against the amended enum. Under a strict
   enum, TeaStore likely needs reclassification (mesh-sever + toggle + code-level + dependency-down = 4 only
   if dependency-down is admitted).
3. Re-derive the write-path classification and the "≥6 acked-but-lost across write-path SUTs" margin against
   the amended enum, and re-state in the matrix.

---

## [MAJOR]

### M1 — The §4 scoring contract is written for injected/twin cases and is undefined for S3 (wild), which it must nonetheless aggregate.
`c2-freeze.md:164-174` (scoring contract), `c2-freeze.md:175-177` (per-stratum aggregation incl. S3),
`c2-freeze.md:39,115` (S3 stratum).

Step 2 says "Run the fault leg (`stimulus.script` + `fault.injection`)"; step 3 keys off
`negative_control.present`. **S3 wild cases have no `fault.injection`** (they are naturally observed flags —
plan §3.2 M-prevalence "single-leg read-back-absence check") and frequently **no clean twin**. The contract
claims to cover "how a harness scores ANY oracle against a case" (`:164`) and the index MUST aggregate
per-stratum including S3 (`:175`), yet steps 2–3 break on S3. **Failing scenario:** a harness iterates
`cases/*.yaml`, hits an `stratum: S3` case, and finds `fault.injection` empty and no control leg — the
contract gives no defined scoring path. **Fix:** add an explicit S3 branch — for wild cases the oracle's
verdict is the ALREADY-EMITTED flag and scoring is TP/FP/excluded against the adjudicated `label.value` with
no fault/control legs; state that S1/S2 use the inject-and-twin path, S3 uses the observed-flag path.

### M2 — "Precision including underspecified" is promised by the rubric but never defined by the scoring contract.
`c2-freeze.md:136-137` and `c2c3-execution-plan.md:132-133` ("precision is reported both including and
excluding them") vs `c2-freeze.md:170-172` (scoring contract only implements EXCLUDING).

The §3 rubric and plan both promise precision reported *both* ways; the §4 scoring contract (`:172`) only
says underspecified cases are "tallied separately and excluded from the primary precision denominator" — it
never states how the INCLUDING variant is computed (are underspecified-fires counted as FP in the included
denominator? as TP? dropped from numerator only?). **Failing scenario:** the scoring engineer implements §4
verbatim and cannot produce the "including" number the abstract will cite. **Fix:** define it in §4 — e.g.
"included precision = genuine-fires / (genuine-fires + benign-fires + underspecified-fires)," i.e.
underspecified-fires count against precision when included; excluded precision drops them from both terms.

### M3 — No structured durable-state read-back / assertion field: the ground-truth "did the write land" is not machine-derivable from the case file, undercutting "automated per-case replay" and "score ANY oracle."
`c2-freeze.md:61` (`expected_observable` = prose), `c2-freeze.md:75` (`observable_pin` = prose),
`c2-freeze.md:164,187-188` (acceptance requires automated replay + scoring any oracle).

The only representations of "the durable observable" are free-text (`expected_observable`, `observable_pin`).
There is **no machine-readable read-back** (endpoint/SQL/query + expected-present-vs-absent predicate). For
S1 the label is genuine-by-construction, but confirming "the write did not land / lands in the control" — the
very thing the negative control and the replay script exist to prove — has nowhere structured to live, and
the neutral cross-oracle ground-truth `observable_pin` ("anti-gaming; no oracle-specific tailoring," `:75`)
cannot be mechanically checked if it is prose. **Failing scenario:** a reviewer doing the acceptance's
"schema/label-audit m=15" (`c2-freeze.md:190`) cannot verify a case's ground truth without reverse-engineering
each ad-hoc replay script; a comparator harness cannot bind the observable. **Fix:** add
`oracle_eval.read_back: {probe: <endpoint|sql|path>, expect_without_fault: present, expect_with_fault: absent}`
OR state explicitly that `negative_control.replay_script`'s exit code encodes the durable-state assertion and
that `observable_pin` is documentation-only — currently the freeze specifies neither, so the ground-truth
check is unlocated.

### M4 — The rater rubric is NOT identical to the frozen rubric, despite the "one authoritative rubric, not a fork" guarantee; the difference is on the crux (admissibility of the observed state).
`c3-rater-materials.md:7,79` ("one authoritative rubric, not a fork" / "frozen — identical to c2-freeze.md
§3") vs `c3-rater-materials.md:91` vs `c2-freeze.md:140`.

Freeze §3 (`c2-freeze.md:140`): "**Inadmissible evidence: runtime behavior, traces, MIST output.**" Rater §3
(`c3-rater-materials.md:91`): "Inadmissible: **runtime behavior beyond what the case shows**, traces, any tool
output." These are not identical, and the difference is load-bearing: the rater IS shown "the resulting
observed durable state" (`:47`) — runtime behavior — and must use it as the *observation* to be judged. Taken
literally, the freeze's flat "runtime behavior inadmissible" forbids the very datum every case is built
around; the rater copy silently repairs this with "beyond what the case shows." So the "identical, not a
fork" guarantee (`:7`) is violated, and the authoritative frozen copy is the *wrong* one. **Failing
scenario:** a reviewer diffs the two rubrics (the freeze's whole point is one authoritative copy) and finds
they disagree on what evidence a rater may use — the reliability claim rests on a rubric that exists in two
versions. **Fix:** reconcile verbatim in BOTH files: the case's own presented observation is the datum to be
judged (admissible AS the observation); docs/OpenAPI/source are the sole source of the NORM (what should have
happened); traces and any tool output remain inadmissible.

### M5 — Blindness leak: a structural tell distinguishes S3 (measurement) from calibration cases, so §0's "a rater cannot tell a flagged case from a calibration case" is not established.
`c3-rater-materials.md:26-27` (blindness claim + "the observed state on the paired clean run where one
exists"), `c3-rater-materials.md:128` (calibration drawn from S1+S2), `c2-freeze.md:112` (S1 ⇒
`negative_control.present==true`), plan §3.2 (S3 prevalence = single-leg, no twin).

Calibration cases are S1 positives + S2 benign; **every S1 case has a negative control** (`c2-freeze.md:112`),
so calibration cases carry a "paired clean run." **S3 wild cases — especially single-leg read-back-absence
flags — have no clean twin.** The rater-facing case shows "the observed state on the paired clean run **where
one exists**" (`:27`), so *presence-vs-absence of the clean-run comparison is a systematic signal*:
clean-run-present ⇒ calibration (known-label), clean-run-absent ⇒ wild (the cases actually being measured). A
rater who notices this can separate the measurement stratum from the tuned calibration set even without naming
a tool — defeating the "normalized mix" premise the precision claim rests on (`:16-31`). **Failing scenario:**
a microservice-literate rater (the required profile) observes that half the set lacks any "no-fault run"
column and infers those are the real targets; their labeling on that subset is no longer blind-symmetric.
**Fix:** normalize the structural surface — either synthesize/attach a clean-run comparison for S3 cases too
(or strip it from calibration cases), and audit for other distributional tells (SUT/endpoint mix), so the two
strata are surface-indistinguishable. §0 must add this as an explicit invariant, not just "one common format."

### M6 — Reported κ pools calibration + S3, re-injecting the rubric-tuned calibration cases into the headline reliability statistic; it is upward-biased.
`c2-freeze.md:148` / `c3-rater-materials.md:124-125` ("κ over pooled calibration+S3, n≥50"),
`c2-freeze.md:142-144` (rubric iterated to maximize agreement ON calibration).

The κ-gate iterates the rubric to convergence **on the calibration cases** (`:142-144`), then the reported
reliability coefficient is computed **over a pool that includes those same calibration cases** (`:148`).
Calibration cases are pre-vetted, known-label, and were the optimization target — pooling them inflates the
reported agreement above what holds on the fresh S3 (wild) cases that the precision claim actually depends on.
The *measurement* separation is sound (calibration cases are NOT reused as S3 measurement cases,
`c3-rater-materials.md:132`; iteration is calibration-only with "no S3 peeking," `:143`) — so this is a
REPORTING-validity issue, not measurement leakage. **Failing scenario:** a stats-literate reviewer notes the
headline κ is computed on a set half-composed of the cases used to tune the rubric, and discounts it. **Fix:**
report **S3-only κ as the primary** reliability number (with Clopper–Pearson/counts where n<10 per the
existing rule), and present pooled κ as a secondary small-n-stability figure with the calibration-inflation
caveat disclosed.

### M7 — The S1 ≥ 45 floor is not demonstrably reachable from the normative survey + matrix; a good-faith tally lands ~37–41.
`c2-freeze.md:180` / `e-sut-applicability-matrix.md:39-41` (S1 arithmetic asserted, not counted).

The matrix says new SUTs give "~9–11 new S1 cases" and "TT … + SS carry the balance" (`:39-41`) but does not
count to 45. Generous enumeration: TeaStore 5 + OTel-Demo 5 + Boutique 1 = 11; TT F-corpus target 10 + G1/G3
"~10 seed cases" (plan §1) ≈ 20; SS shipping-h2h + carts ≈ 5–8. **Max ≈ 39; realistic ≈ 37**, below the 45
floor — and the matrix itself admits "the F-corpus floor … is the main remaining lever" while the lever's
*target* (10) is already in the tally. **Failing scenario:** population reaches ~38 S1 cases and the plan's
"floors = stop-and-replan" (plan §5.5 / §2.3) triggers, un-forecast, at step 3. **Fix:** show the S1 tally to
45 explicitly in the matrix (per-source counts), or raise specific quotas / name additional S1 sources now,
or pre-register the shortfall risk with the stop-and-replan branch surfaced. This is a freeze-time catch, not
a step-3 surprise.

### M8 — The OTel-Demo "flagship" S1 case is genuine-BY-CONSTRUCTION, but its durable write is arguably by-design best-effort; the freeze does not reconcile S1's construction bar with the rubric's contract-grounding bar.
`c2-depth-survey.md:71-99,146` (flagship async), `e-sut-applicability-matrix.md:14` ("flagship async
acked-but-lost"), `c2-freeze.md:88,112-113` (S1 ⇒ genuine/by-injection), `c2-freeze.md:128-131` (rubric
"genuine" REQUIRES contract-grounding from docs/spec/source).

The rubric defines **genuine** as requiring that "it should have persisted" be "derivable from
docs/OpenAPI-spec/source" (`c2-freeze.md:130-131`). S1's invariant asserts genuine purely by injection
(`:112`), bypassing that contract bar. For most S1 cases this is fine (injection + control divergence = a real
lost write). But the flagship OTel case sits exactly on the seam: checkout's Kafka publish to the accounting
sink is **deliberately fire-and-forget** (verified: `sendToPostProcessor` has no error return, publish error
only logged) — which a critic can read as "post-processing accounting is intentionally best-effort/decoupled,"
i.e. **benign by-design under the very rubric the benchmark ships**. The benchmark's answer ("genuine because
the negative control shows the accounting row normally lands") is defensible but *contestable*, and it is
undisclosed that S1-by-injection genuineness ≠ the rubric's contract-grounded genuineness. (The class is
industrially real — the sweep cites Cast's "HTTP 200 OK … despite the internal failure on unchecked async
Kafka publishes," `c2-claim-sweep.md:§1` — so this is a labeling-soundness nuance, not a strawman worry.)
**Failing scenario:** a venue reviewer asks "is your flagship positive a defect or documented best-effort?"
and the freeze has no contract-grounding evidence on record for it. **Fix:** for S1 data-integrity cases whose
durable write is plausibly best-effort (OTel accounting especially), attach the contract-grounding evidence
(OTel-Demo docs/spec establishing order→accounting persistence is expected, not decorative), OR explicitly
disclose that S1-by-injection genuineness = "injection-induced divergence from the negative control," a bar
distinct from the rubric's contract bar, and that the flagship rests on the former.

---

## [MINOR]

### m1 — An S2 invariant clause listed under "machine-checkable" is not machine-checkable.
`c2-freeze.md:111` ("Schema invariants (frozen, machine-checkable at population)") vs `:113-114`
(`fault.mechanism==none` **OR a documented benign degradation**). The "OR a documented benign degradation"
disjunct is a human judgment; a machine can only check `==none`, and real S2 cases use non-none mechanisms
(e.g. OTel `kafkaQueueProblems` = a flag producing benign delay). **Fix:** split it — the machine-checkable
part is `label.value==benign ∧ provenance==by-docs ∧ doc_citation!=null`; the "benign degradation" judgment is
an audit criterion, not a machine invariant. Relabel accordingly.

### m2 — The `negative_control` comment bakes in the outcome it is supposed to measure.
`c2-freeze.md:64` ("scored as a true negative"). The control leg is a TN only if the oracle does NOT fire on
it; if it fires, it is an FP (which §4 step 4 correctly says). "Scored as a true negative" pre-supposes the
pass. **Fix:** "the control leg is the oracle's negative test — not-firing ⇒ TN, firing ⇒ FP."

### m3 — "Seven fields" are numbered 1,2,3,3b,4,5,6 (no field labeled 7).
`c2-freeze.md:31` ("All seven … fields are present") vs the `# +field` tags (`:52,63,76,77,81,90,98`). The 7th
(the single frozen MIST commit) is folded as `# +field 3b` (`:81`). All seven ARE present and correct — this
is only an audit-legibility nit. **Fix:** note "3b is the 7th field" or renumber to `# +field 7`.

### m4 — The survey's TeaStore swallow citation names `NonBalancedCRUDOperations` as the call site; `placeOrder` actually calls the `LoadBalancedCRUDOperations` wrapper.
`c2-depth-survey.md:37-42`. Substantively correct and VERIFIED (the wrapper delegates to NonBalanced and
propagates the `-1` unchanged — see below), but the chain omits the intermediary. **Fix:** cite
`AuthUserActionsRest.placeOrder → LoadBalancedCRUDOperations.sendEntityForCreation → (ServiceLoadBalancer) →
NonBalancedCRUDOperations.sendEntityForCreation` so the reproduction path is exact.

---

## What I VERIFIED as CORRECT (holds — for the reconciliation)

1. **TeaStore natural masked-write chain — VERIFIED from upstream source (the survey's key finding HOLDS).**
   - `NonBalancedCRUDOperations.sendEntityForCreation` (utilities/…/registryclient/rest/): `long id = -1L`,
     set only on HTTP 201; throws `NotFoundException` on 404 and `TimeoutException` on 408; **returns `-1L` on
     any other status (500/503) with no throw** — verbatim confirmed.
   - `LoadBalancedCRUDOperations.sendEntityForCreation(Service, endpointURI, class, entity)` =
     `Optional.ofNullable(ServiceLoadBalancer.loadBalanceRESTOperation(…, client ->
     NonBalancedCRUDOperations.sendEntityForCreation(client, entity))).orElse(-1L)` — **propagates the `-1`
     unchanged, adds no id check** — verbatim confirmed.
   - `AuthUserActionsRest.placeOrder` catches only `LoadBalancerTimeoutException`→408 and `NotFoundException`
     →404, **never checks `orderId == -1`**, then `blob.setOrder(new Order()); blob.getOrderItems().clear();`
     and returns **200 OK + SessionBlob** — verbatim confirmed.
   - **The charge's "-1 body is a tell → weak masked claim" concern does NOT apply:** the blob is CLEARED
     before return, so the `-1` is never echoed to the client — the response is a clean 200 ORDERCONFIRMED.
     This STRENGTHENS the masked claim. The one place `-1` IS visible (the internal-CRUD 201/`-1` tier) the
     survey correctly caps as "breadth only" (`c2-depth-survey.md:47-48,54-55`).

2. **OTel-Demo async Kafka-swallow — VERIFIED from upstream source (the flagship claim HOLDS on mechanics).**
   checkout `PlaceOrder`: `_ = cs.emptyUserCart(...)` swallowed; `sendToPostProcessor` has **no error return
   type**, Kafka publish errors only logged (`logger.Error(... Failed to write message ...)`) — the ack path
   is unaffected. accounting `Consumer.cs` persists `OrderEntity`/`OrderItemEntity`/`ShippingEntity` via
   `dbContext.SaveChanges()` to Postgres — the durable consumer-side read-back exists. (The one live risk —
   whether checkout stays fast / stays up when the broker is down — the survey already flags as a
   verify-at-deploy rider, `c2-depth-survey.md:99,157`. Labeling nuance = M8.)

3. **Bookinfo "0 opportunities" — VERIFIED.** `samples/bookinfo/src/ratings/ratings.js`: POST
   `/ratings/{productId}` returns **501** on `SERVICE_VERSION==='v2'` (DB-backed); non-DB variants write only
   to a local `userAddedRatings[]` in-memory array (no severable downstream). The matrix's exclusion of
   Bookinfo as non-write-path (`e-sut-applicability-matrix.md:16,24`) follows correctly.

4. **Boutique / Bookinfo EXCLUSION logic is sound given the counts.** Both are *exclusions* (conservative:
   the risk in a write-path table is false INCLUSION, and B1 is exactly that risk for TeaStore). Boutique's
   "orders not persisted" (1 site) was not independently re-sourced here but is consistent with Online
   Boutique's known stateless-except-Redis-cart architecture; Bookinfo is source-verified above.

5. **All seven review-B-M5 schema fields present and correct:** negative control (`:63`), health
   preconditions + seeding (`:52`), oracle-config provenance (`:77`) + one frozen MIST commit (`:81`), label
   version-validity (`:90`), per-case license (`:98`), trace-visibility class with the exact three-value enum
   (`:76`). The "MIST commit identical across all cases" invariant is present and machine-checkable (`:118`).

6. **The §8.5-1 three-way rule and κ-gate are reproduced faithfully** from plan §3.1 into freeze §3 and rater
   §3 (genuine/benign/underspecified definitions; underspecified excluded + fraction reported; disagreement →
   third adjudicator; ≤2 calibration-only iteration rounds; relabel-all) — modulo the admissibility wording
   in M4. The κ-gate's *measurement* separation (calibration not reused as S3; no S3 peeking) is sound; only
   the *reported* statistic is biased (M6).

7. **Claim-sweep and license-audit are thorough** (reviewed at a glance): 32 competitors examined, none
   satisfy the conjunction, proactive-citation obligations (CloudAnoBench, Uber Zenodo) and positioning
   evidence (OpenRCA 2.0 / FP-aware-TT filter out the masked class) captured; license audit finds no blockers,
   correctly routes the unlicensed fault-replicate corpus to replicate-by-description and MIST (LGPL-3.0) to
   by-reference. Not the focus of this charge; no issues surfaced on the read.

---

*Reviewer A (fidelity + soundness). Upstream verified via WebFetch against DescartesResearch/TeaStore@master,
open-telemetry/opentelemetry-demo@main, istio/istio@master samples on 2026-07-08.*
