package io.mist.core.health;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Black-box preflight that probes each Root API the registry knows about
 * BEFORE scenario discovery, so an unhealthy SUT endpoint becomes visible
 * in the startup banner instead of materialising as silently-dropped
 * scenarios five hours later.
 *
 * <p>Motivation — Run 13 detected 5/10 documented faults instead of 10/10.
 * Forensic recount traced the gap to the SUT returning HTTP 500 on every
 * {@code GET /api/v1/admintravelservice/admintravel} at run-time (the call
 * {@code SmartInputFetcher} makes to populate the {@code tripId} pool), with
 * no way to see that without log archaeology. This preflight surfaces those
 * failures at second 0 of the run, not hour 5.
 *
 * <p>Design — pure-function {@link #check(List, Probe)} so the iteration is
 * unit-testable with a stub probe. Production callers use
 * {@link #httpClientProbe(int)} which wraps {@link HttpClient}.
 */
public final class SutHealthCheck {

    private SutHealthCheck() {}

    /** A single root API endpoint to probe. */
    public static final class Endpoint {
        public final String verb;
        public final String url;
        public Endpoint(String verb, String url) {
            this.verb = verb;
            this.url = url;
        }
        @Override public String toString() { return verb + " " + url; }
    }

    /** Per-endpoint probe outcome. */
    public static final class Result {
        public final Endpoint endpoint;
        public final int statusCode;    // -1 on transport error / timeout
        public final long latencyMs;
        public final String errorMessage; // non-null only on transport failure
        public Result(Endpoint endpoint, int statusCode, long latencyMs, String errorMessage) {
            this.endpoint = endpoint;
            this.statusCode = statusCode;
            this.latencyMs = latencyMs;
            this.errorMessage = errorMessage;
        }
        /**
         * Healthy = the endpoint is *reachable* (any HTTP status code &lt; 500).
         * 4xx (incl. 401/403 auth, 404 not-found) is treated healthy — the
         * service is up; only 5xx and transport errors mean the SUT is broken
         * in a way that will break scenario generation.
         */
        public boolean healthy() {
            return statusCode >= 100 && statusCode < 500;
        }
    }

    /** Aggregated preflight outcome. */
    public static final class Report {
        public final List<Result> results;
        public Report(List<Result> results) {
            this.results = Collections.unmodifiableList(new ArrayList<>(results));
        }
        public List<Result> unhealthy() {
            List<Result> out = new ArrayList<>();
            for (Result r : results) if (!r.healthy()) out.add(r);
            return out;
        }
        public int totalCount()     { return results.size(); }
        public int unhealthyCount() { return unhealthy().size(); }
        public int healthyCount()   { return totalCount() - unhealthyCount(); }
        public boolean allHealthy() { return unhealthyCount() == 0; }

        /** Single-line summary suitable for a startup banner. */
        public String summary() {
            return String.format("SUT preflight: %d/%d endpoints reachable, %d unhealthy",
                    healthyCount(), totalCount(), unhealthyCount());
        }
    }

    /** Functional interface so tests can stub a probe without HTTP. */
    @FunctionalInterface
    public interface Probe {
        Result probe(Endpoint endpoint);
    }

    /** Iterate {@code endpoints}, probe each, return an aggregated report. */
    public static Report check(List<Endpoint> endpoints, Probe probe) {
        List<Result> results = new ArrayList<>(endpoints.size());
        for (Endpoint e : endpoints) {
            results.add(probe.probe(e));
        }
        return new Report(results);
    }

    /**
     * Default HTTP-based probe (unauthenticated). Sends the requested verb at
     * the URL with a 0-byte body, follows no redirects, treats anything
     * &lt; 500 as reachable. {@code timeoutMs} bounds both connect and read.
     *
     * <p>{@code HEAD} is preferred semantically but several SUT endpoints
     * (notably train-ticket's gateway) 405 on HEAD; callers that want to
     * be strict can pass {@code "HEAD"} as the endpoint verb anyway.
     *
     * <p>This unauthenticated form is good for "is the network path open?"
     * but cannot detect handlers that work without auth (return 4xx) yet
     * crash under valid auth (return 5xx). For that case use
     * {@link #httpClientProbe(int, Map)} with the same Authorization header
     * the actual run will send.
     */
    public static Probe httpClientProbe(int timeoutMs) {
        return httpClientProbe(timeoutMs, Collections.emptyMap());
    }

    /**
     * Authenticated/header-aware HTTP probe. Same shape as
     * {@link #httpClientProbe(int)} but attaches every entry of
     * {@code extraHeaders} to each outgoing request, so the probe matches
     * the auth shape the actual run will use.
     *
     * <p>Motivating case (train-ticket, 2026-05-22): the SUT returns 403 to
     * unauthenticated GETs (which the no-auth probe classifies "reachable")
     * but a deterministic 500 to the same GET with a valid admin JWT (which
     * is what MIST sends at run time). An auth-aware preflight surfaces this
     * at second 0 instead of letting it cascade into a 5h run with silent
     * scenario drops.
     */
    public static Probe httpClientProbe(int timeoutMs, Map<String, String> extraHeaders) {
        // Copy to insulate the returned probe from caller mutation; preserve
        // insertion order (LinkedHashMap) for predictable test assertions.
        final Map<String, String> headers = new LinkedHashMap<>(
                extraHeaders == null ? Collections.emptyMap() : extraHeaders);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return endpoint -> {
            long t0 = System.nanoTime();
            try {
                HttpRequest req = buildPreflightRequest(endpoint, headers, timeoutMs);
                HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
                long ms = (System.nanoTime() - t0) / 1_000_000L;
                return new Result(endpoint, resp.statusCode(), ms, null);
            } catch (Exception ex) {
                long ms = (System.nanoTime() - t0) / 1_000_000L;
                return new Result(endpoint, -1, ms, ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        };
    }

    /**
     * Test-visible helper that builds the {@link HttpRequest} the probe would
     * send. Hoisted so unit tests can verify verb, URI, timeout, and header
     * threading without a live HTTP server.
     */
    static HttpRequest buildPreflightRequest(Endpoint endpoint,
                                              Map<String, String> extraHeaders,
                                              int timeoutMs) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(endpoint.url))
                .timeout(Duration.ofMillis(timeoutMs))
                .method(endpoint.verb, HttpRequest.BodyPublishers.noBody());
        if (extraHeaders != null) {
            for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    b.header(e.getKey(), e.getValue());
                }
            }
        }
        return b.build();
    }
}
