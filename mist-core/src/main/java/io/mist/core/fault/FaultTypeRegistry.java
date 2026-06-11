package io.mist.core.fault;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 * Registry of {@link FaultType} entries. Loads the bundled default YAML
 * (eight categories that mirror the legacy {@code InvalidInputType} enum)
 * and optionally overlays a per-SUT YAML pointed to by
 * {@code mist.fault.types.path} / passed explicitly to {@link #loadOverlay(Path)}.
 *
 * <p>Overlay semantics: entries with an id already present in the registry
 * replace the prior entry (mined types can refine the default applicability);
 * new ids extend the registry, preserving declared order so the round-robin
 * cursor still fires defaults first.
 *
 * <p>Insertion order is preserved on iteration so the registry's
 * {@code values()} matches the YAML rotation order.
 */
public final class FaultTypeRegistry {

    private static final Logger log = LogManager.getLogger(FaultTypeRegistry.class);
    private static final String DEFAULT_CLASSPATH_RESOURCE = "/mist/fault-types.default.yaml";

    private final LinkedHashMap<String, FaultType> byId;
    private final List<FaultType> orderedValues;

    private FaultTypeRegistry(LinkedHashMap<String, FaultType> byId) {
        this.byId = byId;
        this.orderedValues = Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    public static FaultTypeRegistry loadDefault() {
        return new FaultTypeRegistry(parseClasspath(DEFAULT_CLASSPATH_RESOURCE, FaultType.FaultSource.DEFAULT));
    }

    /**
     * Returns a new registry with the YAML at {@code perSutYaml} overlaid on
     * top of this registry. The receiver is not mutated.
     */
    public FaultTypeRegistry loadOverlay(Path perSutYaml) {
        if (perSutYaml == null) {
            throw new IllegalArgumentException("FaultTypeRegistry.loadOverlay: path must not be null");
        }
        LinkedHashMap<String, FaultType> merged = new LinkedHashMap<>(this.byId);
        try (InputStream in = Files.newInputStream(perSutYaml)) {
            List<FaultType> overlay = parseStream(in, "file " + perSutYaml, FaultType.FaultSource.MINED);
            for (FaultType ft : overlay) {
                merged.put(ft.id(), ft);
            }
        } catch (IOException ioe) {
            throw new RuntimeException(
                    "FaultTypeRegistry: failed to read overlay file " + perSutYaml, ioe);
        }
        return new FaultTypeRegistry(merged);
    }

    public FaultType byId(String id) {
        return byId.get(id);
    }

    public List<FaultType> values() {
        return orderedValues;
    }

    public int size() {
        return byId.size();
    }

    /**
     * Returns {@code true} when the fault type with the given id applies to the
     * given OAS parameter type and location. Either {@code oasType} or
     * {@code location} may be {@code null}; an absent axis is unfiltered.
     * Returns {@code false} when {@code faultTypeId} is unknown.
     */
    public boolean applies(String faultTypeId, String oasType, String location) {
        FaultType ft = byId(faultTypeId);
        if (ft == null) return false;
        return matches(
                ft,
                normalizeOasType(oasType),
                location == null ? null : location.toLowerCase());
    }

    /**
     * Fault types that apply to the given OAS type and parameter location.
     * Either argument may be {@code null}, in which case that axis is unfiltered
     * — this mirrors the legacy enum's conservative "when in doubt, applicable"
     * behaviour.
     */
    public List<FaultType> applicableFor(String oasType, String location) {
        String t = normalizeOasType(oasType);
        String loc = location == null ? null : location.toLowerCase();
        List<FaultType> out = new ArrayList<>();
        for (FaultType ft : orderedValues) {
            if (matches(ft, t, loc)) out.add(ft);
        }
        return out;
    }

    // Shared predicate used by applicableFor() and ApplicabilityMatrix.applies().
    // Takes already-normalised oasType / lowercased location to avoid re-doing
    // that work per element.
    static boolean matches(FaultType ft, String normalizedOasType, String lowerLocation) {
        if (normalizedOasType != null && !ft.applicableTo().contains(normalizedOasType)) return false;
        if (lowerLocation != null && !ft.applicableLocations().contains(lowerLocation)) return false;
        return true;
    }

    // OAS-type aliases that the legacy enum accepted (int/long/double/float).
    // Folding them down here keeps the YAML clean (just the six OAS types).
    static String normalizeOasType(String oasType) {
        if (oasType == null) return null;
        String t = oasType.toLowerCase();
        switch (t) {
            case "int":
            case "long":
                return "integer";
            case "double":
            case "float":
                return "number";
            default:
                return t;
        }
    }

    // --- YAML loading -----------------------------------------------------

    private static LinkedHashMap<String, FaultType> parseClasspath(String resource, FaultType.FaultSource defaultSource) {
        try (InputStream in = FaultTypeRegistry.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new RuntimeException(
                        "FaultTypeRegistry: bundled default resource not found on classpath: " + resource);
            }
            LinkedHashMap<String, FaultType> map = new LinkedHashMap<>();
            for (FaultType ft : parseStream(in, "classpath:" + resource, defaultSource)) {
                map.put(ft.id(), ft);
            }
            return map;
        } catch (IOException ioe) {
            throw new RuntimeException(
                    "FaultTypeRegistry: failed to read bundled default resource " + resource, ioe);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<FaultType> parseStream(InputStream in, String origin, FaultType.FaultSource defaultSource) {
        Object raw;
        try {
            raw = new Yaml().load(in);
        } catch (RuntimeException re) {
            throw new RuntimeException(
                    "FaultTypeRegistry: malformed YAML in " + origin + " — " + re.getMessage(), re);
        }
        if (raw == null) {
            return Collections.emptyList();
        }
        if (!(raw instanceof Map)) {
            throw new RuntimeException(
                    "FaultTypeRegistry: " + origin + " must contain a top-level YAML map; got "
                            + raw.getClass().getSimpleName());
        }
        Object faultsNode = ((Map<?, ?>) raw).get("faults");
        if (faultsNode == null) {
            return Collections.emptyList();
        }
        if (!(faultsNode instanceof List)) {
            throw new RuntimeException(
                    "FaultTypeRegistry: " + origin + " 'faults' must be a YAML list; got "
                            + faultsNode.getClass().getSimpleName());
        }
        List<FaultType> out = new ArrayList<>();
        for (Object entry : (List<Object>) faultsNode) {
            if (!(entry instanceof Map)) {
                throw new RuntimeException(
                        "FaultTypeRegistry: " + origin + " each fault entry must be a YAML map");
            }
            out.add(toFaultType((Map<String, Object>) entry, origin, defaultSource));
        }
        log.debug("FaultTypeRegistry: loaded {} entries from {}", out.size(), origin);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static FaultType toFaultType(Map<String, Object> entry, String origin, FaultType.FaultSource defaultSource) {
        Object idObj = entry.get("id");
        if (!(idObj instanceof String) || ((String) idObj).isEmpty()) {
            throw new RuntimeException(
                    "FaultTypeRegistry: " + origin + " fault entry missing required 'id' string");
        }
        String id = (String) idObj;
        Object dnObj = entry.get("displayName");
        String displayName = dnObj instanceof String ? (String) dnObj : id;

        Set<String> applicableTo = toLowerStringSet(entry.get("applicableTo"), "applicableTo", origin, id);
        Set<String> applicableLocations = toLowerStringSet(entry.get("applicableLocations"), "applicableLocations", origin, id);

        FaultType.FaultSource source = defaultSource;
        Object srcObj = entry.get("source");
        if (srcObj instanceof String) {
            try {
                source = FaultType.FaultSource.valueOf(((String) srcObj).toUpperCase());
            } catch (IllegalArgumentException iae) {
                throw new RuntimeException(
                        "FaultTypeRegistry: " + origin + " fault '" + id + "' has unknown source '" + srcObj + "'");
            }
        }
        return new FaultType(id, displayName, applicableTo, applicableLocations, source);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> toLowerStringSet(Object node, String field, String origin, String id) {
        if (node == null) {
            return Collections.emptySet();
        }
        if (!(node instanceof List)) {
            throw new RuntimeException(
                    "FaultTypeRegistry: " + origin + " fault '" + id + "' field '" + field
                            + "' must be a YAML list");
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Object v : (List<Object>) node) {
            if (!(v instanceof String)) {
                throw new RuntimeException(
                        "FaultTypeRegistry: " + origin + " fault '" + id + "' field '" + field
                                + "' entries must be strings");
            }
            out.add(((String) v).toLowerCase());
        }
        return out;
    }
}
