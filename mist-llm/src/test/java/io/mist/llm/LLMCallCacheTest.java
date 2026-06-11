package io.mist.llm;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * Locks in the contract that the determinism switch relies on: key stability
 * across the seven input dimensions, exact round-trip on get/put, and loud
 * failure on a corrupt on-disk file (because silently masking corruption
 * would invisibly reintroduce nondeterminism).
 *
 * <p>Uses {@link LLMCallCache#forTesting(String)} to bypass the JVM-wide
 * singleton, so each test gets a private cache rooted in a {@link TemporaryFolder}
 * and tests do not leak state into each other.
 */
public class LLMCallCacheTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void putThenGetRoundTrips() throws IOException {
        Path path = tmp.newFolder("cache1").toPath().resolve("cache.json");
        LLMCallCache cache = LLMCallCache.forTesting(path.toString());
        String key = LLMCallCache.key("OPENAI_COMPATIBLE", "gpt-4o-mini",
                "OPENAI_COMPATIBLE", "sys", "user", 0.0, 256);

        assertNull("Fresh cache must miss before any put()", cache.get(key));

        cache.put(key, "the-cached-response");
        assertEquals("the-cached-response", cache.get(key));
    }

    @Test
    public void differentUserPromptsYieldDifferentKeys() {
        String k1 = LLMCallCache.key("OPENAI_COMPATIBLE", "gpt-4o-mini",
                "OPENAI_COMPATIBLE", "sys", "userA", 0.0, 256);
        String k2 = LLMCallCache.key("OPENAI_COMPATIBLE", "gpt-4o-mini",
                "OPENAI_COMPATIBLE", "sys", "userB", 0.0, 256);
        assertNotEquals("Different user prompts must hash to different keys", k1, k2);
    }

    @Test
    public void differentSystemPromptsYieldDifferentKeys() {
        String k1 = LLMCallCache.key("OPENAI_COMPATIBLE", "gpt-4o-mini",
                "OPENAI_COMPATIBLE", "sysA", "user", 0.0, 256);
        String k2 = LLMCallCache.key("OPENAI_COMPATIBLE", "gpt-4o-mini",
                "OPENAI_COMPATIBLE", "sysB", "user", 0.0, 256);
        assertNotEquals(k1, k2);
    }

    @Test
    public void differentTemperaturesYieldDifferentKeys() {
        String k1 = LLMCallCache.key("OPENAI_COMPATIBLE", "gpt-4o-mini",
                "OPENAI_COMPATIBLE", "sys", "user", 0.0, 256);
        String k2 = LLMCallCache.key("OPENAI_COMPATIBLE", "gpt-4o-mini",
                "OPENAI_COMPATIBLE", "sys", "user", 0.7, 256);
        assertNotEquals("temperature is a key component — different temps must collide-free", k1, k2);
    }

    @Test
    public void differentBackendsYieldDifferentKeys() {
        String k1 = LLMCallCache.key("OPENAI_COMPATIBLE", "shared-model",
                "OPENAI_COMPATIBLE", "sys", "user", 0.0, 256);
        String k2 = LLMCallCache.key("OPENAI_COMPATIBLE", "shared-model",
                "OLLAMA", "sys", "user", 0.0, 256);
        assertNotEquals("backend is a separate key field — even with identical model name, "
                + "OPENAI_COMPATIBLE and OLLAMA responses must not collide", k1, k2);
    }

    @Test
    public void differentMaxTokensYieldDifferentKeys() {
        String k1 = LLMCallCache.key("OPENAI_COMPATIBLE", "gpt-4o-mini",
                "OPENAI_COMPATIBLE", "sys", "user", 0.0, 256);
        String k2 = LLMCallCache.key("OPENAI_COMPATIBLE", "gpt-4o-mini",
                "OPENAI_COMPATIBLE", "sys", "user", 0.0, 512);
        assertNotEquals(k1, k2);
    }

    @Test
    public void sameInputsYieldSameKey() {
        String k1 = LLMCallCache.key("OPENAI_COMPATIBLE", "gpt-4o-mini",
                "OPENAI_COMPATIBLE", "sys", "user", 0.0, 256);
        String k2 = LLMCallCache.key("OPENAI_COMPATIBLE", "gpt-4o-mini",
                "OPENAI_COMPATIBLE", "sys", "user", 0.0, 256);
        assertEquals("key() must be deterministic across calls", k1, k2);
    }

    @Test
    public void flushPersistsAndReloads() throws IOException {
        Path path = tmp.newFolder("cache2").toPath().resolve("cache.json");
        LLMCallCache cache = LLMCallCache.forTesting(path.toString());
        String key = LLMCallCache.key("OPENAI_COMPATIBLE", "gpt-4o-mini",
                "OPENAI_COMPATIBLE", "sys", "user-persist", 0.0, 256);
        cache.put(key, "persisted-value");
        cache.flush();

        // A fresh instance over the same file must pick up the persisted entry.
        LLMCallCache reloaded = LLMCallCache.forTesting(path.toString());
        assertEquals("persisted-value", reloaded.get(key));
    }

    @Test
    public void corruptCacheFileThrowsAtStartup() throws IOException {
        Path path = tmp.newFolder("cache3").toPath().resolve("cache.json");
        Files.write(path, "{bad json".getBytes(StandardCharsets.UTF_8));

        try {
            LLMCallCache.forTesting(path.toString());
            fail("Expected RuntimeException on corrupt JSON cache file — silent fallback "
                    + "to empty would mask lost determinism state");
        } catch (RuntimeException expected) {
            // Caught: the constructor refused to silently proceed. Good.
        }
    }

    @Test
    public void missingCacheFileIsColdStart() throws IOException {
        // Pointing at a path whose file does not yet exist must succeed and
        // start empty — this is the normal "first ever run" case.
        Path path = tmp.newFolder("cache4").toPath().resolve("does-not-yet-exist.json");
        LLMCallCache cache = LLMCallCache.forTesting(path.toString());
        String key = LLMCallCache.key("OPENAI_COMPATIBLE", "any", "OPENAI_COMPATIBLE",
                "sys", "user", 0.0, 256);
        assertNull(cache.get(key));
    }
}
