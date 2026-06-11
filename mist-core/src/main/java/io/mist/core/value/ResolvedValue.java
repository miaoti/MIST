package io.mist.core.value;

import java.util.Objects;

/**
 * Immutable value carrier coupling a parameter's string representation with
 * the {@link ValueProvenance} describing how it was obtained.
 *
 * <p>Used by the smart-input pipeline and downstream test-case classifier;
 * see §III.L of the companion paper. Java 11 — not a record.
 */
public final class ResolvedValue {

    private final String value;
    private final ValueProvenance provenance;

    public ResolvedValue(String value, ValueProvenance provenance) {
        this.value = Objects.requireNonNull(value, "ResolvedValue.value");
        this.provenance = Objects.requireNonNull(provenance, "ResolvedValue.provenance");
    }

    public String value() { return value; }
    public ValueProvenance provenance() { return provenance; }

    public static ResolvedValue live(String v)      { return new ResolvedValue(v, ValueProvenance.RESOLVED_LIVE); }
    public static ResolvedValue cache(String v)     { return new ResolvedValue(v, ValueProvenance.RESOLVED_CACHE); }
    public static ResolvedValue llm(String v)       { return new ResolvedValue(v, ValueProvenance.LLM_GENERATED); }
    public static ResolvedValue synthetic(String v) { return new ResolvedValue(v, ValueProvenance.SYNTHETIC_PLACEHOLDER); }
    public static ResolvedValue mutated(String v)   { return new ResolvedValue(v, ValueProvenance.MUTATED_FROM_RESOLVED); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResolvedValue)) return false;
        ResolvedValue that = (ResolvedValue) o;
        return value.equals(that.value) && provenance == that.provenance;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, provenance);
    }

    @Override
    public String toString() {
        return value + "[" + provenance + "]";
    }
}
