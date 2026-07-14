#!/usr/bin/env python3
"""R2 neutralizer (rev 3): build canonical, rater-clean sidecars from the real
capture sidecars. FAITHFUL — records come from the captured transcripts;
presence/count decision signals are COMPUTED from the real read-back bodies on
the HTML/order-items paths, and MACHINE-READ from the committed runner psql
evidence (readback-psql.txt) for the two OTel durable cases (nothing hand-set).
NEUTRAL — probe -> plain canonical locator; injection-mechanism prose, epoch,
HTML chrome removed; markers that carry a banned token OR encode the
control/fault LABEL substituted to a neutral display token. Clean-by-
construction: after transform every field is re-scanned and any residual banned
token is elided + LOGGED for the manual read-through. Capture-evidence sidecars
are untouched (provenance); these are derivatives.

rev-2 folded the manual-read-through findings: (1) label-tell markers (C/F/X,
Ctl/Flt, NoOp, TRACED) neutralized; (2) orderitems observations dispatched by
BODY SHAPE not position + rendered as count/presence descriptors; (3) SQL probe
marker made consistent with the payload; (4) collection bodies truncated more
generously. KNOWN-DISCLOSED (gated Step-5): TT admin/global-collection
read-backs DO truncate off the acting record; per-endpoint acting-record
rendering is rater-cut assembly.

rev-3 folds the post-hoc 3-cold-review BLOCKING set (B-1..5, C-1): (a) the two
OTel durable observations are RE-KEYED on the acked order_id (both corpus legs
shared streetAddress='1 Corpus Way', so a street_address-keyed query is
counterfactual under its own text) with row counts MACHINE-READ from
readback-psql.txt, rendering the full permanence evidence (in-window +
post-restore + after-heal for the absent case) and status "n/a" (psql has no
HTTP status); (b) observations taken in a separate later pass are MARKED
"(... verification read/pass)" in the probe and carry ROUND PLACEHOLDER
t_rel_ms offsets — real cross-phase offsets are not recorded in the committed
evidence; relative ORDER is faithful (disclosed in AUDIT-r2.md); (c) the
label-encoding email local-parts (otlc1/otlf2) neutralized; (d) the bookinfo
response renders a COMPUTED page descriptor carrying the visible
ratings-unavailable message (the case's decision signal) instead of a vacuous
placeholder; (e) ISO-T datetimes inside rendered bodies masked to <time>
(cross-pair ordering tell); (f) import-safe (__main__ guard)."""
import json, os, re, hashlib

import os
_HERE = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.join(_HERE, "captures") + os.sep
W120 = os.path.join(_HERE, "s3", "window-oteldemo", "sidecars", "w120-sidecar.json")
OUT  = os.path.join(_HERE, "rater-sidecars") + os.sep

BANNED = [
    "mist","oracle","verdict","fire","no_fire","quiescence","gate","triple",
    "paired","fault_flag","fabricated","injection","injector","acked-but-lost",
    "lost write","lostwrite","observe mode","meshsever","mesh-sever","severed",
    "teardown","post-vs","post-drain","envoyfilter","virtualservice","scaled",
    "scale to","toggle","drain","maintenance","read-back cap","readback cap","epoch_ms",
]
# label-encoding tells that the BANNED list can't catch (found by the manual
# read-through): applied to every displayed string.
GLOBAL_TELL_SUBS = [
    ("MIST","Corpus"), ("-TRACED",""), ("Breadth Ctl","Breadth Contact"),
    ("Breadth Flt","Breadth Contact"), ("BR-CTL","BR"), ("BR-FLT","BR"),
    ("NoOp","Corpus"),
    # rev-3 (review B-1): otl{c,f}N email local-parts encode control/fault —
    # the same single-letter convention as TSMW{C,F}; neutralized label-symmetric.
    ("otlc1","corpus-user"), ("otlf2","corpus-user"),
]
CLEAN_VERSION = {
    "teastore": "TeaStore v1.4.2",
    "oteldemo": "opentelemetry-demo app 2.2.0 / chart 0.40.9",
    "sockshop": "microservices-demo (Sock Shop)",
    "bookinfo": "Istio Bookinfo sample",
    "trainticket": "train-ticket (pinned image set)",
}
DISP_MARKER = "corpus-marker"   # neutral display token for label-encoding markers

LOG = []
def is_html(s): return isinstance(s,str) and s.lstrip().lower().startswith(("<!doctype","<html","<meta"))
def banned_hit(s): low=(s or "").lower(); return [b for b in BANNED if b in low]
def elide_if_banned(s, ctx):
    if not isinstance(s,str): return s
    h=banned_hit(s)
    if h: LOG.append("ELIDE %s banned=%s :: %s"%(ctx,h,s[:80])); return "<elided>"
    return s
def sub_all(s, sub):
    if isinstance(s,str):
        for a,b in (GLOBAL_TELL_SUBS + (sub or [])):
            s = re.sub(re.escape(a), b, s, flags=re.I)
        # rev-3 (review B-11): ISO-T datetimes inside rendered bodies carry
        # absolute wall-clock (cross-pair ordering inferable) -> masked. The
        # tail is [0-9:.]*Z? so a datetime cut mid-way by trunc() still masks.
        s = re.sub(r"\d{4}-\d{2}-\d{2}T[0-9:.]*Z?", "<time>", s)
        s = re.sub(r"\s{2,}"," ",s)
    return s
def trunc(s, n=600): return (s[:n]+"…") if isinstance(s,str) and len(s)>n else s

def load(path): return json.load(open(BASE+path if not path.startswith("C:") else path, encoding="utf-8"))
def confirm_marker(recs):
    for r in recs:
        m=re.search(r'firstname=([^&]+)', r.get("path","") or "")
        if m: return m.group(1)
    return None
def checkout_marker(recs):
    for r in recs:
        m=re.search(r'"streetAddress"\s*:\s*"([^"]+)"', r.get("payload","") or "")
        if m: return m.group(1)
    return None
def acked_order_id(recs):
    for r in recs:
        if r.get("kind")=="response" and isinstance(r.get("body"),str):
            m=re.search(r'"orderId"\s*:\s*"([^"]+)"', r["body"])
            if m: return m.group(1)
    return None
def parse_psql_evidence(path):
    """rev-3 (reviews B-6/C-1b): machine-read the committed runner psql evidence
    (key=value lines; '#' comments and inline comments ignored)."""
    facts={}
    for line in open(path, encoding="utf-8", errors="replace"):
        line=line.strip()
        if not line or line.startswith("#"): continue
        m=re.match(r"([A-Za-z0-9_]+)=(\S+)", line)
        if m: facts[m.group(1)]=m.group(2)
    return facts

def clean_payload(pl, sub):
    if pl is None: return None
    if is_html(pl): return "<form submission>"
    return elide_if_banned(sub_all(trunc(pl), sub), "payload")
def clean_resp_body(b, sub):
    if is_html(b): return "<success-shaped page rendered>"
    return elide_if_banned(sub_all(trunc(b), sub), "resp.body")

def xform_write(recs, sub, disp_sub):
    """login/add/confirm/ack; DROP in-window observation reads."""
    out=[]
    for r in recs:
        k=r.get("kind")
        if k=="request":
            nr={"t_rel_ms":r["t_rel_ms"],"kind":"request","method":r.get("method"),
                "path":elide_if_banned(sub_all(r.get("path"), sub+disp_sub),"path")}
            if r.get("payload") is not None: nr["payload"]=clean_payload(r.get("payload"), sub+disp_sub)
            out.append(nr)
        elif k=="response":
            out.append({"t_rel_ms":r["t_rel_ms"],"kind":"response","status":r.get("status"),
                        "body":clean_resp_body(r.get("body"), sub+disp_sub)})
    return out

def presence_html_obs(t, canon, status, body, real_marker, sub, html_desc):
    present = bool(real_marker) and isinstance(body,str) and (real_marker in body)
    return {"t_rel_ms":t,"kind":"observation","probe":canon,"status":status,
            "body":"%s: order '%s' %s"%(html_desc, DISP_MARKER, "PRESENT" if present else "ABSENT")}

def orderitems_dispatch(body):
    """classify a teastore persistence read-back body by shape."""
    try: arr=json.loads(body) if isinstance(body,str) else body
    except Exception: return ("unknown", None)
    if not isinstance(arr,list): return ("unknown", None)
    if not arr: return ("empty", 0)
    keys=set(arr[0].keys()) if isinstance(arr[0],dict) else set()
    if {"productId","quantity","orderId"} <= keys and "addressName" not in keys: return ("orderitems", len(arr))
    if "addressName" in keys or "userId" in keys: return ("orders", len(arr))
    return ("unknown", len(arr))

def enforce_monotonic(recs):
    prev=-1
    for r in recs:
        t=r.get("t_rel_ms",prev+1)
        if not isinstance(t,(int,float)) or t<prev: t=prev
        r["t_rel_ms"]=t; prev=t
    return recs

SUB0=[]
CASES = {
 # ---- TrainTicket ----
 "TT-adminbasic-contacts-control-001": dict(mode="single", src="tt-s2-adminbasic-contacts-control-traced/sidecar.json", canon="GET /api/v1/adminbasicservice/adminbasic/contacts", sub=SUB0),
 "TT-adminbasic-contacts-lostwrite-001": dict(mode="single", src="tt-s1-adminbasic-contacts-lostwrite-traced/sidecar.json", canon="GET /api/v1/adminbasicservice/adminbasic/contacts", sub=SUB0),
 "TT-adminroute-control-001": dict(mode="single", src="tt-ctl-adminroute-create-traced/sidecar.json", canon="GET /api/v1/adminrouteservice/adminroute", sub=SUB0),
 "TT-adminroute-lostwrite-001": dict(mode="single", src="tt-s1-adminroute-lostwrite-traced/sidecar.json", canon="GET /api/v1/adminrouteservice/adminroute", sub=SUB0),
 "TT-cancel-refund-clean-001": dict(mode="single", src="tt-s2-cancel-clean-traced/sidecar.json", canon="GET /api/v1/inside_pay_service/inside_payment/account", sub=SUB0),
 "TT-cancel-refund-fabricatedack-001": dict(mode="single", src="tt-s1-cancel-fabricatedack-traced/sidecar.json", canon="GET /api/v1/inside_pay_service/inside_payment/account", sub=SUB0),
 "TT-cancel-refund-natural-001": dict(mode="single", src="tt-s1-cancel-natural/sidecar.json", canon="GET /api/v1/inside_pay_service/inside_payment/account", sub=SUB0),
 "TT-contacts-dedupe-benign-001": dict(mode="single", src="tt-benign-contacts-dedupe/sidecar.json", canon="GET /api/v1/contactservice/contacts/account/{accountId}", sub=SUB0),
 "TT-contacts-noop-modify-benign-001": dict(mode="single", src="tt-s2-contacts-noop-modify/sidecar.json", canon="GET /api/v1/contactservice/contacts/account/{accountId}", sub=SUB0),
 "TT-createaccount-agreement-001": dict(mode="single", src="tt-s1-createaccount-agreement-traced/sidecar.json", canon="GET /api/v1/inside_pay_service/inside_payment/account", sub=SUB0),
 "TT-createaccount-clean-001": dict(mode="single", src="tt-s2-createaccount-clean-traced/sidecar.json", canon="GET /api/v1/inside_pay_service/inside_payment/account", sub=SUB0),
 # ---- bookinfo (read-path; no durable write read-back). rev-3 (review B-3):
 # the response HTML carries the case's DECISION SIGNAL (the visible
 # ratings-unavailable message) -> rendered as a COMPUTED page descriptor. ----
 "bookinfo-ratings-benign-001": dict(mode="single", src="bookinfo-ratings-benign-traced/sidecar.json", canon="GET /productpage", page_desc=True, sub=SUB0),
 # ---- sockshop ----
 "sockshop-shipping-control-001": dict(mode="single", src="sockshop-shipping-control-traced/sidecar.json", canon="GET /orders (the acting customer's orders)", sub=SUB0),
 "sockshop-shipping-swallowed-enqueue-001": dict(mode="single", src="sockshop-shipping-swallowed-traced/sidecar.json", canon="GET /orders (the acting customer's orders)", sub=SUB0),
 # ---- oteldemo eventual (SQL read-back already in sidecar) ----
 "oteldemo-checkout-eventual-benign-001": dict(mode="single", src=W120, canon="SQL", sub=SUB0),
 "oteldemo-checkout-eventual-benign-002": dict(mode="single", src="oteldemo-checkout-eventual-induced/sidecar-002.json", canon="SQL", sub=SUB0),
 "oteldemo-checkout-eventual-benign-003": dict(mode="single", src="oteldemo-checkout-eventual-induced/sidecar-003.json", canon="SQL", sub=SUB0),
 # ---- oteldemo control/lost (durable SQL read-back MACHINE-READ from the
 # committed runner psql evidence; order_id-keyed — rev-3, reviews B-4/B-6/C-1) ----
 "oteldemo-checkout-control-001": dict(mode="durable", src="oteldemo-checkout-control/sidecar.json", evidence="oteldemo-checkout-control/readback-psql.txt", sub=SUB0),
 "oteldemo-checkout-lost-001": dict(mode="durable", src="oteldemo-checkout-lost/sidecar.json", evidence="oteldemo-checkout-lost/readback-psql.txt", sub=SUB0),
 # ---- teastore whole-order (merge; profile HTML -> presence descriptor) ----
 "teastore-order-control-001": dict(mode="merge_profile", write="teastore-order-control/sidecar.json", readback="teastore-order-control/sidecar-postrestore.json", canon="GET /tools.descartes.teastore.webui/profile (orders table)", html_desc="orders table", sub=SUB0),
 "teastore-order-maintenance-masked-001": dict(mode="merge_profile", write="teastore-order-masked/sidecar.json", readback="teastore-order-masked/sidecar-postrestore.json", canon="GET /tools.descartes.teastore.webui/profile (orders table)", html_desc="orders table", sub=SUB0),
 "teastore-order-meshsever-control-001": dict(mode="merge_profile", write="teastore-order-meshsever-control/sidecar.json", readback="teastore-order-meshsever-control/sidecar-postteardown.json", canon="GET /tools.descartes.teastore.webui/profile (orders table)", html_desc="orders table", sub=SUB0),
 "teastore-order-meshsever-masked-001": dict(mode="merge_profile", write="teastore-order-meshsever-masked/sidecar.json", readback="teastore-order-meshsever-masked/sidecar-postteardown.json", canon="GET /tools.descartes.teastore.webui/profile (orders table)", html_desc="orders table", sub=SUB0),
 # ---- teastore orderitems (merge; dispatch obs by body shape) ----
 "teastore-orderitems-meshsever-control-001": dict(mode="merge_items", write="teastore-orderitems-meshsever-control/sidecar.json", readback="teastore-orderitems-meshsever-control/sidecar-postteardown.json", sub=SUB0),
 "teastore-orderitems-meshsever-masked-001": dict(mode="merge_items", write="teastore-orderitems-meshsever-masked/sidecar.json", readback="teastore-orderitems-meshsever-masked/sidecar-postteardown.json", sub=SUB0),
}
CANON_ORDERS = "GET /tools.descartes.teastore.persistence/rest/orders/user/{id} (parent order)"
CANON_ITEMS  = "GET /tools.descartes.teastore.persistence/rest/orderitems/order/{orderId} (line items)"

def build(cid, spec):
    mode=spec["mode"]; sub=spec.get("sub",SUB0)
    if mode=="single":
        d=load(spec["src"]); recs=d.get("records") or []
        marker=confirm_marker(recs) or checkout_marker(recs)
        disp_sub=[(marker, DISP_MARKER)] if (marker and re.search(r'[CFX]\d', marker or "")) else []
        out=[]
        for r in recs:
            k=r.get("kind")
            if k=="request":
                nr={"t_rel_ms":r["t_rel_ms"],"kind":"request","method":r.get("method"),
                    "path":elide_if_banned(sub_all(r.get("path"), sub+disp_sub),"path")}
                if r.get("payload") is not None: nr["payload"]=clean_payload(r.get("payload"), sub+disp_sub)
                out.append(nr)
            elif k=="response":
                b=r.get("body")
                if spec.get("page_desc") and is_html(b):
                    # COMPUTED page descriptor from the REAL body (never hand-set):
                    # substring checks only; carries the visible degradation
                    # message when (and only when) the page actually shows it.
                    msg="Ratings service is currently unavailable"
                    parts=[]
                    if ("Reviewer" in b) or ("reviews" in b.lower()): parts.append("product and reviews content shown")
                    if msg in b: parts.append('ratings panel displays the message "%s"'%msg)
                    elif ("Ratings" in b) or ("ratings" in b): parts.append("ratings content shown")
                    body="page rendered: "+"; ".join(parts) if parts else "page rendered"
                    out.append({"t_rel_ms":r["t_rel_ms"],"kind":"response","status":r.get("status"),
                                "body":elide_if_banned(body,"page_desc")})
                else:
                    out.append({"t_rel_ms":r["t_rel_ms"],"kind":"response","status":r.get("status"),
                                "body":clean_resp_body(b, sub+disp_sub)})
            elif k=="observation":
                if spec["canon"]=="SQL":
                    m2 = sub_all(marker or "<marker>", sub)
                    canon="SQL SELECT street_address FROM accounting.shipping WHERE street_address='%s'"%m2
                else:
                    canon=spec["canon"]
                b=r.get("body")
                if is_html(b):
                    out.append(presence_html_obs(r["t_rel_ms"], canon, r.get("status"), b, marker, sub, "read-back"))
                else:
                    out.append({"t_rel_ms":r["t_rel_ms"],"kind":"observation","probe":canon,
                                "status":r.get("status"),"body":elide_if_banned(sub_all(trunc(b), sub+disp_sub),"obs.body")})
        meta=d
    elif mode=="durable":
        # rev-3 (reviews B-4/B-6/C-1): durable SQL observations are keyed on the
        # acked order_id (unique; visible in the rendered ack — a street_address
        # key would be counterfactual: both legs shared '1 Corpus Way'); row
        # counts MACHINE-READ from readback-psql.txt; permanence evidence
        # rendered in full; status "n/a" (psql has no HTTP status); probes are
        # marked verification reads with ROUND PLACEHOLDER offsets (real
        # cross-phase offsets unrecorded; order faithful — see AUDIT-r2.md).
        d=load(spec["src"]); recs=d.get("records") or []
        out=xform_write(recs, sub, [])
        facts=parse_psql_evidence(BASE+spec["evidence"])
        oid=facts.get("order_id")
        ack_oid=acked_order_id(recs)
        if not oid or (ack_oid and ack_oid!=oid):
            raise RuntimeError("durable evidence order_id mismatch: ack=%r evidence=%r"%(ack_oid,oid))
        probe="SQL SELECT count(*) FROM accounting.\"order\" WHERE order_id='%s'"%oid
        last=out[-1]["t_rel_ms"] if out else 0
        if "row_count" in facts:      # single verification read (the row landed)
            seq=[(probe+" (verification read, performed later)", facts["row_count"], 30000)]
        else:                          # permanence evidence: absent at every read
            seq=[(probe+" (verification read)", facts["row_count_in_window"], 30000),
                 (probe+" (later verification read)", facts["row_count_post_restore"], 300000),
                 (probe+" (final verification read, later still)", facts["row_count_after_verified_heal"], 900000)]
        for p,count,dt in seq:
            out.append({"t_rel_ms":last+dt,"kind":"observation","probe":p,"status":"n/a","body":str(count)})
        meta=d
    elif mode=="merge_profile":
        w=load(spec["write"]); rb=load(spec["readback"])
        marker=confirm_marker(w.get("records") or [])
        disp_sub=[(marker, DISP_MARKER)] if marker else []
        out=xform_write(w.get("records") or [], sub, disp_sub)
        last=out[-1]["t_rel_ms"] if out else 0
        obs=[r for r in (rb.get("records") or []) if r.get("kind")=="observation"]
        # rev-3 (review C-1a): the read-back is a SEPARATE later pass — marked in
        # the probe; ROUND PLACEHOLDER offset (+120000), REAL intra-phase deltas.
        base=obs[0].get("t_rel_ms",0) if obs else 0
        for r in obs:
            t=last+120000+(r.get("t_rel_ms",0)-base)
            out.append(presence_html_obs(t, spec["canon"]+" — read in a later verification pass", r.get("status"), r.get("body"), marker, sub, spec["html_desc"]))
        meta=w
    elif mode=="merge_items":
        w=load(spec["write"]); rb=load(spec["readback"])
        marker=confirm_marker(w.get("records") or [])
        disp_sub=[(marker, DISP_MARKER)] if marker else []
        out=xform_write(w.get("records") or [], sub, disp_sub)
        last=out[-1]["t_rel_ms"] if out else 0
        obs=[r for r in (rb.get("records") or []) if r.get("kind")=="observation"]
        # rev-3 (review C-1a): same later-verification-pass marking + placeholder
        # offset + real intra-phase deltas as merge_profile.
        base=obs[0].get("t_rel_ms",0) if obs else 0
        for r in obs:
            kind,n = orderitems_dispatch(r.get("body"))
            if kind in ("orderitems","empty"):
                # a productId-shaped body OR an EMPTY body is the child line-item
                # collection (the parent order is always non-empty/present in these
                # cases, so an empty read-back is the lost child, count 0)
                canon=CANON_ITEMS; body="line items: %d item(s)"%(n or 0)
            elif kind=="orders":
                # parent order present iff the acting marker (firstname) is in the full body
                present = bool(marker) and isinstance(r.get("body"),str) and (marker in r.get("body"))
                canon=CANON_ORDERS; body="parent order '%s': %s"%(DISP_MARKER, "PRESENT" if present else "ABSENT")
            else:
                canon=CANON_ORDERS; body=elide_if_banned(sub_all(trunc(r.get("body")), sub+disp_sub),"obs.body")
            t=last+120000+(r.get("t_rel_ms",0)-base)
            out.append({"t_rel_ms":t,"kind":"observation","probe":canon+" — read in a later verification pass","status":r.get("status"),"body":body})
        meta=w
    enforce_monotonic(out)
    return {"sidecar_version":1,"case_id":cid,"producer":meta.get("producer","seed-capture-driver"),
            "mist_commit":meta.get("mist_commit","7d69de9"),
            "sut":{"name":(meta.get("sut") or {}).get("name"),
                   "version_ref":elide_if_banned(CLEAN_VERSION.get((meta.get("sut") or {}).get("name"),"pinned"),"version_ref")},
            "records":out}

def main():
    # rev-3 (review B-7): guarded — importing this module must never rebuild
    # the 25 deliverables as a side effect.
    os.makedirs(OUT, exist_ok=True)
    man=[]
    for cid,spec in CASES.items():
        try: doc=build(cid,spec)
        except Exception as e:
            print("BUILD-FAIL",cid,type(e).__name__,e); continue
        txt=json.dumps(doc,indent=1,ensure_ascii=False)
        open(OUT+cid+".json","w",encoding="utf-8",newline="\n").write(txt+"\n")
        man.append((cid,len(doc["records"]),hashlib.sha256(txt.encode()).hexdigest()[:12]))
    print("built %d -> %s"%(len(man),OUT))
    for cid,n,h in man: print(f"  {cid:46} recs={n} sha={h}")
    print("\n--- ELISION LOG (%d) ---"%len(LOG))
    for l in LOG: print("  ",l)
    return man

if __name__ == "__main__":
    main()
