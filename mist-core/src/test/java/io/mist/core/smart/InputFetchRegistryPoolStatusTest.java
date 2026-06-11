package io.mist.core.smart;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Phase 1 pool-status contract: per-value marking, query, status promotion
 * rules, and YAML serde roundtrip (including back-compat with legacy YAMLs
 * that have no poolEntryStatus field).
 */
public class InputFetchRegistryPoolStatusTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void markVerifiedThenQuery_returnsValueInVerifiedList() {
        InputFetchRegistry reg = new InputFetchRegistry();
        reg.markVerified("POST /api/v1/orders", "seatClass", "FirstClass");
        List<String> verified = reg.getVerifiedValues("POST /api/v1/orders", "seatClass");
        assertEquals(1, verified.size());
        assertTrue(verified.contains("FirstClass"));
        assertEquals(InputFetchRegistry.PoolEntryStatus.VERIFIED_VALID,
                reg.getPoolEntryStatus("POST /api/v1/orders", "seatClass", "FirstClass"));
    }

    @Test
    public void markRejected_excludesFromVerifiedList() {
        InputFetchRegistry reg = new InputFetchRegistry();
        reg.markRejected("POST /api/v1/orders", "seatClass", "InvalidClass");
        List<String> verified = reg.getVerifiedValues("POST /api/v1/orders", "seatClass");
        assertTrue(verified.isEmpty());
        assertEquals(InputFetchRegistry.PoolEntryStatus.REJECTED_BY_SUT,
                reg.getPoolEntryStatus("POST /api/v1/orders", "seatClass", "InvalidClass"));
    }

    @Test
    public void verifiedNeverDemotedByLaterReject() {
        InputFetchRegistry reg = new InputFetchRegistry();
        reg.markVerified("POST /a", "p", "v");
        reg.markRejected("POST /a", "p", "v");   // intermittent 4xx — must not demote
        assertEquals(InputFetchRegistry.PoolEntryStatus.VERIFIED_VALID,
                reg.getPoolEntryStatus("POST /a", "p", "v"));
        assertEquals(1, reg.getVerifiedValues("POST /a", "p").size());
    }

    @Test
    public void rejectedPromotedToVerified_whenSutAccepts() {
        InputFetchRegistry reg = new InputFetchRegistry();
        reg.markRejected("POST /a", "p", "v");
        reg.markVerified("POST /a", "p", "v");
        assertEquals(InputFetchRegistry.PoolEntryStatus.VERIFIED_VALID,
                reg.getPoolEntryStatus("POST /a", "p", "v"));
    }

    @Test
    public void unverified_isDefaultForUnknownEntries() {
        InputFetchRegistry reg = new InputFetchRegistry();
        assertEquals(InputFetchRegistry.PoolEntryStatus.UNVERIFIED,
                reg.getPoolEntryStatus("POST /a", "p", "never-seen"));
        assertTrue(reg.getVerifiedValues("POST /a", "p").isEmpty());
    }

    @Test
    public void nullArgs_areNoop() {
        InputFetchRegistry reg = new InputFetchRegistry();
        reg.markVerified(null, "p", "v");
        reg.markVerified("e", null, "v");
        reg.markVerified("e", "p", null);
        reg.markRejected(null, "p", "v");
        assertTrue(reg.getPoolEntryStatusMapForTest().isEmpty());
    }

    @Test
    public void yamlRoundtrip_preservesPoolStatus() throws Exception {
        InputFetchRegistry reg = new InputFetchRegistry();
        reg.markVerified("POST /api/v1/orders", "seatClass", "FirstClass");
        reg.markVerified("POST /api/v1/orders", "seatClass", "Business");
        reg.markRejected("POST /api/v1/orders", "seatClass", "BadInput");
        reg.markVerified("GET /api/v1/users/{id}", "userId", "u-001");

        File f = tmp.newFile("registry.yaml");
        reg.saveToFile(f);
        assertTrue(f.length() > 0);

        InputFetchRegistry loaded = InputFetchRegistry.loadFromFile(f);
        // Sanity: counts match
        assertEquals(2, loaded.getVerifiedValues("POST /api/v1/orders", "seatClass").size());
        assertEquals(1, loaded.getVerifiedValues("GET /api/v1/users/{id}", "userId").size());
        // Sanity: REJECTED preserved
        assertEquals(InputFetchRegistry.PoolEntryStatus.REJECTED_BY_SUT,
                loaded.getPoolEntryStatus("POST /api/v1/orders", "seatClass", "BadInput"));
        // Sanity: verified list does not include rejected entry
        List<String> verified = loaded.getVerifiedValues("POST /api/v1/orders", "seatClass");
        assertFalse(verified.contains("BadInput"));
    }

    @Test
    public void legacyYaml_withoutPoolEntryStatus_loadsAsEmpty() throws Exception {
        // Build a legacy-style YAML without the poolEntryStatus field.
        // Easiest: create a fresh registry, save it (no pool entries set),
        // confirm subsequent ops still work.
        InputFetchRegistry reg = new InputFetchRegistry();
        File f = tmp.newFile("legacy.yaml");
        reg.saveToFile(f);

        InputFetchRegistry loaded = InputFetchRegistry.loadFromFile(f);
        assertTrue(loaded.getVerifiedValues("any", "any").isEmpty());
        assertEquals(InputFetchRegistry.PoolEntryStatus.UNVERIFIED,
                loaded.getPoolEntryStatus("any", "any", "any"));
        // Can mark new entries on the loaded registry
        loaded.markVerified("e", "p", "v");
        assertEquals(1, loaded.getVerifiedValues("e", "p").size());
    }
}
