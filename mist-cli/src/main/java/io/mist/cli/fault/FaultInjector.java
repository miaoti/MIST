package io.mist.cli.fault;

/**
 * Backend-swappable fault activation for the main-track control/fault pairing
 * executor (TOOL-EXECUTION-PLAN B1.1): {@link #inject} makes the target's
 * injected fault live, {@link #clear} restores the SUT, and both return only
 * once the SUT has converged so the next request observes the new state.
 *
 * <p>The whole fault-injection mode is gated by {@link #ENABLED_PROPERTY}
 * (default {@code false}, read via {@code System.getProperty} exactly like
 * {@code FaultMiner.ENABLED_PROPERTY}); with the flag off no implementation
 * of this interface is ever constructed and the legacy pipeline is untouched.
 *
 * <p>Gate-1 backend: {@link SutFlagFaultInjector} (SUT-side {@code -D} flag —
 * scaffolding that manufactures labeled ground truth, not a tool dependency).
 * A connection-level Toxiproxy backend is deferred to Gate 3.
 */
public interface FaultInjector {

    String ENABLED_PROPERTY = "mist.fault.injection.enabled";

    /** True only when the opt-in fault-injection mode was requested. */
    static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"));
    }

    /** Activates the fault on the target; blocks until the SUT converged. */
    void inject(FaultTarget target);

    /**
     * Deactivates the fault on the target; blocks until the SUT converged.
     * Idempotent: clearing a target that is not injected is a no-op on the
     * SUT (the first pairing run also flushes any stale flag a manual smoke
     * left behind).
     */
    void clear(FaultTarget target);

    /**
     * Coordinates of one SUT-side fault flag: the deployment to toggle and
     * the JVM system property the injected code reads. Populated from the
     * target-triple registry's {@code fault_flag} block.
     */
    final class FaultTarget {
        public final String deployment;
        public final String property;

        public FaultTarget(String deployment, String property) {
            if (deployment == null || deployment.trim().isEmpty()) {
                throw new IllegalArgumentException("FaultTarget needs a non-empty deployment");
            }
            if (property == null || property.trim().isEmpty()) {
                throw new IllegalArgumentException("FaultTarget needs a non-empty property");
            }
            this.deployment = deployment.trim();
            this.property = property.trim();
        }

        @Override
        public String toString() {
            return deployment + " (-D" + property + ")";
        }
    }

    /** Signals that fault activation infrastructure failed (not a SUT verdict). */
    class FaultInjectionException extends RuntimeException {
        public FaultInjectionException(String message) {
            super(message);
        }

        public FaultInjectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
