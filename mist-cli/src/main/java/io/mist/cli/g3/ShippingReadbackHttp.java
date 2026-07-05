package io.mist.cli.g3;

import io.mist.cli.fault.DataIntegrityRuntime;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Read-back {@link DataIntegrityRuntime.Http} for the Sock Shop shipping enqueue
 * head-to-head. The write (POST /shipping) is fire-and-forget into a RabbitMQ
 * queue, so the write's durable effect is observed on the BROKER management API —
 * a DIFFERENT host than the SUT, with basic auth (guest is loopback-only on 3.7+,
 * so a dedicated read-back user is used). Installed via
 * {@link DataIntegrityRuntime#installHttpOverride} so the reviewed value-delta
 * oracle reads {@code GET /api/queues/%2f} (match_field=name -> shipping-task,
 * value_field=messages) unchanged.
 *
 * <p>{@code getSut(path)} prepends the broker base + basic auth. {@code getAbsolute}
 * is unused here (no Jaeger trace gate — the read-back is timeout-gated) but is
 * implemented for completeness. A transport failure is reported as a non-2xx
 * response so the oracle's decisive-read gate treats it as UNUSABLE (never as
 * evidence of presence or absence), matching RestAssuredHttp's contract.
 *
 * <p>SCOPE (review B-m3): this override routes EVERY {@code getSut} at the broker, so it is
 * valid only for a triple whose ONLY {@code getSut} is the value-delta read-back path — a
 * SUPPLIED triple with no station-pair / freshening SUT lookups. Do not reuse it with a triple
 * that issues other SUT reads (they would be misrouted to the broker mgmt API).
 */
public final class ShippingReadbackHttp implements DataIntegrityRuntime.Http {

    private final String base;        // e.g. http://localhost:15673 (rabbitmq mgmt, port-forwarded)
    private final String authHeader;  // Basic base64(user:pass)
    private final int timeoutMs;

    public ShippingReadbackHttp(String base, String user, String pass, int timeoutMs) {
        if (base == null || base.trim().isEmpty()) {
            throw new IllegalArgumentException("ShippingReadbackHttp needs a broker base URL");
        }
        if (user == null || pass == null) {
            throw new IllegalArgumentException("ShippingReadbackHttp needs read-back credentials");
        }
        this.base = base.trim().replaceAll("/+$", "");
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(
                (user + ":" + pass).getBytes(StandardCharsets.UTF_8));
        this.timeoutMs = timeoutMs;
    }

    @Override
    public DataIntegrityRuntime.HttpResponse getSut(String path) {
        String p = path.startsWith("/") ? path : "/" + path;
        return get(base + p);
    }

    @Override
    public DataIntegrityRuntime.HttpResponse getAbsolute(String url) {
        return get(url);
    }

    private DataIntegrityRuntime.HttpResponse get(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("Authorization", authHeader);
            c.setRequestProperty("Accept", "application/json");
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            int status = c.getResponseCode();
            InputStream in = status / 100 == 2 ? c.getInputStream() : c.getErrorStream();
            String body = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new DataIntegrityRuntime.HttpResponse(status, body);
        } catch (IOException e) {
            return new DataIntegrityRuntime.HttpResponse(0, "readback IO error: " + e);
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }
}
