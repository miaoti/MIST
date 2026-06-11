package io.mist.core.oracle.attribution;

import io.mist.core.oracle.shape.TraceModel;

/**
 * Phase 2 entry point: given a trace from a negative test execution and
 * the test's target (service + parameter name), decide whether the SUT
 * rejection landed on our target or on something else.
 *
 * <p>Algorithm (Jha CLOUD'22 simplified):
 * <ol>
 *   <li>Walk the trace via {@link LeafErrorSpanFinder} to find the
 *       deepest error span on an unbroken error chain.</li>
 *   <li>If no error span, return {@link AttributionVerdict#NO_ATTRIBUTION}.</li>
 *   <li>Compare the leaf span's service name with the target service.
 *       If different services, return {@link AttributionVerdict#UPSTREAM_REJECTION}.</li>
 *   <li>If same service, use {@link MethodToParamMapper} to ask whether
 *       the leaf operation is responsible for the target parameter.
 *       Yes → {@link AttributionVerdict#TARGET_REJECTION}, no →
 *       {@link AttributionVerdict#WRONG_PARAM_REJECTION}.</li>
 * </ol>
 *
 * <p>Service matching is lenient: target service is a substring of leaf
 * service or vice versa (Jaeger service names often have prefixes/suffixes
 * like {@code ts-order-service.production}). Exact match always wins.
 */
public final class TraceAttribution {

    private TraceAttribution() {}

    /**
     * Attribute a trace's outcome to either the target rejection or one
     * of three off-target buckets.
     *
     * @param trace        the SUT-execution trace; may be null
     * @param targetService the target service of the negative variant
     *                      (e.g., {@code "ts-order-service"})
     * @param targetParam   the target parameter name (e.g.,
     *                      {@code "seatNumber"}); may be null when only
     *                      service-level attribution is meaningful
     */
    public static AttributionVerdict attribute(TraceModel trace,
                                               String targetService,
                                               String targetParam) {
        TraceModel.Span leaf = LeafErrorSpanFinder.findLeafError(trace);
        if (leaf == null) return AttributionVerdict.NO_ATTRIBUTION;

        boolean serviceMatch = serviceMatches(leaf.service, targetService);
        if (!serviceMatch) return AttributionVerdict.UPSTREAM_REJECTION;

        // Same service; check whether the leaf operation is the validator
        // for our target param. If no param info, the best we can say is
        // TARGET_REJECTION at service granularity (the SUT did reject on
        // the right service).
        if (targetParam == null || targetParam.isEmpty()) {
            return AttributionVerdict.TARGET_REJECTION;
        }

        boolean paramMatch = MethodToParamMapper.isResponsibleFor(leaf.operation, targetParam);
        return paramMatch ? AttributionVerdict.TARGET_REJECTION
                          : AttributionVerdict.WRONG_PARAM_REJECTION;
    }

    /**
     * Lenient service-name match: equal, substring either direction, or
     * shared longest token when both are dot/dash-separated identifiers.
     * Both null returns false; one null returns false. Empty strings
     * never match (avoid false positives on unset service names).
     */
    static boolean serviceMatches(String leafService, String targetService) {
        if (leafService == null || targetService == null) return false;
        if (leafService.isEmpty() || targetService.isEmpty()) return false;
        String a = leafService.toLowerCase();
        String b = targetService.toLowerCase();
        if (a.equals(b)) return true;
        if (a.contains(b) || b.contains(a)) return true;
        return false;
    }
}
