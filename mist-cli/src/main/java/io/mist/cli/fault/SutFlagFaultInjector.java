package io.mist.cli.fault;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Gate-1 {@link FaultInjector} backend: toggles a SUT-side fault flag by
 * setting {@code JAVA_TOOL_OPTIONS=-D<property>=true} on the target
 * deployment and waiting for the rollout to converge. The {@code -D} system
 * property is load-bearing — env relaxed binding silently fails on
 * TrainTicket's Spring Cloud + nacos bootstrap (gate1-smoke-result.md), so
 * the flag must reach the JVM as a system property.
 *
 * <p>{@code inject} = {@code kubectl set env deployment/<d>
 * JAVA_TOOL_OPTIONS=-D<p>=true} + {@code rollout status};
 * {@code clear} = {@code kubectl set env deployment/<d> JAVA_TOOL_OPTIONS-}
 * + {@code rollout status}. Set/unset semantics assume the target deployment
 * does not otherwise use {@code JAVA_TOOL_OPTIONS} — verified true for every
 * TrainTicket deployment in the deployed manifest (deploy.yaml has zero
 * occurrences; only the unused SkyWalking sample sets it).
 *
 * <p>Each toggle costs a rollout (tens of seconds on minikube), so callers
 * batch fault runs rather than toggling per request. The kubectl context is
 * always passed explicitly when configured — the host may also have an
 * unrelated cluster (kind/Istio), and inheriting the current context could
 * target it.
 */
public final class SutFlagFaultInjector implements FaultInjector {

    private static final Logger logger = LogManager.getLogger(SutFlagFaultInjector.class);

    /** Bounds a stuck `kubectl set env` call (server-side request timeout). */
    static final String SET_ENV_REQUEST_TIMEOUT = "30s";
    /** Extra process-level grace on top of kubectl's own rollout timeout. */
    static final long PROCESS_GRACE_SECONDS = 60;

    /** Test seam: runs an argv, returns exit code + combined output. */
    interface Exec {
        ExecResult run(List<String> argv, long timeoutSeconds) throws IOException, InterruptedException;
    }

    static final class ExecResult {
        final int exitCode;
        final String output;

        ExecResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private final String kubectlContext;
    private final String namespace;
    private final long rolloutTimeoutSeconds;
    private final Exec exec;

    /**
     * @param kubectlContext        kubectl context to target, or {@code null}
     *                              to use the current one (discouraged outside
     *                              tests — see class doc)
     * @param namespace             namespace of the SUT deployments
     * @param rolloutTimeoutSeconds upper bound for each rollout to converge
     */
    public SutFlagFaultInjector(String kubectlContext, String namespace, long rolloutTimeoutSeconds) {
        this(kubectlContext, namespace, rolloutTimeoutSeconds, SutFlagFaultInjector::runProcess);
    }

    SutFlagFaultInjector(String kubectlContext, String namespace, long rolloutTimeoutSeconds, Exec exec) {
        if (namespace == null || namespace.trim().isEmpty()) {
            throw new IllegalArgumentException("SutFlagFaultInjector needs a non-empty namespace");
        }
        if (rolloutTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("rolloutTimeoutSeconds must be positive");
        }
        this.kubectlContext = kubectlContext;
        this.namespace = namespace.trim();
        this.rolloutTimeoutSeconds = rolloutTimeoutSeconds;
        this.exec = exec;
    }

    @Override
    public void inject(FaultTarget target) {
        logger.info("FaultInjector: INJECT {} — setting JAVA_TOOL_OPTIONS and waiting for rollout", target);
        kubectl("set", "env", "deployment/" + target.deployment,
                "JAVA_TOOL_OPTIONS=-D" + target.property + "=true",
                "--request-timeout=" + SET_ENV_REQUEST_TIMEOUT);
        awaitRollout(target);
        logger.info("FaultInjector: INJECT {} converged", target);
    }

    @Override
    public void clear(FaultTarget target) {
        logger.info("FaultInjector: CLEAR {} — unsetting JAVA_TOOL_OPTIONS and waiting for rollout", target);
        kubectl("set", "env", "deployment/" + target.deployment,
                "JAVA_TOOL_OPTIONS-",
                "--request-timeout=" + SET_ENV_REQUEST_TIMEOUT);
        awaitRollout(target);
        logger.info("FaultInjector: CLEAR {} converged", target);
    }

    private void awaitRollout(FaultTarget target) {
        // No --request-timeout here: rollout status holds a long watch request
        // that a per-request timeout would abort; kubectl's own --timeout
        // bounds the wait and the process-level grace bounds everything else.
        kubectl("rollout", "status", "deployment/" + target.deployment,
                "--timeout=" + rolloutTimeoutSeconds + "s");
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

        ExecResult result;
        try {
            result = exec.run(argv, rolloutTimeoutSeconds + PROCESS_GRACE_SECONDS);
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

    /** Production {@link Exec}: ProcessBuilder with merged stderr. */
    private static ExecResult runProcess(List<String> argv, long timeoutSeconds)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(argv).redirectErrorStream(true).start();
        // kubectl output here is a few lines, far below pipe capacity, so it
        // is safe to wait first and drain afterwards; the wait is the hard
        // upper bound that keeps a wedged kubectl from hanging the run.
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("timed out after " + timeoutSeconds + "s");
        }
        return new ExecResult(process.exitValue(), drain(process.getInputStream()));
    }

    private static String drain(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
    }
}
