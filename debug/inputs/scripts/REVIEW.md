# Code Review — D4-D7 Input Quality Metrics
Date: 2026-04-30

## Summary

The D4-D7 implementation is structurally sound and the test suite (63/63 passing) catches many obvious issues. The pipeline runs end-to-end against real artefacts and produces interpretable JSON summaries with appropriate "ship a bound, not a number" caveats for D4. However, **a handful of specific bugs cause material under- or mis-counting on real data**, and one CSV-encoding issue silently corrupts ~46 LLM provenance entries on every run. Additionally, the pipeline silently drops a parameter name (`id`) that accounts for ~30% of all body-parameter rows in the test data, because `is_id_like('id')` requires a non-empty stem before the suffix.

**Top three issues, ranked by severity:**

1. **Critical — `mine_provenance.classify_line` does not strip the `(from N options)` suffix from `LLM (...)` lines.** Every LLM-fallback row in `provenance.csv` carries the polluted suffix in its `value` field (`'orderId' = 'ORD123456 (from 5 options)'`), so the join with `inputs.csv` on `(parameter, value)` fails for every LLM-classified value. In the run I exercised, 46/46 LLM entries are corrupted; on real data those rows fall through to `UNKNOWN` (or — see #2 — to `NEGATIVE_FAULT`). The `VALUE_STOP` regex needs to include `' (from '` as a stop token.

2. **High — the (param, value) join in D4 is non-injective and the upgrade rule is wrong.** The same value (e.g., `accountId='\`\`\`'`) can be drawn from the shared pool in a positive variant *and* injected as a fault in a negative variant; `mine_provenance` then collapses the two events to a single entry whose label depends on log-line order. The current "upgrade" rule (`SHARED_POOL_DRAW` → `NEGATIVE_FAULT` when the latter appears later) actively *prefers* the negative classification, which then mis-classifies legitimate positive variants as `NEGATIVE_FAULT` in D4. In this run, six positive ID-typed inputs were silently mis-attributed as `negative`.

3. **High — `is_id_like('id')` returns False, dropping 1,974 rows (~30%) of body-typed `id` parameters from the D4/D5/D6 denominator.** The `ID_SUFFIX` regex `^.+?(Id|...)` requires at least one stem character before the suffix, so the literal `id` parameter (used by `POST /api/v1/adminorderservice/adminorder` etc., per the OAS) never enters the metric. This is a coverage hole, not a wrong number — but it is a substantial coverage hole for TrainTicket's admin endpoints.

**Verdict: ship after small fixes.** The D5/D6/D7 logic is essentially correct; D4's regex fixes and the (param, value) join discipline are 30-line changes. The framework's threshold gates won't change qualitatively after these fixes, but the metric numbers will.

---

## Findings

### Finding #1 — `(from N options)` suffix is not stripped from LLM provenance lines [Critical]
**File:** `debug/inputs/scripts/mine_provenance.py:57, 77-79`
**Issue:** `VALUE_STOP` lists the trailing decorations the value regex must terminate on (` (type:`, ` (pool size:`, ` ✅`, etc.) but is missing the `LLM (Fallback, Rotated)` line's actual suffix `' (from N options)'`. The LLM regex therefore captures the suffix as part of the value.
**Evidence:** Direct repro:
```python
classify_line("LLM (Fallback, Rotated) → orderId = ORD123456 (from 5 options)")
# returns ('LLM', 'orderId', 'ORD123456 (from 5 options)')
```
On the live exec log under `logs/trainticket_twostage_test/trainticket_test_execution.log`, **46 of 46 LLM-classified provenance rows carry the polluted suffix.** Subsequent join in `validate_d4` against `inputs.csv` (whose value is just `ORD123456`) misses every one of them. Concrete output: `provenance.csv:34` reads `accountId,98779e1f-8cce-4435-9ff4-81411a9d9bd5 (from 6 options),LLM`.
**Impact on metric:** D4's `llm` bucket count is artificially zero; values that should be classified `LLM` are reclassified `UNKNOWN` (best case) or `NEGATIVE_FAULT` (when finding #2 also fires). SFHR conservative is unaffected (LLM is excluded either way), but the `by_classification.llm` counter is wrong, and the per-parameter breakdown is misleading.
**Suggested fix:** Add ` \(from ` to `VALUE_STOP`:
```python
VALUE_STOP = r"(?: \(from | \(type:| \(pool size:| ✅| ⚠| ❌| - LOCKED| \(step | \[InvalidType:|$)"
```
**Test gap:** `tests/test_provenance.py` lines 87-91 test `LLM (Fallback, Rotated)` but the test fixture omits the `(from N options)` suffix, which is precisely what real lines have. Add a fixture line ending with ` (from 5 options)`.

---

### Finding #2 — `mine_provenance` first-seen map collapses positive and negative provenance for the same (param, value) [High]
**File:** `debug/inputs/scripts/mine_provenance.py:179-185`
**Issue:** The (parameter, value) key is non-injective: the same value can be drawn by a positive variant (`Shared Pool` log line) and injected as a fault in a negative variant (`Negative Test [InvalidType: ...]`). The upgrade rule promotes a `SHARED_POOL_DRAW` entry to `NEGATIVE_FAULT` whenever a later negative-test line is observed for the same (param, value), so the *positive* variant's row in `inputs.csv` joins to a `NEGATIVE_FAULT` provenance label and is then classified as a "negative" hit in D4 — even though the input row is positive.
**Evidence:** In the live run, `accountId='\`\`\`'` was first emitted as a positive `Shared Pool (Step 1) → ts-admin-order-service accountId = \`\`\``, then later as `✅ Negative Test (Round-Robin) → accountId = \`\`\` [InvalidType: SEMANTIC_MISMATCH]`. The current map ends with `('accountId', '\`\`\`')` → `NEGATIVE_FAULT`. The positive variant in inputs.csv (test_kind=positive) then gets classified as `negative` in `d4_per_row.csv`. Six positive ID-typed rows in the live data are mis-attributed this way; the bug becomes load-bearing once Finding #1 is fixed because more positive variants will land on `'value' == '\`\`\`'`-style ambiguous values.
**Suggested fix:** Either drop the `→ NEGATIVE_FAULT` upgrade entirely (keep the original `SHARED_POOL_DRAW` for the joined positive variant), or — better — emit a richer provenance file that records the *set* of provenance labels seen for each (param, value), and let `validate_d4` pick the label that matches the row's `test_kind`. The minimal fix:
```python
elif first_seen[key] == P_SHARED_POOL and prov in (P_SMART_FETCH, P_LLM):
    # Do NOT upgrade to NEGATIVE_FAULT — that would mis-label positive draws.
    first_seen[key] = prov
```
**Test gap:** No e2e test exercises a (param, value) pair that appears in both a positive and a negative variant. Add one.

---

### Finding #3 — `is_id_like('id')` returns False, silently dropping all literal `id` parameters from D4/D5/D6 [High]
**File:** `debug/inputs/scripts/id_helpers.py:18, 42-48`
**Issue:** `ID_SUFFIX = re.compile(r"^.+?(Id|ID|UUID|Uuid|_id|_ID|_uuid|_UUID)$")` requires at least one character before the suffix (`.+?`). For the parameter literally named `id` (lowercase), neither `ID_SUFFIX` nor `ID_PREFIX` matches, so it is excluded. TrainTicket's admin endpoints use the literal `id` field (per `inputs.csv`).
**Evidence:** In the live run, **1,974 rows have `parameter = 'id'`** (all 887 admin-order, 332 admin-route, 293 admin-train, 242 consign, etc.), and **none** are counted in the D4/D5/D6 denominators. `d4_per_param.csv` lists only `accountId/loginId/orderId`.
**Impact on metric:** D4 denominator is 57; if `id` parameters were included it would be in the thousands. SFHR scores are likely close to correct for the parameters that are counted, but the metric is silently quoting on a denominator ~30× smaller than it should be — and SFHR for `id` (overwhelmingly LLM-fallback values like `train_009`) would be much lower than the headline 0.70.
**Suggested fix:** Treat the literal name `id` (and `ID`, `Id`, `uuid`, `UUID`) as ID-like via a special case:
```python
SHORT_ID_NAMES = {"id", "ID", "Id", "uuid", "UUID", "Uuid"}

def is_id_like(param_name: str) -> bool:
    if not param_name:
        return False
    if param_name in SHORT_ID_NAMES:
        return True
    if ID_SUFFIX.match(param_name):
        return True
    if ID_PREFIX.match(param_name):
        return True
    return False
```
And `normalise_id_stem('id')` should return some sensible stem — perhaps `''` (no constraint) or a sentinel like `'_id'` so it matches the path's nearest noun.
**Test gap:** No test asserts the behavior of the literal `id` parameter. Add one.

---

### Finding #4 — `mine_jaeger.NOISE_TAG_PREFIXES` does not filter `net.*` tags; port numbers leak into the observed-values set [High]
**File:** `debug/inputs/scripts/mine_jaeger.py:53-57`
**Issue:** The prefix list filters `peer.` and `host.` (without a leading dot), but Jaeger tag keys use full dotted prefixes like `net.peer.port`, `net.host.port`, `net.sock.peer.port`. None of those start with `peer.` or `host.`. The result: every numeric port (e.g., `12032`, `18888`) passes the long-numeric regex (`\b\d{5,}\b`) and becomes a candidate observed value.
**Evidence:** Of 1,361 rows in `jaeger_outputs.csv`, **1,062 (78%) come from `net.*.port` keys**:
| Source field | Rows |
|---|---|
| `net.host.port` | 392 |
| `net.sock.peer.port` | 392 |
| `net.peer.port` | 238 |
| `net.sock.host.port` | 34 |
| `http.url` | 264 |
| `http.target` | 41 |

Distinct port values: `{11178, 12031, 12032, ..., 18888}`. Any positive-variant ID-typed input that happens to be a 5-digit numeric in the 11000–19000 range would resolve as "observed in trace" — a false positive for D5.
**Impact on metric:** No false positives in *this* run because no positive ID-typed input is in the port range, but the bug is real. Also, the output file is 4× larger than necessary, and any downstream "what was actually produced as an output" analysis is dominated by network metadata.
**Suggested fix:**
```python
NOISE_TAG_PREFIXES = (
    "internal.", "otel.", "span.", "tracestate", "sampler.",
    "thread.", "process.", "host.", "telemetry.",
    "db.connection_string", "peer.",
    "net.",                                   # NEW: filters net.peer.port, net.host.port, etc.
    "http.url", "http.target",                # rarely identifier-bearing; consider filtering
)
```
Or, more surgically, treat keys containing `port` or matching `net\..+\.port` as noise. Note `http.url` may sometimes carry IDs in the path; consider whether the gain (occasional UUID in URL) is worth the noise (lots of URLs without IDs that still satisfy LONG_NUM_RX).
**Test gap:** No test feeds a Jaeger trace with `net.host.port` tags; the noise filter is untested.

---

### Finding #5 — Smart-Fetch line "Independent" pattern requires `(step ...)` decoration but real lines may not have it [Medium]
**File:** `debug/inputs/scripts/mine_provenance.py:65-67, 60-62`
**Issue:** The three Smart-Fetch sub-patterns (`Step 1`, `Independent`, bare) are tested in order, with the bare `RE_SMART_BARE = r"Smart Fetch → (?P<param>\S+) = ..."`. However, the param/value capture relies on `\S+` for the parameter name; if the param name contains a hyphen (e.g., `boughtDate-end`) or other separator… actually `\S+` allows everything except whitespace, so this is fine.
**Lower-severity concern:** A bare `Smart Fetch →` line during pool fill (e.g., line 173 `INFO SmartInputFetcher:183 - Smart Fetch → endPlace = beijing ✅`) sets the FIRST occurrence to `SMART_FETCH`. Then the variant draws from the pool with `Shared Pool (Step 1) → endPlace = beijing ✅`, which would mark a separate entry `(endPlace, beijing)` → `SHARED_POOL_DRAW`. But because first-seen wins, the bare smart-fetch entry takes precedence — so a value that came from smart-fetch *via* the pool is correctly attributed. Good design, this part. (Not a finding.)
**Real concern:** The `RE_SMART_INDEP` and `RE_SMART_STEP1` regexes don't actually appear in the live log (only `Smart Fetch (Step 1)` and the bare `Smart Fetch →` are emitted, per `grep`). The `Independent` pattern is dead code in this codebase as exercised. Not a bug, just an unused branch.

---

### Finding #6 — `path_helpers.producer_stems` does not split CamelCase service names [Medium]
**File:** `debug/inputs/scripts/path_helpers.py:55-77`
**Issue:** Path segments are lower-cased before tokenization, so `orderOtherService` → `orderotherservice` → strip `service` → `orderother`. A consumer expecting stem `order` won't find a match. The TrainTicket live data has `POST /api/v1/orderOtherService/orderOther/refresh` and `POST /api/v1/adminorderservice/adminorder`, both of which would benefit from CamelCase splitting.
**Evidence:**
```python
producer_stems("POST", "/api/v1/orderOtherService/orderOther/refresh")
# returns {'orderother', 'refresh'} — does NOT contain 'order'
```
**Impact on metric:** D6 CRR for chains where step 1 is `POST /orderOtherService/orderOther` and step 2 consumes `orderId` would mis-classify as a violation, even though `orderother` arguably *does* produce orders.
**Suggested fix:** Split CamelCase boundaries before lower-casing, or insert a token-split on uppercase boundaries:
```python
import re
CAMEL_SPLIT = re.compile(r"(?<=[a-z])(?=[A-Z])")
# ... in producer_stems ...
for raw_seg in path.split("/"):
    if not raw_seg or raw_seg.startswith("{"):
        continue
    seg = raw_seg
    # Camel-split BEFORE lower-case
    parts = CAMEL_SPLIT.split(seg)
    for part in parts:
        for token in TOKEN_SPLIT_RX.split(part.lower()):
            ...
```
**Test gap:** `tests/test_path_helpers.py` does not test CamelCase service names.

---

### Finding #7 — `path_helpers` strips `service` suffix from any token, including `firstService` → `first` (potentially over-eager) [Low]
**File:** `debug/inputs/scripts/path_helpers.py:65-66`
**Issue:** `if token.endswith("service") and len(token) > len("service")` — this matches `firstservice` → `first`, but also `webservice` → `web`, `microservice` → `micro`, etc. For TrainTicket's naming convention this is fine, but a path containing the literal noun `service` is rare to begin with.
**Severity:** Low (matches the documented heuristic intent).

---

### Finding #8 — `validate_d4` `--include-negatives` flag is honored but the negative-variant value join is undefined-by-spec [Medium / DESIGN]
**File:** `debug/inputs/scripts/validate_d4.py:71-74, 109-110`
**Issue:** The CLI flag exists, but per the framework definition (line 119 of `input-quality-measurement-framework.md`), SFHR is over `V_pos(P)` — positive-variant values only. The flag's existence implies a use case (probably "what if we score negatives too?"), but if invoked, the denominator includes negative-variant rows whose values are deliberately bad — virtually all of which will be classified `NEGATIVE_FAULT` (correctly), pushing the metric down. This is honest reporting, but the metric is no longer SFHR-as-defined.
**Suggested fix:** Either rename the flag to `--score-negatives` and clearly document that the resulting number is *not* the framework's SFHR, or remove the flag entirely. Also: `run_metrics.sh` accepts `--include-negatives` and passes it to D1 only, *not* D4 — inconsistent.
**Severity:** Medium (DESIGN — flag exists but its semantics deviate from the framework's headline metric definition).

---

### Finding #9 — `realism_oracle` does no substring/fuzzy matching; multi-word place names with city qualifiers always miss [Medium / DESIGN]
**File:** `debug/inputs/scripts/realism_oracle.py:72-88`
**Issue:** `is_real("New York City")` returns `(False, "unknown")` even though `'new york'` is in the curated list, because `normalise()` lower-cases and collapses whitespace but does no substring match. Live data has `endPlace='Houston, TX'`, `startPlace="Chicago O'Hare International Airport"` — none of these match.
**Evidence:** D7 reports Realism = 0.265 on the live run. Many of the 100 unrealistic values are real cities with disambiguators: `'Houston, TX'`, `'New York City'`, `'Phoenix Sky Harbor International Airport'`, `'Dallas/Fort Worth International Airport'`. By the framework's intent ("matches an entity in KB"), these *should* match.
**Suggested fix:** After the exact-match check fails, attempt a tokenized partial match: split the value on punctuation/whitespace, normalize each token, check whether ALL tokens are in the entity set OR the largest contiguous prefix is. Example:
```python
def _partial_match(self, norm: str) -> bool:
    # Try progressively shorter prefixes against the entity set
    tokens = re.split(r"[\s,/.()-]+", norm)
    while tokens:
        candidate = " ".join(tokens)
        if candidate in self.entities:
            return True
        tokens.pop()
    return False
```
**Severity:** Medium (DESIGN — but materially understates the realism metric on the live data).

---

### Finding #10 — `validate_d4` `examples` field collects non-smart-fetch examples for "worst parameters" but skews including NEGATIVE_FAULT [Low]
**File:** `debug/inputs/scripts/validate_d4.py:141-142`
**Issue:** `if len(agg["examples"]) < 3 and cls != "smart_fetch": agg["examples"].append({"value": value, "provenance": provenance})` collects up to 3 non-SF examples. When the bug in Finding #2 fires, those examples include `provenance: NEGATIVE_FAULT` for what are actually positive variants. The `examples` field then shows the user "for accountId, here's a negative-fault example", which is misleading.
**Suggested fix:** Filter out `NEGATIVE_FAULT` from the examples, or — once Finding #2 is fixed — this becomes moot.

---

### Finding #11 — `validate_d6` does not import `path_helpers`'s producer-set construction defensively; missing http_method in inputs.csv yields empty stem set silently [Low]
**File:** `debug/inputs/scripts/validate_d6.py:86-87, 109`
**Issue:** `producer_stems(method_http, path)` is called even when the method or path is empty. The function returns `set()` for empty path; it does not warn. So an inputs.csv with a missing path silently produces no producers. There's no observable failure.
**Severity:** Low (defensive issue only).

---

### Finding #12 — `RealismOracle.online_calls` counter increments on ALL online attempts including failures, but `online_hits` only on successes; the cache then stores the failure so `unknown` is reported correctly [Low]
**File:** `debug/inputs/scripts/realism_oracle.py:81-87`
**Issue:** When `online=True` and Wikidata fails (network error, rate limit), `_wikidata_search` returns `False`, the cache stores `False`, and the call is counted as "online". The `source` returned is `"online"`, not `"unknown"`. A subsequent run reads the cache (now permanently `False` for that value) and reports `("False", "cache")` — even after the network is back.
**Suggested fix:** When `_wikidata_search` returns `False`, distinguish between "negative match (Wikidata returned no entity)" and "lookup failed (transient)". Don't write transient failures to cache, or write a sentinel so the next run retries.
**Severity:** Low (only matters if network is flaky).

---

### Finding #13 — `realism_entities.txt` contains duplicate entries that get silently de-duplicated [Trivial]
**File:** `debug/inputs/scripts/realism_entities.txt:131, 148; 161, 165; 478, 530; 555, 528; 519, 564; 489, 565`
**Issue:** Several Pinyin syllables/cities are listed twice (e.g., `taizhou` at 123 and 135, `jiang` at 518/553, `li` at 478/566, `shi` at 537/554, `tang` at 502/564, `yichun` at 115/162). The `set()` in `load_offline_entities` silently de-duplicates them. Not a bug per se, but indicates manual list curation noise.

---

### Finding #14 — Tests have an unused `import io` [Trivial]
**File:** `debug/inputs/scripts/tests/test_realism.py:83`
**Issue:** `import io` is unused (line 83 of the test). Cosmetic only.

---

### Finding #15 — `mine_provenance` does not capture `Smart Fetch (Step 2)` / `(Step 3)` lines if/when they appear [Low / forward-compat]
**File:** `debug/inputs/scripts/mine_provenance.py:60-62`
**Issue:** `RE_SMART_STEP1` is hard-coded to `Step 1`. If the codebase emits `(Step 2)` lines in future runs (e.g., for two-stage step-2 input resolution), they would not be classified as Smart-Fetch, and the bare `Smart Fetch →` regex (which uses `\S+` for param) might also miss them.
**Suggested fix:** Generalize:
```python
RE_SMART_STEP = re.compile(
    r"Smart Fetch \(Step \d+\) → \S+ (?P<param>\S+) = (?P<value>.*?)" + VALUE_STOP
)
```
**Severity:** Low (no live runs emit Step 2; forward-compat).

---

### Finding #16 — D6 producer-stem heuristic may treat GET as a producer; SemanticDependencyRegistry on the Java side conflates GET/POST too, but a stricter view would penalize tests that consume IDs without an upstream POST [Medium / DESIGN]
**File:** `debug/inputs/scripts/path_helpers.py:46-50` (docstring acknowledges this)
**Issue:** The docstring is honest: "Does NOT distinguish strong (POST) from weak (GET) producers — any operation whose URL touches a resource is treated as a candidate producer of that resource's ID." This means a sequence `GET /orders → GET /orders/{orderId}` is treated as fully resolved (the first GET "produces" `order`), even though no real ID was created in step 1.
**Severity:** Medium (DESIGN). Acknowledged in code comments. Tightening to require POST/PUT for "strong" production is the right next step. CRR is already at N/A on this run, so the lenience doesn't materially affect output here.

---

### Finding #17 — `validate_d4` reports `worst_parameters` sorted by `sfhr_conservative` but only for parameters with `total >= 3`; tiny pools are silently excluded from the worst-list [Low]
**File:** `debug/inputs/scripts/validate_d4.py:191-205`
**Issue:** The `if v["total"] >= 3` filter excludes parameters that appear ≤ 2 times. The intent is to avoid noise, but in a small-run scenario (e.g., 1 test per operation), all parameters with total=1 vanish from the worst-list — including legitimate fully-broken parameters.
**Severity:** Low (DESIGN; threshold is reasonable but undocumented).

---

### Finding #18 — `RealismOracle.is_real` cache priority order is correct (offline → cache → online) but the `(False, 'unknown')` return for offline+no-online has no audit trail [Trivial]
**File:** `debug/inputs/scripts/realism_oracle.py:81-88`
**Issue:** When offline mode misses and online is disabled, the return is `(False, 'unknown')`. The `unknown` label is reported in `d7_per_row.csv` but the JSON summary's `by_lookup_source` counter doesn't separate "offline-miss" from "cache-miss". Minor reporting nit.

---

### Finding #19 — Tests do not exercise the Wikidata online code path (correctly — they avoid network), but the `_wikidata_search` failure mode is also not tested via mocking [Low]
**File:** `debug/inputs/scripts/tests/test_realism.py`
**Issue:** No test injects a fake `urllib.request.urlopen` to verify "network down → False" or "JSON unparseable → False" behavior. The error-handling code is unverified.
**Suggested fix:** Add a test that monkey-patches `urllib.request.urlopen` to raise `URLError` and asserts `_wikidata_search` returns `False`.
**Severity:** Low.

---

### Finding #20 — `validate_d6` walks `step_indices = sorted(by_step.keys())` but does not dedupe path/method per step (e.g., if a step has multiple parameters, each row independently calls `producer_stems`) — minor inefficiency, no correctness issue [Trivial]
**File:** `debug/inputs/scripts/validate_d6.py:82-109`
**Issue:** `producer_stems(method_http, path)` is computed once per step (line 109) — that's correct. The earlier inner loop `for r in step_rows` only checks consumers, not producers. Good. (Misread on first pass; not a finding.)

---

### Finding #21 — `validate_d4` and `validate_d5` write `worst_parameters` JSON entries whose dict iteration order is not stabilized; deterministic output is implicitly relied on but not guaranteed in older Pythons [Low]
**File:** `debug/inputs/scripts/validate_d4.py:191-205, validate_d5.py:126-139`
**Issue:** Python 3.7+ guarantees dict insertion order, but the input dict is built from `defaultdict` populated in CSV-row order. Re-running the pipeline twice produces identical output (verified — `cmp` reports no difference). However, if the input row order changes (e.g., concurrent writes, parallelized mining), the worst_parameters list could shift. The `sorted(per_param.items())` on line 159 of d4 sorts by parameter name for the per-param CSV, but the worst-list JSON sorts by sfhr_conservative *with no tie-breaker*, so two parameters with identical SFHR could swap positions.
**Suggested fix:** Add a secondary sort key:
```python
sorted(..., key=lambda x: (x["sfhr_conservative"], x["parameter"]))
```
**Severity:** Low (only matters under non-deterministic input ordering).

---

### Finding #22 — `mine_provenance` does not handle `Smart Fetch → param = NO_VALUES_GENERATED ✅` followed by `(value: ...)` — sentinel detection happens AFTER the value is captured [Trivial]
**File:** `debug/inputs/scripts/mine_provenance.py:97-98, 137-138`
**Issue:** The sentinel set `{"NO_GOOD_MATCH", "NO_VALUES_FOUND", "NO_VALUES_GENERATED"}` is checked correctly: if the captured value matches a sentinel, the line is rejected as not a real hit. Good. However, the `LLM` regex does NOT have a similar sentinel check — if an LLM line ever emits one of these markers, it would be recorded as a real LLM value (which is wrong, but unobserved in this log).
**Severity:** Trivial.

---

### Finding #23 — `mine_jaeger` records short alphanumeric IDs only when the tag KEY ends in `id` or `uuid`, but the regex `[A-Za-z0-9_\-]{2,64}` is generous (would match arbitrary 2-character strings) [Low]
**File:** `debug/inputs/scripts/mine_jaeger.py:150-153`
**Issue:** `re.fullmatch(r"[A-Za-z0-9_\-]{2,64}", sval)` matches any 2-char alphanumeric. So a tag with key `db.id = ok` would record `'ok'` as an ID. The 2-char minimum is too low for IDs.
**Suggested fix:** Bump the minimum to 4 or 5: `r"[A-Za-z0-9_\-]{5,64}"`.
**Severity:** Low.

---

### Finding #24 — `validate_d7.is_nlp_param` may over-match parameters like `accountName`, `productName`, `firstName` — author calls this out in the prompt [Medium / DESIGN]
**File:** `debug/inputs/scripts/validate_d7.py:32-56`
**Issue:** The pattern `r"^name$|Name$"` matches every CamelCase ending in `Name`: `accountName`, `productName`, `firstName`, `lastName`, `displayName`. Whether these should count as "NLP-typed" is debatable. `firstName` should match real first names (covered by curated list), but `productName`/`displayName` are essentially open-string and shouldn't count.
**Suggested fix:** Treat only `name`, `firstName`, `lastName`, `fullName`, `contactsName` (the framework's intended slate) as NLP-typed, and explicitly anti-pattern out `productName`, `displayName`, `accountName`. Use an opt-in allow-list rather than the current pattern-then-anti-pattern approach.
**Severity:** Medium (DESIGN — but the heuristic is a known weak point).

---

### Finding #25 — `run_metrics.sh` `--include-negatives` flag is propagated to D1 only, not D4; CLI flag inconsistent [Low]
**File:** `debug/inputs/scripts/run_metrics.sh:135`
**Issue:** `$INCLUDE_NEG` is only used on the D1 invocation (line 135). D4 always defaults to "exclude negatives". Inconsistent.
**Suggested fix:** Decide whether `--include-negatives` is a per-validator flag or a global flag, then propagate it everywhere (D1, D4) — or remove from D1 too. See also Finding #8.

---

### Finding #26 — `id_helpers.normalise_id_stem('Id')` returns None (correct), but `normalise_id_stem('aId')` returns `'a'` — a single-character stem that won't match any path noun [Low]
**File:** `debug/inputs/scripts/id_helpers.py:91-96`
**Issue:** `normalise_id_stem('aId')` → `'a'`. Then in D6, when this is checked against producer stems (which require length ≥ 3 per `path_helpers.py:72`), it never resolves. The stem is "valid" but useless.
**Suggested fix:** Bump the minimum stem length in `normalise_id_stem` to mirror `path_helpers`:
```python
stripped = ID_SUFFIX_STRIP.sub("", param_name).rstrip("_")
if len(stripped) < 2:
    return None
```
**Severity:** Low (no live data hits this; defensive).

---

### Finding #27 — `tests/test_validators_e2e.py:test_d6_id_input_resolution_rate` accepts that step 1's `someField` (non-ID) does NOT make `'order'` a producer — but the test passes because step 1's PATH `/api/v1/orderservice/orders` produces `'order'` regardless of what parameter the row has [Low]
**File:** `debug/inputs/scripts/tests/test_validators_e2e.py:291-312`
**Issue:** The test claims to verify "Step 1 produces 'order' but not 'user'" but step 1's actual contribution is via `producer_stems('POST', '/api/v1/orderservice/orders')` = `{'order'}`, regardless of the row's `parameter` field. The test is correct in intent, but the comment misleads — it's the path, not the parameter, that drives producer detection.
**Severity:** Cosmetic / documentation.

---

### Finding #28 — `mine_provenance` summary print uses `dict(sorted(counts.items()))` which is deterministic but does not include zero-value categories [Trivial]
**File:** `debug/inputs/scripts/mine_provenance.py:194-196`
**Issue:** If a category has zero hits (e.g., zero `LLM` lines), it is not printed. Slight loss of information when comparing across runs.
**Severity:** Trivial.

---

### Finding #29 — `mine_jaeger` walks `tag.get("value")` and casts to `str(value)` — booleans become `'True'`/`'False'`, possibly matching nothing in inputs but inflating the observed-set [Trivial]
**File:** `debug/inputs/scripts/mine_jaeger.py:120-125`
**Issue:** A tag with value `True` (Python bool from parsed JSON) becomes the string `"True"`. The long-numeric and UUID regexes won't match, so the value is silently dropped. Good. The leaf-walker (line 96) does the same. Fine.
**Severity:** Trivial — flagging only because Python's `str(True)` is the capitalised form, not JSON's `true`. If anyone tries to compare against a JSON-encoded value, the case wouldn't match. No active bug.

---

### Finding #30 — `realism_oracle.is_real` returns `('online', ...)` source label even for the failure case `(False, ...)`; downstream reporting in `validate_d7` aggregates this in `by_source` indistinguishably from a real online success [Low]
**File:** `debug/inputs/scripts/realism_oracle.py:81-87`
**Issue:** `_wikidata_search` returning `False` (e.g., empty response, network error) caches the False, increments `online_calls`, returns `(False, "online")`. The `by_source` counter in `d7_summary.json` then shows "online: N" but doesn't tell the user how many were genuine matches versus failures. The summary's `online_hits` counter is correct, but the per-row `source` field doesn't distinguish.
**Suggested fix:** Return `(False, "online_miss")` when Wikidata returns nothing, and `(True, "online")` only on success. Or return `(False, "online_error")` on actual exceptions.
**Severity:** Low.

---

## Other observations not rising to "finding"

- **Determinism.** I ran `run_metrics.sh` twice — output JSON differs only in `computed_at` timestamps. Good.
- **Encoding.** `csv.QUOTE_MINIMAL` is used everywhere with `encoding="utf-8"`. Values containing commas, quotes, or newlines are correctly escaped. Verified by inspecting `provenance.csv` row 6 (`accountId,"!@#$%^&*(){}[]|\:;""'<>?,./",NEGATIVE_FAULT`) — the embedded comma and double-quote are properly preserved.
- **`run_metrics.sh` resilience.** Missing exec_log → empty `provenance.csv` → D4 stub JSON. Missing trace_dir → empty `jaeger_outputs.csv` → D5 stub JSON. Both gracefully degrade. Good.
- **Test runner.** `run_tests.py` is a clean stdlib-only harness. 63/63 tests pass; the harness correctly injects `tmp_path` for tests that take it. No complaints.
- **D5 v real data.** As the task description acknowledged, the trace file `traces-1772605095842.json` is from March and unrelated to the current run; D5 reports IDR=0.0 on real data. The script structure is correct; the data mismatch is operational, not a code defect.
- **N/A handling.** All four validators emit `null` (Python None) in the JSON summary when the denominator is zero, with the threshold-pass check correctly handling `None`. Good.

---

## Recommendations summary

| # | File | Severity | Fix complexity |
|---|---|---|---|
| 1 | `mine_provenance.py:57` | Critical | One-line regex addition |
| 2 | `mine_provenance.py:179-185` | High | 2-line if-branch fix |
| 3 | `id_helpers.py:18,42-48` | High | Add SHORT_ID_NAMES set |
| 4 | `mine_jaeger.py:53-57` | High | Add `net.` to NOISE_TAG_PREFIXES |
| 6 | `path_helpers.py:55-77` | Medium | Add CamelCase pre-split |
| 8 | `validate_d4.py:71-74` | DESIGN | Document or remove flag |
| 9 | `realism_oracle.py:72-88` | DESIGN | Add token-prefix matcher |

Total estimated fix effort: ~50 lines of code + 5 new test cases.
