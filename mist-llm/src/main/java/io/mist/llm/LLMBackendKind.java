package io.mist.llm;

/**
 * Discriminator for the LLM backend a caller wants to address. Matches the
 * three {@link LLMConfig.ModelType} values so the SPI and the concrete
 * configuration stay in lock-step.
 */
public enum LLMBackendKind {
    /** Local or remote Ollama installation (typically {@code http://localhost:11434}). */
    OLLAMA,

    /** Google Gemini ({@code generativelanguage.googleapis.com/v1beta/models}). */
    GEMINI,

    /**
     * Any OpenAI-compatible chat-completions HTTP endpoint: DeepSeek, OpenAI,
     * OpenRouter, Together, Groq, gpt4all, llama.cpp, ...
     */
    OPENAI_COMPATIBLE
}
