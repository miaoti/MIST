package io.mist.cli;

import io.mist.cli.MistPathResolver;
import io.mist.cli.MistRunResult;
import io.mist.cli.MistRunner;
import io.mist.core.config.MstConfig;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Stand-alone MIST entry point. Lives in the {@code mist-cli} module
 * (Stage 1.C of {@code PATH_B_REBUILD_PLAN.md}). The legacy
 * {@code TestGenerationAndExecution} still dispatches MST work to
 * {@link MistRunner}; this class lets {@code java -jar mist.jar} reach
 * the same runner without crossing the RESTest main class.
 *
 * <p>Behaviour mirror.  Both launch paths
 * ({@code java -jar restest.jar} and {@code java -jar mist.jar})
 * end at {@link MistRunner#run()} with the same {@link MstConfig}.
 * To keep the resulting System-properties state byte-identical to
 * {@code TestGenerationAndExecution.main}, this entry point pushes
 * only the MST-file keys into System (via the legacy
 * {@code io.mist.core.config.legacy.MstConfig}
 * loader's {@code applyToSystemProperties()}); core-file keys stay in
 * the local {@code Properties} bag and feed the {@link MistRunner.Inputs}
 * builder directly. Under {@code -Drandom.seed=42} this gives byte-
 * identical scenario output between the two launch paths (Stage 1.D
 * gate of the plan).
 */
public final class MistMain {

    private MistMain() {}

    public static void main(String[] args) throws Exception {
        Path propsFile = Paths.get(args.length > 0
                ? args[0]
                : "mist-cli/src/main/resources/My-Example/trainticket-demo.properties")
                .toAbsolutePath().normalize();

        Properties coreProps = new Properties();
        try (InputStream in = Files.newInputStream(propsFile)) {
            coreProps.load(in);
        } catch (NoSuchFileException nsfe) {
            System.err.println("MIST: properties file not found: " + propsFile);
            System.err.println();
            System.err.println("Usage: java -jar mist-cli/target/mist.jar <path/to/your.properties>");
            System.err.println();
            System.err.println("For the bundled TrainTicket demo from the repo root:");
            System.err.println("  java -jar mist-cli/target/mist.jar mist-cli/src/main/resources/My-Example/trainticket-demo.properties");
            System.err.println();
            System.err.println("In IntelliJ, use the pre-shipped run configuration");
            System.err.println("'MIST: Demo (bundled TrainTicket)' (Run → Edit Configurations).");
            System.exit(2);
            return;
        }

        // Resolve relative INPUT paths against the .properties file's own
        // directory so the demo works the same from repo root, from any
        // module directory, or via IntelliJ play-button (the JVM CWD no
        // longer matters for input lookup).
        MistPathResolver.resolveInputPaths(coreProps, propsFile.toFile());

        // Push core-file network keys to System.properties so code that
        // runs before any generated test has set RestAssured.baseURI
        // (notably the SUT preflight + MstAuthHandler login, both added
        // in commits 534b028e + ddb38cb3) can read base.url + auth.*
        // directly. The MST file already does this via
        // applyToSystemProperties; the core file did not have an
        // analogous push because pre-preflight MIST only needed these at
        // test-runtime, by which time the writer-emitted
        // RestAssured.baseURI = "..." line had already run.
        for (String key : new String[]{
                "base.url",
                "auth.mode", "auth.static.token", "auth.header", "auth.token.prefix",
                "auth.login.url", "auth.login.username", "auth.login.password",
                "auth.login.body.template", "auth.login.token.json.path",
                "auth.login.expected.status"}) {
            String v = coreProps.getProperty(key);
            if (v != null && System.getProperty(key) == null) {
                System.setProperty(key, v);
            }
        }

        // MST keys are pushed to System properties — same code path the
        // legacy TestGenerationAndExecution.loadMstConfig() takes. Keeping the
        // System state identical between the two launch paths is what makes
        // their generation byte-identical under -Drandom.seed.
        //
        // Single-file mode (the default for every bundled demo): when the core
        // file does not name a separate overlay via mst.config.path, the core
        // .properties file ITSELF is the MST source — one file carries every
        // key. A split overlay remains supported for users who want the
        // separation; -D flags still override either way (applyToSystem
        // never clobbers an existing System property).
        String mstConfigPath = coreProps.getProperty("mst.config.path");
        if (mstConfigPath == null || mstConfigPath.trim().isEmpty()) {
            mstConfigPath = propsFile.toString();
        }
        if (mstConfigPath != null && !mstConfigPath.trim().isEmpty()) {
            io.mist.core.config.legacy.MstConfig
                    .load(mstConfigPath)
                    .applyToSystemProperties();
            // The MST file's own input paths are now in System; resolve them
            // against the MST file's own directory (which can differ from the
            // core file's directory when the user splits the two).
            //
            // Iterate MistPathResolver.MST_INPUT_PATH_KEYS rather than a
            // hardcoded list so adding a new MST input-path key only requires
            // touching one place. A previous duplicate list here silently
            // skipped smart.input.fetch.openapi.spec.path, which left the
            // OAS path CWD-relative and triggered the "OpenAPI specification
            // file not found" error when running from project root.
            Properties mstView = new Properties();
            for (String key : MistPathResolver.MST_INPUT_PATH_KEYS) {
                String v = System.getProperty(key);
                if (v != null) mstView.setProperty(key, v);
            }
            MistPathResolver.resolveInputPaths(
                    mstView, java.nio.file.Paths.get(mstConfigPath).toFile());
            mstView.forEach((k, v) -> System.setProperty(String.valueOf(k), String.valueOf(v)));
        }

        MstConfig config = MstConfig.fromSystemProperties();
        Path workdir = Paths.get(System.getProperty("user.dir"));

        // Fail fast with a readable error if the .properties file is missing
        // the two required input keys. Without this guard, a missing key
        // surfaces as a NullPointerException from FileInputStream deep inside
        // createMstGenerator — same root cause but the trace points at the
        // wrong line and gives no hint about the actual fix.
        String oasPathChk  = coreProps.getProperty("oas.path");
        String confPathChk = coreProps.getProperty("conf.path");
        if (oasPathChk == null || oasPathChk.trim().isEmpty()
                || confPathChk == null || confPathChk.trim().isEmpty()) {
            System.err.println("MIST: properties file is missing required key(s):");
            System.err.println("  file: " + propsFile);
            if (oasPathChk == null || oasPathChk.trim().isEmpty()) {
                System.err.println("    oas.path   (path to OpenAPI specification yaml)");
            }
            if (confPathChk == null || confPathChk.trim().isEmpty()) {
                System.err.println("    conf.path  (path to multi-service configuration yaml)");
            }
            System.err.println();
            System.err.println("For the bundled TrainTicket demo, your properties file should include:");
            System.err.println("  oas.path=trainticket/merged_openapi_spec 1.yaml");
            System.err.println("  conf.path=trainticket/real-system-conf.yaml");
            System.exit(2);
            return;
        }

        // Resolve the trace directory. If the SUT-specific key is absent we fall back to the
        // bundled TrainTicket demo trace dir so the no-arg demo path keeps working, but warn
        // loudly so a real (non-train-ticket) run does not silently consume demo traces.
        String traceFilePath = coreProps.getProperty("trace.file.path");
        if (traceFilePath == null || traceFilePath.trim().isEmpty()) {
            traceFilePath = "src/main/resources/My-Example/trainticket/test-trace";
            System.err.println("MIST: 'trace.file.path' not set in " + propsFile
                    + "; falling back to the bundled TrainTicket demo trace dir: " + traceFilePath);
        }

        MistRunner.Inputs inputs = MistRunner.Inputs.builder()
                .testClassName(coreProps.getProperty("testclass.name"))
                .targetDirJava(coreProps.getProperty("test.target.dir"))
                .packageName(coreProps.getProperty("experiment.name"))
                .experimentName(coreProps.getProperty("experiment.name"))
                .oasPath(coreProps.getProperty("oas.path"))
                .confPath(coreProps.getProperty("conf.path"))
                .propertiesFilePath(propsFile.toString())
                .mstPropertiesFilePath(mstConfigPath)
                .traceFilePath(traceFilePath)
                .numTestCases(parseIntOrNull(coreProps.getProperty("testsperoperation")))
                .faultyRatio(parseFloatOrNull(coreProps.getProperty("faulty.ratio")))
                .executeTestCases(parseBoolOrNull(coreProps.getProperty("experiment.execute")))
                .allureReports(parseBoolOrNull(coreProps.getProperty("allure.report")))
                .enableCSVStats(parseBoolOrNull(coreProps.getProperty("stats.csv")))
                .enableInputCoverage(parseBoolOrNull(coreProps.getProperty("coverage.input")))
                .enableOutputCoverage(parseBoolOrNull(coreProps.getProperty("coverage.output")))
                .deletePreviousResults(parseBoolOrNull(coreProps.getProperty("deletepreviousresults")))
                .logToFile(parseBoolOrNull(coreProps.getProperty("logToFile")))
                .checkTestCases(Boolean.parseBoolean(coreProps.getProperty("testcases.check", "false")))
                .proxy(normaliseProxy(coreProps.getProperty("proxy")))
                .build();

        MistRunResult result = new MistRunner(config, workdir, inputs).run();
        result.summarise(System.out);
        System.exit(result.exitCode());
    }

    private static Integer parseIntOrNull(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try { return Integer.parseInt(raw.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static Float parseFloatOrNull(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try { return Float.parseFloat(raw.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static Boolean parseBoolOrNull(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        return Boolean.parseBoolean(raw.trim());
    }

    private static String normaliseProxy(String raw) {
        if (raw == null || "null".equals(raw) || raw.split(":").length != 2) return null;
        return raw;
    }
}
