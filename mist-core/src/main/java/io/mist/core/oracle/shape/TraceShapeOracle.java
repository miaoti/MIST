package io.mist.core.oracle.shape;

import io.mist.core.config.MstConfig;
import io.mist.core.oracle.shape.invariant.HiddenDownstreamFailureInvariant;
import io.mist.core.oracle.shape.invariant.ResponseEnvelopeInvariant;
import io.mist.core.oracle.shape.invariant.SpanTreeShapeInvariant;
import io.mist.core.oracle.shape.invariant.StatusPropagationInvariant;
import io.mist.core.oracle.shape.invariant.TargetAttributionInvariant;
import io.mist.core.oracle.shape.invariant.TimingEnvelopeInvariant;

/**
 * Top-level entry point for runtime evaluation. Runs the enabled invariant
 * kinds for a given root API — the four learned structural invariants loaded
 * from the {@link ShapeInvariantStore} plus the two evaluation-only invariants
 * (target-attribution, hidden-downstream-failure) — against a {@link TraceModel}.
 * The aggregate verdict's {@code passed} flag is true iff every ERROR-severity
 * invariant outcome is true.
 *
 * <p>Each invariant is gated by a flag carried on the
 * {@link MstConfig.Oracle} record passed to the constructor. When the
 * whole-oracle gate is off, {@link #evaluate} returns
 * {@link TraceShapeVerdict#empty()} without loading any invariant; when
 * one per-invariant gate is off, that invariant's load + evaluate calls
 * are skipped while the rest still run.
 */
public final class TraceShapeOracle {

    private final ShapeInvariantStore store;
    private final boolean shapeOracleEnabled;
    private final boolean spanTreeEnabled;
    private final boolean statusPropagationEnabled;
    private final boolean responseEnvelopeEnabled;
    private final boolean timingEnvelopeEnabled;
    private final boolean targetAttributionEnabled;
    private final boolean hiddenDownstreamFailureEnabled;

    /** Optional runtime classifier propagated to ResponseEnvelopeInvariant (null => legacy permissive). */
    private ResponseEnvelopeInvariant.EnvelopeClassifier envelopeClassifier;

    public TraceShapeOracle(ShapeInvariantStore store, MstConfig.Oracle oracleConfig) {
        this.store = store == null ? new ShapeInvariantStore() : store;
        MstConfig.Oracle cfg = oracleConfig == null ? new MstConfig.Oracle() : oracleConfig;
        this.shapeOracleEnabled = cfg.shapeOracleEnabled();
        this.spanTreeEnabled = cfg.spanTreeInvariantEnabled();
        this.statusPropagationEnabled = cfg.statusPropagationInvariantEnabled();
        this.responseEnvelopeEnabled = cfg.responseEnvelopeInvariantEnabled();
        this.timingEnvelopeEnabled = cfg.timingEnvelopeInvariantEnabled();
        this.targetAttributionEnabled = cfg.targetAttributionInvariantEnabled();
        this.hiddenDownstreamFailureEnabled = cfg.hiddenDownstreamFailureInvariantEnabled();
    }

    public TraceShapeOracle(ShapeInvariantStore store) {
        this(store, MstConfig.instance().oracle());
    }

    /**
     * Wire a runtime envelope classifier (typically LLM-backed) used by
     * {@link ResponseEnvelopeInvariant} to classify a previously-unseen 2xx
     * {@code primaryField} value once, then cache it. Null (the default) keeps the
     * legacy permissive behaviour. Returns {@code this} for chaining.
     */
    public TraceShapeOracle setEnvelopeClassifier(ResponseEnvelopeInvariant.EnvelopeClassifier classifier) {
        this.envelopeClassifier = classifier;
        return this;
    }

    public TraceShapeVerdict evaluate(TraceModel trace, String rootApiKey) {
        return evaluate(trace, rootApiKey, null, null);
    }

    /**
     * Phase 2 part 2 (FIXES.md F1+F3): negative-test-aware overload. When
     * {@code targetService} is non-null and the target-attribution invariant
     * is enabled, appends a {@link TargetAttributionInvariant} outcome to
     * the verdict so the report can render the attribution classification
     * (TARGET / WRONG_PARAM / UPSTREAM / NO_ATTRIBUTION) alongside the four
     * structural invariants.
     *
     * <p>Setting {@code mst.oracle.shape.invariants.target_attribution.enabled
     * =false} skips the invariant entirely — no
     * {@link io.mist.core.oracle.attribution.TraceAttribution#attribute
     * TraceAttribution.attribute} call, no outcome appended, no perf cost.
     *
     * <p>Note: the whole-oracle gate {@code mst.oracle.shape.enabled=false}
     * also disables this overload — it short-circuits to
     * {@link TraceShapeVerdict#empty()} before any invariant runs. To
     * surface attribution without the four structural invariants, leave
     * {@code mst.oracle.shape.enabled=true} and toggle the per-invariant
     * flags off individually.
     */
    public TraceShapeVerdict evaluate(TraceModel trace, String rootApiKey,
                                      String targetService, String targetParam) {
        if (!shapeOracleEnabled) return TraceShapeVerdict.empty();
        TraceShapeVerdict.Builder builder = TraceShapeVerdict.builder();
        if (spanTreeEnabled) {
            builder.add(SpanTreeShapeInvariant.load(rootApiKey, store).evaluate(trace));
        }
        if (statusPropagationEnabled) {
            builder.add(StatusPropagationInvariant.load(rootApiKey, store).evaluate(trace));
        }
        if (timingEnvelopeEnabled) {
            builder.add(TimingEnvelopeInvariant.load(rootApiKey, store).evaluate(trace));
        }
        if (responseEnvelopeEnabled) {
            builder.add(ResponseEnvelopeInvariant.load(rootApiKey, store)
                    .withClassifier(envelopeClassifier, store)
                    .evaluate(trace));
        }
        if (targetAttributionEnabled
                && targetService != null && !targetService.isEmpty()) {
            builder.add(new TargetAttributionInvariant(rootApiKey, targetService, targetParam)
                    .evaluate(trace));
        }
        // Phase 3: intent-agnostic trace detector (opt-in) — flags a swallowed
        // downstream failure that response-level / soft-error oracles cannot see.
        if (hiddenDownstreamFailureEnabled) {
            builder.add(new HiddenDownstreamFailureInvariant(rootApiKey).evaluate(trace));
        }
        return builder.build();
    }
}
