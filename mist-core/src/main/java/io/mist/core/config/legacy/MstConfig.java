package io.mist.core.config.legacy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Loader for the MST-only properties file. Keeps MST-specific keys
 * (LLM, smart fetch, jaeger, fault detection, soft-error cache, enhancer,
 * status-code exploration, root-API registry, trace merging, etc.) out of
 * the RESTest-core properties file so classic generators (RT/CBT/ART/FT/LLM)
 * never see MST configuration.
 *
 * The file is plain {@code java.util.Properties} format (same syntax as the
 * RESTest-core file). The path is supplied via the {@code mst.config.path}
 * key in the user's main properties file.
 *
 * Loaded values are also pushed to System properties via
 * {@link #applyToSystemProperties()} so that downstream readers (writers,
 * generators, the smart input fetcher, generated test code) that already
 * read via {@code System.getProperty} keep working unchanged.
 */
public final class MstConfig {

    private static final Logger logger = LogManager.getLogger(MstConfig.class.getName());

    private final String filePath;
    private final Properties properties;

    private MstConfig(String filePath, Properties properties) {
        this.filePath = filePath;
        this.properties = properties;
    }

    /**
     * Loads an MST properties file. Throws IOException if the file is
     * specified but unreadable; returns an empty config when the path is
     * null/blank so callers can opt out by simply omitting the key.
     */
    public static MstConfig load(String filePath) throws IOException {
        Properties props = new Properties();
        if (filePath == null || filePath.trim().isEmpty()) {
            return new MstConfig(null, props);
        }
        if (!Files.exists(Paths.get(filePath))) {
            throw new IOException("MST configuration file not found: " + filePath);
        }
        try (FileInputStream in = new FileInputStream(filePath)) {
            props.load(in);
        }
        logger.info("Loaded MST configuration from {} ({} keys)", filePath, props.size());
        return new MstConfig(filePath, props);
    }

    /**
     * Copy every loaded key/value pair into System properties so that
     * existing {@code System.getProperty(key)} consumers (writers,
     * generators, smart fetcher, generated tests) read MST values without
     * modification. Keys already present in System properties (e.g. set via
     * {@code -Dkey=val} on the command line) are not overwritten.
     */
    public void applyToSystemProperties() {
        int applied = 0;
        for (String key : properties.stringPropertyNames()) {
            if (System.getProperty(key) == null) {
                System.setProperty(key, properties.getProperty(key));
                applied++;
            }
        }
        logger.info("Applied {} MST properties to System (out of {} loaded)", applied, properties.size());
    }

    public String get(String key) {
        return properties.getProperty(key);
    }

    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public boolean isLoaded() {
        return filePath != null;
    }

    public String getFilePath() {
        return filePath;
    }

    public int size() {
        return properties.size();
    }
}
