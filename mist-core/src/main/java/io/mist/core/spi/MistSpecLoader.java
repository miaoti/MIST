package io.mist.core.spi;

/**
 * Factory for {@link MistSpec} instances. mist-core looks up the
 * implementation via {@link java.util.ServiceLoader}, so a runtime that
 * includes {@code mist-restest-adapter} on the classpath gets the
 * RESTest-backed spec loader without any explicit wiring; a runtime that
 * does not include it fails fast at startup with a clear error.
 *
 * <p>Implementations must be thread-safe; {@link #load(String)} may be
 * called from multiple threads.
 */
public interface MistSpecLoader {

    /**
     * Parse the OpenAPI v3 specification at the given location (file path
     * or URL) and return a read-only {@link MistSpec} view.
     *
     * @throws RuntimeException if the location cannot be read or the
     *         document is not a valid OpenAPI v3 spec
     */
    MistSpec load(String location);
}
