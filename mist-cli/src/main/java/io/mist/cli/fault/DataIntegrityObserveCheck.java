package io.mist.cli.fault;

/**
 * UX W1/W2 (REVIEW-UX-RECONCILIATION U1/U4/U5/U8, mist-ux-design.md rev 2):
 * the end-of-write check the writer emits into generated tests right after
 * every hooked, non-negative step's {@code afterWrite}.
 *
 * <p>Inert by construction outside product observe runs: it reads the CURRENT
 * session's record via {@link DataIntegrityRuntime#observeRecordFor}, which
 * returns {@code null} when no session is armed or the session is a paired
 * eval leg (U5) — so PairedFaultExecutor / g3 harness behavior is unchanged.
 *
 * <p>afterWrite is synchronous (the quiescence poll completes inside the
 * hook), so the record exists by the time this runs.
 *
 * <p>Failure semantics (U4): only an observation-gated absence on a VALIDATED
 * read-back ({@link DataIntegrityRuntime#observeTripleHasObservedPresent})
 * can fail the test, and only when {@code mst.oracle.dataintegrity.failonlost}
 * is set — the shipped default is WARN (the S2-FP calibration at product
 * default caps is the pre-registered condition for flipping the default).
 * Every Allure interaction is individually guarded so a missing/odd Allure
 * runtime can never break a test; the deliberate {@link AssertionError} is
 * the only thing this class ever throws.
 */
public final class DataIntegrityObserveCheck {

    /** Boolean system/config property: fail the test on a validated lost write. Default false = warn. */
    public static final String FAIL_ON_LOST_PROPERTY = "mst.oracle.dataintegrity.failonlost";

    /** Marker string carried by the failure message; the Allure category regex keys on it. */
    public static final String LOST_MARKER = "ACKED-BUT-LOST WRITE";

    private DataIntegrityObserveCheck() {
        // static entry point for generated code only
    }

    /** Emitted by the writer after a hooked step; correlationId = {@code <class>.<method>#<stepIdx>}. */
    public static void afterStep(String correlationId) {
        DataIntegrityRuntime.RunRecord r;
        try {
            r = DataIntegrityRuntime.observeRecordFor(correlationId);
        } catch (RuntimeException e) {
            return; // never let the check itself disturb a test
        }
        if (r == null) {
            return; // no session / paired leg (U5) / no record for this step
        }
        if (r.error != null) {
            step("ℹ️ data-integrity [" + r.tripleName + "]: internal error — not evidence ("
                    + trim(r.error, 160) + ")");
            return;
        }
        if (!r.acked) {
            step("ℹ️ data-integrity [" + r.tripleName + "]: write not acknowledged (HTTP "
                    + r.ackHttpStatus + ") — no durable-write claim");
            return;
        }
        switch (r.gate) {
            case OBSERVED_PRESENT:
                step("✅ durable write confirmed [" + r.tripleName + "] — read-back shows it ("
                        + r.polls + " poll(s), " + r.elapsedMs + " ms)");
                return;
            case TIMEOUT_ABSENT:
                step("⏳ persistence UNCONFIRMED [" + r.tripleName + "] — absent at the timeout "
                        + "with no trace confirmation (" + r.polls + " poll(s), " + r.elapsedMs
                        + " ms); timeout-gated, NOT counted as a defect");
                attach("⏳ persistence unconfirmed — " + r.tripleName, evidenceText(r,
                        "UNCONFIRMED (timeout-gated)",
                        "The write was acknowledged but the read-back never showed it before the "
                                + "timeout, and no trace confirmation was available to upgrade the "
                                + "absence to a defect verdict. Configure jaeger.base.url (trace "
                                + "backend) to enable the observation-gated defect tier."));
                return;
            case OBSERVED_COMPLETE_ABSENT:
                boolean validated;
                try {
                    validated = DataIntegrityRuntime.observeTripleHasObservedPresent(r.tripleName);
                } catch (RuntimeException e) {
                    validated = false;
                }
                if (!validated) {
                    // U8 quarantine: this read-back has never shown ANY write landing
                    // this run — a mis-bound read-back looks exactly like this.
                    step("⚠️ read-back QUARANTINED [" + r.tripleName + "] — acked write is "
                            + "observation-gated absent, but this triple's read-back has not shown any "
                            + "write landing this run; verify the triple binding before trusting a "
                            + "defect verdict");
                    attach("⚠️ quarantined absence — " + r.tripleName, evidenceText(r,
                            "QUARANTINED (unvalidated read-back)",
                            "No write to this triple was observed landing in this run, so the absence "
                                    + "may be a mis-bound read-back (wrong endpoint/shape) rather than a "
                                    + "lost write. Precision-first: reported as a warning, not a failure."));
                    return;
                }
                String msg = LOST_MARKER + " [" + r.tripleName + "] " + r.correlationId
                        + " — client got HTTP " + r.ackHttpStatus
                        + " (acked) but the durable write is ABSENT from the read-back "
                        + "(observation-gated: the step's own trace is complete)";
                attach("💧 " + LOST_MARKER + " — " + r.tripleName, evidenceText(r,
                        "ACKED-BUT-LOST (observation-gated defect)",
                        "The acknowledgement told the client the operation succeeded, but the durable "
                                + "state the operation promises never appeared on the read-back, and the "
                                + "step's own trace is complete — the write is lost, not late."));
                if (Boolean.getBoolean(FAIL_ON_LOST_PROPERTY)) {
                    throw new AssertionError(msg);
                }
                step("💧 " + msg + "  [" + FAIL_ON_LOST_PROPERTY + "=false → warning only]");
                return;
            case NOT_APPLICABLE:
            default:
                // no wait ran; nothing to report
        }
    }

    /** Plain-language evidence block mirroring the hidden-downstream attachment pattern. */
    private static String evidenceText(DataIntegrityRuntime.RunRecord r, String verdict, String meaning) {
        StringBuilder sb = new StringBuilder();
        sb.append("VERDICT: ").append(verdict).append('\n');
        sb.append(meaning).append("\n\n");
        sb.append("WHAT THE CLIENT SAW:\n  HTTP ").append(r.ackHttpStatus);
        if (r.ackBodyStatus != null) {
            sb.append("  (body status ").append(r.ackBodyStatus).append(')');
        }
        sb.append("  — acknowledged ✅\n\n");
        sb.append("WHAT THE READ-BACK SHOWS:\n");
        sb.append("  triple        : ").append(r.tripleName).append('\n');
        sb.append("  step          : ").append(r.correlationId == null ? r.stepKey : r.correlationId).append('\n');
        sb.append("  isolation key : ").append(r.isolationKey).append('\n');
        sb.append("  polls         : ").append(r.polls).append(" over ").append(r.elapsedMs).append(" ms\n");
        sb.append("  last read HTTP: ").append(r.readbackHttpStatus).append('\n');
        sb.append("  gate          : ").append(r.gate).append('\n');
        if (r.baselineBody != null) {
            sb.append("\nBASELINE (before the write, first ").append(SNIPPET).append(" chars):\n  ")
                    .append(trim(r.baselineBody, SNIPPET)).append('\n');
        }
        if (r.lastReadbackBody != null) {
            sb.append("\nLAST READ-BACK (first ").append(SNIPPET).append(" chars):\n  ")
                    .append(trim(r.lastReadbackBody, SNIPPET)).append('\n');
        }
        String jaeger = System.getProperty("jaeger.base.url");
        sb.append("\nTRACE BACKEND: ").append(jaeger == null || jaeger.trim().isEmpty()
                ? "not configured (jaeger.base.url unset) — defect tier unavailable"
                : jaeger.trim());
        return sb.toString();
    }

    private static final int SNIPPET = 500;

    private static String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').replace('\r', ' ');
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    // ── individually-guarded Allure interactions (a broken/absent Allure runtime must never fail a test) ──

    private static void step(String text) {
        try {
            io.qameta.allure.Allure.step(text);
        } catch (Throwable ignored) {
            // Allure lifecycle unavailable — the terminal summary still reports
        }
    }

    private static void attach(String name, String content) {
        try {
            io.qameta.allure.Allure.addAttachment(name, "text/plain", content);
        } catch (Throwable ignored) {
            // ditto
        }
    }
}
