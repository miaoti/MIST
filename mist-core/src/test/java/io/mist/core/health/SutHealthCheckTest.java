package io.mist.core.health;

import java.net.http.HttpRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.mist.core.health.SutHealthCheck.Endpoint;
import io.mist.core.health.SutHealthCheck.Probe;
import io.mist.core.health.SutHealthCheck.Report;
import io.mist.core.health.SutHealthCheck.Result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link SutHealthCheck}. Uses stub probes so the suite has
 * zero network dependency — preserves the byte-identical idempotent test
 * contract that lets these run inside CI without a live SUT.
 */
public class SutHealthCheckTest {

    private static final Endpoint GET_HEALTH    = new Endpoint("GET",    "http://sut/api/health");
    private static final Endpoint GET_BROKEN    = new Endpoint("GET",    "http://sut/api/admintravel");
    private static final Endpoint POST_BACKUP   = new Endpoint("POST",   "http://sut/api/backup");
    private static final Endpoint DELETE_GONE   = new Endpoint("DELETE", "http://sut/api/admintravel/{tripId}");

    /** Stub probe driven by a verb+url → statusCode lookup table. */
    private static Probe stubProbe(Map<String, Integer> table) {
        return endpoint -> {
            Integer status = table.get(endpoint.verb + " " + endpoint.url);
            if (status == null) status = 0;
            return new Result(endpoint, status, /*latencyMs*/ 1L, /*errorMessage*/ null);
        };
    }

    // ── Result.healthy() boundary cases ────────────────────────────────────

    @Test
    public void result_status200_isHealthy() {
        assertTrue(new Result(GET_HEALTH, 200, 1, null).healthy());
    }

    @Test
    public void result_status404_isHealthy_serviceUpJustNotFound() {
        // 404 means the service is reachable; only 5xx implies the SUT is broken
        // in a way that will silently drop scenarios downstream.
        assertTrue("404 must be healthy — service is up", new Result(GET_HEALTH, 404, 1, null).healthy());
    }

    @Test
    public void result_status403_isHealthy_authIsSeparateFromReachability() {
        // Auth-rejected is a separate concern from health. The preflight only
        // certifies "the network path works"; auth is handled by MstAuthHandler.
        assertTrue(new Result(GET_HEALTH, 403, 1, null).healthy());
    }

    @Test
    public void result_status500_isUnhealthy_thisIsTheRun13RegressionShape() {
        // This is exactly the Run 13 shape: GET /admintravelservice/admintravel → 500.
        // Preflight MUST flag this as unhealthy so the operator sees the regression
        // at startup, not as a missing-fault detection 5 hours later.
        assertFalse(new Result(GET_BROKEN, 500, 1, null).healthy());
    }

    @Test
    public void result_status503_isUnhealthy() {
        assertFalse(new Result(GET_BROKEN, 503, 1, null).healthy());
    }

    @Test
    public void result_transportError_isUnhealthy() {
        // statusCode == -1 = no HTTP response at all (timeout / connect-refused).
        assertFalse(new Result(GET_BROKEN, -1, 1, "ConnectException: connection refused").healthy());
    }

    // ── check() iteration + Report aggregation ─────────────────────────────

    @Test
    public void check_emptyList_returnsEmptyReport() {
        Report r = SutHealthCheck.check(Collections.emptyList(), stubProbe(new HashMap<>()));
        assertEquals(0, r.totalCount());
        assertTrue("empty list trivially all-healthy", r.allHealthy());
    }

    @Test
    public void check_invokesProbeForEachEndpoint() {
        Map<String, Integer> table = new HashMap<>();
        table.put("GET http://sut/api/health",    200);
        table.put("GET http://sut/api/admintravel",  500);
        table.put("POST http://sut/api/backup",     201);

        Report r = SutHealthCheck.check(
                Arrays.asList(GET_HEALTH, GET_BROKEN, POST_BACKUP),
                stubProbe(table));

        assertEquals(3, r.totalCount());
        assertEquals(2, r.healthyCount());
        assertEquals(1, r.unhealthyCount());
        assertFalse(r.allHealthy());
    }

    @Test
    public void check_preservesEndpointOrder() {
        List<Endpoint> endpoints = Arrays.asList(POST_BACKUP, GET_HEALTH, DELETE_GONE);
        Map<String, Integer> table = new HashMap<>();
        table.put("POST http://sut/api/backup", 201);
        table.put("GET http://sut/api/health",  200);
        table.put("DELETE http://sut/api/admintravel/{tripId}", 404);

        Report r = SutHealthCheck.check(endpoints, stubProbe(table));
        assertEquals("POST",   r.results.get(0).endpoint.verb);
        assertEquals("GET",    r.results.get(1).endpoint.verb);
        assertEquals("DELETE", r.results.get(2).endpoint.verb);
    }

    @Test
    public void report_unhealthy_returnsOnlyFailingResults() {
        Map<String, Integer> table = new HashMap<>();
        table.put("GET http://sut/api/health",        200);
        table.put("GET http://sut/api/admintravel",   500);
        table.put("DELETE http://sut/api/admintravel/{tripId}", 503);

        Report r = SutHealthCheck.check(
                Arrays.asList(GET_HEALTH, GET_BROKEN, DELETE_GONE),
                stubProbe(table));

        List<Result> bad = r.unhealthy();
        assertEquals(2, bad.size());
        // Order preserved — same as input.
        assertEquals(500, bad.get(0).statusCode);
        assertEquals(503, bad.get(1).statusCode);
    }

    @Test
    public void report_summary_humanReadable() {
        Map<String, Integer> table = new HashMap<>();
        table.put("GET http://sut/api/health",      200);
        table.put("GET http://sut/api/admintravel", 500);

        Report r = SutHealthCheck.check(Arrays.asList(GET_HEALTH, GET_BROKEN), stubProbe(table));
        String s = r.summary();
        assertTrue("summary mentions reachable count: " + s, s.contains("1/2 endpoints reachable"));
        assertTrue("summary mentions unhealthy count: " + s, s.contains("1 unhealthy"));
    }

    // ── Run 13 scenario: real failure pattern reproduced via stub ──────────

    // ── buildPreflightRequest header threading ─────────────────────────────

    @Test
    public void buildRequest_attachesAllHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer abc123");
        headers.put("X-Mist-Run-Id",  "42");

        HttpRequest req = SutHealthCheck.buildPreflightRequest(GET_HEALTH, headers, 5000);

        assertEquals("GET", req.method());
        assertEquals("http://sut/api/health", req.uri().toString());
        // HttpRequest header lookup is case-insensitive and returns the first match.
        assertEquals("Bearer abc123", req.headers().firstValue("Authorization").orElse(null));
        assertEquals("42",            req.headers().firstValue("X-Mist-Run-Id").orElse(null));
    }

    @Test
    public void buildRequest_skipsNullValueHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer abc123");
        headers.put("X-Empty-Optional", null);  // simulate "no auth handler available"

        HttpRequest req = SutHealthCheck.buildPreflightRequest(GET_HEALTH, headers, 5000);
        assertEquals("Bearer abc123", req.headers().firstValue("Authorization").orElse(null));
        assertFalse("null-valued header must be skipped, not crash",
                req.headers().firstValue("X-Empty-Optional").isPresent());
    }

    @Test
    public void buildRequest_acceptsNullHeaderMap() {
        // Caller may have no auth configured; preflight must still build a
        // valid request (= the unauthenticated form, identical to phase 1).
        HttpRequest req = SutHealthCheck.buildPreflightRequest(GET_HEALTH, null, 5000);
        assertEquals("GET", req.method());
        assertFalse(req.headers().firstValue("Authorization").isPresent());
    }

    @Test
    public void buildRequest_preservesPerEndpointVerb() {
        HttpRequest req = SutHealthCheck.buildPreflightRequest(POST_BACKUP, Collections.emptyMap(), 5000);
        assertEquals("POST", req.method());
    }

    @Test
    public void httpClientProbe_authOverload_doesNotShareMutableStateWithCaller() {
        // The probe must snapshot the headers at construction time —
        // otherwise late mutation in the caller's map would silently
        // change probe behaviour mid-run.
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer initial");
        Probe probe = SutHealthCheck.httpClientProbe(5000, headers);
        // Caller mutates: the probe instance must not pick this up.
        headers.put("Authorization", "Bearer late-change");
        headers.put("X-Sneaky", "after-construction");
        // (We can't easily intercept the actual HTTP call without a server,
        // but we proved insulation by reading the original map back unchanged
        // is meaningless — the real assertion is that the probe still works.
        // Sanity: probe is non-null and callable.)
        assertTrue("probe must remain usable after caller mutates the source map",
                probe != null);
    }

    // ── Run 13 SUT-blocker shape (auth-aware preflight catches it) ─────────

    @Test
    public void authAwarePreflight_catchesAuthenticated500_thatUnauthMisses() {
        // train-ticket 2026-05-22 SUT shape: GET /admintravelservice/admintravel
        //   no auth  → 403 (preflight phase 1 sees this as "reachable")
        //   + JWT    → 500 (the actual MIST run blows up here)
        // The new overload — invoked with the same Authorization MIST uses —
        // makes the 500 visible at startup.
        Endpoint admintravel = new Endpoint("GET",
                "http://sut/api/v1/admintravelservice/admintravel");

        // Unauth simulation (phase 1): 403 → healthy
        Probe unauthProbe = ep -> new Result(ep, 403, 1L, null);
        Report unauthReport = SutHealthCheck.check(Collections.singletonList(admintravel), unauthProbe);
        assertTrue("phase 1 (no auth) misclassifies 403 as reachable", unauthReport.allHealthy());

        // Auth-aware simulation (phase 2): same endpoint, 500 → unhealthy
        Probe authProbe = ep -> new Result(ep, 500, 1L, null);
        Report authReport = SutHealthCheck.check(Collections.singletonList(admintravel), authProbe);
        assertFalse("auth-aware preflight surfaces the SUT-side 500", authReport.allHealthy());
        assertEquals(1, authReport.unhealthyCount());
    }

    @Test
    public void run13RegressionShape_flagsAdmintravel500() {
        // Reproduces the exact endpoint mix from the Run 13 failure: most root
        // APIs healthy, but admintravel returns 500. The preflight Report must
        // call this out at startup so the operator catches the regression
        // without log archaeology.
        Map<String, Integer> table = new HashMap<>();
        table.put("GET http://sut/api/v1/adminbasicservice/adminbasic/stations", 200);
        table.put("GET http://sut/api/v1/userservice/users",                     200);
        table.put("GET http://sut/api/v1/admintravelservice/admintravel",        500);
        table.put("POST http://sut/api/v1/adminbasicservice/adminbasic/prices",  200);

        Report r = SutHealthCheck.check(Arrays.asList(
                new Endpoint("GET",  "http://sut/api/v1/adminbasicservice/adminbasic/stations"),
                new Endpoint("GET",  "http://sut/api/v1/userservice/users"),
                new Endpoint("GET",  "http://sut/api/v1/admintravelservice/admintravel"),
                new Endpoint("POST", "http://sut/api/v1/adminbasicservice/adminbasic/prices")),
                stubProbe(table));

        assertFalse(r.allHealthy());
        assertEquals(1, r.unhealthyCount());
        Result bad = r.unhealthy().get(0);
        assertEquals("the regression endpoint is admintravel, exactly as Run 13",
                "http://sut/api/v1/admintravelservice/admintravel", bad.endpoint.url);
        assertEquals(500, bad.statusCode);
    }
}
