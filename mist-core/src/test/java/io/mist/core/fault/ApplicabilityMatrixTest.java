package io.mist.core.fault;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Parity table between the retired {@code InvalidInputType.appliesTo(String)}
 * enum semantics and the registry-driven {@link ApplicabilityMatrix}.
 * Phase 3.C of PATH_B_REBUILD_PLAN.md moves the applicability matrix from
 * code into data; this test pins the data to the legacy semantics on every
 * (oasType x category) pair.
 */
public class ApplicabilityMatrixTest {

    private static final String[] OAS_TYPES = {"string", "integer", "number", "boolean", "array", "object"};

    private static Map<String, Set<String>> legacyMatrix() {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        m.put("TYPE_MISMATCH", new HashSet<>(Arrays.asList("string", "integer", "number", "boolean", "array", "object")));
        m.put("REGEX_MISMATCH", new HashSet<>(Arrays.asList("string")));
        m.put("SEMANTIC_MISMATCH", new HashSet<>(Arrays.asList("string", "integer", "number", "array", "object")));
        m.put("OVERFLOW", new HashSet<>(Arrays.asList("string", "integer", "number", "array")));
        m.put("EMPTY_INPUT", new HashSet<>(Arrays.asList("string", "array", "object")));
        m.put("NULL_INPUT", new HashSet<>(Arrays.asList("string", "integer", "number", "boolean", "array", "object")));
        m.put("SPECIAL_CHARACTERS", new HashSet<>(Arrays.asList("string", "array")));
        m.put("BOUNDARY_VIOLATION", new HashSet<>(Arrays.asList("string", "integer", "number", "array")));
        return m;
    }

    @Test
    public void parityWithLegacyEnumOnEveryOasType() {
        FaultTypeRegistry registry = FaultTypeRegistry.loadDefault();
        ApplicabilityMatrix matrix = new ApplicabilityMatrix(registry);
        for (Map.Entry<String, Set<String>> e : legacyMatrix().entrySet()) {
            String id = e.getKey();
            FaultType ft = registry.byId(id);
            for (String oas : OAS_TYPES) {
                boolean legacyApplies = e.getValue().contains(oas);
                boolean matrixApplies = matrix.applies(ft, oas, null);
                assertEquals(
                        id + " applicability mismatch for OAS type '" + oas + "'",
                        legacyApplies, matrixApplies);
            }
        }
    }

    @Test
    public void nullOasTypeDefaultsToApplicableForAllDefaults() {
        FaultTypeRegistry registry = FaultTypeRegistry.loadDefault();
        ApplicabilityMatrix matrix = new ApplicabilityMatrix(registry);
        for (FaultType ft : registry.values()) {
            assertTrue(ft.id() + " should apply when oasType is null",
                    matrix.applies(ft, null, null));
        }
    }

    @Test
    public void locationFilterIsHonoured() {
        FaultTypeRegistry registry = FaultTypeRegistry.loadDefault();
        ApplicabilityMatrix matrix = new ApplicabilityMatrix(registry);
        FaultType regex = registry.byId("REGEX_MISMATCH");
        // All default locations are accepted.
        assertTrue(matrix.applies(regex, "string", "query"));
        assertTrue(matrix.applies(regex, "string", "path"));
        // Unknown location filters out.
        assertFalse(matrix.applies(regex, "string", "no-such-location"));
    }

    @Test
    public void aliasedOasTypesNormalize() {
        FaultTypeRegistry registry = FaultTypeRegistry.loadDefault();
        ApplicabilityMatrix matrix = new ApplicabilityMatrix(registry);
        FaultType overflow = registry.byId("OVERFLOW");
        // Legacy enum accepted "int" and "long" as integer aliases.
        assertTrue(matrix.applies(overflow, "int", null));
        assertTrue(matrix.applies(overflow, "long", null));
        assertTrue(matrix.applies(overflow, "double", null));
        assertTrue(matrix.applies(overflow, "float", null));
    }

    @Test
    public void nullFaultTypeIsNotApplicable() {
        FaultTypeRegistry registry = FaultTypeRegistry.loadDefault();
        ApplicabilityMatrix matrix = new ApplicabilityMatrix(registry);
        assertFalse(matrix.applies(null, "string", "query"));
    }
}
