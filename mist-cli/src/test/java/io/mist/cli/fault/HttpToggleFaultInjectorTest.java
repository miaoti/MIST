package io.mist.cli.fault;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit coverage for the pure mode-derivation of {@link HttpToggleFaultInjector} (the HTTP call
 * itself needs the live inside-payment endpoint and is exercised by the G3 head-to-head run).
 */
public class HttpToggleFaultInjectorTest {

    @Test
    public void modeOf_extractsTheModeSegmentFromTheDrawbackProperty() {
        assertEquals("fail", HttpToggleFaultInjector.modeOf(new FaultInjector.FaultTarget(
                "ts-inside-payment-service", "mist.fault.drawback.fail.enabled")));
        assertEquals("fabricatedack", HttpToggleFaultInjector.modeOf(new FaultInjector.FaultTarget(
                "ts-inside-payment-service", "mist.fault.drawback.fabricatedack.enabled")));
    }

    @Test(expected = FaultInjector.FaultInjectionException.class)
    public void modeOf_rejectsAPropertyWithTooFewSegments() {
        HttpToggleFaultInjector.modeOf(new FaultInjector.FaultTarget("ts-inside-payment-service", "too.short"));
    }
}
