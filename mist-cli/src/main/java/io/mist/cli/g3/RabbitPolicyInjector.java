package io.mist.cli.g3;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Constructed-stratum fault: a {@code max-length:1 / overflow:reject-publish} policy on the
 * shipping-task queue via the RabbitMQ management API. With the queue already at depth &ge;1
 * (queue-master scaffolded to 0 so nothing drains), a new publish is rejected + lost, yet the
 * broker connection stays live so {@code /health} reads GREEN — the clean-win mechanism
 * (live-verified on 3.8.34). {@code reject-publish} needs RabbitMQ 3.7+.
 *
 * <p>{@link #inject} PUTs the policy then polls the queue detail until the policy is applied
 * (so the fault is provably live before the fault leg runs); {@link #clear} DELETEs it (a
 * DELETE of an absent policy returns 404, which is fine for start-clean hygiene) then polls
 * until no policy is applied.
 */
public final class RabbitPolicyInjector implements ShippingEnqueueHeadToHead.Fault {

    private static final String POLICY = "ship-drop";
    private static final int CONVERGE_POLLS = 60;
    private static final long CONVERGE_SLEEP_MS = 250;

    private final String base;
    private final String authHeader;
    private final String queue;

    public RabbitPolicyInjector(String base, String user, String pass, String queue) {
        this.base = base.trim().replaceAll("/+$", "");
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(
                (user + ":" + pass).getBytes(StandardCharsets.UTF_8));
        this.queue = queue;
    }

    @Override
    public void inject() throws IOException, InterruptedException {
        String body = "{\"pattern\":\"^" + queue + "$\",\"apply-to\":\"queues\","
                + "\"definition\":{\"max-length\":1,\"overflow\":\"reject-publish\"},\"priority\":10}";
        Resp put = send("PUT", policyUrl(), body);
        if (put.status / 100 != 2) {
            throw new IOException("reject-publish policy PUT failed: HTTP " + put.status + " " + put.body);
        }
        awaitPolicy(true);
    }

    @Override
    public void clear() throws IOException, InterruptedException {
        send("DELETE", policyUrl(), null); // idempotent (404 if absent)
        awaitPolicy(false);
    }

    /** Polls the queue detail until its applied policy matches the desired state, or times out. */
    private void awaitPolicy(boolean applied) throws IOException, InterruptedException {
        String queueUrl = base + "/api/queues/%2f/" + queue;
        for (int i = 0; i < CONVERGE_POLLS; i++) {
            Resp q = send("GET", queueUrl, null);
            if (q.status / 100 == 2) {
                boolean shown = q.body.contains("\"policy\":\"" + POLICY + "\"");
                if (shown == applied) {
                    return;
                }
            }
            Thread.sleep(CONVERGE_SLEEP_MS);
        }
        throw new IOException("reject-publish policy did not " + (applied ? "apply" : "clear")
                + " within " + (CONVERGE_POLLS * CONVERGE_SLEEP_MS) + "ms");
    }

    private String policyUrl() {
        return base + "/api/policies/%2f/" + POLICY; // %2f = the default vhost "/"
    }

    private Resp send(String method, String url, String body) throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setRequestMethod(method);
            c.setRequestProperty("Authorization", authHeader);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "application/json");
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            if (body != null) {
                c.setDoOutput(true);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = c.getResponseCode();
            InputStream in = status / 100 == 2 ? c.getInputStream() : c.getErrorStream();
            String respBody = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new Resp(status, respBody);
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private static final class Resp {
        final int status;
        final String body;

        Resp(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
