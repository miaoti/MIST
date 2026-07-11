package io.mist.cli.enable;

import io.mist.cli.fault.DataIntegrityRuntime;
import io.mist.cli.fault.PairedFaultExecutor;
import io.mist.cli.fault.TargetTripleRegistry;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Wave 2.75-A paired run for OpenTelemetry-Demo's async acked-but-lost checkout write: MIST's
 * differential data-integrity oracle (MEMBERSHIP) reads the durable {@code accounting.shipping}
 * Postgres row through {@link SqlDurableReadback} and FIREs on the control-present /
 * broker-down-absent split. Mirrors {@link TeaStoreOrderHeadToHead} (reuses the reviewed
 * {@link PairedFaultExecutor#evaluate} verdict) but the fault is LEG-level (a single kafka scale
 * toggle between legs, {@link KafkaClusterOps}), not stimulus-owned.
 *
 * <p><b>Honest framing (review C-B1):</b> this is NOT a discrimination win over a trace comparator.
 * The case's {@code tracetest_presence_oracle} already FLAGs (the accounting CONSUMER span is absent
 * on the fault leg), so MIST's read-back is CONCORDANT with presence &mdash; it independently
 * confirms, at the durable store, the loss that presence catches. The datum's value is that MIST's
 * read-back binds an async SQL system-of-record and agrees with ground truth.
 *
 * <p><b>Async floor (review A-F8/B-F7):</b> the read-back timeout is set &ge;20s so the control
 * leg's genuinely-async landing (~5s measured) is never mis-read as absence; on the fault leg the
 * message never exists, so the full-window absence is a PERMANENT loss (gate {@code TIMEOUT_ABSENT}
 * &mdash; there is no async-completion signal to observe).
 *
 * <p><b>Marker threading:</b> {@link SqlDurableReadback} filters server-side by the current probe's
 * marker (B-R1, so the growing shipping table never inflates the read); the harness threads that
 * marker via {@link #currentMarker}, set immediately before {@code beforeWriteSupplied}. The
 * read-back polls SYNCHRONOUSLY inside {@code afterWrite} (single-threaded run), so one field is
 * correct.
 */
public final class OtelCheckoutHeadToHead {

    /** The SUT-specific write boundary: place one checkout carrying {@code marker} in streetAddress. */
    public interface Stimulus {
        Ack placeOrder(String marker) throws Exception;
    }

    /** One checkout ack: HTTP status + body (the success-shaped 200 order confirmation). */
    public static final class Ack {
        public final int status;
        public final String body;

        public Ack(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private final Stimulus stimulus;
    private final KafkaClusterOps cluster;
    private final int probes;
    private volatile String currentMarker;

    public OtelCheckoutHeadToHead(Stimulus stimulus, KafkaClusterOps cluster, int probes) {
        this.stimulus = stimulus;
        this.cluster = cluster;
        this.probes = probes;
    }

    /** The current probe's marker (read by {@link SqlDurableReadback}'s supplier at read time). */
    public String currentMarker() {
        return currentMarker;
    }

    private List<DataIntegrityRuntime.RunRecord> runLeg(TargetTripleRegistry.Triple triple, String leg)
            throws Exception {
        DataIntegrityRuntime.beginRun(Collections.singletonList(triple), leg);
        try {
            for (int i = 0; i < probes; i++) {
                String corr = triple.name + "#p" + i;                 // shared across legs, unique within
                String marker = "mist-" + leg + "-p" + i + "-" + UUID.randomUUID();  // globally unique
                currentMarker = marker;                                // threaded to the SQL read-back
                DataIntegrityRuntime.beforeWriteSupplied(triple.writeEndpoint, corr, null,
                        triple.isolationKey.get(0), marker);
                Ack ack = stimulus.placeOrder(marker);
                DataIntegrityRuntime.afterWrite(triple.writeEndpoint, corr, ack.status, ack.body, null);
            }
        } finally {
            return DataIntegrityRuntime.endRun();
        }
    }

    /**
     * Control leg (kafka up) then a SINGLE toggle to kafka-down, the fault leg, and restore. The
     * broker is scaled back to 1 in a finally even if the fault leg throws; the rdkafka-wedge
     * recovery (rollout-restart checkout+accounting+fraud) is the orchestrator's job after this
     * returns (it does not affect the already-collected records).
     */
    public List<PairedFaultExecutor.PairResult> run(TargetTripleRegistry.Triple triple) throws Exception {
        List<DataIntegrityRuntime.RunRecord> control = runLeg(triple, "control");
        List<DataIntegrityRuntime.RunRecord> fault;
        System.out.println("  [inject] scaling kafka -> 0 (single toggle) ...");
        cluster.scaleKafka(0);
        cluster.waitKafkaGone(90_000);
        System.out.println("  [inject] kafka down; running fault leg ...");
        try {
            fault = runLeg(triple, "fault");
        } finally {
            System.out.println("  [restore] scaling kafka -> 1 ...");
            cluster.scaleKafka(1);
        }
        List<PairedFaultExecutor.PairResult> pairs =
                PairedFaultExecutor.evaluate(Collections.singletonList(triple), control, fault);
        printCells(pairs);
        return pairs;
    }

    private static void printCells(List<PairedFaultExecutor.PairResult> pairs) {
        System.out.println("\n=== OTel-Demo checkout async masked-write (MEMBERSHIP, presence-concordant) ===");
        for (PairedFaultExecutor.PairResult p : pairs) {
            int joined = p.firePairs + p.noFirePairs + p.notEvaluablePairs;
            System.out.println("  triple=" + p.tripleName + "  verdict=" + p.pureDifferential.name()
                    + "  FIRE " + p.firePairs + "/" + joined + " probe-pair(s)"
                    + " (control-present / broker-down-absent)");
            System.out.println("    no_fire=" + p.noFirePairs + " not_evaluable=" + p.notEvaluablePairs
                    + " unjoined=" + p.unjoinedRecords
                    + "  join=" + p.joinMode + " correlatorUnique=" + p.correlatorUnique);
            System.out.println("    " + p.reason);
        }
    }

    private static String required(String key) {
        String v = System.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("missing required -D" + key);
        }
        return v.trim();
    }

    /**
     * Wiring (-D properties): {@code otel.base} (frontend-proxy, e.g. http://localhost:8085),
     * {@code otel.product} (default 0PUK6V6EV0), {@code otel.pgpod} (the postgresql pod name),
     * {@code otel.triple} (SUPPLIED+MEMBERSHIP on street_address), {@code otel.probes} (default 4),
     * {@code otel.kubectl} (space-separated kubectl prefix incl. context, default
     * "wsl kubectl --context kind-mist"), {@code otel.report} (optional report path). The async
     * read-back floor is set to 25s unless overridden.
     */
    public static void main(String[] args) throws Exception {
        String base = required("otel.base");
        String pgpod = required("otel.pgpod");
        int probes = Integer.getInteger("otel.probes", 4);
        String kubectl = System.getProperty("otel.kubectl", "wsl kubectl --context kind-mist");
        List<String> kubectlPrefix = new ArrayList<>(Arrays.asList(kubectl.trim().split("\\s+")));
        List<String> nsPrefix = new ArrayList<>(kubectlPrefix);
        nsPrefix.addAll(Arrays.asList("-n", "otel-demo"));

        if (System.getProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY) == null) {
            System.setProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY, "25000"); // >=20s async floor
        }
        System.setProperty("mst.test.parallelism", "1");

        ProcessCommandRunner runner = new ProcessCommandRunner();
        // psql read-back exec prefix: kubectl -n otel-demo exec -i <pod> -- env PGPASSWORD=otel psql ... -c
        List<String> execPrefix = new ArrayList<>(nsPrefix);
        execPrefix.addAll(Arrays.asList("exec", "-i", pgpod, "--", "env", "PGPASSWORD=otel",
                "psql", "-U", "root", "-d", "otel", "-t", "-A", "-c"));

        OtelCheckoutHeadToHead[] harnessHolder = new OtelCheckoutHeadToHead[1];
        SqlDurableReadback readback = new SqlDurableReadback(execPrefix, "street_address",
                "SELECT street_address FROM accounting.shipping WHERE street_address='%s'",
                () -> harnessHolder[0].currentMarker(), 20_000, runner);

        KafkaClusterOps cluster = new KafkaClusterOps(nsPrefix, runner, 60_000);
        try {
            DataIntegrityRuntime.installHttpOverride(readback);
            TargetTripleRegistry.Registry reg = TargetTripleRegistry.load(Paths.get(required("otel.triple")));
            TargetTripleRegistry.Triple triple = reg.triples.get(0);
            OtelCheckoutStimulus stimulus = new OtelCheckoutStimulus(base,
                    System.getProperty("otel.product", "0PUK6V6EV0"));
            OtelCheckoutHeadToHead harness = new OtelCheckoutHeadToHead(stimulus, cluster, probes);
            harnessHolder[0] = harness;
            List<PairedFaultExecutor.PairResult> pairs = harness.run(triple);
            String report = System.getProperty("otel.report");
            if (report != null && !report.trim().isEmpty()) {
                PairedFaultExecutor.writeReport(Paths.get(report.trim()), pairs,
                        "oteldemo-checkout-lost-" + System.currentTimeMillis());
                System.out.println("  report written to " + report.trim());
            }
        } finally {
            DataIntegrityRuntime.installHttpOverride(null);
        }
    }
}
