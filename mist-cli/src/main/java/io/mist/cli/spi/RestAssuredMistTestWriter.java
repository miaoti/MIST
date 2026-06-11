package io.mist.cli.spi;

import java.util.Collection;
import java.util.List;

import io.mist.core.testcase.TestCase;
import io.mist.cli.writer.MultiServiceRESTAssuredWriter;
import io.mist.core.spi.MistTestWriter;

/**
 * {@link MistTestWriter} implementation that emits JUnit + RESTAssured
 * source files by delegating to RESTest's
 * {@link MultiServiceRESTAssuredWriter}.
 *
 * <p>The writer's construction-time configuration (OpenAPI spec path,
 * test-config path, output directory, class name, package name, base
 * URI, log-to-file flag) is captured up front via
 * {@link #configure(MultiServiceRESTAssuredWriter)}. mist-core's
 * generation pipeline calls only the parameterless
 * {@link #write(List, String)} entry point on the SPI; the adapter
 * surface keeps the rich configuration knobs on the delegate.
 */
public final class RestAssuredMistTestWriter implements MistTestWriter<Object> {

    private MultiServiceRESTAssuredWriter delegate;

    /** Required no-arg constructor for {@link java.util.ServiceLoader}. */
    public RestAssuredMistTestWriter() {}

    /**
     * Programmatic constructor used by adapter-side wiring that already
     * has a configured {@code MultiServiceRESTAssuredWriter} in hand.
     */
    public RestAssuredMistTestWriter(MultiServiceRESTAssuredWriter delegate) {
        this.delegate = delegate;
    }

    /**
     * Inject (or replace) the underlying RESTAssured writer. Allows the
     * SPI to be discovered via ServiceLoader without immediate
     * configuration; the runtime configures it once paths are known.
     */
    public void configure(MultiServiceRESTAssuredWriter writer) {
        this.delegate = writer;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void write(List<Object> testCases, String outputDirectory) {
        if (delegate == null) {
            throw new IllegalStateException(
                    "RestAssuredMistTestWriter has not been configured — call configure(...) "
                            + "with a fully-built MultiServiceRESTAssuredWriter before writing.");
        }
        // The delegate's write() expects Collection<TestCase>; the SPI
        // carrier is Object so adapter call sites can avoid leaking the
        // RESTest TestCase type across the seam. We cast unchecked
        // because the runtime contract is: only RESTest TestCase
        // instances ever flow through this implementation.
        Collection raw = (Collection) testCases;
        delegate.write((Collection<TestCase>) raw);
    }
}
