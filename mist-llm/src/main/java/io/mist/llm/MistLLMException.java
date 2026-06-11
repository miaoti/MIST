package io.mist.llm;

/**
 * Unchecked exception surfaced by the {@link LLMClient} SPI for unrecoverable
 * failures (configuration mismatches, backend wire-format errors that the
 * client does not know how to recover from). Recoverable failures (rate
 * limits, timeouts, parser misses) are signalled by a {@code null} result
 * from {@link LLMClient#prompt(String, String)} so existing callers continue
 * to short-circuit through their fallback path.
 */
public class MistLLMException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MistLLMException(String message) {
        super(message);
    }

    public MistLLMException(String message, Throwable cause) {
        super(message, cause);
    }
}
