package io.mist.core.fault;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.mist.llm.LLMClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the gating semantics and LLM-driven mining behaviour of
 * {@link FaultMiner}. When the system property
 * {@code mist.fault.mining.enabled} is unset/false the miner returns an empty
 * list so the registry-driven Sniper baseline is byte-for-byte identical with
 * the legacy enum baseline. When enabled, the miner calls a {@link LLMClient}
 * via the injected SPI and persists accepted candidates to
 * {@code .mist/mined-fault-types.yaml}.
 *
 * <p>The fake {@link LLMClient} below is a small in-test anonymous class so
 * mist-core stays free of {@code es.us.isa.*} imports.
 */
public class FaultMinerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private String previousEnabled;
    private Path previousMinedPath;

    @Before
    public void setUp() throws Exception {
        previousEnabled = System.getProperty(FaultMiner.ENABLED_PROPERTY);
        System.clearProperty(FaultMiner.ENABLED_PROPERTY);
        // Redirect the mined-fault-types file into a temp folder so each test
        // runs against a fresh on-disk state.
        previousMinedPath = FaultMiner.minedTypesPath;
        FaultMiner.minedTypesPath = tmp.newFolder(".mist").toPath().resolve("mined-fault-types.yaml");
    }

    @After
    public void tearDown() {
        if (previousEnabled == null) {
            System.clearProperty(FaultMiner.ENABLED_PROPERTY);
        } else {
            System.setProperty(FaultMiner.ENABLED_PROPERTY, previousEnabled);
        }
        FaultMiner.minedTypesPath = previousMinedPath;
    }

    @Test
    public void disabledByDefaultReturnsEmptyList() {
        RecordingLLMClient fake = new RecordingLLMClient("ignored");
        FaultMiner miner = new FaultMiner(fake);
        SpecRef spec = singleParamSpec("TrainTicket", "stationName", "Name of the train station");
        List<FaultType> mined = miner.mine(spec, Collections.<ObservedResponse>emptyList());
        assertEquals(0, mined.size());
        assertEquals("disabled miner must not call the LLM", 0, fake.calls);
    }

    @Test
    public void disabledFactoryReturnsEmptyEvenWhenEnabled() {
        System.setProperty(FaultMiner.ENABLED_PROPERTY, "true");
        FaultMiner miner = FaultMiner.disabled();
        SpecRef spec = singleParamSpec("TrainTicket", "stationName", "Name of the train station");
        List<FaultType> mined = miner.mine(spec, Collections.<ObservedResponse>emptyList());
        assertEquals(0, mined.size());
    }

    @Test
    public void enabledAcceptsTwoCandidatesAndPersistsYaml() throws Exception {
        System.setProperty(FaultMiner.ENABLED_PROPERTY, "true");
        String response =
                "{\"id\":\"INVALID_STATION_NAME\",\"displayName\":\"Invalid station name\","
                        + "\"applicableTo\":[\"string\"],\"applicableLocations\":[\"path\",\"query\"]}\n"
                        + "{\"id\":\"INVALID_TRIP_ID\",\"displayName\":\"Invalid trip id\","
                        + "\"applicableTo\":[\"string\"],\"applicableLocations\":[\"path\"]}\n";
        RecordingLLMClient fake = new RecordingLLMClient(response);
        FaultMiner miner = new FaultMiner(fake);
        SpecRef spec = singleParamSpec("TrainTicket", "stationName", "Name of the train station");

        List<FaultType> mined = miner.mine(spec, Collections.<ObservedResponse>emptyList());

        assertEquals(1, fake.calls);
        assertEquals(2, mined.size());
        assertEquals("INVALID_STATION_NAME", mined.get(0).id());
        assertEquals(FaultType.FaultSource.MINED, mined.get(0).source());
        assertTrue(mined.get(0).applicableTo().contains("string"));
        assertEquals("INVALID_TRIP_ID", mined.get(1).id());

        // The yaml file must exist and contain both ids.
        Path yamlFile = FaultMiner.minedTypesPath;
        assertNotNull(yamlFile);
        assertTrue("mined-fault-types.yaml must be written", Files.exists(yamlFile));
        String text = new String(Files.readAllBytes(yamlFile));
        assertTrue(text.contains("INVALID_STATION_NAME"));
        assertTrue(text.contains("INVALID_TRIP_ID"));

        // Idempotency: a second invocation with the same response appends nothing.
        List<FaultType> secondRun = miner.mine(spec, Collections.<ObservedResponse>emptyList());
        assertEquals("second run must be idempotent", 0, secondRun.size());
        String afterSecond = new String(Files.readAllBytes(yamlFile));
        assertEquals("yaml must be unchanged after a duplicate run", text, afterSecond);
    }

    @Test
    public void enabledToleratesMalformedLinesAndAcceptsTheGoodOne() throws Exception {
        System.setProperty(FaultMiner.ENABLED_PROPERTY, "true");
        String response =
                "{not valid json at all}\n"
                        + "{\"id\":\"INVALID_STATION_NAME\",\"displayName\":\"Invalid station name\","
                        + "\"applicableTo\":[\"string\"],\"applicableLocations\":[\"path\"]}\n";
        RecordingLLMClient fake = new RecordingLLMClient(response);
        FaultMiner miner = new FaultMiner(fake);
        SpecRef spec = singleParamSpec("TrainTicket", "stationName", "Name of the train station");

        List<FaultType> mined = miner.mine(spec, Collections.<ObservedResponse>emptyList());

        assertEquals(1, fake.calls);
        assertEquals(1, mined.size());
        assertEquals("INVALID_STATION_NAME", mined.get(0).id());
    }

    @Test
    public void enabledRejectsCandidatesThatCollideWithDefaults() {
        System.setProperty(FaultMiner.ENABLED_PROPERTY, "true");
        String response =
                "{\"id\":\"TYPE_MISMATCH\",\"displayName\":\"Type Mismatch\","
                        + "\"applicableTo\":[\"string\"],\"applicableLocations\":[\"path\"]}\n";
        RecordingLLMClient fake = new RecordingLLMClient(response);
        FaultMiner miner = new FaultMiner(fake);
        SpecRef spec = singleParamSpec("TrainTicket", "stationName", "Name of the train station");

        List<FaultType> mined = miner.mine(spec, Collections.<ObservedResponse>emptyList());

        assertEquals(1, fake.calls);
        assertEquals("collision with a default id must be rejected silently", 0, mined.size());

        // No yaml file should have been written (or it should not contain TYPE_MISMATCH).
        Path yamlFile = FaultMiner.minedTypesPath;
        assertFalse("mined-fault-types.yaml must not be written when nothing is accepted",
                Files.exists(yamlFile));
    }

    // --- helpers -----------------------------------------------------------

    private static SpecRef singleParamSpec(String apiKey, String paramName, String paramDescription) {
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put(paramName, paramDescription);
        return new SpecRef(apiKey, descriptions);
    }

    /** Tiny test double: records call count and returns a canned response. */
    private static final class RecordingLLMClient implements LLMClient {
        final String cannedResponse;
        int calls = 0;

        RecordingLLMClient(String cannedResponse) {
            this.cannedResponse = cannedResponse;
        }

        @Override
        public String prompt(String systemPrompt, String userPrompt) {
            calls++;
            return cannedResponse;
        }
    }
}
