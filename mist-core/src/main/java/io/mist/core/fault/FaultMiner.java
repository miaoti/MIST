package io.mist.core.fault;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import io.mist.llm.LLMClient;

/**
 * LLM-assisted miner that proposes SUT-specific {@link FaultType} categories.
 *
 * <p>Gated by the system property {@code mist.fault.mining.enabled} (default
 * {@code false}). When disabled, {@link #mine(SpecRef, List)} returns an empty
 * list so the registry-driven Sniper baseline stays byte-for-byte identical
 * with the legacy enum behaviour.
 *
 * <p>When enabled, the miner builds a system + user prompt from the SUT's
 * OpenAPI parameter descriptions and observed 4xx/5xx responses, calls the
 * {@link LLMClient} (which routes through {@code LLMCallCache} so seeded
 * reruns short-circuit the backend), parses the JSON-per-line response into
 * candidate {@link FaultType} entries, validates them against the registry's
 * canonical shape, rejects duplicates of the eight defaults, and appends the
 * accepted survivors to {@code .mist/mined-fault-types.yaml} for the user to
 * promote into a per-SUT overlay manually.
 */
public final class FaultMiner {

    public static final String ENABLED_PROPERTY = "mist.fault.mining.enabled";

    static final Path DEFAULT_MINED_TYPES_PATH = Paths.get(".mist", "mined-fault-types.yaml");

    // Package-private mutable handle so unit tests can redirect the output
    // file to a temporary folder without using reflection to strip 'final'.
    // Not a production-tunable: there is no system property to override it.
    static Path minedTypesPath = DEFAULT_MINED_TYPES_PATH;

    static final int MAX_USER_PROMPT_CHARS = 6_000;
    static final int MAX_OBSERVED_RESPONSE_CHARS = 500;
    static final int MAX_OBSERVED_RESPONSES_PER_PROMPT = 3;

    static final Set<String> ALLOWED_OAS_TYPES = Collections.unmodifiableSet(new LinkedHashSet<>(
            Arrays.asList("string", "integer", "number", "boolean", "array", "object")));

    static final Set<String> ALLOWED_LOCATIONS = Collections.unmodifiableSet(new LinkedHashSet<>(
            Arrays.asList("path", "query", "header", "cookie", "body")));

    /** The built-in defaults — candidates colliding with these are rejected silently. */
    static final Set<String> DEFAULT_IDS = Collections.unmodifiableSet(new LinkedHashSet<>(
            Arrays.asList(
                    "TYPE_MISMATCH",
                    "REGEX_MISMATCH",
                    "SEMANTIC_MISMATCH",
                    "OVERFLOW",
                    "EMPTY_INPUT",
                    "NULL_INPUT",
                    "SPECIAL_CHARACTERS",
                    "BOUNDARY_VIOLATION",
                    "ENUM_VIOLATION")));

    private static final Pattern UPPER_SNAKE_CASE = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    static final String SYSTEM_PROMPT =
            "You are an expert REST API security and robustness tester.\n" +
            "Given an OpenAPI parameter description and a sample of observed\n" +
            "4xx/5xx responses for that parameter, propose up to 3 SUT-specific\n" +
            "invalid-input categories the test generator should additionally\n" +
            "exercise. Output each category on its own line as a JSON object\n" +
            "with the keys:\n" +
            "  {\"id\": \"UPPER_SNAKE_CASE_ID\",\n" +
            "   \"displayName\": \"human-readable name\",\n" +
            "   \"applicableTo\": [\"string\"|\"integer\"|\"number\"|\"boolean\"|\"array\"|\"object\", ...],\n" +
            "   \"applicableLocations\": [\"path\"|\"query\"|\"header\"|\"cookie\"|\"body\", ...]}\n" +
            "Do not propose categories that overlap with these defaults:\n" +
            "  TYPE_MISMATCH, REGEX_MISMATCH, SEMANTIC_MISMATCH, OVERFLOW,\n" +
            "  EMPTY_INPUT, NULL_INPUT, SPECIAL_CHARACTERS, BOUNDARY_VIOLATION,\n" +
            "  ENUM_VIOLATION.\n";

    private static final Logger log = LogManager.getLogger(FaultMiner.class);

    private final LLMClient llmClient;

    public FaultMiner(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * Returns a miner whose {@link #mine(SpecRef, List)} always returns an
     * empty list. Convenience for unit tests that exercise upstream code
     * paths and don't care about the LLM dimension.
     */
    public static FaultMiner disabled() {
        return new FaultMiner(null);
    }

    public List<FaultType> mine(SpecRef spec, List<ObservedResponse> observedResponses) {
        if (!isEnabled()) {
            log.debug("FaultMiner: mining disabled ({}=false); returning empty list", ENABLED_PROPERTY);
            return Collections.emptyList();
        }
        if (llmClient == null) {
            log.debug("FaultMiner: no LLMClient injected; returning empty list");
            return Collections.emptyList();
        }
        if (spec == null) {
            log.debug("FaultMiner: spec is null; returning empty list");
            return Collections.emptyList();
        }
        Map<String, String> descriptions = spec.parameterDescriptions();
        List<ObservedResponse> responses = observedResponses == null
                ? Collections.<ObservedResponse>emptyList()
                : observedResponses;

        if ((descriptions == null || descriptions.isEmpty()) && responses.isEmpty()) {
            log.debug("FaultMiner: no parameter descriptions and no observed responses; nothing to mine");
            return Collections.emptyList();
        }

        String userPrompt = buildUserPrompt(spec, responses);
        log.info("FaultMiner: prompting LLM with {} parameters and {} observed responses for SUT={}",
                descriptions == null ? 0 : descriptions.size(),
                responses.size(),
                spec.apiKey());
        log.debug("FaultMiner: system prompt = {}", SYSTEM_PROMPT);
        log.debug("FaultMiner: user prompt = {}", userPrompt);

        String response;
        try {
            response = llmClient.prompt(SYSTEM_PROMPT, userPrompt);
        } catch (RuntimeException re) {
            log.warn("FaultMiner: LLM call threw '{}'; returning empty list", re.getMessage());
            return Collections.emptyList();
        }
        if (response == null || response.isEmpty()) {
            log.warn("FaultMiner: LLM returned no content; returning empty list");
            return Collections.emptyList();
        }
        log.debug("FaultMiner: LLM response = {}", response);

        ParseResult parsed = parseResponse(response);
        List<FaultType> accepted = parsed.accepted;
        int malformed = parsed.malformed;
        int duplicates = parsed.duplicates;

        // Deduplicate against any candidates already present in
        // .mist/mined-fault-types.yaml (idempotent across runs).
        Path outPath = minedTypesPath;
        Set<String> existingIds = readExistingMinedIds(outPath);
        List<FaultType> netNew = new ArrayList<>();
        for (FaultType ft : accepted) {
            if (existingIds.contains(ft.id())) {
                duplicates++;
            } else {
                netNew.add(ft);
                existingIds.add(ft.id());
            }
        }

        if (!netNew.isEmpty()) {
            try {
                persist(outPath, netNew);
            } catch (IOException ioe) {
                log.warn("FaultMiner: failed to persist mined types to {}: {}",
                        outPath, ioe.getMessage());
            }
        }

        log.info("FaultMiner: accepted {} candidates, rejected {} duplicates, {} malformed",
                netNew.size(), duplicates, malformed);
        return netNew;
    }

    // --- prompt assembly --------------------------------------------------

    static String buildUserPrompt(SpecRef spec, List<ObservedResponse> responses) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("SUT: ").append(spec.apiKey() == null ? "(unknown)" : spec.apiKey()).append('\n');

        Map<String, String> descriptions = spec.parameterDescriptions();
        if (descriptions != null && !descriptions.isEmpty()) {
            sb.append('\n').append("OpenAPI parameters:\n");
            for (Map.Entry<String, String> e : descriptions.entrySet()) {
                if (sb.length() >= MAX_USER_PROMPT_CHARS) break;
                sb.append("- ").append(e.getKey());
                String value = e.getValue();
                if (value != null && !value.isEmpty()) {
                    sb.append(": ").append(value);
                }
                sb.append('\n');
            }
        }

        if (!responses.isEmpty()) {
            sb.append('\n').append("Observed 4xx/5xx responses (sample):\n");
            int kept = 0;
            for (ObservedResponse r : responses) {
                if (kept >= MAX_OBSERVED_RESPONSES_PER_PROMPT) break;
                if (sb.length() >= MAX_USER_PROMPT_CHARS) break;
                String body = r.responseBody() == null ? "" : r.responseBody();
                if (body.length() > MAX_OBSERVED_RESPONSE_CHARS) {
                    body = body.substring(0, MAX_OBSERVED_RESPONSE_CHARS);
                }
                sb.append("- HTTP ").append(r.statusCode()).append(": ").append(body).append('\n');
                kept++;
            }
        }

        if (sb.length() > MAX_USER_PROMPT_CHARS) {
            return sb.substring(0, MAX_USER_PROMPT_CHARS);
        }
        return sb.toString();
    }

    // --- response parsing -------------------------------------------------

    static final class ParseResult {
        final List<FaultType> accepted;
        final int duplicates;
        final int malformed;

        ParseResult(List<FaultType> accepted, int duplicates, int malformed) {
            this.accepted = accepted;
            this.duplicates = duplicates;
            this.malformed = malformed;
        }
    }

    static ParseResult parseResponse(String response) {
        List<FaultType> accepted = new ArrayList<>();
        Set<String> seenInThisResponse = new HashSet<>();
        int duplicates = 0;
        int malformed = 0;

        try (BufferedReader reader = new BufferedReader(new StringReader(response))) {
            String rawLine;
            while ((rawLine = reader.readLine()) != null) {
                String line = rawLine.trim();
                if (line.isEmpty() || !line.startsWith("{")) {
                    continue;
                }
                FaultType candidate;
                try {
                    candidate = parseCandidate(line);
                } catch (CandidateRejected rejected) {
                    log.info("FaultMiner: rejected malformed candidate line: {} ({})",
                            line, rejected.getMessage());
                    malformed++;
                    continue;
                } catch (JsonSyntaxException jse) {
                    log.info("FaultMiner: rejected malformed JSON line: {} ({})",
                            line, jse.getMessage());
                    malformed++;
                    continue;
                }
                if (DEFAULT_IDS.contains(candidate.id())) {
                    duplicates++;
                    continue;
                }
                if (!seenInThisResponse.add(candidate.id())) {
                    duplicates++;
                    continue;
                }
                accepted.add(candidate);
            }
        } catch (IOException ioe) {
            // BufferedReader over a StringReader cannot throw IOException in
            // practice; fall through to whatever we've accumulated.
            log.warn("FaultMiner: unexpected IO error reading response: {}", ioe.getMessage());
        }
        return new ParseResult(accepted, duplicates, malformed);
    }

    private static FaultType parseCandidate(String line) {
        JsonElement el = JsonParser.parseString(line);
        if (!el.isJsonObject()) {
            throw new CandidateRejected("not a JSON object");
        }
        JsonObject obj = el.getAsJsonObject();
        String id = readString(obj, "id");
        if (id == null || id.isEmpty() || !UPPER_SNAKE_CASE.matcher(id).matches()) {
            throw new CandidateRejected("'id' must be UPPER_SNAKE_CASE");
        }
        String displayName = readString(obj, "displayName");
        Set<String> applicableTo = readStringSet(obj, "applicableTo", ALLOWED_OAS_TYPES);
        if (applicableTo.isEmpty()) {
            throw new CandidateRejected("'applicableTo' must be a non-empty subset of " + ALLOWED_OAS_TYPES);
        }
        Set<String> applicableLocations = readStringSet(obj, "applicableLocations", ALLOWED_LOCATIONS);
        if (applicableLocations.isEmpty()) {
            throw new CandidateRejected("'applicableLocations' must be a non-empty subset of " + ALLOWED_LOCATIONS);
        }
        return new FaultType(id, displayName == null ? id : displayName,
                applicableTo, applicableLocations, FaultType.FaultSource.MINED);
    }

    private static String readString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            return null;
        }
        return el.getAsString();
    }

    private static Set<String> readStringSet(JsonObject obj, String key, Set<String> allowed) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonArray()) {
            throw new CandidateRejected("'" + key + "' must be a JSON array");
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (JsonElement entry : el.getAsJsonArray()) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                throw new CandidateRejected("'" + key + "' entries must be strings");
            }
            String v = entry.getAsString().toLowerCase(Locale.ROOT);
            if (!allowed.contains(v)) {
                throw new CandidateRejected("'" + key + "' contains invalid value '" + v
                        + "'; must be a subset of " + allowed);
            }
            out.add(v);
        }
        return out;
    }

    private static final class CandidateRejected extends RuntimeException {
        private static final long serialVersionUID = 1L;
        CandidateRejected(String message) { super(message); }
    }

    // --- persistence ------------------------------------------------------

    @SuppressWarnings("unchecked")
    static Set<String> readExistingMinedIds(Path path) {
        if (path == null || !Files.exists(path)) {
            return new LinkedHashSet<>();
        }
        try (java.io.InputStream in = Files.newInputStream(path)) {
            Object raw = new Yaml().load(in);
            if (!(raw instanceof Map)) {
                return new LinkedHashSet<>();
            }
            Object faultsNode = ((Map<?, ?>) raw).get("faults");
            if (!(faultsNode instanceof List)) {
                return new LinkedHashSet<>();
            }
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (Object entry : (List<Object>) faultsNode) {
                if (entry instanceof Map) {
                    Object idObj = ((Map<?, ?>) entry).get("id");
                    if (idObj instanceof String) {
                        ids.add((String) idObj);
                    }
                }
            }
            return ids;
        } catch (IOException | RuntimeException ex) {
            log.warn("FaultMiner: failed to read existing mined-fault-types at {}: {}",
                    path, ex.getMessage());
            return new LinkedHashSet<>();
        }
    }

    @SuppressWarnings("unchecked")
    static void persist(Path path, List<FaultType> netNew) throws IOException {
        if (netNew == null || netNew.isEmpty()) {
            return;
        }
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        // Load existing document so we can append while preserving prior
        // ordering and any unknown top-level keys that may have been added
        // by hand.
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> faults = new ArrayList<>();
        if (Files.exists(path)) {
            try (java.io.InputStream in = Files.newInputStream(path)) {
                Object raw = new Yaml().load(in);
                if (raw instanceof Map) {
                    root.putAll((Map<String, Object>) raw);
                    Object faultsNode = root.get("faults");
                    if (faultsNode instanceof List) {
                        for (Object o : (List<Object>) faultsNode) {
                            if (o instanceof Map) {
                                faults.add(new LinkedHashMap<String, Object>((Map<String, Object>) o));
                            }
                        }
                    }
                }
            } catch (RuntimeException re) {
                throw new IOException("malformed YAML at " + path + ": " + re.getMessage(), re);
            }
        }

        for (FaultType ft : netNew) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", ft.id());
            entry.put("displayName", ft.displayName());
            entry.put("applicableTo", new ArrayList<>(ft.applicableTo()));
            entry.put("applicableLocations", new ArrayList<>(ft.applicableLocations()));
            entry.put("source", ft.source().name());
            faults.add(entry);
        }
        root.put("faults", faults);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        String yaml = new Yaml(options).dump(root);
        Files.write(path, yaml.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"));
    }
}
