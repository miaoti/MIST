package io.mist.llm;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Locks in the contract of the two cache gates exposed by
 * {@link LLMService#cacheReadEnabled()} and
 * {@link LLMService#cacheWriteEnabled()}, both of which can be controlled
 * from the MST {@code .properties} file (every key is mirrored into
 * System properties by {@code MstConfig.applyToSystemProperties}) or via
 * a {@code -D} flag on the JVM.
 *
 * <p>READ precedence: {@code mist.llm.cache.read} explicit value &gt; the
 * legacy "seeded run ⇒ read" gate keyed off {@code -Drandom.seed}.
 *
 * <p>WRITE default: ON unless {@code mist.llm.cache.write=false}.
 *
 * <p>Hermetic: saves/restores both system properties in @Before / @After.
 */
public class LLMServiceCacheGateTest {

    private static final String READ_PROP  = "mist.llm.cache.read";
    private static final String WRITE_PROP = "mist.llm.cache.write";
    private static final String SEED_PROP  = "random.seed";

    private String savedRead;
    private String savedWrite;
    private String savedSeed;

    @Before
    public void snapshot() {
        savedRead  = System.getProperty(READ_PROP);
        savedWrite = System.getProperty(WRITE_PROP);
        savedSeed  = System.getProperty(SEED_PROP);
        System.clearProperty(READ_PROP);
        System.clearProperty(WRITE_PROP);
        System.clearProperty(SEED_PROP);
    }

    @After
    public void restore() {
        restoreOrClear(READ_PROP,  savedRead);
        restoreOrClear(WRITE_PROP, savedWrite);
        restoreOrClear(SEED_PROP,  savedSeed);
    }

    private static void restoreOrClear(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }

    /* ─────────────────────────── READ gate ────────────────────────────── */

    @Test
    public void readGate_autoMode_unseeded_isOff() {
        // No mist.llm.cache.read; no random.seed → cache reads disabled
        // (every prompt goes to the LLM, matching the pre-property
        // behaviour where unseeded runs always hit the backend).
        assertFalse(LLMService.cacheReadEnabled());
    }

    @Test
    public void readGate_autoMode_seeded_isOn() {
        // No mist.llm.cache.read; -Drandom.seed=42 set → cache reads
        // enabled (legacy "seeded ⇒ read" behaviour).
        System.setProperty(SEED_PROP, "42");
        assertTrue(LLMService.cacheReadEnabled());
    }

    @Test
    public void readGate_explicitTrue_overridesUnseeded() {
        // mist.llm.cache.read=true forces reads ON even without a seed —
        // this is the "use my pre-populated cache to short-circuit LLM"
        // use case the .properties knob was added for.
        System.setProperty(READ_PROP, "true");
        assertTrue(LLMService.cacheReadEnabled());
    }

    @Test
    public void readGate_explicitFalse_overridesSeeded() {
        // mist.llm.cache.read=false suppresses reads even when -Drandom.seed
        // is set — the "I want a determinism-fresh run that ignores the
        // shared cache file" use case.
        System.setProperty(SEED_PROP, "42");
        System.setProperty(READ_PROP, "false");
        assertFalse(LLMService.cacheReadEnabled());
    }

    @Test
    public void readGate_caseInsensitive_acceptsAliases() {
        // Documented aliases (true/on/yes ↔ false/off/no) work regardless
        // of case.
        System.setProperty(READ_PROP, "ON");
        assertTrue(LLMService.cacheReadEnabled());
        System.setProperty(READ_PROP, "Yes");
        assertTrue(LLMService.cacheReadEnabled());
        System.setProperty(READ_PROP, "OFF");
        assertFalse(LLMService.cacheReadEnabled());
        System.setProperty(READ_PROP, "no");
        assertFalse(LLMService.cacheReadEnabled());
    }

    @Test
    public void readGate_unrecognisedValue_fallsBackToAutoMode() {
        // "auto" or any unrecognised value falls through to the legacy
        // -Drandom.seed gate. Important so misconfigured values don't
        // silently disable the seeded-reproducibility contract.
        System.setProperty(READ_PROP, "auto");
        assertFalse(LLMService.cacheReadEnabled());
        System.setProperty(SEED_PROP, "42");
        assertTrue(LLMService.cacheReadEnabled());

        System.setProperty(READ_PROP, "garbage");
        // Still seeded → still on
        assertTrue(LLMService.cacheReadEnabled());
    }

    /* ─────────────────────────── WRITE gate ───────────────────────────── */

    @Test
    public void writeGate_defaultIsOn() {
        // Default-on so unseeded runs feed the cache; a future seeded
        // re-run can replay them. Matches pre-property behaviour.
        assertTrue(LLMService.cacheWriteEnabled());
    }

    @Test
    public void writeGate_explicitFalse_suppressesWrites() {
        System.setProperty(WRITE_PROP, "false");
        assertFalse(LLMService.cacheWriteEnabled());
        System.setProperty(WRITE_PROP, "Off");
        assertFalse(LLMService.cacheWriteEnabled());
        System.setProperty(WRITE_PROP, "no");
        assertFalse(LLMService.cacheWriteEnabled());
    }

    @Test
    public void writeGate_explicitTrue_isOn() {
        System.setProperty(WRITE_PROP, "true");
        assertTrue(LLMService.cacheWriteEnabled());
    }

    @Test
    public void writeGate_unrecognisedValue_defaultsToOn() {
        // Any unrecognised value (including "auto") falls back to ON —
        // safer default for cache: a typo shouldn't silently drop
        // cache writes the rest of the run depends on.
        System.setProperty(WRITE_PROP, "auto");
        assertTrue(LLMService.cacheWriteEnabled());
        System.setProperty(WRITE_PROP, "garbage");
        assertTrue(LLMService.cacheWriteEnabled());
    }
}
