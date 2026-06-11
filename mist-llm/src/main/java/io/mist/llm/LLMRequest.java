package io.mist.llm;

/**
 * Immutable request envelope for the {@link LLMClient} SPI. Final class with
 * field accessors (NOT a record — the project's source/target is Java 11).
 *
 * <p>Callers that only need the legacy "two strings in, one string out"
 * shape can keep using {@link LLMClient#prompt(String, String)}; the
 * envelope form exists so future callers can carry per-call sampling
 * settings ({@code maxTokens}, {@code temperature}) and a backend hint
 * without further widening the SPI.
 */
public final class LLMRequest {

    private final String systemPrompt;
    private final String userPrompt;
    private final int maxTokens;
    private final double temperature;
    private final LLMBackendKind backendHint;

    public LLMRequest(String systemPrompt, String userPrompt) {
        this(systemPrompt, userPrompt, 200, 0.7, null);
    }

    public LLMRequest(String systemPrompt,
                      String userPrompt,
                      int maxTokens,
                      double temperature,
                      LLMBackendKind backendHint) {
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.backendHint = backendHint;
    }

    public String getSystemPrompt() { return systemPrompt; }
    public String getUserPrompt() { return userPrompt; }
    public int getMaxTokens() { return maxTokens; }
    public double getTemperature() { return temperature; }
    /** May be {@code null} when the caller is happy to let the configured backend decide. */
    public LLMBackendKind getBackendHint() { return backendHint; }
}
