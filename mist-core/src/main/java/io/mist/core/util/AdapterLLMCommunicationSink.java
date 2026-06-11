package io.mist.core.util;

import io.mist.llm.LLMCommunicationSink;

import java.util.Properties;

/**
 * {@link LLMCommunicationSink} binding for {@code mist-llm} that delegates
 * every event to the legacy {@link LLMCommunicationLogger} singleton living
 * in this adapter module. Registered via
 * {@code META-INF/services/io.mist.llm.LLMCommunicationSink} so the
 * mist-llm {@code LLMService} discovers it through {@link java.util.ServiceLoader}
 * with zero compile-time edge back into the adapter.
 *
 * <p>The bridge preserves all existing behaviour:
 * <ul>
 *   <li>{@code llm.communication.logging.enabled} (default {@code true}) still
 *       gates whether the on-disk log files are written.</li>
 *   <li>{@code llm.resource.monitoring.*} still wires {@code SystemResourceMonitor}
 *       through {@code LLMCommunicationLogger.getInstance(properties)}.</li>
 *   <li>No new properties or log destinations are introduced.</li>
 * </ul>
 */
public final class AdapterLLMCommunicationSink implements LLMCommunicationSink {

    private LLMCommunicationLogger delegate;

    /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
    public AdapterLLMCommunicationSink() {
        // Construction is split from init(): ServiceLoader instantiates with
        // the no-arg constructor and then the LLMService hands us the
        // properties bundle it would have passed to LLMCommunicationLogger
        // directly.
    }

    @Override
    public void init(Properties properties) {
        this.delegate = LLMCommunicationLogger.getInstance(properties);
    }

    @Override
    public RequestHandle logRequest(String modelType,
                                    String modelName,
                                    String systemPrompt,
                                    String userPrompt,
                                    String endpoint,
                                    Object metadata) {
        final LLMCommunicationLogger.LLMRequestContext ctx = delegate.logRequest(
                modelType, modelName, systemPrompt, userPrompt, endpoint, metadata);
        return new RequestHandleImpl(ctx);
    }

    @Override
    public void logResponse(RequestHandle handle,
                            String response,
                            boolean success,
                            String errorMessage) {
        if (!(handle instanceof RequestHandleImpl)) {
            return;
        }
        delegate.logResponse(((RequestHandleImpl) handle).context, response, success, errorMessage);
    }

    @Override
    public void close() {
        if (delegate != null) {
            delegate.close();
        }
    }

    /** Adapter handle that smuggles the legacy context across the SPI boundary. */
    private static final class RequestHandleImpl implements RequestHandle {
        private final LLMCommunicationLogger.LLMRequestContext context;

        RequestHandleImpl(LLMCommunicationLogger.LLMRequestContext context) {
            this.context = context;
        }

        @Override public long getStartTime() { return context.getStartTime(); }
    }
}
