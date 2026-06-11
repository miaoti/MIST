package io.mist.core.workflow;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 * Externalised noun-to-key mapping used by
 * {@link TraceWorkflowExtractor#extractFieldsFromUrl(String, java.util.Map) URL extraction}
 * to turn path segments such as {@code orders} into semantic field names like
 * {@code orderId}.
 *
 * <p>The default map is bundled on the classpath at
 * {@code mist/noun-map.default.yaml} and is tailored for the TrainTicket demo.
 * Per-SUT overrides can be supplied via {@code mist.noun.map.path} (read by
 * {@link io.mist.core.config.MstConfig.Core#nounMapPath()}); a
 * loaded override is overlaid on top of the default, so unspecified nouns
 * still resolve through the default.
 *
 * <p>This class is immutable after construction. Malformed YAML aborts startup
 * via a {@link RuntimeException} so configuration mistakes are surfaced
 * immediately rather than silently producing missing path-parameter keys.
 */
public final class NounKeyMap {

    private static final Logger log = LogManager.getLogger(NounKeyMap.class);
    private static final String DEFAULT_CLASSPATH_RESOURCE = "/mist/noun-map.default.yaml";

    private final Map<String, String> map;

    private NounKeyMap(Map<String, String> map) {
        this.map = map;
    }

    /** Loads the bundled default YAML from {@code mist/noun-map.default.yaml} on the classpath. */
    public static NounKeyMap fromDefault() {
        Map<String, String> defaults = loadDefaultMap();
        return new NounKeyMap(defaults);
    }

    /**
     * Loads a YAML file from {@code path} and overlays it on top of the bundled
     * default map. Missing keys fall back to the defaults. Throws a
     * {@link RuntimeException} if the file is missing or malformed.
     */
    public static NounKeyMap fromPath(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("NounKeyMap: path must not be null");
        }
        Map<String, String> overlay = new LinkedHashMap<>(loadDefaultMap());
        try (InputStream in = Files.newInputStream(path)) {
            Map<String, String> parsed = parseYamlMap(in, "file " + path);
            overlay.putAll(parsed);
        } catch (IOException ioe) {
            throw new RuntimeException(
                    "NounKeyMap: failed to read override file " + path, ioe);
        }
        return new NounKeyMap(overlay);
    }

    /** Returns the key name for a noun, or {@code null} if the noun is not in the map. */
    public String keyFor(String noun) {
        return map.get(noun);
    }

    /** Read-only view of the underlying map; intended for diagnostics and tests. */
    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(map);
    }

    // --- internals --------------------------------------------------------

    private static Map<String, String> loadDefaultMap() {
        try (InputStream in = NounKeyMap.class.getResourceAsStream(DEFAULT_CLASSPATH_RESOURCE)) {
            if (in == null) {
                throw new RuntimeException(
                        "NounKeyMap: bundled default resource not found on classpath: "
                                + DEFAULT_CLASSPATH_RESOURCE);
            }
            return parseYamlMap(in, "classpath:" + DEFAULT_CLASSPATH_RESOURCE);
        } catch (IOException ioe) {
            throw new RuntimeException(
                    "NounKeyMap: failed to read bundled default resource " + DEFAULT_CLASSPATH_RESOURCE,
                    ioe);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> parseYamlMap(InputStream in, String origin) {
        Object raw;
        try {
            raw = new Yaml().load(in);
        } catch (RuntimeException re) {
            throw new RuntimeException(
                    "NounKeyMap: malformed YAML in " + origin + " — " + re.getMessage(), re);
        }
        if (raw == null) {
            // Empty file or comments-only — treat as empty overlay rather than fail.
            return new LinkedHashMap<>();
        }
        if (!(raw instanceof Map)) {
            throw new RuntimeException(
                    "NounKeyMap: " + origin + " must contain a top-level YAML map; got "
                            + raw.getClass().getSimpleName());
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            Object k = entry.getKey();
            Object v = entry.getValue();
            if (k == null || v == null) {
                throw new RuntimeException(
                        "NounKeyMap: " + origin + " contains a null key or value");
            }
            // Reject non-string entries explicitly. SnakeYAML deserialises bare
            // integer values to Integer, etc.; silent String.valueOf coercion
            // would mask a misconfigured YAML file (`orders: 42`).
            if (!(k instanceof String) || !(v instanceof String)) {
                throw new RuntimeException(
                        "NounKeyMap: " + origin + " entries must be string:string; got "
                                + k.getClass().getSimpleName() + ":" + v.getClass().getSimpleName());
            }
            out.put((String) k, (String) v);
        }
        log.debug("NounKeyMap: loaded {} entries from {}", out.size(), origin);
        return out;
    }
}
