package io.mist.core.smart;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Data lint for the SHIPPED TrainTicket input-fetch registries (de-poison,
 * 2026-06-10). Until A2 (producer-keyed feedback) lands, NO code path can
 * legitimately raise a producer's successRate, so any non-zero rate in a
 * shipped registry is poison-era residue from the deleted format-check
 * feedback. A non-zero rate also keeps the cold-start gate
 * (max successRate < 1e-9) permanently false, which bypasses the Fix B
 * name-affinity prior for exactly the parameters it was built for
 * (endStation→trains at 0.9721 was the live failure). Revisit the all-zero
 * assertion when A2 starts committing genuinely learned rates.
 *
 * <p>Runs against the repo files via module-relative paths and skips
 * (assumption failure, not error) when the checkout layout is absent.
 */
public class ShippedRegistryDepoisonTest {

    private static final String[] SHIPPED_REGISTRIES = {
            "../mist-cli/src/main/resources/My-Example/trainticket/input-fetch-registry.yaml",
            "../evaluation/suts/trainticket/input-fetch-registry.yaml",
    };

    @Test
    public void shippedRegistries_carryNoSuccessRate() throws IOException {
        boolean checkedAny = false;
        for (String path : SHIPPED_REGISTRIES) {
            File f = new File(path);
            if (!f.isFile()) continue;
            checkedAny = true;
            InputFetchRegistry reg = InputFetchRegistry.loadFromFile(f);
            for (Map.Entry<String, List<ApiMapping>> e : reg.getParameterMappings().entrySet()) {
                for (ApiMapping m : e.getValue()) {
                    assertTrue("shipped registry " + f.getName() + " carries a non-zero successRate for '"
                                    + e.getKey() + "' -> " + m.getEndpoint()
                                    + " (poison: no code path can raise it until A2)",
                            m.getSuccessRate() < 1e-9);
                }
            }
        }
        assumeTrue("no shipped registry found relative to module dir — skipping", checkedAny);
    }

    @Test
    public void endStation_coldStartGateEngages_andStationsOutranksTrains() throws IOException {
        File f = new File(SHIPPED_REGISTRIES[0]);
        assumeTrue("mist-cli shipped registry not found — skipping", f.isFile());
        InputFetchRegistry reg = InputFetchRegistry.loadFromFile(f);
        List<ApiMapping> mappings = reg.getMappingsForParameter(
                "POST /api/v1/adminrouteservice/adminroute", "endStation");
        assumeTrue("registry has no endStation mappings — skipping", !mappings.isEmpty());

        boolean coldStart = mappings.stream()
                .mapToDouble(ApiMapping::getSuccessRate)
                .max().orElse(0.0) < 1e-9;
        assertTrue("endStation must be at cold start in the shipped registry", coldStart);

        ApiMapping stations = null, trains = null;
        for (ApiMapping m : mappings) {
            if (m.getEndpoint().contains("/stationservice/")) stations = m;
            if (m.getEndpoint().contains("/trainservice/")) trains = m;
        }
        assumeTrue("expected both stations and trains candidates", stations != null && trains != null);

        assertTrue("with the de-poisoned registry, stations must outrank trains for endStation",
                SmartInputFetcher.rankingScore(stations, "endStation", true)
                        > SmartInputFetcher.rankingScore(trains, "endStation", true));
        assertFalse("trains must no longer be the top-ranked candidate",
                SmartInputFetcher.rankingScore(trains, "endStation", true)
                        >= mappings.stream()
                                .mapToDouble(m -> SmartInputFetcher.rankingScore(m, "endStation", true))
                                .max().orElse(Double.NEGATIVE_INFINITY));
    }
}
