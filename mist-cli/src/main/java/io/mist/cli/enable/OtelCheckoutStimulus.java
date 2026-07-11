package io.mist.cli.enable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Live HTTP stimulus for {@link OtelCheckoutHeadToHead}: drives OpenTelemetry-Demo's checkout flow
 * (POST /api/cart &rarr; POST /api/checkout) against the frontend-proxy JSON API. Each order uses a
 * fresh client-side session uuid and plants {@code marker} in {@code address.streetAddress}, which
 * (kafka up) rides checkout&rarr;Kafka {@code orders}&rarr;accounting and lands verbatim in
 * {@code accounting.shipping.street_address} (verified live). The checkout ack is a full 200 order
 * confirmation whether or not the broker is up (the produce is swallowed-and-logged, survey
 * src/checkout/main.go) &mdash; the acked-but-lost condition the read-back oracle measures.
 *
 * <p>Unlike the TeaStore stimulus this owns NO fault: the kafka scale toggle is LEG-level and owned
 * by the harness ({@link KafkaClusterOps}, single-toggle between the control and fault legs), so the
 * stimulus is a pure order placer. Stateless (session rides the JSON payload, not a cookie), so no
 * cookie jar / redirect following is needed.
 */
public final class OtelCheckoutStimulus implements OtelCheckoutHeadToHead.Stimulus {

    private final String base;
    private final String productId;
    private final int timeoutMs;

    public OtelCheckoutStimulus(String base, String productId) {
        this.base = base == null ? null : base.trim().replaceAll("/+$", "");
        this.productId = productId;
        this.timeoutMs = 15000;
    }

    @Override
    public OtelCheckoutHeadToHead.Ack placeOrder(String marker) throws Exception {
        String session = UUID.randomUUID().toString();
        postJson(base + "/api/cart?currencyCode=USD",
                "{\"item\":{\"productId\":\"" + productId + "\",\"quantity\":1},\"userId\":\"" + session + "\"}");
        Resp checkout = postJson(base + "/api/checkout?currencyCode=USD",
                "{\"userId\":\"" + session + "\",\"userCurrency\":\"USD\","
                        + "\"email\":\"" + marker + "@corpus.test\","
                        + "\"address\":{\"streetAddress\":\"" + marker + "\",\"state\":\"CA\","
                        + "\"country\":\"United States\",\"city\":\"Mountain View\",\"zipCode\":\"94043\"},"
                        + "\"creditCard\":{\"creditCardCvv\":672,\"creditCardExpirationMonth\":1,"
                        + "\"creditCardExpirationYear\":2030,\"creditCardNumber\":\"4432-8015-6152-0454\"}}");
        return new OtelCheckoutHeadToHead.Ack(checkout.status, checkout.body);
    }

    private Resp postJson(String url, String json) throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setRequestMethod("POST");
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            c.setRequestProperty("Content-Length", String.valueOf(b.length));
            try (OutputStream os = c.getOutputStream()) {
                os.write(b);
            }
            int status = c.getResponseCode();
            InputStream in = status / 100 == 2 ? c.getInputStream() : c.getErrorStream();
            String body = in == null ? "" : new String(readAll(in), StandardCharsets.UTF_8);
            return new Resp(status, body);
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }

    /** One response: status + body. */
    private static final class Resp {
        final int status;
        final String body;

        Resp(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
