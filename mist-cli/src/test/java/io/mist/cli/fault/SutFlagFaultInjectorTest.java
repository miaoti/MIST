package io.mist.cli.fault;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins the B1.1 SutFlagFaultInjector against a recorded Exec seam. The
 * critical contract is APPEND/STRIP on JAVA_TOOL_OPTIONS: the traced
 * TrainTicket topology loads the OTel javaagent through the same variable, so
 * inject must preserve it and clear must restore it — never overwrite or
 * unset wholesale. Live-cluster behavior is verified by the Gate-1 session.
 */
public class SutFlagFaultInjectorTest {

    private static final FaultInjector.FaultTarget TARGET =
            new FaultInjector.FaultTarget("ts-admin-route-service", "mist.fault.lostwrite.enabled");
    private static final String TOKEN = "-Dmist.fault.lostwrite.enabled=true";
    private static final String AGENT = "-javaagent:/otel/opentelemetry-javaagent.jar";
    private static final String LIST_HEADER = "# Deployment ts-admin-route-service, container ts-admin-route-service\n";

    private static final class RecordingExec implements SutFlagFaultInjector.Exec {
        final List<List<String>> calls = new ArrayList<>();
        final Deque<SutFlagFaultInjector.ExecResult> scripted = new ArrayDeque<>();
        IOException failNextWith;

        @Override
        public SutFlagFaultInjector.ExecResult run(List<String> argv, long timeoutSeconds) throws IOException {
            calls.add(new ArrayList<>(argv));
            if (failNextWith != null) {
                IOException e = failNextWith;
                failNextWith = null;
                throw e;
            }
            return scripted.isEmpty()
                    ? new SutFlagFaultInjector.ExecResult(0, "ok")
                    : scripted.removeFirst();
        }
    }

    private RecordingExec exec;
    private SutFlagFaultInjector injector;

    @Before
    public void setUp() {
        exec = new RecordingExec();
        injector = new SutFlagFaultInjector("minikube", "default", 180, exec);
    }

    @After
    public void tearDown() {
        System.clearProperty(FaultInjector.ENABLED_PROPERTY);
    }

    private void scriptList(String javaToolOptionsValue) {
        String body = LIST_HEADER + "OTHER_VAR=x\n"
                + (javaToolOptionsValue == null ? "" : "JAVA_TOOL_OPTIONS=" + javaToolOptionsValue + "\n");
        exec.scripted.add(new SutFlagFaultInjector.ExecResult(0, body));
    }

    @Test
    public void inject_onBareDeployment_setsOnlyTheFlag() {
        scriptList(null);
        injector.inject(TARGET);
        assertEquals(3, exec.calls.size());
        assertEquals(Arrays.asList(
                "kubectl", "--context=minikube", "set", "env",
                "deployment/ts-admin-route-service", "--list",
                "--request-timeout=30s",
                "-n", "default"), exec.calls.get(0));
        assertEquals(Arrays.asList(
                "kubectl", "--context=minikube", "set", "env",
                "deployment/ts-admin-route-service",
                "JAVA_TOOL_OPTIONS=" + TOKEN,
                "--request-timeout=30s",
                "-n", "default"), exec.calls.get(1));
        assertEquals(Arrays.asList(
                "kubectl", "--context=minikube", "rollout", "status",
                "deployment/ts-admin-route-service",
                "--timeout=180s",
                "-n", "default"), exec.calls.get(2));
    }

    @Test
    public void inject_onTracedDeployment_appendsAfterTheAgent() {
        scriptList(AGENT);
        injector.inject(TARGET);
        assertEquals("JAVA_TOOL_OPTIONS=" + AGENT + " " + TOKEN, exec.calls.get(1).get(5));
    }

    @Test
    public void inject_whenFlagAlreadyPresent_isANoOp() {
        scriptList(AGENT + " " + TOKEN);
        injector.inject(TARGET);
        assertEquals("read only — no set, no rollout", 1, exec.calls.size());
    }

    @Test
    public void clear_onTracedDeployment_restoresAgentOnly() {
        scriptList(AGENT + " " + TOKEN);
        injector.clear(TARGET);
        assertEquals(3, exec.calls.size());
        assertEquals("JAVA_TOOL_OPTIONS=" + AGENT, exec.calls.get(1).get(5));
        assertEquals("rollout", exec.calls.get(2).get(2));
    }

    @Test
    public void clear_whenOnlyOurFlag_unsetsTheVariable() {
        scriptList(TOKEN);
        injector.clear(TARGET);
        assertEquals("JAVA_TOOL_OPTIONS-", exec.calls.get(1).get(5));
    }

    @Test
    public void clear_whenFlagAbsent_isANoOpWithoutRollout() {
        scriptList(AGENT);
        injector.clear(TARGET);
        assertEquals(1, exec.calls.size());
        scriptList(null);
        injector.clear(TARGET);
        assertEquals(2, exec.calls.size());
    }

    @Test
    public void nullContext_omitsContextArg() {
        injector = new SutFlagFaultInjector(null, "default", 180, exec);
        scriptList(null);
        injector.inject(TARGET);
        assertEquals("kubectl", exec.calls.get(0).get(0));
        assertEquals("set", exec.calls.get(0).get(1));
        assertFalse(exec.calls.get(0).toString().contains("--context"));
    }

    @Test
    public void setEnvFailure_throwsAndSkipsRollout() {
        scriptList(null);
        exec.scripted.add(new SutFlagFaultInjector.ExecResult(1, "error: deployment not found"));
        try {
            injector.inject(TARGET);
            fail("expected FaultInjectionException");
        } catch (FaultInjector.FaultInjectionException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("deployment not found"));
        }
        assertEquals("rollout must not run after a failed set env", 2, exec.calls.size());
    }

    @Test
    public void rolloutFailure_throwsWithOutput() {
        scriptList(null);
        exec.scripted.add(new SutFlagFaultInjector.ExecResult(0, "env updated"));
        exec.scripted.add(new SutFlagFaultInjector.ExecResult(1, "error: rollout timed out"));
        try {
            injector.inject(TARGET);
            fail("expected FaultInjectionException");
        } catch (FaultInjector.FaultInjectionException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("rollout timed out"));
        }
    }

    @Test
    public void readFailure_onClear_propagates() {
        exec.failNextWith = new IOException("kubectl not on PATH");
        try {
            injector.clear(TARGET);
            fail("expected FaultInjectionException");
        } catch (FaultInjector.FaultInjectionException e) {
            assertEquals("kubectl not on PATH", e.getCause().getMessage());
        }
    }

    @Test
    public void enabledGate_defaultsFalse_andReadsProperty() {
        System.clearProperty(FaultInjector.ENABLED_PROPERTY);
        assertFalse("fault-injection mode must default OFF", FaultInjector.enabled());
        System.setProperty(FaultInjector.ENABLED_PROPERTY, "true");
        assertTrue(FaultInjector.enabled());
    }

    @Test
    public void faultTarget_rejectsBlankFields() {
        try {
            new FaultInjector.FaultTarget(" ", "p");
            fail("expected IllegalArgumentException for blank deployment");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            new FaultInjector.FaultTarget("d", "");
            fail("expected IllegalArgumentException for blank property");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
