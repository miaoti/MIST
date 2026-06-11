package io.mist.core.smart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mist.core.llm.ParameterInfo;
import org.junit.Test;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * LIVE validation of the 2026-06-10 registry de-poison: with the shipped
 * (all-zero successRate) TrainTicket registry, MIST's own smart-fetch path for
 * {@code endStation} must ground from the station producer and return a real
 * station name, not a train name (the pre-fix failure: poisoned
 * {@code trains} at successRate 0.97 ranked first and yielded values the SUT
 * rejects with 400 "station not exists").
 *
 * <p>Gated: runs only when {@code -Dtt.live.base.url=http://host:port} is set
 * AND {@code DEEPSEEK_API_KEY} is in the environment (the value-extraction
 * step is LLM-backed). Skipped otherwise. The shipped registry YAML is copied
 * to a temp file first so the fetcher's shutdown flush cannot mutate the
 * committed file.
 */
public class TTEndStationLiveCheck {

    private static final String SHIPPED_REGISTRY =
            "../mist-cli/src/main/resources/My-Example/trainticket/input-fetch-registry.yaml";

    @Test
    public void endStation_groundsFromStationService_onLiveTT() throws Exception {
        String base = System.getProperty("tt.live.base.url");
        assumeTrue("live TT base url not set (-Dtt.live.base.url=...) — skipping",
                base != null && !base.isBlank());
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        assumeTrue("DEEPSEEK_API_KEY not set — skipping", apiKey != null && !apiKey.isBlank());
        File shipped = new File(SHIPPED_REGISTRY);
        assumeTrue("shipped registry not found — skipping", shipped.isFile());

        // LLM wiring for the extraction step (SmartInputFetcher reads System properties).
        System.setProperty("llm.enabled", "true");
        System.setProperty("llm.model.type", "openai_compatible");
        System.setProperty("llm.openai_compatible.enabled", "true");
        System.setProperty("llm.openai_compatible.url", "https://api.deepseek.com/v1/chat/completions");
        System.setProperty("llm.openai_compatible.model", "deepseek-chat");
        System.setProperty("llm.openai_compatible.api.key", apiKey);

        // Ground truth: the live station name set.
        Set<String> stationNames = liveStationNames(base);
        assumeTrue("live /stations returned no names — SUT not usable", !stationNames.isEmpty());

        // Copy the shipped registry so the fetcher's dirty-flush cannot touch it.
        Path tempRegistry = Files.createTempFile("tt-registry-live-", ".yaml");
        Files.copy(shipped.toPath(), tempRegistry, StandardCopyOption.REPLACE_EXISTING);

        SmartInputFetchConfig cfg = new SmartInputFetchConfig();
        cfg.setEnabled(true);
        cfg.setSmartFetchPercentage(1.0);
        cfg.setRegistryPath(tempRegistry.toString());
        cfg.setLlmDiscoveryEnabled(false);  // pure registry ranking — the path under test
        cfg.setMaxCandidates(5);
        cfg.setCacheEnabled(false);

        SmartInputFetcher fetcher = new SmartInputFetcher(cfg, base);
        ParameterInfo p = new ParameterInfo();
        p.setName("endStation");
        p.setType("string");
        p.setInLocation("body");
        p.setApiName("POST /api/v1/adminrouteservice/adminroute");
        p.setServiceName("ts-admin-route-service");

        String value = fetcher.fetchSmartInput(p);

        assertNotNull("smart fetch must return a value for endStation", value);
        assertTrue("fetched endStation '" + value + "' must be a real station name "
                        + "(grounded from the station producer, not trains)",
                stationNames.contains(value));
    }

    private static Set<String> liveStationNames(String base) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(base + "/api/v1/stationservice/stations"))
                .timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        Set<String> names = new HashSet<>();
        if (resp.statusCode() != 200) return names;
        JsonNode data = new ObjectMapper().readTree(resp.body()).path("data");
        for (JsonNode station : data) {
            if (station.hasNonNull("name")) names.add(station.get("name").asText());
        }
        return names;
    }
}
