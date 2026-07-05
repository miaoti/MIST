package io.mist.cli.g3;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins {@link IstioAmqpSeverInjector} against fake Exec + HealthProbe seams (no live cluster):
 * inject applies the manifest, force-closes the cached broker connection, and gates on /health
 * converging to err; clear deletes + gates on OK; a /health that never flips (or is unreadable)
 * makes inject throw rather than run a degenerate leg. The connection-close is the fix verified
 * live (an L4 policy apply alone does not break shipping's cached AMQP connection).
 */
public class IstioAmqpSeverInjectorTest {

    private static final class RecordingExec implements IstioAmqpSeverInjector.Exec {
        final List<List<String>> calls = new ArrayList<>();
        int exitCode = 0;

        @Override
        public IstioAmqpSeverInjector.ExecResult run(List<String> argv, long timeoutSeconds) {
            calls.add(new ArrayList<>(argv));
            return new IstioAmqpSeverInjector.ExecResult(exitCode, "ok");
        }
    }

    private static Path manifest() throws IOException {
        Path p = Files.createTempFile("sever", ".yaml");
        p.toFile().deleteOnExit();
        return p;
    }

    private static IstioAmqpSeverInjector injector(String brokerWorkload, RecordingExec exec,
            IstioAmqpSeverInjector.HealthProbe probe) throws IOException {
        // convergeTimeout 1s + poll 1ms keeps the non-convergence tests fast.
        return new IstioAmqpSeverInjector("kubectl", null, "sock-shop", manifest(),
                "http://localhost:8079/health", 1L, brokerWorkload, 1L, exec, probe);
    }

    @Test
    public void inject_applies_thenClosesCachedConnection_thenConvergesToErr() throws Exception {
        RecordingExec exec = new RecordingExec();
        injector("deploy/rabbitmq", exec, url -> Boolean.TRUE).inject(); // TRUE = severed

        assertEquals("apply then close = two exec calls", 2, exec.calls.size());
        assertTrue("first call applies the manifest", exec.calls.get(0).contains("apply"));

        List<String> close = exec.calls.get(1);
        assertTrue("second call is a kubectl exec", close.contains("exec"));
        assertTrue("targets the broker workload", close.contains("deploy/rabbitmq"));
        int dash = close.indexOf("--");
        int ns = close.indexOf("-n");
        assertTrue("-n must precede -- (else rabbitmqctl receives the namespace flag)",
                ns >= 0 && dash > ns);
        assertEquals("rabbitmqctl", close.get(dash + 1));
        assertTrue(close.contains("close_all_connections"));
        assertTrue(close.contains(IstioAmqpSeverInjector.CLOSE_REASON));
    }

    @Test
    public void inject_throwsWhenHealthNeverFlips() throws Exception {
        try {
            injector("deploy/rabbitmq", new RecordingExec(), url -> Boolean.FALSE).inject(); // stays OK
            fail("expected non-convergence to throw");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("did not converge"));
        }
    }

    @Test
    public void inject_probeIoFailure_neverConverges() throws Exception {
        try {
            injector("deploy/rabbitmq", new RecordingExec(), url -> null).inject(); // unreadable
            fail("expected an unreadable /health to never converge");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("did not converge"));
        }
    }

    @Test
    public void inject_emptyBrokerWorkload_skipsClose() throws Exception {
        RecordingExec exec = new RecordingExec();
        injector("", exec, url -> Boolean.TRUE).inject();
        assertEquals("apply only (no connection-close)", 1, exec.calls.size());
        assertTrue(exec.calls.get(0).contains("apply"));
    }

    @Test
    public void clear_deletesManifest_thenConvergesToOk() throws Exception {
        RecordingExec exec = new RecordingExec();
        injector("deploy/rabbitmq", exec, url -> Boolean.FALSE).clear(); // FALSE = OK

        assertEquals(1, exec.calls.size());
        assertTrue("clear deletes the manifest", exec.calls.get(0).contains("delete"));
    }

    @Test
    public void inject_kubectlNonZero_throwsIOException() throws Exception {
        RecordingExec exec = new RecordingExec();
        exec.exitCode = 1;
        try {
            injector("deploy/rabbitmq", exec, url -> Boolean.TRUE).inject();
            fail("expected a non-zero kubectl to throw");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("exited 1"));
        }
    }
}
