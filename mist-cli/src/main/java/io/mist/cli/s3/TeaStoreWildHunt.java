package io.mist.cli.s3;

import io.mist.cli.enable.JsonDurableReadback;
import io.mist.cli.fault.DataIntegrityRuntime;
import io.mist.cli.fault.TargetTripleRegistry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S3 wild-hunt window runner — TeaStore (plan rev 2.1 §6 P2). Pinned fault-free journey: login
 * (pre-generated user, ROTATED per journey — review B-m10) → browse a category page (read step) →
 * addToCart → cartAction confirm with the neutral marker in {@code address1} (the bound write).
 * Read-back = the 2.75-A {@code JsonDurableReadback} over the FULL {@code /rest/orders} collection
 * (scope ""; `readback_bound` growth watched via the runtime's bound rule). Trace-uninstrumented
 * SUT: bundles are sidecar-only. Credentials are redacted at record time (B-M6d). NEVER touches
 * {@code /rest/generatedb}. Modes: {@code s3.mode=calibration|window}.
 */
public final class TeaStoreWildHunt {

    public static void main(String[] args) throws Exception {
        String webui = req("s3.webui");             // http://localhost:8082/tools.descartes.teastore.webui
        String persistence = req("s3.persistence"); // http://localhost:8083/tools.descartes.teastore.persistence
        String mode = System.getProperty("s3.mode", "calibration");
        int journeys = Integer.getInteger("s3.journeys", "calibration".equals(mode) ? 20 : 100);
        int userBase = Integer.getInteger("s3.userbase", 22); // user22.. (user21 consumed by 2.75-A)
        Path outDir = Paths.get(System.getProperty("s3.out",
                "debug/a-main/benchmark/b4/s3/" + mode + "-teastore"));
        long reProbeMs = Long.getLong("s3.reprobe.ms", 300_000L);
        System.setProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY, "10000");
        System.setProperty(DataIntegrityRuntime.POLL_MS_PROPERTY, "500");
        System.setProperty("mst.test.parallelism", "1");

        JsonDurableReadback readback = new JsonDurableReadback(persistence, "", 8000);
        TargetTripleRegistry.Registry reg = TargetTripleRegistry.load(Paths.get(System.getProperty(
                "s3.triple", "evaluation/suts/teastore/triples/teastore-order-triple.yaml")));
        TargetTripleRegistry.Triple triple = reg.triples.get(0);

        WildHuntEngine engine = new WildHuntEngine("teastore", mode + "-teastore", outDir,
                reProbeMs, Long.getLong("s3.marker.seed", 20260713L), null);
        engine.probeDescriptor = "GET /rest/orders";
        Files.createDirectories(outDir);

        List<WildHuntEngine.Journey> js = new ArrayList<>();
        for (int i = 0; i < journeys; i++) {
            final int user = userBase + (i % Integer.getInteger("s3.userspan", 40));
            js.add(ctx -> journey(ctx, webui, triple, "user" + user, "password"));
        }
        try {
            DataIntegrityRuntime.installHttpOverride(readback);
            engine.runWindow(java.util.Collections.singletonList(triple), js, readback, null);
        } finally {
            DataIntegrityRuntime.installHttpOverride(null);
        }
        // Rater-facing version_ref = SOFTWARE IDENTITY ONLY (the cluster name "mist" is a B4 banned
        // substring; deployment detail moves to the our-side environment_guard — plan §4.3, P5 norm).
        engine.emit(System.getProperty("s3.mist.commit", "unpinned"),
                "TeaStore v1.4.2 (images pinned :1.4.2; Kieker-only, trace-uninstrumented)",
                new org.json.JSONObject().put("maintenance_verified_false",
                        System.getProperty("s3.envguard", "pending")));
        System.out.println("window done: see " + outDir.resolve("window-log.json"));
    }

    /** One journey: login (rotated user) → category browse (read) → addToCart → confirm (write). */
    private static void journey(WildHuntEngine.JourneyContext ctx, String webui,
                                TargetTripleRegistry.Triple triple, String user, String pass)
            throws Exception {
        Session s = new Session();
        ctx.recorder.request("POST", "/loginAction?<credentials redacted>", null);
        Resp login = post(s, webui + "/loginAction", "username=" + enc(user) + "&password=" + enc(pass));
        ctx.recorder.response(login.status, "<login page elided>");

        ctx.recorder.request("GET", "/category?category=2&page=1", null);
        Resp cat = get(s, webui + "/category?category=2&page=1");
        ctx.recorder.response(cat.status, "<category page elided>");
        ctx.countRead();

        ctx.recorder.request("POST", "/cartAction?addToCart=&productid=42", null);
        Resp add = post(s, webui + "/cartAction", "addToCart=&productid=42");
        ctx.recorder.response(add.status, "<cart page elided>");

        String marker = ctx.nextMarker();
        String form = "firstname=Order&lastname=Journey&address1=" + enc(marker)
                + "&address2=TestCity&cardtype=volvo&cardnumber=314159265&expirydate="
                + enc("12/2030") + "&confirm=Confirm";
        ctx.recorder.request("POST", "/cartAction?" + form, null);
        ctx.write(triple, triple.isolationKey.get(0), marker, () -> {
            Resp confirm = post(s, webui + "/cartAction", form);
            ctx.recorder.response(confirm.status, "<order confirmation page elided>");
            return new WildHuntEngine.Ack(confirm.status, confirm.body);
        });
    }

    // ── minimal cookie-session HTTP (mirrors the 2.75-A stimulus semantics) ────────────────────

    private static final class Session {
        final Map<String, String> cookies = new LinkedHashMap<>();

        void absorb(List<String> setCookies) {
            if (setCookies == null) {
                return;
            }
            for (String sc : setCookies) {
                String first = sc.split(";", 2)[0].trim();
                int eq = first.indexOf('=');
                if (eq > 0) {
                    cookies.put(first.substring(0, eq).trim(), first.substring(eq + 1).trim());
                }
            }
        }

        String header() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : cookies.entrySet()) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(e.getKey()).append('=').append(e.getValue());
            }
            return sb.toString();
        }
    }

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

    private static Resp post(Session s, String url, String form) throws IOException {
        Resp r = one("POST", url, form, s);
        for (int hop = 0; hop < 5 && r.location != null
                && (r.status == 301 || r.status == 302 || r.status == 303 || r.status == 307); hop++) {
            r = one("GET", URI.create(url).resolve(r.location).toString(), null, s);
        }
        return r;
    }

    private static Resp get(Session s, String url) throws IOException {
        return one("GET", url, null, s);
    }

    private static Resp one(String method, String url, String form, Session s) throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setRequestMethod(method);
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            if (!s.cookies.isEmpty()) {
                c.setRequestProperty("Cookie", s.header());
            }
            if (form != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(form.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = c.getResponseCode();
            s.absorb(c.getHeaderFields().get("Set-Cookie"));
            String location = c.getHeaderField("Location");
            InputStream in = status / 100 == 2 ? c.getInputStream() : c.getErrorStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            if (in != null) {
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    buf.write(chunk, 0, n);
                }
            }
            return new Resp(status, buf.toString("UTF-8"), location);
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static String req(String k) {
        String v = System.getProperty(k);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("missing -D" + k);
        }
        return v.trim();
    }
}
