package io.mist.core.smart;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Grounding fix B: cold-start producer ranking via a generic param↔producer name-affinity prior.
 * Guards the endStation->trains regression and the reviewer findings (#1 cold-start gate, #2 token equality).
 */
public class ProducerRankingTest {

    private static ApiMapping mapping(String endpoint, String service, int priority, double successRate) {
        ApiMapping m = new ApiMapping(endpoint, service, "DIRECT_EXTRACTION");
        m.setPriority(priority);
        m.setSuccessRate(successRate);
        return m;
    }

    private static final ApiMapping STATIONS =
            mapping("/api/v1/stationservice/stations", "ts-station-service", 8, 0.0);
    private static final ApiMapping TRAINS =
            mapping("/api/v1/trainservice/trains", "ts-train-service", 9, 0.0);
    private static final ApiMapping VENDORS =
            mapping("/api/v1/vendorservice/vendors", "ts-vendor-service", 5, 0.0);

    /** The bug: at cold-start endStation must prefer the station service over the higher-priority trains. */
    @Test
    public void endStationPrefersStationServiceAtColdStart() {
        assertTrue("station service must out-affinity train service for endStation",
                SmartInputFetcher.nameAffinity("endStation", STATIONS)
                        > SmartInputFetcher.nameAffinity("endStation", TRAINS));
        assertTrue("at cold-start stations (pri 8) must outrank the higher-priority trains (pri 9) for endStation",
                SmartInputFetcher.rankingScore(STATIONS, "endStation", true)
                        > SmartInputFetcher.rankingScore(TRAINS, "endStation", true));
    }

    /** Reviewer #2: token 'end' must NOT substring-match 'vendor'. */
    @Test
    public void endDoesNotMatchVendor() {
        assertEquals(0.0, SmartInputFetcher.nameAffinity("endStation", VENDORS), 1e-9);
    }

    /** Reviewer #1: the prior is a cold-start-only tie-breaker; once feedback exists it must be OFF. */
    @Test
    public void affinityOffWhenNotColdStart() {
        // not cold-start => rankingScore is exactly calculateScore (no prior, so it can never override feedback)
        assertEquals(STATIONS.calculateScore(),
                SmartInputFetcher.rankingScore(STATIONS, "endStation", false), 1e-9);
        // cold-start => prior is added for a name match
        assertTrue(SmartInputFetcher.rankingScore(STATIONS, "endStation", true) > STATIONS.calculateScore());
    }

    /** Symmetric normalization: a plural 'stations' producer token matches a singular 'station' param token. */
    @Test
    public void pluralProducerMatchesSingularParam() {
        assertTrue(SmartInputFetcher.nameAffinity("stationList", STATIONS) > 0.0);
        assertEquals(0.0, SmartInputFetcher.nameAffinity("trainNumber", STATIONS), 1e-9);
    }
}
