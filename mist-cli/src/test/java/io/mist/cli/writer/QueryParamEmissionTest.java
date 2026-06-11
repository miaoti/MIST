package io.mist.cli.writer;

import io.mist.core.spec.Operation;
import io.mist.core.testcase.MultiServiceTestCase;
import io.mist.core.testcase.TestCase;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Locks the query-parameter emission added to the writer: query params were
 * collected by the generator and captured for telemetry but never emitted
 * into the generated request, so query-declared operations (Sock Shop
 * catalogue) were exercised without their parameters and a query-located
 * fault target never reached the SUT.
 *
 * <p>Values must arrive form-encoded in the generated source because the
 * request spec runs with urlEncodingEnabled(false).
 */
public class QueryParamEmissionTest {

    @Test
    public void positiveQueryParams_areEmittedFormEncoded() throws IOException {
        MultiServiceTestCase tc = newTestCase(false);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("page", "1");
        query.put("tags", "a b&c");
        tc.addStepCall(step(query));

        String src = writeAndRead(tc);

        assertTrue("plain value must be emitted",
                src.contains("req.queryParam(\"page\", \"1\");"));
        assertTrue("value must be form-encoded (space → +, & → %26)",
                src.contains("req.queryParam(\"tags\", \"a+b%26c\");"));
    }

    @Test
    public void queryLocatedFaultTarget_replacesValueWithInvalid() throws IOException {
        MultiServiceTestCase tc = newTestCase(true);
        tc.setTargetFaultParamLocation("query");
        tc.addFaultyParameter("page", "NOT_A_NUMBER");
        Map<String, String> query = new LinkedHashMap<>();
        query.put("page", "1");
        tc.addStepCall(step(query));

        String src = writeAndRead(tc);

        assertTrue("fault value must replace the positive value",
                src.contains("req.queryParam(\"page\", \"NOT_A_NUMBER\");"));
        assertFalse("the valid value must not also be emitted for the target param",
                src.contains("req.queryParam(\"page\", \"1\");"));
    }

    @Test
    public void dependencyWiredQueryParam_isNotEmittedAsLiteral() throws IOException {
        MultiServiceTestCase tc = newTestCase(false);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("page", "1");
        MultiServiceTestCase.StepCall s = step(query);
        s.addParamDependency("page", 0, "data.id");
        tc.addStepCall(s);

        String src = writeAndRead(tc);

        assertFalse("dependency-wired param must not be double-emitted as a literal",
                src.contains("req.queryParam(\"page\", \"1\");"));
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static MultiServiceTestCase newTestCase(boolean faulty) {
        MultiServiceTestCase tc = new MultiServiceTestCase("QueryDemo");
        tc.setScenarioName("QueryScenario");
        tc.setFaulty(faulty);
        return tc;
    }

    private static MultiServiceTestCase.StepCall step(Map<String, String> query) {
        Operation op = new Operation();
        op.setMethod("get");
        op.setTestPath("/catalogue");
        MultiServiceTestCase.StepCall s = new MultiServiceTestCase.StepCall(
                "catalogue", op, "/catalogue", null, query, null, null, 200, null);
        s.setTopLevelRoot(true);
        return s;
    }

    private static String writeAndRead(MultiServiceTestCase tc) throws IOException {
        Path out = Files.createTempDirectory("query-emission-test");
        MultiServiceRESTAssuredWriter writer = new MultiServiceRESTAssuredWriter(
                null, null, out.toString(), "QueryDemo", "io.mist.generated", "http://localhost", false);
        writer.setAllureReport(true);
        writer.write(Collections.<TestCase>singletonList(tc));
        try (Stream<Path> files = Files.walk(out)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .map(p -> {
                        try {
                            return Files.readString(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.joining("\n"));
        }
    }
}
