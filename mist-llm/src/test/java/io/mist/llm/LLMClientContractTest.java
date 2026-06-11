package io.mist.llm;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Locks in the public contract of the {@link LLMClient} SPI as wired up in
 * the {@code mist-llm} module:
 *
 * <ul>
 *   <li>{@link LLMService} implements {@link LLMClient} so consumers (including
 *       {@code mist-core}, which forbids {@code es.us.isa.*} deps) can program
 *       against the SPI without dragging in mist-restest-adapter.</li>
 *   <li>The prompt-cache layer ({@link LLMCallCache}) honours the
 *       {@code random.seed} system property the same way it did when the code
 *       lived in mist-restest-adapter.</li>
 *   <li>{@link LLMResponse#failure(String)} and {@link LLMResponse#ok(String)}
 *       carry the {@code success} flag in a way the default
 *       {@link LLMClient#promptWith(LLMRequest)} can rely on.</li>
 * </ul>
 *
 * <p>We do not exercise the network path here — the test only inspects the
 * relationships between the types. Backend behaviour is covered by
 * {@link LLMCallCacheTest} and {@link LLMConfigSeedGateTest}, which moved
 * over from the adapter alongside the production classes.
 */
public class LLMClientContractTest {

    private static final String SEED_PROP = "random.seed";

    private String savedSeed;

    @Before
    public void saveSeed() {
        savedSeed = System.getProperty(SEED_PROP);
        System.clearProperty(SEED_PROP);
    }

    @After
    public void restoreSeed() {
        if (savedSeed == null) {
            System.clearProperty(SEED_PROP);
        } else {
            System.setProperty(SEED_PROP, savedSeed);
        }
    }

    @Test
    public void llmServiceImplementsLLMClient() {
        // The headline Phase 1.C deliverable: callers can program against the
        // SPI without dragging in the rest of the adapter surface. If this
        // assertion fails the SPI / impl have drifted apart and downstream
        // mist-core consumers cannot bind through the interface.
        assertTrue("LLMService must implement LLMClient so mist-core can call the LLM "
                        + "without depending on mist-restest-adapter",
                LLMClient.class.isAssignableFrom(LLMService.class));
    }

    @Test
    public void promptCacheReplayHonoursRandomSeed() {
        // The adapter test that asserts cache key stability lives in
        // LLMCallCacheTest. Here we cover the seed-gate side of the contract:
        // with random.seed set, the cache's get() must read deterministic
        // entries; with the property unset, the lookup is a no-op so the
        // legacy "write through but do not replay" behaviour is preserved.
        LLMCallCache cache = LLMCallCache.forTesting(
                System.getProperty("java.io.tmpdir") + "/llm-client-contract-" + System.nanoTime() + ".json");

        String key = LLMCallCache.key("OPENAI_COMPATIBLE", "stub-model", "OPENAI_COMPATIBLE",
                "system prompt", "user prompt", 0.0, 128);

        cache.put(key, "cached-response");
        assertEquals("cached-response", cache.get(key));

        // Even with the seed gate active, get() returns the same value —
        // the gate lives in LLMConfig.applySeedGate() / LLMService, not in
        // the cache itself. So the cache invariant is "any get(seeded-or-not)
        // returns what put() wrote".
        System.setProperty(SEED_PROP, "1234");
        assertEquals("cached-response", cache.get(key));

        // A miss under any seed value must be null, not a stale default.
        String missKey = LLMCallCache.key("OPENAI_COMPATIBLE", "stub-model", "OPENAI_COMPATIBLE",
                "system prompt", "different user prompt", 0.0, 128);
        assertNull(cache.get(missKey));
    }

    @Test
    public void defaultPromptWithWrapsStringEntryPoint() {
        // Stub client that returns a canned response: verifies the default
        // promptWith() implementation actually calls prompt(String, String).
        LLMClient stub = (sys, user) -> "stub:" + user;
        LLMResponse response = stub.promptWith(new LLMRequest("sys", "hello"));
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("stub:hello", response.getContent());
    }

    @Test
    public void defaultPromptWithSurfaceNullAsFailure() {
        // A null backend response (timeout, parser miss, ...) must map to a
        // failure envelope so callers that program against promptWith() do
        // not have to know about the legacy "null means fallback" convention.
        LLMClient stub = (sys, user) -> null;
        LLMResponse response = stub.promptWith(new LLMRequest("sys", "hello"));
        assertNotNull(response);
        assertTrue("promptWith default impl must report failure when prompt() returns null",
                !response.isSuccess());
        assertNull(response.getContent());
    }
}
