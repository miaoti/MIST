package io.mist.cli.spi;

import io.mist.core.spec.OpenAPISpecification;
import io.mist.core.spi.MistSpec;
import io.mist.core.spi.MistSpecLoader;

/**
 * {@link MistSpecLoader} implementation registered via
 * {@code META-INF/services/io.mist.core.spi.MistSpecLoader}.
 * Delegates parsing to {@link OpenAPISpecification}'s string-constructor.
 */
public final class DefaultMistSpecLoader implements MistSpecLoader {

    /** Required no-arg constructor for {@link java.util.ServiceLoader}. */
    public DefaultMistSpecLoader() {}

    @Override
    public MistSpec load(String location) {
        return new DefaultMistSpec(new OpenAPISpecification(location));
    }
}
