package io.mist.cli.g3;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Natural-stratum fault: a surgical shipping&#8617;broker sever on an UNMODIFIED SUT.
 * {@link #inject} applies a reviewed, committed Istio manifest (an L4 AuthorizationPolicy /
 * EnvoyFilter denying shipping&rarr;rabbitmq:5672 while the mgmt read-back on 15672 stays
 * live); {@link #clear} deletes it. The manifest is per-SUT deploy configuration, not
 * generated here. This makes {@code ShippingController.postShipping}'s
 * {@code convertAndSend("shipping-task",…)} throw → the swallow returns HTTP 201 + the
 * message is lost, and (because {@code getHealth} live-probes the broker on the same
 * connection factory) {@code /health} reads "err" — the diagnosis-gap stratum.
 *
 * <p><b>The L4 policy blocks only NEW connections</b>, so an apply alone does not break shipping's
 * already-CACHED broker connection (verified live: {@code /health} stays "OK" on apply). {@link
 * #inject} therefore force-closes the cached connection inside the broker
 * ({@code rabbitmqctl close_all_connections}) right after applying, so shipping's next publish and
 * {@code /health} probe RECONNECT into the block — verified live: {@code /health} then flips to
 * "err" within ~1s with no pod bounce. (Only shipping publishes here — queue-master is scaled to 0
 * — so closing all connections closes exactly shipping's. The mgmt read-back is a pod port-forward
 * that bypasses the sidecar, so it stays reachable regardless.)
 *
 * <p><b>Convergence is probed, not assumed</b> (mirrors {@link io.mist.cli.fault} 's reviewed
 * IstioRouteFaultInjector): mesh-policy propagation is eventually consistent, and a fault leg
 * that starts before the sever is live silently degrades into a second control leg. The sever
 * flips no HTTP STATUS (POST still 201, /health still 200) — the acked-but-lost nature is the
 * whole point — so the convergence signal is the {@code /health} BODY: the
 * {@code shipping-rabbitmq} entry reads "err" once severed, "OK" once restored. A probe I/O
 * failure never satisfies either direction (a dead gateway must not pass for a converged
 * clear). If {@code /health} never flips within the budget, inject() throws rather than run a
 * degenerate leg.
 */
public final class IstioAmqpSeverInjector implements ShippingEnqueueHeadToHead.Fault {

    static final String REQUEST_TIMEOUT = "30s";
    static final long PROCESS_GRACE_SECONDS = 60;
    static final String CLOSE_REASON = "mist-natural-sever";

    /** Runs a process to completion within a timeout. Test seam. */
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

    /** Reads the shipping-rabbitmq health: TRUE=err (severed), FALSE=OK, null=unreadable. Test seam. */
    interface HealthProbe {
        Boolean brokerErr(String healthUrl);
    }

    private final String kubectl;
    private final String kubeconfig;
    private final String namespace;
    private final Path manifest;
    private final String healthUrl;
    private final long convergeTimeoutSeconds;
    private final String brokerWorkload;
    private final long probePollMs;
    private final Exec exec;
    private final HealthProbe probe;

    public IstioAmqpSeverInjector(String kubectl, String kubeconfig, String namespace, Path manifest,
                                  String healthUrl, long convergeTimeoutSeconds, String brokerWorkload) {
        this(kubectl, kubeconfig, namespace, manifest, healthUrl, convergeTimeoutSeconds, brokerWorkload,
                500, IstioAmqpSeverInjector::runProcess, IstioAmqpSeverInjector::readHealth);
    }

    IstioAmqpSeverInjector(String kubectl, String kubeconfig, String namespace, Path manifest,
                           String healthUrl, long convergeTimeoutSeconds, String brokerWorkload,
                           long probePollMs, Exec exec, HealthProbe probe) {
        if (namespace == null || namespace.trim().isEmpty()) {
            throw new IllegalArgumentException("IstioAmqpSeverInjector needs a non-empty namespace");
        }
        if (manifest == null || !Files.isRegularFile(manifest)) {
            throw new IllegalArgumentException("IstioAmqpSeverInjector needs an existing manifest file"
                    + " (got: " + manifest + ")");
        }
        if (healthUrl == null || !healthUrl.matches("https?://.+")) {
            throw new IllegalArgumentException("IstioAmqpSeverInjector needs an absolute http(s)"
                    + " /health URL (got: " + healthUrl + ")");
        }
        if (convergeTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("convergeTimeoutSeconds must be positive");
        }
        this.kubectl = kubectl == null || kubectl.trim().isEmpty() ? "kubectl" : kubectl.trim();
        this.kubeconfig = kubeconfig;
        this.namespace = namespace.trim();
        this.manifest = manifest;
        this.healthUrl = healthUrl;
        this.convergeTimeoutSeconds = convergeTimeoutSeconds;
        this.brokerWorkload = brokerWorkload == null ? "" : brokerWorkload.trim();
        this.probePollMs = probePollMs;
        this.exec = exec;
        this.probe = probe;
    }

    @Override
    public void inject() throws IOException, InterruptedException {
        kubectl("apply", "-f", manifest.toString(), "--request-timeout=" + REQUEST_TIMEOUT);
        // The L4 policy blocks only NEW connections; shipping's CACHED broker connection survives an
        // apply (verified live — /health does not flip on apply alone). Force it closed inside the
        // broker so shipping's next publish + /health probe reconnect INTO the block. Skipped only
        // when no broker workload is configured, in which case awaitHealth is the loud backstop: it
        // will not converge on the manifest alone, so inject() throws rather than run a degenerate leg.
        if (!brokerWorkload.isEmpty()) {
            brokerExec("rabbitmqctl", "close_all_connections", CLOSE_REASON);
        }
        awaitHealth(true, "INJECT");
    }

    @Override
    public void clear() throws IOException, InterruptedException {
        kubectl("delete", "-f", manifest.toString(), "--ignore-not-found=true",
                "--request-timeout=" + REQUEST_TIMEOUT);
        awaitHealth(false, "CLEAR");
    }

    /** Polls /health until shipping-rabbitmq reads err (inject) / OK (clear); I/O never converges. */
    private void awaitHealth(boolean expectErr, String operation) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(convergeTimeoutSeconds);
        Boolean last = null;
        while (System.nanoTime() - deadline < 0) {
            last = probe.brokerErr(healthUrl);
            if (last != null && last == expectErr) {
                return;
            }
            Thread.sleep(probePollMs);
        }
        String observed = last == null ? "unreadable /health" : "shipping-rabbitmq=" + (last ? "err" : "OK");
        throw new IllegalStateException(operation + " did not converge within " + convergeTimeoutSeconds
                + "s: " + healthUrl + " last read " + observed + ", wanted shipping-rabbitmq="
                + (expectErr ? "err" : "OK"));
    }

    private void kubectl(String... args) throws IOException, InterruptedException {
        List<String> argv = new ArrayList<>();
        argv.add(kubectl);
        if (kubeconfig != null && !kubeconfig.trim().isEmpty()) {
            argv.add("--kubeconfig=" + kubeconfig.trim());
        }
        argv.addAll(Arrays.asList(args));
        argv.add("-n");
        argv.add(namespace);
        ExecResult result = exec.run(argv, convergeTimeoutSeconds + PROCESS_GRACE_SECONDS);
        if (result.exitCode != 0) {
            throw new IOException("kubectl exited " + result.exitCode + ": " + String.join(" ", argv)
                    + "\n" + result.output);
        }
    }

    /**
     * {@code kubectl exec <brokerWorkload> -n <ns> -- <cmd...>} — used to close the cached broker
     * connection. The {@code -n} MUST precede {@code --}, so this cannot reuse {@link #kubectl}
     * (which appends {@code -n <ns>} last, which for an exec would be handed to the inner command).
     */
    private void brokerExec(String... cmd) throws IOException, InterruptedException {
        List<String> argv = new ArrayList<>();
        argv.add(kubectl);
        if (kubeconfig != null && !kubeconfig.trim().isEmpty()) {
            argv.add("--kubeconfig=" + kubeconfig.trim());
        }
        argv.add("exec");
        argv.add(brokerWorkload);
        argv.add("-n");
        argv.add(namespace);
        argv.add("--");
        argv.addAll(Arrays.asList(cmd));
        ExecResult result = exec.run(argv, convergeTimeoutSeconds + PROCESS_GRACE_SECONDS);
        if (result.exitCode != 0) {
            throw new IOException("kubectl exec exited " + result.exitCode + ": "
                    + String.join(" ", argv) + "\n" + result.output);
        }
    }

    /** Production process runner: merges stderr, bounds the wait, returns exit code + output. */
    static ExecResult runProcess(List<String> argv, long timeoutSeconds)
            throws IOException, InterruptedException {
        Process p = new ProcessBuilder(argv).redirectErrorStream(true).start();
        // Review C-MAJOR-2 (mirrors the reviewed SutFlagFaultInjector.runProcess): kubectl
        // output here is a few lines, far below pipe capacity, so it is safe to wait FIRST and
        // drain afterwards — the bounded wait is the hard upper bound that keeps a wedged
        // kubectl (stuck exec-credential plugin, black-holed API server) from hanging the run.
        // Draining first is an UNBOUNDED blocking read that would never reach the timeout.
        if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("process timed out after " + timeoutSeconds + "s: "
                    + String.join(" ", argv));
        }
        StringBuilder out = new StringBuilder();
        try (InputStream in = p.getInputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
        }
        return new ExecResult(p.exitValue(), out.toString());
    }

    /** Production probe: GET /health, return whether shipping-rabbitmq reads err (null = unreadable). */
    static Boolean readHealth(String healthUrl) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) URI.create(healthUrl).toURL().openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            int status = c.getResponseCode();
            if (status / 100 != 2) {
                // Review C-M-2: this assumes shipping serves /health — INCLUDING the broker-"err"
                // state — as HTTP 200 (true for this image; getHealth always ResponseEntity.ok).
                // A non-2xx or unreachable /health is treated as UNREADABLE (never converged),
                // never as "severed", so a health-endpoint outage cannot false-signal convergence.
                return null;
            }
            InputStream in = c.getInputStream();
            String body = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // Find the shipping-rabbitmq entry and read its status.
            Matcher m = Pattern.compile(
                    "\\{[^{}]*\"service\"\\s*:\\s*\"shipping-rabbitmq\"[^{}]*\\}").matcher(body);
            if (!m.find()) {
                return null;
            }
            Matcher s = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]*)\"").matcher(m.group());
            if (!s.find()) {
                return null;
            }
            return !"OK".equalsIgnoreCase(s.group(1));
        } catch (IOException e) {
            return null;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }
}
