package io.mist.core.oracle.shape;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File-backed JSON store for learned invariant data. One entry per
 * (invariant kind, root API) key; the writer side atomically renames a
 * sibling {@code .tmp} file over the destination so partially-written state
 * is never visible to a concurrent reader.
 *
 * <p>The store path defaults to {@code .mist/trace-shape-invariants.json}
 * and can be overridden with {@code -Dmist.tso.store.path=...}.
 */
public final class ShapeInvariantStore {

    private static final Logger logger = LogManager.getLogger(ShapeInvariantStore.class);
    private static final String DEFAULT_PATH = ".mist/trace-shape-invariants.json";
    private static final String STORE_PATH_PROPERTY = "mist.tso.store.path";

    private final Path storeFile;
    private final ConcurrentHashMap<String, JsonObject> entries = new ConcurrentHashMap<>();

    public ShapeInvariantStore() {
        this(resolveDefaultPath());
    }

    public ShapeInvariantStore(Path storeFile) {
        this.storeFile = storeFile;
        loadFromDisk();
    }

    private static Path resolveDefaultPath() {
        String configured = System.getProperty(STORE_PATH_PROPERTY);
        return Paths.get(configured != null && !configured.isEmpty() ? configured : DEFAULT_PATH);
    }

    public void put(String key, JsonObject invariantData) {
        if (key == null || invariantData == null) return;
        entries.put(key, invariantData);
    }

    public JsonObject get(String key) {
        if (key == null) return null;
        return entries.get(key);
    }

    public boolean has(String key) {
        return key != null && entries.containsKey(key);
    }

    public int size() { return entries.size(); }

    public synchronized void flush() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, JsonObject> e : entries.entrySet()) {
            root.add(e.getKey(), e.getValue());
        }
        try {
            Path parent = storeFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = storeFile.resolveSibling(storeFile.getFileName().toString() + ".tmp");
            Files.write(tmp, root.toString().getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, storeFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                // Some filesystems (notably tmpfs on overlay-on-overlay) reject
                // ATOMIC_MOVE; a plain replace still beats a half-written file.
                Files.move(tmp, storeFile, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.info("ShapeInvariantStore: flushed {} entries to {}", entries.size(), storeFile);
        } catch (IOException ioe) {
            logger.error("ShapeInvariantStore: failed to flush to {}: {}", storeFile, ioe.getMessage(), ioe);
        }
    }

    private void loadFromDisk() {
        String content;
        try {
            content = new String(Files.readAllBytes(storeFile), StandardCharsets.UTF_8);
        } catch (NoSuchFileException nsfe) {
            logger.info("ShapeInvariantStore: no existing file at {}; starting empty", storeFile);
            return;
        } catch (IOException ioe) {
            logger.warn("ShapeInvariantStore: could not load from {}: {}", storeFile, ioe.getMessage());
            return;
        }
        if (content.trim().isEmpty()) return;
        try {
            JsonObject root = new JsonParser().parse(content).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                if (e.getValue().isJsonObject()) {
                    entries.put(e.getKey(), e.getValue().getAsJsonObject());
                }
            }
            logger.info("ShapeInvariantStore: loaded {} entries from {}", entries.size(), storeFile);
        } catch (RuntimeException ex) {
            logger.warn("ShapeInvariantStore: malformed content in {}: {}", storeFile, ex.getMessage());
        }
    }
}
