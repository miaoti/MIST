package io.mist.cli;

import io.mist.core.spi.MistServices;
import io.mist.core.spi.MistSpecLoader;
import io.mist.core.spi.MistTestExecutor;
import io.mist.core.spi.MistTestWriter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration test that verifies the four MIST SPIs in
 * {@code io.mist.core.spi.*} are all discoverable on the cli
 * classpath — i.e. that the adapter's
 * {@code META-INF/services/io.mist.core.spi.*} registrations are
 * picked up by {@link java.util.ServiceLoader}.
 *
 * <p>This sits in mist-cli rather than mist-core because mist-cli
 * is the only module with both mist-core and mist-restest-adapter on
 * its classpath; testing the wiring in mist-core itself would create
 * a forbidden cross-module dependency.
 */
public class SpiDiscoveryIntegrationTest {

    @Test
    public void spec_loader_is_discoverable() {
        MistSpecLoader loader = MistServices.requireSpecLoader();
        assertNotNull(loader);
        assertEquals(
                "io.mist.cli.spi.RestestMistSpecLoader",
                loader.getClass().getName());
    }

    @Test
    public void test_writer_is_discoverable() {
        MistTestWriter<Object> writer = MistServices.requireTestWriter();
        assertNotNull(writer);
        assertEquals(
                "io.mist.cli.spi.RestAssuredMistTestWriter",
                writer.getClass().getName());
    }

    @Test
    public void test_executor_is_discoverable() {
        MistTestExecutor executor = MistServices.requireTestExecutor();
        assertNotNull(executor);
        assertEquals(
                "io.mist.cli.spi.MavenSurefireMistTestExecutor",
                executor.getClass().getName());
    }

    @Test
    public void isPresent_returns_true_for_all_registered_spis() {
        assertTrue(MistServices.isPresent(MistSpecLoader.class));
        assertTrue(MistServices.isPresent(MistTestWriter.class));
        assertTrue(MistServices.isPresent(MistTestExecutor.class));
    }
}
