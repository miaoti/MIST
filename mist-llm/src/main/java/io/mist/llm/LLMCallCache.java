package io.mist.llm;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * On-disk JSON cache mapping (model, backend, prompt, sampling settings) to
 * the LLM response string. When {@code -Drandom.seed=<n>} is set, lookups
 * short-circuit backend HTTP calls so seeded reruns are byte-deterministic;
 * unseeded runs still write through so a later seeded run can replay them.
 *
 * <p>The cache file path is governed by the {@code mist.llm.cache.path}
 * system property; default is {@code .mist/llm-call-cache.json}. The
 * directory is created on first write. The file format is a single JSON
 * object mapping {@code sha256(key)} to the response string.
 *
 * <p>Writes are buffered through a debounced background flush (5 s window)
 * with an atomic temp-file rename, and the shutdown hook performs one final
 * flush so in-flight entries land on disk before the JVM exits.
 *
 * <p>Loading errors (file exists but is not parseable JSON) throw a
 * RuntimeException at startup rather than silently falling back to an empty
 * cache — corrupt determinism state must be surfaced, not masked.
 */
public class LLMCallCache {

    private static final Logger logger = LogManager.getLogger(LLMCallCache.class);

    private static final String DEFAULT_PATH = ".mist/llm-call-cache.json";
    private static final String KEY_DELIM = "||";
    private static final long FLUSH_DEBOUNCE_SECONDS = 5;

    private static volatile LLMCallCache instance;
    private static final Object INSTANCE_LOCK = new Object();

    private final Path cacheFile;
    private final ConcurrentHashMap<String, String> entries = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Object flushLock = new Object();
    private ScheduledFuture<?> pendingFlush;

    private LLMCallCache(Path cacheFile) {
        this.cacheFile = cacheFile;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LLMCallCache-flush");
            t.setDaemon(true);
            return t;
        });
        loadFromDisk();
    }

    /**
     * Lazy singleton. The cache file path is sourced from the
     * {@code mist.llm.cache.path} system property (default
     * {@value #DEFAULT_PATH}) — the same key the adapter's MstConfig used to
     * read for us, kept here as a direct {@code System.getProperty} read so
     * the mist-llm module carries no compile-time edge to mist-restest-adapter.
     */
    public static LLMCallCache getInstance() {
        LLMCallCache local = instance;
        if (local == null) {
            synchronized (INSTANCE_LOCK) {
                local = instance;
                if (local == null) {
                    String path = System.getProperty("mist.llm.cache.path", DEFAULT_PATH);
                    local = new LLMCallCache(Paths.get(path));
                    // Register the shutdown hook once, only for the production
                    // singleton. forTesting() instances skip it so a test run
                    // doesn't accumulate one hook (and one flush against a
                    // disposed temp path) per case.
                    Runtime.getRuntime().addShutdownHook(
                            new Thread(local::flush, "LLMCallCache-shutdown"));
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Package-private constructor for tests that need to control the cache
     * file path and bypass the JVM-wide singleton. No shutdown hook is
     * registered.
     */
    static LLMCallCache forTesting(String path) {
        return new LLMCallCache(Paths.get(path));
    }

    /**
     * Compute the SHA-256 hex digest of the joined call descriptor. The
     * {@code ||} delimiter prevents substring-boundary collisions between
     * adjacent fields.
     */
    public static String key(String modelType, String modelName, String backend,
                             String systemPrompt, String userPrompt,
                             double temperature, int maxTokens) {
        StringBuilder sb = new StringBuilder(512);
        sb.append(nullSafe(modelType)).append(KEY_DELIM)
          .append(nullSafe(modelName)).append(KEY_DELIM)
          .append(nullSafe(backend)).append(KEY_DELIM)
          .append(nullSafe(systemPrompt)).append(KEY_DELIM)
          .append(nullSafe(userPrompt)).append(KEY_DELIM)
          .append(temperature).append(KEY_DELIM)
          .append(maxTokens);
        return sha256Hex(sb.toString());
    }

    /** Return the cached response for {@code key} or {@code null} if absent. */
    public String get(String key) {
        if (key == null) return null;
        return entries.get(key);
    }

    /**
     * Insert or overwrite a cache entry. The in-memory map is updated
     * immediately; the on-disk JSON is rewritten after a debounce window.
     */
    public void put(String key, String value) {
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

    // --- internals -----------------------------------------------------

    private void loadFromDisk() {
        if (!Files.exists(cacheFile)) {
            logger.info("LLMCallCache: loaded 0 entries from {} (cold start, file does not yet exist)", cacheFile);
            return;
        }
        try {
            String content = new String(Files.readAllBytes(cacheFile), StandardCharsets.UTF_8);
            if (content.trim().isEmpty()) {
                logger.info("LLMCallCache: loaded 0 entries from {} (empty file)", cacheFile);
                return;
            }
            JSONObject obj;
            try {
                obj = new JSONObject(content);
            } catch (JSONException jsonEx) {
                throw new RuntimeException(
                    "LLMCallCache: corrupt JSON at " + cacheFile +
                    " — refusing to start with an empty cache because that would silently mask "
                    + "lost determinism state. Inspect or delete the file and rerun.", jsonEx);
            }
            for (String k : obj.keySet()) {
                entries.put(k, obj.getString(k));
            }
            logger.info("LLMCallCache: loaded {} entries from {}", entries.size(), cacheFile);
        } catch (IOException ioe) {
            throw new RuntimeException(
                "LLMCallCache: failed to read " + cacheFile + " at startup", ioe);
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
        // Snapshot the entries under the flushLock so the write captures a
        // consistent view even if put() calls race in from other threads.
        JSONObject obj = new JSONObject();
        for (java.util.Map.Entry<String, String> e : entries.entrySet()) {
            obj.put(e.getKey(), e.getValue());
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
                // Some filesystems (notably tmpfs on certain Docker overlays)
                // do not support ATOMIC_MOVE across directories. Fall back to
                // a plain replace — still safer than partial overwrite.
                Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.info("LLMCallCache: flushed {} entries to {}", entries.size(), cacheFile);
        } catch (IOException ioe) {
            logger.error("LLMCallCache: failed to flush to {}: {}", cacheFile, ioe.getMessage(), ioe);
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException nsae) {
            // SHA-256 is guaranteed by the JLS; this branch is unreachable on a sane JVM.
            throw new IllegalStateException("SHA-256 not available on this JVM", nsae);
        }
    }
}
