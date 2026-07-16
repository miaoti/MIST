package io.mist.cli.enable;

import io.mist.cli.fault.DataIntegrityRuntime;
import io.mist.cli.fault.PairedFaultExecutor;
import io.mist.cli.fault.TargetTripleRegistry;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * A5(iii) (completion-set wave, Phase C): the MISSING MIST read-back legs for the four
 * teastore MESH-SEVER cases — the same paired MEMBERSHIP binding as wave 2.75-A
 * ({@link TeaStoreOrderHeadToHead}, reused verbatim) under the MESH-SEVER producer instead
 * of the maintenance toggle: {@code faulted} wraps the confirm in a VirtualService
 * abort-503 window (the committed wave-3a/r1 recipes), applied before and ALWAYS deleted
 * after the write and BEFORE the oracle polls the read-back — mirroring the maintenance
 * discipline (fault scoped to the write; the read-back path is clean at probe time).
 *
 * <p>Sites: {@code -Dts.site=order} (the order-row pair; the 2.75-A
 * {@link JsonDurableReadback} + the committed order triple) or {@code orderitems} (the
 * child-collection pair; {@link ChainedOrderItemsReadback} + the orderitems triple).
 * Ground truth stays DIRECT persistence reads (never MIST) — recorded by the runner script.
 */
public final class TeaStoreMeshseverHeadToHead {

    /** Decorator: mesh-sever VS window around the inner (never-maintenance) confirm. */
    static final class MeshseverStimulus implements TeaStoreOrderHeadToHead.Stimulus {
        private final TeaStoreHttpStimulus inner;
        private final String vsYamlWsl;
        private final ProcessCommandRunner runner = new ProcessCommandRunner();
        private final List<String> prefix;

        MeshseverStimulus(TeaStoreHttpStimulus inner, String vsYamlWindows, String context) {
            this.inner = inner;
            this.vsYamlWsl = toWslPath(vsYamlWindows);
            this.prefix = Arrays.asList("wsl", "kubectl", "--context", context);
        }

        @Override
        public TeaStoreOrderHeadToHead.Ack placeOrder(String marker, boolean faulted)
                throws Exception {
            if (!faulted) {
                return inner.placeOrder(marker, false);
            }
            exec("apply", true);
            Thread.sleep(2500); // VS propagation to the client sidecar
            try {
                return inner.placeOrder(marker, false); // the fault is the mesh, not maintenance
            } finally {
                exec("delete", false); // tolerate not-found; NEVER left behind
                Thread.sleep(1500);
            }
        }

        private void exec(String verb, boolean loud) throws Exception {
            java.util.ArrayList<String> argv = new java.util.ArrayList<>(prefix);
            argv.addAll(Arrays.asList(verb, "-f", vsYamlWsl));
            if ("delete".equals(verb)) {
                argv.add("--ignore-not-found=true");
            }
            SqlDurableReadback.CommandRunner.Result r = runner.run(argv, 30000);
            System.out.println("  [meshsever] kubectl " + verb + " -> exit " + r.exitCode);
            if (loud && r.exitCode != 0) {
                throw new IllegalStateException("kubectl " + verb + " failed: " + r.stderr);
            }
        }

        private static String toWslPath(String windows) {
            String p = windows.replace('\\', '/');
            if (p.length() > 2 && p.charAt(1) == ':') {
                p = "/mnt/" + Character.toLowerCase(p.charAt(0)) + p.substring(2);
            }
            return p;
        }
    }

    public static void main(String[] args) throws Exception {
        String webui = req("ts.webui");
        String persistence = req("ts.persistence");
        String site = req("ts.site"); // order | orderitems
        String vs = req("ts.vs");
        Path triplePath = Paths.get(req("ts.triple"));
        int probes = Integer.getInteger("ts.probes", 4);
        if (System.getProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY) == null) {
            System.setProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY, "15000");
        }
        System.setProperty("mst.test.parallelism", "1");
        try {
            if ("orderitems".equals(site)) {
                DataIntegrityRuntime.installHttpOverride(
                        new ChainedOrderItemsReadback(persistence, "", 8000));
            } else {
                DataIntegrityRuntime.installHttpOverride(
                        new JsonDurableReadback(persistence, "", 8000));
            }
            TargetTripleRegistry.Registry reg = TargetTripleRegistry.load(triplePath);
            TargetTripleRegistry.Triple triple = reg.triples.get(0);
            TeaStoreHttpStimulus inner = new TeaStoreHttpStimulus(
                    webui, persistence,
                    System.getProperty("ts.user", "user21"),
                    System.getProperty("ts.pass", "password"),
                    System.getProperty("ts.product", "42"));
            MeshseverStimulus stimulus = new MeshseverStimulus(
                    inner, vs, System.getProperty("ts.kube.context", "kind-mist"));
            List<PairedFaultExecutor.PairResult> pairs =
                    new TeaStoreOrderHeadToHead(stimulus, probes).run(triple);
            String report = System.getProperty("ts.report");
            if (report != null && !report.trim().isEmpty()) {
                PairedFaultExecutor.writeReport(Paths.get(report.trim()), pairs,
                        "teastore-" + site + "-meshsever-" + System.currentTimeMillis());
                System.out.println("  report written to " + report.trim());
            }
        } finally {
            DataIntegrityRuntime.installHttpOverride(null);
        }
    }

    private static String req(String key) {
        String v = System.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("missing required -D" + key);
        }
        return v.trim();
    }
}
