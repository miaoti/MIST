# Cold review C — C2+C3 execution plan @ 980164c, lens: execution feasibility (engineering-lead)

**Verdict: ACCEPT-WITH-CHANGES** — architecture + risk register sound; §5's budget is 2–3× optimistic
against THIS REPO'S OWN documented history; three unbudgeted work packages; one mis-sequenced spike;
~150 tool-hours of the E1 grid as written are vacuous. Executable only after the fixes; recommends a
second machine / cloud burst.

## BLOCKING
**B1 compute math.** E1 = 300 h driven load minimum; M-yield unpinned (only proven generation-driven
datapoint: G1 = ~7.8 h wall for ONE TT run — gen ~5.2 h + pairing/probe ~2.6 h; G1-style ×10 ×TT alone
= ~78 h); M-prevalence "N hours × 6 SUTs" with N unspecified = unbudgetable; E5 ≈ +60 h. Total ≈
**420–540 h ≈ 18–23 days at 100% utilization** — and utilization won't be 100%: the box WEDGED under
concurrent load twice (vmmemWSL 17.3 GB/host-free 0.6 GB relay starvation, ~a day to recover;
gate1 runs #1/#2 wedged the WSL VM at 26 GiB). Fair fixed-budget tool runs must be EXCLUSIVE →
strictly serial; big SUTs are solo tenants (TT forced SS→0). **Fixes:** pin M-yield budget + N in the
plan; re-budget steps 3–4 at 4–6 wk single-box OR add the never-made infrastructure ask (one cloud VM
32–64 GB, ~300–400 machine-hours ≈ $150–600 spot — halves elapsed + removes the WSL-relay SPOF);
budget 2–4 days for an unattended WAVE-RUNNER (timeout, log capture, state reset, dispatch) — no
baseline tooling/orchestration exists anywhere in the repo (verified).

**B2 E1 grid partly vacuous + auth glue unbudgeted.** Specs on disk: TT 11,976 lines (rich); SS 925 /
~18 paths; Boutique 99 lines / 8 paths; Bookinfo 247 lines / 4 GET paths; **TeaStore + OTel-Demo have
NO evaluation/suts/ assets at all** (no spec, no deploy script). research/05 §6 already carries the
fallback ("restrict head-to-head generation to spec-rich SUTs") — the plan ignored its own companion
input. Bookinfo: 5 tools saturate 4 GETs in minutes → 50 h of degenerate zero-variance rows. Auth: TT
Bearer-JWT + SS cookie glue per tool (up to 5 tools × 4 authed SUTs), nowhere in §5; without it
baselines flatline → E1's calibrate-as-strong goal INVERTS into a strawman. **Fix:** two-tier E1 grid
(full 5×10×1h on TT/TeaStore/SS; 3 seeds × 15–30 min on Bookinfo/Boutique/OTel-Demo, saturation
disclosed — keeps Gate-4's letter, saves ~140 h); work items: author TeaStore+OTel-Demo specs; per
SUT×tool auth glue with a smoke-test evaluability gate ("tool reaches ≥1 authed endpoint").

**B3 per-SUT MIST enablement absent.** M-yield needs per SUT: OpenAPI + root-api-registry +
input-fetch-registry + auth wiring + properties + triples + live-verified read-backs. Sock Shop's
enablement took multiple sessions (g3-write-path-enable.sh: 2 live-discovered fixes; per_jvm_cookie;
registries STILL untracked); generation-driven completed only on TT ever; TeaStore/OTel-Demo start
from zero. Realistic: 1–3 days/SUT = 1–2 wk skilled work invisible in "steps 1–2 ≈ days". **Fix:**
named work package in step 2 w/ per-SUT DoD (registry + auth smoke + one end-to-end paired run);
pre-register thin-surface SUTs as oracle/prevalence-only for M-yield.

## MAJOR
- **M1 deploy wave:** Bookinfo live; Boutique has assets; TeaStore = Kieker-default (OTel path needs
  enabling) + registry startup flakiness; OTel-Demo = 15–20 svc + collector stack ~6–8 GB = SOLO
  tenant + built-in loadgen must be disabled. Step-2 realistic total incl. B3: **2.5–3.5 wk**.
- **M2 memory envelope:** G1 succeeded at 26 GiB WSL cap; current budget ~22 GB already produced one
  relay-starvation incident. Restore .wslconfig 26 GB for TT waves; pre-register a tenancy schedule;
  never build images concurrently with a deployed graph; pin ONE TT topology for E1/M-yield (the
  scaled-to-0 cancel-refund subgraph turns fuzzer calls into 20–30 s 503 walls that eat 1 h budgets).
- **M3 E2 operability + trace darkness:** TraceAnomaly = TF-1.x-era research code + needs per-SUT
  normal-traffic training; Tracetest = own server+Postgres+OTLP per SUT; AND both proven SUTs are
  currently TRACE-DARK on the paths that matter (TT quick_start deployed w/o tracing; SS "no traceId
  wired"). Move the R4 spike to STEP 1; commit the fallback pair (naive span-error + Tracetest) as
  baseline w/ TraceAnomaly stretch; add "trace coverage verified on the write path" to each SUT's
  deploy DoD (feeds §8.5-2).
- **M4 F-corpus cost:** each F-fault = fork port + image build + kind load + repoint + live-verify;
  builds died under memory pressure before (succeeded only from Windows w/ warm BuildKit cache).
  ~0.5–1 day/fault initially. Floor ≥6 (F6/F8/F10/F20 + 2), target ≥10; builds strictly off-peak.
- **M5 no seed/state-reset policy:** the comparison template restarts SUTs per run; TT redeploy 20–30
  min × 50 E1 runs = +17–25 h on TT alone; fuzzer-accumulated state contaminates later seeds + MIST
  isolation keys. Pre-register per-SUT reset method (DB-wipe scripts vs rollout restart); write wipe
  scripts in step 2; count in budget.
- **M6 M-prevalence workload sources don't exist for most SUTs** (OTel-Demo ships one; SS upstream
  load-gen; TT none — author; TeaStore browse profile). Name per-SUT source + N in the step-1 freeze.

## MINOR
m1 AutoRestTest needs an LLM key (cost + model pin); MIST's own LLM condition decided + pinned (G1 ran
LLM-off, disclosed). m2 substitution rule if Morest/AutoRestTest installs fail → RestTestGen alternate;
floor "≥4 runnable tools". m3 disk housekeeping per wave (images + fork builds + traces balloon the
VHDX). m4 add per-rater workload estimate to the recruitment ask (≥20 S3 + calibration × 15–45 min ≈
10–30 h each) so compensation is sized honestly.

## Revised timeline (single box, overnight automation; ∥ parallel)
step 0 review 2 d · step 1 freeze + depth surveys + R4 SPIKE (moved) + rater outreach 1 wk (rater lead
2–6 wk ∥) · step 2 deploys + specs + MIST enablement + wipe scripts + orchestrator 2.5–3.5 wk · 3a
S1/S2 + F-builds 1–2 wk (∥ nights) · 3b E1 descoped ~160 h → 1.5–3 wk calendar · 4 M-yield 1–1.5 wk ·
5 M-prevalence 1 wk run + 2–4 wk adjudication (rater-gated ∥) · 6 E2 1 wk (+1 if TraceAnomaly viable)
· 7 E5 3–4 d · 8 E6 + review 1 wk. **Total ≈ 10–13 wk single-box; ≈ 8–10 wk with a cloud-burst second
node + two-tier E1.**

## De-scope ladder (first → last; never below the C2 floor)
1 two-tier E1 (saves ~140 h, keeps Gate-4 letter) · 2 TraceAnomaly/TraceRCA → fallback pair from day
one (research code only if the step-1 spike clears in ≤2 d) · 3 E5 → one SUT ×5 seeds · 4 F-corpus
≥10→≥6 · 5 M-yield seeds ×10 spec-rich / ×3 thin (disclosed) · 6 NEVER cut core-6 from C2 (thin SUTs
contribute S2 benign traps nearly free) — cut E-series depth before SUT count · 7 E3 stays (free,
mined from logs).

**Critical path:** rater recruitment (start today) + the step-2 enablement wave. Highest-leverage
change: cloud-burst second node + two-tier E1 grid.
