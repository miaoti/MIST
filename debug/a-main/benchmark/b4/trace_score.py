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

SELECTORS = {
    "TT-cancel-refund":        {"entry": "ts-cancel-service",           "presence": ("ts-inside-payment-service", "drawback")},
    "TT-createaccount":        {"entry": "ts-inside-payment-service",   "presence": None},
    "TT-adminroute":           {"entry": "ts-admin-route-service",      "presence": ("ts-route-service", "routeservice")},
    "TT-adminbasic-contacts":  {"entry": "ts-admin-basic-info-service", "presence": ("ts-contacts-service", "contact")},
}

DB_SYSTEMS = ("jdbc", "mysql", "mongodb", "mongo")


def sel_for(case_id):
    for prefix, sel in SELECTORS.items():
        if case_id.startswith(prefix):
            return sel
    raise SystemExit("no frozen selector for case %r" % case_id)


def tagmap(span):
    return {t["key"]: t.get("value") for t in span.get("tags", [])}


def has_entry_server_span(tr, entry_svc):
    procs = {pid: p.get("serviceName", "?") for pid, p in tr.get("processes", {}).items()}
    for s in tr.get("spans", []):
        if procs.get(s.get("processID")) == entry_svc and tagmap(s).get("span.kind") == "server":
            return True
    return False


def score(case_id, trace_path):
    sel = sel_for(case_id)
    doc = json.load(open(trace_path, encoding="utf-8"))
    raw = doc.get("data") or []
    traces = [t for t in raw if has_entry_server_span(t, sel["entry"])]
    if len(traces) != 1:
        raise SystemExit("ERROR: expected exactly ONE entry-server trace in %s, found %d of %d raw "
                         "(T8 exactly-one-match rule after the disclosed server-span selection)"
                         % (trace_path, len(traces), len(raw)))
    tr = traces[0]
    procs = {pid: p.get("serviceName", "?") for pid, p in tr.get("processes", {}).items()}
    spans = tr.get("spans", [])

    entry_seen, presence_hit, error_spans, db_report = False, False, [], {}
    for s in spans:
        svc = procs.get(s.get("processID"), "?")
        tags = tagmap(s)
        kind = tags.get("span.kind")
        op = s.get("operationName", "")
        if svc == sel["entry"] and kind == "server":
            entry_seen = True
        if sel["presence"]:
            psvc, frag = sel["presence"]
            if svc == psvc and kind == "server" and frag in op.lower():
                presence_hit = True
        if svc in INSTRUMENTED and (str(tags.get("error")).lower() == "true"
                                    or str(tags.get("otel.status_code")).upper() == "ERROR"):
            error_spans.append("%s::%s" % (svc, op))
        if str(tags.get("db.system", "")).lower() in DB_SYSTEMS:
            db_report[svc] = db_report.get(svc, 0) + 1

    if not entry_seen:
        raise SystemExit("ERROR: entry server span for %s not found -- wrong trace selected?" % sel["entry"])

    naive = "flag" if error_spans else "no_flag"
    if sel["presence"] is None:
        presence = "not_applicable"
    else:
        presence = "no_flag" if presence_hit else "flag"

    print(json.dumps({
        "case_id": case_id, "trace": trace_path, "spans": len(spans),
        "naive_span_error_oracle": naive,
        "error_spans": error_spans,
        "tracetest_presence_oracle_verdict_on_this_leg": presence,
        "presence_selector": sel["presence"],
        "db_client_spans_per_service (T6 disclosure)": db_report,
    }, indent=1))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    score(sys.argv[1], sys.argv[2])
