package io.mist.cli.spi;

import java.io.IOException;
import java.util.Arrays;

import io.mist.core.spi.MistTestExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * {@link MistTestExecutor} implementation that shells out to a Maven
 * Surefire test invocation against the directory containing the
 * previously-written test sources.
 *
 * <p>This is the simplest possible executor — it relies on the
 * surrounding project's Maven configuration (Surefire / Failsafe
 * plugin bound to {@code mvn test}) to do the actual JUnit driving.
 * A future executor implementation can drive JUnit programmatically
 * for tighter pass/fail counting; for now {@code mvn test}'s exit
 * code is the only failure signal.
 *
 * <p>This class is wired via {@link java.util.ServiceLoader} from
 * {@code META-INF/services/io.mist.core.spi.MistTestExecutor}.
 */
public final class MavenSurefireMistTestExecutor implements MistTestExecutor {

    private static final Logger log =
            LogManager.getLogger(MavenSurefireMistTestExecutor.class);

    /** Required no-arg constructor for {@link java.util.ServiceLoader}. */
    public MavenSurefireMistTestExecutor() {}

    @Override
    public int execute(String testsDirectory) {
        if (testsDirectory == null || testsDirectory.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "testsDirectory must not be blank");
        }
        log.info("[MistTestExecutor] mvn test target: {}", testsDirectory);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    Arrays.asList("mvn", "-q", "test",
                            "-Dtest=" + classGlob(testsDirectory)))
                    .inheritIO()
                    .redirectErrorStream(true);
            Process p = pb.start();
            int code = p.waitFor();
            log.info("[MistTestExecutor] mvn test exit code: {}", code);
            return code;
        } catch (IOException | InterruptedException e) {
            log.error("Failed to invoke mvn test on {}", testsDirectory, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return -1;
        }
    }

    /**
     * Translate a tests-directory to a Surefire {@code -Dtest=} glob.
     * The bundled MIST output convention places each scenario in its
     * own sub-package, so a directory-name-based glob is the safest
     * default until the executor surface grows a richer selection
     * API.
     */
    private static String classGlob(String testsDirectory) {
        java.io.File f = new java.io.File(testsDirectory);
        return f.getName() + ".*";
    }
}
