package io.mist.core.generation;

import io.mist.core.fault.FaultTypeRegistry;
import io.mist.core.fault.InvalidInputPool;
import io.mist.core.llm.ParameterInfo;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the schema-aware ENUM_VIOLATION fault type
 * ({@link HardcodedInvalidInputGenerator#generateEnumViolationInputs}). The
 * invariant under test: every emitted value is of the correct TYPE but is NOT a
 * member of the declared enum, so only enum validation — not the type check —
 * can reject it.
 */
public class EnumViolationTest {

    private final HardcodedInvalidInputGenerator gen = new HardcodedInvalidInputGenerator();

    private static ParameterInfo param(String type, List<String> enumValues) {
        ParameterInfo p = new ParameterInfo();
        p.setName("status");
        p.setType(type);
        p.setEnumValues(enumValues);
        return p;
    }

    /** Drain all values the generator put into the pool (only ENUM_VIOLATION here). */
    private static List<Object> drain(InvalidInputPool pool) {
        List<Object> out = new ArrayList<>();
        while (pool.hasNextRoundRobin()) {
            Object v = pool.getNextRoundRobin();
            if ("ENUM_VIOLATION".equals(pool.getLastSelectedType())) {
                out.add(v);
            }
        }
        return out;
    }

    @Test
    public void stringEnumProducesTypedNonMembers() {
        List<String> members = Arrays.asList("ACTIVE", "INACTIVE", "PENDING");
        InvalidInputPool pool = new InvalidInputPool("status", "string");
        gen.generateEnumViolationInputs(param("string", members), pool);

        List<Object> vals = drain(pool);
        assertFalse("must emit at least one enum-violation value", vals.isEmpty());
        for (Object v : vals) {
            assertTrue("string-enum violation must stay a String, was " + v.getClass(),
                    v instanceof String);
            assertFalse("value must NOT be an enum member: " + v, members.contains(v));
        }
    }

    @Test
    public void integerEnumProducesNonMemberNumbers() {
        List<String> members = Arrays.asList("1", "2", "5");
        InvalidInputPool pool = new InvalidInputPool("status", "integer");
        gen.generateEnumViolationInputs(param("integer", members), pool);

        List<Object> vals = drain(pool);
        assertFalse(vals.isEmpty());
        List<Long> declared = Arrays.asList(1L, 2L, 5L);
        boolean hasMaxPlusOne = false, hasGap = false;
        for (Object v : vals) {
            assertTrue("integer-enum violation must be a Number, was " + v.getClass(),
                    v instanceof Number);
            long lv = ((Number) v).longValue();
            assertFalse("value must NOT be a declared member: " + lv, declared.contains(lv));
            if (lv == 6L) hasMaxPlusOne = true;       // max(1,2,5)+1
            if (lv == 3L || lv == 4L) hasGap = true;  // a gap between 2 and 5
        }
        assertTrue("should emit max+1 (6)", hasMaxPlusOne);
        assertTrue("should emit a gap value (3 or 4)", hasGap);
    }

    @Test
    public void noEnumProducesNothing() {
        InvalidInputPool pool = new InvalidInputPool("status", "string");
        gen.generateEnumViolationInputs(param("string", null), pool);
        assertEquals(0, pool.getCountForType("ENUM_VIOLATION"));
    }

    @Test
    public void singleElementIntegerEnumDoesNotCrash() {
        List<String> members = Arrays.asList("5");
        InvalidInputPool pool = new InvalidInputPool("status", "integer");
        gen.generateEnumViolationInputs(param("integer", members), pool);

        List<Object> vals = drain(pool);
        assertFalse("single-element enum must still yield non-members", vals.isEmpty());
        for (Object v : vals) {
            assertTrue(v instanceof Number);
            assertFalse(((Number) v).longValue() == 5L);
        }
    }

    @Test
    public void registryGatesEnumViolationByType() {
        FaultTypeRegistry reg = FaultTypeRegistry.loadDefault();
        assertTrue("ENUM_VIOLATION applies to string", reg.applies("ENUM_VIOLATION", "string", null));
        assertTrue("ENUM_VIOLATION applies to integer", reg.applies("ENUM_VIOLATION", "integer", null));
        assertFalse("ENUM_VIOLATION must NOT apply to boolean", reg.applies("ENUM_VIOLATION", "boolean", null));
    }
}
