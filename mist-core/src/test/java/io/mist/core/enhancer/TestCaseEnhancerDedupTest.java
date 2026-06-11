package io.mist.core.enhancer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Smoke tests for the deduplication primitives in {@link TestCaseEnhancer} and
 * the persistence behavior of {@link EnhancementCache}.
 *
 * <p>These tests construct synthetic {@link FailedTestResult} fixtures and check
 * that the canonical-key function:
 * <ul>
 *   <li>collapses 100 variants of the same scenario+fault+error to one key,</li>
 *   <li>differentiates on the fields it should (method, endpoint, status,
 *       invalidParam names, locked names, schema, response body),</li>
 *   <li>ignores the fields it shouldn't (test class/method name, parameter
 *       values, invalid-parameter values),</li>
 *   <li>and that {@link EnhancementCache} round-trips through disk.</li>
 * </ul>
 *
 * <p>No LLM service is needed for these tests — they exercise the dedup
 * primitives directly. The enhancer is constructed with a null LLMService and
 * the {@code enhance()} path is never exercised here.
 */
public class TestCaseEnhancerDedupTest {

    private TestCaseEnhancer enhancer;
    private Path tempCacheFile;

    @Before
    public void setUp() throws IOException {
        // null LLMService is safe — these tests never call enhance() or enhanceBatch()
        // (the latter only when we exercise the cache, and only with a pre-seeded
        // cache so the LLM path is short-circuited).
        enhancer = new TestCaseEnhancer(null);
        tempCacheFile = Files.createTempFile("enhancement-cache-test-", ".json");
        // Start empty: delete the placeholder so EnhancementCache treats it as
        // a cold-start (Files.exists() == false).
        Files.deleteIfExists(tempCacheFile);
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(tempCacheFile);
    }

    // ===================================================================
    // canonicalKey: 100 same-canonical-group variants → 1 key
    // ===================================================================

    @Test
    public void hundredVariantsSameScenarioCollapseToOneKey() {
        FailedTestResult v1 = baseNegativeTest(1, "BAD_CHARS_1", "user_a", "x@y.com");
        String expectedKey = enhancer.canonicalKey(v1);

        for (int i = 1; i <= 100; i++) {
            FailedTestResult vi = baseNegativeTest(i, "BAD_CHARS_" + i, "user_" + i, "v" + i + "@z.com");
            String k = enhancer.canonicalKey(vi);
            assertEquals("variant " + i + " should share canonical key", expectedKey, k);
        }
    }

    // ===================================================================
    // canonicalKey: must NOT collapse different scenarios
    // ===================================================================

    @Test
    public void differentHttpMethodProducesDifferentKey() {
        FailedTestResult post = baseNegativeTest(1, "BAD", "a", "x@y.com");
        FailedTestResult put = baseNegativeTest(1, "BAD", "a", "x@y.com");
        put.setHttpMethod("PUT");
        assertNotEquals(enhancer.canonicalKey(post), enhancer.canonicalKey(put));
    }

    @Test
    public void differentEndpointProducesDifferentKey() {
        FailedTestResult a = baseNegativeTest(1, "BAD", "a", "x@y.com");
        FailedTestResult b = baseNegativeTest(1, "BAD", "a", "x@y.com");
        b.setEndpoint("/api/v1/different");
        assertNotEquals(enhancer.canonicalKey(a), enhancer.canonicalKey(b));
    }

    @Test
    public void differentStatusCodeProducesDifferentKey() {
        FailedTestResult s400 = baseNegativeTest(1, "BAD", "a", "x@y.com");
        FailedTestResult s422 = baseNegativeTest(1, "BAD", "a", "x@y.com");
        s422.setActualStatusCode(422);
        assertNotEquals(enhancer.canonicalKey(s400), enhancer.canonicalKey(s422));
    }

    @Test
    public void differentInvalidParamNamesProducesDifferentKey() {
        FailedTestResult specialChars = baseNegativeTest(1, "BAD", "a", "x@y.com");
        FailedTestResult overflow = baseNegativeTest(1, "BAD", "a", "x@y.com");
        // Same scenario, different fault target → different invalid param name set.
        overflow.setInvalidParameters(Collections.singletonList("trainNumber=OVERFLOW_VALUE_9"));
        assertNotEquals(enhancer.canonicalKey(specialChars), enhancer.canonicalKey(overflow));
    }

    @Test
    public void differentResponseBodyShapeProducesDifferentKey() {
        FailedTestResult authFail = baseNegativeTest(1, "BAD", "a", "x@y.com");
        FailedTestResult validationFail = baseNegativeTest(1, "BAD", "a", "x@y.com");
        validationFail.setResponseBody("validation error: field 'foo' missing");
        assertNotEquals(enhancer.canonicalKey(authFail), enhancer.canonicalKey(validationFail));
    }

    @Test
    public void differentLockedDepNamesProducesDifferentKey() {
        FailedTestResult a = baseNegativeTest(1, "BAD", "a", "x@y.com");
        FailedTestResult b = baseNegativeTest(1, "BAD", "a", "x@y.com");
        b.setLockedDependencyParams(new HashSet<>(Arrays.asList("userId")));
        assertNotEquals(enhancer.canonicalKey(a), enhancer.canonicalKey(b));
    }

    @Test
    public void differentParamSchemaProducesDifferentKey() {
        FailedTestResult a = baseNegativeTest(1, "BAD", "a", "x@y.com");
        FailedTestResult b = baseNegativeTest(1, "BAD", "a", "x@y.com");
        // Add a new parameter — schema differs.
        b.getParameters().add(ParameterSnapshot.builder()
                .name("extraField").value("zzz").type("string").location("body").required(false)
                .stepIndex(1).build());
        assertNotEquals(enhancer.canonicalKey(a), enhancer.canonicalKey(b));
    }

    // ===================================================================
    // canonicalKey: must IGNORE variant-distinctive fields
    // ===================================================================

    @Test
    public void differentTestMethodNameDoesNotChangeKey() {
        FailedTestResult a = baseNegativeTest(1, "BAD_1", "a", "x@y.com");
        FailedTestResult b = baseNegativeTest(1, "BAD_2", "a", "x@y.com");
        a.setTestMethodName("test_negative_flow_S22350_v1_fault_Root1_SPECIAL_CHARACTERS");
        b.setTestMethodName("test_negative_flow_S22350_v999_fault_Root1_SPECIAL_CHARACTERS");
        assertEquals(enhancer.canonicalKey(a), enhancer.canonicalKey(b));
    }

    @Test
    public void differentParamValuesDoesNotChangeKey() {
        FailedTestResult a = baseNegativeTest(1, "BAD_1", "alice", "alice@example.com");
        FailedTestResult b = baseNegativeTest(1, "BAD_2", "bob", "bob@example.com");
        // Schemas identical, names identical, just values differ. Same canonical key.
        assertEquals(enhancer.canonicalKey(a), enhancer.canonicalKey(b));
    }

    @Test
    public void differentInvalidParamValueButSameNameDoesNotChangeKey() {
        FailedTestResult a = baseNegativeTest(1, "<<<<<<", "a", "x@y.com");
        FailedTestResult b = baseNegativeTest(1, "&^%@#!", "a", "x@y.com");
        // Same invalid-param NAME ("accountId"), different VALUES (the
        // distinctive variant content). Same canonical key — variant identity
        // is preserved at the file level by TestFileRegenerator, not at the
        // enhancer dedup level.
        assertEquals(enhancer.canonicalKey(a), enhancer.canonicalKey(b));
    }

    @Test
    public void responseBodyTruncationDoesNotShiftKey() {
        FailedTestResult a = baseNegativeTest(1, "BAD", "a", "x@y.com");
        FailedTestResult b = baseNegativeTest(1, "BAD", "a", "x@y.com");
        StringBuilder longBody = new StringBuilder(a.getResponseBody());
        // First 200 chars match (those are the canonicalization window); only
        // the suffix differs.
        while (longBody.length() < 200) longBody.append(" pad");
        b.setResponseBody(longBody.toString() + " UNIQUE_SUFFIX_THAT_SHOULD_NOT_AFFECT_KEY");
        a.setResponseBody(longBody.toString());
        assertEquals(enhancer.canonicalKey(a), enhancer.canonicalKey(b));
    }

    // ===================================================================
    // EnhancementCache: put / get / persist roundtrip
    // ===================================================================

    @Test
    public void cachePutGetRoundtripInMemory() {
        EnhancementCache cache = EnhancementCache.forTesting(tempCacheFile.toString());
        Map<String, String> params = new LinkedHashMap<>();
        params.put("userId", "admin-001");
        params.put("email", "a@b.com");
        cache.put("key-A", new EnhancementCache.CachedEnhancement(params, "test reasoning"));

        Optional<EnhancementCache.CachedEnhancement> hit = cache.get("key-A");
        assertTrue("entry should be present", hit.isPresent());
        assertEquals("admin-001", hit.get().enhancedParameters.get("userId"));
        assertEquals("a@b.com", hit.get().enhancedParameters.get("email"));
        assertEquals("test reasoning", hit.get().reasoning);

        assertFalse("missing key returns empty", cache.get("nonexistent").isPresent());
        assertNull("null key returns empty (no NPE)", cache.get(null).orElse(null));
    }

    @Test
    public void cachePersistsToDiskAndReloads() {
        EnhancementCache writer = EnhancementCache.forTesting(tempCacheFile.toString());
        Map<String, String> params = new LinkedHashMap<>();
        params.put("date", "2026-06-01");
        params.put("region", "us-east");
        writer.put("key-persist", new EnhancementCache.CachedEnhancement(params, "persist test"));
        writer.flush(); // synchronous write

        // File exists and is non-empty.
        assertTrue("cache file should exist after flush", Files.exists(tempCacheFile));

        // New instance reads the same file back.
        EnhancementCache reader = EnhancementCache.forTesting(tempCacheFile.toString());
        Optional<EnhancementCache.CachedEnhancement> hit = reader.get("key-persist");
        assertTrue("entry should survive disk roundtrip", hit.isPresent());
        assertEquals("2026-06-01", hit.get().enhancedParameters.get("date"));
        assertEquals("us-east", hit.get().enhancedParameters.get("region"));
        assertEquals("persist test", hit.get().reasoning);
    }

    @Test
    public void cacheLoadFromEmptyFileIsCleanStart() throws IOException {
        // Create an empty file.
        Files.write(tempCacheFile, new byte[0]);
        EnhancementCache c = EnhancementCache.forTesting(tempCacheFile.toString());
        assertEquals("empty file → 0 entries", 0, c.size());
        // Cache remains usable.
        c.put("k1", new EnhancementCache.CachedEnhancement(
                Collections.singletonMap("p", "v"), ""));
        assertEquals(1, c.size());
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    /**
     * Build a baseline negative test in the spirit of train-ticket's
     * test_negative_flow_S22350_v{n}_fault_Root1_SPECIAL_CHARACTERS.
     *
     * @param vIdx              variant index — only affects identifiers, NOT canonical key
     * @param invalidAccountId  the distinctive invalid value (only this VALUE
     *                          changes across variants of the same fault type)
     * @param userId            supporting-param value (random per variant)
     * @param email             supporting-param value (random per variant)
     */
    private static FailedTestResult baseNegativeTest(int vIdx, String invalidAccountId,
                                                     String userId, String email) {
        List<ParameterSnapshot> params = new ArrayList<>();
        params.add(ParameterSnapshot.builder()
                .name("accountId").value(invalidAccountId).type("string").location("body")
                .required(true).stepIndex(1).build());
        params.add(ParameterSnapshot.builder()
                .name("userId").value(userId).type("string").location("body")
                .required(true).stepIndex(1).build());
        params.add(ParameterSnapshot.builder()
                .name("email").value(email).type("string").location("body")
                .required(false).stepIndex(1).build());

        FailedTestResult ft = new FailedTestResult();
        ft.setTestClassName("trainticket_twostage_test.TrainTicketTwoStageTest.Flow_Scenario_22350");
        ft.setTestMethodName("test_negative_flow_S22350_v" + vIdx + "_fault_Root1_SPECIAL_CHARACTERS");
        ft.setScenarioName("Flow_Scenario_22350");
        ft.setHttpMethod("POST");
        ft.setEndpoint("/api/v1/adminorderservice/adminorder");
        ft.setServiceName("ts-admin-order-service");
        ft.setNegativeTest(true);
        ft.setInvalidParameters(new ArrayList<>(Collections.singletonList("accountId=" + invalidAccountId)));
        ft.setActualStatusCode(400);
        ft.setResponseBody("{\"status\":0,\"msg\":\"Order rejected: contactsName cannot be purely numeric\"}");
        ft.setErrorMessage("Negative test failed: error not related to invalid input");
        ft.setFailureType("ASSERTION");
        ft.setFailedStepIndex(1);
        ft.setParameters(params);
        ft.setLockedDependencyParams(new HashSet<>());
        ft.setBypassTriggered(false);
        return ft;
    }
}
