# §1.95.0 Raw-artifact INVENTORY — what each seed asset has vs what a rater-facing case needs

**Required per rated case (corpus plan §5 sidecar):** ordered request records (method/path/payload) ·
response records (status + FULL body) · durable-state observations (read-back/probe bodies) ·
relative times. **Verdict per asset below: what exists / what's missing / capture disposition.**
Live facts (2026-07-09): TT quick_start UP (upstream codewisdom:1.0.0 images); kind node cache holds
**fork-built `ts-inside-payment-service` 1.0.2/1.0.3/1.0.4/1.0.5** (the G3 iterations — repoint, no
build needed); NO fork `ts-admin-route-service` in cache (G1 ran on minikube; fork image never
kind-loaded); Bookinfo 0 pods; sock-shop scaled 0.

| candidate case | label | what EXISTS | missing for the sidecar | capture disposition |
|---|---|---|---|---|
| TT adminroute lost-write (S1 genuine) | by-injection | gate1-run3-report.json fault-record: isolationKey, ack status codes, baseline/lastReadback bodies, gate, polls — **pre-pin, freeze-invalid** | request payload+sequence, full ack body; the FAULT (fork `mist.fault.lostwrite.enabled` flag) not in deployed upstream images | **NEEDS FORK BUILD** (hack/build-image.sh ts-admin-route-service + kind load + repoint) — schedule off-peak; OR postpone to step-3a S1 population |
| TT adminroute control (calibration TN/benign-ish) | control | same report's control record | same gaps; NO fault needed | **NOW-capturable** vs live TT (plain write + read-back transcript) |
| TT contacts lost-write (S1 genuine) | by-injection | build-verified only; NO capture | everything + fork flag on ts-admin-basic-info-service | NEEDS FORK BUILD (same wave as adminroute) |
| TT contacts dedupe (S2 benign) | by-docs | seed JSON (specified; provenance null) | everything; no fault needed | **NOW-capturable** vs live TT |
| TT cancel→refund natural `{1,"error"}` (tell-bearing genuine; calibration-only anchor) | by-injection (runtime toggle) | G3 run logs = verdict summaries | request seq (create-paid-order→cancel), full bodies, /account probe values | **NOW-capturable**: repoint ts-inside-payment-service → cached fork **1.0.5**, runtime toggle `faultmode fail`; harness-level transcript capture |
| TT cancel→refund fabricated-ack (S1 genuine, clean-silent) | by-injection | G3 run logs = summaries | same | **NOW-capturable**: same fork image, toggle `fabricatedack` |
| TT createAccount agreement (S1 genuine, body-carrying) | by-injection | G3 agreement logs = summaries | same | **NOW-capturable**: fork 1.0.5 createAccount fabricated-ack toggle |
| SS shipping swallowed-enqueue (genuine, trace-only class) | natural | h2h logs = verdict lines (queue counts inline) | request seq, ack body, broker-count observations as first-class records | needs SS re-warm window (**TT-down or co-residence** — 2.15 tenancy); RabbitMQ runbook (mist:mist user + warm-up) |
| SS carts (S1/S2 depending on leg) | natural | cookie-session flow documented; no transcripts | everything | same SS window |
| Bookinfo ratings benign (S2) | by-docs | seed JSON (specified) | everything | **NOW-capturable after tiny redeploy** (istio samples apply; scale ratings→0 for the degraded leg) |
| 2 eligibility-screen cases | unambiguous pair | — | everything | mint from the NOW-capturable set (one clear genuine [cancel fabricated-ack], one clear benign [Bookinfo ratings]) — DISJOINT from calibration per m7 |

**Sizing: 6–7 cases NOW-capturable** (adminroute-control, contacts-dedupe, cancel-natural,
cancel-fabricated-ack, createAccount-agreement, Bookinfo-benign [+SS pair when its window opens]) —
enough for B4 fixtures + the first calibration tranche + both eligibility cases. The 2 fork-flag TT
cases (adminroute/contacts lost-write) move to a **fork-build wave** (off-peak; or fold into step-3a
population). All captures at MIST pin 7d69de9; fork side pinned a1767ab3 (inside-payment 1.0.5 =
that branch's build). Pre-pin July artifacts: reference-only (provenance notes), never case material.

**Capture ownership (B1-fix-3, decided):** harness-level transcript capture — a thin Python driver
issues the scripted requests + read-backs and writes the sidecar directly; MIST is NOT in the loop
for seed captures (keeps the pin untouched; the oracle's verdicts for these calibration cases are
already known by construction/design — the sidecar records BEHAVIOR, not verdicts).
