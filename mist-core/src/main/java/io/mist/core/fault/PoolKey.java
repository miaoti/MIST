package io.mist.core.fault;

import java.util.Objects;

/**
 * Composite key for the faulty-parameter pool index — the {@code (paramName,
 * paramLocation)} pair that keeps two parameters of the same name but
 * different locations (e.g. {@code path-vs-query userId}) from sharing a
 * fault pool entry.
 *
 * <p>{@code paramLocation} must be a value previously normalised by the
 * generator's {@code normaliseParamLocation(String)} helper so equality is
 * reliable across spec-authoring quirks (case differences, OpenAPI-2
 * {@code formData}, null/empty defaults).
 *
 * <p>Used by {@link InvalidInputPool} consumers in the workflow pipeline.
 */
public final class PoolKey {
    private final String paramName;
    private final String paramLocation;

    public PoolKey(String paramName, String paramLocation) {
        this.paramName = paramName;
        this.paramLocation = paramLocation;
    }

    public String getParamName()     { return paramName; }
    public String getParamLocation() { return paramLocation; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PoolKey)) return false;
        PoolKey k = (PoolKey) o;
        return Objects.equals(paramName, k.paramName)
                && Objects.equals(paramLocation, k.paramLocation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paramName, paramLocation);
    }

    @Override
    public String toString() {
        return paramName + "@" + paramLocation;
    }
}
