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
 * Pins the two main-track flags reserved by TOOL-EXECUTION-PLAN P1:
 * {@code mst.oracle.dataintegrity.enabled} (B2 differential data-integrity
 * oracle, read through {@link MstConfig.Oracle}) and
 * {@code mist.fault.injection.enabled} (B1 fault-injection mode, read
 * directly via {@code System.getProperty} by the mist-cli pairing executor —
 * deliberately NOT an MstConfig field, mirroring FaultMiner's gating).
 *
 * <p>Both must default OFF so a flags-off run stays byte-identical with
 * the pre-P1 baseline. Mirrors the property-save/restore pattern of
 * {@link MstConfigAdaptiveTest}.
 */
public class MstConfigDataIntegrityTest {

    private static final List<String> KEYS = Arrays.asList(
            "mst.oracle.dataintegrity.enabled",
            "mist.fault.injection.enabled",
            "mst.config.strict"
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
    public void dataIntegrityOracle_defaultsOff() {
        assertFalse("B2 must default OFF — opt-in only",
                MstConfig.fromSystemProperties().oracle().dataIntegrityOracleEnabled());
    }

    @Test
    public void dataIntegrityOracle_optInPropagates() {
        System.setProperty("mst.oracle.dataintegrity.enabled", "true");
        assertTrue(MstConfig.fromSystemProperties().oracle().dataIntegrityOracleEnabled());
    }

    @Test
    public void reservedKeys_surviveStrictValidator() {
        // Whitelist regression gate: fromSystemProperties() runs the
        // validator; under strict mode an un-whitelisted key in an owned
        // namespace becomes a fatal IllegalStateException. Setting both
        // reserved keys plus strict must therefore complete without throwing.
        System.setProperty("mst.oracle.dataintegrity.enabled", "true");
        System.setProperty("mist.fault.injection.enabled", "true");
        System.setProperty("mst.config.strict", "true");
        MstConfig cfg = MstConfig.fromSystemProperties();
        assertTrue(cfg.oracle().dataIntegrityOracleEnabled());
    }
}
