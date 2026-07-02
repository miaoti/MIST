package io.mist.cli.comparator;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Pins the strict bindings loader (comparator-runner-design.md §2, test t1). */
public class AssertionBindingsTest {

    private static InputStream yaml(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static final String HEADER = "sut: trainticket\n"
            + "frozen_set: \"blind-assertions-trainticket.yaml @ commit 15954a8\"\n"
            + "endpoints:\n"
            + "  - endpoint: \"POST /api/v1/x\"\n"
            + "    triple: t1\n"
            + "    body_template: '{\"a\":\"${uuid:a}\"}'\n"
            + "    clauses:\n";

    @Test
    public void shippedCalibrationBindings_load() throws Exception {
        AssertionBindings.Bindings bindings = AssertionBindings.load(java.nio.file.Paths.get(
                "..", "debug", "a-main", "g2-comparator",
                "assertion-bindings-trainticket-calibration.yaml"));
        assertEquals("trainticket", bindings.sut);
        assertEquals(2, bindings.endpoints.size());
        assertEquals("adminroute-create", bindings.endpoints.get(0).triple);
        // Every endpoint carries a NOT_CHECKABLE failure clause with a reason.
        for (AssertionBindings.BoundEndpoint ep : bindings.endpoints) {
            boolean hasNotCheckable = ep.clauses.stream().flatMap(c -> c.checks.stream())
                    .anyMatch(ch -> ch.primitive == AssertionBindings.Primitive.NOT_CHECKABLE
                            && ch.reason != null && !ch.reason.isEmpty());
            assertTrue(ep.endpoint + " must record its unexercised failure clause", hasNotCheckable);
        }
    }

    @Test
    public void unknownPrimitive_rejected() {
        String doc = HEADER
                + "      - cite: \"c\"\n"
                + "        checks:\n"
                + "          - primitive: BODY_REGEX\n"
                + "            expect: \"x\"\n";
        try {
            AssertionBindings.parse(yaml(doc), "test-doc");
            fail("expected unknown primitive to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("BODY_REGEX"));
        }
    }

    @Test
    public void stateGet_containsFields_requiresFields() {
        String doc = HEADER
                + "      - cite: \"c\"\n"
                + "        checks:\n"
                + "          - primitive: STATE_GET\n"
                + "            path: \"/x\"\n"
                + "            expect: \"contains-submitted-fields\"\n";
        try {
            AssertionBindings.parse(yaml(doc), "test-doc");
            fail("expected missing 'fields' to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("fields"));
        }
    }

    @Test
    public void notCheckable_requiresReason() {
        String doc = HEADER
                + "      - cite: \"c\"\n"
                + "        checks:\n"
                + "          - primitive: NOT_CHECKABLE\n";
        try {
            AssertionBindings.parse(yaml(doc), "test-doc");
            fail("expected missing 'reason' to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("reason"));
        }
    }

    @Test
    public void unknownKey_rejectedAsTypo() {
        String doc = HEADER
                + "      - cite: \"c\"\n"
                + "        checks:\n"
                + "          - primitive: HTTP_STATUS\n"
                + "            expect: \"200\"\n"
                + "            pth: \"/oops\"\n";
        try {
            AssertionBindings.parse(yaml(doc), "test-doc");
            fail("expected unknown check key to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("pth"));
        }
    }
}
