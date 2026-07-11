package io.mist.cli.g3;

import org.junit.Test;

import java.util.Random;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * E2 (plan rev 2.1, C1): the client-generated W3C traceparent + the trace-id carrier are unit-tested
 * without the live SUT. This is the visibility-only change that lets the out-of-band scorer select
 * EXACTLY the cancel's trace by id; it does NOT touch the verdict (the read-back stays timeout-gated).
 */
public class CancelRefundHeadToHeadC1Test {

    /** version 00, sampled (01), 32-hex trace-id, 16-hex span-id. */
    private static final Pattern W3C = Pattern.compile("00-[0-9a-f]{32}-[0-9a-f]{16}-01");

    @Test
    public void newClientTrace_isValidW3CAndTraceIdMatchesHeader() {
        CancelRefundHeadToHead.ClientTrace ct = CancelRefundHeadToHead.newClientTrace(new Random(42));
        assertTrue("traceparent must be a valid W3C header: " + ct.traceparent,
                W3C.matcher(ct.traceparent).matches());
        assertEquals("traceId must be the 32-hex field of the header",
                "00-" + ct.traceId + "-", ct.traceparent.substring(0, ct.traceId.length() + 4));
        assertEquals(32, ct.traceId.length());
    }

    @Test
    public void newClientTrace_traceIdIsNonZeroAndUnique() {
        Random rnd = new Random(7);
        CancelRefundHeadToHead.ClientTrace a = CancelRefundHeadToHead.newClientTrace(rnd);
        CancelRefundHeadToHead.ClientTrace b = CancelRefundHeadToHead.newClientTrace(rnd);
        // W3C forbids an all-zero trace-id; and two draws must differ (per-probe selection).
        assertTrue("trace-id must not be all-zero", !a.traceId.matches("0{32}"));
        assertNotEquals("two draws must differ so each cancel is individually selectable",
                a.traceId, b.traceId);
    }

    @Test
    public void resp_carriesTraceId_andLegacyOverloadIsNull() {
        CancelRefundHeadToHead.Resp withId =
                new CancelRefundHeadToHead.Resp(200, "{\"status\":1}", "abc123");
        assertEquals("abc123", withId.traceId);
        assertEquals(200, withId.status);
        // The legacy 2-arg path (any non-traced caller) must stay null — never a bogus id.
        CancelRefundHeadToHead.Resp legacy = new CancelRefundHeadToHead.Resp(200, "{}");
        assertNull(legacy.traceId);
    }
}
