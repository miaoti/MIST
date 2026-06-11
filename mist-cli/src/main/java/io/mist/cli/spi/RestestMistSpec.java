package io.mist.cli.spi;

import io.mist.core.spec.OpenAPISpecification;
import io.mist.core.spi.MistSpec;
import io.swagger.v3.oas.models.OpenAPI;

/**
 * Thin adapter that exposes RESTest's {@link OpenAPISpecification} via
 * the {@link MistSpec} SPI. mist-core depends only on the SPI; this
 * class lives in mist-restest-adapter because the underlying parser
 * (and its full transitive dependency tree) stays on the adapter
 * side.
 */
public final class RestestMistSpec implements MistSpec {

    private final OpenAPISpecification delegate;

    public RestestMistSpec(OpenAPISpecification delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    /** Exposes the wrapped RESTest spec for adapter-side glue. */
    public OpenAPISpecification delegate() {
        return delegate;
    }

    @Override
    public OpenAPI getOpenApi() {
        return delegate.getSpecification();
    }

    @Override
    public String getLocation() {
        return delegate.getPath();
    }

    @Override
    public String getTitle(boolean capitalize) {
        return delegate.getTitle(capitalize);
    }
}
