package io.mist.cli.enable;

import io.mist.cli.fault.DataIntegrityRuntime;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Durable-store read-back {@link DataIntegrityRuntime.Http} for TeaStore's masked order write. Wave
 * 2.75-A, plan rev 2.1.
 *
 * <p>TeaStore's webui/auth chain swallows the persistence CREATE's {@code -1} and renders
 * ORDERCONFIRMED, so the durable system-of-record is the persistence service's {@code /rest/orders}
 * JSON (an internal, unauthenticated REST surface). MIST binds THAT rather than the user-facing
 * {@code /profile} HTML: the HTML marker (the order's firstname) also renders in the profile
 * greeting, so a text match reads PRESENT on both legs &mdash; a false negative (review A-F3/A-F13).
 * The modality is therefore {@code api-get}-JSON durable-store (a dated freeze &sect;6 amendment),
 * disclosed as an INTERNAL durable-store probe.
 *
 * <p>Installed via {@link DataIntegrityRuntime#installHttpOverride} exactly like
 * {@link io.mist.cli.g3.ShippingReadbackHttp} (minus basic auth &mdash; the persistence REST is
 * unauthenticated): {@code getSut} GETs {@code /rest/orders}, optionally scoped by a
 * {@code scopeQuery} (e.g. {@code ?userid=21}) so the returned collection is bounded to the leg's
 * user rather than the whole growing orders table (review B-R1), and returns the JSON array the
 * reviewed MEMBERSHIP oracle already parses. The oracle then checks {@code {address1:<marker>}}
 * membership; the marker is request-supplied in the confirm form's address line, so it is
 * request-derived (never read from the response). A transport failure passes its status through
 * (non-2xx) or reads as status 0 &mdash; never mistaken for presence/absence (review A-F5).
 *
 * <p>SCOPE (matches {@code ShippingReadbackHttp}'s B-m3 note): routes EVERY {@code getSut} at the
 * persistence orders API, so it is valid only for a SUPPLIED-isolation MEMBERSHIP triple whose ONLY
 * {@code getSut} is this read-back.
 */
public final class JsonDurableReadback implements DataIntegrityRuntime.Http {

    private final String base;        // e.g. http://localhost:8083/tools.descartes.teastore.persistence
    private final String scopeQuery;  // e.g. "?userid=21" to bound the collection; "" for none
    private final int timeoutMs;

    public JsonDurableReadback(String base, String scopeQuery, int timeoutMs) {
        if (base == null || base.trim().isEmpty()) {
            throw new IllegalArgumentException("JsonDurableReadback needs a persistence base URL");
        }
        this.base = base.trim().replaceAll("/+$", "");
        this.scopeQuery = scopeQuery == null ? "" : scopeQuery.trim();
        this.timeoutMs = timeoutMs;
    }

    @Override
    public DataIntegrityRuntime.HttpResponse getSut(String path) {
        String p = path.startsWith("/") ? path : "/" + path;
        return get(base + p + scopeQuery);
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
