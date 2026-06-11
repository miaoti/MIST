/**
 * MIST's LLM dispatch + cache layer. Houses the SPI ({@link io.mist.llm.LLMClient},
 * {@link io.mist.llm.LLMRequest}, {@link io.mist.llm.LLMResponse},
 * {@link io.mist.llm.LLMBackendKind}, {@link io.mist.llm.MistLLMException})
 * and the concrete implementations ({@link io.mist.llm.LLMService},
 * {@link io.mist.llm.LLMConfig}, {@link io.mist.llm.LLMCallCache},
 * {@link io.mist.llm.GeminiApiClient}, {@link io.mist.llm.OllamaApiClient}).
 *
 * <p>The module is deliberately free of {@code mist-core},
 * {@code mist-restest-adapter}, and {@code restest-core} dependencies so that
 * {@code mist-core} (which forbids {@code es.us.isa.*} deps) can call the LLM
 * directly. Communication-side artefacts that need RESTest-specific helpers
 * (e.g. {@code LLMCommunicationLogger}) stay in
 * {@code mist-restest-adapter} and bind to the module via the {@link
 * io.mist.llm.LLMCommunicationSink} ServiceLoader SPI.
 */
package io.mist.llm;
