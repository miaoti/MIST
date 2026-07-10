package io.mist.cli.enable;

import com.sun.net.httpserver.HttpServer;
import io.mist.cli.fault.DataIntegrityRuntime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link JsonDurableReadback}'s wire behavior against a loopback HTTP server (mirrors
 * {@link io.mist.cli.g3.ShippingReadbackHttp}'s test): (1) a 2xx orders array is passed through
 * for the MEMBERSHIP oracle; (2) the {@code scopeQuery} (e.g. {@code ?userid=21}) reaches the wire
 * so the collection is bounded server-side (review B-R1); (3) a non-2xx (503 persistence
 * maintenance) passes its status THROUGH so the decisive-read gate treats it as unusable, never
 * evidence; (4) a transport failure reads as status 0 &mdash; never presence/absence.
 */
public class JsonDurableReadbackTest {

    private HttpServer server;
    private volatile int respStatus = 200;
    private volatile String respBody = "[{\"id\":9,\"address1\":\"mist-run7-p3\"}]";
    private final AtomicReference<String> seenRawPath = new AtomicReference<>();
    private final AtomicReference<String> seenQuery = new AtomicReference<>();
    private String base;

    @Before
    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            seenRawPath.set(exchange.getRequestURI().getRawPath());
            seenQuery.set(exchange.getRequestURI().getRawQuery());
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
    public void getSut_returnsArray_andAppendsScopeQuery() {
        DataIntegrityRuntime.HttpResponse r =
                new JsonDurableReadback(base, "?userid=21", 5000).getSut("/rest/orders");
        assertEquals(200, r.status);
        assertTrue(r.body.contains("mist-run7-p3"));
        assertEquals("/rest/orders", seenRawPath.get());
        assertEquals("the userid scope must bound the collection server-side (B-R1)",
                "userid=21", seenQuery.get());
    }

    @Test
    public void maintenance503_passesStatusThrough_notSwallowed() {
        respStatus = 503;
        respBody = "maintenance";
        DataIntegrityRuntime.HttpResponse r =
                new JsonDurableReadback(base, "", 5000).getSut("/rest/orders");
        assertEquals(503, r.status);
    }

    @Test
    public void transportFailure_readsAsStatusZero() {
        server.stop(0);
        server = null;
        DataIntegrityRuntime.HttpResponse r =
                new JsonDurableReadback(base, "?userid=21", 2000).getSut("/rest/orders");
        assertEquals("a refused/failed read must be non-2xx (never evidence)", 0, r.status);
    }

    @Test
    public void noScope_leavesQueryNull() {
        DataIntegrityRuntime.HttpResponse r =
                new JsonDurableReadback(base, "", 5000).getSut("/rest/orders");
        assertEquals(200, r.status);
        assertEquals("/rest/orders", seenRawPath.get());
    }
}
