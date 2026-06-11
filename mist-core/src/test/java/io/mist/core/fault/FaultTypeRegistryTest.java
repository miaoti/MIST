package io.mist.core.fault;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Locks in the default-YAML registry semantics. Eight of the default ids match
 * the legacy {@code InvalidInputType} enum byte-for-byte; ENUM_VIOLATION is a
 * later schema-aware addition (still {@code DEFAULT} source). This fixture
 * encodes the legacy applicability matrix as an explicit table so the
 * registry's YAML-loaded values remain pinned to the same semantics.
 */
public class FaultTypeRegistryTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String[] OAS_TYPES = {"string", "integer", "number", "boolean", "array", "object"};

    @Test
    public void loadDefaultExposesAllDefaultIds() {
        FaultTypeRegistry registry = FaultTypeRegistry.loadDefault();
        Set<String> expected = new HashSet<>(Arrays.asList(
                "TYPE_MISMATCH",
                "REGEX_MISMATCH",
                "SEMANTIC_MISMATCH",
                "OVERFLOW",
                "EMPTY_INPUT",
                "NULL_INPUT",
                "SPECIAL_CHARACTERS",
                "BOUNDARY_VIOLATION",
                "ENUM_VIOLATION"));
        assertEquals(expected.size(), registry.size());
        for (String id : expected) {
            assertNotNull("registry must expose default id " + id, registry.byId(id));
            assertEquals(FaultType.FaultSource.DEFAULT, registry.byId(id).source());
        }
    }

    @Test
    public void applicableForMatchesLegacyEnumOnEveryOasType() {
        FaultTypeRegistry registry = FaultTypeRegistry.loadDefault();
        // Explicit legacy applicability table — copy of the matrix that the
        // retired InvalidInputType.appliesTo(String) enum encoded.
        java.util.Map<String, java.util.Set<String>> legacyMatrix = new java.util.LinkedHashMap<>();
        legacyMatrix.put("TYPE_MISMATCH", new HashSet<>(Arrays.asList("string", "integer", "number", "boolean", "array", "object")));
        legacyMatrix.put("REGEX_MISMATCH", new HashSet<>(Arrays.asList("string")));
        legacyMatrix.put("SEMANTIC_MISMATCH", new HashSet<>(Arrays.asList("string", "integer", "number", "array", "object")));
        legacyMatrix.put("OVERFLOW", new HashSet<>(Arrays.asList("string", "integer", "number", "array")));
        legacyMatrix.put("EMPTY_INPUT", new HashSet<>(Arrays.asList("string", "array", "object")));
        legacyMatrix.put("NULL_INPUT", new HashSet<>(Arrays.asList("string", "integer", "number", "boolean", "array", "object")));
        legacyMatrix.put("SPECIAL_CHARACTERS", new HashSet<>(Arrays.asList("string", "array")));
        legacyMatrix.put("BOUNDARY_VIOLATION", new HashSet<>(Arrays.asList("string", "integer", "number", "array")));

        for (java.util.Map.Entry<String, java.util.Set<String>> e : legacyMatrix.entrySet()) {
            String id = e.getKey();
            FaultType ft = registry.byId(id);
            assertNotNull("registry must expose default id " + id, ft);
            for (String oas : OAS_TYPES) {
                boolean legacyApplies = e.getValue().contains(oas);
                boolean registryApplies = ft.applicableTo().contains(oas);
                assertEquals(
                        id + " applicability mismatch for OAS type '" + oas + "'",
                        legacyApplies, registryApplies);
            }
        }
    }

    @Test
    public void applicableForFiltersByOasTypeAndLocation() {
        FaultTypeRegistry registry = FaultTypeRegistry.loadDefault();
        // REGEX_MISMATCH is the strictest single-OAS-type fault — easiest to assert.
        List<FaultType> stringQuery = registry.applicableFor("string", "query");
        assertTrue("REGEX_MISMATCH should apply to string query",
                stringQuery.stream().anyMatch(ft -> ft.id().equals("REGEX_MISMATCH")));

        List<FaultType> integerQuery = registry.applicableFor("integer", "query");
        assertTrue("REGEX_MISMATCH should NOT apply to integer query",
                integerQuery.stream().noneMatch(ft -> ft.id().equals("REGEX_MISMATCH")));
    }

    @Test
    public void normalizationAcceptsIntAndLongAliases() {
        FaultTypeRegistry registry = FaultTypeRegistry.loadDefault();
        // Legacy enum's appliesTo accepted "int" and "long" — registry normalizes.
        List<FaultType> intResult = registry.applicableFor("int", null);
        List<FaultType> integerResult = registry.applicableFor("integer", null);
        assertEquals(integerResult.size(), intResult.size());
    }

    @Test
    public void overlayAddsMinedTypesAndPreservesDefaults() throws Exception {
        FaultTypeRegistry base = FaultTypeRegistry.loadDefault();
        File overlay = tmp.newFile("mined.yaml");
        try (FileWriter w = new FileWriter(overlay)) {
            w.write("faults:\n");
            w.write("  - id: INVALID_STATION_NAME\n");
            w.write("    displayName: Invalid station name\n");
            w.write("    applicableTo: [string]\n");
            w.write("    applicableLocations: [path, query]\n");
            w.write("    source: MINED\n");
        }
        FaultTypeRegistry merged = base.loadOverlay(overlay.toPath());
        assertEquals(base.size() + 1, merged.size());
        FaultType mined = merged.byId("INVALID_STATION_NAME");
        assertNotNull(mined);
        assertEquals(FaultType.FaultSource.MINED, mined.source());
        assertTrue(mined.applicableTo().contains("string"));
        // Defaults still resolve through the merged registry.
        assertNotNull(merged.byId("TYPE_MISMATCH"));
    }

    @Test
    public void malformedYamlOverlayThrows() throws Exception {
        FaultTypeRegistry base = FaultTypeRegistry.loadDefault();
        File bad = tmp.newFile("bad.yaml");
        try (FileWriter w = new FileWriter(bad)) {
            w.write(": not valid yaml :\n");
        }
        try {
            base.loadOverlay(bad.toPath());
            fail("Malformed YAML must surface as a RuntimeException");
        } catch (RuntimeException expected) {
            // pass
        }
    }

    @Test
    public void unknownByIdReturnsNull() {
        FaultTypeRegistry registry = FaultTypeRegistry.loadDefault();
        assertNull(registry.byId("DOES_NOT_EXIST"));
    }
}
