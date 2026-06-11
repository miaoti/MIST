package io.mist.core.fault;

import java.util.*;

/**
 * Manages invalid input values for a parameter, tracking which values have been used
 * in round-robin mode to ensure variety in negative testing.
 *
 * <p>Keyed on the String fault-type id (from {@link io.mist.core.fault.FaultType#id()}
 * loaded via {@link io.mist.core.fault.FaultTypeRegistry}). The eight default ids
 * — {@code TYPE_MISMATCH}, {@code REGEX_MISMATCH}, {@code SEMANTIC_MISMATCH},
 * {@code OVERFLOW}, {@code EMPTY_INPUT}, {@code NULL_INPUT}, {@code SPECIAL_CHARACTERS},
 * {@code BOUNDARY_VIOLATION} — are guaranteed to be valid keys.
 */
public class InvalidInputPool {

    private final String parameterName;
    private final String parameterType;

    private final Map<String, List<Object>> valuesByType;

    private final Map<String, Set<Integer>> usedIndicesByType;

    private int currentTypeIndex = 0;
    private final List<String> typeRotation;

    /**
     * Edge-case priority: high-risk types that catch real bugs are tested first.
     * This ordering ensures that boundary violations, overflows, and null/empty
     * inputs (the most likely to reveal server-side validation gaps) are fired
     * before lower-risk semantic mismatches.
     */
    private static final List<String> PRIORITIZED_TYPE_ORDER = Arrays.asList(
            "BOUNDARY_VIOLATION",
            "ENUM_VIOLATION",
            "OVERFLOW",
            "NULL_INPUT",
            "EMPTY_INPUT",
            "SPECIAL_CHARACTERS",
            "TYPE_MISMATCH",
            "REGEX_MISMATCH",
            "SEMANTIC_MISMATCH"
    );

    public InvalidInputPool(String parameterName, String parameterType) {
        this.parameterName = parameterName;
        this.parameterType = parameterType;
        this.valuesByType = new LinkedHashMap<>();
        this.usedIndicesByType = new LinkedHashMap<>();

        this.typeRotation = new ArrayList<>(PRIORITIZED_TYPE_ORDER);

        for (String type : PRIORITIZED_TYPE_ORDER) {
            valuesByType.put(type, new ArrayList<>());
            usedIndicesByType.put(type, new HashSet<>());
        }
    }

    /**
     * Add invalid value for a specific fault type id.
     */
    public void addValue(String type, Object value) {
        valuesByType.computeIfAbsent(type, k -> new ArrayList<>()).add(value);
        usedIndicesByType.computeIfAbsent(type, k -> new HashSet<>());
        if (!typeRotation.contains(type)) {
            typeRotation.add(type);
        }
    }

    private String lastSelectedType = null;

    /**
     * Get the next unused invalid input in round-robin fashion.
     * Rotates through types, then through values within each type.
     * Returns null when all values have been used.
     */
    public Object getNextRoundRobin() {
        int typesChecked = 0;

        while (typesChecked < typeRotation.size()) {
            String currentType = typeRotation.get(currentTypeIndex);
            List<Object> values = valuesByType.get(currentType);
            Set<Integer> usedIndices = usedIndicesByType.get(currentType);

            for (int i = 0; i < values.size(); i++) {
                if (!usedIndices.contains(i)) {
                    usedIndices.add(i);
                    Object value = values.get(i);

                    lastSelectedType = currentType;

                    currentTypeIndex = (currentTypeIndex + 1) % typeRotation.size();

                    return value;
                }
            }

            currentTypeIndex = (currentTypeIndex + 1) % typeRotation.size();
            typesChecked++;
        }

        lastSelectedType = null;
        return null;
    }

    /**
     * Get the fault-type id of the last selected value (for logging).
     */
    public String getLastSelectedType() {
        return lastSelectedType;
    }

    /**
     * Returns {@code true} if the pool still has at least one un-yielded value
     * remaining in the current round-robin cycle.
     *
     * <p>This is the correct exhaustion check to use instead of comparing the
     * return value of {@link #getNextRoundRobin()} to {@code null}, because
     * {@code getNextRoundRobin()} may legitimately return a stored Java
     * {@code null} (e.g. for the {@code NULL_INPUT} fault category), which
     * would otherwise be misinterpreted as the "pool exhausted" sentinel.
     */
    public boolean hasNextRoundRobin() {
        int totalUsed = 0;
        for (Set<Integer> used : usedIndicesByType.values()) {
            totalUsed += used.size();
        }
        return totalUsed < getTotalCount();
    }

    /**
     * Get a random invalid input (can repeat).
     */
    public Object getRandomValue(Random random) {
        List<String> availableTypes = new ArrayList<>();
        for (Map.Entry<String, List<Object>> e : valuesByType.entrySet()) {
            if (!e.getValue().isEmpty()) {
                availableTypes.add(e.getKey());
            }
        }

        if (availableTypes.isEmpty()) {
            return null;
        }

        String randomType = availableTypes.get(random.nextInt(availableTypes.size()));
        List<Object> values = valuesByType.get(randomType);

        return values.get(random.nextInt(values.size()));
    }

    /**
     * Reset usage tracking (for new test run).
     */
    public void resetUsage() {
        for (Set<Integer> indices : usedIndicesByType.values()) {
            indices.clear();
        }
        currentTypeIndex = 0;
    }

    /**
     * Get total number of invalid values across all types.
     */
    public int getTotalCount() {
        int count = 0;
        for (List<Object> values : valuesByType.values()) {
            count += values.size();
        }
        return count;
    }

    /**
     * Get count of values for a specific fault-type id.
     */
    public int getCountForType(String type) {
        List<Object> values = valuesByType.get(type);
        return values == null ? 0 : values.size();
    }

    /**
     * Check if all values have been used (round-robin mode).
     */
    public boolean allValuesUsed() {
        for (String type : typeRotation) {
            List<Object> values = valuesByType.get(type);
            Set<Integer> usedIndices = usedIndicesByType.get(type);
            if (values == null) continue;
            if (values.size() > (usedIndices == null ? 0 : usedIndices.size())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get details about the pool for logging.
     */
    public String getPoolSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Invalid Input Pool for '").append(parameterName).append("' (").append(parameterType).append("):\n");

        for (Map.Entry<String, List<Object>> e : valuesByType.entrySet()) {
            int count = e.getValue().size();
            if (count > 0) {
                sb.append("  - ").append(e.getKey()).append(": ").append(count).append(" values\n");
            }
        }

        sb.append("Total: ").append(getTotalCount()).append(" invalid values");
        return sb.toString();
    }

    public String getParameterName() {
        return parameterName;
    }

    public String getParameterType() {
        return parameterType;
    }
}
