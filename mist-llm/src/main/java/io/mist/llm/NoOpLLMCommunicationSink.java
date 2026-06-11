package io.mist.llm;

import java.util.Properties;

/**
 * Default {@link LLMCommunicationSink} binding used when no downstream
 * implementation registers itself via {@link java.util.ServiceLoader}. The
 * sink drops every event silently — equivalent to setting
 * {@code llm.communication.logging.enabled=false} in the adapter — but it
 * keeps the SPI path totally allocation-free so callers never have to
 * null-check.
 */
public final class NoOpLLMCommunicationSink implements LLMCommunicationSink {

    /** Cached handle so we don't allocate per call when the sink is disabled. */
    private static final RequestHandle HANDLE = new RequestHandle() {
        @Override public long getStartTime() { return System.currentTimeMillis(); }
    };

    @Override public void init(Properties properties) { /* no-op */ }

    @Override
    public RequestHandle logRequest(String modelType,
                                    String modelName,
                                    String systemPrompt,
                                    String userPrompt,
                                    String endpoint,
                                    Object metadata) {
        return HANDLE;
    }

    @Override
    public void logResponse(RequestHandle handle,
                            String response,
                            boolean success,
                            String errorMessage) {
        /* no-op */
    }

    @Override public void close() { /* no-op */ }
}
