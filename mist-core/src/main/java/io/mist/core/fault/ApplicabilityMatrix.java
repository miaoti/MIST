package io.mist.core.fault;

/**
 * Wraps the registry's {@code applicableFor(...)} logic into a single
 * {@link #applies(FaultType, String, String)} predicate. Per Phase 3.C of
 * PATH_B_REBUILD_PLAN.md the applicability matrix moves from code into data:
 * each {@link FaultType} now carries its own {@code applicableTo} and
 * {@code applicableLocations} sets and this class is the read side.
 *
 * <p>Either {@code oasType} or {@code location} may be {@code null}; an absent
 * axis is unfiltered, which mirrors the legacy enum's conservative
 * "when in doubt, applicable" behaviour.
 */
public final class ApplicabilityMatrix {

    private final FaultTypeRegistry registry;

    public ApplicabilityMatrix(FaultTypeRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("ApplicabilityMatrix: registry must not be null");
        }
        this.registry = registry;
    }

    public boolean applies(FaultType ft, String oasType, String location) {
        if (ft == null) return false;
        // Prefer the registered FaultType so registry overrides (e.g. mined
        // refinements of a default id) win over the caller's potentially stale copy.
        FaultType registered = registry.byId(ft.id());
        FaultType effective = registered == null ? ft : registered;
        return FaultTypeRegistry.matches(
                effective,
                FaultTypeRegistry.normalizeOasType(oasType),
                location == null ? null : location.toLowerCase());
    }

    public FaultTypeRegistry registry() {
        return registry;
    }
}
