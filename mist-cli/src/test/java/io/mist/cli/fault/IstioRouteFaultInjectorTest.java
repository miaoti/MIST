package io.mist.cli.fault;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins the G3 Istio route-fault backend: kubectl apply/delete argv assembly
 * (context, namespace, ignore-not-found), probe-gated convergence in BOTH
 * directions (an unconverged apply or delete must throw, never return), I/O
 * probe failures satisfying neither direction, and constructor validation.
 */
public class IstioRouteFaultInjectorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String PROBE = "http://localhost:8080/api/v1/inside_pay_service/inside_payment/drawback";

    private Path manifest;
    private ScriptedExec exec;
    private ScriptedProbe probe;
    private FaultInjector.FaultTarget target;

    private static final class ScriptedExec implements SutFlagFaultInjector.Exec {
        final List<List<String>> calls = new ArrayList<>();
        int exitCode = 0;
        String output = "applied";

        @Override
        public SutFlagFaultInjector.ExecResult run(List<String> argv, long timeoutSeconds) {
            calls.add(argv);
            return new SutFlagFaultInjector.ExecResult(exitCode, output);
        }
    }

    private static final class ScriptedProbe implements IstioRouteFaultInjector.Probe {
        final Deque<Integer> statuses = new ArrayDeque<>();
        int calls;

        void script(int... values) {
            for (int v : values) {
                statuses.add(v);
            }
        }

        @Override
        public int status(String url) {
            calls++;
            // Exhausted script repeats its last value (a stable route).
            return statuses.size() > 1 ? statuses.poll()
                    : statuses.isEmpty() ? -1 : statuses.peek();
        }
    }

    @Before
    public void setUp() throws IOException {
        manifest = tmp.newFile("drawback-abort-vs.yaml").toPath();
        Files.write(manifest, "kind: VirtualService\n".getBytes(StandardCharsets.UTF_8));
        exec = new ScriptedExec();
        probe = new ScriptedProbe();
        target = new FaultInjector.FaultTarget("ts-inside-payment-service", "istio.route.abort.drawback");
    }

    private IstioRouteFaultInjector injector() {
        return new IstioRouteFaultInjector("kind-mist", "default", manifest, PROBE, 500, 5,
                1, exec, probe);
    }

    @Test
    public void inject_appliesManifestAndWaitsForAbort() {
        probe.script(404, 404, 500); // propagation lands on the third look
        injector().inject(target);

        assertEquals(1, exec.calls.size());
        List<String> argv = exec.calls.get(0);
        assertEquals("kubectl", argv.get(0));
        assertEquals("--context=kind-mist", argv.get(1));
        assertEquals("apply", argv.get(2));
        assertEquals("-f", argv.get(3));
        assertEquals(manifest.toString(), argv.get(4));
        assertTrue(argv.contains("--request-timeout=" + IstioRouteFaultInjector.REQUEST_TIMEOUT));
        assertEquals("-n", argv.get(argv.size() - 2));
        assertEquals("default", argv.get(argv.size() - 1));
        assertEquals(3, probe.calls);
    }

    @Test
    public void clear_deletesWithIgnoreNotFoundAndWaitsForRestore() {
        probe.script(500, 500, 404); // abort stops on the third look
        injector().clear(target);

        List<String> argv = exec.calls.get(0);
        assertEquals("delete", argv.get(2));
        assertTrue(argv.contains("--ignore-not-found=true"));
        assertEquals(3, probe.calls);
    }

    @Test
    public void inject_neverConverging_throwsInsteadOfReturning() {
        probe.script(404); // the abort never becomes visible
        try {
            new IstioRouteFaultInjector("kind-mist", "default", manifest, PROBE, 500, 1,
                    1, exec, probe).inject(target);
            fail("expected FaultInjectionException — a fault leg must not start unconverged");
        } catch (FaultInjector.FaultInjectionException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("did not converge"));
            assertTrue(e.getMessage(), e.getMessage().contains("404"));
        }
    }

    @Test
    public void clear_neverConverging_throws() {
        probe.script(500); // the abort never stops
        try {
            new IstioRouteFaultInjector("kind-mist", "default", manifest, PROBE, 500, 1,
                    1, exec, probe).clear(target);
            fail("expected FaultInjectionException — a wedged clear must be loud");
        } catch (FaultInjector.FaultInjectionException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("did not converge"));
        }
    }

    @Test
    public void probeIoFailure_satisfiesNeitherDirection() {
        // A dead gateway (-1) must not pass for "the abort stopped": the clear
        // keeps polling through the outage and succeeds only on a real 404.
        probe.script(-1, -1, 404);
        injector().clear(target);
        assertEquals(3, probe.calls);

        // ...and on inject, -1 must not pass for the abort either.
        exec = new ScriptedExec();
        probe = new ScriptedProbe();
        probe.script(-1, 500);
        injector().inject(target);
        assertEquals(2, probe.calls);
    }

    @Test
    public void kubectlFailure_throwsWithOutput() {
        exec.exitCode = 1;
        exec.output = "error validating data";
        probe.script(500);
        try {
            injector().inject(target);
            fail("expected FaultInjectionException");
        } catch (FaultInjector.FaultInjectionException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("exited 1"));
            assertTrue(e.getMessage(), e.getMessage().contains("error validating data"));
        }
    }

    @Test
    public void nullContext_omitsContextFlag() {
        probe.script(500);
        new IstioRouteFaultInjector(null, "default", manifest, PROBE, 500, 5, 1, exec, probe)
                .inject(target);
        assertEquals("apply", exec.calls.get(0).get(1));
    }

    @Test
    public void constructorValidation_isLoud() {
        try {
            new IstioRouteFaultInjector("c", "default", tmp.getRoot().toPath().resolve("missing.yaml"),
                    PROBE, 500, 5, 1, exec, probe);
            fail("expected IllegalArgumentException for a missing manifest");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("manifest"));
        }
        try {
            new IstioRouteFaultInjector("c", "default", manifest, "not-a-url", 500, 5, 1, exec, probe);
            fail("expected IllegalArgumentException for a relative probe URL");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("probe URL"));
        }
        try {
            new IstioRouteFaultInjector("c", "default", manifest, PROBE, 42, 5, 1, exec, probe);
            fail("expected IllegalArgumentException for a non-HTTP abort status");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("abortStatus"));
        }
        try {
            new IstioRouteFaultInjector("c", "default", manifest, PROBE, 500, 0, 1, exec, probe);
            fail("expected IllegalArgumentException for a zero converge timeout");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("convergeTimeoutSeconds"));
        }
        try {
            new IstioRouteFaultInjector("c", " ", manifest, PROBE, 500, 5, 1, exec, probe);
            fail("expected IllegalArgumentException for a blank namespace");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("namespace"));
        }
    }
}
