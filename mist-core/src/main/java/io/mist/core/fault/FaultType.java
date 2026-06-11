package io.mist.core.fault;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Data carrier for a fault category. Replaces the fixed (now retired)
 * legacy {@code InvalidInputType} enum with an identity-driven value
 * object that the registry can mine and overlay.
 *
 * <p>Identity is the {@link #id()} string. The id of a {@link FaultSource#DEFAULT}
 * type matches the legacy enum name byte-for-byte so existing Allure
 * attachments and reports keep working through the migration.
 *
 * <p>Java 11 — not a record.
 */
public final class FaultType {

    public enum FaultSource { DEFAULT, MINED }

    private final String id;
    private final String displayName;
    private final Set<String> applicableTo;
    private final Set<String> applicableLocations;
    private final FaultSource source;

    public FaultType(String id,
                     String displayName,
                     Set<String> applicableTo,
                     Set<String> applicableLocations,
                     FaultSource source) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("FaultType.id must not be null or empty");
        }
        this.id = id;
        this.displayName = displayName == null ? id : displayName;
        this.applicableTo = Collections.unmodifiableSet(new LinkedHashSet<>(applicableTo));
        this.applicableLocations = Collections.unmodifiableSet(new LinkedHashSet<>(applicableLocations));
        this.source = source == null ? FaultSource.DEFAULT : source;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public Set<String> applicableTo() { return applicableTo; }
    public Set<String> applicableLocations() { return applicableLocations; }
    public FaultSource source() { return source; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FaultType)) return false;
        FaultType that = (FaultType) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "FaultType{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", applicableTo=" + applicableTo +
                ", applicableLocations=" + applicableLocations +
                ", source=" + source +
                '}';
    }
}
