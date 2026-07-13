package io.mist.cli.s3;

import io.mist.cli.enable.ProcessCommandRunner;
import io.mist.cli.enable.SqlDurableReadback;
import io.mist.cli.fault.DataIntegrityRuntime;
import io.mist.cli.fault.TargetTripleRegistry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * S3 wild-hunt window runner — OTel-Demo (plan rev 2.1 §6 P1). Pinned fault-free journey: browse
 * (GET products — read step) → POST /api/cart → POST /api/checkout with the neutral marker in
 * {@code address.streetAddress} and a client-generated W3C traceparent (adoption canary-gated at P1;
 * export-selection only — the observe read-back stays runtime-gated). Read-back = the 2.75-A
 * {@code SqlDurableReadback} on {@code accounting.shipping} (marker-scoped; the re-probe re-points
 * the supplier via the engine swap). FlagHook: RAW-time trace snapshot + CONFIRM-time two-read
 * span-count stability re-fetch (plan §2d; review A-F4). Modes: {@code s3.mode=calibration|window}.
 */
public final class OtelWildHunt {

    private static volatile String currentMarker;

    public static void main(String[] args) throws Exception {
        String base = req("s3.base");                        // http://localhost:8085
        String pgpod = req("s3.pgpod");
        String jaeger = System.getProperty("s3.jaeger", ""); // e.g. http://localhost:16687 ("" = no snapshots)
        String mode = System.getProperty("s3.mode", "calibration");
        int journeys = Integer.getInteger("s3.journeys", "calibration".equals(mode) ? 20 : 100);
        Path outDir = Paths.get(System.getProperty("s3.out",
                "debug/a-main/benchmark/b4/s3/" + mode + "-oteldemo"));
        long reProbeMs = Long.getLong("s3.reprobe.ms", 300_000L);
        // Freeze-row knobs (pin 2/§2): OTel timeout 25 s, poll 2 s (psql-exec-dominated).
        System.setProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY, "25000");
        System.setProperty(DataIntegrityRuntime.POLL_MS_PROPERTY, "2000");
        System.setProperty("mst.test.parallelism", "1");

        String kubectl = System.getProperty("s3.kubectl", "wsl kubectl --context kind-mist");
        List<String> execPrefix = new ArrayList<>(Arrays.asList(kubectl.trim().split("\\s+")));
        execPrefix.addAll(Arrays.asList("-n", "otel-demo", "exec", "-i", pgpod, "--", "env",
                "PGPASSWORD=otel", "psql", "-U", "root", "-d", "otel", "-t", "-A", "-c"));
        SqlDurableReadback readback = new SqlDurableReadback(execPrefix, "street_address",
                "SELECT street_address FROM accounting.shipping WHERE street_address='%s'",
                () -> currentMarker, 20_000, new ProcessCommandRunner());

        TargetTripleRegistry.Registry reg = TargetTripleRegistry.load(Paths.get(System.getProperty(
                "s3.triple", "debug/a-main/benchmark/b4/enable/oteldemo-checkout-triple.yaml")));
        TargetTripleRegistry.Triple triple = reg.triples.get(0);

        WildHuntEngine.FlagHook hook = new WildHuntEngine.FlagHook() {
            @Override
            public void onAckedAbsent(WildHuntEngine.WriteEntry e) {
                snapshotTrace(jaeger, e, outDir, "raw");
            }

            @Override
            public void onReProbeDone(WildHuntEngine.WriteEntry e) {
                if (e.reProbe != null
                        && e.reProbe.outcome == DataIntegrityRuntime.ReProbeOutcome.ABSENT) {
                    stabilityFetch(jaeger, e, outDir);
                }
            }
        };
        WildHuntEngine engine = new WildHuntEngine("oteldemo", mode + "-oteldemo", outDir,
                reProbeMs, Long.getLong("s3.marker.seed", 20260713L), hook);
        engine.probeDescriptor =
                "SQL SELECT street_address FROM accounting.shipping WHERE street_address='<marker>'";
        Files.createDirectories(outDir);

        List<WildHuntEngine.Journey> js = new ArrayList<>();
        Random tp = new Random(20260713L);
        for (int i = 0; i < journeys; i++) {
            js.add(ctx -> journey(ctx, base, triple, tp));
        }
        try {
            DataIntegrityRuntime.installHttpOverride(readback);
            engine.runWindow(java.util.Collections.singletonList(triple), js, readback, marker -> {
                String prev = currentMarker;
                currentMarker = marker;
                return () -> currentMarker = prev;
            });
        } finally {
            DataIntegrityRuntime.installHttpOverride(null);
        }
        // Rater-facing version_ref = SOFTWARE IDENTITY ONLY. Deployment detail (cluster name "mist" —
        // a B4 banned substring — namespace, flagd/load-gen env) lives in the our-side environment_guard
        // below, never in the rendered version_ref (plan §4.3; cross-strata normalization is a P5 item).
        engine.emit(System.getProperty("s3.mist.commit", "unpinned"),
                "opentelemetry-demo app 2.2.0 / chart 0.40.9",
                new org.json.JSONObject().put("flagd_defaults_verified", System.getProperty("s3.envguard", "pending"))
                        .put("load_generator", "off"));
        System.out.println("window done: see " + outDir.resolve("window-log.json"));
    }

    /** One journey: browse (read) → cart → checkout (the bound write). */
    private static void journey(WildHuntEngine.JourneyContext ctx, String base,
                                TargetTripleRegistry.Triple triple, Random tp) throws Exception {
        String session = UUID.randomUUID().toString();
        // read step: product browse (write-path-fraction denominator).
        ctx.recorder.request("GET", "/api/products/0PUK6V6EV0", null);
        Resp browse = http("GET", base + "/api/products/0PUK6V6EV0", null, null);
        ctx.recorder.response(browse.status, "<product json elided>");
        ctx.countRead();

        ctx.recorder.request("POST", "/api/cart?currencyCode=USD",
                "{\"item\":{\"productId\":\"0PUK6V6EV0\",\"quantity\":1},\"userId\":\"" + session + "\"}");
        Resp cart = http("POST", base + "/api/cart?currencyCode=USD",
                "{\"item\":{\"productId\":\"0PUK6V6EV0\",\"quantity\":1},\"userId\":\"" + session + "\"}", null);
        ctx.recorder.response(cart.status, cart.body);

        String marker = ctx.nextMarker();
        currentMarker = marker;
        String traceId = hex(tp, 16);
        String traceparent = "00-" + traceId + "-" + hex(tp, 8) + "-01";
        String payload = "{\"userId\":\"" + session + "\",\"userCurrency\":\"USD\","
                + "\"email\":\"" + marker + "@example.test\","
                + "\"address\":{\"streetAddress\":\"" + marker + "\",\"state\":\"CA\","
                + "\"country\":\"United States\",\"city\":\"Mountain View\",\"zipCode\":\"94043\"},"
                + "\"creditCard\":{\"creditCardCvv\":672,\"creditCardExpirationMonth\":1,"
                + "\"creditCardExpirationYear\":2030,\"creditCardNumber\":\"4432-8015-6152-0454\"}}";
        ctx.recorder.request("POST", "/api/checkout?currencyCode=USD", payload);
        // traceId passed INTO write() so it is set before onAckedAbsent fires (the RAW-time trace
        // snapshot needs it); export-selection only — never enters the sidecar records (plan §2d).
        ctx.write(triple, triple.isolationKey.get(0), marker, traceId, () -> {
            Resp r = http("POST", base + "/api/checkout?currencyCode=USD", payload, traceparent);
            ctx.recorder.response(r.status, r.body);
            return new WildHuntEngine.Ack(r.status, r.body);
        });
    }

    private static void snapshotTrace(String jaeger, WildHuntEngine.WriteEntry e, Path outDir,
                                      String tag) {
        if (jaeger == null || jaeger.isEmpty() || e.traceId == null) {
            return;
        }
        try {
            Files.createDirectories(outDir.resolve("flags"));
            Resp r = http("GET", jaeger + "/jaeger/ui/api/traces/" + e.traceId, null, null);
            Files.write(outDir.resolve("flags").resolve("trace-w" + e.index + "-" + tag + ".json"),
                    (r.body == null ? "" : r.body).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            System.err.println("trace snapshot failed (w" + e.index + "): " + ex);
        }
    }

    /** CONFIRM-time stability re-fetch (plan §2d): two reads, span counts must match. */
    private static void stabilityFetch(String jaeger, WildHuntEngine.WriteEntry e, Path outDir) {
        if (jaeger == null || jaeger.isEmpty() || e.traceId == null) {
            return;
        }
        try {
            Resp a = http("GET", jaeger + "/jaeger/ui/api/traces/" + e.traceId, null, null);
            Thread.sleep(5000);
            Resp b = http("GET", jaeger + "/jaeger/ui/api/traces/" + e.traceId, null, null);
            int ca = spanCount(a.body);
            int cb = spanCount(b.body);
            Files.createDirectories(outDir.resolve("flags"));
            Files.write(outDir.resolve("flags").resolve("trace-w" + e.index + "-confirm.json"),
                    ("{\"span_count_read1\":" + ca + ",\"span_count_read2\":" + cb
                            + ",\"stable\":" + (ca == cb && ca >= 0) + ",\"trace\":"
                            + (b.body == null ? "null" : b.body) + "}").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            System.err.println("stability fetch failed (w" + e.index + "): " + ex);
        }
    }

    private static int spanCount(String traceJson) {
        if (traceJson == null) {
            return -1;
        }
        try {
            org.json.JSONObject d = new org.json.JSONObject(traceJson);
            org.json.JSONArray data = d.optJSONArray("data");
            if (data == null || data.length() == 0) {
                return 0;
            }
            int n = 0;
            for (int i = 0; i < data.length(); i++) {
                n += data.getJSONObject(i).getJSONArray("spans").length();
            }
            return n;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static String hex(Random r, int bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes; i++) {
            sb.append(String.format("%02x", r.nextInt(256)));
        }
        return sb.toString();
    }

    private static String req(String k) {
        String v = System.getProperty(k);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("missing -D" + k);
        }
        return v.trim();
    }

    private static final class Resp {
        final int status;
        final String body;

        Resp(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private static Resp http(String method, String url, String jsonBody, String traceparent)
            throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            if (traceparent != null) {
                c.setRequestProperty("traceparent", traceparent);
            }
            if (jsonBody != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                byte[] b = jsonBody.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(b);
                }
            }
            int status = c.getResponseCode();
            InputStream in = status / 100 == 2 ? c.getInputStream() : c.getErrorStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            if (in != null) {
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    buf.write(chunk, 0, n);
                }
            }
            return new Resp(status, buf.toString("UTF-8"));
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }
}
