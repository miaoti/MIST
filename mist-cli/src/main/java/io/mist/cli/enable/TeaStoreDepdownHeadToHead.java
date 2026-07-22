package io.mist.cli.enable;

import io.mist.cli.fault.DataIntegrityRuntime;
import io.mist.cli.fault.PairedFaultExecutor;
import io.mist.cli.fault.TargetTripleRegistry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Depdown live-upgrade wave (2026-07-21, user-directed): the MISSING MIST read-back leg for
 * {@code teastore-order-depdown-specified-001} — the same paired MEMBERSHIP binding as wave
 * 2.75-A ({@link TeaStoreOrderHeadToHead}, oracle logic reused verbatim; control legs delegate
 * to the proven {@link TeaStoreHttpStimulus}) under the DEPENDENCY-DOWN producer instead of the
 * maintenance toggle or the mesh-sever VS.
 *
 * <p><b>Fault choreography (mirrors the 2026-07-20 capture protocol, incl. the disclosed
 * buffer-drop nuance):</b> the faulted leg splits the journey because login/browse READS require
 * the db (B-m3): login + addToCart run with the db UP; then {@code teastore-db} is scaled to 0
 * and polled until 0 db pods (an unverified fault state aborts loudly); the confirm executes
 * against the down dependency (the webui masks it as a 302/200 confirmed page); then, in a
 * finally, the BUFFER-DROP restore runs BEFORE the oracle polls: force-delete the persistence
 * pod WHILE the db is still down (dropping the JPA connection-pool's buffered write — without
 * this the pool flushes on reconnect and ~50% of "lost" writes persist), scale the db back to 1,
 * wait both rollouts, settle. The oracle's read-back therefore probes a clean, restored
 * {@code /rest/orders} — mirroring the meshsever "deleted after the write and BEFORE the oracle
 * polls" discipline.
 *
 * <p>Ground truth stays DIRECT persistence reads (never MIST) — recorded by the runner script
 * from the report's markers. Records + disclosures are indexed in
 * {@code debug/a-main/benchmark/b4/RESULT-depdown-live.md}.
 */
public final class TeaStoreDepdownHeadToHead {

    /** Decorator: control = the proven 2.75-A journey; fault = split journey + db-down window. */
    static final class DepdownStimulus implements TeaStoreOrderHeadToHead.Stimulus {
        private final TeaStoreHttpStimulus inner;
        private final String webui;
        private final String user;
        private final String pass;
        private final String product;
        private final int timeoutMs = 12000;
        private final ProcessCommandRunner runner = new ProcessCommandRunner();
        private final List<String> kubectl;

        DepdownStimulus(TeaStoreHttpStimulus inner, String webui, String user, String pass,
                        String product, String context) {
            this.inner = inner;
            this.webui = webui.trim().replaceAll("/+$", "");
            this.user = user;
            this.pass = pass;
            this.product = product;
            this.kubectl = Arrays.asList("wsl", "kubectl", "--context", context, "-n", "teastore");
        }

        @Override
        public TeaStoreOrderHeadToHead.Ack placeOrder(String marker, boolean faulted)
                throws Exception {
            if (!faulted) {
                return inner.placeOrder(marker, false);
            }
            // (1) journey prep with the db UP — login/browse reads REQUIRE the db (B-m3)
            Session s = new Session();
            post(s, webui + "/loginAction", "username=" + enc(user) + "&password=" + enc(pass));
            post(s, webui + "/cartAction", "addToCart=&productid=" + enc(product));
            Resp confirm;
            try {
                // (2) dependency down, VERIFIED (0 db pods) before the write
                dbDown();
                // (3) the masked write rides the db-down window.
                // Attempt-1 fix (disclosed): 90 s read timeout (the confirm under db-down can
                // exceed the journey's 12 s; attempt 1 died SocketTimeoutException pre-verdict).
                // Attempt-3 fix (disclosed): the ACK handed to the oracle is the write POST's
                // OWN response (first hop, no redirect-follow) — attempt 2 conflated the write
                // ack with the post-redirect PAGE READ (which 500s under the down dependency)
                // and recorded "ack 500". The oracle judges the faithful first-hop ack by ITS
                // OWN frozen rule (DataIntegrityRuntime L700: acked = 2xx) — nothing is
                // manufactured; a 302 is expected to read as not-acked. The followed chain is
                // logged as JOURNEY CONTEXT only, never into the Ack.
                confirm = request("POST", webui + "/cartAction",
                        "firstname=Mist&lastname=Order&address1=" + enc(marker)
                                + "&address2=CorpusCity&cardtype=volvo&cardnumber=314159265"
                                + "&expirydate=" + enc("12/2030") + "&confirm=Confirm",
                        s, 90000);
                System.out.println("  [depdown] fault confirm WRITE-OWN response: HTTP "
                        + confirm.status + " location=" + confirm.location);
                if (confirm.status >= 300 && confirm.status < 400 && confirm.location != null) {
                    try {
                        Resp page = request("GET",
                                absolutize(webui + "/cartAction", confirm.location), null, s, 90000);
                        System.out.println("  [depdown] journey-context ONLY - followed page: HTTP "
                                + page.status);
                    } catch (Exception e) {
                        System.out.println("  [depdown] journey-context ONLY - followed page threw: "
                                + e);
                    }
                }
            } finally {
                // (4) BUFFER-DROP restore, ALWAYS, and BEFORE the oracle polls the read-back
                bufferDropRestore();
            }
            return new TeaStoreOrderHeadToHead.Ack(confirm.status, confirm.body);
        }

        /** Scale teastore-db to 0 and poll until 0 db pods; loud abort if unverified. */
        private void dbDown() throws Exception {
            exec(30000, true, "scale", "deploy", "teastore-db", "--replicas=0");
            for (int i = 0; i < 20; i++) {
                if (dbPodCount() == 0) {
                    System.out.println("  [depdown] teastore-db pods: 0 (fault state VERIFIED)");
                    return;
                }
                Thread.sleep(3000);
            }
            throw new IllegalStateException(
                    "ABORT: teastore-db pods never reached 0 - unverified fault state");
        }

        /**
         * Force-delete the persistence pod WHILE the db is still down (drops the JPA pool's
         * buffered write - the capture's disclosed determinism nuance), then restore the db and
         * wait both rollouts so the oracle polls a clean read-back path.
         */
        private void bufferDropRestore() throws Exception {
            String pod = persistencePodName();
            if (pod != null) {
                exec(30000, false, "delete", "pod", pod, "--grace-period=0", "--force");
            }
            exec(30000, true, "scale", "deploy", "teastore-db", "--replicas=1");
            exec(150000, true, "rollout", "status", "deploy/teastore-db", "--timeout=120s");
            exec(180000, true, "rollout", "status", "deploy/teastore-persistence", "--timeout=150s");
            Thread.sleep(8000);
            System.out.println("  [depdown] buffer-drop restore complete (db+persistence rolled)");
        }

        private int dbPodCount() throws Exception {
            SqlDurableReadback.CommandRunner.Result r = runPods();
            int n = 0;
            for (String line : r.stdout.split("\n")) {
                if (line.trim().startsWith("teastore-db")) {
                    n++;
                }
            }
            return n;
        }

        private String persistencePodName() throws Exception {
            SqlDurableReadback.CommandRunner.Result r = runPods();
            for (String line : r.stdout.split("\n")) {
                String t = line.trim();
                if (t.startsWith("teastore-persistence")) {
                    return t.split("\\s+")[0];
                }
            }
            return null;
        }

        private SqlDurableReadback.CommandRunner.Result runPods() throws Exception {
            ArrayList<String> argv = new ArrayList<>(kubectl);
            argv.addAll(Arrays.asList("get", "pods", "--no-headers"));
            return runner.run(argv, 30000);
        }

        private void exec(int timeoutMs, boolean loud, String... args) throws Exception {
            ArrayList<String> argv = new ArrayList<>(kubectl);
            argv.addAll(Arrays.asList(args));
            SqlDurableReadback.CommandRunner.Result r = runner.run(argv, timeoutMs);
            System.out.println("  [depdown] kubectl " + String.join(" ", args)
                    + " -> exit " + r.exitCode);
            if (loud && r.exitCode != 0) {
                throw new IllegalStateException("kubectl " + String.join(" ", args)
                        + " failed: " + r.stderr);
            }
        }

        // ---- minimal HTTP journey helpers (copied from TeaStoreHttpStimulus, whose members are
        // private; duplication confined to this driver and disclosed in the RESULT) ----

        private Resp post(Session s, String url, String form) throws Exception {
            return post(s, url, form, timeoutMs);
        }

        private Resp post(Session s, String url, String form, int readTimeoutMs) throws Exception {
            String target = url;
            Resp r = null;
            for (int hop = 0; hop < 6; hop++) {
                r = request(hop == 0 ? "POST" : "GET", target,
                        hop == 0 ? form : null, s, readTimeoutMs);
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

        private Resp request(String method, String url, String body, Session s, int readTimeoutMs)
                throws IOException {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) URI.create(url).toURL().openConnection();
                c.setRequestMethod(method);
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(timeoutMs);
                c.setReadTimeout(readTimeoutMs);
                if (s != null && !s.cookies.isEmpty()) {
                    c.setRequestProperty("Cookie", s.cookieHeader());
                }
                if (body != null) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
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
            return URI.create(base).resolve(location).toString();
        }

        private static String enc(String v) {
            return URLEncoder.encode(v, StandardCharsets.UTF_8);
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

    public static void main(String[] args) throws Exception {
        String webui = req("ts.webui");
        String persistence = req("ts.persistence");
        Path triplePath = Paths.get(req("ts.triple"));
        int probes = Integer.getInteger("ts.probes", 4);
        if (System.getProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY) == null) {
            System.setProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY, "15000");
        }
        System.setProperty("mst.test.parallelism", "1");
        try {
            DataIntegrityRuntime.installHttpOverride(
                    new JsonDurableReadback(persistence, "", 8000));
            TargetTripleRegistry.Registry reg = TargetTripleRegistry.load(triplePath);
            TargetTripleRegistry.Triple triple = reg.triples.get(0);
            TeaStoreHttpStimulus inner = new TeaStoreHttpStimulus(
                    webui, persistence,
                    System.getProperty("ts.user", "user21"),
                    System.getProperty("ts.pass", "password"),
                    System.getProperty("ts.product", "42"));
            DepdownStimulus stimulus = new DepdownStimulus(
                    inner, webui,
                    System.getProperty("ts.user", "user21"),
                    System.getProperty("ts.pass", "password"),
                    System.getProperty("ts.product", "42"),
                    System.getProperty("ts.kube.context", "kind-mist"));
            List<PairedFaultExecutor.PairResult> pairs =
                    new TeaStoreOrderHeadToHead(stimulus, probes).run(triple);
            String report = System.getProperty("ts.report");
            if (report != null && !report.trim().isEmpty()) {
                PairedFaultExecutor.writeReport(Paths.get(report.trim()), pairs,
                        "teastore-order-depdown-" + System.currentTimeMillis());
                System.out.println("  report written to " + report.trim());
            }
        } finally {
            DataIntegrityRuntime.installHttpOverride(null);
        }
    }

    private static String req(String key) {
        String v = System.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("missing required -D" + key);
        }
        return v.trim();
    }
}
