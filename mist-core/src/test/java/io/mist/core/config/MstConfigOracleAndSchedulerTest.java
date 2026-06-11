package io.mist.core.config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the six ablation toggles introduced for H2 (PROMPT_H2 § 4.1):
 * {@code mst.oracle.shape.enabled}, the four
 * {@code mst.oracle.shape.invariants.*.enabled} flags, and
 * {@code mst.scheduler.bandit.enabled}. The test confirms each accessor
 * reflects its system property and that the default-on / default-off
 * pattern from PROMPT_H2 § 4.3 holds (Timing alone defaults to off).
 *
 * <p>Each test snapshots the prior System property value and restores it
 * in {@link #tearDown()} so cases run in arbitrary order without
 * leaking state.
 */
public class MstConfigOracleAndSchedulerTest {

    private static final List<String> KEYS = Arrays.asList(
            "mst.oracle.shape.enabled",
            "mst.oracle.shape.invariants.span_tree.enabled",
            "mst.oracle.shape.invariants.status_propagation.enabled",
            "mst.oracle.shape.invariants.response_envelope.enabled",
            "mst.oracle.shape.invariants.timing.enabled",
            "mst.scheduler.bandit.enabled"
    );

    private final Map<String, String> previous = new HashMap<>();

    @Before
    public void setUp() {
        previous.clear();
        for (String k : KEYS) {
            previous.put(k, System.getProperty(k));
            System.clearProperty(k);
        }
        MstConfig.resetForTesting();
    }

    @After
    public void tearDown() {
        for (Map.Entry<String, String> e : previous.entrySet()) {
            if (e.getValue() == null) {
                System.clearProperty(e.getKey());
            } else {
                System.setProperty(e.getKey(), e.getValue());
            }
        }
        MstConfig.resetForTesting();
    }

    @Test
    public void defaults_matchPromptSection_4_3() {
        MstConfig cfg = MstConfig.fromSystemProperties();
        assertTrue("oracle whole-system on by default",
                cfg.oracle().shapeOracleEnabled());
        assertTrue("span_tree on by default",
                cfg.oracle().spanTreeInvariantEnabled());
        assertTrue("status_propagation on by default",
                cfg.oracle().statusPropagationInvariantEnabled());
        assertTrue("response_envelope on by default",
                cfg.oracle().responseEnvelopeInvariantEnabled());
        assertFalse("timing OFF by default (paper revision dropped it)",
                cfg.oracle().timingEnvelopeInvariantEnabled());
        assertTrue("bandit on by default",
                cfg.scheduler().banditEnabled());
    }

    @Test
    public void shapeOracleDisable_propagates() {
        System.setProperty("mst.oracle.shape.enabled", "false");
        MstConfig cfg = MstConfig.fromSystemProperties();
        assertFalse(cfg.oracle().shapeOracleEnabled());
    }

    @Test
    public void perInvariantToggles_propagate() {
        System.setProperty("mst.oracle.shape.invariants.span_tree.enabled", "false");
        System.setProperty("mst.oracle.shape.invariants.status_propagation.enabled", "false");
        System.setProperty("mst.oracle.shape.invariants.response_envelope.enabled", "false");
        System.setProperty("mst.oracle.shape.invariants.timing.enabled", "true");
        MstConfig cfg = MstConfig.fromSystemProperties();
        assertFalse(cfg.oracle().spanTreeInvariantEnabled());
        assertFalse(cfg.oracle().statusPropagationInvariantEnabled());
        assertFalse(cfg.oracle().responseEnvelopeInvariantEnabled());
        assertTrue(cfg.oracle().timingEnvelopeInvariantEnabled());
    }

    @Test
    public void banditDisable_propagates() {
        System.setProperty("mst.scheduler.bandit.enabled", "false");
        MstConfig cfg = MstConfig.fromSystemProperties();
        assertFalse(cfg.scheduler().banditEnabled());
    }

    @Test
    public void allOff_combination() {
        for (String k : KEYS) System.setProperty(k, "false");
        MstConfig cfg = MstConfig.fromSystemProperties();
        assertFalse(cfg.oracle().shapeOracleEnabled());
        assertFalse(cfg.oracle().spanTreeInvariantEnabled());
        assertFalse(cfg.oracle().statusPropagationInvariantEnabled());
        assertFalse(cfg.oracle().responseEnvelopeInvariantEnabled());
        assertFalse(cfg.oracle().timingEnvelopeInvariantEnabled());
        assertFalse(cfg.scheduler().banditEnabled());
    }

    @Test
    public void allOn_combination() {
        for (String k : KEYS) System.setProperty(k, "true");
        MstConfig cfg = MstConfig.fromSystemProperties();
        assertTrue(cfg.oracle().shapeOracleEnabled());
        assertTrue(cfg.oracle().spanTreeInvariantEnabled());
        assertTrue(cfg.oracle().statusPropagationInvariantEnabled());
        assertTrue(cfg.oracle().responseEnvelopeInvariantEnabled());
        assertTrue(cfg.oracle().timingEnvelopeInvariantEnabled());
        assertTrue(cfg.scheduler().banditEnabled());
    }

    @Test
    public void validator_doesNotWarn_onKnownKeys() {
        for (String k : KEYS) System.setProperty(k, "true");
        // Validator.validate(...) runs inside fromSystemProperties();
        // any unknown-key warning becomes a fatal IllegalStateException
        // only under strict mode. Strict-off path just confirms the keys
        // are whitelisted (no exception on validation of valid range).
        MstConfig cfg = MstConfig.fromSystemProperties();
        // Sanity: full round-trip readable.
        assertTrue(cfg.oracle().shapeOracleEnabled());
    }
}
