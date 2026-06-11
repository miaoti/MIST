package io.mist.core.policy;

/**
 * Per-endpoint testing strategy. Replaces the previous one-size-fits-all
 * fixed thresholds in {@code MistGenerator} (K_ZERO_STEP=3, K_DEDUP_EXHAUSTED=10,
 * unconditional payload-fingerprint dedup) with values resolved per HTTP method,
 * OpenAPI hints, and parameter-domain size.
 *
 * <p>{@link #LEGACY} reproduces the historical fixed values exactly. Resolved
 * policies adjust thresholds for endpoints whose properties don't fit those
 * defaults (e.g. POST gets {@link DedupMode#OFF} because the response is
 * state-dependent; small-domain GETs get a higher {@code kDedupExhausted}
 * because their fingerprint space is small by construction).
 */
public final class EndpointPolicy {

    public enum DedupMode {
        /** Hash request method + path + body + query + path params. Current behavior. */
        PAYLOAD,
        /** Hash request + response-class (status + body fingerprint). Phase 2 only. */
        RESPONSE_AWARE,
        /** Emit every variant. For state-dependent endpoints where same request can have different responses. */
        OFF
    }

    private final DedupMode dedupMode;
    private final int kDedupExhausted;
    private final int kZeroStep;
    private final int variantBudget;

    public EndpointPolicy(DedupMode dedupMode, int kDedupExhausted, int kZeroStep, int variantBudget) {
        this.dedupMode = dedupMode;
        this.kDedupExhausted = kDedupExhausted;
        this.kZeroStep = kZeroStep;
        this.variantBudget = variantBudget;
    }

    public DedupMode dedupMode()    { return dedupMode; }
    public int kDedupExhausted()    { return kDedupExhausted; }
    public int kZeroStep()          { return kZeroStep; }
    /** -1 = no cap (caller uses its own budget). */
    public int variantBudget()      { return variantBudget; }

    /** Historical fixed defaults — used when {@code mst.adaptive.enabled=false}. */
    public static final EndpointPolicy LEGACY =
            new EndpointPolicy(DedupMode.PAYLOAD, 10, 3, -1);
}
