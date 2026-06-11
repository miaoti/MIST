package io.mist.core.generation;

import io.mist.core.fault.InvalidInputPool;
import io.mist.core.llm.ParameterInfo;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Guards against "secretly-valid negatives" — fault values that claim to violate
 * a constraint but are actually accepted by the SUT, which would deflate the
 * reported fault-detection rate. Covers the OVERFLOW (numeric in-range
 * sentinels), boolean TYPE_MISMATCH (binder-coercible values) and NULL_INPUT
 * (string-encoded nulls on string params) hardening.
 */
public class SecretlyValidNegativeTest {

    private final HardcodedInvalidInputGenerator gen = new HardcodedInvalidInputGenerator();

    private static ParameterInfo param(String type) {
        ParameterInfo p = new ParameterInfo();
        p.setName("p");
        p.setType(type);
        p.setRequired(Boolean.TRUE);
        return p;
    }

    /** Drain every value tagged with the given fault type (preserving Java nulls). */
    private static List<Object> drain(InvalidInputPool pool, String faultType) {
        List<Object> out = new ArrayList<>();
        while (pool.hasNextRoundRobin()) {
            Object v = pool.getNextRoundRobin();
            if (faultType.equals(pool.getLastSelectedType())) {
                out.add(v);
            }
        }
        return out;
    }

    private static boolean isValidInt32(Object v) {
        try {
            BigInteger b = new BigInteger(String.valueOf(v).trim());
            return b.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                    && b.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0;
        } catch (NumberFormatException e) {
            return false; // non-numeric → certainly not an in-range int
        }
    }

    private static boolean isValidInt64(Object v) {
        try {
            new BigInteger(String.valueOf(v).trim()).longValueExact();
            return true;
        } catch (ArithmeticException | NumberFormatException e) {
            return false;
        }
    }

    @Test
    public void integerOverflowNeverEmitsInRangeInt32() {
        InvalidInputPool pool = new InvalidInputPool("p", "integer");
        gen.generateOverflowInputs(param("integer"), pool);
        List<Object> vals = drain(pool, "OVERFLOW");
        assertFalse("must emit overflow values", vals.isEmpty());
        for (Object v : vals) {
            assertFalse("int32 OVERFLOW must not be an in-range int32 (secretly valid): " + v,
                    isValidInt32(v));
        }
    }

    @Test
    public void longOverflowNeverEmitsInRangeInt64() {
        InvalidInputPool pool = new InvalidInputPool("p", "long");
        gen.generateOverflowInputs(param("long"), pool);
        List<Object> vals = drain(pool, "OVERFLOW");
        assertFalse("must emit overflow values", vals.isEmpty());
        for (Object v : vals) {
            assertFalse("int64 OVERFLOW must not be an in-range int64 (secretly valid): " + v,
                    isValidInt64(v));
        }
    }

    @Test
    public void numberOverflowParsesToNonFiniteDouble() {
        InvalidInputPool pool = new InvalidInputPool("p", "number");
        gen.generateOverflowInputs(param("number"), pool);
        List<Object> vals = drain(pool, "OVERFLOW");
        assertFalse("must emit overflow values", vals.isEmpty());
        for (Object v : vals) {
            double d = Double.parseDouble(String.valueOf(v).trim());
            assertTrue("number OVERFLOW must exceed the double range (parse to ±Infinity): " + v,
                    Double.isInfinite(d));
        }
    }

    @Test
    public void booleanTypeMismatchNeverEmitsCoercibleValue() {
        // Values that Spring/Jackson coerce back to a valid boolean.
        List<String> coercible = Arrays.asList("true", "false", "yes", "no", "on", "off", "1", "0");
        InvalidInputPool pool = new InvalidInputPool("p", "boolean");
        gen.generateTypeMismatchInputs(param("boolean"), pool);
        List<Object> vals = drain(pool, "TYPE_MISMATCH");
        assertFalse("must emit type-mismatch values", vals.isEmpty());
        for (Object v : vals) {
            assertFalse("boolean TYPE_MISMATCH must not be binder-coercible to a boolean: " + v,
                    coercible.contains(String.valueOf(v).trim().toLowerCase()));
        }
    }

    @Test
    public void nullInputOnStringParamEmitsOnlyActualNull() {
        InvalidInputPool pool = new InvalidInputPool("p", "string");
        gen.generateNullInputs(param("string"), pool);
        List<Object> vals = drain(pool, "NULL_INPUT");
        assertTrue("string NULL_INPUT must contain the genuine null", vals.contains(null));
        for (Object v : vals) {
            // "null"/"undefined"/"None" are valid non-empty strings for a string field.
            assertTrue("string NULL_INPUT must emit only the genuine null, not a string literal: " + v,
                    v == null);
        }
    }

    @Test
    public void nullInputOnIntegerParamKeepsStringEncodedNulls() {
        InvalidInputPool pool = new InvalidInputPool("p", "integer");
        gen.generateNullInputs(param("integer"), pool);
        List<Object> vals = drain(pool, "NULL_INPUT");
        assertTrue("integer NULL_INPUT must contain the genuine null", vals.contains(null));
        assertTrue("integer NULL_INPUT should still carry string-encoded nulls (type-invalid here)",
                vals.contains("null"));
    }
}
