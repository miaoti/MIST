package io.mist.core.spi;

import io.swagger.v3.oas.models.OpenAPI;

/**
 * Read-only view of an OpenAPI v3 specification consumed by the MIST
 * generation pipeline.
 *
 * <p>The MIST contribution does not own a spec parser: parsing is delegated
 * to whichever {@link MistSpecLoader} the runtime finds on the classpath
 * (typically the RESTest-backed implementation in {@code mist-restest-adapter}).
 * mist-core only depends on the parsed result through this interface and
 * the underlying {@link OpenAPI} model from swagger-core, which is the
 * de-facto standard for OpenAPI v3 representations on the JVM.
 *
 * <p>Implementations are expected to be immutable; methods may be called
 * from multiple threads.
 */
public interface MistSpec {

    /**
     * Returns the raw underlying OpenAPI v3 model. mist-core consumers walk
     * this directly when they need path / operation / schema details.
     */
    OpenAPI getOpenApi();

    /**
     * Returns the source location the spec was loaded from (file path or
     * URL). Used by diagnostics, logging, and the test-writer header
     * comments.
     */
    String getLocation();

    /**
     * Returns the spec's display title with the first letter cased per
     * the {@code capitalize} flag. Empty string when the spec has no
     * title or the title collapses to whitespace after stripping
     * non-alphanumeric characters.
     */
    String getTitle(boolean capitalize);
}
