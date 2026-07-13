# Plan — S3 wild-hunt + M-prevalence window (rev 2, post 3-cold-review)

**Status:** rev 2 — folds ALL findings from the 3-cold review of rev 1 (A oracle-soundness 5-BLOCKING/
5-MAJOR · B engineering 3-BLOCKING/4-MAJOR · C hostile-PC 3-BLOCKING/6-MAJOR; reconciliation =
`REVIEW-S3-PLAN-RECONCILIATION.md`). **Nothing executes until the confirmation pass returns unanimous
ACCEPT.** Deliverable identity unchanged: the Step-5 hunt + the assembly-ready C3 rating corpus, with
the rater-material pipeline and the A-goal as first-class constraints.

## §0 Deliverables + the HONEST PRIOR (C-M1: stated before we look)

1. **S3 candidate corpus** — every CONFIRMED wild flag captured at flag time as a sidecar-format
   bundle; S3 case files (`stratum: 3`, `capture_status: captured`, `label.provenance:
   by-adjudication`, label PENDING — labels ONLY from blind raters).
2. **M-prevalence datum** — the §5 estimand, both denominators, coverage ratio, write-path fraction,
   recall-on-S1 qualifier, workload scripts versioned pre-run.
3. **Assembly-ready rating corpus** — S3 sample + calibration mix, B4-rendered, delivered to the
   9-check entry gate; **"assembly-ready EXCEPT the Step-4 M-yield stratum (named hold — C-M3)"**:
   the hand-over note states in bold that rating must not begin until the M-yield audit stratum is
   merged into the sealed mix (raters are debriefed/unblinded at close; a second round with the same
   raters is impossible), OR a two-round protocol is separately pre-registered. USER-side holds: IRB
   received before FIRST CONTACT (F22); per-rater §11 blindness screens + debriefs.
4. **A-goal mapping, with the honest prior (C-M1/C-1):** the natural-discriminator headline needs
   (a) ≥1 CONFIRMED wild flag × (b) raters label it *genuine* × (c) on a traced SUT × (d) the frozen
   comparator misses. Two sober facts, stated up front: **every acked-lost behavior ever captured on
   OTel-Demo/TeaStore required an injected fault** — the honest prior of a natural find there is
   ≈ 0, so the **scarcity branch is the central expectation, not a "null path"**; and **the frozen
   rubric's async tie-break rules an async absence with no bundle-derivable completion bound
   *underspecified*, not genuine** — so even a true natural loss on OTel's async path most plausibly
   forfeits leg (b) BY RULE. The only surface where (a)–(d) are simultaneously plausible is a
   **sync-acked path on the known-buggy SUT = TrainTicket** → TT is MANDATORY (§6). P0 verifies
   whether each per-SUT pinned docs bundle contains any upstream statement bounding async completion
   (included only if it exists — never manufactured).
5. **Pre-committed claim sentences (C-M5) — the only forms the RESULT/paper may use:**
   - *Zero finds:* "0 CONFIRMED flags in N acked writes over K bound endpoints on these SUTs under
     the pinned workload; rule-of-three 95% upper bound ≈ 3/N on the per-write CONFIRMED-flag rate
     under these conditions; no cross-population claim."
   - *Any find:* existential only ("natural acked-lost writes exist in deployed OSS microservices")
     + the site count; never a rate extrapolation in either direction.
   - *κ:* S3-only κ is PRIMARY but **withheld at |S3|<10** (raw agreement + Clopper–Pearson only —
     A-F12/C-m1); |S3|=0 degenerate branch: rated set = calibration (+M-yield when merged), no S3
     precision row; the study still yields calibration κ + the bias audit.

## §1 Pre-registration + THE P0 FREEZE ROW (C-B1)

**Before any calibration or counted window**, commit ONE dated freeze §6 amendment row —
"Step-5-as-amended" — pinning: detector-(ii)-only (D1, detector-(i) unbuilt); the 500-write-stop
branch; the RAW/CONFIRMED flag levels + T+5 min re-probe + evidence rules (§2); scarcity threshold
binding on CONFIRMED (A-F1c); the provenance-scoped known-site rule (§2b); the sampling strata + the
no-tool-signal-in-sampling rule (§2c); the Sockshop exclusion (draining-queue read-back);
the re-qualified estimand string (§5); the CONFIRMED-level FP bar (§3); the unconditional calibration
top-up (§4). A pre-registered study amends BEFORE the data exist, not at RESULT time.

**Deviation D1 (disclosed):** detector-(i) (`mist_trace_shape`) unbuilt → hunt = detector-(ii) only;
estimand re-qualified (§5). Compensating measure: per-flag trace exports on OTel for COMPARATOR
scoring (§2d) — the headline needs the comparator to miss, not MIST's trace oracle to run.

## §2 Detector spec (fully pinned; A-F1, B-m8)

**RAW flag** (runtime terms): write acked (2xx/success envelope per the frozen ack rule) ∧ observe
record `error == null` ∧ absence at cap (gate `TIMEOUT_ABSENT`; gates reported per stratum — on
un-traced SUTs every absence is timeout-gated and CONFIRMED is explicitly REACHABLE from
`TIMEOUT_ABSENT`, A-F1b) ∧ the W3 quarantine gate open (≥1 `OBSERVED_PRESENT` for the same triple
in-session). Read-back ERROR records (non-2xx decisive read, `VANISHED` value-delta, collection at
`readback_bound`) are UNUSABLE — counted in their own window-log bucket per triple (A-F11), never
flags.

**CONFIRMED flag** = RAW ∧ a **T+5 min re-probe still absent**, where the re-probe: (i) goes through
the SAME transport instance; (ii) uses the SAME presence predicate as the runtime — via a **public
static probe-evaluation accessor added to `DataIntegrityRuntime`** (visibility-only widening,
precedent `installHttpOverride`, disclosed under the open gate; no predicate fork — B-M4); (iii) is
subject to the runtime's evidence discipline VERBATIM: a non-2xx re-probe, a VANISHED row, or a
bound-hit re-read yields an ERROR record and **never a CONFIRMED flag** (A-F1a); (iv) is scheduled
≥T+5 min and executed between journeys (single-threaded — the ThreadLocal handoff assumes one
thread, A-F14/B); OTel's `markerSupplier` is re-pointed to the flagged marker for the re-probe and
restored. Journey transcripts + RunRecord bodies are retained until each flag is CONFIRMED/cleared.
Scarcity (<20) binds on CONFIRMED; RAW-only flags are reported as the
**present-at-re-probe (delayed-beyond-cap)** bucket (descriptive, never pre-judged benign — A-F15a).

**Markers (B-B2, would otherwise kill the corpus at render time):** neutral, whitelist-compatible
grammar **`corpus-w<seq>-<12hex>`** — NO "mist" or any tool string (2.75-A's `mist-<leg>-…` grammar
is BANNED here; `SqlDurableReadback`'s whitelist forbids spaces so no "1 Corpus Way" style on OTel).
P0 unit tests: marker grammar × `b4_harness.py` BANNED_STRINGS; probe descriptors human-neutral
("SQL SELECT … WHERE …", "GET /rest/orders") — never Java class names (A-m3). **Per-SUT entry
condition: one calibration write's bundle round-trips through `b4_harness.py` render successfully
BEFORE the counted window starts.**

**Session + quarantine (A-F6):** ONE observe session per SUT window; quarantine evaluated at window
end; the window log reports acked-writes + zero-presence counts per quarantined triple; quarantined
triples may be manually inspected/upstream-filed but are **never S3-eligible** (post-hoc promotion
would change the detector). The estimand carries the W3 conditioning explicitly (§5): always-lost
defect sites are UNHUNTABLE by design — exactly the class the OTel flagship represents; disclosed.

**Mid-window circuit breaker (B-M5):** ≥5 consecutive RAW flags OR trailing-50 RAW rate >20% →
PAUSE + runbook health check (kafka/rdkafka wedge, accounting, DB, PFs). Pre-registered resolution:
if the health check finds an infrastructure fault, the window is marked INTERRUPTED at that write
index, the environment is repaired, and the window RESUMES with the interruption + repair logged
(counted writes before/after both reported); flags raised during a diagnosed infrastructure fault
are excluded from S3 (environment artifacts) but reported. If no infrastructure fault is found, the
flags stand and the window continues.

**Knobs (numeric, P0-pinned — B-m9):** OTel `timeout.ms=25000`, `poll.ms=2000` (psql-exec-dominated);
TeaStore/TT `timeout.ms=10000`, `poll.ms=500`. TT JWT refresh per journey batch (401 ⇒ ERROR record
+ re-login, B-m10); TeaStore user rotation over pre-generated users with the 2.75-A full-collection
read + `readback_bound` growth watch (A-F11/B-m10). Expected wall-clock (B-m11): OTel ≈ 1.5–2.5 h,
TeaStore ≈ 45–90 min, TT ≈ 2–4 h healthy; the breaker bounds the pathological case.

### §2b Known-site rule (provenance-scoped — C-B2/B-B1; was rev-1's headline-killer)

A CONFIRMED flag matching an authored S1/S2 case site (endpoint + failure mode) is excluded from the
S3 rated sample **only when the authored case's defect has NATURAL provenance** (the defect ships in
the SUT's code: TT cancel→refund natural, TT contacts dedupe, TT noop-modify). Sites whose authored
case is **injected-fault provenance** (OTel checkout-lost = kafka-scale injector; TeaStore
maintenance/mesh-sever = operator toggles) remain **S3-ELIGIBLE**: a natural loss there with no
injector active is a NEW defect. Guard: pre-window environment verification — flagd at frozen
defaults (via the flagd-ui API, the toggle of record), kafka healthy, TeaStore maintenance=false, no
VS/mesh artifacts — recorded in the window log; every such flag's bundle carries a mandatory
root-cause-distinction note ("no injector active; frozen-default config verified at <t_rel>").
**Precision reported BOTH ways (A-F8):** (i) new-sites-only (rater-labeled); (ii) all-CONFIRMED
including rediscoveries scored by their known authored labels; rediscovery counts reported by class.

### §2c Sampling (A-F3)

If CONFIRMED flags > 40: strata = **SUT × distinct defect-site**, proportional allocation,
deterministic seed recorded in the freeze row. **No tool-derived signal beyond the CONFIRMED flag
itself — in particular trace exports and comparator outcomes — plays ANY role in sampling.**
CI units = distinct defect-sites, never flagged events.

### §2d Per-flag trace export on OTel (A-F4 + B-B3 — headline anti-fabrication)

- **Canary gate (P1, pre-window):** a benign checkout with an injected client `traceparent`;
  `GET /jaeger/api/traces/<id>` must return frontend+checkout spans under OUR id. Adoption on
  OTel-Demo (Envoy→Node→Go) is UNPROVEN (the E2 precedent is TT/javaagent) — if the canary fails,
  the pinned fallback is a tight-window service query keyed by the session uuid attribute with an
  exactly-one-match-else-ERROR rule (the E2 temporal-isolation rationale does NOT transfer to a
  continuous window; the fallback's ambiguity failure mode is loud, never silent).
- **Retention rider:** Jaeger is memory-storage — at calibration, verify a ~10-min-old trace is
  still fetchable; export a snapshot at RAW-flag time; **the demo load-generator is OFF** (pinned;
  it churns Jaeger memory AND would contaminate the denominators — any deviation disclosed).
- **Stability rule (A-F4):** the comparator-scoring export is (re-)fetched **at/after CONFIRM time**
  with a two-read span-count stability check (mirroring `traceComplete`'s settle discipline); fetch
  times + span counts recorded in the bundle. **A comparator MISS is headline-eligible ONLY on a
  stability-checked export.** The RAW-time snapshot is supplementary.
- Traces are stored BESIDE the sidecar (separate file — any extra record kind fails `_check_sidecar`,
  B-M6c), never rater-facing (strip-list), and traceparent ids never appear inside sidecar records.

## §3 S2-FP calibration (A-F2 — the frozen bar cannot see the operative rule)

Per SUT, before the counted window, ≥20 acked benign writes through the IDENTICAL detector path —
**in observe mode** (A-F14), same knobs, same re-probe machinery. TWO bars, BOTH must pass:
1. the frozen fpProbe sync bar (non-timeout-gated FP ≤5%) — kept for continuity, and explicitly
   disclosed as structurally weak on trace-uninstrumented SUTs (all absences there are timeout-gated);
2. **the CONFIRMED-level FP bar (new, pre-registered): CONFIRMED-flag rate ≤5% (≤1/20) on the same
   benign writes with the identical T+5 min re-probe** — the bar that actually gates the sampled rule.
Bar fails ⇒ fix binding/caps → full re-calibration; the window opens only on double-PASS. Calibration
bundles are emitted in the same format/cadence and may seed the §4 top-up pool (C-m6) subject to the
degradation-shape rule.

## §4 Rater-material pipeline (corrected arithmetic + shape/cadence uniformity)

1. **Calibration arithmetic (A-F5 — corrected):** required = max(30, 50−|S3|) ⇒ **range 30–50**
   (50 at |S3|=0), benign ≥2:1 ⇒ **20–34 benign** against a captured pool of **11 negatives** (minus
   eligibility-screen + worked-example consumption). **The shortfall is UNCONDITIONAL.** Handling
   (C-B3 pinned, constrained): a **fixed worst-case benign top-up batch of ~25–30, captured DURING
   each SUT's window** (no post-window re-revival — |S3| is only known at P4), spread across SUTs
   for distributional balance; authored as **S2 benign cases**; **degradation-shaped ONLY**
   (documented async-delay windows, by-design drops/soft-rejects, bounded eventual consistency —
   NEVER "nothing happened" clean journeys: a present-vs-absent split between calibration and S3
   would make the stratum decodable from observation content, C-B3/A-F10); surplus discarded at P5
   by a pinned deterministic rule. If the degradation-shaped supply cannot reach the skew at the
   computed size ⇒ the **disclosed shortfall branch at floor 30** (never dilute shape or skew).
   Disclose: the bias-audit's genuine row is thin (9 captured positives) and its negatives are
   easier than S3 material to the extent the top-up under-represents async ambiguity (A-F10).
2. **ONE observation cadence for ALL rater-facing strata (C-M2):** initial poll cadence + the
   T+5 min re-probe pinned identically for S3 bundles, top-up captures, and calibration cases;
   legacy calibration cases that cannot match the cadence are re-captured with the S3 runner or
   excluded from the rated mix. "Observation-record cadence uniformity" is added to the tell-audit
   checklist and the P0 unit tests. (The re-probe is NOT stripped from S3 — the relative-time datum
   is judgment-relevant for the async tie-break.)
3. **Sidecar pins (B-M6):** `t_rel_ms` rebased to each case's FIRST record (never window-global);
   record scope = the flagged write's whole journey transcript + observations (baseline / at-cap /
   ≥T+5 min re-probe — one shape for every producer); credentials REDACTED by the producer (TT
   register/login, TeaStore loginAction — format rule 5); psql probes phrased neutrally; `producer:
   "wildflag-bundle"` + mist_commit stamp (stripped by B4); **emission architecture = Java runner
   emits ordered raw records, a thin Python assembler reusing `capture_driver`'s serialization
   conventions produces the sidecar** (deterministic bytes; org.json field order is not guaranteed).
   Replay pointers are sidecar-internal, never rater-facing (A-F13).
4. **B4 render** of all strata (strip-list, opaque ids, shape uniformity); **worked examples
   authored on real calibration cases at P5 (our-side — C-M4)** + packet rev ≥3 finalization
   confirmed; the 9-check entry gate delivered with USER-side holds marked; M-yield hold per §0.3.
5. **Rater-time budget (C-m4):** the hand-over note includes a per-scenario table (|S3|=0 vs 40 ⇒
   rated-set size ⇒ hours/rater at 15–45 min/case) and flags the packet's internal 15–45 h vs
   22–68 h inconsistency for the user's consent-accuracy decision (IRB-facing).

## §5 Estimand + qualifiers (A-F6/F7/F9, C-M5/m5)

**Estimand string (frozen at P0):** "a LOWER BOUND on the prevalence of rater-confirmed-genuine
acked-lost writes, **conditioned on detector-(ii) (single-leg read-back absence) + the W3
presence-quarantine + the CONFIRMED re-probe qualifier, over the BOUND write paths, under the pinned
fault-free workload**." Conditioning consequences disclosed: always-lost sites unhuntable (W3);
trace-visible/store-present anomalies unhunted (D1); coverage = bound/total write endpoints per SUT.
**Denominators (A-F7):** numerator = rater-confirmed genuine DISTINCT DEFECT-SITES among CONFIRMED
flags; denominator (a) = acked writes through bound triples in the counted windows; denominator
(b) = distinct bound write endpoints; the quotable bound = the per-write rate vs (a). The 500-write
stop counts acked bound-triple writes.
**Recall-on-S1 qualifier (A-F9):** reported per case, each entry marked MEASURED or ANALYTIC;
analytic entries derived under the ACTUAL observe semantics (ack rule + T+5 re-probe + W3 + session
scope) — honestly: an always-on permanent-loss fault with no benign same-triple write in-session has
**analytic recall 0 (quarantined)**; analytic entries are `specified`-grade, never pooled with
measured entries. **Measured leg (scheduled):** one observe-mode run per SUT with the already-built
2.75-A/G3 injectors, during the same tenancy, **strictly AFTER the counted window closes** (C-m2),
distinct markers, excluded from all window denominators.

## §6 SUT set (C-M6 + B-M7 reconciled: TT MANDATORY)

- **P1 OTel-Demo** (traced; comparator-column compensating measure; flagd frozen defaults verified).
- **P2 TeaStore** (bound, cheap; trace-uninstrumented ⇒ sidecar-only; never `GET /rest/generatedb`).
- **P3 TrainTicket — MANDATORY** (the only nonzero-prior + sync-acked + traced-capable surface; the
  revival runbook is battle-tested). **Rider: bind 2–3 ADDITIONAL TT write endpoints** as YAML
  triples over the existing `installHttpOverride`/RestAssured mechanics (configuration within the
  opened gate, no new detector code), chosen at P0 from write paths WITHOUT natural-provenance
  authored cases (so §2b keeps them eligible); journeys cover all bound endpoints.
- Optional rider (reviewer-priced, not required): one OTel cart triple (~half-day, session-scoped
  holder) — only if P1 scheduling allows; disclosed framing.
- **Coverage restated per-endpoint (C-M6):** ≥500 acked writes per SUT spread across ALL bound
  endpoints with a per-endpoint minimum ≥100; both denominators reported per endpoint.
- Sockshop EXCLUDED (draining-queue read-back = single-leg FP storm; disclosed).
- One tenant at a time; every window ends scale-to-0 (after its top-up captures + measured-recall leg).

## §7 Phases (each gated)

- **P0 — pins + freeze row:** the §1 freeze §6 amendment row committed; journey scripts versioned;
  runner + accessor + assembler code with unit tests (flag predicate; marker×BANNED_STRINGS; cadence
  uniformity; sidecar shape round-trip through `b4_harness.py`; re-probe evidence rules; neutral
  descriptors); knobs pinned; TT extra-triple selection pinned; docs-bundle async-bound check (§0.4).
- **P1 OTel:** revive → traceparent CANARY + retention check + load-gen OFF + flagd-defaults guard →
  double-bar calibration → 500-write window (+breaker) → top-up captures + measured-recall leg →
  bundles + window log → scale to 0.
- **P2 TeaStore:** revive → calibration → window → top-up + recall leg → scale to 0.
- **P3 TT (MANDATORY):** revive per runbook (mysql-0 force-recreate fix; nacos doubleWrite) →
  calibration → window over ALL bound TT triples → top-up + recall leg → scale to 0.
- **P4 — dedup (provenance-scoped) → environment-guard audit → stratified sample → S3 case files +
  sidecars; <20 CONFIRMED ⇒ scarcity branch invoked (pre-registered, the central expectation).**
- **P5 — assembly readiness:** calibration sizing per |S3| + top-up finalization (deterministic
  surplus rule) → cadence-uniformity tell-audit → B4 render (all strata) → worked examples authored →
  our-side entry-gate checks → SEALED manifest + hash → hand-over note (USER-side holds: IRB F22,
  blindness screens; the M-yield hold in bold; the rater-time table).
- **P6 — RESULT + 3-cold review** (the standing backstop) before anything is called claim-ready;
  freeze §6 dated close-out row; README/FILE_INDEX/memory sync.

## §8 Out of scope

Detector-(i) build (Branch-B stays deferred) · the kafkaQueueProblems S1 candidate (separate
discipline) · the actual rating (user-side IRB/rater logistics; Step-4 M-yield merge precedes any
rating) · any genuineness claim before raters rule · population-prevalence claims in any direction.

## §9 Definition of done

P0 freeze row + pins committed pre-window; per-SUT double-bar calibration PASS records; window logs
(both denominators per endpoint, write-path fraction, ERROR/quarantine/breaker buckets, RAW +
present-at-re-probe + CONFIRMED counts per gate stratum); environment-guard records; every CONFIRMED
flag has a bundle (+ stability-checked trace export on OTel); provenance-scoped dedup + rediscovery
counts; S3 sample + cases authored (or scarcity branch invoked); top-up batch captured + sized;
cadence-uniform B4 render; worked examples; our-side gate checks green + SEALED manifest; hand-over
note (user-side + M-yield holds, rater-time table); measured-recall legs done; RESULT-of-record
carrying §0 verbatim (pre-committed claim sentences only); freeze close-out row; docs/memory sync;
tenants at 0. THEN the 3-cold review of the RESULT.
