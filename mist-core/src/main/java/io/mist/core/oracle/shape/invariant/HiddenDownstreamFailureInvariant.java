package io.mist.core.oracle.shape.invariant;

import io.mist.core.oracle.shape.ShapeInvariant;
import io.mist.core.oracle.shape.TraceModel;
import io.mist.core.oracle.shape.TraceShapeVerdict;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Structural, label-free invariant that flags a HIDDEN downstream failure: the
 * client-facing entry returned success (2xx) while a deeper span server-errored,
 * so the failure was swallowed and never surfaced to the caller.
 *
 * <p><b>Why this needs the trace.</b> The bytes the client receives are a valid
 * 2xx response; the only evidence of failure lives in a downstream span that
 * never touches the response path. No response-level oracle can see it — not a
 * status/schema check, not MIST's own LLM soft-error check (the body is a clean
 * success). It is observable only in the distributed trace. The detector uses no
 * learned data ({@code T = Void}), no human-authored span assertions, and no
 * trained normal-pattern baseline — it is a pure runtime structural check. It is
 * intentionally intent-<i>agnostic</i>; the system's intent-conditioning lives in
 * the negative-test generation and {@code TargetAttributionInvariant}, not here.
 *
 * <p><b>Entry identification.</b> The client-facing entry is the span matching
 * {@link #rootApiKey} (the endpoint under test). Earlier versions treated <i>any</i>
 * parentless span as a root and passed as soon as one was a server error; under
 * Jaeger's partial/service-scoped trace views a downstream 5xx can appear as a
 * co-root, which silently suppressed real findings. We now key off the matched
 * entry and treat every <i>other</i> server-error span as swallowed. When no span
 * matches the key we fall back to all parentless spans (precision degrades on
 * partial views — documented, not silent).
 *
 * <p><b>Server error</b> is {@code http >= 500} OR {@code otel.status_code = ERROR}
 * — deliberately NOT the {@code >= 400} definition, because a downstream 4xx is
 * typically benign control-flow (lookup miss, internal validation).
 *
 * <p><b>Confidence (ERROR vs WARN).</b> A swallowed span with {@code http >= 500}
 * is a synchronous call the caller waited on and masked → {@link
 * TraceShapeVerdict.Severity#ERROR} (a real swallowed failure that fails the
 * verdict). A span with only {@code otel=ERROR} (no HTTP 5xx) lacks that
 * synchronous-RPC evidence — it may be an internal/asynchronous/tolerated error
 * — so it is reported as {@link TraceShapeVerdict.Severity#WARN}: surfaced, but
 * non-blocking, so a deliberately-tolerated downstream error is not over-claimed
 * as a bug.
 */
public final class HiddenDownstreamFailureInvariant implements ShapeInvariant<Void> {

    public static final String KIND = "HIDDEN_DOWNSTREAM_FAILURE";

    private final String rootApiKey;

    public HiddenDownstreamFailureInvariant(String rootApiKey) {
        this.rootApiKey = rootApiKey == null ? "" : rootApiKey;
    }

    @Override public String kind() { return KIND; }

    @Override public String rootApiKey() { return rootApiKey; }

    @Override
    public TraceShapeVerdict.InvariantOutcome evaluate(TraceModel trace) {
        if (trace == null || trace.getSpans() == null || trace.getSpans().isEmpty()) {
            return pass();
        }
        List<TraceModel.Span> spans = trace.getSpans();

        // Ground-truth client-facing status, injected by the writer from the ACTUAL
        // test response. MIST drives EXTERNAL entry points; the external client is not
        // itself traced, so the entry service's own inbound server span is frequently
        // absent or orphaned in the captured trace (its spans appear as parentless
        // client/egress spans). Trace topology then cannot reliably identify the entry
        // or confirm the caller saw success — which silently suppressed real findings.
        // When the writer supplies the real status we anchor on it; otherwise (offline
        // replay over a captured, fully-nested trace) we fall back to the topology
        // heuristic below.
        Integer clientStatus = injectedClientStatus(spans);
        if (clientStatus != null) {
            if (clientStatus < 200 || clientStatus >= 300) {
                return pass(); // caller saw the failure (or a client error) — not hidden
            }
            // Caller got a clean 2xx: every server-errored span in the trace was swallowed.
            return scanSwallowed(spans, java.util.Collections.<String>emptySet());
        }

        // --- Topology fallback (no injected client status) ---
        // Identify the client-facing entry: prefer spans matching rootApiKey;
        // fall back to all parentless spans when nothing matches.
        List<TraceModel.Span> roots = trace.roots();
        Set<String> entryIds = new HashSet<>();
        List<TraceModel.Span> entries = new ArrayList<>();
        for (TraceModel.Span r : roots) {
            if (isEntry(r)) { entries.add(r); entryIds.add(r.spanId); }
        }
        if (entries.isEmpty()) {
            entries = roots;
            for (TraceModel.Span r : roots) entryIds.add(r.spanId);
        }
        // If the entry itself surfaced a server error, the failure was LOUD
        // (already visible to the caller) — not hidden.
        boolean entry2xx = false;
        for (TraceModel.Span e : entries) {
            if (isServerError(e)) return pass();
            if (is2xx(e)) entry2xx = true;
        }
        if (!entry2xx) return pass();
        return scanSwallowed(spans, entryIds);
    }

    /** The writer-injected client-facing HTTP status (the real test response code), or null. */
    private static Integer injectedClientStatus(List<TraceModel.Span> spans) {
        for (TraceModel.Span s : spans) {
            String v = s.tags == null ? null : s.tags.get("mist.client.status");
            if (v != null && !v.isEmpty()) {
                try { return Integer.parseInt(v.trim()); } catch (NumberFormatException ignore) { }
            }
        }
        return null;
    }

    /**
     * Collect every server-errored span (excluding the given entry ids) as swallowed,
     * and build the verdict. The caller has already established the client saw a 2xx.
     */
    private TraceShapeVerdict.InvariantOutcome scanSwallowed(List<TraceModel.Span> spans, Set<String> entryIds) {
        List<String> swallowed = new ArrayList<>();
        boolean anyHttp5xx = false;
        StringBuilder detail = new StringBuilder();
        for (TraceModel.Span s : spans) {
            if (entryIds.contains(s.spanId) || !isServerError(s)) continue;
            swallowed.add(s.spanId);
            if (s.httpStatus >= 500) anyHttp5xx = true;
            if (detail.length() > 0) detail.append("; ");
            // Direction-explicit: <caller/recorder> ──▶ <callee/target>. OTel records a failed
            // downstream call on the CALLER's client span, so s.service is the caller (upstream)
            // and s.operation is the callee (the downstream that actually failed).
            detail.append(s.service).append(" ──▶ ").append(s.operation)
                  .append(" (http=").append(s.httpStatus).append(" otel=").append(s.otelStatus).append(")");
        }
        if (swallowed.isEmpty()) return pass();
        TraceShapeVerdict.Severity sev = anyHttp5xx
                ? TraceShapeVerdict.Severity.ERROR
                : TraceShapeVerdict.Severity.WARN;
        // The verdict message is JUST the concise relation(s) — "caller ──▶ callee (codes)".
        // The "client got 2xx, swallowed" framing belongs to the consumer (console summary,
        // .txt report, Allure 🕳️ box), which already say it; repeating it here double-printed it.
        return TraceShapeVerdict.InvariantOutcome.fail(
                KIND, rootApiKey, sev,
                (swallowed.size() > 1 ? swallowed.size() + " swallowed downstream call(s): " : "")
                        + detail,
                swallowed);
    }

    /** True if this span is the endpoint under test (the client-facing entry). */
    private boolean isEntry(TraceModel.Span s) {
        if (rootApiKey.isEmpty() || s == null) return false;
        String op = s.operation == null ? "" : s.operation;
        if (op.equalsIgnoreCase(rootApiKey)) return true;
        int sp = rootApiKey.indexOf(' ');
        String path = sp >= 0 ? rootApiKey.substring(sp + 1).trim() : rootApiKey;
        if (path.isEmpty()) return false;
        if (op.equalsIgnoreCase(path) || op.endsWith(path)) return true;
        String url = s.tags.get("http.url");
        if (url == null) url = s.tags.get("http.target");
        return url != null && url.contains(path);
    }

    private TraceShapeVerdict.InvariantOutcome pass() {
        return TraceShapeVerdict.InvariantOutcome.pass(KIND, rootApiKey, TraceShapeVerdict.Severity.ERROR);
    }

    private static boolean isServerError(TraceModel.Span s) {
        if (s == null) return false;
        return s.httpStatus >= 500 || "ERROR".equalsIgnoreCase(s.otelStatus);
    }

    private static boolean is2xx(TraceModel.Span s) {
        return s != null && s.httpStatus >= 200 && s.httpStatus < 300;
    }
}
