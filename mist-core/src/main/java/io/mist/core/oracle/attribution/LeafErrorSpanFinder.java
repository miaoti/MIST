package io.mist.core.oracle.attribution;

import io.mist.core.oracle.shape.TraceModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 2: walks a {@link TraceModel} to find the <em>leaf error span</em> —
 * the deepest span in an unbroken error chain rooted at the trace's root.
 *
 * <p>Algorithm (Jha CLOUD'22 simplified):
 * <ol>
 *   <li>Build child index keyed by parent span id (single pass over spans).</li>
 *   <li>From each root, depth-first descend into error-tagged children only.</li>
 *   <li>Track the deepest error span on each path; return the deepest across
 *       all roots.</li>
 * </ol>
 *
 * <p>A span is considered error-tagged when its OTEL status is "ERROR" or
 * its HTTP status is &gt;= 400 (Jha's definition extended to client errors
 * for negative-test attribution).
 */
public final class LeafErrorSpanFinder {

    private LeafErrorSpanFinder() {}

    /**
     * Find the deepest error span reachable through an unbroken error chain
     * from any root. Returns {@code null} when no root span is error-tagged.
     */
    public static TraceModel.Span findLeafError(TraceModel trace) {
        if (trace == null) return null;
        List<TraceModel.Span> spans = trace.getSpans();
        if (spans == null || spans.isEmpty()) return null;

        Map<String, List<TraceModel.Span>> childrenByParent = new HashMap<>();
        for (TraceModel.Span s : spans) {
            String pid = s.parentSpanId;
            if (pid == null || pid.isEmpty()) continue;
            childrenByParent.computeIfAbsent(pid, k -> new ArrayList<>()).add(s);
        }

        TraceModel.Span deepest = null;
        int deepestDepth = -1;
        for (TraceModel.Span root : trace.roots()) {
            if (!isErrorTagged(root)) continue;
            int[] depthHolder = {0};
            TraceModel.Span found = descend(root, childrenByParent, 0, depthHolder);
            if (found != null && depthHolder[0] > deepestDepth) {
                deepest = found;
                deepestDepth = depthHolder[0];
            }
        }
        return deepest;
    }

    private static TraceModel.Span descend(TraceModel.Span span,
                                           Map<String, List<TraceModel.Span>> childrenByParent,
                                           int depth, int[] deepestDepthOut) {
        TraceModel.Span deepest = span;
        deepestDepthOut[0] = depth;
        List<TraceModel.Span> kids = childrenByParent.get(span.spanId);
        if (kids == null) return deepest;
        for (TraceModel.Span kid : kids) {
            if (!isErrorTagged(kid)) continue;
            int[] kidDepth = {depth + 1};
            TraceModel.Span sub = descend(kid, childrenByParent, depth + 1, kidDepth);
            if (sub != null && kidDepth[0] > deepestDepthOut[0]) {
                deepest = sub;
                deepestDepthOut[0] = kidDepth[0];
            }
        }
        return deepest;
    }

    /**
     * A span is error-tagged when:
     * <ul>
     *   <li>OTEL status code is "ERROR" (per OTEL spec), OR</li>
     *   <li>HTTP status is &gt;= 400 (client or server error)</li>
     * </ul>
     * Either condition is sufficient — different SUT instrumentations
     * populate one or both.
     */
    public static boolean isErrorTagged(TraceModel.Span span) {
        if (span == null) return false;
        if (span.otelStatus != null && "ERROR".equalsIgnoreCase(span.otelStatus)) return true;
        return span.httpStatus >= 400;
    }
}
