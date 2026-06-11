package io.mist.llm;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Locks in the contract of {@link LLMConfig#applySeedGate(double)}: the master
 * determinism switch. When {@code -Drandom.seed=<n>} is set, every per-call
 * temperature is collapsed to {@code 0.0} (greedy decoding) so that the
 * combination of (cache, seed, temp=0) produces byte-identical LLM responses
 * across runs.
 *
 * <p>The test must be hermetic: saving and restoring the {@code random.seed}
 * system property in {@code @Before} / {@code @After} keeps it from polluting
 * sibling tests in the same JVM run.
 */
public class LLMConfigSeedGateTest {

    private static final String SEED_PROP = "random.seed";

    private String savedSeed;

    @Before
    public void saveSeed() {
        // Snapshot the current value so we can restore it exactly — including
        // the "unset" case (System.clearProperty) — in @After.
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
    public void unsetSeedReturnsConfiguredTemperature() {
        // Sanity check (also enforces hermeticity from @Before).
        assertEquals(0.7, LLMConfig.applySeedGate(0.7), 0.0);
        assertEquals(0.0, LLMConfig.applySeedGate(0.0), 0.0);
        assertEquals(1.0, LLMConfig.applySeedGate(1.0), 0.0);
    }

    @Test
    public void setSeedForcesZeroTemperature() {
        System.setProperty(SEED_PROP, "42");
        assertEquals("With -Drandom.seed=42 set, applySeedGate must collapse any "
                + "configured temperature to 0.0 (greedy decoding)",
                0.0, LLMConfig.applySeedGate(0.7), 0.0);
        assertEquals(0.0, LLMConfig.applySeedGate(1.0), 0.0);
        assertEquals(0.0, LLMConfig.applySeedGate(0.0), 0.0);
    }

    @Test
    public void anySeedValueTriggersGate() {
        // The gate predicate is just "property is set", not "property is a long" —
        // even a non-numeric value still forces temperature=0. (The numeric form
        // is required only by the seed-forwarding code in LLMService /
        // OllamaApiClient / GeminiApiClient, which logs and skips on parse fail.)
        System.setProperty(SEED_PROP, "not-a-number");
        assertEquals(0.0, LLMConfig.applySeedGate(0.9), 0.0);
    }

    @Test
    public void emptySeedStringStillTriggersGate() {
        // System.getProperty returns the empty string (not null) for -D=""; the
        // gate fires on "set", not on "non-empty". This is intentional: an
        // empty seed is still a deliberate user request for determinism.
        System.setProperty(SEED_PROP, "");
        assertEquals(0.0, LLMConfig.applySeedGate(0.5), 0.0);
    }
}
