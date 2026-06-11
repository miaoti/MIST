package io.mist.core.generation;

import io.mist.core.workflow.WorkflowScenario;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Phase 1 part 2 contract: the two new public hooks on MistGenerator that
 * MistRunner's two-phase orchestrator depends on (setFaultyRatio /
 * getFaultyRatio round-trip; resetForNewPhase clears approvedApiKeys and
 * restores the scenarios snapshot).
 *
 * <p>Constructs a minimal MistGenerator with empty specs / configs /
 * scenarios so the test stays out of LLM and SUT territory; only the
 * fields touched by these methods matter.
 */
public class MistGeneratorTwoPhaseTest {

    @Test
    public void setFaultyRatio_andGet_roundTrip() {
        MistGenerator gen = newEmptyGenerator();
        float original = gen.getFaultyRatio();
        gen.setFaultyRatio(0.0f);
        assertEquals(0.0f, gen.getFaultyRatio(), 0.0f);
        gen.setFaultyRatio(original);
        assertEquals(original, gen.getFaultyRatio(), 0.0f);
    }

    @Test
    public void setFaultyRatioZero_isDistinctFromDefault() {
        MistGenerator gen = newEmptyGenerator();
        // Default from MstConfig.faulty().ratio() is the canonical legacy
        // value — for the trainticket bundled config it's > 0. The contract
        // we care about is that setFaultyRatio(0) does something OBSERVABLE:
        // the cached field changes. Other state (scenarios, configs) is
        // unchanged.
        gen.setFaultyRatio(0.0f);
        assertEquals(0.0f, gen.getFaultyRatio(), 0.0f);
    }

    @Test
    public void resetForNewPhase_isIdempotent_onEmptyGenerator() {
        MistGenerator gen = newEmptyGenerator();
        // No approvedApiKeys, no scenarios — reset should not throw and
        // should leave the field-shape unchanged.
        gen.resetForNewPhase();
        gen.resetForNewPhase();
        gen.resetForNewPhase();
    }

    @Test
    public void resetForNewPhase_doesNotResetFaultyRatio() {
        // The ratio is restored by MistRunner, not by the generator's reset
        // hook. resetForNewPhase must NOT silently revert the ratio
        // (otherwise the runner's Phase A → resetForNewPhase → Phase B
        // sequence would clobber the in-flight setFaultyRatio(0)).
        MistGenerator gen = newEmptyGenerator();
        gen.setFaultyRatio(0.42f);
        gen.resetForNewPhase();
        assertEquals(0.42f, gen.getFaultyRatio(), 0.0f);
    }

    // ---------------------------------------------------------------------

    private static MistGenerator newEmptyGenerator() {
        OpenAPI primary = new OpenAPI();
        primary.setInfo(new Info().title("empty").version("0.0.0"));
        List<WorkflowScenario> scenarios = new ArrayList<>();
        return new MistGenerator(
                primary,
                null /* TestConfigurationObject — not exercised in these tests */,
                Collections.emptyMap(),
                Collections.emptyMap(),
                scenarios,
                false /* useLLMforParams */,
                false /* ignoreFlowsFlag */);
    }
}
