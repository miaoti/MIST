package io.mist.cli.tools;

import io.mist.cli.fault.DataIntegrityRuntime;
import io.mist.cli.fault.TargetTripleRegistry;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * E2E Allure demo (2026-07-18): run ALREADY-GENERATED MIST scenario classes under a
 * manually-armed data-integrity OBSERVE session with the Allure JUnit4 listener attached —
 * the surgical replay of MistRunner's observe branch (beginObserveRun → run → endRun →
 * Allure categories) for a HAND-PICKED subset of the generated suite, so the DI-hooked
 * write scenario is reached inside a bounded window (the full-suite run starved the hooked
 * stretch behind slow unrelated scenarios — the MYC observe-starvation mechanism).
 *
 * <p>Usage: {@code TinyObserveRunner <target-triples.yaml> <label> <token> [token...]} where a
 * token is either a test-class name or {@code exec:<shell command>} (run via bash -c between
 * class executions INSIDE the same observe session — e.g. flipping the SUT fault flag mid-run,
 * the intermittent-production-bug shape the U8 read-back-validation quarantine expects).
 * Usual observe-mode system properties apply ({@code mst.oracle.dataintegrity.failonlost},
 * {@code jaeger.base.url}, {@code allure.results.directory}).
 */
public final class TinyObserveRunner {

    private TinyObserveRunner() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: TinyObserveRunner <target-triples.yaml> <label> <testClass>...");
            System.exit(2);
        }
        TargetTripleRegistry.Registry reg = TargetTripleRegistry.load(Paths.get(args[0]));

        DataIntegrityRuntime.beginObserveRun(reg.triples, args[1]);
        JUnitCore core = new JUnitCore();
        core.addListener(new io.qameta.allure.junit4.AllureJunit4());
        int run = 0;
        int failed = 0;
        java.util.List<String> failures = new java.util.ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("exec:")) {
                String cmd = args[i].substring(5);
                System.out.println(">> exec: " + cmd);
                Process proc = new ProcessBuilder("bash", "-c", cmd)
                        .redirectErrorStream(true).start();
                new java.io.BufferedReader(new java.io.InputStreamReader(proc.getInputStream()))
                        .lines().forEach(l -> System.out.println("   " + l));
                int rc = proc.waitFor();
                System.out.println(">> exec rc=" + rc);
                continue;
            }
            Result result = core.run(Class.forName(args[i]));
            run += result.getRunCount();
            failed += result.getFailureCount();
            result.getFailures().forEach(f -> failures.add(f.getTestHeader() + " :: "
                    + String.valueOf(f.getMessage()).replace('\n', ' ')));
        }
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();

        writeAllureCategories();

        System.out.println("=== TinyObserveRunner summary ===");
        System.out.println("tests run=" + run + " failures=" + failed);
        failures.forEach(f -> System.out.println("FAILURE: " + f));
        System.out.println("data-integrity records: " + records.size());
        for (DataIntegrityRuntime.RunRecord r : records) {
            System.out.println("DI[" + r.tripleName + "] acked=" + r.acked
                    + " ackHttp=" + r.ackHttpStatus
                    + " gate=" + r.gate
                    + " readbackContainedX=" + r.readbackContainedX
                    + (r.error != null ? " error=" + r.error : "")
                    + (r.traceShapeNote != null ? " traceShape=" + r.traceShapeNote : ""));
        }
    }

    /** Same categories MistRunner's observe branch writes (💧 ACKED-BUT-LOST + trace box). */
    private static void writeAllureCategories() throws Exception {
        String dirProp = System.getProperty("allure.results.directory", "target/allure-results");
        Path dir = Paths.get(dirProp);
        Files.createDirectories(dir);
        String json = "[\n"
                + "  {\"name\": \"\\uD83D\\uDCA7 Data integrity — acked-but-lost write\","
                + " \"matchedStatuses\": [\"failed\", \"broken\"],"
                + " \"messageRegex\": \".*ACKED-BUT-LOST.*\"},\n"
                + "  {\"name\": \"Hidden downstream failure (trace)\","
                + " \"matchedStatuses\": [\"failed\", \"broken\"],"
                + " \"messageRegex\": \".*HIDDEN DOWNSTREAM.*\"}\n"
                + "]\n";
        Files.write(dir.resolve("categories.json"), json.getBytes(StandardCharsets.UTF_8));
    }
}
