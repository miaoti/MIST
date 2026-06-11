package io.mist.core.oracle.attribution;

/**
 * Phase 2: result of walking a Jaeger trace's leaf-error span and asking
 * "did the SUT reject because of OUR targeted invalid parameter, or for
 * some unrelated upstream reason?"
 *
 * <p>Verdicts in increasing-confidence order:
 * <ul>
 *   <li>{@link #NO_ATTRIBUTION} — SUT returned non-2xx but no error span
 *       in the trace; algorithm can't reach a conclusion.</li>
 *   <li>{@link #UPSTREAM_REJECTION} — a service other than the target
 *       service raised the error; pool pollution or service-side bug,
 *       not our shot landing on the target.</li>
 *   <li>{@link #WRONG_PARAM_REJECTION} — the target service raised the
 *       error, but the leaf method's responsible-param set didn't include
 *       our target param; Sniper landed near, not on the target.</li>
 *   <li>{@link #TARGET_REJECTION} — target service raised the error AND
 *       the leaf method's responsible-param set includes our target
 *       param; confirmed bug-detection event.</li>
 * </ul>
 *
 * <p>Used by the Phase 0 report to upgrade an oracle anomaly into a
 * high-confidence detection.
 */
public enum AttributionVerdict {
    NO_ATTRIBUTION,
    UPSTREAM_REJECTION,
    WRONG_PARAM_REJECTION,
    TARGET_REJECTION
}
