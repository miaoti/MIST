package io.mist.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Outcome of a single {@link MistRunner#run()} invocation.
 *
 * <p>Created by Phase 1.A of {@code PATH_B_REBUILD_PLAN.md} to give the new
 * {@code mist.jar} entry point a typed return value instead of relying on
 * {@code System.exit(0)} buried inside the old MST branch of
 * {@link TestGenerationAndExecution#main(String[])}.
 */
public final class MistRunResult {

    private final int exitCode;
    private final int testCaseCount;
    private final Path allureReportDir;
    private final String runId;

    private MistRunResult(int exitCode, int testCaseCount, Path allureReportDir, String runId) {
        this.exitCode = exitCode;
        this.testCaseCount = testCaseCount;
        this.allureReportDir = allureReportDir;
        this.runId = runId;
    }

    public int exitCode() { return exitCode; }
    public int testCaseCount() { return testCaseCount; }
    public Path allureReportDir() { return allureReportDir; }
    public String runId() { return runId; }

    public void summarise(PrintStream out) {
        Objects.requireNonNull(out, "out");
        out.println("MIST run " + runId + " complete.");
        out.println("  test cases generated: " + testCaseCount);
        if (allureReportDir != null) {
            out.println("  allure report dir:    " + allureReportDir);
        }
        out.println("  exit code:            " + exitCode);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int exitCode = 0;
        private int testCaseCount = 0;
        private Path allureReportDir;
        private String runId = "";

        public Builder exitCode(int v)          { this.exitCode = v; return this; }
        public Builder testCaseCount(int v)     { this.testCaseCount = v; return this; }
        public Builder allureReportDir(Path v)  { this.allureReportDir = v; return this; }
        public Builder runId(String v)          { this.runId = v == null ? "" : v; return this; }
        public MistRunResult build() {
            return new MistRunResult(exitCode, testCaseCount, allureReportDir, runId);
        }
    }
}
