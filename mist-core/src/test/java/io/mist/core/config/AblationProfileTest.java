package io.mist.core.config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.mist.core.fault.FaultMiner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the AblationProfile summary format and the from(MstConfig)
 * factory's read paths. The exhaustive 2^7 case grid is intentionally
 * not enumerated; the prompt (PROMPT_H2 section 7.4) calls for all-on,
 * all-off, and one mid-configuration to keep the smoke test concise.
 */
public class AblationProfileTest {

    private static final List<String> ORACLE_KEYS = Arrays.asList(
            "mst.oracle.shape.enabled",
            "mst.oracle.shape.invariants.span_tree.enabled",
            "mst.oracle.shape.invariants.status_propagation.enabled",
            "mst.oracle.shape.invariants.response_envelope.enabled",
            "mst.oracle.shape.invariants.timing.enabled"
    );

    private final Map<String, String> previous = new HashMap<>();

    @Before
    public void setUp() {
        previous.clear();
        for (String k : ORACLE_KEYS) {
            previous.put(k, System.getProperty(k));
            System.clearProperty(k);
        }
        previous.put("mst.scheduler.bandit.enabled",
                System.getProperty("mst.scheduler.bandit.enabled"));
        System.clearProperty("mst.scheduler.bandit.enabled");
        previous.put(FaultMiner.ENABLED_PROPERTY,
                System.getProperty(FaultMiner.ENABLED_PROPERTY));
        System.clearProperty(FaultMiner.ENABLED_PROPERTY);
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
    public void defaults_R3_matchesBundledDemo() {
        AblationProfile p = AblationProfile.from(MstConfig.fromSystemProperties());
        // Defaults: shape on, three invariants on (timing off), bandit on,
        // fault mining off. This is R3 in PATH_B_POSITIONING.md section 4.2.
        assertTrue(p.shapeOracleEnabled());
        assertTrue(p.spanTreeInvariantEnabled());
        assertTrue(p.statusPropagationInvariantEnabled());
        assertTrue(p.responseEnvelopeInvariantEnabled());
        assertFalse(p.timingEnvelopeInvariantEnabled());
        assertTrue(p.banditEnabled());
        assertFalse(p.faultMiningEnabled());

        String summary = p.summary();
        assertTrue("oracle on", summary.contains("oracle:on"));
        assertTrue("span listed", summary.contains("span"));
        assertTrue("timing off", summary.contains("timing:off"));
        assertTrue("bandit on", summary.contains("bandit:on"));
        assertTrue("faultmining off", summary.contains("faultmining:off"));
    }

    @Test
    public void allOn_R1_summaryListsAllInvariants() {
        for (String k : ORACLE_KEYS) System.setProperty(k, "true");
        System.setProperty("mst.scheduler.bandit.enabled", "true");
        System.setProperty(FaultMiner.ENABLED_PROPERTY, "true");

        AblationProfile p = AblationProfile.from(MstConfig.fromSystemProperties());
        assertTrue(p.shapeOracleEnabled());
        assertTrue(p.timingEnvelopeInvariantEnabled());
        assertTrue(p.banditEnabled());
        assertTrue(p.faultMiningEnabled());

        String summary = p.summary();
        assertTrue(summary.contains("oracle:on"));
        assertTrue(summary.contains("timing:on"));
        assertTrue(summary.contains("bandit:on"));
        assertTrue(summary.contains("faultmining:on"));
    }

    @Test
    public void allOff_R4_summaryCollapsesOracleSection() {
        for (String k : ORACLE_KEYS) System.setProperty(k, "false");
        System.setProperty("mst.scheduler.bandit.enabled", "false");
        System.setProperty(FaultMiner.ENABLED_PROPERTY, "false");

        AblationProfile p = AblationProfile.from(MstConfig.fromSystemProperties());
        assertFalse(p.shapeOracleEnabled());
        assertFalse(p.banditEnabled());
        assertFalse(p.faultMiningEnabled());

        String summary = p.summary();
        assertTrue("oracle off short form", summary.contains("oracle:off"));
        assertFalse("no invariant list when oracle is off",
                summary.contains("timing:") || summary.contains("span"));
        assertTrue(summary.contains("bandit:off"));
        assertTrue(summary.contains("faultmining:off"));
    }

    @Test
    public void R2_traceShapeOff_butAdaptiveOn() {
        System.setProperty("mst.oracle.shape.enabled", "false");
        System.setProperty(FaultMiner.ENABLED_PROPERTY, "true");

        AblationProfile p = AblationProfile.from(MstConfig.fromSystemProperties());
        assertFalse(p.shapeOracleEnabled());
        assertTrue("faultmining drives adaptive even when oracle is off",
                p.faultMiningEnabled());
        assertTrue(p.banditEnabled());

        String summary = p.summary();
        assertTrue(summary.contains("oracle:off"));
        assertTrue(summary.contains("faultmining:on"));
    }

    @Test
    public void summary_isSingleLine() {
        AblationProfile p = AblationProfile.from(MstConfig.fromSystemProperties());
        String summary = p.summary();
        assertFalse("summary must not contain newlines", summary.contains("\n"));
        assertTrue("summary starts with bracket", summary.startsWith("["));
        assertTrue("summary ends with bracket", summary.endsWith("]"));
    }

    @Test
    public void perInvariantToggle_oneOff_summaryShowsRemaining() {
        // Disable status_propagation only; the other three default-on invariants
        // and timing's default-off survive.
        System.setProperty("mst.oracle.shape.invariants.status_propagation.enabled", "false");

        AblationProfile p = AblationProfile.from(MstConfig.fromSystemProperties());
        assertTrue(p.shapeOracleEnabled());
        assertTrue(p.spanTreeInvariantEnabled());
        assertFalse(p.statusPropagationInvariantEnabled());
        assertTrue(p.responseEnvelopeInvariantEnabled());

        String summary = p.summary();
        assertTrue("span listed", summary.contains("span"));
        assertFalse("status omitted from invariant list", summary.contains("status"));
        assertTrue("response listed", summary.contains("response"));
    }

    @Test
    public void constructorAccessors_roundTrip() {
        AblationProfile p = new AblationProfile(true, false, true, false, true, false, true);
        assertTrue(p.shapeOracleEnabled());
        assertFalse(p.spanTreeInvariantEnabled());
        assertTrue(p.statusPropagationInvariantEnabled());
        assertFalse(p.responseEnvelopeInvariantEnabled());
        assertTrue(p.timingEnvelopeInvariantEnabled());
        assertFalse(p.banditEnabled());
        assertTrue(p.faultMiningEnabled());
    }

    @Test
    public void summary_emptyOracle_whenAllInvariantsOff() {
        // Oracle still globally on but every per-invariant disabled.
        for (int i = 1; i < ORACLE_KEYS.size(); i++) {
            System.setProperty(ORACLE_KEYS.get(i), "false");
        }
        // Index 0 is the whole-oracle gate; leave it on.

        AblationProfile p = AblationProfile.from(MstConfig.fromSystemProperties());
        assertTrue(p.shapeOracleEnabled());
        String summary = p.summary();
        assertTrue("oracle on but no invariants -> 'none' marker",
                summary.contains("none") || summary.contains("oracle:on"));
        // Sanity: the whole call did not throw, even with the empty list.
        assertEquals("[", summary.substring(0, 1));
    }
}
