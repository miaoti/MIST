package io.mist.core.oracle.shape;

/**
 * A learned property of a "known-good" trace shape, evaluable against a fresh
 * trace. Implementations have two sides: a learner that materialises some
 * data record of type {@code T} from a corpus and a runtime evaluator that
 * compares a candidate {@link TraceModel} against that record.
 *
 * @param <T> the implementation's learned-data record type
 */
public interface ShapeInvariant<T> {

    /** Stable identifier for this invariant family. Goes into verdict reports. */
    String kind();

    /** The root API (e.g. {@code "POST /api/v1/orders"}) this invariant scopes to. */
    String rootApiKey();

    /** Evaluate the trace and produce one outcome describing pass/fail and evidence. */
    TraceShapeVerdict.InvariantOutcome evaluate(TraceModel trace);
}
