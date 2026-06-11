package io.mist.core.config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the {@link MstConfig.Adaptive} sub-record's read paths and defaults.
 * Mirrors the property-save/restore pattern used by
 * {@link AblationProfileTest} so the singleton {@link MstConfig} can be
 * rebuilt under different system-property snapshots without leaking state
 * across tests.
 *
 * <p>The defaults asserted here are the regression gate for the
 * {@code mst.adaptive.enabled=false} path: any drift means a non-adaptive
 * run will not match pre-adaptive behaviour byte-for-byte.
 */
public class MstConfigAdaptiveTest {

    private static final List<String> ADAPTIVE_KEYS = Arrays.asList(
            "mst.adaptive.enabled",
            "mst.adaptive.k.dedup.exhausted",
            "mst.adaptive.k.zero.step",
            "mst.adaptive.auth.token.min.age.ns"
    );

    private final Map<String, String> previous = new HashMap<>();

    @Before
    public void setUp() {
        previous.clear();
        for (String k : ADAPTIVE_KEYS) {
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
    public void defaults_match_LEGACY_so_disabled_run_is_byte_identical() {
        MstConfig.Adaptive a = MstConfig.fromSystemProperties().adaptive();
        assertFalse("adaptive must default OFF — opt-in only", a.enabled());
        // The two K defaults must mirror io.mist.core.policy.EndpointPolicy.LEGACY
        // because MistGenerator.resolveEndpointPolicy returns LEGACY when
        // adaptive is off; if these constants drifted apart, the "adaptive
        // disabled = legacy behaviour" claim would silently break.
        assertEquals(10, a.kDedupExhaustedDefault());
        assertEquals(3, a.kZeroStepDefault());
        // 5 seconds in nanoseconds — the historical MstAuthRefreshFilter
        // hard-coded constant. Must match exactly so the off-path is a no-op.
        assertEquals(5_000_000_000L, a.authTokenMinAgeNs());
    }

    @Test
    public void enabled_flagPicksUpSystemProperty() {
        System.setProperty("mst.adaptive.enabled", "true");
        MstConfig.Adaptive a = MstConfig.fromSystemProperties().adaptive();
        assertTrue(a.enabled());
    }

    @Test
    public void kDedupExhaustedDefault_overridable() {
        System.setProperty("mst.adaptive.k.dedup.exhausted", "42");
        MstConfig.Adaptive a = MstConfig.fromSystemProperties().adaptive();
        assertEquals(42, a.kDedupExhaustedDefault());
    }

    @Test
    public void kZeroStepDefault_overridable() {
        System.setProperty("mst.adaptive.k.zero.step", "7");
        MstConfig.Adaptive a = MstConfig.fromSystemProperties().adaptive();
        assertEquals(7, a.kZeroStepDefault());
    }

    @Test
    public void authTokenMinAgeNs_overridable() {
        // 10 seconds: lets ops bump the post-403 quiet window for slow auth
        // backends without recompiling.
        System.setProperty("mst.adaptive.auth.token.min.age.ns", "10000000000");
        MstConfig.Adaptive a = MstConfig.fromSystemProperties().adaptive();
        assertEquals(10_000_000_000L, a.authTokenMinAgeNs());
    }

}
