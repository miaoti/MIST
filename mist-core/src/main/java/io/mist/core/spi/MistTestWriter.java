package io.mist.core.spi;

import java.util.List;

/**
 * Strategy for serialising MIST-generated test cases into a target test
 * framework's source files (e.g. JUnit + RESTAssured). mist-core's
 * generation pipeline produces opaque test-case payloads and hands them
 * to whichever writer the runtime finds via
 * {@link java.util.ServiceLoader}.
 *
 * <p>The writer choice is orthogonal to MIST's contribution; keeping it
 * behind this SPI lets a future MIST distribution swap the output
 * framework (e.g. plain {@code java.net.http} or {@code rest-assured-5})
 * without touching mist-core.
 *
 * <p>Implementations are stateful between {@link #write(List, String)}
 * calls (the bundled RESTAssured writer caches header / cookie /
 * fingerprint state across scenarios for de-duplication), but each
 * single call is expected to be reentrant against its own state.
 *
 * @param <T> the writer-specific test-case carrier type
 */
public interface MistTestWriter<T> {

    /**
     * Write the given test cases into compilable test source files under
     * {@code outputDirectory}. The writer is responsible for naming the
     * generated files, picking the package, and any framework-specific
     * scaffolding.
     *
     * @param testCases the test cases to emit
     * @param outputDirectory absolute path to the directory where source
     *        files are placed; created if it does not exist
     */
    void write(List<T> testCases, String outputDirectory);
}
