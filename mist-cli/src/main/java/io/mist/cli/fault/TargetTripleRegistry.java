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
 * per-test isolation (TOOL-EXECUTION-PLAN P2); the optional top-level
 * {@code cluster} block carries the kubectl coordinates the
 * {@link SutFlagFaultInjector} needs (B1.1).
 *
 * <p>Pure data holder: consulted only by the flag-gated control/fault pairing
 * executor ({@code mist.fault.injection.enabled=true}), never on the legacy
 * path. Registry files are small and hand-written, so parsing is strict —
 * unknown or missing keys fail loudly rather than degrading silently.
 */
public final class TargetTripleRegistry {

    /** How the pairing run freshens the isolation-key fields per execution. */
    public enum IsolationStrategy {
        /** Unique generated string per key field (general create endpoints). */
        FRESH_STRINGS,
        /**
         * TrainTicket adminroute adapter: ordered pair of EXISTING stations
         * unused by any baseline route (the SUT validates station existence).
         */
        STATION_PAIR;

        static IsolationStrategy parse(String raw, String origin) {
            if (raw == null) {
                return FRESH_STRINGS;
            }
            switch (raw) {
                case "fresh-strings": return FRESH_STRINGS;
                case "station-pair":  return STATION_PAIR;
                default:
                    throw new IllegalArgumentException("TargetTripleRegistry: unknown isolation_strategy '"
                            + raw + "' in " + origin + " (allowed: fresh-strings, station-pair)");
            }
        }
    }

    /** Parsed registry: optional cluster coordinates + at least one triple. */
    public static final class Registry {
        public final Cluster cluster;
        public final List<Triple> triples;

        Registry(Cluster cluster, List<Triple> triples) {
            this.cluster = cluster;
            this.triples = triples;
        }
    }

    /** kubectl coordinates for the SutFlagFaultInjector; null context = current. */
    public static final class Cluster {
        public final String context;
        public final String namespace;
        public final long rolloutTimeoutSeconds;

        Cluster(String context, String namespace, long rolloutTimeoutSeconds) {
            this.context = context;
            this.namespace = namespace;
            this.rolloutTimeoutSeconds = rolloutTimeoutSeconds;
        }
    }

    /** One (write endpoint, dependency, read-back, isolation key) target. */
    public static final class Triple {
        public final String name;
        public final String writeEndpoint;
        public final String dependency;
        public final String readbackEndpoint;
        public final List<String> isolationKey;
        public final IsolationStrategy isolationStrategy;
        /** SUT-side ground-truth flag (B1.1); null on benign-trap-only targets. */
        public final FaultInjector.FaultTarget faultFlag;

        Triple(String name, String writeEndpoint, String dependency,
               String readbackEndpoint, List<String> isolationKey,
               IsolationStrategy isolationStrategy,
               FaultInjector.FaultTarget faultFlag) {
            this.name = name;
            this.writeEndpoint = writeEndpoint;
            this.dependency = dependency;
            this.readbackEndpoint = readbackEndpoint;
            this.isolationKey = Collections.unmodifiableList(new ArrayList<>(isolationKey));
            this.isolationStrategy = isolationStrategy;
            this.faultFlag = faultFlag;
        }
    }

    private static final Set<String> ALLOWED_TOP_KEYS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("cluster", "triples")));

    private static final Set<String> ALLOWED_KEYS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("name", "write_endpoint", "dependency", "readback_endpoint", "isolation_key",
                    "isolation_strategy", "fault_flag")));

    private static final Set<String> ALLOWED_FAULT_FLAG_KEYS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("deployment", "property")));

    private static final Set<String> ALLOWED_CLUSTER_KEYS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("context", "namespace", "rollout_timeout_s")));

    private TargetTripleRegistry() {
        // static loader only
    }

    /** Loads and validates a registry file from disk. */
    public static Registry load(Path yamlFile) throws IOException {
        try (InputStream in = Files.newInputStream(yamlFile)) {
            return parse(in, yamlFile.toString());
        }
    }

    /**
     * Parses a registry document from a stream; {@code origin} only labels
     * error messages.
     */
    @SuppressWarnings("unchecked")
    public static Registry parse(InputStream in, String origin) {
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
        Map<String, Object> root = (Map<String, Object>) raw;
        for (String key : root.keySet()) {
            if (!ALLOWED_TOP_KEYS.contains(key)) {
                throw new IllegalArgumentException("TargetTripleRegistry: unknown top-level key '" + key
                        + "' in " + origin + " (typo? allowed: " + ALLOWED_TOP_KEYS + ")");
            }
        }
        Object triplesNode = root.get("triples");
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
                    IsolationStrategy.parse(optionalString(entry, "isolation_strategy", origin), origin),
                    optionalFaultFlag(entry, origin)));
        }
        if (triples.isEmpty()) {
            throw new IllegalArgumentException(
                    "TargetTripleRegistry: 'triples' list in " + origin + " is empty");
        }
        return new Registry(optionalCluster(root, origin), Collections.unmodifiableList(triples));
    }

    @SuppressWarnings("unchecked")
    private static Cluster optionalCluster(Map<String, Object> root, String origin) {
        Object node = root.get("cluster");
        if (node == null) {
            return null;
        }
        if (!(node instanceof Map)) {
            throw new IllegalArgumentException("TargetTripleRegistry: 'cluster' in " + origin
                    + " must be a map");
        }
        Map<String, Object> cluster = (Map<String, Object>) node;
        for (String key : cluster.keySet()) {
            if (!ALLOWED_CLUSTER_KEYS.contains(key)) {
                throw new IllegalArgumentException("TargetTripleRegistry: unknown cluster key '" + key
                        + "' in " + origin + " (typo? allowed: " + ALLOWED_CLUSTER_KEYS + ")");
            }
        }
        String namespace = optionalString(cluster, "namespace", origin);
        long timeout = 180;
        Object timeoutNode = cluster.get("rollout_timeout_s");
        if (timeoutNode != null) {
            if (!(timeoutNode instanceof Integer) || ((Integer) timeoutNode) <= 0) {
                throw new IllegalArgumentException("TargetTripleRegistry: 'rollout_timeout_s' in " + origin
                        + " must be a positive integer");
            }
            timeout = ((Integer) timeoutNode).longValue();
        }
        return new Cluster(
                optionalString(cluster, "context", origin),
                namespace == null ? "default" : namespace,
                timeout);
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

    private static String optionalString(Map<String, Object> entry, String key, String origin) {
        Object value = entry.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new IllegalArgumentException("TargetTripleRegistry: '" + key + "' in " + origin
                    + " must be a non-empty string when present");
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
