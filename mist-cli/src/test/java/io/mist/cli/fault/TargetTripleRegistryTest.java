package io.mist.cli.fault;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins the P2 target-triple registry: the shipped TrainTicket file parses to
 * exactly the two Gate-1 triples from prep/target-triples.md (spec-verified
 * business keys), and the strict parser rejects the malformations a
 * hand-edited registry could realistically contain.
 */
public class TargetTripleRegistryTest {

    private static InputStream shippedTrainTicketRegistry() {
        InputStream in = TargetTripleRegistryTest.class
                .getResourceAsStream("/My-Example/trainticket/target-triples.yaml");
        assertNotNull("shipped trainticket target-triples.yaml must be on the classpath", in);
        return in;
    }

    private static InputStream yaml(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void shippedTrainTicketRegistry_loadsBothGate1Triples() {
        List<TargetTripleRegistry.Triple> triples =
                TargetTripleRegistry.parse(shippedTrainTicketRegistry(), "shipped");
        assertEquals(2, triples.size());

        TargetTripleRegistry.Triple adminroute = triples.get(0);
        assertEquals("adminroute-create", adminroute.name);
        assertEquals("POST /api/v1/adminrouteservice/adminroute", adminroute.writeEndpoint);
        assertEquals("ts-route-service", adminroute.dependency);
        assertEquals("GET /api/v1/adminrouteservice/adminroute", adminroute.readbackEndpoint);
        assertEquals(Arrays.asList("startStation", "endStation"), adminroute.isolationKey);

        TargetTripleRegistry.Triple contacts = triples.get(1);
        assertEquals("adminbasic-contacts-create", contacts.name);
        assertEquals("POST /api/v1/adminbasicservice/adminbasic/contacts", contacts.writeEndpoint);
        assertEquals("ts-contacts-service", contacts.dependency);
        assertEquals("GET /api/v1/adminbasicservice/adminbasic/contacts", contacts.readbackEndpoint);
        assertEquals(Arrays.asList("accountId", "documentNumber"), contacts.isolationKey);
    }

    @Test
    public void missingField_failsWithFieldName() {
        String doc = "triples:\n"
                + "  - name: t1\n"
                + "    write_endpoint: \"POST /x\"\n"
                + "    readback_endpoint: \"GET /x\"\n"
                + "    isolation_key: [a]\n";
        try {
            TargetTripleRegistry.parse(yaml(doc), "test-doc");
            fail("expected IllegalArgumentException for missing 'dependency'");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("dependency"));
        }
    }

    @Test
    public void unknownKey_failsAsTypo() {
        String doc = "triples:\n"
                + "  - name: t1\n"
                + "    write_endpoint: \"POST /x\"\n"
                + "    dependecy: ts-x\n"
                + "    readback_endpoint: \"GET /x\"\n"
                + "    isolation_key: [a]\n";
        try {
            TargetTripleRegistry.parse(yaml(doc), "test-doc");
            fail("expected IllegalArgumentException for unknown key 'dependecy'");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("dependecy"));
        }
    }

    @Test
    public void duplicateName_fails() {
        String one = "  - name: t1\n"
                + "    write_endpoint: \"POST /x\"\n"
                + "    dependency: ts-x\n"
                + "    readback_endpoint: \"GET /x\"\n"
                + "    isolation_key: [a]\n";
        try {
            TargetTripleRegistry.parse(yaml("triples:\n" + one + one), "test-doc");
            fail("expected IllegalArgumentException for duplicate name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("duplicate"));
        }
    }

    @Test
    public void emptyIsolationKey_fails() {
        String doc = "triples:\n"
                + "  - name: t1\n"
                + "    write_endpoint: \"POST /x\"\n"
                + "    dependency: ts-x\n"
                + "    readback_endpoint: \"GET /x\"\n"
                + "    isolation_key: []\n";
        try {
            TargetTripleRegistry.parse(yaml(doc), "test-doc");
            fail("expected IllegalArgumentException for empty isolation_key");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("isolation_key"));
        }
    }

    @Test
    public void missingTriplesList_fails() {
        try {
            TargetTripleRegistry.parse(yaml("something_else: true\n"), "test-doc");
            fail("expected IllegalArgumentException for missing 'triples'");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("triples"));
        }
    }
}
