package io.mist.core.analysis;

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
 * Per-signature cache of {@link TraceErrorAnalyzer#generateIntelligentAnalysis}
 * LLM responses.
 * <p>
 * Keyed by the sorted set of root-cause failure signatures
 * ({@code service|operation|status|exception} per failure, joined by {@code ;;}),
 * which captures the dimensions that actually determine the LLM's diagnosis.
 * Two traces that fail in the same way reuse the same diagnostic text instead
 * of paying for a fresh LLM call each time. On the canonical benchmark run,
 * this single hot path accounted for 9,145 LLM calls and ~6 hours of LLM time
 * across a 36-hour run, with only ~10 unique failure-mode signatures.
 * <p>
 * Cached value is the final formatted output string (after
 * {@code formatLLMResponse}), so a cache hit produces byte-identical content
 * to a fresh LLM call. The cache is opaque to consumers (Allure attachments,
 * CLI prints) — no decoration or "cached" marker is added.
 * <p>
 * Thread-safe via {@link ConcurrentHashMap}; persisted to a JSON file so the
 * cache survives across enhancement rounds within a JVM run. Mirrors the
 * design of the smart-fetch parameter error analysis cache.
 */
public class IntelligentAnalysisCache {

    private static final Logger logger = LogManager.getLogger(IntelligentAnalysisCache.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, IntelligentAnalysisCache> instances = new ConcurrentHashMap<>();

    private final String filePath;
    private final ConcurrentHashMap<String, String> analyses = new ConcurrentHashMap<>();

    private IntelligentAnalysisCache(String filePath) {
        this.filePath = filePath;
        load();
    }

    /**
     * Get (or create) a singleton cache instance backed by the given file path.
     */
    public static synchronized IntelligentAnalysisCache getInstance(String filePath) {
        return instances.computeIfAbsent(filePath, IntelligentAnalysisCache::new);
    }

    public Optional<String> get(String key) {
        String v = analyses.get(key);
        if (v != null) {
            logger.debug("Intelligent-analysis cache HIT for key={}", key);
        }
        return Optional.ofNullable(v);
    }

    public void put(String key, String analysis) {
        analyses.put(key, analysis);
        save();
        logger.info("Intelligent-analysis cache STORE for key={}", key);
    }

    public int size() {
        return analyses.size();
    }

    private void load() {
        Path path = Paths.get(filePath);
        // One-shot migration: if the configured path lives under a .mist/
        // directory and a sibling target/<filename> cache exists, move that
        // legacy file under the new .mist/ location so accumulated LLM
        // diagnoses survive the cache-location switch. Gated on the parent
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
            logger.info("No existing intelligent-analysis cache at {}; starting fresh", filePath);
            return;
        }
        try (Reader reader = new FileReader(filePath)) {
            Type mapType = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loaded = GSON.fromJson(reader, mapType);
            if (loaded != null) {
                analyses.putAll(loaded);
                logger.info("Loaded {} intelligent-analysis cache entries from {}", analyses.size(), filePath);
            }
        } catch (Exception e) {
            logger.warn("Could not load intelligent-analysis cache from {}: {}", filePath, e.getMessage());
        }
    }

    private synchronized void save() {
        try {
            Path parent = Paths.get(filePath).getParent();
            if (parent != null) Files.createDirectories(parent);
            try (Writer writer = new FileWriter(filePath)) {
                GSON.toJson(analyses, writer);
            }
        } catch (IOException e) {
            logger.warn("Could not save intelligent-analysis cache to {}: {}", filePath, e.getMessage());
        }
    }

    /**
     * Compute the legacy {@code target/<filename>} location that a cache file
     * configured at {@code path} would have lived in before the migration to
     * {@code .mist/}. Returns {@code null} if {@code path} does not have the
     * canonical {@code <prefix>/.mist/<filename>} shape — i.e., the user has
     * overridden the cache location to something custom and we should not auto-move
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
