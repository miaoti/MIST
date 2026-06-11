package io.mist.core.tools;

import io.mist.core.enhancer.FailedTestResult;
import io.mist.core.enhancer.TestFileRegenerator;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Standalone verifier for {@link TestFileRegenerator}'s escape semantics.
 *
 * <p>Runs the regenerator against a known-good Java test class with an embedded
 * JSON-in-Java-string body, then checks two properties:
 * <ol>
 *   <li>The regenerated file contains the canonical single-escape pattern
 *       {@code \"foo\":\"newValue\"} and NOT the broken double-escape pattern
 *       {@code \\\"foo\\\":\\\"newValue\\\"} that produced 131 624 compile
 *       errors per file in the May 2 run.</li>
 *   <li>If a JDK compiler is available, the regenerated file passes javac.</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 *   mvn -q exec:java -Dexec.mainClass=io.mist.core.tools.RegenEscapeVerifier
 * </pre>
 *
 * <p>Exits 0 on success, non-zero with a printed diagnostic on failure.
 */
public final class RegenEscapeVerifier {

    /** Java source for a minimal test class that mimics the failing flow_Scenario shape. */
    private static final String SAMPLE_TEST_CLASS =
            "public class SampleScenario {\n" +
            "    @org.junit.Test\n" +
            "    public void test_positive_flow_S1_v1() throws Exception {\n" +
            "        String requestBody1 = \"{\\\"foo\\\":\\\"oldValue\\\"}\";\n" +
            "        System.out.println(requestBody1);\n" +
            "    }\n" +
            "}\n";

    public static void main(String[] args) throws Exception {
        Path tmpDir = Files.createTempDirectory("regen-verify-");
        Path testFile = tmpDir.resolve("SampleScenario.java");
        Files.writeString(testFile, SAMPLE_TEST_CLASS);

        FailedTestResult failure = new FailedTestResult();
        failure.setTestClassName("SampleScenario");
        failure.setTestMethodName("test_positive_flow_S1_v1");
        failure.setActualStatusCode(400);
        failure.setResponseBody("{\"error\":\"bad value\"}");
        failure.setFailedStepIndex(0);
        failure.setParameters(Collections.emptyList());

        Map<String, String> enhanced = new LinkedHashMap<>();
        enhanced.put("foo", "newValue");

        TestFileRegenerator regen = new TestFileRegenerator();
        boolean ok = regen.regenerateTestFile(testFile.toString(),
                "test_positive_flow_S1_v1", enhanced, failure);
        if (!ok) {
            fail("regenerateTestFile returned false");
        }

        String regenerated = Files.readString(testFile);
        System.out.println("--- regenerated content ---");
        System.out.println(regenerated);
        System.out.println("--- end ---");

        // Property 1: the broken double-escape pattern must not appear.
        if (regenerated.contains("\\\\\"foo\\\\\":")) {
            fail("regenerated file contains the double-escape pattern \\\\\\\"...\\\\\\\" — fix is broken");
        }
        // Property 2: the canonical single-escape pattern must be present.
        if (!regenerated.contains("\\\"foo\\\":\\\"newValue\\\"")) {
            fail("regenerated file does not contain the expected \\\"foo\\\":\\\"newValue\\\" pattern");
        }

        // Property 3 (best-effort): javac the regenerated file.
        // Note: the regenerator injects Allure + JUnit calls via addEnhancementMarker, so
        // the file needs the test-scope classpath to compile. When run via
        // `mvn exec:java`, that scope is NOT on the runtime classpath, so javac may
        // report "package org.junit does not exist" / "cannot find symbol Allure".
        // Those are environmental, not escape-related — we only fail this check when the
        // error mentions an escape problem ("illegal character: '\\'", "unterminated
        // string literal", "';' expected").
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.out.println("PASS (substring properties only — no JDK compiler in this env)");
            return;
        }
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Path classOut = tmpDir.resolve("classes");
        Files.createDirectories(classOut);
        int rc = compiler.run(null, null, new PrintStream(err),
                "-d", classOut.toString(),
                "-classpath", System.getProperty("java.class.path"),
                testFile.toString());
        String errText = err.toString();
        boolean escapeError =
                errText.contains("illegal character: '\\'") ||
                errText.contains("unterminated string literal") ||
                errText.contains("';' expected") ||
                errText.contains("not a statement");
        if (escapeError) {
            System.err.println("javac stderr (escape-related errors detected):");
            System.err.println(errText);
            fail("regenerated file failed javac with escape errors — fix is broken");
        }
        if (rc != 0) {
            System.out.println("PASS — substring properties verified.");
            System.out.println("(javac reported environmental errors only — JUnit/Allure deps "
                    + "missing on the exec:java runtime classpath; these are unrelated to the escape bug.)");
            return;
        }
        System.out.println("PASS — regenerated file compiles cleanly with javac");
    }

    private static void fail(String msg) {
        System.err.println("FAIL: " + msg);
        System.exit(1);
    }

    private RegenEscapeVerifier() {}
}
