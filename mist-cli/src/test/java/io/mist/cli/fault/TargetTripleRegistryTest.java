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

    // ── G3 depth adapter: supplied isolation + value-delta read-back ──

    private static final String SUPPLIED_VALUE_TRIPLE =
            "  - name: tt-cancel-refund\n"
                    + "    write_endpoint: \"GET /api/v1/cancelservice/cancel/{orderId}/{loginId}\"\n"
                    + "    dependency: ts-inside-payment-service\n"
                    + "    readback_endpoint: \"GET /api/v1/inside_pay_service/inside_payment/account\"\n"
                    + "    isolation_key: [userId]\n"
                    + "    isolation_strategy: supplied\n"
                    + "    readback_mode: value-delta\n"
                    + "    value_probe:\n"
                    + "      match_field: userId\n"
                    + "      value_field: balance\n";

    @Test
    public void suppliedValueDelta_parses() {
        TargetTripleRegistry.Registry registry = TargetTripleRegistry.parse(
                yaml("triples:\n" + SUPPLIED_VALUE_TRIPLE), "test-doc");
        TargetTripleRegistry.Triple t = registry.triples.get(0);
        assertEquals(TargetTripleRegistry.IsolationStrategy.SUPPLIED, t.isolationStrategy);
        assertEquals(TargetTripleRegistry.ReadbackMode.VALUE_DELTA, t.readbackMode);
        assertEquals("userId", t.valueProbe.matchField);
        assertEquals("balance", t.valueProbe.valueField);
    }

    @Test
    public void readbackMode_defaultsToMembership_withoutProbe() {
        TargetTripleRegistry.Triple t = TargetTripleRegistry.parse(
                yaml("triples:\n" + MINIMAL_TRIPLE), "test-doc").triples.get(0);
        assertEquals(TargetTripleRegistry.ReadbackMode.MEMBERSHIP, t.readbackMode);
        assertNull(t.valueProbe);
    }

    @Test
    public void valueDelta_withoutProbe_fails() {
        try {
            TargetTripleRegistry.parse(
                    yaml("triples:\n" + MINIMAL_TRIPLE + "    readback_mode: value-delta\n"),
                    "test-doc");
            fail("expected IllegalArgumentException for value-delta without value_probe");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("value_probe"));
        }
    }

    @Test
    public void probe_withoutValueDelta_fails() {
        String doc = "triples:\n" + MINIMAL_TRIPLE
                + "    value_probe:\n"
                + "      match_field: a\n"
                + "      value_field: v\n";
        try {
            TargetTripleRegistry.parse(yaml(doc), "test-doc");
            fail("expected IllegalArgumentException for value_probe without value-delta");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("value_probe"));
        }
    }

    @Test
    public void probeMatchField_outsideIsolationKey_fails() {
        String doc = "triples:\n"
                + "  - name: t1\n"
                + "    write_endpoint: \"POST /x\"\n"
                + "    dependency: ts-x\n"
                + "    readback_endpoint: \"GET /x\"\n"
                + "    isolation_key: [a]\n"
                + "    isolation_strategy: supplied\n"
                + "    readback_mode: value-delta\n"
                + "    value_probe:\n"
                + "      match_field: other\n"
                + "      value_field: v\n";
        try {
            TargetTripleRegistry.parse(yaml(doc), "test-doc");
            fail("expected IllegalArgumentException for match_field outside isolation_key");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("match_field"));
        }
    }

    @Test
    public void valueDelta_onNonSuppliedStrategy_fails() {
        // Review DEPTH-A F5: value-delta is defined only for supplied keys.
        String doc = "triples:\n"
                + "  - name: t1\n"
                + "    write_endpoint: \"POST /x\"\n"
                + "    dependency: ts-x\n"
                + "    readback_endpoint: \"GET /x\"\n"
                + "    isolation_key: [a]\n"
                + "    readback_mode: value-delta\n"
                + "    value_probe:\n"
                + "      match_field: a\n"
                + "      value_field: v\n";
        try {
            TargetTripleRegistry.parse(yaml(doc), "test-doc");
            fail("expected IllegalArgumentException for value-delta on FRESH_STRINGS");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("requires 'supplied'"));
        }
    }

    @Test
    public void probe_matchFieldEqualsValueField_fails() {
        // Review DEPTH-B F2: a constant probe can never observe a change.
        String doc = "triples:\n"
                + "  - name: t1\n"
                + "    write_endpoint: \"POST /x\"\n"
                + "    dependency: ts-x\n"
                + "    readback_endpoint: \"GET /x\"\n"
                + "    isolation_key: [a]\n"
                + "    isolation_strategy: supplied\n"
                + "    readback_mode: value-delta\n"
                + "    value_probe:\n"
                + "      match_field: a\n"
                + "      value_field: a\n";
        try {
            TargetTripleRegistry.parse(yaml(doc), "test-doc");
            fail("expected IllegalArgumentException for match_field == value_field");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("constant probe"));
        }
    }

    @Test
    public void shippedG3Configs_parseToTheCancelRefundTriples() throws Exception {
        // The two committed G3 head-to-head registries (evaluation/suts/trainticket/g3)
        // must parse through the reviewed loader to the expected supplied+value-delta
        // cancel->refund triple — natural without a fault_flag (EnvoyFilter fault),
        // constructed with the fork's fabricated-ack flag.
        java.nio.file.Path g3 = locateG3Dir();

        TargetTripleRegistry.Registry natural =
                TargetTripleRegistry.load(g3.resolve("target-triples-natural.yaml"));
        assertEquals(1, natural.triples.size());
        TargetTripleRegistry.Triple n = natural.triples.get(0);
        assertEquals("tt-cancel-refund-natural", n.name);
        assertEquals("GET /api/v1/cancelservice/cancel/{orderId}/{loginId}", n.writeEndpoint);
        assertEquals(TargetTripleRegistry.IsolationStrategy.SUPPLIED, n.isolationStrategy);
        assertEquals(java.util.Arrays.asList("userId"), n.isolationKey);
        assertEquals(TargetTripleRegistry.ReadbackMode.VALUE_DELTA, n.readbackMode);
        assertEquals("userId", n.valueProbe.matchField);
        assertEquals("balance", n.valueProbe.valueField);
        assertNull("natural stratum fault is the EnvoyFilter, not a SUT flag", n.faultFlag);
        assertEquals("kind-mist", natural.cluster.context);
        assertEquals("trainticket", natural.cluster.namespace);

        TargetTripleRegistry.Registry constructed =
                TargetTripleRegistry.load(g3.resolve("target-triples-constructed.yaml"));
        TargetTripleRegistry.Triple c = constructed.triples.get(0);
        assertEquals("tt-cancel-refund-constructed", c.name);
        assertEquals(TargetTripleRegistry.IsolationStrategy.SUPPLIED, c.isolationStrategy);
        assertEquals(TargetTripleRegistry.ReadbackMode.VALUE_DELTA, c.readbackMode);
        assertNotNull("constructed stratum toggles the fork fabricated-ack flag", c.faultFlag);
        assertEquals("ts-inside-payment-service", c.faultFlag.deployment);
        assertEquals("mist.fault.drawback.fabricatedack.enabled", c.faultFlag.property);
    }

    /** The g3 configs live in evaluation/ (not the classpath); find them from either
     *  the module basedir or the repo root, so the test works under -pl and the reactor. */
    private static java.nio.file.Path locateG3Dir() {
        for (String rel : new String[]{
                "../evaluation/suts/trainticket/g3", "evaluation/suts/trainticket/g3"}) {
            java.nio.file.Path p = java.nio.file.Paths.get(rel);
            if (java.nio.file.Files.isDirectory(p)) {
                return p;
            }
        }
        throw new IllegalStateException("cannot locate evaluation/suts/trainticket/g3 from "
                + java.nio.file.Paths.get("").toAbsolutePath());
    }

    @Test
    public void valueDelta_withReadbackBound_fails() {
        // Review DEPTH-A F2: the bound is a membership-absence guard; in
        // value-delta a truncated list must surface as an error instead.
        String doc = "triples:\n" + SUPPLIED_VALUE_TRIPLE + "    readback_bound: 100\n";
        try {
            TargetTripleRegistry.parse(yaml(doc), "test-doc");
            fail("expected IllegalArgumentException for value-delta + readback_bound");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("readback_bound"));
        }
    }

    @Test
    public void suppliedIsolation_requiresSingleKeyField() {
        String doc = "triples:\n"
                + "  - name: t1\n"
                + "    write_endpoint: \"POST /x\"\n"
                + "    dependency: ts-x\n"
                + "    readback_endpoint: \"GET /x\"\n"
                + "    isolation_key: [a, b]\n"
                + "    isolation_strategy: supplied\n";
        try {
            TargetTripleRegistry.parse(yaml(doc), "test-doc");
            fail("expected IllegalArgumentException for supplied isolation with two key fields");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("supplied"));
        }
    }

    @Test
    public void valueProbe_unknownKey_fails() {
        String doc = "triples:\n"
                + "  - name: t1\n"
                + "    write_endpoint: \"POST /x\"\n"
                + "    dependency: ts-x\n"
                + "    readback_endpoint: \"GET /x\"\n"
                + "    isolation_key: [a]\n"
                + "    readback_mode: value-delta\n"
                + "    value_probe:\n"
                + "      match_field: a\n"
                + "      value_field: v\n"
                + "      list_field: data\n";
        try {
            TargetTripleRegistry.parse(yaml(doc), "test-doc");
            fail("expected IllegalArgumentException for unknown value_probe key");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("list_field"));
        }
    }
}
