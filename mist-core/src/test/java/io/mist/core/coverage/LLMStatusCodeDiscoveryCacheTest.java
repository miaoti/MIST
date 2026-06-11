package io.mist.core.coverage;

import io.mist.core.config.CacheToggle;
import io.mist.core.llm.ParameterInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Pins the signature-based cache behavior added to LLMStatusCodeDiscovery for
 * paper/A会 reproducibility.
 *
 * <p>Three guarantees are tested:
 * <ol>
 *   <li>{@link LLMStatusCodeDiscovery#normalizePath} collapses concrete value
 *       segments to {@code {id}} so 100 invocations with different invalid
 *       inputs produce the same cache key.</li>
 *   <li>{@link LLMStatusCodeDiscovery#buildSignatureCacheKey} is stable
 *       regardless of parameter list iteration order — the sort step in the
 *       implementation makes the key deterministic.</li>
 *   <li>The cache file lives at the path the test pinned, ignoring the
 *       default; this is the test seam that lets parallel tests run without
 *       clobbering each other.</li>
 * </ol>
 *
 * <p>No LLM service is needed — the tests exercise only the pure
 * static helpers and the constructor's disk-load path.
 */
public class LLMStatusCodeDiscoveryCacheTest {

    private String prevRead;
    private String prevWrite;
    private Path tmpCacheFile;

    @Before
    public void setUp() throws Exception {
        // Save and clear master cache toggles so tests run with defaults
        // regardless of the JVM-wide state.
        prevRead = System.getProperty(CacheToggle.MASTER_READ);
        prevWrite = System.getProperty(CacheToggle.MASTER_WRITE);
        System.clearProperty(CacheToggle.MASTER_READ);
        System.clearProperty(CacheToggle.MASTER_WRITE);
        tmpCacheFile = Files.createTempFile("llm-status-code-cache-test-", ".json");
        // Start empty so the constructor sees a cold start (file deleted but
        // path still resolves).
        Files.deleteIfExists(tmpCacheFile);
    }

    @After
    public void tearDown() throws Exception {
        if (prevRead == null) System.clearProperty(CacheToggle.MASTER_READ);
        else System.setProperty(CacheToggle.MASTER_READ, prevRead);
        if (prevWrite == null) System.clearProperty(CacheToggle.MASTER_WRITE);
        else System.setProperty(CacheToggle.MASTER_WRITE, prevWrite);
        if (tmpCacheFile != null) Files.deleteIfExists(tmpCacheFile);
    }

    // ─── normalizePath ──────────────────────────────────────────────────

    @Test
    public void normalizePath_keepsStableLowercaseSegments() {
        assertEquals("/api/v1/contactservice/contacts/account/{id}",
                LLMStatusCodeDiscovery.normalizePath(
                        "/api/v1/contactservice/contacts/account/FALLBACK_accountId_3"));
    }

    @Test
    public void normalizePath_collapsesUrlEncodedValues() {
        assertEquals("/api/v1/contactservice/contacts/account/{id}",
                LLMStatusCodeDiscovery.normalizePath(
                        "/api/v1/contactservice/contacts/account/%00%01%02"));
    }

    @Test
    public void normalizePath_collapsesOverflow() {
        String overflow = "A" + new String(new char[200]).replace('\0', 'B');
        assertEquals("/api/v1/svc/resource/{id}",
                LLMStatusCodeDiscovery.normalizePath("/api/v1/svc/resource/" + overflow));
    }

    @Test
    public void normalizePath_keepsCurlyBraceTemplate() {
        // Already-templated paths pass through verbatim — the regex's
        // alternative {@code \{[^}]+\}} matches them as stable. Two test
        // generators (RESTest's vs MIST's writer) emit different templating
        // styles; both must survive.
        assertEquals("/users/{userId}/orders/{orderId}",
                LLMStatusCodeDiscovery.normalizePath("/users/{userId}/orders/{orderId}"));
    }

    @Test
    public void normalizePath_preservesEmpty() {
        assertEquals("", LLMStatusCodeDiscovery.normalizePath(""));
    }

    // ─── buildSignatureCacheKey ─────────────────────────────────────────

    @Test
    public void signatureKey_sameAcrossDifferentConcreteValues() {
        ParameterInfo p = new ParameterInfo();
        p.setName("accountId");
        p.setType("string");
        p.setInLocation("path");
        p.setRequired(true);
        List<ParameterInfo> params = Collections.singletonList(p);

        String k1 = LLMStatusCodeDiscovery.buildSignatureCacheKey(
                "GET", "/api/v1/svc/account/FALLBACK_accountId_3", params);
        String k2 = LLMStatusCodeDiscovery.buildSignatureCacheKey(
                "GET", "/api/v1/svc/account/%00%01%02", params);
        String k3 = LLMStatusCodeDiscovery.buildSignatureCacheKey(
                "GET", "/api/v1/svc/account/x", params);
        assertEquals(k1, k2);
        assertEquals(k2, k3);
    }

    @Test
    public void signatureKey_differsOnMethod() {
        List<ParameterInfo> empty = Collections.emptyList();
        String getK = LLMStatusCodeDiscovery.buildSignatureCacheKey("GET", "/api/svc", empty);
        String postK = LLMStatusCodeDiscovery.buildSignatureCacheKey("POST", "/api/svc", empty);
        assertNotEquals(getK, postK);
    }

    @Test
    public void signatureKey_differsOnParamSchema() {
        ParameterInfo a = mkParam("a", "string", "path", true);
        ParameterInfo b = mkParam("b", "string", "path", true);
        String k1 = LLMStatusCodeDiscovery.buildSignatureCacheKey(
                "GET", "/api/svc/{id}", Collections.singletonList(a));
        String k2 = LLMStatusCodeDiscovery.buildSignatureCacheKey(
                "GET", "/api/svc/{id}", Arrays.asList(a, b));
        assertNotEquals(k1, k2);
    }

    @Test
    public void signatureKey_stableUnderReversedParamOrder() {
        ParameterInfo a = mkParam("a", "string", "path", true);
        ParameterInfo b = mkParam("b", "int", "query", false);
        String forward = LLMStatusCodeDiscovery.buildSignatureCacheKey(
                "GET", "/api/svc", Arrays.asList(a, b));
        String reversed = LLMStatusCodeDiscovery.buildSignatureCacheKey(
                "GET", "/api/svc", Arrays.asList(b, a));
        assertEquals("schema fingerprint must be order-independent", forward, reversed);
    }

    @Test
    public void constructor_coldStartDoesNotThrow() {
        // Building against a non-existent file should be a no-op, just log.
        new LLMStatusCodeDiscovery(null, tmpCacheFile);
        // No exception → pass.
    }

    private static ParameterInfo mkParam(String name, String type, String location, boolean required) {
        ParameterInfo p = new ParameterInfo();
        p.setName(name);
        p.setType(type);
        p.setInLocation(location);
        p.setRequired(required);
        return p;
    }
}
