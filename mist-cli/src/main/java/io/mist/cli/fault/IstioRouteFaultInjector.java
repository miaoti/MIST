package io.mist.cli.fault;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * G3 {@link FaultInjector} backend for route-scoped network faults on an
 * UNMODIFIED SUT: {@code inject} applies a reviewed, committed Istio
 * VirtualService manifest (e.g. {@code fault.abort} on exactly the
 * inside-payment {@code /drawback} URI prefix — the read-back routes stay
 * live), {@code clear} deletes it. The manifest is per-SUT configuration,
 * not generated here.
 *
 * <p><b>Convergence is probed, not assumed.</b> VirtualService propagation
 * through istiod to the sidecars is eventually-consistent with no bound the
 * injector could await declaratively, and the {@link FaultInjector} contract
 * requires returning only once the SUT converged (a fault leg that starts
 * before the abort is live silently degrades into a second control leg).
 * So both operations poll a caller-supplied probe URL until it observes /
 * stops observing the abort status. The probe path must be non-mutating:
 * for the TT drawback route an incomplete path under the aborted prefix
 * answers with the abort status from Envoy when the fault is live and the
 * application's own 404/405 when it is not — no write ever reaches the SUT.
 *
 * <p>The {@link FaultTarget} argument identifies the faulted dependency for
 * logging/reports; the manifest supplied at construction is the operative
 * coordinate (one injector instance = one fault shape, matching the
 * one-injector-per-run executor wiring).
 */
public final class IstioRouteFaultInjector implements FaultInjector {

    private static final Logger logger = LogManager.getLogger(IstioRouteFaultInjector.class);

    /** Bounds a stuck kubectl apply/delete (server-side request timeout). */
    static final String REQUEST_TIMEOUT = "30s";
    /** Extra process-level grace on top of the converge budget. */
    static final long PROCESS_GRACE_SECONDS = 60;

    private final String kubectlContext;
    private final String namespace;
    private final Path manifest;
    private final String probeUrl;
    private final int abortStatus;
    private final long convergeTimeoutSeconds;
    private final long probePollMs;
    private final SutFlagFaultInjector.Exec exec;
    private final Probe probe;

    /** Test seam: one non-mutating GET, returns the HTTP status (-1 = I/O failure). */
    interface Probe {
        int status(String url);
    }

    public IstioRouteFaultInjector(String kubectlContext, String namespace, Path manifest,
                                   String probeUrl, int abortStatus, long convergeTimeoutSeconds) {
        this(kubectlContext, namespace, manifest, probeUrl, abortStatus, convergeTimeoutSeconds,
                500, SutFlagFaultInjector::runProcess, IstioRouteFaultInjector::httpProbe);
    }

    IstioRouteFaultInjector(String kubectlContext, String namespace, Path manifest,
                            String probeUrl, int abortStatus, long convergeTimeoutSeconds,
                            long probePollMs, SutFlagFaultInjector.Exec exec, Probe probe) {
        if (namespace == null || namespace.trim().isEmpty()) {
            throw new IllegalArgumentException("IstioRouteFaultInjector needs a non-empty namespace");
        }
        if (manifest == null || !Files.isRegularFile(manifest)) {
            throw new IllegalArgumentException("IstioRouteFaultInjector needs an existing manifest file"
                    + " (got: " + manifest + ")");
        }
        if (probeUrl == null || !probeUrl.matches("https?://.+")) {
            throw new IllegalArgumentException("IstioRouteFaultInjector needs an absolute http(s)"
                    + " probe URL (got: " + probeUrl + ")");
        }
        if (abortStatus < 200 || abortStatus > 599) {
            throw new IllegalArgumentException("abortStatus must be an HTTP status (got: "
                    + abortStatus + ")");
        }
        if (convergeTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("convergeTimeoutSeconds must be positive");
        }
        this.kubectlContext = kubectlContext;
        this.namespace = namespace.trim();
        this.manifest = manifest;
        this.probeUrl = probeUrl;
        this.abortStatus = abortStatus;
        this.convergeTimeoutSeconds = convergeTimeoutSeconds;
        this.probePollMs = probePollMs;
        this.exec = exec;
        this.probe = probe;
    }

    @Override
    public void inject(FaultTarget target) {
        logger.info("FaultInjector: INJECT {} — applying {} and probing for HTTP {}",
                target, manifest, abortStatus);
        kubectl("apply", "-f", manifest.toString(), "--request-timeout=" + REQUEST_TIMEOUT);
        awaitProbe(true, "INJECT " + target);
        logger.info("FaultInjector: INJECT {} converged (route aborts)", target);
    }

    @Override
    public void clear(FaultTarget target) {
        logger.info("FaultInjector: CLEAR {} — deleting {} and probing for the abort to stop",
                target, manifest);
        kubectl("delete", "-f", manifest.toString(), "--ignore-not-found=true",
                "--request-timeout=" + REQUEST_TIMEOUT);
        awaitProbe(false, "CLEAR " + target);
        logger.info("FaultInjector: CLEAR {} converged (route restored)", target);
    }

    /**
     * Polls the probe until the abort status is (inject) / is no longer
     * (clear) observed. I/O failures never satisfy either direction — a dead
     * gateway must not pass for a converged clear.
     */
    private void awaitProbe(boolean expectAbort, String operation) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(convergeTimeoutSeconds);
        int last = Integer.MIN_VALUE;
        while (System.nanoTime() < deadline) {
            last = probe.status(probeUrl);
            if (last != -1 && (last == abortStatus) == expectAbort) {
                return;
            }
            try {
                Thread.sleep(probePollMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FaultInjectionException("interrupted while probing after " + operation, e);
            }
        }
        throw new FaultInjectionException(operation + " did not converge within "
                + convergeTimeoutSeconds + "s: probe " + probeUrl + " last returned "
                + (last == Integer.MIN_VALUE ? "nothing" : String.valueOf(last))
                + ", wanted " + (expectAbort ? "" : "anything but ") + abortStatus);
    }

    private void kubectl(String... args) {
        List<String> argv = new ArrayList<>();
        argv.add("kubectl");
        if (kubectlContext != null && !kubectlContext.trim().isEmpty()) {
            argv.add("--context=" + kubectlContext.trim());
        }
        argv.addAll(Arrays.asList(args));
        argv.add("-n");
        argv.add(namespace);

        SutFlagFaultInjector.ExecResult result;
        try {
            result = exec.run(argv, convergeTimeoutSeconds + PROCESS_GRACE_SECONDS);
        } catch (IOException e) {
            throw new FaultInjectionException("kubectl failed to start: " + String.join(" ", argv), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FaultInjectionException("interrupted while running: " + String.join(" ", argv), e);
        }
        if (result.exitCode != 0) {
            throw new FaultInjectionException("kubectl exited " + result.exitCode + ": "
                    + String.join(" ", argv) + "\n" + result.output);
        }
    }

    /** Production probe: one plain GET, never throws (I/O failure = -1). */
    private static int httpProbe(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            drainQuietly(connection);
            return status;
        } catch (IOException e) {
            return -1;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Best-effort body drain so keep-alive connections can be reused. */
    private static void drainQuietly(HttpURLConnection connection) {
        try (InputStream in = connection.getResponseCode() / 100 == 2
                ? connection.getInputStream() : connection.getErrorStream()) {
            if (in == null) {
                return;
            }
            byte[] buffer = new byte[1024];
            while (in.read(buffer) != -1) {
                // discard
            }
        } catch (IOException ignored) {
            // the status code is already captured
        }
    }
}
