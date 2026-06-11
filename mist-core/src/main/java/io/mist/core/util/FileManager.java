package io.mist.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

/**
 * Minimal file utility helpers — the only static methods MIST consumers
 * need are {@link #createDir(String)} and {@link #deleteDir(String)}.
 * Lifted from the deleted mist-restest-adapter's RESTest util.
 */
public final class FileManager {

    private FileManager() {}

    public static boolean checkIfExists(String path) {
        return path != null && Files.exists(Paths.get(path));
    }

    public static void createDir(String path) {
        if (path == null) return;
        try {
            Files.createDirectories(Paths.get(path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + path, e);
        }
    }

    public static void deleteDir(String path) {
        if (path == null) return;
        Path p = Paths.get(path);
        if (!Files.exists(p)) return;
        try {
            try (var stream = Files.walk(p)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete directory: " + path, e);
        }
    }

    public static void deleteFile(String path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + path, e);
        }
    }

    public static void createFileIfNotExists(String path) {
        if (path == null) return;
        Path p = Paths.get(path);
        if (Files.exists(p)) return;
        try {
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.createFile(p);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create file: " + path, e);
        }
    }
}
