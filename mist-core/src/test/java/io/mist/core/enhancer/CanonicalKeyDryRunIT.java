package io.mist.core.enhancer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assume.assumeTrue;

/**
 * Read-only "dry run" of the canonical-key dedup over a real
 * {@code failed-tests.json} produced by a prior run. No LLM calls, no disk
 * writes — just feeds every entry through
 * {@link TestCaseEnhancer#canonicalKey(FailedTestResult)} and reports how many
 * unique groups result.
 *
 * <p>Usage:
 * <pre>
 *   mvn test -pl mist-core \
 *     -Dtest=CanonicalKeyDryRunIT \
 *     -Dfailed.tests.json=/home/.../target/enhancer/42/round-0/failed-tests.json
 * </pre>
 *
 * <p>If the system property is unset the test self-skips so the regular CI run
 * stays unaffected.
 */
public class CanonicalKeyDryRunIT {

    @Test
    public void analyzeRealFailedTestsFile() throws Exception {
        String path = System.getProperty("failed.tests.json");
        assumeTrue(
            "Skipping CanonicalKeyDryRunIT — pass -Dfailed.tests.json=/path/to/failed-tests.json to enable",
            path != null && !path.trim().isEmpty());

        File file = new File(path);
        assumeTrue("file does not exist: " + path, file.exists() && file.isFile());

        ObjectMapper mapper = new ObjectMapper()
                .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
                .configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        System.out.println("=== CanonicalKeyDryRunIT ===");
        System.out.println("Loading: " + path + " (" + file.length() + " bytes)");
        long t0 = System.currentTimeMillis();
        List<FailedTestResult> all = Arrays.asList(mapper.readValue(file, FailedTestResult[].class));
        long t1 = System.currentTimeMillis();
        System.out.println("Loaded " + all.size() + " entries in " + (t1 - t0) + "ms");

        TestCaseEnhancer enhancer = new TestCaseEnhancer(null);

        // Bucket by negative/positive
        long neg = all.stream().filter(FailedTestResult::isNegativeTest).count();
        long pos = all.size() - neg;
        System.out.println("Split: " + neg + " negative, " + pos + " positive");

        // === Apply canonical key to NEGATIVES (dedup target) ===
        Map<String, Integer> groupCounts = new LinkedHashMap<>();
        Map<String, String> exampleByGroup = new LinkedHashMap<>();
        long tKey0 = System.currentTimeMillis();
        for (FailedTestResult ft : all) {
            if (!ft.isNegativeTest()) continue;
            String k = enhancer.canonicalKey(ft);
            groupCounts.merge(k, 1, Integer::sum);
            exampleByGroup.putIfAbsent(k, ft.getTestMethodName());
        }
        long tKey1 = System.currentTimeMillis();
        System.out.println("Computed " + neg + " canonical keys in " + (tKey1 - tKey0) + "ms");

        int uniqueGroups = groupCounts.size();
        double reduction = neg == 0 ? 0 : (double) neg / Math.max(1, uniqueGroups);
        System.out.println();
        System.out.println("=== NEGATIVE-TEST DEDUP ANALYSIS ===");
        System.out.println("  Total negative tests:    " + neg);
        System.out.println("  Unique canonical groups: " + uniqueGroups);
        System.out.printf ("  Reduction factor:        %.1fx%n", reduction);
        System.out.printf ("  LLM calls saved:         %d (%.1f%%)%n",
                neg - uniqueGroups, (neg == 0 ? 0 : 100.0 * (neg - uniqueGroups) / neg));

        // === Top 10 groups by size ===
        List<Map.Entry<String, Integer>> sorted = new java.util.ArrayList<>(groupCounts.entrySet());
        sorted.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed());
        System.out.println();
        System.out.println("=== TOP 10 LARGEST GROUPS ===");
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            Map.Entry<String, Integer> e = sorted.get(i);
            String example = exampleByGroup.getOrDefault(e.getKey(), "?");
            System.out.printf("  %5d variants → 1 LLM call  |  example: %s%n",
                e.getValue(), example);
        }

        // === Distribution histogram (size buckets) ===
        int[] buckets = new int[7];
        int[] cumVariants = new int[7];
        // buckets: 1, 2-4, 5-9, 10-49, 50-99, 100-299, 300+
        for (Map.Entry<String, Integer> e : groupCounts.entrySet()) {
            int s = e.getValue();
            int b = (s == 1) ? 0
                  : (s <= 4) ? 1
                  : (s <= 9) ? 2
                  : (s <= 49) ? 3
                  : (s <= 99) ? 4
                  : (s <= 299) ? 5
                  : 6;
            buckets[b]++;
            cumVariants[b] += s;
        }
        String[] labels = {"1", "2-4", "5-9", "10-49", "50-99", "100-299", "300+"};
        System.out.println();
        System.out.println("=== GROUP-SIZE DISTRIBUTION ===");
        System.out.printf("  %-10s %10s %10s%n", "bucket", "#groups", "#variants");
        for (int i = 0; i < buckets.length; i++) {
            System.out.printf("  %-10s %10d %10d%n", labels[i], buckets[i], cumVariants[i]);
        }
        // Sanity: cumulative variants must match total
        int sumVariants = 0;
        for (int n : cumVariants) sumVariants += n;
        System.out.println("  ──────────────────────────────────");
        System.out.printf("  %-10s %10s %10d%n", "TOTAL", uniqueGroups + "", sumVariants);

        // === Sanity assertion: dedup must actually compress something ===
        // For the 5/25 run we know the LLM produced near-identical recipes
        // across same-scenario+fault variants. Expect at least 2x compression.
        if (neg >= 1000) {
            org.junit.Assert.assertTrue(
                "Expected dedup factor >= 2x on a real failed-tests sample, got " + reduction + "x",
                reduction >= 2.0);
        }
    }
}
