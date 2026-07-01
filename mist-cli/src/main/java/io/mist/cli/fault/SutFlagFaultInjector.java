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
 * editing {@code JAVA_TOOL_OPTIONS} on the target deployment and waiting for
 * the rollout to converge. The {@code -D} system property is load-bearing —
 * env relaxed binding silently fails on TrainTicket's Spring Cloud + nacos
 * bootstrap (gate1-smoke-result.md), so the flag must reach the JVM as a
 * system property.
 *
 * <p><b>Append/strip semantics, never overwrite:</b> the traced TrainTicket
 * topology (run22's {@code --with-tracing} = sw_deploy variant) loads the
 * OpenTelemetry javaagent through this same variable
 * ({@code -javaagent:/otel/opentelemetry-javaagent.jar}), so {@code inject}
 * first reads the current value ({@code kubectl set env --list}) and appends
 * the flag token, and {@code clear} strips exactly our token and restores
 * the remainder (unsetting only when nothing else was there). Clearing a
 * deployment that does not carry the token is a no-op without a rollout,
 * which also makes the batch hygiene pass cheap.
 *
 * <p>Each real toggle costs a rollout (tens of seconds on minikube), so
 * callers batch fault runs rather than toggling per request. The kubectl
 * context is always passed explicitly when configured — the host may also
 * run an unrelated cluster (kind/Istio), and inheriting the current context
 * could target it. Multi-container pods: the first JAVA_TOOL_OPTIONS
 * reported by {@code --list} wins (TrainTicket app containers are the only
 * JVM containers).
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
    private final long postChangeSettleSeconds;
    private final Exec exec;

    /**
     * @param kubectlContext          kubectl context to target, or {@code null}
     *                                to use the current one (discouraged outside
     *                                tests — see class doc)
     * @param namespace               namespace of the SUT deployments
     * @param rolloutTimeoutSeconds   upper bound for each rollout to converge
     * @param postChangeSettleSeconds extra wait after a converged rollout —
     *                                nacos/ribbon client caches can serve stale
     *                                instances briefly after the pod swap, and a
     *                                5xx on the fault run's first request would
     *                                burn the leg as not-acked (review F6)
     */
    public SutFlagFaultInjector(String kubectlContext, String namespace, long rolloutTimeoutSeconds,
                                long postChangeSettleSeconds) {
        this(kubectlContext, namespace, rolloutTimeoutSeconds, postChangeSettleSeconds,
                SutFlagFaultInjector::runProcess);
    }

    SutFlagFaultInjector(String kubectlContext, String namespace, long rolloutTimeoutSeconds, Exec exec) {
        this(kubectlContext, namespace, rolloutTimeoutSeconds, 0, exec);
    }

    SutFlagFaultInjector(String kubectlContext, String namespace, long rolloutTimeoutSeconds,
                         long postChangeSettleSeconds, Exec exec) {
        if (namespace == null || namespace.trim().isEmpty()) {
            throw new IllegalArgumentException("SutFlagFaultInjector needs a non-empty namespace");
        }
        if (rolloutTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("rolloutTimeoutSeconds must be positive");
        }
        this.kubectlContext = kubectlContext;
        this.namespace = namespace.trim();
        this.rolloutTimeoutSeconds = rolloutTimeoutSeconds;
        this.postChangeSettleSeconds = postChangeSettleSeconds;
        this.exec = exec;
    }

    /** Post-rollout settle for stale discovery caches (no-op when 0). */
    private void settle() {
        if (postChangeSettleSeconds <= 0) {
            return;
        }
        try {
            Thread.sleep(postChangeSettleSeconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FaultInjectionException("interrupted during post-rollout settle", e);
        }
    }

    @Override
    public void inject(FaultTarget target) {
        String token = flagToken(target);
        List<String> parts = currentJavaToolOptions(target);
        if (parts.contains(token)) {
            logger.info("FaultInjector: INJECT {} — flag already present, nothing to do", target);
            return;
        }
        parts.add(token);
        logger.info("FaultInjector: INJECT {} — appending to JAVA_TOOL_OPTIONS and waiting for rollout", target);
        kubectl("set", "env", "deployment/" + target.deployment,
                "JAVA_TOOL_OPTIONS=" + String.join(" ", parts),
                "--request-timeout=" + SET_ENV_REQUEST_TIMEOUT);
        awaitRollout(target);
        settle();
        logger.info("FaultInjector: INJECT {} converged", target);
    }

    @Override
    public void clear(FaultTarget target) {
        String token = flagToken(target);
        List<String> parts = currentJavaToolOptions(target);
        if (!parts.remove(token)) {
            logger.info("FaultInjector: CLEAR {} — flag not present, nothing to do", target);
            return;
        }
        logger.info("FaultInjector: CLEAR {} — stripping from JAVA_TOOL_OPTIONS and waiting for rollout", target);
        kubectl("set", "env", "deployment/" + target.deployment,
                parts.isEmpty() ? "JAVA_TOOL_OPTIONS-" : "JAVA_TOOL_OPTIONS=" + String.join(" ", parts),
                "--request-timeout=" + SET_ENV_REQUEST_TIMEOUT);
        awaitRollout(target);
        settle();
        logger.info("FaultInjector: CLEAR {} converged", target);
    }

    static String flagToken(FaultTarget target) {
        return "-D" + target.property + "=true";
    }

    /** Whitespace-split current JAVA_TOOL_OPTIONS of the deployment (empty when unset). */
    private List<String> currentJavaToolOptions(FaultTarget target) {
        ExecResult result = kubectlCapture("set", "env", "deployment/" + target.deployment,
                "--list", "--request-timeout=" + SET_ENV_REQUEST_TIMEOUT);
        for (String line : result.output.split("\\R")) {
            if (line.startsWith("JAVA_TOOL_OPTIONS=")) {
                String value = line.substring("JAVA_TOOL_OPTIONS=".length()).trim();
                if (value.isEmpty()) {
                    return new ArrayList<>();
                }
                return new ArrayList<>(Arrays.asList(value.split("\\s+")));
            }
        }
        return new ArrayList<>();
    }

    private void awaitRollout(FaultTarget target) {
        // No --request-timeout here: rollout status holds a long watch request
        // that a per-request timeout would abort; kubectl's own --timeout
        // bounds the wait and the process-level grace bounds everything else.
        kubectl("rollout", "status", "deployment/" + target.deployment,
                "--timeout=" + rolloutTimeoutSeconds + "s");
    }

    private void kubectl(String... args) {
        kubectlCapture(args);
    }

    private ExecResult kubectlCapture(String... args) {
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
        return result;
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
