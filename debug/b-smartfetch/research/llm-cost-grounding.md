# LLM-on Cost Grounding — mined evidence for the §9-A.4a per-run envelope

> Produced 2026-07-21 on the user's direction ("去代码或历史 run 里挖一下坐实数据"), targeting
> Risk #1 of the v4 plan review: the ≤75-min cold-A1 envelope was a bottom-up estimate with
> "no LLM-on measured precedent" (Round-4 C-R4-3). This memo replaces that with mined data:
> **239,895 real LLM calls** (2025-08 → 2026-05-13) recovered from
> `C:\Users\miaot\Github\Rest\logs\llm-communications\` (256 files, 837 MB, user-local,
> NOT in any git repo), plus code-verified call-chain constants from the current tree.
>
> Miner + committed outputs: `llm-cost-grounding/mine_llm_latency.py`,
> `llm-cost-grounding/aggregate.json` (all backend/model/month aggregates),
> `llm-cost-grounding/per_file_summary.csv` (one row per log session).
> The per-call CSV (239,895 rows) is regenerable:
> `python mine_llm_latency.py <log-dir> <out-dir>`.
>
> Status of R4-3 after this memo: the *timing* of the current code on the current box under
> a-main contention is still G2's to measure (G2 stays a HARD gate). What is no longer open
> is the per-call latency assumption, the call-count order of magnitude, and the failure
> modes the envelope must price. Risk #1 moves from "unmeasured assumption" to
> "measured center + two known operational failure modes".

All numbers below computed by the committed miner from the raw logs; nothing hand-copied
from older docs. Latency = the logger's own `Response Time` field
(`LLMCommunicationLogger.java:202-215`, wall-clock around the backend call).

---

## 1. Data provenance and why it is clean

- The old fork (`Github\Rest`, pre-MIST-1.6) has **no `LLMCallCache`** (verified: zero hits
  for the class in that tree), and the mined distribution has **0 calls under 100 ms**
  (`n_under_100ms=0` in every aggregate bucket) — so no cache-replay contamination; every
  mined latency is a real backend round-trip.
- Backend identification is per-call from the log's `Endpoint:` field (api.deepseek.com vs
  localhost:11434), not inferred from config files.
- Eras: 2025-08→2026-01 = older local models (gemma3:4b, llama-3-8b, qwen3:8b/30b) —
  context only; **2026-03→05 = qwen2.5-coder:14b local** (the D4-era workhorse);
  **2026-05-05→05-13 = deepseek-chat hosted** (the sprint's intended primary).
- Miner limitations (disclosed): prompt/response sizes are char counts (token ≈ chars/4
  heuristic; 2025-era logs truncated at 10k chars — the `Original length` marker is used
  when present); per-session `wall_span` runs first-request→last-response, so idle time
  between calls counts toward span and `llm_share` is a *lower bound* on LLM's share of
  active time.

### Session ↔ run-of-record mapping (D4 dirs are epoch-ms of MistRunner start)

| D4 measurement dir | epoch → local | log session | calls | failed | notes |
|---|---|---|---:|---:|---|
| 1777065076883 | 04-24 16:11 | 20260424-161112 | 5,969 | 0 | qwen; pre-D4-instrumentation run |
| 1777348134277 | 04-27 22:48 | 20260427-224852 | 8,632 | 0 | qwen; a second 8.8k-call session (22:34) ran the same night — see §5 F5 |
| 1777780533352 | 05-02 22:55 | 20260502-225524 | 11,433 | 0 | qwen; SFHR-peak run (75.91%) |
| 1778001841606 | 05-05 12:24 | 20260505-122355 | 13,033 | **13,033** | **DeepSeek 100%-fail session** — see §4 |
| 1778039778981 | 05-05 22:56 | 20260505-225616 | 7,871 | 0 | DeepSeek healthy; = the "22:56 run" whose HTTP/2 stall is cited in `LLMService.java:113-116` (max 193 s in the mined data) |

Post-D4 DeepSeek sessions also mined: 20260508-152629 (6,283 calls, 0 fail),
20260513-134405 (1,173, 0), 20260513-180446 (252, 1).

---

## 2. Headline: the per-call latency assumption is CONFIRMED for DeepSeek

**deepseek-chat, successful calls only, n = 15,626** (May 2026, this box, WSL→hosted API):

| p50 | p90 | p95 | p99 | max | mean |
|---:|---:|---:|---:|---:|---:|
| **1.78 s** | **3.51 s** | 4.41 s | 15.6 s | 193 s | **2.34 s** |

The plan's "~2-4 s/call" band (§9-A.4a) brackets p50-p90 almost exactly. The tail is real
but thin (p99 15.6 s; 6/29,157 calls over 120 s; max = the documented HTTP/2 stall, now
capped by the 180 s callTimeout + 185 s watchdog, `LLMService.java:117-125,437-452`).

**Fallback-path cost (local qwen2.5-coder:14b), n = 148,041** — this is what a call costs
when the per-call Ollama cascade (`LLMService.java:244-266`) fires, and what the whole run
costs if DeepSeek is dead (§4):

| p50 | p90 | p95 | p99 | mean |
|---:|---:|---:|---:|---:|
| 5.43 s | 18.7 s | 24.6 s | 49.1 s | **8.60 s** |

i.e. **2-5× the DeepSeek band**, with a documented contention mode on top (§5 F5).

Token/dollar grounding: DeepSeek-era mean prompt ≈ 1,545 chars + response ≈ 235 chars
≈ **~450 tokens/call** (chars/4). At the data-derived call counts (§3) a scoped run is
~0.1-0.4 M tokens, the ~148-run matrix ~15-60 M tokens — at DeepSeek's per-M rates that is
**tens of dollars total**, an order below the plan's "low hundreds" (which came from a
2-8 M-token/run guess ≈ 3-5k tokens/call; measured is ~450). The plan's $ risk is
conservative by ~5-20×; latency remains the only real cost axis, as §9-A.4a says.

---

## 3. Call-count grounding: full-run precedents and the scoped-unit estimate

Full two-stage TT suites (~15-32k mined input values) made **7.9k-11.4k LLM calls each**:
0.34-0.36 calls per input value (7,871/23,189 on 05-05; 11,433/32,198 on 05-02). LLM time
totalled 5.8 h (DeepSeek) to 15.5-34 h (qwen) per full run — `llm_share` 0.27-0.76 of
span — and the archive's longest session (20260412) spans 96.9 h with 48.4 h of LLM time.
April 2026 alone contains **267.6 h of pure LLM wall-clock** — the measured reality behind
both the LLM-off choice in run22/MYC and the sprint's scoped-unit rule.

Code-verified cold-start chain (current tree, post-audit-F38): per parameter with an empty
registry — 1 apiDiscovery (`SmartInputFetcher.java:600`) + per whitelisted suggested
service (≤3): 1 endpointSelection, +1 forced on a deterministic NO_GOOD_MATCH (no retry),
≤3 attempts only on *transient* failures (`:4165-4190`), then 1 directValueExtraction
(+1 semanticFieldMatching fallback) per fill. **Typical cold cost 3-6 calls/parameter ✓**
(the plan's band); worst case ~10-11 only in a transient-failure storm. Warm/registry-hit
fills are 1-2 calls ✓. Two run-level multipliers the estimate must keep: the diverse-value
cache is cleared per scenario (`resetValueRotation`, F26) so extraction repeats per
scenario; discovery persists within the run once it succeeds (registry mappings, so the
burst is one-time).

Scoped cold-A1 TT unit (57 registry params; ≈5 scenarios × ~10 variants ≈ 50 tests):
discovery burst ≈ 57 × (2-4) ≈ **115-230 calls** (one-time) + extraction/generation
≈ 150-550 (scenario-repeated fills + generation fallbacks at percentage=1.0) →
**~250-800 calls/run, center ~400-500** — inside the plan's 0.4-2.4k band but in its
**lower half**; the 2.4k upper edge would require a retry storm (§4), not normal operation.
(A G2 measured count >1.2k should therefore trigger *investigation*, not just re-sizing.)

---

## 4. The two operational failure modes the envelope must price

**(a) Auth/config fast-fail storm — the measured worst case.** Session 20260505-122355:
**13,033/13,033 calls failed** at a constant ~320 ms (p50 320, p99 498, max 1,055 ms) over
**9.95 h** — a session-level rejection (the pattern `LLMService.java:653-659` warns about:
missing/invalid key → "thousands of silent 401s"), not load-dependent throttling
(zero successes; failures are fast and flat). The old fork had no cascade → every fill fell
to template/placeholder values. **This session IS D4 run 1778001841606** — the inventory's
"suspicious `llm: 0`" run with `FALLBACK_loginId_11` pool entries and the vacuous 100%
upper bound now has its mechanism. Aggregate-level consequence: the naive "DeepSeek
fail_rate 46.4%" in `aggregate.json` is **entirely** this session + a 495-call sibling
(22:36); healthy sessions run at ~0-2%.
- *Current-code consequence is different but worse for validity:* the per-call cascade
  would silently serve every one of those calls from Ollama — the run completes, 1.5-4×
  slower, and the "DeepSeek arm" cells would actually measure qwen2.5 behavior with
  nothing in the comm log distinguishing them (§5 F2).

**(b) Hosted-stream stall tail.** Max 193 s observed (the `LLMService` comment's "22:56
run", present in the mined data); now bounded at 180/185 s by callTimeout+watchdog, then
+1 Ollama call. At the §3 call counts, p99-tail + 1-2 stalls cost ≈ +2-8 min/run — real,
but inside the envelope's slack.

---

## 5. Envelope recompute and findings for G1/PROTOCOL

**Envelope arithmetic** (LLM term = calls × effective latency; non-LLM term for a ~50-test
scoped unit bracketed 5-25 min from: run22's amortized 1.56 s/test
(`paper/tool-demo/REVIEW_ISSTA_2026.md:211` — 15,036 tests / ~6.5 h, LLM-off), the MYC
1-h-wall-budget legs (LLM-off, larger suites, DI on) and the 30-min E5 caps; where the
sprint unit sits in that bracket depends mainly on the DI-oracle mode the sprint elects —
a knob §9-A.4a should pin):

| Scenario | calls | eff. latency | LLM min | + non-LLM | verdict vs ≤75 min / 2 h cap |
|---|---:|---:|---:|---:|---|
| DeepSeek healthy, center | 500 | 2.34 s mean | 19.5 | 25-45 | **holds with ~2× margin** |
| DeepSeek healthy, high-calls | 800 | 2.34 s | 31 | 36-56 | holds |
| p95-pessimistic | 800 | 4.41 s | 59 | 64-84 | ~75 min line; well inside 2 h |
| All-Ollama (dead key, silent) | 500 | ~8.9 s | 74 | 79-99 | **busts 75; inside 2 h** |
| All-Ollama + high-calls | 800 | ~8.9 s | 119 | 124-144 | **busts 2 h → discard-and-rerun** |

Fallback-share tolerance: at 500 calls the 75-min target survives up to f≈0.7 fallback
share; at 800 calls only f≲1/3. The binding product is calls × effective-latency — which
is exactly what the G2 cost criterion already measures. **Bottom line: for a healthy
DeepSeek backend the ≤75-min envelope is comfortably plausible at the data-derived call
counts; the envelope's real threats are operational (dead-key silent fallback, retry
storms), not the latency assumption.** The ≥8 runs/day floor needs only ~3.3-5.3 h/day of
run time at the healthy center — inside the 7-11 h/day capacity model even with a-main
contention.

**Findings to absorb (F1 blocking-for-PROTOCOL; F2-F6 scoped riders):**

- **F1 — `LLMCallCache` seeded-read confound (not in the plan; plan has zero mentions).**
  Current code: *every seeded run reads the LLM response cache by default*
  (`LLMService.cacheReadEnabled()`, `LLMService.java:321-330`: explicit
  `mist.llm.cache.read` else `random.seed != null` → read), writes by default
  (`:301-308`), key = (model, backend, prompts, temperature, maxTokens) —
  **the seed is NOT in the key**, and the seed gate pins temperature for all seeded runs
  (`applySeedGate`, `:186`), so *different seeds and different arms produce identical keys
  for identical prompts*. All sprint runs are seeded ⇒ by default r2+ rounds AND
  sibling seeds AND sibling arms replay each other's LLM responses at ~0 ms from
  `.mist/llm-call-cache.json`. That (i) manufactures exactly the cache-warming r1→r2 gain
  the RQ3a scope statement forbids, (ii) collapses cross-seed variance on the LLM axis,
  (iii) leaks values across arms through a shared file. This is not hypothetical: the
  Windows working dir's `.mist/llm-call-cache.json` holds **7,315 entries, last written
  2026-07-15**. → PROTOCOL must pin `mist.llm.cache.read=false` for every banked run,
  decide `mist.llm.cache.write` (recommend false, or per-run isolated `.mist/` dirs), and
  extend B4's between-r-round invalidation to name `.mist/` explicitly. Cost consequence:
  with read pinned false, *every* seeded run pays full LLM latency — the §5 table already
  assumes this (it must).
- **F2 — fallback-served calls are invisible in the comm log.** `logRequest` records the
  *primary* model name once; an Ollama-served result is logged under the same context
  (`LLMService.java:198-266`). E4's token/cost accounting needs a per-call `served_by`
  field (or at minimum count the `"[LLM] Ollama fallback succeeded"` mist.log lines
  per run) — otherwise a degraded run's cells are indistinguishable from healthy ones.
- **F3 — DeepSeek preflight + fail-fast abort.** The 9.95-h all-fail session is the
  precedent: a dead key today would not fail the run, it would silently re-platform it
  onto qwen (§4a). Add to the run harness: N-call preflight before each banked run +
  abort/flag when primary-failure share crosses a threshold early. Cheap rider on E4/E8;
  G2's smoke should demonstrate it firing (kill the key deliberately).
- **F4 — $ estimate correction (good news).** Measured ~450 tokens/call vs the plan's
  implied 3-5k: sprint total ≈ tens of $, not low hundreds. Keep the E4 per-run token
  accounting as the precise source.
- **F5 — one-JVM-at-a-time for overnight batches.** The only two same-night concurrent
  sessions in the archive (04-27 22:34 + 22:48, both ~50 h span) show qwen p50 ≈ 12.0-12.2 s
  vs 4.9-6.7 s in every solo session that month — local-model latency roughly doubles
  under co-residency (strong precedent, not a controlled experiment). Hosted-primary runs
  mostly decouple, but fallback stretches do not. The per-run-banked scheduler should be
  explicitly serial (one MIST JVM + one Ollama consumer at a time).
- **F6 — G2 checks the measured call count against the §3 center (250-800).** Keep the
  plan's 0.4-2.4k band as the outer gate, but treat >1.2k as "diagnose retry storm first,
  re-size second" (a storm inflates calls *and* latency together — re-sizing variants
  would mask it).

---

## 6. What this memo does NOT settle (G2 remains a hard gate)

1. No timed LLM-on run of the **current** code exists (June-17 WSL run: LLM service up,
   `smart.input.fetch.enabled=false`) — the May data is old-fork call *structure*
   (post-audit deltas are modest and mostly reduce calls: F38 retry cap, F29/F30 dead
   prompts, no-fabrication drops), so G2's first cold-A1 timing is still the first
   end-to-end measurement of *this* pipeline, on *this* box, under a-main contention.
2. Latency snapshot is 2026-05 (2.5 months old, n=15.6k). A 10-minute re-confirmation
   (~20 live calls) fits naturally into the G2 smoke; not done here (spends the user's
   key autonomously for marginal n).
3. The scoped-unit non-LLM term (5-25 min) is bracketed, not measured at exactly the
   50-test shape; its width is dominated by the un-pinned DI-oracle-mode knob.
4. chars/4 token heuristic; May runs ran registry-warm TT (cold-A1's discovery burst is
   code-derived, not replayed); per-session `llm_share` is a lower bound (idle time in span).
5. The 837 MB log archive is user-local and unversioned (`Github\Rest\logs\`) — it is now
   load-bearing evidence for the paper's cost story and should be preserved (user-side
   call whether to snapshot it; not copied into the repo autonomously).
