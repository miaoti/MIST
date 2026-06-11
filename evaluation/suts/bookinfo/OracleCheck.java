import io.mist.core.oracle.shape.TraceModel;
import io.mist.core.oracle.shape.TraceShapeVerdict;
import io.mist.core.oracle.shape.invariant.HiddenDownstreamFailureInvariant;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Runs MIST's real HiddenDownstreamFailure oracle (from mist.jar) on a captured
 * Bookinfo Jaeger trace, alongside what a response-level oracle would see.
 * Usage: java -cp mist.jar OracleCheck.java <trace.json> [rootApiKey]
 */
public class OracleCheck {
    public static void main(String[] args) throws Exception {
        Path p = Paths.get(args[0]);
        String rootApiKey = args.length > 1 ? args[1] : "GET /productpage";
        List<TraceModel> models = TraceModel.fromJaegerJson(p);
        System.out.println("Loaded " + models.size() + " trace(s) from " + p.getFileName());
        for (TraceModel tm : models) {
            System.out.println("\n=== trace " + tm.getTraceId() + "  (" + tm.getSpans().size() + " spans) ===");
            List<TraceModel.Span> roots = tm.roots();
            boolean rootAll2xx = true;
            for (TraceModel.Span r : roots) {
                System.out.println("  client-facing ROOT : " + r.service + "  " + r.operation
                        + "  http=" + r.httpStatus + " otel=" + r.otelStatus);
                if (!(r.httpStatus >= 200 && r.httpStatus < 300)) rootAll2xx = false;
            }
            for (TraceModel.Span s : tm.getSpans()) {
                boolean se = s.httpStatus >= 500 || "ERROR".equalsIgnoreCase(s.otelStatus);
                if (se) System.out.println("  downstream ERROR   : " + s.service + "  " + s.operation
                        + "  http=" + s.httpStatus + " otel=" + s.otelStatus);
            }
            System.out.println("  --> RESPONSE-LEVEL oracle (status/schema/soft-error sees the client response): "
                    + (rootAll2xx ? "PASS  (root is 2xx — looks successful, MISSES the failure)" : "would flag"));
            HiddenDownstreamFailureInvariant inv = new HiddenDownstreamFailureInvariant(rootApiKey);
            TraceShapeVerdict.InvariantOutcome o = inv.evaluate(tm);
            System.out.println("  --> TRACE oracle HIDDEN_DOWNSTREAM_FAILURE: "
                    + (o.passed ? "pass" : "FIRES") + "  severity=" + o.severity);
            if (!o.passed) System.out.println("      " + o.detail);
        }
    }
}
