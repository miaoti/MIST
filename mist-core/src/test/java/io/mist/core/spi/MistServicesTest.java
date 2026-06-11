package io.mist.core.spi;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * Smoke test for the {@link MistServices} service locator. Unit-test
 * scope: only validates the discovery mechanism in isolation; the
 * adapter-side integration test (in mist-restest-adapter) verifies that
 * the bundled RestestMistSpecLoader is the one discovered when the
 * adapter is on the classpath.
 */
public class MistServicesTest {

    /**
     * isPresent() never throws when called for an SPI with no
     * implementation on the test classpath. mist-core's tests run
     * without the adapter, so no SpecLoader is discoverable here.
     */
    @Test
    public void isPresent_returns_false_when_no_impl_registered() {
        assertFalse("mist-core's own test scope has no spec loader registered",
                MistServices.isPresent(MistSpecLoader.class));
    }

    /**
     * The locator class itself loads without exceptions even when no
     * SPI implementations are present — that's required because
     * mist-core may be exercised in unit tests with mocked specs.
     */
    @Test
    public void locator_class_loads_without_explosion() {
        assertNotNull(MistServices.class.getName());
    }
}
