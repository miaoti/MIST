package io.mist.core.smart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-signature cache of {@link ParameterErrorAnalyzer} LLM verdicts.
 * <p>
 * Keyed by {@code (service|operation|status|exceptionType)} — the dimensions
 * the LLM actually sees in the prompt. On the first failure with a given
 * signature the LLM is queried and its verdict is stored; subsequent failures
 * with the same signature reuse the cached verdict, eliminating redundant
 * LLM calls.
 * <p>
 * Both positive verdicts ("YES, parameter {@code X} is wrong with type
 * {@code Y}") and negative verdicts ("NO, not a parameter-related error")
 * are cached. Negative verdicts are roughly half of all LLM answers on
 * observed runs, so caching them matters.
 * <p>
 * Thread-safe via {@link ConcurrentHashMap}; persisted to a JSON file so
 * the cache survives across enhancement rounds within a JVM run.
 */
public class ParameterErrorAnalysisCache {

    private static final Logger logger = LogManager.getLogger(ParameterErrorAnalysisCache.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, ParameterErrorAnalysisCache> instances = new ConcurrentHashMap<>();

    private final String filePath;
    private final ConcurrentHashMap<String, CachedVerdict> verdicts = new ConcurrentHashMap<>();

    private ParameterErrorAnalysisCache(String filePath) {
        this.filePath = filePath;
        load();
    }

    /**
     * Get (or create) a singleton cache instance backed by the given file path.
     */
    public static synchronized ParameterErrorAnalysisCache getInstance(String filePath) {
        return instances.computeIfAbsent(filePath, ParameterErrorAnalysisCache::new);
    }

    /**
     * Cached LLM verdict for one failure signature.
     * <p>
     * When {@code isParameterError} is {@code true}, {@code parameterName}
     * and {@code errorType} hold the LLM's answer. When {@code false},
     * both are {@code null}.
     */
    public static class CachedVerdict {
        private boolean isParameterError;
        private String parameterName;
        private String errorType;

        public CachedVerdict() {}

        public CachedVerdict(boolean isParameterError, String parameterName, String errorType) {
            this.isParameterError = isParameterError;
            this.parameterName = parameterName;
            this.errorType = errorType;
        }

        public boolean isParameterError() { return isParameterError; }
        public void setParameterError(boolean v) { this.isParameterError = v; }
        public String getParameterName() { return parameterName; }
        public void setParameterName(String n) { this.parameterName = n; }
        public String getErrorType() { return errorType; }
        public void setErrorType(String t) { this.errorType = t; }
    }

    /**
     * Build the cache key from failure attributes that determine the LLM verdict.
     * Empirically, (service, operation, status, exception) gave a 99.4% hit rate
     * over a 1,815-call sample.
     */
    public static String buildKey(String service, String operation, int status, String exceptionType) {
        return safe(service) + "|" + safe(operation) + "|" + status + "|" + safe(exceptionType);
    }

    private static String safe(String s) {
        return (s == null || s.isEmpty()) ? "none" : s;
    }

    public Optional<CachedVerdict> get(String key) {
        CachedVerdict v = verdicts.get(key);
        if (v != null) {
            logger.debug("PEA cache HIT for key={}: isParamError={}, param={}, type={}",
                    key, v.isParameterError(), v.getParameterName(), v.getErrorType());
        }
        return Optional.ofNullable(v);
    }

    /**
     * Endpoint-level peek that ignores the exception-type dimension. Returns
     * a representative cached verdict for the {@code (service, operation,
     * status)} triple if and only if every cached verdict under that prefix
     * agrees on {@code isParameterError}, {@code parameterName}, and
     * {@code errorType}.
     * <p>
     * The caller uses this to skip a full Jaeger-trace fetch (typically
     * ~7s per call): the exception-type dimension is the only field of the
     * exact cache key that requires the trace, and in practice the same
     * endpoint failing with the same status almost always carries the same
     * underlying parameter error. When the cached verdicts under the prefix
     * disagree (a real ambiguity the exception type was disambiguating),
     * this method returns empty and the caller falls back to the full
     * trace-aware lookup.
     */
    public Optional<CachedVerdict> peekByEndpoint(String service, String operation, int status) {
        String prefix = safe(service) + "|" + safe(operation) + "|" + status + "|";
        CachedVerdict representative = null;
        for (Map.Entry<String, CachedVerdict> e : verdicts.entrySet()) {
            if (!e.getKey().startsWith(prefix)) continue;
            CachedVerdict v = e.getValue();
            if (representative == null) {
                representative = v;
                continue;
            }
            // Disagreement -> ambiguous; force the caller to fetch the trace.
            if (v.isParameterError() != representative.isParameterError()) return Optional.empty();
            if (!java.util.Objects.equals(v.getParameterName(), representative.getParameterName())) return Optional.empty();
            if (!java.util.Objects.equals(v.getErrorType(), representative.getErrorType())) return Optional.empty();
        }
        if (representative != null) {
            logger.debug("PEA cache endpoint-PEEK HIT for {}|{}|{}: isParamError={}, param={}, type={} (skipping Jaeger fetch)",
                    service, operation, status,
                    representative.isParameterError(),
                    representative.getParameterName(),
                    representative.getErrorType());
        }
        return Optional.ofNullable(representative);
    }

    public void put(String key, CachedVerdict verdict) {
        verdicts.put(key, verdict);
        save();
        logger.info("PEA cache STORE for key={}: isParamError={}, param={}, type={}",
                key, verdict.isParameterError(), verdict.getParameterName(), verdict.getErrorType());
    }

    public int size() {
        return verdicts.size();
    }

    private void load() {
        Path path = Paths.get(filePath);
        // One-shot migration: if the configured path lives under a .mist/
        // directory and a sibling target/<filename> cache exists, move that
        // legacy file under the new .mist/ location so accumulated LLM
        // verdicts survive the cache-location switch. Gated on the parent
        // directory being named ".mist" so user-overridden cache paths do
        // not accidentally swallow same-named files under target/. Once
        // the legacy file is gone, this branch is a cheap Files.exists()
        // no-op on every subsequent run.
        Path legacy = legacyTargetSiblingPath(path);
        if (legacy != null && !Files.exists(path) && Files.exists(legacy)) {
            try {
                Path parent = path.getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.move(legacy, path, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Migrated cache: {} -> {}", legacy, path);
            } catch (IOException e) {
                logger.warn("Failed to migrate legacy cache {} -> {}: {}", legacy, path, e.getMessage());
            }
        }
        if (!Files.exists(path)) {
            logger.info("No existing PEA cache at {}; starting fresh", filePath);
            return;
        }
        try (Reader reader = new FileReader(filePath)) {
            Type mapType = new TypeToken<Map<String, CachedVerdict>>() {}.getType();
            Map<String, CachedVerdict> loaded = GSON.fromJson(reader, mapType);
            if (loaded != null) {
                verdicts.putAll(loaded);
                logger.info("Loaded {} PEA cache entries from {}", verdicts.size(), filePath);
            }
        } catch (Exception e) {
            logger.warn("Could not load PEA cache from {}: {}", filePath, e.getMessage());
        }
    }

    private synchronized void save() {
        try {
            Path parent = Paths.get(filePath).getParent();
            if (parent != null) Files.createDirectories(parent);
            try (Writer writer = new FileWriter(filePath)) {
                GSON.toJson(verdicts, writer);
            }
        } catch (IOException e) {
            logger.warn("Could not save PEA cache to {}: {}", filePath, e.getMessage());
        }
    }

    /**
     * Compute the legacy {@code target/<filename>} location that a cache file
     * configured at {@code path} would have lived in before the migration to
     * {@code .mist/}. Returns
     * {@code null} if {@code path} does not have the canonical
     * {@code <prefix>/.mist/<filename>} shape — i.e., the user has overridden
     * the cache location to something custom and we should not auto-move
     * anything for them.
     */
    static Path legacyTargetSiblingPath(Path path) {
        Path parent = path.getParent();
        if (parent == null) return null;
        Path parentName = parent.getFileName();
        if (parentName == null || !".mist".equals(parentName.toString())) {
            return null;
        }
        Path grandparent = parent.getParent();
        if (grandparent == null) {
            return Paths.get("target", path.getFileName().toString());
        }
        return grandparent.resolve("target").resolve(path.getFileName());
    }
}
