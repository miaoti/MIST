package io.mist.cli.g3;

import org.json.JSONObject;

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
    private static final int MAX_LEN = 1;
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
                + "\"definition\":{\"max-length\":" + MAX_LEN
                + ",\"overflow\":\"reject-publish\"},\"priority\":10}";
        Resp put = send("PUT", policyUrl(), body);
        if (put.status / 100 != 2) {
            throw new IOException("reject-publish policy PUT failed: HTTP " + put.status + " " + put.body);
        }
        awaitPolicy(true);
        requireRejectWillBite();
    }

    /**
     * Review C-MAJOR-3: "policy applied" is NOT "the next publish is rejected". max-length +
     * reject-publish rejects a publish only when the queue is already AT the limit AND nothing
     * drains it between the control and fault legs. Assert both preconditions here (the control
     * leg's message must be resident with no consumer) so a forgotten queue-master scale-down
     * fails LOUD instead of silently degrading the clean-win cell to NO_FIRE.
     */
    private void requireRejectWillBite() throws IOException {
        Resp q = send("GET", base + "/api/queues/%2f/" + queue, null);
        if (q.status / 100 != 2) {
            throw new IOException("cannot read " + queue + " detail to verify reject preconditions:"
                    + " HTTP " + q.status);
        }
        int consumers = intField(q.body, "consumers");
        int messages = intField(q.body, "messages");
        if (consumers != 0) {
            throw new IOException("reject-publish will not bite: " + queue + " has " + consumers
                    + " consumer(s) — scale queue-master to 0 so depth is monotonic (RUNBOOK)");
        }
        if (messages < MAX_LEN) {
            throw new IOException("reject-publish will not bite: " + queue + " depth " + messages
                    + " < max-length " + MAX_LEN + " — the control leg's message must be resident"
                    + " (no drainer) before the fault leg");
        }
    }

    /** Reads a top-level integer field from the queue-detail JSON object (-1 if absent/unparseable). */
    private static int intField(String body, String field) {
        try {
            return new JSONObject(body).optInt(field, -1);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    @Override
    public void clear() throws IOException, InterruptedException {
        // Review C-M-5: surface a genuine DELETE failure here (a 404 = already-absent is the
        // idempotent no-op we want) instead of letting it masquerade as an awaitPolicy timeout.
        Resp del = send("DELETE", policyUrl(), null);
        if (del.status / 100 != 2 && del.status != 404) {
            throw new IOException("reject-publish policy DELETE failed: HTTP " + del.status + " " + del.body);
        }
        awaitPolicy(false);
    }

    /** Polls the queue detail until its applied policy matches the desired state, or times out. */
    private void awaitPolicy(boolean applied) throws IOException, InterruptedException {
        String queueUrl = base + "/api/queues/%2f/" + queue;
        int lastStatus = -1;
        for (int i = 0; i < CONVERGE_POLLS; i++) {
            Resp q = send("GET", queueUrl, null);
            lastStatus = q.status;
            if (q.status / 100 == 2) {
                boolean shown = q.body.contains("\"policy\":\"" + POLICY + "\"");
                if (shown == applied) {
                    return;
                }
            }
            Thread.sleep(CONVERGE_SLEEP_MS);
        }
        // Review C-M-6: report the last observed read so a persistent queue-GET non-2xx (which
        // reads identically to a genuinely stuck policy) is distinguishable in the failure.
        throw new IOException("reject-publish policy did not " + (applied ? "apply" : "clear")
                + " within " + (CONVERGE_POLLS * CONVERGE_SLEEP_MS) + "ms (last queue-detail read: HTTP "
                + lastStatus + ")");
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
