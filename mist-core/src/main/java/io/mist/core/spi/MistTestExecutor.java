package io.mist.core.spi;

/**
 * Strategy for executing previously-written MIST test sources. Kept
 * separate from {@link MistTestWriter} so a future workflow can write
 * once and execute many times (e.g. ablation re-runs against the same
 * generated suite).
 *
 * <p>The bundled implementation in {@code mist-restest-adapter} shells
 * out to a Maven Failsafe / Surefire invocation; alternative
 * implementations can wire JUnit programmatically.
 */
public interface MistTestExecutor {

    /**
     * Run the test suite under {@code testsDirectory}. The return value is
     * the number of failed assertions reported by the executed suite, or
     * a non-zero sentinel when the runner could not even start the
     * suite.
     *
     * @param testsDirectory absolute path to the directory containing the
     *        previously-written test sources
     * @return failure count (zero means everything passed)
     */
    int execute(String testsDirectory);
}
