#!/usr/bin/env python3
"""Traced-capture wave: mechanical trace-oracle scorer (traced-capture-wave-plan.md rev 2, T8).

COMMITTED BEFORE the first real capture (frozen selectors below; debugged ONLY on canary/throwaway
traces). Scores TWO comparator columns on an exported Jaeger v2 trace JSON; NEVER mist_trace_shape
(no typed contract -> hand-derivation banned, plan T9).

  usage: trace_score.py <case_id> <trace.json>

SELECTION AMENDMENT (2026-07-10, committed BEFORE scoring any leg; disclosed): the raw Jaeger window
query also returns 1-span INTERNAL scheduler traces (the agent instruments NacosWatch$$Lambda.run,
observed on the canary + the first raw export). Selection is therefore implemented as: keep only traces
containing a SERVER-kind span of the case's entry service (the mechanical meaning of "entry
service+operation" selection), THEN require exactly one. Verdict semantics untouched.

Pinned semantics (reconciliation T6/T8, B-M1):
- tracetest_presence = EXISTENCE-ONLY assertion at cross-service HTTP-span granularity:
  a SERVER-kind span of the case's dependency service whose operation matches the frozen fragment.
  The error/status axis belongs exclusively to naive_span_error.
- naive_span_error   = ANY error span (error=true or otel.status_code=ERROR) among the 7 instrumented
  services in the selected trace. Mechanical. NO exclusions ever.
- DB-granularity REPORT (not a verdict): counts DB-client spans (jdbc/mysql/mongo) per service -- the
  T6 mandatory disclosure datum (control leg shows the write span; fabricated-ack leg lacks it).
- DB-granularity PRESENCE verdict (E2 plan rev 2.1, C2; FROZEN + committed BEFORE the E2 run): an
  EXISTENCE assertion at DB-CLIENT-span granularity -- a DB-client span (db.system in DB_SYSTEMS) of the
  case's "db_presence" service whose operation matches the frozen fragment. This is the STRONGEST trace
  comparator and it CATCHES the fabricated-ack (the skipped INSERT is absent on the fault leg), so the
  head-to-head reports all THREE trace configs (naive MISS / service-map-presence MISS / DB-span CATCH).
  The point is specification-locality: this verdict exists only because the author pre-specified an
  assertion on the EXACT skipped durable write; the coarser service-map presence (drawback SERVER span,
  present on both legs) misses. only cases carrying a "db_presence" selector emit this verdict.
- Agent pin: opentelemetry-javaagent 1.33.6
  sha256 055c4fe4c67b0eed944d09e4e130d79255ad226929d11cdc71286d6ba67e4fdb; 1.x semconv
  (http.target/http.method/http.status_code). Selector authoring cost: ~12 min for the 4-case table.

Frozen per-case selector table (T8; entry service asserted present, presence selector per case):
  case                                entry svc                      presence: svc :: op-fragment
  TT-cancel-refund-*                  ts-cancel-service              ts-inside-payment-service :: drawback
  TT-createaccount-*                  ts-inside-payment-service      NOT_APPLICABLE (in-process persistence, T7)
  TT-adminroute-*                     ts-admin-route-service         ts-route-service :: routeservice
  TT-adminbasic-contacts-*            ts-admin-basic-info-service    ts-contacts-service :: contact
"""
import json, sys

INSTRUMENTED = {
    "ts-cancel-service", "ts-inside-payment-service", "ts-order-service",
    "ts-admin-route-service", "ts-route-service", "ts-admin-basic-info-service",
    "ts-contacts-service",
}

# TENANCY-WINDOW EXTENSION (plan rev-2 T4, committed BEFORE the first real capture; name strings
# bound from control/canary traces only — semantics pinned in the plan):
# - presence selectors carry a span KIND: "server" for HTTP dependencies, "consumer" for AMQP
#   (sockshop queue-master consume = the required-durable-effect span).
# - error rule extended for Envoy-emitted spans: error=true OR otel.status_code=ERROR OR
#   http.status_code >= 500 (mechanical; applies uniformly).
# - naive scope becomes PER-CASE ("scope"); TT cases keep the module INSTRUMENTED set.
# - optional per-case entry operation fragment ("entry_op") narrows the entry-server match
#   (sockshop: POST /orders). Canary-bound names: Envoy = <svc>.<ns> (productpage.default,
#   ratings.default...); javaagent = plain OTEL_SERVICE_NAME (orders, shipping, queue-master).
SELECTORS = {
    "TT-cancel-refund":        {"entry": "ts-cancel-service",           "presence": ("ts-inside-payment-service", "drawback"),
                                # E2/C2 (FROZEN pre-E2-run): the DB-CLIENT INSERT of the drawback Money row
                                # in inside-payment. Present-control / absent-fault = the DB-granularity
                                # trace comparator that CATCHES (op fragment "insert"; the P3 control-leg
                                # canary must show this span or the fault no_flag is declared uninformative).
                                "db_presence": ("ts-inside-payment-service", "insert")},
    "TT-createaccount":        {"entry": "ts-inside-payment-service",   "presence": None},
    "TT-adminroute":           {"entry": "ts-admin-route-service",      "presence": ("ts-route-service", "routeservice")},
    "TT-adminbasic-contacts":  {"entry": "ts-admin-basic-info-service", "presence": ("ts-contacts-service", "contact")},
    "bookinfo-ratings-benign": {"entry": "productpage.default", "entry_op": None,
                                "presence": ("ratings.default", "ratings", "server"),
                                "scope": {"productpage.default", "reviews.default", "ratings.default"}},
    "sockshop-shipping-swallowed-enqueue": {"entry": "orders", "entry_op": "POST /orders",
                                "presence": ("queue-master", "shipping-task", "consumer"),
                                "scope": {"orders", "shipping", "queue-master"}},
    "sockshop-shipping-control": {"entry": "orders", "entry_op": "POST /orders",
                                "presence": ("queue-master", "shipping-task", "consumer"),
                                "scope": {"orders", "shipping", "queue-master"}},
    # PHASE-D EXTENSION (tenancy plan rev-2 D3, committed BEFORE the first OTel-Demo capture; names
    # bound from D2 canary traces only): OTel-Demo Kafka CONSUMERS continue the flow in their OWN
    # traces (span links, canary-verified: accounting "receive orders" consumer + "order-consumed" +
    # the postgres client span live in a separate trace from the checkout entry trace). The
    # required-effect span therefore can NEVER appear in the entry trace, so these cases carry
    # presence_scope="file": the per-leg export FILE = entry-trace query (service=checkout) MERGED
    # with the consumer-service query (service=accounting) over the leg window, and BOTH the
    # presence assertion and the naive error scan run over every span in the file (still bounded by
    # the per-case service scope). Entry selection + the exactly-one rule are UNCHANGED (only the
    # checkout trace carries a frontend-proxy server span). fraud-detection deliberately OUT of
    # scope (its flagd EventStream client spans error routinely; not part of the asserted effect).
    "oteldemo-checkout-lost":    {"entry": "frontend-proxy", "entry_op": "POST",
                                "presence": ("accounting", "receive orders", "consumer"),
                                "scope": {"frontend-proxy", "frontend", "checkout", "accounting"},
                                "presence_scope": "file"},
    "oteldemo-checkout-control": {"entry": "frontend-proxy", "entry_op": "POST",
                                "presence": ("accounting", "receive orders", "consumer"),
                                "scope": {"frontend-proxy", "frontend", "checkout", "accounting"},
                                "presence_scope": "file"},
    # WAVE-3A ITEM-1 (plan rev 2.1, unanimous-accept b0b5a54; committed BEFORE the item's first
    # capture; names bound from the Phase-D canary — cart EmptyCart SERVER span
    # "POST /oteldemo.CartService/EmptyCart" lives IN the checkout entry trace (sync gRPC), so the
    # default entry-trace presence scope applies; re-verified on a fresh canary pre-capture).
    # Presence stays existence-only: a present-but-erroring EmptyCart span = no_flag (the plan's
    # pre-pinned branch-alpha MISS); the error axis belongs to naive_span_error.
    # OUTCOME (2026-07-11): the CASE WAS NOT AUTHORED — the C-M5 refutation branch fired (deployed
    # 2.2.0 cartFailure = LOUD 504, no masked ack; survey corrected). These rows are retained as
    # the refutation evidence's scoring record (captures kept under oteldemo-emptycart-*).
    # WAVE-3A ITEM-3 (probe-gated S2, plan rev 2.1 §3; committed BEFORE any item-3 capture per
    # B-F6): same selector family as the flagship pair (consumer spans live in LINKED traces ->
    # presence_scope=file; T2 family baseline = the flagship oteldemo-checkout-control capture +
    # a fresh pre-flag canary on the day). The AUTHOR/NOT-AUTHORED/STOP decision is made by the
    # probe round; this row is inert unless the case is authored.
    # OUTCOME (2026-07-10): STOP branch fired (plan rev 2.1 §3, C-m8). Probe N=4 + confirmatory N=2
    # measured kafkaQueueProblems=100 on 2.2.0 as a STOCHASTIC MIX dominated by PERMANENT PRODUCTION
    # LOSS -- 7 of 8 in-window acked orders never landed (still absent after an accounting+checkout
    # rollout-restart; a later canary drained past the lost ones => dropped at production, not
    # buffered), 1 fast success under-flag, 0 pending. "delayed-not-lost" REFUTED; NO case authored
    # (neither S2 nor S1) -- S1-positive candidate deferred to its own discipline. This row stays
    # inert, retained as the STOP decision's scoring record (see c2-depth-survey.md OTel item-3 block
    # and b4/runners/3a/item3-*).
    "oteldemo-kafkaqueue-pending-benign": {"entry": "frontend-proxy", "entry_op": "POST",
                                "presence": ("accounting", "receive orders", "consumer"),
                                "scope": {"frontend-proxy", "frontend", "checkout", "accounting"},
                                "presence_scope": "file"},
    "oteldemo-emptycart-swallowed": {"entry": "frontend-proxy", "entry_op": "POST",
                                "presence": ("cart", "emptycart", "server"),
                                "scope": {"frontend-proxy", "frontend", "checkout", "cart"}},
    "oteldemo-emptycart-control":  {"entry": "frontend-proxy", "entry_op": "POST",
                                "presence": ("cart", "emptycart", "server"),
                                "scope": {"frontend-proxy", "frontend", "checkout", "cart"}},
}

# postgresql added with the Phase-D extension (OTel-Demo accounting writes via Npgsql; canary-bound)
DB_SYSTEMS = ("jdbc", "mysql", "mongodb", "mongo", "postgresql", "postgres")


def sel_for(case_id):
    for prefix, sel in SELECTORS.items():
        if case_id.startswith(prefix):
            return sel
    raise SystemExit("no frozen selector for case %r" % case_id)


def tagmap(span):
    return {t["key"]: t.get("value") for t in span.get("tags", [])}


def has_entry_server_span(tr, entry_svc, entry_op=None):
    procs = {pid: p.get("serviceName", "?") for pid, p in tr.get("processes", {}).items()}
    for s in tr.get("spans", []):
        if procs.get(s.get("processID")) == entry_svc and tagmap(s).get("span.kind") == "server":
            if entry_op is None or entry_op.lower() in s.get("operationName", "").lower():
                return True
    return False


def is_error_span(tags):
    if str(tags.get("error")).lower() == "true" or str(tags.get("otel.status_code")).upper() == "ERROR":
        return True
    try:
        return int(str(tags.get("http.status_code", 0))) >= 500
    except (TypeError, ValueError):
        return False


def score(case_id, trace_path):
    sel = sel_for(case_id)
    scope = sel.get("scope", INSTRUMENTED)
    doc = json.load(open(trace_path, encoding="utf-8"))
    raw = doc.get("data") or []
    traces = [t for t in raw if has_entry_server_span(t, sel["entry"], sel.get("entry_op"))]
    if len(traces) != 1:
        raise SystemExit("ERROR: expected exactly ONE entry-server trace in %s, found %d of %d raw "
                         "(T8 exactly-one-match rule after the disclosed server-span selection)"
                         % (trace_path, len(traces), len(raw)))
    tr = traces[0]
    # presence_scope="file" (Phase-D): scan every exported trace (consumer spans live in linked
    # traces); default remains the selected entry trace only.
    scan = raw if sel.get("presence_scope") == "file" else [tr]
    spans = [(t, s) for t in scan for s in t.get("spans", [])]

    entry_seen, presence_hit, error_spans, db_report = False, False, [], {}
    db_presence_hit = False  # E2/C2: the frozen DB-client INSERT span of the drawback write
    for t, s in spans:
        procs = {pid: p.get("serviceName", "?") for pid, p in t.get("processes", {}).items()}
        svc = procs.get(s.get("processID"), "?")
        tags = tagmap(s)
        kind = tags.get("span.kind")
        op = s.get("operationName", "")
        if svc == sel["entry"] and kind == "server":
            entry_seen = True
        if sel["presence"]:
            p = sel["presence"]
            psvc, frag, pkind = (p[0], p[1], p[2]) if len(p) == 3 else (p[0], p[1], "server")
            if svc == psvc and kind == pkind and frag in op.lower():
                presence_hit = True
        if svc in scope and is_error_span(tags):
            error_spans.append("%s::%s" % (svc, op))
        # db.system.name = the STABLE database semconv key (.NET auto-instr 1.13, canary-bound
        # Phase-D); REPORT-only field, verdict columns untouched.
        db_sys = str(tags.get("db.system", tags.get("db.system.name", ""))).lower()
        if db_sys in DB_SYSTEMS:
            db_report[svc] = db_report.get(svc, 0) + 1
            # E2/C2: the DB-CLIENT-granularity presence verdict (frozen selector; existence-only).
            dp = sel.get("db_presence")
            if dp and svc == dp[0] and dp[1] in op.lower():
                db_presence_hit = True

    if not entry_seen:
        raise SystemExit("ERROR: entry server span for %s not found -- wrong trace selected?" % sel["entry"])

    naive = "flag" if error_spans else "no_flag"
    if sel["presence"] is None:
        presence = "not_applicable"
    else:
        presence = "no_flag" if presence_hit else "flag"

    # E2/C2: the DB-CLIENT-granularity presence verdict (only for cases with a frozen db_presence).
    db_presence_sel = sel.get("db_presence")
    if db_presence_sel is None:
        db_presence = "not_applicable"
    else:
        db_presence = "no_flag" if db_presence_hit else "flag"

    print(json.dumps({
        "case_id": case_id, "trace": trace_path, "spans": len(spans),
        "naive_span_error_oracle": naive,
        "error_spans": error_spans,
        "tracetest_presence_oracle_verdict_on_this_leg": presence,
        "presence_selector": sel["presence"],
        "db_span_presence_oracle_verdict_on_this_leg": db_presence,
        "db_presence_selector": db_presence_sel,
        "db_client_spans_per_service (T6 disclosure)": db_report,
    }, indent=1))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    score(sys.argv[1], sys.argv[2])
