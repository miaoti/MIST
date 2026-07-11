package io.mist.cli.enable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The LEG-level kafka fault for {@link OtelCheckoutHeadToHead}: a SINGLE scale toggle between the
 * control and fault legs (review A-F8/B-F7, control-first single-toggle). Scaling the kafka
 * deployment to 0 makes checkout's async produce fail; because checkout swallows-and-logs the
 * delivery error and acks 200 anyway, the order is acknowledged-but-permanently-lost (the message
 * never exists, nothing to drain on restore).
 *
 * <p>Shells out to {@code kubectl} through the same injectable {@link SqlDurableReadback.CommandRunner}
 * the SQL read-back uses ({@link ProcessCommandRunner} in production); the {@code kubectlPrefix}
 * carries the Windows/WSL split (e.g. {@code [wsl, kubectl, --context, kind-mist, -n, otel-demo]},
 * review B-R2). Restoring the broker (scale back to 1) is done here; the rdkafka-wedge recovery
 * (rollout-restart checkout+accounting+fraud-detection after a kafka pod replacement, the measured
 * runbook) is the ORCHESTRATOR's job AFTER the verdict is computed &mdash; it does not affect the
 * already-collected records, and keeping it out of the harness keeps the fragile multi-rollout wait
 * out of the verdict path.
 */
public final class KafkaClusterOps {

    private final List<String> kubectlPrefix;
    private final SqlDurableReadback.CommandRunner runner;
    private final int cmdTimeoutMs;

    public KafkaClusterOps(List<String> kubectlPrefix, SqlDurableReadback.CommandRunner runner,
                           int cmdTimeoutMs) {
        if (kubectlPrefix == null || kubectlPrefix.isEmpty()) {
            throw new IllegalArgumentException("KafkaClusterOps needs a non-empty kubectl prefix");
        }
        if (runner == null) {
            throw new IllegalArgumentException("KafkaClusterOps needs a CommandRunner");
        }
        this.kubectlPrefix = new ArrayList<>(kubectlPrefix);
        this.runner = runner;
        this.cmdTimeoutMs = cmdTimeoutMs;
    }

    /** {@code kubectl scale deployment/kafka --replicas=<n>} (loud on non-zero exit). */
    public void scaleKafka(int replicas) throws Exception {
        exec(argv("scale", "deployment/kafka", "--replicas=" + replicas));
    }

    /**
     * Poll until no kafka pod remains, so the produce actually fails before the fault leg runs. A
     * scale-0 that has not finished terminating would let the first fault probe land &mdash; a
     * cross-leg contamination. Loud if the pod is still present after {@code maxWaitMs}.
     */
    public void waitKafkaGone(int maxWaitMs) throws Exception {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            SqlDurableReadback.CommandRunner.Result r =
                    exec(argv("get", "pods", "-l", "app.kubernetes.io/component=kafka", "--no-headers"));
            String out = r.stdout == null ? "" : r.stdout.trim();
            if (out.isEmpty() || out.toLowerCase().contains("no resources")) {
                return;
            }
            Thread.sleep(2000);
        }
        throw new IllegalStateException("kafka pod still present after " + maxWaitMs + "ms scale-0 wait");
    }

    private List<String> argv(String... tail) {
        List<String> argv = new ArrayList<>(kubectlPrefix);
        argv.addAll(Arrays.asList(tail));
        return argv;
    }

    private SqlDurableReadback.CommandRunner.Result exec(List<String> argv) throws Exception {
        SqlDurableReadback.CommandRunner.Result r = runner.run(argv, cmdTimeoutMs);
        if (r.exitCode != 0) {
            throw new IllegalStateException("cmd " + argv + " -> exit " + r.exitCode + ": "
                    + (r.stderr == null ? "" : r.stderr.trim()));
        }
        return r;
    }
}
