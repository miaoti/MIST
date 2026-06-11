package io.mist.core.generation;
import io.mist.core.llm.ParameterInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * The new AI-driven generator that leverages the ZeroShotLLMGenerator.
 * Replaces your old 'AiDrivenInputGenerator'.
 */
public class AiDrivenLLMGenerator {

    private static final Logger log = LogManager.getLogger(AiDrivenLLMGenerator.class);
    private final ZeroShotLLMGenerator zeroShotLLM;

    public AiDrivenLLMGenerator() {
        this.zeroShotLLM = new ZeroShotLLMGenerator();
    }

    /**
     * Produce candidate values for the parameter using the zero-shot approach.
     * Uses the default count of 5.
     */
    public List<String> generateParameterValues(ParameterInfo param) {
        return zeroShotLLM.generateParameterValues(param, 5);
    }

    /**
     * Produce candidate values with a caller-specified count.
     * Used by shared pool generation to request larger batches.
     */
    public List<String> generateParameterValues(ParameterInfo param, int howMany) {
        return zeroShotLLM.generateParameterValues(param, howMany);
    }
    
    /**
     * Generate faulty parameter values using LLM - DEPRECATED
     * Use generateInvalidInputPool instead
     */
    @Deprecated
    public List<String> generateFaultyParameterValues(ParameterInfo param, int howMany) {
        log.info("DEPRECATED: Use generateInvalidInputPool instead");
        return zeroShotLLM.generateFaultyParameterValues(param, howMany);
    }
    
    /**
     * Generate comprehensive invalid input pool with 8 fault types
     * Delegates to ZeroShotLLMGenerator for actual generation
     */
    public io.mist.core.fault.InvalidInputPool generateInvalidInputPool(ParameterInfo param) {
        log.info("Delegating invalid input pool generation to ZeroShotLLMGenerator for parameter '{}'", param.getName());
        return zeroShotLLM.generateInvalidInputPool(param);
    }
}
