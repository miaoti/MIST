package io.mist.core.fault;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Basic enqueue/dequeue and round-robin semantics for
 * {@link MistInvalidInputPool}. Mirrors the legacy pool's contract; the only
 * difference is the inner key (now {@code FaultType.id} string, not the enum).
 */
public class MistInvalidInputPoolTest {

    private FaultTypeRegistry registry;
    private List<FaultType> rotation;

    @Before
    public void setUp() {
        registry = FaultTypeRegistry.loadDefault();
        rotation = Arrays.asList(
                registry.byId("BOUNDARY_VIOLATION"),
                registry.byId("OVERFLOW"),
                registry.byId("NULL_INPUT"));
    }

    @Test
    public void emptyPoolReturnsNullAndReportsNotHasNext() {
        MistInvalidInputPool pool = new MistInvalidInputPool("p", "string", rotation);
        assertFalse(pool.hasNextRoundRobin());
        assertNull(pool.getNextRoundRobin());
        assertEquals(0, pool.getTotalCount());
    }

    @Test
    public void roundRobinRotatesAcrossTypesAndDrainsValues() {
        MistInvalidInputPool pool = new MistInvalidInputPool("p", "string", rotation);
        pool.addValue("BOUNDARY_VIOLATION", "B1");
        pool.addValue("BOUNDARY_VIOLATION", "B2");
        pool.addValue("OVERFLOW", "O1");
        pool.addValue("NULL_INPUT", null);

        Set<Object> drained = new HashSet<>();
        Set<String> typesSeen = new HashSet<>();
        int expectedTotal = 4;
        for (int i = 0; i < expectedTotal; i++) {
            assertTrue("pool must have a next value at iteration " + i, pool.hasNextRoundRobin());
            Object v = pool.getNextRoundRobin();
            drained.add(v);
            typesSeen.add(pool.getLastSelectedTypeId());
        }
        assertFalse("pool must be exhausted after draining " + expectedTotal + " values",
                pool.hasNextRoundRobin());
        assertEquals(4, drained.size());
        assertEquals(3, typesSeen.size());
        assertTrue(typesSeen.contains("BOUNDARY_VIOLATION"));
        assertTrue(typesSeen.contains("OVERFLOW"));
        assertTrue(typesSeen.contains("NULL_INPUT"));
    }

    @Test
    public void roundRobinFirstThreeYieldDistinctTypes() {
        MistInvalidInputPool pool = new MistInvalidInputPool("p", "string", rotation);
        pool.addValue("BOUNDARY_VIOLATION", "B1");
        pool.addValue("OVERFLOW", "O1");
        pool.addValue("NULL_INPUT", "N1");

        Set<String> firstThreeTypes = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            pool.getNextRoundRobin();
            firstThreeTypes.add(pool.getLastSelectedTypeId());
        }
        assertEquals("first three picks must rotate across all three types",
                3, firstThreeTypes.size());
    }

    @Test
    public void nullStoredValueIsDistinguishableFromExhaustion() {
        MistInvalidInputPool pool = new MistInvalidInputPool("p", "string", rotation);
        pool.addValue("NULL_INPUT", null);
        assertTrue(pool.hasNextRoundRobin());
        Object v = pool.getNextRoundRobin();
        assertNull("NULL_INPUT slot must yield a literal null", v);
        assertNotNull("but lastSelectedTypeId must be set so callers can tell exhaustion apart",
                pool.getLastSelectedTypeId());
        assertFalse(pool.hasNextRoundRobin());
    }

    @Test
    public void resetUsageReplaysCycle() {
        MistInvalidInputPool pool = new MistInvalidInputPool("p", "string", rotation);
        pool.addValue("BOUNDARY_VIOLATION", "B1");
        pool.getNextRoundRobin();
        assertFalse(pool.hasNextRoundRobin());
        pool.resetUsage();
        assertTrue(pool.hasNextRoundRobin());
        assertEquals("B1", pool.getNextRoundRobin());
    }

    @Test
    public void getCountForType() {
        MistInvalidInputPool pool = new MistInvalidInputPool("p", "string", rotation);
        pool.addValue("OVERFLOW", 1);
        pool.addValue("OVERFLOW", 2);
        assertEquals(2, pool.getCountForType("OVERFLOW"));
        assertEquals(0, pool.getCountForType("NULL_INPUT"));
    }

    @Test
    public void getRandomValueIsDrawnFromPool() {
        MistInvalidInputPool pool = new MistInvalidInputPool("p", "string", rotation);
        pool.addValue("OVERFLOW", "O1");
        pool.addValue("BOUNDARY_VIOLATION", "B1");
        Random random = new Random(42L);
        Set<Object> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            seen.add(pool.getRandomValue(random));
        }
        assertTrue(seen.contains("O1") || seen.contains("B1"));
    }

    @Test
    public void unknownFaultTypeIdRejected() {
        MistInvalidInputPool pool = new MistInvalidInputPool("p", "string", rotation);
        try {
            pool.addValue("NOT_IN_ROTATION", "x");
            fail("unknown fault type id must be rejected");
        } catch (IllegalArgumentException expected) {
            // pass
        }
    }
}
