package io.mist.core.bandit;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins the {@link ThompsonScheduler} contract: samples land in
 * {@code (0, 1)}, the conjugate update shifts the posterior in the
 * expected direction, and the prefix of a ranked queue concentrates
 * on high-α keys after enough evidence — the property the §III.L
 * bandit relies on for sublinear regret over the fault queue.
 */
public class ThompsonSchedulerTest {

    @Test
    public void coldSamplesAreInOpenUnitInterval() {
        ThompsonScheduler s = new ThompsonScheduler(new Random(1));
        for (int i = 0; i < 200; i++) {
            double d = s.sample("k");
            assertTrue("sample out of (0,1): " + d, d > 0.0 && d < 1.0);
        }
    }

    @Test
    public void recordSuccessIncreasesPosteriorMean() {
        ThompsonScheduler s = new ThompsonScheduler(new Random(2));
        double before = s.posteriorMean("k");
        for (int i = 0; i < 10; i++) s.recordSuccess("k");
        double after = s.posteriorMean("k");
        assertTrue("expected mean to rise after successes: " + before + " -> " + after, after > before);
    }

    @Test
    public void recordFailureDecreasesPosteriorMean() {
        ThompsonScheduler s = new ThompsonScheduler(new Random(3));
        double before = s.posteriorMean("k");
        for (int i = 0; i < 10; i++) s.recordFailure("k");
        double after = s.posteriorMean("k");
        assertTrue("expected mean to drop after failures: " + before + " -> " + after, after < before);
    }

    @Test
    public void rankPrefersHighAlphaKeysOverMultipleDraws() {
        // "good" key has 50 successes / 0 failures; "bad" has the opposite.
        // The order of any single draw is random, but over many draws the
        // good key wins the top slot the vast majority of the time.
        ThompsonScheduler s = new ThompsonScheduler(new Random(4));
        for (int i = 0; i < 50; i++) s.recordSuccess("good");
        for (int i = 0; i < 50; i++) s.recordFailure("bad");

        int goodFirst = 0;
        int trials = 500;
        List<String> keys = Arrays.asList("good", "bad");
        for (int i = 0; i < trials; i++) {
            if ("good".equals(s.rank(keys).get(0))) goodFirst++;
        }
        assertTrue("good should dominate top rank: " + goodFirst + "/" + trials,
                goodFirst > (int) (0.95 * trials));
    }

    @Test
    public void snapshotRoundTripsCounters() {
        ThompsonScheduler s = new ThompsonScheduler(new Random(5));
        s.recordSuccess("a");
        s.recordSuccess("a");
        s.recordFailure("b");
        Map<String, double[]> snap = s.snapshot();
        assertNotNull(snap);
        assertEquals(3.0, snap.get("a")[0], 1e-9);   // 1 (prior) + 2 (successes)
        assertEquals(1.0, snap.get("a")[1], 1e-9);   // 1 (prior)
        assertEquals(1.0, snap.get("b")[0], 1e-9);   // 1 (prior)
        assertEquals(2.0, snap.get("b")[1], 1e-9);   // 1 (prior) + 1 (failure)
    }

    @Test
    public void seedPopulatesCountersForReload() {
        ThompsonScheduler s = new ThompsonScheduler(new Random(6));
        s.seed("preloaded", 7.0, 3.0);
        double[] c = s.counters("preloaded");
        assertEquals(7.0, c[0], 1e-9);
        assertEquals(3.0, c[1], 1e-9);
    }

    @Test(expected = IllegalArgumentException.class)
    public void priorsMustBePositive() {
        new ThompsonScheduler(new Random(), 0.0, 1.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void seedRejectsNonPositiveCounters() {
        new ThompsonScheduler(new Random(7)).seed("k", 1.0, 0.0);
    }
}
