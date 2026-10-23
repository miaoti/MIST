package io.mist.core.generation;

import io.mist.core.value.ValueProvenance;
import io.mist.core.value.ValueProvenanceInference;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The value the zero-shot generator invents when the LLM is unavailable must read as a synthetic
 * placeholder, so a test that sends it is marked negative in advance.
 */
public class ZeroShotFallbackPlaceholderTest {

    @Test
    public void fallbackValueIsRecognizedAsPlaceholder() {
        for (int n : new int[] {0, 72, 740, 999}) {
            assertEquals(ValueProvenance.SYNTHETIC_PLACEHOLDER,
                    ValueProvenanceInference.infer(ZeroShotLLMGenerator.fallbackPlaceholder(n)));
        }
    }

    @Test
    public void fallbackValueKeepsItsNumber() {
        assertEquals("FALLBACK_test740", ZeroShotLLMGenerator.fallbackPlaceholder(740));
    }
}
