package io.mist.core.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class Timer {

    private static Map<String, List<Long>> counters = new HashMap<>();

    public static Map<String, List<Long>> getCounters() {
        return counters;
    }

    public static void resetCounters() { counters = new HashMap<>(); }

    public static void startCounting(TestStep step) {
        counters.putIfAbsent(step.name, new ArrayList<>());
        List<Long> stepMeasures = counters.get(step.name);
        if (stepMeasures.size() > 0 && stepMeasures.get(stepMeasures.size()-1) < 0)
            stepMeasures.remove(stepMeasures.size() - 1);
//            throw new IllegalStateException("A timer of the same type can only be started once before it's stopped.");
        stepMeasures.add(-new Date().getTime());
    }

    public static void stopCounting(TestStep step) {
        Long stopTime = new Date().getTime();
        List<Long> stepMeasures = counters.get(step.name);
        // Defensive: a stopCounting() with no matching startCounting() is a stats glitch,
        // not a correctness problem — silently skip rather than crash the main thread
        // at the END of the run (which would block fault-detection report generation
        // and the final summary).
        if (stepMeasures == null || stepMeasures.isEmpty()) return;
        stepMeasures.set(stepMeasures.size()-1, stopTime+stepMeasures.get(stepMeasures.size()-1));
    }

    public static void exportToCSV(String path, Integer iterations) {
        Path csv = Paths.get(path);
        if (Files.exists(csv)) return; // write-once

        StringBuilder header = new StringBuilder();
        boolean first = true;
        for (String counterName : counters.keySet()) {
            if (first) { header.append(counterName); first = false; }
            else { header.append(",").append(counterName); }
        }
        try {
            if (csv.getParent() != null) Files.createDirectories(csv.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(csv,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                w.write(header.toString());
                w.newLine();
                for (int i = 0; i < iterations; i++) {
                    w.write(buildRow(i));
                    w.newLine();
                }
            }
        } catch (IOException e) {
            // CSV stats are best-effort; never block the run if the path is
            // unwritable. Log via stderr only because Timer is mist-core
            // util and has no logger of its own.
            System.err.println("Timer.exportToCSV: failed to write " + path + ": " + e.getMessage());
        }
    }

    private static String buildRow(int i) {
        StringBuilder row = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, List<Long>> entry : counters.entrySet()) {
            Long value = entry.getKey().equals("Whole process")
                    ? entry.getValue().get(0)
                    : entry.getValue().get(i);
            if (first) { row.append(value); first = false; }
            else { row.append(",").append(value); }
        }
        return row.toString();
    }

    public enum TestStep {
        TEST_CASE_GENERATION("Test case generation"),
        TEST_SUITE_GENERATION("Test suite generation"),
        TEST_SUITE_EXECUTION("Test suite execution"),
        ALL("Whole process");

        private String name;

        TestStep(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
