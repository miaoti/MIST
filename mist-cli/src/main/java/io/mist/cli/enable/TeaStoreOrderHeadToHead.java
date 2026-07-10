package io.mist.cli.enable;

import io.mist.cli.fault.DataIntegrityRuntime;
import io.mist.cli.fault.PairedFaultExecutor;
import io.mist.cli.fault.TargetTripleRegistry;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Wave 2.75-A paired run for TeaStore's masked order write: MIST's differential data-integrity
 * oracle (MEMBERSHIP) reads the durable {@code /rest/orders} JSON and FIREs on the
 * control-present / maintenance-masked-absent split. Mirrors {@link
 * io.mist.cli.g3.ShippingEnqueueHeadToHead} (reuses the reviewed verdict logic, not
 * PairedFaultExecutor.execute) but is MEMBERSHIP, not value-delta, and has NO comparator arm (this
 * SUT is trace-uninstrumented, so MIST's read-back is the SOLE oracle here &mdash; the wave reports
 * this as a sole-oracle datum, NOT a discrimination win over a trace comparator; review C-B1).
 *
 * <p>Fault ownership (review B-R3): the persistence maintenance toggle is owned INSIDE the stimulus,
 * not as a leg-bracketing {@code Fault}. A maintenance-ON window straddling the read-back would make
 * {@code /rest/orders} 503 (the leg would log a transport error, not observe absence); so the
 * faulted stimulus toggles maintenance ON only around the confirm write and restores it OFF before
 * returning &mdash; by the time the oracle polls the read-back, maintenance is OFF and the order is
 * genuinely absent (it was never persisted). {@link Stimulus#placeOrder} therefore takes the
 * {@code faulted} flag and restores maintenance in its own finally.
 *
 * <p>Isolation: SUPPLIED MEMBERSHIP on {@code address1}. Each probe plants a GLOBALLY unique marker
 * in the confirm-form address line (unique per leg AND per probe so a fault-leg read-back can never
 * match a control-leg order), supplied to {@link DataIntegrityRuntime#beforeWriteSupplied}. The
 * per-probe correlator {@code <triple>#p<i>} is shared across the control and fault legs so
 * {@link PairedFaultExecutor} pairs them, and is unique WITHIN each leg (claim-eligibility, A-F10).
 */
public final class TeaStoreOrderHeadToHead {

    /** The SUT-specific write boundary against the live TeaStore webui. */
    public interface Stimulus {
        /**
         * Log in, add the product, and confirm an order carrying {@code marker} in its address line.
         * When {@code faulted}, wrap the confirm in a persistence maintenance window (ON before,
         * OFF after &mdash; in a finally) so the order is masked-lost but the read-back that follows
         * sees a served {@code /rest/orders}. Returns the confirm ack (status + body).
         */
        Ack placeOrder(String marker, boolean faulted) throws Exception;
    }

    /** One confirm ack: HTTP status + body (the success-shaped-clean ORDERCONFIRMED page). */
    public static final class Ack {
        public final int status;
        public final String body;

        public Ack(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private final Stimulus stimulus;
    private final int probes;

    public TeaStoreOrderHeadToHead(Stimulus stimulus, int probes) {
        this.stimulus = stimulus;
        this.probes = probes;
    }

    /** One leg (control or fault): N probes, each a fresh marker + a shared-across-legs correlator. */
    private List<DataIntegrityRuntime.RunRecord> runLeg(TargetTripleRegistry.Triple triple,
                                                        String leg, boolean faulted) throws Exception {
        DataIntegrityRuntime.beginRun(Collections.singletonList(triple), leg);
        try {
            for (int i = 0; i < probes; i++) {
                String corr = triple.name + "#p" + i;         // shared across legs, unique within a leg
                String marker = "mist-" + leg + "-p" + i + "-" + UUID.randomUUID();  // globally unique
                DataIntegrityRuntime.beforeWriteSupplied(triple.writeEndpoint, corr, null,
                        triple.isolationKey.get(0), marker);
                Ack ack = stimulus.placeOrder(marker, faulted);
                DataIntegrityRuntime.afterWrite(triple.writeEndpoint, corr, ack.status, ack.body, null);
            }
        } finally {
            return DataIntegrityRuntime.endRun();
        }
    }

    /** Control leg then fault leg, then the paired MIST verdicts (one per probe pair). */
    public List<PairedFaultExecutor.PairResult> run(TargetTripleRegistry.Triple triple) throws Exception {
        List<DataIntegrityRuntime.RunRecord> control = runLeg(triple, "control", false);
        List<DataIntegrityRuntime.RunRecord> fault = runLeg(triple, "fault", true);
        List<PairedFaultExecutor.PairResult> pairs =
                PairedFaultExecutor.evaluate(Collections.singletonList(triple), control, fault);
        printCells(pairs);
        return pairs;
    }

    private static void printCells(List<PairedFaultExecutor.PairResult> pairs) {
        System.out.println("\n=== TeaStore order masked-write (MEMBERSHIP, sole-oracle) ===");
        for (PairedFaultExecutor.PairResult p : pairs) {
            int joined = p.firePairs + p.noFirePairs + p.notEvaluablePairs;
            System.out.println("  triple=" + p.tripleName + "  verdict=" + p.pureDifferential.name()
                    + "  FIRE " + p.firePairs + "/" + joined + " probe-pair(s)"
                    + " (control-present / maintenance-masked-absent)");
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
     * Wiring (-D properties): {@code ts.webui} (webui base), {@code ts.persistence} (persistence
     * base), {@code ts.user}/{@code ts.pass}, {@code ts.product} (default 42), {@code ts.triple}
     * (the SUPPLIED+MEMBERSHIP triple), {@code ts.probes} (default 4). The read-back is routed at
     * the persistence {@code /rest/orders} JSON via {@link JsonDurableReadback}; the async floor is
     * irrelevant (synchronous SUT) but a generous read-back timeout is set.
     */
    public static void main(String[] args) throws Exception {
        String webui = required("ts.webui");
        String persistence = required("ts.persistence");
        int probes = Integer.getInteger("ts.probes", 4);
        if (System.getProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY) == null) {
            System.setProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY, "15000");
        }
        System.setProperty("mst.test.parallelism", "1");
        try {
            // Full /rest/orders read (complete collection -> sound MEMBERSHIP absence; the marker is
            // globally unique so no false match). Scope is left empty; the triple's readback_bound
            // guards a truncation surprise (review B-R1).
            DataIntegrityRuntime.installHttpOverride(new JsonDurableReadback(persistence, "", 8000));
            TargetTripleRegistry.Registry reg = TargetTripleRegistry.load(Paths.get(required("ts.triple")));
            TargetTripleRegistry.Triple triple = reg.triples.get(0);
            TeaStoreHttpStimulus stimulus = new TeaStoreHttpStimulus(
                    webui, persistence,
                    System.getProperty("ts.user", "user21"),
                    System.getProperty("ts.pass", "password"),
                    System.getProperty("ts.product", "42"));
            List<PairedFaultExecutor.PairResult> pairs =
                    new TeaStoreOrderHeadToHead(stimulus, probes).run(triple);
            String report = System.getProperty("ts.report");
            if (report != null && !report.trim().isEmpty()) {
                PairedFaultExecutor.writeReport(Paths.get(report.trim()), pairs,
                        "teastore-order-maintenance-masked-" + System.currentTimeMillis());
                System.out.println("  report written to " + report.trim());
            }
        } finally {
            DataIntegrityRuntime.installHttpOverride(null);
        }
    }
}
