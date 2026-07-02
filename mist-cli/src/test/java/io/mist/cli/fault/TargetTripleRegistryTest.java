package io.mist.cli.fault;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins the P2/B1.1 target-triple registry: the shipped TrainTicket file
 * parses to exactly the two Gate-1 triples from prep/target-triples.md
 * (spec-verified business keys, cluster coordinates, isolation strategies),
 * and the strict parser rejects the malformations a hand-edited registry
 * could realistically contain.
 */
public class TargetTripleRegistryTest {

    private static final String MINIMAL_TRIPLE =
            "  - name: t1\n"
                    + "    write_endpoint: \"POST /x\"\n"
                    + "    dependency: ts-x\n"
                    + "    readback_endpoint: \"GET /x\"\n"
                    + "    isolation_key: [a]\n";

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
        TargetTripleRegistry.Registry registry =
                TargetTripleRegistry.parse(shippedTrainTicketRegistry(), "shipped");
        List<TargetTripleRegistry.Triple> triples = registry.triples;
        assertEquals(2, triples.size());

        assertNotNull(registry.cluster);
        assertEquals("minikube", registry.cluster.context);
        assertEquals("default", registry.cluster.namespace);
        assertEquals(180, registry.cluster.rolloutTimeoutSeconds);

        TargetTripleRegistry.Triple adminroute = triples.get(0);
        assertEquals("adminroute-create", adminroute.name);
        assertEquals("POST /api/v1/adminrouteservice/adminroute", adminroute.writeEndpoint);
        assertEquals("ts-route-service", adminroute.dependency);
        assertEquals("GET /api/v1/adminrouteservice/adminroute", adminroute.readbackEndpoint);
        assertEquals(Arrays.asList("startStation", "endStation"), adminroute.isolationKey);
        assertEquals(TargetTripleRegistry.IsolationStrategy.STATION_PAIR, adminroute.isolationStrategy);
        assertNotNull(adminroute.faultFlag);
        assertEquals("ts-admin-route-service", adminroute.faultFlag.deployment);
        assertEquals("mist.fault.lostwrite.enabled", adminroute.faultFlag.property);

        TargetTripleRegistry.Triple contacts = triples.get(1);
        assertEquals("adminbasic-contacts-create", contacts.name);
        assertEquals("POST /api/v1/adminbasicservice/adminbasic/contacts", contacts.writeEndpoint);
        assertEquals("ts-contacts-service", contacts.dependency);
        assertEquals("GET /api/v1/adminbasicservice/adminbasic/contacts", contacts.readbackEndpoint);
        assertEquals(Arrays.asList("accountId", "documentNumber"), contacts.isolationKey);
        assertEquals(TargetTripleRegistry.IsolationStrategy.FRESH_STRINGS, contacts.isolationStrategy);
        assertNotNull(contacts.faultFlag);
        assertEquals("ts-admin-basic-info-service", contacts.faultFlag.deployment);
        assertEquals("mist.fault.lostwrite.enabled", contacts.faultFlag.property);
    }

    @Test
    public void clusterAndStrategyAndFaultFlag_areOptionalWithDefaults() {
        TargetTripleRegistry.Registry registry =
                TargetTripleRegistry.parse(yaml("triples:\n" + MINIMAL_TRIPLE), "test-doc");
        assertNull(registry.cluster);
        TargetTripleRegistry.Triple t = registry.triples.get(0);
        assertNull(t.faultFlag);
        assertEquals(TargetTripleRegistry.IsolationStrategy.FRESH_STRINGS, t.isolationStrategy);
    }

    @Test
    public void clusterDefaults_namespaceAndTimeout() {
        String doc = "cluster:\n"
                + "  context: minikube\n"
                + "triples:\n" + MINIMAL_TRIPLE;
        TargetTripleRegistry.Registry registry = TargetTripleRegistry.parse(yaml(doc), "test-doc");
        assertEquals("minikube", registry.cluster.context);
        assertEquals("default", registry.cluster.namespace);
        assertEquals(180, registry.cluster.rolloutTimeoutSeconds);
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
    public void unknownTopLevelKey_failsAsTypo() {
        String doc = "clusterr:\n  context: minikube\ntriples:\n" + MINIMAL_TRIPLE;
        try {
            TargetTripleRegistry.parse(yaml(doc), "test-doc");
            fail("expected IllegalArgumentException for unknown top-level key");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("clusterr"));
        }
    }

    @Test
    public void unknownIsolationStrategy_fails() {
        String doc = "triples:\n" + MINIMAL_TRIPLE + "    isolation_strategy: uuid-pair\n";
        try {
            TargetTripleRegistry.parse(yaml(doc), "test-doc");
            fail("expected IllegalArgumentException for unknown isolation_strategy");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("uuid-pair"));
        }
    }

    @Test
    public void duplicateName_fails() {
        try {
            TargetTripleRegistry.parse(yaml("triples:\n" + MINIMAL_TRIPLE + MINIMAL_TRIPLE), "test-doc");
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
    public void faultFlag_missingProperty_fails() {
        String doc = "triples:\n" + MINIMAL_TRIPLE
                + "    fault_flag:\n"
                + "      deployment: ts-front\n";
        try {
            TargetTripleRegistry.parse(yaml(doc), "test-doc");
            fail("expected IllegalArgumentException for missing fault_flag property");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("property"));
        }
    }

    @Test
    public void faultFlag_unknownKey_failsAsTypo() {
        String doc = "triples:\n" + MINIMAL_TRIPLE
                + "    fault_flag:\n"
                + "      deployment: ts-front\n"
                + "      property: mist.fault.x.enabled\n"
                + "      namespase: default\n";
        try {
            TargetTripleRegistry.parse(yaml(doc), "test-doc");
            fail("expected IllegalArgumentException for unknown fault_flag key");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("namespase"));
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

    // ── hardening wave: R7fix load-time GET validation + R1fix readback_bound ──

    @Test
    public void nonGetReadback_failsAtLoad() {
        String doc = "triples:\n"
                + "  - name: t1\n"
                + "    write_endpoint: \"POST /x\"\n"
                + "    dependency: ts-x\n"
                + "    readback_endpoint: \"get /x\"\n"
                + "    isolation_key: [a]\n";
        try {
            TargetTripleRegistry.parse(yaml(doc), "test-doc");
            fail("expected IllegalArgumentException for a non-'GET ' readback_endpoint");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("GET "));
        }
    }

    @Test
    public void readbackBound_parsesAndDefaultsToOff() {
        TargetTripleRegistry.Registry registry = TargetTripleRegistry.parse(
                yaml("triples:\n" + MINIMAL_TRIPLE + "    readback_bound: 500\n"), "test-doc");
        assertEquals(500, registry.triples.get(0).readbackBound);

        TargetTripleRegistry.Registry noBound = TargetTripleRegistry.parse(
                yaml("triples:\n" + MINIMAL_TRIPLE), "test-doc");
        assertEquals("readback_bound defaults to 0 = off", 0, noBound.triples.get(0).readbackBound);
    }

    @Test
    public void readbackBound_rejectsNonPositive() {
        try {
            TargetTripleRegistry.parse(
                    yaml("triples:\n" + MINIMAL_TRIPLE + "    readback_bound: 0\n"), "test-doc");
            fail("expected IllegalArgumentException for readback_bound: 0");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("readback_bound"));
        }
    }
}
