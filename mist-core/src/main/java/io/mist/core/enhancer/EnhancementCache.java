package io.mist.core.enhancer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * On-disk JSON cache for {@link TestCaseEnhancer} results, keyed by a canonical
 * scenario-fault-error fingerprint (not by raw prompt hash). Survives JVM
 * restarts so subsequent runs skip the LLM entirely for canonically-equivalent
 * failed tests.
 *
 * <p>Mirrors the {@code LLMCallCache} pattern in {@code mist-llm}: lazy
 * singleton, debounced background flush with atomic temp-file rename, and a
 * shutdown hook that performs one final flush. Loading a corrupt file throws
 * at startup rather than masking lost cache state.
 *
 * <p>The cache file path is governed by the {@code mst.enhancer.cache.path}
 * system property; default {@code .mist/enhancement-cache.json}. Enable/disable
 * is gated by {@code mst.enhancer.cache.enabled} at the
 * {@link TestCaseEnhancer#enhanceBatch} call site, not here.
 *
 * <p>The cached value omits per-test fields (test class/method name) so a
 * single cache entry can be reused across many variants of the same scenario.
 * The enhancer rehydrates the per-test identity when distributing results.
 */
public final class EnhancementCache {

    private static final Logger logger = LogManager.getLogger(EnhancementCache.class);

    private static final String DEFAULT_PATH = ".mist/enhancement-cache.json";
    private static final long FLUSH_DEBOUNCE_SECONDS = 5;

    private static volatile EnhancementCache instance;
    private static final Object INSTANCE_LOCK = new Object();

    private final Path cacheFile;
    private final ConcurrentHashMap<String, CachedEnhancement> entries = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Object flushLock = new Object();
    private ScheduledFuture<?> pendingFlush;

    private EnhancementCache(Path cacheFile) {
        this.cacheFile = cacheFile;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EnhancementCache-flush");
            t.setDaemon(true);
            return t;
        });
        loadFromDisk();
    }

    /**
     * Lazy singleton. Cache file path comes from {@code mst.enhancer.cache.path}
     * (default {@value #DEFAULT_PATH}). Registers a shutdown hook on first use
     * so in-flight writes land on disk before JVM exit.
     */
    public static EnhancementCache getInstance() {
        EnhancementCache local = instance;
        if (local == null) {
            synchronized (INSTANCE_LOCK) {
                local = instance;
                if (local == null) {
                    String path = System.getProperty("mst.enhancer.cache.path", DEFAULT_PATH);
                    local = new EnhancementCache(Paths.get(path));
                    Runtime.getRuntime().addShutdownHook(
                            new Thread(local::flush, "EnhancementCache-shutdown"));
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Package-private constructor for tests that need to control the cache
     * file path and bypass the JVM-wide singleton. No shutdown hook registered.
     */
    static EnhancementCache forTesting(String path) {
        return new EnhancementCache(Paths.get(path));
    }

    /** Return the cached enhancement for {@code key} or empty if absent. */
    public Optional<CachedEnhancement> get(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(entries.get(key));
    }

    /** Insert/overwrite. Map updated immediately; disk write happens after debounce. */
    public void put(String key, CachedEnhancement value) {
        if (key == null || value == null) return;
        entries.put(key, value);
        scheduleFlush();
    }

    /** Force a synchronous flush of the in-memory state to disk. */
    public void flush() {
        synchronized (flushLock) {
            writeToDisk();
        }
    }

    /** Approximate count of cached entries — for logging only. */
    public int size() {
        return entries.size();
    }

    // --- internals -----------------------------------------------------

    private void loadFromDisk() {
        if (!Files.exists(cacheFile)) {
            logger.info("EnhancementCache: loaded 0 entries from {} (cold start, file does not yet exist)", cacheFile);
            return;
        }
        try {
            String content = new String(Files.readAllBytes(cacheFile), StandardCharsets.UTF_8);
            if (content.trim().isEmpty()) {
                logger.info("EnhancementCache: loaded 0 entries from {} (empty file)", cacheFile);
                return;
            }
            JSONObject obj;
            try {
                obj = new JSONObject(content);
            } catch (JSONException jsonEx) {
                throw new RuntimeException(
                    "EnhancementCache: corrupt JSON at " + cacheFile +
                    " — refusing to start with an empty cache because that would silently mask lost "
                    + "enhancement state. Inspect or delete the file and rerun.", jsonEx);
            }
            int loaded = 0;
            for (String k : obj.keySet()) {
                try {
                    entries.put(k, CachedEnhancement.fromJson(obj.getJSONObject(k)));
                    loaded++;
                } catch (JSONException entryEx) {
                    logger.warn("EnhancementCache: skipping malformed entry {}: {}", k, entryEx.getMessage());
                }
            }
            logger.info("EnhancementCache: loaded {} entries from {}", loaded, cacheFile);
        } catch (IOException ioe) {
            throw new RuntimeException(
                "EnhancementCache: failed to read " + cacheFile + " at startup", ioe);
        }
    }

    private void scheduleFlush() {
        synchronized (flushLock) {
            if (pendingFlush != null && !pendingFlush.isDone()) {
                pendingFlush.cancel(false);
            }
            pendingFlush = scheduler.schedule(this::flush, FLUSH_DEBOUNCE_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void writeToDisk() {
        JSONObject obj = new JSONObject();
        for (Map.Entry<String, CachedEnhancement> e : entries.entrySet()) {
            obj.put(e.getKey(), e.getValue().toJson());
        }
        try {
            Path parent = cacheFile.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Path tmp = cacheFile.resolveSibling(cacheFile.getFileName().toString() + ".tmp");
            Files.write(tmp, obj.toString(2).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, cacheFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException atomicEx) {
                // Some filesystems (notably tmpfs on some Docker overlays) do not
                // support ATOMIC_MOVE across directories. Fall back to plain
                // replace — still safer than partial overwrite.
                Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.info("EnhancementCache: flushed {} entries to {}", entries.size(), cacheFile);
        } catch (IOException ioe) {
            logger.error("EnhancementCache: failed to flush to {}: {}", cacheFile, ioe.getMessage(), ioe);
        }
    }

    /**
     * Serializable per-key payload. Holds only LLM-output fields; the
     * test-specific identifiers (testClassName, testMethodName) are
     * deliberately absent so one cached entry serves N variants.
     */
    public static final class CachedEnhancement {
        public Map<String, String> enhancedParameters;
        public String reasoning;

        public CachedEnhancement() {
            this.enhancedParameters = new LinkedHashMap<>();
            this.reasoning = "";
        }

        public CachedEnhancement(Map<String, String> enhancedParameters, String reasoning) {
            this.enhancedParameters = enhancedParameters != null
                    ? new LinkedHashMap<>(enhancedParameters)
                    : new LinkedHashMap<>();
            this.reasoning = reasoning != null ? reasoning : "";
        }

        JSONObject toJson() {
            JSONObject out = new JSONObject();
            JSONObject params = new JSONObject();
            for (Map.Entry<String, String> e : enhancedParameters.entrySet()) {
                params.put(e.getKey(), e.getValue() == null ? "" : e.getValue());
            }
            out.put("enhancedParameters", params);
            out.put("reasoning", reasoning == null ? "" : reasoning);
            return out;
        }

        static CachedEnhancement fromJson(JSONObject obj) {
            CachedEnhancement ce = new CachedEnhancement();
            JSONObject params = obj.optJSONObject("enhancedParameters");
            if (params != null) {
                Map<String, String> map = new LinkedHashMap<>();
                for (String k : params.keySet()) {
                    map.put(k, params.optString(k, ""));
                }
                ce.enhancedParameters = map;
            }
            ce.reasoning = obj.optString("reasoning", "");
            return ce;
        }
    }
}
