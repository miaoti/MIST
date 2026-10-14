#!/usr/bin/env python3
"""First-rendered-use audit of the paper's coined and technical terms.

Reads the PDF text dump (page order, floats where they render) and prints, for every term,
the page and the sentence of its first occurrence, so a person can check that the sentence
defines the term or that a definition precedes it.

    python paper/tool-demo/tools/termaudit.py <pdf-text-dump.txt>
"""
import re
import sys

TERMS = [
    # branded features and components
    "Root API Mode", "Sniper Strategy", "Trace Shape Oracle", "HiddenDownstreamFailure",
    "ResponseEnvelope", "SpanTreeShape", "StatusPropagation", "TimingEnvelope",
    "Spec Ingestor", "Semantic Dependency Registry", "Sequence Generator", "Fault Injector",
    "Trace Checker", "fault miner", "mist-core", "mist-cli", "mist-llm",
    # coined names of failures and concepts
    "soft error", "hidden downstream failure", "fabricated state", "generator pitfall",
    "root call", "entry call", "invariant", "scenario", "negative test", "positive",
    "invalid input", "defect", "registry", "seed corpus", "learned famil", "swallow",
    # tracing vocabulary
    "span", "trace", "OpenTelemetry", "Jaeger", "otel.status_code", "span status",
    "gRPC status", "HTTP status attribute", "service mesh", "sidecar",
    # tooling vocabulary
    "OpenAPI", "properties file", "Allure", "JUnit", "REST-Assured", "Maven", "in-process",
    "committed", "offline", "replay", "DeepSeek", "Ollama", "JDK",
    # systems
    "TrainTicket", "Bookinfo", "Sock Shop", "Online Boutique", "adservice", "ratings", "reviews",
    "frontend", "catalogue",
]

text = open(sys.argv[1], encoding="utf-8").read()
# page markers: the dump concatenates pages; approximate page boundaries by form feeds if present
pages = text.split("\f") if "\f" in text else [text]
flat = re.sub(r"-\n(?=[a-z])", "", text)        # undo hyphenation at line ends
flat = re.sub(r"\s+", " ", flat)
sentences = re.split(r"(?<=[.!?])\s+(?=[A-Z\\(])", flat)


def page_of(pos):
    acc = 0
    for i, p in enumerate(pages):
        acc += len(p) + 1
        if pos < acc:
            return i + 1
    return len(pages)


for term in TERMS:
    pat = re.compile(re.escape(term), re.I)
    m = pat.search(flat)
    if not m:
        print(f"{term:28s} NOT FOUND")
        continue
    # find sentence containing the match
    sent = next((s for s in sentences if pat.search(s)), "")
    # page: map flat position back roughly by searching the raw text
    raw = re.search(re.escape(term), text, re.I)
    pg = page_of(raw.start()) if raw else "?"
    count = len(pat.findall(flat))
    print(f"{term:28s} p{pg} x{count:<3d} {sent[:170]}")
