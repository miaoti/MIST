package io.mist.core.oracle.shape.invariant;

import io.mist.core.oracle.attribution.AttributionVerdict;
import io.mist.core.oracle.attribution.TraceAttribution;
import io.mist.core.oracle.shape.ShapeInvariant;
import io.mist.core.oracle.shape.TraceModel;
import io.mist.core.oracle.shape.TraceShapeVerdict;

import java.util.Collections;

/**
 * Phase 2 part 2 (FIXES.md F1+F3): join the TraceShapeOracle invariant
 * family. Wraps {@link TraceAttribution#attribute} in the
 * {@link ShapeInvariant} interface so attribution is gated by the same
 * ablation framework as the other four invariants and so the verdict
 * carries the attribution outcome alongside the others (the report
 * renderer then collapses it uniformly).
 *
 * <p>Unlike the other four invariants, this one has no learned data ({@code
 * T = Void}) and no on-disk persistence — it's a pure runtime classifier
 * given the trace and the per-test target context.
 *
 * <p>Outcome semantics:
 * <ul>
 *   <li>{@link AttributionVerdict#TARGET_REJECTION} → {@code passed=true}.
 *       The negative test landed on its target — that's the success path.</li>
 *   <li>{@link AttributionVerdict#NO_ATTRIBUTION} → {@code passed=true}.
 *       Insufficient evidence; not a deviation.</li>
 *   <li>{@link AttributionVerdict#UPSTREAM_REJECTION} → {@code passed=false}.
 *       The SUT rejected on a different service; signal of pool pollution
 *       or upstream issue, not our target.</li>
 *   <li>{@link AttributionVerdict#WRONG_PARAM_REJECTION} → {@code passed=false}.
 *       Right service but wrong parameter; Sniper landed near, not on.</li>
 * </ul>
 *
 * <p>Severity is always {@link TraceShapeVerdict.Severity#INFO} — these are
 * diagnostic classifications, not schema violations. The detail field
 * carries the {@link AttributionVerdict#name()} so
 * {@code FaultDetectionTracker.recordVerdict} can route it into the
 * attribution histogram on the matching {@code OracleAnomaly}.
 */
public final class TargetAttributionInvariant implements ShapeInvariant<Void> {

    public static final String KIND = "TARGET_ATTRIBUTION";

    private final String rootApiKey;
    private final String targetService;
    private final String targetParam;

    public TargetAttributionInvariant(String rootApiKey, String targetService, String targetParam) {
        this.rootApiKey = rootApiKey == null ? "" : rootApiKey;
        this.targetService = targetService;
        this.targetParam = targetParam;
    }

    @Override public String kind() { return KIND; }

    @Override public String rootApiKey() { return rootApiKey; }

    @Override
    public TraceShapeVerdict.InvariantOutcome evaluate(TraceModel trace) {
        AttributionVerdict verdict;
        if (trace == null || targetService == null || targetService.isEmpty()) {
            verdict = AttributionVerdict.NO_ATTRIBUTION;
        } else {
            try {
                verdict = TraceAttribution.attribute(trace, targetService, targetParam);
            } catch (Throwable t) {
                verdict = AttributionVerdict.NO_ATTRIBUTION;
            }
        }
        boolean passed = verdict == AttributionVerdict.TARGET_REJECTION
                       || verdict == AttributionVerdict.NO_ATTRIBUTION;
        if (passed) {
            return new TraceShapeVerdict.InvariantOutcome(
                    KIND, rootApiKey, true,
                    TraceShapeVerdict.Severity.INFO,
                    verdict.name(),
                    Collections.emptyList());
        }
        return TraceShapeVerdict.InvariantOutcome.fail(
                KIND, rootApiKey,
                TraceShapeVerdict.Severity.INFO,
                verdict.name(),
                Collections.emptyList());
    }
}
