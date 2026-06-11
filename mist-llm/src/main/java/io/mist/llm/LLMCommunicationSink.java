package io.mist.llm;

import java.util.Properties;

/**
 * Sink SPI that lets {@code mist-llm} hand off request/response metadata to a
 * communication logger living in a downstream module (typically
 * {@code mist-restest-adapter}'s {@code LLMCommunicationLogger}, which in
 * turn drives the on-disk LLM-communication log files and the
 * {@code SystemResourceMonitor}).
 *
 * <p>The interface stays narrow on purpose: it carries the two events
 * {@link LLMService} already emits and otherwise leaves logging policy
 * (timestamps, file rotation, redaction, resource monitoring) to the
 * implementation. Discovery is by {@link java.util.ServiceLoader} so
 * {@code mist-llm} introduces no compile-time edge to the adapter.
 *
 * <p>Callers that have no sink registered get the no-op binding
 * {@code NoOpLLMCommunicationSink} that ships in this module — keeping the
 * legacy zero-config behaviour ({@code llm.communication.logging.enabled}
 * still defaults to {@code true} in the adapter, but only the adapter knows
 * how to honour it).
 */
public interface LLMCommunicationSink {

    /**
     * Opaque token returned by {@link #logRequest} and passed to
     * {@link #logResponse} so the sink can correlate the two events without
     * the LLM module having to know its internal request-id strategy.
     */
    interface RequestHandle {
        long getStartTime();
    }

    /**
     * Configure the sink (called once, from the {@link LLMService}
     * constructor). The properties argument is the same {@link Properties}
     * the legacy code already passes through, so existing keys keep working
     * unchanged.
     */
    void init(Properties properties);

    /** Record an outbound request. The returned handle must not be {@code null}. */
    RequestHandle logRequest(String modelType,
                             String modelName,
                             String systemPrompt,
                             String userPrompt,
                             String endpoint,
                             Object metadata);

    /** Record the response (or failure) paired to a previously-issued handle. */
    void logResponse(RequestHandle handle,
                     String response,
                     boolean success,
                     String errorMessage);

    /** Release any resources held by the sink (file writers, monitors, ...). */
    void close();
}
