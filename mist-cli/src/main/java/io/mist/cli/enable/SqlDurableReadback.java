package io.mist.cli.enable;

import io.mist.cli.fault.DataIntegrityRuntime;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Durable-store read-back {@link DataIntegrityRuntime.Http} for OpenTelemetry-Demo's async
 * acked-but-lost write (checkout &rarr; Kafka {@code orders} &rarr; accounting-Postgres). Wave
 * 2.75-A, plan rev 2.1.
 *
 * <p>The SUT exposes NO order-query API, so the write's only durable record is the accounting
 * Postgres row; MIST reads it back through {@code kubectl exec ... psql}. The correlation key is a
 * REQUEST-DERIVED unique marker planted in the checkout street address: {@code accounting."order"}
 * carries only the server-assigned {@code order_id}, which the oracle forbids as a correlation key
 * ("X is request-derived, never read from the response" &mdash; {@link DataIntegrityRuntime} class
 * doc), whereas {@code accounting.shipping.street_address} is client-supplied. Accounting persists
 * Order+Shipping+OrderItem in ONE {@code SaveChanges()} transaction, so a {@code street_address}-keyed
 * absence is equivalence-preserving vs an {@code order_id}-keyed one (review C-R2).
 *
 * <p>Installed via {@link DataIntegrityRuntime#installHttpOverride} exactly like
 * {@link io.mist.cli.g3.ShippingReadbackHttp}: {@code getSut} runs the marker-filtered query
 * SERVER-SIDE (a {@code WHERE street_address='<marker>'} the harness bakes into
 * {@code queryTemplate}) and SYNTHESIZES the JSON collection the reviewed MEMBERSHIP oracle already
 * parses &mdash; {@code [{"street_address":"<marker>"}]} present, {@code []} absent &mdash; so the
 * decision loop is untouched AND the growing shipping table never inflates the read (result is
 * &le;1 row by the unique marker; review B-R1).
 *
 * <p>A transport failure (non-zero exit, timeout, unparseable) is returned as a non-2xx
 * {@link DataIntegrityRuntime.HttpResponse} (status 0) so the oracle's decisive-read gate treats it
 * as UNUSABLE, never as absence &mdash; a broken probe must never read as a false FLAG (review
 * A-F5/B-F8, the error-vs-absence rule, mirroring {@code ShippingReadbackHttp}).
 *
 * <p>SCOPE (matches {@code ShippingReadbackHttp}'s B-m3 note): this override routes EVERY
 * {@code getSut} at psql, so it is valid only for a SUPPLIED-isolation MEMBERSHIP triple whose ONLY
 * {@code getSut} is this read-back. The per-probe marker is read from {@code markerSupplier} at read
 * time; the harness threads the SAME fresh marker to the stimulus, to
 * {@link DataIntegrityRuntime#beforeWriteSupplied}, and here.
 */
public final class SqlDurableReadback implements DataIntegrityRuntime.Http {

    /**
     * Injectable command runner so the exec transport is unit-testable without a live cluster.
     * {@link ProcessCommandRunner} is the production {@code ProcessBuilder} implementation.
     */
    public interface CommandRunner {
        Result run(List<String> argv, int timeoutMs) throws Exception;

        /** Exit code + captured streams of one command. */
        final class Result {
            public final int exitCode;
            public final String stdout;
            public final String stderr;

            public Result(int exitCode, String stdout, String stderr) {
                this.exitCode = exitCode;
                this.stdout = stdout == null ? "" : stdout;
                this.stderr = stderr == null ? "" : stderr;
            }
        }
    }

    /**
     * Whitelist for the interpolated marker (defence-in-depth even though the harness generates it):
     * the marker is spliced into a single-quoted SQL literal, so anything outside this set &mdash;
     * notably a {@code '} &mdash; is rejected to a non-2xx before any command runs.
     */
    private static final Pattern MARKER_WHITELIST = Pattern.compile("[A-Za-z0-9_.:-]+");

    private final List<String> execPrefix;
    private final String keyField;
    private final String queryTemplate;
    private final Supplier<String> markerSupplier;
    private final int timeoutMs;
    private final CommandRunner runner;

    /**
     * @param execPrefix     the argv up to (but excluding) the SQL, e.g. {@code [kubectl, -n,
     *                       otel-demo, exec, -i, <pod>, --, env, PGPASSWORD=otel, psql, -U, root,
     *                       -d, otel, -t, -A, -c]} &mdash; a runbook constant (the kubectl binary is
     *                       a configurable knob for the Windows/WSL split, review B-R2).
     * @param keyField       the field name in both the synthesized JSON and the SQL projection
     *                       (e.g. {@code street_address}); must equal the triple's single
     *                       isolation-key field so the MEMBERSHIP oracle matches.
     * @param queryTemplate  a single-{@code %s} SQL template whose {@code %s} is the whitelisted
     *                       marker, e.g. {@code SELECT street_address FROM accounting.shipping WHERE
     *                       street_address='%s'}.
     * @param markerSupplier reads the CURRENT probe's marker at read time (harness-threaded).
     */
    public SqlDurableReadback(List<String> execPrefix, String keyField, String queryTemplate,
                              Supplier<String> markerSupplier, int timeoutMs, CommandRunner runner) {
        if (execPrefix == null || execPrefix.isEmpty()) {
            throw new IllegalArgumentException("SqlDurableReadback needs a non-empty exec prefix");
        }
        if (keyField == null || keyField.trim().isEmpty()) {
            throw new IllegalArgumentException("SqlDurableReadback needs a keyField");
        }
        if (queryTemplate == null || !queryTemplate.contains("%s")) {
            throw new IllegalArgumentException("SqlDurableReadback queryTemplate needs one %s marker slot");
        }
        if (markerSupplier == null) {
            throw new IllegalArgumentException("SqlDurableReadback needs a markerSupplier");
        }
        if (runner == null) {
            throw new IllegalArgumentException("SqlDurableReadback needs a CommandRunner");
        }
        this.execPrefix = new ArrayList<>(execPrefix);
        this.keyField = keyField.trim();
        this.queryTemplate = queryTemplate;
        this.markerSupplier = markerSupplier;
        this.timeoutMs = timeoutMs;
        this.runner = runner;
    }

    @Override
    public DataIntegrityRuntime.HttpResponse getSut(String path) {
        String marker = markerSupplier.get();
        if (marker == null || marker.trim().isEmpty()) {
            return new DataIntegrityRuntime.HttpResponse(0, "sql readback: no marker supplied for this probe");
        }
        marker = marker.trim();
        if (!MARKER_WHITELIST.matcher(marker).matches()) {
            // Never interpolate an unsafe marker; treat as an UNUSABLE read (non-2xx), never absence.
            return new DataIntegrityRuntime.HttpResponse(0, "sql readback: marker not in whitelist: " + marker);
        }
        String sql = String.format(queryTemplate, marker);
        List<String> argv = new ArrayList<>(execPrefix);
        argv.add(sql);
        try {
            CommandRunner.Result r = runner.run(argv, timeoutMs);
            if (r.exitCode != 0) {
                // Error, NOT absence: pass a non-2xx so the decisive-read gate rejects it (A-F5).
                return new DataIntegrityRuntime.HttpResponse(0,
                        "sql readback exit " + r.exitCode + ": " + firstLine(r.stderr));
            }
            JSONArray rows = new JSONArray();
            for (String line : r.stdout.split("\\R")) {
                String v = line.trim();
                if (!v.isEmpty()) {
                    rows.put(new JSONObject().put(keyField, v));
                }
            }
            return new DataIntegrityRuntime.HttpResponse(200, rows.toString());
        } catch (Exception e) {
            return new DataIntegrityRuntime.HttpResponse(0, "sql readback transport error: " + e);
        }
    }

    @Override
    public DataIntegrityRuntime.HttpResponse getAbsolute(String url) {
        // A timeout-gated MEMBERSHIP read-back never uses the Jaeger trace gate (getAbsolute), so
        // this is unreachable in practice; return a non-2xx rather than pretend to serve a URL.
        return new DataIntegrityRuntime.HttpResponse(0, "sql readback: getAbsolute is unsupported");
    }

    private static String firstLine(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        int nl = s.indexOf('\n');
        return nl < 0 ? s.trim() : s.substring(0, nl).trim();
    }
}
