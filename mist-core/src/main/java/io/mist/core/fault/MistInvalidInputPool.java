package io.mist.core.fault;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Parallel implementation of the legacy {@code InvalidInputPool}
 * (kept in mist-restest-adapter), keyed on {@link FaultType#id()} instead of
 * the retired {@code InvalidInputType} enum. Per Phase 3.D of PATH_B_REBUILD_PLAN.md.
 *
 * <p>Forward-compatible scaffolding only — the generator does not yet route
 * through this class. Once the swap is made the legacy pool is retired and
 * this class moves under the {@code inputs} package as the live pool.
 *
 * <p>The outer pool map shape
 * ({@code Map<String, Map<PoolKey, MistInvalidInputPool>>}) is unchanged from
 * Fix A-5b; Phase 3 only touches this inner fault dimension.
 */
public final class MistInvalidInputPool {

    private final String parameterName;
    private final String parameterType;

    private final Map<String, List<Object>> valuesByType = new LinkedHashMap<>();
    private final Map<String, Set<Integer>> usedIndicesByType = new LinkedHashMap<>();

    // Rotation order. Defaults from the registry's declared order go first, so
    // mined types do not steal slots from the legacy round-robin baseline.
    private final List<String> typeRotation = new ArrayList<>();

    private int currentTypeIndex = 0;
    private String lastSelectedTypeId = null;

    public MistInvalidInputPool(String parameterName, String parameterType, List<FaultType> rotationOrder) {
        this.parameterName = parameterName;
        this.parameterType = parameterType;
        if (rotationOrder == null || rotationOrder.isEmpty()) {
            throw new IllegalArgumentException(
                    "MistInvalidInputPool: rotationOrder must contain at least one FaultType");
        }
        for (FaultType ft : rotationOrder) {
            String id = ft.id();
            if (!valuesByType.containsKey(id)) {
                typeRotation.add(id);
                valuesByType.put(id, new ArrayList<>());
                usedIndicesByType.put(id, new HashSet<>());
            }
        }
    }

    public void addValue(String faultTypeId, Object value) {
        List<Object> values = valuesByType.get(faultTypeId);
        if (values == null) {
            throw new IllegalArgumentException(
                    "MistInvalidInputPool: unknown faultTypeId '" + faultTypeId
                            + "' (not in rotation for parameter " + parameterName + ")");
        }
        values.add(value);
    }

    /**
     * Round-robin selection. Rotates through fault type ids, then through
     * values within each type. Returns {@code null} once the pool is
     * exhausted — callers needing to distinguish a stored {@code null} value
     * (e.g. {@code NULL_INPUT}) from exhaustion should use
     * {@link #hasNextRoundRobin()}.
     */
    public Object getNextRoundRobin() {
        int typesChecked = 0;
        while (typesChecked < typeRotation.size()) {
            String currentId = typeRotation.get(currentTypeIndex);
            List<Object> values = valuesByType.get(currentId);
            Set<Integer> used = usedIndicesByType.get(currentId);
            for (int i = 0; i < values.size(); i++) {
                if (!used.contains(i)) {
                    used.add(i);
                    Object value = values.get(i);
                    lastSelectedTypeId = currentId;
                    currentTypeIndex = (currentTypeIndex + 1) % typeRotation.size();
                    return value;
                }
            }
            currentTypeIndex = (currentTypeIndex + 1) % typeRotation.size();
            typesChecked++;
        }
        lastSelectedTypeId = null;
        return null;
    }

    public boolean hasNextRoundRobin() {
        return !allValuesUsed();
    }

    public Object getRandomValue(Random random) {
        List<String> available = new ArrayList<>();
        for (Map.Entry<String, List<Object>> e : valuesByType.entrySet()) {
            if (!e.getValue().isEmpty()) available.add(e.getKey());
        }
        if (available.isEmpty()) return null;
        String pickedId = available.get(random.nextInt(available.size()));
        List<Object> values = valuesByType.get(pickedId);
        return values.get(random.nextInt(values.size()));
    }

    public void resetUsage() {
        for (Set<Integer> indices : usedIndicesByType.values()) {
            indices.clear();
        }
        currentTypeIndex = 0;
        lastSelectedTypeId = null;
    }

    public int getTotalCount() {
        int count = 0;
        for (List<Object> values : valuesByType.values()) count += values.size();
        return count;
    }

    public int getCountForType(String faultTypeId) {
        List<Object> values = valuesByType.get(faultTypeId);
        return values == null ? 0 : values.size();
    }

    public boolean allValuesUsed() {
        for (String id : typeRotation) {
            if (valuesByType.get(id).size() > usedIndicesByType.get(id).size()) return false;
        }
        return true;
    }

    public String getLastSelectedTypeId() {
        return lastSelectedTypeId;
    }

    public String getParameterName() {
        return parameterName;
    }

    public String getParameterType() {
        return parameterType;
    }

    public List<String> getTypeRotation() {
        return new ArrayList<>(typeRotation);
    }
}
