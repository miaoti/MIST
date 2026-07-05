package io.mist.cli.g3;

import com.sun.net.httpserver.HttpServer;
import io.mist.cli.fault.DataIntegrityRuntime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link ShippingReadbackHttp}'s wire behavior against a real loopback HTTP server —
 * the read-back's subtle bits that the head-to-head fake (path-agnostic) cannot cover:
 * (1) the {@code %2f} default-vhost encoding must reach the request line UNDECODED (a
 * URLEncoder/RestAssured "simplification" would corrupt it to %252f or //), (2) basic auth
 * is sent, (3) a non-2xx (401 wrong-creds / 500 mgmt-stats-DB-unhealthy) passes its status
 * THROUGH (so the oracle's decisive-read gate treats it as unusable, never evidence), and
 * (4) a transport failure reads as status 0 — never mistaken for presence/absence.
 */
public class ShippingReadbackHttpTest {

    private HttpServer server;
    private volatile int respStatus = 200;
    private volatile String respBody = "[{\"name\":\"shipping-task\",\"messages\":1}]";
    private final AtomicReference<String> seenRawPath = new AtomicReference<>();
    private final AtomicReference<String> seenAuth = new AtomicReference<>();
    private String base;

    @Before
    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            seenRawPath.set(exchange.getRequestURI().getRawPath());
            seenAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = respBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(respStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void getSut_preservesEncodedVhost_andSendsBasicAuth() {
        DataIntegrityRuntime.HttpResponse r =
                new ShippingReadbackHttp(base, "mist", "mist", 5000).getSut("/api/queues/%2f");
        assertEquals(200, r.status);
        assertEquals("[{\"name\":\"shipping-task\",\"messages\":1}]", r.body);
        assertEquals("the %2f default-vhost encoding must reach the wire UNDECODED",
                "/api/queues/%2f", seenRawPath.get());
        assertEquals("Basic " + Base64.getEncoder().encodeToString(
                        "mist:mist".getBytes(StandardCharsets.UTF_8)),
                seenAuth.get());
    }

    @Test
    public void unauthorized_passesStatusThrough_notSwallowedToZero() {
        respStatus = 401;
        respBody = "{\"error\":\"not_authorised\"}";
        DataIntegrityRuntime.HttpResponse r =
                new ShippingReadbackHttp(base, "wrong", "creds", 5000).getSut("/api/queues/%2f");
        assertEquals(401, r.status);
        assertTrue(r.body.contains("not_authorised"));
    }

    @Test
    public void serverError_passesStatusThrough() {
        respStatus = 500;
        respBody = "internal";
        DataIntegrityRuntime.HttpResponse r =
                new ShippingReadbackHttp(base, "mist", "mist", 5000).getSut("/api/queues/%2f");
        assertEquals(500, r.status);
    }

    @Test
    public void transportFailure_readsAsStatusZero() {
        server.stop(0); // the port now refuses connections
        server = null;
        DataIntegrityRuntime.HttpResponse r =
                new ShippingReadbackHttp(base, "mist", "mist", 2000).getSut("/api/queues/%2f");
        assertEquals("a refused/failed read must be non-2xx (never evidence)", 0, r.status);
    }
}
