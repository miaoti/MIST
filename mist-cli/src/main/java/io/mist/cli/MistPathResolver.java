package io.mist.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;

/**
 * Resolves relative INPUT path values in a MIST {@code .properties} file
 * against the directory containing that {@code .properties} file, so
 * generation runs the same way regardless of the user's current working
 * directory (CLI from repo root, IntelliJ play-button from a module dir,
 * scheduled CI runs, ...).
 *
 * <p>OUTPUT path keys ({@code test.target.dir}, {@code allure.results.dir},
 * {@code data.tests.dir}, the various {@code .mist/*-cache.json} keys, etc.)
 * are intentionally left untouched — they follow Maven's project-relative
 * convention and the {@code -Dkey=val} CLI override stays well-defined.
 *
 * <p>The list of input keys is explicit rather than a heuristic so future
 * additions are reviewed once and the behaviour stays predictable. Adding
 * a new input-path key means appending it to {@link #INPUT_PATH_KEYS}.
 */
public final class MistPathResolver {

    /** Keys whose value is read as an existing file or directory. */
    private static final Set<String> INPUT_PATH_KEYS = Set.of(
            // core .properties
            "mst.config.path",
            "oas.path",
            "conf.path",
            "trace.file.path",
            "fault.detection.injected.faults.path",
            // MST .properties — these must include every key listed in
            // MST_INPUT_PATH_KEYS below, otherwise MistMain's
            // resolveInputPaths(...) leaves them CWD-relative and the
            // generator hits "file not found" when the user runs from
            // the project root rather than the module directory.
            "input.fetch.registry.path",
            "smart.input.fetch.registry.path",
            "smart.input.fetch.openapi.spec.path",
            "root.api.registry.path",
            "noun.map.path",
            "fault.types.path",
            "mist.fault.types.path",
            "seed.trace.labels.path",
            "mist.tso.store.path"
    );

    /** Subset of {@link #INPUT_PATH_KEYS} that live in the MST .properties file. */
    public static final Set<String> MST_INPUT_PATH_KEYS = Set.of(
            "input.fetch.registry.path",
            "smart.input.fetch.registry.path",
            "smart.input.fetch.openapi.spec.path",
            "root.api.registry.path",
            "noun.map.path",
            "fault.types.path",
            "mist.fault.types.path",
            "seed.trace.labels.path",
            "mist.tso.store.path",
            "fault.detection.injected.faults.path"
    );

    /**
     * Resolves a single relative path string against {@code baseDir}.
     * Null, empty, or already-absolute values pass through unchanged.
     */
    public static String resolveAgainst(Path baseDir, String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return value;
        if (baseDir == null) return value;
        Path p = Paths.get(trimmed);
        if (p.isAbsolute()) return value;
        return baseDir.toAbsolutePath().normalize().resolve(trimmed).normalize().toString();
    }

    private MistPathResolver() {}

    /**
     * Rewrites every relative {@link #INPUT_PATH_KEYS} value in {@code props}
     * to its absolute equivalent under {@code baseDir}. Absent keys, blank
     * values and already-absolute paths are left alone. The {@code props}
     * argument is mutated in place.
     *
     * @param props   the loaded properties bag
     * @param baseDir the directory the {@code .properties} file lives in;
     *                values relative to this directory will be resolved
     */
    public static void resolveInputPaths(Properties props, Path baseDir) {
        if (props == null || baseDir == null) return;
        Path absBase = baseDir.toAbsolutePath().normalize();
        for (String key : INPUT_PATH_KEYS) {
            String raw = props.getProperty(key);
            if (raw == null) continue;
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;
            Path p = Paths.get(trimmed);
            if (p.isAbsolute()) continue;
            props.setProperty(key, absBase.resolve(trimmed).normalize().toString());
        }
    }

    /**
     * Same as {@link #resolveInputPaths(Properties, Path)} but takes the
     * {@code .properties} file itself; the base directory is its parent
     * (or the JVM's {@code user.dir} when the file has no parent).
     */
    public static void resolveInputPaths(Properties props, java.io.File propertiesFile) {
        if (props == null || propertiesFile == null) return;
        Path parent = propertiesFile.toPath().toAbsolutePath().normalize().getParent();
        if (parent == null) parent = Paths.get(System.getProperty("user.dir"));
        resolveInputPaths(props, parent);
    }
}
