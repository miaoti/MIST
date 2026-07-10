package io.mist.cli.enable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live HTTP stimulus for {@link TeaStoreOrderHeadToHead}: drives the webui order flow
 * (loginAction &rarr; cartAction addToCart &rarr; cartAction confirm) against the running SUT with
 * a fresh per-order cookie jar and manual redirect following (the cart is session-scoped, so the
 * three POSTs must share cookies). The confirm plants {@code marker} in the {@code address1} field,
 * which lands verbatim in the persistence order row (verified live). When {@code faulted}, the
 * persistence maintenance flag is toggled ON around the confirm and restored OFF in a finally
 * (review B-R3), so the write is masked-lost (persistence CREATE returns 201/-1, webui shows
 * ORDERCONFIRMED) yet the following read-back sees a served {@code /rest/orders}.
 *
 * <p>The stimulus owns its own session (never {@link io.mist.cli.auth.MstAuthHandler}'s single
 * cached cookie), so per-leg isolation holds without the read-back needing auth (the persistence
 * REST is unauthenticated) &mdash; review A-F4.
 */
public final class TeaStoreHttpStimulus implements TeaStoreOrderHeadToHead.Stimulus {

    private final String webui;
    private final String persistence;
    private final String user;
    private final String pass;
    private final String product;
    private final int timeoutMs;

    public TeaStoreHttpStimulus(String webui, String persistence, String user, String pass, String product) {
        this.webui = trimSlash(webui);
        this.persistence = trimSlash(persistence);
        this.user = user;
        this.pass = pass;
        this.product = product;
        this.timeoutMs = 12000;
    }

    @Override
    public TeaStoreOrderHeadToHead.Ack placeOrder(String marker, boolean faulted) throws Exception {
        Session s = new Session();
        post(s, webui + "/loginAction", "username=" + enc(user) + "&password=" + enc(pass));
        post(s, webui + "/cartAction", "addToCart=&productid=" + enc(product));
        if (faulted) {
            setMaintenance(true);
        }
        try {
            Resp confirm = post(s, webui + "/cartAction",
                    "firstname=Mist&lastname=Order&address1=" + enc(marker)
                            + "&address2=CorpusCity&cardtype=volvo&cardnumber=314159265"
                            + "&expirydate=" + enc("12/2030") + "&confirm=Confirm");
            return new TeaStoreOrderHeadToHead.Ack(confirm.status, confirm.body);
        } finally {
            if (faulted) {
                setMaintenance(false); // B-R3: restore BEFORE the oracle reads /rest/orders
            }
        }
    }

    /** POST a JSON boolean to the persistence maintenance toggle; loud on non-2xx. */
    private void setMaintenance(boolean on) throws Exception {
        Resp r = request("POST", persistence + "/rest/generatedb/maintenance",
                String.valueOf(on), "application/json", null);
        if (r.status / 100 != 2) {
            throw new IllegalStateException("maintenance toggle " + on + " -> HTTP " + r.status + ": " + r.body);
        }
    }

    /** Form POST that follows up to 5 redirects carrying the session cookies. */
    private Resp post(Session s, String url, String form) throws Exception {
        String target = url;
        Resp r = null;
        for (int hop = 0; hop < 6; hop++) {
            r = request(hop == 0 ? "POST" : "GET", target,
                    hop == 0 ? form : null, "application/x-www-form-urlencoded", s);
            if (r.status == 301 || r.status == 302 || r.status == 303 || r.status == 307) {
                if (r.location == null) {
                    break;
                }
                target = absolutize(target, r.location);
                continue;
            }
            break;
        }
        return r;
    }

    private Resp request(String method, String url, String body, String contentType, Session s)
            throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setRequestMethod(method);
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            if (s != null && !s.cookies.isEmpty()) {
                c.setRequestProperty("Cookie", s.cookieHeader());
            }
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", contentType);
                byte[] b = body.getBytes(StandardCharsets.UTF_8);
                c.setRequestProperty("Content-Length", String.valueOf(b.length));
                try (OutputStream os = c.getOutputStream()) {
                    os.write(b);
                }
            }
            int status = c.getResponseCode();
            if (s != null) {
                List<String> setCookies = c.getHeaderFields().get("Set-Cookie");
                if (setCookies != null) {
                    s.absorb(setCookies);
                }
            }
            String location = c.getHeaderField("Location");
            InputStream in = status / 100 == 2 ? c.getInputStream() : c.getErrorStream();
            String respBody = in == null ? "" : new String(readAll(in), StandardCharsets.UTF_8);
            return new Resp(status, respBody, location);
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

    private static String absolutize(String base, String location) {
        if (location.startsWith("http://") || location.startsWith("https://")) {
            return location;
        }
        URI b = URI.create(base);
        return b.resolve(location).toString();
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static String trimSlash(String s) {
        return s == null ? null : s.trim().replaceAll("/+$", "");
    }

    /** One response: status + body + any Location (for manual redirect following). */
    private static final class Resp {
        final int status;
        final String body;
        final String location;

        Resp(int status, String body, String location) {
            this.status = status;
            this.body = body;
            this.location = location;
        }
    }

    /** A minimal per-order cookie jar (name -> value; last write wins). */
    private static final class Session {
        final Map<String, String> cookies = new LinkedHashMap<>();

        void absorb(List<String> setCookieHeaders) {
            for (String sc : setCookieHeaders) {
                String first = sc.split(";", 2)[0].trim();
                int eq = first.indexOf('=');
                if (eq > 0) {
                    cookies.put(first.substring(0, eq).trim(), first.substring(eq + 1).trim());
                }
            }
        }

        String cookieHeader() {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, String> e : cookies.entrySet()) {
                parts.add(e.getKey() + "=" + e.getValue());
            }
            return String.join("; ", parts);
        }
    }
}
