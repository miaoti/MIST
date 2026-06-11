package io.mist.llm;

/**
 * Immutable response envelope for the {@link LLMClient} SPI. Final class
 * with field accessors (NOT a record — the project's source/target is
 * Java 11).
 *
 * <p>{@link #getContent()} is {@code null} when the backend failed (timeout,
 * parser miss, etc.) — the same convention as the legacy
 * {@link LLMService#generateText(String, String, int, double)} string return.
 * Callers that want a hard failure signal should check {@link #isSuccess()}.
 */
public final class LLMResponse {

    private final String content;
    private final boolean success;
    private final String errorMessage;

    public LLMResponse(String content, boolean success, String errorMessage) {
        this.content = content;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static LLMResponse ok(String content) {
        return new LLMResponse(content, content != null, null);
    }

    public static LLMResponse failure(String errorMessage) {
        return new LLMResponse(null, false, errorMessage);
    }

    public String getContent() { return content; }
    public boolean isSuccess() { return success; }
    /** Human-readable failure description; {@code null} when {@link #isSuccess()} is {@code true}. */
    public String getErrorMessage() { return errorMessage; }
}
