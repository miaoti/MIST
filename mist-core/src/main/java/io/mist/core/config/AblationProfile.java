package io.mist.core.config;

import io.mist.core.fault.FaultMiner;

/**
 * Snapshot of the seven ablation toggles that drive the R1-R4
 * configurations from {@code PATH_B_POSITIONING.md} section 4.2. Built
 * once per run from {@link MstConfig} and surfaced in the startup banner
 * so the eventual paper-writing pipeline can identify which row a run
 * belongs to.
 *
 * <p>Six of the toggles live in {@link MstConfig.Oracle} and
 * {@link MstConfig.Scheduler}; the seventh is the pre-existing
 * {@code mist.fault.mining.enabled} system property owned by
 * {@link FaultMiner#ENABLED_PROPERTY}.
 */
public final class AblationProfile {

    private final boolean shapeOracleEnabled;
    private final boolean spanTreeInvariantEnabled;
    private final boolean statusPropagationInvariantEnabled;
    private final boolean responseEnvelopeInvariantEnabled;
    private final boolean timingEnvelopeInvariantEnabled;
    private final boolean banditEnabled;
    private final boolean faultMiningEnabled;

    public AblationProfile(boolean shapeOracleEnabled,
                           boolean spanTreeInvariantEnabled,
                           boolean statusPropagationInvariantEnabled,
                           boolean responseEnvelopeInvariantEnabled,
                           boolean timingEnvelopeInvariantEnabled,
                           boolean banditEnabled,
                           boolean faultMiningEnabled) {
        this.shapeOracleEnabled = shapeOracleEnabled;
        this.spanTreeInvariantEnabled = spanTreeInvariantEnabled;
        this.statusPropagationInvariantEnabled = statusPropagationInvariantEnabled;
        this.responseEnvelopeInvariantEnabled = responseEnvelopeInvariantEnabled;
        this.timingEnvelopeInvariantEnabled = timingEnvelopeInvariantEnabled;
        this.banditEnabled = banditEnabled;
        this.faultMiningEnabled = faultMiningEnabled;
    }

    public static AblationProfile from(MstConfig config) {
        MstConfig.Oracle oracle = config.oracle();
        MstConfig.Scheduler scheduler = config.scheduler();
        boolean faultMining = Boolean.parseBoolean(
                System.getProperty(FaultMiner.ENABLED_PROPERTY, "false"));
        return new AblationProfile(
                oracle.shapeOracleEnabled(),
                oracle.spanTreeInvariantEnabled(),
                oracle.statusPropagationInvariantEnabled(),
                oracle.responseEnvelopeInvariantEnabled(),
                oracle.timingEnvelopeInvariantEnabled(),
                scheduler.banditEnabled(),
                faultMining);
    }

    public boolean shapeOracleEnabled() { return shapeOracleEnabled; }
    public boolean spanTreeInvariantEnabled() { return spanTreeInvariantEnabled; }
    public boolean statusPropagationInvariantEnabled() { return statusPropagationInvariantEnabled; }
    public boolean responseEnvelopeInvariantEnabled() { return responseEnvelopeInvariantEnabled; }
    public boolean timingEnvelopeInvariantEnabled() { return timingEnvelopeInvariantEnabled; }
    public boolean banditEnabled() { return banditEnabled; }
    public boolean faultMiningEnabled() { return faultMiningEnabled; }

    /**
     * One-line human-readable summary of the seven toggles. Format keeps
     * each subsystem in its own bracket group so {@code grep} on the run
     * log is trivial:
     *
     * <pre>
     * [oracle:on (span,status,response | timing:off) bandit:on faultmining:off]
     * </pre>
     *
     * <p>When the whole oracle is gated off, the invariant list collapses
     * to {@code oracle:off} for readability.
     */
    public String summary() {
        StringBuilder sb = new StringBuilder("[oracle:");
        if (!shapeOracleEnabled) {
            sb.append("off");
        } else {
            sb.append("on (");
            StringBuilder loaded = new StringBuilder();
            if (spanTreeInvariantEnabled) appendName(loaded, "span");
            if (statusPropagationInvariantEnabled) appendName(loaded, "status");
            if (responseEnvelopeInvariantEnabled) appendName(loaded, "response");
            if (loaded.length() == 0) loaded.append("none");
            sb.append(loaded).append(" | timing:");
            sb.append(timingEnvelopeInvariantEnabled ? "on" : "off");
            sb.append(")");
        }
        sb.append(" bandit:").append(banditEnabled ? "on" : "off");
        sb.append(" faultmining:").append(faultMiningEnabled ? "on" : "off");
        sb.append("]");
        return sb.toString();
    }

    private static void appendName(StringBuilder sb, String name) {
        if (sb.length() > 0) sb.append(",");
        sb.append(name);
    }
}
