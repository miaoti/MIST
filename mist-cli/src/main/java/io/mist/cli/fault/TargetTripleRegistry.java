package io.mist.cli.fault;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads the per-SUT target-triple registry ({@code target-triples.yaml}, kept
 * beside the SUT's {@code real-system-conf.yaml}) for the main-track
 * differential data-integrity oracle. Each triple names a state-mutating write
 * endpoint, the trace-matchable persisting dependency D, the black-box
 * read-back GET, and the request-supplied business-key fields used for
 * per-test isolation (TOOL-EXECUTION-PLAN P2).
 *
 * <p>Pure data holder: consulted only by the flag-gated control/fault pairing
 * executor ({@code mist.fault.injection.enabled=true}), never on the legacy
 * path. Registry files are small and hand-written, so parsing is strict —
 * unknown or missing keys fail loudly rather than degrading silently.
 */
public final class TargetTripleRegistry {

    /** One (write endpoint, dependency, read-back, isolation key) target. */
    public static final class Triple {
        public final String name;
        public final String writeEndpoint;
        public final String dependency;
        public final String readbackEndpoint;
        public final List<String> isolationKey;
        /** SUT-side ground-truth flag (B1.1); null on benign-trap-only targets. */
        public final FaultInjector.FaultTarget faultFlag;

        Triple(String name, String writeEndpoint, String dependency,
               String readbackEndpoint, List<String> isolationKey,
               FaultInjector.FaultTarget faultFlag) {
            this.name = name;
            this.writeEndpoint = writeEndpoint;
            this.dependency = dependency;
            this.readbackEndpoint = readbackEndpoint;
            this.isolationKey = Collections.unmodifiableList(new ArrayList<>(isolationKey));
            this.faultFlag = faultFlag;
        }
    }

    private static final Set<String> ALLOWED_KEYS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("name", "write_endpoint", "dependency", "readback_endpoint", "isolation_key",
                    "fault_flag")));

    private static final Set<String> ALLOWED_FAULT_FLAG_KEYS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("deployment", "property")));

    private TargetTripleRegistry() {
        // static loader only
    }

    /** Loads and validates a registry file from disk. */
    public static List<Triple> load(Path yamlFile) throws IOException {
        try (InputStream in = Files.newInputStream(yamlFile)) {
            return parse(in, yamlFile.toString());
        }
    }

    /**
     * Parses a registry document. Package-private so tests can feed streams;
     * {@code origin} only labels error messages.
     */
    @SuppressWarnings("unchecked")
    static List<Triple> parse(InputStream in, String origin) {
        Object raw;
        try {
            raw = new Yaml().load(in);
        } catch (RuntimeException re) {
            throw new IllegalArgumentException(
                    "TargetTripleRegistry: malformed YAML in " + origin + " — " + re.getMessage(), re);
        }
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException(
                    "TargetTripleRegistry: " + origin + " must be a map with a top-level 'triples' list");
        }
        Object triplesNode = ((Map<String, Object>) raw).get("triples");
        if (!(triplesNode instanceof List)) {
            throw new IllegalArgumentException(
                    "TargetTripleRegistry: " + origin + " is missing the top-level 'triples' list");
        }

        List<Triple> triples = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        for (Object entryNode : (List<Object>) triplesNode) {
            if (!(entryNode instanceof Map)) {
                throw new IllegalArgumentException(
                        "TargetTripleRegistry: non-map triple entry in " + origin);
            }
            Map<String, Object> entry = (Map<String, Object>) entryNode;
            for (String key : entry.keySet()) {
                if (!ALLOWED_KEYS.contains(key)) {
                    throw new IllegalArgumentException("TargetTripleRegistry: unknown key '" + key
                            + "' in " + origin + " (typo? allowed: " + ALLOWED_KEYS + ")");
                }
            }
            String name = requireString(entry, "name", origin);
            if (!seenNames.add(name)) {
                throw new IllegalArgumentException(
                        "TargetTripleRegistry: duplicate triple name '" + name + "' in " + origin);
            }
            triples.add(new Triple(
                    name,
                    requireString(entry, "write_endpoint", origin),
                    requireString(entry, "dependency", origin),
                    requireString(entry, "readback_endpoint", origin),
                    requireStringList(entry, "isolation_key", origin),
                    optionalFaultFlag(entry, origin)));
        }
        if (triples.isEmpty()) {
            throw new IllegalArgumentException(
                    "TargetTripleRegistry: 'triples' list in " + origin + " is empty");
        }
        return Collections.unmodifiableList(triples);
    }

    @SuppressWarnings("unchecked")
    private static FaultInjector.FaultTarget optionalFaultFlag(Map<String, Object> entry, String origin) {
        Object node = entry.get("fault_flag");
        if (node == null) {
            return null;
        }
        if (!(node instanceof Map)) {
            throw new IllegalArgumentException("TargetTripleRegistry: 'fault_flag' in " + origin
                    + " must be a map with 'deployment' and 'property'");
        }
        Map<String, Object> flag = (Map<String, Object>) node;
        for (String key : flag.keySet()) {
            if (!ALLOWED_FAULT_FLAG_KEYS.contains(key)) {
                throw new IllegalArgumentException("TargetTripleRegistry: unknown fault_flag key '" + key
                        + "' in " + origin + " (typo? allowed: " + ALLOWED_FAULT_FLAG_KEYS + ")");
            }
        }
        return new FaultInjector.FaultTarget(
                requireString(flag, "deployment", origin),
                requireString(flag, "property", origin));
    }

    private static String requireString(Map<String, Object> entry, String key, String origin) {
        Object value = entry.get(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new IllegalArgumentException("TargetTripleRegistry: triple in " + origin
                    + " needs a non-empty string '" + key + "'");
        }
        return ((String) value).trim();
    }

    private static List<String> requireStringList(Map<String, Object> entry, String key, String origin) {
        Object value = entry.get(key);
        if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
            throw new IllegalArgumentException("TargetTripleRegistry: triple in " + origin
                    + " needs a non-empty list '" + key + "'");
        }
        List<String> out = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof String) || ((String) item).trim().isEmpty()) {
                throw new IllegalArgumentException("TargetTripleRegistry: '" + key + "' in " + origin
                        + " must contain only non-empty strings");
            }
            out.add(((String) item).trim());
        }
        return out;
    }
}
