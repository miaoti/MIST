package io.mist.core.spi;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Lazily-resolved service locator for the four MIST SPIs. Each SPI is
 * loaded via {@link ServiceLoader} from the runtime classpath; the
 * first implementation discovered wins. If no implementation is
 * available, the corresponding {@code requireXxx} accessor throws an
 * {@link IllegalStateException} with a message naming the missing SPI
 * so the user can fix the classpath instead of seeing a
 * {@link NullPointerException} ten frames deep.
 *
 * <p>The bundled {@code mist-restest-adapter} module ships an adapter
 * for each SPI under {@code io.mist.cli.spi} and registers it
 * via {@code META-INF/services/}. Running {@code mist-cli} without the
 * adapter on the classpath is a configuration error that this class
 * surfaces cleanly.
 *
 * <p>All accessors are idempotent — the lookup happens on first call
 * and the result is memoised.
 */
public final class MistServices {

    private static volatile MistSpecLoader specLoader;
    private static volatile MistTestWriter<?> testWriter;
    private static volatile MistTestExecutor testExecutor;

    private MistServices() {}

    /**
     * Returns the first {@link MistSpecLoader} found on the classpath, or
     * throws if none are registered.
     */
    public static MistSpecLoader requireSpecLoader() {
        MistSpecLoader local = specLoader;
        if (local == null) {
            local = first(MistSpecLoader.class);
            if (local == null) {
                throw new IllegalStateException(
                        "No io.mist.core.spi.MistSpecLoader implementation on classpath — "
                                + "include mist-restest-adapter (or another adapter that registers "
                                + "the SPI via META-INF/services/) in the runtime classpath.");
            }
            specLoader = local;
        }
        return local;
    }

    /**
     * Returns the first {@link MistTestWriter} found on the classpath, or
     * throws if none are registered.
     */
    @SuppressWarnings("unchecked")
    public static <T> MistTestWriter<T> requireTestWriter() {
        MistTestWriter<?> local = testWriter;
        if (local == null) {
            local = first(MistTestWriter.class);
            if (local == null) {
                throw new IllegalStateException(
                        "No io.mist.core.spi.MistTestWriter implementation on classpath — "
                                + "include mist-restest-adapter (or another adapter that registers "
                                + "the SPI via META-INF/services/) in the runtime classpath.");
            }
            testWriter = local;
        }
        return (MistTestWriter<T>) local;
    }

    /**
     * Returns the first {@link MistTestExecutor} found on the classpath,
     * or throws if none are registered.
     */
    public static MistTestExecutor requireTestExecutor() {
        MistTestExecutor local = testExecutor;
        if (local == null) {
            local = first(MistTestExecutor.class);
            if (local == null) {
                throw new IllegalStateException(
                        "No io.mist.core.spi.MistTestExecutor implementation on classpath — "
                                + "include mist-restest-adapter (or another adapter that registers "
                                + "the SPI via META-INF/services/) in the runtime classpath.");
            }
            testExecutor = local;
        }
        return local;
    }

    /**
     * Returns true when an implementation of the named SPI is present
     * on the runtime classpath. Used by callers that want to soft-fall
     * back when an adapter is optional (the writer and the executor
     * are mandatory at run time, but a unit-test build that mocks the
     * spec loader can run without the writer SPI on the classpath).
     */
    public static <T> boolean isPresent(Class<T> spi) {
        return first(spi) != null;
    }

    private static <T> T first(Class<T> spi) {
        ServiceLoader<T> loader = ServiceLoader.load(spi);
        Iterator<T> it = loader.iterator();
        return it.hasNext() ? it.next() : null;
    }
}
