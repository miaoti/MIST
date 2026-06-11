package io.mist.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.mist.core.multiservice.MicroserviceTestConfigurationIO;
import io.mist.core.spec.TestConfigurationObject;
import io.mist.core.spec.OpenAPISpecification;
import io.mist.core.registry.SemanticDependencyRegistry;
import io.mist.core.registry.SemanticDependencyRegistry.Pass;
import io.mist.core.workflow.TraceWorkflowExtractor;
import io.mist.core.workflow.WorkflowScenario;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Evaluates the {@link io.mist.core.registry.SemanticDependencyRegistry}
 * against a manually curated ground-truth file and reports Precision/Recall/F1.
 *
 * <p>Inputs:
 * <ul>
 *   <li>{@code --dump}  path to {@code semantic-registry-dump.json}
 *       (default {@code target/semantic-registry-dump.json}).</li>
 *   <li>{@code --truth} path to the ground-truth YAML
 *       (default {@code src/test/resources/dependency-ground-truth.trainticket.yaml}).</li>
 *   <li>{@code --threshold} minimum F1 for exit-code 0 (default {@code 0.0}).</li>
 * </ul>
 *
 * <p>Exit code 0 on success (F1 ≥ threshold) or if no threshold given; 1 otherwise.
 */
public class SemanticRegistryEvaluator {

    // A (consumer, param, producer) triple — the unit of evaluation.
    public static final class Binding {
        public final String consumer;
        public final String param;
        public final String producer;

        public Binding(String consumer, String param, String producer) {
            this.consumer = consumer;
            this.param = param;
            this.producer = producer;
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Binding)) return false;
            Binding b = (Binding) o;
            return consumer.equals(b.consumer) && param.equals(b.param) && Objects.equals(producer, b.producer);
        }
        @Override public int hashCode() { return Objects.hash(consumer, param, producer); }
        @Override public String toString() { return consumer + " [" + param + "] ← " + producer; }
    }

    public static final class GroundTruth {
        /** Labeled (consumer, param, producer) triples where producer is required. */
        public final Set<Binding> positives = new LinkedHashSet<>();
        /** (consumer, param) pairs where NO binding should be emitted — e.g. the
         *  true producer exists in production but is not declared in the Swagger
         *  or test configuration the registry is given, so inference is impossible. */
        public final Set<String>  negativePairs = new LinkedHashSet<>();
        /** Consumer keys where no binding on ANY param is expected (stronger form). */
        public final Set<String>  zeroConsumers = new LinkedHashSet<>();

        /** Consumer keys considered in-scope for FP accounting. */
        public Set<String> scopeConsumers() {
            Set<String> s = new LinkedHashSet<>(zeroConsumers);
            for (Binding b : positives) s.add(b.consumer);
            for (String pair : negativePairs) s.add(pair.substring(0, pair.indexOf('|')));
            return s;
        }
    }

    public static final class EvalResult {
        public int tp, fp, fn;
        public final List<Binding> tpBindings = new ArrayList<>();
        public final List<Binding> fpBindings = new ArrayList<>();
        public final List<Binding> fnBindings = new ArrayList<>();

        public double precision() { return (tp + fp) == 0 ? 1.0 : (double) tp / (tp + fp); }
        public double recall()    { return (tp + fn) == 0 ? 1.0 : (double) tp / (tp + fn); }
        public double f1() {
            double p = precision(), r = recall();
            return (p + r) == 0 ? 0.0 : 2 * p * r / (p + r);
        }
    }

    // ── Loaders ──────────────────────────────────────────────────────────

    public static GroundTruth loadGroundTruth(String path) throws Exception {
        YAMLMapper yaml = new YAMLMapper(new YAMLFactory());
        JsonNode root = yaml.readTree(new File(path));
        GroundTruth gt = new GroundTruth();
        JsonNode bindings = root.get("bindings");
        if (bindings != null && bindings.isArray()) {
            for (JsonNode b : bindings) {
                String consumer = b.path("consumer").asText();
                String param    = b.path("param").asText();
                JsonNode prod   = b.get("producer");
                if (prod == null || prod.isNull()) {
                    // producer: null ⇒ no binding expected for this (consumer, param).
                    gt.negativePairs.add(consumer + "|" + param);
                } else {
                    gt.positives.add(new Binding(consumer, param, prod.asText()));
                }
            }
        }
        JsonNode noBind = root.get("no_binding_consumers");
        if (noBind != null && noBind.isArray()) {
            for (JsonNode c : noBind) gt.zeroConsumers.add(c.asText());
        }
        return gt;
    }

    /** All (consumer, param, producer) triples extracted from the dump. */
    public static Set<Binding> loadRegistryBindings(String path) throws Exception {
        ObjectMapper json = new ObjectMapper();
        JsonNode root = json.readTree(new File(path));
        JsonNode consumerIdx = root.get("consumerIndex");
        Set<Binding> out = new LinkedHashSet<>();
        if (consumerIdx == null || !consumerIdx.isObject()) return out;
        Iterator<Map.Entry<String, JsonNode>> it = consumerIdx.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> consumerE = it.next();
            String consumerKey = consumerE.getKey();
            JsonNode params = consumerE.getValue();
            if (params == null || !params.isObject()) continue;
            Iterator<Map.Entry<String, JsonNode>> pit = params.fields();
            while (pit.hasNext()) {
                Map.Entry<String, JsonNode> pe = pit.next();
                String producer = pe.getValue().path("producerApiKey").asText(null);
                out.add(new Binding(consumerKey, pe.getKey(), producer));
            }
        }
        return out;
    }

    // ── Core evaluator ───────────────────────────────────────────────────

    public static EvalResult evaluate(GroundTruth gt, Set<Binding> registry) {
        Set<String> scope = gt.scopeConsumers();
        EvalResult r = new EvalResult();

        // Index ground-truth positives by (consumer, param) for O(1) comparison.
        Map<String, String> positiveProducer = new LinkedHashMap<>();
        for (Binding b : gt.positives) {
            positiveProducer.put(b.consumer + "|" + b.param, b.producer);
        }

        // Walk only registry bindings whose consumer is in scope.
        Set<String> seenPositiveKeys = new LinkedHashSet<>();
        for (Binding rb : registry) {
            if (!scope.contains(rb.consumer)) continue;
            String k = rb.consumer + "|" + rb.param;
            String expected = positiveProducer.get(k);
            if (expected == null) {
                // Either zero-expected consumer, or unknown (param, consumer) pair → FP.
                r.fp++;
                r.fpBindings.add(rb);
            } else if (Objects.equals(expected, rb.producer)) {
                r.tp++;
                r.tpBindings.add(rb);
                seenPositiveKeys.add(k);
            } else {
                // Wrong producer — counted as FP here; the corresponding FN is added below.
                r.fp++;
                r.fpBindings.add(rb);
            }
        }
        for (Binding gb : gt.positives) {
            String k = gb.consumer + "|" + gb.param;
            if (!seenPositiveKeys.contains(k)) {
                r.fn++;
                r.fnBindings.add(gb);
            }
        }
        return r;
    }

    // ── Rendering ────────────────────────────────────────────────────────

    public static String renderReport(EvalResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ground-truth evaluation — SemanticDependencyRegistry\n");
        sb.append("-----------------------------------------------------\n");
        sb.append(String.format("TP=%d  FP=%d  FN=%d%n", r.tp, r.fp, r.fn));
        sb.append(String.format("Precision = %.3f%n", r.precision()));
        sb.append(String.format("Recall    = %.3f%n", r.recall()));
        sb.append(String.format("F1        = %.3f%n", r.f1()));

        if (!r.fpBindings.isEmpty()) {
            sb.append("\nFalse positives (registry emitted a wrong / unexpected binding):\n");
            for (Binding b : r.fpBindings) sb.append("  FP  ").append(b).append('\n');
        }
        if (!r.fnBindings.isEmpty()) {
            sb.append("\nFalse negatives (ground truth binding missing from registry):\n");
            for (Binding b : r.fnBindings) sb.append("  FN  ").append(b).append('\n');
        }
        return sb.toString();
    }

    // ── Ablation + in-process rebuild ────────────────────────────────────
    //
    // The evaluator can build the registry in-process from the trainticket
    // sample with any subset of the optional {@link Pass}es enabled. Used by
    // {@code --ablation} to produce a paper-ready contribution table.

    private static final String OAS_PATH   = "src/main/resources/My-Example/trainticket/merged_openapi_spec 1.yaml";
    private static final String CONF_PATH  = "src/main/resources/My-Example/trainticket/real-system-conf.yaml";
    private static final String TRACE_PATH = "src/main/resources/My-Example/trainticket/test-trace";

    /** A named ablation configuration. */
    public static final class AblationConfig {
        public final String name;
        public final EnumSet<Pass> passes;
        public AblationConfig(String name, EnumSet<Pass> passes) { this.name = name; this.passes = passes; }
    }

    /** The fixed ablation menu — each row corresponds to one column in the paper's ablation table. */
    public static List<AblationConfig> ablationMenu() {
        List<AblationConfig> list = new ArrayList<>();
        list.add(new AblationConfig("Full (all passes)", EnumSet.allOf(Pass.class)));
        list.add(new AblationConfig("– Swagger consumer scan (Pass 1c)",
                allExcept(Pass.SWAGGER_CONSUMER_SCAN)));
        list.add(new AblationConfig("– Schema jsonPath refinement (Pass 2a)",
                allExcept(Pass.SCHEMA_JSONPATH_REFINEMENT)));
        list.add(new AblationConfig("– Trace jsonPath refinement (Pass 2b)",
                allExcept(Pass.TRACE_JSONPATH_REFINEMENT)));
        list.add(new AblationConfig("– Generic {id} path inference",
                allExcept(Pass.GENERIC_ID_PATH_INFERENCE)));
        list.add(new AblationConfig("– Scored producer selection (→ first-candidate)",
                allExcept(Pass.SCORED_PRODUCER_SELECTION)));
        return list;
    }

    private static EnumSet<Pass> allExcept(Pass p) {
        EnumSet<Pass> s = EnumSet.allOf(Pass.class);
        s.remove(p);
        return s;
    }

    /** Build the registry in-process with a given pass mask, extract its bindings. */
    public static Set<Binding> buildAndExtract(EnumSet<Pass> passes) throws Exception {
        OpenAPISpecification spec = new OpenAPISpecification(OAS_PATH);
        Map<String, TestConfigurationObject> configs;
        try (FileInputStream in = new FileInputStream(CONF_PATH)) {
            configs = MicroserviceTestConfigurationIO.loadMultiServiceConfiguration(in);
        }
        Map<String, OpenAPISpecification> specs = new LinkedHashMap<>();
        for (String svc : configs.keySet()) specs.put(svc, spec);

        List<WorkflowScenario> scenarios;
        try { scenarios = TraceWorkflowExtractor.extractScenarios(TRACE_PATH); }
        catch (Exception e) { scenarios = null; }

        SemanticDependencyRegistry reg =
                SemanticDependencyRegistry.build(
                        configs,
                        io.mist.cli.spi.PojoConverter.toOpenApiMap(specs),
                        scenarios, passes);

        File tmp = File.createTempFile("registry-eval", ".json");
        tmp.deleteOnExit();
        reg.dumpRegistryToFile(tmp.getAbsolutePath());
        return loadRegistryBindings(tmp.getAbsolutePath());
    }

    public static String renderAblationTable(GroundTruth gt, List<AblationConfig> configs) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-52s  %4s %4s %4s  %5s %5s %5s%n",
                "configuration", "TP", "FP", "FN", "P", "R", "F1"));
        sb.append(String.format("%-52s  %4s %4s %4s  %5s %5s %5s%n",
                repeat('-', 52), "----", "----", "----", "-----", "-----", "-----"));
        for (AblationConfig cfg : configs) {
            EvalResult r = evaluate(gt, buildAndExtract(cfg.passes));
            sb.append(String.format("%-52s  %4d %4d %4d  %5.3f %5.3f %5.3f%n",
                    cfg.name, r.tp, r.fp, r.fn, r.precision(), r.recall(), r.f1()));
        }
        return sb.toString();
    }

    private static String repeat(char c, int n) {
        StringBuilder s = new StringBuilder(n);
        for (int i = 0; i < n; i++) s.append(c);
        return s.toString();
    }

    /** Diff a freshly-built registry dump against the checked-in golden copy. */
    public static boolean matchesGolden(String goldenPath) throws Exception {
        Set<Binding> fresh = buildAndExtract(EnumSet.allOf(Pass.class));
        Set<Binding> golden = loadRegistryBindings(goldenPath);
        if (fresh.equals(golden)) return true;
        Set<Binding> added = new LinkedHashSet<>(fresh);   added.removeAll(golden);
        Set<Binding> removed = new LinkedHashSet<>(golden); removed.removeAll(fresh);
        System.err.println("Registry diverged from golden file " + goldenPath + ":");
        for (Binding b : added)   System.err.println("  + " + b);
        for (Binding b : removed) System.err.println("  - " + b);
        return false;
    }

    // ── Entry point ──────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parseArgs(args);
        boolean ablation = hasFlag(args, "--ablation");
        boolean patterns = hasFlag(args, "--patterns");
        String  golden   = opts.get("--golden");
        String  dump     = opts.getOrDefault("--dump",  "target/semantic-registry-dump.json");
        String  truth    = opts.getOrDefault("--truth", "src/test/resources/dependency-ground-truth.trainticket.yaml");
        String  thrStr   = opts.get("--threshold");

        if (patterns) {
            runPatternSelfCheck();
            return;
        }

        GroundTruth gt = loadGroundTruth(truth);

        if (golden != null) {
            boolean ok = matchesGolden(golden);
            System.out.println(ok ? "Golden file matches." : "Golden file MISMATCH (see stderr).");
            if (!ok) System.exit(1);
            return;
        }

        if (ablation) {
            System.out.print(renderAblationTable(gt, ablationMenu()));
            return;
        }

        Set<Binding> registry = loadRegistryBindings(dump);
        EvalResult r = evaluate(gt, registry);
        System.out.print(renderReport(r));

        if (thrStr != null) {
            double threshold = Double.parseDouble(thrStr);
            if (r.f1() < threshold) {
                System.err.printf("F1 %.3f below threshold %.3f%n", r.f1(), threshold);
                System.exit(1);
            }
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) if (flag.equals(a)) return true;
        return false;
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new TreeMap<>();
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].startsWith("--")) m.put(args[i], args[i + 1]);
        }
        return m;
    }

    /**
     * Self-check for {@link SemanticDependencyRegistry#isIdLikeParam} and
     * {@link SemanticDependencyRegistry#normaliseIdStem}. Exercises suffix
     * forms, prefix forms, noise rejection, and plural-safe stemming.
     * Exits non-zero on any failure.
     */
    private static void runPatternSelfCheck() {
        int fail = 0;
        Object[][] recognise = {
            // suffix positives
            {"orderId", true},   {"account_id", true}, {"orderUUID", true},
            // prefix positives
            {"id_account", true}, {"idAccount", true},
            {"uuid_order", true}, {"uuidOrder", true}, {"Id_account", true},
            // noise — must reject
            {"identify", false}, {"identity", false}, {"idle", false},
            {"idp_account", false},
        };
        for (Object[] row : recognise) {
            boolean got = SemanticDependencyRegistry.isIdLikeParam((String) row[0]);
            if (got != (boolean) row[1]) {
                System.out.printf("FAIL isIdLikeParam(\"%s\"): got %s, want %s%n", row[0], got, row[1]);
                fail++;
            }
        }
        String[][] stems = {
            {"orderId", "order"}, {"account_id", "account"},
            {"id_account", "account"}, {"idAccount", "account"},
            {"uuid_order", "order"}, {"uuidOrder", "order"},
            // plural-safe stemming
            {"ordersId", "order"}, {"successId", "success"}, {"statusId", "status"},
        };
        for (String[] row : stems) {
            String got = SemanticDependencyRegistry.normaliseIdStem(row[0]);
            if (!row[1].equals(got)) {
                System.out.printf("FAIL normaliseIdStem(\"%s\"): got \"%s\", want \"%s\"%n", row[0], got, row[1]);
                fail++;
            }
        }
        if (fail == 0) System.out.println("Pattern self-check: ALL PASSED (" + (recognise.length + stems.length) + " cases)");
        else { System.err.println("Pattern self-check: " + fail + " failures"); System.exit(1); }
    }
}
