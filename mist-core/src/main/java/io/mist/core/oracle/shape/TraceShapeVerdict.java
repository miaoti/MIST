package io.mist.core.oracle.shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Verdict produced by {@link TraceShapeOracle} for one trace. Aggregates the
 * outcomes of every {@link ShapeInvariant} that participated in the evaluation.
 *
 * <p>{@link #passed} is the AND of the boolean outcomes whose severity is
 * {@link Severity#ERROR}; lower-severity findings are reported as evidence but
 * do not flip the overall verdict.
 */
public final class TraceShapeVerdict {

    public enum Severity { ERROR, WARN, INFO }

    private final boolean passed;
    private final List<InvariantOutcome> outcomes;

    private TraceShapeVerdict(boolean passed, List<InvariantOutcome> outcomes) {
        this.passed = passed;
        this.outcomes = Collections.unmodifiableList(new ArrayList<>(outcomes));
    }

    /**
     * Verdict produced when the entire Trace Shape Oracle is gated off via
     * {@code mst.oracle.shape.enabled=false}. Always passes and carries
     * zero invariant outcomes so downstream consumers (writer, Allure
     * attachments) treat the verdict as a clean no-op instead of having
     * to special-case {@code null}.
     */
    public static TraceShapeVerdict empty() {
        return new TraceShapeVerdict(true, Collections.emptyList());
    }

    public boolean isPassed() { return passed; }
    public List<InvariantOutcome> getOutcomes() { return outcomes; }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(passed ? "PASS" : "FAIL").append(" (").append(outcomes.size()).append(" invariants)");
        for (InvariantOutcome o : outcomes) {
            sb.append("\n  - ").append(o.kind).append(" [").append(o.severity)
              .append("] ").append(o.passed ? "ok" : "violation");
            if (o.detail != null && !o.detail.isEmpty()) {
                sb.append(": ").append(o.detail);
            }
        }
        return sb.toString();
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<InvariantOutcome> outcomes = new ArrayList<>();

        public Builder add(InvariantOutcome outcome) {
            outcomes.add(outcome);
            return this;
        }

        public TraceShapeVerdict build() {
            boolean passed = true;
            for (InvariantOutcome o : outcomes) {
                if (o.severity == Severity.ERROR && !o.passed) {
                    passed = false;
                    break;
                }
            }
            return new TraceShapeVerdict(passed, outcomes);
        }
    }

    public static final class InvariantOutcome {
        public final String kind;
        public final String rootApiKey;
        public final boolean passed;
        public final Severity severity;
        public final String detail;
        public final List<String> evidenceSpanIds;

        public InvariantOutcome(String kind, String rootApiKey, boolean passed,
                                Severity severity, String detail,
                                List<String> evidenceSpanIds) {
            this.kind = kind;
            this.rootApiKey = rootApiKey;
            this.passed = passed;
            this.severity = severity;
            this.detail = detail == null ? "" : detail;
            this.evidenceSpanIds = evidenceSpanIds == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(evidenceSpanIds));
        }

        public static InvariantOutcome pass(String kind, String rootApiKey, Severity severity) {
            return new InvariantOutcome(kind, rootApiKey, true, severity, "", Collections.emptyList());
        }

        public static InvariantOutcome fail(String kind, String rootApiKey, Severity severity,
                                            String detail, List<String> evidenceSpanIds) {
            return new InvariantOutcome(kind, rootApiKey, false, severity, detail, evidenceSpanIds);
        }
    }
}
