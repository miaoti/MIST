package io.mist.core.generation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the bandit toggle on MistGenerator's fault-queue ranking gate.
 * Exercises {@link MistGenerator#applyBanditGate(List, boolean, UnaryOperator)}
 * which is the test seam hoisted out of
 * {@link MistGenerator#rankWithBandit(List)}; the production rank function
 * only runs when {@code mst.scheduler.bandit.enabled} is true.
 *
 * <p>The mock ranker reverses the list so the assertion distinguishes
 * "ranker ran" from "ranker skipped" trivially.
 */
public class MistGeneratorBanditGateTest {

    /** A trivial ranker that reverses the input list so test assertions
     * can verify whether the gate invoked the ranker or skipped it. */
    private static final UnaryOperator<List<String>> REVERSING = input -> {
        List<String> copy = new ArrayList<>(input);
        Collections.reverse(copy);
        return copy;
    };

    @Test
    public void banditDisabled_returnsQueueUnchanged() {
        List<String> queue = Arrays.asList("a", "b", "c");
        List<String> result = MistGenerator.applyBanditGate(queue, /*banditEnabled*/false, REVERSING);
        assertEquals("disabled bandit preserves insertion order",
                Arrays.asList("a", "b", "c"), result);
    }

    @Test
    public void banditEnabled_invokesRanker() {
        List<String> queue = Arrays.asList("a", "b", "c");
        List<String> result = MistGenerator.applyBanditGate(queue, /*banditEnabled*/true, REVERSING);
        assertEquals("enabled bandit hands the queue to the ranker",
                Arrays.asList("c", "b", "a"), result);
    }

    @Test
    public void singletonQueue_skipsRankerIrrespectiveOfToggle() {
        List<String> single = Collections.singletonList("only");
        // Even with bandit enabled, a one-item queue is returned unchanged
        // (preserving the early-exit behaviour the original method had).
        assertSame("size==1 short-circuits identity",
                single, MistGenerator.applyBanditGate(single, true, REVERSING));
        assertSame("size==1 short-circuits identity (disabled too)",
                single, MistGenerator.applyBanditGate(single, false, REVERSING));
    }

    @Test
    public void emptyQueue_returnsItself() {
        List<String> empty = Collections.emptyList();
        assertSame(empty, MistGenerator.applyBanditGate(empty, true, REVERSING));
        assertSame(empty, MistGenerator.applyBanditGate(empty, false, REVERSING));
    }

    @Test
    public void nullQueue_returnsNull() {
        assertNull(MistGenerator.applyBanditGate(null, true, REVERSING));
        assertNull(MistGenerator.applyBanditGate(null, false, REVERSING));
    }

    @Test
    public void disabledGate_doesNotInvokeRanker() {
        // The ranker would NPE on a non-empty list; gate must shortcut first.
        UnaryOperator<List<String>> shouldNotRun = input -> {
            throw new AssertionError("ranker invoked despite gate=off");
        };
        List<String> queue = Arrays.asList("a", "b");
        List<String> result = MistGenerator.applyBanditGate(queue, false, shouldNotRun);
        assertEquals(queue, result);
    }

    @Test
    public void enabledGate_invokesRanker_onMultiItem() {
        List<String> queue = Arrays.asList("x", "y", "z", "w");
        List<String> result = MistGenerator.applyBanditGate(queue, true, REVERSING);
        assertTrue("ranker actually produced a different ordering",
                !result.equals(queue));
    }
}
