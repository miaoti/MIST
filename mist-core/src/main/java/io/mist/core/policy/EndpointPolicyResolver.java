package io.mist.core.policy;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves an {@link EndpointPolicy} for a (method, path) endpoint using a
 * layered decision: HTTP-method default → OpenAPI {@code x-mist-*} hints.
 *
 * <p>Layer 1 — HTTP method semantics (RFC 7231):
 * <ul>
 *   <li>GET, HEAD, OPTIONS: safe & nullipotent → {@link EndpointPolicy.DedupMode#PAYLOAD}</li>
 *   <li>POST, PATCH: not idempotent → {@link EndpointPolicy.DedupMode#OFF}</li>
 *   <li>PUT, DELETE: idempotent in spec but server may diverge → {@link EndpointPolicy.DedupMode#PAYLOAD}</li>
 * </ul>
 *
 * <p>Layer 2 — OpenAPI extension hints (case-insensitive):
 * {@code x-mist-dedup-mode}, {@code x-mist-stateful}, {@code x-mist-k-dedup-exhausted},
 * {@code x-mist-variant-budget}.
 *
 * <p>Layer 4 (live probe) deliberately not implemented — phase 2 scope.
 */
public final class EndpointPolicyResolver {

    private final int defaultKDedupExhausted;
    private final int defaultKZeroStep;

    public EndpointPolicyResolver(int defaultKDedupExhausted, int defaultKZeroStep) {
        this.defaultKDedupExhausted = defaultKDedupExhausted;
        this.defaultKZeroStep = defaultKZeroStep;
    }

    /** Defaults to historical legacy values (10, 3). */
    public EndpointPolicyResolver() {
        this(EndpointPolicy.LEGACY.kDedupExhausted(), EndpointPolicy.LEGACY.kZeroStep());
    }

    /** Resolve a policy given method + path + optional OpenAPI extensions map. */
    public EndpointPolicy resolve(String httpMethod, String pathTemplate, Map<String, Object> extensions) {
        EndpointPolicy.DedupMode mode = methodDefault(httpMethod);
        int kDedup = methodKDedup(httpMethod);
        int kZero = defaultKZeroStep;
        // Default variant budget. POST/PATCH carry DedupMode.OFF so the variant
        // loop has no fingerprint-based natural cap; without a budget, the
        // exhaustive fault-injection queue (params × fault-types) could grow to
        // hundreds of variants → multi-MB single-file Java sources → javac OOM
        // at default heap. Other methods stay -1 because PAYLOAD dedup caps
        // them organically via K_DEDUP_EXHAUSTED.
        int variantBudget = methodVariantBudget(httpMethod);

        if (extensions != null && !extensions.isEmpty()) {
            Object hintMode = lookupCaseInsensitive(extensions, "x-mist-dedup-mode");
            if (hintMode instanceof String) {
                EndpointPolicy.DedupMode parsed = parseMode((String) hintMode);
                if (parsed != null) mode = parsed;
            }
            Object stateful = lookupCaseInsensitive(extensions, "x-mist-stateful");
            if (truthy(stateful)) mode = EndpointPolicy.DedupMode.OFF;

            Integer parsedKd = toInt(lookupCaseInsensitive(extensions, "x-mist-k-dedup-exhausted"));
            if (parsedKd != null && parsedKd > 0) kDedup = parsedKd;

            Integer parsedVb = toInt(lookupCaseInsensitive(extensions, "x-mist-variant-budget"));
            if (parsedVb != null && parsedVb > 0) variantBudget = parsedVb;
        }

        return new EndpointPolicy(mode, kDedup, kZero, variantBudget);
    }

    public EndpointPolicy resolve(String httpMethod, String pathTemplate) {
        return resolve(httpMethod, pathTemplate, null);
    }

    // ─── Layer 1 ──────────────────────────────────────────────────────────

    private EndpointPolicy.DedupMode methodDefault(String method) {
        if (method == null) return EndpointPolicy.DedupMode.PAYLOAD;
        String m = method.toUpperCase(Locale.ROOT);
        switch (m) {
            case "GET":
            case "HEAD":
            case "OPTIONS":
                return EndpointPolicy.DedupMode.PAYLOAD;
            case "POST":
            case "PATCH":
                return EndpointPolicy.DedupMode.OFF;
            case "PUT":
            case "DELETE":
            default:
                return EndpointPolicy.DedupMode.PAYLOAD;
        }
    }

    private int methodKDedup(String method) {
        if (method == null) return defaultKDedupExhausted;
        String m = method.toUpperCase(Locale.ROOT);
        switch (m) {
            case "GET":
            case "HEAD":
            case "OPTIONS":
                // read-side: small fingerprint space → tolerate more dup retries
                return Math.max(defaultKDedupExhausted, 25);
            default:
                return defaultKDedupExhausted;
        }
    }

    /**
     * Default per-method variant budget. Returned by {@link #resolve} so
     * MistGenerator can hard-cap how many variants a single scenario emits.
     * Only POST/PATCH (which get {@link EndpointPolicy.DedupMode#OFF} by
     * Layer 1) need this cap — their variant loop has no fingerprint-based
     * stopping rule, so the exhaustive fault queue can run unbounded and the
     * resulting test file outgrows javac. 50 is empirically chosen: large
     * enough to cover Thompson-ranked high-value fault targets, small enough
     * that even worst-case single-file Java sources stay under ~3 MB.
     */
    private int methodVariantBudget(String method) {
        if (method == null) return -1;
        String m = method.toUpperCase(Locale.ROOT);
        switch (m) {
            case "POST":
            case "PATCH":
                return 50;
            default:
                return -1;
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private static Object lookupCaseInsensitive(Map<String, Object> map, String key) {
        Object exact = map.get(key);
        if (exact != null) return exact;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        return null;
    }

    private static EndpointPolicy.DedupMode parseMode(String s) {
        if (s == null) return null;
        switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "payload":         return EndpointPolicy.DedupMode.PAYLOAD;
            case "off":             return EndpointPolicy.DedupMode.OFF;
            case "response-aware":  return EndpointPolicy.DedupMode.RESPONSE_AWARE;
            default:                return null;
        }
    }

    private static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String) {
            String s = (String) v;
            return s.equalsIgnoreCase("true") || s.equals("1");
        }
        return false;
    }

    private static Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Number)  return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt(((String) v).trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
