package io.mist.llm;

/**
 * SPI for invoking an LLM backend from any MIST module. Implementations may
 * route to OpenAI-compatible HTTP endpoints, Google Gemini, Ollama, or a
 * stub. The interface is intentionally tiny so {@code mist-core} (which
 * forbids {@code es.us.isa.*} deps) can program against it without dragging
 * the whole {@code mist-restest-adapter} surface in.
 *
 * <p>The legacy "two strings in, one string out" entry point is exposed as
 * {@link #prompt(String, String)} for symmetry with the existing
 * {@link LLMService#generateText(String, String)} convenience method.
 * Implementations that want to expose the full request envelope override
 * {@link #promptWith(LLMRequest)}; the default implementation here wraps the
 * string version so simple clients do not have to.
 */
public interface LLMClient {

    /**
     * Synchronous prompt. Returns {@code null} when the backend fails for a
     * reason callers usually want to handle by short-circuiting to their own
     * fallback (timeout, parser miss, rate-limit exhaustion). Throws
     * {@link MistLLMException} for unrecoverable wire-format / configuration
     * failures.
     */
    String prompt(String systemPrompt, String userPrompt);

    /**
     * Envelope-shaped prompt. Default implementation delegates to
     * {@link #prompt(String, String)} so legacy implementations stay
     * compatible. Override when the implementation can usefully consume
     * {@link LLMRequest#getMaxTokens()} / {@link LLMRequest#getTemperature()}.
     */
    default LLMResponse promptWith(LLMRequest request) {
        if (request == null) {
            return LLMResponse.failure("request is null");
        }
        String content;
        try {
            content = prompt(request.getSystemPrompt(), request.getUserPrompt());
        } catch (RuntimeException re) {
            return LLMResponse.failure(re.getMessage());
        }
        if (content == null) {
            return LLMResponse.failure("backend returned no content");
        }
        return LLMResponse.ok(content);
    }
}
